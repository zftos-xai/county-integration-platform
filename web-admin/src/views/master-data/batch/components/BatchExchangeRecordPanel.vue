<!-- 机构目录批次的通用HIS调用事实区块：展示已落库脱敏交换记录，不触发重发。 -->
<script setup lang="ts">
import { FileWarning } from 'lucide-vue-next'
import { computed } from 'vue'
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
const managementPath = computed(() => ({
  path: '/exchanges',
  query: { organizationCode: props.organizationCode, sourceRecordId: props.sourceRecordId },
}))

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
      <div class="record-actions"><span v-if="!loading" class="record-total">{{ records.length }} 次调用</span><RouterLink v-if="canOpenManagement" class="record-management-link" :to="managementPath">打开管理页</RouterLink></div>
    </div>
    <div v-if="loading" class="panel-state" role="status">正在读取已保存的 HIS 调用事实…</div>
    <div v-else-if="error" class="panel-state"><FileWarning :size="18" />{{ error }}</div>
    <div v-else-if="records.length === 0" class="panel-state"><FileWarning :size="18" />该历史批次没有调用追踪记录；不能据此推断 HIS 未被调用。</div>
    <div v-else class="table-scroll" role="region" aria-label="HIS调用记录" tabindex="0">
      <table class="work-table invocation-table">
        <thead><tr><th>请求时间</th><th>交易</th><th>结果</th><th>耗时</th><th>请求与响应摘要</th></tr></thead>
        <tbody><tr v-for="record in records" :key="record.id">
          <td>{{ formatBatchTime(record.receivedAt) }}</td>
          <td><strong>{{ record.interfaceCode }}</strong><small>请求编号：{{ record.requestId }}</small></td>
          <td><strong>{{ resultLabel(record.result) }}</strong><small v-if="record.targetResultCode">结果码：{{ record.targetResultCode }}</small></td>
          <td>{{ record.durationMs === null ? '未确认' : `${record.durationMs} ms` }}</td>
          <td><span>{{ record.requestSummary ?? '未保存请求摘要' }}</span><small>{{ record.resultMessage ?? record.communicationErrorSummary ?? '未取得可展示的响应摘要' }}</small></td>
        </tr></tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.card-heading { min-height: 48px; padding: 9px 16px; border-bottom: 1px solid #e9eef0; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.card-heading h3 { margin: 0; padding: 0; border: 0; }
.card-heading p { margin: 3px 0 0; color: #687d84; font-size: 12px; line-height: 1.5; }
.record-actions { flex: none; display: flex; align-items: center; gap: 10px; }.record-total { color: #287a6b; font-size: 12px; font-weight: 700; }.record-management-link { color: #147467; font-size: 12px; font-weight: 700; text-decoration: none; }.record-management-link:hover { text-decoration: underline; }
.panel-state { min-height: 72px; padding: 14px 16px; color: #63777e; display: flex; align-items: center; gap: 8px; font-size: 13px; }
.invocation-table td { vertical-align: top; }
.invocation-table strong, .invocation-table small, .invocation-table span { display: block; }
.invocation-table small { margin-top: 3px; color: #72858b; font-size: 11px; line-height: 1.45; }
.invocation-table td:last-child { min-width: 280px; max-width: 460px; line-height: 1.5; overflow-wrap: anywhere; }
</style>
