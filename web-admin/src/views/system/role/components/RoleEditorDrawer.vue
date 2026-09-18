<!-- 角色编辑抽屉：展示角色资料与代码注册权限，不直接访问后端。 -->
<script setup lang="ts">
import { AlertCircle, LoaderCircle, LockKeyhole, X } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import type { Permission, Role } from '@/api/system/role'
import { useModalDialog } from '@/composables/useModalDialog'
import { isWriteResultUncertain } from '@/utils/request'
import type { ApiClientError } from '@/utils/request'
import type { RoleForm } from '../form'

type EditorMode = 'view' | 'create' | 'edit'

const props = defineProps<{
  mode: EditorMode
  selected: Role | null
  permissions: Permission[]
  assignedUserNames: string[]
  userAssignmentKnown: boolean
  canWrite: boolean
  isSaving: boolean
  isDetailLoading: boolean
  operationError: ApiClientError | null
  formError: string
}>()
const emit = defineEmits<{ close: []; submitBase: []; savePermissions: []; edit: []; reload: [] }>()
const form = defineModel<RoleForm>('form', { required: true })
const permissionDraft = defineModel<string[]>('permissionDraft', { required: true })
const isOpen = ref(true)
const { dialogRef, handleDialogKeydown } = useModalDialog(isOpen, () => emit('close'))
const canEditSelected = computed(() => props.canWrite && !props.selected?.systemManaged)
const permissionNameByCode = computed(() => new Map(props.permissions.map(item => [item.permissionCode, item.permissionName])))
const addedPermissions = computed(() => permissionDraft.value
  .filter(code => !props.selected?.permissionCodes.includes(code))
  .map(code => permissionNameByCode.value.get(code) ?? code))
const removedPermissions = computed(() => (props.selected?.permissionCodes ?? [])
  .filter(code => !permissionDraft.value.includes(code))
  .map(code => permissionNameByCode.value.get(code) ?? code))
const permissionGroups = computed(() => {
  const groupNames: Readonly<Record<string, string>> = {
    organization: '机构管理', identity: '用户管理', access: '角色权限',
    configuration: '参数与字典', audit: '操作记录',
  }
  const groups = new Map<string, Permission[]>()
  props.permissions.forEach(permission => {
    const prefix = permission.permissionCode.split(':')[0] ?? ''
    const name = groupNames[prefix] ?? '其他功能'
    groups.set(name, [...(groups.get(name) ?? []), permission])
  })
  return [...groups].map(([name, items]) => ({ name, items }))
})
</script>

<template>
  <div class="drawer-layer" @mousedown.self="emit('close')">
    <section ref="dialogRef" class="role-drawer" role="dialog" aria-modal="true" aria-labelledby="role-editor-title" tabindex="-1" @keydown="handleDialogKeydown">
      <header>
        <div><span>{{ mode === 'create' ? '新增平台角色' : mode === 'edit' ? '编辑角色' : '角色详情' }}</span><h2 id="role-editor-title">{{ mode === 'create' ? '建立功能角色' : selected?.roleName }}</h2></div>
        <button class="prototype-icon" type="button" aria-label="关闭" @click="emit('close')"><X :size="18" /></button>
      </header>
      <div v-if="isDetailLoading" class="drawer-loading"><LoaderCircle class="spinning" :size="22" />正在读取最新版本…</div>
      <div class="role-drawer-body">
        <div v-if="operationError" class="feedback danger" role="alert"><AlertCircle :size="18" /><span><strong>{{ operationError.message }}</strong><small v-if="operationError.status === 409">数据已变化，请重新读取最新版本后再操作。</small><small v-if="operationError.requestId">请求编号：{{ operationError.requestId }}</small></span><button v-if="operationError.status === 409 || isWriteResultUncertain(operationError)" class="work-quiet-button" type="button" @click="emit('reload')">重新读取</button></div>
        <div v-if="formError" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ formError }}</span></div>
        <div v-if="selected?.systemManaged" class="protected-note"><LockKeyhole :size="18" /><span><strong>系统保护角色</strong><small>角色资料与权限由系统预置并维护，管理页面只读。</small></span></div>

        <form class="role-form" @submit.prevent="emit('submitBase')">
          <section><h3>基础信息</h3><p>角色代码创建后不可修改；停用角色会使使用该角色的旧会话在下一次请求时失效。</p></section>
          <label><span>角色代码 <b>*</b></span><input v-model="form.roleCode" :disabled="mode !== 'create'" maxlength="64" autocomplete="off" placeholder="例如 REPORT_REVIEWER" /></label>
          <label><span>角色名称 <b>*</b></span><input v-model="form.roleName" :disabled="mode === 'view' || selected?.systemManaged" maxlength="100" autocomplete="off" /></label>
          <label v-if="mode === 'edit'" class="role-enabled"><input v-model="form.enabled" type="checkbox" :disabled="selected?.systemManaged" />启用角色</label>
          <footer v-if="mode === 'create' || mode === 'edit'"><button class="work-quiet-button" type="button" @click="emit('close')">取消</button><button class="prototype-button" type="submit" :disabled="isSaving || isDetailLoading || selected?.systemManaged">{{ isSaving ? '正在保存…' : '保存基础信息' }}</button></footer>
          <footer v-else-if="canEditSelected"><button class="prototype-button" type="button" @click="emit('edit')">编辑基础信息</button></footer>
        </form>

        <section v-if="selected" class="permission-section">
          <header><div><h3>功能权限</h3><p>{{ !userAssignmentKnown ? '当前账号无权查看哪些用户使用该角色；保存仍会立即影响相关用户。' : assignedUserNames.length ? `保存后将影响使用该角色的 ${assignedUserNames.length} 个用户：${assignedUserNames.slice(0, 5).join('、')}${assignedUserNames.length > 5 ? '等' : ''}` : '当前没有用户使用该角色。' }}</p></div><button v-if="canEditSelected" class="work-quiet-button" type="button" :disabled="isSaving || (!addedPermissions.length && !removedPermissions.length)" @click="emit('savePermissions')">保存权限</button></header>
          <div v-if="addedPermissions.length || removedPermissions.length" class="permission-changes" role="status"><strong>待保存的权限变化</strong><span v-if="addedPermissions.length">新增：{{ addedPermissions.join('、') }}</span><span v-if="removedPermissions.length">取消：{{ removedPermissions.join('、') }}</span></div>
          <div v-if="permissions.length" class="permission-groups">
            <section v-for="group in permissionGroups" :key="group.name"><h4>{{ group.name }}</h4><div class="permission-grid"><label v-for="permission in group.items" :key="permission.permissionCode"><input v-model="permissionDraft" type="checkbox" :value="permission.permissionCode" :disabled="!canEditSelected" /><span><strong>{{ permission.permissionName }}</strong><small>{{ permission.permissionCode }}</small></span></label></div></section>
          </div>
          <p v-else class="section-empty">后端当前没有可分配的注册权限。</p>
        </section>
      </div>
    </section>
  </div>
