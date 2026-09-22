import {
  apiRequest,
  asUncertainWriteError,
  ApiClientError,
} from '@/utils/request'
import { isRecord } from '@/utils/validation'
import {
  masterDataBatchCollectionPath,
  masterDataBatchCancelPath,
  masterDataBatchDetailPath,
  masterDataBatchDirectoryResultsPath,
  masterDataBatchMedicalDirectoryResultsPath,
  masterDataBatchRunPath,
  masterDataBatchRecoverPath,
  masterDataBatchListPath,
  masterDataSyncOptionsPath,
  type MasterDataBatchQueryParameters,
} from './batchApiPaths'
import { hospitalDirectoryTypes, type HospitalDirectoryType } from './directory'

/** 当前已经确认进入首批基础数据业务的类别。 */
export const masterDataCategories = [
  'HOSPITAL_DIRECTORY',
  'MEDICAL_DIRECTORY',
] as const

/** 首批基础数据类别代码。 */
export type MasterDataCategory = (typeof masterDataCategories)[number]

/** 基础数据属于单一机构或平台公共范围。 */
export type MasterDataScopeType = 'ORGANIZATION' | 'PLATFORM'

/** 外部接口配置的运行环境。 */
export type MasterDataEnvironment = 'DEVELOPMENT' | 'TEST' | 'PRODUCTION'

/** 医疗目录明确选择全量或时间范围；医院综合目录不使用时间筛选。 */
export type MasterDataSyncMode = 'NOT_APPLICABLE' | 'TIME_RANGE' | 'FULL'

/** 后端批次状态；页面必须转换为业务文案后再展示。 */
export const masterDataBatchStatuses = [
  'CREATED',
  'FETCHING',
  'COMPLETED',
  'COMPLETED_WITH_ERRORS',
  'COMPLETED_WITH_UNKNOWN',
  'FAILED',
  'RESULT_UNKNOWN',
] as const

/** 基础数据同步批次状态代码。 */
export type MasterDataBatchStatus = (typeof masterDataBatchStatuses)[number]

/** 一次同步运行从来源取得到当前目录对账的数量事实。 */
export type MasterDataBatchCounts = {
  declared: number | null
  returned: number
  duplicate: number
  invalid: number
  conflict: number
  created: number
  updated: number
  unchanged: number
  sourceMissing: number
  active: number | null
}

/** 100-003中单一目录类型的已落库运行结果。 */
export type HospitalDirectorySyncResultStatus =
  | 'COMPLETED'
  | 'FAILED'
  | 'RESULT_UNKNOWN'

/** 100-003按科室、医生、病区或床位保存的处理事实。 */
export type HospitalDirectorySyncResult = {
  directoryType: HospitalDirectoryType
  status: HospitalDirectorySyncResultStatus
  returnedCount: number
  duplicateCount: number
  invalidCount: number
  conflictCount: number
  createdCount: number
  updatedCount: number
  unchangedCount: number
  sourceMissingCount: number
  activeCount: number | null
  failureSummary: string | null
}

/** 同步批次列表和详情共用的只读摘要。 */
export type MasterDataBatchSummary = {
  id: number
  batchNo: string
  scopeType: MasterDataScopeType
  organizationCode: string | null
  organizationName: string | null
  environment: MasterDataEnvironment
  category: MasterDataCategory
  mode?: MasterDataSyncMode
  dataTradeCode: string
  countTradeCode: string | null
  sourceType: string | null
  sourceOrganizationId: string | null
  fullRuleEvidence?: string | null
  rangeStart: string | null
  rangeEnd: string | null
  status: MasterDataBatchStatus
  counts: MasterDataBatchCounts
  startedAt: string | null
  finishedAt: string | null
  completedAt: string | null
  failureCode: string | null
  failureSummary: string | null
  version: string
}

/** 服务端分页后的同步批次列表。 */
export type MasterDataBatchPage = {
  items: MasterDataBatchSummary[]
  total: number
  page: number
  pageSize: number
}

/** 发起同步时明确且可复核的来源查询范围。 */
export type StartMasterDataBatchInput = {
  requestKey: string
  organizationCode: string | null
  environment: MasterDataEnvironment
  category: MasterDataCategory
  mode: MasterDataSyncMode
  rangeStart: string | null
  rangeEnd: string | null
}

