import process from 'node:process'

const BASE_URL = (process.env.PLATFORM_ACCEPTANCE_BASE_URL ?? 'http://127.0.0.1:18080').replace(/\/$/, '')
const ADMIN_LOGIN = process.env.PLATFORM_ACCEPTANCE_ADMIN_LOGIN ?? 'admin'
const ADMIN_PASSWORD = process.env.PLATFORM_ACCEPTANCE_ADMIN_PASSWORD ?? ''
const TEMPORARY_PASSWORD = process.env.PLATFORM_ACCEPTANCE_TEMP_PASSWORD ?? ''
const CLEANUP = process.argv.includes('--cleanup')
const REQUEST_TIMEOUT_MS = 15_000

const organizationDefinitions = [
  { code: 'ACCEPT.COUNTY', name: '业务验收县级机构', type: 'COUNTY', parentCode: null },
  { code: 'ACCEPT.CLINIC', name: '业务验收基层机构', type: 'PRIMARY_CARE', parentCode: 'ACCEPT.COUNTY' },
  { code: 'ACCEPT.FUTURE', name: '业务验收尚未生效机构', type: 'PRIMARY_CARE', parentCode: 'ACCEPT.COUNTY', timeState: 'future' },
  { code: 'ACCEPT.EXPIRED', name: '业务验收已过期机构', type: 'PRIMARY_CARE', parentCode: 'ACCEPT.COUNTY', timeState: 'expired' },
]

const roleDefinitions = [
  {
    code: 'ACCEPTANCE_OPERATOR',
    name: '业务验收管理员',
    permissions: [
      'organization:read', 'organization:write', 'identity:read', 'identity:write',
      'access:read', 'access:write', 'configuration:read', 'configuration:write', 'audit:read',
    ],
  },
  {
    code: 'ACCEPTANCE_READER',
    name: '业务验收只读人员',
    permissions: ['organization:read', 'identity:read', 'access:read', 'configuration:read', 'audit:read'],
  },
  { code: 'ACCEPTANCE_EMPTY', name: '业务验收待分配权限角色', permissions: [] },
]

const userDefinitions = [
  { login: 'accept.operator', name: '业务验收管理员', organizationCode: 'ACCEPT.COUNTY', roleCode: 'ACCEPTANCE_OPERATOR', scopeCodes: ['ACCEPT.COUNTY', 'ACCEPT.CLINIC', 'ACCEPT.FUTURE', 'ACCEPT.EXPIRED'], enabled: true },
  { login: 'accept.reader', name: '业务验收只读人员', organizationCode: 'ACCEPT.CLINIC', roleCode: 'ACCEPTANCE_READER', scopeCodes: ['ACCEPT.CLINIC'], enabled: true },
  { login: 'accept.pending', name: '业务验收待授权用户', organizationCode: 'ACCEPT.COUNTY', roleCode: null, scopeCodes: ['ACCEPT.COUNTY'], enabled: true },
  { login: 'accept.disabled', name: '业务验收已注销用户', organizationCode: 'ACCEPT.CLINIC', roleCode: 'ACCEPTANCE_READER', scopeCodes: ['ACCEPT.CLINIC'], enabled: false },
]

const parameterDefinitions = [
  { key: 'management.list.default-page-size', environment: 'DEVELOPMENT', value: '25', enabled: true },
  { key: 'management.audit.default-query-limit', environment: 'TEST', value: '80', enabled: false },
]

const dictionaryDefinitions = [
  {
    code: 'ACCEPT_VISIT_TYPE',
    name: '业务验收就诊类型',
    description: '仅用于管理页面业务流程验收，不用于真实诊疗数据。',
    items: [
      { code: 'OUTPATIENT', label: '门诊', sortOrder: 10, enabled: true },
      { code: 'INPATIENT', label: '住院', sortOrder: 20, enabled: true },
      { code: 'EMERGENCY', label: '急诊', sortOrder: 30, enabled: true },
      { code: 'OLD_SERVICE', label: '已停用的旧服务', sortOrder: 90, enabled: false },
    ],
  },
  {
    code: 'ACCEPT_TASK_STATUS',
    name: '业务验收任务状态',
    description: '用于检查字典项查询、排序提示、启停和并发修改。',
    items: [
      { code: 'PENDING', label: '等待处理', sortOrder: 10, enabled: true },
      { code: 'PROCESSING', label: '正在处理', sortOrder: 20, enabled: true },
      { code: 'DONE', label: '处理完成', sortOrder: 20, enabled: true },
    ],
  },
]

