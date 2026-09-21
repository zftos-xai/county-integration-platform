<!-- 100-003同步结果组件：按目录类型展示已落库的取得、校验和当前数据更新事实。 -->
<script setup lang="ts">
import { AlertCircle, CheckCircle2, CircleHelp, LoaderCircle } from 'lucide-vue-next'
import type { HospitalDirectorySyncResult } from '@/api/master-data/batch'

const props = defineProps<{
  /** 当前批次已落库的单目录类型处理结果。 */
  results: HospitalDirectorySyncResult[]
  /** 分项事实是否仍在读取。 */
  loading: boolean
}>()

/** 100-003目录类型的业务展示名称。 */
const typeLabels = {
  DEPARTMENT: '科室',
  DOCTOR: '医生',
  WARD: '病区',
  BED: '床位',
} as const

/** @param result 单目录类型结果 @return 面向业务人员的处理结论 */
function statusLabel(result: HospitalDirectorySyncResult) {
  return result.status === 'COMPLETED'
    ? '已更新当前数据'
    : result.status === 'RESULT_UNKNOWN'
      ? '结果无法确认'
      : '本类型未更新'
}
</script>

<template>
  <section class="directory-result-panel">
    <header>
      <div>
        <h3>分项处理结果</h3>
        <p>每类目录独立处理；只有“已更新当前数据”的类型才会使HIS未返回记录标记无效。</p>
      </div>
    </header>
    <div v-if="props.loading" class="result-state"><LoaderCircle class="spinning" :size="18" />正在读取分项结果</div>
    <div v-else-if="!props.results.length" class="result-state neutral"><CircleHelp :size="18" /><span><strong>本批次没有可按类型核对的结果</strong>该记录早于分项结果启用时间，或本次尚未结束；只能查看批次总计，不能据此完成分项验收。</span></div>
    <div v-else class="directory-result-grid">
      <article v-for="result in props.results" :key="result.directoryType" class="directory-result-card" :class="result.status.toLowerCase()">
        <header><strong>{{ typeLabels[result.directoryType] }}</strong><span :class="result.status.toLowerCase()"><CheckCircle2 v-if="result.status === 'COMPLETED'" :size="14" /><AlertCircle v-else :size="14" />{{ statusLabel(result) }}</span></header>
        <dl>
          <div><dt>HIS返回</dt><dd>{{ result.returnedCount }} 条</dd></div>
          <div><dt>新增 / 更新</dt><dd>{{ result.createdCount }} / {{ result.updatedCount }} 条</dd></div>
          <div><dt>标记无效</dt><dd>{{ result.sourceMissingCount }} 条</dd></div>
          <div><dt>当前有效</dt><dd>{{ result.activeCount === null ? '未更新' : `${result.activeCount} 条` }}</dd></div>
          <div v-if="result.invalidCount + result.conflictCount > 0"><dt>校验问题</dt><dd>{{ result.invalidCount + result.conflictCount }} 条</dd></div>
        </dl>
        <p v-if="result.failureSummary" class="result-failure">{{ result.failureSummary }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.directory-result-panel{border:1px solid #dce5e7;border-radius:6px;background:#fff}.directory-result-panel>header{padding:15px 17px;border-bottom:1px solid #e9eef0}.directory-result-panel h3{margin:0;color:#2d424b;font-size:15px}.directory-result-panel p{margin:5px 0 0;color:#718189;font-size:12px;line-height:1.55}.result-state{min-height:68px;padding:15px 17px;display:flex;align-items:center;gap:8px;color:#587077;font-size:13px}.result-state.neutral{align-items:flex-start;background:#f8fbfb}.result-state.neutral span{display:grid;gap:3px;line-height:1.55}.directory-result-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;padding:12px}.directory-result-card{min-width:0;padding:13px;border:1px solid #dbe7e6;border-radius:5px;background:#fbfefd}.directory-result-card.failed,.directory-result-card.result_unknown{border-color:#ecd2c9;background:#fffaf8}.directory-result-card>header{display:flex;align-items:center;justify-content:space-between;gap:8px}.directory-result-card>header strong{color:#29424b;font-size:14px}.directory-result-card>header span{display:inline-flex;align-items:center;gap:4px;color:#237461;font-size:12px;white-space:nowrap}.directory-result-card>header span.failed,.directory-result-card>header span.result_unknown{color:#ae5039}.directory-result-card dl{margin:12px 0 0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.directory-result-card dl div{min-width:0}.directory-result-card dt{color:#7a8a91;font-size:11px}.directory-result-card dd{margin:3px 0 0;color:#2e444d;font-size:13px;font-weight:650;white-space:nowrap}.result-failure{padding-top:9px;border-top:1px solid #f0d7d1;color:#7f5146!important}.spinning{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:720px){.directory-result-grid{grid-template-columns:1fr}}
</style>
