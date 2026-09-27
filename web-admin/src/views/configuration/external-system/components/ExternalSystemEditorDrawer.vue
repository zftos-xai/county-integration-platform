<!-- 外部系统侧边编辑窗口：维护系统身份、用途和启用状态。 -->
<script setup lang="ts">
import { AlertCircle, Copy, KeyRound, RotateCw } from 'lucide-vue-next'
import type { ApiClientError } from '@/utils/request'
import { isWriteResultUncertain } from '@/utils/request'
import DrawerFrame from '@/components/DrawerFrame.vue'
import type { ExternalSystemForm } from '../form'

const props = defineProps<{
  creating: boolean
  canWrite: boolean
  inboundKeyConfigured: boolean
  issuedInboundKey: string | null
  isRotatingInboundKey: boolean
  isSaving: boolean
  error: ApiClientError | null
  formError: string
}>()
const emit = defineEmits<{ close: []; submit: []; reload: []; rotateInboundKey: [] }>()
const form = defineModel<ExternalSystemForm>('form', { required: true })

/** 将本次签发的调用Key复制到剪贴板；失败时保留页面中的可选文本供手动复制。 */
async function copyIssuedKey() {
  if (!props.issuedInboundKey || !navigator.clipboard) return
  try {
    await navigator.clipboard.writeText(props.issuedInboundKey)
  } catch {
    // 浏览器权限受限时，Key仍显示在只读字段中，可由管理员手动复制。
  }
}
</script>

<template>
  <DrawerFrame panel-class="system-drawer" panel-width="min(560px, 100vw)" labelled-by="external-system-editor-title" @close="emit('close')">
      <template #title><div><small>{{ creating ? '外部系统身份' : form.systemCode }}</small><h2 id="external-system-editor-title">{{ creating ? '登记外部系统' : '系统资料' }}</h2></div></template>
        <div v-if="error" class="feedback danger" role="alert"><AlertCircle :size="18" /><span><strong>{{ error.message }}</strong><small v-if="error.status === 409">配置已经变化，请重新读取最新版本。</small><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button v-if="error.status === 409 || isWriteResultUncertain(error)" class="work-quiet-button" type="button" @click="emit('reload')">重新读取</button></div>
        <div v-if="formError" class="feedback danger" role="alert"><AlertCircle :size="18" />{{ formError }}</div>
        <form id="system-form" class="system-form" @submit.prevent="emit('submit')">
          <label class="system-field" for="external-system-code"><span>系统编码</span><input id="external-system-code" v-model="form.systemCode" maxlength="64" :disabled="!creating" placeholder="例如 PRIMARY_HIS" autocomplete="off" /><small>稳定编码用于选择运行适配器，保存后不可修改。</small></label>
          <label class="system-field" for="external-system-name"><span>系统名称</span><input id="external-system-name" v-model="form.systemName" maxlength="100" placeholder="请输入业务人员可识别的名称" /></label>
          <label class="system-field" for="external-system-description"><span>用途说明</span><textarea id="external-system-description" v-model="form.description" maxlength="500" rows="4" placeholder="说明系统来源、使用场景和维护责任" /></label>
          <label v-if="!creating" class="system-field" for="external-system-enabled"><span>使用状态</span><select id="external-system-enabled" v-model="form.enabled"><option :value="true">启用</option><option :value="false">停用</option></select><small>{{ form.enabled ? '该系统的已启用接口配置可以正常使用。' : '停用后，各机构的接口配置暂时不可用。' }}</small></label>
          <section v-if="!creating" class="inbound-key" aria-labelledby="inbound-key-title">
            <div class="key-heading"><KeyRound :size="16" /><strong id="inbound-key-title">县医院调用平台 Key</strong></div>
            <p>用于县医院识别平台调用方身份。此 Key 与下方机构接口的出站凭证相互独立。</p>
            <div class="key-status"><span :class="inboundKeyConfigured ? 'configured' : 'missing'">{{ inboundKeyConfigured ? '已设置' : '未设置' }}</span><small>系统停用时，入站调用仍会被拒绝。</small></div>
            <div v-if="issuedInboundKey" class="issued-key" role="status">
              <strong>新 Key 已签发，请立即复制并安全交付</strong>
              <div class="issued-key-row"><input aria-label="本次新签发的调用 Key" :value="issuedInboundKey" readonly autocomplete="off" /><button class="work-quiet-button" type="button" @click="copyIssuedKey"><Copy :size="15" />复制</button></div>
              <small>关闭此窗口后不再显示。轮换后旧 Key 已立即失效。</small>
            </div>
            <button v-if="canWrite" class="work-quiet-button rotate-key" type="button" :disabled="isRotatingInboundKey || isSaving" @click="emit('rotateInboundKey')">
              <RotateCw :size="15" />{{ isRotatingInboundKey ? '正在轮换…' : inboundKeyConfigured ? '轮换调用 Key' : '生成调用 Key' }}
            </button>
          </section>
          <p v-if="creating" class="creation-note">保存系统后，再为各机构添加接口配置。</p>
        </form>
      <template #footer><button class="work-quiet-button" type="button" @click="emit('close')">取消</button><button v-if="canWrite" class="prototype-button" type="submit" form="system-form" :disabled="isSaving">{{ isSaving ? '正在保存…' : '保存系统' }}</button></template>
  </DrawerFrame>
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
.inbound-key { display: grid; gap: 9px; padding: 13px; border: 1px solid #dbe4e6; border-radius: 5px; background: #f8fafa; color: #43555e; }
.key-heading,.key-status,.issued-key-row { display: flex; align-items: center; gap: 8px; }
.key-heading { color: #29444b; font-size: 12px; }
.inbound-key p,.inbound-key small { margin: 0; color: #718189; font-size: 10px; line-height: 1.55; }
.key-status { justify-content: space-between; }
.key-status > span { padding: 3px 7px; border-radius: 3px; background: #e7f4ed; color: #26724f; font-size: 10px; font-weight: 700; }
.key-status > span.missing { background: #fff2dd; color: #946216; }
.issued-key { display: grid; gap: 7px; padding: 10px; border: 1px solid #e7d49e; border-radius: 4px; background: #fffaf0; color: #72551a; font-size: 11px; }
.issued-key-row input { flex: 1; min-width: 0; padding: 8px; border: 1px solid #d8d0b7; border-radius: 3px; background: white; color: #293d45; font: 11px ui-monospace, monospace; }
.rotate-key { justify-self: start; }
.feedback { min-height: 44px; margin-bottom: 12px; padding: 9px 12px; border: 1px solid #e1c0b7; border-radius: 5px; background: #fbf1ee; color: #8d432e; display: flex; align-items: center; gap: 9px; font-size: 12px; }
.feedback span { display: grid; gap: 3px; }
</style>
