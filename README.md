# 县人民医院与基层卫生院中间接口平台

本目录是工程建设规范对应的首版项目骨架。平台部署在县人民医院侧，保存获批的基层机构基础数据；县医院HIS厂商在平台数据库上建立受控视图，由县医院HIS使用只读账号读取这些基础数据。其他业务通过平台按`PHIS_Interface`等正式接口交互。当前只建立公共工程能力，不表示75项业务接口、平台库视图或基础数据同步已经完成联调。

平台以“基层基础数据进入平台库并由县医院 HIS 通过受控视图只读获取”和“其他业务通过正式接口交互”两条链路运行。完整关系、边界和实施范围统一见[总体方案](docs/plans/总体方案.md)。

## 当前技术基线

- 后台：Java 17、Spring Boot 3.5、MyBatis
- 数据库：Microsoft SQL Server 2012 SP4，安装医院批准的最新可用安全/GDR更新，使用独立平台数据库和账号
- 页面：Vue 3、TypeScript，分为管理端和嵌入端
- 部署：医院机房或私有云
- 数据：经批准的基层机构、人员、科室和目录基础数据保存到平台库；运行记录和审计信息最小化留存；业务正文默认不留存，确需暂存时逐接口审批、加密并按期清理

## 工程目录说明

项目采用一个Maven后端和两个npm workspace前端。以下目录树同时展示当前已经落地的结构和确定的目标模块；标记“按需创建”的目录只在开始实现相应能力时建立，不使用空包占位。`node_modules/`、`backend/target/`和前端`dist/`属于依赖或构建产物，不纳入工程结构。

```text
.
├─ backend/                                      Java 17 / Spring Boot 3.5 / MyBatis模块化单体
│  ├─ pom.xml                                    后端依赖、构建插件及Java版本约束
│  ├─ checkstyle.xml                             Java静态规则和Javadoc门禁
│  └─ src/
│     ├─ main/
│     │  ├─ java/cn/zqkj/platform/               Java基础包
│     │  │  ├─ PlatformApplication.java          应用启动和组件扫描入口
│     │  │  ├─ bootstrap/                        Spring装配及启动配置（按需创建）
│     │  │  ├─ foundation/                       不包含业务规则的平台基础能力
│     │  │  │  ├─ web/                          统一API响应及Web公共处理
│     │  │  │  │  └─ error/                     全局异常转换和错误响应
│     │  │  │  ├─ security/                     安全配置及机构数据范围守卫
│     │  │  │  ├─ observability/                请求编号、日志和可观测能力
│     │  │  │  └─ persistence/                  MyBatis公共配置（按需创建）
│     │  │  ├─ system/                           平台自身管理能力
│     │  │  │  └─ api/                          平台信息等只读系统接口
│     │  │  └─ modules/                          业务模块容器
│     │  │     ├─ organization/                 机构、人员和科室归属（按需创建）
│     │  │     ├─ patientidentity/              患者标识及跨系统关联（按需创建）
│     │  │     ├─ registry/                     接口登记、版本、字典和编码映射（按需创建）
│     │  │     ├─ exchange/                     接收、发送、交换状态及追踪
│     │  │     │  ├─ api/                       HTTP入口、校验、授权和响应转换
│     │  │     │  ├─ application/               查询用例、事务及持久化边界
│     │  │     │  ├─ domain/                    交换实体、状态和业务规则（按需创建）
│     │  │     │  └─ infrastructure/            交换模块的技术实现
│     │  │     │     ├─ persistence/            MyBatis Mapper及查询实现
│     │  │     │     └─ client/                 获批外部系统适配器（按需创建）
│     │  │     ├─ reconciliation/               数据核对和差异处理（按需创建）
│     │  │     ├─ alert/                        运行告警及处置（按需创建）
│     │  │     └─ audit/                        访问、配置和操作审计（按需创建）
│     │  └─ resources/
│     │     ├─ application.yml                  数据源、Flyway、MyBatis、服务及监控配置
│     │     ├─ db/migration/                    SQL Server版本化迁移脚本
│     │     └─ mybatis/
│     │        └─ exchange/                     交换模块MyBatis XML
│     └─ test/java/cn/zqkj/platform/             镜像主代码包结构的后端测试
│        ├─ foundation/security/                 机构权限边界测试
│        └─ modules/exchange/application/        交换查询用例测试
├─ web-admin/                                    Vue 3 / TypeScript / Vite运行管理端
│  ├─ package.json                               管理端依赖和开发、构建命令
│  ├─ vite.config.ts                             Vite构建与开发服务配置
│  ├─ tsconfig.json                              TypeScript编译配置
│  ├─ index.html                                 管理端HTML入口
│  └─ src/
│     ├─ main.ts                                 管理端启动入口
│     ├─ App.vue                                 路由和应用外壳
│     ├─ views/                                  运行总览、双角色原型和占位视图
│     ├─ styles/                                 基础样式和原型样式
│     └─ prototypeData.ts                        仅供原型使用的合成演示数据
├─ web-embed/                                    Vue 3 / TypeScript / Vite工作站嵌入端
│  ├─ package.json                               嵌入端依赖和开发、构建命令
│  ├─ vite.config.ts                             Vite构建与开发服务配置
│  ├─ tsconfig.json                              TypeScript编译配置
│  ├─ index.html                                 嵌入端HTML入口
│  └─ src/
│     ├─ main.ts                                 嵌入端启动入口
│     ├─ App.vue                                 等待安全上下文的入口页面
│     └─ styles/                                 嵌入端基础样式
├─ contracts/                                    机器可读合同及受控样例
│  ├─ interface-onboarding-template.md           单项接口准入信息模板
│  ├─ openapi/platform-api-v1.yaml               当前管理API的OpenAPI基线
│  ├─ mappings/README.md                         获批字段和编码映射的存放规则
│  └─ samples/README.md                          正式脱敏样例存放规则
├─ deploy/                                       私有化部署配置
│  ├─ Dockerfile.backend                         后端容器镜像定义
│  ├─ docker-compose.yml                         连接外部SQL Server的后端编排
│  ├─ .env.example                               非敏感环境变量示例
│  └─ README.md                                  SQL Server 2012 SP4部署及验证说明
├─ docs/                                         总体方案、设计、规范、决策和接口资料
│  ├─ plans/                                     唯一总体方案
│  ├─ standards/                                 工程、Java与SQL Server建设规范
│  ├─ design/                                    原型设计与验证要求
│  ├─ decisions/                                 技术基线和架构决策记录
│  └─ reference/interfaces/                      接口PDF、Excel及配套图片原始资料
├─ tools/                                        构建和验证辅助脚本
│  ├─ build.sh / build.ps1                       macOS/Linux及Windows统一构建入口
│  ├─ verify-sql-migrations.mjs                  SQL主版本、补丁、命名和注释门禁
│  └─ verify.sh / verify.ps1                     macOS/Linux及Windows统一验证入口
├─ .github/workflows/ci.yml                      后端与前端持续集成流程
├─ AGENTS.md                                     仓库级AI协作与工程约束
├─ .gitignore                                    依赖、构建产物和本地文件忽略规则
├─ LICENSE                                       仓库许可证
├─ package.json                                  npm workspace及统一命令
├─ package-lock.json                             npm依赖锁定文件
└─ README.md                                     项目入口和开发说明
```

