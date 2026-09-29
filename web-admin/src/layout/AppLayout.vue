<!-- 登录后管理端布局：统一承载导航、页面标题、用户信息和退出入口。 -->
<script setup lang="ts">
import {
  Activity,
  BookOpen,
  Building2,
  CheckCircle2,
  ClipboardList,
  Clock3,
  Database,
  FileOutput,
  LogOut,
  Menu,
  ServerCog,
  Settings2,
  ShieldCheck,
  Users,
  X,
} from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import type { Component } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { platformSettings } from '@/settings';
import { authState, endSession, hasPermission } from '@/store/modules/auth';

type NavigationItem = {
  to: string;
  label: string;
  icon: Component;
  permission?: string;
};

type NavigationGroup = {
  label: string;
  items: NavigationItem[];
};

// 布局只展示已经接入真实 API 的业务入口，未完成页面不进入正式导航。
const route = useRoute();
const router = useRouter();
const isSidebarCollapsed = ref(false);
const isMobileOpen = ref(false);
const responsiveNavigationQuery = window.matchMedia('(max-width: 900px)');
const isResponsiveViewport = ref(responsiveNavigationQuery.matches);
const menuToggleButton = ref<HTMLButtonElement | null>(null);
const mobileCloseButton = ref<HTMLButtonElement | null>(null);

/** Keep drawer accessibility state aligned with the responsive breakpoint. */
function syncResponsiveViewport(event: MediaQueryListEvent) {
  isResponsiveViewport.value = event.matches;
  if (!event.matches) isMobileOpen.value = false;
}

onMounted(() => responsiveNavigationQuery.addEventListener('change', syncResponsiveViewport));
onBeforeUnmount(() => responsiveNavigationQuery.removeEventListener('change', syncResponsiveViewport));
const pageSection = computed(() => {
  const path = route.path;
  if (path.startsWith('/master-data/')) return '基础数据';
  if (['/users', '/roles', '/organizations'].includes(path)) return '系统管理';
  if (['/parameters', '/dictionaries', '/external-systems'].includes(path)) return '基础配置';
  if (['/database-contract', '/exchanges', '/audit'].includes(path)) return '运行与核查';
  if (path === '/') return '运行监控';
  return '平台管理';
});
// 角色名称只用于辨认账号；菜单与操作仍只依据服务端权限代码。
const currentRoleNames = computed(() => authState.user?.roleNames.join('、') || '未分配角色');
const authorizedOrganizationCount = computed(() => authState.user?.organizationCodes.length ?? 0);

const navigationGroups: NavigationGroup[] = [
  {
    label: '运行监控',
    items: [{ to: '/', label: '运行总览', icon: Activity }],
  },
  {
    label: '基础数据',
    items: [
      {
        to: '/master-data/directory',
        label: '数据目录',
        icon: Database,
        permission: 'master-data:read',
      },
      {
        to: '/master-data/batches',
        label: '同步批次',
        icon: Clock3,
        permission: 'master-data:read',
      },
    ],
  },
  {
    label: '系统管理',
    items: [
      {
        to: '/users',
        label: '用户管理',
        icon: Users,
        permission: 'identity:read',
      },
      {
        to: '/roles',
        label: '角色权限',
        icon: ShieldCheck,
        permission: 'access:read',
      },
      {
        to: '/organizations',
        label: '机构管理',
        icon: Building2,
        permission: 'organization:read',
      },
    ],
  },
  {
    label: '基础配置',
    items: [
      {
        to: '/parameters',
        label: '参数配置',
        icon: Settings2,
        permission: 'configuration:read',
      },
      {
        to: '/dictionaries',
        label: '数据字典',
        icon: BookOpen,
        permission: 'configuration:read',
      },
      {
        to: '/external-systems',
        label: '外部系统',
        icon: ServerCog,
        permission: 'configuration:read',
      },
    ],
  },
  {
    label: '运行与核查',
    items: [
      {
        to: '/database-contract',
        label: '数据库结构维护',
        icon: Database,
        permission: 'database-contract:read',
      },
      {
        to: '/exchanges',
        label: 'HIS调用记录',
        icon: FileOutput,
        permission: 'exchange:read',
      },
      {
        to: '/audit',
        label: '审计记录',
        icon: ClipboardList,
        permission: 'audit:read',
      },
    ],
  },
];

// 菜单可见性提升使用体验，后端仍对每个请求执行最终授权。
const visibleNavigationGroups = computed(() =>
  navigationGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => hasPermission(item.permission)),
    }))
    .filter((group) => group.items.length > 0),
);

/** 只高亮当前正式路由对应的菜单，根路由不会因共享父路由而一直处于选中态。 */
function isNavigationItemActive(item: NavigationItem) {
  if (item.to === '/') return route.path === '/';
  if (item.to === '/master-data/directory') return route.path.startsWith('/master-data/directory');
  return route.path === item.to || route.path.startsWith(`${item.to}/`);
}

async function logout() {
  await endSession();
  isMobileOpen.value = false;
  await router.replace('/login');
}

