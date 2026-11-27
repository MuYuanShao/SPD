<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { AlertTriangle, ArrowLeft, Eye, History, Info, Minus, Plus, RotateCcw, Search, ShoppingCart, UserPlus, X } from '@lucide/vue'
import { ElDialog } from 'element-plus'
import 'element-plus/theme-chalk/el-dialog.css'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import {
  analyzeDepartmentRequisition,
  createRequisition,
  fetchServerPagedClosureList,
  fetchDepartmentRequisitionOptions,
  fetchDepartmentRequisitionWarehouses,
  fetchRequisitionItems,
  generateRequisitionsFromAnalysis,
  processRequisition,
  type DepartmentSmartSuggestion,
  type RequisitionMode,
  type ClosureOptions
} from '../../api/operationalClosure'
import type { RequisitionWarehouseOption } from '../../api/operationalClosure'
import {
  fetchDepartmentRequisitionCatalog,
  type DepartmentRequisitionCatalogRow
} from '../../api/quotaPackages'
import { formatStatusText } from '../../utils/chineseDisplay'
import { useAuthStore } from '../../stores/auth'
import { usePagedFilters } from '../../composables/usePagedFilters'
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
import { useNotify, notifyError, resolveErrorMessage } from '../../composables/useNotify'
import { useFillHeightTable } from '../../composables/useFillHeightTable'

const notify = useNotify()


interface RequisitionCatalogItem extends DepartmentRequisitionCatalogRow {
  selected: boolean
  quantity: number
  mode: RequisitionMode
}

const filters = reactive({
  productCode: '',
  productName: '',
  specModel: '',
  manufacturerName: '',
  targetDeptCode: '',
  warehouseId: '' as number | '',
  sourceWarehouseId: '' as number | '',
  mode: ''
})

const loading = ref(false)
const optionsLoading = ref(false)
const submitting = ref(false)
const error = ref('')
const message = ref('')
const submittedCount = ref(0)
const products = ref<RequisitionCatalogItem[]>([])
const quantityDrafts = new Map<string, Pick<RequisitionCatalogItem, 'quantity' | 'mode' | 'selected'>>()
let draftScope = ''
let catalogRequestVersion = 0
let warehouseRequestVersion = 0

function draftKey(row: DepartmentRequisitionCatalogRow) {
  return JSON.stringify([row.productCode, row.sourceWarehouseId, row.templateCode])
}

function rememberQuantities() {
  products.value.forEach(({ quantity, mode, selected, ...row }) => {
    quantityDrafts.set(draftKey(row), { quantity, mode, selected })
  })
}

function clearQuantityDrafts() {
  catalogRequestVersion++
  quantityDrafts.clear()
  draftScope = ''
  products.value = []
}

const requisitionOrders = ref<Record<string, unknown>[]>([])
const approvingRequisitionNo = ref('')
const approvalMode = ref(false)
const approvalError = ref('')
const approvalQuantities = ref<Record<number, number | string>>({})
const detailRows = ref<Record<string, unknown>[]>([])
const detailOpen = ref(false)
let detailRequestVersion = 0
const detailLoading = ref(false)
const activeRequisitionNo = ref('')
const requisitionStarted = ref(false)
const requisitionType = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)
const historyPage = ref(1)
const historySize = ref(20)
const historyTotal = ref(0)
/** 历史申领单加载失败：统一用通用消息接口抛出，不渲染内联错误面板 */
const historyLoadError = ref('')
const {
  filters: historyFilters, filterError: historyFilterError, apply: applyHistoryFilters,
  reset: resetHistoryFilters, load: loadRequisitionOrders, beginRequest: beginHistoryRequest
} = usePagedFilters({
  defaults: { dateFrom: '', dateTo: '', status: '', deptName: '', requisitionNo: '', sort: 'timeDesc' },
  page: historyPage,
  size: historySize,
  loading,
  loadError: historyLoadError,
  errorMessage: '科室申领记录加载失败',
  isActive: () => !requisitionStarted.value,
  fetchPage: params => fetchServerPagedClosureList('requisition', params),
  onPage: (result) => {
    requisitionOrders.value = result.rows
    historyTotal.value = result.total
  }
})
const historyStatuses = ['draft', 'pending_approval', 'approved', 'rejected', 'partial_picked', 'picked', 'signed', 'completed', 'cancelled']

/* ---------------------------------------------------------------------
 * 历史申领单日期区间约束：开始日期不能晚于结束日期
 * ---------------------------------------------------------------------
 * 两层保障：
 *   1) disabled-date —— 日历面板里把非法日期置灰；
 *   2) 提交前的显式校验 —— 即便绕过置灰，也要拦下来并提示，绝不发请求。
 * 绑定值是 value-format="YYYY-MM-DD" 的纯字符串，可直接按字典序比较。
 * ------------------------------------------------------------------- */
