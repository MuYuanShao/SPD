<script setup lang="ts">
/**
 * SpdButton — 全站通用按钮组件
 * =====================================================================
 * 解决的产品问题：
 *   1. 全站按钮**字号不统一** → 字号只由 L1 / L2 两个层级决定，
 *      取自 styles/components/button-typography.css，
 *      自动跟随 <html data-font-size> 三档（小 14 / 中 16 / 大 18）。
 *   2. 全站按钮**颜色不统一** → 颜色只取自 styles/tokens/colors.css，
 *      三套配色方案（标准白 / 静墨宣纸 / 暗夜黑）自动适配。
 *   3. **相同文本的按钮必须完全一致** → tone / variant / size 全部由
 *      label 确定性推导，映射表见 src/config/buttonStyle.ts。
 *
 * 两种用法（都支持）：
 *
 *   A) label 模式（**推荐**）—— 文本与色调由同一个来源决定，
 *      永远不会出现「文字和颜色对不上」：
 *
 *      <SpdButton label="保存" @click="onSave" />
 *      <SpdButton label="删除" :disabled="busy" @click="onDelete" />
 *
 *      样式自动取自 BUTTON_STYLE_BY_LABEL['保存']
 *      === { tone: 'success', variant: 'solid', size: 'default' }。
 *      想改某个按钮的样式 → 改 config/buttonStyle.ts 的映射表，
 *      全站同文本按钮一起变。
 *
 *   B) 默认插槽模式 —— 需要富文本 / 图标组合时使用，
 *      此时**必须显式传 tone**，否则落到默认主色：
 *
 *      <SpdButton tone="danger" @click="onDelete">
 *        <Trash2 :size="16" />
 *        删除
 *      </SpdButton>
 *
 * 作用域说明：
 *   本组件用于**非操作列**按钮（工具栏 / 筛选区 / 弹窗底部 /
 *   页面头部 / 卡片动作 / 表单动作行）。
 *   表格行内的「操作」列按钮（.ulc-action）刻意保持紧凑形态，
 *   不使用本组件。
 *
 * 样式来源：styles/components/spd-button.css（全局，非 scoped），
 *   颜色 / 字号全部来自令牌，本组件内不写任何 px 字号与 hex 色值。
 */
import { computed, ref } from 'vue'
import {
  styleForLabel,
  type SpdButtonSize,
  type SpdButtonTone,
  type SpdButtonVariant
} from '../../config/buttonStyle'

const props = withDefaults(
  defineProps<{
    /**
     * 按钮文本。传入后由它同时决定「显示什么」与「什么颜色」，
     * 保证相同文本的按钮在全站长得一模一样。
     */
    label?: string
    /**
     * 语义色调。显式传入时优先级高于 label 的自动推导。
     */
    tone?: SpdButtonTone
    /**
     * 视觉形态：solid（浅底描边）/ ghost（透明底描边）/ text（纯文字）/ link（下划线）。
     * 未显式传入时由 label 经全局注册表推导。
     */
    variant?: SpdButtonVariant
    /**
     * 尺寸档：default / small。
     * 当前两档几何完全一致（同高同宽同内边距），保留该 prop 仅用于表达意图。
     */
    size?: SpdButtonSize
    /**
     * 原生 button 的 type 属性，默认 button（不会误触发表单提交）。
     */
    type?: 'button' | 'submit' | 'reset'
    /** 禁用 */
    disabled?: boolean
    /** 加载中（显示旋转指示器并置灰、aria-busy） */
    loading?: boolean
    /** 占满容器宽度 */
    block?: boolean
    /** 纯图标按钮（无文字） */
    iconOnly?: boolean
    /** 无障碍标签（iconOnly 时必填） */
    ariaLabel?: string
  }>(),
  {
    label: undefined,
    tone: undefined,
    // variant / size 不设默认值：留空时由 label 经全局注册表推导，
    // 保证「相同文本 → 相同形态 + 相同尺寸」。
    variant: undefined,
    size: undefined,
    type: 'button',
    disabled: false,
    loading: false,
    block: false,
    iconOnly: false,
    ariaLabel: undefined
  }
)

const emit = defineEmits<{
  (e: 'click', event: MouseEvent): void
}>()

/**
 * 最终色调 / 形态 / 尺寸：
 *   1. 显式传入的 prop 优先；
 *   2. 否则按 label 从全局注册表（config/buttonStyle.ts）推导；
 *   3. 都没有 → 注册表默认值。
 *
 * 三个维度统一由注册表驱动，是「相同文本的按钮全站像素级一致」的
 * 实现基础：文本相同 → 色调、形态、尺寸三项全部相同。
 */
const resolvedTone = computed<SpdButtonTone>(() => {
  if (props.tone) return props.tone
  return styleForLabel(props.label).tone
})

const resolvedVariant = computed<SpdButtonVariant>(() => {
  if (props.variant) return props.variant
  return styleForLabel(props.label).variant
})

const resolvedSize = computed<SpdButtonSize>(() => {
  if (props.size) return props.size
  return styleForLabel(props.label).size
})

const rootClasses = computed<Array<string | Record<string, boolean>>>(() => [
  'spd-btn',
  `spd-btn--tone-${resolvedTone.value}`,
  `spd-btn--variant-${resolvedVariant.value}`,
  `spd-btn--size-${resolvedSize.value}`,
  {
    'is-loading': props.loading,
    'is-disabled': props.disabled,
    'spd-btn--block': props.block,
    'spd-btn--icon-only': props.iconOnly
  }
])

function handleClick(event: MouseEvent) {
  if (props.disabled || props.loading) {
    event.preventDefault()
    event.stopPropagation()
    return
  }
  emit('click', event)
}

/* ============================================================================
 * 暴露根 DOM 元素
 * ----------------------------------------------------------------------------
 * Vue 3 中，模板 ref 绑定到**组件**时拿到的是组件的公开实例（暴露代理），
 * 而不是根 DOM 元素。调用方若想测量按钮的位置
 * （例如「列设置」浮层的视口定位 useColSettingsPopover），
 * 必须能拿到真正的 HTMLElement，否则 measure() 会抛 TypeError。
 *
 * 这里显式 defineExpose：
 *   el    —— getter，每次访问都返回当前根元素（响应式更新后依然正确）
 *   $el   —— 兼容常见约定（等同 el）
 */
const rootEl = ref<HTMLButtonElement | null>(null)

defineExpose({
  /** 根按钮元素（getter，元素被替换后依然指向最新实例） */
  get el() {
    return rootEl.value
  },
  /** 与 el 等价的兼容别名 */
  $el: rootEl
})
</script>

<template>
  <button
    ref="rootEl"
    :type="type"
    :class="rootClasses"
    :disabled="disabled || loading"
    :aria-busy="loading ? 'true' : undefined"
    :aria-label="ariaLabel"
    @click="handleClick"
  >
    <span v-if="loading" class="spd-btn__spinner" aria-hidden="true" />
    <span v-else-if="$slots.icon" class="spd-btn__icon" aria-hidden="true">
      <slot name="icon" />
    </span>
    <span v-if="label" class="spd-btn__label">{{ label }}</span>
    <slot v-else />
  </button>
</template>
