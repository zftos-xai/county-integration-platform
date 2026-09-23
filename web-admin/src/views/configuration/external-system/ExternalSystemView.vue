<!-- 外部系统管理页面：使用真实API维护系统身份及按环境、机构隔离的接口配置。 -->
<script setup lang="ts">
import {
  AlertCircle, Circle, ClipboardList, KeyRound, LoaderCircle, Pencil,
  Plus, RefreshCw, Search, ServerCog, Settings2,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createExternalEndpoint, createExternalSystem, listExternalEndpoints, listExternalSystems,
  updateExternalEndpoint, updateExternalSystem, verifyExternalEndpoint,
} from '@/api/system/configuration'
import type { ExternalEndpoint, ExternalSystem, ParameterEnvironment } from '@/api/system/configuration'
import { listOrganizations } from '@/api/system/organization'
import type { Organization } from '@/api/system/organization'
import AdminPagination from '@/components/AdminPagination.vue'
import AuditAwareSuccess from '@/components/AuditAwareSuccess.vue'
import ListQueryToolbar from '@/components/ListQueryToolbar.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { authState, hasPermission } from '@/store/modules/auth'
import { formatLocalDateTime } from '@/utils/managementDisplay'
import { ApiClientError, asUncertainWriteError } from '@/utils/request'
import ExternalEndpointEditorDrawer from './components/ExternalEndpointEditorDrawer.vue'
import ExternalSystemEditorDrawer from './components/ExternalSystemEditorDrawer.vue'
import {
  emptyExternalEndpointForm, emptyExternalSystemForm, externalEndpointToForm, externalSystemToForm, normalizeHisServiceUrl,
  toCreateExternalEndpointInput, toCreateExternalSystemInput, toUpdateExternalEndpointInput,
  toUpdateExternalSystemInput, validateExternalEndpointForm, validateExternalSystemForm,
} from './form'
import type { ExternalEndpointForm, ExternalSystemForm } from './form'

const router = useRouter()
const systems = ref<ExternalSystem[]>([])
const endpoints = ref<ExternalEndpoint[]>([])
const organizations = ref<Organization[]>([])
const selectedSystem = ref<ExternalSystem | null>(null)
const selectedEndpoint = ref<ExternalEndpoint | null>(null)
const systemForm = ref<ExternalSystemForm>(emptyExternalSystemForm())
const endpointForm = ref<ExternalEndpointForm>(emptyExternalEndpointForm(null))
const systemEditorOpen = ref(false)
const endpointEditorOpen = ref(false)
const query = ref('')
const endpointStatus = ref<'all' | 'ready' | 'attention'>('all')
const onlyIncomplete = ref(false)
const appliedFilters = ref<{ query: string; endpointStatus: 'all' | 'ready' | 'attention'; onlyIncomplete: boolean }>({
  query: '', endpointStatus: 'all', onlyIncomplete: false,
})
const isLoading = ref(true)
const isEndpointLoading = ref(false)
const isRefreshing = ref(false)
const isSaving = ref(false)
const error = ref<ApiClientError | null>(null)
const operationError = ref<ApiClientError | null>(null)
const formError = ref('')
const notice = ref('')
const auditTarget = ref<{ targetType: string; targetId: string } | null>(null)
const editorSnapshot = ref('')
let pageController: AbortController | null = null
let endpointController: AbortController | null = null
let mounted = true

const canWrite = computed(() => hasPermission('configuration:write'))
const canReadExchange = computed(() => hasPermission('exchange:read'))
const organizationOptions = computed(() => organizations.value.length > 0 ? organizations.value : authState.user ? [{
  id: authState.user.primaryOrganizationId,
  organizationCode: authState.user.organizationCode,
  organizationName: `当前机构（${authState.user.organizationCode}）`,
  organizationType: '', parentId: null, enabled: true, validFrom: null, validTo: null,
  createdAt: '', updatedAt: '', version: '',
}] : [])
const endpointOrganizationOptions = computed(() => selectedSystem.value?.systemCode === 'PRIMARY_HIS'
  ? organizationOptions.value.filter(item => item.enabled && item.organizationType === 'PRIMARY_CARE')
  : organizationOptions.value.filter(item => item.enabled))
