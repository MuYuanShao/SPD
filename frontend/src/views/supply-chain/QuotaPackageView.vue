<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Teleport } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  AlertCircle,
  ArchiveRestore,
  ArrowRight,
  Ban,
  Boxes,
  CheckCircle2,
  ClipboardList,
  Columns3,
  Download,
  Edit3,
  FileDown,
  History,
  PackageCheck,
  Plus,
  PlusCircle,
  Printer,
  RefreshCw,
  Save,
  Search,
  ShieldCheck,
  Upload,
  X
} from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import TableStateRow from '../../components/common/TableStateRow.vue'
import { useAuthStore } from '../../stores/auth'
import { fetchMasterDataPage } from '../../api/masterData'
import { useQuotaPackageDataLoader } from '../../composables/useQuotaPackageDataLoader'
import { useQuotaPackagePagination } from '../../composables/useQuotaPackagePagination'
import { useQuotaPackingTaskActions } from '../../composables/useQuotaPackingTaskActions'
import { useQuotaPackageViewState } from '../../composables/useQuotaPackageViewState'
import { useQuotaProductSelector } from '../../composables/useQuotaProductSelector'
import { useQuotaSafetyCatalog } from '../../composables/useQuotaSafetyCatalog'
import { useQuotaSafetyDialog } from '../../composables/useQuotaSafetyDialog'
import { useQuotaSafetyImportExport } from '../../composables/useQuotaSafetyImportExport'
import { useQuotaTemplateActions, type QuotaActionResult } from '../../composables/useQuotaTemplateActions'
import { useQuotaTemplateSelector } from '../../composables/useQuotaTemplateSelector'
import { useSpdConfirm } from '../../composables/useSpdConfirm'
import {
  labelStatusText,
  taskStatusText
} from '../../config/quotaPackageDisplay'
import {
  type PackageLabelRow,
  type PackingTaskRow,
  type QuotaSafetyRow,
  type QuotaTemplateRow,
  allocatePackingTaskFromReceiving,
  fetchPackingTaskAllocations,
  fetchReceivingLooseStock
} from '../../api/quotaPackages'
import { formatBusinessText, formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'
import QuotaProductSelectorDialog from '../../components/supply-chain/QuotaProductSelectorDialog.vue'
import QuotaSafetyDialog from '../../components/supply-chain/QuotaSafetyDialog.vue'
import QuotaTemplateSelectorDialog from '../../components/supply-chain/QuotaTemplateSelectorDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
/**
 * 统一的操作结果通道：定数包下所有 composable 共用。
 * tone=success → 在对应菜单的就地提示条显示；tone=error/warning → ElMessage 弹出。
 * 这样既不会出现「提示挂在别的子菜单上」，也不会把错误静默吞掉。
 */
const actionResult = ref<QuotaActionResult>({ text: '', tone: 'success' })
/** 当前子菜单的就地成功提示条 */
const packingFeedback = ref('')
const loadError = ref('')
const templateDialogOpen = ref(false)
const templateDialogMode = ref<'create' | 'edit'>('create')
const templateError = ref('')
const selectedTemplateCodes = ref<string[]>([])
const importInput = ref<HTMLInputElement | null>(null)
const safetyImportInput = ref<HTMLInputElement | null>(null)
const templates = ref<QuotaTemplateRow[]>([])
const safetyRows = ref<QuotaSafetyRow[]>([])
const tasks = ref<PackingTaskRow[]>([])
const labels = ref<PackageLabelRow[]>([])
const events = ref<Record<string, unknown>[]>([])
const warehouses = ref<Array<{ warehouseName: string; warehouseType: string }>>([])
const candidates = ref<Array<Record<string, unknown>>>([])
const snapshotColumns = ref([
  { key: 'warehouseName', label: '库房', visible: true }, { key: 'productCode', label: '商品编码', visible: true },
  { key: 'productName', label: '商品名称', visible: true }, { key: 'systemBatchNo', label: '系统批次', visible: true },
  { key: 'productionBatchNo', label: '生产批号', visible: true }, { key: 'expireDate', label: '有效期', visible: true },
  { key: 'batchUnitPrice', label: '批次单价', visible: true }, { key: 'availableQty', label: '可用数量', visible: true }
])
const visibleSnapshotColumns = computed(() => snapshotColumns.value.filter(column => column.visible))
const selectedTaskNo = ref('')
const taskReservations = ref<Record<string, unknown>[]>([])
const taskLoading = ref(false)
const safetyImporting = ref(false)

const query = reactive({
  warehouseName: '',
  taskNo: '',
  eventNo: '',
  eventType: '',
  templateCode: '',
  templateName: '',
  productCode: '',
  productName: '',
  deptName: '',
  labelNo: ''
})
const templateForm = reactive({
  templateCode: '',
  templateName: '',
  productCode: '',
  quantity: 1,
  unit: ''
})
const safetyForm = reactive({
  deptCode: '',
  deptName: '',
  warehouseName: '',
  templateCode: '',
  templateId: undefined as number | undefined,
  productCode: '',
  minQty: 1,
  maxQty: 3
})
const safetyWarehouseOptions = ref<Array<{ name: string; dept: string; deptCode: string }>>([])

async function loadSafetyWarehouses() {
  try {
    const page = await fetchMasterDataPage('warehouse-location-management', { page: '1', size: '200' })
    safetyWarehouseOptions.value = page.rows.map((row) => ({
      name: String(row.name ?? ''),
      dept: String(row.dept ?? ''),
      deptCode: String(row.deptCode ?? '')
    }))
  } catch (err) {
    safetyWarehouseOptions.value = []
  }
}

function selectSafetyWarehouse(warehouse: { name: string; dept: string; deptCode: string }) {
  safetyForm.warehouseName = warehouse.name
  safetyForm.deptName = warehouse.dept && warehouse.dept !== '-' ? warehouse.dept : ''
  safetyForm.deptCode = warehouse.deptCode
}

// ── 按验收单分配散货 ──
const allocateTaskNo = ref('')
const receivingNo = ref('')
const allocateQty = ref<number | null>(null)
const looseStockRows = ref<Record<string, unknown>[]>([])
const receivingAllocations = ref<Record<string, unknown>[]>([])

async function searchReceivingLooseStock() {
  const no = receivingNo.value.trim()
  if (!no) {
    notifyError('请输入验收单号', 'warning')
    return
  }
  try {
    looseStockRows.value = await fetchReceivingLooseStock(no)
    if (!looseStockRows.value.length) {
      notifyError('该验收单没有可分配的散货库存', 'warning')
    }
  } catch (error: any) {
    notifyError(error?.response?.data?.message || error?.message || '验收单散货查询失败')
  }
}

async function loadReceivingAllocations() {
  if (!allocateTaskNo.value) {
    receivingAllocations.value = []
    return
  }
  try {
    receivingAllocations.value = await fetchPackingTaskAllocations(allocateTaskNo.value)
  } catch {
    receivingAllocations.value = []
  }
}

async function doAllocateFromReceiving(full: boolean) {
  if (!allocateTaskNo.value || !receivingNo.value.trim()) {
    notifyError('请先选择打包任务并输入验收单号', 'warning')
    return
  }
  try {
    const result = await allocatePackingTaskFromReceiving(allocateTaskNo.value, {
      receivingNo: receivingNo.value.trim(),
      quantity: full ? null : allocateQty.value
    })
    notifySuccess(
      full
        ? `${result.taskNo} 已按验收单 ${result.receivingNo} 全部打包分配 ${result.allocatedQty}`
        : `${result.taskNo} 已按验收单 ${result.receivingNo} 分配 ${result.allocatedQty}，剩余保持散货库存`
    )
    allocateQty.value = null
    await loadReceivingAllocations()
    await searchReceivingLooseStock()
    await loadData()
  } catch (error: any) {
    notifyError(error?.response?.data?.message || error?.message || '按验收单分配失败')
  }
}

watch(allocateTaskNo, () => {
  void loadReceivingAllocations()
})
const packingForm = reactive({
  templateCode: '',
  warehouseName: '',
  packageCount: 1,
  remark: ''
})
const {
  productSelectorOpen,
  productLoading,
  productQuery,
  quotaProducts,
  productPage,
  productSize,
  productTotal,
  selectedProductInfo,
  searchQuotaProducts,
  openProductSelector,
  selectProduct
} = useQuotaProductSelector(templateForm)
const {
  templateSelectorOpen,
  templateSelectorQuery,
  showAllTemplates,
  filteredPackingTemplates,
  openTemplateSelector,
  selectPackingTemplate,
  clearPackingTemplate,
  fillPacking
} = useQuotaTemplateSelector({ templates, warehouses, packingForm })
const {
  safetyCatalogQuery,
  safetyCatalogPagination,
  filteredSafetyTemplates,
  pagedSafetyTemplates,
  resetSafetyCatalogQuery,
  selectSafetyTemplate,
  changeSafetyCatalogPage,
  changeSafetyCatalogPageSize
} = useQuotaSafetyCatalog({ templates, safetyForm })
const {
  safetyDialogOpen,
  safetyDialogMode,
  editingSafetyId,
  fillSafety,
  resetSafetyForm,
  openSafetyDialog,
  closeSafetyDialog,
  submitSafety
} = useQuotaSafetyDialog({
  safetyForm,
  message: actionResult,
  safetyCatalogQuery,
  resetSafetyCatalogQuery,
  reload: loadData
})

// 打开安全量弹窗时预加载库房选项（关联库房搜索）
watch(safetyDialogOpen, (open) => {
  if (open) {
    loadSafetyWarehouses()
  }
})
const selectedTemplates = computed(() =>
  templates.value.filter((item) => selectedTemplateCodes.value.includes(item.templateCode))
)

const selectedPackingTemplate = computed(() =>
  templates.value.find((t) => t.templateCode === packingForm.templateCode)
)

const { mode, packageSection, title, subtitle } = useQuotaPackageViewState({
  routeCode: () => String(route.params.code),
  templates,
  safetyRows,
  tasks,
  labels,
  events,
  warehouses,
  candidates
})

const {
  quotaPagination,
  changeQuotaPage,
  changeQuotaPageSize,
  resetQuotaPages
} = useQuotaPackagePagination(loadData)

const { loadData: loadQuotaPackageData, templateQuery } = useQuotaPackageDataLoader({
  mode,
  packageSection,
  loading,
  loadError,
  query,
  quotaPagination,
  packingForm,
  templates,
  safetyRows,
  tasks,
  labels,
  events,
  warehouses,
  candidates
})

async function loadData() {
  await loadQuotaPackageData()
}

function resetTemplateQuery() {
  query.templateCode = ''
  query.templateName = ''
  query.productCode = ''
  query.productName = ''
  quotaPagination.templates.page = 1
  loadData()
}

function onTemplateQuerySubmit() {
  quotaPagination.templates.page = 1
  loadData()
}

async function showGeneratedLabels() {
  query.labelNo = ''
  quotaPagination.labels.page = 1
  await router.push('/features/quota-label-unpack')
}

/* ===== 定数包标签与解包：工具条概览 + 查询辅助 ===== */

/** 当前页各状态标签数量，用于工具条状态概览 */
const labelStat = computed(() => {
  const stat = { pending_print: 0, available: 0, void: 0 }
  for (const row of labels.value) {
    if (row.status in stat) stat[row.status as keyof typeof stat] += 1
  }
  return stat
})

/** 是否填写了标签查询条件（用于禁用「重置」） */
const hasLabelQuery = computed(() =>
  ['labelNo', 'templateCode', 'productCode', 'productName', 'deptName'].some((key) =>
    String((query as Record<string, unknown>)[key] ?? '').trim()
  )
)

/** 清空标签查询条件并重新加载 */
function resetLabelQuery() {
  query.labelNo = ''
  query.templateCode = ''
  query.productCode = ''
  query.productName = ''
  query.deptName = ''
  quotaPagination.labels.page = 1
  loadData()
}

/** 是否允许打印该标签（与工具条/行内按钮的显示条件保持一致） */
function canPrintLabel(row: PackageLabelRow) {
  return (
    (row.status === 'pending_print' || row.status === 'available') &&
    authStore.hasPermission('quota-package-label:print')
  )
}

/* ===== 定数包事件：类型语义 + 状态流转 + 数量变化 ===== */

/**
 * 事件类型下拉项。
 * 取值来自后端 quota_package_event.event_type 的实际写入点，
 * 标签与 chineseDisplay.ts 的中文映射保持一致。
 */
const eventTypeOptions = [
  { value: 'pack_confirm', label: '确认打包' },
  { value: 'label_print', label: '标签打印' },
  { value: 'delivery_out', label: '配送出库' },
  { value: 'delivery_sign', label: '科室签收' },
  { value: 'unpack_to_loose', label: '拆包转散货' },
  { value: 'terminate_to_loose', label: '终止并退回散货' },
  { value: 'recall_unpack_to_loose', label: '召回解包退回散货' }
]

/** 事件类型的语义色调，用于徽标着色 */
function eventTone(raw: unknown) {
  const type = String(raw ?? '').trim()
  if (type === 'recall_unpack_to_loose' || type === 'terminate_to_loose') return 'is-danger'
  if (type === 'unpack_to_loose') return 'is-warn'
  if (type === 'delivery_sign') return 'is-ok'
  if (type === 'delivery_out') return 'is-info'
  if (type === 'label_print') return 'is-print'
  return 'is-pack'
}

/** 数量是否发生变化（0 视为无变化） */
function hasQtyChange(raw: unknown) {
  const value = Number(raw)
  return Number.isFinite(value) && value !== 0
}

/** 数量变化的语义色调：正数入库、负数出库 */
function qtyTone(raw: unknown) {
  return Number(raw) > 0 ? 'is-plus' : 'is-minus'
}

/** 数量变化展示：保留符号并去除多余的尾随零 */
function formatQtyChange(raw: unknown) {
  const value = Number(raw)
  if (!Number.isFinite(value)) return '-'
  const normalized = Number(value.toFixed(4))
  return `${normalized > 0 ? '+' : ''}${normalized}`
}

/** 是否填写了事件查询条件（用于禁用「重置」） */
const hasEventQuery = computed(() =>
  ['eventNo', 'labelNo', 'eventType'].some((key) =>
    String((query as Record<string, unknown>)[key] ?? '').trim()
  )
)

/** 查询事件：回到第一页再加载 */
function onEventQuerySubmit() {
  quotaPagination.events.page = 1
  loadData()
}

/** 清空事件查询条件并重新加载 */
function resetEventQuery() {
  query.eventNo = ''
  query.labelNo = ''
  query.eventType = ''
  quotaPagination.events.page = 1
  loadData()
}

/* ===== 可打包散货快照：列配置 + 有效期预警 + 数值格式化 ===== */

/** 显示列面板展开状态（用于收起后收起浮层） */
const columnPanelOpen = ref(false)

/** 查询散货快照：回到第一页再加载 */
function onSnapshotQuery() {
  quotaPagination.candidates.page = 1
  loadData()
}

/** 是否填写了散货快照查询条件（用于禁用「重置」） */
const hasSnapshotQuery = computed(() =>
  ['productName', 'productCode', 'warehouseName'].some((key) =>
    String((query as Record<string, unknown>)[key] ?? '').trim()
  )
)

/** 清空散货快照查询条件并重新加载 */
function resetSnapshotQuery() {
  query.productName = ''
  query.productCode = ''
  query.warehouseName = ''
  quotaPagination.candidates.page = 1
  loadData()
}

/** 关闭显示列面板时同步状态，避免浮层残留 */
function onColumnPanelToggle(event: Event) {
  columnPanelOpen.value = (event.target as HTMLDetailsElement).open
}

/** 固定列的对齐方式：金额与数量右对齐，其余左对齐 */
function snpColumnClass(key: string) {
  if (key === 'batchUnitPrice' || key === 'availableQty') return 'snp-col-num'
  if (key === 'expireDate') return 'snp-col-expire'
  if (key === 'warehouseName') return 'snp-col-warehouse'
  if (key === 'productCode' || key === 'productionBatchNo' || key === 'systemBatchNo') return 'snp-col-code'
  return ''
}

/** 距到期天数；无法解析时返回 null */
function daysToExpire(raw: unknown): number | null {
  const text = String(raw ?? '').trim()
  if (!text) return null
  const target = new Date(text)
  if (Number.isNaN(target.getTime())) return null
  return Math.ceil((target.getTime() - Date.now()) / 86_400_000)
}

/** 有效期语义色调：已过期 / 30 天内到期 / 90 天内到期 / 正常 */
function expiryTone(raw: unknown) {
  const days = daysToExpire(raw)
  if (days === null) return 'is-unknown'
  if (days < 0) return 'is-expired'
  if (days <= 30) return 'is-critical'
  if (days <= 90) return 'is-warning'
  return 'is-normal'
}

/** 有效期悬浮提示：补充剩余天数 */
function expiryTitle(raw: unknown) {
  const text = String(raw ?? '').trim()
  const days = daysToExpire(raw)
  if (!text || days === null) return text
  if (days < 0) return `${text}（已过期 ${Math.abs(days)} 天）`
  if (days === 0) return `${text}（今天到期）`
  return `${text}（剩余 ${days} 天）`
}

/** 批次单价：固定两位小数 */
function formatSnapshotPrice(raw: unknown) {
  const value = Number(raw)
  return Number.isFinite(value) ? value.toFixed(2) : '-'
}

/** 可用数量：去除多余的尾随零 */
function formatSnapshotQty(raw: unknown) {
  const value = Number(raw)
  return Number.isFinite(value) ? String(Number(value.toFixed(4))) : '-'
}

/** 本页可用散货合计（仅统计当前页，不代表全量） */
const pageAvailableQty = computed(() => {
  const total = candidates.value.reduce((sum, row) => {
    const value = Number(row.availableQty)
    return sum + (Number.isFinite(value) ? value : 0)
  }, 0)
  return formatSnapshotQty(total)
})

/* ===== 打印模板调整（定数包模板维护） ===== */

/** 是否已勾选当前页全部模板 */
const allTemplatesSelected = computed(
  () => templates.value.length > 0 && selectedTemplateCodes.value.length === templates.value.length
)

/** 勾选/取消勾选当前页全部模板 */
function toggleAllTemplates(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  selectedTemplateCodes.value = checked ? templates.value.map((item) => item.templateCode) : []
}

/** 是否填写了模板查询条件（用于禁用「重置」） */
const hasTemplateQuery = computed(() =>
  ['templateCode', 'templateName', 'productCode', 'productName'].some((key) =>
    String((query as Record<string, unknown>)[key] ?? '').trim()
  )
)

/** 包装数量展示：去掉 .000 这类无意义尾随零 */
function formatTemplateQty(raw: unknown) {
  const value = Number(raw)
  return Number.isFinite(value) ? String(Number(value.toFixed(4))) : '-'
}

/* ===== 定数安全量 ===== */

/** 是否填写了安全量查询条件（用于禁用「重置」） */
const hasSafetyQuery = computed(() =>
  ['deptName', 'productCode', 'productName'].some((key) =>
    String((query as Record<string, unknown>)[key] ?? '').trim()
  )
)

/** 查询安全量：回到第一页再加载 */
function onSafetyQuery() {
  quotaPagination.safety.page = 1
  loadData()
}

/** 清空安全量查询条件并重新加载 */
function resetSafetyQuery() {
  query.deptName = ''
  query.productCode = ''
  query.productName = ''
  quotaPagination.safety.page = 1
  loadData()
}

/** 安全量展示：去掉 .000 这类无意义尾随零 */
function formatSafetyQty(raw: unknown) {
  const value = Number(raw)
  return Number.isFinite(value) ? String(Number(value.toFixed(4))) : '-'
}

/** 安全量是否启用（兼容中文/英文两种状态值） */
function isSafetyEnabled(status: unknown) {
  const text = String(status ?? '').trim()
  return text !== '禁用' && text.toLowerCase() !== 'disabled' && text.toLowerCase() !== 'inactive'
}

/**
 * 安全区间可视化条。
 * 下限/上限都是同一单位，直接比较即可；用上限作为满格基准展示下限占比，
 * 让「下限偏低 / 区间偏窄」这类配置问题一眼可见。
 */
function safetyRangeStyle(row: { minQty: number; maxQty: number }) {
  const min = Number(row.minQty)
  const max = Number(row.maxQty)
  if (!Number.isFinite(min) || !Number.isFinite(max) || max <= 0) {
    return { width: '0%' }
  }
  const ratio = Math.min(Math.max(min / max, 0), 1)
  return { width: `${(ratio * 100).toFixed(1)}%` }
}

const { ask, ConfirmHost: SpdConfirmHost } = useSpdConfirm()

const {
  fillTemplate,
  resetTemplateForm,
  openCreateTemplate,
  openEditTemplate,
  submitTemplate,
  disableSelectedTemplates,
  enableSelectedTemplates,
  exportTemplates,
  downloadTemplateImportFile,
  triggerTemplateImport,
  handleTemplateImport
} = useQuotaTemplateActions({
  templates,
  selectedTemplates,
  selectedTemplateCodes,
  templateForm,
  packingForm,
  selectedProductInfo,
  templateDialogOpen,
  templateDialogMode,
  templateError,
  importInput,
  message: actionResult,
  reload: loadData,
  askConfirm: ask
})

const {
  triggerSafetyImport,
  downloadSafetyImportFile,
  exportSafetyRows,
  handleSafetyImport
} = useQuotaSafetyImportExport({
  safetyImportInput,
  safetyImporting,
  safetyRows,
  safetyPagination: quotaPagination.safety,
  query,
  message: actionResult,
  reload: loadData
})

/* 打包任务操作与模板/安全量操作共用 actionResult 通道 */
const {
  submitPackingTask: submitPackingTaskRaw,
  approveTask,
  cancelTask,
  terminateTask,
  terminateTaskByTaskNo: terminateTaskByTaskNoRaw,
  recalculateTask,
  viewTaskReservations,
  unpackLabel,
  printLabel
} = useQuotaPackingTaskActions({
  packingForm,
  message: actionResult,
  taskLoading,
  selectedTaskNo,
  taskReservations,
  reload: loadData,
  showGeneratedLabels
})

/* =====================================================================
   统一反馈出口
   ---------------------------------------------------------------------
   - success：在「当前子菜单」的就地提示条显示，成功的同时列表已刷新
   - error / warning：用 ElMessage 弹出醒目提示，不占用页面版面
   因此无论在哪个子菜单操作，提示都不会跑到别的菜单去。
   ===================================================================== */
function notifySuccess(text: string) {
  packingFeedback.value = text
}

function notifyError(text: string, tone: 'error' | 'warning' = 'error') {
  packingFeedback.value = ''
  ElMessage({ message: text, type: tone, duration: 4000, showClose: true })
}

watch(actionResult, (result) => {
  if (!result.text) return
  if (result.tone === 'success') {
    notifySuccess(result.text)
  } else {
    notifyError(result.text, result.tone)
  }
})

async function submitPackingTask() {
  if (!packingForm.templateCode) {
    notifyError('请先点击上方「打包模板」选择模板，再创建任务', 'warning')
    return
  }
  packingFeedback.value = ''
  await submitPackingTaskRaw()
}

async function terminateTaskByTaskNo() {
  if (tasks.value.length === 0) {
    notifyError('当前没有可终止的打包任务', 'warning')
    return
  }
  packingFeedback.value = ''
  await terminateTaskByTaskNoRaw()
}

// 选中模板后清空旧的成功提示
watch(() => packingForm.templateCode, (code) => {
  if (code && packingFeedback.value) {
    packingFeedback.value = ''
  }
})

onMounted(loadData)
watch(() => route.params.code, () => {
  // 切换子菜单时清空反馈，避免上一菜单的提示显示在当前菜单
  packingFeedback.value = ''
  actionResult.value = { text: '', tone: 'success' }
  resetQuotaPages()
  loadData()
})

watch(
  () => [
    safetyCatalogQuery.templateCode,
    safetyCatalogQuery.templateName,
    safetyCatalogQuery.deptName,
    safetyCatalogQuery.productName
  ],
  () => {
    safetyCatalogPagination.page = 1
  }
)
</script>

<template>
  <section class="md-partner-page quota-package-page">
    <MdPageHeader
      crumb="院内 SPD · 定数包"
      :title="title"
      :description="subtitle"
      :loading="loading"
      @refresh="loadData"
    />

    <!-- 全局横幅已移除：所有操作反馈统一走 ElMessage（失败/警告）
         或当前子菜单的就地提示条（成功），不会跨菜单残留。 -->
    <div v-if="loadError" class="pt-load-error">
      <AlertCircle :size="15" />
      <span>{{ loadError }}</span>
      <button type="button" class="btn-text" @click="loadData">重试</button>
    </div>

    <template v-if="mode === 'template'">
      <section v-if="packageSection === 'quota-template-maintenance'" class="tpl-card">
        <div v-if="packingFeedback" class="tpl-banner">
          <CheckCircle2 :size="15" />
          <span>{{ packingFeedback }}</span>
        </div>

        <form class="tpl-filter" @submit.prevent="onTemplateQuerySubmit">
          <label class="tpl-field">
            <span>模板编码</span>
            <input v-model.trim="query.templateCode" type="search" placeholder="模糊查询模板编码" autocomplete="off" />
          </label>
          <label class="tpl-field">
            <span>模板名称</span>
            <input v-model.trim="query.templateName" type="search" placeholder="模糊查询模板名称" autocomplete="off" />
          </label>
          <label class="tpl-field">
            <span>商品编码</span>
            <input v-model.trim="query.productCode" type="search" placeholder="模糊查询商品编码" autocomplete="off" />
          </label>
          <label class="tpl-field">
            <span>商品名称</span>
            <input v-model.trim="query.productName" type="search" placeholder="模糊查询商品名称" autocomplete="off" />
          </label>
          <div class="tpl-filter-actions">
            <button class="tpl-btn" type="button" :disabled="loading || !hasTemplateQuery" @click="resetTemplateQuery">
              <RefreshCw :size="15" />
              重置
            </button>
            <button class="tpl-btn tpl-btn-primary" type="submit" :disabled="loading">
              <Search :size="16" />
              查询
            </button>
          </div>
        </form>

        <div class="tpl-toolbar">
          <div class="tpl-toolbar-main">
            <button
              v-if="authStore.hasPermission('quota-template-maintenance:write')"
              class="tpl-btn tpl-btn-primary"
              type="button"
              aria-label="新增定数包模板"
              title="新增定数包模板"
              @click="openCreateTemplate"
            >
              <Plus :size="16" />
              新增
            </button>
            <button
              v-if="authStore.hasPermission('quota-template-maintenance:write')"
              class="tpl-btn"
              type="button"
              :disabled="selectedTemplateCodes.length !== 1"
              @click="openEditTemplate()"
            >
              <Edit3 :size="16" />
              修改
            </button>
            <button
              v-if="authStore.hasPermission('quota-template-maintenance:write')"
              class="tpl-btn tpl-btn-danger"
              type="button"
              :disabled="!selectedTemplateCodes.length"
              @click="disableSelectedTemplates()"
            >
              <Ban :size="16" />
              停用
            </button>
            <button
              v-if="authStore.hasPermission('quota-template-maintenance:write')"
              class="tpl-btn"
              type="button"
              :disabled="!selectedTemplateCodes.length"
              @click="enableSelectedTemplates()"
            >
              <CheckCircle2 :size="16" />
              启用
            </button>
            <details class="tpl-import">
              <summary class="tpl-btn">
                <Upload :size="16" />
                导入
              </summary>
              <div class="tpl-import-menu">
                <button type="button" @click="downloadTemplateImportFile">
                  <FileDown :size="16" />
                  导入模板下载
                </button>
                <button type="button" @click="triggerTemplateImport">
                  <Upload :size="16" />
                  上传模板导入
                </button>
              </div>
            </details>
            <button class="tpl-btn" type="button" @click="exportTemplates">
              <Download :size="16" />
              导出
            </button>
            <input ref="importInput" class="tpl-file-input" type="file" accept=".csv" @change="handleTemplateImport" />
          </div>
          <div class="tpl-toolbar-info">
            <span class="tpl-chip">共 {{ quotaPagination.templates.total }} 条</span>
            <span v-if="selectedTemplateCodes.length" class="tpl-chip is-primary">
              已选 {{ selectedTemplateCodes.length }} 条
            </span>
          </div>
        </div>

        <div class="tpl-scroll">
          <table class="tpl-table">
            <thead>
              <tr>
                <th class="tpl-col-select">
                  <input
                    type="checkbox"
                    :checked="allTemplatesSelected"
                    aria-label="选择当前页全部模板"
                    @change="toggleAllTemplates"
                  />
                </th>
                <th class="tpl-col-code">模板编码</th>
                <th>模板名称</th>
                <th class="tpl-col-code">商品编码</th>
                <th>商品名称</th>
                <th class="tpl-col-spec">规格</th>
                <th>厂家</th>
                <th>供应商</th>
                <th class="tpl-col-version">版本</th>
                <th class="tpl-col-qty">包装数量</th>
                <th class="tpl-col-status">状态</th>
                <th class="tpl-col-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="12" state="loading" message="正在加载定数包模板..." />
              <TableStateRow v-else-if="templates.length === 0" :colspan="12" message="暂无符合条件的定数包模板" />
              <tr
                v-for="row in templates"
                v-else
                :key="row.templateId"
                class="tpl-row"
                :class="{ 'is-selected': selectedTemplateCodes.includes(row.templateCode) }"
              >
                <td class="tpl-col-select">
                  <input v-model="selectedTemplateCodes" type="checkbox" :value="row.templateCode" :aria-label="`选择模板 ${row.templateCode}`" />
                </td>
                <td class="tpl-col-code">
                  <span class="tpl-code">{{ row.templateCode }}</span>
                </td>
                <td :title="String(row.templateName)">
                  <div class="tpl-main">{{ row.templateName }}</div>
                </td>
                <td class="tpl-col-code">
                  <span class="tpl-code">{{ row.productCode }}</span>
                </td>
                <td>
                  <div class="tpl-main">{{ row.productName }}</div>
                </td>
                <td class="tpl-col-spec">{{ row.specModel || '-' }}</td>
                <td>{{ row.manufacturerName || '-' }}</td>
                <td>{{ row.supplierName || '-' }}</td>
                <td class="tpl-col-version">
                  <span class="tpl-version">V{{ row.versionNo || 1 }}</span>
                  <span v-if="row.currentVersion" class="tpl-version-current">当前</span>
                </td>
                <td class="tpl-col-qty">
                  <span class="tpl-qty">{{ formatTemplateQty(row.quantity) }}</span>
                  <span v-if="row.unit" class="tpl-qty-unit">{{ row.unit }}</span>
                </td>
                <td class="tpl-col-status">
                  <span class="tpl-status" :class="row.status === '禁用' ? 'is-disabled' : 'is-enabled'">
                    <i class="tpl-status-dot" />
                    {{ formatStatusText(row.status) }}
                  </span>
                </td>
                <td class="tpl-col-actions">
                  <div class="tpl-actions">
                    <button
                      v-if="authStore.hasPermission('quota-template-maintenance:write')"
                      class="tpl-act"
                      type="button"
                      @click="openEditTemplate(row)"
                    >
                      <Edit3 :size="14" />
                      修改
                    </button>
                    <button
                      v-if="authStore.hasPermission('quota-template-maintenance:write')"
                      class="tpl-act tpl-act-danger"
                      type="button"
                      @click="disableSelectedTemplates(row)"
                    >
                      <Ban :size="14" />
                      停用
                    </button>
                    <button
                      v-if="row.status === '禁用' && authStore.hasPermission('quota-template-maintenance:write')"
                      class="tpl-act"
                      type="button"
                      @click="enableSelectedTemplates(row)"
                    >
                      <CheckCircle2 :size="14" />
                      启用
                    </button>
                    <button
                      v-if="authStore.hasPermission('quota-packing-task:create')"
                      class="tpl-act tpl-act-primary"
                      type="button"
                      @click="fillPacking(row)"
                    >
                      <PackageCheck :size="14" />
                      打包
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <PaginationControls
          :page="quotaPagination.templates.page"
          :size="quotaPagination.templates.size"
          :total="quotaPagination.templates.total"
          :loading="loading"
          @change-page="changeQuotaPage('templates', $event)"
          @change-size="changeQuotaPageSize('templates', $event)"
        />
      </section>

      <!-- =========================================================
           打包任务确认
           ========================================================= -->
      <section
        v-if="packageSection === 'packing-task-confirmation'"
        class="pt-page"
      >
        <!-- 主卡片：新建任务 -->
        <div class="pt-card">
          <div class="pt-card-header">
            <div class="pt-card-header-left">
              <span class="pt-card-header-icon">
                <PlusCircle :size="14" />
              </span>
              <span class="pt-card-header-title">新建打包任务</span>
            </div>
          </div>
          <div class="pt-form-grid">
            <label class="pt-form-field">
              <span class="pt-form-label">打包模板 <em class="pt-form-required">*</em></span>
              <button
                v-if="!selectedPackingTemplate"
                type="button"
                class="pt-template-picker is-empty"
                @click="openTemplateSelector"
              >
                <Search :size="14" />
                <span class="pt-template-picker-text">点击选择打包模板</span>
                <span class="pt-template-picker-code">未选择</span>
              </button>
              <div v-else class="pt-selected-template">
                <div class="pt-selected-template-head">
                  <div class="pt-selected-template-title">
                    <Boxes :size="14" />
                    <strong class="pt-selected-template-name">{{ selectedPackingTemplate.templateName }}</strong>
                    <span class="pt-selected-template-code">{{ selectedPackingTemplate.templateCode }}</span>
                  </div>
                  <div class="pt-selected-template-actions">
                    <button
                      type="button"
                      class="pt-btn-text"
                      :title="'更换打包模板'"
                      @click="openTemplateSelector"
                    >
                      <RefreshCw :size="13" />
                      <span>更换</span>
                    </button>
                    <button
                      type="button"
                      class="pt-btn-text pt-btn-text-danger"
                      :title="'清除已选模板'"
                      @click="clearPackingTemplate"
                    >
                      <X :size="13" />
                      <span>清除</span>
                    </button>
                  </div>
                </div>
                <dl class="pt-selected-template-meta">
                  <div class="pt-selected-template-meta-item">
                    <dt>商品</dt>
                    <dd>{{ selectedPackingTemplate.productName || '-' }}</dd>
                  </div>
                  <div class="pt-selected-template-meta-item">
                    <dt>规格</dt>
                    <dd>{{ selectedPackingTemplate.specModel || '-' }}</dd>
                  </div>
                  <div class="pt-selected-template-meta-item">
                    <dt>厂家</dt>
                    <dd>{{ selectedPackingTemplate.manufacturerName || '-' }}</dd>
                  </div>
                  <div class="pt-selected-template-meta-item">
                    <dt>每包数量</dt>
                    <dd>{{ selectedPackingTemplate.quantity }} {{ selectedPackingTemplate.unit }}/包</dd>
                  </div>
                </dl>
              </div>
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">库房</span>
              <select v-model="packingForm.warehouseName" class="pt-select">
                <option
                  v-for="item in warehouses"
                  :key="item.warehouseName"
                  :value="item.warehouseName"
                >
                  {{ item.warehouseName }}
                </option>
              </select>
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">打包数量</span>
              <input
                v-model.number="packingForm.packageCount"
                type="number"
                min="1"
                class="pt-input"
              />
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">备注</span>
              <input
                v-model="packingForm.remark"
                placeholder="任务说明"
                class="pt-input"
              />
            </label>
            <div class="pt-form-actions">
              <button
                v-if="authStore.hasPermission('quota-packing-task:terminate')"
                type="button"
                class="pt-btn pt-btn-ghost"
                :disabled="tasks.length === 0"
                :title="tasks.length === 0 ? '当前没有可终止的打包任务' : '按任务号终止打包任务'"
                @click="terminateTaskByTaskNo"
              >
                <Ban :size="14" />
                <span>终止任务</span>
              </button>
              <button
                v-if="authStore.hasPermission('quota-packing-task:create')"
                type="button"
                class="pt-btn pt-btn-primary"
                :disabled="taskLoading || !packingForm.templateCode"
                :title="!packingForm.templateCode ? '请先选择打包模板' : '创建打包任务'"
                @click="submitPackingTask"
              >
                <Save :size="14" />
                <span>{{ taskLoading ? '创建中...' : '新建任务' }}</span>
              </button>
            </div>
          </div>

          <!-- 成功提示就地显示；失败统一用 ElMessage 弹出，不占用页面版面 -->
          <div
            v-if="packingFeedback"
            class="pt-feedback pt-feedback-success"
          >
            <CheckCircle2 :size="14" />
            <span>{{ packingFeedback }}</span>
          </div>

          <!-- 列表标题条 -->
          <div class="pt-card-header pt-card-header-list">
            <div class="pt-card-header-left">
              <span class="pt-card-header-icon">
                <PackageCheck :size="14" />
              </span>
              <span class="pt-card-header-title">打包任务列表</span>
              <span class="pt-card-header-desc">待确认与已确认的打包任务，支持确认、重算、取消、终止、预占明细</span>
            </div>
            <div class="pt-card-header-right">
              <span class="pt-chip">共 {{ quotaPagination.tasks.total }} 条</span>
            </div>
          </div>

          <!-- 表格 -->
          <div class="pt-table-scroll">
            <table class="pt-table">
              <colgroup>
                <col style="width: 150px" />     <!-- 任务号 -->
                <col style="width: 90px" />      <!-- 状态 -->
                <col style="width: 150px" />     <!-- 模板 -->
                <col style="width: 110px" />     <!-- 库房 -->
                <col style="width: 150px" />     <!-- 商品 -->
                <col style="width: 70px" />      <!-- 包数 -->
                <col style="width: 80px" />      <!-- 每包数量 -->
                <col style="width: 110px" />     <!-- 计划扣减散货 -->
                <col style="width: 110px" />     <!-- 预占散货 -->
                <col style="width: 150px" />     <!-- 创建时间 -->
                <col style="width: 360px" />     <!-- 操作 -->
              </colgroup>
              <thead>
                <tr>
                  <th>任务号</th>
                  <th>状态</th>
                  <th>模板</th>
                  <th>库房</th>
                  <th>商品</th>
                  <th>包数</th>
                  <th>每包数量</th>
                  <th>计划扣减散货</th>
                  <th>预占散货</th>
                  <th>创建时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <TableStateRow
                  v-if="loading"
                  :colspan="11"
                  state="loading"
                  message="正在加载打包任务..."
                />
                <TableStateRow
                  v-else-if="tasks.length === 0"
                  :colspan="11"
                  message="暂无符合条件的打包任务"
                />
                <tr
                  v-for="row in tasks"
                  v-else
                  :key="row.taskNo"
                >
                  <td>{{ row.taskNo }}</td>
                  <td>
                    <span
                      class="pt-badge"
                      :class="{
                        'pt-badge-green': row.status === 'confirmed',
                        'pt-badge-gray': row.status === 'cancelled' || row.status === 'terminated',
                        'pt-badge-orange': row.status === 'need_recalculate',
                        'pt-badge-blue': row.status === 'pending_confirm',
                      }"
                    >
                      {{ taskStatusText[row.status] || formatStatusText(row.status) }}
                    </span>
                  </td>
                  <td>{{ row.templateName }}</td>
                  <td>{{ row.warehouseName }}</td>
                  <td>{{ row.productName }}</td>
                  <td>{{ row.packageCount }}</td>
                  <td>{{ row.packageQuantity }}</td>
                  <td>{{ row.plannedLooseQty }}</td>
                  <td>{{ row.reservedLooseQty }}</td>
                  <td>{{ row.createTime }}</td>
                  <td class="pt-cell-actions">
                    <div class="pt-row-actions">
                      <button
                        v-if="row.status === 'pending_confirm' && authStore.hasPermission('quota-packing-task:confirm')"
                        class="pt-btn-text"
                        type="button"
                        @click="approveTask(row)"
                      >
                        <CheckCircle2 :size="13" />
                        确认
                      </button>
                      <button
                        v-if="(row.status === 'pending_confirm' || row.status === 'need_recalculate') && authStore.hasPermission('quota-packing-task:recalculate')"
                        class="pt-btn-text"
                        type="button"
                        @click="recalculateTask(row)"
                      >
                        <RefreshCw :size="13" />
                        重算
                      </button>
                      <button
                        v-if="(row.status === 'pending_confirm' || row.status === 'need_recalculate') && authStore.hasPermission('quota-packing-task:cancel')"
                        class="pt-btn-text pt-btn-text-danger"
                        type="button"
                        @click="cancelTask(row)"
                      >
                        <X :size="13" />
                        取消
                      </button>
                      <button
                        v-if="(row.status === 'pending_confirm' || row.status === 'need_recalculate' || row.status === 'confirmed') && authStore.hasPermission('quota-packing-task:terminate')"
                        class="pt-btn-text pt-btn-text-danger"
                        type="button"
                        @click="terminateTask(row)"
                      >
                        <Ban :size="13" />
                        终止
                      </button>
                      <button
                        class="pt-btn-text"
                        type="button"
                        @click="viewTaskReservations(row)"
                      >
                        <ClipboardList :size="13" />
                        预占明细
                      </button>
                      <span
                        v-if="row.status !== 'pending_confirm' && row.status !== 'need_recalculate'"
                        class="pt-row-meta"
                      >{{ row.confirmTime || row.cancelTime || '-' }}</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <PaginationControls
            :page="quotaPagination.tasks.page"
            :size="quotaPagination.tasks.size"
            :total="quotaPagination.tasks.total"
            :loading="loading"
            @change-page="changeQuotaPage('tasks', $event)"
            @change-size="changeQuotaPageSize('tasks', $event)"
          />
        </div>

        <!-- 分配卡片 -->
        <div class="pt-card">
          <div class="pt-card-header">
            <div class="pt-card-header-left">
              <span class="pt-card-header-icon">
                <PackageCheck :size="14" />
              </span>
              <span class="pt-card-header-title">按验收单分配散货</span>
            </div>
            <div class="pt-card-header-desc-inline">
              分配数量需为每包数量的整数倍（自动增加打包数量）；部分分配后验收单剩余量保持散货库存
            </div>
          </div>
          <div v-if="authStore.hasPermission('quota-packing-task:create')" class="pt-form-grid pt-form-grid-3">
            <label class="pt-form-field">
              <span class="pt-form-label">打包任务</span>
              <select v-model="allocateTaskNo" class="pt-select">
                <option value="">请选择待确认打包任务</option>
                <option
                  v-for="task in tasks"
                  :key="task.taskNo"
                  :value="task.taskNo"
                >
                  {{ task.taskNo }}（{{ task.productName }}，已预占 {{ task.reservedLooseQty }}）
                </option>
              </select>
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">验收单号</span>
              <input
                v-model.trim="receivingNo"
                placeholder="输入验收单号"
                class="pt-input"
                @keydown.enter.prevent="searchReceivingLooseStock"
              />
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">分配数量</span>
              <input
                v-model.number="allocateQty"
                type="number"
                min="0"
                placeholder="留空为全部"
                class="pt-input"
              />
            </label>
            <div class="pt-form-actions">
              <button
                class="pt-btn pt-btn-ghost"
                type="button"
                @click="searchReceivingLooseStock"
              >
                <Search :size="14" />
                <span>查询散货</span>
              </button>
              <button
                class="pt-btn"
                type="button"
                :disabled="!allocateTaskNo || !receivingNo || !looseStockRows.length"
                @click="doAllocateFromReceiving(false)"
              >
                分配
              </button>
              <button
                class="pt-btn pt-btn-primary"
                type="button"
                :disabled="!allocateTaskNo || !receivingNo || !looseStockRows.length"
                @click="doAllocateFromReceiving(true)"
              >
                全部打包分配
              </button>
            </div>
          </div>

          <!-- 散货批次 -->
          <div v-if="looseStockRows.length" class="pt-sub-group">
            <div class="pt-sub-group-header">
              <span class="pt-sub-group-title">
                <ClipboardList :size="13" />
                散货批次
              </span>
              <span class="pt-chip">共 {{ looseStockRows.length }} 条</span>
            </div>
            <div class="pt-table-scroll">
              <table class="pt-table pt-table-compact">
                <thead>
                  <tr>
                    <th>商品</th>
                    <th>系统批号</th>
                    <th>生产批次</th>
                    <th>可用散货</th>
                    <th>批次单价</th>
                    <th>单位</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(row, index) in looseStockRows"
                    :key="index"
                  >
                    <td>{{ row.productName }}（{{ row.productCode }}）</td>
                    <td>{{ row.systemBatchNo }}</td>
                    <td>{{ row.productionBatchNo || '-' }}</td>
                    <td>{{ row.availableQty }}</td>
                    <td>{{ row.unitPrice }}</td>
                    <td>{{ row.unit }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- 已分配记录 -->
          <div v-if="receivingAllocations.length" class="pt-sub-group">
            <div class="pt-sub-group-header">
              <span class="pt-sub-group-title">
                <ClipboardList :size="13" />
                已分配记录
              </span>
              <span class="pt-chip">共 {{ receivingAllocations.length }} 条</span>
            </div>
            <div class="pt-table-scroll">
              <table class="pt-table pt-table-compact">
                <thead>
                  <tr>
                    <th>验收单号</th>
                    <th>系统批号</th>
                    <th>生产批次</th>
                    <th>商品</th>
                    <th>已分配</th>
                    <th>单价</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in receivingAllocations"
                    :key="String(row.reservationId)"
                  >
                    <td>{{ row.receivingNo }}</td>
                    <td>{{ row.systemBatchNo }}</td>
                    <td>{{ row.productionBatchNo || '-' }}</td>
                    <td>{{ row.productName }}（{{ row.productCode }}）</td>
                    <td>{{ row.reservedQty }}</td>
                    <td>{{ row.unitPrice }}</td>
                    <td>{{ row.status }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- 预占明细弹窗 -->
        <el-dialog
          :model-value="Boolean(selectedTaskNo)"
          title="预占明细"
          width="min(1000px, 94vw)"
          append-to-body
          @close="selectedTaskNo = ''"
        >
          <div class="reservation-panel">
            <div class="section-title compact">
              <ClipboardList :size="18" />
              <h3>打包任务：{{ selectedTaskNo }}</h3>
            </div>
            <div class="table-scroll">
              <table class="master-table compact-table">
                <thead>
                  <tr>
                    <th>系统批次</th>
                    <th>生产批号</th>
                    <th>效期</th>
                    <th>预占数量</th>
                    <th>单价</th>
                    <th>预占时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in taskReservations"
                    :key="String(row.reservationId)"
                  >
                    <td>{{ row.systemBatchNo }}</td>
                    <td>{{ row.productionBatchNo || '-' }}</td>
                    <td>{{ row.expireDate || '-' }}</td>
                    <td>{{ row.reservedQty }}</td>
                    <td>{{ row.unitPrice }}</td>
                    <td>{{ row.createTime }}</td>
                  </tr>
                  <TableStateRow
                    v-if="taskReservations.length === 0"
                    :colspan="6"
                    message="暂无预占明细"
                  />
                </tbody>
              </table>
            </div>
          </div>
        </el-dialog>
      </section>

      <section v-if="packageSection === 'quota-label-unpack'" class="lbl-panel">
        <div v-if="packingFeedback" class="lbl-banner lbl-banner-ok">
          <CheckCircle2 :size="15" />
          <span>{{ packingFeedback }}</span>
        </div>

        <!-- 工具条：标题 + 状态概览 + 操作 -->
        <header class="lbl-toolbar">
          <div class="lbl-toolbar-lead">
            <span class="lbl-toolbar-icon"><ArchiveRestore :size="18" /></span>
            <div class="lbl-toolbar-text">
              <h3>定数包标签与解包</h3>
              <p>打印后可解包退回散货；已解包标签不可复用</p>
            </div>
          </div>

          <div class="lbl-stats">
            <div class="lbl-stat">
              <span class="lbl-stat-label">待打印</span>
              <strong class="lbl-stat-value is-pending">{{ labelStat.pending_print }}</strong>
            </div>
            <div class="lbl-stat">
              <span class="lbl-stat-label">可用</span>
              <strong class="lbl-stat-value is-available">{{ labelStat.available }}</strong>
            </div>
            <div class="lbl-stat">
              <span class="lbl-stat-label">已解包</span>
              <strong class="lbl-stat-value is-void">{{ labelStat.void }}</strong>
            </div>
          </div>
        </header>

        <!-- 筛选条 -->
        <div class="lbl-filter">
          <label class="lbl-field">
            <span>标签号</span>
            <input v-model="query.labelNo" placeholder="DYYYYMMDD000001" />
          </label>
          <label class="lbl-field">
            <span>模板编码</span>
            <input v-model="query.templateCode" placeholder="模糊查询模板" />
          </label>
          <label class="lbl-field">
            <span>商品编码</span>
            <input v-model="query.productCode" placeholder="模糊查询商品编码" />
          </label>
          <label class="lbl-field">
            <span>商品名称</span>
            <input v-model="query.productName" placeholder="模糊查询商品" />
          </label>
          <label class="lbl-field">
            <span>科室</span>
            <input v-model="query.deptName" placeholder="模糊查询科室" />
          </label>
          <div class="lbl-filter-actions">
            <button class="lbl-btn lbl-btn-primary" type="button" :disabled="loading" @click="loadData">
              <Search :size="16" />
              查询
            </button>
            <button class="lbl-btn" type="button" :disabled="loading || !hasLabelQuery" @click="resetLabelQuery">
              <RefreshCw :size="15" />
              重置
            </button>
          </div>
        </div>

        <!-- 标签列表 -->
        <div class="lbl-scroll">
          <table class="lbl-table">
            <thead>
              <tr>
                <th class="lbl-col-no">标签号</th>
                <th class="lbl-col-status">状态</th>
                <th>模板</th>
                <th>库房</th>
                <th>商品</th>
                <th class="lbl-col-num">包数量</th>
                <th class="lbl-col-source">来源批次</th>
                <th class="lbl-col-print">打印次数</th>
                <th class="lbl-col-time">生成时间</th>
                <th class="lbl-col-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="10" state="loading" message="正在加载定数包标签..." />
              <tr v-for="row in labels" :key="row.labelNo" class="lbl-row">
                <td class="lbl-col-no">
                  <span class="lbl-no">{{ row.labelNo }}</span>
                </td>
                <td class="lbl-col-status">
                  <span
                    class="lbl-status"
                    :class="{
                      'is-pending': row.status === 'pending_print',
                      'is-available': row.status === 'available',
                      'is-void': row.status === 'void'
                    }"
                  >
                    <i class="lbl-status-dot" />
                    {{ labelStatusText[row.status] || formatStatusText(row.status) }}
                  </span>
                </td>
                <td>
                  <div class="lbl-main">{{ row.templateName || '-' }}</div>
                  <div class="lbl-sub">{{ row.templateCode || '-' }}</div>
                </td>
                <td>{{ row.warehouseName || '-' }}</td>
                <td>
                  <div class="lbl-main">{{ row.productName || '-' }}</div>
                  <div class="lbl-sub lbl-mono">{{ row.productCode || '-' }}</div>
                </td>
                <td class="lbl-col-num">
                  <span class="lbl-qty">{{ row.packageQuantity }}</span>
                </td>
                <td class="lbl-col-source">
                  <span v-if="row.sourceBatches" class="lbl-source" :title="row.sourceBatches">{{ row.sourceBatches }}</span>
                  <span v-else class="lbl-sub">-</span>
                </td>
                <td class="lbl-col-print">
                  <span class="lbl-print" :class="{ 'is-zero': !row.printCount }">{{ row.printCount ?? 0 }}</span>
                </td>
                <td class="lbl-col-time lbl-mono">{{ row.createTime || '-' }}</td>
                <td class="lbl-col-actions">
                  <div class="lbl-actions">
                    <button
                      v-if="(row.status === 'pending_print' || row.status === 'available') && authStore.hasPermission('quota-package-label:print')"
                      class="lbl-act"
                      type="button"
                      @click="printLabel(row)"
                    >
                      <Printer :size="14" />
                      {{ row.status === 'pending_print' ? '打印' : '重打' }}
                    </button>
                    <button
                      v-if="row.status === 'available' && authStore.hasPermission('quota-label-unpack:write')"
                      class="lbl-act lbl-act-danger"
                      type="button"
                      @click="unpackLabel(row)"
                    >
                      <ArchiveRestore :size="14" />
                      解包
                    </button>
                    <span v-if="row.status === 'void'" class="lbl-act-none">已解包</span>
                    <span
                      v-if="!canPrintLabel(row) && row.status !== 'available' && row.status !== 'void'"
                      class="lbl-act-none"
                    >
                      无操作
                    </span>
                  </div>
                </td>
              </tr>
              <TableStateRow v-if="!loading && labels.length === 0" :colspan="10" message="暂无已生成的定数包标签" />
            </tbody>
          </table>
        </div>

        <PaginationControls
          :page="quotaPagination.labels.page"
          :size="quotaPagination.labels.size"
          :total="quotaPagination.labels.total"
          :loading="loading"
          @change-page="changeQuotaPage('labels', $event)"
          @change-size="changeQuotaPageSize('labels', $event)"
        />
      </section>

      <section v-if="packageSection === 'quota-package-events'" class="evt-panel">
        <header class="evt-toolbar">
          <div class="evt-toolbar-lead">
            <span class="evt-toolbar-icon"><History :size="18" /></span>
            <div class="evt-toolbar-text">
              <h3>定数包事件</h3>
              <p>标签全生命周期追溯记录，仅供追溯不可编辑</p>
            </div>
          </div>
          <div class="evt-stats">
            <div class="evt-stat">
              <span class="evt-stat-label">本页事件</span>
              <strong class="evt-stat-value">{{ events.length }}</strong>
            </div>
            <div class="evt-stat">
              <span class="evt-stat-label">累计</span>
              <strong class="evt-stat-value">{{ quotaPagination.events.total }}</strong>
            </div>
          </div>
        </header>

        <div class="evt-filter">
          <label class="evt-field">
            <span>事件号</span>
            <input v-model="query.eventNo" placeholder="模糊查询事件号" />
          </label>
          <label class="evt-field">
            <span>标签号</span>
            <input v-model="query.labelNo" placeholder="模糊查询标签号" />
          </label>
          <label class="evt-field evt-field-wide">
            <span>事件类型</span>
            <select v-model="query.eventType">
              <option value="">全部类型</option>
              <option v-for="option in eventTypeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <div class="evt-filter-actions">
            <button class="evt-btn evt-btn-primary" type="button" :disabled="loading" @click="onEventQuerySubmit">
              <Search :size="16" />
              查询
            </button>
            <button class="evt-btn" type="button" :disabled="loading || !hasEventQuery" @click="resetEventQuery">
              <RefreshCw :size="15" />
              重置
            </button>
          </div>
        </div>

        <div class="evt-scroll">
          <table class="evt-table">
            <thead>
              <tr>
                <th class="evt-col-no">事件号</th>
                <th class="evt-col-label">标签号</th>
                <th class="evt-col-type">事件类型</th>
                <th class="evt-col-flow">状态变更</th>
                <th class="evt-col-qty">数量变化</th>
                <th class="evt-col-time">发生时间</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="7" state="loading" message="正在加载定数包事件..." />
              <TableStateRow v-else-if="events.length === 0" :colspan="7" message="暂无符合条件的定数包事件" />
              <tr v-for="row in events" :key="String(row.eventNo)" class="evt-row">
                <td class="evt-col-no">
                  <span class="evt-no">{{ row.eventNo }}</span>
                </td>
                <td class="evt-col-label">
                  <span v-if="row.labelNo" class="evt-label-no">{{ row.labelNo }}</span>
                  <span v-else class="evt-sub">-</span>
                </td>
                <td class="evt-col-type">
                  <span class="evt-type" :class="eventTone(row.eventType)">
                    <i class="evt-type-dot" />
                    {{ formatBusinessText(row.eventType) }}
                  </span>
                </td>
                <td class="evt-col-flow">
                  <div class="evt-flow">
                    <span class="evt-flow-node" :class="{ 'is-empty': !row.statusBefore }">
                      {{ formatStatusText(row.statusBefore) }}
                    </span>
                    <ArrowRight :size="13" class="evt-flow-arrow" />
                    <span
                      class="evt-flow-node is-after"
                      :class="{ 'is-void': row.statusAfter === 'void', 'is-empty': !row.statusAfter }"
                    >
                      {{ formatStatusText(row.statusAfter) }}
                    </span>
                  </div>
                </td>
                <td class="evt-col-qty">
                  <span v-if="hasQtyChange(row.qtyChange)" class="evt-qty" :class="qtyTone(row.qtyChange)">
                    {{ formatQtyChange(row.qtyChange) }}
                  </span>
                  <span v-else class="evt-sub">无变化</span>
                </td>
                <td class="evt-col-time evt-mono">{{ row.eventTime || '-' }}</td>
                <td class="evt-remark" :title="String(row.remark ?? '')">
                  {{ formatRemarkText(row.remark) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <PaginationControls
          :page="quotaPagination.events.page"
          :size="quotaPagination.events.size"
          :total="quotaPagination.events.total"
          :loading="loading"
          @change-page="changeQuotaPage('events', $event)"
          @change-size="changeQuotaPageSize('events', $event)"
        />
      </section>

      <section v-if="packageSection === 'packable-loose-snapshot'" class="snp-panel">
        <header class="snp-toolbar">
          <div class="snp-toolbar-lead">
            <span class="snp-toolbar-icon"><ShieldCheck :size="18" /></span>
            <div class="snp-toolbar-text">
              <h3>可打包散货快照</h3>
              <p>仅统计启用定数管理、非高值、非冷链的一级/中心库可用散货</p>
            </div>
          </div>
          <div class="snp-stats">
            <div class="snp-stat">
              <span class="snp-stat-label">本页批次</span>
              <strong class="snp-stat-value">{{ candidates.length }}</strong>
            </div>
            <div class="snp-stat">
              <span class="snp-stat-label">累计批次</span>
              <strong class="snp-stat-value">{{ quotaPagination.candidates.total }}</strong>
            </div>
            <div class="snp-stat">
              <span class="snp-stat-label">本页可用量</span>
              <strong class="snp-stat-value">{{ pageAvailableQty }}</strong>
            </div>
          </div>
        </header>

        <div class="snp-filter">
          <label class="snp-field">
            <span>商品名称</span>
            <input v-model="query.productName" placeholder="模糊查询商品名称" @keyup.enter="onSnapshotQuery" />
          </label>
          <label class="snp-field">
            <span>商品编码</span>
            <input v-model="query.productCode" placeholder="模糊查询商品编码" @keyup.enter="onSnapshotQuery" />
          </label>
          <label class="snp-field">
            <span>库房</span>
            <input v-model="query.warehouseName" placeholder="模糊查询库房" @keyup.enter="onSnapshotQuery" />
          </label>
          <div class="snp-filter-actions">
            <button class="snp-btn snp-btn-primary" type="button" :disabled="loading" @click="onSnapshotQuery">
              <Search :size="16" />
              查询
            </button>
            <button class="snp-btn" type="button" :disabled="loading || !hasSnapshotQuery" @click="resetSnapshotQuery">
              <RefreshCw :size="15" />
              重置
            </button>
            <details class="snp-columns" :open="columnPanelOpen" @toggle="onColumnPanelToggle">
              <summary class="snp-btn">
                <Columns3 :size="15" />
                显示列
                <span class="snp-columns-count">{{ visibleSnapshotColumns.length }}/{{ snapshotColumns.length }}</span>
              </summary>
              <div class="snp-columns-menu">
                <label v-for="column in snapshotColumns" :key="column.key" class="snp-columns-item">
                  <input
                    v-model="column.visible"
                    type="checkbox"
                    :disabled="column.visible && visibleSnapshotColumns.length === 1"
                  />
                  <span>{{ column.label }}</span>
                </label>
              </div>
            </details>
          </div>
        </div>

        <div class="snp-scroll">
          <table class="snp-table">
            <thead>
              <tr>
                <th
                  v-for="column in visibleSnapshotColumns"
                  :key="column.key"
                  :class="snpColumnClass(column.key)"
                >
                  {{ column.label }}
                </th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow
                v-if="loading"
                :colspan="visibleSnapshotColumns.length"
                state="loading"
                message="正在加载可打包散货..."
              />
              <TableStateRow
                v-else-if="candidates.length === 0"
                :colspan="visibleSnapshotColumns.length"
                message="当前没有可打包散货"
              />
              <tr v-for="row in candidates" :key="`${row.warehouseName}-${row.productCode}-${row.systemBatchNo}`" class="snp-row">
                <td
                  v-for="column in visibleSnapshotColumns"
                  :key="column.key"
                  :class="snpColumnClass(column.key)"
                >
                  <template v-if="column.key === 'productName'">
                    <div class="snp-main">{{ row.productName || '-' }}</div>
                    <div v-if="row.productCode" class="snp-sub snp-mono">{{ row.productCode }}</div>
                  </template>

                  <template v-else-if="column.key === 'systemBatchNo'">
                    <span class="snp-batch">{{ row.systemBatchNo || '-' }}</span>
                  </template>

                  <template v-else-if="column.key === 'expireDate'">
                    <span
                      v-if="row.expireDate"
                      class="snp-expire"
                      :class="expiryTone(row.expireDate)"
                      :title="expiryTitle(row.expireDate)"
                    >
                      {{ row.expireDate }}
                    </span>
                    <span v-else class="snp-sub">-</span>
                  </template>

                  <template v-else-if="column.key === 'batchUnitPrice'">
                    <span class="snp-price">¥ {{ formatSnapshotPrice(row.batchUnitPrice) }}</span>
                  </template>

                  <template v-else-if="column.key === 'availableQty'">
                    <span class="snp-qty">{{ formatSnapshotQty(row.availableQty) }}</span>
                  </template>

                  <template v-else-if="column.key === 'productionBatchNo'">
                    <span class="snp-mono">{{ row.productionBatchNo || '-' }}</span>
                  </template>

                  <template v-else>
                    {{ row[column.key] ?? '-' }}
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <PaginationControls
          :page="quotaPagination.candidates.page"
          :size="quotaPagination.candidates.size"
          :total="quotaPagination.candidates.total"
          :loading="loading"
          @change-page="changeQuotaPage('candidates', $event)"
          @change-size="changeQuotaPageSize('candidates', $event)"
        />
      </section>
    </template>

    <template v-else>
      <section class="saf-card">
        <div v-if="packingFeedback" class="saf-banner">
          <CheckCircle2 :size="15" />
          <span>{{ packingFeedback }}</span>
        </div>

        <div class="saf-toolbar">
          <div class="saf-toolbar-lead">
            <span class="saf-toolbar-icon"><ShieldCheck :size="18" /></span>
            <div class="saf-toolbar-text">
              <h3>定数安全量</h3>
              <p>按科室与商品维护定数包上下限，用于补货触发判断</p>
            </div>
          </div>
          <div class="saf-toolbar-actions">
            <button
              v-if="authStore.hasPermission('quota-safety-stock:write')"
              class="saf-btn saf-btn-primary"
              type="button"
              @click="openSafetyDialog()"
            >
              <Plus :size="16" />
              新增维护
            </button>
            <button
              v-if="authStore.hasPermission('quota-safety-stock:write')"
              class="saf-btn"
              type="button"
              :disabled="safetyImporting"
              @click="triggerSafetyImport"
            >
              <Upload :size="16" />
              {{ safetyImporting ? '导入中' : '导入' }}
            </button>
            <details class="saf-import">
              <summary class="saf-btn">
                <FileDown :size="16" />
                导入模板
              </summary>
              <div class="saf-import-menu">
                <button type="button" @click="downloadSafetyImportFile">
                  <FileDown :size="16" />
                  下载导入模板
                </button>
              </div>
            </details>
            <button class="saf-btn" type="button" @click="exportSafetyRows">
              <Download :size="16" />
              导出
            </button>
            <input
              ref="safetyImportInput"
              class="saf-file-input"
              type="file"
              accept=".csv,text/csv"
              @change="handleSafetyImport"
            />
          </div>
          <div class="saf-toolbar-info">
            <span class="saf-chip">共 {{ quotaPagination.safety.total }} 条</span>
          </div>
        </div>

        <div class="saf-filter">
          <label class="saf-field">
            <span>科室名称</span>
            <input v-model="query.deptName" placeholder="模糊查询科室" @keyup.enter="onSafetyQuery" />
          </label>
          <label class="saf-field">
            <span>商品编码</span>
            <input v-model="query.productCode" placeholder="模糊查询商品编码" @keyup.enter="onSafetyQuery" />
          </label>
          <label class="saf-field">
            <span>商品名称</span>
            <input v-model="query.productName" placeholder="模糊查询商品名称" @keyup.enter="onSafetyQuery" />
          </label>
          <div class="saf-filter-actions">
            <button class="saf-btn" type="button" :disabled="loading || !hasSafetyQuery" @click="resetSafetyQuery">
              <RefreshCw :size="15" />
              重置
            </button>
            <button class="saf-btn saf-btn-primary" type="button" :disabled="loading" @click="onSafetyQuery">
              <Search :size="16" />
              查询
            </button>
          </div>
        </div>

        <div class="saf-scroll">
          <table class="saf-table">
            <thead>
              <tr>
                <th class="saf-col-dept">科室</th>
                <th class="saf-col-code">商品编码</th>
                <th>商品名称</th>
                <th class="saf-col-code">模板编码</th>
                <th>模板名称</th>
                <th class="saf-col-range">安全区间</th>
                <th class="saf-col-num">下限</th>
                <th class="saf-col-num">上限</th>
                <th class="saf-col-status">状态</th>
                <th class="saf-col-time">更新时间</th>
                <th class="saf-col-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <TableStateRow v-if="loading" :colspan="11" state="loading" message="正在加载安全量配置..." />
              <TableStateRow v-else-if="safetyRows.length === 0" :colspan="11" message="暂无符合条件的安全量配置" />
              <tr v-for="row in safetyRows" :key="row.safetyId" class="saf-row">
                <td class="saf-col-dept">
                  <span class="saf-dept">{{ row.deptName || '-' }}</span>
                </td>
                <td class="saf-col-code">
                  <span class="saf-code">{{ row.productCode }}</span>
                </td>
                <td>
                  <div class="saf-main">{{ row.productName || '-' }}</div>
                </td>
                <td class="saf-col-code">
                  <span v-if="row.templateCode && row.templateCode !== '-'" class="saf-code">
                    {{ row.templateCode }}
                  </span>
                  <span v-else class="saf-sub">未指定</span>
                </td>
                <td>{{ row.templateName && row.templateName !== '-' ? row.templateName : '-' }}</td>
                <td class="saf-col-range">
                  <div class="saf-range" :title="`安全下限 ${row.minQty}，安全上限 ${row.maxQty}`">
                    <span class="saf-range-track">
                      <i class="saf-range-fill" :style="safetyRangeStyle(row)" />
                    </span>
                    <span class="saf-range-text">
                      {{ formatSafetyQty(row.minQty) }} – {{ formatSafetyQty(row.maxQty) }}
                    </span>
                  </div>
                </td>
                <td class="saf-col-num">
                  <span class="saf-qty">{{ formatSafetyQty(row.minQty) }}</span>
                </td>
                <td class="saf-col-num">
                  <span class="saf-qty is-max">{{ formatSafetyQty(row.maxQty) }}</span>
                </td>
                <td class="saf-col-status">
                  <span class="saf-status" :class="isSafetyEnabled(row.status) ? 'is-enabled' : 'is-disabled'">
                    <i class="saf-status-dot" />
                    {{ formatStatusText(row.status) }}
                  </span>
                </td>
                <td class="saf-col-time saf-mono">{{ row.updateTime || '-' }}</td>
                <td class="saf-col-actions">
                  <div class="saf-actions">
                    <button
                      v-if="authStore.hasPermission('quota-safety-stock:write')"
                      class="saf-act"
                      type="button"
                      @click="openSafetyDialog(row)"
                    >
                      <Edit3 :size="14" />
                      编辑
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <PaginationControls
          :page="quotaPagination.safety.page"
          :size="quotaPagination.safety.size"
          :total="quotaPagination.safety.total"
          :loading="loading"
          @change-page="changeQuotaPage('safety', $event)"
          @change-size="changeQuotaPageSize('safety', $event)"
        />
      </section>
    </template>

    <QuotaSafetyDialog
      v-if="safetyDialogOpen"
      :mode="safetyDialogMode"
      :safety-form="safetyForm"
      :catalog-query="safetyCatalogQuery"
      :templates="pagedSafetyTemplates"
      :warehouses="safetyWarehouseOptions"
      :filtered-count="filteredSafetyTemplates.length"
      :page="safetyCatalogPagination.page"
      :size="safetyCatalogPagination.size"
      :loading="loading"
      @close="closeSafetyDialog"
      @submit="submitSafety"
      @select-template="selectSafetyTemplate"
      @select-warehouse="selectSafetyWarehouse"
      @change-page="changeSafetyCatalogPage"
      @change-size="changeSafetyCatalogPageSize"
    />

    <Teleport to="body">
    <div v-if="templateDialogOpen" class="attachment-preview-mask" @click.self="templateDialogOpen = false">
      <section class="quota-template-dialog" role="dialog" aria-modal="true">
        <header class="qtd-dialog-header">
          <div class="qtd-dialog-title">
            <p>定数包模板维护</p>
            <h3>{{ templateDialogMode === 'create' ? '新增定数包模板' : '修改定数包模板' }}</h3>
            <span v-if="templateDialogMode !== 'create'" class="qtd-dialog-tag">编辑模式</span>
          </div>
          <button class="qtd-btn qtd-btn-ghost" type="button" aria-label="关闭" @click="templateDialogOpen = false">
            <X :size="18" />
          </button>
        </header>
        <div class="qtd-dialog-body">
          <div class="qtd-form-grid">
            <label class="qtd-field">
              <span>模板编码</span>
              <input
                v-model="templateForm.templateCode"
                readonly
                :placeholder="templateDialogMode === 'create' ? '商品编码 + 001 起自动生成' : ''"
              />
            </label>
            <label class="qtd-field qtd-field-wide">
              <span>定数包名称</span>
              <input
                readonly
                :value="templateForm.templateName || ((selectedProductInfo?.productName || '') + '定数包')"
                placeholder="选择商品后自动生成：商品名称 + 定数包"
              />
            </label>
            <label class="qtd-field qtd-field-wide">
              <span>商品编码</span>
              <div class="qtd-input-with-btn">
                <input v-model="templateForm.productCode" readonly placeholder="点击右侧按钮选择已启用定数管理的商品" />
                <button class="qtd-btn qtd-btn-info qtd-btn-icon" type="button" title="选择商品" @click="openProductSelector">
                  <Search :size="16" />
                </button>
              </div>
            </label>

            <template v-if="selectedProductInfo">
              <label class="qtd-field">
                <span>商品名称</span>
                <input :value="selectedProductInfo.productName" readonly />
              </label>
              <label class="qtd-field">
                <span>规格型号</span>
                <input :value="selectedProductInfo.specModel" readonly />
              </label>
              <label class="qtd-field">
                <span>品牌</span>
                <input :value="selectedProductInfo.brand || '-'" readonly />
              </label>
              <label class="qtd-field">
                <span>生产厂家</span>
                <input :value="selectedProductInfo.manufacturerName" readonly />
              </label>
              <label class="qtd-field">
                <span>单价</span>
                <input :value="selectedProductInfo.purchasePrice ? '¥ ' + selectedProductInfo.purchasePrice : '-'" readonly />
              </label>
              <label class="qtd-field">
                <span>基本单位</span>
                <input :value="selectedProductInfo.unit" readonly />
              </label>
              <label class="qtd-field">
                <span>中包装数量</span>
                <input :value="selectedProductInfo.middlePackageQty || '-'" readonly />
              </label>
            </template>

            <label class="qtd-field">
              <span>包内数量</span>
              <input v-model.number="templateForm.quantity" type="number" min="1" />
            </label>
            <label class="qtd-field">
              <span>套包单位</span>
              <input value="包" readonly />
            </label>
          </div>
          <p v-if="templateError" class="qtd-error">{{ templateError }}</p>
          <div class="qtd-dialog-actions">
            <button class="qtd-btn qtd-btn-secondary" type="button" @click="templateDialogOpen = false">取消</button>
            <button class="qtd-btn qtd-btn-primary" type="button" @click="submitTemplate">
              <Save :size="16" />
              保存
            </button>
          </div>
        </div>
      </section>
    </div>
    </Teleport>

    <QuotaTemplateSelectorDialog
      v-model:open="templateSelectorOpen"
      v-model:show-all="showAllTemplates"
      :template-query="templateSelectorQuery"
      :templates="filteredPackingTemplates"
      @select="selectPackingTemplate"
    />

    <QuotaProductSelectorDialog
      v-model:open="productSelectorOpen"
      :loading="productLoading"
      :product-query="productQuery"
      :products="quotaProducts" :page="productPage" :size="productSize" :total="productTotal"
      @change-page="searchQuotaProducts($event)" @change-size="productSize = $event; searchQuotaProducts()"
      @search="searchQuotaProducts()"
      @select="selectProduct"
    />

    <SpdConfirmHost />
  </section>
</template>

<!-- =====================================================================
   打包任务确认 专属样式
   全部内联到本文件，不依赖任何外部样式表
   ===================================================================== -->
<style scoped>
.pt-page {
  /* 跟随全局主题色（theme.css → --fli-brand → --primary） */
  --pt-primary: var(--primary, var(--fli-brand, #0f766e));
  --pt-primary-dark: var(--primary-strong, var(--fli-brand-strong, #115e59));
  --pt-primary-soft: color-mix(in srgb, var(--pt-primary) 8%, #ffffff);
  --pt-primary-glow: color-mix(in srgb, var(--pt-primary) 15%, transparent);
  --pt-bg-page: #f5f7f8;
  --pt-bg-card: #ffffff;
  --pt-bg-muted: #f8faf9;
  --pt-border: color-mix(in srgb, var(--pt-primary) 14%, #e2e8e5);
  --pt-border-subtle: color-mix(in srgb, var(--pt-primary) 8%, #eef2f0);
  --pt-text: #1d2a35;
  --pt-text-secondary: #5a6b78;
  --pt-text-tertiary: #8a98a5;
  --pt-shadow: 0 1px 3px var(--pt-primary-glow), 0 1px 2px rgba(0, 0, 0, 0.04);
  --pt-shadow-md: 0 4px 14px var(--pt-primary-glow);
  --pt-radius: 8px;
  --pt-radius-sm: 4px;
}

/* ===== 容器 ===== */
.pt-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

/* ===== Card ===== */
.pt-card {
  background: var(--pt-bg-card);
  border: 1px solid var(--pt-border);
  border-radius: var(--pt-radius);
  box-shadow: var(--pt-shadow);
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.pt-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--pt-border-subtle);
  background: var(--pt-bg-card);
  flex-shrink: 0;
  flex-wrap: wrap;
}

.pt-card-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex: 1 1 auto;
  min-width: 0;
}

.pt-card-header-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: var(--pt-radius-sm);
  background: var(--pt-primary-soft);
  color: var(--pt-primary);
  flex: 0 0 22px;
}

.pt-card-header-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--pt-text);
}

.pt-card-header-desc {
  color: var(--pt-text-tertiary);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.4;
}

.pt-card-header-desc-inline {
  color: var(--pt-text-tertiary);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.4;
  flex: 0 1 auto;
  text-align: right;
}

.pt-card-header-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.pt-card-header-list {
  background: var(--pt-bg-muted);
}

/* ===== Form grid ===== */
.pt-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr)) auto;
  gap: 12px 16px;
  align-items: end;
  padding: 16px 18px;
  background: var(--pt-bg-muted);
  border-bottom: 1px solid var(--pt-border-subtle);
}

.pt-form-grid-3 {
  grid-template-columns: repeat(3, minmax(160px, 1fr)) auto;
}

.pt-form-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.pt-form-label {
  font-size: 12px;
  color: var(--pt-text-secondary);
  font-weight: 500;
}

.pt-form-required {
  color: #dc2626;
  font-style: normal;
  font-weight: 700;
}

/* ===== 模板选择器（整块可点击，明显入口）===== */
.pt-template-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  height: 32px;
  padding: 0 10px;
  border: 1px dashed var(--pt-border);
  border-radius: var(--pt-radius-sm);
  background: var(--pt-bg-card);
  color: var(--pt-text-secondary);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.18s ease;
  text-align: left;
}

.pt-template-picker > svg {
  flex: 0 0 14px;
  color: var(--pt-primary);
}

.pt-template-picker-text {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--pt-text-tertiary);
  font-weight: 500;
}

