import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import test from 'node:test'

const pageStatePath = new URL('../src/components/PageState.vue', import.meta.url)
const drawerFramePath = new URL('../src/components/DrawerFrame.vue', import.meta.url)
const modalFramePath = new URL('../src/components/ModalFrame.vue', import.meta.url)
const paginationPath = new URL('../src/components/AdminPagination.vue', import.meta.url)
const tableFramePath = new URL('../src/components/AdminTableFrame.vue', import.meta.url)
const rowActionsPath = new URL('../src/components/ListRowActions.vue', import.meta.url)

test('共享页面状态组件统一加载、空结果、错误播报与紧凑布局', async () => {
  const source = await readFile(pageStatePath, 'utf8')

  assert.match(source, /type PageStateKind = 'loading' \| 'empty' \| 'error' \| 'info'/)
  assert.match(source, /kind === 'error' \? 'alert' : kind === 'loading' \? 'status'/)
  assert.match(source, /aria-live="kind === 'loading' \? 'polite' : kind === 'error' \? 'assertive' : undefined"/)
  assert.match(source, /\.page-state \{[\s\S]*min-height: 240px/)
  assert.match(source, /\.page-state\.compact \{ min-height: 120px/)
  assert.match(source, /<slot name="icon"/)
  assert.match(source, /<slot name="actions"/)
})

test('管理端基础色彩令牌映射到当前设计值并供工作区主题复用', async () => {
  const base = await readFile(new URL('../src/assets/styles/base.css', import.meta.url), 'utf8')
  const theme = await readFile(new URL('../src/assets/styles/prototype.css', import.meta.url), 'utf8')
  const organization = await readFile(new URL('../src/views/system/organization/OrganizationView.vue', import.meta.url), 'utf8')
  const batches = await readFile(new URL('../src/views/master-data/batch/MasterDataBatchView.vue', import.meta.url), 'utf8')

  assert.match(base, /--ui-color-text:\s*#17272e/)
  assert.match(base, /--ui-color-primary:\s*#147467/)
  assert.match(base, /--ui-color-border:\s*#d8e0e2/)
  assert.match(base, /--ui-radius-panel:\s*6px/)
  assert.match(base, /color:\s*var\(--ui-color-text\);\s*background:\s*var\(--ui-color-canvas\);/)
  assert.match(theme, /--ink:\s*var\(--ui-color-text\)/)
  assert.match(theme, /--accent:\s*var\(--ui-color-primary\)/)
  assert.match(theme, /--line:\s*var\(--ui-color-border\)/)
  assert.match(organization, /\.organization-page\s*\{\s*color:\s*var\(--ui-color-text\);\s*\}/)
  assert.match(batches, /\.master-batch-page\s*\{[\s\S]*color:\s*var\(--ui-color-text\);/)
})

test('共享查询栏只有搜索框时让搜索框占满可用宽度', async () => {
  const sharedStyles = await readFile(new URL('../src/assets/styles/prototype.css', import.meta.url), 'utf8')

  assert.match(sharedStyles, /\.standard-list-toolbar__filters:has\(> \.prototype-search:only-child\)\s*\{\s*grid-template-columns:\s*minmax\(0, 1fr\);\s*\}/)
  assert.match(sharedStyles, /\.standard-list-toolbar--search-with-selects \{ grid-template-columns: minmax\(0, 1fr\); \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters \{ grid-column: 1; grid-row: 1; display: grid; grid-template-areas: "search status type"; grid-template-columns: minmax\(240px, 1fr\) repeat\(2, minmax\(170px, 220px\)\); \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters > \.standard-list-filter--search \{ grid-area: search; \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters > \.standard-list-filter--status \{ grid-area: status; \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters > \.standard-list-filter--type \{ grid-area: type; \}/)
  assert.match(sharedStyles, /@container \(max-width: 560px\) \{\s*\.standard-list-toolbar \{ grid-template-columns: minmax\(0, 1fr\); \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar__commands > \.standard-list-toolbar__refresh \{ margin-inline-start: 0; white-space: nowrap; \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar__commands \{[^}]*justify-content: flex-start;/)
  assert.match(sharedStyles, /@container \(max-width: 560px\) \{\s*\.standard-list-toolbar--search-with-selects \{ grid-template-columns: minmax\(0, 1fr\); \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters \{ grid-column: 1; grid-row: auto; grid-template-areas: "search" "status" "type"; grid-template-columns: minmax\(0, 1fr\); \}/)
})

test('三类数据目录页共享页签、查询工具栏和机构目录骨架', async () => {
  const [tabs, layout, hospital, medical, icd10] = await Promise.all([
    readFile(new URL('../src/components/DatasetTabs.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/master-data/directory/DirectoryLayout.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/master-data/directory/HospitalDirectoryView.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/master-data/directory/MedicalDirectoryView.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/master-data/directory/Icd10DirectoryView.vue', import.meta.url), 'utf8'),
  ])

  for (const routeView of [hospital, medical]) assert.match(routeView, /<DirectoryLayout\b/)
  assert.match(layout, /<DatasetTabs :organization-code="organizationCode"\s*\/>/)
  assert.match(layout, /<ListQueryToolbar[\s\S]*?filters-layout="search-with-filter"[\s\S]*?<\/ListQueryToolbar>/)
  assert.match(layout, /<PageState v-if="error"/)
  assert.match(layout, /<slot v-if="!isLoading && !error && total > 0" name="pagination" \/>/)
  assert.match(tabs, /aria-label="基础数据集"/)
  assert.match(tabs, /organizationQuery/)
  assert.match(icd10, /<DatasetTabs \/>/)
  assert.match(icd10, /<ListQueryToolbar\b/)
})

test('正式页面不得私自覆盖共享查询栏样式', async () => {
  const root = new URL('../src/views/', import.meta.url)
  const businessAreas = ['audit', 'configuration', 'dashboard', 'database-contract', 'error', 'exchange', 'master-data', 'system']

  async function findVueFiles(directory) {
    const entries = await readdir(directory, { withFileTypes: true })
    const nested = await Promise.all(entries.map(entry => {
      const path = new URL(`${entry.name}${entry.isDirectory() ? '/' : ''}`, directory)
      return entry.isDirectory() ? findVueFiles(path) : entry.isFile() && entry.name.endsWith('.vue') ? [path] : []
    }))
    return nested.flat()
  }

  const files = (await Promise.all(businessAreas.map(area => findVueFiles(new URL(`${area}/`, root))))).flat()
  for (const file of files) {
    const source = await readFile(file, 'utf8')
    const scopedStyles = [...source.matchAll(/<style\b[^>]*scoped[^>]*>([\s\S]*?)<\/style>/g)].map(match => match[1]).join('\n')
    assert.doesNotMatch(scopedStyles, /\.standard-list-toolbar\b/, `${file.pathname} must keep shared toolbar styles in ListQueryToolbar`)
  }
})

test('共享抽屉框架统一对话框语义、键盘焦点管理和固定操作区', async () => {
  const source = await readFile(drawerFramePath, 'utf8')
  const sharedStyles = await readFile(new URL('../src/assets/styles/prototype.css', import.meta.url), 'utf8')

  assert.match(source, /useModalDialog\(isOpen, \(\) => emit\('close'\)\)/)
  assert.match(source, /role="dialog"[\s\S]*aria-modal="true"[\s\S]*:aria-labelledby="labelledBy"/)
  assert.match(source, /@keydown="handleDialogKeydown"/)
  assert.match(source, /@mousedown\.self="dismissOnBackdrop && emit\('close'\)"/)
  assert.match(source, /<slot name="title"/)
  assert.match(source, /<slot name="subheader"/)
  assert.match(source, /<slot name="tabs"/)
  assert.match(source, /<footer v-if="\$slots\.footer" class="work-drawer-footer"><slot name="footer" \/><\/footer>/)
  assert.match(sharedStyles, /\.work-drawer > \.work-drawer-footer \{ min-height: 64px/)
})

test('共享居中对话框框架统一语义、键盘焦点管理和响应式固定操作区', async () => {
  const source = await readFile(modalFramePath, 'utf8')
  const sharedStyles = await readFile(new URL('../src/assets/styles/prototype.css', import.meta.url), 'utf8')

  assert.match(source, /useModalDialog\(isOpen, \(\) => emit\('close'\)\)/)
  assert.match(source, /role="dialog"[\s\S]*aria-modal="true"[\s\S]*:aria-labelledby="labelledBy"/)
  assert.match(source, /@keydown="handleDialogKeydown"/)
  assert.match(source, /@mousedown\.self="dismissOnBackdrop && emit\('close'\)"/)
  assert.match(source, /<slot name="title"/)
  assert.match(source, /<slot name="footer" \/>/)
  assert.match(sharedStyles, /\.work-modal \{[^}]*max-height: var\(--work-modal-max-height/)
  assert.match(sharedStyles, /\.work-modal-footer \.prototype-button \{ flex: 1; \}/)
})

test('正式列表页统一使用受控共享分页组件', async () => {
  const paths = [
    '../src/views/audit/management/AuditView.vue',
    '../src/views/configuration/dictionary/DictionaryView.vue',
    '../src/views/configuration/external-system/ExternalSystemView.vue',
    '../src/views/configuration/parameter/ParameterView.vue',
    '../src/views/database-contract/DatabaseContractMaintenanceView.vue',
    '../src/views/exchange/record/ExchangeRecordView.vue',
    '../src/views/master-data/batch/MasterDataBatchView.vue',
    '../src/views/master-data/directory/HospitalDirectoryView.vue',
    '../src/views/master-data/directory/Icd10DirectoryView.vue',
    '../src/views/master-data/directory/MedicalDirectoryView.vue',
    '../src/views/system/organization/OrganizationView.vue',
    '../src/views/system/role/RoleView.vue',
    '../src/views/system/user/UserView.vue',
  ]
  const [pagination, sharedStyles, ...views] = await Promise.all([
    readFile(paginationPath, 'utf8'),
    readFile(new URL('../src/assets/styles/prototype.css', import.meta.url), 'utf8'),
    ...paths.map(path => readFile(new URL(path, import.meta.url), 'utf8')),
  ])

  for (const source of views) assert.match(source, /<AdminPagination\b/)
  assert.match(pagination, /aria-label="列表分页"/)
  assert.match(pagination, /aria-label="每页条数"/)
  assert.match(pagination, /'update:pageSize'/)
  assert.match(sharedStyles, /\.standard-pagination \{[^}]*display: grid;[^}]*grid-template-columns: minmax\(0, 1fr\) auto;[^}]*container-type: inline-size/)
  assert.match(sharedStyles, /\.standard-pagination > nav \{ min-width: 0; padding: 0; display: block; \}/)
  assert.match(sharedStyles, /@container \(max-width: 480px\)[\s\S]*?\.standard-pagination \.pagination li:not\(:first-child\):not\(:last-child\):not\(\.active\) \{ display: none; \}/)
  assert.match(sharedStyles, /\.pagination-summary \.form-select \{ box-sizing: border-box; width: 108px; min-width: 108px; height: 32px; padding-right: 28px; font-size: 11px; \}/)
  assert.match(sharedStyles, /@container \(max-width: 480px\)[\s\S]*?\.standard-pagination \.pagination-summary \.form-select,[\s\S]*?\.standard-pagination\.compact \.pagination-summary \.form-select \{ width: 108px; min-width: 108px; padding-inline: 6px 32px; font-size: 10px; \}/)
  assert.match(sharedStyles, /@media \(max-width: 560px\) \{\s*\.real-app \.standard-pagination \{ grid-template-columns: minmax\(0, 1fr\) auto; \}\s*\.real-app \.standard-pagination \.pagination-summary \{ min-width: 0; justify-self: end; justify-content: flex-end; white-space: nowrap; \}/)
})

test('数据字典的类型列表和字典项无结果时隐藏共享分页', async () => {
  const source = await readFile(new URL('../src/views/configuration/dictionary/DictionaryView.vue', import.meta.url), 'utf8')

  assert.match(source, /<AdminPagination v-if="filteredTypes\.length > 0" compact :total="filteredTypes\.length"/)
  assert.match(source, /<PageState v-if="itemRows\.length === 0" kind="empty" title="没有符合当前条件的字典项" compact \/>/)
  assert.match(source, /<template v-else><AdminTableFrame label="字典项列表" has-actions :pin-actions="false">/)
  assert.match(source, /<AdminPagination :total="itemRows\.length" :page="itemPage"/)
})

test('正式管理列表仅在当前筛选结果非空时显示共享分页', async () => {
  const paths = [
    '../src/views/system/role/RoleView.vue',
    '../src/views/system/user/UserView.vue',
    '../src/views/system/organization/OrganizationView.vue',
    '../src/views/configuration/parameter/ParameterView.vue',
    '../src/views/configuration/external-system/ExternalSystemView.vue',
  ]
  const views = await Promise.all(paths.map(path => readFile(new URL(path, import.meta.url), 'utf8')))
  const resultCollections = ['filteredRoles', 'filtered', 'filtered', 'rows', 'filteredEndpointGroups']

  for (const [index, source] of views.entries()) {
    const resultCollection = resultCollections[index]
    assert.match(source, new RegExp(`<AdminPagination v-if="[^\"]*${resultCollection}\\.length[^\"]*" :total="${resultCollection}\\.length"`))
  }
})

test('所有含操作列的正式列表复用共享表格与行操作组', async () => {
  const paths = [
    '../src/views/audit/management/AuditView.vue',
    '../src/views/configuration/dictionary/DictionaryView.vue',
    '../src/views/configuration/external-system/ExternalSystemView.vue',
    '../src/views/configuration/parameter/ParameterView.vue',
    '../src/views/exchange/record/ExchangeRecordView.vue',
    '../src/views/master-data/batch/MasterDataBatchView.vue',
    '../src/views/master-data/directory/HospitalDirectoryView.vue',
    '../src/views/master-data/directory/Icd10DirectoryView.vue',
    '../src/views/master-data/directory/MedicalDirectoryView.vue',
    '../src/views/system/organization/OrganizationView.vue',
    '../src/views/system/role/RoleView.vue',
    '../src/views/system/user/UserView.vue',
  ]
  const [tableFrame, rowActions, ...views] = await Promise.all([
    readFile(tableFramePath, 'utf8'),
    readFile(rowActionsPath, 'utf8'),
    ...paths.map(path => readFile(new URL(path, import.meta.url), 'utf8')),
  ])
  const sharedStyles = await readFile(new URL('../src/assets/styles/prototype.css', import.meta.url), 'utf8')

  assert.match(tableFrame, /class="standard-table-frame"[\s\S]*class="prototype-table-wrap standard-table-scroll" role="region"/)
  assert.match(tableFrame, /'action-column-table': hasActions/)
  assert.match(rowActions, /role="group" :aria-label="label"/)
  assert.match(sharedStyles, /\.standard-row-actions \{ display: flex;[^}]*justify-content: flex-end/)
  for (const source of views) {
    assert.match(source, /<AdminTableFrame\b/)
    assert.match(source, /<ListRowActions\b/)
    assert.doesNotMatch(source, /class="[^"]*scroll-hint"/)
  }
})

test('每个含真实业务表格的正式页面和批次详情表格统一使用共享表格框架', async () => {
  const paths = [
    '../src/views/audit/management/AuditView.vue',
    '../src/views/configuration/dictionary/DictionaryView.vue',
    '../src/views/configuration/external-system/ExternalSystemView.vue',
    '../src/views/configuration/parameter/ParameterView.vue',
    '../src/views/database-contract/DatabaseContractMaintenanceView.vue',
    '../src/views/exchange/record/ExchangeRecordView.vue',
    '../src/views/master-data/batch/MasterDataBatchView.vue',
    '../src/views/master-data/batch/components/BatchAuditPanel.vue',
    '../src/views/master-data/batch/components/BatchExchangeRecordPanel.vue',
    '../src/views/master-data/batch/components/Icd10HisInvocationPanel.vue',
    '../src/views/master-data/directory/HospitalDirectoryView.vue',
    '../src/views/master-data/directory/Icd10DirectoryView.vue',
    '../src/views/master-data/directory/MedicalDirectoryView.vue',
    '../src/views/system/organization/OrganizationView.vue',
    '../src/views/system/role/RoleView.vue',
    '../src/views/system/user/UserView.vue',
  ]
  const views = await Promise.all(paths.map(path => readFile(new URL(path, import.meta.url), 'utf8')))

  for (const source of views) {
    assert.match(source, /<AdminTableFrame\b/)
    assert.match(source, /label="[^"]+"/)
  }
})

test('批次详情的 H I S 调用分页也使用共享分页组件并支持页大小变更', async () => {
  const [panel, detail] = await Promise.all([
    readFile(new URL('../src/views/master-data/batch/components/Icd10HisInvocationPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/master-data/batch/MasterDataBatchDetailView.vue', import.meta.url), 'utf8'),
  ])

  assert.match(panel, /<AdminPagination\b/)
  assert.match(panel, /@update:page-size="emit\('changePageSize', \$event\)"/)
  assert.match(detail, /getIcd10HisInvocations\(batch\.value\.id, page, pageSize\)/)
  assert.match(detail, /@change-page-size="changeHisInvocationPage\(1, \$event\)"/)
})

test('参数、字典與外部系统配置抽屉共享 DrawerFrame 并保留写入权限条件', async () => {
  const drawerPaths = [
    '../src/views/system/user/components/UserEditorDrawer.vue',
    '../src/views/system/role/components/RoleEditorDrawer.vue',
    '../src/views/system/organization/components/OrganizationEditorDrawer.vue',
    '../src/views/configuration/parameter/components/ParameterEditorDrawer.vue',
    '../src/views/configuration/dictionary/components/DictionaryEditorDrawer.vue',
    '../src/views/configuration/external-system/components/ExternalSystemEditorDrawer.vue',
    '../src/views/configuration/external-system/components/ExternalEndpointEditorDrawer.vue',
  ]
  const drawers = await Promise.all(drawerPaths.map(path => readFile(new URL(path, import.meta.url), 'utf8')))

  for (const source of drawers) {
    assert.match(source, /import DrawerFrame from '@\/components\/DrawerFrame\.vue'/)
    assert.match(source, /<DrawerFrame\b/)
    assert.doesNotMatch(source, /useModalDialog/)
  }
  assert.match(drawers[0], /v-if="canWrite" type="button" :class="\{ active: activeSection === 'password' \}"/)
  assert.match(drawers[1], /selected\?\.systemManaged/)
  assert.match(drawers[2], /form="organization-form"/)
  assert.match(drawers[3], /v-if="canWrite" class="prototype-button" type="submit"/)
  assert.match(drawers[5], /v-if="canWrite" class="prototype-button" type="submit" form="system-form"/)
  assert.match(drawers[6], /v-if="canWrite" class="prototype-button" type="submit" form="endpoint-form"/)
  assert.match(drawers[4], /<template #footer>[\s\S]*?type="button"[\s\S]*?取消[\s\S]*?type="submit" :form="isType \? 'dictionary-type-form' : 'dictionary-item-form'"/)
  assert.match(drawers[4], /<form v-if="isType" id="dictionary-type-form"/)
  assert.match(drawers[4], /<form v-else id="dictionary-item-form"/)
  assert.doesNotMatch(drawers[4], /class="work-inline-actions"/)
})

test('审计、HIS 调用和同步批次详情使用共享对话框或抽屉框架', async () => {
  const [audit, exchange, batch, startBatch] = await Promise.all([
    readFile(new URL('../src/views/audit/management/AuditView.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/exchange/record/ExchangeRecordView.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/master-data/batch/MasterDataBatchView.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/master-data/batch/components/StartBatchDrawer.vue', import.meta.url), 'utf8'),
  ])

  assert.match(audit, /<ModalFrame\b[^>]*labelled-by="audit-detail-title"/)
  assert.match(exchange, /<ModalFrame\b[^>]*labelled-by="exchange-detail-title"/)
  assert.match(batch, /<DrawerFrame\b[^>]*labelled-by="batch-detail-title"/)
  assert.match(startBatch, /<ModalFrame\b[^>]*labelled-by="batch-start-title"/)
  for (const source of [audit, exchange, batch, startBatch]) assert.doesNotMatch(source, /useModalDialog/)
})

test('只读审计和HIS记录列表共用页面状态视觉组件并保留业务文案', async () => {
  const [audit, exchange] = await Promise.all([
    readFile(new URL('../src/views/audit/management/AuditView.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/exchange/record/ExchangeRecordView.vue', import.meta.url), 'utf8'),
  ])

  for (const source of [audit, exchange]) {
    assert.match(source, /import PageState from '@\/components\/PageState\.vue'/)
    assert.match(source, /<PageState v-if="isLoading" kind="loading"/)
    assert.match(source, /<PageState v-else-if="!error && .*\.length === 0" kind="empty"/)
    assert.doesNotMatch(source, /\.page-state \{/)
  }
  assert.match(audit, /当前账号机构范围内的脱敏记录/)
  assert.match(exchange, /不会补造记录/)
})

test('基础目录共用列表状态组件并保留可重试错误行为', async () => {
  const [directory, icd10] = await Promise.all([
    readFile(new URL('../src/views/master-data/directory/DirectoryLayout.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/master-data/directory/Icd10DirectoryView.vue', import.meta.url), 'utf8'),
  ])

  for (const source of [directory, icd10]) {
    assert.match(source, /import PageState from '@\/components\/PageState\.vue'/)
    assert.match(source, /<PageState v-if="error" kind="error"/)
    assert.match(source, /<PageState v-else-if="isLoading" kind="loading"/)
  }
  assert.match(directory, /emit\('retry'\)/)
  assert.match(icd10, /@click="load\(\)"/)
})

test('常用管理列表复用页面状态组件并移除页面专属状态面板尺寸', async () => {
  const viewPaths = [
    '../src/views/system/user/UserView.vue',
    '../src/views/system/role/RoleView.vue',
    '../src/views/system/organization/OrganizationView.vue',
    '../src/views/configuration/parameter/ParameterView.vue',
    '../src/views/configuration/dictionary/DictionaryView.vue',
    '../src/views/configuration/external-system/ExternalSystemView.vue',
    '../src/views/master-data/batch/MasterDataBatchView.vue',
  ]
  const views = await Promise.all(viewPaths.map(path => readFile(new URL(path, import.meta.url), 'utf8')))

  for (const source of views) {
    assert.match(source, /import PageState from '@\/components\/PageState\.vue'/)
    assert.match(source, /<PageState\b/)
    assert.doesNotMatch(source, /\.page-state \{ min-height:/)
  }
})
