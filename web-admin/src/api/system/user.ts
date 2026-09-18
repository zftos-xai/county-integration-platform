import { apiRequest } from '@/utils/request'
import {
  userDetailPath, userEnabledPath, userOrganizationScopesPath,
  userPasswordResetPath, userRolesPath, userWriteRequest,
} from './userApiPaths'
import { isNumberArray, isRecord } from '@/utils/validation'

/** 当前用户可管理范围内的用户 API 模型。 */
export type ManagedUser = {
  id: number
  loginName: string
  displayName: string
  primaryOrganizationId: number
  organizationCode: string
  enabled: boolean
  mustChangePassword: boolean
  createdAt: string
  updatedAt: string
  version: string
  roleIds: number[]
  organizationScopeIds: number[]
}

/** 创建用户时提交的基本资料和一次性临时密码。 */
export type CreateUserInput = {
  loginName: string
  displayName: string
  primaryOrganizationId: number
  temporaryPassword: string
}

/** 更新用户基本资料时提交的字段；version 用于乐观并发检查。 */
export type UpdateUserInput = {
  displayName: string
  primaryOrganizationId: number
  version: string
}

/** Validates one managed user returned by the identity API. */
export function isManagedUser(value: unknown): value is ManagedUser {
  if (!isRecord(value)) return false
  return typeof value.id === 'number'
    && typeof value.loginName === 'string'
    && typeof value.displayName === 'string'
    && typeof value.primaryOrganizationId === 'number'
    && typeof value.organizationCode === 'string'
    && typeof value.enabled === 'boolean'
    && typeof value.mustChangePassword === 'boolean'
    && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string'
    && typeof value.version === 'string'
    && isNumberArray(value.roleIds)
    && isNumberArray(value.organizationScopeIds)
}

/** Validates a user collection returned by the identity API. */
export function isManagedUserList(value: unknown): value is ManagedUser[] {
  return Array.isArray(value) && value.every(isManagedUser)
}

/** 查询当前用户可见的用户列表。 */
export function listUsers(signal?: AbortSignal) {
  return apiRequest<ManagedUser[]>('/users', { signal }, isManagedUserList)
}

/** 读取指定用户及其角色、机构范围。 */
export function getUser(id: number, signal?: AbortSignal) {
  return apiRequest<ManagedUser>(userDetailPath(id), { signal }, isManagedUser)
}

/** 创建必须首次改密的新用户。 */
export function createUser(input: CreateUserInput) {
  return apiRequest<ManagedUser>('/users', userWriteRequest('POST', input), isManagedUser)
}

/** 使用当前 rowversion 更新用户基本资料。 */
export function updateUser(id: number, input: UpdateUserInput) {
  return apiRequest<ManagedUser>(userDetailPath(id), userWriteRequest('PUT', input), isManagedUser)
}

/** 使用当前 rowversion 启用或停用用户。 */
export function setUserEnabled(id: number, enabled: boolean, version: string) {
  return apiRequest<ManagedUser>(userEnabledPath(id), userWriteRequest('PATCH', { enabled, version }), isManagedUser)
}

/** 重置用户临时密码；密码只进入请求体，不在前端持久化。 */
export function resetUserPassword(id: number, temporaryPassword: string) {
  return apiRequest<void>(userPasswordResetPath(id), userWriteRequest('POST', { temporaryPassword }))
}

/** 以完整集合替换用户角色，避免前端进行增量授权推断。 */
export function replaceUserRoles(id: number, roleIds: number[]) {
  return apiRequest<ManagedUser>(userRolesPath(id), userWriteRequest('PUT', { roleIds }), isManagedUser)
}

/** 以完整集合替换用户显式机构范围。 */
export function replaceUserOrganizationScopes(id: number, organizationIds: number[]) {
  return apiRequest<ManagedUser>(
    userOrganizationScopesPath(id),
    userWriteRequest('PUT', { organizationIds }),
    isManagedUser,
  )
}
