<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElOption, ElSelect } from 'element-plus'
import { ChevronDown, Columns3, Download, FileSpreadsheet, RefreshCw, Search } from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import type { SpdStatusChipTone } from '../../components/common/SpdStatusChip.vue'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import {
  exportCentralizedProcurementProgressExcel,
  exportInventoryMovementSummaryExcel,
  exportSupplierDeliveryLedgerExcel,
  fetchCentralizedProcurementProgress,
  fetchInventoryMovementSummary,
  fetchSupplierDeliveryLedger,
  fetchSupplierDeliveryLedgerOptions,
  recordRegulatoryPdfExport,
  type CentralizedProcurementRow,
  type InventoryMovementRow,
  type SupplierDeliveryRow,
  type SupplierLedgerOption,
} from '../../api/reportCenter'

type ReportType = 'supplier' | 'centralized' | 'inventory'
type ReportRow = SupplierDeliveryRow | CentralizedProcurementRow | InventoryMovementRow

const route = useRoute()
const today = new Date()
const monthAgo = new Date(today)
monthAgo.setDate(today.getDate() - 29)

const reportType = computed(() => (route.meta.reportType || 'supplier') as ReportType)
const configs = {
  supplier: {
    code: 'supplier-delivery-ledger',
    title: '供应商供货明细台账',
    crumb: '运营与决策 / 报表中心 / 供应链合规',
  },
  centralized: {
    code: 'centralized-procurement-progress',
    title: '集采执行进度报表',
    crumb: '运营与决策 / 报表中心 / 集采与医保',
  },
  inventory: {
    code: 'inventory-movement-summary',
    title: '全院物资进销存汇总表',
    crumb: '运营与决策 / 报表中心 / 进销存汇总',
  },
} as const
const config = computed(() => configs[reportType.value])
const query = reactive({
  dateFrom: inputDate(monthAgo), dateTo: inputDate(today), supplierId: undefined as number | undefined,
  supplierDepartment: undefined as number | string | undefined, category: '', department: '',
  centralizedStatus: 'ALL', batch: '', warehouse: '', materialType: 'ALL', page: 1, size: 20,
})
const appliedQuery = ref({ ...query })
let reportRequestVersion = 0
const rows = ref<ReportRow[]>([])
const supplierRows = computed(() => rows.value as SupplierDeliveryRow[])
const centralizedRows = computed(() => rows.value as CentralizedProcurementRow[])
const inventoryRows = computed(() => rows.value as InventoryMovementRow[])
const total = ref(0)
const summary = ref<Record<string, number>>({})
const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const supplierOptions = ref<SupplierLedgerOption[]>([])
const departmentOptions = ref<SupplierLedgerOption[]>([])
const supplierOptionsLoading = ref(false)
const supplierOptionsError = ref('')

/* ===== 列表列配置 =====
   ---------------------------------------------------------------------
   监管报表页按报表类型渲染三张表（供货台账 / 集采执行 / 进销存汇总），
   各自维护一套列定义与持久化 key，避免切换报表时列设置互相污染。

   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 使用默认的 'fixed'：每列宽度互相独立，
       拖拽某一列不会因为容器宽度变化而影响其它列；
     - 列宽与列显隐持久化到 localStorage。
   ===================================================================== */
const SUPPLIER_LEDGER_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'index', label: '序号', width: 64, visible: true, weight: 1, minWidth: 56, maxWidth: 90 },
  { key: 'supplyDate', label: '供货日期', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 160 },
  { key: 'supplierName', label: '供应商', width: 170, visible: true, flexible: true, idealWidth: 210, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'manufacturerName', label: '生产厂家', width: 160, visible: true, flexible: true, idealWidth: 200, weight: 6, minWidth: 120, maxWidth: 300 },
  { key: 'productCode', label: '物资编码', width: 130, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'medicalInsuranceCode', label: '医保编码', width: 130, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'productName', label: '物资名称', width: 190, visible: true, flexible: true, idealWidth: 230, weight: 8, minWidth: 140, maxWidth: 360 },
  { key: 'specModel', label: '规格', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 180 },
  { key: 'registrationNo', label: '注册证号', width: 160, visible: true, weight: 5, minWidth: 130, maxWidth: 240 },
  { key: 'unitPrice', label: '采购单价', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'supplyQuantity', label: '供货数量', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'supplyAmount', label: '供货金额', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 180 },
  { key: 'orderNo', label: '订单号', width: 170, visible: true, weight: 6, minWidth: 140, maxWidth: 260 },
  { key: 'contractNo', label: '合同号', width: 150, visible: true, weight: 5, minWidth: 120, maxWidth: 230 },
  { key: 'acceptanceDepartment', label: '验收科室', width: 130, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'acceptanceUser', label: '验收人', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 170 },
  { key: 'acceptanceTime', label: '验收时间', width: 160, visible: true, weight: 5, minWidth: 130, maxWidth: 240, fill: true }
]

