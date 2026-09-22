<!-- ICD10公共目录合成原型：验证无诊断版本时的类别、查询和同步事实层级，不读取或写入真实业务数据。 -->
<script setup lang="ts">
import { CheckCircle2, ChevronRight, Database, RefreshCw, Search, X } from 'lucide-vue-next'
import { computed, ref } from 'vue'

type DiagnosisCategory = 'WESTERN' | 'TRADITIONAL'

type DiagnosisItem = {
  name: string
  code: string
  category: DiagnosisCategory
  mnemonic: string
  createdAt: string
  syncedAt: string
}

const categories: ReadonlyArray<{ id: DiagnosisCategory; label: string; count: number }> = [
  { id: 'WESTERN', label: '西医诊断', count: 12_583 },
  { id: 'TRADITIONAL', label: '中医诊断', count: 5_208 },
]

const diagnosisItems: ReadonlyArray<DiagnosisItem> = [
  { name: '高血压病', code: 'I10', category: 'WESTERN', mnemonic: 'GX YB', createdAt: '2019-01-01 08:00:00', syncedAt: '2026-09-22 10:14:32' },
  { name: '2型糖尿病', code: 'E11', category: 'WESTERN', mnemonic: 'TN B', createdAt: '2019-01-01 08:00:00', syncedAt: '2026-09-22 09:33:21' },
  { name: '急性上呼吸道感染', code: 'J06', category: 'WESTERN', mnemonic: 'SFXDG', createdAt: '2019-01-01 08:00:00', syncedAt: '2026-09-22 08:45:10' },
  { name: '肺炎', code: 'J18', category: 'WESTERN', mnemonic: 'FY', createdAt: '2019-01-01 08:00:00', syncedAt: '2026-09-22 11:02:18' },
  { name: '慢性胃炎', code: 'K29', category: 'WESTERN', mnemonic: 'MXWY', createdAt: '2019-01-01 08:00:00', syncedAt: '2026-09-22 09:28:37' },
  { name: '胆囊结石', code: 'K80', category: 'WESTERN', mnemonic: 'DNJS', createdAt: '2019-01-01 08:00:00', syncedAt: '2026-09-22 07:58:41' },
  { name: '脑梗死', code: 'I63', category: 'WESTERN', mnemonic: 'NGS', createdAt: '2019-01-01 08:00:00', syncedAt: '2026-09-22 10:26:55' },
  { name: '冠状动脉粥样硬化性心脏病', code: 'I25', category: 'WESTERN', mnemonic: 'GZDM', createdAt: '2019-01-01 08:00:00', syncedAt: '2026-09-22 09:11:03' },
  { name: '感冒', code: 'A01.01', category: 'TRADITIONAL', mnemonic: 'GM', createdAt: '2022-01-01 08:00:00', syncedAt: '2026-09-22 10:08:14' },
  { name: '咳嗽病', code: 'A04.01', category: 'TRADITIONAL', mnemonic: 'KS', createdAt: '2022-01-01 08:00:00', syncedAt: '2026-09-22 10:08:14' },
  { name: '胃脘痛', code: 'A06.01', category: 'TRADITIONAL', mnemonic: 'WWT', createdAt: '2022-01-01 08:00:00', syncedAt: '2026-09-22 10:08:14' },
  { name: '眩晕病', code: 'A03.01', category: 'TRADITIONAL', mnemonic: 'XY', createdAt: '2022-01-01 08:00:00', syncedAt: '2026-09-22 10:08:14' },
]

const selectedCategory = ref<DiagnosisCategory>('WESTERN')
const keyword = ref('')
const submittedKeyword = ref('')
const isSyncPanelOpen = ref(false)
const toast = ref('')

const selectedCategoryInfo = computed(() => categories.find((item) => item.id === selectedCategory.value) ?? categories[0])
const visibleItems = computed(() => {
  const normalizedKeyword = submittedKeyword.value.trim().toLocaleLowerCase('zh-CN')
  return diagnosisItems.filter((item) => item.category === selectedCategory.value
    && (!normalizedKeyword || `${item.name} ${item.code} ${item.mnemonic}`.toLocaleLowerCase('zh-CN').includes(normalizedKeyword)))
})