.pt-template-picker-code {
  flex: 0 0 auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--pt-text-tertiary);
  background: var(--pt-bg-muted);
  border: 1px solid var(--pt-border-subtle);
  border-radius: 3px;
  padding: 1px 6px;
}

.pt-template-picker.is-empty {
  border-color: var(--pt-primary);
  background: var(--pt-primary-soft);
}

.pt-template-picker.is-empty .pt-template-picker-text {
  color: var(--pt-primary);
  font-weight: 600;
}

.pt-template-picker:hover {
  border-color: var(--pt-primary);
  border-style: solid;
  background: var(--pt-primary-soft);
}

.pt-template-picker:not(.is-empty) {
  border-style: solid;
}

.pt-template-picker:not(.is-empty) .pt-template-picker-text {
  color: var(--pt-text);
  font-weight: 600;
}

.pt-template-picker:not(.is-empty) .pt-template-picker-code {
  color: var(--pt-primary);
  background: var(--pt-primary-soft);
  border-color: var(--pt-primary-glow);
}

/* ===== 加载失败（保留重试入口）===== */
.pt-load-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 6px;
  background: color-mix(in srgb, #dc2626 8%, #ffffff);
  color: #b91c1c;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid color-mix(in srgb, #dc2626 24%, #ffffff);
}

