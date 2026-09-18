# 县人民医院与基层卫生院中间接口平台

本目录是工程建设规范对应的首版项目骨架。平台部署在县人民医院侧，保存获批的基层机构基础数据；县医院HIS厂商在平台数据库上建立受控视图，由县医院HIS使用只读账号读取这些基础数据。其他业务通过平台按`PHIS_Interface`等正式接口交互。当前只建立公共工程结构，不表示75项业务接口、平台库视图或基础数据同步已经完成联调。

平台以“基层基础数据进入平台库并由县医院 HIS 通过受控视图只读获取”和“其他业务通过正式接口交互”两条数据传递过程运行。完整关系、边界和实施范围统一见[总体方案](docs/plans/总体方案.md)。

当前实施顺序为“平台基础管理功能 → 基础数据从来源系统到平台库再到 HIS 读取 → 外部业务接口”。批次、依赖、任务、验收和暂停条件见[开发批次任务计划](docs/plans/开发批次任务计划.md)，平台管理模型、表写入权、迁移版本和安全引导边界见[平台基础管理功能技术设计](docs/design/平台基础管理功能技术设计.md)。

## 当前技术选型和版本

- 后台：Java 17、Spring Boot 3.5、MyBatis
- 数据库：Microsoft SQL Server 2012 SP4，安装医院批准的最新可用安全/GDR更新，使用独立平台数据库和账号
- 页面：Vue 3、TypeScript，分为管理端和嵌入端
- 部署：医院机房或私有云
- 数据：经批准的基层机构、人员、科室和目录基础数据保存到平台库；运行记录和审计信息最小化留存；业务正文默认不留存，确需暂存时逐接口审批、加密并按期清理

## 工程目录说明

项目采用一个Maven后端和两个npm workspace前端。以下目录树同时展示当前已实现的结构和确定的目标模块；标记“按需创建”的目录只在开始实现相应功能时建立，不使用空包占位。`node_modules/`、`backend/target/`和前端`dist/`属于依赖或构建产物，不纳入工程结构。

```text
.
├─ backend/                                      Java 17 / Spring Boot 3.5 / MyBatis模块化单体
│  ├─ pom.xml                                    后端依赖、构建插件及Java版本约束
│  ├─ checkstyle.xml                             Java静态规则和Javadoc强制检查
│  └─ src/
│     ├─ main/
│     │  ├─ java/cn/zqkj/platform/               Java基础包
│     │  │  ├─ PlatformApplication.java          应用启动和组件扫描入口
│     │  │  ├─ common/                           跨业务且职责明确的公共类型
│     │  │  │  ├─ core/                         统一API响应等核心传输类型
│     │  │  │  └─ exception/                    业务异常及全局异常转换
│     │  │  ├─ framework/                        Spring、安全和Web技术装配
│     │  │  │  ├─ config/                       安全等框架配置
│     │  │  │  ├─ security/                     登录用户、数据范围守卫及过滤器
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
│     │  │  └─ exchange/                         交换运行记录和后续接口交换功能
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
│     ├─ router/                                 集中页面地址表
│     ├─ store/modules/                          会话等跨页面状态
│     ├─ utils/                                  请求和页面访问判定函数
│     ├─ views/                                  按业务域组织的正式页面与独立原型
│     ├─ App.vue                                 顶层页面内容出口
│     ├─ main.ts                                 管理端启动入口
│     ├─ permission.ts                           全局页面访问权限检查
│     └─ settings.ts                             非敏感界面配置
├─ web-embed/                                    Vue 3 / TypeScript / Vite工作站嵌入端
│  ├─ package.json                               嵌入端依赖和开发、构建命令
│  ├─ vite.config.ts                             Vite构建与开发服务配置
│  ├─ tsconfig.json                              TypeScript编译配置
│  ├─ index.html                                 嵌入端HTML入口
│  └─ src/
│     ├─ main.ts                                 嵌入端启动入口
│     ├─ App.vue                                 等待安全登录信息的入口页面
│     └─ styles/                                 嵌入端基础样式
├─ interface-specs/                                    机器可读接口规范及受控样例
│  ├─ 接口开发前检查表.md           单项接口开发前必填信息模板
│  ├─ openapi/platform-api-v1.yaml               当前管理 API 的 OpenAPI 接口定义（机器可读的 HTTP 接口说明）
│  ├─ mappings/README.md                         获批字段和编码映射的存放规则
│  └─ samples/README.md                          正式已删除或遮盖敏感字段的样例存放规则
├─ deploy/                                       私有化部署配置
│  ├─ Dockerfile.backend                         后端容器镜像定义
│  ├─ docker-compose.yml                         连接外部SQL Server的后端编排
│  ├─ .env.example                               非敏感环境变量示例
│  └─ README.md                                  SQL Server 2012 SP4部署及验证说明
├─ docs/                                         总体方案、设计、规范、决策和接口资料
│  ├─ plans/                                     唯一总体方案
│  ├─ standards/                                 工程、Java与SQL Server建设规范
│  ├─ design/                                    原型设计与验证要求
│  ├─ decisions/                                 技术选型和版本和架构决策记录
│  └─ reference/interfaces/                      接口PDF、Excel及配套图片原始资料
├─ tools/                                        构建和验证辅助脚本
│  ├─ build.sh / build.ps1                       macOS/Linux及Windows统一构建入口
│  ├─ verify-sql-migrations.mjs                  SQL主版本、补丁、命名和注释自动检查
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

管理端同样参考 RuoYi-Vue 的直观职责分层，并按 Vue 3 与本项目实际范围裁剪；目录与注释要求见[前端编码与注释规范](docs/standards/frontend-coding-guidelines.md)。Vue 组件职责注释和 TypeScript 导出声明说明由 `node tools/verify-frontend-comments.mjs` 执行自动检查。

当前已实现`common`、`framework`、`system`和`exchange`。患者标识、接口登记、对账、告警和审计功能待正式需求、接口规范和实际代码明确后再创建，不使用空包预占。管理端除运行总览的后台健康检查和双角色交互原型外，其他业务视图仍不代表真实接口已经接入。

## 开始开发

前置环境：JDK 17、Maven 3.9、Node.js 22、npm 11。数据库统一使用 SQL Server 2012 SP4，并安装医院批准的最新可用安全/GDR更新。

```bash
npm install
./tools/verify.sh
```

当前开发阶段统一使用 FRP 暴露的 SQL Server 2012 开发实例和临时 TLS 1.0 兼容启动器。先从模板创建只保存在本机的环境文件，并通过安全渠道填写平台账号密码：

```bash
cp deploy/.env.dev.example deploy/.env.dev.local
chmod 600 deploy/.env.dev.local
./tools/run-backend-dev.sh
```

启动器只对当前开发进程追加 Java 17 兼容策略，并强制检查开发标识、`127.0.0.1:14330` 和 TLS 参数；生产环境会被拒绝。具体边界、IDEA 配置和退出条件见[开发环境临时 TLS 兼容方案](docs/plans/开发环境临时TLS兼容方案.md)。

SQL Server 2012 只能部署在受支持的 Windows Server 环境，不能使用本项目原有的 SQL Server Linux 容器。平台库可以与HIS库位于同一台服务器或同一实例，但必须使用独立数据库、读写账号、备份和维护计划；县医院HIS读取平台库视图使用独立只读账号。数据库实例、`county_integration` 数据库、兼容级别 110、TLS及备份恢复策略须由医院 DBA 按 [部署说明](deploy/README.md) 准备；Compose 只启动后台并连接外部数据库。

管理端和嵌入端分别启动：

```bash
VITE_BACKEND_TARGET=http://127.0.0.1:18080 npm run dev --workspace web-admin
npm run dev --workspace web-embed
```

管理后端本地开发端口默认使用`18080`，避免与机器上常见的`8080`服务冲突；管理端开发代理也默认指向`http://127.0.0.1:18080`，仅在环境不同时通过`VITE_BACKEND_TARGET`覆盖。

