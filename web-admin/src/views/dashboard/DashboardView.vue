<!-- 正式运行总览页面：聚合当前用户有权读取的管理数据并提供业务下钻入口。 -->
<script setup lang="ts">
import { AlertCircle, BookOpen, CheckCircle2, ClipboardList, RefreshCw, Settings2 } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { listManagementAuditEvents } from '@/api/system/audit'
import type { ManagementAuditEvent } from '@/api/system/audit'
import { listDictionaryTypes, listParameterDefinitions, listParameterValues } from '@/api/system/configuration'
import { isBackendAvailable } from '@/api/health'
import { listOrganizations } from '@/api/system/organization'
import { listRoles } from '@/api/system/role'
import { listUsers } from '@/api/system/user'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'
import { auditActionLabel, auditTargetLabel, formatLocalDateTime } from '@/utils/managementDisplay'

type HealthState = 'checking' | 'available' | 'unavailable'
type SummaryMetric = { label: string; value: number | null; detail: string; to: string; permission: string }

const router = useRouter()
const health = ref<HealthState>('checking')
const checkedAt = ref('')
const isLoading = ref(true)
const failureCount = ref(0)
const failureLabels = ref<string[]>([])
const enabledOrganizationCount = ref<number | null>(null)
const enabledUserCount = ref<number | null>(null)
const enabledRoleCount = ref<number | null>(null)
const configuredParameterCount = ref<number | null>(null)
const parameterDefinitionCount = ref<number | null>(null)
const dictionaryTypeCount = ref<number | null>(null)
const recentAuditEvents = ref<ManagementAuditEvent[]>([])
const organizationAttentionCount = ref<number | null>(null)
const userAttentionCount = ref<number | null>(null)
const roleAttentionCount = ref<number | null>(null)
const missingParameterCount = ref<number | null>(null)
let dashboardController: AbortController | null = null
let mounted = true

const metrics = computed<SummaryMetric[]>(() => [
  { label: '机构需要处理', value: organizationAttentionCount.value, detail: enabledOrganizationCount.value === null ? '读取机构状态' : `${enabledOrganizationCount.value} 个机构处于启用状态`, to: '/organizations?status=attention', permission: 'organization:read' },
  { label: '用户需要处理', value: userAttentionCount.value, detail: enabledUserCount.value === null ? '读取用户状态' : `${enabledUserCount.value} 个用户处于启用状态`, to: '/users?attention=needsAttention', permission: 'identity:read' },
  { label: '角色需要处理', value: roleAttentionCount.value, detail: enabledRoleCount.value === null ? '读取角色状态' : `${enabledRoleCount.value} 个角色处于启用状态`, to: '/roles?attention=needsAttention', permission: 'access:read' },
  { label: '参数尚未配置', value: missingParameterCount.value, detail: parameterDefinitionCount.value === null ? '读取参数配置' : `${configuredParameterCount.value ?? 0} 项配置已启用`, to: '/parameters?status=missing', permission: 'configuration:read' },
].filter(metric => hasPermission(metric.permission)))

const canReadConfiguration = computed(() => hasPermission('configuration:read'))
const canReadAudit = computed(() => hasPermission('audit:read'))
const recentFailureCount = computed(() => recentAuditEvents.value.filter(event => event.resultCode === 'FAILURE').length)

function asApiError(caught: unknown) {
  return caught instanceof ApiClientError ? caught : new ApiClientError('UNKNOWN_ERROR', '管理数据读取失败', 0)
}