.pt-load-error > svg {
  flex: 0 0 auto;
}

.pt-load-error .btn-text {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}

/* ===== 表单内联反馈 ===== */
.pt-feedback {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 18px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
  border-bottom: 1px solid var(--pt-border-subtle);
}

.pt-feedback > svg {
  flex: 0 0 14px;
}

.pt-feedback-success {
  background: color-mix(in srgb, #16a34a 8%, #ffffff);
  color: #15803d;
}

.pt-feedback-error {
  background: color-mix(in srgb, #dc2626 8%, #ffffff);
  color: #b91c1c;
}

.pt-input,
.pt-select {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--pt-border);
  border-radius: var(--pt-radius-sm);
  background: var(--pt-bg-card);
  color: var(--pt-text);
  font-size: 13px;
  font-family: inherit;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.pt-select {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='10' height='6' viewBox='0 0 10 6'><path fill='%235a6b78' d='M0 0l5 6 5-6z'/></svg>");
  background-repeat: no-repeat;
  background-position: right 10px center;
  padding-right: 26px;
}

.pt-input:focus,
.pt-select:focus {
  outline: none;
  border-color: var(--pt-primary);
  box-shadow: 0 0 0 3px var(--pt-primary-glow);
}

.pt-input-icon-wrap {
  display: flex;
  align-items: stretch;
  width: 100%;
  min-width: 0;
  gap: 0;
}

.pt-input-icon-wrap .pt-input {
  flex: 1 1 auto;
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
  border-right: 0;
}

.pt-input-icon-btn {
  flex: 0 0 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--pt-border);
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
  border-top-right-radius: var(--pt-radius-sm);
  border-bottom-right-radius: var(--pt-radius-sm);
  background: var(--pt-bg-card);
  color: var(--pt-text-secondary);
  cursor: pointer;
  transition: all 0.18s ease;
}

.pt-input-icon-btn:hover {
  color: var(--pt-primary);
  border-color: var(--pt-primary);
  background: var(--pt-primary-soft);
}

.pt-form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
  grid-column: -2 / -1;
}

