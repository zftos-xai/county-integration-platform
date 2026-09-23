<!-- 同步记录详情：展示已落库事实，并允许有同步权限的人员受控结束中断批次；不提供人工发布。 -->
<script setup lang="ts">
import { AlertCircle, CheckCircle2, RefreshCw } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getBatchExchangeRecords, getHospitalDirectorySyncResults, getIcd10HisInvocations, getIcd10SyncResults, getMasterDataBatch, getMedicalDirectorySyncResults, recoverMasterDataBatch } from '@/api/master-data/batch'
import type { BatchExchangeRecord, HospitalDirectorySyncResult, Icd10HisInvocationPage, Icd10SyncResult, MasterDataBatchSummary, MedicalDirectorySyncResult } from '@/api/master-data/batch'
import { listManagementAuditEvents } from '@/api/system/audit'
import type { ManagementAuditEvent } from '@/api/system/audit'
import { ApiClientError } from '@/utils/request'
import { hasPermission } from '@/store/modules/auth'
import DirectorySyncResultPanel from './components/DirectorySyncResultPanel.vue'
import Icd10SyncResultPanel from './components/Icd10SyncResultPanel.vue'
import Icd10HisInvocationPanel from './components/Icd10HisInvocationPanel.vue'
import BatchExchangeRecordPanel from './components/BatchExchangeRecordPanel.vue'
import MedicalDirectorySyncResultPanel from './components/MedicalDirectorySyncResultPanel.vue'
import BatchAuditPanel from './components/BatchAuditPanel.vue'
import { formatBatchDuration, formatBatchTime } from './batchTime'

const route = useRoute()
const batchId = Number(route.params.id)
const batch = ref<MasterDataBatchSummary | null>(null)
const loading = ref(false)
const directoryResults = ref<HospitalDirectorySyncResult[]>([])
const medicalDirectoryResults = ref<MedicalDirectorySyncResult[]>([])
const icd10Results = ref<Icd10SyncResult[]>([])
const hisInvocations = ref<Icd10HisInvocationPage | null>(null)
const hisInvocationLoading = ref(false)
const exchangeRecords = ref<BatchExchangeRecord[]>([])
const exchangeRecordLoading = ref(false)
const exchangeRecordError = ref('')
const auditEvents = ref<ManagementAuditEvent[]>([])
const auditLoading = ref(false)
const auditError = ref('')
const error = ref('')
const recovering = ref(false)
const confirmedRecovery = ref(false)
const recoveryMessage = ref('')
const currentTime = ref(Date.now())
let durationTimer: ReturnType<typeof setInterval> | null = null
const canRecover = computed(() => hasPermission('master-data:sync') &&
  (batch.value?.status === 'FETCHING' || batch.value?.status === 'RESULT_UNKNOWN'))
const canReadAudit = computed(() => hasPermission('audit:read'))

/** 返回当前批次对应的数据目录；公共ICD-10目录不附加机构筛选。 */
const directoryPath = computed(() => ({
  path: batch.value?.category === 'ICD10_DIAGNOSIS'
    ? '/master-data/directory/icd10'
    : batch.value?.category === 'MEDICAL_DIRECTORY'
      ? '/master-data/directory/medical'
      : '/master-data/directory',
  query: batch.value?.organizationCode ? { organizationCode: batch.value.organizationCode } : {},
}))

/** 当前批次面向用户的同步数据范围。 */
const dataScope = computed(() =>
  batch.value?.category === 'ICD10_DIAGNOSIS'
    ? '西医诊断、中医诊断'
    : batch.value?.category === 'MEDICAL_DIRECTORY'
      ? '中药、西药、诊疗、耗材'
      : '科室、医生、病区、床位',
)

