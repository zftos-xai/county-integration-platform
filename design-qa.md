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

# ICD10 平台公共目录原型 Design QA（2026-09-22）

- Source visual truth: `/Users/vliudbe/.codex/generated_images/01a0c7a9-9212-7b20-97b9-3a0c21ef5b98/exec-7b8d11a5-2a0f-4df2-b615-5ab6ae444dea.png`（第二版，1487 × 1058 像素）。
- Implementation route: `http://127.0.0.1:5173/master-data/directory/icd10`。
- Implementation evidence: Chrome 浏览器在当前运行中捕获的桌面原型截图（约 1692 × 792 CSS 像素，DPR 1）；浏览器接口只提供内联截图，没有可持久化的本地截图路径。
- State: 已登录桌面管理端；无版本来源下的西医诊断默认目录、以及中医诊断类别切换、关键词“感冒”查询和同步原型抽屉。
- Normalization: 比较同一桌面管理端壳层；源图按 1440 × 1024 设计，浏览器捕获为较矮的桌面视口，因此仅比较可见内容区的结构、密度、文字层级和交互状态，不将底部裁切当作视觉缺陷。

## Full-view comparison evidence

- 无版本模式的“疾病类别”左侧上下文轨道已替代机构选择；页面未出现机构名称、机构代码、机构筛选或虚构的版本切换。
- 用户要求的顶部融合已落实：既有应用壳层继续提供面包屑、页面标题和说明；“平台公共目录”作为标题同行标记，不另造第二个页面头。正文从合成原型提示、类别版本轨道和同步摘要开始。
- 同步摘要、搜索条和紧凑表格维持选中视觉稿的白底、浅灰工作区、低饱和绿色主操作和高密度目录阅读节奏。

## Focused region comparison evidence

- 标题区：原视觉稿在内容区内显示标题；为与现有管理端融合，标题上移至统一顶部栏。这是有意调整，避免双标题，并保留“平台公共目录”紧邻标题。
- 类别轨道：点击“中医诊断”后，摘要数从 12,583 改为 5,208，并展示 4 条中医合成记录；无版本字段不进入主表。
- 查询：输入“感冒”并提交后，表格与记录数同时收敛为一条匹配记录。
- 同步操作：抽屉明确显示“目录归属：平台公共目录”和“机构归属：不适用”，确认操作仅提示原型反馈，不创建真实批次或外部调用。

## Findings and iteration history

- Iteration 1 [P2]：公共目录标记初次显示在标题下方并横向拉伸，无法融入现有顶部层级。已将标题与标记包装为同行标题区；复验后标记紧邻标题且说明文本仍单独成行。
- Iteration 2 [P1]：中医类别切换后没有合成目录记录，容易被误读为数据为空。已补齐中医 2022 版和 2019 版合成记录，并让底部展示数量随筛选结果变化；浏览器复验通过。
- Iteration 3 [P1]：ICD-10 原型原先使用独立地址，未落在现有“数据目录”的一级页签位置。已将地址收敛为 `/master-data/directory/icd10`，并在“综合目录、三大目录”后添加选中态 `ICD-10` 页签；浏览器在桌面管理端截图中复验通过。
- Iteration 4 [P1]：接口资料只将诊断版本列为可选请求条件，单条响应不返回版本；原型却硬编码多个版本轨道和表格列。已删除版本选择与版本列，摘要只标记“来源未提供诊断版本”，同步详情保存“诊断版本：来源未提供”。浏览器复验了中医类别切换及同步抽屉，均未出现机构或版本筛选。
- Remaining P0/P1/P2: 无。

## Required fidelity surfaces

- Fonts and typography: passed；沿用现有 Microsoft YaHei / Segoe UI 中文管理端字体、标题层级和 11–14px 高密度表格节奏。
- Spacing and layout rhythm: passed；统一顶部不重复，正文采用 244px 上下文轨道和单一目录工作区，桌面视口无页面级横向溢出。
- Colors and visual tokens: passed；复用白色表面、浅灰画布和既有低饱和绿色主操作/成功状态。
- Image quality and asset fidelity: passed；视觉稿没有非标准栅格资产；页面复用项目已有图标组件，无替代性占位图。
- Copy and content: passed；明确“平台公共目录”“机构归属：不适用”和原型不可写入边界，不将合成数据包装成联调事实。

