<!-- 数据目录共用布局：统一机构选择、目录导航、查询和读取状态；业务表格由页面提供。 -->
<script setup lang="ts" generic="T extends string">
import { AlertCircle, Building2, CheckCircle2, Database, RefreshCw, Search } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listOrganizations, type Organization } from '@/api/system/organization'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'

const props = defineProps<{
  title: string
  types: readonly T[]
  typeLabels: Record<T, string>
  directoryType: T
  counts: Record<T, number>
  total: number
  isLoading: boolean
  isRefreshing: boolean
  isEmpty: boolean
  error: ApiClientError | null
}>()
const organizationCode = defineModel<string>('organizationCode', { required: true })
const keyword = defineModel<string>('keyword', { required: true })
const emit = defineEmits<{
  organizationChange: []
  typeChange: [type: T]
  search: []
  refresh: []
  retry: []
}>()
const route = useRoute()
const router = useRouter()
const organizationQuery = ref('')
const organizations = ref<Organization[]>([])
const isInitializing = ref(true)
const organizationError = ref('')
const request = new AbortController()
type OrganizationOption = Pick<Organization, 'organizationCode' | 'organizationName'> & { enabled?: boolean }

// 机构资料读取权限与目录权限不同；缺少资料时只展示会话中的机构编码，不伪造启用状态。
const organizationOptions = computed(() => {
  const values = new Map<string, OrganizationOption>()
  for (const code of authState.user?.organizationCodes ?? []) {
    const organization = organizations.value.find(item => item.organizationCode === code)
    values.set(code, organization ?? { organizationCode: code, organizationName: code })
  }
  return [...values.values()].sort((left, right) => left.organizationName.localeCompare(right.organizationName, 'zh-CN'))
})
const visibleOrganizations = computed(() => {
  const query = organizationQuery.value.trim().toLocaleLowerCase('zh-CN')
  return organizationOptions.value.filter(item => `${item.organizationName} ${item.organizationCode}`.toLocaleLowerCase('zh-CN').includes(query))
})
const selectedOrganization = computed(() => organizationOptions.value.find(item => item.organizationCode === organizationCode.value))
const currentOrganizationTotal = computed(() => props.types.reduce((sum, type) => sum + props.counts[type], 0))
const isBusy = computed(() => isInitializing.value || props.isLoading || props.isRefreshing)
const navigationQuery = computed(() => organizationCode.value ? { organizationCode: organizationCode.value } : {})
const statusText = computed(() => {
  if (!isInitializing.value && !selectedOrganization.value) return '未选择机构'
  if (isBusy.value) return '正在读取当前数据'
  if (props.error) return '当前数据读取失败'
  return currentOrganizationTotal.value > 0 ? '已有当前有效数据' : '尚无当前有效数据'
})

function readOrganizationContext() {
  if (isInitializing.value || request.signal.aborted) return
  const requested = typeof route.query.organizationCode === 'string' ? route.query.organizationCode : ''
  const allowed = organizationOptions.value
  const next = allowed.find(item => item.organizationCode === requested)
    ?? allowed.find(item => item.organizationCode === authState.user?.organizationCode)
    ?? allowed[0]
  const code = next?.organizationCode ?? ''
  if (organizationCode.value === code) return
  organizationCode.value = code
  emit('organizationChange')
}

async function initialize() {
  try {
    if (hasPermission('organization:read')) organizations.value = await listOrganizations(undefined, request.signal)
  } catch (caught) {
    if (request.signal.aborted) return
    if (caught instanceof ApiClientError && caught.status === 401) {
      authState.user = null
      authState.isInitialized = true
      await router.replace({ path: '/login', query: { redirect: route.fullPath } })
      return
    }
    organizationError.value = '机构资料暂不可用，按账号授权机构编码显示。'
  }
  if (request.signal.aborted) return
  isInitializing.value = false
  readOrganizationContext()
}

