<!-- HIS调用记录管理页：按获授权机构检索脱敏运行事实，不执行重发或修改任何HIS数据。 -->
<script setup lang="ts">
import { AlertCircle, FileWarning, LoaderCircle, Search, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listExchangeRecords } from '@/api/exchange/exchangeRecord'
import type { ExchangeRecord } from '@/api/exchange/exchangeRecord'
import { listPhisTrades } from '@/api/exchange/phisTradeCatalog'
import type { PhisTrade } from '@/api/exchange/phisTradeCatalog'
import AdminPagination from '@/components/AdminPagination.vue'
import ListQueryToolbar from '@/components/ListQueryToolbar.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { useModalDialog } from '@/composables/useModalDialog'
import { authState } from '@/store/modules/auth'
import { formatLocalDateTime } from '@/utils/managementDisplay'
import { ApiClientError } from '@/utils/request'

type ExchangeResultFilter = 'SUCCESS' | 'FAILURE' | 'NO_RESPONSE' | 'INVALID_RESPONSE' | ''

const route = useRoute()
const router = useRouter()
const visibleOrganizationCodes = computed(() => authState.user?.organizationCodes ?? [])
const initialOrganizationCode = readVisibleOrganizationCode(route.query.organizationCode)
const organizationCode = ref(initialOrganizationCode)
const interfaceCode = ref(readQueryText(route.query.interfaceCode, 64))
const sourceRecordId = ref(readQueryText(route.query.sourceRecordId, 128))
const result = ref<ExchangeResultFilter>(readResult(route.query.result))
const receivedFrom = ref(readDateTime(route.query.receivedFrom))
const receivedTo = ref(readDateTime(route.query.receivedTo))
const records = ref<ExchangeRecord[]>([])
const trades = ref<PhisTrade[]>([])
const isTradeMenuOpen = ref(false)
const isTradeCatalogUnavailable = ref(false)
const selectedRecord = ref<ExchangeRecord | null>(null)
const isLoading = ref(false)
const error = ref<ApiClientError | null>(null)
const filterError = ref('')
const isDetailOpen = ref(false)
const { dialogRef, handleDialogKeydown } = useModalDialog(isDetailOpen, closeDetail)
const { page, pageSize, pagedRows } = useClientPagination(computed(() => records.value))
let controller: AbortController | null = null
let tradeController: AbortController | null = null
let mounted = true

/** 从路由读取受限长度的文本查询条件，不接受数组或超长值。 */
function readQueryText(value: unknown, maximumLength: number) {
  return typeof value === 'string' && value.length <= maximumLength ? value : ''
}

/** 只接受当前会话可访问机构作为初始查询范围；服务端仍会再次验证。 */
function readVisibleOrganizationCode(value: unknown) {
  const candidate = readQueryText(value, 64)
  if (candidate && visibleOrganizationCodes.value.includes(candidate)) return candidate
  return authState.user?.organizationCode ?? visibleOrganizationCodes.value[0] ?? ''
}

/** 从路由读取可用于datetime-local控件的本地时间文本。 */
function readDateTime(value: unknown) {
  const text = readQueryText(value, 40)
  if (!text) return ''
  const date = new Date(text)
  return Number.isNaN(date.getTime()) ? '' : toLocalDateTimeInput(date)
}

/** 读取允许的HIS终态筛选值。 */
function readResult(value: unknown): ExchangeResultFilter {
  return value === 'SUCCESS' || value === 'FAILURE' || value === 'NO_RESPONSE' || value === 'INVALID_RESPONSE'
    ? value
    : ''
}

/** 将本地时间控件值转为服务端明确要求的UTC时间。 */
function toUtcDateTime(value: string) {
  if (!value) return undefined
  const instant = new Date(value)
  return Number.isNaN(instant.getTime()) ? undefined : instant.toISOString().slice(0, 19)
}

/** 把可确认时间转换为datetime-local控件使用的本地分钟精度文本。 */
function toLocalDateTimeInput(value: Date) {
  const offsetMilliseconds = value.getTimezoneOffset() * 60_000
  return new Date(value.getTime() - offsetMilliseconds).toISOString().slice(0, 16)
}

