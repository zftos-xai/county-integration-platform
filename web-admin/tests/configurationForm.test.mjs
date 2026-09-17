import assert from 'node:assert/strict'
import test from 'node:test'
import { parameterToForm, toUpsertParameterInput, validateParameterForm } from '../src/views/configuration/parameter/form.ts'
import {
  emptyDictionaryItemForm, emptyDictionaryTypeForm, toCreateDictionaryItemInput,
  validateDictionaryItemForm, validateDictionaryTypeForm,
} from '../src/views/configuration/dictionary/form.ts'

const definition = {
  key: 'batch.size', name: '批次大小', valueType: 'INTEGER', environments: ['TEST', 'PRODUCTION'],
  organizationScoped: false, sensitive: false, maximumLength: 4, minimumNumber: 1, maximumNumber: 1000, pattern: null,
}

test('参数表单采用注册环境并校验数值边界', () => {
  const form = parameterToForm(definition, null, 9)
  assert.equal(form.environment, 'TEST')
  form.value = '1001'
  assert.equal(validateParameterForm(definition, form), '参数值不能大于 1000')
  form.value = '50'
  assert.equal(validateParameterForm(definition, form), null)
  assert.deepEqual(toUpsertParameterInput(form), { environment: 'TEST', organizationId: null, value: '50', enabled: true })
})

test('敏感参数编辑时必须输入新值，避免把掩码或空值写回', () => {
  const secret = { ...definition, key: 'secret.value', valueType: 'STRING', sensitive: true, maximumLength: 100, minimumNumber: null, maximumNumber: null }
  const existing = { id: 1, parameterKey: 'secret.value', valueType: 'STRING', environment: 'TEST', organizationId: null, organizationCode: null, value: '******', configured: true, enabled: true, updatedAt: '2026-01-01T00:00:00', version: 'v1' }
  const form = parameterToForm(secret, existing, 9)
  assert.equal(form.value, '')
  assert.equal(validateParameterForm(secret, form), '请输入新的敏感参数值')
  form.value = 'new-secret-reference'
  assert.equal(validateParameterForm(secret, form), null)
})

test('字典表单限制稳定代码与排序范围', () => {
  const type = emptyDictionaryTypeForm()
  type.typeCode = '1bad'; type.typeName = '显示模式'; type.description = '控制显示方式'
  assert.match(validateDictionaryTypeForm(type, true), /类型代码/)
  const item = emptyDictionaryItemForm()
  item.itemCode = 'COMPACT'; item.itemLabel = '紧凑'; item.sortOrder = '10'
  assert.equal(validateDictionaryItemForm(item, true), null)
  assert.deepEqual(toCreateDictionaryItemInput(item), { itemCode: 'COMPACT', itemLabel: '紧凑', sortOrder: 10 })
})
