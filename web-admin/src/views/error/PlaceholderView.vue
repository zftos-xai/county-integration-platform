<!-- 未接入真实 API 的页面地址占位页；不得加入正式导航冒充已完成功能。 -->
<script setup lang="ts">
import { FileClock } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <section class="content">
    <div class="empty-page">
      <FileClock :size="34" />
      <h2>{{ route.meta.title }}</h2>
      <p>当前功能暂未开放。</p>
    </div>
  </section>
</template>
