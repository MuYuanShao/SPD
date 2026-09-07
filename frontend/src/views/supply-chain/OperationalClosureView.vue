<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import 'element-plus/theme-chalk/el-dialog.css'
import { useRoute } from 'vue-router'
import {
  Activity,
  AlertTriangle,
  Building2,
  CheckCircle2,
  ClipboardCheck,
  FileCheck2,
  History,
  PackageCheck,
  RefreshCw,
  RotateCcw,
  Save,
  ScanLine,
  Search,
  ShieldCheck,
  Smartphone,
  Truck,
  X
} from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import {
  bindHighValuePatient,
  confirmLoosePicking,
  confirmPicking,
  createColdChainException,
  createConsumption,
  createDelivery,
  createRecall,
  createRequisition,
  fetchClosureList,
  fetchClosureOptions,
  fetchClosureOverview,
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
  signDelivery,
  smartReplenishmentAnalysis,
  uploadPdaOffline,
  type ClosureOptions,
  type PackageLabelDetail
} from '../../api/operationalClosure'
import { fetchDepartmentWarehouses, type DepartmentWarehouseRelation } from '../../api/masterData'
import { consumeQuotaPackageByCode } from '../../api/udiTraceability'
import { formatBusinessText, formatStatusText } from '../../utils/chineseDisplay'

const route = useRoute()
const loading = ref(false)
const message = ref('')
const deliverySubmitting = ref(false)
const deliveryError = ref('')
const rows = ref<Record<string, unknown>[]>([])
const analysisRows = ref<Record<string, unknown>[]>([])
const pickingRequisitionRows = ref<Record<string, unknown>[]>([])
const pickingPackageRows = ref<Record<string, unknown>[]>([])
const selectedPickingRequisitionNo = ref('')
const selectedPickingItemId = ref('')
const selectedPickingItemType = ref('loose')
const selectedPickingLabels = ref<string[]>([])
const pickingPackageScanCode = ref('')
const pickingUniqueCodeRows = ref<Array<Record<string, unknown>>>([])
const selectedPickingUniqueCodes = ref<string[]>([])
const pickingLooseRows = ref<Array<Record<string, unknown> & { pickQty?: number }>>([])
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

