<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { AlertTriangle, ArrowLeft, Eye, History, Info, Minus, Plus, RotateCcw, Search, ShoppingCart, UserPlus, X } from '@lucide/vue'
import { ElDialog } from 'element-plus'
import 'element-plus/theme-chalk/el-dialog.css'
import PaginationControls from '../../components/common/PaginationControls.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import {
  analyzeDepartmentRequisition,
  createRequisition,
  fetchClosureList,
  fetchDepartmentRequisitionOptions,
  fetchDepartmentRequisitionWarehouses,
  fetchRequisitionItems,
  generateRequisitionsFromAnalysis,
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
import { useNotify } from '../../composables/useNotify'

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
  warehouseName: '',
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
const detailRows = ref<Record<string, unknown>[]>([])
const detailOpen = ref(false)
const detailLoading = ref(false)
const activeRequisitionNo = ref('')
const requisitionStarted = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)
const historyPage = ref(1)
const historySize = ref(20)
const historyTotal = ref(0)
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
const selectedWarehouseLabel = computed(() => filters.warehouseName || '全部关联库房')
const selectedItems = computed(() => products.value.filter((item) => item.selected))
const canCreateRequisition = computed(() => authStore.hasPermission('department-requisition:create'))
const canSmartAnalyze = computed(() => authStore.hasPermission('department-requisition:smart-analysis'))

function money(value: unknown) {
  const amount = Number(value || 0)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}

async function loadRequisitionOrders() {
  if (requisitionStarted.value) return
  loading.value = true
  error.value = ''
  try {
    const page = await fetchClosureList('requisition', {
      page: String(historyPage.value),
      size: String(historySize.value)
    })
    requisitionOrders.value = page.rows
    historyTotal.value = page.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '科室请购记录加载失败'
  } finally {
    loading.value = false
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
    error.value = err instanceof Error ? err.message : '科室选项加载失败'
  } finally {
    optionsLoading.value = false
  }
}

async function loadLinkedWarehouses() {
  linkedWarehouses.value = []
  filters.warehouseName = ''
  const deptCode = selectedDepartment.value?.deptCode
  if (!deptCode) return
  const warehouses = await fetchDepartmentRequisitionWarehouses(deptCode)
  linkedWarehouses.value = warehouses.filter((item) => Number(item.selected ?? 0) === 1)
  // 选择科室时默认带出科室所关联的库房
  filters.warehouseName = linkedWarehouses.value[0]?.name || ''
}

async function loadCatalog() {
  if (!requisitionStarted.value) return
  if (!filters.targetDeptCode) {
    clearQuantityDrafts()
    totalItems.value = 0
    loading.value = false
    error.value = '请先选择科室'
    return
  }
  const requestVersion = ++catalogRequestVersion
  const scope = JSON.stringify([filters.targetDeptCode, filters.warehouseName])
  if (scope !== draftScope) {
    quantityDrafts.clear()
    products.value = []
    draftScope = scope
  }
  loading.value = true
  error.value = ''
  try {
    const result = await fetchDepartmentRequisitionCatalog({
      deptCode: selectedDepartment.value?.deptCode || '',
      deptName: selectedDepartment.value?.deptName || '',
      warehouseName: filters.warehouseName,
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
    error.value = err instanceof Error ? err.message : '科室申领目录加载失败'
  } finally {
    if (requestVersion === catalogRequestVersion) loading.value = false
  }
}

async function startRequisition() {
  clearQuantityDrafts()
  notify.success('')
  error.value = ''
  if (!options.value.departments.length) {
    await loadOptions()
  } else {
    await loadLinkedWarehouses()
  }
  requisitionStarted.value = true
  currentPage.value = 1
  await loadCatalog()
}

function leaveRequisition() {
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

async function openDetails(requisitionNo: unknown) {
  const no = String(requisitionNo || '')
  if (!no) return
  activeRequisitionNo.value = no
  detailOpen.value = true
  detailLoading.value = true
  detailRows.value = []
  try {
    const result = await fetchRequisitionItems(no)
    detailRows.value = result.rows
  } catch (err) {
    error.value = err instanceof Error ? err.message : '科室请购明细加载失败'
  } finally {
    detailLoading.value = false
  }
}

function closeDetails() {
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
  const minimum = item.mode === 'loose' ? 0.0001 : 1
  item.quantity = Math.max(minimum, Number(item.quantity || minimum) + delta)
  normalizeQuantity(item)
}

function normalizeQuantity(item: RequisitionCatalogItem) {
  const quantity = Number(item.quantity)
  item.quantity = Number.isFinite(quantity) && quantity > 0 ? quantity : 1
  if (item.mode !== 'loose') item.quantity = Math.max(1, Math.floor(item.quantity))
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
      warehouseName: filters.warehouseName,
      sourceWarehouseId: submittedItems[0]?.sourceWarehouseId,
      items
    })
    submittedCount.value += submittedItems.length
    const requisitionNo = (result as { requisitionNo?: string }).requisitionNo
    notify.success(requisitionNo
      ? `已提交 ${submittedItems.length} 条科室申领明细，申请单号：${requisitionNo}`
      : `已提交 ${submittedItems.length} 条科室申领明细`)
    clearQuantityDrafts()
    await loadCatalog()
    await loadRequisitionOrders()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '科室申领提交失败'
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
  if (!filters.targetDeptCode) {
    notify.warning('请先选择科室')
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
      destinationWarehouseName: filters.warehouseName || undefined,
      selectedPeriodDays: smartPeriodDays.value
    })
    smartAnalysisId.value = result.analysisId
    smartRows.value = result.rows.map((row) => ({ ...row, selected: Number(row.recommendedQty || 0) > 0 }))
  } catch (err) {
    error.value = err instanceof Error ? err.message : '智能补货分析失败'
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
    error.value = err instanceof Error ? err.message : '智能补货生成申领单失败'
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
    warehouseName: '',
    mode: ''
  })
  currentPage.value = 1
  await loadLinkedWarehouses()
  await loadCatalog()
}

