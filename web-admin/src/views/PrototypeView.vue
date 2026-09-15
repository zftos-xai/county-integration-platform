<script setup lang="ts">
import {
  Activity, AlertTriangle, ArrowDownUp, ArrowLeftRight, ArrowUpRight, Bell,
  Building2, Check, ClipboardCheck, Database, FileSearch,
  Menu, Plus, RefreshCw, Search, Settings2, ShieldCheck, Trash2, X,
} from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { Component } from 'vue'
import { scenarios } from '../prototypeData'
import type { Scenario } from '../prototypeData'

type Role = 'manager' | 'operator'
type Page = 'overview' | 'organizations' | 'interfaces' | 'exchanges' | 'exceptions' | 'reconciliation' | 'alerts' | 'audit'
type Drawer = 'organization' | 'interface' | 'scenario' | 'reconciliation' | 'alert' | null
type NavItem = { key: Page; label: string; icon: Component; roles: readonly Role[] }
type NavGroup = { label: string; items: NavItem[] }
type Outcome = 'written' | 'not-written' | 'uncertain'
type Resolution = Outcome | 'revalidate' | 'blocked' | 'reconcile'
type Organization = {
  id: string
  name: string
  relation: string
  system: string
  externalCode: string
  status: '启用' | '停用'
}
type InterfaceStatus = '启用' | '停用'
type CodeMapping = { sourceCode: string; targetCode: string; description: string }
type InterfaceConfig = {
  id: string
  name: string
  domain: string
  direction: string
  version: string
  status: InterfaceStatus
  related: string
  mappings: CodeMapping[]
}
type ReconciliationTask = { id: string; date: string; organizationCode: string; interfaceCode: string; status: '待执行' | '执行中' | '有差异' | '无差异'; differenceIds: string[] }
type PlatformAlert = { id: string; level: '高' | '中' | '低'; type: string; object: string; occurredAt: string; status: '待确认' | '处理中' | '已恢复'; note: string }

const role = ref<Role>('manager')
const page = ref<Page>('overview')
const drawer = ref<Drawer>(null)
const drawerTab = ref<'overview' | 'work' | 'trace'>('overview')
const selectedId = ref('')
const menuOpen = ref(false)
const query = ref('')
const orgFilter = ref('全部机构')
const statusFilter = ref('全部状态')
const auditActorFilter = ref('全部主体')
const auditActionFilter = ref('全部动作')
const descending = ref(true)
const toast = ref('')
const claimed = ref<string[]>([])
const outcome = ref<Outcome>('uncertain')
const handlingAction = ref<Exclude<Resolution, Outcome>>('revalidate')
const evidence = ref('')
const note = ref('')
const findings = ref<Record<string, { outcome: Resolution; evidence: string; note: string }>>({})
const localEvents = ref<{ action: string; object: string; actor: string; time: string; detail?: string }[]>([])
const reconciliationTasks = ref<ReconciliationTask[]>([
  { id: 'SIM-RECON-20260914-01', date: '2026-09-14', organizationCode: 'SIM-ORG-001', interfaceCode: 'ACB666576D94A008', status: '有差异', differenceIds: ['DESIGN-01'] },
  { id: 'SIM-RECON-20260914-02', date: '2026-09-14', organizationCode: 'SIM-ORG-000', interfaceCode: '500-003', status: '有差异', differenceIds: ['DESIGN-02'] },
])
const reconciliationDraft = ref({ date: '2026-09-14', organizationCode: 'SIM-ORG-001', interfaceCode: 'ACB666576D94A008' })
const alerts = ref<PlatformAlert[]>([
  { id: 'SIM-ALERT-001', level: '高', type: '接口连续失败', object: '500-003', occurredAt: '2026-09-14 10:08:18', status: '待确认', note: '' },
  { id: 'SIM-ALERT-002', level: '中', type: '对账差异', object: 'SIM-RECON-20260914-01', occurredAt: '2026-09-14 11:10:00', status: '处理中', note: '正在核查目标端结果' },
  { id: 'SIM-ALERT-003', level: '低', type: '异常访问', object: 'SIM-REQ-20260914-0003', occurredAt: '2026-09-14 08:41:05', status: '已恢复', note: '越权请求已拒绝' },
])
const alertNote = ref('')
const persistenceReady = ref(false)
const storageKey = 'county-integration-prototype-v4'

const navGroups: NavGroup[] = [
  { label: '工作台', items: [
    { key: 'overview', label: '运行总览', icon: Activity, roles: ['manager', 'operator'] },
  ] },
  { label: '配置管理', items: [
    { key: 'organizations', label: '机构与权限', icon: Building2, roles: ['manager'] },
    { key: 'interfaces', label: '接口与映射', icon: Settings2, roles: ['manager'] },
  ] },
  { label: '运行管理', items: [
    { key: 'exchanges', label: '交换记录', icon: ArrowLeftRight, roles: ['manager', 'operator'] },
    { key: 'exceptions', label: '异常与处理', icon: AlertTriangle, roles: ['manager', 'operator'] },
    { key: 'reconciliation', label: '数据对账', icon: ClipboardCheck, roles: ['manager', 'operator'] },
    { key: 'alerts', label: '告警管理', icon: Bell, roles: ['manager', 'operator'] },
  ] },
  { label: '安全审计', items: [
    { key: 'audit', label: '审计记录', icon: FileSearch, roles: ['manager', 'operator'] },
  ] },
]
const nav = navGroups.flatMap(group => group.items)
const organizations = ref<Organization[]>([
  { id: 'SIM-ORG-000', name: '示例县人民医院', relation: '县级医院', system: 'HIS / LIS / PACS', externalCode: 'SIM-HOSP-000', status: '启用' },
  { id: 'SIM-ORG-001', name: '示例青禾镇卫生院', relation: '基层卫生院', system: '基层业务系统', externalCode: 'SIM-PRIMARY-001', status: '启用' },
  { id: 'SIM-ORG-099', name: '示例未授权机构', relation: '基层卫生院', system: '基层业务系统', externalCode: 'SIM-PRIMARY-099', status: '停用' },
])
const organizationInterfaceAccess = ref<Record<string, string[]>>({
  'SIM-ORG-000': ['500-003', '600-003'],
  'SIM-ORG-001': ['ACB666576D94A008', 'ACB666576D94A000'],
  'SIM-ORG-099': [],
})
const organizationDraft = ref<Organization>({ id: '', name: '', relation: '基层卫生院', system: '', externalCode: '', status: '启用' })
const organizationDraftInterfaces = ref<string[]>([])
const organizationChangeReason = ref('')
const isNewOrganization = ref(false)
const interfaces = ref<InterfaceConfig[]>([
  { id: '100-008', name: '机构范围查询', domain: '基础数据', direction: '基层系统 → 平台 → HIS', version: 'V1.0', status: '启用', related: 'DESIGN-03', mappings: [] },
  { id: 'ACB666576D94A008', name: '上传健康档案', domain: '健康档案', direction: '院内系统 → 平台 → 健康云', version: 'V1.0', status: '启用', related: 'DESIGN-01', mappings: [] },
  { id: 'ACB666576D94A000', name: '上传健康体检', domain: '老年人健康', direction: '院内系统 → 平台 → 健康云', version: 'V1.0', status: '启用', related: 'DESIGN-04', mappings: [] },
  { id: '500-003', name: 'PACS 报告回写', domain: '检查报告', direction: 'PACS → 平台 → HIS', version: 'V1.0', status: '启用', related: 'DESIGN-02', mappings: [] },
  { id: '600-003', name: 'LIS 报告回写', domain: '检验报告', direction: 'LIS → 平台 → HIS', version: 'V1.0', status: '启用', related: 'DESIGN-05', mappings: [] },
  { id: '700-003', name: '心电报告回写', domain: '心电报告', direction: '心电系统 → 平台 → HIS', version: 'V1.0', status: '停用', related: '', mappings: [] },
  { id: '400-003', name: '电子病历回写', domain: '电子病历', direction: 'EMR → 平台 → HIS', version: 'V1.0', status: '停用', related: '', mappings: [] },
])
const interfaceDraft = ref<InterfaceConfig | null>(null)
const interfaceChangeReason = ref('')
const interfaceHistory = ref<Record<string, { time: string; actor: string; summary: string; reason: string }[]>>({})
const interfaceStatuses: InterfaceStatus[] = ['启用', '停用']
const auditSeed = [
  { time: '09:27:14', actor: 'SIM-SVC-EHR', action: '发送超时，转人工核查', object: 'SIM-REQ-20260914-0018', detail: '结果未知' },
  { time: '10:08:18', actor: 'SIM-SVC-PACS', action: '报告版本校验未通过', object: 'SIM-REQ-20260914-0026', detail: '申请单版本 2 / 报告引用版本 1' },
  { time: '08:41:05', actor: 'SIM-SVC-ACCESS', action: '拒绝越权调用', object: 'SIM-REQ-20260914-0003', detail: '机构不在允许范围' },
]
const queueStatuses = ['结果未知', '校验未通过', '越权拒绝']
const visibleNavGroups = computed(() => navGroups
  .map(group => ({ ...group, items: group.items.filter(item => item.roles.includes(role.value)) }))
  .filter(group => group.items.length))
