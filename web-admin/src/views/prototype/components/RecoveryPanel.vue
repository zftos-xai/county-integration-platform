<!-- 原型异常恢复面板：演示不同恢复场景的状态迁移与阻断规则。 -->
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { IconArrowRight, IconCalendar, IconCheck, IconCopy, IconEdit, IconFile, IconFileText, IconInfoCircle, IconListDetails, IconSearch, IconServer, IconX } from '@tabler/icons-vue'
import AdminPagination from '@/components/AdminPagination.vue'
import { recoveryActions, recoveryBlock } from '../model/recoveryWorkflow'
import type { RecoveryAction, RecoveryDetails, RecoveryTask } from '../model/recoveryWorkflow'
import type { Scenario } from '../model/prototypeData'
import { businessResultLabel, businessResultTone, dataStatusLabel, dataStatusTone, interfaceStatusLabel, progressStatusLabel, progressStatusTone } from '../model/statusVocabulary'

type InterfaceReference = {
  id: string
  name: string
  version: string
  direction: string
  sourceSystem: string
  targetSystem: string
  status: string
  retention: string
}

const props = defineProps<{ tasks: RecoveryTask[]; role: string; scenarios: Scenario[]; interfaces: InterfaceReference[]; focusId: string; exceptionsOnly?: boolean }>()
const emit = defineEmits<{
  action: [id: string, action: RecoveryAction, evidence: string, note: string, revision: number, details: RecoveryDetails]
  exchange: [id: string]; record: [id: string, tab: 'overview' | 'work' | 'trace']; audit: [requestId: string]
  interface: [interfaceId: string]; operate: []; rehearse: [id: string]
}>()
const query = ref('')
const interfaceFilter = ref('全部接口')
const resultFilter = ref('全部')
const progressFilter = ref('全部')
const dataFilter = ref('全部')
const selected = ref(props.focusId || '')
const detailTab = ref<'overview' | 'version' | 'request' | 'trace'>('overview')
const action = ref<RecoveryAction | null>(null)
const evidence = ref('')
const note = ref('')
const nextCheckAt = ref('2026-09-15 16:00')
const externalOwner = ref('')
const externalDueAt = ref('2026-09-16 12:00')
const cleanupScope = ref('本次请求对应的暂存正文与重试副本')
const linkedRequestId = ref('')
const verificationOutcome = ref<'written' | 'not-written' | 'uncertain'>('uncertain')
const isCopied = ref(false)
const closeButton = ref<HTMLButtonElement | null>(null)
const checkedRows = ref<string[]>([])
const listRoot = ref<HTMLElement | null>(null)
const returnFocusId = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const task = computed(() => props.tasks.find(item => item.id === selected.value))
const scenario = computed(() => props.scenarios.find(item => item.id === task.value?.scenarioId))
const currentInterface = computed(() => props.interfaces.find(item => item.id === scenario.value?.interfaceCode))
const dataRows = computed(() => props.scenarios.map(item => {
  const recovery = props.tasks.find(t => t.scenarioId === item.id)
  const interfaceConfig = props.interfaces.find(config => config.id === item.interfaceCode)
  return { ...item, recovery, interfaceConfig, businessResult: businessResultLabel(recovery?.target ?? item.status),
    progress: recovery ? displayProgress(recovery) : scenarioProgress(item.status),
    saving: recovery?.kind === 'version' ? '仅保存交换记录' : recovery?.dataStatus ?? item.savedContent ?? '仅保存交换记录' }
}))
const selectedDataHandling = computed(() => {
  const row = dataRows.value.find(item => item.id === scenario.value?.id)
  return row ? dataHandling(row) : { title: '无正文暂存', sub: '无需清理' }
})
const rows = computed(() => dataRows.value
  .filter(item => !props.exceptionsOnly || Boolean(item.recovery))
  .filter(item => resultFilter.value === '全部' || item.businessResult === resultFilter.value)
  .filter(item => progressFilter.value === '全部' || item.progress === progressFilter.value)
  .filter(item => dataFilter.value === '全部' || dataHandling(item).title === dataFilter.value)
  .filter(item => interfaceFilter.value === '全部接口' || item.interfaceCode === interfaceFilter.value)
  .filter(item => `${item.sourceRecordId} ${item.requestId} ${item.organization} ${item.interfaceCode} ${item.interfaceName}`.toLowerCase().includes(query.value.trim().toLowerCase()))
  .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt)))
const resultOptions = computed(() => [...new Set(dataRows.value.map(item => item.businessResult))])
const progressOptions = computed(() => [...new Set(dataRows.value.map(item => item.progress))])
const dataOptions = computed(() => [...new Set(dataRows.value.map(item => dataHandling(item).title))])
const interfaceOptions = computed(() => [...new Set(dataRows.value.map(item => item.interfaceCode))]
  .map(code => ({ code, name: dataRows.value.find(item => item.interfaceCode === code)?.interfaceName ?? code })))