/* ===== Buttons ===== */
.pt-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--pt-border);
  background: var(--pt-bg-card);
  color: var(--pt-text);
  border-radius: var(--pt-radius-sm);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
  font-family: inherit;
}

.pt-btn:hover:not(:disabled) {
  border-color: var(--pt-primary);
  color: var(--pt-primary);
  background: var(--pt-primary-soft);
}

.pt-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pt-btn-primary {
  background: var(--pt-primary);
  border-color: var(--pt-primary);
  color: #ffffff;
  box-shadow: 0 1px 0 var(--pt-primary-glow) inset;
}

.pt-btn-primary:hover:not(:disabled) {
  background: var(--pt-primary-dark);
  border-color: var(--pt-primary-dark);
  color: #ffffff;
}

.pt-btn-ghost {
  background: transparent;
}

/* 已选模板单行信息（已废弃，样式保留为兼容占位） */
.pt-template-row {
  display: none;
}

.pt-template-row > svg {
  display: none;
}

.pt-template-row-label {
  display: none;
}

.pt-template-row-name {
  display: none;
}

.pt-template-row-sep {
  display: none;
}

/* ===== 已选模板卡片（替代原散落的 .pt-template-row 单行） ===== */
.pt-selected-template {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--pt-primary-glow);
  background: var(--pt-primary-soft);
  border-radius: var(--pt-radius-md);
  box-sizing: border-box;
}

