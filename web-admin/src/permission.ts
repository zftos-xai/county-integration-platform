import type { Router } from 'vue-router'
import { authState, initializeAuth } from '@/store/modules/auth'
import { resolveRouteAccess } from '@/utils/routeAccess'
import { platformSettings } from '@/settings'

/**
 * 注册全局页面访问权限检查。
 * 前端只负责导航约束和体验提示，最终身份、权限与机构范围仍由后端校验。
 */
export function setupPermissionGuard(router: Router) {
  router.beforeEach(async to => {
    const pageTitle = typeof to.meta.title === 'string' ? to.meta.title : ''
    document.title = pageTitle ? `${pageTitle} - ${platformSettings.title}` : platformSettings.title
    const publicPage = Boolean(to.meta.public)
    const guestOnly = Boolean(to.meta.guestOnly)
    if (!publicPage || guestOnly) await initializeAuth()
    const decision = resolveRouteAccess(authState.user, {
      path: to.path,
      publicPage,
      guestOnly,
      requiredPermission: typeof to.meta.requiredPermission === 'string'
        ? to.meta.requiredPermission
        : undefined,
    })
    if (decision === 'login') return { path: '/login', query: { redirect: to.fullPath } }
    if (decision === 'home') return '/'
    if (decision === 'change-password') return '/change-password'
    if (decision === 'forbidden') return '/forbidden'
    return true
  })
}
