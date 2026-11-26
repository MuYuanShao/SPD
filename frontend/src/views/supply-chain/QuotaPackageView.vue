<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { Teleport } from 'vue'
import { packageReferencePrice } from '../../utils/quotaPackagePricing'
import { useRoute, useRouter } from 'vue-router'
import { ElDialog, ElOption, ElSelect } from 'element-plus'
import { notifyError as notifyErrorShared, notifyWarning } from '../../composables/useNotify'
import {
  ArchiveRestore,
  ArrowRight,
  Ban,
  CheckCircle2,
  ClipboardList,
  Columns3,
  Download,
  Edit3,
  FileDown,
  History,
  PackageCheck,
  Plus,
  Printer,
  RefreshCw,
  Save,
  Search,
  ShieldCheck,
  Upload,
  X
} from '@lucide/vue'
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
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'

import {
  ChevronDown
} from '@lucide/vue'
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
const selectedTaskNo = ref('')
const taskReservations = ref<Record<string, unknown>[]>([])
const taskLoading = ref(false)
const packingCreateOpen = ref(false)
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
  selectedPackingTemplate,
  openTemplateSelector,
  templateSelectorLoading,
  templateSelectorError,
  loadTemplateChoices,
  selectPackingTemplate,
  clearPackingTemplate
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

