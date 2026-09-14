<script setup lang="ts">
import {
  Activity, AlertTriangle, ArrowLeftRight, Building2, ClipboardCheck,
  Database, Menu, ScrollText, Settings2, ShieldCheck, X,
} from 'lucide-vue-next'
import { ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()
const mobileOpen = ref(false)
const navigation = [
  { to: '/', label: '运行总览', icon: Activity },
  { to: '/organizations', label: '机构与权限', icon: Building2 },
  { to: '/interfaces', label: '接口与映射', icon: Settings2 },
  { to: '/exchanges', label: '交换记录', icon: ArrowLeftRight },
  { to: '/exceptions', label: '异常与处理', icon: AlertTriangle },
  { to: '/reconciliation', label: '数据对账', icon: ClipboardCheck },
  { to: '/audit', label: '审计记录', icon: ScrollText },
]
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ open: mobileOpen }">
      <div class="brand">
        <span class="brand-mark"><Database :size="20" /></span>
        <span><strong>县域接口平台</strong><small>协同运行管理</small></span>
        <button class="icon-button close-menu" title="关闭导航" @click="mobileOpen = false"><X :size="19" /></button>
      </div>
      <nav>
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to" @click="mobileOpen = false">
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-footer"><ShieldCheck :size="17" /><span>院内私有化部署</span></div>
    </aside>

    <div v-if="mobileOpen" class="scrim" @click="mobileOpen = false" />

    <main class="main-area">
      <header class="topbar">
        <button class="icon-button menu-button" title="打开导航" @click="mobileOpen = true"><Menu :size="20" /></button>
        <div><span class="eyebrow">县人民医院 ⇄ 基层卫生院</span><h1>{{ route.meta.title }}</h1></div>
        <span class="environment">当前环境：未配置</span>
      </header>
      <RouterView />
    </main>
  </div>
</template>
