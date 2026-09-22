import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const pagePath = new URL('../src/views/prototype/Icd10DirectoryPrototypeView.vue', import.meta.url)
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

test('ICD10原型挂入现有登录后布局但不进入正式导航', async () => {
  const source = await readFile(routerPath, 'utf8')

  assert.match(source, /path: 'master-data\/directory\/icd10'/)
  assert.match(source, /prototypeOnly: true/)
  assert.match(source, /catalogScope: 'PUBLIC'/)
})