const { mode, packageSection } = useQuotaPackageViewState({
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

const { loadData: loadQuotaPackageData, invalidateLoad, templateQuery } = useQuotaPackageDataLoader({
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

async function openPackingTaskForTemplate(row: QuotaTemplateRow) {
  const failure = await router.push({ name: 'quota-package', params: { code: 'packing-task-confirmation' } })
  if (!failure && route.params.code === 'packing-task-confirmation') {
    selectPackingTemplate(row)
    packingCreateOpen.value = true
  }
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

/* ===== 可打包散货快照：有效期预警 + 数值格式化 ===== */

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
  recalculateTask,
  viewTaskReservations,
  unpackLabel,
  printLabel
} = useQuotaPackingTaskActions({
  packingForm,
  onTemplateConfirmed: (template) => {
    if (selectedPackingTemplate.value?.templateCode === template.templateCode) {
      selectPackingTemplate({ ...selectedPackingTemplate.value, ...template })
    }
  },
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
  if (tone === 'warning') {
    notifyWarning(text, { duration: 4000, showClose: true })
    return
  }
  notifyErrorShared(text, { duration: 4000, showClose: true })
}

watch(actionResult, (result) => {
  if (!result.text) return
  if (result.tone === 'success') {
    notifySuccess(result.text)
  } else {
    notifyError(result.text, result.tone)
  }
})

/**
 * 加载失败 → 通用消息接口弹出。
 * 原来失败会渲染一条 .pt-load-error 内联错误面板（带「重试」按钮），
 * 既占版面又与全站反馈方式不一致；这里改为统一抛 Element Plus 消息。
 */
watch(loadError, (message) => {
  if (!message) return
  notifyErrorShared(message)
})

async function submitPackingTask() {
  if (!selectedPackingTemplate.value) {
    notifyError('请先点击上方「打包模板」选择模板，再创建任务', 'warning')
    return
  }
  packingFeedback.value = ''
  if (await submitPackingTaskRaw(selectedPackingTemplate.value)) packingCreateOpen.value = false
}

// 选中模板后清空旧的成功提示
watch(() => packingForm.templateCode, (code) => {
  if (code && packingFeedback.value) {
    packingFeedback.value = ''
  }
})

const templateReferencePrice = computed(() => {
  const price = packageReferencePrice(selectedProductInfo.value?.purchasePrice, templateForm.quantity)
  return price == null ? '-' : `¥ ${price}`
})
watch(templates, rows => {
  const current = rows.find(row => row.productCode === templateForm.productCode)
  if (current && selectedProductInfo.value) {
    selectedProductInfo.value.purchasePrice = current.baseUnitPrice == null ? '' : String(current.baseUnitPrice)
  }
})
let activatedOnce = false
onActivated(() => {
  if (activatedOnce) void loadData()
  activatedOnce = true
})
onMounted(loadData)
onDeactivated(invalidateLoad)
watch(() => route.params.code, () => {
  if (route.name !== 'quota-package') return
  if (route.params.code !== 'packing-task-confirmation') packingCreateOpen.value = false
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

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const QUOTASAFETY_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'deptName', label: '科室', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productCode', label: '商品编码', width: 150, visible: true, weight: 3, minWidth: 120, maxWidth: 210 },
  { key: 'productName', label: '商品名称', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'templateCode', label: '模板编码', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 230 },
  { key: 'templateName', label: '模板名称', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'safetyRange', label: '安全区间', width: 150, visible: true, weight: 3, minWidth: 110, maxWidth: 230 },
  { key: 'lowerLimit', label: '下限', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'upperLimit', label: '上限', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'status', label: '状态', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'createTime', label: '创建时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'updateTime', label: '更新时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'actions', label: '操作', width: 140, visible: true, fill: true, minWidth: 120, maxWidth: 220 }
]

const quotaSafetyCols = useListColumns({
  storageKey: 'spd-quota-safety-cols-v1',
  defaults: QUOTASAFETY_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const quotaSafetyTableRef = quotaSafetyCols.tableRef
const isQuotaSafetyColVisible = (key: string) => quotaSafetyCols.isColumnVisible(key)
const quotaSafetyColBinding = (key: string) => quotaSafetyCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const quotaSafetyActionFix = useActionColumnFix()
const quotaSafetyActionFixed = computed(() => quotaSafetyActionFix.actionFixedBinding.value)

watch(
  [quotaSafetyTableRef],
  () => {
    quotaSafetyActionFix.observe(quotaSafetyTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const quotaSafetyColSettingsAnchorRef = ref<HTMLElement | null>(null)
const quotaSafetyColSettingsPopRef = ref<HTMLElement | null>(null)
const quotaSafetyColSettingsPop = useColSettingsPopover({
  open: quotaSafetyCols.settingsOpen,
  anchorRef: quotaSafetyColSettingsAnchorRef,
  popRef: quotaSafetyColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const QUOTALABELEVENT_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'eventNo', label: '事件号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 250 },
  { key: 'labelNo', label: '标签号', width: 180, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'eventType', label: '事件类型', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'statusChange', label: '状态变更', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 210 },
  { key: 'qtyChange', label: '数量变化', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'eventTime', label: '发生时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'remark', label: '备注', width: 220, visible: true, weight: 6, minWidth: 140, maxWidth: 380, fill: true }
]

const quotaLabelEventCols = useListColumns({
  storageKey: 'spd-quota-label-event-cols-v1',
  defaults: QUOTALABELEVENT_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const quotaLabelEventTableRef = quotaLabelEventCols.tableRef
const isQuotaLabelEventColVisible = (key: string) => quotaLabelEventCols.isColumnVisible(key)
const quotaLabelEventColBinding = (key: string) => quotaLabelEventCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const quotaLabelEventColSettingsAnchorRef = ref<HTMLElement | null>(null)
const quotaLabelEventColSettingsPopRef = ref<HTMLElement | null>(null)
const quotaLabelEventColSettingsPop = useColSettingsPopover({
  open: quotaLabelEventCols.settingsOpen,
  anchorRef: quotaLabelEventColSettingsAnchorRef,
  popRef: quotaLabelEventColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const QUOTALABEL_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'labelNo', label: '标签号', width: 180, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'status', label: '状态', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'template', label: '模板', width: 180, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'product', label: '商品', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'packageQty', label: '包数量', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'sourceBatch', label: '来源批次', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'printCount', label: '打印次数', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'generateTime', label: '生成时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'updateTime', label: '修改时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'actions', label: '操作', width: 140, visible: true, fill: true, minWidth: 120, maxWidth: 220 }
]

const quotaLabelCols = useListColumns({
  storageKey: 'spd-quota-label-cols-v1',
  defaults: QUOTALABEL_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const quotaLabelTableRef = quotaLabelCols.tableRef
const isQuotaLabelColVisible = (key: string) => quotaLabelCols.isColumnVisible(key)
const quotaLabelColBinding = (key: string) => quotaLabelCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const quotaLabelActionFix = useActionColumnFix()
const quotaLabelActionFixed = computed(() => quotaLabelActionFix.actionFixedBinding.value)

watch(
  [quotaLabelTableRef],
  () => {
    quotaLabelActionFix.observe(quotaLabelTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const quotaLabelColSettingsAnchorRef = ref<HTMLElement | null>(null)
const quotaLabelColSettingsPopRef = ref<HTMLElement | null>(null)
const quotaLabelColSettingsPop = useColSettingsPopover({
  open: quotaLabelCols.settingsOpen,
  anchorRef: quotaLabelColSettingsAnchorRef,
  popRef: quotaLabelColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const QUOTAPACKINGTASK_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'taskNo', label: '任务号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 250 },
  { key: 'status', label: '状态', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'template', label: '模板', width: 180, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'product', label: '商品', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'packageCount', label: '包数', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'qtyPerPackage', label: '每包数量', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'planDeduct', label: '计划扣减散货', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'reservedQty', label: '预占散货', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'createTime', label: '创建时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'actions', label: '操作', width: 170, visible: true, fill: true, minWidth: 140, maxWidth: 260 }
]

const quotaPackingTaskCols = useListColumns({
  storageKey: 'spd-quota-packing-task-cols-v1',
  defaults: QUOTAPACKINGTASK_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const quotaPackingTaskTableRef = quotaPackingTaskCols.tableRef
const isQuotaPackingTaskColVisible = (key: string) => quotaPackingTaskCols.isColumnVisible(key)
const quotaPackingTaskColBinding = (key: string) => quotaPackingTaskCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const quotaPackingTaskActionFix = useActionColumnFix()
const quotaPackingTaskActionFixed = computed(() => quotaPackingTaskActionFix.actionFixedBinding.value)

watch(
  [quotaPackingTaskTableRef],
  () => {
    quotaPackingTaskActionFix.observe(quotaPackingTaskTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const quotaPackingTaskColSettingsAnchorRef = ref<HTMLElement | null>(null)
const quotaPackingTaskColSettingsPopRef = ref<HTMLElement | null>(null)
const quotaPackingTaskColSettingsPop = useColSettingsPopover({
  open: quotaPackingTaskCols.settingsOpen,
  anchorRef: quotaPackingTaskColSettingsAnchorRef,
  popRef: quotaPackingTaskColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const QUOTARECEIVINGALLOC_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'acceptanceNo', label: '验收单号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 250 },
  { key: 'systemBatch', label: '系统批号', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productionBatch', label: '生产批次', width: 140, visible: true, weight: 4, minWidth: 110, maxWidth: 230 },
  { key: 'product', label: '商品', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'allocatedQty', label: '已分配', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'unitPrice', label: '单价', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'status', label: '状态', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150, fill: true }
]

const quotaReceivingAllocCols = useListColumns({
  storageKey: 'spd-quota-receiving-alloc-cols-v1',
  defaults: QUOTARECEIVINGALLOC_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const quotaReceivingAllocTableRef = quotaReceivingAllocCols.tableRef
const isQuotaReceivingAllocColVisible = (key: string) => quotaReceivingAllocCols.isColumnVisible(key)
const quotaReceivingAllocColBinding = (key: string) => quotaReceivingAllocCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const quotaReceivingAllocColSettingsAnchorRef = ref<HTMLElement | null>(null)
const quotaReceivingAllocColSettingsPopRef = ref<HTMLElement | null>(null)
const quotaReceivingAllocColSettingsPop = useColSettingsPopover({
  open: quotaReceivingAllocCols.settingsOpen,
  anchorRef: quotaReceivingAllocColSettingsAnchorRef,
  popRef: quotaReceivingAllocColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const QUOTALOOSESTOCK_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'product', label: '商品', width: 200, visible: true, weight: 6, minWidth: 140, maxWidth: 340 },
  { key: 'systemBatch', label: '系统批号', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productionBatch', label: '生产批次', width: 140, visible: true, weight: 4, minWidth: 110, maxWidth: 230 },
  { key: 'availableQty', label: '可用散货', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'batchPrice', label: '批次单价', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'unit', label: '单位', width: 90, visible: true, weight: 1, minWidth: 70, maxWidth: 120, fill: true }
]

const quotaLooseStockCols = useListColumns({
  storageKey: 'spd-quota-loose-stock-cols-v1',
  defaults: QUOTALOOSESTOCK_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const quotaLooseStockTableRef = quotaLooseStockCols.tableRef
const isQuotaLooseStockColVisible = (key: string) => quotaLooseStockCols.isColumnVisible(key)
const quotaLooseStockColBinding = (key: string) => quotaLooseStockCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const quotaLooseStockColSettingsAnchorRef = ref<HTMLElement | null>(null)
const quotaLooseStockColSettingsPopRef = ref<HTMLElement | null>(null)
const quotaLooseStockColSettingsPop = useColSettingsPopover({
  open: quotaLooseStockCols.settingsOpen,
  anchorRef: quotaLooseStockColSettingsAnchorRef,
  popRef: quotaLooseStockColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const QUOTATEMPLATE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'selection', label: '', width: 60, visible: true, weight: 1, minWidth: 48, maxWidth: 80 },
  { key: 'templateCode', label: '模板编码', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 230 },
  { key: 'templateName', label: '模板名称', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'productCode', label: '商品编码', width: 150, visible: true, weight: 3, minWidth: 120, maxWidth: 210 },
  { key: 'productName', label: '商品名称', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'spec', label: '规格', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'manufacturer', label: '厂家', width: 170, visible: true, weight: 5, minWidth: 120, maxWidth: 280 },
  { key: 'supplierName', label: '供应商', width: 170, visible: true, weight: 4, minWidth: 120, maxWidth: 270 },
  { key: 'version', label: '版本', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'packageQty', label: '包装数量', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'packageUnitPrice', label: '套包参考单价', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'status', label: '状态', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'createTime', label: '创建时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'updateTime', label: '修改时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'actions', label: '操作', width: 180, visible: true, fill: true, minWidth: 150, maxWidth: 280 }
]

const quotaTemplateCols = useListColumns({
  storageKey: 'spd-quota-template-cols-v1',
  defaults: QUOTATEMPLATE_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const quotaTemplateTableRef = quotaTemplateCols.tableRef
const isQuotaTemplateColVisible = (key: string) => quotaTemplateCols.isColumnVisible(key)
const quotaTemplateColBinding = (key: string) => quotaTemplateCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const quotaTemplateActionFix = useActionColumnFix()
const quotaTemplateActionFixed = computed(() => quotaTemplateActionFix.actionFixedBinding.value)

watch(
  [quotaTemplateTableRef],
  () => {
    quotaTemplateActionFix.observe(quotaTemplateTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const quotaTemplateColSettingsAnchorRef = ref<HTMLElement | null>(null)
const quotaTemplateColSettingsPopRef = ref<HTMLElement | null>(null)
const quotaTemplateColSettingsPop = useColSettingsPopover({
  open: quotaTemplateCols.settingsOpen,
  anchorRef: quotaTemplateColSettingsAnchorRef,
  popRef: quotaTemplateColSettingsPopRef
})

/* ===== 统一列表：可打包散货快照（动态列） ===== */
const QUOTASNAPSHOT_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productCode', label: '商品编码', width: 150, visible: true, weight: 3, minWidth: 120, maxWidth: 210 },
  { key: 'productName', label: '商品名称', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'systemBatchNo', label: '系统批次', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productionBatchNo', label: '生产批号', width: 140, visible: true, weight: 4, minWidth: 110, maxWidth: 230 },
  { key: 'expireDate', label: '有效期', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'batchUnitPrice', label: '批次单价', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'availableQty', label: '可用数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160, fill: true }
]

const quotaSnapshotCols = useListColumns({
  storageKey: 'spd-quota-snapshot-cols-v1',
  defaults: QUOTASNAPSHOT_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const quotaSnapshotTableRef = quotaSnapshotCols.tableRef

/** 快照列的列宽绑定（hybrid 铺满 + 拖拽持久化） */
const quotaSnapshotColBinding = (key: string) => quotaSnapshotCols.columnBinding(key)

/** 当前可见的快照列（列设置里勾选的列） */
const quotaSnapshotVisibleColumns = computed(() =>
  quotaSnapshotCols.columns.value.filter((column) => column.visible)
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const quotaSnapshotColSettingsAnchorRef = ref<HTMLElement | null>(null)
const quotaSnapshotColSettingsPopRef = ref<HTMLElement | null>(null)
const quotaSnapshotColSettingsPop = useColSettingsPopover({
  open: quotaSnapshotCols.settingsOpen,
  anchorRef: quotaSnapshotColSettingsAnchorRef,
  popRef: quotaSnapshotColSettingsPopRef
})

</script>

<template><section class="md-partner-page quota-package-page spd-unified-list">
    <!-- 全局横幅已移除：所有操作反馈统一走通用消息接口（Element Plus ElMessage，失败/警告）
         或当前子菜单的就地提示条（成功），不会跨菜单残留。
         请求失败同样以消息弹层抛出，不再渲染 .pt-load-error 内联错误面板。 -->

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
            <SpdButton label="重置" tone="neutral" :disabled="loading || !hasTemplateQuery" @click="resetTemplateQuery">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton>
            <SpdButton label="查询" tone="primary" type="submit" :disabled="loading">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
          </div>
        </form>

        <div class="tpl-toolbar">
          <div class="tpl-toolbar-main">
            <SpdButton label="新增" tone="primary" v-if="authStore.hasPermission('quota-template-maintenance:write')"
             
             
              aria-label="新增定数包模板"
              title="新增定数包模板"
              @click="openCreateTemplate">
          <template #icon><Plus :size="16" /></template>
        </SpdButton>
            <SpdButton label="修改" tone="primary" v-if="authStore.hasPermission('quota-template-maintenance:write')"
             
             
              :disabled="selectedTemplateCodes.length !== 1"
              @click="openEditTemplate()">
          <template #icon><Edit3 :size="16" /></template>
        </SpdButton>
            <SpdButton label="停用" tone="danger" v-if="authStore.hasPermission('quota-template-maintenance:write')"
             
             
              :disabled="!selectedTemplateCodes.length"
              @click="disableSelectedTemplates()">
          <template #icon><Ban :size="16" /></template>
        </SpdButton>
            <SpdButton label="启用" tone="success" v-if="authStore.hasPermission('quota-template-maintenance:write')"
             
             
              :disabled="!selectedTemplateCodes.length"
              @click="enableSelectedTemplates()">
          <template #icon><CheckCircle2 :size="16" /></template>
        </SpdButton>
            <details class="tpl-import">
              <summary class="tpl-btn">
                <Upload :size="16" />
                导入
              </summary>
              <div class="tpl-import-menu">
                <SpdButton label="导入模板下载" tone="neutral" @click="downloadTemplateImportFile">
                  <template #icon><FileDown :size="16" /></template>
                </SpdButton>
                <SpdButton label="上传模板导入" tone="neutral" @click="triggerTemplateImport">
                  <template #icon><Upload :size="16" /></template>
                </SpdButton>
              </div>
            </details>
            <SpdButton label="导出" tone="neutral" @click="exportTemplates">
          <template #icon><Download :size="16" /></template>
        </SpdButton>
            <input ref="importInput" class="tpl-file-input" type="file" accept=".csv" @change="handleTemplateImport" />
          </div>
        </div>

                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="quotaTemplateColSettingsAnchorRef"
             
             
              @click="quotaTemplateCols.settingsOpen.value = !quotaTemplateCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="quotaTemplateCols.settingsOpen.value"
ref="quotaTemplateColSettingsPopRef"
:class="{ 'is-align-right': quotaTemplateColSettingsPop.align.value === 'right', 'is-align-up': quotaTemplateColSettingsPop.vertical.value === 'up' }"
:style="quotaTemplateColSettingsPop.popStyle.value"
:columns="quotaTemplateCols.columns.value"
:visible-column-count="quotaTemplateCols.visibleColumnCount.value"
@toggle="quotaTemplateCols.setColumnVisible"
@move="quotaTemplateCols.moveColumn"
@reset="quotaTemplateCols.resetColumns()"
@confirm="quotaTemplateCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <template #meta-extra>
            <span class="md-chip primary">共 {{ quotaPagination.templates.total }} 条</span>
            <span v-if="selectedTemplateCodes.length" class="md-chip primary">
              已选 {{ selectedTemplateCodes.length }} 条
            </span>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="quotaTemplateTableRef"
              :data="templates"
              row-key="templateId"
              border
              stripe
              class="ulc-table is-sticky"
              max-height="560"
              style="width: 100%"
              :empty-text="'暂无符合条件的定数包模板'"
              @header-dragend="(width: number, oldWidth: number, column: any) => quotaTemplateCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="selection"
                v-if="isQuotaTemplateColVisible('selection')"
                label=""
                v-bind="quotaTemplateColBinding('selection')"
              >
                <template #header>
                  <input
                    type="checkbox"
                    :checked="allTemplatesSelected"
                    aria-label="选择当前页全部模板"
                    @change="toggleAllTemplates"
                  />
                </template>
                <template #default="{ row }">
                  <input v-model="selectedTemplateCodes" type="checkbox" :value="row.templateCode" :aria-label="`选择模板 ${row.templateCode}`" />
                </template>
              </el-table-column>
              <el-table-column
                column-key="templateCode"
                v-if="isQuotaTemplateColVisible('templateCode')"
                label="模板编码"
                v-bind="quotaTemplateColBinding('templateCode')"
              >
                <template #header>
                  <span class="ulc-th-text">模板编码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text tpl-code">{{ row.templateCode }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="templateName"
                v-if="isQuotaTemplateColVisible('templateName')"
                label="模板名称"
                v-bind="quotaTemplateColBinding('templateName')"
              >
                <template #header>
                  <span class="ulc-th-text">模板名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text tpl-main">{{ row.templateName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productCode"
                v-if="isQuotaTemplateColVisible('productCode')"
                label="商品编码"
                v-bind="quotaTemplateColBinding('productCode')"
              >
                <template #header>
                  <span class="ulc-th-text">商品编码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text tpl-code">{{ row.productCode }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productName"
                v-if="isQuotaTemplateColVisible('productName')"
                label="商品名称"
                v-bind="quotaTemplateColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text tpl-main">{{ row.productName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="spec"
                v-if="isQuotaTemplateColVisible('spec')"
                label="规格"
                v-bind="quotaTemplateColBinding('spec')"
              >
                <template #header>
                  <span class="ulc-th-text">规格</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.specModel || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="manufacturer"
                v-if="isQuotaTemplateColVisible('manufacturer')"
                label="厂家"
                v-bind="quotaTemplateColBinding('manufacturer')"
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
                v-if="isQuotaTemplateColVisible('supplierName')"
                label="供应商"
                v-bind="quotaTemplateColBinding('supplierName')"
              >
                <template #header>
                  <span class="ulc-th-text">供应商</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.supplierName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="version"
                v-if="isQuotaTemplateColVisible('version')"
                label="版本"
                v-bind="quotaTemplateColBinding('version')"
              >
                <template #header>
                  <span class="ulc-th-text">版本</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-cell-stack">
                    <span class="tpl-version">V{{ row.versionNo || 1 }}</span>
                    <span v-if="row.currentVersion" class="tpl-version-current">当前</span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="packageQty"
                v-if="isQuotaTemplateColVisible('packageQty')"
                label="包装数量"
                v-bind="quotaTemplateColBinding('packageQty')"
              >
                <template #header>
                  <span class="ulc-th-text">包装数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-joined">
                    <span class="tpl-qty">{{ formatTemplateQty(row.quantity) }}</span>
                    <span v-if="row.unit" class="tpl-qty-unit">{{ row.unit }}</span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="packageUnitPrice"
                v-if="isQuotaTemplateColVisible('packageUnitPrice')"
                label="套包参考单价"
                v-bind="quotaTemplateColBinding('packageUnitPrice')"
              >
                <template #header>
                  <span class="ulc-th-text">套包参考单价</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.packageUnitPrice == null ? '-' : `¥ ${Number(row.packageUnitPrice).toFixed(4)}` }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="status"
                v-if="isQuotaTemplateColVisible('status')"
                label="状态"
                v-bind="quotaTemplateColBinding('status')"
              >
                <template #header>
                  <span class="ulc-th-text">状态</span>
                </template>
                <template #default="{ row }">
                  <span class="tpl-status" :class="row.status === '禁用' ? 'is-disabled' : 'is-enabled'">
                    <i class="tpl-status-dot" />
                    {{ formatStatusText(row.status) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="createTime"
                v-if="isQuotaTemplateColVisible('createTime')"
                label="创建时间"
                v-bind="quotaTemplateColBinding('createTime')"
              >
                <template #header>
                  <span class="ulc-th-text">创建时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="updateTime"
                v-if="isQuotaTemplateColVisible('updateTime')"
                label="修改时间"
                v-bind="quotaTemplateColBinding('updateTime')"
              >
                <template #header>
                  <span class="ulc-th-text">修改时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.updateTime || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="actions"
                v-if="isQuotaTemplateColVisible('actions')"
                label="操作"
                v-bind="quotaTemplateColBinding('actions')"
                :fixed="quotaTemplateActionFixed"
                align="center"
              >
                <template #header>
                  <span class="ulc-th-text">操作</span>
                </template>
                <template #default="{ row }">
                <div class="ulc-actions">
                  <button v-if="authStore.hasPermission('quota-template-maintenance:write')"
                      @click="openEditTemplate(row)" class="ulc-action tpl-act" type="button"><Edit3 :size="14" />
                      修改</button>
                  <button v-if="authStore.hasPermission('quota-template-maintenance:write')"
                      @click="disableSelectedTemplates(row)" class="ulc-action tpl-act tpl-act-danger" type="button"><Ban :size="14" />
                      停用</button>
                  <button v-if="row.status === '禁用' && authStore.hasPermission('quota-template-maintenance:write')"
                      @click="enableSelectedTemplates(row)" class="ulc-action tpl-act" type="button"><CheckCircle2 :size="14" />
                      启用</button>
                  <button v-if="authStore.hasPermission('quota-packing-task:create') && authStore.canAccessMenu('packing-task-confirmation')"
                      :disabled="row.status !== '启用' && row.status !== 'enabled'"
                      @click="openPackingTaskForTemplate(row)" class="ulc-action tpl-act tpl-act-primary" type="button"><PackageCheck :size="14" />
                      打包</button>
                </div>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无符合条件的定数包模板" />
              </template>
            </el-table>
          </div>
          <template #pagination>
            <MdPagination
              :page="quotaPagination.templates.page"
              :total-pages="Math.max(1, Math.ceil(quotaPagination.templates.total / Math.max(quotaPagination.templates.size, 1)))"
              :total="quotaPagination.templates.total"
              :size="quotaPagination.templates.size"
              :loading="loading"
              @update:page="changeQuotaPage('templates', $event)"
              @update:size="changeQuotaPageSize('templates', $event)"
            />
          </template>
        </MdDataTableCard>
      </section>

      <!-- =========================================================
           打包任务确认
           ========================================================= -->
      <section
        v-if="packageSection === 'packing-task-confirmation'"
        class="pt-page"
      >
        <!-- 分配卡片 -->
        <div class="pt-card receiving-allocate-panel">
          <div class="pt-card-header">
            <div class="pt-card-header-left">
              <span class="pt-card-header-icon">
                <PackageCheck :size="14" />
              </span>
              <h3 class="pt-card-header-title">按验收单分配散货</h3>
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
              <input v-integer-quantity
                v-model.number="allocateQty"
                type="number"
                min="0"
                placeholder="留空为全部"
                class="pt-input"
              />
            </label>
            <div class="pt-form-actions">
              <SpdButton label="查询散货" tone="primary" @click="searchReceivingLooseStock">
          <template #icon><Search :size="14" /></template>
        </SpdButton>
              <SpdButton label="分配" tone="primary" :disabled="!allocateTaskNo || !receivingNo || !looseStockRows.length"
                @click="doAllocateFromReceiving(false)"></SpdButton>
              <SpdButton label="全部打包分配" tone="primary" :disabled="!allocateTaskNo || !receivingNo || !looseStockRows.length"
                @click="doAllocateFromReceiving(true)"></SpdButton>
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
                        <MdDataTableCard>
              <template #toolbar>
              <div class="ulc-col-settings">
                <SpdButton label="列设置" tone="neutral" ref="quotaLooseStockColSettingsAnchorRef"
                 
                 
                  @click="quotaLooseStockCols.settingsOpen.value = !quotaLooseStockCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
                <SpdColumnSettings
v-if="quotaLooseStockCols.settingsOpen.value"
ref="quotaLooseStockColSettingsPopRef"
:class="{ 'is-align-right': quotaLooseStockColSettingsPop.align.value === 'right', 'is-align-up': quotaLooseStockColSettingsPop.vertical.value === 'up' }"
:style="quotaLooseStockColSettingsPop.popStyle.value"
:columns="quotaLooseStockCols.columns.value"
:visible-column-count="quotaLooseStockCols.visibleColumnCount.value"
@toggle="quotaLooseStockCols.setColumnVisible"
@move="quotaLooseStockCols.moveColumn"
@reset="quotaLooseStockCols.resetColumns()"
@confirm="quotaLooseStockCols.settingsOpen.value = false"
          />
              </div>
              </template>
              <div class="ulc-table-wrap">
                <el-table
                  ref="quotaLooseStockTableRef"
                  :data="looseStockRows"
                  border
                  stripe
                  class="ulc-table is-sticky"
                  max-height="560"
                  style="width: 100%"
                  :empty-text="'暂无可分配散货'"
                  @header-dragend="(width: number, oldWidth: number, column: any) => quotaLooseStockCols.handleHeaderDragEnd(width, oldWidth, column)"
                >
                  <el-table-column
                    column-key="product"
                    v-if="isQuotaLooseStockColVisible('product')"
                    label="商品"
                    v-bind="quotaLooseStockColBinding('product')"
                  >
                    <template #header>
                      <span class="ulc-th-text">商品</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.productName }}（{{ row.productCode }}）</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="systemBatch"
                    v-if="isQuotaLooseStockColVisible('systemBatch')"
                    label="系统批号"
                    v-bind="quotaLooseStockColBinding('systemBatch')"
                  >
                    <template #header>
                      <span class="ulc-th-text">系统批号</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.systemBatchNo }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="productionBatch"
                    v-if="isQuotaLooseStockColVisible('productionBatch')"
                    label="生产批次"
                    v-bind="quotaLooseStockColBinding('productionBatch')"
                  >
                    <template #header>
                      <span class="ulc-th-text">生产批次</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.productionBatchNo || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="availableQty"
                    v-if="isQuotaLooseStockColVisible('availableQty')"
                    label="可用散货"
                    v-bind="quotaLooseStockColBinding('availableQty')"
                  >
                    <template #header>
                      <span class="ulc-th-text">可用散货</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.availableQty }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="batchPrice"
                    v-if="isQuotaLooseStockColVisible('batchPrice')"
                    label="批次单价"
                    v-bind="quotaLooseStockColBinding('batchPrice')"
                  >
                    <template #header>
                      <span class="ulc-th-text">批次单价</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.unitPrice }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="unit"
                    v-if="isQuotaLooseStockColVisible('unit')"
                    label="单位"
                    v-bind="quotaLooseStockColBinding('unit')"
                  >
                    <template #header>
                      <span class="ulc-th-text">单位</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.unit }}</span>
                    </template>
                  </el-table-column>
                  <template #empty>
                    <MdEmptyState text="暂无可分配散货" />
                  </template>
                </el-table>
              </div>
            </MdDataTableCard>
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
                        <MdDataTableCard>
              <template #toolbar>
              <div class="ulc-col-settings">
                <SpdButton label="列设置" tone="neutral" ref="quotaReceivingAllocColSettingsAnchorRef"
                 
                 
                  @click="quotaReceivingAllocCols.settingsOpen.value = !quotaReceivingAllocCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
                <SpdColumnSettings
v-if="quotaReceivingAllocCols.settingsOpen.value"
ref="quotaReceivingAllocColSettingsPopRef"
:class="{ 'is-align-right': quotaReceivingAllocColSettingsPop.align.value === 'right', 'is-align-up': quotaReceivingAllocColSettingsPop.vertical.value === 'up' }"
:style="quotaReceivingAllocColSettingsPop.popStyle.value"
:columns="quotaReceivingAllocCols.columns.value"
:visible-column-count="quotaReceivingAllocCols.visibleColumnCount.value"
@toggle="quotaReceivingAllocCols.setColumnVisible"
@move="quotaReceivingAllocCols.moveColumn"
@reset="quotaReceivingAllocCols.resetColumns()"
@confirm="quotaReceivingAllocCols.settingsOpen.value = false"
          />
              </div>
              </template>
              <div class="ulc-table-wrap">
                <el-table
                  ref="quotaReceivingAllocTableRef"
                  :data="receivingAllocations"
                  border
                  stripe
                  class="ulc-table is-sticky"
                  max-height="560"
                  style="width: 100%"
                  :empty-text="'暂无已分配记录'"
                  @header-dragend="(width: number, oldWidth: number, column: any) => quotaReceivingAllocCols.handleHeaderDragEnd(width, oldWidth, column)"
                >
                  <el-table-column
                    column-key="acceptanceNo"
                    v-if="isQuotaReceivingAllocColVisible('acceptanceNo')"
                    label="验收单号"
                    v-bind="quotaReceivingAllocColBinding('acceptanceNo')"
                  >
                    <template #header>
                      <span class="ulc-th-text">验收单号</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.receivingNo }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="systemBatch"
                    v-if="isQuotaReceivingAllocColVisible('systemBatch')"
                    label="系统批号"
                    v-bind="quotaReceivingAllocColBinding('systemBatch')"
                  >
                    <template #header>
                      <span class="ulc-th-text">系统批号</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.systemBatchNo }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="productionBatch"
                    v-if="isQuotaReceivingAllocColVisible('productionBatch')"
                    label="生产批次"
                    v-bind="quotaReceivingAllocColBinding('productionBatch')"
                  >
                    <template #header>
                      <span class="ulc-th-text">生产批次</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.productionBatchNo || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="product"
                    v-if="isQuotaReceivingAllocColVisible('product')"
                    label="商品"
                    v-bind="quotaReceivingAllocColBinding('product')"
                  >
                    <template #header>
                      <span class="ulc-th-text">商品</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.productName }}（{{ row.productCode }}）</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="allocatedQty"
                    v-if="isQuotaReceivingAllocColVisible('allocatedQty')"
                    label="已分配"
                    v-bind="quotaReceivingAllocColBinding('allocatedQty')"
                  >
                    <template #header>
                      <span class="ulc-th-text">已分配</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.reservedQty }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="unitPrice"
                    v-if="isQuotaReceivingAllocColVisible('unitPrice')"
                    label="单价"
                    v-bind="quotaReceivingAllocColBinding('unitPrice')"
                  >
                    <template #header>
                      <span class="ulc-th-text">单价</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.unitPrice }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    column-key="status"
                    v-if="isQuotaReceivingAllocColVisible('status')"
                    label="状态"
                    v-bind="quotaReceivingAllocColBinding('status')"
                  >
                    <template #header>
                      <span class="ulc-th-text">状态</span>
                    </template>
                    <template #default="{ row }">
                      <span class="ulc-wrap-text">{{ row.status }}</span>
                    </template>
                  </el-table-column>
                  <template #empty>
                    <MdEmptyState text="暂无已分配记录" />
                  </template>
                </el-table>
              </div>
            </MdDataTableCard>
          </div>
        </div>

          <ElDialog v-model="packingCreateOpen" title="新建打包任务" width="min(760px, 94vw)" append-to-body
            :close-on-click-modal="false" :close-on-press-escape="!taskLoading" :show-close="!taskLoading">
          <div class="pt-page pt-create-form">
          <div class="pt-form-grid">
            <div class="pt-form-field pt-template-controls">
              <span class="pt-form-label">打包模板 <em class="pt-form-required">*</em></span>
              <div class="pt-template-controls-actions">
                <SpdButton tone="primary" :disabled="taskLoading" @click="openTemplateSelector">
          <template #icon><Search :size="14" /></template>
          {{ selectedPackingTemplate ? '更换打包模板' : '选择打包模板' }}
        </SpdButton>
                <SpdButton label="清除" tone="primary" v-if="selectedPackingTemplate"
                  :disabled="taskLoading" @click="clearPackingTemplate">
          <template #icon><X :size="14" /></template>
        </SpdButton>
              </div>
            </div>
            <label class="pt-form-field">
              <span class="pt-form-label">模板编码</span>
              <input class="pt-input pt-template-readonly" :value="selectedPackingTemplate?.templateCode" readonly placeholder="—" />
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">模板名称</span>
              <input class="pt-input pt-template-readonly" :value="selectedPackingTemplate?.productName" readonly placeholder="—" />
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">商品名称</span>
              <input class="pt-input pt-template-readonly" :value="selectedPackingTemplate?.productName" readonly placeholder="—" />
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">规格型号</span>
              <input class="pt-input pt-template-readonly" :value="selectedPackingTemplate?.specModel" readonly placeholder="—" />
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">生产厂家</span>
              <input class="pt-input pt-template-readonly" :value="selectedPackingTemplate?.manufacturerName" readonly placeholder="—" />
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">每包数量</span>
              <input class="pt-input pt-template-readonly" readonly placeholder="—"
                :value="selectedPackingTemplate ? `${selectedPackingTemplate.quantity} ${selectedPackingTemplate.unit}/包` : ''" />
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">库房</span>
              <ElSelect v-model="packingForm.warehouseName" class="pt-warehouse-select"
                aria-label="库房" placeholder="搜索或选择库房" filterable :disabled="taskLoading">
                <ElOption
                  v-for="item in warehouses"
                  :key="item.warehouseName"
                  :label="item.warehouseName"
                  :value="item.warehouseName"
                />
              </ElSelect>
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">打包数量</span>
              <input v-integer-quantity
                v-model.number="packingForm.packageCount"
                :disabled="taskLoading"
                type="number"
                min="1"
                class="pt-input"
              />
            </label>
            <label class="pt-form-field">
              <span class="pt-form-label">备注</span>
              <input
                v-model="packingForm.remark"
                :disabled="taskLoading"
                placeholder="任务说明"
                class="pt-input"
              />
            </label>
          </div>
          <div class="pt-form-actions">
              <SpdButton label="取消" tone="neutral" :disabled="taskLoading" @click="packingCreateOpen = false"></SpdButton>
              <SpdButton tone="primary" v-if="authStore.hasPermission('quota-packing-task:create')"
               
               
                :disabled="taskLoading || !selectedPackingTemplate || !packingForm.warehouseName"
                :title="!packingForm.templateCode ? '请先选择打包模板' : '创建打包任务'"
                @click="submitPackingTask">
          <template #icon><Save :size="14" /></template>
          <span>{{ taskLoading ? '创建中...' : '确认创建' }}</span>
        </SpdButton>
            </div>
          </div>
          </ElDialog>

        <div class="pt-card">

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
                    <MdDataTableCard>
            <template #toolbar>
            <div class="ulc-col-settings">
              <SpdButton label="列设置" tone="neutral" ref="quotaPackingTaskColSettingsAnchorRef"
               
               
                @click="quotaPackingTaskCols.settingsOpen.value = !quotaPackingTaskCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
              <SpdColumnSettings
v-if="quotaPackingTaskCols.settingsOpen.value"
ref="quotaPackingTaskColSettingsPopRef"
:class="{ 'is-align-right': quotaPackingTaskColSettingsPop.align.value === 'right', 'is-align-up': quotaPackingTaskColSettingsPop.vertical.value === 'up' }"
:style="quotaPackingTaskColSettingsPop.popStyle.value"
:columns="quotaPackingTaskCols.columns.value"
:visible-column-count="quotaPackingTaskCols.visibleColumnCount.value"
@toggle="quotaPackingTaskCols.setColumnVisible"
@move="quotaPackingTaskCols.moveColumn"
@reset="quotaPackingTaskCols.resetColumns()"
@confirm="quotaPackingTaskCols.settingsOpen.value = false"
          />
            </div>
            </template>
            <div class="ulc-table-wrap">
              <el-table
                ref="quotaPackingTaskTableRef"
                :data="tasks"
                row-key="taskNo"
                border
                stripe
                class="ulc-table is-sticky"
                max-height="560"
                style="width: 100%"
                :empty-text="'暂无打包任务'"
                @header-dragend="(width: number, oldWidth: number, column: any) => quotaPackingTaskCols.handleHeaderDragEnd(width, oldWidth, column)"
              >
                <el-table-column
                  column-key="taskNo"
                  v-if="isQuotaPackingTaskColVisible('taskNo')"
                  label="任务号"
                  v-bind="quotaPackingTaskColBinding('taskNo')"
                >
                  <template #header>
                    <span class="ulc-th-text">任务号</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.taskNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="status"
                  v-if="isQuotaPackingTaskColVisible('status')"
                  label="状态"
                  v-bind="quotaPackingTaskColBinding('status')"
                >
                  <template #header>
                    <span class="ulc-th-text">状态</span>
                  </template>
                  <template #default="{ row }">
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
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="template"
                  v-if="isQuotaPackingTaskColVisible('template')"
                  label="模板"
                  v-bind="quotaPackingTaskColBinding('template')"
                >
                  <template #header>
                    <span class="ulc-th-text">模板</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.templateName }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="warehouseName"
                  v-if="isQuotaPackingTaskColVisible('warehouseName')"
                  label="库房"
                  v-bind="quotaPackingTaskColBinding('warehouseName')"
                >
                  <template #header>
                    <span class="ulc-th-text">库房</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.warehouseName }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="product"
                  v-if="isQuotaPackingTaskColVisible('product')"
                  label="商品"
                  v-bind="quotaPackingTaskColBinding('product')"
                >
                  <template #header>
                    <span class="ulc-th-text">商品</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.productName }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="packageCount"
                  v-if="isQuotaPackingTaskColVisible('packageCount')"
                  label="包数"
                  v-bind="quotaPackingTaskColBinding('packageCount')"
                >
                  <template #header>
                    <span class="ulc-th-text">包数</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.packageCount }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="qtyPerPackage"
                  v-if="isQuotaPackingTaskColVisible('qtyPerPackage')"
                  label="每包数量"
                  v-bind="quotaPackingTaskColBinding('qtyPerPackage')"
                >
                  <template #header>
                    <span class="ulc-th-text">每包数量</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.packageQuantity }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="planDeduct"
                  v-if="isQuotaPackingTaskColVisible('planDeduct')"
                  label="计划扣减散货"
                  v-bind="quotaPackingTaskColBinding('planDeduct')"
                >
                  <template #header>
                    <span class="ulc-th-text">计划扣减散货</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.plannedLooseQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="reservedQty"
                  v-if="isQuotaPackingTaskColVisible('reservedQty')"
                  label="预占散货"
                  v-bind="quotaPackingTaskColBinding('reservedQty')"
                >
                  <template #header>
                    <span class="ulc-th-text">预占散货</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.reservedLooseQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="createTime"
                  v-if="isQuotaPackingTaskColVisible('createTime')"
                  label="创建时间"
                  v-bind="quotaPackingTaskColBinding('createTime')"
                >
                  <template #header>
                    <span class="ulc-th-text">创建时间</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.createTime }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="actions"
                  v-if="isQuotaPackingTaskColVisible('actions')"
                  label="操作"
                  v-bind="quotaPackingTaskColBinding('actions')"
                  :fixed="quotaPackingTaskActionFixed"
                  align="center"
                >
                  <template #header>
                    <span class="ulc-th-text">操作</span>
                  </template>
                  <template #default="{ row }">
                  <div class="ulc-actions">
                    <button v-if="row.status === 'pending_confirm' && authStore.hasPermission('quota-packing-task:confirm')"
                        @click="approveTask(row)" class="ulc-action pt-btn-text" type="button"><CheckCircle2 :size="13" />
                        确认</button>
                    <button v-if="(row.status === 'pending_confirm' || row.status === 'need_recalculate') && authStore.hasPermission('quota-packing-task:recalculate')"
                        @click="recalculateTask(row)" class="ulc-action pt-btn-text" type="button"><RefreshCw :size="13" />
                        重算</button>
                    <button v-if="(row.status === 'pending_confirm' || row.status === 'need_recalculate') && authStore.hasPermission('quota-packing-task:cancel')"
                        @click="cancelTask(row)" class="ulc-action pt-btn-text pt-btn-text-danger" type="button"><X :size="13" />
                        取消</button>
                    <button v-if="(row.status === 'pending_confirm' || row.status === 'need_recalculate' || row.status === 'confirmed') && authStore.hasPermission('quota-packing-task:terminate')"
                        @click="terminateTask(row)" class="ulc-action pt-btn-text pt-btn-text-danger" type="button"><Ban :size="13" />
                        终止</button>
                    <button @click="viewTaskReservations(row)" class="ulc-action pt-btn-text" type="button"><ClipboardList :size="13" />
                        预占明细</button>
                  </div>
                  </template>
                </el-table-column>
                <template #empty>
                  <MdEmptyState text="暂无打包任务" />
                </template>
              </el-table>
            </div>
            <template #pagination>
              <MdPagination
                :page="quotaPagination.tasks.page"
                :total-pages="Math.max(1, Math.ceil(quotaPagination.tasks.total / Math.max(quotaPagination.tasks.size, 1)))"
                :total="quotaPagination.tasks.total"
                :size="quotaPagination.tasks.size"
                :loading="loading"
                @update:page="changeQuotaPage('tasks', $event)"
                @update:size="changeQuotaPageSize('tasks', $event)"
              />
            </template>
          </MdDataTableCard>
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
            <SpdButton label="查询" tone="primary" :disabled="loading" @click="loadData">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
            <SpdButton label="重置" tone="neutral" :disabled="loading || !hasLabelQuery" @click="resetLabelQuery">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton>
          </div>
        </div>

        <!-- 标签列表 -->
                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="quotaLabelColSettingsAnchorRef"
             
             
              @click="quotaLabelCols.settingsOpen.value = !quotaLabelCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="quotaLabelCols.settingsOpen.value"
ref="quotaLabelColSettingsPopRef"
:class="{ 'is-align-right': quotaLabelColSettingsPop.align.value === 'right', 'is-align-up': quotaLabelColSettingsPop.vertical.value === 'up' }"
:style="quotaLabelColSettingsPop.popStyle.value"
:columns="quotaLabelCols.columns.value"
:visible-column-count="quotaLabelCols.visibleColumnCount.value"
@toggle="quotaLabelCols.setColumnVisible"
@move="quotaLabelCols.moveColumn"
@reset="quotaLabelCols.resetColumns()"
@confirm="quotaLabelCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="quotaLabelTableRef"
              :data="labels"
              row-key="labelNo"
              border
              stripe
              class="ulc-table is-sticky"
              max-height="560"
              style="width: 100%"
              :empty-text="'暂无标签'"
              @header-dragend="(width: number, oldWidth: number, column: any) => quotaLabelCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="labelNo"
                v-if="isQuotaLabelColVisible('labelNo')"
                label="标签号"
                v-bind="quotaLabelColBinding('labelNo')"
              >
                <template #header>
                  <span class="ulc-th-text">标签号</span>
                </template>
                <template #default="{ row }">
                  <span class="lbl-no">{{ row.labelNo }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="status"
                v-if="isQuotaLabelColVisible('status')"
                label="状态"
                v-bind="quotaLabelColBinding('status')"
              >
                <template #header>
                  <span class="ulc-th-text">状态</span>
                </template>
                <template #default="{ row }">
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
                </template>
              </el-table-column>
              <el-table-column
                column-key="template"
                v-if="isQuotaLabelColVisible('template')"
                label="模板"
                v-bind="quotaLabelColBinding('template')"
              >
                <template #header>
                  <span class="ulc-th-text">模板</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-cell-stack">
                    <span class="lbl-main">{{ row.templateName || '-' }}</span>
                    <span class="lbl-sub">{{ row.templateCode || '-' }}</span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="warehouseName"
                v-if="isQuotaLabelColVisible('warehouseName')"
                label="库房"
                v-bind="quotaLabelColBinding('warehouseName')"
              >
                <template #header>
                  <span class="ulc-th-text">库房</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.warehouseName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="product"
                v-if="isQuotaLabelColVisible('product')"
                label="商品"
                v-bind="quotaLabelColBinding('product')"
              >
                <template #header>
                  <span class="ulc-th-text">商品</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-cell-stack">
                    <span class="lbl-main">{{ row.productName || '-' }}</span>
                    <span class="lbl-sub lbl-mono">{{ row.productCode || '-' }}</span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="packageQty"
                v-if="isQuotaLabelColVisible('packageQty')"
                label="包数量"
                v-bind="quotaLabelColBinding('packageQty')"
              >
                <template #header>
                  <span class="ulc-th-text">包数量</span>
                </template>
                <template #default="{ row }">
                  <span class="lbl-qty">{{ row.packageQuantity }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="sourceBatch"
                v-if="isQuotaLabelColVisible('sourceBatch')"
                label="来源批次"
                v-bind="quotaLabelColBinding('sourceBatch')"
              >
                <template #header>
                  <span class="ulc-th-text">来源批次</span>
                </template>
                <template #default="{ row }">
                  <span v-if="row.sourceBatches" class="lbl-source" :title="row.sourceBatches">{{ row.sourceBatches }}</span>
                  <span v-else class="lbl-sub">-</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="printCount"
                v-if="isQuotaLabelColVisible('printCount')"
                label="打印次数"
                v-bind="quotaLabelColBinding('printCount')"
              >
                <template #header>
                  <span class="ulc-th-text">打印次数</span>
                </template>
                <template #default="{ row }">
                  <span class="lbl-print" :class="{ 'is-zero': !row.printCount }">{{ row.printCount ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="generateTime"
                v-if="isQuotaLabelColVisible('generateTime')"
                label="生成时间"
                v-bind="quotaLabelColBinding('generateTime')"
              >
                <template #header>
                  <span class="ulc-th-text">生成时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="updateTime"
                v-if="isQuotaLabelColVisible('updateTime')"
                label="修改时间"
                v-bind="quotaLabelColBinding('updateTime')"
              >
                <template #header>
                  <span class="ulc-th-text">修改时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.updateTime || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="actions"
                v-if="isQuotaLabelColVisible('actions')"
                label="操作"
                v-bind="quotaLabelColBinding('actions')"
                :fixed="quotaLabelActionFixed"
                align="center"
              >
                <template #header>
                  <span class="ulc-th-text">操作</span>
                </template>
                <template #default="{ row }">
                <div class="ulc-actions">
                  <button v-if="(row.status === 'pending_print' || row.status === 'available') && authStore.hasPermission('quota-package-label:print')"
                      @click="printLabel(row)" class="ulc-action lbl-act" type="button"><Printer :size="14" />
                      {{ row.status === 'pending_print' ? '打印' : '重打' }}</button>
                  <button v-if="row.status === 'available' && authStore.hasPermission('quota-label-unpack:write')"
                      @click="unpackLabel(row)" class="ulc-action lbl-act lbl-act-danger" type="button"><ArchiveRestore :size="14" />
                      解包</button>
                </div>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无标签" />
              </template>
            </el-table>
          </div>
          <template #pagination>
            <MdPagination
              :page="quotaPagination.labels.page"
              :total-pages="Math.max(1, Math.ceil(quotaPagination.labels.total / Math.max(quotaPagination.labels.size, 1)))"
              :total="quotaPagination.labels.total"
              :size="quotaPagination.labels.size"
              :loading="loading"
              @update:page="changeQuotaPage('labels', $event)"
              @update:size="changeQuotaPageSize('labels', $event)"
            />
          </template>
        </MdDataTableCard>
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
            <SpdButton label="查询" tone="primary" :disabled="loading" @click="onEventQuerySubmit">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
            <SpdButton label="重置" tone="neutral" :disabled="loading || !hasEventQuery" @click="resetEventQuery">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton>
          </div>
        </div>

                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="quotaLabelEventColSettingsAnchorRef"
             
             
              @click="quotaLabelEventCols.settingsOpen.value = !quotaLabelEventCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="quotaLabelEventCols.settingsOpen.value"
ref="quotaLabelEventColSettingsPopRef"
:class="{ 'is-align-right': quotaLabelEventColSettingsPop.align.value === 'right', 'is-align-up': quotaLabelEventColSettingsPop.vertical.value === 'up' }"
:style="quotaLabelEventColSettingsPop.popStyle.value"
:columns="quotaLabelEventCols.columns.value"
:visible-column-count="quotaLabelEventCols.visibleColumnCount.value"
@toggle="quotaLabelEventCols.setColumnVisible"
@move="quotaLabelEventCols.moveColumn"
@reset="quotaLabelEventCols.resetColumns()"
@confirm="quotaLabelEventCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="quotaLabelEventTableRef"
              :data="events"
              row-key="eventNo"
              border
              stripe
              class="ulc-table is-sticky"
              max-height="560"
              style="width: 100%"
              :empty-text="'暂无标签事件'"
              @header-dragend="(width: number, oldWidth: number, column: any) => quotaLabelEventCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="eventNo"
                v-if="isQuotaLabelEventColVisible('eventNo')"
                label="事件号"
                v-bind="quotaLabelEventColBinding('eventNo')"
              >
                <template #header>
                  <span class="ulc-th-text">事件号</span>
                </template>
                <template #default="{ row }">
                  <span class="evt-no">{{ row.eventNo }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="labelNo"
                v-if="isQuotaLabelEventColVisible('labelNo')"
                label="标签号"
                v-bind="quotaLabelEventColBinding('labelNo')"
              >
                <template #header>
                  <span class="ulc-th-text">标签号</span>
                </template>
                <template #default="{ row }">
                  <span v-if="row.labelNo" class="ulc-wrap-text evt-label-no">{{ row.labelNo }}</span>
                  <span v-else class="evt-sub">-</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="eventType"
                v-if="isQuotaLabelEventColVisible('eventType')"
                label="事件类型"
                v-bind="quotaLabelEventColBinding('eventType')"
              >
                <template #header>
                  <span class="ulc-th-text">事件类型</span>
                </template>
                <template #default="{ row }">
                  <span class="evt-type" :class="eventTone(row.eventType)">
                    <i class="evt-type-dot" />
                    {{ formatBusinessText(row.eventType) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="statusChange"
                v-if="isQuotaLabelEventColVisible('statusChange')"
                label="状态变更"
                v-bind="quotaLabelEventColBinding('statusChange')"
              >
                <template #header>
                  <span class="ulc-th-text">状态变更</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-joined evt-flow">
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
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="qtyChange"
                v-if="isQuotaLabelEventColVisible('qtyChange')"
                label="数量变化"
                v-bind="quotaLabelEventColBinding('qtyChange')"
              >
                <template #header>
                  <span class="ulc-th-text">数量变化</span>
                </template>
                <template #default="{ row }">
                  <span v-if="hasQtyChange(row.qtyChange)" class="evt-qty" :class="qtyTone(row.qtyChange)">
                    {{ formatQtyChange(row.qtyChange) }}
                  </span>
                  <span v-else class="evt-sub">无变化</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="eventTime"
                v-if="isQuotaLabelEventColVisible('eventTime')"
                label="发生时间"
                v-bind="quotaLabelEventColBinding('eventTime')"
              >
                <template #header>
                  <span class="ulc-th-text">发生时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.eventTime || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="remark"
                v-if="isQuotaLabelEventColVisible('remark')"
                label="备注"
                v-bind="quotaLabelEventColBinding('remark')"
              >
                <template #header>
                  <span class="ulc-th-text">备注</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ formatRemarkText(row.remark) }}</span>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无标签事件" />
              </template>
            </el-table>
          </div>
          <template #pagination>
            <MdPagination
              :page="quotaPagination.events.page"
              :total-pages="Math.max(1, Math.ceil(quotaPagination.events.total / Math.max(quotaPagination.events.size, 1)))"
              :total="quotaPagination.events.total"
              :size="quotaPagination.events.size"
              :loading="loading"
              @update:page="changeQuotaPage('events', $event)"
              @update:size="changeQuotaPageSize('events', $event)"
            />
          </template>
        </MdDataTableCard>
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
            <SpdButton label="查询" tone="primary" :disabled="loading" @click="onSnapshotQuery">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
            <SpdButton label="重置" tone="neutral" :disabled="loading || !hasSnapshotQuery" @click="resetSnapshotQuery">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton>
          </div>
        </div>

        <MdDataTableCard>
          <template #toolbar>
            <div class="ulc-col-settings">
              <SpdButton label="列设置" tone="neutral" ref="quotaSnapshotColSettingsAnchorRef"
               
               
                @click="quotaSnapshotCols.settingsOpen.value = !quotaSnapshotCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
              <SpdColumnSettings
v-if="quotaSnapshotCols.settingsOpen.value"
ref="quotaSnapshotColSettingsPopRef"
:class="{ 'is-align-right': quotaSnapshotColSettingsPop.align.value === 'right', 'is-align-up': quotaSnapshotColSettingsPop.vertical.value === 'up' }"
:style="quotaSnapshotColSettingsPop.popStyle.value"
:columns="quotaSnapshotCols.columns.value"
:visible-column-count="quotaSnapshotCols.visibleColumnCount.value"
@toggle="quotaSnapshotCols.setColumnVisible"
@move="quotaSnapshotCols.moveColumn"
@reset="quotaSnapshotCols.resetColumns()"
@confirm="quotaSnapshotCols.settingsOpen.value = false"
          />
            </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="quotaSnapshotTableRef"
              :data="candidates"
              border
              stripe
              class="ulc-table is-sticky"
              max-height="560"
              style="width: 100%"
              :empty-text="'当前没有可打包散货'"
              @header-dragend="(width: number, oldWidth: number, column: any) => quotaSnapshotCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                v-for="snapColumn in quotaSnapshotVisibleColumns"
                :key="snapColumn.key"
                :column-key="snapColumn.key"
                :label="snapColumn.label"
                v-bind="quotaSnapshotColBinding(snapColumn.key)"
              >
                <template #header>
                  <span class="ulc-th-text">{{ snapColumn.label }}</span>
                </template>
                <template #default="{ row }">
                  <template v-if="snapColumn.key === 'productName'">
                    <span class="ulc-cell-stack">
                      <span class="snp-main">{{ row.productName || '-' }}</span>
                      <span v-if="row.productCode" class="snp-sub snp-mono">{{ row.productCode }}</span>
                    </span>
                  </template>

                  <template v-else-if="snapColumn.key === 'systemBatchNo'">
                    <span class="ulc-wrap-text snp-batch">{{ row.systemBatchNo || '-' }}</span>
                  </template>

                  <template v-else-if="snapColumn.key === 'expireDate'">
                    <span
                      v-if="row.expireDate"
                      class="ulc-wrap-text snp-expire"
                      :class="expiryTone(row.expireDate)"
                      :title="expiryTitle(row.expireDate)"
                    >
                      {{ row.expireDate }}
                    </span>
                    <span v-else class="snp-sub">-</span>
                  </template>

                  <template v-else-if="snapColumn.key === 'batchUnitPrice'">
                    <span class="ulc-wrap-text ulc-number snp-price">¥ {{ formatSnapshotPrice(row.batchUnitPrice) }}</span>
                  </template>

                  <template v-else-if="snapColumn.key === 'availableQty'">
                    <span class="ulc-wrap-text ulc-number snp-qty">{{ formatSnapshotQty(row.availableQty) }}</span>
                  </template>

                  <template v-else-if="snapColumn.key === 'productionBatchNo'">
                    <span class="ulc-wrap-text snp-mono">{{ row.productionBatchNo || '-' }}</span>
                  </template>

                  <template v-else>
                    <span class="ulc-wrap-text">{{ row[snapColumn.key] ?? '-' }}</span>
                  </template>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="当前没有可打包散货" />
              </template>
            </el-table>
          </div>
          <template #pagination>
            <MdPagination
              :page="quotaPagination.candidates.page"
              :total-pages="Math.max(1, Math.ceil(quotaPagination.candidates.total / Math.max(quotaPagination.candidates.size, 1)))"
              :total="quotaPagination.candidates.total"
              :size="quotaPagination.candidates.size"
              :loading="loading"
              @update:page="changeQuotaPage('candidates', $event)"
              @update:size="changeQuotaPageSize('candidates', $event)"
            />
          </template>
        </MdDataTableCard>
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
            <SpdButton label="新增维护" tone="primary" v-if="authStore.hasPermission('quota-safety-stock:write')"
             
             
              @click="openSafetyDialog()">
          <template #icon><Plus :size="16" /></template>
        </SpdButton>
            <SpdButton tone="primary" v-if="authStore.hasPermission('quota-safety-stock:write')"
             
             
              :disabled="safetyImporting"
              @click="triggerSafetyImport">
          <template #icon><Upload :size="16" /></template>
          {{ safetyImporting ? '导入中' : '导入' }}
        </SpdButton>
            <details class="saf-import">
              <summary class="saf-btn">
                <FileDown :size="16" />
                导入模板
              </summary>
              <div class="saf-import-menu">
                <SpdButton label="下载导入模板" tone="neutral" @click="downloadSafetyImportFile">
                  <template #icon><FileDown :size="16" /></template>
                </SpdButton>
              </div>
            </details>
            <SpdButton label="导出" tone="neutral" @click="exportSafetyRows">
          <template #icon><Download :size="16" /></template>
        </SpdButton>
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
            <SpdButton label="重置" tone="neutral" :disabled="loading || !hasSafetyQuery" @click="resetSafetyQuery">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton>
            <SpdButton label="查询" tone="primary" :disabled="loading" @click="onSafetyQuery">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
          </div>
        </div>

                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="quotaSafetyColSettingsAnchorRef"
             
             
              @click="quotaSafetyCols.settingsOpen.value = !quotaSafetyCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="quotaSafetyCols.settingsOpen.value"
ref="quotaSafetyColSettingsPopRef"
:class="{ 'is-align-right': quotaSafetyColSettingsPop.align.value === 'right', 'is-align-up': quotaSafetyColSettingsPop.vertical.value === 'up' }"
:style="quotaSafetyColSettingsPop.popStyle.value"
:columns="quotaSafetyCols.columns.value"
:visible-column-count="quotaSafetyCols.visibleColumnCount.value"
@toggle="quotaSafetyCols.setColumnVisible"
@move="quotaSafetyCols.moveColumn"
@reset="quotaSafetyCols.resetColumns()"
@confirm="quotaSafetyCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="quotaSafetyTableRef"
              :data="safetyRows"
              row-key="templateCode"
              border
              stripe
              class="ulc-table is-sticky"
              max-height="560"
              style="width: 100%"
              :empty-text="'暂无安全库存配置'"
              @header-dragend="(width: number, oldWidth: number, column: any) => quotaSafetyCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="deptName"
                v-if="isQuotaSafetyColVisible('deptName')"
                label="科室"
                v-bind="quotaSafetyColBinding('deptName')"
              >
                <template #header>
                  <span class="ulc-th-text">科室</span>
                </template>
                <template #default="{ row }">
                  <span class="saf-dept">{{ row.deptName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productCode"
                v-if="isQuotaSafetyColVisible('productCode')"
                label="商品编码"
                v-bind="quotaSafetyColBinding('productCode')"
              >
                <template #header>
                  <span class="ulc-th-text">商品编码</span>
                </template>
                <template #default="{ row }">
                  <span class="saf-code">{{ row.productCode }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productName"
                v-if="isQuotaSafetyColVisible('productName')"
                label="商品名称"
                v-bind="quotaSafetyColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text saf-main">{{ row.productName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="templateCode"
                v-if="isQuotaSafetyColVisible('templateCode')"
                label="模板编码"
                v-bind="quotaSafetyColBinding('templateCode')"
              >
                <template #header>
                  <span class="ulc-th-text">模板编码</span>
                </template>
                <template #default="{ row }">
                  <span v-if="row.templateCode && row.templateCode !== '-'" class="saf-code">
                    {{ row.templateCode }}
                  </span>
                  <span v-else class="saf-sub">未指定</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="templateName"
                v-if="isQuotaSafetyColVisible('templateName')"
                label="模板名称"
                v-bind="quotaSafetyColBinding('templateName')"
              >
                <template #header>
                  <span class="ulc-th-text">模板名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.templateName && row.templateName !== '-' ? row.templateName : '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="safetyRange"
                v-if="isQuotaSafetyColVisible('safetyRange')"
                label="安全区间"
                v-bind="quotaSafetyColBinding('safetyRange')"
              >
                <template #header>
                  <span class="ulc-th-text">安全区间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-joined saf-range" :title="`安全下限 ${row.minQty}，安全上限 ${row.maxQty}`">
                    <span class="saf-range-track">
                      <i class="saf-range-fill" :style="safetyRangeStyle(row)" />
                    </span>
                    <span class="saf-range-text">
                      {{ formatSafetyQty(row.minQty) }} – {{ formatSafetyQty(row.maxQty) }}
                    </span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="lowerLimit"
                v-if="isQuotaSafetyColVisible('lowerLimit')"
                label="下限"
                v-bind="quotaSafetyColBinding('lowerLimit')"
              >
                <template #header>
                  <span class="ulc-th-text">下限</span>
                </template>
                <template #default="{ row }">
                  <span class="saf-qty">{{ formatSafetyQty(row.minQty) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="upperLimit"
                v-if="isQuotaSafetyColVisible('upperLimit')"
                label="上限"
                v-bind="quotaSafetyColBinding('upperLimit')"
              >
                <template #header>
                  <span class="ulc-th-text">上限</span>
                </template>
                <template #default="{ row }">
                  <span class="saf-qty is-max">{{ formatSafetyQty(row.maxQty) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="status"
                v-if="isQuotaSafetyColVisible('status')"
                label="状态"
                v-bind="quotaSafetyColBinding('status')"
              >
                <template #header>
                  <span class="ulc-th-text">状态</span>
                </template>
                <template #default="{ row }">
                  <span class="saf-status" :class="isSafetyEnabled(row.status) ? 'is-enabled' : 'is-disabled'">
                    <i class="saf-status-dot" />
                    {{ formatStatusText(row.status) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="createTime"
                v-if="isQuotaSafetyColVisible('createTime')"
                label="创建时间"
                v-bind="quotaSafetyColBinding('createTime')"
              >
                <template #header>
                  <span class="ulc-th-text">创建时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="updateTime"
                v-if="isQuotaSafetyColVisible('updateTime')"
                label="更新时间"
                v-bind="quotaSafetyColBinding('updateTime')"
              >
                <template #header>
                  <span class="ulc-th-text">更新时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.updateTime || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="actions"
                v-if="isQuotaSafetyColVisible('actions')"
                label="操作"
                v-bind="quotaSafetyColBinding('actions')"
                :fixed="quotaSafetyActionFixed"
                align="center"
              >
                <template #header>
                  <span class="ulc-th-text">操作</span>
                </template>
                <template #default="{ row }">
                <div class="ulc-actions">
                  <button v-if="authStore.hasPermission('quota-safety-stock:write')"
                      @click="openSafetyDialog(row)" class="ulc-action saf-act" type="button"><Edit3 :size="14" />
                      编辑</button>
                </div>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无安全库存配置" />
              </template>
            </el-table>
          </div>
          <template #pagination>
            <MdPagination
              :page="quotaPagination.safety.page"
              :total-pages="Math.max(1, Math.ceil(quotaPagination.safety.total / Math.max(quotaPagination.safety.size, 1)))"
              :total="quotaPagination.safety.total"
              :size="quotaPagination.safety.size"
              :loading="loading"
              @update:page="changeQuotaPage('safety', $event)"
              @update:size="changeQuotaPageSize('safety', $event)"
            />
          </template>
        </MdDataTableCard>
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
          <SpdButton label="" tone="primary" aria-label="关闭" @click="templateDialogOpen = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
              <span>模板名称</span>
              <input
                readonly
                :value="selectedProductInfo?.productName || ''"
                placeholder="选择商品后自动回填"
              />
            </label>
            <label class="qtd-field qtd-field-wide">
              <span>商品编码</span>
              <div class="qtd-input-with-btn">
                <input v-model="templateForm.productCode" readonly placeholder="点击右侧按钮选择已启用定数管理的商品" />
                <SpdButton label="" tone="primary" icon-only title="选择商品" @click="openProductSelector">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
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
                <span>套包参考单价</span>
                <input :value="templateReferencePrice" readonly />
                <small>目录基本单位价 × 包内数量</small>
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
              <input v-integer-quantity v-model.number="templateForm.quantity" type="number" min="1" />
            </label>
            <label class="qtd-field">
              <span>套包单位</span>
              <input value="包" readonly />
            </label>
          </div>
          <p v-if="templateError" class="qtd-error">{{ templateError }}</p>
          <div class="qtd-dialog-actions">
            <SpdButton label="取消" tone="neutral" @click="templateDialogOpen = false"></SpdButton>
            <SpdButton label="保存" tone="success" @click="submitTemplate">
          <template #icon><Save :size="16" /></template>
        </SpdButton>
          </div>
        </div>
      </section>
    </div>
      <QuotaProductSelectorDialog
        v-model:open="productSelectorOpen"
        :loading="productLoading"
        :product-query="productQuery"
        :products="quotaProducts" :page="productPage" :size="productSize" :total="productTotal"
        @change-page="searchQuotaProducts($event)" @change-size="productSize = $event; searchQuotaProducts()"
        @search="searchQuotaProducts()"
        @select="selectProduct"
      />
    <QuotaTemplateSelectorDialog
      v-model:open="templateSelectorOpen"
      v-model:show-all="showAllTemplates"
      :template-query="templateSelectorQuery"
      :templates="filteredPackingTemplates"
      :loading="templateSelectorLoading"
      :error="templateSelectorError"
      @retry="loadTemplateChoices"
      @select="selectPackingTemplate"
    />
    </Teleport>

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
  --pt-primary: var(--primary, var(--fli-brand, var(--spd-p-primary)));
  --pt-primary-dark: var(--primary-strong, var(--fli-brand-strong, var(--spd-p-primary-strong)));
  --pt-primary-soft: color-mix(in srgb, var(--pt-primary) 8%, var(--spd-p-bg));
  --pt-primary-glow: color-mix(in srgb, var(--pt-primary) 15%, transparent);
  --pt-bg-page: var(--spd-p-bg-subtle);
  --pt-bg-card: var(--spd-p-bg);
  --pt-bg-muted: var(--spd-p-bg-page);
  --pt-border: color-mix(in srgb, var(--pt-primary) 14%, var(--spd-p-primary-bg));
  --pt-border-subtle: color-mix(in srgb, var(--pt-primary) 8%, var(--spd-p-bg-muted));
  --pt-text: var(--spd-p-text);
  --pt-text-secondary: var(--spd-p-text-muted);
  --pt-text-tertiary: var(--spd-p-text-muted);
  --pt-shadow: 0 1px 3px var(--pt-primary-glow), 0 1px 2px color-mix(in srgb, var(--spd-p-text-strong) 4%, transparent);
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

.pt-create-form .pt-form-grid {
  grid-template-columns: repeat(auto-fit, minmax(min(240px, 100%), 1fr));
}

.pt-create-form > .pt-form-actions {
  justify-content: flex-end;
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
  margin: 0;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--pt-text);
}

.pt-card-header-desc {
  color: var(--pt-text-tertiary);
  font-size: var(--spd-font-size-sm);
  font-weight: 400;
  line-height: var(--spd-lh-base);
}

.pt-card-header-desc-inline {
  color: var(--pt-text-tertiary);
  font-size: var(--spd-font-size-sm);
  font-weight: 400;
  line-height: var(--spd-lh-base);
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
  font-size: var(--spd-font-size-sm);
  color: var(--pt-text-secondary);
  font-weight: 600;
}

.pt-form-required {
  color: var(--spd-p-danger);
  font-style: normal;
  font-weight: 700;
}

.pt-template-controls {
  grid-column: 1 / -1;
}

.pt-template-controls-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pt-input.pt-template-readonly {
  background: var(--pt-bg-muted);
}

/* ===== 表单内联反馈 ===== */
.pt-feedback {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 18px;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  line-height: var(--spd-lh-base);
  border-bottom: 1px solid var(--pt-border-subtle);
}

.pt-feedback > svg {
  flex: 0 0 14px;
}

.pt-feedback-success {
  background: color-mix(in srgb, var(--spd-p-success) 8%, var(--spd-p-bg));
  color: var(--spd-p-success-text);
}

.pt-feedback-error {
  background: color-mix(in srgb, var(--spd-p-danger) 8%, var(--spd-p-bg));
  color: var(--spd-p-danger-strong);
}

.pt-input,
.pt-select {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--pt-border);
  border-radius: var(--pt-radius-sm);
  background: var(--pt-bg-card);
  color: var(--pt-text);
  font-size: var(--spd-font-size-base);
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

.pt-warehouse-select {
  width: 100%;
  --el-border-color: var(--pt-border);
  --el-border-radius-base: var(--pt-radius-sm);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
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
  color: var(--spd-p-bg);
  box-shadow: 0 1px 0 var(--pt-primary-glow) inset;
}

.pt-btn-primary:hover:not(:disabled) {
  background: var(--pt-primary-dark);
  border-color: var(--pt-primary-dark);
  color: var(--spd-p-bg);
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

/* ===== Chip ===== */
.pt-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--pt-primary-soft);
  color: var(--pt-primary);
  border: 1px solid var(--pt-primary-glow);
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-base);
  color: var(--pt-text);
}

.pt-table thead th {
  background: var(--pt-bg-muted);
  color: var(--pt-text);
  font-weight: 700;
  font-size: var(--spd-font-size-table-head);
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
  font-size: var(--spd-font-size-base);
  line-height: var(--spd-lh-tight);
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
  font-size: var(--spd-font-size-sm);
  height: 32px;
  padding: 6px 12px;
}

/* ===== Badge ===== */
.pt-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: var(--spd-font-size-xs);
  font-weight: 600;
  line-height: var(--spd-lh-base);
  white-space: nowrap;
  border: 1px solid transparent;
}

.pt-badge-green {
  background: var(--spd-p-primary-bg);
  color: var(--spd-p-success);
  border-color: var(--spd-p-success-border);
}

.pt-badge-blue {
  background: var(--spd-p-border);
  color: var(--spd-p-info);
  border-color: var(--spd-p-info-border);
}

.pt-badge-orange {
  background: var(--spd-p-warning-bg);
  color: var(--spd-p-warning);
  border-color: var(--spd-p-warning-border);
}

.pt-badge-gray {
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-secondary);
  border-color: var(--spd-p-border);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
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
  color: var(--spd-p-danger);
}

.pt-btn-text-danger:hover {
  background: var(--spd-p-danger-bg);
}

.pt-row-meta {
  color: var(--pt-text-tertiary);
  font-size: var(--spd-font-size-xs);
  font-family: var(--spd-font-family-mono);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--pt-text-secondary);
}

.pt-sub-group-title > svg {
  color: var(--pt-primary);
  flex: 0 0 13px;
}

/* ---------- 数字列：居中 + 等宽数字 ----------
   与统一列表标准一致：金额 / 数量类单元格与普通列一样**居中**，
   数字使用等宽字体便于纵向比对。
   具体样式统一由 styles/list/unified-list.css 的 .ulc-number 提供，
   这里不再重复定义，避免各页面出现不同的对齐口径。 */

/* ===== Pagination separator (MdPagination) ===== */
.pt-card :deep(.md-pagination) {
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
  --lbl-primary: var(--pt-primary, var(--spd-p-primary));
  --lbl-danger: var(--spd-p-danger);
  --lbl-warn: var(--spd-p-warning-text);
  --lbl-ok: var(--spd-p-success-text);
  --lbl-border: color-mix(in srgb, var(--lbl-primary) 13%, var(--spd-p-primary-bg));
  --lbl-border-soft: color-mix(in srgb, var(--lbl-primary) 7%, var(--spd-p-bg-muted));
  --lbl-head: var(--spd-p-bg-subtle);
  --lbl-text: var(--spd-p-text-strong);
  --lbl-text-2: var(--spd-p-text-muted);
  --lbl-text-3: var(--spd-p-text-muted);

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--lbl-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
  padding: 12px 14px 14px;
}

/* ---------- 顶部成功提示 ---------- */
.lbl-banner {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
}

.lbl-banner-ok {
  border: 1px solid color-mix(in srgb, var(--lbl-ok) 26%, var(--spd-p-bg));
  background: color-mix(in srgb, var(--lbl-ok) 8%, var(--spd-p-bg));
  color: var(--spd-p-success-text);
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
  background: color-mix(in srgb, var(--lbl-primary) 10%, var(--spd-p-bg));
  color: var(--lbl-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--lbl-primary) 14%, transparent);
}

.lbl-toolbar-text {
  min-width: 0;
}

.lbl-toolbar-text h3 {
  margin: 0;
  color: var(--lbl-text);
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.lbl-toolbar-text p {
  margin: 2px 0 0;
  color: var(--lbl-text-3);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
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
  background: var(--spd-p-bg);
}

.lbl-stat-label {
  color: var(--lbl-text-3);
  font-size: var(--spd-font-size-xs);
  font-weight: 500;
  line-height: 1;
}

.lbl-stat-value {
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  line-height: var(--spd-lh-tight);
}

.lbl-field input {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--lbl-border);
  border-radius: 5px;
  background: var(--spd-p-bg);
  color: var(--lbl-text);
  font-size: var(--spd-font-size-base);
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.lbl-field input::placeholder {
  color: var(--spd-p-text-secondary);
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
  background: var(--spd-p-bg);
  color: var(--lbl-text-2);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
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
  color: var(--spd-p-bg);
}

.lbl-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, var(--spd-p-primary-strong));
  background: var(--pt-primary-dark, var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
}

/* ---------- 表格 ---------- */
.lbl-scroll {
  overflow: auto;
  max-height: min(58vh, 620px);
  border: 1px solid var(--lbl-border-soft);
  border-radius: 6px;
  background: var(--spd-p-bg);
}

.lbl-table {
  width: 100%;
  min-width: 1180px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

.lbl-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, var(--spd-p-text-strong), var(--spd-p-primary-bg));
}

.lbl-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--lbl-border);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-table-head);
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.lbl-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--spd-p-bg-muted);
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
  background: var(--spd-p-bg-page);
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
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  letter-spacing: -0.1px;
}

.lbl-no {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--lbl-text);
}

.lbl-main {
  color: var(--lbl-text);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.lbl-sub {
  color: var(--lbl-text-3);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-tight);
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
  font-size: var(--spd-font-size-base);
  line-height: var(--spd-lh-tight);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.lbl-print.is-zero {
  border-color: var(--spd-p-bg-muted);
  background: var(--spd-p-row-stripe);
  /* 零值弱化文字：可读的中性灰（此前误用浅色底令牌作文字，对比度仅 1.07:1） */
  color: var(--spd-p-text-muted);
}

/* ---------- 状态徽标 ---------- */
.lbl-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px 0 7px;
  border-radius: 11px;
  font-size: var(--spd-font-size-base);
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
  background: color-mix(in srgb, var(--lbl-warn) 12%, var(--spd-p-bg));
  color: var(--lbl-warn);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--lbl-warn) 22%, transparent);
}

.lbl-status.is-available {
  background: color-mix(in srgb, var(--lbl-ok) 12%, var(--spd-p-bg));
  color: var(--lbl-ok);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--lbl-ok) 20%, transparent);
}

.lbl-status.is-void {
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-muted);
  box-shadow: var(--spd-p-shadow-1);
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
  background: color-mix(in srgb, var(--lbl-primary) 7%, var(--spd-p-bg));
  color: var(--lbl-primary);
  font-size: var(--spd-font-size-base);
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
  background: color-mix(in srgb, var(--lbl-primary) 14%, var(--spd-p-bg));
}

.lbl-act-danger {
  border-color: color-mix(in srgb, var(--lbl-danger) 24%, transparent);
  background: color-mix(in srgb, var(--lbl-danger) 7%, var(--spd-p-bg));
  color: var(--lbl-danger);
}

.lbl-act-danger:hover {
  border-color: var(--lbl-danger);
  background: color-mix(in srgb, var(--lbl-danger) 14%, var(--spd-p-bg));
}

.lbl-act-none {
  color: var(--lbl-text-3);
  font-size: var(--spd-font-size-base);
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
  --evt-primary: var(--pt-primary, var(--spd-p-primary));
  --evt-danger: var(--spd-p-danger);
  --evt-warn: var(--spd-p-warning-text);
  --evt-ok: var(--spd-p-success-text);
  --evt-info: var(--spd-p-info-text);
  --evt-border: color-mix(in srgb, var(--evt-primary) 13%, var(--spd-p-primary-bg));
  --evt-border-soft: color-mix(in srgb, var(--evt-primary) 7%, var(--spd-p-bg-muted));
  --evt-head: var(--spd-p-bg-subtle);
  --evt-text: var(--spd-p-text-strong);
  --evt-text-2: var(--spd-p-text-muted);
  --evt-text-3: var(--spd-p-text-muted);

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--evt-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
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
  background: color-mix(in srgb, var(--evt-primary) 10%, var(--spd-p-bg));
  color: var(--evt-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-primary) 14%, transparent);
}

.evt-toolbar-text h3 {
  margin: 0;
  color: var(--evt-text);
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.evt-toolbar-text p {
  margin: 2px 0 0;
  color: var(--evt-text-3);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
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
  background: var(--spd-p-bg);
}

.evt-stat-label {
  color: var(--evt-text-3);
  font-size: var(--spd-font-size-xs);
  font-weight: 500;
  line-height: 1;
}

.evt-stat-value {
  color: var(--evt-text);
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  line-height: var(--spd-lh-tight);
}

.evt-field input,
.evt-field select {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--evt-border);
  border-radius: 5px;
  background: var(--spd-p-bg);
  color: var(--evt-text);
  font-size: var(--spd-font-size-base);
  font-family: inherit;
  outline: none;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.evt-field input {
  cursor: text;
}

.evt-field input::placeholder {
  color: var(--spd-p-text-secondary);
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
  background: var(--spd-p-bg);
  color: var(--evt-text-2);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
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
  color: var(--spd-p-bg);
}

.evt-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, var(--spd-p-primary-strong));
  background: var(--pt-primary-dark, var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
}

/* ---------- 表格 ---------- */
.evt-scroll {
  overflow: auto;
  max-height: min(58vh, 620px);
  border: 1px solid var(--evt-border-soft);
  border-radius: 6px;
  background: var(--spd-p-bg);
}

.evt-table {
  width: 100%;
  min-width: 1160px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

.evt-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, var(--spd-p-text-strong), var(--spd-p-primary-bg));
}

.evt-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--evt-border);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-table-head);
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.evt-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--spd-p-bg-muted);
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
  background: var(--spd-p-bg-page);
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
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  color: var(--evt-text-2);
}

.evt-no {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--evt-text);
}

.evt-label-no {
  display: inline-block;
  padding: 2px 7px;
  border: 1px solid var(--evt-border-soft);
  border-radius: 4px;
  background: var(--evt-head);
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-xs);
  color: var(--evt-text-2);
}

.evt-sub {
  color: var(--evt-text-3);
  font-size: var(--spd-font-size-sm);
}

/* ---------- 事件类型徽标 ---------- */
.evt-type {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px 0 7px;
  border-radius: 11px;
  font-size: var(--spd-font-size-base);
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
  background: color-mix(in srgb, var(--evt-primary) 11%, var(--spd-p-bg));
  color: var(--evt-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-primary) 20%, transparent);
}

.evt-type.is-print {
  background: color-mix(in srgb, var(--spd-p-info) 11%, var(--spd-p-bg));
  color: var(--spd-p-info);
  box-shadow: var(--spd-p-shadow-1);
}

.evt-type.is-info {
  background: color-mix(in srgb, var(--evt-info) 11%, var(--spd-p-bg));
  color: var(--evt-info);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-info) 20%, transparent);
}

.evt-type.is-ok {
  background: color-mix(in srgb, var(--evt-ok) 12%, var(--spd-p-bg));
  color: var(--evt-ok);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-ok) 20%, transparent);
}

.evt-type.is-warn {
  background: color-mix(in srgb, var(--evt-warn) 12%, var(--spd-p-bg));
  color: var(--evt-warn);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-warn) 22%, transparent);
}

.evt-type.is-danger {
  background: color-mix(in srgb, var(--evt-danger) 11%, var(--spd-p-bg));
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
  background: var(--spd-p-bg-muted);
  color: var(--evt-text-2);
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  white-space: nowrap;
}

.evt-flow-node.is-after {
  background: color-mix(in srgb, var(--evt-primary) 10%, var(--spd-p-bg));
  color: var(--evt-primary);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-primary) 18%, transparent);
}

.evt-flow-node.is-after.is-void {
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-muted);
  box-shadow: var(--spd-p-shadow-1);
}

.evt-flow-node.is-empty {
  background: transparent;
  color: var(--evt-text-3);
  font-style: italic;
  box-shadow: var(--spd-p-shadow-1);
}

.evt-flow-arrow {
  flex: 0 0 auto;
  color: var(--spd-p-text-secondary);
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
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.evt-qty.is-plus {
  background: color-mix(in srgb, var(--evt-ok) 12%, var(--spd-p-bg));
  color: var(--evt-ok);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-ok) 20%, transparent);
}

.evt-qty.is-minus {
  background: color-mix(in srgb, var(--evt-warn) 12%, var(--spd-p-bg));
  color: var(--evt-warn);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--evt-warn) 22%, transparent);
}

/* ---------- 备注 ---------- */
.evt-remark {
  max-width: 1px;
  overflow: hidden;
  color: var(--evt-text-2);
  font-size: var(--spd-font-size-base);
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
  --snp-primary: var(--pt-primary, var(--spd-p-primary));
  --snp-danger: var(--spd-p-danger);
  --snp-warn: var(--spd-p-warning-text);
  --snp-border: color-mix(in srgb, var(--snp-primary) 13%, var(--spd-p-primary-bg));
  --snp-border-soft: color-mix(in srgb, var(--snp-primary) 7%, var(--spd-p-bg-muted));
  --snp-head: var(--spd-p-bg-subtle);
  --snp-text: var(--spd-p-text-strong);
  --snp-text-2: var(--spd-p-text-muted);
  --snp-text-3: var(--spd-p-text-muted);

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--snp-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
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
  background: color-mix(in srgb, var(--snp-primary) 10%, var(--spd-p-bg));
  color: var(--snp-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--snp-primary) 14%, transparent);
}

.snp-toolbar-text h3 {
  margin: 0;
  color: var(--snp-text);
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.snp-toolbar-text p {
  margin: 2px 0 0;
  color: var(--snp-text-3);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
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
  background: var(--spd-p-bg);
}

.snp-stat-label {
  color: var(--snp-text-3);
  font-size: var(--spd-font-size-xs);
  font-weight: 500;
  line-height: 1;
}

.snp-stat-value {
  color: var(--snp-text);
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  line-height: var(--spd-lh-tight);
}

.snp-field input {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--snp-border);
  border-radius: 5px;
  background: var(--spd-p-bg);
  color: var(--snp-text);
  font-size: var(--spd-font-size-base);
  font-family: inherit;
  outline: none;
  cursor: text;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.snp-field input::placeholder {
  color: var(--spd-p-text-secondary);
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
  background: var(--spd-p-bg);
  color: var(--snp-text-2);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
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
  color: var(--spd-p-bg);
}

.snp-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, var(--spd-p-primary-strong));
  background: var(--pt-primary-dark, var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
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
  background: color-mix(in srgb, var(--snp-primary) 12%, var(--spd-p-bg));
  color: var(--snp-primary);
  font-size: var(--spd-font-size-xs);
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
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-3);
}

.snp-columns-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 6px;
  border-radius: 4px;
  color: var(--snp-text-2);
  font-size: var(--spd-font-size-base);
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
  background: var(--spd-p-bg);
}

.snp-table {
  width: 100%;
  min-width: 980px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

.snp-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, var(--spd-p-text-strong), var(--spd-p-primary-bg));
}

