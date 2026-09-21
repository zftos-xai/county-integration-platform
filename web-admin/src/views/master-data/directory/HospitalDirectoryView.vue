<!-- 医院综合目录页面：按机构和目录类型查看当前有效数据及其真实来源链路。 -->
<script setup lang="ts">
import { AlertCircle, Building2, CheckCircle2, ChevronDown, ChevronRight, Database, RefreshCw, Search } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminPagination from '@/components/AdminPagination.vue'
import { hospitalDirectoryTypes, listHospitalDirectory, type HospitalDirectoryItem, type HospitalDirectoryType } from '@/api/master-data/directory'
import { listOrganizations, type Organization } from '@/api/system/organization'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'

const router = useRouter()
const items = ref<HospitalDirectoryItem[]>([])
const counts = ref<Record<HospitalDirectoryType, number>>({ DEPARTMENT: 0, DOCTOR: 0, WARD: 0, BED: 0 })
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const organizationCode = ref(authState.user?.organizationCode ?? '')
const organizationQuery = ref('')
const directoryType = ref<HospitalDirectoryType>('DEPARTMENT')
const keyword = ref('')
const organizations = ref<Organization[]>([])
const selectedItemId = ref<number | null>(null)
const isLoading = ref(true)
const isRefreshing = ref(false)
const error = ref<ApiClientError | null>(null)
let controller: AbortController | null = null
let mounted = true

/** 当前有效目录类型对应的业务名称。 */
const typeLabels: Record<HospitalDirectoryType, string> = { DEPARTMENT: '科室', DOCTOR: '医生', WARD: '病区', BED: '床位' }

/** 当前机构选择器展示的真实机构范围。 */
const visibleOrganizations = computed(() => {
  const values = new Map(organizations.value.map((item) => [item.organizationCode, item]))
  for (const code of authState.user?.organizationCodes ?? []) {
    if (!values.has(code)) values.set(code, { id: 0, organizationCode: code, organizationName: code, organizationType: 'UNKNOWN', parentId: null, enabled: true, validFrom: null, validTo: null, createdAt: '', updatedAt: '', version: '' })
  }
  const normalizedQuery = organizationQuery.value.trim().toLocaleLowerCase('zh-CN')
  return [...values.values()]
    .filter((item) => !normalizedQuery || `${item.organizationName} ${item.organizationCode}`.toLocaleLowerCase('zh-CN').includes(normalizedQuery))
    .sort((left, right) => left.organizationName.localeCompare(right.organizationName, 'zh-CN'))
})

/** 当前选中的平台机构。 */
const selectedOrganization = computed(() => visibleOrganizations.value.find((item) => item.organizationCode === organizationCode.value)
  ?? organizations.value.find((item) => item.organizationCode === organizationCode.value) ?? null)

/** 当前展开查看来源链路的目录记录。 */
const selectedItem = computed(() => items.value.find((item) => item.id === selectedItemId.value) ?? null)

/** 当前机构四类有效目录的合计主记录数。 */
const currentOrganizationTotal = computed(() => hospitalDirectoryTypes.reduce((sum, type) => sum + counts.value[type], 0))

/** 将未知异常转换为可展示的API错误。 */
function asApiError(caught: unknown) {
  return caught instanceof ApiClientError ? caught : new ApiClientError('UNKNOWN_ERROR', '无法读取医院综合目录', 0)
}

/** 读取服务端当前有效目录；查看不会调用HIS或改变目录。 */
async function load(background = false) {
  controller?.abort()
  const request = new AbortController()
  controller = request
  if (background) isRefreshing.value = true
  else isLoading.value = true
  error.value = null
  selectedItemId.value = null
  try {
    const result = await listHospitalDirectory({ organizationCode: organizationCode.value || undefined, directoryType: directoryType.value, keyword: keyword.value.trim() || undefined, page: page.value, pageSize: pageSize.value }, request.signal)
    if (!mounted || request.signal.aborted) return
    items.value = result.items
    total.value = result.total
    counts.value = { DEPARTMENT: 0, DOCTOR: 0, WARD: 0, BED: 0 }
    for (const count of result.counts) counts.value[count.directoryType] = count.total
  } catch (caught) {
    const apiError = asApiError(caught)
    if (apiError.code !== 'REQUEST_ABORTED') {
      if (apiError.status === 401) {
        authState.user = null
        authState.isInitialized = true
        await router.replace({ path: '/login', query: { redirect: '/master-data/directory' } })
      } else error.value = apiError
    }
  } finally {
    if (controller === request && mounted) {
      isLoading.value = false
      isRefreshing.value = false
    }
  }
}

