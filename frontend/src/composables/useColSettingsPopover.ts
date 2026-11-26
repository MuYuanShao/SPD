/**
 * 列设置浮层视口钳制
 * =============================================================================
 * 问题：
 * ------------------------------------------------------------------
 * 「列设置」浮层（.ulc-col-settings-pop）默认与按钮左边缘对齐。
 * 当按钮位于工具条**右端**时，268px 宽的浮层会向右溢出视口，
 * 内容被浏览器视口裁切；当按钮位于**左端**、用 right 对齐时，
 * 浮层又会整体被推到屏幕左侧之外 —— 两种情况都会「看起来被截断」。
 * 纵向同理：按钮靠近视口底部时，向下展开的浮层会被下边缘裁掉；
 * 而向上展开时，若浮层高度超过按钮上方的空间，又会顶出视口上缘。
 *
 * 还有一个更隐蔽的问题：浮层原本是 `position: absolute`，
 * 定位基准是包裹按钮的 `.ulc-col-settings`。只要链条上有任意一个祖先
 * 带 `overflow: hidden`（表格容器 .ulc-table-wrap 就是），浮层就会被裁掉，
 * 无论父容器多高都一样。
 *
 * 方案：
 * ------------------------------------------------------------------
 * 用一个轻量的浮层定位钩子在浮层打开时测量视口可用空间，
 * 把**方向**和**可用高度**一并交给浮层：
 *   - 横向：优先与按钮左边缘对齐；右侧空间不足则改为右对齐；
 *   - 纵向：**默认向下展开**；下方确实放不下、且上方更宽敞时改为向上展开；
 *   - 两种情况下都把实测可用高度作为 max-height 传给浮层，
 *     浮层内部的列项列表本身可滚动，因此永远不会溢出视口。
 *
 * 关键实现细节：
 * ------------------------------------------------------------------
 * 1. 锚点既可能是 DOM 元素，也可能是 Vue 组件实例。
 *    模板 ref 绑定到 `<SpdButton>` 这类**组件**时，Vue 3 给出的是组件的
 *    公开实例（暴露代理），不是 HTMLElement —— 直接调用 getBoundingClientRect()
 *    会抛 TypeError，测量中断，maxHeight / offsetY 停留在 undefined，
 *    模板写出 `--ulc-pop-offset-y: undefinedpx`，
 *    CSS 退回 `calc(100% + 8px)` 相对错误的盒子计算，浮层就盖在了按钮上。
 *    这里统一用 resolveAnchorElement() 归一化，两种形态都能拿到真元素。
 *
 * 2. 浮层改为 `position: fixed` + 视口坐标。
 *    这是唯一能保证「不被任意祖先 overflow 裁掉」的做法 ——
 *    absolute 的定位与裁切都受祖先 overflow 影响，fixed 只受视口约束
 *    （除非祖先有 transform / filter / contain 建立包含块，但页面里没有）。
 *    横向依然锚定按钮：left 对齐按钮左边缘，右对齐时对齐按钮右边缘。
 *
 * 3. 绝不写出 `undefinedpx`：值为 undefined 时直接省略该自定义属性，
 *    让 CSS 走默认值；同时 max-height 有下限（MIN_POPOVER_HEIGHT），
 *    保证列项列表始终可用、绝不被压扁。
 *
 * 之所以做成 composable 而不是改每处模板：
 * 列设置浮层的标记在十几个列表里各写了一份，
 * 统一从 composable 注入定位状态，改一处即可全站生效。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

/** 浮层与视口边缘的最小留白（px） */
const VIEWPORT_MARGIN = 12
/** 浮层与按钮之间的间距（px） */
const ANCHOR_GAP = 8
/** 浮层最小可用高度（px）：低于此值时列项列表会几乎不可用 */
const MIN_POPOVER_HEIGHT = 160
/** 浮层最大可用高度（px）：再高也没有意义，避免超高浮层遮挡整屏 */
const MAX_POPOVER_HEIGHT = 480
/** 浮层默认宽度（px），与 CSS 中的 width 保持一致 */
const DEFAULT_POPOVER_WIDTH = 268

