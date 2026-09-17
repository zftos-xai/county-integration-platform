<!-- 原型外部系统面板：演示系统与环境端点的配置流程。 -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckCircle2, KeyRound, Plus, Search, ServerCog, ShieldCheck, X } from 'lucide-vue-next'
import AdminPagination from '@/components/AdminPagination.vue'
import { endpointReadinessError } from '../model/externalEndpointRules'

type Endpoint = { id: string; environment: '开发' | '测试' | '生产'; organization: string; baseUrl: string; connectTimeout: number; readTimeout: number; tls: boolean; credentialRef: string; enabled: boolean }
type ExternalSystem = { id: string; code: string; name: string; description: string; enabled: boolean; updatedAt: string; endpoints: Endpoint[] }

const emit = defineEmits<{ notify: [message: string]; audit: [event: { action: string; object: string; detail: string }] }>()
const query = ref('')
const status = ref('全部状态')
const page = ref(1)
const pageSize = ref(10)
const selected = ref<ExternalSystem | null>(null)
const draft = ref<ExternalSystem | null>(null)
const drawerTab = ref<'detail' | 'endpoints' | 'checks'>('endpoints')
const isNew = ref(false)

const systems = ref<ExternalSystem[]>([
  { id: 'SYS-001', code: 'COUNTY_HIS', name: '县医院 HIS', description: '县医院业务数据提供系统（合成演示）', enabled: true, updatedAt: '2026-09-15 10:20', endpoints: [
    { id: 'EP-001', environment: '测试', organization: '示例县人民医院', baseUrl: 'https://his-test.example.invalid/api', connectTimeout: 3000, readTimeout: 15000, tls: true, credentialRef: 'secret-store://county-his/test', enabled: true },
    { id: 'EP-002', environment: '生产', organization: '示例县人民医院', baseUrl: 'https://his.example.invalid/api', connectTimeout: 3000, readTimeout: 15000, tls: true, credentialRef: 'secret-store://county-his/production', enabled: false },
  ] },
  { id: 'SYS-002', code: 'PRIMARY_CARE', name: '基层业务系统', description: '基层卫生院业务协同系统（合成演示）', enabled: true, updatedAt: '2026-09-14 16:05', endpoints: [
    { id: 'EP-003', environment: '测试', organization: '示例青禾镇卫生院', baseUrl: 'https://primary-test.example.invalid/service', connectTimeout: 5000, readTimeout: 20000, tls: true, credentialRef: 'secret-store://primary-care/test', enabled: true },
  ] },
])

const rows = computed(() => systems.value.filter(item => {
  const text = `${item.code} ${item.name} ${item.description}`.toLowerCase()
  return text.includes(query.value.trim().toLowerCase()) && (status.value === '全部状态' || item.enabled === (status.value === '启用'))
}))
const pagedRows = computed(() => rows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
// 筛选变化后回到第一页，避免旧页码产生空白列表。
watch([query, status], () => { page.value = 1 })

function clone<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T }
function open(item: ExternalSystem) { selected.value = item; draft.value = clone(item); isNew.value = false; drawerTab.value = 'endpoints' }
function addSystem() {
  isNew.value = true
  selected.value = null
  draft.value = { id: '', code: '', name: '', description: '', enabled: true, updatedAt: '', endpoints: [] }
  drawerTab.value = 'detail'
}
function close() { selected.value = null; draft.value = null; isNew.value = false }
function addEndpoint() {
  if (!draft.value) return
  draft.value.endpoints.push({ id: `EP-${Date.now()}`, environment: '测试', organization: '县域平台管理机构', baseUrl: 'https://', connectTimeout: 3000, readTimeout: 15000, tls: true, credentialRef: '', enabled: false })
}
function endpointReady(item: Endpoint) { return endpointReadinessError(item) === null }
function save() {
  if (!draft.value?.code.trim() || !draft.value.name.trim() || !draft.value.description.trim()) return emit('notify', '请完整填写系统编码、名称和用途说明')
  const invalidEndpoint = draft.value.endpoints.find(item => item.enabled && !endpointReady(item))
  if (invalidEndpoint) return emit('notify', endpointReadinessError(invalidEndpoint) ?? '端点配置未通过启用检查')
  const item = clone(draft.value)
  item.code = item.code.trim().toUpperCase(); item.id ||= `SYS-${String(systems.value.length + 1).padStart(3, '0')}`; item.updatedAt = '2026-09-15 12:00'
  const index = systems.value.findIndex(row => row.id === item.id); index >= 0 ? systems.value.splice(index, 1, item) : systems.value.unshift(item)
  emit('audit', { action: isNew.value ? '新增外部系统' : '更新外部系统', object: item.code, detail: `保存系统和 ${item.endpoints.length} 个环境端点（原型演示；凭证仅显示引用）` })
  emit('notify', '外部系统配置已保存（原型演示）'); close()
}
</script>

