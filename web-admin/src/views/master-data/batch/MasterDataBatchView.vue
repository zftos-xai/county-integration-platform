<!-- 同步批次正式页面：使用真实API查询、创建和回读基础数据同步批次。 -->
<script setup lang="ts">
import {
  AlertCircle,
  ChevronRight,
  Database,
  LoaderCircle,
  Plus,
  RefreshCw,
  XCircle,
} from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AdminPagination from '@/components/AdminPagination.vue';
import AuditAwareSuccess from '@/components/AuditAwareSuccess.vue';
import {
  cancelMasterDataBatch,
  getMasterDataBatch,
  getHospitalDirectorySyncResults,
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
  MedicalDirectorySyncResult,
  StartMasterDataBatchInput,
} from '@/api/master-data/batch';
import { listOrganizations } from '@/api/system/organization';
import { authState, hasPermission } from '@/store/modules/auth';
import { ApiClientError } from '@/utils/request';
import StartBatchDrawer from './components/StartBatchDrawer.vue';
import DirectorySyncResultPanel from './components/DirectorySyncResultPanel.vue';
import MedicalDirectorySyncResultPanel from './components/MedicalDirectorySyncResultPanel.vue';
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
let listController: AbortController | null = null;
let detailController: AbortController | null = null;
let mounted = true;

const canStart = computed(() => hasPermission('master-data:sync'));
const hasSyncOption = computed(
  () =>
    syncOptions.value.sources.length > 0 &&
    syncOptions.value.businesses.length > 0,
);
const hasSyncSource = computed(() => syncOptions.value.sources.length > 0);
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

