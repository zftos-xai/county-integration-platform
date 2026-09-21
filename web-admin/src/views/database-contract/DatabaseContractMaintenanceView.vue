<!-- 数据库契约正式维护页：使用真实接口完成扫描、方案、双人审批、事务执行、复验和执行前取消。 -->
<script setup lang="ts">
import { AlertTriangle, CheckCircle2, Database, FileCode2, LoaderCircle, Play, RefreshCw, ShieldCheck, XCircle } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import {
  approveDatabaseContractPlan,
  cancelDatabaseContractPlan,
  createDatabaseContractPlan,
  executeDatabaseContractPlan,
  inspectDatabaseContract,
  listDatabaseContractPlans,
  type DatabaseContractInspection,
  type DatabaseContractIssue,
  type DatabaseContractPlan,
  type DatabaseContractPlanStatus,
} from '@/api/database-contract/maintenance'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'
import AuditAwareSuccess from '@/components/AuditAwareSuccess.vue'
import DatabaseContractConflictNotice from './components/DatabaseContractConflictNotice.vue'
import { databaseContractExecutionConfirmationMatches } from './form'

const inspection = ref<DatabaseContractInspection | null>(null)
const plans = ref<DatabaseContractPlan[]>([])
const selectedIssueKeys = ref<string[]>([])
const selectedPlanId = ref<number | null>(null)
const summary = ref('修复当前数据库与Flyway契约的可安全执行差异')
const approvalNote = ref('已核对影响范围、执行窗口、备份点和事务回滚条件。')
const executionConfirmation = ref('')
const loading = ref(false)
const action = ref('')
const error = ref('')
const notice = ref('')
const operationError = ref<ApiClientError | null>(null)
const auditTargetId = ref('')

const selectedPlan = computed(() => plans.value.find(item => item.id === selectedPlanId.value) ?? null)
const canPlan = computed(() => hasPermission('database-contract:plan'))
const canApprove = computed(() => hasPermission('database-contract:approve'))
const canExecute = computed(() => hasPermission('database-contract:execute'))
const canApproveSelected = computed(() => selectedPlan.value?.status === 'DRAFT'
  && selectedPlan.value.createdBy !== authState.user?.loginName
  && selectedPlan.value.executableCount === selectedPlan.value.issueCount)
const canExecuteSelected = computed(() => selectedPlan.value?.status === 'APPROVED'
  && databaseContractExecutionConfirmationMatches(executionConfirmation.value, selectedPlan.value.planNo))

/** 读取实时扫描和最近维护方案。 */
async function load() {
  loading.value = true
  error.value = ''
  operationError.value = null
  try {
    const [latestInspection, recentPlans] = await Promise.all([
      inspectDatabaseContract(), listDatabaseContractPlans(),
    ])
    inspection.value = latestInspection
    plans.value = recentPlans
    selectedIssueKeys.value = selectedIssueKeys.value.filter(key => latestInspection.issues.some(item => item.issueKey === key))
    if (selectedPlanId.value === null && recentPlans.length > 0) selectedPlanId.value = recentPlans[0]?.id ?? null
  } catch (value) {
    error.value = errorMessage(value)
  } finally {
    loading.value = false
  }
}

/** 重新扫描，不复用旧差异作为执行依据。 */
async function rescan() {
  await load()
  notice.value = '已重新读取数据库、Flyway、Mapper和模型契约。'
}

/** 用实时差异键创建持久化方案。 */
async function createPlan() {
  if (!selectedIssueKeys.value.length || !summary.value.trim()) return
  await perform('create', async () => createDatabaseContractPlan({
    issueKeys: selectedIssueKeys.value,
    summary: summary.value.trim(),
  }), '方案已生成，等待非创建人审批。')
  selectedIssueKeys.value = []
}

/** 使用最新并发版本审批方案。 */
async function approvePlan() {
  const plan = selectedPlan.value
  if (!plan || !approvalNote.value.trim()) return
  await perform('approve', () => approveDatabaseContractPlan(plan.id, plan.version, approvalNote.value.trim()), '方案已批准，可以在确认编号后执行。')
}

