<script setup lang="ts">
/**
 * 通用分页器（与 MdDataTableCard 底部 slot 配合）
 */
import { ChevronLeft, ChevronRight } from '@lucide/vue'

const props = defineProps<{
  /** 当前页 */
  page: number
  /** 总页数 */
  totalPages: number
  /** 总条数 */
  total: number
  /** 每页大小 */
  size: number
  /** 加载中 */
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:page': [page: number]
  'update:size': [size: number]
}>()

function go(delta: number) {
  const next = props.page + delta
  if (next < 1 || next > props.totalPages) return
  emit('update:page', next)
}
</script>

<template>
  <div class="md-pagination">
    <div class="md-pagination-info">
      <span class="md-pagination-info-text">
        共 <strong>{{ total }}</strong> 条
      </span>
      <span class="md-pagination-info-size">
        每页
        <select
          class="md-select"
          :value="size"
          :disabled="loading"
          @change="emit('update:size', Number(($event.target as HTMLSelectElement).value))"
        >
          <option v-for="s in [10, 20, 50, 100]" :key="s" :value="s">{{ s }}</option>
        </select>
        条
      </span>
    </div>
    <div class="md-pagination-controls">
      <button class="md-btn md-btn-sm" :disabled="loading || page <= 1" @click="go(-1)">
        <ChevronLeft :size="14" /> 上一页
      </button>
      <span class="md-page-indicator">第 {{ page }} / {{ totalPages || 1 }} 页</span>
      <button class="md-btn md-btn-sm" :disabled="loading || page >= totalPages" @click="go(1)">
        下一页 <ChevronRight :size="14" />
      </button>
    </div>
  </div>
</template>