/** 读取一次同步记录；查看不会再次调用HIS。 */
async function load() {
  if (loading.value || recovering.value) return
  if (!Number.isInteger(batchId) || batchId <= 0) { error.value = '同步记录编号无效'; return }
  loading.value = true; error.value = ''
  confirmedRecovery.value = false
  try {
    const summary = await getMasterDataBatch(batchId)
    batch.value = summary
    if (summary.category === 'MEDICAL_DIRECTORY') {
      medicalDirectoryResults.value = await getMedicalDirectorySyncResults(batchId)
      directoryResults.value = []
      icd10Results.value = []
      hisInvocations.value = null
    } else if (summary.category === 'ICD10_DIAGNOSIS') {
      const [results, invocations] = await Promise.all([
        getIcd10SyncResults(batchId),
        getIcd10HisInvocations(batchId),
      ])
      icd10Results.value = results
      hisInvocations.value = invocations
      directoryResults.value = []
      medicalDirectoryResults.value = []
      exchangeRecords.value = []
    } else {
      directoryResults.value = await getHospitalDirectorySyncResults(batchId)
      medicalDirectoryResults.value = []
      icd10Results.value = []
      hisInvocations.value = null
    }
    void loadExchangeRecords(summary)
    void loadAuditEvents(summary)
  }
  catch (caught) { error.value = caught instanceof ApiClientError ? caught.message : '无法读取同步记录' }
  finally { loading.value = false }
}

/** 读取机构目录批次的通用交换事实；失败不能覆盖已读取的同步结果。 */
async function loadExchangeRecords(summary: MasterDataBatchSummary) {
  exchangeRecords.value = []
  exchangeRecordError.value = ''
  if (summary.category === 'ICD10_DIAGNOSIS') return
  exchangeRecordLoading.value = true
  try {
    exchangeRecords.value = await getBatchExchangeRecords(summary.id)
  } catch (caught) {
    exchangeRecordError.value = caught instanceof ApiClientError ? caught.message : '无法读取HIS调用记录'
  } finally {
    exchangeRecordLoading.value = false
  }
}

/** 按批次号读取独立的管理审计；无权限或读取失败不能覆盖同步结果。 */
async function loadAuditEvents(summary: MasterDataBatchSummary) {
  auditEvents.value = []
  auditError.value = ''
  if (!canReadAudit.value) return
  auditLoading.value = true
  try {
    auditEvents.value = await listManagementAuditEvents({
      targetType: 'MASTER_DATA_BATCH', targetId: summary.batchNo, limit: 100,
    })
  } catch (caught) {
    auditError.value = caught instanceof ApiClientError ? caught.message : '无法读取管理审计记录'
  } finally {
    auditLoading.value = false
  }
}

/** 翻页读取已保存的ICD-10调用事实；查看不会重发HIS请求。 */
async function changeHisInvocationPage(page: number) {
  if (!batch.value || batch.value.category !== 'ICD10_DIAGNOSIS' || hisInvocationLoading.value) return
  hisInvocationLoading.value = true
  try {
    hisInvocations.value = await getIcd10HisInvocations(batch.value.id, page)
  } catch (caught) {
    error.value = caught instanceof ApiClientError ? caught.message : '无法读取HIS调用记录'
  } finally {
    hisInvocationLoading.value = false
  }
}

/** 明确确认后结束当前版本的中断批次；请求结果未知时仅提示回读，不能自动重试。 */
async function recover() {
  const current = batch.value
  if (!current || !canRecover.value || !confirmedRecovery.value || loading.value || recovering.value) return
  recovering.value = true
  error.value = ''
  recoveryMessage.value = ''
  try {
    batch.value = await recoverMasterDataBatch(current.id, current.version)
    recoveryMessage.value = '批次已收尾，已提交数据保留。请刷新读取最新分项结果；未完成类型需要另建同步。'
  } catch (caught) {
    error.value = caught instanceof ApiClientError ? caught.message : '收尾结果无法确认，请刷新读取批次状态，不要直接重复提交。'
  } finally {
    confirmedRecovery.value = false
    recovering.value = false
  }
}

