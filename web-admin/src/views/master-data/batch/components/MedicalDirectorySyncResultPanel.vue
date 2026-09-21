<!-- 100-004/100-005同步结果组件：按目录类型展示同范围数量核对、分页取得和当前数据更新事实。 -->
<script setup lang="ts">
import { AlertCircle, CheckCircle2, CircleHelp, LoaderCircle } from 'lucide-vue-next'
import type { MedicalDirectorySyncResult } from '@/api/master-data/batch'

const props = defineProps<{
  /** 当前批次已落库的单目录类型处理结果。 */
  results: MedicalDirectorySyncResult[]
  /** 分项事实是否仍在读取。 */
  loading: boolean
}>()

/** 100-004医疗目录类型的业务展示名称。 */
const typeLabels = { TRADITIONAL_MEDICINE: '中药', WESTERN_MEDICINE: '西药', TREATMENT: '诊疗', CONSUMABLE: '耗材' } as const

/** @param result 单目录类型结果 @return 面向业务人员的处理结论 */
function statusLabel(result: MedicalDirectorySyncResult) {
  return result.status === 'COMPLETED' ? '已更新当前数据' : result.status === 'RESULT_UNKNOWN' ? '结果无法确认' : '本类型未更新'
}
</script>

<template>
  <section class="medical-result-panel">
    <header><div><h3>分项处理结果</h3><p>每类目录先由 100-005 核对同一时间范围的数量，再由 100-004 分页取得；未完成类型不会更新当前数据。</p></div></header>
    <div v-if="props.loading" class="result-state"><LoaderCircle class="spinning" :size="18" />正在读取分项结果</div>
    <div v-else-if="!props.results.length" class="result-state neutral"><CircleHelp :size="18" /><span><strong>本批次没有可按类型核对的结果</strong>本次尚未结束，或历史记录早于医疗目录分项结果启用时间。</span></div>
    <div v-else class="result-grid">
      <article v-for="result in props.results" :key="result.directoryType" class="result-card" :class="result.status.toLowerCase()">
        <header><strong>{{ typeLabels[result.directoryType] }}</strong><span :class="result.status.toLowerCase()"><CheckCircle2 v-if="result.status === 'COMPLETED'" :size="14" /><AlertCircle v-else :size="14" />{{ statusLabel(result) }}</span></header>
        <dl><div><dt>声明 / 返回</dt><dd>{{ result.declaredCount ?? '—' }} / {{ result.returnedCount }}</dd></div><div><dt>新增 / 更新</dt><dd>{{ result.createdCount }} / {{ result.updatedCount }}</dd></div><div><dt>当前有效</dt><dd>{{ result.activeCount === null ? '未更新' : `${result.activeCount} 条` }}</dd></div><div><dt>校验问题</dt><dd>{{ result.invalidCount + result.conflictCount }} 条</dd></div></dl>
        <p v-if="result.failureSummary" class="result-failure">{{ result.failureSummary }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.medical-result-panel{border:1px solid #dce5e7;border-radius:6px;background:#fff}.medical-result-panel>header{padding:15px 17px;border-bottom:1px solid #e9eef0}.medical-result-panel h3{margin:0;color:#2d424b;font-size:15px}.medical-result-panel p{margin:5px 0 0;color:#718189;font-size:12px;line-height:1.55}.result-state{min-height:68px;padding:15px 17px;display:flex;align-items:center;gap:8px;color:#587077;font-size:13px}.result-state.neutral{align-items:flex-start;background:#f8fbfb}.result-state.neutral span{display:grid;gap:3px;line-height:1.55}.result-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;padding:12px}.result-card{min-width:0;padding:13px;border:1px solid #dbe7e6;border-radius:5px;background:#fbfefd}.result-card.failed,.result-card.result_unknown{border-color:#ecd2c9;background:#fffaf8}.result-card>header{display:flex;align-items:center;justify-content:space-between;gap:8px}.result-card>header strong{color:#29424b;font-size:14px}.result-card>header span{display:inline-flex;align-items:center;gap:4px;color:#237461;font-size:12px;white-space:nowrap}.result-card>header span.failed,.result-card>header span.result_unknown{color:#ae5039}.result-card dl{margin:12px 0 0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.result-card dl div{min-width:0}.result-card dt{color:#7a8a91;font-size:11px}.result-card dd{margin:3px 0 0;color:#2e444d;font-size:13px;font-weight:650;white-space:nowrap}.result-failure{padding-top:9px;border-top:1px solid #f0d7d1;color:#7f5146!important}.spinning{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:720px){.result-grid{grid-template-columns:1fr}}
</style>
