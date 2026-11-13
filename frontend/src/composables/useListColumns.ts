import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

export interface ListColumnConfig {
  /** 稳定的列标识，用于 localStorage 持久化与列设置 */
  key: string
  /** 表头展示文案 */
  label: string
  /** 列宽（px） */
  width: number
  /** 是否展示 */
  visible: boolean
  /** 固定列 */
  fixed?: 'left' | 'right' | false
  /**
   * 该列是否参与「自适应填充」。
   * 为 true 时，列宽 = 基准宽度 + 按剩余空间比例分配到的增量。
   */
  flexible?: boolean
  /** 弹性列的最小宽度 */
  minFlexibleWidth?: number
  /**
   * 弹性列的最大宽度。
   * 限制弹性列的膨胀上限，避免长文本列吃掉全部剩余空间，
   * 把短字段列（状态 / 来源 / 时间）挤压变形。
   */
  maxWidth?: number
}

export interface UseListColumnsOptions {
  /** localStorage key，用于持久化列宽与显隐 */
  storageKey: string
  /** 默认列定义 */
  defaults: ListColumnConfig[]
  /** 允许拖拽的最小列宽 */
  minWidth?: number
  /** 允许拖拽的最大列宽 */
  maxWidth?: number
  /**
   * 自适应模式：
   * - 'fixed'（默认）：列宽固定，用户拖拽后保持
   * - 'fit'：列宽随容器宽度自动伸缩，容器可用宽度按各列基准宽度比例分配
   */
  fitMode?: 'fixed' | 'fit' | 'hybrid'
  /** 自适应模式下，固定列（选择 / 操作）占用的宽度合计，用于计算可用宽度 */
  reservedWidth?: number
}

/**
 * 统一列表列配置（列宽拖拽 + 列设置 + localStorage 持久化）。
 * 供基础数据各列表页复用，保证列宽调节与列显隐交互一致。
 */