function historyToDayStart(value: string) {
  if (!value) return null
  const parsed = new Date(`${value}T00:00:00`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/** 开始日期：禁用晚于结束日期的日期 */
function historyDisableAfterEnd(date: Date) {
  const end = historyToDayStart(historyFilters.dateTo)
  if (!end) return false
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  return target.getTime() > end.getTime()
}

/** 结束日期：禁用早于开始日期的日期 */
function historyDisableBeforeStart(date: Date) {
  const start = historyToDayStart(historyFilters.dateFrom)
  if (!start) return false
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  return target.getTime() < start.getTime()
}

/**
 * 提交历史申领单筛选。
 * 开始日期晚于结束日期时，用通用消息接口（ElMessage）提示并终止，
 * 不触发任何请求。
 */
function submitHistoryFilters() {
  if (historyFilters.dateFrom && historyFilters.dateTo && historyFilters.dateFrom > historyFilters.dateTo) {
    notifyError('开始日期不能晚于结束日期')
    return
  }
  applyHistoryFilters()
}
const options = ref<ClosureOptions>({ departments: [], warehouses: [], products: [], balances: [] })
const linkedWarehouses = ref<RequisitionWarehouseOption[]>([])
const smartOpen = ref(false)
const smartLoading = ref(false)
const smartGenerating = ref(false)
const smartPeriodDays = ref(7)
const smartAnalysisId = ref(0)
const smartRows = ref<Array<DepartmentSmartSuggestion & { selected: boolean }>>([])
const authStore = useAuthStore()

const filteredProducts = computed(() => products.value)
const selectedDepartment = computed(() => options.value.departments.find((item) => item.deptCode === filters.targetDeptCode))
const selectedWarehouse = computed(() => linkedWarehouses.value.find(warehouse => warehouse.warehouseId === filters.warehouseId))
const selectedWarehouseLabel = computed(() => selectedWarehouse.value?.name || '请选择收货库房')
const sourceWarehouses = computed(() => selectedWarehouse.value?.sourceWarehouses ?? [])
const warehouseSetupMessage = computed(() => {
  if (!filters.targetDeptCode) return '请先选择科室'
  if (!linkedWarehouses.value.length) return '当前科室暂无启用的收货库房，请先在科室管理中关联库房'
  if (!selectedWarehouse.value) return '请选择收货库房'
  if (!sourceWarehouses.value.length) return '收货库房所在院区暂无启用的一级库或中心库，请先维护供货库房'
  if (!filters.sourceWarehouseId) return '当前院区有多个供货库房，请选择本次从哪个库房发货'
  return ''
})
const selectedItems = computed(() => products.value.filter((item) => item.selected))
const canCreateRequisition = computed(() => authStore.hasPermission('department-requisition:create'))
const canApproveRequisition = computed(() => authStore.hasPermission('department-requisition:approve'))
const canSmartAnalyze = computed(() => authStore.hasPermission('department-requisition:smart-analysis'))

function money(value: unknown) {
  const amount = Number(value || 0)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}

async function approveRequisition(order: Record<string, unknown>) {
  const no = String(order.bizNo || '')
  if (!no || approvingRequisitionNo.value || !canApproveRequisition.value || order.status !== 'pending_approval') return
  await openDetails(no, true)
}

function approvalFactor(item: Record<string, unknown>) {
  return item.itemType === 'quota_package' ? Number(item.packageQuantity || 0) : 1
}

async function confirmApproval() {
  const no = activeRequisitionNo.value
  if (!approvalMode.value || !no || detailLoading.value || approvingRequisitionNo.value || !detailRows.value.length) return
  const edits = detailRows.value.map(item => {
    const itemId = Number(item.itemId)
    const originalQuantity = Number(item.applyQuantity)
    const quantity = Number(approvalQuantities.value[itemId])
    const changed = approvalFactor(item) > 0 && !Number(item.pickedQuantity)
      && quantity !== originalQuantity / approvalFactor(item)
    return { itemId, originalQuantity, quantity: changed ? quantity : undefined }
  })
  if (edits.some(edit => edit.quantity !== undefined && (!Number.isSafeInteger(edit.quantity) || edit.quantity <= 0))) {
    approvalError.value = '审批数量和包数必须为正整数'
    return
  }
  approvingRequisitionNo.value = no
  approvalError.value = ''
  try {
    const result = await processRequisition(no, 'approve', edits)
    notify.success(result.status === 'approved' ? '审批通过，已进入待拣配' : '本级审批已记录，等待后续审批')
    approvingRequisitionNo.value = ''
    closeDetails()
    await loadRequisitionOrders()
  } catch (err) {
    approvalError.value = err instanceof Error ? err.message : '科室申领审批失败'
  } finally {
    approvingRequisitionNo.value = ''
  }
}

async function loadOptions() {
  optionsLoading.value = true
  try {
    options.value = await fetchDepartmentRequisitionOptions()
    if (!filters.targetDeptCode && options.value.departments[0]) {
      filters.targetDeptCode = options.value.departments[0].deptCode
    }
    await loadLinkedWarehouses()
  } catch (err) {
    notifyError(resolveErrorMessage(err, '科室选项加载失败'))
  } finally {
    optionsLoading.value = false
  }
}

async function loadLinkedWarehouses() {
  const request = ++warehouseRequestVersion
  clearQuantityDrafts()
  loading.value = false
  totalItems.value = 0
  error.value = ''
  linkedWarehouses.value = []
  filters.warehouseId = ''
  filters.sourceWarehouseId = ''
  const deptCode = selectedDepartment.value?.deptCode
  if (!deptCode) {
    optionsLoading.value = false
    return
  }
  optionsLoading.value = true
  try {
    const warehouses = await fetchDepartmentRequisitionWarehouses(deptCode)
    if (request !== warehouseRequestVersion) return
    linkedWarehouses.value = warehouses.filter((item) => Number(item.selected ?? 0) === 1)
    filters.warehouseId = linkedWarehouses.value[0]?.warehouseId ?? ''
    selectDefaultSourceWarehouse()
  } catch (err) {
    if (request === warehouseRequestVersion) notifyError(resolveErrorMessage(err, '收货库房加载失败'))
  } finally {
    if (request === warehouseRequestVersion) optionsLoading.value = false
  }
}

function selectDefaultSourceWarehouse() {
  filters.sourceWarehouseId = sourceWarehouses.value.length === 1 ? sourceWarehouses.value[0].warehouseId : ''
}

async function loadCatalog() {
  if (!requisitionStarted.value || optionsLoading.value) return
  const requestVersion = ++catalogRequestVersion
  const scope = JSON.stringify([filters.targetDeptCode, filters.warehouseId, filters.sourceWarehouseId])
  if (scope !== draftScope) {
    quantityDrafts.clear()
    products.value = []
    totalItems.value = 0
    draftScope = scope
  }
  if (warehouseSetupMessage.value) {
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await fetchDepartmentRequisitionCatalog({
      deptCode: selectedDepartment.value?.deptCode || '',
      deptName: selectedDepartment.value?.deptName || '',
      warehouseName: selectedWarehouse.value?.name || '',
      destinationWarehouseId: String(filters.warehouseId),
      sourceWarehouseId: String(filters.sourceWarehouseId),
      productCode: filters.productCode,
      productName: filters.productName,
      specModel: filters.specModel,
      manufacturerName: filters.manufacturerName,
      mode: filters.mode,
      page: String(currentPage.value),
      size: String(pageSize.value)
    })
    if (requestVersion !== catalogRequestVersion || !requisitionStarted.value) return
    rememberQuantities()
    totalItems.value = result.total
    products.value = result.rows.map((row) => {
      const allowedModes = row.allowedModes?.length
        ? row.allowedModes
        : row.defaultMode === 'high_value'
          ? ['high_value' as RequisitionMode]
          : row.defaultMode === 'quota_package'
            ? ['loose' as RequisitionMode, 'quota_package' as RequisitionMode]
            : ['loose' as RequisitionMode]
      const draft = quantityDrafts.get(draftKey(row))
      const mode = draft && allowedModes.includes(draft.mode)
        ? draft.mode : allowedModes.includes(row.defaultMode) ? row.defaultMode : allowedModes[0]
      const item = { ...row, allowedModes, selected: draft?.selected ?? false, quantity: draft?.quantity ?? 1, mode }
      if (draft && draft.mode !== mode) normalizeQuantity(item)
      return item
    })
  } catch (err) {
    if (requestVersion !== catalogRequestVersion || !requisitionStarted.value) return
    notifyError(resolveErrorMessage(err, '科室申领目录加载失败'))
  } finally {
    if (requestVersion === catalogRequestVersion) loading.value = false
  }
}

async function startRequisition() {
  if (optionsLoading.value) return
  requisitionType.value = ''
  clearQuantityDrafts()
  notify.success('')
  error.value = ''
  await loadOptions()
  beginHistoryRequest()
  requisitionStarted.value = true
  currentPage.value = 1
  await loadCatalog()
}

function leaveRequisition() {
  warehouseRequestVersion++
  optionsLoading.value = false
  clearQuantityDrafts()
  requisitionStarted.value = false
  products.value = []
  totalItems.value = 0
  currentPage.value = 1
  error.value = ''
  notify.success('')
  submittedCount.value = 0
  void loadRequisitionOrders()
}

async function changePage(page: number) {
  const totalPages = Math.max(Math.ceil(totalItems.value / Math.max(pageSize.value, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === currentPage.value) return
  currentPage.value = nextPage
  await loadCatalog()
}

async function changePageSize(size: number) {
  if (size === pageSize.value) return
  pageSize.value = size
  currentPage.value = 1
  await loadCatalog()
}

async function changeHistoryPage(page: number) {
  const totalPages = Math.max(Math.ceil(historyTotal.value / Math.max(historySize.value, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === historyPage.value) return
  historyPage.value = nextPage
  await loadRequisitionOrders()
}

async function changeHistorySize(size: number) {
  if (size === historySize.value) return
  historySize.value = size
  historyPage.value = 1
  await loadRequisitionOrders()
}

async function openDetails(requisitionNo: unknown, approving = false) {
  const no = String(requisitionNo || '')
  if (!no) return
  const request = ++detailRequestVersion
  activeRequisitionNo.value = no
  approvalMode.value = approving
  approvalError.value = ''
  approvalQuantities.value = {}
  detailOpen.value = true
  detailLoading.value = true
  detailRows.value = []
  try {
    const result = await fetchRequisitionItems(no)
    if (request !== detailRequestVersion) return
    detailRows.value = result.rows
    if (approving) {
      approvalQuantities.value = Object.fromEntries(result.rows.map(item => [
        Number(item.itemId), Number(item.applyQuantity) / approvalFactor(item)
      ]))
    }
  } catch (err) {
    if (request === detailRequestVersion) {
      approvalError.value = err instanceof Error ? err.message : '科室申领明细加载失败'
    }
  } finally {
    if (request === detailRequestVersion) detailLoading.value = false
  }
}

function closeDetails() {
  if (approvingRequisitionNo.value) return
  detailRequestVersion++
  detailOpen.value = false
  activeRequisitionNo.value = ''
  detailRows.value = []
}

function searchCatalog() {
  currentPage.value = 1
  void loadCatalog()
}

/** 页头刷新：按当前视图刷新历史订单或申领目录 */
function refreshCurrentView() {
  if (!requisitionStarted.value) {
    void loadRequisitionOrders()
    return
  }
  void loadCatalog()
}

function availableByMode(item: RequisitionCatalogItem) {
  if (item.mode === 'high_value') return Number(item.uniqueCodeAvailableQty || 0)
  return item.mode === 'quota_package' ? Number(item.packageAvailableQty || 0) : Number(item.looseAvailableQty || 0)
}

function unitByMode(item: RequisitionCatalogItem) {
  if (item.mode === 'high_value') return '件（拣配时选码）'
  return item.mode === 'quota_package' ? '包' : item.baseUnit
}

function baseQtyByMode(item: RequisitionCatalogItem) {
  if (item.mode === 'quota_package') {
    return Number(item.packageQuantity || item.conversionRate || 1) * item.quantity
  }
  return item.quantity
}

function stepQuantity(item: RequisitionCatalogItem, delta: number) {
  const minimum = 1
  item.quantity = Math.max(minimum, Number(item.quantity || minimum) + delta)
  normalizeQuantity(item)
}

function normalizeQuantity(item: RequisitionCatalogItem) {
  const quantity = Number(item.quantity)
  item.quantity = Number.isFinite(quantity) && quantity > 0 ? quantity : 1
  if (!Number.isInteger(quantity)) error.value = "申领数量必须为整数，不支持小数"
  else if (error.value === "申领数量必须为整数，不支持小数") error.value = ''
}

async function submitSelectedRequisitions() {
  if (submitting.value || loading.value) return
  if (!filters.targetDeptCode) {
    notify.warning('请先选择科室')
    return
  }
  if (selectedItems.value.length === 0) {
    notify.warning('请先勾选需要申领的商品')
    return
  }
  if (selectedItems.value.some(item => !Number.isInteger(Number(item.quantity)) || Number(item.quantity) <= 0)) {
    notify.warning("申领数量必须为正整数，不支持小数")
    return
  }
  submitting.value = true
  notify.success('')
  error.value = ''
  try {
    const submittedItems = [...selectedItems.value]
    const items: Array<Record<string, unknown>> = []
    for (const item of submittedItems) {
      const payload: Record<string, unknown> = {
        productCode: item.productCode,
        quantity: baseQtyByMode(item),
        requisitionMode: item.mode
      }
      if (item.mode === 'quota_package') {
        payload.templateCode = item.templateCode
        payload.packageCount = item.quantity
      }
      items.push(payload)
    }
    const result = await createRequisition({
      deptCode: selectedDepartment.value?.deptCode,
      deptName: selectedDepartment.value?.deptName,
      warehouseName: selectedWarehouse.value?.name,
      destinationWarehouseId: filters.warehouseId,
      sourceWarehouseId: filters.sourceWarehouseId,
      requisitionType: requisitionType.value || undefined,
      items
    })
    submittedCount.value += submittedItems.length
    requisitionType.value = ''
    const requisitionNo = (result as { requisitionNo?: string }).requisitionNo
    notify.success(requisitionNo
      ? `已提交 ${submittedItems.length} 条科室申领明细，申请单号：${requisitionNo}`
      : `已提交 ${submittedItems.length} 条科室申领明细`)
    clearQuantityDrafts()
    await loadCatalog()
    await loadRequisitionOrders()
  } catch (err) {
    notifyError(resolveErrorMessage(err, '科室申领提交失败'))
  } finally {
    submitting.value = false
  }
}

function toggleAllProducts(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  products.value.forEach((item) => {
    item.selected = checked
  })
}

/** 从请购列表移除已选商品（取消勾选） */
function removeFromSelection(item: RequisitionCatalogItem) {
  item.selected = false
}

async function openSmartReplenishment() {
  if (warehouseSetupMessage.value) {
    notify.warning(warehouseSetupMessage.value)
    return
  }
  const deptCode = selectedDepartment.value?.deptCode
  if (!deptCode) return
  smartOpen.value = true
  smartLoading.value = true
  smartRows.value = []
  error.value = ''
  try {
    const result = await analyzeDepartmentRequisition({
      deptCode,
      deptName: selectedDepartment.value?.deptName,
      destinationWarehouseId: selectedWarehouse.value?.warehouseId,
      destinationWarehouseName: selectedWarehouse.value?.name,
      sourceWarehouseId: Number(filters.sourceWarehouseId),
      selectedPeriodDays: smartPeriodDays.value
    })
    smartAnalysisId.value = result.analysisId
    smartRows.value = result.rows.map((row) => ({ ...row, selected: Number(row.recommendedQty || 0) > 0 }))
  } catch (err) {
    notifyError(resolveErrorMessage(err, '智能补货分析失败'))
    smartOpen.value = false
  } finally {
    smartLoading.value = false
  }
}

async function generateSmartRequisitions() {
  const rows = smartRows.value.filter((row) => row.selected && Number(row.recommendedQty) > 0)
  if (!rows.length) {
    error.value = '请至少选择一条建议并填写大于零的申领数量'
    return
  }
  smartGenerating.value = true
  error.value = ''
  try {
    const result = await generateRequisitionsFromAnalysis({
      analysisId: smartAnalysisId.value,
      requisitionType: requisitionType.value || undefined,
      items: smartRows.value.map((row) => ({
        analysisItemId: Number(row.analysisItemId),
        quantity: Math.max(0, Number(row.recommendedQty || 0)),
        selected: row.selected
      }))
    })
    notify.success(`智能补货已生成 ${result.createdCount} 张申领单：${result.requisitionNos.join('、')}`)
    smartOpen.value = false
    await loadRequisitionOrders()
  } catch (err) {
    notifyError(resolveErrorMessage(err, '智能补货生成申领单失败'))
  } finally {
    smartGenerating.value = false
  }
}

async function resetFilters() {
  Object.assign(filters, {
    productCode: '',
    productName: '',
    specModel: '',
    manufacturerName: '',
    targetDeptCode: options.value.departments[0]?.deptCode || '',
    mode: ''
  })
  currentPage.value = 1
  await loadLinkedWarehouses()
  await loadCatalog()
}

onMounted(async () => {
  await loadRequisitionOrders()
})

async function changeDepartment() {
  await loadLinkedWarehouses()
  currentPage.value = 1
  await loadCatalog()
}

async function changeWarehouse() {
  error.value = ''
  currentPage.value = 1
  await loadCatalog()
}

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const DEPTREQORDERS_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'documentNo', label: '单据号', width: 180, visible: true, weight: 4, minWidth: 130, maxWidth: 250 },
  { key: 'deptName', label: '科室', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'applicant', label: '请购人', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'requisitionTime', label: '申领时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'totalQty', label: '商品总数量', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'totalAmount', label: '总金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'status', label: '状态', width: 130, visible: true, weight: 2, minWidth: 90, maxWidth: 170 },
  { key: 'actions', label: '操作', width: 160, visible: true, fill: true, minWidth: 130, maxWidth: 240 }
]

const deptReqOrdersCols = useListColumns({
  storageKey: 'spd-dept-req-orders-cols-v1',
  defaults: DEPTREQORDERS_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const deptReqOrdersTableRef = deptReqOrdersCols.tableRef
const fillHeightDeptReqOrders = useFillHeightTable({ tableRef: deptReqOrdersTableRef })
const isDeptReqOrdersColVisible = (key: string) => deptReqOrdersCols.isColumnVisible(key)
const deptReqOrdersColBinding = (key: string) => deptReqOrdersCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const deptReqOrdersActionFix = useActionColumnFix()
const deptReqOrdersActionFixed = computed(() => deptReqOrdersActionFix.actionFixedBinding.value)

/**
 * 历史申领单加载失败 → 通用消息接口抛出（Element Plus 消息弹层），
 * 不再在页面上方渲染一条内联错误条。
 */
watch(historyLoadError, (message) => {
  if (!message) return
  notifyError(message)
})

watch(
  [deptReqOrdersTableRef],
  () => {
    deptReqOrdersActionFix.observe(deptReqOrdersTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const deptReqOrdersColSettingsAnchorRef = ref<HTMLElement | null>(null)
const deptReqOrdersColSettingsPopRef = ref<HTMLElement | null>(null)
const deptReqOrdersColSettingsPop = useColSettingsPopover({
  open: deptReqOrdersCols.settingsOpen,
  anchorRef: deptReqOrdersColSettingsAnchorRef,
  popRef: deptReqOrdersColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const DEPTREQPRODUCTS_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'col0', label: '', width: 60, visible: true, weight: 1, minWidth: 48, maxWidth: 80 },
  { key: 'productCode', label: '商品编码', width: 160, visible: true, weight: 3, minWidth: 120, maxWidth: 210 },
  { key: 'productName', label: '商品名称', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'spec', label: '规格型号', width: 170, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'col4', label: '生产厂家', width: 170, visible: true, weight: 5, minWidth: 120, maxWidth: 280 },
  { key: 'baseUnit', label: '基本单位', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'looseAvailable', label: '散货可用', width: 140, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'packageAvailable', label: '定数包可用', width: 140, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'defaultRequisition', label: '默认申领', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'requisitionQty', label: '申领数量', width: 150, visible: true, weight: 3, minWidth: 110, maxWidth: 210 },
  { key: 'convertedQty', label: '本次折算', width: 140, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'template', label: '模板', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'status', label: '状态', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'unitPrice', label: '单价', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'chargeable', label: '是否收费', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'centralized', label: '是否集采', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 150, fill: true }
]

const deptReqProductsCols = useListColumns({
  storageKey: 'spd-dept-req-products-cols-v1',
  defaults: DEPTREQPRODUCTS_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const deptReqProductsTableRef = deptReqProductsCols.tableRef
const fillHeightDeptReqProducts = useFillHeightTable({ tableRef: deptReqProductsTableRef })
const isDeptReqProductsColVisible = (key: string) => deptReqProductsCols.isColumnVisible(key)
const deptReqProductsColBinding = (key: string) => deptReqProductsCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const deptReqProductsColSettingsAnchorRef = ref<HTMLElement | null>(null)
const deptReqProductsColSettingsPopRef = ref<HTMLElement | null>(null)
const deptReqProductsColSettingsPop = useColSettingsPopover({
  open: deptReqProductsCols.settingsOpen,
  anchorRef: deptReqProductsColSettingsAnchorRef,
  popRef: deptReqProductsColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const DEPTREQDETAIL_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'productCode', label: '商品编码', width: 160, visible: true, weight: 3, minWidth: 120, maxWidth: 210 },
  { key: 'productName', label: '商品名称', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'requisitionMode', label: '申领模式', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'template', label: '模板', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'conversion', label: '换算', width: 150, visible: true, weight: 3, minWidth: 110, maxWidth: 210 },
  { key: 'manufacturer', label: '厂家', width: 170, visible: true, weight: 5, minWidth: 120, maxWidth: 280 },
  { key: 'unitPrice', label: '单价', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'col7', label: '申请数量', width: 130, visible: true, weight: 2, minWidth: 90, maxWidth: 170 },
  { key: 'approvedQty', label: '审批数量', width: 130, visible: true, weight: 2, minWidth: 90, maxWidth: 170 },
  { key: 'fulfillProgress', label: '履约进度', width: 150, visible: true, weight: 3, minWidth: 110, maxWidth: 210 },
  { key: 'uniqueCode', label: '唯一码', width: 170, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'applyAmount', label: '申请金额', width: 130, visible: true, weight: 2, minWidth: 90, maxWidth: 170 },
  { key: 'registrationNo', label: '注册证', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 240, fill: true }
]

const deptReqDetailCols = useListColumns({
  storageKey: 'spd-dept-req-detail-cols-v1',
  defaults: DEPTREQDETAIL_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const deptReqDetailTableRef = deptReqDetailCols.tableRef
const fillHeightDeptReqDetail = useFillHeightTable({ tableRef: deptReqDetailTableRef })
const isDeptReqDetailColVisible = (key: string) => deptReqDetailCols.isColumnVisible(key)
const deptReqDetailColBinding = (key: string) => deptReqDetailCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const deptReqDetailColSettingsAnchorRef = ref<HTMLElement | null>(null)
const deptReqDetailColSettingsPopRef = ref<HTMLElement | null>(null)
const deptReqDetailColSettingsPop = useColSettingsPopover({
  open: deptReqDetailCols.settingsOpen,
  anchorRef: deptReqDetailColSettingsAnchorRef,
  popRef: deptReqDetailColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const DEPTREQSMART_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'selection', label: '选择', width: 80, visible: true, weight: 1, minWidth: 60, maxWidth: 110 },
  { key: 'deptTarget', label: '科室 / 目标库', width: 170, visible: true, weight: 5, minWidth: 120, maxWidth: 260 },
  { key: 'product', label: '商品', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'mode', label: '模式', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'periodicDemand', label: '周期需求', width: 140, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'targetStock', label: '目标库库存', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'sourceAvailable', label: '来源库可用', width: 140, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'suggestedRequisition', label: '建议申领', width: 140, visible: true, weight: 3, minWidth: 100, maxWidth: 200, fill: true }
]

const deptReqSmartCols = useListColumns({
  storageKey: 'spd-dept-req-smart-cols-v1',
  defaults: DEPTREQSMART_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const deptReqSmartTableRef = deptReqSmartCols.tableRef
const fillHeightDeptReqSmart = useFillHeightTable({ tableRef: deptReqSmartTableRef })
const isDeptReqSmartColVisible = (key: string) => deptReqSmartCols.isColumnVisible(key)
const deptReqSmartColBinding = (key: string) => deptReqSmartCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const deptReqSmartColSettingsAnchorRef = ref<HTMLElement | null>(null)
const deptReqSmartColSettingsPopRef = ref<HTMLElement | null>(null)
const deptReqSmartColSettingsPop = useColSettingsPopover({
  open: deptReqSmartCols.settingsOpen,
  anchorRef: deptReqSmartColSettingsAnchorRef,
  popRef: deptReqSmartColSettingsPopRef
})

</script>

<template><section class="dept-req-page spd-unified-list">
    <MdPageHeader
      crumb="供应链业务"
      title="科室申领"
      :loading="loading"
      @refresh="refreshCurrentView"
    />

    <StatusMessage :message="error" tone="error" />
      <StatusMessage :message="message" tone="success" />

      <section class="dept-req-table-wrap dept-req-history-card">
        <div class="dept-req-section-title">
          <History :size="18" />
          <h3>历史申领单</h3>
          <SpdButton label="新增申领" tone="primary" class="dept-req-create-btn" v-if="canCreateRequisition" @click="startRequisition">
          <template #icon><UserPlus :size="16" /></template>
        </SpdButton>
        </div>
        <SpdFilterBar aria-label="历史申领单查询" @submit="submitHistoryFilters" @reset="resetHistoryFilters">
          <label><span>开始日期</span>
            <el-date-picker
              v-model="historyFilters.dateFrom"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="开始日期"
              clearable
              class="dept-req-date-picker"
              :disabled-date="historyDisableAfterEnd"
            />
          </label>
          <label><span>结束日期</span>
            <el-date-picker
              v-model="historyFilters.dateTo"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="结束日期"
              clearable
              class="dept-req-date-picker"
              :disabled-date="historyDisableBeforeStart"
            />
          </label>
          <label>
            <span>状态</span>
            <select v-model="historyFilters.status">
              <option value="">全部状态</option>
              <option v-for="status in historyStatuses" :key="status" :value="status">{{ formatStatusText(status) }}</option>
            </select>
          </label>
          <label><span>科室</span><input v-model.trim="historyFilters.deptName" type="search" placeholder="输入科室名称" /></label>
          <label><span>申领单号</span><input v-model.trim="historyFilters.requisitionNo" type="search" placeholder="输入申领单号" /></label>
          <label>
            <span>排序</span>
            <select v-model="historyFilters.sort" @change="applyHistoryFilters">
              <option value="timeDesc">申领时间从新到旧</option>
              <option value="timeAsc">申领时间从旧到新</option>
              <option value="departmentAsc">科室名称升序</option>
              <option value="departmentDesc">科室名称降序</option>
            </select>
          </label>
        </SpdFilterBar>
        <StatusMessage :message="historyFilterError" tone="error" role="alert" />
              <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="deptReqOrdersColSettingsAnchorRef"
           
           
            @click="deptReqOrdersCols.settingsOpen.value = !deptReqOrdersCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="deptReqOrdersCols.settingsOpen.value"
ref="deptReqOrdersColSettingsPopRef"
:class="{ 'is-align-right': deptReqOrdersColSettingsPop.align.value === 'right', 'is-align-up': deptReqOrdersColSettingsPop.vertical.value === 'up' }"
:style="deptReqOrdersColSettingsPop.popStyle.value"
:columns="deptReqOrdersCols.columns.value"
:visible-column-count="deptReqOrdersCols.visibleColumnCount.value"
@toggle="deptReqOrdersCols.setColumnVisible"
@move="deptReqOrdersCols.moveColumn"
@reset="deptReqOrdersCols.resetColumns()"
@confirm="deptReqOrdersCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="deptReqOrdersTableRef"
            :data="requisitionOrders"
            row-key="requisitionNo"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightDeptReqOrders.maxHeight"
            style="width: 100%"
            :empty-text="'暂无申领单'"
            @header-dragend="(width: number, oldWidth: number, column: any) => deptReqOrdersCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="documentNo"
              v-if="isDeptReqOrdersColVisible('documentNo')"
              label="单据号"
              v-bind="deptReqOrdersColBinding('documentNo')"
            >
              <template #header>
                <span class="ulc-th-text">单据号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.bizNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="deptName"
              v-if="isDeptReqOrdersColVisible('deptName')"
              label="科室"
              v-bind="deptReqOrdersColBinding('deptName')"
            >
              <template #header>
                <span class="ulc-th-text">科室</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.deptName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="warehouseName"
              v-if="isDeptReqOrdersColVisible('warehouseName')"
              label="库房"
              v-bind="deptReqOrdersColBinding('warehouseName')"
            >
              <template #header>
                <span class="ulc-th-text">库房</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.warehouseName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="applicant"
              v-if="isDeptReqOrdersColVisible('applicant')"
              label="请购人"
              v-bind="deptReqOrdersColBinding('applicant')"
            >
              <template #header>
                <span class="ulc-th-text">请购人</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.applicantName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="requisitionTime"
              v-if="isDeptReqOrdersColVisible('requisitionTime')"
              label="申领时间"
              v-bind="deptReqOrdersColBinding('requisitionTime')"
            >
              <template #header>
                <span class="ulc-th-text">申领时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="totalQty"
              v-if="isDeptReqOrdersColVisible('totalQty')"
              label="商品总数量"
              v-bind="deptReqOrdersColBinding('totalQty')"
            >
              <template #header>
                <span class="ulc-th-text">商品总数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number">{{ row.totalQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="totalAmount"
              v-if="isDeptReqOrdersColVisible('totalAmount')"
              label="总金额"
              v-bind="deptReqOrdersColBinding('totalAmount')"
            >
              <template #header>
                <span class="ulc-th-text">总金额</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number">{{ money(row.totalAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isDeptReqOrdersColVisible('status')"
              label="状态"
              v-bind="deptReqOrdersColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatStatusText(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isDeptReqOrdersColVisible('actions')"
              label="操作"
              v-bind="deptReqOrdersColBinding('actions')"
              :fixed="deptReqOrdersActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button @click="openDetails(row.bizNo)" class="ulc-action btn-sm" type="button"><Eye :size="15" />
                    查看明细</button>
                <button v-if="canApproveRequisition && row.status === 'pending_approval'" :disabled="!!approvingRequisitionNo"
                    @click="approveRequisition(row)" class="ulc-action btn-sm btn-primary" type="button">{{ approvingRequisitionNo === row.bizNo ? '审批中…' : '审批通过' }}</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无申领单" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="historyPage"
            :total-pages="Math.max(1, Math.ceil(historyTotal / historySize))"
            :total="historyTotal"
            :size="historySize"
            :loading="loading"
            @update:page="changeHistoryPage"
            @update:size="changeHistorySize"
          />
        </template>
      </MdDataTableCard>
      </section>

    <ElDialog
      v-model="requisitionStarted"
      title="新增科室申领"
      width="min(1320px, 97vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      @closed="leaveRequisition"
      class="dept-req-dialog dept-req-new-dialog"
    >
      <!-- 弹窗头部右侧主操作 -->
      <template #header>
        <div class="dept-req-dialog-title">
          <span>新增科室申领</span>
          <div class="dept-req-dialog-submit">
            <span class="dept-req-selected-count">
              <ShoppingCart :size="14" />
              已选 <strong>{{ selectedItems.length }}</strong> 条
            </span>
            <SpdButton label="智能补货" tone="primary" :disabled="loading || optionsLoading || submitting || !!warehouseSetupMessage" @click="openSmartReplenishment">
          <template #icon><AlertTriangle :size="14" /></template>
        </SpdButton>
            <SpdButton label="提交申领" tone="primary" :disabled="loading || submitting || selectedItems.length === 0" @click="submitSelectedRequisitions">
          <template #icon><ShoppingCart :size="14" /></template>
        </SpdButton>
          </div>
        </div>
      </template>

      <!-- 筛选条：单行紧凑 -->
      <div class="dept-req-filterbar">
        <label class="dept-req-filterbar-item">
          <span>申领类型</span>
          <select v-model="requisitionType" :disabled="submitting || optionsLoading">
            <option value="">请选择（选填）</option>
            <option v-for="option in options.requisitionTypes" :key="option.optionValue" :value="option.optionValue">
              {{ option.optionLabel }}
            </option>
          </select>
        </label>
        <label class="dept-req-filterbar-item">
          <span>科室</span>
          <select v-model="filters.targetDeptCode" :disabled="submitting" @change="changeDepartment">
            <option value="">请选择科室</option>
            <option v-for="dept in options.departments" :key="dept.deptCode" :value="dept.deptCode">{{ dept.deptName }}</option>
          </select>
        </label>
        <label class="dept-req-filterbar-item">
          <span>收货库房</span>
          <select v-model="filters.warehouseId" aria-label="收货库房" :disabled="submitting" @change="selectDefaultSourceWarehouse(); changeWarehouse()">
            <option value="">请选择收货库房</option>
            <option v-for="warehouse in linkedWarehouses" :key="warehouse.code" :value="warehouse.warehouseId">{{ warehouse.name }}</option>
          </select>
        </label>
        <label class="dept-req-filterbar-item">
          <span>供货库房</span>
          <select v-model="filters.sourceWarehouseId" aria-label="供货库房" :disabled="submitting || !sourceWarehouses.length" @change="changeWarehouse">
            <option value="">请选择供货库房</option>
            <option v-for="warehouse in sourceWarehouses" :key="warehouse.code" :value="warehouse.warehouseId">{{ warehouse.code }} · {{ warehouse.name }}</option>
          </select>
        </label>
        <label class="dept-req-filterbar-item">
          <span>申领形式</span>
          <select v-model="filters.mode">
            <option value="">全部</option>
            <option value="quota_package">定数包优先</option>
            <option value="high_value">高值耗材</option>
            <option value="loose">散货申领</option>
          </select>
        </label>
        <label class="dept-req-filterbar-item">
          <span>商品编码</span>
          <input v-model.trim="filters.productCode" placeholder="商品编码" />
        </label>
        <label class="dept-req-filterbar-item">
          <span>商品名称</span>
          <input v-model.trim="filters.productName" placeholder="名称 / ID / HRP" />
        </label>
        <label class="dept-req-filterbar-item">
          <span>规格型号</span>
          <input v-model.trim="filters.specModel" placeholder="规格型号" />
        </label>
        <label class="dept-req-filterbar-item">
          <span>生产厂家</span>
          <input v-model.trim="filters.manufacturerName" placeholder="生产厂家" />
        </label>
        <div class="dept-req-filterbar-actions">
          <SpdButton label="查询" tone="primary" :disabled="loading" @click="searchCatalog">
          <template #icon><Search :size="14" /></template>
        </SpdButton>
          <SpdButton label="重置" tone="neutral" :disabled="loading || submitting" @click="resetFilters">
          <template #icon><RotateCcw :size="14" /></template>
        </SpdButton>
        </div>
      </div>

      <!-- 已选条：始终显示一行小标签，便于确认 -->
      <div v-if="selectedItems.length || submittedCount" class="dept-req-summary">
        <span v-if="submittedCount" class="dept-req-summary-ok">
          <Info :size="14" />本次已成功提交 {{ submittedCount }} 条申领记录
        </span>
        <span v-else class="dept-req-summary-hint">
          <Info :size="14" />当前库房：{{ selectedWarehouseLabel }} · 目录来自当前科室有效库房绑定 · 同一申领单支持高值耗材、定数包和散货
        </span>
      </div>

      <!-- 已选列表：淡色面板 -->
      <div v-if="selectedItems.length" class="dept-req-selected">
        <div class="dept-req-selected-head">
          <strong>请购列表（{{ selectedItems.length }}）</strong>
          <span>可调整默认申领类型与数量，不需要的商品可直接移除</span>
        </div>
        <ul class="dept-req-selected-list">
          <li v-for="item in selectedItems" :key="item.productCode">
            <span class="selected-code">{{ item.productCode }}</span>
            <strong class="selected-name">{{ item.productName }}</strong>
            <small class="selected-spec">{{ item.specModel }}</small>
            <label>
              <span>默认申领</span>
              <select v-model="item.mode" :disabled="submitting" @change="normalizeQuantity(item)">
                <option v-if="item.allowedModes.includes('loose')" value="loose">散货</option>
                <option v-if="item.allowedModes.includes('quota_package')" value="quota_package">定数包</option>
                <option v-if="item.allowedModes.includes('high_value')" value="high_value">高值耗材</option>
              </select>
            </label>
            <div class="qty-stepper">
              <SpdButton variant="text" :icon-only="true" aria-label="减少申领数量" :disabled="submitting" @click="stepQuantity(item, -1)">
                <template #icon><Minus :size="12" /></template>
              </SpdButton>
              <input v-integer-quantity v-model.number="item.quantity" type="number" min="1" step="1" aria-label="申领数量" :disabled="submitting" @change="normalizeQuantity(item)" />
              <SpdButton variant="text" :icon-only="true" aria-label="增加申领数量" :disabled="submitting" @click="stepQuantity(item, 1)">
                <template #icon><Plus :size="12" /></template>
              </SpdButton>
            </div>
            <span class="selected-total">{{ baseQtyByMode(item) }} {{ item.baseUnit }}</span>
            <SpdButton label="移除" :disabled="submitting" @click="removeFromSelection(item)">
              <template #icon><X :size="14" /></template>
            </SpdButton>
          </li>
        </ul>
      </div>

      <!-- 目录表：占大头 -->
      <div class="dept-req-catalog">
        <div class="dept-req-catalog-head">
          <h3><ShoppingCart :size="16" />科室申领目录</h3>
          <span class="dept-req-catalog-meta">共 {{ totalItems }} 条 · 第 {{ currentPage }} / {{ Math.max(1, Math.ceil(totalItems / pageSize)) }} 页</span>
        </div>
        <div class="dept-req-catalog-body">
                  <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="deptReqProductsColSettingsAnchorRef"
             
             
              @click="deptReqProductsCols.settingsOpen.value = !deptReqProductsCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="deptReqProductsCols.settingsOpen.value"
ref="deptReqProductsColSettingsPopRef"
:class="{ 'is-align-right': deptReqProductsColSettingsPop.align.value === 'right', 'is-align-up': deptReqProductsColSettingsPop.vertical.value === 'up' }"
:style="deptReqProductsColSettingsPop.popStyle.value"
:columns="deptReqProductsCols.columns.value"
:visible-column-count="deptReqProductsCols.visibleColumnCount.value"
@toggle="deptReqProductsCols.setColumnVisible"
@move="deptReqProductsCols.moveColumn"
@reset="deptReqProductsCols.resetColumns()"
@confirm="deptReqProductsCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="deptReqProductsTableRef"
              :data="filteredProducts"
              border
              stripe
              class="ulc-table is-sticky"
              :max-height="fillHeightDeptReqProducts.maxHeight"
              style="width: 100%"
              :empty-text="'暂无可申领商品'"
              @header-dragend="(width: number, oldWidth: number, column: any) => deptReqProductsCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="col0"
                v-if="isDeptReqProductsColVisible('col0')"
                label=""
                v-bind="deptReqProductsColBinding('col0')"
              >
                <template #header>
                  <span class="ulc-th-text"></span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text"><input v-model="row.selected" :disabled="submitting" type="checkbox" /></span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productCode"
                v-if="isDeptReqProductsColVisible('productCode')"
                label="商品编码"
                v-bind="deptReqProductsColBinding('productCode')"
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
                v-if="isDeptReqProductsColVisible('productName')"
                label="商品名称"
                v-bind="deptReqProductsColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <strong>{{ row.productName }}</strong>
                    <small>{{ row.supplierName }}</small>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="spec"
                v-if="isDeptReqProductsColVisible('spec')"
                label="规格型号"
                v-bind="deptReqProductsColBinding('spec')"
              >
                <template #header>
                  <span class="ulc-th-text">规格型号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.specModel }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="col4"
                v-if="isDeptReqProductsColVisible('col4')"
                label="生产厂家"
                v-bind="deptReqProductsColBinding('col4')"
              >
                <template #header>
                  <span class="ulc-th-text">生产厂家</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.manufacturerName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="baseUnit"
                v-if="isDeptReqProductsColVisible('baseUnit')"
                label="基本单位"
                v-bind="deptReqProductsColBinding('baseUnit')"
              >
                <template #header>
                  <span class="ulc-th-text">基本单位</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.baseUnit }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="looseAvailable"
                v-if="isDeptReqProductsColVisible('looseAvailable')"
                label="散货可用"
                v-bind="deptReqProductsColBinding('looseAvailable')"
              >
                <template #header>
                  <span class="ulc-th-text">散货可用</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-joined">{{ row.looseAvailableQty }} {{ row.baseUnit }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="packageAvailable"
                v-if="isDeptReqProductsColVisible('packageAvailable')"
                label="定数包可用"
                v-bind="deptReqProductsColBinding('packageAvailable')"
              >
                <template #header>
                  <span class="ulc-th-text">定数包可用</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <strong>{{ row.packageAvailableQty }} 包</strong>
                    <small v-if="row.packageQuantity">1 包 = {{ row.packageQuantity }} {{ row.packageUnit }}</small>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="defaultRequisition"
                v-if="isDeptReqProductsColVisible('defaultRequisition')"
                label="默认申领"
                v-bind="deptReqProductsColBinding('defaultRequisition')"
              >
                <template #header>
                  <span class="ulc-th-text">默认申领</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text"><select v-model="row.mode" :disabled="submitting" @change="normalizeQuantity(row)">
                      <option v-if="row.allowedModes.includes('loose')" value="loose">散货</option>
                      <option v-if="row.allowedModes.includes('quota_package')" value="quota_package">定数包</option>
                      <option v-if="row.allowedModes.includes('high_value')" value="high_value">高值耗材</option>
                    </select></span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="requisitionQty"
                v-if="isDeptReqProductsColVisible('requisitionQty')"
                label="申领数量"
                v-bind="deptReqProductsColBinding('requisitionQty')"
              >
                <template #header>
                  <span class="ulc-th-text">申领数量</span>
                </template>
                <template #default="{ row }">
                  <div class="qty-stepper">
                    <SpdButton variant="text" :icon-only="true" aria-label="减少申领数量" :disabled="submitting" @click="stepQuantity(row, -1)">
                      <template #icon><Minus :size="12" /></template>
                    </SpdButton>
                    <input v-integer-quantity v-model.number="row.quantity" type="number" min="1" step="1" aria-label="申领数量" :disabled="submitting" @change="normalizeQuantity(row)" />
                    <SpdButton variant="text" :icon-only="true" aria-label="增加申领数量" :disabled="submitting" @click="stepQuantity(row, 1)">
                      <template #icon><Plus :size="12" /></template>
                    </SpdButton>
                  </div>
                    <small>{{ unitByMode(row) }}</small>
                    <small v-if="row.quantity > availableByMode(row)" class="shortage">超过当前可用库存，按实际需求申领</small>
                </template>
              </el-table-column>
              <el-table-column
                column-key="convertedQty"
                v-if="isDeptReqProductsColVisible('convertedQty')"
                label="本次折算"
                v-bind="deptReqProductsColBinding('convertedQty')"
              >
                <template #header>
                  <span class="ulc-th-text">本次折算</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-joined">{{ baseQtyByMode(row) }} {{ row.baseUnit }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="template"
                v-if="isDeptReqProductsColVisible('template')"
                label="模板"
                v-bind="deptReqProductsColBinding('template')"
              >
                <template #header>
                  <span class="ulc-th-text">模板</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <strong>{{ row.templateCode }}</strong>
                    <small>{{ row.templateName }}</small>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="status"
                v-if="isDeptReqProductsColVisible('status')"
                label="状态"
                v-bind="deptReqProductsColBinding('status')"
              >
                <template #header>
                  <span class="ulc-th-text">状态</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ formatStatusText(row.requisitionStatus) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="unitPrice"
                v-if="isDeptReqProductsColVisible('unitPrice')"
                label="单价"
                v-bind="deptReqProductsColBinding('unitPrice')"
              >
                <template #header>
                  <span class="ulc-th-text">单价</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-number">{{ row.unitPrice }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="chargeable"
                v-if="isDeptReqProductsColVisible('chargeable')"
                label="是否收费"
                v-bind="deptReqProductsColBinding('chargeable')"
              >
                <template #header>
                  <span class="ulc-th-text">是否收费</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ Number(row.chargeable) === 1 ? '是' : '否' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="centralized"
                v-if="isDeptReqProductsColVisible('centralized')"
                label="是否集采"
                v-bind="deptReqProductsColBinding('centralized')"
              >
                <template #header>
                  <span class="ulc-th-text">是否集采</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ Number(row.centralized) === 1 ? '是' : '否' }}</span>
                </template>
              </el-table-column>
              <template #empty>
                <!-- 空态优先显示前置条件提示：
                     未选科室 / 未关联收货库房 / 未选供货库房时，
                     说明具体原因，而不是笼统显示「暂无可申领商品」。 -->
                <MdEmptyState :text="error || warehouseSetupMessage || '暂无可申领商品'" />
              </template>
            </el-table>
          </div>
        <template #pagination>
          <MdPagination
            :page="currentPage"
            :total-pages="Math.max(1, Math.ceil(totalItems / pageSize))"
            :total="totalItems"
            :size="pageSize"
            :loading="loading"
            @update:page="changePage"
            @update:size="changePageSize"
          />
        </template>
        </MdDataTableCard>
        </div>
      </div>
    </ElDialog>

    <div v-if="detailOpen" class="dept-req-modal-backdrop" @click.self="closeDetails">
      <section class="dept-req-modal" role="dialog" aria-modal="true" aria-labelledby="requisition-detail-title">
        <header>
          <div>
            <span id="requisition-detail-title">{{ approvalMode ? '科室申领审批' : '科室请购明细' }}</span>
            <strong>{{ activeRequisitionNo }}</strong>
          </div>
          <SpdButton label="关闭" :disabled="!!approvingRequisitionNo" @click="closeDetails">
          <template #icon><X :size="16" /></template>
        </SpdButton>
        </header>
        <StatusMessage :message="approvalError" tone="error" />
                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="deptReqDetailColSettingsAnchorRef"
             
             
              @click="deptReqDetailCols.settingsOpen.value = !deptReqDetailCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="deptReqDetailCols.settingsOpen.value"
ref="deptReqDetailColSettingsPopRef"
:class="{ 'is-align-right': deptReqDetailColSettingsPop.align.value === 'right', 'is-align-up': deptReqDetailColSettingsPop.vertical.value === 'up' }"
:style="deptReqDetailColSettingsPop.popStyle.value"
:columns="deptReqDetailCols.columns.value"
:visible-column-count="deptReqDetailCols.visibleColumnCount.value"
@toggle="deptReqDetailCols.setColumnVisible"
@move="deptReqDetailCols.moveColumn"
@reset="deptReqDetailCols.resetColumns()"
@confirm="deptReqDetailCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="deptReqDetailTableRef"
              :data="detailRows"
              border
              stripe
              class="ulc-table is-sticky"
              :max-height="fillHeightDeptReqDetail.maxHeight"
              style="width: 100%"
              :empty-text="'暂无申领明细'"
              @header-dragend="(width: number, oldWidth: number, column: any) => deptReqDetailCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="productCode"
                v-if="isDeptReqDetailColVisible('productCode')"
                label="商品编码"
                v-bind="deptReqDetailColBinding('productCode')"
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
                v-if="isDeptReqDetailColVisible('productName')"
                label="商品名称"
                v-bind="deptReqDetailColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-cell-stack">
                    <span>{{ row.productName || '-' }}</span>
                    <small v-if="row.specModel">{{ row.specModel }}</small>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                column-key="requisitionMode"
                v-if="isDeptReqDetailColVisible('requisitionMode')"
                label="申领模式"
                v-bind="deptReqDetailColBinding('requisitionMode')"
              >
                <template #header>
                  <span class="ulc-th-text">申领模式</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.itemType === 'quota_package' ? '定数包' : row.itemType === 'high_value' ? '高值耗材' : '散货' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="template"
                v-if="isDeptReqDetailColVisible('template')"
                label="模板"
                v-bind="deptReqDetailColBinding('template')"
              >
                <template #header>
                  <span class="ulc-th-text">模板</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.templateCode ? `${row.templateCode} v${row.templateVersion}` : '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="conversion"
                v-if="isDeptReqDetailColVisible('conversion')"
                label="换算"
                v-bind="deptReqDetailColBinding('conversion')"
              >
                <template #header>
                  <span class="ulc-th-text">换算</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-joined">{{ row.packageCount ? `${row.packageCount} 包 × ${row.packageQuantity} ${row.packageUnit}` : '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="manufacturer"
                v-if="isDeptReqDetailColVisible('manufacturer')"
                label="厂家"
                v-bind="deptReqDetailColBinding('manufacturer')"
              >
                <template #header>
                  <span class="ulc-th-text">厂家</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.manufacturerName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="unitPrice"
                v-if="isDeptReqDetailColVisible('unitPrice')"
                label="单价"
                v-bind="deptReqDetailColBinding('unitPrice')"
              >
                <template #header>
                  <span class="ulc-th-text">单价</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-number">{{ money(row.unitPrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="col7"
                v-if="isDeptReqDetailColVisible('col7')"
                label="申请数量"
                v-bind="deptReqDetailColBinding('col7')"
              >
                <template #header>
                  <span class="ulc-th-text">申请数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-number">{{ row.applyQuantity || 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="approvedQty"
                v-if="isDeptReqDetailColVisible('approvedQty')"
                label="审批数量"
                v-bind="deptReqDetailColBinding('approvedQty')"
              >
                <template #header>
                  <span class="ulc-th-text">审批数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text"><template v-if="approvalFactor(row) > 0 && !Number(row.pickedQuantity)">
                      <input v-model.number="approvalQuantities[Number(row.itemId)]" class="dept-req-approval-quantity"
                        type="number" inputmode="numeric" min="1" step="1" :aria-label="`${row.productCode} 审批数量`"
                        :disabled="!!approvingRequisitionNo" />
                      <small>{{ row.itemType === 'quota_package' ? '包' : row.unit }}</small>
                    </template>
                    <span v-else>沿用申请数量</span></span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="fulfillProgress"
                v-if="isDeptReqDetailColVisible('fulfillProgress')"
                label="履约进度"
                v-bind="deptReqDetailColBinding('fulfillProgress')"
              >
                <template #header>
                  <span class="ulc-th-text">履约进度</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-joined">{{ row.pickedQuantity || 0 }} / {{ row.applyQuantity || 0 }}，剩余 {{ row.remainingQuantity || 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="uniqueCode"
                v-if="isDeptReqDetailColVisible('uniqueCode')"
                label="唯一码"
                v-bind="deptReqDetailColBinding('uniqueCode')"
              >
                <template #header>
                  <span class="ulc-th-text">唯一码</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.uniqueCodes || (row.itemType === 'high_value' ? '待拣配绑定' : '-') }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="applyAmount"
                v-if="isDeptReqDetailColVisible('applyAmount')"
                label="申请金额"
                v-bind="deptReqDetailColBinding('applyAmount')"
              >
                <template #header>
                  <span class="ulc-th-text">申请金额</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-number">{{ money(row.applyAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="registrationNo"
                v-if="isDeptReqDetailColVisible('registrationNo')"
                label="注册证"
                v-bind="deptReqDetailColBinding('registrationNo')"
              >
                <template #header>
                  <span class="ulc-th-text">注册证</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.registrationNo || '-' }}</span>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无申领明细" />
              </template>
            </el-table>
          </div>
        </MdDataTableCard>
        <footer v-if="approvalMode" class="dept-req-approval-actions">
          <SpdButton label="取消" tone="neutral" :disabled="!!approvingRequisitionNo" @click="closeDetails"></SpdButton>
          <SpdButton tone="primary" :disabled="detailLoading || !detailRows.length || !!approvingRequisitionNo"
            @click="confirmApproval">
          {{ approvingRequisitionNo ? '审批中…' : '确认通过' }}
        </SpdButton>
        </footer>
      </section>
    </div>

    <ElDialog v-model="smartOpen" title="智能补货分析" width="min(1280px, 98vw)" :close-on-click-modal="false" class="dept-req-smart-dialog">
      <div class="smart-toolbar">
        <label>
          <span>统计周期</span>
          <select v-model.number="smartPeriodDays" :disabled="smartLoading || smartGenerating" @change="openSmartReplenishment">
            <option :value="5">近 5 天</option>
            <option :value="7">近 7 天</option>
            <option :value="15">近 15 天</option>
            <option :value="30">近 30 天</option>
          </select>
        </label>
        <span>建议量可调整；来源中心库不足不会截断真实需求。</span>
      </div>
            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="deptReqSmartColSettingsAnchorRef"
           
           
            @click="deptReqSmartCols.settingsOpen.value = !deptReqSmartCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="deptReqSmartCols.settingsOpen.value"
ref="deptReqSmartColSettingsPopRef"
:class="{ 'is-align-right': deptReqSmartColSettingsPop.align.value === 'right', 'is-align-up': deptReqSmartColSettingsPop.vertical.value === 'up' }"
:style="deptReqSmartColSettingsPop.popStyle.value"
:columns="deptReqSmartCols.columns.value"
:visible-column-count="deptReqSmartCols.visibleColumnCount.value"
@toggle="deptReqSmartCols.setColumnVisible"
@move="deptReqSmartCols.moveColumn"
@reset="deptReqSmartCols.resetColumns()"
@confirm="deptReqSmartCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="deptReqSmartTableRef"
            :data="smartRows"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightDeptReqSmart.maxHeight"
            style="width: 100%"
            :empty-text="'暂无智能补货建议'"
            @header-dragend="(width: number, oldWidth: number, column: any) => deptReqSmartCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="selection"
              v-if="isDeptReqSmartColVisible('selection')"
              label="选择"
              v-bind="deptReqSmartColBinding('selection')"
            >
              <template #header>
                <span class="ulc-th-text">选择</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text"><input v-model="row.selected" type="checkbox" /></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="deptTarget"
              v-if="isDeptReqSmartColVisible('deptTarget')"
              label="科室 / 目标库"
              v-bind="deptReqSmartColBinding('deptTarget')"
            >
              <template #header>
                <span class="ulc-th-text">科室 / 目标库</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <strong>{{ row.deptName }}</strong>
                  <small>{{ row.warehouseName }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="product"
              v-if="isDeptReqSmartColVisible('product')"
              label="商品"
              v-bind="deptReqSmartColBinding('product')"
            >
              <template #header>
                <span class="ulc-th-text">商品</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <strong>{{ row.productName }}</strong>
                  <small>{{ row.productCode }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="mode"
              v-if="isDeptReqSmartColVisible('mode')"
              label="模式"
              v-bind="deptReqSmartColBinding('mode')"
            >
              <template #header>
                <span class="ulc-th-text">模式</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.itemMode === 'quota_package' ? '定数包' : row.itemMode === 'high_value' ? '高值耗材' : '散货' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="periodicDemand"
              v-if="isDeptReqSmartColVisible('periodicDemand')"
              label="周期需求"
              v-bind="deptReqSmartColBinding('periodicDemand')"
            >
              <template #header>
                <span class="ulc-th-text">周期需求</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-joined">{{ row.periodDemand }} {{ row.baseUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="targetStock"
              v-if="isDeptReqSmartColVisible('targetStock')"
              label="目标库库存"
              v-bind="deptReqSmartColBinding('targetStock')"
            >
              <template #header>
                <span class="ulc-th-text">目标库库存</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-joined">{{ row.currentQty }} {{ row.baseUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="sourceAvailable"
              v-if="isDeptReqSmartColVisible('sourceAvailable')"
              label="来源库可用"
              v-bind="deptReqSmartColBinding('sourceAvailable')"
            >
              <template #header>
                <span class="ulc-th-text">来源库可用</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-joined">{{ row.sourceAvailableQty }} {{ row.baseUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="suggestedRequisition"
              v-if="isDeptReqSmartColVisible('suggestedRequisition')"
              label="建议申领"
              v-bind="deptReqSmartColBinding('suggestedRequisition')"
            >
              <template #header>
                <span class="ulc-th-text">建议申领</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text"><input v-integer-quantity v-model.number="row.recommendedQty" type="number" min="0" step="1" :disabled="!row.selected" />
                <small>{{ row.itemMode === 'quota_package' ? '包' : row.baseUnit }}</small></span>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无智能补货建议" />
            </template>
          </el-table>
        </div>
      </MdDataTableCard>
      <template #footer>
        <div class="dept-req-smart-footer">
          <SpdButton label="取消" tone="neutral" :disabled="smartGenerating" @click="smartOpen = false"></SpdButton>
          <SpdButton tone="primary" :disabled="smartLoading || smartGenerating" @click="generateSmartRequisitions">
          {{ smartGenerating ? '正在生成...' : '确认并生成申领单' }}
        </SpdButton>
        </div>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped>
/* =====================================================================
   科室申领 dept-req-page
   ---------------------------------------------------------------------
   视觉沿用采购管理 / 收货验收 / 库存管理的 md-* 设计语言：
     1. 页头改用 MdPageHeader（渐变底 + 白色标题 + 内建刷新）
     2. 查询区由无标签输入条改为「标签 + 控件」网格
     3. 历史单 / 申领目录统一为单卡片 + 段标题 + md 密度表格
     4. 提示条由彩色块改为可扫读的单行提示
   ===================================================================== */
.dept-req-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  max-width: none;
  margin: 0 !important;
  padding: 16px 20px 24px;
  border: 0 !important;
  background: transparent;
  overflow: visible;
}

.dept-req-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

.dept-req-actions {
  display: flex;
  gap: 8px;
}

/* ---------- 历史申领单查询区：Element Plus 中文日期选择器 ----------
   el-date-picker 外壳是 .el-input__wrapper，共享样式表
   styles/components/spd-input.css 已把高度 / 圆角 / 边框 / 字号
   统一到字段令牌（34px / --spd-p-field-radius / 1px 内阴影环 / 基础字号）。
   这里只补两件事：
     1) 选择器撑满栅格列宽（EP 默认是 inline-block，不会跟着 label 走）；
     2) 兜住共享样式表里「原生 input 统一规格」那条通用规则，
        避免内层 input 被画成第二层小框（框中框）。
   三套配色方案都走同一批令牌，无需额外适配。 */
.dept-req-date-picker {
  width: 100%;
}

.dept-req-date-picker .el-input__wrapper {
  width: 100%;
}

.dept-req-actions .btn {
  min-height: 32px;
  padding: 0 14px;
  font-size: var(--spd-font-size-base);
  border-radius: var(--spd-radius-sm, 6px);
}

/* ---------- 提示条 ---------- */
.dept-req-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 10px 14px;
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  color: var(--md-text-secondary, var(--spd-text-secondary));
}

.dept-req-tags > svg {
  flex-shrink: 0;
  color: var(--md-primary, var(--primary));
}

.dept-req-tags span {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
}

.dept-req-tags .blue {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 8%, var(--spd-p-bg));
  color: var(--md-primary-deep, var(--spd-p-info-strong));
}

.dept-req-tags .yellow {
  background: var(--spd-p-warning-bg);
  color: var(--spd-p-warning);
}

.dept-req-tags .orange {
  background: var(--spd-p-warning-bg);
  color: var(--spd-p-danger-text);
}

/* ---------- 查询区 ---------- */
/* 历史申领单查询区已迁移为统一筛选栏组件 <SpdFilterBar>：
   栅格列数 / 换行规则 / 控件高度 / 标签排版 / 操作区位置
   统一由 styles/components/spd-filter-bar.css 的「标准规则」提供，
   本页不再覆写（原先写死 32px，与标准 --spd-p-field-height 34px 不一致）。 */

.dept-req-submit-btn {
  margin-left: auto;
  border-color: var(--md-primary, var(--primary)) !important;
  background: var(--md-primary, var(--primary)) !important;
  color: var(--spd-p-bg) !important;
}

.dept-req-submit-btn:disabled {
  opacity: 0.55;
}

/* ---------- 列表卡片 ---------- */
.dept-req-table-wrap {
  min-width: 0;
  overflow: auto;
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
}

.dept-req-section-title {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px 12px;
  background: var(--md-bg-card, var(--spd-p-bg));
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
  color: var(--md-primary, var(--primary));
}

.dept-req-section-title h3 {
  margin: 0;
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

.dept-req-section-title > span {
  margin-left: auto;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-xs);
  font-variant-numeric: tabular-nums;
}

/* 段标题右侧的主操作：新增申领 / 智能补货 */
/* 「新增申领 / 智能生成」按钮：完全服从 SpdButton 统一几何，
   仅保留靠右布局，不再覆盖高度 / 内边距 / 字号 / 圆角。 */
.dept-req-create-btn,
.dept-req-smart-btn {
  margin-left: auto;
}

/* ---------- 表格：md 密度 ---------- */
.dept-req-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

.dept-req-table th,
.dept-req-table td {
  padding: 9px 12px;
  max-width: 210px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
}

.dept-req-table th {
  position: sticky;
  top: 46px;
  z-index: 2;
  background: var(--md-bg-subtle, var(--spd-p-bg-subtle));
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: var(--spd-font-size-table-head);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
}

.dept-req-table td {
  color: var(--md-text, var(--spd-text-primary));
  background: var(--md-bg-card, var(--spd-p-bg));
}

.dept-req-table tbody tr:nth-child(even) td {
  background: color-mix(in srgb, var(--md-bg-subtle, var(--spd-p-bg-subtle)) 46%, var(--spd-p-bg));
}

.dept-req-table tbody tr:hover td {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 6%, var(--spd-p-bg));
}

.dept-req-table tr.selected td {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 9%, var(--spd-p-bg)) !important;
}

.dept-req-table tr.low:not(.selected) td {
  background: var(--spd-p-warning-bg) !important;
}

.requisition-order-table {
  min-width: 1180px;
}

.requisition-catalog-table {
  min-width: 1540px;
}

.requisition-catalog-table strong,
.requisition-catalog-table small {
  display: block;
}

.requisition-catalog-table small {
  margin-top: 2px;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-sm);
}

.requisition-catalog-table select {
  min-height: 30px;
  padding: 0 8px;
  color: var(--md-text, var(--spd-text-primary));
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
  font-size: var(--spd-font-size-sm);
}

.requisition-catalog-table .shortage {
  color: var(--spd-p-warning-strong);
}

:deep(.hospital-pagination) {
  margin: 0;
  padding: 12px 16px;
  border-top: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
}

/* ---------- 已选清单 ---------- */
.dept-req-selected {
  margin: 0;
  overflow: hidden;
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
}

.dept-req-selected-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--md-bg-card, var(--spd-p-bg));
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
}

.dept-req-selected-head strong {
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.dept-req-selected-head span {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-xs);
}

.dept-req-selected-list {
  list-style: none;
  max-height: 260px;
  margin: 0;
  padding: 0;
  overflow: auto;
}

.dept-req-selected-list li {
  display: grid;
  grid-template-columns: 130px minmax(160px, 1fr) minmax(120px, 0.8fr) 170px 146px 100px auto;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
}

.dept-req-selected-list label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dept-req-selected-list label span {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-sm);
  white-space: nowrap;
}

.dept-req-selected-list select {
  padding: 4px 6px;
  color: var(--md-text, var(--spd-text-primary));
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
  font: inherit;
  font-size: var(--spd-font-size-sm);
}

.selected-code {
  color: var(--md-primary, var(--primary));
  font-weight: 600;
  font-size: var(--spd-font-size-xs);
}

.selected-name {
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: var(--spd-font-size-base);
}

.selected-spec {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-total {
  color: var(--md-text, var(--spd-text-primary));
  font-size: var(--spd-font-size-base);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* ---------- 数量步进器 ---------- */
.qty-stepper {
  display: inline-grid;
  grid-template-columns: 28px 88px 28px;
  align-items: center;
  overflow: hidden;
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
}

.qty-stepper button {
  display: grid;
  place-items: center;
  width: 100%;
  min-width: 0;
  min-height: 30px;
  height: 30px;
  border: 0;
  background: var(--md-bg-subtle, var(--spd-p-bg-subtle));
  cursor: pointer;
}

.qty-stepper button:hover {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 10%, var(--spd-p-bg));
}

.qty-stepper input {
  box-sizing: border-box;
  width: 88px;
  min-width: 0;
  height: 30px;
  padding: 0 6px;
  border: 0;
  border-inline: 1px solid var(--md-border, var(--spd-border-subtle));
  text-align: center;
  font-size: var(--spd-font-size-base);
  font-variant-numeric: tabular-nums;
}

/* ---------- 明细弹窗 ---------- */
.dept-req-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background: color-mix(in srgb, var(--spd-p-text-strong) 36%, transparent);
}

.dept-req-modal {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: min(1320px, 94vw);
  max-height: 82vh;
  overflow: hidden;
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border);
  border-radius: 14px;
  box-shadow: var(--spd-p-shadow-dialog);
}

