<!-- 参数配置页面：加载代码注册定义与当前用户可见的真实按适用范围保存的值。 -->
<script setup lang="ts">
import { AlertCircle, LoaderCircle, Plus, RefreshCw, Search, Settings2, Trash2 } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  deleteParameterValue, listParameterDefinitions, listParameterValues, upsertParameterValue,
} from '@/api/system/configuration'
import type { ParameterDefinition, ParameterEnvironment, ParameterValue } from '@/api/system/configuration'
import { listOrganizations } from '@/api/system/organization'
import type { Organization } from '@/api/system/organization'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError, asUncertainWriteError } from '@/utils/request'
import AdminPagination from '@/components/AdminPagination.vue'
import AuditAwareSuccess from '@/components/AuditAwareSuccess.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { environmentLabel, parameterValueTypeLabel, formatLocalDateTime } from '@/utils/managementDisplay'
import ParameterEditorDrawer from './components/ParameterEditorDrawer.vue'
import { parameterToForm, toUpsertParameterInput, validateParameterForm } from './form'

const router = useRouter()
const route = useRoute()
const definitions = ref<ParameterDefinition[]>([])
const values = ref<ParameterValue[]>([])
const organizations = ref<Organization[]>([])
const isLoading = ref(true)
const isRefreshing = ref(false)
const isSaving = ref(false)
const deletingValueId = ref<number | null>(null)
const error = ref<ApiClientError | null>(null)
const operationError = ref<ApiClientError | null>(null)
const notice = ref('')
const auditTarget = ref<{ targetType: string; targetId: string } | null>(null)
const query = ref(typeof route.query.query === 'string' ? route.query.query : '')
const environment = ref<'all' | ParameterEnvironment>('all')
const configurationStatus = ref<'all' | 'configured' | 'missing' | 'disabled'>(route.query.status === 'missing' ? 'missing' : 'all')
const selectedDefinition = ref<ParameterDefinition | null>(null)
const selectedValue = ref<ParameterValue | null>(null)
const form = ref(parameterToForm({ key: '', name: '', valueType: 'STRING', environments: ['DEVELOPMENT'], organizationScoped: false, sensitive: false, maximumLength: null, minimumNumber: null, maximumNumber: null, pattern: null }, null, 0))
const formError = ref('')
const editorSnapshot = ref('')
let controller: AbortController | null = null
let mounted = true

type ParameterRow = { definition: ParameterDefinition; value: ParameterValue | null; targetEnvironment: ParameterEnvironment }
type NewParameterScope = { definition: ParameterDefinition; environment: ParameterEnvironment; organizationId: number | null }

const canWrite = computed(() => hasPermission('configuration:write'))
const expandedRows = computed<ParameterRow[]>(() => {
  const expanded: ParameterRow[] = []
  definitions.value.forEach(definition => {
    const matches = values.value.filter(value => value.parameterKey === definition.key)
    matches.forEach(value => expanded.push({ definition, value, targetEnvironment: value.environment }))
    definition.environments.forEach(targetEnvironment => {
      const targetOrganizationId = definition.organizationScoped ? authState.user?.primaryOrganizationId : null
      const hasCurrentScope = matches.some(value => value.environment === targetEnvironment && value.organizationId === targetOrganizationId)
      if (!hasCurrentScope) expanded.push({ definition, value: null, targetEnvironment })
    })
  })
  return expanded
})
const rows = computed(() => expandedRows.value.filter(row => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  const textMatch = !keyword || [row.definition.name, row.definition.key, row.value?.organizationCode ?? ''].some(value => value.toLocaleLowerCase('zh-CN').includes(keyword))
  if (!textMatch || (environment.value !== 'all' && row.targetEnvironment !== environment.value)) return false
  if (configurationStatus.value === 'configured' && (!row.value?.configured || !row.value.enabled)) return false
  if (configurationStatus.value === 'missing' && row.value?.configured) return false
  if (configurationStatus.value === 'disabled' && (!row.value?.configured || row.value.enabled)) return false
  return true
}))
const configuredCount = computed(() => expandedRows.value.filter(row => row.value?.configured && row.value.enabled).length)
const missingCount = computed(() => expandedRows.value.filter(row => !row.value?.configured).length)
const newConfigurationScope = computed<NewParameterScope | null>(() => {
  for (const definition of definitions.value) {
    const organizationIds = definition.organizationScoped
      ? organizations.value.filter(item => item.enabled).map(item => item.id)
      : [null]
    for (const targetEnvironment of definition.environments) {
      for (const organizationId of organizationIds) {
        const exists = values.value.some(value => value.parameterKey === definition.key
          && value.environment === targetEnvironment && value.organizationId === organizationId)
        if (!exists) return { definition, environment: targetEnvironment, organizationId }
      }
    }
  }
  return null
})
const { page, pageSize, pagedRows } = useClientPagination(rows)

