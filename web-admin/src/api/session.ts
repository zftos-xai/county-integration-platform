import { apiRequest, clearCsrfToken } from '@/utils/request'
import { isRecord, isStringArray } from '@/utils/validation'

/** 后端返回的当前登录用户；权限与机构范围均以服务端结果为准。 */
export type CurrentUser = {
  userId: number
  loginName: string
  displayName: string
  primaryOrganizationId: number
  organizationCode: string
  organizationName: string
  roleNames: string[]
  mustChangePassword: boolean
  permissions: string[]
  organizationCodes: string[]
}

/** Validates the current-user response before permissions enter application state. */
export function isCurrentUser(value: unknown): value is CurrentUser {
  if (!isRecord(value)) return false
  return typeof value.userId === 'number'
    && typeof value.loginName === 'string'
    && typeof value.displayName === 'string'
    && typeof value.primaryOrganizationId === 'number'
    && typeof value.organizationCode === 'string'
    && typeof value.organizationName === 'string'
    && isStringArray(value.roleNames)
    && typeof value.mustChangePassword === 'boolean'
    && isStringArray(value.permissions)
    && isStringArray(value.organizationCodes)
}

/** 读取当前服务端会话对应的登录用户。 */
export function getCurrentUser(signal?: AbortSignal) {
  return apiRequest<CurrentUser>('/session/current', { signal }, isCurrentUser)
}

/** 使用本地账号建立服务端会话，并丢弃登录前取得的 CSRF 令牌。 */
export async function login(loginName: string, password: string) {
  const user = await apiRequest<CurrentUser>('/session/login', {
    method: 'POST',
    body: JSON.stringify({ loginName, password }),
  }, isCurrentUser)
  clearCsrfToken()
  return user
}

/** 注销当前服务端会话；即使请求失败也清除本地 CSRF 令牌缓存。 */
export async function logout() {
  try {
    await apiRequest<void>('/session/logout', { method: 'POST' })
  } finally {
    clearCsrfToken()
  }
}

/** 修改当前用户密码；后端会使原会话失效，因此始终清除 CSRF 缓存。 */
export async function changePassword(currentPassword: string, newPassword: string) {
  try {
    await apiRequest<void>('/session/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    })
  } finally {
    clearCsrfToken()
  }
}
