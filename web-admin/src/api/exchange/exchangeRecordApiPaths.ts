/** HIS调用记录管理页支持的服务端筛选条件。 */
export type ExchangeRecordFilters = {
  organizationCode: string
  receivedFrom?: string
  receivedTo?: string
  interfaceCode?: string
  sourceRecordId?: string
  result?: 'SUCCESS' | 'FAILURE' | 'NO_RESPONSE' | 'INVALID_RESPONSE' | ''
}

/** 构造机构范围内的HIS调用记录查询地址，并编码可选筛选条件。 */
export function exchangeRecordsPath(filters: ExchangeRecordFilters) {
  const query = new URLSearchParams()
  query.set('organizationCode', filters.organizationCode.trim())
  const interfaceCode = filters.interfaceCode?.trim()
  const sourceRecordId = filters.sourceRecordId?.trim()
  if (filters.receivedFrom) query.set('receivedFrom', filters.receivedFrom)
  if (filters.receivedTo) query.set('receivedTo', filters.receivedTo)
  if (interfaceCode) query.set('interfaceCode', interfaceCode)
  if (sourceRecordId) query.set('sourceRecordId', sourceRecordId)
  if (filters.result) query.set('result', filters.result)
  query.set('limit', '100')
  return `/exchange-records?${query.toString()}`
}
