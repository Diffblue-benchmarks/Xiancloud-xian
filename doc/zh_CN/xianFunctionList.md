## xian frame现有功能
1. 微服务间通讯RPC、MQ。
2. 方法级粒度的服务治理、服务可视化管理（zkui）。
3. 集中日志收集和可视化日志查询（提供了gelf4j日志插件和graylog集成）。
4. 分布式业务链路追踪方案，可以在上述3的日志系统内查询定位出单条业务链路上完备日志线（xian框架基于ttl实现msgId传递机制，将业务日志做了串联）。
5. 将Java web应用集成到微服务集群内形成业务层的“微服务”，复用框架提供的自动化集成部署和横向扩展能力，目前支持任何servlet框架集成，特别对springboot做了友好支持。
6. 微服务和数据库一对一、一对多、多对多关系的灵活支持。（业务层和dao层的插件可以拆分成部署）
7. 构建部署和持续集成插件。（集成了cloud.tencent.com容器服务restapi插件，同时xian_template提供rancher pipeline支持）
8. 业务监控插件。（监控unit健康度，主要是监控unit的执行耗时情况，并收集日志数据，支持输出到openfalcon、grafana）
9. 业务线程池管理和监控。（同上）
10. 服务不下线：全微服务0停服更新。（滚动更新）
11. 内置轻量级的持久层dao插件，支持连接池监控、慢SQL监控和防SQL注入等。（数据访问DAO层插件内置功能）
12. 轻量级api网关，具有一定的api接口编排能力。（unit编排，形成新的业务API）
13. api文档自动化生成的能力。（apidoc插件）
14. 基于oauth2.0的api网关安全管理和客户端ip白名单控制能力。
15. 快速实现开放平台能力，助力SaaS化服务落地。
16. redis缓存插件，支持多redis数据源能力。
17. 分布式消息订阅和推送功能。（xianRabbitmq插件集成了rabbitmq实现消息总线能力）
18. 定时任务调度功能。（集成了quartz）
19. 集中配置管理。（zk和zkui配置管理）
20. 分布式锁。（redis全局分布式锁）
21. 多环境管理（研发、测试、生产环境，类似maven profile的概念）
22. 本地非集群运行模式和本地集群运行模式，方便开发阶段调试。
23. log4j-1.x、log4j-2.x日志插件
24. 短信和邮件发送插件
25. mqtt协议客户端集成
26. 对腾讯云k8s容器服务的集成
27. 对数据库读写分离的友好支持。
28. 一致性哈希算法的封装支持。
29. 基于一致性哈希算法的异步保序功能。
31. 第三方功能扩展能力：自定义插件开发。
32. 响应式编程（rxJava2集成）。
33. **MongoDB持久层插件。**

### 正在开发中的功能
1. 内置持久层框架对分布式事务支持
2. api网关内置反向代理的功能
3. api接口编排脚本支持热更新
4. 断路器、熔断技术

### 规划中的功能
1. 基于api网关内置反向代理实现灰度/蓝绿/红黑发布。
2. ~~集成rxJava实现纯异步的微服务调用模式，可完全杜绝线程阻塞情况的发生，预估可成倍提升业务线程的性能。~~ reactiveXian早已上线。
3. ~~不局限于特定语言，将来会率先支持.NET语言实现微服务，帮助解决许多传统企业历史信息系统转型互联网微服务架构。~~可行性方面，本框架已经抽象出了rpc通信协议规范和服务治理规范，因此几乎其他所有OOP语言都可以集成进来。多开发语言支持，暂时不计划实现。
4. 基于“录音机”的API自动化测试方案。
5. ~~分库分表方案。~~ 根据互联网和IT行业技术趋势，目前已经有很多成熟的商业OLTP数据库软件如tidb，cockroachdb等等，如果您的业务达到了读写分离都解决不了的地步，那恭喜您的产品大概已经赢得了市场的青睐成为了独角兽产品了，求抱大腿。
6. ~~对rancher管理平台的rest api集成实现自动化和CI/CD插件。~~ 请直接使用rancherUI 2.x友好的界面操作和pipeline进行CI/CD。