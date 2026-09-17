<!-- 数据字典页面：接入真实类型与字典项接口，读写均保留后端并发版本。 -->
<script setup lang="ts">
import { AlertCircle, BookOpen, Check, ChevronRight, LoaderCircle, Pencil, Plus, RefreshCw, Search, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createDictionaryItem, createDictionaryType, listDictionaryItems, listDictionaryTypes,
  updateDictionaryItem, updateDictionaryType,
} from '@/api/system/configuration'
import type { DictionaryItem, DictionaryType } from '@/api/system/configuration'
import { authState, hasPermission } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'
import DictionaryEditorDrawer from './components/DictionaryEditorDrawer.vue'
import type { DictionaryEditorKind } from './components/DictionaryEditorDrawer.vue'
import {
  dictionaryItemToForm, dictionaryTypeToForm, emptyDictionaryItemForm, emptyDictionaryTypeForm,
  toCreateDictionaryItemInput, toCreateDictionaryTypeInput, toUpdateDictionaryItemInput,
  toUpdateDictionaryTypeInput, validateDictionaryItemForm, validateDictionaryTypeForm,
} from './form'

const router = useRouter()
const types = ref<DictionaryType[]>([])
const items = ref<DictionaryItem[]>([])
const selectedType = ref<DictionaryType | null>(null)
const selectedItem = ref<DictionaryItem | null>(null)
const editorKind = ref<DictionaryEditorKind | null>(null)
const typeForm = ref(emptyDictionaryTypeForm())
const itemForm = ref(emptyDictionaryItemForm())
const query = ref('')
const isLoading = ref(true)
const isItemsLoading = ref(false)
const isRefreshing = ref(false)
const isSaving = ref(false)
const error = ref<ApiClientError | null>(null)
const operationError = ref<ApiClientError | null>(null)
const formError = ref('')
const notice = ref('')
let pageController: AbortController | null = null
let itemController: AbortController | null = null
let mounted = true

const canWrite = computed(() => hasPermission('configuration:write'))
const filteredTypes = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  return types.value.filter(type => !keyword || [type.typeCode, type.typeName, type.description].some(value => value.toLocaleLowerCase('zh-CN').includes(keyword)))
})
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
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取数据字典')
    if (apiError.code !== 'REQUEST_ABORTED' && !await handleUnauthorized(apiError)) error.value = apiError
  } finally { if (pageController === current && mounted) { isLoading.value = false; isRefreshing.value = false } }
}

async function selectType(type: DictionaryType) {
  selectedType.value = type; items.value = []; itemController?.abort(); const current = new AbortController(); itemController = current; isItemsLoading.value = true; error.value = null
  try {
    const rows = await listDictionaryItems(type.id, current.signal)
    if (!mounted || current.signal.aborted || selectedType.value?.id !== type.id) return
    items.value = rows
  } catch (caught) {
    const apiError = asApiError(caught, '无法读取字典项')
    if (apiError.code !== 'REQUEST_ABORTED' && !await handleUnauthorized(apiError)) error.value = apiError
  } finally { if (itemController === current && mounted) isItemsLoading.value = false }
}

function openTypeCreate() { typeForm.value = emptyDictionaryTypeForm(); operationError.value = null; formError.value = ''; editorKind.value = 'type-create' }
function openTypeEdit(type: DictionaryType) { selectedType.value = type; typeForm.value = dictionaryTypeToForm(type); operationError.value = null; formError.value = ''; editorKind.value = 'type-edit' }
function openItemCreate() { if (!selectedType.value) return; itemForm.value = emptyDictionaryItemForm(); selectedItem.value = null; operationError.value = null; formError.value = ''; editorKind.value = 'item-create' }
function openItemEdit(item: DictionaryItem) { selectedItem.value = item; itemForm.value = dictionaryItemToForm(item); operationError.value = null; formError.value = ''; editorKind.value = 'item-edit' }
function closeEditor() { if (!isSaving.value) editorKind.value = null }

