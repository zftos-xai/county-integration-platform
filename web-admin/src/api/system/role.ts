import { apiRequest } from '@/utils/request'
import { isRecord, isStringArray } from '@/utils/validation'
import { roleDetailPath, rolePermissionsPath, roleWriteRequest } from './roleContract'

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

/** 后端代码注册、可分配给角色的功能权限。 */
export type Permission = {
  permissionCode: string
  permissionName: string
}

/** 创建非系统角色的请求。 */
export type CreateRoleInput = {
  roleCode: string
  roleName: string
}

/** 修改非系统角色资料和状态的请求。 */
export type UpdateRoleInput = {
  roleName: string
  enabled: boolean
  version: string
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

/** Validates one code-registered permission returned by the API. */
export function isPermission(value: unknown): value is Permission {
  return isRecord(value)
    && typeof value.permissionCode === 'string'
    && typeof value.permissionName === 'string'
}

/** Validates the registered permission collection returned by the API. */
export function isPermissionList(value: unknown): value is Permission[] {
  return Array.isArray(value) && value.every(isPermission)
}

/** 查询当前主体可读取的角色及其权限代码。 */
export function listRoles(signal?: AbortSignal) {
  return apiRequest<Role[]>('/roles', { signal }, isRoleList)
}

/** 查询指定角色及其当前权限。 */
export function getRole(id: number, signal?: AbortSignal) {
  return apiRequest<Role>(roleDetailPath(id), { signal }, isRole)
}

/** 查询后端代码注册的可分配权限清单。 */
export function listPermissions(signal?: AbortSignal) {
  return apiRequest<Permission[]>('/permissions', { signal }, isPermissionList)
}

/** 创建不受系统保护的平台角色。 */
export function createRole(input: CreateRoleInput) {
  return apiRequest<Role>('/roles', roleWriteRequest('POST', input), isRole)
}

/** 修改非系统角色的名称和启用状态。 */
export function updateRole(id: number, input: UpdateRoleInput) {
  return apiRequest<Role>(roleDetailPath(id), roleWriteRequest('PUT', input), isRole)
}

/** 原子替换非系统角色的代码注册权限。 */
export function replaceRolePermissions(id: number, permissionCodes: string[]) {
  return apiRequest<Role>(rolePermissionsPath(id), roleWriteRequest('PUT', { permissionCodes }), isRole)
}
