/** Returns whether an external value is a non-null object with string keys. */
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** Returns whether every member of an external array is a string. */
export function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every(item => typeof item === 'string')
}

/** Returns whether every member of an external array is a finite number. */
export function isNumberArray(value: unknown): value is number[] {
  return Array.isArray(value) && value.every(item => typeof item === 'number' && Number.isFinite(item))
}

/** 校验新密码原值，不裁剪或截断；与后端 BCrypt 的 UTF-8 72 字节上限保持一致。 */
export function isValidPasswordSize(password: string): boolean {
  return password.trim().length > 0 && password.length >= 9 && new TextEncoder().encode(password).length <= 72
}
