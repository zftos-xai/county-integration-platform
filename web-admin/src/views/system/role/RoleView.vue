<!-- 角色权限页面：接入真实角色、代码注册权限和并发更新接口。 -->
<script setup lang="ts">
import { AlertCircle, Check, ChevronRight, KeyRound, LoaderCircle, Pencil, Plus, RefreshCw, Search, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createRole, getRole, listPermissions, listRoles, replaceRolePermissions, updateRole,
} from '@/api/system/role'
import type { Permission, Role } from '@/api/system/role'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'
import RoleEditorDrawer from './components/RoleEditorDrawer.vue'
import { emptyRoleForm, roleToForm, toCreateRoleInput, toUpdateRoleInput, validateRoleForm } from './form'

type EditorMode = 'closed' | 'view' | 'create' | 'edit'

const router = useRouter()
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const isLoading = ref(true)
const isRefreshing = ref(false)
const isSaving = ref(false)
const isDetailLoading = ref(false)
const error = ref<ApiClientError | null>(null)
const operationError = ref<ApiClientError | null>(null)
const notice = ref('')
const query = ref('')
const statusFilter = ref<'all' | 'enabled' | 'disabled'>('all')
const mode = ref<EditorMode>('closed')
const selected = ref<Role | null>(null)
const form = ref(emptyRoleForm())
const permissionDraft = ref<string[]>([])
const formError = ref('')
const confirmStatusId = ref<number | null>(null)
const statusSavingId = ref<number | null>(null)
const editorSnapshot = ref('')
let pageController: AbortController | null = null
let detailController: AbortController | null = null
let isMounted = true

const canWrite = computed(() => hasPermission('access:write'))
const filteredRoles = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  return roles.value.filter(role => {
    if (statusFilter.value === 'enabled' && !role.enabled) return false
    if (statusFilter.value === 'disabled' && role.enabled) return false
    return !keyword || [role.roleCode, role.roleName].some(value => value.toLocaleLowerCase('zh-CN').includes(keyword))
  })
})

function asApiError(caught: unknown, message = '角色数据处理失败，请稍后重试') {
  return caught instanceof ApiClientError ? caught : new ApiClientError('UNKNOWN_ERROR', message, 0)
}

async function handleUnauthorized(caught: ApiClientError) {
  if (caught.status !== 401) return false
  authState.user = null
  authState.isInitialized = true
  await router.replace({ path: '/login', query: { redirect: '/roles' } })
  return true
}

function currentEditorSnapshot() {
  return JSON.stringify({ form: form.value, permissions: permissionDraft.value })
}

function captureEditorSnapshot() {
  editorSnapshot.value = currentEditorSnapshot()
}

function closeEditor() {
  if (isSaving.value) return
  if (currentEditorSnapshot() !== editorSnapshot.value && !window.confirm('当前修改尚未保存，确定关闭吗？')) return
  detailController?.abort()
  mode.value = 'closed'
  selected.value = null
  operationError.value = null
  formError.value = ''
}

function replaceRole(updated: Role) {
  const index = roles.value.findIndex(item => item.id === updated.id)
  if (index === -1) roles.value.push(updated)
  else roles.value[index] = updated
}

function applySelected(role: Role) {
  selected.value = role
  form.value = roleToForm(role)
  permissionDraft.value = [...role.permissionCodes]
  replaceRole(role)
  captureEditorSnapshot()
}

async function loadPage(isBackground = false) {
  pageController?.abort()
  const controller = new AbortController()
  pageController = controller
  if (isBackground) isRefreshing.value = true
  else isLoading.value = true
  error.value = null
  try {
    const [roleRows, permissionRows] = await Promise.all([
      listRoles(controller.signal),
      listPermissions(controller.signal),
    ])
    if (controller.signal.aborted || !isMounted) return
    roles.value = roleRows
    permissions.value = permissionRows
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取角色权限数据')
    if (apiError.code === 'REQUEST_ABORTED') return
    if (!await handleUnauthorized(apiError)) error.value = apiError
  } finally {
    if (pageController === controller && isMounted) {
      isLoading.value = false
      isRefreshing.value = false
    }
  }
}

