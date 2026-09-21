import assert from 'node:assert/strict'
import test from 'node:test'
import {
  emptyUserForm, toCreateUserInput, toUpdateUserInput,
  userToForm, validateTemporaryPassword, validateUserForm,
} from '../src/views/system/user/form.ts'

test('用户表单拒绝非法登录名、机构和临时密码', () => {
  const form = emptyUserForm()
  assert.equal(validateUserForm(form, true), '请输入登录名')
  form.loginName = 'a b'
  assert.match(validateUserForm(form, true), /登录名需为/)
  form.loginName = 'operator'
  form.displayName = '运维人员'
  assert.equal(validateUserForm(form, true), '请选择主要机构')
  form.primaryOrganizationId = '2'
  form.temporaryPassword = 'operator@123456'
  assert.equal(validateUserForm(form, true), '临时密码不能包含登录名')
  assert.equal(validateTemporaryPassword('short', 'operator'), '临时密码长度需为 9—128 个字符')
})

test('用户表单映射保留rowversion且规范化登录名', () => {
  const user = {
    id: 7, loginName: 'Admin.User', displayName: '管理员', primaryOrganizationId: 2,
    organizationCode: 'HOSPITAL.001', enabled: true, mustChangePassword: false,
    createdAt: '2026-09-17T00:00:00', updatedAt: '2026-09-17T00:00:00',
    version: 'AAAAAAAAAAE=', roleIds: [1], organizationScopeIds: [2],
  }
  const form = userToForm(user)
  assert.equal(toUpdateUserInput(form).version, 'AAAAAAAAAAE=')
  form.temporaryPassword = 'Temporary@123'
  assert.deepEqual(toCreateUserInput(form), {
    loginName: 'admin.user', displayName: '管理员', primaryOrganizationId: 2,
    temporaryPassword: 'Temporary@123',
  })
})