/** 将未知异常收敛成页面可展示且不泄漏内部信息的错误。 */
function asApiError(caught: unknown) {
  return caught instanceof ApiClientError
    ? caught
    : new ApiClientError('UNKNOWN_ERROR', '无法读取HIS调用记录', 0)
}

/** 用当前筛选读取运行事实；不会触发HIS调用或补发。 */
async function loadRecords() {
  if (!organizationCode.value) {
    error.value = new ApiClientError('NO_ORGANIZATION_SCOPE', '当前账号没有可查询的机构范围', 403)
    records.value = []
    return
  }
  controller?.abort()
  const current = new AbortController()
  controller = current
  isLoading.value = true
  error.value = null
  try {
    const rows = await listExchangeRecords({
      organizationCode: organizationCode.value,
      interfaceCode: interfaceCode.value,
      sourceRecordId: sourceRecordId.value,
      result: result.value,
      receivedFrom: toUtcDateTime(receivedFrom.value),
      receivedTo: toUtcDateTime(receivedTo.value),
    }, current.signal)
    if (!mounted || current.signal.aborted) return
    records.value = rows
    page.value = 1
  } catch (caught) {
    const apiError = asApiError(caught)
    if (apiError.code === 'REQUEST_ABORTED') return
    if (apiError.status === 401) {
      authState.user = null
      authState.isInitialized = true
      await router.replace({ path: '/login', query: { redirect: '/exchanges' } })
      return
    }
    records.value = []
    error.value = apiError
  } finally {
    if (controller === current && mounted) isLoading.value = false
  }
}

/** 校验时间范围、同步安全路由条件并读取记录。 */
async function submitFilters() {
  filterError.value = ''
  if (receivedFrom.value && receivedTo.value && new Date(receivedFrom.value) > new Date(receivedTo.value)) {
    filterError.value = '开始时间不能晚于结束时间'
    return
  }
  const query: Record<string, string> = { organizationCode: organizationCode.value }
  if (interfaceCode.value.trim()) query.interfaceCode = interfaceCode.value.trim()
  if (sourceRecordId.value.trim()) query.sourceRecordId = sourceRecordId.value.trim()
  if (result.value) query.result = result.value
  if (receivedFrom.value) query.receivedFrom = receivedFrom.value
  if (receivedTo.value) query.receivedTo = receivedTo.value
  await router.replace({ path: '/exchanges', query })
  await loadRecords()
}

/** 清空可选筛选条件，保留当前机构范围。 */
async function clearFilters() {
  interfaceCode.value = ''
  sourceRecordId.value = ''
  result.value = ''
  receivedFrom.value = ''
  receivedTo.value = ''
  await submitFilters()
}

/** 返回无需仅依赖颜色识别的HIS终态文本。 */
function resultLabel(value: ExchangeRecord['result']) {
  if (value === null) return '终态未记录'
  return ({ SUCCESS: '成功', FAILURE: 'HIS明确失败', NO_RESPONSE: '结果未知', INVALID_RESPONSE: '响应不可确认' } as const)[value]
}

/** 返回终态对应的既有视觉标签类型。 */
function resultTone(value: ExchangeRecord['result']) {
  return value === 'SUCCESS' ? 'success' : value === 'FAILURE' ? 'danger' : 'warning'
}

/** 仅对已知关联前缀标注类型；其余值按普通业务引用展示，不推断业务含义。 */
function association(record: ExchangeRecord) {
  const endpointPrefix = 'EXTERNAL_ENDPOINT:'
  if (record.sourceRecordId.startsWith(endpointPrefix)) {
    return { type: '外部系统端点', id: record.sourceRecordId.slice(endpointPrefix.length) }
  }
  if (record.sourceRecordId.startsWith('BD-')) return { type: '同步批次', id: record.sourceRecordId }
  return { type: '业务关联编号', id: record.sourceRecordId }
}

/** 在用户机构代码旁展示当前会话已知的机构名称，不对其他授权代码伪造名称。 */
function organizationLabel(code: string) {
  const user = authState.user
  return user?.organizationCode === code && user.organizationName
    ? user.organizationName
    : code
}

/** 显示服务端提供的UTC时间；终态未确认时不把空值渲染为有效时间。 */
function formatExchangeTime(value: string | null) {
  return value === null ? '未确认' : formatLocalDateTime(value)
}

