<!-- 机构管理页面：接入真实列表、详情、创建、修改和启停接口。 -->
<script setup lang="ts">
import {
  AlertCircle, Building2, Check, ChevronRight, CircleOff, LoaderCircle,
  Pencil, Plus, RefreshCw, Search, X,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiClientError } from '@/utils/request'
import OrganizationEditorDrawer from './components/OrganizationEditorDrawer.vue'
import {
  createOrganization, getOrganization, listOrganizations,
  setOrganizationEnabled, updateOrganization,
} from '@/api/system/organization'
import type { Organization } from '@/api/system/organization'
import { authState, hasPermission } from '@/store/modules/auth'
import {
  emptyOrganizationForm, organizationToForm, toCreateInput, toUpdateInput,
  validateOrganizationForm,
} from './form'

type EditorMode = 'closed' | 'view' | 'create' | 'edit'

// 页面状态按列表、编辑器和操作反馈分组，服务端错误保留 requestId 供追踪。
const router = useRouter()
const organizations = ref<Organization[]>([])
const isLoading = ref(true)
const isRefreshing = ref(false)
const error = ref<ApiClientError | null>(null)
const notice = ref('')
const query = ref('')
const statusFilter = ref<'all' | 'enabled' | 'disabled'>('all')
const mode = ref<EditorMode>('closed')
const selected = ref<Organization | null>(null)
const form = ref(emptyOrganizationForm())
const formError = ref('')
const operationError = ref<ApiClientError | null>(null)
const isSaving = ref(false)
const isDetailLoading = ref(false)
const confirmStatusId = ref<number | null>(null)
const statusSavingId = ref<number | null>(null)
const editorSnapshot = ref('')
let listController: AbortController | null = null
let detailController: AbortController | null = null
let isMounted = true

const canWrite = computed(() => hasPermission('organization:write'))
const nameById = computed(() => new Map(organizations.value.map(item => [item.id, item.organizationName])))
const filtered = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  return organizations.value.filter(item => {
    if (statusFilter.value === 'enabled' && !item.enabled) return false
    if (statusFilter.value === 'disabled' && item.enabled) return false
    if (!keyword) return true
    return [item.organizationCode, item.organizationName, item.organizationType]
      .some(value => value.toLocaleLowerCase('zh-CN').includes(keyword))
  })
})
const unavailableParentIds = computed(() => {
  const ids = new Set<number>()
  if (!selected.value) return ids
  ids.add(selected.value.id)
  let changed = true
  while (changed) {
    changed = false
    for (const item of organizations.value) {
      if (item.parentId !== null && ids.has(item.parentId) && !ids.has(item.id)) {
        ids.add(item.id)
        changed = true
      }
    }
  }
  return ids
})
const parentOptions = computed(() => organizations.value
  .filter(item => (item.enabled || item.id === selected.value?.parentId) && !unavailableParentIds.value.has(item.id))
  .sort((a, b) => a.organizationName.localeCompare(b.organizationName, 'zh-CN')))
function captureEditorSnapshot() {
  editorSnapshot.value = JSON.stringify(form.value)
}

function closeEditor() {
  if (isSaving.value) return
  if (JSON.stringify(form.value) !== editorSnapshot.value && !window.confirm('当前修改尚未保存，确定关闭吗？')) return
  detailController?.abort()
  mode.value = 'closed'
  selected.value = null
  operationError.value = null
  formError.value = ''
}

function asApiError(caught: unknown, message = '机构数据处理失败，请稍后重试') {
  return caught instanceof ApiClientError
    ? caught
    : new ApiClientError('UNKNOWN_ERROR', message, 0)
}

async function handleUnauthorized(caught: ApiClientError) {
  if (caught.status !== 401) return false
  authState.user = null
  authState.isInitialized = true
  await router.replace({ path: '/login', query: { redirect: '/organizations' } })
  return true
}

