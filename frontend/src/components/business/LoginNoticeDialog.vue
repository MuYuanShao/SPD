<script setup lang="ts">
import { ref, watch } from 'vue'
import { acknowledgeSystemNotice, claimSystemNotice, type SystemNotice } from '../../api/systemNotices'
import { useAuthStore } from '../../stores/auth'
import StatusMessage from '../common/StatusMessage.vue'

const auth = useAuthStore()
const notice = ref<SystemNotice | null>(null)
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const confirmError = ref('')
let loginId = ''

function newLoginId() {
  // getRandomValues is available on hospital intranet HTTP as well as HTTPS.
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-')
}

watch(() => [auth.completedLogins, auth.token] as const, ([completed, token], previous) => {
  loginId = ''
  notice.value = null
  loading.value = false
  saving.value = false
  loadError.value = ''
  confirmError.value = ''
  if (!token || !completed || completed === previous?.[0]) return
  loginId = newLoginId()
  void loadNotice()
})

async function loadNotice() {
  if (!loginId || loading.value) return
  const request = loginId
  loading.value = true
  try {
    let result: SystemNotice | null
    try {
      result = await claimSystemNotice(request)
    } catch {
      if (request !== loginId) return
      result = await claimSystemNotice(request)
    }
    if (request === loginId) {
      notice.value = result
      loadError.value = ''
    }
  } catch {
    if (request === loginId) loadError.value = '系统公告加载失败，请重试'
  } finally {
    if (request === loginId) loading.value = false
  }
}

async function confirmNotice() {
  if (!notice.value || saving.value) return
  const request = loginId
  saving.value = true
  confirmError.value = ''
  try {
    await acknowledgeSystemNotice(notice.value.id)
    if (request === loginId) {
      notice.value = null
      void loadNotice()
    }
  } catch {
    if (request === loginId) confirmError.value = '确认失败，请重试'
  } finally {
    if (request === loginId) saving.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="Boolean(notice || loadError)" title="系统公告" width="min(560px, 92vw)"
    :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
    <template v-if="notice">
      <h3>{{ notice.title }}</h3>
      <time>{{ notice.date }}</time>
      <p class="notice-content">{{ notice.content }}</p>
      <StatusMessage :message="confirmError" tone="error" role="alert" />
    </template>
    <StatusMessage v-else :message="loadError" tone="error" role="alert" />
    <template #footer>
      <SpdButton v-if="notice" label="我知道了" tone="primary" :loading="saving" @click="confirmNotice"></SpdButton>
      <template v-else>
        <SpdButton label="稍后查看" tone="neutral" :disabled="loading" @click="loadError = ''"></SpdButton>
        <SpdButton label="重试" tone="primary" :loading="loading" @click="loadNotice"></SpdButton>
      </template>
    </template>
  </el-dialog>
</template>

<style scoped>
.notice-content {
  max-height: 48vh;
  overflow: auto;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  line-height: 1.65;
}
</style>