## Interaction and browser verification

- 验证顶层 `综合目录｜三大目录｜ICD-10` 页签排列；仅前两个机构目录页签保留机构查询参数，ICD-10 页签不携带机构上下文。
- 验证西医/中医类别切换与无版本目录上下文更新。
- 验证关键词搜索及结果数同步变化。
- 验证“新建同步”抽屉、机构归属不适用说明和确认后仅产生原型反馈。
- `npm run typecheck --workspace web-admin` 通过；`node --test web-admin/tests/icd10DirectoryPrototype.test.mjs web-admin/tests/directoryLayout.test.mjs` 13 项通过；`git diff --check` 通过；浏览器控制台无 error/warn。

final result: passed

---

# 基础数据开发前业务形态复验

## 复验结果

1. 数据目录归属：机构目录与平台公共目录有明确切换，ICD10待确认状态不再暗示机构所有权。健康状态：通过。
2. 批次发起职责：管理视角仅能切换职责，运维视角具有明确“新建同步批次”主操作。健康状态：通过。
3. 发起前置条件：表单展示机构映射、HIS接口配置和同范围活动批次三项就绪检查。健康状态：通过。
4. 批次粒度：一次只选择一个机构和一个数据类型，药品、诊疗、耗材按接口要求显示时间范围。健康状态：通过。
5. 创建后反馈：创建后列表聚焦新批次，状态为“取得中”，详情说明上一成功版本继续可读。健康状态：通过。
6. 列表可读性：同步批次由八列压缩为六组业务信息，名称和关键结果保持单行，详情承载完整核对数据。健康状态：通过。

## 开发边界

- 原型只定义用户操作、状态及事实呈现，不证明真实接口已经完成同步。
- 三项就绪检查、活动批次唯一性、发布原子性、机构范围和操作权限必须由服务端强制执行。
- ICD10在来源、维护责任、版本与交易码确认前不得创建机构批次，也不得作为已接入公共目录发布。

## 自动与浏览器证据

- TypeScript严格类型检查、17项基础数据业务原型验收测试、前端注释检查和生产构建通过。
- 浏览器已复验机构目录、平台公共目录、管理切换运维、创建同步批次及创建后“取得中”详情。

final result: passed

---

# 基础数据业务口径收口 Design QA（2026-09-19）

- Source visual truth: `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-de0dab35-8570-47ae-9765-4593b381a95e.png`
- Business truth: `docs/reference/interfaces/新版基层HIS 与云平台V1.0接口文档.md` 的 `100-003` 至 `100-008`，以及 `docs/plans/基础数据业务详细计划.md`
- Implementation route: `http://127.0.0.1:5173/prototype`，管理视角下的数据目录和同步批次
- Viewport: 1074 × 900 CSS px，设备像素比 1
- Source pixels: 1488 × 1058；本轮沿用既有视觉体系，重点比较信息层级、密度和业务文案
- Implementation screenshot: current-run Codex in-app browser inline captures；浏览器接口未提供持久化本地路径
- State: 数据目录科室详情、`100-003`已发布批次详情、`100-004/100-005`结果未知批次详情

## Full-view comparison evidence

- 数据目录继续沿用参考图的机构列表、数据页签、高密度目录和展开详情，没有因新增业务口径另造页面。
- 新增的ICD10范围说明和三方数量核对位于目录页签与筛选之间，仍处于同一工作流，未形成竞争性的第二套导航。
- 同步批次保持原有列表密度；展开区仍使用左右两列，只在左侧增加查询范围或四类子调用，在右侧增加平台有效数、县医院HIS读取和验收状态。
- 1074px桌面视口下，页面操作列持续可见，展开详情没有造成页面级横向溢出。

