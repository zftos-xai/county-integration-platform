/** 用户写接口允许的 HTTP 方法。 */
export type UserWriteMethod = 'POST' | 'PUT' | 'PATCH'

/** 构造用户详情地址。 */
export function userDetailPath(id: number) {
  return `/users/${id}`
}

/** 构造用户启停地址。 */
export function userEnabledPath(id: number) {
  return `/users/${id}/enabled`
}

/** 构造用户临时密码重置地址。 */
export function userPasswordResetPath(id: number) {
  return `/users/${id}/password-reset`
}

/** 构造用户角色替换地址。 */
export function userRolesPath(id: number) {
  return `/users/${id}/roles`
}

/** 构造用户显式机构范围替换地址。 */
export function userOrganizationScopesPath(id: number) {
  return `/users/${id}/organization-scopes`
}

/** 将用户写模型序列化为统一请求配置。 */
export function userWriteRequest(method: UserWriteMethod, body: unknown): RequestInit {
  return { method, body: JSON.stringify(body) }
}
