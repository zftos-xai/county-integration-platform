# 县人民医院与基层卫生院中间接口平台

本目录是工程建设规范对应的首版项目骨架。平台部署在县人民医院侧，保存获批的基层机构基础数据；县医院HIS厂商在平台数据库上建立受控视图，由县医院HIS使用只读账号读取这些基础数据。其他业务通过平台按`PHIS_Interface`等正式接口交互。当前只建立公共工程能力，不表示75项业务接口、平台库视图或基础数据同步已经完成联调。

平台以“基层基础数据进入平台库并由县医院 HIS 通过受控视图只读获取”和“其他业务通过正式接口交互”两条链路运行。完整关系、边界和实施范围统一见[总体方案](docs/plans/总体方案.md)。

当前实施顺序为“平台管理底座 → 基础数据闭环 → 外部业务接口”。批次、依赖、任务、验收和阻断决策见[开发批次任务计划](docs/plans/开发批次任务计划.md)，平台管理模型、表写入权、迁移版本和安全引导边界见[平台管理底座技术设计](docs/design/平台管理底座技术设计.md)。

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
│     │  │  ├─ common/                           跨业务且职责明确的公共类型
│     │  │  │  ├─ core/                         统一API响应等核心传输类型
│     │  │  │  └─ exception/                    业务异常及全局异常转换
│     │  │  ├─ framework/                        Spring、安全和Web技术装配
│     │  │  │  ├─ config/                       安全等框架配置
│     │  │  │  ├─ security/                     登录主体、数据范围守卫及过滤器
│     │  │  │  └─ web/filter/                   请求编号等Web过滤器
│     │  │  ├─ system/                           机构、用户、角色和平台配置管理
│     │  │  │  ├─ controller/                   仅存放HTTP控制器
│     │  │  │  ├─ domain/
│     │  │  │  │  ├─ dto/                      请求DTO和服务命令
│     │  │  │  │  ├─ model/                    内部业务及持久化模型
│     │  │  │  │  └─ vo/                       API输出VO
│     │  │  │  ├─ mapper/                       仅存放MyBatis Mapper接口
│     │  │  │  └─ service/                      系统管理服务接口
│     │  │  │     └─ impl/                      系统管理规则和事务实现
│     │  │  └─ exchange/                         交换运行记录和后续接口交换能力
│     │  │     ├─ controller/                   交换HTTP入口
│     │  │     ├─ domain/
│     │  │     │  ├─ dto/                      交换查询DTO
│     │  │     │  ├─ model/                    交换内部运行事实
│     │  │     │  └─ vo/                       交换查询输出VO
│     │  │     ├─ mapper/                       仅存放交换MyBatis Mapper接口
│     │  │     └─ service/                      交换服务接口
│     │  │        └─ impl/                      交换规则和事务实现
│     │  └─ resources/
│     │     ├─ application.yml                  数据源、Flyway、MyBatis、服务及监控配置
│     │     ├─ db/migration/                    SQL Server版本化迁移脚本
│     │     └─ mapper/
│     │        ├─ system/                       系统管理MyBatis XML
│     │        └─ exchange/                     交换MyBatis XML
│     └─ test/java/cn/zqkj/platform/             镜像主代码包结构的后端测试
│        ├─ system/                              系统管理接口、服务和Mapper测试
│        ├─ framework/security/                  机构权限边界测试
│        └─ exchange/service/                    交换服务测试
├─ web-admin/                                    Vue 3 / TypeScript / Vite运行管理端
│  ├─ package.json                               管理端依赖和开发、构建命令
│  ├─ vite.config.ts                             Vite构建与开发服务配置
│  ├─ tsconfig.json                              TypeScript编译配置
│  ├─ index.html                                 管理端HTML入口
│  └─ src/
│     ├─ api/                                    会话及各业务域API请求
│     ├─ assets/                                 全局样式和静态资源
│     ├─ components/                             跨页面公共组件
│     ├─ layout/                                 登录后管理端应用外壳
│     ├─ router/                                 集中路由表
│     ├─ store/modules/                          会话等跨页面状态
│     ├─ utils/                                  请求和路由判定基础能力
│     ├─ views/                                  按业务域组织的正式页面与独立原型
│     ├─ App.vue                                 顶层路由出口
│     ├─ main.ts                                 管理端启动入口
│     ├─ permission.ts                           全局路由权限守卫
│     └─ settings.ts                             非敏感界面配置
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

