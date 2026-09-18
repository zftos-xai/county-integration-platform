<!-- 写操作成功提示：在有审计读取权限时提供对应业务对象的审计追溯入口。 -->
<script setup lang="ts">
import { Check, ClipboardList, X } from 'lucide-vue-next'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { hasPermission } from '@/store/modules/auth'

const props = defineProps<{ message: string; targetType?: string; targetId?: string }>()
const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const canOpenAudit = computed(() => hasPermission('audit:read') && Boolean(props.targetType && props.targetId))

function openAudit() {
  if (!canOpenAudit.value) return
  void router.push({ path: '/audit', query: { targetType: props.targetType, targetId: props.targetId } })
}
</script>

<template>
  <div class="audit-aware-success" role="status">
    <Check :size="18" /><span>{{ message }}</span>
    <button v-if="canOpenAudit" class="audit-link" type="button" @click="openAudit"><ClipboardList :size="15" />查看审计</button>
    <button class="close-button" type="button" aria-label="关闭提示" @click="emit('close')"><X :size="16" /></button>
  </div>
</template>

<style scoped>
.audit-aware-success { min-height: 46px; margin-bottom: 12px; padding: 9px 12px; border: 1px solid #bcd9ca; border-radius: 5px; background: #edf7f2; color: #246b4f; display: flex; align-items: center; gap: 10px; font-size: 12px; }
.audit-aware-success > span { min-width: 0; flex: 1; }
.audit-link, .close-button { border: 0; background: transparent; color: inherit; }
.audit-link { min-height: 30px; padding: 0 8px; display: inline-flex; align-items: center; gap: 5px; border-radius: 4px; font-weight: 600; }
.audit-link:hover { background: rgb(36 107 79 / 9%); }
.close-button { width: 30px; height: 30px; display: grid; place-items: center; }
</style>
