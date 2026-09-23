import { apiRequest } from '@/utils/request'
import { isRecord } from '@/utils/validation'
import { exchangeRecordsPath } from './exchangeRecordApiPaths'
import type { ExchangeRecordFilters } from './exchangeRecordApiPaths'

/** HIS调用完成后可供管理端读取的脱敏运行事实。 */
export type ExchangeRecord = {
  id: number
  requestId: string
  interfaceCode: string
  organizationCode: string
  sourceRecordId: string
  callerSystemCode: string
  targetSystemCode: string
  result: 'SUCCESS' | 'FAILURE' | 'NO_RESPONSE' | 'INVALID_RESPONSE' | null
  targetResultCode: string | null
  resultMessage: string | null
  durationMs: number | null
  requestSummary: string | null
  communicationErrorSummary: string | null
  receivedAt: string
  processedAt: string | null
}

/** 校验单条交换记录响应，禁止未经收窄的服务端数据进入管理页。 */
export function isExchangeRecord(value: unknown): value is ExchangeRecord {
  if (!isRecord(value)) return false
  const result = value.result
  return typeof value.id === 'number'
    && typeof value.requestId === 'string'
    && typeof value.interfaceCode === 'string'
    && typeof value.organizationCode === 'string'
    && typeof value.sourceRecordId === 'string'
    && typeof value.callerSystemCode === 'string'
    && typeof value.targetSystemCode === 'string'
    && (result === 'SUCCESS' || result === 'FAILURE' || result === 'NO_RESPONSE'
      || result === 'INVALID_RESPONSE' || result === null)
    && (typeof value.targetResultCode === 'string' || value.targetResultCode === null)
    && (typeof value.resultMessage === 'string' || value.resultMessage === null)
    && (typeof value.durationMs === 'number' || value.durationMs === null)
    && (typeof value.requestSummary === 'string' || value.requestSummary === null)
    && (typeof value.communicationErrorSummary === 'string' || value.communicationErrorSummary === null)
    && typeof value.receivedAt === 'string'
    && (typeof value.processedAt === 'string' || value.processedAt === null)
}

const isExchangeRecordList = (value: unknown): value is ExchangeRecord[] => (
  Array.isArray(value) && value.every(isExchangeRecord)
)

/** 查询当前账号有权访问的机构内HIS调用记录；该操作不会重发HIS请求。 */
export function listExchangeRecords(filters: ExchangeRecordFilters, signal?: AbortSignal) {
  return apiRequest<ExchangeRecord[]>(exchangeRecordsPath(filters), { signal }, isExchangeRecordList)
}