async function submitEditor() {
  const kind = editorKind.value
  if (!kind || !canWrite.value || isSaving.value) return
  const isType = kind.startsWith('type'); const isCreating = kind.endsWith('create')
  formError.value = isType ? validateDictionaryTypeForm(typeForm.value, isCreating) ?? '' : validateDictionaryItemForm(itemForm.value, isCreating) ?? ''
  if (formError.value) return
  isSaving.value = true; operationError.value = null
  try {
    if (kind === 'type-create') {
      const created = await createDictionaryType(toCreateDictionaryTypeInput(typeForm.value)); types.value.push(created); notice.value = `${created.typeName}已创建。`
    } else if (kind === 'type-edit' && selectedType.value) {
      const updated = await updateDictionaryType(selectedType.value.id, toUpdateDictionaryTypeInput(typeForm.value)); replaceType(updated); notice.value = `${updated.typeName}已保存。`
    } else if (kind === 'item-create' && selectedType.value) {
      const created = await createDictionaryItem(selectedType.value.id, toCreateDictionaryItemInput(itemForm.value)); items.value.push(created); sortItems(); notice.value = `${created.itemLabel}已创建。`
    } else if (kind === 'item-edit' && selectedItem.value) {
      const updated = await updateDictionaryItem(selectedItem.value.id, toUpdateDictionaryItemInput(itemForm.value)); replaceItem(updated); sortItems(); notice.value = `${updated.itemLabel}已保存。`
    }
    editorKind.value = null
  } catch (caught) {
    const apiError = asApiError(caught, '无法保存数据字典')
    if (!await handleUnauthorized(apiError)) operationError.value = apiError
  } finally { isSaving.value = false }
}

function replaceType(updated: DictionaryType) { const index = types.value.findIndex(type => type.id === updated.id); if (index >= 0) types.value[index] = updated; selectedType.value = updated }
function replaceItem(updated: DictionaryItem) { const index = items.value.findIndex(item => item.id === updated.id); if (index >= 0) items.value[index] = updated }
function sortItems() { items.value.sort((left, right) => left.sortOrder - right.sortOrder || left.itemCode.localeCompare(right.itemCode)) }

onMounted(() => loadTypes())
onBeforeUnmount(() => { mounted = false; pageController?.abort(); itemController?.abort() })
</script>

<template>
  <section class="content dictionary-page">
    <div v-if="notice" class="feedback success" role="status"><Check :size="18" /><span>{{ notice }}</span><button aria-label="关闭提示" @click="notice = ''"><X :size="16" /></button></div>
    <div v-if="error && !isLoading" class="feedback danger" role="alert"><AlertCircle :size="18" /><span>{{ error.message }}</span><button class="prototype-text-button" type="button" @click="loadTypes(true)"><RefreshCw :size="15" />重试</button></div>
    <div class="work-toolbar"><label class="prototype-search"><Search :size="16" /><input v-model="query" type="search" placeholder="类型名称、代码或用途" aria-label="搜索字典类型" /></label><span>{{ filteredTypes.length }} / {{ types.length }} 个类型</span><button class="work-quiet-button" type="button" :disabled="isRefreshing" @click="loadTypes(true)"><RefreshCw :size="15" :class="{ spinning: isRefreshing }" />刷新</button><button v-if="canWrite" class="prototype-button" type="button" @click="openTypeCreate"><Plus :size="15" />新增类型</button></div>
    <section class="prototype-section work-table-section dictionary-workspace">
      <div v-if="isLoading" class="page-state"><LoaderCircle class="spinning" :size="28" /><strong>正在加载数据字典</strong></div>
      <div v-else-if="!error && types.length === 0" class="page-state"><BookOpen :size="30" /><strong>平台暂无字典类型</strong><span v-if="canWrite">通过“新增类型”建立受控字典，再维护其字典项。</span></div>
      <template v-else>
        <aside class="dictionary-types"><button v-for="type in filteredTypes" :key="type.id" type="button" :class="{ active: selectedType?.id === type.id }" @click="selectType(type)"><span><strong>{{ type.typeName }}</strong><small>{{ type.typeCode }}</small></span><span class="prototype-tag" :class="type.enabled ? 'success' : 'neutral'">{{ type.enabled ? '启用' : '停用' }}</span><ChevronRight :size="15" /></button><div v-if="filteredTypes.length === 0" class="prototype-empty">没有符合条件的类型</div></aside>
        <main class="dictionary-items"><div v-if="!selectedType" class="page-state compact"><BookOpen :size="27" /><strong>选择一个字典类型</strong><span>查看和维护其字典项。</span></div><template v-else><header><div><small>{{ selectedType.typeCode }}</small><h2>{{ selectedType.typeName }}</h2><p>{{ selectedType.description }}</p></div><div><button v-if="canWrite" class="work-quiet-button" type="button" @click="openTypeEdit(selectedType)"><Pencil :size="14" />编辑类型</button><button v-if="canWrite && selectedType.enabled" class="prototype-button" type="button" @click="openItemCreate"><Plus :size="14" />新增字典项</button></div></header><div v-if="isItemsLoading" class="page-state compact"><LoaderCircle class="spinning" :size="25" /><strong>正在加载字典项</strong></div><div v-else-if="items.length === 0" class="page-state compact"><BookOpen :size="26" /><strong>该类型暂无字典项</strong></div><div v-else class="prototype-table-wrap"><table class="work-table"><thead><tr><th>字典项</th><th>排序</th><th>状态</th><th><span class="visually-hidden">操作</span></th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong>{{ item.itemLabel }}</strong><small>{{ item.itemCode }}</small></td><td>{{ item.sortOrder }}</td><td><span class="prototype-tag" :class="item.enabled ? 'success' : 'neutral'">{{ item.enabled ? '已启用' : '已停用' }}</span></td><td><button v-if="canWrite" class="prototype-icon" type="button" aria-label="编辑字典项" @click="openItemEdit(item)"><Pencil :size="15" /></button></td></tr></tbody></table></div></template></main>
      </template>
    </section>
    <DictionaryEditorDrawer v-if="editorKind" v-model:type-form="typeForm" v-model:item-form="itemForm" :kind="editorKind" :title="editorTitle" :is-saving="isSaving" :error="operationError" :form-error="formError" @close="closeEditor" @submit="submitEditor" />
  </section>
