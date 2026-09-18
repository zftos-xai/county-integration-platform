# 正式管理端布局 Design QA

- Source visual truth:
  - `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-e7081628-1b6e-4107-94ea-f042df539668.png`
  - `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-fa0756bf-127d-4775-8519-328a86713d7f.png`
  - `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-d4a76c9b-b7f1-49c0-abb0-faec3856f1d9.png`
  - `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-35ad6c6b-85ae-468d-b491-b1131758ca3d.png`
  - `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-a92285cd-af78-44e4-87ba-1ea9d12d504d.png`
  - `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-8ffc941e-a6d6-43f5-acad-b5933851983e.png`
  - `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-951c04b3-28ca-4b6d-a57b-09f3212361a5.png`
- Implementation route: `http://127.0.0.1:5173/parameters`
- Viewport: 1440 × 900 CSS px, DPR 1
- Source pixels: 870 × 1698, 1400 × 1514, focused target header crop 370 × 242, defect header crop 768 × 308, defect footer crop 600 × 414, annotated layout 1368 × 1700 and header reference 824 × 236
- State: authenticated desktop management shell with six real parameter rows
- Implementation evidence: current-run Chrome full-page capture in the browser QA session; the capture is inline evidence and has no persisted local file path.

## Full-view comparison evidence

- The formal shell uses a 280px fixed desktop sidebar, a 136px page context header and a fluid content column.
- Brand, grouped navigation, active teal route and authenticated user identity form the left rail. Product version and logout are separated into the main page footer.
- The top region now follows the selected reference's three-level rhythm: small `基础配置 / 参数配置` breadcrumb, large `参数配置` heading and a separate descriptive line. The product name remains only in the brand area.
- The parameter toolbar and five-column table remain inside the content width. At 1440px there is no document-level horizontal overflow.
- No prototype data or reference-screen copy was introduced; the page renders the six values returned by the local backend.

## Focused region comparison evidence

- Sidebar/brand: 280px wide, white surface, 42px teal mark, grouped navigation and an 82px bottom account block. The brand region follows the supplied 96px reference height.
- Header: 136px high with muted breadcrumb, 28px page heading and one descriptive line aligned directly to the content grid; the redundant desktop collapse control is absent.
- Active navigation: only `参数配置` is active; the root route no longer receives a false parent-active state.
- Footer: a 56px main-area footer contains product version on the left and logout on the right without competing with sidebar identity.

## Findings and iteration history

- Iteration 1 used a 260px sidebar and 88px header. Browser comparison found the shell too narrow and the root route incorrectly highlighted together with the current page.
- Iteration 2 moved the shell to 280px / 96px, corrected exact route activation and increased brand/navigation hierarchy.
- Iteration 3 removed the repeated product name from the page header and replaced it with the current module name, producing the correct `基础配置 / 参数配置` hierarchy.
- Iteration 4 separated that hierarchy into the focused reference's three visual rows and increased the header to 136px. The post-fix browser capture shows the breadcrumb, large title and description with clear spacing and no collision with the page toolbar.
- Iteration 5 corrected the follow-up defect evidence: the 96px brand/header boundary mismatch was removed by aligning both regions at 136px, and the sidebar footer was reduced from 178px to 148px with tighter 10–12px internal rhythm. The post-fix capture shows one continuous top divider and a compact, fully visible footer.
- Iteration 6 followed the annotated correction: restored the 96px independent brand header, removed desktop collapse controls, kept only account identity in the sidebar footer, and moved version/logout into a 56px global main footer. The post-fix capture shows the requested separation and direct header alignment.
- 高、中和低优先级问题剩余：无。
- 观察项：项目保留现有 Lucide 图标库和 Inter/PingFang/Microsoft YaHei 字体组合，不复制栅格化参考图标，也不引入无法获得的参考字体。

## Required fidelity surfaces

- Fonts and typography: passed; brand, module, page title, description, navigation and table levels are distinct.
- Spacing and layout rhythm: passed; the independent 96px brand and 136px context header match the supplied reference, while navigation, account block and global footer remain compact.
- Colors and visual tokens: passed; white shell, neutral canvas and restrained teal active states are consistent across routes.
- Image quality and asset fidelity: passed; the references require brand/interface symbols only, and the implementation uses crisp project-native vector icons.
- Copy and content: passed; real route titles, module context, descriptions, user identity, permissions and backend values are retained.

## Interaction and browser verification