<template>
  <div class="external-panel">
    <section class="external-boundary"><ShieldCheck :size="21" /><div><strong>凭证不进入平台数据库</strong><small>页面只维护医院批准密钥来源的引用标识；启用端点前检查 HTTPS、超时和机构范围。</small></div><span class="badge bg-blue-lt text-blue">configuration:write</span></section>
    <div class="admin-summary"><ServerCog :size="20" /><div><strong>{{ systems.length }} 个外部系统</strong><small>{{ systems.reduce((sum, item) => sum + item.endpoints.filter(endpoint => endpoint.enabled).length, 0) }} 个端点当前启用 · 数据为合成演示</small></div><button class="prototype-button" @click="addSystem"><Plus :size="15" />新增外部系统</button></div>
    <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" aria-label="搜索外部系统" placeholder="系统编码、名称或用途" /></label><select v-model="status" aria-label="状态筛选"><option>全部状态</option><option value="启用">已启用</option><option value="停用">已停用</option></select><span>{{ rows.length }} 条</span></div>
    <section class="prototype-section work-table-section action-column-table"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>系统编码 / 名称</th><th>用途说明</th><th>环境端点</th><th>启用端点</th><th>状态</th><th>最后修改</th><th>操作</th></tr></thead><tbody><tr v-for="item in pagedRows" :key="item.id"><td><button class="work-row-link" @click="open(item)">{{ item.name }}</button><small>{{ item.code }}</small></td><td>{{ item.description }}</td><td>{{ item.endpoints.length }} 个</td><td>{{ item.endpoints.filter(endpoint => endpoint.enabled).length }} 个</td><td><span class="prototype-tag" :class="item.enabled ? 'success' : 'danger'">{{ item.enabled ? '已启用' : '已停用' }}</span></td><td>{{ item.updatedAt }}</td><td><button class="btn btn-outline-primary btn-sm" @click="open(item)">配置端点</button></td></tr></tbody></table><div v-if="!rows.length" class="prototype-empty">没有符合条件的外部系统</div></div><AdminPagination :total="rows.length" :page="page" :page-size="pageSize" @update:page="page = $event" @update:page-size="pageSize = $event" /></section>

    <div v-if="draft" class="data-record-overlay" @click.self="close"><aside class="data-record-drawer external-drawer" role="dialog" aria-modal="true" aria-label="外部系统配置"><header class="drawer-titlebar"><div><small>{{ isNew ? '新增' : '编辑' }}</small><h2>外部系统配置</h2></div><button class="prototype-icon" aria-label="关闭" @click="close"><X :size="18" /></button></header><div class="admin-drawer-summary"><span class="prototype-tag" :class="draft.enabled ? 'success' : 'danger'">{{ draft.enabled ? '已启用' : '已停用' }}</span><strong>{{ draft.name || '待填写' }}</strong><small>{{ draft.code || '保存后生成标识' }}</small></div>
      <nav class="drawer-tabs"><button :class="{ active: drawerTab === 'detail' }" @click="drawerTab = 'detail'">基本信息</button><button :class="{ active: drawerTab === 'endpoints' }" @click="drawerTab = 'endpoints'">环境端点</button><button :class="{ active: drawerTab === 'checks' }" @click="drawerTab = 'checks'">启用检查</button></nav>
      <div class="admin-drawer-body"><div v-if="drawerTab === 'detail'" class="work-form work-form-compact"><label>系统编码</label><input v-model="draft.code" :disabled="!isNew" /><label>系统名称</label><input v-model="draft.name" /><label>用途说明</label><textarea v-model="draft.description" rows="3"></textarea><label>启用状态</label><select v-model="draft.enabled"><option :value="true">启用</option><option :value="false">停用</option></select></div>
        <template v-else-if="drawerTab === 'endpoints'"><div class="section-heading"><div><h3>环境端点</h3><small>同一系统、环境与机构范围只允许一个启用端点。</small></div><button class="btn btn-outline-primary btn-sm" @click="addEndpoint"><Plus :size="14" />新增端点</button></div><div class="endpoint-list"><article v-for="endpoint in draft.endpoints" :key="endpoint.id" class="endpoint-card"><header><strong>{{ endpoint.environment }} · {{ endpoint.organization }}</strong><span class="prototype-tag" :class="endpointReady(endpoint) ? 'success' : 'warning'">{{ endpointReady(endpoint) ? '检查通过' : '待完善' }}</span></header><div class="endpoint-grid"><label>环境<select v-model="endpoint.environment"><option>开发</option><option>测试</option><option>生产</option></select></label><label>机构范围<input v-model="endpoint.organization" /></label><label class="wide">基础地址<input v-model="endpoint.baseUrl" placeholder="https://" /></label><label>连接超时（毫秒）<input v-model.number="endpoint.connectTimeout" type="number" min="100" /></label><label>读取超时（毫秒）<input v-model.number="endpoint.readTimeout" type="number" min="100" /></label><label class="wide">凭证引用<KeyRound :size="14" /><input v-model="endpoint.credentialRef" placeholder="secret-store://...（不填写密钥明文）" /></label><label class="endpoint-check"><input v-model="endpoint.tls" type="checkbox" />启用 TLS</label><label class="endpoint-check"><input v-model="endpoint.enabled" type="checkbox" />启用端点</label></div></article><div v-if="!draft.endpoints.length" class="admin-empty">暂未配置环境端点</div></div></template>
        <div v-else class="check-list"><div><CheckCircle2 :size="18" /><span><strong>地址与 TLS</strong><small>只允许 HTTPS 地址，生产端点必须启用 TLS。</small></span></div><div><CheckCircle2 :size="18" /><span><strong>凭证边界</strong><small>仅保存医院批准密钥来源的引用 URI，不展示或回传完整引用。</small></span></div><div><CheckCircle2 :size="18" /><span><strong>超时与范围</strong><small>读取超时不得小于连接超时，机构范围由服务端再次校验。</small></span></div></div>
      </div><footer class="drawer-footer"><button class="btn btn-outline-secondary" @click="close">取消</button><button class="btn btn-primary" @click="save">保存配置</button></footer></aside></div>
  </div>
