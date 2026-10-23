<script setup lang="ts">
import { computed, reactive, ref, watch, Teleport } from 'vue'
import { fetchPurchaseSelectableProducts, type PurchaseProductOption } from '../../api/purchaseOrders'
import {
  Ban,
  Calendar,
  CheckCircle2,
  Copy,
  Eye,
  FileText,
  FileCheck2,
  List,
  MessageSquare,
  PackagePlus,
  Paperclip,
  Plus,
  RefreshCw,
  Save,
  Search,
  Send,
  Trash2,
  Truck,
  Upload,
  X
} from '@lucide/vue'
import { useNotify } from '../../composables/useNotify'
import { usePurchaseManagement } from '../../composables/usePurchaseManagement'
import { useAuthStore } from '../../stores/auth'
import EmptyState from '../../components/common/EmptyState.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import { formatBusinessText, formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'

const {
  activeTab,
  rows,
  plans,
  smartRows,
  smartPeriods,
  smartSelectedPeriod,
  smartAnalysisNo,
  smartTotalFormulaQty,
  smartTotalRecommendedQty,
  showSmartAnalysisDialog,
  smartAnalysisLoading,
  smartAnalysisError,
  products,
  suppliers,
  departments,
  loading,
  message,
  purchasePagination,
  showCreateModal,
  showDemandModal,
  demandSubmitting,
  orderSubmitting,
  closeTarget,
  closeReason,
  detail,
  selectedOrder,
  showOrderDetailDialog,
  orderOperationMode,
  orderOperationText,
  showOrderAttachmentDialog,
  orderAttachments,
  orderAttachmentsLoading,
  orderAttachmentPreviewUrl,
  orderAttachmentPreviewType,
  orderAttachmentPreviewName,
  query,
  form,
  productSearchQuery,
  demandProductSearchQuery,
  demandForm,
  planForm,
  selectedDemandNos,
  demandGroups,
  showDemandDetailDialog,
  selectedDemandGroup,
  tabs,
  statusOptions,
  actionQueryLabel,
  resetQuery,
  filteredProducts,
  demandValidItemCount,
  demandEstimatedAmount,
  productByCode,
  openDemandDetail,
  statusLabel,
  statusTone,
  remainingQty,
  canCloseOrder,
  fillProduct,
  addItem,
  removeItem,
  resetOrderForm,
  resetDemandForm,
  loadData,
  selectTab,
  runSmartAnalysis,
  updateSmartRecommendedQty,
  createDemandFromSmartAnalysis,
  changePurchasePage,
  changePurchasePageSize,
  submitCreate,
  submitDemand,
  removeDemandItem,
  generatePlans,
  runOrderAction,
  requestClose,
  submitClose,
  runDemandAction,
  runPlanAction,
  openDetail,
  openTracking,
  selectOrder,
  runSelectedOrderAction,
  openOrderOperation,
  submitOrderOperation,
  copySelectedOrder,
  openOrderAttachments,
  uploadSelectedOrderAttachment,
  previewOrderAttachment,
  closeOrderAttachmentDialog
} = usePurchaseManagement()
const authStore = useAuthStore()
const notify = useNotify()

const showProductPicker = ref(false)
const pickerScope = ref<'hospital' | 'department'>('hospital')
const pickerDeptCode = ref('')
const pickerKeyword = ref('')
const pickerRows = ref<PurchaseProductOption[]>([])
const pickerSelected = reactive(new Map<string, PurchaseProductOption>())
const pickerLoading = ref(false)
const pickerError = ref('')
const entryMessage = ref('')
const pickerPagination = reactive({ page: 1, size: 20, total: 0 })
let pickerRequestId = 0
const visibleDemandItems = computed(() => demandForm.items
  .map((item, index) => ({ item, index }))
  .filter(({ item }) => {
    const product = productByCode(item.productCode)
    const keyword = demandProductSearchQuery.value.trim().toLowerCase()
    return !keyword || [product?.productCode, product?.productName, product?.specModel]
      .some(value => value?.toLowerCase().includes(keyword))
  }))

watch(showDemandModal, () => {
  showProductPicker.value = false
  entryMessage.value = ''
})
watch(() => demandForm.deptCode, () => {
  entryMessage.value = ''
  if (pickerScope.value === 'department') showProductPicker.value = false
})
watch(showProductPicker, value => {
  if (!value) {
    pickerRequestId++
    pickerLoading.value = false
    pickerSelected.clear()
  }
})

async function loadPickerProducts(page = 1) {
  const requestId = ++pickerRequestId
  pickerLoading.value = true
  pickerError.value = ''
  pickerRows.value = []
  pickerPagination.page = page
  try {
    const result = await fetchPurchaseSelectableProducts({
      scope: pickerScope.value,
      deptCode: pickerScope.value === 'department' ? pickerDeptCode.value : '',
      keyword: pickerKeyword.value,
      page: String(page), size: String(pickerPagination.size)
    })
    if (requestId !== pickerRequestId) return
    pickerRows.value = result.rows
    pickerPagination.total = result.total
  } catch (error) {
    if (requestId !== pickerRequestId) return
    pickerError.value = error instanceof Error ? error.message : '商品目录加载失败'
    notify.error(pickerError.value)
    pickerPagination.total = 0
  } finally {
    if (requestId === pickerRequestId) pickerLoading.value = false
  }
}

function openProductPicker(scope: 'hospital' | 'department') {
  if (scope === 'department' && !demandForm.deptCode) {
    notify.warning('目前没有选择发起科室，请先选择发起科室')
    return
  }
  entryMessage.value = ''
  pickerRequestId++
  pickerScope.value = scope
  pickerDeptCode.value = scope === 'department' ? demandForm.deptCode : ''
  pickerKeyword.value = ''
  pickerSelected.clear()
  pickerPagination.total = 0
  showProductPicker.value = true
  void loadPickerProducts()
}

function togglePickerProduct(product: PurchaseProductOption, checked: boolean) {
  if (checked) pickerSelected.set(product.productCode, product)
  else pickerSelected.delete(product.productCode)
}

function addSelectedProducts() {
  if (pickerLoading.value || pickerError.value) return
  if (pickerScope.value === 'department' && pickerDeptCode.value !== demandForm.deptCode) return
  for (const product of pickerSelected.values()) {
    if (!productByCode(product.productCode)) products.value.push(product)
    if (demandForm.items.some(item => item.productCode === product.productCode)) continue
    const blank = demandForm.items.find(item => !item.productCode)
    if (blank) Object.assign(blank, { productCode: product.productCode, quantity: 1 })
    else demandForm.items.push({ productCode: product.productCode, quantity: 1 })
  }
  showProductPicker.value = false
}

const orderAttachmentInput = ref<HTMLInputElement | null>(null)

function chooseOrderAttachment() {
  if (!selectedOrder.value) {
    notify.warning('请先选择一条采购订单')
    return
  }
  orderAttachmentInput.value?.click()
}

async function handleOrderAttachmentUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  await uploadSelectedOrderAttachment(file)
  input.value = ''
}
</script>

