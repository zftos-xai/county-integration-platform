<!-- 原型视角选择组件：不模拟账号认证，也不接受或保存密码。 -->
<script setup lang="ts">
import { Database, Eye, ShieldCheck, Wrench } from 'lucide-vue-next'
import { createPrototypeSession } from '../model/prototypeAuth'
import type { PrototypeRole, PrototypeSession } from '../model/prototypeAuth'

const emit = defineEmits<{ authenticated: [session: PrototypeSession] }>()

function enterPrototype(role: PrototypeRole) {
  emit('authenticated', createPrototypeSession(role))
}
</script>

<template>
  <main class="prototype-login">
    <section class="prototype-login-context" aria-label="平台说明">
      <div class="prototype-login-brand"><span><Database :size="24" /></span><div><strong>县域接口平台</strong><small>集成运行管理</small></div></div>
      <div class="prototype-login-intro"><span class="prototype-login-eyebrow">交互评审入口</span><h1>让每一次跨机构交换<br />都有依据、有边界、可追溯</h1><p>本入口只展示合成数据和交互流程，不连接正式认证、会话或业务接口。</p></div>
      <div class="prototype-login-boundaries"><div><ShieldCheck :size="19" /><span><strong>原型与正式页面分开</strong><small>所有对象均为合成示例，不代表正式权限</small></span></div><div><Eye :size="19" /><span><strong>按角色检查</strong><small>选择管理或运维角色检查对应页面</small></span></div></div>
      <small class="prototype-login-deployment">院内私有化部署 · 合成数据原型</small>
    </section>
    <section class="prototype-login-panel">
      <div class="prototype-login-mobile-brand"><Database :size="20" /><strong>县域接口平台</strong></div>
      <section class="prototype-login-card" aria-labelledby="prototype-entry-title">
        <div class="prototype-login-card-head"><span class="prototype-login-card-icon"><Eye :size="20" /></span><div><span>原型视角</span><h2 id="prototype-entry-title">选择评审视角</h2><p>该操作不建立登录会话</p></div></div>
        <div class="prototype-login-message warning" role="status">正式账号登录请使用管理端登录页；此处不接受账号或密码。</div>
        <button class="prototype-login-submit" type="button" @click="enterPrototype('manager')"><ShieldCheck :size="17" />进入管理视角</button>
        <button class="prototype-login-back" type="button" @click="enterPrototype('operator')"><Wrench :size="17" />进入运维视角</button>
        <p class="prototype-login-help">视角只控制原型中的可见操作，不代表服务端授权结果。</p>
      </section>
      <p class="prototype-login-footer">合成数据 · 不连接正式认证</p>
    </section>
  </main>
</template>
