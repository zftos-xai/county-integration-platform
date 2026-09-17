<!-- 原型基础数据面板：演示机构、人员和目录同步状态。 -->
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { AlertTriangle, CheckCircle2, RotateCcw, Search, ShieldCheck, X } from 'lucide-vue-next'
import AdminPagination from '@/components/AdminPagination.vue'
import { foundationStatusLabel } from '../model/statusVocabulary'

type PanelTab = 'datasets' | 'history'
type DetailTab = 'overview' | 'source' | 'access' | 'runs'
type DataStatus = '正常' | '接入条件待确认' | '同步失败'
type Dataset = {
  id: string; name: string; source: string; method: string; updateRule: string; count: string
  sourceUpdatedAt: string; syncedAt: string; view: string; status: DataStatus; stableKey: string
  versionField: string; stopRule: string; retainedFields: string; historyVisibility: string
  lastResult: string; failureReason: string
}
type DataView = {
  id: string; name: string; dataset: string; consumer: string; account: string
  scope: string; version: string; verifiedAt: string; status: '已发布（演示）' | '待发布' | '接入条件待确认'
}
type SyncEvent = {
  id: string; dataset: string; startedAt: string; endedAt: string; mode: string
  received: string; changed: string; failed: string; result: string; evidence: string
  sourceVersion: string; batchRange: string; nextRun: string
}
type FailureEvidence = { batch: string; field: string; rule: string; sample: string; owner: string; retryScope: string; attempts: string; errorCode: string }

const props = defineProps<{ role: string }>()
const emit = defineEmits<{ (event: 'switch-role'): void }>()
const tab = ref<PanelTab>('datasets')
const detailTab = ref<DetailTab>('overview')
const query = ref('')
const status = ref('全部状态')
const page = ref(1)
const pageSize = ref(10)
const selectedDataset = ref<Dataset | null>(null)
const retryEvidence = ref('')
const retryNote = ref('')
const isRetryScopeConfirmed = ref(false)
const operationMessage = ref('')
const foundationStorageKey = 'county-integration-foundation-v3'
const failureEvidence: Record<string, FailureEvidence> = {
  'SIM-MD-DRUG': { batch: 'SIM-BATCH-20260915-1030', field: 'drug_name', rule: '最大 120 字符（演示规则）', sample: '合成样例：药品名称长度 146', owner: '基层系统目录维护人员', retryScope: '本批次 200 条失败记录', attempts: '自动重试 0 次；计划同步已暂停', errorCode: 'FIELD_LENGTH_EXCEEDED' },
}