/** 输入完整方案编号后执行服务端生成DDL，并等待复验结果。 */
async function executePlan() {
  const plan = selectedPlan.value
  if (!plan || !databaseContractExecutionConfirmationMatches(executionConfirmation.value, plan.planNo)) return
  await perform('execute', () => executeDatabaseContractPlan(plan.id, plan.version), '执行请求已完成，请查看方案状态和逐项复验结果。')
  executionConfirmation.value = ''
  await load()
}

/** 在DDL开始前取消方案，保留完整历史和审计。 */
async function cancelPlan() {
  const plan = selectedPlan.value
  if (!plan) return
  await perform('cancel', () => cancelDatabaseContractPlan(plan.id, plan.version), '方案已取消，未执行DDL。')
}

async function perform(name: string, operation: () => Promise<DatabaseContractPlan>, success: string) {
  action.value = name
  error.value = ''
  operationError.value = null
  notice.value = ''
  try {
    const updated = await operation()
    plans.value = [updated, ...plans.value.filter(item => item.id !== updated.id)]
    selectedPlanId.value = updated.id
    auditTargetId.value = updated.planNo
    notice.value = success
  } catch (value) {
    if (value instanceof ApiClientError) operationError.value = value
    else error.value = errorMessage(value)
    await load()
  } finally {
    action.value = ''
  }
}

function errorMessage(value: unknown) {
  return value instanceof ApiClientError ? value.message : '操作失败，请重新读取后再试'
}

function statusLabel(status: DatabaseContractPlanStatus) {
  return ({ DRAFT: '待审批', APPROVED: '待执行', EXECUTING: '执行中', COMPLETED: '已完成', FAILED: '失败已回滚', CANCELLED: '已取消' })[status]
}

function statusTone(status: DatabaseContractPlanStatus) {
  return status === 'COMPLETED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'CANCELLED' ? 'neutral' : 'warning'
}

function directionLabel(issue: DatabaseContractIssue) {
  return issue.direction === 'DATABASE' ? '改数据库' : issue.direction === 'MAPPER_OR_MODEL' ? '改 Mapper / 模型' : '人工判断'
}

function formatTime(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value)) : '—'
}

onMounted(load)
</script>

