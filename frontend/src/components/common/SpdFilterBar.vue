<script setup lang="ts">
/**
 * SpdFilterBar — 全站统一筛选栏组件
 * =====================================================================
 * 产品硬约束（用户诉求）：
 *   「筛选框也写一个组件，保持完全一致，筛选框的高度也要一样，
 *     筛选组件要有标准规则，如多宽需要换行，对应的按钮要放在哪里，
 *     都写一个统一的组件」
 *
 * 因此本组件负责**筛选栏的全部外观（chrome）**，调用方只负责**字段内容**：
 *   - 栅格列数与换行规则（标准规则，见下方「标准规则」注释）；
 *   - 标签排版（标题在控件上方）；
 *   - 输入控件高度（严格等于 --spd-p-field-height）；
 *   - 操作区位置（宽屏靠右独立成区，窄屏整行靠右）。
 *
 * 两种用法：
 *
 *   A) 默认操作区（**推荐**）—— 组件自己渲染「查询」「重置」，
 *      按钮顺序与位置全站一致，页面无需重复写按钮：
 *
 *      <SpdFilterBar @submit="load" @reset="reset">
 *        <label><span>商品编码</span><input v-model="q.code" class="md-input" /></label>
 *        <label><span>状态</span><el-select v-model="q.status" /></label>
 *      </SpdFilterBar>
 *
 *   B) 自定义操作区 —— 需要额外按钮（如「更多 / 收起」）时使用
 *      `actions` 插槽，此时**不再**渲染默认的「查询 / 重置」：
 *
 *      <SpdFilterBar collapsible :expanded="expanded" @toggle="expanded = !expanded">
 *        ...
 *        <template #actions>
 *          <SpdButton label="更多" tone="neutral" @click="..." />
 *        </template>
 *      </SpdFilterBar>
 *
 * 按钮顺序（产品规则）：**「查询」在前，「重置」在后**，
 * 两者都带图标（查询 = Search，重置 = RefreshCw）；
 * 折叠按钮（更多 / 收起）排在最左。
 *
 * 按钮统一使用 SpdButton（全站唯一按钮组件），本组件**不引入第二套按钮样式**。
 *
 * 样式来源：styles/components/spd-filter-bar.css（全局，非 scoped），
 *   颜色 / 尺寸全部取自 tokens/palette.css，本组件内不写任何 hex 与 px 字号。
 */
import { computed, useSlots } from 'vue'
import { ChevronDown, RefreshCw, Search } from '@lucide/vue'
import SpdButton from './SpdButton.vue'

const props = withDefaults(
  defineProps<{
    /**
     * 是否启用「更多 / 收起」折叠能力。
     * 开启后需配合 `expanded` + `@toggle` 使用；
     * 也可只传 `collapsible` 并监听 `toggle`，由页面自行维护状态。
     */
    collapsible?: boolean
    /**
     * 折叠状态（受控）。传 true 表示当前展开全部字段。
     */
    expanded?: boolean
    /** 「更多」按钮文案 */
    moreLabel?: string
    /** 「收起」按钮文案 */
    lessLabel?: string
    /** 默认操作区的「查询」按钮文案 */
    submitLabel?: string
    /** 默认操作区的「重置」按钮文案 */
    resetLabel?: string
    /** 查询按钮是否处于加载中 */
    loading?: boolean
    /** 重置按钮是否禁用 */
    resetDisabled?: boolean
    /**
     * 标签宽度。省略时为「自适应」（由标签文字长度决定）。
     * 需要标签列对齐时可传 CSS 长度值，如 '84px'。
     */
    labelWidth?: string | number
    /**
     * 显式指定每行字段列数。
     * 省略时使用标准规则：响应式 auto-fit（见 spd-filter-bar.css）。
     */
    fieldsPerRow?: number
    /**
     * 标签对齐方式，默认跟随内容（start）。
     */
    labelAlign?: 'start' | 'end'
  }>(),
  {
    collapsible: false,
    expanded: true,
    moreLabel: '更多',
    lessLabel: '收起',
    submitLabel: '查询',
    resetLabel: '重置',
    loading: false,
    resetDisabled: false,
    labelWidth: undefined,
    fieldsPerRow: undefined,
    labelAlign: 'start'
  }
)

const emit = defineEmits<{
  /** 点击「查询」 */
  (e: 'submit'): void
  /** 点击「重置」 */
  (e: 'reset'): void
  /** 折叠状态切换（true 表示展开，false 表示收起） */
  (e: 'toggle', expanded: boolean): void
}>()

const slots = useSlots()

/**
 * 是否渲染默认操作区（「重置」+「查询」）。
 * 调用方提供了 `actions` 插槽时，由调用方完全掌控操作区内容与顺序。
 */
const hasCustomActions = computed(() => Boolean(slots.actions))

/**
 * 显式列数 → CSS 变量 `--spd-filter-columns`。
 * 省略时返回 undefined，由样式表按标准规则（auto-fit）接管。
 */
const columnsStyle = computed<Record<string, string> | undefined>(() => {
  if (!props.fieldsPerRow || props.fieldsPerRow < 1) return undefined
  return { '--spd-filter-columns': String(props.fieldsPerRow) }
})

/**
 * 标签宽度 → CSS 变量 `--spd-filter-label-width`。
 * 省略时为 undefined（自适应）。
 */
const labelWidthStyle = computed<Record<string, string> | undefined>(() => {
  const raw = props.labelWidth
  if (raw === undefined || raw === null || raw === '') return undefined
  const value = typeof raw === 'number' ? `${raw}px` : raw
  return { '--spd-filter-label-width': value }
})

function handleToggle() {
  emit('toggle', !props.expanded)
}
</script>

<template>
  <div
    class="spd-filter-bar"
    :class="{ 'is-collapsible': collapsible, 'is-expanded': expanded }"
    :style="{ ...columnsStyle, ...labelWidthStyle }"
  >
    <!-- 字段区：栅格列数与换行规则由样式表统一控制 -->
    <div class="spd-filter-bar__fields">
      <slot />
    </div>

    <!-- 操作区：宽屏固定在字段栅格右侧（独立区域，不挤占栅格列） -->
    <div class="spd-filter-bar__actions">
      <slot name="actions">
        <!--
          按钮顺序（产品规则）：**「查询」在前，「重置」在后**。
          折叠按钮（更多 / 收起）排在最左，它只是显隐切换，不参与主次。
          两个动作按钮都带图标：查询 = Search，重置 = RefreshCw。
          注意：这里的顺序与 styles/components/spd-filter-bar.css 里
          「标准规则 2」的旧描述（重置 → 查询）相反 —— 旧描述已过时，
          以本组件的 DOM 顺序为准；该样式表的布局规则（flex + gap +
          justify-content: flex-end）与按钮先后无关，无需同步改动。
        -->
        <SpdButton
          v-if="collapsible"
          :label="expanded ? lessLabel : moreLabel"
          tone="neutral"
          @click="handleToggle"
        >
          <template #icon>
            <ChevronDown :size="16" :class="{ rotated: expanded }" />
          </template>
        </SpdButton>
        <SpdButton
          :label="submitLabel"
          tone="primary"
          type="submit"
          :loading="loading"
          @click="emit('submit')"
        >
          <template #icon><Search :size="16" /></template>
        </SpdButton>
        <SpdButton
          :label="resetLabel"
          tone="neutral"
          :disabled="resetDisabled"
          @click="emit('reset')"
        >
          <template #icon><RefreshCw :size="16" /></template>
        </SpdButton>
      </slot>
    </div>
  </div>
</template>
