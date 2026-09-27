<!-- 管理端表格框架：统一列表的横向滚动区和操作列定位边界。 -->
<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{
  /** 为可横向滚动的表格区域提供屏幕阅读器名称。 */
  label: string
  /** 是否为表格最后一列启用统一操作列定位。 */
  hasActions?: boolean
  /** 在宽表格的窄屏布局中关闭固定操作列，避免遮住相邻业务字段。 */
  pinActions?: boolean
  /** 设置业务表格确有需要时的操作列宽度。 */
  actionColumnWidth?: string
}>(), {
  hasActions: false,
  pinActions: true,
})

const scrollContainer = ref<HTMLDivElement | null>(null)

/** 将横向滚动位置恢复到表格首列，供筛选或展开详情后调用。 */
function resetHorizontalScroll() {
  if (scrollContainer.value) scrollContainer.value.scrollLeft = 0
}

defineExpose({ resetHorizontalScroll })
</script>

<template>
  <div class="standard-table-frame" :class="{ 'action-column-table': hasActions, 'action-column-table--scroll-actions': hasActions && !pinActions }" :style="props.actionColumnWidth ? { '--action-column-width': props.actionColumnWidth } : undefined">
    <div ref="scrollContainer" class="prototype-table-wrap standard-table-scroll" role="region" :aria-label="label" tabindex="0">
      <slot />
    </div>
  </div>
</template>
