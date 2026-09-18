import type {
  ParameterDefinition, ParameterEnvironment, ParameterValue, UpsertParameterInput,
} from '@/api/system/configuration'

/** 参数编辑器使用的适用范围和值的草稿。 */
export type ParameterForm = {
  environment: ParameterEnvironment
  organizationId: number | null
  value: string
  enabled: boolean
  version: string
}

/** 基于参数定义和可选已保存值创建独立表单草稿。 */
export function parameterToForm(
  definition: ParameterDefinition,
  value: ParameterValue | null,
  primaryOrganizationId: number,
): ParameterForm {
  return {
    environment: value?.environment ?? definition.environments[0] ?? 'DEVELOPMENT',
    organizationId: value?.organizationId ?? (definition.organizationScoped ? primaryOrganizationId : null),
    value: definition.sensitive && value ? '' : value?.value ?? '',
    enabled: value?.enabled ?? true,
    version: value?.version ?? '',
  }
}

/** 按代码注册约束校验参数值并返回首个错误。 */
export function validateParameterForm(definition: ParameterDefinition, form: ParameterForm): string | null {
  if (!definition.environments.includes(form.environment)) return '所选环境不在该参数允许范围内'
  if (definition.organizationScoped && !form.organizationId) return '请选择参数所属机构'
  if (!definition.organizationScoped && form.organizationId !== null) return '平台级参数不能提交机构范围'
  if (!form.value) return definition.sensitive ? '请输入新的敏感参数值' : '请输入参数值'
  if (definition.maximumLength !== null && form.value.length > definition.maximumLength) {
    return `参数值不能超过 ${definition.maximumLength} 个字符`
  }
  if (definition.pattern && form.value && !new RegExp(definition.pattern).test(form.value)) return '参数值不符合注册格式'
  if (definition.valueType === 'BOOLEAN' && !['true', 'false'].includes(form.value)) return '布尔参数只能选择 true 或 false'
  if (['INTEGER', 'DECIMAL'].includes(definition.valueType) && form.value) {
    if (definition.valueType === 'INTEGER' && !/^-?\d+$/.test(form.value)) return '请输入整数'
    const number = Number(form.value)
    if (!Number.isFinite(number)) return '请输入有效数字'
    if (definition.minimumNumber !== null && number < definition.minimumNumber) return `参数值不能小于 ${definition.minimumNumber}`
    if (definition.maximumNumber !== null && number > definition.maximumNumber) return `参数值不能大于 ${definition.maximumNumber}`
  }
  return null
}

/** 将参数表单转换为写接口请求；首次创建不发送空版本。 */
export function toUpsertParameterInput(form: ParameterForm): UpsertParameterInput {
  return {
    environment: form.environment,
    organizationId: form.organizationId,
    value: form.value,
    enabled: form.enabled,
    ...(form.version ? { version: form.version } : {}),
  }
}