.pt-selected-template-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.pt-selected-template-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.pt-selected-template-title > svg {
  color: var(--pt-primary);
  flex-shrink: 0;
}

.pt-selected-template-name {
  color: var(--pt-text);
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360px;
}

.pt-selected-template-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--pt-text-tertiary);
  background: var(--pt-bg);
  border: 1px solid var(--pt-border-subtle);
  border-radius: var(--pt-radius-sm);
  padding: 1px 6px;
}

.pt-selected-template-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.pt-selected-template-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px 18px;
  margin: 0;
  padding: 8px 10px;
  background: var(--pt-bg);
  border-radius: var(--pt-radius-sm);
  border: 1px dashed var(--pt-border-subtle);
}

.pt-selected-template-meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.pt-selected-template-meta-item dt {
  color: var(--pt-text-tertiary);
  font-size: 11px;
  font-weight: 500;
  flex: 0 0 auto;
  margin: 0;
}

.pt-selected-template-meta-item dd {
  color: var(--pt-text);
  font-size: 12px;
  font-weight: 500;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1 1 auto;
}

/* ===== Chip ===== */
.pt-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--pt-primary-soft);
  color: var(--pt-primary);
  border: 1px solid var(--pt-primary-glow);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

/* ===== Table ===== */
.pt-table-scroll {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  background: var(--pt-bg-card);
  scrollbar-width: thin;
  scrollbar-color: var(--pt-border) transparent;
}

.pt-table-scroll::-webkit-scrollbar {
  height: 6px;
}

