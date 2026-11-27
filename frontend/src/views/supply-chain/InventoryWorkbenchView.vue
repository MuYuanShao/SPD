<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Teleport } from 'vue'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import 'element-plus/theme-chalk/el-dialog.css'
import { useRoute } from 'vue-router'
import { CheckCircle2, ClipboardCheck, Eye, PackageSearch, Plus, RefreshCw, Save, Search, X } from '@lucide/vue'
import InventoryEventLedger from '../../components/inventory/InventoryEventLedger.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import type { SpdStatusChipTone } from '../../components/common/SpdStatusChip.vue'
import {
  approveBatchPriceAdjustment,
  approveStocktaking,
  createBatchPriceAdjustment,
  createStocktakingSheet,
  fetchBatchPriceAdjustments,
  fetchInventoryBalances,
  fetchLooseStock,
  type LooseStockRow,
  fetchQuotaPackageStock,
  type QuotaPackageStockRow,
  previewStocktakingSheet,
  fetchStocktakingItems,
  fetchStocktakingList,
  fetchUniqueCodeStock,
  updateStocktakingItems,
  type InventoryBalanceRow,
  type StocktakingSheetItem
} from '../../api/inventory'
import { fetchClosureOptions, type ClosureOptions } from '../../api/operationalClosure'
import { useNotify } from '../../composables/useNotify'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'

import {
  Columns3,
  ChevronDown
} from '@lucide/vue'
import { formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'
import { useFillHeightTable } from '../../composables/useFillHeightTable'

const notify = useNotify()

const route = useRoute()
const balances = ref<InventoryBalanceRow[]>([])
const stocktakingRows = ref<Record<string, unknown>[]>([])
const stocktakingScopeOptions = [
  { key: 'highValue', label: '高值耗材' },
  { key: 'chargeable', label: '可收费耗材' },
  { key: 'nonChargeable', label: '不可收费耗材' },
  { key: 'quotaPackage', label: '定数包' }
] as const
const stocktakingSheetOpen = ref(false)
const stocktakingSheetForm = reactive({
  warehouseName: '',
  deptName: '',
  scopes: [] as string[]
})
const stocktakingOptions = ref<ClosureOptions>({ departments: [], warehouses: [], products: [], balances: [] })
const stocktakingDetailOpen = ref(false)
const stocktakingDetailNo = ref('')
const stocktakingDetailRows = ref<StocktakingSheetItem[]>([])
const stocktakingDetailLoading = ref(false)
const stocktakingDetailSaving = ref(false)
const stocktakingPreviewRows = ref<StocktakingSheetItem[]>([])
const stocktakingPreviewLoading = ref(false)

/** 盘点明细差异数量 = 库存数量 - 盘点数量 */
function stocktakingRowDiff(row: StocktakingSheetItem) {
  if (row.actualQty == null) return null
  return Number(row.systemQty) - Number(row.actualQty)
}

function toggleSheetScope(key: string) {
  const scopes = new Set(stocktakingSheetForm.scopes)
  if (scopes.has(key)) {
    scopes.delete(key)
  } else {
    scopes.add(key)
  }
  stocktakingSheetForm.scopes = [...scopes]
  void loadStocktakingPreview()
}

async function loadStocktakingPreview() {
  stocktakingPreviewRows.value = []
  if (!stocktakingSheetForm.warehouseName || !stocktakingSheetForm.scopes.length) return
  stocktakingPreviewLoading.value = true
  try {
    const result = await previewStocktakingSheet({
      warehouseName: stocktakingSheetForm.warehouseName,
      deptName: stocktakingSheetForm.deptName || undefined,
      scopes: stocktakingSheetForm.scopes
    })
    stocktakingPreviewRows.value = result.rows
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '当前库存明细加载失败')
  } finally {
    stocktakingPreviewLoading.value = false
  }
}

const selectedScopeLabels = computed(() =>
  stocktakingSheetForm.scopes
    .map((key) => stocktakingScopeOptions.find((item) => item.key === key)?.label ?? key)
)

async function openStocktakingSheet() {
  if (!stocktakingOptions.value.warehouses.length) {
    try {
      stocktakingOptions.value = await fetchClosureOptions()
    } catch {
      stocktakingOptions.value = { departments: [], warehouses: [], products: [], balances: [] }
    }
  }
  stocktakingSheetForm.warehouseName = stocktakingOptions.value.warehouses[0]?.warehouseName || ''
  stocktakingSheetForm.deptName = stocktakingOptions.value.departments[0]?.deptName || ''
  stocktakingSheetForm.scopes = []
  stocktakingPreviewRows.value = []
  stocktakingSheetOpen.value = true
}

async function submitStocktakingSheet() {
  if (!stocktakingSheetForm.warehouseName) {
    notify.warning('请选择盘点库房')
    return
  }
  if (!stocktakingSheetForm.scopes.length) {
    notify.warning('请至少选择一个盘点商品范围')
    return
  }
  try {
    const result = await createStocktakingSheet({
      warehouseName: stocktakingSheetForm.warehouseName,
      deptName: stocktakingSheetForm.deptName || undefined,
      scopes: stocktakingSheetForm.scopes
    })
    stocktakingSheetOpen.value = false
    notify.success(`盘点表已生成：${result.stocktakingNo}，共 ${result.rowCount} 条盘点明细`)
    await loadData()
    await openStocktakingDetail(result.stocktakingNo)
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '盘点表生成失败')
  }
}

async function openStocktakingDetail(stocktakingNo: string) {
  stocktakingDetailOpen.value = true
  stocktakingDetailNo.value = stocktakingNo
  stocktakingDetailLoading.value = true
  stocktakingDetailRows.value = []
  try {
    const result = await fetchStocktakingItems(stocktakingNo)
    stocktakingDetailRows.value = result.rows
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '盘点明细加载失败')
  } finally {
    stocktakingDetailLoading.value = false
  }
}

async function saveStocktakingDetail() {
  const items = stocktakingDetailRows.value
    .filter((row) => row.actualQty != null)
    .map((row) => ({ itemId: row.itemId, actualQty: Number(row.actualQty) }))
  if (!items.length) {
    notify.warning('请至少填写一条盘点数量')
    return
  }
  stocktakingDetailSaving.value = true
  try {
    const result = await updateStocktakingItems(stocktakingDetailNo.value, items)
    notify.success(`盘点数量已保存：${result.stocktakingNo}，共 ${result.updatedRows} 条明细`)
    await loadData()
    await openStocktakingDetail(stocktakingDetailNo.value)
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '盘点数量保存失败')
  } finally {
    stocktakingDetailSaving.value = false
  }
}
const priceRows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const inventoryPagination = reactive({
  balances: { page: 1, size: 20, total: 0 },
  looseStock: { page: 1, size: 20, total: 0 },
  quotaStock: { page: 1, size: 20, total: 0 },
  codeStock: { page: 1, size: 20, total: 0 },
  stocktaking: { page: 1, size: 20, total: 0 },
  price: { page: 1, size: 20, total: 0 }
})
const inventoryTabs = [
  { key: 'summary', label: '库存汇总查询' },
  { key: 'quota', label: '定数包库存查询' },
  { key: 'unique', label: '唯一码查询' },
  { key: 'loose', label: '可用散货查询' }
] as const
type InventoryTab = typeof inventoryTabs[number]['key']
const inventoryTab = ref<InventoryTab>('summary')
const looseStockRows = ref<LooseStockRow[]>([])
const looseStockError = ref('')
const looseStockLoading = ref(false)
const looseQuery = reactive({ warehouseName: '', productCode: '', productName: '', systemBatchNo: '' })
const appliedLooseQuery = ref({ ...looseQuery })
let looseRequestVersion = 0
const quotaStockRows = ref<QuotaPackageStockRow[]>([])
const quotaCodesOpen = ref(false)
const selectedQuotaCodes = ref<string[]>([])

function openQuotaCodes(codes: string[]) {
  selectedQuotaCodes.value = codes
  quotaCodesOpen.value = true
}
const codeStockRows = ref<Record<string, unknown>[]>([])
const quotaQuery = reactive({
  warehouseName: '',
  deptName: '',
  packageName: '',
  productName: ''
})
const codeQuery = reactive({
  deptName: '',
  warehouseName: '',
  productCode: '',
  productName: '',
  batchNo: '',
  uniqueCode: '',
  udiCode: ''
})
const query = reactive({
  deptName: '',
  warehouseName: '',
  productCode: '',
  productName: '',
  batchNo: '',
  productionBatchNo: '',
  manufacturerName: '',
  supplierName: '',
  startTime: '',
  endTime: '',
  systemBatchNo: '',
  transactionType: ''
})
const priceForm = reactive({
  systemBatchNo: '',
  newUnitPrice: 0,
  reason: ''
})

const pageCode = computed(() => String(route.params.code))
const isInventoryManagement = computed(() => pageCode.value === 'inventory-management')
const isInventoryTransactionLedger = computed(() => pageCode.value === 'inventory-events')
const mode = computed(() => {
  if (isInventoryTransactionLedger.value) return 'events'
  if (pageCode.value === 'stocktaking-management') return 'stocktaking'
  if (pageCode.value === 'batch-price-adjustment') return 'price'
  return 'inventory'
})
const title = computed(() => {
  if (mode.value === 'events') return '库存交易流水'
  if (mode.value === 'stocktaking') return '盘点管理'
  if (mode.value === 'price') return '价格调整'
  return '库存管理'
})

/**
 * 盘点单状态 → 全站通用状态条色调（SpdStatusChip 的 tone）。
 * 草稿待复核 → warning；已复核归档 → success。
 */
function stocktakingTone(status: unknown): SpdStatusChipTone {
  if (String(status || '') === 'draft') return 'warning'
  return 'success'
}
async function loadLooseStock() {
  const state = inventoryPagination.looseStock
  const version = ++looseRequestVersion
  looseStockError.value = ''
  looseStockLoading.value = true
  try {
    const data = await fetchLooseStock({
      ...appliedLooseQuery.value, page: String(state.page), size: String(state.size)
    })
    if (version !== looseRequestVersion) return
    const lastPage = Math.max(1, Math.ceil(data.total / data.size))
    if (data.page > lastPage) {
      state.page = lastPage
      return await loadLooseStock()
    }
    looseStockRows.value = data.rows
    Object.assign(state, { page: data.page, size: data.size, total: data.total })
  } catch (cause) {
    if (version !== looseRequestVersion) return
    looseStockRows.value = []
    state.total = 0
    looseStockError.value = cause instanceof Error ? cause.message : '可用散货加载失败'
  } finally {
    if (version === looseRequestVersion) looseStockLoading.value = false
  }
}

