# 县医院入站 Key 基础实施验证

日期：2026-09-24

范围：外部系统入站 Key 轮换管理、单向校验、独立集成安全链及管理页展示。

## 已实施

- `sys_external_system.inbound_key_hash` 保存 BCrypt 校验值；Key 由平台密码学安全随机数生成，明文仅随本次轮换响应返回。
- 管理端 `POST /api/v1/configuration/external-systems/{systemId}/inbound-key` 使用 `configuration:write`、`rowversion` 和管理审计；响应使用 `Cache-Control: no-store`，审计摘要不包含Key正文。
- `/api/integration/**` 使用独立无状态安全链，通过`X-External-System-Id`与`X-External-System-Key`识别已登记且启用的外部系统。认证仅建立系统身份，不做机构白名单或逐交易授权。
- 外部系统管理页显示Key设置状态并支持生成/轮换；旧Key立即失效确认后，明文只在当前抽屉内存显示并可复制，关闭后清除。该功能与平台访问基层HIS的出站凭证分离。

## 验证结果

| 验证 | 结果 |
| --- | --- |
| SQL Server 2012开发库迁移 | V1400首次尝试暴露SQL Server同一批次内新增列后创建约束的编译问题；Flyway事务回滚。脚本增加`GO`批次边界后，V1400重试成功，schema版本升至1400 |
| 后端启动数据库合同门禁 | 通过：SQL Server 2012、兼容级别110、365个字段和构造映射一致 |
| `mvn verify` | 通过：332项测试，0失败、0错误、6项真实HIS探测跳过 |
| 前端`npm run verify:frontend` | 通过：169项测试；Web Admin/Web Embed类型检查和生产构建、12个正式菜单、前端注释检查通过 |
| SQL迁移静态检查与数据库契约生成检查 | 通过；V1400字段和约束有对应`MS_Description`，部署核对/修复SQL与Flyway迁移一致 |
| 实时HTTP | `/actuator/health`与`/api/v1/session/csrf`返回200；集成入口无凭证、错误系统ID/Key均返回401 |
| 浏览器真实写入/回读/审计 | 2026-09-24，以已登录 `admin`（平台管理员）在正式管理端 `/external-systems` 操作。新增 `VERIFY_INBOUND_KEY_20260924` 临时系统，更新用途说明为本地开发演示、禁止生产并由项目负责人手动删除；没有机构接口配置。页面正常启用系统并签发Key，重开资料页显示“已设置”但不回显明文。审计页回读到新增、启用/资料更新、两次Key轮换成功记录；轮换摘要均说明明文未写入审计。演示记录按用户要求保留，供后续手动删除。 |
| 集成入口真实凭据验证 | 首次对`18080`探测发现未映射路由被通用异常处理误报为`500 INTERNAL_ERROR`。修复为标准404后，在隔离启动的`18081`实例（Flyway关闭、连接同一SQL Server 2012开发库、启动数据库契约门禁通过）复验：健康检查200；有效Key访问`/api/integration/v1/probe`返回`404 RESOURCE_NOT_FOUND`，无效Key返回401；错误响应带平台请求编号。有效Key已通过身份认证，但路径没有Controller，因此这不证明任何LIS业务成功。 |
| 路由错误语义回归 | `GlobalExceptionHandler` 对Spring MVC未映射请求增加404转换；`ConfigurationSecurityWebTest` 中有效认证访问未实现路径断言404及`RESOURCE_NOT_FOUND`。`mvn -f backend/pom.xml -Dtest=ConfigurationSecurityWebTest test` 通过：11项，0失败、0错误。 |
| 后端完整回归 | `mvn -f backend/pom.xml verify` 通过：330项，0失败、0错误、6项真实HIS探测跳过；Java样式和生产Javadoc门禁通过。 |
| 审计操作展示 | 首次回读发现Key轮换动作在审计页显示“未登记中文名称”；已补充 `EXTERNAL_SYSTEM_INBOUND_KEY_ROTATED` 中文映射和外部系统菜单审计动作登记，复验列表显示“轮换县医院入站调用Key”，审计摘要无Key明文。 |
| 前端复验 | `npm run verify:frontend` 通过：菜单登记检查、前后端TypeScript类型检查、生产构建及169项前端测试全部通过；新增审计动作映射回归断言。 |
| `./tools/verify.sh` | 未通过Java包结构检查：工作区现有5个`backend/src/main/java/cn/zqkj/platform/his/domain/lis/` DTO/模型路径不符合该检查器规则；本轮没有重构这组业务适配代码 |

## 尚未完成

- 当前演示调用方在本地开发库处于启用状态，用户计划后续手动删除。主开发服务`18080`未重启，新增404修复在隔离实例`18081`完成验证；如需在原管理端浏览器中体验该错误语义，需在后续重启主服务。县医院业务Controller实现后，仍需验证正式业务路由、业务结果及交换记录；当前不宣称LIS业务成功。
- 县医院确认`600-001/002/003`入站字段、交易码、报告编码、重复请求及目标HIS结果未知处理后，再实现业务Controller并进行真实联调。
- 生产环境TLS、县医院网络接入和凭证交付尚未验证。
