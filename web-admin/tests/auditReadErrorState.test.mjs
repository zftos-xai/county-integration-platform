import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const page = await readFile(new URL('../src/views/audit/management/AuditView.vue', import.meta.url), 'utf8')

test('审计表格在窄视口中折行长操作码与对象编号', () => {
  assert.match(page, /\.audit-table td:nth-child\(3\) strong \{ overflow-wrap: anywhere; \}/)
  assert.match(page, /\.audit-table td small \{[^}]*overflow-wrap: anywhere; white-space: normal;/)
})

test('failed audit filter does not leave results from the previous filter under the new conditions', () => {
  assert.match(page, /await router\.replace\(\{ path: '\/audit', query \}\)\s+events\.value = \[\]\s+hasMore\.value = false\s+page\.value = 1\s+await loadEvents\(\)/)
  assert.match(page, /<PageState v-else-if="error && events\.length === 0" kind="error"[\s\S]*?@click="loadEvents\(\)"\>重试/)
})

test('failed audit refresh marks retained results as possibly out of date', () => {
  assert.match(page, /v-if="error && !isLoading && events\.length"/)
  assert.match(page, /当前显示上次成功读取的数据，可能不是最新结果。/)
})

test('audit history uses a fixed bounded fetch size without a redundant limit filter', () => {
  assert.match(page, /const AUDIT_FETCH_BATCH_SIZE = 100/)
  assert.match(page, /limit: AUDIT_FETCH_BATCH_SIZE/)
  assert.doesNotMatch(page, /最多读取条数|查看最近|audit-limit/)
  assert.match(page, /v-if="hasMore"[^>]*@click="loadEvents\(true\)"/)
})
