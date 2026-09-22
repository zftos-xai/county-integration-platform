<!-- 首次登录强制改密页面：成功后结束旧会话并返回登录页。 -->
<script setup lang="ts">
import { Database, KeyRound } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authState, updatePassword } from '@/store/modules/auth'
import { ApiClientError } from '@/utils/request'
import { isValidPasswordSize } from '@/utils/validation'

const router = useRouter()
const currentPassword = ref('')
const newPassword = ref('')
const confirmation = ref('')
const error = ref<ApiClientError | null>(null)
const localError = ref('')
const isSubmitDisabled = computed(() => !currentPassword.value || !newPassword.value || !confirmation.value || authState.isLoading)

async function submit() {
  localError.value = ''
  error.value = null
  if (!isValidPasswordSize(newPassword.value)) {
    localError.value = '新密码至少 9 个字符，UTF-8 编码不能超过 72 字节'
    return
  }
  if (newPassword.value !== confirmation.value) {
    localError.value = '两次输入的新密码不一致'
    return
  }
  try {
    await updatePassword(currentPassword.value, newPassword.value)
    await router.replace({ path: '/login', query: { changed: '1' } })
  } catch (caught) {
    error.value = caught instanceof ApiClientError
      ? caught
      : new ApiClientError('UNKNOWN_ERROR', '密码修改失败，请稍后重试', 0)
  }
}
</script>

<template>
  <main class="prototype-login real-login">
    <section class="prototype-login-panel single-panel">
      <div class="prototype-login-mobile-brand always"><Database :size="20" /><strong>县域接口平台</strong></div>
      <form class="prototype-login-card" @submit.prevent="submit">
        <div class="prototype-login-card-head"><span class="prototype-login-card-icon"><KeyRound :size="20" /></span><div><span>首次登录保护</span><h2>修改初始密码</h2><p>修改成功后会注销当前会话</p></div></div>
        <div class="prototype-login-message warning">当前账号仅有修改密码权限，完成后请重新登录。</div>
        <div v-if="localError || error" class="prototype-login-message danger" role="alert"><strong>{{ localError || error?.message }}</strong><small v-if="error?.requestId">请求编号：{{ error.requestId }}</small></div>
        <label for="current-password">当前密码</label><input id="current-password" v-model="currentPassword" type="password" autocomplete="current-password" maxlength="128" />
        <label for="new-password">新密码</label><input id="new-password" v-model="newPassword" type="password" autocomplete="new-password" maxlength="128" placeholder="至少 9 个字符" />
        <label for="confirm-password">确认新密码</label><input id="confirm-password" v-model="confirmation" type="password" autocomplete="new-password" maxlength="128" />
        <button class="prototype-login-submit" type="submit" :disabled="isSubmitDisabled">{{ authState.isLoading ? '正在修改…' : '修改密码并退出' }}</button>
      </form>
    </section>
  </main>
</template>
