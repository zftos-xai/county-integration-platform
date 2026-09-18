<!-- 字典编辑抽屉：复用类型与字典项表单，并保持稳定代码只在创建时可写。 -->
<script setup lang="ts">
import { AlertCircle, X } from 'lucide-vue-next'
import { ref } from 'vue'
import { useModalDialog } from '@/composables/useModalDialog'
import { isWriteResultUncertain } from '@/utils/request'
import type { ApiClientError } from '@/utils/request'
import type { DictionaryItemForm, DictionaryTypeForm } from '../form'

export type DictionaryEditorKind = 'type-create' | 'type-edit' | 'item-create' | 'item-edit'

const props = defineProps<{ kind: DictionaryEditorKind; title: string; isSaving: boolean; error: ApiClientError | null; formError: string }>()
const emit = defineEmits<{ close: []; submit: []; reload: [] }>()
const typeForm = defineModel<DictionaryTypeForm>('typeForm', { required: true })
const itemForm = defineModel<DictionaryItemForm>('itemForm', { required: true })
const isOpen = ref(true)
const { dialogRef, handleDialogKeydown } = useModalDialog(isOpen, () => emit('close'))
const isType = props.kind.startsWith('type')
const isCreating = props.kind.endsWith('create')
</script>

<template>
  <div class="work-drawer-backdrop" @mousedown.self="emit('close')">
    <section ref="dialogRef" class="work-drawer dictionary-editor" role="dialog" aria-modal="true" aria-labelledby="dictionary-editor-title" tabindex="-1" @keydown="handleDialogKeydown">
      <header class="work-drawer-header"><div><small>{{ isType ? '字典类型' : '字典项' }}</small><h2 id="dictionary-editor-title">{{ title }}</h2></div><button class="prototype-icon" type="button" aria-label="关闭" @click="emit('close')"><X :size="18" /></button></header>
      <div class="work-drawer-body">
        <div v-if="error" class="drawer-error" role="alert"><AlertCircle :size="18" /><span><strong>{{ error.message }}</strong><small v-if="error.status === 409">数据已被更新，请刷新列表后重新操作。</small><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button v-if="error.status === 409 || isWriteResultUncertain(error)" class="work-quiet-button" type="button" @click="emit('reload')">刷新列表</button></div>
        <div v-if="formError" class="drawer-error" role="alert"><AlertCircle :size="18" />{{ formError }}</div>
        <form v-if="isType" class="work-form" @submit.prevent="emit('submit')">
          <label for="dictionary-type-code">类型代码</label><input id="dictionary-type-code" v-model="typeForm.typeCode" :disabled="!isCreating" maxlength="64" autocomplete="off" placeholder="例如 display_mode" />
          <label for="dictionary-type-name">类型名称</label><input id="dictionary-type-name" v-model="typeForm.typeName" maxlength="100" autocomplete="off" />
          <label for="dictionary-type-description">用途说明</label><textarea id="dictionary-type-description" v-model="typeForm.description" rows="4" maxlength="500" />
          <template v-if="!isCreating"><label for="dictionary-type-enabled">使用状态</label><label class="editor-check"><input id="dictionary-type-enabled" v-model="typeForm.enabled" type="checkbox" />启用字典类型</label></template>
          <div class="work-inline-note">类型代码创建后不可修改。停用类型前应确认没有业务流程继续依赖其字典项。</div>
          <div class="work-inline-actions"><button class="work-quiet-button" type="button" @click="emit('close')">取消</button><button class="prototype-button" type="submit" :disabled="isSaving">{{ isSaving ? '正在保存…' : '保存' }}</button></div>
        </form>
        <form v-else class="work-form" @submit.prevent="emit('submit')">
          <label for="dictionary-item-code">字典项代码</label><input id="dictionary-item-code" v-model="itemForm.itemCode" :disabled="!isCreating" maxlength="64" autocomplete="off" placeholder="例如 COMPACT" />
          <label for="dictionary-item-label">展示文本</label><input id="dictionary-item-label" v-model="itemForm.itemLabel" maxlength="200" autocomplete="off" />
          <label for="dictionary-item-order">排序值</label><input id="dictionary-item-order" v-model="itemForm.sortOrder" type="number" min="0" max="999999" step="1" />
          <template v-if="!isCreating"><label for="dictionary-item-enabled">使用状态</label><label class="editor-check"><input id="dictionary-item-enabled" v-model="itemForm.enabled" type="checkbox" />启用字典项</label></template>
          <div class="work-inline-note">字典项代码创建后不可修改；排序值越小越靠前，相同时按代码排序。</div>
          <div class="work-inline-actions"><button class="work-quiet-button" type="button" @click="emit('close')">取消</button><button class="prototype-button" type="submit" :disabled="isSaving">{{ isSaving ? '正在保存…' : '保存' }}</button></div>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dictionary-editor { width: min(580px, 100vw); }.editor-check { display: flex; align-items: center; gap: 8px; font-weight: 400; }.editor-check input { width: 16px; accent-color: #147467; }.drawer-error { min-height: 44px; margin-bottom: 12px; padding: 9px 12px; border: 1px solid #e1c0b7; border-radius: 5px; background: #fbf1ee; color: #8d432e; display: flex; align-items: center; gap: 9px; font-size: 12px; }.drawer-error span { display: grid; gap: 3px; }
</style>