const visibleNav = computed(() => visibleNavGroups.value.flatMap(group => group.items))
const title = computed(() => nav.find(item => item.key === page.value)?.label ?? '运行总览')
const pageDescription = computed(() => ({
  overview: role.value === 'manager' ? '配置状态与运行事项' : '异常、对账与告警处置',
  organizations: '维护交换机构、编码对应和接口权限',
  interfaces: '维护接口版本、启停状态和编码对应',
  exchanges: '按业务单号追踪受理、发送和回执',
  exceptions: '领取异常，登记核查结果或转入对账',
  reconciliation: '核对业务日期内的交换差异',
  alerts: '确认运行告警并登记恢复结果',
  audit: '查询配置、核查、对账和告警操作',
}[page.value]))
const selectedScenario = computed(() => scenarios.find(item => item.id === selectedId.value))
const selectedInterface = computed(() => interfaces.value.find(item => item.id === selectedId.value))
const relatedScenario = computed(() => scenarios.find(item => item.id === selectedInterface.value?.related))
const selectedReconciliation = computed(() => reconciliationTasks.value.find(item => item.id === selectedId.value))
const selectedAlert = computed(() => alerts.value.find(item => item.id === selectedId.value))
const drawerTitle = computed(() => {
  if (drawer.value === 'scenario') return selectedScenario.value?.sourceRecordId ?? '交换详情'
  if (drawer.value === 'interface') return selectedInterface.value?.name ?? '接口配置'
  if (drawer.value === 'reconciliation') return selectedReconciliation.value?.id ?? '新建对账任务'
  if (drawer.value === 'alert') return selectedAlert.value?.type ?? '告警详情'
  return organizationDraft.value.name || '新增机构'
})
const rows = computed(() => scenarios
  .filter(item => orgFilter.value === '全部机构' || item.organizationCode === orgFilter.value)
  .filter(item => statusFilter.value === '全部状态' || item.status === statusFilter.value)
  .filter(item => `${item.sourceRecordId} ${item.requestId} ${item.organization} ${item.interfaceCode} ${item.title}`.toLowerCase().includes(query.value.trim().toLowerCase()))
  .sort((a, b) => descending.value ? b.occurredAt.localeCompare(a.occurredAt) : a.occurredAt.localeCompare(b.occurredAt)))
const queue = computed(() => rows.value.filter(item => queueStatuses.includes(item.status)))
const interfaceRows = computed(() => interfaces.value.filter(item => `${item.id} ${item.name} ${item.domain} ${item.status}`.toLowerCase().includes(query.value.trim().toLowerCase())))
const organizationRows = computed(() => organizations.value.filter(item => `${item.id} ${item.name} ${item.externalCode}`.toLowerCase().includes(query.value.trim().toLowerCase())))
const canSaveFinding = computed(() => role.value === 'operator' && selectedScenario.value && claimed.value.includes(selectedId.value)
  && note.value.trim() && (selectedScenario.value.status !== '结果未知' || outcome.value === 'uncertain' || evidence.value.trim()))
const auditRows = computed(() => [
  ...localEvents.value,
  ...auditSeed.map(item => ({ ...item, time: `2026-09-14 ${item.time}` })),
].filter(item => auditActorFilter.value === '全部主体' || (auditActorFilter.value === '系统事件' ? item.actor.startsWith('SIM-SVC') : !item.actor.startsWith('SIM-SVC')))
  .filter(item => auditActionFilter.value === '全部动作' || item.action.includes(auditActionFilter.value))
  .filter(item => `${item.actor} ${item.action} ${item.object} ${item.detail ?? ''}`.toLowerCase().includes(query.value.trim().toLowerCase())))

