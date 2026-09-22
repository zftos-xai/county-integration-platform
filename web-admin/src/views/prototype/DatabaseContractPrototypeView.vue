<!-- 数据库契约维护线交互原型：仅演示正常实例中的分析、审批、执行和撤销流程，不调用真实DDL。 -->
<script setup lang="ts">
import {
  AlertTriangle, ArrowLeft, Check, CheckCircle2, ChevronRight, ClipboardCheck,
  Database, FileCode2, History, Play, RefreshCw, Search, ShieldCheck,
  Wrench, X,
} from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

type Direction = 'DATABASE' | 'MAPPER_OR_MODEL' | 'MANUAL_REVIEW'
type Risk = '低' | '中' | '高'
type WorkflowStep = 'scan' | 'plan' | 'approval' | 'execution' | 'verification'
type ContractIssue = {
  id: string
  code: string
  objectName: string
  summary: string
  expected: string
  actual: string
  direction: Direction
  risk: Risk
  action: string
  executable: boolean
}

const router = useRouter()
const query = ref('')
const directionFilter = ref<'ALL' | Direction>('ALL')
const selectedId = ref('DBCONTRACT-E103-1')
const selectedForPlan = ref<string[]>(['DBCONTRACT-E103-1', 'DBCONTRACT-E201-1'])
const workflowStep = ref<WorkflowStep>('scan')
const notice = ref('')
const scanTime = ref('2026-09-19 10:32:18')
const approvalNote = ref('已核对影响范围、备份点和回退条件。')

const issues = ref<ContractIssue[]>([
  {
    id: 'DBCONTRACT-E103-1', code: 'DBCONTRACT-E103',
    objectName: 'dbo.md_hospital_directory.source_record_name', summary: '字段长度不一致',
    expected: 'nvarchar(100) NOT NULL', actual: 'nvarchar(50) NOT NULL',
    direction: 'DATABASE', risk: '中', action: '扩大数据库字段长度到 nvarchar(100)，执行前确认现有索引长度。',
    executable: true,
  },
  {
    id: 'DBCONTRACT-E201-1', code: 'DBCONTRACT-E201',
    objectName: 'DepartmentMapper.findPage → DepartmentVO.name', summary: '结果映射缺少显式别名',
    expected: 'department_name AS name', actual: 'department_name',
    direction: 'MAPPER_OR_MODEL', risk: '低', action: '修改 Mapper.xml 的 SELECT 别名；该项只生成代码修改建议，不执行 DDL。',
    executable: false,
  },
  {
    id: 'DBCONTRACT-E106-1', code: 'DBCONTRACT-E106',
    objectName: 'dbo.md_organization.external_code', summary: '空值约束不一致',
    expected: 'nvarchar(64) NOT NULL', actual: 'nvarchar(64) NULL',
    direction: 'MANUAL_REVIEW', risk: '高', action: '先处理现有空值并确认业务规则，再决定收紧数据库或调整迁移契约。',
    executable: false,
  },
])

const filteredIssues = computed(() => issues.value.filter(item => {
  const matchesDirection = directionFilter.value === 'ALL' || item.direction === directionFilter.value
  const keyword = query.value.trim().toLowerCase()
  const matchesQuery = !keyword || `${item.code} ${item.objectName} ${item.summary}`.toLowerCase().includes(keyword)
  return matchesDirection && matchesQuery
}))
const selectedIssue = computed(() => issues.value.find(item => item.id === selectedId.value) ?? issues.value[0])
const executableCount = computed(() => issues.value.filter(item => item.executable).length)
const stepOrder: WorkflowStep[] = ['scan', 'plan', 'approval', 'execution', 'verification']
const stepLabels: Record<WorkflowStep, string> = {
  scan: '分析', plan: '生成方案', approval: '审批', execution: '执行', verification: '复验',
}