class ApiSession {
  constructor(baseUrl) {
    this.baseUrl = baseUrl
    this.cookies = new Map()
    this.csrf = null
  }

  captureCookies(response) {
    const headers = typeof response.headers.getSetCookie === 'function'
      ? response.headers.getSetCookie()
      : [response.headers.get('set-cookie')].filter(Boolean)
    for (const header of headers) {
      const pair = header.split(';', 1)[0]
      const separator = pair.indexOf('=')
      if (separator > 0) this.cookies.set(pair.slice(0, separator), pair.slice(separator + 1))
    }
  }

  cookieHeader() {
    return [...this.cookies].map(([name, value]) => `${name}=${value}`).join('; ')
  }

  async loadCsrf() {
    const data = await this.request('/session/csrf', { skipCsrf: true })
    this.csrf = data
  }

  async login(loginName, password) {
    this.cookies.clear()
    this.csrf = null
    await this.loadCsrf()
    const user = await this.request('/session/login', {
      method: 'POST',
      body: { loginName, password },
    })
    this.csrf = null
    return user
  }

  async request(path, options = {}) {
    const method = options.method ?? 'GET'
    if (!options.skipCsrf && !['GET', 'HEAD', 'OPTIONS'].includes(method) && !this.csrf) {
      await this.loadCsrf()
    }
    const headers = new Headers({ Accept: 'application/json' })
    if (options.body !== undefined) headers.set('Content-Type', 'application/json')
    const cookie = this.cookieHeader()
    if (cookie) headers.set('Cookie', cookie)
    if (!options.skipCsrf && this.csrf && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      headers.set(this.csrf.headerName, this.csrf.token)
    }
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
    let response
    try {
      response = await fetch(`${this.baseUrl}/api/v1${path}`, {
        method,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: controller.signal,
      })
    } catch (error) {
      if (controller.signal.aborted) throw new Error(`请求超时：${method} ${path}`)
      throw new Error(`无法连接后台服务 ${this.baseUrl}：${error instanceof Error ? error.message : String(error)}`)
    } finally {
      clearTimeout(timeout)
    }
    this.captureCookies(response)
    const contentType = response.headers.get('content-type') ?? ''
    const payload = contentType.includes('application/json') ? await response.json() : null
    if (!response.ok) {
      const message = payload?.message ?? `HTTP ${response.status}`
      const requestId = payload?.requestId ?? response.headers.get('x-request-id')
      throw new Error(`${method} ${path} 失败：${message}${requestId ? `（请求编号 ${requestId}）` : ''}`)
    }
    if (!payload || !Object.hasOwn(payload, 'data')) throw new Error(`${method} ${path} 返回格式不正确`)
    return payload.data
  }
}

function requireSecrets() {
  if (!ADMIN_PASSWORD) throw new Error('请设置 PLATFORM_ACCEPTANCE_ADMIN_PASSWORD')
  if (!CLEANUP && TEMPORARY_PASSWORD.length < 12) {
    throw new Error('请设置至少 12 个字符的 PLATFORM_ACCEPTANCE_TEMP_PASSWORD')
  }
  if (!CLEANUP && userDefinitions.some(user => TEMPORARY_PASSWORD.toLowerCase().includes(user.login.toLowerCase()))) {
    throw new Error('PLATFORM_ACCEPTANCE_TEMP_PASSWORD 不能包含验收用户登录名')
  }
}

function sameValues(left, right) {
  return [...left].sort().join('\n') === [...right].sort().join('\n')
}