/**
 * 可能作为锚点的形态：
 * - HTMLElement：直接就是 DOM 元素
 * - Vue 组件公开实例：暴露了 `el` / `$el` 的组件实例
 */
export type ColSettingsAnchor = HTMLElement | null | undefined | {
  el?: unknown
  $el?: unknown
}

/**
 * 把锚点归一化为真正的 DOM 元素。
 *
 * 模板 ref 绑定到组件时拿到的是组件公开实例，
 * 这里依次尝试 `instance.el` / `instance.$el`，
 * 并在拿到值后再次确认它是 HTMLElement。
 */
export function resolveAnchorElement(anchor: ColSettingsAnchor): HTMLElement | null {
  if (!anchor) return null
  if (anchor instanceof HTMLElement) return anchor

  const instance = anchor as { el?: unknown; $el?: unknown }
  const candidates: unknown[] = [instance.el, instance.$el]

  for (const candidate of candidates) {
    if (candidate instanceof HTMLElement) return candidate
    // 组件实例可能再包一层（expose 的是另一个组件实例）
    if (candidate && typeof candidate === 'object') {
      const nested = candidate as { el?: unknown; $el?: unknown }
      for (const deeper of [nested.el, nested.$el]) {
        if (deeper instanceof HTMLElement) return deeper
      }
    }
  }
  return null
}

export interface UseColSettingsPopoverOptions {
  /** 浮层是否打开 */
  open: Ref<boolean>
  /** 触发按钮的 ref（DOM 元素或 Vue 组件实例均可） */
  anchorRef: Ref<ColSettingsAnchor>
  /**
   * 浮层元素的 ref。
   * 可以是 DOM 元素，也可以是组件实例
   * （例如共享的 <SpdColumnSettings> 组件 —— 它 expose 了根元素 `el`）。
   */
  popRef: Ref<ColSettingsAnchor>
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
   * 浮层相对视口顶部的纵向坐标（px）。
   *
   * 浮层使用 `position: fixed`，所以这里存的是**视口坐标**。
   * 向下展开时：top = 按钮底边 + 间距。
   * 向上展开时：改用 bottom = 视口高度 - 按钮顶边 + 间距，
   *              由浏览器按浮层**实际高度**向上生长，无需预先知道高度。
   */
  const offsetY = ref<number | undefined>(undefined)
  /**
   * 浮层相对视口底部的距离（px），仅在向上展开时使用。
   * 用 bottom 而不是 top 来定位，浏览器会按真实高度向上展开，
   * 因此**永远不会盖住触发按钮**。
   */
  const offsetBottom = ref<number | undefined>(undefined)
  /** 浮层相对视口左侧的横向坐标（px），用于 position: fixed 下的水平锚定 */
  const offsetX = ref<number | undefined>(undefined)