<template>
  <section class="contract-page" aria-labelledby="contract-page-title">
    <header class="contract-intro">
      <div><span>正常维护</span><h2 id="contract-page-title">数据库契约维护</h2><p>扫描真实差异，生成受控方案；只有全部可安全执行且经非创建人审批的方案才能执行。</p></div>
      <button class="prototype-secondary compact" :disabled="loading || Boolean(action)" @click="rescan"><RefreshCw :class="{ spinning: loading }" :size="17" />重新扫描</button>
    </header>

    <p v-if="error" class="contract-notice error" role="alert"><XCircle :size="17" />{{ error }}</p>
    <DatabaseContractConflictNotice v-if="operationError" :error="operationError" @reload="load" />
    <AuditAwareSuccess v-if="notice" :message="notice" target-type="DATABASE_CONTRACT_PLAN" :target-id="auditTargetId" @close="notice = ''; auditTargetId = ''" />

    <section v-if="inspection" class="contract-metrics" aria-label="扫描摘要">
      <div><span>契约字段</span><strong>{{ inspection.expectedColumnCount }}</strong><small>Flyway迁移契约</small></div>
      <div><span>发现差异</span><strong>{{ inspection.issueCount }}</strong><small>数据库、Mapper和模型</small></div>
      <div><span>可在线执行</span><strong>{{ inspection.executableCount }}</strong><small>仅扩大长度或放宽NULL</small></div>
      <div><span>扫描时间</span><strong class="time">{{ formatTime(inspection.scannedAt) }}</strong><small>只读实时检查</small></div>
    </section>

    <div v-if="loading && !inspection" class="contract-state"><LoaderCircle class="spinning" :size="28" /><strong>正在扫描数据库契约</strong></div>
    <template v-else-if="inspection">
      <section class="contract-card">
        <div class="contract-card-head"><div><h3>差异与方案</h3><p>不可执行项仍会明确告诉开发或DBA应该修改的位置，但不能进入在线DDL审批。</p></div><span>{{ selectedIssueKeys.length }} 项已选</span></div>
        <div v-if="inspection.issues.length === 0" class="contract-state"><CheckCircle2 :size="30" /><strong>当前契约完全一致</strong><span>数据库字段、Mapper和模型没有发现差异。</span></div>
        <div v-else class="prototype-table-wrap">
          <table class="work-table contract-issue-table"><thead><tr><th>选择</th><th>对象 / 差异</th><th>修改方向</th><th>执行边界</th><th>DDL</th></tr></thead><tbody>
            <tr v-for="item in inspection.issues" :key="item.issueKey">
              <td><input v-model="selectedIssueKeys" type="checkbox" :value="item.issueKey" :disabled="!canPlan" :aria-label="`选择${item.objectName}`" /></td>
              <td><strong>{{ item.objectName }}</strong><small>{{ item.code }} · 期望 {{ item.expected }} · 实际 {{ item.actual }}</small></td>
              <td><span class="prototype-tag" :class="item.direction === 'DATABASE' ? 'warning' : item.direction === 'MANUAL_REVIEW' ? 'danger' : 'neutral'">{{ directionLabel(item) }}</span></td>
              <td>{{ item.executionNote }}<small>{{ item.action }}</small></td>
              <td><span class="prototype-tag" :class="item.executable ? 'success' : 'neutral'">{{ item.executable ? '可生成' : '不执行' }}</span></td>
            </tr>
          </tbody></table>
        </div>
        <footer v-if="inspection.issues.length && canPlan" class="contract-create">
          <label><span>方案摘要</span><input v-model="summary" maxlength="500" /></label>
          <button class="prototype-primary" :disabled="!selectedIssueKeys.length || !summary.trim() || Boolean(action)" @click="createPlan"><FileCode2 :size="16" />{{ action === 'create' ? '生成中…' : '生成方案' }}</button>
        </footer>
      </section>

      <div class="contract-plan-grid">
        <section class="contract-card plan-list">
          <div class="contract-card-head"><div><h3>维护方案</h3><p>最近100条，所有取消和失败记录都会保留。</p></div><span>{{ plans.length }} 条</span></div>
          <button v-for="plan in plans" :key="plan.id" :class="{ active: selectedPlanId === plan.id }" @click="selectedPlanId = plan.id; executionConfirmation = ''">
            <span><strong>{{ plan.planNo }}</strong><small>{{ plan.summary }}</small></span><span><em class="prototype-tag" :class="statusTone(plan.status)">{{ statusLabel(plan.status) }}</em><small>{{ formatTime(plan.updatedAt) }}</small></span>
          </button>
          <div v-if="plans.length === 0" class="contract-state compact-state">尚无维护方案</div>
        </section>

        <section v-if="selectedPlan" class="contract-card plan-detail">
          <div class="contract-card-head"><div><h3>{{ selectedPlan.planNo }}</h3><p>{{ selectedPlan.summary }}</p></div><span class="prototype-tag" :class="statusTone(selectedPlan.status)">{{ statusLabel(selectedPlan.status) }}</span></div>
          <dl class="plan-facts"><div><dt>创建</dt><dd>{{ selectedPlan.createdBy }} · {{ formatTime(selectedPlan.createdAt) }}</dd></div><div><dt>审批</dt><dd>{{ selectedPlan.approvedBy ? `${selectedPlan.approvedBy} · ${formatTime(selectedPlan.approvedAt)}` : '尚未审批' }}</dd></div><div><dt>执行</dt><dd>{{ selectedPlan.executedBy ? `${selectedPlan.executedBy} · ${formatTime(selectedPlan.executedAt)}` : '尚未执行' }}</dd></div><div><dt>复验</dt><dd>{{ formatTime(selectedPlan.verifiedAt) }}</dd></div></dl>
          <p v-if="selectedPlan.failureMessage" class="plan-failure"><AlertTriangle :size="16" />{{ selectedPlan.failureMessage }}</p>
          <div class="plan-items"><article v-for="item in selectedPlan.items" :key="item.id"><header><strong>{{ item.objectName }}</strong><span>{{ item.verificationStatus === 'PASSED' ? '复验通过' : item.executable ? '等待执行' : '需人工处理' }}</span></header><p>{{ item.expectedValue }} ← {{ item.actualValue }}</p><pre v-if="item.ddlPreview">{{ item.ddlPreview }}</pre><small>{{ item.executionNote }}</small></article></div>

          <div v-if="selectedPlan.status === 'DRAFT' && canApprove" class="plan-action-form">
            <p v-if="selectedPlan.createdBy === authState.user?.loginName">创建人不能审批自己的方案，请由另一名具备审批权限的用户处理。</p>
            <p v-else-if="selectedPlan.executableCount !== selectedPlan.issueCount">该方案包含不可在线执行项，只能取消后按修改方向由开发或DBA处理。</p>
            <label v-else><span>审批说明</span><textarea v-model="approvalNote" rows="3" maxlength="500" /></label>
            <button v-if="canApproveSelected" class="prototype-primary" :disabled="!approvalNote.trim() || Boolean(action)" @click="approvePlan"><ShieldCheck :size="16" />批准方案</button>
          </div>
          <div v-if="selectedPlan.status === 'APPROVED' && canExecute" class="plan-action-form danger-zone">
            <p>执行将在一个SQL Server事务中完成；任一DDL或复验失败都会整体回滚。</p>
            <label><span>输入方案编号确认执行</span><input v-model="executionConfirmation" :placeholder="selectedPlan.planNo" autocomplete="off" /></label>
            <button class="prototype-primary" :disabled="!canExecuteSelected || Boolean(action)" @click="executePlan"><Play :size="16" />执行并复验</button>
          </div>
          <footer v-if="canPlan && ['DRAFT', 'APPROVED'].includes(selectedPlan.status)" class="plan-cancel"><button class="work-danger-button" :disabled="Boolean(action)" @click="cancelPlan">取消方案</button><span>只取消未执行方案，不删除历史。</span></footer>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.contract-page{display:grid;gap:16px}.contract-intro{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;padding:22px 24px;border:1px solid #d9e2e5;border-radius:14px;background:#fff}.contract-intro>div>span{color:#147565;font-size:13px;font-weight:700}.contract-intro h2{margin:4px 0 6px;font-size:24px}.contract-intro p,.contract-card-head p{margin:0;color:#697d87}.compact{display:inline-flex;align-items:center;gap:7px;white-space:nowrap}.contract-notice{margin:0;padding:12px 15px;border-radius:9px;display:flex;align-items:center;gap:8px}.contract-notice.error,.plan-failure{background:#fff0ed;color:#9c3f31}.contract-notice.success{background:#e9f6f1;color:#146b5b}.contract-metrics{display:grid;grid-template-columns:repeat(4,1fr);border:1px solid #d9e2e5;border-radius:14px;background:#fff;overflow:hidden}.contract-metrics>div{padding:17px 19px;border-right:1px solid #e8edef}.contract-metrics>div:last-child{border:0}.contract-metrics span,.contract-metrics small{display:block;color:#718087;font-size:12px}.contract-metrics strong{display:block;margin:7px 0;font-size:25px}.contract-metrics .time{font-size:15px;line-height:1.7}.contract-card{border:1px solid #d9e2e5;border-radius:14px;background:#fff;overflow:hidden}.contract-card-head{min-height:72px;padding:16px 19px;border-bottom:1px solid #e6edef;display:flex;align-items:center;justify-content:space-between;gap:15px}.contract-card-head h3{margin:0 0 4px;font-size:17px}.contract-card-head>span{color:#718087;font-size:12px}.contract-issue-table{min-width:1000px}.contract-issue-table td:first-child,.contract-issue-table th:first-child{width:58px;text-align:center}.contract-issue-table td:nth-child(4){max-width:410px;white-space:normal}.contract-issue-table input[type=checkbox]{width:16px;height:16px;accent-color:#147467}.contract-create{padding:14px 18px;border-top:1px solid #e8edef;display:flex;align-items:end;justify-content:flex-end;gap:12px;background:#f8faf9}.contract-create label{flex:1;display:grid;gap:6px}.contract-create label span,.plan-action-form label span{font-size:12px;font-weight:700;color:#415963}.contract-create input,.plan-action-form input,.plan-action-form textarea{width:100%;border:1px solid #cad7da;border-radius:7px;padding:9px 11px;background:#fff;color:#233a44;font:inherit}.contract-plan-grid{display:grid;grid-template-columns:minmax(300px,.72fr) minmax(0,1.28fr);gap:16px;align-items:start}.plan-list>button{width:100%;min-height:72px;padding:12px 16px;border:0;border-bottom:1px solid #edf1f2;background:#fff;display:flex;align-items:center;justify-content:space-between;gap:12px;text-align:left}.plan-list>button:hover,.plan-list>button.active{background:#eef7f4}.plan-list>button>span{min-width:0;display:grid;gap:5px}.plan-list>button>span:last-child{justify-items:end}.plan-list strong,.plan-list small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.plan-list small{color:#7a8a91;font-size:11px}.plan-list em{font-style:normal}.plan-facts{margin:0;padding:4px 18px 12px;display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.plan-facts>div{padding:12px 0;border-bottom:1px solid #edf1f2}.plan-facts dt{color:#718087;font-size:11px}.plan-facts dd{margin:5px 0 0;color:#2d424c;font-size:12px}.plan-failure{margin:0 18px 14px;padding:11px 12px;display:flex;gap:8px;border-radius:7px}.plan-items{padding:0 18px 16px;display:grid;gap:9px}.plan-items article{padding:12px;border:1px solid #e2e9eb;border-radius:8px;background:#fafbfb}.plan-items header{display:flex;justify-content:space-between;gap:10px}.plan-items header span{color:#147565;font-size:11px}.plan-items p,.plan-items small{color:#677a82;font-size:11px}.plan-items pre{padding:9px;border-radius:5px;background:#26383f;color:#e5f1ed;white-space:pre-wrap;overflow-wrap:anywhere;font-size:11px}.plan-action-form{margin:0 18px 16px;padding:14px;border:1px solid #d7e4e1;border-radius:8px;background:#f4f9f7;display:grid;gap:10px}.plan-action-form p{margin:0;color:#667a81;font-size:12px}.plan-action-form label{display:grid;gap:6px}.danger-zone{border-color:#e7d2ad;background:#fffaf0}.plan-cancel{padding:13px 18px;border-top:1px solid #e7ecee;display:flex;align-items:center;gap:10px}.plan-cancel span{color:#73858d;font-size:11px}.contract-state{min-height:180px;padding:30px;display:grid;place-items:center;align-content:center;gap:9px;color:#60757e}.compact-state{min-height:110px}.spinning{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:1050px){.contract-metrics{grid-template-columns:1fr 1fr}.contract-metrics>div:nth-child(2){border-right:0}.contract-metrics>div:nth-child(-n+2){border-bottom:1px solid #e8edef}.contract-plan-grid{grid-template-columns:1fr}}@media(max-width:700px){.contract-intro{display:grid}.contract-metrics{grid-template-columns:1fr}.contract-metrics>div{border-right:0;border-bottom:1px solid #e8edef}.contract-create{align-items:stretch;flex-direction:column}.plan-facts{grid-template-columns:1fr}}
</style>
