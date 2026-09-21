import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { scenarios } from '../src/views/prototype/model/prototypeData.ts'
import { createRecoveryTasks, recoveryBlock } from '../src/views/prototype/model/recoveryWorkflow.ts'

const scenario = id => scenarios.find(item => item.id === id)
const recovery = id => createRecoveryTasks().find(item => item.scenarioId === id)

test('S01 已受理后重启：恢复前必须检查，目标结果仍为尚未发送', () => {
  const record = scenario('DESIGN-04')
  const task = recovery('DESIGN-04')
  assert.equal(record.status, '已受理')
  assert.equal(task.target, '尚未发送')
  assert.match(recoveryBlock(task, 'resume', 'operator'), /先检查/)
})

test('S02 超时结果未知：没有明确目标结果时禁止恢复发送', () => {
  const record = scenario('DESIGN-01')
  const task = recovery('DESIGN-01')
  assert.equal(record.status, '结果未知')
  assert.equal(task.target, '无法确认')
  assert.ok(recoveryBlock(task, 'resume', 'operator'))
})

test('S03 报告版本变化：原请求和来源最新版本分别展示且禁止直接恢复', () => {
  const task = recovery('DESIGN-07')
  assert.equal(task.originalVersion, '1')
  assert.equal(task.latestVersion, '2')
  assert.ok(recoveryBlock(task, 'resume', 'operator'))
})

test('S04 正文清理后重复提交：保留原结果引用且不生成第二笔写入', () => {
  const record = scenario('DESIGN-06')
  assert.equal(record.savedContent, '已清理')
  assert.equal(record.status, '重复已识别')
  assert.match(record.receipt, /不生成第二笔写入/)
})

test('S05 来源系统停机：明确显示查询失败，不能解释成无报告', () => {
  const record = scenario('DESIGN-09')
  assert.equal(record.status, '查询失败')
  assert.match(record.receipt, /不能判断是否存在报告/)
  assert.match(record.receipt, /未提供离线缓存/)
})

test('S06 数据到期但任务未完成：先停止发送，再允许清理', () => {
  const task = recovery('DESIGN-08')
  assert.equal(task.dataStatus, '已到期')
  assert.equal(task.target, '无法确认')
  assert.match(recoveryBlock(task, 'clean', 'operator'), /先停止/)
})

test('S07 两端数量相同但版本不同：不能登记本次请求成功或未写入', () => {
  const record = scenario('DESIGN-13')
  const task = recovery('DESIGN-13')
  assert.equal(record.comparison.sourceVersion, '2')
  assert.equal(record.comparison.targetVersion, '1')
  assert.ok(recoveryBlock(task, 'written', 'operator'))
  assert.ok(recoveryBlock(task, 'not-written', 'operator'))
})

test('S08 恢复旧记录：自动发送暂停，必须先核查接收结果', () => {
  const record = scenario('DESIGN-10')
  const task = recovery('DESIGN-10')
  assert.match(record.receipt, /自动发送暂停/)
  assert.ok(recoveryBlock(task, 'resume', 'operator'))
})

test('S09 原版本不可取得：不能用最新版本替换原请求', () => {
  const record = scenario('DESIGN-11')
  const task = recovery('DESIGN-11')
  assert.equal(task.dataStatus, '原版本不可取得')
  assert.match(record.receipt, /停止原请求发送/)
  assert.ok(recoveryBlock(task, 'resume', 'operator'))
})

test('S10 越权访问：拒绝请求且不交换业务数据', () => {
  const record = scenario('DESIGN-03')
  assert.equal(record.status, '越权拒绝')
  assert.equal(record.patientRef, '未传递')
  assert.match(record.receipt, /未交换业务数据/)
})

test('S11 清理失败或与发送冲突：保留失败状态且不允许并发清理', () => {
  const task = recovery('DESIGN-12')
  assert.equal(task.cleanupFailed, true)
  assert.equal(task.dataStatus, '清理失败')
  assert.match(recoveryBlock({ ...task, activeSender: true }, 'clean', 'operator'), /正在发送/)
})

test('基础数据旧原型已撤下，避免把人工映射和人工核查误作业务流程', async () => {
  const view = await readFile('web-admin/src/views/prototype/PrototypeView.vue', 'utf8')
  const plan = await readFile('docs/plans/基础数据业务详细计划.md', 'utf8')

  assert.doesNotMatch(view, /foundation-mappings|foundation-issues|FoundationDataPanel|FoundationOperationsPanel/)
  assert.match(plan, /配置即归属，不另建映射/)
  assert.match(plan, /直接幂等更新平台当前目录/)
  assert.match(plan, /不以人工确认代替程序校验/)
})

test('侧栏导航：桌面密度、会话操作和收起状态使用完整布局规则', async () => {
  const view = await readFile('web-admin/src/views/prototype/PrototypeView.vue', 'utf8')
  const styles = await readFile('web-admin/src/assets/styles/prototype.css', 'utf8')

  assert.match(view, /class="prototype-session-actions"/)
  assert.match(view, /class="prototype-collapse-button"/)
  assert.match(styles, /grid-template-columns: 224px minmax\(0, 1fr\)/)
  assert.match(styles, /\.prototype-session-actions \{[^}]*display: grid/)
  assert.match(styles, /\.prototype-collapse-button \{[^}]*border: 1px solid/)
  assert.match(styles, /\.prototype-app\.sidebar-collapsed \{ grid-template-columns: 68px minmax\(0, 1fr\); \}/)
  assert.match(styles, /\.prototype-sidebar\.collapsed \.prototype-nav button > span/)
})
