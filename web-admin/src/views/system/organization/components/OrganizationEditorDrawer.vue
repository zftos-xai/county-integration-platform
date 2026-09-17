<!-- 机构编辑抽屉：负责表单展示、焦点管理和编辑器事件，不执行远程请求。 -->
<script setup lang="ts">
import { AlertCircle, LoaderCircle, X } from 'lucide-vue-next'
import { ref } from 'vue'
import { useModalDialog } from '@/composables/useModalDialog'
import type { ApiClientError } from '@/utils/request'
import type { Organization } from '@/api/system/organization'
import type { OrganizationForm } from '../form'

type EditorMode = 'view' | 'create' | 'edit'

const props = defineProps<{
  mode: EditorMode
  selected: Organization | null
  parentOptions: Organization[]
  canWrite: boolean
  isSaving: boolean
  isDetailLoading: boolean
  operationError: ApiClientError | null
  formError: string
  formatTime: (value: string | null) => string
}>()
const emit = defineEmits<{ close: []; submit: []; reload: []; edit: [] }>()
const form = defineModel<OrganizationForm>('form', { required: true })
const isOpen = ref(true)
const { dialogRef, handleDialogKeydown } = useModalDialog(isOpen, () => emit('close'))
</script>

<template>
  <div class="drawer-layer" @mousedown.self="emit('close')">
    <section ref="dialogRef" class="organization-drawer" role="dialog" aria-modal="true" aria-labelledby="organization-editor-title" tabindex="-1" @keydown="handleDialogKeydown">
      <header><div><span>{{ mode === 'create' ? '新增下级机构' : mode === 'edit' ? '编辑机构' : '机构详情' }}</span><h2 id="organization-editor-title">{{ mode === 'create' ? '建立机构档案' : selected?.organizationName }}</h2></div><button class="icon-button" type="button" aria-label="关闭" @click="emit('close')"><X :size="18" /></button></header>
      <div v-if="isDetailLoading" class="drawer-loading"><LoaderCircle class="spinning" :size="22" />正在读取最新版本…</div>
      <form class="organization-form" @submit.prevent="emit('submit')">
        <div v-if="operationError" class="feedback danger" role="alert"><AlertCircle :size="18" /><span><strong>{{ operationError.message }}</strong><small v-if="operationError.status === 409">当前表单仍保留，请刷新最新版本后再修改。</small><small v-if="operationError.requestId">请求编号：{{ operationError.requestId }}</small></span><button v-if="selected" class="text-button" type="button" @click="emit('reload')">刷新详情</button></div>
        <div v-if="formError" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ formError }}</span></div>
        <label><span>机构编码 <b>*</b></span><input v-model="form.organizationCode" :disabled="mode !== 'create'" maxlength="64" autocomplete="off" placeholder="例如 COUNTY.HOSPITAL.001" /><small v-if="mode !== 'create'">稳定编码创建后不可修改</small></label>
        <label><span>机构名称 <b>*</b></span><input v-model="form.organizationName" :disabled="mode === 'view'" maxlength="200" autocomplete="off" /></label>
        <label><span>机构类型代码 <b>*</b></span><input v-model="form.organizationType" :disabled="mode === 'view'" maxlength="32" autocomplete="off" placeholder="使用项目已确认的类型代码" /><small>此处不猜测业务枚举，仅接受已确认代码</small></label>
        <label><span>上级机构 <b v-if="mode === 'create'">*</b></span><select v-model="form.parentId" :disabled="mode === 'view'"><option value="">{{ mode === 'create' ? '请选择可访问的上级机构' : '顶级机构' }}</option><option v-for="item in parentOptions" :key="item.id" :value="String(item.id)">{{ item.organizationName }}（{{ item.organizationCode }}）</option></select><small v-if="mode === 'create'">新增机构只能建立在当前账号可访问的父机构下</small></label>
        <div class="date-grid"><label><span>有效开始时间</span><input v-model="form.validFrom" :disabled="mode === 'view'" type="datetime-local" /></label><label><span>有效结束时间</span><input v-model="form.validTo" :disabled="mode === 'view'" type="datetime-local" /></label></div>
        <dl v-if="selected && mode === 'view'" class="detail-meta"><div><dt>当前状态</dt><dd><span class="status-pill" :class="selected.enabled ? 'enabled' : 'disabled'">{{ selected.enabled ? '已启用' : '已停用' }}</span></dd></div><div><dt>创建时间</dt><dd>{{ props.formatTime(selected.createdAt) }}</dd></div><div><dt>最近更新</dt><dd>{{ props.formatTime(selected.updatedAt) }}</dd></div></dl>
        <footer><button class="secondary-button" type="button" @click="emit('close')">{{ mode === 'view' ? '关闭' : '取消' }}</button><button v-if="mode === 'view' && canWrite" class="primary-button" type="button" @click="emit('edit')">编辑</button><button v-if="mode === 'create' || mode === 'edit'" class="primary-button" type="submit" :disabled="isSaving || isDetailLoading">{{ isSaving ? '正在保存…' : '保存' }}</button></footer>
      </form>
    </section>
  </div>