/** 桌面端折叠侧栏，窄屏端打开导航抽屉并将键盘焦点移入。 */
async function toggleNavigation() {
  if (isResponsiveViewport.value) {
    isMobileOpen.value = true;
    await nextTick();
    mobileCloseButton.value?.focus();
    return;
  }
  isSidebarCollapsed.value = !isSidebarCollapsed.value;
}

/** 关闭窄屏抽屉后将焦点交还页头开关，避免焦点留在移出视口的菜单中。 */
async function closeMobileNavigation() {
  isMobileOpen.value = false;
  if (isResponsiveViewport.value) {
    await nextTick();
    menuToggleButton.value?.focus();
  }
}
</script>

<template>
  <div class="prototype-app real-app" :class="{ 'sidebar-collapsed': isSidebarCollapsed }">
    <aside
      id="primary-navigation"
      class="prototype-sidebar"
      :class="{ open: isMobileOpen, collapsed: isSidebarCollapsed }"
      :aria-hidden="isResponsiveViewport && !isMobileOpen"
      :inert="isResponsiveViewport && !isMobileOpen"
    >
      <div class="prototype-brand">
        <span class="prototype-logo"><Database :size="20" /></span>
        <span class="prototype-brand-copy"
          ><strong>{{ platformSettings.title }}</strong
          ><small>{{ platformSettings.subtitle }}</small></span
        >
        <button
          class="prototype-icon prototype-close"
          ref="mobileCloseButton"
          aria-label="关闭导航"
          title="关闭导航"
          @click="closeMobileNavigation"
        >
          <X :size="18" />
        </button>
      </div>
      <nav class="prototype-nav" aria-label="功能导航">
        <div
          v-for="group in visibleNavigationGroups"
          :key="group.label"
          class="prototype-nav-group"
        >
          <span class="prototype-nav-label">{{ group.label }}</span>
          <RouterLink
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            :aria-label="item.label"
            :aria-current="isNavigationItemActive(item) ? 'page' : undefined"
            :title="item.label"
            active-class="prototype-router-match"
            exact-active-class="prototype-router-exact"
            :class="{ 'prototype-route-parent': isNavigationItemActive(item) }"
            @click="closeMobileNavigation"
          >
            <component :is="item.icon" :size="17" /><span>{{
              item.label
            }}</span>
          </RouterLink>
        </div>
      </nav>
      <div class="prototype-sidebar-foot">
        <div class="prototype-user">
          <span class="prototype-avatar">{{
            authState.user?.displayName?.slice(0, 1) || '用'
          }}</span>
          <span class="prototype-user-copy">
            <span class="prototype-user-name">
              <strong :title="authState.user?.displayName">{{ authState.user?.displayName }}</strong>
              <span :title="authState.user?.loginName">{{ authState.user?.loginName }}</span>
            </span>
            <small class="prototype-user-role" :title="`角色：${currentRoleNames}；授权机构：${authorizedOrganizationCount} 家`">{{ currentRoleNames }} · {{ authorizedOrganizationCount }} 家机构</small>
            <small :title="`${authState.user?.organizationName}（${authState.user?.organizationCode}）`">{{ authState.user?.organizationName }}</small>
          </span>
        </div>
      </div>
    </aside>
    <div
      v-if="isMobileOpen"
      class="prototype-scrim"
      @click="closeMobileNavigation"
    />

    <main
      class="prototype-main"
      :class="{ 'dashboard-page-shell': route.path === '/', 'directory-page': route.path.startsWith('/master-data/directory') }"
      :inert="isResponsiveViewport && isMobileOpen"
    >
      <header class="prototype-topbar real-page-heading">
        <button
          class="prototype-icon prototype-menu"
          ref="menuToggleButton"
          aria-label="切换导航"
          title="切换导航"
          aria-controls="primary-navigation"
          :aria-expanded="isResponsiveViewport ? isMobileOpen : !isSidebarCollapsed"
          @click="toggleNavigation"
        >
          <Menu :size="20" />
        </button>
        <div class="prototype-heading">
          <!-- 当前页面名称作为面包屑末项，页头与菜单保持同一行。 -->
          <nav class="prototype-breadcrumb" aria-label="页面层级">
            <span>{{ platformSettings.title }}</span>
            <span aria-hidden="true">/</span>
            <span>{{ pageSection }}</span>
            <span aria-hidden="true">/</span>
            <h1>{{ route.meta.title }}</h1>
          </nav>
        </div>
        <span class="prototype-session-status" role="status" :title="`当前登录账号：${authState.user?.loginName || '未知'}`">
          <CheckCircle2 :size="15" aria-hidden="true" />
          <span>会话正常</span>
        </span>
      </header>
      <div class="prototype-content real-content"><RouterView /></div>
      <footer class="prototype-main-footer">
        <div class="prototype-product-meta">
          <ShieldCheck :size="16" />
          <span
            ><strong>{{ platformSettings.version }}</strong
            ><small>{{ platformSettings.subtitle }}</small></span
          >
        </div>
        <button
          class="prototype-footer-logout"
          aria-label="退出登录"
          title="退出登录"
          @click="logout"
        >
          <LogOut :size="16" /><span>退出登录</span>
        </button>
      </footer>
    </main>
  </div>
</template>
