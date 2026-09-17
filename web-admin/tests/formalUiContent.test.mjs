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
  'web-admin/src/views/error/AccessDeniedView.vue',
]

test('正式页面不显示开发、原型或实现阶段说明', async () => {
  const source = (await Promise.all(formalUiFiles.map(file => readFile(file, 'utf8')))).join('\n')
  const forbiddenCopy = [
    '真实数据', '当前角色', '真实服务', '接入后显示', '对应纵切',
    '原型合成', '原型数据', '工程结构已建立', '等待真实交换记录',
  ]

  for (const copy of forbiddenCopy) {
    assert.equal(source.includes(copy), false, `正式页面包含开发说明：${copy}`)
  }
})

test('配置保存成功后直接关闭编辑器而不受保存中保护拦截', async () => {
  const parameterSource = await readFile('web-admin/src/views/configuration/parameter/ParameterView.vue', 'utf8')
  const dictionarySource = await readFile('web-admin/src/views/configuration/dictionary/DictionaryView.vue', 'utf8')

  assert.match(parameterSource, /notice\.value = `\$\{definition\.name\}已保存。`\s+selectedDefinition\.value = null\s+selectedValue\.value = null/)
  assert.match(dictionarySource, /notice\.value = `\$\{updated\.itemLabel\}已保存。`\s+}\s+editorKind\.value = null/)
})