const datasets = ref<Dataset[]>([
  { id: 'SIM-MD-ORG', name: '基层机构', source: '基层系统', method: '接口', updateRule: '初始全量 + 每日增量', count: '24', sourceUpdatedAt: '2026-09-15 08:00', syncedAt: '2026-09-15 08:05', view: 'SIM_VIEW_ORG', status: '正常', stableKey: '机构唯一标识（正式字段待确认）', versionField: '源更新时间（正式语义待确认）', stopRule: '停用后保留标识并标记无效（待确认）', retainedFields: '机构标识、名称、行政区划、有效状态（演示）', historyVisibility: 'HIS默认仅查询有效数据（待确认）', lastResult: '收到4条，变更1条，失败0条', failureReason: '—' },
  { id: 'SIM-MD-DEPT', name: '基层科室', source: '基层系统', method: '接口', updateRule: '初始全量 + 每日增量', count: '86', sourceUpdatedAt: '2026-09-15 08:00', syncedAt: '2026-09-15 08:06', view: 'SIM_VIEW_DEPT', status: '正常', stableKey: '机构标识 + 科室标识（待确认）', versionField: '源更新时间（待确认）', stopRule: '来源停用后视图不可见（待确认）', retainedFields: '科室标识、机构标识、名称、有效状态（演示）', historyVisibility: '历史范围待正式视图配置确认', lastResult: '收到12条，变更3条，失败0条', failureReason: '—' },
  { id: 'SIM-MD-STAFF', name: '基层人员', source: '基层系统', method: '未确认', updateRule: '主键和停用语义待确认', count: '—', sourceUpdatedAt: '—', syncedAt: '—', view: '未发布', status: '接入条件待确认', stableKey: '待确认', versionField: '待确认', stopRule: '离岗、停用和跨机构变更语义待确认', retainedFields: '不得根据HIS需求推测字段', historyVisibility: '待确认', lastResult: '未执行', failureReason: '缺少正式来源字段、主键和停用规则' },
  { id: 'SIM-MD-ITEM', name: '诊疗项目目录', source: '基层系统', method: '未确认', updateRule: '编码版本待确认', count: '—', sourceUpdatedAt: '—', syncedAt: '—', view: '未发布', status: '接入条件待确认', stableKey: '项目编码及机构范围待确认', versionField: '目录版本待确认', stopRule: '项目停用与替代关系待确认', retainedFields: '不得根据HIS需求推测字段', historyVisibility: '待确认', lastResult: '未执行', failureReason: '缺少正式来源规则和目录版本语义' },
  { id: 'SIM-MD-DRUG', name: '药品目录', source: '基层系统', method: '增量接口', updateRule: '每30分钟', count: '1,286', sourceUpdatedAt: '2026-09-15 10:30', syncedAt: '2026-09-15 10:31', view: 'SIM_VIEW_DRUG', status: '同步失败', stableKey: '机构标识 + 药品编码（演示）', versionField: '更新时间（演示）', stopRule: '来源停用后保留标识并从有效视图移除（演示）', retainedFields: '药品编码、名称、规格、单位和有效状态（演示）', historyVisibility: 'HIS仅查询有效数据（演示）', lastResult: '收到200条，变更0条，失败200条', failureReason: '字段长度校验未通过；自动增量已暂停（演示）' },
  { id: 'SIM-MD-DICT', name: '公共字典', source: '基层系统', method: '全量接口', updateRule: '每日', count: '48', sourceUpdatedAt: '2026-09-15 02:00', syncedAt: '2026-09-15 02:03', view: 'SIM_VIEW_DICT', status: '正常', stableKey: '字典类型 + 字典编码（演示）', versionField: '全量批次号（演示）', stopRule: '来源停用后标记无效（演示）', retainedFields: '类型、编码、名称、排序和有效状态（演示）', historyVisibility: 'HIS仅查询有效字典（演示）', lastResult: '收到48条，变更0条，失败0条', failureReason: '—' },
])

const views: DataView[] = [
  { id: 'SIM-VIEW-ORG', name: 'SIM_VIEW_ORG', dataset: '基层机构', consumer: '县医院HIS', account: 'HIS只读账号（演示）', scope: '启用机构最小字段（演示）', version: 'V1.0（演示）', verifiedAt: '2026-09-15 09:10', status: '已发布（演示）' },
  { id: 'SIM-VIEW-DEPT', name: 'SIM_VIEW_DEPT', dataset: '基层科室', consumer: '县医院HIS', account: 'HIS只读账号（演示）', scope: '启用科室最小字段（演示）', version: 'V1.0（演示）', verifiedAt: '2026-09-15 09:12', status: '已发布（演示）' },
  { id: 'SIM-VIEW-DRUG', name: 'SIM_VIEW_DRUG', dataset: '药品目录', consumer: '县医院HIS', account: '待授权', scope: '字段和过滤范围待确认', version: '草案', verifiedAt: '—', status: '待发布' },
  { id: 'SIM-VIEW-STAFF', name: '视图名称待确认', dataset: '基层人员', consumer: '县医院HIS', account: '待授权', scope: '用途、字段和历史范围待确认', version: '—', verifiedAt: '—', status: '接入条件待确认' },
]

