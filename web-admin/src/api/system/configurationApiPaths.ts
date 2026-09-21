/** 配置管理写接口允许的 HTTP 方法。 */
export type ConfigurationWriteMethod = 'POST' | 'PUT' | 'DELETE'

/** 构造参数值写入地址，并对参数键执行路径编码。 */
export function parameterValuePath(key: string) {
  return `/configuration/parameters/${encodeURIComponent(key)}`
}

/** 构造字典类型详情地址。 */
export function dictionaryTypePath(typeId: number) {
  return `/configuration/dictionaries/${typeId}`
}

/** 构造字典项集合地址。 */
export function dictionaryItemsPath(typeId: number, includeDisabled = false) {
  const suffix = includeDisabled ? '?includeDisabled=true' : ''
  return `/configuration/dictionaries/${typeId}/items${suffix}`
}

/** 构造字典项详情地址。 */
export function dictionaryItemPath(itemId: number) {
  return `/configuration/dictionary-items/${itemId}`
}

/** 构造指定外部系统的机构服务地址集合路径。 */
export function externalEndpointsPath(systemId: number) {
  return `/configuration/external-systems/${systemId}/endpoints`
}

/** 构造外部系统详情路径。 */
export function externalSystemPath(systemId: number) {
  return `/configuration/external-systems/${systemId}`
}

/** 构造机构服务地址详情路径。 */
export function externalEndpointPath(endpointId: number) {
  return `/configuration/external-endpoints/${endpointId}`
}

/** 构造机构HIS接入信息按需查看路径。 */
export function externalEndpointAuthenticationPath(endpointId: number) {
  return `${externalEndpointPath(endpointId)}/authentication`
}

/** 构造以100-008自动确认HIS来源机构的路径。 */
export function externalEndpointVerificationPath(endpointId: number) {
  return `${externalEndpointPath(endpointId)}/verify`
}

/** 将配置写模型序列化为统一请求配置。 */
export function configurationWriteRequest(method: ConfigurationWriteMethod, body: unknown): RequestInit {
  return { method, body: JSON.stringify(body) }
}
