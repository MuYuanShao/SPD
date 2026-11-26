<script setup lang="ts">
import { computed, reactive, ref, watch, Teleport } from 'vue'
import { fetchPurchaseSelectableProducts, type PurchaseProductOption } from '../../api/purchaseOrders'
import {
  Ban,
  Calendar,
  CheckCircle2,
  ClipboardCheck,
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
import SpdStatusChip from '../../components/common/SpdStatusChip.vue'
import PurchasePlanDetailDialog from '../../components/purchase/PurchasePlanDetailDialog.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
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
import { formatBusinessText, formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'

const {
  activeTab,
  rows,
  plans,
  smartRows,
  smartPeriods,
  smartSelectedPeriod,
  smartAnalysisNo,
  smartDeptCode,
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
  orderActionPending,
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
const planDetailNo = ref('')
const authStore = useAuthStore()
const notify = useNotify()

/**
 * 动作条右侧是否真的有按钮。
 *
 * 「订单跟踪」页签在未选中订单时右侧没有任何动作，
 * 若无条件渲染右侧动作区，会留下一段空的空白区域。
 * 这里用计算属性把关：有按钮才渲染，无按钮整块不渲染。
 */
const hasAsideActions = computed(() => {
  if (activeTab.value === 'smart') return true
  if (activeTab.value === 'demands') {
    return authStore.hasPermission('purchase-demand:create')
      || authStore.hasPermission('purchase-demand:convert-plan')
  }
  if (activeTab.value === 'orders') return true
  if (activeTab.value === 'tracking') return Boolean(detail.value)
  return false
})

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

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const PURCHASEORDER_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'selection', label: '选择', width: 70, visible: true, weight: 1, minWidth: 56, maxWidth: 90 },
  { key: 'orderNo', label: '订单编号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'supplierName', label: '供应商', width: 170, visible: true, weight: 5, minWidth: 120, maxWidth: 300 },
  { key: 'source', label: '来源', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'detailCount', label: '明细数', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'purchaseQty', label: '采购数量', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'fulfillProgress', label: '履约进度', width: 120, visible: true, weight: 2, minWidth: 100, maxWidth: 170 },
  { key: 'remainingQty', label: '剩余数量', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'totalAmount', label: '总金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'expectedArrival', label: '预计到货', width: 120, visible: true, weight: 2, minWidth: 100, maxWidth: 170 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'updateTime', label: '修改时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 320 }
]

const purchaseOrderCols = useListColumns({
  storageKey: 'spd-purchase-order-cols-v1',
  defaults: PURCHASEORDER_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const purchaseOrderTableRef = purchaseOrderCols.tableRef
const isPurchaseOrderColVisible = (key: string) => purchaseOrderCols.isColumnVisible(key)
const purchaseOrderColBinding = (key: string) => purchaseOrderCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const purchaseOrderActionFix = useActionColumnFix()
const purchaseOrderActionFixed = computed(() => purchaseOrderActionFix.actionFixedBinding.value)

watch(
  [purchaseOrderTableRef],
  () => {
    purchaseOrderActionFix.observe(purchaseOrderTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const purchaseOrderColSettingsAnchorRef = ref<HTMLElement | null>(null)
const purchaseOrderColSettingsPopRef = ref<HTMLElement | null>(null)
const purchaseOrderColSettingsPop = useColSettingsPopover({
  open: purchaseOrderCols.settingsOpen,
  anchorRef: purchaseOrderColSettingsAnchorRef,
  popRef: purchaseOrderColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const PURCHASEPLAN_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'planNo', label: '计划编号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'detailCount', label: '商品明细数', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'orderCount', label: '已生成订单数', width: 120, visible: true, weight: 2, minWidth: 100, maxWidth: 170 },
  { key: 'amount', label: '金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'supplierCount', label: '配送商数', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'sourceDept', label: '发起科室', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'actions', label: '操作', width: 200, visible: true, fill: true, minWidth: 150, maxWidth: 320 }
]

const purchasePlanCols = useListColumns({
  storageKey: 'spd-purchase-plan-cols-v1',
  defaults: PURCHASEPLAN_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const purchasePlanTableRef = purchasePlanCols.tableRef
const isPurchasePlanColVisible = (key: string) => purchasePlanCols.isColumnVisible(key)
const purchasePlanColBinding = (key: string) => purchasePlanCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const purchasePlanActionFix = useActionColumnFix()
const purchasePlanActionFixed = computed(() => purchasePlanActionFix.actionFixedBinding.value)

watch(
  [purchasePlanTableRef],
  () => {
    purchasePlanActionFix.observe(purchasePlanTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const purchasePlanColSettingsAnchorRef = ref<HTMLElement | null>(null)
const purchasePlanColSettingsPopRef = ref<HTMLElement | null>(null)
const purchasePlanColSettingsPop = useColSettingsPopover({
  open: purchasePlanCols.settingsOpen,
  anchorRef: purchasePlanColSettingsAnchorRef,
  popRef: purchasePlanColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const PURCHASEDEMAND_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'selection', label: '选择', width: 70, visible: true, weight: 1, minWidth: 56, maxWidth: 90 },
  { key: 'demandNo', label: '需求编号', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'source', label: '来源', width: 110, visible: true, weight: 2, minWidth: 80, maxWidth: 150 },
  { key: 'deptName', label: '科室', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'productSummary', label: '商品明细', width: 220, visible: true, weight: 5, minWidth: 140, maxWidth: 320 },
  { key: 'requiredQty', label: '需求总数', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'suggestedQty', label: '建议采购量', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'amount', label: '金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'urgency', label: '紧急程度', width: 100, visible: true, weight: 2, minWidth: 80, maxWidth: 140 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'createTime', label: '创建时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'actions', label: '操作', width: 170, visible: true, fill: true, minWidth: 140, maxWidth: 300 }
]

const purchaseDemandCols = useListColumns({
  storageKey: 'spd-purchase-demand-cols-v1',
  defaults: PURCHASEDEMAND_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const purchaseDemandTableRef = purchaseDemandCols.tableRef
const isPurchaseDemandColVisible = (key: string) => purchaseDemandCols.isColumnVisible(key)
const purchaseDemandColBinding = (key: string) => purchaseDemandCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const purchaseDemandActionFix = useActionColumnFix()
const purchaseDemandActionFixed = computed(() => purchaseDemandActionFix.actionFixedBinding.value)

watch(
  [purchaseDemandTableRef],
  () => {
    purchaseDemandActionFix.observe(purchaseDemandTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const purchaseDemandColSettingsAnchorRef = ref<HTMLElement | null>(null)
const purchaseDemandColSettingsPopRef = ref<HTMLElement | null>(null)
const purchaseDemandColSettingsPop = useColSettingsPopover({
  open: purchaseDemandCols.settingsOpen,
  anchorRef: purchaseDemandColSettingsAnchorRef,
  popRef: purchaseDemandColSettingsPopRef
})

</script>

<template>
  <section class="md-page purchase-page spd-unified-list">
    <MdPageHeader
      crumb="供应链业务"
      title="采购管理"
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

      <!-- 动作条：左侧「业务动作」标题 + 分析周期，右侧动作按钮同行左右分列
           （flex space-between）。用户诉求：「智能补货分析这个按钮不要单独一行，
           和业务动作在一行，使用 flex 布局的 space-between 分别在两边对齐就好，
           很多出现这种情况的页面都要这么调整，使得整体样式统一，
           然后删掉这个边框背景」。
           基础布局由 styles/components/spd-filter-bar.css 的 .spd-action-bar 提供。 -->
      <div v-if="activeTab !== 'plans'" class="spd-action-bar" :class="`action-row--${activeTab}`">
        <div class="spd-action-bar__main">
          <div class="section-title">
            <ClipboardCheck :size="20" />
            <h3>业务动作</h3>
          </div>
          <label v-if="activeTab === 'smart'" class="inline-field">
            <span>分析周期</span>
            <select v-model.number="smartSelectedPeriod" class="inline-select md-select">
              <option v-for="period in smartPeriods" :key="period" :value="period">近{{ period }}天</option>
            </select>
          </label>
        </div>
        <div v-if="hasAsideActions" class="spd-action-bar__aside">
        <SpdButton label="智能补货分析" tone="primary" v-if="activeTab === 'smart'" @click="runSmartAnalysis">
          <template #icon><Search :size="17" /></template>
        </SpdButton>
        <SpdButton label="新增采购" tone="primary" v-if="activeTab === 'demands' && authStore.hasPermission('purchase-demand:create')" @click="resetDemandForm(); showDemandModal = true">
          <template #icon><PackagePlus :size="18" /></template>
        </SpdButton>
        <SpdButton label="需求转计划" tone="primary" v-if="activeTab === 'demands' && authStore.hasPermission('purchase-demand:convert-plan')" :disabled="selectedDemandNos.length === 0" :title="selectedDemandNos.length === 0 ? '请先勾选已审核需求' : ''" @click="generatePlans">
          <template #icon><FileCheck2 :size="17" /></template>
        </SpdButton>
        <SpdButton label="新增订单" tone="primary" v-if="activeTab === 'orders' && authStore.hasPermission('purchase-order:create')" @click="resetOrderForm(); showCreateModal = true">
          <template #icon><PackagePlus :size="18" /></template>
        </SpdButton>
        <template v-if="activeTab === 'orders'">
          <SpdButton label="提交" tone="primary" v-if="authStore.hasPermission('purchase-order:submit')" :disabled="orderActionPending || selectedOrder?.orderStatus !== 'draft'" title="仅草稿订单可提交" @click="runSelectedOrderAction('submit')">
          <template #icon><Send :size="16" /></template>
        </SpdButton>
          <SpdButton label="审批" tone="primary" v-if="authStore.hasPermission('purchase-order:approve')" :disabled="orderActionPending || selectedOrder?.orderStatus !== 'pending_approval'" title="仅待审批订单可审批" @click="runSelectedOrderAction('approve')">
          <template #icon><CheckCircle2 :size="16" /></template>
        </SpdButton>
          <SpdButton label="作废" tone="danger" v-if="authStore.hasPermission('purchase-order:void')"
           
           
            :disabled="!selectedOrder || !['draft', 'pending_approval', 'approved'].includes(selectedOrder.orderStatus)"
            @click="openOrderOperation('void')">
          <template #icon><Ban :size="16" /></template>
        </SpdButton>
          <SpdButton label="复制订单" tone="primary" v-if="authStore.hasPermission('purchase-order:create')" :disabled="!selectedOrder" @click="copySelectedOrder">
          <template #icon><Copy :size="16" /></template>
        </SpdButton>
          <SpdButton label="上传附件" tone="primary" v-if="authStore.hasPermission('purchase-order:attachment')" :disabled="!selectedOrder" @click="chooseOrderAttachment">
          <template #icon><Upload :size="16" /></template>
        </SpdButton>
          <input ref="orderAttachmentInput" type="file" hidden @change="handleOrderAttachmentUpload" />
          <SpdButton label="附件阅览" tone="primary" :disabled="!selectedOrder" @click="openOrderAttachments">
          <template #icon><Paperclip :size="16" /></template>
        </SpdButton>
          <SpdButton label="添加备注" tone="primary" v-if="authStore.hasPermission('purchase-order:remark')" :disabled="!selectedOrder" @click="openOrderOperation('remark')">
          <template #icon><MessageSquare :size="16" /></template>
        </SpdButton>
        </template>
        <SpdButton label="清空跟踪" tone="primary" v-if="activeTab === 'tracking' && detail" @click="detail = null">
          <template #icon><X :size="17" /></template>
        </SpdButton>
        </div>
      </div>

      <SpdFilterBar v-if="activeTab !== 'smart'" class="purchase-query-grid" @submit="loadData" @reset="resetQuery">
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
        <template #actions>
          <SpdButton label="重置" tone="neutral" @click="resetQuery">
            <template #icon><X :size="16" /></template>
          </SpdButton>
          <SpdButton :label="actionQueryLabel" tone="primary" @click="loadData">
            <template #icon><Search :size="16" /></template>
          </SpdButton>
        </template>
      </SpdFilterBar>

            <MdDataTableCard v-if="activeTab === 'demands'">
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="purchaseDemandColSettingsAnchorRef"
           
           
            @click="purchaseDemandCols.settingsOpen.value = !purchaseDemandCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="purchaseDemandCols.settingsOpen.value"
ref="purchaseDemandColSettingsPopRef"
:class="{ 'is-align-right': purchaseDemandColSettingsPop.align.value === 'right', 'is-align-up': purchaseDemandColSettingsPop.vertical.value === 'up' }"
:style="purchaseDemandColSettingsPop.popStyle.value"
:columns="purchaseDemandCols.columns.value"
:visible-column-count="purchaseDemandCols.visibleColumnCount.value"
@toggle="purchaseDemandCols.setColumnVisible"
@move="purchaseDemandCols.moveColumn"
@reset="purchaseDemandCols.resetColumns()"
@confirm="purchaseDemandCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="purchaseDemandTableRef"
            :data="demandGroups"
            row-key="demandNo"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无需求单'"
            @header-dragend="(width: number, oldWidth: number, column: any) => purchaseDemandCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="selection"
              v-if="isPurchaseDemandColVisible('selection')"
              label="选择"
              v-bind="purchaseDemandColBinding('selection')"
            >
              <template #header>
                <span class="ulc-th-text">选择</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text"><input v-model="selectedDemandNos" type="checkbox" :value="row.demandNo" :disabled="row.demandStatus !== 'approved'" :aria-label="`选择需求 ${row.demandNo}`" /></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="demandNo"
              v-if="isPurchaseDemandColVisible('demandNo')"
              label="需求编号"
              v-bind="purchaseDemandColBinding('demandNo')"
            >
              <template #header>
                <span class="ulc-th-text">需求编号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-joined">
                  <span class="ulc-joined-part">{{ row.demandNo }}</span>
                  <span class="ulc-joined-part">{{ row.itemCount }}项</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="source"
              v-if="isPurchaseDemandColVisible('source')"
              label="来源"
              v-bind="purchaseDemandColBinding('source')"
            >
              <template #header>
                <span class="ulc-th-text">来源</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatBusinessText(row.demandSource) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="deptName"
              v-if="isPurchaseDemandColVisible('deptName')"
              label="科室"
              v-bind="purchaseDemandColBinding('deptName')"
            >
              <template #header>
                <span class="ulc-th-text">科室</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.deptName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="productSummary"
              v-if="isPurchaseDemandColVisible('productSummary')"
              label="商品明细"
              v-bind="purchaseDemandColBinding('productSummary')"
            >
              <template #header>
                <span class="ulc-th-text">商品明细</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-cell-stack">
                  <span class="item-summary">共 {{ row.itemCount }} 种商品</span>
                  <SpdButton variant="link" label="查看明细" @click="openDemandDetail(row)" />
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="requiredQty"
              v-if="isPurchaseDemandColVisible('requiredQty')"
              label="需求总数"
              v-bind="purchaseDemandColBinding('requiredQty')"
            >
              <template #header>
                <span class="ulc-th-text">需求总数</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.totalQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="suggestedQty"
              v-if="isPurchaseDemandColVisible('suggestedQty')"
              label="建议采购量"
              v-bind="purchaseDemandColBinding('suggestedQty')"
            >
              <template #header>
                <span class="ulc-th-text">建议采购量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.totalSuggestedQty }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="amount"
              v-if="isPurchaseDemandColVisible('amount')"
              label="金额"
              v-bind="purchaseDemandColBinding('amount')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">金额</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">¥ {{ row.totalAmount.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="urgency"
              v-if="isPurchaseDemandColVisible('urgency')"
              label="紧急程度"
              v-bind="purchaseDemandColBinding('urgency')"
            >
              <template #header>
                <span class="ulc-th-text">紧急程度</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatBusinessText(row.urgentLevel) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isPurchaseDemandColVisible('status')"
              label="状态"
              v-bind="purchaseDemandColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip :tone="statusTone(row.demandStatus)" :label="statusLabel(row.demandStatus)" />
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isPurchaseDemandColVisible('createTime')"
              label="创建时间"
              v-bind="purchaseDemandColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isPurchaseDemandColVisible('actions')"
              label="操作"
              v-bind="purchaseDemandColBinding('actions')"
              :fixed="purchaseDemandActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button v-if="row.demandStatus === 'draft' && authStore.hasPermission('purchase-demand:submit')" @click="runDemandAction(row, 'submit')" class="ulc-action" type="button">提交</button>
                <button v-if="row.demandStatus === 'pending_review' && authStore.hasPermission('purchase-demand:review')" @click="runDemandAction(row, 'approve')" class="ulc-action" type="button">审核通过</button>
                <button v-if="row.demandStatus === 'pending_review' && authStore.hasPermission('purchase-demand:reject')" @click="runDemandAction(row, 'reject')" class="ulc-action" type="button">驳回</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无需求单" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="purchasePagination.demands.page"
            :total-pages="Math.max(1, Math.ceil(purchasePagination.demands.total / Math.max(purchasePagination.demands.size, 1)))"
            :total="purchasePagination.demands.total"
            :size="purchasePagination.demands.size"
            :loading="loading"
            @update:page="changePurchasePage('demands', $event)"
            @update:size="changePurchasePageSize('demands', $event)"
          />
        </template>
      </MdDataTableCard>

            <MdDataTableCard v-if="activeTab === 'plans'">
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="purchasePlanColSettingsAnchorRef"
           
           
            @click="purchasePlanCols.settingsOpen.value = !purchasePlanCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="purchasePlanCols.settingsOpen.value"
ref="purchasePlanColSettingsPopRef"
:class="{ 'is-align-right': purchasePlanColSettingsPop.align.value === 'right', 'is-align-up': purchasePlanColSettingsPop.vertical.value === 'up' }"
:style="purchasePlanColSettingsPop.popStyle.value"
:columns="purchasePlanCols.columns.value"
:visible-column-count="purchasePlanCols.visibleColumnCount.value"
@toggle="purchasePlanCols.setColumnVisible"
@move="purchasePlanCols.moveColumn"
@reset="purchasePlanCols.resetColumns()"
@confirm="purchasePlanCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="purchasePlanTableRef"
            :data="plans"
            row-key="planNo"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无采购计划'"
            @header-dragend="(width: number, oldWidth: number, column: any) => purchasePlanCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="planNo"
              v-if="isPurchasePlanColVisible('planNo')"
              label="计划编号"
              v-bind="purchasePlanColBinding('planNo')"
            >
              <template #header>
                <span class="ulc-th-text">计划编号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.planNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="detailCount"
              v-if="isPurchasePlanColVisible('detailCount')"
              label="商品明细数"
              v-bind="purchasePlanColBinding('detailCount')"
            >
              <template #header>
                <span class="ulc-th-text">商品明细数</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.itemCount }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="orderCount"
              v-if="isPurchasePlanColVisible('orderCount')"
              label="已生成订单数"
              v-bind="purchasePlanColBinding('orderCount')"
            >
              <template #header>
                <span class="ulc-th-text">已生成订单数</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.orderCount }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="amount"
              v-if="isPurchasePlanColVisible('amount')"
              label="金额"
              v-bind="purchasePlanColBinding('amount')"
            >
              <template #header>
                <span class="ulc-th-text">金额</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.amount == null ? '历史未记录' : `¥ ${Number(row.amount).toFixed(2)}` }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="supplierCount"
              v-if="isPurchasePlanColVisible('supplierCount')"
              label="配送商数"
              v-bind="purchasePlanColBinding('supplierCount')"
            >
              <template #header>
                <span class="ulc-th-text">配送商数</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.supplierCount }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="sourceDept"
              v-if="isPurchasePlanColVisible('sourceDept')"
              label="发起科室"
              v-bind="purchasePlanColBinding('sourceDept')"
            >
              <template #header>
                <span class="ulc-th-text">发起科室</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.initiatingDeptName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isPurchasePlanColVisible('status')"
              label="状态"
              v-bind="purchasePlanColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip :tone="statusTone(row.planStatus)" :label="statusLabel(row.planStatus)" />
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isPurchasePlanColVisible('createTime')"
              label="创建时间"
              v-bind="purchasePlanColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isPurchasePlanColVisible('actions')"
              label="操作"
              v-bind="purchasePlanColBinding('actions')"
              :fixed="purchasePlanActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button @click="planDetailNo = row.planNo" class="ulc-action" type="button">明细/配送商</button>
                <button v-if="row.planStatus === 'draft' && authStore.hasPermission('purchase-plan:approve')" @click="runPlanAction(row, 'approve')" class="ulc-action" type="button">计划审核</button>
                <button v-if="row.planStatus === 'approved' && authStore.hasPermission('purchase-plan:execute')" @click="runPlanAction(row, 'execute')" class="ulc-action" type="button">转订单</button>
                <button v-if="row.planStatus === 'draft' && authStore.hasPermission('purchase-plan:reject')" @click="runPlanAction(row, 'reject')" class="ulc-action" type="button">驳回</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无采购计划" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="purchasePagination.plans.page"
            :total-pages="Math.max(1, Math.ceil(purchasePagination.plans.total / Math.max(purchasePagination.plans.size, 1)))"
            :total="purchasePagination.plans.total"
            :size="purchasePagination.plans.size"
            :loading="loading"
            @update:page="changePurchasePage('plans', $event)"
            @update:size="changePurchasePageSize('plans', $event)"
          />
        </template>
      </MdDataTableCard>

            <MdDataTableCard v-if="activeTab === 'orders'">
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="purchaseOrderColSettingsAnchorRef"
           
           
            @click="purchaseOrderCols.settingsOpen.value = !purchaseOrderCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="purchaseOrderCols.settingsOpen.value"
ref="purchaseOrderColSettingsPopRef"
:class="{ 'is-align-right': purchaseOrderColSettingsPop.align.value === 'right', 'is-align-up': purchaseOrderColSettingsPop.vertical.value === 'up' }"
:style="purchaseOrderColSettingsPop.popStyle.value"
:columns="purchaseOrderCols.columns.value"
:visible-column-count="purchaseOrderCols.visibleColumnCount.value"
@toggle="purchaseOrderCols.setColumnVisible"
@move="purchaseOrderCols.moveColumn"
@reset="purchaseOrderCols.resetColumns()"
@confirm="purchaseOrderCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="purchaseOrderTableRef"
            :data="rows"
            row-key="orderNo"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无采购订单'"
            @header-dragend="(width: number, oldWidth: number, column: any) => purchaseOrderCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="selection"
              v-if="isPurchaseOrderColVisible('selection')"
              label="选择"
              v-bind="purchaseOrderColBinding('selection')"
            >
              <template #header>
                <span class="ulc-th-text">选择</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text"><input
                  type="radio"
                  name="purchase-order-selection"
                  :checked="selectedOrder?.orderNo === row.orderNo"
                  :aria-label="`选择订单 ${row.orderNo}`"
                  @change="selectOrder(row)"
                /></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="orderNo"
              v-if="isPurchaseOrderColVisible('orderNo')"
              label="订单编号"
              v-bind="purchaseOrderColBinding('orderNo')"
            >
              <template #header>
                <span class="ulc-th-text">订单编号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.orderNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="supplierName"
              v-if="isPurchaseOrderColVisible('supplierName')"
              label="供应商"
              v-bind="purchaseOrderColBinding('supplierName')"
            >
              <template #header>
                <span class="ulc-th-text">供应商</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.supplierName }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="source"
              v-if="isPurchaseOrderColVisible('source')"
              label="来源"
              v-bind="purchaseOrderColBinding('source')"
            >
              <template #header>
                <span class="ulc-th-text">来源</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatBusinessText(row.orderSource) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="detailCount"
              v-if="isPurchaseOrderColVisible('detailCount')"
              label="明细数"
              v-bind="purchaseOrderColBinding('detailCount')"
            >
              <template #header>
                <span class="ulc-th-text">明细数</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.itemCount }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="purchaseQty"
              v-if="isPurchaseOrderColVisible('purchaseQty')"
              label="采购数量"
              v-bind="purchaseOrderColBinding('purchaseQty')"
            >
              <template #header>
                <span class="ulc-th-text">采购数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.orderQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="fulfillProgress"
              v-if="isPurchaseOrderColVisible('fulfillProgress')"
              label="履约进度"
              v-bind="purchaseOrderColBinding('fulfillProgress')"
            >
              <template #header>
                <span class="ulc-th-text">履约进度</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-cell-stack fulfillment-cell">
                  <span>{{ row.receivedQuantity }} / {{ row.orderQuantity }}</span>
                  <i>
                    <b :style="{ width: `${Math.min(100, Math.round((Number(row.receivedQuantity || 0) / Math.max(1, Number(row.orderQuantity || 0))) * 100))}%` }" />
                  </i>
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="remainingQty"
              v-if="isPurchaseOrderColVisible('remainingQty')"
              label="剩余数量"
              v-bind="purchaseOrderColBinding('remainingQty')"
            >
              <template #header>
                <span class="ulc-th-text">剩余数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">
                  <span :class="['quantity-gap', remainingQty(row) > 0 ? 'pending' : 'done']">
                    {{ remainingQty(row) }}
                  </span>
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="totalAmount"
              v-if="isPurchaseOrderColVisible('totalAmount')"
              label="总金额"
              v-bind="purchaseOrderColBinding('totalAmount')"
            >
              <template #header>
                <span class="ulc-th-text">总金额</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">¥ {{ Number(row.totalAmount).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="expectedArrival"
              v-if="isPurchaseOrderColVisible('expectedArrival')"
              label="预计到货"
              v-bind="purchaseOrderColBinding('expectedArrival')"
            >
              <template #header>
                <span class="ulc-th-text">预计到货</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.expectedArrivalDate || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isPurchaseOrderColVisible('createTime')"
              label="创建时间"
              v-bind="purchaseOrderColBinding('createTime')"
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
              v-if="isPurchaseOrderColVisible('updateTime')"
              label="修改时间"
              v-bind="purchaseOrderColBinding('updateTime')"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.updateTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isPurchaseOrderColVisible('status')"
              label="状态"
              v-bind="purchaseOrderColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip :tone="statusTone(row.orderStatus)" :label="statusLabel(row.orderStatus)" />
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isPurchaseOrderColVisible('actions')"
              label="操作"
              v-bind="purchaseOrderColBinding('actions')"
              :fixed="purchaseOrderActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button @click="openDetail(row)" class="ulc-action" type="button"><Eye :size="15" /> 查看</button>
                <button @click="openTracking(row)" class="ulc-action" type="button"><List :size="15" /> 跟踪</button>
                <button v-if="row.orderStatus === 'approved' && authStore.hasPermission('purchase-order:send')" :disabled="orderActionPending" @click="runOrderAction(row, 'send')" class="ulc-action" type="button"><Send :size="15" /> 发送</button>
                <button v-if="['approved', 'sent'].includes(row.orderStatus) && authStore.hasPermission('purchase-order:close')"
                  :class="{ 'btn-text-muted': !canCloseOrder(row) }"
                  :title="canCloseOrder(row) ? '填写关闭原因后关闭订单' : '需先完成收货验收'"
                  @click="requestClose(row)" class="ulc-action" type="button">{{ canCloseOrder(row) ? '关闭' : '待收货' }}</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无采购订单" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="purchasePagination.orders.page"
            :total-pages="Math.max(1, Math.ceil(purchasePagination.orders.total / Math.max(purchasePagination.orders.size, 1)))"
            :total="purchasePagination.orders.total"
            :size="purchasePagination.orders.size"
            :loading="loading"
            @update:page="changePurchasePage('orders', $event)"
            @update:size="changePurchasePageSize('orders', $event)"
          />
        </template>
      </MdDataTableCard>

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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" :disabled="demandSubmitting" @click="showSmartAnalysisDialog = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
          <label class="inline-field">
            <span>发起科室</span>
            <select v-model="smartDeptCode" class="md-select" :disabled="demandSubmitting">
              <option value="">{{ departments.length ? '请选择授权范围内科室' : '当前账号暂无可选科室' }}</option>
              <option v-for="dept in departments" :key="dept.deptCode" :value="dept.deptCode">{{ dept.deptName }}</option>
            </select>
          </label>
          <SpdButton label="关闭" tone="neutral" :disabled="demandSubmitting" @click="showSmartAnalysisDialog = false"></SpdButton>
          <SpdButton label="生成采购需求" tone="primary" :disabled="smartAnalysisLoading || demandSubmitting || !smartDeptCode || smartRows.length === 0" @click="createDemandFromSmartAnalysis">
          <template #icon><PackagePlus :size="18" /></template>
        </SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" @click="showDemandDetailDialog = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
              <td>¥ {{ Number(item.lineAmount ?? 0).toFixed(2) }}</td>
              <td>{{ item.registrationNo || '-' }}</td>
              <td>{{ item.manufacturerName || '-' }}</td>
            </tr>
          </tbody>
        </table>
        <footer>
          <SpdButton label="关闭" tone="neutral" @click="showDemandDetailDialog = false"></SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭订单明细" @click="showOrderDetailDialog = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
        </header>
        <div class="order-detail-body">
          <section class="purchase-detail-summary order-detail-summary">
            <span>供应商：<strong>{{ detail.order.supplierName }}</strong></span>
            <span>状态：<strong>{{ statusLabel(detail.order.orderStatus) }}</strong></span>
            <span>订单金额：<strong>¥ {{ Number(detail.order.totalAmount || 0).toFixed(2) }}</strong></span>
            <span>创建时间：<strong>{{ detail.order.createTime || '-' }}</strong></span>
            <span>修改时间：<strong>{{ detail.order.updateTime || '-' }}</strong></span>
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
            <SpdButton label="关闭" tone="neutral" @click="showOrderDetailDialog = false"></SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭" @click="orderOperationMode = null">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
          <SpdButton label="取消" tone="neutral" @click="orderOperationMode = null"></SpdButton>
          <SpdButton tone="primary" :class="{ 'btn-danger-soft': orderOperationMode === 'void', 'btn-primary': orderOperationMode === 'remark' }" @click="submitOrderOperation">
          {{ orderOperationMode === 'void' ? '确认作废' : '保存备注' }}
        </SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭附件阅览" @click="closeOrderAttachmentDialog">
          <template #icon><X :size="18" /></template>
        </SpdButton>
        </header>
        <div class="order-attachment-body">
          <aside class="order-attachment-list">
            <SpdButton label="上传附件" tone="primary" @click="chooseOrderAttachment">
          <template #icon><Upload :size="15" /></template>
        </SpdButton>
            <p v-if="orderAttachmentsLoading" class="approval-empty">正在加载附件...</p>
            <p v-else-if="orderAttachments.length === 0" class="approval-empty">暂无订单附件</p>
            <ul v-else>
              <li v-for="attachment in orderAttachments" :key="attachment.id">
                <div>
                  <strong>{{ attachment.fileName }}</strong>
                  <small>{{ attachment.createTime }} · {{ (attachment.size / 1024).toFixed(1) }} KB</small>
                </div>
                <SpdButton label="阅览" @click="previewOrderAttachment(attachment)">
                  <template #icon><Eye :size="14" /></template>
                </SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭新增采购" @click="showDemandModal = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
              <SpdButton label="新增" tone="primary" @click="openProductPicker('hospital')">
          <template #icon><Plus :size="17" /></template>
        </SpdButton>
              <SpdButton label="科室目录新增" tone="primary" @click="openProductPicker('department')">
          <template #icon><List :size="17" /></template>
        </SpdButton>
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
            <SpdButton label="取消" tone="neutral" @click="showDemandModal = false"></SpdButton>
            <SpdButton tone="primary" :disabled="demandSubmitting" @click="submitDemand(true)">
          <template #icon><Plus :size="16" /></template>
          {{ demandSubmitting ? '保存中…' : '保存并新建' }}
        </SpdButton>
            <SpdButton tone="primary" :disabled="demandSubmitting" @click="submitDemand(false)">
          <template #icon><Save :size="17" /></template>
          {{ demandSubmitting ? '保存中…' : '保存并关闭' }}
        </SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" aria-label="关闭商品选择" @click="showProductPicker = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
        </header>
        <p v-if="pickerScope === 'department'" class="purchase-picker-dept">发起科室：<strong>{{ demandForm.deptName }}</strong></p>
        <form class="purchase-entry-toolbar" @submit.prevent="loadPickerProducts()">
          <label class="purchase-entry-search"><span>商品检索</span><input v-model="pickerKeyword" aria-label="选择商品检索" placeholder="商品编码、名称或规格" /></label>
          <SpdButton label="查询" tone="primary" :disabled="pickerLoading"></SpdButton>
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
        <MdPagination
          :page="pickerPagination.page"
          :total-pages="Math.max(1, Math.ceil(pickerPagination.total / Math.max(pickerPagination.size, 1)))"
          :total="pickerPagination.total"
          :size="pickerPagination.size"
          :loading="pickerLoading"
          @update:page="loadPickerProducts"
          @update:size="pickerPagination.size = $event; loadPickerProducts()"
        />
        <footer>
          <span>已选 {{ pickerSelected.size }} 项（支持跨页多选）</span>
          <div class="purchase-picker-actions">
            <SpdButton label="取消" tone="neutral" @click="showProductPicker = false"></SpdButton>
            <SpdButton label="加入采购明细" tone="primary" :disabled="!pickerSelected.size || pickerLoading || !!pickerError" @click="addSelectedProducts"></SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" aria-label="关闭新增采购订单" @click="showCreateModal = false">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
              <SpdButton label="添加商品" tone="primary" :disabled="filteredProducts.length === 0" @click="addItem">
          <template #icon><Plus :size="14" /></template>
        </SpdButton>
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
                    <SpdButton label="删除" :disabled="form.items.length === 1" @click="removeItem(index)">
                      <template #icon><Trash2 :size="14" /></template>
                    </SpdButton>
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
            <SpdButton label="取消" tone="neutral" @click="showCreateModal = false"></SpdButton>
            <SpdButton tone="primary" :disabled="orderSubmitting" @click="submitCreate">
          <template #icon><Save :size="15" /></template>
          {{ orderSubmitting ? '保存中…' : '保存采购订单' }}
        </SpdButton>
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" @click="closeTarget = null">
          <template #icon><X :size="18" /></template>
        </SpdButton>
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
          <SpdButton label="取消" tone="neutral" @click="closeTarget = null"></SpdButton>
          <SpdButton label="确认关闭" tone="primary" :disabled="!closeReason.trim()" @click="submitClose"></SpdButton>
        </footer>
      </section>
    </div>
    </Teleport>
  </section>
  <PurchasePlanDetailDialog :plan-no="planDetailNo" @close="planDetailNo = ''" @updated="loadData" />
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

/* ---------- 数字列：居中 + 等宽数字 ----------
   与统一列表标准一致：金额 / 数量类单元格与普通列一样**居中**，
   数字使用等宽字体便于纵向比对。
   具体样式统一由 styles/list/unified-list.css 的 .ulc-number 提供，
   这里不再重复定义，避免各页面出现不同的对齐口径。 */

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
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  border-radius: var(--md-radius, 6px);
}

/* ---------- 动作条 ----------
   用户诉求：「智能补货分析这个按钮不要单独一行，和业务动作在一行，
   使用 flex 布局的 space-between 分别在两边对齐就好，
   很多出现这种情况的页面都要这么调整，使得整体样式统一，
   然后删掉这个边框背景」。

   本页动作区不再使用带边框 / 背景的 .hospital-action-row.purchase-toolbar，
   改用全站统一的 .spd-action-bar（flex + space-between + align-items:center）。
   基础布局由 styles/components/spd-filter-bar.css 提供，这里只做本页微调。 */
.purchase-page .spd-action-bar {
  margin-bottom: 12px;
}

.purchase-page .spd-action-bar .section-title {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: 0;
}

.purchase-page .spd-action-bar .inline-select {
  min-width: 140px;
}

.purchase-query-actions {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  min-height: 50px;
  padding-bottom: 2px;
}

.purchase-product-picker tr.is-picked {
  background: var(--spd-success-bg, var(--spd-p-primary-bg));
}

.purchase-product-picker tr.is-picked td {
  color: var(--spd-text-secondary, var(--spd-p-text-secondary));
}

.purchase-product-picker .picker-checkbox {
  cursor: pointer;
}

.purchase-product-picker .picker-checkbox:checked:disabled,
.purchase-product-picker .picker-checkbox:disabled:checked {
  accent-color: var(--spd-success, var(--spd-p-success));
  cursor: not-allowed;
}

.purchase-product-picker-mask .master-table {
  font-size: var(--spd-font-size-base) !important;
  line-height: var(--spd-lh-base);
}

.purchase-product-picker-mask .master-table thead th {
  font-size: var(--spd-font-size-table-head) !important;
  padding: 10px 12px !important;
}

.purchase-product-picker-mask .master-table tbody td {
  font-size: var(--spd-font-size-base) !important;
  padding: 10px 12px !important;
  line-height: var(--spd-lh-base);
}

.purchase-query-actions .btn {
  min-height: 30px;
  padding: 0 14px;
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-sm);
}

.inline-select {
  min-height: 30px;
  font-size: var(--spd-font-size-base);
}

.inline-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

.action-hint {
  color: var(--spd-text-tertiary);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
  margin-left: auto;
}

.action-row--plans .inline-select,
.action-row--orders .inline-select,
.action-row--tracking .inline-select {
  display: none;
}

/* ---------- 查询区 ----------
   筛选区已统一改用 SpdFilterBar 组件，
   栅格列数 / 换行规则 / 控件高度 / 操作区位置全部由
   styles/components/spd-filter-bar.css 的「标准规则」统一控制，
   本页不再覆写列数与间距（避免各页各写一套换行规则）。 */

/* ---------- 列表滚动容器 ---------- */
.purchase-list-scroll {
  max-height: max(300px, calc(100dvh - 420px));
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  background: var(--md-bg-card, var(--spd-p-bg));
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
  font-size: var(--spd-font-size-base) !important;
  line-height: var(--spd-lh-base);
}

.purchase-table thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  padding: 10px 12px !important;
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 700;
  letter-spacing: 0.2px;
  white-space: nowrap;
  color: var(--md-text-heading) !important;
  background: linear-gradient(180deg, var(--spd-p-bg-subtle) 0%, var(--spd-p-success-bg) 100%) !important;
  border-bottom: 1px solid var(--md-border, var(--spd-border-default)) !important;
}

.purchase-table tbody td {
  padding: 10px 12px !important;
  font-size: var(--spd-font-size-base) !important;
  line-height: var(--spd-lh-base);
  color: var(--md-text) !important;
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  vertical-align: middle;
}

.purchase-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted, var(--spd-p-row-stripe));
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
  font-size: var(--spd-font-size-sm);
  margin-top: 1px;
}

/* ---------- 单据号 / 金额 ---------- */
.document-no-cell {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base) !important;
  font-weight: 600;
  color: var(--md-text-heading) !important;
  white-space: nowrap;
}

.purchase-plan-amount {
  font-family: var(--spd-font-family-mono);
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

/* 【已移除】.purchase-table .status-badge 局部样式（20px 高 / 999px 圆角）。
   列表状态条统一改用 <SpdStatusChip>（styles/components/spd-status-chip.css），
   全站共享同一套高度 / 内边距 / 圆角 / 浅底深字配色。 */

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
  font-size: var(--spd-font-size-base);
}

/* ---------- 履约进度 ---------- */
.fulfillment-cell {
  display: grid;
  gap: 3px;
  min-width: 88px;
}

.fulfillment-cell span {
  font-variant-numeric: tabular-nums;
  font-size: var(--spd-font-size-sm);
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
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.quantity-gap.pending {
  color: var(--spd-warning-text, var(--spd-p-warning-text));
  background: var(--spd-warning-bg, var(--spd-p-warning-bg));
}

.quantity-gap.done {
  color: var(--spd-success-text, var(--spd-p-success-text));
  background: var(--spd-success-bg, var(--spd-p-success-bg));
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
  font-size: var(--spd-font-size-xs);
  font-weight: 600;
}

.item-summary {
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: var(--spd-font-size-sm);
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
  font-size: var(--spd-font-size-base);
  text-align: center;
}

.btn-danger-soft {
  border-color: var(--spd-danger-border) !important;
  background: var(--spd-danger-bg) !important;
  color: var(--spd-danger-text) !important;
}

.btn-danger-soft:hover:not(:disabled) {
  border-color: var(--spd-danger) !important;
  background: color-mix(in srgb, var(--spd-danger) 12%, var(--spd-p-bg)) !important;
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
  background: var(--spd-p-bg-page);
}

.order-detail-summary {
  margin-bottom: 14px;
  border: 1px solid var(--spd-p-border);
  border-radius: 7px;
  padding: 12px 14px;
  background: var(--spd-p-bg);
}

.order-detail-summary strong {
  color: var(--spd-p-success-text);
}

.order-detail-table-wrap {
  border: 1px solid var(--spd-p-border);
  border-radius: 7px;
  background: var(--spd-p-bg);
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
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-sm);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 7px;
  padding: 12px;
  background: var(--spd-p-bg-page);
}

.order-operation-order span,
.order-operation-body label > span {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.order-operation-body textarea {
  resize: vertical;
  border: 1px solid var(--spd-p-border);
  border-radius: 7px;
  padding: 10px;
  color: var(--spd-p-text);
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
  background: var(--spd-p-bg-page);
}

.order-attachment-list,
.order-attachment-preview {
  min-height: 0;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
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
  border-bottom: 1px solid var(--spd-p-bg-muted);
  padding: 10px 2px;
}

.order-attachment-list li > div {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.order-attachment-list strong {
  overflow: hidden;
  color: var(--spd-p-text);
  font-size: var(--spd-font-size-base);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-attachment-list small {
  color: var(--spd-p-text-muted);
}

.order-attachment-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--spd-p-text-strong);
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
  border-bottom: 1px solid var(--spd-p-text);
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
  color: var(--spd-p-text-secondary);
}

.fulfillment-cell i {
  display: block;
  width: 100%;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--spd-p-bg-muted);
}

.fulfillment-cell b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--spd-p-primary);
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
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
}

.quantity-gap.done {
  color: var(--spd-p-primary);
  background: var(--spd-p-primary-bg);
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
  color: var(--spd-p-text-secondary);
}

.timeline-list {
  display: grid;
  gap: 10px;
}

.timeline-list article {
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--spd-p-bg);
}

.timeline-list span,
.timeline-list small {
  display: block;
  color: var(--spd-p-text-secondary);
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: color-mix(in srgb, var(--spd-p-text) 35%, transparent);
}

.edit-modal {
  width: min(720px, 100%);
  max-height: 88vh;
  overflow: auto;
  border-radius: 8px;
  padding: 22px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-dialog);
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
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
  color: var(--spd-p-text);
  line-height: var(--spd-lh-tight);
}

.purchase-product-picker-mask header .btn-icon {
  margin-left: auto;
}

.purchase-picker-dept {
  margin: 0 0 10px;
  padding: 7px 12px;
  border-radius: 6px;
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
  flex-shrink: 0;
}

.purchase-picker-dept strong {
  color: var(--spd-p-primary);
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
  border: 1px solid var(--spd-p-border);
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
  border-top: 1px solid var(--spd-p-info-bg);
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
  border-bottom: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
}

.purchase-entry-header p,
.purchase-entry-header h3 {
  margin: 0;
}

.purchase-entry-header p {
  margin-bottom: 3px;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.purchase-entry-header h3 {
  color: var(--spd-p-success-text);
  font-size: var(--spd-font-size-2xl);
}

.purchase-entry-body {
  min-height: 0;
  overflow: auto;
  padding: 18px 22px;
  background: var(--spd-p-bg-page);
}

.purchase-entry-section {
  margin: 0 0 16px;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  padding: 16px;
  background: var(--spd-p-bg);
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
  color: var(--spd-p-text-strong);
  font-size: var(--spd-font-size-lg);
}

.purchase-entry-section-heading small {
  margin-top: 4px;
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-sm);
}

.purchase-entry-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 14px;
}

.purchase-entry-meta-grid label {
  display: grid;
  gap: 6px;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

.purchase-entry-meta-grid em {
  color: var(--spd-p-danger);
  font-style: normal;
}

.purchase-entry-meta-grid input,
.purchase-entry-meta-grid select,
.purchase-entry-table input,
.purchase-entry-table select {
  min-height: 38px;
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  padding: 0 10px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text-secondary);
  font: inherit;
}

.purchase-entry-meta-grid input:focus,
.purchase-entry-meta-grid select:focus,
.purchase-entry-table input:focus,
.purchase-entry-table select:focus,
.purchase-entry-search:focus-within {
  border-color: var(--spd-p-primary);
  outline: 2px solid color-mix(in srgb, var(--spd-p-primary) 12%, transparent);
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
  border: 1px solid var(--spd-p-primary-border);
  border-radius: 999px;
  padding: 5px 10px;
  background: var(--spd-p-primary-bg);
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-base);
}

