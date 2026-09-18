<!-- 外部系统管理页面：使用真实API维护系统身份及按环境、机构隔离的接口配置。 -->
<script setup lang="ts">
import {
  AlertCircle, ChevronDown, ChevronRight, Circle, KeyRound, LoaderCircle, Pencil,
  Plus, RefreshCw, Search, ServerCog, Settings2,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createExternalEndpoint, createExternalSystem, listExternalEndpoints, listExternalSystems,
  updateExternalEndpoint, updateExternalSystem,
} from '@/api/system/configuration'
import type { ExternalEndpoint, ExternalSystem, ParameterEnvironment } from '@/api/system/configuration'
import { listOrganizations } from '@/api/system/organization'
import type { Organization } from '@/api/system/organization'
import AdminPagination from '@/components/AdminPagination.vue'
import AuditAwareSuccess from '@/components/AuditAwareSuccess.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { authState, hasPermission } from '@/store/modules/auth'
import { environmentLabel, formatLocalDateTime } from '@/utils/managementDisplay'
import { ApiClientError, asUncertainWriteError } from '@/utils/request'
import ExternalEndpointEditorDrawer from './components/ExternalEndpointEditorDrawer.vue'
import ExternalSystemEditorDrawer from './components/ExternalSystemEditorDrawer.vue'
import {
  emptyExternalEndpointForm, emptyExternalSystemForm, externalEndpointToForm, externalSystemToForm,
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
const endpointStatus = ref<'all' | 'enabled' | 'disabled'>('all')
const onlyIncomplete = ref(false)
const expandedOrganizationKey = ref<string | null>(null)
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
  latestEndpoint: ExternalEndpoint
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
  endpoints.value.forEach(endpoint => {
    const key = endpointGroupKey(endpoint)
    const existing = groups.get(key)
    if (existing) {
      existing.endpoints[endpoint.environment] = endpoint
      if (endpoint.updatedAt > existing.latestEndpoint.updatedAt) existing.latestEndpoint = endpoint
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
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  return endpointGroups.value.filter(group => {
    const configured = Object.values(group.endpoints)
    if (endpointStatus.value === 'enabled' && !configured.some(endpoint => endpoint.enabled)) return false
    if (endpointStatus.value === 'disabled' && configured.some(endpoint => endpoint.enabled)) return false
    if (onlyIncomplete.value && group.endpoints.PRODUCTION && group.endpoints.TEST
      && configured.every(endpoint => endpoint.credentialConfigured)) return false
    return !keyword || [group.organizationName, group.organizationCode]
      .some(value => value.toLocaleLowerCase('zh-CN').includes(keyword))
  })
})
const { page, pageSize, pagedRows } = useClientPagination(filteredEndpointGroups)

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
    const keys = new Set(rows.map(endpointGroupKey))
    if (!expandedOrganizationKey.value || !keys.has(expandedOrganizationKey.value)) {
      expandedOrganizationKey.value = rows[0] ? endpointGroupKey(rows[0]) : null
    }
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
  selectedSystem.value = system; endpoints.value = []; expandedOrganizationKey.value = null; error.value = null
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
function openEndpointCreate() {
  if (!selectedSystem.value) return
  endpointForm.value = emptyExternalEndpointForm(endpointOrganizationOptions.value[0]?.id ?? null)
  selectedEndpoint.value = null; operationError.value = null; formError.value = ''
  editorSnapshot.value = JSON.stringify(endpointForm.value); endpointEditorOpen.value = true
}

/** 打开机构接口配置编辑抽屉。 */
function openEndpointEdit(endpoint: ExternalEndpoint) {
  endpointForm.value = externalEndpointToForm(endpoint); selectedEndpoint.value = endpoint
  operationError.value = null; formError.value = ''; editorSnapshot.value = JSON.stringify(endpointForm.value)
  endpointEditorOpen.value = true
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
  if (JSON.stringify(endpointForm.value) !== editorSnapshot.value && !window.confirm('当前接口配置尚未保存，确定关闭吗？')) return
  endpointEditorOpen.value = false; selectedEndpoint.value = null
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
    const saved = selectedEndpoint.value
      ? await updateExternalEndpoint(selectedEndpoint.value.id, toUpdateExternalEndpointInput(endpointForm.value))
      : await createExternalEndpoint(system.id, toCreateExternalEndpointInput(endpointForm.value))
    const index = endpoints.value.findIndex(item => item.id === saved.id)
    if (index < 0) endpoints.value.push(saved); else endpoints.value[index] = saved
    endpoints.value.sort((left, right) => left.environment.localeCompare(right.environment)
      || (left.organizationCode ?? '').localeCompare(right.organizationCode ?? ''))
    notice.value = `机构接口配置已保存，当前状态为${saved.enabled ? '启用' : '停用'}。`
    auditTarget.value = { targetType: 'EXTERNAL_ENDPOINT', targetId: String(saved.id) }
    endpointEditorOpen.value = false; selectedEndpoint.value = null
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

/** 切换机构接口配置的展开详情。 */
function toggleOrganization(group: EndpointGroup) {
  expandedOrganizationKey.value = expandedOrganizationKey.value === group.key ? null : group.key
}

/** 返回一条接口配置的简短状态文案。 */
function endpointState(endpoint?: ExternalEndpoint) {
  if (!endpoint) return '未配置'
  return endpoint.enabled ? '已启用' : '已停用'
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
          <button v-if="canWrite && selectedSystem" class="prototype-button" type="button" :disabled="!selectedSystem.enabled" @click="openEndpointCreate"><Plus :size="15" />新增机构接口配置</button>
        </div>
      </section>

      <section class="prototype-section connection-matrix">
        <div v-if="endpointGroups.length" class="matrix-toolbar">
          <label class="prototype-search"><Search :size="16" /><input v-model="query" type="search" placeholder="搜索机构名称或编码" aria-label="搜索机构接口配置" /></label>
          <label class="incomplete-filter"><input v-model="onlyIncomplete" type="checkbox" />仅看缺失配置</label>
          <select v-model="endpointStatus" aria-label="接口配置状态"><option value="all">全部状态</option><option value="enabled">存在已启用配置</option><option value="disabled">无已启用配置</option></select>
          <span class="matrix-count">共 {{ filteredEndpointGroups.length }} 个机构</span>
          <button class="prototype-icon" type="button" aria-label="刷新接口配置" :disabled="isRefreshing" @click="loadPage(true)"><RefreshCw :size="16" :class="{ spinning: isRefreshing }" /></button>
        </div>

        <div v-if="isEndpointLoading" class="page-state"><LoaderCircle class="spinning" :size="28" /><strong>正在加载机构接口配置</strong></div>
        <div v-else-if="selectedSystem && endpointGroups.length === 0" class="endpoint-empty"><ServerCog :size="32" aria-hidden="true" /><h2>还没有机构接口配置</h2><p>请先为 {{ selectedSystem.systemName }} 添加一家机构的接口配置。</p><button v-if="canWrite && selectedSystem.enabled" class="prototype-button" type="button" @click="openEndpointCreate"><Plus :size="15" />新增机构接口配置</button></div>
        <div v-else class="prototype-table-wrap matrix-table-wrap">
        <table class="work-table matrix-table">
          <colgroup><col class="org-column" /><col class="environment-column" /><col class="environment-column" /><col class="credential-column" /><col class="updated-column" /><col class="action-column" /></colgroup>
          <thead><tr><th>机构</th><th>生产环境</th><th>测试环境</th><th>接入信息</th><th>最后更新</th><th>操作</th></tr></thead>
          <tbody>
            <template v-for="group in pagedRows" :key="group.key">
              <tr class="organization-row" :class="{ expanded: expandedOrganizationKey === group.key }">
                <td><button class="organization-toggle" type="button" :aria-expanded="expandedOrganizationKey === group.key" @click="toggleOrganization(group)"><ChevronDown v-if="expandedOrganizationKey === group.key" :size="16" /><ChevronRight v-else :size="16" /><span class="single-line" :title="group.organizationName"><strong>{{ group.organizationName }}</strong><small>{{ group.organizationCode }}</small></span></button></td>
                <td><div class="environment-cell" :class="{ missing: !group.endpoints.PRODUCTION, disabled: group.endpoints.PRODUCTION && !group.endpoints.PRODUCTION.enabled }"><span class="state-line"><Circle :size="8" fill="currentColor" />{{ endpointState(group.endpoints.PRODUCTION) }}</span><small class="single-line" :title="group.endpoints.PRODUCTION?.baseUrl">{{ group.endpoints.PRODUCTION?.baseUrl ?? '—' }}</small></div></td>
                <td><div class="environment-cell" :class="{ missing: !group.endpoints.TEST, disabled: group.endpoints.TEST && !group.endpoints.TEST.enabled }"><span class="state-line"><Circle :size="8" fill="currentColor" />{{ endpointState(group.endpoints.TEST) }}</span><small class="single-line" :title="group.endpoints.TEST?.baseUrl">{{ group.endpoints.TEST?.baseUrl ?? '—' }}</small></div></td>
                <td><span class="credential-status" :class="{ missing: !primaryEndpoint(group).credentialConfigured }"><KeyRound :size="14" />{{ primaryEndpoint(group).credentialConfigured ? '已配置' : '未配置' }}</span></td>
                <td><span class="single-line" :title="formatLocalDateTime(group.latestEndpoint.updatedAt)">{{ formatLocalDateTime(group.latestEndpoint.updatedAt) }}</span></td>
                <td><button v-if="canWrite" class="table-action" type="button" @click="openEndpointEdit(primaryEndpoint(group))"><Pencil :size="13" />编辑</button></td>
              </tr>
              <tr v-if="expandedOrganizationKey === group.key" class="endpoint-detail-row">
                <td colspan="6"><div class="endpoint-detail"><div><span>运行环境</span><strong>{{ environmentLabel(primaryEndpoint(group).environment) }}</strong></div><div class="detail-url"><span>接口地址</span><strong class="single-line" :title="primaryEndpoint(group).baseUrl">{{ primaryEndpoint(group).baseUrl }}</strong></div><div><span>接入信息</span><strong>{{ primaryEndpoint(group).credentialConfigured ? '已填写' : '未填写' }}</strong></div><div><span>超时设置</span><strong>{{ primaryEndpoint(group).connectTimeoutMs }} / {{ primaryEndpoint(group).readTimeoutMs }} ms</strong></div><div><span>更新时间</span><strong>{{ formatLocalDateTime(primaryEndpoint(group).updatedAt) }}</strong></div><button v-if="canWrite" class="work-quiet-button" type="button" @click="openEndpointEdit(primaryEndpoint(group))"><Pencil :size="14" />编辑配置</button></div></td>
              </tr>
            </template>
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
.matrix-toolbar { min-height: 64px; padding: 12px 14px; border-bottom: 1px solid #e6ebed; display: flex; align-items: center; gap: 10px; }
.matrix-toolbar .prototype-search { width: min(330px, 36vw); }
.matrix-toolbar select { min-height: 36px; margin-left: auto; padding: 0 34px 0 11px; border: 1px solid #ced8dc; border-radius: 4px; background: white; color: #455b65; }
.incomplete-filter { display: inline-flex; align-items: center; gap: 7px; color: #4b6069; font-size: 12px; white-space: nowrap; }
.incomplete-filter input { width: 15px; height: 15px; accent-color: #147467; }
.matrix-count { color: #718189; font-size: 11px; white-space: nowrap; }
.matrix-table-wrap { overflow-x: auto; }
.matrix-table { min-width: 1080px; table-layout: fixed; }
.matrix-table .org-column { width: 23%; }.matrix-table .environment-column { width: 20%; }.matrix-table .credential-column { width: 13%; }.matrix-table .updated-column { width: 15%; }.matrix-table .action-column { width: 9%; }
.matrix-table th,.matrix-table td { overflow: hidden; }
.matrix-table td { height: 72px; padding-top: 10px; padding-bottom: 10px; white-space: nowrap; }
.matrix-table td:last-child { text-align: left; }
.organization-row.expanded td { background: #f1faf7; border-bottom-color: #cfe7df; }
.organization-toggle { width: 100%; min-width: 0; padding: 0; border: 0; background: transparent; color: #1d343d; display: flex; align-items: center; gap: 8px; text-align: left; cursor: pointer; }
.organization-toggle > span { min-width: 0; }
.organization-toggle strong { display: block; overflow: hidden; text-overflow: ellipsis; }
.organization-toggle small,.environment-cell small { display: block; margin-top: 5px; color: #70828b; font-size: 10px; overflow: hidden; text-overflow: ellipsis; }
.environment-cell { min-width: 0; color: #16824f; }
.environment-cell.missing,.environment-cell.disabled { color: #7d8b91; }
.state-line { display: flex; align-items: center; gap: 7px; font-weight: 650; }
.credential-status { display: inline-flex; align-items: center; gap: 6px; color: #17765e; font-weight: 650; }
.credential-status.missing { color: #bb613e; }
.table-action { padding: 4px 0; border: 0; background: transparent; color: #147467; display: inline-flex; align-items: center; gap: 5px; font-weight: 650; white-space: nowrap; }
.endpoint-detail-row td { height: auto; padding: 0 14px 12px; background: #f1faf7; }
.endpoint-detail { min-width: 980px; padding: 14px; border: 1px solid #d8e7e2; border-radius: 5px; background: white; display: grid; grid-template-columns: 100px minmax(240px, 1.5fr) minmax(170px, 1fr) 150px 165px auto; align-items: end; gap: 16px; }
.endpoint-detail > div { min-width: 0; display: grid; gap: 5px; }
.endpoint-detail span { color: #718189; font-size: 10px; white-space: nowrap; }
.endpoint-detail strong { color: #324851; font-size: 11px; font-weight: 600; }
.single-line { min-width: 0; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.page-state { min-height: 390px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #33745c; text-align: center; }
.page-state span { max-width: 420px; color: #75828c; font-size: 11px; line-height: 1.6; }
.feedback.page-error { margin-bottom: 0; }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media(max-width:1100px){.system-context{grid-template-columns:minmax(260px,1fr) auto}.system-context>strong,.system-state{display:none}.context-actions{grid-column:auto;justify-content:flex-end}.matrix-toolbar .prototype-search{width:min(280px,32vw)}}
@media(max-width:700px){.first-system-empty,.endpoint-empty{min-height:300px;padding:42px 20px}.system-context{grid-template-columns:1fr}.context-actions{grid-column:auto;align-items:stretch;flex-direction:column}.matrix-toolbar{align-items:stretch;flex-direction:column}.matrix-toolbar .prototype-search{width:100%}.matrix-toolbar select{margin-left:0}.matrix-count{margin-left:0}.matrix-toolbar .prototype-icon{align-self:flex-end}}
</style>
