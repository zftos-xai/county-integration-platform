<!-- 同步批次正式页面：使用真实API查询、创建并重新读取基础数据同步批次。 -->
<script setup lang="ts">
import {
  AlertCircle,
  ChevronRight,
  Database,
  LoaderCircle,
  Plus,
  RefreshCw,
  Search,
  XCircle,
} from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AdminPagination from '@/components/AdminPagination.vue';
import AdminTableFrame from '@/components/AdminTableFrame.vue';
import AuditAwareSuccess from '@/components/AuditAwareSuccess.vue';
import DrawerFrame from '@/components/DrawerFrame.vue';
import ListQueryToolbar from '@/components/ListQueryToolbar.vue';
import ListRowActions from '@/components/ListRowActions.vue';
import PageState from '@/components/PageState.vue';
import {
  cancelMasterDataBatch,
  getMasterDataBatch,
  getHospitalDirectorySyncResults,
  getIcd10SyncResults,
  getMedicalDirectorySyncResults,
  runMasterDataBatch,
  listMasterDataBatches,
  getMasterDataSyncOptions,
  masterDataBatchStatuses,
  masterDataCategories,
  startMasterDataBatch,
} from '@/api/master-data/batch';
import type {
  MasterDataBatchStatus,
  MasterDataBatchSummary,
  MasterDataCategory,
  MasterDataSyncOptions,
  HospitalDirectorySyncResult,
  Icd10SyncResult,
  MedicalDirectorySyncResult,
  StartMasterDataBatchInput,
} from '@/api/master-data/batch';
import { listOrganizations } from '@/api/system/organization';
import { authState, hasPermission } from '@/store/modules/auth';
import { ApiClientError } from '@/utils/request';
import StartBatchDrawer from './components/StartBatchDrawer.vue';
import DirectorySyncResultPanel from './components/DirectorySyncResultPanel.vue';
import Icd10SyncResultPanel from './components/Icd10SyncResultPanel.vue';
import MedicalDirectorySyncResultPanel from './components/MedicalDirectorySyncResultPanel.vue';
import { formatBatchDuration, formatBatchTime as formatTime } from './batchTime';
import {
  emptyMasterDataBatchForm,
  masterDataCategoryLabels,
  toStartMasterDataBatchInput,
  validateMasterDataBatchForm,
} from './form';

