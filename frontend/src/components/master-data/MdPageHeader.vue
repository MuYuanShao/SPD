<script setup lang="ts">
import { RefreshCw } from '@lucide/vue'

defineProps<{
  /** 面包屑上级菜单，如 "基础数据管理" */
  crumb?: string
  /** 主标题，如 "医院目录" */
  title: string
  /** 副标题（说明） */
  description?: string
  /** 加载状态 */
  loading?: boolean
  /** 是否显示刷新按钮，默认 true */
  showRefresh?: boolean
}>()

defineEmits<{
  refresh: []
}>()
</script>

<template>
  <header class="md-header">
    <div class="md-header-inner">
      <div class="md-header-text">
        <div v-if="crumb" class="md-header-crumbs">
          <strong>{{ crumb }}</strong>
        </div>
        <h2 class="md-header-title">
          {{ title }}
        </h2>
        <p v-if="description" class="md-header-sub">{{ description }}</p>
      </div>
      <div class="md-header-actions">
        <button
          v-if="showRefresh !== false"
          type="button"
          class="md-btn"
          :disabled="loading"
          aria-label="刷新"
          @click="$emit('refresh')"
        >
          <RefreshCw :size="16" :class="{ 'md-spin': loading }" />
          <span>刷新</span>
        </button>
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.md-spin {
  animation: md-spin 1s linear infinite;
}

@keyframes md-spin {
  to { transform: rotate(360deg); }
}
</style>
