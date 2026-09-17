import { createApp } from 'vue'
import App from './App.vue'
import './styles/base.css'

// 嵌入端暂不注册路由或业务插件，直至安全上下文合同完成评审。
createApp(App).mount('#app')