/** 模拟重新读取同一套契约核心，始终不访问正式API。 */
function rescan() {
  workflowStep.value = 'scan'
  scanTime.value = '刚刚'
  notice.value = '原型扫描已完成：发现 3 项差异，未执行任何变更。'
}

/** 进入方案预览并保留不可自动执行项作为人工任务。 */
function generatePlan() {
  workflowStep.value = 'plan'
  notice.value = `已生成方案：${selectedForPlan.value.length} 项纳入本次处理，只有 ${executableCount.value} 项可生成 DDL。`
}

/** 模拟审批结论；真实实现必须由独立权限和服务端审计约束。 */
function approve() {
  workflowStep.value = 'approval'
  notice.value = '方案已批准，执行窗口和回退条件已记录。'
}

/** 原型只改变页面状态，绝不发送真实DDL请求。 */
function executePlan() {
  workflowStep.value = 'verification'
  notice.value = '模拟执行和复验完成：数据库项已通过，代码项仍等待开发提交。'
}

/** 模拟在DDL执行前取消方案；执行后的成功变更只能通过新的受控迁移继续演进。 */
function cancelPlan() {
  workflowStep.value = 'scan'
  notice.value = '方案已在执行前取消；未执行任何 DDL，记录仍保留。'
}

function directionLabel(direction: Direction) {
  return direction === 'DATABASE' ? '改数据库' : direction === 'MAPPER_OR_MODEL' ? '改 Mapper / 模型' : '人工判断'
}

function directionTone(direction: Direction) {
  return direction === 'DATABASE' ? 'warning' : direction === 'MAPPER_OR_MODEL' ? 'neutral' : 'danger'
}
</script>

