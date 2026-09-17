<!-- 无权访问页面：用于路由守卫拒绝缺少功能权限的直接访问。 -->
<script setup lang="ts">
import { ShieldX } from 'lucide-vue-next'
import StandaloneState from '@/components/StandaloneState.vue'
</script>

<template>
  <StandaloneState code="403" title="无权访问此页面" description="当前账号没有所需功能权限。机构数据范围仍由服务端校验，前端不会扩展授权边界。" action-label="返回可用首页" action-to="/" tone="danger">
    <template #icon><ShieldX :size="30" /></template>
  </StandaloneState>
</template>