</template>

<style scoped>
.dictionary-page { display: grid; gap: 0; }.dictionary-workspace { min-height: 530px; display: grid; grid-template-columns: minmax(240px, 31%) minmax(0, 1fr); overflow: hidden; }.dictionary-types { border-right: 1px solid #dfe5e7; background: #fafbfb; }.dictionary-types > button { width: 100%; min-height: 68px; padding: 11px 13px; border: 0; border-bottom: 1px solid #e4e8e9; background: transparent; display: flex; align-items: center; gap: 9px; text-align: left; }.dictionary-types > button:hover, .dictionary-types > button.active { background: #edf7f4; }.dictionary-types > button.active { box-shadow: inset 3px 0 #187a6d; }.dictionary-types button > span:first-child { min-width: 0; flex: 1; }.dictionary-types strong, .dictionary-types small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.dictionary-types strong { font-size: 12px; }.dictionary-types small { margin-top: 4px; color: #77878d; font-size: 11px; }.dictionary-items { min-width: 0; background: white; }.dictionary-items > header { min-height: 104px; padding: 17px 18px; border-bottom: 1px solid #dfe5e7; display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.dictionary-items header small { color: #718188; font-size: 10px; }.dictionary-items h2 { margin: 3px 0; font-size: 17px; }.dictionary-items p { max-width: 620px; margin: 0; color: #738188; font-size: 11px; line-height: 1.5; }.dictionary-items header > div:last-child { display: flex; gap: 8px; }.feedback { min-height: 46px; margin-bottom: 12px; padding: 9px 12px; border: 1px solid; border-radius: 5px; display: flex; align-items: center; gap: 9px; font-size: 12px; }.feedback span { flex: 1; }.feedback button { border: 0; background: transparent; }.feedback.success { border-color: #bfddce; background: #ecf7f0; color: #226c49; }.feedback.danger { border-color: #e1c0b7; background: #fbf1ee; color: #8d432e; }.page-state { min-height: 420px; grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #33745c; text-align: center; }.page-state.compact { min-height: 320px; }.page-state span { color: #75828c; font-size: 12px; }.spinning { animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }@media (max-width: 760px) { .dictionary-workspace { grid-template-columns: 1fr; }.dictionary-types { max-height: 240px; overflow-y: auto; border-right: 0; border-bottom: 1px solid #dfe5e7; }.dictionary-items > header { flex-direction: column; }.dictionary-items header > div:last-child { width: 100%; } }
</style>