const CENTRALIZED_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'batch', label: '集采批次', width: 200, visible: true, flexible: true, idealWidth: 240, weight: 7, minWidth: 150, maxWidth: 340 },
  { key: 'productName', label: '物资名称', width: 200, visible: true, flexible: true, idealWidth: 240, weight: 9, minWidth: 150, maxWidth: 380 },
  { key: 'specModel', label: '规格', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'selectedManufacturer', label: '中选厂家', width: 170, visible: true, flexible: true, idealWidth: 210, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'selectedPrice', label: '中选价格', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 180 },
  { key: 'annualTargetQuantity', label: '年度任务量', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 180 },
  { key: 'periodPurchaseQuantity', label: '本期采购量', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 180 },
  { key: 'cumulativePurchaseQuantity', label: '累计采购量', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 180 },
  { key: 'completionRate', label: '完成率', width: 200, visible: true, weight: 5, minWidth: 160, maxWidth: 280 },
  { key: 'selectedFlag', label: '中选标识', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 170 },
  { key: 'departmentConsumptionQuantity', label: '科室消耗量', width: 130, visible: true, weight: 4, minWidth: 100, maxWidth: 190 },
  { key: 'incompleteReason', label: '未完成原因', width: 170, visible: true, flexible: true, idealWidth: 210, weight: 6, minWidth: 130, maxWidth: 320, fill: true }
]

const INVENTORY_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'productCode', label: '物资编码', width: 130, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'productName', label: '物资名称', width: 190, visible: true, flexible: true, idealWidth: 230, weight: 9, minWidth: 140, maxWidth: 360 },
  { key: 'specModel', label: '规格', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 180 },
  { key: 'manufacturerName', label: '生产厂家', width: 160, visible: true, flexible: true, idealWidth: 200, weight: 6, minWidth: 120, maxWidth: 300 },
  { key: 'distributorName', label: '配送商', width: 160, visible: true, flexible: true, idealWidth: 200, weight: 6, minWidth: 120, maxWidth: 300 },
  { key: 'openingQuantity', label: '期初库存', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'inboundQuantity', label: '本期入库', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'returnQuantity', label: '本期退库', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'requisitionQuantity', label: '本期领用', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'consumptionQuantity', label: '本期消耗', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'scrapQuantity', label: '本期报废', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'closingQuantity', label: '期末库存', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'unitPrice', label: '库存单价', width: 110, visible: true, weight: 3, minWidth: 96, maxWidth: 170 },
  { key: 'inventoryAmount', label: '库存金额', width: 130, visible: true, weight: 4, minWidth: 110, maxWidth: 190 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 5, minWidth: 120, maxWidth: 240 },
  { key: 'materialAttribute', label: '物资属性', width: 130, visible: true, weight: 4, minWidth: 100, maxWidth: 200, fill: true }
]

const supplierLedgerCols = useListColumns({
  storageKey: 'spd-regulatory-supplier-ledger-cols-v1',
  defaults: SUPPLIER_LEDGER_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列。
  fitMode: 'hybrid',
  reservedWidth: 0
})
const supplierLedgerColBinding = (key: string) => supplierLedgerCols.columnBinding(key)

const centralizedCols = useListColumns({
  storageKey: 'spd-regulatory-centralized-cols-v1',
  defaults: CENTRALIZED_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「物资名称 / 中选厂家 / 未完成原因」等长文本列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})
const centralizedColBinding = (key: string) => centralizedCols.columnBinding(key)

const inventoryCols = useListColumns({
  storageKey: 'spd-regulatory-inventory-cols-v1',
  defaults: INVENTORY_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「物资名称 / 生产厂家 / 配送商」等长文本列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})
const inventoryColBinding = (key: string) => inventoryCols.columnBinding(key)

/** 当前报表类型对应的列配置（三种报表各自独立） */
const activeCols = computed(() => {
  if (reportType.value === 'supplier') return supplierLedgerCols
  if (reportType.value === 'centralized') return centralizedCols
  return inventoryCols
})

const isReportColVisible = (key: string) => activeCols.value.isColumnVisible(key)
const reportColBinding = (key: string) => activeCols.value.columnBinding(key)

const regulatoryTableRef = ref<any>(null)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const regulatoryActionFix = useActionColumnFix()
const regulatoryActionFixed = computed(() => regulatoryActionFix.actionFixedBinding.value)