function selectOrganization(code: string) {
  if (organizationCode.value === code) return
  void router.replace({ query: { ...route.query, organizationCode: code } })
}

function clearFilters() {
  keyword.value = ''
  emit('search')
}

// URL只保存机构上下文，跨数据集切换和浏览器历史导航都使用同一个选择来源。
watch(() => route.query.organizationCode, readOrganizationContext)
onMounted(() => void initialize())
onBeforeUnmount(() => request.abort())
</script>

<template>
  <section class="content directory-layout">
    <nav class="dataset-nav" aria-label="基础数据集">
      <RouterLink :class="{ active: route.path === '/master-data/directory' }" :to="{ path: '/master-data/directory', query: navigationQuery }">综合目录</RouterLink>
      <RouterLink :class="{ active: route.path === '/master-data/directory/medical' }" :to="{ path: '/master-data/directory/medical', query: navigationQuery }">三大目录</RouterLink>
    </nav>
    <aside class="organization-rail" aria-label="平台机构">
      <header><h2>平台机构</h2><span>{{ visibleOrganizations.length }} 家</span></header>
      <label class="organization-search"><Search :size="16" aria-hidden="true" /><input v-model="organizationQuery" aria-label="搜索机构名称或编码" placeholder="搜索机构名称或编码" /></label>
      <p v-if="organizationError" class="organization-notice" role="status">{{ organizationError }}</p>
      <div class="organization-list">
        <button v-for="organization in visibleOrganizations" :key="organization.organizationCode" type="button" :aria-pressed="organization.organizationCode === organizationCode" :class="{ active: organization.organizationCode === organizationCode }" @click="selectOrganization(organization.organizationCode)">
          <Building2 :size="16" aria-hidden="true" />
          <span><strong :title="organization.organizationName">{{ organization.organizationName }}</strong><small :title="organization.organizationCode">{{ organization.organizationCode }}</small></span>
          <em v-if="organization.enabled !== undefined" :class="{ disabled: !organization.enabled }">{{ organization.enabled ? '已启用' : '已停用' }}</em>
        </button>
        <div v-if="!visibleOrganizations.length" class="organization-empty">{{ isInitializing ? '正在读取机构…' : '没有符合条件的机构' }}</div>
      </div>
    </aside>
    <section class="directory-workspace">
      <header class="directory-context">
        <div><span>当前查看机构</span><h2>{{ selectedOrganization?.organizationName || '未选择机构' }}</h2><p>机构编码：{{ organizationCode || '—' }}</p></div>
        <span class="directory-published-state" :class="{ 'is-empty': isBusy || error || currentOrganizationTotal === 0 }" role="status">
          <CheckCircle2 v-if="!isBusy && !error && currentOrganizationTotal > 0" :size="15" aria-hidden="true" /><Database v-else :size="15" aria-hidden="true" />{{ statusText }}
        </span>
      </header>
      <nav class="directory-tabs" :aria-label="`${title}类型`">
        <button v-for="type in types" :key="type" type="button" :aria-pressed="directoryType === type" :class="{ active: directoryType === type }" @click="emit('typeChange', type)">{{ typeLabels[type] }}<span>{{ isLoading || error ? '—' : counts[type] }}</span></button>
      </nav>
      <section class="directory-panel">
        <header class="directory-panel-heading">
          <div><h3>当前{{ title }} · {{ typeLabels[directoryType] }}</h3><p>当前有效数据 {{ isLoading || error ? '—' : counts[directoryType] }} 条 · 来自最近一次成功同步 · 查看不会重新调用 HIS</p></div>
          <button class="work-quiet-button" type="button" :disabled="isBusy || !organizationCode" @click="emit('refresh')"><RefreshCw :size="16" aria-hidden="true" :class="{ spinning: isRefreshing }" />刷新</button>
        </header>
        <form class="directory-toolbar" @submit.prevent="emit('search')">
          <label class="directory-search"><Search :size="17" aria-hidden="true" /><input v-model="keyword" maxlength="50" aria-label="搜索名称、编码或助记码" placeholder="搜索名称、编码或助记码" /></label>
          <button class="prototype-button" type="submit" :disabled="isBusy || !organizationCode">查询</button>
          <button class="work-quiet-button" type="button" :disabled="isBusy || !organizationCode" @click="clearFilters">清除</button><span>共 {{ isLoading || error ? '—' : total }} 条</span>
        </form>
        <p v-if="error" class="feedback danger" role="alert"><AlertCircle :size="18" aria-hidden="true" /><span><strong>{{ error.message }}</strong><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button class="text-button" type="button" @click="emit('retry')">重试</button></p>
        <div v-else-if="isInitializing" class="directory-state" role="status">正在读取机构…</div>
        <div v-else-if="!organizationCode" class="directory-state" role="status">当前账号没有可查看的机构</div>
        <div v-else-if="isLoading" class="directory-state" role="status">正在读取当前有效数据…</div>
        <div v-else-if="isEmpty" class="directory-state" role="status">
          <Database :size="28" aria-hidden="true" />
          <template v-if="keyword.trim()"><strong>没有符合查询条件的{{ typeLabels[directoryType] }}</strong><span>请调整关键词或清除查询条件。</span></template>
          <template v-else><strong>当前机构没有{{ typeLabels[directoryType] }}有效数据</strong><span>请到“同步批次”查看最近一次{{ title }}同步结果。</span></template>
        </div>
        <slot v-else />
        <slot v-if="!isLoading && !error && total > 0" name="pagination" />
      </section>
    </section>
  </section>
