<!-- 用户编辑抽屉：呈现资料、角色、机构范围和密码操作，不直接访问后端。 -->
<script setup lang="ts">
import { AlertCircle, KeyRound, LoaderCircle, X } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useModalDialog } from '@/composables/useModalDialog'
import type { Organization } from '@/api/system/organization'
import type { Role } from '@/api/system/role'
import type { ManagedUser } from '@/api/system/user'
import { isWriteResultUncertain } from '@/utils/request'
import type { ApiClientError } from '@/utils/request'
import type { UserForm } from '../form'

type EditorMode = 'view' | 'create' | 'edit'

const props = defineProps<{
  mode: EditorMode
  selected: ManagedUser | null
  organizations: Organization[]
  roles: Role[]
  enabledOrganizations: Organization[]
  enabledRoles: Role[]
  canWrite: boolean
  canManageAccess: boolean
  isSaving: boolean
  isDetailLoading: boolean
  operationError: ApiClientError | null
  formError: string
}>()
const emit = defineEmits<{
  close: []
  submitBase: []
  saveRoles: []
  saveScopes: []
  submitPasswordReset: []
  reload: []
}>()
const form = defineModel<UserForm>('form', { required: true })
const roleDraft = defineModel<number[]>('roleDraft', { required: true })
const scopeDraft = defineModel<number[]>('scopeDraft', { required: true })
const temporaryPassword = defineModel<string>('temporaryPassword', { required: true })
const isPasswordResetOpen = defineModel<boolean>('isPasswordResetOpen', { required: true })
const isOpen = ref(true)
const { dialogRef, handleDialogKeydown } = useModalDialog(isOpen, () => emit('close'))
const roleNameById = computed(() => new Map(props.roles.map(item => [item.id, item.roleName])))
const organizationNameById = computed(() => new Map(props.organizations.map(item => [item.id, item.organizationName])))
const addedRoleNames = computed(() => roleDraft.value.filter(id => !props.selected?.roleIds.includes(id)).map(id => roleNameById.value.get(id) ?? `角色 #${id}`))
const removedRoleNames = computed(() => (props.selected?.roleIds ?? []).filter(id => !roleDraft.value.includes(id)).map(id => roleNameById.value.get(id) ?? `角色 #${id}`))
const addedScopeNames = computed(() => scopeDraft.value.filter(id => !props.selected?.organizationScopeIds.includes(id)).map(id => organizationNameById.value.get(id) ?? `机构 #${id}`))
const removedScopeNames = computed(() => (props.selected?.organizationScopeIds ?? []).filter(id => !scopeDraft.value.includes(id)).map(id => organizationNameById.value.get(id) ?? `机构 #${id}`))
</script>

