import assert from 'node:assert/strict'
import test from 'node:test'
import { createPrototypeSession } from '../src/views/prototype/model/prototypeAuth.ts'

test('原型视角不依赖账号密码并明确标记合成身份', () => {
  assert.deepEqual(createPrototypeSession('manager'), {
    loginName: 'prototype-manager', displayName: '管理视角', role: 'manager',
  })
  assert.deepEqual(createPrototypeSession('operator'), {
    loginName: 'prototype-operator', displayName: '运维视角', role: 'operator',
  })
})
