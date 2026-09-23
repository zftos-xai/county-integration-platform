<!-- 同步批次详情的只读管理审计区块：展示操作人和结果，不承载HIS报文。 -->
<script setup lang="ts">
import { FileWarning } from 'lucide-vue-next'
import type { ManagementAuditEvent } from '@/api/system/audit'
import { formatBatchTime } from '../batchTime'

defineProps<{
  events: ManagementAuditEvent[]
  loading: boolean
  error: string
  permitted: boolean
}>()

/** 将稳定审计动作代码转换为适合批次详情的业务名称。 */
function actionLabel(value: string) {
  return ({
    MASTER_DATA_BATCH_CREATED: '创建批次',
    MASTER_DATA_BATCH_RUN_STARTED: '开始同步',
    MASTER_DATA_ICD10_SYNCED: 'ICD-10同步结束',
    MASTER_DATA_BATCH_RECOVERED: '中断批次收尾',
    MASTER_DATA_BATCH_CANCELLED: '取消批次',
  } as Record<string, string>)[value] ?? value
}
</script>

<template>
  <section class="record-card audit-card">
    <div class="card-heading"><div><h3>管理审计记录</h3><p>记录谁在何时创建、开始、结束或收尾批次；不包含 HIS 报文。</p></div></div>
    <div v-if="!permitted" class="panel-state"><FileWarning :size="18" />当前账号没有审计查询权限，无法读取该批次的管理审计记录。</div>
    <div v-else-if="loading" class="panel-state" role="status">正在读取管理审计记录…</div>
    <div v-else-if="error" class="panel-state"><FileWarning :size="18" />{{ error }}</div>
    <div v-else-if="events.length === 0" class="panel-state">当前筛选下没有审计记录。</div>
    <div v-else class="table-scroll" role="region" aria-label="管理审计记录" tabindex="0">
      <table class="work-table"><thead><tr><th>发生时间</th><th>操作人</th><th>动作</th><th>结果</th><th>说明</th><th>请求编号</th></tr></thead>
        <tbody><tr v-for="event in events" :key="event.id"><td>{{ formatBatchTime(event.occurredAt) }}</td><td>{{ event.actorLogin }}</td><td>{{ actionLabel(event.actionCode) }}</td><td>{{ event.resultCode === 'SUCCESS' ? '成功' : '失败' }}</td><td>{{ event.changeSummary }}</td><td>{{ event.requestId ?? '—' }}</td></tr></tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.card-heading { padding: 9px 16px; border-bottom: 1px solid #e9eef0; }
.card-heading h3 { margin: 0; padding: 0; border: 0; }
.card-heading p { margin: 3px 0 0; color: #687d84; font-size: 12px; line-height: 1.5; }
.panel-state { min-height: 72px; padding: 14px 16px; color: #63777e; display: flex; align-items: center; gap: 8px; font-size: 13px; }
</style>
