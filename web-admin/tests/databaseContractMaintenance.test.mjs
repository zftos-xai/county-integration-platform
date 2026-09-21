import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const viewPath = new URL('../src/views/database-contract/DatabaseContractMaintenanceView.vue', import.meta.url)
const apiPath = new URL('../src/api/database-contract/maintenance.ts', import.meta.url)

test('正式数据库契约维护页接入真实扫描、方案、审批、执行复验和取消接口', async () => {
  const [view, api] = await Promise.all([readFile(viewPath, 'utf8'), readFile(apiPath, 'utf8')])

  assert.match(view, /inspectDatabaseContract/)
  assert.match(view, /createDatabaseContractPlan/)
  assert.match(view, /approveDatabaseContractPlan/)
  assert.match(view, /executeDatabaseContractPlan/)
  assert.match(view, /cancelDatabaseContractPlan/)
  assert.doesNotMatch(view, /prototypeData|localStorage/)
  assert.match(api, /\/database-contract\/plans\/\$\{id\}\/execute/)
})

test('DDL执行要求输入完整方案编号并明确事务失败自动回滚', async () => {
  const view = await readFile(viewPath, 'utf8')

  assert.match(view, /databaseContractExecutionConfirmationMatches/)
  assert.match(view, /任一DDL或复验失败都会整体回滚/)
  assert.match(view, /创建人不能审批自己的方案/)
  assert.match(view, /只取消未执行方案，不删除历史/)
})