.purchase-entry-summary strong {
  color: var(--spd-p-primary);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  padding: 0 10px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text-muted);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 7px;
}

.purchase-entry-table {
  min-width: 1080px;
}

.purchase-entry-table th {
  white-space: nowrap;
  background: var(--spd-p-bg-subtle);
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
  color: var(--spd-p-text-secondary);
  font-family: var(--spd-font-family-mono);
}

.purchase-product-cell {
  min-width: 270px;
}

.purchase-quantity-cell {
  width: 110px;
}

.purchase-amount-cell {
  color: var(--spd-p-primary);
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
  color: var(--spd-p-danger);
  cursor: pointer;
  font: inherit;
}

.purchase-row-delete:disabled {
  color: var(--spd-p-text-secondary);
  cursor: not-allowed;
}

.purchase-entry-footer {
  padding: 14px 22px;
  border-top: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
}

.purchase-entry-footer > span {
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-sm);
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
  border: 1px solid var(--spd-p-border);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--spd-p-bg-muted);
}

.close-check-panel span,
.close-check-panel strong {
  display: block;
}

.close-check-panel span {
  color: var(--spd-p-text-secondary);
}

.close-reason-field {
  display: grid;
  gap: 8px;
  margin-bottom: 18px;
}

.close-reason-field textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid var(--spd-p-border);
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
  background: var(--spd-p-bg-page);
  box-shadow: var(--spd-p-shadow-dialog);
}