/** @param current 同步记录 @return 面向业务人员的状态名称 */
function statusLabel(current: MasterDataBatchSummary) {
  if (current.status === 'FAILED' && current.failureCode === 'HIS_BUSINESS_FAILURE') return 'HIS 查询失败'
  return ({ CREATED: '尚未开始', FETCHING: '正在从 HIS 取得', COMPLETED: '已完成对账',
    COMPLETED_WITH_ERRORS: '部分未完成', COMPLETED_WITH_UNKNOWN: '存在未知结果',
    FAILED: '同步失败', RESULT_UNKNOWN: '结果待确认' } satisfies Record<MasterDataBatchSummary['status'], string>)[current.status]
}

/** @param current 同步记录 @return 页面可展示的失败归类 */
function failureTitle(current: MasterDataBatchSummary) {
  if (current.failureCode === 'HIS_BUSINESS_FAILURE') {
    return 'HIS 拒绝目录查询'
  }
  if (current.failureCode === 'HIS_DATA_INVALID') return 'HIS 返回的数据未通过校验'
  if (current.failureCode === 'HIS_CONFIGURATION_ERROR') return 'HIS 接口配置不可用'
  return '本次同步未完成'
}

/** @param current 同步记录 @return 不暴露HIS原始响应的结果说明 */
function outcomeSummary(current: MasterDataBatchSummary) {
  if (current.status === 'COMPLETED_WITH_ERRORS') return '部分目录未通过自动校验或被 HIS 拒绝；已完成目录类型已更新，失败类型没有改动。'
  if (current.status === 'COMPLETED_WITH_UNKNOWN') return '存在未确认的处理结果；以已落库分项为准。历史批次缺少分项时，不能据此判断该类型是否曾更新，请先核查再新建同步。'
  if (current.status === 'FAILED' && current.failureCode === 'HIS_BUSINESS_FAILURE') return `读取 ${current.dataTradeCode} 时被 HIS 拒绝；平台没有取得可确认的数据。`
  if (current.status === 'FAILED' && current.failureCode === 'HIS_DATA_INVALID') return 'HIS 已返回目录数据，但自动校验未通过；平台没有更新该目录类型。'
  if (current.status === 'RESULT_UNKNOWN') return '无法确认 HIS 是否处理完成。请先查询交易记录，不要直接再次提交。'
  return '本次同步未完成，平台没有更新当前有效数据。'
}

/** @param current 同步记录 @return 不引导重复提交的下一步 */
function nextStep(current: MasterDataBatchSummary) {
  if (current.failureCode === 'HIS_BUSINESS_FAILURE') return '请根据分项失败原因和交易记录核对接口参数与授权；确认后再新建同步。'
  if (current.failureCode === 'HIS_CONFIGURATION_ERROR') return '请检查该机构的 HIS 接口配置，处理后从同步列表新建一次同步。'
  return '请先处理失败原因；不要重跑当前记录，处理完成后再新建同步。'
}

/** @param current 同步记录 @return 与目录范围相符的页面标题 */
function categoryLabel(current: MasterDataBatchSummary) {
  return current.category === 'ICD10_DIAGNOSIS'
    ? 'ICD-10诊断目录'
    : current.category === 'MEDICAL_DIRECTORY'
      ? '医疗目录'
      : '医院综合目录'
}

/** @param current 同步记录 @return 不将端点机构误表达为公共目录归属的范围名称 */
function scopeLabel(current: MasterDataBatchSummary) {
  return current.scopeType === 'PLATFORM'
    ? '平台公共目录'
    : (current.organizationName ?? current.organizationCode ?? '机构未识别')
}

onMounted(() => {
  durationTimer = setInterval(() => { currentTime.value = Date.now() }, 1000)
  void load()
})
onBeforeUnmount(() => {
  if (durationTimer) clearInterval(durationTimer)
})
</script>