</template>

<style scoped>
.directory-layout { display:grid; grid-template-columns:260px minmax(0,1fr); gap:14px; align-items:start; }
.dataset-nav { grid-column:1/-1; display:flex; gap:8px; border-bottom:1px solid var(--line); overflow-x:auto; }
.dataset-nav a { flex:none; padding:11px 13px; border:1px solid transparent; border-bottom:0; border-radius:7px 7px 0 0; color:var(--muted); font-size:13px; text-decoration:none; }
.dataset-nav a:hover { background:#f5f8f8; }
.dataset-nav a.active { border-color:#d6e5e1; background:#eaf5f2; color:var(--accent); font-weight:650; }
.organization-rail,.directory-workspace { min-width:0; border:1px solid var(--line); border-radius:8px; background:var(--surface); overflow:hidden; }
.organization-rail { position:sticky; top:16px; }
.organization-rail>header { height:54px; padding:0 14px; display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid var(--line-soft); }
.organization-rail h2 { margin:0; color:var(--ink); font-size:15px; }
.organization-rail>header span { color:var(--muted); font-size:12px; }
.organization-search { height:38px; margin:12px; padding:0 10px; border:1px solid #d0dcdf; border-radius:6px; display:flex; align-items:center; gap:7px; color:var(--muted); }
.organization-search input,.directory-search input { min-width:0; width:100%; border:0; outline:0; background:transparent; color:var(--ink); font-size:12px; }
.organization-search:focus-within,.directory-search:focus-within { border-color:var(--accent); box-shadow:0 0 0 2px rgb(25 130 115 / 12%); }
.organization-list { padding:0 8px 10px; max-height:620px; overflow:auto; }
.organization-list button { width:100%; min-height:58px; padding:9px 8px; border:0; border-radius:6px; background:transparent; display:grid; grid-template-columns:18px minmax(0,1fr) auto; align-items:center; gap:7px; color:var(--muted); text-align:left; cursor:pointer; }
.organization-list button:hover { background:#f4f8f7; }
.organization-list button.active { background:#e7f3f0; color:var(--accent); }
.organization-list button span { min-width:0; }
.organization-list strong,.organization-list small { display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.organization-list strong { color:var(--ink); font-size:12px; }
.organization-list small { margin-top:4px; color:var(--muted); font-size:11px; }
.organization-list em { font-style:normal; color:var(--accent); font-size:11px; white-space:nowrap; }
.organization-list em.disabled { color:var(--muted); }
.organization-empty,.organization-notice { padding:16px 10px; color:var(--muted); font-size:12px; text-align:center; }
.directory-context { min-height:78px; padding:14px 18px 12px; display:flex; justify-content:space-between; align-items:center; gap:16px; }
.directory-context>div { min-width:0; }
.directory-context>div>span { color:var(--muted); font-size:12px; }
.directory-context h2 { margin:3px 0 2px; color:var(--ink); font-size:18px; overflow-wrap:anywhere; }
.directory-context p { margin:0; color:var(--muted); font-size:12px; overflow-wrap:anywhere; }
.directory-published-state { display:inline-flex; align-items:center; gap:6px; color:var(--accent); font-size:12px; font-weight:650; white-space:nowrap; }
.directory-published-state.is-empty { color:var(--muted); }
.directory-tabs { padding:0 18px; border-top:1px solid var(--line-soft); border-bottom:1px solid var(--line); display:flex; gap:28px; overflow-x:auto; }
.directory-tabs button { height:45px; padding:0 2px; border:0; border-bottom:2px solid transparent; background:transparent; color:var(--muted); font-size:12px; font-weight:600; white-space:nowrap; }
.directory-tabs button span { margin-left:5px; padding:1px 6px; border-radius:10px; background:#eef3f4; color:var(--muted); font-size:11px; }
.directory-tabs button.active { border-bottom-color:var(--accent); color:var(--accent); }
.directory-tabs button.active span { background:#e2f1ed; color:var(--accent); }
.directory-panel { min-width:0; background:var(--surface); }
.directory-panel-heading { padding:13px 16px; border-bottom:1px solid var(--line-soft); display:flex; justify-content:space-between; align-items:center; gap:16px; }
.directory-panel-heading h3 { margin:0; color:var(--ink); font-size:15px; }
.directory-panel-heading p { margin:4px 0 0; color:var(--muted); font-size:12px; }
.directory-panel-heading button { flex:none; }
.directory-toolbar { padding:11px 16px; border-bottom:1px solid var(--line-soft); display:grid; grid-template-columns:minmax(120px,420px) auto auto minmax(55px,1fr); gap:8px; align-items:center; }
.directory-toolbar>span { justify-self:end; color:var(--muted); font-size:12px; white-space:nowrap; }
.directory-search { min-width:0; height:36px; padding:0 11px; border:1px solid #ccd9dc; border-radius:6px; display:flex; align-items:center; gap:8px; color:var(--muted); }
.directory-state { min-height:260px; padding:30px; display:flex; flex-direction:column; justify-content:center; align-items:center; gap:8px; color:var(--muted); text-align:center; }
.directory-state strong { color:var(--ink); }
.directory-state span { font-size:12px; }
button:focus-visible,a:focus-visible { outline:2px solid var(--accent); outline-offset:-2px; }
@media(max-width:1100px) { .directory-layout { grid-template-columns:220px minmax(0,1fr); } .directory-context { flex-wrap:wrap; } }
@media(max-width:780px) { .directory-layout { grid-template-columns:minmax(0,1fr); } .organization-rail { position:static; } .organization-list { max-height:210px; } .directory-toolbar { grid-template-columns:minmax(0,1fr) auto auto; } .directory-toolbar>span { grid-column:1/-1; justify-self:start; } }
@media(max-width:520px) { .directory-context { display:grid; } .directory-tabs { gap:20px; } .directory-panel-heading { align-items:flex-start; } .directory-toolbar { grid-template-columns:1fr 1fr; } .directory-search,.directory-toolbar>span { grid-column:1/-1; } }
</style>
