<!-- 管理端根组件：只提供顶层路由出口，应用外壳由 layout 负责。 -->
<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <RouterView />
</template>
