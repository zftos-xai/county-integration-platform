import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setupPermissionGuard } from './permission'
import '@tabler/core/dist/css/tabler.min.css'
import './assets/styles/base.css'
import './assets/styles/prototype.css'

// 权限守卫必须在挂载前注册，避免首次导航短暂显示未授权页面。
setupPermissionGuard(router)
createApp(App).use(router).mount('#app')
