<!-- 机构目录批次的通用HIS调用事实区块：展示已落库脱敏交换记录，不触发重发。 -->
<script setup lang="ts">
import { ChevronDown, ChevronRight, FileWarning } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import type { BatchExchangeRecord } from '@/api/master-data/batch'
import { hasPermission } from '@/store/modules/auth'
import { formatBatchTime } from '../batchTime'

const props = defineProps<{
  records: BatchExchangeRecord[]
  loading: boolean
  error: string
  organizationCode: string
  sourceRecordId: string
}>()

const canOpenManagement = computed(() => hasPermission('exchange:read'))
const expandedRecordId = ref<number | null>(null)
const isSectionExpanded = ref(true)
const managementPath = computed(() => ({
  path: '/exchanges',
  query: { organizationCode: props.organizationCode, sourceRecordId: props.sourceRecordId },
}))

/** 在通用调用记录中只展开一条，保持多条记录的扫描效率。 */
function toggleRecord(id: number) {
  expandedRecordId.value = expandedRecordId.value === id ? null : id
}

watch(() => props.records, () => {
  expandedRecordId.value = null
})

/** 将稳定交换终态转换为无需依赖颜色的业务名称。 */
function resultLabel(value: BatchExchangeRecord['result']) {
  if (value === null) return '终态未记录'
  return ({ SUCCESS: '成功', FAILURE: 'HIS明确失败', NO_RESPONSE: '结果未知', INVALID_RESPONSE: '响应不可确认' })[value]
}
</script>