- Signed in through the real local authentication flow; the password was used only for the session and was not written by this change.
- Verified the fixed desktop sidebar and retained mobile navigation trigger.
- Verified `/`, `/users`, `/roles`, `/organizations`, `/parameters` and `/dictionaries`; each route has exactly one active navigation item.
- Verified the parameter page at 1440 × 900 with sidebar width 280px, header height 136px and no horizontal overflow.
- Checked browser console output after navigation and interaction; no warnings or errors were present.
- `npm run verify:frontend` passed: comment gate, both workspace typechecks, both builds and all 60 frontend tests.

final result: passed

---

# 机构接口配置术语与提示复验

- 业务对象统一命名为“机构接口配置”，页面入口、抽屉标题、空状态、筛选、操作按钮、保存提示和未保存提醒不再把它称为“连接”。
- 正式页面已移除数据库字段名、交易码、查询参数处理、变量名和加密实现说明。
- 当前系统选择器仅显示业务名称；机构选择器仅显示机构名称，不再把稳定系统编码和长机构编码暴露给普通用户。
- 表单保留三段必要信息：机构与环境、HIS 接口地址、HIS 接入信息；登录账号和超时设置默认折叠。
- 已在本地正式页面复验入口、空状态和新增表单；移动宽度下字段无横向溢出，主操作为“保存配置”。

final result: passed

---

# 外部系统小白化配置复验

## 本次调整

- 删除面向实现人员的“凭证引用”录入方式，管理员不再理解或填写环境变量地址。
- 新增机构连接收敛为三个业务步骤：选择机构和环境、填写 HIS 地址、填写 HIS 接入信息。
- 地址说明直接对应原平台 `his_web_url`，HIS 验证码说明直接对应原平台 `his_auth_code`，并明确带有 `?op=PHIS_Interface` 时应删除查询参数。
- 首屏只保留厂商编号和 HIS 验证码两个主要接入字段；100-002 专用登录账号与连接超时参数分别收入“登录验证账号”和“高级设置”。
- 列表将“凭证状态”改为“接入信息”，避免把底层安全实现暴露为业务概念。

## 安全与运行边界

- 管理端直接录入的接入信息由服务端使用 AES-GCM 和部署独立主密钥加密保存；查询接口只返回是否已配置，不返回明文。
- 编辑已有连接时全部接入字段留空表示保持原值，避免用掩码或空值覆盖已保存内容。
- 数据库补丁已在本地 SQL Server 2012 SP4 环境应用，后端重新启动后 `/actuator/health` 返回 `UP`。
- 后端重启使原浏览器会话失效；重启前已完成真实页面抽屉检查，重启后的三步式页面由类型检查、生产构建和内容测试复验，不将登录页截图冒充最终业务页面证据。

## 自动化验证

- `./tools/verify.sh` 完整通过。
- 后端 102 项测试通过；前端 92 项测试通过。
- SQL 迁移静态检查、Java Checkstyle、前端注释检查、TypeScript 严格类型检查和两端生产构建均通过。

final result: passed

# 运行总览 KPI 布局 Design QA

- Source visual truth path: `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-e8026641-188e-47f8-8987-212b042bf979.png`
- Previous implementation reference: `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-8a680136-aaa8-4ac0-a074-b21a2ba21af0.png`
- Implementation screenshot path: current-run Chrome CUA inline capture; the browser interface did not expose a persistent local path.
- Viewport: approximately 1712 × 900 CSS px in the authenticated Chrome desktop page.
- Source pixels: 2736 × 202. Implementation capture pixels: 1712 × 900. The source is a focused KPI strip while the implementation is a full page, so comparison used the visible KPI region and normalized by the four-column container width rather than image density.
- State: authenticated platform administrator; backend available; four KPI values loaded from the local backend.

## Full-view comparison evidence

- The earlier implementation showed four independent rounded cards, one icon per card, and a two-column internal arrangement.
- The revised implementation shows one continuous white strip divided into four equal columns, matching the source's primary composition.
- The surrounding command bar, audit list and configuration shortcuts intentionally remain unchanged because the requested visual target only covers the KPI region.

## Focused region comparison evidence

- Source and revised KPI regions were inspected together in the same visual review: both use four equal tracks, thin vertical separators, left-aligned text and the order “label, large value, muted detail”.
- Icons were removed from the KPI region, eliminating the large left indentation visible in the previous implementation.
- The project keeps its existing neutral text palette and 4px container radius. These are intentional design-system constraints; the requested layout, alignment and grouping match the source.

