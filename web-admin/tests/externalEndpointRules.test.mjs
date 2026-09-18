import test from 'node:test'
import assert from 'node:assert/strict'
import { endpointReadinessError } from '../src/views/prototype/model/externalEndpointRules.ts'

const valid = { baseUrl: 'https://his.example.invalid/api', connectTimeout: 3000, readTimeout: 15000, tls: true, credentialRef: 'vault://his/production' }

test('外部系统服务地址仅允许 HTTPS、TLS 和凭证引用', () => {
  assert.equal(endpointReadinessError(valid), null)
  assert.match(endpointReadinessError({ ...valid, baseUrl: 'http://his.local' }), /HTTPS/)
  assert.match(endpointReadinessError({ ...valid, tls: false }), /TLS/)
  assert.match(endpointReadinessError({ ...valid, credentialRef: 'secret-value' }), /引用 URI/)
})

test('外部系统服务地址限制连接与读取超时', () => {
  assert.match(endpointReadinessError({ ...valid, connectTimeout: 99 }), /连接超时/)
  assert.match(endpointReadinessError({ ...valid, readTimeout: 2000 }), /读取超时/)
  assert.match(endpointReadinessError({ ...valid, readTimeout: 300001 }), /读取超时/)
})
