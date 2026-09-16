# SQL Server 2012 Express 建库基线

本目录用于在医院提供的 Windows Server 2012 R2、SQL Server 2012 SP4 Express 实例上建立平台独立数据库。脚本不包含服务器地址或真实凭证，也不操作 HIS 业务数据库。

## 1. 建库前确认

由 DBA 在 SQL Server Configuration Manager 和 Windows 防火墙中完成以下事项：

1. 安装 SQL Server 2012 SP4 及医院批准的最新可用安全/GDR 更新，记录版本、补丁和实例名。
2. 确认数据库引擎身份验证模式。`sa`只用于首次管理，不作为平台连接账号；初始化结束后轮换其密码，并按医院制度决定是否停用。
3. 启用 TCP/IP，配置固定端口，重启 SQL Server 服务；防火墙只允许获批平台服务器访问该端口。
4. 确认服务器具备 TLS 1.2 能力。联调阶段使用自签名证书时必须记录例外；生产应使用医院信任证书。
5. 确认数据文件、日志文件、备份目录的磁盘容量和访问权限。
6. 确认 Express 版本的容量、资源和运维限制满足当前环境；Express 不提供 SQL Server Agent，备份、完整性检查和监控需使用医院运维工具或 Windows 任务计划程序。

不得把真实服务器地址、账号或密码写入本目录、提交记录、执行日志或普通工单。

## 2. 账号与权限关系

| 登录名 | 用途 | 数据库权限 | 使用时段 |
| --- | --- | --- | --- |
| `platform_migration` | Flyway 结构迁移 | DDL及迁移所需数据读写 | 发布窗口 |
| `platform_app` | 后端日常运行 | `dbo`架构的查询、写入和存储过程执行 | 应用运行期 |
| `platform_his_reader` | 县医院HIS读取基础数据视图 | 初始无对象权限，后续仅逐个批准视图授予`SELECT` | HIS读取期 |

三个账号必须使用不同密码。应用运行账号不能使用`sa`，HIS账号不能获得基础表读写权限。非交互式应用和HIS账号不启用SQL Server自动密码过期，必须纳入医院凭证轮换制度；迁移账号在发布窗口使用并启用密码过期。完成迁移后，可将`PLATFORM_DB_MIGRATION_ENABLED`设为`false`并按发布制度管控迁移账号。

## 3. 执行顺序

脚本应在数据库服务器本机通过受控管理员会话执行。示例使用默认 Express 实例；实际实例名不同时由 DBA 替换。

1. 以 SQL Server 系统管理员身份执行建库和角色脚本：

   ```powershell
   sqlcmd -S .\SQLEXPRESS -E -b -i .\01-create-database.sql
   ```

2. 复制账号模板为不会提交的本地文件，设置仅管理员可读权限，并为三个登录名填写互不相同的强密码：

   ```powershell
   Copy-Item .\02-create-logins.template.sql .\02-create-logins.local.sql
   sqlcmd -S .\SQLEXPRESS -E -b -i .\02-create-logins.local.sql
   ```

   执行后安全删除本地凭证文件；不要把其内容复制到聊天、构建日志或版本库。如果不能使用 Windows 集成认证，DBA可通过医院批准的安全方式执行脚本，但不得把管理员口令写入命令历史或脚本。

3. 执行只读核对脚本并保存输出作为 DEV-003/DEV-011 的数据库证据：

   ```powershell
   sqlcmd -S .\SQLEXPRESS -E -b -i .\03-verify-baseline.sql -o .\database-baseline-check.txt
   ```

4. 在部署主机的`deploy/.env`中配置实际 JDBC 地址及两类应用凭证。迁移账号填入`PLATFORM_DB_MIGRATION_*`，运行账号填入`PLATFORM_DB_*`。该文件已被 Git 忽略。

5. 首次发布运行 Flyway 后再次执行核对脚本，并补充空库迁移、应用连接、TLS、备份和恢复验证记录。当前脚本仅提供建库基线，不能代替 SQL Server 2012 SP4 真实验证。

## 4. 数据库恢复与变更边界

- 数据库初建失败且尚无业务数据时，由 DBA 删除失败数据库后重新执行；删除前必须确认目标仅为`county_integration`。
- Flyway 脚本一旦进入共享环境不得改写，后续迁移遵守项目 SQL 规范。
- 平台表由平台迁移维护；HIS厂商提供的受控视图必须经过字段、范围、性能和授权评审后单独版本化。
- 生产启用前至少完成一次可复核的完整备份与恢复演练。Express 环境的计划任务必须验证实际执行账号、退出码、输出日志和失败告警。