.snp-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--snp-border);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-table-head);
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.snp-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--spd-p-bg-muted);
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
  background: var(--spd-p-bg-page);
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
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  color: var(--snp-text-2);
}

.snp-main {
  color: var(--snp-text);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.snp-sub {
  color: var(--snp-text-3);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-tight);
}

.snp-batch {
  display: inline-block;
  padding: 2px 7px;
  border: 1px solid var(--snp-border-soft);
  border-radius: 4px;
  background: var(--snp-head);
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-xs);
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
  font-size: var(--spd-font-size-base);
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
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  white-space: nowrap;
}

.snp-expire.is-normal {
  background: var(--spd-p-bg-muted);
  color: var(--snp-text-2);
}

.snp-expire.is-warning {
  background: color-mix(in srgb, var(--snp-warn) 10%, var(--spd-p-bg));
  color: var(--snp-warn);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--snp-warn) 20%, transparent);
}

.snp-expire.is-critical {
  background: color-mix(in srgb, var(--spd-p-warning-strong) 12%, var(--spd-p-bg));
  color: var(--spd-p-warning-strong);
  font-weight: 600;
  box-shadow: var(--spd-p-shadow-1);
}

.snp-expire.is-expired {
  background: color-mix(in srgb, var(--snp-danger) 12%, var(--spd-p-bg));
  color: var(--snp-danger);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--snp-danger) 26%, transparent);
}

