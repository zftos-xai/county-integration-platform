# 仓库协作规则

## 文档格式

- 项目方案、立项材料、工程规范、工程决策和其他项目文档默认使用 Markdown。
- 除非用户或任务明确要求，不创建、更新或自动生成 Word、Excel、PowerPoint、PDF 等 Office/PDF 文档，也不新增对应生成脚本和依赖。
- `docs/reference/interfaces/` 是接口资料例外：保留上游提供的 PDF、Excel 和图片原始文件。

## 数据库基线

- 数据库统一为 Microsoft SQL Server 2012 SP4，兼容级别 110，并安装医院批准的最新可用安全/GDR 更新。
- SQL Server 2012 使用医院提供的 Windows Server 或现有实例，不配置 SQL Server Linux 容器。
- 数据库语法、迁移、驱动、事务、锁、TLS、备份与恢复必须按 SQL Server 2012 SP4 验证。
- SQL修改必须遵守`docs/standards/sql-coding-guidelines.md`：一个业务一个主版本；主版本未进入共享环境时不得新增补丁，必须收敛回主版本。
- 已进入共享环境后的补丁必须引用归属主版本、连续编号并通过`node tools/verify-sql-migrations.mjs`；表、字段、约束、索引和时间口径必须有准确注释。

## Java 与 AI Coding

- Java、MyBatis 和数据库修改必须遵守 `docs/standards/java-coding-guidelines.md`。
- Java基础包统一为`cn.zqkj.platform`；采用易于定位的`controller / domain / mapper / service / service.impl`分包。`controller`只放Controller，输入对象放`domain/dto`，API输出对象放`domain/vo`并使用`VO`后缀，内部模型放`domain/model`。`mapper`只放MyBatis Mapper接口，不增加Repository或MyBatis适配实现，XML放`resources/mapper/<业务域>`。Controller依赖Service接口，事务和业务规则由`service.impl`实现。
- 不再使用`modules/<模块>/api/application/infrastructure`多重目录。`common`只能包含跨业务且边界明确的基础类型，不得演变为通用工具或业务规则堆放区；不创建空包占位，不引入无边界的`shared`或`utils`包。
- 所有新增或修改的类、接口、枚举、`record` 和方法必须添加准确的 Javadoc，并随实现同步更新。
- AI 生成内容必须经过需求、权限、数据安全、兼容性、测试和实际差异检查；不得编造接口合同、环境条件或验证结果。

## 前端开发

- Vue、TypeScript 和前端样式修改必须遵守 `docs/standards/frontend-coding-guidelines.md`。
- 管理端目录参考 RuoYi-Vue 的直观分层，统一使用 `api / assets / components / layout / router / store / utils / views`；业务页面及其表单模型按领域就近放在 `views/<领域>/<业务>`，不得再次把业务文件堆到 `src` 根目录。
- 所有导出的 TypeScript 类型、类、常量和函数必须使用准确的 TSDoc 说明职责或契约；Vue 单文件组件必须在文件顶部说明页面或组件职责。权限、会话、并发控制、敏感数据和兼容性等非显然逻辑必须解释“为什么”，不得用逐行翻译代码的无效注释充数。
- 正式页面、嵌入页面和合成原型的认证及业务状态必须隔离；前端权限、路由守卫和表单校验不得替代服务端授权、机构范围及数据校验。
- TypeScript保持严格模式，禁止使用`any`、`@ts-ignore`或不安全断言掩盖合同问题；API变化必须同步类型、调用方、错误处理和测试。
- 前端不得保存或输出密码、Token、Cookie、患者正文、完整身份信息及其他敏感数据；`VITE_*`变量一律按公开信息处理。
- 前端 AI 生成内容必须检查加载、空数据、失败、无权限、会话失效、并发冲突、可访问性、响应式和真实差异，不得用原型或模拟结果冒充联调证据。
- 前端修改必须通过 `node tools/verify-frontend-comments.mjs`、类型检查、自动化测试和生产构建。
