# 开发环境临时 TLS 兼容方案

| 项目 | 当前约定 |
| --- | --- |
| 适用阶段 | 本项目开发与本机联调阶段 |
| 数据库入口 | `127.0.0.1:14330`，由 FRP 转发到独立内网环境 |
| 数据库版本 | SQL Server 2012 SP4，兼容级别 110 |
| Java | Java 17 |
| 临时协议 | TLS 1.0 |
| 退出条件 | 目标服务器能够与 Java 17 协商 TLS 1.2 |
| 禁止范围 | 测试验收、预生产和生产环境 |

## 1. 决策

开发阶段统一通过 `tools/run-backend-dev.sh` 启动后端。启动器只对当前工程进程追加 Java 安全策略，不修改 JDK 全局配置、macOS 系统 TLS 配置、FRP 配置或 SQL Server 实例配置。

开发 JDBC 参数固定为：

```text
jdbc:sqlserver://127.0.0.1:14330;
databaseName=county_integration;
encrypt=true;
trustServerCertificate=true;
sslProtocol=TLSv1;
loginTimeout=5
```

该方式仍加密 JDBC 会话，但使用已经淘汰的 TLS 1.0，并跳过服务器证书身份校验，只能作为当前开发阻塞的临时兼容措施。

## 2. 首次配置

复制非敏感模板：

```bash
cp deploy/.env.dev.example deploy/.env.dev.local
chmod 600 deploy/.env.dev.local
```

通过安全渠道填写：

- `PLATFORM_DB_PASSWORD`：`platform_app` 的开发环境密码。
- `PLATFORM_DB_MIGRATION_PASSWORD`：`platform_migration` 的开发环境密码。
- `PLATFORM_BOOTSTRAP_SECRET`：仅空库首次初始化时设置；初始化完成后清空。

`deploy/.env.dev.local` 已被 Git 忽略，禁止提交、粘贴到普通工单或写入项目文档。平台日常运行不得使用 `sa`。

## 3. 日常启动

后端统一执行：

```bash
./tools/run-backend-dev.sh
```

管理端执行：

```bash
VITE_BACKEND_TARGET=http://127.0.0.1:18080 npm run dev --workspace web-admin
```

如本机端口冲突，在 `deploy/.env.dev.local` 修改 `PLATFORM_SERVER_PORT`，并同步修改 `VITE_BACKEND_TARGET`。

## 4. 迁移控制

- 日常开发保持 `PLATFORM_DB_MIGRATION_ENABLED=false`。
- 只有需要执行已评审迁移时，才临时设为 `true`，同时配置迁移账号。
- 迁移前必须运行 `node tools/verify-sql-migrations.mjs`。
- 迁移完成并核对 `flyway_schema_history` 后立即恢复为 `false`。
- 应用连接只使用 `platform_app`，不得为了方便改用 `sa` 或迁移账号。

## 5. IDEA 启动约定

直接从 IDEA 启动 `PlatformApplication` 时，必须与脚本保持相同配置：

```text
-Djava.security.properties=<项目绝对路径>/deploy/dev/sqlserver2012-legacy-tls.java.security
-Djdk.tls.client.protocols=TLSv1
```

环境变量从本机 `deploy/.env.dev.local` 对应项录入。不得将密码写入共享 Run Configuration。优先使用脚本启动，避免 IDEA 配置漂移。

## 6. 防误用边界

启动脚本遇到以下任一情况会拒绝运行：

- `PLATFORM_ENVIRONMENT` 不是 `development`。
- Spring profile 包含 `prod` 或 `production`。
- JDBC 地址不是 `127.0.0.1:14330`。
- 未显式设置 `encrypt=true`、`trustServerCertificate=true` 和 `sslProtocol=TLSv1`。
- 运行账号密码缺失或仍为模板占位值。

生产环境继续遵守《生产部署与实施方案》：使用 TLS 1.2、正式 DNS、受信任服务器证书和 `trustServerCertificate=false`。本方案不得反向修改生产基线。

## 7. 验证标准

每次环境重建或驱动升级后至少确认：

1. `/actuator/health` 返回 `UP`。
2. `county_integration` 兼容级别为 110。
3. `flyway_schema_history` 全部记录成功且版本符合当前代码。
4. 当前应用会话 `encrypt_option=TRUE`。
5. 管理端能够获取 CSRF、登录并读取当前会话。
6. 日志中只允许出现已知的 TLS 1.0 临时风险警告，不得出现密码、Token 或患者数据。
