<script setup lang="ts">
import { Inbox } from '@lucide/vue'

defineProps<{
  /** 主提示文字 */
  text?: string
  /** 副提示 */
  hint?: string
  /** 加载状态 */
  loading?: boolean
}>()
</script>

<template>
  <div v-if="loading" class="md-loading">
    <div class="md-loading-spinner" />
    <span>正在加载...</span>
  </div>
  <div v-else class="md-empty">
    <Inbox :size="48" :stroke-width="1.5" />
    <strong>{{ text || '暂无数据' }}</strong>
    <p v-if="hint">{{ hint }}</p>
    <slot />
  </div>
</template>
