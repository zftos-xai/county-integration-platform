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
      <p>工程结构已建立，业务字段和操作将在正式接口规则确认后实现。</p>
    </div>
  </section>
</template>
