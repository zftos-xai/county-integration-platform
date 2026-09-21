<!-- 数据库契约写操作冲突提示：要求重新读取真实状态，不允许直接重复DDL请求。 -->
<script setup lang="ts">
import { AlertTriangle, RefreshCw } from 'lucide-vue-next'
import { ApiClientError } from '@/utils/request'

defineProps<{ error: ApiClientError }>()
defineEmits<{ reload: [] }>()
</script>

<template>
  <div class="contract-conflict" role="alert">
    <AlertTriangle :size="18" />
    <div><strong>{{ error.status === 409 ? '方案状态已经变化' : '写操作结果需要重新确认' }}</strong><p>{{ error.message }}。请先重新读取扫描和方案，不要直接重复执行。</p></div>
    <button class="prototype-secondary" @click="$emit('reload')"><RefreshCw :size="15" />重新读取</button>
  </div>
</template>

<style scoped>
.contract-conflict{padding:13px 15px;border:1px solid #e6c6a1;border-radius:9px;background:#fff8ed;color:#7d5726;display:flex;align-items:flex-start;gap:10px}.contract-conflict>svg{flex:none;margin-top:2px}.contract-conflict>div{flex:1}.contract-conflict strong{font-size:13px}.contract-conflict p{margin:4px 0 0;font-size:12px;line-height:1.6}.contract-conflict button{display:inline-flex;align-items:center;gap:6px;white-space:nowrap}@media(max-width:700px){.contract-conflict{flex-wrap:wrap}.contract-conflict button{margin-left:28px}}
</style>
