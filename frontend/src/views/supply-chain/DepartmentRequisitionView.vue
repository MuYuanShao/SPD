<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { AlertTriangle, ArrowLeft, Eye, Minus, Plus, RotateCcw, Search, ShoppingCart, UserPlus, X } from '@lucide/vue'
import { ElDialog } from 'element-plus'
import 'element-plus/theme-chalk/el-dialog.css'
import PaginationControls from '../../components/common/PaginationControls.vue'
import PageHeader from '../../components/common/PageHeader.vue'
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
    error.value = '请先选择科室'
    return
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
    totalItems.value = result.total
    products.value = result.rows.map((row) => {
      const allowedModes = row.allowedModes?.length
        ? row.allowedModes
        : row.defaultMode === 'high_value'
          ? ['high_value' as RequisitionMode]
          : row.defaultMode === 'quota_package'
            ? ['loose' as RequisitionMode, 'quota_package' as RequisitionMode]
            : ['loose' as RequisitionMode]
      return {
      ...row, allowedModes,
      selected: false,
      quantity: 1,
      mode: allowedModes.includes(row.defaultMode) ? row.defaultMode : allowedModes[0]
    }})
  } catch (err) {
    error.value = err instanceof Error ? err.message : '科室申领目录加载失败'
  } finally {
    loading.value = false
  }
}

async function startRequisition() {
  message.value = ''
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
  requisitionStarted.value = false
  products.value = []
  totalItems.value = 0
  currentPage.value = 1
  error.value = ''
  message.value = ''
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
  item.quantity = Number(item.quantity || 1) + delta
  normalizeQuantity(item)
}

function normalizeQuantity(item: RequisitionCatalogItem) {
  const quantity = Number(item.quantity)
  item.quantity = Number.isFinite(quantity) && quantity > 0 ? quantity : 1
  if (item.mode !== 'loose') item.quantity = Math.max(1, Math.floor(item.quantity))
}

