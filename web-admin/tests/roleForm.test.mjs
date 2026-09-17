import assert from 'node:assert/strict'
import test from 'node:test'
import {
  emptyRoleForm, roleToForm, toCreateRoleInput, toUpdateRoleInput, validateRoleForm,
} from '../src/views/system/role/form.ts'

test('角色表单拒绝非法和系统保护代码', () => {
  const form = emptyRoleForm()
  form.roleCode = 'bad-code'
  form.roleName = '审核员'
  assert.match(validateRoleForm(form, true), /角色代码/)

  form.roleCode = 'PLATFORM_ADMIN'
  assert.match(validateRoleForm(form, true), /系统保护/)
})

test('角色表单规范化代码并保留并发版本', () => {
  const form = roleToForm({
    id: 2, roleCode: 'AUDITOR', roleName: '审核员', enabled: true, systemManaged: false,
    createdAt: '2026-09-17T00:00:00', updatedAt: '2026-09-17T00:00:00',
    version: 'AAAAAAAAAAE=', permissionCodes: ['audit:read'],
  })
  form.roleCode = ' auditor '
  form.roleName = ' 审核员 '

  assert.deepEqual(toCreateRoleInput(form), { roleCode: 'AUDITOR', roleName: '审核员' })
  assert.equal(toUpdateRoleInput(form).version, 'AAAAAAAAAAE=')
})