/** 仅使用后端权威目录解释交易码；未知码继续原样可见。 */
function tradeFor(code: string) {
  return trades.value.find(trade => trade.code === code)
}

/** 为目录筛选选项明确呈现公版文档收录状态。 */
const filteredTrades = computed(() => {
  const query = interfaceCode.value.trim().toLocaleLowerCase()
  const matched = query
    ? trades.value.filter(trade => `${trade.code} ${trade.displayName} ${trade.description}`.toLocaleLowerCase().includes(query))
    : trades.value
  return matched.slice(0, 50)
})

/** 延迟关闭交易目录菜单，让鼠标按下选项时先完成选择。 */
function deferTradeMenuClose() {
  window.setTimeout(() => { isTradeMenuOpen.value = false }, 120)
}

/** 选择权威交易目录项，仅回填交易码，查询仍由用户明确提交。 */
function selectTrade(trade: PhisTrade) {
  interfaceCode.value = trade.code
  isTradeMenuOpen.value = false
}

/** 清除交易目录筛选条件，不影响其他查询条件。 */
function clearTradeFilter() {
  interfaceCode.value = ''
  isTradeMenuOpen.value = false
}

/** 独立读取全局交易目录；失败时不阻断已授权调用记录查看。 */
async function loadTradeCatalog() {
  tradeController?.abort()
  const current = new AbortController()
  tradeController = current
  try {
    const catalog = await listPhisTrades(current.signal)
    if (!mounted || current.signal.aborted) return
    trades.value = catalog
    isTradeCatalogUnavailable.value = false
  } catch (caught) {
    const apiError = asApiError(caught)
    if (!mounted || current.signal.aborted || apiError.code === 'REQUEST_ABORTED') return
    trades.value = []
    isTradeCatalogUnavailable.value = true
  }
}

/** 打开不含报文和凭证的单次调用详情。 */
function openDetail(record: ExchangeRecord) {
  selectedRecord.value = record
  isDetailOpen.value = true
}

/** 关闭调用详情并清理其只读选择状态。 */
function closeDetail() {
  isDetailOpen.value = false
  selectedRecord.value = null
}

onMounted(() => {
  void loadRecords()
  void loadTradeCatalog()
})
onBeforeUnmount(() => {
  mounted = false
  controller?.abort()
  tradeController?.abort()
})
</script>