## Focused region comparison evidence

- 数据目录：中药和西药成为独立页签；ICD10不再放入机构页签。时间表头由不具备来源依据的“来源更新时间”改为“平台最近取得”。
- 三方核对：本批实际返回、平台当前有效、县医院HIS读取和最近核对并列展示；验收人缺失被明确标成待确认，没有伪造验收完成。
- `100-003`批次：科室、医生、病区、床位分别展示目录类型、返回数、结果和请求标识，避免把410条误解为一次接口响应。
- 分页目录批次：明确展示目录类型、开始结束时间和名称条件；“全量分页”改为“约定时间范围分页”。
- ICD10：批次范围改为“平台公共目录（归属待确认）”，不再把接口未提供的机构维度当作事实。

## Required fidelity surfaces

- Fonts and typography: passed；继续使用现有中文管理端字号和字重，新增长范围使用分层文字，没有挤压主标题。
- Spacing and layout rhythm: passed；三方核对使用四列紧凑条，子调用使用两列网格，与现有详情密度一致。
- Colors and visual tokens: passed；范围待确认使用现有浅黄色提示，正常核对和已发布数据使用现有低饱和绿色。
- Image quality and asset fidelity: passed；本轮无新增位图资产，继续使用项目现有矢量图标。
- Copy and content: passed；来源HIS和县医院HIS的角色已区分，未继续暴露未经确认的更新时间、机构归属和全量结论。

## Findings and comparison history

- Iteration 1 [P1]：机构目录把ICD10当作机构数据；已移出机构页签，并把批次标记为公共范围归属待确认。
- Iteration 1 [P1]：`100-003`四类目录合并成一个410条结果；已在详情展示四次分项取得事实。
- Iteration 1 [P1]：`100-003/100-008`数据使用“来源更新时间”；已改为“平台最近取得”。
- Iteration 1 [P1]：药品没有区分中药和西药；已拆分独立页签和筛选口径。
- Iteration 1 [P1]：分页接口把未知时间语义称为全量；已改为可复核的约定时间范围。
- Post-fix：数据目录和两类批次在浏览器中复验，核心业务身份、范围、数量、状态和操作完整可读；控制台无warning或error。
- Remaining P3：人员范围和多科室规则仍需来源方真实样例确认；原型已改为待确认问题，不再提前固化单一关系。

## Verification

- 前端注释规则检查通过。
- TypeScript严格类型检查通过。
- 98项前端自动化测试通过。
- 管理端生产构建通过。
- 浏览器控制台warning/error为0。

final result: passed

---

# 基础数据机构映射冲突闭环审查

## 演练步骤与结果

1. 发现名称变化：同时展示 HIS 当前机构名称、稳定来源代码和平台现有机构，不把名称相似直接当作同一机构。健康状态：通过。
2. 阻断新数据：映射冲突期间暂停该机构新批次，既有正式数据仍按原归属可读。健康状态：通过。
3. 核对机构身份：管理员必须选择唯一的平台机构，并填写名称变更批复、统一社会信用代码或其他可复核依据。健康状态：通过。
4. 保存核对结果：状态进入“待验证”，不直接变成“已映射”，也不自动恢复日常同步。健康状态：通过。
5. 创建首次验证批次：只取得一条机构资料，核对来源代码、机构名称和平台归属；验证数据不直接发布。健康状态：通过。
6. 查看验证影响：首次接入机构仍无正式数据；发生名称变化的机构继续读取既有正式版本，新数据在验证通过前不可读。健康状态：通过。

## 克制设计边界

- 未新增独立机构审批系统，只记录平台机构选择、核对依据和一次验证批次。
- 页面不根据名称自动合并机构，也不允许验证批次改写平台机构名称、层级或权限范围。
- 机构归属确认与恢复同步分为两个状态，避免一次保存误放开全部基础数据。
- 同一映射只能创建一个活动验证批次，重复操作直接进入已创建批次。

## 证据边界

