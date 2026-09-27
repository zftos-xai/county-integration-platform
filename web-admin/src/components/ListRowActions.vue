<!-- 管理端列表行操作组件：统一同一行多个按钮的间距、对齐与换行规则。 -->
<script setup lang="ts">
withDefaults(defineProps<{
  /** 屏幕阅读器用于描述当前行操作组的名称。 */
  label?: string
}>(), {
  label: '行操作',
})
</script>

<template>
  <div class="standard-row-actions" role="group" :aria-label="label">
    <slot />
  </div>
</template>
