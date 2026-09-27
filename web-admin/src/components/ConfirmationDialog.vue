<!-- 管理端共享确认对话框：统一危险操作与未保存离开的说明、焦点和响应式操作区。 -->
<script setup lang="ts">
import ModalFrame from '@/components/ModalFrame.vue'
import type { ConfirmationRequest } from '@/composables/useConfirmationDialog'

defineProps<{ request: ConfirmationRequest }>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <ModalFrame
    panel-class="confirmation-dialog"
    panel-width="min(480px, 100%)"
    body-padding="20px 22px"
    labelled-by="confirmation-dialog-title"
    close-label="取消操作"
    @close="emit('cancel')"
  >
    <template #title><h2 id="confirmation-dialog-title">{{ request.title }}</h2></template>
    <p class="confirmation-dialog-message">{{ request.message }}</p>
    <template #footer>
      <button class="work-quiet-button" type="button" @click="emit('cancel')">取消</button>
      <button
        :class="request.danger ? 'work-danger-button' : 'prototype-button'"
        type="button"
        @click="emit('confirm')"
      >{{ request.confirmLabel ?? '确认' }}</button>
    </template>
  </ModalFrame>
</template>

<style scoped>
.confirmation-dialog-message { margin: 0; color: var(--ui-color-muted, #62717a); line-height: 1.65; overflow-wrap: anywhere; }
</style>
