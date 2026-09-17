<!-- 工作站嵌入端入口：在安全上下文合同明确前不接收业务数据。 -->
<script setup lang="ts">
import { Link2, ShieldCheck } from 'lucide-vue-next'
</script>

<template>
  <main class="embed-shell">
    <header><Link2 :size="20" /><strong>县域协同业务入口</strong><span>未配置</span></header>
    <section>
      <ShieldCheck :size="34" />
      <h1>等待安全上下文</h1>
      <p>机构、用户和业务引用通过医院批准的安全接入方式提供后，才会加载对应业务。</p>
    </section>
  </main>
</template>