const history = ref<SyncEvent[]>([
  { id: 'SIM-SYNC-0915-006', dataset: '基层科室', startedAt: '2026-09-15 08:06', endedAt: '2026-09-15 08:06', mode: '增量（演示）', received: '12', changed: '3', failed: '0', result: '成功', evidence: 'SIM-RUN-006', sourceVersion: '源更新时间 2026-09-15 08:00', batchRange: '08:00 后变更', nextRun: '2026-09-16 08:00' },
  { id: 'SIM-SYNC-0915-005', dataset: '基层机构', startedAt: '2026-09-15 08:05', endedAt: '2026-09-15 08:05', mode: '增量（演示）', received: '4', changed: '1', failed: '0', result: '成功', evidence: 'SIM-RUN-005', sourceVersion: '源更新时间 2026-09-15 08:00', batchRange: '08:00 后变更', nextRun: '2026-09-16 08:00' },
  { id: 'SIM-SYNC-0915-004', dataset: '药品目录', startedAt: '2026-09-15 10:31', endedAt: '2026-09-15 10:31', mode: '增量（演示）', received: '200', changed: '0', failed: '200', result: '失败', evidence: 'SIM-VALIDATION-004', sourceVersion: '源更新时间 2026-09-15 10:30', batchRange: '10:00—10:30', nextRun: '故障排除后恢复' },
  { id: 'SIM-SYNC-0915-003', dataset: '公共字典', startedAt: '2026-09-15 02:03', endedAt: '2026-09-15 02:03', mode: '全量（演示）', received: '48', changed: '0', failed: '0', result: '成功', evidence: 'SIM-RUN-003', sourceVersion: '全量批次 20260915', batchRange: '全部有效字典', nextRun: '2026-09-16 02:00' },
])

const datasetRows = computed(() => datasets.value.filter(item => status.value === '全部状态' || item.status === status.value).filter(item => `${item.name} ${item.source} ${item.id} ${item.view}`.toLowerCase().includes(query.value.toLowerCase())))
const historyRows = computed(() => history.value.filter(item => `${item.id} ${item.dataset} ${item.result} ${item.evidence}`.toLowerCase().includes(query.value.toLowerCase())))
const activeRows = computed(() => tab.value === 'datasets' ? datasetRows.value : historyRows.value)
const pageStart = computed(() => (page.value - 1) * pageSize.value)
const pagedDatasetRows = computed(() => datasetRows.value.slice(pageStart.value, pageStart.value + pageSize.value))
const pagedHistoryRows = computed(() => historyRows.value.slice(pageStart.value, pageStart.value + pageSize.value))
const selectedRuns = computed(() => selectedDataset.value ? history.value.filter(item => item.dataset === selectedDataset.value?.name) : [])
const selectedSuccessfulRun = computed(() => selectedRuns.value.find(item => item.result.includes('成功')))
const selectedDatasetView = computed(() => views.find(item => item.dataset === selectedDataset.value?.name))
const selectedFailureEvidence = computed(() => selectedDataset.value ? failureEvidence[selectedDataset.value.id] : undefined)
const canRetry = computed(() => props.role === 'operator' && selectedDataset.value?.status === '同步失败' && isRetryScopeConfirmed.value && retryEvidence.value.trim() && retryNote.value.trim())

onMounted(() => {
  const saved = localStorage.getItem(foundationStorageKey)
  if (!saved) return
  try {
    const state = JSON.parse(saved) as { datasets?: Dataset[]; history?: SyncEvent[] }
    if (state.datasets?.length) datasets.value = state.datasets
    if (state.history?.length) history.value = state.history
  } catch {
    localStorage.removeItem(foundationStorageKey)
  }
})
// 原型状态仅保存在独立键中，以便评审者刷新后继续演练且不污染正式页面。
watch([datasets, history], () => localStorage.setItem(foundationStorageKey, JSON.stringify({ datasets: datasets.value, history: history.value })), { deep: true })

// 切换数据域时清理上一数据域的筛选和抽屉状态。
watch(tab, () => { query.value = ''; status.value = '全部状态'; page.value = 1; closeDetail() })
// 筛选条件变化后回到第一页，避免保留已经越界的页码。
watch([query, status], () => { page.value = 1 })

