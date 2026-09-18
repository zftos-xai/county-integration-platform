import type { CurrentUser } from '@/api/session'

/** 页面访问权限检查可执行的标准化的页面跳转决定。 */
export type RouteAccessDecision = 'allow' | 'login' | 'home' | 'change-password' | 'forbidden'

/** 权限决策所需的目标页面的最少信息，避免工具层依赖 Vue Router 对象。 */
export type RouteAccessTarget = {
  path: string
  publicPage: boolean
  guestOnly: boolean
  requiredPermission?: string
}

/**
 * 根据会话、首次改密状态和功能权限解析导航结果。
 * 此判断不替代后端授权，也不推导用户的机构数据范围。
 */
export function resolveRouteAccess(
  user: CurrentUser | null,
  target: RouteAccessTarget,
): RouteAccessDecision {
  if (target.publicPage && !target.guestOnly) return 'allow'
  if (target.guestOnly) {
    if (!user) return 'allow'
    return user.mustChangePassword ? 'change-password' : 'home'
  }
  if (!user) return 'login'
  if (user.mustChangePassword && target.path !== '/change-password') return 'change-password'
  if (!user.mustChangePassword && target.path === '/change-password') return 'home'
  if (target.requiredPermission && !user.permissions.includes(target.requiredPermission)) return 'forbidden'
  return 'allow'
}