- 原型验证了名称冲突展示、核对依据必填、待验证状态和首次验证批次创建。
- 正式实现仍需服务端校验机构范围、保存变更前后值与证据编号、限制活动验证批次唯一性，并在验证成功后以审计事件恢复同步。

final result: passed

# 基础数据问题复核闭环审查

## 演练步骤与结果

1. 从待核查批次进入问题：自动携带批次号筛选并展开“来源缺失”问题。健康状态：通过。
2. 管理视角查看影响：只显示问题事实、当前可读版本、责任方和下一步，不提供代办操作。健康状态：通过。
3. 切换运维并领取：责任人更新为当前运维，问题进入核查中。健康状态：通过。
4. 登记来源结论：选择“来源已确认停用，等待新批次复核”并填写问题单依据后，问题正确进入已完成，不再误判为等待来源方。健康状态：通过。
5. 创建复核批次：只在结论要求重新取得数据时出现入口；新批次沿用原机构、数据类型、交易码和范围，不直接发布。健康状态：通过。
6. 查看复核批次：阶段显示为“正在取得数据—等待校验—等待发布”，上一正式版本及 HIS 可读数据保持不变。健康状态：通过。

## 本轮修正

- 将批次中的“3 项问题”修正为“1 项问题”，受影响记录仍在问题页显示为 3 条，避免混淆问题数量和数据数量。
- 核查结果的状态判断改为按业务结论分类；包含“等待新批次复核”不再等同于“仍等待来源方”。
- 核查完成后更新期限和下一步；只有来源问题已处理且需要复核时，才提供按原范围创建新批次。
- 同机构、同数据范围已有取得中或校验中批次时，不允许再次创建复核批次。
- 新批次增加“取得中”阶段，避免 0 条数据时错误展示“取得数据、校验核对已完成”。

## 证据边界

- 浏览器截图和可访问性树验证了可见状态、角色切换、筛选、表单和页面跳转。
- 原型数据仍为组件内合成状态；刷新后的持久化、服务端权限、并发锁和真实批次执行需要在正式 API 实现阶段验证。
- 截图不能证明完整键盘遍历和屏幕阅读器播报，正式页面仍需自动化与人工无障碍测试。

final result: passed

# 基础数据运行闭环深化审查

## 审查范围

- 同步批次：回答一次同步由谁发起、取得了什么、为什么发布或未发布、当前正式版本和 HIS 可读数据是否受影响。
- 待核查问题：只承接必须由人工或来源方确认的异常，不在问题页修改来源业务数据，也不提供无条件重跑或一键发布。
- 机构映射：只确认“来源机构对应平台哪一家机构”，不修改平台机构档案、层级和数据权限。

## 发现与修正

- P1：批次详情只展示技术数量和状态，没有说明未发布批次对当前正式数据的影响。已增加“取得数据—校验核对—发布/未发布”处理结果、当前正式版本和 HIS 可读数量；失败、待核查和结果未知均明确继续使用上一成功版本或暂无可读版本。
- P1：所有问题共用一组处理结论，容易让运维对“来源缺失”“结果未知”“数量不一致”做出不适用的选择。已按问题类型限制可登记结论，并补充责任方、下一步和处理边界。
- P1：批次问题入口和问题原批次之间没有带条件跳转。已让数据目录、批次、问题和映射页面携带机构或批次条件跳转，并自动筛选、展开目标对象。
- P2：机构映射只说明代码对应关系，没有说明未映射和冲突会阻止什么。已明确未映射时不能开始同步、冲突时暂停新批次；保存正确映射后提示通过新批次重新取得和校验数据。
- P2：映射页面允许选择任意平台机构，名称不一致时也可保存。原型现在阻止明显名称不一致的映射，并要求填写可复核依据；没有增加额外审批流。

## 克制设计边界