async function loadDashboard() {
  dashboardController?.abort()
  const controller = new AbortController()
  dashboardController = controller
  isLoading.value = true
  failureCount.value = 0
  failureLabels.value = []
  enabledOrganizationCount.value = null
  enabledUserCount.value = null
  enabledRoleCount.value = null
  configuredParameterCount.value = null
  parameterDefinitionCount.value = null
  dictionaryTypeCount.value = null
  organizationAttentionCount.value = null
  userAttentionCount.value = null
  roleAttentionCount.value = null
  missingParameterCount.value = null
  recentAuditEvents.value = []
  health.value = 'checking'

  const tasks: Array<{ label: string; run: () => Promise<void> }> = [{ label: '后台连接', run: async () => {
    health.value = await isBackendAvailable(controller.signal) ? 'available' : 'unavailable'
  } }]
  if (hasPermission('organization:read')) tasks.push({ label: '机构状态', run: async () => {
    const rows = await listOrganizations(true, controller.signal)
    enabledOrganizationCount.value = rows.filter(item => item.enabled).length
    const now = Date.now()
    organizationAttentionCount.value = rows.filter(item => {
      if (!item.enabled) return false
      const from = item.validFrom ? new Date(`${item.validFrom}${/[zZ]|[+-]\d\d:\d\d$/.test(item.validFrom) ? '' : 'Z'}`).getTime() : null
      const to = item.validTo ? new Date(`${item.validTo}${/[zZ]|[+-]\d\d:\d\d$/.test(item.validTo) ? '' : 'Z'}`).getTime() : null
      return (from !== null && from > now) || (to !== null && to < now)
    }).length
  } })
  if (hasPermission('identity:read')) tasks.push({ label: '用户状态', run: async () => {
    const rows = await listUsers(controller.signal)
    enabledUserCount.value = rows.filter(item => item.enabled).length
    userAttentionCount.value = rows.filter(item => item.enabled && (item.mustChangePassword || item.roleIds.length === 0 || !item.organizationScopeIds.includes(item.primaryOrganizationId))).length
  } })
  if (hasPermission('access:read')) tasks.push({ label: '角色权限', run: async () => {
    const rows = await listRoles(controller.signal)
    enabledRoleCount.value = rows.filter(item => item.enabled).length
    roleAttentionCount.value = rows.filter(item => item.enabled && item.permissionCodes.length === 0).length
  } })
  if (hasPermission('configuration:read')) tasks.push({ label: '参数与字典', run: async () => {
    const [definitions, values, dictionaries] = await Promise.all([
      listParameterDefinitions(controller.signal), listParameterValues(controller.signal), listDictionaryTypes(controller.signal),
    ])
    parameterDefinitionCount.value = definitions.length
    const applicableValues = values.filter(value => {
      const definition = definitions.find(item => item.key === value.parameterKey)
      if (!definition) return false
      const expectedOrganizationId = definition.organizationScoped ? authState.user?.primaryOrganizationId ?? null : null
      return value.organizationId === expectedOrganizationId
    })
    configuredParameterCount.value = applicableValues.filter(item => item.configured && item.enabled).length
    missingParameterCount.value = definitions.reduce((count, definition) => count + definition.environments.filter(environment => {
      const expectedOrganizationId = definition.organizationScoped ? authState.user?.primaryOrganizationId ?? null : null
      return !applicableValues.some(value => value.parameterKey === definition.key
        && value.environment === environment && value.organizationId === expectedOrganizationId && value.configured)
    }).length, 0)
    dictionaryTypeCount.value = dictionaries.filter(item => item.enabled).length
  } })
  if (hasPermission('audit:read')) tasks.push({ label: '最近管理操作', run: async () => {
    recentAuditEvents.value = await listManagementAuditEvents({ limit: 5 }, controller.signal)
  } })

  const results = await Promise.allSettled(tasks.map(task => task.run()))
  if (!mounted || controller.signal.aborted) return
  const failures = results.filter((result): result is PromiseRejectedResult => result.status === 'rejected')
  const unauthorized = failures.map(result => asApiError(result.reason)).find(error => error.status === 401)
  if (unauthorized) {
    authState.user = null
    authState.isInitialized = true
    await router.replace({ path: '/login', query: { redirect: '/' } })
    return
  }
  failureCount.value = failures.filter(result => asApiError(result.reason).code !== 'REQUEST_ABORTED').length
  failureLabels.value = results.flatMap((result, index) => result.status === 'rejected' && asApiError(result.reason).code !== 'REQUEST_ABORTED' ? [tasks[index]!.label] : [])
  if (health.value === 'checking') health.value = 'unavailable'
  checkedAt.value = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date())
  isLoading.value = false
}