/** 加载当前账号可见的机构资料；失败时仍保留账号机构范围。 */
async function loadOrganizations() {
  if (!hasPermission('organization:read')) return
  try {
    const result = await listOrganizations(undefined)
    if (!mounted) return
    organizations.value = result
    if (!organizationCode.value && result.length) organizationCode.value = result[0]!.organizationCode
  } catch {
    // 机构列表只负责切换上下文，当前有效目录仍按当前账号机构范围读取。
  }
}

/** 选择一个平台机构并读取该机构当前有效目录。 */
function selectOrganization(code: string) {
  if (organizationCode.value === code) return
  organizationCode.value = code
  page.value = 1
  void load()
}

/** 切换目录类型并读取该类当前有效数据。 */
function selectType(type: HospitalDirectoryType) {
  if (directoryType.value === type) return
  directoryType.value = type
  page.value = 1
  void load()
}

/** 应用名称或编码查询条件并回到第一页。 */
function applyFilters() { page.value = 1; void load() }

/** 清空目录关键词，保留当前机构和目录类型。 */
function clearFilters() { keyword.value = ''; applyFilters() }

/** 展开或收起一条当前有效目录的来源链路。 */
function toggleDetail(id: number) { selectedItemId.value = selectedItemId.value === id ? null : id }

/** 切换服务端页码。 */
function changePage(value: number) { page.value = value; void load() }

/** 切换每页条数并读取第一页。 */
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

/** 格式化服务端UTC时间。 */
function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}

onMounted(() => void Promise.all([loadOrganizations(), load()]))
onBeforeUnmount(() => { mounted = false; controller?.abort() })
</script>

