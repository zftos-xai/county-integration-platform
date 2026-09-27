import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const pagePaths = [
  '../src/views/configuration/dictionary/DictionaryView.vue',
  '../src/views/configuration/external-system/ExternalSystemView.vue',
  '../src/views/configuration/parameter/ParameterView.vue',
  '../src/views/system/organization/OrganizationView.vue',
  '../src/views/system/role/RoleView.vue',
  '../src/views/system/user/UserView.vue',
]

test('正式管理页使用共享确认对话框处理危险动作与未保存离开', async () => {
  const pages = await Promise.all(pagePaths.map(path => readFile(new URL(path, import.meta.url), 'utf8')))
  for (const source of pages) {
    assert.doesNotMatch(source, /window\.confirm\(/)
    assert.match(source, /useConfirmationDialog\(/)
    assert.match(source, /<ConfirmationDialog\b/)
  }
})

test('共享确认框复用标准对话框并统一取消、危险操作和可访问标题', async () => {
  const [dialog, modal] = await Promise.all([
    readFile(new URL('../src/components/ConfirmationDialog.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/ModalFrame.vue', import.meta.url), 'utf8'),
  ])
  assert.match(dialog, /<ModalFrame/)
  assert.match(dialog, /labelled-by="confirmation-dialog-title"/)
  assert.match(dialog, /request\.danger \? 'work-danger-button'/)
  assert.match(dialog, /@click="emit\('cancel'\)"/)
  assert.match(dialog, /@click="emit\('confirm'\)"/)
  assert.match(modal, /role="dialog"[\s\S]*aria-modal="true"[\s\S]*:aria-labelledby="labelledBy"/)
  assert.match(modal, /@keydown="handleDialogKeydown"/)
})
