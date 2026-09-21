import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { hospitalDirectoryListPath } from '../src/api/master-data/directoryApiPaths.ts'

const pagePath = new URL(
  '../src/views/master-data/directory/HospitalDirectoryView.vue',
  import.meta.url,
)
const detailPath = new URL(
  '../src/views/master-data/batch/MasterDataBatchDetailView.vue',
  import.meta.url,
)

test('数据目录地址携带机构、类型、关键词和服务端分页', () => {
  const path = hospitalDirectoryListPath({
    organizationCode: 'ORG001',
    directoryType: 'DOCTOR',
    keyword: '内科',
    page: 2,
    pageSize: 20,
  })
  const url = new URL(path, 'https://platform.example')

  assert.equal(url.pathname, '/master-data/hospital-directory')
  assert.equal(url.searchParams.get('organizationCode'), 'ORG001')
  assert.equal(url.searchParams.get('directoryType'), 'DOCTOR')
  assert.equal(url.searchParams.get('keyword'), '内科')
  assert.equal(url.searchParams.get('page'), '2')
  assert.equal(url.searchParams.get('pageSize'), '20')
})

test('当前有效数据目录只读展示来源、当前数量和来源批次', async () => {
  const source = await readFile(pagePath, 'utf8')

  assert.match(source, /listHospitalDirectory/)
  assert.match(source, /当前医院综合目录/)
  assert.match(source, /当前有效数据/)
  assert.match(source, /latestBatchNo/)
  assert.match(source, /relationCount/)
  assert.match(source, /<AdminPagination/)
  assert.match(source, /text-overflow:ellipsis/)
  assert.doesNotMatch(source, /localStorage|模拟数据|编辑目录|删除目录/)
})

test('批次详情区分HIS返回行、展开重复、无效关系和主数据冲突', async () => {
  const source = await readFile(detailPath, 'utf8')

  assert.match(source, /HIS 返回行/)
  assert.match(source, /展开重复/)
  assert.match(source, /丢弃的无效关系/)
  assert.match(source, /主数据冲突/)
  assert.match(source, /\/master-data\/directory/)
  assert.doesNotMatch(source, /校验问题/)
})
