<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardCheck,
  FileCheck2,
  History,
  PackageCheck,
  RefreshCw,
  RotateCcw,
  Save,
  Smartphone,
  Truck,
  X
} from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import {
  bindHighValuePatient,
  confirmPicking,
  confirmSettlement,
  createColdChainException,
  createConsumption,
  createDelivery,
  createRecall,
  createRequisition,
  fetchClosureList,
  fetchClosureOptions,
  fetchClosureOverview,
  fetchPickingPackageLabels,
  fetchPickingRequisitions,
  processRequisition,
  generateShortage,
  receiveHighValueBillingCallback,
  reverseConsumption,
  signDelivery,
  smartReplenishmentAnalysis,
  uploadPdaOffline,
  type ClosureOptions
} from '../../api/operationalClosure'
import { fetchDepartmentWarehouses } from '../../api/masterData'
import { formatBusinessText, formatStatusText } from '../../utils/chineseDisplay'

const route = useRoute()
const loading = ref(false)
const message = ref('')
const rows = ref<Record<string, unknown>[]>([])
const analysisRows = ref<Record<string, unknown>[]>([])
const pickingRequisitionRows = ref<Record<string, unknown>[]>([])
const pickingPackageRows = ref<Record<string, unknown>[]>([])
const selectedPickingRequisitionNo = ref('')
const selectedPickingItemId = ref('')
const selectedPickingLabels = ref<string[]>([])
const linkedWarehouses = ref<Array<{ code: string; name: string; selected?: boolean | number }>>([])
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
const titleMap: Record<string, string> = {
  shortage: '缺货补货闭环',
  delivery: '拣配配送',
  requisition: '科室申领',
  consumption: '科室消耗 / 反消耗 / 红冲',
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
  return '围绕库存、申领、配送、消耗和财务事实形成可追溯业务流水。'
})
const stats = computed(() => [
  { label: '缺货任务', value: summary.value.shortageTasks ?? 0 },
  { label: '待签收配送', value: summary.value.pendingDeliveries ?? 0 },
  { label: '消耗单据', value: summary.value.consumptions ?? 0 },
  { label: '风险事件', value: summary.value.riskEvents ?? 0 }
])

