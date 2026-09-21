import assert from 'node:assert/strict'
import test from 'node:test'
import {
  masterDataBatchCancelPath,
  masterDataBatchDetailPath,
  masterDataBatchDirectoryResultsPath,
  masterDataBatchRunPath,
  masterDataBatchListPath,
  masterDataSyncOptionsPath,
} from '../src/api/master-data/batchApiPaths.ts'

test('同步批次列表地址保留真实分页和可选业务范围', () => {
  const path = masterDataBatchListPath({
    organizationCode: '510724001',
    requestKey: '8BCDCA4E11A44A9D888D7E70',
    category: 'DEPARTMENT',
    status: 'COMPLETED',
    startedFrom: '2026-09-18T00:00:00+08:00',
    page: 1,
    pageSize: 20,
  })
  const url = new URL(path, 'https://platform.example')

  assert.equal(url.pathname, '/master-data/batches')
  assert.equal(url.searchParams.get('organizationCode'), '510724001')
  assert.equal(url.searchParams.get('requestKey'), '8BCDCA4E11A44A9D888D7E70')
  assert.equal(url.searchParams.get('category'), 'DEPARTMENT')
  assert.equal(url.searchParams.get('status'), 'COMPLETED')
  assert.equal(url.searchParams.get('page'), '1')
  assert.equal(url.searchParams.get('pageSize'), '20')
})

test('医院综合目录使用一个批次子资源完成取得、校验和直接对账', () => {
  assert.equal(masterDataBatchRunPath(27), '/master-data/batches/27/run')
})

test('取消尚未执行批次使用独立受控动作地址', () => {
  assert.equal(masterDataBatchCancelPath(27), '/master-data/batches/27/cancel')
})

test('同步批次详情地址只使用平台批次主键', () => {
  assert.equal(masterDataBatchDetailPath(27), '/master-data/batches/27')
})

test('100-003分项结果使用批次子资源地址', () => {
  assert.equal(
    masterDataBatchDirectoryResultsPath(27),
    '/master-data/batches/27/directory-results',
  )
})

test('同步来源和业务能力使用批次下的只读子资源', () => {
  assert.equal(masterDataSyncOptionsPath, '/master-data/batches/options')
})
