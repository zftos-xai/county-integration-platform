import assert from 'node:assert/strict'
import test from 'node:test'
import {
  configurationWriteRequest, dictionaryItemPath, dictionaryItemsPath, dictionaryTypePath, parameterValuePath,
} from '../src/api/system/configurationContract.ts'

test('配置管理路径按 OpenAPI 契约生成', () => {
  assert.equal(parameterValuePath('display.mode'), '/configuration/parameters/display.mode')
  assert.equal(parameterValuePath('name/unsafe'), '/configuration/parameters/name%2Funsafe')
  assert.equal(dictionaryTypePath(8), '/configuration/dictionaries/8')
  assert.equal(dictionaryItemsPath(8), '/configuration/dictionaries/8/items')
  assert.equal(dictionaryItemsPath(8, true), '/configuration/dictionaries/8/items?includeDisabled=true')
  assert.equal(dictionaryItemPath(12), '/configuration/dictionary-items/12')
})

test('配置写请求保留 rowversion 和明确方法', () => {
  const request = configurationWriteRequest('PUT', { enabled: false, version: 'AAAAAAAAAAE=' })
  assert.equal(request.method, 'PUT')
  assert.deepEqual(JSON.parse(request.body), { enabled: false, version: 'AAAAAAAAAAE=' })
})