.pt-table-scroll::-webkit-scrollbar-thumb {
  background: var(--pt-border);
  border-radius: 3px;
}

.pt-table {
  min-width: 1530px;
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
  color: var(--pt-text);
}

.pt-table thead th {
  background: var(--pt-bg-muted);
  color: var(--pt-text);
  font-weight: 600;
  font-size: 12px;
  border-bottom: 1px solid var(--pt-border);
  border-right: 1px solid var(--pt-border-subtle);
  padding: 9px 12px;
  text-align: left;
  white-space: nowrap;
  height: 36px;
  box-sizing: border-box;
  position: sticky;
  top: 0;
  z-index: 1;
}

.pt-table thead th:last-child {
  border-right: 0;
}

.pt-table tbody td {
  border-bottom: 1px solid var(--pt-border-subtle);
  border-right: 1px solid var(--pt-border-subtle);
  padding: 8px 12px;
  color: var(--pt-text);
  font-size: 13px;
  line-height: 1.5;
  height: 36px;
  box-sizing: border-box;
  vertical-align: middle;
  white-space: nowrap;
}

/* 操作列：单元格内容允许换行，按钮组不换行 */
.pt-table tbody td.pt-cell-actions {
  white-space: normal;
  vertical-align: middle;
  height: auto;
  min-height: 36px;
  padding-top: 6px;
  padding-bottom: 6px;
}

.pt-table tbody td:last-child {
  border-right: 0;
}

.pt-table tbody tr:hover td {
  background: var(--pt-primary-soft);
}

.pt-table tbody tr:nth-child(even) td {
  background: var(--pt-bg-muted);
}

.pt-table tbody tr:nth-child(even):hover td {
  background: var(--pt-primary-soft);
}

.pt-table-compact tbody td,
.pt-table-compact thead th {
  font-size: 12px;
  height: 32px;
  padding: 6px 12px;
}

/* ===== Badge ===== */
.pt-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.7;
  white-space: nowrap;
  border: 1px solid transparent;
}

.pt-badge-green {
  background: #dcfce7;
  color: #16a34a;
  border-color: #bbf7d0;
}

.pt-badge-blue {
  background: #dbeafe;
  color: #2563eb;
  border-color: #bfdbfe;
}

.pt-badge-orange {
  background: #fef3c7;
  color: #d97706;
  border-color: #fde68a;
}

.pt-badge-gray {
  background: #f1f5f9;
  color: #64748b;
  border-color: #e2e8f0;
}

/* ===== Row actions ===== */
.pt-row-actions {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 4px;
  min-width: 340px;
}

.pt-btn-text {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-height: 24px;
  padding: 0 6px;
  background: transparent;
  border: 0;
  color: var(--pt-primary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border-radius: var(--pt-radius-sm);
  transition: all 0.15s ease;
  font-family: inherit;
  white-space: nowrap;
  flex-shrink: 0;
}

.pt-btn-text:hover {
  background: var(--pt-primary-soft);
}

.pt-btn-text-danger {
  color: #dc2626;
}

.pt-btn-text-danger:hover {
  background: #fee2e2;
}

.pt-row-meta {
  color: var(--pt-text-tertiary);
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  margin-left: 0;
  white-space: nowrap;
  flex-basis: 100%;
  padding-top: 2px;
}

/* ===== Sub group ===== */
.pt-sub-group {
  border-top: 1px solid var(--pt-border-subtle);
}

.pt-sub-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 18px;
  background: var(--pt-bg-muted);
  border-bottom: 1px solid var(--pt-border-subtle);
}

.pt-sub-group-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--pt-text-secondary);
}

.pt-sub-group-title > svg {
  color: var(--pt-primary);
  flex: 0 0 13px;
}

/* ===== Pagination separator (PaginationControls) ===== */
.pt-card :deep(.pagination-controls) {
  border-top: 1px solid var(--pt-border-subtle);
  background: var(--pt-bg-card);
  padding: 10px 18px;
}

/* ===== Responsive ===== */
@media (max-width: 1100px) {
  .pt-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  }

  .pt-form-grid-3 {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  }

  .pt-form-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }
}

@media (max-width: 700px) {
  .pt-form-grid,
  .pt-form-grid-3 {
    grid-template-columns: 1fr !important;
  }

  .pt-card-header-desc-inline {
    display: none;
  }
}

/* =====================================================================
   定数包标签与解包
   ---------------------------------------------------------------------
   独立的 lbl-* 命名空间，样式全部收敛在本文件 scoped 内，
   不再依赖全局 quota-package.css 里缺失的 pt-label-* 类。
   设计取向：密集、便于批量扫读与打印/解包操作，不做营销式大卡片。
   ===================================================================== */
.lbl-panel {
  --lbl-primary: var(--pt-primary, #0f766e);
  --lbl-danger: #c0352b;
  --lbl-warn: #b45309;
  --lbl-ok: #087f5b;
  --lbl-border: color-mix(in srgb, var(--lbl-primary) 13%, #e3e9e7);
  --lbl-border-soft: color-mix(in srgb, var(--lbl-primary) 7%, #eef2f1);
  --lbl-head: #f4f8f7;
  --lbl-text: #17262f;
  --lbl-text-2: #5b6c78;
  --lbl-text-3: #8c9aa4;

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--lbl-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(23, 38, 47, 0.04);
  padding: 12px 14px 14px;
}

/* ---------- 顶部成功提示 ---------- */
.lbl-banner {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
}

.lbl-banner-ok {
  border: 1px solid color-mix(in srgb, var(--lbl-ok) 26%, #fff);
  background: color-mix(in srgb, var(--lbl-ok) 8%, #fff);
  color: #076c4e;
}

.lbl-banner > svg {
  flex: 0 0 auto;
}

/* ---------- 工具条 ---------- */
.lbl-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--lbl-border-soft);
}

.lbl-toolbar-lead {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.lbl-toolbar-icon {
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--lbl-primary) 10%, #fff);
  color: var(--lbl-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--lbl-primary) 14%, transparent);
}

.lbl-toolbar-text {
  min-width: 0;
}

.lbl-toolbar-text h3 {
  margin: 0;
  color: var(--lbl-text);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.lbl-toolbar-text p {
  margin: 2px 0 0;
  color: var(--lbl-text-3);
  font-size: 12px;
  line-height: 1.4;
}

/* 状态概览（紧凑计数，非大型统计卡） */
.lbl-stats {
  display: flex;
  align-items: stretch;
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--lbl-border-soft);
  border-radius: 6px;
  background: var(--lbl-border-soft);
}

.lbl-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  min-width: 68px;
  padding: 5px 12px;
  background: #fff;
}

.lbl-stat-label {
  color: var(--lbl-text-3);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
}

.lbl-stat-value {
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.lbl-stat-value.is-pending {
  color: var(--lbl-warn);
}

.lbl-stat-value.is-available {
  color: var(--lbl-ok);
}

.lbl-stat-value.is-void {
  color: var(--lbl-text-3);
}

/* ---------- 筛选条 ---------- */
.lbl-filter {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr)) auto;
  gap: 8px;
  align-items: end;
  border: 1px solid var(--lbl-border-soft);
  border-radius: 6px;
  background: var(--lbl-head);
  padding: 10px;
}

.lbl-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.lbl-field > span {
  color: var(--lbl-text-2);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.lbl-field input {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--lbl-border);
  border-radius: 5px;
  background: #fff;
  color: var(--lbl-text);
  font-size: 13px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.lbl-field input::placeholder {
  color: #a9b6bf;
}

.lbl-field input:focus {
  border-color: var(--lbl-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--lbl-primary) 13%, transparent);
}

.lbl-filter-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 1px;
}

.lbl-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--lbl-border);
  border-radius: 5px;
  background: #fff;
  color: var(--lbl-text-2);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.lbl-btn > svg {
  flex: 0 0 auto;
}

.lbl-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--lbl-primary) 40%, var(--lbl-border));
  color: var(--lbl-primary);
}

.lbl-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.lbl-btn-primary {
  border-color: var(--lbl-primary);
  background: var(--lbl-primary);
  color: #fff;
}

.lbl-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, #115e59);
  background: var(--pt-primary-dark, #115e59);
  color: #fff;
}

/* ---------- 表格 ---------- */
.lbl-scroll {
  overflow: auto;
  max-height: min(58vh, 620px);
  border: 1px solid var(--lbl-border-soft);
  border-radius: 6px;
  background: #fff;
}

.lbl-table {
  width: 100%;
  min-width: 1180px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.lbl-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f2f8f7, #eaf2f1);
}

.lbl-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--lbl-border);
  color: #4d6472;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.lbl-table td {
  padding: 9px 12px;
  border-bottom: 1px solid #eef3f2;
  color: var(--lbl-text);
  vertical-align: middle;
}

.lbl-table tbody tr:last-child td {
  border-bottom: 0;
}

.lbl-row td {
  transition: background 0.12s ease;
}

.lbl-row:hover td {
  background: #f7fbfa;
}

.lbl-row:has(.lbl-status.is-void) td:not(.lbl-col-no):not(.lbl-col-status) {
  color: var(--lbl-text-3);
}

/* 空状态 / 加载中：让 TableStateRow 在本表内更紧凑 */
.lbl-table :deep(.table-state-row) td {
  height: 132px;
  padding: 20px 24px;
}

/* 列宽：min-width + 自动撑开，宽屏时列等比放大，窄屏时触发横向滚动 */
.lbl-col-no {
  min-width: 168px;
}

.lbl-col-status {
  min-width: 104px;
}

.lbl-col-source {
  min-width: 200px;
}

.lbl-col-print {
  min-width: 88px;
}

.lbl-col-time {
  min-width: 168px;
}

.lbl-col-actions {
  min-width: 150px;
}

.lbl-col-num {
  min-width: 88px;
  text-align: right;
}

/* 单元格内容 */
.lbl-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  letter-spacing: -0.1px;
}

.lbl-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--lbl-text);
}

.lbl-main {
  color: var(--lbl-text);
  font-weight: 500;
  line-height: 1.4;
}

.lbl-sub {
  color: var(--lbl-text-3);
  font-size: 11px;
  line-height: 1.4;
}

.lbl-qty {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--lbl-text);
}

.lbl-source {
  display: -webkit-box;
  overflow: hidden;
  color: var(--lbl-text-2);
  font-size: 12px;
  line-height: 1.45;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.lbl-print {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  border: 1px solid var(--lbl-border-soft);
  background: var(--lbl-head);
  color: var(--lbl-text-2);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.lbl-print.is-zero {
  border-color: #eef1f2;
  background: #fafbfb;
  color: #b3bec5;
}

/* ---------- 状态徽标 ---------- */
.lbl-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px 0 7px;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: inset 0 0 0 1px transparent;
}

.lbl-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex: 0 0 6px;
}

.lbl-status.is-pending {
  background: color-mix(in srgb, var(--lbl-warn) 12%, #fff);
  color: var(--lbl-warn);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--lbl-warn) 22%, transparent);
}

.lbl-status.is-available {
  background: color-mix(in srgb, var(--lbl-ok) 12%, #fff);
  color: var(--lbl-ok);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--lbl-ok) 20%, transparent);
}

.lbl-status.is-void {
  background: #f2f4f5;
  color: #8d9aa3;
  box-shadow: inset 0 0 0 1px #e6eaec;
}

/* ---------- 行内操作 ---------- */
.lbl-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.lbl-act {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 9px;
  border: 1px solid color-mix(in srgb, var(--lbl-primary) 24%, transparent);
  border-radius: 5px;
  background: color-mix(in srgb, var(--lbl-primary) 7%, #fff);
  color: var(--lbl-primary);
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.lbl-act > svg {
  flex: 0 0 auto;
}

.lbl-act:hover {
  border-color: var(--lbl-primary);
  background: color-mix(in srgb, var(--lbl-primary) 14%, #fff);
}

.lbl-act-danger {
  border-color: color-mix(in srgb, var(--lbl-danger) 24%, transparent);
  background: color-mix(in srgb, var(--lbl-danger) 7%, #fff);
  color: var(--lbl-danger);
}

.lbl-act-danger:hover {
  border-color: var(--lbl-danger);
  background: color-mix(in srgb, var(--lbl-danger) 14%, #fff);
}

.lbl-act-none {
  color: var(--lbl-text-3);
  font-size: 12px;
}

/* ---------- 分页 ---------- */
.lbl-panel :deep(.pagination-controls) {
  border-top: 1px solid var(--lbl-border-soft);
  padding: 8px 2px 0;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1400px) {
  .lbl-filter {
    grid-template-columns: repeat(3, minmax(120px, 1fr)) auto;
  }
}

@media (max-width: 900px) {
  .lbl-toolbar {
    align-items: flex-start;
  }

  .lbl-filter {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .lbl-filter-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}

@media (max-width: 560px) {
  .lbl-filter {
    grid-template-columns: 1fr;
  }

  .lbl-stats {
    width: 100%;
  }

  .lbl-stat {
    flex: 1 1 0;
    min-width: 0;
  }
}

/* =====================================================================
   定数包事件
   ---------------------------------------------------------------------
   独立的 evt-* 命名空间，样式同样收敛在本文件 scoped 内。
   核心诉求：一眼看清「发生了什么类型的事件 → 状态怎么流转 → 数量怎么动」。
   ===================================================================== */
.evt-panel {
  --evt-primary: var(--pt-primary, #0f766e);
  --evt-danger: #c0352b;
  --evt-warn: #b45309;
  --evt-ok: #087f5b;
  --evt-info: #1d6fa5;
  --evt-border: color-mix(in srgb, var(--evt-primary) 13%, #e3e9e7);
  --evt-border-soft: color-mix(in srgb, var(--evt-primary) 7%, #eef2f1);
  --evt-head: #f4f8f7;
  --evt-text: #17262f;
  --evt-text-2: #5b6c78;
  --evt-text-3: #8c9aa4;

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--evt-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(23, 38, 47, 0.04);
  padding: 12px 14px 14px;
}

/* ---------- 工具条 ---------- */
.evt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--evt-border-soft);
}

.evt-toolbar-lead {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.evt-toolbar-icon {
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--evt-primary) 10%, #fff);
  color: var(--evt-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-primary) 14%, transparent);
}

.evt-toolbar-text h3 {
  margin: 0;
  color: var(--evt-text);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.evt-toolbar-text p {
  margin: 2px 0 0;
  color: var(--evt-text-3);
  font-size: 12px;
  line-height: 1.4;
}

.evt-stats {
  display: flex;
  align-items: stretch;
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--evt-border-soft);
  border-radius: 6px;
  background: var(--evt-border-soft);
}

.evt-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  min-width: 76px;
  padding: 5px 12px;
  background: #fff;
}

.evt-stat-label {
  color: var(--evt-text-3);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
}

.evt-stat-value {
  color: var(--evt-text);
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

/* ---------- 筛选条 ---------- */
.evt-filter {
  display: grid;
  grid-template-columns: repeat(2, minmax(130px, 1fr)) minmax(150px, 1.2fr) auto;
  gap: 8px;
  align-items: end;
  border: 1px solid var(--evt-border-soft);
  border-radius: 6px;
  background: var(--evt-head);
  padding: 10px;
}

.evt-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.evt-field > span {
  color: var(--evt-text-2);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.evt-field input,
.evt-field select {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--evt-border);
  border-radius: 5px;
  background: #fff;
  color: var(--evt-text);
  font-size: 13px;
  font-family: inherit;
  outline: none;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.evt-field input {
  cursor: text;
}

.evt-field input::placeholder {
  color: #a9b6bf;
}

.evt-field input:focus,
.evt-field select:focus {
  border-color: var(--evt-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--evt-primary) 13%, transparent);
}

.evt-filter-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 1px;
}

.evt-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--evt-border);
  border-radius: 5px;
  background: #fff;
  color: var(--evt-text-2);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.evt-btn > svg {
  flex: 0 0 auto;
}

.evt-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--evt-primary) 40%, var(--evt-border));
  color: var(--evt-primary);
}

.evt-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.evt-btn-primary {
  border-color: var(--evt-primary);
  background: var(--evt-primary);
  color: #fff;
}

.evt-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, #115e59);
  background: var(--pt-primary-dark, #115e59);
  color: #fff;
}

/* ---------- 表格 ---------- */
.evt-scroll {
  overflow: auto;
  max-height: min(58vh, 620px);
  border: 1px solid var(--evt-border-soft);
  border-radius: 6px;
  background: #fff;
}

.evt-table {
  width: 100%;
  min-width: 1160px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.evt-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f2f8f7, #eaf2f1);
}

.evt-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--evt-border);
  color: #4d6472;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.evt-table td {
  padding: 9px 12px;
  border-bottom: 1px solid #eef3f2;
  color: var(--evt-text);
  vertical-align: middle;
}

