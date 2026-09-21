import { apiRequest } from '@/utils/request'
import { isRecord } from '@/utils/validation'
import {
  configurationWriteRequest, dictionaryItemPath, dictionaryItemsPath, dictionaryTypePath,
  externalEndpointAuthenticationPath, externalEndpointPath, externalEndpointVerificationPath,
  externalEndpointsPath, externalSystemPath,
  parameterValuePath,
} from './configurationApiPaths'

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

/** 当前管理员可见的按适用范围保存的参数值。 */
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

/** 创建或更新按适用范围保存的参数值的请求。 */
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

/** 平台登记的外部系统。 */
export type ExternalSystem = {
  id: number
  systemCode: string
  systemName: string
  description: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  version: string
}

/** 外部系统在某一环境和机构下的服务地址；凭证只返回是否已配置。 */
export type ExternalEndpoint = {
  id: number
  externalSystemId: number
  environment: ParameterEnvironment
  organizationId: number | null
  organizationCode: string | null
  baseUrl: string
  connectTimeoutMs: number
  readTimeoutMs: number
  credentialConfigured: boolean
  enabled: boolean
  createdAt: string
  updatedAt: string
  version: string
  sourceOrganizationId: string | null
  sourceOrganizationName: string | null
  verificationStatus: 'NOT_VERIFIED' | 'VERIFIED' | 'FAILED' | 'RESULT_UNKNOWN'
  verifiedAt: string | null
  verificationFailureSummary: string | null
}

/** 创建外部系统的请求。 */
export type CreateExternalSystemInput = Pick<ExternalSystem, 'systemCode' | 'systemName' | 'description'>
/** 更新外部系统的请求。 */
export type UpdateExternalSystemInput = Pick<ExternalSystem, 'systemName' | 'description' | 'enabled' | 'version'>
/** 管理端写入的机构HIS接口认证信息。 */
export type ExternalEndpointAuthenticationInput = {
  vendorCode: string
  username: string
  password: string
  authorizationCode: string
}
/** 仅在管理员主动查看时返回的机构HIS接入信息。 */
export type ExternalEndpointAuthentication = ExternalEndpointAuthenticationInput
/** 创建机构服务地址的请求。 */
export type CreateExternalEndpointInput = Pick<ExternalEndpoint, 'environment' | 'organizationId' | 'baseUrl' | 'connectTimeoutMs' | 'readTimeoutMs' | 'enabled'> & {
  authentication: ExternalEndpointAuthenticationInput
}
/** 更新机构接口配置的请求；认证对象为空表示保留已配置内容。 */
export type UpdateExternalEndpointInput = Pick<ExternalEndpoint, 'environment' | 'organizationId' | 'baseUrl' | 'connectTimeoutMs' | 'readTimeoutMs' | 'enabled' | 'version'> & {
  authentication: ExternalEndpointAuthenticationInput | null
}

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

/** Validates one external system returned by the management API. */
export function isExternalSystem(value: unknown): value is ExternalSystem {
  if (!isRecord(value)) return false
  return typeof value.id === 'number' && typeof value.systemCode === 'string'
    && typeof value.systemName === 'string' && typeof value.description === 'string'
    && typeof value.enabled === 'boolean' && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string' && typeof value.version === 'string'
}

/** Validates one organization-scoped external endpoint returned by the management API. */
export function isExternalEndpoint(value: unknown): value is ExternalEndpoint {
  if (!isRecord(value)) return false
  return typeof value.id === 'number' && typeof value.externalSystemId === 'number'
    && environments.includes(value.environment as ParameterEnvironment)
    && isNullableNumber(value.organizationId) && isNullableString(value.organizationCode)
    && typeof value.baseUrl === 'string' && Number.isInteger(value.connectTimeoutMs)
    && Number.isInteger(value.readTimeoutMs) && typeof value.credentialConfigured === 'boolean'
    && typeof value.enabled === 'boolean' && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string' && typeof value.version === 'string'
    && isNullableString(value.sourceOrganizationId)
    && isNullableString(value.sourceOrganizationName) && ['NOT_VERIFIED', 'VERIFIED', 'FAILED', 'RESULT_UNKNOWN'].includes(value.verificationStatus as string)
    && isNullableString(value.verifiedAt) && isNullableString(value.verificationFailureSummary)
}

/** 校验按需查看接口返回的机构HIS接入信息。 */
export function isExternalEndpointAuthentication(value: unknown): value is ExternalEndpointAuthentication {
  if (!isRecord(value)) return false
  return typeof value.vendorCode === 'string' && typeof value.username === 'string'
    && typeof value.password === 'string' && typeof value.authorizationCode === 'string'
}

