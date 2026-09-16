#!/usr/bin/env sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

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

"$maven_command" -B -f "$project_root/backend/pom.xml" clean package

cd "$project_root"
npm run build