function searchLooseStock() {
  appliedLooseQuery.value = { ...looseQuery }
  inventoryPagination.looseStock.page = 1
  void loadData()
}

async function loadData() {
  if (mode.value === 'inventory' && inventoryTab.value === 'loose') return loadLooseStock()
  loading.value = true
  try {
    if (mode.value === 'events') return

    if (mode.value === 'inventory') {
      if (inventoryTab.value === 'quota') {
        const data = await fetchQuotaPackageStock({
          ...quotaQuery,
          page: String(inventoryPagination.quotaStock.page),
          size: String(inventoryPagination.quotaStock.size)
        })
        quotaStockRows.value = data.rows
        inventoryPagination.quotaStock.total = data.total
        return
      }
      if (inventoryTab.value === 'unique') {
        const data = await fetchUniqueCodeStock({
          ...codeQuery,
          page: String(inventoryPagination.codeStock.page),
          size: String(inventoryPagination.codeStock.size)
        })
        codeStockRows.value = data.rows
        inventoryPagination.codeStock.total = data.total
        return
      }
    }

    const balanceData = await fetchInventoryBalances({
      deptName: query.deptName,
      warehouseName: query.warehouseName,
      productCode: query.productCode,
      productName: query.productName,
      systemBatchNo: query.systemBatchNo,
      manufacturerName: query.manufacturerName,
      supplierName: query.supplierName,
      page: String(inventoryPagination.balances.page),
      size: String(inventoryPagination.balances.size)
    })
    balances.value = balanceData.rows
    inventoryPagination.balances.total = balanceData.total


    if (mode.value === 'stocktaking') {
      const stocktakingData = await fetchStocktakingList({
        page: String(inventoryPagination.stocktaking.page),
        size: String(inventoryPagination.stocktaking.size)
      })
      stocktakingRows.value = stocktakingData.rows
      inventoryPagination.stocktaking.total = stocktakingData.total
      return
    }

    if (mode.value === 'price') {
      const priceData = await fetchBatchPriceAdjustments({
        page: String(inventoryPagination.price.page),
        size: String(inventoryPagination.price.size)
      })
      priceRows.value = priceData.rows
      inventoryPagination.price.total = priceData.total
    }
  } finally {
    loading.value = false
  }
}

