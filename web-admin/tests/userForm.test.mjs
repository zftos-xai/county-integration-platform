import assert from 'node:assert/strict'
import test from 'node:test'
import { registerHooks } from 'node:module'

// Node 测试没有 Vite 别名解析，映射到真实源码，不替换校验实现。
registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier.startsWith('@/')) {
      return nextResolve(new URL(`../src/${specifier.slice(2)}.ts`, import.meta.url).href, context)
    }
    return nextResolve(specifier, context)
  },
})

const {
  emptyUserForm, toCreateUserInput, toUpdateUserInput,
  userToForm, validateTemporaryPassword, validateUserForm,
} = await import('../src/views/system/user/form.ts')

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
  assert.equal(validateTemporaryPassword('short', 'operator'), '临时密码至少 9 个字符，UTF-8 编码不能超过 72 字节')
})

test('密码按 UTF-8 字节限制，不截断多字节字符', () => {
  assert.equal(validateTemporaryPassword('a'.repeat(72), ''), null)
  assert.equal(validateTemporaryPassword('中'.repeat(24), ''), null)
  assert.match(validateTemporaryPassword('a'.repeat(73), ''), /72 字节/)
  assert.match(validateTemporaryPassword('中'.repeat(25), ''), /72 字节/)
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
