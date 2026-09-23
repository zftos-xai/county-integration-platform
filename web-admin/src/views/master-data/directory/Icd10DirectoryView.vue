<!-- 平台公共ICD-10目录正式页面：只读展示已经完成同步并发布的当前诊断数据。 -->
<script setup lang="ts">
import { AlertCircle, CheckCircle2, Database, RefreshCw, Search } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminPagination from '@/components/AdminPagination.vue'
import ListQueryToolbar from '@/components/ListQueryToolbar.vue'
import {
  icd10DiagnosisCategories,
  listIcd10Directory,
  type Icd10DiagnosisCategory,
  type Icd10DirectoryItem,
} from '@/api/master-data/icd10Directory'
import { authState } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'

const router = useRouter()
const items = ref<Icd10DirectoryItem[]>([])
const counts = ref<Record<Icd10DiagnosisCategory, number>>({ WESTERN: 0, TRADITIONAL: 0 })
const category = ref<Icd10DiagnosisCategory>('WESTERN')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedItemId = ref<number | null>(null)
const isLoading = ref(true)
const isRefreshing = ref(false)
const error = ref<ApiClientError | null>(null)
let controller: AbortController | null = null
let mounted = true

/** 公共ICD-10诊断类别的业务名称。 */
const categoryLabels: Record<Icd10DiagnosisCategory, string> = {
  WESTERN: '西医诊断',
  TRADITIONAL: '中医诊断',
}

/** 当前在表格内展开来源追溯信息的诊断记录。 */
const selectedItem = computed(() => items.value.find((item) => item.id === selectedItemId.value) ?? null)

/** 将未知异常收敛为不包含来源正文的页面错误。 */
function asApiError(caught: unknown) {
  return caught instanceof ApiClientError
    ? caught
    : new ApiClientError('UNKNOWN_ERROR', '无法读取公共 ICD-10 目录', 0)
}

/** 读取已发布的公共目录；查看和刷新均不调用HIS。 */
async function load(background = false) {
  controller?.abort()
  const request = new AbortController()
  controller = request
  if (background) isRefreshing.value = true
  else isLoading.value = true
  error.value = null
  selectedItemId.value = null
  try {
    const result = await listIcd10Directory({
      diagnosisCategory: category.value,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      pageSize: pageSize.value,
    }, request.signal)
    if (!mounted || request.signal.aborted) return
    items.value = result.items
    total.value = result.total
    counts.value = { WESTERN: 0, TRADITIONAL: 0 }
    for (const count of result.counts) counts.value[count.diagnosisCategory] = count.total
  } catch (caught) {
    if (!mounted || request.signal.aborted) return
    const apiError = asApiError(caught)
    if (apiError.code !== 'REQUEST_ABORTED') {
      if (apiError.status === 401) {
        authState.user = null
        authState.isInitialized = true
        await router.replace({ path: '/login', query: { redirect: '/master-data/directory/icd10' } })
      } else {
        error.value = apiError
      }
    }
  } finally {
    if (controller === request && mounted) {
      isLoading.value = false
      isRefreshing.value = false
    }
  }
}

/** 切换西医或中医类别后重新从第一页读取当前公共目录。 */
function selectCategory(value: Icd10DiagnosisCategory) {
  if (category.value === value) return
  category.value = value
  page.value = 1
  void load()
}

/** 应用名称、编码或助记码查询。 */
function applyFilters() {
  page.value = 1
  void load()
}

/** 清除关键词并重新读取当前类别。 */
function clearFilters() {
  keyword.value = ''
  page.value = 1
  void load()
}

/** 展开或收起一条公共诊断的来源追溯字段。 */
function toggleDetail(id: number) {
  selectedItemId.value = selectedItemId.value === id ? null : id
}

/** 双击数据单元格查看详情，不拦截按钮原有操作。 */
function toggleDetailFromRow(id: number, event: MouseEvent) {
  if (event.target instanceof Element && event.target.closest('button')) return
  toggleDetail(id)
}

/** 切换服务端页码。 */
function changePage(value: number) {
  page.value = value
  void load()
}

/** 切换每页数量并重新读取第一页。 */
function changePageSize(value: number) {
  pageSize.value = value
  page.value = 1
  void load()
}