type EndpointGroup = {
  key: string
  organizationId: number | null
  organizationCode: string
  organizationName: string
  endpoints: Partial<Record<ParameterEnvironment, ExternalEndpoint>>
  latestEndpoint: ExternalEndpoint | null
}

const selectedSystemId = computed({
  get: () => selectedSystem.value?.id ?? null,
  set: (systemId: number | null) => {
    const system = systems.value.find(item => item.id === systemId)
    if (system) void selectSystem(system)
  },
})
const endpointGroups = computed<EndpointGroup[]>(() => {
  const groups = new Map<string, EndpointGroup>()
  // 基层 HIS 按机构配置；其他系统可能使用全局接口，不能将所有机构误判为缺项。
  if (selectedSystem.value?.systemCode === 'PRIMARY_HIS') {
    for (const organization of endpointOrganizationOptions.value) {
      const key = `ORG-${organization.id}`
      groups.set(key, {
        key,
        organizationId: organization.id,
        organizationCode: organization.organizationCode,
        organizationName: organization.organizationName,
        endpoints: {},
        latestEndpoint: null,
      })
    }
  }
  endpoints.value.forEach(endpoint => {
    const key = endpointGroupKey(endpoint)
    const existing = groups.get(key)
    if (existing) {
      existing.endpoints[endpoint.environment] = endpoint
      if (!existing.latestEndpoint || endpoint.updatedAt > existing.latestEndpoint.updatedAt) existing.latestEndpoint = endpoint
      return
    }
    groups.set(key, {
      key,
      organizationId: endpoint.organizationId,
      organizationCode: endpoint.organizationCode ?? 'GLOBAL',
      organizationName: organizationLabel(endpoint),
      endpoints: { [endpoint.environment]: endpoint },
      latestEndpoint: endpoint,
    })
  })
  return [...groups.values()].sort((left, right) => left.organizationName.localeCompare(right.organizationName, 'zh-CN'))
})
const filteredEndpointGroups = computed(() => {
  const keyword = appliedFilters.value.query.toLocaleLowerCase('zh-CN')
  return endpointGroups.value.filter(group => {
    const configured = Object.values(group.endpoints)
    if (appliedFilters.value.endpointStatus === 'ready' && !configured.some(endpoint => endpoint.verificationStatus === 'VERIFIED')) return false
    if (appliedFilters.value.endpointStatus === 'attention' && configured.some(endpoint => endpoint.verificationStatus === 'VERIFIED')) return false
    if (appliedFilters.value.onlyIncomplete && group.endpoints.PRODUCTION && group.endpoints.TEST
      && configured.every(endpoint => endpoint.credentialConfigured)) return false
    return !keyword || [group.organizationName, group.organizationCode]
      .some(value => value.toLocaleLowerCase('zh-CN').includes(keyword))
  })
})
const { page, pageSize, pagedRows } = useClientPagination(filteredEndpointGroups)

/** 按当前输入条件查询机构接口列表。 */
function applyFilters() {
  appliedFilters.value = {
    query: query.value.trim(), endpointStatus: endpointStatus.value, onlyIncomplete: onlyIncomplete.value,
  }
  page.value = 1
}

/** 恢复机构接口列表的默认筛选条件。 */
function resetFilters() {
  query.value = ''
  endpointStatus.value = 'all'
  onlyIncomplete.value = false
  appliedFilters.value = { query: '', endpointStatus: 'all', onlyIncomplete: false }
  page.value = 1
}

/** 将未知错误转换为页面可展示的API错误。 */
function asApiError(caught: unknown, message: string) {
  return caught instanceof ApiClientError ? caught : new ApiClientError('UNKNOWN_ERROR', message, 0)
}