.dept-req-modal > header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  gap: 16px;
  background: var(--spd-p-info-bg);
  border-bottom: 1px solid var(--spd-p-text);
}

.dept-req-modal > header div {
  display: grid;
  gap: 4px;
  min-width: 0;
  overflow-wrap: anywhere;
}

.dept-req-modal > header span {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.dept-req-modal > header strong {
  color: var(--spd-p-text);
  font-size: var(--spd-font-size-lg);
}

.dept-req-modal .dept-req-table-wrap {
  min-height: 0;
  max-height: calc(82vh - 70px);
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.dept-req-approval-quantity {
  box-sizing: border-box;
  width: 3.25rem;
  min-height: 30px;
  margin-inline-end: 4px;
  padding: 3px 5px;
  border: 1px solid var(--spd-p-info-border);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: inherit;
  font: inherit;
  text-align: center;
  appearance: textfield;
}

.dept-req-approval-quantity::-webkit-inner-spin-button,
.dept-req-approval-quantity::-webkit-outer-spin-button {
  appearance: none;
  margin: 0;
}

.dept-req-approval-quantity:focus-visible {
  outline: 2px solid var(--primary, var(--spd-p-info));
  outline-offset: 2px;
}

.dept-req-approval-actions {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--spd-p-text);
  background: var(--spd-p-info-bg);
}

.requisition-detail-table {
  min-width: 820px;
}

.requisition-detail-table th,
.requisition-detail-table td {
  padding-inline: 8px;
  white-space: normal;
  overflow-wrap: anywhere;
  text-overflow: clip;
}

.requisition-detail-table td small {
  color: var(--spd-p-text-secondary);
}

.requisition-detail-table th {
  top: 0;
}

/* ---------- 智能补货弹窗 ---------- */
/* 智能补货弹窗：放宽、按钮间距加大、表格撑满 */
.dept-req-smart-dialog :deep(.el-dialog) {
  margin: 12px auto !important;
  width: min(1280px, 98vw) !important;
  min-height: 560px;
  max-height: calc(100vh - 24px);
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dept-req-smart-dialog :deep(.el-dialog__body) {
  padding: 0 !important;
  flex: 1 1 auto;
  min-height: 480px;
  display: flex;
  flex-direction: column;
  background: var(--spd-p-bg);
}

.dept-req-smart-dialog :deep(.el-dialog__header) {
  padding: 14px 22px;
  border-bottom: 1px solid var(--spd-p-bg-muted);
  flex: 0 0 auto;
}

.dept-req-smart-dialog :deep(.el-dialog__title) {
  font-size: var(--spd-font-size-xl);
  font-weight: 600;
  /* 标题文字：深色（此前误用边框浅色作文字，几乎不可见） */
  color: var(--spd-p-text);
}

.dept-req-smart-dialog :deep(.el-dialog__footer) {
  padding: 12px 22px;
  border-top: 1px solid var(--spd-p-bg-muted);
  flex: 0 0 auto;
}

.dept-req-smart-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
}

.dept-req-smart-footer .btn {
  min-width: 96px;
  height: 34px;
  padding: 0 18px;
  font-size: var(--spd-font-size-base);
  border-radius: 6px;
}

.smart-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 22px;
  margin: 0;
  background: var(--spd-p-bg-muted);
  border-bottom: 1px solid var(--spd-p-bg-muted);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.smart-toolbar label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.smart-toolbar select {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid var(--spd-p-border);
  border-radius: 5px;
  font: inherit;
  font-size: var(--spd-font-size-sm);
  background: var(--spd-p-bg);
  /* 输入文字：深色（此前误用边框浅色作文字，几乎不可见） */
  color: var(--spd-p-text);
}

.smart-toolbar span {
  color: var(--spd-p-text-secondary);
}

.smart-table-wrap {
  flex: 1 1 auto;
  min-height: 420px;
  overflow: auto;
  margin: 0;
  border: none;
  border-radius: 0;
  background: var(--spd-p-bg);
}

.smart-table {
  min-width: 980px;
}

.smart-table td strong,
.smart-table td small {
  display: block;
}

.smart-table td input[type='number'] {
  width: 90px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.smart-table .shortage {
  color: var(--spd-p-warning-text);
  font-weight: 600;
}

/* ---------- 响应式 ---------- */
@media (max-width: 760px) {
  .dept-req-page {
    padding: 12px 12px 20px;
  }

  .dept-req-selected {
    padding: 14px;
  }

  .dept-req-submit-btn {
    margin-left: 0;
  }
}

/* ---------- 新增申领弹窗 ---------- */
.dept-req-dialog :deep(.el-dialog) {
  margin: 12px auto !important;
  width: min(1320px, 98vw);
  min-height: 620px;
  max-height: calc(100vh - 24px);
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  overflow: hidden;
}

.dept-req-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: 1px solid var(--spd-p-bg-muted);
}

.dept-req-dialog :deep(.el-dialog__title) {
  display: none;
}

.dept-req-dialog :deep(.el-dialog__headerbtn) {
  top: 14px;
  right: 16px;
  z-index: 2;
}

.dept-req-dialog :deep(.el-dialog__body) {
  padding: 0 !important;
  flex: 1 1 auto;
  min-height: 540px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 自定义 header 区域 */
.dept-req-dialog-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 56px 14px 22px;
  gap: 16px;
}

.dept-req-dialog-title > span {
  font-size: var(--spd-font-size-2xl);
  font-weight: 600;
  line-height: var(--spd-lh-tight);
  /* 标题文字：深色（此前误用边框浅色作文字，几乎不可见） */
  color: var(--spd-p-text);
  letter-spacing: 0.2px;
}

.dept-req-dialog-submit {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dept-req-selected-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 10px;
  height: 28px;
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-info-strong);
  background: var(--spd-p-info-bg);
  border-radius: 14px;
}

