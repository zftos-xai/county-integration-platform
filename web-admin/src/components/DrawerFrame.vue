<!-- 管理端侧边抽屉通用框架：统一遮罩、关闭入口、焦点管理、滚动正文和固定操作栏。 -->
<script setup lang="ts">
import { ref } from 'vue'
import { X } from 'lucide-vue-next'
import { useModalDialog } from '@/composables/useModalDialog'

withDefaults(defineProps<{
  /** 抽屉标题元素的 ID，用于建立对话框可访问名称。 */
  labelledBy: string
  /** 业务抽屉用于设置宽度或补充局部外观的根类名。 */
  panelClass?: string
  /** 页面需要专属宽度时的 CSS 长度表达式。 */
  panelWidth?: string
  /** 正文区域的局部布局类名。 */
  bodyClass?: string
  /** 正文内边距；未设置时使用管理端默认值。 */
  bodyPadding?: string
  /** 是否允许点击遮罩关闭抽屉。 */
  dismissOnBackdrop?: boolean
  /** 关闭按钮的可访问名称。 */
  closeLabel?: string
}>(), {
  panelClass: '',
  panelWidth: '',
  bodyClass: '',
  bodyPadding: '',
  dismissOnBackdrop: true,
  closeLabel: '关闭',
})

const emit = defineEmits<{ close: [] }>()
const isOpen = ref(true)
const { dialogRef, handleDialogKeydown } = useModalDialog(isOpen, () => emit('close'))
</script>

<template>
  <div class="work-drawer-backdrop" @mousedown.self="dismissOnBackdrop && emit('close')">
    <section
      ref="dialogRef"
      class="work-drawer"
      :class="panelClass"
      :style="panelWidth ? { '--work-drawer-width': panelWidth } : undefined"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="labelledBy"
      tabindex="-1"
      @keydown="handleDialogKeydown"
    >
      <header class="work-drawer-header">
        <div class="work-drawer-title"><slot name="title" /></div>
        <button class="prototype-icon" type="button" :aria-label="closeLabel" @click="emit('close')"><X :size="18" /></button>
      </header>
      <slot name="subheader" />
      <slot name="tabs" />
      <div class="work-drawer-body" :class="bodyClass" :style="bodyPadding ? { padding: bodyPadding } : undefined"><slot /></div>
      <footer v-if="$slots.footer" class="work-drawer-footer"><slot name="footer" /></footer>
    </section>
  </div>
</template>

<style scoped>
.work-drawer-title { min-width: 0; }
</style>
