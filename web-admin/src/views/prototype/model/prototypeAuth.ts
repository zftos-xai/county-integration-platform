/** 原型支持的演示视角。 */
export type PrototypeRole = 'manager' | 'operator'

/** 只存在于原型内存中的演示会话。 */
export type PrototypeSession = {
  loginName: string
  displayName: string
  role: PrototypeRole
}

/**
 * 创建不具备认证含义的原型视角。
 * 原型不接受账号密码，避免把硬编码演示凭据误解为可用的登录设计。
 */
export function createPrototypeSession(role: PrototypeRole): PrototypeSession {
  return role === 'manager'
    ? { loginName: 'prototype-manager', displayName: '管理视角', role }
    : { loginName: 'prototype-operator', displayName: '运维视角', role }
}