async function changeInventoryPage(key: keyof typeof inventoryPagination, page: number) {
  const state = inventoryPagination[key]
  const totalPages = Math.max(Math.ceil(state.total / Math.max(state.size, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === state.page) return
  state.page = nextPage
  await loadData()
}

async function changeInventoryPageSize(key: keyof typeof inventoryPagination, size: number) {
  const state = inventoryPagination[key]
  if (size === state.size) return
  state.size = size
  state.page = 1
  await loadData()
}

function changeInventoryTab(tab: InventoryTab) {
  if (tab === inventoryTab.value) return
  inventoryTab.value = tab
  inventoryPagination.balances.page = 1
  inventoryPagination.quotaStock.page = 1
  inventoryPagination.codeStock.page = 1
  void loadData()
}

async function approveStocktakingRow(no: string) {
  const result = await approveStocktaking(no)
  notify.success(`${result.stocktakingNo} 已复核通过并生成库存事件`)
  await loadData()
}

async function submitPriceAdjustment() {
  const result = await createBatchPriceAdjustment(priceForm)
  notify.success(`批次调价单已创建：${result.adjustmentNo}，影响库存 ${result.affectedQty}`)
  await loadData()
}

async function approvePriceRow(no: string) {
  const result = await approveBatchPriceAdjustment(no)
  notify.success(`${result.adjustmentNo} 已审批通过`)
  await loadData()
}

onMounted(() => {
  if (!isInventoryTransactionLedger.value) void loadData()
})
watch(mode, () => {
  inventoryPagination.balances.page = 1
  inventoryPagination.stocktaking.page = 1
  inventoryPagination.price.page = 1
  if (!isInventoryTransactionLedger.value) void loadData()
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const INVPRICEADJUST_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'adjustmentNo', label: '调价单号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'systemBatch', label: '系统批次', width: 150, visible: true, weight: 3, minWidth: 120, maxWidth: 220 },
  { key: 'oldUnitPrice', label: '原价', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'newUnitPrice', label: '新价', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'affectedQty', label: '影响数量', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'reason', label: '原因', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 320 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'actions', label: '操作', width: 170, visible: true, fill: true, minWidth: 140, maxWidth: 300 }
]

const invPriceAdjustCols = useListColumns({
  storageKey: 'spd-inv-price-adjust-cols-v1',
  defaults: INVPRICEADJUST_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const invPriceAdjustTableRef = invPriceAdjustCols.tableRef
const fillHeightInvPriceAdjust = useFillHeightTable({ tableRef: invPriceAdjustTableRef })
const isInvPriceAdjustColVisible = (key: string) => invPriceAdjustCols.isColumnVisible(key)
const invPriceAdjustColBinding = (key: string) => invPriceAdjustCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const invPriceAdjustActionFix = useActionColumnFix()
const invPriceAdjustActionFixed = computed(() => invPriceAdjustActionFix.actionFixedBinding.value)

watch(
  [invPriceAdjustTableRef],
  () => {
    invPriceAdjustActionFix.observe(invPriceAdjustTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const invPriceAdjustColSettingsAnchorRef = ref<HTMLElement | null>(null)
const invPriceAdjustColSettingsPopRef = ref<HTMLElement | null>(null)
const invPriceAdjustColSettingsPop = useColSettingsPopover({
  open: invPriceAdjustCols.settingsOpen,
  anchorRef: invPriceAdjustColSettingsAnchorRef,
  popRef: invPriceAdjustColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const INVSTOCKTAKING_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'stocktakingNo', label: '盘点单号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'deptName', label: '科室', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'stocktakingType', label: '类型', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'diffQty', label: '差异', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'reason', label: '原因', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 320 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'actions', label: '操作', width: 170, visible: true, fill: true, minWidth: 140, maxWidth: 300 }
]

const invStocktakingCols = useListColumns({
  storageKey: 'spd-inv-stocktaking-cols-v1',
  defaults: INVSTOCKTAKING_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const invStocktakingTableRef = invStocktakingCols.tableRef
const fillHeightInvStocktaking = useFillHeightTable({ tableRef: invStocktakingTableRef })
const isInvStocktakingColVisible = (key: string) => invStocktakingCols.isColumnVisible(key)
const invStocktakingColBinding = (key: string) => invStocktakingCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const invStocktakingActionFix = useActionColumnFix()
const invStocktakingActionFixed = computed(() => invStocktakingActionFix.actionFixedBinding.value)

watch(
  [invStocktakingTableRef],
  () => {
    invStocktakingActionFix.observe(invStocktakingTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const invStocktakingColSettingsAnchorRef = ref<HTMLElement | null>(null)
const invStocktakingColSettingsPopRef = ref<HTMLElement | null>(null)
const invStocktakingColSettingsPop = useColSettingsPopover({
  open: invStocktakingCols.settingsOpen,
  anchorRef: invStocktakingColSettingsAnchorRef,
  popRef: invStocktakingColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const INVLOOSESTOCK_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productCode', label: '商品编码', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'productName', label: '商品名称', width: 200, visible: true, flexible: true, idealWidth: 280, weight: 8, minWidth: 130, maxWidth: 460 },
  { key: 'spec', label: '规格型号', width: 160, visible: true, flexible: true, idealWidth: 190, weight: 5, minWidth: 104, maxWidth: 280 },
  { key: 'manufacturer', label: '厂家', width: 170, visible: true, flexible: true, idealWidth: 260, weight: 6, minWidth: 104, maxWidth: 400 },
  { key: 'unit', label: '单位', width: 80, visible: true, weight: 2, minWidth: 56, maxWidth: 110 },
  { key: 'unitPrice', label: '单价', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'systemBatch', label: '系统批次', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'productionBatch', label: '生产批号', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'expireDate', label: '有效期', width: 130, visible: true, weight: 2, minWidth: 110, maxWidth: 190 },
  { key: 'availableQty', label: '可用数量', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160, fill: true }
]

const invLooseStockCols = useListColumns({
  storageKey: 'spd-inv-loose-stock-cols-v1',
  defaults: INVLOOSESTOCK_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const invLooseStockTableRef = invLooseStockCols.tableRef
const fillHeightInvLooseStock = useFillHeightTable({ tableRef: invLooseStockTableRef })
const isInvLooseStockColVisible = (key: string) => invLooseStockCols.isColumnVisible(key)
const invLooseStockColBinding = (key: string) => invLooseStockCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const invLooseStockColSettingsAnchorRef = ref<HTMLElement | null>(null)
const invLooseStockColSettingsPopRef = ref<HTMLElement | null>(null)
const invLooseStockColSettingsPop = useColSettingsPopover({
  open: invLooseStockCols.settingsOpen,
  anchorRef: invLooseStockColSettingsAnchorRef,
  popRef: invLooseStockColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const INVCODESTOCK_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'deptName', label: '科室', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productCode', label: '商品编码', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'productName', label: '商品名称', width: 190, visible: true, flexible: true, idealWidth: 280, weight: 8, minWidth: 130, maxWidth: 460 },
  { key: 'spec', label: '规格型号', width: 150, visible: true, flexible: true, idealWidth: 190, weight: 5, minWidth: 104, maxWidth: 280 },
  { key: 'registrationNo', label: '注册证号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'batchNo', label: '批号', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 210 },
  { key: 'systemBatch', label: '批次', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'unitPrice', label: '单价', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'unit', label: '单位', width: 80, visible: true, weight: 2, minWidth: 56, maxWidth: 110 },
  { key: 'quantity', label: '数量', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'amount', label: '金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'manufacturer', label: '厂家', width: 160, visible: true, flexible: true, idealWidth: 260, weight: 6, minWidth: 104, maxWidth: 400 },
  { key: 'supplierName', label: '供应商', width: 160, visible: true, weight: 5, minWidth: 110, maxWidth: 280 },
  { key: 'uniqueCode', label: '唯一码', width: 190, visible: true, weight: 5, minWidth: 140, maxWidth: 300 },
  { key: 'uidCode', label: 'UID码', width: 180, visible: true, weight: 5, minWidth: 140, maxWidth: 300, fill: true }
]

const invCodeStockCols = useListColumns({
  storageKey: 'spd-inv-code-stock-cols-v1',
  defaults: INVCODESTOCK_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const invCodeStockTableRef = invCodeStockCols.tableRef
const fillHeightInvCodeStock = useFillHeightTable({ tableRef: invCodeStockTableRef })
const isInvCodeStockColVisible = (key: string) => invCodeStockCols.isColumnVisible(key)
const invCodeStockColBinding = (key: string) => invCodeStockCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const invCodeStockColSettingsAnchorRef = ref<HTMLElement | null>(null)
const invCodeStockColSettingsPopRef = ref<HTMLElement | null>(null)
const invCodeStockColSettingsPop = useColSettingsPopover({
  open: invCodeStockCols.settingsOpen,
  anchorRef: invCodeStockColSettingsAnchorRef,
  popRef: invCodeStockColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const INVQUOTASTOCK_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'warehouseName', label: '库房', width: 140, visible: true, weight: 4, minWidth: 110, maxWidth: 230 },
  { key: 'deptName', label: '科室', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'packageCode', label: '定数包编码', width: 150, visible: true, weight: 4, minWidth: 120, maxWidth: 230 },
  { key: 'packageUniqueCode', label: '定数包唯一码', width: 200, visible: true, weight: 5, minWidth: 140, maxWidth: 320 },
  { key: 'packageName', label: '定数包名称', width: 190, visible: true, flexible: true, idealWidth: 260, weight: 7, minWidth: 130, maxWidth: 420 },
  { key: 'templateVersion', label: '模板版本', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'productCode', label: '商品编码', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'productName', label: '商品名称', width: 180, visible: true, flexible: true, idealWidth: 260, weight: 7, minWidth: 130, maxWidth: 420 },
  { key: 'spec', label: '规格型号', width: 150, visible: true, flexible: true, idealWidth: 190, weight: 5, minWidth: 104, maxWidth: 280 },
  { key: 'registrationNo', label: '注册证号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'baseUnitPrice', label: '基本单位单价', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'baseUnit', label: '基本单位', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'packageQty', label: '定数包数量', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'packageQtyTotal', label: '包内数量合计', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'looseQty', label: '散货数量', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'amount', label: '金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'manufacturer', label: '厂家', width: 160, visible: true, flexible: true, idealWidth: 260, weight: 6, minWidth: 104, maxWidth: 400 },
  { key: 'supplierName', label: '供应商', width: 160, visible: true, weight: 5, minWidth: 110, maxWidth: 280, fill: true }
]

const invQuotaStockCols = useListColumns({
  storageKey: 'spd-inv-quota-stock-cols-v1',
  defaults: INVQUOTASTOCK_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const invQuotaStockTableRef = invQuotaStockCols.tableRef
const fillHeightInvQuotaStock = useFillHeightTable({ tableRef: invQuotaStockTableRef })
const isInvQuotaStockColVisible = (key: string) => invQuotaStockCols.isColumnVisible(key)
const invQuotaStockColBinding = (key: string) => invQuotaStockCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const invQuotaStockColSettingsAnchorRef = ref<HTMLElement | null>(null)
const invQuotaStockColSettingsPopRef = ref<HTMLElement | null>(null)
const invQuotaStockColSettingsPop = useColSettingsPopover({
  open: invQuotaStockCols.settingsOpen,
  anchorRef: invQuotaStockColSettingsAnchorRef,
  popRef: invQuotaStockColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const INVBALANCE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'deptName', label: '科室', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'productCode', label: '商品编码', width: 150, visible: true, weight: 3, minWidth: 120, maxWidth: 230 },
  { key: 'productName', label: '商品名称', width: 200, visible: true, flexible: true, idealWidth: 280, weight: 8, minWidth: 130, maxWidth: 460 },
  { key: 'spec', label: '规格型号', width: 160, visible: true, flexible: true, idealWidth: 190, weight: 5, minWidth: 104, maxWidth: 280 },
  { key: 'registrationNo', label: '注册证号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'unitPrice', label: '单价', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'unit', label: '单位', width: 90, visible: true, weight: 2, minWidth: 56, maxWidth: 130 },
  { key: 'quantity', label: '数量', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'amount', label: '金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'manufacturer', label: '厂家', width: 170, visible: true, flexible: true, idealWidth: 260, weight: 6, minWidth: 104, maxWidth: 400 },
  { key: 'supplierName', label: '供应商', width: 170, visible: true, weight: 5, minWidth: 110, maxWidth: 280 },
  { key: 'updateTime', label: '更新时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200, fill: true }
]

const invBalanceCols = useListColumns({
  storageKey: 'spd-inv-balance-cols-v1',
  defaults: INVBALANCE_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const invBalanceTableRef = invBalanceCols.tableRef
const fillHeightInvBalance = useFillHeightTable({ tableRef: invBalanceTableRef })
const isInvBalanceColVisible = (key: string) => invBalanceCols.isColumnVisible(key)
const invBalanceColBinding = (key: string) => invBalanceCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const invBalanceColSettingsAnchorRef = ref<HTMLElement | null>(null)
const invBalanceColSettingsPopRef = ref<HTMLElement | null>(null)
const invBalanceColSettingsPop = useColSettingsPopover({
  open: invBalanceCols.settingsOpen,
  anchorRef: invBalanceColSettingsAnchorRef,
  popRef: invBalanceColSettingsPopRef
})

</script>

<template><section class="purchase-page inventory-page spd-unified-list" :class="{ 'price-adjust-page': mode === 'price' }">
    <InventoryEventLedger v-if="isInventoryTransactionLedger" />

    <MdPageHeader
      v-else-if="mode === 'price'"
      crumb="结算与财务"
      :title="title"
      :loading="loading"
      @refresh="loadData"
    />

    <MdPageHeader
      v-else
      crumb="供应链业务"
      :title="title"
      :loading="isInventoryManagement && inventoryTab === 'loose' ? looseStockLoading : loading"
      @refresh="loadData"
    />

    <section v-if="isInventoryManagement" class="inventory-workspace">
      <div class="subnav-tabs inventory-query-tabs" role="tablist" aria-label="库存查询类型">
        <button
          v-for="tab in inventoryTabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="inventoryTab === tab.key"
          :class="{ active: inventoryTab === tab.key }"
          @click="changeInventoryTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 库存汇总查询 -->
      <template v-if="inventoryTab === 'summary'">
        <SpdFilterBar role="search" @submit="loadData">
          <label><span>库房</span><input v-model.trim="query.warehouseName" class="md-input" placeholder="模糊查询库房" /></label>
          <label><span>科室</span><input v-model.trim="query.deptName" class="md-input" placeholder="科室名称" /></label>
          <label><span>商品编码</span><input v-model.trim="query.productCode" class="md-input" placeholder="商品编码" /></label>
          <label><span>商品名称</span><input v-model.trim="query.productName" class="md-input" placeholder="商品名称" /></label>
          <label><span>厂家</span><input v-model.trim="query.manufacturerName" class="md-input" placeholder="厂家名称" /></label>
          <label><span>供应商</span><input v-model.trim="query.supplierName" class="md-input" placeholder="供应商名称" /></label>
        </SpdFilterBar>

                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="invBalanceColSettingsAnchorRef"
             
             
              @click="invBalanceCols.settingsOpen.value = !invBalanceCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="invBalanceCols.settingsOpen.value"
ref="invBalanceColSettingsPopRef"
:class="{ 'is-align-right': invBalanceColSettingsPop.align.value === 'right', 'is-align-up': invBalanceColSettingsPop.vertical.value === 'up' }"
:style="invBalanceColSettingsPop.popStyle.value"
:columns="invBalanceCols.columns.value"
:visible-column-count="invBalanceCols.visibleColumnCount.value"
@toggle="invBalanceCols.setColumnVisible"
@move="invBalanceCols.moveColumn"
@reset="invBalanceCols.resetColumns()"
@confirm="invBalanceCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="invBalanceTableRef"
              :data="balances"
              border
              stripe
              class="ulc-table is-sticky"
              :max-height="fillHeightInvBalance.maxHeight"
              style="width: 100%"
              :empty-text="'暂无库存汇总数据'"
              @header-dragend="(width: number, oldWidth: number, column: any) => invBalanceCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="warehouseName"
                v-if="isInvBalanceColVisible('warehouseName')"
                label="库房"
                v-bind="invBalanceColBinding('warehouseName')"
              >
                <template #header>
                  <span class="ulc-th-text">库房</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.warehouseName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="deptName"
                v-if="isInvBalanceColVisible('deptName')"
                label="科室"
                v-bind="invBalanceColBinding('deptName')"
              >
                <template #header>
                  <span class="ulc-th-text">科室</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.deptName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productCode"
                v-if="isInvBalanceColVisible('productCode')"
                label="商品编码"
                v-bind="invBalanceColBinding('productCode')"
              >
                <template #header>
                  <span class="ulc-th-text">商品编码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productCode }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productName"
                v-if="isInvBalanceColVisible('productName')"
                label="商品名称"
                v-bind="invBalanceColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="spec"
                v-if="isInvBalanceColVisible('spec')"
                label="规格型号"
                v-bind="invBalanceColBinding('spec')"
              >
                <template #header>
                  <span class="ulc-th-text">规格型号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.specModel || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="registrationNo"
                v-if="isInvBalanceColVisible('registrationNo')"
                label="注册证号"
                v-bind="invBalanceColBinding('registrationNo')"
              >
                <template #header>
                  <span class="ulc-th-text">注册证号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.registrationNo || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="unitPrice"
                v-if="isInvBalanceColVisible('unitPrice')"
                label="单价"
                v-bind="invBalanceColBinding('unitPrice')"
              >
                <template #header>
                  <span class="ulc-th-text">单价</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.unitPrice).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="unit"
                v-if="isInvBalanceColVisible('unit')"
                label="单位"
                v-bind="invBalanceColBinding('unit')"
              >
                <template #header>
                  <span class="ulc-th-text">单位</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.unit || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="quantity"
                v-if="isInvBalanceColVisible('quantity')"
                label="数量"
                v-bind="invBalanceColBinding('quantity')"
              >
                <template #header>
                  <span class="ulc-th-text">数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">{{ row.qty }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="amount"
                v-if="isInvBalanceColVisible('amount')"
                label="金额"
                v-bind="invBalanceColBinding('amount')"
              >
                <template #header>
                  <span class="ulc-th-text">金额</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.amount).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="manufacturer"
                v-if="isInvBalanceColVisible('manufacturer')"
                label="厂家"
                v-bind="invBalanceColBinding('manufacturer')"
              >
                <template #header>
                  <span class="ulc-th-text">厂家</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.manufacturerName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="supplierName"
                v-if="isInvBalanceColVisible('supplierName')"
                label="供应商"
                v-bind="invBalanceColBinding('supplierName')"
              >
                <template #header>
                  <span class="ulc-th-text">供应商</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.supplierName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="updateTime"
                v-if="isInvBalanceColVisible('updateTime')"
                label="更新时间"
                v-bind="invBalanceColBinding('updateTime')"
              >
                <template #header>
                  <span class="ulc-th-text">更新时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.updateTime || '-' }}</span>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无库存汇总数据" />
              </template>
            </el-table>
          </div>
        <template #pagination>
          <MdPagination
            :page="inventoryPagination.balances.page"
            :total-pages="Math.max(1, Math.ceil(inventoryPagination.balances.total / Math.max(inventoryPagination.balances.size, 1)))"
            :total="inventoryPagination.balances.total"
            :size="inventoryPagination.balances.size"
            :loading="loading"
            @update:page="changeInventoryPage('balances', $event)"
            @update:size="changeInventoryPageSize('balances', $event)"
          />
        </template>
      </MdDataTableCard>
      </template>

      <!-- 定数包库存查询 -->
      <template v-else-if="inventoryTab === 'quota'">
        <SpdFilterBar role="search" @submit="loadData">
          <label><span>库房</span><input v-model.trim="quotaQuery.warehouseName" class="md-input" placeholder="模糊查询库房" /></label>
          <label><span>科室</span><input v-model.trim="quotaQuery.deptName" class="md-input" placeholder="科室名称" /></label>
          <label><span>定数包名称</span><input v-model.trim="quotaQuery.packageName" class="md-input" placeholder="定数包名称" /></label>
          <label><span>商品名称</span><input v-model.trim="quotaQuery.productName" class="md-input" placeholder="商品名称" /></label>
        </SpdFilterBar>

                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="invQuotaStockColSettingsAnchorRef"
             
             
              @click="invQuotaStockCols.settingsOpen.value = !invQuotaStockCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="invQuotaStockCols.settingsOpen.value"
ref="invQuotaStockColSettingsPopRef"
:class="{ 'is-align-right': invQuotaStockColSettingsPop.align.value === 'right', 'is-align-up': invQuotaStockColSettingsPop.vertical.value === 'up' }"
:style="invQuotaStockColSettingsPop.popStyle.value"
:columns="invQuotaStockCols.columns.value"
:visible-column-count="invQuotaStockCols.visibleColumnCount.value"
@toggle="invQuotaStockCols.setColumnVisible"
@move="invQuotaStockCols.moveColumn"
@reset="invQuotaStockCols.resetColumns()"
@confirm="invQuotaStockCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="invQuotaStockTableRef"
              :data="quotaStockRows"
              border
              stripe
              class="ulc-table is-sticky"
              :max-height="fillHeightInvQuotaStock.maxHeight"
              style="width: 100%"
              :empty-text="'暂无定数包库存'"
              @header-dragend="(width: number, oldWidth: number, column: any) => invQuotaStockCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="warehouseName"
                v-if="isInvQuotaStockColVisible('warehouseName')"
                label="库房"
                v-bind="invQuotaStockColBinding('warehouseName')"
              >
                <template #header>
                  <span class="ulc-th-text">库房</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.warehouseName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="deptName"
                v-if="isInvQuotaStockColVisible('deptName')"
                label="科室"
                v-bind="invQuotaStockColBinding('deptName')"
              >
                <template #header>
                  <span class="ulc-th-text">科室</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.deptName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="packageCode"
                v-if="isInvQuotaStockColVisible('packageCode')"
                label="定数包编码"
                v-bind="invQuotaStockColBinding('packageCode')"
              >
                <template #header>
                  <span class="ulc-th-text">定数包编码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.packageCode }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="packageUniqueCode"
                v-if="isInvQuotaStockColVisible('packageUniqueCode')"
                label="定数包唯一码"
                v-bind="invQuotaStockColBinding('packageUniqueCode')"
              >
                <template #header>
                  <span class="ulc-th-text">定数包唯一码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text"><SpdButton tone="primary" size="small" v-if="row.packageUniqueCodes && row.packageUniqueCodes.length > 1"
                    @click="openQuotaCodes(row.packageUniqueCodes)">
          查看 {{ row.packageUniqueCodes.length }} 个唯一码
        </SpdButton>
                  <span v-else>{{ row.packageUniqueCodes?.[0] || '—' }}</span></span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="packageName"
                v-if="isInvQuotaStockColVisible('packageName')"
                label="定数包名称"
                v-bind="invQuotaStockColBinding('packageName')"
              >
                <template #header>
                  <span class="ulc-th-text">定数包名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.packageName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="templateVersion"
                v-if="isInvQuotaStockColVisible('templateVersion')"
                label="模板版本"
                v-bind="invQuotaStockColBinding('templateVersion')"
              >
                <template #header>
                  <span class="ulc-th-text">模板版本</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.templateVersion ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productCode"
                v-if="isInvQuotaStockColVisible('productCode')"
                label="商品编码"
                v-bind="invQuotaStockColBinding('productCode')"
              >
                <template #header>
                  <span class="ulc-th-text">商品编码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productCode || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productName"
                v-if="isInvQuotaStockColVisible('productName')"
                label="商品名称"
                v-bind="invQuotaStockColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="spec"
                v-if="isInvQuotaStockColVisible('spec')"
                label="规格型号"
                v-bind="invQuotaStockColBinding('spec')"
              >
                <template #header>
                  <span class="ulc-th-text">规格型号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.specModel || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="registrationNo"
                v-if="isInvQuotaStockColVisible('registrationNo')"
                label="注册证号"
                v-bind="invQuotaStockColBinding('registrationNo')"
              >
                <template #header>
                  <span class="ulc-th-text">注册证号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.registrationNo || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="baseUnitPrice"
                v-if="isInvQuotaStockColVisible('baseUnitPrice')"
                label="基本单位单价"
                v-bind="invQuotaStockColBinding('baseUnitPrice')"
              >
                <template #header>
                  <span class="ulc-th-text">基本单位单价</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.unitPrice).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="baseUnit"
                v-if="isInvQuotaStockColVisible('baseUnit')"
                label="基本单位"
                v-bind="invQuotaStockColBinding('baseUnit')"
              >
                <template #header>
                  <span class="ulc-th-text">基本单位</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.unit || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="packageQty"
                v-if="isInvQuotaStockColVisible('packageQty')"
                label="定数包数量"
                v-bind="invQuotaStockColBinding('packageQty')"
              >
                <template #header>
                  <span class="ulc-th-text">定数包数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">{{ row.packageCount }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="packageQtyTotal"
                v-if="isInvQuotaStockColVisible('packageQtyTotal')"
                label="包内数量合计"
                v-bind="invQuotaStockColBinding('packageQtyTotal')"
              >
                <template #header>
                  <span class="ulc-th-text">包内数量合计</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.packageQty ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="looseQty"
                v-if="isInvQuotaStockColVisible('looseQty')"
                label="散货数量"
                v-bind="invQuotaStockColBinding('looseQty')"
              >
                <template #header>
                  <span class="ulc-th-text">散货数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">{{ row.looseQty }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="amount"
                v-if="isInvQuotaStockColVisible('amount')"
                label="金额"
                v-bind="invQuotaStockColBinding('amount')"
              >
                <template #header>
                  <span class="ulc-th-text">金额</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.amount).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="manufacturer"
                v-if="isInvQuotaStockColVisible('manufacturer')"
                label="厂家"
                v-bind="invQuotaStockColBinding('manufacturer')"
              >
                <template #header>
                  <span class="ulc-th-text">厂家</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.manufacturerName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="supplierName"
                v-if="isInvQuotaStockColVisible('supplierName')"
                label="供应商"
                v-bind="invQuotaStockColBinding('supplierName')"
              >
                <template #header>
                  <span class="ulc-th-text">供应商</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.supplierName }}</span>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无定数包库存" />
              </template>
            </el-table>
          </div>
        <template #pagination>
          <MdPagination
            :page="inventoryPagination.quotaStock.page"
            :total-pages="Math.max(1, Math.ceil(inventoryPagination.quotaStock.total / Math.max(inventoryPagination.quotaStock.size, 1)))"
            :total="inventoryPagination.quotaStock.total"
            :size="inventoryPagination.quotaStock.size"
            :loading="loading"
            @update:page="changeInventoryPage('quotaStock', $event)"
            @update:size="changeInventoryPageSize('quotaStock', $event)"
          />
        </template>
      </MdDataTableCard>
      </template>

      <!-- 唯一码查询 -->
      <template v-else-if="inventoryTab === 'unique'">
        <SpdFilterBar role="search" @submit="loadData">
          <label><span>科室</span><input v-model.trim="codeQuery.deptName" class="md-input" placeholder="科室名称" /></label>
          <label><span>库房</span><input v-model.trim="codeQuery.warehouseName" class="md-input" placeholder="库房名称" /></label>
          <label><span>商品编码</span><input v-model.trim="codeQuery.productCode" class="md-input" placeholder="商品编码" /></label>
          <label><span>商品名称</span><input v-model.trim="codeQuery.productName" class="md-input" placeholder="商品名称" /></label>
          <label><span>批号</span><input v-model.trim="codeQuery.batchNo" class="md-input" placeholder="系统批号" /></label>
          <label><span>唯一码</span><input v-model.trim="codeQuery.uniqueCode" class="md-input" placeholder="唯一码" /></label>
          <label><span>UID码</span><input v-model.trim="codeQuery.udiCode" class="md-input" placeholder="UID码" /></label>
        </SpdFilterBar>

                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="invCodeStockColSettingsAnchorRef"
             
             
              @click="invCodeStockCols.settingsOpen.value = !invCodeStockCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="invCodeStockCols.settingsOpen.value"
ref="invCodeStockColSettingsPopRef"
:class="{ 'is-align-right': invCodeStockColSettingsPop.align.value === 'right', 'is-align-up': invCodeStockColSettingsPop.vertical.value === 'up' }"
:style="invCodeStockColSettingsPop.popStyle.value"
:columns="invCodeStockCols.columns.value"
:visible-column-count="invCodeStockCols.visibleColumnCount.value"
@toggle="invCodeStockCols.setColumnVisible"
@move="invCodeStockCols.moveColumn"
@reset="invCodeStockCols.resetColumns()"
@confirm="invCodeStockCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="invCodeStockTableRef"
              :data="codeStockRows"
              border
              stripe
              class="ulc-table is-sticky"
              :max-height="fillHeightInvCodeStock.maxHeight"
              style="width: 100%"
              :empty-text="'暂无唯一码库存'"
              @header-dragend="(width: number, oldWidth: number, column: any) => invCodeStockCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="deptName"
                v-if="isInvCodeStockColVisible('deptName')"
                label="科室"
                v-bind="invCodeStockColBinding('deptName')"
              >
                <template #header>
                  <span class="ulc-th-text">科室</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.deptName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="warehouseName"
                v-if="isInvCodeStockColVisible('warehouseName')"
                label="库房"
                v-bind="invCodeStockColBinding('warehouseName')"
              >
                <template #header>
                  <span class="ulc-th-text">库房</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.warehouseName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productCode"
                v-if="isInvCodeStockColVisible('productCode')"
                label="商品编码"
                v-bind="invCodeStockColBinding('productCode')"
              >
                <template #header>
                  <span class="ulc-th-text">商品编码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productCode }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productName"
                v-if="isInvCodeStockColVisible('productName')"
                label="商品名称"
                v-bind="invCodeStockColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="spec"
                v-if="isInvCodeStockColVisible('spec')"
                label="规格型号"
                v-bind="invCodeStockColBinding('spec')"
              >
                <template #header>
                  <span class="ulc-th-text">规格型号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.specModel || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="registrationNo"
                v-if="isInvCodeStockColVisible('registrationNo')"
                label="注册证号"
                v-bind="invCodeStockColBinding('registrationNo')"
              >
                <template #header>
                  <span class="ulc-th-text">注册证号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.registrationNo || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="batchNo"
                v-if="isInvCodeStockColVisible('batchNo')"
                label="批号"
                v-bind="invCodeStockColBinding('batchNo')"
              >
                <template #header>
                  <span class="ulc-th-text">批号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.batchNo }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="systemBatch"
                v-if="isInvCodeStockColVisible('systemBatch')"
                label="批次"
                v-bind="invCodeStockColBinding('systemBatch')"
              >
                <template #header>
                  <span class="ulc-th-text">批次</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productionBatchNo }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="unitPrice"
                v-if="isInvCodeStockColVisible('unitPrice')"
                label="单价"
                v-bind="invCodeStockColBinding('unitPrice')"
              >
                <template #header>
                  <span class="ulc-th-text">单价</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.unitPrice).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="unit"
                v-if="isInvCodeStockColVisible('unit')"
                label="单位"
                v-bind="invCodeStockColBinding('unit')"
              >
                <template #header>
                  <span class="ulc-th-text">单位</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.unit || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="quantity"
                v-if="isInvCodeStockColVisible('quantity')"
                label="数量"
                v-bind="invCodeStockColBinding('quantity')"
              >
                <template #header>
                  <span class="ulc-th-text">数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">{{ row.qty }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="amount"
                v-if="isInvCodeStockColVisible('amount')"
                label="金额"
                v-bind="invCodeStockColBinding('amount')"
              >
                <template #header>
                  <span class="ulc-th-text">金额</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.amount).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="manufacturer"
                v-if="isInvCodeStockColVisible('manufacturer')"
                label="厂家"
                v-bind="invCodeStockColBinding('manufacturer')"
              >
                <template #header>
                  <span class="ulc-th-text">厂家</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.manufacturerName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="supplierName"
                v-if="isInvCodeStockColVisible('supplierName')"
                label="供应商"
                v-bind="invCodeStockColBinding('supplierName')"
              >
                <template #header>
                  <span class="ulc-th-text">供应商</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.supplierName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="uniqueCode"
                v-if="isInvCodeStockColVisible('uniqueCode')"
                label="唯一码"
                v-bind="invCodeStockColBinding('uniqueCode')"
              >
                <template #header>
                  <span class="ulc-th-text">唯一码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.uniqueCode }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="uidCode"
                v-if="isInvCodeStockColVisible('uidCode')"
                label="UID码"
                v-bind="invCodeStockColBinding('uidCode')"
              >
                <template #header>
                  <span class="ulc-th-text">UID码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.udiCode }}</span>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无唯一码库存" />
              </template>
            </el-table>
          </div>
        <template #pagination>
          <MdPagination
            :page="inventoryPagination.codeStock.page"
            :total-pages="Math.max(1, Math.ceil(inventoryPagination.codeStock.total / Math.max(inventoryPagination.codeStock.size, 1)))"
            :total="inventoryPagination.codeStock.total"
            :size="inventoryPagination.codeStock.size"
            :loading="loading"
            @update:page="changeInventoryPage('codeStock', $event)"
            @update:size="changeInventoryPageSize('codeStock', $event)"
          />
        </template>
      </MdDataTableCard>
      </template>
      <template v-else-if="inventoryTab === 'loose'">
        <SpdFilterBar aria-label="可用散货查询" @submit="searchLooseStock">
          <label><span>库房</span><input v-model.trim="looseQuery.warehouseName" placeholder="中心库或一级库" /></label>
          <label><span>商品编码</span><input v-model.trim="looseQuery.productCode" /></label>
          <label><span>商品名称</span><input v-model.trim="looseQuery.productName" /></label>
          <label><span>系统批次</span><input v-model.trim="looseQuery.systemBatchNo" /></label>
        </SpdFilterBar>
                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="invLooseStockColSettingsAnchorRef"
             
             
              @click="invLooseStockCols.settingsOpen.value = !invLooseStockCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="invLooseStockCols.settingsOpen.value"
ref="invLooseStockColSettingsPopRef"
:class="{ 'is-align-right': invLooseStockColSettingsPop.align.value === 'right', 'is-align-up': invLooseStockColSettingsPop.vertical.value === 'up' }"
:style="invLooseStockColSettingsPop.popStyle.value"
:columns="invLooseStockCols.columns.value"
:visible-column-count="invLooseStockCols.visibleColumnCount.value"
@toggle="invLooseStockCols.setColumnVisible"
@move="invLooseStockCols.moveColumn"
@reset="invLooseStockCols.resetColumns()"
@confirm="invLooseStockCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="invLooseStockTableRef"
              :data="looseStockRows"
              border
              stripe
              class="ulc-table is-sticky"
              :max-height="fillHeightInvLooseStock.maxHeight"
              style="width: 100%"
              :empty-text="'暂无散货库存'"
              @header-dragend="(width: number, oldWidth: number, column: any) => invLooseStockCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="warehouseName"
                v-if="isInvLooseStockColVisible('warehouseName')"
                label="库房"
                v-bind="invLooseStockColBinding('warehouseName')"
              >
                <template #header>
                  <span class="ulc-th-text">库房</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.warehouseName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productCode"
                v-if="isInvLooseStockColVisible('productCode')"
                label="商品编码"
                v-bind="invLooseStockColBinding('productCode')"
              >
                <template #header>
                  <span class="ulc-th-text">商品编码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productCode }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productName"
                v-if="isInvLooseStockColVisible('productName')"
                label="商品名称"
                v-bind="invLooseStockColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="spec"
                v-if="isInvLooseStockColVisible('spec')"
                label="规格型号"
                v-bind="invLooseStockColBinding('spec')"
              >
                <template #header>
                  <span class="ulc-th-text">规格型号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.specModel || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="manufacturer"
                v-if="isInvLooseStockColVisible('manufacturer')"
                label="厂家"
                v-bind="invLooseStockColBinding('manufacturer')"
              >
                <template #header>
                  <span class="ulc-th-text">厂家</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.manufacturerName || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="unit"
                v-if="isInvLooseStockColVisible('unit')"
                label="单位"
                v-bind="invLooseStockColBinding('unit')"
              >
                <template #header>
                  <span class="ulc-th-text">单位</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.unit || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="unitPrice"
                v-if="isInvLooseStockColVisible('unitPrice')"
                label="单价"
                v-bind="invLooseStockColBinding('unitPrice')"
              >
                <template #header>
                  <span class="ulc-th-text">单价</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">{{ row.unitPrice == null ? '—' : Number(row.unitPrice).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="systemBatch"
                v-if="isInvLooseStockColVisible('systemBatch')"
                label="系统批次"
                v-bind="invLooseStockColBinding('systemBatch')"
              >
                <template #header>
                  <span class="ulc-th-text">系统批次</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.systemBatchNo }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productionBatch"
                v-if="isInvLooseStockColVisible('productionBatch')"
                label="生产批号"
                v-bind="invLooseStockColBinding('productionBatch')"
              >
                <template #header>
                  <span class="ulc-th-text">生产批号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productionBatchNo || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="expireDate"
                v-if="isInvLooseStockColVisible('expireDate')"
                label="有效期"
                v-bind="invLooseStockColBinding('expireDate')"
              >
                <template #header>
                  <span class="ulc-th-text">有效期</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.expireDate || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="availableQty"
                v-if="isInvLooseStockColVisible('availableQty')"
                label="可用数量"
                v-bind="invLooseStockColBinding('availableQty')"
              >
                <template #header>
                  <span class="ulc-th-text">可用数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text ulc-number">{{ row.availableQty }}</span>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无散货库存" />
              </template>
            </el-table>
          </div>
        <template #pagination>
          <MdPagination
            :page="inventoryPagination.looseStock.page"
            :total-pages="Math.max(1, Math.ceil(inventoryPagination.looseStock.total / Math.max(inventoryPagination.looseStock.size, 1)))"
            :total="inventoryPagination.looseStock.total"
            :size="inventoryPagination.looseStock.size"
            :loading="looseStockLoading"
            @update:page="changeInventoryPage('looseStock', $event)"
            @update:size="changeInventoryPageSize('looseStock', $event)"
          />
        </template>
      </MdDataTableCard>
      </template>
    </section>

    <section v-if="mode === 'stocktaking'" class="hospital-catalog-panel stocktaking-workspace">
      <div class="section-title stocktaking-title">
        <ClipboardCheck :size="20" />
        <h3>盘点管理</h3>
        <SpdButton label="新增盘点表" tone="primary" class="stocktaking-create-btn" @click="openStocktakingSheet">
          <template #icon><Plus :size="16" /></template>
        </SpdButton>
      </div>
      <p class="stocktaking-hint">盘点复核后生成盘盈/盘亏库存事件，禁止形成负库存。</p>
      
              <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="invStocktakingColSettingsAnchorRef"
           
           
            @click="invStocktakingCols.settingsOpen.value = !invStocktakingCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="invStocktakingCols.settingsOpen.value"
ref="invStocktakingColSettingsPopRef"
:class="{ 'is-align-right': invStocktakingColSettingsPop.align.value === 'right', 'is-align-up': invStocktakingColSettingsPop.vertical.value === 'up' }"
:style="invStocktakingColSettingsPop.popStyle.value"
:columns="invStocktakingCols.columns.value"
:visible-column-count="invStocktakingCols.visibleColumnCount.value"
@toggle="invStocktakingCols.setColumnVisible"
@move="invStocktakingCols.moveColumn"
@reset="invStocktakingCols.resetColumns()"
@confirm="invStocktakingCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="invStocktakingTableRef"
            :data="stocktakingRows"
            row-key="stocktakingNo"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightInvStocktaking.maxHeight"
            style="width: 100%"
            :empty-text="'暂无盘点表'"
            @header-dragend="(width: number, oldWidth: number, column: any) => invStocktakingCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="stocktakingNo"
              v-if="isInvStocktakingColVisible('stocktakingNo')"
              label="盘点单号"
              v-bind="invStocktakingColBinding('stocktakingNo')"
            >
              <template #header>
                <span class="ulc-th-text">盘点单号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.stocktakingNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="warehouseName"
              v-if="isInvStocktakingColVisible('warehouseName')"
              label="库房"
              v-bind="invStocktakingColBinding('warehouseName')"
            >
              <template #header>
                <span class="ulc-th-text">库房</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.warehouseName }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="deptName"
              v-if="isInvStocktakingColVisible('deptName')"
              label="科室"
              v-bind="invStocktakingColBinding('deptName')"
            >
              <template #header>
                <span class="ulc-th-text">科室</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.deptName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="stocktakingType"
              v-if="isInvStocktakingColVisible('stocktakingType')"
              label="类型"
              v-bind="invStocktakingColBinding('stocktakingType')"
            >
              <template #header>
                <span class="ulc-th-text">类型</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.stocktakingType || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="diffQty"
              v-if="isInvStocktakingColVisible('diffQty')"
              label="差异"
              v-bind="invStocktakingColBinding('diffQty')"
            >
              <template #header>
                <span class="ulc-th-text">差异</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.diffQty ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isInvStocktakingColVisible('status')"
              label="状态"
              v-bind="invStocktakingColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip :tone="stocktakingTone(row.status)" :label="formatStatusText(row.status)" />
              </template>
            </el-table-column>
            <el-table-column
              column-key="reason"
              v-if="isInvStocktakingColVisible('reason')"
              label="原因"
              v-bind="invStocktakingColBinding('reason')"
            >
              <template #header>
                <span class="ulc-th-text">原因</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.reason || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isInvStocktakingColVisible('createTime')"
              label="创建时间"
              v-bind="invStocktakingColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isInvStocktakingColVisible('actions')"
              label="操作"
              v-bind="invStocktakingColBinding('actions')"
              :fixed="invStocktakingActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button @click="openStocktakingDetail(String(row.stocktakingNo))" class="ulc-action" type="button"><Eye :size="15" /> 明细</button>
                <button v-if="row.status === 'draft'" @click="approveStocktakingRow(String(row.stocktakingNo))" class="ulc-action stocktaking-approve-btn" type="button"><CheckCircle2 :size="15" /> 复核</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无盘点表" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="inventoryPagination.stocktaking.page"
            :total-pages="Math.max(1, Math.ceil(inventoryPagination.stocktaking.total / Math.max(inventoryPagination.stocktaking.size, 1)))"
            :total="inventoryPagination.stocktaking.total"
            :size="inventoryPagination.stocktaking.size"
            :loading="loading"
            @update:page="changeInventoryPage('stocktaking', $event)"
            @update:size="changeInventoryPageSize('stocktaking', $event)"
          />
        </template>
      </MdDataTableCard>
    </section>

    <section v-if="mode === 'price'" class="hospital-catalog-panel">
      <div class="section-title">
        <PackageSearch :size="20" />
        <h3>批次调价</h3>
      </div>
      <SpdFilterBar>
        <label><span>系统批次</span><input v-model="priceForm.systemBatchNo" /></label>
        <label><span>新批次单价</span><input v-model.number="priceForm.newUnitPrice" type="number" step="0.01" /></label>
        <label class="wide-field"><span>调价原因</span><input v-model="priceForm.reason" /></label>
        <template #actions>
          <SpdButton label="保存" tone="success" @click="submitPriceAdjustment">
            <template #icon><Save :size="18" /></template>
          </SpdButton>
        </template>
      </SpdFilterBar>
      
              <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="invPriceAdjustColSettingsAnchorRef"
           
           
            @click="invPriceAdjustCols.settingsOpen.value = !invPriceAdjustCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="invPriceAdjustCols.settingsOpen.value"
ref="invPriceAdjustColSettingsPopRef"
:class="{ 'is-align-right': invPriceAdjustColSettingsPop.align.value === 'right', 'is-align-up': invPriceAdjustColSettingsPop.vertical.value === 'up' }"
:style="invPriceAdjustColSettingsPop.popStyle.value"
:columns="invPriceAdjustCols.columns.value"
:visible-column-count="invPriceAdjustCols.visibleColumnCount.value"
@toggle="invPriceAdjustCols.setColumnVisible"
@move="invPriceAdjustCols.moveColumn"
@reset="invPriceAdjustCols.resetColumns()"
@confirm="invPriceAdjustCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="invPriceAdjustTableRef"
            :data="priceRows"
            row-key="adjustmentNo"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightInvPriceAdjust.maxHeight"
            style="width: 100%"
            :empty-text="'暂无调价单'"
            @header-dragend="(width: number, oldWidth: number, column: any) => invPriceAdjustCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="adjustmentNo"
              v-if="isInvPriceAdjustColVisible('adjustmentNo')"
              label="调价单号"
              v-bind="invPriceAdjustColBinding('adjustmentNo')"
            >
              <template #header>
                <span class="ulc-th-text">调价单号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.adjustmentNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="systemBatch"
              v-if="isInvPriceAdjustColVisible('systemBatch')"
              label="系统批次"
              v-bind="invPriceAdjustColBinding('systemBatch')"
            >
              <template #header>
                <span class="ulc-th-text">系统批次</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.systemBatchNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="oldUnitPrice"
              v-if="isInvPriceAdjustColVisible('oldUnitPrice')"
              label="原价"
              v-bind="invPriceAdjustColBinding('oldUnitPrice')"
            >
              <template #header>
                <span class="ulc-th-text">原价</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.oldUnitPrice).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="newUnitPrice"
              v-if="isInvPriceAdjustColVisible('newUnitPrice')"
              label="新价"
              v-bind="invPriceAdjustColBinding('newUnitPrice')"
            >
              <template #header>
                <span class="ulc-th-text">新价</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.newUnitPrice).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="affectedQty"
              v-if="isInvPriceAdjustColVisible('affectedQty')"
              label="影响数量"
              v-bind="invPriceAdjustColBinding('affectedQty')"
            >
              <template #header>
                <span class="ulc-th-text">影响数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.affectedQty }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isInvPriceAdjustColVisible('status')"
              label="状态"
              v-bind="invPriceAdjustColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatStatusText(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="reason"
              v-if="isInvPriceAdjustColVisible('reason')"
              label="原因"
              v-bind="invPriceAdjustColBinding('reason')"
            >
              <template #header>
                <span class="ulc-th-text">原因</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.reason || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isInvPriceAdjustColVisible('createTime')"
              label="创建时间"
              v-bind="invPriceAdjustColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isInvPriceAdjustColVisible('actions')"
              label="操作"
              v-bind="invPriceAdjustColBinding('actions')"
              :fixed="invPriceAdjustActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button v-if="row.status === 'draft'" @click="approvePriceRow(String(row.adjustmentNo))" class="ulc-action btn-primary btn-sm" type="button"><CheckCircle2 :size="16" /> 审批</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无调价单" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="inventoryPagination.price.page"
            :total-pages="Math.max(1, Math.ceil(inventoryPagination.price.total / Math.max(inventoryPagination.price.size, 1)))"
            :total="inventoryPagination.price.total"
            :size="inventoryPagination.price.size"
            :loading="loading"
            @update:page="changeInventoryPage('price', $event)"
            @update:size="changeInventoryPageSize('price', $event)"
          />
        </template>
      </MdDataTableCard>
    </section>

    <Teleport to="body">
    <div v-if="stocktakingSheetOpen" class="attachment-preview-mask" @click.self="stocktakingSheetOpen = false">
      <section class="supplier-dialog product-dialog stocktaking-sheet-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>盘点管理</p>
            <h3>新增盘点表</h3>
          </div>
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭" @click="stocktakingSheetOpen = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
        </header>
        <form @submit.prevent="submitStocktakingSheet">
          <div class="supplier-form-grid compact">
            <label>
              <span>盘点库房</span>
              <el-select v-model="stocktakingSheetForm.warehouseName" popper-class="spd-select-popper" placeholder="请选择库房" @change="loadStocktakingPreview">
                <el-option label="" value="" />
                <el-option v-for="item in stocktakingOptions.warehouses" :key="item.warehouseName" :label="item.warehouseName" :value="item.warehouseName" />
              </el-select>
            </label>
            <label>
              <span>盘点科室</span>
              <el-select v-model="stocktakingSheetForm.deptName" popper-class="spd-select-popper" placeholder="全院">
                <el-option label="全院" value="" />
                <el-option v-for="item in stocktakingOptions.departments" :key="item.deptCode" :label="item.deptName" :value="item.deptName" />
              </el-select>
            </label>
          </div>
          <div class="stocktaking-scope-picker">
            <div class="stocktaking-scope-head">
              <strong>选择商品范围（可多选）</strong>
              <span>按所选范围生成盘点明细，支持多个范围合并盘点</span>
            </div>
            <div class="stocktaking-scope-options">
              <label v-for="option in stocktakingScopeOptions" :key="option.key" class="stocktaking-scope-option">
                <input
                  type="checkbox"
                  :checked="stocktakingSheetForm.scopes.includes(option.key)"
                  @change="toggleSheetScope(option.key)"
                />
                <span>{{ option.label }}</span>
              </label>
            </div>
            <div class="stocktaking-scope-selected">
              <span>已选范围：</span>
              <template v-if="selectedScopeLabels.length">
                <span v-for="label in selectedScopeLabels" :key="label" class="stocktaking-scope-chip">{{ label }}</span>
              </template>
              <em v-else>未选择任何范围</em>
            </div>
          </div>
          <div class="table-scroll stocktaking-detail-scroll">
            <table class="master-table stocktaking-detail-table">
              <thead>
                <tr>
                  <th>商品编码</th><th>商品名称</th><th>规格型号</th><th>厂家</th><th>单位</th><th>当前库存</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="stocktakingPreviewLoading"><td colspan="6" class="approval-empty">正在加载当前库房库存...</td></tr>
                <tr v-else-if="stocktakingSheetForm.scopes.length && !stocktakingPreviewRows.length">
                  <td colspan="6" class="approval-empty">所选范围暂无库存明细</td>
                </tr>
                <tr v-else-if="!stocktakingSheetForm.scopes.length"><td colspan="6" class="approval-empty">选择商品范围后展示当前库存明细</td></tr>
                <tr v-for="row in stocktakingPreviewRows" v-else :key="row.productCode">
                  <td class="stocktaking-code-cell">{{ row.productCode }}</td><td>{{ row.productName }}</td><td>{{ row.specModel || '-' }}</td>
                  <td>{{ row.manufacturerName || '-' }}</td><td>{{ row.unit || '-' }}</td><td class="stocktaking-qty-cell">{{ row.systemQty }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="dialog-actions">
            <SpdButton label="取消" tone="neutral" @click="stocktakingSheetOpen = false"></SpdButton>
            <SpdButton label="确认生成盘点明细" tone="primary" type="submit">
          <template #icon><CheckCircle2 :size="16" /></template>
        </SpdButton>
          </div>
        </form>
      </section>
    </div>
    </Teleport>
    <ElDialog v-model="quotaCodesOpen" title="定数包唯一码" width="min(560px, calc(100vw - 32px))"
      class="quota-stock-codes-dialog" align-center append-to-body destroy-on-close>
      <div class="quota-stock-code-list" role="region" aria-label="定数包唯一码列表" tabindex="0">
        <ul>
          <li v-for="code in selectedQuotaCodes" :key="code"><code>{{ code }}</code></li>
        </ul>
      </div>
      <template #footer>
        <SpdButton label="关闭" tone="neutral" @click="quotaCodesOpen = false"></SpdButton>
      </template>
    </ElDialog>

    <Teleport to="body">
    <div v-if="stocktakingDetailOpen" class="attachment-preview-mask" @click.self="stocktakingDetailOpen = false">
      <section class="supplier-dialog stocktaking-detail-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>盘点明细</p>
            <h3>{{ stocktakingDetailNo }}</h3>
          </div>
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭" @click="stocktakingDetailOpen = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
        </header>
        <div class="table-scroll stocktaking-detail-scroll">
          <table class="master-table stocktaking-detail-table">
            <thead>
              <tr>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格型号</th>
                <th>厂家</th>
                <th>单位</th>
                <th>库存数量</th>
                <th>盘点数量</th>
                <th>差异数量</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="stocktakingDetailLoading">
                <td colspan="8" class="approval-empty">正在加载盘点明细...</td>
              </tr>
              <tr v-else-if="!stocktakingDetailRows.length">
                <td colspan="8" class="approval-empty">所选范围内暂无库存商品</td>
              </tr>
              <tr v-for="row in stocktakingDetailRows" v-else :key="row.itemId">
                <td class="code-cell">{{ row.productCode }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.specModel || '-' }}</td>
                <td>{{ row.manufacturerName || '-' }}</td>
                <td>{{ row.unit || '-' }}</td>
                <td class="number-cell">{{ row.systemQty }}</td>
                <td>
                  <input v-integer-quantity
                    v-model.number="row.actualQty"
                    class="stocktaking-qty-input"
                    type="number"
                    min="0" step="1"
                    placeholder="待盘点"
                  />
                </td>
                <td class="number-cell" :class="{ profit: stocktakingRowDiff(row) !== null && Number(stocktakingRowDiff(row)) < 0, loss: stocktakingRowDiff(row) !== null && Number(stocktakingRowDiff(row)) > 0 }">
                  {{ stocktakingRowDiff(row) == null ? '-' : stocktakingRowDiff(row) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="dialog-actions">
          <SpdButton label="关闭" tone="neutral" @click="stocktakingDetailOpen = false"></SpdButton>
          <SpdButton tone="primary" :disabled="stocktakingDetailSaving" @click="saveStocktakingDetail">
          <template #icon><Save :size="15" /></template>
          {{ stocktakingDetailSaving ? '保存中...' : '保存盘点数量' }}
        </SpdButton>
        </div>
      </section>
    </div>
    </Teleport>
  </section>
</template>

<style scoped>
/* ---------- 数字列：居中 + 等宽数字 ----------
   与统一列表标准一致：金额 / 数量类单元格与普通列一样**居中**，
   数字使用等宽字体便于纵向比对。
   具体样式统一由 styles/list/unified-list.css 的 .ulc-number 提供，
   这里不再重复定义，避免各页面出现不同的对齐口径。 */

:global(.el-dialog.quota-stock-codes-dialog) {
  width: min(560px, calc(100vw - 32px)) !important;
}

.quota-stock-code-list {
  max-height: min(45vh, 400px);
  overflow: auto;
  overflow-wrap: anywhere;
}

.quota-stock-code-list ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-inline-start: 24px;
}

/* =====================================================================
   库存管理 样式优化
   ---------------------------------------------------------------------
   范围：仅 inventory-management（库存汇总 / 定数包 / 唯一码 三个查询页签）。
   盘点管理、批次调价、库存交易流水沿用各自原有结构与样式，未做改动。

   与采购管理、收货验收保持同一套 md 设计语言：
   1. 页头改用 MdPageHeader（由 breadcrumb-line + detail-heading 替换）
   2. 工作区单卡片：页签 / 查询区 / 表格 三段式
   3. 表格 md 密度：表头 12px、表体 13px、sticky 表头、斑马纹
   4. 编码/数量/金额列统一等宽数字与右对齐，便于纵向比对

   注意优先级：.master-table 的 padding / font-size / color 由
   spd-override.css 以 !important 统一设定；本页规则统一加
   .inventory-page 前缀提高特指度，确保覆盖生效。
   ===================================================================== */

/* ---------- 工作区卡片 ---------- */
.inventory-page {
  padding: 20px 20px 24px;
}

.inventory-page .inventory-workspace {
  display: flex !important;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 14px 20px 16px;
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle)) !important;
  border-radius: var(--md-radius-md, 10px) !important;
  box-shadow: var(--spd-p-shadow-1);
}

/* ---------- 页签 ---------- */
.inventory-page .inventory-query-tabs {
  gap: 6px;
  padding: 0 0 10px;
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle));
}

.inventory-page .inventory-query-tabs button {
  min-height: 30px;
  padding: 0 14px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  border-radius: var(--md-radius, 6px);
}

/* ---------- 查询区 ----------
   筛选区已统一改用 SpdFilterBar 组件，
   栅格列数 / 换行规则 / 控件高度 / 操作区位置全部由
   styles/components/spd-filter-bar.css 的「标准规则」统一控制，
   因此本页不再另写一套查询区样式。 */

/* ---------- 列表滚动容器 ---------- */
.inventory-page .inventory-list-scroll {
  max-height: max(300px, calc(100dvh - 420px));
  overflow: auto;
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  background: var(--md-bg-card, var(--spd-p-bg));
  scrollbar-width: thin;
  scrollbar-color: var(--md-border, var(--spd-border-default)) transparent;
}

.inventory-page .inventory-list-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.inventory-page .inventory-list-scroll::-webkit-scrollbar-thumb {
  background: var(--md-border, var(--spd-border-default));
  border-radius: 4px;
}

/* ---------- 表格：md 密度 ---------- */
.inventory-page .inventory-table {
  min-width: 100%;
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base) !important;
}

.inventory-page .inventory-table thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  padding: 9px 12px !important;
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 700 !important;
  letter-spacing: 0.2px;
  white-space: nowrap;
  color: var(--md-text-heading) !important;
  background: var(--inventory-table-heading-bg) !important;
  border-bottom: 1px solid var(--md-border, var(--spd-border-default)) !important;
}

