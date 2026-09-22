import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { compileScript, parse } from '@vue/compiler-sfc'
import * as Vue from 'vue'
import ts from 'typescript'

const directory = new URL('../src/views/master-data/directory/', import.meta.url)
const source = await readFile(new URL('DirectoryLayout.vue', directory), 'utf8')
const { descriptor } = parse(source)
const { outputText } = ts.transpileModule(compileScript(descriptor, { id: 'directory-test', inlineTemplate: true }).content, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
})

// 使用Vue渲染器验证真实组件的状态和事件；原生输入绑定与CSS由真实浏览器验收。
function node(type, text = '') { return { type, text, props: {}, children: [], parent: null } }
const renderer = Vue.createRenderer({
  createElement: type => node(type), createText: text => node('#text', text), createComment: text => node('#comment', text),
  setText: (target, text) => { target.text = text },
  setElementText: (target, text) => { target.text = text; target.children = [] },
  patchProp: (target, key, previous, value) => { target.props[key] = value },
  parentNode: target => target.parent,
  nextSibling: target => target.parent?.children[target.parent.children.indexOf(target) + 1] ?? null,
  insert(target, parent, anchor) {
    if (target.parent) target.parent.children.splice(target.parent.children.indexOf(target), 1)
    target.parent = parent
    parent.children.splice(anchor ? parent.children.indexOf(anchor) : parent.children.length, 0, target)
  },
  remove(target) { target.parent?.children.splice(target.parent.children.indexOf(target), 1) },
})
function descendants(root) { return [root, ...root.children.flatMap(descendants)] }
function text(root) { return root.text + root.children.map(text).join('') }
async function flush() { await Promise.resolve(); await Vue.nextTick(); await Vue.nextTick() }

async function mountLayout(t, { query = {}, codes = ['ORG-A', 'ORG-B'], organizationFailure, ...overrides } = {}) {
  const route = Vue.reactive({ path: '/master-data/directory', fullPath: '/master-data/directory', query })
  const authState = Vue.reactive({ user: { organizationCode: 'ORG-A', organizationCodes: codes }, isInitialized: true })
  const events = []
  const routeChanges = []
  class ApiClientError extends Error { constructor(message, status) { super(message); this.status = status } }
  const organizationResult = codes.map(code => ({ organizationCode: code, organizationName: `测试机构${code}`, enabled: true }))
  const router = { async replace(location) { routeChanges.push(location); if (location.query) route.query = location.query } }
  const imports = {
    vue: { ...Vue, vModelText: {} },
    'vue-router': { useRoute: () => route, useRouter: () => router },
    'lucide-vue-next': Object.fromEntries(['AlertCircle', 'Building2', 'CheckCircle2', 'Database', 'RefreshCw', 'Search'].map(name => [name, { render: () => Vue.h('svg') }])),
    '@/api/system/organization': { listOrganizations: async () => { if (organizationFailure) throw new ApiClientError('机构读取失败', organizationFailure); return organizationResult } },
    '@/store/modules/auth': { authState, hasPermission: () => true },
    '@/utils/request': { ApiClientError },
  }
  const module = { exports: {} }
  new Function('require', 'module', 'exports', outputText)(name => {
    assert.ok(name in imports, `未声明的组件依赖: ${name}`)
    return imports[name]
  }, module, module.exports)
  const props = Vue.reactive({
    organizationCode: '', keyword: '', title: '医院综合目录', types: ['DEPARTMENT'],
    typeLabels: { DEPARTMENT: '科室' }, directoryType: 'DEPARTMENT', counts: { DEPARTMENT: 0 },
    total: 0, isLoading: false, isRefreshing: false, isEmpty: true, error: null, ...overrides,
  })
  const root = node('root')
  const app = renderer.createApp({ render: () => Vue.h(module.exports.default, {
    ...props,
    'onUpdate:organizationCode': value => { props.organizationCode = value },
    'onUpdate:keyword': value => { props.keyword = value },
    onOrganizationChange: () => events.push(props.organizationCode),
    onClear: () => events.push('clear'),
    onSearch: () => events.push('search'),
    onRefresh: () => events.push('refresh'),
  }, { default: () => Vue.h('table', '测试业务记录'), pagination: () => Vue.h('footer', '测试分页') }) })
  app.component('RouterLink', { props: ['to'], setup: (props, { slots }) => () => Vue.h('a', { to: props.to }, slots.default?.()) })
  app.mount(root)
  t.after(() => app.unmount())
  await flush()
  return { root, route, props, events, routeChanges, authState }
}