<template>
  <div class="drawer-layer" @mousedown.self="emit('close')">
    <section ref="dialogRef" class="user-drawer" role="dialog" aria-modal="true" aria-labelledby="user-editor-title" tabindex="-1" @keydown="handleDialogKeydown">
      <header><div><span>{{ mode === 'create' ? '新增平台用户' : mode === 'edit' ? '编辑用户' : '用户详情' }}</span><h2 id="user-editor-title">{{ mode === 'create' ? '建立用户账号' : selected?.displayName }}</h2></div><button class="prototype-icon" type="button" aria-label="关闭" @click="emit('close')"><X :size="18" /></button></header>
      <div v-if="isDetailLoading" class="drawer-loading"><LoaderCircle class="spinning" :size="22" />正在读取最新版本…</div>
      <div class="user-drawer-body">
        <div v-if="operationError" class="feedback danger" role="alert"><AlertCircle :size="18" /><span><strong>{{ operationError.message }}</strong><small v-if="operationError.status === 409">数据已变化，请重新读取最新版本后再操作。</small><small v-if="operationError.requestId">请求编号：{{ operationError.requestId }}</small></span><button v-if="operationError.status === 409 || isWriteResultUncertain(operationError)" class="work-quiet-button" type="button" @click="emit('reload')">重新读取</button></div>
        <div v-if="formError" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ formError }}</span></div>
        <form class="user-form" @submit.prevent="emit('submitBase')">
          <section><h3>基础信息</h3><p>登录名创建后不可修改；主要机构必须位于当前账号的数据范围内。</p></section>
          <label><span>登录名 <b>*</b></span><input v-model="form.loginName" :disabled="mode !== 'create'" maxlength="64" autocomplete="off" /></label>
          <label><span>用户姓名 <b>*</b></span><input v-model="form.displayName" :disabled="mode === 'view'" maxlength="100" autocomplete="off" /></label>
          <label><span>主要机构 <b>*</b></span><select v-model="form.primaryOrganizationId" :disabled="mode === 'view'"><option value="">请选择主要机构</option><option v-for="organization in enabledOrganizations" :key="organization.id" :value="String(organization.id)">{{ organization.organizationName }}（{{ organization.organizationCode }}）</option></select></label>
          <label v-if="mode === 'create'"><span>一次性临时密码 <b>*</b></span><input v-model="form.temporaryPassword" type="password" maxlength="128" autocomplete="new-password" /><small>至少9个字符且UTF-8编码不超过72字节，不能包含登录名；创建后不再显示。</small></label>
          <footer v-if="mode === 'create' || mode === 'edit'"><button class="work-quiet-button" type="button" @click="emit('close')">取消</button><button class="prototype-button" type="submit" :disabled="isSaving || isDetailLoading">{{ isSaving ? '正在保存…' : '保存基础信息' }}</button></footer>
        </form>
        <template v-if="selected">
          <section class="access-section"><header><div><h3>角色</h3><p>角色决定用户可访问的功能；修改当前账号角色后需重新登录。</p></div><button v-if="canManageAccess" class="work-quiet-button" type="button" :disabled="isSaving || (!addedRoleNames.length && !removedRoleNames.length)" @click="emit('saveRoles')">保存角色</button></header><div v-if="addedRoleNames.length || removedRoleNames.length" class="change-summary" role="status"><strong>待保存的角色变化</strong><span v-if="addedRoleNames.length">新增：{{ addedRoleNames.join('、') }}</span><span v-if="removedRoleNames.length">取消：{{ removedRoleNames.join('、') }}</span></div><div v-if="roles.length" class="choice-grid"><label v-for="role in enabledRoles" :key="role.id"><input v-model="roleDraft" type="checkbox" :value="role.id" :disabled="!canManageAccess" /><span><strong>{{ role.roleName }}</strong><small>{{ role.roleCode }}</small></span></label></div><p v-else class="section-empty">当前账号无权读取角色清单。</p></section>
          <section class="access-section"><header><div><h3>可查看机构</h3><p>必须包含主要机构，且不能超出当前账号可管理范围。</p></div><button v-if="canManageAccess" class="work-quiet-button" type="button" :disabled="isSaving || (!addedScopeNames.length && !removedScopeNames.length)" @click="emit('saveScopes')">保存可查看机构</button></header><div v-if="addedScopeNames.length || removedScopeNames.length" class="change-summary" role="status"><strong>待保存的机构范围变化</strong><span v-if="addedScopeNames.length">新增：{{ addedScopeNames.join('、') }}</span><span v-if="removedScopeNames.length">取消：{{ removedScopeNames.join('、') }}</span></div><div v-if="organizations.length" class="choice-grid"><label v-for="organization in enabledOrganizations" :key="organization.id"><input v-model="scopeDraft" type="checkbox" :value="organization.id" :disabled="!canManageAccess || organization.id === selected.primaryOrganizationId" /><span><strong>{{ organization.organizationName }}</strong><small>{{ organization.organizationCode }}{{ organization.id === selected.primaryOrganizationId ? ' · 主要机构' : '' }}</small></span></label></div><p v-else class="section-empty">当前账号无权读取机构清单。</p></section>
          <section v-if="canWrite" class="access-section"><header><div><h3>临时密码</h3><p>重置后现有登录状态失效，用户下次登录必须修改密码。</p></div><button v-if="!isPasswordResetOpen" class="work-quiet-button" type="button" @click="isPasswordResetOpen = true"><KeyRound :size="15" />重置密码</button></header><form v-if="isPasswordResetOpen" class="password-reset" @submit.prevent="emit('submitPasswordReset')"><label class="visually-hidden" for="temporary-password">一次性临时密码</label><input id="temporary-password" v-model="temporaryPassword" type="password" maxlength="128" autocomplete="new-password" placeholder="输入一次性临时密码" /><button class="work-quiet-button" type="button" @click="isPasswordResetOpen = false; temporaryPassword = ''">取消</button><button class="prototype-button" type="submit" :disabled="isSaving">确认重置</button></form></section>
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
.drawer-layer { position: fixed; inset: 0; z-index: 100; background: rgba(19,31,43,.38); display: flex; justify-content: flex-end; }
.user-drawer { width: min(650px, 100%); height: 100%; overflow-y: auto; background: white; box-shadow: -12px 0 32px rgba(22,35,47,.14); }
.user-drawer > header { min-height: 76px; padding: 15px 20px; position: sticky; top: 0; z-index: 2; border-bottom: 1px solid #dfe5ea; background: white; display: flex; align-items: center; justify-content: space-between; }
.user-drawer > header span { color: #71808e; font-size: 11px; }
.user-drawer > header h2 { margin: 3px 0 0; font-size: 18px; }
.drawer-loading { padding: 9px 20px; background: #edf5f2; color: #456c61; display: flex; align-items: center; gap: 8px; font-size: 12px; }
.user-drawer-body { padding: 20px; display: grid; gap: 16px; }
.feedback { min-height: 46px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 10px; font-size: 12px; }
.feedback > span { min-width: 0; display: grid; gap: 2px; }
.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }
.user-form { display: grid; gap: 15px; }
.user-form section h3, .access-section h3 { margin: 0; font-size: 14px; }
.user-form section p, .access-section p { margin: 5px 0 0; color: #75828c; font-size: 11px; line-height: 1.6; }
.user-form > label { display: grid; gap: 6px; color: #465565; font-size: 12px; }
.user-form label > span { font-weight: 600; }
.user-form b { color: #aa4934; }
.user-form input, .user-form select, .password-reset input { min-height: 38px; padding: 0 10px; border: 1px solid #cfd7df; border-radius: 5px; background: white; color: #263341; }
.user-form input:disabled, .user-form select:disabled { background: #f3f5f6; color: #67727d; }
.user-form small { color: #7a8691; font-size: 11px; }
.user-form footer { display: flex; justify-content: flex-end; gap: 8px; }
.access-section { padding-top: 16px; border-top: 1px solid #e2e7e9; }
.access-section > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.change-summary { margin-top: 12px; padding: 10px 12px; border: 1px solid #e0cf9c; border-radius: 5px; background: #fff9e8; color: #735b21; display: grid; gap: 4px; font-size: 11px; line-height: 1.5; }
.choice-grid { margin-top: 12px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.choice-grid > label { min-height: 50px; padding: 9px 10px; border: 1px solid #dce3e5; border-radius: 5px; display: flex; align-items: flex-start; gap: 9px; }
.choice-grid input { margin-top: 3px; accent-color: #147467; }
.choice-grid strong, .choice-grid small { display: block; }
.choice-grid strong { font-size: 12px; }
.choice-grid small { margin-top: 3px; color: #7b8990; font-size: 10px; }
.section-empty { padding: 14px; background: #f6f8f9; }
.password-reset { margin-top: 12px; display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 8px; }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spinning { animation: none; } }
@media (max-width: 600px) { .choice-grid { grid-template-columns: 1fr; } .password-reset { grid-template-columns: 1fr; } }
</style>