/** 当前账号可实际使用的HIS机构和环境组合。 */
export type MasterDataSyncSource = {
  organizationCode: string
  organizationName: string
  environments: MasterDataEnvironment[]
  fullSyncEnvironments?: MasterDataEnvironment[]
}

/** 平台当前已经具备完整执行闭环的基础数据业务。 */
export type MasterDataSyncBusiness = {
  category: MasterDataCategory
  name: string
  tradeCode: string
  countTradeCode: string | null
  requiresTimeRange: boolean
}

/** 发起同步时由后端给出的真实来源和已实现业务。 */
export type MasterDataSyncOptions = {
  sources: MasterDataSyncSource[]
  businesses: MasterDataSyncBusiness[]
}

/** 校验后端返回的可用HIS同步来源。 */
export function isMasterDataSyncSource(
  value: unknown,
): value is MasterDataSyncSource {
  if (!isRecord(value) || !Array.isArray(value.environments)) return false
  const environments: unknown[] = value.environments
  return (
    typeof value.organizationCode === 'string' &&
    typeof value.organizationName === 'string' &&
    value.environments.length > 0 &&
    value.environments.every(
      (item) =>
        item === 'DEVELOPMENT' || item === 'TEST' || item === 'PRODUCTION',
    ) &&
    (value.fullSyncEnvironments === undefined ||
      (Array.isArray(value.fullSyncEnvironments) &&
        value.fullSyncEnvironments.every((item) => environments.includes(item))))
  )
}

/** 校验后端返回的一项可执行同步业务。 */
export function isMasterDataSyncBusiness(
  value: unknown,
): value is MasterDataSyncBusiness {
  return (
    isRecord(value) &&
    isMasterDataCategory(value.category) &&
    typeof value.name === 'string' &&
    typeof value.tradeCode === 'string' &&
    (typeof value.countTradeCode === 'string' || value.countTradeCode === null) &&
    typeof value.requiresTimeRange === 'boolean'
  )
}

/** 校验发起同步选项，禁止页面使用不完整的来源或业务。 */
export function isMasterDataSyncOptions(
  value: unknown,
): value is MasterDataSyncOptions {
  return (
    isRecord(value) &&
    Array.isArray(value.sources) &&
    value.sources.every(isMasterDataSyncSource) &&
    Array.isArray(value.businesses) &&
    value.businesses.every(isMasterDataSyncBusiness)
  )
}

/** 判断未知值是否为已登记的基础数据类别。 */
export function isMasterDataCategory(
  value: unknown,
): value is MasterDataCategory {
  return (
    typeof value === 'string' &&
    masterDataCategories.some((category) => category === value)
  )
}

/** 判断未知值是否为后端支持的批次状态。 */
export function isMasterDataBatchStatus(
  value: unknown,
): value is MasterDataBatchStatus {
  return (
    typeof value === 'string' &&
    masterDataBatchStatuses.some((status) => status === value)
  )
}

/** 校验批次数量对象，避免页面把缺失字段显示成零。 */
export function isMasterDataBatchCounts(
  value: unknown,
): value is MasterDataBatchCounts {
  if (!isRecord(value)) return false
  const nullableNumbers = [value.declared, value.active]
  const numbers = [
    value.returned,
    value.duplicate,
    value.invalid,
    value.conflict,
    value.created,
    value.updated,
    value.unchanged,
    value.sourceMissing,
  ]
  return (
    nullableNumbers.every(
      (item) => typeof item === 'number' || item === null,
    ) && numbers.every((item) => typeof item === 'number')
  )
}

/** 校验单目录类型运行事实，禁止页面将缺失字段伪装成零。 */
export function isHospitalDirectorySyncResult(
  value: unknown,
): value is HospitalDirectorySyncResult {
  if (!isRecord(value)) return false
  const numericFields = [
    value.returnedCount,
    value.duplicateCount,
    value.invalidCount,
    value.conflictCount,
    value.createdCount,
    value.updatedCount,
    value.unchangedCount,
    value.sourceMissingCount,
  ]
  return (
    typeof value.directoryType === 'string' &&
    hospitalDirectoryTypes.some((type) => type === value.directoryType) &&
    (value.status === 'COMPLETED' ||
      value.status === 'FAILED' ||
      value.status === 'RESULT_UNKNOWN') &&
    numericFields.every((item) => typeof item === 'number') &&
    (typeof value.activeCount === 'number' || value.activeCount === null) &&
    (typeof value.failureSummary === 'string' || value.failureSummary === null)
  )
}

