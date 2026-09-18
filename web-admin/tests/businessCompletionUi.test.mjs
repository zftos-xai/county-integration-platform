import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const writeMenus = [
  ['机构管理', 'web-admin/src/views/system/organization/OrganizationView.vue', 'ORGANIZATION'],
  ['用户管理', 'web-admin/src/views/system/user/UserView.vue', 'USER'],
  ['角色权限', 'web-admin/src/views/system/role/RoleView.vue', 'ROLE'],
  ['参数配置', 'web-admin/src/views/configuration/parameter/ParameterView.vue', 'PARAMETER'],
  ['数据字典', 'web-admin/src/views/configuration/dictionary/DictionaryView.vue', 'DICTIONARY_'],
]

test('每个正式写菜单在成功后提供对应对象的审计追溯入口', async () => {
  for (const [label, path, targetType] of writeMenus) {
    const source = await readFile(path, 'utf8')
    assert.match(source, /AuditAwareSuccess/, `${label} 缺少统一成功提示`)
    assert.ok(source.includes(targetType), `${label} 缺少审计目标类型`)
  }
})

test('每个并发写菜单提供409恢复操作', async () => {
  const deliveryStatus = JSON.parse(await readFile('web-admin/menu-delivery-status.json', 'utf8'))
  for (const menu of deliveryStatus.menus.filter(item => item.writeCapable)) {
    const [page, editor] = await Promise.all([readFile(menu.page, 'utf8'), readFile(menu.editor, 'utf8')])
    assert.match(page, /@reload=/, `${menu.label} 页面缺少重新读取事件`)
    assert.match(editor, /status === 409/, `${menu.label} 编辑器缺少409恢复提示`)
    assert.match(editor, /emit\('reload'\)/, `${menu.label} 编辑器缺少重新读取动作`)
  }
})

test('审计成功入口携带目标条件且审计页从页面地址恢复筛选', async () => {
  const [success, audit] = await Promise.all([
    readFile('web-admin/src/components/AuditAwareSuccess.vue', 'utf8'),
    readFile('web-admin/src/views/audit/management/AuditView.vue', 'utf8'),
  ])
  assert.match(success, /query: \{ targetType: props\.targetType, targetId: props\.targetId \}/)
  assert.match(audit, /route\.query\.targetType/)
  assert.match(audit, /route\.query\.targetId/)
})

test('审计记录可按请求编号查询并返回对应业务页面', async () => {
  const [paths, audit] = await Promise.all([
    readFile('web-admin/src/api/system/auditApiPaths.ts', 'utf8'),
    readFile('web-admin/src/views/audit/management/AuditView.vue', 'utf8'),
  ])
  assert.match(paths, /query\.set\('requestId', requestId\)/)
  assert.match(audit, /v-model="requestId"/)
  assert.match(audit, /打开业务对象/)
  assert.match(audit, /DICTIONARY_ITEM/)
})

test('所有写页面对超时或断网提供先读取再决定是否重试的恢复流程', async () => {
  for (const [label, path] of writeMenus) {
    const source = await readFile(path, 'utf8')
    assert.match(source, /asUncertainWriteError/, `${label} 未区分结果不确定的写请求`)
  }
})

test('参数配置明确提供新增、修改和删除当前配置的操作', async () => {
  const [page, api, labels] = await Promise.all([
    readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8'),
    readFile('web-admin/src/api/system/configuration.ts', 'utf8'),
    readFile('web-admin/src/utils/managementDisplay.ts', 'utf8'),
  ])
  assert.match(page, /新增配置/)
  assert.match(page, /\? '修改' : '新增配置'/)
  assert.match(page, /删除后该范围会恢复为尚未配置/)
  assert.match(api, /deleteParameterValue/)
  assert.match(labels, /PARAMETER_DELETED: '删除参数配置'/)
})

test('数据字典提供类型和字典项删除入口并说明使用关系限制', async () => {
  const [page, api, labels] = await Promise.all([
    readFile('web-admin/src/views/configuration/dictionary/DictionaryView.vue', 'utf8'),
    readFile('web-admin/src/api/system/configuration.ts', 'utf8'),
    readFile('web-admin/src/utils/managementDisplay.ts', 'utf8'),
  ])
  assert.match(page, /删除类型/)
  assert.match(page, /已被使用时不会删除/)
  assert.match(api, /deleteDictionaryType/)
  assert.match(api, /deleteDictionaryItem/)
  assert.match(labels, /DICTIONARY_ITEM_DELETED: '删除字典项'/)
})

test('六个业务页明确体现各自停止使用规则和审计保留说明', async () => {
  const [audit, users, roles, parameters, dictionaries, organizations] = await Promise.all([
    readFile('web-admin/src/views/audit/management/AuditView.vue', 'utf8'),
    readFile('web-admin/src/views/system/user/UserView.vue', 'utf8'),
    readFile('web-admin/src/views/system/role/RoleView.vue', 'utf8'),
    readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8'),
    readFile('web-admin/src/views/configuration/dictionary/DictionaryView.vue', 'utf8'),
    readFile('web-admin/src/views/system/organization/OrganizationView.vue', 'utf8'),
  ])
  assert.match(audit, /不会自动清理/)
  assert.doesNotMatch(audit, /新增审计|修改审计|删除审计/)
  assert.match(users, /注销本人（立即退出）/)
  assert.match(roles, /先在用户管理中调整/)
  assert.match(parameters, /恢复为尚未配置/)
  assert.match(dictionaries, /后台会先检查业务数据引用/)
  assert.match(organizations, /历史数据保留/)
})
