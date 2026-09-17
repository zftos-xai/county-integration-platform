import type { CreateRoleInput, Role, UpdateRoleInput } from '@/api/system/role'

/** 角色基础资料编辑器使用的表单状态。 */
export type RoleForm = {
  roleCode: string
  roleName: string
  enabled: boolean
  version: string
}

/** 创建空白角色表单。 */
export function emptyRoleForm(): RoleForm {
  return { roleCode: '', roleName: '', enabled: true, version: '' }
}

/** 将角色 API 快照转换为独立编辑草稿。 */
export function roleToForm(role: Role): RoleForm {
  return {
    roleCode: role.roleCode,
    roleName: role.roleName,
    enabled: role.enabled,
    version: role.version,
  }
}

/** 校验角色表单并返回首个可执行错误。 */
export function validateRoleForm(form: RoleForm, isCreating: boolean): string | null {
  const roleCode = form.roleCode.trim().toUpperCase()
  const roleName = form.roleName.trim()
  if (isCreating && !/^[A-Z][A-Z0-9_]{2,63}$/.test(roleCode)) {
    return '角色代码需为 3—64 位大写字母、数字或下划线，并以字母开头'
  }
  if (isCreating && roleCode === 'PLATFORM_ADMIN') return '平台管理员是系统保护角色代码'
  if (!roleName) return '请输入角色名称'
  if (roleName.length > 100) return '角色名称不能超过 100 个字符'
  if (!isCreating && !form.version) return '缺少并发版本，请刷新后重试'
  return null
}

/** 将表单转换为创建角色请求。 */
export function toCreateRoleInput(form: RoleForm): CreateRoleInput {
  return { roleCode: form.roleCode.trim().toUpperCase(), roleName: form.roleName.trim() }
}

/** 将表单转换为包含 rowversion 的修改角色请求。 */
export function toUpdateRoleInput(form: RoleForm): UpdateRoleInput {
  return { roleName: form.roleName.trim(), enabled: form.enabled, version: form.version }
}
