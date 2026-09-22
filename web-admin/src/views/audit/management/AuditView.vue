<!-- 管理审计页面：按当前用户可访问的机构查询并展示后端已删除或遮盖敏感字段的管理操作记录。 -->
<script setup lang="ts">
import { AlertCircle, ClipboardList, LoaderCircle, RefreshCw, Search, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listManagementAuditEvents } from '@/api/system/audit'
import type { ManagementAuditEvent } from '@/api/system/audit'
import AdminPagination from '@/components/AdminPagination.vue'
import ListQueryToolbar from '@/components/ListQueryToolbar.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { useModalDialog } from '@/composables/useModalDialog'
import { authState } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'
import {
  AUDIT_ACTION_LABELS, AUDIT_TARGET_LABELS, auditActionLabel, auditTargetLabel, formatLocalDateTime,
} from '@/utils/managementDisplay'

const router = useRouter()
const route = useRoute()
const events = ref<ManagementAuditEvent[]>([])
const targetType = ref(typeof route.query.targetType === 'string' ? route.query.targetType : '')
const targetId = ref(typeof route.query.targetId === 'string' ? route.query.targetId : '')
const requestId = ref(typeof route.query.requestId === 'string' ? route.query.requestId : '')
const actorLogin = ref(typeof route.query.actorLogin === 'string' ? route.query.actorLogin : '')
const actionCode = ref(typeof route.query.actionCode === 'string' ? route.query.actionCode : '')
const resultCode = ref<'SUCCESS' | 'FAILURE' | ''>(route.query.resultCode === 'SUCCESS' || route.query.resultCode === 'FAILURE' ? route.query.resultCode : '')
const occurredFrom = ref(typeof route.query.occurredFrom === 'string' ? route.query.occurredFrom : '')
const occurredTo = ref(typeof route.query.occurredTo === 'string' ? route.query.occurredTo : '')
const limit = ref(100)
const isLoading = ref(true)
const isLoadingMore = ref(false)
const hasMore = ref(false)
const error = ref<ApiClientError | null>(null)
const filterError = ref('')
const selectedEvent = ref<ManagementAuditEvent | null>(null)
const isDetailOpen = ref(false)
const { dialogRef, handleDialogKeydown } = useModalDialog(isDetailOpen, closeAuditDetail)
let controller: AbortController | null = null
let mounted = true

const eventRows = computed(() => events.value)
const { page, pageSize, pagedRows } = useClientPagination(eventRows)

function asApiError(caught: unknown) {
  return caught instanceof ApiClientError
    ? caught
    : new ApiClientError('UNKNOWN_ERROR', '无法读取管理审计记录', 0)
}

async function loadEvents(append = false) {
  if (!append) controller?.abort()
  const current = new AbortController()
  controller = current
  if (append) isLoadingMore.value = true
  else isLoading.value = true
  error.value = null
  try {
    const cursor = append ? events.value.at(-1) : undefined
    const rows = await listManagementAuditEvents({
      actorLogin: actorLogin.value,
      actionCode: actionCode.value,
      targetType: targetType.value,
      targetId: targetId.value,
      requestId: requestId.value,
      resultCode: resultCode.value,
      occurredFrom: toUtcDateTime(occurredFrom.value),
      occurredTo: toUtcDateTime(occurredTo.value),
      beforeOccurredAt: cursor?.occurredAt,
      beforeId: cursor?.id,
      limit: limit.value,
    }, current.signal)
    if (!mounted || current.signal.aborted) return
    events.value = append ? [...events.value, ...rows] : rows
    hasMore.value = rows.length === limit.value
  } catch (caught) {
    const apiError = asApiError(caught)
    if (apiError.code === 'REQUEST_ABORTED') return
    if (apiError.status === 401) {
      authState.user = null
      authState.isInitialized = true
      await router.replace({ path: '/login', query: { redirect: '/audit' } })
      return
    }
    error.value = apiError
  } finally {
    if (controller === current && mounted) {
      isLoading.value = false
      isLoadingMore.value = false
    }
  }
}

