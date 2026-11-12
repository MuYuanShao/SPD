<script setup lang="ts">
/**
 * 通用分页器（与 MdDataTableCard 底部 slot 配合）
 * 基于 Element Plus 的 el-pagination 原生组件实现：
 * - total：总条数
 * - sizes：每页条数选择（Element Plus 原生 select）
 * - prev / pager / next：上一页、页码、下一页
 * - jumper：输入页码跳转
 * 组件自身不做额外样式覆盖，保持 Element Plus 原生行为。
 */
import { computed } from 'vue'

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

const DEFAULT_PAGE_SIZES = [10, 20, 50, 100]

/**
 * Element Plus 的 el-select 在 value 不属于 options 时只显示原始值，
 * 不会追加「条/页」后缀（例如当前每页 15 条却只显示「15」）。
 * 这里把当前 size 补进选项，保证显示完整。
 */
const pageSizes = computed(() => {
  const current = Number(props.size)
  if (!Number.isFinite(current) || current <= 0) return DEFAULT_PAGE_SIZES
  return DEFAULT_PAGE_SIZES.includes(current) ? DEFAULT_PAGE_SIZES : [...DEFAULT_PAGE_SIZES, current].sort((a, b) => a - b)
})

function handleCurrentChange(next: number) {
  if (next === props.page) return
  emit('update:page', next)
}

function handleSizeChange(next: number) {
  emit('update:size', next)
}
</script>

<template>
  <div class="md-pagination">
    <el-pagination
      :current-page="page"
      :page-size="size"
      :page-sizes="pageSizes"
      :total="total"
      :disabled="loading"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>
