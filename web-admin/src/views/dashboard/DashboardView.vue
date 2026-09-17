<!-- 正式运行总览页面：只展示后台健康检查已经返回的运行状态。 -->
<script setup lang="ts">
import { AlertCircle, CheckCircle2, RefreshCw, Server } from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { isBackendAvailable } from '@/api/health'

type HealthState = 'checking' | 'available' | 'unavailable'
const health = ref<HealthState>('checking')
const checkedAt = ref('')
let healthController: AbortController | null = null

async function checkHealth() {
  healthController?.abort()
  const controller = new AbortController()
  healthController = controller
  health.value = 'checking'
  try {
    health.value = await isBackendAvailable(controller.signal) ? 'available' : 'unavailable'
  } catch {
    if (controller.signal.aborted) return
    health.value = 'unavailable'
  } finally {
    checkedAt.value = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date())
  }
}

onMounted(checkHealth)
onBeforeUnmount(() => healthController?.abort())
</script>

<template>
  <section class="content">
    <div class="overview-commandbar">
      <div>
        <strong>{{ health === 'checking' ? '正在检查后台服务' : health === 'available' ? '运行状态' : '后台服务未连接' }}</strong>
        <small>{{ checkedAt ? `最近检查 ${checkedAt}` : '正在读取服务状态' }}</small>
      </div>
      <div class="overview-commandbar-actions">
        <span class="prototype-tag overview-health-tag" :class="health === 'available' ? 'success' : health === 'unavailable' ? 'danger' : 'neutral'">
          {{ health === 'available' ? '服务可用' : health === 'unavailable' ? '连接失败' : '检查中' }}
        </span>
        <button class="work-quiet-button overview-refresh" title="重新检查" :disabled="health === 'checking'" @click="checkHealth"><RefreshCw :size="14" />刷新</button>
      </div>
    </div>

    <section class="overview-kpis" aria-label="运行关键指标">
      <button disabled><span>启用机构</span><strong>—</strong><small>暂无数据</small></button>
      <button disabled><span>允许新请求的接口</span><strong>—</strong><small>暂无数据</small></button>
      <button class="warning" disabled><span>待核查交换</span><strong>—</strong><small>暂无数据</small></button>
      <button class="danger" disabled><span>待处理告警</span><strong>—</strong><small>暂无数据</small></button>
    </section>

    <div class="overview-work-grid">
      <section class="card overview-work-card">
        <div class="card-header"><div><h3 class="card-title">待处理告警</h3><small>先确认影响范围，再登记处理结果</small></div></div>
        <div v-if="health === 'available'" class="empty-state"><CheckCircle2 :size="27" /><strong>暂无可展示数据</strong></div>
        <div v-else class="empty-state warning"><AlertCircle :size="27" /><strong>暂时无法读取运行状态</strong><span>请检查后台服务后重试。</span></div>
      </section>
      <section class="card overview-work-card">
        <div class="card-header"><div><h3 class="card-title">待核查交换</h3><small>业务结果未确认或当前规则阻止继续处理</small></div></div>
        <div class="empty-state"><Server :size="27" /><strong>暂无可展示数据</strong></div>
      </section>
    </div>
  </section>
</template>