/** 处理会话失效并返回是否已经完成跳转。 */
async function handleUnauthorized(apiError: ApiClientError) {
  if (apiError.status !== 401) return false
  authState.user = null; authState.isInitialized = true
  await router.replace({ path: '/login', query: { redirect: '/external-systems' } })
  return true
}

/** 加载系统清单、机构选项，并选中当前系统。 */
async function loadPage(background = false) {
  pageController?.abort(); const current = new AbortController(); pageController = current
  if (background) isRefreshing.value = true; else isLoading.value = true
  error.value = null
  try {
    const [systemRows, organizationRows] = await Promise.all([
      listExternalSystems(current.signal),
      hasPermission('organization:read') ? listOrganizations(true, current.signal) : Promise.resolve([]),
    ])
    if (!mounted || current.signal.aborted) return
    systems.value = systemRows
    organizations.value = organizationRows
    const latestSelection = selectedSystem.value
      ? systemRows.find(item => item.id === selectedSystem.value?.id) ?? null
      : systemRows[0] ?? null
    selectedSystem.value = latestSelection
    if (latestSelection) await loadEndpoints(latestSelection, current.signal)
    else endpoints.value = []
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取外部系统配置')
    if (apiError.code !== 'REQUEST_ABORTED' && !await handleUnauthorized(apiError)) error.value = apiError
  } finally { if (pageController === current && mounted) { isLoading.value = false; isRefreshing.value = false } }
}

/** 加载指定系统下当前管理员可见的机构接口配置。 */
async function loadEndpoints(system: ExternalSystem, parentSignal?: AbortSignal) {
  endpointController?.abort(); const current = new AbortController(); endpointController = current
  const abortFromParent = () => current.abort()
  parentSignal?.addEventListener('abort', abortFromParent, { once: true })
  isEndpointLoading.value = true
  try {
    const rows = await listExternalEndpoints(system.id, current.signal)
    if (!mounted || current.signal.aborted || selectedSystem.value?.id !== system.id) return
    endpoints.value = rows
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取机构接口配置')
    if (apiError.code !== 'REQUEST_ABORTED' && !await handleUnauthorized(apiError)) error.value = apiError
  } finally {
    parentSignal?.removeEventListener('abort', abortFromParent)
    if (endpointController === current && mounted) isEndpointLoading.value = false
  }
}

/** 选择系统并读取其机构接口配置。 */
async function selectSystem(system: ExternalSystem) {
  selectedSystem.value = system; endpoints.value = []; error.value = null
  await loadEndpoints(system)
}

/** 打开新增系统抽屉。 */
function openSystemCreate() {
  systemForm.value = emptyExternalSystemForm(); operationError.value = null; formError.value = ''
  editorSnapshot.value = JSON.stringify(systemForm.value); systemEditorOpen.value = true
}

/** 打开系统编辑抽屉。 */
function openSystemEdit(system: ExternalSystem) {
  selectedSystem.value = system; systemForm.value = externalSystemToForm(system)
  operationError.value = null; formError.value = ''; editorSnapshot.value = JSON.stringify(systemForm.value)
  systemEditorOpen.value = true
}

/** 打开新增机构接口配置抽屉。 */
function openEndpointCreate(organizationId?: number | null) {
  if (!selectedSystem.value) return
  endpointForm.value = emptyExternalEndpointForm(organizationId ?? endpointOrganizationOptions.value[0]?.id ?? null)
  selectedEndpoint.value = null; operationError.value = null; formError.value = ''
  editorSnapshot.value = endpointFormState(endpointForm.value); endpointEditorOpen.value = true
}

/** 打开机构接口配置编辑抽屉。 */
function openEndpointEdit(endpoint: ExternalEndpoint) {
  endpointForm.value = externalEndpointToForm(endpoint); selectedEndpoint.value = endpoint
  operationError.value = null; formError.value = ''; editorSnapshot.value = endpointFormState(endpointForm.value)
  endpointEditorOpen.value = true
}