<template>
  <section class="md-page purchase-page">
    <MdPageHeader
      crumb="供应链业务"
      title="采购管理"
      description="需求池、采购计划、采购订单、订单跟踪按子流程拆分；验收入库按最新医院目录采购价生成系统批次单价。"
      :loading="loading"
      @refresh="loadData"
    />

    <section class="purchase-workspace">
      <div class="subnav-tabs purchase-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          :class="{ active: activeTab === tab.key }"
          @click="selectTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="activeTab !== 'plans'" class="hospital-action-row purchase-toolbar" :class="`action-row--${activeTab}`">
        <label v-if="activeTab === 'smart'" class="inline-field">
          <span>分析周期</span>
          <select v-model.number="smartSelectedPeriod" class="inline-select md-select">
            <option v-for="period in smartPeriods" :key="period" :value="period">近{{ period }}天</option>
          </select>
        </label>
        <button v-if="activeTab === 'smart'" class="btn btn-primary md-btn md-btn-primary" type="button" @click="runSmartAnalysis">
          <Search :size="17" />
          智能补货分析
        </button>
        <button v-if="activeTab === 'demands' && authStore.hasPermission('purchase-demand:create')" class="btn btn-primary md-btn md-btn-primary" type="button" @click="resetDemandForm(); showDemandModal = true">
          <PackagePlus :size="18" />
          新增采购
        </button>
        <select v-if="activeTab === 'demands'" v-model="planForm.supplierName" class="inline-select md-select" title="计划供应商">
          <option value="">按商品默认供应商生成计划</option>
          <option v-for="supplier in suppliers" :key="supplier.supplierName" :value="supplier.supplierName">
            {{ supplier.supplierName }}
          </option>
        </select>
        <button v-if="activeTab === 'demands' && authStore.hasPermission('purchase-demand:convert-plan')" class="btn md-btn" type="button" :disabled="selectedDemandNos.length === 0" :title="selectedDemandNos.length === 0 ? '请先勾选已审核需求' : ''" @click="generatePlans">
          <FileCheck2 :size="17" />
          需求转计划
        </button>
        <button v-if="activeTab === 'orders' && authStore.hasPermission('purchase-order:create')" class="btn btn-primary md-btn md-btn-primary" type="button" @click="resetOrderForm(); showCreateModal = true">
          <PackagePlus :size="18" />
          新增订单
        </button>
        <template v-if="activeTab === 'orders'">
          <button v-if="authStore.hasPermission('purchase-order:submit')" class="btn md-btn" type="button" :disabled="selectedOrder?.orderStatus !== 'draft'" title="仅草稿订单可提交" @click="runSelectedOrderAction('submit')">
            <Send :size="16" /> 提交
          </button>
          <button v-if="authStore.hasPermission('purchase-order:approve')" class="btn md-btn" type="button" :disabled="selectedOrder?.orderStatus !== 'pending_approval'" title="仅待审批订单可审批" @click="runSelectedOrderAction('approve')">
            <CheckCircle2 :size="16" /> 审批
          </button>
          <button
            v-if="authStore.hasPermission('purchase-order:void')"
            class="btn btn-danger-soft md-btn"
            type="button"
            :disabled="!selectedOrder || !['draft', 'pending_approval', 'approved'].includes(selectedOrder.orderStatus)"
            @click="openOrderOperation('void')"
          >
            <Ban :size="16" /> 作废
          </button>
          <button v-if="authStore.hasPermission('purchase-order:create')" class="btn md-btn" type="button" :disabled="!selectedOrder" @click="copySelectedOrder">
            <Copy :size="16" /> 复制订单
          </button>
          <button v-if="authStore.hasPermission('purchase-order:attachment')" class="btn md-btn" type="button" :disabled="!selectedOrder" @click="chooseOrderAttachment">
            <Upload :size="16" /> 上传附件
          </button>
          <input ref="orderAttachmentInput" type="file" hidden @change="handleOrderAttachmentUpload" />
          <button class="btn md-btn" type="button" :disabled="!selectedOrder" @click="openOrderAttachments">
            <Paperclip :size="16" /> 附件阅览
          </button>
          <button v-if="authStore.hasPermission('purchase-order:remark')" class="btn md-btn" type="button" :disabled="!selectedOrder" @click="openOrderOperation('remark')">
            <MessageSquare :size="16" /> 添加备注
          </button>
        </template>
        <button v-if="activeTab === 'tracking' && detail" class="btn md-btn" type="button" @click="detail = null">
          <X :size="17" />
          清空跟踪
        </button>
      </div>

      <div v-if="activeTab !== 'smart'" class="hospital-query-grid purchase-query-grid">
        <label v-if="activeTab === 'orders'"><span>订单编号</span><input v-model="query.orderNo" class="md-input" placeholder="CG2026..." /></label>
        <label v-if="activeTab === 'demands'"><span>需求编号</span><input v-model="query.demandNo" class="md-input" placeholder="XQ2026..." /></label>
        <label v-if="activeTab === 'demands'"><span>科室</span><select v-model="query.deptCode" class="md-select"><option value="">全部授权科室</option><option v-for="dept in departments" :key="dept.deptCode" :value="dept.deptCode">{{ dept.deptName }}</option></select></label>
        <label v-if="activeTab === 'plans'"><span>计划编号</span><input v-model="query.planNo" class="md-input" placeholder="JH2026..." /></label>
        <label v-if="activeTab === 'plans' || activeTab === 'orders'"><span>供应商</span><input v-model="query.supplierName" class="md-input" placeholder="模糊查询供应商" /></label>
        <label v-if="activeTab !== 'tracking'"><span>商品</span><input v-model="query.keyword" class="md-input" placeholder="商品编码/名称/规格" /></label>
        <label>
          <span>状态</span>
          <select v-model="query.status" class="md-select">
            <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
        <div class="purchase-query-actions">
          <button class="btn md-btn md-btn-primary" type="button" @click="loadData">
            <Search :size="16" />
            {{ actionQueryLabel }}
          </button>
          <button class="btn md-btn" type="button" @click="resetQuery">
            <X :size="16" />
            重置
          </button>
        </div>
      </div>

      <div v-if="activeTab === 'demands'" class="table-scroll purchase-list-scroll">
        <table class="master-table purchase-table purchase-demand-table">
          <thead>
            <tr>
              <th class="order-select-column">选择</th>
              <th>需求编号</th>
              <th>来源</th>
              <th>科室</th>
              <th>商品明细</th>
              <th>需求总数</th>
              <th>建议采购量</th>
              <th>紧急程度</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="10" class="approval-empty">正在加载采购需求...</td>
            </tr>
            <tr v-for="group in demandGroups" v-else :key="group.demandNo" class="demand-group-row">
              <td><input v-model="selectedDemandNos" type="checkbox" :value="group.demandNo" :disabled="group.demandStatus !== 'approved'" :aria-label="`选择需求 ${group.demandNo}`" /></td>
              <td>
                <strong>{{ group.demandNo }}</strong>
                <span class="item-count-badge">{{ group.itemCount }}项</span>
              </td>
              <td>{{ formatBusinessText(group.demandSource) }}</td>
              <td>{{ group.deptName || '-' }}</td>
              <td class="demand-group-items-cell">
                <span class="item-summary">共 {{ group.itemCount }} 种商品</span>
                <button class="btn-link" type="button" @click="openDemandDetail(group)">查看明细</button>
              </td>
              <td>{{ group.totalQuantity }}</td>
              <td>{{ group.totalSuggestedQty }}</td>
              <td>{{ formatBusinessText(group.urgentLevel) }}</td>
              <td><span :class="['status-badge', statusTone(group.demandStatus)]">{{ statusLabel(group.demandStatus) }}</span></td>
              <td class="row-actions">
                <button v-if="group.demandStatus === 'draft' && authStore.hasPermission('purchase-demand:submit')" class="btn-text" @click="runDemandAction(group, 'submit')">提交</button>
                <button v-if="group.demandStatus === 'pending_review' && authStore.hasPermission('purchase-demand:review')" class="btn-text" @click="runDemandAction(group, 'approve')">审核通过</button>
                <button v-if="group.demandStatus === 'pending_review' && authStore.hasPermission('purchase-demand:reject')" class="btn-text btn-text-danger" @click="runDemandAction(group, 'reject')">驳回</button>
              </td>
            </tr>
            <tr v-if="!loading && demandGroups.length === 0">
              <td colspan="10" class="approval-empty"><EmptyState message="暂无采购需求，请调整筛选条件或新建采购需求" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        v-if="activeTab === 'demands'"
        :page="purchasePagination.demands.page"
        :size="purchasePagination.demands.size"
        :total="purchasePagination.demands.total"
        :loading="loading"
        @change-page="changePurchasePage('demands', $event)"
        @change-size="changePurchasePageSize('demands', $event)"
      />

      <div v-if="activeTab === 'plans'" class="table-scroll purchase-list-scroll">
        <table class="master-table purchase-table purchase-plan-table">
          <thead>
            <tr>
              <th>计划编号</th>
              <th>商品</th>
              <th>采购数量</th>
              <th>金额</th>
              <th>供应商</th>
              <th>发起科室</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="approval-empty">正在加载采购计划...</td>
            </tr>
            <tr v-for="row in plans" v-else :key="row.planNo">
              <td class="document-no-cell">{{ row.planNo }}</td>
              <td :title="`${row.productCode || ''} ${row.specModel || ''} ${row.manufacturerName || ''}`"><strong>{{ row.productName || '-' }}</strong><small>{{ row.productCode || '-' }} · {{ row.specModel || '-' }}</small></td>
              <td>{{ row.plannedQuantity }}</td>
              <td class="purchase-plan-amount">¥ {{ Number(row.amount ?? Number(row.unitPrice || 0) * Number(row.plannedQuantity || 0)).toFixed(2) }}</td>
              <td>{{ row.supplierName || '-' }}</td>
              <td>{{ row.initiatingDeptName || '-' }}</td>
              <td><span :class="['status-badge', statusTone(row.planStatus)]">{{ statusLabel(row.planStatus) }}</span></td>
              <td class="row-actions">
                <button v-if="row.planStatus === 'draft' && authStore.hasPermission('purchase-plan:approve')" class="btn-text" @click="runPlanAction(row, 'approve')">计划审核</button>
                <button v-if="row.planStatus === 'approved' && authStore.hasPermission('purchase-plan:execute')" class="btn-text" @click="runPlanAction(row, 'execute')">转订单</button>
                <button v-if="row.planStatus === 'draft' && authStore.hasPermission('purchase-plan:reject')" class="btn-text btn-text-danger" @click="runPlanAction(row, 'reject')">驳回</button>
              </td>
            </tr>
            <tr v-if="!loading && plans.length === 0">
              <td colspan="8" class="approval-empty"><EmptyState message="暂无采购计划" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        v-if="activeTab === 'plans'"
        :page="purchasePagination.plans.page"
        :size="purchasePagination.plans.size"
        :total="purchasePagination.plans.total"
        :loading="loading"
        @change-page="changePurchasePage('plans', $event)"
        @change-size="changePurchasePageSize('plans', $event)"
      />

      <div v-if="activeTab === 'orders'" class="table-scroll purchase-list-scroll">
        <table class="master-table purchase-table">
          <thead>
            <tr>
              <th class="order-select-column">选择</th>
              <th>订单编号</th>
              <th>供应商</th>
              <th>来源</th>
              <th>明细数</th>
              <th>采购数量</th>
              <th>履约进度</th>
              <th>剩余数量</th>
              <th>总金额</th>
              <th>预计到货</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="12" class="approval-empty">正在加载采购订单...</td>
            </tr>
            <tr
              v-for="row in rows"
              v-else
              :key="row.orderNo"
              :class="{ 'selected-order-row': selectedOrder?.orderNo === row.orderNo }"
              @click="selectOrder(row)"
            >
              <td class="order-select-column">
                <input
                  type="radio"
                  name="purchase-order-selection"
                  :checked="selectedOrder?.orderNo === row.orderNo"
                  :aria-label="`选择订单 ${row.orderNo}`"
                  @change="selectOrder(row)"
                />
              </td>
              <td class="document-no-cell">{{ row.orderNo }}</td>
              <td>{{ row.supplierName }}</td>
              <td>{{ formatBusinessText(row.orderSource) }}</td>
              <td>{{ row.itemCount }}</td>
              <td>{{ row.orderQuantity }}</td>
              <td>
                <div class="fulfillment-cell">
                  <span>{{ row.receivedQuantity }} / {{ row.orderQuantity }}</span>
                  <i>
                    <b :style="{ width: `${Math.min(100, Math.round((Number(row.receivedQuantity || 0) / Math.max(1, Number(row.orderQuantity || 0))) * 100))}%` }" />
                  </i>
                </div>
              </td>
              <td>
                <span :class="['quantity-gap', remainingQty(row) > 0 ? 'pending' : 'done']">
                  {{ remainingQty(row) }}
                </span>
              </td>
              <td>¥ {{ Number(row.totalAmount).toFixed(2) }}</td>
              <td>{{ row.expectedArrivalDate || '-' }}</td>
              <td><span :class="['status-badge', statusTone(row.orderStatus)]">{{ statusLabel(row.orderStatus) }}</span></td>
              <td class="row-actions">
                <button class="btn-text" @click="openDetail(row)"><Eye :size="15" /> 查看</button>
                <button class="btn-text" @click="openTracking(row)"><List :size="15" /> 跟踪</button>
                <button v-if="row.orderStatus === 'approved' && authStore.hasPermission('purchase-order:send')" class="btn-text" @click="runOrderAction(row, 'send')">
                  <Send :size="15" /> 发送
                </button>
                <button
                  v-if="['approved', 'sent'].includes(row.orderStatus) && authStore.hasPermission('purchase-order:close')"
                  class="btn-text"
                  :class="{ 'btn-text-muted': !canCloseOrder(row) }"
                  :title="canCloseOrder(row) ? '填写关闭原因后关闭订单' : '需先完成收货验收'"
                  @click="requestClose(row)"
                >
                  {{ canCloseOrder(row) ? '关闭' : '待收货' }}
                </button>
              </td>
            </tr>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="12" class="approval-empty">暂无采购订单</td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        v-if="activeTab === 'orders'"
        :page="purchasePagination.orders.page"
        :size="purchasePagination.orders.size"
        :total="purchasePagination.orders.total"
        :loading="loading"
        @change-page="changePurchasePage('orders', $event)"
        @change-size="changePurchasePageSize('orders', $event)"
      />

      <div v-if="activeTab === 'tracking'" class="tracking-panel">
        <div v-if="!detail" class="approval-empty">请从采购订单点击“查看”进入订单跟踪。</div>
        <template v-else>
          <section class="purchase-detail-summary">
            <span>状态：{{ statusLabel(detail.order.orderStatus) }}</span>
            <span>金额：¥ {{ Number(detail.order.totalAmount).toFixed(2) }}</span>
            <span>收货：{{ detail.order.receivedQuantity || 0 }} / {{ detail.order.orderQuantity || 0 }}</span>
            <span>剩余：{{ remainingQty(detail.order) }}</span>
          </section>
          <div class="table-scroll">
            <table class="master-table purchase-table">
              <thead>
                <tr>
                  <th>商品</th>
                  <th>订单数量</th>
                  <th>订单价</th>
                  <th>最新目录价</th>
                  <th>价差</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail.items" :key="String(item.itemId)">
                  <td>{{ item.productName }}</td>
                  <td>{{ item.quantity }} {{ item.unit }}</td>
                  <td>¥ {{ Number(item.estimatedUnitPrice || 0).toFixed(2) }}</td>
                  <td>¥ {{ Number(item.latestCatalogPrice || 0).toFixed(2) }}</td>
                  <td>{{ Number(item.priceDiff || 0).toFixed(2) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="timeline-list">
            <article v-for="item in detail.tracking" :key="String(item.trackingId)">
              <strong>{{ formatBusinessText(item.eventType) }}</strong>
              <span>{{ formatStatusText(item.eventStatus) }} · {{ item.createTime }}</span>
              <small>{{ formatRemarkText(item.remark) }}</small>
            </article>
          </div>
        </template>
      </div>
    </section>

    <Teleport to="body">
    <div v-if="showSmartAnalysisDialog" class="modal-mask">
      <section class="edit-modal purchase-smart-modal">
        <header>
          <div>
            <p>{{ smartAnalysisNo || '正在生成分析' }}</p>
            <h3>智能补货分析</h3>
          </div>
          <button class="btn-icon" type="button" @click="showSmartAnalysisDialog = false"><X :size="18" /></button>
        </header>

        <div class="analysis-summary">
          <span>分析周期：近{{ smartSelectedPeriod }}天</span>
          <span>公式总量：{{ smartTotalFormulaQty }}</span>
          <span>建议总量：{{ smartTotalRecommendedQty }}</span>
          <span>结果：{{ smartRows.length }} 条</span>
        </div>
        <div class="table-scroll">
          <table class="master-table purchase-table smart-replenishment-table">
            <thead>
              <tr>
                <th>一级库</th>
                <th>供应商</th>
                <th>商品</th>
                <th>当前库存</th>
                <th>5天</th>
                <th>15天</th>
                <th>30天</th>
                <th>45天</th>
                <th>60天</th>
                <th>建议采购</th>
                <th>公式</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="smartAnalysisLoading">
                <td colspan="11" class="approval-empty"><EmptyState message="正在分析一级库实际拣配出库数据..." /></td>
              </tr>
              <tr v-else-if="smartRows.length === 0">
                <td colspan="11" class="approval-empty"><EmptyState message="暂无可补货建议" /></td>
              </tr>
              <tr v-for="row in smartRows" v-else :key="`${row.warehouseCode}-${row.productCode}`">
                <td>{{ row.warehouseName }}</td>
                <td>{{ row.supplierName || '-' }}</td>
                <td>
                  <strong>{{ row.productName }}</strong>
                  <span class="muted-cell">{{ row.productCode }} / {{ row.specModel || '-' }}</span>
                </td>
                <td>{{ row.currentQty }}</td>
                <td>{{ row.issue5 }}</td>
                <td>{{ row.issue15 }}</td>
                <td>{{ row.issue30 }}</td>
                <td>{{ row.issue45 }}</td>
                <td>{{ row.issue60 }}</td>
                <td>
                  <input
                    class="analysis-qty-input"
                    type="number"
                    min="0"
                    :value="row.recommendedQty"
                    @input="updateSmartRecommendedQty(row, $event)"
                  />
                </td>
                <td>{{ row.formulaText }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer>
          <button class="btn" type="button" @click="showSmartAnalysisDialog = false">关闭</button>
          <button class="btn btn-primary" type="button" :disabled="smartAnalysisLoading || smartRows.length === 0" @click="createDemandFromSmartAnalysis">
            <PackagePlus :size="18" />
            生成采购需求
          </button>
        </footer>
      </section>
    </div>
    </Teleport>

    <!-- 需求明细弹窗 -->
    <Teleport to="body">
    <div v-if="showDemandDetailDialog" class="modal-mask">
      <section class="edit-modal purchase-detail-modal">
        <header>
          <h3>需求明细 - {{ selectedDemandGroup?.demandNo }}</h3>
          <button class="btn-icon" type="button" @click="showDemandDetailDialog = false"><X :size="18" /></button>
        </header>
        <div class="detail-summary-bar">
          <span>来源: {{ selectedDemandGroup?.demandSource }}</span>
          <span>科室: {{ selectedDemandGroup?.deptName || '-' }}</span>
          <span>商品数: {{ selectedDemandGroup?.itemCount }}项</span>
          <span>总数量: {{ selectedDemandGroup?.totalQuantity }}</span>
        </div>
        <table class="master-table">
          <thead>
            <tr>
              <th>商品编码</th>
              <th>商品名称</th>
              <th>规格型号</th>
              <th>数量</th>
              <th>单价</th>
              <th>金额</th>
              <th>注册证号</th>
              <th>厂家</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in selectedDemandGroup?.items" :key="item.demandId">
              <td>{{ item.productCode }}</td>
              <td>{{ item.productName }}</td>
              <td>{{ item.specModel }}</td>
              <td>{{ item.quantity }}</td>
              <td>¥ {{ Number(item.unitPrice || 0).toFixed(2) }}</td>
              <td>¥ {{ (Number(item.unitPrice || 0) * Number(item.quantity)).toString() }}</td>
              <td>{{ item.registrationNo || '-' }}</td>
              <td>{{ item.manufacturerName || '-' }}</td>
            </tr>
          </tbody>
        </table>
        <footer>
          <button class="btn" type="button" @click="showDemandDetailDialog = false">关闭</button>
        </footer>
      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="showOrderDetailDialog && detail" class="modal-mask purchase-entry-mask" @click.self="showOrderDetailDialog = false">
      <section class="edit-modal order-detail-modal" role="dialog" aria-modal="true" aria-labelledby="order-detail-title">
        <header class="purchase-entry-header">
          <div>
            <p>采购订单明细</p>
            <h3 id="order-detail-title">{{ detail.order.orderNo }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭订单明细" @click="showOrderDetailDialog = false"><X :size="18" /></button>
        </header>
        <div class="order-detail-body">
          <section class="purchase-detail-summary order-detail-summary">
            <span>供应商：<strong>{{ detail.order.supplierName }}</strong></span>
            <span>状态：<strong>{{ statusLabel(detail.order.orderStatus) }}</strong></span>
            <span>订单金额：<strong>¥ {{ Number(detail.order.totalAmount || 0).toFixed(2) }}</strong></span>
            <span>预计到货：<strong>{{ detail.order.expectedArrivalDate || '-' }}</strong></span>
          </section>
          <div class="table-scroll order-detail-table-wrap">
            <table class="master-table purchase-table order-detail-table">
              <thead>
                <tr>
                  <th>供应商</th>
                  <th>商品编码</th>
                  <th>商品名称</th>
                  <th>规格型号</th>
                  <th>注册证号</th>
                  <th>厂家</th>
                  <th>单位</th>
                  <th>单价</th>
                  <th>数量</th>
                  <th>金额</th>
                  <th>中包装数量</th>
                  <th>采购包装数量</th>
                  <th>招采子编码</th>
                  <th>合同编码</th>
                  <th>UDI</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail.items" :key="item.itemId">
                  <td>{{ item.supplierName || detail.order.supplierName || '-' }}</td>
                  <td>{{ item.productCode || '-' }}</td>
                  <td>{{ item.productName || '-' }}</td>
                  <td>{{ item.specModel || '-' }}</td>
                  <td>{{ item.registrationNo || '-' }}</td>
                  <td>{{ item.manufacturerName || '-' }}</td>
                  <td>{{ item.unit || '-' }}</td>
                  <td>¥ {{ Number(item.estimatedUnitPrice || 0).toFixed(2) }}</td>
                  <td>{{ item.quantity }}</td>
                  <td class="purchase-plan-amount">¥ {{ Number(item.amount || 0).toFixed(2) }}</td>
                  <td>{{ item.middlePackageQuantity ?? '-' }}</td>
                  <td>{{ item.purchasePackageQuantity ?? '-' }}</td>
                  <td>{{ item.tenderSubCode || '-' }}</td>
                  <td>{{ item.contractCode || '-' }}</td>
                  <td>{{ item.udiCode || '-' }}</td>
                </tr>
                <tr v-if="detail.items.length === 0">
                  <td colspan="15" class="approval-empty">该订单暂无商品明细</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <footer class="purchase-entry-footer">
          <span>订单商品价格与包装信息取创建订单时的业务数据及当前商品目录字段。</span>
          <div>
            <button class="btn" type="button" @click="showOrderDetailDialog = false">关闭</button>
          </div>
        </footer>
      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="orderOperationMode && selectedOrder" class="modal-mask" @click.self="orderOperationMode = null">
      <section class="edit-modal order-operation-modal" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>{{ orderOperationMode === 'void' ? '订单状态处理' : '订单业务记录' }}</p>
            <h3>{{ orderOperationMode === 'void' ? '作废采购订单' : '添加订单备注' }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="orderOperationMode = null"><X :size="18" /></button>
        </header>
        <div class="order-operation-body">
          <div class="order-operation-order">
            <span>订单编号</span>
            <strong>{{ selectedOrder.orderNo }}</strong>
          </div>
          <label>
            <span>{{ orderOperationMode === 'void' ? '作废原因' : '备注内容' }}</span>
            <textarea
              v-model="orderOperationText"
              rows="5"
              :placeholder="orderOperationMode === 'void' ? '请填写作废原因，作废后不可继续流转' : '请输入需要记录在订单跟踪中的备注'"
            />
          </label>
        </div>
        <footer>
          <button class="btn" type="button" @click="orderOperationMode = null">取消</button>
          <button class="btn" :class="{ 'btn-danger-soft': orderOperationMode === 'void', 'btn-primary': orderOperationMode === 'remark' }" type="button" @click="submitOrderOperation">
            {{ orderOperationMode === 'void' ? '确认作废' : '保存备注' }}
          </button>
        </footer>
      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="showOrderAttachmentDialog && selectedOrder" class="modal-mask purchase-entry-mask" @click.self="closeOrderAttachmentDialog">
      <section class="edit-modal order-attachment-modal" role="dialog" aria-modal="true" aria-labelledby="order-attachment-title">
        <header class="purchase-entry-header">
          <div>
            <p>采购订单附件</p>
            <h3 id="order-attachment-title">{{ selectedOrder.orderNo }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭附件阅览" @click="closeOrderAttachmentDialog"><X :size="18" /></button>
        </header>
        <div class="order-attachment-body">
          <aside class="order-attachment-list">
            <button class="btn" type="button" @click="chooseOrderAttachment"><Upload :size="15" /> 上传附件</button>
            <p v-if="orderAttachmentsLoading" class="approval-empty">正在加载附件...</p>
            <p v-else-if="orderAttachments.length === 0" class="approval-empty">暂无订单附件</p>
            <ul v-else>
              <li v-for="attachment in orderAttachments" :key="attachment.id">
                <div>
                  <strong>{{ attachment.fileName }}</strong>
                  <small>{{ attachment.createTime }} · {{ (attachment.size / 1024).toFixed(1) }} KB</small>
                </div>
                <button class="btn-text" type="button" @click="previewOrderAttachment(attachment)"><Eye :size="14" /> 阅览</button>
              </li>
            </ul>
          </aside>
          <section class="order-attachment-preview">
            <template v-if="orderAttachmentPreviewUrl">
              <img v-if="orderAttachmentPreviewType.startsWith('image/')" :src="orderAttachmentPreviewUrl" :alt="orderAttachmentPreviewName" />
              <iframe v-else-if="orderAttachmentPreviewType.includes('pdf')" :src="orderAttachmentPreviewUrl" :title="orderAttachmentPreviewName" />
              <div v-else class="approval-empty">
                <FileText :size="32" />
                <span>该文件类型不支持在线预览：{{ orderAttachmentPreviewName }}</span>
              </div>
            </template>
            <div v-else class="approval-empty">
              <Paperclip :size="32" />
              <span>请从左侧选择附件阅览</span>
            </div>
          </section>
        </div>
      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="showDemandModal" class="modal-mask purchase-entry-mask" tabindex="-1" @click.self="showDemandModal = false" @keydown.esc="showDemandModal = false">
      <section class="edit-modal purchase-entry-modal" role="dialog" aria-modal="true" aria-labelledby="purchase-entry-title">
        <header class="purchase-entry-header">
          <div>
            <p>采购需求录入</p>
            <h3 id="purchase-entry-title">新增采购</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭新增采购" @click="showDemandModal = false"><X :size="18" /></button>
        </header>

        <div class="purchase-entry-body">
          <section class="form-section purchase-entry-section">
            <div class="purchase-entry-section-heading">
              <div>
                <h4>采购信息</h4>
                <small>填写采购申请基本信息，保存后进入采购需求池。</small>
              </div>
            </div>
            <div class="purchase-entry-meta-grid">
              <label>
                <span><em>*</em> 发起科室</span>
                <select v-model="demandForm.deptCode" @change="demandForm.deptName = departments.find((item) => item.deptCode === demandForm.deptCode)?.deptName || ''">
                  <option value="">请选择授权范围内科室</option>
                  <option v-for="dept in departments" :key="dept.deptCode" :value="dept.deptCode">{{ dept.deptName }}</option>
                </select>
              </label>
              <label>
                <span><em>*</em> 采购来源</span>
                <input v-model="demandForm.demandSource" placeholder="例如：临时采购" />
              </label>
              <label>
                <span>紧急程度</span>
                <select v-model="demandForm.urgentLevel">
                  <option value="normal">普通</option>
                  <option value="urgent">紧急</option>
                </select>
              </label>
              <label class="purchase-entry-remark">
                <span>备注</span>
                <input v-model="demandForm.remark" placeholder="可填写采购说明或补充要求" />
              </label>
            </div>
          </section>

          <section class="form-section purchase-entry-section purchase-entry-detail-section">
            <div class="purchase-entry-section-heading">
              <div>
                <h4>采购明细</h4>
                <small>从医院商品目录选择商品并填写采购数量。</small>
              </div>
              <div class="purchase-entry-summary">
                <span>已选 <strong>{{ demandValidItemCount }}</strong> 项</span>
                <span>预计金额 <strong>¥ {{ demandEstimatedAmount.toFixed(2) }}</strong></span>
              </div>
            </div>

            <div class="purchase-entry-toolbar">
              <button class="btn btn-primary" type="button" @click="openProductPicker('hospital')">
                <Plus :size="17" /> 新增
              </button>
              <button class="btn" type="button" @click="openProductPicker('department')">
                <List :size="17" /> 科室目录新增
              </button>
              <label class="purchase-entry-search">
                <Search :size="16" />
                <span>商品检索</span>
                <input v-model="demandProductSearchQuery" aria-label="商品检索" placeholder="商品编码、名称或规格" />
              </label>
            </div>

            <p v-if="entryMessage" class="inline-message" role="alert">{{ entryMessage }}</p>

            <div class="purchase-entry-table-wrap">
              <table class="master-table purchase-entry-table">
                <thead>
                  <tr>
                    <th>商品编码</th>
                    <th>商品名称</th>
                    <th>规格型号</th>
                    <th>单位</th>
                    <th>采购数量</th>
                    <th>参考单价</th>
                    <th>预计金额</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="{ item, index } in visibleDemandItems" :key="item.productCode || index">
                    <td class="purchase-code-cell">{{ productByCode(item.productCode)?.productCode || '-' }}</td>
                    <td class="purchase-product-cell">
                      {{ productByCode(item.productCode)?.productName || '请点击新增选择商品' }}
                    </td>
                    <td>{{ productByCode(item.productCode)?.specModel || '-' }}</td>
                    <td>{{ productByCode(item.productCode)?.unit || '-' }}</td>
                    <td class="purchase-quantity-cell">
                      <input v-integer-quantity v-model.number="item.quantity" type="number" min="1" step="1" :aria-label="`第 ${index + 1} 行采购数量`" />
                    </td>
                    <td>¥ {{ Number(productByCode(item.productCode)?.purchasePrice || 0).toFixed(2) }}</td>
                    <td class="purchase-amount-cell">
                      ¥ {{ (Number(item.quantity || 0) * Number(productByCode(item.productCode)?.purchasePrice || 0)).toString() }}
                    </td>
                    <td>
                      <button class="purchase-row-delete" type="button" @click="removeDemandItem(index)">
                        <Trash2 :size="15" />
                        删除
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>

        <footer class="purchase-entry-footer">
          <span>保存后可继续审核和生成采购计划；勾选商品后支持一键加入明细。</span>
          <div>
            <button class="btn" type="button" @click="showDemandModal = false">取消</button>
            <button class="btn" type="button" :disabled="demandSubmitting" @click="submitDemand(true)">
              <Plus :size="16" />
              {{ demandSubmitting ? '保存中…' : '保存并新建' }}
            </button>
            <button class="btn btn-primary" type="button" :disabled="demandSubmitting" @click="submitDemand(false)">
              <Save :size="17" />
              {{ demandSubmitting ? '保存中…' : '保存并关闭' }}
            </button>
          </div>
        </footer>
      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="showProductPicker && showDemandModal" class="modal-mask purchase-product-picker-mask" @click.self="showProductPicker = false" @keydown.esc.stop="showProductPicker = false">
      <section class="edit-modal wide" role="dialog" aria-modal="true" aria-labelledby="purchase-product-picker-title">
        <header>
          <h3 id="purchase-product-picker-title">{{ pickerScope === 'department' ? '科室目录新增' : '新增商品' }}</h3>
          <button class="btn-icon" aria-label="关闭商品选择" @click="showProductPicker = false"><X :size="18" /></button>
        </header>
        <p v-if="pickerScope === 'department'" class="purchase-picker-dept">发起科室：<strong>{{ demandForm.deptName }}</strong></p>
        <form class="purchase-entry-toolbar" @submit.prevent="loadPickerProducts()">
          <label class="purchase-entry-search"><span>商品检索</span><input v-model="pickerKeyword" aria-label="选择商品检索" placeholder="商品编码、名称或规格" /></label>
          <button class="btn btn-primary" :disabled="pickerLoading">查询</button>
        </form>
        <p v-if="pickerError" class="inline-message" role="alert">{{ pickerError }}</p>

        <div class="table-scroll">
          <table class="master-table">
            <thead><tr><th>选择</th><th>商品编码</th><th>商品名称</th><th>规格型号</th><th>单位</th><th>参考单价</th></tr></thead>
            <tbody>
              <tr v-if="pickerLoading"><td colspan="6">正在加载商品目录...</td></tr>
              <tr v-for="product in pickerRows" v-else :key="product.productCode" :class="{ 'is-picked': demandForm.items.some(item => item.productCode === product.productCode) }">
                <td><input type="checkbox" class="picker-checkbox" :aria-label="`选择商品 ${product.productCode}`" :checked="pickerSelected.has(product.productCode) || demandForm.items.some(item => item.productCode === product.productCode)" :disabled="demandForm.items.some(item => item.productCode === product.productCode)" :title="demandForm.items.some(item => item.productCode === product.productCode) ? '该商品已添加至本次需求' : ''" @change="togglePickerProduct(product, ($event.target as HTMLInputElement).checked)" /></td>
                <td>{{ product.productCode }}</td><td>{{ product.productName }}</td><td>{{ product.specModel || '-' }}</td><td>{{ product.unit }}</td><td>¥ {{ Number(product.purchasePrice || 0).toFixed(2) }}</td>
              </tr>
              <tr v-if="!pickerLoading && !pickerError && !pickerRows.length"><td colspan="6"><EmptyState :message="pickerScope === 'department' ? '当前发起科室暂无匹配的启用目录商品' : '暂无匹配商品'" /></td></tr>
            </tbody>
          </table>
        </div>
        <PaginationControls :page="pickerPagination.page" :size="pickerPagination.size" :total="pickerPagination.total" :loading="pickerLoading" @change-page="loadPickerProducts" @change-size="pickerPagination.size = $event; loadPickerProducts()" />
        <footer>
          <span>已选 {{ pickerSelected.size }} 项（支持跨页多选）</span>
          <div class="purchase-picker-actions">
            <button class="btn" type="button" @click="showProductPicker = false">取消</button>
            <button class="btn btn-primary" type="button" :disabled="!pickerSelected.size || pickerLoading || !!pickerError" @click="addSelectedProducts">加入采购明细</button>
          </div>
        </footer>
      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="showCreateModal" class="modal-mask purchase-create-mask" tabindex="-1" @click.self="showCreateModal = false" @keydown.esc="showCreateModal = false">
      <section class="edit-modal purchase-create-modal" role="dialog" aria-modal="true" aria-labelledby="purchase-order-create-title">

        <!-- ====== 头部 ====== -->
        <header class="purchase-create-header">
          <div class="purchase-create-header-main">
            <i class="purchase-create-header-icon"><PackagePlus :size="18" /></i>
            <div>
              <p>采购订单录入</p>
              <h3 id="purchase-order-create-title">新增采购订单</h3>
            </div>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭新增采购订单" @click="showCreateModal = false"><X :size="18" /></button>
        </header>

        <!-- ====== 顶部概要卡（与右下"合计"呼应） ====== -->
        <section class="purchase-create-overview">
          <article class="purchase-create-overview-card tone-blue">
            <span class="purchase-create-overview-icon"><PackagePlus :size="15" /></span>
            <div>
              <small>采购类型</small>
              <strong>{{ form.purchaseType === 'temporary' ? '临时采购' : form.purchaseType === 'urgent' ? '紧急采购' : '常规采购' }}</strong>
            </div>
          </article>
          <article class="purchase-create-overview-card tone-green">
            <span class="purchase-create-overview-icon"><FileCheck2 :size="15" /></span>
            <div>
              <small>订单来源</small>
              <strong>{{ form.orderSource === 'plan' ? '采购计划' : form.orderSource === 'demand' ? '采购需求' : '手工创建' }}</strong>
            </div>
          </article>
          <article class="purchase-create-overview-card tone-amber">
            <span class="purchase-create-overview-icon"><Calendar :size="15" /></span>
            <div>
              <small>预计到货</small>
              <strong>{{ form.expectedArrivalDate || '待定' }}</strong>
            </div>
          </article>
          <article class="purchase-create-overview-card tone-purple">
            <span class="purchase-create-overview-icon"><Truck :size="15" /></span>
            <div>
              <small>供应商</small>
              <strong>{{ form.supplierName || '未选择' }}</strong>
            </div>
          </article>
        </section>

        <!-- ====== 主体：左右双栏 ====== -->
        <div class="purchase-create-body">

          <!-- 左侧：采购信息 -->
          <section class="purchase-create-panel">
            <div class="purchase-create-panel-header">
              <i class="purchase-create-panel-icon is-green"><FileCheck2 :size="13" /></i>
              <div>
                <h4>采购信息</h4>
                <p>填写采购类型、来源、供应商与到货时间</p>
              </div>
            </div>
            <div class="purchase-create-form-grid">
              <label class="purchase-create-field">
                <span><em>*</em> 供应商</span>
                <select v-model="form.supplierName">
                  <option value="">请选择供应商</option>
                  <option v-for="supplier in suppliers" :key="supplier.supplierName" :value="supplier.supplierName">
                    {{ supplier.supplierName }}
                  </option>
                </select>
              </label>
              <label class="purchase-create-field">
                <span>订单来源</span>
                <select v-model="form.orderSource">
                  <option value="manual">手工创建</option>
                  <option value="plan">采购计划</option>
                  <option value="demand">采购需求</option>
                </select>
              </label>
              <label class="purchase-create-field">
                <span>采购类型</span>
                <select v-model="form.purchaseType">
                  <option value="regular">常规采购</option>
                  <option value="temporary">临时采购</option>
                  <option value="urgent">紧急采购</option>
                </select>
              </label>
              <label class="purchase-create-field">
                <span>预计到货日期</span>
                <input v-model="form.expectedArrivalDate" type="date" />
              </label>
              <label class="purchase-create-field is-wide">
                <span>订单备注</span>
                <textarea
                  v-model="form.remark"
                  rows="3"
                  placeholder="如：到货地点、付款条款、质量要求等"
                />
              </label>
            </div>

            <!-- 字段提示 -->
            <div class="purchase-create-tips">
              <p><strong>填写提示</strong></p>
              <ul>
                <li>供应商、订单来源、采购类型、预计到货日期为订单基础信息，保存后随订单流转。</li>
                <li>订单备注会写入订单跟踪，便于收货 / 财务 / 仓储查询。</li>
                <li>右侧"采购明细"中的单价、金额按当前医院目录价与商品规格自动计算。</li>
              </ul>
            </div>
          </section>

          <!-- 右侧：采购明细 -->
          <section class="purchase-create-panel">
            <div class="purchase-create-panel-header">
              <i class="purchase-create-panel-icon is-blue"><List :size="13" /></i>
              <div>
                <h4>采购明细</h4>
                <p>从医院商品目录选择商品并填写采购数量</p>
              </div>
            </div>

            <!-- 工具栏：检索 / 全部 / 添加 / 删除 -->
            <div class="purchase-create-toolbar">
              <label class="purchase-create-search">
                <Search :size="14" />
                <input v-model="productSearchQuery" placeholder="商品编码 / 名称 / 规格" />
              </label>
              <button class="btn" type="button" :disabled="filteredProducts.length === 0" @click="addItem">
                <Plus :size="14" /> 添加商品
              </button>
            </div>

            <!-- 商品行卡列表 -->
            <div class="purchase-create-items">
              <article
                v-for="(item, index) in form.items"
                :key="index"
                class="purchase-create-item"
              >
                <div class="purchase-create-item-row">
                  <div class="purchase-create-item-product">
                    <span class="purchase-create-item-label">商品</span>
                    <select v-model="item.productCode" :class="{ 'is-empty': !item.productCode }" @change="fillProduct(index)">
                      <option value="">点击选择商品</option>
                      <option v-for="product in filteredProducts" :key="product.productCode" :value="product.productCode">
                        {{ product.productName }} · {{ product.specModel }}
                      </option>
                    </select>
                    <small v-if="item.productCode" class="purchase-create-item-meta">
                      编码 {{ productByCode(item.productCode)?.productCode || '-' }} · 单位 {{ productByCode(item.productCode)?.unit || '-' }}
                    </small>
                    <small v-else class="purchase-create-item-meta is-placeholder">
                      请从商品目录选择一项
                    </small>
                  </div>
                  <div class="purchase-create-item-cell">
                    <span class="purchase-create-item-label">采购数量</span>
                    <input v-integer-quantity v-model.number="item.quantity" type="number" min="1" step="1" />
                  </div>
                  <div class="purchase-create-item-cell">
                    <span class="purchase-create-item-label">单位</span>
                    <input v-model="item.unit" placeholder="单位" />
                  </div>
                  <div class="purchase-create-item-cell">
                    <span class="purchase-create-item-label">单价</span>
                    <input v-model.number="item.estimatedUnitPrice" type="number" min="0" step="0.01" placeholder="0.00" />
                  </div>
                  <div class="purchase-create-item-cell purchase-create-item-amount">
                    <span class="purchase-create-item-label">金额</span>
                    <strong>¥ {{ ((Number(item.quantity) || 0) * (Number(item.estimatedUnitPrice) || 0)).toString() }}</strong>
                  </div>
                  <div class="purchase-create-item-actions">
                    <button class="btn-text btn-text-danger" type="button" :disabled="form.items.length === 1" @click="removeItem(index)">
                      <Trash2 :size="14" /> 删除
                    </button>
                  </div>
                </div>
              </article>
            </div>

            <!-- 合计 -->
            <div class="purchase-create-summary">
              <div class="purchase-create-summary-row">
                <span>商品种类</span>
                <strong>{{ form.items.filter((i) => i.productCode).length }} 种</strong>
              </div>
              <div class="purchase-create-summary-row">
                <span>采购总数量</span>
                <strong>{{ form.items.reduce((s, i) => s + (Number(i.quantity) || 0), 0) }}</strong>
              </div>
              <div class="purchase-create-summary-row is-total">
                <span>合计金额</span>
                <strong>¥ {{ form.items.reduce((s, i) => s + (Number(i.quantity) || 0) * (Number(i.estimatedUnitPrice) || 0), 0).toString() }}</strong>
              </div>
            </div>
          </section>

        </div>

        <!-- ====== 底部 ====== -->
        <footer class="purchase-create-footer">
          <span>保存后可在"采购订单"中继续提交、审批、发送与收货验收。</span>
          <div>
            <button class="btn" type="button" @click="showCreateModal = false">取消</button>
            <button class="btn btn-primary" type="button" :disabled="orderSubmitting" @click="submitCreate">
              <Save :size="15" />
              {{ orderSubmitting ? '保存中…' : '保存采购订单' }}
            </button>
          </div>
        </footer>

      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="closeTarget" class="modal-mask">
      <section class="edit-modal">
        <header>
          <h3>关闭采购订单</h3>
          <button class="btn-icon" type="button" @click="closeTarget = null"><X :size="18" /></button>
        </header>
        <div class="close-check-panel">
          <article>
            <span>订单编号</span>
            <strong>{{ closeTarget.orderNo }}</strong>
          </article>
          <article>
            <span>数量履约</span>
            <strong>{{ closeTarget.receivedQuantity }} / {{ closeTarget.orderQuantity }}</strong>
          </article>
          <article>
            <span>剩余数量</span>
            <strong>{{ remainingQty(closeTarget) }}</strong>
          </article>
        </div>
        <label class="close-reason-field">
          <span>关闭原因</span>
          <textarea v-model="closeReason" rows="4" placeholder="请填写关闭原因，系统将写入订单跟踪和审计日志" />
        </label>
        <footer>
          <button class="btn" type="button" @click="closeTarget = null">取消</button>
          <button class="btn btn-primary" type="button" :disabled="!closeReason.trim()" @click="submitClose">确认关闭</button>
        </footer>
      </section>
    </div>
    </Teleport>
  </section>
</template>

<style scoped>
/* =====================================================================
   采购管理 样式优化
   ---------------------------------------------------------------------
   目标：整体切到主数据 md-* 设计语言（页头 + 卡片工作区 + md 表格）。
   1. 页面外壳：md-page 纵向 flex，去掉原 breadcrumb/detail-heading 双段标题
   2. 工作区卡片：页签 / 工具条 / 查询区 / 表格 四段式，与其他菜单一致
   3. 表格：md-table 密度（13px / 10px 12px）、sticky 表头、卡片化滚动容器
   4. 业务列强化：单据号等宽、金额右对齐、选中行主色条、状态徽标收敛
   ===================================================================== */
.purchase-page.md-page {
  /* md-page 自带 padding-bottom 40px，这里只收紧顶部间距 */
  padding: 16px 20px 24px;
  gap: 14px;
}

/* ---------- 工作区卡片 ---------- */
.purchase-workspace {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--md-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
  padding: 14px 16px 16px;
}

/* ---------- 页签 ---------- */
.purchase-tabs {
  gap: 6px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle));
}

.purchase-tabs button {
  min-height: 30px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  border-radius: var(--md-radius, 6px);
}

/* ---------- 工具条 ---------- */
.purchase-toolbar {
  display: flex !important;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-height: 0;
  padding: 10px 12px !important;
  background: var(--md-bg-page, var(--spd-bg-page)) !important;
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  border-radius: var(--md-radius, 6px) !important;
}

.purchase-toolbar .btn {
  min-height: 30px;
  padding: 0 12px;
  font-size: 13px;
}

.purchase-toolbar .btn svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.purchase-query-actions {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  min-height: 50px;
  padding-bottom: 2px;
}

.purchase-product-picker tr.is-picked {
  background: var(--spd-success-bg, #f0fdf4);
}

.purchase-product-picker tr.is-picked td {
  color: var(--spd-text-secondary, #475569);
}

.purchase-product-picker .picker-checkbox {
  cursor: pointer;
}

.purchase-product-picker .picker-checkbox:checked:disabled,
.purchase-product-picker .picker-checkbox:disabled:checked {
  accent-color: var(--spd-success, #16a34a);
  cursor: not-allowed;
}

.purchase-product-picker-mask .master-table {
  font-size: 14px !important;
  line-height: 1.55;
}

.purchase-product-picker-mask .master-table thead th {
  font-size: 13px !important;
  padding: 10px 12px !important;
}

.purchase-product-picker-mask .master-table tbody td {
  font-size: 14px !important;
  padding: 10px 12px !important;
  line-height: 1.55;
}

.purchase-query-actions .btn {
  min-height: 30px;
  padding: 0 14px;
  font-size: 13px;
}

.purchase-query-actions .btn svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.inline-message {
  margin: 0;
  padding: 10px 14px;
  border: 1px solid var(--spd-primary-border);
  border-radius: var(--md-radius, 6px);
  color: var(--spd-primary-text);
  background: var(--spd-primary-bg);
  font-size: 13px;
}

.inline-select {
  min-height: 30px;
  font-size: 13px;
}

.inline-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--spd-text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.action-hint {
  color: var(--spd-text-tertiary);
  font-size: 12px;
  line-height: 1.4;
  margin-left: auto;
}

.action-row--plans .inline-select,
.action-row--orders .inline-select,
.action-row--tracking .inline-select {
  display: none;
}

/* ---------- 查询区 ---------- */
.purchase-query-grid {
  display: grid !important;
  grid-template-columns: repeat(auto-fit, minmax(180px, 240px));
  justify-content: start;
  gap: 10px 14px;
  padding: 12px !important;
  background: var(--md-bg-muted, var(--spd-bg-page));
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
}

.purchase-query-grid label {
  display: grid;
  gap: 4px;
  min-width: 0;
  width: 100%;
}

.purchase-query-grid label > span {
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.purchase-query-grid .md-input,
.purchase-query-grid .md-select {
  min-height: 30px;
  font-size: 13px;
  width: 100%;
}

/* ---------- 列表滚动容器 ---------- */
.purchase-list-scroll {
  max-height: max(300px, calc(100dvh - 420px));
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  background: var(--md-bg-card, #fff);
  scrollbar-width: thin;
  scrollbar-color: var(--md-border, var(--spd-border-default)) transparent;
}

.purchase-list-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.purchase-list-scroll::-webkit-scrollbar-thumb {
  background: var(--md-border, var(--spd-border-default));
  border-radius: 4px;
}

/* ---------- 表格：md 密度 ---------- */
/* .master-table 的 padding / font-size 由 spd-override.css 以 !important
   统一设定，覆盖时需同级 !important 才能进入同一优先级层级 */
.purchase-table {
  min-width: 100%;
  width: 100%;
  font-size: 14px !important;
  line-height: 1.55;
}

.purchase-table thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  padding: 10px 12px !important;
  font-size: 13px !important;
  font-weight: 600;
  letter-spacing: 0.2px;
  white-space: nowrap;
  color: var(--md-text-heading) !important;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%) !important;
  border-bottom: 1px solid var(--md-border, var(--spd-border-default)) !important;
}

.purchase-table tbody td {
  padding: 10px 12px !important;
  font-size: 14px !important;
  line-height: 1.55;
  color: var(--md-text) !important;
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  vertical-align: middle;
}

.purchase-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted, #fafbfc);
}

.purchase-table tbody tr:hover td {
  background: var(--md-primary-soft, var(--spd-bg-active)) !important;
}

.purchase-table tbody td strong {
  color: var(--md-text-heading) !important;
  font-weight: 600;
}

.purchase-table tbody td small {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 12px;
  margin-top: 1px;
}

/* ---------- 单据号 / 金额 ---------- */
.document-no-cell {
  font-family: var(--md-font-mono, ui-monospace, monospace);
  font-size: 12px !important;
  font-weight: 600;
  color: var(--md-text-heading) !important;
  white-space: nowrap;
}

.purchase-plan-amount {
  font-family: var(--md-font-mono, ui-monospace, monospace);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--md-primary-strong, var(--spd-text-heading));
  white-space: nowrap;
  text-align: right;
}

/* 采购计划仅 8 列，保留适度最小宽度避免字段挤压 */
.purchase-plan-table {
  min-width: 1080px;
}

.purchase-plan-table td {
  vertical-align: middle;
}

/* ---------- 选择列 ---------- */
.order-select-column {
  width: 44px;
  text-align: center;
}

.order-select-column input {
  width: 14px;
  height: 14px;
  accent-color: var(--md-primary, var(--spd-primary));
  cursor: pointer;
  vertical-align: middle;
  margin: 0;
}

/* 选中订单：淡背景 + 左侧主色条 */
.selected-order-row td {
  background: var(--md-primary-soft, var(--spd-primary-bg)) !important;
}

.selected-order-row td:first-child {
  box-shadow: inset 3px 0 0 var(--md-primary, var(--spd-primary));
}

/* ---------- 状态徽标 ---------- */
.purchase-table .status-badge {
  min-height: 20px;
  padding: 0 9px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
}

/* ---------- 行内操作 ---------- */
.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}

.row-actions .btn-text {
  min-height: 24px;
  padding: 0 8px;
  font-size: 12px;
}

/* ---------- 履约进度 ---------- */
.fulfillment-cell {
  display: grid;
  gap: 3px;
  min-width: 88px;
}

.fulfillment-cell span {
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  color: var(--md-text-muted, var(--spd-text-tertiary));
}

.fulfillment-cell i {
  display: block;
  height: 3px;
  border-radius: 2px;
  background: var(--md-border-soft, var(--spd-border-subtle));
  overflow: hidden;
}

.fulfillment-cell b {
  display: block;
  height: 100%;
  border-radius: 2px;
  background: var(--md-primary, var(--spd-primary));
  transition: width 0.2s ease;
}

/* ---------- 剩余数量 ---------- */
.quantity-gap {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 8px;
  border-radius: var(--md-radius-sm, 4px);
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  font-weight: 600;
}

.quantity-gap.pending {
  color: var(--spd-warning-text, #a15c00);
  background: var(--spd-warning-bg, #fff7e6);
}

.quantity-gap.done {
  color: var(--spd-success-text, #087a4d);
  background: var(--spd-success-bg, #eaf8f2);
}

/* ---------- 明细角标 ---------- */
.item-count-badge {
  display: inline-flex;
  align-items: center;
  margin-left: 5px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--md-bg-muted, var(--spd-bg-page));
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 11px;
  font-weight: 600;
}

.item-summary {
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: 12px;
}

.demand-group-items-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ---------- 空状态 ---------- */
.purchase-table .approval-empty {
  padding: 52px 16px;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 13px;
  text-align: center;
}

.btn-danger-soft {
  border-color: var(--spd-danger-border) !important;
  background: var(--spd-danger-bg) !important;
  color: var(--spd-danger-text) !important;
}

.btn-danger-soft:hover:not(:disabled) {
  border-color: var(--spd-danger) !important;
  background: color-mix(in srgb, var(--spd-danger) 12%, white) !important;
  color: var(--spd-danger-text) !important;
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .purchase-page.md-page {
    padding: 12px 12px 20px;
  }

  .purchase-workspace {
    padding: 12px 12px 14px;
  }

  .purchase-query-grid {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }

  .purchase-list-scroll {
    max-height: max(240px, calc(100dvh - 380px));
  }
}

.order-detail-modal {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  width: min(1500px, 100%);
  height: min(760px, 90vh);
  max-height: 90vh;
  overflow: hidden;
  padding: 0;
}

.order-detail-body {
  min-height: 0;
  overflow: auto;
  padding: 18px 22px;
  background: #f7f9fb;
}

.order-detail-summary {
  margin-bottom: 14px;
  border: 1px solid #dfe7ee;
  border-radius: 7px;
  padding: 12px 14px;
  background: #fff;
}

.order-detail-summary strong {
  color: #24495b;
}

.order-detail-table-wrap {
  border: 1px solid #dfe7ee;
  border-radius: 7px;
  background: #fff;
}

.order-detail-table {
  min-width: 2400px;
}

.order-detail-table th {
  white-space: nowrap;
}

.order-detail-table td {
  min-width: 110px;
  vertical-align: middle;
}

.order-detail-table td:nth-child(3),
.order-detail-table td:nth-child(4),
.order-detail-table td:nth-child(5),
.order-detail-table td:nth-child(6),
.order-detail-table td:nth-child(13),
.order-detail-table td:nth-child(14),
.order-detail-table td:nth-child(15) {
  min-width: 160px;
}

.order-operation-modal {
  width: min(560px, 100%);
}

.order-operation-modal header p,
.order-operation-modal header h3 {
  margin: 0;
}

.order-operation-modal header p {
  margin-bottom: 3px;
  color: #728596;
  font-size: 12px;
}

.order-operation-body {
  display: grid;
  gap: 16px;
  margin: 18px 0;
}

.order-operation-order,
.order-operation-body label {
  display: grid;
  gap: 6px;
}

.order-operation-order {
  border: 1px solid #dfe7ee;
  border-radius: 7px;
  padding: 12px;
  background: #f7f9fb;
}

.order-operation-order span,
.order-operation-body label > span {
  color: #607587;
  font-size: 12px;
}

.order-operation-body textarea {
  resize: vertical;
  border: 1px solid #d6e0ea;
  border-radius: 7px;
  padding: 10px;
  color: #243f50;
  font: inherit;
}

.order-attachment-modal {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: min(1080px, 100%);
  height: min(680px, 88vh);
  max-height: 88vh;
  overflow: hidden;
  padding: 0;
}

.order-attachment-body {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
  padding: 16px;
  background: #f7f9fb;
}

.order-attachment-list,
.order-attachment-preview {
  min-height: 0;
  border: 1px solid #dfe7ee;
  border-radius: 8px;
  background: #fff;
}

.order-attachment-list {
  overflow: auto;
  padding: 12px;
}

.order-attachment-list ul {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}

.order-attachment-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-bottom: 1px solid #edf1f4;
  padding: 10px 2px;
}

.order-attachment-list li > div {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.order-attachment-list strong {
  overflow: hidden;
  color: #263f50;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-attachment-list small {
  color: #8292a1;
}

.order-attachment-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f3f6f8;
}

.order-attachment-preview img,
.order-attachment-preview iframe {
  width: 100%;
  height: 100%;
  max-width: 100%;
  border: 0;
  object-fit: contain;
}

.order-attachment-preview .approval-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}

.smart-replenishment-table {
  min-width: 1380px;
}

.smart-replenishment-table th,
.smart-replenishment-table td {
  border-bottom: 1px solid #e8edf2;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}


.fulfillment-cell {
  min-width: 120px;
}

.fulfillment-cell span {
  display: block;
  margin-bottom: 6px;
  color: #52677a;
}

.fulfillment-cell i {
  display: block;
  width: 100%;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: #e7eef5;
}

.fulfillment-cell b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #0f766e;
}

.quantity-gap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 54px;
  min-height: 30px;
  border-radius: 8px;
  font-weight: 700;
}

.quantity-gap.pending {
  color: #b45309;
  background: #fff7ed;
}

.quantity-gap.done {
  color: #0f766e;
  background: #ecfdf5;
}

.tracking-panel {
  display: grid;
  gap: 16px;
}

.tracking-header,
.edit-modal header,
.edit-modal footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.purchase-detail-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: #52677a;
}

.timeline-list {
  display: grid;
  gap: 10px;
}

.timeline-list article {
  border: 1px solid #dbe5ee;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.timeline-list span,
.timeline-list small {
  display: block;
  color: #66788a;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 34, 51, 0.35);
}

.edit-modal {
  width: min(720px, 100%);
  max-height: 88vh;
  overflow: auto;
  border-radius: 8px;
  padding: 22px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(15, 34, 51, 0.22);
}

.edit-modal.wide {
  width: min(980px, 100%);
}

.purchase-product-picker-mask {
  z-index: 2100;
}

.purchase-product-picker-mask .edit-modal.wide {
  width: min(1080px, 100%);
  height: min(820px, 96vh);
  max-height: 96vh;
  display: flex;
  flex-direction: column;
  padding: 18px 22px 18px;
  overflow: hidden;
}

.purchase-product-picker-mask header {
  margin: 0 0 10px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
}

.purchase-product-picker-mask header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f2a3a;
  line-height: 1.2;
}

.purchase-product-picker-mask header .btn-icon {
  margin-left: auto;
}

.purchase-picker-dept {
  margin: 0 0 10px;
  padding: 7px 12px;
  border-radius: 6px;
  background: #f0f7f9;
  color: #52677a;
  font-size: 13px;
  line-height: 1.5;
  flex-shrink: 0;
}

.purchase-picker-dept strong {
  color: #0f766e;
  margin-left: 4px;
  font-weight: 600;
}

.purchase-product-picker-mask .purchase-entry-toolbar {
  margin: 0 0 10px;
  flex-shrink: 0;
}

.purchase-product-picker-mask .table-scroll {
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow: auto;
  border: 1px solid #dfe7ee;
  border-radius: 7px;
}

.purchase-product-picker-mask .pagination-controls,
.purchase-product-picker-mask .master-pagination {
  flex-shrink: 0;
  margin-top: 10px;
}

.purchase-product-picker-mask footer {
  flex-shrink: 0;
  margin-top: 12px;
  padding: 12px 0 0;
  border-top: 1px solid #ecf0f3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.purchase-picker-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.purchase-entry-search > span {
  flex-shrink: 0;
  white-space: nowrap;
}

.purchase-entry-mask {
  padding: 20px;
}

.purchase-entry-modal {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  width: min(1320px, 100%);
  height: min(820px, 92vh);
  max-height: 92vh;
  overflow: hidden;
  padding: 0;
}

.purchase-entry-header {
  padding: 16px 22px;
  border-bottom: 1px solid #dbe5ee;
  background: #fff;
}

.purchase-entry-header p,
.purchase-entry-header h3 {
  margin: 0;
}

.purchase-entry-header p {
  margin-bottom: 3px;
  color: #708396;
  font-size: 12px;
}

.purchase-entry-header h3 {
  color: #15394a;
  font-size: 20px;
}

.purchase-entry-body {
  min-height: 0;
  overflow: auto;
  padding: 18px 22px;
  background: #f7f9fb;
}

.purchase-entry-section {
  margin: 0 0 16px;
  border: 1px solid #dfe7ee;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.purchase-entry-detail-section {
  margin-bottom: 0;
}

.purchase-entry-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.purchase-entry-section-heading h4,
.purchase-entry-section-heading small {
  display: block;
  margin: 0;
}

.purchase-entry-section-heading h4 {
  color: #1a3b4d;
  font-size: 15px;
}

.purchase-entry-section-heading small {
  margin-top: 4px;
  color: #7b8d9d;
  font-size: 12px;
}

.purchase-entry-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 14px;
}

.purchase-entry-meta-grid label {
  display: grid;
  gap: 6px;
  color: #425a6b;
  font-size: 13px;
  font-weight: 600;
}

.purchase-entry-meta-grid em {
  color: #dc4c4c;
  font-style: normal;
}

.purchase-entry-meta-grid input,
.purchase-entry-meta-grid select,
.purchase-entry-table input,
.purchase-entry-table select {
  min-height: 38px;
  border: 1px solid #d6e0ea;
  border-radius: 6px;
  padding: 0 10px;
  background: #fff;
  color: #243f50;
  font: inherit;
}

.purchase-entry-meta-grid input:focus,
.purchase-entry-meta-grid select:focus,
.purchase-entry-table input:focus,
.purchase-entry-table select:focus,
.purchase-entry-search:focus-within {
  border-color: #0f766e;
  outline: 2px solid rgba(15, 118, 110, 0.12);
  outline-offset: 0;
}

.purchase-entry-remark {
  grid-column: span 3;
}

.purchase-entry-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.purchase-entry-summary span {
  border: 1px solid #d8e8e5;
  border-radius: 999px;
  padding: 5px 10px;
  background: #f1f8f7;
  color: #526d6b;
  font-size: 12px;
}

.purchase-entry-summary strong {
  color: #0f766e;
}

.purchase-entry-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.purchase-entry-search {
  display: flex;
  align-items: center;
  width: min(360px, 100%);
  min-height: 38px;
  border: 1px solid #d6e0ea;
  border-radius: 6px;
  padding: 0 10px;
  background: #fff;
  color: #7a8d9c;
}

.purchase-entry-search input {
  width: 100%;
  border: 0;
  outline: 0;
  padding: 0 0 0 8px;
  background: transparent;
  font: inherit;
}

.purchase-entry-table-wrap {
  overflow: auto;
  border: 1px solid #dfe7ee;
  border-radius: 7px;
}

.purchase-entry-table {
  min-width: 1080px;
}

.purchase-entry-table th {
  white-space: nowrap;
  background: #f4f7f9;
}

.purchase-entry-table td {
  vertical-align: middle;
}

.purchase-entry-table select {
  width: 100%;
  min-width: 250px;
}

.purchase-entry-table input {
  width: 88px;
}

.purchase-code-cell {
  width: 130px;
  color: #52697a;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.purchase-product-cell {
  min-width: 270px;
}

.purchase-quantity-cell {
  width: 110px;
}

.purchase-amount-cell {
  color: #0f766e;
  font-weight: 700;
  white-space: nowrap;
}

.purchase-row-delete {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  padding: 5px 2px;
  background: transparent;
  color: #c24141;
  cursor: pointer;
  font: inherit;
}

.purchase-row-delete:disabled {
  color: #aab5bf;
  cursor: not-allowed;
}

.purchase-entry-footer {
  padding: 14px 22px;
  border-top: 1px solid #dbe5ee;
  background: #fff;
}

.purchase-entry-footer > span {
  color: #718494;
  font-size: 12px;
}

.purchase-entry-footer > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.modal-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 1fr));
  gap: 14px;
  margin: 18px 0;
}

