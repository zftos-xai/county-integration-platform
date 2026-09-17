import assert from 'node:assert/strict'
import test from 'node:test'
import {
  emptyOrganizationForm, localInputToOffset, organizationToForm,
  toCreateInput, validateOrganizationForm,
} from '../src/views/system/organization/form.ts'

test('机构表单拒绝非法编码和倒置有效期', () => {
  const form = emptyOrganizationForm()
  form.organizationCode = '非法 编码'
  form.organizationName = '测试机构'
  form.organizationType = 'CLINIC'
  form.parentId = '1'
  assert.match(validateOrganizationForm(form, true), /机构编码/)

  form.organizationCode = 'CLINIC.001'
  form.validFrom = '2026-09-18T08:00'
  form.validTo = '2026-09-17T08:00'
  assert.match(validateOrganizationForm(form, true), /开始时间/)
})

test('datetime-local按浏览器本地时区转换为带时区API值', () => {
  const actual = localInputToOffset('2026-09-17T08:30')
  assert.equal(actual, new Date('2026-09-17T08:30').toISOString())
})

test('详情映射保留rowversion且创建请求清理文本', () => {
  const form = organizationToForm({
    id: 2, organizationCode: 'CLINIC.001', organizationName: '第一卫生院', organizationType: 'CLINIC',
    parentId: 1, enabled: true, validFrom: null, validTo: null,
    createdAt: '2026-09-17T00:00:00', updatedAt: '2026-09-17T00:00:00', version: 'AAAAAAAAAAE=',
  })
  assert.equal(form.version, 'AAAAAAAAAAE=')
  form.organizationName = '  第一卫生院  '
  assert.equal(toCreateInput(form).organizationName, '第一卫生院')
})
