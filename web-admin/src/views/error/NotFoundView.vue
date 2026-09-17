<!-- 受控404页面：用于未知地址，不推断资源或权限是否存在。 -->
<script setup lang="ts">
import { FileQuestion } from 'lucide-vue-next'
import StandaloneState from '@/components/StandaloneState.vue'
</script>

<template>
  <StandaloneState code="404" title="页面不存在" description="地址可能已经变更，请返回平台首页重新选择功能。" action-label="返回首页" action-to="/">
    <template #icon><FileQuestion :size="30" /></template>
  </StandaloneState>
</template>
