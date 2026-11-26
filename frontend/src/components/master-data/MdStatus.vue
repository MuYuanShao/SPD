<script setup lang="ts">
import { computed } from 'vue'
import type { SpdStatusChipTone } from '../common/SpdStatusChip.vue'

type Status = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const props = defineProps<{
  /** 状态文本 */
  label: string
  /** 预设类型 */
  variant?: Status
  /** 自动根据文本推断 variant（success 文本 -> success 状态）*/
  auto?: boolean
}>()

const variant = computed<Status>(() => {
  if (props.variant) return props.variant
  if (!props.auto) return 'neutral'
  const t = props.label
  if (t.includes('启用') || t.includes('有效') || t.includes('正常') || t.includes('成功') || t.includes('已') || t === 'OK') {
    return 'success'
  }
  if (t.includes('停') || t.includes('失效') || t.includes('禁用') || t.includes('失败') || t.includes('错误')) {
    return 'danger'
  }
  if (t.includes('待') || t.includes('审核') || t.includes('进行') || t.includes('部分')) {
    return 'warning'
  }
  return 'neutral'
})

/**
 * 状态条色调：与全站通用状态条组件 SpdStatusChip 的 tone 语义一一对应。
 * master-data 的 neutral（灰）语义在通用组件里同样用 neutral。
 */
const tone = computed<SpdStatusChipTone>(() => variant.value)
</script>

<template>
  <SpdStatusChip :tone="tone" :label="label" />
</template>
