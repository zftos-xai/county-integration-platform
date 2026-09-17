import assert from 'node:assert/strict'
import test from 'node:test'
import { apiRequest, ApiClientError, clearCsrfToken } from '../src/utils/request.ts'

test('写请求先取得CSRF令牌并携带会话凭据', async () => {
  clearCsrfToken()
  const calls = []
  globalThis.fetch = async (url, init = {}) => {
    calls.push({ url, init })
    if (url === '/api/v1/session/csrf') return Response.json({ code: 'SUCCESS', message: 'ok', data: { headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'token-1' } })
    return Response.json({ code: 'SUCCESS', message: 'ok', data: { loginName: 'admin' } })
  }

  const result = await apiRequest('/session/login', { method: 'POST', body: JSON.stringify({ loginName: 'admin', password: 'secret' }) })

  assert.equal(result.loginName, 'admin')
  assert.equal(calls.length, 2)
  assert.equal(calls[1].init.credentials, 'same-origin')
  assert.equal(calls[1].init.headers.get('X-XSRF-TOKEN'), 'token-1')
})

test('API错误保留安全消息与请求编号', async () => {
  globalThis.fetch = async () => Response.json(
    { code: 'RESOURCE_CONFLICT', message: '资源状态已变更，请刷新后重试', requestId: 'req-001' },
    { status: 409 },
  )

  await assert.rejects(
    () => apiRequest('/organizations/1'),
    error => error instanceof ApiClientError && error.status === 409 && error.requestId === 'req-001',
  )
})

test('网络失败转换为不泄露内部细节的错误', async () => {
  globalThis.fetch = async () => { throw new Error('socket details') }
  await assert.rejects(
    () => apiRequest('/session/current'),
    error => error instanceof ApiClientError && error.code === 'NETWORK_ERROR' && error.message === '无法连接后台服务',
  )
})

test('非平台CSRF响应归类为安全会话不可用并保留请求编号', async () => {
  clearCsrfToken()
  globalThis.fetch = async () => new Response('404 page not found', {
    status: 404,
    headers: { 'X-Request-Id': 'proxy-404' },
  })
  await assert.rejects(
    () => apiRequest('/session/login', { method: 'POST', body: '{}' }),
    error => error instanceof ApiClientError
      && error.code === 'CSRF_UNAVAILABLE'
      && error.message === '无法建立安全会话，请确认后台服务可用'
      && error.requestId === 'proxy-404',
  )
})

test('成功状态缺少统一响应数据时拒绝作为业务成功', async () => {
  globalThis.fetch = async () => Response.json({ code: 'SUCCESS', message: 'ok' })
  await assert.rejects(
    () => apiRequest('/organizations'),
    error => error instanceof ApiClientError && error.code === 'INVALID_RESPONSE',
  )
})

test('业务数据未通过运行时合同校验时拒绝进入页面状态', async () => {
  globalThis.fetch = async () => Response.json({ code: 'SUCCESS', message: 'ok', data: { id: 'wrong-type' } })
  await assert.rejects(
    () => apiRequest('/organizations/1', {}, value => typeof value === 'object' && value !== null && value.id === 1),
    error => error instanceof ApiClientError && error.code === 'INVALID_RESPONSE',
  )
})

test('请求超过共享超时后返回受控超时错误', async () => {
  globalThis.fetch = async (_url, init) => new Promise((_resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), { once: true })
  })
  await assert.rejects(
    () => apiRequest('/session/current', { timeoutMs: 5 }),
    error => error instanceof ApiClientError && error.code === 'REQUEST_TIMEOUT',
  )
})
