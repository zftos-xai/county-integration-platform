import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { medicalDirectoryListPath } from '../src/api/master-data/medicalDirectoryApiPaths.ts'

const pagePath = new URL(
  '../src/views/master-data/directory/MedicalDirectoryView.vue',
  import.meta.url,
)
const apiPath = new URL('../src/api/master-data/medicalDirectory.ts', import.meta.url)

test('医疗目录地址携带机构、目录类型、HIS启用值和服务端分页', () => {
  const path = medicalDirectoryListPath({
    organizationCode: 'ORG001',
    directoryType: 'WESTERN_MEDICINE',
    keyword: '阿莫西林',
    sourceEnabledFlag: '是',
    page: 3,
    pageSize: 20,
  })
  const url = new URL(path, 'https://platform.example')

  assert.equal(url.pathname, '/master-data/medical-directory')
  assert.equal(url.searchParams.get('organizationCode'), 'ORG001')
  assert.equal(url.searchParams.get('directoryType'), 'WESTERN_MEDICINE')
  assert.equal(url.searchParams.get('keyword'), '阿莫西林')
  assert.equal(url.searchParams.get('sourceEnabledFlag'), '是')
  assert.equal(url.searchParams.has('region'), false)
  assert.equal(url.searchParams.get('page'), '3')
  assert.equal(url.searchParams.get('pageSize'), '20')
})

test('医疗目录列表展示创建与记录时间及HIS启用值，仅以固定选项筛选启用原值', async () => {
  const page = await readFile(pagePath, 'utf8')
  assert.match(page, /<th scope="col">创建时间 \/ 记录时间<\/th>/)
  assert.match(page, /item\.sourceCreatedAt/)
  assert.match(page, /formatTime\(item\.firstSeenAt\)/)
  assert.doesNotMatch(page, /<th scope="col">地区<\/th>/)
  assert.match(page, /<th scope="col">HIS 启用值<\/th>/)
  assert.match(page, /<td :title="item\.sourceEnabledFlag">/)
  assert.match(page, /#filters/)
  assert.doesNotMatch(page, /按地区筛选|region: region\.value/)
  assert.match(page, /<select v-model="sourceEnabledFlag" aria-label="按 HIS 启用值筛选">/)
  assert.match(page, /<option value="是">HIS 启用值：是<\/option>/)
  assert.match(page, /<option value="否">HIS 启用值：否<\/option>/)
  assert.match(page, /sourceEnabledFlag: sourceEnabledFlag\.value \|\| undefined/)
  assert.match(page, /@clear="clearExtraFilters"/)
  assert.match(page, /colspan="7"/)
})

test('今日新增来自服务端首次记录事实，分类保留每日提示，记录仅以浅色区分', async () => {
  const api = await readFile(apiPath, 'utf8')
  const page = await readFile(pagePath, 'utf8')
  const layout = await readFile(new URL('../src/views/master-data/directory/DirectoryLayout.vue', import.meta.url), 'utf8')
  assert.match(api, /todayNewCount: number/)
  assert.match(api, /newToday: boolean/)
  assert.match(page, /todayNewCounts\.value\[count\.directoryType\] = count\.todayNewCount/)
  assert.match(page, /'is-today-new': todayMarkersAreCurrent && item\.newToday/)
  assert.match(page, /\.directory-table tr\.is-today-new:not\(\.expanded\) > td \{ background: #f6faf8; \}/)
  assert.doesNotMatch(page, /directory-row-new|>新<\/span>/)
  assert.match(page, /:today-new-counts="visibleTodayNewCounts"/)
  assert.match(page, /loadedBeijingDay\.value === displayedBeijingDay\.value/)
  assert.match(page, /document\.addEventListener\('visibilitychange', checkVisibleDay\)/)
  assert.match(page, /scheduleDayRollover\(\)/)
  assert.match(layout, /v-if="!isLoading && !error && todayNewCounts\?\.\[type\]" class="directory-new-mark"/)
  assert.match(layout, /今日新增 \$\{todayNewCounts\[type\]\} 条/)
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

test('100-004 可选字段经接口校验后可在展开详情逐项核对', async () => {
  const api = await readFile(apiPath, 'utf8')
  const page = await readFile(pagePath, 'utf8')
  for (const field of [
    'dosageForm', 'remark', 'packageUnit', 'conversionFactor', 'approvalNumber',
    'standardCode', 'packageMaterial', 'processingMethod', 'region', 'category',
  ]) {
    assert.match(api, new RegExp(`typeof value\\.${field} === 'string'`), `${field} 必须由接口校验`)
    assert.match(page, new RegExp(`selectedItem\\.${field}`), `${field} 必须可在详情核对`)
  }
  for (const field of ['dosageForm', 'packageUnit', 'conversionFactor']) {
    assert.match(page, new RegExp(`item\\.${field}`), `${field} 应在列表直接呈现`)
  }
  assert.doesNotMatch(page, /100-004 来源字段|HIS 未返回该字段/)
})

test('展开详情将短值放左列、长文本放右列且保留全部来源字段', async () => {
  const page = await readFile(pagePath, 'utf8')
  const fields = page.match(/aria-label="医疗目录详情与来源追溯"[^>]*>[\s\S]*?<section>\s*<dl>([\s\S]*?)<\/dl>/)?.[1]
  assert.ok(fields)
  const labels = [...fields.matchAll(/<dt>([^<]+)<\/dt>/g)].map((match) => match[1])
  assert.deepEqual(labels, [
    '目录类型', '目录编码', '助记码', '目录名称', '目录类别名称', '生产厂家',
    '规格', '来源创建时间', '单位', '国药准字号', '剂型', '药品本位码',
    '包装单位', '炮制方法', '转换系数', '包装材质', 'HIS 启用值', '地区',
    '来源类别', '备注',
  ])
})

test('列表将助记码放在厂家列，双击数据行可切换详情且不劫持按钮或链接', async () => {
  const page = await readFile(pagePath, 'utf8')
  assert.match(page, /<th scope="col">厂家 \/ 助记码<\/th>/)
  assert.match(page, /<strong v-else-if="item\.mnemonicCode">助记码/)
  assert.match(page, /<small v-if="item\.manufacturerName && item\.mnemonicCode">助记码/)
  const manufacturerCell = page.split('<strong v-if="item.manufacturerName">')[1]?.split('</td>')[0]
  assert.ok(manufacturerCell)
  assert.equal((manufacturerCell.match(/<small /g) ?? []).length, 1, '厂家列仅保留厂家与助记码两行')
  for (const field of ['approvalNumber', 'standardCode', 'packageMaterial', 'category']) {
    assert.ok(!manufacturerCell.includes(`item.${field}`), `${field} 应仅在展开详情展示`)
  }
  assert.match(page, /@dblclick="toggleDetailFromRow\(item\.id, \$event\)"/)
  assert.match(page, /event\.target\.closest\('a, button'\)/)
  assert.match(page, /function toggleDetailFromRow[\s\S]*?toggleDetail\(id\)/)
  assert.match(page, /@click="toggleDetail\(item\.id\)"/)
})
