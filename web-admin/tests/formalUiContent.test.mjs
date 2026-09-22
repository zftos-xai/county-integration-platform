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

  assert.match(layout, /active-class="prototype-route-parent"\s+exact-active-class="router-link-active"/)
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

test('侧栏账号区展示真实角色与主机构名称，长名称保持在侧栏内', async () => {
  const layout = await readFile('web-admin/src/layout/AppLayout.vue', 'utf8')
  const styles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  const session = await readFile('web-admin/src/api/session.ts', 'utf8')

  assert.match(layout, /authState\.user\?\.roleNames\.join\('、'\)/)
  assert.match(layout, /\{\{ authState\.user\?\.organizationName \}\}/)
  assert.match(layout, /authState\.user\?\.loginName/)
  assert.match(session, /isStringArray\(value\.roleNames\)/)
  assert.match(session, /typeof value\.organizationName === 'string'/)
  assert.match(styles, /\.prototype-user \{[^}]*width: 100%;[^}]*min-width: 0;/)
  assert.match(styles, /\.prototype-user > span:last-child \{[^}]*flex: 1;[^}]*min-width: 0;[^}]*overflow: hidden;/)
  assert.match(styles, /\.prototype-user small \{[^}]*overflow: hidden;[^}]*text-overflow: ellipsis;[^}]*white-space: nowrap;/)
  assert.match(styles, /\.prototype-sidebar-foot \{[^}]*min-height: 72px;[^}]*padding: 9px 16px;/)
})

test('用户编辑按资料、访问权限和密码操作分区，登录页不暴露内部契约错误', async () => {
  const [users, editor, login] = await Promise.all([
    readFile('web-admin/src/views/system/user/UserView.vue', 'utf8'),
    readFile('web-admin/src/views/system/user/components/UserEditorDrawer.vue', 'utf8'),
    readFile('web-admin/src/views/login/LoginView.vue', 'utf8'),
  ])

  assert.match(users, /<th>姓名 \/ 登录名<\/th>/)
  assert.match(editor, /用户编辑区域/)
  assert.match(editor, /基础资料/)
  assert.match(editor, /访问权限/)
  assert.match(editor, /密码操作/)
  assert.match(editor, /保存数据范围/)
  assert.match(login, /登录服务暂时异常，请稍后重试。/)
  assert.doesNotMatch(login, /请求编号：\{\{ error\.requestId \}\}/)
})

test('用户列表将真实角色与机构范围分列并保持单行', async () => {
  const source = await readFile('web-admin/src/views/system/user/UserView.vue', 'utf8')

  assert.match(source, /<th title="角色决定功能权限，数据范围单独授权">权限角色<\/th>/)
  assert.match(source, /<th title="当前账号可访问的机构范围">数据范围<\/th>/)
  assert.match(source, /function roleSummary\(user: ManagedUser\)[\s\S]*roleById\.value\.get\(id\)\?\.roleName/)
  assert.match(source, /\{\{ scopeCountLabel\(user\) \}\}/)
  assert.match(source, /\.user-table \{[^}]*table-layout: fixed;/)
  assert.match(source, /\.user-table td \{[^}]*white-space: nowrap;/)
  assert.match(source, /\.user-cell-text \{[^}]*text-overflow: ellipsis;[^}]*white-space: nowrap;/)
  assert.doesNotMatch(source, /<small>\{\{ user\.loginName \}\}<\/small>/)
  assert.doesNotMatch(source, /<small>\{\{ user\.organizationCode \}\}<\/small>/)
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
  assert.match(dictionary, /--action-column-width:\s*132px/)
  assert.match(organization, /--action-column-width:\s*142px/)
  assert.doesNotMatch(organization, /确认撤销（/)
})

test('正式列表的操作列表头可见并与行内操作保持右对齐', async () => {
  const directoryStyles = await readFile('web-admin/src/views/master-data/directory/directory-records.css', 'utf8')
  const sharedStyles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')
  const batch = await readFile('web-admin/src/views/master-data/batch/MasterDataBatchView.vue', 'utf8')
  const external = await readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8')
  const medical = await readFile('web-admin/src/views/master-data/directory/MedicalDirectoryView.vue', 'utf8')
  const hospital = await readFile('web-admin/src/views/master-data/directory/HospitalDirectoryView.vue', 'utf8')

  for (const file of [
    'web-admin/src/views/configuration/dictionary/DictionaryView.vue',
    'web-admin/src/views/system/user/UserView.vue',
    'web-admin/src/views/system/role/RoleView.vue',
    'web-admin/src/views/system/organization/OrganizationView.vue',
  ]) {
    const source = await readFile(file, 'utf8')
    assert.match(source, /<th>操作<\/th>/, `${file} 应显示操作列表头`)
  }
  assert.match(medical, /<th scope="col">操作<\/th>/)
  assert.match(hospital, /<th>操作<\/th>/)
  assert.doesNotMatch(medical + hospital, /visually-hidden">操作/)
  assert.match(sharedStyles, /\.action-column-table th:last-child, \.action-column-table td:last-child:not\(\[colspan\]\)\s*\{[^}]*text-align: right/s)
  assert.match(sharedStyles, /\.action-column-table td:last-child:not\(\[colspan\]\) > \.prototype-icon,[\s\S]*?display: inline-grid; vertical-align: middle;/)
  assert.match(directoryStyles, /\.directory-table th:last-child,\.directory-table \.directory-actions\s*\{ text-align:right; \}/)
  assert.match(batch, /\.batch-table-section th:nth-child\(5\)\s*\{[^}]*text-align: right;/s)
  assert.match(external, /\.matrix-table th:last-child,\.matrix-table td:last-child\s*\{ text-align: right; \}/)
  const parameter = await readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8')
  assert.match(parameter, /\.parameter-actions\s*\{[^}]*justify-content: flex-end;/)
  for (const file of [
    'web-admin/src/views/configuration/dictionary/DictionaryView.vue',
    'web-admin/src/views/system/user/UserView.vue',
    'web-admin/src/views/system/role/RoleView.vue',
    'web-admin/src/views/system/organization/OrganizationView.vue',
  ]) {
    const source = await readFile(file, 'utf8')
    assert.doesNotMatch(source, /\.(?:dictionary-row-actions|user-actions|role-actions|row-actions)\s*\{\s*display:\s*flex/, `${file} 不应把单元格改成 flex`)
  }
})

test('弹窗卸载后再恢复触发控件焦点', async () => {
  const source = await readFile('web-admin/src/composables/useModalDialog.ts', 'utf8')

  assert.match(source, /onUnmounted\(\(\) =>/)
  assert.match(source, /queueMicrotask\(\(\) => target\?\.focus\(\)\)/)
  assert.doesNotMatch(source, /onBeforeUnmount/)
})
