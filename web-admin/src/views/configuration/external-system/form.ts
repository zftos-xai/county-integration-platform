import type {
  CreateExternalEndpointInput, CreateExternalSystemInput, ExternalEndpoint, ExternalSystem,
  ParameterEnvironment, UpdateExternalEndpointInput, UpdateExternalSystemInput,
} from '@/api/system/configuration'

/** 外部系统编辑表单。 */
export type ExternalSystemForm = {
  systemCode: string
  systemName: string
  description: string
  enabled: boolean
  version: string
}

/** 机构接口配置编辑表单。 */
export type ExternalEndpointForm = {
  environment: ParameterEnvironment
  organizationId: number | null
  baseUrl: string
  connectTimeoutMs: number
  readTimeoutMs: number
  vendorCode: string
  username: string
  password: string
  authorizationCode: string
  authenticationChanged: boolean
  enabled: boolean
  version: string
}

/** 创建空白外部系统表单。 */
export function emptyExternalSystemForm(): ExternalSystemForm {
  return { systemCode: '', systemName: '', description: '', enabled: true, version: '' }
}

/** 将外部系统映射为编辑表单。 */
export function externalSystemToForm(system: ExternalSystem): ExternalSystemForm {
  return {
    systemCode: system.systemCode,
    systemName: system.systemName,
    description: system.description,
    enabled: system.enabled,
    version: system.version,
  }
}

/** 创建待自动校验的机构接口配置表单。 */
export function emptyExternalEndpointForm(defaultOrganizationId: number | null): ExternalEndpointForm {
  return {
    environment: 'TEST',
    organizationId: defaultOrganizationId,
    baseUrl: '',
    connectTimeoutMs: 3000,
    readTimeoutMs: 15000,
    vendorCode: '',
    username: '',
    password: '',
    authorizationCode: '',
    authenticationChanged: true,
    enabled: true,
    version: '',
  }
}

/** 将接口配置映射为编辑表单，接入信息在管理员主动查看后另行填充。 */
export function externalEndpointToForm(endpoint: ExternalEndpoint): ExternalEndpointForm {
  return {
    environment: endpoint.environment,
    organizationId: endpoint.organizationId,
    baseUrl: endpoint.baseUrl,
    connectTimeoutMs: endpoint.connectTimeoutMs,
    readTimeoutMs: endpoint.readTimeoutMs,
    vendorCode: '',
    username: '',
    password: '',
    authorizationCode: '',
    authenticationChanged: false,
    enabled: endpoint.enabled,
    version: endpoint.version,
  }
}

/** 校验外部系统表单。 */
export function validateExternalSystemForm(form: ExternalSystemForm, creating: boolean): string | null {
  const code = form.systemCode.trim().toUpperCase()
  if (creating && !/^[A-Z][A-Z0-9_]{1,63}$/.test(code)) return '系统编码必须以字母开头，只能包含大写字母、数字和下划线，长度为2至64位'
  if (!form.systemName.trim() || form.systemName.trim().length > 100) return '系统名称不能为空且不能超过100个字符'
  if (!form.description.trim() || form.description.trim().length > 500) return '用途说明不能为空且不能超过500个字符'
  return null
}

/** 校验机构接口配置及启用条件。 */
export function validateExternalEndpointForm(
  form: ExternalEndpointForm,
  existing: ExternalEndpoint | null,
  organizationRequired: boolean,
): string | null {
  if (organizationRequired && form.organizationId === null) return '请选择适用机构'
  let url: URL
  try { url = new URL(form.baseUrl.trim()) } catch { return '请输入正确的 HIS 接口地址' }
  if (!['http:', 'https:'].includes(url.protocol)) return '请输入正确的 HIS 接口地址'
  if (url.username || url.password || url.hash || !isSupportedHisOperationQuery(url)) return '请输入正确的 HIS 接口地址'
  if (!Number.isInteger(form.connectTimeoutMs) || form.connectTimeoutMs < 100 || form.connectTimeoutMs > 60000) return '连接超时必须为100至60000毫秒的整数'
  if (!Number.isInteger(form.readTimeoutMs) || form.readTimeoutMs < form.connectTimeoutMs || form.readTimeoutMs > 300000) return '读取超时必须不小于连接超时且不超过300000毫秒'
  const vendorCode = form.vendorCode.trim()
  const authorizationCode = form.authorizationCode.trim()
  const username = form.username.trim()
  const password = form.password.trim()
  const organizationChanged = existing !== null && existing.organizationId !== form.organizationId
  if (organizationChanged && !form.authenticationChanged) return '更换机构后，请重新填写该机构的接入信息'
  if ((!existing?.credentialConfigured || organizationChanged) && (!vendorCode || !authorizationCode)) {
    return organizationChanged ? '更换机构后，请重新填写该机构的厂商编号和 HIS 验证码' : '请填写厂商编号和 HIS 验证码'
  }
  if ((username && !password) || (!username && password)) return 'HIS 用户名和 HIS 密码必须同时填写'
  return null
}

/** 判断查询参数是否为空或仅为ASMX页面提供的PHIS_Interface操作提示。 */
function isSupportedHisOperationQuery(url: URL): boolean {
  if (!url.search) return true
  return url.searchParams.size === 1 && url.searchParams.get('op') === 'PHIS_Interface'
}

/** 规范化管理员填写的HIS配置地址，并保留受支持的ASMX操作参数用于回显。 */
export function normalizeHisServiceUrl(value: string): string {
  const url = new URL(value.trim())
  return url.toString()
}

/** 构造新增外部系统请求。 */
export function toCreateExternalSystemInput(form: ExternalSystemForm): CreateExternalSystemInput {
  return {
    systemCode: form.systemCode.trim().toUpperCase(),
    systemName: form.systemName.trim(),
    description: form.description.trim(),
  }
}

/** 构造更新外部系统请求。 */
export function toUpdateExternalSystemInput(form: ExternalSystemForm): UpdateExternalSystemInput {
  return {
    systemName: form.systemName.trim(),
    description: form.description.trim(),
    enabled: form.enabled,
    version: form.version,
  }
}

/** 构造新增机构接口配置请求。 */
export function toCreateExternalEndpointInput(form: ExternalEndpointForm): CreateExternalEndpointInput {
  return {
    environment: form.environment,
    organizationId: form.organizationId,
    baseUrl: normalizeHisServiceUrl(form.baseUrl),
    connectTimeoutMs: form.connectTimeoutMs,
    readTimeoutMs: form.readTimeoutMs,
    authentication: {
      vendorCode: form.vendorCode.trim(), username: form.username.trim(),
      password: form.password, authorizationCode: form.authorizationCode.trim(),
    },
    enabled: form.enabled,
  }
}

/** 构造更新机构接口配置请求，未修改接入信息时保留原值。 */
export function toUpdateExternalEndpointInput(form: ExternalEndpointForm): UpdateExternalEndpointInput {
  const authentication = form.authenticationChanged
    ? {
        vendorCode: form.vendorCode.trim(), username: form.username.trim(),
        password: form.password, authorizationCode: form.authorizationCode.trim(),
      }
    : null
  return {
    environment: form.environment,
    organizationId: form.organizationId,
    baseUrl: normalizeHisServiceUrl(form.baseUrl),
    connectTimeoutMs: form.connectTimeoutMs,
    readTimeoutMs: form.readTimeoutMs,
    authentication,
    enabled: form.enabled,
    version: form.version,
  }
}