function openCreate() {
  selected.value = null
  form.value = emptyRoleForm()
  permissionDraft.value = []
  operationError.value = null
  formError.value = ''
  mode.value = 'create'
  captureEditorSnapshot()
}

async function openRole(role: Role, isEdit = false) {
  applySelected(role)
  operationError.value = null
  formError.value = ''
  mode.value = isEdit && canWrite.value && !role.systemManaged ? 'edit' : 'view'
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  isDetailLoading.value = true
  try {
    const latest = await getRole(role.id, controller.signal)
    if (controller.signal.aborted || !isMounted || selected.value?.id !== role.id) return
    applySelected(latest)
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取角色详情')
    if (apiError.code === 'REQUEST_ABORTED') return
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    if (detailController === controller && isMounted) isDetailLoading.value = false
  }
}

async function submitBase() {
  const isCreating = mode.value === 'create'
  formError.value = validateRoleForm(form.value, isCreating) ?? ''
  if (formError.value || (!isCreating && mode.value !== 'edit') || isSaving.value) return
  isSaving.value = true
  operationError.value = null
  try {
    const updated = isCreating
      ? await createRole(toCreateRoleInput(form.value))
      : await updateRole(selected.value!.id, toUpdateRoleInput(form.value))
    applySelected(updated)
    mode.value = 'view'
    notice.value = isCreating ? '角色已创建，可继续分配功能权限。' : '角色基础信息已保存。'
  } catch (caught) {
    const apiError = asApiError(caught)
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    isSaving.value = false
  }
}

async function savePermissions() {
  if (!selected.value || selected.value.systemManaged || !canWrite.value || isSaving.value) return
  isSaving.value = true
  operationError.value = null
  try {
    const updated = await replaceRolePermissions(selected.value.id, permissionDraft.value)
    applySelected(updated)
    notice.value = '角色功能权限已更新；使用该角色的旧会话将在下一次请求时失效。'
  } catch (caught) {
    const apiError = asApiError(caught, '无法保存角色权限')
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    isSaving.value = false
  }
}

async function changeStatus(role: Role) {
  if (!canWrite.value || role.systemManaged || statusSavingId.value !== null) return
  if (confirmStatusId.value !== role.id) {
    confirmStatusId.value = role.id
    return
  }
  confirmStatusId.value = null
  statusSavingId.value = role.id
  error.value = null
  try {
    const updated = await updateRole(role.id, { roleName: role.roleName, enabled: !role.enabled, version: role.version })
    replaceRole(updated)
    notice.value = `${updated.roleName}已${updated.enabled ? '启用' : '停用'}。`
  } catch (caught) {
    const apiError = asApiError(caught, '无法修改角色状态')
    if (!await handleUnauthorized(apiError)) {
      if (apiError.status === 409) await loadPage(true)
      error.value = apiError
    }
  } finally {
    statusSavingId.value = null
  }
}