/* ---------- 头部 ---------- */
.purchase-create-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid var(--spd-p-border);
  background: linear-gradient(180deg, var(--spd-p-bg) 0%, var(--spd-p-bg-muted) 100%);
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
  background: linear-gradient(180deg, var(--spd-p-primary) 0%, var(--spd-p-primary-strong) 100%);
  color: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
}

.purchase-create-header p {
  margin: 0 0 2px;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.purchase-create-header h3 {
  margin: 0;
  color: var(--spd-p-success-text);
  font-size: var(--spd-font-size-xl);
  font-weight: 700;
}

/* ---------- 顶部 4 张概要卡 ---------- */
.purchase-create-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
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
  font-size: var(--spd-font-size-sm);
  opacity: 0.85;
  margin-bottom: 2px;
}

.purchase-create-overview-card strong {
  display: block;
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
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
  background: color-mix(in srgb, var(--spd-p-bg) 70%, transparent);
  flex-shrink: 0;
  border: 1px solid color-mix(in srgb, var(--spd-p-bg) 90%, transparent);
}

.purchase-create-overview-card.tone-blue   { background: linear-gradient(180deg, var(--spd-p-border) 0%, var(--spd-p-info-border) 100%); border-color: var(--spd-p-info-border); color: var(--spd-p-info-strong); }
.purchase-create-overview-card.tone-green  { background: linear-gradient(180deg, var(--spd-p-success-bg) 0%, var(--spd-p-success-border) 100%); border-color: var(--spd-p-success); color: var(--spd-p-primary-strong); }
.purchase-create-overview-card.tone-amber  { background: linear-gradient(180deg, var(--spd-p-warning-bg) 0%, var(--spd-p-warning-border) 100%); border-color: var(--spd-p-warning-border); color: var(--spd-p-warning-strong); }
.purchase-create-overview-card.tone-purple { background: linear-gradient(180deg, var(--spd-p-info-bg) 0%, var(--spd-p-info-bg) 100%); border-color: var(--spd-p-info-border); color: var(--spd-p-primary-strong); }

