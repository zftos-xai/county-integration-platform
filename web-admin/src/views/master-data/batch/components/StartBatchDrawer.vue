<!-- 基础数据同步窗口：从已验证HIS来源选择业务及必要的来源查询范围后立即执行。 -->
<script setup lang="ts">
import {
  AlertCircle,
  LoaderCircle,
  X,
} from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { watch } from 'vue'
import { useModalDialog } from '@/composables/useModalDialog'
import type { ApiClientError } from '@/utils/request'
import type {
  MasterDataEnvironment,
  MasterDataSyncOptions,
} from '@/api/master-data/batch'
import { previewFullSyncRange, type MasterDataBatchForm } from '../form'

const props = defineProps<{
  options: MasterDataSyncOptions
  isSourceLoading: boolean
  sourceError: ApiClientError | null
  isSaving: boolean
  error: ApiClientError | null
  formError: string
}>()
const form = defineModel<MasterDataBatchForm>('form', { required: true })
const fullRangePreview = ref(previewFullSyncRange(new Date()))
const emit = defineEmits<{ close: []; submit: []; reload: []; configure: []; retrySources: [] }>()
const isOpen = ref(true)
const { dialogRef, handleDialogKeydown } = useModalDialog(isOpen, () =>
  emit('close'),
)
/** 无法确认写入结果时，允许使用原请求编号回查，避免重复创建批次。 */
const canRecoverResult = computed(
  () =>
    props.error?.status === 409 ||
    props.error?.code === 'REQUEST_TIMEOUT' ||
    props.error?.code === 'NETWORK_ERROR',
)

/** 当前机构实际启用且认证信息可解析的HIS接口环境。 */
const availableEnvironments = computed(
  () =>
    props.options.sources.find(
      (item) => item.organizationCode === form.value.organizationCode,
    )?.environments ?? [],
)

/** 当前表单选择的唯一可执行同步业务。 */
const selectedBusiness = computed(() =>
  props.options.businesses.find(
    (item) => item.category === form.value.category,
  ),
)

/** 将接口环境代码转换为业务人员可读名称。 */
function environmentLabel(environment: MasterDataEnvironment) {
  if (environment === 'PRODUCTION') return '生产环境'
  if (environment === 'TEST') return '测试环境'
  return '开发环境'
}

watch(
  () => form.value.organizationCode,
  () => {
    if (!availableEnvironments.value.includes(form.value.environment)) {
      const firstEnvironment = availableEnvironments.value[0]
      if (firstEnvironment) form.value.environment = firstEnvironment
    }
  },
  { immediate: true },
)

watch(
  () => props.options.sources,
  (sources) => {
    if (sources.length && !sources.some((source) => source.organizationCode === form.value.organizationCode)) {
      form.value.organizationCode = sources[0].organizationCode
    }
  },
)

watch(
  () => props.options.businesses,
  (businesses) => {
    if (businesses.length && !businesses.some((business) => business.category === form.value.category)) {
      form.value.category = businesses[0].category
    }
  },
)

watch(
  () => form.value.category,
  (category) => {
    form.value.mode = category === 'MEDICAL_DIRECTORY' ? 'TIME_RANGE' : 'NOT_APPLICABLE'
    form.value.rangeStart = ''
    form.value.rangeEnd = ''
  },
)

watch(
  () => form.value.mode,
  (mode) => {
    if (mode === 'FULL') fullRangePreview.value = previewFullSyncRange(new Date())
  },
)

</script>