- 未增加“批次重跑”按钮：结果未知必须先核查来源结果，来源缺失和数量不一致需要来源修正后再创建新批次。
- 未增加“一键发布”或在问题页直接编辑业务字段：正式数据只能经过新批次重新取得、校验和原子发布。
- 未增加映射审批层级：管理人员在权限范围内登记映射及依据，审计由正式实现统一记录。
- 未把同步调度、告警、审计和接口配置复制到这三个页面；只保留到相关业务对象的查看入口。

## 浏览器复验

- 管理视角从待核查批次点击问题数量后，页面自动按批次号筛选并展开对应问题。
- 管理视角只能查看影响范围；切换运维并领取后，页面只展示“来源缺失”适用的两个结论和核查依据输入。
- 机构映射维护使用紧凑对话框，不再以整屏抽屉制造大面积空白；未映射机构的来源信息、平台机构选择、依据和保存结果位于同一视线范围。
- 1440px 桌面视口无页面级横向溢出；表格内容保持单行，超出列宽时省略。
- `npm run verify:frontend` 通过：注释与菜单检查、两个前端类型检查、生产构建和 93 项前端测试全部成功。

final result: passed

---

# 基础数据剩余页面 Design QA

- Source visual truth:
  - `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-e1d479c9-2c1c-44a2-ba89-2bc9c7dba0f4.png`
  - `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-de0dab35-8570-47ae-9765-4593b381a95e.png`
- Implementation route: `http://127.0.0.1:5173/prototype`，管理视角下的同步批次、待核查问题和机构映射。
- Implementation screenshot: current-run Codex in-app browser inline captures; the browser interface did not expose a persistent local screenshot path.
- Viewport: 1488 × 1058 CSS px, device scale factor 1.
- Source pixels: navigation crop 390 × 390；完整数据目录参考 1488 × 1058。
- Implementation pixels: 1488 × 1058。
- State: 合成数据原型；同步批次列表、首条问题展开、机构映射列表及维护抽屉均已验证。

## Full-view comparison evidence

- 三个入口位于“数据目录”下方，顺序、单行高度、线性图标和绿色选中态与导航参考一致。
- 三个页面继续使用现有 280px 白色侧栏、136px 上下文页头、浅灰画布和白色高密度工作区，没有引入新的视觉体系。
- 页面内部统一为“局部页签 → 摘要/边界说明 → 筛选 → 高密度列表 → 展开详情”，与完整数据目录参考的操作节奏一致。

## Focused region comparison evidence

- 同步批次：连续指标条、批次范围、交易码、数量、状态和耗时在 1488px 首屏完整显示；耗时按毫秒、秒、分秒分级。
- 待核查问题：展开行明确并列“为什么需要核查”和“处理本项问题”；来源缺失、结果未知和数量不一致均保留上一成功版本，不提供直接改来源字段入口。
- 机构映射：来源代码、来源名称、平台机构、影响范围和状态保持单行省略；管理视角可维护，运维视角只查看。
- 映射表初次比较在 1488px 出现轻微横向滚动；收窄列宽和表格最小宽度后，固定操作列进入首屏且长代码仍以省略方式展示。

## Required fidelity surfaces

- Fonts and typography: passed；沿用项目现有中文系统字体及标题、页签、表头、数据的层级。
- Spacing and layout rhythm: passed；48px 页签、40px 控件、48px 数据行及展开区域与数据目录保持一致。
- Colors and visual tokens: passed；使用既有白色表面、浅灰分隔线、绿色选中态和受控的状态色。
- Image quality and asset fidelity: passed；页面没有位图内容，导航与操作使用项目现有矢量图标库。
- Copy and content: passed；页面文案对应批次、核查、映射业务，不出现凭证、数据库账号或未经确认的自动停用结论。

## Interaction and browser verification

- 验证同步批次搜索、机构/数据/状态筛选、详情展开以及从问题数量进入待核查页面。
- 验证管理视角只能查看问题；切换运维后可以领取事项、选择结论、填写依据并登记，状态更新为“等待来源方”。
- 验证运维视角不能修改机构映射；切换管理视角后可选择平台机构、填写变更依据并保存。
- 验证保存映射只改变来源对应关系，不修改平台机构档案或权限范围。
- 浏览器控制台无 error 或 warn。
- 前端注释检查、菜单检查、双端严格类型检查、双端生产构建及 93 项前端测试全部通过。

