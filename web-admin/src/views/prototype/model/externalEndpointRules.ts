/** 原型服务地址就绪校验所需的最小配置。 */
export type EndpointCandidate = {
  baseUrl: string
  connectTimeout: number
  readTimeout: number
  tls: boolean
  credentialRef: string
}

/** 按已确认的 HTTPS、凭证引用和超时边界返回首个无法继续的原因。 */
export function endpointReadinessError(endpoint: EndpointCandidate): string | null {
  if (!endpoint.baseUrl.startsWith('https://') || !endpoint.tls) return '服务地址必须使用 HTTPS 并启用 TLS'
  if (!/^[a-z][a-z0-9+.-]*:\/\/\S+$/.test(endpoint.credentialRef)) return '凭证必须使用医院批准的引用 URI，不能填写密钥明文'
  if (!Number.isInteger(endpoint.connectTimeout) || endpoint.connectTimeout < 100 || endpoint.connectTimeout > 60000) return '连接超时必须为 100 至 60000 毫秒的整数'
  if (!Number.isInteger(endpoint.readTimeout) || endpoint.readTimeout < endpoint.connectTimeout || endpoint.readTimeout > 300000) return '读取超时必须为不小于连接超时且不超过 300000 毫秒的整数'
  return null
}
