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
        minFlexibleWidth: fallback.minFlexibleWidth ?? (fallback.flexible ? 160 : undefined)
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
  /**
   * 计算最终生效的列宽（作为 el-table-column 的 min-width 传入）。
   * fit 模式：直接返回基准宽度，由 Element Plus 原生 fit 机制
   * 把容器剩余空间按各列 min-width 比例分配回去，
   * 避免 JS 测量容器宽度的时序问题，同时保证表格恰好铺满容器。
   */
  const effectiveWidths = computed<Record<string, number>>(() => {
    const result: Record<string, number> = {}
    visibleColumns.value.forEach((column) => {
      result[column.key] = column.width
    })
    return result
  })
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
      return document.querySelector('.ulc-table-wrap') as HTMLElement | null
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
      const el = resolveContainerEl()
      if (el) resizeObserver.observe(el)
    }
    window.addEventListener('resize', handleWindowResize)
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
    window.removeEventListener('resize', handleWindowResize)
  })

  return {
    columns,
    settingsOpen,
    tableRef,
    visibleColumns,
    visibleColumnCount,
    columnWidth,
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
