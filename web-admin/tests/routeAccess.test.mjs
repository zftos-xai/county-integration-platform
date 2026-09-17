import assert from 'node:assert/strict'
import test from 'node:test'
import { resolveRouteAccess } from '../src/utils/routeAccess.ts'

const user = {
  userId: 1,
  loginName: 'admin',
  displayName: 'Administrator',
  primaryOrganizationId: 1,
  organizationCode: 'ORG001',
  mustChangePassword: false,
  permissions: ['organization:read'],
  organizationCodes: ['ORG001'],
}

test('未登录主体只能进入公开页面', () => {
  assert.equal(resolveRouteAccess(null, { path: '/prototype', publicPage: true, guestOnly: false }), 'allow')
  assert.equal(resolveRouteAccess(null, { path: '/organizations', publicPage: false, guestOnly: false }), 'login')
})

test('首次登录主体被限制在修改密码页面', () => {
  const firstLogin = { ...user, mustChangePassword: true, permissions: ['password:change'] }
  assert.equal(resolveRouteAccess(firstLogin, { path: '/', publicPage: false, guestOnly: false }), 'change-password')
  assert.equal(resolveRouteAccess(firstLogin, { path: '/change-password', publicPage: false, guestOnly: false }), 'allow')
})

test('直接URL访问继续执行功能权限检查', () => {
  assert.equal(resolveRouteAccess(user, { path: '/organizations', publicPage: false, guestOnly: false, requiredPermission: 'organization:read' }), 'allow')
  assert.equal(resolveRouteAccess(user, { path: '/users', publicPage: false, guestOnly: false, requiredPermission: 'identity:read' }), 'forbidden')
})