<template>
  <section class="content exchange-record-page">
    <ListQueryToolbar class="exchange-filters" :refreshing="isLoading" @query="submitFilters" @reset="clearFilters" @refresh="loadRecords">
      <label class="exchange-field"><span>所属机构</span><select v-model="organizationCode" aria-label="机构范围"><option v-for="code in visibleOrganizationCodes" :key="code" :value="code">{{ organizationLabel(code) }}</option></select></label>
      <div class="exchange-field trade-combobox"><label for="trade-filter">交易目录</label><div class="trade-combobox-control"><input id="trade-filter" v-model="interfaceCode" maxlength="64" autocomplete="off" placeholder="输入交易码，或从目录按名称选择" role="combobox" aria-label="HIS交易目录筛选" aria-controls="trade-options" :aria-expanded="isTradeMenuOpen" aria-autocomplete="list" @focus="isTradeMenuOpen = true" @blur="deferTradeMenuClose" @keydown.escape="isTradeMenuOpen = false" /><button v-if="interfaceCode" class="trade-clear" type="button" aria-label="清除交易目录" @mousedown.prevent @click="clearTradeFilter">×</button></div><div v-if="isTradeMenuOpen" id="trade-options" class="trade-options" role="listbox"><button v-for="trade in filteredTrades" :key="trade.code" class="trade-option" type="button" role="option" :aria-selected="interfaceCode === trade.code" @mousedown.prevent @click="selectTrade(trade)"><strong>{{ trade.code }}</strong><span>{{ trade.displayName }}</span><small>{{ trade.documentedInPublicSpecification ? '公版文档已收录' : '文档未收录' }}</small></button><p v-if="!filteredTrades.length" class="trade-options-empty">没有匹配的交易目录</p><p v-else-if="filteredTrades.length < trades.length" class="trade-options-hint">仅显示前 {{ filteredTrades.length }} 项，请继续输入缩小范围</p></div></div>
      <label class="prototype-search exchange-reference"><Search :size="16" /><input v-model="sourceRecordId" maxlength="128" autocomplete="off" placeholder="批次号 / 端点编号" aria-label="批次号或端点编号" /></label>
      <label class="exchange-field"><span>调用结果</span><select v-model="result" aria-label="调用终态"><option value="">全部结果</option><option value="SUCCESS">成功</option><option value="FAILURE">HIS明确失败</option><option value="NO_RESPONSE">结果未知</option><option value="INVALID_RESPONSE">响应不可确认</option></select></label>
      <label class="exchange-date"><span>请求开始时间</span><input v-model="receivedFrom" type="datetime-local" aria-label="请求开始时间" /></label>
      <label class="exchange-date"><span>请求结束时间</span><input v-model="receivedTo" type="datetime-local" aria-label="请求结束时间" /></label>
    </ListQueryToolbar>

    <div v-if="isTradeCatalogUnavailable" class="feedback warning" role="status"><AlertCircle :size="18" /><span>交易目录暂不可用，列表保留原始交易码；中文名称和文档状态未确认。</span><button class="work-quiet-button" type="button" @click="loadTradeCatalog">重试读取目录</button></div>
    <div v-if="filterError" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ filterError }}</span></div>
    <div v-if="error && !isLoading" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ error.message }}<small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button class="work-quiet-button" type="button" @click="loadRecords">重试</button></div>

    <section class="prototype-section work-table-section exchange-panel">
      <div v-if="isLoading" class="page-state" aria-live="polite"><LoaderCircle class="spinning" :size="28" /><strong>正在读取HIS调用记录</strong></div>
      <div v-else-if="!error && records.length === 0" class="page-state"><FileWarning :size="30" /><strong>当前条件下没有调用记录</strong><span>历史批次或端点校验发生在留痕功能接入前时，不会补造调用事实。</span></div>
      <div v-else-if="records.length" class="prototype-table-wrap">
        <table class="work-table exchange-table">
          <thead><tr><th>请求时间</th><th>接口名称</th><th>交易码</th><th>所属机构</th><th>业务关联</th><th>调用结果</th><th>处理摘要</th><th>操作</th></tr></thead>
          <tbody><tr v-for="record in pagedRows" :key="record.id">
            <td data-label="请求时间"><span class="exchange-cell-value"><span>{{ formatLocalDateTime(record.receivedAt) }}</span><code class="request-code">{{ record.requestId }}</code></span></td>
            <td data-label="接口名称"><span class="exchange-cell-value"><strong>{{ tradeFor(record.interfaceCode)?.displayName ?? '交易目录未登记' }}</strong><small v-if="tradeFor(record.interfaceCode)">{{ tradeFor(record.interfaceCode)?.category }} · {{ tradeFor(record.interfaceCode)?.documentedInPublicSpecification ? '公版文档已收录' : '文档未收录' }}</small><small v-else-if="!isTradeCatalogUnavailable">当前交易码未登记</small></span></td>
            <td data-label="交易码"><span class="exchange-cell-value"><code>{{ record.interfaceCode }}</code></span></td>
            <td data-label="所属机构"><span class="exchange-cell-value"><strong>{{ organizationLabel(record.organizationCode) }}</strong><code class="organization-code">{{ record.organizationCode }}</code></span></td>
            <td data-label="业务关联"><span class="exchange-cell-value"><strong>{{ association(record).type }}</strong><code>{{ association(record).id }}</code></span></td>
            <td data-label="调用结果"><span class="exchange-cell-value"><span class="prototype-tag" :class="resultTone(record.result)">{{ resultLabel(record.result) }}</span><small>{{ record.durationMs === null ? '耗时未确认' : `${record.durationMs} ms` }}</small></span></td>
            <td data-label="处理摘要" class="summary-cell"><span class="exchange-cell-value">{{ record.resultMessage ?? record.communicationErrorSummary ?? record.requestSummary ?? '未取得可展示的摘要' }}</span></td>
            <td data-label="操作"><span class="exchange-cell-value"><button class="work-quiet-button" type="button" @click="openDetail(record)">查看详情</button></span></td>
          </tr></tbody>
        </table>
      </div>
      <AdminPagination v-if="!isLoading && records.length" :total="records.length" :page="page" :page-size="pageSize" @update:page="page = $event" @update:page-size="pageSize = $event" />
    </section>

    <div v-if="selectedRecord" class="exchange-detail-layer" role="presentation" @mousedown.self="closeDetail">
      <section ref="dialogRef" class="exchange-detail" role="dialog" aria-modal="true" aria-labelledby="exchange-detail-title" tabindex="-1" @keydown="handleDialogKeydown">
        <header><div><small>HIS 调用记录 · {{ selectedRecord.organizationCode }}</small><h2 id="exchange-detail-title">{{ tradeFor(selectedRecord.interfaceCode)?.displayName ?? '交易目录未登记' }} · {{ resultLabel(selectedRecord.result) }}</h2><code>{{ selectedRecord.interfaceCode }}</code></div><button class="prototype-icon" type="button" aria-label="关闭详情" @click="closeDetail"><X :size="18" /></button></header>
        <dl>
          <div><dt>请求时间</dt><dd>{{ formatLocalDateTime(selectedRecord.receivedAt) }}</dd></div>
          <div><dt>完成时间</dt><dd>{{ formatExchangeTime(selectedRecord.processedAt) }}</dd></div>
          <div><dt>请求编号</dt><dd><code>{{ selectedRecord.requestId }}</code></dd></div>
          <div><dt>所属机构</dt><dd>{{ organizationLabel(selectedRecord.organizationCode) }}</dd></div>
          <div><dt>业务关联类型</dt><dd>{{ association(selectedRecord).type }}</dd></div>
          <div><dt>业务关联编号</dt><dd><code>{{ association(selectedRecord).id }}</code></dd></div>
          <div class="wide"><dt>交易说明 / 文档状态</dt><dd v-if="tradeFor(selectedRecord.interfaceCode)">{{ tradeFor(selectedRecord.interfaceCode)?.description }}<small>{{ tradeFor(selectedRecord.interfaceCode)?.category }} · {{ tradeFor(selectedRecord.interfaceCode)?.documentedInPublicSpecification ? '公版文档已收录' : '文档未收录，接口定义与调用约束待确认' }}</small></dd><dd v-else>{{ isTradeCatalogUnavailable ? '交易目录读取失败；中文名称、描述和文档状态暂不可用。' : '当前交易码不在PhisTrade权威目录中，保留原始交易码供核查。' }}</dd></div>
          <div><dt>调用方 / 目标</dt><dd>{{ selectedRecord.callerSystemCode }} → {{ selectedRecord.targetSystemCode }}</dd></div>
          <div><dt>调用耗时</dt><dd>{{ selectedRecord.durationMs === null ? '未确认' : `${selectedRecord.durationMs} ms` }}</dd></div>
          <div><dt>HIS返回码（协议原值）</dt><dd>{{ selectedRecord.targetResultCode ?? '未取得' }}<small>返回码含义以对应HIS交易接口文档为准。</small></dd></div>
          <div class="wide"><dt>请求摘要</dt><dd>{{ selectedRecord.requestSummary ?? '未保存请求摘要' }}</dd></div>
          <div class="wide"><dt>处理结果 / 通信摘要</dt><dd>{{ selectedRecord.resultMessage ?? selectedRecord.communicationErrorSummary ?? '未取得可展示的响应摘要' }}</dd></div>
          <div v-if="selectedRecord.communicationErrorSummary && selectedRecord.resultMessage" class="wide"><dt>通信异常摘要</dt><dd>{{ selectedRecord.communicationErrorSummary }}</dd></div>
        </dl>
        <footer><button class="prototype-button" type="button" @click="closeDetail">关闭</button></footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.exchange-record-page { display: grid; gap: 12px; container-type: inline-size; }
