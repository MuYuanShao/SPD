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
  /**
   * 该列是否**填充表格剩余宽度**（占满 100%）。
   *
   * 典型场景：操作列是最后一列，内容较短（编辑 / 停用），
   * 但业务希望它把表格右侧的空白吃掉，避免整张表右侧出现一大片空白。
   *
   * 实现方式：该列不设固定 width，只设 min-width，
   * 由 Element Plus 原生 fit 布局把剩余空间全部分配给它。
   */
  fill?: boolean
  /**
   * 该列的宽度是否由用户**手动拖拽**确定。
   *
   * 为什么需要这个标记：
   * 自适应模式（fit / hybrid）下，列宽是「按权重比例铺满内容区」的，
   * 而拖拽结果只能作为**基准宽度**参与分配 —— 分配完又被改写，
   * 于是「拖到 420px、刷新后变成 263px」。
   *
   * 标记为手动后：
   * - 该列宽度**精确等于**用户设定值，不参与比例分配（既不占别人的空间，
   *   也不被别人挤压）；
   * - 该列不再参与 hybrid 的「理想宽度调剂」，避免被临时借走宽度；
   * - 其余未拖拽的列继续共享剩余空间，表格依旧铺满内容区。
   *
   * 该标记随列宽一起持久化到 localStorage，刷新后依然生效。
   * 旧数据没有这个字段时按「未手动」处理，保证平滑升级。
   */
  manual?: boolean
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
   * - 'fixed'（**默认**）：列宽恒等于用户设定值，各列**互相独立**。
   *   拖拽某一列只改变该列自身的宽度，不会挤占或改写其它列，
   *   也不会因为窗口宽度变化而重新分配 —— 因此拖拽结果始终稳定。
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
  /**
   * 可拖拽调宽的「操作」列。
   * ---------------------------------------------------------------------
   * 与 fixedColumns 的区别：
   * - fixedColumns 只参与宽度分配，**不提供拖拽手柄**（Element Plus
   *   的 fixed="right" 列由框架托管，表头与单元格天然对齐）。
   * - actionColumn 用于**原生 <table>** 场景：操作列不再用
   *   `position: sticky; right: 0` 固定在右侧，而是与其它数据列一样
   *   随表格横向滚动，并提供与数据列完全一致的拖拽热区。
   *
   * 背景：原生表格里用 sticky 固定右端时，表头单元格会停在原位，
   * 而单元格被吸到视口右缘，两者错位 —— 表头与按钮对不上列。
   * 让操作列回归普通列，是唯一能保证「表头与内容永远同列」的做法。
   */
  actionColumn?: {
    key: string
    label: string
    width: number
    /** 拖拽下限 */
    minWidth?: number
    /** 拖拽上限 */
    maxWidth?: number
  }
}

/**
 * 统一列表列配置（列宽拖拽 + 列设置 + localStorage 持久化）。
 * 供基础数据各列表页复用，保证列宽调节与列显隐交互一致。
 */
