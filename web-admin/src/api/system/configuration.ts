import { apiRequest } from '@/utils/request'
import { isRecord } from '@/utils/validation'
import {
  configurationWriteRequest, dictionaryItemPath, dictionaryItemsPath, dictionaryTypePath, parameterValuePath,
} from './configurationContract'

/** 平台参数支持的值类型。 */
export type ParameterValueType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
/** 平台参数支持的部署环境。 */
export type ParameterEnvironment = 'DEVELOPMENT' | 'TEST' | 'PRODUCTION'

/** 后端代码注册的参数定义。 */
export type ParameterDefinition = {
  key: string
  name: string
  valueType: ParameterValueType
  environments: ParameterEnvironment[]
  organizationScoped: boolean
  sensitive: boolean
  maximumLength: number | null
  minimumNumber: number | null
  maximumNumber: number | null
  pattern: string | null
}

/** 当前管理员可见的参数作用域值。 */
export type ParameterValue = {
  id: number
  parameterKey: string
  valueType: ParameterValueType
  environment: ParameterEnvironment
  organizationId: number | null
  organizationCode: string | null
  value: string
  configured: boolean
  enabled: boolean
  updatedAt: string
  version: string
}

/** 创建或更新参数作用域值的请求。 */
export type UpsertParameterInput = {
  environment: ParameterEnvironment
  organizationId: number | null
  value: string
  enabled: boolean
  version?: string
}

/** 平台字典类型。 */
export type DictionaryType = {
  id: number
  typeCode: string
  typeName: string
  description: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  version: string
}

/** 平台字典项。 */
export type DictionaryItem = {
  id: number
  dictionaryTypeId: number
  itemCode: string
  itemLabel: string
  sortOrder: number
  enabled: boolean
  createdAt: string
  updatedAt: string
  version: string
}

/** 创建字典类型的请求。 */
export type CreateDictionaryTypeInput = Pick<DictionaryType, 'typeCode' | 'typeName' | 'description'>
/** 更新字典类型的请求。 */
export type UpdateDictionaryTypeInput = Pick<DictionaryType, 'typeName' | 'description' | 'enabled' | 'version'>
/** 创建字典项的请求。 */
export type CreateDictionaryItemInput = Pick<DictionaryItem, 'itemCode' | 'itemLabel' | 'sortOrder'>
/** 更新字典项的请求。 */
export type UpdateDictionaryItemInput = Pick<DictionaryItem, 'itemLabel' | 'sortOrder' | 'enabled' | 'version'>

const valueTypes: ParameterValueType[] = ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN']
const environments: ParameterEnvironment[] = ['DEVELOPMENT', 'TEST', 'PRODUCTION']
const isNullableNumber = (value: unknown) => value === null || typeof value === 'number'
const isNullableString = (value: unknown) => value === null || typeof value === 'string'

/** Validates one code-registered parameter definition. */
export function isParameterDefinition(value: unknown): value is ParameterDefinition {
  if (!isRecord(value)) return false
  return typeof value.key === 'string' && typeof value.name === 'string'
    && valueTypes.includes(value.valueType as ParameterValueType)
    && Array.isArray(value.environments) && value.environments.every(item => environments.includes(item as ParameterEnvironment))
    && typeof value.organizationScoped === 'boolean' && typeof value.sensitive === 'boolean'
    && isNullableNumber(value.maximumLength) && isNullableNumber(value.minimumNumber)
    && isNullableNumber(value.maximumNumber) && isNullableString(value.pattern)
}

/** Validates one parameter value visible to the current administrator. */
export function isParameterValue(value: unknown): value is ParameterValue {
  if (!isRecord(value)) return false
  return typeof value.id === 'number' && typeof value.parameterKey === 'string'
    && valueTypes.includes(value.valueType as ParameterValueType)
    && environments.includes(value.environment as ParameterEnvironment)
    && isNullableNumber(value.organizationId) && isNullableString(value.organizationCode)
    && typeof value.value === 'string' && typeof value.configured === 'boolean'
    && typeof value.enabled === 'boolean' && typeof value.updatedAt === 'string' && typeof value.version === 'string'
}

