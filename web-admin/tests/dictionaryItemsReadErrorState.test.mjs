import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const page = await readFile(new URL('../src/views/configuration/dictionary/DictionaryView.vue', import.meta.url), 'utf8')

test('failed dictionary-item reads show a retryable error instead of a false empty state', () => {
  assert.match(page, /const itemError = ref<ApiClientError \| null>\(null\)/)
  assert.match(page, /if \(apiError\.code !== 'REQUEST_ABORTED' && !await handleUnauthorized\(apiError\)\) itemError\.value = apiError/)
  assert.match(page, /<PageState v-else-if="itemError" kind="error" title="暂时无法读取字典项"[\s\S]*?selectType\(selectedType, false\)/)
  assert.match(page, /<PageState v-else-if="items\.length === 0" kind="empty" title="该类型暂无字典项"/)
  assert.match(page, /<p v-if="!itemError" class="dictionary-meta">/)
})

test('dictionary item mutations requiring a loaded item list stay disabled while that list is unavailable', () => {
  assert.match(page, /:disabled="deletingId !== null \|\| Boolean\(itemError\)"/)
  assert.match(page, /:disabled="Boolean\(itemError\)" :title="itemError \? '读取字典项失败，重试成功后再新增'/)
  assert.match(page, /function openItemCreate\(\) \{ if \(!selectedType\.value \|\| itemError\.value\) return;/)
})
