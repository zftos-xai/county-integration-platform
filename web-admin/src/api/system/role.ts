import { apiRequest } from '@/utils/request'
import { isRecord, isStringArray } from '@/utils/validation'

/** 用户授权页面使用的角色摘要模型。 */
export type Role = {
  id: number
  roleCode: string
  roleName: string
  enabled: boolean
  systemManaged: boolean
  createdAt: string
  updatedAt: string
  version: string
  permissionCodes: string[]
}

/** Validates one role returned by the access-management API. */
export function isRole(value: unknown): value is Role {
  if (!isRecord(value)) return false
  return typeof value.id === 'number'
    && typeof value.roleCode === 'string'
    && typeof value.roleName === 'string'
    && typeof value.enabled === 'boolean'
    && typeof value.systemManaged === 'boolean'
    && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string'
    && typeof value.version === 'string'
    && isStringArray(value.permissionCodes)
}

/** Validates a role collection returned by the access-management API. */
export function isRoleList(value: unknown): value is Role[] {
  return Array.isArray(value) && value.every(isRole)
}

/** 查询当前主体可读取的角色及其权限代码。 */
export function listRoles(signal?: AbortSignal) {
  return apiRequest<Role[]>('/roles', { signal }, isRoleList)
}
