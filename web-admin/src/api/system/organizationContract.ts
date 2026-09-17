/** 机构写接口允许的 HTTP 方法。 */
export type OrganizationWriteMethod = 'POST' | 'PUT' | 'PATCH'

/** 构造机构列表地址，并只在调用方明确指定时附加启用状态。 */
export function organizationListPath(enabled?: boolean) {
  return enabled === undefined ? '/organizations' : `/organizations?enabled=${enabled}`
}

/** 构造机构详情地址。 */
export function organizationDetailPath(id: number) {
  return `/organizations/${id}`
}

/** 构造机构启停地址。 */
export function organizationEnabledPath(id: number) {
  return `/organizations/${id}/enabled`
}

/** 将机构写模型序列化为统一请求配置。 */
export function organizationWriteRequest(method: OrganizationWriteMethod, body: unknown): RequestInit {
  return { method, body: JSON.stringify(body) }
}
