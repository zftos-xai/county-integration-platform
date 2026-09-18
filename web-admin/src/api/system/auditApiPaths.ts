/** 管理审计列表支持的服务端筛选条件。 */
export type ManagementAuditFilters = {
  actorLogin?: string
  actionCode?: string
  targetType?: string
  targetId?: string
  requestId?: string
  resultCode?: 'SUCCESS' | 'FAILURE' | ''
  occurredFrom?: string
  occurredTo?: string
  beforeOccurredAt?: string
  beforeId?: number
  limit: number
}

/**
 * 构造管理审计查询路径，统一裁剪可选筛选值并交由 URLSearchParams 编码。
 */
export function managementAuditEventsPath(filters: ManagementAuditFilters) {
  const query = new URLSearchParams()
  const actorLogin = filters.actorLogin?.trim()
  const actionCode = filters.actionCode?.trim()
  const targetType = filters.targetType?.trim()
  const targetId = filters.targetId?.trim()
  const requestId = filters.requestId?.trim()
  if (actorLogin) query.set('actorLogin', actorLogin)
  if (actionCode) query.set('actionCode', actionCode)
  if (targetType) query.set('targetType', targetType)
  if (targetId) query.set('targetId', targetId)
  if (requestId) query.set('requestId', requestId)
  if (filters.resultCode) query.set('resultCode', filters.resultCode)
  if (filters.occurredFrom) query.set('occurredFrom', filters.occurredFrom)
  if (filters.occurredTo) query.set('occurredTo', filters.occurredTo)
  if (filters.beforeOccurredAt) query.set('beforeOccurredAt', filters.beforeOccurredAt)
  if (filters.beforeId) query.set('beforeId', String(filters.beforeId))
  query.set('limit', String(filters.limit))
  return `/audit/events?${query.toString()}`
}
