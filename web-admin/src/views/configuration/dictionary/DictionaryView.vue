<!-- 数据字典页面：接入真实类型与字典项接口，读写均保留后端并发版本。 -->
<script setup lang="ts">
import { AlertCircle, BookOpen, ChevronRight, LoaderCircle, Pencil, Plus, RefreshCw, Search, Trash2 } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createDictionaryItem, createDictionaryType, deleteDictionaryItem, deleteDictionaryType,
  listDictionaryItems, listDictionaryTypes,
  updateDictionaryItem, updateDictionaryType,
} from '@/api/system/configuration'
import type { DictionaryItem, DictionaryType } from '@/api/system/configuration'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError, asUncertainWriteError } from '@/utils/request'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import AdminPagination from '@/components/AdminPagination.vue'
import AdminTableFrame from '@/components/AdminTableFrame.vue'
import ListRowActions from '@/components/ListRowActions.vue'
import AuditAwareSuccess from '@/components/AuditAwareSuccess.vue'
import ListQueryToolbar from '@/components/ListQueryToolbar.vue'
import PageState from '@/components/PageState.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { useConfirmationDialog } from '@/composables/useConfirmationDialog'
import { formatLocalDateTime } from '@/utils/managementDisplay'
import DictionaryEditorDrawer from './components/DictionaryEditorDrawer.vue'
import type { DictionaryEditorKind } from './components/DictionaryEditorDrawer.vue'
import {
  dictionaryItemToForm, dictionaryTypeToForm, emptyDictionaryItemForm, emptyDictionaryTypeForm,
  toCreateDictionaryItemInput, toCreateDictionaryTypeInput, toUpdateDictionaryItemInput,
  toUpdateDictionaryTypeInput, validateDictionaryItemForm, validateDictionaryTypeForm,
} from './form'

const router = useRouter()
const route = useRoute()
const types = ref<DictionaryType[]>([])
const items = ref<DictionaryItem[]>([])
const selectedType = ref<DictionaryType | null>(null)
const selectedItem = ref<DictionaryItem | null>(null)
const editorKind = ref<DictionaryEditorKind | null>(null)
const typeForm = ref(emptyDictionaryTypeForm())
const itemForm = ref(emptyDictionaryItemForm())
const query = ref(typeof route.query.query === 'string' ? route.query.query : '')
const itemQuery = ref(typeof route.query.itemQuery === 'string' ? route.query.itemQuery : '')
const itemStatusFilter = ref<'all' | 'enabled' | 'disabled'>('all')
const isLoading = ref(true)
const isItemsLoading = ref(false)
const isRefreshing = ref(false)
const isSaving = ref(false)
const deletingId = ref<string | null>(null)
const error = ref<ApiClientError | null>(null)
const itemError = ref<ApiClientError | null>(null)
const operationError = ref<ApiClientError | null>(null)
const deleteError = ref<ApiClientError | null>(null)
const formError = ref('')
const notice = ref('')
const auditTarget = ref<{ targetType: string; targetId: string } | null>(null)
const editorSnapshot = ref('')
let pageController: AbortController | null = null
let itemController: AbortController | null = null
let mounted = true

const canWrite = computed(() => hasPermission('configuration:write'))
const filteredTypes = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  return types.value.filter(type => !keyword || [type.typeCode, type.typeName, type.description].some(value => value.toLocaleLowerCase('zh-CN').includes(keyword)))
})
watch(filteredTypes, visibleTypes => {
  const selected = selectedType.value
  if (!selected || visibleTypes.some(type => type.id === selected.id)) return
  // 隐藏当前类型时清空详情，并取消旧请求，避免结果与筛选上下文脱节。
  selectedType.value = null
  selectedItem.value = null
  items.value = []
  itemError.value = null
  itemQuery.value = ''
  itemStatusFilter.value = 'all'
  itemController?.abort()
  isItemsLoading.value = false
})
const itemRows = computed(() => {
  const keyword = itemQuery.value.trim().toLocaleLowerCase('zh-CN')
  return items.value.filter(item => {
    if (itemStatusFilter.value === 'enabled' && !item.enabled) return false
    if (itemStatusFilter.value === 'disabled' && item.enabled) return false
    return !keyword || [item.itemCode, item.itemLabel].some(value => value.toLocaleLowerCase('zh-CN').includes(keyword))
  })
})
const enabledItemCount = computed(() => items.value.filter(item => item.enabled).length)
const duplicateSortOrders = computed(() => {
  const counts = new Map<number, number>()
  items.value.forEach(item => counts.set(item.sortOrder, (counts.get(item.sortOrder) ?? 0) + 1))
  return new Set([...counts].filter(([, count]) => count > 1).map(([sortOrder]) => sortOrder))
})
const { page: typePage, pageSize: typePageSize, pagedRows: pagedTypes } = useClientPagination(filteredTypes)
const { page: itemPage, pageSize: itemPageSize, pagedRows: pagedItems } = useClientPagination(itemRows)
const { request: confirmationRequest, confirm, resolve: resolveConfirmation } = useConfirmationDialog()

