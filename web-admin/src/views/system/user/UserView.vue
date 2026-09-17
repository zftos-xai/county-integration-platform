<!-- 用户管理页面：接入真实资料、启停、密码重置、角色和机构范围接口。 -->
<script setup lang="ts">
import {
  AlertCircle, Check, ChevronRight, LoaderCircle, Pencil,
  Plus, RefreshCw, Search, UserRound, X,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listOrganizations } from '@/api/system/organization'
import type { Organization } from '@/api/system/organization'
import { listRoles } from '@/api/system/role'
import type { Role } from '@/api/system/role'
import {
  createUser, getUser, listUsers, replaceUserOrganizationScopes,
  replaceUserRoles, resetUserPassword, setUserEnabled, updateUser,
} from '@/api/system/user'
import type { ManagedUser } from '@/api/system/user'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'
import UserEditorDrawer from './components/UserEditorDrawer.vue'
import {
  emptyUserForm, toCreateUserInput, toUpdateUserInput, userToForm,
  validateTemporaryPassword, validateUserForm,
} from './form'

type EditorMode = 'closed' | 'view' | 'create' | 'edit'

// 角色与机构范围使用独立草稿，避免基本资料保存时意外扩大授权。
const router = useRouter()
const users = ref<ManagedUser[]>([])
const organizations = ref<Organization[]>([])
const roles = ref<Role[]>([])
const isLoading = ref(true)
const isRefreshing = ref(false)
const error = ref<ApiClientError | null>(null)
const operationError = ref<ApiClientError | null>(null)
const notice = ref('')
const query = ref('')
const statusFilter = ref<'all' | 'enabled' | 'disabled'>('all')
const mode = ref<EditorMode>('closed')
const selected = ref<ManagedUser | null>(null)
const form = ref(emptyUserForm())
const roleDraft = ref<number[]>([])
const scopeDraft = ref<number[]>([])
const formError = ref('')
const isSaving = ref(false)
const isDetailLoading = ref(false)
const confirmStatusId = ref<number | null>(null)
const isPasswordResetOpen = ref(false)
const temporaryPassword = ref('')
const statusSavingId = ref<number | null>(null)
const editorSnapshot = ref('')
let pageController: AbortController | null = null
let detailController: AbortController | null = null
let isMounted = true

const canWrite = computed(() => hasPermission('identity:write'))
const canManageAccess = computed(() => hasPermission('access:write') && hasPermission('access:read'))
const canCreate = computed(() => canWrite.value && hasPermission('organization:read'))
const organizationById = computed(() => new Map(organizations.value.map(item => [item.id, item])))
const roleById = computed(() => new Map(roles.value.map(item => [item.id, item])))
const enabledOrganizations = computed(() => organizations.value.filter(item => item.enabled))
const enabledRoles = computed(() => roles.value.filter(item => item.enabled))
const filtered = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  return users.value.filter(user => {
    if (statusFilter.value === 'enabled' && !user.enabled) return false
    if (statusFilter.value === 'disabled' && user.enabled) return false
    if (!keyword) return true
    return [user.loginName, user.displayName, user.organizationCode]
      .some(value => value.toLocaleLowerCase('zh-CN').includes(keyword))
  })
})
function currentEditorSnapshot() {
  return JSON.stringify({ form: form.value, roles: roleDraft.value, scopes: scopeDraft.value, temporaryPassword: temporaryPassword.value })
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
  isPasswordResetOpen.value = false
  temporaryPassword.value = ''
  operationError.value = null
  formError.value = ''
}

function asApiError(caught: unknown, message = '用户数据处理失败，请稍后重试') {
  return caught instanceof ApiClientError ? caught : new ApiClientError('UNKNOWN_ERROR', message, 0)
}

async function handleUnauthorized(caught: ApiClientError) {
  if (caught.status !== 401) return false
  authState.user = null
  authState.isInitialized = true
  await router.replace({ path: '/login', query: { redirect: '/users' } })
  return true
}

async function returnToLogin(reason: 'authorizationChanged' | 'accountChanged' | 'passwordReset') {
  authState.user = null
  authState.isInitialized = true
  await router.replace({ path: '/login', query: { redirect: '/users', [reason]: '1' } })
}

async function loadPage(background = false) {
  pageController?.abort()
  const controller = new AbortController()
  pageController = controller
  if (background) isRefreshing.value = true
  else isLoading.value = true
  error.value = null
  try {
    const [userRows, organizationRows, roleRows] = await Promise.all([
      listUsers(controller.signal),
      hasPermission('organization:read') ? listOrganizations(undefined, controller.signal) : Promise.resolve([]),
      hasPermission('access:read') ? listRoles(controller.signal) : Promise.resolve([]),
    ])
    if (controller.signal.aborted || !isMounted) return
    users.value = userRows
    organizations.value = organizationRows
    roles.value = roleRows
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取用户管理数据')
    if (apiError.code === 'REQUEST_ABORTED') return
    if (!await handleUnauthorized(apiError)) error.value = apiError
  } finally {
    if (pageController === controller && isMounted) {
      isLoading.value = false
      isRefreshing.value = false
    }
  }
}