watch(
  [regulatoryTableRef],
  () => {
    regulatoryActionFix.observe(regulatoryTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const regulatoryColSettingsAnchorRef = ref<HTMLElement | null>(null)
const regulatoryColSettingsPopRef = ref<HTMLElement | null>(null)
const regulatoryColSettingsPop = useColSettingsPopover({
  open: computed({
    get: () => activeCols.value.settingsOpen.value,
    set: (value) => { activeCols.value.settingsOpen.value = value }
  }),
  anchorRef: regulatoryColSettingsAnchorRef,
  popRef: regulatoryColSettingsPopRef
})

function inputDate(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
}
/**
 * 中选标识 → 全站通用状态条色调（SpdStatusChip 的 tone）。
 * 后端返回「中选」/「非中选」；中选用 success，非中选保持中性灰。
 */
function selectedFlagTone(value: unknown): SpdStatusChipTone {
  const text = String(value ?? '').trim()
  if (!text) return 'neutral'
  if (text === '中选' || text === '是' || text === 'true' || text === '1') return 'success'
  if (text === '非中选' || text === '否' || text === 'false' || text === '0') return 'neutral'
  return 'neutral'
}
function number(value: unknown) { return Number(value || 0) }
function quantity(value: unknown) { return number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 }) }
function amount(value: unknown) { return number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function reportParams() {
  const filters = appliedQuery.value
  const base = { dateFrom: filters.dateFrom, dateTo: filters.dateTo, page: query.page, size: query.size }
  if (reportType.value === 'supplier') return {
    ...base,
    supplierId: filters.supplierId || undefined,
    departmentId: typeof filters.supplierDepartment === 'number' ? filters.supplierDepartment : undefined,
    department: typeof filters.supplierDepartment === 'string' ? filters.supplierDepartment : undefined,
    category: filters.category,
    centralizedStatus: filters.centralizedStatus as 'ALL' | 'SELECTED' | 'NON_SELECTED'
  }
  if (reportType.value === 'centralized') return { ...base, batch: filters.batch, category: filters.category, department: filters.department }
  return { ...base, warehouse: filters.warehouse, category: filters.category, materialType: filters.materialType as 'ALL' | 'HIGH' | 'LOW' | 'REAGENT' }
}
function readableError(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message && reason.message !== 'Request failed' ? reason.message : fallback
}

async function loadSupplierOptions() {
  supplierOptionsLoading.value = true
  supplierOptionsError.value = ''
  try {
    const options = await fetchSupplierDeliveryLedgerOptions()
    supplierOptions.value = options.suppliers
    departmentOptions.value = options.departments
  } catch (reason) {
    supplierOptionsError.value = readableError(reason, '供应商或科室选项加载失败')
  } finally {
    supplierOptionsLoading.value = false
  }
}

async function loadReport() {
  const request = ++reportRequestVersion
  loading.value = true
  error.value = ''
  try {
    const params = reportParams()
    const result = reportType.value === 'supplier'
      ? await fetchSupplierDeliveryLedger(params as Parameters<typeof fetchSupplierDeliveryLedger>[0])
      : reportType.value === 'centralized'
        ? await fetchCentralizedProcurementProgress(params as Parameters<typeof fetchCentralizedProcurementProgress>[0])
        : await fetchInventoryMovementSummary(params as Parameters<typeof fetchInventoryMovementSummary>[0])
    if (request !== reportRequestVersion) return
    rows.value = result.rows
    total.value = result.total
    summary.value = Object.fromEntries(Object.entries(result.summary || {}).map(([key, value]) => [key, number(value)]))
  } catch (reason) {
    if (request !== reportRequestVersion) return
    rows.value = []; total.value = 0; summary.value = {}
    error.value = readableError(reason, `${config.value.title}加载失败，请稍后重试`)
    ElMessage.error(error.value)
  } finally {
    if (request === reportRequestVersion) loading.value = false
  }
}

function applyQuery() {
  query.page = 1
  appliedQuery.value = { ...query }
  void loadReport()
}

function resetQuery() {
  Object.assign(query, { dateFrom: inputDate(monthAgo), dateTo: inputDate(today), supplierId: undefined,
    supplierDepartment: undefined, category: '', department: '', centralizedStatus: 'ALL', batch: '',
    warehouse: '', materialType: 'ALL', page: 1 })
  applyQuery()
}
function changePage(page: number) {
  if (page === query.page) return
  query.page = page
  void loadReport()
}
function changeSize(size: number) {
  query.size = size
  query.page = 1
  void loadReport()
}
async function exportExcel() {
  exporting.value = true
  error.value = ''
  try {
    const params = { ...reportParams(), page: undefined, size: undefined }
    const blob = reportType.value === 'supplier'
      ? await exportSupplierDeliveryLedgerExcel(params as Parameters<typeof exportSupplierDeliveryLedgerExcel>[0])
      : reportType.value === 'centralized'
        ? await exportCentralizedProcurementProgressExcel(params as Parameters<typeof exportCentralizedProcurementProgressExcel>[0])
        : await exportInventoryMovementSummaryExcel(params as Parameters<typeof exportInventoryMovementSummaryExcel>[0])
    const href = URL.createObjectURL(blob)
    const link = document.createElement('a'); link.href = href; link.download = `${config.value.title}_${params.dateFrom}_${params.dateTo}.xlsx`; link.click()
    URL.revokeObjectURL(href)
    ElMessage.success('Excel 已开始下载')
  } catch (reason) {
    error.value = readableError(reason, 'Excel 导出失败，请稍后重试')
    ElMessage.error(error.value)
  } finally { exporting.value = false }
}
async function exportPdf() {
  try { await recordRegulatoryPdfExport(config.value.code); window.print() }
  catch (reason) { error.value = readableError(reason, 'PDF 导出准备失败，请稍后重试'); ElMessage.error(error.value) }
}

watch(reportType, () => {
  resetQuery()
  if (reportType.value === 'supplier') void loadSupplierOptions()
})
onMounted(() => {
  void loadReport()
  if (reportType.value === 'supplier') void loadSupplierOptions()
})
</script>

<template>
  <main class="report-page regulatory-page spd-unified-list">
    <MdPageHeader
      :crumb="config.crumb"
      :title="config.title"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadReport"
    />

    <section class="report-toolbar">
      <div class="report-filters">
        <label><span>统计时间</span><div class="date-range"><input v-model="query.dateFrom" type="date" /><i>至</i><input v-model="query.dateTo" type="date" /></div></label>
        <template v-if="reportType === 'supplier'">
          <label><span>供应商</span>
            <ElSelect v-model="query.supplierId" aria-label="供应商" clearable filterable
              :loading="supplierOptionsLoading" placeholder="全部供应商">
              <ElOption v-for="supplier in supplierOptions" :key="supplier.id" :value="supplier.id"
                :label="`${supplier.name} · ${supplier.code}`" />
            </ElSelect>
          </label>
          <label><span>物资分类</span><input v-model.trim="query.category" type="search" placeholder="一级 / 二级 / 三级分类" @keyup.enter="applyQuery" /></label>
          <label><span>科室</span>
            <ElSelect v-model="query.supplierDepartment" aria-label="科室" clearable filterable allow-create default-first-option
              :loading="supplierOptionsLoading" placeholder="请选择科室或输入库房名称">
              <ElOption v-for="department in departmentOptions" :key="department.id" :value="department.id"
                :label="`${department.name} · ${department.code}`" />
            </ElSelect>
          </label>
          <label><span>集采中选状态</span><select v-model="query.centralizedStatus"><option value="ALL">全部</option><option value="SELECTED">中选</option><option value="NON_SELECTED">非中选</option></select></label>
        </template>
        <template v-else-if="reportType === 'centralized'">
          <label><span>集采批次</span><input v-model.trim="query.batch" type="search" placeholder="批次名称 / 编码" @keyup.enter="applyQuery" /></label>
          <label><span>物资分类</span><input v-model.trim="query.category" type="search" placeholder="全部分类" @keyup.enter="applyQuery" /></label>
          <label><span>科室</span><input v-model.trim="query.department" type="search" placeholder="消耗科室" @keyup.enter="applyQuery" /></label>
        </template>
        <template v-else>
          <label><span>库房</span><input v-model.trim="query.warehouse" type="search" placeholder="全部库房" @keyup.enter="applyQuery" /></label>
          <label><span>物资分类</span><input v-model.trim="query.category" type="search" placeholder="全部分类" @keyup.enter="applyQuery" /></label>
          <label><span>耗材类型</span><select v-model="query.materialType"><option value="ALL">全部</option><option value="HIGH">高值耗材</option><option value="LOW">低值耗材</option><option value="REAGENT">试剂</option></select></label>
        </template>
      </div>
      <div class="report-actions">
        <SpdButton label="查询" tone="primary" :disabled="loading" @click="applyQuery">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
        <SpdButton label="重置" tone="neutral" :disabled="loading" @click="resetQuery">
          <template #icon><RefreshCw :size="16" /></template>
        </SpdButton>
        <SpdButton label="导出 Excel" tone="primary" :disabled="loading || exporting" @click="exportExcel">
          <template #icon><FileSpreadsheet :size="16" /></template>
        </SpdButton>
        <SpdButton label="导出 PDF" tone="primary" :disabled="loading" @click="exportPdf">
          <template #icon><Download :size="16" /></template>
        </SpdButton>
      </div>
    </section>

    <StatusMessage :message="error || (reportType === 'supplier' ? supplierOptionsError : '')" tone="error" role="alert" />

    <section class="report-summary" aria-label="汇总指标">
      <template v-if="reportType === 'supplier'">
        <span>明细 <strong>{{ quantity(summary.totalCount) }}</strong> 条</span>
        <span>供应商 <strong>{{ quantity(summary.supplierCount) }}</strong> 家</span>
        <span>供货数量 <strong>{{ quantity(summary.supplyQuantity) }}</strong></span>
        <span>供货金额 <strong class="primary-text">¥{{ amount(summary.supplyAmount) }}</strong></span>
      </template>
      <template v-else-if="reportType === 'centralized'">
        <span>任务物资 <strong>{{ quantity(summary.taskCount) }}</strong> 项</span>
        <span>年度任务量 <strong>{{ quantity(summary.annualTargetQuantity) }}</strong></span>
        <span>累计采购量 <strong>{{ quantity(summary.cumulativePurchaseQuantity) }}</strong></span>
        <span>综合完成率 <strong class="primary-text">{{ quantity(summary.completionRate) }}%</strong></span>
        <span>未完成 <strong class="danger-text">{{ quantity(summary.incompleteCount) }}</strong> 项</span>
      </template>
      <template v-else>
        <span>物资库房组合 <strong>{{ quantity(summary.itemCount) }}</strong> 项</span>
        <span>期初金额 <strong>¥{{ amount(summary.openingAmount) }}</strong></span>
        <span>入库金额 <strong class="primary-text">¥{{ amount(summary.inboundAmount) }}</strong></span>
        <span>消耗金额 <strong>¥{{ amount(summary.consumptionAmount) }}</strong></span>
        <span>期末金额 <strong class="primary-text">¥{{ amount(summary.closingAmount) }}</strong></span>
      </template>
    </section>

    <MdDataTableCard :loading="loading">
      <template #toolbar>
        <!-- 查询 / 重置 / 导出 按钮保留在页面上方工具区，
             表格工具条只承载列设置，避免同一动作出现两处。 -->
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="regulatoryColSettingsAnchorRef"
           
           
            @click="activeCols.settingsOpen.value = !activeCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="activeCols.settingsOpen.value"
ref="regulatoryColSettingsPopRef"
:class="{ 'is-align-right': regulatoryColSettingsPop.align.value === 'right', 'is-align-up': regulatoryColSettingsPop.vertical.value === 'up' }"
:style="regulatoryColSettingsPop.popStyle.value"
:columns="activeCols.columns.value"
:visible-column-count="activeCols.visibleColumnCount.value"
@toggle="activeCols.setColumnVisible"
@move="activeCols.moveColumn"
@reset="activeCols.resetColumns()"
@confirm="activeCols.settingsOpen.value = false"
          />
        </div>
      </template>
      <template #meta-extra>
        <span class="md-chip primary">共 {{ total.toLocaleString('zh-CN') }} 条</span>
      </template>

      <div class="ulc-table-wrap">
        <el-table
          v-if="reportType === 'supplier'"
          ref="regulatoryTableRef"
          v-loading="loading"
          :data="supplierRows"
          stripe
          border
          row-key="orderNo"
          class="ulc-table is-sticky"
          max-height="560"
          style="width: 100%"
          :empty-text="loading ? '正在加载供货明细…' : '当前筛选条件下暂无供货数据'"
          @header-dragend="(width: number, oldWidth: number, column: any) => supplierLedgerCols.handleHeaderDragEnd(width, oldWidth, column)"
        >
          <el-table-column
            column-key="index"
            v-if="isReportColVisible('index')"
            type="index"
            align="center"
            v-bind="reportColBinding('index')"
            :index="(i: number) => (query.page - 1) * query.size + i + 1"
          >
            <template #header>
              <span class="ulc-th-text">序号</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="supplyDate"
            v-if="isReportColVisible('supplyDate')"
            prop="supplyDate"
            v-bind="reportColBinding('supplyDate')"
          >
            <template #header>
              <span class="ulc-th-text">供货日期</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="supplierName"
            v-if="isReportColVisible('supplierName')"
            prop="supplierName"
            v-bind="reportColBinding('supplierName')"
          >
            <template #header>
              <span class="ulc-th-text">供应商</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="manufacturerName"
            v-if="isReportColVisible('manufacturerName')"
            prop="manufacturerName"
            v-bind="reportColBinding('manufacturerName')"
          >
            <template #header>
              <span class="ulc-th-text">生产厂家</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="productCode"
            v-if="isReportColVisible('productCode')"
            prop="productCode"
            v-bind="reportColBinding('productCode')"
          >
            <template #header>
              <span class="ulc-th-text">物资编码</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="medicalInsuranceCode"
            v-if="isReportColVisible('medicalInsuranceCode')"
            prop="medicalInsuranceCode"
            v-bind="reportColBinding('medicalInsuranceCode')"
          >
            <template #header>
              <span class="ulc-th-text">医保编码</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="productName"
            v-if="isReportColVisible('productName')"
            prop="productName"
            v-bind="reportColBinding('productName')"
          >
            <template #header>
              <span class="ulc-th-text">物资名称</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="specModel"
            v-if="isReportColVisible('specModel')"
            prop="specModel"
            v-bind="reportColBinding('specModel')"
          >
            <template #header>
              <span class="ulc-th-text">规格</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="registrationNo"
            v-if="isReportColVisible('registrationNo')"
            prop="registrationNo"
            v-bind="reportColBinding('registrationNo')"
          >
            <template #header>
              <span class="ulc-th-text">注册证号</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="unitPrice"
            v-if="isReportColVisible('unitPrice')"
            prop="unitPrice"
            v-bind="reportColBinding('unitPrice')"
          >
            <template #header>
              <span class="ulc-th-text">采购单价</span>
            </template>
            <template #default="{ row }">¥{{ amount(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column
            column-key="supplyQuantity"
            v-if="isReportColVisible('supplyQuantity')"
            prop="supplyQuantity"
            v-bind="reportColBinding('supplyQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">供货数量</span>
            </template>
            <template #default="{ row }">{{ quantity(row.supplyQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="supplyAmount"
            v-if="isReportColVisible('supplyAmount')"
            prop="supplyAmount"
            v-bind="reportColBinding('supplyAmount')"
          >
            <template #header>
              <span class="ulc-th-text">供货金额</span>
            </template>
            <template #default="{ row }">¥{{ amount(row.supplyAmount) }}</template>
          </el-table-column>
          <el-table-column
            column-key="orderNo"
            v-if="isReportColVisible('orderNo')"
            prop="orderNo"
            v-bind="reportColBinding('orderNo')"
          >
            <template #header>
              <span class="ulc-th-text">订单号</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="contractNo"
            v-if="isReportColVisible('contractNo')"
            prop="contractNo"
            v-bind="reportColBinding('contractNo')"
          >
            <template #header>
              <span class="ulc-th-text">合同号</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="acceptanceDepartment"
            v-if="isReportColVisible('acceptanceDepartment')"
            prop="acceptanceDepartment"
            v-bind="reportColBinding('acceptanceDepartment')"
          >
            <template #header>
              <span class="ulc-th-text">验收科室</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="acceptanceUser"
            v-if="isReportColVisible('acceptanceUser')"
            prop="acceptanceUser"
            v-bind="reportColBinding('acceptanceUser')"
          >
            <template #header>
              <span class="ulc-th-text">验收人</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="acceptanceTime"
            v-if="isReportColVisible('acceptanceTime')"
            prop="acceptanceTime"
            :fixed="regulatoryActionFixed"
            v-bind="reportColBinding('acceptanceTime')"
          >
            <template #header>
              <span class="ulc-th-text">验收时间</span>
            </template>
          </el-table-column>
        </el-table>

        <el-table
          v-else-if="reportType === 'centralized'"
          ref="regulatoryTableRef"
          v-loading="loading"
          :data="centralizedRows"
          stripe
          border
          row-key="productCode"
          class="ulc-table is-sticky"
          max-height="560"
          style="width: 100%"
          :empty-text="loading ? '正在计算集采执行进度…' : '暂无集采任务或中选物资'"
          @header-dragend="(width: number, oldWidth: number, column: any) => centralizedCols.handleHeaderDragEnd(width, oldWidth, column)"
        >
          <el-table-column
            column-key="batch"
            v-if="isReportColVisible('batch')"
            v-bind="reportColBinding('batch')"
          >
            <template #header>
              <span class="ulc-th-text">集采批次</span>
            </template>
            <template #default="{ row }">
              <div class="ulc-cell-stack">
                <span class="ulc-strong ulc-wrap-text">{{ row.batchName }}</span>
                <span class="ulc-sub ulc-wrap-text is-sub">{{ row.batchCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            column-key="productName"
            v-if="isReportColVisible('productName')"
            prop="productName"
            v-bind="reportColBinding('productName')"
          >
            <template #header>
              <span class="ulc-th-text">物资名称</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="specModel"
            v-if="isReportColVisible('specModel')"
            prop="specModel"
            v-bind="reportColBinding('specModel')"
          >
            <template #header>
              <span class="ulc-th-text">规格</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="selectedManufacturer"
            v-if="isReportColVisible('selectedManufacturer')"
            prop="selectedManufacturer"
            v-bind="reportColBinding('selectedManufacturer')"
          >
            <template #header>
              <span class="ulc-th-text">中选厂家</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="selectedPrice"
            v-if="isReportColVisible('selectedPrice')"
            prop="selectedPrice"
            v-bind="reportColBinding('selectedPrice')"
          >
            <template #header>
              <span class="ulc-th-text">中选价格</span>
            </template>
            <template #default="{ row }">¥{{ amount(row.selectedPrice) }}</template>
          </el-table-column>
          <el-table-column
            column-key="annualTargetQuantity"
            v-if="isReportColVisible('annualTargetQuantity')"
            prop="annualTargetQuantity"
            v-bind="reportColBinding('annualTargetQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">年度任务量</span>
            </template>
            <template #default="{ row }">{{ quantity(row.annualTargetQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="periodPurchaseQuantity"
            v-if="isReportColVisible('periodPurchaseQuantity')"
            prop="periodPurchaseQuantity"
            v-bind="reportColBinding('periodPurchaseQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">本期采购量</span>
            </template>
            <template #default="{ row }">{{ quantity(row.periodPurchaseQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="cumulativePurchaseQuantity"
            v-if="isReportColVisible('cumulativePurchaseQuantity')"
            prop="cumulativePurchaseQuantity"
            v-bind="reportColBinding('cumulativePurchaseQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">累计采购量</span>
            </template>
            <template #default="{ row }">{{ quantity(row.cumulativePurchaseQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="completionRate"
            v-if="isReportColVisible('completionRate')"
            v-bind="reportColBinding('completionRate')"
          >
            <template #header>
              <span class="ulc-th-text">完成率</span>
            </template>
            <template #default="{ row }">
              <div class="progress-cell">
                <span><i :style="{ width: `${Math.min(100, number(row.completionRate))}%` }"></i></span>
                <strong>{{ quantity(row.completionRate) }}%</strong>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            column-key="selectedFlag"
            v-if="isReportColVisible('selectedFlag')"
            prop="selectedFlag"
            v-bind="reportColBinding('selectedFlag')"
          >
            <template #header>
              <span class="ulc-th-text">中选标识</span>
            </template>
            <template #default="{ row }"><SpdStatusChip :tone="selectedFlagTone(row.selectedFlag)">{{ row.selectedFlag }}</SpdStatusChip></template>
          </el-table-column>
          <el-table-column
            column-key="departmentConsumptionQuantity"
            v-if="isReportColVisible('departmentConsumptionQuantity')"
            prop="departmentConsumptionQuantity"
            v-bind="reportColBinding('departmentConsumptionQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">科室消耗量</span>
            </template>
            <template #default="{ row }">{{ quantity(row.departmentConsumptionQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="incompleteReason"
            v-if="isReportColVisible('incompleteReason')"
            prop="incompleteReason"
            :fixed="regulatoryActionFixed"
            v-bind="reportColBinding('incompleteReason')"
          >
            <template #header>
              <span class="ulc-th-text">未完成原因</span>
            </template>
            <template #default="{ row }"><span :class="{ 'warning-text': row.incompleteReason !== '-' }">{{ row.incompleteReason }}</span></template>
          </el-table-column>
        </el-table>

        <el-table
          v-else
          ref="regulatoryTableRef"
          v-loading="loading"
          :data="inventoryRows"
          stripe
          border
          row-key="productCode"
          class="ulc-table is-sticky"
          max-height="560"
          style="width: 100%"
          :empty-text="loading ? '正在读取每日进销存快照…' : '当前筛选条件下暂无每日进销存快照'"
          @header-dragend="(width: number, oldWidth: number, column: any) => inventoryCols.handleHeaderDragEnd(width, oldWidth, column)"
        >
          <el-table-column
            column-key="productCode"
            v-if="isReportColVisible('productCode')"
            prop="productCode"
            v-bind="reportColBinding('productCode')"
          >
            <template #header>
              <span class="ulc-th-text">物资编码</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="productName"
            v-if="isReportColVisible('productName')"
            prop="productName"
            v-bind="reportColBinding('productName')"
          >
            <template #header>
              <span class="ulc-th-text">物资名称</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="specModel"
            v-if="isReportColVisible('specModel')"
            prop="specModel"
            v-bind="reportColBinding('specModel')"
          >
            <template #header>
              <span class="ulc-th-text">规格</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="manufacturerName"
            v-if="isReportColVisible('manufacturerName')"
            prop="manufacturerName"
            v-bind="reportColBinding('manufacturerName')"
          >
            <template #header>
              <span class="ulc-th-text">生产厂家</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="distributorName"
            v-if="isReportColVisible('distributorName')"
            prop="distributorName"
            v-bind="reportColBinding('distributorName')"
          >
            <template #header>
              <span class="ulc-th-text">配送商</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="openingQuantity"
            v-if="isReportColVisible('openingQuantity')"
            prop="openingQuantity"
            v-bind="reportColBinding('openingQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">期初库存</span>
            </template>
            <template #default="{ row }">{{ quantity(row.openingQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="inboundQuantity"
            v-if="isReportColVisible('inboundQuantity')"
            prop="inboundQuantity"
            v-bind="reportColBinding('inboundQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">本期入库</span>
            </template>
            <template #default="{ row }"><span class="positive-text">{{ quantity(row.inboundQuantity) }}</span></template>
          </el-table-column>
          <el-table-column
            column-key="returnQuantity"
            v-if="isReportColVisible('returnQuantity')"
            prop="returnQuantity"
            v-bind="reportColBinding('returnQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">本期退库</span>
            </template>
            <template #default="{ row }">{{ quantity(row.returnQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="requisitionQuantity"
            v-if="isReportColVisible('requisitionQuantity')"
            prop="requisitionQuantity"
            v-bind="reportColBinding('requisitionQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">本期领用</span>
            </template>
            <template #default="{ row }">{{ quantity(row.requisitionQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="consumptionQuantity"
            v-if="isReportColVisible('consumptionQuantity')"
            prop="consumptionQuantity"
            v-bind="reportColBinding('consumptionQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">本期消耗</span>
            </template>
            <template #default="{ row }">{{ quantity(row.consumptionQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="scrapQuantity"
            v-if="isReportColVisible('scrapQuantity')"
            prop="scrapQuantity"
            v-bind="reportColBinding('scrapQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">本期报废</span>
            </template>
            <template #default="{ row }"><span class="danger-text">{{ quantity(row.scrapQuantity) }}</span></template>
          </el-table-column>
          <el-table-column
            column-key="closingQuantity"
            v-if="isReportColVisible('closingQuantity')"
            prop="closingQuantity"
            v-bind="reportColBinding('closingQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">期末库存</span>
            </template>
            <template #default="{ row }"><strong class="strong-number">{{ quantity(row.closingQuantity) }}</strong></template>
          </el-table-column>
          <el-table-column
            column-key="unitPrice"
            v-if="isReportColVisible('unitPrice')"
            prop="unitPrice"
            v-bind="reportColBinding('unitPrice')"
          >
            <template #header>
              <span class="ulc-th-text">库存单价</span>
            </template>
            <template #default="{ row }">¥{{ amount(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column
            column-key="inventoryAmount"
            v-if="isReportColVisible('inventoryAmount')"
            prop="inventoryAmount"
            v-bind="reportColBinding('inventoryAmount')"
          >
            <template #header>
              <span class="ulc-th-text">库存金额</span>
            </template>
            <template #default="{ row }"><strong class="strong-number">¥{{ amount(row.inventoryAmount) }}</strong></template>
          </el-table-column>
          <el-table-column
            column-key="warehouseName"
            v-if="isReportColVisible('warehouseName')"
            prop="warehouseName"
            v-bind="reportColBinding('warehouseName')"
          >
            <template #header>
              <span class="ulc-th-text">库房</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="materialAttribute"
            v-if="isReportColVisible('materialAttribute')"
            prop="materialAttribute"
            :fixed="regulatoryActionFixed"
            v-bind="reportColBinding('materialAttribute')"
          >
            <template #header>
              <span class="ulc-th-text">物资属性</span>
            </template>
            <template #default="{ row }"><SpdStatusChip tone="neutral">{{ row.materialAttribute }}</SpdStatusChip></template>
          </el-table-column>
        </el-table>
      </div>
      <template #pagination>
        <MdPagination
          :page="query.page"
          :total-pages="Math.max(1, Math.ceil(total / query.size))"
          :total="total"
          :size="query.size"
          :loading="loading"
          @update:page="changePage"
          @update:size="changeSize"
        />
      </template>
    </MdDataTableCard>
  </main>
</template>

<style scoped>
/* =====================================================================
   报表中心 / 监管报表 RegulatoryReportView
   ---------------------------------------------------------------------
   1. MdPageHeader 渐变页头（无动作按钮）
   2. 工具区：查询与动作同卡
   3. 汇总条：报告类型不同，指标不同
   4. 三种 el-table：supplier / centralized / inventory
   5. 内容区 padding 16/20/24
   ===================================================================== */
.report-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 16px 20px 24px;
  box-sizing: border-box;
}
.report-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}
/* 工具区：查询 + 动作同一张卡 */
.report-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 14px;
  padding: 14px 16px;
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-line, var(--spd-p-border-subtle));
  border-radius: 10px;
  box-shadow: var(--spd-p-shadow-1);
}
.report-filters {
  display: grid;
  grid-template-columns: minmax(280px, 1.2fr) repeat(4, minmax(140px, 1fr));
  gap: 10px 14px;
  align-items: end;
}
.report-filters label {
  display: grid;
  gap: 5px;
  min-width: 0;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}