async function loadOrganizations(background = false) {
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  if (background) isRefreshing.value = true
  else isLoading.value = true
  error.value = null
  try {
    const rows = await listOrganizations(undefined, controller.signal)
    if (controller.signal.aborted || !isMounted) return
    organizations.value = rows
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取机构列表')
    if (apiError.code === 'REQUEST_ABORTED') return
    if (!await handleUnauthorized(apiError)) error.value = apiError
  } finally {
    if (listController === controller && isMounted) {
      isLoading.value = false
      isRefreshing.value = false
    }
  }
}

function openCreate() {
  notice.value = ''
  formError.value = ''
  operationError.value = null
  selected.value = null
  form.value = emptyOrganizationForm()
  mode.value = 'create'
  captureEditorSnapshot()
}

async function openOrganization(item: Organization, edit = false) {
  notice.value = ''
  formError.value = ''
  operationError.value = null
  selected.value = item
  form.value = organizationToForm(item)
  mode.value = edit && canWrite.value ? 'edit' : 'view'
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  isDetailLoading.value = true
  try {
    const latest = await getOrganization(item.id, controller.signal)
    if (controller.signal.aborted || !isMounted || selected.value?.id !== item.id) return
    selected.value = latest
    form.value = organizationToForm(latest)
    replaceOrganization(latest)
    captureEditorSnapshot()
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取机构详情')
    if (apiError.code === 'REQUEST_ABORTED') return
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    if (detailController === controller && isMounted) isDetailLoading.value = false
  }
}

function replaceOrganization(updated: Organization) {
  const index = organizations.value.findIndex(item => item.id === updated.id)
  if (index === -1) organizations.value.push(updated)
  else organizations.value[index] = updated
}

async function submit() {
  const creating = mode.value === 'create'
  formError.value = validateOrganizationForm(form.value, creating) ?? ''
  if (creating && !form.value.parentId) formError.value = '请选择可访问的上级机构'
  if (formError.value || (mode.value !== 'create' && mode.value !== 'edit')) return
  if (isSaving.value) return
  isSaving.value = true
  operationError.value = null
  try {
    const updated = creating
      ? await createOrganization(toCreateInput(form.value))
      : await updateOrganization(selected.value!.id, toUpdateInput(form.value))
    if (creating) {
      authState.user = null
      authState.isInitialized = true
      await router.replace({
        path: '/login',
        query: { redirect: '/organizations', scopeChanged: '1' },
      })
      return
    }
    replaceOrganization(updated)
    selected.value = updated
    form.value = organizationToForm(updated)
    captureEditorSnapshot()
    mode.value = 'view'
    notice.value = '机构信息已保存。'
  } catch (caught) {
    const apiError = asApiError(caught)
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    isSaving.value = false
  }
}

async function reloadDetail() {
  if (!selected.value) return
  await openOrganization(selected.value, mode.value === 'edit')
}

async function changeStatus(item: Organization) {
  if (!canWrite.value || statusSavingId.value !== null) return
  if (confirmStatusId.value !== item.id) {
    confirmStatusId.value = item.id
    return
  }
  confirmStatusId.value = null
  statusSavingId.value = item.id
  operationError.value = null
  try {
    const updated = await setOrganizationEnabled(item.id, !item.enabled, item.version)
    replaceOrganization(updated)
    if (selected.value?.id === updated.id) {
      selected.value = updated
      form.value = organizationToForm(updated)
    }
    notice.value = `${updated.organizationName}已${updated.enabled ? '启用' : '停用'}。`
  } catch (caught) {
    const apiError = asApiError(caught, '无法修改机构状态')
    if (!await handleUnauthorized(apiError)) {
      if (apiError.status === 409) await loadOrganizations(true)
      error.value = apiError
    }
  } finally {
    statusSavingId.value = null
  }
}

