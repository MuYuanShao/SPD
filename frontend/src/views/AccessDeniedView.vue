<script setup lang="ts">
import { ArrowLeft, ShieldX } from '@lucide/vue'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="access-denied-page" role="alert">
    <ShieldX :size="48" aria-hidden="true" />
    <h2>无权访问该功能</h2>
    <p>当前账号未获得此菜单或操作权限，请联系系统管理员授权。</p>
    <SpdButton tone="neutral" @click="router.back()">
      <template #icon><ArrowLeft :size="17" aria-hidden="true" /></template>
      返回上一页
    </SpdButton>
  </main>
</template>

<style scoped>
.access-denied-page {
  display: grid;
  justify-items: center;
  gap: 12px;
  max-width: 620px;
  margin: 80px auto;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--spd-p-bg);
  padding: 48px 24px;
  color: var(--spd-p-text-secondary);
  text-align: center;
}

.access-denied-page > svg { color: var(--spd-p-danger-strong); }
.access-denied-page h2 { margin: 0; color: var(--spd-p-text); }
.access-denied-page p { margin: 0 0 8px; }
</style>
