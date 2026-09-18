import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const formalUiFiles = [
  'web-admin/src/layout/AppLayout.vue',
  'web-admin/src/views/dashboard/DashboardView.vue',
  'web-admin/src/views/error/PlaceholderView.vue',
  'web-admin/src/views/login/LoginView.vue',
  'web-admin/src/views/login/ChangePasswordView.vue',
  'web-admin/src/views/system/organization/OrganizationView.vue',
  'web-admin/src/views/system/user/UserView.vue',
  'web-admin/src/views/system/role/RoleView.vue',
  'web-admin/src/views/configuration/parameter/ParameterView.vue',
  'web-admin/src/views/configuration/dictionary/DictionaryView.vue',
  'web-admin/src/views/audit/management/AuditView.vue',
  'web-admin/src/views/error/AccessDeniedView.vue',
]

test('正式页面不显示开发、原型或实现阶段说明', async () => {
  const source = (await Promise.all(formalUiFiles.map(file => readFile(file, 'utf8')))).join('\n')
  const forbiddenCopy = [
    '真实数据', '当前角色', '真实服务', '接入后显示', '对应的完整功能',
    '原型合成', '原型数据', '工程结构已建立', '等待真实交换记录',
  ]

  for (const copy of forbiddenCopy) {
    assert.equal(source.includes(copy), false, `正式页面包含开发说明：${copy}`)
  }
})

test('配置保存成功后直接关闭编辑器而不受保存中保护拦截', async () => {
  const parameterSource = await readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8')
  const dictionarySource = await readFile('web-admin/src/views/configuration/dictionary/DictionaryView.vue', 'utf8')

  assert.match(parameterSource, /notice\.value = `\$\{definition\.name\}已保存。`[\s\S]*auditTarget\.value = \{ targetType: 'PARAMETER'[\s\S]*selectedDefinition\.value = null\s+selectedValue\.value = null/)
  assert.match(dictionarySource, /notice\.value = `\$\{updated\.itemLabel\}已保存。`; auditTarget\.value = \{ targetType: 'DICTIONARY_ITEM'[\s\S]*}\s+editorKind\.value = null/)
})

test('登录与强制改密页的品牌区在所有视口与表单对齐并保留间距', async () => {
  const source = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  const baseRule = source.match(/\.prototype-login-mobile-brand \{([^}]*)\}/)?.[1] ?? ''

  assert.match(baseRule, /width:\s*100%/)
  assert.match(baseRule, /max-width:\s*430px/)
  assert.match(baseRule, /margin-bottom:\s*18px/)
  assert.match(baseRule, /align-items:\s*center/)
  assert.match(baseRule, /gap:\s*9px/)
})

test('桌面登录页主标题显式覆盖Tabler标题色并保持深色背景可读', async () => {
  const source = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  const headingRule = source.match(/\.prototype-login-intro h1 \{([^}]*)\}/)?.[1] ?? ''

  assert.match(headingRule, /color:\s*#eef4f3/)
})

test('参数页仅在存在机构级参数时读取机构选项', async () => {
  const source = await readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8')

  assert.match(source, /definitionRows\.some\(definition => definition\.organizationScoped\)/)
  assert.match(source, /needsOrganizationOptions && hasPermission\('organization:read'\)/)
  assert.doesNotMatch(source, /Promise<Organization\[\]>/)
})

test('正式管理端采用参考图的宽侧栏、三层页面标题和全局底部信息', async () => {
  const layout = await readFile('web-admin/src/layout/AppLayout.vue', 'utf8')
  const styles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(layout, /active-class="prototype-route-parent" exact-active-class="router-link-active"/)
  assert.match(layout, /prototype-breadcrumb[\s\S]*pageContext\.section[\s\S]*route\.meta\.title[\s\S]*<h1>\{\{ route\.meta\.title \}\}<\/h1>/)
  assert.match(layout, /prototype-main-footer/)
  assert.match(layout, /prototype-product-meta/)
  assert.doesNotMatch(layout, /prototype-collapse-button|sidebar-collapsed/)
  assert.match(styles, /grid-template-columns:\s*280px minmax\(0, 1fr\)/)
  assert.match(styles, /\.prototype-brand \{[^}]*min-height:\s*96px/)
  assert.match(styles, /\.prototype-topbar \{[^}]*min-height:\s*136px/)
  assert.match(styles, /\.prototype-heading h1 \{[^}]*font-size:\s*28px/)
  assert.match(styles, /\.prototype-sidebar-foot \{[^}]*min-height:\s*82px/)
  assert.match(styles, /\.prototype-main-footer \{[^}]*min-height:\s*56px/)
})

test('窄屏导航使用完整抽屉并把关闭按钮固定在品牌区内部', async () => {
  const layout = await readFile('web-admin/src/layout/AppLayout.vue', 'utf8')
  const styles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(layout, /class="prototype-brand-copy"/)
  assert.match(styles, /width:\s*min\(320px, calc\(100vw - 32px\)\)/)
  assert.match(styles, /transform:\s*translateX\(-100%\)/)
  assert.match(styles, /\.prototype-brand-copy \{[^}]*min-width:\s*0;[^}]*overflow:\s*hidden;/)
  assert.match(styles, /\.prototype-close \{[^}]*position:\s*absolute;[^}]*right:\s*18px;[^}]*transform:\s*translateY\(-50%\)/)
})

test('正式列表统一提供分页并在最小桌面宽度固定操作列', async () => {
  const listFiles = [
    'web-admin/src/views/system/user/UserView.vue',
    'web-admin/src/views/system/role/RoleView.vue',
    'web-admin/src/views/system/organization/OrganizationView.vue',
    'web-admin/src/views/configuration/parameter/ParameterView.vue',
    'web-admin/src/views/configuration/dictionary/DictionaryView.vue',
  ]

  for (const file of listFiles) {
    const source = await readFile(file, 'utf8')
    assert.match(source, /AdminPagination/, `${file} 缺少统一分页组件`)
    assert.match(source, /action-column-table/, `${file} 缺少固定操作列边界`)
  }
})

test('正式列表的操作列保持紧凑且危险操作使用统一按钮样式', async () => {
  const styles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  const dictionary = await readFile('web-admin/src/views/configuration/dictionary/DictionaryView.vue', 'utf8')
  const organization = await readFile('web-admin/src/views/system/organization/OrganizationView.vue', 'utf8')

  assert.match(styles, /width:\s*var\(--action-column-width, 128px\)/)
  assert.match(styles, /max-width:\s*var\(--action-column-width, 128px\)/)
  assert.match(styles, /\.work-danger-button \{[^}]*border:\s*1px solid #e2c0ba/)
  assert.match(dictionary, /grid-template-columns:\s*clamp\(270px, 22vw, 330px\) minmax\(0, 1fr\)/)
  assert.match(dictionary, /--action-column-width:\s*112px/)
  assert.match(organization, /--action-column-width:\s*142px/)
  assert.doesNotMatch(organization, /确认撤销（/)
})

test('弹窗卸载后再恢复触发控件焦点', async () => {
  const source = await readFile('web-admin/src/composables/useModalDialog.ts', 'utf8')

  assert.match(source, /onUnmounted\(\(\) =>/)
  assert.match(source, /queueMicrotask\(\(\) => target\?\.focus\(\)\)/)
  assert.doesNotMatch(source, /onBeforeUnmount/)
})