.snp-expire.is-unknown {
  background: transparent;
  color: var(--snp-text-3);
  box-shadow: var(--spd-p-shadow-1);
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
  --tpl-primary: var(--pt-primary, var(--spd-p-primary));
  --tpl-danger: var(--spd-p-danger);
  --tpl-border: color-mix(in srgb, var(--tpl-primary) 13%, var(--spd-p-primary-bg));
  --tpl-border-soft: color-mix(in srgb, var(--tpl-primary) 7%, var(--spd-p-bg-muted));
  --tpl-head: var(--spd-p-bg-subtle);
  --tpl-text: var(--spd-p-text-strong);
  --tpl-text-2: var(--spd-p-text-muted);
  --tpl-text-3: var(--spd-p-text-muted);

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--tpl-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
  padding: 12px 14px 14px;
}

/* ---------- 顶部提示 ---------- */
.tpl-banner {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--tpl-primary) 26%, var(--spd-p-bg));
  border-radius: 6px;
  background: color-mix(in srgb, var(--tpl-primary) 8%, var(--spd-p-bg));
  color: var(--spd-p-success-text);
  font-size: var(--spd-font-size-sm);
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  line-height: var(--spd-lh-tight);
}

.tpl-field input {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--tpl-border);
  border-radius: 5px;
  background: var(--spd-p-bg);
  color: var(--tpl-text);
  font-size: var(--spd-font-size-base);
  font-family: inherit;
  outline: none;
  cursor: text;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.tpl-field input::placeholder {
  color: var(--spd-p-text-secondary);
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
  background: var(--spd-p-bg);
  color: var(--tpl-text-2);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
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
  color: var(--spd-p-bg);
}

