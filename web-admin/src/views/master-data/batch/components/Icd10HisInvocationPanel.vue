<!-- ICD-10批次详情的脱敏HIS调用事实区块：分页展示100-006和100-007，不重发来源请求。 -->
<script setup lang="ts">
import { ChevronDown, ChevronLeft, ChevronRight, ChevronRight as ChevronRightIcon, FileWarning } from 'lucide-vue-next'
import { ref, watch } from 'vue'
import type { Icd10HisInvocationPage } from '@/api/master-data/batch'
import { formatBatchTime } from '../batchTime'

const props = defineProps<{
  page: Icd10HisInvocationPage | null
  loading: boolean
}>()

const emit = defineEmits<{
  changePage: [page: number]
}>()

const expandedInvocationId = ref<number | null>(null)
const isSectionExpanded = ref(true)

/** 在当前页只展开一条调用，避免详情把表格撑成连续长文本。 */
function toggleInvocation(id: number) {
  expandedInvocationId.value = expandedInvocationId.value === id ? null : id
}

watch(() => props.page?.page, () => {
  expandedInvocationId.value = null
})

/** 将来源类别转换为面向业务人员的稳定标签。 */
function categoryLabel(value: Icd10HisInvocationPage['items'][number]['diagnosisCategory']) {
  return value === 'WESTERN' ? '西医诊断' : '中医诊断'
}

/** 将调用终态转为不依赖颜色的受控文字。 */
function outcomeLabel(value: Icd10HisInvocationPage['items'][number]['outcomeStatus']) {
  return ({ SUCCESS: '成功', FAILURE: 'HIS明确失败', NO_RESPONSE: '结果未知', INVALID_RESPONSE: '响应不可确认' })[value]
}

/** 由页码和页大小确定是否还能读取下一页。 */
function canAdvance() {
  return props.page !== null && props.page.page * props.page.pageSize < props.page.total
}
</script>

