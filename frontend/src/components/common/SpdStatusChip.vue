<script setup lang="ts">
/**
 * SpdStatusChip — 全站通用状态条（列表里的状态标签）
 * =====================================================================
 * 产品诉求（硬约束）：
 *   「列表状态条的样式也要统一，你需要选择一些市面系统的常用字体和
 *     背景色搭配，不要使用太深的颜色作为背景色，把状态条也变成通用组件，
 *     内边距和圆角都要规定好，所有列表使用这样的状态条的都要统一，
 *     给列表使用」
 *
 * 因此本组件是**全站唯一的列表状态条实现**：
 *   - 几何（高度 / 内边距 / 圆角 / 边框）全部取自
 *     styles/tokens/palette.css 的「8b 状态条几何」；
 *   - 配色全部取自「8c 状态条色调」；
 *   - 底色一律**浅色染色**，文字一律**深色**，
 *     对比度在标准白 / 静墨宣纸 / 暗夜黑三套方案下均 ≥ 4.5:1。
 *
 * 用法：
 *
 *   A) label 模式（最常用）
 *
 *      <SpdStatusChip tone="success" label="已启用" />
 *
 *   B) 插槽模式（需要富文本时）
 *
 *      <SpdStatusChip tone="warning">
 *        待审批 <strong>3</strong>
 *      </SpdStatusChip>
 *
 *   C) 前导圆点（dot）
 *
 *      <SpdStatusChip tone="danger" dot label="异常" />
 *
 * 语义映射建议（与 config/buttonStyle.ts 的按钮色调保持同一套语义）：
 *   启用 / 通过 / 正常 → success
 *   停用 / 驳回 / 异常 → danger
 *   待办 / 预警 / 超期 → warning
 *   处理中 / 进行中   → info
 *   主流程 / 当前项   → primary
 *   其它 / 未分类     → neutral
 *
 * 样式来源：styles/components/spd-status-chip.css（全局，非 scoped），
 *   颜色 / 尺寸全部来自令牌，本组件内不写任何 hex 与 px 字号。
 */
import { computed } from 'vue'

/** 状态条语义色调 */
export type SpdStatusChipTone = 'success' | 'danger' | 'warning' | 'info' | 'primary' | 'neutral'

/** 状态条尺寸档 */
export type SpdStatusChipSize = 'sm' | 'md'

const props = withDefaults(
  defineProps<{
    /** 语义色调，决定底色 / 边框色 / 文字色 */
    tone?: SpdStatusChipTone
    /** 状态条文本（与默认插槽二选一） */
    label?: string
    /** 尺寸档：sm（22px）/ md（24px，默认） */
    size?: SpdStatusChipSize
    /** 是否显示前导圆点 */
    dot?: boolean
    /**
     * 悬浮提示文本（显示完整文字）。
     * 不传时默认取 label —— 文字过长被裁切时悬浮可见全文。
     */
    title?: string
  }>(),
  {
    tone: 'neutral',
    label: undefined,
    size: 'md',
    dot: false,
    title: undefined
  }
)

const rootClasses = computed<Array<string | Record<string, boolean>>>(() => [
  'spd-status-chip',
  `spd-status-chip--tone-${props.tone}`,
  {
    'spd-status-chip--sm': props.size === 'sm',
    'has-dot': props.dot
  }
])

/**
 * 悬浮提示文本。
 * 状态条允许换行增高（用户明确要求），但列宽有限时文字仍可能被裁切；
 * 这里把完整文本挂到 title 上，悬浮即可看到全文
 * （原生 tooltip，零 JS 依赖、可访问）。
 * 未显式传入 title 时，用 label 兜底。
 */
const tipText = computed<string | undefined>(() => {
  if (props.title) return props.title
  return props.label || undefined
})
</script>

<template>
  <span :class="rootClasses" :title="tipText">
    <span v-if="dot" class="spd-status-chip__dot" aria-hidden="true" />
    <span v-if="label" class="spd-status-chip__label">{{ label }}</span>
    <slot v-else />
  </span>
</template>
