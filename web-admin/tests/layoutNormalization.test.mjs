import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('共享面包屑清除全局 nav 留白，避免页头被撑高', async () => {
  const source = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  const breadcrumbRule = source.match(/\.prototype-breadcrumb \{([^}]*)\}/)?.[1] ?? ''

  assert.match(breadcrumbRule, /padding: 0/)
  assert.match(breadcrumbRule, /display: flex/)
})

test('三个数据目录页共用带当前态和机构上下文的页签导航', async () => {
  const tabs = await readFile('web-admin/src/components/DatasetTabs.vue', 'utf8')
  const directory = await readFile('web-admin/src/views/master-data/directory/DirectoryLayout.vue', 'utf8')
  const icd10 = await readFile('web-admin/src/views/master-data/directory/Icd10DirectoryView.vue', 'utf8')

  assert.match(tabs, /aria-label="基础数据集"/)
  assert.equal((tabs.match(/aria-current=/g) ?? []).length, 3)
  assert.match(tabs, /organizationQuery/)
  assert.match(tabs, /query: organizationQuery/)
  assert.match(tabs, /props\.organizationCode \? \{ organizationCode: props\.organizationCode \} : \{\}/)
  assert.match(directory, /<DatasetTabs :organization-code="organizationCode" \/>/)
  assert.match(icd10, /<DatasetTabs \/>/)
  assert.doesNotMatch(directory, /<nav class="dataset-nav"/)
  assert.doesNotMatch(icd10, /<nav class="dataset-nav"/)
})