function timeRange(timeState) {
  const now = Date.now()
  if (timeState === 'future') return { validFrom: new Date(now + 7 * 86_400_000).toISOString(), validTo: null }
  if (timeState === 'expired') return {
    validFrom: new Date(now - 365 * 86_400_000).toISOString(),
    validTo: new Date(now - 86_400_000).toISOString(),
  }
  return { validFrom: null, validTo: null }
}

async function ensureOrganizations(session) {
  const currentUser = await session.request('/session/current')
  let organizations = await session.request('/organizations')
  const configuredParentCode = process.env.PLATFORM_ACCEPTANCE_PARENT_ORGANIZATION_CODE
  const root = organizations.find(item => item.organizationCode === configuredParentCode)
    ?? organizations.find(item => item.organizationCode === currentUser.organizationCode)
    ?? organizations.find(item => item.enabled)
  if (!root) throw new Error('当前账号没有可作为验收机构上级的启用机构')

  const byCode = new Map(organizations.map(item => [item.organizationCode, item]))
  for (const definition of organizationDefinitions) {
    const parent = definition.parentCode ? byCode.get(definition.parentCode) : root
    if (!parent) throw new Error(`找不到 ${definition.code} 的上级机构`)
    let organization = byCode.get(definition.code)
    if (!organization) {
      organization = await session.request('/organizations', {
        method: 'POST',
        body: {
          organizationCode: definition.code,
          organizationName: definition.name,
          organizationType: definition.type,
          parentId: parent.id,
          ...timeRange(definition.timeState),
        },
      })
      await session.login(ADMIN_LOGIN, ADMIN_PASSWORD)
      organizations = await session.request('/organizations')
      organization = organizations.find(item => item.organizationCode === definition.code) ?? organization
    }
    if (!organization.enabled) {
      organization = await session.request(`/organizations/${organization.id}/enabled`, {
        method: 'PATCH', body: { enabled: true, version: organization.version },
      })
    }
    byCode.set(definition.code, organization)
  }
  return byCode
}

async function ensureRoles(session) {
  const registered = await session.request('/permissions')
  const registeredCodes = new Set(registered.map(item => item.permissionCode))
  let roles = await session.request('/roles')
  const byCode = new Map(roles.map(item => [item.roleCode, item]))
  for (const definition of roleDefinitions) {
    const unknown = definition.permissions.filter(code => !registeredCodes.has(code))
    if (unknown.length) throw new Error(`${definition.code} 使用了后台未登记权限：${unknown.join('、')}`)
    let role = byCode.get(definition.code)
    if (!role) role = await session.request('/roles', { method: 'POST', body: { roleCode: definition.code, roleName: definition.name } })
    if (role.roleName !== definition.name || !role.enabled) {
      role = await session.request(`/roles/${role.id}`, {
        method: 'PUT', body: { roleName: definition.name, enabled: true, version: role.version },
      })
    }
    if (!sameValues(role.permissionCodes, definition.permissions)) {
      role = await session.request(`/roles/${role.id}/permissions`, {
        method: 'PUT', body: { permissionCodes: definition.permissions },
      })
    }
    byCode.set(definition.code, role)
  }
  return byCode
}

