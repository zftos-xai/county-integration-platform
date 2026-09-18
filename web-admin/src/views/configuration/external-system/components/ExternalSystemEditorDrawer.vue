<!-- 外部系统编辑抽屉：维护稳定系统身份、用途和启停状态。 -->
<script setup lang="ts">
import { AlertCircle, X } from 'lucide-vue-next'
import { ref } from 'vue'
import type { ApiClientError } from '@/utils/request'
import { isWriteResultUncertain } from '@/utils/request'
import { useModalDialog } from '@/composables/useModalDialog'
import type { ExternalSystemForm } from '../form'

const props = defineProps<{
  creating: boolean
  canWrite: boolean
  isSaving: boolean
  error: ApiClientError | null
  formError: string
}>()
const emit = defineEmits<{ close: []; submit: []; reload: [] }>()
const form = defineModel<ExternalSystemForm>('form', { required: true })
const isOpen = ref(true)
const { dialogRef, handleDialogKeydown } = useModalDialog(isOpen, () => emit('close'))
</script>

<template>
  <div class="work-drawer-backdrop" @mousedown.self="emit('close')">
    <section ref="dialogRef" class="work-drawer system-drawer" role="dialog" aria-modal="true" aria-labelledby="external-system-editor-title" tabindex="-1" @keydown="handleDialogKeydown">
      <header class="work-drawer-header"><div><small>{{ creating ? '外部系统身份' : form.systemCode }}</small><h2 id="external-system-editor-title">{{ creating ? '登记外部系统' : '系统资料' }}</h2></div><button class="prototype-icon" type="button" aria-label="关闭" @click="emit('close')"><X :size="18" /></button></header>
      <div class="work-drawer-body">
        <div v-if="error" class="feedback danger" role="alert"><AlertCircle :size="18" /><span><strong>{{ error.message }}</strong><small v-if="error.status === 409">配置已经变化，请重新读取最新版本。</small><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button v-if="error.status === 409 || isWriteResultUncertain(error)" class="work-quiet-button" type="button" @click="emit('reload')">重新读取</button></div>
        <div v-if="formError" class="feedback danger" role="alert"><AlertCircle :size="18" />{{ formError }}</div>
        <form id="system-form" class="system-form" @submit.prevent="emit('submit')">
          <label class="system-field" for="external-system-code"><span>系统编码</span><input id="external-system-code" v-model="form.systemCode" maxlength="64" :disabled="!creating" placeholder="例如 PRIMARY_HIS" autocomplete="off" /><small>稳定编码用于选择运行适配器，保存后不可修改。</small></label>
          <label class="system-field" for="external-system-name"><span>系统名称</span><input id="external-system-name" v-model="form.systemName" maxlength="100" placeholder="请输入业务人员可识别的名称" /></label>
          <label class="system-field" for="external-system-description"><span>用途说明</span><textarea id="external-system-description" v-model="form.description" maxlength="500" rows="4" placeholder="说明系统来源、使用场景和维护责任" /></label>
          <label v-if="!creating" class="system-field" for="external-system-enabled"><span>使用状态</span><select id="external-system-enabled" v-model="form.enabled"><option :value="true">启用</option><option :value="false">停用</option></select><small>{{ form.enabled ? '该系统的已启用接口配置可以正常使用。' : '停用后，各机构的接口配置暂时不可用。' }}</small></label>
          <p v-if="creating" class="creation-note">保存系统后，再为各机构添加接口配置。</p>
        </form>
      </div>
      <footer class="system-footer"><button class="work-quiet-button" type="button" @click="emit('close')">取消</button><button v-if="canWrite" class="prototype-button" type="submit" form="system-form" :disabled="isSaving">{{ isSaving ? '正在保存…' : '保存系统' }}</button></footer>
    </section>
  </div>
</template>

<style scoped>
.system-drawer { width: min(560px, 100vw); }
.system-form { display: grid; gap: 17px; }
.system-field { min-width: 0; display: grid; gap: 7px; }
.system-field > span { color: #3f555d; font-size: 12px; font-weight: 700; }
.system-field input,.system-field textarea,.system-field select { width: 100%; min-width: 0; padding: 9px 10px; border: 1px solid #cdd8da; border-radius: 4px; outline-color: #147467; background: white; color: #20333e; font-size: 12px; }
.system-field input,.system-field select { min-height: 40px; }
.system-field input:disabled { border-color: #dde4e6; background: #f3f5f5; color: #68787f; cursor: not-allowed; }
.system-field textarea { min-height: 104px; resize: vertical; line-height: 1.6; }
.system-field small { color: #718189; font-size: 10px; line-height: 1.55; }
.creation-note { margin: 0; padding: 10px 12px; border-left: 3px solid #2f826f; background: #f1f8f5; color: #456a61; font-size: 11px; line-height: 1.55; }
.system-footer { min-height: 64px; padding: 11px 22px; border-top: 1px solid #e1e7e9; background: #fbfcfc; display: flex; justify-content: flex-end; align-items: center; gap: 9px; }
.feedback { min-height: 44px; margin-bottom: 12px; padding: 9px 12px; border: 1px solid #e1c0b7; border-radius: 5px; background: #fbf1ee; color: #8d432e; display: flex; align-items: center; gap: 9px; font-size: 12px; }
.feedback span { display: grid; gap: 3px; }
@media(max-width:620px){.system-footer{padding-inline:17px}.system-footer .prototype-button{flex:1}}
</style>
