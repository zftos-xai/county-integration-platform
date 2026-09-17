<!-- 原型管理配置面板：仅用于交互评审，不代表真实 API 数据。 -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { BookOpen, Check, Info, KeyRound, Plus, Search, ShieldCheck, SlidersHorizontal, Users, X } from 'lucide-vue-next'
import AdminPagination from '@/components/AdminPagination.vue'

type Section = 'parameters' | 'dictionaries' | 'system-users' | 'system-roles'
type DictionaryItem = { id: string; code: string; name: string; order: number; enabled: boolean }
type Dictionary = { id: string; code: string; name: string; description: string; enabled: boolean; updatedAt: string; items: DictionaryItem[] }
type SystemUser = { id: string; loginName: string; displayName: string; organization: string; roles: string[]; scopes: string[]; enabled: boolean; mustChangePassword: boolean; updatedAt: string }
type SystemRole = { id: string; code: string; name: string; description: string; enabled: boolean; systemManaged: boolean; users: number; permissions: string[]; updatedAt: string }
type SystemOrganization = { id: string; code: string; name: string; type: string; parent: string; enabled: boolean; validFrom: string; validTo: string; updatedAt: string }
type Editable = Dictionary | SystemUser | SystemRole

const props = defineProps<{ section: Section }>()
const emit = defineEmits<{ notify: [message: string]; audit: [event: { action: string; object: string; detail: string }] }>()

const query = ref('')
const status = ref('全部状态')
const page = ref(1)
const pageSize = ref(10)
const drawer = ref<'dictionary' | 'user' | 'role' | null>(null)
const isNew = ref(false)
const draft = ref<Editable | null>(null)
const drawerTab = ref<'detail' | 'items' | 'permissions' | 'record'>('detail')

const dictionaries = ref<Dictionary[]>([
  { id: 'DICT-002', code: 'EXCHANGE_DIRECTION', name: '交换方向', description: '用于接口和交换记录的方向标识', enabled: true, updatedAt: '2026-09-14 16:40', items: [
    { id: 'ITEM-003', code: 'UPSTREAM', name: '上行', order: 10, enabled: true },
    { id: 'ITEM-004', code: 'DOWNSTREAM', name: '下行', order: 20, enabled: true },
  ] },
  { id: 'DICT-003', code: 'ALERT_LEVEL', name: '告警级别', description: '用于告警优先级展示', enabled: true, updatedAt: '2026-09-13 09:15', items: [
    { id: 'ITEM-005', code: 'HIGH', name: '高', order: 10, enabled: true },
    { id: 'ITEM-006', code: 'MEDIUM', name: '中', order: 20, enabled: true },
    { id: 'ITEM-007', code: 'LOW', name: '低', order: 30, enabled: true },
  ] },
])
const users = ref<SystemUser[]>([
  { id: 'USR-001', loginName: 'zhangjianguo', displayName: '张建国', organization: '县域平台管理机构', roles: ['平台管理员'], scopes: ['县域平台管理机构', '示例县人民医院', '示例青禾镇卫生院'], enabled: true, mustChangePassword: false, updatedAt: '2026-09-15 09:42' },
  { id: 'USR-002', loginName: 'liqiang', displayName: '李强', organization: '县域平台管理机构', roles: [], scopes: ['县域平台管理机构', '示例县人民医院', '示例青禾镇卫生院'], enabled: true, mustChangePassword: false, updatedAt: '2026-09-14 17:05' },
  { id: 'USR-003', loginName: 'wangqiang', displayName: '王强', organization: '示例县人民医院', roles: [], scopes: ['示例县人民医院'], enabled: true, mustChangePassword: true, updatedAt: '2026-09-13 14:22' },
  { id: 'USR-004', loginName: 'chenmin', displayName: '陈敏', organization: '示例青禾镇卫生院', roles: [], scopes: ['示例青禾镇卫生院'], enabled: false, mustChangePassword: false, updatedAt: '2026-09-10 11:30' },
])
const permissionCatalog = [
  { group: '用户管理', items: [['identity:read', '查看用户'], ['identity:write', '维护用户']] },
  { group: '权限管理', items: [['access:read', '查看角色权限'], ['access:write', '维护角色权限']] },
  { group: '基础配置', items: [['configuration:read', '查看参数与字典'], ['configuration:write', '维护参数与字典']] },
  { group: '机构管理', items: [['organization:read', '查看机构'], ['organization:write', '维护机构']] },
]
const roles = ref<SystemRole[]>([
  { id: 'ROLE-001', code: 'PLATFORM_ADMIN', name: '平台管理员', description: '维护平台配置、账号、权限和机构', enabled: true, systemManaged: true, users: 1, permissions: permissionCatalog.flatMap(group => group.items.map(item => item[0])), updatedAt: '2026-09-15 09:35' },
])
const organizations = ref<SystemOrganization[]>([
  { id: 'ORG-001', code: 'COUNTY-PLATFORM', name: '县域平台管理机构', type: '机构类型待确认', parent: '—', enabled: true, validFrom: '2026-01-01', validTo: '长期', updatedAt: '2026-09-15 08:30' },
  { id: 'ORG-002', code: 'SIM-HOSP-000', name: '示例县人民医院', type: '机构类型待确认', parent: '县域平台管理机构', enabled: true, validFrom: '2026-01-01', validTo: '2027-12-31', updatedAt: '2026-09-14 16:10' },
  { id: 'ORG-003', code: 'SIM-PRIMARY-001', name: '示例青禾镇卫生院', type: '机构类型待确认', parent: '县域平台管理机构', enabled: true, validFrom: '2026-01-01', validTo: '2027-12-31', updatedAt: '2026-09-13 11:05' },
  { id: 'ORG-004', code: 'SIM-PRIMARY-099', name: '示例停用机构', type: '机构类型待确认', parent: '县域平台管理机构', enabled: false, validFrom: '2025-01-01', validTo: '2026-08-31', updatedAt: '2026-09-01 09:20' },
])