const router = useRouter();
const batches = ref<MasterDataBatchSummary[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const organizationFilter = ref('');
const categoryFilter = ref<MasterDataCategory | ''>('');
const statusFilter = ref<MasterDataBatchStatus | ''>('');
const requestKeyFilter = ref('');
const isLoading = ref(true);
const isRefreshing = ref(false);
const error = ref<ApiClientError | null>(null);
const selected = ref<MasterDataBatchSummary | null>(null);
const detailError = ref<ApiClientError | null>(null);
const isDetailLoading = ref(false);
const directoryResults = ref<HospitalDirectorySyncResult[]>([]);
const medicalDirectoryResults = ref<MedicalDirectorySyncResult[]>([]);
const icd10Results = ref<Icd10SyncResult[]>([]);
const isDirectoryResultsLoading = ref(false);
const isAdvancing = ref(false);
const confirmCancelId = ref<number | null>(null);
const actionError = ref<ApiClientError | null>(null);
const isCreatorOpen = ref(false);
const isSaving = ref(false);
const createError = ref<ApiClientError | null>(null);
const formError = ref('');
const notice = ref('');
const auditTarget = ref<{ targetType: string; targetId: string } | null>(null);
const form = ref(
  emptyMasterDataBatchForm(authState.user?.organizationCode ?? ''),
);
const lastStartInput = ref<StartMasterDataBatchInput | null>(null);
const organizationOptions = ref<Array<{ code: string; name: string }>>([]);
const syncOptions = ref<MasterDataSyncOptions>({ sources: [], businesses: [] });
const isSyncSourceLoading = ref(true);
const hasLoadedSyncSources = ref(false);
const syncSourceError = ref<ApiClientError | null>(null);
const currentTime = ref(Date.now());
let listController: AbortController | null = null;
let syncSourceController: AbortController | null = null;
let detailController: AbortController | null = null;
let mounted = true;
let detailPollTimer: ReturnType<typeof setTimeout> | null = null;
let durationTimer: ReturnType<typeof setInterval> | null = null;

const canStart = computed(() => hasPermission('master-data:sync'));
const hasBatchRowActions = computed(
  () =>
    canStart.value &&
    batches.value.some((item) => item.status === 'CREATED'),
);
const hasSyncOption = computed(
  () =>
    hasLoadedSyncSources.value &&
    syncOptions.value.sources.length > 0 &&
    syncOptions.value.businesses.length > 0,
);
const hasSyncSource = computed(
  () => hasLoadedSyncSources.value && syncOptions.value.sources.length > 0,
);
const canRunSelectedBatch = computed(
  () =>
    hasPermission('master-data:sync') &&
    selected.value?.status === 'CREATED',
);
const canCancelBatch = computed(
  () =>
    hasPermission('master-data:sync') && selected.value?.status === 'CREATED',
);
const visibleOrganizationOptions = computed(() => {
  const byCode = new Map(
    organizationOptions.value.map((item) => [item.code, item]),
  );
  for (const code of authState.user?.organizationCodes ?? []) {
    if (!byCode.has(code)) byCode.set(code, { code, name: code });
  }
  for (const batch of batches.value) {
    if (batch.organizationCode && !byCode.has(batch.organizationCode)) {
      byCode.set(batch.organizationCode, {
        code: batch.organizationCode,
        name: batch.organizationName ?? batch.organizationCode,
      });
    }
  }
  return [...byCode.values()].sort((left, right) =>
    left.name.localeCompare(right.name, 'zh-CN'),
  );
});

/** 将未知异常转换为页面可处理的API错误。 */
function asApiError(caught: unknown, message: string) {
  return caught instanceof ApiClientError
    ? caught
    : new ApiClientError('UNKNOWN_ERROR', message, 0);
}

/** 会话失效时回到登录页并保留返回地址。 */
async function handleUnauthorized(apiError: ApiClientError) {
  if (apiError.status !== 401) return false;
  authState.user = null;
  authState.isInitialized = true;
  await router.replace({
    path: '/login',
    query: { redirect: '/master-data/batches' },
  });
  return true;
}

/** 使用当前筛选读取真实分页批次。 */
async function loadBatches(background = false) {
  listController?.abort();
  const controller = new AbortController();
  listController = controller;
  if (background) isRefreshing.value = true;
  else isLoading.value = true;
  error.value = null;
  try {
    const result = await listMasterDataBatches(
      {
        organizationCode: organizationFilter.value || undefined,
        requestKey: requestKeyFilter.value.trim() || undefined,
        category: categoryFilter.value || undefined,
        status: statusFilter.value || undefined,
        page: page.value,
        pageSize: pageSize.value,
      },
      controller.signal,
    );
    if (!mounted || controller.signal.aborted) return;
    batches.value = result.items;
    total.value = result.total;
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取同步批次');
    if (
      apiError.code !== 'REQUEST_ABORTED' &&
      !(await handleUnauthorized(apiError))
    )
      error.value = apiError;
  } finally {
    if (listController === controller && mounted) {
      isLoading.value = false;
      isRefreshing.value = false;
    }
  }
}

/** 同时重读批次和同步来源，恢复页面中彼此独立的读取状态。 */
async function reloadPage() {
  await Promise.all([loadBatches(true), loadSyncSources()]);
}

/** 在有机构查询权限时补充机构名称；否则仍使用当前账号的机构代码范围。 */
async function loadOrganizationOptions() {
  if (!hasPermission('organization:read')) return;
  try {
    const rows = await listOrganizations(true);
    if (mounted)
      organizationOptions.value = rows.map((item) => ({
        code: item.organizationCode,
        name: item.organizationName,
      }));
  } catch {
    // 机构选项是辅助数据，失败不覆盖批次主列表的真实错误状态。
  }
}

/** 读取当前可连接的基层HIS机构和环境；机构基本信息不能替代接口连接设置。 */
async function loadSyncSources() {
  if (!canStart.value) {
    isSyncSourceLoading.value = false;
    return;
  }
  syncSourceController?.abort();
  const controller = new AbortController();
  syncSourceController = controller;
  isSyncSourceLoading.value = true;
  syncSourceError.value = null;
  try {
    const options = await getMasterDataSyncOptions(controller.signal);
    if (mounted && !controller.signal.aborted) {
      syncOptions.value = options;
      hasLoadedSyncSources.value = true;
    }
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取可用的HIS接口配置');
    if (apiError.code !== 'REQUEST_ABORTED' &&
        !(await handleUnauthorized(apiError)) && mounted && !controller.signal.aborted) {
      syncOptions.value = { sources: [], businesses: [] };
      hasLoadedSyncSources.value = false;
      syncSourceError.value = apiError;
    }
  } finally {
    if (mounted && syncSourceController === controller) isSyncSourceLoading.value = false;
  }
}

/** 应用筛选并回到第一页。 */
function applyFilters() {
  page.value = 1;
  void loadBatches();
}

/** 清除全部批次筛选。 */
function clearFilters() {
  organizationFilter.value = '';
  categoryFilter.value = '';
  statusFilter.value = '';
  requestKeyFilter.value = '';
  applyFilters();
}

/** 关闭成功提示并清理对应审计目标。 */
function closeNotice() {
  notice.value = '';
  auditTarget.value = null;
}

/** 切换服务端页码。 */
function changePage(value: number) {
  page.value = value;
  void loadBatches();
}

/** 切换服务端每页条数并重新读取第一页。 */
function changePageSize(value: number) {
  pageSize.value = value;
  page.value = 1;
  void loadBatches();
}

/** 打开统一的基础数据同步窗口；只能选择已验证的HIS接口来源。 */
function openCreator() {
  const currentCode = authState.user?.organizationCode ?? '';
  const defaultSource =
    syncOptions.value.sources.find(
      (item) => item.organizationCode === currentCode,
    ) ?? syncOptions.value.sources[0];
  form.value = emptyMasterDataBatchForm(defaultSource?.organizationCode ?? '');
  if (defaultSource?.environments.length) {
    form.value.environment = defaultSource.environments[0];
  }
  const defaultBusiness = syncOptions.value.businesses[0];
  if (defaultBusiness) form.value.category = defaultBusiness.category;
  form.value.mode = form.value.category === 'MEDICAL_DIRECTORY' || form.value.category === 'ICD10_DIAGNOSIS' ? 'TIME_RANGE' : 'NOT_APPLICABLE';
  formError.value = '';
  createError.value = null;
  lastStartInput.value = null;
  isCreatorOpen.value = true;
}

/** 将未满足条件的机构配置引导回唯一维护入口，避免在同步页重复维护机构或HIS资料。 */
async function openInterfaceConfiguration() {
  isCreatorOpen.value = false;
  await router.push('/external-systems');
}

/** 二次确认后取消尚未执行的批次，不删除历史记录。 */
async function cancelBatch(item: MasterDataBatchSummary) {
  if (confirmCancelId.value !== item.id) {
    confirmCancelId.value = item.id;
    return;
  }
  if (isAdvancing.value) return;
  isAdvancing.value = true;
  actionError.value = null;
  try {
    const updated = await cancelMasterDataBatch(item.id, item.version);
    if (selected.value?.id === item.id) selected.value = updated;
    notice.value = `批次 ${updated.batchNo} 已取消，未调用来源HIS。`;
    auditTarget.value = {
      targetType: 'MASTER_DATA_BATCH',
      targetId: updated.batchNo,
    };
  } catch (caught) {
    const apiError = asApiError(caught, '无法取消同步批次');
    if (!(await handleUnauthorized(apiError))) {
      if (selected.value?.id === item.id) await openDetail(item);
      actionError.value = apiError;
    }
  } finally {
    confirmCancelId.value = null;
    isAdvancing.value = false;
    await loadBatches(true);
  }
}

/** 创建当前选择业务批次并立即完成自动取得、校验和当前数据更新。 */
async function submitBatch() {
  formError.value = validateMasterDataBatchForm(form.value) ?? '';
  const business = syncOptions.value.businesses.find(
    (item) => item.category === form.value.category,
  );
  if (!formError.value && !business) formError.value = '请选择可用的同步业务';
  if (formError.value || isSaving.value) return;
  const input = toStartMasterDataBatchInput(form.value);
  lastStartInput.value = input;
  isSaving.value = true;
  createError.value = null;
  let created: MasterDataBatchSummary | null = null;
  try {
    created = await startMasterDataBatch(input);
    const updated = await runMasterDataBatch(created.id, created.version);
    isCreatorOpen.value = false;
    notice.value =
      updated.status === 'COMPLETED'
        ? `${masterDataCategoryLabels[updated.category]}数量已自动核对，当前数据已更新。`
        : `${masterDataCategoryLabels[updated.category]}：${resultSummary(updated)}`;
    auditTarget.value = {
      targetType: 'MASTER_DATA_BATCH',
      targetId: updated.batchNo,
    };
    page.value = 1;
    await loadBatches(true);
    await openDetail(updated);
  } catch (caught) {
    const apiError = asApiError(
      caught,
      created ? '批次已创建，但无法完成本次同步' : '无法开始同步',
    );
    if (!(await handleUnauthorized(apiError))) {
      if (created) {
        isCreatorOpen.value = false;
        await loadBatches(true);
        if (!(await recoverCompletedBatch(created, apiError))) {
          await openDetail(created);
          actionError.value = apiError;
        }
      } else {
        createError.value = apiError;
      }
    }
  } finally {
    isSaving.value = false;
  }
}

/**
 * 运行请求未返回时重新读取批次结果；HIS 已完成而浏览器断开时，不能把通信异常误报成业务失败。
 *
 * @param created 已成功创建的批次
 * @param apiError 本次运行请求的通信或服务端异常
 * @returns 已读取到最终状态，或确认仍在执行并开始只读轮询时为true
 */
async function recoverCompletedBatch(
  created: MasterDataBatchSummary,
  apiError: ApiClientError,
) {
  try {
    const latest = await getMasterDataBatch(created.id);
    if (latest.status === 'FETCHING') {
      await openDetail(latest);
      notice.value = '同步仍在后台执行，正在自动读取进度；请勿重复发起。';
      return true;
    }
    if (
      latest.status !== 'COMPLETED' &&
      latest.status !== 'COMPLETED_WITH_ERRORS' &&
      latest.status !== 'COMPLETED_WITH_UNKNOWN' &&
      latest.status !== 'FAILED'
    ) {
      return false;
    }
    await openDetail(latest);
    actionError.value = null;
    notice.value =
      latest.status === 'COMPLETED'
        ? '已重新读取并确认：本次同步已完成，当前目录已更新。'
        : `已重新读取并确认：本次同步已结束，${resultSummary(latest)}`;
    auditTarget.value = {
      targetType: 'MASTER_DATA_BATCH',
      targetId: latest.batchNo,
    };
    return true;
  } catch (recoveryCaught) {
    const recoveryError = asApiError(recoveryCaught, '无法重新读取同步批次');
    if (!(await handleUnauthorized(recoveryError))) actionError.value = apiError;
    return false;
  }
}

/** 打开当前批次对应的有效目录；公共ICD-10目录不附加机构筛选。 */
async function openCurrentDirectory() {
  const organizationCode = selected.value?.organizationCode;
  const directoryPath = selected.value?.category === 'ICD10_DIAGNOSIS'
    ? '/master-data/directory/icd10'
    : selected.value?.category === 'MEDICAL_DIRECTORY'
      ? '/master-data/directory/medical'
      : '/master-data/directory';
  selected.value = null;
  actionError.value = null;
  await router.push({
    path: directoryPath,
    query: organizationCode ? { organizationCode } : undefined,
  });
}

/** 发生冲突或结果未知时按原请求编号重新读取，避免直接重复提交。 */
async function reloadCreatedBatch() {
  const requestKey = lastStartInput.value?.requestKey;
  if (!requestKey) return;
  isCreatorOpen.value = false;
  requestKeyFilter.value = requestKey;
  page.value = 1;
  await loadBatches();
  if (batches.value[0]) await openDetail(batches.value[0]);
}

/** 读取批次最新详情，查看不会触发重新执行。 */
async function openDetail(item: MasterDataBatchSummary) {
  if (detailPollTimer) clearTimeout(detailPollTimer);
  selected.value = item;
  actionError.value = null;
  detailError.value = null;
  detailController?.abort();
  const controller = new AbortController();
  detailController = controller;
  isDetailLoading.value = true;
  isDirectoryResultsLoading.value = true;
  directoryResults.value = [];
  medicalDirectoryResults.value = [];
  icd10Results.value = [];
  try {
    const latest = await getMasterDataBatch(item.id, controller.signal);
    if (!mounted || controller.signal.aborted || selected.value?.id !== item.id)
      return;
    selected.value = latest;
    if (latest.category === 'HOSPITAL_DIRECTORY') {
      directoryResults.value = await getHospitalDirectorySyncResults(item.id, controller.signal);
    } else if (latest.category === 'MEDICAL_DIRECTORY') {
      medicalDirectoryResults.value = await getMedicalDirectorySyncResults(item.id, controller.signal);
    } else {
      icd10Results.value = await getIcd10SyncResults(item.id, controller.signal);
    }
    if (item.status === 'FETCHING' && latest.status !== 'FETCHING' &&
        !controller.signal.aborted && selected.value?.id === item.id) {
      notice.value = `已重新读取并确认：${resultSummary(latest)}`;
      await loadBatches(true);
    }
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取批次详情');
    if (
      apiError.code !== 'REQUEST_ABORTED' &&
      !(await handleUnauthorized(apiError))
    )
      detailError.value = apiError;
  } finally {
    if (detailController === controller && mounted)
      isDetailLoading.value = false;
    if (detailController === controller && mounted)
      isDirectoryResultsLoading.value = false;
    // 只重新读取运行结果，不自动重发run；关闭详情、卸载或到达终态后停止。
    if (mounted && detailController === controller && !controller.signal.aborted &&
        selected.value?.id === item.id && selected.value.status === 'FETCHING' && !detailError.value) {
      detailPollTimer = setTimeout(() => {
        if (mounted && selected.value?.id === item.id) void openDetail(selected.value);
      }, 5000);
    }
  }
}

/** 取得当前选择业务并由服务端自动校验和更新当前数据，结果未知时不自动重试。 */
async function runSelectedBatch() {
  const current = selected.value;
  if (!current || isAdvancing.value) return;
  isAdvancing.value = true;
  actionError.value = null;
  try {
    const updated = await runMasterDataBatch(current.id, current.version);
    selected.value = updated;
    notice.value =
      updated.status === 'COMPLETED'
        ? `${masterDataCategoryLabels[updated.category]}数量已自动核对，当前数据已更新。`
        : `${masterDataCategoryLabels[updated.category]}：${resultSummary(updated)}`;
    auditTarget.value = {
      targetType: 'MASTER_DATA_BATCH',
      targetId: updated.batchNo,
    };
    await openDetail(updated);
  } catch (caught) {
    const apiError = asApiError(caught, '无法同步当前基础数据业务');
    if (!(await handleUnauthorized(apiError))) {
      await openDetail(current);
      actionError.value = apiError;
    }
  } finally {
    isAdvancing.value = false;
    await loadBatches(true);
  }
}

/** 重新读取当前打开批次。 */
function reloadSelectedDetail() {
  if (selected.value) void openDetail(selected.value);
}

/** 将后端状态转换为业务人员可理解的文案。 */
function statusPresentation(
  status: MasterDataBatchStatus,
  failureCode: string | null = null,
) {
  if (status === 'FAILED' && failureCode === 'CANCELLED_BY_USER') {
    return { label: '已取消', tone: 'neutral' };
  }
  if (status === 'FAILED' && failureCode === 'HIS_BUSINESS_FAILURE') {
    return { label: 'HIS 查询失败', tone: 'danger' };
  }
  const values: Record<MasterDataBatchStatus, { label: string; tone: string }> =
    {
      CREATED: { label: '尚未开始', tone: 'neutral' },
      FETCHING: { label: '正在取得', tone: 'info' },
      COMPLETED: { label: '已完成', tone: 'success' },
      COMPLETED_WITH_ERRORS: { label: '部分未完成', tone: 'warning' },
      COMPLETED_WITH_UNKNOWN: { label: '存在未知结果', tone: 'warning' },
      FAILED: { label: '同步失败', tone: 'danger' },
      RESULT_UNKNOWN: { label: '结果待确认', tone: 'warning' },
    };
  return values[status];
}

/** @param item 批次摘要 @return 页面可安全展示的同步结果摘要 */
function resultSummary(item: MasterDataBatchSummary) {
  const business = masterDataCategoryLabels[item.category];
  if (item.status === 'FAILED' && item.failureCode === 'HIS_BUSINESS_FAILURE') {
    return `HIS 拒绝${business}查询，平台没有取得任何目录数据。`;
  }
  if (item.status === 'FAILED' && item.failureCode === 'HIS_DATA_INVALID') {
    return '取得的数据未能通过系统校验，平台没有更新当前有效数据。';
  }
  if (item.status === 'RESULT_UNKNOWN') {
    return '无法确认 HIS 是否已处理完成，请先查询交易记录，不要重复提交。';
  }
  if (item.status === 'FAILED') {
    return `本次${business}同步未完成，平台没有更新当前有效数据。`;
  }
  if (item.status === 'COMPLETED_WITH_ERRORS') {
    return `部分${business}类型未通过自动校验或被 HIS 拒绝；已完成类型已经更新，失败类型没有改动。`;
  }
  if (item.status === 'COMPLETED_WITH_UNKNOWN') {
    return `${business}的结果尚未确认；请先查看各项结果，不要直接重复同步。`;
  }
  return item.status === 'COMPLETED'
    ? '系统已完成自动校验，并更新本机构当前目录。'
    : '等待系统处理。';
}

/** @param item 失败批次 @return 面向业务人员的失败归类 */
function failureTitle(item: MasterDataBatchSummary) {
  if (item.failureCode === 'HIS_BUSINESS_FAILURE') return 'HIS 拒绝目录查询';
  if (item.failureCode === 'HIS_DATA_INVALID') return 'HIS 返回的数据未通过校验';
  if (item.failureCode === 'HIS_CONFIGURATION_ERROR') return 'HIS 接口配置不可用';
  return '本次同步未完成';
}

/** @param item 失败批次 @return 不引导重复提交的下一步 */
function failureNextStep(item: MasterDataBatchSummary) {
  if (item.failureCode === 'HIS_BUSINESS_FAILURE') {
    return item.category === 'MEDICAL_DIRECTORY'
      ? '请接口提供方开通该机构的 100-004 和 100-005 目录查询权限；确认后新建一次同步。'
      : '请接口提供方开通该机构的 100-003 目录查询权限；确认后新建一次同步。';
  }
  if (item.failureCode === 'HIS_CONFIGURATION_ERROR') {
    return '请检查该机构的 HIS 接口配置，再从同步列表新建一次同步。';
  }
  return '请先处理失败原因；不要重跑当前记录，处理完成后再新建同步。';
}

/** 显示批次开始至结束或当前时刻的实际运行时长。 */
function formatDuration(item: MasterDataBatchSummary) {
  return formatBatchDuration(item, currentTime.value);
}

/** 显示批次明确的机构或平台公共范围。 */
function scopeLabel(item: MasterDataBatchSummary) {
  return item.scopeType === 'PLATFORM'
    ? '平台公共目录'
    : (item.organizationName ?? item.organizationCode ?? '机构未识别');
}

/** 将来源接口环境转换为业务页面名称。 */
function environmentLabel(item: MasterDataBatchSummary) {
  return item.environment === 'PRODUCTION'
    ? '生产环境'
    : item.environment === 'TEST'
      ? '测试环境'
      : '开发环境';
}

/** 汇总列表行的业务、环境和同步方式，便于识别同一机构的不同批次。 */
function scopeDescription(item: MasterDataBatchSummary) {
  const mode = item.mode === 'FULL'
    ? '全量同步'
    : item.mode === 'TIME_RANGE'
      ? '指定时间范围'
      : '目录同步';
  return `${masterDataCategoryLabels[item.category]} · ${environmentLabel(item)} · ${mode}`;
}

/** 将取得数和来源声明数放在同一视觉层级。 */
function resultCountLabel(item: MasterDataBatchSummary) {
  const { returned, declared } = item.counts;
  return declared === null
    ? `取得 ${returned} 条 · 总数未提供`
    : `取得 ${returned} 条 / 声明 ${declared} 条`;
}

/** @param item 同步批次 @return 本次实际处理的目录范围 */
function batchScopeDescription(item: MasterDataBatchSummary) {
  return item.category === 'ICD10_DIAGNOSIS'
    ? '西医诊断、中医诊断'
    : item.category === 'MEDICAL_DIRECTORY'
      ? '中药、西药、诊疗、耗材'
      : '科室、医生、病区、床位';
}

/** @param item 同步批次 @return 更新策略的准确业务说明 */
function currentDataExplanation(item: MasterDataBatchSummary) {
  return item.category === 'ICD10_DIAGNOSIS'
    ? '系统分别核对100-007声明的数量，并分页取得100-006西医、中医诊断数据；完整通过检查的类别会直接更新平台公共目录。来源时间范围不能证明数据完整，因此本次不会把未返回的数据标记为无效。'
    : item.category === 'MEDICAL_DIRECTORY'
    ? '系统先核对100-005声明的数量，再分页取得100-004数据；完整通过检查的类别会直接更新当前目录。来源时间范围不能证明数据完整，因此本次不会把未返回的数据标记为无效。'
    : '本次读取科室、病区、床位和人员目录；完整返回且通过校验的部分已直接更新为当前数据。';
}

onMounted(() => {
  durationTimer = setInterval(() => { currentTime.value = Date.now(); }, 1000);
  void Promise.all([
    loadBatches(),
    loadOrganizationOptions(),
    loadSyncSources(),
  ]);
});
onBeforeUnmount(() => {
  if (detailPollTimer) clearTimeout(detailPollTimer);
  if (durationTimer) clearInterval(durationTimer);
  mounted = false;
  listController?.abort();
  syncSourceController?.abort();
  detailController?.abort();
});
</script>

<template>
  <section class="content master-batch-page">
    <AuditAwareSuccess
      v-if="notice"
      :message="notice"
      :target-type="auditTarget?.targetType"
      :target-id="auditTarget?.targetId"
      @close="closeNotice"
    />
    <ListQueryToolbar
      filters-layout="dense-grid"
      :refreshing="isRefreshing"
      @query="applyFilters"
      @reset="clearFilters"
      @refresh="reloadPage"
    >
      <label class="prototype-search standard-list-filter--span-2 standard-list-filter--full-tablet">
        <Search :size="16" />
        <input
          v-model="requestKeyFilter"
          type="search"
          maxlength="64"
          placeholder="请求标识"
          aria-label="请求标识"
        />
      </label>
      <select class="standard-list-filter--span-2" v-model="organizationFilter" aria-label="机构范围">
          <option value="">全部机构和公共目录</option>
          <option
            v-for="item in visibleOrganizationOptions"
            :key="item.code"
            :value="item.code"
          >
            {{ item.name }}
          </option>
      </select>
      <select class="standard-list-filter" v-model="categoryFilter" aria-label="数据类别">
          <option value="">全部数据类别</option>
          <option
            v-for="category in masterDataCategories"
            :key="category"
            :value="category"
          >
            {{ masterDataCategoryLabels[category] }}
          </option>
      </select>
      <select class="standard-list-filter" v-model="statusFilter" aria-label="批次状态">
          <option value="">全部状态</option>
          <option
            v-for="status in masterDataBatchStatuses"
            :key="status"
            :value="status"
          >
            {{ statusPresentation(status).label }}
          </option>
      </select>
      <template #actions>
        <button
          v-if="canStart"
          class="prototype-button"
          type="button"
          :disabled="isSyncSourceLoading"
          :title="
            isSyncSourceLoading
              ? '正在读取可用的HIS接口配置'
              : undefined
          "
          @click="openCreator()"
        >
          <Plus :size="15" />发起同步
        </button>
      </template>
    </ListQueryToolbar>

    <section
      v-if="canStart && !error && !isSyncSourceLoading && syncSourceError"
      class="sync-readiness-notice sync-readiness-error"
      role="alert"
    >
      <AlertCircle :size="18" />
      <div>
        <strong>暂时无法确认可同步的机构</strong>
        <span>{{ syncSourceError.message }}；不能据此判断机构是否可用。<template v-if="syncSourceError.requestId">请求编号：{{ syncSourceError.requestId }}</template></span>
      </div>
      <button class="work-quiet-button" type="button" @click="loadSyncSources">
        重试读取
      </button>
    </section>

    <section
      v-if="canStart && !error && hasLoadedSyncSources && !hasSyncSource"
      class="sync-readiness-notice"
      aria-live="polite"
    >
      <AlertCircle :size="18" />
      <div>
        <strong>未找到当前账号可用的同步来源</strong>
        <span>配置已读取，但没有可用的机构和接口环境组合；请核对机构授权与基层 HIS 接口状态。</span>
      </div>
      <button class="work-quiet-button" type="button" @click="openInterfaceConfiguration">
        查看接口配置
      </button>
    </section>

    <section
      v-if="canStart && !error && hasLoadedSyncSources && hasSyncSource && !hasSyncOption"
      class="sync-readiness-notice"
      aria-live="polite"
    >
      <AlertCircle :size="18" />
      <div>
        <strong>当前没有已开放的同步业务</strong>
        <span>机构接口已可用，但尚无可执行的目录同步业务；请联系管理员确认业务接入状态。</span>
      </div>
    </section>

    <section
      class="prototype-section work-table-section batch-table-section"
    >
      <div v-if="error && !isLoading" class="batch-list-error" role="alert">
        <AlertCircle :size="19" />
        <div>
          <strong>同步批次读取失败</strong>
          <span>{{ error.message }}；本次查询未完成，无法确认批次总数。</span>
          <small v-if="error.requestId">请求编号：{{ error.requestId }}</small>
        </div>
        <button class="work-quiet-button" type="button" @click="reloadPage">重试读取</button>
      </div>
      <PageState v-if="isLoading" kind="loading" title="正在读取同步批次" description="请稍候" />
      <PageState v-else-if="!error && total === 0" kind="empty" title="当前条件下没有同步批次" compact :description="
          canStart && hasSyncOption
            ? '可选择已开放的业务发起新同步。'
            : '可调整筛选条件，或稍后刷新查看最新记录。'
        ">
        <template #icon><Database :size="29" /></template>
      </PageState>
      <AdminTableFrame v-else-if="!error && batches.length" label="同步批次列表" :has-actions="hasBatchRowActions">
        <table class="work-table">
          <thead>
            <tr>
              <th>批次号 / 开始时间</th>
              <th>同步对象</th>
              <th>取得结果</th>
              <th>处理结果</th>
              <th v-if="hasBatchRowActions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in batches" :key="item.id">
              <td>
                <strong>{{ item.batchNo }}</strong>
                <small>
                  {{ item.startedAt ? `开始 ${formatTime(item.startedAt)}` : '尚未开始' }}
                </small>
              </td>
              <td>
                <strong :title="scopeLabel(item)">{{ scopeLabel(item) }}</strong>
                <small :title="scopeDescription(item)">{{ scopeDescription(item) }}</small>
              </td>
              <td>
                <strong>{{ resultCountLabel(item) }}</strong>
                <small>{{
                  item.countTradeCode
                    ? `${item.dataTradeCode} / ${item.countTradeCode}`
                    : item.dataTradeCode
                }}</small>
              </td>
              <td>
                <span
                  class="prototype-tag"
                  :class="
                    statusPresentation(item.status, item.failureCode).tone
                  "
                  >{{
                    statusPresentation(item.status, item.failureCode).label
                  }}</span
                ><small>{{ item.startedAt ? (item.finishedAt ? '耗时 ' : '已运行 ') : '' }}{{ formatDuration(item) }}</small>
                <small
                  v-if="item.status === 'FAILED' || item.status === 'RESULT_UNKNOWN'"
                  class="batch-result-note"
                  :title="resultSummary(item)"
                  >{{ resultSummary(item) }}</small
                >
              </td>
              <td v-if="hasBatchRowActions">
                <ListRowActions label="同步批次操作">
                  <button
                    v-if="
                      hasPermission('master-data:sync') &&
                      item.status === 'CREATED'
                    "
                    class="work-danger-button"
                    type="button"
                    :disabled="isAdvancing"
                    @click="cancelBatch(item)"
                  >
                    {{ confirmCancelId === item.id ? '确认取消' : '取消' }}
                  </button>
                </ListRowActions>
              </td>
            </tr>
          </tbody>
        </table>
      </AdminTableFrame>
      <AdminPagination
        v-if="!isLoading && !error && total > 0"
        :total="total"
        :page="page"
        :page-size="pageSize"
        @update:page="changePage"
        @update:page-size="changePageSize"
      />
    </section>

    <DrawerFrame v-if="selected" panel-class="batch-detail-drawer" panel-width="min(760px, 100vw)" body-padding="16px 22px 22px" labelled-by="batch-detail-title" @close="selected = null">
        <template #title>
          <div>
            <small>同步记录 · {{ masterDataCategoryLabels[selected.category] }} · {{ selected.mode === 'FULL' ? '全量同步' : selected.mode === 'TIME_RANGE' ? '指定时间范围' : '目录同步' }}</small>
            <h2 id="batch-detail-title">同步结果</h2>
          </div>
        </template>
        <template #subheader><div class="work-drawer-sub">
          <span
            class="prototype-tag"
            :class="
              statusPresentation(selected.status, selected.failureCode).tone
            "
            >{{
              statusPresentation(selected.status, selected.failureCode).label
            }}</span
          ><span>{{ scopeLabel(selected) }}</span><span>·</span><span>{{ environmentLabel(selected) }}</span
          ><span>·</span><span class="batch-reference">批次号 {{ selected.batchNo }}</span>
        </div></template>
          <PageState v-if="isDetailLoading" kind="loading" title="正在读取最新结果" compact />
          <div v-else-if="detailError" class="feedback danger" role="alert">
            <AlertCircle :size="18" /><span>{{ detailError.message }}</span
            ><button
              class="text-button"
              type="button"
              @click="reloadSelectedDetail"
            >
              重新读取
            </button>
          </div>
          <template v-else>
            <section
              v-if="selected.status === 'FAILED'"
              class="batch-detail-section failure-outcome"
            >
              <div class="failure-heading">
                <AlertCircle :size="20" />
                <div>
                  <small>本次没有取得数据</small>
                  <h3>{{ failureTitle(selected) }}</h3>
                </div>
              </div>
              <p>{{ resultSummary(selected) }}</p>
              <dl class="failure-facts">
                <div><dt>本次范围</dt><dd>{{ batchScopeDescription(selected) }}</dd></div>
                <div><dt>平台数据</dt><dd>未写入，当前有效数据未改变</dd></div>
                <div><dt>下一步</dt><dd>{{ failureNextStep(selected) }}</dd></div>
              </dl>
            </section>
            <DirectorySyncResultPanel
              v-if="selected.category === 'HOSPITAL_DIRECTORY'"
              :results="directoryResults"
              :loading="isDirectoryResultsLoading"
            />
            <MedicalDirectorySyncResultPanel
              v-else-if="selected.category === 'MEDICAL_DIRECTORY'"
              :results="medicalDirectoryResults"
              :loading="isDirectoryResultsLoading"
            />
            <Icd10SyncResultPanel
              v-else
              :results="icd10Results"
              :loading="isDirectoryResultsLoading"
            />
            <section class="batch-detail-section batch-execution-section">
              <h3>本次同步</h3>
              <dl class="batch-execution-facts">
                <div>
                  <dt>{{ selected.scopeType === 'PLATFORM' ? '目录范围' : '同步机构' }}</dt>
                  <dd>{{ scopeLabel(selected) }}</dd>
                </div>
                <div>
                  <dt>接口环境</dt>
                  <dd>{{ environmentLabel(selected) }}</dd>
                </div>
                <div>
                  <dt>接口交易</dt>
                  <dd>{{ selected.dataTradeCode }}<template v-if="selected.countTradeCode">、{{ selected.countTradeCode }}</template></dd>
                </div>
                <div>
                  <dt>开始时间</dt>
                  <dd>{{ selected.startedAt ? formatTime(selected.startedAt) : '尚未开始' }}</dd>
                </div>
                <div>
                  <dt>结束时间</dt>
                  <dd>{{ selected.finishedAt ? formatTime(selected.finishedAt) : selected.startedAt ? '进行中' : '—' }}</dd>
                </div>
                <div>
                  <dt>{{ selected.finishedAt ? '总耗时' : '已运行' }}</dt>
                  <dd>{{ formatDuration(selected) }}</dd>
                </div>
              </dl>
            </section>
            <section
              v-if="selected.counts.returned > 0 || selected.status === 'COMPLETED'"
              class="batch-detail-section"
            >
              <h3>数据更新结果</h3>
              <p class="batch-result-intro">
                {{ currentDataExplanation(selected) }}
              </p>
              <div class="count-grid">
                <span
                  ><small>取得记录</small
                  ><strong>{{ selected.counts.returned }} 条</strong></span
                ><span
                  ><small>新增 / 更新</small
                  ><strong>{{ selected.counts.created }} / {{ selected.counts.updated }} 条</strong></span
                ><span
                  v-if="selected.category === 'HOSPITAL_DIRECTORY'"
                  ><small>标记无效</small
                  ><strong>{{ selected.counts.sourceMissing }} 条</strong></span
                ><span
                  ><small>当前有效目录</small
                  ><strong>{{
                    selected.counts.active ?? '未完成'
                  }} 条</strong></span
                ><span
                  ><small>校验问题</small
                  ><strong>{{ selected.counts.invalid + selected.counts.conflict }} 条</strong></span
                >
              </div>
              <button
                v-if="selected.status === 'COMPLETED'"
                class="work-quiet-button batch-directory-button"
                type="button"
                @click="openCurrentDirectory"
              >
                查看当前数据目录 <ChevronRight :size="15" />
              </button>
            </section>
          </template>
        <div
          v-if="actionError"
          class="feedback danger batch-action-error"
          role="alert"
        >
          <AlertCircle :size="18" />
          <span
            ><strong>{{ actionError.message }}</strong
            ><small>页面已重新读取最新批次状态，请勿直接重复操作。</small></span
          >
        </div>
        <template #footer>
          <button
            class="work-quiet-button"
            type="button"
            :disabled="isAdvancing"
            @click="selected = null"
          >
            关闭
          </button>
          <button
            v-if="canCancelBatch"
            class="work-danger-button"
            type="button"
            :disabled="isAdvancing"
            @click="cancelBatch(selected)"
          >
            <XCircle :size="15" />{{
              confirmCancelId === selected.id ? '确认取消批次' : '取消本批次'
            }}
          </button>
          <button
            v-if="canRunSelectedBatch"
            class="prototype-button"
            type="button"
            :disabled="isAdvancing"
            @click="runSelectedBatch"
          >
            <LoaderCircle v-if="isAdvancing" class="spinning" :size="15" />{{
              isAdvancing ? '正在同步' : `同步${masterDataCategoryLabels[selected.category]}`
            }}
          </button>
          <button
            v-if="selected.status === 'COMPLETED'"
            class="prototype-button"
            type="button"
            @click="openCurrentDirectory"
          >
            查看当前数据
          </button>
        </template>
    </DrawerFrame>

    <StartBatchDrawer
      v-if="isCreatorOpen"
      v-model:form="form"
      :options="syncOptions"
      :is-source-loading="isSyncSourceLoading"
      :source-error="syncSourceError"
      :is-saving="isSaving"
      :error="createError"
      :form-error="formError"
      @close="isCreatorOpen = false"
      @submit="submitBatch"
      @reload="reloadCreatedBatch"
      @configure="openInterfaceConfiguration"
      @retry-sources="loadSyncSources"
    />
  </section>
</template>

<style scoped>
.master-batch-page {
  min-width: 0;
  color: var(--ui-color-text);
}
.batch-table-section {
  min-height: 0;
}
.sync-readiness-notice {
  margin: 10px 0 12px;
  padding: 12px 14px;
  border: 1px solid #ead6a8;
  border-left: 3px solid #d89a2b;
  border-radius: 6px;
  background: #fffaf0;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #805819;
}
.sync-readiness-notice > div { min-width: 0; display: grid; gap: 3px; }
.sync-readiness-notice strong { color: #6f4d16; font-size: 13px; }
.sync-readiness-notice span { color: #805f2b; font-size: 12px; line-height: 1.55; }
.sync-readiness-notice button { margin-left: auto; flex: 0 0 auto; }
.sync-readiness-error {
  border-color: #e6c9c5;
  border-left-color: #ba5b4f;
  background: #fff8f7;
}
.sync-readiness-error strong,
.sync-readiness-error span { color: #8d4038; }
.batch-list-error {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 20px;
  color: #8d4038;
  background: #fff8f7;
  border-bottom: 1px solid #e6c9c5;
}
.batch-list-error > div { display: grid; gap: 4px; min-width: 0; }
.batch-list-error strong { font-size: 14px; }
.batch-list-error span,
.batch-list-error small { font-size: 12px; line-height: 1.5; }
.batch-list-error button { margin-left: auto; flex: 0 0 auto; }
.table-scroll {
  overflow-x: auto;
}
.batch-table-section .work-table {
  min-width: 960px;
  table-layout: fixed;
}
.batch-table-section th:nth-child(1) {
  width: 23%;
}
.batch-table-section th:nth-child(2) {
  width: 25%;
}
.batch-table-section th:nth-child(3) {
  width: 20%;
}
.batch-table-section th:nth-child(5) {
  width: 150px;
  text-align: right;
}
.batch-table-section td {
  overflow: hidden;
}
.batch-table-section .work-row-link {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
.batch-row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}
.batch-table-section td strong,
.batch-table-section td small,
.batch-table-section td span:not(.prototype-tag) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.batch-table-section td strong {
  display: block;
}
.batch-result-note {
  display: block;
  color: #74858d;
}
.batch-result-note.warning {
  color: #8a641f;
}
.batch-detail-section {
  margin-bottom: 18px;
  padding: 15px;
  border: 1px solid #e0e6e8;
  border-radius: 6px;
}
.failure-outcome {
  border-color: #efc9c1;
  background: #fff8f6;
}
.failure-heading {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #ae5039;
}
.failure-heading small {
  display: block;
  color: #8d6b63;
  font-size: 11px;
}
.failure-heading h3 {
  margin: 3px 0 0;
  color: #813d2c;
}
.failure-outcome > p {
  margin: 12px 0 0;
  color: #553f39;
  line-height: 1.65;
}
.failure-facts {
  margin-top: 14px !important;
  padding-top: 13px;
  border-top: 1px solid #f0d7d1;
}
.failure-facts div {
  grid-template-columns: 74px minmax(0, 1fr) !important;
}
.failure-facts dd {
  color: #3e3532;
}
.batch-reference {
  color: #74838a;
  font-size: 11px;
}
.batch-detail-section h3 {
  margin: 0 0 13px;
  font-size: 14px;
}
.batch-result-intro {
  margin: -4px 0 12px;
  color: #74858c;
  font-size: 12px;
  line-height: 1.6;
}
.batch-directory-button { margin-top: 14px; }
.batch-detail-section dl {
  margin: 0;
  display: grid;
  gap: 10px;
}
.batch-detail-section dl div {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 10px;
}
.batch-detail-section dt {
  color: #73828a;
}
.batch-detail-section dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.batch-execution-section .batch-execution-facts {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: 18px;
  row-gap: 14px;
}
.batch-execution-section .batch-execution-facts div {
  display: block;
  min-width: 0;
}
.batch-execution-section .batch-execution-facts dt {
  font-size: 12px;
  white-space: nowrap;
}
.batch-execution-section .batch-execution-facts dd {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 600;
}
.count-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.count-grid span {
  padding: 10px;
  border-radius: 5px;
  background: #f5f8f8;
}
.count-grid small,
.count-grid strong {
  display: block;
}
.count-grid small {
  color: #75858d;
}
.count-grid strong {
  margin-top: 4px;
}
.danger-detail {
  border-color: #edd3d3;
  background: #fff8f8;
}
.danger-detail p {
  margin: 6px 0 0;
  line-height: 1.65;
}
.batch-action-error {
  margin: 0 22px 14px;
}
.work-drawer-footer {
  padding: 14px 22px;
  display: flex;
  justify-content: flex-end;
  gap: 9px;
}
.text-button {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: #176f61;
  font-weight: 650;
}
@media (max-width: 640px) {
  .sync-readiness-notice,
  .batch-list-error { align-items: flex-start; flex-wrap: wrap; }
  .sync-readiness-notice button,
  .batch-list-error button { margin-left: 28px; }
  .batch-execution-section .batch-execution-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .count-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
