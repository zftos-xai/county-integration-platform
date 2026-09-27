import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('清空首页下钻列表筛选时同时移除地址栏筛选参数', async () => {
  const views = [
    'web-admin/src/views/system/organization/OrganizationView.vue',
    'web-admin/src/views/system/user/UserView.vue',
    'web-admin/src/views/system/role/RoleView.vue',
    'web-admin/src/views/configuration/parameter/ParameterView.vue',
  ]

  for (const path of views) {
    const source = await readFile(path, 'utf8')
    const reset = source.match(/function resetListFilters\(\) \{([\s\S]*?)\n\}/)?.[1] ?? ''

    assert.match(reset, /void router\.replace\(\{ path: route\.path \}\)/, `${path} 重置后仍会保留旧筛选参数`)
  }
})
