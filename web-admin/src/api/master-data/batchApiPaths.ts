/** 同步批次列表和创建操作共用的正式 API 地址。 */
export const masterDataBatchCollectionPath = '/master-data/batches'

/** 发起同步所需真实来源和可执行业务的地址。 */
export const masterDataSyncOptionsPath = `${masterDataBatchCollectionPath}/options`

/**
 * 生成同步批次详情地址。
 *
 * @param batchId 平台批次主键
 */
export function masterDataBatchDetailPath(batchId: number) {
  return `${masterDataBatchCollectionPath}/${batchId}`
}

/** @param batchId 平台批次主键 @return 100-003按目录类型保存的结果地址 */
export function masterDataBatchDirectoryResultsPath(batchId: number) {
  return `${masterDataBatchDetailPath(batchId)}/directory-results`
}

/** @param batchId 平台批次主键 @return 100-004/100-005按目录类型保存的结果地址 */
export function masterDataBatchMedicalDirectoryResultsPath(batchId: number) {
  return `${masterDataBatchDetailPath(batchId)}/medical-directory-results`
}

/** @param batchId 平台批次主键 @return 100-006/100-007按诊断类别保存的结果地址 */
export function masterDataBatchIcd10ResultsPath(batchId: number) {
  return `${masterDataBatchDetailPath(batchId)}/icd10-results`
}

/** @param batchId 平台批次主键 @param page 从一开始页码 @param pageSize 每页调用事实数 @return ICD-10脱敏HIS调用事实地址 */
export function masterDataBatchIcd10HisInvocationsPath(batchId: number, page: number, pageSize: number) {
  const parameters = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  return `${masterDataBatchDetailPath(batchId)}/his-invocations?${parameters.toString()}`
}

/** @param batchId 平台批次主键 @return 机构目录批次关联的通用脱敏HIS调用事实地址 */
export function masterDataBatchExchangeRecordsPath(batchId: number) {
  return `${masterDataBatchDetailPath(batchId)}/exchange-records`
}

/** @param batchId 平台批次主键 @return 尚未执行批次的取消地址 */
export function masterDataBatchCancelPath(batchId: number) {
  return `${masterDataBatchDetailPath(batchId)}/cancel`
}

/** @param batchId 平台批次主键 @return 100-003整体取得、校验和直接对账地址 */
export function masterDataBatchRunPath(batchId: number) {
  return `${masterDataBatchDetailPath(batchId)}/run`
}

/** @param batchId 平台批次主键 @return 中断批次的受控收尾地址 */
export function masterDataBatchRecoverPath(batchId: number) {
  return `${masterDataBatchDetailPath(batchId)}/recover`
}

/** 可用于查询同步批次列表的筛选条件。 */
export type MasterDataBatchQueryParameters = {
  organizationCode?: string
  requestKey?: string
  category?: string
  status?: string
  startedFrom?: string
  startedTo?: string
  page: number
  pageSize: number
}

/**
 * 按稳定参数名生成同步批次查询地址。
 *
 * @param query 页面已校验的查询条件
 */
export function masterDataBatchListPath(query: MasterDataBatchQueryParameters) {
  const parameters = new URLSearchParams()
  if (query.organizationCode)
    parameters.set('organizationCode', query.organizationCode)
  if (query.requestKey) parameters.set('requestKey', query.requestKey)
  if (query.category) parameters.set('category', query.category)
  if (query.status) parameters.set('status', query.status)
  if (query.startedFrom) parameters.set('startedFrom', query.startedFrom)
  if (query.startedTo) parameters.set('startedTo', query.startedTo)
  parameters.set('page', String(query.page))
  parameters.set('pageSize', String(query.pageSize))
  return `${masterDataBatchCollectionPath}?${parameters.toString()}`
}