</template>

<style scoped>
.external-panel{min-width:0}.admin-summary{min-height:64px;margin-bottom:12px;padding:12px 15px;display:flex;align-items:center;gap:12px;border:1px solid #dfe5e8;border-radius:6px;background:#fff}.admin-summary>svg{width:36px;height:36px;padding:8px;border-radius:6px;background:#eaf5f1;color:#176e61}.admin-summary>div{display:grid;gap:3px}.admin-summary strong{color:#273b47;font-size:13px}.admin-summary small{color:#7a8792;font-size:10px}.admin-summary .prototype-button{margin-left:auto}.external-drawer .drawer-titlebar h2{margin:3px 0 0;font-size:18px}.external-drawer .drawer-titlebar small{color:#77858f;font-size:10px}.admin-drawer-summary{min-height:58px;padding:10px 20px;border-bottom:1px solid #e8ecef;background:#fafbfc;display:flex;align-items:center;gap:10px}.admin-drawer-summary strong{color:#30444f;font-size:12px}.admin-drawer-summary small{color:#7a8791;font-size:10px}.drawer-tabs{padding-inline:20px;border-bottom:1px solid #e4e8eb;display:flex;gap:26px}.drawer-tabs button{min-height:44px;padding:0;border:0;border-bottom:2px solid transparent;background:transparent;color:#657581;font-size:11px}.drawer-tabs button.active{color:#147365;border-bottom-color:#147365;font-weight:700}.admin-drawer-body{flex:1;min-height:0;padding:20px;overflow-y:auto}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.section-heading h3{margin:0;font-size:13px}.section-heading small{color:#7a8792;font-size:10px}.admin-empty{min-height:180px;display:grid;place-items:center;color:#77858f}
.external-boundary{margin-bottom:12px;padding:14px 16px;display:grid;grid-template-columns:28px minmax(0,1fr) auto;align-items:center;gap:12px;border:1px solid #cddfdb;border-radius:6px;background:linear-gradient(100deg,#f0f8f5,#fff);color:#275a50}.external-boundary strong,.external-boundary small{display:block}.external-boundary small{margin-top:4px;color:#657781;font-size:11px}.external-drawer{width:min(720px,96vw)}.endpoint-list{display:grid;gap:12px}.endpoint-card{padding:14px;border:1px solid #dce4e8;border-radius:6px;background:#fff}.endpoint-card header{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}.endpoint-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px 12px}.endpoint-grid label{display:grid;gap:5px;color:#667983;font-size:11px}.endpoint-grid input,.endpoint-grid select{width:100%;min-height:34px;border:1px solid #ced9de;border-radius:4px;padding:6px 9px;background:#fff;color:#263b45}.endpoint-grid .wide{grid-column:1/-1;position:relative}.endpoint-grid .wide svg{position:absolute;left:9px;bottom:10px}.endpoint-grid .wide svg+input{padding-left:30px}.endpoint-grid .endpoint-check{display:flex;align-items:center;gap:7px;color:#334b56}.endpoint-check input{width:auto;min-height:0}.check-list{display:grid;gap:12px}.check-list>div{display:flex;gap:10px;padding:14px;border:1px solid #dce4e8;border-radius:6px;color:#2e7568}.check-list span,.check-list strong,.check-list small{display:block}.check-list small{margin-top:4px;color:#667983}@media(max-width:700px){.external-boundary{grid-template-columns:28px 1fr}.external-boundary .badge{grid-column:2;width:max-content}.endpoint-grid{grid-template-columns:1fr}.endpoint-grid .wide{grid-column:auto}}
</style>