const sectionMeta = computed(() => ({
  parameters: { icon: SlidersHorizontal, count: 0, noun: '项参数', action: '' },
  dictionaries: { icon: BookOpen, count: dictionaries.value.length, noun: '类字典', action: '新增字典' },
  'system-users': { icon: Users, count: users.value.length, noun: '个用户', action: '新增用户' },
  'system-roles': { icon: KeyRound, count: roles.value.length, noun: '个角色', action: '新增角色' },
}[props.section]))
const normalizedQuery = computed(() => query.value.trim().toLowerCase())
function matches(value: string, enabled: boolean) {
  return value.toLowerCase().includes(normalizedQuery.value) && (status.value === '全部状态' || enabled === (status.value === '启用'))
}
const dictionaryRows = computed(() => dictionaries.value.filter(item => matches(`${item.code} ${item.name} ${item.description}`, item.enabled)))
const userRows = computed(() => users.value.filter(item => matches(`${item.loginName} ${item.displayName} ${item.organization} ${item.roles.join(' ')}`, item.enabled)))
const roleRows = computed(() => roles.value.filter(item => matches(`${item.code} ${item.name} ${item.description}`, item.enabled)))
const currentRows = computed(() => props.section === 'dictionaries' ? dictionaryRows.value : props.section === 'system-users' ? userRows.value : props.section === 'system-roles' ? roleRows.value : [])
const pagedRows = computed(() => currentRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const enabledOrganizationNames = computed(() => organizations.value.filter(item => item.enabled).map(item => item.name))

function scopeSummary(item: SystemUser) {
  const allEnabled = enabledOrganizationNames.value.length > 0 && enabledOrganizationNames.value.every(name => item.scopes.includes(name))
  return allEnabled ? `全部启用机构（${enabledOrganizationNames.value.length}）` : item.scopes.join('、') || '未配置'
}

// 切换管理对象时清理上一对象的筛选和编辑草稿。
watch(() => props.section, () => { query.value = ''; status.value = '全部状态'; page.value = 1; closeDrawer() })
// 筛选变化后回到第一页，避免旧页码产生空白列表。
watch([query, status], () => { page.value = 1 })

function clone<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T }
function openDrawer(kind: Exclude<typeof drawer.value, null>, item: Editable) {
  drawer.value = kind; draft.value = clone(item); isNew.value = false
  drawerTab.value = kind === 'dictionary' ? 'items' : kind === 'role' ? 'permissions' : 'detail'
}
function addItem() {
  isNew.value = true
  if (props.section === 'dictionaries') openNew('dictionary', { id: '', code: '', name: '', description: '', enabled: true, updatedAt: '', items: [] })
  if (props.section === 'system-users') openNew('user', { id: '', loginName: '', displayName: '', organization: '县域平台管理机构', roles: [], scopes: [], enabled: true, mustChangePassword: true, updatedAt: '' })
  if (props.section === 'system-roles') openNew('role', { id: '', code: '', name: '', description: '', enabled: true, systemManaged: false, users: 0, permissions: [], updatedAt: '' })
}
function openNew(kind: Exclude<typeof drawer.value, null>, item: Editable) { drawer.value = kind; draft.value = item; drawerTab.value = kind === 'role' ? 'permissions' : kind === 'dictionary' ? 'items' : 'detail' }
function closeDrawer() { drawer.value = null; draft.value = null; isNew.value = false }
function saveDraft() {
  if (!draft.value || !drawer.value) return
  const now = '2026-09-15 12:00'
  let object = ''
  if (drawer.value === 'dictionary') {
    const item = draft.value as Dictionary
    if (!item.code.trim() || !item.name.trim()) return emit('notify', '请填写字典编码和名称')
    item.id ||= `DICT-${String(dictionaries.value.length + 1).padStart(3, '0')}`; item.updatedAt = now; object = item.code
    const index = dictionaries.value.findIndex(row => row.id === item.id); index >= 0 ? dictionaries.value.splice(index, 1, clone(item)) : dictionaries.value.unshift(clone(item))
  } else if (drawer.value === 'user') {
    const item = draft.value as SystemUser
    if (!item.loginName.trim() || !item.displayName.trim()) return emit('notify', '请填写登录名和姓名')
    item.id ||= `USR-${String(users.value.length + 1).padStart(3, '0')}`; item.updatedAt = now; object = item.loginName
    const index = users.value.findIndex(row => row.id === item.id); index >= 0 ? users.value.splice(index, 1, clone(item)) : users.value.unshift(clone(item))
  } else {
    const item = draft.value as SystemRole
    if (!item.code.trim() || !item.name.trim()) return emit('notify', '请填写角色编码和名称')
    item.id ||= `ROLE-${String(roles.value.length + 1).padStart(3, '0')}`; item.updatedAt = now; object = item.code
    const index = roles.value.findIndex(row => row.id === item.id); index >= 0 ? roles.value.splice(index, 1, clone(item)) : roles.value.unshift(clone(item))
  }
  emit('audit', { action: isNew.value ? '新增系统配置' : '更新系统配置', object, detail: `保存${drawerTitle.value}（原型演示）` })
  emit('notify', `${drawerTitle.value}已保存`); closeDrawer()
}
function addDictionaryEntry() {
  if (drawer.value !== 'dictionary' || !draft.value) return
  const item = draft.value as Dictionary
  item.items.push({ id: `ITEM-${Date.now()}`, code: `NEW_${item.items.length + 1}`, name: '新字典项', order: (item.items.length + 1) * 10, enabled: true })
}
function togglePermission(code: string) {
  if (drawer.value !== 'role' || !draft.value) return
  const item = draft.value as SystemRole; const index = item.permissions.indexOf(code)
  index >= 0 ? item.permissions.splice(index, 1) : item.permissions.push(code)
}
function resetPassword() {
  if (drawer.value !== 'user' || !draft.value) return
  ;(draft.value as SystemUser).mustChangePassword = true
  emit('notify', '已生成密码重置记录；用户下次登录必须修改密码')
}
function editableName(item: Editable) {
  return 'name' in item ? item.name : item.displayName
}
function editableCode(item: Editable) {
  return 'code' in item ? item.code : item.loginName
}
const drawerTitle = computed(() => ({ dictionary: '数据字典', user: '用户信息', role: '角色权限' }[drawer.value ?? 'dictionary']))
</script>