<template>
  <div class="prototype-app contract-prototype">
    <aside class="prototype-sidebar">
      <div class="prototype-brand">
        <span class="prototype-logo"><Database :size="20" /></span>
        <span><strong>数据库契约中心</strong><small>维护机制交互原型</small></span>
      </div>
      <nav class="prototype-nav" aria-label="数据库契约导航">
        <div class="prototype-nav-group">
          <span class="prototype-nav-label">数据库维护</span>
          <button class="active" type="button"><Wrench :size="17" /><span>正常维护</span></button>
        </div>
        <div class="prototype-nav-group">
          <span class="prototype-nav-label">维护记录</span>
          <button type="button" @click="notice = '最近一次模拟执行：2026-09-18，复验通过。'">
            <History :size="17" /><span>变更记录</span>
          </button>
        </div>
      </nav>
      <div class="prototype-sidebar-foot">
        <span><ShieldCheck :size="16" />原型与真实DDL隔离</span>
      </div>
    </aside>

    <main class="prototype-main">
      <header class="prototype-topbar">
        <button class="prototype-icon" type="button" aria-label="返回业务原型" title="返回业务原型" @click="router.push('/prototype')"><ArrowLeft :size="18" /></button>
        <div class="prototype-heading">
          <span class="prototype-section-name">系统管理 / 数据库契约</span>
          <div class="prototype-title-line"><h1>数据库契约维护</h1><p>在健康实例中分析差异并受控处理</p></div>
        </div>
        <span class="prototype-demo">交互原型 · 不会执行真实 DDL</span>
      </header>

      <div class="prototype-content contract-content">
        <div v-if="notice" class="work-toast" role="status"><Check :size="16" />{{ notice }}<button class="prototype-icon" type="button" aria-label="关闭提示" @click="notice = ''"><X :size="15" /></button></div>

        <section class="contract-boundary" aria-label="运行边界">
          <span class="contract-health"><CheckCircle2 :size="20" /><strong>当前实例运行正常</strong></span>
          <p>可以扫描数据库契约差异，并按审批流程生成和执行维护方案。</p>
          <button class="work-quiet-button" type="button" @click="rescan"><RefreshCw :size="15" />重新扫描</button>
        </section>

        <section class="contract-summary" aria-label="检查摘要">
          <div><span>契约字段</span><strong>186</strong><small>来自 6 个 Flyway 主版本</small></div>
          <div><span>发现差异</span><strong class="warning-text">3</strong><small>数据库、映射与人工判断</small></div>
          <div><span>可生成 DDL</span><strong>{{ executableCount }}</strong><small>其余只生成修改建议</small></div>
          <div><span>最近扫描</span><strong class="time-value">{{ scanTime }}</strong><small>只读扫描 · SQL Server 2012</small></div>
        </section>

        <section class="contract-workflow" aria-label="维护流程">
          <template v-for="(step, index) in stepOrder" :key="step">
            <div :class="{ current: workflowStep === step, complete: stepOrder.indexOf(workflowStep) > index }">
              <span><Check v-if="stepOrder.indexOf(workflowStep) > index" :size="13" />{{ index + 1 }}</span>
              <small>{{ stepLabels[step] }}</small>
            </div>
            <ChevronRight v-if="index < stepOrder.length - 1" :size="15" />
          </template>
        </section>

        <div class="contract-grid">
          <section class="prototype-section contract-list action-column-table">
            <div class="prototype-section-head">
              <div><h3>差异清单</h3><small>先选择对象，再查看依据和处理边界</small></div>
              <span>{{ filteredIssues.length }} 项</span>
            </div>
            <div class="work-toolbar">
              <label class="prototype-search"><Search :size="15" /><input v-model="query" type="search" aria-label="搜索差异" placeholder="错误码、表、字段或 Mapper" /></label>
              <select v-model="directionFilter" aria-label="修改方向">
                <option value="ALL">全部方向</option><option value="DATABASE">改数据库</option>
                <option value="MAPPER_OR_MODEL">改 Mapper / 模型</option><option value="MANUAL_REVIEW">人工判断</option>
              </select>
            </div>
            <div class="prototype-table-wrap">
              <table class="work-table">
                <thead><tr><th class="select-column">选择</th><th>对象 / 问题</th><th>修改方向</th><th>风险</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="item in filteredIssues" :key="item.id" :class="{ selected: selectedId === item.id }">
                    <td><input v-model="selectedForPlan" type="checkbox" :value="item.id" :aria-label="`选择 ${item.objectName}`" /></td>
                    <td><button class="work-row-link" type="button" @click="selectedId = item.id">{{ item.objectName }}</button><small>{{ item.code }} · {{ item.summary }}</small></td>
                    <td><span class="prototype-tag" :class="directionTone(item.direction)">{{ directionLabel(item.direction) }}</span></td>
                    <td><span :class="['risk-text', `risk-${item.risk}`]">{{ item.risk }}</span></td>
                    <td><button class="prototype-text-button" type="button" @click="selectedId = item.id">查看</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="contract-list-actions">
              <span>已选 {{ selectedForPlan.length }} 项</span>
              <button class="prototype-button" type="button" :disabled="selectedForPlan.length === 0" @click="generatePlan"><FileCode2 :size="15" />生成处理方案</button>
            </div>
          </section>

          <aside class="prototype-section contract-detail" aria-label="差异详情">
            <div class="prototype-section-head"><div><h3>差异详情</h3><small>{{ selectedIssue.code }}</small></div><span class="prototype-tag" :class="directionTone(selectedIssue.direction)">{{ directionLabel(selectedIssue.direction) }}</span></div>
            <div class="contract-detail-body">
              <h2>{{ selectedIssue.objectName }}</h2>
              <p class="detail-summary">{{ selectedIssue.summary }}</p>
              <div class="contract-compare">
                <div><span>期望契约</span><code>{{ selectedIssue.expected }}</code></div>
                <div><span>实际检测</span><code>{{ selectedIssue.actual }}</code></div>
              </div>
              <div class="contract-advice"><AlertTriangle :size="17" /><div><strong>应该改哪里</strong><p>{{ selectedIssue.action }}</p></div></div>

              <template v-if="workflowStep !== 'scan'">
                <h3 class="detail-section-title">本次方案</h3>
                <ul class="contract-plan-list">
                  <li><CheckCircle2 :size="15" />生成带前置条件和事务保护的 SQL Server 2012 脚本</li>
                  <li><ClipboardCheck :size="15" />执行前记录审批人、影响范围和执行窗口，可在执行前取消</li>
                  <li><RefreshCw :size="15" />DDL 与复验处于同一事务，失败自动回滚；成功后如需调整必须新建迁移</li>
                </ul>
                <label v-if="workflowStep === 'plan'" class="approval-note">审批说明<textarea v-model="approvalNote" rows="3" maxlength="300" /></label>
              </template>
            </div>
            <footer class="contract-detail-actions">
              <span v-if="workflowStep === 'scan'">选择差异并生成方案后，才会出现审批操作。</span>
              <template v-if="workflowStep === 'plan'">
                <button class="work-quiet-button" type="button" @click="cancelPlan">执行前取消</button>
                <button class="prototype-button" type="button" :disabled="!approvalNote.trim()" @click="approve"><ShieldCheck :size="15" />批准方案</button>
              </template>
              <template v-else-if="workflowStep === 'approval'">
                <button class="work-quiet-button" type="button" @click="cancelPlan">执行前取消</button>
                <button class="prototype-button" type="button" @click="executePlan"><Play :size="15" />模拟执行并复验</button>
              </template>
              <span v-else-if="workflowStep === 'verification'">复验已结束；后续调整需重新扫描并新建方案。</span>
            </footer>
          </aside>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.contract-content { max-width: 1500px; }