  /** 测量锚点与视口，按可用空间决定方向与高度 */
  function measure() {
    const anchor = resolveAnchorElement(options.anchorRef.value)
    // 浮层可能是 DOM 元素，也可能是组件实例（共享的 <SpdColumnSettings>），
    // 统一归一化成 DOM 元素后再量尺寸。
    const pop = resolveAnchorElement(options.popRef.value)
    if (!anchor || !pop) return

    const anchorRect = anchor.getBoundingClientRect()
    const popWidth = pop.offsetWidth || DEFAULT_POPOVER_WIDTH
    const viewportWidth = window.innerWidth
    const viewportHeight = window.innerHeight

    // 横向：右侧剩余空间不足以放下浮层时，改为与按钮右边缘对齐（向左展开）
    const spaceRight = viewportWidth - anchorRect.left - VIEWPORT_MARGIN
    align.value = spaceRight < popWidth ? 'right' : 'left'

    // 横向坐标：与按钮左边缘（或右边缘）严格对齐，
    // 并保证浮层整体不超出视口左右边缘。
    if (align.value === 'left') {
      offsetX.value = Math.max(
        VIEWPORT_MARGIN,
        Math.min(anchorRect.left, viewportWidth - popWidth - VIEWPORT_MARGIN)
      )
    } else {
      const rightAligned = anchorRect.right
      offsetX.value = Math.max(
        VIEWPORT_MARGIN,
        Math.min(rightAligned - popWidth, viewportWidth - popWidth - VIEWPORT_MARGIN)
      )
    }

    // 纵向：分别算出按钮上下两侧的可用空间
    const spaceBelow = viewportHeight - anchorRect.bottom - ANCHOR_GAP - VIEWPORT_MARGIN
    const spaceAbove = anchorRect.top - ANCHOR_GAP - VIEWPORT_MARGIN

    // 默认向下展开；只有当下方**确实**放不下最小可用高度、
    // 且上方空间更大时，才改为向上展开。
    // 向下 → top = 按钮底边 + 间距（浮层在按钮下方，绝不重叠）
    // 向上 → bottom = 视口高度 - 按钮顶边 + 间距
    //         （浮层底边始终在按钮上边缘之上，绝不重叠）
    if (spaceBelow >= MIN_POPOVER_HEIGHT || spaceAbove <= spaceBelow) {
      vertical.value = 'down'
      maxHeight.value = clampHeight(spaceBelow)
      offsetY.value = anchorRect.bottom + ANCHOR_GAP
      offsetBottom.value = undefined
    } else {
      vertical.value = 'up'
      maxHeight.value = clampHeight(spaceAbove)
      offsetY.value = undefined
      offsetBottom.value = viewportHeight - anchorRect.top + ANCHOR_GAP
    }
  }

  /**
   * 把实测空间夹到 [MIN_POPOVER_HEIGHT, MAX_POPOVER_HEIGHT]。
   * 下限保证浮层永远可用（列表可滚动），上限避免浮层过高遮挡整屏。
   */
  function clampHeight(space: number): number {
    if (!Number.isFinite(space)) return MIN_POPOVER_HEIGHT
    return Math.min(MAX_POPOVER_HEIGHT, Math.max(MIN_POPOVER_HEIGHT, Math.round(space)))
  }

  /**
   * 数值 → px 字符串；值为 undefined / null / NaN 时返回 undefined，
   * 这样 Vue 绑定自定义属性时会**省略该属性**，而不是写出 `undefinedpx`。
   */
  function toPx(value: number | undefined | null): string | undefined {
    if (value === undefined || value === null || !Number.isFinite(value)) return undefined
    return `${Math.round(value)}px`
  }

  /**
   * 浮层的内联样式（模板里直接 :style="xxx.popStyle" 即可）。
   *
   * 之所以收敛成一个对象：
   *   1. 值未测量出来时**整条属性被省略**，CSS 回退到默认值，
   *      绝不会出现 `--ulc-pop-offset-y: undefinedpx` 这种无效值；
   *   2. 集中管理新增的自定义属性，调用方不需要知道内部细节。
   */
  const popStyle = computed<Record<string, string | undefined>>(() => ({
    '--ulc-pop-offset-x': toPx(offsetX.value),
    '--ulc-pop-offset-y': toPx(offsetY.value),
    '--ulc-pop-bottom': toPx(offsetBottom.value),
    '--ulc-pop-max-h': toPx(maxHeight.value)
  }))

  /** 打开浮层后测量一次（等 DOM 更新完再量） */
  watch(
    () => options.open.value,
    async (isOpen) => {
      if (!isOpen) {
        maxHeight.value = undefined
        offsetY.value = undefined
        offsetBottom.value = undefined
        offsetX.value = undefined
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

  function handleWindowScroll() {
    if (options.open.value) measure()
  }

  onMounted(() => {
    window.addEventListener('resize', handleWindowResize)
    // 捕获阶段监听 document 上的 scroll：页面的真实滚动容器是 .spd-content，
    // 滚动事件不会冒泡到 window，必须用捕获阶段 + document 才能收到，
    // 否则浮层会停留在旧坐标，出现「浮层与按钮错位」。
    document.addEventListener('scroll', handleWindowScroll, true)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleWindowResize)
    document.removeEventListener('scroll', handleWindowScroll, true)
  })

  return { align, vertical, maxHeight, offsetY, offsetBottom, offsetX, popStyle, measure }
}