.modal-grid label,
.item-editor article {
  display: grid;
  gap: 6px;
}

.modal-grid input,
.modal-grid select,
.item-editor input,
.item-editor select {
  min-height: 40px;
  border: 1px solid #d6e0ea;
  border-radius: 8px;
  padding: 0 10px;
  font: inherit;
}

.item-editor {
  display: grid;
  gap: 10px;
  margin-bottom: 18px;
}

.item-editor article {
  grid-template-columns: minmax(220px, 1fr) 90px 90px 110px 60px;
  align-items: end;
}

.close-check-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 12px;
  margin: 18px 0;
}

.close-check-panel article {
  border: 1px solid #dbe5ee;
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
}

.close-check-panel span,
.close-check-panel strong {
  display: block;
}

.close-check-panel span {
  color: #66788a;
}

.close-reason-field {
  display: grid;
  gap: 8px;
  margin-bottom: 18px;
}

.close-reason-field textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid #d6e0ea;
  border-radius: 8px;
  padding: 10px;
  font: inherit;
}

/* ── Purchase create modal ─────────────────────────────────────────────
   双栏布局：左 = 采购信息（基本字段 + 提示卡）
              右 = 采购明细（行卡 + 合计）
   顶部 4 张概要卡，底部固定操作区。
   ─────────────────────────────────────────────────────────────────── */
