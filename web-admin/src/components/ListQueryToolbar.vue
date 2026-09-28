<!-- 管理端列表的统一查询栏：页面提供筛选项和业务操作，查询、重置、业务操作与刷新按钮保持同组并自然换行；结果数由分页栏唯一展示。 -->
<script setup lang="ts">
import { RefreshCw } from 'lucide-vue-next'

withDefaults(defineProps<{
  /** 是否正在重新读取服务端数据。 */
  refreshing?: boolean
  /** 当前列表不可操作时同时禁用公共动作。 */
  disabled?: boolean
  /** 是否显示查询按钮。 */
  showQuery?: boolean
  /** 是否显示重置按钮。 */
  showReset?: boolean
  /** 是否显示刷新按钮。 */
  showRefresh?: boolean
  /** 仅显示筛选字段，用于无需查询、重置和刷新的嵌套列表筛选区。 */
  filtersOnly?: boolean
  /** 选择共享筛选区布局及其内建响应式断点。 */
  filtersLayout?: 'default' | 'search-with-selects' | 'search-with-three-selects' | 'search-with-four-selects' | 'search-with-filter' | 'search-full-width' | 'three-columns' | 'dense-grid'
}>(), {
  refreshing: false,
  disabled: false,
  showQuery: true,
  showReset: true,
  showRefresh: true,
  filtersOnly: false,
  filtersLayout: 'default',
})

const emit = defineEmits<{
  /** 页面确认当前筛选条件。 */
  query: []
  /** 页面恢复默认筛选条件。 */
  reset: []
  /** 页面重新读取服务端列表。 */
  refresh: []
}>()
</script>

<template>
  <div class="standard-list-toolbar-container">
    <form class="standard-list-toolbar" :class="{ 'standard-list-toolbar--filters-only': filtersOnly, 'standard-list-toolbar--search-with-selects': filtersLayout === 'search-with-selects', 'standard-list-toolbar--search-with-three-selects': filtersLayout === 'search-with-three-selects', 'standard-list-toolbar--search-with-four-selects': filtersLayout === 'search-with-four-selects', 'standard-list-toolbar--search-full-width': filtersLayout === 'search-full-width' }" @submit.prevent="emit('query')">
      <div class="standard-list-toolbar__filters" :class="{ 'standard-list-toolbar__filters--search-with-selects': filtersLayout === 'search-with-selects', 'standard-list-toolbar__filters--search-with-three-selects': filtersLayout === 'search-with-three-selects', 'standard-list-toolbar__filters--search-with-four-selects': filtersLayout === 'search-with-four-selects', 'standard-list-toolbar__filters--search-with-filter': filtersLayout === 'search-with-filter', 'standard-list-toolbar__filters--search-full-width': filtersLayout === 'search-full-width', 'standard-list-toolbar__filters--three-columns': filtersLayout === 'three-columns', 'standard-list-toolbar__filters--dense-grid': filtersLayout === 'dense-grid' }">
        <slot />
      </div>
      <div v-if="!filtersOnly" class="standard-list-toolbar__commands">
        <button v-if="showQuery" class="prototype-button" type="submit" :disabled="disabled">查询</button>
        <button v-if="showReset" class="work-quiet-button" type="button" :disabled="disabled" @click="emit('reset')">重置</button>
        <slot name="actions" />
        <button v-if="showRefresh" class="work-quiet-button standard-list-toolbar__refresh" type="button" :disabled="disabled || refreshing" @click="emit('refresh')">
          <RefreshCw :size="15" :class="{ spinning: refreshing }" />刷新
        </button>
      </div>
    </form>
  </div>
</template>
