import { apiRequest } from '@/utils/request'
import { isRecord } from '@/utils/validation'
import { managementAuditEventsPath } from './auditApiPaths'
import type { ManagementAuditFilters } from './auditApiPaths'

/** 当前用户在机构范围内可见的脱敏管理审计事件。 */
export type ManagementAuditEvent = {
  id: number
  occurredAt: string
  actorLogin: string
  /** 本次操作涉及的数据所属机构；为空表示平台范围，不表示操作人的所属机构。 */
  organizationCode: string | null
  actionCode: string
  targetType: string
  targetId: string
  resultCode: 'SUCCESS' | 'FAILURE'
  changeSummary: string
  requestId: string | null
}

/** 校验单条管理审计响应，防止未知响应直接进入正式页面。 */
export function isManagementAuditEvent(value: unknown): value is ManagementAuditEvent {
  if (!isRecord(value)) return false
  return typeof value.id === 'number' && typeof value.occurredAt === 'string'
    && typeof value.actorLogin === 'string'
    && (value.organizationCode === null || typeof value.organizationCode === 'string')
    && typeof value.actionCode === 'string' && typeof value.targetType === 'string'
    && typeof value.targetId === 'string'
    && (value.resultCode === 'SUCCESS' || value.resultCode === 'FAILURE')
    && typeof value.changeSummary === 'string'
    && (value.requestId === null || typeof value.requestId === 'string')
}

const isManagementAuditEventList = (value: unknown): value is ManagementAuditEvent[] => (
  Array.isArray(value) && value.every(isManagementAuditEvent)
)

/** 查询当前用户可见的管理审计事件。 */
export function listManagementAuditEvents(filters: ManagementAuditFilters, signal?: AbortSignal) {
  return apiRequest<ManagementAuditEvent[]>(managementAuditEventsPath(filters), { signal }, isManagementAuditEventList)
}
