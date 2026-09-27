import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('角色列表工具栏在桌面端对齐筛选项和操作并随窄屏分组换行', async () => {
  const source = await readFile('web-admin/src/views/system/role/RoleView.vue', 'utf8')
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(source, /<ListQueryToolbar class="role-toolbar" filters-layout="search-with-three-selects"/)
  assert.match(source, /standard-list-filter--attention/)
  assert.doesNotMatch(source, /\.role-page :deep\(\.standard-list-toolbar/)
  assert.match(sharedStyles, /\.standard-list-toolbar__filters \{[^}]*grid-template-columns: repeat\(auto-fill, minmax\(min\(240px, 100%\), 1fr\)\)/)
  assert.match(sharedStyles, /\.standard-list-toolbar \.prototype-search \{ justify-self: stretch; width: 100%; min-width: 0; max-width: none; \}/)
  assert.match(sharedStyles, /@container \(max-width: 560px\)[\s\S]*?\.standard-list-toolbar__filters \{ grid-template-columns: minmax\(0, 1fr\); \}/)
  assert.match(sharedStyles, /\.standard-list-toolbar__commands \{ grid-column: 1 \/ -1; grid-row: 2;/)
  assert.match(sharedStyles, /\.standard-list-toolbar--search-with-three-selects > \.standard-list-toolbar__filters \{[^}]*grid-template-areas: "search status type attention"; grid-template-columns: minmax\(240px, 1fr\) repeat\(3, minmax\(160px, 220px\)\);/)
  assert.match(sharedStyles, /@container \(min-width: 561px\) and \(max-width: 760px\)[\s\S]*?\.standard-list-toolbar--search-with-three-selects > \.standard-list-toolbar__filters \{[^}]*grid-template-areas: "search search search" "status type attention";/)
  assert.match(sharedStyles, /@container \(max-width: 560px\)[\s\S]*?\.standard-list-toolbar--search-with-three-selects > \.standard-list-toolbar__filters \{[^}]*grid-template-areas: "search" "status" "type" "attention";/)
  assert.match(source, /<AdminTableFrame[^>]*label="角色列表"[^>]*has-actions/)
  assert.doesNotMatch(source, /@container \(max-width: 560px\)[\s\S]*?standard-list-toolbar__filters/)
})

test('角色列表卡片随内容收缩并让分页紧跟表格', async () => {
  const source = await readFile('web-admin/src/views/system/role/RoleView.vue', 'utf8')
  const panelRule = source.match(/\.role-panel \{([^}]*)\}/)?.[1] ?? ''

  assert.match(panelRule, /--action-column-width: 156px/)
  assert.doesNotMatch(panelRule, /min-height/)
  assert.match(source, /<PageState v-else-if="!error && filteredRoles\.length === 0" kind="empty" title="没有符合当前筛选条件的角色" compact>/)
  assert.match(source, /<AdminTableFrame v-else-if="filteredRoles\.length" label="角色列表" has-actions>/)
  assert.doesNotMatch(source, /filteredRoles\.length === 0" class="prototype-empty"/)
  assert.match(source, /<AdminPagination v-if="!isLoading && filteredRoles.length"/)
})

test('角色详情抽屉基础操作固定在共享底栏且仍提交对应表单', async () => {
  const source = await readFile('web-admin/src/views/system/role/components/RoleEditorDrawer.vue', 'utf8')

  assert.match(source, /<form id="role-base-form" class="role-form"/)
  assert.match(source, /<template v-if="showBaseActions" #footer>/)
  assert.match(source, /type="submit" form="role-base-form"/)
  assert.ok(source.includes(`<button v-else class="prototype-button" type="button" @click="emit('edit')">编辑基础信息</button>`))
  assert.ok(!source.includes(`<footer v-if="mode === 'create' || mode === 'edit'">`))
})
