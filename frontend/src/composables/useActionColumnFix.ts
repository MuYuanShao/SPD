/**
 * 操作列固定规则（统一列表页共用）
 * =============================================================================
 * 目标：所有基础数据管理列表页的「操作」列采用**同一套**固定策略。
 *
 * 规则：
 * ------------------------------------------------------------------
 * **表格出现横向滚动条时才固定操作列；没有滚动条时不固定。**
 *
 * 为什么这样更合理：
 * ------------------------------------------------------------------
 * 1. 没有横向滚动时，操作列本来就在视野里，固定它毫无意义 ——
 *    反而会让 Element Plus 额外渲染一份 .el-table__fixed-right 副本，
 *    视觉上像是凭空多了一列。
 * 2. 出现横向滚动时，用户往右看数据时操作按钮会滚出视野，
 *    此时固定右侧才能保证「随时能点操作」。
 * 3. 判定依据是**实际是否溢出**，而不是列数 —— 列宽会随拖拽、
 *    窗口大小、主题、字号档位变化，用列数判断容易与实际不符。
 *
 * 横向滚动容器是哪一层：
 * ------------------------------------------------------------------
 * Element Plus 2.x 的表格把横向滚动放在内部的 el-scrollbar 上：
 *   .el-table__body-wrapper > .el-scrollbar__wrap   （真正的滚动容器）
 * 注意 .el-table__body-wrapper 自己是 overflow-x: hidden，
 * scrollWidth 永远等于 clientWidth，**不能**用它判断是否溢出。
 * 老版本没有 el-scrollbar 时才回退到 .el-table__body-wrapper。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

export interface ActionColumnFixOptions {
  /**
   * 溢出的判定余量（px）。
   * 留 1px 避免亚像素误差导致反复切换。
   */
  tolerance?: number
}

/** 在表格根节点里定位真正的横向滚动容器 */
function resolveScrollContainer(root: HTMLElement): HTMLElement | null {
  const scrollbarWrap = root.querySelector(
    '.el-table__body-wrapper .el-scrollbar__wrap'
  ) as HTMLElement | null
  if (scrollbarWrap) return scrollbarWrap
  const body = root.querySelector('.el-table__body-wrapper') as HTMLElement | null
  return body
}

export function useActionColumnFix(options: ActionColumnFixOptions = {}) {
  const tolerance = options.tolerance ?? 1
  /** 表格是否出现横向滚动 */
  const hasHorizontalScroll = ref(false)
  /** 已挂载的观察器与监听 */
  let resizeObserver: ResizeObserver | null = null
  /** 观察内部表格主体宽度（列宽变化时滚动容器自身宽度可能不变） */
  let bodyResizeObserver: ResizeObserver | null = null
  let scrollElement: HTMLElement | null = null
  let mutationObserver: MutationObserver | null = null
  /** 表格根节点：用于在表格重绘后重新定位滚动容器 */
  let tableRoot: HTMLElement | null = null

  /**
   * 是否固定操作列。
   * 有横向滚动才固定。
   */
  const actionFixed = computed(() => hasHorizontalScroll.value)

  /**
   * 绑定到 el-table-column 的 fixed 属性。
   * 传 undefined 表示不固定（Element Plus 接受 undefined / false）。
   */
  const actionFixedBinding = computed(() => (actionFixed.value ? 'right' : undefined))

  /** 读取当前横向滚动容器的溢出状态 */
  function measure(el: HTMLElement | null | undefined) {
    if (!el) return
    hasHorizontalScroll.value = el.scrollWidth - el.clientWidth > tolerance
  }

  /**
   * 绑定滚动事件到新的容器（容器被替换时重新绑定）。
   *
   * 注意：即使容器没有变化，也必须重新测量一次。
   * 列宽拖拽 / 列显隐切换 / 窗口缩放时，滚动容器**自身**的宽度可能不变，
   * 变的是内部表格的宽度 —— 此时 ResizeObserver 不会触发，
   * 若这里提前 return，溢出状态就会停留在旧值。
   */
  function bindContainer(container: HTMLElement | null) {
    if (container !== scrollElement) {
      if (scrollElement) {
        scrollElement.removeEventListener('scroll', onScroll)
      }
      scrollElement = container
      if (container) {
        container.addEventListener('scroll', onScroll, { passive: true })
        if (typeof ResizeObserver !== 'undefined') {
          resizeObserver?.disconnect()
          resizeObserver = new ResizeObserver(() => measure(scrollElement))
          resizeObserver.observe(container)
        }
      }
    }
    measure(scrollElement)
  }

  /**
   * 从表格 DOM 找到横向滚动容器并开始观察。
   * @param tableEl el-table 的组件实例
   */
  function observe(tableEl: { $el?: HTMLElement } | null | undefined) {
    const root = tableEl?.$el
    if (!root) return
    tableRoot = root
    bindContainer(resolveScrollContainer(root))
    // 列设置里勾选/取消列、拖拽列宽都会改变布局，用 MutationObserver 兜底重新测量
    if (typeof MutationObserver !== 'undefined' && !mutationObserver) {
      mutationObserver = new MutationObserver(() => {
        if (!tableRoot) return
        // 表格重绘后滚动容器可能被替换，重新定位
        bindContainer(resolveScrollContainer(tableRoot))
        measure(scrollElement)
        // 表格主体节点也可能被替换，重新挂载宽度观察
        observeTableBodyWidth()
      })
      mutationObserver.observe(root, { childList: true, subtree: true, characterData: true })
    }
    // 内部表格宽度变化时，滚动容器自身的宽度可能不变（只变内容宽度），
    // 因此额外观察表格主体，宽度一变就重新判定是否溢出。
    observeTableBodyWidth()
  }

  /**
   * 观察内部表格主体的宽度变化。
   *
   * 拖拽列宽 / 列显隐切换后，横向溢出状态会改变，
   * 但滚动容器（.el-scrollbar__wrap）的 clientWidth 往往保持不变，
   * 只观察容器会漏掉这类变化 —— 所以要额外观察表格主体。
   */
  function observeTableBodyWidth() {
    if (!tableRoot || typeof ResizeObserver === 'undefined') return
    bodyResizeObserver?.disconnect()
    bodyResizeObserver = new ResizeObserver(() => {
      measure(scrollElement)
    })
    const body = tableRoot.querySelector(
      '.el-table__body-wrapper .el-scrollbar__wrap > .el-table'
    ) as HTMLElement | null
    if (body) bodyResizeObserver.observe(body)
  }

  function onScroll() {
    measure(scrollElement)
  }

  function detach() {
    resizeObserver?.disconnect()
    resizeObserver = null
    bodyResizeObserver?.disconnect()
    bodyResizeObserver = null
    mutationObserver?.disconnect()
    mutationObserver = null
    if (scrollElement) {
      scrollElement.removeEventListener('scroll', onScroll)
    }
    scrollElement = null
    tableRoot = null
  }

  /** 表格重排后重新测量（列宽拖拽、字号切换、窗口缩放） */
  function refresh() {
    if (tableRoot) {
      bindContainer(resolveScrollContainer(tableRoot))
      observeTableBodyWidth()
    }
    measure(scrollElement)
  }

  onMounted(() => {
    // 表格可能尚未渲染完成，延后再测一次
    requestAnimationFrame(refresh)
    setTimeout(refresh, 300)
  })

  onBeforeUnmount(() => {
    detach()
  })

  return {
    hasHorizontalScroll,
    actionFixed,
    actionFixedBinding,
    observe,
    refresh,
    measure
  }
}