async function submitFilters() {
  filterError.value = ''
  if (occurredFrom.value && occurredTo.value && new Date(occurredFrom.value) > new Date(occurredTo.value)) {
    filterError.value = '开始时间不能晚于结束时间'
    return
  }
  const query: Record<string, string> = {}
  if (actorLogin.value.trim()) query.actorLogin = actorLogin.value.trim()
  if (actionCode.value) query.actionCode = actionCode.value
  if (targetType.value.trim()) query.targetType = targetType.value.trim()
  if (targetId.value.trim()) query.targetId = targetId.value.trim()
  if (requestId.value.trim()) query.requestId = requestId.value.trim()
  if (resultCode.value) query.resultCode = resultCode.value
  if (occurredFrom.value) query.occurredFrom = occurredFrom.value
  if (occurredTo.value) query.occurredTo = occurredTo.value
  await router.replace({ path: '/audit', query })
  await loadEvents()
}

async function clearFilters() {
  targetType.value = ''
  targetId.value = ''
  requestId.value = ''
  actorLogin.value = ''
  actionCode.value = ''
  resultCode.value = ''
  occurredFrom.value = ''
  occurredTo.value = ''
  await submitFilters()
}

const targetOptions = Object.entries(AUDIT_TARGET_LABELS)
const actionOptions = Object.entries(AUDIT_ACTION_LABELS)

function toUtcDateTime(value: string) {
  if (!value) return undefined
  const instant = new Date(value)
  return Number.isNaN(instant.getTime()) ? undefined : instant.toISOString().slice(0, 19)
}

function openAuditDetail(event: ManagementAuditEvent) {
  selectedEvent.value = event
  isDetailOpen.value = true
}

function closeAuditDetail() {
  isDetailOpen.value = false
  selectedEvent.value = null
}

function businessObjectRoute(event: ManagementAuditEvent) {
  const base = ({
    USER: '/users', ROLE: '/roles', ORGANIZATION: '/organizations', PARAMETER: '/parameters',
    DICTIONARY_TYPE: '/dictionaries',
  } as Readonly<Record<string, string>>)[event.targetType]
  if (base) return { path: base, query: { query: event.targetId } }
  if (event.targetType !== 'DICTIONARY_ITEM') return null
  const separator = event.targetId.indexOf(':')
  if (separator < 1) return { path: '/dictionaries', query: { query: event.targetId } }
  return {
    path: '/dictionaries',
    query: { typeCode: event.targetId.slice(0, separator), itemQuery: event.targetId.slice(separator + 1) },
  }
}

async function openBusinessObject(event: ManagementAuditEvent) {
  const destination = businessObjectRoute(event)
  if (!destination) return
  closeAuditDetail()
  await router.push(destination)
}

onMounted(() => loadEvents())
onBeforeUnmount(() => {
  mounted = false
  controller?.abort()
})
</script>