.tpl-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, var(--spd-p-primary-strong));
  background: var(--pt-primary-dark, var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
}

.tpl-btn-danger {
  border-color: color-mix(in srgb, var(--tpl-danger) 28%, var(--spd-p-bg));
  background: color-mix(in srgb, var(--tpl-danger) 7%, var(--spd-p-bg));
  color: var(--tpl-danger);
}

.tpl-btn-danger:hover:not(:disabled) {
  border-color: var(--tpl-danger);
  background: color-mix(in srgb, var(--tpl-danger) 14%, var(--spd-p-bg));
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  white-space: nowrap;
}

.tpl-chip.is-primary {
  border-color: color-mix(in srgb, var(--tpl-primary) 26%, var(--spd-p-bg));
  background: color-mix(in srgb, var(--tpl-primary) 10%, var(--spd-p-bg));
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
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-3);
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
  font-size: var(--spd-font-size-base);
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
  background: var(--spd-p-bg);
}

.tpl-table {
  width: 100%;
  min-width: 1280px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

.tpl-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, var(--spd-p-text-strong), var(--spd-p-primary-bg));
}

.tpl-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--tpl-border);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-table-head);
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.tpl-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--spd-p-bg-muted);
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
  background: var(--spd-p-bg-page);
}

/* sticky 列必须显式给背景（tr 本身无背景），否则横向滚动时下方内容会透出 */
.tpl-row:hover td.tpl-col-select,
.tpl-row:hover td.tpl-col-actions {
  background: var(--spd-p-bg-page);
}

