<!-- 参数侧边编辑窗口：只提供系统已登记的适用范围和值输入。 -->
<script setup lang="ts">
import { AlertCircle, ShieldAlert } from 'lucide-vue-next'
import { computed } from 'vue'
import type { ParameterDefinition, ParameterValue } from '@/api/system/configuration'
import type { Organization } from '@/api/system/organization'
import DrawerFrame from '@/components/DrawerFrame.vue'
import { isWriteResultUncertain } from '@/utils/request'
import type { ApiClientError } from '@/utils/request'
import { environmentLabel, parameterValueTypeLabel } from '@/utils/managementDisplay'
import type { ParameterForm } from '../form'

const props = defineProps<{
  definition: ParameterDefinition
  existing: ParameterValue | null
  organizations: Organization[]
  canWrite: boolean
  isSaving: boolean
  error: ApiClientError | null
  formError: string
}>()
const emit = defineEmits<{ close: []; submit: []; reload: [] }>()
const form = defineModel<ParameterForm>('form', { required: true })
const valueInputType = computed(() => props.definition.sensitive ? 'password' : 'text')
const inputRequirement = computed(() => {
  const requirements: string[] = [`填写${parameterValueTypeLabel(props.definition.valueType)}`]
  if (props.definition.maximumLength !== null) requirements.push(`最多 ${props.definition.maximumLength} 个字符`)
  if (props.definition.minimumNumber !== null) requirements.push(`不能小于 ${props.definition.minimumNumber}`)
  if (props.definition.maximumNumber !== null) requirements.push(`不能大于 ${props.definition.maximumNumber}`)
  return requirements.join('；')
})
const hasChanges = computed(() => !props.existing || (props.definition.sensitive
  ? Boolean(form.value.value) || form.value.enabled !== props.existing.enabled
  : form.value.value !== props.existing.value || form.value.enabled !== props.existing.enabled))
</script>

<template>
  <DrawerFrame panel-class="parameter-drawer" panel-width="min(600px, 100vw)" labelled-by="parameter-editor-title" @close="emit('close')">
      <template #title><small>平台注册参数</small><h2 id="parameter-editor-title">{{ definition.name }}</h2></template>
      <template #subheader><div class="work-drawer-sub"><code>{{ definition.key }}</code><span class="prototype-tag neutral">{{ parameterValueTypeLabel(definition.valueType) }}</span></div></template>
        <div v-if="definition.sensitive" class="work-data-boundary"><ShieldAlert :size="18" /><span><strong>敏感参数不会回显</strong><small>{{ existing ? '如需修改，必须输入新值覆盖原值；保存后仍只显示固定掩码。' : '首次配置必须输入值，保存后页面只显示固定掩码。' }}</small></span></div>
        <div v-if="error" class="feedback danger" role="alert"><AlertCircle :size="18" /><span><strong>{{ error.message }}</strong><small v-if="error.status === 409">参数已变化，请重新读取最新版本。</small><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button v-if="error.status === 409 || isWriteResultUncertain(error)" class="work-quiet-button" type="button" @click="emit('reload')">重新读取</button></div>
        <div v-if="formError" class="feedback danger" role="alert"><AlertCircle :size="18" />{{ formError }}</div>
        <form id="parameter-editor-form" class="work-form" @submit.prevent="emit('submit')">
          <label for="parameter-environment">运行环境</label><select id="parameter-environment" v-model="form.environment" :disabled="Boolean(existing)"><option v-for="environment in definition.environments" :key="environment" :value="environment">{{ environmentLabel(environment) }}</option></select>
          <template v-if="definition.organizationScoped">
            <label for="parameter-organization">所属机构</label>
            <select id="parameter-organization" v-model="form.organizationId" :disabled="Boolean(existing) || organizations.length === 0"><option v-for="organization in organizations" :key="organization.id" :value="organization.id">{{ organization.organizationName }}（{{ organization.organizationCode }}）</option></select>
          </template>
          <label for="parameter-value">参数值</label>
          <select v-if="definition.valueType === 'BOOLEAN'" id="parameter-value" v-model="form.value"><option value="true">是</option><option value="false">否</option></select>
          <input v-else id="parameter-value" v-model="form.value" :type="valueInputType" :maxlength="definition.maximumLength ?? 1000" autocomplete="new-password" :placeholder="definition.sensitive && existing ? '输入新值以覆盖原值' : '请输入参数值'" />
          <div class="work-inline-note">填写要求：{{ inputRequirement }}</div>
          <label for="parameter-enabled">使用状态</label><label class="parameter-check"><input id="parameter-enabled" v-model="form.enabled" type="checkbox" />启用当前按适用范围保存的值</label>
          <div class="work-inline-note">参数键、值类型和约束来自后端代码注册。页面不会创建任意参数键。</div>
        </form>
      <template #footer>
        <button class="work-quiet-button" type="button" @click="emit('close')">取消</button>
        <button v-if="canWrite" class="prototype-button" type="submit" form="parameter-editor-form" :disabled="isSaving || !hasChanges">{{ isSaving ? '正在保存…' : '保存参数' }}</button>
      </template>
  </DrawerFrame>
</template>

<style scoped>
.parameter-drawer { width: min(600px, 100vw); }
.work-drawer-sub code { color: #315a54; overflow-wrap: anywhere; }
.parameter-check { display: flex; align-items: center; gap: 8px; font-weight: 400; }
.parameter-check input { width: 16px; accent-color: #147467; }
.feedback { min-height: 44px; margin-bottom: 12px; padding: 9px 12px; border: 1px solid #e1c0b7; border-radius: 5px; background: #fbf1ee; color: #8d432e; display: flex; align-items: center; gap: 9px; font-size: 12px; }
.feedback span { display: grid; gap: 3px; }
</style>