.report-filters input,
.report-filters select {
  width: 100%;
  min-height: 36px;
  border: 1px solid var(--spd-line, var(--spd-p-border));
  border-radius: 7px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  padding: 0 10px;
  font: inherit;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
}
.report-filters input:focus,
.report-filters select:focus {
  outline: none;
  border-color: var(--spd-primary, var(--spd-p-primary));
  box-shadow: var(--spd-p-shadow-1);
}
.date-range {
  display: grid;
  grid-template-columns: minmax(112px, 1fr) auto minmax(112px, 1fr);
  align-items: center;
  gap: 6px;
}
.date-range i { color: var(--spd-p-text-muted); font-style: normal; }
.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.report-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 22px;
  min-height: 44px;
  padding: 8px 16px;
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-line, var(--spd-p-border-subtle));
  border-radius: 10px;
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-base);
}
.report-summary strong { color: var(--spd-p-text); font-size: var(--spd-font-size-lg); font-weight: 800; }
.report-summary .primary-text { color: var(--spd-primary, var(--spd-p-primary)); }
.report-summary .danger-text { color: var(--spd-p-danger-strong); }
.report-summary .warning-text { color: var(--spd-p-warning-text); font-weight: 800; }

/* ---------- 表格 ----------
   表头 / 斑马纹 / 拖拽热区等通用外观由 unified-list.css 的
   .spd-unified-list .ulc-table 提供，这里只保留本页特有的
   完成率进度条、状态徽标与数值强调样式。 */
