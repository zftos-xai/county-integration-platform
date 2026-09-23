import type { Icd10DiagnosisCategory } from './icd10Directory'

/** 平台公共ICD-10目录列表查询参数。 */
export type Icd10DirectoryQueryParameters = {
  diagnosisCategory?: Icd10DiagnosisCategory
  keyword?: string
  page: number
  pageSize: number
}

/** @param query 查询参数 @return 公共ICD-10目录分页地址 */
export function icd10DirectoryListPath(query: Icd10DirectoryQueryParameters) {
  const parameters = new URLSearchParams()
  if (query.diagnosisCategory) parameters.set('diagnosisCategory', query.diagnosisCategory)
  if (query.keyword) parameters.set('keyword', query.keyword)
  parameters.set('page', String(query.page))
  parameters.set('pageSize', String(query.pageSize))
  return `/master-data/icd10-directory?${parameters.toString()}`
}
