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
  /**
   * 该列「内容单行放下」的理想宽度（px）。
   *
   * 由实测内容自然宽度得到（canvas 按真实字体量得），
   * 代表该列不需要换行就能完整显示的宽度。
   *
   * 分配剩余空间时，列会**先涨到 idealWidth**，再继续按比例分配；
   * 这样「规格型号」这类理想宽度不大但内容确实很长的列，
   * 不会被上限更大的列（商品名称）一直挤压而始终放不下内容。
   */
  idealWidth?: number
  /**
   * 该列的**宽度权重**（用于按比例铺满容器）。
   *
   * 权重由实测内容自然宽度排名得出（字越多权重越大），
   * 分配可用宽度时按权重比例分配，保证表格始终铺满内容区，
   * 且字多的列自然获得更大的份额。
   *
   * 权重示例（科室库房目录）：
   *   商品名称 9 > 生产厂家 7 > 规格型号 5 > 科室/库房 3
   *   > 更新时间 3 > 商品编码 2 > 维护来源 2 > 状态 2
   */
  weight?: number
  /**
   * 该列的**保底宽度**（px）。
   *
   * 按比例分配前先给每列发到保底宽度，保证短列也不会被压到
   * 连表头都放不下的程度；剩余空间再按 weight 比例分配。
   */
  minWidth?: number
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
   * - 'fit'：列宽随容器宽度自动伸缩，所有列按权重比例同步增长
   * - 'hybrid'：在 fit 基础上，长文本列额外享有「理想宽度优先」调剂
   */
  fitMode?: 'fixed' | 'fit' | 'hybrid'
  /** 自适应模式下，固定列（选择 / 操作）占用的宽度合计，用于计算可用宽度 */
  reservedWidth?: number
  /**
   * 参与比例分配、但**不进入列设置面板**的固定列（如「操作」列）。
   *
   * 为什么要纳入分配：
   *   若把操作列排除在分配之外，它的宽度会被硬扣掉，
   *   剩余宽度再按比例分给数据列 —— 这会让「商品名称」这类
   *   字最多的列拿到的比例被无谓压低。
   *   把它纳入同一个权重池，表格才能真正做到
   *   **铺满内容区 + 剩余宽度按比例分配给所有列**。
   *
   * 这些列不提供显隐开关与拖拽手柄（保持 fixed 定位与操作区稳定）。
   */
  fixedColumns?: Array<{
    key: string
    label: string
    width: number
    /** 权重，参与比例分配 */
    weight?: number
    /** 比例分配时的保底宽度 */
    minWidth?: number
    /** 比例分配时的上限 */
    maxWidth?: number
  }>
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
        maxWidth: fallback.maxWidth,
        idealWidth: fallback.idealWidth,
        weight: fallback.weight,
        minWidth: fallback.minWidth
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
   * 计算最终生效的列宽 —— **按权重比例铺满内容区**。
   * ---------------------------------------------------------------------
   * 设计目标：表格默认恰好铺满内容区，不留右侧空白，也不出现横向滚动。
   *
   * 三个宽度来源，优先级从高到低：
   *
   * 1) **用户设定值 width** —— 用户拖拽表头调整后的宽度，最高优先级。
   *    拖拽结果会被持久化，且不会被按比例分配覆盖。
   *
   * 2) **保底宽度 minWidth** —— 该列的可压缩下限，
   *    保证「状态」这类短列不会被压到连内容都放不下。
   *
   * 3) **权重 weight** —— 分配富余空间的比例依据。
   *    由实测内容自然宽度排名得出，字多的列权重高、自然份额大。
   *    例如科室库房目录：
   *      商品名称 9 > 生产厂家 7 > 规格型号 5 > 科室/库房 3
   *      > 更新时间 3 > 商品编码 2 > 维护来源 2 > 状态 2
   *
   * 分配流程：
   *   - 基准和 ≤ 可用宽度：富余按权重比例分给各列（受 maxWidth 上限约束）；
   *   - 基准和 > 可用宽度：按权重比例压缩各列（受 minWidth 下限约束）；
   *   - hybrid 模式下额外为未达「理想宽度」的长文本列做调剂，
   *     从有余量的列按比例借，借多少还多少，总量始终守恒。
   *
   * fitMode 说明：
   * - 'fixed'  ：列宽恒等于用户设定值，完全不随视口变化。
   * - 'fit'    ：按上述权重比例铺满。
   * - 'hybrid' ：同样铺满，但长文本列额外享有「理想宽度优先」。
   */
  const effectiveWidths = computed<Record<string, number>>(() => {
    const result: Record<string, number> = {}
    const cols = visibleColumns.value
    if (!cols.length) return result

    const reserved = options.reservedWidth ?? 0
    const available = containerWidth.value > 0 ? containerWidth.value - reserved : 0

    // 容器尚未测量出来时，先用基准宽度，避免首帧抖动
    if (available <= 0 || fitMode === 'fixed') {
      cols.forEach((column) => {
        result[column.key] = column.width
      })
      ;(options.fixedColumns ?? []).forEach((column) => {
        result[column.key] = column.width
      })
      return result
    }

    const capOf = (column: ListColumnConfig) => column.maxWidth ?? Infinity
    const floorOf = (column: ListColumnConfig) =>
      Math.max(0, Math.min(column.minWidth ?? 56, capOf(column)))
    const weightOf = (column: ListColumnConfig) => Math.max(0.01, column.weight ?? 1)

    // —— 参与比例分配的固定列（如「操作」列）——
    //    它们与数据列在同一个权重池里竞争剩余宽度，
    //    这样表格才能真正做到「铺满 + 按比例分配」。
    const fixedCols = options.fixedColumns ?? []
    const fixedCap = (c: (typeof fixedCols)[number]) => c.maxWidth ?? Infinity
    const fixedFloor = (c: (typeof fixedCols)[number]) =>
      Math.max(0, Math.min(c.minWidth ?? c.width, fixedCap(c)))
    const fixedWeight = (c: (typeof fixedCols)[number]) => Math.max(0.01, c.weight ?? 1)

    // —— 基准：用户设定值（拖拽结果优先）——
    const widths: Record<string, number> = {}
    cols.forEach((column) => {
      widths[column.key] = column.width
    })
    fixedCols.forEach((column) => {
      widths[column.key] = column.width
    })
    const baseTotal =
      cols.reduce((sum, column) => sum + column.width, 0) +
      fixedCols.reduce((sum, column) => sum + column.width, 0)

    if (baseTotal < available) {
      // —— 富余：按权重比例分配（受 maxWidth 上限约束）——
      let pending = available - baseTotal
      for (let round = 0; round < 8 && pending > 0.5; round++) {
        const active = cols.filter((column) => capOf(column) - widths[column.key] > 0.5)
        const activeFixed = fixedCols.filter(
          (column) => fixedCap(column) - widths[column.key] > 0.5
        )
        if (!active.length && !activeFixed.length) break
        const wSum =
          active.reduce((sum, column) => sum + weightOf(column), 0) +
          activeFixed.reduce((sum, column) => sum + fixedWeight(column), 0)
        if (wSum <= 0) break
        let used = 0
        for (const column of active) {
          const room = capOf(column) - widths[column.key]
          const give = Math.min(room, (pending * weightOf(column)) / wSum)
          widths[column.key] += give
          used += give
        }
        for (const column of activeFixed) {
          const room = fixedCap(column) - widths[column.key]
          const give = Math.min(room, (pending * fixedWeight(column)) / wSum)
          widths[column.key] += give
          used += give
        }
        pending -= used
        if (used < 0.5) break
      }
    } else if (baseTotal > available) {
      // —— 不足：按权重比例压缩（受 minWidth 下限约束）——
      let need = baseTotal - available
      for (let round = 0; round < 8 && need > 0.5; round++) {
        const active = cols.filter((column) => widths[column.key] - floorOf(column) > 0.5)
        const activeFixed = fixedCols.filter(
          (column) => widths[column.key] - fixedFloor(column) > 0.5
        )
        if (!active.length && !activeFixed.length) break
        const wSum =
          active.reduce((sum, column) => sum + weightOf(column), 0) +
          activeFixed.reduce((sum, column) => sum + fixedWeight(column), 0)
        if (wSum <= 0) break
        let used = 0
        for (const column of active) {
          const room = widths[column.key] - floorOf(column)
          const take = Math.min(room, (need * weightOf(column)) / wSum)
          widths[column.key] -= take
          used += take
        }
        for (const column of activeFixed) {
          const room = widths[column.key] - fixedFloor(column)
          const take = Math.min(room, (need * fixedWeight(column)) / wSum)
          widths[column.key] -= take
          used += take
        }
        need -= used
        if (used < 0.5) break
      }
    }

    // —— hybrid：为未达「理想宽度」的长文本列做调剂 ——
    //    场景：规格型号内容确实很长（P90 188px），但权重排名不是最高，
    //    按比例分到的宽度可能仍放不下内容。
    //    从「还有余量」的列按权重比例借给它，借多少还多少，总量守恒。
    if (fitMode === 'hybrid') {
      for (const column of cols) {
        if (!column.flexible || !column.idealWidth) continue
        const target = Math.min(column.idealWidth, capOf(column))
        let need = target - widths[column.key]
        if (need <= 0.5) continue

        // 出借方：还有余量（超过自己的理想宽度或保底）且未到上限
        const lenders = cols.filter((c) => {
          if (c.key === column.key) return false
          const lenderIdeal =
            c.flexible && c.idealWidth ? Math.min(c.idealWidth, capOf(c)) : floorOf(c)
          const spare = widths[c.key] - lenderIdeal
          return spare > 0.5 && capOf(c) - widths[c.key] > 0.5
        })
        if (!lenders.length) continue

        const lenderWeight = lenders.reduce((sum, c) => sum + weightOf(c), 0)
        if (lenderWeight <= 0) continue

        for (const lender of lenders) {
          const lenderIdeal =
            lender.flexible && lender.idealWidth
              ? Math.min(lender.idealWidth, capOf(lender))
              : floorOf(lender)
          const spare = widths[lender.key] - lenderIdeal
          if (spare <= 0.5) continue
          const give = Math.min(need, spare, (need * weightOf(lender)) / lenderWeight)
          widths[lender.key] -= give
          widths[column.key] += give
          need -= give
        }
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
    const computed = effectiveWidths.value[key]
    if (typeof computed === 'number') return computed
    // 固定列（如「操作」列）不在 columns 中，回退到其自身基准宽度
    const fixed = (options.fixedColumns ?? []).find((column) => column.key === key)
    if (fixed) return fixed.width
    return 150
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
