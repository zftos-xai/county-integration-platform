# Team Engineering Standards

团队工程规范插件，用于审计项目治理基线、选择适用技术栈规则、补齐 CI 门禁，并指导规范在其他项目分阶段落地。

## 安装

在仓库根目录执行：

```bash
codex plugin marketplace add "$(pwd)"
codex plugin add team-engineering-standards@county-integration-team
```

安装后新建 Codex 任务，通过 `$project-engineering-guardrails` 发起审计或推广。没有 Codex 的项目仍可直接采用仓库内 Markdown 规范和统一验证命令。

## 使用方式

```text
使用 $project-engineering-guardrails 审计当前项目，区分通用规则、技术栈规则和项目覆盖项，并修复可自动处理的问题。
```

只读运行仓库基线审计：

```bash
python3 skills/project-engineering-guardrails/scripts/audit_repository.py /path/to/project
```

在 CI 中要求基线完整：

```bash
python3 skills/project-engineering-guardrails/scripts/audit_repository.py /path/to/project --strict
```

## 维护

- 插件遵循语义化版本；规则行为变化必须更新版本和发布说明。
- 通用规则进入插件，项目专属要求留在项目 `AGENTS.md`。
- 每次修改后运行技能、插件和目标项目三类验证。

首次参与插件开发时安装固定校验依赖，然后运行统一校验：

```bash
python3 -m pip install -r requirements-dev.txt
./scripts/validate.sh
```

`requirements-dev.txt` 只服务插件开发校验，不会进入业务应用运行依赖。可通过 `PYTHON_COMMAND` 指定虚拟环境 Python，通过 `CODEX_HOME` 指定非默认 Codex 配置目录。
