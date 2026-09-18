import test from 'node:test'
import assert from 'node:assert/strict'
import { endpointReadinessError } from '../src/views/prototype/model/externalEndpointRules.ts'

const valid = { baseUrl: 'https://his.example.invalid/api', connectTimeout: 3000, readTimeout: 15000, tls: true, credentialRef: 'vault://his/production' }

test('外部系统服务地址接受匹配实际协议的 HTTP 或 HTTPS 地址', () => {
  assert.equal(endpointReadinessError(valid), null)
  assert.equal(endpointReadinessError({ ...valid, baseUrl: 'http://his.local/WebService.asmx', tls: false }), null)
  assert.match(endpointReadinessError({ ...valid, tls: false }), /TLS/)
  assert.match(endpointReadinessError({ ...valid, baseUrl: 'http://his.local', tls: true }), /不能标记为 TLS/)
  assert.match(endpointReadinessError({ ...valid, baseUrl: 'ftp://his.local', tls: false }), /HTTP 或 HTTPS/)
  assert.match(endpointReadinessError({ ...valid, baseUrl: 'http://his.local/api?op=PHIS_Interface', tls: false }), /查询参数/)
  assert.match(endpointReadinessError({ ...valid, credentialRef: 'secret-value' }), /引用 URI/)
})

test('外部系统服务地址限制连接与读取超时', () => {
  assert.match(endpointReadinessError({ ...valid, connectTimeout: 99 }), /连接超时/)
  assert.match(endpointReadinessError({ ...valid, readTimeout: 2000 }), /读取超时/)
  assert.match(endpointReadinessError({ ...valid, readTimeout: 300001 }), /读取超时/)
})