function formatTime(value: string) {
  const instant = new Date(value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`)
  if (Number.isNaN(instant.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(instant)
}

onMounted(() => loadPage())
onBeforeUnmount(() => {
  isMounted = false
  pageController?.abort()
  detailController?.abort()
})
</script>

<template>
  <section class="content role-page">
    <div v-if="notice" class="feedback success" role="status"><Check :size="18" /><span>{{ notice }}</span><button aria-label="关闭提示" @click="notice = ''"><X :size="16" /></button></div>
    <div v-if="error && !isLoading" class="feedback danger" role="alert"><AlertCircle :size="19" /><span><strong>{{ error.message }}</strong><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button class="prototype-text-button" type="button" :disabled="isRefreshing" @click="loadPage(true)"><RefreshCw :size="15" />重试</button></div>

    <div class="work-toolbar">
      <label class="prototype-search"><Search :size="16" /><input v-model="query" type="search" placeholder="角色名称或代码" aria-label="搜索角色" /></label>
      <span>{{ filteredRoles.length }} / {{ roles.length }} 个角色</span>
      <select v-model="statusFilter" aria-label="角色状态"><option value="all">全部状态</option><option value="enabled">已启用</option><option value="disabled">已停用</option></select>
      <button class="work-quiet-button" type="button" :disabled="isRefreshing" @click="loadPage(true)"><RefreshCw :size="15" :class="{ spinning: isRefreshing }" />刷新</button>
      <button v-if="canWrite" class="prototype-button" type="button" @click="openCreate"><Plus :size="15" />新增角色</button>
    </div>

    <section class="prototype-section work-table-section role-panel">
      <div v-if="isLoading" class="page-state" aria-live="polite"><LoaderCircle class="spinning" :size="28" /><strong>正在加载角色权限</strong></div>
      <div v-else-if="!error && roles.length === 0" class="page-state"><KeyRound :size="30" /><strong>平台暂无角色</strong></div>
      <div v-else-if="roles.length" class="prototype-table-wrap">
        <table class="work-table role-table">
          <thead><tr><th>角色</th><th>类型</th><th>功能权限</th><th>状态</th><th>最近更新</th><th><span class="visually-hidden">操作</span></th></tr></thead>
          <tbody><tr v-for="role in filteredRoles" :key="role.id">
            <td><button class="work-row-link" type="button" @click="openRole(role)">{{ role.roleName }}</button><small>{{ role.roleCode }}</small></td>
            <td><span class="prototype-tag" :class="role.systemManaged ? 'warning' : 'neutral'">{{ role.systemManaged ? '系统保护' : '自定义' }}</span></td>
            <td>{{ role.permissionCodes.length }} 项<small>{{ role.permissionCodes.slice(0, 3).join('、') || '未分配权限' }}{{ role.permissionCodes.length > 3 ? '…' : '' }}</small></td>
            <td><span class="prototype-tag" :class="role.enabled ? 'success' : 'neutral'">{{ role.enabled ? '已启用' : '已停用' }}</span></td>
            <td>{{ formatTime(role.updatedAt) }}</td>
            <td class="role-actions"><button v-if="canWrite && !role.systemManaged" class="prototype-icon" type="button" aria-label="编辑角色" title="编辑" @click="openRole(role, true)"><Pencil :size="15" /></button><button v-if="canWrite && !role.systemManaged" class="status-action" :class="{ confirm: confirmStatusId === role.id }" type="button" :disabled="statusSavingId !== null" @blur="confirmStatusId = null" @click="changeStatus(role)">{{ statusSavingId === role.id ? '处理中…' : confirmStatusId === role.id ? `确认${role.enabled ? '停用' : '启用'}` : role.enabled ? '停用' : '启用' }}</button><button v-else class="prototype-icon" type="button" aria-label="查看角色详情" title="查看详情" @click="openRole(role)"><ChevronRight :size="16" /></button></td>
          </tr></tbody>
        </table>
        <div v-if="filteredRoles.length === 0" class="prototype-empty">没有符合当前条件的角色</div>
      </div>
    </section>

    <RoleEditorDrawer v-if="mode !== 'closed'" v-model:form="form" v-model:permission-draft="permissionDraft" :mode="mode" :selected="selected" :permissions="permissions" :can-write="canWrite" :is-saving="isSaving" :is-detail-loading="isDetailLoading" :operation-error="operationError" :form-error="formError" @close="closeEditor" @submit-base="submitBase" @save-permissions="savePermissions" @edit="mode = 'edit'; captureEditorSnapshot()" />
  </section>
</template>

<style scoped>
.role-page { color: #263341; }
.role-panel { min-height: 390px; }
.page-state { min-height: 310px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #55766d; }
.role-table { min-width: 850px; }
.role-table td small { display: block; margin-top: 3px; color: #7c8993; font-size: 10px; }
.role-actions { display: flex; justify-content: flex-end; gap: 6px; }
.status-action { min-height: 32px; padding: 0 9px; border: 1px solid #ccd7da; border-radius: 5px; background: white; color: #53636b; font-size: 11px; white-space: nowrap; }
.status-action.confirm { border-color: #a94b42; background: #a94b42; color: white; }
.feedback { min-height: 46px; margin: 0 0 12px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 10px; font-size: 12px; }
.feedback > span { min-width: 0; display: grid; gap: 2px; }
.feedback > button:last-child { margin-left: auto; }
.feedback.success { border-color: #bcd9ca; background: #edf7f2; color: #246b4f; }
.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }
.feedback.success > button { border: 0; background: transparent; color: inherit; }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spinning { animation: none; } }
</style>