Java基础包统一为`cn.zqkj.platform`。包结构参考RuoYi-Vue的直观分层并结合本项目规模，采用`controller / domain / mapper / service / service.impl`；`controller`只放Controller，`domain/dto`放输入对象，`domain/vo`放以`VO`结尾的输出对象，`domain/model`放内部模型，`mapper`只放MyBatis Mapper接口，XML统一位于`resources/mapper/<业务域>`。Service包定义业务接口，`service.impl`保存事务和规则实现，不再叠加Repository及MyBatis适配实现。详细约束见[平台架构决策](docs/decisions/架构决策.md)。

管理端同样参考 RuoYi-Vue 的直观职责分层，并按 Vue 3 与本项目实际范围裁剪；目录与注释要求见[前端编码与注释规范](docs/standards/frontend-coding-guidelines.md)。Vue 组件职责注释和 TypeScript 导出契约由 `node tools/verify-frontend-comments.mjs` 执行静态门禁。

当前已经落地`common`、`framework`、`system`和`exchange`。患者标识、接口登记、对账、告警和审计能力待正式需求、接口合同和实际代码明确后再创建，不使用空包预占。管理端除运行总览的后台健康检查和双角色交互原型外，其他业务视图仍不代表真实接口已经接入。

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
mvn -f ./backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

SQL Server 2012 只能部署在受支持的 Windows Server 环境，不能使用本项目原有的 SQL Server Linux 容器。平台库可以与HIS库位于同一台服务器或同一实例，但必须使用独立数据库、读写账号、备份和维护计划；县医院HIS读取平台库视图使用独立只读账号。数据库实例、`county_integration` 数据库、兼容级别 110、TLS及备份恢复策略须由医院 DBA 按 [部署说明](deploy/README.md) 准备；Compose 只启动后台并连接外部数据库。

管理端和嵌入端分别启动：

```bash
VITE_BACKEND_TARGET=http://127.0.0.1:18080 npm run dev --workspace web-admin
npm run dev --workspace web-embed
```

管理后端本地开发端口默认使用`18080`，避免与机器上常见的`8080`服务冲突；管理端开发代理也默认指向`http://127.0.0.1:18080`，仅在环境不同时通过`VITE_BACKEND_TARGET`覆盖。

### 当前本地开发管理账号

| 项目 | 内容 |
| --- | --- |
| 登录地址 | `http://127.0.0.1:5173/login` |
| 账号 | `admin` |
| 临时密码 | `Local#Manage2026!` |
| 机构 | `HOSPITAL.001` |
| 角色 | 平台管理员 |

该账号仅对当前本机开发数据库有效，不是数据库迁移自动创建的通用默认账号，严禁用于测试、预生产或生产环境。首次登录必须修改密码；密码修改后，应立即同步更新或删除本节，避免文档与开发环境状态不一致。

## 当前实现范围

- 已建立模块目录、统一响应、请求编号、默认拒绝的安全入口和健康检查。
- 已将交换运行记录收敛为一个业务主版本SQL；尚未确认的接口登记和审计业务不预建数据库对象。
- 已建立交换运行记录的最小写入与受控查询、OpenAPI基线和前端真实连接状态；写入只接受脱敏摘要及成功、失败、无响应三类最终结果。
- 已建立工程决策、私有化部署和CI检查入口。

以下内容尚未完成，不能据此宣称接口已接入：医院身份认证方式、75项接口的正式合同和映射、真实脱敏样例、测试和生产网络、系统凭证、患者匹配规则及各业务完整过程。

## 接口开发条件

每项接口必须先具备正式文件、脱敏样例、网络条件、凭证、双方负责人和验收场景。条件不足时不得进入具体适配，不得猜测字段或使用演示结果代替联调证据。接口确认和推进台账由项目管理材料统一维护，不纳入工程仓库。

项目范围、接口准入、实施顺序和放行依据见[总体方案](docs/plans/总体方案.md)，原型页面及启动前验证见[原型设计与验证](docs/design/原型设计与验证.md)。工程执行遵守[工程建设规范](docs/standards/engineering-guidelines.md)、[Java 与 AI Coding 规范](docs/standards/java-coding-guidelines.md)、[前端工程与 AI Coding 规范](docs/standards/frontend-coding-guidelines.md)和[SQL Server 建设与迁移脚本规范](docs/standards/sql-coding-guidelines.md)。统一验证会执行Java静态规则、SQL主版本与补丁门禁、前端类型检查和构建。

## 项目资料

项目资料已收敛为总体方案、原型设计与验证、工程规范、Java与AI Coding规范、架构决策以及上游接口资料。除非另有明确说明，项目不生成 Office 或 PDF 文档。