/** 校验后端返回的一条同步批次摘要。 */
export function isMasterDataBatchSummary(
  value: unknown,
): value is MasterDataBatchSummary {
  if (!isRecord(value)) return false
  return (
    typeof value.id === 'number' &&
    typeof value.batchNo === 'string' &&
    (value.scopeType === 'ORGANIZATION' || value.scopeType === 'PLATFORM') &&
    (typeof value.organizationCode === 'string' ||
      value.organizationCode === null) &&
    (typeof value.organizationName === 'string' ||
      value.organizationName === null) &&
    (value.environment === 'DEVELOPMENT' ||
      value.environment === 'TEST' ||
      value.environment === 'PRODUCTION') &&
    isMasterDataCategory(value.category) &&
    typeof value.dataTradeCode === 'string' &&
    (typeof value.countTradeCode === 'string' ||
      value.countTradeCode === null) &&
    (typeof value.sourceOrganizationId === 'string' || value.sourceOrganizationId === null) &&
    (value.fullRuleEvidence === undefined || typeof value.fullRuleEvidence === 'string' || value.fullRuleEvidence === null) &&
    (typeof value.rangeStart === 'string' || value.rangeStart === null) &&
    (typeof value.rangeEnd === 'string' || value.rangeEnd === null) &&
    isMasterDataBatchStatus(value.status) &&
    isMasterDataBatchCounts(value.counts) &&
    (typeof value.startedAt === 'string' || value.startedAt === null) &&
    (typeof value.finishedAt === 'string' || value.finishedAt === null) &&
    (typeof value.completedAt === 'string' || value.completedAt === null) &&
    (typeof value.failureCode === 'string' || value.failureCode === null) &&
    (typeof value.failureSummary === 'string' ||
      value.failureSummary === null) &&
    typeof value.version === 'string'
  )
}

/** 校验后端返回的同步批次分页结果。 */
export function isMasterDataBatchPage(
  value: unknown,
): value is MasterDataBatchPage {
  if (!isRecord(value) || !Array.isArray(value.items)) return false
  return (
    value.items.every(isMasterDataBatchSummary) &&
    typeof value.total === 'number' &&
    typeof value.page === 'number' &&
    typeof value.pageSize === 'number'
  )
}

/** 按机构、类别、状态和开始时间查询正式同步批次。 */
export function listMasterDataBatches(
  query: MasterDataBatchQueryParameters,
  signal?: AbortSignal,
) {
  return apiRequest<MasterDataBatchPage>(
    masterDataBatchListPath(query),
    { signal },
    isMasterDataBatchPage,
  )
}

/** 读取已启用且地址、认证信息均可解析的基层HIS同步来源。 */
export function getMasterDataSyncOptions(signal?: AbortSignal) {
  return apiRequest<MasterDataSyncOptions>(
    masterDataSyncOptionsPath,
    { signal },
    isMasterDataSyncOptions,
  )
}

/** 读取一条同步批次的最新事实，不触发重新执行。 */
export function getMasterDataBatch(batchId: number, signal?: AbortSignal) {
  return apiRequest<MasterDataBatchSummary>(
    masterDataBatchDetailPath(batchId),
    { signal },
    isMasterDataBatchSummary,
  )
}

/** 读取一个100-003批次的分项结果；查看不会重新调用HIS。 */
export function getHospitalDirectorySyncResults(
  batchId: number,
  signal?: AbortSignal,
) {
  return apiRequest<HospitalDirectorySyncResult[]>(
    masterDataBatchDirectoryResultsPath(batchId),
    { signal },
    (value): value is HospitalDirectorySyncResult[] =>
      Array.isArray(value) && value.every(isHospitalDirectorySyncResult),
  )
}

