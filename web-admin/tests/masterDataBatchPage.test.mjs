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
const medicalResultPanelPath = new URL(
  '../src/views/master-data/batch/components/MedicalDirectorySyncResultPanel.vue',
  import.meta.url,
);
const batchTimePath = new URL(
  '../src/views/master-data/batch/batchTime.ts',
  import.meta.url,
);
const layoutPath = new URL('../src/layout/AppLayout.vue', import.meta.url);
const layoutStylePath = new URL('../src/assets/styles/prototype.css', import.meta.url);
const detailGridStylePath = new URL('../src/views/master-data/batch/batch-detail-grid.css', import.meta.url);

test('批次详情顶部只保留一个页面标题，完成状态与批次信息并排展示', async () => {
  const [layout, detail, styles] = await Promise.all([
    readFile(layoutPath, 'utf8'),
    readFile(detailPath, 'utf8'),
    readFile(layoutStylePath, 'utf8'),
  ]);
  assert.match(layout, /'batch-detail-page': route\.path\.startsWith\('\/master-data\/batches\/'\)/);
  assert.match(detail, /<header class="record-heading">/);
  assert.match(detail, /class="record-complete" role="status"/);
  assert.doesNotMatch(detail, /<h2>同步结果<\/h2>|<section v-if="batch\.status === 'COMPLETED'" class="record-alert success">/);
  assert.match(styles, /\.prototype-main\.batch-detail-page \.prototype-heading > p \{ display: none; \}/);
});

test('批次字段表使用独立的虚线栅格，不继承全局详情表留白', async () => {
  const [detail, panel, styles] = await Promise.all([
    readFile(detailPath, 'utf8'),
    readFile(medicalResultPanelPath, 'utf8'),
    readFile(detailGridStylePath, 'utf8'),
  ]);
  assert.match(detail, /class="batch-detail-grid record-flow"/);
  assert.match(detail, /class="batch-detail-grid record-counts"/);
  assert.match(panel, /class="batch-detail-grid result-card-fields"/);
  assert.match(styles, /padding: 4px 7px;/);
  assert.doesNotMatch(styles, /(^|\n)\.detail-grid\s*\{/);
});

test('医疗目录分项结果使用完整业务标签并显示批次起止与运行时长', async () => {
  const [page, detail, panel, time] = await Promise.all([
    readFile(pagePath, 'utf8'),
    readFile(detailPath, 'utf8'),
    readFile(medicalResultPanelPath, 'utf8'),
    readFile(batchTimePath, 'utf8'),
  ]);
  assert.match(panel, /中药、西药、诊疗与耗材同步结果/);
  assert.match(panel, /HIS 统计/);
  assert.match(panel, /实际取得/);
  assert.match(panel, /未变化/);
  assert.doesNotMatch(panel, /声明 \/ 返回|每类目录先由 100-005/);
  for (const source of [page, detail]) {
    assert.match(source, /开始时间/);
    assert.match(source, /结束时间/);
    assert.match(source, /总耗时/);
    assert.match(source, /已运行/);
    assert.doesNotMatch(source, /接口环境 \/ 交易/);
  }
  assert.match(time, /second: '2-digit'/);
  assert.match(time, /batch\.finishedAt \? new Date\(batch\.finishedAt\)\.getTime\(\) : now/);
});

test('中断批次收尾要求权限、明确确认与当前版本，失败不自动重试', async () => {
  const source = await readFile(detailPath, 'utf8');
  assert.match(source, /hasPermission\('master-data:sync'\)/);
  assert.match(source, /confirmedRecovery/);
  assert.match(source, /recoverMasterDataBatch\(current.id, current.version\)/);
  assert.match(source, /loading.value \|\| recovering.value/);
  assert.match(source, /不要直接重复提交/);
  assert.doesNotMatch(source, /runMasterDataBatch\(/);
});

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
  assert.match(source, /<th>批次号 \/ 开始时间<\/th>[\s\S]*?<th>同步对象<\/th>[\s\S]*?<th>取得结果<\/th>[\s\S]*?<th>处理结果<\/th>[\s\S]*?<th>操作<\/th>/);
  assert.match(source, /item\.startedAt \? `开始 \$\{formatTime\(item\.startedAt\)\}` : '尚未开始'/);
  assert.match(source, /<strong>\{\{ resultCountLabel\(item\) \}\}<\/strong>/);
  assert.match(source, /<td>\s*<div class="batch-row-actions">/);
  assert.doesNotMatch(source, /batch-table-section action-column-table|由系统自动校验；未完成类型不改变当前数据/);
});

