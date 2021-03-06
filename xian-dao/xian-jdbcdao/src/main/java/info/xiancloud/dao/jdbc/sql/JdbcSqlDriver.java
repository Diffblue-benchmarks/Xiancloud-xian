package info.xiancloud.dao.jdbc.sql;

import com.alibaba.fastjson.JSONObject;
import info.xiancloud.core.message.UnitResponse;
import info.xiancloud.core.util.LOG;
import info.xiancloud.core.util.Pair;
import info.xiancloud.core.util.StringUtil;
import info.xiancloud.core.util.thread.MsgIdHolder;
import info.xiancloud.dao.core.DaoGroup;
import info.xiancloud.dao.core.action.SqlAction;
import info.xiancloud.dao.core.action.insert.BatchInsertAction;
import info.xiancloud.dao.core.action.select.CustomSelectAction;
import info.xiancloud.dao.core.connection.XianConnection;
import info.xiancloud.dao.core.model.ddl.JavaType;
import info.xiancloud.dao.core.model.ddl.Table;
import info.xiancloud.dao.core.model.sqlresult.*;
import info.xiancloud.dao.core.sql.BaseSqlDriver;
import info.xiancloud.dao.core.utils.BasicSqlBuilder;
import info.xiancloud.dao.core.utils.JdbcPatternUtil;
import info.xiancloud.dao.core.utils.SqlUtils;
import info.xiancloud.dao.jdbc.connection.JdbcConnection;
import io.reactivex.Completable;
import io.reactivex.Single;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jdbc prepared sql builder implementation
 *
 * @author happyyangyuan
 */
public class JdbcSqlDriver extends BaseSqlDriver {

    /**
     * the jdbc internal connection.
     * {@link #setConnection(XianConnection)} must be invoked before using this property
     */
    private Connection connection0;

    @Override
    public String preparedSql(String patternSql) {
        if (preparedSql == null) {
            preparedSql = JdbcPatternUtil.getPreparedSql(patternSql);
        }
        return preparedSql;
    }


    @Override
    public Single<SingleInsertionResult> insert(String patternSql, Map<String, Object> value) {
        return insert(preparedSql(patternSql), preparedParams(patternSql, value));
    }

    @Override
    public Single<BatchInsertionResult> batchInsert(BatchInsertAction batchInsertAction) {
        return Single.fromCallable(() -> {
            Pair<String, Object[]> preparedSqlAndParamArrayPair = preparedBatchInsertionSql(batchInsertAction);
            int count = doSql(preparedSqlAndParamArrayPair.fst, preparedSqlAndParamArrayPair.snd);
            return new BatchInsertionResult().setCount(count);
        });
    }

    @Override
    public Pair<String, Object[]> preparedBatchInsertionSql(BatchInsertAction batchInsertAction) {
        if (preparedSql == null) {
            Pair<String, Object[]> preparedSqlAndValues = BasicSqlBuilder.buildJdbcBatchInsertPreparedSQL(
                    batchInsertAction.getTableName(), batchInsertAction.getCols(), batchInsertAction.getValues());
            preparedSql = preparedSqlAndValues.fst;
            preparedParams = preparedSqlAndValues.snd;
        }
        return Pair.of(preparedSql, preparedParams);
    }

    /**
     * Async single record insertion
     *
     * @return {@link SingleInsertionResult}
     * 如果插入的记录是自动生成主键的，返回结果中会包含自动生成的主键id，否则返回的id属性为null，
     * 同时返回插入条数。
     */
    private Single<SingleInsertionResult> insert(String preparedSql, Object[] sqlParams) {
        return Single.fromCallable(() -> {
            PreparedStatement statement = connection0.prepareStatement(preparedSql, PreparedStatement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < sqlParams.length; i++) {
                statement.setObject(i + 1, sqlParams[i]);
            }
            statement.execute();
            ResultSet rs = statement.getGeneratedKeys();
            Integer count = statement.getUpdateCount();
            Long id;
            if (rs.next()) {
                id = rs.getLong(1);
            } else {
                //no id is generated
                id = null;
            }
            rs.close();
            statement.close();
            return new SingleInsertionResult().setCount(count).setId(id).setPreparedSql(preparedSql);
        });
    }

    @Override
    public Single<UpdatingResult> update(String sqlPattern, Map<String, Object> map) {
        return Single.fromCallable(() -> {
            int count = doSql(preparedSql(sqlPattern), preparedParams(sqlPattern, map));
            return new UpdatingResult().setCount(count);
        });
    }

    private int doSql(String preparedSql, Object[] preparedParams) throws SQLException {
        PreparedStatement pstmt = connection0.prepareStatement(preparedSql);
        for (int i = 0; i < preparedParams.length; i++) {
            pstmt.setObject(i + 1, preparedParams[i]);
        }
        return pstmt.executeUpdate();
    }

