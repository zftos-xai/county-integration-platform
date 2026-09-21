import assert from 'node:assert/strict'
import test from 'node:test'
import { managementAuditEventsPath } from '../src/api/system/auditApiPaths.ts'

test('管理审计查询去除空白条件并安全编码目标筛选', () => {
  assert.equal(managementAuditEventsPath({ limit: 100 }), '/audit/events?limit=100')
  assert.equal(
    managementAuditEventsPath({ targetType: ' USER ', targetId: '账号/001', limit: 50 }),
    '/audit/events?targetType=USER&targetId=%E8%B4%A6%E5%8F%B7%2F001&limit=50',
  )
})

test('管理审计查询支持操作人、操作、结果和时间范围', () => {
  assert.equal(managementAuditEventsPath({
    actorLogin: 'admin', actionCode: 'USER_UPDATED', requestId: ' REQ-1 ', resultCode: 'FAILURE',
    occurredFrom: '2026-09-01T00:00:00', occurredTo: '2026-09-17T23:59:59', limit: 200,
  }), '/audit/events?actorLogin=admin&actionCode=USER_UPDATED&requestId=REQ-1&resultCode=FAILURE&occurredFrom=2026-09-01T00%3A00%3A00&occurredTo=2026-09-17T23%3A59%3A59&limit=200')
})

test('管理审计历史记录使用时间和编号组成稳定翻页位置', () => {
  assert.equal(managementAuditEventsPath({
    beforeOccurredAt: '2026-09-17T12:30:00', beforeId: 88, limit: 100,
  }), '/audit/events?beforeOccurredAt=2026-09-17T12%3A30%3A00&beforeId=88&limit=100')
})

test('正式审计页面使用只读接口且不提供写操作', async () => {
  const { readFile } = await import('node:fs/promises')
  const page = await readFile('web-admin/src/views/audit/management/AuditView.vue', 'utf8')
  const router = await readFile('web-admin/src/router/index.ts', 'utf8')

  assert.match(page, /listManagementAuditEvents/)
  assert.match(page, /当前账号机构范围内的脱敏记录/)
  assert.doesNotMatch(page, /createManagementAudit|updateManagementAudit|deleteManagementAudit/)
  assert.match(router, /path:\s*'audit',[\s\S]{0,80}?component:\s*AuditView/)
})
