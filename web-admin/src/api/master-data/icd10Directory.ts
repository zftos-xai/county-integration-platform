import { apiRequest } from '@/utils/request'
import { isRecord } from '@/utils/validation'
import { icd10DirectoryListPath, type Icd10DirectoryQueryParameters } from './icd10DirectoryApiPaths'

/** 平台公共ICD-10目录按来源协议划分的诊断类别。 */
export const icd10DiagnosisCategories = ['WESTERN', 'TRADITIONAL'] as const

/** 平台公共ICD-10诊断类别代码。 */
export type Icd10DiagnosisCategory = (typeof icd10DiagnosisCategories)[number]

/** 公共ICD-10当前目录中的一条只读记录。 */
export type Icd10DirectoryItem = {
  id: number
  diagnosisCategory: Icd10DiagnosisCategory
  diseaseCode: string
  diseaseName: string
  mnemonicCode: string | null
  remark: string | null
  sourceCreatedAt: string
  sourceDiseaseId: string | null
  latestBatchId: number
  latestBatchNo: string
  lastSeenAt: string
}

/** 一个诊断类别当前已发布的公共目录数量。 */
export type Icd10DirectoryCount = {
  diagnosisCategory: Icd10DiagnosisCategory
  total: number
}

/** 平台公共ICD-10目录分页响应。 */
export type Icd10DirectoryPage = {
  items: Icd10DirectoryItem[]
  counts: Icd10DirectoryCount[]
  total: number
  page: number
  pageSize: number
}

/** 判断未知值是否为已支持的公共ICD-10诊断类别。 */
export function isIcd10DiagnosisCategory(value: unknown): value is Icd10DiagnosisCategory {
  return icd10DiagnosisCategories.some((category) => category === value)
}

/** 校验一条公共ICD-10目录记录，禁止页面把缺失字段伪装为可用数据。 */
export function isIcd10DirectoryItem(value: unknown): value is Icd10DirectoryItem {
  return isRecord(value)
    && typeof value.id === 'number'
    && isIcd10DiagnosisCategory(value.diagnosisCategory)
    && typeof value.diseaseCode === 'string'
    && typeof value.diseaseName === 'string'
    && (typeof value.mnemonicCode === 'string' || value.mnemonicCode === null)
    && (typeof value.remark === 'string' || value.remark === null)
    && typeof value.sourceCreatedAt === 'string'
    && (typeof value.sourceDiseaseId === 'string' || value.sourceDiseaseId === null)
    && typeof value.latestBatchId === 'number'
    && typeof value.latestBatchNo === 'string'
    && typeof value.lastSeenAt === 'string'
}

/** 校验公共ICD-10目录分页响应。 */
export function isIcd10DirectoryPage(value: unknown): value is Icd10DirectoryPage {
  return isRecord(value)
    && Array.isArray(value.items)
    && value.items.every(isIcd10DirectoryItem)
    && Array.isArray(value.counts)
    && value.counts.every((count) => isRecord(count)
      && isIcd10DiagnosisCategory(count.diagnosisCategory)
      && typeof count.total === 'number')
    && typeof value.total === 'number'
    && typeof value.page === 'number'
    && typeof value.pageSize === 'number'
}

/** 读取当前已发布的平台公共ICD-10目录；该请求不会发起HIS同步。 */
export function listIcd10Directory(query: Icd10DirectoryQueryParameters, signal?: AbortSignal) {
  return apiRequest<Icd10DirectoryPage>(
    icd10DirectoryListPath(query),
    { signal },
    isIcd10DirectoryPage,
  )
}
