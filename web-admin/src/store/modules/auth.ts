import { reactive } from 'vue'
import { ApiClientError } from '@/utils/request'
import {
  changePassword as changePasswordRequest,
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
} from '@/api/session'
import type { CurrentUser } from '@/api/session'

type AuthState = {
  isInitialized: boolean
  isLoading: boolean
  user: CurrentUser | null
  error: ApiClientError | null
}

/** 全局会话状态；不保存密码、Cookie 或 CSRF 令牌。 */
export const authState = reactive<AuthState>({
  isInitialized: false,
  isLoading: false,
  user: null,
  error: null,
})

let initialization: Promise<void> | null = null

/**
 * 初始化当前会话，复用进行中的请求，避免路由并发导航重复查询主体。
 * 401 表示未登录而不是系统错误。
 */
export async function initializeAuth() {
  if (authState.isInitialized) return
  if (initialization) return initialization
  initialization = (async () => {
    authState.isLoading = true
    authState.error = null
    try {
      authState.user = await getCurrentUser()
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 401) authState.user = null
      else authState.error = error instanceof ApiClientError
        ? error
        : new ApiClientError('UNKNOWN_ERROR', '无法读取当前登录状态', 0)
    } finally {
      authState.isLoading = false
      authState.isInitialized = true
      initialization = null
    }
  })()
  return initialization
}

/** 使用凭据登录并更新内存中的当前主体。 */
export async function authenticate(loginName: string, password: string) {
  authState.isLoading = true
  authState.error = null
  try {
    authState.user = await loginRequest(loginName, password)
    authState.isInitialized = true
    return authState.user
  } finally {
    authState.isLoading = false
  }
}

/** 注销服务端会话，并无条件清空前端主体状态。 */
export async function endSession() {
  authState.isLoading = true
  try {
    if (authState.user) await logoutRequest()
  } finally {
    authState.user = null
    authState.isLoading = false
    authState.isInitialized = true
  }
}

/** 修改密码后清空主体，要求使用新密码重新建立会话。 */
export async function updatePassword(currentPassword: string, newPassword: string) {
  authState.isLoading = true
  try {
    await changePasswordRequest(currentPassword, newPassword)
  } finally {
    authState.user = null
    authState.isLoading = false
    authState.isInitialized = true
  }
}

/** 判断当前主体是否具有指定功能权限；未指定权限的功能默认可见。 */
export function hasPermission(permission?: string) {
  return !permission || Boolean(authState.user?.permissions.includes(permission))
}