function replaceUser(updated: ManagedUser) {
  const index = users.value.findIndex(item => item.id === updated.id)
  if (index === -1) users.value.push(updated)
  else users.value[index] = updated
}

function applySelected(user: ManagedUser) {
  selected.value = user
  form.value = userToForm(user)
  roleDraft.value = [...user.roleIds]
  scopeDraft.value = [...user.organizationScopeIds]
  replaceUser(user)
  captureEditorSnapshot()
}

function openCreate() {
  selected.value = null
  form.value = emptyUserForm()
  roleDraft.value = []
  scopeDraft.value = []
  operationError.value = null
  formError.value = ''
  isPasswordResetOpen.value = false
  temporaryPassword.value = ''
  mode.value = 'create'
  captureEditorSnapshot()
}

async function openUser(user: ManagedUser, edit = false) {
  applySelected(user)
  operationError.value = null
  formError.value = ''
  isPasswordResetOpen.value = false
  temporaryPassword.value = ''
  mode.value = edit && canWrite.value ? 'edit' : 'view'
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  isDetailLoading.value = true
  try {
    const latest = await getUser(user.id, controller.signal)
    if (controller.signal.aborted || !isMounted || selected.value?.id !== user.id) return
    applySelected(latest)
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取用户详情')
    if (apiError.code === 'REQUEST_ABORTED') return
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    if (detailController === controller && isMounted) isDetailLoading.value = false
  }
}

async function submitBase() {
  const creating = mode.value === 'create'
  formError.value = validateUserForm(form.value, creating) ?? ''
  if (formError.value || (!creating && mode.value !== 'edit')) return
  if (isSaving.value) return
  isSaving.value = true
  operationError.value = null
  try {
    const updated = creating
      ? await createUser(toCreateUserInput(form.value))
      : await updateUser(selected.value!.id, toUpdateUserInput(form.value))
    applySelected(updated)
    form.value.temporaryPassword = ''
    mode.value = 'view'
    notice.value = creating
      ? '用户已创建；临时密码不会再次显示，请通过安全渠道交付。'
      : '用户基础信息已保存。'
  } catch (caught) {
    const apiError = asApiError(caught)
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    isSaving.value = false
  }
}

async function saveRoles() {
  if (!selected.value || !canManageAccess.value) return
  if (isSaving.value) return
  isSaving.value = true
  operationError.value = null
  try {
    const updated = await replaceUserRoles(selected.value.id, roleDraft.value)
    applySelected(updated)
    if (updated.id === authState.user?.userId) {
      await returnToLogin('authorizationChanged')
      return
    }
    notice.value = '用户角色已更新。'
  } catch (caught) {
    const apiError = asApiError(caught, '无法保存用户角色')
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    isSaving.value = false
  }
}

async function saveScopes() {
  if (!selected.value || !canManageAccess.value) return
  if (!scopeDraft.value.includes(selected.value.primaryOrganizationId)) {
    formError.value = '机构范围必须包含主要机构'
    return
  }
  if (isSaving.value) return
  isSaving.value = true
  operationError.value = null
  formError.value = ''
  try {
    const updated = await replaceUserOrganizationScopes(selected.value.id, scopeDraft.value)
    applySelected(updated)
    if (updated.id === authState.user?.userId) {
      await returnToLogin('authorizationChanged')
      return
    }
    notice.value = '用户机构范围已更新。'
  } catch (caught) {
    const apiError = asApiError(caught, '无法保存用户机构范围')
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    isSaving.value = false
  }
}