.dept-req-selected-count strong {
  color: var(--spd-p-info-strong);
  font-weight: 700;
}

.dept-req-dialog-submit .btn {
  height: 30px;
  padding: 0 12px;
  font-size: var(--spd-font-size-base);
  border-radius: 6px;
}

/* 筛选条：单行（左侧 label/输入紧凑，右侧按钮） */
.dept-req-filterbar {
  flex: 0 0 auto;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 22px;
  background: var(--spd-p-bg);
  border-bottom: 1px solid var(--spd-p-bg-muted);
}

.dept-req-filterbar-item {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
  min-width: 110px;
  flex: 1 1 110px;
  max-width: 180px;
}

.dept-req-filterbar-item > span {
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-muted);
  font-weight: 600;
  letter-spacing: 0.3px;
}

.dept-req-filterbar-item > input,
.dept-req-filterbar-item > select {
  height: 30px;
  padding: 0 10px;
  font-size: var(--spd-font-size-base);
  /* 输入文字：深色（此前误用边框浅色作文字，几乎不可见） */
  color: var(--spd-p-text);
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-border);
  border-radius: 5px;
  transition: border-color 0.15s, background 0.15s;
  min-width: 0;
}

.dept-req-filterbar-item > input:focus,
.dept-req-filterbar-item > select:focus {
  border-color: var(--spd-p-info);
  background: var(--spd-p-bg);
  outline: none;
}