.purchase-create-mask {
  padding: 24px;
}

.purchase-create-modal {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  width: min(1280px, 100%);
  height: min(820px, 92vh);
  max-height: 92vh;
  overflow: hidden;
  padding: 0;
  border-radius: 14px;
  background: #f7f9fb;
  box-shadow: 0 20px 60px rgba(15, 34, 51, 0.22);
}

/* ---------- 头部 ---------- */
.purchase-create-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.purchase-create-header-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.purchase-create-header-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: linear-gradient(180deg, #0f766e 0%, #0b5e57 100%);
  color: #fff;
  box-shadow: 0 2px 6px rgba(15, 118, 110, 0.25);
}

.purchase-create-header p {
  margin: 0 0 2px;
  color: #708396;
  font-size: 12px;
}

.purchase-create-header h3 {
  margin: 0;
  color: #15394a;
  font-size: 18px;
  font-weight: 700;
}

/* ---------- 顶部 4 张概要卡 ---------- */
.purchase-create-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 14px 24px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}

.purchase-create-overview-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid;
  border-radius: 10px;
  min-width: 0;
}

.purchase-create-overview-card small {
  display: block;
  font-size: 11px;
  opacity: 0.85;
  margin-bottom: 2px;
}

.purchase-create-overview-card strong {
  display: block;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.purchase-create-overview-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.purchase-create-overview-card.tone-blue   { background: linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%); border-color: #93c5fd; color: #1e3a8a; }
.purchase-create-overview-card.tone-green  { background: linear-gradient(180deg, #d1fae5 0%, #a7f3d0 100%); border-color: #6ee7b7; color: #065f46; }
.purchase-create-overview-card.tone-amber  { background: linear-gradient(180deg, #fef3c7 0%, #fde68a 100%); border-color: #fcd34d; color: #78350f; }
.purchase-create-overview-card.tone-purple { background: linear-gradient(180deg, #ede9fe 0%, #ddd6fe 100%); border-color: #c4b5fd; color: #4c1d95; }

.purchase-create-overview-card.tone-blue   .purchase-create-overview-icon { color: #1d4ed8; }
.purchase-create-overview-card.tone-green  .purchase-create-overview-icon { color: #047857; }
.purchase-create-overview-card.tone-amber  .purchase-create-overview-icon { color: #b45309; }
.purchase-create-overview-card.tone-purple .purchase-create-overview-icon { color: #6d28d9; }

/* ---------- 主体：左右双栏 ---------- */
.purchase-create-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 14px;
  min-height: 0;
  padding: 14px 24px;
  overflow: hidden;
}

.purchase-create-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #dfe7ee;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.purchase-create-panel-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafdfb;
}

.purchase-create-panel-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  flex-shrink: 0;
}

.purchase-create-panel-icon.is-green { background: #d1fae5; color: #047857; }
.purchase-create-panel-icon.is-blue  { background: #dbeafe; color: #1d4ed8; }

.purchase-create-panel-header h4 {
  margin: 0;
  color: #15394a;
  font-size: 14px;
  font-weight: 700;
}

.purchase-create-panel-header p {
  margin: 2px 0 0;
  color: #7b8d9d;
  font-size: 12px;
}

/* ---------- 左栏：采购信息表单 ---------- */
.purchase-create-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 14px;
  padding: 16px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.purchase-create-field {
  display: grid;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #425a6b;
}

.purchase-create-field.is-wide { grid-column: 1 / -1; }

.purchase-create-field em {
  color: #dc4c4c;
  font-style: normal;
  margin-right: 2px;
}

.purchase-create-field input,
.purchase-create-field select,
.purchase-create-field textarea {
  width: 100%;
  min-height: 36px;
  border: 1px solid #d6e0ea;
  border-radius: 6px;
  padding: 0 10px;
  background: #fff;
  color: #243f50;
  font: inherit;
  font-weight: 400;
  font-size: 13px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.purchase-create-field textarea {
  min-height: 70px;
  padding: 8px 10px;
  resize: vertical;
  line-height: 1.5;
}

.purchase-create-field input:focus,
.purchase-create-field select:focus,
.purchase-create-field textarea:focus {
  border-color: #0f766e;
  outline: 2px solid rgba(15, 118, 110, 0.12);
  outline-offset: 0;
}

.purchase-create-tips {
  margin: 0 16px 16px;
  padding: 10px 12px;
  border: 1px solid #c7d2fe;
  background: #f5f7ff;
  border-radius: 8px;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.purchase-create-tips p { margin: 0 0 4px; color: #1e3a8a; }
.purchase-create-tips strong { color: #1e3a8a; }
.purchase-create-tips ul { margin: 0; padding-left: 18px; }
.purchase-create-tips li { margin-bottom: 2px; }

/* ---------- 右栏：采购明细 ---------- */
.purchase-create-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafdfb;
}

.purchase-create-search {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  max-width: 360px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #94a3b8;
}

.purchase-create-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #102033;
  font: inherit;
  font-size: 13px;
}

.purchase-create-search:focus-within {
  border-color: #0f766e;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

.purchase-create-items {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.purchase-create-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fafdfb;
  padding: 10px 12px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.purchase-create-item:hover {
  border-color: #6ee7b7;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.06);
}

.purchase-create-item-row {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 90px 90px 110px 120px 70px;
  gap: 8px 10px;
  align-items: end;
}

.purchase-create-item-product {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.purchase-create-item-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.purchase-create-item-amount strong {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  width: 100%;
  padding: 0 8px;
  border: 1px solid #d6e0ea;
  border-radius: 6px;
  background: #ecfdf5;
  color: #047857;
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.purchase-create-item-label {
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.purchase-create-item-product select,
.purchase-create-item-cell input {
  width: 100%;
  min-height: 34px;
  height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 8px;
  background: #fff;
  color: #102033;
  font: inherit;
  font-size: 13px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.purchase-create-item-product select.is-empty {
  color: #94a3b8;
}

.purchase-create-item-product select:focus,
.purchase-create-item-cell input:focus {
  border-color: #0f766e;
  outline: 2px solid rgba(15, 118, 110, 0.12);
  outline-offset: 0;
}

.purchase-create-item-meta {
  color: #64748b;
  font-size: 11px;
  line-height: 1.4;
}

.purchase-create-item-meta.is-placeholder {
  color: #c2410c;
  font-style: italic;
}

.purchase-create-item-actions {
  display: flex;
  align-items: end;
  justify-content: flex-end;
  min-width: 0;
}

.purchase-create-item-actions .btn-text {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fff;
  color: #b91c1c;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.purchase-create-item-actions .btn-text:hover:not(:disabled) {
  background: #fee2e2;
  border-color: #fca5a5;
}

.purchase-create-item-actions .btn-text:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ---------- 合计 ---------- */
.purchase-create-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 12px 14px;
  border-top: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #fafdfb 0%, #ecfdf5 100%);
}

.purchase-create-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  font-size: 12px;
  color: #475569;
}

.purchase-create-summary-row strong {
  color: #047857;
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.purchase-create-summary-row.is-total {
  grid-column: span 3;
  background: linear-gradient(180deg, #d1fae5 0%, #a7f3d0 100%);
  border-color: #6ee7b7;
}

.purchase-create-summary-row.is-total span { color: #065f46; font-weight: 600; }
.purchase-create-summary-row.is-total strong { color: #064e3b; font-size: 18px; }

/* ---------- 底部 ---------- */
.purchase-create-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 24px;
  border-top: 1px solid #e2e8f0;
  background: #fff;
}

.purchase-create-footer > span {
  color: #708396;
  font-size: 12px;
}

.purchase-create-footer > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1100px) {
  .purchase-create-body { grid-template-columns: 1fr; }
  .purchase-create-overview { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .purchase-create-item-row {
    grid-template-columns: minmax(0, 1.4fr) 80px 80px 100px 100px 60px;
  }
}

@media (max-width: 720px) {
  .purchase-create-mask { padding: 0; }
  .purchase-create-modal {
    width: 100%;
    height: 100vh;
    max-height: 100vh;
    border-radius: 0;
  }
  .purchase-create-form-grid { grid-template-columns: 1fr; }
  .purchase-create-overview { grid-template-columns: 1fr; }
  .purchase-create-item-row {
    grid-template-columns: 1fr 1fr;
  }
  .purchase-create-item-actions { grid-column: 1 / -1; justify-content: flex-end; }
  .purchase-create-summary { grid-template-columns: 1fr; }
  .purchase-create-summary-row.is-total { grid-column: auto; }
}

.purchase-smart-modal {
  width: min(1280px, 94vw);
}

.purchase-smart-modal header p {
  margin: 0 0 4px;
  color: #66788a;
}

.analysis-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 0;
  color: #52677a;
}

.analysis-error {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.analysis-qty-input {
  width: 96px;
  min-height: 36px;
  border: 1px solid #d6e0ea;
  border-radius: 8px;
  padding: 0 10px;
  font: inherit;
}

.muted-cell {
  display: block;
  color: #718096;
  font-size: 13px;
}

.form-section {
  margin: 16px 0;
}

.form-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a3b4d;
  margin: 0 0 10px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #e8edf2;
}

.product-search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.product-search-input {
  min-height: 38px;
  border: 1px solid #d6e0ea;
  border-radius: 8px;
  padding: 0 12px;
  font: inherit;
  width: min(320px, 100%);
}

.product-search-hint {
  font-size: 12px;
  color: #8899aa;
}

.item-editor-header {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 80px 80px 100px 60px;
  gap: 8px;
  padding: 0 2px;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #66788a;
}

.demand-item-header {
  grid-template-columns: minmax(220px, 1fr) 100px 60px;
}

.demand-item-editor {
  display: grid;
  gap: 10px;
  margin-bottom: 18px;
}

.demand-item-editor article {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 100px 60px;
  gap: 8px;
  align-items: end;
}

.demand-item-editor input,
.demand-item-editor select {
  min-height: 40px;
  border: 1px solid #d6e0ea;
  border-radius: 8px;
  padding: 0 10px;
  font: inherit;
}

.item-editor-header span {
  padding: 0 2px;
}

.add-item-btn {
  justify-self: start;
}

/* ── Demand group table ── */
.purchase-demand-table {
  min-width: 1400px;
}

.demand-group-row td {
  vertical-align: top;
}

.demand-group-items-cell {
  min-width: 160px;
  padding: 8px 14px !important;
}

.demand-group-items-cell .item-summary {
  display: block;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 4px;
}

.detail-summary-bar {
  display: flex;
  gap: 20px;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  font-size: 13px;
  color: #475569;
}

.detail-summary-bar span {
  white-space: nowrap;
}

.purchase-detail-modal {
  max-width: 1100px;
  width: 92vw;
}

.purchase-detail-modal table {
  margin: 0;
}

.item-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 22px;
  border-radius: 999px;
  background: #eef8f6;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  padding: 0 7px;
  margin-left: 6px;
  vertical-align: middle;
}

@media (max-width: 860px) {
  .purchase-query-grid,
  .close-check-panel {
    grid-template-columns: 1fr;
  }
  .item-editor article {
    grid-template-columns: 1fr;
  }
  .demand-item-editor article {
    grid-template-columns: 1fr;
  }
  .purchase-entry-meta-grid {
    grid-template-columns: repeat(2, minmax(160px, 1fr));
  }
  .purchase-entry-remark {
    grid-column: span 2;
  }
  .order-attachment-body {
    grid-template-columns: 1fr;
    overflow: auto;
  }
  .order-attachment-list {
    max-height: 240px;
  }
  .order-attachment-preview {
    min-height: 360px;
  }
}

@media (max-width: 560px) {
  .purchase-create-modal .modal-grid,
  .item-editor article {
    grid-template-columns: 1fr;
  }
  .purchase-entry-mask {
    padding: 0;
  }
  .purchase-entry-modal {
    width: 100%;
    height: 100vh;
    max-height: 100vh;
    border-radius: 0;
  }
  .purchase-entry-body,
  .purchase-entry-header,
  .purchase-entry-footer {
    padding-left: 14px;
    padding-right: 14px;
  }
  .purchase-entry-meta-grid {
    grid-template-columns: 1fr;
  }
  .purchase-entry-remark {
    grid-column: auto;
  }
  .purchase-entry-section-heading,
  .purchase-entry-toolbar,
  .purchase-entry-footer {
    align-items: stretch;
    flex-direction: column;
  }
  .purchase-entry-footer > div {
    justify-content: flex-end;
  }
}
</style>
