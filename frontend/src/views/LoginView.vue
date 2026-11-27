<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  LogIn,
  ShieldCheck,
  User,
  Lock,
  Eye,
  EyeOff,
  AlertCircle,
  Loader2,
  Stethoscope,
  Boxes,
  ClipboardCheck,
  Network,
  Activity
} from '@lucide/vue'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { APP_VERSION } from '../config/appVersion'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const uiStore = useUiStore()

const username = ref('')
const password = ref('')
const remember = ref(true)
const showPassword = ref(false)
const error = ref('')
const loading = ref(false)

const submitDisabled = computed(() => loading.value || !username.value || !password.value)

async function submitLogin() {
  if (submitDisabled.value) return
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
    <!-- 左侧品牌墙 -->
    <aside class="login-aside">
      <!-- 装饰：心电波 + 圆环 + 抽象网格 -->
      <div class="login-aside-art" aria-hidden="true">
        <svg viewBox="0 0 600 900" preserveAspectRatio="xMidYMid slice">
          <defs>
            <linearGradient id="aside-line" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stop-color="var(--spd-p-bg)" stop-opacity="0" />
              <stop offset="50%" stop-color="var(--spd-p-bg)" stop-opacity="0.45" />
              <stop offset="100%" stop-color="var(--spd-p-bg)" stop-opacity="0" />
            </linearGradient>
            <linearGradient id="aside-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="var(--spd-p-info)" stop-opacity="0.35" />
              <stop offset="100%" stop-color="var(--spd-p-info)" stop-opacity="0" />
            </linearGradient>
          </defs>
          <!-- 心电波 -->
          <path
            d="M 0 360 L 80 360 L 110 320 L 140 400 L 170 280 L 200 440 L 230 360 L 320 360 L 350 320 L 380 400 L 410 360 L 600 360"
            fill="none"
            stroke="url(#aside-line)"
            stroke-width="1.5"
          />
          <path
            d="M 0 360 L 80 360 L 110 320 L 140 400 L 170 280 L 200 440 L 230 360 L 320 360 L 350 320 L 380 400 L 410 360 L 600 360 L 600 600 L 0 600 Z"
            fill="url(#aside-fill)"
            opacity="0.4"
          />
          <!-- 圆环 -->
          <circle cx="500" cy="780" r="180" fill="none" stroke="var(--spd-p-bg)" stroke-opacity="0.12" stroke-width="1" />
          <circle cx="500" cy="780" r="130" fill="none" stroke="var(--spd-p-bg)" stroke-opacity="0.18" stroke-width="1" />
          <circle cx="500" cy="780" r="80" fill="none" stroke="var(--spd-p-bg)" stroke-opacity="0.24" stroke-width="1" />
          <!-- 抽象六边形（医疗分子感） -->
          <g transform="translate(80 760)" fill="none" stroke="var(--spd-p-bg)" stroke-opacity="0.18" stroke-width="1">
            <polygon points="40,0 80,23 80,69 40,92 0,69 0,23" />
            <polygon points="100,46 140,69 140,115 100,138 60,115 60,69" />
            <polygon points="40,92 80,115 80,161 40,184 0,161 0,115" />
          </g>
        </svg>
      </div>

      <header class="login-brand">
        <span class="brand-mark">SPD</span>
        <div class="brand-text">
          <strong>院内 SPD 供应链</strong>
          <small>Hospital Supply Chain Platform</small>
        </div>
      </header>

      <div class="login-aside-hero">
        <h1>
          让院内物资流转
          <br />
          <span class="accent">更透明、更高效</span>
        </h1>
        <p>
          覆盖供应商、厂家、库房到临床科室的全链路供给与结算，
          让耗材、定数包与高值备件流转可追溯、可审计。
        </p>
      </div>

      <ul class="login-aside-features">
        <li>
          <span class="ico"><Stethoscope :size="14" /></span>
          <span>临床科室请领 / 配送</span>
        </li>
        <li>
          <span class="ico"><Boxes :size="14" /></span>
          <span>库房与货位实时台账</span>
        </li>
        <li>
          <span class="ico"><Network :size="14" /></span>
          <span>定数包与 UDI 追溯</span>
        </li>
        <li>
          <span class="ico"><ClipboardCheck :size="14" /></span>
          <span>证照 / 批号 / 效期监管</span>
        </li>
      </ul>

      <div class="login-aside-stats" aria-hidden="true">
        <div class="stat">
          <strong>{{ 42 }}</strong>
          <span>接入库房</span>
        </div>
        <div class="stat">
          <strong>{{ 680 }}</strong>
          <span>在用商品</span>
        </div>
        <div class="stat">
          <strong>{{ 12 }}</strong>
          <span>在途定数包</span>
        </div>
      </div>

      <div class="login-aside-foot">
        <span>© {{ new Date().getFullYear() }} 院内供应链中心</span>
        <span class="divider" />
        <span>持续集成部署</span>
      </div>
    </aside>

    <!-- 右侧登录区 -->
    <main class="login-main">
      <div class="login-blob" aria-hidden="true" />
      <div class="login-blob-2" aria-hidden="true" />
      <div class="login-blob-3" aria-hidden="true" />
      <div class="login-panel">
        <header class="login-panel-head">
          <h2>
            <span class="ico"><ShieldCheck :size="18" /></span>
            <span>账户登录</span>
          </h2>
          <p>请使用院内 SPD 账号登录；忘记密码请联系本院系统管理员重置。</p>
        </header>

        <form @submit.prevent="submitLogin" novalidate>
          <label>
            <div class="lbl">
              <span class="lbl-text">
                <span class="lbl-ico"><User :size="13" /></span>
                <span>用户名</span>
                <span class="required">*</span>
              </span>
            </div>
            <div class="login-field">
              <span class="lead"><User :size="16" /></span>
              <input
                v-model="username"
                type="text"
                autocomplete="username"
                placeholder="请输入用户名"
                spellcheck="false"
              />
            </div>
          </label>

          <label>
            <div class="lbl">
              <span class="lbl-text">
                <span class="lbl-ico"><Lock :size="13" /></span>
                <span>密码</span>
                <span class="required">*</span>
              </span>
            </div>
            <div class="login-field">
              <span class="lead"><Lock :size="16" /></span>
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
              />
              <span class="trail">
                <button
                  type="button"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <component :is="showPassword ? EyeOff : Eye" :size="16" />
                </button>
              </span>
            </div>
          </label>

          <div class="login-panel-row">
            <label class="remember">
              <input v-model="remember" type="checkbox" />
              <span>记住此账号</span>
            </label>
          </div>

          <p
            v-if="error || authStore.initializationError"
            role="alert"
            class="login-error"
          >
            <AlertCircle :size="14" />
            <span>{{ error || authStore.initializationError }}</span>
          </p>

          <button
            type="submit"
            class="login-submit"
            :class="{ 'is-loading': loading }"
            :disabled="submitDisabled"
          >
            <component :is="loading ? Loader2 : LogIn" :size="18" />
            <span>{{ loading ? '登录中…' : '登 录' }}</span>
          </button>
        </form>

        <div class="login-panel-foot">
          <span>院内供应链中心 · {{ APP_VERSION }}</span>
          <span class="divider" />
          <span>技术支持 <Activity :size="11" style="vertical-align: -1px" /> Online</span>
        </div>
      </div>
    </main>
  </section>
</template>

<style scoped>
/* =====================================================================
 * 登录页输入框样式
 * ---------------------------------------------------------------------
 * 输入框外壳（.login-field）与框内图标的完整规格写在
 * styles/views/login.css 里（与登录页其余样式同源，便于统一维护）。
 *
 * 【本文件为什么必须保留这些覆盖】
 *   styles/components/spd-input.css（在 main.ts 里排在 main.css 之后引入）
 *   对原生 input 有一条特指度 (0,9,1) 的通用规则：
 *     input:not([type=checkbox]):not([type=radio])… {
 *       height: 34px; padding: 0 10px;
 *       border: 1px solid …; border-radius: 4px; background: …;
 *     }
 *   它的特指度远高于 .login-field input，
 *   会把「外壳画框 + 内层透明文本区」的结构打回「框里又画一个实心小框」，
 *   同时把图标留位用的 padding 改成 10px → 文字压住左侧图标。
 *   因此这里用 scoped（特指度更高）+ !important 把内层 input 清成无框。
 * ===================================================================== */

/* 内层文本区：无框、透明、撑满外壳 */
.login-field input {
  border: 0 !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  outline: none;
  height: 100% !important;
  min-height: 0 !important;
  max-height: none !important;
  /* 左侧让开前置图标，右侧让开尾部「显示密码」按钮 */
  padding: 0 36px 0 38px !important;
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-field-text);
  caret-color: var(--el-color-primary, var(--spd-p-info));
}

.login-field input::placeholder {
  color: var(--spd-p-field-placeholder);
}

/* ---------- 标签行：图标 + 文案 + 必填星号 ---------- */
.login-panel label > .lbl .lbl-text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.login-panel label > .lbl .lbl-ico {
  display: inline-grid;
  place-items: center;
  color: var(--spd-p-text-muted);
}
</style>