<template>
  <section class="content hospital-directory-page">
    <aside class="organization-rail" aria-label="平台机构">
      <header><h2>平台机构</h2><span>{{ visibleOrganizations.length }} 家</span></header>
      <label class="organization-search"><Search :size="16" /><input v-model="organizationQuery" placeholder="搜索机构名称或编码" /></label>
      <div class="organization-list">
        <button v-for="organization in visibleOrganizations" :key="organization.organizationCode" type="button" :class="{ active: organization.organizationCode === organizationCode }" @click="selectOrganization(organization.organizationCode)">
          <Building2 :size="16" />
          <span><strong>{{ organization.organizationName }}</strong><small>{{ organization.organizationCode }}</small></span>
          <em :class="{ disabled: !organization.enabled }">{{ organization.enabled ? '已启用' : '已停用' }}</em>
        </button>
        <div v-if="!visibleOrganizations.length" class="organization-empty">没有符合条件的机构</div>
      </div>
    </aside>

    <section class="directory-workspace">
      <header class="directory-context">
        <div><span>当前查看机构</span><h2>{{ selectedOrganization?.organizationName || organizationCode || '未选择机构' }}</h2><p>机构编码：{{ selectedOrganization?.organizationCode || organizationCode || '—' }}</p></div>
        <span class="directory-published-state" :class="{ empty: currentOrganizationTotal === 0 }"><CheckCircle2 :size="15" />{{ currentOrganizationTotal > 0 ? '已有当前有效数据' : '尚无当前有效数据' }}</span>
      </header>

      <nav class="directory-tabs" role="tablist" aria-label="医院综合目录类型">
        <button v-for="type in hospitalDirectoryTypes" :key="type" type="button" role="tab" :aria-selected="directoryType === type" :class="{ active: directoryType === type }" @click="selectType(type)">{{ typeLabels[type] }}<span>{{ counts[type] }}</span></button>
      </nav>

      <section class="directory-panel">
        <header class="directory-panel-heading">
          <div><h3>当前医院综合目录 · {{ typeLabels[directoryType] }}</h3><p>当前有效数据 {{ counts[directoryType] }} 条 · 来自最近一次成功同步 · 查看不会重新调用 HIS</p></div>
          <button class="work-quiet-button" type="button" :disabled="isRefreshing" @click="load(true)"><RefreshCw :size="16" :class="{ spinning: isRefreshing }" />刷新</button>
        </header>

        <form class="directory-toolbar" @submit.prevent="applyFilters">
          <label class="directory-search"><Search :size="17" /><input v-model="keyword" maxlength="50" placeholder="搜索名称、编码或助记码" /></label>
          <button class="prototype-button" type="submit">查询</button><button class="work-quiet-button" type="button" @click="clearFilters">清除</button><span>共 {{ total }} 条</span>
        </form>

        <p v-if="error" class="feedback danger" role="alert"><AlertCircle :size="18" /><span><strong>{{ error.message }}</strong><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button class="text-button" type="button" @click="load()">重试</button></p>
        <div v-else-if="isLoading" class="directory-state" role="status">正在读取当前有效数据…</div>
        <div v-else-if="!items.length" class="directory-state"><Database :size="28" /><strong>当前机构没有{{ typeLabels[directoryType] }}有效数据</strong><span>请先到“同步批次”查看最近一次医院综合目录同步结果。</span></div>
        <div v-else class="directory-table-wrap">
          <table class="directory-table">
            <thead><tr><th>目录编码</th><th>{{ typeLabels[directoryType] }}名称</th><th>助记码</th><th>类别</th><th>有效关系</th><th>最近同步</th><th>来源批次</th><th><span class="visually-hidden">操作</span></th></tr></thead>
            <tbody>
              <template v-for="item in items" :key="item.id">
                <tr :class="{ expanded: selectedItemId === item.id }"><td class="directory-code" :title="item.sourceRecordCode">{{ item.sourceRecordCode }}</td><td :title="item.sourceRecordName"><strong>{{ item.sourceRecordName }}</strong></td><td :title="item.mnemonicCode || '—'">{{ item.mnemonicCode || '—' }}</td><td :title="item.categoryName || '—'">{{ item.categoryName || '—' }}</td><td>{{ item.relationCount ? `${item.relationCount} 条` : '—' }}</td><td>{{ formatTime(item.lastSeenAt) }}</td><td><RouterLink class="batch-link" :to="`/master-data/batches/${item.latestBatchId}`">{{ item.latestBatchNo }}</RouterLink></td><td class="directory-actions"><button type="button" :aria-expanded="selectedItemId === item.id" @click="toggleDetail(item.id)"><ChevronDown v-if="selectedItemId === item.id" :size="15" /><ChevronRight v-else :size="15" />{{ selectedItemId === item.id ? '收起' : '查看' }}</button></td></tr>
                <tr v-if="selectedItemId === item.id && selectedItem" class="directory-detail-row"><td colspan="8"><div class="directory-detail">
                  <section><h4>平台当前数据</h4><dl><div><dt>目录类型</dt><dd>{{ typeLabels[selectedItem.directoryType] }}</dd></div><div><dt>目录编码</dt><dd>{{ selectedItem.sourceRecordCode }}</dd></div><div><dt>目录名称</dt><dd>{{ selectedItem.sourceRecordName }}</dd></div><div><dt>助记码</dt><dd>{{ selectedItem.mnemonicCode || '—' }}</dd></div><div><dt>类别</dt><dd>{{ selectedItem.categoryName || '—' }}</dd></div><div><dt>有效关系</dt><dd>{{ selectedItem.relationCount ? `${selectedItem.relationCount} 条` : '—' }}</dd></div></dl></section>
                  <section><h4>来源追溯</h4><dl><div><dt>来源系统</dt><dd>基层 HIS</dd></div><div><dt>平台机构</dt><dd>{{ selectedItem.organizationName }}</dd></div><div><dt>平台机构编码</dt><dd>{{ selectedItem.organizationCode }}</dd></div><div><dt>来源机构标识</dt><dd>{{ selectedItem.sourceOrganizationCode || 'HIS 本次未返回' }}</dd></div><div><dt>来源交易</dt><dd>100-003 · 医院综合目录查询</dd></div><div><dt>最近成功同步</dt><dd>{{ formatTime(selectedItem.lastSeenAt) }}</dd></div><div><dt>同步记录</dt><dd><RouterLink class="batch-link" :to="`/master-data/batches/${selectedItem.latestBatchId}`">{{ selectedItem.latestBatchNo }}</RouterLink></dd></div></dl></section>
                </div></td></tr>
              </template>
            </tbody>
          </table>
        </div>
        <AdminPagination v-if="!isLoading && !error && total > 0" :total="total" :page="page" :page-size="pageSize" @update:page="changePage" @update:page-size="changePageSize" />
      </section>
    </section>
  </section>