test('统一同步入口明确当前业务对象和直接对账边界', async () => {
  const source = await readFile(drawerPath, 'utf8');

  assert.match(source, /发起基础数据同步/);
  assert.match(source, /options\.businesses/);
  assert.match(source, /selectedBusiness\.tradeCode/);
  assert.match(source, /开始取得并校验/);
  assert.doesNotMatch(source, /当前阶段|保存后的结果|确认机构无误/);
  assert.match(source, /canRecoverResult/);
  assert.match(source, /读取现有批次/);
});

test('统一入口保留全量选择，并只读预览服务端将冻结的时间范围', async () => {
  const source = await readFile(drawerPath, 'utf8');
  const page = await readFile(pagePath, 'utf8');

  assert.match(source, /<fieldset v-if="selectedBusiness\?\.requiresTimeRange" class="batch-mode-field">/);
  assert.match(source, /<legend>本次同步方式<\/legend>/);
  assert.match(source, /type="radio" value="FULL"/);
  assert.match(source, /<span>全量同步<\/span>/);
  assert.match(source, /:value="fullRangePreview\.start" type="datetime-local" readonly/);
  assert.match(source, /:value="fullRangePreview\.end" type="datetime-local" readonly/);
  assert.match(source, /以上为预览，实际范围以创建后的批次记录为准/);
  assert.match(source, /:disabled="isSaving"/);
  assert.doesNotMatch(source, /fullSyncAvailable|fullSyncEnvironments/);
  assert.match(page, /@click="openCreator\(\)"/);
  assert.match(page, /form\.value\.mode = form\.value\.category === 'MEDICAL_DIRECTORY' \? 'TIME_RANGE' : 'NOT_APPLICABLE'/);
  assert.doesNotMatch(page, /发起医疗目录全量同步|openCreator\('FULL'\)/);
  assert.match(source, /\.batch-start-form \.batch-mode-option \{[^}]*display: flex; align-items: center;/);
  assert.match(source, /\.batch-start-form input\[type="datetime-local"\]/);
  assert.doesNotMatch(source, /\.batch-range-field input \{/);
});

test('同步弹窗以简明分区组织字段，说明紧邻对应控件', async () => {
  const source = await readFile(drawerPath, 'utf8');

  assert.match(source, /class="batch-start-form"/);
  assert.match(source, /<span>HIS 来源机构<\/span>/);
  assert.match(source, /class="batch-setup-grid"/);
  assert.match(source, /class="batch-range-grid"[\s\S]*?<small v-else>数量核对与目录查询使用同一范围/);
  assert.doesNotMatch(source, /work-form batch-start-form|batch-source-heading|batch-settings-heading/);
  assert.doesNotMatch(source, /class="batch-section-heading"|<span>1<\/span>|<span>2<\/span>/);
  assert.doesNotMatch(source, /class="batch-confirmation"/);
});

test('接口尚未就绪时不隐藏同步入口，并提供唯一配置入口', async () => {
  const page = await readFile(pagePath, 'utf8');
  const drawer = await readFile(drawerPath, 'utf8');

  assert.match(page, /未找到当前账号可用的同步来源/);
  assert.match(page, /查看接口配置/);
  assert.match(page, /@configure="openInterfaceConfiguration"/);
  assert.doesNotMatch(page, /:disabled="isSyncSourceLoading \|\| !hasSyncOption"/);
  assert.match(drawer, /当前还不能发起同步/);
  assert.match(drawer, /机构编码和名称只在“机构管理”维护/);
  assert.match(drawer, /emit\('configure'\)/);
});

test('同步来源读取失败不伪装成无可用机构，批次读取失败不显示零条或旧列表', async () => {
  const [page, drawer] = await Promise.all([
    readFile(pagePath, 'utf8'),
    readFile(drawerPath, 'utf8'),
  ]);
  assert.match(page, /syncSourceError\.value = apiError/);
  assert.match(page, /syncSourceController\?\.abort\(\)/);
  assert.match(page, /getMasterDataSyncOptions\(controller\.signal\)/);
  assert.match(page, /hasLoadedSyncSources\.value = true/);
  assert.match(page, /canStart && !error && hasLoadedSyncSources && !hasSyncSource/);
  assert.match(page, /hasLoadedSyncSources && hasSyncSource && !hasSyncOption/);
  assert.match(page, /canStart && !error && !isSyncSourceLoading && syncSourceError/);
  assert.match(page, /:summary="!isLoading && !error \? `共 \$\{total\} 个批次` : ''"/);
  assert.match(page, /v-else-if="!error && batches\.length"/);
  assert.match(page, /同步批次读取失败/);
  assert.match(drawer, /v-else-if="sourceError"/);
  assert.match(drawer, /emit\('retrySources'\)/);
  assert.match(drawer, /v-else-if="options\.sources\.length === 0"/);
  assert.match(drawer, /v-else-if="options\.businesses\.length === 0"/);
});

test('已接入目录业务在一次服务端操作中自动校验并直接对账，不提供人工发布按钮', async () => {
  const source = await readFile(pagePath, 'utf8');

  assert.match(source, /runMasterDataBatch/);
  assert.match(
    source,
    /startMasterDataBatch\(input\)[\s\S]*runMasterDataBatch/,
  );
  assert.match(source, /masterDataCategoryLabels\[selected\.category\]/);
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

test('同步记录详情按数据集呈现失败闭环，不暴露HIS原始错误或提供人工复核', async () => {
  const source = await readFile(detailPath, 'utf8');

  assert.match(source, /HIS 拒绝目录查询/);
  assert.doesNotMatch(source, /HIS 未授予|等待接口授权/);
  assert.match(source, /中药、西药、诊疗、耗材/);
  assert.match(source, /getMedicalDirectorySyncResults/);
  assert.match(source, /query: batch.value\?\.organizationCode \? \{ organizationCode: batch.value.organizationCode \}/);
  assert.match(source, /当前有效数据未改变/);
  assert.doesNotMatch(source, /batch\.failureSummary/);
  assert.doesNotMatch(source, /人工复核|人工核对|已核查确认|登记结论|发布为正式/);
});

test('全失败不显示部分成功，长同步超时只读进度不重发运行', async () => {
  const source = await readFile(pagePath, 'utf8');
  assert.doesNotMatch(source, /\[updated.category\]\}部分未完成/);
  assert.match(source, /latest.status !== 'FAILED'/);
  assert.match(source, /latest.status === 'FETCHING'/);
  assert.match(source, /detailPollTimer = setTimeout/);
  assert.match(source, /clearTimeout\(detailPollTimer\)/);
  assert.match(source, /item.status === 'FETCHING' && latest.status !== 'FETCHING'/);
});

test('同步结果按四类目录读取已落库事实，不以整批汇总推算明细', async () => {
  const detail = await readFile(detailPath, 'utf8');
  const panel = await readFile(resultPanelPath, 'utf8');

  assert.match(detail, /DirectorySyncResultPanel/);
  assert.match(detail, /getHospitalDirectorySyncResults/);
  assert.match(panel, /不能据此完成分项验收/);
});
