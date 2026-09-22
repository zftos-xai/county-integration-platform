<!-- 医院综合目录页面：按机构和目录类型查看当前有效数据及其真实来源链路。 -->
<script setup lang="ts">
import { ChevronDown, ChevronRight } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminPagination from '@/components/AdminPagination.vue'
import { hospitalDirectoryTypes, listHospitalDirectory, type HospitalDirectoryItem, type HospitalDirectoryType } from '@/api/master-data/directory'
import DirectoryLayout from './DirectoryLayout.vue'
import { authState } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'

const router = useRouter()
const items = ref<HospitalDirectoryItem[]>([])
const counts = ref<Record<HospitalDirectoryType, number>>({ DEPARTMENT: 0, DOCTOR: 0, WARD: 0, BED: 0 })
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const organizationCode = ref('')
const directoryType = ref<HospitalDirectoryType>('DEPARTMENT')
const keyword = ref('')
const selectedItemId = ref<number | null>(null)
const tableWrap = ref<HTMLElement | null>(null)
const isLoading = ref(true)
const isRefreshing = ref(false)
const error = ref<ApiClientError | null>(null)
let controller: AbortController | null = null
let mounted = true

/** 当前有效目录类型对应的业务名称。 */
const typeLabels: Record<HospitalDirectoryType, string> = { DEPARTMENT: '科室', DOCTOR: '医生', WARD: '病区', BED: '床位' }

/** 当前展开查看来源链路的目录记录。 */
const selectedItem = computed(() => items.value.find((item) => item.id === selectedItemId.value) ?? null)

/** 将未知异常转换为可展示的API错误。 */
function asApiError(caught: unknown) {
  return caught instanceof ApiClientError ? caught : new ApiClientError('UNKNOWN_ERROR', '无法读取医院综合目录', 0)
}

/** 读取服务端当前有效目录；查看不会调用HIS或改变目录。 */
async function load(background = false) {
  if (!organizationCode.value) return
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
    if (!mounted || request.signal.aborted) return
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

/** 机构上下文确定后，清除旧页码和数量再读取当前目录。 */
function selectOrganization() {
  page.value = 1
  total.value = 0
  items.value = []
  for (const type of hospitalDirectoryTypes) counts.value[type] = 0
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

/** 展开目录时将横向滚动复位，让字段名从第一列开始可见。 */
function toggleDetail(id: number) {
  selectedItemId.value = selectedItemId.value === id ? null : id
  if (selectedItemId.value && tableWrap.value) tableWrap.value.scrollLeft = 0
}

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

onBeforeUnmount(() => { mounted = false; controller?.abort() })
</script>

<template>
  <DirectoryLayout
    v-model:organization-code="organizationCode"
    v-model:keyword="keyword"
    title="医院综合目录"
    :types="hospitalDirectoryTypes"
    :type-labels="typeLabels"
    :directory-type="directoryType"
    :counts="counts"
    :total="total"
    :is-loading="isLoading"
    :is-refreshing="isRefreshing"
    :is-empty="items.length === 0"
    :error="error"
    @organization-change="selectOrganization"
    @type-change="selectType"
    @search="applyFilters"
    @refresh="load(true)"
    @retry="load()"
  >
    <div ref="tableWrap" class="directory-table-wrap">
          <table class="directory-table">
            <thead><tr><th>目录编码</th><th>{{ typeLabels[directoryType] }}名称</th><th>助记码</th><th>类别</th><th>有效关系</th><th>最近同步</th><th>来源批次</th><th><span class="visually-hidden">操作</span></th></tr></thead>
            <tbody>
              <template v-for="item in items" :key="item.id">
                <tr :class="{ expanded: selectedItemId === item.id }"><td class="directory-code" :title="item.sourceRecordCode">{{ item.sourceRecordCode }}</td><td :title="item.sourceRecordName"><strong>{{ item.sourceRecordName }}</strong></td><td :title="item.mnemonicCode || '—'">{{ item.mnemonicCode || '—' }}</td><td :title="item.categoryName || '—'">{{ item.categoryName || '—' }}</td><td>{{ item.relationCount ? `${item.relationCount} 条` : '—' }}</td><td>{{ formatTime(item.lastSeenAt) }}</td><td><RouterLink class="batch-link" :to="`/master-data/batches/${item.latestBatchId}`">{{ item.latestBatchNo }}</RouterLink></td><td class="directory-actions"><button type="button" :aria-expanded="selectedItemId === item.id" @click="toggleDetail(item.id)"><ChevronDown v-if="selectedItemId === item.id" :size="15" /><ChevronRight v-else :size="15" />{{ selectedItemId === item.id ? '收起' : '查看' }}</button></td></tr>
                <tr v-if="selectedItemId === item.id && selectedItem" class="directory-detail-row"><td colspan="8"><div class="directory-detail directory-detail--hospital" role="region" aria-label="医院综合目录详情与来源追溯" tabindex="0">
                  <section><h4>平台当前数据</h4><dl><div><dt>目录类型</dt><dd>{{ typeLabels[selectedItem.directoryType] }}</dd></div><div><dt>目录编码</dt><dd>{{ selectedItem.sourceRecordCode }}</dd></div><div><dt>目录名称</dt><dd>{{ selectedItem.sourceRecordName }}</dd></div><div><dt>助记码</dt><dd>{{ selectedItem.mnemonicCode || '—' }}</dd></div><div><dt>类别</dt><dd>{{ selectedItem.categoryName || '—' }}</dd></div><div><dt>有效关系</dt><dd>{{ selectedItem.relationCount ? `${selectedItem.relationCount} 条` : '—' }}</dd></div></dl></section>
                  <section><h4>来源追溯</h4><dl><div><dt>来源系统</dt><dd>基层 HIS</dd></div><div><dt>平台机构</dt><dd>{{ selectedItem.organizationName }}</dd></div><div><dt>平台机构编码</dt><dd>{{ selectedItem.organizationCode }}</dd></div><div><dt>来源机构标识</dt><dd>{{ selectedItem.sourceOrganizationCode || 'HIS 本次未返回' }}</dd></div><div><dt>来源交易</dt><dd>100-003 · 医院综合目录查询</dd></div><div><dt>最近成功同步</dt><dd>{{ formatTime(selectedItem.lastSeenAt) }}</dd></div><div><dt>同步记录</dt><dd><RouterLink class="batch-link" :to="`/master-data/batches/${selectedItem.latestBatchId}`">{{ selectedItem.latestBatchNo }}</RouterLink></dd></div></dl></section>
                </div></td></tr>
              </template>
            </tbody>
          </table>
        </div>
    <template #pagination>
      <AdminPagination :total="total" :page="page" :page-size="pageSize" @update:page="changePage" @update:page-size="changePageSize" />
    </template>
  </DirectoryLayout>
</template>

<style scoped src="@/views/master-data/directory/directory-records.css"></style>
<style scoped>
.directory-table th:nth-child(1){width:150px}
.directory-table th:nth-child(2){width:160px}
.directory-table th:nth-child(3){width:100px}
.directory-table th:nth-child(4){width:110px}
.directory-table th:nth-child(5){width:88px}
.directory-table th:nth-child(6){width:138px}
.directory-table th:nth-child(7){width:184px}
.directory-table th:nth-child(8){width:82px}
</style>