<template>
  <section class="sync-record-page">
    <header class="record-heading">
      <div class="record-identity">
        <small>{{ batch ? categoryLabel(batch) : '同步记录' }}</small>
        <p v-if="batch">{{ scopeLabel(batch) }} · {{ batch.environment === 'PRODUCTION' ? '生产环境' : batch.environment === 'TEST' ? '测试环境' : '开发环境' }} · 批次号 {{ batch.batchNo }}</p>
        <p v-else>读取已落库的同步运行事实；查看不会再次调用 HIS。</p>
      </div>
      <div class="record-heading-actions">
        <div v-if="batch?.status === 'COMPLETED'" class="record-complete" role="status">
          <CheckCircle2 :size="18" />
          <div><strong>当前目录已完成对账</strong><span>本次同步已在 {{ formatBatchTime(batch.completedAt) }} 完成自动处理；可从数据目录查看当前数据。</span></div>
        </div>
        <button class="work-quiet-button" type="button" :disabled="loading || recovering" @click="load"><RefreshCw :size="16" />刷新</button>
      </div>
    </header>
    <p v-if="error" class="record-alert danger" role="alert"><AlertCircle :size="18" />{{ error }}</p>
    <p v-if="recoveryMessage" class="record-alert success" role="status">{{ recoveryMessage }}</p>
    <template v-if="batch && !error">
      <section v-if="canRecover" class="record-alert warning">
        <div>
          <strong>批次中断时可结束本次执行</strong>
          <span>只汇总已提交事实，不再调用 HIS；尚未完成的类型标记为结果未知。收尾会阻止原执行者继续写入，不能用于重跑当前批次。</span>
          <label><input v-model="confirmedRecovery" type="checkbox" :disabled="loading || recovering" />我已确认需要结束本次执行并保留已提交数据</label>
          <button class="work-quiet-button" type="button" :disabled="!confirmedRecovery || loading || recovering" @click="recover">{{ recovering ? '正在收尾…' : '结束中断批次' }}</button>
        </div>
      </section>
      <section v-if="batch.status === 'FAILED'" class="failure-card"><div class="failure-title"><AlertCircle :size="20" /><div><small>本次没有取得可用的数据</small><h3>{{ failureTitle(batch) }}</h3></div></div><p>{{ outcomeSummary(batch) }}</p><dl><div><dt>平台数据</dt><dd>未写入，当前有效数据未改变</dd></div><div><dt>下一步</dt><dd>{{ nextStep(batch) }}</dd></div></dl></section>
      <section v-else-if="batch.status === 'RESULT_UNKNOWN'" class="record-alert warning"><AlertCircle :size="20" /><div><strong>结果待确认</strong><span>{{ outcomeSummary(batch) }}</span></div></section>
      <section class="record-card">
        <h3>本次调用</h3>
        <div class="record-overview" :class="{ 'record-overview--single': batch.category !== 'MEDICAL_DIRECTORY' && batch.category !== 'ICD10_DIAGNOSIS' }">
          <div class="batch-detail-grid-scroll" role="region" aria-label="本次同步信息" tabindex="0">
            <dl class="batch-detail-grid record-flow"><div><dt>{{ batch.scopeType === 'PLATFORM' ? '目录范围' : '同步机构' }}</dt><dd>{{ scopeLabel(batch) }}</dd></div><div><dt>数据范围</dt><dd>{{ dataScope }}</dd></div><div><dt>HIS 交易</dt><dd>{{ batch.dataTradeCode }}{{ batch.countTradeCode ? `、${batch.countTradeCode}` : '' }}</dd></div><div><dt>结果</dt><dd>{{ statusLabel(batch) }}</dd></div><div><dt>开始时间</dt><dd>{{ batch.startedAt ? formatBatchTime(batch.startedAt) : '尚未开始' }}</dd></div><div><dt>结束时间</dt><dd>{{ batch.finishedAt ? formatBatchTime(batch.finishedAt) : batch.startedAt ? '进行中' : '—' }}</dd></div><div><dt>{{ batch.finishedAt ? '总耗时' : '已运行' }}</dt><dd>{{ formatBatchDuration(batch, currentTime) }}</dd></div></dl>
          </div>
          <div v-if="batch.category === 'MEDICAL_DIRECTORY' || batch.category === 'ICD10_DIAGNOSIS'" class="batch-detail-grid-scroll" role="region" aria-label="查询范围" tabindex="0">
            <dl class="batch-detail-grid record-window">
              <div><dt>同步方式</dt><dd>{{ batch.category === 'ICD10_DIAGNOSIS' ? '指定时间范围' : batch.mode === 'FULL' ? '全量同步' : '指定时间范围' }}</dd></div>
              <div><dt>实际查询</dt><dd>{{ batch.rangeStart ?? '—' }} 至 {{ batch.rangeEnd ?? '—' }}</dd></div>
              <div v-if="batch.mode === 'FULL' && batch.fullRuleEvidence"><dt>来源规则依据</dt><dd>{{ batch.fullRuleEvidence }}</dd></div>
            </dl>
          </div>
        </div>
      </section>
      <section v-if="batch.counts.returned > 0 || batch.status === 'COMPLETED'" class="record-card">
        <h3>取得与对账结果</h3>
        <div class="batch-detail-grid-scroll record-counts-wrap" role="region" aria-label="取得与对账统计" tabindex="0">
          <dl v-if="batch.category === 'MEDICAL_DIRECTORY' || batch.category === 'ICD10_DIAGNOSIS'" class="batch-detail-grid record-counts"><div><dt>来源声明行数</dt><dd>{{ batch.counts.declared ?? '未确认' }}</dd></div><div><dt>HIS 返回行</dt><dd>{{ batch.counts.returned }} 条</dd></div><div><dt>自动拦截数</dt><dd>{{ batch.counts.invalid + batch.counts.conflict }} 条</dd></div><div><dt>新增 / 更新</dt><dd>{{ batch.counts.created }} / {{ batch.counts.updated }} 条</dd></div><div><dt>当前有效目录</dt><dd>{{ batch.counts.active === null ? '未完成' : `${batch.counts.active} 条` }}</dd></div></dl>
          <dl v-else class="batch-detail-grid record-counts"><div><dt>HIS 返回行</dt><dd>{{ batch.counts.returned }} 条</dd></div><div><dt>展开重复</dt><dd>{{ batch.counts.duplicate }} 行</dd></div><div><dt>丢弃的无效关系</dt><dd>{{ batch.counts.invalid }} 条</dd></div><div><dt>主数据冲突</dt><dd>{{ batch.counts.conflict }} 条</dd></div><div><dt>新增 / 更新</dt><dd>{{ batch.counts.created }} / {{ batch.counts.updated }} 条</dd></div><div><dt>当前有效目录</dt><dd>{{ batch.counts.active === null ? '未完成' : `${batch.counts.active} 条` }}</dd></div></dl>
        </div>
        <p v-if="batch.status === 'COMPLETED'" class="record-directory-link"><RouterLink :to="directoryPath">查看当前数据目录</RouterLink></p>
      </section>
      <MedicalDirectorySyncResultPanel v-if="batch.category === 'MEDICAL_DIRECTORY'" :results="medicalDirectoryResults" :loading="loading" />
      <BatchExchangeRecordPanel v-if="batch.category === 'MEDICAL_DIRECTORY'" :records="exchangeRecords" :loading="exchangeRecordLoading" :error="exchangeRecordError" :organization-code="batch.organizationCode ?? ''" :source-record-id="batch.batchNo" />
      <template v-else-if="batch.category === 'ICD10_DIAGNOSIS'">
        <Icd10SyncResultPanel :results="icd10Results" :loading="loading" />
        <Icd10HisInvocationPanel :page="hisInvocations" :loading="loading || hisInvocationLoading" @change-page="changeHisInvocationPage" />
      </template>
      <template v-else>
        <DirectorySyncResultPanel :results="directoryResults" :loading="loading" />
        <BatchExchangeRecordPanel :records="exchangeRecords" :loading="exchangeRecordLoading" :error="exchangeRecordError" :organization-code="batch.organizationCode ?? ''" :source-record-id="batch.batchNo" />
      </template>
      <BatchAuditPanel :events="auditEvents" :loading="auditLoading" :error="auditError" :permitted="canReadAudit" />
    </template>
  </section>
