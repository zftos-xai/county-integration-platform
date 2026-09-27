<!-- 管理端居中对话框通用框架：统一遮罩、命名、键盘焦点、滚动正文和固定操作栏。 -->
<script setup lang="ts">
import { ref } from 'vue'
import { X } from 'lucide-vue-next'
import { useModalDialog } from '@/composables/useModalDialog'

withDefaults(defineProps<{
  /** 对话框标题元素的 ID，用于建立可访问名称。 */
  labelledBy: string
  /** 对话框的业务外观类名。 */
  panelClass?: string
  /** 对话框宽度；未设置时使用管理端默认值。 */
  panelWidth?: string
  /** 对话框最大高度；未设置时由通用样式限制。 */
  panelMaxHeight?: string
  /** 正文内边距；未设置时使用管理端默认值。 */
  bodyPadding?: string
  /** 是否允许点击遮罩关闭对话框。 */
  dismissOnBackdrop?: boolean
  /** 关闭按钮的可访问名称。 */
  closeLabel?: string
}>(), {
  panelClass: '',
  panelWidth: '',
  panelMaxHeight: '',
  bodyPadding: '',
  dismissOnBackdrop: true,
  closeLabel: '关闭',
})

const emit = defineEmits<{ close: [] }>()
const isOpen = ref(true)
const { dialogRef, handleDialogKeydown } = useModalDialog(isOpen, () => emit('close'))
</script>

<template>
  <div class="work-modal-backdrop" @mousedown.self="dismissOnBackdrop && emit('close')">
    <section
      ref="dialogRef"
      class="work-modal"
      :class="panelClass"
      :style="{
        ...(panelWidth ? { '--work-modal-width': panelWidth } : {}),
        ...(panelMaxHeight ? { '--work-modal-max-height': panelMaxHeight } : {}),
      }"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="labelledBy"
      tabindex="-1"
      @keydown="handleDialogKeydown"
    >
      <header class="work-modal-header">
        <div class="work-modal-title"><slot name="title" /></div>
        <button class="prototype-icon" type="button" :aria-label="closeLabel" @click="emit('close')"><X :size="18" /></button>
      </header>
      <div class="work-modal-body" :style="bodyPadding ? { padding: bodyPadding } : undefined"><slot /></div>
      <footer v-if="$slots.footer" class="work-modal-footer"><slot name="footer" /></footer>
    </section>
  </div>
</template>
