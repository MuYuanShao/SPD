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
    <button class="btn btn-primary" type="button" @click="router.back()">
      <ArrowLeft :size="17" aria-hidden="true" />
      返回上一页
    </button>
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
  background: #fff;
  padding: 48px 24px;
  color: #52677a;
  text-align: center;
}

.access-denied-page svg { color: #b42318; }
.access-denied-page h2 { margin: 0; color: #102033; }
.access-denied-page p { margin: 0 0 8px; }
</style>
