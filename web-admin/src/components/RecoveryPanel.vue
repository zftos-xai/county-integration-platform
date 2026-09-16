<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { IconArrowRight, IconCalendar, IconCheck, IconCopy, IconEdit, IconFile, IconFileText, IconInfoCircle, IconListDetails, IconSearch, IconServer, IconX } from '@tabler/icons-vue'
import { recoveryActions, recoveryBlock } from '../recoveryWorkflow'
import type { RecoveryAction, RecoveryDetails, RecoveryTask } from '../recoveryWorkflow'
import type { Scenario } from '../prototypeData'
import AdminPagination from './AdminPagination.vue'

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
const copied = ref(false)
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
  return { ...item, recovery, interfaceConfig, businessResult: recovery?.target ?? item.status,
    progress: recovery ? displayProgress(recovery) : (['处理成功', '重复已识别'].includes(item.status) ? '已完成' : item.status),
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
  return item.kind === 'unknown' && item.assignee && item.status === '结果未知' ? '核查中' : item.status
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
function currentWork(t?: RecoveryTask) {
  if (!requiresHuman(t)) return { title: '已按规则处理', sub: '无需人工操作' }
  if (t?.externalOwner) return { title: '等待来源系统', sub: `${t.externalOwner} · ${t.externalDueAt || '期限待定'}` }
  if (t?.kind === 'unknown' && t.assignee) return { title: '核查接收结果', sub: `${t.assignee} · ${t.nextCheckAt ? `下次 ${t.nextCheckAt}` : '处理中'}` }
  if (t?.kind === 'expiry') return { title: '重试数据清理', sub: '待运维人员处理' }
  if (t?.kind === 'version') return { title: '联系来源系统', sub: '确认新版请求安排' }
  return { title: '核查接收结果', sub: '待运维人员领取' }
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
  copied.value = true
  window.setTimeout(() => { copied.value = false }, 1200)
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
watch(() => props.focusId, id => { selected.value = id })
watch([query, interfaceFilter, resultFilter, progressFilter, dataFilter], () => { currentPage.value = 1 })
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
          <td><span class="badge" :class="item.businessResult.includes('成功') || item.businessResult === '已写入' ? 'bg-green-lt text-green' : item.businessResult.includes('未知') || item.businessResult.includes('无法') ? 'bg-yellow-lt text-yellow' : 'bg-red-lt text-red'">{{ item.businessResult }}</span></td>
          <td><strong :class="{ 'text-red': dataHandling(item).title === '清理失败' || dataHandling(item).title === '待清理' }">{{ dataHandling(item).title }}</strong><small>{{ dataHandling(item).sub }}</small></td>
          <td><div class="work-item-cell"><strong :class="{ 'text-red': requiresHuman(item.recovery) }">{{ currentWork(item.recovery).title }}</strong><small>{{ currentWork(item.recovery).sub }}</small></div></td>
          <td class="row-action-cell"><button class="btn btn-outline-primary btn-sm" :data-row-action="item.id" :aria-label="`${rowAction(item)} ${item.sourceRecordId}`" @click="chooseNext(item)">{{ rowAction(item) }} <IconArrowRight :size="14" /></button></td>
        </tr>
        <tr v-if="!rows.length"><td colspan="7" class="text-center text-secondary py-5">没有符合条件的交换记录</td></tr>
      </tbody></table></div><AdminPagination :total="rows.length" :page="currentPage" :page-size="pageSize" @update:page="currentPage = $event" @update:page-size="pageSize = $event" />
    </div>

    <div v-if="task && scenario" class="recovery-detail-pane" @keydown.esc="closeDetail">
      <section :key="task.id" class="data-record-drawer recovery-detail-workspace" role="region" aria-labelledby="record-title">
        <header class="drawer-titlebar"><div><h2 id="record-title">业务记录详情</h2><p class="drawer-request-id">{{ scenario.sourceRecordId }} <button class="btn btn-icon btn-ghost-secondary btn-sm" :aria-label="copied ? '已复制业务单号' : '复制业务单号'" @click="copyRequestId"><IconCheck v-if="copied" :size="15" /><IconCopy v-else :size="15" /></button></p><p class="drawer-interface-ref">{{ scenario.interfaceName }} · {{ scenario.interfaceCode }} · {{ scenario.interfaceVersion }}</p></div><button ref="closeButton" class="btn btn-icon btn-ghost-secondary" aria-label="关闭详情" @click="closeDetail"><IconX :size="23" /></button></header>
        <div class="drawer-summary reference-summary"><div><span>业务结果</span><strong><em class="status-pill warning">{{ task.target }}</em></strong></div><div><span>处理进度</span><strong><em class="status-pill primary">{{ displayProgress(task) }}</em></strong></div><div><span>数据状态</span><strong><em class="status-pill success">{{ selectedDataHandling.title }}</em></strong></div><div class="summary-owner"><span>当前责任人</span><strong>{{ requiresHuman(task) ? task.assignee || task.externalOwner || '待分配' : '无需人工' }}</strong></div><div class="summary-next"><span>下次核查时间</span><strong>{{ task.nextCheckAt || (requiresHuman(task) ? nextCheckAt : '—') }}</strong></div></div>
        <nav class="nav nav-tabs drawer-tabs four-tabs" aria-label="详情页签" role="tablist"><button :class="['nav-link', { active: detailTab === 'overview' }]" role="tab" :aria-selected="detailTab === 'overview'" @click="detailTab = 'overview'">概况</button><button :class="['nav-link', { active: detailTab === 'version' }]" role="tab" :aria-selected="detailTab === 'version'" @click="detailTab = 'version'">数据版本</button><button :class="['nav-link', { active: detailTab === 'request' }]" role="tab" :aria-selected="detailTab === 'request'" @click="detailTab = 'request'">发送请求</button><button :class="['nav-link', { active: detailTab === 'trace' }]" role="tab" :aria-selected="detailTab === 'trace'" @click="detailTab = 'trace'">处理记录</button></nav>

        <div class="drawer-body">
          <template v-if="detailTab === 'overview'">
            <section class="detail-section record-interface-section"><div class="section-heading"><h3>本次采用的接口规则</h3><button class="btn btn-outline-secondary btn-sm" @click="emit('interface', scenario.interfaceCode)">查看接口定义 <IconArrowRight :size="14" /></button></div><div class="record-interface-grid"><div><span>业务接口</span><strong>{{ scenario.interfaceName }}</strong><small>{{ scenario.interfaceCode }}</small></div><div><span>受理时规则版本</span><strong>{{ scenario.interfaceVersion }}</strong><small>历史记录固定保留</small></div><div><span>当前接口版本</span><strong>{{ currentInterface?.version ?? '尚未登记' }}</strong><small>{{ currentInterface?.status ?? '需补充接口定义' }}</small></div><div><span>关联检查</span><strong :class="scenario.interfaceLinkStatus === '已关联' ? 'text-green' : 'text-yellow'">{{ scenario.interfaceLinkStatus }}</strong><small>{{ scenario.direction }}</small></div></div><p v-if="currentInterface?.version !== scenario.interfaceVersion" class="record-version-warning">当前接口版本与受理时规则版本不同，历史记录继续按受理时快照解释，不回写覆盖。</p></section>
            <section class="detail-section reference-section"><div class="section-heading"><h3>查询条件</h3><button class="btn btn-outline-secondary btn-sm" @click="emit('record', scenario.id, 'overview')"><IconEdit :size="15" /> 查看完整条件</button></div><dl class="reference-facts"><div><dt>目标系统</dt><dd>{{ scenario.targetSystem }}</dd></div><div><dt>数据来源机构</dt><dd>{{ scenario.organization }}</dd></div><div><dt>业务编号</dt><dd>{{ scenario.sourceRecordId }}</dd></div><div><dt>请求编号</dt><dd>{{ scenario.requestId }}</dd></div><div><dt>数据版本</dt><dd>v{{ task.originalVersion }}.0</dd></div></dl></section>

            <template v-if="task.kind === 'unknown'">
              <section class="detail-section reference-section"><div class="section-heading"><h3>证据信息</h3><span class="section-help"><IconInfoCircle :size="15" /> 说明</span></div><div class="evidence-card"><div><IconFileText :size="17" /><span>已发送请求</span><strong>{{ scenario.occurredAt }}</strong><button class="btn btn-link btn-sm" @click="emit('exchange', scenario.id)">查看请求报文</button></div><div><IconServer :size="17" /><span>接收系统响应</span><strong>{{ task.target.includes('无法') || task.target.includes('未知') ? '未收到明确响应' : task.target }}</strong></div><div><IconFile :size="17" /><span>本地数据文件</span><strong>{{ task.dataStatus === '暂存可用' ? `request_${scenario.requestId.slice(-4)}.xml（演示文件）` : '未保存业务正文' }}</strong></div><div><IconListDetails :size="17" /><span>相关日志</span><strong>应用日志 {{ scenario.steps.length + 4 }} 条</strong><button class="btn btn-link btn-sm" @click="emit('audit', scenario.requestId)">查看日志</button></div></div></section>

              <section class="detail-section reference-section verification-section"><h3>核查结果 <small>（请选择或登记当前核查结论）</small></h3><div v-if="role !== 'operator'" class="claim-strip"><strong>管理视角只读</strong><span>切换到运维视角后，可以领取并登记核查结论。</span></div><div v-else-if="!task.assignee" class="claim-strip"><strong>该记录尚未领取</strong><span>先领取后才能登记目标系统核查结论。</span></div><div class="verification-options" role="radiogroup" aria-label="核查结果"><button :class="{ selected: verificationOutcome === 'written' }" role="radio" :aria-checked="verificationOutcome === 'written'" :disabled="role !== 'operator' || !task.assignee || Boolean(recoveryBlock(task, 'written', 'operator'))" @click="selectVerification('written')"><span class="choice-dot"><IconCheck v-if="verificationOutcome === 'written'" :size="13" /></span><strong>已写入</strong><small>已在对方系统中查到</small></button><button :class="{ selected: verificationOutcome === 'not-written' }" role="radio" :aria-checked="verificationOutcome === 'not-written'" :disabled="role !== 'operator' || !task.assignee || Boolean(recoveryBlock(task, 'not-written', 'operator'))" @click="selectVerification('not-written')"><span class="choice-dot"><IconCheck v-if="verificationOutcome === 'not-written'" :size="13" /></span><strong>明确未写入</strong><small>确认未接收到数据</small></button><button :class="{ selected: verificationOutcome === 'uncertain' }" role="radio" :aria-checked="verificationOutcome === 'uncertain'" :disabled="role !== 'operator' || !task.assignee || Boolean(recoveryBlock(task, 'uncertain', 'operator'))" @click="selectVerification('uncertain')"><span class="choice-dot"><IconCheck v-if="verificationOutcome === 'uncertain'" :size="13" /></span><strong>暂时无法确认</strong><small>需进一步核查</small></button></div></section>

              <section class="detail-section reference-section"><h3>其他信息</h3><div class="reference-form"><label>外部记录编号<input v-model="evidence" class="form-control" :disabled="!task.assignee || role !== 'operator'" placeholder="请输入外部系统记录编号" /></label><label>下次核查时间<div class="input-with-icon"><input v-model="nextCheckAt" class="form-control" :disabled="!task.assignee || role !== 'operator' || verificationOutcome !== 'uncertain'" /><IconCalendar :size="17" /></div></label></div></section>
              <div class="reference-actions"><button class="btn btn-primary" :disabled="role === 'operator' && Boolean(task.assignee) && (!evidence.trim() || verificationOutcome === 'uncertain' && !nextCheckAt.trim())" @click="role === 'operator' ? saveVerification() : emit('operate')">{{ role !== 'operator' ? '切换运维视角' : task.assignee ? '保存核查结果' : '确认领取' }}</button><button class="btn btn-outline-secondary" @click="closeDetail">关闭</button></div>
            </template>
            <template v-else><section class="detail-section"><h3>当前判断</h3><p class="alert alert-warning mb-0">{{ task.title }}。{{ scenario.receipt }}</p></section></template>
          </template>

          <template v-else-if="detailTab === 'version'">
            <section v-if="task.kind === 'version'" class="detail-section version-section"><h3>版本信息对比</h3><div class="version-compare"><article class="version-card old"><h4>原请求第 {{ task.originalVersion }} 版（已失败）</h4><dl><div><dt>请求编号</dt><dd>{{ scenario.requestId }}</dd></div><div><dt>版本号</dt><dd>v{{ task.originalVersion }}.0</dd></div><div><dt>请求时间</dt><dd>{{ scenario.occurredAt }}</dd></div><div><dt>处理结果</dt><dd class="text-red">{{ task.target }}</dd></div><div><dt>当前状态</dt><dd class="text-red">已拦截（禁止恢复）</dd></div></dl></article><article class="version-card latest"><h4>源系统第 {{ task.latestVersion }} 版（最新）</h4><dl><div><dt>来源系统</dt><dd>{{ scenario.sourceSystem }}</dd></div><div><dt>版本号</dt><dd>v{{ task.latestVersion }}.0</dd></div><div><dt>生成时间</dt><dd>2026-09-15 14:20:00</dd></div><div><dt>文件状态</dt><dd class="text-green">可获取</dd></div><div><dt>数据状态</dt><dd class="text-green">待新请求接入</dd></div></dl></article></div><div class="version-stop"><strong>禁止恢复原请求</strong><span>原请求第 {{ task.originalVersion }} 版已失败并被系统拦截，不允许恢复处理。请引导源系统按第 {{ task.latestVersion }} 版重新发起独立请求。</span></div></section>
            <section v-else class="detail-section"><h3>数据版本</h3><dl class="reference-facts"><div><dt>本次请求版本</dt><dd>v{{ task.originalVersion }}.0</dd></div><div><dt>来源最新版本</dt><dd>v{{ task.latestVersion }}.0</dd></div><div><dt>版本判断</dt><dd>{{ task.originalVersion === task.latestVersion ? '版本一致' : '版本不一致' }}</dd></div></dl></section>
          </template>

          <section v-else-if="detailTab === 'request'" class="detail-section"><h3>发送与响应证据</h3><dl class="reference-facts"><div><dt>已发送请求</dt><dd>{{ scenario.occurredAt }}</dd></div><div><dt>接收系统响应</dt><dd>{{ task.target }}</dd></div><div><dt>暂存数据</dt><dd>{{ task.dataStatus }}</dd></div><div><dt>暂存截止时间</dt><dd>{{ task.deadline }}</dd></div></dl><button class="btn btn-link px-0" @click="emit('exchange', scenario.id)">查看完整发送过程</button></section>
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