Java基础包统一为`cn.zqkj.platform`。`foundation`只承载跨模块技术能力，`system`承载平台自身接口，`modules`按业务边界组织代码。业务模块内部依赖方向为`api -> application -> domain`，`infrastructure`实现应用层或领域层声明的技术边界；模块之间通过应用层协作，不直接调用其他模块的Mapper或写入其数据表。详细约束见[平台架构决策](docs/decisions/架构决策.md)。

当前已经落地`foundation`、`system`和`exchange`查询链路。机构、患者标识、接口登记、对账、告警和审计模块保留为目标边界，待正式需求、接口合同和实际代码明确后再创建。管理端除运行总览的后台健康检查和双角色交互原型外，其他业务视图仍不代表真实接口已经接入。

## 开始开发

前置环境：JDK 17、Maven 3.9、Node.js 22、npm 11。数据库统一使用 SQL Server 2012 SP4，并安装医院批准的最新可用安全/GDR更新。

```bash
npm install
./tools/verify.sh
```

后台启动前必须通过环境变量提供独立平台数据库连接，禁止把密码写入仓库：

```bash
export PLATFORM_DB_URL='jdbc:sqlserver://127.0.0.1:1433;databaseName=county_integration;encrypt=true;trustServerCertificate=true'
export PLATFORM_DB_USERNAME='platform_app'
export PLATFORM_DB_PASSWORD='<由安全渠道提供>'
mvn -f ./backend/pom.xml spring-boot:run
```

SQL Server 2012 只能部署在受支持的 Windows Server 环境，不能使用本项目原有的 SQL Server Linux 容器。平台库可以与HIS库位于同一台服务器或同一实例，但必须使用独立数据库、读写账号、备份和维护计划；县医院HIS读取平台库视图使用独立只读账号。数据库实例、`county_integration` 数据库、兼容级别 110、TLS及备份恢复策略须由医院 DBA 按 [部署说明](deploy/README.md) 准备；Compose 只启动后台并连接外部数据库。

管理端和嵌入端分别启动：

```bash
npm run dev --workspace web-admin
npm run dev --workspace web-embed
```

## 当前实现范围

- 已建立模块目录、统一响应、请求编号、默认拒绝的安全入口和健康检查。
- 已将交换运行记录收敛为一个业务主版本SQL；尚未确认的接口登记和审计业务不预建数据库对象。
- 已建立交换运行记录的最小写入与受控查询、OpenAPI基线和前端真实连接状态；写入只接受脱敏摘要及成功、失败、无响应三类最终结果。
- 已建立工程决策、私有化部署和CI检查入口。

以下内容尚未完成，不能据此宣称接口已接入：医院身份认证方式、75项接口的正式合同和映射、真实脱敏样例、测试和生产网络、系统凭证、患者匹配规则及各业务完整过程。

## 接口开发条件

每项接口必须先具备正式文件、脱敏样例、网络条件、凭证、双方负责人和验收场景。条件不足时不得进入具体适配，不得猜测字段或使用演示结果代替联调证据。接口确认和推进台账由项目管理材料统一维护，不纳入工程仓库。

项目范围、接口准入、实施顺序和放行依据见[总体方案](docs/plans/总体方案.md)，原型页面及启动前验证见[原型设计与验证](docs/design/原型设计与验证.md)。工程执行遵守[工程建设规范](docs/standards/engineering-guidelines.md)、[Java 与 AI Coding 规范](docs/standards/java-coding-guidelines.md)和[SQL Server 建设与迁移脚本规范](docs/standards/sql-coding-guidelines.md)。统一验证会执行Java静态规则、SQL主版本与补丁门禁、前端类型检查和构建。

## 项目资料

项目资料已收敛为总体方案、原型设计与验证、工程规范、Java与AI Coding规范、架构决策以及上游接口资料。除非另有明确说明，项目不生成 Office 或 PDF 文档。