const form = reactive({
  deptName: '',
  warehouseName: '',
  productCode: '',
  quantity: 1,
  minQty: 1,
  currentQty: 0,
  replenishQty: '' as number | '',
  replenishmentDays: 7 as number | 'custom',
  customReplenishmentDays: '' as number | '',
  requisitionNo: '',
  deliveryNo: '',
  consumptionNo: '',
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
const recallScope = ref<'all' | 'primary' | 'secondary' | 'tertiary'>('all')
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
  settlement: '结算对账 / 供应商明细口径',
  pda: 'PDA 基础作业与离线补传',
  risk: '冷链异常 / 召回隔离',
  'high-value': '高值使用计费基础'
}
const title = computed(() => pageCode.value === 'red-flush-management' ? '红冲管理' : titleMap[type.value])
const subtitle = computed(() => {
  if (type.value === 'red-flush') return '展示反消耗生成并持久化到数据库的红冲记录。'
  if (type.value === 'settlement') return '按库存批次固化的结算点自动生成结算明细，财务只需复核确认。'
  if (type.value === 'risk') return '登记冷链异常与召回隔离事件，形成合规追踪入口。'
  if (type.value === 'pda') return '模拟 PDA 离线作业补传，记录设备、作业类型与回放状态。'
  if (type.value === 'delivery') return '中心库按科室申领单拣配定数包，绑定申领明细后出一级库。'
  if (type.value === 'consumption') return '面向科室库房的扫码消耗工作台，支持定数包、UDI、唯一码登记及反消耗追溯。'
  return '围绕库存、申领、配送、消耗和财务事实形成可追溯业务流水。'
})
const stats = computed(() => [
  { label: '缺货任务', value: summary.value.shortageTasks ?? 0 },
  { label: '待签收配送', value: summary.value.pendingDeliveries ?? 0 },
  { label: '消耗单据', value: summary.value.consumptions ?? 0 },
  { label: '风险事件', value: summary.value.riskEvents ?? 0 }
])
const consumptionStats = computed(() => [
  { label: '本页消耗记录', value: rows.value.length, icon: 'records' },
  { label: '已确认', value: rows.value.filter(row => String(row.status) === 'confirmed').length, icon: 'confirmed' },
  { label: '已红冲', value: rows.value.filter(row => ['reversed', 'red_flushed'].includes(String(row.status))).length, icon: 'reversed' },
  { label: '本页消耗数量', value: rows.value.reduce((sum, row) => sum + Number(row.quantity || 0), 0), icon: 'quantity' }
])
const displayStats = computed(() => type.value === 'consumption'
  ? consumptionStats.value
  : stats.value.map(item => ({ ...item, icon: '' })))

const replenishmentDayOptions = [5, 7, 15, 30]
const selectedDepartment = computed(() => options.value.departments.find(item => item.deptName === form.deptName))
const warehouseChoices = computed(() => {
  if (type.value === 'delivery') return primaryWarehouses.value
  if (isRecallPage.value) {
    const pattern = recallScope.value === 'primary' ? /一级|中心/ : recallScope.value === 'secondary' ? /二级/ : /三级/
    return options.value.warehouses.filter(item => pattern.test(String(item.warehouseType || '')))
  }
  if (['shortage', 'requisition', 'consumption'].includes(type.value)) {
    return linkedWarehouses.value.map(item => ({ warehouseName: item.name }))
  }
  return options.value.warehouses
})
const effectiveReplenishmentDays = computed(() => {
  const raw = form.replenishmentDays === 'custom' ? form.customReplenishmentDays : form.replenishmentDays
  const days = Number(raw)
  return Number.isFinite(days) && days > 0 ? days : 7
})

function shortagePayload() {
  return {
    ...form,
    replenishmentDays: effectiveReplenishmentDays.value,
    replenishQty: form.replenishQty === '' ? undefined : form.replenishQty
  }
}

async function loadLinkedWarehouses() {
  const deptCode = selectedDepartment.value?.deptCode
  if (!deptCode) {
    linkedWarehouses.value = []
    if (type.value !== 'delivery') form.warehouseName = ''
    return
  }
  try {
    const warehouses = await fetchDepartmentWarehouses(deptCode)
    linkedWarehouses.value = warehouses.filter(item => Number(item.selected ?? 0) === 1)
    // 选择科室时默认带出科室所关联的库房
    const preferred = linkedWarehouses.value[0]?.name || ''
    if (type.value === 'delivery') {
      // 拣配配送只允许一级库：科室关联库房为一级库时直接带出，否则保持当前一级库
      if (preferred && primaryWarehouses.value.some(item => item.warehouseName === preferred)) {
        form.warehouseName = preferred
      } else if (!primaryWarehouses.value.some(item => item.warehouseName === form.warehouseName)) {
        form.warehouseName = primaryWarehouses.value[0]?.warehouseName || ''
      }
      return
    }
    if (!linkedWarehouses.value.some(item => item.name === form.warehouseName)) {
      form.warehouseName = preferred
    }
  } catch (error) {
    linkedWarehouses.value = []
    if (type.value !== 'delivery') form.warehouseName = ''
    message.value = error instanceof Error ? error.message : '关联库房加载失败'
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
  loading.value = true
  try {
    applyRouteSmartDefaults()
    const [overview, optionData, listData] = await Promise.all([
      fetchClosureOverview(),
      fetchClosureOptions(),
      fetchClosureList(type.value, { page: String(currentPage.value), size: String(pageSize.value) })
    ])
    summary.value = overview.summary
    options.value = optionData
    rows.value = listData.rows
    totalItems.value = listData.total
    if (!form.deptName && optionData.departments[0]) form.deptName = optionData.departments[0].deptName
    applyRouteSmartDefaults()
    if (['shortage', 'requisition', 'consumption', 'delivery'].includes(type.value)) {
      await loadLinkedWarehouses()
    } else if (!form.warehouseName && optionData.warehouses[0]) {
      form.warehouseName = optionData.warehouses[0].warehouseName
    }
    if (!form.productCode && optionData.products[0]) form.productCode = optionData.products[0].productCode
    if (type.value === 'delivery') {
      // 拣配配送只允许选择一级库（中心库），展示一级库库存
      if (!primaryWarehouses.value.some(item => item.warehouseName === form.warehouseName)) {
        form.warehouseName = primaryWarehouses.value[0]?.warehouseName || ''
      }
      await loadPickingRequisitions()
    }
    await maybeOpenRouteSmartAnalysis()
  } finally {
    loading.value = false
  }
}

async function loadPickingRequisitions() {
  const result = await fetchPickingRequisitions()
  pickingRequisitionRows.value = result.rows || []
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
  } else if (selectedPickingItemId.value) {
    await loadPickingSources()
  }
}

/** 申请明细类型展示：唯一码/定数包/散货，与申请单申请的商品明细类型配对 */
function itemTypeLabel(itemType: unknown) {
  switch (String(itemType || '')) {
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
  selectedPickingItemType.value = String(row.itemType || 'loose')
  form.requisitionNo = selectedPickingRequisitionNo.value
  form.deptName = String(row.deptName || form.deptName)
  form.productCode = String(row.productCode || form.productCode)
  form.quantity = Number(row.remainingQty || row.requisitionQty || 1)
  selectedPickingLabels.value = []
  selectedPickingUniqueCodes.value = []
  await loadPickingSources()
}

async function loadPickingSources() {
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
  if (itemType === 'unique_code') {
    const result = await fetchPickingUniqueCodes(baseParams)
    pickingUniqueCodeRows.value = result.rows || []
    pickingPackageRows.value = []
    pickingLooseRows.value = []
    return
  }
  if (itemType === 'quota_package') {
    const labelsResult = await fetchPickingPackageLabels(baseParams)
    pickingPackageRows.value = labelsResult.rows || []
    pickingLooseRows.value = []
  } else {
    const looseResult = await fetchPickingLooseStock(baseParams)
    pickingLooseRows.value = (looseResult.rows || []).map((row) => ({ ...row, pickQty: 0 }))
    pickingPackageRows.value = []
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
    message.value = `定数包码 ${code} 不属于当前申请商品或不在所选一级库`
    return
  }
  if (!selectedPickingLabels.value.includes(String(matched.labelNo))) {
    selectedPickingLabels.value = [...selectedPickingLabels.value, String(matched.labelNo)]
  }
  pickingPackageScanCode.value = ''
  message.value = `已扫码选中定数包：${matched.labelNo}`
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

async function confirmSelectedPicking() {
  if (!selectedPickingRequisitionNo.value || !selectedPickingItemId.value) {
    message.value = '请先选择科室申领明细'
    return
  }
  if (!form.warehouseName) {
    message.value = '请先选择中心库'
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
      message.value = '请先勾选唯一码/UDI'
      return
    }
    const result = await createDelivery({
      ...form,
      requisitionNo: selectedPickingRequisitionNo.value,
      quantity: selectedPickingUniqueCodes.value.length,
      uniqueCodes: selectedPickingUniqueCodes.value.join(',')
    })
    form.deliveryNo = String(result.deliveryNo || '')
    message.value = `拣配出库完成：${result.deliveryNo}，唯一码 ${selectedPickingUniqueCodes.value.length} 个`
    selectedPickingUniqueCodes.value = []
    await loadData()
    return
  }

  // 履约模式必须与已审批申领快照一致。
  const looseQty = pickingLooseRows.value.reduce((sum, row) => sum + Number(row.pickQty || 0), 0)
  if (!selectedPickingLabels.value.length && looseQty <= 0) {
    message.value = itemType === 'quota_package' ? '请勾选匹配申领快照的定数包标签' : '请填写散货拣配数量'
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
  form.deliveryNo = String(done[0]?.split('（')[1]?.replace('）', '') || '')
  message.value = `拣配出库完成：${done.join('、')}`
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
    message.value = error instanceof Error ? error.message : '定数包明细加载失败'
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

/** 科室消耗：按定数包码 / UDI / 唯一码定位商品后再登记消耗 */
async function resolveConsumption() {
  const code = consumptionQueryCode.value.trim()
  if (!code) {
    message.value = '请输入定数包码、UDI 或唯一码'
    return
  }
  consumptionResolving.value = true
  message.value = ''
  consumptionResolved.value = null
  try {
    const result = await resolveConsumptionProduct(code)
    consumptionResolved.value = result
    form.productCode = String(result.productCode || '')
    if (result.sourceType === 'package') {
      form.quantity = Number(result.packageQuantity || 0)
    }
    if (result.warehouseName) {
      const choices = warehouseChoices.value.map(item => item.warehouseName)
      if (choices.includes(String(result.warehouseName))) {
        form.warehouseName = String(result.warehouseName)
      }
    }
    message.value = `已定位商品：${result.productCode} / ${result.productName}（${result.sourceType === 'package' ? '定数包码' : 'UDI/唯一码'}）`
  } catch (error) {
    message.value = error instanceof Error ? error.message : '查询码解析失败'
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
    message.value = error instanceof Error ? error.message : '召回批次加载失败'
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
      warehouseName: recallScope.value === 'all' ? '' : form.warehouseName
    })
    recallInventoryRows.value = result.rows || []
  } catch (error) {
    message.value = error instanceof Error ? error.message : '召回范围库存加载失败'
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

async function runAction(action: string, row?: Record<string, unknown>) {
  if (type.value !== 'delivery') return performAction(action, row)
  if (deliverySubmitting.value) return
  deliverySubmitting.value = true
  deliveryError.value = ''
  message.value = ''
  try {
    await performAction(action, row)
  } catch (error) {
    deliveryError.value = error instanceof Error ? error.message : '配送操作失败，请重试'
  } finally {
    deliverySubmitting.value = false
  }
}

async function performAction(action: string, row?: Record<string, unknown>) {
  let result: Record<string, unknown> = {}
  if (action === 'shortage') {
    result = await generateShortage(shortagePayload())
    message.value = `补货任务已生成：${result.taskNo}，周期 ${result.periodDays} 天，建议 ${result.formulaReplenishQty}，实际 ${result.replenishQty}`
  }
  if (action === 'requisition') {
    result = await createRequisition(form)
    form.requisitionNo = String(result.requisitionNo || '')
    message.value = `科室申领已提交待审批：${result.requisitionNo}`
  }
  if (action === 'approveRequisition' || action === 'rejectRequisition') {
    result = await processRequisition(String(row?.bizNo || ''), action === 'approveRequisition' ? 'approve' : 'reject')
    message.value = action === 'approveRequisition' ? `申领已审批：${result.requisitionNo}` : `申领已驳回：${result.requisitionNo}`
  }
  if (action === 'delivery') {
    if (form.uniqueCodes.trim()) {
      result = await createDelivery(form)
      form.deliveryNo = String(result.deliveryNo || '')
      message.value = `高值耗材已按唯一码拣配：${result.deliveryNo}`
    } else {
      await confirmSelectedPicking()
      return
    }
  }
  if (action === 'sign') {
    result = await signDelivery(String(row?.bizNo || form.deliveryNo))
    message.value = `配送已签收：${result.deliveryNo}`
  }
  if (action === 'consumption') {
    if (consumptionResolved.value?.sourceType === 'package') {
      result = await consumeQuotaPackageByCode({
        code: consumptionQueryCode.value.trim(),
        deptName: form.deptName || undefined
      })
    } else {
      result = await createConsumption(form)
    }
    form.consumptionNo = String(result.consumptionNo || '')
    message.value = `科室消耗已登记：${result.consumptionNo}`
  }
  if (action === 'reverse') {
    result = await reverseConsumption(String(row?.bizNo || form.consumptionNo))
    message.value = `反消耗红冲已生成：${result.flushNo}`
  }
  if (action === 'pda') {
    result = await uploadPdaOffline(form)
    message.value = `PDA 离线记录已补传：${result.recordNo}`
  }
  if (action === 'cold') {
    result = await createColdChainException(form)
    message.value = `冷链异常已登记：${result.eventNo}`
  }
  if (action === 'recall') {
    result = await createRecall({
      scope: recallScope.value,
      deptName: recallScope.value === 'all' ? undefined : form.deptName,
      warehouseName: recallScope.value === 'all' ? undefined : form.warehouseName,
      productCode: form.productCode,
      batchNo: recallBatchNo.value,
      reason: form.reason
    })
    message.value = `召回隔离已完成：${result.recallNo}，${result.affectedQty} 件已回收到 ${result.primaryWarehouseName} 并隔离`
  }
  if (action === 'bindPatient') {
    const uniqueCode = form.uniqueCodes.split(/[,，\s]+/).find(Boolean) || ''
    result = await bindHighValuePatient({ ...form, uniqueCode })
    message.value = `唯一码 ${result.uniqueCode} 已绑定患者 ${result.patientNo}`
  }
  if (action === 'billingCallback') {
    const uniqueCode = form.uniqueCodes.split(/[,，\s]+/).find(Boolean) || ''
    result = await receiveHighValueBillingCallback({
      ...form,
      uniqueCode,
      quantity: 1,
      externalChargeNo: form.externalChargeNo
    })
    message.value = `高值耗材已按唯一码计费扣减：${result.chargeNo}`
  }
  await loadData()
}

async function openSmartAnalysis() {
  // 支持全院分析：未选择科室时按全院科室库房出库核算
  message.value = ''
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
    analysisError.value = error instanceof Error ? error.message : '智能补货分析失败'
    message.value = analysisError.value
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
  try {
    await runAction('shortage')
    analysisDialogOpen.value = false
  } catch (error) {
    message.value = error instanceof Error ? error.message : '补货任务生成失败'
  }
}

onMounted(loadData)
watch(type, () => {
  currentPage.value = 1
  autoSmartAnalysisHandled.value = false
  loadData()
})
watch(() => form.deptName, () => {
  if (['shortage', 'requisition', 'consumption', 'delivery'].includes(type.value)) {
    loadLinkedWarehouses()
  }
  if (type.value === 'risk') {
    if (isRecallPage.value) void applyRecallScope()
    else loadRecallBatches()
  }
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
</script>

<template>
  <section class="purchase-page closure-page" :class="{ 'consumption-saas-page': type === 'consumption', 'delivery-acceptance-page': type === 'delivery' }">
    <div class="breadcrumb-line">
      <span>一期上线闭环</span>
      <strong>{{ title }}</strong>
    </div>

    <div class="detail-heading">
      <div>
        <div v-if="type === 'consumption'" class="consumption-eyebrow">
          <ShieldCheck :size="14" />
          科室库存闭环
        </div>
        <p>第 6-10 批业务闭环</p>
        <h2>{{ title }}</h2>
        <small>{{ subtitle }}</small>
      </div>
      <button class="btn" type="button" @click="loadData">
        <RefreshCw :size="17" />
        刷新
      </button>
    </div>

    <div v-if="type !== 'delivery'" class="foundation-stat-grid closure-stat-grid">
      <article v-for="item in displayStats" :key="item.label">
        <i v-if="type === 'consumption'" class="consumption-stat-icon" :class="`is-${item.icon}`">
          <Activity v-if="item.icon === 'records'" :size="20" />
          <CheckCircle2 v-else-if="item.icon === 'confirmed'" :size="20" />
          <RotateCcw v-else-if="item.icon === 'reversed'" :size="20" />
          <PackageCheck v-else :size="20" />
        </i>
        <div>
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </article>
    </div>

    <StatusMessage v-if="type === 'delivery'" :message="deliveryError" tone="error" role="alert" />
    <StatusMessage v-if="type === 'delivery'" :message="message" tone="info" role="status" />
    <p v-else-if="message" class="inline-message">{{ message }}</p>
    <p v-if="type === 'settlement'" class="inline-message">结算数据在验收入库、科室消耗或患者计费达到批次结算点时自动生成，无需人工生成。</p>

    <section v-if="type !== 'settlement'" class="hospital-catalog-panel" :class="{ 'consumption-entry-card': type === 'consumption' }">
      <div class="section-title" :class="{ 'consumption-section-title': type === 'consumption' }">
        <template v-if="type === 'consumption'">
          <i class="consumption-section-icon"><ScanLine :size="20" /></i>
          <div>
            <h3>消耗登记</h3>
            <p class="consumption-section-description">扫描追溯码定位耗材，系统将实时校验科室库房库存</p>
          </div>
          <span class="consumption-live-badge"><span></span>实时库存校验</span>
        </template>
        <template v-else>
          <ClipboardCheck :size="20" />
          <h3>业务动作</h3>
        </template>
      </div>
      <div class="hospital-query-grid closure-form-grid" :class="{ 'consumption-form-grid': type === 'consumption' }">
        <label v-if="isRecallPage">
          <span>召回范围</span>
          <select v-model="recallScope" @change="applyRecallScope">
            <option value="all">全部</option><option value="primary">一级库</option>
            <option value="secondary">二级库</option><option value="tertiary">三级库</option>
          </select>
        </label>
        <label v-if="!isRecallPage || ['secondary', 'tertiary'].includes(recallScope)" :class="{ 'consumption-grid-dept': type === 'consumption' }">
          <span>科室</span>
          <select v-model="form.deptName">
            <option v-if="type === 'shortage'" value="">全院</option>
            <option v-for="item in options.departments" :key="item.deptName" :value="item.deptName">{{ item.deptName }}</option>
          </select>
        </label>
        <label v-if="!isRecallPage || recallScope !== 'all'" :class="{ 'consumption-grid-warehouse': type === 'consumption' }">
          <span>库房</span>
          <select v-model="form.warehouseName">
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
          <label class="wide-field consumption-grid-code">
            <span>扫码识别耗材</span>
            <div class="consumption-code-search">
              <input
                v-model.trim="consumptionQueryCode"
                placeholder="扫码或输入定数包码、UDI、唯一码，点击查询定位商品"
                @keyup.enter="resolveConsumption"
              />
              <button class="btn" type="button" :disabled="consumptionResolving" @click="resolveConsumption">
                <Search :size="15" />
                {{ consumptionResolving ? '查询中...' : '查询' }}
              </button>
            </div>
          </label>
          <label v-if="consumptionResolved" class="wide-field consumption-grid-resolved">
            <span>已识别耗材</span>
            <div class="consumption-resolved">
              <strong>{{ consumptionResolved.productCode }} / {{ consumptionResolved.productName }}</strong>
              <small>{{ consumptionResolved.specModel || '-' }} · {{ consumptionResolved.unit || '-' }} · 来源：{{ consumptionResolved.sourceType === 'package' ? '定数包码' : 'UDI/唯一码' }}</small>
            </div>
          </label>
        </template>
        <label v-if="!['shortage', 'delivery'].includes(type) && !isRecallPage" :class="{ 'consumption-grid-quantity': type === 'consumption' }"><span>数量</span><input v-model.number="form.quantity" type="number" min="1" :readonly="type === 'consumption' && consumptionResolved?.sourceType === 'package'" /></label>
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
        <label v-if="type === 'high-value'"><span>患者号</span><input v-model="form.patientNo" /></label>
        <label v-if="['requisition', 'delivery', 'consumption', 'high-value'].includes(type)" class="wide-field" :class="{ 'consumption-grid-unique': type === 'consumption' }">
          <span>高值唯一码</span>
          <input v-model.trim="form.uniqueCodes" placeholder="扫描或输入唯一码；多码用逗号分隔" />
        </label>
        <label v-if="type === 'high-value'"><span>患者姓名</span><input v-model.trim="form.patientNameMasked" placeholder="脱敏姓名" /></label>
        <label v-if="type === 'high-value'" class="wide-field"><span>外部计费流水</span>
          <input v-model.trim="form.externalChargeNo" placeholder="HIS/AIMS 计费流水号" /></label>
        <template v-if="type === 'shortage'">
          <label>
            <span>补货周期</span>
            <select v-model="form.replenishmentDays">
              <option v-for="days in replenishmentDayOptions" :key="days" :value="days">{{ days }} 天</option>
            </select>
          </label>
        </template>
      </div>
      <div class="hospital-action-row" :class="{ 'consumption-action-row': type === 'consumption' }">
        <span v-if="type === 'consumption'" class="consumption-action-hint">登记后将同步扣减所选科室库房库存，并写入追溯记录</span>
        <button v-if="type === 'shortage'" class="btn btn-primary" type="button" @click="openSmartAnalysis">
          <AlertTriangle :size="18" />
          智能补货分析
        </button>
        <button v-if="type === 'requisition'" class="btn btn-primary" type="button" @click="runAction('requisition')">
          <Save :size="18" />
          提交申领
        </button>
        <button v-if="type === 'delivery'" class="btn btn-primary" type="button" :disabled="deliverySubmitting || loading" @click="runAction('delivery')">
          <Truck :size="18" />
          确认拣配出库
        </button>
        <button v-if="type === 'consumption'" class="btn btn-primary" type="button" @click="runAction('consumption')">
          <PackageCheck :size="18" />
          登记消耗
        </button>
        <button v-if="type === 'consumption'" class="btn" type="button" @click="runAction('reverse')">
          <RotateCcw :size="18" />
          反消耗/红冲
        </button>
        <button v-if="type === 'pda'" class="btn btn-primary" type="button" @click="runAction('pda')">
          <Smartphone :size="18" />
          离线补传
        </button>
        <button v-if="type === 'risk' && !isRecallPage" class="btn btn-primary" type="button" @click="runAction('cold')">
          <AlertTriangle :size="18" />
          冷链异常
        </button>
        <button v-if="type === 'risk' && isRecallPage" class="btn btn-primary" type="button" @click="runAction('recall')">
          <RotateCcw :size="18" />
          召回隔离
        </button>
        <button v-if="type === 'high-value'" class="btn" type="button" @click="runAction('bindPatient')">
          <ClipboardCheck :size="18" />
          绑定患者
        </button>
        <button v-if="type === 'high-value'" class="btn btn-primary" type="button" @click="runAction('billingCallback')">
          <FileCheck2 :size="18" />
          计费回传并扣减
        </button>
      </div>

      <div v-if="isRecallPage" class="table-scroll">
        <table class="master-table purchase-detail-table">
          <thead><tr><th>库房级别</th><th>库房</th><th>商品</th><th>规格</th><th>厂家</th><th>单位</th><th>单价</th><th>系统批号</th><th>生产批号</th><th>散货</th><th>定数包折散</th><th>合计</th></tr></thead>
          <tbody>
            <tr v-if="recallBatchesLoading"><td colspan="12" class="approval-empty">正在加载范围库存...</td></tr>
            <tr v-else-if="!recallInventoryRows.length"><td colspan="12" class="approval-empty">当前范围暂无可召回库存</td></tr>
            <tr v-for="row in recallInventoryRows" v-else :key="`${row.warehouseId}-${row.batchId}`">
              <td>{{ row.warehouseType }}</td><td>{{ row.warehouseName }}</td><td>{{ row.productName }}</td><td>{{ row.specModel || '-' }}</td>
              <td>{{ row.manufacturerName || '-' }}</td><td>{{ row.unit }}</td><td>¥ {{ Number(row.unitPrice || 0).toFixed(2) }}</td>
              <td>{{ row.systemBatchNo }}</td><td>{{ row.productionBatchNo || '-' }}</td><td>{{ row.looseQty }}</td><td>{{ row.packageQty }}</td><td><strong>{{ row.totalQty }}</strong></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="type === 'delivery'" class="picking-workbench">
        <section class="picking-panel">
          <div class="section-title compact">
            <h3>待拣配申领单</h3>
          </div>
          <div class="table-scroll">
            <table class="master-table purchase-detail-table">
              <thead>
                <tr>
                  <th>申领单号</th>
                  <th>科室</th>
                  <th>商品</th>
                  <th>申请类型</th>
                  <th>申领数量</th>
                  <th>已拣配</th>
                  <th>待拣配</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!pickingRequisitionRows.length">
                  <td colspan="8" class="approval-empty">暂无待拣配申领单</td>
                </tr>
                <tr
                  v-for="row in pickingRequisitionRows"
                  :key="`${row.requisitionNo}-${row.itemId}`"
                  :class="{ selected: selectedPickingItemId === String(row.itemId) }"
                >
                  <td>
                    <strong>{{ row.requisitionNo }}</strong>
                    <span v-if="String(row.status || '') === 'partial_picked'" class="status-badge pending">部分拣配</span>
                  </td>
                  <td>{{ row.deptName }}</td>
                  <td>
                    <strong>{{ row.productName }}</strong>
                    <span class="muted-cell">{{ row.productCode }}</span>
                  </td>
                  <td>
                    <span class="status-badge">{{ itemTypeLabel(row.itemType) }}</span>
                    <span v-if="row.itemType === 'quota_package'" class="muted-cell">
                      版本 {{ row.templateVersion ?? '历史快照缺失' }} ·
                      {{ row.packageQuantity ?? '-' }} / {{ row.packageUnit ?? '-' }}
                      · 待拣 {{ row.remainingPackageCount ?? '-' }} 包
                    </span>
                  </td>
                  <td>{{ row.requisitionQty }}</td>
                  <td>{{ row.pickedQty }}</td>
                  <td>{{ row.remainingQty }}</td>
                  <td>
                    <button class="btn-text" type="button" @click="selectPickingRequisition(row)">选择</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <!-- 唯一码/UDI 类型：展示申请单绑定的唯一码 -->
        <section v-if="selectedPickingItemType === 'unique_code'" class="picking-panel">
          <div class="section-title compact">
            <h3>可用唯一码 / UDI</h3>
            <span class="muted-hint">已选 {{ selectedPickingUniqueCodes.length }} / 待拣配 {{ form.quantity }}</span>
          </div>
          <div class="table-scroll">
            <table class="master-table purchase-detail-table">
              <thead>
                <tr>
                  <th></th>
                  <th>唯一码</th>
                  <th>UDI</th>
                  <th>系统批次</th>
                  <th>有效期</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!pickingUniqueCodeRows.length">
                  <td colspan="5" class="approval-empty">该申请明细没有可拣配的唯一码/UDI</td>
                </tr>
                <tr v-for="row in pickingUniqueCodeRows" :key="String(row.uniqueCode)">
                  <td>
                    <input
                      type="checkbox"
                      :checked="selectedPickingUniqueCodes.includes(String(row.uniqueCode))"
                      @change="togglePickingUniqueCode(String(row.uniqueCode), ($event.target as HTMLInputElement).checked)"
                    />
                  </td>
                  <td><strong>{{ row.uniqueCode }}</strong></td>
                  <td>{{ row.udiCode }}</td>
                  <td>{{ row.batchNo || '-' }}</td>
                  <td>{{ row.expireDate || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <template v-else>
        <section v-if="selectedPickingItemType === 'quota_package'" class="picking-panel">
          <div class="section-title compact">
            <h3>可用定数包</h3>
            <span class="muted-hint">按申领模板版本和包装规格选择标签</span>
          </div>
          <div class="consumption-code-search picking-package-scan">
            <input v-model.trim="pickingPackageScanCode" placeholder="扫描或输入定数包码" @keyup.enter="scanPickingPackage" />
            <button class="btn" type="button" @click="scanPickingPackage"><Search :size="15" />扫码拣选</button>
          </div>
          <div class="table-scroll">
            <table class="master-table purchase-detail-table">
              <thead>
                <tr>
                  <th></th>
                  <th>定数包码</th>
                  <th>库房</th>
                  <th>商品</th>
                  <th>包内数量</th>
                  <th>生成时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!pickingPackageRows.length">
                  <td colspan="6" class="approval-empty">请选择申领单和库房后查看可用定数包</td>
                </tr>
                <tr v-for="row in pickingPackageRows" :key="String(row.labelNo)">
                  <td>
                    <input
                      type="checkbox"
                      :checked="selectedPickingLabels.includes(String(row.labelNo))"
                      @change="togglePickingLabel(String(row.labelNo), ($event.target as HTMLInputElement).checked)"
                    />
                  </td>
                  <td>
                    <button class="btn-link package-label-link" type="button" @click="openPackageDetail(row.labelNo)">
                      {{ row.labelNo }}
                    </button>
                  </td>
                  <td>{{ row.warehouseName }}</td>
                  <td>
                    <strong>{{ row.productName }}</strong>
                    <span class="muted-cell">{{ row.productCode }}</span>
                  </td>
                  <td>{{ row.packageQuantity }}</td>
                  <td>{{ row.createTime }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-if="selectedPickingItemType === 'loose'" class="picking-panel picking-panel--loose">
          <div class="section-title compact">
            <h3>可用散货</h3>
            <span class="muted-hint">已选合计 {{ selectedPickingTotal }} / 待拣配 {{ form.quantity }}（基础单位）</span>
          </div>
          <div class="table-scroll">
            <table class="master-table purchase-detail-table">
              <thead>
                <tr>
                  <th>商品名称</th>
                  <th>规格型号</th>
                  <th>厂家</th>
                  <th>单位</th>
                  <th>单价</th>
                  <th>系统批次</th>
                  <th>生产批号</th>
                  <th>有效期</th>
                  <th>可用数量</th>
                  <th>拣配数量</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!pickingLooseRows.length">
                  <td colspan="10" class="approval-empty">一级库没有该商品的可用散货</td>
                </tr>
                <tr v-for="row in pickingLooseRows" :key="String(row.balanceId)">
                  <td><strong>{{ row.productName }}</strong><span class="muted-cell">{{ row.productCode }}</span></td>
                  <td>{{ row.specModel || '-' }}</td>
                  <td>{{ row.manufacturerName || '-' }}</td>
                  <td>{{ row.unit || '-' }}</td>
                  <td>¥ {{ Number(row.unitPrice || 0).toFixed(2) }}</td>
                  <td>{{ row.systemBatchNo }}</td>
                  <td>{{ row.productionBatchNo || '-' }}</td>
                  <td>{{ row.expireDate || '-' }}</td>
                  <td>{{ row.availableQty }}</td>
                  <td>
                    <input
                      v-model.number="row.pickQty"
                      class="stocktaking-qty-input"
                      type="number"
                      min="0"
                      :max="Number(row.availableQty)"
                      step="0.0001"
                      placeholder="0"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        </template>
      </div>
    </section>

    <section class="hospital-catalog-panel" :class="{ 'consumption-history-card': type === 'consumption' }">
      <div class="section-title" :class="{ 'consumption-section-title': type === 'consumption' }">
        <template v-if="type === 'consumption'">
          <i class="consumption-section-icon is-history"><History :size="20" /></i>
          <div>
            <h3>消耗记录</h3>
            <p class="consumption-section-description">按时间倒序展示登记、反消耗及红冲结果</p>
          </div>
        </template>
        <template v-else>
          <History :size="20" />
          <h3>{{ type === 'settlement' ? '结算明细' : type === 'delivery' ? '拣配记录' : '业务单据' }}</h3>
        </template>
      </div>
      <div v-if="type === 'settlement'" class="settlement-table-summary">
        <span>共 {{ totalItems }} 条结算明细</span>
        <span class="settlement-table-hint">长字段已分组展示，可横向滚动查看全部信息</span>
      </div>
      <div v-if="type === 'settlement'" class="table-scroll settlement-detail-scroll">
        <table class="master-table settlement-detail-table">
          <thead>
            <tr>
              <th class="settlement-col-document">结算单</th>
              <th class="settlement-col-location">结算归属</th>
              <th class="settlement-col-product">商品信息</th>
              <th class="settlement-col-spec">规格 / 单位</th>
              <th class="settlement-col-manufacturer">厂家 / 注册证</th>
              <th class="settlement-col-supplier">结算供应商</th>
              <th class="numeric-cell">单价</th>
              <th class="numeric-cell">数量</th>
              <th class="numeric-cell">金额</th>
              <th class="settlement-col-policy">带量 / 医保</th>
              <th class="settlement-col-trace">追溯码</th>
              <th class="settlement-col-finance">患者 / 财务科室</th>
              <th class="settlement-col-time">结算信息</th>
              <th class="settlement-col-status">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="14" class="approval-empty">正在加载结算明细...</td>
            </tr>
            <tr v-else-if="!rows.length">
              <td colspan="14" class="approval-empty">暂无结算明细</td>
            </tr>
            <tr v-for="row in rows" v-else :key="String(row.settlementItemId)">
              <td class="settlement-col-document">
                <div class="settlement-cell-stack" :title="String(row.settlementNo || '-')">
                  <strong>{{ row.settlementNo || '-' }}</strong>
                  <span class="settlement-cell-meta">ID：{{ row.settlementId || '-' }}</span>
                </div>
              </td>
              <td class="settlement-col-location">
                <div class="settlement-cell-stack">
                  <strong :title="String(row.settlementDept || '-')">{{ row.settlementDept || '-' }}</strong>
                  <span class="settlement-cell-meta" :title="String(row.settlementWarehouse || '-')">库房：{{ row.settlementWarehouse || '-' }}</span>
                </div>
              </td>
              <td class="settlement-col-product">
                <div class="settlement-cell-stack">
                  <strong :title="String(row.productName || '-')">{{ row.productName || '-' }}</strong>
                  <span class="settlement-cell-meta" :title="String(row.productCode || '-')">编码：{{ row.productCode || '-' }}</span>
                  <span class="settlement-cell-meta" :title="String(row.genericName || '-')">通用名：{{ row.genericName || '-' }}</span>
                </div>
              </td>
              <td class="settlement-col-spec">
                <div class="settlement-cell-stack">
                  <span :title="String(row.specModel || '-')">{{ row.specModel || '-' }}</span>
                  <span class="settlement-cell-meta">单位：{{ row.unit || '-' }}</span>
                </div>
              </td>
              <td class="settlement-col-manufacturer">
                <div class="settlement-cell-stack">
                  <strong :title="String(row.manufacturerName || '-')">{{ row.manufacturerName || '-' }}</strong>
                  <span class="settlement-cell-meta" :title="String(row.registrationNo || '-')">注册证：{{ row.registrationNo || '-' }}</span>
                </div>
              </td>
              <td class="settlement-col-supplier" :title="String(row.settlementSupplier || '-')">{{ row.settlementSupplier || '-' }}</td>
              <td class="numeric-cell">{{ row.unitPrice ?? '-' }}</td>
              <td class="numeric-cell">{{ row.settlementQuantity ?? '-' }}</td>
              <td class="numeric-cell amount-cell">{{ row.settlementAmount ?? '-' }}</td>
              <td class="settlement-col-policy">
                <div class="settlement-cell-stack settlement-policy-list">
                  <span><b>带量</b>{{ row.volumeBased || '-' }}<small>{{ row.volumeBasedType || '-' }}</small></span>
                  <span><b>医保</b>{{ row.medicalInsurancePayment || '-' }}<small>{{ row.medicalInsurancePaymentType || '-' }}</small></span>
                </div>
              </td>
              <td class="settlement-col-trace">
                <div class="settlement-cell-stack settlement-code-list">
                  <span :title="String(row.uid || '-')"><b>UID</b>{{ row.uid || '-' }}</span>
                  <span :title="String(row.uniqueCode || '-')"><b>唯一码</b>{{ row.uniqueCode || '-' }}</span>
                  <span :title="String(row.quotaPackageCode || '-')"><b>定数包</b>{{ row.quotaPackageCode || '-' }}</span>
                </div>
              </td>
              <td class="settlement-col-finance">
                <div class="settlement-cell-stack">
                  <strong :title="String(row.patientName || '-')">患者：{{ row.patientName || '-' }}</strong>
                  <span class="settlement-cell-meta" :title="String(row.financeDepartment || '-')">财务科室：{{ row.financeDepartment || '-' }}</span>
                </div>
              </td>
              <td class="settlement-col-time">
                <div class="settlement-cell-stack">
                  <strong>{{ row.settlementOperator || '-' }}</strong>
                  <span class="settlement-cell-meta">结算：{{ row.settlementTime || '-' }}</span>
                  <span class="settlement-cell-meta">创建：{{ row.createTime || '-' }}</span>
                </div>
              </td>
              <td class="settlement-col-status">
                <span class="status-badge" :class="row.status === 'pending_confirm' ? 'pending' : 'enabled'">{{ formatStatusText(row.status) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="table-scroll">
        <table class="master-table purchase-table" :class="{ 'consumption-history-table': type === 'consumption' }">
          <thead>
            <tr>
              <th>{{ type === 'consumption' ? '消耗单号' : '单据号' }}</th>
              <th>{{ type === 'consumption' ? '科室 / 库房' : '来源/科室' }}</th>
              <th>商品编码</th>
              <th>商品名称</th>
              <th>{{ type === 'consumption' ? '数量' : '数量/金额' }}</th>
              <th>状态</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="approval-empty">正在加载闭环数据...</td>
            </tr>
            <tr v-else-if="!rows.length">
              <td colspan="8" class="approval-empty">
                <EmptyState :message="type === 'delivery' ? '暂无拣配记录' : '暂无业务单据'" />
              </td>
            </tr>
            <tr v-for="row in rows" v-else :key="String(row.bizNo)">
              <td>{{ row.bizNo }}</td>
              <td>
                <template v-if="type === 'delivery'">
                  {{ row.deptName || '-' }}
                </template>
                <template v-else-if="type === 'consumption'">
                  <strong>{{ row.deptName || '-' }}</strong>
                  <span class="muted-cell"><Building2 :size="13" />{{ row.warehouseName || '-' }}</span>
                </template>
                <template v-else>{{ row.deptName || row.sourceNo || row.supplierName || row.deviceNo || formatBusinessText(row.eventType) }}</template>
              </td>
              <td>{{ row.productCode || '-' }}</td>
              <td>
                <template v-if="type === 'delivery'">
                  {{ row.productName || '-' }}
                  <div v-if="row.labelNos" class="package-label-chips">
                    <button
                      v-for="labelNo in String(row.labelNos).split(', ')"
                      :key="labelNo"
                      class="btn-link package-label-chip"
                      type="button"
                      :title="`查看定数包 ${labelNo} 明细`"
                      @click="openPackageDetail(labelNo)"
                    >
                      {{ labelNo }}
                    </button>
                  </div>
                </template>
                <template v-else-if="type === 'consumption'"><strong>{{ row.productName || '-' }}</strong></template>
                <template v-else>{{ row.productName || row.period || formatBusinessText(row.operationType) }}</template>
              </td>
              <td>
                <template v-if="type === 'shortage'">
                  <strong>{{ row.quantity || '-' }}</strong>
                  <span class="muted-cell">周期 {{ row.periodDays || '-' }} 天 / 建议 {{ row.formulaReplenishQty ?? '-' }}</span>
                  <span v-if="Number(row.manualAdjusted || 0) === 1" class="muted-cell">人工调整</span>
                </template>
                <template v-else>{{ row.quantity || row.amount || '-' }}</template>
              </td>
              <td>
                <span
                  class="status-badge"
                  :class="type === 'consumption' ? (row.status === 'confirmed' ? 'enabled' : ['reversed', 'red_flushed'].includes(String(row.status)) ? 'disabled' : 'pending') : 'pending'"
                >{{ formatStatusText(row.status) }}</span>
              </td>
              <td>{{ row.createTime || '-' }}</td>
              <td>
                <div class="row-actions">
                  <button
                    v-if="type === 'delivery' && row.status === 'picked'"
                    type="button"
                    :disabled="deliverySubmitting || loading"
                    @click="runAction('sign', row)"
                  >
                    <CheckCircle2 :size="15" />
                    确认签收
                  </button>
                  <span v-else-if="type === 'delivery'">已签收</span>
                  <button v-if="type === 'consumption' && row.status === 'confirmed'" type="button" @click="runAction('reverse', row)">
                    <RotateCcw :size="15" />
                    反消耗
                  </button>
                  <button v-if="type === 'requisition' && row.status === 'pending_approval'" type="button" @click="runAction('approveRequisition', row)">
                    <CheckCircle2 :size="15" />审批
                  </button>
                  <button v-if="type === 'requisition' && row.status === 'pending_approval'" type="button" @click="runAction('rejectRequisition', row)">
                    驳回
                  </button>
                  <span v-if="!['delivery', 'consumption'].includes(type)">已联动</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="currentPage"
        :size="pageSize"
        :total="totalItems"
        :loading="loading"
        @change-page="changePage"
        @change-size="changePageSize"
      />
    </section>

    <div v-if="analysisDialogOpen" class="attachment-preview-mask" @click.self="analysisDialogOpen = false">
      <section class="supplier-dialog replenishment-analysis-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>{{ analysisMeta?.deptName || form.deptName }} / {{ analysisMeta?.warehouseName || form.warehouseName }}</p>
            <h3>智能补货分析</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="analysisDialogOpen = false">
            <X :size="18" />
          </button>
        </header>

        <div class="analysis-summary">
          <span>分析周期：{{ analysisMeta?.selectedPeriodDays || effectiveReplenishmentDays }} 天</span>
          <span>公式：周期内科室出库数量 - 当前库房库存</span>
          <span>结果：{{ analysisRows.length }} 条建议</span>
        </div>
        <StatusMessage :message="analysisError" tone="error" />

        <div class="table-scroll">
          <table class="master-table replenishment-analysis-table">
            <thead>
              <tr>
                <th>商品</th>
                <th>科室 / 库房</th>
                <th>5 天出库</th>
                <th>7 天出库</th>
                <th>15 天出库</th>
                <th>30 天出库</th>
                <th>当前库存</th>
                <th>建议补货</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="analysisLoading">
                <td colspan="9" class="approval-empty"><EmptyState message="正在分析周期出库数据..." /></td>
              </tr>
              <tr v-else-if="analysisRows.length === 0">
                <td colspan="9" class="approval-empty"><EmptyState message="暂无可补货建议" /></td>
              </tr>
              <tr
                v-for="row in analysisRows"
                v-else
                :key="`${row.deptName}-${row.warehouseName}-${row.productCode}`"
              >
                <td>
                  <strong>{{ row.productName || '-' }}</strong>
                  <span class="muted-cell">{{ row.productCode || '-' }}</span>
                </td>
                <td>
                  <strong>{{ row.deptName || '-' }}</strong>
                  <span class="muted-cell">{{ row.warehouseName || '-' }}</span>
                </td>
                <td>{{ row.issue5 ?? 0 }}</td>
                <td>{{ row.issue7 ?? 0 }}</td>
                <td>{{ row.issue15 ?? 0 }}</td>
                <td>{{ row.issue30 ?? 0 }}</td>
                <td>{{ row.currentQty ?? 0 }}</td>
                <td>
                  <input
                    class="analysis-qty-input"
                    type="number"
                    min="0"
                    :value="row.recommendedQty ?? 0"
                    @input="updateAnalysisQty(row, $event)"
                  />
                  <span class="muted-cell">{{ row.formulaText }}</span>
                </td>
                <td>
                  <button class="btn btn-primary" type="button" @click="createTaskFromAnalysis(row)">
                    <Save :size="15" />
                    生成任务
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
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
          <button class="btn-icon" type="button" aria-label="关闭" @click="closePackageDetail">
            <X :size="18" />
          </button>
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
.delivery-acceptance-page .picking-workbench { grid-template-columns: minmax(0, 1fr); }
.delivery-acceptance-page .picking-panel:first-child :is(th, td):first-child { position: sticky; left: 0; z-index: 1; background: white; }
.delivery-acceptance-page :is(.picking-panel:first-child, .picking-panel--loose) :is(th, td):last-child { position: sticky; right: 0; z-index: 1; background: white; }
.delivery-acceptance-page .picking-panel tr.selected :is(td:first-child, td:last-child) { background: #e8f7f5; }
.package-detail-content { min-width: 0; max-height: 72vh; overflow: auto; }
.package-detail-content header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.package-detail-content header h3 { overflow-wrap: anywhere; }
.package-detail-content .package-detail-info { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); gap: 12px; margin-block: 16px; }
.package-detail-content .package-detail-info strong { overflow-wrap: anywhere; }
.package-detail-content .table-scroll { overflow: auto; max-height: 260px; margin-block: 12px; }
.package-detail-content .master-table { min-width: 640px; }
.consumption-saas-page {
  --consumption-blue: #1677ff;
  --consumption-blue-deep: #0958d9;
  --consumption-green: #16a36a;
  --consumption-text: #172033;
  --consumption-muted: #657086;
  --consumption-border: #e5eaf0;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: none;
  gap: 24px;
  padding: 24px;
  color: var(--consumption-text);
  background: #f5f7fa;
  font-family: "Segoe UI Variable", "Inter", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.consumption-saas-page .breadcrumb-line {
  margin: 0;
  color: #8993a5;
  font-size: 13px;
}

.consumption-saas-page .breadcrumb-line strong {
  color: #465269;
  font-weight: 500;
}

.consumption-saas-page .detail-heading {
  box-sizing: border-box;
  min-height: 112px;
  margin: 0;
  padding: 20px 24px;
  border: 1px solid var(--consumption-border);
  border-radius: 12px;
  background: linear-gradient(115deg, #ffffff 0%, #ffffff 72%, #f0f7ff 100%);
  box-shadow: 0 2px 8px rgb(31 50 79 / 4%);
}

.consumption-saas-page .detail-heading h2 {
  margin: 6px 0 5px;
  color: #111c2e;
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.consumption-saas-page .detail-heading small {
  color: var(--consumption-muted);
  font-size: 14px;
  line-height: 1.6;
}

.consumption-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--consumption-blue-deep);
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.08em;
}

.consumption-saas-page .btn,
.consumption-saas-page .hospital-query-grid input,
.consumption-saas-page .hospital-query-grid select {
  box-sizing: border-box;
  min-height: 40px;
  height: 40px;
  border-radius: 8px;
  font-family: inherit;
}

.consumption-saas-page .btn {
  padding: 0 16px;
  border-color: #d7dde7;
  color: #33415c;
  background: #fff;
  font-weight: 550;
}

.consumption-saas-page .btn:hover {
  border-color: #91caff;
  color: var(--consumption-blue);
  background: #f7fbff;
}

.consumption-saas-page .btn-primary {
  border-color: var(--consumption-blue);
  color: #fff;
  background: var(--consumption-blue);
  box-shadow: 0 2px 5px rgb(22 119 255 / 16%);
}

.consumption-saas-page .btn-primary:hover {
  border-color: var(--consumption-blue-deep);
  color: #fff;
  background: var(--consumption-blue-deep);
}

.consumption-saas-page .closure-stat-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
}

.consumption-saas-page .closure-stat-grid article {
  grid-column: span 3;
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--consumption-border);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 8px rgb(31 50 79 / 3%);
}

.consumption-saas-page .closure-stat-grid article > div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.consumption-saas-page .closure-stat-grid article span {
  color: var(--consumption-muted);
  font-size: 13px;
}

.consumption-saas-page .closure-stat-grid article strong {
  color: #14213a;
  font-size: 24px;
  font-weight: 650;
  line-height: 1.1;
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
  background: #eaf4ff;
  font-style: normal;
}

.consumption-stat-icon.is-confirmed,
.consumption-stat-icon.is-quantity {
  color: var(--consumption-green);
  background: #eaf8f2;
}

.consumption-stat-icon.is-reversed {
  color: #7c879a;
  background: #f0f2f5;
}

.consumption-saas-page .hospital-catalog-panel {
  box-sizing: border-box;
  min-width: 0;
  margin: 0;
  padding: 24px;
  border: 1px solid var(--consumption-border);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 8px rgb(31 50 79 / 4%);
}

.consumption-section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.consumption-section-title > div {
  min-width: 0;
}

.consumption-section-title h3 {
  margin: 0;
  color: #172033;
  font-size: 17px;
  font-weight: 650;
}

.consumption-section-description {
  display: block !important;
  margin: 4px 0 0;
  color: var(--consumption-muted);
  font-size: 13px;
  line-height: 1.45;
}

.consumption-section-icon.is-history {
  color: var(--consumption-green);
  background: #eaf8f2;
}

.consumption-live-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-left: auto;
  padding: 5px 10px;
  border: 1px solid #b7ebd3;
  border-radius: 999px;
  color: #087a4d;
  background: #f0fbf6;
  font-size: 12px;
  font-weight: 550;
}

.consumption-live-badge > span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #20b878;
  box-shadow: 0 0 0 3px rgb(32 184 120 / 12%);
}

.consumption-saas-page .consumption-form-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 20px 16px;
  padding-top: 20px;
  border-top: 1px solid #eef1f5;
}

.consumption-saas-page .consumption-form-grid > label {
  min-width: 0;
}

.consumption-saas-page .consumption-form-grid > label > span {
  margin-bottom: 8px;
  color: #34415a;
  font-size: 13px;
  font-weight: 550;
}

.consumption-saas-page .consumption-form-grid input,
.consumption-saas-page .consumption-form-grid select {
  width: 100%;
  border-color: #d8dee8;
  color: #26334d;
  background-color: #fff;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.consumption-saas-page .consumption-form-grid input:focus,
.consumption-saas-page .consumption-form-grid select:focus {
  border-color: var(--consumption-blue);
  outline: 0;
  box-shadow: 0 0 0 3px rgb(22 119 255 / 10%);
}

.consumption-grid-dept,
.consumption-grid-warehouse {
  grid-column: span 3 !important;
}

.consumption-grid-code {
  grid-column: span 6 !important;
}

.consumption-grid-resolved {
  grid-column: span 6 !important;
}

.consumption-grid-quantity {
  grid-column: span 2 !important;
}

.consumption-grid-unique {
  grid-column: span 4 !important;
}

.consumption-saas-page .consumption-code-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.consumption-saas-page .consumption-resolved {
  box-sizing: border-box;
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid #b7ebd3;
  border-left: 3px solid var(--consumption-green);
  border-radius: 8px;
  color: #174c38;
  background: #f4fbf7;
}

.consumption-saas-page .consumption-resolved small {
  color: #587264;
}

.consumption-action-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #eef1f5;
}

.consumption-action-hint {
  margin-right: auto;
  color: #7b8597;
  font-size: 12px;
}

.consumption-history-card .table-scroll {
  overflow: auto;
  border: 1px solid #e7ebf1;
  border-radius: 10px;
}

.consumption-history-table {
  min-width: 1050px;
}

.consumption-history-table thead th {
  height: 44px;
  border-bottom-color: #dfe5ed;
  color: #45536b;
  background: #f7f9fc;
  font-size: 13px;
  font-weight: 600;
}

.consumption-history-table tbody td {
  height: 52px;
  border-bottom-color: #edf0f4;
  color: #34415a;
  font-size: 13px;
}

.consumption-history-table tbody tr:hover td {
  background: #f7fbff;
}

.consumption-history-table .muted-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  color: #7b8597;
}

.consumption-history-table .row-actions button {
  color: var(--consumption-blue-deep);
}

.consumption-saas-page :deep(.pagination-controls) {
  margin-top: 18px;
}

@media (max-width: 1200px) {
  .consumption-saas-page .closure-stat-grid article {
    grid-column: span 6;
  }

  .consumption-grid-dept,
  .consumption-grid-warehouse {
    grid-column: span 6 !important;
  }

  .consumption-grid-code,
  .consumption-grid-resolved {
    grid-column: span 12 !important;
  }

  .consumption-grid-quantity {
    grid-column: span 4 !important;
  }

  .consumption-grid-unique {
    grid-column: span 8 !important;
  }
}

@media (max-width: 760px) {
  .consumption-saas-page {
    gap: 16px;
    padding: 16px;
  }

  .consumption-saas-page .detail-heading,
  .consumption-saas-page .hospital-catalog-panel {
    padding: 18px;
  }

  .consumption-saas-page .detail-heading {
    align-items: flex-start;
  }

  .consumption-saas-page .closure-stat-grid article,
  .consumption-grid-dept,
  .consumption-grid-warehouse,
  .consumption-grid-code,
  .consumption-grid-resolved,
  .consumption-grid-quantity,
  .consumption-grid-unique {
    grid-column: span 12 !important;
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
</style>
