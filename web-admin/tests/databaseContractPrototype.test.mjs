import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const viewPath = new URL('../src/views/prototype/DatabaseContractPrototypeView.vue', import.meta.url)
const routerPath = new URL('../src/router/index.ts', import.meta.url)

test('数据库结构页面只展示正常维护并与真实DDL隔离', async () => {
  const source = await readFile(viewPath, 'utf8')

  assert.match(source, /正常维护/)
  assert.match(source, /不会执行真实 DDL/)
  assert.match(source, /可以检查数据库实际结构与迁移脚本的差异，再按审批流程生成和执行维护方案/)
  assert.doesNotMatch(source, /两条独立控制线|启动阶段门禁|启动门禁已通过/)
})

test('数据库结构页面说明检查、审批和修改步骤', async () => {
  const source = await readFile(viewPath, 'utf8')

  for (const label of ['分析', '生成方案', '审批', '执行', '复验', '执行前取消']) {
    assert.match(source, new RegExp(label))
  }
  assert.match(source, /失败自动回滚/)
  assert.match(source, /成功后如需调整必须新建迁移/)
  assert.doesNotMatch(source, /生成补偿迁移/)
  assert.match(source, /改数据库/)
  assert.match(source, /改 Mapper \/ 模型/)
  assert.match(source, /人工判断/)
  assert.match(source, /迁移脚本规定/)
  assert.match(source, /实际检测/)
})

test('数据库结构维护示例保持公开隔离且正式页面受读取权限保护', async () => {
  const router = await readFile(routerPath, 'utf8')

  assert.match(router, /path:\s*'\/prototype\/database-contract',[\s\S]{0,160}?public:\s*true/)
  assert.match(router, /path:\s*'database-contract',[\s\S]{0,220}?public:\s*false[\s\S]{0,100}?requiredPermission:\s*'database-contract:read'/)
})