/** 读取真实可执行的基层HIS机构与环境，不以机构主数据代替接口配置。 */
async function loadSyncSources() {
  if (!canStart.value) {
    isSyncSourceLoading.value = false;
    return;
  }
  isSyncSourceLoading.value = true;
  try {
    const options = await getMasterDataSyncOptions();
    if (mounted) syncOptions.value = options;
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取可用的HIS接口配置');
    if (!(await handleUnauthorized(apiError))) createError.value = apiError;
  } finally {
    if (mounted) isSyncSourceLoading.value = false;
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

/** 打开基础数据同步窗口；只能选择已验证的HIS接口来源。 */
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
        ? `${masterDataCategoryLabels[updated.category]}已完成自动对账，当前数据已更新。`
        : `${masterDataCategoryLabels[updated.category]}部分未完成：${resultSummary(updated)}`;
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
 * 运行请求未返回时回读批次事实；HIS 已完成而浏览器断开时，不能把通信异常误报成业务失败。
 *
 * @param created 已成功创建的批次
 * @param apiError 本次运行请求的通信或服务端异常
 * @returns 已确认批次终态时为true
 */
async function recoverCompletedBatch(
  created: MasterDataBatchSummary,
  apiError: ApiClientError,
) {
  try {
    const latest = await getMasterDataBatch(created.id);
    if (
      latest.status !== 'COMPLETED' &&
      latest.status !== 'COMPLETED_WITH_ERRORS' &&
      latest.status !== 'COMPLETED_WITH_UNKNOWN'
    ) {
      return false;
    }
    selected.value = latest;
    actionError.value = null;
    notice.value =
      latest.status === 'COMPLETED'
        ? '已回读确认：本次同步已完成，当前目录已更新。'
        : `已回读确认：本次同步已结束，${resultSummary(latest)}`;
    auditTarget.value = {
      targetType: 'MASTER_DATA_BATCH',
      targetId: latest.batchNo,
    };
    return true;
  } catch (recoveryCaught) {
    const recoveryError = asApiError(recoveryCaught, '无法回读同步批次');
    if (!(await handleUnauthorized(recoveryError))) actionError.value = apiError;
    return false;
  }
}

/** 打开该机构当前有效目录；目录页面是同步完成后实际使用数据的入口。 */
async function openCurrentDirectory() {
  const organizationCode = selected.value?.organizationCode;
  selected.value = null;
  actionError.value = null;
  await router.push({
    path: '/master-data/directory',
    query: organizationCode ? { organization: organizationCode } : undefined,
  });
}

/** 发生冲突或结果未知时按原请求标识回读，避免直接重复提交。 */
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
  try {
    const latest = await getMasterDataBatch(item.id, controller.signal);
    if (!mounted || controller.signal.aborted || selected.value?.id !== item.id)
      return;
    selected.value = latest;
    if (latest.category === 'HOSPITAL_DIRECTORY') {
      directoryResults.value = await getHospitalDirectorySyncResults(item.id, controller.signal);
    } else {
      medicalDirectoryResults.value = await getMedicalDirectorySyncResults(item.id, controller.signal);
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
  }
}

/** 打开读取本次同步事实与系统校验结论的批次详情页面。 */
async function openReview(batchId: number) {
  await router.push(`/master-data/batches/${batchId}`);
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
        ? `${masterDataCategoryLabels[updated.category]}已完成自动对账，当前数据已更新。`
        : `${masterDataCategoryLabels[updated.category]}部分未完成：${resultSummary(updated)}`;
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
    return { label: '等待接口授权', tone: 'danger' };
  }
  const values: Record<MasterDataBatchStatus, { label: string; tone: string }> =
    {
      CREATED: { label: '尚未开始', tone: 'neutral' },
      FETCHING: { label: '正在取得', tone: 'info' },
      COMPLETED: { label: '已完成对账', tone: 'success' },
      COMPLETED_WITH_ERRORS: { label: '部分未完成', tone: 'warning' },
      COMPLETED_WITH_UNKNOWN: { label: '部分结果未知', tone: 'warning' },
      FAILED: { label: '未完成', tone: 'danger' },
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
    return `部分${business}查询结果无法确认；已完成类型已经更新，结果未知类型没有改动。`;
  }
  return item.status === 'COMPLETED'
    ? '系统已完成自动校验，并更新本机构当前目录。'
    : '等待系统处理。';
}

/** @param item 失败批次 @return 面向业务人员的失败归类 */
function failureTitle(item: MasterDataBatchSummary) {
  if (item.failureCode === 'HIS_BUSINESS_FAILURE') return 'HIS 未授予目录数据权限';
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

/** 格式化UTC接口时间为本地显示时间。 */
function formatTime(value: string | null) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

/** 按毫秒、秒或分秒显示已经结束的批次耗时。 */
function formatDuration(item: MasterDataBatchSummary) {
  if (!item.startedAt) return '尚未开始';
  if (!item.finishedAt) return '进行中';
  const milliseconds = Math.max(
    0,
    new Date(item.finishedAt).getTime() - new Date(item.startedAt).getTime(),
  );
  if (milliseconds < 1000) return `${milliseconds}毫秒`;
  const seconds = Math.floor(milliseconds / 1000);
  if (seconds < 60) return `${seconds}秒`;
  return `${Math.floor(seconds / 60)}分${seconds % 60}秒`;
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

/** @param item 同步批次 @return 本次实际处理的目录范围 */
function batchScopeDescription(item: MasterDataBatchSummary) {
  return item.category === 'MEDICAL_DIRECTORY' ? '中药、西药、诊疗、耗材' : '科室、医生、病区、床位';
}

/** @param item 同步批次 @return 更新策略的准确业务说明 */
function currentDataExplanation(item: MasterDataBatchSummary) {
  return item.category === 'MEDICAL_DIRECTORY'
    ? '系统先核对100-005声明数量，再分页取得100-004数据；完整通过校验的类型直接更新当前目录。来源时间范围未证明为全量快照，因此本次不会按未返回数据标记无效。'
    : '本次读取科室、病区、床位和人员目录；完整返回且通过校验的部分已直接更新为当前数据。';
}

onMounted(() => {
  void Promise.all([
    loadBatches(),
    loadOrganizationOptions(),
    loadSyncSources(),
  ]);
});
onBeforeUnmount(() => {
  mounted = false;
  listController?.abort();
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
    <div v-if="error && !isLoading" class="feedback danger" role="alert">
      <AlertCircle :size="18" /><span
        ><strong>{{ error.message }}</strong
        ><small v-if="error.requestId"
          >请求编号：{{ error.requestId }}</small
        ></span
      ><button class="text-button" type="button" @click="loadBatches(true)">
        重试
      </button>
    </div>

    <form class="work-toolbar batch-toolbar" @submit.prevent="applyFilters">
      <div class="batch-filter-row">
        <input
          v-model="requestKeyFilter"
          class="batch-request-search"
          maxlength="64"
          placeholder="请求标识"
          aria-label="请求标识"
        />
        <select v-model="organizationFilter" aria-label="机构范围">
          <option value="">全部机构和公共目录</option>
          <option
            v-for="item in visibleOrganizationOptions"
            :key="item.code"
            :value="item.code"
          >
            {{ item.name }}
          </option>
        </select>
        <select v-model="categoryFilter" aria-label="数据类别">
          <option value="">全部数据类别</option>
          <option
            v-for="category in masterDataCategories"
            :key="category"
            :value="category"
          >
            {{ masterDataCategoryLabels[category] }}
          </option>
        </select>
        <select v-model="statusFilter" aria-label="批次状态">
          <option value="">全部状态</option>
          <option
            v-for="status in masterDataBatchStatuses"
            :key="status"
            :value="status"
          >
            {{ statusPresentation(status).label }}
          </option>
        </select>
      </div>
      <div class="batch-action-row">
        <button class="work-quiet-button" type="submit">查询</button>
        <button class="work-quiet-button" type="button" @click="clearFilters">
          清除
        </button>
        <span>共 {{ total }} 个批次</span>
        <button
          class="work-quiet-button"
          type="button"
          :disabled="isRefreshing"
          @click="loadBatches(true)"
        >
          <RefreshCw :size="15" :class="{ spinning: isRefreshing }" />刷新
        </button>
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
          @click="openCreator"
        >
          <Plus :size="15" />发起同步
        </button>
      </div>
    </form>

    <section
      v-if="canStart && !isSyncSourceLoading && !hasSyncSource"
      class="sync-readiness-notice"
      aria-live="polite"
    >
      <AlertCircle :size="18" />
      <div>
        <strong>当前没有可发起同步的机构</strong>
        <span>请在“外部系统”保存并启用该机构的基层 HIS 接口和接入信息。保存后系统自动确认；确认成功后，这里即可发起同步。</span>
      </div>
      <button class="work-quiet-button" type="button" @click="openInterfaceConfiguration">
        查看接口配置
      </button>
    </section>

    <section
      class="prototype-section work-table-section batch-table-section action-column-table"
    >
      <div v-if="isLoading" class="page-state" aria-live="polite">
        <LoaderCircle class="spinning" :size="27" /><strong
          >正在读取同步批次</strong
        ><span>请稍候</span>
      </div>
      <div v-else-if="!error && total === 0" class="page-state">
        <Database :size="29" /><strong>当前条件下没有同步批次</strong
        ><span>{{
          canStart && hasSyncOption
            ? '选择已配置HIS接口的机构和已开放业务，发起首次同步。'
            : '请先确认该机构的基层HIS接口配置；本页不会重复维护机构资料。'
        }}</span>
      </div>
      <div v-else-if="batches.length" class="table-scroll">
        <table class="work-table">
          <thead>
            <tr>
              <th>批次与同步范围</th>
              <th>取得结果</th>
              <th>状态与处理结果</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in batches" :key="item.id">
              <td>
                <button
                  class="work-row-link"
                  type="button"
                  @click="openReview(item.id)"
                >
                  {{ item.batchNo }}</button
                ><strong>{{ scopeLabel(item) }}</strong
                ><small
                  >{{ masterDataCategoryLabels[item.category] }} ·
                  {{ environmentLabel(item) }}</small
                >
              </td>
              <td>
                <strong>{{
                  item.countTradeCode
                    ? `${item.dataTradeCode} / ${item.countTradeCode}`
                    : item.dataTradeCode
                }}</strong
                ><small
                  >取得 {{ item.counts.returned }} 条{{
                    item.counts.declared === null
                      ? ' · 来源未提供总数'
                      : ` / 声明 ${item.counts.declared} 条`
                  }}</small
                >
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
                ><small
                  >{{ formatTime(item.startedAt) }} ·
                  {{ formatDuration(item) }}</small
                >
                <small
                  v-if="item.status === 'FAILED' || item.status === 'RESULT_UNKNOWN'"
                  class="batch-result-note"
                  :title="resultSummary(item)"
                  >{{ resultSummary(item) }}</small
                >
                <small v-else class="batch-result-note"
                  >由系统自动校验；未完成类型不改变当前数据</small
                >
              </td>
              <td class="batch-row-actions">
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
                <button
                  class="icon-button"
                  type="button"
                  aria-label="查看批次详情"
                  title="查看详情"
                  @click="openReview(item.id)"
                >
                  <ChevronRight :size="16" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <AdminPagination
        v-if="!isLoading && total > 0"
        :total="total"
        :page="page"
        :page-size="pageSize"
        @update:page="changePage"
        @update:page-size="changePageSize"
      />
    </section>

    <div
      v-if="selected"
      class="work-drawer-backdrop"
      @mousedown.self="selected = null"
    >
      <aside
        class="work-drawer batch-detail-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="batch-detail-title"
      >
        <header class="work-drawer-header">
          <div>
            <small>同步记录 · {{ masterDataCategoryLabels[selected.category] }}</small>
            <h2 id="batch-detail-title">同步结果</h2>
          </div>
          <button
            class="prototype-icon"
            type="button"
            aria-label="关闭"
            @click="selected = null"
          >
            ×
          </button>
        </header>
        <div class="work-drawer-sub">
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
        </div>
        <div class="work-drawer-body">
          <div v-if="isDetailLoading" class="page-state">
            <LoaderCircle class="spinning" :size="24" /><span
              >正在读取最新事实</span
            >
          </div>
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
              v-else
              :results="medicalDirectoryResults"
              :loading="isDirectoryResultsLoading"
            />
            <section class="batch-detail-section">
              <h3>本次调用</h3>
              <dl>
                <div>
                  <dt>同步机构</dt>
                  <dd>{{ scopeLabel(selected) }}</dd>
                </div>
                <div>
                  <dt>接口环境 / 交易</dt>
                  <dd>
                    {{ environmentLabel(selected) }} · {{ selected.dataTradeCode }}<template v-if="selected.countTradeCode"> / {{ selected.countTradeCode }}</template>
                  </dd>
                </div>
                <div>
                  <dt>处理耗时</dt>
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
        </div>
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
        <footer class="work-drawer-footer">
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
        </footer>
      </aside>
    </div>

    <StartBatchDrawer
      v-if="isCreatorOpen"
      v-model:form="form"
      :options="syncOptions"
      :is-saving="isSaving"
      :error="createError"
      :form-error="formError"
      @close="isCreatorOpen = false"
      @submit="submitBatch"
      @reload="reloadCreatedBatch"
      @configure="openInterfaceConfiguration"
    />
  </section>
</template>

<style scoped>
.master-batch-page {
  min-width: 0;
  color: #293a43;
}
.batch-toolbar {
  min-width: 0;
  display: grid;
  gap: 10px;
  padding: 12px;
}
.batch-filter-row {
  min-width: 0;
  display: grid;
  grid-template-columns:
    minmax(150px, 1.1fr)
    minmax(210px, 1.5fr)
    minmax(145px, 0.85fr)
    minmax(135px, 0.8fr);
  gap: 9px;
}
.batch-filter-row > * {
  min-width: 0;
}
.batch-action-row {
  min-width: 0;
  padding-top: 10px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9px;
  border-top: 1px solid #edf0f1;
}
.batch-action-row > span {
  margin-left: 4px;
  margin-right: auto;
  color: #718087;
  font-size: 11px;
  white-space: nowrap;
}
.batch-request-search {
  width: 100%;
  min-width: 0;
  height: 36px;
  padding: 0 10px;
  border: 1px solid #ccd7da;
  border-radius: 5px;
  font-size: 12px;
}
.batch-toolbar select {
  width: 100%;
  max-width: none;
}
.batch-table-section {
  min-height: 420px;
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
.table-scroll {
  overflow-x: auto;
}
.batch-table-section .work-table {
  min-width: 760px;
  table-layout: fixed;
}
.batch-table-section th:nth-child(1) {
  width: 34%;
}
.batch-table-section th:nth-child(2) {
  width: 24%;
}
.batch-table-section th:nth-child(3) {
  width: 30%;
}
.batch-table-section th:nth-child(4) {
  width: 12%;
}
.batch-table-section td {
  overflow: hidden;
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
.batch-table-section td:first-child strong {
  display: block;
  margin-top: 4px;
}
.batch-result-note {
  display: block;
  color: #74858d;
}
.batch-result-note.warning {
  color: #8a641f;
}
.page-state {
  min-height: 300px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 8px;
  color: #6f8088;
}
.page-state strong {
  color: #344953;
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
@media (max-width: 1180px) {
  .batch-filter-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .batch-filter-row {
    grid-template-columns: minmax(0, 1fr);
  }
  .batch-action-row {
    align-items: stretch;
    flex-wrap: wrap;
  }
  .batch-action-row > span {
    order: -1;
    width: 100%;
    margin: 0;
  }
  .count-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