function asApiError(caught: unknown, message: string) {
  return caught instanceof ApiClientError ? caught : new ApiClientError('UNKNOWN_ERROR', message, 0)
}

async function handleUnauthorized(apiError: ApiClientError) {
  if (apiError.status !== 401) return false
  authState.user = null
  authState.isInitialized = true
  await router.replace({ path: '/login', query: { redirect: '/parameters' } })
  return true
}

async function loadPage(background = false) {
  controller?.abort()
  const current = new AbortController()
  controller = current
  if (background) isRefreshing.value = true
  else isLoading.value = true
  error.value = null
  try {
    let definitionRows: ParameterDefinition[]
    try {
      definitionRows = await listParameterDefinitions(current.signal)
    } catch (caught) {
      const apiError = asApiError(caught, '无法读取参数定义')
      throw new ApiClientError(apiError.code, `无法读取参数定义：${apiError.message}`, apiError.status, apiError.requestId)
    }
    let valueRows: ParameterValue[]
    try {
      valueRows = await listParameterValues(current.signal)
    } catch (caught) {
      const apiError = asApiError(caught, '无法读取参数值')
      throw new ApiClientError(apiError.code, `无法读取参数值：${apiError.message}`, apiError.status, apiError.requestId)
    }
    const needsOrganizationOptions = definitionRows.some(definition => definition.organizationScoped)
    const organizationRows = needsOrganizationOptions && hasPermission('organization:read')
      ? await listOrganizations(true, current.signal)
      : []
    if (!mounted || current.signal.aborted) return
    definitions.value = definitionRows
    values.value = valueRows
    organizations.value = organizationRows
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取参数配置')
    if (apiError.code !== 'REQUEST_ABORTED' && !await handleUnauthorized(apiError)) error.value = apiError
  } finally {
    if (controller === current && mounted) { isLoading.value = false; isRefreshing.value = false }
  }
}

function openEditor(definition: ParameterDefinition, value: ParameterValue | null, targetEnvironment: ParameterEnvironment) {
  selectedDefinition.value = definition
  selectedValue.value = value
  operationError.value = null
  formError.value = ''
  form.value = parameterToForm(definition, value, authState.user?.primaryOrganizationId ?? 0)
  form.value.environment = targetEnvironment
  editorSnapshot.value = JSON.stringify(form.value)
}

function openNewConfiguration() {
  const scope = newConfigurationScope.value
  if (!scope) return
  openEditor(scope.definition, null, scope.environment)
  form.value.organizationId = scope.organizationId
  editorSnapshot.value = JSON.stringify(form.value)
}

function closeEditor() {
  if (isSaving.value) return
  if (JSON.stringify(form.value) !== editorSnapshot.value && !window.confirm('当前参数修改尚未保存，确定关闭吗？')) return
  selectedDefinition.value = null
  selectedValue.value = null
}

async function reloadEditor() {
  const definition = selectedDefinition.value
  if (!definition) return
  const targetEnvironment = form.value.environment
  const targetOrganizationId = definition.organizationScoped ? form.value.organizationId : null
  await loadPage(true)
  if (error.value) return
  const latestDefinition = definitions.value.find(item => item.key === definition.key)
  if (!latestDefinition) {
    closeEditor()
    return
  }
  const latestValue = values.value.find(value => value.parameterKey === definition.key
    && value.environment === targetEnvironment && value.organizationId === targetOrganizationId) ?? null
  openEditor(latestDefinition, latestValue, targetEnvironment)
}

