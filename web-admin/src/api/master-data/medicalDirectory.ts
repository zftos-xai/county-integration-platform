import { apiRequest } from '@/utils/request'
import { isRecord } from '@/utils/validation'
import {
  medicalDirectoryListPath,
  type MedicalDirectoryQueryParameters,
} from './medicalDirectoryApiPaths'

/** 当前已接入的医疗目录类型。 */
export const medicalDirectoryTypes = [
  'TRADITIONAL_MEDICINE',
  'WESTERN_MEDICINE',
  'TREATMENT',
  'CONSUMABLE',
] as const

/** 医疗目录类型代码。 */
export type MedicalDirectoryType = (typeof medicalDirectoryTypes)[number]

/** 一条从100-004取得并自动更新到当前目录的只读记录。 */
export type MedicalDirectoryItem = {
  id: number
  organizationCode: string
  organizationName: string
  directoryType: MedicalDirectoryType
  sourceRecordCode: string
  sourceRecordName: string
  mnemonicCode: string | null
  categoryName: string
  unit: string | null
  specification: string | null
  manufacturerName: string | null
  sourceEnabledFlag: string
  sourceCreatedAt: string
  dosageForm: string | null
  remark: string | null
  packageUnit: string | null
  conversionFactor: string | null
  approvalNumber: string | null
  standardCode: string | null
  packageMaterial: string | null
  processingMethod: string | null
  region: string | null
  category: string | null
  latestBatchId: number
  latestBatchNo: string
  sourceOrganizationId: string | null
  lastSeenAt: string
}

/** 某一医疗目录类型的当前有效记录数。 */
export type MedicalDirectoryCount = {
  directoryType: MedicalDirectoryType
  total: number
}

/** 医疗目录分页响应。 */
export type MedicalDirectoryPage = {
  items: MedicalDirectoryItem[]
  counts: MedicalDirectoryCount[]
  total: number
  page: number
  pageSize: number
}

/** 判断未知值是否为医疗目录类型。 */
export function isMedicalDirectoryType(
  value: unknown,
): value is MedicalDirectoryType {
  return medicalDirectoryTypes.some((type) => type === value)
}

/** 校验一条当前医疗目录记录，避免页面把缺失字段伪装成来源数据。 */
export function isMedicalDirectoryItem(
  value: unknown,
): value is MedicalDirectoryItem {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    typeof value.organizationCode === 'string' &&
    typeof value.organizationName === 'string' &&
    isMedicalDirectoryType(value.directoryType) &&
    typeof value.sourceRecordCode === 'string' &&
    typeof value.sourceRecordName === 'string' &&
    (typeof value.mnemonicCode === 'string' || value.mnemonicCode === null) &&
    typeof value.categoryName === 'string' &&
    (typeof value.unit === 'string' || value.unit === null) &&
    (typeof value.specification === 'string' || value.specification === null) &&
    (typeof value.manufacturerName === 'string' || value.manufacturerName === null) &&
    typeof value.sourceEnabledFlag === 'string' &&
    typeof value.sourceCreatedAt === 'string' &&
    (typeof value.dosageForm === 'string' || value.dosageForm === null) &&
    (typeof value.remark === 'string' || value.remark === null) &&
    (typeof value.packageUnit === 'string' || value.packageUnit === null) &&
    (typeof value.conversionFactor === 'string' || value.conversionFactor === null) &&
    (typeof value.approvalNumber === 'string' || value.approvalNumber === null) &&
    (typeof value.standardCode === 'string' || value.standardCode === null) &&
    (typeof value.packageMaterial === 'string' || value.packageMaterial === null) &&
    (typeof value.processingMethod === 'string' || value.processingMethod === null) &&
    (typeof value.region === 'string' || value.region === null) &&
    (typeof value.category === 'string' || value.category === null) &&
    typeof value.latestBatchId === 'number' &&
    typeof value.latestBatchNo === 'string' &&
    (typeof value.sourceOrganizationId === 'string' ||
      value.sourceOrganizationId === null) &&
    typeof value.lastSeenAt === 'string'
  )
}

/** 校验医疗目录分页响应，禁止缺失字段被页面显示成零。 */
export function isMedicalDirectoryPage(
  value: unknown,
): value is MedicalDirectoryPage {
  return (
    isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(isMedicalDirectoryItem) &&
    Array.isArray(value.counts) &&
    value.counts.every(
      (count) =>
        isRecord(count) &&
        isMedicalDirectoryType(count.directoryType) &&
        typeof count.total === 'number',
    ) &&
    typeof value.total === 'number' &&
    typeof value.page === 'number' &&
    typeof value.pageSize === 'number'
  )
}

/** 读取当前账号机构范围内已经自动更新的医疗目录。 */
export function listMedicalDirectory(
  query: MedicalDirectoryQueryParameters,
  signal?: AbortSignal,
) {
  return apiRequest<MedicalDirectoryPage>(
    medicalDirectoryListPath(query),
    { signal },
    isMedicalDirectoryPage,
  )
}