export function useListColumns(options: UseListColumnsOptions) {
  const minWidth = options.minWidth ?? 90
  const maxWidth = options.maxWidth ?? 560
  const fitMode = options.fitMode ?? 'fixed'

  function normalize(saved: Array<Partial<ListColumnConfig> & { key: string }>): ListColumnConfig[] {
    const map = new Map(saved.map((item) => [item.key, item]))
    return options.defaults.map((fallback) => {
      const previous = map.get(fallback.key)
      const width = Math.min(maxWidth, Math.max(minWidth, Number(previous?.width ?? fallback.width) || fallback.width))
      return {
        key: fallback.key,
        label: fallback.label,
        width,
        visible: previous?.visible !== false,
        fixed: fallback?.fixed ?? false,
        flexible: fallback.flexible ?? false,
        minFlexibleWidth: fallback.minFlexibleWidth ?? (fallback.flexible ? 160 : undefined),
        maxWidth: fallback.maxWidth
      }
    })
  }

  function load(): ListColumnConfig[] {
    try {
      const raw = localStorage.getItem(options.storageKey)
      if (!raw) return options.defaults.map((item) => ({ ...item }))
      return normalize(JSON.parse(raw) as Array<Partial<ListColumnConfig> & { key: string }>)
    } catch {
      return options.defaults.map((item) => ({ ...item }))
    }
  }

  const columns = ref<ListColumnConfig[]>(load())
  const settingsOpen = ref(false)
  const tableRef = ref<any>(null)
  /** 自适应模式下的容器可用宽度（px） */
  const containerWidth = ref(0)

  const visibleColumns = computed(() => columns.value.filter((column) => column.visible))
  const visibleColumnCount = computed(() => visibleColumns.value.length)

  /**
   * 计算最终生效的列宽。
   * ---------------------------------------------------------------------
   * 设计目标：**字少的列保持紧凑，字多的列充分展开**。
   *
   * 每列都声明两个边界：
   *   - maxWidth        ：该列可增长到的上限
   *                       字少的列（状态 / 维护来源 / 更新时间）上限贴近实际内容，
   *                       因此永远不会被富余空间撑成空旷的大块；
   *                       字多的列（商品名称 / 生产厂家）上限放宽，可充分展开。
   *   - minFlexibleWidth ：窄屏压缩时的下限
   *
   * 剩余空间分配：
   *   1) 弹性列（flexible）优先吸收，按「各自剩余上限」的比例分配，
   *      谁先到上限谁让出份额给另一个，因此长文本列自然占大头；
   *   2) 弹性列全部到上限后，富余才补给其余列，且同样受各自上限约束。
   *
   * 空间不足时：
   *   1) 先压缩弹性列（长文本换行即可，用户要求「文字太多要换行」）；
   *   2) 仍不足再按各自可压缩余量压缩其余列，避免短字段列被压扁。
   */
  const effectiveWidths = computed<Record<string, number>>(() => {
    const result: Record<string, number> = {}
    const cols = visibleColumns.value
    if (!cols.length) return result

    const reserved = options.reservedWidth ?? 0
    const available = containerWidth.value > 0 ? containerWidth.value - reserved : 0
    const base = cols.reduce((sum, column) => sum + column.width, 0)

    // 容器尚未测量出来时，先用基准宽度，避免首帧抖动
    if (available <= 0 || fitMode === 'fixed') {
      cols.forEach((column) => {
        result[column.key] = column.width
      })
      return result
    }

    const widths: Record<string, number> = {}
    cols.forEach((column) => {
      widths[column.key] = column.width
    })

    const capOf = (column: ListColumnConfig) => column.maxWidth ?? Infinity
    const floorOf = (column: ListColumnConfig) =>
      Math.min(column.width, column.minFlexibleWidth ?? 100)

    /**
     * 把 amount 分配给候选列，按「各自剩余上限」的比例分配。
     * 先到上限的列自动让出份额，因此长文本列能自然占到大头。
     */
    function grow(candidates: ListColumnConfig[], amount: number) {
      let rest = amount
      // 多轮收敛：一轮分完则结束，未分完（全部触顶）则不再继续
      for (let round = 0; round < 8 && rest > 0.5; round++) {
        const active = candidates.filter((column) => capOf(column) - widths[column.key] > 0.5)
        if (!active.length) break
        const totalRoom = active.reduce((sum, column) => sum + (capOf(column) - widths[column.key]), 0)
        if (totalRoom <= 0) break
        let used = 0
        for (const column of active) {
          const room = capOf(column) - widths[column.key]
          const give = Math.min(room, (rest * room) / totalRoom)
          widths[column.key] += give
          used += give
        }
        rest -= used
        if (used < 0.5) break
      }
      return amount - rest
    }

    /**
     * 从 candidates 压缩 amount，按「各自可压缩余量」的比例分配，
     * 谁先到下限谁让出份额，避免某一列被压扁。
     */
    function shrink(candidates: ListColumnConfig[], amount: number) {
      let rest = amount
      for (let round = 0; round < 8 && rest > 0.5; round++) {
        const active = candidates.filter((column) => widths[column.key] - floorOf(column) > 0.5)
        if (!active.length) break
        const totalRoom = active.reduce((sum, column) => sum + (widths[column.key] - floorOf(column)), 0)
        if (totalRoom <= 0) break
        let used = 0
        for (const column of active) {
          const room = widths[column.key] - floorOf(column)
          const take = Math.min(room, (rest * room) / totalRoom)
          widths[column.key] -= take
          used += take
        }
        rest -= used
        if (used < 0.5) break
      }
      return amount - rest
    }

    if (available >= base) {
      // —— 容器有余量：字多的列优先吸收 ——
      let surplus = available - base
      const flexibleCols = cols.filter((column) => column.flexible)
      if (fitMode === 'hybrid') {
        // 弹性列优先，按剩余上限比例分配
        surplus -= grow(flexibleCols, surplus)
      }
      if (surplus > 0.5) {
        // 弹性列到上限后，富余补给其余列（同样受各自上限约束）
        const others = cols.filter((column) => !column.flexible)
        grow(others, surplus)
      }
    } else {
      // —— 容器偏窄：先压弹性列（长文本换行），再压其余列 ——
      let deficit = base - available
      const flexibleCols = cols.filter((column) => column.flexible)
      deficit -= shrink(flexibleCols, deficit)
      if (deficit > 0.5) {
        shrink(
          cols.filter((column) => !column.flexible),
          deficit
        )
      }
    }

    Object.assign(result, widths)
    return result
  })

  /**
   * 列宽绑定。
   * ---------------------------------------------------------------------
   * 统一返回固定 width —— 剩余空间已在 effectiveWidths 中
   * 按「弹性列优先吸收 + 上限约束 + 其余按比例补给」分配完毕，
   * 再交给 Element Plus 原生 fit 会把空间重新吞回弹性列，
   * 导致短字段列再次被挤压，因此统一使用 width。
   */
  function columnBinding(key: string): { width?: number; minWidth?: number } {
    return { width: columnWidth(key) }
  }
  function persist() {
    try {
      localStorage.setItem(options.storageKey, JSON.stringify(columns.value))
    } catch {
      /* 忽略持久化失败 */
    }
  }

  function columnWidth(key: string) {
    return effectiveWidths.value[key] ?? 150
  }

  function isColumnVisible(key: string) {
    return columns.value.some((column) => column.key === key && column.visible)
  }

  function setColumnVisible(key: string, visible: boolean) {
    const column = columns.value.find((item) => item.key === key)
    if (!column) return
    column.visible = visible
    persist()
    syncLayout()
  }

  function resetColumns() {
    columns.value = options.defaults.map((item) => ({ ...item }))
    persist()
    syncLayout()
  }

  function syncLayout() {
    try {
      tableRef.value?.doLayout?.()
    } catch {
      /* 忽略 */
    }
  }

  /** 读取表格容器宽度，供自适应列宽计算使用 */
  function resolveContainerEl(): HTMLElement | null {
    try {
      const root = tableRef.value?.$el
      if (root) {
        const wrap = root.closest?.('.ulc-table-wrap')
        if (wrap) return wrap as HTMLElement
        return root as HTMLElement
      }
    } catch {
      /* 忽略 */
    }
    // 兜底：表格尚未挂载时，先用文档中的容器
    try {
      const wrap = document.querySelector('.ulc-table-wrap') as HTMLElement | null
      if (wrap) return wrap
      const table = document.querySelector('.el-table') as HTMLElement | null
      return table
    } catch {
      return null
    }
  }

  function measureContainer() {
    if (fitMode === 'fixed') return
    const el = resolveContainerEl()
    if (el && el.clientWidth > 0) {
      containerWidth.value = el.clientWidth
    }
  }

  let resizeObserver: ResizeObserver | null = null
  let mutationObserver: MutationObserver | null = null
  let dragState: { key: string; startX: number; startWidth: number } | null = null

  /**
   * 列宽拖拽（供原生 HTML 表格使用）。
   * ---------------------------------------------------------------------
   * 拖拽热区放在列的标准右边框上（透明元素覆盖在边框位置），
   * 拖拽时只改变列宽，不新增任何可见线条，
   * 因此表头分割线始终与下方各行的列分隔线完全对齐。
   */
  function startColumnDrag(key: string, event: MouseEvent) {
    const column = columns.value.find((item) => item.key === key)
    if (!column) return
    event.preventDefault()
    event.stopPropagation()
    dragState = { key, startX: event.clientX, startWidth: column.width }
    document.body.style.userSelect = 'none'
    document.body.style.cursor = 'col-resize'
    const onMove = (moveEvent: MouseEvent) => {
      if (!dragState) return
      const delta = moveEvent.clientX - dragState.startX
      column.width = Math.min(maxWidth, Math.max(minWidth, dragState.startWidth + delta))
      persist()
      syncLayout()
    }
    const onUp = () => {
      dragState = null
      document.body.style.userSelect = ''
      document.body.style.cursor = ''
      persist()
      syncLayout()
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }

  /**
   * 表头列宽拖拽结束回调（Element Plus 原生 resizable 机制）。
   * ---------------------------------------------------------------------
   * 使用 Element Plus 原生的表头边框拖拽，而不是自定义可见手柄：
   * 原生拖拽的热区就在列的标准右边框上，拖拽时显示的竖线与表格
   * 各行之间的列分隔线完全重合，不会出现错位的「特别分割线」。
   *
   * @param newWidth 拖拽后的新列宽（px）
   * @param _oldWidth 拖拽前的列宽（px）
   * @param column   被拖拽的列，通过 columnKey 映射回本地列配置
   */
  function handleHeaderDragEnd(
    newWidth: number,
    _oldWidth: number,
    column: { columnKey?: string } | null
  ) {
    const key = column?.columnKey
    if (!key) return
    const target = columns.value.find((item) => item.key === key)
    if (!target) return
    const width = Math.round(Number(newWidth))
    if (!Number.isFinite(width) || width <= 0) return
    target.width = Math.min(maxWidth, Math.max(minWidth, width))
    persist()
    syncLayout()
  }

  function handleWindowResize() {
    measureContainer()
    syncLayout()
  }

  onMounted(() => {
    // 表格可能尚未完成渲染，延后再测量一次，确保拿到真实容器宽度
    measureContainer()
    nextTick(() => {
      measureContainer()
      syncLayout()
    })
    if (fitMode !== 'fixed' && typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => {
        measureContainer()
        syncLayout()
      })
      // 表格常在数据加载后才渲染，onMounted 时可能还没有容器元素，
      // 因此这里除了观察当前元素，还要在下一轮 tick 再观察一次。
      const observe = () => {
        const el = resolveContainerEl()
        if (el) resizeObserver?.observe(el)
      }
      observe()
      nextTick(() => {
        observe()
        measureContainer()
        syncLayout()
      })
      // 数据异步到达后表格才出现，用 MutationObserver 兜底重新挂载观察
      if (typeof MutationObserver !== 'undefined') {
        mutationObserver = new MutationObserver(() => {
          observe()
          measureContainer()
          syncLayout()
        })
        mutationObserver.observe(document.body, { childList: true, subtree: true })
      }
    }
    window.addEventListener('resize', handleWindowResize)
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
    mutationObserver?.disconnect()
    mutationObserver = null
    window.removeEventListener('resize', handleWindowResize)
  })

  return {
    columns,
    settingsOpen,
    tableRef,
    visibleColumns,
    visibleColumnCount,
    columnWidth,
    columnBinding,
    isColumnVisible,
    setColumnVisible,
    resetColumns,
    persist,
    syncLayout,
    measureContainer,
    startColumnDrag,
    handleHeaderDragEnd
  }
}