function applyTypeFilter() {
  typePage.value = 1
}

function resetTypeFilter() {
  query.value = ''
  typePage.value = 1
}
const editorTitle = computed(() => {
  if (editorKind.value === 'type-create') return '新增字典类型'
  if (editorKind.value === 'type-edit') return selectedType.value?.typeName ?? '编辑字典类型'
  if (editorKind.value === 'item-create') return `新增${selectedType.value?.typeName ?? ''}字典项`
  return selectedItem.value?.itemLabel ?? '编辑字典项'
})

function asApiError(caught: unknown, message: string) { return caught instanceof ApiClientError ? caught : new ApiClientError('UNKNOWN_ERROR', message, 0) }
async function handleUnauthorized(apiError: ApiClientError) {
  if (apiError.status !== 401) return false
  authState.user = null; authState.isInitialized = true
  await router.replace({ path: '/login', query: { redirect: '/dictionaries' } })
  return true
}

async function loadTypes(background = false) {
  pageController?.abort(); const current = new AbortController(); pageController = current
  if (background) isRefreshing.value = true; else isLoading.value = true
  error.value = null
  try {
    const rows = await listDictionaryTypes(current.signal)
    if (!mounted || current.signal.aborted) return
    types.value = rows
    if (selectedType.value) selectedType.value = rows.find(type => type.id === selectedType.value?.id) ?? null
    else if (typeof route.query.typeCode === 'string') {
      const linkedType = rows.find(type => type.typeCode === route.query.typeCode)
      if (linkedType) await selectType(linkedType, false)
    }
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取数据字典')
    if (apiError.code !== 'REQUEST_ABORTED' && !await handleUnauthorized(apiError)) error.value = apiError
  } finally { if (pageController === current && mounted) { isLoading.value = false; isRefreshing.value = false } }
}

async function selectType(type: DictionaryType, clearItemFilter = true) {
  selectedType.value = type; items.value = []; itemError.value = null; if (clearItemFilter) itemQuery.value = ''; itemStatusFilter.value = 'all'; itemController?.abort(); const current = new AbortController(); itemController = current; isItemsLoading.value = true; error.value = null
  try {
    const rows = await listDictionaryItems(type.id, current.signal)
    if (!mounted || current.signal.aborted || selectedType.value?.id !== type.id) return
    items.value = rows
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取字典项')
    if (apiError.code !== 'REQUEST_ABORTED' && !await handleUnauthorized(apiError)) itemError.value = apiError
  } finally { if (itemController === current && mounted) isItemsLoading.value = false }
}

function currentEditorSnapshot() { return JSON.stringify(editorKind.value?.startsWith('type') ? typeForm.value : itemForm.value) }
function captureEditorSnapshot() { editorSnapshot.value = currentEditorSnapshot() }
function openTypeCreate() { typeForm.value = emptyDictionaryTypeForm(); operationError.value = null; formError.value = ''; editorKind.value = 'type-create'; captureEditorSnapshot() }
function openTypeEdit(type: DictionaryType) { selectedType.value = type; typeForm.value = dictionaryTypeToForm(type); operationError.value = null; formError.value = ''; editorKind.value = 'type-edit'; captureEditorSnapshot() }
function openItemCreate() { if (!selectedType.value || itemError.value) return; itemForm.value = emptyDictionaryItemForm(); selectedItem.value = null; operationError.value = null; formError.value = ''; editorKind.value = 'item-create'; captureEditorSnapshot() }
function openItemEdit(item: DictionaryItem) { selectedItem.value = item; itemForm.value = dictionaryItemToForm(item); operationError.value = null; formError.value = ''; editorKind.value = 'item-edit'; captureEditorSnapshot() }
async function closeEditor() {
  if (isSaving.value) return
  if (currentEditorSnapshot() !== editorSnapshot.value && !await confirm({
    title: '放弃未保存的字典修改？',
    message: '关闭后，本次修改的字典类型或字典项不会保存。',
    confirmLabel: '放弃修改',
    danger: true,
  })) return
  editorKind.value = null
}

