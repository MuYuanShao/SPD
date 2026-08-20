<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogIn, ShieldCheck } from '@lucide/vue'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const uiStore = useUiStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submitLogin() {
  error.value = ''
  loading.value = true
  uiStore.showLoading('正在登录...')

  try {
    await authStore.login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  } finally {
    loading.value = false
    uiStore.hideLoading()
  }
}
</script>

<template>
  <section class="login-layout standalone-login">
    <div class="login-panel">
      <div class="login-brand">
        <span class="brand-mark">SPD</span>
        <div>
          <strong>院内 SPD</strong>
          <small>供应链管理平台</small>
        </div>
      </div>
      <div class="section-title">
        <ShieldCheck :size="22" />
        <h2>院内 SPD 登录</h2>
      </div>
      <form @submit.prevent="submitLogin">
      <label>
        <span>用户名</span>
        <input v-model="username" type="text" autocomplete="username" placeholder="请输入用户名" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
      </label>
      <p v-if="error" class="error-text login-error">{{ error }}</p>
      <button class="btn btn-primary" type="submit" :disabled="loading">
        <LogIn :size="18" />
        {{ loading ? '登录中...' : '登录' }}
      </button>
      </form>
    </div>
  </section>
</template>

<style scoped>
.login-panel .btn-primary {
  width: 100%;
  min-height: 42px;
}
</style>