function navigate(next: Page) {
  page.value = next
  drawer.value = null
  menuOpen.value = false
  query.value = ''
  statusFilter.value = '全部状态'
  auditActorFilter.value = '全部主体'
  auditActionFilter.value = '全部动作'
}
function switchRole(next: Role) {
  if (role.value === next) return
  role.value = next
  if (!visibleNav.value.some(item => item.key === page.value)) navigate('overview')
  drawer.value = null
  toast.value = `已切换至${next === 'manager' ? '管理' : '运维'}视角`
}
function open(kind: Exclude<Drawer, null>, id: string, tab: 'overview' | 'work' | 'trace' = 'overview') {
  drawer.value = kind
  selectedId.value = id
  drawerTab.value = tab
  outcome.value = 'uncertain'
  handlingAction.value = 'revalidate'
  evidence.value = ''
  note.value = ''
  alertNote.value = ''
}
function editInterface(item: InterfaceConfig, tab: 'overview' | 'work' | 'trace' = 'overview') {
  interfaceDraft.value = { ...item, mappings: item.mappings.map(mapping => ({ ...mapping })) }
  interfaceChangeReason.value = ''
  open('interface', item.id, tab)
}
function addCodeMapping() {
  if (role.value !== 'manager') return
  interfaceDraft.value?.mappings.push({ sourceCode: '', targetCode: '', description: '' })
}
function createReconciliation() {
  if (role.value !== 'operator') return
  const id = `SIM-RECON-${reconciliationDraft.value.date.replaceAll('-', '')}-${String(reconciliationTasks.value.length + 1).padStart(2, '0')}`
  reconciliationTasks.value.unshift({ id, ...reconciliationDraft.value, status: '待执行', differenceIds: [] })
  record('创建对账任务', id, `${reconciliationDraft.value.organizationCode} · ${reconciliationDraft.value.interfaceCode}`)
  toast.value = '对账任务已创建'
  drawer.value = null
}
function runReconciliation(task: ReconciliationTask) {
  if (role.value !== 'operator') return
  task.status = '执行中'
  const matches = scenarios.filter(item => item.organizationCode === task.organizationCode && item.interfaceCode === task.interfaceCode && queueStatuses.includes(item.status))
  task.differenceIds = matches.map(item => item.id)
  task.status = matches.length ? '有差异' : '无差异'
  record('执行对账任务', task.id, `${matches.length} 项差异`)
  toast.value = `对账完成，发现 ${matches.length} 项差异`
}
function updateAlert(status: PlatformAlert['status']) {
  if (role.value !== 'operator' || !selectedAlert.value || (status !== '待确认' && !alertNote.value.trim())) return
  selectedAlert.value.status = status
  if (alertNote.value.trim()) selectedAlert.value.note = alertNote.value.trim()
  record(status === '处理中' ? '确认告警' : '登记告警恢复', selectedAlert.value.id, selectedAlert.value.note)
  toast.value = status === '处理中' ? '告警已确认' : '告警已登记恢复'
  alertNote.value = ''
}
function resetPrototype() {
  localStorage.removeItem(storageKey)
  window.location.reload()
}
function removeCodeMapping(index: number) {
  if (role.value !== 'manager') return
  interfaceDraft.value?.mappings.splice(index, 1)
}
function saveInterface() {
  if (role.value !== 'manager' || !interfaceDraft.value || !interfaceChangeReason.value.trim()) return
  const index = interfaces.value.findIndex(item => item.id === selectedId.value)
  if (index < 0) return
  const invalidMapping = interfaceDraft.value.mappings.some(item => !item.sourceCode.trim() || !item.targetCode.trim())
  if (invalidMapping) { toast.value = '编码映射的源编码和目标编码不能为空'; return }
  const saved = { ...interfaceDraft.value, mappings: interfaceDraft.value.mappings.map(item => ({ ...item })) }
  interfaces.value[index] = saved
  const summary = `状态：${saved.status}；版本：${saved.version || '待确认'}；编码映射：${saved.mappings.length} 项`
  const history = interfaceHistory.value[saved.id] ?? []
  interfaceHistory.value[saved.id] = [{ time: now(), actor: '平台管理', summary, reason: interfaceChangeReason.value.trim() }, ...history]
  record('修改接口配置', saved.id, summary)
  interfaceChangeReason.value = ''
  toast.value = '接口配置已保存'
}
function editOrganization(item: Organization, tab: 'overview' | 'work' | 'trace' = 'overview') {
  organizationDraft.value = { ...item }
  organizationDraftInterfaces.value = [...(organizationInterfaceAccess.value[item.id] ?? [])]
  isNewOrganization.value = false
  organizationChangeReason.value = ''
  open('organization', item.id, tab)
}
function addOrganization() {
  if (role.value !== 'manager') return
  organizationDraft.value = { id: '', name: '', relation: '基层卫生院', system: '', externalCode: '', status: '启用' }
  organizationDraftInterfaces.value = []
  isNewOrganization.value = true
  organizationChangeReason.value = ''
  open('organization', '__new__')
}
function toggleOrganizationInterface(interfaceCode: string) {
  if (role.value !== 'manager') return
  const current = organizationDraftInterfaces.value
  organizationDraftInterfaces.value = current.includes(interfaceCode)
    ? current.filter(item => item !== interfaceCode)
    : [...current, interfaceCode]
}
function saveOrganization() {
  if (role.value !== 'manager') return
  const draft = { ...organizationDraft.value, id: organizationDraft.value.id.trim(), name: organizationDraft.value.name.trim(), externalCode: organizationDraft.value.externalCode.trim(), system: organizationDraft.value.system.trim() }
  if (!draft.id || !draft.name || !draft.externalCode || !draft.system || !organizationChangeReason.value.trim()) return
  const duplicate = organizations.value.some(item => item.id === draft.id && item.id !== selectedId.value)
  if (duplicate) { toast.value = '机构代码已存在'; return }
  const oldId = selectedId.value
  if (isNewOrganization.value) organizations.value.push(draft)
  else {
    const index = organizations.value.findIndex(item => item.id === oldId)
    if (index >= 0) organizations.value[index] = draft
    if (oldId !== draft.id) delete organizationInterfaceAccess.value[oldId]
  }
  organizationInterfaceAccess.value[draft.id] = [...organizationDraftInterfaces.value]
  selectedId.value = draft.id
  isNewOrganization.value = false
  record(oldId === '__new__' ? '新增机构配置' : '修改机构配置', draft.id, organizationChangeReason.value.trim())
  organizationChangeReason.value = ''
  toast.value = '机构配置已保存'
}
function openScenario(item: Scenario, tab: 'overview' | 'work' | 'trace' = 'overview') {
  open('scenario', item.id, tab)
}
function now() {
  return new Intl.DateTimeFormat('sv-SE', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date())
}
function record(action: string, object: string, detail = '') {
  localEvents.value.unshift({
    time: now(),
    actor: role.value === 'manager' ? '平台管理' : '平台运维',
    action, object, detail,
  })
}
function claim() {
  if (role.value !== 'operator' || !selectedScenario.value || claimed.value.includes(selectedId.value)) return
  claimed.value.push(selectedId.value)
  record('领取异常', selectedScenario.value.requestId)
  toast.value = '异常已领取'
}
function saveFinding() {
  if (!canSaveFinding.value || !selectedScenario.value) return
  findings.value[selectedId.value] = {
    outcome: selectedScenario.value.status === '结果未知' ? outcome.value : handlingAction.value,
    evidence: evidence.value.trim(),
    note: note.value.trim(),
  }
  if (selectedScenario.value.status !== '结果未知' && handlingAction.value === 'reconcile') {
    const exists = reconciliationTasks.value.some(task => task.differenceIds.includes(selectedScenario.value!.id))
    if (!exists) reconciliationTasks.value.unshift({ id: `SIM-RECON-${selectedScenario.value.occurredAt.slice(0, 10).replaceAll('-', '')}-${String(reconciliationTasks.value.length + 1).padStart(2, '0')}`, date: selectedScenario.value.occurredAt.slice(0, 10), organizationCode: selectedScenario.value.organizationCode, interfaceCode: selectedScenario.value.interfaceCode, status: '有差异', differenceIds: [selectedScenario.value.id] })
  }
  record('登记核查', selectedScenario.value.requestId, outcomeLabel(findings.value[selectedId.value].outcome))
  toast.value = '核查已记录，未改变目标业务状态'
  note.value = ''
  evidence.value = ''
}
function showRelated(id: string) {
  const item = scenarios.find(candidate => candidate.id === id)
  if (!item) return
  navigate('exchanges')
  openScenario(item)
}
function statusClass(status: string) {
  return status === '处理成功' ? 'success' : status === '越权拒绝' ? 'danger'
    : status === '已受理' || status === '重复已识别' ? 'neutral' : 'warning'
}
function interfaceStatusClass(status: InterfaceStatus) {
  return status === '启用' ? 'success' : 'danger'
}
function organizationName(code: string) {
  return organizations.value.find(item => item.id === code)?.name ?? code
}
function outcomeLabel(value: Resolution) {
  return { written: '目标已写入', 'not-written': '明确未写入', uncertain: '仍无法确认', revalidate: '重新校验', blocked: '保持拦截', reconcile: '转入对账' }[value]
}
function handlingStatus(id: string) {
  if (findings.value[id]) return findings.value[id].outcome === 'uncertain' ? '持续核查' : '已登记结论'
  return claimed.value.includes(id) ? '处理中' : '待领取'
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { drawer.value = null; menuOpen.value = false }
}
onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) ?? 'null')
    if (saved) {
      if (Array.isArray(saved.organizations)) organizations.value = saved.organizations
      if (saved.organizationInterfaceAccess) organizationInterfaceAccess.value = saved.organizationInterfaceAccess
      if (Array.isArray(saved.interfaces)) interfaces.value = saved.interfaces
      if (Array.isArray(saved.claimed)) claimed.value = saved.claimed
      if (saved.findings) findings.value = saved.findings
      if (Array.isArray(saved.localEvents)) localEvents.value = saved.localEvents
      if (Array.isArray(saved.reconciliationTasks)) reconciliationTasks.value = saved.reconciliationTasks
      if (Array.isArray(saved.alerts)) alerts.value = saved.alerts
      if (saved.interfaceHistory) interfaceHistory.value = saved.interfaceHistory
    }
  } catch {
    localStorage.removeItem(storageKey)
  }
  persistenceReady.value = true
})
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
watch([organizations, organizationInterfaceAccess, interfaces, claimed, findings, localEvents, reconciliationTasks, alerts, interfaceHistory], () => {
  if (!persistenceReady.value) return
  localStorage.setItem(storageKey, JSON.stringify({ organizations: organizations.value, organizationInterfaceAccess: organizationInterfaceAccess.value, interfaces: interfaces.value, claimed: claimed.value, findings: findings.value, localEvents: localEvents.value, reconciliationTasks: reconciliationTasks.value, alerts: alerts.value, interfaceHistory: interfaceHistory.value }))
}, { deep: true })
</script>