<template>
  <div class="admin-panel">
    <section v-if="section === 'parameters'" class="admin-parameter-boundary">
      <div class="admin-boundary-icon"><SlidersHorizontal :size="22" /></div>
      <div><h2>当前没有已注册参数</h2><p>参数键必须随服务版本在后端代码中注册。本页只能维护已注册参数在全局或机构范围内的取值，不能从页面新增参数键。</p></div>
      <span class="badge bg-blue-lt text-blue">configuration:read</span>
    </section>
    <template v-if="section === 'parameters'">
      <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input disabled aria-label="搜索已注册参数" placeholder="没有可搜索的已注册参数" /></label><select disabled aria-label="环境筛选"><option>全部环境</option></select><select disabled aria-label="机构范围筛选"><option>全部机构范围</option></select><span>0 项参数</span></div>
      <section class="prototype-section work-table-section"><div class="prototype-table-wrap"><table class="work-table"><thead><tr><th>参数键 / 名称</th><th>值类型</th><th>作用范围</th><th>当前值</th><th>状态</th><th>最后修改</th><th>操作</th></tr></thead></table><div class="admin-empty"><Info :size="22" /><strong>代码注册清单为空</strong><span>新增参数需先完成需求确认和代码评审；后端注册后，本页才会出现对应配置项。</span></div></div><AdminPagination :total="0" :page="1" :page-size="10" /></section>
      <section class="admin-rule-card"><header><ShieldCheck :size="18" /><strong>页面与后端边界</strong></header><dl><div><dt>查看权限</dt><dd>configuration:read</dd></div><div><dt>修改取值</dt><dd>configuration:write</dd></div><div><dt>允许的操作</dt><dd>为已注册参数设置环境或机构范围值</dd></div><div><dt>不允许的操作</dt><dd>在页面动态创建参数键</dd></div></dl></section>
    </template>

    <template v-else>
      <div class="admin-summary"><component :is="sectionMeta.icon" :size="20" /><div><strong>{{ sectionMeta.count }} {{ sectionMeta.noun }}</strong><small>数据为合成演示，字段和操作对应现有后端能力</small></div><button class="prototype-button" @click="addItem"><Plus :size="15" />{{ sectionMeta.action }}</button></div>
      <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" :aria-label="section === 'dictionaries' ? '搜索数据字典' : section === 'system-users' ? '搜索用户' : '搜索角色'" :placeholder="section === 'dictionaries' ? '字典编码、名称或说明' : section === 'system-users' ? '登录名、姓名、机构或角色' : '角色编码、名称或说明'" /></label><select v-model="status" aria-label="状态筛选"><option>全部状态</option><option value="启用">已启用</option><option value="停用">已停用</option></select><span>{{ currentRows.length }} 条</span></div>

      <section class="prototype-section work-table-section action-column-table"><div class="prototype-table-wrap">
        <table v-if="section === 'dictionaries'" class="work-table"><thead><tr><th>字典编码 / 名称</th><th>用途说明</th><th>字典项</th><th>状态</th><th>最后修改</th><th>操作</th></tr></thead><tbody><tr v-for="item in pagedRows as Dictionary[]" :key="item.id"><td><button class="work-row-link" @click="openDrawer('dictionary', item)">{{ item.name }}</button><small>{{ item.code }}</small></td><td>{{ item.description }}</td><td>{{ item.items.length }} 项<small>{{ item.items.filter(row => row.enabled).length }} 项启用</small></td><td><span class="prototype-tag" :class="item.enabled ? 'success' : 'danger'">{{ item.enabled ? '已启用' : '已停用' }}</span></td><td>{{ item.updatedAt }}</td><td><button class="btn btn-outline-primary btn-sm" @click="openDrawer('dictionary', item)">配置字典项</button></td></tr></tbody></table>
        <table v-else-if="section === 'system-users'" class="work-table"><thead><tr><th>登录账号 / 姓名</th><th>主要机构</th><th>角色</th><th>可访问机构</th><th>登录要求</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in pagedRows as SystemUser[]" :key="item.id"><td><button class="work-row-link" @click="openDrawer('user', item)">{{ item.displayName }}</button><small>{{ item.loginName }}</small></td><td>{{ item.organization }}</td><td>{{ item.roles.join('、') || '未分配' }}</td><td>{{ scopeSummary(item) }}</td><td>{{ item.mustChangePassword ? '下次登录修改密码' : '无待处理要求' }}</td><td><span class="prototype-tag" :class="item.enabled ? 'success' : 'danger'">{{ item.enabled ? '已启用' : '已停用' }}</span></td><td><button class="btn btn-outline-primary btn-sm" @click="openDrawer('user', item)">管理账号</button></td></tr></tbody></table>
        <table v-else class="work-table"><thead><tr><th>角色编码 / 名称</th><th>职责说明</th><th>用户数</th><th>权限数</th><th>类型</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in pagedRows as SystemRole[]" :key="item.id"><td><button class="work-row-link" @click="openDrawer('role', item)">{{ item.name }}</button><small>{{ item.code }}</small></td><td>{{ item.description }}</td><td>{{ item.users }} 人</td><td>{{ item.permissions.length }} 项</td><td>{{ item.systemManaged ? '系统角色' : '自定义角色' }}</td><td><span class="prototype-tag" :class="item.enabled ? 'success' : 'danger'">{{ item.enabled ? '已启用' : '已停用' }}</span></td><td><button class="btn btn-outline-primary btn-sm" @click="openDrawer('role', item)">配置权限</button></td></tr></tbody></table>
        <div v-if="!currentRows.length" class="prototype-empty">没有符合条件的记录</div>
      </div><AdminPagination :total="currentRows.length" :page="page" :page-size="pageSize" @update:page="page = $event" @update:page-size="pageSize = $event" /></section>
    </template>

    <div v-if="drawer && draft" class="data-record-overlay" @click.self="closeDrawer">
      <aside class="data-record-drawer admin-drawer" role="dialog" aria-modal="true" :aria-label="drawerTitle">
        <header class="drawer-titlebar"><div><small>{{ isNew ? '新增' : '编辑' }}</small><h2>{{ drawerTitle }}</h2></div><button class="prototype-icon" aria-label="关闭" @click="closeDrawer"><X :size="18" /></button></header>
        <div class="admin-drawer-summary"><span class="prototype-tag" :class="draft.enabled ? 'success' : 'danger'">{{ draft.enabled ? '已启用' : '已停用' }}</span><strong>{{ editableName(draft) || '待填写' }}</strong><small>{{ editableCode(draft) || '保存后生成标识' }}</small></div>
        <nav class="drawer-tabs"><button :class="{ active: drawerTab === 'detail' }" @click="drawerTab = 'detail'">基本信息</button><button v-if="drawer === 'dictionary'" :class="{ active: drawerTab === 'items' }" @click="drawerTab = 'items'">字典项</button><button v-if="drawer === 'role'" :class="{ active: drawerTab === 'permissions' }" @click="drawerTab = 'permissions'">功能权限</button><button :class="{ active: drawerTab === 'record' }" @click="drawerTab = 'record'">变更记录</button></nav>
        <div class="admin-drawer-body">
          <template v-if="drawerTab === 'detail'">
            <div v-if="drawer === 'dictionary'" class="work-form work-form-compact"><label>字典编码</label><input v-model="(draft as Dictionary).code" :disabled="!isNew" /><label>字典名称</label><input v-model="(draft as Dictionary).name" /><label>用途说明</label><textarea v-model="(draft as Dictionary).description" rows="3"></textarea><label>启用状态</label><select v-model="(draft as Dictionary).enabled"><option :value="true">启用</option><option :value="false">停用</option></select></div>
            <div v-else-if="drawer === 'user'" class="work-form work-form-compact"><label>登录名</label><input v-model="(draft as SystemUser).loginName" :disabled="!isNew" /><label>姓名</label><input v-model="(draft as SystemUser).displayName" /><label>主要机构</label><select v-model="(draft as SystemUser).organization"><option v-for="item in organizations" :key="item.id">{{ item.name }}</option></select><label>角色</label><div class="admin-checks"><label v-for="item in roles" :key="item.id"><input v-model="(draft as SystemUser).roles" type="checkbox" :value="item.name" />{{ item.name }}</label></div><label>机构范围</label><div class="admin-checks"><label v-for="item in organizations" :key="item.id"><input v-model="(draft as SystemUser).scopes" type="checkbox" :value="item.name" />{{ item.name }}</label></div><label>账号状态</label><select v-model="(draft as SystemUser).enabled"><option :value="true">启用</option><option :value="false">停用</option></select><div class="work-inline-actions"><button v-if="!isNew" class="btn btn-outline-secondary" @click="resetPassword">重置密码</button><span class="admin-action-note">重置后用户下次登录必须修改密码。</span></div></div>
            <div v-else class="work-form work-form-compact"><label>角色编码</label><input v-model="(draft as SystemRole).code" :disabled="!isNew || (draft as SystemRole).systemManaged" /><label>角色名称</label><input v-model="(draft as SystemRole).name" /><label>职责说明</label><textarea v-model="(draft as SystemRole).description" rows="3"></textarea><label>角色状态</label><select v-model="(draft as SystemRole).enabled"><option :value="true">启用</option><option :value="false">停用</option></select></div>
          </template>
          <template v-else-if="drawerTab === 'items' && drawer === 'dictionary'"><div class="section-heading"><div><h3>字典项</h3><small>编码保存后不可直接修改；停用不会删除历史引用。</small></div><button class="btn btn-outline-primary btn-sm" @click="addDictionaryEntry"><Plus :size="14" />新增字典项</button></div><div class="admin-item-list"><div class="admin-item-head"><span>编码</span><span>显示名称</span><span>顺序</span><span>状态</span></div><div v-for="item in (draft as Dictionary).items" :key="item.id" class="admin-item-row"><input v-model="item.code" /><input v-model="item.name" /><input v-model="item.order" type="number" /><select v-model="item.enabled"><option :value="true">启用</option><option :value="false">停用</option></select></div></div></template>
          <template v-else-if="drawerTab === 'permissions' && drawer === 'role'"><div class="admin-permission-note"><ShieldCheck :size="17" /><span>权限按后端鉴权代码分组。保存后，拥有该角色的用户按新权限访问功能。</span></div><section v-for="group in permissionCatalog" :key="group.group" class="admin-permission-group"><h3>{{ group.group }}</h3><label v-for="permission in group.items" :key="permission[0]"><input type="checkbox" :checked="(draft as SystemRole).permissions.includes(permission[0])" @change="togglePermission(permission[0])" /><span><strong>{{ permission[1] }}</strong><small>{{ permission[0] }}</small></span></label></section></template>
          <template v-else><div class="admin-record"><Check :size="18" /><div><strong>最近更新</strong><p>{{ draft.updatedAt || '尚未保存' }}</p><small>正式环境由审计记录保存操作人、时间、对象和变更内容。</small></div></div></template>
        </div>
        <footer class="drawer-footer"><span>保存需要对应的写权限</span><div><button class="btn btn-outline-secondary" @click="closeDrawer">取消</button><button class="btn btn-primary" @click="saveDraft">保存</button></div></footer>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.admin-panel { min-width: 0; }