## Required fidelity surfaces

- Fonts and typography: passed; existing project fonts are retained, with clear 11px label, 28px value and 10px detail hierarchy.
- Spacing and layout rhythm: passed; the strip uses equal columns, 22px horizontal padding, 108px minimum height and no gaps between items.
- Colors and visual tokens: passed; white surface, neutral text and existing teal hover/focus state remain consistent with the management shell.
- Image quality and asset fidelity: passed; the source KPI region has no required image assets, and the previous decorative icons were removed.
- Copy and content: passed; all four business labels, live values, explanations and destinations are preserved.

## Findings and comparison history

- Initial finding [P2]: four separate rounded cards and leading icons fragmented one KPI group and did not match the source hierarchy.
- Fix: replaced the card grid with one bordered strip, removed KPI icons, stacked the three text levels and added only vertical separators.
- Post-fix evidence: the refreshed Chrome capture shows four continuous equal columns with aligned text and no icon gutter. No actionable P0, P1 or P2 mismatch remains.
- Responsive behavior: existing two-column and one-column breakpoints are retained; scoped borders switch to row separators at those breakpoints.

## Interaction and browser verification

- Loaded the real dashboard values while authenticated.
- Clicked “机构需要处理”; the page opened `/organizations?status=attention` and selected “只看需要处理”.
- Returned to the dashboard and confirmed the KPI strip remained populated.
- Type check, all 79 frontend tests and the production build passed.

final result: passed

---

# 外部系统页面 Design QA

- Source visual truth: `/Users/vliudbe/Projects/county-integration-platform/.design-qa-source.png`
- Implementation screenshot: `/Users/vliudbe/Projects/county-integration-platform/.design-qa-implementation.png`
- Side-by-side evidence: `/Users/vliudbe/Projects/county-integration-platform/.design-qa-side-by-side.png`
- Comparison page: `/Users/vliudbe/Projects/county-integration-platform/.design-qa-comparison.html`
- Viewport: 1440 × 1024 CSS px
- Source pixels: 1487 × 1058; normalized by proportional browser rendering to the same comparison column width.
- Implementation pixels: 1440 × 1024 at device scale factor 1.
- State: 基层 HIS 已选中；3 个机构；首个机构展开；生产与测试环境混合配置状态。

## Full-view comparison evidence

并排证据确认实现保留了选定方案的三层结构：现有平台侧栏与页头、当前系统选择区、机构连接矩阵。主操作“新增机构连接”、系统资料入口、筛选区、连接状态和展开详情均位于与设计目标一致的层级。正式实现继续使用项目既有的更紧凑字体、间距和页脚，因此没有复制设计图中的演示数据横幅或不存在的菜单权限。

## Focused region comparison evidence

机构矩阵是本次重点区域。1440px 视口下，机构、生产环境、测试环境、凭证状态、最后更新和操作列均完整显示。机构名称、机构编码、URL、更新时间和操作均使用单行布局；超长内容以省略号收敛，并通过 `title` 提供完整值。较窄桌面视口使用横向滚动，不通过折行压缩表格。展开行保留服务地址、凭证状态、超时和更新时间，并可进入真实连接编辑抽屉。

## Required fidelity surfaces

- Fonts and typography: 复用平台现有中文系统字体与字号层级；标题、字段名和数据层级清楚。列表内容保持单行，未出现非预期换行。
- Spacing and layout rhythm: 系统上下文与矩阵间距清楚；矩阵工具栏、表头、数据行和展开行形成稳定纵向节奏。
- Colors and visual tokens: 复用平台现有灰白表面、低饱和绿色主色和语义状态色；展开行使用浅绿色弱强调。
- Image quality and asset fidelity: 页面没有需要复刻的位图资产；品牌和功能图标继续使用项目现有图标库，没有用占位图形或手绘 SVG 代替。
- Copy and content: 使用真实领域文案，明确“当前系统”“生产环境”“测试环境”“凭证状态”和“新增机构连接”；敏感凭证只显示配置状态。

## Findings

- 无 P0、P1 或 P2 问题。
- P3：设计图展示“最后核验”，当前接口只提供 `updatedAt`，正式页面因此准确显示“最后更新”，避免伪造不存在的连通性核验结果。
- P3：无对应环境的机构显示“未配置”，已有但停用的连接显示“已停用”，比设计图的统一灰态更精确。

