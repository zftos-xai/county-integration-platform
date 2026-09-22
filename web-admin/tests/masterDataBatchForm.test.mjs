import assert from 'node:assert/strict'
import test from 'node:test'
import { emptyMasterDataBatchForm, previewFullSyncRange, toStartMasterDataBatchInput, validateMasterDataBatchForm } from '../src/views/master-data/batch/form.ts'

test('全量范围预览按北京时间显示服务端的20年窗口，不依赖浏览器时区', () => {
  const preview = previewFullSyncRange(new Date('2026-09-22T06:12:23Z'))
  assert.deepEqual(preview, { start: '2006-09-22T14:12', end: '2026-09-22T14:12' })
})

test('医疗目录医院时间带明确偏移，不能依赖浏览器本地时区', () => {
  const form = { ...emptyMasterDataBatchForm('ORG001'), category: 'MEDICAL_DIRECTORY', mode: 'TIME_RANGE', rangeStart: '2026-09-01T09:00', rangeEnd: '2026-09-22T09:00' }
  assert.equal(validateMasterDataBatchForm(form), null)
  const input = toStartMasterDataBatchInput(form)
  assert.equal(input.rangeStart, '2026-09-01T09:00+08:00')
  assert.equal(new Date(input.rangeStart).toISOString(), '2026-09-01T01:00:00.000Z')
})

test('切换回医院综合目录后不提交残留医疗目录时间条件', () => {
  const input = toStartMasterDataBatchInput({ ...emptyMasterDataBatchForm('ORG001'), rangeStart: '2026-09-01T09:00', rangeEnd: '2026-09-22T09:00' })
  assert.equal(input.rangeStart, null)
  assert.equal(input.rangeEnd, null)
})

test('非法时间在发送前拒绝，不能抛出ISO转换异常', () => {
  assert.equal(validateMasterDataBatchForm({ ...emptyMasterDataBatchForm('ORG001'), category: 'MEDICAL_DIRECTORY', mode: 'TIME_RANGE', rangeStart: 'bad-date', rangeEnd: '2026-09-22T09:00' }), '请填写有效的来源查询时间')
})

test('全量请求不夹带浏览器输入的时间范围', () => {
  const form = { ...emptyMasterDataBatchForm('ORG001'), category: 'MEDICAL_DIRECTORY', mode: 'FULL', rangeStart: '2026-09-01T09:00', rangeEnd: '2026-09-22T09:00' }
  assert.equal(validateMasterDataBatchForm(form), null)
  const input = toStartMasterDataBatchInput(form)
  assert.equal(input.mode, 'FULL')
  assert.equal(input.rangeStart, null)
  assert.equal(input.rangeEnd, null)
})
