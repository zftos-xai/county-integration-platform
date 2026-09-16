<script setup lang="ts">
import {
  Activity, ArrowDownUp, ArrowLeftRight, ArrowUpRight, Bell,
  Building2, Check, Database, FileSearch,
  Menu, Plus, RefreshCw, Search, Settings2, ShieldCheck, Trash2, X,
} from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { Component } from 'vue'
import { scenarios as initialScenarios } from '../prototypeData'
import type { Scenario } from '../prototypeData'
import RecoveryPanel from '../components/RecoveryPanel.vue'
import AdminPagination from '../components/AdminPagination.vue'
import FoundationDataPanel from '../components/FoundationDataPanel.vue'
import { createRecoveryTasks, transitionRecovery } from '../recoveryWorkflow'
import type { RecoveryTask, RecoveryAction, RecoveryDetails } from '../recoveryWorkflow'

const recoveryTasks = ref<RecoveryTask[]>(createRecoveryTasks())
const recoveryFocus = ref('')
const scenarios = computed<Scenario[]>(() => initialScenarios.map(item => {
  const task = recoveryTasks.value.find(row => row.scenarioId === item.id)
  if (!task) return item
  return { ...item, status: task.status === '已核实成功' ? '处理成功' : task.status === '待发送' ? '待发送' : task.target === '明确未写入' ? '明确未写入' : item.status,
    receipt: task.status === '已核实成功' ? `核查依据：${task.evidence}；目标已写入（演示）` : task.target === '明确未写入' ? '已登记明确未写入的核查结论；依据见处置记录（演示）' : item.receipt }
}))
const selectedRecovery = computed(() => recoveryTasks.value.find(item => item.scenarioId === selectedId.value))
function openRecovery(id: string) {
  navigate('recovery')
  recoveryFocus.value = id
}
function recoveryAudit(requestId: string) {
  navigate('audit')
  query.value = requestId
}
function rehearseRecovery(id: string) {
  const index = recoveryTasks.value.findIndex(item => item.id === id)
  const seed = createRecoveryTasks().find(item => item.id === id)
  if (index < 0 || !seed) return
  seed.revision = recoveryTasks.value[index].revision + 1
  recoveryTasks.value[index] = seed
  const relatedAlert = alerts.value.find(item => item.id === `SIM-CLEAN-${id}`)
  if (relatedAlert) { relatedAlert.status = '已恢复'; relatedAlert.note = '当前合成事项已重新开始演练' }
  record('重新演练事项', initialScenarios.find(item => item.id === seed.scenarioId)!.requestId, '仅恢复合成事项初始状态；历史审计保留')
  toast.value = '当前事项已恢复初始演练状态'
}
function applyRecovery(id: string, action: RecoveryAction, evidence: string, note: string, revision: number, details: RecoveryDetails = {}) {
  const index = recoveryTasks.value.findIndex(item => item.id === id)
  if (index < 0) return
  try {
    const next = transitionRecovery(recoveryTasks.value[index], action, role.value, evidence, note, revision, now(), details)
    recoveryTasks.value[index] = next
    const scenario = initialScenarios.find(item => item.id === next.scenarioId)!
    record(`恢复处置：${next.history[0].action}`, scenario.requestId, `${evidence} · ${note}；业务：${next.status}；数据：${next.dataStatus}`)
    const alertId = `SIM-CLEAN-${next.id}`
    if (next.cleanupFailed && !alerts.value.some(item => item.id === alertId)) {
      alerts.value.unshift({ id: alertId, level: '高', type: '到期清理失败', object: scenario.requestId, occurredAt: now(), status: '待确认', note: '暂存数据清理失败；后续发送已停止' })
    }
    const cleanupAlert = alerts.value.find(item => item.id === alertId)
    if (action === 'clean' && cleanupAlert) { cleanupAlert.status = '已恢复'; cleanupAlert.note = `清理已完成，依据：${evidence}` }
    toast.value = '演示处置已记录，交换详情与审计已同步'
  } catch (error) { toast.value = error instanceof Error ? error.message : '操作未完成' }
}

type Role = 'manager' | 'operator'
type Page = 'overview' | 'foundation' | 'organizations' | 'interfaces' | 'exchanges' | 'exceptions' | 'alerts' | 'audit' | 'recovery'
type Drawer = 'organization' | 'interface' | 'scenario' | 'alert' | null
type NavItem = { key: Page; label: string; icon: Component; roles: readonly Role[] }
type NavGroup = { label: string; items: NavItem[] }
type Outcome = 'written' | 'not-written' | 'uncertain'
type Resolution = Outcome | 'revalidate' | 'blocked' | 'review'
type DrawerTab = 'overview' | 'work' | 'trace' | 'related'
type Organization = {
  id: string
  name: string
  relation: string
  system: string
  externalCode: string
  status: '启用' | '停用'
}
type InterfaceStatus = '接入条件待确认' | '待联调' | '演示启用' | '已启用' | '已停用'
type CodeMapping = { sourceCode: string; targetCode: string; description: string }
type InterfaceConfig = {
  id: string
  name: string
  domain: string
  direction: string
  provider: string
  caller: string
  sourceSystem: string
  targetSystem: string
  channelType: 'PHIS_Interface' | '回写接口' | '约定页面'
  tradeCode: string
  version: string
  auth: string
  dataScope: string
  exchangeMode: string
  retention: string
  status: InterfaceStatus
  related: string
  mappings: CodeMapping[]
}
type PlatformAlert = { id: string; level: '高' | '中' | '低'; type: string; object: string; occurredAt: string; status: '待确认' | '处理中' | '已恢复'; note: string }

const role = ref<Role>('manager')
const page = ref<Page>('overview')
const drawer = ref<Drawer>(null)
const drawerTab = ref<DrawerTab>('overview')
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
const handlingAction = ref<'revalidate' | 'blocked'>('revalidate')
const evidence = ref('')
const note = ref('')
const findings = ref<Record<string, { outcome: Resolution; evidence: string; note: string }>>({})
const localEvents = ref<{ action: string; object: string; actor: string; time: string; detail?: string }[]>([])
// Preserve older local demo tasks for compatibility; no task execution or UI remains.
const reviewTasks = ref<unknown[]>([])
const alerts = ref<PlatformAlert[]>([
  { id: 'SIM-ALERT-001', level: '高', type: '接口连续失败', object: '500-003', occurredAt: '2026-09-14 10:08:18', status: '待确认', note: '' },
  { id: 'SIM-ALERT-002', level: '中', type: '接收结果待核查', object: 'SIM-REQ-20260914-0018', occurredAt: '2026-09-14 11:10:00', status: '处理中', note: '正在核查目标端结果' },
  { id: 'SIM-ALERT-003', level: '低', type: '异常访问', object: 'SIM-REQ-20260914-0003', occurredAt: '2026-09-14 08:41:05', status: '已恢复', note: '越权请求已拒绝' },
])
const alertNote = ref('')
const alertProbeState = ref<'待执行' | '已通过'>('待执行')
const alertValidationState = ref<'未创建' | '已通过'>('未创建')
const alertRequestsLimited = ref(true)
const alertRetryPaused = ref(true)
const alertFollowupOwner = ref('李强')
const alertDueAt = ref('2026-09-16 16:00')
const alertNextCheckAt = ref('2026-09-16 14:00')
const listPage = ref(1)
const listPageSize = ref(10)
const alertTypeFilter = ref('全部问题')
const alertDataFilter = ref('全部数据状态')
const alertOwnerFilter = ref('全部责任人')
const persistenceReady = ref(false)
const storageKey = 'county-integration-prototype-v13'