<template>
  <section class="record-card batch-exchange-card">
    <div class="card-heading">
      <div><h3>HIS 请求记录</h3><p>展示与本批次关联的脱敏调用事实；不保存或显示完整 SOAP 报文、端点和凭证。</p></div>
      <div class="record-actions"><span v-if="!loading" class="record-total">{{ records.length }} 次调用</span><RouterLink v-if="canOpenManagement" class="record-management-link" :to="managementPath">打开管理页</RouterLink><button class="section-toggle-button" type="button" :aria-expanded="isSectionExpanded" @click="isSectionExpanded = !isSectionExpanded"><ChevronDown v-if="isSectionExpanded" :size="15" /><ChevronRight v-else :size="15" />{{ isSectionExpanded ? '收起' : '展开' }}</button></div>
    </div>
    <template v-if="isSectionExpanded">
      <div v-if="loading" class="panel-state" role="status">正在读取已保存的 HIS 调用事实…</div>
      <div v-else-if="error" class="panel-state"><FileWarning :size="18" />{{ error }}</div>
      <div v-else-if="records.length === 0" class="panel-state"><FileWarning :size="18" />该历史批次没有调用追踪记录；不能据此推断 HIS 未被调用。</div>
      <div v-else class="table-scroll" role="region" aria-label="HIS调用记录" tabindex="0">
        <table class="work-table invocation-table">
          <thead><tr><th>请求时间</th><th>交易</th><th>结果</th><th>耗时</th><th>请求与响应摘要</th><th class="invocation-actions">操作</th></tr></thead>
        <tbody><template v-for="record in records" :key="record.id"><tr :class="{ 'is-expanded': expandedRecordId === record.id }">
          <td>{{ formatBatchTime(record.receivedAt) }}</td>
          <td><strong>{{ record.interfaceCode }}</strong><small>请求编号：{{ record.requestId }}</small></td>
          <td><strong>{{ resultLabel(record.result) }}</strong><small v-if="record.targetResultCode">结果码：{{ record.targetResultCode }}</small></td>
          <td>{{ record.durationMs === null ? '未确认' : `${record.durationMs} ms` }}</td>
          <td><span>{{ record.requestSummary ?? '未保存请求摘要' }}</span><small>{{ record.resultMessage ?? record.communicationErrorSummary ?? '未取得可展示的响应摘要' }}</small></td>
          <td class="invocation-actions"><button class="invocation-detail-button" type="button" :aria-expanded="expandedRecordId === record.id" @click="toggleRecord(record.id)"><ChevronDown v-if="expandedRecordId === record.id" :size="14" /><ChevronRight v-else :size="14" />{{ expandedRecordId === record.id ? '收起' : '详情' }}</button></td>
        </tr><tr v-if="expandedRecordId === record.id" class="invocation-detail-row"><td colspan="6"><div class="invocation-detail"><dl class="invocation-detail-grid"><div><dt>请求时间</dt><dd>{{ formatBatchTime(record.receivedAt) }}</dd></div><div><dt>完成时间</dt><dd>{{ record.processedAt ? formatBatchTime(record.processedAt) : '未确认' }}</dd></div><div><dt>交易 / 请求编号</dt><dd>{{ record.interfaceCode }} · {{ record.requestId }}</dd></div><div><dt>结果 / 结果码</dt><dd>{{ resultLabel(record.result) }} · {{ record.targetResultCode ?? '—' }}</dd></div><div><dt>耗时</dt><dd>{{ record.durationMs === null ? '未确认' : `${record.durationMs} ms` }}</dd></div></dl><div class="invocation-payload-grid"><section><h4>请求摘要</h4><p>{{ record.requestSummary ?? '未保存请求摘要' }}</p></section><section><h4>响应摘要</h4><p>{{ record.resultMessage ?? record.communicationErrorSummary ?? '未取得可展示的响应摘要' }}</p></section></div><p class="invocation-detail-note">仅展示已保存的脱敏摘要；完整 SOAP 报文、端点和凭证不保存。</p></div></td></tr></template></tbody>
      </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.card-heading { min-height: 48px; padding: 9px 16px; border-bottom: 1px solid #e9eef0; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.card-heading h3 { margin: 0; padding: 0; border: 0; }
.card-heading p { margin: 3px 0 0; color: #687d84; font-size: 12px; line-height: 1.5; }
.record-actions { flex: none; display: flex; align-items: center; gap: 10px; }.record-total { color: #287a6b; font-size: 12px; font-weight: 700; }.record-management-link { color: #147467; font-size: 12px; font-weight: 700; text-decoration: none; }.record-management-link:hover { text-decoration: underline; }
.section-toggle-button { min-height: 28px; padding: 0 7px; border: 1px solid #b9d2cc; border-radius: 4px; background: #fff; color: #167467; display: inline-flex; align-items: center; gap: 3px; font-size: 11px; cursor: pointer; }.section-toggle-button:hover { background: #f1f8f5; }
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
.invocation-detail-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0; margin: 0; border-top: 1px dashed #b9cdd0; border-left: 1px dashed #b9cdd0; }
.invocation-detail-grid > div { min-width: 0; display: grid; grid-template-columns: 92px minmax(0, 1fr); border-right: 1px dashed #b9cdd0; border-bottom: 1px dashed #b9cdd0; }
.invocation-detail-grid dt, .invocation-detail-grid dd { min-width: 0; margin: 0; padding: 6px 8px; line-height: 1.45; overflow-wrap: anywhere; }
.invocation-detail-grid dt { color: #6a7e84; font-size: 11px; font-weight: 700; background: #f6f8f9; }
.invocation-detail-grid dd { color: #304950; font-size: 12px; }
.invocation-payload-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 10px; }
.invocation-payload-grid section { min-width: 0; padding: 8px 10px; border: 1px solid #e3ece9; border-radius: 4px; background: #fbfdfc; }
.invocation-payload-grid h4 { margin: 0 0 5px; color: #526b72; font-size: 11px; }
.invocation-payload-grid p { margin: 0; color: #304950; font-size: 12px; line-height: 1.55; overflow-wrap: anywhere; }
.invocation-detail-note { margin: 9px 0 0; color: #7a8b90; font-size: 11px; }
@media (max-width: 900px) { .invocation-detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .record-actions { width: 100%; justify-content: flex-end; flex-wrap: wrap; } .invocation-detail-grid, .invocation-payload-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
