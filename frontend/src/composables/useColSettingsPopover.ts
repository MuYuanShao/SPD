/**
 * 列设置浮层视口钳制
 * =============================================================================
 * 问题：
 * ------------------------------------------------------------------
 * 「列设置」浮层（.ulc-col-settings-pop）默认与按钮左边缘对齐。
 * 当按钮位于工具条**右端**时，268px 宽的浮层会向右溢出视口，
 * 内容被浏览器视口裁切；当按钮位于**左端**、用 right 对齐时，
 * 浮层又会整体被推到屏幕左侧之外 —— 两种情况都会「看起来被截断」。
 *
 * 纵向同理：按钮靠近视口底部时，向下展开的浮层会被下边缘裁掉；
 * 而向上展开时，若浮层高度超过按钮上方的空间，又会顶出视口上缘。
 *
 * 方案：
 * ------------------------------------------------------------------
 * 用一个轻量的浮层定位钩子在浮层打开时测量视口可用空间，
 * 把**方向**和**可用高度**一并交给浮层：
 *   - 横向：优先与按钮左边缘对齐；右侧空间不足则改为右对齐；
 *   - 纵向：下方空间足够则向下展开，否则向上展开；
 *   - 两种情况下都把实测可用高度作为 max-height 传给浮层，
 *     浮层内部的列项列表本身可滚动，因此永远不会溢出视口。
 *
 * 关键实现细节：
 * ------------------------------------------------------------------
 * 浮层是 `position: absolute`，定位基准是包裹按钮的 `.ulc-col-settings`
 * （高约 34px），**不是按钮本身**。因此不能靠 CSS 的百分比
 * （如 `bottom: calc(100% + 8px)`）来表达"按钮上方 8px"——
 * 那会相对包裹盒计算，把浮层顶出视口。
 * 所以方向与高度都由 JS 实测视口后写成内联样式，位置才准确。
 *
 * 之所以做成 composable 而不是改每处模板：
 * 列设置浮层的标记在十几个列表里各写了一份，
 * 统一从 composable 注入定位状态，改一处即可全站生效。
 */
import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

/** 浮层与视口边缘的最小留白（px） */
const VIEWPORT_MARGIN = 12
/** 浮层与按钮之间的间距（px） */
const ANCHOR_GAP = 8
/** 浮层最小可用高度（px）：低于此值时列项列表会几乎不可用 */
const MIN_POPOVER_HEIGHT = 160

export interface UseColSettingsPopoverOptions {
  /** 浮层是否打开 */
  open: Ref<boolean>
  /** 触发按钮的 ref（用于测量锚点位置） */
  anchorRef: Ref<HTMLElement | null | undefined>
  /** 浮层元素的 ref */
  popRef: Ref<HTMLElement | null | undefined>
}

export function useColSettingsPopover(options: UseColSettingsPopoverOptions) {
  /**
   * 横向定位模式：
   * - 'left'  : 与按钮左边缘对齐（默认）
   * - 'right' : 与按钮右边缘对齐，向左展开
   */
  const align = ref<'left' | 'right'>('left')
  /**
   * 纵向定位模式：
   * - 'down' : 向下展开（默认）
   * - 'up'   : 向上展开（按钮靠近视口底部时）
   */
  const vertical = ref<'down' | 'up'>('down')
  /** 浮层可用的最大高度（px），由上下两侧空间实测得出 */
  const maxHeight = ref<number | undefined>(undefined)
  /**
   * 浮层相对定位基准（.ulc-col-settings 包裹盒）的纵向偏移（px）。
   * 由 JS 实测按钮位置后写入，避免 CSS 百分比相对错误的盒子计算。
   */
  const offsetY = ref<number | undefined>(undefined)

  /** 测量锚点与视口，按可用空间决定方向与高度 */
  function measure() {
    const anchor = options.anchorRef.value
    const pop = options.popRef.value
    if (!anchor || !pop) return

    const anchorRect = anchor.getBoundingClientRect()
    const popWidth = pop.offsetWidth || 268
    const viewportWidth = window.innerWidth
    const viewportHeight = window.innerHeight

    // 横向：右侧剩余空间不足以放下浮层时，改为与按钮右边缘对齐（向左展开）
    const spaceRight = viewportWidth - anchorRect.left - VIEWPORT_MARGIN
    align.value = spaceRight < popWidth ? 'right' : 'left'

    // 纵向：分别算出按钮上下两侧的可用空间
    const spaceBelow = viewportHeight - anchorRect.bottom - ANCHOR_GAP - VIEWPORT_MARGIN
    const spaceAbove = anchorRect.top - ANCHOR_GAP - VIEWPORT_MARGIN

    // 优先向下展开；下方确实放不下、且上方更宽敞时改为向上展开。
    // 两侧都不够时，仍选空间较大的一侧，靠 max-height + 内部滚动兜底。
    //
    // 纵向偏移以浮层的定位基准（.ulc-col-settings 包裹盒）为参照：
    //   向下 → 用 `top`，偏移 = 按钮底边相对包裹盒顶部的距离 + 间距
    //   向上 → 用 `bottom`，偏移 = 包裹盒高度 + 间距
    //          （浮层从这条基线向上生长，max-height 决定它能长多高）
    // 这里直接用按钮与包裹盒的实际几何差算出，保证像素级准确。
    const wrapper = pop.offsetParent as HTMLElement | null
    const wrapperRect = wrapper ? wrapper.getBoundingClientRect() : anchorRect
    if (spaceBelow >= MIN_POPOVER_HEIGHT || spaceAbove <= spaceBelow) {
      vertical.value = 'down'
      maxHeight.value = Math.max(spaceBelow, MIN_POPOVER_HEIGHT)
      offsetY.value = anchorRect.bottom - wrapperRect.top + ANCHOR_GAP
    } else {
      vertical.value = 'up'
      maxHeight.value = Math.max(spaceAbove, MIN_POPOVER_HEIGHT)
      offsetY.value = wrapperRect.height + ANCHOR_GAP
    }
  }

  /** 打开浮层后测量一次（等 DOM 更新完再量） */
  watch(
    () => options.open.value,
    async (isOpen) => {
      if (!isOpen) {
        maxHeight.value = undefined
        offsetY.value = undefined
        return
      }
      await nextTick()
      measure()
      // 浮层渲染后宽度才确定，再测一次更稳妥
      await nextTick()
      measure()
    },
    { flush: 'post' }
  )

  function handleWindowResize() {
    if (options.open.value) measure()
  }

  onMounted(() => {
    window.addEventListener('resize', handleWindowResize)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleWindowResize)
  })

  return { align, vertical, maxHeight, offsetY, measure }
}