.tpl-row.is-selected td {
  background: color-mix(in srgb, var(--tpl-primary) 7%, var(--spd-p-bg));
}

.tpl-row.is-selected td.tpl-col-select,
.tpl-row.is-selected td.tpl-col-actions {
  background: color-mix(in srgb, var(--tpl-primary) 7%, var(--spd-p-bg));
}

.tpl-row.is-selected:hover td {
  background: color-mix(in srgb, var(--tpl-primary) 11%, var(--spd-p-bg));
}

.tpl-row.is-selected:hover td.tpl-col-select,
.tpl-row.is-selected:hover td.tpl-col-actions {
  background: color-mix(in srgb, var(--tpl-primary) 11%, var(--spd-p-bg));
}

/* 勾选列：固定在左侧，横向滚动时始终可见 */
.tpl-col-select {
  position: sticky;
  left: 0;
  z-index: 1;
  min-width: 44px;
  text-align: center;
  background: var(--spd-p-bg);
}

.tpl-table thead .tpl-col-select {
  z-index: 4;
  background: var(--spd-p-primary-bg);
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
  background: var(--spd-p-bg);
  box-shadow: -1px 0 0 var(--tpl-border-soft);
}

.tpl-table thead .tpl-col-actions {
  z-index: 4;
  background: var(--spd-p-primary-bg);
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
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--tpl-text);
}

