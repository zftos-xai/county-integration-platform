import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import DashboardView from './views/DashboardView.vue'
import PlaceholderView from './views/PlaceholderView.vue'
import PrototypeView from './views/PrototypeView.vue'
import '@tabler/core/dist/css/tabler.min.css'
import './styles/base.css'
import './styles/prototype.css'

const routes = [
  { path: '/prototype', component: PrototypeView, meta: { title: '业务原型' } },
  { path: '/', component: DashboardView, meta: { title: '运行总览' } },
  { path: '/organizations', component: PlaceholderView, meta: { title: '机构与权限' } },
  { path: '/interfaces', component: PlaceholderView, meta: { title: '接口与映射' } },
  { path: '/exchanges', component: PlaceholderView, meta: { title: '交换记录' } },
  { path: '/exceptions', component: PlaceholderView, meta: { title: '异常与处理' } },
  { path: '/data-review', component: PlaceholderView, meta: { title: '数据核查' } },
  { path: '/audit', component: PlaceholderView, meta: { title: '审计记录' } },
]

const router = createRouter({ history: createWebHistory(), routes })
createApp(App).use(router).mount('#app')