onMounted(() => loadDashboard())
onBeforeUnmount(() => {
  mounted = false
  dashboardController?.abort()
})
</script>

<template>
  <section class="content dashboard-page">
    <div class="overview-commandbar">
      <div><strong>{{ health === 'checking' ? '正在读取管理概况' : health === 'available' ? '后台服务可连接' : '后台服务连接失败' }}</strong><small>{{ checkedAt ? `最近检查 ${checkedAt}；业务是否正常请查看下方待处理事项` : '正在检查后台服务与业务数据' }}</small></div>
      <div class="overview-commandbar-actions"><span class="prototype-tag overview-health-tag" :class="health === 'available' ? 'success' : health === 'unavailable' ? 'danger' : 'neutral'">{{ health === 'available' ? '服务可用' : health === 'unavailable' ? '连接失败' : '检查中' }}</span><button class="work-quiet-button overview-refresh" type="button" title="重新检查" :disabled="isLoading" @click="loadDashboard"><RefreshCw :size="14" :class="{ spinning: isLoading }" />刷新</button></div>
    </div>

    <div v-if="failureCount" class="dashboard-warning" role="alert"><AlertCircle :size="18" /><span><strong>部分管理概况暂时无法读取</strong><small>未能读取：{{ failureLabels.join('、') }}。已有数据仍可使用，可稍后刷新。</small></span></div>

    <section v-if="metrics.length" class="overview-kpis" aria-label="管理关键指标">
      <RouterLink v-for="metric in metrics" :key="metric.to" :to="metric.to"><span>{{ metric.label }}</span><strong>{{ metric.value ?? '—' }}</strong><small>{{ metric.value === null ? '读取失败或尚未返回' : metric.detail }}</small></RouterLink>
    </section>

    <div class="overview-work-grid">
      <section class="card overview-work-card dashboard-audit-card">
        <div class="card-header"><div><h2 class="card-title">最近管理操作</h2><small>最近记录中有 {{ recentFailureCount }} 次失败；点击可查看具体原因</small></div><div class="audit-card-actions"><RouterLink v-if="canReadAudit && recentFailureCount" class="work-quiet-button" to="/audit?resultCode=FAILURE">只看失败</RouterLink><RouterLink v-if="canReadAudit" class="work-quiet-button" to="/audit">查看全部</RouterLink></div></div>
        <div v-if="isLoading && canReadAudit" class="empty-state"><RefreshCw class="spinning" :size="27" /><strong>正在读取审计记录</strong></div>
        <div v-else-if="!canReadAudit" class="empty-state"><ClipboardList :size="27" /><strong>当前账号无审计读取权限</strong></div>
        <div v-else-if="recentAuditEvents.length === 0" class="empty-state"><CheckCircle2 :size="27" /><strong>当前范围内暂无管理操作</strong></div>
        <ol v-else class="dashboard-audit-list"><li v-for="event in recentAuditEvents" :key="event.id"><span class="prototype-tag" :class="event.resultCode === 'SUCCESS' ? 'success' : 'danger'">{{ event.resultCode === 'SUCCESS' ? '成功' : '失败' }}</span><div><strong>{{ auditActionLabel(event.actionCode) }}</strong><p>{{ event.changeSummary }}</p><small>{{ auditTargetLabel(event.targetType) }} · {{ event.targetId }} · {{ event.actorLogin }} · {{ formatLocalDateTime(event.occurredAt) }}</small></div></li></ol>
      </section>

      <section class="card overview-work-card dashboard-shortcuts">
        <div class="card-header"><div><h2 class="card-title">基础配置</h2><small>查看参数和字典的当前配置情况</small></div></div>
        <div v-if="canReadConfiguration" class="shortcut-grid"><RouterLink to="/parameters"><Settings2 :size="22" /><span><strong>参数配置</strong><small>{{ configuredParameterCount ?? '—' }} 个启用当前适用范围的值</small></span></RouterLink><RouterLink to="/dictionaries"><BookOpen :size="22" /><span><strong>数据字典</strong><small>{{ dictionaryTypeCount ?? '—' }} 个启用类型</small></span></RouterLink></div>
        <div v-else class="empty-state"><Settings2 :size="27" /><strong>当前账号无配置读取权限</strong></div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.dashboard-page { display: grid; gap: 14px; }
