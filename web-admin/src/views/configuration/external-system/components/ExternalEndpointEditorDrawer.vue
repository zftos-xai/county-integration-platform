<!-- 机构接口配置编辑抽屉：维护机构在指定环境使用的HIS接口参数。 -->
<script setup lang="ts">
import { AlertCircle, ChevronDown, Eye, EyeOff, ShieldCheck, X } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import { getExternalEndpointAuthentication } from '@/api/system/configuration'
import type { ExternalEndpoint, ExternalSystem } from '@/api/system/configuration'
import { ApiClientError, isWriteResultUncertain } from '@/utils/request'
import { useModalDialog } from '@/composables/useModalDialog'
import type { ExternalEndpointForm } from '../form'

const props = defineProps<{
  system: ExternalSystem
  existing: ExternalEndpoint | null
  organizations: { id: number; organizationCode: string; organizationName: string; enabled: boolean }[]
  canWrite: boolean
  isSaving: boolean
  error: ApiClientError | null
  formError: string
}>()
const emit = defineEmits<{ close: []; submit: []; reload: [] }>()
const form = defineModel<ExternalEndpointForm>('form', { required: true })
const isOpen = ref(true)
const { dialogRef, handleDialogKeydown } = useModalDialog(isOpen, () => emit('close'))
const replaceAuthentication = ref(!props.existing?.credentialConfigured)
const organizationChanged = computed(() => props.existing !== null
  && props.existing.organizationId !== form.value.organizationId)
const showAuthenticationFields = computed(() => replaceAuthentication.value || organizationChanged.value)
const authenticationState = computed(() => props.existing?.credentialConfigured ? '已保存' : '待填写')
const authenticationHint = computed(() => organizationChanged.value
  ? '已更换机构，请填写新机构的接入信息。'
  : '请填写该机构收到的接入信息。')
const isAuthenticationLoading = ref(false)
const authenticationError = ref('')
const showAuthorizationCode = ref(false)
const showPassword = ref(false)

/** 标记接入信息已由管理员修改，保存时才向后端提交新内容。 */
function markAuthenticationChanged() {
  form.value.authenticationChanged = true
}

/** 按需读取已保存内容；敏感字段只保存在当前抽屉内存中。 */
async function revealAuthentication() {
  if (!props.existing || isAuthenticationLoading.value) return
  isAuthenticationLoading.value = true
  authenticationError.value = ''
  try {
    const authentication = await getExternalEndpointAuthentication(props.existing.id)
    form.value.vendorCode = authentication.vendorCode
    form.value.username = authentication.username
    form.value.password = authentication.password
    form.value.authorizationCode = authentication.authorizationCode
    form.value.authenticationChanged = false
    replaceAuthentication.value = true
  } catch (caught) {
    authenticationError.value = caught instanceof ApiClientError
      ? caught.message
      : '无法读取已保存的接入信息，请稍后重试'
  } finally {
    isAuthenticationLoading.value = false
  }
}

watch(() => form.value.organizationId, (current, previous) => {
  if (!props.existing || current === previous || current === props.existing.organizationId) return
  form.value.vendorCode = ''
  form.value.username = ''
  form.value.password = ''
  form.value.authorizationCode = ''
  form.value.authenticationChanged = true
  replaceAuthentication.value = true
  showAuthorizationCode.value = false
  showPassword.value = false
})
</script>

