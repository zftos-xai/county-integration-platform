import assert from 'node:assert/strict'
import test from 'node:test'
import {
  userDetailPath, userEnabledPath, userOrganizationScopesPath,
  userPasswordResetPath, userRolesPath, userWriteRequest,
} from '../src/api/system/userApiPaths.ts'

test('用户管理路径与授权子资源符合接口定义', () => {
  assert.equal(userDetailPath(9), '/users/9')
  assert.equal(userEnabledPath(9), '/users/9/enabled')
  assert.equal(userPasswordResetPath(9), '/users/9/password-reset')
  assert.equal(userRolesPath(9), '/users/9/roles')
  assert.equal(userOrganizationScopesPath(9), '/users/9/organization-scopes')
})

test('用户写请求保留并发版本且不改写敏感字段', () => {
  const update = userWriteRequest('PUT', {
    displayName: '系统管理员', primaryOrganizationId: 2, version: 'AAAAAAAAAAE=',
  })
  const reset = userWriteRequest('POST', { temporaryPassword: 'Temporary@123' })

  assert.equal(update.method, 'PUT')
  assert.equal(JSON.parse(update.body).version, 'AAAAAAAAAAE=')
  assert.equal(JSON.parse(reset.body).temporaryPassword, 'Temporary@123')
})
