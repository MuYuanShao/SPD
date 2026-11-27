<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { notifyError as notifyErrorShared, notifyWarning } from '../../composables/useNotify'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import 'element-plus/theme-chalk/el-dialog.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import { useRoute } from 'vue-router'
import {
  AlertTriangle,
  Building2,
  CheckCircle2,
  ClipboardCheck,
  FileCheck2,
  FileText,
  History,
  PackageCheck,
  RefreshCw,
  RotateCcw,
  Save,
  ScanLine,
  Search,
  Smartphone,
  Truck,
  X
} from '@lucide/vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import HighValueOperationsPanel from '../../components/supply-chain/HighValueOperationsPanel.vue'
import {
  bindHighValuePatient,
  confirmHighValuePicking,
  confirmLoosePicking,
  confirmPicking,
  createColdChainException,
  createConsumption,
  createRecall,
  fetchPendingRecallReceipts,
  receiveRecall,
  type PendingRecallReceipt,
  createRequisition,
  fetchClosureList,
  fetchClosureOptions,
  fetchClosureOverview,
  fetchRedFlushOptions,
  fetchServerPagedClosureList,
  fetchPickingLooseStock,
  fetchPickingPackageLabelDetail,
  fetchPickingPackageLabels,
  fetchPickingRequisitions,
  fetchPickingUniqueCodes,
  fetchRecallBatches,
  fetchRecallInventory,
  processRequisition,
  generateShortage,
  receiveHighValueBillingCallback,
  resolveConsumptionProduct,
  reverseConsumption,
  smartReplenishmentAnalysis,
  uploadPdaOffline,
  type ClosureOptions,
  type PackageLabelDetail
} from '../../api/operationalClosure'
import { fetchDepartmentWarehouses, type DepartmentWarehouseRelation } from '../../api/masterData'
import { consumeQuotaPackageByCode } from '../../api/udiTraceability'
import { formatApiErrorText, formatBusinessText, formatStatusText } from '../../utils/chineseDisplay'
import { usePagedFilters } from '../../composables/usePagedFilters'
import { formatDateTime } from '../../utils/dateTime'
import type { PageResult } from '../../api/http'
import { useAuthStore } from '../../stores/auth'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'

import {
  Columns3,
  ChevronDown
} from '@lucide/vue'
import { confirmWarehouseReturn, fetchWarehouseReturnQueue, type ReturnRecord } from '../../api/stockReturns'
import { useFillHeightTable } from '../../composables/useFillHeightTable'

const route = useRoute()
const loading = ref(false)
const actionError = ref('')
const recallDialogError = ref('')
const deliverySubmitting = ref(false)
const confirmingWarehouseReturn = ref('')
const reversingConsumption = ref(false)
const auth = useAuthStore()

/** 各业务动作的兜底失败文案，避免一律显示"操作失败" */
const ACTION_FAILURE_TEXT: Record<string, string> = {
  shortage: '补货任务生成失败，请重试',
  requisition: '科室申领提交失败，请重试',
  approveRequisition: '申领审批失败，请重试',
  rejectRequisition: '申领驳回失败，请重试',
  delivery: '拣配出库失败，请重试',
  consumption: '消耗登记失败，请检查科室库存后重试',
  reverse: '反消耗红冲失败，请重试',
  pda: 'PDA 离线补传失败，请重试',
  cold: '冷链异常登记失败，请重试',
  recall: '召回隔离失败，请重试',
  bindPatient: '患者绑定失败，请重试',
  billingCallback: '计费回传失败，请重试'
}
const rows = ref<Record<string, unknown>[]>([])
const analysisRows = ref<Record<string, unknown>[]>([])
const pickingRequisitionRows = ref<Record<string, unknown>[]>([])
const pickingFilters = reactive({ deptCode: '', dateFrom: '', dateTo: '' })
const appliedPickingFilters = ref({ ...pickingFilters })
let pickingRequestVersion = 0
const pickingPackageRows = ref<Record<string, unknown>[]>([])
const selectedPickingRequisitionNo = ref('')
const selectedPickingItemId = ref('')
const selectedPickingItemType = ref('loose')
const selectedPickingLabels = ref<string[]>([])
const pickingPackageScanCode = ref('')
const pickingUniqueCodeRows = ref<Array<Record<string, unknown>>>([])
const selectedPickingUniqueCodes = ref<string[]>([])
const pickingLooseRows = ref<Array<Record<string, unknown> & { pickQty?: number }>>([])
const pickingLooseLoading = ref(false)
const pickingLooseError = ref('')
const pickingLooseStatus = computed(() => {
  if (selectedPickingItemType.value !== 'loose') return ''
  if (pickingLooseLoading.value) return '正在查询散货…'
  if (pickingLooseError.value) return pickingLooseError.value
  if (!pickingLooseRows.value.length) return '当前库房没有该商品的可用散货'
  return ''
})
let pickingSourceRequest = 0
const maxPickingDetailSize = 50
const pickingDetailSize = ref(10)
const pickingDetailPage = ref(1)
const pickingDetailTotal = computed(() => {
  switch (selectedPickingItemType.value) {
    case 'unique_code': return pickingUniqueCodeRows.value.length
    case 'quota_package': return pickingPackageRows.value.length
    case 'loose': return pickingLooseRows.value.length
    default: return 0
  }
})
const pickingDetailLastPage = computed(() => Math.max(1, Math.ceil(pickingDetailTotal.value / pickingDetailSize.value)))

function pagePickingDetails<T>(rows: T[]) {
  const start = (pickingDetailPage.value - 1) * pickingDetailSize.value
  return rows.slice(start, start + pickingDetailSize.value)
}

function changePickingDetailSize(size: number) {
  pickingDetailSize.value = Math.min(size, maxPickingDetailSize)
  pickingDetailPage.value = 1
}

const linkedWarehouses = ref<DepartmentWarehouseRelation[]>([])
const summary = ref<Record<string, number>>({})
const analysisDialogOpen = ref(false)
const analysisLoading = ref(false)
const analysisError = ref('')
const analysisMeta = ref<Record<string, unknown> | null>(null)
const autoSmartAnalysisHandled = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)

// 待拣配申领单前端分页
const pickingPage = ref(1)
const pickingSize = ref(20)
const pickingTotal = ref(0)
const options = ref<ClosureOptions>({ departments: [], warehouses: [], products: [], balances: [] })
const packageDetail = ref<PackageLabelDetail | null>(null)
const packageDetailOpen = ref(false)
const packageDetailLoading = ref(false)

/** 拣配配送只展示一级库（中心库）库房，出库来源为一级库库存 */
const primaryWarehouses = computed(() =>
  options.value.warehouses.filter((item) =>
    /一级|中心/.test(String(item.warehouseType ?? ''))
  )
)

/** 待拣配申领单前端分页展示 */
const paginatedPickingRequisitions = computed(() => {
  const start = (pickingPage.value - 1) * pickingSize.value
  return pickingRequisitionRows.value.slice(start, start + pickingSize.value)
})

/** 拣配配送底部表格：history type */

const form = reactive({
  deptName: '',
  deptCode: '',
  warehouseId: '' as number | '',
  warehouseName: '',
  productCode: '',
  quantity: 1,
  minQty: 1,
  currentQty: 0,
  replenishQty: '' as number | '',
  replenishmentDays: 7 as number | 'custom',
  customReplenishmentDays: '' as number | '',
  requisitionNo: '',
  period: '2026-05',
  deviceNo: 'PDA-001',
  operationType: 'delivery_sign',
  temperature: 9,
  severity: 'high',
  reason: '召回隔离',
  patientNo: 'MZ-0001',
  patientNameMasked: '',
  uniqueCodes: '',
  externalChargeNo: ''
})

const consumptionQueryCode = ref('')
const consumptionResolved = ref<Record<string, unknown> | null>(null)
const consumptionResolving = ref(false)
const recallBatches = ref<Array<Record<string, unknown>>>([])
const recallBatchesLoading = ref(false)
const recallBatchId = ref<number | null>(null)
const recallDialogOpen = ref(false)
const recallSubmitting = ref(false)
const recallBusinessType = ref<'recall' | 'isolate'>('recall')
const recallScope = ref<'all' | 'primary' | 'secondary' | 'tertiary'>('all')
const defaultRecallHistoryFilters = { dateFrom: '', dateTo: '', productName: '', productCode: '', deptName: '' }
const recallHistoryFilters = reactive({ ...defaultRecallHistoryFilters })
const appliedRecallHistoryFilters = ref({ ...defaultRecallHistoryFilters })
const recallHistoryError = ref('')
const receivingRecallNo = ref('')
const recallBatchNo = ref('')
const recallInventoryRows = ref<Array<Record<string, unknown>>>([])

const pageCode = computed(() => String(route.meta.operationalType || route.params.code))
const type = computed(() => {
  if (['shortage-reminder', 'replenishment-task'].includes(pageCode.value)) return 'shortage'
  if (['picking-distribution', 'picking-delivery'].includes(pageCode.value)) return 'delivery'
  if (['department-requisition'].includes(pageCode.value)) return 'requisition'
  if (['department-consumption', 'reverse-consumption'].includes(pageCode.value)) return 'consumption'
  if (pageCode.value === 'red-flush-management') return 'red-flush'
  if (['settlement-reconciliation', 'supplier-detail-caliber'].includes(pageCode.value)) return 'settlement'
  if (['pda-offline-record'].includes(pageCode.value)) return 'pda'
  if (['cold-chain-monitoring', 'recall-isolation'].includes(pageCode.value)) return 'risk'
  if (['high-value-consumables'].includes(pageCode.value)) return 'high-value'
  return 'shortage'
})
const isRecallPage = computed(() => pageCode.value === 'recall-isolation')
const titleMap: Record<string, string> = {
  shortage: '缺货补货闭环',
  delivery: '拣配配送',
  requisition: '科室申领',
  consumption: '科室消耗',
  settlement: '结算对账',
  pda: 'PDA 基础作业与离线补传',
  risk: '冷链异常 / 召回隔离',
  'high-value': '高值使用计费基础'
}
const title = computed(() => pageCode.value === 'red-flush-management' ? '红冲管理' : titleMap[type.value])

/**
 * 动作条右侧是否有可显示的按钮。
 *
 * 「动作区」原本是一个始终渲染的 .hospital-action-row 容器，
 * 在红冲 / 结算对账 / PDA 离线补传 / 召回隔离等分支下**没有任何子节点**，
 * 于是在筛选栏下方留下一条「空的有边框白条」（用户诉求：把这个空白边框删掉）。
 *
 * 现在右侧动作区由这个计算属性把关：
 *   - 有按钮 → 渲染 .spd-action-bar__aside；
 *   - 无按钮 → 整块不渲染，页面上不会出现空边框容器。
 */
const hasActionButtons = computed(() => {
  if (type.value === 'shortage') return true
  if (type.value === 'requisition') return true
  if (type.value === 'delivery') return true
  if (type.value === 'consumption') return true
  if (type.value === 'risk' && !isRecallPage.value) return true
  return false
})

const replenishmentDayOptions = [5, 7, 15, 30]
const selectedDepartment = computed(() => options.value.departments.find(item =>
  type.value === 'consumption' && form.deptCode ? item.deptCode === form.deptCode : item.deptName === form.deptName))
const warehouseChoices = computed(() => {
  if (type.value === 'delivery') return primaryWarehouses.value
  if (isRecallPage.value) {
    const pattern = recallScope.value === 'primary' ? /一级|中心/ : recallScope.value === 'secondary' ? /二级/ : /三级/
    return options.value.warehouses.filter(item => pattern.test(String(item.warehouseType || '')))
  }
  if (['shortage', 'requisition', 'consumption'].includes(type.value)) {
    return linkedWarehouses.value.map(item => ({ warehouseName: item.name, warehouseId: item.warehouseId }))
  }
  return options.value.warehouses
})
const effectiveReplenishmentDays = computed(() => {
  const raw = form.replenishmentDays === 'custom' ? form.customReplenishmentDays : form.replenishmentDays
  const days = Number(raw)
  return Number.isFinite(days) && days > 0 ? days : 7
})

const settlementLoadError = ref('')
const settlementSummary = ref<Record<string, number>>({})
const redFlushOptions = ref<Awaited<ReturnType<typeof fetchRedFlushOptions>>>({ departments: [], warehouses: [] })
const redFlushFilters = reactive({ deptCode: '', warehouseId: '' })
const appliedRedFlushFilters = ref({ ...redFlushFilters })
const redFlushError = ref('')
const redFlushWarehouses = computed(() =>
  redFlushOptions.value.warehouses.filter(item => item.deptCode === redFlushFilters.deptCode))
const {
  filters: settlementFilters, filterError: settlementFilterError, apply: applySettlementFilters,
  reset: resetSettlementFilters, load: loadSettlement, beginRequest: beginClosureRequest
} = usePagedFilters({
  defaults: { dateFrom: '', dateTo: '', traceCode: '', deptName: '', productKeyword: '', goodsType: '', sort: 'timeDesc' },
  page: currentPage,
  size: pageSize,
  loading,
  loadError: settlementLoadError,
  errorMessage: '结算明细加载失败',
  isActive: () => type.value === 'settlement',
  fetchPage: params => fetchServerPagedClosureList('settlement', params),
  onPage: (result) => {
    rows.value = result.rows
    totalItems.value = result.total
    settlementSummary.value = result.summary ?? {}
  },
  onError: () => {
    rows.value = []
    totalItems.value = 0
    settlementSummary.value = {}
  }
})

/**
 * 结算明细筛选的「查询」。
 *
 * 日期区间规则（产品硬约束）：**开始时间不得晚于结束时间**。
 * usePagedFilters 内部也会拦截，但只写页面内联错误条；这里在请求发出前
 * 统一用 notifyError 给出明确提示，并且不触发任何网络请求。
 */
function applySettlementFiltersWithGuard() {
  const { dateFrom, dateTo } = settlementFilters
  if (dateFrom && dateTo && dateFrom > dateTo) {
    notifyWarning('开始日期不能晚于结束日期')
    return
  }
  applySettlementFilters()
}

function settlementGoodsLabel(value: unknown) {
  return ({ high_value: '高值耗材', quota_package: '定数包', loose: '散货', unknown: '待核对货型' } as Record<string, string>)[String(value)] || '-'
}

function shortagePayload() {
  return {
    ...form,
    replenishmentDays: effectiveReplenishmentDays.value,
    replenishQty: form.replenishQty === '' ? undefined : form.replenishQty
  }
}

async function loadLinkedWarehouses(isCurrent = () => true) {
  const requestedType = type.value
  const deptCode = selectedDepartment.value?.deptCode
  if (!deptCode) {
    linkedWarehouses.value = []
    if (type.value !== 'delivery') form.warehouseName = ''
    return
  }
  const isCurrentDepartment = () =>
    isCurrent() && type.value === requestedType && selectedDepartment.value?.deptCode === deptCode
  try {
    const warehouses = await fetchDepartmentWarehouses(deptCode)
    if (!isCurrentDepartment()) return
    linkedWarehouses.value = warehouses.filter(item => Number(item.selected ?? 0) === 1)
    // 选择科室时默认带出科室所关联的库房
    const preferred = linkedWarehouses.value[0]?.name || ''
    if (type.value === 'consumption') {
      const selected = linkedWarehouses.value.find(item => item.warehouseId === form.warehouseId) || linkedWarehouses.value[0]
      form.warehouseId = selected?.warehouseId ?? ''
      form.warehouseName = selected?.name || ''
      return
    }
    if (!linkedWarehouses.value.some(item => item.name === form.warehouseName)) {
      form.warehouseName = preferred
    }
  } catch (error) {
    if (!isCurrentDepartment()) return
    linkedWarehouses.value = []
    if (type.value !== 'delivery') form.warehouseName = ''
    notifyError(apiErrorText(error, '关联库房加载失败'))
  }
}

function applyRouteSmartDefaults() {
  const deptName = String(route.query.deptName || '').trim()
  const warehouseName = String(route.query.warehouseName || '').trim()
  if (deptName) form.deptName = deptName
  if (warehouseName) form.warehouseName = warehouseName
}

async function maybeOpenRouteSmartAnalysis() {
  if (autoSmartAnalysisHandled.value || route.query.autoSmart !== '1' || type.value !== 'shortage') return
  autoSmartAnalysisHandled.value = true
  applyRouteSmartDefaults()
  await openSmartAnalysis()
}

async function loadData() {
  const requestedType = type.value
  const recallPage = isRecallPage.value
  if (requestedType === 'settlement') return loadSettlement()
  const isLatest = beginClosureRequest()
  const isCurrent = () => isLatest() && type.value === requestedType && isRecallPage.value === recallPage
  loading.value = true
  if (requestedType === 'red-flush') {
    redFlushError.value = ''
    try {
      const [filterOptions, result] = await Promise.all([
        fetchRedFlushOptions(),
        fetchServerPagedClosureList('red-flush', {
          ...appliedRedFlushFilters.value, page: String(currentPage.value), size: String(pageSize.value)
        })
      ])
      if (!isCurrent()) return
      redFlushOptions.value = filterOptions
      rows.value = result.rows
      totalItems.value = result.total
      pageSize.value = result.size
    } catch (cause) {
      if (!isCurrent()) return
      rows.value = []
      totalItems.value = 0
      redFlushError.value = apiErrorText(cause, '红冲记录加载失败')
    } finally {
      if (isCurrent()) loading.value = false
    }
    return
  }
  try {
    if (recallPage) recallHistoryError.value = ''
    applyRouteSmartDefaults()
    const [overview, optionData, listData] = await Promise.all([
      fetchClosureOverview(),
      fetchClosureOptions(),
      recallPage ? fetchServerPagedClosureList('recall', {
        ...appliedRecallHistoryFilters.value, page: String(currentPage.value), size: String(pageSize.value)
      }) : requestedType === 'delivery' ? null
        : fetchClosureList(requestedType, { page: String(currentPage.value), size: String(pageSize.value) })
    ])
    if (!isCurrent()) return
    summary.value = overview.summary
    options.value = optionData
    rows.value = listData?.rows ?? []
    totalItems.value = listData?.total ?? 0
    if (!form.deptName && optionData.departments[0]) form.deptName = optionData.departments[0].deptName
    if (type.value === 'consumption' && !form.deptCode) {
      const department = optionData.departments.find(item => item.deptName === form.deptName) || optionData.departments[0]
      form.deptCode = department?.deptCode || ''
      form.deptName = department?.deptName || ''
    }
    applyRouteSmartDefaults()
    if (['shortage', 'requisition', 'consumption'].includes(type.value)) {
      await loadLinkedWarehouses(isCurrent)
      if (!isCurrent()) return
    } else if (!form.warehouseName && optionData.warehouses[0]) {
      form.warehouseName = optionData.warehouses[0].warehouseName
    }
    if (!form.productCode && optionData.products[0]) form.productCode = optionData.products[0].productCode
    if (type.value === 'delivery') {
      // 拣配配送只允许选择一级库（中心库），展示一级库库存
      if (!primaryWarehouses.value.some(item => item.warehouseName === form.warehouseName)) {
        form.warehouseName = primaryWarehouses.value[0]?.warehouseName || ''
      }
      await loadPickingRequisitions(isCurrent)
    }
    await maybeOpenRouteSmartAnalysis()
  } catch (cause) {
    if (!isCurrent()) return
    if (!recallPage) throw cause
    rows.value = []
    totalItems.value = 0
    recallHistoryError.value = apiErrorText(cause, '召回与隔离记录加载失败')
  } finally {
    if (isCurrent()) loading.value = false
  }
}

const deliveryTabs = [
  { key: 'picking', label: '拣配管理' },
  { key: 'returns', label: '科室退货确认' },
  { key: 'recalls', label: '召回确认' }
] as const
type DeliveryTab = typeof deliveryTabs[number]['key']
const deliveryTab = ref<DeliveryTab>('picking')
const visibleDeliveryTabs = computed(() => auth.canWrite('picking-delivery') ? deliveryTabs : deliveryTabs.slice(0, 1))

function useDeliveryQueue<T>(fetchPage: (params: { page: number; size: number }) => Promise<PageResult<T>>, errorMessage: string) {
  const result = ref<PageResult<T>>({ rows: [], total: 0, page: 1, size: 20 })
  const loading = ref(false)
  const error = ref('')
  let version = 0

  async function load(page = result.value.page, size = result.value.size) {
    if (type.value !== 'delivery' || !auth.canWrite('picking-delivery')) return
    const request = ++version
    loading.value = true
    error.value = ''
    try {
      let data = await fetchPage({ page, size })
      if (request !== version) return
      while (data.page > Math.max(1, Math.ceil(data.total / data.size))) {
        data = await fetchPage({ page: Math.max(1, Math.ceil(data.total / data.size)), size: data.size })
        if (request !== version) return
      }
      result.value = data
    } catch (cause) {
      if (request !== version) return
      result.value = { ...result.value, rows: [] }
      error.value = apiErrorText(cause, errorMessage)
    } finally {
      if (request === version) loading.value = false
    }
  }

  return reactive({ result, loading, error, load })
}

const returnQueue = useDeliveryQueue(fetchWarehouseReturnQueue, '待确认退库单加载失败')
const recallQueue = useDeliveryQueue(fetchPendingRecallReceipts, '召回待签收列表加载失败')

function refreshDeliveryTab() {
  if (deliveryTab.value === 'returns') return returnQueue.load()
  if (deliveryTab.value === 'recalls') return recallQueue.load()
  return loadData()
}

function changeDeliveryTab(tab: DeliveryTab) {
  if (deliveryTab.value === tab) return
  deliveryTab.value = tab
  void refreshDeliveryTab()
}