<template>
  <div class="work-drawer-backdrop" @mousedown.self="emit('close')">
    <section ref="dialogRef" class="work-drawer endpoint-drawer" role="dialog" aria-modal="true" aria-labelledby="endpoint-editor-title" tabindex="-1" @keydown="handleDialogKeydown">
      <header class="work-drawer-header"><div><small>{{ system.systemName }}</small><h2 id="endpoint-editor-title">{{ existing ? '编辑机构接口配置' : '新增机构接口配置' }}</h2></div><button class="prototype-icon" type="button" aria-label="关闭" @click="emit('close')"><X :size="18" /></button></header>
      <div class="work-drawer-body">
        <div v-if="error" class="feedback danger" role="alert"><AlertCircle :size="18" /><span><strong>{{ error.message }}</strong><small v-if="error.status === 409">接口配置已发生变化，请重新读取后再保存。</small><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button v-if="error.status === 409 || isWriteResultUncertain(error)" class="work-quiet-button" type="button" @click="emit('reload')">重新读取</button></div>
        <div v-if="formError" class="feedback danger" role="alert"><AlertCircle :size="18" />{{ formError }}</div>
        <form id="endpoint-form" class="endpoint-form" @submit.prevent="emit('submit')">
          <section class="form-step wide" aria-labelledby="endpoint-scope-title">
            <header><i>1</i><div><strong id="endpoint-scope-title">选择机构和环境</strong></div></header>
            <div class="step-grid">
              <label class="endpoint-field" for="endpoint-environment"><span>运行环境</span><select id="endpoint-environment" v-model="form.environment"><option value="DEVELOPMENT">开发环境</option><option value="TEST">测试环境</option><option value="PRODUCTION">生产环境</option></select></label>
              <label class="endpoint-field" for="endpoint-organization"><span>适用机构</span><select id="endpoint-organization" v-model="form.organizationId"><option :value="null">全部机构</option><option v-for="organization in organizations" :key="organization.id" :value="organization.id">{{ organization.organizationName }}</option></select></label>
            </div>
          </section>
          <section class="form-step wide" aria-labelledby="endpoint-address-title">
            <header><i>2</i><div><strong id="endpoint-address-title">填写 HIS 接口地址</strong></div></header>
            <label class="endpoint-field" for="endpoint-base-url"><span>接口地址</span><input id="endpoint-base-url" v-model="form.baseUrl" maxlength="500" placeholder="请输入该机构的 HIS 接口地址" autocomplete="url" /></label>
          </section>
          <section class="authentication-section wide" aria-labelledby="endpoint-authentication-title">
            <header><i>3</i><div><strong id="endpoint-authentication-title">填写 HIS 接入信息</strong></div><span class="authentication-status" :class="{ configured: existing?.credentialConfigured }">{{ authenticationState }}</span></header>
            <div v-if="existing?.credentialConfigured && !showAuthenticationFields" class="credential-summary">
              <ShieldCheck :size="17" />
              <span><strong>接入信息已保存</strong><small>需要核对或修改时再查看。</small></span>
              <button class="work-quiet-button" type="button" :disabled="isAuthenticationLoading" @click="revealAuthentication">{{ isAuthenticationLoading ? '正在读取…' : '查看或修改' }}</button>
            </div>
            <div v-if="authenticationError" class="authentication-error" role="alert"><AlertCircle :size="15" />{{ authenticationError }}</div>
            <template v-if="showAuthenticationFields || !existing?.credentialConfigured">
              <div class="credential-boundary"><ShieldCheck :size="16" /><span>{{ authenticationHint }}</span></div>
              <div class="authentication-grid">
                <label class="endpoint-field" for="endpoint-vendor-code"><span>厂商编号 <em>必填</em></span><input id="endpoint-vendor-code" v-model="form.vendorCode" maxlength="100" autocomplete="off" placeholder="请输入" @input="markAuthenticationChanged" /></label>
                <label class="endpoint-field" for="endpoint-authorization-code"><span>HIS 验证码 <em>必填</em></span><span class="secret-input"><input id="endpoint-authorization-code" v-model="form.authorizationCode" maxlength="200" :type="showAuthorizationCode ? 'text' : 'password'" autocomplete="new-password" placeholder="请输入" @input="markAuthenticationChanged" /><button type="button" :aria-label="showAuthorizationCode ? '隐藏HIS验证码' : '查看HIS验证码'" @click="showAuthorizationCode = !showAuthorizationCode"><EyeOff v-if="showAuthorizationCode" :size="16" /><Eye v-else :size="16" /></button></span></label>
              </div>
              <details class="login-settings">
              <summary><span>登录账号（选填）</span><ChevronDown :size="15" aria-hidden="true" /></summary>
              <div class="authentication-grid">
                <label class="endpoint-field" for="endpoint-username"><span>HIS 用户名</span><input id="endpoint-username" v-model="form.username" maxlength="100" autocomplete="off" placeholder="请输入" @input="markAuthenticationChanged" /></label>
                <label class="endpoint-field" for="endpoint-password"><span>HIS 密码</span><span class="secret-input"><input id="endpoint-password" v-model="form.password" maxlength="200" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="请输入" @input="markAuthenticationChanged" /><button type="button" :aria-label="showPassword ? '隐藏HIS密码' : '查看HIS密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="16" /><Eye v-else :size="16" /></button></span></label>
              </div>
              </details>
            </template>
          </section>
          <details class="advanced-settings wide">
            <summary><span>高级设置</span><small>一般无需修改</small><ChevronDown :size="15" aria-hidden="true" /></summary>
            <div class="step-grid">
              <label class="endpoint-field" for="endpoint-connect-timeout"><span>连接超时</span><div class="number-input"><input id="endpoint-connect-timeout" v-model.number="form.connectTimeoutMs" type="number" min="100" max="60000" /><em>毫秒</em></div></label>
              <label class="endpoint-field" for="endpoint-read-timeout"><span>读取超时</span><div class="number-input"><input id="endpoint-read-timeout" v-model.number="form.readTimeoutMs" type="number" min="100" max="300000" /><em>毫秒</em></div></label>
            </div>
          </details>
          <div v-if="system.systemCode === 'PRIMARY_HIS'" class="endpoint-ready-note wide"><ShieldCheck :size="17" /><span><strong>保存后自动校验</strong><small>系统将用 100-008 确认 HIS 返回的唯一机构；校验通过后才能用于数据同步。</small></span></div>
        </form>
      </div>
      <footer class="endpoint-footer"><button class="work-quiet-button" type="button" @click="emit('close')">取消</button><button v-if="canWrite" class="prototype-button" type="submit" form="endpoint-form" :disabled="isSaving">{{ isSaving ? '正在保存…' : '保存配置' }}</button></footer>
    </section>
  </div>