.dept-req-filterbar-actions {
  margin-left: auto;
  display: inline-flex;
  gap: 8px;
  flex: 0 0 auto;
}

.dept-req-filterbar-actions .btn {
  height: 30px;
  padding: 0 12px;
  font-size: var(--spd-font-size-base);
  border-radius: 5px;
}

/* 摘要条：单行小字，浅色背景 */
.dept-req-summary {
  flex: 0 0 auto;
  padding: 8px 22px;
  background: var(--spd-p-bg-muted);
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
  border-bottom: 1px solid var(--spd-p-bg-muted);
}

.dept-req-summary-ok {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-primary-bg);
  padding: 4px 10px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dept-req-summary-hint {
  color: var(--spd-p-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 已选列表：浅色背景面板 */
.dept-req-selected {
  flex: 0 0 auto;
  margin: 10px 22px 0 22px;
  padding: 10px 14px;
  background: var(--spd-p-info-bg);
  border-radius: 8px;
  max-height: 200px;
  overflow: auto;
}

.dept-req-selected-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.dept-req-selected-head strong {
  color: var(--spd-p-info-strong);
  font-size: var(--spd-font-size-sm);
}

.dept-req-selected-head span {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-xs);
}

.dept-req-selected-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dept-req-selected-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  background: var(--spd-p-bg);
  border-radius: 6px;
  font-size: var(--spd-font-size-base);
  line-height: var(--spd-lh-tight);
}