</template>

<style scoped>
.drawer-layer { position: fixed; inset: 0; z-index: 100; background: rgba(19,31,43,.38); display: flex; justify-content: flex-end; }
.organization-drawer { width: min(560px, 100%); height: 100%; background: white; box-shadow: -12px 0 32px rgba(22,35,47,.14); display: flex; flex-direction: column; overflow-y: auto; }
.organization-drawer > header { min-height: 76px; padding: 15px 20px; border-bottom: 1px solid #dfe5ea; display: flex; align-items: center; justify-content: space-between; }
.organization-drawer > header span { color: #71808e; font-size: 11px; }
.organization-drawer > header h2 { margin: 3px 0 0; font-size: 18px; }
.drawer-loading { padding: 9px 20px; background: #edf3f8; color: #45617b; display: flex; align-items: center; gap: 8px; font-size: 12px; }
.organization-form { padding: 20px; display: grid; gap: 16px; }
.organization-form > label, .date-grid label { display: grid; gap: 6px; color: #465565; font-size: 12px; }
.organization-form label > span { font-weight: 600; }
.organization-form b { color: #aa4934; }
.organization-form small { color: #7a8691; font-size: 11px; }
.organization-form input, .organization-form select { min-height: 38px; border: 1px solid #cfd7df; border-radius: 5px; background: white; color: #263341; padding: 0 10px; }
.organization-form input:disabled, .organization-form select:disabled { background: #f3f5f6; color: #67727d; opacity: 1; }
.date-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.detail-meta { margin: 2px 0 0; padding: 13px; background: #f7f8fa; display: grid; gap: 9px; }
.detail-meta div { display: flex; align-items: center; justify-content: space-between; gap: 15px; }
.detail-meta dt { color: #6c7883; font-size: 12px; }
.detail-meta dd { margin: 0; font-size: 12px; }
.status-pill { width: fit-content; padding: 4px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; white-space: nowrap; }
.status-pill.enabled { background: #e7f3ed; color: #236c4f; }
.status-pill.disabled { background: #eef0f2; color: #66717b; }
.organization-form footer { margin: 4px -20px -20px; padding: 14px 20px; border-top: 1px solid #e1e6ea; display: flex; justify-content: flex-end; gap: 8px; }
.primary-button, .secondary-button, .text-button { min-height: 36px; padding: 0 13px; border-radius: 5px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; font-weight: 600; font-size: 13px; }
.primary-button { border: 1px solid #0d6258; background: #147467; color: white; }
.secondary-button { border: 1px solid #cdd6df; background: white; color: #34465a; }
.text-button { min-height: 30px; padding: 0 8px; border: 0; background: transparent; color: inherit; }
.feedback { min-height: 48px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 10px; font-size: 12px; }
.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }
.feedback > button:last-child { margin-left: auto; }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spinning { animation: none; } }
@media (max-width: 700px) { .date-grid { grid-template-columns: 1fr; } }
</style>
