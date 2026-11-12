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
  <h2 class="md-page-accessible-title">{{ title }}</h2>
  <div v-if="showRefresh !== false || $slots.actions" class="md-page-actions">
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
</template>

<style scoped>
.md-page-accessible-title {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.md-page-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.md-spin {
  animation: md-spin 1s linear infinite;
}

@keyframes md-spin {
  to { transform: rotate(360deg); }
}
</style>
