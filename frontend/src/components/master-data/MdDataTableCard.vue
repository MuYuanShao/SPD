<script setup lang="ts">
/**
 * 通用数据表卡片
 * - 顶部工具条 toolbar slot：操作按钮 + 右侧 info（metaCount / meta-extra slot）
 * - 中部 table slot
 * - 底部 pagination slot
 * - 完整卡片样式（白底 + 1px 边框 + 8px 圆角 + 浅阴影）
 */
defineProps<{
  /** meta 计数（如 "共 128 条"），渲染在 toolbar 右侧 */
  metaCount?: string | number
  /** loading 状态 */
  loading?: boolean
}>()
</script>

<template>
  <div class="md-table-card">
    <div v-if="$slots.toolbar" class="md-table-toolbar">
      <div class="md-table-toolbar-main">
        <slot name="toolbar" />
      </div>
      <div v-if="metaCount || $slots['meta-extra']" class="md-table-toolbar-info">
        <span v-if="metaCount" class="md-chip primary">{{ metaCount }}</span>
        <!-- 具名 slot 名必须与父组件的 #meta-extra 完全一致，否则内容不会渲染 -->
        <slot name="meta-extra" />
      </div>
    </div>
    <div class="md-table-wrap">
      <slot />
    </div>
    <slot name="pagination" />
  </div>
</template>
