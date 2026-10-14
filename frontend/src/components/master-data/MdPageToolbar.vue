<script setup lang="ts">
/**
 * 通用业务查询 + 操作工具栏
 * 左侧 primary 操作（新增 / 修改 / 批量）
 * 右侧 secondary 操作（导入 / 导出 / 列设置）
 * 中间可插入筛选 chip / 已选统计
 */
defineProps<{
  /** 左侧已选数量提示文字（例：已选 12 条） */
  selectedInfo?: string
}>()
</script>

<template>
  <div class="md-toolbar">
    <div v-if="$slots.primary || selectedInfo" class="md-toolbar-primary">
      <slot v-if="selectedInfo" name="selected">
        <span class="md-toolbar-selected">{{ selectedInfo }}</span>
        <span class="md-toolbar-divider" />
      </slot>
      <slot name="primary" />
    </div>
    <div v-if="$slots.secondary" class="md-toolbar-secondary">
      <slot name="secondary" />
    </div>
  </div>
</template>

<style scoped>
.md-toolbar-selected {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  background: var(--md-primary-soft);
  color: var(--md-primary-strong);
  border: 1px solid rgba(13, 107, 99, 0.24);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
</style>