test('两个页面使用同一布局和表格样式，不再独立定义机构栏和查询区', async () => {
  for (const name of ['HospitalDirectoryView.vue', 'MedicalDirectoryView.vue']) {
    const page = await readFile(new URL(name, directory), 'utf8')
    assert.match(page, /<DirectoryLayout/)
    assert.match(page, /directory-records\.css/)
    assert.doesNotMatch(page, /<h1|class="dataset-nav"|class="directory-toolbar"|listOrganizations/)
  }
  const staticClasses = [...source.matchAll(/(?<!:)class="([^"]*)"/g)].flatMap(match => match[1].split(/\s+/))
  assert.ok(!staticClasses.includes('empty'))
  assert.doesNotMatch(source, /\{ empty:/)
  assert.match(source, /'is-empty':/)
})

test('两个目录的展开详情共用带单元格边界的字段栅格', async () => {
  const styles = await readFile(new URL('directory-records.css', directory), 'utf8')
  const hospital = await readFile(new URL('HospitalDirectoryView.vue', directory), 'utf8')
  assert.match(styles, /\.directory-detail dl\s*\{[^}]*border-top:1px dashed var\(--detail-line\)/s)
  assert.match(styles, /\.directory-detail dt\s*\{[^}]*border-right:1px dashed var\(--detail-line\)/s)
  assert.match(styles, /\.directory-table th:last-child,\.directory-table td\.directory-actions\s*\{[^}]*position:sticky/s)
  assert.match(styles, /\.directory-detail--hospital section:first-child dl\s*\{ grid-template-columns:max-content; \}/)
  assert.match(hospital, /directory-detail directory-detail--hospital/)
  assert.match(styles, /\.directory-detail dt,\.directory-detail dd\s*\{[^}]*white-space:nowrap;/s)
  assert.doesNotMatch(styles, /\.directory-detail dl div\s*\{[^}]*min-height:/s)
  assert.match(styles, /\.directory-detail\s*\{[^}]*width:100%;[^}]*overflow-x:auto;[^}]*grid-template-columns:max-content max-content;/s)
  assert.match(styles, /\.directory-detail section:last-child dl\s*\{ grid-template-columns:max-content; \}/)
  assert.doesNotMatch(styles, /@media\s*\(max-width:1100px\)[\s\S]*?\.directory-detail/)
})

test('数据目录页头和页签去除通用导航内边距造成的多余留白', async () => {
  const layout = await readFile(new URL('../../../layout/AppLayout.vue', directory), 'utf8')
  const shellStyles = await readFile(new URL('../../../assets/styles/prototype.css', directory), 'utf8')
  assert.match(layout, /'directory-page': route\.path\.startsWith\('\/master-data\/directory'\)/)
  assert.match(shellStyles, /\.prototype-main\.directory-page \.prototype-breadcrumb\s*\{[^}]*padding:\s*0;/s)
  assert.match(shellStyles, /\.prototype-main\.directory-page \.prototype-content\s*\{[^}]*padding:\s*12px 20px 28px;/s)
  assert.match(source, /\.dataset-nav\s*\{[^}]*padding:\s*0;/s)
})

test('类型页签已说明目录种类，不再重复标题说明；刷新仍在查询区可用', async t => {
  const { root, events } = await mountLayout(t, { isEmpty: false, total: 46, counts: { DEPARTMENT: 46 } })
  assert.doesNotMatch(text(root), /当前医院综合目录 · 科室|来自最近一次成功同步|查看不会重新调用 HIS/)
  assert.equal(descendants(root).filter(item => item.type === 'h3').length, 0)
  assert.match(text(root), /共 46 条/)
  const refresh = descendants(root).find(item => item.type === 'button' && text(item) === '刷新')
  assert.ok(refresh)
  refresh.props.onClick()
  assert.equal(events.at(-1), 'refresh')
})