.dept-req-selected-list .selected-code {
  font-family: var(--spd-font-family-mono);
  /* 浅底深字（此前是近黑底配蓝字，几乎不可见且偏蓝） */
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-xs);
  font-variant-numeric: tabular-nums;
  background: var(--spd-p-bg-muted);
  padding: 1px 6px;
  border-radius: 3px;
}

.dept-req-selected-list .selected-name {
  /* 商品名：深色（此前误用边框浅色作文字，几乎不可见） */
  color: var(--spd-p-text);
  font-weight: 600;
  font-size: var(--spd-font-size-base);
  min-width: 140px;
}

.dept-req-selected-list .selected-spec {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  flex: 1 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dept-req-selected-list label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-muted);
}

.dept-req-selected-list label select {
  height: 26px;
  font-size: var(--spd-font-size-sm);
  border: 1px solid var(--spd-p-border);
  border-radius: 4px;
  background: var(--spd-p-bg);
  padding: 0 6px;
}

.dept-req-selected-list .qty-stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--spd-p-border);
  border-radius: 4px;
  overflow: hidden;
}

.dept-req-selected-list .qty-stepper .spd-btn {
  width: 22px;
  min-width: 22px;
  height: 22px;
  min-height: 22px;
  background: transparent;
  border: none;
  color: var(--spd-p-text-secondary);
}

