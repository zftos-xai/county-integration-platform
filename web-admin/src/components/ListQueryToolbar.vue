<!-- 管理端列表的统一查询栏：页面提供筛选项和业务操作，组件固定查询、重置和刷新位置；结果数由分页栏唯一展示。 -->
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
}>(), {
  refreshing: false,
  disabled: false,
  showQuery: true,
  showReset: true,
  showRefresh: true,
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
  <form class="standard-list-toolbar" @submit.prevent="emit('query')">
    <div class="standard-list-toolbar__filters">
      <slot />
    </div>
    <div class="standard-list-toolbar__commands">
      <button v-if="showQuery" class="prototype-button" type="submit" :disabled="disabled">查询</button>
      <button v-if="showReset" class="work-quiet-button" type="button" :disabled="disabled" @click="emit('reset')">重置</button>
      <slot name="actions" />
    </div>
    <div class="standard-list-toolbar__utility">
      <button v-if="showRefresh" class="work-quiet-button" type="button" :disabled="disabled || refreshing" @click="emit('refresh')">
        <RefreshCw :size="15" :class="{ spinning: refreshing }" />刷新
      </button>
    </div>
  </form>
</template>