.tpl-main {
  color: var(--tpl-text);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.tpl-qty {
  font-variant-numeric: tabular-nums;
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  color: var(--tpl-primary);
}

.tpl-qty-unit {
  margin-left: 3px;
  color: var(--tpl-text-3);
  font-size: var(--spd-font-size-xs);
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
  font-size: var(--spd-font-size-xs);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.tpl-version-current {
  display: inline-flex;
  align-items: center;
  margin-left: 4px;
  color: var(--tpl-primary);
  font-size: var(--spd-font-size-xs);
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
  font-size: var(--spd-font-size-base);
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
  background: color-mix(in srgb, var(--spd-p-success-text) 12%, var(--spd-p-bg));
  color: var(--spd-p-success-text);
  box-shadow: var(--spd-p-shadow-1);
}

.tpl-status.is-disabled {
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-muted);
  box-shadow: var(--spd-p-shadow-1);
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
  background: var(--spd-p-bg);
  color: var(--tpl-text-2);
  font-size: var(--spd-font-size-base);
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
  background: color-mix(in srgb, var(--tpl-primary) 8%, var(--spd-p-bg));
  color: var(--tpl-primary);
}

.tpl-act-primary {
  border-color: color-mix(in srgb, var(--tpl-primary) 26%, var(--spd-p-bg));
  background: color-mix(in srgb, var(--tpl-primary) 9%, var(--spd-p-bg));
  color: var(--tpl-primary);
}

.tpl-act-danger {
  border-color: color-mix(in srgb, var(--tpl-danger) 24%, transparent);
  background: color-mix(in srgb, var(--tpl-danger) 7%, var(--spd-p-bg));
  color: var(--tpl-danger);
}

.tpl-act-danger:hover {
  border-color: var(--tpl-danger);
  background: color-mix(in srgb, var(--tpl-danger) 14%, var(--spd-p-bg));
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
  --saf-primary: var(--pt-primary, var(--spd-p-primary));
  --saf-border: color-mix(in srgb, var(--saf-primary) 13%, var(--spd-p-primary-bg));
  --saf-border-soft: color-mix(in srgb, var(--saf-primary) 7%, var(--spd-p-bg-muted));
  --saf-head: var(--spd-p-bg-subtle);
  --saf-text: var(--spd-p-text-strong);
  --saf-text-2: var(--spd-p-text-muted);
  --saf-text-3: var(--spd-p-text-muted);

  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--saf-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
  padding: 12px 14px 14px;
}

/* ---------- 顶部提示 ---------- */
.saf-banner {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--saf-primary) 26%, var(--spd-p-bg));
  border-radius: 6px;
  background: color-mix(in srgb, var(--saf-primary) 8%, var(--spd-p-bg));
  color: var(--spd-p-success-text);
  font-size: var(--spd-font-size-sm);
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
  background: color-mix(in srgb, var(--saf-primary) 10%, var(--spd-p-bg));
  color: var(--saf-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--saf-primary) 14%, transparent);
}