function resetAction() { retryEvidence.value = ''; retryNote.value = ''; isRetryScopeConfirmed.value = false; operationMessage.value = '' }
function openDataset(item: Dataset, nextTab: DetailTab = 'overview') { selectedDataset.value = item; detailTab.value = nextTab; resetAction() }
async function openDatasetFromHistory(item: SyncEvent) { tab.value = 'datasets'; await nextTick(); query.value = item.dataset }
async function showFailedDatasets() { tab.value = 'datasets'; await nextTick(); status.value = '同步失败'; page.value = 1 }
function closeDetail() { selectedDataset.value = null; resetAction() }
function datasetAccessState(dataset: string) {
  const view = views.find(item => item.dataset === dataset)
  if (!view || view.status === '接入条件待确认') return '未开放'
  return view.status === '待发布' ? '待开放' : '可读取'
}
function datasetAccessDetail(dataset: string) {
  const view = views.find(item => item.dataset === dataset)
  if (!view || view.status === '接入条件待确认') return '尚未开放读取'
  return view.status === '待发布' ? '等待授权和范围确认' : '县医院HIS可按批准范围读取'
}
function stateClass(value: string) { return value.includes('正常') || value.includes('成功') || value.includes('已发布') ? 'success' : value.includes('失败') ? 'danger' : 'warning' }
function retrySync() {
  if (!canRetry.value || !selectedDataset.value) return
  selectedDataset.value.status = '正常'
  selectedDataset.value.syncedAt = '2026-09-15 12:08'
  selectedDataset.value.lastResult = '受控重试收到200条，变更18条，失败0条（演示）'
  selectedDataset.value.failureReason = '已依据演示核查记录完成受控重试'
  history.value.unshift({ id: 'SIM-SYNC-0915-007', dataset: selectedDataset.value.name, startedAt: '2026-09-15 12:08', endedAt: '2026-09-15 12:08', mode: '受控重试（演示）', received: '200', changed: '18', failed: '0', result: '重试成功', evidence: retryEvidence.value.trim(), sourceVersion: '源更新时间 2026-09-15 10:30', batchRange: '原失败批次 200 条', nextRun: '2026-09-15 12:30' })
  operationMessage.value = `重试成功，已记录依据 ${retryEvidence.value.trim()}；后续计划同步恢复（演示）`
  retryEvidence.value = ''
  retryNote.value = ''
  isRetryScopeConfirmed.value = false
}
</script>

