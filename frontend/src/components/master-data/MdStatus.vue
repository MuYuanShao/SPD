<script setup lang="ts">
import { computed } from 'vue'

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
</script>

<template>
  <span :class="['md-status', variant]">{{ label }}</span>
</template>