async function confirmDepartmentReturn(row: ReturnRecord) {
  if (confirmingWarehouseReturn.value || !auth.canWrite('picking-delivery')) return
  confirmingWarehouseReturn.value = row.returnNo
  try {
    try {
      await ElMessageBox.confirm(
        `确认退库单「${row.returnNo}」的实物已到达 ${row.destinationWarehouse || '中心库'}？`,
        '确认科室退库', { type: 'warning', confirmButtonText: '确认退库', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
    await confirmWarehouseReturn(row.returnNo)
    await returnQueue.load()
    notifySuccess(`科室退库已确认：${row.returnNo}`)
  } catch (cause) {
    notifyError(apiErrorText(cause, '科室退库确认失败'))
  } finally {
    confirmingWarehouseReturn.value = ''
  }
}

/**
 * 统一筛选栏（SpdFilterBar 默认操作区）的「查询」处理。
 *
 * 根因修复（补货任务点击「查询」无任何网络请求）：
 *   SpdFilterBar 的默认操作区会渲染 `type="submit"` 的「查询」按钮，
 *   并在点击时 emit('submit')。本页面原先只写了筛选栏组件而没有监听
 *   @submit / @reset，页面上也不存在 form 元素，因此按钮点击后既不提交表单
 *   也不触发任何处理函数 —— 表现为「点查询没反应、不发请求」。
 *   现在显式监听这两个事件，按当前页面类型分发到对应的查询逻辑。
 */
function applyFilterBarFilters() {
  if (type.value === 'delivery') {
    // 拣配配送的日期筛选在独立的 picking 筛选表单里
    void applyPickingFilters()
    return
  }
  if (isRecallPage.value) {
    applyRecallHistoryFilters()
    return
  }
  // 补货任务 / 科室申领 / 科室消耗 / 冷链 / PDA 等页面：列表范围由服务端按
  // 当前登录人权限限定，查询动作即重新拉取当前列表与选项数据。
  currentPage.value = 1
  void loadData()
}

/** 统一筛选栏的「重置」处理：清空本页筛选条件后重新查询。 */
function resetFilterBarFilters() {
  if (type.value === 'delivery') {
    resetPickingFilters()
    return
  }
  if (isRecallPage.value) {
    resetRecallHistoryFilters()
    return
  }
  form.deptName = ''
  form.warehouseName = ''
  form.productCode = ''
  form.quantity = 1
  // 复位到与 form 初始声明一致的默认值：
  // replenishmentDays 是 number | 'custom'，temperature 是 number，
  // 二者都不能赋空串（否则筛选永远带着非法值去查询）。
  form.replenishmentDays = 7
  form.deviceNo = ''
  form.temperature = 9
  // recallBatchNo 是独立的 ref，不在 form 里
  recallBatchNo.value = ''
  form.reason = ''
  form.uniqueCodes = ''
  form.deptCode = ''
  currentPage.value = 1
  void loadData()
}

function applyRecallHistoryFilters() {
  const { dateFrom, dateTo } = recallHistoryFilters
  if (dateFrom && dateTo && dateFrom > dateTo) {
    notifyWarning('开始日期不能晚于结束日期')
    return
  }
  appliedRecallHistoryFilters.value = { ...recallHistoryFilters }
  currentPage.value = 1
  void loadData()
}

function resetRecallHistoryFilters() {
  Object.assign(recallHistoryFilters, defaultRecallHistoryFilters)
  applyRecallHistoryFilters()
}

async function confirmRecallReceipt(row: PendingRecallReceipt) {
  const { recallNo, destinationWarehouse } = row
  if (receivingRecallNo.value || !auth.canWrite('picking-delivery')) return
  receivingRecallNo.value = recallNo
  try {
    try {
      await ElMessageBox.confirm(
        `确认召回单「${recallNo}」的实物已到达 ${destinationWarehouse}？`,
        '确认签收入库', { type: 'warning', confirmButtonText: '签收入库', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
    await receiveRecall(recallNo)
    await recallQueue.load()
    notifySuccess(`召回单已签收入库：${recallNo}`)
  } catch (cause) {
    notifyError(apiErrorText(cause, '召回退库签收失败'))
  } finally {
    receivingRecallNo.value = ''
  }
}

function applyRedFlushFilters() {
  appliedRedFlushFilters.value = { ...redFlushFilters }
  currentPage.value = 1
  void loadData()
}

function resetRedFlushFilters() {
  Object.assign(redFlushFilters, { deptCode: '', warehouseId: '' })
  applyRedFlushFilters()
}

async function loadPickingRequisitions(isCurrent = () => type.value === 'delivery') {
  const request = ++pickingRequestVersion
  const result = await fetchPickingRequisitions(Object.fromEntries(
    Object.entries(appliedPickingFilters.value).filter(([, value]) => value)
  ))
  if (!isCurrent() || request !== pickingRequestVersion) return
  pickingRequisitionRows.value = result.rows || []
  pickingTotal.value = pickingRequisitionRows.value.length
  const selectedStillPending = pickingRequisitionRows.value.some(row => String(row.itemId || '') === selectedPickingItemId.value)
  if ((!selectedPickingItemId.value || !selectedStillPending) && pickingRequisitionRows.value[0]) {
    await selectPickingRequisition(pickingRequisitionRows.value[0])
  } else if (!selectedStillPending) {
    selectedPickingRequisitionNo.value = ''
    selectedPickingItemId.value = ''
    selectedPickingItemType.value = ''
    selectedPickingLabels.value = []
    pickingPackageRows.value = []
    pickingUniqueCodeRows.value = []
    pickingLooseRows.value = []
    if (appliedPickingFilters.value.deptCode) form.warehouseName = ''
  } else if (selectedPickingItemId.value) {
    await loadPickingSources()
  }
}

async function applyPickingFilters() {
  if (pickingFilters.dateFrom && pickingFilters.dateTo && pickingFilters.dateFrom > pickingFilters.dateTo) {
    notifyWarning('开始日期不能晚于结束日期')
    return
  }
  appliedPickingFilters.value = { ...pickingFilters }
  pickingPage.value = 1
  selectedPickingItemId.value = ''
  try {
    await loadPickingRequisitions()
  } catch (cause) {
    notifyError(apiErrorText(cause, '待拣配申领单查询失败'))
  }
}

function resetPickingFilters() {
  Object.assign(pickingFilters, { deptCode: '', dateFrom: '', dateTo: '' })
  void applyPickingFilters()
}

/** 申请明细类型展示：唯一码/定数包/散货，与申请单申请的商品明细类型配对 */
function itemTypeLabel(itemType: unknown) {
  switch (String(itemType || '')) {
    case 'high_value':
    case 'unique_code':
      return '唯一码'
    case 'quota_package':
      return '定数包'
    case 'loose':
      return '散货'
    default:
      return '散货'
  }
}

async function selectPickingRequisition(row: Record<string, unknown>) {
  selectedPickingRequisitionNo.value = String(row.requisitionNo || '')
  selectedPickingItemId.value = String(row.itemId || '')
  selectedPickingItemType.value = row.itemType === 'high_value' ? 'unique_code' : String(row.itemType || 'loose')
  form.requisitionNo = selectedPickingRequisitionNo.value
  form.deptName = String(row.deptName || form.deptName)
  if (row.sourceWarehouseName) form.warehouseName = String(row.sourceWarehouseName)
  form.productCode = String(row.productCode || form.productCode)
  form.quantity = Number(row.remainingQty || row.requisitionQty || 1)
  selectedPickingLabels.value = []
  selectedPickingUniqueCodes.value = []
  await loadPickingSources()
}

async function loadPickingSources() {
  const request = ++pickingSourceRequest
  pickingLooseLoading.value = false
  pickingLooseError.value = ''
  if (!selectedPickingRequisitionNo.value || !selectedPickingItemId.value) {
    pickingPackageRows.value = []
    pickingUniqueCodeRows.value = []
    pickingLooseRows.value = []
    return
  }
  const itemType = selectedPickingItemType.value
  const baseParams = {
    requisitionNo: selectedPickingRequisitionNo.value,
    itemId: selectedPickingItemId.value,
    warehouseName: form.warehouseName
  }
  const isCurrent = () => request === pickingSourceRequest && type.value === 'delivery'
    && selectedPickingItemId.value === baseParams.itemId && form.warehouseName === baseParams.warehouseName
  if (itemType === 'unique_code') {
    const result = await fetchPickingUniqueCodes(baseParams)
    if (!isCurrent()) return
    pickingUniqueCodeRows.value = result.rows || []
    pickingPackageRows.value = []
    pickingLooseRows.value = []
    return
  }
  if (itemType === 'quota_package') {
    const labelsResult = await fetchPickingPackageLabels(baseParams)
    if (!isCurrent()) return
    pickingPackageRows.value = labelsResult.rows || []
    pickingLooseRows.value = []
  } else {
    pickingLooseRows.value = []
    pickingPackageRows.value = []
    pickingUniqueCodeRows.value = []
    pickingLooseLoading.value = true
    try {
      const looseResult = await fetchPickingLooseStock(baseParams)
      if (!isCurrent()) return
      pickingLooseRows.value = (looseResult.rows || []).map(row => ({ ...row, pickQty: 0 }))
    } catch (cause) {
      if (isCurrent()) pickingLooseError.value = apiErrorText(cause, '可用散货查询失败')
    } finally {
      if (isCurrent()) pickingLooseLoading.value = false
    }
  }
  pickingUniqueCodeRows.value = []
}

async function loadPickingLabels() {
  await loadPickingSources()
}

function togglePickingLabel(labelNo: string, checked: boolean) {
  const next = new Set(selectedPickingLabels.value)
  if (checked) {
    next.add(labelNo)
  } else {
    next.delete(labelNo)
  }
  selectedPickingLabels.value = [...next]
}

function scanPickingPackage() {
  const code = pickingPackageScanCode.value.trim()
  if (!code) return
  const matched = pickingPackageRows.value.find(row => String(row.labelNo || '').toLowerCase() === code.toLowerCase())
  if (!matched) {
    notifyError(`定数包码 ${code} 不属于当前申请商品或不在所选一级库`, 'warning')
    return
  }
  if (!selectedPickingLabels.value.includes(String(matched.labelNo))) {
    selectedPickingLabels.value = [...selectedPickingLabels.value, String(matched.labelNo)]
  }
  pickingPackageScanCode.value = ''
  notifySuccess(`已扫码选中定数包：${matched.labelNo}`)
}

function togglePickingUniqueCode(code: string, checked: boolean) {
  const next = new Set(selectedPickingUniqueCodes.value)
  if (checked) {
    next.add(code)
  } else {
    next.delete(code)
  }
  selectedPickingUniqueCodes.value = [...next]
}

/** 当前已选拣配总量（定数包按包内基础数量、唯一码按个数、散货按数量） */
const selectedPickingTotal = computed(() => {
  const labels = selectedPickingLabels.value.reduce((sum, labelNo) => {
    const row = pickingPackageRows.value.find(item => String(item.labelNo) === labelNo)
    return sum + Number(row?.packageQuantity || 0)
  }, 0)
  const codes = selectedPickingUniqueCodes.value.length
  const loose = pickingLooseRows.value.reduce((sum, row) => sum + Number(row.pickQty || 0), 0)
  return labels + codes + loose
})

/** 当前选中申领明细的待拣配数量，取自申领行本身，不复用 form.quantity */
const selectedPendingQty = computed(() => {
  const row = pickingRequisitionRows.value.find(
    (item) => String(item.itemId) === selectedPickingItemId.value
  )
  if (!row) return 0
  if (row.itemType === 'quota_package') {
    return Number(row.remainingPackageCount ?? row.remainingQty ?? 0)
  }
  return Number(row.remainingQty ?? 0)
})

async function confirmSelectedPicking() {
  if (!selectedPickingRequisitionNo.value || !selectedPickingItemId.value) {
    notifyError('请先选择科室申领明细', 'warning')
    return
  }
  if (!form.warehouseName) {
    notifyError('请先选择中心库', 'warning')
    return
  }
  const itemType = selectedPickingItemType.value
  const basePayload = {
    requisitionNo: selectedPickingRequisitionNo.value,
    itemId: selectedPickingItemId.value,
    warehouseName: form.warehouseName
  }

  // 唯一码类型：按唯一码/UDI 拣配（配对申请单的唯一码）
  if (itemType === 'unique_code') {
    if (!selectedPickingUniqueCodes.value.length) {
      notifyError('请先勾选唯一码/UDI', 'warning')
      return
    }
    const traceCodeIds = selectedPickingUniqueCodes.value.map(code =>
      Number(pickingUniqueCodeRows.value.find(row => String(row.uniqueCode) === code)?.traceCodeId)
    )
    if (traceCodeIds.some(id => !Number.isSafeInteger(id) || id <= 0)) {
      notifyError('所选唯一码已失效，请刷新货源后重试', 'warning')
      return
    }
    const result = await confirmHighValuePicking({
      itemId: Number(selectedPickingItemId.value),
      traceCodeIds
    })
    notifySuccess(`拣配出库完成：${result.deliveryNo}，唯一码 ${selectedPickingUniqueCodes.value.length} 个`)
    selectedPickingUniqueCodes.value = []
    await loadData()
    return
  }

  // 履约模式必须与已审批申领快照一致。
  const looseQty = pickingLooseRows.value.reduce((sum, row) => sum + Number(row.pickQty || 0), 0)
  if (!selectedPickingLabels.value.length && looseQty <= 0) {
    notifyError(itemType === 'quota_package' ? '请勾选匹配申领快照的定数包标签' : '请填写散货拣配数量', 'warning')
    return
  }
  const done: string[] = []
  if (selectedPickingLabels.value.length) {
    const labelResult = await confirmPicking({
      ...basePayload,
      labelNos: selectedPickingLabels.value
    })
    done.push(`定数包 ${labelResult.labelCount} 个（${labelResult.deliveryNo}）`)
  }
  if (looseQty > 0) {
    const looseResult = await confirmLoosePicking({ ...basePayload, quantity: looseQty })
    done.push(`散货 ${looseQty}（${looseResult.deliveryNo}）`)
  }
  notifySuccess(`拣配出库完成：${done.join('、')}`)
  selectedPickingLabels.value = []
  pickingLooseRows.value = pickingLooseRows.value.map((row) => ({ ...row, pickQty: 0 }))
  await loadData()
}

async function openPackageDetail(labelNo: unknown) {
  const no = String(labelNo || '').trim()
  if (!no) return
  packageDetailOpen.value = true
  packageDetailLoading.value = true
  packageDetail.value = null
  try {
    packageDetail.value = await fetchPickingPackageLabelDetail(no)
  } catch (error) {
    notifyError(apiErrorText(error, '定数包明细加载失败'))
  } finally {
    packageDetailLoading.value = false
  }
}

function closePackageDetail() {
  packageDetailOpen.value = false
  packageDetail.value = null
}

async function changePage(page: number) {
  const totalPages = Math.max(Math.ceil(totalItems.value / Math.max(pageSize.value, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === currentPage.value) return
  currentPage.value = nextPage
  await loadData()
}

async function changePageSize(size: number) {
  if (size === pageSize.value) return
  pageSize.value = size
  currentPage.value = 1
  await loadData()
}

function changePickingPage(page: number) {
  const totalPages = Math.max(Math.ceil(pickingTotal.value / Math.max(pickingSize.value, 1)), 1)
  pickingPage.value = Math.min(Math.max(page, 1), totalPages)
}

function changePickingSize(size: number) {
  pickingSize.value = size
  pickingPage.value = 1
}

/** 科室消耗：按定数包码 / UDI / 唯一码定位商品后再登记消耗 */
async function resolveConsumption() {
  const code = consumptionQueryCode.value.trim()
  if (!code) {
    notifyError('请输入定数包码、UDI 或唯一码', 'warning')
    return
  }
  consumptionResolving.value = true
  consumptionResolved.value = null
  try {
    const result = await resolveConsumptionProduct(code)
    if (code !== consumptionQueryCode.value.trim()) return
    consumptionResolved.value = result
    form.productCode = String(result.productCode || '')
    if (result.sourceType === 'package') {
      form.quantity = Number(result.packageQuantity || 0)
    } else if (Number(result.highValue) === 1) {
      form.quantity = 1
    }
    if (result.warehouseName) {
      const warehouse = warehouseChoices.value.find(item => result.warehouseId
        ? item.warehouseId === Number(result.warehouseId) : item.warehouseName === result.warehouseName)
      if (warehouse) {
        form.warehouseName = warehouse.warehouseName
        if (warehouse.warehouseId) form.warehouseId = warehouse.warehouseId
      }
    }
    notifySuccess(`已定位商品：${result.productCode} / ${result.productName}（${result.sourceType === 'package' ? '定数包码' : 'UDI/唯一码'}）`)
  } catch (error) {
    notifyError(apiErrorText(error, '查询码解析失败'))
  } finally {
    consumptionResolving.value = false
  }
}

/** 召回隔离：选择商品后加载该商品在该库房的可用批次 */
async function loadRecallBatches() {
  recallBatches.value = []
  recallBatchId.value = null
  if (!form.productCode || !form.warehouseName) return
  recallBatchesLoading.value = true
  try {
    const result = await fetchRecallBatches(form.productCode, form.warehouseName)
    recallBatches.value = result.rows || []
    recallBatchId.value = recallBatches.value[0] ? Number(recallBatches.value[0].batchId) : null
  } catch (error) {
    recallBatches.value = []
    notifyError(apiErrorText(error, '召回批次加载失败'))
  } finally {
    recallBatchesLoading.value = false
  }
}

async function loadRecallInventory() {
  recallInventoryRows.value = []
  if (!isRecallPage.value || !form.productCode) return
  if (recallScope.value !== 'all' && !form.warehouseName) return
  recallBatchesLoading.value = true
  try {
    const result = await fetchRecallInventory({
      productCode: form.productCode,
      scope: recallScope.value,
      businessType: recallBusinessType.value,
      warehouseName: recallScope.value === 'all' ? '' : form.warehouseName
    })
    recallInventoryRows.value = result.rows || []
  } catch (error) {
    notifyError(apiErrorText(error, '召回范围库存加载失败'))
  } finally {
    recallBatchesLoading.value = false
  }
}

async function applyRecallScope() {
  recallBatchNo.value = ''
  if (recallScope.value === 'all') {
    form.warehouseName = ''
    await loadRecallInventory()
    return
  }
  if (recallScope.value === 'primary') {
    form.warehouseName = primaryWarehouses.value[0]?.warehouseName || ''
    await loadRecallInventory()
    return
  }
  await loadLinkedWarehouses()
  const typePattern = recallScope.value === 'secondary' ? /二级/ : recallScope.value === 'tertiary' ? /三级/ : /一级|中心/
  const matched = linkedWarehouses.value.find(item => typePattern.test(String(item.type || '')))
  form.warehouseName = matched?.name || ''
  await loadRecallInventory()
}

async function openRecallDialog() {
  recallDialogError.value = ''
  recallScope.value = 'secondary'
  recallBusinessType.value = 'recall'
  form.reason = ''
  recallDialogOpen.value = true
  await applyRecallScope()
}

async function submitRecallDialog() {
  if (recallSubmitting.value) return
  recallSubmitting.value = true
  try {
    // runAction 内部已统一兜底错误提示；失败时保留弹窗与已填内容
    if (await runAction('recall')) {
      recallDialogOpen.value = false
    } else {
      recallDialogError.value = '召回隔离登记失败，请重试'
    }
  } finally {
    recallSubmitting.value = false
  }
}

/** 从接口异常中提取后端返回的业务文案（兼容 axios 与裸 Error） */
function apiErrorText(error: unknown, fallback: string) {
  const data = (error as { response?: { data?: { message?: string } } }).response?.data
  return formatApiErrorText(data?.message || (error instanceof Error ? error.message : ''), fallback)
}

/* =====================================================================
   统一反馈出口
   ---------------------------------------------------------------------
   失败 / 警告用通用消息接口（Element Plus ElMessage）弹出，
   不占用页面版面、不挤压表格可视区域；
   成功也统一弹出，保证操作后列表已刷新，用户不需要再找页面内横幅。
   与 QuotaPackageView 的反馈策略保持一致。
   ===================================================================== */
function notifyError(text: string, tone: 'error' | 'warning' = 'error') {
  actionError.value = ''
  if (tone === 'warning') {
    notifyWarning(text, { duration: 4000, showClose: true })
    return
  }
  notifyErrorShared(text, { duration: 4000, showClose: true })
}

function notifySuccess(text: string) {
  actionError.value = ''
  ElMessage({ message: text, type: 'success', duration: 3000, showClose: true })
}

/**
 * 所有业务动作的统一入口，返回是否成功。
 * 任何后端校验失败（如库存不足）都用 ElMessage 提示，
 * 不能变成 unhandled rejection 让用户点了按钮"没反应"。
 */
async function runAction(action: string, row?: Record<string, unknown>): Promise<boolean> {
  const fallback = ACTION_FAILURE_TEXT[action] || '操作失败，请重试'
  actionError.value = ''
  if (type.value !== 'delivery') {
    try {
      await performAction(action, row)
      return true
    } catch (error) {
      notifyError(apiErrorText(error, fallback))
      return false
    }
  }
  if (deliverySubmitting.value) return false
  deliverySubmitting.value = true
  try {
    await performAction(action, row)
    return true
  } catch (error) {
    notifyError(apiErrorText(error, fallback))
    return false
  } finally {
    deliverySubmitting.value = false
  }
}

async function confirmConsumptionReversal(row: Record<string, unknown>) {
  const sourceNo = String(row.bizNo || '').trim()
  if (!sourceNo || row.status !== 'confirmed' || reversingConsumption.value
    || !auth.hasPermission('reverse-consumption:write')) return
  reversingConsumption.value = true
  try {
    try {
      await ElMessageBox.confirm(
        `确认冲销消耗单「${sourceNo}」？冲销后将恢复原批次库存，并撤销该单待确认的结算明细。`,
        '确认反消耗', { type: 'warning', confirmButtonText: '确认冲销', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
    await runAction('reverse', row)
  } finally {
    reversingConsumption.value = false
  }
}

async function performAction(action: string, row?: Record<string, unknown>) {
  let result: Record<string, unknown> = {}
  if (action === 'shortage') {
    result = await generateShortage(shortagePayload())
    notifySuccess(`补货任务已生成：${result.taskNo}，周期 ${result.periodDays} 天，建议 ${result.formulaReplenishQty}，实际 ${result.replenishQty}`)
  }
  if (action === 'requisition') {
    result = await createRequisition(form)
    form.requisitionNo = String(result.requisitionNo || '')
    notifySuccess(`科室申领已提交待审批：${result.requisitionNo}`)
  }
  if (action === 'approveRequisition' || action === 'rejectRequisition') {
    result = await processRequisition(String(row?.bizNo || ''), action === 'approveRequisition' ? 'approve' : 'reject')
    notifySuccess(action === 'approveRequisition' ? `申领已审批：${result.requisitionNo}` : `申领已驳回：${result.requisitionNo}`)
  }
  if (action === 'delivery') {
    await confirmSelectedPicking()
    return
  }
  if (action === 'consumption') {
    if (!Number.isInteger(Number(form.quantity)) || Number(form.quantity) <= 0) throw new Error('消耗数量必须为正整数，不支持小数')
    if (consumptionQueryCode.value.trim() && !consumptionResolved.value) throw new Error('请先查询扫码结果，再登记消耗')
    if (consumptionResolved.value?.sourceType === 'package') {
      result = await consumeQuotaPackageByCode({
        code: consumptionQueryCode.value.trim(),
        deptName: form.deptName || undefined
      })
    } else {
      result = await createConsumption({
        ...form,
        uniqueCode: Number(consumptionResolved.value?.highValue) === 1 ? consumptionResolved.value?.uniqueCode : undefined
      })
    }
    const settlementNos = Array.isArray(result.settlementNos) ? result.settlementNos : []
    notifySuccess(`科室消耗已登记：${result.consumptionNo}${settlementNos.length ? `，结算单：${settlementNos.join('、')}` : ''}`)
    consumptionQueryCode.value = ''
    consumptionResolved.value = null
  }
  if (action === 'reverse') {
    const sourceNo = String(row?.bizNo || '').trim()
    if (!sourceNo) throw new Error('请选择要反消耗的原消耗单')
    result = await reverseConsumption(sourceNo)
    notifySuccess(`反消耗红冲已生成：${result.flushNo}`)
  }
  if (action === 'pda') {
    result = await uploadPdaOffline(form)
    notifySuccess(`PDA 离线记录已补传：${result.recordNo}`)
  }
  if (action === 'cold') {
    result = await createColdChainException(form)
    notifySuccess(`冷链异常已登记：${result.eventNo}`)
  }
  if (action === 'recall') {
    result = await createRecall({
      businessType: recallBusinessType.value,
      scope: recallScope.value,
      deptName: recallScope.value === 'all' ? undefined : form.deptName,
      warehouseName: recallScope.value === 'all' ? undefined : form.warehouseName,
      productCode: form.productCode,
      batchNo: recallBatchNo.value,
      reason: form.reason
    })
    notifySuccess(recallBusinessType.value === 'isolate'
      ? `隔离完成：${result.recallNo}，${result.affectedQty} 件已在原库房隔离`
      : `召回退库已出库：${result.recallNo}，${result.affectedQty} 件待 ${result.primaryWarehouseName} 签收入库`)
  }
  if (action === 'bindPatient') {
    const uniqueCode = form.uniqueCodes.split(/[,，\s]+/).find(Boolean) || ''
    result = await bindHighValuePatient({ ...form, uniqueCode })
    notifySuccess(`唯一码 ${result.uniqueCode} 已绑定患者 ${result.patientNo}`)
  }
  if (action === 'billingCallback') {
    const uniqueCode = form.uniqueCodes.split(/[,，\s]+/).find(Boolean) || ''
    result = await receiveHighValueBillingCallback({
      ...form,
      uniqueCode,
      quantity: 1,
      externalChargeNo: form.externalChargeNo
    })
    notifySuccess(`高值耗材已按唯一码计费扣减：${result.chargeNo}`)
  }
  await loadData()
}

async function openSmartAnalysis() {
  // 支持全院分析：未选择科室时按全院科室库房出库核算
  analysisError.value = ''
  await loadLinkedWarehouses()
  const linkedWarehouseName = linkedWarehouses.value.some(item => item.name === form.warehouseName)
    ? form.warehouseName
    : linkedWarehouses.value[0]?.name || ''
  form.warehouseName = linkedWarehouseName
  analysisRows.value = []
  analysisMeta.value = null
  analysisLoading.value = true
  analysisDialogOpen.value = true
  try {
    const result = await smartReplenishmentAnalysis({
      deptName: form.deptName,
      warehouseName: linkedWarehouseName || undefined,
      selectedPeriodDays: effectiveReplenishmentDays.value
    })
    analysisMeta.value = result
    analysisRows.value = Array.isArray(result.rows) ? result.rows as Record<string, unknown>[] : []
    form.warehouseName = String(result.warehouseName || form.warehouseName || '')
  } catch (error) {
    analysisRows.value = []
    analysisMeta.value = null
    analysisError.value = apiErrorText(error, '智能补货分析失败')
    notifyError(analysisError.value)
  } finally {
    analysisLoading.value = false
  }
}

function updateAnalysisQty(row: Record<string, unknown>, event: Event) {
  const input = event.target as HTMLInputElement
  const value = Number(input.value)
  row.recommendedQty = Number.isFinite(value) && value >= 0 ? value : 0
}

async function createTaskFromAnalysis(row: Record<string, unknown>) {
  form.deptName = String(row.deptName || form.deptName || '')
  form.warehouseName = String(row.warehouseName || form.warehouseName || '')
  form.productCode = String(row.productCode || '')
  form.currentQty = Number(row.currentQty || 0)
  form.replenishQty = Number(row.recommendedQty || 0)
  form.replenishmentDays = Number(analysisMeta.value?.selectedPeriodDays || 7)
  // runAction 内部已统一兜底错误提示；失败时保留分析弹窗
  if (await runAction('shortage')) analysisDialogOpen.value = false
}

onMounted(loadData)
watch([type, isRecallPage], () => {
  deliveryTab.value = 'picking'
  currentPage.value = 1
  autoSmartAnalysisHandled.value = false
  loadData()
})
/**
 * 结算明细加载失败 → 通用消息接口抛出（Element Plus 消息弹层），
 * 不再在筛选栏下方渲染一条内联错误条。
 */
watch(settlementLoadError, (message) => {
  if (!message) return
  notifyErrorShared(message)
})
watch(() => form.deptName, () => {
  if (['shortage', 'requisition', 'consumption'].includes(type.value)) {
    loadLinkedWarehouses()
  }
  if (type.value === 'risk') {
    if (isRecallPage.value) void applyRecallScope()
    else loadRecallBatches()
  }
})
watch(() => form.deptCode, () => {
  if (type.value !== 'consumption') return
  form.deptName = options.value.departments.find(item => item.deptCode === form.deptCode)?.deptName || ''
  loadLinkedWarehouses()
})
watch(() => form.warehouseId, () => {
  if (type.value === 'consumption') form.warehouseName = linkedWarehouses.value.find(item => item.warehouseId === form.warehouseId)?.name || ''
})
watch([selectedPickingItemId, () => form.warehouseName], () => { pickingDetailPage.value = 1 })
watch(pickingDetailLastPage, lastPage => {
  pickingDetailPage.value = Math.min(pickingDetailPage.value, lastPage)
})
watch(() => form.warehouseName, () => {
  if (type.value === 'delivery') {
    selectedPickingLabels.value = []
    loadPickingLabels()
  }
  if (type.value === 'risk') {
    if (isRecallPage.value) loadRecallInventory()
    else loadRecallBatches()
  }
})
watch(() => form.productCode, () => {
  if (type.value === 'risk') {
    if (isRecallPage.value) loadRecallInventory()
    else loadRecallBatches()
  }
})
watch(recallBusinessType, () => {
  if (!isRecallPage.value) return
  if (recallBusinessType.value === 'recall' && recallScope.value === 'primary') {
    recallScope.value = 'secondary'
    void applyRecallScope()
  } else {
    void loadRecallInventory()
  }
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCREPLENISHMENTANALYSIS_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'product', label: '商品', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'deptWarehouse', label: '科室 / 库房', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'out5', label: '5 天出库', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'out7', label: '7 天出库', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'out15', label: '15 天出库', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'out30', label: '30 天出库', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'currentStock', label: '当前库存', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'suggestedReplenishment', label: '建议补货', width: 150, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'actions', label: '操作', width: 140, visible: true, fill: true, minWidth: 120, maxWidth: 220 }
]

const ocReplenishmentAnalysisCols = useListColumns({
  storageKey: 'spd-oc-replenishment-analysis-cols-v1',
  defaults: OCREPLENISHMENTANALYSIS_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocReplenishmentAnalysisTableRef = ocReplenishmentAnalysisCols.tableRef
const fillHeightOcReplenishmentAnalysis = useFillHeightTable({ tableRef: ocReplenishmentAnalysisTableRef })
const isOcReplenishmentAnalysisColVisible = (key: string) => ocReplenishmentAnalysisCols.isColumnVisible(key)
const ocReplenishmentAnalysisColBinding = (key: string) => ocReplenishmentAnalysisCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const ocReplenishmentAnalysisActionFix = useActionColumnFix()
const ocReplenishmentAnalysisActionFixed = computed(() => ocReplenishmentAnalysisActionFix.actionFixedBinding.value)

watch(
  [ocReplenishmentAnalysisTableRef],
  () => {
    ocReplenishmentAnalysisActionFix.observe(ocReplenishmentAnalysisTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocReplenishmentAnalysisColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocReplenishmentAnalysisColSettingsPopRef = ref<HTMLElement | null>(null)
const ocReplenishmentAnalysisColSettingsPop = useColSettingsPopover({
  open: ocReplenishmentAnalysisCols.settingsOpen,
  anchorRef: ocReplenishmentAnalysisColSettingsAnchorRef,
  popRef: ocReplenishmentAnalysisColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCRECALLHISTORY_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'bizNo', label: '单据号', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 230 },
  { key: 'businessType', label: '类型', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'productCode', label: '商品编码', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'productName', label: '商品名称', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'sourceDept', label: '来源科室', width: 160, visible: true, weight: 4, minWidth: 110, maxWidth: 260 },
  { key: 'recordWarehouse', label: '记录库房', width: 150, visible: true, weight: 3, minWidth: 110, maxWidth: 240 },
  { key: 'quantity', label: '数量', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'status', label: '状态', width: 120, visible: true, weight: 2, minWidth: 80, maxWidth: 160 },
  { key: 'createTime', label: '登记时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'receivedTime', label: '签收时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210, fill: true }
]

const ocRecallHistoryCols = useListColumns({
  storageKey: 'spd-oc-recall-history-cols-v1',
  defaults: OCRECALLHISTORY_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocRecallHistoryTableRef = ocRecallHistoryCols.tableRef
const fillHeightOcRecallHistory = useFillHeightTable({ tableRef: ocRecallHistoryTableRef })
const isOcRecallHistoryColVisible = (key: string) => ocRecallHistoryCols.isColumnVisible(key)
const ocRecallHistoryColBinding = (key: string) => ocRecallHistoryCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocRecallHistoryColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocRecallHistoryColSettingsPopRef = ref<HTMLElement | null>(null)
const ocRecallHistoryColSettingsPop = useColSettingsPopover({
  open: ocRecallHistoryCols.settingsOpen,
  anchorRef: ocRecallHistoryColSettingsAnchorRef,
  popRef: ocRecallHistoryColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCREDFLUSH_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'bizNo', label: '红冲单号', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 230 },
  { key: 'sourceNo', label: '原消耗单号', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 230 },
  { key: 'deptName', label: '科室', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 3, minWidth: 110, maxWidth: 230 },
  { key: 'flushType', label: '红冲类型', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'status', label: '状态', width: 120, visible: true, weight: 2, minWidth: 80, maxWidth: 160 },
  { key: 'createTime', label: '红冲时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'remark', label: '备注', width: 220, visible: true, weight: 6, minWidth: 140, maxWidth: 380, fill: true }
]

const ocRedFlushCols = useListColumns({
  storageKey: 'spd-oc-red-flush-cols-v1',
  defaults: OCREDFLUSH_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocRedFlushTableRef = ocRedFlushCols.tableRef
const fillHeightOcRedFlush = useFillHeightTable({ tableRef: ocRedFlushTableRef })
const isOcRedFlushColVisible = (key: string) => ocRedFlushCols.isColumnVisible(key)
const ocRedFlushColBinding = (key: string) => ocRedFlushCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocRedFlushColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocRedFlushColSettingsPopRef = ref<HTMLElement | null>(null)
const ocRedFlushColSettingsPop = useColSettingsPopover({
  open: ocRedFlushCols.settingsOpen,
  anchorRef: ocRedFlushColSettingsAnchorRef,
  popRef: ocRedFlushColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCSETTLEMENT_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'settlementNo', label: '结算单', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'settlementOwner', label: '结算归属', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'requisitionType', label: '申领类型', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'productInfo', label: '商品信息', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'specUnit', label: '规格 / 单位', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'manufacturer', label: '厂家 / 注册证', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'supplierName', label: '结算供应商', width: 170, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'goodsType', label: '货型', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'basePrice', label: '基本单价', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'baseQty', label: '基本数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'packageCount', label: '包数（去重）', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 200 },
  { key: 'looseQty', label: '散货数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'highValueQty', label: '高值数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'amount', label: '金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'medicalInsurance', label: '带量 / 医保', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'traceCode', label: '追溯码', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'patientDept', label: '患者 / 财务科室', width: 170, visible: true, weight: 4, minWidth: 120, maxWidth: 260 },
  { key: 'settlementInfo', label: '结算信息', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 300 },
  { key: 'status', label: '状态', width: 120, visible: true, weight: 2, minWidth: 80, maxWidth: 160, fill: true }
]

const ocSettlementCols = useListColumns({
  storageKey: 'spd-oc-settlement-cols-v1',
  defaults: OCSETTLEMENT_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocSettlementTableRef = ocSettlementCols.tableRef
const fillHeightOcSettlement = useFillHeightTable({ tableRef: ocSettlementTableRef })
const isOcSettlementColVisible = (key: string) => ocSettlementCols.isColumnVisible(key)
const ocSettlementColBinding = (key: string) => ocSettlementCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocSettlementColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocSettlementColSettingsPopRef = ref<HTMLElement | null>(null)
const ocSettlementColSettingsPop = useColSettingsPopover({
  open: ocSettlementCols.settingsOpen,
  anchorRef: ocSettlementColSettingsAnchorRef,
  popRef: ocSettlementColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCRECALLQUEUE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'recallNo', label: '召回单号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 250 },
  { key: 'sourceWarehouse', label: '来源二级库', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 250 },
  { key: 'destinationWarehouse', label: '接收中心库', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 250 },
  { key: 'quantity', label: '待收数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'createTime', label: '登记时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'actions', label: '操作', width: 140, visible: true, fill: true, minWidth: 120, maxWidth: 220 }
]

const ocRecallQueueCols = useListColumns({
  storageKey: 'spd-oc-recall-queue-cols-v1',
  defaults: OCRECALLQUEUE_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocRecallQueueTableRef = ocRecallQueueCols.tableRef
const fillHeightOcRecallQueue = useFillHeightTable({ tableRef: ocRecallQueueTableRef })
const isOcRecallQueueColVisible = (key: string) => ocRecallQueueCols.isColumnVisible(key)
const ocRecallQueueColBinding = (key: string) => ocRecallQueueCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const ocRecallQueueActionFix = useActionColumnFix()
const ocRecallQueueActionFixed = computed(() => ocRecallQueueActionFix.actionFixedBinding.value)

watch(
  [ocRecallQueueTableRef],
  () => {
    ocRecallQueueActionFix.observe(ocRecallQueueTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocRecallQueueColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocRecallQueueColSettingsPopRef = ref<HTMLElement | null>(null)
const ocRecallQueueColSettingsPop = useColSettingsPopover({
  open: ocRecallQueueCols.settingsOpen,
  anchorRef: ocRecallQueueColSettingsAnchorRef,
  popRef: ocRecallQueueColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCRETURNQUEUE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'returnNo', label: '退库单号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 250 },
  { key: 'sourceWarehouse', label: '二级库', width: 150, visible: true, weight: 4, minWidth: 120, maxWidth: 240 },
  { key: 'destinationWarehouse', label: '接收中心库', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 250 },
  { key: 'quantity', label: '基本数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'createTime', label: '登记时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'actions', label: '操作', width: 140, visible: true, fill: true, minWidth: 120, maxWidth: 220 }
]

const ocReturnQueueCols = useListColumns({
  storageKey: 'spd-oc-return-queue-cols-v1',
  defaults: OCRETURNQUEUE_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocReturnQueueTableRef = ocReturnQueueCols.tableRef
const fillHeightOcReturnQueue = useFillHeightTable({ tableRef: ocReturnQueueTableRef })
const isOcReturnQueueColVisible = (key: string) => ocReturnQueueCols.isColumnVisible(key)
const ocReturnQueueColBinding = (key: string) => ocReturnQueueCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const ocReturnQueueActionFix = useActionColumnFix()
const ocReturnQueueActionFixed = computed(() => ocReturnQueueActionFix.actionFixedBinding.value)

watch(
  [ocReturnQueueTableRef],
  () => {
    ocReturnQueueActionFix.observe(ocReturnQueueTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocReturnQueueColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocReturnQueueColSettingsPopRef = ref<HTMLElement | null>(null)
const ocReturnQueueColSettingsPop = useColSettingsPopover({
  open: ocReturnQueueCols.settingsOpen,
  anchorRef: ocReturnQueueColSettingsAnchorRef,
  popRef: ocReturnQueueColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCPICKINGLOOSE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'productName', label: '商品名称', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'systemBatch', label: '系统批次', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'availableQty', label: '可用数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'pickedQty', label: '拣配数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160, fill: true }
]

const ocPickingLooseCols = useListColumns({
  storageKey: 'spd-oc-picking-loose-cols-v1',
  defaults: OCPICKINGLOOSE_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocPickingLooseTableRef = ocPickingLooseCols.tableRef
const fillHeightOcPickingLoose = useFillHeightTable({ tableRef: ocPickingLooseTableRef })
const isOcPickingLooseColVisible = (key: string) => ocPickingLooseCols.isColumnVisible(key)
const ocPickingLooseColBinding = (key: string) => ocPickingLooseCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocPickingLooseColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocPickingLooseColSettingsPopRef = ref<HTMLElement | null>(null)
const ocPickingLooseColSettingsPop = useColSettingsPopover({
  open: ocPickingLooseCols.settingsOpen,
  anchorRef: ocPickingLooseColSettingsAnchorRef,
  popRef: ocPickingLooseColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCPICKINGQUOTAPACKAGE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'selection', label: '', width: 60, visible: true, weight: 1, minWidth: 48, maxWidth: 80 },
  { key: 'labelNo', label: '定数包码', width: 180, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'product', label: '商品', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'packageQuantity', label: '包内数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'createTime', label: '生成时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'updateTime', label: '修改时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210, fill: true }
]

const ocPickingQuotaPackageCols = useListColumns({
  storageKey: 'spd-oc-picking-quota-package-cols-v1',
  defaults: OCPICKINGQUOTAPACKAGE_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocPickingQuotaPackageTableRef = ocPickingQuotaPackageCols.tableRef
const fillHeightOcPickingQuotaPackage = useFillHeightTable({ tableRef: ocPickingQuotaPackageTableRef })
const isOcPickingQuotaPackageColVisible = (key: string) => ocPickingQuotaPackageCols.isColumnVisible(key)
const ocPickingQuotaPackageColBinding = (key: string) => ocPickingQuotaPackageCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocPickingQuotaPackageColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocPickingQuotaPackageColSettingsPopRef = ref<HTMLElement | null>(null)
const ocPickingQuotaPackageColSettingsPop = useColSettingsPopover({
  open: ocPickingQuotaPackageCols.settingsOpen,
  anchorRef: ocPickingQuotaPackageColSettingsAnchorRef,
  popRef: ocPickingQuotaPackageColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCPICKINGUNIQUECODE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'selection', label: '', width: 60, visible: true, weight: 1, minWidth: 48, maxWidth: 80 },
  { key: 'uniqueCode', label: '唯一码', width: 200, visible: true, weight: 6, minWidth: 140, maxWidth: 320 },
  { key: 'udiCode', label: 'UDI', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 300 },
  { key: 'batchNo', label: '系统批次', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'expireDate', label: '有效期', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200, fill: true }
]

const ocPickingUniqueCodeCols = useListColumns({
  storageKey: 'spd-oc-picking-unique-code-cols-v1',
  defaults: OCPICKINGUNIQUECODE_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocPickingUniqueCodeTableRef = ocPickingUniqueCodeCols.tableRef
const fillHeightOcPickingUniqueCode = useFillHeightTable({ tableRef: ocPickingUniqueCodeTableRef })
const isOcPickingUniqueCodeColVisible = (key: string) => ocPickingUniqueCodeCols.isColumnVisible(key)
const ocPickingUniqueCodeColBinding = (key: string) => ocPickingUniqueCodeCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocPickingUniqueCodeColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocPickingUniqueCodeColSettingsPopRef = ref<HTMLElement | null>(null)
const ocPickingUniqueCodeColSettingsPop = useColSettingsPopover({
  open: ocPickingUniqueCodeCols.settingsOpen,
  anchorRef: ocPickingUniqueCodeColSettingsAnchorRef,
  popRef: ocPickingUniqueCodeColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCPICKINGREQUISITION_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'requisitionNo', label: '申领单号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 250 },
  { key: 'requisitionCreateTime', label: '申领单创建时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'requisitionType', label: '申领类型', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'partialPickingTime', label: '部分拣配完成时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'deptName', label: '科室', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 220 },
  { key: 'product', label: '商品', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'applyType', label: '申请类型', width: 150, visible: true, weight: 3, minWidth: 110, maxWidth: 240 },
  { key: 'requisitionQty', label: '申领数量', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'pickedQty', label: '已拣配', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'pendingQty', label: '待拣配', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'actions', label: '操作', width: 140, visible: true, fill: true, minWidth: 120, maxWidth: 220 }
]

const ocPickingRequisitionCols = useListColumns({
  storageKey: 'spd-oc-picking-requisition-cols-v1',
  defaults: OCPICKINGREQUISITION_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocPickingRequisitionTableRef = ocPickingRequisitionCols.tableRef
const fillHeightOcPickingRequisition = useFillHeightTable({ tableRef: ocPickingRequisitionTableRef })
const isOcPickingRequisitionColVisible = (key: string) => ocPickingRequisitionCols.isColumnVisible(key)
const ocPickingRequisitionColBinding = (key: string) => ocPickingRequisitionCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const ocPickingRequisitionActionFix = useActionColumnFix()
const ocPickingRequisitionActionFixed = computed(() => ocPickingRequisitionActionFix.actionFixedBinding.value)

watch(
  [ocPickingRequisitionTableRef],
  () => {
    ocPickingRequisitionActionFix.observe(ocPickingRequisitionTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocPickingRequisitionColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocPickingRequisitionColSettingsPopRef = ref<HTMLElement | null>(null)
const ocPickingRequisitionColSettingsPop = useColSettingsPopover({
  open: ocPickingRequisitionCols.settingsOpen,
  anchorRef: ocPickingRequisitionColSettingsAnchorRef,
  popRef: ocPickingRequisitionColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重铺满内容区，表格 100% 铺满；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const OCRECALLINVENTORY_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'warehouseType', label: '库房级别', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'warehouseName', label: '库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productName', label: '商品', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'spec', label: '规格', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'manufacturer', label: '厂家', width: 170, visible: true, weight: 5, minWidth: 120, maxWidth: 280 },
  { key: 'unit', label: '单位', width: 90, visible: true, weight: 1, minWidth: 70, maxWidth: 120 },
  { key: 'unitPrice', label: '单价', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'systemBatch', label: '系统批号', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productionBatch', label: '生产批号', width: 140, visible: true, weight: 4, minWidth: 110, maxWidth: 230 },
  { key: 'looseQty', label: '散货', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'packageQty', label: '定数包折散', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'totalQty', label: '合计', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160, fill: true }
]

const ocRecallInventoryCols = useListColumns({
  storageKey: 'spd-oc-recall-inventory-cols-v1',
  defaults: OCRECALLINVENTORY_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocRecallInventoryTableRef = ocRecallInventoryCols.tableRef
const fillHeightOcRecallInventory = useFillHeightTable({ tableRef: ocRecallInventoryTableRef })
const isOcRecallInventoryColVisible = (key: string) => ocRecallInventoryCols.isColumnVisible(key)
const ocRecallInventoryColBinding = (key: string) => ocRecallInventoryCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocRecallInventoryColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocRecallInventoryColSettingsPopRef = ref<HTMLElement | null>(null)
const ocRecallInventoryColSettingsPop = useColSettingsPopover({
  open: ocRecallInventoryCols.settingsOpen,
  anchorRef: ocRecallInventoryColSettingsAnchorRef,
  popRef: ocRecallInventoryColSettingsPopRef
})

/* ===== 统一列表：业务单据（消耗 / 申领 / 缺货通用表头） ===== */
const OCCLOSUREDOC_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'bizNo', label: '单据号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 250 },
  { key: 'requisitionType', label: '申领类型', width: 120, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'deptWarehouse', label: '科室 / 库房', width: 180, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'productCode', label: '商品编码', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'productName', label: '商品名称', width: 190, visible: true, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'quantity', label: '数量 / 金额', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'status', label: '状态', width: 120, visible: true, weight: 2, minWidth: 80, maxWidth: 160 },
  { key: 'createTime', label: '时间', width: 170, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'actions', label: '操作', width: 140, visible: true, fill: true, minWidth: 120, maxWidth: 220 }
]

const ocClosureDocCols = useListColumns({
  storageKey: 'spd-oc-closure-doc-cols-v1',
  defaults: OCCLOSUREDOC_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ocClosureDocTableRef = ocClosureDocCols.tableRef
const fillHeightOcClosureDoc = useFillHeightTable({ tableRef: ocClosureDocTableRef })
const isOcClosureDocColVisible = (key: string) => ocClosureDocCols.isColumnVisible(key)
const ocClosureDocColBinding = (key: string) => ocClosureDocCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const ocClosureDocActionFix = useActionColumnFix()
const ocClosureDocActionFixed = computed(() => ocClosureDocActionFix.actionFixedBinding.value)

watch(
  [ocClosureDocTableRef],
  () => {
    ocClosureDocActionFix.observe(ocClosureDocTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ocClosureDocColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ocClosureDocColSettingsPopRef = ref<HTMLElement | null>(null)
const ocClosureDocColSettingsPop = useColSettingsPopover({
  open: ocClosureDocCols.settingsOpen,
  anchorRef: ocClosureDocColSettingsAnchorRef,
  popRef: ocClosureDocColSettingsPopRef
})

</script>

<template><section class="purchase-page closure-page spd-unified-list" :class="{ 'consumption-saas-page': type === 'consumption', 'delivery-acceptance-page': type === 'delivery', 'shortage-task-page': type === 'shortage', 'pda-offline-page': type === 'pda', 'recall-isolation-page': isRecallPage, 'finance-settlement-page': type === 'settlement' || type === 'red-flush', 'high-value-operation-page': type === 'high-value' }">
    <!-- 补货任务 / 拣配配送：页头复用 MdPageHeader，与采购/库存等页面完全一致 -->
    <MdPageHeader
      v-if="type === 'shortage'"
      crumb="供应链业务"
      :title="title"
      :loading="loading"
      @refresh="loadData"
    />

    <MdPageHeader
      v-else-if="type === 'delivery'"
      crumb="供应链业务"
      :title="title"
      :loading="deliveryTab === 'returns' ? returnQueue.loading : deliveryTab === 'recalls' ? recallQueue.loading : loading"
      @refresh="refreshDeliveryTab"
    />

    <MdPageHeader
      v-else-if="type === 'consumption'"
      crumb="供应链业务"
      :title="title"
      :loading="loading"
      @refresh="loadData"
    />

    <template v-else-if="type === 'pda' || isRecallPage">
      <MdPageHeader
        crumb="供应链业务"
        :title="title"
        :loading="loading"
        @refresh="loadData"
      >
        <template #actions>
          <SpdButton label="离线补传" tone="primary" v-if="type === 'pda'" @click="runAction('pda')">
          <template #icon><Smartphone :size="16" /></template>
        </SpdButton>
          <SpdButton label="召回隔离" tone="primary" v-else @click="openRecallDialog">
          <template #icon><RotateCcw :size="16" /></template>
        </SpdButton>
        </template>
      </MdPageHeader>
    </template>

    <MdPageHeader
      v-else-if="type === 'high-value'"
      crumb="供应链业务"
      :title="title"
      :loading="loading"
      @refresh="loadData"
    />

    <MdPageHeader
      v-else-if="type === 'settlement'"
      crumb="结算与财务"
      :title="title"
      :loading="loading"
      @refresh="loadData"
    />

    <MdPageHeader
      v-else-if="type === 'red-flush'"
      crumb="结算与财务"
      :title="title"
      :loading="loading"
      @refresh="loadData"
    />

    <!-- 顶部统计卡与重复操作按钮已移除，操作入口统一收敛到页头动作位；
         结算对账 / 红冲管理属于结算与财务类页面，无关业务统计卡也不展示；
         高值耗材计费操作同样不展示通用运营 KPI。 -->

    <el-dialog v-model="recallDialogOpen" title="登记召回 / 隔离" width="min(960px, 94vw)" append-to-body :close-on-click-modal="!recallSubmitting">
      <form @submit.prevent="submitRecallDialog">
        <div class="supplier-form-grid">
          <label><span>业务类型</span><select v-model="recallBusinessType"><option value="recall">召回退库（待中心库签收）</option><option value="isolate">隔离（原库房隔离）</option></select></label>
          <label><span>库存范围</span><select v-model="recallScope" @change="applyRecallScope"><option value="all">{{ recallBusinessType === 'recall' ? '全部非中心库' : '全部库房' }}</option><option v-if="recallBusinessType === 'isolate'" value="primary">一级库</option><option value="secondary">二级库</option><option value="tertiary">三级库</option></select></label>
          <label><span>科室</span><select v-model="form.deptName" :disabled="!['secondary', 'tertiary'].includes(recallScope)" :required="['secondary', 'tertiary'].includes(recallScope)"><option value="">请选择科室</option><option v-for="item in options.departments" :key="item.deptCode" :value="item.deptName">{{ item.deptName }}</option></select></label>
          <label><span>库房</span><select v-model="form.warehouseName" :disabled="recallScope === 'all'" :required="recallScope !== 'all'"><option value="">请选择库房</option><option v-for="item in warehouseChoices" :key="item.warehouseName" :value="item.warehouseName">{{ item.warehouseName }}</option></select></label>
          <label class="wide"><span>商品</span><select v-model="form.productCode" required><option value="">请选择商品</option><option v-for="item in options.products" :key="item.productCode" :value="item.productCode">{{ item.productName }}（{{ item.productCode }}）</option></select></label>
          <label><span>批号</span><input v-model.trim="recallBatchNo" required placeholder="系统批号或生产批号" /></label>
          <label><span>原因</span><input v-model.trim="form.reason" required placeholder="填写召回或隔离原因" /></label>
        </div>
        <StatusMessage :message="recallDialogError" tone="error" role="alert" />
        <div class="dialog-actions"><SpdButton label="取消" tone="neutral" :disabled="recallSubmitting" @click="recallDialogOpen = false"></SpdButton><SpdButton tone="primary" type="submit" :disabled="recallSubmitting">
          {{ recallSubmitting ? '正在提交...' : '确认登记' }}
        </SpdButton></div>
      </form>
    </el-dialog>

    <section v-if="type !== 'settlement' && type !== 'red-flush' && type !== 'high-value' && !isRecallPage"
      class="hospital-catalog-panel" :class="{ 'consumption-entry-card': type === 'consumption', 'delivery-workspace-card': type === 'delivery' }">
      <div v-if="type === 'delivery'" class="subnav-tabs delivery-tabs" role="tablist" aria-label="拣配配送业务">
        <button v-for="tab in visibleDeliveryTabs"
          :id="'delivery-tab-' + tab.key" :key="tab.key" type="button" role="tab"
          :aria-selected="deliveryTab === tab.key" :aria-controls="'delivery-panel-' + tab.key"
          :class="{ active: deliveryTab === tab.key }" @click="changeDeliveryTab(tab.key)">
          {{ tab.label }}
        </button>
      </div>
      <template v-if="type !== 'delivery' || deliveryTab === 'picking'">
      <!-- 动作条：左侧「业务动作 / 消耗登记 / 拣配出库」分区标题，
           右侧本区的动作按钮（智能补货分析 / 提交申领 / 确认拣配出库 / 登记消耗 / 冷链异常）。
           两者同一行，flex space-between 左右分列（用户诉求：不要单独一行）。 -->
      <div class="spd-action-bar">
        <div class="spd-action-bar__main">
          <div class="section-title" :class="{ 'consumption-section-title': type === 'consumption', 'delivery-section-title': type === 'delivery' }">
            <template v-if="type === 'consumption'">
              <i class="consumption-section-icon"><ScanLine :size="20" /></i>
              <div>
                <h3>消耗登记</h3>
                <p class="consumption-section-description">扫描追溯码定位耗材，系统将实时校验科室库房库存</p>
              </div>
              <span class="consumption-live-badge"><span></span>实时库存校验</span>
            </template>
            <template v-else-if="type === 'delivery'">
              <Truck :size="20" />
              <div>
                <h3>拣配出库</h3>
                <p class="delivery-section-description">选择待拣配申领单，再按定数包、唯一码或散货完成拣配并出库</p>
              </div>
            </template>
            <template v-else>
              <ClipboardCheck :size="20" />
              <h3>业务动作</h3>
            </template>
          </div>
          <span v-if="type === 'consumption'" class="consumption-action-hint">登记后将同步扣减所选科室库房库存，并写入追溯记录</span>
        </div>
        <!-- 右侧动作区：仅在确有按钮时渲染，避免出现空的有边框容器 -->
        <div v-if="hasActionButtons" class="spd-action-bar__aside">
          <span v-if="type === 'delivery'" class="delivery-action-hint">
            <template v-if="!selectedPickingItemId">请先在下方「待拣配申领单」中选择要拣配的申领明细</template>
            <template v-else>已选 {{ selectedPickingLabels.length }} 个定数包 / {{ selectedPickingUniqueCodes.length }} 个唯一码 / 散货 {{ selectedPickingTotal }}，待拣配 {{ selectedPendingQty }}</template>
          </span>
          <SpdButton label="智能补货分析" tone="primary" v-if="type === 'shortage'" @click="openSmartAnalysis">
            <template #icon><AlertTriangle :size="18" /></template>
          </SpdButton>
          <SpdButton label="提交申领" tone="primary" v-if="type === 'requisition'" @click="runAction('requisition')">
            <template #icon><Save :size="18" /></template>
          </SpdButton>
          <SpdButton label="确认拣配出库" tone="primary" v-if="type === 'delivery'" :disabled="deliverySubmitting || loading || !selectedPickingItemId || Boolean(pickingLooseStatus)" @click="runAction('delivery')">
            <template #icon><Truck :size="18" /></template>
          </SpdButton>
          <SpdButton label="登记消耗" tone="primary" v-if="type === 'consumption'" @click="runAction('consumption')">
            <template #icon><PackageCheck :size="18" /></template>
          </SpdButton>
          <SpdButton label="冷链异常" tone="primary" v-if="type === 'risk' && !isRecallPage" @click="runAction('cold')">
            <template #icon><AlertTriangle :size="18" /></template>
          </SpdButton>
        </div>
      </div>
      <SpdFilterBar :class="{ 'consumption-form-grid': type === 'consumption' }" @submit="applyFilterBarFilters" @reset="resetFilterBarFilters">
        <label v-if="isRecallPage">
          <span>召回范围</span>
          <select v-model="recallScope" @change="applyRecallScope">
            <option value="all">全部</option><option value="primary">一级库</option>
            <option value="secondary">二级库</option><option value="tertiary">三级库</option>
          </select>
        </label>
        <label v-if="!isRecallPage || ['secondary', 'tertiary'].includes(recallScope)">
          <span>科室</span>
          <select v-if="type === 'delivery'" v-model="pickingFilters.deptCode" @change="applyPickingFilters">
            <option value="">全部授权科室</option>
            <option v-for="item in options.departments" :key="item.deptCode" :value="item.deptCode">{{ item.deptName }}</option>
          </select>
          <select v-else-if="type === 'consumption'" v-model="form.deptCode">
            <option v-for="item in options.departments" :key="item.deptCode" :value="item.deptCode">{{ item.deptName }}（{{ item.deptCode }}）</option>
          </select>
          <select v-else v-model="form.deptName">
            <option v-if="type === 'shortage'" value="">全院</option>
            <option v-for="item in options.departments" :key="item.deptName" :value="item.deptName">{{ item.deptName }}</option>
          </select>
        </label>
        <label v-if="!isRecallPage || recallScope !== 'all'">
          <span>库房</span>
          <select v-if="type === 'consumption'" v-model="form.warehouseId">
            <option v-for="item in warehouseChoices" :key="item.warehouseId" :value="item.warehouseId">{{ item.warehouseName }}</option>
          </select>
          <select v-else v-model="form.warehouseName">
            <option v-for="item in warehouseChoices" :key="item.warehouseName" :value="item.warehouseName">{{ item.warehouseName }}</option>
          </select>
        </label>
        <label v-if="!['shortage', 'delivery', 'consumption'].includes(type)">
          <span>商品</span>
          <select v-model="form.productCode">
            <option v-for="item in options.products" :key="item.productCode" :value="item.productCode">
              {{ item.productCode }} / {{ item.productName }}
            </option>
          </select>
        </label>
        <template v-if="type === 'consumption'">
          <label class="wide-field">
            <span>扫码识别耗材</span>
            <div class="consumption-code-search">
              <input
                v-model.trim="consumptionQueryCode"
                @input="consumptionResolved = null"
                placeholder="扫码或输入定数包码、UDI、唯一码，点击「定位」识别商品"
                @keyup.enter="resolveConsumption"
              />
              <!-- 这里刻意不叫「查询」：本按钮只做「按码定位耗材」，
                   与筛选栏右侧的标准「查询」（提交筛选表单）是两个不同动作。
                   同名会让科室消耗页出现两个「查询」按钮，用户无从分辨。 -->
              <SpdButton tone="primary" :disabled="consumptionResolving" @click="resolveConsumption">
                <template #icon><ScanLine :size="15" /></template>
                {{ consumptionResolving ? '定位中...' : '定位' }}
              </SpdButton>
            </div>
          </label>
          <label v-if="consumptionResolved" class="wide-field">
            <span>已识别耗材</span>
            <div class="consumption-resolved">
              <strong>{{ consumptionResolved.productCode }} / {{ consumptionResolved.productName }}</strong>
              <small>{{ consumptionResolved.specModel || '-' }} · {{ consumptionResolved.unit || '-' }} · 来源：{{ consumptionResolved.sourceType === 'package' ? '定数包码' : 'UDI/唯一码' }}<template v-if="consumptionResolved.warehouseName"> · 库房 {{ consumptionResolved.warehouseName }}</template></small>
            </div>
          </label>
        </template>
        <label v-if="!['shortage', 'delivery'].includes(type) && !isRecallPage"><span>数量</span><input v-integer-quantity v-model.number="form.quantity" type="number" min="1" :readonly="type === 'consumption' && (consumptionResolved?.sourceType === 'package' || Number(consumptionResolved?.highValue) === 1)" /></label>
        <label v-if="type === 'pda'"><span>设备号</span><input v-model="form.deviceNo" /></label>
        <template v-if="type === 'risk'">
          <label v-if="!isRecallPage"><span>温度（冷链）</span><input v-model="form.temperature" type="number" placeholder="冷链异常温度" /></label>
          <label v-if="!isRecallPage">
            <span>召回批号</span>
            <select v-model.number="recallBatchId" :disabled="recallBatchesLoading || !recallBatches.length">
              <option :value="null" disabled>请先选择商品</option>
              <option v-for="batch in recallBatches" :key="String(batch.batchId)" :value="Number(batch.batchId)">
                {{ batch.systemBatchNo }}（可用 {{ batch.availableQty }}）
              </option>
            </select>
          </label>
          <label v-else>
            <span>召回批号</span>
            <input v-model.trim="recallBatchNo" placeholder="填写系统批号或生产批号" />
          </label>
          <label class="wide-field">
            <span>召回原因</span>
            <input v-model.trim="form.reason" placeholder="填写召回原因" />
          </label>
        </template>
        <label v-if="['requisition', 'consumption'].includes(type)" class="wide-field">
          <span>高值唯一码</span>
          <input v-model.trim="form.uniqueCodes" placeholder="扫描或输入唯一码；多码用逗号分隔" />
        </label>
        <template v-if="type === 'shortage'">
          <label>
            <span>补货周期</span>
            <select v-model="form.replenishmentDays">
              <option v-for="days in replenishmentDayOptions" :key="days" :value="days">{{ days }} 天</option>
            </select>
          </label>
        </template>
      </SpdFilterBar>

            <MdDataTableCard v-if="isRecallPage">
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="ocRecallInventoryColSettingsAnchorRef"
           
           
            @click="ocRecallInventoryCols.settingsOpen.value = !ocRecallInventoryCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="ocRecallInventoryCols.settingsOpen.value"
ref="ocRecallInventoryColSettingsPopRef"
:class="{ 'is-align-right': ocRecallInventoryColSettingsPop.align.value === 'right', 'is-align-up': ocRecallInventoryColSettingsPop.vertical.value === 'up' }"
:style="ocRecallInventoryColSettingsPop.popStyle.value"
:columns="ocRecallInventoryCols.columns.value"
:visible-column-count="ocRecallInventoryCols.visibleColumnCount.value"
@toggle="ocRecallInventoryCols.setColumnVisible"
@move="ocRecallInventoryCols.moveColumn"
@reset="ocRecallInventoryCols.resetColumns()"
@confirm="ocRecallInventoryCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="ocRecallInventoryTableRef"
            :data="recallInventoryRows"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightOcRecallInventory.maxHeight"
            style="width: 100%"
            :empty-text="'当前范围暂无可召回库存'"
            @header-dragend="(width: number, oldWidth: number, column: any) => ocRecallInventoryCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="warehouseType"
              v-if="isOcRecallInventoryColVisible('warehouseType')"
              label="库房级别"
              v-bind="ocRecallInventoryColBinding('warehouseType')"
            >
              <template #header>
                <span class="ulc-th-text">库房级别</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.warehouseType }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="warehouseName"
              v-if="isOcRecallInventoryColVisible('warehouseName')"
              label="库房"
              v-bind="ocRecallInventoryColBinding('warehouseName')"
            >
              <template #header>
                <span class="ulc-th-text">库房</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.warehouseName }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="productName"
              v-if="isOcRecallInventoryColVisible('productName')"
              label="商品"
              v-bind="ocRecallInventoryColBinding('productName')"
            >
              <template #header>
                <span class="ulc-th-text">商品</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.productName }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="spec"
              v-if="isOcRecallInventoryColVisible('spec')"
              label="规格"
              v-bind="ocRecallInventoryColBinding('spec')"
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
              v-if="isOcRecallInventoryColVisible('manufacturer')"
              label="厂家"
              v-bind="ocRecallInventoryColBinding('manufacturer')"
            >
              <template #header>
                <span class="ulc-th-text">厂家</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.manufacturerName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="unit"
              v-if="isOcRecallInventoryColVisible('unit')"
              label="单位"
              v-bind="ocRecallInventoryColBinding('unit')"
            >
              <template #header>
                <span class="ulc-th-text">单位</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.unit }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="unitPrice"
              v-if="isOcRecallInventoryColVisible('unitPrice')"
              label="单价"
              v-bind="ocRecallInventoryColBinding('unitPrice')"
            >
              <template #header>
                <span class="ulc-th-text">单价</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.unitPrice || 0).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="systemBatch"
              v-if="isOcRecallInventoryColVisible('systemBatch')"
              label="系统批号"
              v-bind="ocRecallInventoryColBinding('systemBatch')"
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
              v-if="isOcRecallInventoryColVisible('productionBatch')"
              label="生产批号"
              v-bind="ocRecallInventoryColBinding('productionBatch')"
            >
              <template #header>
                <span class="ulc-th-text">生产批号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.productionBatchNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="looseQty"
              v-if="isOcRecallInventoryColVisible('looseQty')"
              label="散货"
              v-bind="ocRecallInventoryColBinding('looseQty')"
            >
              <template #header>
                <span class="ulc-th-text">散货</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.looseQty }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="packageQty"
              v-if="isOcRecallInventoryColVisible('packageQty')"
              label="定数包折散"
              v-bind="ocRecallInventoryColBinding('packageQty')"
            >
              <template #header>
                <span class="ulc-th-text">定数包折散</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.packageQty }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="totalQty"
              v-if="isOcRecallInventoryColVisible('totalQty')"
              label="合计"
              v-bind="ocRecallInventoryColBinding('totalQty')"
            >
              <template #header>
                <span class="ulc-th-text">合计</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number"><strong>{{ row.totalQty }}</strong></span>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="当前范围暂无可召回库存" />
            </template>
          </el-table>
        </div>
      </MdDataTableCard>

      <div v-if="type === 'delivery'" id="delivery-panel-picking" class="picking-workbench"
        role="tabpanel" aria-labelledby="delivery-tab-picking">
        <section class="picking-panel picking-panel--requisition">
          <form class="settlement-filters" aria-label="待拣配申领日期查询" @submit.prevent="applyPickingFilters">
            <label><span>审批开始日期</span><input v-model="pickingFilters.dateFrom" class="md-input" type="date" :max="pickingFilters.dateTo || undefined" /></label>
            <label><span>审批结束日期</span><input v-model="pickingFilters.dateTo" class="md-input" type="date" :min="pickingFilters.dateFrom || undefined" /></label>
            <div class="settlement-query-actions">
              <SpdButton label="查询" tone="primary" type="submit">
                <template #icon><Search :size="16" /></template>
              </SpdButton>
              <SpdButton label="重置" tone="neutral" @click="resetPickingFilters">
                <template #icon><RefreshCw :size="16" /></template>
              </SpdButton>
            </div>
          </form>
                    <MdDataTableCard>
            <template #toolbar>
            <div class="ulc-col-settings">
              <SpdButton label="列设置" tone="neutral" ref="ocPickingRequisitionColSettingsAnchorRef"
               
               
                @click="ocPickingRequisitionCols.settingsOpen.value = !ocPickingRequisitionCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
              <SpdColumnSettings
v-if="ocPickingRequisitionCols.settingsOpen.value"
ref="ocPickingRequisitionColSettingsPopRef"
:class="{ 'is-align-right': ocPickingRequisitionColSettingsPop.align.value === 'right', 'is-align-up': ocPickingRequisitionColSettingsPop.vertical.value === 'up' }"
:style="ocPickingRequisitionColSettingsPop.popStyle.value"
:columns="ocPickingRequisitionCols.columns.value"
:visible-column-count="ocPickingRequisitionCols.visibleColumnCount.value"
@toggle="ocPickingRequisitionCols.setColumnVisible"
@move="ocPickingRequisitionCols.moveColumn"
@reset="ocPickingRequisitionCols.resetColumns()"
@confirm="ocPickingRequisitionCols.settingsOpen.value = false"
          />
            </div>
            </template>
            <div class="ulc-table-wrap">
              <el-table
                ref="ocPickingRequisitionTableRef"
                :data="paginatedPickingRequisitions"
                border
                stripe
                class="ulc-table is-sticky"
                :max-height="fillHeightOcPickingRequisition.maxHeight"
                style="width: 100%"
                :empty-text="'暂无待拣配申领单'"
                @header-dragend="(width: number, oldWidth: number, column: any) => ocPickingRequisitionCols.handleHeaderDragEnd(width, oldWidth, column)"
              >
                <el-table-column
                  column-key="requisitionNo"
                  v-if="isOcPickingRequisitionColVisible('requisitionNo')"
                  label="申领单号"
                  v-bind="ocPickingRequisitionColBinding('requisitionNo')"
                >
                  <template #header>
                    <span class="ulc-th-text">申领单号</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-cell-stack">
                      <strong>{{ row.requisitionNo }}</strong>
                      <SpdStatusChip
                        v-if="String(row.status || '') === 'partial_picked'"
                        tone="warning"
                        :label="formatStatusText(row.status)"
                      />
                    </span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="requisitionCreateTime"
                  v-if="isOcPickingRequisitionColVisible('requisitionCreateTime')"
                  label="申领单创建时间"
                  v-bind="ocPickingRequisitionColBinding('requisitionCreateTime')"
                >
                  <template #header>
                    <span class="ulc-th-text">申领单创建时间</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ formatDateTime(row.requisitionCreatedAt) }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="requisitionType"
                  v-if="isOcPickingRequisitionColVisible('requisitionType')"
                  label="申领类型"
                  v-bind="ocPickingRequisitionColBinding('requisitionType')"
                >
                  <template #header>
                    <span class="ulc-th-text">申领类型</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.requisitionTypeLabel || row.requisitionType || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="partialPickingTime"
                  v-if="isOcPickingRequisitionColVisible('partialPickingTime')"
                  label="部分拣配完成时间"
                  v-bind="ocPickingRequisitionColBinding('partialPickingTime')"
                >
                  <template #header>
                    <span class="ulc-th-text">部分拣配完成时间</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ formatDateTime(row.partialPickingCompletedAt) }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="deptName"
                  v-if="isOcPickingRequisitionColVisible('deptName')"
                  label="科室"
                  v-bind="ocPickingRequisitionColBinding('deptName')"
                >
                  <template #header>
                    <span class="ulc-th-text">科室</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.deptName }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="product"
                  v-if="isOcPickingRequisitionColVisible('product')"
                  label="商品"
                  v-bind="ocPickingRequisitionColBinding('product')"
                >
                  <template #header>
                    <span class="ulc-th-text">商品</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-cell-stack">
                      <strong>{{ row.productName }}</strong>
                      <span class="muted-cell">{{ row.productCode }}</span>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="applyType"
                  v-if="isOcPickingRequisitionColVisible('applyType')"
                  label="申请类型"
                  v-bind="ocPickingRequisitionColBinding('applyType')"
                >
                  <template #header>
                    <span class="ulc-th-text">申请类型</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-cell-stack">
                      <SpdStatusChip tone="neutral" :label="itemTypeLabel(row.itemType)" />
                      <span v-if="row.itemType === 'quota_package'" class="muted-cell">
                        版本 {{ row.templateVersion ?? '历史快照缺失' }} ·
                        {{ row.packageQuantity ?? '-' }} / {{ row.packageUnit ?? '-' }}
                        · 待拣 {{ row.remainingPackageCount ?? '-' }} 包
                      </span>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="requisitionQty"
                  v-if="isOcPickingRequisitionColVisible('requisitionQty')"
                  label="申领数量"
                  v-bind="ocPickingRequisitionColBinding('requisitionQty')"
                >
                  <template #header>
                    <span class="ulc-th-text">申领数量</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.requisitionQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="pickedQty"
                  v-if="isOcPickingRequisitionColVisible('pickedQty')"
                  label="已拣配"
                  v-bind="ocPickingRequisitionColBinding('pickedQty')"
                >
                  <template #header>
                    <span class="ulc-th-text">已拣配</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.pickedQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="pendingQty"
                  v-if="isOcPickingRequisitionColVisible('pendingQty')"
                  label="待拣配"
                  v-bind="ocPickingRequisitionColBinding('pendingQty')"
                >
                  <template #header>
                    <span class="ulc-th-text">待拣配</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.remainingQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="actions"
                  v-if="isOcPickingRequisitionColVisible('actions')"
                  label="操作"
                  v-bind="ocPickingRequisitionColBinding('actions')"
                  :fixed="ocPickingRequisitionActionFixed"
                  align="center"
                >
                  <template #header>
                    <span class="ulc-th-text">操作</span>
                  </template>
                  <template #default="{ row }">
                  <div class="ulc-actions">
                    <button :aria-pressed="selectedPickingItemId === String(row.itemId)"
                      @click="selectPickingRequisition(row)" class="ulc-action" type="button">{{ selectedPickingItemId === String(row.itemId) ? '已选择' : '选择' }}</button>
                  </div>
                  <!-- 已选中的散货明细行旁显示查询状态：
                       加载中 / 查询失败 / 无可用散货，让用户知道为什么不能提交出库。 -->
                  <span
                    v-if="selectedPickingItemId === String(row.itemId) && pickingLooseStatus"
                    class="muted-cell"
                    :role="pickingLooseError ? 'alert' : 'status'">{{ pickingLooseStatus }}</span>
                  </template>
                </el-table-column>
                <template #empty>
                  <MdEmptyState text="暂无待拣配申领单" />
                </template>
              </el-table>
            </div>
            <template #pagination>
              <MdPagination
                :page="pickingPage"
                :total-pages="Math.max(1, Math.ceil(pickingTotal / pickingSize))"
                :total="pickingTotal"
                :size="pickingSize"
                :loading="loading"
                @update:page="changePickingPage"
                @update:size="changePickingSize"
              />
            </template>
          </MdDataTableCard>
        </section>

        <!-- 唯一码/UDI 类型：展示申请单绑定的唯一码 -->
        <section v-if="selectedPickingItemType === 'unique_code'" class="picking-panel">
          <div class="section-title compact">
            <h3>可用唯一码 / UDI</h3>
            <span class="muted-hint">已选 {{ selectedPickingUniqueCodes.length }} / 待拣配 {{ selectedPendingQty }}</span>
          </div>
                    <MdDataTableCard>
            <template #toolbar>
            <div class="ulc-col-settings">
              <SpdButton label="列设置" tone="neutral" ref="ocPickingUniqueCodeColSettingsAnchorRef"
               
               
                @click="ocPickingUniqueCodeCols.settingsOpen.value = !ocPickingUniqueCodeCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
              <SpdColumnSettings
v-if="ocPickingUniqueCodeCols.settingsOpen.value"
ref="ocPickingUniqueCodeColSettingsPopRef"
:class="{ 'is-align-right': ocPickingUniqueCodeColSettingsPop.align.value === 'right', 'is-align-up': ocPickingUniqueCodeColSettingsPop.vertical.value === 'up' }"
:style="ocPickingUniqueCodeColSettingsPop.popStyle.value"
:columns="ocPickingUniqueCodeCols.columns.value"
:visible-column-count="ocPickingUniqueCodeCols.visibleColumnCount.value"
@toggle="ocPickingUniqueCodeCols.setColumnVisible"
@move="ocPickingUniqueCodeCols.moveColumn"
@reset="ocPickingUniqueCodeCols.resetColumns()"
@confirm="ocPickingUniqueCodeCols.settingsOpen.value = false"
          />
            </div>
            </template>
            <div class="ulc-table-wrap">
              <el-table
                ref="ocPickingUniqueCodeTableRef"
                :data="pagePickingDetails(pickingUniqueCodeRows)"
                border
                stripe
                class="ulc-table is-sticky"
                :max-height="fillHeightOcPickingUniqueCode.maxHeight"
                style="width: 100%"
                :empty-text="'该申请明细没有可拣配的唯一码/UDI'"
                @header-dragend="(width: number, oldWidth: number, column: any) => ocPickingUniqueCodeCols.handleHeaderDragEnd(width, oldWidth, column)"
              >
                <el-table-column
                  column-key="selection"
                  v-if="isOcPickingUniqueCodeColVisible('selection')"
                  label=""
                  v-bind="ocPickingUniqueCodeColBinding('selection')"
                >
                  <template #header>
                    <span class="ulc-th-text"></span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text"><input
                      type="checkbox"
                      :checked="selectedPickingUniqueCodes.includes(String(row.uniqueCode))"
                      @change="togglePickingUniqueCode(String(row.uniqueCode), ($event.target as HTMLInputElement).checked)"
                    /></span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="uniqueCode"
                  v-if="isOcPickingUniqueCodeColVisible('uniqueCode')"
                  label="唯一码"
                  v-bind="ocPickingUniqueCodeColBinding('uniqueCode')"
                >
                  <template #header>
                    <span class="ulc-th-text">唯一码</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text"><strong>{{ row.uniqueCode }}</strong></span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="udiCode"
                  v-if="isOcPickingUniqueCodeColVisible('udiCode')"
                  label="UDI"
                  v-bind="ocPickingUniqueCodeColBinding('udiCode')"
                >
                  <template #header>
                    <span class="ulc-th-text">UDI</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.udiCode }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="batchNo"
                  v-if="isOcPickingUniqueCodeColVisible('batchNo')"
                  label="系统批次"
                  v-bind="ocPickingUniqueCodeColBinding('batchNo')"
                >
                  <template #header>
                    <span class="ulc-th-text">系统批次</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.batchNo || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="expireDate"
                  v-if="isOcPickingUniqueCodeColVisible('expireDate')"
                  label="有效期"
                  v-bind="ocPickingUniqueCodeColBinding('expireDate')"
                >
                  <template #header>
                    <span class="ulc-th-text">有效期</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.expireDate || '-' }}</span>
                  </template>
                </el-table-column>
                <template #empty>
                  <MdEmptyState text="该申请明细没有可拣配的唯一码/UDI" />
                </template>
              </el-table>
            </div>
            <template #pagination>
              <MdPagination
                :page="pickingDetailPage"
                :total-pages="pickingDetailLastPage"
                :total="pickingDetailTotal"
                :size="pickingDetailSize"
                :loading="loading || pickingLooseLoading"
                @update:page="pickingDetailPage = $event"
                @update:size="changePickingDetailSize"
              />
            </template>
          </MdDataTableCard>
        </section>

        <template v-else>
        <section v-if="selectedPickingItemType === 'quota_package'" class="picking-panel">
          <div class="section-title compact">
            <h3>可用定数包</h3>
            <span class="muted-hint">按申领模板版本和包装规格选择标签</span>
          </div>
          <div class="consumption-code-search picking-package-scan">
            <input v-model.trim="pickingPackageScanCode" placeholder="扫描或输入定数包码" @keyup.enter="scanPickingPackage" />
            <SpdButton label="扫码拣选" tone="primary" @click="scanPickingPackage">
          <template #icon><Search :size="15" /></template>
        </SpdButton>
          </div>
                    <MdDataTableCard>
            <template #toolbar>
            <div class="ulc-col-settings">
              <SpdButton label="列设置" tone="neutral" ref="ocPickingQuotaPackageColSettingsAnchorRef"
               
               
                @click="ocPickingQuotaPackageCols.settingsOpen.value = !ocPickingQuotaPackageCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
              <SpdColumnSettings
v-if="ocPickingQuotaPackageCols.settingsOpen.value"
ref="ocPickingQuotaPackageColSettingsPopRef"
:class="{ 'is-align-right': ocPickingQuotaPackageColSettingsPop.align.value === 'right', 'is-align-up': ocPickingQuotaPackageColSettingsPop.vertical.value === 'up' }"
:style="ocPickingQuotaPackageColSettingsPop.popStyle.value"
:columns="ocPickingQuotaPackageCols.columns.value"
:visible-column-count="ocPickingQuotaPackageCols.visibleColumnCount.value"
@toggle="ocPickingQuotaPackageCols.setColumnVisible"
@move="ocPickingQuotaPackageCols.moveColumn"
@reset="ocPickingQuotaPackageCols.resetColumns()"
@confirm="ocPickingQuotaPackageCols.settingsOpen.value = false"
          />
            </div>
            </template>
            <div class="ulc-table-wrap">
              <el-table
                ref="ocPickingQuotaPackageTableRef"
                :data="pagePickingDetails(pickingPackageRows)"
                border
                stripe
                class="ulc-table is-sticky"
                :max-height="fillHeightOcPickingQuotaPackage.maxHeight"
                style="width: 100%"
                :empty-text="'请选择申领单和库房后查看可用定数包'"
                @header-dragend="(width: number, oldWidth: number, column: any) => ocPickingQuotaPackageCols.handleHeaderDragEnd(width, oldWidth, column)"
              >
                <el-table-column
                  column-key="selection"
                  v-if="isOcPickingQuotaPackageColVisible('selection')"
                  label=""
                  v-bind="ocPickingQuotaPackageColBinding('selection')"
                >
                  <template #header>
                    <span class="ulc-th-text"></span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text"><input
                      type="checkbox"
                      :checked="selectedPickingLabels.includes(String(row.labelNo))"
                      @change="togglePickingLabel(String(row.labelNo), ($event.target as HTMLInputElement).checked)"
                    /></span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="labelNo"
                  v-if="isOcPickingQuotaPackageColVisible('labelNo')"
                  label="定数包码"
                  v-bind="ocPickingQuotaPackageColBinding('labelNo')"
                >
                  <template #header>
                    <span class="ulc-th-text">定数包码</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text"><SpdButton variant="link" tone="primary" @click="openPackageDetail(row.labelNo)">
                      {{ row.labelNo }}
                    </SpdButton></span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="warehouseName"
                  v-if="isOcPickingQuotaPackageColVisible('warehouseName')"
                  label="库房"
                  v-bind="ocPickingQuotaPackageColBinding('warehouseName')"
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
                  v-if="isOcPickingQuotaPackageColVisible('product')"
                  label="商品"
                  v-bind="ocPickingQuotaPackageColBinding('product')"
                >
                  <template #header>
                    <span class="ulc-th-text">商品</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-cell-stack">
                      <strong>{{ row.productName }}</strong>
                      <span class="muted-cell">{{ row.productCode }}</span>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="packageQuantity"
                  v-if="isOcPickingQuotaPackageColVisible('packageQuantity')"
                  label="包内数量"
                  v-bind="ocPickingQuotaPackageColBinding('packageQuantity')"
                >
                  <template #header>
                    <span class="ulc-th-text">包内数量</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.packageQuantity }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="createTime"
                  v-if="isOcPickingQuotaPackageColVisible('createTime')"
                  label="生成时间"
                  v-bind="ocPickingQuotaPackageColBinding('createTime')"
                >
                  <template #header>
                    <span class="ulc-th-text">生成时间</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.createTime }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="updateTime"
                  v-if="isOcPickingQuotaPackageColVisible('updateTime')"
                  label="修改时间"
                  v-bind="ocPickingQuotaPackageColBinding('updateTime')"
                >
                  <template #header>
                    <span class="ulc-th-text">修改时间</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.updateTime || '-' }}</span>
                  </template>
                </el-table-column>
                <template #empty>
                  <MdEmptyState text="请选择申领单和库房后查看可用定数包" />
                </template>
              </el-table>
            </div>
            <template #pagination>
              <MdPagination
                :page="pickingDetailPage"
                :total-pages="pickingDetailLastPage"
                :total="pickingDetailTotal"
                :size="pickingDetailSize"
                :loading="loading || pickingLooseLoading"
                @update:page="pickingDetailPage = $event"
                @update:size="changePickingDetailSize"
              />
            </template>
          </MdDataTableCard>
        </section>

        <section v-if="selectedPickingItemType === 'loose'" class="picking-panel picking-panel--loose">
          <div class="section-title compact">
            <h3>散货拣配</h3>
            <span class="muted-hint">已选合计 {{ selectedPickingTotal }} / 待拣配 {{ selectedPendingQty }}（基础单位）</span>
          </div>
                    <MdDataTableCard>
            <template #toolbar>
            <div class="ulc-col-settings">
              <SpdButton label="列设置" tone="neutral" ref="ocPickingLooseColSettingsAnchorRef"
               
               
                @click="ocPickingLooseCols.settingsOpen.value = !ocPickingLooseCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
              <SpdColumnSettings
v-if="ocPickingLooseCols.settingsOpen.value"
ref="ocPickingLooseColSettingsPopRef"
:class="{ 'is-align-right': ocPickingLooseColSettingsPop.align.value === 'right', 'is-align-up': ocPickingLooseColSettingsPop.vertical.value === 'up' }"
:style="ocPickingLooseColSettingsPop.popStyle.value"
:columns="ocPickingLooseCols.columns.value"
:visible-column-count="ocPickingLooseCols.visibleColumnCount.value"
@toggle="ocPickingLooseCols.setColumnVisible"
@move="ocPickingLooseCols.moveColumn"
@reset="ocPickingLooseCols.resetColumns()"
@confirm="ocPickingLooseCols.settingsOpen.value = false"
          />
            </div>
            </template>
            <div class="ulc-table-wrap">
              <el-table
                ref="ocPickingLooseTableRef"
                :data="pagePickingDetails(pickingLooseRows)"
                border
                stripe
                class="ulc-table is-sticky"
                :max-height="fillHeightOcPickingLoose.maxHeight"
                style="width: 100%"
                :empty-text="'暂无可拣配散货'"
                @header-dragend="(width: number, oldWidth: number, column: any) => ocPickingLooseCols.handleHeaderDragEnd(width, oldWidth, column)"
              >
                <el-table-column
                  column-key="productName"
                  v-if="isOcPickingLooseColVisible('productName')"
                  label="商品名称"
                  v-bind="ocPickingLooseColBinding('productName')"
                >
                  <template #header>
                    <span class="ulc-th-text">商品名称</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-cell-stack">
                      <strong>{{ row.productName }}</strong>
                      <span class="muted-cell">{{ row.productCode }}</span>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="systemBatch"
                  v-if="isOcPickingLooseColVisible('systemBatch')"
                  label="系统批次"
                  v-bind="ocPickingLooseColBinding('systemBatch')"
                >
                  <template #header>
                    <span class="ulc-th-text">系统批次</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.systemBatchNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="availableQty"
                  v-if="isOcPickingLooseColVisible('availableQty')"
                  label="可用数量"
                  v-bind="ocPickingLooseColBinding('availableQty')"
                >
                  <template #header>
                    <span class="ulc-th-text">可用数量</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text">{{ row.availableQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  column-key="pickedQty"
                  v-if="isOcPickingLooseColVisible('pickedQty')"
                  label="拣配数量"
                  v-bind="ocPickingLooseColBinding('pickedQty')"
                >
                  <template #header>
                    <span class="ulc-th-text">拣配数量</span>
                  </template>
                  <template #default="{ row }">
                    <span class="ulc-wrap-text"><input v-integer-quantity
                      v-model.number="row.pickQty"
                      class="stocktaking-qty-input"
                      type="number"
                      min="0"
                      :max="Number(row.availableQty)" step="1"
                      placeholder="0"
                    /></span>
                  </template>
                </el-table-column>
                <template #empty>
                  <!-- 空态文案与散货查询状态保持一致：
                       加载中 / 查询失败 / 无可用散货 三种情况都给出明确提示，
                       而不是统一显示「暂无可拣配散货」。 -->
                  <MdEmptyState :text="pickingLooseStatus || '暂无可拣配散货'" />
                </template>
              </el-table>
            </div>
            <template #pagination>
              <MdPagination
                :page="pickingDetailPage"
                :total-pages="pickingDetailLastPage"
                :total="pickingDetailTotal"
                :size="pickingDetailSize"
                :loading="loading || pickingLooseLoading"
                @update:page="pickingDetailPage = $event"
                @update:size="changePickingDetailSize"
              />
            </template>
          </MdDataTableCard>
        </section>
        </template>
      </div>
      </template>

    <section v-else-if="deliveryTab === 'returns' && auth.canWrite('picking-delivery')"
      id="delivery-panel-returns" class="delivery-confirmation-panel" role="tabpanel" aria-labelledby="delivery-tab-returns">
      <StatusMessage :message="returnQueue.error" tone="error" role="alert" />
            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="ocReturnQueueColSettingsAnchorRef"
           
           
            @click="ocReturnQueueCols.settingsOpen.value = !ocReturnQueueCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="ocReturnQueueCols.settingsOpen.value"
ref="ocReturnQueueColSettingsPopRef"
:class="{ 'is-align-right': ocReturnQueueColSettingsPop.align.value === 'right', 'is-align-up': ocReturnQueueColSettingsPop.vertical.value === 'up' }"
:style="ocReturnQueueColSettingsPop.popStyle.value"
:columns="ocReturnQueueCols.columns.value"
:visible-column-count="ocReturnQueueCols.visibleColumnCount.value"
@toggle="ocReturnQueueCols.setColumnVisible"
@move="ocReturnQueueCols.moveColumn"
@reset="ocReturnQueueCols.resetColumns()"
@confirm="ocReturnQueueCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="ocReturnQueueTableRef"
            :data="returnQueue.result.rows"
            row-key="returnNo"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightOcReturnQueue.maxHeight"
            style="width: 100%"
            :empty-text="'暂无待确认科室退库'"
            @header-dragend="(width: number, oldWidth: number, column: any) => ocReturnQueueCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="returnNo"
              v-if="isOcReturnQueueColVisible('returnNo')"
              label="退库单号"
              v-bind="ocReturnQueueColBinding('returnNo')"
            >
              <template #header>
                <span class="ulc-th-text">退库单号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.returnNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="sourceWarehouse"
              v-if="isOcReturnQueueColVisible('sourceWarehouse')"
              label="二级库"
              v-bind="ocReturnQueueColBinding('sourceWarehouse')"
            >
              <template #header>
                <span class="ulc-th-text">二级库</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.sourceWarehouse }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="destinationWarehouse"
              v-if="isOcReturnQueueColVisible('destinationWarehouse')"
              label="接收中心库"
              v-bind="ocReturnQueueColBinding('destinationWarehouse')"
            >
              <template #header>
                <span class="ulc-th-text">接收中心库</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.destinationWarehouse }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="quantity"
              v-if="isOcReturnQueueColVisible('quantity')"
              label="基本数量"
              v-bind="ocReturnQueueColBinding('quantity')"
            >
              <template #header>
                <span class="ulc-th-text">基本数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.quantity }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isOcReturnQueueColVisible('createTime')"
              label="登记时间"
              v-bind="ocReturnQueueColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">登记时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.createTime }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isOcReturnQueueColVisible('actions')"
              label="操作"
              v-bind="ocReturnQueueColBinding('actions')"
              :fixed="ocReturnQueueActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button :disabled="returnQueue.loading || Boolean(confirmingWarehouseReturn)"
                @click="confirmDepartmentReturn(row)" class="ulc-action md-btn md-btn-primary" type="button">确认退库</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无待确认科室退库" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="returnQueue.result.page"
            :total-pages="Math.max(1, Math.ceil(returnQueue.result.total / Math.max(returnQueue.result.size, 1)))"
            :total="returnQueue.result.total"
            :size="returnQueue.result.size"
            :loading="returnQueue.loading"
            @update:page="returnQueue.load($event)"
            @update:size="returnQueue.load(1, $event)"
          />
        </template>
      </MdDataTableCard>
    </section>

    <section v-else-if="deliveryTab === 'recalls' && auth.canWrite('picking-delivery')"
      id="delivery-panel-recalls" class="delivery-confirmation-panel" role="tabpanel" aria-labelledby="delivery-tab-recalls">
      <StatusMessage :message="recallQueue.error" tone="error" role="alert" />
            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton
            ref="ocRecallQueueColSettingsAnchorRef"
            label="列设置"
            @click="ocRecallQueueCols.settingsOpen.value = !ocRecallQueueCols.settingsOpen.value">
            <template #icon><Columns3 :size="16" /></template>
          </SpdButton>
          <SpdColumnSettings
v-if="ocRecallQueueCols.settingsOpen.value"
ref="ocRecallQueueColSettingsPopRef"
:class="{ 'is-align-right': ocRecallQueueColSettingsPop.align.value === 'right', 'is-align-up': ocRecallQueueColSettingsPop.vertical.value === 'up' }"
:style="ocRecallQueueColSettingsPop.popStyle.value"
:columns="ocRecallQueueCols.columns.value"
:visible-column-count="ocRecallQueueCols.visibleColumnCount.value"
@toggle="ocRecallQueueCols.setColumnVisible"
@move="ocRecallQueueCols.moveColumn"
@reset="ocRecallQueueCols.resetColumns()"
@confirm="ocRecallQueueCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="ocRecallQueueTableRef"
            :data="recallQueue.result.rows"
            row-key="recallNo"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightOcRecallQueue.maxHeight"
            style="width: 100%"
            :empty-text="'暂无待签收召回单'"
            @header-dragend="(width: number, oldWidth: number, column: any) => ocRecallQueueCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="recallNo"
              v-if="isOcRecallQueueColVisible('recallNo')"
              label="召回单号"
              v-bind="ocRecallQueueColBinding('recallNo')"
            >
              <template #header>
                <span class="ulc-th-text">召回单号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.recallNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="sourceWarehouse"
              v-if="isOcRecallQueueColVisible('sourceWarehouse')"
              label="来源二级库"
              v-bind="ocRecallQueueColBinding('sourceWarehouse')"
            >
              <template #header>
                <span class="ulc-th-text">来源二级库</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.sourceWarehouses || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="destinationWarehouse"
              v-if="isOcRecallQueueColVisible('destinationWarehouse')"
              label="接收中心库"
              v-bind="ocRecallQueueColBinding('destinationWarehouse')"
            >
              <template #header>
                <span class="ulc-th-text">接收中心库</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.destinationWarehouse }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="quantity"
              v-if="isOcRecallQueueColVisible('quantity')"
              label="待收数量"
              v-bind="ocRecallQueueColBinding('quantity')"
            >
              <template #header>
                <span class="ulc-th-text">待收数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.quantity }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isOcRecallQueueColVisible('createTime')"
              label="登记时间"
              v-bind="ocRecallQueueColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">登记时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatDateTime(row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isOcRecallQueueColVisible('actions')"
              label="操作"
              v-bind="ocRecallQueueColBinding('actions')"
              :fixed="ocRecallQueueActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button :disabled="recallQueue.loading || Boolean(receivingRecallNo)"
                @click="confirmRecallReceipt(row)" class="ulc-action md-btn md-btn-primary" type="button">确认签收</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无待签收召回单" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="recallQueue.result.page"
            :total-pages="Math.max(1, Math.ceil(recallQueue.result.total / Math.max(recallQueue.result.size, 1)))"
            :total="recallQueue.result.total"
            :size="recallQueue.result.size"
            :loading="recallQueue.loading"
            @update:page="recallQueue.load($event)"
            @update:size="recallQueue.load(1, $event)"
          />
        </template>
      </MdDataTableCard>
    </section>

    </section>

    <section v-if="type !== 'high-value' && type !== 'delivery'" class="hospital-catalog-panel" :class="{ 'consumption-history-card': type === 'consumption', 'finance-detail-card': type === 'settlement' }">
      <div class="section-title" :class="{ 'consumption-section-title': type === 'consumption' }">
        <template v-if="type === 'consumption'">
          <i class="consumption-section-icon is-history"><History :size="20" /></i>
          <div>
            <h3>消耗记录</h3>
            <p class="consumption-section-description">按时间倒序展示登记、反消耗及红冲结果。成本金额保留原批次口径，供应商结算金额请查看结算对账。</p>
          </div>
        </template>
        <template v-else>
          <History :size="20" />
          <h3>{{ isRecallPage ? '召回与隔离记录' : type === 'settlement' ? '结算明细' : '业务单据' }}</h3>
        </template>
      </div>
      <div v-if="type === 'settlement'" class="settlement-query">
        <form class="settlement-filters" aria-label="结算明细查询" @submit.prevent="applySettlementFiltersWithGuard">
          <label><span>申领开始日期</span><input v-model="settlementFilters.dateFrom" class="md-input" type="date" :max="settlementFilters.dateTo || undefined" /></label>
          <label><span>申领结束日期</span><input v-model="settlementFilters.dateTo" class="md-input" type="date" :min="settlementFilters.dateFrom || undefined" /></label>
          <label><span>追溯码</span><input v-model.trim="settlementFilters.traceCode" class="md-input" type="search" placeholder="UDI、唯一码或定数包码" /></label>
          <label><span>科室</span><input v-model.trim="settlementFilters.deptName" class="md-input" type="search" placeholder="输入结算科室名称" /></label>
          <label><span>商品信息</span><input v-model.trim="settlementFilters.productKeyword" class="md-input" type="search" placeholder="编码、名称、规格、厂家或注册证" /></label>
          <label>
            <span>结算货型</span>
            <select v-model="settlementFilters.goodsType" class="md-select" @change="applySettlementFiltersWithGuard">
              <option value="">全部货型</option>
              <option value="high_value">高值耗材</option>
              <option value="quota_package">定数包</option>
              <option value="loose">散货</option>
              <option value="unknown">待核对货型</option>
            </select>
          </label>
          <label>
            <span>排序</span>
            <select v-model="settlementFilters.sort" class="md-select" @change="applySettlementFiltersWithGuard">
              <option value="timeDesc">申领时间从新到旧</option>
              <option value="timeAsc">申领时间从旧到新</option>
              <option value="departmentAsc">科室名称升序</option>
              <option value="departmentDesc">科室名称降序</option>
            </select>
          </label>
          <div class="settlement-query-actions">
            <SpdButton label="查询" tone="primary" type="submit">
              <template #icon><Search :size="16" /></template>
            </SpdButton>
            <SpdButton label="重置" tone="neutral" @click="resetSettlementFilters">
              <template #icon><RefreshCw :size="16" /></template>
            </SpdButton>
          </div>
        </form>
        <StatusMessage :message="settlementFilterError" tone="error" role="alert" />
        <p class="settlement-query-hint">申领日期仅匹配可关联申领单的明细。同一定数包仅在首条结算明细计 1 包。</p>
      </div>
            <MdDataTableCard v-if="type === 'settlement'">
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="ocSettlementColSettingsAnchorRef"
           
           
            @click="ocSettlementCols.settingsOpen.value = !ocSettlementCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="ocSettlementCols.settingsOpen.value"
ref="ocSettlementColSettingsPopRef"
:class="{ 'is-align-right': ocSettlementColSettingsPop.align.value === 'right', 'is-align-up': ocSettlementColSettingsPop.vertical.value === 'up' }"
:style="ocSettlementColSettingsPop.popStyle.value"
:columns="ocSettlementCols.columns.value"
:visible-column-count="ocSettlementCols.visibleColumnCount.value"
@toggle="ocSettlementCols.setColumnVisible"
@move="ocSettlementCols.moveColumn"
@reset="ocSettlementCols.resetColumns()"
@confirm="ocSettlementCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="ocSettlementTableRef"
            :data="rows"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightOcSettlement.maxHeight"
            style="width: 100%"
            :empty-text="'暂无结算明细'"
            @header-dragend="(width: number, oldWidth: number, column: any) => ocSettlementCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="settlementNo"
              v-if="isOcSettlementColVisible('settlementNo')"
              label="结算单"
              v-bind="ocSettlementColBinding('settlementNo')"
            >
              <template #header>
                <span class="ulc-th-text">结算单</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack settlement-cell-stack" :title="String(row.settlementNo || '-')">
                  <strong>{{ row.settlementNo || '-' }}</strong>
                  <span class="settlement-cell-meta">ID：{{ row.settlementId || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="settlementOwner"
              v-if="isOcSettlementColVisible('settlementOwner')"
              label="结算归属"
              v-bind="ocSettlementColBinding('settlementOwner')"
            >
              <template #header>
                <span class="ulc-th-text">结算归属</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack settlement-cell-stack">
                  <strong :title="String(row.settlementDept || '-')">{{ row.settlementDept || '-' }}</strong>
                  <span class="settlement-cell-meta" :title="String(row.settlementWarehouse || '-')">库房：{{ row.settlementWarehouse || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="requisitionType"
              v-if="isOcSettlementColVisible('requisitionType')"
              label="申领类型"
              v-bind="ocSettlementColBinding('requisitionType')"
            >
              <template #header>
                <span class="ulc-th-text">申领类型</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.requisitionTypeLabel || row.requisitionType || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="productInfo"
              v-if="isOcSettlementColVisible('productInfo')"
              label="商品信息"
              v-bind="ocSettlementColBinding('productInfo')"
            >
              <template #header>
                <span class="ulc-th-text">商品信息</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack settlement-cell-stack">
                  <strong :title="String(row.productName || '-')">{{ row.productName || '-' }}</strong>
                  <span class="settlement-cell-meta" :title="String(row.productCode || '-')">编码：{{ row.productCode || '-' }}</span>
                  <span class="settlement-cell-meta" :title="String(row.genericName || '-')">通用名：{{ row.genericName || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="specUnit"
              v-if="isOcSettlementColVisible('specUnit')"
              label="规格 / 单位"
              v-bind="ocSettlementColBinding('specUnit')"
            >
              <template #header>
                <span class="ulc-th-text">规格 / 单位</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack settlement-cell-stack">
                  <span :title="String(row.specModel || '-')">{{ row.specModel || '-' }}</span>
                  <span class="settlement-cell-meta">单位：{{ row.unit || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="manufacturer"
              v-if="isOcSettlementColVisible('manufacturer')"
              label="厂家 / 注册证"
              v-bind="ocSettlementColBinding('manufacturer')"
            >
              <template #header>
                <span class="ulc-th-text">厂家 / 注册证</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack settlement-cell-stack">
                  <strong :title="String(row.manufacturerName || '-')">{{ row.manufacturerName || '-' }}</strong>
                  <span class="settlement-cell-meta" :title="String(row.registrationNo || '-')">注册证：{{ row.registrationNo || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="supplierName"
              v-if="isOcSettlementColVisible('supplierName')"
              label="结算供应商"
              v-bind="ocSettlementColBinding('supplierName')"
            >
              <template #header>
                <span class="ulc-th-text">结算供应商</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.settlementSupplier || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="goodsType"
              v-if="isOcSettlementColVisible('goodsType')"
              label="货型"
              v-bind="ocSettlementColBinding('goodsType')"
            >
              <template #header>
                <span class="ulc-th-text">货型</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ settlementGoodsLabel(row.goodsType) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="basePrice"
              v-if="isOcSettlementColVisible('basePrice')"
              label="基本单价"
              v-bind="ocSettlementColBinding('basePrice')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">基本单价</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.unitPrice ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="baseQty"
              v-if="isOcSettlementColVisible('baseQty')"
              label="基本数量"
              v-bind="ocSettlementColBinding('baseQty')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">基本数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.settlementQuantity ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="packageCount"
              v-if="isOcSettlementColVisible('packageCount')"
              label="包数（去重）"
              v-bind="ocSettlementColBinding('packageCount')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">包数（去重）</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number"><div v-if="row.goodsType === 'quota_package'" class="ulc-cell-stack settlement-cell-stack">
                  <strong>{{ row.packageCount ?? 0 }} 包</strong>
                  <span class="settlement-cell-meta" :title="`本行包内基本数量 ${row.packageBaseQuantity}，每包 ${row.packageQuantity} ${row.unit}`">{{ row.packageBaseQuantity }} / {{ row.packageQuantity }} {{ row.unit }}</span>
                </div>
                <span v-else>0</span></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="looseQty"
              v-if="isOcSettlementColVisible('looseQty')"
              label="散货数量"
              v-bind="ocSettlementColBinding('looseQty')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">散货数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.looseQuantity ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="highValueQty"
              v-if="isOcSettlementColVisible('highValueQty')"
              label="高值数量"
              v-bind="ocSettlementColBinding('highValueQty')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">高值数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.highValueQuantity ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="amount"
              v-if="isOcSettlementColVisible('amount')"
              label="金额"
              v-bind="ocSettlementColBinding('amount')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">金额</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number"><div class="ulc-cell-stack settlement-cell-stack">
                  <strong :title="String(row.settlementAmount ?? '-')">{{ row.settlementAmount ?? '-' }}</strong>
                  <span v-if="row.goodsType === 'quota_package'" class="settlement-cell-meta" :title="`整包金额：${row.packageAmount ?? '-'}`">整包金额：{{ row.packageAmount ?? '-' }}</span>
                </div></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="medicalInsurance"
              v-if="isOcSettlementColVisible('medicalInsurance')"
              label="带量 / 医保"
              v-bind="ocSettlementColBinding('medicalInsurance')"
            >
              <template #header>
                <span class="ulc-th-text">带量 / 医保</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack settlement-cell-stack settlement-policy-list">
                  <span><b>带量</b>{{ row.volumeBased || '-' }}<small>{{ row.volumeBasedType || '-' }}</small></span>
                  <span><b>医保</b>{{ row.medicalInsurancePayment || '-' }}<small>{{ row.medicalInsurancePaymentType || '-' }}</small></span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="traceCode"
              v-if="isOcSettlementColVisible('traceCode')"
              label="追溯码"
              v-bind="ocSettlementColBinding('traceCode')"
            >
              <template #header>
                <span class="ulc-th-text">追溯码</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack settlement-cell-stack settlement-code-list">
                  <span :title="String(row.uid || '-')"><b>UID</b>{{ row.uid || '-' }}</span>
                  <span :title="String(row.uniqueCode || '-')"><b>唯一码</b>{{ row.uniqueCode || '-' }}</span>
                  <span :title="String(row.quotaPackageCode || '-')"><b>定数包</b>{{ row.quotaPackageCode || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="patientDept"
              v-if="isOcSettlementColVisible('patientDept')"
              label="患者 / 财务科室"
              v-bind="ocSettlementColBinding('patientDept')"
            >
              <template #header>
                <span class="ulc-th-text">患者 / 财务科室</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack settlement-cell-stack">
                  <strong :title="String(row.patientName || '-')">患者：{{ row.patientName || '-' }}</strong>
                  <span class="settlement-cell-meta" :title="String(row.financeDepartment || '-')">财务科室：{{ row.financeDepartment || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="settlementInfo"
              v-if="isOcSettlementColVisible('settlementInfo')"
              label="结算信息"
              v-bind="ocSettlementColBinding('settlementInfo')"
            >
              <template #header>
                <span class="ulc-th-text">结算信息</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack settlement-cell-stack">
                  <strong>{{ row.settlementOperator || '-' }}</strong>
                  <span class="settlement-cell-meta">申领：{{ formatDateTime(row.requisitionTime) }}</span>
                  <span class="settlement-cell-meta">结算：{{ row.settlementTime || '-' }}</span>
                  <span class="settlement-cell-meta">创建：{{ row.createTime || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isOcSettlementColVisible('status')"
              label="状态"
              v-bind="ocSettlementColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip
                  :tone="row.status === 'pending_confirm' ? 'warning' : 'success'"
                  :label="formatStatusText(row.status)"
                />
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无结算明细" />
            </template>
          </el-table>
        </div>
      </MdDataTableCard>
      <div v-else-if="type === 'red-flush'" class="table-scroll">
        <div class="settlement-query">
          <form class="settlement-filters" aria-label="红冲记录查询" @submit.prevent="applyRedFlushFilters">
            <label><span>科室</span>
              <select v-model="redFlushFilters.deptCode" class="md-select" @change="redFlushFilters.warehouseId = ''">
                <option value="">全部授权科室</option>
                <option v-for="dept in redFlushOptions.departments" :key="dept.deptCode" :value="dept.deptCode">{{ dept.deptName }}</option>
              </select>
            </label>
            <label><span>库房</span>
              <select v-model="redFlushFilters.warehouseId" class="md-select" :disabled="!redFlushFilters.deptCode || !redFlushWarehouses.length">
                <option value="">{{ !redFlushFilters.deptCode ? '请先选择科室' : redFlushWarehouses.length ? '该科室全部库房' : '该科室暂无关联库房' }}</option>
                <option v-for="warehouse in redFlushWarehouses" :key="warehouse.warehouseId" :value="String(warehouse.warehouseId)">{{ warehouse.warehouseName }}</option>
              </select>
            </label>
            <div class="settlement-query-actions">
              <SpdButton label="查询" tone="primary" type="submit">
                <template #icon><Search :size="16" /></template>
              </SpdButton>
              <SpdButton label="重置" tone="neutral" @click="resetRedFlushFilters">
                <template #icon><RefreshCw :size="16" /></template>
              </SpdButton>
            </div>
          </form>
          <StatusMessage :message="redFlushError" tone="error" role="alert" />
        </div>
              <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="ocRedFlushColSettingsAnchorRef"
           
           
            @click="ocRedFlushCols.settingsOpen.value = !ocRedFlushCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="ocRedFlushCols.settingsOpen.value"
ref="ocRedFlushColSettingsPopRef"
:class="{ 'is-align-right': ocRedFlushColSettingsPop.align.value === 'right', 'is-align-up': ocRedFlushColSettingsPop.vertical.value === 'up' }"
:style="ocRedFlushColSettingsPop.popStyle.value"
:columns="ocRedFlushCols.columns.value"
:visible-column-count="ocRedFlushCols.visibleColumnCount.value"
@toggle="ocRedFlushCols.setColumnVisible"
@move="ocRedFlushCols.moveColumn"
@reset="ocRedFlushCols.resetColumns()"
@confirm="ocRedFlushCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="ocRedFlushTableRef"
            :data="rows"
            row-key="bizNo"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightOcRedFlush.maxHeight"
            style="width: 100%"
            :empty-text="'暂无红冲记录'"
            @header-dragend="(width: number, oldWidth: number, column: any) => ocRedFlushCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="bizNo"
              v-if="isOcRedFlushColVisible('bizNo')"
              label="红冲单号"
              v-bind="ocRedFlushColBinding('bizNo')"
            >
              <template #header>
                <span class="ulc-th-text">红冲单号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.bizNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="sourceNo"
              v-if="isOcRedFlushColVisible('sourceNo')"
              label="原消耗单号"
              v-bind="ocRedFlushColBinding('sourceNo')"
            >
              <template #header>
                <span class="ulc-th-text">原消耗单号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.sourceNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="deptName"
              v-if="isOcRedFlushColVisible('deptName')"
              label="科室"
              v-bind="ocRedFlushColBinding('deptName')"
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
              v-if="isOcRedFlushColVisible('warehouseName')"
              label="库房"
              v-bind="ocRedFlushColBinding('warehouseName')"
            >
              <template #header>
                <span class="ulc-th-text">库房</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.warehouseName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="flushType"
              v-if="isOcRedFlushColVisible('flushType')"
              label="红冲类型"
              v-bind="ocRedFlushColBinding('flushType')"
            >
              <template #header>
                <span class="ulc-th-text">红冲类型</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.flushType === 'reverse_consumption' ? '反消耗' : formatBusinessText(row.flushType) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isOcRedFlushColVisible('status')"
              label="状态"
              v-bind="ocRedFlushColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.flushType === 'reverse_consumption' && row.status === 'approved' ? '已冲销' : formatStatusText(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isOcRedFlushColVisible('createTime')"
              label="红冲时间"
              v-bind="ocRedFlushColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">红冲时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatDateTime(row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="remark"
              v-if="isOcRedFlushColVisible('remark')"
              label="备注"
              v-bind="ocRedFlushColBinding('remark')"
            >
              <template #header>
                <span class="ulc-th-text">备注</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.remark === 'reverse consumption red flush' ? '反消耗红冲' : row.remark || '-' }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无红冲记录" />
            </template>
          </el-table>
        </div>
      </MdDataTableCard>
      </div>
      <template v-else-if="isRecallPage">
        <div class="settlement-query">
          <form class="settlement-filters" aria-label="召回与隔离记录查询" @submit.prevent="applyRecallHistoryFilters">
            <label><span>开始日期</span><input v-model="recallHistoryFilters.dateFrom" type="date" class="md-input" :max="recallHistoryFilters.dateTo || undefined" /></label>
            <label><span>结束日期</span><input v-model="recallHistoryFilters.dateTo" type="date" class="md-input" :min="recallHistoryFilters.dateFrom || undefined" /></label>
            <label><span>商品名称</span><input v-model.trim="recallHistoryFilters.productName" type="search" class="md-input" /></label>
            <label><span>商品编码</span><input v-model.trim="recallHistoryFilters.productCode" type="search" class="md-input" /></label>
            <label><span>来源科室</span><input v-model.trim="recallHistoryFilters.deptName" type="search" class="md-input" /></label>
            <div class="settlement-query-actions">
              <SpdButton label="查询" tone="primary" type="submit">
                <template #icon><Search :size="16" /></template>
              </SpdButton>
              <SpdButton label="重置" tone="neutral" @click="resetRecallHistoryFilters">
                <template #icon><RefreshCw :size="16" /></template>
              </SpdButton>
            </div>
          </form>
          <StatusMessage :message="recallHistoryError" tone="error" role="alert" />
        </div>
                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="ocRecallHistoryColSettingsAnchorRef"
             
             
              @click="ocRecallHistoryCols.settingsOpen.value = !ocRecallHistoryCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="ocRecallHistoryCols.settingsOpen.value"
ref="ocRecallHistoryColSettingsPopRef"
:class="{ 'is-align-right': ocRecallHistoryColSettingsPop.align.value === 'right', 'is-align-up': ocRecallHistoryColSettingsPop.vertical.value === 'up' }"
:style="ocRecallHistoryColSettingsPop.popStyle.value"
:columns="ocRecallHistoryCols.columns.value"
:visible-column-count="ocRecallHistoryCols.visibleColumnCount.value"
@toggle="ocRecallHistoryCols.setColumnVisible"
@move="ocRecallHistoryCols.moveColumn"
@reset="ocRecallHistoryCols.resetColumns()"
@confirm="ocRecallHistoryCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="ocRecallHistoryTableRef"
              :data="rows"
              row-key="bizNo"
              border
              stripe
              class="ulc-table is-sticky"
              :max-height="fillHeightOcRecallHistory.maxHeight"
              style="width: 100%"
              :empty-text="'暂无召回与隔离记录'"
              @header-dragend="(width: number, oldWidth: number, column: any) => ocRecallHistoryCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="bizNo"
                v-if="isOcRecallHistoryColVisible('bizNo')"
                label="单据号"
                v-bind="ocRecallHistoryColBinding('bizNo')"
              >
                <template #header>
                  <span class="ulc-th-text">单据号</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.bizNo }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="businessType"
                v-if="isOcRecallHistoryColVisible('businessType')"
                label="类型"
                v-bind="ocRecallHistoryColBinding('businessType')"
              >
                <template #header>
                  <span class="ulc-th-text">类型</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.businessType === 'isolate' ? '库存隔离' : '产品召回' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productCode"
                v-if="isOcRecallHistoryColVisible('productCode')"
                label="商品编码"
                v-bind="ocRecallHistoryColBinding('productCode')"
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
                v-if="isOcRecallHistoryColVisible('productName')"
                label="商品名称"
                v-bind="ocRecallHistoryColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.productName }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="sourceDept"
                v-if="isOcRecallHistoryColVisible('sourceDept')"
                label="来源科室"
                v-bind="ocRecallHistoryColBinding('sourceDept')"
              >
                <template #header>
                  <span class="ulc-th-text">来源科室</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.sourceDepartments || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="recordWarehouse"
                v-if="isOcRecallHistoryColVisible('recordWarehouse')"
                label="记录库房"
                v-bind="ocRecallHistoryColBinding('recordWarehouse')"
              >
                <template #header>
                  <span class="ulc-th-text">记录库房</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.warehouseName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="quantity"
                v-if="isOcRecallHistoryColVisible('quantity')"
                label="数量"
                v-bind="ocRecallHistoryColBinding('quantity')"
              >
                <template #header>
                  <span class="ulc-th-text">数量</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.quantity }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="status"
                v-if="isOcRecallHistoryColVisible('status')"
                label="状态"
                v-bind="ocRecallHistoryColBinding('status')"
              >
                <template #header>
                  <span class="ulc-th-text">状态</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ formatStatusText(row.status) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="createTime"
                v-if="isOcRecallHistoryColVisible('createTime')"
                label="登记时间"
                v-bind="ocRecallHistoryColBinding('createTime')"
              >
                <template #header>
                  <span class="ulc-th-text">登记时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ formatDateTime(row.createTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="receivedTime"
                v-if="isOcRecallHistoryColVisible('receivedTime')"
                label="签收时间"
                v-bind="ocRecallHistoryColBinding('receivedTime')"
              >
                <template #header>
                  <span class="ulc-th-text">签收时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ formatDateTime(row.receivedTime) }}</span>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无召回与隔离记录" />
              </template>
            </el-table>
          </div>
        </MdDataTableCard>
      </template>
      <div v-else>
        <MdDataTableCard>
          <template #toolbar>
            <div class="ulc-col-settings">
              <SpdButton label="列设置" tone="neutral" ref="ocClosureDocColSettingsAnchorRef"
               
               
                @click="ocClosureDocCols.settingsOpen.value = !ocClosureDocCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
              <SpdColumnSettings
v-if="ocClosureDocCols.settingsOpen.value"
ref="ocClosureDocColSettingsPopRef"
:class="{ 'is-align-right': ocClosureDocColSettingsPop.align.value === 'right', 'is-align-up': ocClosureDocColSettingsPop.vertical.value === 'up' }"
:style="ocClosureDocColSettingsPop.popStyle.value"
:columns="ocClosureDocCols.columns.value"
:visible-column-count="ocClosureDocCols.visibleColumnCount.value"
@toggle="ocClosureDocCols.setColumnVisible"
@move="ocClosureDocCols.moveColumn"
@reset="ocClosureDocCols.resetColumns()"
@confirm="ocClosureDocCols.settingsOpen.value = false"
          />
            </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="ocClosureDocTableRef"
              :data="rows"
              row-key="bizNo"
              border
              stripe
              class="ulc-table is-sticky"
              :max-height="fillHeightOcClosureDoc.maxHeight"
              style="width: 100%"
              :empty-text="'暂无业务单据'"
              @header-dragend="(width: number, oldWidth: number, column: any) => ocClosureDocCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="bizNo"
                v-if="isOcClosureDocColVisible('bizNo')"
                :label="type === 'consumption' ? '消耗单号' : '单据号'"
                v-bind="ocClosureDocColBinding('bizNo')"
              >
                <template #header>
                  <span class="ulc-th-text">{{ type === 'consumption' ? '消耗单号' : '单据号' }}</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.bizNo }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="requisitionType"
                v-if="type === 'consumption' && isOcClosureDocColVisible('requisitionType')"
                label="申领类型"
                v-bind="ocClosureDocColBinding('requisitionType')"
              >
                <template #header>
                  <span class="ulc-th-text">申领类型</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.requisitionTypeLabel || row.requisitionType || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="deptWarehouse"
                v-if="isOcClosureDocColVisible('deptWarehouse')"
                :label="type === 'consumption' ? '科室 / 库房' : '来源/科室'"
                v-bind="ocClosureDocColBinding('deptWarehouse')"
              >
                <template #header>
                  <span class="ulc-th-text">{{ type === 'consumption' ? '科室 / 库房' : '来源/科室' }}</span>
                </template>
                <template #default="{ row }">
                  <template v-if="type === 'consumption'">
                    <span class="ulc-cell-stack">
                      <strong>{{ row.deptName || '-' }}</strong>
                      <span class="muted-cell"><Building2 :size="13" />{{ row.warehouseName || '-' }}</span>
                    </span>
                  </template>
                  <template v-else>{{ row.deptName || row.sourceNo || row.supplierName || row.deviceNo || formatBusinessText(row.eventType) }}</template>
                </template>
              </el-table-column>
              <el-table-column
                column-key="productCode"
                v-if="isOcClosureDocColVisible('productCode')"
                label="商品编码"
                v-bind="ocClosureDocColBinding('productCode')"
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
                v-if="isOcClosureDocColVisible('productName')"
                label="商品名称"
                v-bind="ocClosureDocColBinding('productName')"
              >
                <template #header>
                  <span class="ulc-th-text">商品名称</span>
                </template>
                <template #default="{ row }">
                  <template v-if="type === 'consumption'"><strong>{{ row.productName || '-' }}</strong></template>
                  <template v-else>{{ row.productName || row.period || formatBusinessText(row.operationType) }}</template>
                </template>
              </el-table-column>
              <el-table-column
                column-key="quantity"
                v-if="isOcClosureDocColVisible('quantity')"
                :label="type === 'consumption' ? '数量 / 成本金额' : '数量/金额'"
                v-bind="ocClosureDocColBinding('quantity')"
              >
                <template #header>
                  <span class="ulc-th-text">{{ type === 'consumption' ? '数量 / 成本金额' : '数量/金额' }}</span>
                </template>
                <template #default="{ row }">
                  <template v-if="type === 'shortage'">
                    <span class="ulc-cell-stack">
                      <strong>{{ row.quantity || '-' }}</strong>
                      <span class="muted-cell">周期 {{ row.periodDays || '-' }} 天 / 建议 {{ row.formulaReplenishQty ?? '-' }}</span>
                      <span v-if="Number(row.manualAdjusted || 0) === 1" class="muted-cell">人工调整</span>
                    </span>
                  </template>
                  <template v-else-if="type === 'consumption'">
                    <span class="ulc-cell-stack">
                      <strong>{{ row.quantity ?? '-' }}</strong>
                      <span class="muted-cell">成本金额：{{ row.amount ?? '-' }}</span>
                    </span>
                  </template>
                  <template v-else>{{ row.quantity || row.amount || '-' }}</template>
                </template>
              </el-table-column>
              <el-table-column
                column-key="status"
                v-if="isOcClosureDocColVisible('status')"
                label="状态"
                v-bind="ocClosureDocColBinding('status')"
              >
                <template #header>
                  <span class="ulc-th-text">状态</span>
                </template>
                <template #default="{ row }">
                  <SpdStatusChip
                    :tone="type === 'consumption'
                      ? (row.status === 'confirmed'
                          ? 'success'
                          : ['reversed', 'red_flushed'].includes(String(row.status)) ? 'neutral' : 'warning')
                      : 'warning'"
                    :label="formatStatusText(row.status)"
                  />
                </template>
              </el-table-column>
              <el-table-column
                column-key="createTime"
                v-if="isOcClosureDocColVisible('createTime')"
                label="时间"
                v-bind="ocClosureDocColBinding('createTime')"
              >
                <template #header>
                  <span class="ulc-th-text">时间</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="actions"
                v-if="isOcClosureDocColVisible('actions')"
                label="操作"
                v-bind="ocClosureDocColBinding('actions')"
                :fixed="ocClosureDocActionFixed"
                align="center"
              >
                <template #header>
                  <span class="ulc-th-text">操作</span>
                </template>
                <template #default="{ row }">
                  <div class="ulc-actions">
                    <button v-if="type === 'consumption' && row.status === 'confirmed' && auth.hasPermission('reverse-consumption:write')"
                      class="ulc-action" type="button" :disabled="reversingConsumption" @click="confirmConsumptionReversal(row)">
                      <RotateCcw :size="15" />
                      反消耗
                    </button>
                    <button v-if="type === 'requisition' && row.status === 'pending_approval'" class="ulc-action" type="button" @click="runAction('approveRequisition', row)">
                      <CheckCircle2 :size="15" />审批
                    </button>
                    <button v-if="type === 'requisition' && row.status === 'pending_approval'" class="ulc-action" type="button" @click="runAction('rejectRequisition', row)">
                      驳回
                    </button>
                    <span v-if="type !== 'consumption'">已联动</span>
                  </div>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无业务单据" />
              </template>
            </el-table>
          </div>
        </MdDataTableCard>
      </div>
      <div class="oc-closure-pagination">
        <MdPagination
          :page="currentPage"
          :total-pages="Math.max(1, Math.ceil(totalItems / Math.max(pageSize, 1)))"
          :total="totalItems"
          :size="pageSize"
          :loading="loading"
          @update:page="changePage"
          @update:size="changePageSize"
        />
      </div>
    </section>

    <HighValueOperationsPanel v-if="type === 'high-value'" />

    <div v-if="analysisDialogOpen" class="attachment-preview-mask" @click.self="analysisDialogOpen = false">
      <section class="supplier-dialog replenishment-analysis-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>{{ analysisMeta?.deptName || form.deptName }} / {{ analysisMeta?.warehouseName || form.warehouseName }}</p>
            <h3>智能补货分析</h3>
          </div>
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭" @click="analysisDialogOpen = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
        </header>

        <div class="analysis-summary">
          <span>分析周期：{{ analysisMeta?.selectedPeriodDays || effectiveReplenishmentDays }} 天</span>
          <span>公式：周期内科室出库数量 - 当前库房库存</span>
          <span>结果：{{ analysisRows.length }} 条建议</span>
        </div>
        <StatusMessage :message="analysisError" tone="error" />

                <MdDataTableCard>
          <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="ocReplenishmentAnalysisColSettingsAnchorRef"
             
             
              @click="ocReplenishmentAnalysisCols.settingsOpen.value = !ocReplenishmentAnalysisCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <SpdColumnSettings
v-if="ocReplenishmentAnalysisCols.settingsOpen.value"
ref="ocReplenishmentAnalysisColSettingsPopRef"
:class="{ 'is-align-right': ocReplenishmentAnalysisColSettingsPop.align.value === 'right', 'is-align-up': ocReplenishmentAnalysisColSettingsPop.vertical.value === 'up' }"
:style="ocReplenishmentAnalysisColSettingsPop.popStyle.value"
:columns="ocReplenishmentAnalysisCols.columns.value"
:visible-column-count="ocReplenishmentAnalysisCols.visibleColumnCount.value"
@toggle="ocReplenishmentAnalysisCols.setColumnVisible"
@move="ocReplenishmentAnalysisCols.moveColumn"
@reset="ocReplenishmentAnalysisCols.resetColumns()"
@confirm="ocReplenishmentAnalysisCols.settingsOpen.value = false"
          />
          </div>
          </template>
          <div class="ulc-table-wrap">
            <el-table
              ref="ocReplenishmentAnalysisTableRef"
              :data="analysisRows"
              border
              stripe
              class="ulc-table is-sticky"
              :max-height="fillHeightOcReplenishmentAnalysis.maxHeight"
              style="width: 100%"
              :empty-text="'暂无补货分析数据'"
              @header-dragend="(width: number, oldWidth: number, column: any) => ocReplenishmentAnalysisCols.handleHeaderDragEnd(width, oldWidth, column)"
            >
              <el-table-column
                column-key="product"
                v-if="isOcReplenishmentAnalysisColVisible('product')"
                label="商品"
                v-bind="ocReplenishmentAnalysisColBinding('product')"
              >
                <template #header>
                  <span class="ulc-th-text">商品</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-cell-stack">
                    <strong>{{ row.productName || '-' }}</strong>
                    <span class="muted-cell">{{ row.productCode || '-' }}</span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="deptWarehouse"
                v-if="isOcReplenishmentAnalysisColVisible('deptWarehouse')"
                label="科室 / 库房"
                v-bind="ocReplenishmentAnalysisColBinding('deptWarehouse')"
              >
                <template #header>
                  <span class="ulc-th-text">科室 / 库房</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-cell-stack">
                    <strong>{{ row.deptName || '-' }}</strong>
                    <span class="muted-cell">{{ row.warehouseName || '-' }}</span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="out5"
                v-if="isOcReplenishmentAnalysisColVisible('out5')"
                label="5 天出库"
                v-bind="ocReplenishmentAnalysisColBinding('out5')"
              >
                <template #header>
                  <span class="ulc-th-text">5 天出库</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.issue5 ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="out7"
                v-if="isOcReplenishmentAnalysisColVisible('out7')"
                label="7 天出库"
                v-bind="ocReplenishmentAnalysisColBinding('out7')"
              >
                <template #header>
                  <span class="ulc-th-text">7 天出库</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.issue7 ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="out15"
                v-if="isOcReplenishmentAnalysisColVisible('out15')"
                label="15 天出库"
                v-bind="ocReplenishmentAnalysisColBinding('out15')"
              >
                <template #header>
                  <span class="ulc-th-text">15 天出库</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.issue15 ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="out30"
                v-if="isOcReplenishmentAnalysisColVisible('out30')"
                label="30 天出库"
                v-bind="ocReplenishmentAnalysisColBinding('out30')"
              >
                <template #header>
                  <span class="ulc-th-text">30 天出库</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.issue30 ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="currentStock"
                v-if="isOcReplenishmentAnalysisColVisible('currentStock')"
                label="当前库存"
                v-bind="ocReplenishmentAnalysisColBinding('currentStock')"
              >
                <template #header>
                  <span class="ulc-th-text">当前库存</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text">{{ row.currentQty ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="suggestedReplenishment"
                v-if="isOcReplenishmentAnalysisColVisible('suggestedReplenishment')"
                label="建议补货"
                v-bind="ocReplenishmentAnalysisColBinding('suggestedReplenishment')"
              >
                <template #header>
                  <span class="ulc-th-text">建议补货</span>
                </template>
                <template #default="{ row }">
                  <span class="ulc-wrap-text"><input
                    class="analysis-qty-input"
                    type="number"
                    min="0"
                    :value="row.recommendedQty ?? 0"
                    @input="updateAnalysisQty(row, $event)"
                  />
                  <span class="muted-cell">{{ row.formulaText }}</span></span>
                </template>
              </el-table-column>
              <el-table-column
                column-key="actions"
                v-if="isOcReplenishmentAnalysisColVisible('actions')"
                label="操作"
                v-bind="ocReplenishmentAnalysisColBinding('actions')"
                :fixed="ocReplenishmentAnalysisActionFixed"
                align="center"
              >
                <template #header>
                  <span class="ulc-th-text">操作</span>
                </template>
                <template #default="{ row }">
                <div class="ulc-actions">
                  <button @click="createTaskFromAnalysis(row)" class="ulc-action btn-primary" type="button"><Save :size="15" />
                    生成任务</button>
                </div>
                </template>
              </el-table-column>
              <template #empty>
                <MdEmptyState text="暂无补货分析数据" />
              </template>
            </el-table>
          </div>
        </MdDataTableCard>
      </section>
    </div>

    <ElDialog v-model="packageDetailOpen" title="定数包明细" width="min(960px, 94vw)"
      :show-close="false" destroy-on-close append-to-body>
      <section class="package-detail-content">
        <header>
          <div>
            <p>定数包明细</p>
            <h3>{{ packageDetail?.labelNo || '加载中...' }}</h3>
          </div>
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭" @click="closePackageDetail">
          <template #icon><X :size="18" /></template>
        </SpdButton>
        </header>
        <EmptyState v-if="packageDetailLoading" message="正在加载定数包明细..." />
        <template v-else-if="packageDetail">
          <div class="package-detail-info">
            <label><span>定数包编码</span><strong>{{ packageDetail.labelNo }}</strong></label>
            <label><span>模板</span><strong>{{ packageDetail.templateCode }} / {{ packageDetail.templateName }}</strong></label>
            <label><span>商品</span><strong>{{ packageDetail.productCode }} / {{ packageDetail.productName }}（{{ packageDetail.specModel }}）</strong></label>
            <label><span>包内数量</span><strong>{{ packageDetail.packageQuantity }} {{ packageDetail.unit }}</strong></label>
            <label><span>库房</span><strong>{{ packageDetail.warehouseName }}（{{ packageDetail.warehouseType }}）</strong></label>
            <label><span>状态</span><strong>{{ formatStatusText(packageDetail.status) }} · 打印 {{ packageDetail.printCount }} 次</strong></label>
            <label><span>生成时间</span><strong>{{ packageDetail.createTime }}</strong></label>
            <label><span>修改时间</span><strong>{{ packageDetail.updateTime || '-' }}</strong></label>
          </div>

          <div class="section-title compact">
            <h3>来源批次明细</h3>
          </div>
          <div class="table-scroll">
            <table class="master-table purchase-table">
              <thead>
                <tr>
                  <th>系统批次</th>
                  <th>生产批号</th>
                  <th>有效期</th>
                  <th>来源数量</th>
                  <th>批次单价</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!packageDetail.sources.length">
                  <td colspan="5" class="approval-empty">暂无来源批次数据</td>
                </tr>
                <tr v-for="source in packageDetail.sources" :key="`${source.batchId}`">
                  <td>{{ source.systemBatchNo || '-' }}</td>
                  <td>{{ source.productionBatchNo || '-' }}</td>
                  <td>{{ source.expireDate || '-' }}</td>
                  <td>{{ source.sourceQty }}</td>
                  <td>¥ {{ Number(source.unitPrice).toFixed(2) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="packageDetail.bindings.length" class="section-title compact">
            <h3>绑定去向</h3>
          </div>
          <div v-if="packageDetail.bindings.length" class="table-scroll">
            <table class="master-table purchase-table">
              <thead>
                <tr>
                  <th>配送单号</th>
                  <th>申领单号</th>
                  <th>包内数量</th>
                  <th>绑定时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="binding in packageDetail.bindings" :key="binding.deliveryNo">
                  <td>{{ binding.deliveryNo }}</td>
                  <td>{{ binding.requisitionNo || '-' }}</td>
                  <td>{{ binding.packageQuantity }}</td>
                  <td>{{ binding.createTime }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="packageDetail.events.length" class="section-title compact">
            <h3>事件流水</h3>
          </div>
          <div v-if="packageDetail.events.length" class="table-scroll">
            <table class="master-table purchase-table">
              <thead>
                <tr>
                  <th>事件编号</th>
                  <th>事件类型</th>
                  <th>状态变化</th>
                  <th>数量变化</th>
                  <th>备注</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="event in packageDetail.events" :key="event.eventNo">
                  <td>{{ event.eventNo }}</td>
                  <td>{{ formatBusinessText(event.eventType) }}</td>
                  <td>{{ formatStatusText(event.statusBefore) }} → {{ formatStatusText(event.statusAfter) }}</td>
                  <td>{{ event.qtyChange }}</td>
                  <td>{{ event.remark || '-' }}</td>
                  <td>{{ event.createTime }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </section>
    </ElDialog>
  </section>
</template>

<style scoped>
.settlement-query {
  padding: 12px 16px;
}
/* 结算 / 拣配 / 红冲 / 召回 各筛选区统一样式 */
.settlement-filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(170px, 100%), 1fr));
  gap: 10px 12px;
}
.settlement-filters label {
  display: grid;
  gap: 5px;
  min-width: 0;
}
.settlement-query-actions {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}
.settlement-filters span,
.settlement-query-hint { color: var(--md-text-secondary, var(--spd-p-text-secondary)); font-size: var(--spd-font-size-sm); }
.settlement-query-hint { margin: 8px 0 0; }
/* =====================================================================
   拣配配送 delivery-acceptance-page
   ---------------------------------------------------------------------
   目标：对齐采购管理 / 收货验收 / 库存管理的 md-* 视觉语言。
     1. 页头改用 MdPageHeader（渐变底 + 白色标题）
     2. 操作卡片单卡片承载：查询区 + 拣配工作台，避免多层边框割裂
     3. 表格 md 密度：表头 12px、表体 13px、sticky 表头
     4. 首列 / 末列（操作列）sticky，横向滚动时保持可见

   注意：.master-table 的 padding / font-size / color 由
   spd-override.css 以 !important 统一设定；本页规则统一加
   .delivery-acceptance-page 前缀提高特指度，确保覆盖生效。
   ===================================================================== */

.delivery-acceptance-page {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: none;
  gap: 14px !important;
  padding: 16px 20px 24px;
}

.delivery-acceptance-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

/* ---------- 操作卡片：查询 + 拣配工作台合并为一块 ---------- */
.delivery-acceptance-page > .delivery-workspace-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 18px 18px;
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
}

.delivery-acceptance-page .delivery-section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
  color: var(--md-primary, var(--primary));
}

.delivery-acceptance-page .delivery-section-title h3 {
  margin: 0;
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

.delivery-acceptance-page .delivery-section-description {
  margin: 2px 0 0;
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
}

/* ---------- 查询区 ----------
   筛选/表单区已统一改用 SpdFilterBar 组件，
   栅格列数 / 换行规则 / 控件高度 / 操作区位置全部由
   styles/components/spd-filter-bar.css 的「标准规则」统一控制，
   本页不再覆写列数、间距与标签排版（避免各页各写一套）。 */

/* ---------- 动作行 ---------- */
.delivery-acceptance-page .delivery-action-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding-top: 12px;
  border-top: 1px dashed var(--md-border-subtle, var(--spd-border-subtle));
}

.delivery-acceptance-page .delivery-action-hint {
  margin-right: auto;
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: var(--spd-font-size-sm);
  font-variant-numeric: tabular-nums;
}

.delivery-acceptance-page .delivery-action-row .btn {
  min-height: 32px;
  padding: 0 14px;
  font-size: var(--spd-font-size-base);
  border-radius: var(--spd-radius-sm, 6px);
}

/* ---------- 拣配工作台：单列三段式 ---------- */
.delivery-acceptance-page .picking-workbench {
  display: flex;
  flex-direction: column;
  grid-template-columns: none;
  gap: 12px;
}

.delivery-acceptance-page .picking-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
  min-width: 0;
}

.delivery-acceptance-page .picking-panel .section-title.compact {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
}

.delivery-acceptance-page .picking-panel .section-title.compact h3 {
  margin: 0;
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

.delivery-acceptance-page .picking-panel .muted-hint {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-xs);
  font-variant-numeric: tabular-nums;
}

/* 扫码条：定数包类型 */
.delivery-acceptance-page .picking-package-scan {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.delivery-acceptance-page .picking-package-scan input {
  flex: 1;
  min-width: 0;
  min-height: 32px;
  height: 32px;
  padding: 0 11px;
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
  font-size: var(--spd-font-size-base);
}

.delivery-acceptance-page .picking-package-scan .btn {
  min-height: 32px;
  padding: 0 14px;
  font-size: var(--spd-font-size-base);
  white-space: nowrap;
  border-radius: var(--spd-radius-sm, 6px);
}

/* ---------- 表格：md 密度 + sticky ---------- */
.delivery-acceptance-page .picking-panel .table-scroll {
  max-height: max(280px, calc(100dvh - 460px));
  overflow: auto;
  border: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
}

.delivery-acceptance-page .picking-panel .purchase-detail-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  min-width: 920px;
}

.delivery-acceptance-page .picking-panel .purchase-detail-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 36px;
  padding: 9px 12px !important;
  background: var(--md-bg-subtle, var(--spd-p-bg-subtle)) !important;
  color: var(--md-text-secondary, var(--spd-text-secondary)) !important;
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 700 !important;
  white-space: nowrap;
  border-bottom: 1px solid var(--md-border, var(--spd-border-subtle)) !important;
}

.delivery-acceptance-page .picking-panel .purchase-detail-table tbody td {
  height: 40px;
  padding: 9px 12px !important;
  color: var(--md-text, var(--spd-text-primary)) !important;
  font-size: var(--spd-font-size-base) !important;
  vertical-align: middle;
  background: var(--md-bg-card, var(--spd-p-bg)) !important;
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle)) !important;
}

.delivery-acceptance-page .picking-panel .purchase-detail-table tbody tr:nth-child(even) td {
  background: color-mix(in srgb, var(--md-bg-subtle, var(--spd-p-bg-subtle)) 46%, var(--spd-p-bg)) !important;
}

.delivery-acceptance-page .picking-panel .purchase-detail-table tbody tr:hover td {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 6%, var(--spd-p-bg)) !important;
}

/* 首列（单号）与末列（操作）冻结，横向滚动时保持可见 */
.delivery-acceptance-page .picking-panel--requisition .table-scroll {
  border: none;
  border-radius: 0;
}

.delivery-acceptance-page :is(.picking-panel--requisition, .picking-panel--loose) :is(th, td):last-child {
  position: sticky;
  right: 0;
  z-index: 1;
  background: var(--md-bg-card, var(--spd-p-bg)) !important;
  box-shadow: -1px 0 0 var(--md-border-subtle, var(--spd-border-subtle));
}

.delivery-acceptance-page :is(.picking-panel--requisition, .picking-panel--loose) thead th:last-child {
  z-index: 3;
  background: var(--md-bg-subtle, var(--spd-p-bg-subtle)) !important;
}

.delivery-acceptance-page .picking-panel--requisition tbody tr.selected td {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 8%, var(--spd-p-bg)) !important;
}

.delivery-acceptance-page .picking-panel--requisition tbody tr.selected :is(td:first-child, td:last-child) {
  box-shadow: inset 3px 0 0 var(--md-primary, var(--primary));
}

/* 数量列右对齐 + 等宽数字，便于纵向比对 */
.delivery-acceptance-page .picking-panel .purchase-detail-table td:nth-child(n+5):nth-child(-n+7) {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.delivery-acceptance-page .picking-panel .purchase-detail-table .stocktaking-qty-input {
  width: 92px;
  height: 28px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.delivery-acceptance-page .picking-panel .purchase-detail-table .approval-empty {
  height: 120px !important;
  color: var(--md-text-muted, var(--spd-text-tertiary)) !important;
  text-align: center !important;
}

.delivery-acceptance-page .picking-panel .muted-cell {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-sm);
}

/* ---------- 定数包明细弹窗 ---------- */
.package-detail-content { min-width: 0; max-height: 72vh; overflow: auto; }
.package-detail-content header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.package-detail-content header h3 { overflow-wrap: anywhere; }
.package-detail-content .package-detail-info { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); gap: 12px; margin-block: 16px; }
.package-detail-content .package-detail-info strong { overflow-wrap: anywhere; }
.package-detail-content .table-scroll { overflow: auto; max-height: 260px; margin-block: 12px; }
.package-detail-content .master-table { min-width: 640px; }
.consumption-saas-page {
  --consumption-blue: var(--spd-p-info);
  --consumption-blue-deep: var(--spd-p-info-strong);
  --consumption-green: var(--spd-p-success-text);
  --consumption-text: var(--spd-p-text);
  --consumption-muted: var(--spd-p-text-secondary);
  --consumption-border: var(--spd-p-border);
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: none;
  gap: 14px !important;
  padding: 16px 20px 24px;
  color: var(--consumption-text);
  background: transparent;
  font-family: var(--spd-font-family);
}

.consumption-saas-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

.consumption-saas-page .breadcrumb-line {
  margin: 0;
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-sm);
}

.consumption-saas-page .breadcrumb-line strong {
  color: var(--spd-p-text-secondary);
  font-weight: 500;
}

.consumption-saas-page .detail-heading {
  box-sizing: border-box;
  min-height: 112px;
  margin: 0;
  padding: 20px 24px;
  border: 1px solid var(--consumption-border);
  border-radius: 12px;
  background: linear-gradient(115deg, var(--spd-p-bg) 0%, var(--spd-p-bg) 72%, var(--spd-p-info-bg) 100%);
  box-shadow: var(--spd-p-shadow-1);
}

.consumption-saas-page .detail-heading h2 {
  margin: 6px 0 5px;
  color: var(--spd-p-info-bg);
  font-size: var(--spd-font-size-3xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.consumption-saas-page .detail-heading small {
  color: var(--consumption-muted);
  font-size: var(--spd-font-size-base);
  line-height: var(--spd-lh-loose);
}

/* 消耗登记页的筛选/表单控件已统一改用 SpdFilterBar，
   控件高度由 --spd-p-field-height 统一控制，此处不再另写一套高度。 */
.consumption-saas-page .btn {
  box-sizing: border-box;
  min-height: 40px;
  height: 40px;
  border-radius: 8px;
  font-family: inherit;
}

.consumption-saas-page .btn {
  padding: 0 16px;
  border-color: var(--spd-p-border);
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-bg);
  font-weight: 600;
}

.consumption-saas-page .btn:hover {
  border-color: var(--spd-p-info-border);
  color: var(--consumption-blue);
  background: var(--spd-p-info-bg);
}

.consumption-saas-page .btn-primary {
  border-color: var(--consumption-blue);
  color: var(--spd-p-bg);
  background: var(--consumption-blue);
  box-shadow: var(--spd-p-shadow-1);
}

.consumption-saas-page .btn-primary:hover {
  border-color: var(--consumption-blue-deep);
  color: var(--spd-p-bg);
  background: var(--consumption-blue-deep);
}

.consumption-saas-page .closure-stat-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 12px;
}

.consumption-saas-page .closure-stat-grid article {
  grid-column: span 3;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--consumption-border);
  border-radius: 10px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
}

.consumption-saas-page .closure-stat-grid article > div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.consumption-saas-page .closure-stat-grid article span {
  color: var(--consumption-muted);
  font-size: var(--spd-font-size-sm);
}

.consumption-saas-page .closure-stat-grid article strong {
  color: var(--spd-p-text);
  font-size: var(--spd-font-size-2xl);
  font-weight: 600;
  line-height: var(--spd-lh-tight);
  font-variant-numeric: tabular-nums;
}

.consumption-stat-icon,
.consumption-section-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  color: var(--consumption-blue);
  background: var(--spd-p-info-bg);
  font-style: normal;
}

.consumption-stat-icon.is-confirmed,
.consumption-stat-icon.is-quantity {
  color: var(--consumption-green);
  background: var(--spd-p-success-bg);
}

.consumption-stat-icon.is-reversed {
  color: var(--spd-p-text-muted);
  background: var(--spd-p-bg-muted);
}

.consumption-saas-page .hospital-catalog-panel {
  box-sizing: border-box;
  min-width: 0;
  margin: 0;
  padding: 16px 18px 18px;
  border: 1px solid var(--consumption-border);
  border-radius: 10px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
}

.consumption-section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--consumption-border);
}

.consumption-section-title > div {
  min-width: 0;
}

.consumption-section-title h3 {
  margin: 0;
  color: var(--spd-p-text);
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

.consumption-section-description {
  display: block !important;
  margin: 4px 0 0;
  color: var(--consumption-muted);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
}

.consumption-section-icon.is-history {
  color: var(--consumption-green);
  background: var(--spd-p-success-bg);
}

.consumption-live-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-left: auto;
  padding: 5px 10px;
  border: 1px solid var(--spd-p-success-border);
  border-radius: 999px;
  color: var(--spd-p-success-text);
  background: var(--spd-p-primary-bg);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

.consumption-live-badge > span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--spd-p-success-text);
  box-shadow: var(--spd-p-shadow-1);
}

/* 【筛选栏根元素：不再套用 12 栏栅格】
   历史上这里给 <SpdFilterBar> 根元素写了
     display: grid; grid-template-columns: repeat(12, minmax(0, 1fr));
   这会把组件自身的 display: flex 布局整个替换掉：
   组件的 .spd-filter-bar__fields（auto-fit 栅格）被压成 12 条 106px 的窄轨道，
   字段只能落在唯一一条 1fr 轨道上 → 每个字段仅 106px 宽（低于 160px 下限），
   5 个字段全部竖着叠在一列里（用户报「筛选部分全部叠在一起了」）。

   现在只保留页面需要的「上分隔线 + 顶部留白」，
   字段排布完全交给 SpdFilterBar 自己的 .spd-filter-bar__fields
   （repeat(auto-fit, minmax(160px, 1fr))）按容器宽度自动换行。 */
.consumption-saas-page .consumption-form-grid {
  padding-top: 20px;
  /* 顶部分隔线用标准边框色（--spd-p-border），
     此前用 --spd-p-bg-muted（#eef3f5）覆盖了 <SpdFilterBar> 的
     1px solid var(--spd-p-border) 边框，导致筛选栏上边框偏淡、与其它页面不一致。 */
  border-top: 1px solid var(--spd-p-border);
}

/* 字段标签：与 SpdFilterBar 的标准标签规则（--spd-p-field-label / sm / 600）一致，
   这里只保留页面需要的标签与控件间距，不再另立一套颜色与字号。 */
.consumption-saas-page .consumption-form-grid > label {
  min-width: 0;
}

.consumption-saas-page .consumption-form-grid > label > span {
  margin-bottom: 0;
  color: var(--spd-p-field-label);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

/* 【筛选栏字段宽度】—— 由统一筛选栏组件 SpdFilterBar 的
   .spd-filter-bar__fields 栅格（auto-fit + minmax(160px, 1fr)）接管。
   历史上这里写的是 12 栏模型的 `grid-column: span N`：
   科室 span 3 / 库房 span 3 / 扫码识别耗材 span 6 / 数量 span 2 /
   高值唯一码 span 4。但统一筛选栏的字段栅格**没有 12 栏轨道**，
   span 只能落在唯一那一条 1fr 列上，于是 span 3 → 32px、span 4 → 48px，
   字段被压扁后标签换行、控件互相叠在一起（用户报「叠在一起了」）。
   现在统一交给 auto-fit 栅格按 160px 下限自动换行，
   全部字段宽度一致，不再出现 32px / 48px 的窄条。 */
.consumption-saas-page .consumption-form-grid input,
.consumption-saas-page .consumption-form-grid select {
  width: 100%;
  border-color: var(--spd-p-field-border);
  /* 文字必须是**深色**令牌：此前写的是 --spd-p-field-disabled-border
     （浅色 #d5e0e5 一族），浅底 + 浅字根本看不清 —— 与用户报的
     「浅色背景搭配浅色文字看不清」是同一类问题。 */
  color: var(--spd-p-field-text);
  background-color: var(--spd-p-field-bg);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.consumption-saas-page .consumption-form-grid input:focus,
.consumption-saas-page .consumption-form-grid select:focus {
  border-color: var(--spd-p-field-focus-border);
  outline: 0;
  box-shadow: 0 0 0 var(--spd-p-focus-width) var(--spd-p-field-focus-ring);
}

/* 【已移除】旧的 12 栏 span 声明
   （.consumption-grid-dept / -warehouse / -code / -resolved / -quantity / -unique）
   统一筛选栏 SpdFilterBar 的字段栅格是 auto-fit 模型（没有 12 栏轨道），
   继续写 `grid-column: span N !important` 会把字段压成 32px / 48px 窄条，
   导致筛选区叠在一起。现改为不写 span，由 auto-fit 按 160px 下限自动换行。 */

.consumption-saas-page .consumption-code-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.consumption-saas-page .consumption-resolved {
  box-sizing: border-box;
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid var(--spd-p-success-border);
  border-left: 3px solid var(--consumption-green);
  border-radius: 8px;
  color: var(--spd-p-success-strong);
  background: var(--spd-p-primary-bg);
}

.consumption-saas-page .consumption-resolved small {
  color: var(--spd-p-success);
}

.consumption-action-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--spd-p-bg-muted);
}

.consumption-action-hint {
  margin-right: auto;
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-sm);
}

.consumption-history-card .table-scroll {
  overflow: auto;
  border: 1px solid var(--spd-p-text);
  border-radius: var(--spd-radius-sm, 6px);
}

.consumption-history-table {
  min-width: 1050px;
}

.consumption-history-table thead th {
  height: 36px;
  padding: 9px 12px !important;
  border-bottom-color: var(--spd-p-border) !important;
  color: var(--spd-p-text-secondary) !important;
  background: var(--spd-p-bg-subtle) !important;
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 700 !important;
  white-space: nowrap;
}

.consumption-history-table tbody td {
  height: 40px;
  padding: 9px 12px !important;
  border-bottom-color: var(--spd-p-bg-muted) !important;
  color: var(--spd-p-text-secondary) !important;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-base) !important;
  vertical-align: middle;
}

.consumption-history-table tbody tr:nth-child(even) td {
  background: var(--spd-p-row-stripe) !important;
}

.consumption-history-table tbody tr:hover td {
  background: var(--spd-p-info-bg) !important;
}

.consumption-history-table .muted-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  color: var(--spd-p-text-muted);
}

.consumption-history-table .row-actions button {
  color: var(--consumption-blue-deep);
}

.consumption-saas-page :deep(.pagination-controls) {
  margin-top: 12px;
}

@media (max-width: 1200px) {
  .consumption-saas-page .closure-stat-grid article {
    grid-column: span 6;
  }

  /* 筛选栏字段宽度在窄屏下同样交给统一筛选栏的 auto-fit 栅格，
     不再写 12 栏 span（见上文「已移除」的说明）。 */
}

@media (max-width: 760px) {
  .consumption-saas-page {
    gap: 12px;
    padding: 12px 12px 20px;
  }

  .consumption-saas-page .hospital-catalog-panel {
    padding: 14px;
  }

  .consumption-saas-page .closure-stat-grid article {
    grid-column: span 12;
  }

  .consumption-live-badge,
  .consumption-action-hint {
    display: none;
  }

  .consumption-action-row {
    align-items: stretch;
    flex-direction: column;
  }
}

/* =====================================================================
   结算对账 / 红冲管理 finance-settlement-page
   ---------------------------------------------------------------------
   统一使用 md-header 渐变页头（与 UDI / 高值耗材页面一致）。
   页面外壳 padding / gap 与 PDA / 召回 / 拣配配送对齐。
   顶部关闭业务统计卡（运营 KPI 与结算/财务场景不相关），查询/动作行
   也已隐藏——页面只剩 detail 列表。
   ===================================================================== */
.finance-settlement-page {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: none;
  gap: 14px !important;
  padding: 16px 20px 24px;
}

.finance-settlement-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

/* ---------- 结算 KPI 摘要卡：参考 UDI udi-summary 卡片化 ---------- */
.finance-settlement-page .settlement-kpi-card {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  padding: 4px 0;
  border: 1px solid var(--md-border-soft, var(--spd-p-border-subtle));
  border-radius: var(--spd-radius-md, 10px);
  background: var(--md-bg-card, var(--spd-p-bg));
  box-shadow: var(--spd-p-shadow-1);
  overflow: hidden;
}

.finance-settlement-page .settlement-kpi-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 18px;
  border-right: 1px solid var(--md-border-soft, var(--spd-p-border-subtle));
}

.finance-settlement-page .settlement-kpi-item:last-child {
  border-right: 0;
}

.finance-settlement-page .settlement-kpi-item.is-amount {
  background: linear-gradient(180deg, color-mix(in srgb, var(--spd-p-primary-strong) 4%, transparent) 0%, color-mix(in srgb, var(--spd-p-primary-strong) 0%, transparent) 100%);
}

.finance-settlement-page .settlement-kpi-item.is-amount .settlement-kpi-value {
  color: var(--spd-primary, var(--spd-p-primary-strong));
}

.finance-settlement-page .settlement-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--md-bg-soft, var(--spd-p-bg-subtle));
  color: var(--spd-text-secondary, var(--spd-p-info));
  flex-shrink: 0;
}

.finance-settlement-page .settlement-kpi-icon.is-doc {
  background: var(--spd-p-info-bg);
  color: var(--spd-p-info);
}

.finance-settlement-page .settlement-kpi-icon.is-supplier {
  background: var(--spd-p-bg-subtle);
  color: var(--spd-p-info);
}

.finance-settlement-page .settlement-kpi-icon.is-qty {
  background: var(--spd-p-warning-bg);
  color: var(--spd-p-warning-text);
}

.finance-settlement-page .settlement-kpi-icon.is-amount {
  background: color-mix(in srgb, var(--spd-p-primary-strong) 10%, transparent);
  color: var(--spd-primary, var(--spd-p-primary-strong));
}

.finance-settlement-page .settlement-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.finance-settlement-page .settlement-kpi-label {
  color: var(--md-text-secondary, var(--spd-text-secondary));
  /* 统计标签：--spd-font-size-sm（13/15/17px） */
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
}

.finance-settlement-page .settlement-kpi-value {
  color: var(--spd-text-heading, var(--spd-p-text));
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
  line-height: var(--spd-lh-tight);
  font-variant-numeric: tabular-nums;
}

.finance-settlement-page .settlement-kpi-value small {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  /* 统计单位：--spd-font-size-xs（11/13/15px） */
  font-size: var(--spd-font-size-xs);
  font-weight: 400;
  margin-left: 2px;
}

.finance-settlement-page .settlement-kpi-hint {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  /* 口径说明：--spd-font-size-xs（11/13/15px） */
  font-size: var(--spd-font-size-xs);
  font-weight: 400;
  line-height: var(--spd-lh-base);
}

/* 表格卡片：与 KPI 卡一致的圆角/阴影 */
.finance-settlement-page > .finance-detail-card,
.finance-settlement-page > .hospital-catalog-panel {
  border-radius: var(--spd-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
}

/* ---------- 表格密度重写：紧凑单元格 + 紧凑堆叠 ---------- */
.finance-settlement-page .settlement-detail-table th,
.finance-settlement-page .settlement-detail-table td {
  padding: 7px 9px !important;
  /* T-02 结算明细表数据：--spd-font-size-base（14/16/18px） */
  font-size: var(--spd-font-size-base) !important;
  line-height: var(--spd-lh-tight) !important;
  border-color: var(--md-border-soft, var(--spd-p-border-subtle)) !important;
}

.finance-settlement-page .settlement-detail-table thead th {
  padding-block: 8px !important;
  background: var(--md-bg-soft, var(--spd-p-bg-subtle)) !important;
  box-shadow: var(--spd-p-shadow-1);
  color: var(--spd-text-secondary, var(--spd-p-info)) !important;
  /* L-03 / T-09 结算明细表头：--spd-font-size-table-head（14/16/18px） */
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 700 !important;
  line-height: var(--spd-lh-tight) !important;
  letter-spacing: 0.1px;
  text-align: left !important;
  white-space: nowrap;
}

.finance-settlement-page .settlement-detail-table thead .settlement-col-document {
  background: var(--md-bg-soft, var(--spd-p-bg-subtle)) !important;
}

.finance-settlement-page .settlement-detail-table tbody tr:nth-child(even) {
  background: var(--spd-p-bg);
}

.finance-settlement-page .settlement-detail-table tbody tr:hover {
  /* 浅色悬停（此前误用近黑底，行悬停时文字几乎不可见） */
  background: var(--spd-p-primary-bg);
}

.finance-settlement-page .settlement-detail-table tbody tr:hover .settlement-col-document {
  background: var(--spd-p-primary-bg);
}

/* ---------- 数字列：居中 + 等宽数字 ----------
   与统一列表标准一致：金额 / 数量类单元格与普通列一样**居中**，
   数字使用等宽字体便于纵向比对。
   具体样式统一由 styles/list/unified-list.css 的 .ulc-number 提供，
   这里不再重复定义，避免各页面出现不同的对齐口径。 */

/* 单元格内堆叠：间距收敛
   长文本超宽自动换行，不做省略号截断（列表页统一标准 1.4）。 */
.finance-settlement-page .settlement-cell-stack {
  gap: 2px !important;
  line-height: var(--spd-lh-tight) !important;
}

.finance-settlement-page .settlement-cell-stack > strong {
  font-weight: 600 !important;
  color: var(--spd-text-primary, var(--spd-p-text)) !important;
  /* L-04 结算明细主字段：--spd-font-size-base（14/16/18px） */
  font-size: var(--spd-font-size-base) !important;
  line-height: var(--spd-lh-tight) !important;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.finance-settlement-page .settlement-cell-meta {
  color: var(--md-text-muted, var(--spd-p-neutral)) !important;
  /* L-05 结算明细元信息：--spd-font-size-xs（11/13/15px） */
  font-size: var(--spd-font-size-xs) !important;
  font-weight: 400;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.finance-settlement-page .settlement-policy-list > span,
.finance-settlement-page .settlement-code-list > span {
  /* L-09 结算策略说明：--spd-font-size-xs（11/13/15px） */
  font-size: var(--spd-font-size-xs) !important;
  line-height: var(--spd-lh-tight) !important;
}

.finance-settlement-page .settlement-policy-list b,
.finance-settlement-page .settlement-code-list b {
  color: var(--md-text-muted, var(--spd-p-neutral)) !important;
  /* L-08 结算策略标记：--spd-font-size-xs（11/13/15px）
     原为 10.5px，低于全站 11px 下限，现已统一到 --spd-font-size-xs */
  font-size: var(--spd-font-size-xs) !important;
  font-weight: 600 !important;
  line-height: var(--spd-lh-tight) !important;
}

.finance-settlement-page .settlement-policy-list small {
  color: var(--md-text-muted, var(--spd-p-neutral)) !important;
  /* L-09 结算策略说明：--spd-font-size-xs（11/13/15px）
     原为 10.5px，低于全站 11px 下限，现已统一到 --spd-font-size-xs */
  font-size: var(--spd-font-size-xs) !important;
}

.finance-settlement-page .settlement-code-list {
  font-family: var(--spd-font-family-mono);
  /* L-07 结算编码列表（等宽）：--spd-font-size-xs（11/13/15px） */
  font-size: var(--spd-font-size-xs) !important;
  font-variant-numeric: tabular-nums;
}

/* 金额列突出 */
.finance-settlement-page .amount-cell {
  color: var(--spd-primary, var(--spd-p-primary-strong)) !important;
  font-weight: 700 !important;
  /* L-06 结算金额：--spd-font-size-base（14/16/18px） */
  font-size: var(--spd-font-size-base) !important;
  font-variant-numeric: tabular-nums;
}

.finance-settlement-page .numeric-cell {
  /* L-12 结算数值单元格：--spd-font-size-base（14/16/18px） */
  font-size: var(--spd-font-size-base) !important;
  font-variant-numeric: tabular-nums;
}

/* 列宽微调：粘性首列更紧凑 */
.finance-settlement-page .settlement-detail-table .settlement-col-document {
  width: 170px !important;
  min-width: 170px !important;
  max-width: 170px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-location {
  width: 200px !important;
  min-width: 200px !important;
  max-width: 200px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-product {
  width: 240px !important;
  min-width: 240px !important;
  max-width: 240px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-spec {
  width: 170px !important;
  min-width: 170px !important;
  max-width: 170px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-manufacturer {
  width: 220px !important;
  min-width: 220px !important;
  max-width: 220px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-supplier {
  width: 200px !important;
  min-width: 200px !important;
  max-width: 200px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-policy {
  width: 200px !important;
  min-width: 200px !important;
  max-width: 200px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-trace {
  width: 280px !important;
  min-width: 280px !important;
  max-width: 280px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-finance {
  width: 200px !important;
  min-width: 200px !important;
  max-width: 200px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-time {
  width: 220px !important;
  min-width: 220px !important;
  max-width: 220px !important;
}

.finance-settlement-page .settlement-detail-table .settlement-col-status {
  width: 96px !important;
  min-width: 96px !important;
  max-width: 96px !important;
}

.finance-settlement-page .settlement-detail-table .numeric-cell {
  width: 96px !important;
  min-width: 96px !important;
  max-width: 96px !important;
}

.finance-settlement-page .settlement-detail-table .numeric-cell.settlement-col-package {
  width: 170px !important;
  min-width: 170px !important;
  max-width: 170px !important;
}

/* 滚动容器上限，避免超长明细把整页拉得很高 */
.finance-settlement-page .settlement-detail-scroll {
  max-height: min(72vh, 820px);
  overflow: auto;
  border: 1px solid var(--md-border-soft, var(--spd-p-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
}

/* =====================================================================
   高值耗材计费操作 high-value-operation-page
   ---------------------------------------------------------------------
   作为收费耗材明细查询（HighValueChargeDetailView）的子页面，
   页头沿用 md-header 渐变设计语言（与父页面 HighValueChargeDetailView
   完全一致），但页面外壳 padding / gap 收紧到与其他业务页面对齐。
   通用运营 KPI 卡关闭（缺货任务 / 待签收 / 消耗 / 风险 与高值计费
   操作场景不相关），右侧"绑定患者 / 计费回传"动作入口通过
   hospital-action-row 保留。
   ===================================================================== */
.high-value-operation-page {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: none;
  gap: 14px !important;
  padding: 16px 20px 24px;
}

.high-value-operation-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

.high-value-operation-page > .hospital-catalog-panel {
  border-radius: var(--spd-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
}

/* =====================================================================
   PDA 离线补传 pda-offline-page / 召回隔离 recall-isolation-page
   ---------------------------------------------------------------------
   这两页此前没有页面级 modifier class，导致外壳退回 .purchase-page 的
   padding: 0，内容直接贴住内容区左上边框。补上与其他页面一致的
   16px 20px 24px 页面边距 + 14px 间距。
   ===================================================================== */
.pda-offline-page,
.recall-isolation-page {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: none;
  gap: 14px !important;
  padding: 16px 20px 24px;
}

/* 与 shortage / delivery 一致：md 页头补圆角 */
.pda-offline-page > .md-header,
.recall-isolation-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

/* =====================================================================
   补货任务 shortage-task-page
   ---------------------------------------------------------------------
   范围：仅 type === 'shortage'（补货任务 / 缺货提醒共用同一 type）。
   拣配配送、科室请领、科室消耗、红冲、结算对账、召回等页面沿用原样。

   与采购管理 / 收货验收 / 库存管理 / 库存流水 统一到 md 设计语言：
   1. 页面级 gap / padding 收紧
   2. 业务动作卡片与列表卡片统一 md 卡片外观
   3. 查询区紧凑化（控件 30px、标签 12px）
   4. 表格 md 密度：表头 12px / 9px 12px，表体 13px / 8px 12px
   5. 列表滚动区加上限并 sticky 表头

   优先级：.master-table 的 padding / font-size / color 由
   spd-override.css 以 !important 统一设定，覆盖时加
   .shortage-task-page 前缀提高特指度。
   ===================================================================== */

/* ---------- 页面外壳 ---------- */
.shortage-task-page {
  gap: 14px !important;
  padding: 16px 20px 24px;
}

/* 面包屑在 md 体系里由 MdPageHeader 承担，这里隐藏（仅兜底） */
.shortage-task-page > .breadcrumb-line {
  display: none;
}

/* 顶部条直接用 MdPageHeader 组件渲染，与采购管理 / 收货验收 /
   库存管理 / 库存交易流水完全一致：
   渐变底 + 白色标题 + 白色副标题 + 主色描边，无需在此覆盖样式。
   这里仅做圆角对齐，保证页面 rhythm 与其他页面相同。 */
.shortage-task-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

/* ---------- 统计卡：保留现状，仅对齐 md 圆角/边框 ---------- */
.shortage-task-page > .closure-stat-grid {
  gap: 12px !important;
}

.shortage-task-page > .closure-stat-grid article {
  padding: 14px 16px;
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
}

.shortage-task-page > .closure-stat-grid article span {
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: var(--spd-font-size-sm);
}

.shortage-task-page > .closure-stat-grid article strong {
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: var(--spd-font-size-2xl);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* ---------- 卡片通用 ---------- */
.shortage-task-page > .hospital-catalog-panel,
.shortage-task-page > .hospital-action-row {
  gap: 12px;
  padding: 14px 16px 16px;
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
}

.shortage-task-page .section-title h3 {
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

.shortage-task-page .section-title svg {
  color: var(--md-primary, var(--spd-primary));
}

/* ---------- 动作条（业务动作标题 + 动作按钮 同行左右分列） ----------
   用户诉求：「智能补货分析这个按钮不要单独一行，和业务动作在一行，
   使用 flex 布局的 space-between 分别在两边对齐就好」。
   基础布局（.spd-action-bar / __main / __aside）由
   styles/components/spd-filter-bar.css 统一提供，这里只做本页微调：
   分区标题进入动作条后不再保留自己的下边框（分隔线由动作条统一承担）。 */
.shortage-task-page .spd-action-bar {
  margin-bottom: 12px;
}

.shortage-task-page .spd-action-bar .section-title {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: 0;
}

.shortage-task-page .spd-action-bar__aside {
  gap: 10px;
}

.shortage-task-page .spd-action-bar__aside .consumption-action-hint,
.shortage-task-page .spd-action-bar__aside .delivery-action-hint {
  max-width: 640px;
}

/* ---------- 表单区 ----------
   已统一改用 SpdFilterBar 组件，控件高度走 --spd-p-field-height，
   列数 / 换行规则 / 标签排版全部由
   styles/components/spd-filter-bar.css 的「标准规则」统一控制。 */

/* ---------- 动作按钮行 ----------
   master-data-all.override.css 的 .hospital-action-row 带 !important
   （min-height 48px + --spd-bg-page 背景），这里统一到 md 规格。 */
.shortage-task-page .hospital-action-row {
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-height: 0 !important;
  background: var(--md-bg-muted, var(--spd-p-row-stripe)) !important;
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  border-radius: var(--md-radius, 6px) !important;
  padding: 10px 12px !important;
}

.shortage-task-page .hospital-action-row .btn {
  min-height: 30px;
  padding: 0 12px;
  font-size: var(--spd-font-size-base);
}

.shortage-task-page .hospital-action-row .btn svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

/* ---------- 列表表格 ---------- */
/* 全局 `.table-scroll, ...` 设 border-radius:10px，需同特指度覆盖。 */
.shortage-task-page > .hospital-catalog-panel .table-scroll,
.shortage-task-page .table-scroll {
  max-height: max(300px, calc(100dvh - 420px));
  overflow: auto;
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  border-radius: var(--md-radius, 6px) !important;
  background: var(--md-bg-card, var(--spd-p-bg));
  scrollbar-width: thin;
  scrollbar-color: var(--md-border, var(--spd-border-default)) transparent;
}

.shortage-task-page .table-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.shortage-task-page .table-scroll::-webkit-scrollbar-thumb {
  background: var(--md-border, var(--spd-border-default));
  border-radius: 4px;
}

.shortage-task-page .purchase-table,
.shortage-task-page .purchase-detail-table {
  min-width: 100%;
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--spd-font-size-base) !important;
}

.shortage-task-page .purchase-table thead th,
.shortage-task-page .purchase-detail-table thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  padding: 9px 12px !important;
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 700 !important;
  letter-spacing: 0.2px;
  white-space: nowrap;
  color: var(--md-text-heading) !important;
  background: linear-gradient(180deg, var(--spd-p-bg-subtle) 0%, var(--spd-p-success-bg) 100%) !important;
  border-bottom: 1px solid var(--md-border, var(--spd-border-default)) !important;
}

.shortage-task-page .purchase-table tbody td,
.shortage-task-page .purchase-detail-table tbody td {
  padding: 8px 12px !important;
  font-size: var(--spd-font-size-base) !important;
  color: var(--md-text) !important;
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  vertical-align: middle;
}

.shortage-task-page .purchase-table tbody tr:nth-child(even) td,
.shortage-task-page .purchase-detail-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted, var(--spd-p-row-stripe)) !important;
}

.shortage-task-page .purchase-table tbody tr:hover td,
.shortage-task-page .purchase-detail-table tbody tr:hover td {
  background: var(--md-primary-soft, var(--spd-p-primary-bg)) !important;
}

/* 单据号等宽、数量金额右对齐 */
.shortage-task-page .purchase-table tbody td:first-child,
.shortage-task-page .purchase-detail-table tbody td:first-child {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-xs) !important;
  color: var(--md-text-heading) !important;
  white-space: nowrap;
}

/* ---------- 状态徽标 ----------
   列表状态条已统一迁移到通用组件 <SpdStatusChip>
   （styles/components/spd-status-chip.css）：几何（高度 / 内边距 / 圆角）
   与浅色底 + 深色字配色全部由令牌统一提供，本页不再写局部覆盖。 */

.shortage-task-page .muted-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  /* 辅助层说明：--spd-font-size-xs（11/13/15px） */
  font-size: var(--spd-font-size-xs);
  line-height: var(--spd-lh-tight);
}

/* ---------- 加载 / 空状态 ---------- */
.shortage-task-page .approval-empty {
  padding: 48px 16px !important;
  text-align: center;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-base);
}

/* ---------- 提示条 ---------- */
.shortage-task-page .inline-message {
  margin: 0;
  padding: 10px 14px;
  border: 1px solid var(--spd-primary-border);
  border-radius: var(--md-radius, 6px);
  color: var(--spd-primary-text);
  background: var(--spd-primary-bg);
  font-size: var(--spd-font-size-sm);
}

/* ---------- 响应式 ---------- */
@media (max-width: 760px) {
  .shortage-task-page {
    padding: 12px 12px 20px;
  }

  .shortage-task-page > .hospital-catalog-panel .table-scroll {
    max-height: max(240px, calc(100dvh - 380px));
  }
}

/* ---------- 拣配配送：响应式 ---------- */
@media (max-width: 760px) {
  .delivery-acceptance-page {
    padding: 12px 12px 20px;
  }

  .delivery-acceptance-page .delivery-workspace-card {
    padding: 14px;
  }

  .delivery-acceptance-page .delivery-action-row {
    align-items: stretch;
    flex-direction: column;
  }

  .delivery-acceptance-page .delivery-action-hint {
    margin-right: 0;
  }

  .delivery-acceptance-page .delivery-action-row .btn {
    width: 100%;
  }

  .delivery-acceptance-page .picking-panel .table-scroll {
    max-height: max(240px, calc(100dvh - 420px));
  }
}
.delivery-tabs {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--spd-border-subtle);
}

.delivery-tabs button {
  position: relative;
  min-height: 38px;
  padding-inline: 16px;
  border: 0;
  background: transparent;
}

.delivery-tabs button.active {
  background: var(--subnav-btn-active-bg);
  color: var(--tech-blue);
}

.delivery-tabs button.active::after {
  position: absolute;
  inset: auto 12px -5px;
  height: 2px;
  background: currentColor;
  content: '';
}

.delivery-confirmation-panel .master-table thead th {
  padding: 9px 12px !important;
  background: var(--inventory-table-heading-bg) !important;
}

.delivery-confirmation-panel .master-table tbody td {
  padding: 8px 12px !important;
}

.delivery-confirmation-panel .master-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted) !important;
}
</style>