.dept-req-selected-list .qty-stepper input {
  width: 56px;
  height: 22px;
  border: none;
  border-left: 1px solid var(--spd-p-border);
  border-right: 1px solid var(--spd-p-border);
  text-align: center;
  font-size: var(--spd-font-size-base);
  font-variant-numeric: tabular-nums;
}

.dept-req-selected-list .selected-total {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  min-width: 80px;
}

.dept-req-selected-list .btn-text-danger {
  background: transparent;
  border: none;
  color: var(--spd-p-danger);
  font-size: var(--spd-font-size-sm);
  display: inline-flex;
  align-items: center;
  gap: 2px;
  cursor: pointer;
}

/* 目录：占大头 */
.dept-req-catalog {
  flex: 1 1 auto;
  min-height: 460px;
  display: flex;
  flex-direction: column;
  margin: 10px 22px 14px 22px;
  background: var(--spd-p-bg);
  border-radius: 10px;
  box-shadow: var(--spd-p-shadow-1);
  overflow: hidden;
}

.dept-req-catalog-head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--spd-p-bg-muted);
  border-bottom: 1px solid var(--spd-p-bg-muted);
}

.dept-req-catalog-head h3 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
  /* 标题文字：深色（此前误用边框浅色作文字，几乎不可见） */
  color: var(--spd-p-text);
}

