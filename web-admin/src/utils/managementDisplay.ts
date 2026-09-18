/** 管理页面中审计对象代码对应的中文名称。 */
export const AUDIT_TARGET_LABELS: Readonly<Record<string, string>> = {
  USER: '用户',
  ROLE: '角色',
  ORGANIZATION: '机构',
  PARAMETER: '参数配置',
  DICTIONARY_TYPE: '字典类型',
  DICTIONARY_ITEM: '字典项',
  SESSION: '登录',
  EXTERNAL_SYSTEM: '外部系统',
  EXTERNAL_ENDPOINT: '外部系统接口',
}

/** 管理页面中审计操作代码对应的中文说明。 */
export const AUDIT_ACTION_LABELS: Readonly<Record<string, string>> = {
  USER_CREATED: '新增用户',
  USER_UPDATED: '修改用户资料',
  USER_ENABLED: '启用用户',
  USER_DISABLED: '注销用户账号',
  USER_PASSWORD_RESET: '重置临时密码',
  USER_ROLES_REPLACED: '修改用户角色',
  USER_ORGANIZATION_SCOPES_REPLACED: '修改用户可查看机构',
  ROLE_CREATED: '新增角色',
  ROLE_UPDATED: '修改角色',
  ROLE_PERMISSIONS_REPLACED: '修改角色权限',
  ROLE_DELETED: '删除角色',
  ORGANIZATION_CREATED: '新增机构',
  ORGANIZATION_UPDATED: '修改机构资料',
  ORGANIZATION_ENABLED: '启用机构',
  ORGANIZATION_DISABLED: '撤销机构',
  PARAMETER_CREATED: '新增参数配置',
  PARAMETER_UPDATED: '修改参数配置',
  PARAMETER_DELETED: '删除参数配置',
  DICTIONARY_TYPE_CREATED: '新增字典类型',
  DICTIONARY_TYPE_UPDATED: '修改字典类型',
  DICTIONARY_TYPE_DELETED: '删除字典类型',
  DICTIONARY_ITEM_CREATED: '新增字典项',
  DICTIONARY_ITEM_UPDATED: '修改字典项',
  DICTIONARY_ITEM_DELETED: '删除字典项',
  LOGIN_FAILED: '登录失败',
  EXTERNAL_SYSTEM_CREATED: '新增外部系统',
  EXTERNAL_SYSTEM_UPDATED: '修改外部系统',
  EXTERNAL_ENDPOINT_CREATED: '新增外部系统接口',
  EXTERNAL_ENDPOINT_UPDATED: '修改外部系统接口',
}

/** 返回审计对象的中文名称；未知代码保留原值，避免隐藏后端新增类型。 */
export function auditTargetLabel(code: string) {
  return AUDIT_TARGET_LABELS[code] ?? code
}

/** 返回审计操作的中文说明；未知代码保留原值，避免误译。 */
export function auditActionLabel(code: string) {
  return AUDIT_ACTION_LABELS[code] ?? code
}

/** 将后端 UTC 时间转换为浏览器所在时区的可读时间。 */
export function formatLocalDateTime(value: string) {
  const normalized = value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`
  const instant = new Date(normalized)
  if (Number.isNaN(instant.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(instant)
}

/** 将部署环境代码转换为业务人员可识别的名称。 */
export function environmentLabel(code: string) {
  return ({ DEVELOPMENT: '开发环境', TEST: '测试环境', PRODUCTION: '生产环境' } as Readonly<Record<string, string>>)[code] ?? code
}

/** 将参数值类型代码转换为填写方式说明。 */
export function parameterValueTypeLabel(code: string) {
  return ({ STRING: '文本', INTEGER: '整数', DECIMAL: '小数', BOOLEAN: '是/否' } as Readonly<Record<string, string>>)[code] ?? code
}

/** 将已登记的机构类型代码转换为业务人员可直接理解的名称。 */
export function organizationTypeLabel(code: string) {
  return ({
    COUNTY: '县级机构',
    HOSPITAL: '医院',
    PRIMARY_CARE: '基层医疗机构',
    CLINIC: '诊所',
  } as Readonly<Record<string, string>>)[code] ?? `未登记类型（${code}）`
}