.saf-toolbar-text h3 {
  margin: 0;
  color: var(--saf-text);
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.saf-toolbar-text p {
  margin: 2px 0 0;
  color: var(--saf-text-3);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
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
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  line-height: var(--spd-lh-tight);
}

.saf-field input {
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--saf-border);
  border-radius: 5px;
  background: var(--spd-p-bg);
  color: var(--saf-text);
  font-size: var(--spd-font-size-base);
  font-family: inherit;
  outline: none;
  cursor: text;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.saf-field input::placeholder {
  color: var(--spd-p-text-secondary);
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
  background: var(--spd-p-bg);
  color: var(--saf-text-2);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
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
  color: var(--spd-p-bg);
}

.saf-btn-primary:hover:not(:disabled) {
  border-color: var(--pt-primary-dark, var(--spd-p-primary-strong));
  background: var(--pt-primary-dark, var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
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
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-3);
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
  font-size: var(--spd-font-size-base);
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
  background: var(--spd-p-bg);
}

.saf-table {
  width: 100%;
  min-width: 1220px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

.saf-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(180deg, var(--spd-p-text-strong), var(--spd-p-primary-bg));
}

.saf-table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--saf-border);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-table-head);
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.saf-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--spd-p-bg-muted);
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
  background: var(--spd-p-bg-page);
}

/* sticky 列必须显式给背景（tr 本身无背景），否则横向滚动时下方内容会透出 */
.saf-row:hover td.saf-col-actions {
  background: var(--spd-p-bg-page);
}

/* 操作列：固定在右侧 */
.saf-col-actions {
  position: sticky;
  right: 0;
  z-index: 1;
  min-width: 92px;
  background: var(--spd-p-bg);
  box-shadow: -1px 0 0 var(--saf-border-soft);
}

.saf-table thead .saf-col-actions {
  z-index: 4;
  background: var(--spd-p-primary-bg);
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
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  color: var(--saf-text-2);
}

.saf-dept {
  color: var(--saf-text);
  font-weight: 600;
}

.saf-code {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  color: var(--saf-text);
}

.saf-main {
  color: var(--saf-text);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.saf-sub {
  color: var(--saf-text-3);
  font-size: var(--spd-font-size-sm);
}

.saf-qty {
  font-variant-numeric: tabular-nums;
  font-size: var(--spd-font-size-base);
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
  background: var(--spd-p-bg-muted);
  overflow: hidden;
}

.saf-range-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--saf-primary), color-mix(in srgb, var(--saf-primary) 55%, var(--spd-p-primary)));
}

.saf-range-text {
  flex: 0 0 auto;
  color: var(--saf-text-2);
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-base);
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
  background: color-mix(in srgb, var(--spd-p-success-text) 12%, var(--spd-p-bg));
  color: var(--spd-p-success-text);
  box-shadow: var(--spd-p-shadow-1);
}

.saf-status.is-disabled {
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-muted);
  box-shadow: var(--spd-p-shadow-1);
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
  background: var(--spd-p-bg);
  color: var(--saf-text-2);
  font-size: var(--spd-font-size-base);
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
  background: color-mix(in srgb, var(--saf-primary) 8%, var(--spd-p-bg));
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
  background: var(--spd-p-bg);
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: var(--spd-p-shadow-dialog);
  overflow: hidden;
}

.qtd-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
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
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.qtd-dialog-title h3 {
  margin: 0;
  font-size: var(--spd-font-size-xl);
  font-weight: 600;
  color: var(--spd-p-bg);
}

.qtd-dialog-tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--spd-p-success) 30%, transparent);
  color: var(--spd-p-primary-bg);
  font-size: var(--spd-font-size-base);
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
  background: var(--spd-p-bg-subtle);
  border-bottom: 1px solid var(--spd-p-border);
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
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-muted);
  font-weight: 600;
}

.qtd-field-wide {
  grid-column: 1 / -1;
}

.qtd-field input,
.qtd-field select {
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  font-size: var(--spd-font-size-base);
  transition: border-color 0.18s, box-shadow 0.18s;
}

.qtd-field input:focus {
  outline: none;
  border-color: var(--spd-p-primary);
  box-shadow: var(--spd-p-shadow-1);
}

.qtd-field input[readonly] {
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-secondary);
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
  background: color-mix(in srgb, var(--spd-p-danger) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--spd-p-danger) 20%, transparent);
  border-radius: 6px;
  color: var(--spd-p-danger);
  font-size: var(--spd-font-size-sm);
}

.qtd-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 24px 18px;
  background: var(--spd-p-bg-subtle);
  border-top: 1px solid var(--spd-p-border);
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
  font-size: var(--spd-font-size-base);
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
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-strong));
  color: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-2);
}
.qtd-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-strong));
  box-shadow: var(--spd-p-shadow-2);
}

.qtd-btn-secondary {
  background: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-primary) 10%, transparent), color-mix(in srgb, var(--spd-p-success-text) 10%, transparent));
  color: var(--spd-p-primary-hover); border-color: color-mix(in srgb, var(--spd-p-primary) 20%, transparent);
}
.qtd-btn-secondary:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-primary-hover));
  color: var(--spd-p-bg); box-shadow: var(--spd-p-shadow-2);
}

.qtd-btn-info {
  background: linear-gradient(135deg, color-mix(in srgb, var(--spd-p-success-text) 10%, transparent), color-mix(in srgb, var(--spd-p-info) 10%, transparent));
  color: var(--spd-p-primary-hover); border-color: color-mix(in srgb, var(--spd-p-success-text) 20%, transparent);
}
.qtd-btn-info:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--spd-p-primary), var(--spd-p-info));
  color: var(--spd-p-bg); box-shadow: var(--spd-p-shadow-2);
}

.qtd-btn-ghost {
  width: 32px; min-height: 32px; padding: 0; justify-content: center;
  background: color-mix(in srgb, var(--spd-p-bg) 18%, transparent);
  color: var(--spd-p-bg); border-color: color-mix(in srgb, var(--spd-p-bg) 30%, transparent);
}
.qtd-btn-ghost:hover { background: color-mix(in srgb, var(--spd-p-bg) 30%, transparent); }
</style>