.dept-req-catalog-head h3 svg {
  color: var(--spd-p-info);
}

.dept-req-catalog-meta {
  font-size: var(--spd-font-size-xs);
  color: var(--spd-p-text-muted);
  font-variant-numeric: tabular-nums;
}

.dept-req-catalog-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 0;
}

.dept-req-catalog-body table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base);
}

.dept-req-catalog-body thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--spd-p-bg);
  font-weight: 700;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-table-head);
  line-height: var(--spd-lh-tight);
  letter-spacing: 0.2px;
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--spd-p-border);
  white-space: nowrap;
}

.dept-req-catalog-body tbody td {
  padding: 7px 10px;
  font-size: var(--spd-font-size-base);
  line-height: var(--spd-lh-tight);
  /* 表格正文：深色（此前误用边框浅色作文字，几乎不可见） */
  color: var(--spd-p-text);
  border-bottom: 1px solid var(--spd-p-text-strong);
  vertical-align: middle;
}

.dept-req-catalog-body tbody tr:hover {
  background: var(--spd-p-bg-muted);
}

.dept-req-catalog-body tbody tr.selected {
  background: var(--spd-p-info-bg);
}

.dept-req-catalog-body tbody tr.low {
  background: var(--spd-p-danger-bg);
}

/* ---------- 数字列：居中 + 等宽数字 ----------
   与统一列表标准一致：金额 / 数量类单元格与普通列一样**居中**，
   数字使用等宽字体，便于纵向比对。
   具体样式统一由 styles/list/unified-list.css 的 .ulc-number 提供，
   这里不再重复定义，避免各页面出现不同的对齐口径。 */
</style>