.admin-summary { min-height: 64px; margin-bottom: 12px; padding: 12px 15px; display: flex; align-items: center; gap: 12px; border: 1px solid #dfe5e8; border-radius: 6px; background: #fff; }
.admin-summary > svg { width: 36px; height: 36px; padding: 8px; border-radius: 6px; background: #eaf5f1; color: #176e61; }
.admin-summary > div { display: grid; gap: 3px; }
.admin-summary strong { color: #273b47; font-size: 13px; }
.admin-summary small { color: #7a8792; font-size: 10px; }
.admin-summary .prototype-button { margin-left: auto; }
.admin-parameter-boundary { min-height: 92px; margin-bottom: 12px; padding: 16px 18px; display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; align-items: center; gap: 14px; border: 1px solid #cddfdb; border-radius: 6px; background: linear-gradient(100deg, #f0f8f5, #fff); }
.admin-boundary-icon { width: 40px; height: 40px; border-radius: 7px; background: #dcefe8; color: #176f60; display: grid; place-items: center; }
.admin-parameter-boundary h2 { margin: 0 0 5px; color: #253b46; font-size: 15px; }
.admin-parameter-boundary p { max-width: 820px; margin: 0; color: #657781; font-size: 11px; line-height: 1.6; }
.admin-empty { min-height: 220px; display: grid; place-items: center; align-content: center; gap: 8px; color: #77858f; text-align: center; }
.admin-empty svg { color: #6a9189; }.admin-empty strong { color: #344b56; font-size: 13px; }.admin-empty span { max-width: 520px; font-size: 11px; line-height: 1.6; }
.admin-rule-card { margin-top: 14px; border: 1px solid #e0e5e8; border-radius: 6px; background: #fff; overflow: hidden; }
.admin-rule-card header { min-height: 48px; padding: 10px 15px; border-bottom: 1px solid #edf0f2; display: flex; align-items: center; gap: 8px; color: #2c4e49; font-size: 12px; }
.admin-rule-card dl { margin: 0; display: grid; grid-template-columns: repeat(4, 1fr); }.admin-rule-card dl > div { padding: 13px 15px; border-right: 1px solid #edf0f2; }.admin-rule-card dl > div:last-child { border: 0; }.admin-rule-card dt { color: #7b8892; font-size: 10px; }.admin-rule-card dd { margin: 6px 0 0; color: #344853; font-size: 11px; line-height: 1.5; }
.admin-drawer { width: min(720px, 100vw); }.drawer-titlebar h2 { margin: 3px 0 0; font-size: 18px; }.drawer-titlebar small { color: #77858f; font-size: 10px; }
.admin-drawer-summary { min-height: 58px; padding: 10px 20px; border-bottom: 1px solid #e8ecef; background: #fafbfc; display: flex; align-items: center; gap: 10px; }.admin-drawer-summary strong { color: #30444f; font-size: 12px; }.admin-drawer-summary small { color: #7a8791; font-size: 10px; }
.admin-drawer-body { flex: 1; min-height: 0; padding: 20px; overflow-y: auto; }.drawer-tabs { padding-inline: 20px; border-bottom: 1px solid #e4e8eb; display: flex; gap: 26px; }.drawer-tabs button { min-height: 44px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: #657581; font-size: 11px; }.drawer-tabs button.active { color: #147365; border-bottom-color: #147365; font-weight: 700; }
.drawer-footer > div { display: flex; gap: 8px; }.admin-checks { display: grid; gap: 7px; }.admin-checks label { font-weight: 400; display: flex; gap: 8px; align-items: center; }.admin-checks input { width: 15px; }.admin-action-note { align-self: center; color: #7a8792; font-size: 10px; }
.section-heading h3 { margin: 0; font-size: 13px; }.section-heading small { color: #7a8792; font-size: 10px; }.section-heading .btn { display: inline-flex; align-items: center; gap: 5px; }
.admin-item-list { margin-top: 12px; border: 1px solid #e1e6e8; border-radius: 5px; overflow: hidden; }.admin-item-head, .admin-item-row { display: grid; grid-template-columns: 1.2fr 1.2fr 80px 92px; gap: 8px; padding: 9px 10px; align-items: center; }.admin-item-head { background: #f7f8fa; color: #66747f; font-size: 10px; font-weight: 700; }.admin-item-row { border-top: 1px solid #edf0f2; }.admin-item-row input, .admin-item-row select { min-width: 0; height: 34px; padding: 0 8px; border: 1px solid #ced8dc; border-radius: 4px; font-size: 11px; }
.admin-permission-note { margin-bottom: 14px; padding: 11px 12px; border: 1px solid #cfe1dc; border-radius: 5px; background: #eff7f4; color: #37685e; display: flex; gap: 8px; font-size: 11px; line-height: 1.5; }.admin-permission-group { margin-bottom: 14px; border: 1px solid #e1e6e8; border-radius: 5px; overflow: hidden; }.admin-permission-group h3 { margin: 0; padding: 9px 12px; background: #f7f8fa; color: #435661; font-size: 11px; }.admin-permission-group label { min-height: 52px; padding: 8px 12px; border-top: 1px solid #edf0f2; display: flex; align-items: center; gap: 10px; cursor: pointer; }.admin-permission-group label:hover { background: #f6faf8; }.admin-permission-group input { accent-color: #147365; }.admin-permission-group strong, .admin-permission-group small { display: block; }.admin-permission-group strong { font-size: 11px; }.admin-permission-group small { margin-top: 3px; color: #7c8993; font-size: 9px; }
.admin-record { padding: 14px; border: 1px solid #dce5e2; border-radius: 5px; background: #f8faf9; display: flex; gap: 10px; color: #38675d; }.admin-record strong { font-size: 12px; }.admin-record p { margin: 4px 0; color: #40545f; font-size: 11px; }.admin-record small { color: #78858f; font-size: 10px; }
@media (max-width: 900px) { .admin-rule-card dl { grid-template-columns: 1fr 1fr; }.admin-parameter-boundary { grid-template-columns: 42px 1fr; }.admin-parameter-boundary .badge { grid-column: 2; width: fit-content; } }
</style>