.inventory-page .inventory-table tbody td {
  padding: 8px 12px !important;
  font-size: var(--spd-font-size-base) !important;
  color: var(--md-text) !important;
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  vertical-align: middle;
}

.inventory-page .inventory-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted, var(--spd-p-row-stripe)) !important;
}

.inventory-page .inventory-table tbody tr:hover td {
  background: var(--md-primary-soft, var(--spd-p-primary-bg)) !important;
}

/* ---------- 盘点管理 ---------- */
.inventory-page .stocktaking-workspace {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inventory-page .stocktaking-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.inventory-page .stocktaking-title h3 {
  margin: 0;
  color: var(--md-text-heading);
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

/* 「新增盘点表」按钮：完全服从 SpdButton 统一几何，仅保留靠右布局。 */
.inventory-page .stocktaking-create-btn {
  margin-left: auto;
}

.inventory-page .stocktaking-hint {
  margin: 0;
  padding: 9px 12px;
  color: var(--md-text-secondary);
  background: var(--md-bg-muted, var(--spd-p-row-stripe));
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
}

.inventory-page .stocktaking-list-scroll {
  max-height: max(300px, calc(100dvh - 400px));
  overflow: auto;
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  scrollbar-width: thin;
  scrollbar-color: var(--md-border, var(--spd-border-default)) transparent;
}

.inventory-page .stocktaking-table {
  width: 100%;
  min-width: 980px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base) !important;
}

.inventory-page .stocktaking-table thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  padding: 9px 12px !important;
  color: var(--md-text-heading) !important;
  background: var(--inventory-table-heading-bg) !important;
  border-bottom: 1px solid var(--md-border, var(--spd-border-default)) !important;
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 700 !important;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.inventory-page .stocktaking-table tbody td {
  padding: 8px 12px !important;
  color: var(--md-text) !important;
  background: var(--md-bg-card, var(--spd-p-bg));
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  font-size: var(--spd-font-size-base) !important;
  vertical-align: middle;
}

.inventory-page .stocktaking-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted, var(--spd-p-row-stripe)) !important;
}

