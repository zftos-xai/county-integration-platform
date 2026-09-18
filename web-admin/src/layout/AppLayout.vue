<!-- 登录后管理端布局：统一承载导航、页面标题、用户信息和退出入口。 -->
<script setup lang="ts">
import {
  Activity, BookOpen, Building2, ClipboardList, Database, LogOut, Menu, ServerCog, Settings2, ShieldCheck, Users,
  X,
} from 'lucide-vue-next'
import { computed, ref } from 'vue'
import type { Component } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { platformSettings } from '@/settings'
import { authState, endSession, hasPermission } from '@/store/modules/auth'

type NavigationItem = {
  to: string
  label: string
  icon: Component
  permission?: string
}

type NavigationGroup = {
  label: string
  items: NavigationItem[]
}

// 布局只展示已经接入真实 API 的业务入口，未完成页面不进入正式导航。
const route = useRoute()
const router = useRouter()
const isMobileOpen = ref(false)

const navigationGroups: NavigationGroup[] = [
  { label: '运行监控', items: [
    { to: '/', label: '运行总览', icon: Activity },
  ] },
  { label: '系统管理', items: [
    { to: '/users', label: '用户管理', icon: Users, permission: 'identity:read' },
    { to: '/roles', label: '角色权限', icon: ShieldCheck, permission: 'access:read' },
    { to: '/organizations', label: '机构管理', icon: Building2, permission: 'organization:read' },
  ] },
  { label: '基础配置', items: [
    { to: '/parameters', label: '参数配置', icon: Settings2, permission: 'configuration:read' },
    { to: '/dictionaries', label: '数据字典', icon: BookOpen, permission: 'configuration:read' },
    { to: '/external-systems', label: '外部系统', icon: ServerCog, permission: 'configuration:read' },
  ] },
  { label: '运行处理', items: [
    { to: '/audit', label: '审计记录', icon: ClipboardList, permission: 'audit:read' },
  ] },
]

// 菜单可见性提升使用体验，后端仍对每个请求执行最终授权。
const visibleNavigationGroups = computed(() => navigationGroups
  .map(group => ({ ...group, items: group.items.filter(item => hasPermission(item.permission)) }))
  .filter(group => group.items.length > 0))

const pageContext = computed(() => {
  const context: Record<string, { section: string; description: string }> = {
    '/': { section: '运行监控', description: '掌握基础数据同步、业务交换和待处理告警' },
    '/data-review': { section: '基础数据处理', description: '核查机构与基础数据的同步状态' },
    '/interfaces': { section: '业务接口处理', description: '维护业务接口、版本与运行边界' },
    '/exchanges': { section: '业务接口处理', description: '追踪跨机构交换和目标端结果' },
    '/exceptions': { section: '运行处理', description: '确认影响范围并推进异常处置' },
    '/audit': { section: '运行处理', description: '查询管理操作与安全审计记录' },
    '/parameters': { section: '基础配置', description: '维护平台注册参数及环境取值' },
    '/dictionaries': { section: '基础配置', description: '维护平台受控字典与字典项' },
    '/external-systems': { section: '基础配置', description: '维护外部系统及各机构的接口配置' },
    '/users': { section: '系统管理', description: '维护平台账号、角色和机构范围' },
    '/roles': { section: '系统管理', description: '维护角色及其功能权限' },
    '/organizations': { section: '系统管理', description: '维护机构档案、层级和启停状态' },
    '/forbidden': { section: '访问控制', description: '当前账号没有访问该功能的权限' },
  }
  return context[route.path] ?? { section: '平台管理', description: '' }
})

async function logout() {
  await endSession()
  isMobileOpen.value = false
  await router.replace('/login')
}
</script>

<template>
  <div class="prototype-app real-app">
    <aside class="prototype-sidebar" :class="{ open: isMobileOpen }">
      <div class="prototype-brand">
        <span class="prototype-logo"><Database :size="20" /></span>
        <span class="prototype-brand-copy"><strong>{{ platformSettings.title }}</strong><small>{{ platformSettings.subtitle }}</small></span>
        <button class="prototype-icon prototype-close" aria-label="关闭导航" title="关闭导航" @click="isMobileOpen = false"><X :size="18" /></button>
      </div>
      <nav class="prototype-nav" aria-label="功能导航">
        <div v-for="group in visibleNavigationGroups" :key="group.label" class="prototype-nav-group">
          <span class="prototype-nav-label">{{ group.label }}</span>
          <RouterLink v-for="item in group.items" :key="item.to" :to="item.to" active-class="prototype-route-parent" exact-active-class="router-link-active" @click="isMobileOpen = false">
            <component :is="item.icon" :size="17" /><span>{{ item.label }}</span>
          </RouterLink>
        </div>
      </nav>
      <div class="prototype-sidebar-foot">
        <div class="prototype-user">
          <span class="prototype-avatar">{{ authState.user?.displayName?.slice(0, 1) || '用' }}</span>
          <span><strong>{{ authState.user?.displayName }}</strong><small>{{ authState.user?.organizationCode }}</small></span>
        </div>
      </div>
    </aside>
    <div v-if="isMobileOpen" class="prototype-scrim" @click="isMobileOpen = false" />

    <main class="prototype-main">
      <header class="prototype-topbar">
        <button class="prototype-icon prototype-menu" aria-label="打开导航" title="打开导航" @click="isMobileOpen = true"><Menu :size="20" /></button>
        <div class="prototype-heading">
          <!-- 顶部按“面包屑、页面标题、说明”分层，便于快速确认所在模块与当前任务。 -->
          <nav class="prototype-breadcrumb" aria-label="页面层级">
            <span>{{ pageContext.section }}</span><span aria-hidden="true">/</span><strong>{{ route.meta.title }}</strong>
          </nav>
          <h1>{{ route.meta.title }}</h1>
          <p>{{ pageContext.description }}</p>
        </div>
      </header>
      <div class="prototype-content real-content"><RouterView /></div>
      <footer class="prototype-main-footer">
        <div class="prototype-product-meta">
          <ShieldCheck :size="16" />
          <span><strong>{{ platformSettings.version }}</strong><small>{{ platformSettings.subtitle }}</small></span>
        </div>
        <button class="prototype-footer-logout" aria-label="退出登录" title="退出登录" @click="logout"><LogOut :size="16" /><span>退出登录</span></button>
      </footer>
    </main>
  </div>
</template>