async function reloadAfterConflict() {
  const kind = editorKind.value
  if (!kind) return
  if (kind.startsWith('item') && selectedType.value) {
    const selectedItemId = selectedItem.value?.id
    await selectType(selectedType.value, false)
    if (itemError.value) return
    if (kind === 'item-create') {
      const saved = items.value.find(item => item.itemCode === itemForm.value.itemCode.trim().toUpperCase())
      if (saved) {
        notice.value = `${saved.itemLabel}已经保存，已按后台最新数据刷新。`
        auditTarget.value = { targetType: 'DICTIONARY_ITEM', targetId: `${selectedType.value.typeCode}:${saved.itemCode}` }
        editorKind.value = null
      } else operationError.value = null
      return
    }
    const latest = items.value.find(item => item.id === selectedItemId)
    if (!latest) {
      editorKind.value = null
      return
    }
    selectedItem.value = latest
    itemForm.value = dictionaryItemToForm(latest)
    operationError.value = null
    captureEditorSnapshot()
    return
  }
  const selectedTypeId = selectedType.value?.id
  await loadTypes(true)
  if (error.value) return
  if (kind === 'type-create') {
    const saved = types.value.find(type => type.typeCode === typeForm.value.typeCode.trim().toUpperCase())
    if (saved) {
      selectedType.value = saved
      notice.value = `${saved.typeName}已经保存，已按后台最新数据刷新。`
      auditTarget.value = { targetType: 'DICTIONARY_TYPE', targetId: saved.typeCode }
      editorKind.value = null
    } else operationError.value = null
    return
  }
  const latest = types.value.find(type => type.id === selectedTypeId)
  if (!latest) {
    editorKind.value = null
    return
  }
  selectedType.value = latest
  typeForm.value = dictionaryTypeToForm(latest)
  operationError.value = null
  captureEditorSnapshot()
}