const navGroups: NavGroup[] = [
  { label: '运行监控', items: [
    { key: 'overview', label: '运行总览', icon: Activity, roles: ['manager', 'operator'] },
  ] },
  { label: '基础数据链路', items: [
    { key: 'foundation', label: '基础数据', icon: Database, roles: ['manager', 'operator'] },
  ] },
  { label: '业务接口链路', items: [
    { key: 'interfaces', label: '业务接口', icon: Settings2, roles: ['manager'] },
    { key: 'recovery', label: '交换记录', icon: ArrowLeftRight, roles: ['manager', 'operator'] },
  ] },
  { label: '运行治理', items: [
    { key: 'alerts', label: '告警管理', icon: Bell, roles: ['manager', 'operator'] },
    { key: 'audit', label: '审计记录', icon: FileSearch, roles: ['manager', 'operator'] },
  ] },
  { label: '接入配置', items: [
    { key: 'organizations', label: '机构与权限', icon: Building2, roles: ['manager'] },
  ] },
]
const nav = navGroups.flatMap(group => group.items)
const organizations = ref<Organization[]>([
  { id: 'SIM-ORG-000', name: '示例县人民医院', relation: '县级医院', system: 'HIS / LIS / PACS', externalCode: 'SIM-HOSP-000', status: '启用' },
  { id: 'SIM-ORG-001', name: '示例青禾镇卫生院', relation: '基层卫生院', system: '基层业务系统', externalCode: 'SIM-PRIMARY-001', status: '启用' },
  { id: 'SIM-ORG-099', name: '示例未授权机构', relation: '基层卫生院', system: '基层业务系统', externalCode: 'SIM-PRIMARY-099', status: '停用' },
])
const organizationInterfaceAccess = ref<Record<string, string[]>>({
  'SIM-ORG-000': ['200-009', '200-011', '300-002', '500-002', '900-001'],
  'SIM-ORG-001': ['400-003', 'PAGE-EMR'],
  'SIM-ORG-099': [],
})
const organizationDraft = ref<Organization>({ id: '', name: '', relation: '基层卫生院', system: '', externalCode: '', status: '启用' })
const organizationDraftInterfaces = ref<string[]>([])
const organizationChangeReason = ref('')
const isNewOrganization = ref(false)
const interfaces = ref<InterfaceConfig[]>([
  { id: '200-009', name: '查询挂号记录', domain: '挂号缴费', direction: '中间接口平台 → 基层系统', provider: '基层系统', caller: '中间接口平台', sourceSystem: '县医院HIS相关应用', targetSystem: '基层系统PHIS_Interface', channelType: 'PHIS_Interface', tradeCode: '200-009', version: '参考文档V1.0', auth: '验证码；正式方式待确认', dataScope: '机构范围、查询条件和返回字段待确认', exchangeMode: '即时查询', retention: '仅保存交换记录和结果摘要', status: '待联调', related: '', mappings: [] },
  { id: '200-011', name: '门诊缴费', domain: '挂号缴费', direction: '中间接口平台 → 基层系统', provider: '基层系统', caller: '中间接口平台', sourceSystem: '县医院HIS相关应用', targetSystem: '基层系统PHIS_Interface', channelType: 'PHIS_Interface', tradeCode: '200-011', version: '参考文档V1.0', auth: '验证码；正式方式待确认', dataScope: '挂号、费用、结算字段待确认', exchangeMode: '业务写入', retention: '正文默认不长期保存', status: '接入条件待确认', related: '', mappings: [] },
  { id: '300-002', name: '获取门诊患者个案信息', domain: '双向转诊', direction: '中间接口平台 → 基层系统', provider: '基层系统', caller: '中间接口平台', sourceSystem: '县医院HIS相关应用', targetSystem: '基层系统PHIS_Interface', channelType: 'PHIS_Interface', tradeCode: '300-002', version: '参考文档V1.0', auth: '验证码；正式方式待确认', dataScope: '患者个案最小字段待确认', exchangeMode: '即时查询', retention: '仅保存交换记录和结果摘要', status: '接入条件待确认', related: '', mappings: [] },
  { id: '400-003', name: '电子病历回写', domain: '电子病历', direction: '电子病历系统 → 平台 → 基层系统', provider: '基层系统', caller: '中间接口平台', sourceSystem: '电子病历系统', targetSystem: '基层系统PHIS_Interface', channelType: '回写接口', tradeCode: '400-003', version: '参考文档V1.0', auth: '调用身份和机构范围待确认', dataScope: '病历标识、医生和时间字段待确认', exchangeMode: '业务写入', retention: '正文默认不长期保存', status: '接入条件待确认', related: '', mappings: [] },
  { id: '500-002', name: '按申请单号获取申请单', domain: 'PACS', direction: '中间接口平台 → 基层系统', provider: '基层系统', caller: '中间接口平台', sourceSystem: '县医院PACS相关应用', targetSystem: '基层系统PHIS_Interface', channelType: 'PHIS_Interface', tradeCode: '500-002', version: '参考文档V1.0', auth: '验证码；正式方式待确认', dataScope: '申请单最小字段和机构范围待确认', exchangeMode: '即时查询', retention: '仅保存交换记录和结果摘要', status: '待联调', related: 'DESIGN-02', mappings: [] },
  { id: '500-003', name: '回写检查报告', domain: 'PACS', direction: 'PACS → 中间接口平台 → 县医院HIS', provider: '县医院HIS', caller: '中间接口平台', sourceSystem: '县医院PACS', targetSystem: '县医院HIS', channelType: '回写接口', tradeCode: '500-003', version: '参考文档V1.0', auth: '验证码；正式方式待确认', dataScope: '检查报告最小字段与报告版本', exchangeMode: '业务写入', retention: '正文默认不长期保存', status: '演示启用', related: 'DESIGN-02', mappings: [] },
  { id: '600-003', name: '回写检验报告', domain: 'LIS', direction: 'LIS → 中间接口平台 → 县医院HIS', provider: '县医院HIS', caller: '中间接口平台', sourceSystem: '县医院LIS', targetSystem: '县医院HIS', channelType: '回写接口', tradeCode: '600-003', version: '参考文档V1.0', auth: '验证码；正式方式待确认', dataScope: '检验报告最小字段与报告版本', exchangeMode: '业务写入', retention: '正文默认不长期保存', status: '演示启用', related: 'DESIGN-05', mappings: [] },
  { id: '100-008', name: '医疗机构信息查询', domain: '机构信息', direction: '中间接口平台 → 基层系统', provider: '基层系统', caller: '中间接口平台', sourceSystem: '中间接口平台', targetSystem: '基层系统PHIS_Interface', channelType: 'PHIS_Interface', tradeCode: '100-008', version: '参考文档V1.0', auth: '验证码；正式方式待确认', dataScope: '机构标识、名称与状态', exchangeMode: '即时查询', retention: '仅保存交换记录和结果摘要', status: '接入条件待确认', related: 'DESIGN-03', mappings: [] },
  { id: 'ACB666576D94A008', name: '健康档案上传', domain: '健康档案', direction: '院内系统 → 中间接口平台 → 健康档案云平台', provider: '健康档案云平台', caller: '中间接口平台', sourceSystem: '基层院内HIS', targetSystem: '健康档案云平台', channelType: '回写接口', tradeCode: 'ACB666576D94A008', version: '演示规则V2', auth: '正式方式待确认', dataScope: '演示健康档案最小字段；正式范围待确认', exchangeMode: '异步写入', retention: '仅在发送恢复期临时保存最小正文', status: '演示启用', related: 'DESIGN-01', mappings: [] },
  { id: 'ACB666576D94A000', name: '体检信息上传', domain: '体检', direction: '院内系统 → 中间接口平台 → 健康档案云平台', provider: '健康档案云平台', caller: '中间接口平台', sourceSystem: '基层院内HIS', targetSystem: '健康档案云平台', channelType: '回写接口', tradeCode: 'ACB666576D94A000', version: '演示规则V1', auth: '正式方式待确认', dataScope: '演示体检最小字段；正式范围待确认', exchangeMode: '异步写入', retention: '仅在发送恢复期临时保存最小正文', status: '接入条件待确认', related: 'DESIGN-04', mappings: [] },
  { id: 'SIM-REPORT-QUERY', name: '查询检查报告', domain: '报告查询', direction: '基层系统 → 中间接口平台 → PACS', provider: '县医院PACS', caller: '中间接口平台', sourceSystem: '基层业务系统', targetSystem: '县医院PACS', channelType: 'PHIS_Interface', tradeCode: 'SIM-REPORT-QUERY', version: '演示规则V1', auth: '正式方式待确认', dataScope: '报告查询条件和结果摘要；正式范围待确认', exchangeMode: '即时查询', retention: '不保存报告正文', status: '已停用', related: 'DESIGN-09', mappings: [] },
  { id: '900-001', name: '回写远程会诊结果', domain: '远程会诊', direction: '远程会诊系统 → 平台 → 基层系统', provider: '基层系统', caller: '中间接口平台', sourceSystem: '远程会诊系统', targetSystem: '基层系统PHIS_Interface', channelType: '回写接口', tradeCode: '900-001', version: '参考文档V1.0', auth: '调用身份和机构范围待确认', dataScope: '会诊结果最小字段待确认', exchangeMode: '业务写入', retention: '正文默认不长期保存', status: '接入条件待确认', related: '', mappings: [] },
  { id: 'PAGE-EMR', name: 'HIS打开电子病历页面', domain: '电子病历', direction: '县医院HIS → 平台入口 → 电子病历系统', provider: '电子病历系统', caller: '县医院HIS', sourceSystem: '县医院HIS', targetSystem: '电子病历系统页面', channelType: '约定页面', tradeCode: '页面标识待确认', version: '接入条件待确认', auth: '单点登录方式待确认', dataScope: '患者上下文、就诊上下文和有效期待确认', exchangeMode: '页面跳转', retention: '平台不保存页面业务正文', status: '接入条件待确认', related: '', mappings: [] },
])
const interfaceDraft = ref<InterfaceConfig | null>(null)
const interfaceChangeReason = ref('')
const intakeCheckResult = ref('')
const interfaceHistory = ref<Record<string, { time: string; actor: string; summary: string; reason: string }[]>>({})
const interfaceStatuses: InterfaceStatus[] = ['接入条件待确认', '待联调', '演示启用', '已启用', '已停用']
const auditSeed = [
  { time: '09:27:14', actor: 'SIM-SVC-EHR', action: '发送超时，转人工核查', object: 'SIM-REQ-20260914-0018', detail: '结果未知' },
  { time: '10:08:18', actor: 'SIM-SVC-PACS', action: '报告版本校验未通过', object: 'SIM-REQ-20260914-0026', detail: '申请单版本 2 / 报告引用版本 1' },
  { time: '08:41:05', actor: 'SIM-SVC-ACCESS', action: '拒绝越权调用', object: 'SIM-REQ-20260914-0003', detail: '机构不在允许范围' },
]
const queueStatuses = ['结果未知', '明确未写入', '校验未通过', '越权拒绝', '查询失败']
const visibleNavGroups = computed(() => navGroups
  .map(group => ({ ...group, items: group.items.filter(item => item.roles.includes(role.value)) }))
  .filter(group => group.items.length))
const visibleNav = computed(() => visibleNavGroups.value.flatMap(group => group.items))
const isDataManagement = computed(() => ['recovery', 'exceptions'].includes(page.value))
const pageHeader = computed(() => ({
  overview: {
    section: '运行监控',
    title: '运行总览',
    description: role.value === 'manager'
      ? '掌握基础数据同步、业务交换和待处理告警'
      : '掌握同步异常、交换异常和当前待办',
  },
  foundation: { section: '基础数据链路', title: '基础数据', description: '核对同步结果、平台保存范围和 HIS 读取状态' },
  organizations: { section: '接入配置', title: '机构与权限', description: '维护交换机构、编码对应和接口授权范围' },
  interfaces: { section: '业务接口链路', title: '业务接口', description: '维护接口规则、版本和新请求准入状态' },
  exchanges: { section: '业务接口链路', title: '交换记录', description: '按业务单号追踪受理、发送、回执和处理进度' },
  recovery: { section: '业务接口链路', title: '交换记录', description: '核查业务结果、数据保存状态和后续处理责任' },
  exceptions: { section: '业务接口链路', title: '异常记录', description: '核查异常原因并登记处理结果和依据' },
  alerts: { section: '运行治理', title: '告警管理', description: '处理结果未知、版本变化和数据到期等运行问题' },
  audit: { section: '运行治理', title: '审计记录', description: '追溯配置变更、人工核查和告警处理记录' },
}[page.value] ?? { section: '运行监控', title: '运行总览', description: '掌握平台当前运行状态' }))
const selectedScenario = computed(() => scenarios.value.find(item => item.id === selectedId.value))
const selectedInterface = computed(() => interfaces.value.find(item => item.id === selectedId.value))
const relatedScenario = computed(() => scenarios.value.find(item => item.id === selectedInterface.value?.related))
const relatedInterfaceScenarios = computed(() => scenarios.value.filter(item => item.interfaceCode === selectedInterface.value?.id)
  .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt)))