async function submitSelectedRequisitions() {
  if (!filters.targetDeptCode) {
    message.value = '请先选择科室'
    return
  }
  if (selectedItems.value.length === 0) {
    message.value = '请先勾选需要申领的商品'
    return
  }
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    const items: Array<Record<string, unknown>> = []
    for (const item of selectedItems.value) {
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
      sourceWarehouseId: selectedItems.value[0]?.sourceWarehouseId,
      items
    })
    submittedCount.value += selectedItems.value.length
    message.value = `已提交 ${selectedItems.value.length} 条科室申领明细${result.requisitionNo ? `，申请单号：${result.requisitionNo}` : ''}`
    products.value.forEach((item) => {
      item.selected = false
    })
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
    message.value = '请先选择科室'
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
    message.value = `智能补货已生成 ${result.createdCount} 张申领单：${result.requisitionNos.join('、')}`
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
    <section class="dept-req-workspace">
      <PageHeader eyebrow="供应链协同" title="科室申领" description="统一申请散货、定数包和高值耗材">
        <template #actions>
        <div class="dept-req-actions">
          <button v-if="!requisitionStarted && canCreateRequisition" class="btn btn-primary" type="button" @click="startRequisition">
            <UserPlus :size="16" />
            新增申领
          </button>
          <button v-else class="btn" type="button" :disabled="loading || submitting" @click="leaveRequisition">
            <ArrowLeft :size="16" />
            返回
          </button>
          <button v-if="requisitionStarted && canSmartAnalyze" class="btn" type="button" :disabled="loading || submitting" @click="openSmartReplenishment">
            <AlertTriangle :size="16" />
            智能补货
          </button>
        </div>
        </template>
      </PageHeader>

      <StatusMessage :message="error" tone="error" />
      <StatusMessage :message="message" tone="success" />

      <section v-if="!requisitionStarted" class="dept-req-table-wrap">
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

      <section v-if="requisitionStarted" class="dept-req-filters">
        <input v-model.trim="filters.productCode" placeholder="商品编码" />
        <input v-model.trim="filters.productName" placeholder="商品名称 / ID / HRP" />
        <input v-model.trim="filters.specModel" placeholder="规格型号" />
        <input v-model.trim="filters.manufacturerName" placeholder="生产厂家" />
        <select v-model="filters.targetDeptCode">
          <option value="">请选择科室</option>
          <option v-for="dept in options.departments" :key="dept.deptCode" :value="dept.deptCode">
            {{ dept.deptName }}
          </option>
        </select>
        <select v-model="filters.warehouseName">
          <option value="">全部关联库房</option>
          <option v-for="warehouse in linkedWarehouses" :key="warehouse.code" :value="warehouse.name">
            {{ warehouse.name }}
          </option>
        </select>
        <select v-model="filters.mode">
          <option value="">申领形式：全部</option>
          <option value="quota_package">定数包优先</option>
          <option value="high_value">高值耗材</option>
          <option value="loose">散货申领</option>
        </select>
        <button class="btn btn-primary" type="button" :disabled="loading" @click="searchCatalog">
          <Search :size="16" />
          查询
        </button>
        <button class="btn" type="button" :disabled="loading || submitting" @click="resetFilters">
          <RotateCcw :size="16" />
          重置
        </button>
        <button class="btn" type="button" :disabled="submitting || selectedItems.length === 0" @click="submitSelectedRequisitions">
          <ShoppingCart :size="16" />
          提交申领{{ selectedItems.length ? `(${selectedItems.length})` : '' }}
        </button>
      </section>

      <p v-if="submittedCount" class="inline-message">本次已成功提交 {{ submittedCount }} 条申领记录</p>

      <section v-if="requisitionStarted && selectedItems.length" class="dept-req-selected">
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
              <select v-model="item.mode" @change="normalizeQuantity(item)">
                <option v-if="item.allowedModes.includes('loose')" value="loose">散货</option>
                <option v-if="item.allowedModes.includes('quota_package')" value="quota_package">定数包</option>
                <option v-if="item.allowedModes.includes('high_value')" value="high_value">高值耗材</option>
              </select>
            </label>
            <div class="qty-stepper">
              <button class="btn-text" type="button" @click="stepQuantity(item, -1)"><Minus :size="12" /></button>
              <input v-model.number="item.quantity" type="number" :min="item.mode === 'loose' ? 0.0001 : 1" :step="item.mode === 'loose' ? 0.0001 : 1" aria-label="申领数量" @change="normalizeQuantity(item)" />
              <button class="btn-text" type="button" @click="stepQuantity(item, 1)"><Plus :size="12" /></button>
            </div>
            <span class="selected-total">{{ baseQtyByMode(item) }} {{ item.baseUnit }}</span>
            <button class="btn-text btn-text-danger" type="button" @click="removeFromSelection(item)">
              <X :size="14" />
              移除
            </button>
          </li>
        </ul>
      </section>

      <section v-if="requisitionStarted" class="dept-req-tags">
        <span class="yellow">目录来自当前科室有效库房绑定</span>
        <span class="yellow">同一申领单支持高值耗材、定数包和散货</span>
        <span class="orange">定数包缺货时可转散货申领</span>
        <span class="blue">高值耗材在拣配时选择唯一码</span>
        <span class="blue">当前库房：{{ selectedWarehouseLabel }}</span>
      </section>

      <section v-if="requisitionStarted" class="dept-req-table-wrap">
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
                <td><input v-model="item.selected" type="checkbox" /></td>
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
                  <select v-model="item.mode" @change="normalizeQuantity(item)">
                    <option v-if="item.allowedModes.includes('loose')" value="loose">散货</option>
                    <option v-if="item.allowedModes.includes('quota_package')" value="quota_package">定数包</option>
                    <option v-if="item.allowedModes.includes('high_value')" value="high_value">高值耗材</option>
                  </select>
                </td>
                <td>
                  <div class="qty-stepper">
                    <button class="btn-text" type="button" @click="stepQuantity(item, -1)"><Minus :size="12" /></button>
                    <input v-model.number="item.quantity" type="number" :min="item.mode === 'loose' ? 0.0001 : 1" :step="item.mode === 'loose' ? 0.0001 : 1" aria-label="申领数量" @change="normalizeQuantity(item)" />
                    <button class="btn-text" type="button" @click="stepQuantity(item, 1)"><Plus :size="12" /></button>
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
      </section>
      <PaginationControls
        v-if="requisitionStarted"
        :page="currentPage"
        :size="pageSize"
        :total="totalItems"
        :loading="loading"
        @change-page="changePage"
        @change-size="changePageSize"
      />
    </section>

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

    <ElDialog v-model="smartOpen" title="智能补货分析" width="min(1120px, 94vw)" :close-on-click-modal="false">
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
        <button class="btn" type="button" :disabled="smartGenerating" @click="smartOpen = false">取消</button>
        <button class="btn btn-primary" type="button" :disabled="smartLoading || smartGenerating" @click="generateSmartRequisitions">
          {{ smartGenerating ? '正在生成...' : '确认并生成申领单' }}
        </button>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped>
