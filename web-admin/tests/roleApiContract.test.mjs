import assert from 'node:assert/strict'
import test from 'node:test'
import { roleDetailPath, rolePermissionsPath, roleWriteRequest } from '../src/api/system/roleContract.ts'

test('角色详情与权限路径按稳定契约生成', () => {
  assert.equal(roleDetailPath(7), '/roles/7')
  assert.equal(rolePermissionsPath(7), '/roles/7/permissions')
})

test('角色写请求保留并发版本和目标权限集合', () => {
  const update = roleWriteRequest('PUT', { roleName: '审核员', enabled: false, version: 'AAAAAAAAAAE=' })
  const permissions = roleWriteRequest('PUT', { permissionCodes: ['audit:read'] })

  assert.equal(update.method, 'PUT')
  assert.equal(JSON.parse(update.body).version, 'AAAAAAAAAAE=')
  assert.deepEqual(JSON.parse(permissions.body), { permissionCodes: ['audit:read'] })
})
