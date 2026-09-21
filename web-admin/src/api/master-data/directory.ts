import { apiRequest } from '@/utils/request'
import { isRecord } from '@/utils/validation'
import {
  hospitalDirectoryListPath,
  type HospitalDirectoryQueryParameters,
} from './directoryApiPaths'

/** 医院综合目录类型。 */
export const hospitalDirectoryTypes = [
  'DEPARTMENT',
  'DOCTOR',
  'WARD',
  'BED',
] as const

/** 医院综合目录类型代码。 */
export type HospitalDirectoryType = (typeof hospitalDirectoryTypes)[number]

/** 当前有效目录中的一条只读记录。 */
export type HospitalDirectoryItem = {
  id: number
  organizationCode: string
  organizationName: string
  directoryType: HospitalDirectoryType
  sourceRecordCode: string
  sourceRecordName: string
  mnemonicCode: string | null
  categoryName: string | null
  remark: string | null
  sourceOrganizationCode: string | null
  relationCount: number
  latestBatchId: number
  latestBatchNo: string
  lastSeenAt: string
}

/** 某一目录类型的当前正式记录数。 */
export type HospitalDirectoryCount = {
  directoryType: HospitalDirectoryType
  total: number
}

/** 医院综合目录分页响应。 */
export type HospitalDirectoryPage = {
  items: HospitalDirectoryItem[]
  counts: HospitalDirectoryCount[]
  total: number
  page: number
  pageSize: number
}

/** 判断未知值是否为医院综合目录类型。 */
export function isHospitalDirectoryType(
  value: unknown,
): value is HospitalDirectoryType {
  return hospitalDirectoryTypes.some((type) => type === value)
}

/** 校验一条正式医院综合目录记录。 */
export function isHospitalDirectoryItem(
  value: unknown,
): value is HospitalDirectoryItem {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    typeof value.organizationCode === 'string' &&
    typeof value.organizationName === 'string' &&
    isHospitalDirectoryType(value.directoryType) &&
    typeof value.sourceRecordCode === 'string' &&
    typeof value.sourceRecordName === 'string' &&
    (typeof value.mnemonicCode === 'string' || value.mnemonicCode === null) &&
    (typeof value.categoryName === 'string' || value.categoryName === null) &&
    (typeof value.remark === 'string' || value.remark === null) &&
    (typeof value.sourceOrganizationCode === 'string' ||
      value.sourceOrganizationCode === null) &&
    typeof value.relationCount === 'number' &&
    typeof value.latestBatchId === 'number' &&
    typeof value.latestBatchNo === 'string' &&
    typeof value.lastSeenAt === 'string'
  )
}

/** 校验医院综合目录分页响应，禁止缺失字段被页面显示成零。 */
export function isHospitalDirectoryPage(
  value: unknown,
): value is HospitalDirectoryPage {
  return (
    isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(isHospitalDirectoryItem) &&
    Array.isArray(value.counts) &&
    value.counts.every(
      (count) =>
        isRecord(count) &&
        isHospitalDirectoryType(count.directoryType) &&
        typeof count.total === 'number',
    ) &&
    typeof value.total === 'number' &&
    typeof value.page === 'number' &&
    typeof value.pageSize === 'number'
  )
}

/** 读取当前账号机构范围内已经发布的医院综合目录。 */
export function listHospitalDirectory(
  query: HospitalDirectoryQueryParameters,
  signal?: AbortSignal,
) {
  return apiRequest<HospitalDirectoryPage>(
    hospitalDirectoryListPath(query),
    { signal },
    isHospitalDirectoryPage,
  )
}
