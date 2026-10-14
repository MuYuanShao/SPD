<script setup lang="ts">
/**
 * 主数据 4 联统计卡片
 *
 * <MdStatsRow :items="[
 *   { label: '总数', value: 128, tone: 'primary' },
 *   { label: '已集采', value: 32, tone: 'success' },
 *   ...
 * ]" />
 */
import type { Component } from 'vue'

export interface MdStatItem {
  /** 大字显示的标签，如 "医院商品总数" */
  label: string
  /** 数值 */
  value: number | string
  /** 副标题/单位说明 */
  hint?: string
  /** 主题色调：primary / success / warning / danger / neutral */
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral'
  /** 可选图标组件 */
  icon?: Component
}

withDefaults(
  defineProps<{
    items: MdStatItem[]
  }>(),
  { items: () => [] }
)
</script>

<template>
  <div class="md-stats-row">
    <div
      v-for="(item, idx) in items"
      :key="idx"
      class="md-stat-card"
      :class="`tone-${item.tone || 'primary'}`"
    >
      <div class="md-stat-head">
        <component
          :is="item.icon"
          v-if="item.icon"
          :size="18"
          aria-hidden="true"
          class="md-stat-icon"
        />
        <span class="md-stat-label">{{ item.label }}</span>
      </div>
      <div class="md-stat-value">
        {{ item.value }}
        <small v-if="item.hint">{{ item.hint }}</small>
      </div>
    </div>
  </div>
</template>

<style scoped>
.md-stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.md-stat-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 18px;
  background: var(--md-bg-card);
  border: 1px solid var(--md-border);
  border-left: 3px solid var(--md-primary);
  border-radius: var(--md-radius-md);
  box-shadow: var(--md-shadow-1);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  overflow: hidden;
}

.md-stat-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 100% 0%, rgba(13, 107, 99, 0.06) 0%, transparent 50%);
  pointer-events: none;
}

.md-stat-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--md-shadow-2);
}

.md-stat-card.tone-success { border-left-color: #047857; }
.md-stat-card.tone-success::after { background: radial-gradient(circle at 100% 0%, rgba(4, 120, 87, 0.08) 0%, transparent 50%); }
.md-stat-card.tone-warning { border-left-color: #b45309; }
.md-stat-card.tone-warning::after { background: radial-gradient(circle at 100% 0%, rgba(180, 83, 9, 0.08) 0%, transparent 50%); }
.md-stat-card.tone-danger { border-left-color: #b91c1c; }
.md-stat-card.tone-danger::after { background: radial-gradient(circle at 100% 0%, rgba(185, 28, 28, 0.08) 0%, transparent 50%); }
.md-stat-card.tone-neutral { border-left-color: #64748b; }
.md-stat-card.tone-neutral::after { background: radial-gradient(circle at 100% 0%, rgba(100, 116, 139, 0.08) 0%, transparent 50%); }

.md-stat-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--md-text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.md-stat-icon {
  color: var(--md-primary);
  flex-shrink: 0;
}

.md-stat-card.tone-success .md-stat-icon { color: #047857; }
.md-stat-card.tone-warning .md-stat-icon { color: #b45309; }
.md-stat-card.tone-danger .md-stat-icon { color: #b91c1c; }
.md-stat-card.tone-neutral .md-stat-icon { color: #64748b; }

.md-stat-label {
  color: var(--md-text-secondary);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

.md-stat-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: var(--md-text-heading);
  font-family: var(--md-font-mono);
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  letter-spacing: -0.4px;
}

.md-stat-value small {
  font-family: var(--md-font);
  font-size: 12px;
  font-weight: 500;
  color: var(--md-text-secondary);
  letter-spacing: 0;
}

.md-stat-card.tone-primary .md-stat-value { color: var(--md-primary-strong); }
.md-stat-card.tone-success .md-stat-value { color: #047857; }
.md-stat-card.tone-warning .md-stat-value { color: #b45309; }
.md-stat-card.tone-danger .md-stat-value { color: #b91c1c; }
.md-stat-card.tone-neutral .md-stat-value { color: #334155; }

@media (max-width: 1100px) {
  .md-stats-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .md-stats-row {
    grid-template-columns: 1fr;
  }
}
</style>