.purchase-create-overview-card.tone-blue   .purchase-create-overview-icon { color: var(--spd-p-info-strong); }
.purchase-create-overview-card.tone-green  .purchase-create-overview-icon { color: var(--spd-p-success-strong); }
.purchase-create-overview-card.tone-amber  .purchase-create-overview-icon { color: var(--spd-p-warning-text); }
.purchase-create-overview-card.tone-purple .purchase-create-overview-icon { color: var(--spd-p-primary); }

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
  border: 1px solid var(--spd-p-border);
  border-radius: 10px;
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
  overflow: hidden;
}

.purchase-create-panel-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
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

.purchase-create-panel-icon.is-green { background: var(--spd-p-success-bg); color: var(--spd-p-success-strong); }
.purchase-create-panel-icon.is-blue  { background: var(--spd-p-border); color: var(--spd-p-info-strong); }

.purchase-create-panel-header h4 {
  margin: 0;
  color: var(--spd-p-success-text);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
}

.purchase-create-panel-header p {
  margin: 2px 0 0;
  color: var(--spd-p-text-muted);
  font-size: var(--spd-font-size-sm);
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  color: var(--spd-p-text-secondary);
}

.purchase-create-field.is-wide { grid-column: 1 / -1; }

.purchase-create-field em {
  color: var(--spd-p-danger);
  font-style: normal;
  margin-right: 2px;
}

