import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('审计记录用中文说明操作并明确只显示最近记录', async () => {
  const source = await readFile('web-admin/src/views/audit/management/AuditView.vue', 'utf8')
  assert.match(source, /auditActionLabel\(event\.actionCode\)/)
  assert.match(source, /加载更早记录/)
  assert.match(source, /formatLocalDateTime\(event\.occurredAt\)/)
  assert.match(source, /查看详情/)
})

test('用户和角色页面直接显示需要处理的授权问题', async () => {
  const [users, userEditor, roles] = await Promise.all([
    readFile('web-admin/src/views/system/user/UserView.vue', 'utf8'),
    readFile('web-admin/src/views/system/user/components/UserEditorDrawer.vue', 'utf8'),
    readFile('web-admin/src/views/system/role/RoleView.vue', 'utf8'),
  ])
  assert.match(users, /只看需要处理/)
  assert.match(users, /未分配角色/)
  assert.match(users, /主要机构已停用/)
  assert.match(userEditor, /待保存的角色变化/)
  assert.match(roles, /permissionSummary\(role\)/)
  assert.match(roles, /使用人数/)
  assert.match(roles, /user-assignment-known/)
})

test('配置、字典和机构页面提供业务筛选与影响说明', async () => {
  const [parameters, dictionaries, organizations] = await Promise.all([
    readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8'),
    readFile('web-admin/src/views/configuration/dictionary/DictionaryView.vue', 'utf8'),
    readFile('web-admin/src/views/system/organization/OrganizationView.vue', 'utf8'),
  ])
  assert.match(parameters, /尚未配置/)
  assert.match(parameters, /已设置（内容保密）/)
  assert.match(parameters, /当前参数修改尚未保存/)
  assert.match(dictionaries, /搜索字典项/)
  assert.match(dictionaries, /与其他字典项排序相同/)
  assert.match(dictionaries, /新的业务录入将不能再选择该项/)
  assert.match(organizations, /尚未生效/)
  assert.match(organizations, /organizationPath\(item\)/)
})

test('运行总览区分后台连接与业务待处理事项', async () => {
  const source = await readFile('web-admin/src/views/dashboard/DashboardView.vue', 'utf8')
  assert.match(source, /后台服务可连接/)
  assert.doesNotMatch(source, /平台运行正常/)
  assert.match(source, /用户需要处理/)
  assert.match(source, /参数尚未配置/)
})