/** 切换来源已提供的疾病类别，并清除当前公共目录查询条件。 */
function selectCategory(category: DiagnosisCategory) {
  selectedCategory.value = category
  submittedKeyword.value = ''
  keyword.value = ''
}

/** 提交合成查询，不调用 HIS 或真实目录 API。 */
function search() {
  submittedKeyword.value = keyword.value
}

/** 清除原型查询条件。 */
function clearSearch() {
  keyword.value = ''
  submittedKeyword.value = ''
}

/** 展示原型中的同步创建边界，不创建真实批次。 */
function openSyncPanel() {
  isSyncPanelOpen.value = true
  toast.value = ''
}

/** 关闭合成同步面板。 */
function closeSyncPanel() {
  isSyncPanelOpen.value = false
}

/** 记录原型操作反馈，不发起任何外部调用。 */
function confirmPrototypeSync() {
  isSyncPanelOpen.value = false
  toast.value = '已记录原型操作：正式接入后将由服务端创建批次并固定类别及查询范围。'
}
</script>

<template>
  <section class="content icd10-prototype" aria-label="ICD10 诊断目录页面原型">
    <nav class="dataset-nav" aria-label="基础数据集">
      <RouterLink to="/master-data/directory">综合目录</RouterLink>
      <RouterLink to="/master-data/directory/medical">三大目录</RouterLink>
      <RouterLink class="active" to="/master-data/directory/icd10">ICD-10</RouterLink>
    </nav>
    <p class="prototype-notice" role="status"><Database :size="15" aria-hidden="true" />合成原型：平台公共目录不读取或修改真实数据。</p>

    <div class="icd10-layout">
      <aside class="diagnosis-context-rail" aria-label="诊断类别">
        <header><h2>诊断类型</h2><span>公共目录</span></header>
        <section v-for="category in categories" :key="category.id" class="diagnosis-category">
          <button type="button" :aria-pressed="selectedCategory === category.id" class="diagnosis-category-button" :class="{ active: selectedCategory === category.id }" @click="selectCategory(category.id)">
            <strong>{{ category.label }}</strong><span>{{ category.count.toLocaleString('zh-CN') }}</span>
          </button>
        </section>
      </aside>

      <section class="icd10-workspace">
        <header class="icd10-summary">
          <div class="summary-icon"><CheckCircle2 :size="21" aria-hidden="true" /></div>
          <div class="summary-title"><span>当前目录</span><strong>{{ selectedCategoryInfo.label }}<ChevronRight :size="16" aria-hidden="true" /><em>来源未提供诊断版本</em></strong></div>
          <dl class="summary-facts">
            <div><dt>目录申报数</dt><dd>{{ selectedCategoryInfo.count.toLocaleString('zh-CN') }}</dd></div>
            <div><dt>已获取数</dt><dd>{{ selectedCategoryInfo.count.toLocaleString('zh-CN') }}</dd></div>
            <div><dt>当前状态</dt><dd class="success"><i aria-hidden="true" />已完成</dd></div>
          </dl>
          <button class="prototype-button" type="button" @click="openSyncPanel"><RefreshCw :size="16" aria-hidden="true" />新建同步</button>
        </header>
        <p class="icd10-boundary">平台公共目录不按机构归属或筛选；来源未提供诊断版本时，不展示或传递版本条件。</p>

        <section class="icd10-catalog">
          <form class="icd10-toolbar" @submit.prevent="search">
            <label class="icd10-search"><Search :size="17" aria-hidden="true" /><input v-model="keyword" maxlength="50" aria-label="搜索诊断名称、ICD编码或助记码" placeholder="请输入诊断名称、ICD 编码或助记码" /></label>
            <button class="prototype-button" type="submit">查询</button>
            <button class="work-quiet-button" type="button" @click="clearSearch">清除</button>
            <span>共 {{ submittedKeyword ? visibleItems.length : selectedCategoryInfo.count.toLocaleString('zh-CN') }} 条</span>
          </form>
          <div class="icd10-table-wrap">
            <table class="icd10-table">
              <thead><tr><th scope="col">诊断名称 / 编码</th><th scope="col">助记码</th><th scope="col">创建时间</th><th scope="col">最近同步</th><th scope="col">操作</th></tr></thead>
              <tbody>
                <tr v-for="item in visibleItems" :key="item.code">
                  <td><strong>{{ item.name }}</strong><small>{{ item.code }}</small></td><td>{{ item.mnemonic }}</td><td>{{ item.createdAt }}</td><td>{{ item.syncedAt }}</td><td><button class="icd10-view-button" type="button" @click="toast = `${item.name} 的合成详情将在正式字段映射确认后展示。`">查看</button></td>
                </tr>
                <tr v-if="!visibleItems.length"><td colspan="5" class="icd10-empty">没有符合查询条件的诊断记录</td></tr>
              </tbody>
            </table>
          </div>
          <footer class="icd10-pagination"><span>合成展示 {{ visibleItems.length }} 条记录</span><div><button type="button" disabled>上一页</button><button class="active" type="button">1</button><button type="button">2</button><button type="button">3</button><button type="button">下一页</button></div></footer>
        </section>
      </section>
    </div>

    <div v-if="isSyncPanelOpen" class="icd10-overlay" role="presentation" @click.self="closeSyncPanel">
      <section class="icd10-drawer" role="dialog" aria-modal="true" aria-labelledby="icd10-sync-title">
        <header><div><span>合成同步操作</span><h2 id="icd10-sync-title">新建 ICD10 同步</h2></div><button class="prototype-icon" type="button" aria-label="关闭" @click="closeSyncPanel"><X :size="18" /></button></header>
        <div class="icd10-drawer-body"><dl><div><dt>目录归属</dt><dd>平台公共目录</dd></div><div><dt>疾病类别</dt><dd>{{ selectedCategoryInfo.label }}</dd></div><div><dt>诊断版本</dt><dd>来源未提供</dd></div><div><dt>机构归属</dt><dd>不适用</dd></div></dl><p>正式接入前仍需确认来源时间范围、行数、分页规则和病种编码的稳定性；本操作不会创建真实同步批次。</p></div>
        <footer><button class="work-quiet-button" type="button" @click="closeSyncPanel">取消</button><button class="prototype-button" type="button" @click="confirmPrototypeSync">确认原型操作</button></footer>
      </section>
    </div>
    <p v-if="toast" class="icd10-toast" role="status">{{ toast }}</p>
  </section>