.purchase-create-field input,
.purchase-create-field select,
.purchase-create-field textarea {
  width: 100%;
  min-height: 36px;
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  padding: 0 10px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  font: inherit;
  font-weight: 400;
  font-size: var(--spd-font-size-base);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.purchase-create-field textarea {
  min-height: 70px;
  padding: 8px 10px;
  resize: vertical;
  line-height: var(--spd-lh-base);
}

.purchase-create-field input:focus,
.purchase-create-field select:focus,
.purchase-create-field textarea:focus {
  border-color: var(--spd-p-primary);
  outline: 2px solid color-mix(in srgb, var(--spd-p-primary) 12%, transparent);
  outline-offset: 0;
}

.purchase-create-tips {
  margin: 0 16px 16px;
  padding: 10px 12px;
  border: 1px solid var(--spd-p-info-border);
  background: var(--spd-p-info-bg);
  border-radius: 8px;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-loose);
}

.purchase-create-tips p { margin: 0 0 4px; color: var(--spd-p-info-strong); }
.purchase-create-tips strong { color: var(--spd-p-info-strong); }
.purchase-create-tips ul { margin: 0; padding-left: 18px; }
.purchase-create-tips li { margin-bottom: 2px; }

/* ---------- 右栏：采购明细 ---------- */
.purchase-create-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
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
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text-muted);
}