/** 序列化用于关闭确认的接口表单；未修改的按需回显内容不视为编辑。 */
function endpointFormState(form: ExternalEndpointForm) {
  return JSON.stringify(form.authenticationChanged ? form : {
    ...form,
    vendorCode: '', username: '', password: '', authorizationCode: '',
  })
}

/** 关闭系统抽屉，并保护尚未保存的编辑。 */
function closeSystemEditor() {
  if (isSaving.value) return
  if (JSON.stringify(systemForm.value) !== editorSnapshot.value && !window.confirm('当前系统修改尚未保存，确定关闭吗？')) return
  systemEditorOpen.value = false
}

/** 关闭机构接口配置抽屉，并保护尚未保存的编辑。 */
function closeEndpointEditor() {
  if (isSaving.value) return
  if (endpointFormState(endpointForm.value) !== editorSnapshot.value && !window.confirm('当前接口配置尚未保存，确定关闭吗？')) return
  endpointEditorOpen.value = false; selectedEndpoint.value = null
  endpointForm.value = emptyExternalEndpointForm(null)
}

/** 保存新增或修改的外部系统。 */
async function submitSystem() {
  if (!canWrite.value || isSaving.value) return
  const creating = !systemForm.value.version
  formError.value = validateExternalSystemForm(systemForm.value, creating) ?? ''
  if (formError.value) return
  isSaving.value = true; operationError.value = null
  try {
    const saved = creating
      ? await createExternalSystem(toCreateExternalSystemInput(systemForm.value))
      : await updateExternalSystem(selectedSystem.value?.id ?? 0, toUpdateExternalSystemInput(systemForm.value))
    const index = systems.value.findIndex(item => item.id === saved.id)
    if (index < 0) systems.value.push(saved); else systems.value[index] = saved
    selectedSystem.value = saved; notice.value = `${saved.systemName}已保存。`
    auditTarget.value = { targetType: 'EXTERNAL_SYSTEM', targetId: saved.systemCode }
    systemEditorOpen.value = false
    await loadEndpoints(saved)
  } catch (caught) {
    const apiError = asUncertainWriteError(asApiError(caught, '无法保存外部系统'))
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally { isSaving.value = false }
}

/** 保存新增或修改的机构接口配置。 */
async function submitEndpoint() {
  const system = selectedSystem.value
  if (!system || !canWrite.value || isSaving.value) return
  formError.value = validateExternalEndpointForm(
    endpointForm.value, selectedEndpoint.value, system.systemCode === 'PRIMARY_HIS',
  ) ?? ''
  if (formError.value) return
  isSaving.value = true; operationError.value = null
  try {
    let saved = selectedEndpoint.value
      ? await updateExternalEndpoint(selectedEndpoint.value.id, toUpdateExternalEndpointInput(endpointForm.value))
      : await createExternalEndpoint(system.id, toCreateExternalEndpointInput(endpointForm.value))
    if (system.systemCode === 'PRIMARY_HIS') saved = await verifyExternalEndpoint(saved.id)
    await loadEndpoints(system)
    notice.value = saved.verificationStatus === 'VERIFIED'
      ? `机构接口已保存并确认数据来源：${saved.sourceOrganizationName}。`
      : `机构接口已保存，但未能确认数据来源：${saved.verificationFailureSummary ?? '请核对HIS配置后重新校验'}。`
    auditTarget.value = { targetType: 'EXTERNAL_ENDPOINT', targetId: String(saved.id) }
    endpointEditorOpen.value = false; selectedEndpoint.value = null
    endpointForm.value = emptyExternalEndpointForm(null)
  } catch (caught) {
    const apiError = asUncertainWriteError(asApiError(caught, '无法保存机构接口配置'))
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally { isSaving.value = false }
}

/** 并发冲突或结果未知后重新读取系统编辑对象。 */
async function reloadSystemEditor() {
  const systemId = selectedSystem.value?.id
  await loadPage(true)
  const latest = systems.value.find(item => item.id === systemId)
  if (latest) openSystemEdit(latest); else systemEditorOpen.value = false
}

/** 并发冲突或结果未知后重新读取机构接口配置。 */
async function reloadEndpointEditor() {
  const system = selectedSystem.value; const endpointId = selectedEndpoint.value?.id
  if (!system) return
  await loadEndpoints(system)
  const latest = endpoints.value.find(item => item.id === endpointId)
  if (latest) openEndpointEdit(latest); else endpointEditorOpen.value = false
}

/** 返回机构接口配置的展示名称。 */
function organizationLabel(endpoint: ExternalEndpoint) {
  return organizationOptions.value.find(item => item.id === endpoint.organizationId)?.organizationName
    ?? endpoint.organizationCode ?? '全部机构'
}

/** 返回机构接口配置表使用的稳定分组键。 */
function endpointGroupKey(endpoint: ExternalEndpoint) {
  return endpoint.organizationId === null ? 'GLOBAL' : `ORG-${endpoint.organizationId}`
}

/** 返回机构分组中优先展示和编辑的接口配置。 */
function primaryEndpoint(group: EndpointGroup) {
  return group.endpoints.PRODUCTION ?? group.endpoints.TEST ?? group.endpoints.DEVELOPMENT ?? group.latestEndpoint
}

/** 编辑机构已有的接口配置；未配置机构只提供新增入口。 */
function openGroupEndpointEdit(group: EndpointGroup) {
  const endpoint = primaryEndpoint(group)
  if (endpoint) openEndpointEdit(endpoint)
}

/** 打开该端点校验请求的只读调用记录，端点主键是服务端保存的稳定关联引用。 */
async function openEndpointExchangeRecords(group: EndpointGroup) {
  const endpoint = primaryEndpoint(group)
  if (!endpoint || !endpoint.organizationCode || !canReadExchange.value) return
  await router.push({
    path: '/exchanges',
    query: {
      organizationCode: endpoint.organizationCode,
      sourceRecordId: `EXTERNAL_ENDPOINT:${endpoint.id}`,
    },
  })
}

/** 返回一条接口配置的简短状态文案。 */
function endpointState(endpoint?: ExternalEndpoint) {
  if (!endpoint) return '未配置'
  if (endpoint.verificationStatus === 'VERIFIED') return '可同步'
  if (endpoint.verificationStatus === 'RESULT_UNKNOWN') return '结果未知'
  if (endpoint.verificationStatus === 'FAILED') return '校验失败'
  return '待校验'
}

/** 显示服务端实际保存的完整HIS配置地址。 */
function displayServiceUrl(endpoint?: ExternalEndpoint) {
  if (!endpoint) return '—'
  try { return normalizeHisServiceUrl(endpoint.baseUrl) } catch { return endpoint.baseUrl }
}

onMounted(() => loadPage())
onBeforeUnmount(() => { mounted = false; pageController?.abort(); endpointController?.abort() })
</script>

<template>
  <section class="content external-system-page">
    <AuditAwareSuccess v-if="notice" :message="notice" :target-type="auditTarget?.targetType" :target-id="auditTarget?.targetId" @close="notice = ''; auditTarget = null" />
    <div v-if="error && !isLoading" class="feedback danger page-error" role="alert"><AlertCircle :size="18" /><span>{{ error.message }}</span><button class="work-quiet-button" type="button" @click="loadPage(true)"><RefreshCw :size="15" />重试</button></div>
    <section v-if="isLoading" class="prototype-section first-load-state"><LoaderCircle class="spinning" :size="28" /><strong>正在加载外部系统</strong></section>
    <section v-else-if="!error && systems.length === 0" class="prototype-section first-system-empty">
      <ServerCog :size="38" aria-hidden="true" />
      <h2>登记第一个外部系统</h2>
      <p>先登记需要对接的外部系统，再为各机构添加接口配置。</p>
      <button v-if="canWrite" class="prototype-button" type="button" @click="openSystemCreate"><Plus :size="16" />登记外部系统</button>
    </section>
    <template v-else>
      <section class="system-context" aria-label="当前外部系统">
        <strong>当前系统</strong>
        <label class="system-selector">
          <ServerCog :size="19" aria-hidden="true" />
          <select v-model="selectedSystemId" aria-label="当前外部系统">
            <option v-for="system in systems" :key="system.id" :value="system.id">{{ system.systemName }}</option>
          </select>
        </label>
        <span v-if="selectedSystem" class="system-state" :class="{ disabled: !selectedSystem.enabled }"><Circle :size="9" fill="currentColor" />{{ selectedSystem.enabled ? '已启用' : '已停用' }}</span>
        <div class="context-actions">
          <button v-if="canWrite && selectedSystem" class="work-quiet-button context-edit" type="button" @click="openSystemEdit(selectedSystem)"><Settings2 :size="15" />系统资料</button>
          <button v-if="canWrite" class="work-quiet-button" type="button" @click="openSystemCreate"><Plus :size="15" />新增系统</button>
          <button v-if="canWrite && selectedSystem" class="prototype-button" type="button" :disabled="!selectedSystem.enabled" @click="openEndpointCreate()"><Plus :size="15" />新增机构接口配置</button>
        </div>
      </section>

      <section class="prototype-section connection-matrix action-column-table">
        <ListQueryToolbar v-if="endpointGroups.length" :refreshing="isRefreshing" @query="applyFilters" @reset="resetFilters" @refresh="loadPage(true)">
            <label class="prototype-search"><Search :size="16" /><input v-model="query" type="search" placeholder="搜索机构名称或编码" aria-label="搜索机构名称或编码" /></label>
            <label class="status-filter"><span>同步状态</span><select v-model="endpointStatus"><option value="all">全部</option><option value="ready">有可用环境</option><option value="attention">无可用环境</option></select></label>
            <label class="incomplete-filter"><input v-model="onlyIncomplete" type="checkbox" />仅看配置缺项</label>
        </ListQueryToolbar>

        <div v-if="isEndpointLoading" class="page-state"><LoaderCircle class="spinning" :size="28" /><strong>正在加载机构接口配置</strong></div>
        <div v-else-if="selectedSystem && endpointGroups.length === 0" class="endpoint-empty"><ServerCog :size="32" aria-hidden="true" /><h2>还没有可展示的机构</h2><p>当前没有可见的机构或接口配置；请检查机构数据范围，或先为 {{ selectedSystem.systemName }} 添加接口配置。</p><button v-if="canWrite && selectedSystem.enabled" class="prototype-button" type="button" @click="openEndpointCreate()"><Plus :size="15" />新增机构接口配置</button></div>
        <div v-else class="prototype-table-wrap matrix-table-wrap">
        <table class="work-table matrix-table">
          <colgroup><col class="org-column" /><col class="org-code-column" /><col class="state-column" /><col class="url-column" /><col class="state-column" /><col class="url-column" /><col class="credential-column" /><col class="source-column" /><col class="updated-column" /><col class="action-column" /></colgroup>
          <thead><tr><th>机构</th><th>机构编码</th><th>生产状态</th><th>生产接口地址</th><th>测试状态</th><th>测试接口地址</th><th>接入信息</th><th>数据来源</th><th>最后更新</th><th>操作</th></tr></thead>
          <tbody>
              <tr v-for="group in pagedRows" :key="group.key" class="organization-row">
                <td><strong class="single-line" :title="group.organizationName">{{ group.organizationName }}</strong></td>
                <td><span class="single-line" :title="group.organizationCode">{{ group.organizationCode }}</span></td>
                <td><span class="environment-cell state-line" :class="{ missing: !group.endpoints.PRODUCTION, attention: group.endpoints.PRODUCTION && group.endpoints.PRODUCTION.verificationStatus !== 'VERIFIED' }"><Circle :size="8" fill="currentColor" />{{ endpointState(group.endpoints.PRODUCTION) }}</span></td>
                <td><span class="single-line endpoint-url" :title="displayServiceUrl(group.endpoints.PRODUCTION)">{{ displayServiceUrl(group.endpoints.PRODUCTION) }}</span></td>
                <td><span class="environment-cell state-line" :class="{ missing: !group.endpoints.TEST, attention: group.endpoints.TEST && group.endpoints.TEST.verificationStatus !== 'VERIFIED' }"><Circle :size="8" fill="currentColor" />{{ endpointState(group.endpoints.TEST) }}</span></td>
                <td><span class="single-line endpoint-url" :title="displayServiceUrl(group.endpoints.TEST)">{{ displayServiceUrl(group.endpoints.TEST) }}</span></td>
                <td><span class="credential-status" :class="{ missing: !primaryEndpoint(group)?.credentialConfigured }"><KeyRound :size="14" />{{ primaryEndpoint(group)?.credentialConfigured ? '已配置' : '未配置' }}</span></td>
                <td><span class="single-line" :title="primaryEndpoint(group)?.sourceOrganizationName ?? '—'">{{ primaryEndpoint(group)?.sourceOrganizationName ?? '—' }}</span></td>
                <td><span class="single-line" :title="group.latestEndpoint ? formatLocalDateTime(group.latestEndpoint.updatedAt) : '—'">{{ group.latestEndpoint ? formatLocalDateTime(group.latestEndpoint.updatedAt) : '—' }}</span></td>
                <td><div class="table-actions"><button v-if="canWrite && primaryEndpoint(group)" class="table-action" type="button" @click="openGroupEndpointEdit(group)"><Pencil :size="13" />编辑</button><button v-else-if="canWrite && group.organizationId && selectedSystem?.enabled" class="table-action" type="button" @click="openEndpointCreate(group.organizationId)"><Plus :size="13" />新增配置</button><button v-if="canReadExchange && selectedSystem?.systemCode === 'PRIMARY_HIS' && primaryEndpoint(group)" class="table-action" type="button" @click="openEndpointExchangeRecords(group)"><ClipboardList :size="13" />调用记录</button></div></td>
              </tr>
          </tbody>
        </table>
        <div v-if="filteredEndpointGroups.length === 0" class="prototype-empty">没有符合当前条件的机构接口配置</div>
        </div>
        <AdminPagination v-if="endpointGroups.length" :total="filteredEndpointGroups.length" :page="page" :page-size="pageSize" @update:page="page = $event" @update:page-size="pageSize = $event" />
      </section>
    </template>
    <ExternalSystemEditorDrawer v-if="systemEditorOpen" v-model:form="systemForm" :creating="!systemForm.version" :can-write="canWrite" :is-saving="isSaving" :error="operationError" :form-error="formError" @close="closeSystemEditor" @submit="submitSystem" @reload="reloadSystemEditor" />
    <ExternalEndpointEditorDrawer v-if="endpointEditorOpen && selectedSystem" v-model:form="endpointForm" :system="selectedSystem" :existing="selectedEndpoint" :organizations="endpointOrganizationOptions" :can-write="canWrite" :is-saving="isSaving" :error="operationError" :form-error="formError" @close="closeEndpointEditor" @submit="submitEndpoint" @reload="reloadEndpointEditor" />
  </section>
</template>

<style scoped>
.external-system-page { display: grid; gap: 16px; }
.first-load-state { min-height: 220px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: #33745c; }
.first-system-empty,.endpoint-empty { min-height: 340px; padding: 56px 24px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; color: #177565; text-align: center; }
.first-system-empty h2,.endpoint-empty h2 { margin: 2px 0 0; color: #20333e; font-size: 20px; line-height: 1.35; }
.first-system-empty p,.endpoint-empty p { max-width: 540px; margin: 0 0 10px; color: #6c7d86; font-size: 13px; line-height: 1.75; }
.endpoint-empty { min-height: 360px; }
.system-context { min-height: 82px; padding: 16px 18px; border: 1px solid #dce4e6; border-radius: 6px; background: #fff; display: grid; grid-template-columns: auto minmax(280px, 440px) auto minmax(0, 1fr); align-items: center; gap: 14px; box-shadow: 0 1px 2px #18243308; }
.system-context > strong { color: #263a43; font-size: 13px; white-space: nowrap; }
.system-selector { min-height: 42px; padding: 0 12px; border: 1px solid #cdd9dd; border-radius: 5px; display: flex; align-items: center; gap: 9px; color: #147467; }
.system-selector select { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: #20333e; font-size: 14px; font-weight: 650; }
.system-state { display: inline-flex; align-items: center; gap: 6px; color: #158351; font-size: 12px; font-weight: 650; white-space: nowrap; }
.system-state.disabled { color: #77858c; }
.context-actions { min-width: 0; display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.context-edit { border-color: #7dafaa; color: #176f63; }
.connection-matrix { min-height: 520px; }
.status-filter { display: inline-flex; align-items: center; gap: 8px; color: #4b6069; font-size: 12px; white-space: nowrap; }
.status-filter select { min-height: 36px; padding: 0 32px 0 11px; border: 1px solid #ced8dc; border-radius: 4px; background: white; color: #455b65; }
.incomplete-filter { display: inline-flex; align-items: center; gap: 7px; color: #4b6069; font-size: 12px; white-space: nowrap; }
.incomplete-filter input { width: 15px; height: 15px; accent-color: #147467; }
.matrix-table-wrap { overflow-x: auto; }
.matrix-table { min-width: 1830px; table-layout: fixed; }
.matrix-table .org-column { width: 210px; }.matrix-table .org-code-column { width: 255px; }.matrix-table .state-column { width: 110px; }.matrix-table .url-column { width: 260px; }.matrix-table .credential-column { width: 105px; }.matrix-table .source-column { width: 230px; }.matrix-table .updated-column { width: 150px; }.matrix-table .action-column { width: 140px; }
.matrix-table th,.matrix-table td { overflow: hidden; }
.matrix-table td { height: 50px; padding-top: 8px; padding-bottom: 8px; white-space: nowrap; }
.matrix-table th:last-child,.matrix-table td:last-child { text-align: right; }
.environment-cell { min-width: 0; color: #16824f; }
.environment-cell.missing,.environment-cell.attention { color: #7d8b91; }
.state-line { display: inline-flex; align-items: center; gap: 7px; font-weight: 650; }
.endpoint-url { color: #70828b; font-size: 11px; }
.credential-status { display: inline-flex; align-items: center; gap: 6px; color: #17765e; font-weight: 650; }
.credential-status.missing { color: #bb613e; }
.table-actions { display: flex; justify-content: flex-end; align-items: center; gap: 10px; }.table-action { padding: 4px 0; border: 0; background: transparent; color: #147467; display: inline-flex; align-items: center; gap: 5px; font-weight: 650; white-space: nowrap; }
.single-line { min-width: 0; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.page-state { min-height: 390px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #33745c; text-align: center; }
.page-state span { max-width: 420px; color: #75828c; font-size: 11px; line-height: 1.6; }
.feedback.page-error { margin-bottom: 0; }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media(max-width:1100px){.system-context{grid-template-columns:minmax(260px,1fr) auto}.system-context>strong,.system-state{display:none}.context-actions{grid-column:auto;justify-content:flex-end}}
@media(max-width:700px){.first-system-empty,.endpoint-empty{min-height:300px;padding:42px 20px}.system-context{grid-template-columns:1fr}.context-actions{grid-column:auto;align-items:stretch;flex-direction:column}}
</style>