## Findings and comparison history

- Iteration 1 [P2]：页面上下文页头下又重复显示一次页面名称和说明，削弱信息层级；已删除重复标题卡，仅保留全局页头和业务页签。
- Iteration 2 [P2]：机构映射表在 1488px 出现横向滚动，操作列不能稳定进入首屏；已压缩非关键列宽并保持单行省略。
- Post-fix：三个页面在同一桌面视口下保持完整操作区，无持续遮挡或重复标题。
- P3：附图只明确了三个导航入口，页面业务字段依据项目基础数据详细计划和现有数据目录设计补齐，仍需业务方确认正式字段与责任人。

final result: passed

---

# 数据目录原型 Design QA

- Source visual truth: `/var/folders/dn/c0_txl2973b7vh7dndrl0c600000gn/T/codex-clipboard-de0dab35-8570-47ae-9765-4593b381a95e.png`
- Implementation route: `http://127.0.0.1:5173/prototype`，管理视角 → 数据目录
- Viewport: 1488 × 1058 CSS px
- Source pixels: 1488 × 1058
- Implementation evidence: current-run Codex in-app browser capture; the browser interface exposed the capture inline and did not provide a persistent local file path.
- Comparison method: source and implementation were inspected side by side at the same dimensions before handoff.

## Full-view comparison evidence

- 页面沿用参考图的白色管理端框架、分组侧栏、136px 上下文页头和浅灰内容画布。
- 主工作区采用与参考图一致的两栏结构：左侧机构树负责选择机构，右侧依次呈现机构上下文、目录页签、批次摘要、筛选与高密度列表。
- 原有“基础数据运行状态”、流程横幅和指标卡已移除，避免把目录查看流程拆成多条竞争路径。

## Focused region comparison evidence

- 机构列表保持单行名称、状态点和选中态；超长内容省略，不靠折行撑高行距。
- 右侧页签覆盖机构资料、科室、人员、病区、床位、药品、诊疗、耗材和 ICD10；其中数量均为原型合成数据，不再表述为真实联调数量，未接入目录明确显示空状态。
- 表格在 1488px 参考视口完整显示编码、名称、辅助信息、类别、状态、来源更新时间、最近发布和操作列。
- 展开行并列显示“平台当前数据”和“来源追溯”，来源系统、机构、记录标识、100-003 交易及最近拉取时间均可直接复核。

## Required fidelity surfaces

- Fonts and typography: passed；沿用项目中文系统字体，标题、机构、目录页签、表头和数据层级与参考图一致。
- Spacing and layout rhythm: passed；机构树、工作区、页签、工具栏和 39px 数据行保持紧凑管理端节奏。
- Colors and visual tokens: passed；白色表面、浅灰画布、低饱和绿色选中态和语义状态点保持一致。
- Image quality and asset fidelity: passed；页面只需要品牌和功能图标，继续使用项目现有矢量图标，无缺失位图资产。
- Copy and content: passed；文案只描述管理员能理解的机构、数据目录、批次和来源，不暴露凭证或内部实现概念。

## Interaction and browser verification

- 验证机构选择、科室/人员/病区/床位页签切换、名称或编码搜索、状态筛选、记录展开/收起和分页。
- 验证从人员搜索切换回科室时自动清理不适用的搜索条件，避免出现“有数据但列表为空”的误导状态。
- 验证药品、诊疗、耗材和 ICD10 显示明确的待接入空状态。
- 浏览器控制台无 error 或 warn。
- TypeScript 严格类型检查、93 项前端测试和管理端生产构建通过。

## Findings

- 无 P0、P1 或 P2 问题。
- P3：科室86条、人员245条、病区12条、床位67条均是原型展示数量，没有可核对的真实成功联调证据；正式页面不得沿用这些数量。
- P3：侧栏保留当前原型已实现的系统管理入口，没有为匹配截图创建不可用的假页面。

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

