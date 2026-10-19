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
  gap: 10px;
  padding: 16px 18px;
  background: linear-gradient(135deg, #06b6d4 0%, #6366f1 50%, #8b5cf6 100%);
  border: 0;
  border-radius: 14px;
  box-shadow: 0 4px 14px -4px rgba(99, 102, 241, 0.4);
  color: #ffffff;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  overflow: hidden;
}

.md-stat-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 100% 0%, rgba(255, 255, 255, 0.18) 0%, transparent 50%);
  pointer-events: none;
}

.md-stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px -6px rgba(99, 102, 241, 0.55);
}

.md-stat-card.tone-primary { background: linear-gradient(135deg, #06b6d4 0%, #6366f1 50%, #8b5cf6 100%); }
.md-stat-card.tone-success { background: linear-gradient(135deg, #059669 0%, #0d9488 100%); }
.md-stat-card.tone-warning { background: linear-gradient(135deg, #f59e0b 0%, #ea580c 100%); }
.md-stat-card.tone-danger  { background: linear-gradient(135deg, #dc2626 0%, #e11d48 100%); }
.md-stat-card.tone-neutral { background: linear-gradient(135deg, #475569 0%, #1e293b 100%); }

.md-stat-card.tone-primary:hover { box-shadow: 0 8px 22px -6px rgba(99, 102, 241, 0.55); }
.md-stat-card.tone-success:hover { box-shadow: 0 8px 22px -6px rgba(5, 150, 105, 0.55); }
.md-stat-card.tone-warning:hover { box-shadow: 0 8px 22px -6px rgba(234, 88, 12, 0.55); }
.md-stat-card.tone-danger:hover  { box-shadow: 0 8px 22px -6px rgba(220, 38, 38, 0.55); }

.md-stat-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.92);
  font-size: 13px;
  font-weight: 500;
}

.md-stat-icon {
  color: rgba(255, 255, 255, 0.92);
  flex-shrink: 0;
}

.md-stat-card.tone-success .md-stat-icon { color: rgba(255, 255, 255, 0.92); }
.md-stat-card.tone-warning .md-stat-icon { color: rgba(255, 255, 255, 0.92); }
.md-stat-card.tone-danger  .md-stat-icon { color: rgba(255, 255, 255, 0.92); }
.md-stat-card.tone-neutral .md-stat-icon { color: rgba(255, 255, 255, 0.92); }

.md-stat-label {
  color: rgba(255, 255, 255, 0.92);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

.md-stat-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: #ffffff;
  font-family: var(--md-font-mono);
  font-size: 30px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  letter-spacing: -0.5px;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.08);
}

.md-stat-value small {
  font-family: var(--md-font);
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
  letter-spacing: 0;
}

.md-stat-card.tone-primary .md-stat-value { color: #ffffff; }
.md-stat-card.tone-success .md-stat-value { color: #ffffff; }
.md-stat-card.tone-warning .md-stat-value { color: #ffffff; }
.md-stat-card.tone-danger  .md-stat-value { color: #ffffff; }
.md-stat-card.tone-neutral .md-stat-value { color: #ffffff; }

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
