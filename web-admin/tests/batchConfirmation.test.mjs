import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('同步批次取消统一通过共享确认对话框', async () => {
  const batch = await readFile(new URL('../src/views/master-data/batch/MasterDataBatchView.vue', import.meta.url), 'utf8')
  assert.match(batch, /async function cancelBatch[\s\S]*?const confirmed = await confirm\(\{/)
  assert.match(batch, /<ConfirmationDialog[\s\S]*?@confirm="resolveConfirmation\(true\)"/)
  assert.doesNotMatch(batch, /confirmCancelId/)
})
