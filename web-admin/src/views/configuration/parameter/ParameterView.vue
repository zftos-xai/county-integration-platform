<!-- 参数配置页面：加载代码注册定义与当前主体可见的真实作用域值。 -->
<script setup lang="ts">
import { AlertCircle, Check, LoaderCircle, RefreshCw, Search, Settings2, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  listParameterDefinitions, listParameterValues, upsertParameterValue,
} from '@/api/system/configuration'
import type { ParameterDefinition, ParameterEnvironment, ParameterValue } from '@/api/system/configuration'
import { listOrganizations } from '@/api/system/organization'
import type { Organization } from '@/api/system/organization'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'
import ParameterEditorDrawer from './components/ParameterEditorDrawer.vue'
import { parameterToForm, toUpsertParameterInput, validateParameterForm } from './form'

const router = useRouter()
const definitions = ref<ParameterDefinition[]>([])
const values = ref<ParameterValue[]>([])
const organizations = ref<Organization[]>([])
const isLoading = ref(true)
const isRefreshing = ref(false)
const isSaving = ref(false)
const error = ref<ApiClientError | null>(null)
const operationError = ref<ApiClientError | null>(null)
const notice = ref('')
const query = ref('')
const environment = ref<'all' | ParameterEnvironment>('all')
const selectedDefinition = ref<ParameterDefinition | null>(null)
const selectedValue = ref<ParameterValue | null>(null)
const form = ref(parameterToForm({ key: '', name: '', valueType: 'STRING', environments: ['DEVELOPMENT'], organizationScoped: false, sensitive: false, maximumLength: null, minimumNumber: null, maximumNumber: null, pattern: null }, null, 0))
const formError = ref('')
let controller: AbortController | null = null
let mounted = true

type ParameterRow = { definition: ParameterDefinition; value: ParameterValue | null; targetEnvironment: ParameterEnvironment }

const canWrite = computed(() => hasPermission('configuration:write'))
const rows = computed<ParameterRow[]>(() => {
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
  return expanded.filter(row => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  const textMatch = !keyword || [row.definition.name, row.definition.key, row.value?.organizationCode ?? ''].some(value => value.toLocaleLowerCase('zh-CN').includes(keyword))
  return textMatch && (environment.value === 'all' || row.targetEnvironment === environment.value)
  })
})

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
    const requests: [Promise<ParameterDefinition[]>, Promise<ParameterValue[]>, Promise<Organization[]>] = [
      listParameterDefinitions(current.signal), listParameterValues(current.signal),
      hasPermission('organization:read') ? listOrganizations(true, current.signal) : Promise.resolve([]),
    ]
    const [definitionRows, valueRows, organizationRows] = await Promise.all(requests)
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
}

function closeEditor() {
  if (isSaving.value) return
  selectedDefinition.value = null
  selectedValue.value = null
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
    selectedDefinition.value = null
    selectedValue.value = null
  } catch (caught) {
    const apiError = asApiError(caught, '无法保存参数')
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally { isSaving.value = false }
}

function scopeLabel(value: ParameterValue | null, definition: ParameterDefinition, targetEnvironment: ParameterEnvironment) {
  if (!value) return definition.organizationScoped ? `当前机构 · ${targetEnvironment}` : `平台级 · ${targetEnvironment}`
  return value.organizationCode ? `${value.organizationCode} · ${value.environment}` : `平台级 · ${value.environment}`
}

onMounted(() => loadPage())
onBeforeUnmount(() => { mounted = false; controller?.abort() })
</script>

<template>
  <section class="content parameter-page">
    <div v-if="notice" class="feedback success" role="status"><Check :size="18" /><span>{{ notice }}</span><button aria-label="关闭提示" @click="notice = ''"><X :size="16" /></button></div>
    <div v-if="error && !isLoading" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ error.message }}</span><button class="prototype-text-button" type="button" @click="loadPage(true)"><RefreshCw :size="15" />重试</button></div>
    <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" type="search" placeholder="参数名称、键或机构" aria-label="搜索参数" /></label><span>{{ rows.length }} 项配置</span><select v-model="environment" aria-label="部署环境"><option value="all">全部环境</option><option value="DEVELOPMENT">开发环境</option><option value="TEST">测试环境</option><option value="PRODUCTION">生产环境</option></select><button class="work-quiet-button" type="button" :disabled="isRefreshing" @click="loadPage(true)"><RefreshCw :size="15" :class="{ spinning: isRefreshing }" />刷新</button></div>
    <section class="prototype-section work-table-section">
      <div v-if="isLoading" class="page-state"><LoaderCircle class="spinning" :size="28" /><strong>正在加载参数定义</strong></div>
      <div v-else-if="!error && definitions.length === 0" class="page-state"><Settings2 :size="30" /><strong>尚无代码注册参数</strong><span>参数必须先在后端完成定义、约束和代码评审，管理页不会创建任意参数键。</span></div>
      <div v-else class="prototype-table-wrap"><table class="work-table"><thead><tr><th>参数</th><th>作用域</th><th>值</th><th>状态</th><th><span class="visually-hidden">操作</span></th></tr></thead><tbody><tr v-for="row in rows" :key="`${row.definition.key}-${row.targetEnvironment}-${row.value?.id ?? 'new'}`"><td><strong>{{ row.definition.name }}</strong><small>{{ row.definition.key }} · {{ row.definition.valueType }}</small></td><td>{{ scopeLabel(row.value, row.definition, row.targetEnvironment) }}</td><td><span v-if="row.value">{{ row.value.value || '空值' }}</span><span v-else class="muted">尚未配置</span><small v-if="row.definition.sensitive">敏感值仅显示掩码</small></td><td><span class="prototype-tag" :class="row.value?.enabled ? 'success' : 'neutral'">{{ row.value ? row.value.enabled ? '已启用' : '已停用' : '未配置' }}</span></td><td><button v-if="canWrite" class="work-quiet-button" type="button" @click="openEditor(row.definition, row.value, row.targetEnvironment)">{{ row.value ? '编辑' : '配置' }}</button></td></tr></tbody></table><div v-if="rows.length === 0" class="prototype-empty">没有符合当前条件的参数</div></div>
    </section>
    <ParameterEditorDrawer v-if="selectedDefinition" v-model:form="form" :definition="selectedDefinition" :existing="selectedValue" :organizations="organizations" :can-write="canWrite" :is-saving="isSaving" :error="operationError" :form-error="formError" @close="closeEditor" @submit="submit" />
  </section>
</template>

<style scoped>
.parameter-page { display: grid; gap: 0; }
.feedback { min-height: 46px; margin-bottom: 12px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 9px; font-size: 12px; }
.feedback span { flex: 1; }.feedback button { border: 0; background: transparent; }.feedback.success { border-color: #bfddce; background: #ecf7f0; color: #226c49; }.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }
.page-state { min-height: 330px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #33745c; text-align: center; }.page-state span { max-width: 520px; color: #75828c; font-size: 12px; line-height: 1.6; }.muted { color: #7b8b92; }.spinning { animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
</style>
