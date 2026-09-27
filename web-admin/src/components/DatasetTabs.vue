<!-- 数据目录页签：在综合目录、三大目录和公共 ICD-10 之间提供一致导航。 -->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps<{
  /** 在机构目录页之间切换时保留当前机构上下文。 */
  organizationCode?: string
}>()

const route = useRoute()
const organizationQuery = computed(() => props.organizationCode ? { organizationCode: props.organizationCode } : {})
</script>

<template>
  <nav class="dataset-nav" aria-label="基础数据集">
    <RouterLink :class="{ active: route.path === '/master-data/directory' }" :aria-current="route.path === '/master-data/directory' ? 'page' : undefined" :to="{ path: '/master-data/directory', query: organizationQuery }">综合目录</RouterLink>
    <RouterLink :class="{ active: route.path === '/master-data/directory/medical' }" :aria-current="route.path === '/master-data/directory/medical' ? 'page' : undefined" :to="{ path: '/master-data/directory/medical', query: organizationQuery }">三大目录</RouterLink>
    <RouterLink :class="{ active: route.path === '/master-data/directory/icd10' }" :aria-current="route.path === '/master-data/directory/icd10' ? 'page' : undefined" to="/master-data/directory/icd10">ICD-10</RouterLink>
  </nav>
</template>

<style scoped>
.dataset-nav { grid-column:1/-1; min-width:0; padding:0; display:flex; gap:8px; border-bottom:1px solid var(--line); overflow-x:auto; }
.dataset-nav a { flex:none; padding:11px 13px; border:1px solid transparent; border-bottom:0; border-radius:7px 7px 0 0; color:var(--muted); font-size:13px; text-decoration:none; }
.dataset-nav a:hover { background:#f5f8f8; }
.dataset-nav a.active { border-color:#d6e5e1; background:#eaf5f2; color:var(--accent); font-weight:650; }
</style>