async function ensureUsers(session, organizations, roles) {
  let users = await session.request('/users')
  const byLogin = new Map(users.map(item => [item.loginName, item]))
  for (const definition of userDefinitions) {
    const primaryOrganization = organizations.get(definition.organizationCode)
    const role = definition.roleCode ? roles.get(definition.roleCode) : null
    const scopeIds = definition.scopeCodes.map(code => organizations.get(code)?.id)
    if (!primaryOrganization || scopeIds.some(id => !id)) throw new Error(`${definition.login} 的机构设置不完整`)
    let user = byLogin.get(definition.login)
    if (!user) {
      user = await session.request('/users', {
        method: 'POST',
        body: {
          loginName: definition.login,
          displayName: definition.name,
          primaryOrganizationId: primaryOrganization.id,
          temporaryPassword: TEMPORARY_PASSWORD,
        },
      })
    } else {
      await session.request(`/users/${user.id}/password-reset`, {
        method: 'POST', body: { temporaryPassword: TEMPORARY_PASSWORD },
      })
      user = await session.request(`/users/${user.id}`)
    }
    if (user.displayName !== definition.name || user.primaryOrganizationId !== primaryOrganization.id) {
      user = await session.request(`/users/${user.id}`, {
        method: 'PUT',
        body: { displayName: definition.name, primaryOrganizationId: primaryOrganization.id, version: user.version },
      })
    }
    const desiredRoleIds = role ? [role.id] : []
    if (!sameValues(user.roleIds, desiredRoleIds)) {
      user = await session.request(`/users/${user.id}/roles`, { method: 'PUT', body: { roleIds: desiredRoleIds } })
    }
    if (!sameValues(user.organizationScopeIds, scopeIds)) {
      user = await session.request(`/users/${user.id}/organization-scopes`, {
        method: 'PUT', body: { organizationIds: scopeIds },
      })
    }
    if (user.enabled !== definition.enabled) {
      user = await session.request(`/users/${user.id}/enabled`, {
        method: 'PATCH', body: { enabled: definition.enabled, version: user.version },
      })
    }
    byLogin.set(definition.login, user)
  }
  return byLogin
}

async function ensureParameters(session) {
  const definitions = await session.request('/configuration/parameter-definitions')
  const knownKeys = new Set(definitions.map(item => item.key))
  let values = await session.request('/configuration/parameters')
  for (const definition of parameterDefinitions) {
    if (!knownKeys.has(definition.key)) throw new Error(`后台未登记参数 ${definition.key}`)
    const existing = values.find(item => item.parameterKey === definition.key
      && item.environment === definition.environment && item.organizationId === null)
    if (existing?.value === definition.value && existing.enabled === definition.enabled) continue
    const saved = await session.request(`/configuration/parameters/${encodeURIComponent(definition.key)}`, {
      method: 'PUT',
      body: {
        environment: definition.environment,
        organizationId: null,
        value: definition.value,
        enabled: definition.enabled,
        ...(existing ? { version: existing.version } : {}),
      },
    })
    values = [...values.filter(item => item.id !== saved.id), saved]
  }
}

async function ensureDictionaries(session) {
  let types = await session.request('/configuration/dictionaries')
  for (const definition of dictionaryDefinitions) {
    let type = types.find(item => item.typeCode === definition.code)
    if (!type) {
      type = await session.request('/configuration/dictionaries', {
        method: 'POST', body: { typeCode: definition.code, typeName: definition.name, description: definition.description },
      })
      types.push(type)
    } else if (type.typeName !== definition.name || type.description !== definition.description || !type.enabled) {
      type = await session.request(`/configuration/dictionaries/${type.id}`, {
        method: 'PUT', body: { typeName: definition.name, description: definition.description, enabled: true, version: type.version },
      })
    }
    let items = await session.request(`/configuration/dictionaries/${type.id}/items?includeDisabled=true`)
    for (const itemDefinition of definition.items) {
      let item = items.find(candidate => candidate.itemCode === itemDefinition.code)
      if (!item) {
        item = await session.request(`/configuration/dictionaries/${type.id}/items`, {
          method: 'POST',
          body: { itemCode: itemDefinition.code, itemLabel: itemDefinition.label, sortOrder: itemDefinition.sortOrder },
        })
        items.push(item)
      }
      if (item.itemLabel !== itemDefinition.label || item.sortOrder !== itemDefinition.sortOrder || item.enabled !== itemDefinition.enabled) {
        item = await session.request(`/configuration/dictionary-items/${item.id}`, {
          method: 'PUT',
          body: { itemLabel: itemDefinition.label, sortOrder: itemDefinition.sortOrder, enabled: itemDefinition.enabled, version: item.version },
        })
        items = [...items.filter(candidate => candidate.id !== item.id), item]
      }
    }
  }
}