/** 格式化服务端UTC记录时间为北京时间。 */
function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    hour12: false, timeZone: 'Asia/Shanghai',
  }).format(date)
}

onMounted(() => void load())
onBeforeUnmount(() => {
  mounted = false
  controller?.abort()
})
</script>

<template>
  <section class="content icd10-directory" aria-label="平台公共 ICD-10 诊断目录">
    <nav class="dataset-nav" aria-label="基础数据集">
      <RouterLink to="/master-data/directory">综合目录</RouterLink>
      <RouterLink to="/master-data/directory/medical">三大目录</RouterLink>
      <RouterLink class="active" to="/master-data/directory/icd10">ICD-10</RouterLink>
    </nav>

    <div class="icd10-layout">
      <aside class="diagnosis-rail" aria-label="诊断类别">
        <header><h2>诊断类别</h2><span>平台公共</span></header>
        <button v-for="value in icd10DiagnosisCategories" :key="value" type="button"
          :aria-pressed="category === value" :class="{ active: category === value }" @click="selectCategory(value)">
          <strong>{{ categoryLabels[value] }}</strong><span>{{ isLoading || error ? '—' : counts[value].toLocaleString('zh-CN') }}</span>
        </button>
      </aside>

      <section class="icd10-workspace">
        <header class="icd10-context">
          <div>
            <span>当前目录</span>
            <h1>平台公共 ICD-10 诊断目录</h1>
            <p><strong>{{ categoryLabels[category] }}</strong><i aria-hidden="true">/</i>来源未提供诊断版本</p>
          </div>
          <span class="published-state" :class="{ muted: isLoading || error || total === 0 }" role="status">
            <CheckCircle2 v-if="!isLoading && !error && total > 0" :size="16" aria-hidden="true" />
            <Database v-else :size="16" aria-hidden="true" />
            {{ isLoading ? '正在读取' : error ? '读取失败' : total > 0 ? '已发布当前数据' : '尚无已发布数据' }}
          </span>
        </header>
        <p class="public-boundary"><Database :size="15" aria-hidden="true" />该目录不按机构归属或筛选；选择 HIS 调用端点的机构只用于授权与审计，不显示在目录记录中。</p>

        <section class="icd10-panel">
          <ListQueryToolbar :refreshing="isRefreshing" :disabled="isLoading || isRefreshing" @query="applyFilters" @reset="clearFilters" @refresh="load(true)">
            <label class="icd10-search"><Search :size="17" aria-hidden="true" /><input v-model="keyword" maxlength="50" aria-label="搜索诊断名称、ICD编码或助记码" placeholder="搜索诊断名称、ICD 编码或助记码" /></label>
          </ListQueryToolbar>

          <p v-if="error" class="feedback danger" role="alert"><AlertCircle :size="18" aria-hidden="true" /><span><strong>{{ error.message }}</strong><small v-if="error.requestId">请求编号：{{ error.requestId }}</small></span><button class="text-button" type="button" @click="load()">重试</button></p>
          <div v-else-if="isLoading" class="icd10-state" role="status">正在读取已发布的公共诊断目录…</div>
          <div v-else-if="items.length === 0" class="icd10-state" role="status"><Database :size="28" aria-hidden="true" /><strong>{{ keyword.trim() ? '没有符合查询条件的诊断记录' : `尚无${categoryLabels[category]}已发布数据` }}</strong><span>{{ keyword.trim() ? '请调整条件或清除查询。' : '请在“同步批次”发起并完成 ICD-10 同步后再查看。' }}</span></div>
          <div v-else class="icd10-table-wrap">
            <table class="icd10-table">
              <thead><tr><th scope="col">诊断名称 / ICD 编码</th><th scope="col">助记码</th><th scope="col">来源创建时间</th><th scope="col">最近记录</th><th scope="col">操作</th></tr></thead>
              <tbody>
                <template v-for="item in items" :key="item.id">
                  <tr :class="{ expanded: selectedItemId === item.id }" @dblclick="toggleDetailFromRow(item.id, $event)">
                    <td :title="item.diseaseName"><strong>{{ item.diseaseName }}</strong><small>{{ item.diseaseCode }}</small></td>
                    <td>{{ item.mnemonicCode || '—' }}</td>
                    <td>{{ item.sourceCreatedAt }}</td>
                    <td><strong>{{ formatTime(item.lastSeenAt) }}</strong><small>{{ item.latestBatchNo }}</small></td>
                    <td><button type="button" :aria-expanded="selectedItemId === item.id" @click="toggleDetail(item.id)">{{ selectedItemId === item.id ? '收起' : '查看' }}</button></td>
                  </tr>
                  <tr v-if="selectedItemId === item.id && selectedItem" class="detail-row"><td colspan="5"><div class="icd10-detail"><section aria-label="第 1 组：诊断类别与疾病编码"><dl><div><dt>诊断类别</dt><dd>{{ categoryLabels[selectedItem.diagnosisCategory] }}</dd></div><div><dt>疾病编码</dt><dd>{{ selectedItem.diseaseCode }}</dd></div></dl></section><section aria-label="第 2 组：助记码与来源创建时间"><dl><div><dt>助记码</dt><dd>{{ selectedItem.mnemonicCode || '—' }}</dd></div><div><dt>来源创建时间</dt><dd>{{ selectedItem.sourceCreatedAt || '—' }}</dd></div></dl></section><section aria-label="第 3 组：来源疾病 ID 与最近同步批次"><dl><div><dt>来源疾病 ID</dt><dd>{{ selectedItem.sourceDiseaseId || '来源未提供' }}</dd></div><div><dt>最近同步批次</dt><dd>{{ selectedItem.latestBatchNo }}</dd></div></dl></section></div></td></tr>
                </template>
              </tbody>
            </table>
          </div>
          <AdminPagination v-if="!isLoading && !error && total > 0" :page="page" :page-size="pageSize" :total="total" @update:page="changePage" @update:page-size="changePageSize" />
        </section>
      </section>
    </div>
  </section>
