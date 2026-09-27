import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const pagePath = new URL('../src/views/prototype/Icd10DirectoryPrototypeView.vue', import.meta.url)
const directoryPagePath = new URL('../src/views/master-data/directory/Icd10DirectoryView.vue', import.meta.url)
const routerPath = new URL('../src/router/index.ts', import.meta.url)

test('ICD10原型明确为公共目录且不设置机构筛选', async () => {
  const source = await readFile(pagePath, 'utf8')

  assert.match(source, /平台公共目录/)
  assert.match(source, /不按机构归属或筛选/)
  assert.match(source, /机构归属<\/dt><dd>不适用/)
  assert.doesNotMatch(source, /平台机构/)
  assert.doesNotMatch(source, /organizationCode/)
})

test('ICD10原型在来源无版本时仅保留类别、查询和不落库的同步交互', async () => {
  const source = await readFile(pagePath, 'utf8')

  assert.match(source, /诊断类型/)
  assert.match(source, /来源未提供诊断版本/)
  assert.match(source, /诊断版本<\/dt><dd>来源未提供/)
  assert.doesNotMatch(source, /ICD-10（2019版）/)
  assert.doesNotMatch(source, /selectedVersion/)
  assert.doesNotMatch(source, /diagnosis-version-list/)
  assert.match(source, /新建同步/)
  assert.match(source, /确认原型操作/)
  assert.match(source, /不调用 HIS 或真实目录 API/)
})

test('ICD10正式目录挂入现有登录后布局并要求读取权限', async () => {
  const source = await readFile(routerPath, 'utf8')

  assert.match(source, /path: 'master-data\/directory\/icd10'/)
  assert.match(source, /component: Icd10DirectoryView/)
  assert.match(source, /requiredPermission: 'master-data:read'/)
  assert.match(source, /catalogScope: 'PUBLIC'/)
})

test('ICD10正式目录详情按三组字段排列且窄值可完整换行', async () => {
  const source = await readFile(directoryPagePath, 'utf8')

  assert.match(source, /<AdminTableFrame[^>]*label="公共诊断目录列表"/)
  assert.doesNotMatch(source, /scroll-hint|hint-breakpoint|窄屏显示名称和编码/)
  assert.match(source, /第 1 组：诊断类别与疾病编码/)
  assert.match(source, /第 2 组：助记码与来源创建时间/)
  assert.match(source, /第 3 组：来源疾病 ID 与最近同步批次/)
  assert.match(source, /\.icd10-detail \{ grid-template-columns:repeat\(3,minmax\(0,1fr\)\); \}/)
  assert.match(source, /\.icd10-detail section > dl > div \{ grid-template-columns:100px minmax\(0,1fr\); \}/)
  assert.match(source, /\.icd10-detail section > dl dt \{ min-width:0; white-space:nowrap; \}/)
  assert.match(source, /\.icd10-detail section > dl dd \{ min-width:0; white-space:normal; overflow-wrap:anywhere; \}/)
  assert.doesNotMatch(source, /<dt>备注<\/dt>/)
  assert.match(source, /\.icd10-table \.detail-row > td \{ height:auto; padding:12px 14px; text-align:left/)
  assert.match(source, /\.icd10-detail section\+section \{ border-left:1px dashed var\(--detail-line\); \}/)
  assert.match(source, /@media \(max-width:780px\).*\.icd10-detail \{ grid-template-columns:minmax\(0,1fr\); \}/)
  assert.match(source, /@media \(max-width: 1100px\)[\s\S]*?\.diagnosis-rail \{\s*position:static;\s*display:grid;\s*grid-template-columns:repeat\(2,minmax\(0,1fr\)\);\s*\}[\s\S]*?\.diagnosis-rail header \{ grid-column:1\/-1; \}/)
  assert.match(source, /<colgroup><col \/><col \/><col \/><col \/><col \/><\/colgroup>/)
  assert.match(source, /@media \(max-width:780px\)[\s\S]*?\.icd10-table \{ min-width:0; table-layout:fixed; \}[\s\S]*?\.icd10-table col:nth-child\(1\) \{ width:85%; \}[\s\S]*?\.icd10-table col:last-child \{ width:15%; \}[\s\S]*?\.icd10-table th:last-child \{ width:15%; \}[\s\S]*?\.icd10-table td:last-child \{ overflow:visible; text-overflow:clip; \}/)
  assert.match(source, /@media \(max-width:520px\)[\s\S]*?\.icd10-table col:nth-child\(1\),\.icd10-table th:nth-child\(1\) \{ width:76%; \}[\s\S]*?\.icd10-table col:last-child,\.icd10-table th:last-child \{ width:24%; \}/)
  assert.match(source, /<label class="prototype-search"><Search[^>]*><input v-model="keyword"/)
  assert.doesNotMatch(source, /icd10-panel :deep\(\.standard-list-toolbar/)
  assert.doesNotMatch(source, /\.icd10-search\s*\{/)
  assert.match(source, /@media \(max-width:780px\)\s*\{\s*\.icd10-detail \{ grid-template-columns:minmax\(0,1fr\); \}/)
})