</template>

<style scoped>
.endpoint-drawer { width: min(680px, 100vw); }
.work-drawer-body { padding-top: 16px; }
.form-step > header > i,.authentication-section > header > i { width: 19px; height: 19px; border-radius: 50%; background: #dceee8; color: #147467; display: inline-flex; align-items: center; justify-content: center; flex: none; font-size: 10px; font-style: normal; }
.credential-boundary { margin: 0; padding: 10px 12px; border-bottom: 1px solid #e5eaec; background: #f3f8f6; color: #477066; display: flex; align-items: center; gap: 8px; font-size: 10px; line-height: 1.5; }
.credential-boundary svg { flex: none; }
.credential-summary { min-height: 64px; padding: 11px 12px; color: #477066; display: flex; align-items: center; gap: 10px; }
.credential-summary > span { min-width: 0; display: grid; gap: 2px; }
.credential-summary strong { color: #304850; font-size: 11px; }
.credential-summary small { color: #718189; font-size: 10px; line-height: 1.5; }
.credential-summary .work-quiet-button { margin-left: auto; flex: none; }
.authentication-error { margin: 10px 12px 0; padding: 8px 10px; border: 1px solid #e1c0b7; border-radius: 4px; background: #fbf1ee; color: #8d432e; display: flex; align-items: center; gap: 7px; font-size: 10px; }
.endpoint-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.form-step { padding: 13px; border: 1px solid #d8e1e3; border-radius: 5px; background: #fff; display: grid; gap: 13px; }
.form-step > header,.authentication-section > header { display: flex; align-items: center; gap: 9px; }
.form-step > header div,.authentication-section > header div { min-width: 0; display: grid; gap: 2px; }
.form-step > header strong,.authentication-section > header strong { color: #304850; font-size: 12px; }
.form-step > header small,.authentication-section > header small { color: #718189; font-size: 10px; }
.step-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; }
.endpoint-field { min-width: 0; display: grid; align-content: start; gap: 7px; }
.endpoint-field > span { min-height: 18px; color: #3f555d; display: flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 700; }
.endpoint-field > span em { color: #7d8b90; font-size: 9px; font-style: normal; font-weight: 500; }
.endpoint-field input,.endpoint-field select { width: 100%; min-width: 0; min-height: 40px; padding: 8px 10px; border: 1px solid #cdd8da; border-radius: 4px; outline-color: #147467; background: #fff; color: #20333e; font-size: 12px; }
.secret-input { position: relative; display: block; }
.secret-input input { padding-right: 40px; }
.secret-input button { position: absolute; top: 50%; right: 5px; width: 31px; height: 31px; padding: 0; border: 0; border-radius: 4px; background: transparent; color: #60737a; display: inline-flex; align-items: center; justify-content: center; cursor: pointer; transform: translateY(-50%); }
.secret-input button:hover { background: #edf4f2; color: #147467; }
.secret-input button:focus-visible { outline: 2px solid #147467; outline-offset: 1px; }
.endpoint-field input:disabled,.endpoint-field select:disabled { border-color: #dde4e6; background: #f3f5f5; color: #68787f; cursor: not-allowed; }
.endpoint-field small { color: #718189; font-size: 10px; line-height: 1.6; }
.endpoint-field small strong { color: #53676f; }
.wide { grid-column: 1 / -1; }
.authentication-section { overflow: hidden; border: 1px solid #d8e1e3; border-radius: 5px; background: #fbfcfc; }
.authentication-section > header { min-height: 50px; padding: 10px 12px; border-bottom: 1px solid #e5eaec; }
.authentication-section > header .authentication-status { margin-left: auto; }
.authentication-status { padding: 3px 8px; border-radius: 999px; background: #f8eee8; color: #a1583e; font-size: 10px; font-weight: 650; }
.authentication-status.configured { background: #e8f4ef; color: #14705b; }
.authentication-grid { padding: 12px; display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px 16px; }
.login-settings { border-top: 1px solid #e5eaec; }
.login-settings summary,.advanced-settings summary { min-height: 42px; padding: 9px 12px; color: #51666e; display: flex; align-items: center; gap: 7px; cursor: pointer; font-size: 11px; font-weight: 650; list-style: none; }
.login-settings summary::-webkit-details-marker,.advanced-settings summary::-webkit-details-marker { display: none; }
.login-settings summary svg,.advanced-settings summary svg { margin-left: auto; transition: transform .16s ease; }
.login-settings[open] summary svg,.advanced-settings[open] summary svg { transform: rotate(180deg); }
.advanced-settings { overflow: hidden; border: 1px solid #d8e1e3; border-radius: 5px; background: #f8faf9; }
.advanced-settings summary small { color: #7b898f; font-size: 9px; font-weight: 500; }
.advanced-settings .step-grid { padding: 2px 12px 12px; }
.endpoint-ready-note { min-height: 58px; padding: 10px 12px; border: 1px solid #cfe4dd; border-radius: 5px; background: #f2f8f6; color: #477066; display: flex; align-items: center; gap: 10px; }
.endpoint-ready-note > span { display: grid; gap: 3px; }.endpoint-ready-note strong { color: #2d6257; font-size: 12px; }.endpoint-ready-note small { color: #52776e; font-size: 10px; line-height: 1.5; }
.number-input { position: relative; }
.number-input input { padding-right: 48px; }
.number-input em { position: absolute; top: 50%; right: 11px; color: #7b898f; font-size: 10px; font-style: normal; transform: translateY(-50%); pointer-events: none; }
.endpoint-footer { min-height: 64px; padding: 11px 22px; border-top: 1px solid #e1e7e9; background: #fbfcfc; display: flex; justify-content: flex-end; align-items: center; gap: 9px; }
.feedback { min-height: 44px; margin-bottom: 14px; padding: 9px 12px; border: 1px solid #e1c0b7; border-radius: 5px; background: #fbf1ee; color: #8d432e; display: flex; align-items: center; gap: 9px; font-size: 12px; }
.feedback span { display: grid; gap: 3px; }
@media(max-width:620px){.endpoint-form,.authentication-grid,.step-grid{grid-template-columns:1fr}.wide{grid-column:auto}.endpoint-footer{padding-inline:17px}.endpoint-footer .prototype-button{flex:1}}
</style>