const isParameterDefinitionList = (value: unknown): value is ParameterDefinition[] => Array.isArray(value) && value.every(isParameterDefinition)
const isParameterValueList = (value: unknown): value is ParameterValue[] => Array.isArray(value) && value.every(isParameterValue)
const isDictionaryTypeList = (value: unknown): value is DictionaryType[] => Array.isArray(value) && value.every(isDictionaryType)
const isDictionaryItemList = (value: unknown): value is DictionaryItem[] => Array.isArray(value) && value.every(isDictionaryItem)
const isExternalSystemList = (value: unknown): value is ExternalSystem[] => Array.isArray(value) && value.every(isExternalSystem)
const isExternalEndpointList = (value: unknown): value is ExternalEndpoint[] => Array.isArray(value) && value.every(isExternalEndpoint)

/** 查询代码注册的参数定义。 */
export function listParameterDefinitions(signal?: AbortSignal) {
  return apiRequest<ParameterDefinition[]>('/configuration/parameter-definitions', { signal }, isParameterDefinitionList)
}

/** 查询当前管理员可见的参数值。 */
export function listParameterValues(signal?: AbortSignal) {
  return apiRequest<ParameterValue[]>('/configuration/parameters', { signal }, isParameterValueList)
}

/** 创建或并发更新一个按适用范围保存的参数值。 */
export function upsertParameterValue(key: string, input: UpsertParameterInput) {
  return apiRequest<ParameterValue>(parameterValuePath(key), configurationWriteRequest('PUT', input), isParameterValue)
}

/** 删除一个适用范围内的参数配置，使该范围恢复为尚未配置。 */
export function deleteParameterValue(key: string, input: Pick<UpsertParameterInput, 'environment' | 'organizationId' | 'version'>) {
  return apiRequest<void>(parameterValuePath(key), configurationWriteRequest('DELETE', input))
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

/** 删除一个不含字典项的字典类型。 */
export function deleteDictionaryType(id: number, version: string) {
  return apiRequest<void>(dictionaryTypePath(id), configurationWriteRequest('DELETE', { version }))
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

/** 删除一个尚未被业务数据引用的字典项。 */
export function deleteDictionaryItem(id: number, version: string) {
  return apiRequest<void>(dictionaryItemPath(id), configurationWriteRequest('DELETE', { version }))
}

/** 查询平台登记的外部系统。 */
export function listExternalSystems(signal?: AbortSignal) {
  return apiRequest<ExternalSystem[]>('/configuration/external-systems', { signal }, isExternalSystemList)
}

/** 新增一个已确认的外部系统。 */
export function createExternalSystem(input: CreateExternalSystemInput) {
  return apiRequest<ExternalSystem>('/configuration/external-systems', configurationWriteRequest('POST', input), isExternalSystem)
}

/** 使用并发版本更新外部系统。 */
export function updateExternalSystem(id: number, input: UpdateExternalSystemInput) {
  return apiRequest<ExternalSystem>(externalSystemPath(id), configurationWriteRequest('PUT', input), isExternalSystem)
}

/** 查询当前管理员可见的机构服务地址。 */
export function listExternalEndpoints(systemId: number, signal?: AbortSignal) {
  return apiRequest<ExternalEndpoint[]>(externalEndpointsPath(systemId), { signal }, isExternalEndpointList)
}

/** 按需读取一个机构已保存的HIS接入信息；调用结果不得写入本地存储。 */
export function getExternalEndpointAuthentication(endpointId: number, signal?: AbortSignal) {
  return apiRequest<ExternalEndpointAuthentication>(
    externalEndpointAuthenticationPath(endpointId),
    { signal, cache: 'no-store' },
    isExternalEndpointAuthentication,
  )
}

/** 新增一个默认停用的机构服务地址。 */
export function createExternalEndpoint(systemId: number, input: CreateExternalEndpointInput) {
  return apiRequest<ExternalEndpoint>(
    externalEndpointsPath(systemId), configurationWriteRequest('POST', input), isExternalEndpoint,
  )
}

/** 更新或启停机构服务地址；认证对象为空时保留当前配置。 */
export function updateExternalEndpoint(id: number, input: UpdateExternalEndpointInput) {
  return apiRequest<ExternalEndpoint>(
    externalEndpointPath(id), configurationWriteRequest('PUT', input), isExternalEndpoint,
  )
}

/** 调用100-008确认当前保存的基层HIS配置，并写回唯一的来源机构。 */
export function verifyExternalEndpoint(id: number) {
  return apiRequest<ExternalEndpoint>(
    externalEndpointVerificationPath(id), configurationWriteRequest('POST', {}), isExternalEndpoint,
  )
}
