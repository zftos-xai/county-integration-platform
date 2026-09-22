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
  assert.match(source, /<ListQueryToolbar v-if="endpointGroups\.length"/)
  assert.match(source, /v-else-if="selectedSystem && endpointGroups\.length === 0" class="endpoint-empty"/)
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
  assert.doesNotMatch(endpointDrawer, /凭证引用|env:\/\//)
  assert.doesNotMatch(endpointDrawer, /setup-guide|配置步骤|1选择机构2填写地址3填写接入信息/)
  assert.doesNotMatch(endpointDrawer, /id="endpoint-(environment|organization)"[^>]*disabled/)
  assert.match(endpointDrawer, /查看或修改/)
  assert.match(endpointDrawer, /class="endpoint-footer"/)
  assert.match(endpointDrawer, /<strong>保存后自动校验<\/strong>/)
  assert.doesNotMatch(endpointDrawer, /type="checkbox" role="switch"/)
  assert.doesNotMatch(endpointDrawer, /保存后的状态/)
  assert.match(endpointDrawer, /'保存配置'/)
  assert.doesNotMatch(endpointDrawer, /创建停用连接/)
  assert.doesNotMatch(endpointDrawer, /his_web_url|his_auth_code|PHIS_Interface|100-002|变量名|加密保存/)
  assert.doesNotMatch(endpointDrawer, /class="work-drawer-sub"/)
  assert.match(systemDrawer, /class="system-field" for="external-system-enabled"/)
  assert.match(systemDrawer, /class="system-footer"/)
  assert.doesNotMatch(systemDrawer, /本次操作/)
})