export function useListColumns(options: UseListColumnsOptions) {
  const minWidth = options.minWidth ?? 90
  const maxWidth = options.maxWidth ?? 560
  const fitMode = options.fitMode ?? 'fixed'

  /**
   * 把持久化数据规整成完整的列配置数组。
   * ---------------------------------------------------------------------
   * 【列顺序 = 数组顺序】
   *   持久化数据本身就是一个 JSON 数组，因此**数组顺序即列顺序**，
   *   无需新增字段即可向后兼容（旧数据仍是数组，直接按其顺序解读）。
   *
   * 【normalize 的顺序规则（关键）】
   *   1) 先按**已保存的顺序**输出保存过的列（用户手动调过的顺序必须保留）；
   *   2) 再把**代码里新增、保存之后才出现的列**追加到末尾
   *      （用户上次保存之后产品新增的列，不能挤掉已有列的顺序）；
   *   3) 已经没有的列（代码里删掉的）直接丢弃。
   *
   * 【向后兼容要点】
   *   旧版本 normalize() 用 options.defaults.map(...) 重建数组，
   *   会把顺序强制拉回声明顺序并丢弃用户保存的顺序。
   *   这里改为「以保存数据的顺序为准」，因此升级后用户已调过的顺序不丢失；
   *   而宽度 / 显隐 / manual 标记依旧按 key 精确应用到对应的列上。
   */
  function normalize(saved: Array<Partial<ListColumnConfig> & { key: string }>): ListColumnConfig[] {
    const savedByKey = new Map(saved.map((item) => [item.key, item]))
    const defaultsByKey = new Map(options.defaults.map((item) => [item.key, item]))

    /** 按 defaults 的元数据补全某一列，saved 提供用户数据（宽度 / 显隐 / manual） */
    const build = (
      fallback: ListColumnConfig,
      previous?: Partial<ListColumnConfig> & { key: string }
    ): ListColumnConfig => {
      const width = Math.min(
        maxWidth,
        Math.max(minWidth, Number(previous?.width ?? fallback.width) || fallback.width)
      )
      return {
        key: fallback.key,
        label: fallback.label,
        width,
        visible: previous?.visible !== false,
        fixed: fallback?.fixed ?? false,
        flexible: fallback?.flexible ?? false,
        minFlexibleWidth: fallback?.minFlexibleWidth ?? (fallback?.flexible ? 160 : undefined),
        maxWidth: fallback?.maxWidth,
        idealWidth: fallback?.idealWidth,
        weight: fallback?.weight,
        minWidth: fallback?.minWidth,
        // fill 来自代码里的 defaults（不是用户可改的数据），
        // 因此始终以 defaults 为准，不从持久化数据读取。
        fill: fallback?.fill === true,
        // 旧版本持久化的数据没有 manual 字段，一律视为「未手动拖拽」，
        // 这样老用户升级后行为不变，直到他自己拖一次列宽。
        manual: previous?.manual === true
      }
    }

    const result: ListColumnConfig[] = []
    const used = new Set<string>()

    // 1) 已保存的列：保持保存时的顺序
    for (const item of saved) {
      if (!item || typeof item.key !== 'string') continue
      const fallback = defaultsByKey.get(item.key)
      // 代码里已删除的列直接丢弃
      if (!fallback) continue
      if (used.has(item.key)) continue
      used.add(item.key)
      result.push(build(fallback, item))
    }

    // 2) 代码里新增、保存之后才出现的列：追加到末尾
    for (const fallback of options.defaults) {
      if (used.has(fallback.key)) continue
      used.add(fallback.key)
      result.push(build(fallback))
    }

    return result
  }

  function load(): ListColumnConfig[] {
    try {
      const raw = localStorage.getItem(options.storageKey)
      if (!raw) return options.defaults.map((item) => ({ ...item }))
      const parsed = JSON.parse(raw)
      // 兼容历史格式：早期版本可能存成 { key: {...} } 的对象
      if (Array.isArray(parsed)) {
        return normalize(parsed as Array<Partial<ListColumnConfig> & { key: string }>)
      }
      if (parsed && typeof parsed === 'object') {
        return normalize(
          Object.entries(
            parsed as Record<string, Partial<ListColumnConfig> & { key: string }>
          ).map(([key, value]) => ({ ...value, key }))
        )
      }
      return options.defaults.map((item) => ({ ...item }))
    } catch {
      return options.defaults.map((item) => ({ ...item }))
    }
  }

  const columns = ref<ListColumnConfig[]>(load())
  const settingsOpen = ref(false)
  const tableRef = ref<any>(null)
  /** 自适应模式下的容器可用宽度（px） */
  const containerWidth = ref(0)

  /* ===== 可拖拽的「操作」列 =====
     与普通数据列一样支持拖拽调宽并持久化，
     但不进入「列设置」面板（不提供显隐开关）。 */
  const actionColumnMin = options.actionColumn?.minWidth ?? 120
  const actionColumnMax = options.actionColumn?.maxWidth ?? 480
  const actionColumnWidth = ref(readActionColumnWidth())

  function readActionColumnWidth(): number {
    const fallback = options.actionColumn?.width ?? 200
    if (!options.actionColumn) return fallback
    try {
      const raw = localStorage.getItem(`${options.storageKey}::action`)
      const parsed = raw ? Number(JSON.parse(raw)) : NaN
      if (Number.isFinite(parsed)) {
        return Math.min(actionColumnMax, Math.max(actionColumnMin, parsed))
      }
    } catch {
      /* 忽略读取失败 */
    }
    return fallback
  }

  function persistActionColumnWidth() {
    if (!options.actionColumn) return
    try {
      localStorage.setItem(`${options.storageKey}::action`, JSON.stringify(actionColumnWidth.value))
    } catch {
      /* 忽略持久化失败 */
    }
  }

  const visibleColumns = computed(() => columns.value.filter((column) => column.visible))
  const visibleColumnCount = computed(() => visibleColumns.value.length)

  /**
   * 按存储顺序排列的列 key（仅可见列）。
   * ---------------------------------------------------------------------
   * 表格的**渲染顺序**就是这个序列：
   * Element Plus 的 el-table 把列顺序定义为「隐藏的 <th> 元素在 DOM 中的位置」，
   * 因此只要按本序列渲染 <el-table-column>，表头的实际顺序就会跟着变。
   */
  const orderedColumnKeys = computed(() => visibleColumns.value.map((column) => column.key))

  /** 按存储顺序排列的列 key（含隐藏列，用于列设置面板展示） */
  const orderedAllColumnKeys = computed(() => columns.value.map((column) => column.key))

  /**
   * 计算最终生效的列宽 —— **按权重比例铺满内容区**。
   * ---------------------------------------------------------------------
   * 设计目标：表格默认恰好铺满内容区，不留右侧空白，也不出现横向滚动。
   *
   * 三个宽度来源，优先级从高到低：
   *
   * 1) **用户手动拖拽过的宽度（manual 列）** —— 最高优先级，**原样生效**。
   *    拖拽结果会被持久化，且不会被按比例分配覆盖：
   *    手动列既不参与分配、也不被压缩，宽度完全由用户掌控。
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
   * 分配流程（只在**未拖拽**的列之间进行）：
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
    const rawAvailable = containerWidth.value > 0 ? containerWidth.value - reserved : 0

    // 「填充」列（fill: true，通常是最后一列「操作」）的宽度
    // = 容器宽度 − 其它列宽度之和，即它吃掉剩余空白，表格 100% 铺满。
    //
    // 分配数据列时，先把填充列的 minWidth 预扣出来：
    // 数据列最多只能用「容器 − 填充列下限」，剩下的全部归填充列。
    // 若不预扣，数据列会把容器占满，填充列的 minWidth 顶出去，
    // 表现为表格横向溢出（表头比容器宽）。
    const fillCols = cols.filter((column) => column.fill === true)
    const fillMinTotal = fillCols.reduce(
      (sum, column) => sum + (column.minWidth ?? column.width),
      0
    )
    const available = Math.max(0, rawAvailable - fillMinTotal)

    // 容器尚未测量出来时，先用基准宽度，避免首帧抖动
    if (available <= 0 || fitMode === 'fixed') {
      cols.forEach((column) => {
        result[column.key] = column.width
      })
      ;(options.fixedColumns ?? []).forEach((column) => {
        result[column.key] = column.width
      })

      // 「填充」列（通常是最后一列「操作」）：
      //   用户要求「操作列占满 100%」—— 即整张表铺满内容区，
      //   表格右侧的剩余空白全部由填充列吃掉。
      //
      //   计算步骤：
      //   1) 其余列按各自基准宽度累加，得到 others；
      //   2) 若 others ≤ 可用宽度：填充列吃掉差额，表格正好 100%；
      //   3) 若 others > 可用宽度：其它列已把容器撑满，
      //      填充列保留自身保底宽度，表格出现横向滚动条（可接受）。
      const fillCols = cols.filter((column) => column.fill === true)
      if (fillCols.length && rawAvailable > 0) {
        let others = cols
          .filter((column) => column.fill !== true)
          .reduce((sum, column) => sum + column.width, 0)
        ;(options.fixedColumns ?? []).forEach((column) => {
          others += column.width
        })
        // 这里用 rawAvailable（未预扣填充列的容器宽度），
        // 因为本分支自己就算出了填充列应占的宽度，不能再扣一次。
        const remainder = rawAvailable - others
        fillCols.forEach((column) => {
          const floor = column.minWidth ?? column.width
          const share =
            fillCols.length === 1 ? remainder : remainder / fillCols.length
          result[column.key] = Math.max(floor, Math.round(share))
        })
      }
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

    // —— 基准：用户设定值 ——
    //    manual 列（用户手动拖拽过）按用户值**钉死**，
    //    后续既不参与分配、也不被压缩。
    const widths: Record<string, number> = {}
    cols.forEach((column) => {
      widths[column.key] = column.width
    })
    fixedCols.forEach((column) => {
      widths[column.key] = column.width
    })

    // —— 参与比例分配的列集合：仅「未拖拽」的数据列 + 全部固定列 ——
    //    手动列被排除在外，因此不会被按比例放大或缩小；
    //    剩余空间由这些列按权重共享，表格依旧铺满内容区。
    //
    //    「填充」列（fill: true，通常是最后一列「操作」）**不参与分配**：
    //    它在 columnBinding() 里只给 minWidth、不给固定 width，
    //    由 Element Plus 原生 fit 把剩余空间全给它。
    //    若把它算进权重池，它会抢走数据列的宽度，
    //    同时又因为没有固定 width 而拿不到那份宽度 —— 宽度被"凭空吞掉"，
    //    表现为表格右侧留白。
    const autoCols = cols.filter((column) => column.manual !== true && column.fill !== true)

    // 固定列若与数据列同 key（如「操作」列同时出现在 defaults 和 fixedColumns），
    // 基准宽度只能算一次，否则 baseTotal 被重复累加，
    // 分配算法的"可用余量"算少，表格铺不满。
    const fixedColsOnly = fixedCols.filter(
      (column) => !cols.some((c) => c.key === column.key)
    )

    // 「填充」列至少要占住 minWidth，这部分已在 available 里预扣，
    // 这里不再重复计算，避免两处口径不一致。
    //
    // 手动列（用户拖拽过的列）不参与按权重分配，但它们的实际宽度
    // **必须计入基准**，否则：
    //   用户把某列拖宽 → 该列被排除出分配池 → baseTotal 变小
    //   → pending 变大 → 其它列被"顺带"拉宽
    // 这就违反了「拖动一列不影响其它列」的要求。
    // 把手动列的实际宽度加回 baseTotal，pending 就不会因拖拽而虚增，
    // 其它列宽度保持不变（仅在确实还有富余/不足时才微调）。
    const manualCols = cols.filter((column) => column.manual === true && column.fill !== true)

    const baseTotal =
      autoCols.reduce((sum, column) => sum + column.width, 0) +
      manualCols.reduce((sum, column) => sum + column.width, 0) +
      fixedColsOnly.reduce((sum, column) => sum + column.width, 0)

    if (baseTotal < available) {
      // 富余（available - baseTotal）：由填充列吸收，或按权重分配
      let pending = available - baseTotal
      // 「用户拖拽过某列」时：**不再重新分配其它列的宽度**。
      // 需求原文：「拖动列改变大小时不要影响其他列」。
      // 若此处仍按权重把富余分给所有列，被拖拽的列变宽/变窄时，
      // 其它列会跟着一起变，用户的调整就"被系统改回去了"。
      //
      // 正确做法：把富余全部留给「填充」列（通常是最后的「操作」列），
      // 由它吃掉剩余空白 —— 表格依旧 100% 铺满，而其它列纹丝不动。
      // 若没有填充列，则富余无处可去，表格右侧会出现一小段空白，
      // 这正是「拖拽不影响其它列」的必然代价（可接受的取舍）。
      const hasManual = manualCols.length > 0
      if (hasManual) {
        // 冻结：所有非拖拽列保持基准宽度，富余留给填充列
        // （填充列在 columnBinding 里只给 minWidth，由 Element Plus 原生 fit 吸收）
        pending = 0
      }
      for (let round = 0; round < 8 && pending > 0.5; round++) {
        const active = autoCols.filter((column) => capOf(column) - widths[column.key] > 0.5)
        const activeFixed = fixedColsOnly.filter(
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

      // —— 兜底：所有列都触到 maxWidth 上限后，剩余宽度必须**无上限地**
      //    按权重比例分给各列，否则超宽屏（≥2560px）会出现右侧留白。
      //    这里解除上限约束再分一次，保证 100% 铺满内容区。
      if (pending > 0.5) {
        const wSum =
          autoCols.reduce((sum, column) => sum + weightOf(column), 0) +
          fixedColsOnly.reduce((sum, column) => sum + fixedWeight(column), 0)
        if (wSum > 0) {
          for (const column of autoCols) {
            widths[column.key] += (pending * weightOf(column)) / wSum
          }
          for (const column of fixedColsOnly) {
            widths[column.key] += (pending * fixedWeight(column)) / wSum
          }
          pending = 0
        }
      }
    } else if (baseTotal > available) {
      // —— 不足：按权重比例压缩（受 minWidth 下限约束）——
      //
      // 同样地：用户拖拽过某列后，其余列不应被"连坐"压缩。
      // 空间不够时优先压缩**未拖拽**的列；若都压到 minWidth 仍不够，
      // 就让表格横向溢出（出现滚动条），
      // 而不是把用户精心调整过的列压回去。
      let need = baseTotal - available

      // 用户拖拽过列时：**不压缩任何列**。
      // 需求是「拖动一列不影响其它列」，若这里压缩其它列，
      // 视觉上就是拖一列、全表抖。
      // 空间不够就让它横向溢出（出现滚动条），这才是符合预期的取舍。
      if (manualCols.length > 0) {
        need = 0
      }

      for (let round = 0; round < 8 && need > 0.5; round++) {
        const active = autoCols.filter((column) => widths[column.key] - floorOf(column) > 0.5)
        const activeFixed = fixedColsOnly.filter(
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
    //    注意：手动拖拽过的列不参与调剂 —— 它的宽度完全由用户决定。
    //
    //    用户拖拽过任意一列后，**整体跳过调剂**：
    //    调剂会在多列之间搬运宽度，用户精心调好的版面会被重新打乱，
    //    表现为「拖一列、旁边几列跟着变」。这与
    //    「拖动列改变大小时不要影响其他列」的要求冲突。
    if (fitMode === 'hybrid' && manualCols.length === 0) {
      for (const column of autoCols) {
        if (!column.flexible || !column.idealWidth) continue
        const target = Math.min(column.idealWidth, capOf(column))
        let need = target - widths[column.key]
        if (need <= 0.5) continue

        // 出借方：还有余量（超过自己的理想宽度或保底）且未到上限
        const lenders = autoCols.filter((c) => {
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

    // —— 填充列收尾：吃掉剩余空间 ——
    //   数据列分配完毕后，容器还剩下的宽度全部交给填充列，
    //   表格因此 100% 铺满内容区，且填充列是唯一「伸缩」的列。
    //   剩余空间不足以满足填充列 minWidth 时，填充列取 minWidth，
    //   表格出现横向滚动条（数据列已按比例压缩过，不会溢出容器）。
    if (fillCols.length && rawAvailable > 0) {
      let usedByOthers = cols
        .filter((column) => column.fill !== true)
        .reduce((sum, column) => sum + (widths[column.key] ?? 0), 0)
      ;(options.fixedColumns ?? []).forEach((column) => {
        if (!cols.some((c) => c.key === column.key)) usedByOthers += widths[column.key] ?? 0
      })
      const remainder = rawAvailable - usedByOthers
      fillCols.forEach((column) => {
        const floor = column.minWidth ?? column.width
        const share = fillCols.length === 1 ? remainder : remainder / fillCols.length
        widths[column.key] = Math.max(floor, Math.round(share))
      })
    }

    Object.assign(result, widths)
    return result
  })

  /**
   * 列宽绑定。
   * ---------------------------------------------------------------------
   * 默认返回固定 width —— 剩余空间已在 effectiveWidths 中
   * 按「弹性列优先吸收 + 上限约束 + 其余按比例补给」分配完毕，
   * 再交给 Element Plus 原生 fit 会把空间重新吞回弹性列，
   * 导致短字段列再次被挤压，因此统一使用 width。
   *
   * 例外：标记了 fill 的列（通常是最后一列「操作」）
   * 只吃掉表格右侧的剩余空白，因此**不设固定 width**，只给 min-width。
   *
   * 原理：Element Plus 的表格布局把「没有数值 width 的列」当作弹性列，
   * 会把容器剩余宽度按 minWidth 比例全部分给它们。
   * 只有「操作」列是弹性列时，剩余宽度就全部落在它身上，
   * 表格因此 100% 铺满，而数据列宽度纹丝不动。
   *
   * ⚠️ 反面教材：若给「操作」列也下发数值 width，
   *    EP 会认为「没有弹性列」，剩余宽度无处可去，
   *    表格右侧就会出现一小段留白。
   *
   * 列宽拖拽行为完全遵循 Element Plus 默认语义：
   * 用户拖动某一列只改变该列宽度，其它列宽度不变；
   * 表格总宽是否仍等于容器宽度，由 Element Plus 的弹性列（fill 列）
   * 按其自身规则决定 —— 这是 Element Plus 的原生表现，不额外干预。
   */
  function columnBinding(key: string): { width?: number; minWidth?: number } {
    const column = columns.value.find((item) => item.key === key)
    if (column?.fill) {
      const min = column.minWidth ?? column.width
      return { minWidth: min }
    }
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
    // 可拖拽的「操作」列单独维护宽度
    if (options.actionColumn && key === options.actionColumn.key) {
      return actionColumnWidth.value
    }
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
    // 显隐会改变表格里实际存在的列集合（Vue 用 v-if 增删 <el-table-column>），
    // 因此隐藏/显示之后必须重新落一次顺序，否则新插入的列会回到模板声明位置。
    scheduleOrderSync()
  }

  function resetColumns() {
    // 恢复默认时必须一并清掉「手动拖拽」标记，
    // 否则该列会以手动宽度钉死，再也回不到自适应填充。
    // 同时恢复、**声明顺序**（columns 数组顺序 = 列顺序），
    // 即「恢复默认」一键把顺序也拉回到代码声明的顺序。
    columns.value = options.defaults.map((item) => ({ ...item, manual: false }))
    persist()
    syncLayout()
    scheduleOrderSync()
  }

  /**
   * 调整列顺序（上移 / 下移）。
   * ---------------------------------------------------------------------
   * 列顺序存在 **columns 数组的顺序** 里，
   * 因此调序 = 重排数组，持久化也自动带上新顺序（无需新增字段）。
   *
   * @param key      要移动的列
   * @param direction -1 = 上移（向前一列），+1 = 下移（向后一列）
   */
  function moveColumn(key: string, direction: -1 | 1) {
    const index = columns.value.findIndex((column) => column.key === key)
    if (index === -1) return
    const target = index + direction
    if (target < 0 || target >= columns.value.length) return
    const list = columns.value.slice()
    const [item] = list.splice(index, 1)
    list.splice(target, 0, item)
    columns.value = list
    persist()
    syncLayout()
    scheduleOrderSync()
  }

  /**
   * 按 key 将列移到指定位置（供拖拽排序等后续扩展使用）。
   */
  function moveColumnTo(key: string, toIndex: number) {
    const index = columns.value.findIndex((column) => column.key === key)
    if (index === -1) return
    const clamped = Math.max(0, Math.min(columns.value.length - 1, toIndex))
    if (clamped === index) return
    const list = columns.value.slice()
    const [item] = list.splice(index, 1)
    list.splice(clamped, 0, item)
    columns.value = list
    persist()
    syncLayout()
    scheduleOrderSync()
  }

  /* ==================================================================
   * 渲染顺序同步 —— 让**表格真正渲染出来的列顺序**跟随存储顺序
   * ==================================================================
   * 【问题】
   *   各列表页的模板里，<el-table-column> 是**逐个写死**的，
   *   因此表格的列顺序 = 模板书写顺序。
   *   列设置面板里调了顺序（存在 columns 数组的顺序里），
   *   但**渲染出来的表头不会跟着动** —— 面板改了顺序却看不出效果。
   *
   * 【Element Plus 的列顺序是怎么决定的】
   *   见 element-plus/es/components/table/src：
   *   - 每个 <el-table-column> 在表格内部渲染一个占位 <div>，
   *     这些占位元素是 `.hidden-columns` 容器的**直接子节点**；
   *   - store.sortColumn() 用 `getColumnIndex()` =
   *     `占位 div 在 .hidden-columns.children 里的下标` 作为列的 `no`，
   *     然后按 `no` 升序排序；
   *   - 表头 <th> 由 `store.states.originColumns` 渲染，
   *     因此**列顺序 = 占位 div 的 DOM 顺序**。
   *
   * 【本实现的做法】
   *   1) 按存储顺序算出「目标顺序」；
   *   2) 把 .hidden-columns 里的占位 div 重排成目标顺序（DOM 手术）；
   *   3) 提交 store 的 updateColumnOrder，让 Element Plus 重新按
   *      新的 DOM 下标排序并重绘表头 / 表体。
   *
   * 【为什么不用「把模板改成 v-for」】
   *   26 个列表页的 <el-table-column> 各带不同的插槽、表头、宽度绑定，
   *   改成数据驱动的工作量与风险都很大；而本方案集中在一个 composable 里，
   *   对所有页面一次性生效，且**不改变任何模板**：
   *   列宽拖拽、显隐、columnBinding、固定列、操作列、
   *   window.__ulcDebug 调试钩子全部原样保留。
   *
   * 【健壮性】
   *   - 列显隐会增删 <el-table-column>（v-if），
   *     新插入的占位 div 会回到模板位置 → 因此每次显隐后都要重新落一次顺序；
   *   - 表格数据异步到达后才渲染 → 用 MutationObserver 兜底重排；
   *   - 任何一步失败都静默降级为「保持模板顺序」，不影响页面可用性。
   * ================================================================== */

  /** 取表格组件实例（Element Plus 的 expose 代理） */
  function resolveTableInstance(): any {
    try {
      return tableRef.value ?? null
    } catch {
      return null
    }
  }

  /**
   * 取表格内部 store。
   *
   * Element Plus 的 el-table 通过 expose() 暴露 store，
   * 模板 ref 拿到的是 exposeProxy（proxy.store 可用）；
   * 但不同版本 / 不同挂载时机下 proxy 上不一定有 store，
   * 因此这里按「代理 → 内部实例 → DOM 反查内部实例」逐级兜底。
   */
  function resolveTableStore(table: any): any {
    const candidates = [
      () => table?.store,
      () => table?.$el?.__vueParentComponent?.exposed?.store,
      () => table?.$el?.__vueParentComponent?.store
    ]
    for (const pick of candidates) {
      try {
        const store = pick()
        if (store && store.states) return store
      } catch {
        /* 继续尝试下一个 */
      }
    }
    return null
  }

  /**
   * 取 .hidden-columns 容器（每个 <el-table-column> 的占位 div 的父节点）。
   * 该容器在表格模板里以 ref="hiddenColumns" 暴露，
   * 但未包含在 expose() 列表里，因此统一从 DOM 反查。
   */
  function resolveHiddenColumns(table: any): HTMLElement | null {
    const candidates = [
      () => table?.refs?.hiddenColumns,
      () => table?.$el?.__vueParentComponent?.refs?.hiddenColumns,
      () => table?.$el?.querySelector?.('.hidden-columns')
    ]
    for (const pick of candidates) {
      try {
        const el = pick()
        if (el && el.children) return el as HTMLElement
      } catch {
        /* 继续尝试下一个 */
      }
    }
    return null
  }

  /**
   * 把 .hidden-columns 里的占位 div 重排成存储顺序，并让表格重绘。
   * @returns 是否真的发生了重排（false 表示无需重排或失败降级）
   */
  function applyRenderedOrder(): boolean {
    const table = resolveTableInstance()
    if (!table) return false

    const store = resolveTableStore(table)
    if (!store) return false

    // 1) 取当前表格里真实存在的列配置（按模板顺序）
    //    type 字段用于识别 Element Plus 的**结构列**（selection / index），
    //    它们不属于列配置里的数据列，需要单独钉在最前面。
    let storeColumns: Array<{ id: string; columnKey?: string; no?: number; type?: string }> = []
    try {
      const list = store.states?._columns?.value
      if (Array.isArray(list)) storeColumns = list
    } catch {
      return false
    }
    if (!storeColumns.length) return false

    // 2) 取占位 div（与 storeColumns 一一对应，顺序相同）
    const hidden = resolveHiddenColumns(table)
    if (!hidden) return false
    let placeholders: Element[] = []
    try {
      placeholders = Array.from(hidden.children)
    } catch {
      return false
    }
    if (placeholders.length !== storeColumns.length) return false

    // 3) 算目标顺序：
    //    a) Element Plus 的**结构列**（type = selection / index）
    //       钉在最前面（rank = -1）；
    //    b) 存储顺序里的**可见数据列**按用户配置顺序排在其后；
    //    c) 未登记的列保持原相对次序排在最后
    //       （例如右侧固定的「操作」列必须保持最后）。
    //
    //    【为什么结构列必须钉在最前】
    //      选择列（type="selection"）是**结构性**的，它不在列配置里，
    //      因此 rankOf 里查不到它的 key，只能落到 Number.MAX_SAFE_INTEGER
    //      → 排到最后，表现为「复选框出现在倒数第二列」。
    //      这里改为按 column.type 判定，与是否设置 column-key 无关
    //      （部分表格的 selection 列没有 column-key）。
    const visibleKeys = visibleColumns.value.map((column) => column.key)
    const keyOf = (column: { columnKey?: string }) => column.columnKey ?? ''
    const rankOf = new Map<string, number>()
    visibleKeys.forEach((key, index) => rankOf.set(key, index))

    /** Element Plus 的结构列：选择列 / 序号列，恒定钉在第一列 */
    const isStructuralColumn = (column: { type?: string }) =>
      column.type === 'selection' || column.type === 'index'

    const decorated = storeColumns.map((column, index) => ({
      column,
      index,
      rank: isStructuralColumn(column)
        ? -1
        : rankOf.has(keyOf(column))
          ? rankOf.get(keyOf(column))!
          : Number.MAX_SAFE_INTEGER
    }))
    const sorted = decorated.slice().sort((a, b) => {
      if (a.rank !== b.rank) return a.rank - b.rank
      return a.index - b.index
    })

    // 已经就是目标顺序 → 无需动手
    if (sorted.every((item, index) => item.index === index)) return false

    // 4) DOM 手术：把占位 div 按目标顺序重排
    try {
      const parent = placeholders[0].parentElement
      if (!parent) return false
      sorted.forEach((item, targetIndex) => {
        const el = placeholders[item.index]
        const current = parent.children[targetIndex]
        if (current !== el) parent.insertBefore(el, current ?? null)
      })
    } catch {
      return false
    }

    // 5) 让 Element Plus 重新按新的 DOM 下标排序并重绘表头 / 表体
    //
    //    Element Plus 的 updateColumnOrder 变更体：
    //      if (column.getColumnIndex?.() === column.no) return;   ← 早退条件
    //      sortColumn(states._columns.value);                      ← 按 DOM 下标重算 no 并重排
    //      if (instance.$ready) instance.store.updateColumns();
    //
    //    它要求提交的那一列「DOM 下标与记录的 no 不一致」才会真正重排。
    //    DOM 手术之后，被移动的列的 no 仍是旧值，
    //    因此这里挑「DOM 下标与 no 不一致」的那一列提交，
    //    保证 sortColumn 一定会执行（结构列被移到首位时尤其关键）。
    try {
      const moved =
        sorted.find((item) => {
          const el = placeholders[item.index]
          const currentIndex = el && el.parentElement
            ? Array.prototype.indexOf.call(el.parentElement.children, el)
            : -1
          return currentIndex >= 0 && currentIndex !== item.column.no
        }) ?? sorted.find((item, index) => item.index !== index)
      if (moved) store.commit('updateColumnOrder', moved.column)
      store.updateColumns?.()
    } catch {
      /* 重绘失败不影响数据，忽略 */
    }
    return true
  }

  /** 顺序同步的调度器：合并同一帧内的多次请求 */
  let orderSyncHandle: number | null = null
  function scheduleOrderSync() {
    if (orderSyncHandle !== null) return
    const schedule =
      typeof requestAnimationFrame === 'function'
        ? requestAnimationFrame
        : (cb: FrameRequestCallback) => window.setTimeout(() => cb(0), 16) as unknown as number
    orderSyncHandle = schedule(() => {
      orderSyncHandle = null
      applyRenderedOrder()
    })
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
    // fixed 模式默认不测量容器（列宽恒定），
    // 但存在「填充」列时必须测量：填充列要吃掉表格右侧的剩余空白，
    // 需要知道容器可用宽度才能算出自己的宽度。
    if (fitMode === 'fixed' && !options.defaults.some((column) => column.fill === true)) return
    const el = resolveContainerEl()
    if (el && el.clientWidth > 0) {
      containerWidth.value = el.clientWidth
    }
  }

  let resizeObserver: ResizeObserver | null = null
  let mutationObserver: MutationObserver | null = null
  let dragState: { key: string; startX: number; startWidth: number } | null = null

  /**
   * 「操作」列的列宽拖拽。
   * ---------------------------------------------------------------------
   * 与数据列的手感完全一致（同样的热区位置、同样的拖拽过程），
   * 区别只在于宽度单独存储，不混入列设置面板的宽度池。
   */
  function startActionColumnDrag(event: MouseEvent) {
    event.preventDefault()
    event.stopPropagation()
    const startWidth = actionColumnWidth.value
    dragState = { key: 'action', startX: event.clientX, startWidth }
    document.body.style.userSelect = 'none'
    document.body.style.cursor = 'col-resize'
    const onMove = (moveEvent: MouseEvent) => {
      if (!dragState) return
      const delta = moveEvent.clientX - dragState.startX
      actionColumnWidth.value = Math.min(
        actionColumnMax,
        Math.max(actionColumnMin, dragState.startWidth + delta)
      )
      persistActionColumnWidth()
    }
    const onUp = () => {
      dragState = null
      document.body.style.userSelect = ''
      document.body.style.cursor = ''
      persistActionColumnWidth()
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }

  /**
   * 列宽拖拽（供原生 HTML 表格使用）。
   * ---------------------------------------------------------------------
   * 拖拽热区放在列的标准右边框上（透明元素覆盖在边框位置），
   * 拖拽时只改变列宽，不新增任何可见线条，
   * 因此表头分割线始终与下方各行的列分隔线完全对齐。
   */
  function startColumnDrag(key: string, event: MouseEvent) {
    // 「操作」列走独立分支：宽度不进入 columns，自行持久化
    if (options.actionColumn && key === options.actionColumn.key) {
      startActionColumnDrag(event)
      return
    }
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
      // 一旦用户拖拽，该列宽度即由用户决定：打上 manual 标记，
      // 自适应分配时把它排除在外，刷新后依然精确等于拖拽结果。
      column.manual = true
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

    // —— 冻结其它列的当前宽度 ——
    // 需求：「拖动列改变大小时不要影响其他列」。
    //
    // 自适应模式下，其它列的**实际渲染宽度**是按权重算出来的，
    // 并不等于配置里的基准宽度。若只把被拖的列标记为 manual，
    // 重新计算时其它列会"弹回"基准宽度 —— 视觉上就是拖一列、全表抖一下。
    //
    // 这里在拖拽结束的瞬间，把所有其它列**当前的渲染宽度**写回配置，
    // 于是重新计算时它们的基准就等于拖拽前的样子，纹丝不动。
    const rendered = effectiveWidths.value
    columns.value.forEach((item) => {
      if (item.key === key) return
      const current = rendered[item.key]
      if (typeof current !== 'number' || !Number.isFinite(current) || current <= 0) return
      item.width = Math.round(current)
    })

    target.width = Math.min(maxWidth, Math.max(minWidth, width))
    // 打上 manual 标记：该列宽度完全由用户决定，
    // 自适应分配时不再按权重放大/缩小，刷新后仍是这个宽度。
    target.manual = true
    persist()
    syncLayout()
  }

  function handleWindowResize() {
    measureContainer()
    syncLayout()
  }

  onMounted(() => {
    // 调试钩子：把计算出的列宽挂到 window，便于自动化脚本核对
    // （按 storageKey 登记，避免多个列表互相覆盖）
    const w = window as any
    w.__ulcDebug = w.__ulcDebug || {}
    w.__ulcDebug[options.storageKey] = {
      get widths() {
        return { ...effectiveWidths.value }
      },
      get cols() {
        return columns.value.map((c) => ({
          key: c.key,
          width: c.width,
          manual: c.manual === true,
          fill: c.fill === true,
          minWidth: c.minWidth,
        }))
      },
      get containerWidth() {
        return containerWidth.value
      },
      get reserved() {
        return options.reservedWidth ?? 0
      },
      get fitMode() {
        return fitMode
      },
    }
    // 表格可能尚未完成渲染，延后再测量一次，确保拿到真实容器宽度
    measureContainer()
    nextTick(() => {
      measureContainer()
      syncLayout()
      // 表格挂载后把存储顺序落到实际渲染顺序上
      scheduleOrderSync()
    })
    // 存在「填充」列时，即使 fixed 模式也要观察容器宽度变化：
    // 填充列的宽度随容器宽度变化，需要重新计算才能持续铺满 100%。
    const hasFillColumn = options.defaults.some((column) => column.fill === true)
    if ((fitMode !== 'fixed' || hasFillColumn) && typeof ResizeObserver !== 'undefined') {
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
          // 表格结构变化（列增删 / 首次渲染）后重新落一次列顺序，
          // 保证「显隐一列」之后顺序依旧正确。
          scheduleOrderSync()
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
    if (orderSyncHandle !== null) {
      if (typeof cancelAnimationFrame === 'function') cancelAnimationFrame(orderSyncHandle)
      else window.clearTimeout(orderSyncHandle)
      orderSyncHandle = null
    }
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
    moveColumn,
    moveColumnTo,
    /** 按存储顺序排列的列 key 序列（只含可见列） */
    orderedColumnKeys,
    /** 按存储顺序排列的列 key 序列（含隐藏列） */
    orderedAllColumnKeys,
    persist,
    syncLayout,
    measureContainer,
    startColumnDrag,
    handleHeaderDragEnd,
    /**
     * 把存储顺序落到**实际渲染顺序**（重排表格占位节点 + 触发重绘）。
     * 调序 / 显隐 / 恢复默认之后自动调用，也可由页面在表格重绘后手动调用。
     */
    applyRenderedOrder,
    /** 合并同一帧内的多次顺序同步请求 */
    scheduleOrderSync
  }
}
