import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const pages = [
  ['用户管理', '../src/views/system/user/UserView.vue', 'users', 'loadPage()'],
  ['角色权限', '../src/views/system/role/RoleView.vue', 'roles', 'loadPage()'],
  ['参数配置', '../src/views/configuration/parameter/ParameterView.vue', 'definitions', 'loadPage()'],
  ['数据字典', '../src/views/configuration/dictionary/DictionaryView.vue', 'types', 'loadTypes()'],
  ['HIS调用记录', '../src/views/exchange/record/ExchangeRecordView.vue', 'records', 'loadRecords'],
]

for (const [name, path, collection, retry] of pages) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')

  test(`${name}首次读取失败显示共享错误状态和重试入口`, () => {
    assert.match(source, new RegExp(`<PageState v-else-if="error && ${collection}\\.length === 0" kind="error"`))
    assert.match(source, new RegExp(`<template #actions><button class="work-quiet-button" type="button" @click="${retry.replace(/[()]/g, '\\$&')}`))
  })
}

test('用户、角色、参数和字典已有列表数据时仍以顶部横幅表达刷新错误', async () => {
  for (const [name, path, collection] of pages.slice(0, 4)) {
    const source = await readFile(new URL(path, import.meta.url), 'utf8')
    assert.match(source, new RegExp(`error && !isLoading && ${collection}\\.length > 0`), name)
  }
})

test('同步批次首次读取失败使用共享错误状态，刷新失败保留已加载记录', async () => {
  const source = await readFile(
    new URL('../src/views/master-data/batch/MasterDataBatchView.vue', import.meta.url),
    'utf8',
  )

  assert.match(source, /<PageState v-else-if="error && batches\.length === 0" kind="error" title="暂时无法读取同步批次" compact/)
  assert.match(source, /error && !isLoading && batches\.length > 0/)
  assert.match(source, /if \(!background\) \{\s*batches\.value = \[\];\s*total\.value = 0;\s*\}/)
  assert.match(source, /<AdminTableFrame v-else-if="batches\.length"/)
  assert.match(source, /@click="reloadPage"><RefreshCw :size="15" \/>重试读取/)
})