test('运行总览中等视口将管理操作和基础配置上下排列', async () => {
  const source = await readFile('web-admin/src/views/dashboard/DashboardView.vue', 'utf8')

  assert.match(source, /@media \(max-width: 1100px\)\s*\{\s*\.overview-work-grid \{ grid-template-columns: minmax\(0, 1fr\); \}/)
})

test('管理列表末端预留滚动余量以完整显示共享页脚', async () => {
  const source = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(source, /\.prototype-main:has\(\.audit-page\) \{ padding-bottom: 1px; \}/)
  assert.match(source, /\.prototype-main:has\(\.dictionary-page\) \{ padding-bottom: 1px; \}/)
  assert.match(source, /\.prototype-main:has\(\.parameter-page\) \{ padding-bottom: 1px; \}/)
  assert.match(source, /\.prototype-main:has\(\.user-page\) \{ padding-bottom: 1px; \}/)
})

test('窄屏页头将会话状态放在标题右侧而不是另起一行', async () => {
  const source = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(source, /@media \(max-width: 700px\)[\s\S]*?\.real-app \.prototype-topbar \{ grid-template-columns: 32px minmax\(0, 1fr\) auto; \}[\s\S]*?\.real-app \.prototype-session-status \{ grid-column: 3; grid-row: 1;/)
})

test('正式管理页面的窄屏内容区使用紧凑边距并覆盖桌面主题间距', async () => {
  const source = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(source, /@media \(max-width: 700px\) \{\s*\.real-app \.prototype-content \{ padding: 12px 12px 28px; \}/)
})

test('管理端共享页头把当前页面名称放在同一行面包屑末项', async () => {
  const layout = await readFile('web-admin/src/layout/AppLayout.vue', 'utf8')
  const styles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(layout, /<nav class="prototype-breadcrumb"[\s\S]*?<h1>\{\{ route\.meta\.title \}\}<\/h1>[\s\S]*?<\/nav>/)
  assert.doesNotMatch(layout, /prototype-page-title/)
  assert.match(styles, /\.real-app \.real-page-heading \.prototype-breadcrumb \{[\s\S]*?white-space: nowrap;/)
  assert.match(styles, /\.real-app \.prototype-main \.real-page-heading \{ min-height: 76px; padding: 18px 28px; \}/)
})

test('共享列表工具栏按宽度组织桌面筛选和窄屏操作', async () => {
  const source = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(source, /\.standard-list-toolbar__filters \{ grid-column: 1 \/ -1; grid-row: 1;[^}]*grid-template-columns: repeat\(auto-fill, minmax\(min\(240px, 100%\), 1fr\)\)/)
  assert.match(source, /\.standard-list-toolbar \.prototype-search \{ justify-self: stretch; width: 100%; min-width: 0; max-width: none; \}/)
  assert.doesNotMatch(source, /\.standard-list-toolbar \.prototype-search \{[^}]*grid-column: 1 \/ -1;/)
  assert.match(source, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters \{ grid-column: 1; grid-row: 1; display: grid; grid-template-areas: "search status type"; grid-template-columns: minmax\(240px, 1fr\) repeat\(2, minmax\(170px, 220px\)\); \}/)
  assert.match(source, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters > \.standard-list-filter--search \{ grid-area: search; \}/)
  assert.match(source, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__commands \{ grid-column: 1 \/ -1; grid-row: 2; \}/)
  assert.match(source, /@container \(max-width: 560px\)[\s\S]*?\.standard-list-toolbar__filters \{ grid-template-columns: minmax\(0, 1fr\); \}/)
  assert.match(source, /@container \(max-width: 560px\)[\s\S]*?\.standard-list-toolbar__commands \{ grid-column: 1; grid-row: auto; width: 100%; min-width: 0; justify-content: flex-start;/)
  assert.match(source, /@container \(min-width: 381px\) and \(max-width: 760px\) \{\s*\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters \{ width: 100%; grid-template-areas: "search search" "status type"; grid-template-columns: repeat\(2, minmax\(0, 1fr\)\); justify-content: start; justify-items: stretch;/)
  assert.match(source, /@container \(min-width: 381px\) and \(max-width: 760px\)[\s\S]*?\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__commands \{ width: 100%; justify-content: flex-start; \}/)
  assert.doesNotMatch(source, /@container \(min-width: 761px\) and \(max-width: 1200px\)\s*\{\s*\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters \{ grid-template-areas: "search search search" "status type \.";/)
  assert.match(source, /\.standard-list-toolbar__commands \{ grid-column: 1 \/ -1; grid-row: 2;[^}]*justify-content: flex-start;/)
  assert.match(source, /\.standard-list-toolbar__commands > \.standard-list-toolbar__refresh \{ margin-inline-start: 0; white-space: nowrap; \}/)
})

test('共享筛选栏使用语义网格区域而不依赖控件顺序', async () => {
  const source = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(source, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters > \.standard-list-filter--search \{ grid-area: search; \}/)
  assert.match(source, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters > \.standard-list-filter--status \{ grid-area: status; \}/)
  assert.match(source, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters > \.standard-list-filter--type \{ grid-area: type; \}/)
  assert.doesNotMatch(source, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters > :nth-child\([23]\)/)
})

test('参数配置概况使用同一网格排列指标卡', async () => {
  const source = await readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8')
  const summaryRule = source.match(/\.parameter-summary \{([^}]*)\}/)?.[1] ?? ''

  assert.match(summaryRule, /display: grid/)
  assert.match(summaryRule, /grid-template-columns: repeat\(3, minmax\(0, 1fr\)\)/)
  assert.match(summaryRule, /max-width: 900px/)
  assert.match(source, /@media \(max-width: 480px\) \{ \.parameter-summary span \{ display:flex; flex-direction:column; align-items:flex-start; gap:2px; \}/)
  assert.match(source, /<PageState v-else-if="!error && rows\.length === 0" kind="empty" title="没有符合当前条件的参数" compact \/>/)
  assert.doesNotMatch(source, /<div v-if="rows\.length === 0" class="prototype-empty">/)
})

test('参数配置筛选统一使用共享工具栏断点', async () => {
  const source = await readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8')

  assert.match(source, /<div class="parameter-query-container">\s*<ListQueryToolbar/)
  assert.match(source, /<ListQueryToolbar filters-layout="search-with-selects"/)
  assert.match(source, /class="prototype-search standard-list-filter--search"/)
  assert.match(source, /class="standard-list-filter--status" v-model="environment"/)
  assert.match(source, /class="standard-list-filter--type" v-model="configurationStatus"/)
  assert.doesNotMatch(source, /@container \(max-width: (?:860|420)px\)[\s\S]*?standard-list-toolbar__filters/)
})

test('参数编辑抽屉移除冗余摘要并使用共享固定操作栏', async () => {
  const source = await readFile('web-admin/src/views/configuration/parameter/components/ParameterEditorDrawer.vue', 'utf8')

  assert.doesNotMatch(source, /scope-confirm|change-summary|本次修改范围|待保存内容/)
  assert.match(source, /<form id="parameter-editor-form" class="work-form"/)
  assert.match(source, /<template #footer>[\s\S]*?form="parameter-editor-form"[\s\S]*?保存参数[\s\S]*?<\/template>/)
  assert.doesNotMatch(source, /<div class="work-inline-actions">/)
})

test('数据列表按实际内容收高且窄视口横向滚动时操作列保持可见', async () => {
  const rolePage = await readFile('web-admin/src/views/system/role/RoleView.vue', 'utf8')
  const userPage = await readFile('web-admin/src/views/system/user/UserView.vue', 'utf8')
  const organizationPage = await readFile('web-admin/src/views/system/organization/OrganizationView.vue', 'utf8')
  const externalSystemPage = await readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8')
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.doesNotMatch(rolePage.match(/\.role-panel \{([^}]*)\}/)?.[1] ?? '', /min-height/)
  assert.doesNotMatch(userPage.match(/\.user-panel \{([^}]*)\}/)?.[1] ?? '', /min-height/)
  assert.doesNotMatch(organizationPage.match(/\.organization-panel \{([^}]*)\}/)?.[1] ?? '', /min-height/)
  assert.match(externalSystemPage, /\.connection-matrix \{ min-height: 0; \}/)
  assert.match(sharedStyles, /\.action-column-table th:last-child, \.action-column-table td:last-child:not\(\[colspan\]\) \{[\s\S]*?position: sticky;[\s\S]*?right: 0;/)
  assert.match(sharedStyles, /@media \(max-width: 860px\)[\s\S]*?\.action-column-table--scroll-actions th:last-child,[\s\S]*?\.action-column-table--scroll-actions td:last-child:not\(\[colspan\]\)\s*\{[^}]*position: static/s)
})

test('机构编辑抽屉使用共享滚动正文和固定操作栏', async () => {
  const source = await readFile('web-admin/src/views/system/organization/components/OrganizationEditorDrawer.vue', 'utf8')
  const [frame, sharedStyles] = await Promise.all([
    readFile('web-admin/src/components/DrawerFrame.vue', 'utf8'),
    readFile('web-admin/src/assets/styles/prototype.css', 'utf8'),
  ])
  const formRule = source.match(/\.organization-form \{([^}]*)\}/)?.[1] ?? ''

  assert.match(source, /<DrawerFrame\b[^>]*body-class="organization-frame-body"/)
  assert.match(source, /<template #footer>[\s\S]*form="organization-form"[\s\S]*<\/template>/)
  assert.match(frame, /<div class="work-drawer-body"/)
  assert.match(sharedStyles, /\.work-drawer > \.work-drawer-footer \{ min-height: 64px/)
  assert.match(sharedStyles, /\.work-drawer-body \{[^}]*overflow-y: auto/)
  assert.doesNotMatch(formRule, /overflow-y: auto/)
  assert.match(source, /\.organization-form input, \.organization-form select \{ width: 100%; min-width: 0;/)
})

test('窄屏共享抽屉按可用布局宽度展开，避免100vw跨过滚动条槽', async () => {
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(sharedStyles, /@media \(max-width: 700px\)[\s\S]*?\.work-drawer \{ width: 100%; \}/)
})

test('机构列表筛选从左侧排列且不显示通用横向滚动提示', async () => {
  const source = await readFile('web-admin/src/views/system/organization/OrganizationView.vue', 'utf8')
  const sharedFrame = await readFile('web-admin/src/components/AdminTableFrame.vue', 'utf8')

  assert.match(source, /<ListQueryToolbar filters-layout="search-with-selects"/)
  assert.match(source, /<AdminTableFrame[^>]*label="机构列表"[^>]*has-actions/)
  assert.match(source, /<PageState v-else-if="!error && filtered\.length === 0" kind="empty" title="没有符合当前筛选条件的机构" compact>/)
  assert.match(source, /<AdminTableFrame v-else-if="filtered\.length" label="机构列表" has-actions>/)
  assert.doesNotMatch(source, /filtered-empty/)
  assert.doesNotMatch(source.match(/\.status-field \{([^}]*)\}/)?.[1] ?? '', /margin-left:\s*auto/)
  assert.doesNotMatch(sharedFrame, /scrollHint|hintBreakpoint|standard-table-hint/)
})

test('用户列表移除重复横向滚动说明并将筛选项压缩为两列', async () => {
  const source = await readFile('web-admin/src/views/system/user/UserView.vue', 'utf8')

  assert.match(source, /<ListQueryToolbar filters-layout="search-with-four-selects"/)
  assert.match(source, /class="standard-list-filter--organization"/)
  assert.match(source, /class="standard-list-filter--role"/)
  assert.match(source, /<AdminTableFrame[^>]*label="用户列表"[^>]*has-actions/)
  assert.doesNotMatch(source, /standard-list-toolbar select \{ width: calc\(50% - 5px\)/)

  const styles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  assert.match(styles, /\.standard-list-toolbar--search-with-four-selects > \.standard-list-toolbar__filters \{ grid-column: 1; grid-row: 1; display: grid; grid-template-areas: "search status organization role attention"; grid-template-columns: minmax\(220px, 1fr\) repeat\(4, minmax\(150px, 190px\)\); \}/)
  assert.match(styles, /@container \(min-width: 561px\) and \(max-width: 859px\)[\s\S]*?grid-template-areas: "search search" "status organization" "role attention";/)
  assert.match(styles, /@container \(max-width: 560px\)[\s\S]*?grid-template-areas: "search" "status" "organization" "role" "attention";/)
})

test('用户筛选零结果使用共享紧凑空态并隐藏空表头', async () => {
  const source = await readFile('web-admin/src/views/system/user/UserView.vue', 'utf8')

  assert.match(source, /<PageState v-else-if="!error && filtered\.length === 0" kind="empty" title="没有符合当前筛选条件的用户" compact>/)
  assert.match(source, /<AdminTableFrame v-else-if="filtered\.length" label="用户列表" has-actions>/)
  assert.doesNotMatch(source, /filtered\.length === 0" class="prototype-empty"/)
})

test('用户抽屉窄屏表单不被选择框最小内容宽度撑开', async () => {
  const source = await readFile('web-admin/src/views/system/user/components/UserEditorDrawer.vue', 'utf8')

  assert.match(source, /\.user-form \{ min-width: 0; display: grid; grid-template-columns: minmax\(0, 1fr\);/)
  assert.match(source, /\.user-form > label \{ min-width: 0;/)
  assert.match(source, /\.user-form input, \.user-form select, \.password-reset input \{ width: 100%; min-width: 0;/)
  assert.match(source, /\.choice-grid > label > span \{ min-width: 0; \}/)
  assert.match(source, /\.choice-grid small \{[^}]*overflow-wrap: anywhere;/)
})

test('用户详情将主要机构编码移出单行下拉框并允许窄屏换行', async () => {
  const source = await readFile('web-admin/src/views/system/user/components/UserEditorDrawer.vue', 'utf8')

  assert.match(source, /const primaryOrganizationCode = computed\(\(\) => props\.organizations\.find\(item => String\(item\.id\) === form\.value\.primaryOrganizationId\)\?\.organizationCode \?\? ''\)/)
  assert.match(source, /v-if="mode !== 'view'">（\{\{ organization\.organizationCode \}\}）/)
  assert.match(source, /v-if="mode !== 'create' && primaryOrganizationCode" class="user-organization-code">\{\{ primaryOrganizationCode \}\}/)
  assert.match(source, /\.user-form \.user-organization-code \{ overflow-wrap: anywhere; \}/)
})

test('用户基础资料新增和编辑操作固定在共享抽屉底栏', async () => {
  const source = await readFile('web-admin/src/views/system/user/components/UserEditorDrawer.vue', 'utf8')

  assert.match(source, /<form v-if="activeSection === 'base'" id="user-base-form" class="user-form"/)
  assert.match(source, /<template v-if="\(mode === 'create' \|\| mode === 'edit'\) && activeSection === 'base'" #footer>/)
  assert.match(source, /type="submit" form="user-base-form"/)
  assert.doesNotMatch(source, /<form[^>]*class="user-form"[\s\S]*?<footer[\s\S]*?<\/form>/)
})

test('用户密码操作窄屏将重置入口放到说明文字下方', async () => {
  const source = await readFile('web-admin/src/views/system/user/components/UserEditorDrawer.vue', 'utf8')
  const mobileStyles = source.slice(source.indexOf('@media (max-width: 600px)'))

  assert.match(mobileStyles, /\.password-operation > header \{ align-items: flex-start; flex-direction: column; \}/)
  assert.match(mobileStyles, /\.access-section > header \{ flex-direction: column; gap: 8px; \}/)
})

test('参数配置共享表格不显示横向滚动说明文案', async () => {
  const source = await readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8')

  assert.match(source, /<AdminTableFrame[^>]*label="参数配置列表"[^>]*has-actions/)
  assert.doesNotMatch(source, /scroll-hint|hint-breakpoint/)
})

test('字典项总数只在类型摘要和分页中展示一次', async () => {
  const source = await readFile('web-admin/src/views/configuration/dictionary/DictionaryView.vue', 'utf8')

  assert.match(source, /class="dictionary-meta"[^>]*>\{\{ enabledItemCount \}\} \/ \{\{ items.length \}\} 个字典项已启用/)
  assert.match(source, /<PageState v-if="itemRows\.length === 0" kind="empty" title="没有符合当前条件的字典项" compact \/>/)
  assert.match(source, /<template v-else><AdminTableFrame label="字典项列表" has-actions :pin-actions="false">/)
  assert.match(source, /<AdminPagination :total="itemRows\.length"/)
  assert.doesNotMatch(source, /itemRows\.length \}\} \/ \{\{ items\.length/)
  assert.doesNotMatch(source, /查看和维护其字典项/)
  assert.doesNotMatch(source, /\.dictionary-workspace \{[^}]*min-height:/)
  assert.match(source, /\.dictionary-types \{[^}]*align-self: start;/)
  assert.match(source, /<AdminTableFrame[^>]*label="字典项列表"[^>]*has-actions/)
})

test('数据库结构维护方案列表使用共享受控分页并展示空列表总数', async () => {
  const source = await readFile('web-admin/src/views/database-contract/DatabaseContractMaintenanceView.vue', 'utf8')

  assert.match(source, /import AdminPagination from '@\/components\/AdminPagination\.vue'/)
  assert.match(source, /useClientPagination\(computed\(\(\) => plans\.value\)\)/)
  assert.match(source, /v-for="plan in pagedPlans"/)
  assert.match(source, /<AdminPagination compact :total="plans\.length" :page="planPage" :page-size="planPageSize"/)
  assert.match(source, /尚无维护方案[\s\S]*?<AdminPagination compact :total="plans\.length"/)
})

test('窄屏分页为每页条数文本和原生下拉箭头保留清晰间距', async () => {
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(sharedStyles, /@container \(max-width: 480px\)[\s\S]*?\.standard-pagination\.compact \.pagination-summary \.form-select \{ width: 108px; min-width: 108px; padding-inline: 6px 32px;/)
})

test('外部系统宽表使用共享滚动区且不显示冗余提示', async () => {
  const source = await readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8')

  assert.match(source, /<AdminTableFrame[^>]*label="外部系统端点配置矩阵"/)
  assert.doesNotMatch(source, /scroll-hint|hint-breakpoint/)
  assert.match(source, /class="standard-list-filter standard-list-filter--select"/)
  assert.match(source, /class="standard-list-filter standard-list-filter--check"/)
  assert.match(source, /\.matrix-table-area \{ min-width: 0; container-type: inline-size; \}/)
  assert.match(source, /<AdminTableFrame[^>]*label="外部系统端点配置矩阵"[^>]*has-actions/)
  assert.doesNotMatch(source, /\.status-filter|\.incomplete-filter/)
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  assert.match(sharedStyles, /\.standard-list-filter--select \{ display: inline-flex/)
  assert.match(sharedStyles, /\.standard-list-filter--check \{ display: inline-flex/)
  assert.doesNotMatch(sharedStyles, /standard-table-frame--hint-wide|standard-table-hint/)
})

test('HIS调用记录宽表使用共享滚动区且不显示横向提示文案', async () => {
  const source = await readFile('web-admin/src/views/exchange/record/ExchangeRecordView.vue', 'utf8')

  assert.match(source, /<AdminTableFrame[^>]*label="HIS 调用记录列表"/)
  assert.doesNotMatch(source, /scroll-hint|hint-breakpoint|standard-table-hint/)
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  assert.doesNotMatch(sharedStyles, /standard-table-frame--hint-desktop|standard-table-hint/)
})

test('医疗目录窄屏宽表不显示重复滚动说明', async () => {
  const source = await readFile('web-admin/src/views/master-data/directory/MedicalDirectoryView.vue', 'utf8')

  assert.match(source, /<AdminTableFrame[^>]*label="医疗目录列表"/)
  assert.doesNotMatch(source, /scroll-hint|hint-breakpoint/)
})

test('综合目录窄屏宽表不显示重复滚动说明', async () => {
  const source = await readFile('web-admin/src/views/master-data/directory/HospitalDirectoryView.vue', 'utf8')

  assert.match(source, /<AdminTableFrame[^>]*label="医院综合目录列表"/)
  assert.doesNotMatch(source, /scroll-hint|hint-breakpoint/)
})

test('正式页面头部去掉重复介绍并使用紧凑高度', async () => {
  const layout = await readFile('web-admin/src/layout/AppLayout.vue', 'utf8')
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.doesNotMatch(layout, /pageContext\.description/)
  assert.doesNotMatch(layout, /public-catalog-badge/)
  assert.match(sharedStyles, /\.prototype-topbar \{[^}]*min-height: 96px/)
  assert.match(sharedStyles, /\.prototype-heading h1 \{[^}]*font-size: 24px/)
})

test('ICD10与审计页面删除重复说明并保留可读的窄屏表格', async () => {
  const icd10Page = await readFile('web-admin/src/views/master-data/directory/Icd10DirectoryView.vue', 'utf8')
  const auditPage = await readFile('web-admin/src/views/audit/management/AuditView.vue', 'utf8')

  assert.doesNotMatch(icd10Page, /public-boundary|published-state|该目录不按机构归属或筛选/)
  assert.doesNotMatch(auditPage, /审计记录不会自动清理；归档或清理须经审批。/)
  assert.match(auditPage, /\.audit-table \{ min-width: 1060px; table-layout: fixed; \}/)
  assert.match(auditPage, /<ListQueryToolbar class="audit-filters" filters-layout="dense-grid"/)
  assert.match(auditPage, /class="prototype-search standard-list-filter--span-2 standard-list-filter--full-tablet"/)
  assert.match(auditPage, /class="audit-date standard-list-filter--span-2 standard-list-filter--tablet-span-1 standard-list-filter--date"/)
  assert.doesNotMatch(auditPage, /audit-filters :deep\(\.standard-list-toolbar/)
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  assert.match(sharedStyles, /\.standard-list-toolbar__filters--dense-grid \{ grid-template-columns: repeat\(6, minmax\(0, 1fr\)\); align-items: end; \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar__filters--dense-grid > \.standard-list-filter--date \{ grid-column: span 1; \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar__filters--dense-grid > \.standard-list-filter--span-2 \{ grid-column: span 2; \}/)
  assert.match(sharedStyles, /@container \(max-width: 860px\)[\s\S]*?\.standard-list-toolbar__filters--dense-grid \{ grid-template-columns: repeat\(2, minmax\(0, 1fr\)\); \}[\s\S]*?\.standard-list-toolbar__filters--dense-grid > \.standard-list-filter--full-tablet \{ grid-column: 1 \/ -1; \}[\s\S]*?\.standard-list-toolbar__filters--dense-grid > \.standard-list-filter--date \{ grid-column: 1 \/ -1; \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar__filters--dense-grid > \.standard-list-filter--tablet-span-1 \{ grid-column: auto; \}/)
  assert.match(sharedStyles, /@container \(max-width: 560px\)[\s\S]*?\.standard-list-toolbar__filters--dense-grid \{ grid-template-columns: minmax\(0, 1fr\); \}/)
  assert.match(auditPage, /\.audit-panel \{ --action-column-width: 104px; container-type: inline-size; \}/)
  assert.match(auditPage, /<AdminTableFrame[^>]*label="审计记录列表"/)
  assert.doesNotMatch(auditPage, /scroll-hint|hint-breakpoint/)
})

test('首页指标把数值靠右对齐并在中等屏幕旁置基础配置卡', async () => {
  const dashboardPage = await readFile('web-admin/src/views/dashboard/DashboardView.vue', 'utf8')
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(dashboardPage, /\.overview-kpis > a \{[^}]*display: grid; grid-template-columns: minmax\(0, 1fr\) auto/)
  assert.match(dashboardPage, /\.overview-kpis strong \{ grid-column: 2; grid-row: 1 \/ 3;/)
  assert.match(sharedStyles, /@media \(max-width: 1050px\) \{[\s\S]*?\.overview-work-grid \{ grid-template-columns: minmax\(0, 1\.25fr\) minmax\(0, \.9fr\); \}/)
  assert.match(sharedStyles, /@media \(max-width: 700px\) \{\s*\.overview-work-grid \{ grid-template-columns: 1fr; \}/)
})

test('ICD-10 窄屏表格按可见列重新布局，避免名称列被隐藏列挤窄', async () => {
  const source = await readFile('web-admin/src/views/master-data/directory/Icd10DirectoryView.vue', 'utf8')

  assert.match(source, /<colgroup><col \/><col \/><col \/><col \/><col \/><\/colgroup>/)
  assert.match(source, /@media \(max-width:780px\)[\s\S]*?\.icd10-table \{ min-width:0; table-layout:fixed; \}[\s\S]*?\.icd10-table col:nth-child\(2\),\.icd10-table col:nth-child\(3\),\.icd10-table col:nth-child\(4\) \{ display:none; \}/)
  assert.match(source, /@media \(max-width:780px\)[\s\S]*?\.icd10-table col:last-child \{ width:15%; \}[\s\S]*?\.icd10-table th:last-child \{ width:15%; \}/)
  assert.match(source, /@media \(max-width:520px\)[\s\S]*?\.icd10-table col:last-child,\.icd10-table th:last-child \{ width:24%; \}/)
  assert.match(source, /\.icd10-table td:last-child \{ overflow:visible; text-overflow:clip; \}/)
})

test('批次详情响应式字段纵向排布并允许长值换行', async () => {
  const source = await readFile('web-admin/src/views/master-data/batch/batch-detail-grid.css', 'utf8')
  const invocationPanel = await readFile('web-admin/src/views/master-data/batch/components/Icd10HisInvocationPanel.vue', 'utf8')

  assert.match(source, /@media \(max-width: 760px\)[\s\S]*?\.batch-detail-grid-scroll \.batch-detail-grid \{[\s\S]*?grid-template-columns: minmax\(0, 1fr\);/)
  assert.match(source, /\.batch-detail-grid-scroll \.batch-detail-grid dt,[\s\S]*?white-space: normal;\s*overflow-wrap: anywhere;/)
  assert.match(invocationPanel, /<AdminTableFrame[^>]*label="HIS 调用记录"/)
  assert.doesNotMatch(invocationPanel, /scroll-hint|hint-breakpoint/)
})
