import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

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
  fitMode?: 'fixed' | 'fit'
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
        flexible: fallback.flexible ?? false
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
   * - fixed 模式：直接返回用户设置的宽度
   * - fit 模式：把容器可用宽度按各列的基准宽度比例分配下去，
   *   使表格始终恰好铺满容器（不出现横向滚动），
   *   且基准更宽的长文本列会自然获得更多空间
   */
  const effectiveWidths = computed<Record<string, number>>(() => {
    const result: Record<string, number> = {}
    const visible = visibleColumns.value
    if (fitMode !== 'fit') {
      visible.forEach((column) => {
        result[column.key] = column.width
      })
      return result
    }

    const total = containerWidth.value || 0
    if (!total || !visible.length) {
      visible.forEach((column) => {
        result[column.key] = column.width
      })
      return result
    }

    // 固定列（选择列 / 操作列）占用宽度，不参与分配
    const reserved = options.reservedWidth ?? 0
    const available = Math.max(0, total - reserved)
    const baseSum = visible.reduce((sum, column) => sum + column.width, 0)
    if (baseSum <= 0) {
      visible.forEach((column) => {
        result[column.key] = column.width
      })
      return result
    }

    // 按基准宽度比例分配可用宽度
    visible.forEach((column) => {
      const share = (column.width / baseSum) * available
      result[column.key] = Math.round(Math.max(minWidth, Math.min(maxWidth, share)))
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
  function measureContainer() {
    if (fitMode !== 'fit') return
    try {
      const el = tableRef.value?.$el?.parentElement ?? tableRef.value?.$el
      if (el) {
        containerWidth.value = el.clientWidth
      }
    } catch {
      /* 忽略 */
    }
  }

  let resizeObserver: ResizeObserver | null = null
  let dragState: { key: string; startX: number; startWidth: number } | null = null

  /** 表头列分隔线拖拽（仅表头可见拖拽手柄） */
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

  onMounted(() => {
    measureContainer()
    if (fitMode === 'fit' && typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => {
        measureContainer()
        syncLayout()
      })
      try {
        const el = tableRef.value?.$el?.parentElement ?? tableRef.value?.$el
        if (el) resizeObserver.observe(el)
      } catch {
        /* 忽略 */
      }
    }
    window.addEventListener('resize', () => {
      measureContainer()
      syncLayout()
    })
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
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
    startColumnDrag
  }
}
