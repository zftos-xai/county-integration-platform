<!-- 独立系统状态组件：为未挂载管理布局的错误页提供一致的可访问反馈。 -->
<script setup lang="ts">
import { RouterLink } from 'vue-router'

defineProps<{
  code: string
  title: string
  description: string
  actionLabel: string
  actionTo: string
  tone?: 'neutral' | 'danger'
}>()
</script>

<template>
  <main class="standalone-state">
    <section class="standalone-state-card" :aria-labelledby="`state-title-${code}`">
      <span class="standalone-state-icon" :class="tone"><slot name="icon" /></span>
      <span class="standalone-state-code">{{ code }}</span>
      <h1 :id="`state-title-${code}`">{{ title }}</h1>
      <p>{{ description }}</p>
      <RouterLink class="standalone-state-action" :to="actionTo">{{ actionLabel }}</RouterLink>
    </section>
  </main>
</template>

<style scoped>
.standalone-state {
  display: grid;
  min-height: 100vh;
  padding: 32px 20px;
  place-items: center;
  background: #f3f6f8;
  color: #263746;
}

.standalone-state-card {
  width: min(100%, 520px);
  padding: 44px 36px;
  border: 1px solid #dce4e8;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 16px 40px rgb(35 58 69 / 8%);
  text-align: center;
}

.standalone-state-icon {
  display: inline-grid;
  width: 56px;
  height: 56px;
  margin-bottom: 20px;
  border-radius: 14px;
  place-items: center;
  background: #e8f2ef;
  color: #247465;
}

.standalone-state-icon.danger { background: #fff1f1; color: #b43c45; }
.standalone-state-code { display: block; margin-bottom: 6px; color: #6f7f89; font-size: 13px; font-weight: 700; letter-spacing: .12em; }
.standalone-state h1 { margin: 0; font-size: 28px; }
.standalone-state p { margin: 14px 0 26px; color: #6f7f89; line-height: 1.7; }
.standalone-state-action { display: inline-flex; min-height: 42px; padding: 0 22px; align-items: center; justify-content: center; border-radius: 8px; background: #247465; color: #fff; font-weight: 700; text-decoration: none; }
.standalone-state-action:focus-visible { outline: 3px solid rgb(36 116 101 / 30%); outline-offset: 3px; }
@media (max-width: 520px) { .standalone-state-card { padding: 36px 24px; } }
</style>
