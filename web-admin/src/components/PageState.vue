<!-- 管理端列表和工作区的统一加载、空数据及错误状态展示。 -->
<script setup lang="ts">
import { LoaderCircle } from 'lucide-vue-next'

type PageStateKind = 'loading' | 'empty' | 'error' | 'info'

withDefaults(defineProps<{
  /** 当前状态的语义类型，用于无障碍播报和视觉区分。 */
  kind: PageStateKind
  /** 状态标题。 */
  title: string
  /** 可选的补充说明。 */
  description?: string
  /** 紧凑布局，用于嵌套面板或局部区域。 */
  compact?: boolean
}>(), {
  description: '',
  compact: false,
})
</script>

<template>
  <section
    class="page-state"
    :class="[`page-state--${kind}`, { compact }]"
    :role="kind === 'error' ? 'alert' : kind === 'loading' ? 'status' : undefined"
    :aria-live="kind === 'loading' ? 'polite' : kind === 'error' ? 'assertive' : undefined"
  >
    <span v-if="$slots.icon || kind === 'loading'" class="page-state-icon" aria-hidden="true">
      <slot name="icon"><LoaderCircle class="page-state-spinner" :size="28" /></slot>
    </span>
    <strong>{{ title }}</strong>
    <span v-if="description || $slots.description" class="page-state-description">
      <slot name="description">{{ description }}</slot>
    </span>
    <div v-if="$slots.actions" class="page-state-actions"><slot name="actions" /></div>
  </section>
</template>

<style scoped>
.page-state {
  min-height: 240px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 9px;
  color: var(--ui-color-success, #33745c);
  text-align: center;
}

.page-state.compact { min-height: 120px; padding: 18px; }
.page-state--error { color: var(--ui-color-danger, #9b4438); }
.page-state-icon { display: inline-flex; }
.page-state-description { max-width: 520px; color: var(--ui-color-state-muted, #75828c); font-size: 12px; line-height: 1.6; }
.page-state-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.page-state-spinner { animation: page-state-spin .8s linear infinite; }

@keyframes page-state-spin { to { transform: rotate(360deg); } }
</style>
