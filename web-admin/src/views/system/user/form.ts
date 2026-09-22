import type { CreateUserInput, ManagedUser, UpdateUserInput } from '@/api/system/user'
import { isValidPasswordSize } from '@/utils/validation'

/** 用户基本资料编辑器使用的字符串表单状态。 */
export type UserForm = {
  loginName: string
  displayName: string
  primaryOrganizationId: string
  temporaryPassword: string
  version: string
}

/** 创建空白用户表单。 */
export function emptyUserForm(): UserForm {
  return { loginName: '', displayName: '', primaryOrganizationId: '', temporaryPassword: '', version: '' }
}

/** 将用户 API 模型映射为基本资料表单；临时密码绝不回填。 */
export function userToForm(user: ManagedUser): UserForm {
  return {
    loginName: user.loginName,
    displayName: user.displayName,
    primaryOrganizationId: String(user.primaryOrganizationId),
    temporaryPassword: '',
    version: user.version,
  }
}

/** 校验用户基本资料，并在创建场景继续校验临时密码。 */
export function validateUserForm(form: UserForm, creating: boolean): string | null {
  const loginName = form.loginName.trim()
  const displayName = form.displayName.trim()
  if (creating && !loginName) return '请输入登录名'
  if (creating && !/^[A-Za-z0-9._-]{3,64}$/.test(loginName)) {
    return '登录名需为 3—64 位字母、数字、点、下划线或短横线'
  }
  if (!displayName) return '请输入用户姓名'
  if (displayName.length > 100) return '用户姓名不能超过 100 个字符'
  const organizationId = Number(form.primaryOrganizationId)
  if (!Number.isInteger(organizationId) || organizationId <= 0) return '请选择主要机构'
  if (creating) return validateTemporaryPassword(form.temporaryPassword, loginName)
  return null
}

/** 校验临时密码长度及不得包含登录名的项目规则。 */
export function validateTemporaryPassword(password: string, loginName: string): string | null {
  if (!isValidPasswordSize(password)) return '临时密码至少 9 个字符，UTF-8 编码不能超过 72 字节'
  if (loginName && password.toLowerCase().includes(loginName.toLowerCase())) return '临时密码不能包含登录名'
  return null
}

/** 将创建表单转换为 API 输入，登录名按接口定义统一转为小写。 */
export function toCreateUserInput(form: UserForm): CreateUserInput {
  return {
    loginName: form.loginName.trim().toLowerCase(),
    displayName: form.displayName.trim(),
    primaryOrganizationId: Number(form.primaryOrganizationId),
    temporaryPassword: form.temporaryPassword,
  }
}

/** 将编辑表单转换为包含 rowversion 的 API 输入。 */
export function toUpdateUserInput(form: UserForm): UpdateUserInput {
  return {
    displayName: form.displayName.trim(),
    primaryOrganizationId: Number(form.primaryOrganizationId),
    version: form.version,
  }
}