.inventory-page .stocktaking-table tbody tr:hover td {
  background: var(--md-primary-soft, var(--spd-p-primary-bg)) !important;
}

.inventory-page .stocktaking-no-cell {
  color: var(--md-primary) !important;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.inventory-page .stocktaking-diff-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.inventory-page .stocktaking-state-cell {
  height: 140px;
  color: var(--md-text-muted) !important;
  text-align: center;
}

.inventory-page .stocktaking-approve-btn {
  color: var(--md-primary) !important;
}

:deep(.hospital-pagination) {
  margin: 0;
  padding: 12px 4px 0;
  border-top: 1px solid var(--md-border-soft, var(--spd-border-subtle));
}

/* ---------- 编码列 ---------- */
.inventory-page .inventory-table .inventory-code-cell {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base) !important;
  color: var(--md-text-secondary, var(--spd-text-secondary)) !important;
  white-space: nowrap;
}

/* 商品名称：过长时省略，避免单行撑爆列宽 */
.inventory-page .inventory-table .inventory-product-cell {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--md-text-heading) !important;
  font-weight: 500;
}

/* ---------- 库存查询长文本 ---------- */
.inventory-page .inventory-table .inventory-wrap-cell {
  min-width: 140px;
  max-width: 220px;
  white-space: normal;
  overflow-wrap: anywhere;
  text-overflow: clip;
}