.overview-kpis { gap: 0; overflow: hidden; border: 1px solid #dfe5e8; border-radius: 4px; background: #fff; }
.overview-kpis > a { min-width: 0; min-height: 108px; padding: 17px 22px; border-right: 1px solid #e3e8eb; color: #31434f; display: flex; flex-direction: column; align-items: flex-start; justify-content: center; text-decoration: none; }
.overview-kpis > a:last-child { border-right: 0; }
.overview-kpis > a:hover { background: #f7fbfa; }
.overview-kpis > a:focus-visible { outline: 2px solid #187a6d; outline-offset: -3px; }
.overview-kpis span { color: #667482; font-size: 11px; font-weight: 600; }
.overview-kpis strong { margin-top: 8px; color: #273746; font-size: 28px; font-weight: 650; line-height: 1; }
.overview-kpis small { margin-top: 8px; color: #84909b; font-size: 10px; }
.dashboard-warning { min-height: 54px; padding: 10px 13px; border: 1px solid #e6cf9d; border-radius: 5px; background: #fff9e9; color: #7b5c1f; display: flex; align-items: center; gap: 10px; }
.dashboard-warning span { display: grid; gap: 2px; }
.dashboard-warning small { font-size: 11px; }
.overview-work-card .card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.audit-card-actions { display: flex; gap: 7px; }
.dashboard-audit-list { margin: 0; padding: 0; list-style: none; }
.dashboard-audit-list li { padding: 13px 16px; border-top: 1px solid #e4e9ea; display: flex; align-items: flex-start; gap: 10px; }
.dashboard-audit-list div { min-width: 0; }
.dashboard-audit-list strong, .dashboard-audit-list small { display: block; }
.dashboard-audit-list p { margin: 3px 0; color: #53636d; font-size: 12px; }
.dashboard-audit-list small { color: #7c8991; font-size: 10px; }
.shortcut-grid { padding: 16px; display: grid; gap: 10px; }
.shortcut-grid a { min-height: 76px; padding: 14px; border: 1px solid #dce4e6; border-radius: 5px; color: #344853; display: flex; align-items: center; gap: 12px; text-decoration: none; }
.shortcut-grid a:hover { border-color: #8fc3b5; background: #f3faf8; }
.shortcut-grid svg { color: #187a6d; }
.shortcut-grid strong, .shortcut-grid small { display: block; }
.shortcut-grid small { margin-top: 4px; color: #77858d; font-size: 11px; }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spinning { animation: none; } }
@media (max-width: 1050px) {
  .overview-kpis > a { border-bottom: 1px solid #e3e8eb; }
  .overview-kpis > a:nth-child(2n) { border-right: 0; }
  .overview-kpis > a:nth-last-child(-n + 2) { border-bottom: 0; }
}
@media (max-width: 480px) {
  .overview-kpis > a { min-height: 92px; border-right: 0; }
  .overview-kpis > a:nth-last-child(2) { border-bottom: 1px solid #e3e8eb; }
  .overview-kpis > a:last-child { border-bottom: 0; }
}
</style>