<template>
  <div class="batch-dialog-backdrop" @mousedown.self="emit('close')">
    <section
      ref="dialogRef"
      class="batch-start-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="batch-start-title"
      tabindex="-1"
      @keydown="handleDialogKeydown"
    >
      <header class="batch-dialog-header">
        <div>
          <h2 id="batch-start-title">发起基础数据同步</h2>
        </div>
        <button
          class="prototype-icon"
          type="button"
          aria-label="关闭"
          @click="emit('close')"
        >
          <X :size="18" />
        </button>
      </header>
      <div class="batch-dialog-body">
        <div v-if="error" class="feedback danger" role="alert">
          <AlertCircle :size="18" /><span
            ><strong>{{ error.message }}</strong
            ><small v-if="error.requestId"
              >请求编号：{{ error.requestId }}</small
            ></span
          ><button
            v-if="canRecoverResult"
            class="text-button"
            type="button"
            @click="emit('reload')"
          >
            读取现有批次
          </button>
        </div>
        <div v-if="formError" class="feedback danger" role="alert">
          <AlertCircle :size="18" /><span>{{ formError }}</span>
        </div>
        <section v-if="isSourceLoading" class="sync-source-unavailable" role="status">
          <LoaderCircle class="spinning" :size="20" />
          <div><strong>正在读取可同步的机构</strong></div>
        </section>
        <section v-else-if="sourceError" class="sync-source-unavailable" role="alert">
          <AlertCircle :size="20" />
          <div>
            <strong>暂时无法确认可同步的机构</strong>
            <span>{{ sourceError.message }}；可用机构尚未确认。<template v-if="sourceError.requestId">请求编号：{{ sourceError.requestId }}</template></span>
          </div>
          <button class="work-quiet-button" type="button" @click="emit('retrySources')">重试读取</button>
        </section>
        <section v-else-if="options.sources.length === 0" class="sync-source-unavailable">
          <AlertCircle :size="20" />
          <div>
            <strong>当前还不能发起同步</strong>
            <span>当前账号未取得可用的基层 HIS 机构和接口环境；请核对机构授权与接口状态。机构编码和名称只在“机构管理”维护。</span>
          </div>
          <button class="work-quiet-button" type="button" @click="emit('configure')">查看接口配置</button>
        </section>
        <section v-else-if="options.businesses.length === 0" class="sync-source-unavailable">
          <AlertCircle :size="20" />
          <div>
            <strong>当前没有已开放的同步业务</strong>
            <span>机构接口已可用，但尚无可执行的目录同步业务；请联系管理员确认业务接入状态。</span>
          </div>
        </section>
        <form
          v-else
          class="batch-start-form"
          @submit.prevent="emit('submit')"
        >
          <section class="batch-form-section" aria-label="HIS 数据来源">
            <label class="batch-form-field">
              <span>HIS 来源机构</span>
              <select v-model="form.organizationCode" required>
                <option value="" disabled>请选择机构</option>
                <option
                  v-for="item in options.sources"
                  :key="item.organizationCode"
                  :value="item.organizationCode"
                >
                  {{ item.organizationName }}
                </option>
              </select>
            </label>
          </section>

          <section class="batch-form-section" aria-label="同步设置">
            <div class="batch-setup-grid">
              <label class="batch-form-field">
                <span>同步业务</span>
                <select v-model="form.category" required>
                  <option
                    v-for="business in options.businesses"
                    :key="business.category"
                    :value="business.category"
                  >
                    {{ business.name }}
                  </option>
                </select>
              </label>
              <label class="batch-form-field">
                <span>接口环境</span>
                <select v-model="form.environment" required>
                  <option
                    v-for="environment in availableEnvironments"
                    :key="environment"
                    :value="environment"
                  >
                    {{ environmentLabel(environment) }}
                  </option>
                </select>
              </label>
            </div>
            <small v-if="selectedBusiness" class="batch-field-help">
              HIS 交易码：{{ selectedBusiness.tradeCode }}<template v-if="selectedBusiness.countTradeCode"> / {{ selectedBusiness.countTradeCode }}</template>
            </small>
            <fieldset v-if="selectedBusiness?.requiresTimeRange" class="batch-mode-field">
              <legend>本次同步方式</legend>
              <div class="batch-mode-options">
                <label class="batch-mode-option" :class="{ 'is-selected': form.mode === 'FULL' }">
                  <input v-model="form.mode" type="radio" value="FULL" />
                  <span>全量同步</span>
                </label>
                <label class="batch-mode-option" :class="{ 'is-selected': form.mode === 'TIME_RANGE' }">
                  <input v-model="form.mode" type="radio" value="TIME_RANGE" />
                  <span>指定时间范围</span>
                </label>
              </div>
            </fieldset>
            <div v-if="selectedBusiness?.requiresTimeRange" class="batch-range-field">
              <strong>查询时间范围 <span>（北京时间{{ form.mode === 'FULL' ? ' · 预计' : '' }}）</span></strong>
              <div class="batch-range-grid">
                <label class="batch-form-field"><span>开始时间</span><input v-if="form.mode === 'FULL'" :value="fullRangePreview.start" type="datetime-local" readonly /><input v-else v-model="form.rangeStart" type="datetime-local" required /></label>
                <label class="batch-form-field"><span>结束时间</span><input v-if="form.mode === 'FULL'" :value="fullRangePreview.end" type="datetime-local" readonly /><input v-else v-model="form.rangeEnd" type="datetime-local" required /></label>
              </div>
              <small v-if="form.mode === 'FULL'">按创建时刻向前 20 年查询；以上为预览，实际范围以创建后的批次记录为准。更早或无时间记录不在范围内。</small>
              <small v-else>数量核对与目录查询使用同一范围，请按 HIS 提供方确认的口径填写。</small>
            </div>
          </section>
        </form>
      </div>
      <footer class="batch-dialog-footer">
        <button
          class="work-quiet-button"
          type="button"
          :disabled="isSaving"
          @click="emit('close')"
        >
          取消</button
        ><button
          v-if="!isSourceLoading && !sourceError && options.sources.length > 0 && options.businesses.length > 0"
          class="prototype-button"
          type="button"
          :disabled="isSaving"
          @click="emit('submit')"
        >
          <LoaderCircle v-if="isSaving" class="spinning" :size="15" />{{
            isSaving ? '正在取得并校验' : '开始取得并校验'
          }}
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.batch-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  padding: 24px;
  display: grid;
  place-items: center;
  background: rgb(19 31 43 / 42%);
}
.batch-start-dialog {
  width: min(620px, 100%);
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 24px 70px rgb(20 34 44 / 24%);
}
.batch-dialog-header {
  padding: 18px 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid #e1e7e9;
}
.batch-dialog-header h2 { margin: 0; color: #1f3039; font-size: 20px; }
.batch-dialog-body { padding: 16px 22px 18px; overflow-y: auto; }
.sync-source-unavailable { padding: 14px; border: 1px solid #ead6a8; border-radius: 6px; background: #fffaf0; display: grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:start; gap:10px; color:#805819; }
.sync-source-unavailable>div { display:grid; gap:4px; }.sync-source-unavailable strong { color:#6f4d16; font-size:14px; }.sync-source-unavailable span { font-size:12px; line-height:1.6; }.sync-source-unavailable button { white-space:nowrap; }
.batch-start-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
}
.batch-form-section { min-width: 0; }
.batch-form-section + .batch-form-section { padding-top: 14px; border-top: 1px solid #e1e7e9; }
.batch-start-form .batch-form-field {
  display: grid;
  min-width: 0;
  gap: 6px;
  color: #40545d;
  font-size: 12px;
  font-weight: 650;
}
.batch-start-form select,
.batch-start-form input[type="datetime-local"] {
  width: 100%;
  min-height: 40px;
  padding: 0 11px;
  border: 1px solid #cfd9dd;
  border-radius: 5px;
  background: #fff;
  color: #263741;
  font-size: 12px;
  outline-color: var(--accent);
}
.batch-start-form input[readonly] {
  background: #f5f9f8;
  color: #35545d;
}
.batch-setup-grid,
.batch-range-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.batch-field-help { display: block; margin-top: 6px; color: #718089; font-size: 11px; line-height: 1.5; }
.batch-mode-field { min-width: 0; margin: 12px 0 0; padding: 0; border: 0; }
.batch-mode-field legend { margin-bottom: 8px; padding: 0; color: #40545d; font-size: 12px; font-weight: 700; }
.batch-mode-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.batch-start-form .batch-mode-option { min-height: 42px; padding: 8px 11px; display: flex; align-items: center; gap: 9px; border: 1px solid #cfd9dd; border-radius: 5px; background: #fff; cursor: pointer; }
.batch-mode-option.is-selected { border-color: #70ae9f; background: #f1f8f6; color: #176f61; }
.batch-mode-option input[type="radio"] { width: 16px; height: 16px; min-height: 0; margin: 0; padding: 0; flex: none; accent-color: #176f61; }
.batch-range-field { margin-top: 12px; display: grid; gap: 6px; color: #40545d; }
.batch-range-field > strong { font-size: 12px; }
.batch-range-field > strong span { color: #718089; font-weight: 500; }
.batch-range-field > small { color: #718089; font-size: 11px; line-height: 1.5; }
.batch-dialog-footer {
  min-height: 64px;
  padding: 12px 22px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9px;
  border-top: 1px solid #e1e7e9;
  background: #fbfcfc;
}
.text-button {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: #176f61;
  font-weight: 650;
}
@media (max-width: 640px) {
  .batch-dialog-backdrop { padding: 0; place-items: end center; }
  .batch-start-dialog { width: 100%; max-height: 92vh; border-radius: 10px 10px 0 0; }
  .batch-setup-grid,
  .batch-range-grid { grid-template-columns: 1fr; }
}
@media (max-width: 420px) {
  .batch-mode-options { grid-template-columns: 1fr; }
}
</style>
