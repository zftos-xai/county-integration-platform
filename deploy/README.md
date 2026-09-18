# 部署说明

## 数据库版本要求

平台数据库统一使用 Microsoft SQL Server 2012 SP4，数据库兼容级别为 110，并安装医院批准的最新可用安全/GDR更新。SQL Server 2012 已结束常规扩展支持和扩展安全更新，医院应将补丁状态、风险接受、网络隔离、备份恢复和后续升级计划纳入上线审批。

SQL Server 2012 不支持 Microsoft SQL Server Linux 容器。`docker-compose.yml` 只构建和启动后台服务，数据库由医院 DBA 在 Windows Server 或医院现有 SQL Server 2012 SP4 实例上提供。平台数据库可以与县医院HIS数据库位于同一台服务器或同一实例，但必须使用独立数据库、账号、备份和维护计划。

## DBA 准备事项

上线或联调前由医院 DBA 完成以下事项：

1. 核对数据库引擎为 SQL Server 2012 SP4，并记录 `SERVERPROPERTY('ProductVersion')`、`SERVERPROPERTY('ProductLevel')` 和已安装安全更新。
2. 按[`sqlserver/README.md`](sqlserver/README.md)创建独立的 `county_integration` 数据库，将兼容级别设置为 110；不得在 HIS 业务库中创建平台表。
3. 创建独立登录名和数据库用户，通过安全渠道交付凭证。应用账号只能访问平台数据库，结构迁移权限与日常运行权限应按医院制度审批。
4. 启用 TCP/IP，固定监听端口，并仅向获批的平台服务器开放网络访问。
5. 配置 SQL Server 2012 所在 Windows Server 是否支持 TLS 1.2，并配置证书。联调可以临时使用 `trustServerCertificate=true`，生产环境应使用医院信任的证书并改为 `trustServerCertificate=false`。
6. 建立独立备份、完整性检查、容量监控和恢复演练计划。
7. 为县医院HIS读取平台库中的基层基础数据创建独立只读登录名和用户，仅授予已批准平台库视图的`SELECT`权限；不得复用平台应用读写账号，也不得授予平台基础表写权限。
8. 与县医院HIS厂商确认每个视图的用途、字段、过滤范围、查询频率、单次上限、允许时段、版本兼容和性能边界，并验证查询负载不影响平台同步与HIS主业务。

建议逻辑隔离如下：

```text
SQL Server 2012 SP4服务器或实例
├─ HIS业务数据库            县医院HIS管理；不向平台开放数据库读写
└─ county_integration       平台管理；保存基层基础数据并向HIS开放受控只读视图
```

平台应用读写账号、HIS视图运行只读账号和视图DDL变更账号相互分离。县医院HIS通过同实例部署也不能获得平台基础表写权限或其他平台对象权限。

可使用以下语句核对版本和数据库兼容级别：

```sql
SELECT
    SERVERPROPERTY('ProductVersion') AS product_version,
    SERVERPROPERTY('ProductLevel') AS product_level,
    SERVERPROPERTY('Edition') AS edition;

SELECT name, compatibility_level
FROM sys.databases
WHERE name = N'county_integration';
```

## 后台连接

复制 `.env.example` 为 `.env`，把主机名、迁移账号、运行账号和密码替换为医院实际配置，再运行：

```powershell
docker compose --env-file ./deploy/.env -f ./deploy/docker-compose.yml up --build
```

首次启用 Flyway 前，应在 SQL Server 2012 SP4 测试实例上验证空库迁移和从上一版本升级。生产执行迁移前必须完成备份并保留执行记录。

## 首次安全引导

平台本地管理身份不提供固定默认账号或密码。首次启动前生成至少32个字符的一次性随机值，通过`PLATFORM_BOOTSTRAP_SECRET`交付。管理端应先调用`GET /api/v1/session/csrf`取得CSRF令牌，再调用一次`POST /api/v1/bootstrap`创建首个机构和管理员。

安全引导成功后必须从部署环境删除`PLATFORM_BOOTSTRAP_SECRET`并重启应用。初始管理员首次登录只能修改密码；修改成功后当前会话失效，重新登录后才取得管理权限。生产环境必须设置`PLATFORM_SESSION_COOKIE_SECURE=true`并通过HTTPS访问。
