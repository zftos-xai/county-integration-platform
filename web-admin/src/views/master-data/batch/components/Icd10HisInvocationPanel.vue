<!-- ICD-10批次详情的脱敏HIS调用记录区块：分页展示100-006和100-007，不重发来源请求。 -->
<script setup lang="ts">
import { ChevronDown, ChevronRight, ChevronRight as ChevronRightIcon, FileWarning } from 'lucide-vue-next'
import { ref } from 'vue'
import type { Icd10HisInvocationPage } from '@/api/master-data/batch'
import AdminPagination from '@/components/AdminPagination.vue'
import AdminTableFrame from '@/components/AdminTableFrame.vue'
import { formatBatchTime } from '../batchTime'

defineProps<{
  page: Icd10HisInvocationPage | null
  loading: boolean
}>()

const emit = defineEmits<{
  changePage: [page: number]
  changePageSize: [pageSize: number]
}>()

const isSectionExpanded = ref(true)

/** 将来源类别转换为面向业务人员的稳定标签。 */
function categoryLabel(value: Icd10HisInvocationPage['items'][number]['diagnosisCategory']) {
  return value === 'WESTERN' ? '西医诊断' : '中医诊断'
}

/** 将调用终态转为不依赖颜色的受控文字。 */
function outcomeLabel(value: Icd10HisInvocationPage['items'][number]['outcomeStatus']) {
  return ({ SUCCESS: '成功', FAILURE: 'HIS明确失败', NO_RESPONSE: '结果未知', INVALID_RESPONSE: '响应不可确认' })[value]
}

</script>

<template>
  <section class="record-card his-invocation-card">
    <div class="card-heading">
      <div><h3>HIS 请求记录</h3><p>仅展示脱敏请求参数与受控响应摘要，不保存或显示完整 SOAP 报文、服务地址和凭证。</p></div>
      <div class="card-heading-actions"><span v-if="page" class="record-total">{{ page.total }} 次调用</span><button class="section-toggle-button" type="button" :aria-expanded="isSectionExpanded" @click="isSectionExpanded = !isSectionExpanded"><ChevronDown v-if="isSectionExpanded" :size="15" /><ChevronRightIcon v-else :size="15" />{{ isSectionExpanded ? '收起' : '展开' }}</button></div>
    </div>
    <template v-if="isSectionExpanded">
      <div v-if="loading" class="panel-state" role="status">正在读取已保存的 HIS 调用记录…</div>
      <div v-else-if="page && page.total === 0" class="panel-state"><FileWarning :size="18" />该历史批次没有调用追踪记录；不能据此推断 HIS 未被调用。</div>
      <template v-else-if="page">
      <AdminTableFrame label="HIS 调用记录">
        <table class="work-table invocation-table">
          <thead><tr><th>请求时间</th><th>交易 / 类别</th><th>请求范围</th><th>结果</th><th>数量 / 耗时</th><th>请求与响应摘要</th></tr></thead>
          <tbody>
            <tr v-for="item in page.items" :key="item.id">
              <td>{{ formatBatchTime(item.requestedAt) }}</td>
              <td><strong>{{ item.tradeCode }}</strong><small>{{ categoryLabel(item.diagnosisCategory) }} · #{{ item.invocationSequence }}</small></td>
              <td>{{ item.pageStart === null ? '同范围数量查询' : `${item.pageStart}–${item.pageEnd}` }}</td>
              <td><strong>{{ outcomeLabel(item.outcomeStatus) }}</strong><small v-if="item.resultCode">结果码：{{ item.resultCode }}</small></td>
              <td>{{ item.returnedCount === null ? '数量未确认' : `${item.returnedCount} 条` }}<small>{{ item.durationMs }} ms</small></td>
              <td><span>{{ item.requestSummary }}</span><small>{{ item.responseSummary ?? '未取得可展示的响应摘要' }}</small></td>
            </tr>
          </tbody>
        </table>
      </AdminTableFrame>
      <AdminPagination :total="page.total" :page="page.page" :page-size="page.pageSize" :page-sizes="[10, 20, 50]" @update:page="emit('changePage', $event)" @update:page-size="emit('changePageSize', $event)" />
      </template>
    </template>
  </section>
</template>

<style scoped>
.card-heading { min-height: 48px; padding: 9px 16px; border-bottom: 1px solid #e9eef0; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.card-heading h3 { margin: 0; padding: 0; border: 0; }
.card-heading p { margin: 3px 0 0; color: #687d84; font-size: 12px; line-height: 1.5; }
.card-heading-actions { flex: none; display: flex; align-items: center; gap: 12px; }
.record-total { flex: none; color: #287a6b; font-size: 12px; font-weight: 700; }
.section-toggle-button { min-height: 28px; padding: 0 7px; border: 1px solid #b9d2cc; border-radius: 4px; background: #fff; color: #167467; display: inline-flex; align-items: center; gap: 3px; font-size: 11px; cursor: pointer; }
.section-toggle-button:hover { background: #f1f8f5; }
.panel-state { min-height: 72px; padding: 14px 16px; color: #63777e; display: flex; align-items: center; gap: 8px; font-size: 13px; }
.invocation-table td { vertical-align: top; }
.invocation-table strong, .invocation-table small, .invocation-table span { display: block; }
.invocation-table small { margin-top: 3px; color: #72858b; font-size: 11px; line-height: 1.45; }
.invocation-table td:last-child { min-width: 280px; max-width: 460px; line-height: 1.5; overflow-wrap: anywhere; }
@media (max-width: 760px) { .card-heading { align-items: flex-start; flex-direction: column; } .card-heading-actions { width: 100%; justify-content: space-between; } }
</style>