</template>

<style scoped>
.hospital-directory-page{display:grid;grid-template-columns:260px minmax(0,1fr);gap:14px;align-items:start}.organization-rail,.directory-workspace{border:1px solid #dce5e7;border-radius:8px;background:#fff;overflow:hidden}.organization-rail{position:sticky;top:16px}.organization-rail>header{height:54px;padding:0 14px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #e6edef}.organization-rail h2{margin:0;color:#253e46;font-size:15px}.organization-rail>header span{color:#7b8b92;font-size:12px}.organization-search{height:38px;margin:12px;padding:0 10px;border:1px solid #d0dcdf;border-radius:6px;display:flex;align-items:center;gap:7px;color:#71838a}.organization-search:focus-within{border-color:#198273;box-shadow:0 0 0 2px rgba(25,130,115,.12)}.organization-search input{width:100%;border:0;outline:0;background:transparent;color:#2c424a;font-size:12px}.organization-list{padding:0 8px 10px;max-height:620px;overflow:auto}.organization-list button{width:100%;min-height:58px;padding:9px 8px;border:0;border-radius:6px;background:transparent;display:grid;grid-template-columns:18px minmax(0,1fr) auto;align-items:center;gap:7px;color:#5b6f76;text-align:left;cursor:pointer}.organization-list button:hover{background:#f4f8f7}.organization-list button.active{background:#e7f3f0;color:#107566}.organization-list button span{min-width:0}.organization-list strong,.organization-list small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.organization-list strong{color:#2c434b;font-size:12px}.organization-list small{margin-top:4px;color:#829198;font-size:11px}.organization-list em{font-style:normal;color:#15806f;font-size:11px;white-space:nowrap}.organization-list em.disabled{color:#8a969b}.organization-empty{padding:24px 10px;color:#819097;font-size:12px;text-align:center}.directory-context{min-height:78px;padding:14px 18px 12px;display:flex;justify-content:space-between;align-items:center;gap:16px}.directory-context>div>span{color:#829198;font-size:12px}.directory-context h2{margin:3px 0 2px;color:#213942;font-size:18px}.directory-context p{margin:0;color:#72858c;font-size:12px}.directory-published-state{display:inline-flex;align-items:center;gap:6px;color:#117966;font-size:12px;font-weight:650;white-space:nowrap}.directory-published-state.empty{color:#7a898f}.directory-tabs{padding:0 18px;border-top:1px solid #e8edef;border-bottom:1px solid #dfe7e9;display:flex;gap:28px;overflow-x:auto}.directory-tabs button{height:45px;padding:0 2px;border:0;border-bottom:2px solid transparent;background:transparent;color:#64777f;font-size:12px;font-weight:600;white-space:nowrap;cursor:pointer}.directory-tabs button span{margin-left:5px;padding:1px 6px;border-radius:10px;background:#eef3f4;color:#75858b;font-size:11px}.directory-tabs button.active{border-bottom-color:#178171;color:#117565}.directory-tabs button.active span{background:#e2f1ed;color:#117565}.directory-panel{border-top:0;background:#fff}.directory-panel-heading{padding:13px 16px;border-bottom:1px solid #e6edef;display:flex;justify-content:space-between;align-items:center;gap:16px}.directory-panel-heading h3{margin:0;color:#263d46;font-size:15px}.directory-panel-heading p{margin:4px 0 0;color:#77888f;font-size:12px}.directory-toolbar{padding:11px 16px;border-bottom:1px solid #e6edef;display:grid;grid-template-columns:minmax(240px,420px) auto auto 1fr;gap:8px;align-items:center}.directory-toolbar>span{justify-self:end;color:#7a8a91;font-size:12px}.directory-search{height:36px;padding:0 11px;border:1px solid #ccd9dc;border-radius:6px;display:flex;align-items:center;gap:8px;color:#6f8188}.directory-search:focus-within{border-color:#198273;box-shadow:0 0 0 2px rgba(25,130,115,.12)}.directory-search input{width:100%;border:0;outline:0;background:transparent;color:#263c45;font-size:12px}.directory-table-wrap{overflow:auto}.directory-table{width:100%;min-width:980px;border-collapse:collapse;table-layout:fixed}.directory-table th,.directory-table td{height:47px;padding:0 12px;border-bottom:1px solid #e8eef0;text-align:left;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:#415860;font-size:12px}.directory-table th{height:40px;background:#f6f8f9;color:#697b83;font-weight:650}.directory-table th:nth-child(1){width:150px}.directory-table th:nth-child(2){width:160px}.directory-table th:nth-child(3){width:100px}.directory-table th:nth-child(4){width:110px}.directory-table th:nth-child(5){width:88px}.directory-table th:nth-child(6){width:138px}.directory-table th:nth-child(7){width:184px}.directory-table th:nth-child(8){width:82px}.directory-table tr.expanded>td{background:#f1f8f6}.directory-table strong{color:#243b44}.directory-code{font-variant-numeric:tabular-nums}.batch-link{color:#087767;text-decoration:none}.batch-link:hover{text-decoration:underline}.directory-actions{text-align:right!important}.directory-actions button{height:32px;padding:0 9px;border:1px solid #70ae9f;border-radius:5px;background:#fff;display:inline-flex;align-items:center;gap:3px;color:#117565;font-size:12px;cursor:pointer}.directory-detail-row td{height:auto!important;padding:0 12px 12px!important;background:#f1f8f6!important;white-space:normal!important;overflow:visible!important}.directory-detail{border:1px solid #d7e8e3;border-radius:6px;background:#fff;display:grid;grid-template-columns:1fr 1fr}.directory-detail section{padding:12px 14px}.directory-detail section+section{border-left:1px solid #e1e9eb}.directory-detail h4{margin:0 0 9px;color:#263e47;font-size:13px}.directory-detail dl{margin:0;display:grid;grid-template-columns:1fr 1fr;gap:7px 18px}.directory-detail dl div{min-width:0;display:grid;grid-template-columns:86px minmax(0,1fr);gap:7px}.directory-detail dt{color:#7b8c92;font-size:12px}.directory-detail dd{margin:0;color:#3a515a;font-size:12px;overflow-wrap:anywhere}.directory-state{min-height:260px;padding:30px;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:8px;color:#7b8c93}.directory-state strong{color:#435a63}.directory-state span{font-size:12px}@media(max-width:1100px){.hospital-directory-page{grid-template-columns:220px minmax(0,1fr)}.directory-detail{grid-template-columns:1fr}.directory-detail section+section{border-left:0;border-top:1px solid #e1e9eb}}@media(max-width:780px){.hospital-directory-page{grid-template-columns:1fr}.organization-rail{position:static}.organization-list{max-height:210px}.directory-context{align-items:flex-start}.directory-toolbar{grid-template-columns:1fr auto auto}.directory-toolbar>span{grid-column:1/-1;justify-self:start}.directory-table{min-width:0}.directory-table th:nth-child(1){width:34%}.directory-table th:nth-child(2){width:30%}.directory-table th:nth-child(5){width:18%}.directory-table th:nth-child(8){width:18%}.directory-table th:nth-child(3),.directory-table td:nth-child(3),.directory-table th:nth-child(4),.directory-table td:nth-child(4),.directory-table th:nth-child(6),.directory-table td:nth-child(6),.directory-table th:nth-child(7),.directory-table td:nth-child(7){display:none}.directory-detail-row td{display:table-cell!important}.directory-detail dl{grid-template-columns:1fr}}@media(max-width:520px){.directory-context{display:grid}.directory-tabs{gap:20px}.directory-toolbar{grid-template-columns:1fr 1fr}.directory-search{grid-column:1/-1}.directory-toolbar>span{grid-column:1/-1}}
</style>