const replenishmentDayOptions = [5, 7, 15, 30]
const selectedDepartment = computed(() => options.value.departments.find(item => item.deptName === form.deptName))
const warehouseChoices = computed(() => {
  if (type.value !== 'shortage') return options.value.warehouses
  return linkedWarehouses.value.map(item => ({ warehouseName: item.name }))
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
  if (type.value !== 'shortage') return
  const deptCode = selectedDepartment.value?.deptCode
  if (!deptCode) {
    linkedWarehouses.value = []
    form.warehouseName = ''
    return
  }
  try {
    const warehouses = await fetchDepartmentWarehouses(deptCode)
    linkedWarehouses.value = warehouses.filter(item => Number(item.selected ?? 0) === 1)
    if (!linkedWarehouses.value.some(item => item.name === form.warehouseName)) {
      form.warehouseName = linkedWarehouses.value[0]?.name || ''
    }
  } catch (error) {
    linkedWarehouses.value = []
    form.warehouseName = ''
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
    if (type.value === 'shortage') {
      await loadLinkedWarehouses()
    } else if (!form.warehouseName && optionData.warehouses[0]) {
      form.warehouseName = optionData.warehouses[0].warehouseName
    }
    if (!form.productCode && optionData.products[0]) form.productCode = optionData.products[0].productCode
    if (type.value === 'delivery') {
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
    selectedPickingLabels.value = []
    pickingPackageRows.value = []
  } else if (selectedPickingItemId.value) {
    await loadPickingLabels()
  }
}

async function selectPickingRequisition(row: Record<string, unknown>) {
  selectedPickingRequisitionNo.value = String(row.requisitionNo || '')
  selectedPickingItemId.value = String(row.itemId || '')
  form.requisitionNo = selectedPickingRequisitionNo.value
  form.deptName = String(row.deptName || form.deptName)
  form.productCode = String(row.productCode || form.productCode)
  form.quantity = Number(row.remainingQty || row.requisitionQty || 1)
  selectedPickingLabels.value = []
  await loadPickingLabels()
}

async function loadPickingLabels() {
  if (!selectedPickingRequisitionNo.value || !selectedPickingItemId.value) {
    pickingPackageRows.value = []
    return
  }
  const result = await fetchPickingPackageLabels({
    requisitionNo: selectedPickingRequisitionNo.value,
    itemId: selectedPickingItemId.value,
    warehouseName: form.warehouseName
  })
  pickingPackageRows.value = result.rows || []
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

async function confirmSelectedPicking() {
  if (!selectedPickingRequisitionNo.value || !selectedPickingItemId.value) {
    message.value = '请先选择科室申领明细'
    return
  }
  if (!form.warehouseName) {
    message.value = '请先选择中心库'
    return
  }
  if (!selectedPickingLabels.value.length) {
    message.value = '请先勾选定数包标签'
    return
  }
  const result = await confirmPicking({
    requisitionNo: selectedPickingRequisitionNo.value,
    itemId: selectedPickingItemId.value,
    warehouseName: form.warehouseName,
    labelNos: selectedPickingLabels.value
  })
  form.deliveryNo = String(result.deliveryNo || '')
  message.value = `拣配出库完成：${result.deliveryNo}，绑定 ${result.labelCount} 个定数包`
  selectedPickingLabels.value = []
  await loadData()
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

async function runAction(action: string, row?: Record<string, unknown>) {
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
    result = await createConsumption(form)
    form.consumptionNo = String(result.consumptionNo || '')
    message.value = `科室消耗已登记：${result.consumptionNo}`
  }
  if (action === 'reverse') {
    result = await reverseConsumption(String(row?.bizNo || form.consumptionNo))
    message.value = `反消耗红冲已生成：${result.flushNo}`
  }
  if (action === 'confirmSettlement') {
    result = await confirmSettlement(String(row?.bizNo || ''))
    message.value = `结算单已确认：${result.settlementNo}`
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
    result = await createRecall(form)
    message.value = `召回隔离已登记：${result.recallNo}`
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
  if (!form.deptName) {
    message.value = '请先选择科室'
    return
  }
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
  if (type.value === 'shortage') {
    loadLinkedWarehouses()
  }
})
watch(() => form.warehouseName, () => {
  if (type.value === 'delivery') {
    selectedPickingLabels.value = []
    loadPickingLabels()
  }
})
</script>

<template>
  <section class="purchase-page closure-page">
    <div class="breadcrumb-line">
      <span>一期上线闭环</span>
      <strong>{{ title }}</strong>
    </div>

    <div class="detail-heading">
      <div>
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
      <article v-for="item in stats" :key="item.label">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </div>

    <p v-if="message" class="inline-message">{{ message }}</p>
    <p v-if="type === 'settlement'" class="inline-message">结算数据在验收入库、科室消耗或患者计费达到批次结算点时自动生成，无需人工生成。</p>

    <section v-if="type !== 'settlement'" class="hospital-catalog-panel">
      <div class="section-title">
        <ClipboardCheck :size="20" />
        <h3>业务动作</h3>
      </div>
      <div class="hospital-query-grid closure-form-grid">
        <label>
          <span>科室</span>
          <select v-model="form.deptName">
            <option v-for="item in options.departments" :key="item.deptName" :value="item.deptName">{{ item.deptName }}</option>
          </select>
        </label>
        <label>
          <span>库房</span>
          <select v-model="form.warehouseName">
            <option v-for="item in warehouseChoices" :key="item.warehouseName" :value="item.warehouseName">{{ item.warehouseName }}</option>
          </select>
        </label>
        <label v-if="!['shortage', 'delivery'].includes(type)">
          <span>商品</span>
          <select v-model="form.productCode">
            <option v-for="item in options.products" :key="item.productCode" :value="item.productCode">
              {{ item.productCode }} / {{ item.productName }}
            </option>
          </select>
        </label>
        <label v-if="!['shortage', 'delivery'].includes(type)"><span>数量</span><input v-model.number="form.quantity" type="number" min="1" /></label>
        <label v-if="type === 'pda'"><span>设备号</span><input v-model="form.deviceNo" /></label>
        <label v-if="type === 'risk'"><span>温度/原因</span><input v-model="form.temperature" type="number" /></label>
        <label v-if="type === 'high-value'"><span>患者号</span><input v-model="form.patientNo" /></label>
        <label v-if="['requisition', 'delivery', 'consumption', 'high-value'].includes(type)" class="wide-field">
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
      <div class="hospital-action-row">
        <button v-if="type === 'shortage'" class="btn btn-primary" type="button" @click="openSmartAnalysis">
          <AlertTriangle :size="18" />
          智能补货分析
        </button>
        <button v-if="type === 'requisition'" class="btn btn-primary" type="button" @click="runAction('requisition')">
          <Save :size="18" />
          提交申领
        </button>
        <button v-if="type === 'delivery'" class="btn btn-primary" type="button" @click="runAction('delivery')">
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
        <button v-if="type === 'risk'" class="btn btn-primary" type="button" @click="runAction('cold')">
          <AlertTriangle :size="18" />
          冷链异常
        </button>
        <button v-if="type === 'risk'" class="btn" type="button" @click="runAction('recall')">
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
                  <th>申领数量</th>
                  <th>已拣配</th>
                  <th>待拣配</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!pickingRequisitionRows.length">
                  <td colspan="7" class="approval-empty">暂无待拣配申领单</td>
                </tr>
                <tr
                  v-for="row in pickingRequisitionRows"
                  :key="`${row.requisitionNo}-${row.itemId}`"
                  :class="{ selected: selectedPickingItemId === String(row.itemId) }"
                >
                  <td>{{ row.requisitionNo }}</td>
                  <td>{{ row.deptName }}</td>
                  <td>
                    <strong>{{ row.productName }}</strong>
                    <span class="muted-cell">{{ row.productCode }}</span>
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

        <section class="picking-panel">
          <div class="section-title compact">
            <h3>可用定数包</h3>
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
                  <td>{{ row.labelNo }}</td>
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
      </div>
    </section>

    <section class="hospital-catalog-panel">
      <div class="section-title">
        <History :size="20" />
        <h3>{{ type === 'settlement' ? '结算明细' : type === 'delivery' ? '拣配记录' : '业务单据' }}</h3>
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
              <th class="settlement-col-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="15" class="approval-empty">正在加载结算明细...</td>
            </tr>
            <tr v-else-if="!rows.length">
              <td colspan="15" class="approval-empty">暂无结算明细</td>
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
              <td class="settlement-col-action">
                <button
                  v-if="row.status === 'pending_confirm'"
                  class="settlement-confirm-btn"
                  type="button"
                  @click="runAction('confirmSettlement', { ...row, bizNo: row.settlementNo })"
                >
                  <CheckCircle2 :size="15" />
                  确认
                </button>
                <span v-else class="settlement-confirmed-text"><CheckCircle2 :size="15" />已确认</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="table-scroll">
        <table class="master-table purchase-table">
          <thead>
            <tr>
              <th>单据号</th>
              <th>来源/科室</th>
              <th>商品编码</th>
              <th>商品名称</th>
              <th>数量/金额</th>
              <th>状态</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="approval-empty">正在加载闭环数据...</td>
            </tr>
            <tr v-for="row in rows" v-else :key="String(row.bizNo)">
              <td>{{ row.bizNo }}</td>
              <td>{{ row.deptName || row.sourceNo || row.supplierName || row.deviceNo || formatBusinessText(row.eventType) }}</td>
              <td>{{ row.productCode || '-' }}</td>
              <td>{{ row.productName || row.period || formatBusinessText(row.operationType) }}</td>
              <td>
                <template v-if="type === 'shortage'">
                  <strong>{{ row.quantity || '-' }}</strong>
                  <span class="muted-cell">周期 {{ row.periodDays || '-' }} 天 / 建议 {{ row.formulaReplenishQty ?? '-' }}</span>
                  <span v-if="Number(row.manualAdjusted || 0) === 1" class="muted-cell">人工调整</span>
                </template>
                <template v-else>{{ row.quantity || row.amount || '-' }}</template>
              </td>
              <td><span class="status-badge pending">{{ formatStatusText(row.status) }}</span></td>
              <td>{{ row.createTime || '-' }}</td>
              <td>
                <div class="row-actions">
                  <button
                    v-if="type === 'delivery' && row.status === 'picked'"
                    type="button"
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
        <p v-if="analysisError" class="inline-message analysis-error">{{ analysisError }}</p>

        <div class="table-scroll">
          <table class="master-table replenishment-analysis-table">
            <thead>
              <tr>
                <th>商品</th>
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
                <td colspan="8" class="approval-empty">正在分析周期出库数据...</td>
              </tr>
              <tr v-else-if="analysisRows.length === 0">
                <td colspan="8" class="approval-empty">暂无可补货建议</td>
              </tr>
              <tr v-for="row in analysisRows" v-else :key="String(row.productCode)">
                <td>
                  <strong>{{ row.productName || '-' }}</strong>
                  <span class="muted-cell">{{ row.productCode || '-' }}</span>
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
  </section>
</template>