/* 完成率单元格：进度条 + 百分比**同一行**、垂直居中。
   旧写法 `grid-template-columns: minmax(80px, 1fr) 56px` 把百分比
   固定在 56px 列里，内容稍长（如 106.67%）就换行，
   行高随之从 22px 变成 43px —— 用户报「完成率这一列没有对齐文字，
   文字没有在同一行」。
   现在改为 `minmax(0, 1fr) auto`：进度条吃掉剩余宽度，
   百分比按内容自适应宽度并靠右（justify-self: end），
   因此永远与进度条同处一行。 */
.ulc-table :deep(.progress-cell) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.ulc-table :deep(.progress-cell > span) {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--spd-p-primary-border);
}
.ulc-table :deep(.progress-cell > span i) {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--spd-p-primary-hover);
}
.ulc-table :deep(.progress-cell strong) {
  color: var(--spd-p-primary-strong);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  /* 由栅格把它推到行尾，而不是靠 text-align: right（那会与换行叠加） */
  justify-self: end;
  white-space: nowrap;
}
.ulc-table :deep(.positive-text) { color: var(--spd-p-primary); }
.ulc-table :deep(.danger-text) { color: var(--spd-p-danger-strong); }
.ulc-table :deep(.strong-number) { color: var(--spd-p-text); font-weight: 800; }
.ulc-table :deep(.warning-text) { color: var(--spd-p-warning-text); font-weight: 800; }

@media (max-width: 1480px) {
  .report-toolbar { grid-template-columns: 1fr; }
  .report-actions { justify-content: flex-start; }
  .report-filters { grid-template-columns: repeat(3, minmax(160px, 1fr)); }
}
@media (max-width: 1100px) {
  .report-filters { grid-template-columns: repeat(2, minmax(160px, 1fr)); }
}
@media (max-width: 720px) {
  .report-filters { grid-template-columns: 1fr; }
  .report-actions { display: grid; grid-template-columns: repeat(2, 1fr); }
  .report-actions .btn { width: 100%; }
}
@media print {
  .sidebar, .topbar, .report-toolbar, .report-page > .md-header, .hospital-pagination { display: none !important; }
  .app-shell { display: block !important; height: auto !important; }
  .main-area { overflow: visible !important; padding: 0 !important; background: var(--spd-p-bg) !important; }
  .report-page { padding: 0 !important; }
  .report-summary, .md-table-card { box-shadow: none; border-color: var(--spd-p-text-secondary); }
}
</style>