function formatTime(value: string | null) {
  if (!value) return '未设置'
  const instant = new Date(value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`)
  if (Number.isNaN(instant.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(instant)
}

onMounted(() => loadOrganizations())
onBeforeUnmount(() => {
  isMounted = false
  listController?.abort()
  detailController?.abort()
})
</script>

<template>
  <section class="content organization-page">
    <div v-if="notice" class="feedback success" role="status"><Check :size="18" /><span>{{ notice }}</span><button aria-label="关闭提示" @click="notice = ''"><X :size="16" /></button></div>
    <div v-if="error && !isLoading" class="feedback danger" role="alert">
      <AlertCircle :size="19" /><span><strong>{{ error.message }}</strong><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span>
      <button class="text-button" type="button" :disabled="isRefreshing" @click="loadOrganizations(true)"><RefreshCw :size="15" />重试</button>
    </div>

    <div class="work-toolbar organization-work-toolbar">
      <label class="prototype-search"><Search :size="16" /><input v-model="query" type="search" placeholder="机构名称、编码或类型" aria-label="搜索机构" /></label>
      <span>{{ filtered.length }} / {{ organizations.length }} 个机构</span>
      <label class="status-field"><span class="visually-hidden">状态</span><select v-model="statusFilter" aria-label="机构状态"><option value="all">全部状态</option><option value="enabled">已启用</option><option value="disabled">已停用</option></select></label>
      <button class="work-quiet-button" type="button" :disabled="isRefreshing" @click="loadOrganizations(true)"><RefreshCw :size="15" :class="{ spinning: isRefreshing }" />刷新</button>
      <button v-if="canWrite" class="prototype-button" type="button" @click="openCreate"><Plus :size="15" />新增机构</button>
    </div>

    <section class="organization-panel prototype-section work-table-section">

      <div v-if="isLoading" class="page-state" aria-live="polite"><LoaderCircle class="spinning" :size="28" /><strong>正在加载机构数据</strong><span>请稍候</span></div>
      <div v-else-if="!error && organizations.length === 0" class="page-state"><Building2 :size="30" /><strong>当前范围内暂无机构</strong><span>{{ canWrite ? '可新增下级机构，或联系管理员核对机构范围。' : '请联系管理员核对账号的机构范围。' }}</span></div>
      <div v-else-if="organizations.length" class="table-scroll">
        <table class="work-table">
          <thead><tr><th>机构</th><th>机构类型</th><th>上级机构</th><th>有效期</th><th>状态</th><th>最近更新</th><th><span class="visually-hidden">操作</span></th></tr></thead>
          <tbody>
            <tr v-for="item in filtered" :key="item.id">
              <td><button class="work-row-link" type="button" @click="openOrganization(item)">{{ item.organizationName }}</button><small>{{ item.organizationCode }}</small></td>
              <td><code>{{ item.organizationType }}</code></td>
              <td>{{ item.parentId === null ? '顶级机构' : nameById.get(item.parentId) ?? `机构 #${item.parentId}` }}</td>
              <td><span class="compact-time">{{ formatTime(item.validFrom) }}<br />至 {{ formatTime(item.validTo) }}</span></td>
              <td><span class="prototype-tag" :class="item.enabled ? 'success' : 'neutral'">{{ item.enabled ? '已启用' : '已停用' }}</span></td>
              <td>{{ formatTime(item.updatedAt) }}</td>
              <td class="row-actions">
                <button v-if="canWrite" class="icon-button" type="button" aria-label="编辑机构" title="编辑" @click="openOrganization(item, true)"><Pencil :size="15" /></button>
                <button v-if="canWrite" class="status-action" :class="{ confirm: confirmStatusId === item.id }" type="button" :disabled="statusSavingId !== null" @blur="confirmStatusId = null" @click="changeStatus(item)">{{ statusSavingId === item.id ? '处理中…' : confirmStatusId === item.id ? `确认${item.enabled ? '停用' : '启用'}` : item.enabled ? '停用' : '启用' }}</button>
                <button v-else class="icon-button" type="button" aria-label="查看机构详情" title="查看详情" @click="openOrganization(item)"><ChevronRight :size="16" /></button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="filtered.length === 0" class="filtered-empty"><CircleOff :size="22" /><span>没有符合当前筛选条件的机构</span></div>
      </div>
    </section>

    <OrganizationEditorDrawer
      v-if="mode !== 'closed'"
      v-model:form="form"
      :mode="mode"
      :selected="selected"
      :parent-options="parentOptions"
      :can-write="canWrite"
      :is-saving="isSaving"
      :is-detail-loading="isDetailLoading"
      :operation-error="operationError"
      :form-error="formError"
      :format-time="formatTime"
      @close="closeEditor"
      @submit="submit"
      @reload="reloadDetail"
      @edit="mode = 'edit'"
    />
  </section>
</template>

<style scoped>
.organization-page { color: #263341; }
.organization-toolbar { min-height: 64px; display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.organization-toolbar h2 { margin: 0; font-size: 19px; }
.organization-toolbar p { margin: 5px 0 0; color: #6b7783; font-size: 12px; }
.text-button, .status-action { min-height: 36px; padding: 0 13px; border-radius: 5px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; font-weight: 600; font-size: 13px; }
.text-button { min-height: 30px; padding: 0 8px; border: 0; background: transparent; color: inherit; }
button:disabled { cursor: wait; opacity: .6; }
.organization-panel { min-height: 390px; }
.organization-work-toolbar > span:first-of-type { margin-left: auto; }
.filter-bar { padding: 13px 15px; border-bottom: 1px solid #e1e6ea; display: flex; align-items: center; gap: 10px; }
.search-field { width: min(380px, 100%); height: 38px; padding: 0 11px; border: 1px solid #cfd7df; border-radius: 5px; display: flex; align-items: center; gap: 8px; color: #71808e; }
.search-field:focus-within { border-color: #5b9b90; box-shadow: 0 0 0 2px rgba(76,155,141,.12); }
.search-field input { min-width: 0; flex: 1; border: 0; outline: 0; color: #263341; }
.status-field { margin-left: auto; display: flex; align-items: center; gap: 7px; color: #65717d; font-size: 12px; }
select { min-height: 38px; border: 1px solid #cfd7df; border-radius: 5px; background: white; color: #263341; padding: 0 10px; }
.table-scroll { overflow-x: auto; }
table { width: 100%; min-width: 980px; border-collapse: collapse; font-size: 12px; }
th { padding: 10px 13px; background: #f6f8fa; color: #63717f; text-align: left; font-weight: 600; white-space: nowrap; }
td { padding: 12px 13px; border-top: 1px solid #e8ecef; vertical-align: middle; }
tbody tr:hover { background: #fafbfd; }
code { padding: 3px 6px; border-radius: 3px; background: #eef2f5; color: #3d5267; font: 12px/1.4 ui-monospace, monospace; }
.record-link { padding: 0; border: 0; background: transparent; color: #116b61; text-align: left; display: grid; gap: 2px; }
.record-link strong { font-size: 13px; }
.record-link small { color: #75818d; }
.compact-time { color: #687580; line-height: 1.65; white-space: nowrap; }
.row-actions { display: flex; justify-content: flex-end; gap: 6px; }
.status-action { min-height: 34px; padding: 0 10px; border: 1px solid #ccd5dd; background: white; color: #52606d; white-space: nowrap; }
.status-action.confirm { border-color: #a54b34; background: #a54b34; color: white; }
.page-state { min-height: 310px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #55718b; }
.page-state span, .filtered-empty { color: #77838e; font-size: 12px; }
.filtered-empty { min-width: 980px; padding: 24px; border-top: 1px solid #e8ecef; display: flex; justify-content: center; align-items: center; gap: 8px; }
.feedback { min-height: 48px; margin: 0 0 12px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 10px; font-size: 12px; }
.feedback > span { min-width: 0; display: grid; gap: 2px; }
.feedback small { display: block; }
.feedback > button:last-child { margin-left: auto; }
.feedback.success { border-color: #bcd9ca; background: #edf7f2; color: #246b4f; }
.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }
.feedback.success > button { border: 0; background: transparent; color: inherit; }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spinning { animation: none; } }
@media (max-width: 700px) {
  .organization-toolbar { align-items: stretch; flex-direction: column; }
  .filter-bar { align-items: stretch; flex-wrap: wrap; }
  .search-field { width: 100%; }
  .status-field { margin-left: 0; }
}
</style>
