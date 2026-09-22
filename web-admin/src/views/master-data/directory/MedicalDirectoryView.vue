<!-- 医疗目录正式页面：只读展示100-004/100-005已完成数量核对后写入的当前数据。 -->
<script setup lang="ts">
import { ChevronDown, ChevronRight } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminPagination from '@/components/AdminPagination.vue'
import {
  listMedicalDirectory,
  medicalDirectoryTypes,
  type MedicalDirectoryItem,
  type MedicalDirectoryType,
} from '@/api/master-data/medicalDirectory'
import DirectoryLayout from './DirectoryLayout.vue'
import { authState } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'

const router = useRouter()
const items = ref<MedicalDirectoryItem[]>([])
const counts = ref<Record<MedicalDirectoryType, number>>({
  TRADITIONAL_MEDICINE: 0,
  WESTERN_MEDICINE: 0,
  TREATMENT: 0,
  CONSUMABLE: 0,
})
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const organizationCode = ref('')
const directoryType = ref<MedicalDirectoryType>('TRADITIONAL_MEDICINE')
const keyword = ref('')
const selectedItemId = ref<number | null>(null)
const isLoading = ref(true)
const isRefreshing = ref(false)
const error = ref<ApiClientError | null>(null)
let controller: AbortController | null = null
let mounted = true

/** 当前医疗目录类型对应的业务名称。 */
const typeLabels: Record<MedicalDirectoryType, string> = {
  TRADITIONAL_MEDICINE: '中药',
  WESTERN_MEDICINE: '西药',
  TREATMENT: '诊疗',
  CONSUMABLE: '耗材',
}

/** 当前展开查看来源链路的目录记录。 */
const selectedItem = computed(() =>
  items.value.find((item) => item.id === selectedItemId.value) ?? null,
)

/** 将未知异常转换为可展示的API错误。 */
function asApiError(caught: unknown) {
  return caught instanceof ApiClientError
    ? caught
    : new ApiClientError('UNKNOWN_ERROR', '无法读取医疗目录', 0)
}