.contract-boundary { min-height: 62px; margin-bottom: 14px; padding: 12px 15px; border: 1px solid #bfddce; border-radius: 5px; background: #f2faf6; display: flex; align-items: center; gap: 14px; }
.contract-boundary p { flex: 1; margin: 0; color: #61756e; font-size: 11px; line-height: 1.6; }
.contract-health { display: inline-flex; align-items: center; gap: 8px; color: #226c49; font-size: 12px; white-space: nowrap; }
.contract-summary { margin-bottom: 14px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid var(--line); border-radius: 5px; background: #fff; }
.contract-summary > div { min-width: 0; padding: 15px 17px; border-right: 1px solid var(--line-soft); }
.contract-summary > div:last-child { border-right: 0; }
.contract-summary span, .contract-summary small { display: block; color: #718087; font-size: 10px; }
.contract-summary strong { display: block; margin: 6px 0; font-size: 24px; line-height: 1; }
.contract-summary .time-value { font-size: 15px; line-height: 1.6; }
.warning-text { color: #b16b16; }
.contract-workflow { min-height: 60px; margin-bottom: 14px; padding: 10px 18px; border: 1px solid var(--line); border-radius: 5px; background: white; display: flex; align-items: center; justify-content: center; gap: 14px; }
.contract-workflow > div { min-width: 74px; display: flex; align-items: center; gap: 7px; color: #7b898f; }
.contract-workflow > div > span { width: 24px; height: 24px; border: 1px solid #ced9dc; border-radius: 50%; display: grid; place-items: center; font-size: 10px; }
.contract-workflow > div.current { color: #12695d; font-weight: 700; }
.contract-workflow > div.current > span { border-color: #147467; background: #147467; color: white; }
.contract-workflow > div.complete { color: #39866b; }
.contract-workflow > div.complete > span { border-color: #bfddce; background: #ecf7f0; }
.contract-workflow > svg { color: #acb7ba; }
.contract-grid { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(360px, .85fr); gap: 14px; align-items: start; }
.contract-list .prototype-section-head > div, .contract-detail .prototype-section-head > div { min-width: 0; }
.contract-list .prototype-section-head small, .contract-detail .prototype-section-head small { display: block; margin-top: 4px; color: #7c8a90; font-size: 10px; }
.contract-list .prototype-section-head > span { color: #718087; font-size: 11px; }
.contract-list .work-toolbar { border-inline: 0; border-top: 0; border-radius: 0; box-shadow: none; }
.contract-list .work-table { min-width: 720px; }
.contract-list .select-column { width: 58px; }
.contract-list tbody tr.selected { background: #eef7f4; }
.contract-list input[type="checkbox"] { width: 15px; height: 15px; accent-color: #147467; }
.risk-text { font-weight: 700; font-size: 11px; }
.risk-低 { color: #37745c; }.risk-中 { color: #9a661e; }.risk-高 { color: #a3423a; }
.contract-list-actions { min-height: 58px; padding: 10px 14px; border-top: 1px solid var(--line-soft); display: flex; justify-content: flex-end; align-items: center; gap: 14px; }
.contract-list-actions > span { color: #718087; font-size: 11px; }
.contract-detail { position: sticky; top: 14px; }
.contract-detail-body { padding: 18px; }
.contract-detail-body h2 { margin: 0; font-size: 15px; overflow-wrap: anywhere; }
.detail-summary { margin: 6px 0 16px; color: #687a80; font-size: 11px; }
.contract-compare { display: grid; gap: 8px; }
.contract-compare > div { padding: 11px; border: 1px solid var(--line-soft); border-radius: 4px; background: #fafbfb; }
.contract-compare span { display: block; margin-bottom: 6px; color: #74858c; font-size: 10px; }
.contract-compare code { color: #2e4650; font-size: 11px; overflow-wrap: anywhere; }
.contract-advice { margin-top: 12px; padding: 12px; border-left: 3px solid #c28b3e; background: #fff8eb; display: flex; gap: 9px; color: #775822; }
.contract-advice svg { flex: none; margin-top: 1px; }
.contract-advice strong { font-size: 11px; }.contract-advice p { margin: 5px 0 0; font-size: 11px; line-height: 1.65; }
.detail-section-title { margin: 20px 0 10px; font-size: 12px; }
.contract-plan-list { margin: 0; padding: 0; list-style: none; display: grid; gap: 9px; }
.contract-plan-list li { display: flex; align-items: flex-start; gap: 8px; color: #52676e; font-size: 11px; line-height: 1.55; }
.contract-plan-list svg { flex: none; margin-top: 1px; color: #39866b; }
.approval-note { margin-top: 16px; display: grid; gap: 7px; color: #3b5158; font-size: 11px; font-weight: 700; }
.approval-note textarea { width: 100%; padding: 9px; border: 1px solid #cbd7da; border-radius: 4px; resize: vertical; color: #2d4149; font-size: 11px; }
.contract-detail-actions { min-height: 62px; padding: 11px 18px; border-top: 1px solid var(--line-soft); display: flex; align-items: center; justify-content: flex-end; }
.contract-detail-actions > span { color: #7a898f; font-size: 10px; line-height: 1.5; text-align: right; }
@media (max-width: 1100px) { .contract-grid { grid-template-columns: 1fr; }.contract-detail { position: static; }.contract-summary { grid-template-columns: repeat(2, 1fr); }.contract-summary > div:nth-child(2) { border-right: 0; }.contract-summary > div:nth-child(-n + 2) { border-bottom: 1px solid var(--line-soft); } }
@media (max-width: 700px) { .contract-boundary { align-items: flex-start; flex-wrap: wrap; }.contract-boundary p { flex-basis: 100%; }.contract-summary { grid-template-columns: 1fr; }.contract-summary > div { border-right: 0; border-bottom: 1px solid var(--line-soft); }.contract-workflow { justify-content: flex-start; overflow-x: auto; }.contract-workflow > div { min-width: 60px; }.contract-workflow small { white-space: nowrap; } }
</style>
