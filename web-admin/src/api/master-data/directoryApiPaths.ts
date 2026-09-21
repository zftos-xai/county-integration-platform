/** 医院综合目录列表查询参数。 */
export type HospitalDirectoryQueryParameters = {
  organizationCode?: string
  directoryType?: 'DEPARTMENT' | 'DOCTOR' | 'WARD' | 'BED'
  keyword?: string
  page: number
  pageSize: number
}

/** @param query 查询参数 @return 医院综合目录分页地址 */
export function hospitalDirectoryListPath(
  query: HospitalDirectoryQueryParameters,
) {
  const parameters = new URLSearchParams()
  if (query.organizationCode)
    parameters.set('organizationCode', query.organizationCode)
  if (query.directoryType)
    parameters.set('directoryType', query.directoryType)
  if (query.keyword) parameters.set('keyword', query.keyword)
  parameters.set('page', String(query.page))
  parameters.set('pageSize', String(query.pageSize))
  return `/master-data/hospital-directory?${parameters.toString()}`
}
