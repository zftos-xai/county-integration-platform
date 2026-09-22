/** 医疗目录列表查询参数。 */
export type MedicalDirectoryQueryParameters = {
  organizationCode?: string
  directoryType?:
    | 'TRADITIONAL_MEDICINE'
    | 'WESTERN_MEDICINE'
    | 'TREATMENT'
    | 'CONSUMABLE'
  keyword?: string
  sourceEnabledFlag?: string
  page: number
  pageSize: number
}

/** @param query 查询参数 @return 医疗目录分页地址 */
export function medicalDirectoryListPath(
  query: MedicalDirectoryQueryParameters,
) {
  const parameters = new URLSearchParams()
  if (query.organizationCode) {
    parameters.set('organizationCode', query.organizationCode)
  }
  if (query.directoryType) {
    parameters.set('directoryType', query.directoryType)
  }
  if (query.keyword) parameters.set('keyword', query.keyword)
  if (query.sourceEnabledFlag) parameters.set('sourceEnabledFlag', query.sourceEnabledFlag)
  parameters.set('page', String(query.page))
  parameters.set('pageSize', String(query.pageSize))
  return `/master-data/medical-directory?${parameters.toString()}`
}