async function submitEditor() {
  const kind = editorKind.value
  if (!kind || !canWrite.value || isSaving.value) return
  const isType = kind.startsWith('type'); const isCreating = kind.endsWith('create')
  formError.value = isType ? validateDictionaryTypeForm(typeForm.value, isCreating) ?? '' : validateDictionaryItemForm(itemForm.value, isCreating) ?? ''
  if (formError.value) return
  const isDisablingType = kind === 'type-edit' && selectedType.value?.enabled && !typeForm.value.enabled
  const isDisablingItem = kind === 'item-edit' && selectedItem.value?.enabled && !itemForm.value.enabled
  if (isDisablingType && !await confirm({
    title: '停用字典类型？',
    message: `停用“${selectedType.value?.typeName}”后，其全部字典项将不再用于新的业务录入。`,
    confirmLabel: '确认停用',
    danger: true,
  })) return
  if (isDisablingItem && !await confirm({
    title: '停用字典项？',
    message: `停用“${selectedItem.value?.itemLabel}”后，新的业务录入将不能再选择该项，历史数据不受影响。`,
    confirmLabel: '确认停用',
    danger: true,
  })) return
  isSaving.value = true; operationError.value = null
  try {
    if (kind === 'type-create') {
      const created = await createDictionaryType(toCreateDictionaryTypeInput(typeForm.value)); types.value.push(created); notice.value = `${created.typeName}已创建。`; auditTarget.value = { targetType: 'DICTIONARY_TYPE', targetId: created.typeCode }
    } else if (kind === 'type-edit' && selectedType.value) {
      const updated = await updateDictionaryType(selectedType.value.id, toUpdateDictionaryTypeInput(typeForm.value)); replaceType(updated); notice.value = `${updated.typeName}已保存。`; auditTarget.value = { targetType: 'DICTIONARY_TYPE', targetId: updated.typeCode }
    } else if (kind === 'item-create' && selectedType.value) {
      const created = await createDictionaryItem(selectedType.value.id, toCreateDictionaryItemInput(itemForm.value)); items.value.push(created); sortItems(); notice.value = `${created.itemLabel}已创建。`; auditTarget.value = { targetType: 'DICTIONARY_ITEM', targetId: `${selectedType.value.typeCode}:${created.itemCode}` }
    } else if (kind === 'item-edit' && selectedItem.value) {
      const updated = await updateDictionaryItem(selectedItem.value.id, toUpdateDictionaryItemInput(itemForm.value)); replaceItem(updated); sortItems(); notice.value = `${updated.itemLabel}已保存。`; auditTarget.value = { targetType: 'DICTIONARY_ITEM', targetId: String(updated.id) }
    }
    editorKind.value = null
  } catch (caught) {
    const apiError = asUncertainWriteError(asApiError(caught, '无法保存数据字典'))
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally { isSaving.value = false }
}

function replaceType(updated: DictionaryType) { const index = types.value.findIndex(type => type.id === updated.id); if (index >= 0) types.value[index] = updated; selectedType.value = updated }
function replaceItem(updated: DictionaryItem) { const index = items.value.findIndex(item => item.id === updated.id); if (index >= 0) items.value[index] = updated }
function sortItems() { items.value.sort((left, right) => left.sortOrder - right.sortOrder || left.itemCode.localeCompare(right.itemCode)) }

async function removeType() {
  const type = selectedType.value
  if (!type || !canWrite.value || deletingId.value || itemError.value) return
  deleteError.value = null
  if (items.value.length > 0) {
    deleteError.value = new ApiClientError(
      'DICTIONARY_TYPE_IN_USE',
      `“${type.typeName}”仍包含${items.value.length}个字典项。请先逐项确认是否被业务使用，并删除未使用的字典项。`,
      409,
    )
    return
  }
  if (!await confirm({
    title: '删除字典类型？',
    message: `确认删除字典类型“${type.typeName}”（${type.typeCode}）？删除后不能恢复。`,
    confirmLabel: '删除类型',
    danger: true,
  })) return
  deletingId.value = `type-${type.id}`
  try {
    await deleteDictionaryType(type.id, type.version)
    types.value = types.value.filter(row => row.id !== type.id)
    selectedType.value = null
    items.value = []
    itemError.value = null
    notice.value = `${type.typeName}已删除。`
    auditTarget.value = { targetType: 'DICTIONARY_TYPE', targetId: type.typeCode }
  } catch (caught) {
    const apiError = asUncertainWriteError(asApiError(caught, '无法删除字典类型'))
    if (!await handleUnauthorized(apiError)) deleteError.value = apiError
  } finally { deletingId.value = null }
}

async function removeItem(item: DictionaryItem) {
  const type = selectedType.value
  if (!type || !canWrite.value || deletingId.value) return
  deleteError.value = null
  if (!await confirm({
    title: '删除字典项？',
    message: `确认删除字典项“${item.itemLabel}”（${item.itemCode}）？后台会先检查业务数据引用；已被使用时不会删除。`,
    confirmLabel: '删除字典项',
    danger: true,
  })) return
  deletingId.value = `item-${item.id}`
  try {
    await deleteDictionaryItem(item.id, item.version)
    items.value = items.value.filter(row => row.id !== item.id)
    notice.value = `${item.itemLabel}未被业务数据使用，已删除。`
    auditTarget.value = { targetType: 'DICTIONARY_ITEM', targetId: `${type.typeCode}:${item.itemCode}` }
  } catch (caught) {
    const apiError = asUncertainWriteError(asApiError(caught, '无法删除字典项'))
    if (!await handleUnauthorized(apiError)) deleteError.value = apiError
  } finally { deletingId.value = null }
}

onMounted(() => loadTypes())
onBeforeUnmount(() => { mounted = false; pageController?.abort(); itemController?.abort() })
</script>

<template>
  <section class="content dictionary-page">
    <AuditAwareSuccess v-if="notice" :message="notice" :target-type="auditTarget?.targetType" :target-id="auditTarget?.targetId" @close="notice = ''; auditTarget = null" />
    <div v-if="error && !isLoading && types.length > 0" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ error.message }}</span><button class="prototype-text-button" type="button" @click="loadTypes(true)"><RefreshCw :size="15" />重试</button></div>
    <div v-if="deleteError" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ deleteError.message }}<small v-if="deleteError.requestId">请求编号：{{ deleteError.requestId }}</small></span><button class="prototype-text-button" type="button" @click="deleteError = null">关闭</button></div>
    <ListQueryToolbar :refreshing="isRefreshing" @query="applyTypeFilter" @reset="resetTypeFilter" @refresh="loadTypes(true)"><label class="prototype-search"><Search :size="16" /><input v-model="query" type="search" placeholder="类型名称、代码或用途" aria-label="搜索字典类型" /></label><template #actions><button v-if="canWrite" class="prototype-button" type="button" :disabled="isLoading || isRefreshing || Boolean(error)" @click="openTypeCreate"><Plus :size="15" />新增类型</button></template></ListQueryToolbar>
    <section class="prototype-section work-table-section dictionary-workspace action-column-table" :class="{ 'no-type-selected': !selectedType }">
      <PageState v-if="isLoading" kind="loading" title="正在加载数据字典" />
      <PageState v-else-if="error && types.length === 0" kind="error" title="暂时无法读取数据字典" :description="error.message" compact>
        <template #icon><AlertCircle :size="26" /></template>
        <template #actions><button class="work-quiet-button" type="button" @click="loadTypes()"><RefreshCw :size="15" />重试</button></template>
      </PageState>
      <PageState v-else-if="!error && types.length === 0" kind="empty" title="平台暂无字典类型" :description="canWrite ? '通过“新增类型”建立受控字典，再维护其字典项。' : ''">
        <template #icon><BookOpen :size="30" /></template>
      </PageState>
      <template v-else>
        <aside class="dictionary-types"><div class="dictionary-type-list"><button v-for="type in pagedTypes" :key="type.id" type="button" :class="{ active: selectedType?.id === type.id }" @click="selectType(type)"><span><strong>{{ type.typeName }}</strong><small>{{ type.typeCode }}</small></span><span class="prototype-tag" :class="type.enabled ? 'success' : 'neutral'">{{ type.enabled ? '启用' : '停用' }}</span><ChevronRight :size="15" /></button><div v-if="!error && filteredTypes.length === 0" class="prototype-empty">没有符合条件的类型</div></div><AdminPagination v-if="filteredTypes.length > 0" compact :total="filteredTypes.length" :page="typePage" :page-size="typePageSize" @update:page="typePage = $event" @update:page-size="typePageSize = $event" /></aside>
        <main v-if="selectedType" class="dictionary-items"><header><div><small>{{ selectedType.typeCode }}</small><h2>{{ selectedType.typeName }}</h2><p>{{ selectedType.description || '尚未填写用途说明，建议补充该字典在哪些页面使用。' }}</p><p v-if="!itemError" class="dictionary-meta">{{ enabledItemCount }} / {{ items.length }} 个字典项已启用 · 最近更新 {{ formatLocalDateTime(selectedType.updatedAt) }}</p></div><div><button v-if="canWrite" class="work-quiet-button" type="button" @click="openTypeEdit(selectedType)"><Pencil :size="14" />修改类型</button><button v-if="canWrite" class="work-danger-button" type="button" :disabled="deletingId !== null || Boolean(itemError)" :title="itemError ? '读取字典项失败，请重试后再删除类型' : '仅空类型可删除'" @click="removeType"><Trash2 :size="13" />{{ deletingId === `type-${selectedType.id}` ? '删除中…' : '删除类型' }}</button><button v-if="canWrite && selectedType.enabled" class="prototype-button" type="button" :disabled="Boolean(itemError)" :title="itemError ? '读取字典项失败，重试成功后再新增' : ''" @click="openItemCreate"><Plus :size="14" />新增字典项</button></div></header><PageState v-if="isItemsLoading" kind="loading" title="正在加载字典项" compact /><PageState v-else-if="itemError" kind="error" title="暂时无法读取字典项" :description="itemError.message" compact><template #icon><AlertCircle :size="26" /></template><template #actions><button class="work-quiet-button" type="button" @click="selectedType && selectType(selectedType, false)"><RefreshCw :size="15" />重试</button></template></PageState><PageState v-else-if="items.length === 0" kind="empty" title="该类型暂无字典项" description="确认该类型不再需要后，可使用“删除类型”。" compact><template #icon><BookOpen :size="26" /></template></PageState><template v-else><ListQueryToolbar class="dictionary-item-toolbar" filters-only filters-layout="search-with-filter"><label class="prototype-search"><Search :size="15" /><input v-model="itemQuery" type="search" placeholder="字典项名称或代码" aria-label="搜索字典项" /></label><select v-model="itemStatusFilter" aria-label="字典项状态"><option value="all">全部状态</option><option value="enabled">已启用</option><option value="disabled">已停用</option></select></ListQueryToolbar><PageState v-if="itemRows.length === 0" kind="empty" title="没有符合当前条件的字典项" compact /><template v-else><AdminTableFrame label="字典项列表" has-actions :pin-actions="false"><table class="work-table"><thead><tr><th>字典项</th><th>排序</th><th>状态</th><th>最近更新</th><th>操作</th></tr></thead><tbody><tr v-for="item in pagedItems" :key="item.id"><td><strong>{{ item.itemLabel }}</strong><small>{{ item.itemCode }}</small></td><td>{{ item.sortOrder }}<small v-if="duplicateSortOrders.has(item.sortOrder)" class="sort-warning">与其他字典项排序相同</small></td><td><span class="prototype-tag" :class="item.enabled ? 'success' : 'neutral'">{{ item.enabled ? '已启用' : '已停用' }}</span></td><td>{{ formatLocalDateTime(item.updatedAt) }}</td><td><ListRowActions label="字典项操作"><button v-if="canWrite" class="prototype-icon" type="button" aria-label="修改字典项" title="修改" @click="openItemEdit(item)"><Pencil :size="15" /></button><button v-if="canWrite" class="work-danger-button" type="button" :disabled="deletingId !== null" title="仅未被业务数据使用时可删除" @click="removeItem(item)"><Trash2 :size="13" aria-hidden="true" />{{ deletingId === `item-${item.id}` ? '删除中…' : '删除' }}</button></ListRowActions></td></tr></tbody></table></AdminTableFrame><AdminPagination :total="itemRows.length" :page="itemPage" :page-size="itemPageSize" @update:page="itemPage = $event" @update:page-size="itemPageSize = $event" /></template></template></main>
      </template>
    </section>
    <DictionaryEditorDrawer v-if="editorKind" v-model:type-form="typeForm" v-model:item-form="itemForm" :kind="editorKind" :title="editorTitle" :is-saving="isSaving" :error="operationError" :form-error="formError" @close="closeEditor" @submit="submitEditor" @reload="reloadAfterConflict" />
    <ConfirmationDialog v-if="confirmationRequest" :request="confirmationRequest" @confirm="resolveConfirmation(true)" @cancel="resolveConfirmation(false)" />
  </section>