/* ---------- 数量 / 金额列 ---------- */
.inventory-page .inventory-table .inventory-qty-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--md-text-heading) !important;
  white-space: nowrap;
}

.inventory-page .inventory-table .inventory-money-cell {
  text-align: right;
  font-family: var(--spd-font-family-mono);
  font-variant-numeric: tabular-nums;
  color: var(--md-text) !important;
  white-space: nowrap;
}

/* ---------- 加载 / 空状态 ---------- */
.inventory-page .inventory-table .inventory-state-cell {
  padding: 48px 16px !important;
  text-align: center;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-base);
}

/* ---------- 提示条 ---------- */
.inventory-page .inline-message {
  margin: 0;
  padding: 10px 14px;
  border: 1px solid var(--spd-primary-border);
  border-radius: var(--md-radius, 6px);
  color: var(--spd-primary-text);
  background: var(--spd-primary-bg);
  font-size: var(--spd-font-size-sm);
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .inventory-page .inventory-workspace {
    padding: 12px 12px 14px;
  }

  .inventory-page .inventory-list-scroll {
    max-height: max(240px, calc(100dvh - 380px));
  }
}

/* =====================================================================
   批量调价 price-adjust-page
   ---------------------------------------------------------------------
   与结算对账 / 红冲管理同处"结算与财务"模块，统一使用渐变 md-header
   （与 UDI / 唯一码追溯 / 高值耗材页面一致），页面外壳对齐到
   其他 financial 页面的 padding / gap。
   ===================================================================== */