    @Override
    public Single<String[]> queryCols(String tableName) {
        return Single.fromCallable(() -> {
            List<String> resultList = new ArrayList<>();
            String sql = "SELECT column_name\n" +
                    "FROM information_schema.columns\n" +
                    "WHERE table_schema = DATABASE()\n" +
                    "AND table_name='%s'\n";
            sql = String.format(sql, tableName);
            Statement st = connection0.createStatement();
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                resultList.add(rs.getString(1));
            }
            return resultList.toArray(new String[resultList.size()]);
        });
    }

    /**
     * 查询主键名
     */
    private Single<String> getIdColName(String tableName) {
        String alias = "idColumnName";
        return new CustomSelectAction() {
            @Override
            protected String patternSql() {
                return String.format("SELECT k.COLUMN_NAME as %s\n" +
                        "FROM information_schema.table_constraints t\n" +
                        "LEFT JOIN information_schema.key_column_usage k\n" +
                        "USING(constraint_name,table_schema,table_name)\n" +
                        "WHERE t.constraint_type='PRIMARY KEY'\n" +
                        "    AND t.table_schema=DATABASE() \n" +
                        "    AND t.table_name='%s';", alias, tableName);
            }

            @Override
            public int resultType() {
                return SELECT_SINGLE;
            }
        }.execute(null, null, connection, MsgIdHolder.get()).map(unitResponse -> {
            SingleRecordSelectionResult autoIncrement = unitResponse.getData();
            if (autoIncrement.getCount() == 0) {
                LOG.info("No primary key exists in table " + tableName);
                return "";
            }
            return autoIncrement.getRecord().get(alias).toString();
        });
    }

    @Override
    public Single<String> getIdCol(String tableName) {
        if (StringUtil.isEmpty(idCol)) {
            return getIdColName(tableName).doOnSuccess(idCol -> {
                this.idCol = idCol;
            });
        }
        return Single.just(idCol);
    }

    @Override
    public Completable buildTableMetaData(Table table) {
        return Completable.fromAction(() -> {
            doBuildTable(table, connection0);
        });
    }

    private static void doBuildTable(Table table, Connection conn) {
        String sql = "SELECT * FROM " + table.getName() + " WHERE 1 = 2";
        Statement stm;
        try {
            stm = conn.createStatement();
            ResultSet rs = stm.executeQuery(sql);
            ResultSetMetaData rsmd = rs.getMetaData();

            JavaType javaType = new JavaType();
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String colName = rsmd.getColumnName(i);
                String colClassName = rsmd.getColumnClassName(i);

                Class<?> clazz = javaType.getType(colClassName);
                if (clazz != null) {
                    table.setColumnType(colName, clazz);
                } else {
                    int type = rsmd.getColumnType(i);
                    if (type == Types.BINARY || type == Types.VARBINARY || type == Types.BLOB) {
                        table.setColumnType(colName, byte[].class);
                    } else if (type == Types.CLOB || type == Types.NCLOB) {
                        table.setColumnType(colName, String.class);
                    } else {
                        table.setColumnType(colName, String.class);
                    }
                }
            }
            rs.close();
            stm.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public UnitResponse handleException(Throwable exception, SqlAction sqlAction) {
        String actualSql;
        if (sqlAction instanceof BatchInsertAction) {
            actualSql = preparedBatchInsertionSql((BatchInsertAction) sqlAction).fst;
        } else {
            //todo use sqlAction.getFullSql() instead?
            actualSql = SqlUtils.mapToSql(sqlAction.getPatternSql(), sqlAction.getMap());
        }
        UnitResponse response = UnitResponse.createException(exception, "sql failure: " + actualSql);
        if (exception instanceof SQLException) {
            switch (((SQLException) exception).getErrorCode()) {
                //fixme, this is for mysql only
                /*Error: 1062 SQLSTATE: 23000 (ER_DUP_ENTRY)
                  Message: Duplicate entry '%s' for key %d */
                case 1062:
                    response = UnitResponse.create(DaoGroup.CODE_REPETITION_NOT_ALLOWED, actualSql, exception.getLocalizedMessage());
                    break;
                default:
                    response = UnitResponse.createError(DaoGroup.CODE_SQL_ERROR, actualSql, "执行sql语句出现问题");
            }
        }
        return response;
    }

    @Override
    public BaseSqlDriver setConnection0(XianConnection connection) {
        JdbcConnection jdbcConnection = (JdbcConnection) connection;
        connection0 = jdbcConnection.getConnection0();
        return this;
    }

    @Override
    public Single<RecordsListSelectionResult> select(String patternSql, Map<String, Object> map) {
        return Single.fromCallable(() -> {
            String sql = preparedSql(patternSql);
            Object[] objectArr = preparedParams(patternSql, map);
            PreparedStatement ps = connection0.prepareStatement(sql);
            for (int i = 0; i < objectArr.length; i++) {
                ps.setObject(i + 1, objectArr[i]);
            }
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> list = new ArrayList<>();
            ResultSetMetaData md = rs.getMetaData();
            //Map rowData;
            int columnCount = md.getColumnCount();
            //rowData = new HashMap(columnCount);
            while (rs.next()) {
                Map<String, Object> rowData = new JSONObject();
                for (int i = 1; i <= columnCount; i++) {
                    /*注意两点:多表关联查询会出现字段名重复的情形,重复字段的colName会自动被替换成tableName.colName;
                    我们使用getColumnLabel(i)而不使用getColumnName(i)来支持sql中的别名*/
                    String colName = StringUtil.underlineToCamel(md.getColumnLabel(i));
                    colName = rowData.containsKey(colName) ? md.getTableName(i).concat(".").concat(colName) : colName;
                    rowData.put(colName, rs.getObject(i));
                }
                list.add(rowData);
            }
            return new RecordsListSelectionResult().setCount(list.size()).setRecords(list);
        });
    }

    @Override
    public Single<DeletionResult> delete(String patternSql, Map<String, Object> map) {
        return Single.fromCallable(() -> {
            int count = doSql(preparedSql(patternSql), preparedParams(patternSql, map));
            return new DeletionResult().setCount(count);
        });
    }

}
