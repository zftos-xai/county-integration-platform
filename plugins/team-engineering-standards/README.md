# Team Engineering Standards

团队工程规范插件，用于检查项目是否遵守当前工程规则、选择适用的技术规则、补齐持续集成（CI）自动检查，并指导其他项目分阶段采用这些规则。

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

只读检查仓库当前是否符合规则：

```bash
python3 skills/project-engineering-guardrails/scripts/audit_repository.py /path/to/project
```

在持续集成（CI）中把不符合规则的情况作为失败处理：

```bash
python3 skills/project-engineering-guardrails/scripts/audit_repository.py /path/to/project --strict
```

## 维护

- 插件版本号按变更影响编号；规则行为变化必须更新版本和发布说明。
- 通用规则进入插件，项目专属要求留在项目 `AGENTS.md`。
- 每次修改后运行技能、插件和目标项目三类验证。

首次参与插件开发时安装固定校验依赖，然后运行统一校验：

```bash
python3 -m pip install -r requirements-dev.txt
./scripts/validate.sh
```

`requirements-dev.txt` 只服务插件开发校验，不会进入业务应用运行依赖。可通过 `PYTHON_COMMAND` 指定虚拟环境 Python，通过 `CODEX_HOME` 指定非默认 Codex 配置目录。