</template>

<style scoped>
.icd10-prototype { max-width:none; padding-top:16px; }
.dataset-nav { margin-bottom:12px; padding:0; display:flex; gap:8px; border-bottom:1px solid var(--line); overflow-x:auto; }
.dataset-nav a { flex:none; padding:11px 13px; border:1px solid transparent; border-bottom:0; border-radius:7px 7px 0 0; color:var(--muted); font-size:13px; text-decoration:none; }
.dataset-nav a:hover { background:#f5f8f8; }
.dataset-nav a.active { border-color:#d6e5e1; background:#eaf5f2; color:var(--accent); font-weight:650; }
.prototype-notice { width:fit-content; margin:0 0 12px auto; padding:7px 10px; display:flex; align-items:center; gap:6px; border:1px solid #e2d7bd; border-radius:4px; background:#fffaf0; color:#7d6030; font-size:11px; }
.icd10-layout { display:grid; grid-template-columns:244px minmax(0,1fr); gap:14px; align-items:start; }
.diagnosis-context-rail,.icd10-workspace { min-width:0; border:1px solid var(--line); border-radius:7px; background:var(--surface); overflow:hidden; }
.diagnosis-context-rail { position:sticky; top:16px; }
.diagnosis-context-rail>header { min-height:54px; padding:0 15px; display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid var(--line-soft); }
.diagnosis-context-rail h2 { margin:0; font-size:15px; }.diagnosis-context-rail>header span { color:var(--muted); font-size:11px; }
.diagnosis-category { padding:8px; }.diagnosis-category+.diagnosis-category { border-top:1px solid var(--line-soft); }
.diagnosis-category-button { width:100%; border:0; background:transparent; color:#52676e; text-align:left; }
.diagnosis-category-button { min-height:36px; padding:7px 8px; display:flex; align-items:center; justify-content:space-between; border-radius:5px; font-size:13px; }.diagnosis-category-button:hover { background:#f2f7f5; }.diagnosis-category-button.active { color:var(--accent-dark); }.diagnosis-category-button span { color:#75908a; font-size:12px; }
.icd10-summary { min-height:86px; padding:14px 16px; display:flex; align-items:center; gap:12px; border-bottom:1px solid #d9e8e4; background:#f7fbfa; }.summary-icon { width:39px; height:39px; flex:none; display:grid; place-items:center; border-radius:50%; background:#deefe9; color:#24755f; }.summary-title { min-width:250px; display:grid; gap:4px; }.summary-title span { color:var(--muted); font-size:11px; }.summary-title strong { display:flex; align-items:center; gap:5px; color:#2c4e48; font-size:14px; }.summary-title em { padding:2px 6px; border-radius:10px; background:#edf3f2; color:#5d756e; font-size:10px; font-style:normal; font-weight:600; white-space:nowrap; }.summary-facts { flex:1; min-width:0; margin:0; display:flex; align-items:center; justify-content:flex-end; }.summary-facts div { min-width:108px; padding:0 15px; border-left:1px solid #d9e6e2; }.summary-facts dt { color:#748580; font-size:10px; }.summary-facts dd { margin:4px 0 0; color:#2b4640; font-size:14px; font-weight:650; }.summary-facts dd.success { display:flex; align-items:center; gap:6px; color:#29775f; }.summary-facts i { width:7px; height:7px; border-radius:50%; background:#35a16f; }
.icd10-boundary { margin:0; padding:9px 16px; border-bottom:1px solid var(--line-soft); color:#617a73; font-size:11px; line-height:1.5; }
.icd10-toolbar { min-height:66px; padding:13px 16px; display:flex; align-items:center; gap:8px; border-bottom:1px solid var(--line-soft); }.icd10-search { height:36px; flex:1 1 260px; max-width:540px; padding:0 11px; display:flex; align-items:center; gap:8px; border:1px solid #ccd9dc; border-radius:5px; color:var(--muted); }.icd10-search:focus-within { border-color:var(--accent); box-shadow:0 0 0 2px rgb(20 116 103 / 12%); }.icd10-search input { min-width:0; width:100%; border:0; outline:0; background:transparent; color:var(--ink); font-size:12px; }.icd10-toolbar>span { margin-left:auto; color:var(--muted); font-size:12px; white-space:nowrap; }
.icd10-table-wrap { overflow:auto; }.icd10-table { width:100%; min-width:820px; border-collapse:collapse; table-layout:fixed; }.icd10-table th,.icd10-table td { height:55px; padding:0 14px; border-bottom:1px solid var(--line-soft); color:#435963; font-size:12px; text-align:left; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }.icd10-table th { height:40px; background:#f7f9f9; color:#61737a; font-size:11px; font-weight:650; }.icd10-table th:nth-child(1) { width:31%; }.icd10-table th:nth-child(2) { width:13%; }.icd10-table th:nth-child(3),.icd10-table th:nth-child(4) { width:20%; }.icd10-table td strong { display:block; color:var(--ink); overflow:hidden; text-overflow:ellipsis; }.icd10-table td small { display:block; margin-top:3px; color:var(--muted); font-size:11px; }.icd10-table tr:hover { background:#f6faf8; }.icd10-table td:last-child,.icd10-table th:last-child { text-align:right; }.icd10-view-button { min-height:30px; padding:0 10px; border:1px solid #72aa9e; border-radius:4px; background:white; color:var(--accent); font-size:12px; }.icd10-view-button:hover { background:#f0f8f5; }.icd10-empty { color:var(--muted); text-align:center; }
.icd10-pagination { min-height:57px; padding:0 16px; display:flex; align-items:center; justify-content:space-between; color:var(--muted); font-size:12px; }.icd10-pagination div { display:flex; gap:4px; }.icd10-pagination button { min-width:30px; height:29px; padding:0 7px; border:0; border-radius:4px; background:transparent; color:#4f6268; font-size:12px; }.icd10-pagination button.active { background:var(--accent); color:white; }.icd10-pagination button:disabled { color:#a4b1b3; }
.icd10-overlay { position:fixed; inset:0; z-index:80; display:flex; justify-content:flex-end; background:rgb(23 46 49 / 40%); }.icd10-drawer { width:min(470px,100%); min-height:100%; display:flex; flex-direction:column; background:white; box-shadow:-10px 0 28px rgb(23 46 49 / 18%); }.icd10-drawer>header,.icd10-drawer>footer { padding:16px 20px; display:flex; align-items:center; justify-content:space-between; gap:12px; }.icd10-drawer>header { border-bottom:1px solid var(--line); }.icd10-drawer header span { color:var(--muted); font-size:11px; }.icd10-drawer h2 { margin:4px 0 0; font-size:17px; }.icd10-drawer-body { padding:20px; }.icd10-drawer-body dl { margin:0; border:1px solid #d5e1de; border-radius:5px; overflow:hidden; }.icd10-drawer-body dl div { min-height:43px; padding:0 12px; display:grid; grid-template-columns:95px 1fr; align-items:center; border-bottom:1px solid #e4ece9; }.icd10-drawer-body dl div:last-child { border-bottom:0; }.icd10-drawer-body dt { color:var(--muted); font-size:12px; }.icd10-drawer-body dd { margin:0; color:#314e47; font-size:12px; font-weight:650; }.icd10-drawer-body p { margin:15px 0 0; color:#61746e; font-size:12px; line-height:1.75; }.icd10-drawer>footer { margin-top:auto; border-top:1px solid var(--line); justify-content:flex-end; }.icd10-toast { position:fixed; right:28px; bottom:28px; z-index:81; max-width:min(480px,calc(100vw - 32px)); margin:0; padding:11px 13px; border:1px solid #b8dacd; border-radius:5px; background:#edf8f2; color:#27674e; font-size:12px; box-shadow:0 5px 18px rgb(23 46 49 / 14%); }
@media(max-width:1040px) { .summary-facts div { min-width:92px; padding:0 10px; }.summary-title { min-width:185px; }.icd10-summary { flex-wrap:wrap; }.icd10-summary>.prototype-button { margin-left:auto; } }
@media(max-width:780px) { .icd10-layout { grid-template-columns:1fr; }.diagnosis-context-rail { position:static; }.diagnosis-category { display:grid; grid-template-columns:1fr 1fr; gap:4px; }.diagnosis-category-button { grid-column:1/-1; }.summary-facts { order:3; width:100%; justify-content:flex-start; }.icd10-toolbar>span { width:100%; margin-left:0; }.icd10-pagination { gap:10px; flex-wrap:wrap; padding:10px 16px; } }
@media(max-width:520px) { .icd10-prototype { padding:14px; }.prototype-notice { margin-left:0; }.icd10-summary { align-items:flex-start; }.summary-facts { display:grid; grid-template-columns:1fr 1fr; }.summary-facts div { padding:5px 0; border:0; }.icd10-search { flex-basis:100%; max-width:none; }.icd10-toolbar { flex-wrap:wrap; }.icd10-overlay { align-items:flex-end; }.icd10-drawer { min-height:auto; max-height:92vh; }.icd10-toast { right:16px; bottom:16px; } }
</style>
