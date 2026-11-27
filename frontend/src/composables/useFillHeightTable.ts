/**
 * 表格铺满可用高度（统一列表页共用）
 * =============================================================================
 * 目标：列表页的**主数据表格**不再被硬编码的 `max-height="560"` 截断，
 *      而是随视口高度伸展到「内容区底部 - 分页条」为止。
 *
 * 为什么需要一个共享 composable：
 * ------------------------------------------------------------------
 * 早期每个页面各自写了一份「测量内容区高度 -> 减去表格上方占用 -> 减去分页预留」
 * 的逻辑，既重复又容易各自算错（有的用 window.innerHeight，有的用
 * document.body.clientHeight），不同分辨率下出现大片留白或表格被压扁。
 * 这里把测量规则收敛成一份，各页面只声明「我要哪个表格 ref」。
 *
 * 测量规则：
 * ------------------------------------------------------------------
 *   available = 容器可视高度 - 表格顶部相对容器顶部的偏移 - 分页区预留
 *   maxHeight = max(minHeight, available)
 *
 * - 容器：优先取表格自身所在的 `.spd-content`（页面内容滚动区），
 *   没有则退回 `.main-area`，再没有就退回表格的父元素。
 * - 表格顶部偏移：表格上方还有标题、筛选区、工具栏等元素，
 *   这些都要从可用高度里扣掉，否则表格会把分页条顶出可视区。
 * - minHeight：内容区很矮（窄窗口 / 小屏）时不再继续压缩，
 *   保证表格至少还能显示几行，而不是被压成一条缝。
 *
 * max-height 是**上限**而不是固定高度：
 * Element Plus 仍然按内容撑开表格体，行数少时表格不会被拉伸出大片空白。
 *
 * 重新测量的时机：
 * ------------------------------------------------------------------
 * 1. ResizeObserver 观察容器（视口/侧边栏变化）与表格（行数变化）；
 * 2. window resize；
 * 3. 字号档位变化 —— 应用把档位写在 `<html data-font-size>` 上，
 *    行高随之改变，因此监听该属性的 MutationObserver。
 *
 * 清理：组件卸载时断开所有观察器与监听，避免 keep-alive 之外的内存泄漏。
 */
import { onBeforeUnmount, onMounted, reactive, ref, type Ref } from 'vue'

export interface FillHeightTableOptions {
  /**
   * el-table 的组件实例 ref。
   * 通过 `$el` 拿到真实 DOM 节点，用于测量与观察。
   */
  tableRef?: Ref<any>
  /**
   * 最小可用高度（px）。
   * 低于此值时不再压缩，避免表格只剩一两行。
   * 默认 260。
   */
  minHeight?: number
  /**
   * 表格下方分页区预留高度（px）：分页条 + 间距。
   * 默认 76。
   */
  paginationReserve?: number
  /**
   * 是否启用测量。返回 false 时保持当前高度不变。
   * 用于「表格尚未渲染 / 页面处于隐藏态」等场景。
   * 默认恒为 true。
   */
  enabled?: () => boolean
}

/** 最小可用高度（px）默认值 */
const DEFAULT_MIN_HEIGHT = 260
/** 分页区预留高度（px）默认值 */
const DEFAULT_PAGINATION_RESERVE = 76

/**
 * 在给定的元素里向上查找最近的内容滚动容器。
 * 优先 `.spd-content`（应用主内容区），其次 `.main-area`，最后回退到自身。
 */
function resolveContentContainer(el: HTMLElement | null): HTMLElement | null {
  if (!el) return null
  const fromTable = el.closest('.spd-content, .main-area') as HTMLElement | null
  if (fromTable) return fromTable
  return el.parentElement
}

/**
 * 让列表页主表格填满可用高度的 composable。
 *
 * 返回值是 `reactive` 对象：Vue 的 reactive 会自动解包属性上的 ref，
 * 因此模板里可以直接写 `:max-height="fillHeightXxx.maxHeight"`，
 * 拿到的是数字而不是 Ref 对象（顶层 ref 才会被模板自动解包）。
 */
