<!-- 正式登录页面：使用服务端会话，不展示或保存演示凭据。 -->
<script setup lang="ts">
import { Eye, EyeOff, LogIn } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authState, authenticate } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const loginName = ref('')
const password = ref('')
const isPasswordVisible = ref(false)
const error = ref<ApiClientError | null>(null)
const loginErrorMessage = computed(() => {
  if (!error.value) return ''
  if (error.value.code === 'INVALID_RESPONSE') return '登录服务暂时异常，请稍后重试。'
  if (error.value.code === 'NETWORK_ERROR') return '暂时无法连接登录服务，请检查网络后重试。'
  if (error.value.code === 'REQUEST_TIMEOUT') return '登录请求超时，请稍后重试。'
  return error.value.message
})
const sessionNotice = computed(() => {
  if (route.query.changed === '1') return '密码已修改，会话已注销。请使用新密码重新登录。'
  if (route.query.scopeChanged === '1') return '机构已创建，账号的数据范围已更新。请重新登录以加载最新权限。'
  if (route.query.authorizationChanged === '1') return '当前账号的角色或机构范围已更新。请重新登录以加载最新权限。'
  if (route.query.accountChanged === '1') return '当前账号状态已更新，会话已结束。请重新登录。'
  if (route.query.passwordReset === '1') return '当前账号密码已重置，会话已结束。请使用新密码重新登录。'
  return ''
})

async function submit() {
  error.value = null
  try {
    const user = await authenticate(loginName.value, password.value)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/'
    await router.replace(user.mustChangePassword ? '/change-password' : redirect)
  } catch (caught) {
    error.value = caught instanceof ApiClientError
      ? caught
      : new ApiClientError('UNKNOWN_ERROR', '登录失败，请稍后重试', 0)
  }
}
</script>

<template>
  <main class="prototype-login real-login">
    <section class="prototype-login-panel single-panel">
      <form class="prototype-login-card" @submit.prevent="submit">
        <div class="prototype-login-card-head"><span class="prototype-login-card-icon"><LogIn :size="20" /></span><div><h1>管理端登录</h1><p>县域接口平台</p></div></div>
        <div v-if="sessionNotice" class="prototype-login-message success" role="status">{{ sessionNotice }}</div>
        <div v-if="error" class="prototype-login-message danger" role="alert">{{ loginErrorMessage }}</div>
        <label for="login-name">登录名</label><input id="login-name" v-model="loginName" name="username" autocomplete="username" maxlength="64" autofocus placeholder="请输入登录名" />
        <label for="login-password">密码</label><div class="prototype-password-field"><input id="login-password" v-model="password" name="password" :type="isPasswordVisible ? 'text' : 'password'" autocomplete="current-password" maxlength="128" placeholder="请输入密码" /><button type="button" :aria-label="isPasswordVisible ? '隐藏密码' : '显示密码'" @click="isPasswordVisible = !isPasswordVisible"><EyeOff v-if="isPasswordVisible" :size="17" /><Eye v-else :size="17" /></button></div>
        <button class="prototype-login-submit" type="submit" :disabled="!loginName.trim() || !password || authState.isLoading">{{ authState.isLoading ? '正在登录…' : '登录' }}</button>
        <p class="prototype-login-help">页面不会保存密码；登录失败会进入安全审计。</p>
      </form>
      <p class="prototype-login-footer">仅限获授权人员使用 · 所有管理操作均可审计</p>
    </section>
  </main>
</template>
