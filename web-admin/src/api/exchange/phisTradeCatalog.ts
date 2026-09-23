import { apiRequest } from '@/utils/request'
import { isRecord } from '@/utils/validation'

/** 后端PhisTrade权威目录中的单项交易定义。 */
export type PhisTrade = {
  code: string
  displayName: string
  category: string
  description: string
  documentedInPublicSpecification: boolean
}

/** 严格校验交易目录项，避免错误元数据被当作已确认接口展示。 */
export function isPhisTrade(value: unknown): value is PhisTrade {
  return isRecord(value)
    && typeof value.code === 'string'
    && typeof value.displayName === 'string'
    && typeof value.category === 'string'
    && typeof value.description === 'string'
    && typeof value.documentedInPublicSpecification === 'boolean'
}

const isPhisTradeList = (value: unknown): value is PhisTrade[] => (
  Array.isArray(value) && value.every(isPhisTrade)
)

/** 从后端权威PhisTrade枚举读取交易目录；该目录不含机构数据。 */
export function listPhisTrades(signal?: AbortSignal) {
  return apiRequest<PhisTrade[]>('/his/trades', { signal }, isPhisTradeList)
}