.price-adjust-page {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: none;
  gap: 14px !important;
  padding: 16px 20px 24px;
}

.price-adjust-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

.price-adjust-page > .hospital-catalog-panel {
  border-radius: var(--spd-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
}

/* 批次调价表单区已统一改用 SpdFilterBar，
   控件高度 / 圆角 / 边框走 --spd-p-field-* 令牌，不再单独写死 32px。 */

/* =====================================================================
   新增盘点表弹窗
   ---------------------------------------------------------------------
   把 dialog 改成宽屏 + 高度撑满视口、内部 flex 列布局，
   让「选择商品范围 + 库存明细表」能同时显示更多行。 */
.stocktaking-sheet-dialog {
  width: min(1380px, 98vw) !important;
  max-width: 1380px;
  max-height: min(94vh, 1080px);
  display: flex !important;
  flex-direction: column;
  border-radius: 12px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-dialog);
  overflow: hidden;
}

.stocktaking-sheet-dialog > header {
  flex: 0 0 auto;
  padding: 14px 20px;
  border-bottom: 1px solid var(--spd-p-bg-muted);
  background: linear-gradient(120deg, var(--spd-p-info-bg) 0%, var(--spd-p-bg) 65%, var(--spd-p-bg) 100%);
}

.stocktaking-sheet-dialog > header p {
  margin: 0 0 2px;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  letter-spacing: 0.5px;
  /* 辅助说明文字：中性次级文字（此前用信息语义蓝，发蓝且偏淡） */
  color: var(--spd-p-text-secondary);
  text-transform: none;
}

