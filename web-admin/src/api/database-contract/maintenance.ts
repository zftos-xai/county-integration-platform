import { apiRequest, asUncertainWriteError, ApiClientError } from '@/utils/request'
import { isRecord } from '@/utils/validation'

/** 数据库契约差异建议修改方向。 */
export type DatabaseContractDirection = 'DATABASE' | 'MAPPER_OR_MODEL' | 'MANUAL_REVIEW'

/** 实时扫描发现的一项数据库契约差异。 */
export type DatabaseContractIssue = {
  issueKey: string
  code: string
  direction: DatabaseContractDirection
  objectName: string
  expected: string
  actual: string
  action: string
  executable: boolean
  ddlPreview: string | null
  executionNote: string
}

/** 正常维护页面使用的实时扫描结果。 */
export type DatabaseContractInspection = {
  scannedAt: string
  expectedColumnCount: number
  issueCount: number
  executableCount: number
  issues: DatabaseContractIssue[]
}

/** 数据库契约维护方案状态。 */
export type DatabaseContractPlanStatus = 'DRAFT' | 'APPROVED' | 'EXECUTING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

/** 数据库契约维护方案中的差异快照。 */
export type DatabaseContractPlanItem = {
  id: number
  violationCode: string
  objectName: string
  expectedValue: string
  actualValue: string
  direction: DatabaseContractDirection
  actionText: string
  executable: boolean
  ddlPreview: string | null
  executionNote: string
  executionStatus: string
  verificationStatus: string
}

/** 持久化的数据库契约维护方案和完整处理事实。 */
export type DatabaseContractPlan = {
  id: number
  planNo: string
  status: DatabaseContractPlanStatus
  issueCount: number
  executableCount: number
  summary: string
  createdBy: string
  createdAt: string
  approvedBy: string | null
  approvedAt: string | null
  approvalNote: string | null
  executedBy: string | null
  executedAt: string | null
  verifiedAt: string | null
  failureMessage: string | null
  updatedAt: string
  version: string
  items: DatabaseContractPlanItem[]
}

/** 创建方案时由页面提交的稳定差异键和摘要。 */
export type CreateDatabaseContractPlanInput = { issueKeys: string[]; summary: string }

function isNullableString(value: unknown): value is string | null {
  return typeof value === 'string' || value === null
}

function isIssue(value: unknown): value is DatabaseContractIssue {
  return isRecord(value) && typeof value.issueKey === 'string' && typeof value.code === 'string'
    && ['DATABASE', 'MAPPER_OR_MODEL', 'MANUAL_REVIEW'].includes(String(value.direction))
    && typeof value.objectName === 'string' && typeof value.expected === 'string'
    && typeof value.actual === 'string' && typeof value.action === 'string'
    && typeof value.executable === 'boolean' && isNullableString(value.ddlPreview)
    && typeof value.executionNote === 'string'
}

function isInspection(value: unknown): value is DatabaseContractInspection {
  return isRecord(value) && typeof value.scannedAt === 'string'
    && typeof value.expectedColumnCount === 'number' && typeof value.issueCount === 'number'
    && typeof value.executableCount === 'number' && Array.isArray(value.issues)
    && value.issues.every(isIssue)
}

function isPlanItem(value: unknown): value is DatabaseContractPlanItem {
  return isRecord(value) && typeof value.id === 'number' && typeof value.violationCode === 'string'
    && typeof value.objectName === 'string' && typeof value.expectedValue === 'string'
    && typeof value.actualValue === 'string' && typeof value.direction === 'string'
    && typeof value.actionText === 'string' && typeof value.executable === 'boolean'
    && isNullableString(value.ddlPreview) && typeof value.executionNote === 'string'
    && typeof value.executionStatus === 'string' && typeof value.verificationStatus === 'string'
}

function isPlan(value: unknown): value is DatabaseContractPlan {
  return isRecord(value) && typeof value.id === 'number' && typeof value.planNo === 'string'
    && ['DRAFT', 'APPROVED', 'EXECUTING', 'COMPLETED', 'FAILED', 'CANCELLED'].includes(String(value.status))
    && typeof value.issueCount === 'number' && typeof value.executableCount === 'number'
    && typeof value.summary === 'string' && typeof value.createdBy === 'string'
    && typeof value.createdAt === 'string' && isNullableString(value.approvedBy)
    && isNullableString(value.approvedAt) && isNullableString(value.approvalNote)
    && isNullableString(value.executedBy) && isNullableString(value.executedAt)
    && isNullableString(value.verifiedAt) && isNullableString(value.failureMessage)
    && typeof value.updatedAt === 'string' && typeof value.version === 'string'
    && Array.isArray(value.items) && value.items.every(isPlanItem)
}

function isPlanList(value: unknown): value is DatabaseContractPlan[] {
  return Array.isArray(value) && value.every(isPlan)
}

/** 实时扫描数据库、Flyway、Mapper与模型契约，不执行DDL。 */
export function inspectDatabaseContract(signal?: AbortSignal) {
  return apiRequest<DatabaseContractInspection>('/database-contract/inspection', { signal }, isInspection)
}

/** 查询最近100条数据库契约维护方案。 */
export function listDatabaseContractPlans(signal?: AbortSignal) {
  return apiRequest<DatabaseContractPlan[]>('/database-contract/plans', { signal }, isPlanList)
}

/** 创建只包含服务端当前实时差异的维护方案。 */
export function createDatabaseContractPlan(input: CreateDatabaseContractPlanInput) {
  return write('/database-contract/plans', input)
}

/** 由非创建人批准全部可执行的维护方案。 */
export function approveDatabaseContractPlan(id: number, version: string, note: string) {
  return write(`/database-contract/plans/${id}/approve`, { version, note })
}

/** 在单一数据库事务中执行DDL并立即重新扫描复验。 */
export function executeDatabaseContractPlan(id: number, version: string) {
  return write(`/database-contract/plans/${id}/execute`, { version })
}

/** 在DDL开始前取消草稿或已批准方案，历史记录继续保留。 */
export function cancelDatabaseContractPlan(id: number, version: string) {
  return write(`/database-contract/plans/${id}/cancel`, { version })
}

async function write(path: string, body: object) {
  try {
    return await apiRequest<DatabaseContractPlan>(
      path, { method: 'POST', body: JSON.stringify(body) }, isPlan,
    )
  } catch (error) {
    if (error instanceof ApiClientError) throw asUncertainWriteError(error)
    throw error
  }
}
