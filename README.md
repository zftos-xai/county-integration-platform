# 县人民医院与基层卫生院中间接口平台

本目录是工程建设规范对应的首版项目骨架。业务关系严格限定为“县人民医院 ⇄ 所辖基层卫生院”。当前只建立公共工程能力，不表示75项业务接口已经取得接入批准或完成联调。

## 当前技术基线

- 后台：Java 17、Spring Boot 3.5、MyBatis
- 数据库：Microsoft SQL Server 2019/2022，独立平台数据库和账号
- 页面：Vue 3、TypeScript，分为管理端和嵌入端
- 部署：医院机房或私有云

## 工程目录说明

项目采用一个 Maven 后端和两个 npm workspace 前端。以下只列工程源码与受维护的资料目录；`node_modules/`、`backend/target/` 和前端 `dist/` 是依赖或构建产物，不属于业务模块。

```text
.
├─ backend/                         Java 17 / Spring Boot 3.5 / MyBatis 模块化单体
│  ├─ pom.xml                       后端依赖、构建及 Java 版本约束
│  └─ src/
│     ├─ main/java/cn/zqkj/
│     │  ├─ access/                 机构数据范围校验；已实现机构权限守卫
│     │  ├─ bootstrap/              平台基础信息接口
│     │  ├─ exchange/               交换记录模块；已实现按机构只读查询
│     │  ├─ shared/                 统一响应、请求编号、异常处理与安全配置
│     │  ├─ alert/                  运行告警边界（仅包说明）
│     │  ├─ audit/                  审计记录边界（仅包说明）
│     │  ├─ catalog/                业务字典与编码映射边界（仅包说明）
│     │  ├─ interfaceconfig/        接口登记与配置边界（仅包说明）
│     │  ├─ organization/           机构及人员归属边界（仅包说明）
│     │  ├─ patient/                患者标识与关联边界（仅包说明）
│     │  └─ reconciliation/         跨系统对账边界（仅包说明）
│     ├─ main/resources/
│     │  ├─ application.yml         数据源、Flyway、MyBatis、服务及监控配置
│     │  ├─ db/migration/           SQL Server 数据库迁移脚本
│     │  └─ mapper/                 MyBatis SQL 映射文件
│     └─ test/                      后端单元测试
├─ web-admin/                       Vue 3 / TypeScript / Vite 运行管理端
│  └─ src/                          路由、运行总览、业务占位页和样式
├─ web-embed/                       Vue 3 / TypeScript / Vite 工作站嵌入端
│  └─ src/                          等待医院批准的安全上下文的入口页和样式
├─ contracts/
│  ├─ openapi/                      当前管理 API 的 OpenAPI 基线
│  ├─ mappings/                     正式确认后存放字段与编码映射；现为规则说明
│  └─ samples/                      脱敏样例存放规则；不得提交患者原始数据
├─ deploy/                          后端 Dockerfile 与 SQL Server/后端 Compose 配置
├─ docs/                            项目资料，详见 docs/README.md
│  ├─ plans/                        方案与立项材料
│  ├─ standards/                    工程建设规范
│  ├─ reference/interfaces/         接口参考资料、清单及配套资源
│  └─ decisions/                    工程决策记录
├─ tools/                           PowerShell 构建、验证及文档生成脚本
│  └─ document-generation/          方案、规范、立项书和接口矩阵生成脚本
├─ .github/workflows/               后端与前端持续集成检查
├─ package.json                     npm workspace 与前端统一命令
└─ README.md                        项目入口和开发说明
```

`exchange/` 内按 `controller`、`service`、`repository`、`mapper`、`model` 分层；`client` 和 `support` 目前仅预留包说明。管理端除运行总览的后台健康检查外，其他业务视图仍是占位页。目录划分表示代码归属，不代表医院身份认证、正式业务接口或联调验收已经完成。

## 开始开发

前置环境：JDK 17、Maven 3.9、Node.js 22、npm 11。数据库需要 SQL Server 2019或2022。

```powershell
npm install
./tools/verify.ps1
```

后台启动前必须通过环境变量提供独立平台数据库连接，禁止把密码写入仓库：

```powershell
$env:PLATFORM_DB_URL='jdbc:sqlserver://127.0.0.1:1433;databaseName=county_integration;encrypt=true;trustServerCertificate=true'
$env:PLATFORM_DB_USERNAME='platform_app'
$env:PLATFORM_DB_PASSWORD='<由安全渠道提供>'
mvn -f ./backend/pom.xml spring-boot:run
```

管理端和嵌入端分别启动：

```powershell
npm run dev --workspace web-admin
npm run dev --workspace web-embed
```

## 当前实现范围

- 已建立模块目录、统一响应、请求编号、默认拒绝的安全入口和健康检查。
- 已建立接口定义、交换记录和审计记录的首版 SQL Server 迁移。
- 已建立交换记录只读查询示例、OpenAPI基线和前端真实连接状态。
- 已建立工程决策、私有化部署和CI检查入口。

以下内容尚未完成，不能据此宣称接口已接入：医院身份认证方式、75项接口的正式合同和映射、真实脱敏样例、测试和生产网络、系统凭证、患者匹配规则及各业务完整过程。

## 接口开发条件

每项接口必须先具备正式文件、脱敏样例、网络条件、凭证、双方负责人和验收场景。条件不足时不得进入具体适配，不得猜测字段或使用演示结果代替联调证据。接口确认和推进台账由项目管理材料统一维护，不纳入工程仓库。

接口实施顺序与放行依据见 [接口对接实施方案](docs/integration/接口对接实施方案.md)。Java 编码参考 Alibaba Java Coding Guidelines，项目具体约束见 [Java 编码规范](docs/standards/java-coding-guidelines.md)；后端 Maven `validate` 运行基础静态规则检查。

## 项目资料

项目方案、立项材料、工程规范及接口原始资料已统一归档，详见 [docs/README.md](docs/README.md)。文档生成脚本位于 `tools/document-generation`。
