import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('机构、角色和用户状态变更统一通过共享确认对话框', async () => {
  const paths = [
    '../src/views/system/organization/OrganizationView.vue',
    '../src/views/system/role/RoleView.vue',
    '../src/views/system/user/UserView.vue',
  ]
  const [organization, role, user] = await Promise.all(paths.map(path => readFile(new URL(path, import.meta.url), 'utf8')))

  for (const source of [organization, role, user]) {
    assert.match(source, /async function changeStatus[\s\S]*?const confirmed = await confirm\(\{/)
    assert.match(source, /<ConfirmationDialog v-if="confirmationRequest"/)
    assert.doesNotMatch(source, /confirmStatusId/)
  }
  assert.match(organization, /撤销“\$\{item\.organizationName\}”前请确认影响/)
  assert.match(role, /停用角色“\$\{role\.roleName\}”后会影响/)
  assert.match(user, /注销当前账号后会立即退出登录/)
})