</template>

<style scoped>
.icd10-directory { max-width:none; padding-top:16px; }.dataset-nav { margin-bottom:14px; display:flex; gap:8px; border-bottom:1px solid var(--line); overflow-x:auto; }.dataset-nav a { flex:none; padding:11px 13px; border:1px solid transparent; border-bottom:0; border-radius:7px 7px 0 0; color:var(--muted); font-size:13px; text-decoration:none; }.dataset-nav a:hover { background:#f5f8f8; }.dataset-nav a.active { border-color:#d6e5e1; background:#eaf5f2; color:var(--accent); font-weight:650; }
.icd10-layout { display:grid; grid-template-columns:244px minmax(0,1fr); gap:14px; align-items:start; }.diagnosis-rail,.icd10-workspace { min-width:0; border:1px solid var(--line); border-radius:8px; background:var(--surface); overflow:hidden; }.icd10-workspace { container-type:inline-size; }.diagnosis-rail { position:sticky; top:16px; }.diagnosis-rail header { min-height:54px; padding:0 15px; display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid var(--line-soft); }.diagnosis-rail h2 { margin:0; font-size:15px; }.diagnosis-rail header span { color:var(--muted); font-size:11px; }.diagnosis-rail button { width:100%; min-height:52px; padding:0 15px; border:0; border-bottom:1px solid var(--line-soft); display:flex; align-items:center; justify-content:space-between; background:transparent; color:#52676e; text-align:left; font-size:13px; }.diagnosis-rail button:hover { background:#f2f7f5; }.diagnosis-rail button.active { background:#e7f3f0; color:var(--accent-dark); }.diagnosis-rail button span { color:#75908a; font-size:12px; }
.icd10-context { min-height:86px; padding:14px 18px; display:flex; align-items:center; justify-content:space-between; gap:16px; border-bottom:1px solid #d9e8e4; background:#f7fbfa; }.icd10-context span { color:var(--muted); font-size:11px; }.icd10-context h1 { margin:3px 0; color:var(--ink); font-size:18px; }.icd10-context p { margin:0; display:flex; align-items:center; gap:7px; color:#647972; font-size:12px; }.icd10-context p i { color:#9aa8a6; font-style:normal; }.published-state { display:inline-flex; align-items:center; gap:6px; color:var(--accent); font-size:12px; font-weight:650; white-space:nowrap; }.published-state.muted { color:var(--muted); }.public-boundary { margin:0; padding:9px 18px; display:flex; align-items:center; gap:6px; border-bottom:1px solid var(--line-soft); color:#617a73; font-size:11px; line-height:1.5; }
.icd10-panel { min-width:0; }.icd10-search { min-width:0; height:36px; padding:0 11px; display:flex; align-items:center; gap:8px; border:1px solid #ccd9dc; border-radius:6px; color:var(--muted); }.icd10-search:focus-within { border-color:var(--accent); box-shadow:0 0 0 2px rgb(25 130 115 / 12%); }.icd10-search input { width:100%; min-width:0; border:0; outline:0; background:transparent; color:var(--ink); font-size:12px; }.icd10-panel :deep(.standard-list-toolbar) { border:0; border-bottom:1px solid var(--line-soft); border-radius:0; box-shadow:none; }.icd10-panel :deep(.standard-list-toolbar__filters) { flex:1 1 260px; }.icd10-panel :deep(.icd10-search) { flex:1 1 260px; max-width:500px; }
.icd10-state { min-height:320px; padding:36px 20px; display:grid; place-content:center; justify-items:center; gap:8px; color:var(--muted); text-align:center; font-size:12px; }.icd10-state strong { color:var(--ink); font-size:14px; }.icd10-table-wrap { overflow:auto; }.icd10-table { width:100%; min-width:790px; border-collapse:collapse; table-layout:fixed; }.icd10-table th,.icd10-table td { height:55px; padding:0 14px; border-bottom:1px solid var(--line-soft); color:#435963; font-size:12px; text-align:left; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }.icd10-table th { height:40px; background:#f7f9f9; color:#61737a; font-size:11px; font-weight:650; }.icd10-table th:nth-child(1) { width:34%; }.icd10-table th:nth-child(2) { width:14%; }.icd10-table th:nth-child(3),.icd10-table th:nth-child(4) { width:20%; }.icd10-table td strong,.icd10-table td small { display:block; overflow:hidden; text-overflow:ellipsis; }.icd10-table td strong { color:var(--ink); }.icd10-table td small { margin-top:3px; color:var(--muted); font-size:11px; }.icd10-table tbody>tr:not(.detail-row):hover,.icd10-table tr.expanded { background:#f6faf8; }.icd10-table td:last-child,.icd10-table th:last-child { text-align:right; }.icd10-table td:last-child button { min-height:30px; padding:0 10px; border:1px solid #72aa9e; border-radius:4px; background:#fff; color:var(--accent); font-size:12px; }.icd10-table .detail-row > td { height:auto; padding:12px 14px; text-align:left; white-space:normal; }.icd10-detail { --detail-line:#b9cdd0; width:100%; display:grid; grid-template-columns:minmax(0,1.25fr) minmax(0,1fr); border:1px solid #d7e8e3; border-radius:6px; background:var(--surface); }.icd10-detail section { min-width:0; padding:7px 9px; }.icd10-detail section+section { border-left:1px dashed var(--detail-line); }.icd10-detail dl { margin:0; display:grid; gap:0; border-top:1px dashed var(--detail-line); border-left:1px dashed var(--detail-line); border-radius:4px; background:var(--surface); }.icd10-detail-fields { grid-template-columns:repeat(2,minmax(0,1fr)); }.icd10-detail-trace { grid-template-columns:minmax(0,1fr); }.icd10-detail dl div { min-width:0; display:grid; grid-template-columns:100px minmax(0,1fr); border-right:1px dashed var(--detail-line); border-bottom:1px dashed var(--detail-line); background:var(--surface); }.icd10-detail-fields > div.identifier { grid-column:1/-1; }.icd10-detail-fields > div.identifier dd { white-space:nowrap; }.icd10-detail dt,.icd10-detail dd { min-width:0; padding:4px 7px; line-height:1.35; text-align:left; }.icd10-detail dt { border-right:1px dashed var(--detail-line); background:#f6f8f9; color:var(--muted); font-size:11px; font-weight:600; }.icd10-detail dd { margin:0; color:#3a515a; font-size:12px; overflow-wrap:anywhere; }
@media (max-width:780px) { .icd10-layout { grid-template-columns:1fr; }.diagnosis-rail { position:static; display:grid; grid-template-columns:1fr 1fr; }.diagnosis-rail header { grid-column:1/-1; }.icd10-context { align-items:flex-start; flex-wrap:wrap; }.icd10-table { min-width:0; }.icd10-table th:nth-child(2),.icd10-table th:nth-child(3),.icd10-table th:nth-child(4),.icd10-table td:nth-child(2),.icd10-table td:nth-child(3),.icd10-table td:nth-child(4) { display:none; }.icd10-table th:nth-child(1) { width:auto; }.icd10-table th:last-child { width:72px; }.icd10-detail { grid-template-columns:minmax(0,1fr); }.icd10-detail section+section { border-top:1px dashed var(--detail-line); border-left:0; } }
@media (max-width:520px) { .icd10-directory { padding:14px; }.icd10-context h1 { font-size:16px; }.public-boundary { align-items:flex-start; }.icd10-detail-fields { grid-template-columns:minmax(0,1fr); }.icd10-detail-fields > div.identifier dd { white-space:normal; } }
.icd10-detail-fields { display:grid; grid-template-columns:minmax(0,1fr); gap:8px; }.icd10-detail-fields dl { width:max-content; max-width:100%; grid-template-columns:repeat(2,max-content); }.icd10-detail-fields dl.align-center { margin-inline:auto; }.icd10-detail-fields dl div { grid-template-columns:100px max-content; }.icd10-detail-fields dl div.identifier { grid-column:auto; }.icd10-detail-fields dl div.identifier dd { white-space:nowrap; }
@media (max-width:780px) { .icd10-detail-fields dl { width:100%; grid-template-columns:repeat(2,minmax(0,1fr)); }.icd10-detail-fields dl div { grid-template-columns:100px minmax(0,1fr); } }
@media (max-width:520px) { .icd10-detail-fields dl { grid-template-columns:minmax(0,1fr); }.icd10-detail-fields dl div.identifier dd { white-space:normal; } }
.icd10-panel :deep(.standard-list-toolbar) { display:grid; grid-template-columns:minmax(0,1fr) auto auto; align-items:center; }
.icd10-panel :deep(.standard-list-toolbar__filters) { min-width:0; width:100%; flex:none; }
.icd10-panel :deep(.standard-list-toolbar__commands),.icd10-panel :deep(.standard-list-toolbar__utility) { order:0; margin-left:0; }
@media (max-width:860px) {
  .icd10-panel :deep(.standard-list-toolbar) { grid-template-columns:minmax(0,1fr) auto; row-gap:8px; }
  .icd10-panel :deep(.standard-list-toolbar__filters) { grid-column:1/-1; }
  .icd10-panel :deep(.standard-list-toolbar__commands) { grid-column:1; }
  .icd10-panel :deep(.standard-list-toolbar__utility) { grid-column:2; width:auto; justify-content:flex-end; }
}
@media (max-width:780px) {
  .icd10-detail-fields dl { width:100%; grid-template-columns:repeat(2,minmax(0,1fr)); }
  .icd10-detail-fields dl div { grid-template-columns:100px minmax(0,1fr); }
  .icd10-detail-fields dl div.identifier dd { white-space:normal; overflow-wrap:anywhere; }
}
.icd10-detail { grid-template-columns:repeat(3,minmax(0,1fr)); }
.icd10-detail section { min-width:0; padding:7px 9px; }
.icd10-detail section+section { border-top:0; border-left:1px dashed var(--detail-line); }
.icd10-detail section > dl { width:100%; min-width:0; grid-template-columns:minmax(0,1fr); }
.icd10-detail section > dl > div { grid-template-columns:100px minmax(0,1fr); }
.icd10-detail section > dl dt,.icd10-detail section > dl dd { min-width:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; overflow-wrap:normal; }
@media (max-width:780px) {
  .icd10-detail { grid-template-columns:minmax(0,1fr); }
  .icd10-detail section+section { border-top:1px dashed var(--detail-line); border-left:0; }
}
</style>
