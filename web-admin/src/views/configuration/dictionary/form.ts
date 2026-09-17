import type {
  CreateDictionaryItemInput, CreateDictionaryTypeInput, DictionaryItem, DictionaryType,
  UpdateDictionaryItemInput, UpdateDictionaryTypeInput,
} from '@/api/system/configuration'

/** 字典类型编辑表单。 */
export type DictionaryTypeForm = { typeCode: string; typeName: string; description: string; enabled: boolean; version: string }
/** 字典项编辑表单。 */
export type DictionaryItemForm = { itemCode: string; itemLabel: string; sortOrder: string; enabled: boolean; version: string }

/** 创建空白字典类型草稿。 */
export function emptyDictionaryTypeForm(): DictionaryTypeForm {
  return { typeCode: '', typeName: '', description: '', enabled: true, version: '' }
}

/** 将字典类型快照转换为编辑草稿。 */
export function dictionaryTypeToForm(type: DictionaryType): DictionaryTypeForm {
  return { typeCode: type.typeCode, typeName: type.typeName, description: type.description, enabled: type.enabled, version: type.version }
}

/** 创建空白字典项草稿。 */
export function emptyDictionaryItemForm(): DictionaryItemForm {
  return { itemCode: '', itemLabel: '', sortOrder: '0', enabled: true, version: '' }
}

/** 将字典项快照转换为编辑草稿。 */
export function dictionaryItemToForm(item: DictionaryItem): DictionaryItemForm {
  return { itemCode: item.itemCode, itemLabel: item.itemLabel, sortOrder: String(item.sortOrder), enabled: item.enabled, version: item.version }
}

/** 校验字典类型表单。 */
export function validateDictionaryTypeForm(form: DictionaryTypeForm, isCreating: boolean): string | null {
  if (isCreating && !/^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(form.typeCode.trim())) return '类型代码需以字母开头，只能包含字母、数字、点、横线或下划线'
  if (!form.typeName.trim()) return '请输入类型名称'
  if (form.typeName.trim().length > 100) return '类型名称不能超过 100 个字符'
  if (!form.description.trim()) return '请输入用途说明'
  if (form.description.trim().length > 500) return '用途说明不能超过 500 个字符'
  if (!isCreating && !form.version) return '缺少并发版本，请刷新后重试'
  return null
}

/** 校验字典项表单。 */
export function validateDictionaryItemForm(form: DictionaryItemForm, isCreating: boolean): string | null {
  if (isCreating && !/^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(form.itemCode.trim())) return '字典项代码需以字母开头，只能包含字母、数字、点、横线或下划线'
  if (!form.itemLabel.trim()) return '请输入展示文本'
  if (form.itemLabel.trim().length > 200) return '展示文本不能超过 200 个字符'
  if (!/^\d+$/.test(form.sortOrder) || Number(form.sortOrder) > 999999) return '排序值需为 0—999999 的整数'
  if (!isCreating && !form.version) return '缺少并发版本，请刷新后重试'
  return null
}

/** 构造创建字典类型请求。 */
export function toCreateDictionaryTypeInput(form: DictionaryTypeForm): CreateDictionaryTypeInput {
  return { typeCode: form.typeCode.trim(), typeName: form.typeName.trim(), description: form.description.trim() }
}

/** 构造更新字典类型请求。 */
export function toUpdateDictionaryTypeInput(form: DictionaryTypeForm): UpdateDictionaryTypeInput {
  return { typeName: form.typeName.trim(), description: form.description.trim(), enabled: form.enabled, version: form.version }
}

/** 构造创建字典项请求。 */
export function toCreateDictionaryItemInput(form: DictionaryItemForm): CreateDictionaryItemInput {
  return { itemCode: form.itemCode.trim(), itemLabel: form.itemLabel.trim(), sortOrder: Number(form.sortOrder) }
}

/** 构造更新字典项请求。 */
export function toUpdateDictionaryItemInput(form: DictionaryItemForm): UpdateDictionaryItemInput {
  return { itemLabel: form.itemLabel.trim(), sortOrder: Number(form.sortOrder), enabled: form.enabled, version: form.version }
}