/** 读取当前有效医疗目录；查看不会调用HIS或改变数据。 */
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
    const result = await listMedicalDirectory(
      {
        organizationCode: organizationCode.value || undefined,
        directoryType: directoryType.value,
        keyword: keyword.value.trim() || undefined,
        page: page.value,
        pageSize: pageSize.value,
      },
      request.signal,
    )
    if (!mounted || request.signal.aborted) return
    items.value = result.items
    total.value = result.total
    counts.value = {
      TRADITIONAL_MEDICINE: 0,
      WESTERN_MEDICINE: 0,
      TREATMENT: 0,
      CONSUMABLE: 0,
    }
    for (const count of result.counts) counts.value[count.directoryType] = count.total
  } catch (caught) {
    if (!mounted || request.signal.aborted) return
    const apiError = asApiError(caught)
    if (apiError.code !== 'REQUEST_ABORTED') {
      if (apiError.status === 401) {
        authState.user = null
        authState.isInitialized = true
        await router.replace({
          path: '/login',
          query: { redirect: '/master-data/directory/medical' },
        })
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

/** 机构上下文确定后，清除旧页码和数量再读取当前目录。 */
function selectOrganization() {
  page.value = 1
  total.value = 0
  items.value = []
  for (const type of medicalDirectoryTypes) counts.value[type] = 0
  void load()
}

/** 切换医疗目录类型。 */
function selectType(type: MedicalDirectoryType) {
  if (directoryType.value === type) return
  directoryType.value = type
  page.value = 1
  void load()
}

/** 应用关键词查询。 */
function applyFilters() {
  page.value = 1
  void load()
}

/** 展开或收起一条目录的来源链路。 */
function toggleDetail(id: number) {
  selectedItemId.value = selectedItemId.value === id ? null : id
}

/** 切换服务端页码。 */
function changePage(value: number) {
  page.value = value
  void load()
}

/** 切换每页条数并重新读取第一页。 */
function changePageSize(value: number) {
  pageSize.value = value
  page.value = 1
  void load()
}

/** 格式化服务端UTC时间。 */
function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

onBeforeUnmount(() => {
  mounted = false
  controller?.abort()
})
</script>

<template>
  <DirectoryLayout
    v-model:organization-code="organizationCode"
    v-model:keyword="keyword"
    title="医疗目录"
    :types="medicalDirectoryTypes"
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
    <div class="directory-table-wrap">
          <table class="directory-table">
            <thead><tr><th>目录编码</th><th>目录名称</th><th>类别</th><th>规格 / 单位</th><th>HIS 启用状态</th><th>最近同步</th><th>来源批次</th><th><span class="visually-hidden">操作</span></th></tr></thead>
            <tbody>
              <template v-for="item in items" :key="item.id">
                <tr :class="{ expanded: selectedItemId === item.id }"><td :title="item.sourceRecordCode">{{ item.sourceRecordCode }}</td><td :title="item.sourceRecordName"><strong>{{ item.sourceRecordName }}</strong><small v-if="item.mnemonicCode">{{ item.mnemonicCode }}</small></td><td>{{ item.categoryName }}</td><td>{{ item.specification || '—' }}<template v-if="item.unit"> / {{ item.unit }}</template></td><td>{{ item.sourceEnabledFlag }}</td><td>{{ formatTime(item.lastSeenAt) }}</td><td><RouterLink class="batch-link" :to="`/master-data/batches/${item.latestBatchId}`">{{ item.latestBatchNo }}</RouterLink></td><td class="directory-actions"><button type="button" :aria-expanded="selectedItemId === item.id" @click="toggleDetail(item.id)"><ChevronDown v-if="selectedItemId === item.id" :size="15" /><ChevronRight v-else :size="15" />{{ selectedItemId === item.id ? '收起' : '查看' }}</button></td></tr>
                <tr v-if="selectedItemId === item.id && selectedItem" class="directory-detail-row"><td colspan="8"><div class="directory-detail"><section><h3>平台当前数据</h3><dl><div><dt>目录类型</dt><dd>{{ typeLabels[selectedItem.directoryType] }}</dd></div><div><dt>目录编码</dt><dd>{{ selectedItem.sourceRecordCode }}</dd></div><div><dt>目录名称</dt><dd>{{ selectedItem.sourceRecordName }}</dd></div><div><dt>类别</dt><dd>{{ selectedItem.categoryName }}</dd></div><div><dt>规格 / 单位</dt><dd>{{ selectedItem.specification || '—' }}{{ selectedItem.unit ? ` / ${selectedItem.unit}` : '' }}</dd></div><div><dt>生产厂家</dt><dd>{{ selectedItem.manufacturerName || '—' }}</dd></div><div><dt>HIS 启用值</dt><dd>{{ selectedItem.sourceEnabledFlag }}</dd></div><div><dt>来源创建时间</dt><dd>{{ selectedItem.sourceCreatedAt }}</dd></div></dl></section><section><h3>来源追溯</h3><dl><div><dt>来源系统</dt><dd>基层 HIS</dd></div><div><dt>来源交易</dt><dd>100-004 · 医疗目录查询</dd></div><div><dt>数量核对</dt><dd>同范围 100-005 已在同步时校对</dd></div><div><dt>平台机构</dt><dd>{{ selectedItem.organizationName }}</dd></div><div><dt>实际请求机构参数</dt><dd>{{ selectedItem.sourceOrganizationId || '历史批次未保存' }}</dd></div><div><dt>最近成功同步</dt><dd>{{ formatTime(selectedItem.lastSeenAt) }}</dd></div><div><dt>同步记录</dt><dd><RouterLink class="batch-link" :to="`/master-data/batches/${selectedItem.latestBatchId}`">{{ selectedItem.latestBatchNo }}</RouterLink></dd></div></dl></section></div></td></tr>
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
.directory-table th:nth-child(1){width:140px}
.directory-table th:nth-child(2){width:200px}
.directory-table th:nth-child(3){width:110px}
.directory-table th:nth-child(4){width:150px}
.directory-table th:nth-child(5){width:110px}
.directory-table th:nth-child(6){width:136px}
.directory-table th:nth-child(7){width:174px}
.directory-table th:nth-child(8){width:78px}
</style>
