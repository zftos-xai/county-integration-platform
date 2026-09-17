/** 平台成功响应的统一信封结构。 */
export type ApiEnvelope<T> = {
  code: string
  message: string
  data: T
}

/** 平台错误响应中前端允许读取的诊断字段。 */
export type ApiErrorBody = {
  code?: string
  message?: string
  requestId?: string | null
}

/** Validates unknown response data before it crosses into typed application code. */
export type ApiDataValidator<T> = (value: unknown) => value is T

/** Request options supported by the shared HTTP client. */
export type HttpRequestOptions = RequestInit & {
  timeoutMs?: number
}

type CsrfToken = {
  headerName: string
  parameterName: string
  token: string
}

/** 可供页面按状态码、业务码和请求编号分类处理的平台请求错误。 */
export class ApiClientError extends Error {
  readonly code: string
  readonly requestId?: string
  readonly status: number

  constructor(code: string, message: string, status: number, requestId?: string) {
    super(message)
    this.name = 'ApiClientError'
    this.code = code
    this.requestId = requestId
    this.status = status
  }
}

let csrfToken: CsrfToken | null = null
let csrfRequest: Promise<CsrfToken> | null = null
const DEFAULT_TIMEOUT_MS = 15_000

/** 为非平台错误响应提供不泄露内部实现的用户文案。 */
function fallbackMessage(status: number) {
  if (status === 400) return '请求内容不符合接口要求'
  if (status === 401) return '登录状态已失效，请重新登录'
  if (status === 403) return '当前账号无权执行该操作'
  if (status === 404) return '请求的数据不存在或已不可见'
  if (status === 409) return '数据已被其他操作更新，请刷新后重试'
  return '系统处理失败，请稍后重试'
}

/** 只解析明确声明为 JSON 的响应，避免把代理或网关 HTML 当作平台错误。 */
async function parseJson(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) return null
  try {
    return await response.json()
  } catch {
    return null
  }
}

/** Reads safe error fields from an untrusted response object. */
function parseErrorBody(value: unknown): ApiErrorBody | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return null
  const record = value as Record<string, unknown>
  return {
    code: typeof record.code === 'string' ? record.code : undefined,
    message: typeof record.message === 'string' ? record.message : undefined,
    requestId: typeof record.requestId === 'string' || record.requestId === null
      ? record.requestId
      : undefined,
  }
}

/**
 * Executes a same-origin request with a bounded lifetime.
 * An external abort remains distinguishable from a timeout so callers can ignore obsolete work.
 */
export async function httpRequest(path: string, options: HttpRequestOptions = {}): Promise<Response> {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, signal: externalSignal, ...init } = options
  const controller = new AbortController()
  const abortFromCaller = () => controller.abort(externalSignal?.reason)
  if (externalSignal?.aborted) abortFromCaller()
  else externalSignal?.addEventListener('abort', abortFromCaller, { once: true })
  const timeout = setTimeout(() => controller.abort('timeout'), timeoutMs)
  try {
    return await fetch(path, { ...init, credentials: 'same-origin', signal: controller.signal })
  } catch (error) {
    if (controller.signal.aborted) {
      if (externalSignal?.aborted) throw new ApiClientError('REQUEST_ABORTED', '请求已取消', 0)
      throw new ApiClientError('REQUEST_TIMEOUT', '请求超时，请稍后重试', 0)
    }
    throw error
  } finally {
    clearTimeout(timeout)
    externalSignal?.removeEventListener('abort', abortFromCaller)
  }
}

/** 获取并复用同一时刻的 CSRF 请求，令牌仅保存在当前页面内存中。 */
async function loadCsrfToken(): Promise<CsrfToken> {
  if (csrfToken) return csrfToken
  if (csrfRequest) return csrfRequest
  csrfRequest = (async () => {
    let response: Response
    try {
      response = await httpRequest('/api/v1/session/csrf', {
        headers: { Accept: 'application/json' },
      })
    } catch (error) {
      if (error instanceof ApiClientError) throw error
      throw new ApiClientError('NETWORK_ERROR', '无法连接后台服务', 0)
    }
    const payload = await parseJson(response)
    const error = parseErrorBody(payload)
    if (!response.ok || typeof payload !== 'object' || payload === null || !('data' in payload)) {
      throw new ApiClientError(
        error?.code ?? 'CSRF_UNAVAILABLE',
        error?.message ?? '无法建立安全会话，请确认后台服务可用',
        response.status,
        error?.requestId ?? response.headers.get('x-request-id') ?? undefined,
      )
    }
    const data = (payload as Record<string, unknown>).data
    if (!isCsrfToken(data)) throw invalidResponse(response)
    csrfToken = data
    return data
  })().finally(() => {
    csrfRequest = null
  })
  return csrfRequest
}

/** Returns whether an unknown response contains a usable CSRF token contract. */
function isCsrfToken(value: unknown): value is CsrfToken {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const record = value as Record<string, unknown>
  return typeof record.headerName === 'string'
    && typeof record.parameterName === 'string'
    && typeof record.token === 'string'
}

/** Creates a controlled error for a successful HTTP response that violates the API envelope. */
function invalidResponse(response: Response): ApiClientError {
  return new ApiClientError(
    'INVALID_RESPONSE',
    '后台返回的数据格式不符合约定，请联系管理员',
    response.status,
    response.headers.get('x-request-id') ?? undefined,
  )
}

/** 清除内存中的 CSRF 令牌及获取任务，不触碰浏览器会话 Cookie。 */
export function clearCsrfToken() {
  csrfToken = null
  csrfRequest = null
}

/**
 * 执行同源平台 API 请求，并为非只读请求自动附加服务端签发的 CSRF 令牌。
 * 401 会清除令牌缓存，避免在后续新会话中复用失效令牌。
 */
export async function apiRequest<T>(
  path: string,
  init: HttpRequestOptions = {},
  validateData?: ApiDataValidator<T>,
): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await loadCsrfToken()
    headers.set(token.headerName, token.token)
  }

  let response: Response
  try {
    response = await httpRequest(`/api/v1${path}`, {
      ...init,
      method,
      headers,
    })
  } catch (error) {
    if (error instanceof ApiClientError) throw error
    throw new ApiClientError('NETWORK_ERROR', '无法连接后台服务', 0)
  }

  const payload = await parseJson(response)
  if (!response.ok) {
    if (response.status === 401) clearCsrfToken()
    const error = parseErrorBody(payload)
    throw new ApiClientError(
      error?.code ?? `HTTP_${response.status}`,
      error?.message ?? fallbackMessage(response.status),
      response.status,
      error?.requestId ?? response.headers.get('x-request-id') ?? undefined,
    )
  }
  if (typeof payload !== 'object' || payload === null || !('data' in payload)) {
    throw invalidResponse(response)
  }
  const data = (payload as Record<string, unknown>).data
  if (validateData && !validateData(data)) throw invalidResponse(response)
  return data as T
}