const selectedAlert = computed(() => alerts.value.find(item => item.id === selectedId.value))
function enrichAlert(item: PlatformAlert, index: number) {
  const relatedTask = recoveryTasks.value.find(task => initialScenarios.find(s => s.id === task.scenarioId)?.requestId === item.object)
  const relatedScenario = relatedTask ? initialScenarios.find(s => s.id === relatedTask.scenarioId) : initialScenarios.find(s => s.requestId === item.object)
  const relatedInterface = interfaces.value.find(candidate => candidate.id === item.object)
  const affectedScenarios = relatedInterface ? scenarios.value.filter(candidate => candidate.interfaceCode === relatedInterface.id) : relatedScenario ? [relatedScenario] : []
  return { ...item, relatedTask, relatedScenario, relatedInterface, affectedScenarios,
    businessResult: relatedTask?.target ?? (relatedInterface ? `${affectedScenarios.length} 笔交换受影响` : '—'),
    dataStatus: relatedTask?.dataStatus ?? relatedInterface?.retention ?? '仅保存交换记录',
    owner: item.status === '待确认' ? '未分配' : '当前运维', wait: `${Math.max(1, 26 - index * 4)}小时`, dueAt: `2026-09-15 ${String(10 + index).padStart(2, '0')}:00` }
}
const alertRows = computed(() => alerts.value.map(enrichAlert)
  .filter(item => alertTypeFilter.value === '全部问题' || item.type === alertTypeFilter.value)
  .filter(item => alertDataFilter.value === '全部数据状态' || item.dataStatus === alertDataFilter.value)
  .filter(item => alertOwnerFilter.value === '全部责任人' || item.owner === alertOwnerFilter.value)
  .filter(item => `${item.id} ${item.type} ${item.object} ${item.relatedScenario?.sourceRecordId ?? ''} ${item.relatedInterface?.name ?? ''} ${item.affectedScenarios.map(candidate => candidate.sourceRecordId).join(' ')}`.toLowerCase().includes(query.value.toLowerCase())))
const selectedAlertRow = computed(() => alertRows.value.find(item => item.id === selectedId.value)
  ?? alerts.value.map(enrichAlert).find(item => item.id === selectedId.value))
const isInterfaceFailureAlert = computed(() => selectedAlert.value?.type === '接口连续失败' && Boolean(selectedAlertRow.value?.relatedInterface))
const interfaceIncidentScenarios = computed(() => selectedAlertRow.value?.affectedScenarios ?? [])
const interfaceIncidentOrganizations = computed(() => new Set(interfaceIncidentScenarios.value.map(item => item.organizationCode)).size)
const interfaceIncidentRoutes = computed(() => interfaceIncidentScenarios.value.map(item => {
  if (item.status === '结果未知') return { item, result: '结果未知', tone: 'warning', stage: '请求发出后', route: '核查目标结果', action: '创建核查任务' }
  if (item.status === '校验未通过') return { item, result: '校验未通过', tone: 'danger', stage: '发送前校验', route: '来源修正后重新校验', action: '查看校验原因' }
  if (item.status === '处理成功' || item.status === '重复已识别') return { item, result: '已确认写入', tone: 'success', stage: '目标系统', route: '禁止再次发送', action: '查看写入依据' }
  return { item, result: '明确未发送', tone: 'neutral', stage: '建立连接前', route: '加入受控重试', action: '加入重试队列' }
}))
const interfaceIncidentCounts = computed(() => ({
  notSent: interfaceIncidentRoutes.value.filter(row => row.result === '明确未发送').length,
  unknown: interfaceIncidentRoutes.value.filter(row => row.result === '结果未知').length,
  invalid: interfaceIncidentRoutes.value.filter(row => row.result === '校验未通过').length,
  written: interfaceIncidentRoutes.value.filter(row => row.result === '已确认写入').length,
}))
const selectedAlertSteps = computed(() => {
  const type = selectedAlert.value?.type ?? ''
  if (type.includes('到期') || type.includes('清理')) return [
    ['系统已停止相关发送', '到期规则触发后，平台自动阻止该请求继续发送。'], ['领取故障处理', '自动清理失败后，由运维领取技术故障事项。'],
    ['核对既定清理范围', '查看接口策略中已经批准的临时数据范围，不逐笔重新选择。'], ['重试自动清理', '排除故障后受控重试，并保留执行结果。'], ['登记处理结论', '记录证据、遗留问题和后续责任。'],
  ]
  if (type.includes('接口')) return [
    ['确认影响范围', '核对失败接口、连续失败次数和受影响业务。'], ['领取处理事项', '指定当前处理人并暂停无效的自动重试。'],
    ['检查连接与日志', '核对网络、证书、目标服务和最近一次错误日志。'], ['恢复并验证', '恢复连接后执行受控验证，不直接批量补发。'], ['登记处理结论', '记录恢复依据、遗留业务和后续责任。'],
  ]
  if (type.includes('接收结果')) return [
    ['领取核查事项', '指定核查人并确认本次请求与业务单号。'], ['查询目标系统', '按同一业务单号和请求版本核查目标端结果。'],
    ['登记核查证据', '保存查询记录、回执编号和核查结论。'], ['决定后续处理', '已写入则结束；明确未写入才进入受控恢复。'], ['安排下次核查', '仍无法确认时登记责任人与下次核查时间。'],
  ]
  return [
    ['确认告警对象', '核对调用主体、机构、接口与数据范围。'], ['领取处理事项', '指定当前处理人并保持请求拦截。'],
    ['检查授权配置', '核对机构权限、接口权限和授权变更记录。'], ['确认处置结果', '修正配置或确认拒绝符合预期。'], ['登记处理结论', '记录依据、影响范围和后续安排。'],
  ]
})
const drawerTitle = computed(() => {
  if (drawer.value === 'scenario') return selectedScenario.value?.sourceRecordId ?? '交换详情'
  if (drawer.value === 'interface') return selectedInterface.value?.name ?? '接口配置'
  if (drawer.value === 'alert') return selectedAlert.value?.type ?? '告警详情'
  return organizationDraft.value.name || '新增机构'
})
const rows = computed(() => scenarios.value
  .filter(item => orgFilter.value === '全部机构' || item.organizationCode === orgFilter.value)
  .filter(item => statusFilter.value === '全部状态' || item.status === statusFilter.value
    || (statusFilter.value === '成功或重复已识别' && ['处理成功', '重复已识别'].includes(item.status))
    || (statusFilter.value === '待核查' && ['结果未知', '校验未通过', '查询失败'].includes(item.status)))
  .filter(item => `${item.sourceRecordId} ${item.requestId} ${item.organization} ${item.interfaceCode} ${item.title}`.toLowerCase().includes(query.value.trim().toLowerCase()))
  .sort((a, b) => descending.value ? b.occurredAt.localeCompare(a.occurredAt) : a.occurredAt.localeCompare(b.occurredAt)))
const queue = computed(() => rows.value.filter(item => queueStatuses.includes(item.status)))
const interfaceRows = computed(() => interfaces.value.filter(item => `${item.id} ${item.name} ${item.domain} ${item.status} ${item.provider} ${item.caller} ${item.sourceSystem} ${item.targetSystem} ${item.channelType}`.toLowerCase().includes(query.value.trim().toLowerCase())))
const organizationRows = computed(() => organizations.value.filter(item => `${item.id} ${item.name} ${item.externalCode}`.toLowerCase().includes(query.value.trim().toLowerCase())))
const activeFilterCount = computed(() => Number(Boolean(query.value.trim())) + Number(orgFilter.value !== '全部机构') + Number(statusFilter.value !== '全部状态'))
const canSaveFinding = computed(() => role.value === 'operator' && selectedScenario.value && claimed.value.includes(selectedId.value)
  && note.value.trim() && (selectedScenario.value.status !== '结果未知' || outcome.value === 'uncertain' || evidence.value.trim()))
const auditRows = computed(() => [
  ...localEvents.value,
  ...auditSeed.map(item => ({ ...item, time: `2026-09-14 ${item.time}` })),
].filter(item => auditActorFilter.value === '全部主体' || (auditActorFilter.value === '系统事件' ? item.actor.startsWith('SIM-SVC') : !item.actor.startsWith('SIM-SVC')))
  .filter(item => auditActionFilter.value === '全部动作' || item.action.includes(auditActionFilter.value))
  .filter(item => `${item.actor} ${item.action} ${item.object} ${item.detail ?? ''}`.toLowerCase().includes(query.value.trim().toLowerCase())))
function pageSlice<T>(items: T[]): T[] { return items.slice((listPage.value - 1) * listPageSize.value, listPage.value * listPageSize.value) }
const organizationPagedRows = computed(() => pageSlice(organizationRows.value))
const interfacePagedRows = computed(() => pageSlice(interfaceRows.value))
const exchangePagedRows = computed(() => pageSlice(rows.value))
const alertPagedRows = computed(() => pageSlice(alertRows.value))
const auditPagedRows = computed(() => pageSlice(auditRows.value))

