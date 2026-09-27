import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  emptyExternalEndpointForm, emptyExternalSystemForm, externalEndpointToForm,
  toCreateExternalEndpointInput, toUpdateExternalEndpointInput,
  validateExternalEndpointForm, validateExternalSystemForm,
} from '../src/views/configuration/external-system/form.ts'

test('外部系统表单限制稳定代码并规范化必填说明', () => {
  const form = emptyExternalSystemForm()
  form.systemCode = '1BAD'; form.systemName = '基层HIS'; form.description = '机构基础数据来源'
  assert.match(validateExternalSystemForm(form, true), /系统编码/)
  form.systemCode = 'PRIMARY_HIS'
  assert.equal(validateExternalSystemForm(form, true), null)
})

test('基层HIS接口配置必须选择机构并接受实际HTTP地址', () => {
  const form = emptyExternalEndpointForm(null)
  form.baseUrl = 'http://his.example.invalid/WebService.asmx'
  form.vendorCode = 'V01'
  form.authorizationCode = 'AUTH-008'
  assert.equal(validateExternalEndpointForm(form, null, true), '请选择适用机构')
  form.organizationId = 8
  assert.equal(validateExternalEndpointForm(form, null, true), null)
  assert.equal('organizationQueryName' in toCreateExternalEndpointInput(form), false)
})

test('修改接口配置时接入信息留空表示保留且请求携带并发版本', () => {
  const endpoint = {
    id: 3, externalSystemId: 1, environment: 'TEST', organizationId: 8, organizationCode: 'ORG008',
    baseUrl: 'http://his.example.invalid/WebService.asmx', connectTimeoutMs: 3000, readTimeoutMs: 15000,
    credentialConfigured: true, enabled: false,
    sourceOrganizationId: null, sourceOrganizationName: null, verificationStatus: 'NOT_VERIFIED',
    verifiedAt: null, verificationFailureSummary: null, createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00', version: 'AAAAAAAAAAE=',
  }
  const form = externalEndpointToForm(endpoint)
  assert.equal(form.authorizationCode, '')
  assert.equal(validateExternalEndpointForm(form, endpoint, true), null)
  assert.deepEqual(toUpdateExternalEndpointInput(form), {
    environment: 'TEST', organizationId: 8,
    baseUrl: endpoint.baseUrl, connectTimeoutMs: 3000, readTimeoutMs: 15000,
    authentication: null, enabled: false, version: 'AAAAAAAAAAE=',
  })
})

test('更换适用机构时必须填写新机构自己的接入信息', () => {
  const endpoint = {
    id: 3, externalSystemId: 1, environment: 'TEST', organizationId: 8, organizationCode: 'ORG008',
    baseUrl: 'http://his.example.invalid/WebService.asmx', connectTimeoutMs: 3000, readTimeoutMs: 15000,
    credentialConfigured: true, enabled: true,
    sourceOrganizationId: null, sourceOrganizationName: null, verificationStatus: 'NOT_VERIFIED',
    verifiedAt: null, verificationFailureSummary: null, createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00', version: 'AAAAAAAAAAE=',
  }
  const form = externalEndpointToForm(endpoint)
  form.organizationId = 9
  assert.match(validateExternalEndpointForm(form, endpoint, true) ?? '', /更换机构后/)
  form.vendorCode = 'V09'; form.authorizationCode = 'AUTH-009'; form.authenticationChanged = true
  assert.equal(validateExternalEndpointForm(form, endpoint, true), null)
})

test('接口地址兼容ASMX操作地址并拒绝其他查询参数', () => {
  const form = emptyExternalEndpointForm(8)
  form.baseUrl = 'http://his.example.invalid/WebService.asmx?op=PHIS_Interface'
  form.vendorCode = 'V01'; form.authorizationCode = 'AUTH-008'
  assert.equal(validateExternalEndpointForm(form, null, true), null)
  assert.equal(toCreateExternalEndpointInput(form).baseUrl,
    'http://his.example.invalid/WebService.asmx?op=PHIS_Interface')
  form.baseUrl = 'http://his.example.invalid/WebService.asmx?foo=bar'
  assert.match(validateExternalEndpointForm(form, null, true), /正确的 HIS 接口地址/)
  form.baseUrl = 'http://his.example.invalid/WebService.asmx'
  form.username = 'operator'
  assert.match(validateExternalEndpointForm(form, null, true), /HIS 用户名和 HIS 密码/)
})

test('外部系统页将机构编码、环境状态、地址及数据来源拆列并保持记录单行', async () => {
  const source = await readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8')
  assert.match(source, /机构接口配置表使用的稳定分组键/)
  assert.match(source, /<th>机构<\/th><th>机构编码<\/th><th>生产状态<\/th><th>生产接口地址<\/th><th>测试状态<\/th><th>测试接口地址<\/th>/)
  assert.match(source, /<th>接入信息<\/th><th>数据来源<\/th><th>最后更新<\/th>/)
  assert.match(source, /<td><span class="single-line" :title="group\.organizationCode">\{\{ group\.organizationCode \}\}<\/span><\/td>/)
  assert.match(source, /<td><span class="single-line" :title="primaryEndpoint\(group\)\?\.sourceOrganizationName \?\? '—'">/)
  assert.match(source, /\.single-line \{[^}]*text-overflow: ellipsis;[^}]*white-space: nowrap;/)
  assert.match(source, /displayServiceUrl\(group\.endpoints\.PRODUCTION\)/)
  assert.match(source, /await loadEndpoints\(system\)/)
})

