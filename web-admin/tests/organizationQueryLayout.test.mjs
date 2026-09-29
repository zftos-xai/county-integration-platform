import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const page = await readFile(new URL('../src/views/system/organization/OrganizationView.vue', import.meta.url), 'utf8')
const sharedToolbar = await readFile(new URL('../src/assets/styles/prototype.css', import.meta.url), 'utf8')

test('机构筛选在宽屏同行显示，在较窄视口按顺序换行', () => {
  assert.match(page, /<ListQueryToolbar filters-layout="search-with-selects"/)
  assert.match(page, /class="prototype-search standard-list-filter--search"/)
  assert.match(page, /class="status-field standard-list-filter--status"/)
  assert.match(page, /class="standard-list-filter--type"/)
  assert.match(page, /\.organization-query-container \{ min-width: 0; \}/)
  assert.doesNotMatch(page, /\.organization-query-container :deep\(\.standard-list-toolbar/)

  assert.match(sharedToolbar, /\.standard-list-toolbar--search-with-selects > \.standard-list-toolbar__filters \{ grid-column: 1; grid-row: 1; display: grid; grid-template-areas: "search status type";/)
  assert.match(sharedToolbar, /@container \(min-width: 381px\) and \(max-width: 760px\)[\s\S]*?grid-template-areas: "search search" "status type";/)
  assert.match(sharedToolbar, /@container \(max-width: 560px\)[\s\S]*?grid-template-areas: "search" "status" "type";/)
  assert.match(sharedToolbar, /\.standard-list-toolbar__commands > \.standard-list-toolbar__refresh \{ margin-inline-start: auto; white-space: nowrap; \}/)
})

test('机构列表初次读取失败显示可重试的共享错误状态，后台刷新失败保留旧数据', () => {
  assert.match(page, /error && !isLoading && organizations\.length > 0/)
  assert.match(page, /<PageState v-else-if="error && organizations\.length === 0" kind="error" title="无法读取机构列表"/)
  assert.match(page, /<template #actions><button class="work-quiet-button" type="button" @click="loadOrganizations\(\)">[\s\S]*?重试<\/button><\/template>/)
})
