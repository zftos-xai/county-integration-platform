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
const sourceEnabledFlag = ref<'' | '是' | '否'>('')
const selectedItemId = ref<number | null>(null)
const tableWrap = ref<HTMLElement | null>(null)
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
        sourceEnabledFlag: sourceEnabledFlag.value || undefined,
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

/** 应用关键词和HIS启用原值筛选。 */
function applyFilters() {
  page.value = 1
  void load()
}

/** 清除医疗目录专有条件，再由共用布局提交无筛选查询。 */
function clearExtraFilters() {
  sourceEnabledFlag.value = ''
}

/** 展开目录时将横向滚动复位，让字段名从第一列开始可见。 */
function toggleDetail(id: number) {
  selectedItemId.value = selectedItemId.value === id ? null : id
  if (selectedItemId.value && tableWrap.value) tableWrap.value.scrollLeft = 0
}

/** 双击数据单元格查看详情，不拦截按钮和链接原有的操作。 */
function toggleDetailFromRow(id: number, event: MouseEvent) {
  if (event.target instanceof Element && event.target.closest('a, button')) return
  toggleDetail(id)
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
    :has-extra-filters="Boolean(sourceEnabledFlag)"
    :error="error"
    @organization-change="selectOrganization"
    @type-change="selectType"
    @search="applyFilters"
    @clear="clearExtraFilters"
    @refresh="load(true)"
    @retry="load()"
  >
    <template #filters>
      <label class="directory-extra-filter">
        <span class="visually-hidden">按 HIS 启用值筛选</span>
        <select v-model="sourceEnabledFlag" aria-label="按 HIS 启用值筛选">
          <option value="">HIS 启用值：全部</option>
          <option value="是">HIS 启用值：是</option>
          <option value="否">HIS 启用值：否</option>
        </select>
      </label>
    </template>
    <div ref="tableWrap" class="directory-table-wrap">
      <table class="directory-table">
        <thead>
          <tr>
            <th scope="col">名称 / 目录编码</th>
            <th scope="col">类别 / 状态</th>
            <th scope="col">规格 / 包装</th>
            <th scope="col">
              {{ directoryType === 'TREATMENT' ? '来源信息' : directoryType === 'CONSUMABLE' ? '厂家 / 材质' : '厂家 / 药品标识' }}
            </th>
            <th scope="col">地区</th>
            <th scope="col">HIS 启用值</th>
            <th scope="col"><span class="visually-hidden">操作</span></th>
          </tr>
        </thead>
        <tbody>
          <template v-for="item in items" :key="item.id">
            <tr :class="{ expanded: selectedItemId === item.id }" @dblclick="toggleDetailFromRow(item.id, $event)">
              <td :title="item.sourceRecordName">
                <strong>{{ item.sourceRecordName }}</strong>
                <small class="directory-code" :title="item.sourceRecordCode">{{ item.sourceRecordCode }}</small>
              </td>
              <td>
                <strong>{{ item.categoryName }}</strong>
                <small v-if="item.dosageForm">剂型 {{ item.dosageForm }}</small>
              </td>
              <td>
                <strong>
                  {{ item.specification || (item.unit ? `单位 ${item.unit}` : '—') }}<template v-if="item.specification && item.unit"> / {{ item.unit }}</template>
                </strong>
                <small v-if="item.packageUnit || item.conversionFactor">
                  包装 {{ item.packageUnit || '—' }}<template v-if="item.conversionFactor"> · 换算 {{ item.conversionFactor }}</template>
                </small>
              </td>
              <td>
                <strong v-if="item.manufacturerName">{{ item.manufacturerName }}</strong>
                <strong v-else>创建 {{ item.sourceCreatedAt }}</strong>
                <small v-if="item.approvalNumber">批准/注册号 {{ item.approvalNumber }}</small>
                <small v-if="item.standardCode">本位码 {{ item.standardCode }}</small>
                <small v-if="item.packageMaterial">材质 {{ item.packageMaterial }}</small>
                <small v-if="item.category">来源类别 {{ item.category }}</small>
              </td>
              <td :title="item.region || 'HIS 未返回地区'">{{ item.region || '—' }}</td>
              <td :title="item.sourceEnabledFlag">{{ item.sourceEnabledFlag }}</td>
              <td class="directory-actions">
                <button type="button" :aria-expanded="selectedItemId === item.id" @click="toggleDetail(item.id)">
                  <ChevronDown v-if="selectedItemId === item.id" :size="15" />
                  <ChevronRight v-else :size="15" />
                  {{ selectedItemId === item.id ? '收起' : '查看' }}
                </button>
              </td>
            </tr>
            <tr v-if="selectedItemId === item.id && selectedItem" class="directory-detail-row">
              <td colspan="7">
                <div class="directory-detail" role="region" aria-label="医疗目录详情与来源追溯" tabindex="0">
                  <section>
                    <dl>
                      <div><dt>目录类型</dt><dd>{{ typeLabels[selectedItem.directoryType] }}</dd></div>
                      <div><dt>目录编码</dt><dd>{{ selectedItem.sourceRecordCode }}</dd></div>
                      <div><dt>助记码</dt><dd>{{ selectedItem.mnemonicCode || '—' }}</dd></div>
                      <div><dt>目录名称</dt><dd>{{ selectedItem.sourceRecordName }}</dd></div>
                      <div><dt>目录类别名称</dt><dd>{{ selectedItem.categoryName }}</dd></div>
                      <div><dt>生产厂家</dt><dd>{{ selectedItem.manufacturerName || '—' }}</dd></div>
                      <div><dt>规格</dt><dd>{{ selectedItem.specification || '—' }}</dd></div>
                      <div><dt>来源创建时间</dt><dd>{{ selectedItem.sourceCreatedAt || '—' }}</dd></div>
                      <div><dt>单位</dt><dd>{{ selectedItem.unit || '—' }}</dd></div>
                      <div><dt>国药准字号</dt><dd>{{ selectedItem.approvalNumber || '—' }}</dd></div>
                      <div><dt>剂型</dt><dd>{{ selectedItem.dosageForm || '—' }}</dd></div>
                      <div><dt>药品本位码</dt><dd>{{ selectedItem.standardCode || '—' }}</dd></div>
                      <div><dt>包装单位</dt><dd>{{ selectedItem.packageUnit || '—' }}</dd></div>
                      <div><dt>炮制方法</dt><dd>{{ selectedItem.processingMethod || '—' }}</dd></div>
                      <div><dt>转换系数</dt><dd>{{ selectedItem.conversionFactor || '—' }}</dd></div>
                      <div><dt>包装材质</dt><dd>{{ selectedItem.packageMaterial || '—' }}</dd></div>
                      <div><dt>HIS 启用值</dt><dd>{{ selectedItem.sourceEnabledFlag }}</dd></div>
                      <div><dt>地区</dt><dd>{{ selectedItem.region || '—' }}</dd></div>
                      <div><dt>来源类别</dt><dd>{{ selectedItem.category || '—' }}</dd></div>
                      <div><dt>备注</dt><dd>{{ selectedItem.remark || '—' }}</dd></div>
                    </dl>
                  </section>
                  <section>
                    <dl>
                      <div><dt>来源系统</dt><dd>基层 HIS</dd></div>
                      <div><dt>来源交易</dt><dd>100-004 · 医疗目录查询</dd></div>
                      <div><dt>数量核对</dt><dd>同范围 100-005 已在同步时校对</dd></div>
                      <div><dt>平台机构</dt><dd>{{ selectedItem.organizationName }}</dd></div>
                      <div><dt>实际请求机构参数</dt><dd>{{ selectedItem.sourceOrganizationId || '历史批次未保存' }}</dd></div>
                      <div><dt>最近成功同步</dt><dd>{{ formatTime(selectedItem.lastSeenAt) }}</dd></div>
                      <div>
                        <dt>同步记录</dt>
                        <dd><RouterLink class="batch-link" :to="`/master-data/batches/${selectedItem.latestBatchId}`">{{ selectedItem.latestBatchNo }}</RouterLink></dd>
                      </div>
                    </dl>
                  </section>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <template #pagination>
      <AdminPagination
        :total="total"
        :page="page"
        :page-size="pageSize"
        @update:page="changePage"
        @update:page-size="changePageSize"
      />
    </template>
  </DirectoryLayout>
</template>

<style scoped src="@/views/master-data/directory/directory-records.css"></style>
<style scoped>
.directory-table { min-width: 1140px; }
.directory-table th:nth-child(1) { width: 23%; }
.directory-table th:nth-child(2) { width: 13%; }
.directory-table th:nth-child(3) { width: 17%; }
.directory-table th:nth-child(4) { width: 19%; }
.directory-table th:nth-child(5) { width: 11%; }
.directory-table th:nth-child(6) { width: 9%; }
.directory-table th:nth-child(7) { width: 8%; }
.directory-table tbody tr:not(.directory-detail-row) td { height: 60px; }
.directory-table td small { overflow: hidden; text-overflow: ellipsis; }
.directory-table .directory-detail-row > td { height: auto; }
.directory-extra-filter { flex:0 1 150px; min-width:120px; height:36px; padding:0 10px; border:1px solid #ccd9dc; border-radius:6px; display:flex; align-items:center; }
.directory-extra-filter:focus-within { border-color:var(--accent); box-shadow:0 0 0 2px rgb(25 130 115 / 12%); }
.directory-extra-filter select { min-width:0; width:100%; border:0; outline:0; background:transparent; color:var(--ink); font-size:12px; cursor:pointer; }
</style>
