# LIS 600-001 平台候选入口验证记录

日期：2026-09-24
验证范围：县医院ID/Key安全过滤器 → 600-001 HTTP Controller → 平台机构路由 → 已验证HIS来源机构派生 → `PhisService`调用边界 → 脱敏`ExchangeRuntimeRecord`编排。

## 结果

- `LisIntegrationControllerWebTest`：4项通过。验证有效Key进入入口、错误Key返回401且不调用业务服务、未知包类型返回400、目标无响应以502携带交换编号返回。
- `LisIntegrationServiceImplTest`：3项通过。验证平台机构代码仅作路由、认证调用方代码进入交换记录、成功结果产生独立`operationId`/`exchangeRequestId`、超时记为`NO_RESPONSE`并返回受控“结果无法确认”，未知机构不会调用HIS或写交换记录。
- `PhisServiceImplTest`：18项通过，包含600-001来源机构号从同一次已解析的已验证端点取得并传至协议层。
- 定向命令：`mvn -f backend/pom.xml -Dtest=LisIntegrationServiceImplTest,LisIntegrationControllerWebTest,PhisServiceImplTest test`；25项测试、0失败、0错误。
- 全量后端验证：`mvn -f backend/pom.xml verify`；342项测试、0失败、0错误、6项跳过；Checkstyle和生产Javadoc检查通过。
- Spring Boot真实进程：首次直接从Shell启动时未继承`PLATFORM_DB_URL`；改用仓库`tools/run-backend-dev.sh`后，隔离实例在18081成功连接本地SQL Server 2012开发实例，启动数据库门禁确认兼容级别110及365个映射字段一致。原端口18080用户进程和登录会话未停止或修改。
- 实时HTTP：18081 `/actuator/health`返回200/UP；无凭证请求新LIS入口返回401 `AUTHENTICATION_FAILED`，请求未进入业务层，也未调用HIS或写交换记录。18081临时开发实例保留运行，使用`DEVELOPMENT`环境。
- 完整仓库门禁：工程规范、Java结构/权限/Javadoc、后端验证和SQL迁移检查均通过；菜单交付检查随后提示数据库契约维护未进入正式导航。单独Web Admin/Web Embed类型检查和生产构建通过，前端测试169项中167通过、2项失败，分别为基础数据计划文本断言和同步弹窗布局断言，均不属于本轮LIS改动。
- OpenAPI：`interface-specs/openapi/platform-api-v1.yaml` 可由PyYAML解析，且包含600-001路径定义。

## 限制

- 响应使用合成`PhisResponse`；测试没有连接基层HIS，也没有将交换记录写入开发SQL Server后重新查询。
- 本地演示调用方没有基层HIS端点，因此没有用它进行业务成功探测。
- `600-001` 的平台入站字段及响应目前是平台候选契约，县医院尚未确认；本验证不构成接口签收。
- 服务端默认通过`PLATFORM_INTEGRATION_HIS_ENVIRONMENT`选择`DEVELOPMENT`环境；请求方不能选择或覆盖环境、HIS源机构号、地址及授权码。

## 2026-09-24 流程追踪补强

- `LisIntegrationController` 在认证与入参校验通过后生成平台 `operationId` 并交由业务服务；路由完成、准备实际调用HIS时另生成 `exchangeRequestId`。
- 未知/停用机构返回404，基层HIS端点或来源机构配置缺失返回409；两者均返回 `operationId`，尚未调用HIS时不生成交换编号，也不写交换记录。
- 平台机构路由读取失败返回503及 `operationId`。HIS已返回但交换事实无法落库时返回503并带 `operationId` 与 `exchangeRequestId`，不将平台存储故障伪装成HIS失败。
- 新增覆盖：HIS配置缺失、无效协议响应、交换记录写入失败及HTTP机构路由错误。定向 LIS 编排和Web测试共11项，0失败、0错误。
- 该次复验仍为合成服务测试；未使用管理页展示的基层HIS测试端点发起调用，也未向外部HIS发送请求。真实HIS联调及SQL交换记录回读后续实施。
- 最新代码已在隔离开发端口18081重新启动；SQL Server 2012启动只读门禁通过，兼容级别110、365个字段及Java映射一致。健康检查返回200，未提供外部系统凭证的集成请求返回401 `AUTHENTICATION_FAILED`，在认证层被拦截。
- 用户当前主服务18080及登录会话未重启；隔离服务18081保持运行。此运行检查未使用正确Key，也没有进入机构路由或调用HIS。
- 最新`./tools/verify.sh`通过仓库、Java结构/Javadoc、后端342项测试及SQL迁移静态检查；随后被既有正式菜单登记门禁阻断（数据库契约维护尚未进入正式导航）。单独前端类型检查和管理端/嵌入端构建通过；前端169项中167通过、2项既有失败，分别为基础数据计划文案断言和同步批次弹窗布局断言。
