import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const page = await readFile(new URL('../src/views/configuration/external-system/ExternalSystemView.vue', import.meta.url), 'utf8')

test('failed endpoint reads use an error state instead of the no-organizations empty state', () => {
  assert.match(page, /const endpointError = ref<ApiClientError \| null>\(null\)/)
  assert.match(page, /if \(apiError\.code !== 'REQUEST_ABORTED' && !await handleUnauthorized\(apiError\)\) endpointError\.value = apiError/)
  assert.match(page, /<PageState v-else-if="endpointError" kind="error" title="暂时无法读取机构接口配置"/)
  assert.match(page, /v-if="!endpointError && endpointGroups\.length"/)
  assert.match(page, /<PageState v-else-if="selectedSystem && endpointGroups\.length === 0" kind="empty" title="还没有可展示的机构"[\s\S]*?compact>/)
  assert.match(page, /:description="`当前没有可见的机构或接口配置；请检查机构数据范围，或先为 \$\{selectedSystem\.systemName\} 添加接口配置。`"/)
  assert.match(page, /<template v-if="canWrite && selectedSystem\.enabled" #actions><button class="prototype-button"[^>]*>[^<]*<Plus[^>]*\/>新增机构接口配置/)
  assert.match(page, /\.system-context \{[^}]*grid-template-columns: max-content minmax\(240px, 440px\) max-content max-content;[^}]*justify-content: space-between;/)
  assert.match(page, /<AdminPagination v-if="!endpointError && filteredEndpointGroups\.length"/)
  assert.match(page, /:disabled="!selectedSystem\.enabled \|\| Boolean\(endpointError\)"/)
})

test('failed initial external-system reads expose a retryable shared error state', () => {
  assert.match(page, /<section v-else-if="error && systems\.length === 0"[^>]*><PageState kind="error" title="暂时无法读取外部系统"[\s\S]*?@click="loadPage\(\)"/)
})

test('failed background refreshes identify the retained external-system list as the last successful result', () => {
  assert.match(page, /error && !isLoading && systems\.length[^>]*role="alert"[^>]*>[\s\S]*?error\.message }}；以下为上次成功读取的数据[\s\S]*?@click="loadPage\(true\)"/)
})