function navigate(next: Page) {
  recoveryFocus.value = ''
  page.value = next
  drawer.value = null
  menuOpen.value = false
  query.value = ''
  orgFilter.value = '全部机构'
  statusFilter.value = '全部状态'
  auditActorFilter.value = '全部主体'
  auditActionFilter.value = '全部动作'
  listPage.value = 1
  window.requestAnimationFrame(() => window.scrollTo({ top: 0, left: 0 }))
}
watch([query, orgFilter, statusFilter, auditActorFilter, auditActionFilter, alertTypeFilter, alertDataFilter, alertOwnerFilter], () => { listPage.value = 1 })
function clearListFilters() {
  query.value = ''
  orgFilter.value = '全部机构'
  statusFilter.value = '全部状态'
}
function switchRole(next: Role) {
  if (role.value === next) return
  role.value = next
  if (!isDataManagement.value && !visibleNav.value.some(item => item.key === page.value)) navigate('overview')
  drawer.value = null
  toast.value = `已切换至${next === 'manager' ? '管理' : '运维'}视角`
}
function open(kind: Exclude<Drawer, null>, id: string, tab: DrawerTab = 'overview') {
  drawer.value = kind
  selectedId.value = id
  drawerTab.value = tab
  outcome.value = 'uncertain'
  handlingAction.value = 'revalidate'
  evidence.value = ''
  note.value = ''
  alertNote.value = ''
  alertProbeState.value = '待执行'
  alertValidationState.value = '未创建'
  alertRequestsLimited.value = true
  alertRetryPaused.value = true
}
function editInterface(item: InterfaceConfig, tab: DrawerTab = 'overview') {
  interfaceDraft.value = { ...item, mappings: item.mappings.map(mapping => ({ ...mapping })) }
  interfaceChangeReason.value = ''
  intakeCheckResult.value = ''
  open('interface', item.id, tab)
}
function interfaceScenarioCount(interfaceId: string) {
  return scenarios.value.filter(item => item.interfaceCode === interfaceId).length
}
function interfaceAcceptsNew(item: InterfaceConfig) {
  return item.status === '已启用' || item.status === '演示启用'
}
function interfaceSnapshotMismatchCount(interfaceId: string) {
  const current = interfaces.value.find(item => item.id === interfaceId)
  if (!current) return 0
  return scenarios.value.filter(item => item.interfaceCode === interfaceId && item.interfaceVersion !== current.version).length
}
function runIntakeCheck() {
  const current = selectedInterface.value
  if (!current) return
  intakeCheckResult.value = interfaceAcceptsNew(current)
    ? `允许创建新交换记录；新记录采用 ${current.version}，历史记录保持原规则快照。`
    : `已阻止新请求；接口状态为“${current.status}”，不会生成交换记录。`
  record('检查新请求准入', current.id, intakeCheckResult.value)
  toast.value = interfaceAcceptsNew(current) ? '准入检查通过（演示）' : '准入检查已阻止新请求（演示）'
}
function openInterfaceDefinition(interfaceId: string) {
  const item = interfaces.value.find(row => row.id === interfaceId)
  if (!item) { toast.value = '当前交换记录尚未关联接口定义'; return }
  if (role.value !== 'manager') { toast.value = '运维可在记录中查看规则快照；接口定义由管理人员维护'; return }
  navigate('interfaces')
  editInterface(item)
}
function openRelatedInterfaceScenario(item: Scenario) {
  drawer.value = null
  const task = recoveryTasks.value.find(row => row.scenarioId === item.id)
  if (task) { openRecovery(task.id); return }
  navigate('exchanges')
  openScenario(item)
}
function addCodeMapping() {
  if (role.value !== 'manager') return
  interfaceDraft.value?.mappings.push({ sourceCode: '', targetCode: '', description: '' })
}
function updateAlert(status: PlatformAlert['status']) {
  if (role.value !== 'operator' || !selectedAlert.value) return
  if (status === '已恢复' && (!alertNote.value.trim() || (selectedAlert.value.type === '到期清理失败' && recoveryTasks.value.some(item => item.cleanupFailed && initialScenarios.find(s => s.id === item.scenarioId)?.requestId === selectedAlert.value?.object)))) { toast.value = '请先完成该记录的清理处置，再登记恢复'; return }
  selectedAlert.value.status = status
  if (alertNote.value.trim()) selectedAlert.value.note = alertNote.value.trim()
  record(status === '处理中' ? '确认告警' : '登记告警恢复', selectedAlert.value.id, selectedAlert.value.note)
  toast.value = status === '处理中' ? '告警已确认' : '告警已登记恢复'
  alertNote.value = ''
}
function requireAlertOperator(): boolean {
  if (role.value === 'operator') return true
  toast.value = '当前为管理视角，请切换至运维角色后执行运行控制'
  return false
}
function updateIncidentControl() {
  if (!requireAlertOperator()) return
  alertRequestsLimited.value = true
  alertRetryPaused.value = true
  if (selectedAlert.value?.status === '待确认') updateAlert('处理中')
  record('调整接口运行控制', selectedAlert.value?.id ?? '', '限制新请求并暂停自动重试（演示）')
  toast.value = '运行控制已更新：限制新请求，暂停自动重试'
}
function runInterfaceProbe() {
  if (!requireAlertOperator()) return
  if (selectedAlert.value?.status === '待确认') selectedAlert.value.status = '处理中'
  if (alertProbeState.value === '待执行') {
    alertProbeState.value = '已通过'
    record('执行接口探测', selectedAlert.value?.id ?? '', '目标接口探测通过（合成演示）')
    toast.value = '接口探测已通过，下一步执行受控验证'
    return
  }
  alertValidationState.value = '已通过'
  record('执行受控验证', selectedAlert.value?.id ?? '', '受控验证请求通过（合成演示）')
  toast.value = '受控验证已通过，可以恢复新请求'
}
function resumeIncidentRequests() {
  if (!requireAlertOperator() || alertValidationState.value !== '已通过') return
  alertRequestsLimited.value = false
  alertRetryPaused.value = false
  record('恢复新请求', selectedAlert.value?.id ?? '', '接口探测通过后恢复新请求（演示）')
  toast.value = '新请求已恢复，历史受影响记录仍按各自路径处理'
}
function saveIncidentProgress(observe = false) {
  if (!requireAlertOperator() || !selectedAlert.value) return
  selectedAlert.value.status = '处理中'
  selectedAlert.value.note = alertNote.value.trim() || `后续责任人：${alertFollowupOwner.value}；下次检查：${alertNextCheckAt.value}`
  record(observe ? '转入持续观察' : '保存告警处理进展', selectedAlert.value.id, `${selectedAlert.value.note}；完成期限：${alertDueAt.value}`)
  toast.value = observe ? '已转入持续观察，受影响记录继续跟踪' : '处理进展已保存'
}
function resetPrototype() {
  localStorage.removeItem(storageKey)
  localStorage.removeItem('county-integration-foundation-v1')
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
  if (![interfaceDraft.value.provider, interfaceDraft.value.caller, interfaceDraft.value.sourceSystem, interfaceDraft.value.targetSystem, interfaceDraft.value.auth, interfaceDraft.value.dataScope, interfaceDraft.value.retention].every(value => value.trim())) {
    toast.value = '请先补全运行角色、源目标系统、鉴权、数据范围和保存策略'
    return
  }
  const saved = { ...interfaceDraft.value, mappings: interfaceDraft.value.mappings.map(item => ({ ...item })) }
  interfaces.value[index] = saved
  const summary = `状态：${saved.status}；版本：${saved.version || '待确认'}；编码映射：${saved.mappings.length} 项`
  const history = interfaceHistory.value[saved.id] ?? []
  interfaceHistory.value[saved.id] = [{ time: now(), actor: '平台管理', summary, reason: interfaceChangeReason.value.trim() }, ...history]
  record('修改接口配置', saved.id, summary)
  interfaceChangeReason.value = ''
  intakeCheckResult.value = ''
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
  record('登记核查', selectedScenario.value.requestId, outcomeLabel(findings.value[selectedId.value].outcome))
  toast.value = '核查已记录，未改变目标业务状态'
  note.value = ''
  evidence.value = ''
}
function showRelated(id: string) {
  const item = scenarios.value.find(candidate => candidate.id === id)
  if (!item) return
  navigate('exchanges')
  openScenario(item)
}
function showDataRecord(id: string, tab: 'overview' | 'work' | 'trace' = 'overview') {
  const item = scenarios.value.find(candidate => candidate.id === id)
  if (!item) return
  const recovery = recoveryTasks.value.find(candidate => candidate.scenarioId === id)
  if (recovery) { openRecovery(recovery.id); return }
  openScenario(item, tab)
}
function statusClass(status: string) {
  return status === '处理成功' ? 'success' : status === '越权拒绝' ? 'danger'
    : status === '已受理' || status === '重复已识别' ? 'neutral' : 'warning'
}
function interfaceStatusClass(status: InterfaceStatus) {
  return status === '已启用' || status === '演示启用' ? 'success' : status === '已停用' ? 'danger' : 'warning'
}
function organizationName(code: string) {
  return organizations.value.find(item => item.id === code)?.name ?? code
}
function outcomeLabel(value: Resolution) {
  return { written: '目标已写入', 'not-written': '明确未写入', uncertain: '仍无法确认', revalidate: '重新校验', blocked: '保持拦截', review: '转入核查' }[value]
}
function handlingStatus(id: string) {
  const task = recoveryTasks.value.find(item => item.scenarioId === id)
  if (task) return task.status
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
      if (Array.isArray(saved.reviewTasks)) reviewTasks.value = saved.reviewTasks
      if (Array.isArray(saved.alerts)) alerts.value = saved.alerts.map((item: PlatformAlert) => item.id === 'SIM-ALERT-002' && item.object === 'SIM-RECON-20260914-01' ? { ...item, type: '接收结果核查提醒', object: 'SIM-REQ-20260914-0018', note: '历史演示提醒，请以关联数据的最新核查结果为准' } : item)
      if (Array.isArray(saved.recoveryTasks)) recoveryTasks.value = createRecoveryTasks().map(seed => saved.recoveryTasks.find((item: RecoveryTask) => item.id === seed.id && item.kind === seed.kind && Array.isArray(item.history) && Number.isInteger(item.revision)) ?? seed)
      if (saved.interfaceHistory) interfaceHistory.value = saved.interfaceHistory
    }
    for (const task of recoveryTasks.value.filter(item => item.cleanupFailed)) {
      const id = `SIM-CLEAN-${task.id}`
      if (!alerts.value.some(item => item.id === id)) alerts.value.push({ id, level: '高', type: '到期清理失败', object: initialScenarios.find(item => item.id === task.scenarioId)!.requestId, occurredAt: '2026-09-15 11:01:00', status: '待确认', note: '清理失败演示样例；发送已停止，等待重试' })
    }
  } catch {
    localStorage.removeItem(storageKey)
  }
  persistenceReady.value = true
})
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
watch([recoveryTasks, organizations, organizationInterfaceAccess, interfaces, claimed, findings, localEvents, reviewTasks, alerts, interfaceHistory], () => {
  if (!persistenceReady.value) return
  localStorage.setItem(storageKey, JSON.stringify({ recoveryTasks: recoveryTasks.value, organizations: organizations.value, organizationInterfaceAccess: organizationInterfaceAccess.value, interfaces: interfaces.value, claimed: claimed.value, findings: findings.value, localEvents: localEvents.value, reviewTasks: reviewTasks.value, alerts: alerts.value, interfaceHistory: interfaceHistory.value }))
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
          <button v-for="item in group.items" :key="item.key" :class="{ active: page === item.key || item.key === 'recovery' && isDataManagement }" @click="navigate(item.key as Page)">
            <component :is="item.icon" :size="17" /><span>{{ item.label }}</span>
          </button>
        </div>
      </nav>
      <div class="prototype-sidebar-foot">
        <div class="prototype-user"><span class="prototype-avatar">张</span><span><strong>张建国</strong><small>{{ role === 'manager' ? '管理人员' : '运维人员' }}</small></span></div>
        <button class="prototype-icon" aria-label="重置原型数据" title="重置原型数据" @click="resetPrototype"><RefreshCw :size="15" /></button>
      </div>
    </aside>
    <div v-if="menuOpen" class="prototype-scrim" @click="menuOpen = false"></div>

    <main class="prototype-main" :class="{ 'has-alert-workspace': drawer === 'alert' }">
      <header class="prototype-topbar">
        <button class="prototype-icon prototype-menu" aria-label="打开导航" title="打开导航" @click="menuOpen = true"><Menu :size="20" /></button>
        <div class="prototype-heading">
          <span class="prototype-section-name">{{ pageHeader.section }}</span>
          <div class="prototype-title-line"><h1>{{ pageHeader.title }}</h1><p>{{ pageHeader.description }}</p></div>
        </div>
        <div class="prototype-top-meta">
          <span class="prototype-demo">演示数据 · 截至 2026-09-15 12:00</span>
          <div class="prototype-role-switch">
            <span>当前角色</span>
            <div class="prototype-segment" aria-label="切换角色">
              <button :class="{ active: role === 'manager' }" :aria-pressed="role === 'manager'" @click="switchRole('manager')">管理</button>
              <button :class="{ active: role === 'operator' }" :aria-pressed="role === 'operator'" @click="switchRole('operator')">运维</button>
            </div>
          </div>
        </div>
      </header>
      <div class="prototype-content">
        <div v-if="toast" class="work-toast" role="status"><Check :size="16" />{{ toast }}<button class="prototype-icon" aria-label="关闭提示" @click="toast = ''"><X :size="15" /></button></div>

        <FoundationDataPanel v-if="page === 'foundation'" :role="role" @switch-role="switchRole('operator')" />
        <RecoveryPanel v-else-if="page === 'recovery' || page === 'exceptions'" :tasks="recoveryTasks" :role="role" :scenarios="scenarios" :interfaces="interfaces" :focus-id="recoveryFocus" :exceptions-only="page === 'exceptions'" @action="applyRecovery" @exchange="showRelated" @record="showDataRecord" @interface="openInterfaceDefinition" @audit="recoveryAudit" @operate="role = 'operator'" @rehearse="rehearseRecovery" />
        <template v-else-if="page === 'overview'">
          <div class="overview-commandbar">
            <div><strong>运行状态</strong><small>数据截止 2026-09-15 12:00 · 合成演示</small></div>
            <button class="btn btn-outline-secondary btn-sm" @click="toast = '页面数据已刷新'"><RefreshCw :size="15" />刷新</button>
          </div>
          <section class="overview-kpis" aria-label="运行关键指标">
            <button @click="navigate('organizations')"><span>启用机构</span><strong>{{ organizations.filter(item => item.status === '启用').length }}</strong><small>共 {{ organizations.length }} 个接入机构</small></button>
            <button @click="navigate(role === 'manager' ? 'interfaces' : 'recovery')"><span>允许新请求的接口</span><strong>{{ interfaces.filter(item => interfaceAcceptsNew(item)).length }}</strong><small>{{ interfaces.filter(item => !interfaceAcceptsNew(item)).length }} 项当前阻止新请求</small></button>
            <button class="warning" @click="navigate('recovery')"><span>待核查交换</span><strong>{{ queue.length }}</strong><small>需要确认业务结果或版本</small></button>
            <button class="danger" @click="navigate('alerts')"><span>待处理告警</span><strong>{{ alerts.filter(item => item.status !== '已恢复').length }}</strong><small>进入告警工作队列</small></button>
          </section>
          <div class="overview-work-grid">
            <section class="card overview-work-card">
              <div class="card-header"><div><h3 class="card-title">待处理告警</h3><small>先确认影响范围，再登记处理结果</small></div><button class="btn btn-link btn-sm ms-auto" @click="navigate('alerts')">查看全部</button></div>
              <div class="table-responsive"><table class="table table-vcenter"><thead><tr><th>问题</th><th>业务对象</th><th>影响</th><th>责任人</th><th>操作</th></tr></thead><tbody><tr v-for="item in alertRows.filter(row => row.status !== '已恢复').slice(0, 5)" :key="item.id"><td><span class="badge" :class="item.level === '高' ? 'bg-red-lt text-red' : 'bg-yellow-lt text-yellow'">{{ item.type }}</span><small>{{ item.occurredAt }}</small></td><td><strong>{{ item.relatedInterface?.name || item.relatedScenario?.sourceRecordId || item.object }}</strong><small>{{ item.object }}</small></td><td>{{ item.businessResult }}</td><td>{{ item.owner }}</td><td><button class="btn btn-primary btn-sm" @click="open('alert', item.id)">处理</button></td></tr></tbody></table></div>
            </section>
            <section class="card overview-work-card">
              <div class="card-header"><div><h3 class="card-title">待核查交换</h3><small>业务结果未确认或当前规则阻止继续处理</small></div><button class="btn btn-link btn-sm ms-auto" @click="navigate('recovery')">查看全部</button></div>
              <div class="overview-exchange-list"><button v-for="item in queue.slice(0, 5)" :key="item.id" @click="openScenario(item, 'work')"><span><strong>{{ item.sourceRecordId }}</strong><small>{{ item.organization }} · {{ item.interfaceCode }}</small></span><span><em class="prototype-tag" :class="statusClass(item.status)">{{ item.status }}</em><small>{{ item.occurredAt }}</small></span><ArrowUpRight :size="15" /></button></div>
            </section>
          </div>
        </template>

        <template v-else-if="page === 'organizations'">
          <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索机构" placeholder="机构名称、平台代码或外部代码" /></label><span>{{ organizationRows.length }} 个机构</span><button class="prototype-button" @click="addOrganization"><Plus :size="15" />新增机构</button></div>
          <section class="prototype-section work-table-section action-column-table"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>机构名称</th><th>平台机构代码</th><th>外部机构代码</th><th>机构类型</th><th>允许接口</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in organizationPagedRows" :key="item.id"><td><button class="work-row-link" @click="editOrganization(item)">{{ item.name }}</button><small>{{ item.system }}</small></td><td>{{ item.id }}</td><td>{{ item.externalCode }}</td><td>{{ item.relation }}</td><td>{{ organizationInterfaceAccess[item.id]?.length ?? 0 }} 项</td><td><span class="prototype-tag" :class="item.status === '启用' ? 'success' : 'danger'">{{ item.status }}</span></td><td><button class="prototype-text-button" @click="editOrganization(item)">配置</button></td></tr></tbody></table><div v-if="!organizationRows.length" class="prototype-empty">未找到机构</div></div><AdminPagination :total="organizationRows.length" :page="listPage" :page-size="listPageSize" @update:page="listPage = $event" @update:page-size="listPageSize = $event" /></section>
        </template>

        <template v-else-if="page === 'interfaces'">
          <div class="business-interface-intro"><div><span>核心链路 02</span><strong>接口状态直接控制新请求能否进入</strong><small>新请求采用当前规则版本；历史交换记录保留受理时规则快照，不随配置修改。</small></div><div class="interface-summary"><em>{{ interfaces.length }} 项</em><em>{{ interfaces.filter(item => interfaceAcceptsNew(item)).length }} 项允许演示接入</em><em>{{ interfaces.filter(item => item.status === '已停用').length }} 项已停用</em><em>{{ interfaces.filter(item => item.status === '接入条件待确认').length }} 项条件待确认</em></div></div>
          <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索业务接口" placeholder="交易码、名称、系统或业务场景" /></label><span>{{ interfaceRows.length }} 项业务接口</span></div>
          <section class="prototype-section work-table-section business-interface-table action-column-table"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>接口 / 业务场景</th><th>运行系统</th><th>提供方 / 调用方</th><th>接口规则</th><th>处理与保存</th><th>关联交换</th><th>运行控制</th><th>操作</th></tr></thead><tbody><tr v-for="item in interfacePagedRows" :key="item.id"><td><button class="work-row-link" @click="editInterface(item)">{{ item.name }}</button><small>{{ item.id }} · {{ item.domain }}</small></td><td><strong>{{ item.sourceSystem }}</strong><small>→ {{ item.targetSystem }}</small></td><td><strong>提供：{{ item.provider }}</strong><small>调用：{{ item.caller }}</small></td><td><strong>{{ item.channelType }}</strong><small>{{ item.tradeCode }} · {{ item.version }}</small></td><td><strong>{{ item.exchangeMode }}</strong><small>{{ item.retention }}</small></td><td><button class="interface-record-count" :disabled="!interfaceScenarioCount(item.id)" @click="editInterface(item, 'related')"><strong>{{ interfaceScenarioCount(item.id) }}</strong><span>笔记录</span></button></td><td><span class="prototype-tag" :class="interfaceStatusClass(item.status)">{{ item.status }}</span><small class="interface-runtime-note" :class="{ blocked: !interfaceAcceptsNew(item) }">{{ interfaceAcceptsNew(item) ? `允许新请求 · ${item.version}` : '阻止新请求' }}</small></td><td><button class="btn btn-outline-primary btn-sm" @click="editInterface(item)">查看详情</button></td></tr></tbody></table><div v-if="!interfaceRows.length" class="prototype-empty">没有符合条件的业务接口</div></div><AdminPagination :total="interfaceRows.length" :page="listPage" :page-size="listPageSize" @update:page="listPage = $event" @update:page-size="listPageSize = $event" /></section>
        </template>

        <template v-else-if="page === 'exchanges'">
          <div class="work-toolbar">
            <label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索交换记录" placeholder="业务单号、请求编号或接口" /></label>
            <select v-model="orgFilter" aria-label="机构筛选"><option value="全部机构">全部机构</option><option v-for="item in organizations" :key="item.id" :value="item.id">{{ item.name }}</option></select>
            <select v-model="statusFilter" aria-label="状态筛选"><option>全部状态</option><option v-for="state in ['结果未知', '明确未写入', '校验未通过', '查询失败', '越权拒绝', '已受理', '待发送', '处理成功', '重复已识别', '成功或重复已识别', '待核查']" :key="state">{{ state }}</option></select>
            <button v-if="activeFilterCount" class="prototype-text-button work-clear-filter" @click="clearListFilters"><X :size="14" />清空筛选（{{ activeFilterCount }}）</button><span>{{ rows.length }} 笔</span><button class="work-quiet-button" @click="descending = !descending"><ArrowDownUp :size="15" />{{ descending ? '最新优先' : '最早优先' }}</button>
          </div>
          <section class="prototype-section work-table-section action-column-table"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>业务单号 / 请求编号</th><th>机构</th><th>接口 / 业务域</th><th>受理时间</th><th>业务状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in exchangePagedRows" :key="item.id"><td><button class="work-row-link" @click="openScenario(item)">{{ item.sourceRecordId }}</button><small>{{ item.requestId }}</small></td><td>{{ item.organization }}</td><td>{{ item.interfaceCode }}<small>{{ item.domain }}</small></td><td>{{ item.occurredAt }}</td><td><span class="prototype-tag" :class="statusClass(item.status)">{{ item.status }}</span></td><td><button class="prototype-text-button" @click="openScenario(item)">详情</button></td></tr></tbody></table><div v-if="!(rows).length" class="prototype-empty">没有符合条件的记录</div></div><AdminPagination :total="rows.length" :page="listPage" :page-size="listPageSize" @update:page="listPage = $event" @update:page-size="listPageSize = $event" /></section>
        </template>

        <template v-else-if="page === 'alerts'">
          <div class="alert-filterbar">
            <label><span>问题类型</span><select v-model="alertTypeFilter" class="form-select"><option>全部问题</option><option v-for="name in [...new Set(alerts.map(item => item.type))]" :key="name">{{ name }}</option></select></label>
            <label><span>数据状态</span><select v-model="alertDataFilter" class="form-select"><option>全部数据状态</option><option>暂存可用</option><option>已到期</option><option>清理失败</option><option>仅保存交换记录</option></select></label>
            <label><span>责任人</span><select v-model="alertOwnerFilter" class="form-select"><option>全部责任人</option><option>未分配</option><option>当前运维</option></select></label>
            <label class="alert-date"><span>发生时间</span><div class="form-control">2026-09-01　~　2026-09-15</div></label>
            <button class="btn btn-outline-secondary" @click="alertTypeFilter = '全部问题'; alertDataFilter = '全部数据状态'; alertOwnerFilter = '全部责任人'">重置</button><button class="btn btn-primary">查询</button>
          </div>
          <section class="card alert-queue action-column-table"><div class="card-header"><h3 class="card-title">待处理工作队列</h3><span class="text-secondary ms-2">共 {{ alertRows.length }} 条</span><label class="alert-search ms-auto"><Search :size="16" /><input v-model="query" placeholder="搜索业务单号、接口或问题类型" /></label></div><div class="table-responsive"><table class="table table-vcenter"><thead><tr><th>优先级</th><th>业务对象</th><th>问题类型</th><th>影响结果</th><th>数据状态</th><th>责任人</th><th>等待时长</th><th>应处理时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in alertPagedRows" :key="item.id" :class="{ 'table-active-row': drawer === 'alert' && selectedId === item.id }"><td><span class="badge" :class="item.level === '高' ? 'bg-red-lt text-red' : item.level === '中' ? 'bg-yellow-lt text-yellow' : 'bg-secondary-lt'">{{ item.level }}</span></td><td><button class="data-record-link" @click="open('alert', item.id)">{{ item.relatedInterface?.name || item.relatedScenario?.sourceRecordId || item.object }}</button><small>{{ item.object }}<br />{{ item.relatedInterface ? `${item.affectedScenarios.length} 笔关联交换` : item.relatedScenario?.organization || item.id }}</small></td><td><span class="badge bg-red-lt text-red">{{ item.type }}</span></td><td>{{ item.businessResult }}</td><td><span class="badge bg-blue-lt text-blue">{{ item.dataStatus }}</span></td><td>{{ item.owner }}</td><td :class="{ 'text-red': item.level === '高' }">{{ item.wait }}</td><td>{{ item.dueAt }}</td><td><button class="btn btn-primary btn-sm" @click="open('alert', item.id)">{{ item.status === '已恢复' ? '查看详情' : '进入处置' }}</button></td></tr></tbody></table></div><AdminPagination :total="alertRows.length" :page="listPage" :page-size="listPageSize" @update:page="listPage = $event" @update:page-size="listPageSize = $event" /></section>
        </template>

        <template v-else-if="page === 'audit'">
          <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索审计记录" placeholder="操作者、动作、对象或变更内容" /></label><select v-model="auditActorFilter" aria-label="主体筛选"><option>全部主体</option><option>系统事件</option><option>人工操作</option></select><select v-model="auditActionFilter" aria-label="动作筛选"><option>全部动作</option><option>配置</option><option>核查</option><option>告警</option></select><span>{{ auditRows.length }} 条</span></div>
          <section class="prototype-section work-table-section"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>时间</th><th>操作主体</th><th>动作</th><th>对象</th><th>变更内容</th></tr></thead><tbody><tr v-for="(item, index) in auditPagedRows" :key="`${item.object}-${index}`"><td>{{ item.time }}</td><td>{{ item.actor }}</td><td>{{ item.action }}</td><td>{{ item.object }}</td><td>{{ item.detail || '—' }}</td></tr></tbody></table><div v-if="!auditRows.length" class="prototype-empty">没有符合条件的审计记录</div></div><AdminPagination :total="auditRows.length" :page="listPage" :page-size="listPageSize" @update:page="listPage = $event" @update:page-size="listPageSize = $event" /></section>
        </template>
      </div>
    </main>

    <div v-if="drawer && drawer !== 'alert'" class="work-drawer-backdrop" @click="drawer = null"></div>
    <aside v-if="drawer" class="work-drawer" :class="{ 'alert-workspace': drawer === 'alert', 'alert-incident-drawer': drawer === 'alert' && isInterfaceFailureAlert }" :role="drawer === 'alert' ? 'region' : 'dialog'" :aria-modal="drawer === 'alert' ? undefined : 'true'" aria-label="业务详情">
      <div class="work-drawer-header">
        <div><small>{{ drawer === 'scenario' ? '交换记录' : drawer === 'interface' ? '接口管理' : drawer === 'alert' ? '告警管理' : '机构管理' }}</small><h2>{{ drawerTitle }}</h2></div>
        <button class="prototype-icon" aria-label="关闭详情" title="关闭详情" @click="drawer = null"><X :size="19" /></button>
      </div>

      <template v-if="drawer === 'scenario' && selectedScenario">
        <div class="work-drawer-sub"><span class="prototype-tag" :class="statusClass(selectedScenario.status)">{{ selectedScenario.status }}</span><span>{{ selectedScenario.domain }} · {{ selectedScenario.interfaceCode }}</span></div>
        <div class="work-tabs"><button v-for="tab in [{ key: 'overview', label: '业务详情' }, { key: 'work', label: '异常处置' }, { key: 'trace', label: '处理轨迹' }]" :key="tab.key" :class="{ active: drawerTab === tab.key }" @click="drawerTab = tab.key as typeof drawerTab">{{ tab.label }}</button></div>
        <div class="work-drawer-body">
          <template v-if="drawerTab === 'overview'">
            <div v-if="selectedScenario.savedContent" class="work-inline-note">正文：{{ selectedScenario.savedContent }}。原处理结果仍保留。</div><div v-if="selectedScenario.status === '查询失败'" class="work-inline-note">{{ selectedScenario.question }}</div><div v-if="selectedRecovery" class="work-finding"><span>当前处置：{{ selectedRecovery.status }} · 数据：{{ selectedRecovery.dataStatus }}</span><button class="prototype-text-button" @click="openRecovery(selectedRecovery.id)">数据详情</button></div>
            <dl class="work-facts"><div><dt>机构</dt><dd>{{ selectedScenario.organization }}<small>{{ selectedScenario.organizationCode }}</small></dd></div><div><dt>业务类型</dt><dd>{{ selectedScenario.domain }}</dd></div><div><dt>源系统 / 目标系统</dt><dd>{{ selectedScenario.sourceSystem }} → {{ selectedScenario.targetSystem }}</dd></div><div><dt>源业务单号</dt><dd>{{ selectedScenario.sourceRecordId }}</dd></div><div><dt>患者标识</dt><dd>{{ selectedScenario.patientRef }}</dd></div><div><dt>请求编号</dt><dd>{{ selectedScenario.requestId }}</dd></div><div><dt>受理时间</dt><dd>{{ selectedScenario.occurredAt }}</dd></div><div><dt>回执 / 拦截原因</dt><dd>{{ selectedScenario.receipt }}</dd></div></dl>
            <div class="work-data-boundary"><ShieldCheck :size="17" /><span><strong>数据留存边界</strong><small>本页只展示交换追踪摘要和合成引用，不展示或保存完整请求、响应、患者身份信息及报告正文。</small></span></div>
            <div class="work-inline-actions"><button class="prototype-button" @click="drawerTab = 'trace'">查看处理轨迹</button><button v-if="queueStatuses.includes(selectedScenario.status)" class="work-quiet-button" @click="drawerTab = 'work'">进入处置</button></div>
          </template>
          <template v-else-if="drawerTab === 'trace'">
            <div class="work-section-label">状态时间线</div>
            <ol v-if="selectedRecovery?.history.length" class="work-timeline"><li v-for="(event, index) in selectedRecovery.history" :key="index"><span><Check :size="13" /></span><p><strong>{{ event.action }}</strong>{{ event.note }}<small>{{ event.time }} · {{ event.evidence }}</small></p></li></ol>
            <ol class="work-timeline"><li v-for="(step, index) in selectedScenario.steps" :key="step"><span>{{ index + 1 }}</span><p>{{ step }}</p></li></ol>
            <div class="work-inline-note">请求编号 {{ selectedScenario.requestId }} · 合成记录</div>
          </template>
          <template v-else-if="selectedScenario.status === '查询失败'"><div class="work-inline-note">{{ selectedScenario.receipt }}</div><p>{{ selectedScenario.question }}</p><button class="work-quiet-button" @click="drawerTab = 'trace'">查看查询过程</button></template><template v-else-if="selectedRecovery">
            <dl class="work-facts"><div><dt>当前业务处理</dt><dd>{{ selectedRecovery.status }}</dd></div><div><dt>数据保存状态</dt><dd>{{ selectedRecovery.dataStatus }}</dd></div></dl>
            <div class="work-inline-note">处理前需核对目标系统结果与暂存数据是否可用。</div>
            <button class="prototype-button" @click="openRecovery(selectedRecovery.id)">处理当前数据</button>
          </template>
          <template v-else>
            <div class="work-section-label">处置记录</div>
            <div v-if="findings[selectedScenario.id]" class="work-finding"><Check :size="16" /><span>{{ outcomeLabel(findings[selectedScenario.id].outcome) }} · {{ findings[selectedScenario.id].note }}<small v-if="findings[selectedScenario.id].evidence">依据编号：{{ findings[selectedScenario.id].evidence }}</small></span></div>
            <div v-if="!queueStatuses.includes(selectedScenario.status)" class="work-inline-note">当前记录无需人工异常处置，可查看回执与轨迹。</div>
            <template v-else-if="role === 'manager'"><div class="work-inline-note">当前为管理视角。异常核查由平台运维办理。</div><button class="prototype-button" @click="role = 'operator'">切换运维，办理此记录</button></template>
            <template v-else>
              <div class="work-assignee"><span>处理人</span><strong>{{ claimed.includes(selectedScenario.id) ? '当前运维' : '未领取' }}</strong><button v-if="!claimed.includes(selectedScenario.id)" class="prototype-button" @click="claim">领取</button></div>
              <div v-if="claimed.includes(selectedScenario.id)" class="work-form">
                <template v-if="selectedScenario.status === '结果未知'">
                  <label>目标端核查结果</label>
                  <div class="work-choice-group" role="group" aria-label="目标端核查结果"><button v-for="choice in [{ value: 'written', label: '已写入' }, { value: 'not-written', label: '未写入' }, { value: 'uncertain', label: '无法确认' }]" :key="choice.value" :class="{ active: outcome === choice.value }" :aria-pressed="outcome === choice.value" @click="outcome = choice.value as Outcome">{{ choice.label }}</button></div>
                  <div class="work-inline-note">{{ outcome === 'written' ? '登记对应回执，不再重复发送。' : outcome === 'not-written' ? '记录查询结果，进入后续补偿处理。' : '保持人工核查，不关闭差异或重复写入。' }}</div>
                  <label for="work-evidence">查询依据{{ outcome === 'uncertain' ? '（可选）' : '（必填）' }}</label><input id="work-evidence" v-model="evidence" maxlength="80" placeholder="填写回执编号或日志编号" />
                </template>
                <template v-else>
                  <label>处置动作</label><div class="work-choice-group" role="group" aria-label="处置动作"><button :class="{ active: handlingAction === 'revalidate' }" @click="handlingAction = 'revalidate'">重新校验</button><button :class="{ active: handlingAction === 'blocked' }" @click="handlingAction = 'blocked'">保持拦截</button></div>
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
        <div class="work-tabs"><button v-for="tab in [{ key: 'overview', label: '接口规则' }, { key: 'related', label: `关联交换 ${relatedInterfaceScenarios.length}` }, { key: 'work', label: '编码对应' }, { key: 'trace', label: '变更记录' }]" :key="tab.key" :class="{ active: drawerTab === tab.key }" @click="drawerTab = tab.key as DrawerTab">{{ tab.label }}</button></div>
        <div class="work-drawer-body">
          <template v-if="drawerTab === 'overview'">
            <div class="interface-direction-card"><span>{{ interfaceDraft.sourceSystem }}</span><ArrowLeftRight :size="17" /><strong>中间接口平台</strong><ArrowLeftRight :size="17" /><span>{{ interfaceDraft.targetSystem }}</span></div>
            <div class="work-section-label">运行角色</div>
            <dl class="work-facts interface-facts"><div><dt>接口提供方</dt><dd>{{ interfaceDraft.provider }}</dd></div><div><dt>接口调用方</dt><dd>{{ interfaceDraft.caller }}</dd></div><div><dt>源系统</dt><dd>{{ interfaceDraft.sourceSystem }}</dd></div><div><dt>目标系统</dt><dd>{{ interfaceDraft.targetSystem }}</dd></div></dl>
            <div class="work-section-label work-spaced-label">接口与数据边界</div>
            <div class="work-form work-form-compact interface-rule-form">
              <label for="intf-name">接口名称</label><input id="intf-name" v-model="interfaceDraft.name" maxlength="80" />
              <label for="intf-provider">提供方</label><input id="intf-provider" v-model="interfaceDraft.provider" maxlength="80" />
              <label for="intf-caller">调用方</label><input id="intf-caller" v-model="interfaceDraft.caller" maxlength="80" />
              <label for="intf-source">源系统</label><input id="intf-source" v-model="interfaceDraft.sourceSystem" maxlength="80" />
              <label for="intf-target">目标系统</label><input id="intf-target" v-model="interfaceDraft.targetSystem" maxlength="80" />
              <label for="intf-type">交互类型</label><select id="intf-type" v-model="interfaceDraft.channelType"><option>PHIS_Interface</option><option>回写接口</option><option>约定页面</option></select>
              <label for="intf-code">交易码或页面标识</label><input id="intf-code" v-model="interfaceDraft.tradeCode" maxlength="32" />
              <label for="intf-version">规则版本</label><input id="intf-version" v-model="interfaceDraft.version" maxlength="30" />
              <label for="intf-auth">鉴权方式</label><input id="intf-auth" v-model="interfaceDraft.auth" maxlength="100" />
              <label for="intf-scope">数据范围</label><textarea id="intf-scope" v-model="interfaceDraft.dataScope" maxlength="200" rows="2"></textarea>
              <label for="intf-mode">处理方式</label><input id="intf-mode" v-model="interfaceDraft.exchangeMode" maxlength="50" />
              <label for="intf-retention">数据保存策略</label><textarea id="intf-retention" v-model="interfaceDraft.retention" maxlength="200" rows="2"></textarea>
              <label>接口状态</label><div class="work-choice-group interface-status-group" role="group" aria-label="接口状态"><button v-for="state in interfaceStatuses" :key="state" :class="{ active: interfaceDraft.status === state }" @click="interfaceDraft.status = state">{{ state }}</button></div>
              <div class="work-data-boundary"><ShieldCheck :size="17" /><span><strong>{{ interfaceDraft.retention }}</strong><small>业务写入必须走正式接口。页面和接口均需分别确认鉴权、最小字段、成功条件、超时、重试和数据到期规则。</small></span></div>
            </div>
          </template>
          <template v-else-if="drawerTab === 'related'">
            <section class="interface-runtime-card" :class="{ blocked: !interfaceAcceptsNew(selectedInterface) }">
              <div><span>新请求准入</span><strong>{{ interfaceAcceptsNew(selectedInterface) ? '允许创建交换记录' : '阻止新请求' }}</strong><small>{{ interfaceAcceptsNew(selectedInterface) ? `采用当前规则 ${selectedInterface.version}` : `当前状态：${selectedInterface.status}` }}</small></div>
              <dl><div><dt>历史交换</dt><dd>{{ relatedInterfaceScenarios.length }} 笔</dd></div><div><dt>版本不同</dt><dd>{{ interfaceSnapshotMismatchCount(selectedInterface.id) }} 笔</dd></div></dl>
              <button class="btn btn-outline-primary btn-sm" @click="runIntakeCheck">检查新请求</button>
            </section>
            <div v-if="intakeCheckResult" class="work-inline-note interface-intake-result"><Check :size="15" />{{ intakeCheckResult }}</div>
            <div class="prototype-section-head work-embedded-head"><div><h3>关联交换记录</h3><small>按交易码关联；历史记录显示受理时规则版本。</small></div><span>{{ relatedInterfaceScenarios.length }} 笔</span></div>
            <div v-if="!relatedInterfaceScenarios.length" class="prototype-empty">暂无使用该接口的交换记录</div>
            <div v-else class="interface-related-records"><button v-for="item in relatedInterfaceScenarios" :key="item.id" @click="openRelatedInterfaceScenario(item)"><span><strong>{{ item.sourceRecordId }}</strong><small>{{ item.requestId }} · {{ item.organization }}</small></span><span><em>{{ item.status }}</em><small>{{ item.interfaceVersion }}<template v-if="item.interfaceVersion !== selectedInterface.version"> · 与当前版本不同</template><template v-else> · 当前版本</template><br />{{ item.occurredAt }}</small></span><ArrowUpRight :size="15" /></button></div>
            <div class="work-data-boundary"><ShieldCheck :size="17" /><span><strong>历史记录采用规则快照</strong><small>修改当前接口定义不会覆盖既有交换记录保存的接口名称、交易码和规则版本。</small></span></div>
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
          <div v-if="drawerTab === 'overview' || drawerTab === 'work'" class="work-save-area"><label for="intf-reason">变更依据（必填）</label><textarea id="intf-reason" v-model="interfaceChangeReason" maxlength="200" rows="3" placeholder="说明本次配置变更依据"></textarea><button class="prototype-button" :disabled="!interfaceChangeReason.trim()" @click="saveInterface">保存接口配置</button></div>
        </div>
      </template>

      <template v-else-if="drawer === 'alert' && selectedAlert && selectedAlertRow">
        <div class="work-drawer-sub"><span class="badge" :class="selectedAlert.level === '高' ? 'bg-red-lt text-red' : 'bg-yellow-lt text-yellow'">{{ selectedAlert.level }}优先级</span><span>{{ selectedAlert.status }} · {{ selectedAlert.id }}</span></div>
        <div v-if="isInterfaceFailureAlert" class="incident-summary">
          <div><span>接口</span><strong>{{ selectedAlertRow.relatedInterface?.name }} · {{ selectedAlert.object }}</strong></div><div><span>首次失败</span><strong>{{ selectedAlert.occurredAt.slice(0, 16) }}</strong></div><div><span>最近失败</span><strong>2026-09-15 10:53</strong></div><div><span>连续失败</span><strong>12 次</strong></div><div><span>影响机构</span><strong>{{ interfaceIncidentOrganizations }} 家</strong></div><div><span>受影响记录</span><strong>{{ interfaceIncidentScenarios.length }} 笔</strong></div><div><span>责任人</span><strong>{{ selectedAlert.status === '待确认' ? '未分配' : '张建国' }}</strong></div>
        </div>
        <div class="work-tabs" role="tablist"><button :class="{ active: drawerTab === 'overview' }" role="tab" :aria-selected="drawerTab === 'overview'" @click="drawerTab = 'overview'">处理详情</button><button :class="{ active: drawerTab === 'work' }" role="tab" :aria-selected="drawerTab === 'work'" @click="drawerTab = 'work'">{{ isInterfaceFailureAlert ? `受影响记录 ${interfaceIncidentScenarios.length}` : '处理记录' }}</button><button :class="{ active: drawerTab === 'trace' }" role="tab" :aria-selected="drawerTab === 'trace'" @click="drawerTab = 'trace'">相关日志</button></div>
        <div v-if="drawerTab === 'overview' && isInterfaceFailureAlert" class="work-drawer-body incident-body">
          <section class="incident-control"><div><h3>运行控制</h3><dl><div><dt>新请求</dt><dd :class="alertRequestsLimited ? 'danger' : 'success'">{{ alertRequestsLimited ? '已限制' : '已恢复' }}</dd></div><div><dt>自动重试</dt><dd :class="alertRetryPaused ? 'danger' : 'success'">{{ alertRetryPaused ? '已暂停' : '已恢复' }}</dd></div><div><dt>正在发送</dt><dd>0</dd></div><div><dt>控制原因</dt><dd>连续失败达到阈值</dd></div></dl></div><div class="incident-control-actions"><button class="btn btn-outline-secondary btn-sm" @click="drawerTab = 'work'">查看受影响记录</button><button class="btn btn-outline-danger btn-sm" @click="updateIncidentControl">调整运行控制</button></div></section>
          <div class="incident-work-grid">
            <section class="incident-panel incident-diagnosis"><header><h3>故障诊断</h3><div><button class="btn btn-outline-secondary btn-sm" @click="toast = '已重新检查连接状态（演示）'"><RefreshCw :size="14" />重新检查</button><button class="btn btn-outline-secondary btn-sm" @click="drawerTab = 'trace'"><FileSearch :size="14" />查看错误日志</button></div></header><dl><div><dt>目标系统状态</dt><dd class="danger">服务不可用</dd><span>目标接口服务无响应</span></div><div><dt>网络连通性</dt><dd class="success">通过</dd><span>与目标 IP 网络连接正常</span></div><div><dt>证书状态</dt><dd class="success">通过</dd><span>证书在有效期内</span></div><div><dt>目标服务</dt><dd class="danger">失败</dd><span>TCP 连接被拒绝</span></div><div><dt>最近错误</dt><dd>Connection refused</dd><span>2026-09-15 10:26:17</span></div><div><dt>初步原因</dt><dd>目标接口服务停止</dd><span>等待对方运维确认服务状态</span></div></dl></section>
            <section class="incident-panel incident-verification"><header><h3>恢复验证</h3></header><ol><li class="done"><span><Check :size="14" /></span><div><strong>网络连通性检查</strong><small>与目标 IP 网络连通</small></div><em>已通过</em></li><li :class="{ done: alertProbeState === '已通过' }"><span>{{ alertProbeState === '已通过' ? '✓' : '2' }}</span><div><strong>接口探测</strong><small>发送探测请求验证接口可用性</small></div><em>{{ alertProbeState }}</em></li><li :class="{ done: alertValidationState === '已通过' }"><span>{{ alertValidationState === '已通过' ? '✓' : '3' }}</span><div><strong>受控验证请求</strong><small>选择一笔明确未发送记录验证完整流程</small></div><em>{{ alertValidationState }}</em></li></ol><div class="incident-verification-actions"><button class="btn btn-primary" :disabled="alertValidationState === '已通过'" @click="runInterfaceProbe">{{ alertProbeState === '待执行' ? '执行接口探测' : alertValidationState === '未创建' ? '执行受控验证' : '验证已通过' }}</button><button class="btn btn-outline-primary" :disabled="alertValidationState !== '已通过' || !alertRequestsLimited" @click="resumeIncidentRequests">{{ alertRequestsLimited ? '恢复新请求' : '新请求已恢复' }}</button></div><small class="incident-action-note">完成受控验证后方可恢复；历史记录不会自动重发。</small></section>
          </div>
          <section class="incident-routing"><h3>受影响记录分流</h3><div class="incident-counts"><button @click="drawerTab = 'work'"><span>明确未发送</span><strong>{{ interfaceIncidentCounts.notSent }}</strong><small>可进入受控重试</small></button><button class="warning" @click="drawerTab = 'work'"><span>结果未知</span><strong>{{ interfaceIncidentCounts.unknown }}</strong><small>需核查目标结果</small></button><button class="danger" @click="drawerTab = 'work'"><span>校验未通过</span><strong>{{ interfaceIncidentCounts.invalid }}</strong><small>来源修正后重新校验</small></button><button class="success" @click="drawerTab = 'work'"><span>已确认写入</span><strong>{{ interfaceIncidentCounts.written }}</strong><small>禁止再次发送</small></button></div><div class="table-responsive"><table class="table table-sm incident-record-table"><thead><tr><th>业务记录</th><th>当前结果</th><th>失败位置</th><th>处理路径</th><th>操作</th></tr></thead><tbody><tr v-for="row in interfaceIncidentRoutes" :key="row.item.id"><td><strong>{{ row.item.sourceRecordId }}</strong><small>{{ row.item.requestId }}</small></td><td><span class="prototype-tag" :class="row.tone">{{ row.result }}</span></td><td>{{ row.stage }}</td><td>{{ row.route }}</td><td><button class="btn btn-link btn-sm" @click="openRelatedInterfaceScenario(row.item)">{{ row.action }}</button></td></tr></tbody></table></div></section>
          <section class="incident-followup"><h3>遗留处理</h3><div class="incident-followup-fields"><label>后续责任人<select v-model="alertFollowupOwner" class="form-select"><option>李强</option><option>张建国</option><option>当前运维</option></select></label><label>完成期限<input v-model="alertDueAt" class="form-control" /></label><label>下次检查<input v-model="alertNextCheckAt" class="form-control" /></label><label class="incident-note">备注说明<input v-model="alertNote" class="form-control" maxlength="200" placeholder="记录对方处理进展、验证依据或其他说明" /></label></div><footer><span>仍有 {{ interfaceIncidentCounts.unknown }} 笔结果未知，不能完成告警处理。</span><div><button class="btn btn-outline-secondary" @click="saveIncidentProgress(false)">保存处理进展</button><button class="btn btn-primary" @click="saveIncidentProgress(true)">转入持续观察</button><button class="btn btn-secondary" disabled>完成告警处理</button></div></footer></section>
          <div v-if="role === 'manager'" class="work-inline-note">管理角色可查看完整处置依据；运行控制、接口探测和状态登记由运维角色执行。</div>
        </div>
        <div v-else-if="drawerTab === 'overview'" class="work-drawer-body alert-process"><div class="work-section-label">基本信息</div><dl class="work-facts"><div><dt>问题类型</dt><dd>{{ selectedAlert.type }}</dd></div><div><dt>业务对象</dt><dd>{{ selectedAlertRow.relatedInterface?.name || selectedAlertRow.relatedScenario?.organization || selectedAlert.object }}</dd></div><div><dt>影响结果</dt><dd>{{ selectedAlertRow.businessResult }}</dd></div><div><dt>当前责任人</dt><dd>{{ selectedAlertRow.owner }}</dd></div><div><dt>数据状态</dt><dd>{{ selectedAlertRow.dataStatus }}</dd></div><div><dt>等待时长</dt><dd class="text-red">{{ selectedAlertRow.wait }}</dd></div><div><dt>发生时间</dt><dd>{{ selectedAlert.occurredAt }}</dd></div><div><dt>应处理时间</dt><dd>{{ selectedAlertRow.dueAt }}</dd></div></dl><div class="work-section-label work-spaced-label">处理步骤</div><ol class="alert-steps"><li v-for="(step, index) in selectedAlertSteps" :key="step[0]" :class="{ done: index === 0 || index === 1 && selectedAlert.status !== '待确认' }"><span>{{ index + 1 }}</span><div><strong>{{ step[0] }}</strong><p>{{ step[1] }}</p></div><em v-if="index === 0">已确认</em><button v-else-if="index === 1 && selectedAlert.status === '待确认' && role === 'operator'" class="btn btn-primary btn-sm" @click="updateAlert('处理中')">确认并领取</button></li></ol><div class="alert alert-info">{{ selectedAlert.type.includes('到期') || selectedAlert.type.includes('清理') ? '清理操作只删除临时数据，不会改变业务结果；业务结果仍需由业务方根据实际情况确认。' : '处置告警只更新运行处理进度；业务结果必须依据目标系统回执或有效查询证据单独确认。' }}</div><button v-if="selectedAlertRow.relatedTask" class="btn btn-primary" @click="openRecovery(selectedAlertRow.relatedTask.id)">进入关联数据处理</button><div v-if="selectedAlert.note" class="work-finding"><Check :size="16" /><span>{{ selectedAlert.note }}</span></div><div v-if="role === 'manager'" class="work-inline-note">管理角色只读，告警确认和恢复由运维角色处理。</div><div v-else-if="selectedAlert.status !== '已恢复'" class="work-form"><label for="alert-note">处理备注</label><textarea id="alert-note" v-model="alertNote" rows="3" maxlength="200" placeholder="记录核查证据和后续安排"></textarea><button class="btn btn-outline-secondary" :disabled="!alertNote.trim()" @click="updateAlert('已恢复')">保存并关闭</button></div></div>
        <div v-else-if="drawerTab === 'work' && isInterfaceFailureAlert" class="work-drawer-body incident-records-tab"><div class="prototype-section-head work-embedded-head"><div><h3>受影响交换记录</h3><small>接口恢复与历史记录处理相互独立；每笔记录按结果进入对应路径。</small></div><span>{{ interfaceIncidentScenarios.length }} 笔</span></div><div class="table-responsive"><table class="table incident-record-table"><thead><tr><th>业务记录</th><th>机构</th><th>当前结果</th><th>失败位置</th><th>处理路径</th><th>操作</th></tr></thead><tbody><tr v-for="row in interfaceIncidentRoutes" :key="row.item.id"><td><strong>{{ row.item.sourceRecordId }}</strong><small>{{ row.item.requestId }}</small></td><td>{{ row.item.organization }}</td><td><span class="prototype-tag" :class="row.tone">{{ row.result }}</span></td><td>{{ row.stage }}</td><td>{{ row.route }}</td><td><button class="btn btn-outline-primary btn-sm" @click="openRelatedInterfaceScenario(row.item)">{{ row.action }}</button></td></tr></tbody></table></div></div>
        <div v-else-if="drawerTab === 'work'" class="work-drawer-body"><div class="work-section-label">处理记录</div><ol class="work-timeline"><li><span><Check :size="13" /></span><p><strong>系统生成待处理事项</strong>{{ selectedAlert.type }}<small>{{ selectedAlert.occurredAt }} · {{ selectedAlert.id }}</small></p></li><li v-if="selectedAlert.status !== '待确认'"><span><Check :size="13" /></span><p><strong>运维已确认</strong>{{ selectedAlert.note || '已领取并开始处理' }}<small>当前运维 · {{ selectedAlert.status }}</small></p></li></ol></div>
        <div v-else class="work-drawer-body"><div class="work-section-label">相关日志</div><dl class="work-facts"><div><dt>告警编号</dt><dd>{{ selectedAlert.id }}</dd></div><div><dt>关联对象</dt><dd>{{ selectedAlert.object }}</dd></div><div><dt>错误摘要</dt><dd>{{ selectedAlert.note || selectedAlert.type }}</dd></div><div><dt>数据范围</dt><dd>仅显示合成交换摘要</dd></div></dl><button v-if="selectedAlertRow.relatedInterface && role === 'manager'" class="work-related-link" @click="openInterfaceDefinition(selectedAlertRow.relatedInterface.id)">查看接口定义 <ArrowUpRight :size="14" /></button><button v-else-if="selectedAlertRow.relatedScenario" class="work-related-link" @click="showRelated(selectedAlertRow.relatedScenario.id)">查看关联发送过程 <ArrowUpRight :size="14" /></button></div>
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