.purchase-create-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--spd-p-text);
  font: inherit;
  font-size: var(--spd-font-size-base);
}

.purchase-create-search:focus-within {
  border-color: var(--spd-p-primary);
  box-shadow: var(--spd-p-shadow-1);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  padding: 10px 12px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.purchase-create-item:hover {
  border-color: var(--spd-p-success);
  box-shadow: var(--spd-p-shadow-1);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  background: var(--spd-p-primary-bg);
  color: var(--spd-p-success-strong);
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.purchase-create-item-label {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  letter-spacing: 0.2px;
}

.purchase-create-item-product select,
.purchase-create-item-cell input {
  width: 100%;
  min-height: 34px;
  height: 34px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  padding: 0 8px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  font: inherit;
  font-size: var(--spd-font-size-base);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.purchase-create-item-product select.is-empty {
  color: var(--spd-p-text-muted);
}

.purchase-create-item-product select:focus,
.purchase-create-item-cell input:focus {
  border-color: var(--spd-p-primary);
  outline: 2px solid color-mix(in srgb, var(--spd-p-primary) 12%, transparent);
  outline-offset: 0;
}

.purchase-create-item-meta {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  line-height: var(--spd-lh-base);
}

.purchase-create-item-meta.is-placeholder {
  color: var(--spd-p-warning-strong);
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
  border: 1px solid var(--spd-p-danger-border);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-danger-strong);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.purchase-create-item-actions .btn-text:hover:not(:disabled) {
  background: var(--spd-p-danger-bg);
  border-color: var(--spd-p-danger-border);
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
  border-top: 1px solid var(--spd-p-border);
  background: linear-gradient(180deg, var(--spd-p-bg) 0%, var(--spd-p-primary-bg) 100%);
}

.purchase-create-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-secondary);
}

.purchase-create-summary-row strong {
  color: var(--spd-p-success-strong);
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.purchase-create-summary-row.is-total {
  grid-column: span 3;
  background: linear-gradient(180deg, var(--spd-p-success-bg) 0%, var(--spd-p-success-border) 100%);
  border-color: var(--spd-p-success);
}

.purchase-create-summary-row.is-total span { color: var(--spd-p-primary-strong); font-weight: 600; }
.purchase-create-summary-row.is-total strong { color: var(--spd-p-success-strong); font-size: var(--spd-font-size-2xl); }

/* ---------- 底部 ---------- */
.purchase-create-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 24px;
  border-top: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
}

.purchase-create-footer > span {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
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
  color: var(--spd-p-text-secondary);
}

.analysis-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 0;
  color: var(--spd-p-text-secondary);
}