.exchange-filters { position: relative; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); align-items: end; gap: 10px; padding-bottom: 52px; }
.exchange-filters :deep(.standard-list-toolbar__filters) { display: contents; }
.exchange-filters :deep(.standard-list-toolbar__commands),.exchange-filters :deep(.standard-list-toolbar__utility) { min-width: 0; margin-left: 0; }
.exchange-filters :deep(.standard-list-toolbar__commands) { position: absolute; right: 76px; bottom: 10px; justify-content: flex-end; }
.exchange-filters :deep(.standard-list-toolbar__utility) { position: absolute; right: 12px; bottom: 10px; }
.exchange-field,.exchange-date { min-width: 0; display: grid; align-content: start; gap: 5px; color: var(--muted); font-size: 11px; }
.exchange-field select,.exchange-field input,.exchange-date input { width: 100%; min-width: 0; min-height: 38px; border: 1px solid var(--line); border-radius: 4px; padding: 0 9px; background: var(--surface); color: var(--ink); font: inherit; font-size: 12px; outline-color: var(--accent); }
.exchange-field select { max-width: none; }
.trade-combobox { position: relative; z-index: 20; }
.trade-combobox-control { position: relative; }
.trade-combobox-control input { padding-right: 30px; }
.trade-clear { position: absolute; top: 50%; right: 7px; width: 22px; height: 22px; transform: translateY(-50%); border: 0; border-radius: 50%; background: transparent; color: var(--muted); font-size: 18px; line-height: 1; cursor: pointer; }
.trade-clear:hover { background: #eaf5f2; color: var(--accent-dark); }
.trade-options { position: absolute; top: calc(100% + 5px); right: 0; left: 0; max-height: 280px; overflow-y: auto; padding: 5px; border: 1px solid var(--line); border-radius: 6px; background: var(--surface); box-shadow: 0 10px 26px rgb(23 70 64 / 18%); }
.trade-option { width: 100%; min-height: 52px; padding: 7px 9px; display: grid; grid-template-columns: 82px minmax(0,1fr); grid-template-rows:auto auto; gap: 2px 8px; border: 0; border-radius: 4px; background: transparent; color: var(--ink); text-align: left; cursor: pointer; }
.trade-option:hover,.trade-option[aria-selected="true"] { background: #edf7f4; }
.trade-option strong { grid-row: 1 / span 2; align-self: center; color: var(--ink); font-size: 12px; }
.trade-option span { min-width: 0; overflow: hidden; color: var(--ink); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.trade-option small { color: var(--muted); font-size: 10px; }
.trade-options-empty,.trade-options-hint { margin: 7px 8px; color: var(--muted); font-size: 11px; line-height: 1.4; }
.trade-options-hint { padding-top: 5px; border-top: 1px solid var(--line-soft); }
.exchange-filters :deep(.prototype-search) { width: 100%; min-width: 0; max-width: none; min-height: 38px; }
.exchange-filters :deep(.prototype-search input) { min-width: 0; width: 100%; }
.feedback { min-height: 46px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 9px; font-size: 12px; }.feedback > span { flex: 1; display: grid; gap: 2px; }.feedback small { color: inherit; opacity: .8; }.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }
.feedback.warning { border-color: #ead7ae; background: #fff9ed; color: #785d25; }
.page-state { min-height: 330px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: var(--accent); text-align: center; }.page-state span { max-width: 520px; color: var(--muted); font-size: 12px; line-height: 1.6; }
.exchange-table { min-width: 1480px; table-layout: fixed; }
.exchange-table th:nth-child(1) { width: 18%; }.exchange-table th:nth-child(2) { width: 16%; }.exchange-table th:nth-child(3) { width: 10%; }.exchange-table th:nth-child(4) { width: 16%; }.exchange-table th:nth-child(5) { width: 15%; }.exchange-table th:nth-child(6) { width: 10%; }.exchange-table th:nth-child(7) { width: 10%; }.exchange-table th:nth-child(8) { width: 5%; }
.exchange-table td { min-width: 0; vertical-align: top; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.exchange-table td small { display: block; min-width: 0; margin-top: 3px; color: var(--muted); font-size: 10px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.exchange-table .summary-cell { line-height: 1.55; }.exchange-table code,.request-code { display: block; min-width: 0; padding: 0; border: 0; border-radius: 0; background: transparent; color: var(--muted); font: inherit; font-size: 10px; line-height: 1.55; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.exchange-table .prototype-tag { display: inline; padding: 0; border: 0; border-radius: 0; background: transparent; font-size: inherit; line-height: inherit; }
.exchange-cell-value { min-width: 0; display: grid; align-content: start; gap: 3px; overflow: hidden; }
.exchange-table .organization-code { margin-top: 3px; }
.exchange-detail code { padding: 0; border: 0; border-radius: 0; background: transparent; font: inherit; }
.exchange-detail-layer { position: fixed; inset: 0; z-index: 120; padding: 24px; background: rgb(23 46 49 / 38%); display: flex; align-items: center; justify-content: center; }.exchange-detail { width: min(760px, 100%); max-height: 90vh; overflow-y: auto; border-radius: 7px; background: var(--surface); box-shadow: 0 18px 48px rgb(23 46 49 / 24%); }.exchange-detail > header { padding: 16px 20px; border-bottom: 1px solid var(--line); display: flex; align-items: center; justify-content: space-between; }.exchange-detail header small { color: var(--muted); font-size: 11px; }.exchange-detail h2 { margin: 3px 0 0; color: var(--ink); font-size: 18px; }.exchange-detail header code { display: inline-block; margin-top: 6px; color: var(--muted); font-size: 11px; }.exchange-detail dl { margin: 0; padding: 20px; display: grid; grid-template-columns: 1fr 1fr; gap: 15px 20px; }.exchange-detail dl div { min-width: 0; }.exchange-detail dl .wide { grid-column: 1 / -1; }.exchange-detail dt { color: var(--muted); font-size: 11px; }.exchange-detail dd { margin: 5px 0 0; color: var(--ink); font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }.exchange-detail dd small { display: block; margin-top: 3px; color: var(--muted); font-size: 10px; }.exchange-detail footer { padding: 13px 20px; border-top: 1px solid var(--line); display: flex; justify-content: flex-end; gap: 8px; }
.spinning { animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
@container (max-width: 1100px) {
  .exchange-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .exchange-filters :deep(.standard-list-toolbar__commands) { order: 0; grid-column: 1; }
  .exchange-filters :deep(.standard-list-toolbar__utility) { order: 0; grid-column: 2; }
  .exchange-filters { padding-bottom: 10px; }
  .exchange-filters :deep(.standard-list-toolbar__commands),.exchange-filters :deep(.standard-list-toolbar__utility) { position: static; }
  .exchange-table { display: block; min-width: 0; table-layout: auto; }
  .exchange-table thead { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
  .exchange-table tbody { display: grid; gap: 9px; padding: 10px; }
  .exchange-table tbody tr { min-width: 0; padding: 2px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border: 1px solid var(--line); border-radius: 5px; background: var(--surface); }
  .exchange-table tbody tr:last-child td { border-bottom: 0; }
  .exchange-table td { min-width: 0; display: grid; grid-template-columns: minmax(78px, .38fr) minmax(0, 1fr); align-items: start; gap: 8px; padding: 9px 10px; border-bottom: 1px solid var(--line-soft); text-align: left; white-space: nowrap; }
  .exchange-table td::before { min-width: 0; overflow: hidden; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
  .exchange-table td:nth-child(5),.exchange-table td:nth-child(7) { grid-column: 1 / -1; }
  .exchange-panel .exchange-table th:last-child,.exchange-panel .exchange-table td:last-child { position: static; width: auto; min-width: 0; max-width: none; text-align: left; background: transparent; box-shadow: none; }
}
@container (max-width: 640px) {
  .exchange-filters { grid-template-columns: minmax(0, 1fr); }
  .exchange-filters :deep(.standard-list-toolbar__commands),.exchange-filters :deep(.standard-list-toolbar__utility) { grid-column: 1; }
  .exchange-filters :deep(.standard-list-toolbar__commands) { grid-column: 1; justify-content: flex-start; }
  .exchange-table tbody tr { grid-template-columns: minmax(0, 1fr); }
      .exchange-table td:nth-child(5),.exchange-table td:nth-child(7) { grid-column: auto; }
  .exchange-detail-layer { padding: 12px; }
  .exchange-detail dl { grid-template-columns: 1fr; }
  .exchange-detail dl .wide { grid-column: auto; }
}
</style>