async function submit() {
  const definition = selectedDefinition.value
  if (!definition || !canWrite.value || isSaving.value) return
  formError.value = validateParameterForm(definition, form.value) ?? ''
  if (formError.value) return
  isSaving.value = true
  operationError.value = null
  try {
    const updated = await upsertParameterValue(definition.key, toUpsertParameterInput(form.value))
    const index = values.value.findIndex(value => value.id === updated.id)
    if (index === -1) values.value.push(updated)
    else values.value[index] = updated
    notice.value = `${definition.name}已保存。`
    auditTarget.value = { targetType: 'PARAMETER', targetId: definition.key }
    selectedDefinition.value = null
    selectedValue.value = null
  } catch (caught) {
    const apiError = asUncertainWriteError(asApiError(caught, '无法保存参数'))
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally { isSaving.value = false }
}

async function removeConfiguration(definition: ParameterDefinition, value: ParameterValue) {
  if (!canWrite.value || deletingValueId.value !== null) return
  const scope = `${environmentLabel(value.environment)} · ${organizationLabel(value, definition)}`
  if (!window.confirm(`确定删除“${definition.name}”在${scope}的配置吗？删除后该范围会恢复为尚未配置。`)) return
  deletingValueId.value = value.id
  operationError.value = null
  try {
    await deleteParameterValue(definition.key, {
      environment: value.environment,
      organizationId: value.organizationId,
      version: value.version,
    })
    values.value = values.value.filter(item => item.id !== value.id)
    notice.value = `${definition.name}在${scope}的配置已删除，当前状态为尚未配置。`
    auditTarget.value = { targetType: 'PARAMETER', targetId: definition.key }
  } catch (caught) {
    const apiError = asUncertainWriteError(asApiError(caught, '无法删除参数配置'))
    if (!await handleUnauthorized(apiError)) error.value = apiError
  } finally { deletingValueId.value = null }
}

function organizationLabel(value: ParameterValue | null, definition: ParameterDefinition) {
  if (!definition.organizationScoped) return '全部机构'
  if (value?.organizationId) return organizations.value.find(item => item.id === value.organizationId)?.organizationName ?? value.organizationCode ?? `机构 #${value.organizationId}`
  return organizations.value.find(item => item.id === authState.user?.primaryOrganizationId)?.organizationName ?? '当前用户的主要机构'
}

function constraintLabel(definition: ParameterDefinition) {
  const constraints: string[] = []
  if (definition.maximumLength !== null) constraints.push(`最多 ${definition.maximumLength} 个字符`)
  if (definition.minimumNumber !== null) constraints.push(`最小 ${definition.minimumNumber}`)
  if (definition.maximumNumber !== null) constraints.push(`最大 ${definition.maximumNumber}`)
  return constraints.join('，') || '无额外填写限制'
}

onMounted(() => loadPage())
onBeforeUnmount(() => { mounted = false; controller?.abort() })
</script>

<template>
  <section class="content parameter-page">
    <AuditAwareSuccess v-if="notice" :message="notice" :target-type="auditTarget?.targetType" :target-id="auditTarget?.targetId" @close="notice = ''; auditTarget = null" />
    <div v-if="error && !isLoading" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ error.message }}</span><button class="prototype-text-button" type="button" @click="loadPage(true)"><RefreshCw :size="15" />重试</button></div>
    <div class="parameter-summary" aria-label="参数配置概况"><span><strong>{{ definitions.length }}</strong> 个参数</span><span><strong>{{ configuredCount }}</strong> 项已启用配置</span><span :class="{ warning: missingCount > 0 }"><strong>{{ missingCount }}</strong> 项尚未配置</span></div>
    <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" type="search" placeholder="参数名称、键或机构" aria-label="搜索参数" /></label><select v-model="environment" aria-label="运行环境"><option value="all">全部环境</option><option value="DEVELOPMENT">开发环境</option><option value="TEST">测试环境</option><option value="PRODUCTION">生产环境</option></select><select v-model="configurationStatus" aria-label="配置状态"><option value="all">全部配置状态</option><option value="configured">已配置并启用</option><option value="missing">尚未配置</option><option value="disabled">已配置但停用</option></select><span>{{ rows.length }} / {{ expandedRows.length }} 项</span><button v-if="canWrite" class="prototype-button" type="button" :disabled="!newConfigurationScope" :title="newConfigurationScope ? '为尚未配置的环境或机构新增参数值' : '所有允许的环境和机构范围都已配置'" @click="openNewConfiguration"><Plus :size="15" />新增配置</button><button class="work-quiet-button" type="button" :disabled="isRefreshing" @click="loadPage(true)"><RefreshCw :size="15" :class="{ spinning: isRefreshing }" />刷新</button></div>
    <section class="prototype-section work-table-section parameter-panel action-column-table">
      <div v-if="isLoading" class="page-state"><LoaderCircle class="spinning" :size="28" /><strong>正在加载参数定义</strong></div>
      <div v-else-if="!error && definitions.length === 0" class="page-state"><Settings2 :size="30" /><strong>尚无代码注册参数</strong><span>参数必须先在后端完成定义、约束和代码评审，管理页不会创建任意参数键。</span></div>
      <div v-else class="prototype-table-wrap"><table class="work-table parameter-table"><thead><tr><th>参数</th><th>运行环境</th><th>适用机构</th><th>当前值</th><th>状态</th><th>最近更新</th><th>操作</th></tr></thead><tbody><tr v-for="row in pagedRows" :key="`${row.definition.key}-${row.targetEnvironment}-${row.value?.id ?? 'new'}`"><td><strong>{{ row.definition.name }}</strong><small>{{ row.definition.key }} · {{ parameterValueTypeLabel(row.definition.valueType) }}</small><small>{{ constraintLabel(row.definition) }}</small></td><td>{{ environmentLabel(row.targetEnvironment) }}</td><td>{{ organizationLabel(row.value, row.definition) }}</td><td><span v-if="row.value?.configured">{{ row.definition.sensitive ? '已设置（内容保密）' : row.value.value || '空值' }}</span><span v-else class="muted">尚未配置</span></td><td><span class="prototype-tag" :class="row.value?.enabled ? 'success' : 'neutral'">{{ row.value?.configured ? row.value.enabled ? '已启用' : '已停用' : '未配置' }}</span></td><td>{{ row.value ? formatLocalDateTime(row.value.updatedAt) : '—' }}</td><td><div v-if="canWrite" class="parameter-actions"><button class="work-quiet-button" type="button" @click="openEditor(row.definition, row.value, row.targetEnvironment)">{{ row.value?.configured ? '修改' : '新增配置' }}</button><button v-if="row.value?.configured" class="work-danger-button" type="button" :disabled="deletingValueId !== null" @click="removeConfiguration(row.definition, row.value)"><Trash2 :size="13" />{{ deletingValueId === row.value.id ? '删除中…' : '删除' }}</button></div></td></tr></tbody></table><div v-if="rows.length === 0" class="prototype-empty">没有符合当前条件的参数</div></div>
      <AdminPagination v-if="!isLoading && definitions.length" :total="rows.length" :page="page" :page-size="pageSize" @update:page="page = $event" @update:page-size="pageSize = $event" />
    </section>
    <ParameterEditorDrawer v-if="selectedDefinition" v-model:form="form" :definition="selectedDefinition" :existing="selectedValue" :organizations="organizations" :can-write="canWrite" :is-saving="isSaving" :error="operationError" :form-error="formError" @close="closeEditor" @submit="submit" @reload="reloadEditor" />
  </section>