onMounted(async () => {
  await loadRequisitionOrders()
})

watch(() => filters.targetDeptCode, async () => {
  if (!requisitionStarted.value) return
  await loadLinkedWarehouses()
  currentPage.value = 1
  await loadCatalog()
})

watch(() => filters.warehouseName, async () => {
  if (!requisitionStarted.value) return
  currentPage.value = 1
  await loadCatalog()
})
</script>

<template>
  <section class="dept-req-page">
    <MdPageHeader
      crumb="供应链业务"
      title="科室申领"
      description="统一申请散货、定数包和高值耗材"
      :loading="loading"
      @refresh="refreshCurrentView"
    />

    <StatusMessage :message="error" tone="error" />
      <StatusMessage :message="message" tone="success" />

      <section class="dept-req-table-wrap dept-req-history-card">
        <div class="dept-req-section-title">
          <History :size="18" />
          <h3>历史申领单</h3>
          <button v-if="canCreateRequisition" class="btn btn-primary md-btn md-btn-primary dept-req-create-btn" type="button" @click="startRequisition">
            <UserPlus :size="16" />
            新增申领
          </button>
        </div>
        <table class="dept-req-table requisition-order-table">
          <thead>
            <tr>
              <th>单据号</th>
              <th>科室</th>
              <th>库房</th>
              <th>请购人</th>
              <th>时间</th>
              <th>商品总数量</th>
              <th>总金额</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="9"><EmptyState message="正在加载科室申领记录..." /></td>
            </tr>
            <tr v-else-if="error">
              <td colspan="9"><EmptyState message="科室申领记录加载失败" /></td>
            </tr>
            <template v-else>
              <tr v-for="order in requisitionOrders" :key="String(order.bizNo)">
                <td><strong>{{ order.bizNo || '-' }}</strong></td>
                <td>{{ order.deptName || '-' }}</td>
                <td>{{ order.warehouseName || '-' }}</td>
                <td>{{ order.applicantName || '-' }}</td>
                <td>{{ order.createTime || '-' }}</td>
                <td>{{ order.totalQuantity || 0 }}</td>
                <td>{{ money(order.totalAmount) }}</td>
                <td>{{ formatStatusText(order.status) }}</td>
                <td>
                  <button class="btn btn-sm" type="button" @click="openDetails(order.bizNo)">
                    <Eye :size="15" />
                    查看明细
                  </button>
                </td>
              </tr>
            </template>
            <tr v-if="!loading && !error && requisitionOrders.length === 0">
              <td colspan="9"><EmptyState message="暂无科室申领记录" /></td>
            </tr>
          </tbody>
        </table>
        <PaginationControls
          :page="historyPage"
          :size="historySize"
          :total="historyTotal"
          :loading="loading"
          @change-page="changeHistoryPage"
          @change-size="changeHistorySize"
        />
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
            <button class="btn md-btn" type="button" :disabled="loading || submitting" @click="openSmartReplenishment">
              <AlertTriangle :size="14" />
              智能补货
            </button>
            <button class="btn btn-primary md-btn md-btn-primary" type="button" :disabled="loading || submitting || selectedItems.length === 0" @click="submitSelectedRequisitions">
              <ShoppingCart :size="14" />
              提交申领
            </button>
          </div>
        </div>
      </template>

      <!-- 筛选条：单行紧凑 -->
      <div class="dept-req-filterbar">
        <label class="dept-req-filterbar-item">
          <span>科室</span>
          <select v-model="filters.targetDeptCode">
            <option value="">请选择科室</option>
            <option v-for="dept in options.departments" :key="dept.deptCode" :value="dept.deptCode">{{ dept.deptName }}</option>
          </select>
        </label>
        <label class="dept-req-filterbar-item">
          <span>库房</span>
          <select v-model="filters.warehouseName">
            <option value="">全部关联库房</option>
            <option v-for="warehouse in linkedWarehouses" :key="warehouse.code" :value="warehouse.name">{{ warehouse.name }}</option>
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
          <button class="btn btn-primary md-btn md-btn-primary" type="button" :disabled="loading" @click="searchCatalog">
            <Search :size="14" />
            查询
          </button>
          <button class="btn md-btn" type="button" :disabled="loading || submitting" @click="resetFilters">
            <RotateCcw :size="14" />
            重置
          </button>
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
              <button class="btn-text" type="button" :disabled="submitting" aria-label="减少申领数量" @click="stepQuantity(item, -1)"><Minus :size="12" /></button>
              <input v-model.number="item.quantity" type="number" :min="item.mode === 'loose' ? 0.0001 : 1" :step="item.mode === 'loose' ? 0.0001 : 1" aria-label="申领数量" :disabled="submitting" @change="normalizeQuantity(item)" />
              <button class="btn-text" type="button" :disabled="submitting" aria-label="增加申领数量" @click="stepQuantity(item, 1)"><Plus :size="12" /></button>
            </div>
            <span class="selected-total">{{ baseQtyByMode(item) }} {{ item.baseUnit }}</span>
            <button class="btn-text btn-text-danger" type="button" :disabled="submitting" @click="removeFromSelection(item)">
              <X :size="14" />
              移除
            </button>
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
          <table class="dept-req-table requisition-catalog-table">
            <thead>
              <tr>
                <th><input type="checkbox" @change="toggleAllProducts" /></th>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格型号</th>
                <th>生产厂家</th>
                <th>基本单位</th>
                <th>散货可用</th>
                <th>定数包可用</th>
                <th>默认申领</th>
                <th>申领数量</th>
                <th>本次折算</th>
                <th>模板</th>
                <th>状态</th>
                <th>单价</th>
                <th>是否收费</th>
                <th>是否集采</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading || optionsLoading">
                <td colspan="16"><EmptyState message="正在加载科室申领目录..." /></td>
              </tr>
              <tr v-else-if="error">
                <td colspan="16"><EmptyState message="科室申领目录加载失败" /></td>
              </tr>
              <template v-else>
                <tr
                  v-for="item in filteredProducts"
                  :key="item.productCode"
                  :class="{ selected: item.selected, low: item.requisitionStatus.includes('缺货') }"
                >
                  <td><input v-model="item.selected" :disabled="submitting" type="checkbox" /></td>
                  <td>{{ item.productCode }}</td>
                  <td>
                    <strong>{{ item.productName }}</strong>
                    <small>{{ item.supplierName }}</small>
                  </td>
                  <td>{{ item.specModel }}</td>
                  <td>{{ item.manufacturerName }}</td>
                  <td>{{ item.baseUnit }}</td>
                  <td>{{ item.looseAvailableQty }} {{ item.baseUnit }}</td>
                  <td>
                    <strong>{{ item.packageAvailableQty }} 包</strong>
                    <small v-if="item.packageQuantity">1 包 = {{ item.packageQuantity }} {{ item.packageUnit }}</small>
                  </td>
                  <td>
                    <select v-model="item.mode" :disabled="submitting" @change="normalizeQuantity(item)">
                      <option v-if="item.allowedModes.includes('loose')" value="loose">散货</option>
                      <option v-if="item.allowedModes.includes('quota_package')" value="quota_package">定数包</option>
                      <option v-if="item.allowedModes.includes('high_value')" value="high_value">高值耗材</option>
                    </select>
                  </td>
                  <td>
                    <div class="qty-stepper">
                      <button class="btn-text" type="button" :disabled="submitting" aria-label="减少申领数量" @click="stepQuantity(item, -1)"><Minus :size="12" /></button>
                      <input v-model.number="item.quantity" type="number" :min="item.mode === 'loose' ? 0.0001 : 1" :step="item.mode === 'loose' ? 0.0001 : 1" aria-label="申领数量" :disabled="submitting" @change="normalizeQuantity(item)" />
                      <button class="btn-text" type="button" :disabled="submitting" aria-label="增加申领数量" @click="stepQuantity(item, 1)"><Plus :size="12" /></button>
                    </div>
                    <small>{{ unitByMode(item) }}</small>
                    <small v-if="item.quantity > availableByMode(item)" class="shortage">超过当前可用库存，按实际需求申领</small>
                  </td>
                  <td>{{ baseQtyByMode(item) }} {{ item.baseUnit }}</td>
                  <td>
                    <strong>{{ item.templateCode }}</strong>
                    <small>{{ item.templateName }}</small>
                  </td>
                  <td>{{ formatStatusText(item.requisitionStatus) }}</td>
                  <td>{{ item.unitPrice }}</td>
                  <td>{{ Number(item.chargeable) === 1 ? '是' : '否' }}</td>
                  <td>{{ Number(item.centralized) === 1 ? '是' : '否' }}</td>
                </tr>
              </template>
              <tr v-if="!loading && !error && filteredProducts.length === 0">
                <td colspan="16"><EmptyState message="当前科室暂无可申领目录" /></td>
              </tr>
            </tbody>
          </table>
          <PaginationControls
            v-if="requisitionStarted"
            :page="currentPage"
            :size="pageSize"
            :total="totalItems"
            :loading="loading"
            @change-page="changePage"
            @change-size="changePageSize"
          />
        </div>
      </div>
    </ElDialog>

    <div v-if="detailOpen" class="dept-req-modal-backdrop" @click.self="closeDetails">
      <section class="dept-req-modal">
        <header>
          <div>
            <span>科室请购明细</span>
            <strong>{{ activeRequisitionNo }}</strong>
          </div>
          <button class="btn btn-sm" type="button" @click="closeDetails">
            <X :size="16" />
            关闭
          </button>
        </header>
        <div class="dept-req-table-wrap">
          <table class="dept-req-table requisition-detail-table">
            <thead>
              <tr>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>申领模式</th>
                <th>模板 / 换算</th>
                <th>厂家</th>
                <th>单价</th>
                <th>申请数量</th>
                <th>履约进度</th>
                <th>唯一码</th>
                <th>申请金额</th>
                <th>注册证</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="detailLoading">
                <td colspan="11"><EmptyState message="正在加载申领明细..." /></td>
              </tr>
              <tr v-else-if="detailRows.length === 0">
                <td colspan="11"><EmptyState message="暂无申领明细" /></td>
              </tr>
              <template v-else>
                <tr v-for="item in detailRows" :key="`${item.productCode}-${item.registrationNo}`">
                  <td>{{ item.productCode || '-' }}</td>
                  <td>{{ item.productName || '-' }}</td>
                  <td>{{ item.itemType === 'quota_package' ? '定数包' : item.itemType === 'high_value' ? '高值耗材' : '散货' }}</td>
                  <td>
                    <strong v-if="item.templateCode">{{ item.templateCode }} v{{ item.templateVersion }}</strong>
                    <small v-if="item.packageCount">{{ item.packageCount }} 包 × {{ item.packageQuantity }} {{ item.packageUnit }}</small>
                    <span v-if="!item.templateCode">-</span>
                  </td>
                  <td>{{ item.manufacturerName || '-' }}</td>
                  <td>{{ money(item.unitPrice) }}</td>
                  <td>{{ item.applyQuantity || 0 }}</td>
                  <td>{{ item.pickedQuantity || 0 }} / {{ item.applyQuantity || 0 }}，剩余 {{ item.remainingQuantity || 0 }}</td>
                  <td>{{ item.uniqueCodes || (item.itemType === 'high_value' ? '待拣配绑定' : '-') }}</td>
                  <td>{{ money(item.applyAmount) }}</td>
                  <td>{{ item.registrationNo || '-' }}</td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
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
      <div class="dept-req-table-wrap smart-table-wrap">
        <table class="dept-req-table smart-table">
          <thead>
            <tr><th>选择</th><th>科室 / 目标库</th><th>商品</th><th>模式</th><th>周期需求</th><th>目标库库存</th><th>来源库可用</th><th>建议申领</th></tr>
          </thead>
          <tbody>
            <tr v-if="smartLoading"><td colspan="8"><EmptyState message="正在计算智能补货建议..." /></td></tr>
            <tr v-for="row in smartRows" :key="row.analysisItemId">
              <td><input v-model="row.selected" type="checkbox" /></td>
              <td><strong>{{ row.deptName }}</strong><small>{{ row.warehouseName }}</small></td>
              <td><strong>{{ row.productName }}</strong><small>{{ row.productCode }}</small></td>
              <td>{{ row.itemMode === 'quota_package' ? '定数包' : row.itemMode === 'high_value' ? '高值耗材' : '散货' }}</td>
              <td>{{ row.periodDemand }} {{ row.baseUnit }}</td>
              <td>{{ row.currentQty }} {{ row.baseUnit }}</td>
              <td :class="{ shortage: Number(row.sourceAvailableQty) < Number(row.shortageQty) }">{{ row.sourceAvailableQty }} {{ row.baseUnit }}</td>
              <td>
                <input v-model.number="row.recommendedQty" type="number" min="0" step="1" :disabled="!row.selected" />
                <small>{{ row.itemMode === 'quota_package' ? '包' : row.baseUnit }}</small>
              </td>
            </tr>
            <tr v-if="!smartLoading && smartRows.length === 0"><td colspan="8"><EmptyState message="当前范围没有补货建议" /></td></tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <div class="dept-req-smart-footer">
          <button class="btn" type="button" :disabled="smartGenerating" @click="smartOpen = false">取消</button>
          <button class="btn btn-primary" type="button" :disabled="smartLoading || smartGenerating" @click="generateSmartRequisitions">
            {{ smartGenerating ? '正在生成...' : '确认并生成申领单' }}
          </button>
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