<template>
  <section class="foundation-page">
    <div class="foundation-commandbar">
      <div><strong>基础数据运行状态</strong><small>查看哪些数据已进入平台，以及县医院HIS是否可以读取</small></div>
      <dl><div><dt>数据类别</dt><dd>{{ datasets.length }}</dd></div><div><dt>同步失败</dt><dd class="danger">{{ datasets.filter(item => item.status === '同步失败').length }}</dd></div><div><dt>条件待确认</dt><dd>{{ datasets.filter(item => item.status === '接入条件待确认').length }}</dd></div><div><dt>HIS可读取</dt><dd>{{ views.filter(item => item.status === '已发布（演示）').length }}</dd></div></dl>
      <button class="btn btn-primary btn-sm" @click="showFailedDatasets">处理同步失败</button>
    </div>

    <div class="foundation-tabs" role="tablist" aria-label="基础数据功能"><button :class="{ active: tab === 'datasets' }" role="tab" :aria-selected="tab === 'datasets'" @click="tab = 'datasets'">基础数据</button><button :class="{ active: tab === 'history' }" role="tab" :aria-selected="tab === 'history'" @click="tab = 'history'">同步记录</button></div>
    <div class="data-filterbar foundation-filter" role="search" aria-label="基础数据筛选"><label class="data-filter-search"><Search :size="18" /><input v-model="query" :aria-label="tab === 'history' ? '搜索同步记录' : '搜索基础数据'" :placeholder="tab === 'history' ? '搜索同步编号、数据类别或依据' : '搜索数据类别、来源或编号'" /></label><label v-if="tab !== 'history'"><span>同步状态</span><select v-model="status" aria-label="同步状态"><option>全部状态</option><option value="正常">同步正常</option><option>接入条件待确认</option><option>同步失败</option></select></label><span class="data-filter-count">共 {{ activeRows.length }} 条</span></div>

    <div class="card data-receipt-table foundation-table"><div class="table-responsive">
      <table v-if="tab === 'datasets'" class="table table-vcenter"><thead><tr><th>基础数据</th><th>来源与取得方式</th><th>同步计划</th><th>最近结果</th><th>平台保存</th><th>HIS读取</th><th>同步状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in pagedDatasetRows" :key="item.id"><td><button class="data-record-link" @click="openDataset(item)">{{ item.name }}</button><small>{{ item.id }}</small></td><td><strong>{{ item.source }}</strong><small>{{ item.method }}</small></td><td>{{ item.updateRule }}</td><td><strong>{{ item.syncedAt }}</strong><small>{{ item.lastResult }}</small></td><td><strong>{{ item.count === '—' ? '未同步' : `${item.count} 条` }}</strong><small>{{ item.count === '—' ? '尚未形成平台数据' : '保存批准字段' }}</small></td><td><button class="foundation-access-link" @click="openDataset(item, 'access')"><strong>{{ datasetAccessState(item.name) }}</strong><small>{{ datasetAccessDetail(item.name) }}</small></button></td><td><span class="prototype-tag" :class="stateClass(item.status)">{{ foundationStatusLabel(item.status) }}</span></td><td><button class="btn btn-outline-primary btn-sm" @click="openDataset(item)">{{ item.status === '同步失败' ? '处理' : '详情' }}</button></td></tr><tr v-if="!pagedDatasetRows.length"><td colspan="8" class="text-center text-secondary py-5">没有符合条件的基础数据</td></tr></tbody></table>
      <table v-else class="table table-vcenter"><thead><tr><th>同步编号</th><th>基础数据</th><th>批次与来源版本</th><th>开始 / 结束时间</th><th>同步方式</th><th>收到 / 变更 / 失败</th><th>结果 / 依据</th><th>下次执行</th><th>操作</th></tr></thead><tbody><tr v-for="item in pagedHistoryRows" :key="item.id"><td>{{ item.id }}</td><td>{{ item.dataset }}</td><td><strong>{{ item.batchRange || '历史批次未登记' }}</strong><small>{{ item.sourceVersion || '来源版本未登记' }}</small></td><td>{{ item.startedAt }}<small>{{ item.endedAt }}</small></td><td>{{ item.mode }}</td><td>{{ item.received }} / {{ item.changed }} / {{ item.failed }}</td><td><span class="prototype-tag" :class="stateClass(item.result)">{{ item.result }}</span><small>{{ item.evidence }}</small></td><td>{{ item.nextRun || '待安排' }}</td><td><button class="btn btn-link btn-sm" @click="openDatasetFromHistory(item)">查看对应数据</button></td></tr><tr v-if="!pagedHistoryRows.length"><td colspan="9" class="text-center text-secondary py-5">没有符合条件的同步记录</td></tr></tbody></table>
    </div><AdminPagination :total="activeRows.length" :page="page" :page-size="pageSize" @update:page="page = $event" @update:page-size="pageSize = $event" /></div>

    <div v-if="selectedDataset" class="data-record-overlay" @click.self="closeDetail">
      <aside class="data-record-drawer foundation-drawer" role="dialog" aria-modal="true" aria-label="基础数据详情">
        <header class="drawer-titlebar"><div><h2>{{ selectedDataset.name }}</h2><p>{{ selectedDataset.id }}</p></div><button class="btn btn-icon btn-ghost-secondary" aria-label="关闭详情" @click="closeDetail"><X :size="22" /></button></header>
        <div class="drawer-summary foundation-detail-summary"><div><span>同步状态</span><strong><em class="status-pill" :class="stateClass(selectedDataset.status)">{{ foundationStatusLabel(selectedDataset.status) }}</em></strong></div><div><span>最近同步</span><strong>{{ selectedDataset.syncedAt }}</strong></div><div><span>平台数据量</span><strong>{{ selectedDataset.count }}</strong></div><div><span>HIS读取状态</span><strong>{{ datasetAccessState(selectedDataset.name) }}</strong></div></div>
        <nav class="nav nav-tabs drawer-tabs four-tabs" role="tablist" aria-label="基础数据详情页签"><button class="nav-link" :class="{ active: detailTab === 'overview' }" role="tab" :aria-selected="detailTab === 'overview'" @click="detailTab = 'overview'">同步概况</button><button class="nav-link" :class="{ active: detailTab === 'source' }" role="tab" :aria-selected="detailTab === 'source'" @click="detailTab = 'source'">来源与字段</button><button class="nav-link" :class="{ active: detailTab === 'access' }" role="tab" :aria-selected="detailTab === 'access'" @click="detailTab = 'access'">HIS读取</button><button class="nav-link" :class="{ active: detailTab === 'runs' }" role="tab" :aria-selected="detailTab === 'runs'" @click="detailTab = 'runs'">同步执行</button></nav>
        <div class="drawer-body">
          <template v-if="selectedDataset && detailTab === 'overview'">
            <section class="detail-section"><h3>最近同步结果</h3><dl class="reference-facts"><div><dt>取得方式</dt><dd>{{ selectedDataset.method }}</dd></div><div><dt>同步规则</dt><dd>{{ selectedDataset.updateRule }}</dd></div><div><dt>源更新时间</dt><dd>{{ selectedDataset.sourceUpdatedAt }}</dd></div><div><dt>执行结果</dt><dd>{{ selectedDataset.lastResult }}</dd></div><div><dt>失败原因</dt><dd>{{ selectedDataset.failureReason }}</dd></div></dl></section>
            <section v-if="selectedDataset.status === '同步失败' && selectedFailureEvidence" class="detail-section"><h3>失败证据</h3><dl class="reference-facts"><div><dt>失败批次</dt><dd>{{ selectedFailureEvidence.batch }}</dd></div><div><dt>失败字段</dt><dd>{{ selectedFailureEvidence.field }}</dd></div><div><dt>校验规则</dt><dd>{{ selectedFailureEvidence.rule }}</dd></div><div><dt>脱敏样例</dt><dd>{{ selectedFailureEvidence.sample }}</dd></div><div><dt>来源责任方</dt><dd>{{ selectedFailureEvidence.owner }}</dd></div><div><dt>重试范围</dt><dd>{{ selectedFailureEvidence.retryScope }}</dd></div><div><dt>执行次数</dt><dd>{{ selectedFailureEvidence.attempts }}</dd></div><div><dt>错误代码</dt><dd>{{ selectedFailureEvidence.errorCode }}</dd></div></dl></section>
            <section v-if="selectedDataset.status === '同步失败'" class="detail-section"><h3>受控重试</h3><div class="alert alert-warning">系统已暂停该数据的后续计划同步。先排除失败原因，再仅重试失败批次；重试不会修改来源系统数据。</div><template v-if="role === 'manager'"><div class="work-inline-note">管理视角只读。同步故障由运维依据技术核查结果处理。</div><button class="btn btn-primary" @click="emit('switch-role')">切换运维处理</button></template><div v-else class="reference-form foundation-retry-form"><label>核查依据<input v-model="retryEvidence" class="form-control" placeholder="例如变更单或日志编号" /></label><label>处理说明<input v-model="retryNote" class="form-control" placeholder="说明已排除的失败原因" /></label><label class="retry-scope-confirm"><input v-model="isRetryScopeConfirmed" type="checkbox" /> 已确认仅重试：{{ selectedFailureEvidence?.retryScope || '本次失败记录' }}</label><div class="reference-actions"><button class="btn btn-primary" :disabled="!canRetry" @click="retrySync"><RotateCcw :size="15" />执行受控重试</button></div></div></section>
            <div v-if="operationMessage" class="alert alert-success">{{ operationMessage }}</div>
            <section v-if="selectedDataset.status === '接入条件待确认'" class="detail-section"><h3>阻塞原因</h3><div class="boundary-list"><p><AlertTriangle :size="16" />{{ selectedDataset.failureReason }}</p><p><ShieldCheck :size="16" />正式接入条件完成前不执行同步，也不向HIS开放读取。</p></div></section>
          </template>
          <template v-else-if="selectedDataset && detailTab === 'source'"><section class="detail-section"><h3>来源规则</h3><dl class="reference-facts"><div><dt>权威来源</dt><dd>{{ selectedDataset.source }}</dd></div><div><dt>稳定主键</dt><dd>{{ selectedDataset.stableKey }}</dd></div><div><dt>版本或更新时间</dt><dd>{{ selectedDataset.versionField }}</dd></div><div><dt>停用或删除</dt><dd>{{ selectedDataset.stopRule }}</dd></div><div><dt>历史可见性</dt><dd>{{ selectedDataset.historyVisibility }}</dd></div></dl></section><section class="detail-section"><h3>平台保存字段</h3><div class="boundary-list"><p><CheckCircle2 :size="16" />{{ selectedDataset.retainedFields }}</p><p><ShieldCheck :size="16" />只保存来源规则批准的基础字段，不从HIS消费需求反推来源字段。</p></div></section></template>
          <template v-else-if="selectedDataset && detailTab === 'access'">
            <section class="detail-section"><h3>HIS读取状态</h3><dl class="reference-facts"><div><dt>当前状态</dt><dd>{{ datasetAccessState(selectedDataset.name) }}</dd></div><div><dt>使用系统</dt><dd>{{ selectedDatasetView?.consumer || '县医院HIS' }}</dd></div><div><dt>读取账号</dt><dd>{{ selectedDatasetView?.account || '尚未授权' }}</dd></div><div><dt>可读取范围</dt><dd>{{ selectedDatasetView?.scope || '尚未确认' }}</dd></div><div><dt>当前成功批次</dt><dd>{{ selectedSuccessfulRun?.id || '尚无成功批次' }}</dd></div><div><dt>来源版本</dt><dd>{{ selectedSuccessfulRun?.sourceVersion || '尚未登记' }}</dd></div><div><dt>最近验证</dt><dd>{{ selectedDatasetView?.verifiedAt || '—' }}</dd></div></dl></section>
            <section class="detail-section"><h3>技术配置</h3><div class="work-inline-note">该区域供实施和管理人员核对读取配置。日常使用只需关注上方的“可读取、待开放、未开放”状态。</div><dl class="reference-facts"><div><dt>数据库视图</dt><dd>{{ selectedDatasetView?.name || '尚未配置' }}</dd></div><div><dt>配置版本</dt><dd>{{ selectedDatasetView?.version || '—' }}</dd></div></dl></section>
            <section class="detail-section"><h3>访问边界</h3><div class="boundary-list"><p><ShieldCheck :size="16" />HIS只读账号仅查询批准的数据范围。</p><p><CheckCircle2 :size="16" />只开放获批基础数据及最小字段。</p><p><AlertTriangle :size="16" />不得开放交换正文、操作明细、平台凭证或其他业务表。</p></div></section>
          </template>
          <template v-else-if="selectedDataset && detailTab === 'runs'"><section class="detail-section"><h3>最近执行记录</h3><div v-if="!selectedRuns.length" class="prototype-empty">尚未执行同步</div><ol v-else class="work-timeline"><li v-for="run in selectedRuns" :key="run.id"><span><CheckCircle2 :size="13" /></span><p><strong>{{ run.result }} · {{ run.mode }}</strong>{{ run.batchRange || '历史批次未登记' }}；收到 {{ run.received }}，变更 {{ run.changed }}，失败 {{ run.failed }}<small>{{ run.sourceVersion || '来源版本未登记' }} · {{ run.startedAt }} · {{ run.evidence }} · 下次 {{ run.nextRun || '待安排' }}</small></p></li></ol></section></template>
        </div>
        <footer class="drawer-footer"><span>所有名称、字段、数量和运行结果均为合成演示内容</span><button class="btn btn-outline-secondary btn-sm" @click="closeDetail">关闭</button></footer>
      </aside>
    </div>
  </section>
</template>
