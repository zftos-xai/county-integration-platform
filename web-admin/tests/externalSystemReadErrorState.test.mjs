import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const page = await readFile(new URL('../src/views/configuration/external-system/ExternalSystemView.vue', import.meta.url), 'utf8')

test('failed endpoint reads use an error state instead of the no-organizations empty state', () => {
  assert.match(page, /const endpointError = ref<ApiClientError \| null>\(null\)/)
  assert.match(page, /if \(apiError\.code !== 'REQUEST_ABORTED' && !await handleUnauthorized\(apiError\)\) endpointError\.value = apiError/)
  assert.match(page, /<PageState v-else-if="endpointError" kind="error" title="暂时无法读取机构接口配置"/)
  assert.match(page, /v-if="!endpointError && endpointGroups\.length"/)
  assert.match(page, /<div v-else-if="selectedSystem && endpointGroups\.length === 0" class="endpoint-empty"/)
  assert.match(page, /\.system-context \{[^}]*grid-template-columns: max-content minmax\(240px, 440px\) max-content max-content;[^}]*justify-content: space-between;/)
  assert.match(page, /<AdminPagination v-if="!endpointError && filteredEndpointGroups\.length"/)
  assert.match(page, /:disabled="!selectedSystem\.enabled \|\| Boolean\(endpointError\)"/)
})

test('failed initial external-system reads expose a retryable shared error state', () => {
  assert.match(page, /<section v-else-if="error && systems\.length === 0"[^>]*><PageState kind="error" title="暂时无法读取外部系统"[\s\S]*?@click="loadPage\(\)"/)
})