/** 读取一个100-004/100-005批次的分项事实；查看不会再次调用HIS。 */
export function getMedicalDirectorySyncResults(
  batchId: number,
  signal?: AbortSignal,
) {
  return apiRequest<MedicalDirectorySyncResult[]>(
    masterDataBatchMedicalDirectoryResultsPath(batchId),
    { signal },
    (value): value is MedicalDirectorySyncResult[] =>
      Array.isArray(value) && value.every(isMedicalDirectorySyncResult),
  )
}

/**
 * 发起一次有明确业务范围的同步批次。
 * 网络失败或超时时要求调用方先按请求标识回读，不能直接再次提交。
 */
export async function startMasterDataBatch(input: StartMasterDataBatchInput) {
  try {
    return await apiRequest<MasterDataBatchSummary>(
      masterDataBatchCollectionPath,
      { method: 'POST', body: JSON.stringify(input) },
      isMasterDataBatchSummary,
    )
  } catch (error) {
    if (error instanceof ApiClientError) throw asUncertainWriteError(error)
    throw error
  }
}

/**
 * 取消一条尚未执行的批次；仅终止后续执行并保留完整历史，不删除记录。
 */
export async function cancelMasterDataBatch(batchId: number, version: string) {
  try {
    return await apiRequest<MasterDataBatchSummary>(
      masterDataBatchCancelPath(batchId),
      { method: 'POST', body: JSON.stringify({ version }) },
      isMasterDataBatchSummary,
    )
  } catch (error) {
    if (error instanceof ApiClientError) throw asUncertainWriteError(error)
    throw error
  }
}

/** 使用最近读取版本执行所选目录业务；长批次超时只回读结果，不自动再次运行。 */
export async function runMasterDataBatch(batchId: number, version: string) {
  try {
    return await apiRequest<MasterDataBatchSummary>(
      masterDataBatchRunPath(batchId),
      { method: 'POST', body: JSON.stringify({ version }) },
      isMasterDataBatchSummary,
    )
  } catch (error) {
    if (error instanceof ApiClientError) throw asUncertainWriteError(error)
    throw error
  }
}

/** 汇总中断批次的已提交结果并禁止旧执行者继续写入；不重新调用 HIS。 */
export async function recoverMasterDataBatch(batchId: number, version: string) {
  try {
    return await apiRequest<MasterDataBatchSummary>(
      masterDataBatchRecoverPath(batchId),
      { method: 'POST', body: JSON.stringify({ version }) },
      isMasterDataBatchSummary,
    )
  } catch (error) {
    if (error instanceof ApiClientError) throw asUncertainWriteError(error)
    throw error
  }
}

/** 100-004/100-005按医疗目录类型保存的处理事实。 */
export type MedicalDirectorySyncResult = {
  directoryType: 'TRADITIONAL_MEDICINE' | 'WESTERN_MEDICINE' | 'TREATMENT' | 'CONSUMABLE'
  status: HospitalDirectorySyncResultStatus
  declaredCount: number | null
  returnedCount: number
  duplicateCount: number
  invalidCount: number
  conflictCount: number
  createdCount: number
  updatedCount: number
  unchangedCount: number
  sourceMissingCount: number
  activeCount: number | null
  failureSummary: string | null
}

/** 校验医疗目录分项结果，禁止把缺失字段显示为零。 */
export function isMedicalDirectorySyncResult(
  value: unknown,
): value is MedicalDirectorySyncResult {
  if (!isRecord(value)) return false
  const types = ['TRADITIONAL_MEDICINE', 'WESTERN_MEDICINE', 'TREATMENT', 'CONSUMABLE']
  const numericFields = [
    value.returnedCount, value.duplicateCount, value.invalidCount, value.conflictCount,
    value.createdCount, value.updatedCount, value.unchangedCount, value.sourceMissingCount,
  ]
  return (
    typeof value.directoryType === 'string' && types.some((type) => type === value.directoryType) &&
    (value.status === 'COMPLETED' || value.status === 'FAILED' || value.status === 'RESULT_UNKNOWN') &&
    (typeof value.declaredCount === 'number' || value.declaredCount === null) &&
    numericFields.every((item) => typeof item === 'number') &&
    (typeof value.activeCount === 'number' || value.activeCount === null) &&
    (typeof value.failureSummary === 'string' || value.failureSummary === null)
  )
}