.analysis-error {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
  border-color: var(--spd-p-danger-border);
}

.analysis-qty-input {
  width: 96px;
  min-height: 36px;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  padding: 0 10px;
  font: inherit;
}

.muted-cell {
  display: block;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
}

.form-section {
  margin: 16px 0;
}

.form-section-title {
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--spd-p-text);
  margin: 0 0 10px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--spd-p-border);
}

.product-search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.product-search-input {
  min-height: 38px;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  padding: 0 12px;
  font: inherit;
  width: min(320px, 100%);
}

.product-search-hint {
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-muted);
}

.item-editor-header {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 80px 80px 100px 60px;
  gap: 8px;
  padding: 0 2px;
  margin-bottom: 4px;
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  color: var(--spd-p-text-secondary);
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
  border: 1px solid var(--spd-p-border);
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
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
  margin-bottom: 4px;
}

.detail-summary-bar {
  display: flex;
  gap: 20px;
  padding: 12px 16px;
  background: var(--spd-p-bg-muted);
  border-bottom: 1px solid var(--spd-p-border);
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-secondary);
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
  background: var(--spd-p-primary-bg);
  color: var(--spd-p-primary);
  font-size: var(--spd-font-size-xs);
  font-weight: 700;
  padding: 0 7px;
  margin-left: 6px;
  vertical-align: middle;
}

@media (max-width: 860px) {
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
