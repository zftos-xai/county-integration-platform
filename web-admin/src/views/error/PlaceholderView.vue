<!-- 未接入真实 API 的页面地址占位页；不得加入正式导航冒充已完成功能。 -->
<script setup lang="ts">
import { FileClock } from 'lucide-vue-next'
import PageState from '@/components/PageState.vue'
</script>

<template>
  <section class="content">
    <PageState class="placeholder-page-state" kind="info" title="当前功能暂未开放。">
      <template #icon><FileClock :size="34" /></template>
    </PageState>
  </section>
</template>

<style scoped>
.placeholder-page-state { border: 1px solid var(--ui-color-border); border-radius: 5px; background: var(--ui-color-surface); color: var(--ui-color-muted); }
</style>
