import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { medicalDirectoryListPath } from '../src/api/master-data/medicalDirectoryApiPaths.ts'

const pagePath = new URL(
  '../src/views/master-data/directory/MedicalDirectoryView.vue',
  import.meta.url,
)

test('医疗目录地址携带机构、目录类型、关键词和服务端分页', () => {
  const path = medicalDirectoryListPath({
    organizationCode: 'ORG001',
    directoryType: 'WESTERN_MEDICINE',
    keyword: '阿莫西林',
    page: 3,
    pageSize: 20,
  })
  const url = new URL(path, 'https://platform.example')

  assert.equal(url.pathname, '/master-data/medical-directory')
  assert.equal(url.searchParams.get('organizationCode'), 'ORG001')
  assert.equal(url.searchParams.get('directoryType'), 'WESTERN_MEDICINE')
  assert.equal(url.searchParams.get('keyword'), '阿莫西林')
  assert.equal(url.searchParams.get('page'), '3')
  assert.equal(url.searchParams.get('pageSize'), '20')
})

test('医疗目录只读展示当前数据、数量核对依据和来源批次', async () => {
  const source = await readFile(pagePath, 'utf8')

  assert.match(source, /listMedicalDirectory/)
  assert.match(source, /title="医疗目录"/)
  assert.match(source, /<DirectoryLayout/)
  assert.match(source, /同范围 100-005 已在同步时校对/)
  assert.match(source, /sourceOrganizationId/)
  assert.match(source, /latestBatchNo/)
  assert.match(source, /<AdminPagination/)
  assert.doesNotMatch(source, /localStorage|模拟数据|编辑目录|删除目录|人工复核/)
})
