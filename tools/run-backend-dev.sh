#!/usr/bin/env sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
environment_file=${PLATFORM_DEV_ENV_FILE:-"$project_root/deploy/.env.dev.local"}
security_file="$project_root/deploy/dev/sqlserver2012-legacy-tls.java.security"
credential_key_file="$project_root/deploy/dev/.credential-encryption-key"

if [ ! -f "$environment_file" ]; then
    echo "缺少开发环境配置：$environment_file" >&2
    echo "请复制 deploy/.env.dev.example，并通过安全渠道填写平台数据库账号密码。" >&2
    exit 1
fi

if [ ! -f "$security_file" ]; then
    echo "缺少开发专用 Java TLS 安全策略：$security_file" >&2
    exit 1
fi

bootstrap_secret_override=${PLATFORM_BOOTSTRAP_SECRET:-}

set -a
# shellcheck disable=SC1090
. "$environment_file"
set +a

if [ -n "$bootstrap_secret_override" ]; then
    PLATFORM_BOOTSTRAP_SECRET=$bootstrap_secret_override
    export PLATFORM_BOOTSTRAP_SECRET
fi

if [ "${PLATFORM_DEV_RUN_MIGRATIONS:-false}" = "true" ]; then
    PLATFORM_DB_MIGRATION_ENABLED=true
    export PLATFORM_DB_MIGRATION_ENABLED
fi

if [ "${PLATFORM_ENVIRONMENT:-}" != "development" ]; then
    echo "临时 TLS 兼容启动器只允许 PLATFORM_ENVIRONMENT=development。" >&2
    exit 1
fi

case ",${SPRING_PROFILES_ACTIVE:-}," in
    *,prod,*|*,production,*)
        echo "临时 TLS 兼容启动器拒绝生产 profile。" >&2
        exit 1
        ;;
esac

case "${PLATFORM_DB_URL:-}" in
    *"127.0.0.1:14330"*"encrypt=true"*"trustServerCertificate=true"*"sslProtocol=TLSv1"*)
        ;;
    *)
        echo "开发 JDBC 地址必须指向 127.0.0.1:14330，并显式使用临时 TLSv1 兼容参数。" >&2
        exit 1
        ;;
esac

for required_name in PLATFORM_DB_USERNAME PLATFORM_DB_PASSWORD; do
    eval "required_value=\${$required_name:-}"
    case "$required_value" in
        ""|REPLACE_WITH_*)
            echo "开发环境变量 $required_name 尚未安全配置。" >&2
            exit 1
            ;;
    esac
done

if [ -z "${PLATFORM_CREDENTIAL_ENCRYPTION_KEY:-}" ]; then
    if [ ! -f "$credential_key_file" ]; then
        umask 077
        if command -v openssl >/dev/null 2>&1; then
            openssl rand -base64 48 > "$credential_key_file"
        else
            echo "缺少凭证加密密钥，且未找到openssl用于生成开发密钥。" >&2
            exit 1
        fi
    fi
    PLATFORM_CREDENTIAL_ENCRYPTION_KEY=$(tr -d '\r\n' < "$credential_key_file")
    export PLATFORM_CREDENTIAL_ENCRYPTION_KEY
fi

if command -v mvn >/dev/null 2>&1; then
    maven_command=$(command -v mvn)
elif [ -n "${MAVEN_HOME:-}" ] && [ -x "${MAVEN_HOME}/bin/mvn" ]; then
    maven_command="${MAVEN_HOME}/bin/mvn"
else
    user_directory=$(CDPATH= cd -- && pwd)
    maven_command="${user_directory}/.local/opt/maven/bin/mvn"
fi

if [ ! -x "$maven_command" ]; then
    echo "未找到 Maven。请将 mvn 加入 PATH，或设置 MAVEN_HOME。" >&2
    exit 1
fi

legacy_tls_options="-Djava.security.properties=$security_file -Djdk.tls.client.protocols=TLSv1"
if [ -n "${JAVA_TOOL_OPTIONS:-}" ]; then
    JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $legacy_tls_options"
else
    JAVA_TOOL_OPTIONS="$legacy_tls_options"
fi
export JAVA_TOOL_OPTIONS

echo "警告：正在使用仅限开发环境的 SQL Server TLS 1.0 兼容模式。" >&2
echo "数据库入口：127.0.0.1:14330；生产环境不得使用本启动器。" >&2

exec "$maven_command" \
    -f "$project_root/backend/pom.xml" \
    spring-boot:run \
    "-Dspring-boot.run.arguments=--server.port=${PLATFORM_SERVER_PORT:-18080}"