.dept-req-actions .btn {
  min-height: 32px;
  padding: 0 14px;
  font-size: 13px;
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
  background: var(--md-bg-card, #fff);
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
  font-size: 12px;
  font-weight: 500;
}

.dept-req-tags .blue {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 8%, #ffffff);
  color: var(--md-primary-deep, #0958d9);
}

.dept-req-tags .yellow {
  background: #fff7d6;
  color: #92600e;
}

.dept-req-tags .orange {
  background: #ffedd5;
  color: #9a3412;
}

/* ---------- 查询区 ---------- */
.dept-req-filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px 14px;
  padding: 16px 18px;
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--md-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
}

.dept-req-filters label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.dept-req-filters label > span {
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: 12px;
  font-weight: 600;
}

.dept-req-filters :is(input, select) {
  box-sizing: border-box;
  width: 100%;
  min-height: 32px;
  height: 32px;
  padding: 0 10px;
  color: var(--md-text, var(--spd-text-primary));
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
  font: inherit;
  font-size: 13px;
  outline: none;
}

.dept-req-filters :is(input, select):focus {
  border-color: var(--md-primary, var(--primary));
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--md-primary, var(--primary)) 14%, transparent);
}

.dept-req-filter-actions {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.dept-req-filter-actions .btn {
  min-height: 32px;
  padding: 0 14px;
  font-size: 13px;
  border-radius: var(--spd-radius-sm, 6px);
  white-space: nowrap;
}

.dept-req-submit-btn {
  margin-left: auto;
  border-color: var(--md-primary, var(--primary)) !important;
  background: var(--md-primary, var(--primary)) !important;
  color: #fff !important;
}

.dept-req-submit-btn:disabled {
  opacity: 0.55;
}

/* ---------- 列表卡片 ---------- */
.dept-req-table-wrap {
  min-width: 0;
  overflow: auto;
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--md-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
}

.dept-req-section-title {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px 12px;
  background: var(--md-bg-card, #fff);
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
  color: var(--md-primary, var(--primary));
}

.dept-req-section-title h3 {
  margin: 0;
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: 15px;
  font-weight: 600;
}

.dept-req-section-title > span {
  margin-left: auto;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

/* 段标题右侧的主操作：新增申领 / 智能补货 */
.dept-req-create-btn,
.dept-req-smart-btn {
  margin-left: auto;
  min-height: 32px;
  padding: 0 14px;
  font-size: 13px;
  border-radius: var(--spd-radius-sm, 6px);
}

/* ---------- 表格：md 密度 ---------- */
.dept-req-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
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
  background: var(--md-bg-subtle, #f6f8fb);
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: 12px;
  font-weight: 600;
}

.dept-req-table td {
  color: var(--md-text, var(--spd-text-primary));
  background: var(--md-bg-card, #fff);
}

.dept-req-table tbody tr:nth-child(even) td {
  background: color-mix(in srgb, var(--md-bg-subtle, #f6f8fb) 46%, #ffffff);
}

.dept-req-table tbody tr:hover td {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 6%, #ffffff);
}

.dept-req-table tr.selected td {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 9%, #ffffff) !important;
}

.dept-req-table tr.low:not(.selected) td {
  background: #fff7ed !important;
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
  font-size: 12px;
}

.requisition-catalog-table select {
  min-height: 30px;
  padding: 0 8px;
  color: var(--md-text, var(--spd-text-primary));
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
  font-size: 13px;
}

.requisition-catalog-table .shortage {
  color: #c2410c;
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
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--md-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
}

.dept-req-selected-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--md-bg-card, #fff);
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
}

.dept-req-selected-head strong {
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: 14px;
  font-weight: 600;
}

.dept-req-selected-head span {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 12px;
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
  font-size: 12px;
  white-space: nowrap;
}

.dept-req-selected-list select {
  padding: 4px 6px;
  color: var(--md-text, var(--spd-text-primary));
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
  font: inherit;
  font-size: 13px;
}

.selected-code {
  color: var(--md-primary, var(--primary));
  font-weight: 600;
  font-size: 13px;
}

.selected-name {
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: 13px;
}

.selected-spec {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-total {
  color: var(--md-text, var(--spd-text-primary));
  font-size: 13px;
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
  height: 30px;
  border: 0;
  background: var(--md-bg-subtle, #f6f8fb);
  cursor: pointer;
}

.qty-stepper button:hover {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 10%, #ffffff);
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
  font-size: 13px;
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
  background: rgba(15, 23, 42, 0.36);
}

.dept-req-modal {
  width: 100%;
  max-width: min(1180px, 94vw);
  max-height: 82vh;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #dbe5ec;
  border-radius: 10px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.22);
}

.dept-req-modal > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid #e6edf3;
}

.dept-req-modal > header div {
  display: grid;
  gap: 4px;
}

.dept-req-modal > header span {
  color: #64748b;
  font-size: 13px;
}

.dept-req-modal > header strong {
  color: #102033;
  font-size: 18px;
}

.dept-req-modal .dept-req-table-wrap {
  max-height: calc(82vh - 70px);
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.requisition-detail-table {
  min-width: 1120px;
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
  background: #fff;
}

.dept-req-smart-dialog :deep(.el-dialog__header) {
  padding: 14px 22px;
  border-bottom: 1px solid #eef0f4;
  flex: 0 0 auto;
}

.dept-req-smart-dialog :deep(.el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.dept-req-smart-dialog :deep(.el-dialog__footer) {
  padding: 12px 22px;
  border-top: 1px solid #eef0f4;
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
  font-size: 13px;
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
  background: #f8fafc;
  border-bottom: 1px solid #eef0f4;
  color: #475569;
  font-size: 12px;
}

.smart-toolbar label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.smart-toolbar select {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid #e5e7eb;
  border-radius: 5px;
  font: inherit;
  font-size: 12px;
  background: #fff;
  color: #1f2937;
}

.smart-toolbar span {
  color: #64748b;
}

.smart-table-wrap {
  flex: 1 1 auto;
  min-height: 420px;
  overflow: auto;
  margin: 0;
  border: none;
  border-radius: 0;
  background: #fff;
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
  color: #b45309;
  font-weight: 600;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1100px) {
  .dept-req-filters {
    grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  }
}

@media (max-width: 760px) {
  .dept-req-page {
    padding: 12px 12px 20px;
  }

  .dept-req-filters,
  .dept-req-selected {
    padding: 14px;
  }

  .dept-req-filters {
    grid-template-columns: 1fr;
  }

  .dept-req-filter-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .dept-req-filter-actions .btn {
    width: 100%;
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
  border-bottom: 1px solid #eef0f4;
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
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
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
  font-size: 12px;
  color: #1e3a8a;
  background: #eef4ff;
  border-radius: 14px;
}

.dept-req-selected-count strong {
  color: #1d4ed8;
  font-weight: 700;
}

.dept-req-dialog-submit .btn {
  height: 30px;
  padding: 0 12px;
  font-size: 12px;
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
  background: #fff;
  border-bottom: 1px solid #eef0f4;
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
  font-size: 11px;
  color: #6b7280;
  font-weight: 500;
  letter-spacing: 0.3px;
}

.dept-req-filterbar-item > input,
.dept-req-filterbar-item > select {
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
  color: #1f2937;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 5px;
  transition: border-color 0.15s, background 0.15s;
  min-width: 0;
}

.dept-req-filterbar-item > input:focus,
.dept-req-filterbar-item > select:focus {
  border-color: #3b82f6;
  background: #fff;
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
  font-size: 12px;
  border-radius: 5px;
}

/* 摘要条：单行小字，浅色背景 */
.dept-req-summary {
  flex: 0 0 auto;
  padding: 8px 22px;
  background: #f8fafc;
  font-size: 12px;
  color: #475569;
  display: flex;
  align-items: center;
  gap: 6px;
  border-bottom: 1px solid #eef0f4;
}

.dept-req-summary-ok {
  color: #047857;
  background: #ecfdf5;
  padding: 4px 10px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dept-req-summary-hint {
  color: #475569;
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
  background: #eef4ff;
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
  color: #1e3a8a;
  font-size: 13px;
}

.dept-req-selected-head span {
  color: #64748b;
  font-size: 11px;
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
  background: #fff;
  border-radius: 6px;
  font-size: 12px;
}

.dept-req-selected-list .selected-code {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  color: #1e3a8a;
  font-size: 11px;
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 3px;
}

.dept-req-selected-list .selected-name {
  color: #1f2937;
  font-weight: 600;
  font-size: 12px;
  min-width: 140px;
}

.dept-req-selected-list .selected-spec {
  color: #64748b;
  font-size: 11px;
  flex: 1 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dept-req-selected-list label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #6b7280;
}

.dept-req-selected-list label select {
  height: 26px;
  font-size: 11px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #fff;
  padding: 0 6px;
}

.dept-req-selected-list .qty-stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  overflow: hidden;
}

.dept-req-selected-list .qty-stepper .btn-text {
  width: 22px;
  height: 22px;
  background: transparent;
  border: none;
  color: #475569;
}

.dept-req-selected-list .qty-stepper input {
  width: 56px;
  height: 22px;
  border: none;
  border-left: 1px solid #e5e7eb;
  border-right: 1px solid #e5e7eb;
  text-align: center;
  font-size: 11px;
}

.dept-req-selected-list .selected-total {
  color: #475569;
  font-size: 11px;
  min-width: 80px;
}

.dept-req-selected-list .btn-text-danger {
  background: transparent;
  border: none;
  color: #dc2626;
  font-size: 11px;
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
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.dept-req-catalog-head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #eef0f4;
}

.dept-req-catalog-head h3 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.dept-req-catalog-head h3 svg {
  color: #2563eb;
}

.dept-req-catalog-meta {
  font-size: 11px;
  color: #94a3b8;
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
  font-size: 12px;
}

.dept-req-catalog-body thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #fff;
  font-weight: 600;
  color: #334155;
  font-size: 12px;
  letter-spacing: 0.2px;
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
}

.dept-req-catalog-body tbody td {
  padding: 7px 10px;
  font-size: 12px;
  color: #1f2937;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
}

.dept-req-catalog-body tbody tr:hover {
  background: #f8fafc;
}

.dept-req-catalog-body tbody tr.selected {
  background: #eff6ff;
}

.dept-req-catalog-body tbody tr.low {
  background: #fef2f2;
}
</style>

