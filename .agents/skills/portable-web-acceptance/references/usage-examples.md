# Usage examples

Use these as starting prompts for this repository. Replace the feature, route, roles, and requirement source with confirmed project facts. A prompt does not supply missing API contracts or acceptance evidence; the skill must inspect the repository and state what remains unknown.

## Formal management menu review

```text
使用 $portable-web-acceptance 对管理端「机构管理」(/organizations) 做只读验收审查。
按当前正式需求和菜单交付标准，检查管理员、只读用户、无权限用户及直接访问 URL 的行为；重点看机构范围、401/403/409、网络失败、写入结果回读和审计证据。
先核对 menu-delivery-status.json 中的阶段、关联页面/API/测试和证据，再检查真实浏览器页面与相关自动化检查。不要写入数据或修改交付阶段。
输出需求到验收点的追踪表、页面布局/标准组件/全局主题三项检查、实际运行的命令及结果、未验证项和有证据支持的阶段判断。
```

The route and feature above are examples from the current repository. Confirm their current contracts, environment, and menu record before acting. A review prompt does not authorize a test write.

## Synthetic prototype review

```text
使用 $portable-web-acceptance 审查 /prototype 中的目录同步原型，目标是确认页面流程和设计，不是验证正式功能。
检查完整页面和主要交互状态、响应式与键盘操作，并对照已确认的原型决策；确认演示认证、模拟数据和正式管理端会话/API 保持隔离。
只做审查，不改代码、不调用真实 HIS、不写业务数据。报告原型本身的布局和交互发现，并单独列出哪些结论不能外推到正式页面或真实联调。
```

For this mode, do not treat a simulated account, response, permission, or saved record as production evidence.

## Embedded page acceptance

```text
使用 $portable-web-acceptance 为 web-embed/ 中的 [页面或组件] 制定并执行验收。
依据已确认的 HIS 宿主约定、入口参数、支持的浏览器、接口契约和角色范围检查。先找出当前仓库确实定义的宿主上下文和验证环境；未定义的内容列为待确认，不要猜测。
检查加载、空数据、失败、无权限、会话失效和适用的响应式/键盘状态。说明实际使用的浏览器、视口、构建和数据来源。没有授权的情况下不要更改 HIS、环境配置或真实数据。
```

If the HIS host or integration environment is unavailable, report the embed-specific checks as blocked or not-run; a standalone Vite preview cannot prove host integration.

## Authorized fix and retest

```text
使用 $portable-web-acceptance 处理 [已确认缺陷]，范围限定在 [页面/API/状态]。
修复已获授权。先记录当前工作区基线并追踪需求、调用链和复现步骤；保留已有改动。修复后在能安全复现的最低测试层补回归保护，执行项目要求的前端检查，并在目标浏览器复验 [角色、路线、状态、视口]。
不要使用生产或共享业务数据，不要借此升级依赖或调整正式菜单验收阶段。汇报修改、逐项验证证据、未覆盖场景和环境限制。
```

When using this pattern, follow the repository's frontend coding rules. It does not authorize database writes or stage changes; the user must explicitly include those operations in scope and the required evidence must exist.
