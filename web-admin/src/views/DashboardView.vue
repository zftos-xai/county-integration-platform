<script setup lang="ts">
import { AlertCircle, CheckCircle2, RefreshCw, Server } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'

type HealthState = 'checking' | 'available' | 'unavailable'
const health = ref<HealthState>('checking')
const checkedAt = ref('')

async function checkHealth() {
  health.value = 'checking'
  try {
    const response = await fetch('/actuator/health', { headers: { Accept: 'application/json' } })
    health.value = response.ok ? 'available' : 'unavailable'
  } catch {
    health.value = 'unavailable'
  } finally {
    checkedAt.value = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date())
  }
}

onMounted(checkHealth)
</script>

<template>
  <section class="content">
    <div class="status-strip" :class="health">
      <Server :size="20" />
      <div>
        <strong>{{ health === 'checking' ? '正在检查后台服务' : health === 'available' ? '后台服务可用' : '后台服务未连接' }}</strong>
        <span>{{ checkedAt ? `最近检查 ${checkedAt}` : '等待检查结果' }}</span>
      </div>
      <button class="icon-button" title="重新检查" :disabled="health === 'checking'" @click="checkHealth"><RefreshCw :size="18" /></button>
    </div>

    <div class="metric-grid">
      <article><span>今日交换</span><strong>—</strong><small>连接数据库后显示</small></article>
      <article><span>待处理异常</span><strong>—</strong><small>连接数据库后显示</small></article>
      <article><span>数据差异</span><strong>—</strong><small>连接数据库后显示</small></article>
      <article><span>已启用接口</span><strong>—</strong><small>完成接口登记后显示</small></article>
    </div>

    <section class="work-panel">
      <div class="panel-heading"><div><h2>运行状态</h2><p>按正式接口登记和真实交换记录汇总</p></div></div>
      <div v-if="health === 'available'" class="empty-state"><CheckCircle2 :size="30" /><strong>后台已连接</strong><span>接口统计能力将在对应模块完成后接入。</span></div>
      <div v-else class="empty-state warning"><AlertCircle :size="30" /><strong>暂无可展示的运行数据</strong><span>请先配置 SQL Server 和后台服务，再检查运行状态。</span></div>
    </section>
  </section>
</template>
