package info.xiancloud.test.reactivepgdao;

import com.alibaba.fastjson.JSONObject;
import info.xiancloud.core.Group;
import info.xiancloud.core.Handler;
import info.xiancloud.core.Input;
import info.xiancloud.core.Unit;
import info.xiancloud.core.message.UnitRequest;
import info.xiancloud.core.message.UnitResponse;
import info.xiancloud.dao.core.utils.TableMetaCache;

import java.util.concurrent.ExecutionException;

public class TestReactivepgGetColsUnit implements Unit {
    private static final String TABLE_NAME = "untitled_table";

    @Override
    public Input getInput() {
        return null;
    }

    @Override
    public Group getGroup() {
        return ReactivepgTestDaoGroup.SINGLETON;
    }

    @Override
    public void execute(UnitRequest request, Handler<UnitResponse> handler) throws ExecutionException {
        handler.handle(UnitResponse.createSuccess(
                new JSONObject()
                        .fluentPut("cols", TableMetaCache.COLS.get(TABLE_NAME))
                        .fluentPut("idCol", TableMetaCache.ID_COL.get(TABLE_NAME))
        ));
    }
}