---

# 基础数据分页结果未知闭环审查

## 演练步骤与结果

1. 从结果未知批次进入分页问题：原批次保持未发布，上一成功版本不受影响。健康状态：通过。
2. 查看核查事实：显示来源请求标识、最近核查、下次核查、联系对象和禁止直接重提边界。健康状态：通过。
3. 登记“仍无法确认”：未填写下次核查时间时拒绝保存；补充时间后进入等待来源方，并更新责任期限。健康状态：通过。
4. 来源返回明确结论：登记问题单或日志依据后，问题进入已完成，下次核查变为不再核查。健康状态：通过。
5. 创建复核批次：仅在来源明确本批未取得完整结果后出现；按原机构、目录、交易码和完整查询范围创建，不只重提超时页。健康状态：通过。
6. 查看复核批次：状态从“取得中”开始，依次等待校验和发布；未成功发布前 HIS 不读取本批数据。健康状态：通过。

## 克制设计边界

- 没有“重试第 8 页”或“一键重跑”操作，避免结果未知时产生重复或不完整数据。
- 没有伪造来源查询接口的自动成功结果，只允许登记真实日志、沟通记录或问题单依据。
- 仍未知时只增加一个必要字段“下次核查时间”，没有引入审批流、工单系统或额外状态机。
- 相同机构和数据范围已有取得中或校验中批次时，继续阻止重复创建。

## 证据边界

- 原型验证了页面状态、必填校验、角色操作和跨页面跳转；来源日志查询仍需真实接口或人工核查渠道支持。
- 正式实现必须由服务端校验权限、活动批次唯一性和并发版本，不能依赖前端按钮状态。

final result: passed

---

# 基础数据数量不一致闭环审查

## 演练步骤与结果

1. 从失败批次进入数量问题：平台同时展示来源声明数、实际取得数和差额，上一成功版本继续供 HIS 读取。健康状态：通过。
2. 核对比较口径：明确机构、疾病类别、版本，以及声明接口与分页接口必须使用同一查询范围。健康状态：通过。
3. 来源尚未说明：登记联系记录后仍必须填写下次核查时间，不能把等待状态直接关闭。健康状态：通过。
4. 来源给出明确结论：分别支持“统计口径有误”和“缺失数据已补齐”，两种结论都必须记录书面说明或问题单。健康状态：通过。
5. 创建复核批次：结论明确后才开放入口，按原机构、类别、版本和完整分页范围重新取得，不允许在平台手工补两条。健康状态：通过。
6. 查看复核批次：新批次从取得中开始，来源声明数标记为等待重新取得，不沿用已确认错误的旧声明数。健康状态：通过。

## 克制设计边界

- 没有提供“忽略差额后发布”或“手工补齐”操作，平台只记录来源事实与处理依据。
- 没有新增独立审批流；已有核查记录、责任人、期限和复核批次足以完成当前业务链。
- 复核批次仍受同机构同数据范围活动批次唯一性限制，避免重复取得。

## 证据边界

- 原型验证了数量对比、核查结论、下次核查必填、复核批次创建与跨页状态。
- 正式实现仍需服务端保存来源声明响应、分页累计数量、去重规则、查询条件摘要及并发版本，页面不能替代服务端校验。

final result: passed

---

# 基础数据人员关系冲突闭环审查

## 演练步骤与结果

1. 从人员失败批次进入关系冲突：原批次显示未发布，HIS 继续读取上一成功版本中的 124 人。健康状态：通过。
2. 查看冲突事实：展示人员姓名和编码、两条所属科室、科室编码及各自在来源分页中的位置。健康状态：通过。
3. 来源尚未确认：登记沟通依据后仍必须填写下次核查时间，问题保持等待来源方。健康状态：通过。
4. 来源完成修正：必须填写来源确认的唯一所属科室和问题单，问题才进入已完成。健康状态：通过。
5. 创建复核批次：按原机构及人员全量范围重新取得，不允许只请求或手工修改单个人员。健康状态：通过。
6. 查看复核批次：状态从取得中开始，来源无独立数量接口时继续显示“无独立行数接口”，上一正式版本保持可读。健康状态：通过。

