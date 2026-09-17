#!/usr/bin/env sh
set -eu

plugin_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
python_command=${PYTHON_COMMAND:-python3}
codex_root=${CODEX_HOME:-"$($python_command -c 'from pathlib import Path; print(Path.home() / ".codex")')"}
skill_validator="$codex_root/skills/.system/skill-creator/scripts/quick_validate.py"
plugin_validator="$codex_root/skills/.system/plugin-creator/scripts/validate_plugin.py"
skill_root="$plugin_root/skills/project-engineering-guardrails"

if ! "$python_command" -c 'import yaml' >/dev/null 2>&1; then
    echo "缺少 PyYAML。请先执行：" >&2
    echo "$python_command -m pip install -r $plugin_root/requirements-dev.txt" >&2
    exit 2
fi

if [ ! -f "$skill_validator" ] || [ ! -f "$plugin_validator" ]; then
    echo "未找到 Codex 技能或插件校验器；请检查 CODEX_HOME。" >&2
    exit 2
fi

"$python_command" "$skill_validator" "$skill_root"
"$python_command" "$plugin_validator" "$plugin_root"