test('URL中已授权机构优先于默认机构，机构目录页签携带同一机构', async t => {
  const { root, props, events } = await mountLayout(t, { query: { organizationCode: 'ORG-B' } })
  assert.equal(props.organizationCode, 'ORG-B')
  assert.deepEqual(events, ['ORG-B'])
  const links = descendants(root).filter(item => item.type === 'a')
  assert.equal(links.length, 3)
  assert.deepEqual(links.map(text), ['综合目录', '三大目录', 'ICD-10'])
  assert.ok(links.slice(0, 2).every(item => item.props.to.query.organizationCode === 'ORG-B'))
  assert.equal(links[2].props.to, '/master-data/directory/icd10')
  assert.match(text(root), /机构编码：ORG-B/)
})

test('历史导航保留机构上下文，仅在机构变化时重新读取', async t => {
  const { route, props, events } = await mountLayout(t)
  route.query = { organizationCode: 'ORG-B' }
  await flush()
  assert.equal(props.organizationCode, 'ORG-B')
  assert.deepEqual(events, ['ORG-A', 'ORG-B'])
  route.query = { organizationCode: 'ORG-B', unrelated: 'x' }
  await flush()
  assert.equal(events.length, 2)
})

test('未授权的URL机构不进入请求，无机构时不发出目录加载事件', async t => {
  const invalid = await mountLayout(t, { query: { organizationCode: 'OTHER' } })
  assert.deepEqual(invalid.events, ['ORG-A'])
  const empty = await mountLayout(t, { codes: [] })
  assert.deepEqual(empty.events, [])
  assert.match(text(empty.root), /当前账号没有可查看的机构/)
})

test('无匹配、清除、失败和加载状态保留共用结构，不伪装成当前目录为空', async t => {
  const { root, props, events } = await mountLayout(t, { keyword: '不存在' })
  assert.match(text(root), /没有符合查询条件的科室/)
  descendants(root).find(item => item.type === 'button' && text(item) === '清除').props.onClick()
  await flush()
  assert.equal(props.keyword, '')
  assert.deepEqual(events.slice(-2), ['clear', 'search'])
  assert.match(text(root), /当前机构没有科室有效数据/)
  props.hasExtraFilters = true
  await flush()
  assert.match(text(root), /没有符合查询条件的科室/)
  props.hasExtraFilters = false
  props.error = { message: '无权读取目录', requestId: 'test-request' }
  await flush()
  assert.match(text(root), /当前数据读取失败/)
  assert.doesNotMatch(text(root), /尚无当前有效数据|当前机构没有科室/)
  props.error = null
  props.isLoading = true
  await flush()
  assert.match(text(root), /正在读取当前有效数据/)
  assert.equal(descendants(root).filter(item => item.props.class === 'organization-rail').length, 1)
})

test('非空目录的表格和分页插槽只在可用状态展示', async t => {
  const { root, props } = await mountLayout(t, { isEmpty: false, total: 46, counts: { DEPARTMENT: 46 } })
  assert.match(text(root), /已有当前有效数据/)
  assert.match(text(root), /测试业务记录/)
  assert.match(text(root), /测试分页/)
  props.error = { message: '读取失败' }
  await flush()
  assert.doesNotMatch(text(root), /测试业务记录|测试分页/)
})

test('机构资料失败保留已授权编码且不伪造启用值，会话失效转登录', async t => {
  const unavailable = await mountLayout(t, { organizationFailure: 403 })
  assert.deepEqual(unavailable.events, ['ORG-A'])
  assert.match(text(unavailable.root), /机构资料暂不可用/)
  assert.doesNotMatch(text(unavailable.root), /已启用/)
  const expired = await mountLayout(t, { organizationFailure: 401 })
  assert.equal(expired.authState.user, null)
  assert.equal(expired.routeChanges[0].path, '/login')
  assert.deepEqual(expired.events, [])
})
