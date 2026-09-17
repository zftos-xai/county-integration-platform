/** 角色写接口允许的 HTTP 方法。 */
export type RoleWriteMethod = 'POST' | 'PUT'

/** 构造角色详情地址。 */
export function roleDetailPath(id: number) {
  return `/roles/${id}`
}

/** 构造角色权限替换地址。 */
export function rolePermissionsPath(id: number) {
  return `/roles/${id}/permissions`
}

/** 将角色写模型序列化为统一请求配置。 */
export function roleWriteRequest(method: RoleWriteMethod, body: unknown): RequestInit {
  return { method, body: JSON.stringify(body) }
}
