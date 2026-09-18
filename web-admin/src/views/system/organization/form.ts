import type { CreateOrganizationInput, Organization, UpdateOrganizationInput } from '@/api/system/organization'

/** 机构编辑器使用的字符串表单状态。 */
export type OrganizationForm = {
  organizationCode: string
  organizationName: string
  organizationType: string
  parentId: string
  validFrom: string
  validTo: string
  version: string
}

/** 创建空白机构表单。 */
export function emptyOrganizationForm(): OrganizationForm {
  return {
    organizationCode: '',
    organizationName: '',
    organizationType: '',
    parentId: '',
    validFrom: '',
    validTo: '',
    version: '',
  }
}

/** 将机构 API 模型映射为可编辑表单，并把 UTC 时间转换为本地输入值。 */
export function organizationToForm(organization: Organization): OrganizationForm {
  return {
    organizationCode: organization.organizationCode,
    organizationName: organization.organizationName,
    organizationType: organization.organizationType,
    parentId: organization.parentId === null ? '' : String(organization.parentId),
    validFrom: utcToLocalInput(organization.validFrom),
    validTo: utcToLocalInput(organization.validTo),
    version: organization.version,
  }
}

/** 校验机构表单中前端可以确定的格式与时间关系。 */
export function validateOrganizationForm(form: OrganizationForm, creating: boolean): string | null {
  const code = form.organizationCode.trim()
  const name = form.organizationName.trim()
  const type = form.organizationType.trim()
  if (creating && !code) return '请输入机构编码'
  if (creating && !/^[A-Za-z0-9._-]+$/.test(code)) return '机构编码仅可使用字母、数字、点、下划线和短横线'
  if (code.length > 64) return '机构编码不能超过 64 个字符'
  if (!name) return '请输入机构名称'
  if (name.length > 200) return '机构名称不能超过 200 个字符'
  if (!type) return '请输入已确认的机构类型代码'
  if (type.length > 32) return '机构类型代码不能超过 32 个字符'
  if (form.parentId && (!Number.isInteger(Number(form.parentId)) || Number(form.parentId) <= 0)) return '请选择有效的上级机构'
  if (form.validFrom && form.validTo && new Date(form.validFrom).getTime() > new Date(form.validTo).getTime()) {
    return '有效期开始时间不能晚于结束时间'
  }
  return null
}

/** 将创建表单转换为 API 输入模型。 */
export function toCreateInput(form: OrganizationForm): CreateOrganizationInput {
  return {
    organizationCode: form.organizationCode.trim(),
    organizationName: form.organizationName.trim(),
    organizationType: form.organizationType.trim(),
    parentId: form.parentId ? Number(form.parentId) : null,
    validFrom: localInputToOffset(form.validFrom),
    validTo: localInputToOffset(form.validTo),
  }
}

/** 将编辑表单转换为包含 rowversion 的 API 输入模型。 */
export function toUpdateInput(form: OrganizationForm): UpdateOrganizationInput {
  const { organizationCode: _organizationCode, ...input } = toCreateInput(form)
  return { ...input, version: form.version }
}

/** 将服务端时间转换为当前浏览器时区的 datetime-local 值。 */
export function utcToLocalInput(value: string | null): string {
  if (!value) return ''
  const instant = new Date(value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`)
  if (Number.isNaN(instant.getTime())) return ''
  const offset = instant.getTimezoneOffset() * 60_000
  return new Date(instant.getTime() - offset).toISOString().slice(0, 16)
}

/** 将 datetime-local 值转换为带时区含义的 ISO 时间；空值保持为 null。 */
export function localInputToOffset(value: string): string | null {
  if (!value) return null
  return new Date(value).toISOString()
}