<template>
  <section class="content audit-page">
    <div class="audit-readonly-note" role="note"><ClipboardList :size="18" /><span><strong>操作记录只供查询</strong><small>记录由系统自动生成，不能在本页面新增、修改或删除；需要处理业务数据时请打开对应业务对象。医院尚未确定具体保留年限前，系统不会自动清理这些记录；确定期限后须经审批实施归档或清理。</small></span></div>
    <ListQueryToolbar class="audit-filters" :refreshing="isLoading" @query="submitFilters" @reset="clearFilters" @refresh="loadEvents()">
      <label class="prototype-search"><Search :size="16" /><input v-model="actorLogin" maxlength="128" autocomplete="off" placeholder="操作人登录名" aria-label="操作人登录名" /></label>
      <label class="audit-field"><span>操作</span><select v-model="actionCode" aria-label="操作类型"><option value="">全部操作</option><option v-for="([code, label]) in actionOptions" :key="code" :value="code">{{ label }}</option></select></label>
      <label class="audit-field"><span>操作对象</span><select v-model="targetType" aria-label="操作对象"><option value="">全部对象</option><option v-for="([code, label]) in targetOptions" :key="code" :value="code">{{ label }}</option></select></label>
      <label class="prototype-search"><Search :size="16" /><input v-model="targetId" maxlength="128" autocomplete="off" placeholder="输入用户、角色、机构或配置编号" aria-label="对象编号" /></label>
      <label class="prototype-search"><Search :size="16" /><input v-model="requestId" maxlength="128" autocomplete="off" placeholder="请求编号" aria-label="请求编号" /></label>
      <label class="audit-field"><span>结果</span><select v-model="resultCode" aria-label="处理结果"><option value="">全部结果</option><option value="SUCCESS">成功</option><option value="FAILURE">失败</option></select></label>
      <label class="audit-date"><span>开始时间</span><input v-model="occurredFrom" type="datetime-local" aria-label="开始时间" /></label>
      <label class="audit-date"><span>结束时间</span><input v-model="occurredTo" type="datetime-local" aria-label="结束时间" /></label>
      <label class="audit-limit"><span>查看最近</span><select v-model.number="limit" aria-label="最多读取条数"><option :value="50">50 条</option><option :value="100">100 条</option><option :value="200">200 条</option></select></label>
    </ListQueryToolbar>

    <p class="audit-scope-note">可继续加载更早记录，时间按当前设备所在时区显示。</p>
    <div v-if="filterError" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ filterError }}</span></div>

    <div v-if="error && !isLoading" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ error.message }}<small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button class="prototype-text-button" type="button" @click="loadEvents()">重试</button></div>

    <section class="prototype-section work-table-section audit-panel action-column-table">
      <div v-if="isLoading" class="page-state" aria-live="polite"><LoaderCircle class="spinning" :size="28" /><strong>正在加载审计记录</strong></div>
      <div v-else-if="!error && events.length === 0" class="page-state"><ClipboardList :size="30" /><strong>当前条件下没有审计记录</strong><span>可清除目标筛选后重新查询；页面只展示当前账号机构范围内的脱敏记录。</span></div>
      <div v-else-if="events.length" class="prototype-table-wrap">
        <table class="work-table audit-table">
          <thead><tr><th>时间</th><th>操作人 / 涉及机构</th><th>操作</th><th>对象</th><th>结果</th><th>变更摘要</th><th>操作</th></tr></thead>
          <tbody><tr v-for="event in pagedRows" :key="event.id">
            <td>{{ formatLocalDateTime(event.occurredAt) }}</td>
            <td><strong>{{ event.actorLogin }}</strong><small>{{ event.organizationCode || '平台范围' }}</small></td>
            <td><strong>{{ auditActionLabel(event.actionCode) }}</strong><small v-if="auditActionLabel(event.actionCode) === event.actionCode">未登记中文名称</small></td>
            <td><strong>{{ auditTargetLabel(event.targetType) }}</strong><small>{{ event.targetId }}</small></td>
            <td><span class="prototype-tag" :class="event.resultCode === 'SUCCESS' ? 'success' : 'danger'">{{ event.resultCode === 'SUCCESS' ? '成功' : '失败' }}</span></td>
            <td>{{ event.changeSummary }}</td>
            <td><button class="work-quiet-button" type="button" @click="openAuditDetail(event)">查看详情</button></td>
          </tr></tbody>
        </table>
      </div>
      <AdminPagination v-if="!isLoading && events.length" :total="events.length" :page="page" :page-size="pageSize" @update:page="page = $event" @update:page-size="pageSize = $event" />
      <div v-if="!isLoading && events.length" class="audit-load-more"><button v-if="hasMore" class="work-quiet-button" type="button" :disabled="isLoadingMore" @click="loadEvents(true)"><LoaderCircle v-if="isLoadingMore" class="spinning" :size="15" />{{ isLoadingMore ? '正在读取…' : '加载更早记录' }}</button><span v-else>已到达当前条件下的最早记录</span></div>
    </section>

    <div v-if="selectedEvent" class="audit-detail-layer" role="presentation" @mousedown.self="closeAuditDetail">
      <section ref="dialogRef" class="audit-detail" role="dialog" aria-modal="true" aria-labelledby="audit-detail-title" tabindex="-1" @keydown="handleDialogKeydown">
        <header><div><small>操作记录</small><h2 id="audit-detail-title">{{ auditActionLabel(selectedEvent.actionCode) }}</h2></div><button class="prototype-icon" type="button" aria-label="关闭详情" @click="closeAuditDetail"><X :size="18" /></button></header>
        <dl>
          <div><dt>操作时间</dt><dd>{{ formatLocalDateTime(selectedEvent.occurredAt) }}</dd></div>
          <div><dt>操作人</dt><dd>{{ selectedEvent.actorLogin }}</dd></div>
          <div><dt>本次操作涉及机构</dt><dd>{{ selectedEvent.organizationCode || '平台范围' }}</dd></div>
          <div><dt>操作对象</dt><dd>{{ auditTargetLabel(selectedEvent.targetType) }}（{{ selectedEvent.targetId }}）</dd></div>
          <div><dt>处理结果</dt><dd>{{ selectedEvent.resultCode === 'SUCCESS' ? '成功' : '失败' }}</dd></div>
          <div class="wide"><dt>变更内容</dt><dd>{{ selectedEvent.changeSummary }}</dd></div>
          <div class="wide"><dt>请求编号</dt><dd><code>{{ selectedEvent.requestId || '未记录' }}</code></dd></div>
        </dl>
        <footer><button v-if="businessObjectRoute(selectedEvent)" class="work-quiet-button" type="button" @click="openBusinessObject(selectedEvent)">打开业务对象</button><button class="prototype-button" type="button" @click="closeAuditDetail">关闭</button></footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.audit-page { display: grid; gap: 12px; }