### 当前本地开发管理账号

本机开发库已经完成一次性安全引导，登录地址为`http://127.0.0.1:5173/login`。账号和临时密码只通过安全渠道交付，不写入仓库；首次登录必须修改密码。该账号仅对当前开发数据库有效，严禁复用于测试、预生产或生产环境。

## 当前实现范围

- 已建立模块目录、统一响应、请求编号、默认拒绝的安全入口和健康检查。
- 已将交换运行记录收敛为一个业务主版本SQL；尚未确认的接口登记和审计业务不预建数据库对象。
- 已建立交换运行记录的最小写入与受控查询、OpenAPI 接口定义和前端真实连接状态；写入只接受不含敏感内容的摘要及成功、失败、无响应三类最终结果。
- 已建立工程决策、私有化部署和CI检查入口。

以下内容尚未完成，不能据此宣称接口已接入：医院身份认证方式、75项接口的已确认的接口规范和映射、真实已删除或遮盖敏感字段的样例、测试和生产网络、系统凭证、患者匹配规则及各业务完整过程。

## 接口开发条件

每项接口必须先具备正式文件、已删除或遮盖敏感字段的样例、网络条件、凭证、双方负责人和验收场景。条件不足时不得开始接口接入开发，不得猜测字段或使用演示结果代替联调记录。接口确认和推进台账由项目管理材料统一维护，不纳入工程仓库。

项目范围、接口开始开发的条件、实施顺序和放行依据见[总体方案](docs/plans/总体方案.md)，原型页面及启动前验证见[原型设计与验证](docs/design/原型设计与验证.md)。业务功能只有达到[业务功能完成与验收标准](docs/standards/业务功能完成与验收标准.md)后，才能称为“业务已完善”；页面还须执行[正式菜单完成标准](docs/standards/菜单功能完成检查.md)。工程执行遵守[工程建设规范](docs/standards/engineering-guidelines.md)、[Java 与 AI Coding 规范](docs/standards/java-coding-guidelines.md)、[前端工程与 AI Coding 规范](docs/standards/frontend-coding-guidelines.md)、[SQL Server 建设与迁移脚本规范](docs/standards/sql-coding-guidelines.md)和[团队工程规范管理办法](docs/standards/团队工程规范管理办法.md)。统一验证会执行 Java 静态规则、SQL 主版本与补丁强制检查、前端注释要求、类型检查、构建和自动测试。

跨项目复用通过仓库内的 [`team-engineering-standards`](plugins/team-engineering-standards/README.md) Codex 插件提供。插件用于审计和推广，真正的强制执行仍由项目配置、CI、评审所有权和分支保护共同完成。

## 项目资料

项目资料已收敛为总体方案、原型设计与验证、工程规范、Java与AI Coding规范、架构决策以及上游接口资料。除非另有明确说明，项目不生成 Office 或 PDF 文档。