.stocktaking-sheet-dialog > header h3 {
  margin: 0;
  font-size: var(--spd-font-size-xl);
  font-weight: 600;
  color: var(--spd-p-text);
}

.stocktaking-sheet-dialog form {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
  padding: 14px 20px 0;
  overflow: hidden;
}

.stocktaking-sheet-dialog .supplier-form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  margin: 0;
}

.stocktaking-sheet-dialog .supplier-form-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
}

.stocktaking-sheet-dialog .supplier-form-grid label > span {
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  color: var(--spd-p-text-secondary);
}

.stocktaking-sheet-dialog .supplier-form-grid select {
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-base);
  /* 输入文字：深色（此前误用边框浅色作文字，几乎不可见） */
  color: var(--spd-p-text);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.stocktaking-sheet-dialog .supplier-form-grid select:focus {
  outline: 0;
  border-color: var(--spd-p-info);
  box-shadow: var(--spd-p-shadow-1);
}

.stocktaking-scope-picker {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--spd-p-border);
  border-radius: 10px;
  background: linear-gradient(180deg, var(--spd-p-info-bg) 0%, var(--spd-p-bg) 100%);
}

.stocktaking-scope-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.stocktaking-scope-head strong {
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--spd-p-info-strong);
}

.stocktaking-scope-head span {
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-secondary);
}

.stocktaking-scope-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stocktaking-scope-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--spd-p-info-border);
  border-radius: 999px;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-sm);
  /* 选项文字：深色（此前误用浅色底令牌作文字，对比度仅 1.12:1） */
  color: var(--spd-p-text);
  cursor: pointer;
  transition: all 0.15s ease;
}

.stocktaking-scope-option:hover {
  border-color: var(--spd-p-info);
  color: var(--spd-p-info-strong);
  background: var(--spd-p-info-bg);
}

.stocktaking-scope-option input[type="checkbox"] {
  width: 14px;
  height: 14px;
  accent-color: var(--spd-p-info);
  cursor: pointer;
}

.stocktaking-scope-selected {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-secondary);
}

.stocktaking-scope-selected > span:first-child {
  font-weight: 500;
  color: var(--spd-p-text-secondary);
}

.stocktaking-scope-selected em {
  font-style: normal;
  color: var(--spd-p-text-muted);
}

.stocktaking-scope-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  color: var(--spd-p-info-strong);
  background: var(--spd-p-border);
  border: 1px solid var(--spd-p-info-border);
  border-radius: 999px;
}

.stocktaking-detail-scroll {
  flex: 1 1 auto;
  min-height: 360px;
  max-height: 100%;
  overflow: auto;
  border: 1px solid var(--spd-p-border);
  border-radius: 10px;
  background: var(--spd-p-bg);
  scrollbar-width: thin;
  scrollbar-color: var(--spd-p-border-strong) transparent;
}

.stocktaking-detail-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.stocktaking-detail-scroll::-webkit-scrollbar-thumb {
  background: var(--spd-p-border-strong);
  border-radius: 4px;
}

.stocktaking-detail-scroll::-webkit-scrollbar-thumb:hover {
  background: var(--spd-p-text-muted);
}

.stocktaking-detail-table {
  width: 100%;
  min-width: 1200px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base) !important;
}

.stocktaking-detail-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 14px !important;
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 700 !important;
  /* 表头文字：深色强调文字（深字浅底，保证可读）。
     此前误用 --spd-p-bg-muted（浅灰）配浅蓝底，导致表头文字几乎不可见。 */
  color: var(--spd-p-text-strong) !important;
  background: linear-gradient(180deg, var(--spd-p-bg-subtle) 0%, var(--spd-p-primary-bg) 100%) !important;
  border-bottom: 1px solid var(--spd-p-border) !important;
  text-align: left;
  white-space: nowrap;
}

.stocktaking-detail-table tbody td {
  padding: 9px 14px !important;
  border-bottom: 1px solid var(--spd-p-text-strong) !important;
  color: var(--spd-p-text-secondary) !important;
  font-size: var(--spd-font-size-base) !important;
  background: var(--spd-p-bg);
  vertical-align: middle;
}

.stocktaking-detail-table tbody tr:nth-child(even) td {
  background: var(--spd-p-row-stripe) !important;
}

.stocktaking-detail-table tbody tr:hover td {
  background: var(--spd-p-info-bg) !important;
}

.stocktaking-detail-table .stocktaking-qty-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--spd-p-info-strong) !important;
}

.stocktaking-detail-table .stocktaking-code-cell {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base) !important;
  color: var(--spd-p-text-secondary) !important;
  white-space: nowrap;
}

.stocktaking-detail-table .approval-empty {
  padding: 56px 16px !important;
  text-align: center;
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-base);
}

.stocktaking-sheet-dialog .dialog-actions {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin: 0 -20px;
  padding: 12px 20px;
  border-top: 1px solid var(--spd-p-bg-muted);
  background: linear-gradient(180deg, var(--spd-p-bg) 0%, var(--spd-p-bg-muted) 100%);
}

.stocktaking-sheet-dialog .dialog-actions .btn {
  min-width: 100px;
  height: 34px;
  padding: 0 16px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  border-radius: 6px;
}

.stocktaking-sheet-dialog .dialog-actions .btn-primary {
  background: linear-gradient(135deg, var(--spd-p-info) 0%, var(--spd-p-info) 100%);
  border-color: var(--spd-p-info);
  box-shadow: var(--spd-p-shadow-1);
}

.stocktaking-sheet-dialog .dialog-actions .btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--spd-p-info-strong) 0%, var(--spd-p-info) 100%);
  box-shadow: var(--spd-p-shadow-2);
}

@media (max-width: 1280px) {
  .stocktaking-sheet-dialog {
    width: min(96vw, 1280px) !important;
  }
}

@media (max-width: 900px) {
  .stocktaking-sheet-dialog {
    width: 96vw !important;
    max-height: 92vh;
  }
  .stocktaking-detail-scroll {
    min-height: 260px;
  }
}
</style>