.audit-panel { --action-column-width: 104px; }
.audit-readonly-note { min-height: 52px; padding: 10px 13px; border: 1px solid #d9e5e1; border-radius: 5px; background: #f4faf8; color: #2f665a; display: flex; align-items: center; gap: 10px; }
.audit-readonly-note span { display: grid; gap: 3px; }
.audit-readonly-note small { color: #687b76; font-size: 10px; }
.audit-filters { flex-wrap: wrap; }
.audit-filters .prototype-search { min-width: min(290px, 100%); }
.audit-field { display: flex; align-items: center; gap: 8px; color: #667682; font-size: 12px; }
.audit-field select { min-height: 36px; }
.audit-date { display: flex; align-items: center; gap: 8px; color: #667682; font-size: 12px; }.audit-date input { min-height: 36px; border: 1px solid #cfd8dc; border-radius: 4px; padding: 0 8px; }
.audit-limit { display: flex; align-items: center; gap: 8px; color: #667682; font-size: 12px; }
.audit-limit select { min-height: 36px; }
.audit-scope-note { margin: -4px 0 0; color: #6f7d85; font-size: 11px; }
.feedback { min-height: 46px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 9px; font-size: 12px; }
.feedback > span { flex: 1; display: grid; gap: 2px; }
.feedback small { color: inherit; opacity: .8; }
.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }
.page-state { min-height: 330px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #33745c; text-align: center; }
.page-state span { max-width: 520px; color: #75828c; font-size: 12px; line-height: 1.6; }
.audit-table td { vertical-align: top; }
.audit-table td:nth-child(1) { white-space: nowrap; }
.audit-table td:nth-child(6) { min-width: 220px; white-space: normal; }
.audit-table td small { display: block; margin-top: 3px; color: #78868e; font-size: 10px; }
.audit-load-more { padding: 12px 16px; border-top: 1px solid #e4e9ea; display: flex; justify-content: center; color: #75828c; font-size: 11px; }
.audit-detail-layer { position: fixed; inset: 0; z-index: 120; padding: 24px; background: rgba(19,31,43,.38); display: flex; align-items: center; justify-content: center; }
.audit-detail { width: min(680px, 100%); max-height: 90vh; overflow-y: auto; border-radius: 7px; background: white; box-shadow: 0 18px 48px rgba(20,34,45,.24); }
.audit-detail > header { padding: 16px 20px; border-bottom: 1px solid #dfe5e7; display: flex; align-items: center; justify-content: space-between; }
.audit-detail header small { color: #71808a; font-size: 11px; }.audit-detail h2 { margin: 3px 0 0; font-size: 18px; }
.audit-detail dl { margin: 0; padding: 20px; display: grid; grid-template-columns: 1fr 1fr; gap: 15px 20px; }
.audit-detail dl div { min-width: 0; }.audit-detail dl .wide { grid-column: 1 / -1; }.audit-detail dt { color: #71808a; font-size: 11px; }.audit-detail dd { margin: 5px 0 0; color: #263341; font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.audit-detail footer { padding: 13px 20px; border-top: 1px solid #dfe5e7; display: flex; justify-content: flex-end; gap: 8px; }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 640px) { .audit-detail dl { grid-template-columns: 1fr; }.audit-detail dl .wide { grid-column: auto; } }
</style>