.evt-table tbody tr:last-child td {
  border-bottom: 0;
}

.evt-row td {
  transition: background 0.12s ease;
}

.evt-row:hover td {
  background: #f7fbfa;
}

/* 列宽：min-width + 自动撑开 */
.evt-col-no {
  min-width: 176px;
}

.evt-col-label {
  min-width: 168px;
}

.evt-col-type {
  min-width: 150px;
}

.evt-col-flow {
  min-width: 216px;
}

.evt-col-qty {
  min-width: 104px;
}

.evt-col-time {
  min-width: 150px;
}

/* 单元格 */
.evt-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--evt-text-2);
}

.evt-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--evt-text);
}

.evt-label-no {
  display: inline-block;
  padding: 2px 7px;
  border: 1px solid var(--evt-border-soft);
  border-radius: 4px;
  background: var(--evt-head);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--evt-text-2);
}

.evt-sub {
  color: var(--evt-text-3);
  font-size: 12px;
}

/* ---------- 事件类型徽标 ---------- */
.evt-type {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px 0 7px;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: inset 0 0 0 1px transparent;
}

.evt-type-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex: 0 0 6px;
}

.evt-type.is-pack {
  background: color-mix(in srgb, var(--evt-primary) 11%, #fff);
  color: var(--evt-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-primary) 20%, transparent);
}

.evt-type.is-print {
  background: color-mix(in srgb, #6b4fbb 11%, #fff);
  color: #5b3fa8;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, #6b4fbb 20%, transparent);
}

.evt-type.is-info {
  background: color-mix(in srgb, var(--evt-info) 11%, #fff);
  color: var(--evt-info);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-info) 20%, transparent);
}

.evt-type.is-ok {
  background: color-mix(in srgb, var(--evt-ok) 12%, #fff);
  color: var(--evt-ok);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-ok) 20%, transparent);
}

.evt-type.is-warn {
  background: color-mix(in srgb, var(--evt-warn) 12%, #fff);
  color: var(--evt-warn);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-warn) 22%, transparent);
}

.evt-type.is-danger {
  background: color-mix(in srgb, var(--evt-danger) 11%, #fff);
  color: var(--evt-danger);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-danger) 22%, transparent);
}

/* ---------- 状态流转 ---------- */
.evt-flow {
  display: flex;
  align-items: center;
  gap: 6px;
}

.evt-flow-node {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  background: #f3f5f6;
  color: var(--evt-text-2);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.evt-flow-node.is-after {
  background: color-mix(in srgb, var(--evt-primary) 10%, #fff);
  color: var(--evt-primary);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-primary) 18%, transparent);
}

.evt-flow-node.is-after.is-void {
  background: #f2f4f5;
  color: #8d9aa3;
  box-shadow: inset 0 0 0 1px #e6eaec;
}

.evt-flow-node.is-empty {
  background: transparent;
  color: var(--evt-text-3);
  font-style: italic;
  box-shadow: inset 0 0 0 1px #e9edee;
}

.evt-flow-arrow {
  flex: 0 0 auto;
  color: #a9b6bf;
}

/* ---------- 数量变化 ---------- */
.evt-qty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 62px;
  height: 22px;
  padding: 0 9px;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.evt-qty.is-plus {
  background: color-mix(in srgb, var(--evt-ok) 12%, #fff);
  color: var(--evt-ok);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-ok) 20%, transparent);
}

.evt-qty.is-minus {
  background: color-mix(in srgb, var(--evt-warn) 12%, #fff);
  color: var(--evt-warn);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-warn) 22%, transparent);
}

/* ---------- 备注 ---------- */
.evt-remark {
  max-width: 1px;
  overflow: hidden;
  color: var(--evt-text-2);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 空状态 ---------- */
.evt-table :deep(.table-state-row) td {
  height: 132px;
  padding: 20px 24px;
}

/* ---------- 分页 ---------- */
.evt-panel :deep(.pagination-controls) {
  border-top: 1px solid var(--evt-border-soft);
  padding: 8px 2px 0;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1000px) {
  .evt-filter {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .evt-filter-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}

@media (max-width: 560px) {
  .evt-filter {
    grid-template-columns: 1fr;
  }

  .evt-stats {
    width: 100%;
  }

  .evt-stat {
    flex: 1 1 0;
    min-width: 0;
  }
}

/* =====================================================================
   可打包散货快照
   ---------------------------------------------------------------------
   独立的 snp-* 命名空间，样式同样收敛在本文件 scoped 内。
   核心诉求：按批次核对可打包散货，突出「还有多少」「还能用多久」。
   ===================================================================== */
.snp-panel {
  --snp-primary: var(--pt-primary, #0f766e);
  --snp-danger: #c0352b;
  --snp-warn: #b45309;
  --snp-border: color-mix(in srgb, var(--snp-primary) 13%, #e3e9e7);
  --snp-border-soft: color-mix(in srgb, var(--snp-primary) 7%, #eef2f1);
  --snp-head: #f4f8f7;
  --snp-text: #17262f;
  --snp-text-2: #5b6c78;
  --snp-text-3: #8c9aa4;

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--snp-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(23, 38, 47, 0.04);
  padding: 12px 14px 14px;
}

/* ---------- 工具条 ---------- */
.snp-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--snp-border-soft);
}

.snp-toolbar-lead {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.snp-toolbar-icon {
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--snp-primary) 10%, #fff);
  color: var(--snp-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--snp-primary) 14%, transparent);
}

.snp-toolbar-text h3 {
  margin: 0;
  color: var(--snp-text);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.snp-toolbar-text p {
  margin: 2px 0 0;
  color: var(--snp-text-3);
  font-size: 12px;
  line-height: 1.4;
}

.snp-stats {
  display: flex;
  align-items: stretch;
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--snp-border-soft);
  border-radius: 6px;
  background: var(--snp-border-soft);
}

.snp-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  min-width: 78px;
  padding: 5px 12px;
  background: #fff;
}

.snp-stat-label {
  color: var(--snp-text-3);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
}

.snp-stat-value {
  color: var(--snp-text);
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

/* ---------- 筛选条 ---------- */
.snp-filter {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, minmax(130px, 1fr)) auto;
  gap: 8px;
  align-items: end;
  border: 1px solid var(--snp-border-soft);
  border-radius: 6px;
  background: var(--snp-head);
  padding: 10px;
}

.snp-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.snp-field > span {
  color: var(--snp-text-2);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.snp-field input {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--snp-border);
  border-radius: 5px;
  background: #fff;
  color: var(--snp-text);
  font-size: 13px;
  font-family: inherit;
  outline: none;
  cursor: text;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.snp-field input::placeholder {
  color: #a9b6bf;
}

.snp-field input:focus {
  border-color: var(--snp-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--snp-primary) 13%, transparent);
}

.snp-filter-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 1px;
}

.snp-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--snp-border);
  border-radius: 5px;
  background: #fff;
  color: var(--snp-text-2);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  list-style: none;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.snp-btn > svg {
  flex: 0 0 auto;
}

.snp-btn::-webkit-details-marker {
  display: none;
}

.snp-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--snp-primary) 40%, var(--snp-border));
  color: var(--snp-primary);
}

.snp-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.snp-btn-primary {
  border-color: var(--snp-primary);
  background: var(--snp-primary);
  color: #fff;
}

.snp-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, #115e59);
  background: var(--pt-primary-dark, #115e59);
  color: #fff;
}

/* ---------- 显示列面板 ---------- */
.snp-columns {
  position: relative;
}

.snp-columns-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 17px;
  padding: 0 5px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--snp-primary) 12%, #fff);
  color: var(--snp-primary);
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.snp-columns-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: 2px 10px;
  min-width: 280px;
  padding: 8px;
  border: 1px solid var(--snp-border);
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(23, 38, 47, 0.14);
}

.snp-columns-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 6px;
  border-radius: 4px;
  color: var(--snp-text-2);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.12s ease, color 0.12s ease;
}

.snp-columns-item:hover {
  background: var(--snp-head);
  color: var(--snp-text);
}

.snp-columns-item input {
  width: 14px;
  height: 14px;
  accent-color: var(--snp-primary);
  cursor: pointer;
  flex: 0 0 auto;
}

.snp-columns-item input:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

/* ---------- 表格 ---------- */
.snp-scroll {
  overflow: auto;
  max-height: min(58vh, 620px);
  border: 1px solid var(--snp-border-soft);
  border-radius: 6px;
  background: #fff;
}

.snp-table {
  width: 100%;
  min-width: 980px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.snp-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f2f8f7, #eaf2f1);
}

.snp-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--snp-border);
  color: #4d6472;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.snp-table td {
  padding: 9px 12px;
  border-bottom: 1px solid #eef3f2;
  color: var(--snp-text);
  vertical-align: middle;
}

.snp-table tbody tr:last-child td {
  border-bottom: 0;
}

.snp-row td {
  transition: background 0.12s ease;
}

.snp-row:hover td {
  background: #f7fbfa;
}

/* 列对齐与宽度：min-width + 自动撑开 */
.snp-col-num {
  text-align: right;
}

th.snp-col-num {
  min-width: 112px;
}

.snp-col-warehouse {
  min-width: 140px;
}

.snp-col-code {
  min-width: 150px;
}

.snp-col-expire {
  min-width: 150px;
}

/* 单元格内容 */
.snp-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--snp-text-2);
}

.snp-main {
  color: var(--snp-text);
  font-weight: 500;
  line-height: 1.4;
}

.snp-sub {
  color: var(--snp-text-3);
  font-size: 11px;
  line-height: 1.4;
}

.snp-batch {
  display: inline-block;
  padding: 2px 7px;
  border: 1px solid var(--snp-border-soft);
  border-radius: 4px;
  background: var(--snp-head);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--snp-text-2);
}

.snp-price {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--snp-text);
}

.snp-qty {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  font-size: 14px;
  color: var(--snp-primary);
}

/* 有效期预警 */
.snp-expire {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  white-space: nowrap;
}

.snp-expire.is-normal {
  background: #f3f5f6;
  color: var(--snp-text-2);
}

.snp-expire.is-warning {
  background: color-mix(in srgb, var(--snp-warn) 10%, #fff);
  color: var(--snp-warn);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--snp-warn) 20%, transparent);
}

.snp-expire.is-critical {
  background: color-mix(in srgb, #c2410c 12%, #fff);
  color: #c2410c;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, #c2410c 24%, transparent);
}

.snp-expire.is-expired {
  background: color-mix(in srgb, var(--snp-danger) 12%, #fff);
  color: var(--snp-danger);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--snp-danger) 26%, transparent);
}

.snp-expire.is-unknown {
  background: transparent;
  color: var(--snp-text-3);
  box-shadow: inset 0 0 0 1px #e9edee;
}

/* ---------- 空状态 ---------- */
.snp-table :deep(.table-state-row) td {
  height: 132px;
  padding: 20px 24px;
}

/* ---------- 分页 ---------- */
.snp-panel :deep(.pagination-controls) {
  border-top: 1px solid var(--snp-border-soft);
  padding: 8px 2px 0;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1000px) {
  .snp-filter {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .snp-filter-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
    flex-wrap: wrap;
  }

  .snp-columns-menu {
    right: auto;
    left: 0;
  }
}

@media (max-width: 560px) {
  .snp-filter {
    grid-template-columns: 1fr;
  }

  .snp-stats {
    width: 100%;
  }

  .snp-stat {
    flex: 1 1 0;
    min-width: 0;
  }

  .snp-columns-menu {
    grid-template-columns: 1fr;
    min-width: 200px;
  }
}

/* =====================================================================
   打印模板调整（定数包模板维护）
   ---------------------------------------------------------------------
   独立的 tpl-* 命名空间，样式同样收敛在本文件 scoped 内。
   核心诉求：模板是打包的口径来源，批量维护时要能快速核对
   「哪个模板、多少数量、是否启用、什么版本」。
   ===================================================================== */
.tpl-card {
  --tpl-primary: var(--pt-primary, #0f766e);
  --tpl-danger: #c0352b;
  --tpl-border: color-mix(in srgb, var(--tpl-primary) 13%, #e3e9e7);
  --tpl-border-soft: color-mix(in srgb, var(--tpl-primary) 7%, #eef2f1);
  --tpl-head: #f4f8f7;
  --tpl-text: #17262f;
  --tpl-text-2: #5b6c78;
  --tpl-text-3: #8c9aa4;

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--tpl-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(23, 38, 47, 0.04);
  padding: 12px 14px 14px;
}

/* ---------- 顶部提示 ---------- */
.tpl-banner {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--tpl-primary) 26%, #fff);
  border-radius: 6px;
  background: color-mix(in srgb, var(--tpl-primary) 8%, #fff);
  color: #076c4e;
  font-size: 13px;
  font-weight: 500;
}

.tpl-banner > svg {
  flex: 0 0 auto;
}

/* ---------- 筛选条 ---------- */
.tpl-filter {
  display: grid;
  grid-template-columns: repeat(4, minmax(130px, 1fr)) auto;
  gap: 8px;
  align-items: end;
  border: 1px solid var(--tpl-border-soft);
  border-radius: 6px;
  background: var(--tpl-head);
  padding: 10px;
}

.tpl-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.tpl-field > span {
  color: var(--tpl-text-2);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.tpl-field input {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--tpl-border);
  border-radius: 5px;
  background: #fff;
  color: var(--tpl-text);
  font-size: 13px;
  font-family: inherit;
  outline: none;
  cursor: text;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.tpl-field input::placeholder {
  color: #a9b6bf;
}

.tpl-field input:focus {
  border-color: var(--tpl-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--tpl-primary) 13%, transparent);
}

.tpl-filter-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 1px;
}

/* ---------- 按钮 ---------- */
.tpl-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--tpl-border);
  border-radius: 5px;
  background: #fff;
  color: var(--tpl-text-2);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  list-style: none;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.tpl-btn > svg {
  flex: 0 0 auto;
}

.tpl-btn::-webkit-details-marker {
  display: none;
}

.tpl-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--tpl-primary) 40%, var(--tpl-border));
  color: var(--tpl-primary);
}

.tpl-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tpl-btn-primary {
  border-color: var(--tpl-primary);
  background: var(--tpl-primary);
  color: #fff;
}

.tpl-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, #115e59);
  background: var(--pt-primary-dark, #115e59);
  color: #fff;
}