<template>
  <div class="prototype-app">
    <aside class="prototype-sidebar" :class="{ open: menuOpen }">
      <div class="prototype-brand">
        <span class="prototype-logo"><Database :size="20" /></span>
        <span><strong>县域接口平台</strong><small>集成运行管理</small></span>
        <button class="prototype-icon prototype-close" aria-label="关闭导航" title="关闭导航" @click="menuOpen = false"><X :size="18" /></button>
      </div>
      <nav class="prototype-nav" aria-label="功能导航">
        <div v-for="group in visibleNavGroups" :key="group.label" class="prototype-nav-group">
          <span class="prototype-nav-label">{{ group.label }}</span>
          <button v-for="item in group.items" :key="item.key" :class="{ active: page === item.key }" @click="navigate(item.key as Page)">
            <component :is="item.icon" :size="17" /><span>{{ item.label }}</span>
          </button>
        </div>
      </nav>
      <div class="prototype-sidebar-foot">
        <span><ShieldCheck :size="16" />本地业务原型</span>
        <button class="prototype-icon" aria-label="重置原型数据" title="重置原型数据" @click="resetPrototype"><RefreshCw :size="15" /></button>
      </div>
    </aside>
    <div v-if="menuOpen" class="prototype-scrim" @click="menuOpen = false"></div>

    <main class="prototype-main">
      <header class="prototype-topbar">
        <button class="prototype-icon prototype-menu" aria-label="打开导航" title="打开导航" @click="menuOpen = true"><Menu :size="20" /></button>
        <div class="prototype-heading"><span>{{ role === 'manager' ? '管理工作台' : '运维工作台' }} · {{ pageDescription }}</span><h1>{{ title }}</h1></div>
        <div class="prototype-top-meta">
          <span class="prototype-demo">业务原型 · 合成数据</span>
          <div class="prototype-role-switch">
            <span>角色视角</span>
            <div class="prototype-segment" aria-label="切换角色">
              <button :class="{ active: role === 'manager' }" :aria-pressed="role === 'manager'" @click="switchRole('manager')">管理</button>
              <button :class="{ active: role === 'operator' }" :aria-pressed="role === 'operator'" @click="switchRole('operator')">运维</button>
            </div>
          </div>
        </div>
      </header>
      <div class="prototype-content">
        <div v-if="toast" class="work-toast" role="status"><Check :size="16" />{{ toast }}<button class="prototype-icon" aria-label="关闭提示" @click="toast = ''"><X :size="15" /></button></div>

        <template v-if="page === 'overview'">
          <div class="work-overview-tools"><span>统计日期 2026-09-14</span><button class="work-quiet-button" @click="toast = '页面数据已刷新'"><RefreshCw :size="15" />刷新</button></div>
          <div class="prototype-metrics">
            <button @click="navigate('exchanges')"><span>交换记录</span><strong>{{ scenarios.length }}</strong><small>查看全部 <ArrowUpRight :size="13" /></small></button>
            <button @click="navigate('exceptions')"><span>待核查</span><strong>{{ scenarios.filter(item => item.status === '结果未知' || item.status === '校验未通过').length }}</strong><small>进入异常队列 <ArrowUpRight :size="13" /></small></button>
            <button @click="navigate('reconciliation')"><span>对账差异</span><strong>{{ reconciliationTasks.reduce((total, item) => total + item.differenceIds.length, 0) }}</strong><small>查看差异 <ArrowUpRight :size="13" /></small></button>
            <button v-if="role === 'manager'" @click="navigate('interfaces')"><span>已启用接口</span><strong>{{ interfaces.filter(item => item.status === '启用').length }}</strong><small>查看接口配置 <ArrowUpRight :size="13" /></small></button><button v-else @click="navigate('alerts')"><span>待处理告警</span><strong>{{ alerts.filter(item => item.status !== '已恢复').length }}</strong><small>进入告警队列 <ArrowUpRight :size="13" /></small></button>
          </div>
          <div class="work-overview-grid">
            <section class="prototype-section">
              <div class="prototype-section-head"><h3>待处理事项</h3><button class="prototype-text-button" @click="navigate('exceptions')">查看全部</button></div>
              <div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>业务单号</th><th>机构 / 接口</th><th>状态</th><th>时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in scenarios.filter(row => queueStatuses.includes(row.status))" :key="item.id"><td><button class="work-row-link" @click="openScenario(item, 'work')">{{ item.sourceRecordId }}</button><small>{{ item.domain }}</small></td><td>{{ item.organization }}<small>{{ item.interfaceCode }}</small></td><td><span class="prototype-tag" :class="statusClass(item.status)">{{ item.status }}</span></td><td>{{ item.occurredAt.slice(11, 16) }}</td><td><button class="prototype-text-button" @click="openScenario(item, 'work')">{{ role === 'operator' ? '处理' : '详情' }}</button></td></tr></tbody></table></div>
            </section>
            <section class="prototype-section work-side-panel">
              <div class="prototype-section-head"><h3>{{ role === 'manager' ? '接口清单' : '近期交换' }}</h3><button class="prototype-text-button" @click="navigate(role === 'manager' ? 'interfaces' : 'exchanges')">查看全部</button></div>
              <div v-if="role === 'manager'" class="work-compact-list"><button v-for="item in interfaces.slice(1, 5)" :key="item.id" @click="editInterface(item)"><span><strong>{{ item.name }}</strong><small>{{ item.id }}</small></span><span class="prototype-tag" :class="interfaceStatusClass(item.status)">{{ item.status }}</span></button></div>
              <div v-else class="work-compact-list"><button v-for="item in scenarios.slice(-3).reverse()" :key="item.id" @click="openScenario(item)"><span><strong>{{ item.sourceRecordId }}</strong><small>{{ item.domain }} · {{ item.occurredAt.slice(11, 16) }}</small></span><span class="prototype-tag" :class="statusClass(item.status)">{{ item.status }}</span></button></div>
            </section>
          </div>
        </template>

        <template v-else-if="page === 'organizations'">
          <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索机构" placeholder="机构名称、平台代码或外部代码" /></label><span>{{ organizationRows.length }} 个机构</span><button class="prototype-button" @click="addOrganization"><Plus :size="15" />新增机构</button></div>
          <section class="prototype-section work-table-section"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>机构名称</th><th>平台机构代码</th><th>外部机构代码</th><th>机构类型</th><th>允许接口</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in organizationRows" :key="item.id"><td><button class="work-row-link" @click="editOrganization(item)">{{ item.name }}</button><small>{{ item.system }}</small></td><td>{{ item.id }}</td><td>{{ item.externalCode }}</td><td>{{ item.relation }}</td><td>{{ organizationInterfaceAccess[item.id]?.length ?? 0 }} 项</td><td><span class="prototype-tag" :class="item.status === '启用' ? 'success' : 'danger'">{{ item.status }}</span></td><td><button class="prototype-text-button" @click="editOrganization(item)">配置</button></td></tr></tbody></table><div v-if="!organizationRows.length" class="prototype-empty">未找到机构</div></div></section>
        </template>

        <template v-else-if="page === 'interfaces'">
          <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索接口" placeholder="接口名称、编号或业务域" /></label><span>{{ interfaceRows.length }} 项接口</span><span class="prototype-tag success">启用 {{ interfaces.filter(item => item.status === '启用').length }}</span></div>
          <section class="prototype-section work-table-section"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>接口编号 / 名称</th><th>业务域</th><th>交换方向</th><th>版本</th><th>状态</th><th>编码映射</th><th>操作</th></tr></thead><tbody><tr v-for="item in interfaceRows" :key="item.id"><td><button class="work-row-link" @click="editInterface(item)">{{ item.id }}</button><small>{{ item.name }}</small></td><td>{{ item.domain }}</td><td>{{ item.direction }}</td><td>{{ item.version }}</td><td><span class="prototype-tag" :class="interfaceStatusClass(item.status)">{{ item.status }}</span></td><td>{{ item.mappings.length }} 项</td><td><button class="prototype-text-button" @click="editInterface(item)">配置</button></td></tr></tbody></table><div v-if="!interfaceRows.length" class="prototype-empty">未找到接口</div></div></section>
        </template>

        <template v-else-if="page === 'exchanges' || page === 'exceptions'">
          <div class="work-toolbar">
            <label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索交换记录" placeholder="业务单号、请求编号或接口" /></label>
            <select v-model="orgFilter" aria-label="机构筛选"><option value="全部机构">全部机构</option><option v-for="item in organizations" :key="item.id" :value="item.id">{{ item.name }}</option></select>
            <select v-model="statusFilter" aria-label="状态筛选"><option>全部状态</option><option v-for="state in ['结果未知', '校验未通过', '越权拒绝', '已受理', '处理成功', '重复已识别']" :key="state">{{ state }}</option></select>
            <span>{{ page === 'exceptions' ? queue.length : rows.length }} 笔</span><button class="work-quiet-button" @click="descending = !descending"><ArrowDownUp :size="15" />{{ descending ? '最新优先' : '最早优先' }}</button>
          </div>
          <section class="prototype-section work-table-section"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>业务单号 / 请求编号</th><th>机构</th><th>接口 / 业务域</th><th>受理时间</th><th>业务状态</th><th v-if="page === 'exceptions'">处置状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in page === 'exceptions' ? queue : rows" :key="item.id"><td><button class="work-row-link" @click="openScenario(item)">{{ item.sourceRecordId }}</button><small>{{ item.requestId }}</small></td><td>{{ item.organization }}</td><td>{{ item.interfaceCode }}<small>{{ item.domain }}</small></td><td>{{ item.occurredAt }}</td><td><span class="prototype-tag" :class="statusClass(item.status)">{{ item.status }}</span></td><td v-if="page === 'exceptions'">{{ handlingStatus(item.id) }}</td><td><button class="prototype-text-button" @click="openScenario(item, page === 'exceptions' ? 'work' : 'overview')">{{ page === 'exceptions' && role === 'operator' ? '处理' : '详情' }}</button></td></tr></tbody></table><div v-if="!(page === 'exceptions' ? queue : rows).length" class="prototype-empty">没有符合条件的记录</div></div></section>
        </template>

        <template v-else-if="page === 'reconciliation'">
          <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索对账任务" placeholder="任务编号、机构或接口" /></label><span>{{ reconciliationTasks.length }} 个任务</span><button v-if="role === 'operator'" class="prototype-button" @click="open('reconciliation', '__new__')"><Plus :size="15" />新建对账</button></div>
          <section class="prototype-section work-table-section"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>任务编号</th><th>业务日期</th><th>机构</th><th>接口</th><th>状态</th><th>差异</th><th>操作</th></tr></thead><tbody><tr v-for="task in reconciliationTasks.filter(item => `${item.id} ${item.organizationCode} ${item.interfaceCode}`.toLowerCase().includes(query.toLowerCase()))" :key="task.id"><td><button class="work-row-link" @click="open('reconciliation', task.id)">{{ task.id }}</button></td><td>{{ task.date }}</td><td>{{ organizations.find(item => item.id === task.organizationCode)?.name ?? task.organizationCode }}</td><td>{{ task.interfaceCode }}</td><td><span class="prototype-tag" :class="task.status === '无差异' ? 'success' : task.status === '有差异' ? 'warning' : 'neutral'">{{ task.status }}</span></td><td>{{ task.differenceIds.length }} 项</td><td><button v-if="role === 'operator' && task.status === '待执行'" class="prototype-text-button" @click="runReconciliation(task)">执行</button><button v-else class="prototype-text-button" @click="open('reconciliation', task.id)">详情</button></td></tr></tbody></table></div></section>
        </template>

        <template v-else-if="page === 'alerts'">
          <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索告警" placeholder="告警编号、类型或对象" /></label><span>{{ alerts.length }} 条告警</span><span class="prototype-tag warning">待确认 {{ alerts.filter(item => item.status === '待确认').length }}</span></div>
          <section class="prototype-section work-table-section"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>级别</th><th>告警类型</th><th>对象</th><th>发生时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in alerts.filter(row => `${row.id} ${row.type} ${row.object}`.toLowerCase().includes(query.toLowerCase()))" :key="item.id"><td><span class="prototype-tag" :class="item.level === '高' ? 'danger' : item.level === '中' ? 'warning' : 'neutral'">{{ item.level }}</span></td><td><button class="work-row-link" @click="open('alert', item.id)">{{ item.type }}</button><small>{{ item.id }}</small></td><td>{{ item.object }}</td><td>{{ item.occurredAt }}</td><td>{{ item.status }}</td><td><button class="prototype-text-button" @click="open('alert', item.id)">{{ role === 'operator' && item.status !== '已恢复' ? '处理' : '详情' }}</button></td></tr></tbody></table></div></section>
        </template>

        <template v-else-if="page === 'audit'">
          <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索审计记录" placeholder="操作者、动作、对象或变更内容" /></label><select v-model="auditActorFilter" aria-label="主体筛选"><option>全部主体</option><option>系统事件</option><option>人工操作</option></select><select v-model="auditActionFilter" aria-label="动作筛选"><option>全部动作</option><option>配置</option><option>核查</option><option>对账</option><option>告警</option></select><span>{{ auditRows.length }} 条</span></div>
          <section class="prototype-section work-table-section"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>时间</th><th>操作主体</th><th>动作</th><th>对象</th><th>变更内容</th></tr></thead><tbody><tr v-for="(item, index) in auditRows" :key="`${item.object}-${index}`"><td>{{ item.time }}</td><td>{{ item.actor }}</td><td>{{ item.action }}</td><td>{{ item.object }}</td><td>{{ item.detail || '—' }}</td></tr></tbody></table><div v-if="!auditRows.length" class="prototype-empty">没有符合条件的审计记录</div></div></section>
        </template>
      </div>
    </main>

    <div v-if="drawer" class="work-drawer-backdrop" @click="drawer = null"></div>
    <aside v-if="drawer" class="work-drawer" role="dialog" aria-modal="true" aria-label="业务详情">
      <div class="work-drawer-header">
        <div><small>{{ drawer === 'scenario' ? '交换记录' : drawer === 'interface' ? '接口管理' : drawer === 'reconciliation' ? '数据对账' : drawer === 'alert' ? '告警管理' : '机构管理' }}</small><h2>{{ drawerTitle }}</h2></div>
        <button class="prototype-icon" aria-label="关闭详情" title="关闭详情" @click="drawer = null"><X :size="19" /></button>
      </div>

      <template v-if="drawer === 'scenario' && selectedScenario">
        <div class="work-drawer-sub"><span class="prototype-tag" :class="statusClass(selectedScenario.status)">{{ selectedScenario.status }}</span><span>{{ selectedScenario.domain }} · {{ selectedScenario.interfaceCode }}</span></div>
        <div class="work-tabs"><button v-for="tab in [{ key: 'overview', label: '业务详情' }, { key: 'work', label: '异常处置' }, { key: 'trace', label: '处理轨迹' }]" :key="tab.key" :class="{ active: drawerTab === tab.key }" @click="drawerTab = tab.key as typeof drawerTab">{{ tab.label }}</button></div>
        <div class="work-drawer-body">
          <template v-if="drawerTab === 'overview'">
            <dl class="work-facts"><div><dt>机构</dt><dd>{{ selectedScenario.organization }}<small>{{ selectedScenario.organizationCode }}</small></dd></div><div><dt>业务类型</dt><dd>{{ selectedScenario.domain }}</dd></div><div><dt>源系统 / 目标系统</dt><dd>{{ selectedScenario.sourceSystem }} → {{ selectedScenario.targetSystem }}</dd></div><div><dt>源业务单号</dt><dd>{{ selectedScenario.sourceRecordId }}</dd></div><div><dt>患者关联引用</dt><dd>{{ selectedScenario.patientRef }}</dd></div><div><dt>请求编号</dt><dd>{{ selectedScenario.requestId }}</dd></div><div><dt>受理时间</dt><dd>{{ selectedScenario.occurredAt }}</dd></div><div><dt>回执 / 拦截原因</dt><dd>{{ selectedScenario.receipt }}</dd></div></dl>
            <div class="work-inline-actions"><button class="prototype-button" @click="drawerTab = 'trace'">查看处理轨迹</button><button v-if="queueStatuses.includes(selectedScenario.status)" class="work-quiet-button" @click="drawerTab = 'work'">进入处置</button></div>
          </template>
          <template v-else-if="drawerTab === 'trace'">
            <div class="work-section-label">状态时间线</div>
            <ol class="work-timeline"><li v-for="(step, index) in selectedScenario.steps" :key="step"><span>{{ index + 1 }}</span><p>{{ step }}</p></li></ol>
            <div class="work-inline-note">请求编号 {{ selectedScenario.requestId }} · 合成记录</div>
          </template>
          <template v-else>
            <div class="work-section-label">处置记录</div>
            <div v-if="findings[selectedScenario.id]" class="work-finding"><Check :size="16" /><span>{{ outcomeLabel(findings[selectedScenario.id].outcome) }} · {{ findings[selectedScenario.id].note }}<small v-if="findings[selectedScenario.id].evidence">证据引用：{{ findings[selectedScenario.id].evidence }}</small></span></div>
            <div v-if="!queueStatuses.includes(selectedScenario.status)" class="work-inline-note">当前记录无需人工异常处置，可查看回执与轨迹。</div>
            <template v-else-if="role === 'manager'"><div class="work-inline-note">管理角色只读。异常领取与核查由运维角色办理。</div></template>
            <template v-else>
              <div class="work-assignee"><span>处理人</span><strong>{{ claimed.includes(selectedScenario.id) ? '当前运维' : '未领取' }}</strong><button v-if="!claimed.includes(selectedScenario.id)" class="prototype-button" @click="claim">领取</button></div>
              <div v-if="claimed.includes(selectedScenario.id)" class="work-form">
                <template v-if="selectedScenario.status === '结果未知'">
                  <label>目标端核查结果</label>
                  <div class="work-choice-group" role="group" aria-label="目标端核查结果"><button v-for="choice in [{ value: 'written', label: '已写入' }, { value: 'not-written', label: '未写入' }, { value: 'uncertain', label: '无法确认' }]" :key="choice.value" :class="{ active: outcome === choice.value }" :aria-pressed="outcome === choice.value" @click="outcome = choice.value as Outcome">{{ choice.label }}</button></div>
                  <div class="work-inline-note">{{ outcome === 'written' ? '记录回执并对账；不可重复写入。' : outcome === 'not-written' ? '记录查询结果，进入后续补偿处理。' : '保持人工核查，不关闭差异或重复写入。' }}</div>
                  <label for="work-evidence">查询依据{{ outcome === 'uncertain' ? '（可选）' : '（必填）' }}</label><input id="work-evidence" v-model="evidence" maxlength="80" placeholder="填写回执编号或日志编号" />
                </template>
                <template v-else>
                  <label>处置动作</label><div class="work-choice-group" role="group" aria-label="处置动作"><button :class="{ active: handlingAction === 'revalidate' }" @click="handlingAction = 'revalidate'">重新校验</button><button :class="{ active: handlingAction === 'blocked' }" @click="handlingAction = 'blocked'">保持拦截</button><button :class="{ active: handlingAction === 'reconcile' }" @click="handlingAction = 'reconcile'">转入对账</button></div>
                  <div class="work-inline-note">重新校验只执行平台校验，不直接向目标系统再次写入。</div>
                </template>
                <label for="work-note">核查备注（必填）</label><textarea id="work-note" v-model="note" maxlength="200" rows="4" placeholder="说明核查对象、结果及后续处理"></textarea>
                <button class="prototype-button" :disabled="!canSaveFinding" @click="saveFinding">保存核查结果</button>
              </div>
            </template>
          </template>
        </div>
      </template>

      <template v-else-if="drawer === 'interface' && selectedInterface && interfaceDraft">
        <div class="work-drawer-sub"><span class="prototype-tag" :class="interfaceStatusClass(interfaceDraft.status)">{{ interfaceDraft.status }}</span><span>{{ interfaceDraft.id }} · {{ interfaceDraft.domain }}</span></div>
        <div class="work-tabs"><button v-for="tab in [{ key: 'overview', label: '接口配置' }, { key: 'work', label: '编码映射' }, { key: 'trace', label: '变更记录' }]" :key="tab.key" :class="{ active: drawerTab === tab.key }" @click="drawerTab = tab.key as typeof drawerTab">{{ tab.label }}</button></div>
        <div class="work-drawer-body">
          <template v-if="drawerTab === 'overview'">
            <div class="work-form work-form-compact">
              <label for="intf-name">接口名称</label><input id="intf-name" v-model="interfaceDraft.name" maxlength="80" />
              <label for="intf-version">配置版本</label><input id="intf-version" v-model="interfaceDraft.version" maxlength="30" />
              <label>接口状态</label><div class="work-choice-group" role="group" aria-label="接口状态"><button v-for="state in interfaceStatuses" :key="state" :class="{ active: interfaceDraft.status === state }" @click="interfaceDraft.status = state">{{ state }}</button></div>
            </div>
          </template>
          <template v-else-if="drawerTab === 'work'">
            <div class="prototype-section-head work-embedded-head"><h3>编码对应</h3><button class="work-quiet-button" @click="addCodeMapping"><Plus :size="14" />新增对应</button></div>
            <div v-if="!interfaceDraft.mappings.length" class="prototype-empty">暂无编码对应</div>
            <div v-for="(mapping, index) in interfaceDraft.mappings" :key="index" class="work-mapping-row">
              <input v-model="mapping.sourceCode" aria-label="源编码" placeholder="源编码" /><span>→</span><input v-model="mapping.targetCode" aria-label="目标编码" placeholder="目标编码" /><input v-model="mapping.description" aria-label="编码说明" placeholder="说明（可选）" />
              <button class="prototype-icon" aria-label="删除编码对应" title="删除" @click="removeCodeMapping(index)"><Trash2 :size="15" /></button>
            </div>
          </template>
          <template v-else>
            <div class="work-section-label">配置变更</div>
            <div v-if="!interfaceHistory[interfaceDraft.id]?.length" class="prototype-empty">暂无本地变更记录</div>
            <ol v-else class="work-timeline"><li v-for="item in interfaceHistory[interfaceDraft.id]" :key="item.time"><span><Check :size="13" /></span><p><strong>{{ item.summary }}</strong><small>{{ item.time }} · {{ item.actor }} · {{ item.reason }}</small></p></li></ol>
            <div v-if="relatedScenario" class="work-section-label work-spaced-label">关联交换</div><button v-if="relatedScenario" class="work-related-link" @click="showRelated(relatedScenario.id)">{{ relatedScenario.sourceRecordId }}<ArrowUpRight :size="15" /></button>
          </template>
          <div v-if="drawerTab !== 'trace'" class="work-save-area"><label for="intf-reason">变更依据（必填）</label><textarea id="intf-reason" v-model="interfaceChangeReason" maxlength="200" rows="3" placeholder="说明本次配置变更依据"></textarea><button class="prototype-button" :disabled="!interfaceChangeReason.trim()" @click="saveInterface">保存接口配置</button></div>
        </div>
      </template>

      <template v-else-if="drawer === 'reconciliation'">
        <div v-if="selectedReconciliation" class="work-drawer-sub"><span class="prototype-tag" :class="selectedReconciliation.status === '无差异' ? 'success' : selectedReconciliation.status === '有差异' ? 'warning' : 'neutral'">{{ selectedReconciliation.status }}</span><span>{{ selectedReconciliation.date }} · {{ selectedReconciliation.interfaceCode }}</span></div>
        <div class="work-drawer-body">
          <div v-if="selectedReconciliation">
            <dl class="work-facts"><div><dt>任务编号</dt><dd>{{ selectedReconciliation.id }}</dd></div><div><dt>业务日期</dt><dd>{{ selectedReconciliation.date }}</dd></div><div><dt>机构</dt><dd>{{ organizationName(selectedReconciliation.organizationCode) }}</dd></div><div><dt>接口</dt><dd>{{ selectedReconciliation.interfaceCode }}</dd></div></dl>
            <div class="work-section-label work-spaced-label">差异记录</div><div v-if="!selectedReconciliation.differenceIds.length" class="prototype-empty">没有差异记录</div><button v-for="caseId in selectedReconciliation.differenceIds" :key="caseId" class="work-related-link" @click="openScenario(scenarios.find(item => item.id === caseId)!, 'work')">{{ scenarios.find(item => item.id === caseId)?.sourceRecordId }}<ArrowUpRight :size="15" /></button>
            <div v-if="selectedReconciliation.status === '待执行' && role === 'operator'" class="work-inline-actions"><button class="prototype-button" @click="runReconciliation(selectedReconciliation)">执行对账</button></div>
            <div v-else-if="role === 'manager'" class="work-inline-note">管理角色只读，对账任务由运维角色创建和执行。</div>
          </div>
          <div v-else class="work-form work-form-compact">
            <label for="recon-date">业务日期</label><input id="recon-date" v-model="reconciliationDraft.date" type="date" />
            <label for="recon-org">机构</label><select id="recon-org" v-model="reconciliationDraft.organizationCode"><option v-for="item in organizations" :key="item.id" :value="item.id">{{ item.name }}</option></select>
            <label for="recon-interface">接口</label><select id="recon-interface" v-model="reconciliationDraft.interfaceCode"><option v-for="item in interfaces" :key="item.id" :value="item.id">{{ item.id }} · {{ item.name }}</option></select>
            <button class="prototype-button" @click="createReconciliation">创建任务</button>
          </div>
        </div>
      </template>

      <template v-else-if="drawer === 'alert' && selectedAlert">
        <div class="work-drawer-sub"><span class="prototype-tag" :class="selectedAlert.level === '高' ? 'danger' : selectedAlert.level === '中' ? 'warning' : 'neutral'">{{ selectedAlert.level }}级</span><span>{{ selectedAlert.status }} · {{ selectedAlert.id }}</span></div>
        <div class="work-drawer-body"><dl class="work-facts"><div><dt>告警类型</dt><dd>{{ selectedAlert.type }}</dd></div><div><dt>告警对象</dt><dd>{{ selectedAlert.object }}</dd></div><div><dt>发生时间</dt><dd>{{ selectedAlert.occurredAt }}</dd></div><div><dt>当前状态</dt><dd>{{ selectedAlert.status }}</dd></div></dl>
          <div v-if="selectedAlert.note" class="work-finding"><Check :size="16" /><span>{{ selectedAlert.note }}</span></div>
          <div v-if="role === 'manager'" class="work-inline-note">管理角色只读，告警确认和恢复由运维角色处理。</div>
          <div v-else-if="selectedAlert.status !== '已恢复'" class="work-form"><label for="alert-note">处理备注{{ selectedAlert.status === '处理中' ? '（必填）' : '' }}</label><textarea id="alert-note" v-model="alertNote" rows="4" maxlength="200" placeholder="记录检查结果或恢复依据"></textarea><div class="work-inline-actions"><button v-if="selectedAlert.status === '待确认'" class="prototype-button" @click="updateAlert('处理中')">确认告警</button><button class="work-quiet-button" :disabled="!alertNote.trim()" @click="updateAlert('已恢复')">登记恢复</button></div></div>
        </div>
      </template>

      <template v-else-if="drawer === 'organization'">
        <div class="work-drawer-sub"><span class="prototype-tag" :class="organizationDraft.status === '启用' ? 'success' : 'danger'">{{ organizationDraft.status }}</span><span>{{ organizationDraft.id || '待填写机构代码' }}</span></div>
        <div class="work-tabs"><button v-for="tab in [{ key: 'overview', label: '基本信息' }, { key: 'work', label: '编码映射' }, { key: 'trace', label: '接口权限' }]" :key="tab.key" :class="{ active: drawerTab === tab.key }" @click="drawerTab = tab.key as typeof drawerTab">{{ tab.label }}</button></div>
        <div class="work-drawer-body">
          <div v-if="drawerTab === 'overview'" class="work-form">
            <label for="org-name">机构名称</label><input id="org-name" v-model="organizationDraft.name" maxlength="80" placeholder="填写机构名称" />
             <label for="org-id">平台机构代码</label><input id="org-id" v-model="organizationDraft.id" :disabled="!isNewOrganization" maxlength="64" placeholder="例如 SIM-ORG-002" />
            <label for="org-type">机构类型</label><select id="org-type" v-model="organizationDraft.relation"><option>县级医院</option><option>基层卫生院</option></select>
            <label>机构状态</label><div class="work-choice-group" role="group" aria-label="机构状态"><button :class="{ active: organizationDraft.status === '启用' }" @click="organizationDraft.status = '启用'">启用</button><button :class="{ active: organizationDraft.status === '停用' }" @click="organizationDraft.status = '停用'">停用</button></div>
          </div>
          <div v-else-if="drawerTab === 'work'" class="work-form">
            <label for="org-system">外部系统</label><input id="org-system" v-model="organizationDraft.system" maxlength="80" placeholder="例如 基层业务系统" />
            <label for="org-external-code">外部机构代码</label><input id="org-external-code" v-model="organizationDraft.externalCode" maxlength="64" placeholder="外部系统中的机构代码" />
            <div class="work-inline-note">交换时用平台机构代码限定数据范围，用外部机构代码完成双方机构对应。</div>
          </div>
          <div v-else>
            <div class="work-section-label">允许该机构调用的接口</div>
            <div class="work-permission-list"><label v-for="item in interfaces" :key="item.id"><input type="checkbox" :checked="organizationDraftInterfaces.includes(item.id)" @change="toggleOrganizationInterface(item.id)" /><span><strong>{{ item.name }}</strong><small>{{ item.id }} · {{ item.domain }}</small></span></label></div>
          </div>
          <div class="work-save-area"><label for="org-reason">变更原因（必填）</label><textarea id="org-reason" v-model="organizationChangeReason" maxlength="200" rows="3" placeholder="说明新增、修改、启停或权限调整原因"></textarea><button class="prototype-button" :disabled="!organizationDraft.id.trim() || !organizationDraft.name.trim() || !organizationDraft.externalCode.trim() || !organizationDraft.system.trim() || !organizationChangeReason.trim()" @click="saveOrganization">保存配置</button></div>
        </div>
      </template>
    </aside>
  </div>
</template>