</template>

<style scoped src="./batch-detail-grid.css"></style>
<style scoped>
.sync-record-page { display: grid; gap: 14px; }
.record-heading { min-width: 0; padding: 10px 14px; border: 1px solid #dce5e7; border-radius: 6px; background: #fff; display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.record-identity { min-width: 0; display: flex; align-items: center; flex-wrap: wrap; gap: 4px 12px; }
.record-identity small { flex: none; padding: 3px 7px; border-radius: 4px; background: #f0f5f5; color: #49636a; font-size: 11px; }
.record-identity p { min-width: 0; margin: 0; color: #526770; font-size: 13px; line-height: 1.5; overflow-wrap: anywhere; }
.record-heading-actions { display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
.record-heading-actions > button { flex: none; }
.record-complete { max-width: 520px; padding: 6px 9px; border: 1px solid #b9dfd2; border-radius: 5px; background: #f0f9f6; display: flex; align-items: flex-start; gap: 7px; color: #246d5f; font-size: 12px; }
.record-complete > svg { flex: none; margin-top: 1px; }
.record-complete > div { display: grid; gap: 2px; }
.record-complete span { line-height: 1.45; }
.record-alert { min-height: 56px; margin: 0; padding: 13px 15px; border: 1px solid; border-radius: 6px; display: flex; align-items: flex-start; gap: 10px; font-size: 13px; }
.record-alert div { display: grid; gap: 3px; }
.record-alert span { line-height: 1.6; }
.record-alert.success { border-color: #b9dfd2; background: #eff8f5; color: #246d5f; }
.record-alert.warning { border-color: #ead6a8; background: #fffbef; color: #876320; }
.failure-card { padding: 17px; border: 1px solid #efc9c1; border-radius: 6px; background: #fff8f6; }
.failure-title { display: flex; align-items: flex-start; gap: 10px; color: #ae5039; }
.failure-title small { display: block; color: #8d6b63; font-size: 11px; }
.failure-title h3 { margin: 3px 0 0; color: #813d2c; font-size: 17px; }
.failure-card > p { margin: 13px 0; color: #553f39; line-height: 1.7; }
.failure-card dl { margin: 0; padding-top: 12px; border-top: 1px solid #f0d7d1; display: grid; gap: 8px; }
.failure-card dl div { display: grid; grid-template-columns: 74px minmax(0, 1fr); gap: 10px; }
.failure-card dt { color: #8d6b63; font-size: 12px; }
.failure-card dd { margin: 0; color: #3e3532; font-size: 13px; line-height: 1.6; }
.record-card { min-width: 0; border: 1px solid #dce5e7; border-radius: 6px; background: #fff; }
.record-card h3 { margin: 0; padding: 10px 16px; border-bottom: 1px solid #e9eef0; color: #2d424b; font-size: 14px; }
.record-overview { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 7px 9px; }
.record-overview--single { grid-template-columns: minmax(0, 1fr); }
.record-overview > .batch-detail-grid-scroll { min-width: 0; padding: 0 4px; }
.record-overview > .batch-detail-grid-scroll + .batch-detail-grid-scroll { border-left: 1px dashed #b9cdd0; }
.record-flow { width: 100%; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.record-counts { width: 100%; grid-template-columns: repeat(3, minmax(0, 1fr)); }
.record-flow > div { grid-template-columns: 100px minmax(max-content, 1fr); }
.record-window { grid-template-columns: max-content; }
.record-window > div { grid-template-columns: 125px minmax(max-content, 1fr); }
.record-counts-wrap { margin: 8px 16px; }
.record-counts > div { grid-template-columns: 110px minmax(max-content, 1fr); }
.record-directory-link { margin: 0; padding: 0 16px 9px; text-align: right; font-size: 12px; }
.record-directory-link a { color: #087767; text-decoration: none; }
.record-directory-link a:hover { text-decoration: underline; }
@media (max-width: 900px) { .record-overview { grid-template-columns: minmax(0, 1fr); } .record-overview > .batch-detail-grid-scroll + .batch-detail-grid-scroll { padding-top: 8px; border-left: 0; } }
@media (max-width: 760px) { .record-heading { align-items: stretch; flex-direction: column; } .record-heading-actions { justify-content: space-between; } }
</style>
