import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const pagePath = new URL(
  '../src/views/master-data/batch/MasterDataBatchView.vue',
  import.meta.url,
);
const drawerPath = new URL(
  '../src/views/master-data/batch/components/StartBatchDrawer.vue',
  import.meta.url,
);
const detailPath = new URL(
  '../src/views/master-data/batch/MasterDataBatchDetailView.vue',
  import.meta.url,
);
const resultPanelPath = new URL(
  '../src/views/master-data/batch/components/DirectorySyncResultPanel.vue',
  import.meta.url,
);

test('同步批次正式页面只使用真实API并提供结果未知回读', async () => {
  const source = await readFile(pagePath, 'utf8');

  assert.match(source, /listMasterDataBatches/);
  assert.match(source, /getMasterDataBatch/);
  assert.match(source, /getHospitalDirectorySyncResults/);
  assert.match(source, /startMasterDataBatch/);
  assert.match(source, /requestKeyFilter/);
  assert.match(source, /不要重复提交/);
  assert.doesNotMatch(source, /prototypeData|localStorage/);
  assert.match(
    source,
    /const batches = ref<MasterDataBatchSummary\[\]>\(\[\]\)/,
  );
});

test('同步批次列表保持单行省略并使用服务端分页', async () => {
  const source = await readFile(pagePath, 'utf8');

  assert.match(source, /<AdminPagination/);
  assert.match(source, /text-overflow: ellipsis/);
  assert.match(source, /white-space: nowrap/);
  assert.match(source, /changePageSize/);
});

test('统一同步入口明确当前业务对象、执行步骤和直接对账边界', async () => {
  const source = await readFile(drawerPath, 'utf8');

  assert.match(source, /发起基础数据同步/);
  assert.match(source, /options\.businesses/);
  assert.match(source, /selectedBusiness\.tradeCode/);
  assert.match(source, /开始取得并校验/);
  assert.doesNotMatch(source, /当前阶段|保存后的结果|确认机构无误/);
  assert.match(source, /canRecoverResult/);
  assert.match(source, /读取现有批次/);
});

test('接口尚未就绪时不隐藏同步入口，并提供唯一配置入口', async () => {
  const page = await readFile(pagePath, 'utf8');
  const drawer = await readFile(drawerPath, 'utf8');

  assert.match(page, /当前没有可发起同步的机构/);
  assert.match(page, /查看接口配置/);
  assert.match(page, /@configure="openInterfaceConfiguration"/);
  assert.doesNotMatch(page, /:disabled="isSyncSourceLoading \|\| !hasSyncOption"/);
  assert.match(drawer, /当前还不能发起同步/);
  assert.match(drawer, /机构编码和名称只在“机构管理”维护/);
  assert.match(drawer, /emit\('configure'\)/);
});

test('医院综合目录在一次服务端操作中自动校验并直接对账，不提供人工发布按钮', async () => {
  const source = await readFile(pagePath, 'utf8');

  assert.match(source, /runHospitalDirectoryBatch/);
  assert.match(
    source,
    /startMasterDataBatch\(input\)[\s\S]*runHospitalDirectoryBatch/,
  );
  assert.match(source, /同步医院综合目录/);
  assert.doesNotMatch(source, /publishDepartmentBatch|发布正式版本|master-data:publish/);
  assert.match(source, /请勿直接重复操作/);
});

test('批次管理提供受控取消但不允许从失败记录直接重复提交', async () => {
  const source = await readFile(pagePath, 'utf8');

  assert.match(source, /cancelMasterDataBatch/);
  assert.match(source, /取消本批次/);
  assert.match(source, /不要重跑当前记录/);
  assert.doesNotMatch(source, /按原范围重新发起/);
  assert.match(source, /CANCELLED_BY_USER/);
  assert.doesNotMatch(source, /deleteMasterDataBatch|删除批次/);
});

test('同步记录详情呈现失败闭环，不暴露HIS原始错误或提供人工复核', async () => {
  const source = await readFile(detailPath, 'utf8');

  assert.match(source, /HIS 未授予目录数据权限/);
  assert.match(source, /一次自动取得科室、医生、病区和床位/);
  assert.match(source, /当前有效数据未改变/);
  assert.doesNotMatch(source, /batch\.failureSummary/);
  assert.doesNotMatch(source, /人工复核|人工核对|已核查确认|登记结论|发布为正式/);
});

test('同步结果按四类目录读取已落库事实，不以整批汇总推算明细', async () => {
  const detail = await readFile(detailPath, 'utf8');
  const panel = await readFile(resultPanelPath, 'utf8');

  assert.match(detail, /DirectorySyncResultPanel/);
  assert.match(detail, /getHospitalDirectorySyncResults/);
  assert.match(panel, /不能据此完成分项验收/);
});