## 克制设计边界

- 页面不提供“选择内科”或“选择门诊部”的覆盖操作，唯一有效关系必须由来源系统确认并修正。
- 没有为单条冲突增加审批流或平台侧主数据编辑器，只使用现有核查记录和复核批次完成闭环。
- 复核范围保持人员全量快照，避免只修复单条后遗漏同批次其他关系冲突。

## 证据边界

- 原型验证了冲突详情、等待来源校验、来源修正结论、原批次回查和复核批次创建。
- 正式实现仍需服务端保存冲突两侧原始响应、分页位置、来源问题单、活动批次唯一性和并发版本。

final result: passed

---

# 基础数据未定义取值闭环审查

## 演练步骤与结果

1. 从耗材失败批次进入取值问题：原批次634条未发布，HIS继续读取上一成功版本的630条耗材。健康状态：通过。
2. 查看问题事实：显示字段“启用状态”、实际值9、当前已知规则“0停用/1启用”和4条受影响耗材。健康状态：通过。
3. 等待正式说明：登记沟通依据后仍必须填写下次核查时间，不能由运维猜测取值含义。健康状态：通过。
4. 确认处理方式：支持来源纠正非法值，或正式取值获批且规则维护已经完成；两种结论都要求可复核依据。健康状态：通过。
5. 创建复核批次：仅在处理条件满足后开放，按原机构和耗材完整分页范围重新取得。健康状态：通过。
6. 查看复核批次：从取得中开始，重新取得来源声明、全部耗材和取值校验结果；上一正式版本继续可读。健康状态：通过。

## 克制设计边界

- 将开发术语“未知枚举”改为业务可理解的“未定义取值”。
- 页面不提供把9直接映射成启用或停用的快捷操作，也不允许跳过正式取值说明。
- 未增加独立规则审批模块，只要求登记已有的正式数据字典和规则维护记录。
- 补齐同步批次列表中的“耗材”筛选项，保持已有筛选结构不变。

## 证据边界

- 原型验证了取值事实、受影响数据、核查必填、两种解决路径和复核批次创建。
- 正式实现仍需服务端保存来源原始值、接口版本、批准后的映射规则版本、规则维护审计及并发版本。

final result: passed

---

# 机构映射验证结果回写闭环审查

## 演练步骤与结果

1. 进入验证批次：页面并排展示 HIS 返回的机构名称与代码、准备归属的平台机构及代码，本批发布数保持为 0。健康状态：通过。
2. 验证通过：批次进入“验证通过”，映射进入“已映射”，日常同步入口恢复；验证数据仍不进入正式版本。健康状态：通过。
3. 验证失败：批次进入“验证失败”，映射回到“映射冲突”，首次接入机构仍无正式数据，新数据继续阻断。健康状态：通过。
4. 返回机构映射：批次携带映射编号返回并自动展开对应记录，展示失败影响、下一步和验证批次编号。健康状态：通过。
5. 重复处理边界：已登记结果的批次不再展示结果按钮；失败映射必须重新核对依据后才能创建新的验证批次。健康状态：通过。

## 克制设计边界

- 验证批次只确认机构归属，不修改平台机构名称、层级、权限或业务数据。
- 未增加独立审批页或新的待办体系；结果直接回写已有批次和机构映射。
- 验证失败不伪造待核查问题数量，后续入口仍然是机构映射而不是无对应记录的问题页。
- 验证通过只恢复创建日常同步批次的资格，不代表基础数据已经同步或发布。

## 证据边界

- 当前浏览器复验覆盖验证前、验证通过、验证失败和返回映射四个可见状态。
- 正式实现仍需服务端保存操作者、核对材料摘要、时间、并发版本和审计事件，并原子更新批次与映射状态。

final result: passed
