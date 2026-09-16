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
- Java基础包统一为`cn.zqkj.platform`；平台基础能力放入`foundation`和`system`，业务代码放入`modules/<业务模块>`。
- 业务模块按需使用`api`、`application`、`domain`和`infrastructure`分层，不创建空包占位，不引入无边界的`shared`、`common`或`utils`包。
- 所有新增或修改的类、接口、枚举、`record` 和方法必须添加准确的 Javadoc，并随实现同步更新。
- AI 生成内容必须经过需求、权限、数据安全、兼容性、测试和实际差异检查；不得编造接口合同、环境条件或验证结果。