.tpl-btn-danger {
  border-color: color-mix(in srgb, var(--tpl-danger) 28%, #fff);
  background: color-mix(in srgb, var(--tpl-danger) 7%, #fff);
  color: var(--tpl-danger);
}

.tpl-btn-danger:hover:not(:disabled) {
  border-color: var(--tpl-danger);
  background: color-mix(in srgb, var(--tpl-danger) 14%, #fff);
  color: var(--tpl-danger);
}

/* ---------- 工具条 ---------- */
.tpl-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.tpl-toolbar-main {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tpl-toolbar-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tpl-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border: 1px solid var(--tpl-border-soft);
  border-radius: 999px;
  background: var(--tpl-head);
  color: var(--tpl-text-2);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.tpl-chip.is-primary {
  border-color: color-mix(in srgb, var(--tpl-primary) 26%, #fff);
  background: color-mix(in srgb, var(--tpl-primary) 10%, #fff);
  color: var(--tpl-primary);
}

/* ---------- 导入菜单 ---------- */
.tpl-import {
  position: relative;
}

.tpl-import-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  display: grid;
  gap: 2px;
  min-width: 176px;
  padding: 6px;
  border: 1px solid var(--tpl-border);
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(23, 38, 47, 0.14);
}

.tpl-import-menu button {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 9px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--tpl-text-2);
  font-size: 13px;
  font-family: inherit;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.12s ease, color 0.12s ease;
}

.tpl-import-menu button > svg {
  flex: 0 0 auto;
}

.tpl-import-menu button:hover {
  background: var(--tpl-head);
  color: var(--tpl-primary);
}

.tpl-file-input {
  display: none;
}

/* ---------- 表格 ---------- */
.tpl-scroll {
  overflow: auto;
  max-height: min(58vh, 620px);
  border: 1px solid var(--tpl-border-soft);
  border-radius: 6px;
  background: #fff;
}

.tpl-table {
  width: 100%;
  min-width: 1280px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.tpl-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f2f8f7, #eaf2f1);
}

.tpl-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--tpl-border);
  color: #4d6472;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.tpl-table td {
  padding: 9px 12px;
  border-bottom: 1px solid #eef3f2;
  color: var(--tpl-text);
  vertical-align: middle;
}

.tpl-table tbody tr:last-child td {
  border-bottom: 0;
}

.tpl-row td {
  transition: background 0.12s ease;
}

.tpl-row:hover td {
  background: #f7fbfa;
}

/* sticky 列必须显式给背景（tr 本身无背景），否则横向滚动时下方内容会透出 */
.tpl-row:hover td.tpl-col-select,
.tpl-row:hover td.tpl-col-actions {
  background: #f7fbfa;
}

.tpl-row.is-selected td {
  background: color-mix(in srgb, var(--tpl-primary) 7%, #fff);
}

.tpl-row.is-selected td.tpl-col-select,
.tpl-row.is-selected td.tpl-col-actions {
  background: color-mix(in srgb, var(--tpl-primary) 7%, #fff);
}

.tpl-row.is-selected:hover td {
  background: color-mix(in srgb, var(--tpl-primary) 11%, #fff);
}

.tpl-row.is-selected:hover td.tpl-col-select,
.tpl-row.is-selected:hover td.tpl-col-actions {
  background: color-mix(in srgb, var(--tpl-primary) 11%, #fff);
}

/* 勾选列：固定在左侧，横向滚动时始终可见 */
.tpl-col-select {
  position: sticky;
  left: 0;
  z-index: 1;
  min-width: 44px;
  text-align: center;
  background: #fff;
}

.tpl-table thead .tpl-col-select {
  z-index: 4;
  background: #eaf2f1;
}

.tpl-col-select input {
  width: 15px;
  height: 15px;
  accent-color: var(--tpl-primary);
  cursor: pointer;
  vertical-align: middle;
}

/* 操作列：固定在右侧 */
.tpl-col-actions {
  position: sticky;
  right: 0;
  z-index: 1;
  min-width: 208px;
  background: #fff;
  box-shadow: -1px 0 0 var(--tpl-border-soft);
}

.tpl-table thead .tpl-col-actions {
  z-index: 4;
  background: #eaf2f1;
}

/* 其余列宽：min-width + 自动撑开 */
.tpl-col-code {
  min-width: 136px;
}

.tpl-col-spec {
  min-width: 132px;
}

.tpl-col-version {
  min-width: 96px;
}

.tpl-col-qty {
  min-width: 104px;
  text-align: right;
}

.tpl-col-status {
  min-width: 92px;
}

/* 单元格内容 */
.tpl-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--tpl-text);
}

.tpl-main {
  color: var(--tpl-text);
  font-weight: 500;
  line-height: 1.4;
}

.tpl-qty {
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  font-weight: 700;
  color: var(--tpl-primary);
}

.tpl-qty-unit {
  margin-left: 3px;
  color: var(--tpl-text-3);
  font-size: 11px;
}

.tpl-version {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 20px;
  padding: 0 7px;
  border: 1px solid var(--tpl-border-soft);
  border-radius: 10px;
  background: var(--tpl-head);
  color: var(--tpl-text-2);
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.tpl-version-current {
  display: inline-flex;
  align-items: center;
  margin-left: 4px;
  color: var(--tpl-primary);
  font-size: 11px;
  font-weight: 600;
}

/* 状态徽标 */
.tpl-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px 0 7px;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.tpl-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex: 0 0 6px;
}

.tpl-status.is-enabled {
  background: color-mix(in srgb, #087f5b 12%, #fff);
  color: #087f5b;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, #087f5b 20%, transparent);
}

.tpl-status.is-disabled {
  background: #f2f4f5;
  color: #8d9aa3;
  box-shadow: inset 0 0 0 1px #e6eaec;
}

/* 行内操作 */
.tpl-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tpl-act {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 9px;
  border: 1px solid var(--tpl-border-soft);
  border-radius: 5px;
  background: #fff;
  color: var(--tpl-text-2);
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.tpl-act > svg {
  flex: 0 0 auto;
}

.tpl-act:hover {
  border-color: color-mix(in srgb, var(--tpl-primary) 45%, var(--tpl-border));
  background: color-mix(in srgb, var(--tpl-primary) 8%, #fff);
  color: var(--tpl-primary);
}

.tpl-act-primary {
  border-color: color-mix(in srgb, var(--tpl-primary) 26%, #fff);
  background: color-mix(in srgb, var(--tpl-primary) 9%, #fff);
  color: var(--tpl-primary);
}

.tpl-act-danger {
  border-color: color-mix(in srgb, var(--tpl-danger) 24%, transparent);
  background: color-mix(in srgb, var(--tpl-danger) 7%, #fff);
  color: var(--tpl-danger);
}

.tpl-act-danger:hover {
  border-color: var(--tpl-danger);
  background: color-mix(in srgb, var(--tpl-danger) 14%, #fff);
  color: var(--tpl-danger);
}

/* ---------- 空状态 ---------- */
.tpl-table :deep(.table-state-row) td {
  height: 132px;
  padding: 20px 24px;
}

/* ---------- 分页 ---------- */
.tpl-card :deep(.pagination-controls) {
  border-top: 1px solid var(--tpl-border-soft);
  padding: 8px 2px 0;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .tpl-filter {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .tpl-filter-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}

@media (max-width: 560px) {
  .tpl-filter {
    grid-template-columns: 1fr;
  }

  .tpl-toolbar-info {
    width: 100%;
  }
}

/* =====================================================================
   定数安全量
   ---------------------------------------------------------------------
   独立的 saf-* 命名空间，样式同样收敛在本文件 scoped 内。
   核心诉求：安全量决定补货触发，重点是「区间是否合理、是否启用」。
   ===================================================================== */
.saf-card {
  --saf-primary: var(--pt-primary, #0f766e);
  --saf-border: color-mix(in srgb, var(--saf-primary) 13%, #e3e9e7);
  --saf-border-soft: color-mix(in srgb, var(--saf-primary) 7%, #eef2f1);
  --saf-head: #f4f8f7;
  --saf-text: #17262f;
  --saf-text-2: #5b6c78;
  --saf-text-3: #8c9aa4;

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--saf-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(23, 38, 47, 0.04);
  padding: 12px 14px 14px;
}

/* ---------- 顶部提示 ---------- */
.saf-banner {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--saf-primary) 26%, #fff);
  border-radius: 6px;
  background: color-mix(in srgb, var(--saf-primary) 8%, #fff);
  color: #076c4e;
  font-size: 13px;
  font-weight: 500;
}

.saf-banner > svg {
  flex: 0 0 auto;
}

/* ---------- 工具条 ---------- */
.saf-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--saf-border-soft);
}

.saf-toolbar-lead {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  margin-right: auto;
}

.saf-toolbar-icon {
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--saf-primary) 10%, #fff);
  color: var(--saf-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--saf-primary) 14%, transparent);
}

.saf-toolbar-text h3 {
  margin: 0;
  color: var(--saf-text);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.saf-toolbar-text p {
  margin: 2px 0 0;
  color: var(--saf-text-3);
  font-size: 12px;
  line-height: 1.4;
}

.saf-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.saf-toolbar-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.saf-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border: 1px solid var(--saf-border-soft);
  border-radius: 999px;
  background: var(--saf-head);
  color: var(--saf-text-2);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

/* ---------- 筛选条 ---------- */
.saf-filter {
  display: grid;
  grid-template-columns: repeat(3, minmax(130px, 1fr)) auto;
  gap: 8px;
  align-items: end;
  border: 1px solid var(--saf-border-soft);
  border-radius: 6px;
  background: var(--saf-head);
  padding: 10px;
}

.saf-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.saf-field > span {
  color: var(--saf-text-2);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.saf-field input {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--saf-border);
  border-radius: 5px;
  background: #fff;
  color: var(--saf-text);
  font-size: 13px;
  font-family: inherit;
  outline: none;
  cursor: text;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.saf-field input::placeholder {
  color: #a9b6bf;
}

.saf-field input:focus {
  border-color: var(--saf-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--saf-primary) 13%, transparent);
}

.saf-filter-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 1px;
}

/* ---------- 按钮 ---------- */
.saf-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--saf-border);
  border-radius: 5px;
  background: #fff;
  color: var(--saf-text-2);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  list-style: none;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.saf-btn > svg {
  flex: 0 0 auto;
}

.saf-btn::-webkit-details-marker {
  display: none;
}

.saf-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--saf-primary) 40%, var(--saf-border));
  color: var(--saf-primary);
}

.saf-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.saf-btn-primary {
  border-color: var(--saf-primary);
  background: var(--saf-primary);
  color: #fff;
}

.saf-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, #115e59);
  background: var(--pt-primary-dark, #115e59);
  color: #fff;
}

/* ---------- 导入模板菜单 ---------- */
.saf-import {
  position: relative;
}

.saf-import-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  display: grid;
  gap: 2px;
  min-width: 168px;
  padding: 6px;
  border: 1px solid var(--saf-border);
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(23, 38, 47, 0.14);
}

.saf-import-menu button {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 9px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--saf-text-2);
  font-size: 13px;
  font-family: inherit;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.12s ease, color 0.12s ease;
}

.saf-import-menu button > svg {
  flex: 0 0 auto;
}

.saf-import-menu button:hover {
  background: var(--saf-head);
  color: var(--saf-primary);
}

.saf-file-input {
  display: none;
}

/* ---------- 表格 ---------- */
.saf-scroll {
  overflow: auto;
  max-height: min(58vh, 620px);
  border: 1px solid var(--saf-border-soft);
  border-radius: 6px;
  background: #fff;
}

.saf-table {
  width: 100%;
  min-width: 1220px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.saf-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, #f2f8f7, #eaf2f1);
}

.saf-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--saf-border);
  color: #4d6472;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.saf-table td {
  padding: 9px 12px;
  border-bottom: 1px solid #eef3f2;
  color: var(--saf-text);
  vertical-align: middle;
}

.saf-table tbody tr:last-child td {
  border-bottom: 0;
}

.saf-row td {
  transition: background 0.12s ease;
}

.saf-row:hover td {
  background: #f7fbfa;
}

/* sticky 列必须显式给背景（tr 本身无背景），否则横向滚动时下方内容会透出 */
.saf-row:hover td.saf-col-actions {
  background: #f7fbfa;
}

/* 操作列：固定在右侧 */
.saf-col-actions {
  position: sticky;
  right: 0;
  z-index: 1;
  min-width: 92px;
  background: #fff;
  box-shadow: -1px 0 0 var(--saf-border-soft);
}

.saf-table thead .saf-col-actions {
  z-index: 4;
  background: #eaf2f1;
}

/* 列宽：min-width + 自动撑开 */
.saf-col-dept {
  min-width: 120px;
}

.saf-col-code {
  min-width: 132px;
}

.saf-col-range {
  min-width: 190px;
}

.saf-col-num {
  min-width: 84px;
  text-align: right;
}

.saf-col-status {
  min-width: 92px;
}

.saf-col-time {
  min-width: 156px;
}

/* 单元格内容 */
.saf-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--saf-text-2);
}

.saf-dept {
  color: var(--saf-text);
  font-weight: 600;
}

.saf-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--saf-text);
}

.saf-main {
  color: var(--saf-text);
  font-weight: 500;
  line-height: 1.4;
}

.saf-sub {
  color: var(--saf-text-3);
  font-size: 12px;
}

.saf-qty {
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  font-weight: 700;
  color: var(--saf-text-2);
}

.saf-qty.is-max {
  color: var(--saf-primary);
}

/* 安全区间条 */
.saf-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.saf-range-track {
  position: relative;
  flex: 1 1 auto;
  min-width: 54px;
  height: 6px;
  border-radius: 3px;
  background: #eef2f1;
  overflow: hidden;
}

.saf-range-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--saf-primary), color-mix(in srgb, var(--saf-primary) 55%, #14b8a6));
}

.saf-range-text {
  flex: 0 0 auto;
  color: var(--saf-text-2);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  white-space: nowrap;
}

/* 状态徽标 */
.saf-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px 0 7px;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.saf-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex: 0 0 6px;
}

.saf-status.is-enabled {
  background: color-mix(in srgb, #087f5b 12%, #fff);
  color: #087f5b;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, #087f5b 20%, transparent);
}

.saf-status.is-disabled {
  background: #f2f4f5;
  color: #8d9aa3;
  box-shadow: inset 0 0 0 1px #e6eaec;
}

/* 行内操作 */
.saf-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.saf-act {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 9px;
  border: 1px solid var(--saf-border-soft);
  border-radius: 5px;
  background: #fff;
  color: var(--saf-text-2);
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.saf-act > svg {
  flex: 0 0 auto;
}

.saf-act:hover {
  border-color: color-mix(in srgb, var(--saf-primary) 45%, var(--saf-border));
  background: color-mix(in srgb, var(--saf-primary) 8%, #fff);
  color: var(--saf-primary);
}

/* ---------- 空状态 ---------- */
.saf-table :deep(.table-state-row) td {
  height: 132px;
  padding: 20px 24px;
}

/* ---------- 分页 ---------- */
.saf-card :deep(.pagination-controls) {
  border-top: 1px solid var(--saf-border-soft);
  padding: 8px 2px 0;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1000px) {
  .saf-filter {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .saf-filter-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}

@media (max-width: 560px) {
  .saf-filter {
    grid-template-columns: 1fr;
  }

  .saf-toolbar-lead {
    margin-right: 0;
  }
}

/* ============================================================
   定数包模板新增/编辑弹窗
   ============================================================ */
.quota-template-dialog {
  width: min(1280px, calc(100vw - 32px)) !important;
  max-height: 92vh !important;
  display: flex !important;
  flex-direction: column;
  background: #ffffff;
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: 0 24px 60px -12px rgba(15, 42, 58, 0.35);
  overflow: hidden;
}

.qtd-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
}

.qtd-dialog-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.qtd-dialog-title p {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.qtd-dialog-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
}

.qtd-dialog-tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.3);
  color: #dcfce7;
  font-size: 12px;
  font-weight: 600;
}

.qtd-dialog-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.qtd-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
  padding: 20px 24px 12px;
  background: #f5f8fa;
  border-bottom: 1px solid #e1e9ee;
  overflow-y: auto;
}

@media (max-width: 900px) {
  .qtd-form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 600px) {
  .qtd-form-grid { grid-template-columns: 1fr; }
}

.qtd-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #566b77;
  font-weight: 500;
}

.qtd-field-wide {
  grid-column: 1 / -1;
}

.qtd-field input,
.qtd-field select {
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid #cfdfe7;
  border-radius: 6px;
  background: #ffffff;
  color: #172b3a;
  font-size: 13px;
  transition: border-color 0.18s, box-shadow 0.18s;
}

.qtd-field input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.qtd-field input[readonly] {
  background: #f0f3f5;
  color: #6b7c8f;
  cursor: not-allowed;
}

.qtd-input-with-btn {
  display: flex;
  gap: 6px;
  align-items: center;
}

.qtd-input-with-btn input {
  flex: 1;
  min-width: 0;
}

.qtd-error {
  margin: 10px 24px 0;
  padding: 8px 12px;
  background: rgba(220, 38, 38, 0.08);
  border: 1px solid rgba(220, 38, 38, 0.2);
  border-radius: 6px;
  color: #dc2626;
  font-size: 13px;
}

.qtd-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 24px 18px;
  background: #f5f8fa;
  border-top: 1px solid #e1e9ee;
}

/* ============================================================
   Shared buttons
   ============================================================ */
.qtd-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 5px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s, transform 0.18s, box-shadow 0.18s, color 0.18s;
  font-family: inherit;
}

.qtd-btn:hover { transform: translateY(-1px); }
.qtd-btn:active { transform: translateY(0); }
.qtd-btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }

.qtd-btn-icon {
  width: 32px; min-height: 32px; padding: 0; justify-content: center;
}

.qtd-btn-primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 4px 10px -4px rgba(99, 102, 241, 0.4);
}
.qtd-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 6px 14px -4px rgba(99, 102, 241, 0.55);
}

.qtd-btn-secondary {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.1), rgba(13, 148, 136, 0.1));
  color: #0d9488; border-color: rgba(20, 184, 166, 0.2);
}
.qtd-btn-secondary:hover:not(:disabled) {
  background: linear-gradient(135deg, #14b8a6, #0d9488);
  color: #fff; box-shadow: 0 4px 10px -4px rgba(20, 184, 166, 0.5);
}

.qtd-btn-info {
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.1), rgba(14, 165, 233, 0.1));
  color: #0891b2; border-color: rgba(6, 182, 212, 0.2);
}
.qtd-btn-info:hover:not(:disabled) {
  background: linear-gradient(135deg, #06b6d4, #0ea5e9);
  color: #fff; box-shadow: 0 4px 10px -4px rgba(6, 182, 212, 0.5);
}

.qtd-btn-ghost {
  width: 32px; min-height: 32px; padding: 0; justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  color: #fff; border-color: rgba(255, 255, 255, 0.3);
}
.qtd-btn-ghost:hover { background: rgba(255, 255, 255, 0.3); }
</style>