<template>
  <section class="record-card his-invocation-card">
    <div class="card-heading">
      <div><h3>HIS 请求记录</h3><p>仅展示脱敏请求参数与受控响应摘要，不保存或显示完整 SOAP 报文、端点和凭证。</p></div>
      <div class="card-heading-actions"><span v-if="page" class="record-total">{{ page.total }} 次调用</span><button class="section-toggle-button" type="button" :aria-expanded="isSectionExpanded" @click="isSectionExpanded = !isSectionExpanded"><ChevronDown v-if="isSectionExpanded" :size="15" /><ChevronRightIcon v-else :size="15" />{{ isSectionExpanded ? '收起' : '展开' }}</button></div>
    </div>
    <template v-if="isSectionExpanded">
      <div v-if="loading" class="panel-state" role="status">正在读取已保存的 HIS 调用事实…</div>
      <div v-else-if="page && page.total === 0" class="panel-state"><FileWarning :size="18" />该历史批次没有调用追踪记录；不能据此推断 HIS 未被调用。</div>
      <template v-else-if="page">
      <div class="table-scroll" role="region" aria-label="HIS调用记录" tabindex="0">
        <table class="work-table invocation-table">
          <thead><tr><th>请求时间</th><th>交易 / 类别</th><th>请求范围</th><th>结果</th><th>数量 / 耗时</th><th>请求与响应摘要</th><th class="invocation-actions">操作</th></tr></thead>
          <tbody>
            <template v-for="item in page.items" :key="item.id">
            <tr :class="{ 'is-expanded': expandedInvocationId === item.id }">
              <td>{{ formatBatchTime(item.requestedAt) }}</td>
              <td><strong>{{ item.tradeCode }}</strong><small>{{ categoryLabel(item.diagnosisCategory) }} · #{{ item.invocationSequence }}</small></td>
              <td>{{ item.pageStart === null ? '同范围数量查询' : `${item.pageStart}–${item.pageEnd}` }}</td>
              <td><strong>{{ outcomeLabel(item.outcomeStatus) }}</strong><small v-if="item.resultCode">结果码：{{ item.resultCode }}</small></td>
              <td>{{ item.returnedCount === null ? '数量未确认' : `${item.returnedCount} 条` }}<small>{{ item.durationMs }} ms</small></td>
              <td><span>{{ item.requestSummary }}</span><small>{{ item.responseSummary ?? '未取得可展示的响应摘要' }}</small></td>
              <td class="invocation-actions"><button class="invocation-detail-button" type="button" :aria-expanded="expandedInvocationId === item.id" @click="toggleInvocation(item.id)"><ChevronDown v-if="expandedInvocationId === item.id" :size="14" /><ChevronRightIcon v-else :size="14" />{{ expandedInvocationId === item.id ? '收起' : '详情' }}</button></td>
            </tr>
            <tr v-if="expandedInvocationId === item.id" class="invocation-detail-row">
              <td colspan="7">
                <div class="invocation-detail">
                  <div class="invocation-detail-grid">
                    <div><dt>请求时间</dt><dd>{{ formatBatchTime(item.requestedAt) }}</dd></div>
                    <div><dt>完成时间</dt><dd>{{ formatBatchTime(item.completedAt) }}</dd></div>
                    <div><dt>交易 / 类别</dt><dd>{{ item.tradeCode }} · {{ categoryLabel(item.diagnosisCategory) }} · 第 {{ item.invocationSequence }} 次</dd></div>
                    <div><dt>请求范围</dt><dd>{{ item.pageStart === null ? '同范围数量查询' : `${item.pageStart}–${item.pageEnd}` }}</dd></div>
                    <div><dt>结果 / 结果码</dt><dd>{{ outcomeLabel(item.outcomeStatus) }} · {{ item.resultCode ?? '—' }}</dd></div>
                    <div><dt>返回数量 / 耗时</dt><dd>{{ item.returnedCount === null ? '未确认' : `${item.returnedCount} 条` }} · {{ item.durationMs }} ms</dd></div>
                  </div>
                  <div class="invocation-payload-grid">
                    <section><h4>请求摘要</h4><p>{{ item.requestSummary }}</p></section>
                    <section><h4>响应摘要</h4><p>{{ item.responseSummary ?? '未取得可展示的响应摘要' }}</p></section>
                  </div>
                  <p class="invocation-detail-note">仅展示已保存的脱敏摘要；完整 SOAP 报文、端点和凭证不保存。</p>
                </div>
              </td>
            </tr>
            </template>
          </tbody>
        </table>
      </div>
      <nav v-if="page.total > page.pageSize" class="invocation-pagination" aria-label="HIS调用记录分页">
        <button class="work-quiet-button" type="button" :disabled="page.page <= 1 || loading" @click="emit('changePage', page.page - 1)"><ChevronLeft :size="15" />上一页</button>
        <span>第 {{ page.page }} 页，共 {{ Math.max(1, Math.ceil(page.total / page.pageSize)) }} 页</span>
        <button class="work-quiet-button" type="button" :disabled="!canAdvance() || loading" @click="emit('changePage', page.page + 1)">下一页<ChevronRight :size="15" /></button>
      </nav>
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
.invocation-table th.invocation-actions, .invocation-table td.invocation-actions { width: 74px; min-width: 74px; text-align: right; white-space: nowrap; }
.invocation-detail-button { min-height: 28px; padding: 0 7px; border: 1px solid #b9d2cc; border-radius: 4px; background: #fff; color: #167467; display: inline-flex; align-items: center; gap: 3px; font-size: 11px; cursor: pointer; }
.invocation-detail-button:hover { background: #f1f8f5; }
.invocation-table tbody tr.is-expanded > td { background: #f3faf7; }
.invocation-detail-row > td { padding: 0 14px 13px; background: #f3faf7; }
.invocation-detail { padding: 12px; border: 1px solid #d7e8e3; border-radius: 5px; background: #fff; }
.invocation-detail-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0; border-top: 1px dashed #b9cdd0; border-left: 1px dashed #b9cdd0; }
.invocation-detail-grid > div { min-width: 0; display: grid; grid-template-columns: 92px minmax(0, 1fr); border-right: 1px dashed #b9cdd0; border-bottom: 1px dashed #b9cdd0; }
.invocation-detail-grid dt, .invocation-detail-grid dd { min-width: 0; margin: 0; padding: 6px 8px; line-height: 1.45; overflow-wrap: anywhere; }
.invocation-detail-grid dt { color: #6a7e84; font-size: 11px; font-weight: 700; background: #f6f8f9; }
.invocation-detail-grid dd { color: #304950; font-size: 12px; }
.invocation-payload-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 10px; }
.invocation-payload-grid section { min-width: 0; padding: 8px 10px; border: 1px solid #e3ece9; border-radius: 4px; background: #fbfdfc; }
.invocation-payload-grid h4 { margin: 0 0 5px; color: #526b72; font-size: 11px; }
.invocation-payload-grid p { margin: 0; color: #304950; font-size: 12px; line-height: 1.55; overflow-wrap: anywhere; }
.invocation-detail-note { margin: 9px 0 0; color: #7a8b90; font-size: 11px; }
.invocation-pagination { padding: 10px 16px; border-top: 1px solid #e9eef0; display: flex; align-items: center; justify-content: flex-end; gap: 10px; color: #647980; font-size: 12px; }
@media (max-width: 900px) { .invocation-detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .card-heading { align-items: flex-start; flex-direction: column; } .card-heading-actions { width: 100%; justify-content: space-between; } .invocation-pagination { justify-content: space-between; } .invocation-detail-grid, .invocation-payload-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
