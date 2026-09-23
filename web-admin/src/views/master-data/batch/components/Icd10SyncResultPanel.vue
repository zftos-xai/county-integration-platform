<!-- ICD-10同步结果组件：按西医和中医类别展示公共目录的实际处理结果，不展示机构归属。 -->
<script setup lang="ts">
import { AlertCircle, CheckCircle2, CircleHelp, LoaderCircle } from 'lucide-vue-next'
import type { Icd10SyncResult } from '@/api/master-data/batch'

const props = defineProps<{
  /** 当前公共ICD-10批次已落库的诊断类别处理结果。 */
  results: Icd10SyncResult[]
  /** 分项事实是否仍在读取。 */
  loading: boolean
}>()

/** ICD-10诊断类别的业务展示名称。 */
const categoryLabels = { WESTERN: '西医诊断', TRADITIONAL: '中医诊断' } as const

/** @param result 单一诊断类别的同步结果 @return 面向业务人员的处理结论 */
function statusLabel(result: Icd10SyncResult) {
  return result.status === 'COMPLETED'
    ? '已更新公共目录'
    : result.status === 'RESULT_UNKNOWN'
      ? '结果无法确认'
      : '本类别未更新'
}
</script>

<template>
  <section class="icd10-result-panel">
    <header><h3>ICD-10 西医与中医诊断同步结果</h3></header>
    <div v-if="props.loading" class="result-state"><LoaderCircle class="spinning" :size="18" />正在读取分项结果</div>
    <div v-else-if="!props.results.length" class="result-state neutral"><CircleHelp :size="18" /><span><strong>本批次没有可按类别核对的结果</strong>本次尚未结束，或历史记录早于 ICD-10 分项结果启用时间。</span></div>
    <div v-else class="result-grid">
      <article v-for="result in props.results" :key="result.diagnosisCategory" class="result-card" :class="result.status.toLowerCase()">
        <header><strong>{{ categoryLabels[result.diagnosisCategory] }}</strong><span :class="result.status.toLowerCase()"><CheckCircle2 v-if="result.status === 'COMPLETED'" :size="14" /><AlertCircle v-else :size="14" />{{ statusLabel(result) }}</span></header>
        <div class="batch-detail-grid-scroll result-card-scroll" role="region" :aria-label="`${categoryLabels[result.diagnosisCategory]}同步统计`" tabindex="0">
          <dl class="batch-detail-grid result-card-fields">
            <div><dt>HIS 统计</dt><dd>{{ result.declaredCount === null ? '未提供' : result.declaredCount }}<small v-if="result.declaredCount !== null"> 条</small></dd></div>
            <div><dt>实际取得</dt><dd>{{ result.returnedCount }}<small> 条</small></dd></div>
            <div><dt>当前有效</dt><dd>{{ result.activeCount === null ? '未更新' : result.activeCount }}<small v-if="result.activeCount !== null"> 条</small></dd></div>
            <div><dt>本次变化</dt><dd>新增 {{ result.createdCount }} · 更新 {{ result.updatedCount }} · 未变化 {{ result.unchangedCount }}</dd></div>
            <div class="batch-detail-grid-wide"><dt>校验问题</dt><dd v-if="result.invalidCount + result.duplicateCount + result.conflictCount === 0">无</dd><dd v-else>无效 {{ result.invalidCount }} · 重复 {{ result.duplicateCount }} · 冲突 {{ result.conflictCount }}</dd></div>
          </dl>
        </div>
        <p v-if="result.failureSummary" class="result-failure">{{ result.failureSummary }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped src="../batch-detail-grid.css"></style>
<style scoped>
.icd10-result-panel{border:1px solid #dce5e7;border-radius:6px;background:#fff}
.icd10-result-panel>header{padding:13px 17px;border-bottom:1px solid #e9eef0}
.icd10-result-panel h3{margin:0;color:#2d424b;font-size:15px}
.icd10-result-panel p{margin:5px 0 0;color:#718189;font-size:12px;line-height:1.55}
.result-state{min-height:68px;padding:15px 17px;display:flex;align-items:center;gap:8px;color:#587077;font-size:13px}
.result-state.neutral{align-items:flex-start;background:#f8fbfb}
.result-state.neutral span{display:grid;gap:3px;line-height:1.55}
.result-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;padding:9px}
.result-card{min-width:0;padding:10px;border:1px solid #dbe7e6;border-radius:5px;background:#fbfefd}
.result-card.failed,.result-card.result_unknown{border-color:#ecd2c9;background:#fffaf8}
.result-card>header{display:flex;align-items:center;justify-content:space-between;gap:8px}
.result-card>header strong{color:#29424b;font-size:15px}
.result-card>header span{display:inline-flex;align-items:center;gap:4px;color:#237461;font-size:12px;white-space:nowrap}
.result-card>header span.failed,.result-card>header span.result_unknown{color:#ae5039}
.result-card-scroll{margin-top:9px}
.result-card-fields{width:100%;min-width:0;grid-template-columns:minmax(0,1fr)}
.result-card-fields>div{grid-template-columns:78px minmax(0,1fr)}
.result-card-fields>.batch-detail-grid-wide{grid-column:auto}
.result-card-fields dd{overflow-wrap:anywhere;white-space:normal}
.result-card-fields dd small{font-size:11px;font-weight:400}
.result-failure{padding-top:9px;border-top:1px solid #f0d7d1;color:#7f5146!important;overflow-wrap:anywhere}
.spinning{animation:spin 1s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:900px){.result-grid{grid-template-columns:1fr}}
</style>
