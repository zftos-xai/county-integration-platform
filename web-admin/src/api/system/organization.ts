import { apiRequest } from '@/utils/request'
import {
  organizationDetailPath, organizationEnabledPath, organizationListPath, organizationWriteRequest,
} from './organizationApiPaths'
import { isRecord } from '@/utils/validation'

/** 当前用户可管理范围内的机构 API 模型。 */
export type Organization = {
  id: number
  organizationCode: string
  organizationName: string
  organizationType: string
  parentId: number | null
  enabled: boolean
  validFrom: string | null
  validTo: string | null
  createdAt: string
  updatedAt: string
  version: string
}

/** 创建机构时允许提交的字段。 */
export type CreateOrganizationInput = {
  organizationCode: string
  organizationName: string
  organizationType: string
  parentId: number | null
  validFrom: string | null
  validTo: string | null
}

/** 更新机构时提交的字段；version 用于服务端乐观并发检查。 */
export type UpdateOrganizationInput = Omit<CreateOrganizationInput, 'organizationCode'> & {
  version: string
}

/** Validates one organization returned by the management API. */
export function isOrganization(value: unknown): value is Organization {
  if (!isRecord(value)) return false
  return typeof value.id === 'number'
    && typeof value.organizationCode === 'string'
    && typeof value.organizationName === 'string'
    && typeof value.organizationType === 'string'
    && (typeof value.parentId === 'number' || value.parentId === null)
    && typeof value.enabled === 'boolean'
    && (typeof value.validFrom === 'string' || value.validFrom === null)
    && (typeof value.validTo === 'string' || value.validTo === null)
    && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string'
    && typeof value.version === 'string'
}

/** Validates an organization collection returned by the management API. */
export function isOrganizationList(value: unknown): value is Organization[] {
  return Array.isArray(value) && value.every(isOrganization)
}

/** 按可选启用状态查询当前用户可见的机构。 */
export function listOrganizations(enabled?: boolean, signal?: AbortSignal) {
  return apiRequest<Organization[]>(organizationListPath(enabled), { signal }, isOrganizationList)
}

/** 读取指定机构详情，机构范围越权由后端拒绝。 */
export function getOrganization(id: number, signal?: AbortSignal) {
  return apiRequest<Organization>(organizationDetailPath(id), { signal }, isOrganization)
}

/** 创建机构。 */
export function createOrganization(input: CreateOrganizationInput) {
  return apiRequest<Organization>('/organizations', organizationWriteRequest('POST', input), isOrganization)
}

/** 使用当前 rowversion 更新机构基本信息。 */
export function updateOrganization(id: number, input: UpdateOrganizationInput) {
  return apiRequest<Organization>(organizationDetailPath(id), organizationWriteRequest('PUT', input), isOrganization)
}

/** 使用当前 rowversion 启用或停用机构。 */
export function setOrganizationEnabled(id: number, enabled: boolean, version: string) {
  return apiRequest<Organization>(
    organizationEnabledPath(id),
    organizationWriteRequest('PATCH', { enabled, version }),
    isOrganization,
  )
}