async function verifyAudit(session) {
  const rows = await session.request('/audit/events?limit=200')
  const requiredTargets = ['ORGANIZATION', 'USER', 'ROLE', 'PARAMETER', 'DICTIONARY_TYPE', 'DICTIONARY_ITEM']
  const missing = requiredTargets.filter(type => !rows.some(row => row.targetType === type))
  if (missing.length) throw new Error(`审计记录缺少以下业务对象：${missing.join('、')}`)
  return rows.length
}

async function cleanup(session) {
  const users = await session.request('/users')
  for (const user of users.filter(item => item.loginName.startsWith('accept.'))) {
    if (user.enabled) await session.request(`/users/${user.id}/enabled`, {
      method: 'PATCH', body: { enabled: false, version: user.version },
    })
  }

  const roles = await session.request('/roles')
  for (const role of roles.filter(item => item.roleCode.startsWith('ACCEPTANCE_') && !item.systemManaged)) {
    if (role.enabled) await session.request(`/roles/${role.id}`, {
      method: 'PUT', body: { roleName: role.roleName, enabled: false, version: role.version },
    })
  }

  const values = await session.request('/configuration/parameters')
  for (const definition of parameterDefinitions) {
    const value = values.find(item => item.parameterKey === definition.key
      && item.environment === definition.environment && item.organizationId === null)
    if (value) await session.request(`/configuration/parameters/${encodeURIComponent(definition.key)}`, {
      method: 'DELETE',
      body: { environment: value.environment, organizationId: null, version: value.version },
    })
  }

  const types = await session.request('/configuration/dictionaries')
  for (const type of types.filter(item => item.typeCode.startsWith('ACCEPT_'))) {
    const items = await session.request(`/configuration/dictionaries/${type.id}/items?includeDisabled=true`)
    for (const item of items) {
      await session.request(`/configuration/dictionary-items/${item.id}`, {
        method: 'DELETE', body: { version: item.version },
      })
    }
    const latestTypes = await session.request('/configuration/dictionaries')
    const latest = latestTypes.find(candidate => candidate.id === type.id)
    if (latest) await session.request(`/configuration/dictionaries/${latest.id}`, {
      method: 'DELETE', body: { version: latest.version },
    })
  }

  const organizations = await session.request('/organizations')
  const cleanupOrder = [...organizationDefinitions].reverse().map(definition => definition.code)
  for (const code of cleanupOrder) {
    const organization = organizations.find(item => item.organizationCode === code)
    if (organization?.enabled) await session.request(`/organizations/${organization.id}/enabled`, {
      method: 'PATCH', body: { enabled: false, version: organization.version },
    })
  }
}

async function main() {
  requireSecrets()
  const session = new ApiSession(BASE_URL)
  const currentUser = await session.login(ADMIN_LOGIN, ADMIN_PASSWORD)
  if (currentUser.mustChangePassword) throw new Error('管理账号仍要求首次修改密码，请先在管理端完成改密')

  if (CLEANUP) {
    await cleanup(session)
    console.log('验收用户已注销、角色已停用、参数已恢复为未配置、未被使用的字典已删除、机构已撤销；操作记录继续保留。')
    return
  }

  const organizations = await ensureOrganizations(session)
  const roles = await ensureRoles(session)
  const users = await ensureUsers(session, organizations, roles)
  await ensureParameters(session)
  await ensureDictionaries(session)
  const auditCount = await verifyAudit(session)

  console.log('业务验收数据准备完成。')
  console.log(`机构：${organizationDefinitions.length} 个；角色：${roleDefinitions.length} 个；用户：${users.size} 个。`)
  console.log(`参数场景：${parameterDefinitions.length} 项；字典类型：${dictionaryDefinitions.length} 个；当前可见审计：${auditCount} 条。`)
  console.log(`验收用户：${userDefinitions.map(user => user.login).join('、')}（临时密码来自 PLATFORM_ACCEPTANCE_TEMP_PASSWORD）。`)
}

main().catch(error => {
  console.error(error instanceof Error ? error.message : error)
  process.exitCode = 1
})