test('机构接口字段都在主列表展示，不保留重复的展开详情', async () => {
  const source = await readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8')
  assert.doesNotMatch(source, /endpoint-detail|expandedOrganizationKey|toggleOrganization|<ChevronDown|<ChevronRight/)
  assert.match(source, /<tr v-for="group in pagedRows" :key="group\.key" class="organization-row">/)
  assert.match(source, /@click="openGroupEndpointEdit\(group\)"/)
})

test('外部系统页按数据阶段收敛空状态操作', async () => {
  const source = await readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8')
  assert.match(source, /v-else-if="!error && systems\.length === 0" class="prototype-section first-system-empty"/)
  assert.match(source, /<ListQueryToolbar v-if="!endpointError && endpointGroups\.length"/)
  assert.match(source, /<button v-if="canWrite && selectedSystem && endpointGroups\.length > 0" class="prototype-button"[^>]*>[^<]*<Plus[^>]*\/>新增机构接口配置/)
  assert.match(source, /v-else-if="selectedSystem && endpointGroups\.length === 0" class="endpoint-empty"/)
  assert.match(source, /<PageState v-if="filteredEndpointGroups\.length === 0" kind="empty" title="没有符合当前筛选条件的机构接口配置" compact/)
  assert.match(source, /<AdminTableFrame v-else label="外部系统端点配置矩阵"/)
  assert.doesNotMatch(source, /filteredEndpointGroups\.length === 0" class="prototype-empty"/)
  assert.match(source, /登记第一个外部系统/)
})

test('基层HIS新增接口配置只提供基层机构并默认选中首个可用机构', async () => {
  const source = await readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8')
  assert.match(source, /item\.enabled && item\.organizationType === 'PRIMARY_CARE'/)
  assert.match(source, /emptyExternalEndpointForm\(organizationId \?\? endpointOrganizationOptions\.value\[0\]\?\.id \?\? null\)/)
  assert.match(source, /:organizations="endpointOrganizationOptions"/)
})

test('机构接口搜索包含未建配置的可见机构并支持查询与重置', async () => {
  const source = await readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8')
  assert.match(source, /if \(selectedSystem\.value\?\.systemCode === 'PRIMARY_HIS'\) \{\s*for \(const organization of endpointOrganizationOptions\.value\)/)
  assert.match(source, /latestEndpoint: null/)
  assert.match(source, /@click="openEndpointCreate\(group\.organizationId\)"/)
  assert.match(source, /仅看配置缺项/)
  assert.match(source, /@query="applyFilters" @reset="resetFilters" @refresh="loadPage\(true\)"/)
  assert.match(source, /appliedFilters\.value = \{ query: '', endpointStatus: 'all', onlyIncomplete: false \}/)
  assert.doesNotMatch(source, /:summary=/)
})

test('外部系统与机构接口配置抽屉使用完整字段和固定操作区', async () => {
  const endpointDrawer = await readFile('web-admin/src/views/configuration/external-system/components/ExternalEndpointEditorDrawer.vue', 'utf8')
  const systemDrawer = await readFile('web-admin/src/views/configuration/external-system/components/ExternalSystemEditorDrawer.vue', 'utf8')
  assert.match(endpointDrawer, /class="authentication-section wide"/)
  assert.match(endpointDrawer, /<strong id="endpoint-authentication-title">填写 HIS 接入信息<\/strong>/)
  assert.match(endpointDrawer, /<strong id="endpoint-scope-title">选择机构和环境<\/strong>/)
  assert.match(endpointDrawer, /<strong id="endpoint-address-title">填写 HIS 接口地址<\/strong>/)
  assert.match(endpointDrawer, /<span>厂商编号 <em>必填<\/em><\/span>/)
  assert.match(endpointDrawer, /<span>HIS 验证码 <em>必填<\/em><\/span>/)
  assert.match(endpointDrawer, /\.authentication-status \{[^}]*white-space: nowrap; flex: none;/)
  assert.match(endpointDrawer, /@media\(max-width:620px\)\{[\s\S]*?\.credential-summary\{display:grid;grid-template-columns:20px minmax\(0,1fr\);align-items:start\}[\s\S]*?\.credential-summary \.work-quiet-button\{grid-column:2;margin:0;justify-self:start\}/)
  assert.doesNotMatch(endpointDrawer, /凭证引用|env:\/\//)
  assert.doesNotMatch(endpointDrawer, /setup-guide|配置步骤|1选择机构2填写地址3填写接入信息/)
  assert.doesNotMatch(endpointDrawer, /id="endpoint-(environment|organization)"[^>]*disabled/)
  assert.match(endpointDrawer, /查看或修改/)
  assert.match(endpointDrawer, /<template #footer>[\s\S]*保存配置[\s\S]*<\/template>/)
  assert.match(endpointDrawer, /import DrawerFrame from '@\/components\/DrawerFrame\.vue'/)
  assert.match(endpointDrawer, /<strong>保存后自动校验<\/strong>/)
  assert.doesNotMatch(endpointDrawer, /type="checkbox" role="switch"/)
  assert.doesNotMatch(endpointDrawer, /保存后的状态/)
  assert.match(endpointDrawer, /'保存配置'/)
  assert.doesNotMatch(endpointDrawer, /创建停用连接/)
  assert.doesNotMatch(endpointDrawer, /his_web_url|his_auth_code|PHIS_Interface|100-002|变量名|加密保存/)
  assert.doesNotMatch(endpointDrawer, /class="work-drawer-sub"/)
  assert.match(systemDrawer, /class="system-field" for="external-system-enabled"/)
  assert.match(systemDrawer, /<template #footer>[\s\S]*保存系统[\s\S]*<\/template>/)
  assert.match(systemDrawer, /import DrawerFrame from '@\/components\/DrawerFrame\.vue'/)
  assert.doesNotMatch(systemDrawer, /本次操作/)
})

test('县医院入站Key与HIS出站凭证分开维护且明文仅在当前抽屉展示', async () => {
  const [api, paths, systemPage, systemDrawer] = await Promise.all([
    readFile('web-admin/src/api/system/configuration.ts', 'utf8'),
    readFile('web-admin/src/api/system/configurationApiPaths.ts', 'utf8'),
    readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8'),
    readFile('web-admin/src/views/configuration/external-system/components/ExternalSystemEditorDrawer.vue', 'utf8'),
  ])

  assert.match(api, /inboundKeyConfigured: boolean/)
  assert.match(api, /cache: 'no-store'/)
  assert.match(api, /export function rotateExternalSystemInboundKey/)
  assert.match(paths, /externalSystemInboundKeyPath/)
  assert.match(systemPage, /const keyIsConfigured = system\.inboundKeyConfigured/)
  assert.match(systemPage, /title: keyIsConfigured \? '轮换外部系统调用 Key？' : '生成外部系统调用 Key？'/)
  assert.match(systemPage, /confirmLabel: keyIsConfigured \? '确认轮换' : '生成调用 Key'/)
  assert.match(systemPage, /轮换后，当前 Key 将立即失效/)
  assert.match(systemPage, /生成后县医院才能通过该 Key 识别平台调用方/)
  assert.match(systemPage, /issuedInboundKey\.value = null/)
  assert.match(systemDrawer, /县医院调用平台 Key/)
  assert.match(systemDrawer, /机构接口的出站凭证相互独立/)
  assert.match(systemDrawer, /:value="issuedInboundKey" readonly/)
  assert.doesNotMatch(systemPage + systemDrawer, /localStorage|sessionStorage/)
})

test('外部系统筛选区使用共享搜索优先网格且移除重复滚动说明', async () => {
  const [view, sharedStyles] = await Promise.all([
    readFile('web-admin/src/views/configuration/external-system/ExternalSystemView.vue', 'utf8'),
    readFile('web-admin/src/assets/styles/prototype.css', 'utf8'),
  ])

  assert.match(view, /class="standard-list-filter standard-list-filter--select"/)
  assert.match(view, /class="standard-list-filter standard-list-filter--check"/)
  assert.doesNotMatch(view, /\.connection-matrix :deep\(\.standard-list-toolbar__filters\)|\.status-filter|\.incomplete-filter/)
  assert.match(view, /<AdminTableFrame[^>]*label="外部系统端点配置矩阵"/)
  assert.doesNotMatch(view, /scroll-hint|hint-breakpoint|表格列较多，可左右滑动查看完整字段/)
  assert.doesNotMatch(sharedStyles, /standard-table-hint|standard-table-frame--hint-/)
  assert.match(sharedStyles, /\.standard-list-toolbar__commands \{ grid-column: 1 \/ -1; grid-row: 2;/)
  assert.match(sharedStyles, /\.standard-list-toolbar__filters \{[^}]*grid-template-columns: repeat\(auto-fill, minmax\(min\(240px, 100%\), 1fr\)\)/)
  assert.match(sharedStyles, /\.standard-list-toolbar \.prototype-search \{ justify-self: stretch; width: 100%; min-width: 0; max-width: none; \}/)
  assert.match(sharedStyles, /@container \(max-width: 560px\)[\s\S]*?\.standard-list-toolbar__filters \{ grid-template-columns: minmax\(0, 1fr\); \}/)
  assert.match(sharedStyles, /\.standard-list-filter--check \{ min-width: 0; white-space: normal; \}/)
})