const hasFilters = computed(() => Boolean(query.value || interfaceFilter.value !== '全部接口' || resultFilter.value !== '全部' || progressFilter.value !== '全部' || dataFilter.value !== '全部'))
const pagedRows = computed(() => rows.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
const allVisibleChecked = computed(() => pagedRows.value.length > 0 && pagedRows.value.every(item => checkedRows.value.includes(item.id)))

function clearFilters() { query.value = ''; interfaceFilter.value = '全部接口'; resultFilter.value = '全部'; progressFilter.value = '全部'; dataFilter.value = '全部' }
function toggleAllVisible() {
  const visibleIds = pagedRows.value.map(item => item.id)
  checkedRows.value = allVisibleChecked.value ? checkedRows.value.filter(id => !visibleIds.includes(id)) : [...new Set([...checkedRows.value, ...visibleIds])]
}
function badgeToneClass(value: string) {
  const tone = businessResultTone(value)
  return tone === 'success' ? 'bg-green-lt text-green' : tone === 'primary' ? 'bg-blue-lt text-blue' : tone === 'warning' ? 'bg-yellow-lt text-yellow' : tone === 'danger' ? 'bg-red-lt text-red' : 'bg-secondary-lt text-secondary'
}
function systemCode(item: Scenario) {
  if (item.interfaceCode.startsWith('500') || item.sourceSystem.includes('PACS')) return 'PACS'
  if (item.interfaceCode.startsWith('600') || item.sourceSystem.includes('LIS')) return 'LIS'
  return 'HIS'
}
function transferName(item: Scenario) {
  if (item.domain.includes('报告')) return `${item.domain}数据传输`
  if (item.domain.includes('检验')) return '检验结果数据传输'
  return `${item.domain}数据同步`
}
function displayProgress(item: RecoveryTask) {
  return progressStatusLabel(item.status, Boolean(item.assignee))
}
function scenarioProgress(result: string) {
  if (businessResultLabel(result) === '结果未知') return '待核查'
  if (businessResultLabel(result) === '尚未发送') return '待发送'
  return '已完成'
}
function dataHandling(item: typeof dataRows.value[number]) {
  const t = item.recovery
  if (!t || t.kind === 'version' || ['仅保存交换记录', '原版本不可取得', '仅可重取最新版本'].includes(t.dataStatus)) return { title: '无正文暂存', sub: '无需清理' }
  if (t.dataStatus === '已清理') return { title: '已清理', sub: '保留处理记录' }
  if (t.dataStatus === '清理失败') return { title: '清理失败', sub: '需要重试' }
  if (t.dataStatus === '已到期') return { title: '待清理', sub: `已于 ${t.deadline.slice(0, 16)} 到期` }
  return { title: '临时保存中', sub: `到期 ${t.deadline.slice(0, 16)}` }
}
function requiresHuman(t?: RecoveryTask) {
  return Boolean(t && (t.kind === 'unknown' || t.kind === 'version' || t.kind === 'expiry' && t.cleanupFailed))
}
function handlingMode(t: RecoveryTask) {
  if (!requiresHuman(t)) return { label: '系统处理', detail: t.kind === 'expiry' ? '系统按既定范围执行清理' : '系统按状态规则继续处理' }
  if (t.kind === 'expiry') return { label: '运维处理', detail: '仅处理自动清理失败，不重新选择业务范围' }
  if (t.kind === 'version') return { label: '外部协同', detail: '平台登记处理人、期限和新请求关联' }
  return { label: '人工核查', detail: '人工只登记目标系统证据和后续时间' }
}
function currentWork(t?: RecoveryTask) {
  if (t?.kind === 'expiry' && !t.cleanupFailed && t.dataStatus === '已到期') return { title: '等待系统清理', sub: '无需人工操作' }
  if (!requiresHuman(t)) return { title: '已按规则处理', sub: '无需人工操作' }
  if (t?.externalOwner) return { title: '等待来源系统', sub: `${t.externalOwner} · ${t.externalDueAt || '期限待定'}` }
  if (t?.kind === 'unknown' && t.assignee) return { title: '核查接收结果', sub: `${t.assignee} · ${t.nextCheckAt ? `下次 ${t.nextCheckAt}` : '处理中'}` }
  if (t?.kind === 'expiry') return { title: '重试数据清理', sub: '待运维人员处理' }
  if (t?.kind === 'version') return { title: '联系来源系统', sub: '确认新版请求安排' }
  return { title: '核查接收结果', sub: '待运维人员领取' }
}
function followupSummary(t: RecoveryTask) {
  if (t.kind === 'unknown') return { label: '下次核查', value: t.nextCheckAt || (t.assignee ? nextCheckAt.value : '待领取后安排') }
  if (t.kind === 'version') return t.externalOwner
    ? { label: '外部完成期限', value: t.externalDueAt || '待补充' }
    : { label: '后续安排', value: t.linkedRequestId ? `已关联 ${t.linkedRequestId}` : '待登记来源系统处理' }
  if (t.kind === 'expiry') return t.cleanupFailed
    ? { label: '清理执行', value: '第 1 次失败，待运维重试' }
    : { label: t.cleanupScopeConfirmed ? '清理范围' : '数据到期时间', value: t.cleanupScope || t.deadline }
  return { label: '恢复检查', value: t.checked ? '检查已完成' : '待执行恢复检查' }
}
function stateRule(t: RecoveryTask) {
  if (t.kind === 'unknown') return { enteredBy: '请求已发出，但未取得明确业务响应', allowed: '查询目标系统并登记有效证据；结果未知时安排下次核查', forbidden: '在结果未知时再次发送或直接登记成功' }
  if (t.kind === 'version') return { enteredBy: `受理版本 v${t.originalVersion}.0 与来源最新版本 v${t.latestVersion}.0 不一致`, allowed: '登记来源系统处理人和期限，并关联来源系统重新提交的新请求', forbidden: '用最新版本覆盖原请求或恢复原版本发送' }
  if (t.kind === 'expiry') return { enteredBy: `临时数据已到期，业务结果仍为“${businessResultLabel(t.target)}”`, allowed: '停止发送后按批准范围清理；失败时由运维受控重试', forbidden: '清理与发送并发执行，或用清理结果改写业务结果' }
  return { enteredBy: '平台重启后发现受理记录未完成', allowed: '先检查原数据和发送状态，再决定是否恢复到待发送', forbidden: '跳过恢复检查或把恢复动作解释为目标端成功' }
}
function sendAttemptStatus(t: RecoveryTask) {
  if (t.kind === 'restart') return t.checked ? '恢复检查已完成' : '尚未发送，等待恢复检查'
  if (t.kind === 'unknown' || t.kind === 'expiry') return '请求已发出，响应不明确'
  if (t.kind === 'version') return '发送前版本校验已拦截'
  return '见发送证据'
}
function sendAttempts(t: RecoveryTask, item: Scenario) {
  const suffix = item.requestId.slice(-4)
  if (t.kind === 'version') return [{ id: `SIM-CHECK-${suffix}-01`, startedAt: item.occurredAt, stage: '发送前版本校验', result: '已拦截，未调用目标系统', response: '未产生目标响应', evidence: `SIM-VALIDATION-${suffix}` }]
  if (t.kind === 'restart') return [{ id: `SIM-RECOVERY-${suffix}-01`, startedAt: item.occurredAt, stage: '受理后恢复检查', result: t.checked ? '检查完成，恢复到待发送' : '待完成恢复检查', response: '尚未调用目标系统', evidence: t.checked ? `SIM-RECOVERY-${suffix}` : '待生成' }]
  return [{ id: `SIM-SEND-${suffix}-01`, startedAt: item.occurredAt, stage: '发送并等待响应', result: '等待响应超时', response: '未收到可判定业务结果的响应', evidence: `SIM-LOG-${suffix}` }]
}
function resultEvidenceRule(t: RecoveryTask) {
  if (t.status === '处理成功' || t.target.includes('已写入')) return { conclusion: '已确认写入', current: t.evidence || '已有目标端回执摘要', required: '目标系统业务成功码及回执编号，或按相同业务键查询到相同版本', state: '证据已具备' }
  if (t.status === '明确未写入') return { conclusion: '可确认未写入', current: t.evidence || '已有目标端查询记录', required: '目标系统按业务键和版本查询的无记录结果，或明确失败回执', state: '证据已具备' }
  if (t.kind === 'version') return { conclusion: '仅确认发送被阻止', current: '版本校验记录；未调用目标系统', required: '校验记录可证明未发送，但不能用于证明目标系统业务成功', state: '业务成功证据不适用' }
  if (t.kind === 'restart') return { conclusion: '仅确认恢复检查状态', current: t.checked ? '恢复检查记录' : '尚无完成证据', required: '恢复检查只能决定是否恢复待发送，不能证明目标系统已处理', state: t.checked ? '恢复证据已具备' : '待补充' }
  return { conclusion: '结果未知', current: '只有发送日志，未收到明确业务回执', required: '目标系统回执，或按相同业务键和版本查询形成的结果记录', state: '证据不足' }
}
function openRow(item: typeof dataRows.value[number], work = false) {
  if (!item.recovery) { emit('record', item.id, 'overview'); return }
  returnFocusId.value = item.id
  selected.value = item.recovery.id
  detailTab.value = item.recovery.kind === 'version' ? 'version' : 'overview'
  nextTick(() => closeButton.value?.focus())
}
function closeDetail() {
  selected.value = ''; action.value = null
  nextTick(() => listRoot.value?.querySelector<HTMLButtonElement>(`[data-row-action="${returnFocusId.value}"]`)?.focus())
}
async function copyRequestId() {
  if (!scenario.value) return
  await navigator.clipboard.writeText(scenario.value.sourceRecordId)
  isCopied.value = true
  window.setTimeout(() => { isCopied.value = false }, 1200)
}
function nextAction(t?: RecoveryTask): RecoveryAction | null {
  if (!t) return null
  if (t.kind === 'unknown') {
    if (!t.assignee) return 'claim'
    if (['结果未知', '持续核查'].includes(t.status)) return 'uncertain'
    if (t.status === '明确未写入') return t.checked ? 'resume' : 'check'
    return null
  }
  if (t.kind === 'version') return !t.handoff ? 'request-version' : !t.linkedRequestId ? 'link-request' : null
  if (t.kind === 'expiry') return !t.stopped ? 'stop' : !t.cleanupScopeConfirmed ? 'confirm-cleanup' : t.dataStatus !== '已清理' ? 'clean' : !t.handoff ? 'handoff' : null
  return !t.checked ? 'check' : t.status !== '待发送' ? 'resume' : null
}
function rowAction(item: typeof dataRows.value[number]) {
  if (props.role !== 'operator') return '查看详情'
  if (!requiresHuman(item.recovery)) return '查看记录'
  const next = nextAction(item.recovery)
  if (!next) return '查看记录'
  return next === 'claim' || !item.recovery?.assignee && !item.recovery?.externalOwner ? '开始处理' : '继续处理'
}
function chooseNext(item: typeof dataRows.value[number]) {
  openRow(item, true)
  nextTick(() => { action.value = props.role === 'operator' && item.recovery && requiresHuman(item.recovery) ? nextAction(item.recovery) : null })
}
const available = computed(() => {
  if (!task.value) return []
  return (Object.keys(recoveryActions) as RecoveryAction[]).filter(item => !recoveryBlock(task.value!, item, 'operator'))
})
const needsNextCheck = computed(() => action.value === 'uncertain')
const needsExternal = computed(() => ['request-version', 'handoff'].includes(action.value ?? ''))
const needsCleanup = computed(() => action.value === 'confirm-cleanup')
const needsLink = computed(() => action.value === 'link-request')
const ready = computed(() => {
  if (!task.value || !action.value || recoveryBlock(task.value, action.value, props.role)) return false
  if (action.value === 'claim') return true
  if (!evidence.value.trim() || !note.value.trim()) return false
  if (needsNextCheck.value && !nextCheckAt.value.trim()) return false
  if (needsExternal.value && (!externalOwner.value.trim() || !externalDueAt.value.trim())) return false
  if (needsCleanup.value && !cleanupScope.value.trim()) return false
  if (needsLink.value && !linkedRequestId.value.trim()) return false
  return true
})
// 父页面从关联入口定位记录时同步当前抽屉对象。
watch(() => props.focusId, id => { selected.value = id })
// 任一筛选条件变化后回到第一页。
watch([query, interfaceFilter, resultFilter, progressFilter, dataFilter], () => { currentPage.value = 1 })
// 切换事项、版本或角色时清空未提交的办理草稿，避免串到另一事项。
watch([selected, () => task.value?.revision, () => props.role], () => { evidence.value = ''; note.value = ''; action.value = null; externalOwner.value = ''; linkedRequestId.value = '' })
function selectVerification(value: 'written' | 'not-written' | 'uncertain') {
  verificationOutcome.value = value
  action.value = value
  note.value = value === 'written' ? '已在目标系统查到本次请求对应记录' : value === 'not-written' ? '目标系统确认未接收本次请求数据' : '当前证据仍不足以确认目标端处理结果'
}
function saveVerification() {
  if (!task.value || task.value.kind !== 'unknown') return
  if (!task.value.assignee) { action.value = 'claim'; submit(); return }
  selectVerification(verificationOutcome.value)
  submit()
}
function submit() {
  if (!ready.value || !task.value || !action.value) return
  emit('action', task.value.id, action.value, action.value === 'claim' ? 'SYSTEM-CLAIM' : evidence.value, action.value === 'claim' ? '当前运维领取处理事项' : note.value, task.value.revision, {
    assignee: '当前运维', nextCheckAt: nextCheckAt.value, externalOwner: externalOwner.value,
    externalDueAt: externalDueAt.value, cleanupScope: cleanupScope.value, linkedRequestId: linkedRequestId.value })
}
</script>

<template>
  <div ref="listRoot" class="recovery-page recovery-focused" :class="{ 'has-detail': task && scenario }">
    <div class="data-filterbar" role="search" aria-label="交换记录筛选">
      <div class="data-filter-primary"><label class="data-filter-search"><IconSearch :size="18" /><input v-model="query" aria-label="搜索交换记录" placeholder="业务单号、请求编号或机构名称" /></label><label class="data-filter-interface"><span>业务接口</span><select v-model="interfaceFilter" aria-label="业务接口"><option>全部接口</option><option v-for="item in interfaceOptions" :key="item.code" :value="item.code">{{ item.name }} · {{ item.code }}</option></select></label><span class="data-filter-count">共 {{ rows.length }} 条</span></div>
      <div class="data-filter-secondary"><label><span>业务结果</span><select v-model="resultFilter" aria-label="业务结果"><option>全部</option><option v-for="name in resultOptions" :key="name">{{ name }}</option></select></label><label><span>处理进度</span><select v-model="progressFilter" aria-label="处理进度"><option>全部</option><option v-for="name in progressOptions" :key="name">{{ name }}</option></select></label><label><span>数据保存</span><select v-model="dataFilter" aria-label="数据保存"><option>全部</option><option v-for="name in dataOptions" :key="name">{{ name }}</option></select></label><button v-if="hasFilters" class="btn btn-link btn-sm" @click="clearFilters">清空筛选</button></div>
    </div>

    <div class="card data-receipt-table action-column-table">
      <div class="table-responsive"><table class="table table-vcenter data-table linked-exchange-table"><colgroup><col class="col-check" /><col class="col-record" /><col class="col-route" /><col class="col-result" /><col class="col-data" /><col class="col-work" /><col class="col-action" /></colgroup><thead><tr><th class="data-check"><input class="form-check-input" type="checkbox" aria-label="选择全部记录" :checked="allVisibleChecked" @change="toggleAllVisible" /></th><th>业务记录</th><th>源 → 目标</th><th>业务结果</th><th>数据状态</th><th>当前待办</th><th>操作</th></tr></thead><tbody>
        <tr v-for="item in pagedRows" :key="item.id" :class="{ 'table-active-row': checkedRows.includes(item.id) || item.recovery?.id === selected }">
          <td class="data-check"><input v-model="checkedRows" class="form-check-input" type="checkbox" :value="item.id" :aria-label="`选择 ${item.sourceRecordId}`" /></td>
          <td><div class="business-record-cell"><span :class="['system-chip', systemCode(item).toLowerCase()]">{{ systemCode(item) }}</span><div><button class="data-record-link" @click="openRow(item)">{{ transferName(item) }}</button><small>{{ item.sourceRecordId }}<br />{{ item.requestId }}</small></div></div></td>
          <td><strong>{{ item.organization }}</strong><small><span class="direction-arrow">→</span> {{ item.targetSystem }}</small></td>
          <td><span class="badge" :class="badgeToneClass(item.businessResult)">{{ item.businessResult }}</span></td>
          <td><span class="status-text" :class="`status-text-${dataStatusTone(dataHandling(item).title)}`">{{ dataHandling(item).title }}</span><small>{{ dataHandling(item).sub }}</small></td>
          <td><div class="work-item-cell"><strong :class="{ 'text-red': requiresHuman(item.recovery) }">{{ currentWork(item.recovery).title }}</strong><small>{{ currentWork(item.recovery).sub }}</small></div></td>
          <td class="row-action-cell"><button class="btn btn-outline-primary btn-sm" :data-row-action="item.id" :aria-label="`${rowAction(item)} ${item.sourceRecordId}`" @click="chooseNext(item)">{{ rowAction(item) }} <IconArrowRight :size="14" /></button></td>
        </tr>
        <tr v-if="!rows.length"><td colspan="7" class="text-center text-secondary py-5">没有符合条件的交换记录</td></tr>
      </tbody></table></div><AdminPagination :total="rows.length" :page="currentPage" :page-size="pageSize" @update:page="currentPage = $event" @update:page-size="pageSize = $event" />
    </div>

    <div v-if="task && scenario" class="recovery-detail-pane" @keydown.esc="closeDetail">
      <section :key="task.id" class="data-record-drawer recovery-detail-workspace" role="region" aria-labelledby="record-title">
        <header class="drawer-titlebar"><div><h2 id="record-title">业务记录详情</h2><p class="drawer-request-id">{{ scenario.sourceRecordId }} <button class="btn btn-icon btn-ghost-secondary btn-sm" :aria-label="isCopied ? '已复制业务单号' : '复制业务单号'" @click="copyRequestId"><IconCheck v-if="isCopied" :size="15" /><IconCopy v-else :size="15" /></button></p><p class="drawer-interface-ref">{{ scenario.interfaceName }} · {{ scenario.interfaceCode }} · {{ scenario.interfaceVersion }}</p></div><button ref="closeButton" class="btn btn-icon btn-ghost-secondary" aria-label="关闭详情" @click="closeDetail"><IconX :size="23" /></button></header>
        <div class="drawer-summary reference-summary"><div><span>业务结果</span><strong><em class="status-pill" :class="businessResultTone(task.target)">{{ businessResultLabel(task.target) }}</em></strong></div><div><span>处理进度</span><strong><em class="status-pill" :class="progressStatusTone(displayProgress(task))">{{ displayProgress(task) }}</em></strong></div><div><span>数据状态</span><strong><em class="status-pill" :class="dataStatusTone(selectedDataHandling.title)">{{ selectedDataHandling.title }}</em></strong></div><div class="summary-owner"><span>当前责任人</span><strong>{{ requiresHuman(task) ? task.assignee || task.externalOwner || '待分配' : '无需人工' }}</strong></div><div class="summary-next"><span>{{ followupSummary(task).label }}</span><strong>{{ followupSummary(task).value }}</strong></div></div>
        <nav class="nav nav-tabs drawer-tabs four-tabs" aria-label="详情页签" role="tablist"><button :class="['nav-link', { active: detailTab === 'overview' }]" role="tab" :aria-selected="detailTab === 'overview'" @click="detailTab = 'overview'">概况</button><button :class="['nav-link', { active: detailTab === 'version' }]" role="tab" :aria-selected="detailTab === 'version'" @click="detailTab = 'version'">数据版本</button><button :class="['nav-link', { active: detailTab === 'request' }]" role="tab" :aria-selected="detailTab === 'request'" @click="detailTab = 'request'">发送请求</button><button :class="['nav-link', { active: detailTab === 'trace' }]" role="tab" :aria-selected="detailTab === 'trace'" @click="detailTab = 'trace'">处理记录</button></nav>

        <div class="drawer-body">
          <template v-if="detailTab === 'overview'">
            <section class="detail-section record-interface-section"><div class="section-heading"><h3>本次采用的接口规则</h3><button class="btn btn-outline-secondary btn-sm" @click="emit('interface', scenario.interfaceCode)">查看接口定义 <IconArrowRight :size="14" /></button></div><div class="record-interface-grid"><div><span>业务接口</span><strong>{{ scenario.interfaceName }}</strong><small>{{ scenario.interfaceCode }}</small></div><div><span>受理时规则版本</span><strong>{{ scenario.interfaceVersion }}</strong><small>历史记录固定保留</small></div><div><span>当前接口版本</span><strong>{{ currentInterface?.version ?? '尚未登记' }}</strong><small>{{ currentInterface ? interfaceStatusLabel(currentInterface.status) : '需补充接口定义' }}</small></div><div><span>关联检查</span><strong :class="scenario.interfaceLinkStatus === '已关联' ? 'text-green' : 'text-yellow'">{{ scenario.interfaceLinkStatus }}</strong><small>{{ scenario.direction }}</small></div></div><p v-if="currentInterface?.version !== scenario.interfaceVersion" class="record-version-warning">当前接口版本与受理时规则版本不同，历史记录继续按受理时快照解释，不回写覆盖。</p></section>
            <section class="detail-section object-chain-section"><h3>本记录关联对象</h3><div class="object-chain"><div><span>1</span><strong>业务接口</strong><small>{{ scenario.interfaceCode }} · {{ scenario.interfaceVersion }}</small></div><div><span>2</span><strong>交换请求</strong><small>{{ scenario.requestId }}</small></div><div><span>3</span><strong>发送尝试</strong><small>{{ sendAttemptStatus(task) }}</small></div><div><span>4</span><strong>业务结果</strong><small>{{ businessResultLabel(task.target) }}</small></div><div><span>5</span><strong>数据与事项</strong><small>{{ selectedDataHandling.title }} · {{ requiresHuman(task) ? '需要跟踪' : '系统处理' }}</small></div></div></section>
            <section class="detail-section reference-section"><div class="section-heading"><h3>查询条件</h3><button class="btn btn-outline-secondary btn-sm" @click="emit('record', scenario.id, 'overview')"><IconEdit :size="15" /> 查看完整条件</button></div><dl class="reference-facts"><div><dt>目标系统</dt><dd>{{ scenario.targetSystem }}</dd></div><div><dt>数据来源机构</dt><dd>{{ scenario.organization }}</dd></div><div><dt>业务编号</dt><dd>{{ scenario.sourceRecordId }}</dd></div><div><dt>请求编号</dt><dd>{{ scenario.requestId }}</dd></div><div><dt>数据版本</dt><dd>v{{ task.originalVersion }}.0</dd></div></dl></section>
            <section class="detail-section state-rule-section"><h3>当前状态规则</h3><dl class="reference-facts"><div><dt>处理方式</dt><dd><strong>{{ handlingMode(task).label }}</strong> · {{ handlingMode(task).detail }}</dd></div><div><dt>进入原因</dt><dd>{{ stateRule(task).enteredBy }}</dd></div><div><dt>当前允许</dt><dd>{{ stateRule(task).allowed }}</dd></div><div><dt>当前禁止</dt><dd class="text-red">{{ stateRule(task).forbidden }}</dd></div></dl></section>

            <template v-if="task.kind === 'unknown'">
              <section class="detail-section reference-section"><div class="section-heading"><h3>证据信息</h3><span class="section-help"><IconInfoCircle :size="15" /> 说明</span></div><div class="evidence-card"><div><IconFileText :size="17" /><span>已发送请求</span><strong>{{ scenario.occurredAt }}</strong><button class="btn btn-link btn-sm" @click="emit('exchange', scenario.id)">查看请求报文</button></div><div><IconServer :size="17" /><span>接收系统响应</span><strong>{{ task.target.includes('无法') || task.target.includes('未知') ? '未收到明确响应' : task.target }}</strong></div><div><IconFile :size="17" /><span>本地数据文件</span><strong>{{ task.dataStatus === '暂存可用' ? `request_${scenario.requestId.slice(-4)}.xml（演示文件）` : '未保存业务正文' }}</strong></div><div><IconListDetails :size="17" /><span>相关日志</span><strong>应用日志 {{ scenario.steps.length + 4 }} 条</strong><button class="btn btn-link btn-sm" @click="emit('audit', scenario.requestId)">查看日志</button></div></div></section>

              <section class="detail-section reference-section verification-section"><h3>核查结果 <small>（请选择或登记当前核查结论）</small></h3><div v-if="role !== 'operator'" class="claim-strip"><strong>管理视角只读</strong><span>切换到运维视角后，可以领取并登记核查结论。</span></div><div v-else-if="!task.assignee" class="claim-strip"><strong>该记录尚未领取</strong><span>先领取后才能登记目标系统核查结论。</span></div><div class="verification-options" role="radiogroup" aria-label="核查结果"><button :class="{ selected: verificationOutcome === 'written' }" role="radio" :aria-checked="verificationOutcome === 'written'" :disabled="role !== 'operator' || !task.assignee || Boolean(recoveryBlock(task, 'written', 'operator'))" @click="selectVerification('written')"><span class="choice-dot"><IconCheck v-if="verificationOutcome === 'written'" :size="13" /></span><strong>已确认写入</strong><small>已在对方系统中查到</small></button><button :class="{ selected: verificationOutcome === 'not-written' }" role="radio" :aria-checked="verificationOutcome === 'not-written'" :disabled="role !== 'operator' || !task.assignee || Boolean(recoveryBlock(task, 'not-written', 'operator'))" @click="selectVerification('not-written')"><span class="choice-dot"><IconCheck v-if="verificationOutcome === 'not-written'" :size="13" /></span><strong>明确未写入</strong><small>确认未接收到数据</small></button><button :class="{ selected: verificationOutcome === 'uncertain' }" role="radio" :aria-checked="verificationOutcome === 'uncertain'" :disabled="role !== 'operator' || !task.assignee || Boolean(recoveryBlock(task, 'uncertain', 'operator'))" @click="selectVerification('uncertain')"><span class="choice-dot"><IconCheck v-if="verificationOutcome === 'uncertain'" :size="13" /></span><strong>结果未知</strong><small>需进一步核查</small></button></div></section>

              <section class="detail-section reference-section"><h3>其他信息</h3><div class="reference-form"><label>外部记录编号<input v-model="evidence" class="form-control" :disabled="!task.assignee || role !== 'operator'" placeholder="请输入外部系统记录编号" /></label><label>下次核查时间<div class="input-with-icon"><input v-model="nextCheckAt" class="form-control" :disabled="!task.assignee || role !== 'operator' || verificationOutcome !== 'uncertain'" /><IconCalendar :size="17" /></div></label></div></section>
              <div class="reference-actions"><button class="btn btn-primary" :disabled="role === 'operator' && Boolean(task.assignee) && (!evidence.trim() || verificationOutcome === 'uncertain' && !nextCheckAt.trim())" @click="role === 'operator' ? saveVerification() : emit('operate')">{{ role !== 'operator' ? '切换运维视角' : task.assignee ? '保存核查结果' : '确认领取' }}</button><button class="btn btn-outline-secondary" @click="closeDetail">关闭</button></div>
            </template>
            <template v-else><section class="detail-section"><h3>当前判断</h3><p class="alert alert-warning mb-0">{{ task.title }}。{{ scenario.receipt }}</p></section><section v-if="task.kind === 'expiry'" class="detail-section"><h3>清理执行证据</h3><dl class="reference-facts"><div><dt>发送控制</dt><dd>{{ task.stopped ? '已停止发送' : '待停止发送' }}</dd></div><div><dt>清理范围</dt><dd>{{ task.cleanupScope || '待按既定规则确认' }}</dd></div><div><dt>数据状态</dt><dd>{{ dataStatusLabel(task.dataStatus) }}</dd></div><div><dt>执行次数</dt><dd>{{ task.cleanupFailed ? '第 1 次自动清理失败' : '尚未执行或已完成' }}</dd></div><div><dt>最近错误</dt><dd>{{ task.cleanupFailed ? 'TEMP_DATA_DELETE_TIMEOUT（合成演示）' : '—' }}</dd></div><div><dt>到期时间</dt><dd>{{ task.deadline }}</dd></div></dl><div class="alert alert-info mb-0">清理只处理批准范围内的临时数据，不改变业务结果；失败后保留执行证据并生成运维事项。</div></section></template>
          </template>

          <template v-else-if="detailTab === 'version'">
            <section v-if="task.kind === 'version'" class="detail-section version-section"><h3>版本信息对比</h3><div class="version-compare"><article class="version-card old"><h4>原请求第 {{ task.originalVersion }} 版（已失败）</h4><dl><div><dt>请求编号</dt><dd>{{ scenario.requestId }}</dd></div><div><dt>版本号</dt><dd>v{{ task.originalVersion }}.0</dd></div><div><dt>请求时间</dt><dd>{{ scenario.occurredAt }}</dd></div><div><dt>业务结果</dt><dd class="text-red">{{ businessResultLabel(task.target) }}</dd></div><div><dt>处理进度</dt><dd class="text-red">已拦截</dd></div></dl></article><article class="version-card latest"><h4>源系统第 {{ task.latestVersion }} 版（最新）</h4><dl><div><dt>来源系统</dt><dd>{{ scenario.sourceSystem }}</dd></div><div><dt>版本号</dt><dd>v{{ task.latestVersion }}.0</dd></div><div><dt>生成时间</dt><dd>2026-09-15 14:20:00</dd></div><div><dt>文件状态</dt><dd class="text-green">可获取</dd></div><div><dt>处理进度</dt><dd class="text-green">等待新请求</dd></div></dl></article></div><div class="version-stop"><strong>禁止恢复原请求</strong><span>原请求第 {{ task.originalVersion }} 版已失败并被系统拦截，不允许恢复处理。请引导源系统按第 {{ task.latestVersion }} 版重新发起独立请求。</span></div></section>
            <section v-else class="detail-section"><h3>数据版本</h3><dl class="reference-facts"><div><dt>本次请求版本</dt><dd>v{{ task.originalVersion }}.0</dd></div><div><dt>来源最新版本</dt><dd>v{{ task.latestVersion }}.0</dd></div><div><dt>版本判断</dt><dd>{{ task.originalVersion === task.latestVersion ? '版本一致' : '版本不一致' }}</dd></div></dl></section>
          </template>

          <section v-else-if="detailTab === 'request'" class="detail-section request-evidence"><h3>逐次发送记录</h3><p class="section-help">一次交换请求可以产生多次技术执行；每次尝试单独保存阶段、结果和证据编号。</p><div class="table-responsive"><table class="table table-sm send-attempt-table"><thead><tr><th>尝试编号</th><th>开始时间</th><th>执行阶段</th><th>技术结果</th><th>目标响应</th><th>证据编号</th></tr></thead><tbody><tr v-for="attempt in sendAttempts(task, scenario)" :key="attempt.id"><td><strong>{{ attempt.id }}</strong></td><td>{{ attempt.startedAt }}</td><td>{{ attempt.stage }}</td><td>{{ attempt.result }}</td><td>{{ attempt.response }}</td><td>{{ attempt.evidence }}</td></tr></tbody></table></div><h3>业务结果证据标准</h3><dl class="reference-facts evidence-standard"><div><dt>当前结论</dt><dd><strong>{{ resultEvidenceRule(task).conclusion }}</strong></dd></div><div><dt>现有证据</dt><dd>{{ resultEvidenceRule(task).current }}</dd></div><div><dt>确认要求</dt><dd>{{ resultEvidenceRule(task).required }}</dd></div><div><dt>充分性</dt><dd>{{ resultEvidenceRule(task).state }}</dd></div><div><dt>数据保存</dt><dd>{{ dataStatusLabel(task.dataStatus) }}；截止 {{ task.deadline }}</dd></div></dl><div class="alert alert-info mt-3 mb-0">发送成功、网络成功或恢复检查通过，都不能单独证明目标系统已完成业务处理。</div><button class="btn btn-link px-0" @click="emit('exchange', scenario.id)">查看交换处理轨迹</button></section>
          <section v-else class="detail-section"><h3>处理记录</h3><p v-if="!task.history.length" class="text-secondary">暂无人工处理记录。</p><ol v-else class="audit-timeline"><li v-for="(event, index) in task.history" :key="index"><span><IconCheck :size="13" /></span><div><strong>{{ event.action }}</strong><p>{{ event.note }}</p><small>{{ event.time }} · 证据 {{ event.evidence }}</small></div></li></ol><button class="btn btn-link px-0" @click="emit('audit', scenario.requestId)">查看操作审计</button></section>

          <section v-if="(detailTab === 'overview' && task.kind !== 'unknown' && task.kind !== 'version') || (detailTab === 'version' && task.kind === 'version')" class="detail-section action-section"><h3>下一步处理</h3>
              <div v-if="!requiresHuman(task)" class="alert alert-info mb-0"><strong>本记录无需人工办理。</strong><br />平台按既定规则自动检查、停止、恢复或清理；本页只展示执行结果和证据。自动处理失败时，系统会生成待处理事项。</div>
              <div v-else-if="role === 'manager'" class="alert alert-info">管理视角可查看进度；例外事项由运维人员处理。<button class="btn btn-primary btn-sm ms-2" @click="emit('operate')">切换运维</button></div>
              <template v-else-if="available.length"><div class="action-choice"><button v-for="choice in available" :key="choice" :class="['btn btn-sm', action === choice ? 'btn-primary' : 'btn-outline-secondary']" @click="action = choice">{{ recoveryActions[choice] }}</button></div>
                <form v-if="action" class="action-form" @submit.prevent="submit">
                  <template v-if="action === 'claim'"><div class="claim-confirm"><strong>领取后由当前运维负责处理</strong><span>系统会记录领取时间。后续动作将按本场景依次开放。</span></div></template>
                  <template v-else>
                  <label v-if="needsNextCheck">下次核查时间<input v-model="nextCheckAt" class="form-control" required /></label>
                  <template v-if="needsExternal"><label>外部处理人<input v-model="externalOwner" class="form-control" required placeholder="姓名 / 单位 / 联系方式" /></label><label>完成期限<input v-model="externalDueAt" class="form-control" required /></label></template>
                  <label v-if="needsCleanup">清理范围<input v-model="cleanupScope" class="form-control" required /></label>
                  <label v-if="needsLink">新请求编号<input v-model="linkedRequestId" class="form-control" required placeholder="输入源系统重新提交的请求编号" /></label>
                  <label>证据编号<input v-model="evidence" class="form-control" required placeholder="查询记录、回执、审批或工单编号" /></label>
                  <label>处理说明<textarea v-model="note" class="form-control" required rows="3" placeholder="记录核查对象、结论和后续安排"></textarea></label>
                  </template>
                  <div><button class="btn btn-primary" :disabled="!ready"><IconCheck :size="16" /> {{ action === 'claim' ? '确认领取' : '保存处理结果' }}</button><button type="button" class="btn btn-ghost-secondary ms-2" @click="action = null">取消</button></div>
                </form>
              </template><p v-else class="text-secondary">当前记录已完成本页处理，可在处理记录中查看证据。</p>
          </section>
        </div>
        <footer v-if="task.kind !== 'unknown'" class="drawer-footer"><span>演示数据，操作不会触发真实发送或清理</span><button class="btn btn-outline-secondary btn-sm" @click="emit('rehearse', task.id)">重新演练</button></footer>
      </section>
    </div>
  </div>
</template>
