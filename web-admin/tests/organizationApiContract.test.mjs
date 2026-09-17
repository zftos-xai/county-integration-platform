import assert from 'node:assert/strict'
import test from 'node:test'
import {
  organizationDetailPath, organizationEnabledPath, organizationListPath, organizationWriteRequest,
} from '../src/api/system/organizationContract.ts'

test('机构查询路径按契约生成可选启用状态', () => {
  assert.equal(organizationListPath(), '/organizations')
  assert.equal(organizationListPath(true), '/organizations?enabled=true')
  assert.equal(organizationDetailPath(7), '/organizations/7')
})

test('机构写请求保留并发版本和目标状态', () => {
  const update = organizationWriteRequest('PUT', {
    organizationName: '第一卫生院', organizationType: 'CLINIC', parentId: 1,
    validFrom: null, validTo: null, version: 'AAAAAAAAAAE=',
  })
  const enabled = organizationWriteRequest('PATCH', { enabled: false, version: 'AAAAAAAAAAE=' })

  assert.equal(update.method, 'PUT')
  assert.equal(JSON.parse(update.body).version, 'AAAAAAAAAAE=')
  assert.equal(organizationEnabledPath(7), '/organizations/7/enabled')
  assert.deepEqual(JSON.parse(enabled.body), { enabled: false, version: 'AAAAAAAAAAE=' })
})
