<script setup lang="ts">
/**
 * ModalMask — 通用遮罩层容器
 *
 * 业务页面内的弹窗 mask 通常嵌在 `<div class="main-area">`（overflow: auto）下，
 * 这会导致：
 *   1. position: fixed 的 mask 被 overflow 容器裁切/限制在内部
 *   2. mask 与外部 Element Plus 消息（ElMessage 等）出现 stacking context 冲突
 *
 * 这个组件把内容 Teleport 到 body 末尾，脱离 overflow 容器和 main-area 的层叠上下文，
 * 让 mask 真正铺满视口、并且 ElMessage 之类的全局浮层能正常显示在 mask 之上。
 *
 * 用法（替换原来的 `<div class="modal-mask ...">`）：
 *   <ModalMask v-if="open" class="modal-mask purchase-entry-mask" @click.self="open = false" @keydown.esc="open = false">
 *     ... 弹窗内容 ...
 *   </ModalMask>
 */
import { onBeforeUnmount, onMounted } from 'vue'

const emit = defineEmits<{
  (e: 'close'): void
}>()

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.stopPropagation()
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <slot />
  </Teleport>
</template>
