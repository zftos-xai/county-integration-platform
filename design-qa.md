# DEV-010 登录原型 Design QA

此前的告警管理抽屉验收记录已保留在 `docs/verification/design-qa-alert-history.md`。

- Source visual truth: `docs/verification/assets/dev010-source-prototype.jpg`
- Implementation screenshot: `docs/verification/assets/dev010-login-prototype.jpg`
- Combined comparison: `docs/verification/assets/dev010-login-comparison.jpg`
- Viewport: Codex in-app browser，约 `699 × 908` CSS px
- Source pixels: `699 × 908`，device scale factor 1
- Implementation pixels: `699 × 908`，device scale factor 1
- State: source为现有外部系统原型的窄屏状态；implementation为未认证登录入口。两者不是同一业务页面，因此只比较产品设计系统、视觉层级和响应式密度，不做页面结构一比一判断。

## Full-view comparison evidence

现有原型与登录页共同使用浅灰页面背景、白色内容面板、低强度阴影、绿色主操作、4–6px圆角、紧凑中文字号和线性图标。登录页在当前窄屏浏览器中隐藏说明侧栏，将主任务集中为单列表单，没有横向溢出或被遮挡的持续操作。

## Focused region comparison evidence

登录卡片与现有原型的页头、提示卡、按钮和输入控件具有一致的边框颜色、焦点色、图标尺度和文本层级。无需额外裁切：当前全屏截图中登录标题、字段、主按钮、演示账号和安全说明均清晰可读。

## Findings

- 无P0、P1或P2问题。
- P3：宽屏两栏说明区域未在当前固定宽度的in-app浏览器中截图，但对应断点、布局和构建检查已通过；这不影响当前可见窄屏入口。

## Interaction evidence

- 管理员演示账号可填充并进入运行总览。
- 登录后可以退出并返回登录入口。
- 首次登录账号会进入强制修改初始密码流程。
- 改密后显示会话已注销提示，并要求使用新密码重新登录。
- 新密码重新登录成功。
- 浏览器控制台无错误，仅有Vite连接和热更新调试消息。

## Comparison history

- 首次比较未发现需要修复的P0、P1或P2差异，因此没有后续阻断修复迭代。

## Implementation checklist

- [x] 登录、错误、首次改密、重新登录和退出流程可操作。
- [x] 密码不写入localStorage或原型持久化状态。
- [x] 视觉样式沿用现有原型设计系统。
- [x] 键盘表单语义、标签和状态提示可访问。
- [x] 窄屏布局无横向溢出。

final result: passed