</template>

<style scoped>
.drawer-layer { position: fixed; inset: 0; z-index: 100; background: rgb(19 31 43 / 38%); display: flex; justify-content: flex-end; }
.role-drawer { width: min(680px, 100%); height: 100%; overflow-y: auto; background: white; box-shadow: -12px 0 32px rgb(22 35 47 / 14%); }
.role-drawer > header { min-height: 76px; padding: 15px 20px; position: sticky; top: 0; z-index: 2; border-bottom: 1px solid #dfe5ea; background: white; display: flex; align-items: center; justify-content: space-between; }
.role-drawer > header span { color: #71808e; font-size: 11px; }
.role-drawer > header h2 { margin: 3px 0 0; font-size: 18px; }
.drawer-loading { padding: 9px 20px; background: #edf5f2; color: #456c61; display: flex; align-items: center; gap: 8px; font-size: 12px; }
.role-drawer-body { padding: 20px; display: grid; gap: 16px; }
.feedback, .protected-note { min-height: 46px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 10px; font-size: 12px; }
.feedback > span, .protected-note > span { min-width: 0; display: grid; gap: 2px; }
.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }
.protected-note { border-color: #d9d2b5; background: #faf7e9; color: #715f27; }
.role-form { display: grid; gap: 15px; }
.role-form section h3, .permission-section h3 { margin: 0; font-size: 14px; }
.role-form section p, .permission-section p { margin: 5px 0 0; color: #75828c; font-size: 11px; line-height: 1.6; }
.role-form > label:not(.role-enabled) { display: grid; gap: 6px; color: #465565; font-size: 12px; }
.role-form label > span { font-weight: 600; }
.role-form b { color: #aa4934; }
.role-form input[type="text"], .role-form input:not([type]) { min-height: 38px; padding: 0 10px; border: 1px solid #cfd7df; border-radius: 5px; background: white; color: #263341; }
.role-form input:disabled { background: #f3f5f6; color: #67727d; }
.role-enabled { display: flex; align-items: center; gap: 8px; color: #465565; font-size: 12px; }
.role-enabled input, .permission-grid input { accent-color: #147467; }
.role-form footer { display: flex; justify-content: flex-end; gap: 8px; }
.permission-section { padding-top: 16px; border-top: 1px solid #e2e7e9; }
.permission-section > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.permission-changes { margin-top: 12px; padding: 10px 12px; border: 1px solid #e0cf9c; border-radius: 5px; background: #fff9e8; color: #735b21; display: grid; gap: 4px; font-size: 11px; line-height: 1.5; }
.permission-groups { margin-top: 12px; display: grid; gap: 12px; }.permission-groups section { border: 1px solid #e0e6e8; border-radius: 5px; overflow: hidden; }.permission-groups h4 { margin: 0; padding: 8px 10px; background: #f6f8f9; color: #40545f; font-size: 11px; }
.permission-grid { padding: 8px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.permission-grid > label { min-height: 54px; padding: 9px 10px; border: 1px solid #dce3e5; border-radius: 5px; display: flex; align-items: flex-start; gap: 9px; }
.permission-grid input { margin-top: 3px; }
.permission-grid strong, .permission-grid small { display: block; }
.permission-grid strong { font-size: 12px; }
.permission-grid small { margin-top: 3px; color: #7b8990; font-size: 10px; }
.section-empty { padding: 14px; background: #f6f8f9; }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spinning { animation: none; } }
@media (max-width: 600px) { .permission-grid { grid-template-columns: 1fr; } }
</style>