## Interaction and runtime checks

- 已验证机构行展开与收起。
- 已验证“仅看缺失配置”筛选由 3 个机构收敛为 2 个机构。
- 已验证“编辑连接”打开真实编辑抽屉，机构和环境保持只读，关闭后焦点返回页面。
- 已检查浏览器控制台：无 error 或 warn。
- 已通过项目完整验证脚本，后端 100 项测试、前端 88 项测试、类型检查和生产构建均通过。

## Comparison history

- 初次默认桌面视口约 1264px，表格按设计使用横向滚动，操作列不在首屏；该截图与 1440px 设计目标视口不一致，未作为最终差异判断依据。
- 将浏览器视口规范化为 1440 × 1024 后重新捕获；所有主要列完整进入首屏，无需为匹配设计而牺牲单行展示。

## Follow-up polish

- 后端未来若提供独立的连通性核验时间与结果，可把“最后更新”升级为设计图中的“最后核验”，并增加“待核验”状态。

final result: passed

---

# 外部系统空数据状态复验

- 修改前截图：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-empty-source.png`
- 修改后截图：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-empty-state.jpg`
- 并排对比：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-empty-side-by-side.jpg`
- 对比页面：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-empty-comparison.html`
- 实际状态：本地数据库不存在外部系统记录，使用正式接口与正式页面验证。

## 问题与修正

- 修改前在 0 条系统、0 条机构连接时仍展示空的系统选择器、换行后的“新增系统”、搜索筛选栏和大面积矩阵空壳，界面层级与当前任务不匹配。
- 修改后按数据阶段分流：0 条系统时仅显示“登记第一个外部系统”；已有系统但 0 条连接时仅显示“新增机构连接”；存在连接数据时才显示搜索、筛选和连接矩阵。
- 中等桌面宽度下系统选择与操作保持同一行；机构名称、地址、时间等列表字段继续使用单行省略，不通过折行压缩表格。

## 交互与运行验证

- 已在正式页面打开并关闭“登记外部系统”抽屉，入口与焦点流程正常。
- 浏览器控制台无 error 或 warn。
- `./tools/verify.sh` 完整通过：后端 100 项测试、前端 89 项测试、类型检查与生产构建均通过。

final result: passed

---

# 外部系统配置抽屉流程复验

- 修改前机构连接：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-endpoint-drawer-source.png`
- 修改后机构连接：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-endpoint-drawer.jpg`
- 修改前系统资料：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-system-drawer-source.png`
- 修改后系统资料：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-system-drawer.jpg`
- 并排证据：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-drawer-side-by-side.jpg`
- 对比页面：`/Users/vliudbe/Projects/county-integration-platform/.design-qa-drawer-comparison.html`

## 审查结论

- 步骤 1，系统资料：由高干扰的“本次操作”卡片和错位两列表单，改为稳定编码、名称、用途、整体状态四个连续字段；入口由“管理系统资料”降级为“系统资料”。健康状态：通过。
- 步骤 2，新增机构连接：移除重复的标题摘要和大块安全告知，环境与机构并列，地址和凭证使用完整行，超时值并列且单位固定在输入框内。健康状态：通过。
- 步骤 3，机构选择：基层 HIS 只显示启用的 `PRIMARY_CARE` 机构，并默认选择首个可用基层机构；不再误选平台机构。健康状态：通过。
- 步骤 4，连接启用：删除实现者视角的“保存后的状态”和二选一下拉框，改为紧凑的“启用连接”开关；开启表示参与业务调用，关闭表示只保存配置。默认启用，布尔值随创建请求一次写入。健康状态：通过。
- 步骤 5，保存操作：主操作统一为“保存连接”，不再要求先创建停用连接、再进入编辑启用。两个抽屉均使用固定底部操作区，内容滚动时取消和保存保持可见。健康状态：通过。

## 可访问性与证据边界

- 标签均与控件建立关联，抽屉继续保留对话框语义、关闭按钮名称和键盘处理。
- 截图只能验证可见布局；键盘完整遍历和屏幕阅读器播报未作为本次视觉审查结论。浏览器控制台无 error 或 warn。
- `./tools/verify.sh` 完整通过：后端 100 项测试、前端 91 项测试、类型检查与生产构建均通过。
- 运行时复验发现凭证提供器存在多构造方法但未声明注入入口，已为生产构造方法显式标注注入；应用重新启动后 `/actuator/health` 返回 `UP`。

final result: passed