</template>

<style scoped>
.parameter-page { display: grid; gap: 0; }
.parameter-summary { margin-bottom: 10px; display: flex; flex-wrap: wrap; gap: 8px; }.parameter-summary span { padding: 8px 11px; border: 1px solid #dce4e6; border-radius: 5px; background: white; color: #62717a; font-size: 11px; }.parameter-summary strong { margin-right: 3px; color: #263b42; font-size: 15px; }.parameter-summary .warning { border-color: #e6cf9d; background: #fff9e9; color: #7b5c1f; }
.parameter-panel { --action-column-width: 156px; }
.parameter-table { min-width: 1080px; }.parameter-table td small { display: block; margin-top: 3px; color: #78868e; font-size: 10px; }
.parameter-actions { display: flex; align-items: center; justify-content: flex-end; gap: 6px; white-space: nowrap; }
.feedback { min-height: 46px; margin-bottom: 12px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 9px; font-size: 12px; }
.feedback span { flex: 1; }.feedback button { border: 0; background: transparent; }.feedback.success { border-color: #bfddce; background: #ecf7f0; color: #226c49; }.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }
.page-state { min-height: 330px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #33745c; text-align: center; }.page-state span { max-width: 520px; color: #75828c; font-size: 12px; line-height: 1.6; }.muted { color: #7b8b92; }.spinning { animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
</style>