/** Validates one platform dictionary type. */
export function isDictionaryType(value: unknown): value is DictionaryType {
  if (!isRecord(value)) return false
  return typeof value.id === 'number' && typeof value.typeCode === 'string' && typeof value.typeName === 'string'
    && typeof value.description === 'string' && typeof value.enabled === 'boolean'
    && typeof value.createdAt === 'string' && typeof value.updatedAt === 'string' && typeof value.version === 'string'
}

/** Validates one platform dictionary item. */
export function isDictionaryItem(value: unknown): value is DictionaryItem {
  if (!isRecord(value)) return false
  return typeof value.id === 'number' && typeof value.dictionaryTypeId === 'number'
    && typeof value.itemCode === 'string' && typeof value.itemLabel === 'string'
    && Number.isInteger(value.sortOrder) && typeof value.enabled === 'boolean'
    && typeof value.createdAt === 'string' && typeof value.updatedAt === 'string' && typeof value.version === 'string'
}

const isParameterDefinitionList = (value: unknown): value is ParameterDefinition[] => Array.isArray(value) && value.every(isParameterDefinition)
const isParameterValueList = (value: unknown): value is ParameterValue[] => Array.isArray(value) && value.every(isParameterValue)
const isDictionaryTypeList = (value: unknown): value is DictionaryType[] => Array.isArray(value) && value.every(isDictionaryType)
const isDictionaryItemList = (value: unknown): value is DictionaryItem[] => Array.isArray(value) && value.every(isDictionaryItem)

/** 查询代码注册的参数定义。 */
export function listParameterDefinitions(signal?: AbortSignal) {
  return apiRequest<ParameterDefinition[]>('/configuration/parameter-definitions', { signal }, isParameterDefinitionList)
}

/** 查询当前管理员可见的参数值。 */
export function listParameterValues(signal?: AbortSignal) {
  return apiRequest<ParameterValue[]>('/configuration/parameters', { signal }, isParameterValueList)
}

/** 创建或并发更新一个参数作用域值。 */
export function upsertParameterValue(key: string, input: UpsertParameterInput) {
  return apiRequest<ParameterValue>(parameterValuePath(key), configurationWriteRequest('PUT', input), isParameterValue)
}

/** 查询平台字典类型。 */
export function listDictionaryTypes(signal?: AbortSignal) {
  return apiRequest<DictionaryType[]>('/configuration/dictionaries', { signal }, isDictionaryTypeList)
}

/** 创建平台字典类型。 */
export function createDictionaryType(input: CreateDictionaryTypeInput) {
  return apiRequest<DictionaryType>('/configuration/dictionaries', configurationWriteRequest('POST', input), isDictionaryType)
}

/** 并发更新平台字典类型。 */
export function updateDictionaryType(id: number, input: UpdateDictionaryTypeInput) {
  return apiRequest<DictionaryType>(dictionaryTypePath(id), configurationWriteRequest('PUT', input), isDictionaryType)
}

/** 查询指定类型的字典项，管理页包含停用项。 */
export function listDictionaryItems(typeId: number, signal?: AbortSignal) {
  return apiRequest<DictionaryItem[]>(dictionaryItemsPath(typeId, true), { signal }, isDictionaryItemList)
}

/** 创建平台字典项。 */
export function createDictionaryItem(typeId: number, input: CreateDictionaryItemInput) {
  return apiRequest<DictionaryItem>(dictionaryItemsPath(typeId), configurationWriteRequest('POST', input), isDictionaryItem)
}

/** 并发更新平台字典项。 */
export function updateDictionaryItem(id: number, input: UpdateDictionaryItemInput) {
  return apiRequest<DictionaryItem>(dictionaryItemPath(id), configurationWriteRequest('PUT', input), isDictionaryItem)
}