</template>

<style scoped>
.dictionary-page { display: grid; gap: 0; }.dictionary-workspace { --action-column-width: 132px; display: grid; grid-template-columns: clamp(270px, 22vw, 330px) minmax(0, 1fr); overflow: hidden; }.dictionary-workspace.no-type-selected { grid-template-columns: minmax(0, 1fr); }.dictionary-types { min-width: 0; align-self: start; border-right: 1px solid #dfe5e7; background: #fafbfb; }.dictionary-workspace.no-type-selected .dictionary-types { border-right: 0; }.dictionary-type-list > button { width: 100%; min-height: 68px; padding: 11px 13px; border: 0; border-bottom: 1px solid #e4e8e9; background: transparent; display: flex; align-items: center; gap: 9px; text-align: left; }.dictionary-type-list > button:hover, .dictionary-type-list > button.active { background: #edf7f4; }.dictionary-type-list > button.active { box-shadow: inset 3px 0 #187a6d; }.dictionary-type-list button > span:first-child { min-width: 0; flex: 1; }.dictionary-type-list strong, .dictionary-type-list small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.dictionary-type-list strong { font-size: 12px; }.dictionary-type-list small { margin-top: 4px; color: #77878d; font-size: 11px; }.dictionary-items { min-width: 0; background: white; }.dictionary-items > header { min-height: 104px; padding: 17px 18px; border-bottom: 1px solid #dfe5e7; display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.dictionary-items header small { color: #718188; font-size: 10px; }.dictionary-items h2 { margin: 3px 0; font-size: 17px; }.dictionary-items p { max-width: 620px; margin: 0; color: #738188; font-size: 11px; line-height: 1.5; }.dictionary-items header > div:last-child { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }.feedback { min-height: 46px; margin-bottom: 12px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 9px; font-size: 12px; }.feedback span { flex: 1; }.feedback button { border: 0; background: transparent; }.feedback.success { border-color: #bfddce; background: #ecf7f0; color: #226c49; }.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }.dictionary-workspace > .page-state { grid-column: 1 / -1; }.spinning { animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }@media (max-width: 900px) { .dictionary-workspace { grid-template-columns: 1fr; }.dictionary-types { max-height: none; overflow: visible; border-right: 0; border-bottom: 1px solid #dfe5e7; }.dictionary-type-list { max-height: 260px; overflow-y: auto; }.dictionary-items > header { flex-direction: column; }.dictionary-items header > div:last-child { width: 100%; justify-content: flex-start; } }
.dictionary-meta { margin-top: 5px !important; color: #526d66 !important; }.dictionary-item-toolbar { padding: 10px 14px; border-bottom: 1px solid #e2e7e9; }.sort-warning { display: block; margin-top: 3px; color: #a45437; font-size: 10px; }


</style>