export function useFillHeightTable(options: FillHeightTableOptions = {}) {
  const minHeight = options.minHeight ?? DEFAULT_MIN_HEIGHT
  const paginationReserve = options.paginationReserve ?? DEFAULT_PAGINATION_RESERVE

  /**
   * 表格最大高度（px），由 measure() 更新。
   * 初值取 minHeight：在首次测量完成前（表格尚未渲染 / 容器尚未布局），
   * 表格仍有一个合理的高度，而不是被 max-height=0 压成一条缝。
   */
  const maxHeight = ref(minHeight)

  let containerResizeObserver: ResizeObserver | undefined
  let tableResizeObserver: ResizeObserver | undefined
  let fontSizeObserver: MutationObserver | undefined
  let observedContainer: HTMLElement | null = null
  let observedTable: HTMLElement | null = null
  let frame = 0

  /** 读取表格根 DOM 节点 */
  function resolveTableEl(): HTMLElement | null {
    const el = options.tableRef?.value?.$el as HTMLElement | undefined
    return el ?? null
  }

  /**
   * 重新计算表格可用高度。
   * 任何一步拿不到必要节点时都不抛异常，只保持当前值。
   */
  function measure() {
    if (typeof document === 'undefined') return
    if (options.enabled && options.enabled() === false) return

    const tableEl = resolveTableEl()
    if (!tableEl) return

    const container = resolveContentContainer(tableEl)
    if (!container) return

    // 容器可视高度；为 0 说明容器尚未布局（例如隐藏页签），跳过本轮
    const containerClientH = container.clientHeight
    if (!containerClientH) return

    // 表格顶部相对容器顶部的偏移（标题 + 筛选区 + 工具栏等）
    const containerRect = container.getBoundingClientRect()
    const tableRect = tableEl.getBoundingClientRect()
    const offsetTop = Math.max(0, Math.round(tableRect.top - containerRect.top + container.scrollTop))

    const available = containerClientH - offsetTop - paginationReserve
    const next = Math.max(minHeight, Math.round(available))
    if (next !== maxHeight.value) {
      maxHeight.value = next
    }
  }

  /**
   * 合并到下一帧执行，避免同一轮布局里重复测量（表格自身高度变化会再次触发观察器）。
   */
  function scheduleMeasure() {
    if (frame) return
    frame = requestAnimationFrame(() => {
      frame = 0
      measure()
    })
  }

  /** 挂载观察器：容器尺寸变化 + 表格尺寸变化 + 字号档位变化 */
  function setupObservers() {
    const tableEl = resolveTableEl()
    if (!tableEl) return

    const container = resolveContentContainer(tableEl)
    if (!container) return

    if (typeof ResizeObserver !== 'undefined') {
      // 容器变化：视口尺寸、侧边栏展开收起等
      if (observedContainer !== container) {
        containerResizeObserver?.disconnect()
        containerResizeObserver = new ResizeObserver(() => scheduleMeasure())
        containerResizeObserver.observe(container)
        observedContainer = container
      }
      // 表格变化：行数变化导致表体高度变化时重新计算偏移
      if (observedTable !== tableEl) {
        tableResizeObserver?.disconnect()
        tableResizeObserver = new ResizeObserver(() => scheduleMeasure())
        tableResizeObserver.observe(tableEl)
        observedTable = tableEl
      }
    }

    // 字号档位写在 <html data-font-size> 上，切换后行高变化需要重算
    if (typeof MutationObserver !== 'undefined' && !fontSizeObserver) {
      fontSizeObserver = new MutationObserver(() => scheduleMeasure())
      fontSizeObserver.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['data-font-size']
      })
    }
  }

  function onWindowResize() {
    scheduleMeasure()
  }

  onMounted(() => {
    // 表格可能尚未渲染完成，先测一轮，延后再测一轮
    measure()
    setupObservers()
    requestAnimationFrame(() => {
      setupObservers()
      measure()
    })
    setTimeout(() => {
      setupObservers()
      measure()
    }, 300)
    window.addEventListener('resize', onWindowResize, { passive: true })
  })

  onBeforeUnmount(() => {
    containerResizeObserver?.disconnect()
    containerResizeObserver = undefined
    tableResizeObserver?.disconnect()
    tableResizeObserver = undefined
    fontSizeObserver?.disconnect()
    fontSizeObserver = undefined
    observedContainer = null
    observedTable = null
    if (frame) {
      cancelAnimationFrame(frame)
      frame = 0
    }
    window.removeEventListener('resize', onWindowResize)
  })

  return reactive({
    /** 表格 max-height（px），绑定到 el-table 的 :max-height */
    maxHeight,
    /** 立即重新测量 */
    measure
  })
}
