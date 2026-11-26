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
  background: var(--spd-p-header-gradient);
  border: 0;
  border-radius: 14px;
  box-shadow: var(--spd-p-shadow-2);
  color: var(--spd-p-bg);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  overflow: hidden;
}

.md-stat-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--spd-p-bg) 18%, transparent) 0%, transparent 50%);
  pointer-events: none;
}

.md-stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--spd-p-shadow-3);
}

.md-stat-card.tone-primary { background: var(--spd-p-header-gradient); }
.md-stat-card.tone-success { background: var(--spd-p-success); }
.md-stat-card.tone-warning { background: linear-gradient(135deg, var(--spd-p-warning) 0%, var(--spd-p-warning-strong) 100%); }
.md-stat-card.tone-danger  { background: var(--spd-p-danger); }
.md-stat-card.tone-neutral { background: linear-gradient(135deg, var(--spd-p-text-secondary) 0%, var(--spd-p-bg-muted) 100%); }

.md-stat-card.tone-primary:hover { box-shadow: var(--spd-p-shadow-3); }
.md-stat-card.tone-success:hover { box-shadow: var(--spd-p-shadow-3); }
.md-stat-card.tone-warning:hover { box-shadow: var(--spd-p-shadow-3); }
.md-stat-card.tone-danger:hover  { box-shadow: var(--spd-p-shadow-3); }

.md-stat-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: color-mix(in srgb, var(--spd-p-bg) 92%, transparent);
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
}

.md-stat-icon {
  color: color-mix(in srgb, var(--spd-p-bg) 92%, transparent);
  flex-shrink: 0;
}

.md-stat-card.tone-success .md-stat-icon { color: color-mix(in srgb, var(--spd-p-bg) 92%, transparent); }
.md-stat-card.tone-warning .md-stat-icon { color: color-mix(in srgb, var(--spd-p-bg) 92%, transparent); }
.md-stat-card.tone-danger  .md-stat-icon { color: color-mix(in srgb, var(--spd-p-bg) 92%, transparent); }
.md-stat-card.tone-neutral .md-stat-icon { color: color-mix(in srgb, var(--spd-p-bg) 92%, transparent); }

.md-stat-label {
  color: color-mix(in srgb, var(--spd-p-bg) 92%, transparent);
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  letter-spacing: 0.2px;
}

.md-stat-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: var(--spd-p-bg);
  font-family: var(--md-font-mono);
  font-size: var(--spd-font-size-2xl);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: var(--spd-lh-tight);
  letter-spacing: -0.5px;
  text-shadow: 0 1px 0 color-mix(in srgb, var(--spd-p-text-strong) 8%, transparent);
}

.md-stat-value small {
  font-family: var(--md-font);
  font-size: var(--spd-font-size-xs);
  font-weight: 500;
  color: color-mix(in srgb, var(--spd-p-bg) 85%, transparent);
  letter-spacing: 0;
}

.md-stat-card.tone-primary .md-stat-value { color: var(--spd-p-bg); }
.md-stat-card.tone-success .md-stat-value { color: var(--spd-p-bg); }
.md-stat-card.tone-warning .md-stat-value { color: var(--spd-p-bg); }
.md-stat-card.tone-danger  .md-stat-value { color: var(--spd-p-bg); }
.md-stat-card.tone-neutral .md-stat-value { color: var(--spd-p-bg); }

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