async function submitPasswordReset() {
  if (!selected.value || !canWrite.value) return
  formError.value = validateTemporaryPassword(temporaryPassword.value, selected.value.loginName) ?? ''
  if (formError.value) return
  if (isSaving.value) return
  isSaving.value = true
  operationError.value = null
  try {
    await resetUserPassword(selected.value.id, temporaryPassword.value)
    temporaryPassword.value = ''
    isPasswordResetOpen.value = false
    if (selected.value.id === authState.user?.userId) {
      await returnToLogin('passwordReset')
      return
    }
    applySelected({ ...selected.value, mustChangePassword: true })
    notice.value = '临时密码已重置；用户下次登录必须修改密码。'
  } catch (caught) {
    const apiError = asApiError(caught, '无法重置临时密码')
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally {
    isSaving.value = false
  }
}

async function changeStatus(user: ManagedUser) {
  if (!canWrite.value || statusSavingId.value !== null) return
  if (confirmStatusId.value !== user.id) {
    confirmStatusId.value = user.id
    return
  }
  confirmStatusId.value = null
  statusSavingId.value = user.id
  operationError.value = null
  try {
    const updated = await setUserEnabled(user.id, !user.enabled, user.version)
    applySelected(updated)
    if (updated.id === authState.user?.userId) {
      await returnToLogin('accountChanged')
      return
    }
    notice.value = `${updated.displayName}已${updated.enabled ? '启用' : '停用'}。`
  } catch (caught) {
    const apiError = asApiError(caught, '无法修改用户状态')
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
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(instant)
}

onMounted(() => loadPage())
onBeforeUnmount(() => {
  isMounted = false
  pageController?.abort()
  detailController?.abort()
})
</script>

<template>
  <section class="content user-page">
    <div v-if="notice" class="feedback success" role="status"><Check :size="18" /><span>{{ notice }}</span><button aria-label="关闭提示" @click="notice = ''"><X :size="16" /></button></div>
    <div v-if="error && !isLoading" class="feedback danger" role="alert"><AlertCircle :size="19" /><span><strong>{{ error.message }}</strong><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button class="prototype-text-button" type="button" :disabled="isRefreshing" @click="loadPage(true)"><RefreshCw :size="15" />重试</button></div>

    <div class="work-toolbar">
      <label class="prototype-search"><Search :size="16" /><input v-model="query" type="search" placeholder="姓名、登录名或机构编码" aria-label="搜索用户" /></label>
      <span>{{ filtered.length }} / {{ users.length }} 个用户</span>
      <select v-model="statusFilter" aria-label="用户状态"><option value="all">全部状态</option><option value="enabled">已启用</option><option value="disabled">已停用</option></select>
      <button class="work-quiet-button" type="button" :disabled="isRefreshing" @click="loadPage(true)"><RefreshCw :size="15" :class="{ spinning: isRefreshing }" />刷新</button>
      <button v-if="canCreate" class="prototype-button" type="button" @click="openCreate"><Plus :size="15" />新增用户</button>
    </div>

    <section class="prototype-section work-table-section user-panel">
      <div v-if="isLoading" class="page-state" aria-live="polite"><LoaderCircle class="spinning" :size="28" /><strong>正在加载用户数据</strong></div>
      <div v-else-if="!error && users.length === 0" class="page-state"><UserRound :size="30" /><strong>当前范围内暂无用户</strong></div>
      <div v-else-if="users.length" class="prototype-table-wrap">
        <table class="work-table user-table">
          <thead><tr><th>用户</th><th>主要机构</th><th>角色</th><th>机构范围</th><th>登录要求</th><th>状态</th><th>最近更新</th><th><span class="visually-hidden">操作</span></th></tr></thead>
          <tbody><tr v-for="user in filtered" :key="user.id">
            <td><button class="work-row-link" type="button" @click="openUser(user)">{{ user.displayName }}</button><small>{{ user.loginName }}</small></td>
            <td>{{ organizationById.get(user.primaryOrganizationId)?.organizationName ?? user.organizationCode }}<small>{{ user.organizationCode }}</small></td>
            <td>{{ user.roleIds.length ? user.roleIds.map(id => roleById.get(id)?.roleName ?? `#${id}`).join('、') : '未分配' }}</td>
            <td>{{ user.organizationScopeIds.length }} 个机构</td>
            <td><span v-if="user.mustChangePassword" class="prototype-tag">下次登录改密</span><span v-else>正常</span></td>
            <td><span class="prototype-tag" :class="user.enabled ? 'success' : 'neutral'">{{ user.enabled ? '已启用' : '已停用' }}</span></td>
            <td>{{ formatTime(user.updatedAt) }}</td>
            <td class="user-actions"><button v-if="canWrite" class="prototype-icon" type="button" aria-label="编辑用户" title="编辑" @click="openUser(user, true)"><Pencil :size="15" /></button><button v-if="canWrite" class="status-action" :class="{ confirm: confirmStatusId === user.id }" type="button" :disabled="statusSavingId !== null" @blur="confirmStatusId = null" @click="changeStatus(user)">{{ statusSavingId === user.id ? '处理中…' : confirmStatusId === user.id ? `确认${user.enabled ? '停用' : '启用'}` : user.enabled ? '停用' : '启用' }}</button><button v-else class="prototype-icon" type="button" aria-label="查看用户详情" title="查看详情" @click="openUser(user)"><ChevronRight :size="16" /></button></td>
          </tr></tbody>
        </table>
        <div v-if="filtered.length === 0" class="prototype-empty">没有符合当前条件的用户</div>
      </div>
    </section>

    <UserEditorDrawer
      v-if="mode !== 'closed'"
      v-model:form="form"
      v-model:role-draft="roleDraft"
      v-model:scope-draft="scopeDraft"
      v-model:temporary-password="temporaryPassword"
      v-model:is-password-reset-open="isPasswordResetOpen"
      :mode="mode"
      :selected="selected"
      :organizations="organizations"
      :roles="roles"
      :enabled-organizations="enabledOrganizations"
      :enabled-roles="enabledRoles"
      :can-write="canWrite"
      :can-manage-access="canManageAccess"
      :is-saving="isSaving"
      :is-detail-loading="isDetailLoading"
      :operation-error="operationError"
      :form-error="formError"
      @close="closeEditor"
      @submit-base="submitBase"
      @save-roles="saveRoles"
      @save-scopes="saveScopes"
      @submit-password-reset="submitPasswordReset"
    />
  </section>
</template>

<style scoped>
.user-page { color: #263341; }
.user-panel { min-height: 390px; }
.page-state { min-height: 310px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #55766d; }
.user-table { min-width: 1120px; }
.user-actions { display: flex; justify-content: flex-end; gap: 6px; }
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