.dept-req-selected {
  border: 1px solid #dbe5ec;
  border-radius: 8px;
  margin-bottom: 14px;
  overflow: hidden;
}
.dept-req-selected-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: #f5f9fb;
  border-bottom: 1px solid #e5eef2;
}
.dept-req-selected-head strong {
  color: #123047;
  font-size: 14px;
}
.dept-req-selected-head span {
  color: #6b7c8f;
  font-size: 12px;
}
.dept-req-selected-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 260px;
  overflow: auto;
}
.dept-req-selected-list li {
  display: grid;
  grid-template-columns: 130px minmax(160px, 1fr) minmax(120px, 0.8fr) 170px 130px 100px auto;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-bottom: 1px solid #eef3f5;
}
.dept-req-selected-list label {
  display: flex;
  align-items: center;
  gap: 6px;
}
.dept-req-selected-list label span {
  color: #6b7c8f;
  font-size: 12px;
  white-space: nowrap;
}
.dept-req-selected-list select {
  padding: 4px 6px;
  border: 1px solid #cbd5e1;
  border-radius: 5px;
  font: inherit;
  font-size: 13px;
}
.selected-code {
  color: #0f6f78;
  font-weight: 700;
  font-size: 13px;
}
.selected-name {
  color: #172b3a;
  font-size: 13px;
}
.selected-spec {
  color: #6b7c8f;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-total {
  color: #33485c;
  font-size: 13px;
  white-space: nowrap;
}
.dept-req-toolbar {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.dept-req-toolbar > div:first-child {
  display: flex;
  gap: 8px;
}

.dept-req-actions {
  display: flex;
  gap: 10px;
}

.dept-req-start {
  align-items: center;
  border-top: 1px solid #e6edf3;
  color: #52677a;
  display: flex;
  gap: 12px;
  padding: 18px 16px;
}

.dept-req-start strong {
  color: #123047;
}

.dept-req-tags .blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.dept-req-modal-backdrop {
  align-items: center;
  background: rgba(15, 23, 42, 0.36);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 28px;
  position: fixed;
  z-index: 80;
}

.dept-req-modal {
  background: #ffffff;
  border: 1px solid #dbe5ec;
  border-radius: 8px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.22);
  max-height: 82vh;
  max-width: min(1180px, 94vw);
  overflow: hidden;
  width: 100%;
}

.dept-req-modal > header {
  align-items: center;
  border-bottom: 1px solid #e6edf3;
  display: flex;
  justify-content: space-between;
  padding: 16px 18px;
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

.requisition-detail-table {
  min-width: 1120px;
}

.smart-toolbar {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  color: #52677a;
}

.smart-toolbar label {
  align-items: center;
  display: flex;
  gap: 8px;
}

.smart-table-wrap {
  max-height: 56vh;
  overflow: auto;
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
}

.smart-table .shortage {
  color: #b45309;
  font-weight: 700;
}
</style>
