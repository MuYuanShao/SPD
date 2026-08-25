<script setup lang="ts">
import { ref } from 'vue'
import {
  Ban,
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
  Upload,
  X
} from '@lucide/vue'
import { usePurchaseManagement } from '../../composables/usePurchaseManagement'
import PaginationControls from '../../components/common/PaginationControls.vue'
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
  suppliers,
  loading,
  message,
  purchasePagination,
  showCreateModal,
  showDemandModal,
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
  demandGroups,
  showDemandDetailDialog,
  selectedDemandGroup,
  tabs,
  statusOptions,
  stats,
  actionQueryLabel,
  filteredProducts,
  demandFilteredProducts,
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
  runSmartAnalysis,
  updateSmartRecommendedQty,
  createDemandFromSmartAnalysis,
  changePurchasePage,
  changePurchasePageSize,
  submitCreate,
  submitDemand,
  addDemandItem,
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

const orderAttachmentInput = ref<HTMLInputElement | null>(null)

function chooseOrderAttachment() {
  if (!selectedOrder.value) {
    message.value = '请先选择一条采购订单'
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
  <section class="purchase-page">
    <div class="breadcrumb-line">
      <span>供应链业务</span>
      <strong>采购管理</strong>
    </div>

    <div class="detail-heading">
      <div>
        <p>采购需求到订单履约闭环</p>
        <h2>采购管理</h2>
        <small>需求池、采购计划、采购订单、订单跟踪按子流程拆分；验收入库按最新医院目录采购价生成系统批次单价。</small>
      </div>
      <button class="btn" type="button" @click="loadData">
        <RefreshCw :size="17" />
        刷新
      </button>
    </div>

    <div class="foundation-stat-grid">
      <article v-for="item in stats" :key="item.label">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </div>

    <p v-if="message" class="inline-message">{{ message }}</p>

    <section class="hospital-catalog-panel">
      <div class="subnav-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="hospital-action-row" :class="`action-row--${activeTab}`">
        <label v-if="activeTab === 'smart'" class="inline-field">
          <span>分析周期</span>
          <select v-model.number="smartSelectedPeriod" class="inline-select">
            <option v-for="period in smartPeriods" :key="period" :value="period">近{{ period }}天</option>
          </select>
        </label>
        <button v-if="activeTab === 'smart'" class="btn btn-primary" type="button" @click="runSmartAnalysis">
          <Search :size="17" />
          智能补货分析
        </button>
        <button v-if="activeTab === 'demands'" class="btn btn-primary" type="button" @click="resetDemandForm(); showDemandModal = true">
          <PackagePlus :size="18" />
          新增采购
        </button>
        <select v-model="planForm.supplierName" class="inline-select" title="计划供应商">
          <option value="">按商品默认供应商生成计划</option>
          <option v-for="supplier in suppliers" :key="supplier.supplierName" :value="supplier.supplierName">
            {{ supplier.supplierName }}
          </option>
        </select>
        <button v-if="activeTab === 'demands'" class="btn" type="button" @click="generatePlans">
          <FileCheck2 :size="17" />
          需求转计划
        </button>
        <button v-if="activeTab === 'orders'" class="btn btn-primary" type="button" @click="resetOrderForm(); showCreateModal = true">
          <PackagePlus :size="18" />
          新增订单
        </button>
        <template v-if="activeTab === 'orders'">
          <button class="btn" type="button" :disabled="selectedOrder?.orderStatus !== 'draft'" @click="runSelectedOrderAction('submit')">
            <Send :size="16" /> 提交
          </button>
          <button class="btn" type="button" :disabled="selectedOrder?.orderStatus !== 'pending_approval'" @click="runSelectedOrderAction('approve')">
            <CheckCircle2 :size="16" /> 审批
          </button>
          <button
            class="btn btn-danger-soft"
            type="button"
            :disabled="!selectedOrder || !['draft', 'pending_approval', 'approved'].includes(selectedOrder.orderStatus)"
            @click="openOrderOperation('void')"
          >
            <Ban :size="16" /> 作废
          </button>
          <button class="btn" type="button" :disabled="!selectedOrder" @click="copySelectedOrder">
            <Copy :size="16" /> 复制订单
          </button>
          <button class="btn" type="button" :disabled="!selectedOrder" @click="chooseOrderAttachment">
            <Upload :size="16" /> 上传附件
          </button>
          <input ref="orderAttachmentInput" type="file" hidden @change="handleOrderAttachmentUpload" />
          <button class="btn" type="button" :disabled="!selectedOrder" @click="openOrderAttachments">
            <Paperclip :size="16" /> 附件阅览
          </button>
          <button class="btn" type="button" :disabled="!selectedOrder" @click="openOrderOperation('remark')">
            <MessageSquare :size="16" /> 添加备注
          </button>
        </template>
        <button v-if="activeTab !== 'smart'" class="btn" type="button" @click="loadData">
          <Search :size="17" />
          {{ actionQueryLabel }}
        </button>
        <button v-if="activeTab === 'tracking' && detail" class="btn" type="button" @click="detail = null">
          <X :size="17" />
          清空跟踪
        </button>
        <span v-if="activeTab === 'plans'" class="action-hint">计划审核、驳回、转订单在计划明细行内操作。</span>
        <span v-if="activeTab === 'tracking'" class="action-hint">采购跟踪从采购订单点击“查看”进入。</span>
      </div>

      <div v-if="activeTab !== 'smart'" class="hospital-query-grid purchase-query-grid">
        <label><span>订单编号</span><input v-model="query.orderNo" placeholder="CG2026..." /></label>
        <label><span>需求编号</span><input v-model="query.demandNo" placeholder="XQ2026..." /></label>
        <label><span>计划编号</span><input v-model="query.planNo" placeholder="JH2026..." /></label>
        <label><span>供应商</span><input v-model="query.supplierName" placeholder="模糊查询供应商" /></label>
        <label><span>商品</span><input v-model="query.keyword" placeholder="商品编码/名称/规格" /></label>
        <label>
          <span>状态</span>
          <select v-model="query.status">
            <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
      </div>

      <div v-if="activeTab === 'demands'" class="table-scroll">
        <table class="master-table purchase-table purchase-demand-table">
          <thead>
            <tr>
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
              <td colspan="9" class="approval-empty">正在加载采购需求...</td>
            </tr>
            <tr v-for="group in demandGroups" v-else :key="group.demandNo" class="demand-group-row">
              <td>
                <strong>{{ group.demandNo }}</strong>
                <span class="item-count-badge">{{ group.itemCount }}项</span>
              </td>
              <td>{{ group.demandSource }}</td>
              <td>{{ group.deptName || '-' }}</td>
              <td class="demand-group-items-cell">
                <span class="item-summary">共 {{ group.itemCount }} 种商品</span>
                <button class="btn-link" type="button" @click="openDemandDetail(group)">查看明细</button>
              </td>
              <td>{{ group.totalQuantity }}</td>
              <td>{{ group.totalSuggestedQty }}</td>
              <td>{{ group.urgentLevel }}</td>
              <td><span :class="['status-badge', statusTone(group.demandStatus)]">{{ statusLabel(group.demandStatus) }}</span></td>
              <td class="row-actions">
                <button v-if="group.demandStatus === 'draft'" class="btn-text" @click="runDemandAction(group, 'submit')">提交</button>
                <button v-if="group.demandStatus === 'pending_review'" class="btn-text" @click="runDemandAction(group, 'approve')">审核通过</button>
                <button v-if="group.demandStatus === 'pending_review'" class="btn-text btn-text-danger" @click="runDemandAction(group, 'reject')">驳回</button>
              </td>
            </tr>
            <tr v-if="!loading && demandGroups.length === 0">
              <td colspan="9" class="approval-empty">暂无采购需求</td>
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

      <div v-if="activeTab === 'plans'" class="table-scroll">
        <table class="master-table purchase-table purchase-plan-table">
          <thead>
            <tr>
              <th>计划编号</th>
              <th>商品编码</th>
              <th>商品名称</th>
              <th>规格型号</th>
              <th>注册证号</th>
              <th>厂家</th>
              <th>单位</th>
              <th>单价</th>
              <th>采购数量</th>
              <th>金额</th>
              <th>配送商</th>
              <th>招采子编码</th>
              <th>转订单号</th>
              <th>状态</th>
              <th>发起科室</th>
              <th>送货库房</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="17" class="approval-empty">正在加载采购计划...</td>
            </tr>
            <tr v-for="row in plans" v-else :key="row.planNo">
              <td class="document-no-cell">{{ row.planNo }}</td>
              <td>{{ row.productCode || '-' }}</td>
              <td>{{ row.productName || '-' }}</td>
              <td>{{ row.specModel || '-' }}</td>
              <td>{{ row.registrationNo || '-' }}</td>
              <td>{{ row.manufacturerName || '-' }}</td>
              <td>{{ row.unit || '-' }}</td>
              <td>¥ {{ Number(row.unitPrice || 0).toFixed(2) }}</td>
              <td>{{ row.plannedQuantity }}</td>
              <td class="purchase-plan-amount">¥ {{ Number(row.amount ?? Number(row.unitPrice || 0) * Number(row.plannedQuantity || 0)).toFixed(2) }}</td>
              <td>{{ row.supplierName || '-' }}</td>
              <td>{{ row.tenderSubCode || '-' }}</td>
              <td>{{ row.convertedOrderNo || '-' }}</td>
              <td><span :class="['status-badge', statusTone(row.planStatus)]">{{ statusLabel(row.planStatus) }}</span></td>
              <td>{{ row.initiatingDeptName || '-' }}</td>
              <td>{{ row.deliveryWarehouseName || '-' }}</td>
              <td class="row-actions">
                <button v-if="row.planStatus === 'draft'" class="btn-text" @click="runPlanAction(row, 'approve')">计划审核</button>
                <button v-if="row.planStatus === 'approved'" class="btn-text" @click="runPlanAction(row, 'execute')">转订单</button>
                <button v-if="row.planStatus === 'draft'" class="btn-text btn-text-danger" @click="runPlanAction(row, 'reject')">驳回</button>
              </td>
            </tr>
            <tr v-if="!loading && plans.length === 0">
              <td colspan="17" class="approval-empty">暂无采购计划</td>
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

      <div v-if="activeTab === 'orders'" class="table-scroll">
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
              <td>{{ row.orderSource || '-' }}</td>
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
                <button v-if="row.orderStatus === 'approved'" class="btn-text" @click="runOrderAction(row, 'send')">
                  <Send :size="15" /> 发送
                </button>
                <button
                  v-if="['approved', 'sent'].includes(row.orderStatus)"
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
          <header class="tracking-header">
            <div>
              <span>{{ detail.order.orderNo }}</span>
              <strong>{{ detail.order.supplierName }}</strong>
            </div>
            <button class="btn-icon" type="button" @click="detail = null">
              <X :size="18" />
            </button>
          </header>
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
        <p v-if="smartAnalysisError" class="inline-message analysis-error">{{ smartAnalysisError }}</p>

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
                <td colspan="11" class="approval-empty">正在分析一级库出二级库数据...</td>
              </tr>
              <tr v-else-if="smartRows.length === 0">
                <td colspan="11" class="approval-empty">暂无可补货建议</td>
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

    <!-- 需求明细弹窗 -->
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
              <td>¥ {{ (Number(item.unitPrice || 0) * Number(item.quantity)).toFixed(2) }}</td>
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

    <div v-if="showDemandModal" class="modal-mask purchase-entry-mask">
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
                <span><em>*</em> 申请科室</span>
                <input v-model="demandForm.deptName" placeholder="请输入申请科室" />
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
              <button class="btn btn-primary" type="button" @click="addDemandItem">
                <Plus :size="17" />
                新增商品行
              </button>
              <label class="purchase-entry-search">
                <Search :size="16" />
                <input v-model="demandProductSearchQuery" placeholder="搜索商品编码、名称或规格" />
              </label>
            </div>

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
                  <tr v-for="(item, index) in demandForm.items" :key="index">
                    <td class="purchase-code-cell">{{ productByCode(item.productCode)?.productCode || '-' }}</td>
                    <td class="purchase-product-cell">
                      <select v-model="item.productCode" :aria-label="`第 ${index + 1} 行商品`">
                        <option value="">请选择商品</option>
                        <option v-for="product in demandFilteredProducts" :key="product.productCode" :value="product.productCode">
                          {{ product.productName }} · {{ product.productCode }}
                        </option>
                      </select>
                    </td>
                    <td>{{ productByCode(item.productCode)?.specModel || '-' }}</td>
                    <td>{{ productByCode(item.productCode)?.unit || '-' }}</td>
                    <td class="purchase-quantity-cell">
                      <input v-model.number="item.quantity" type="number" min="1" step="1" :aria-label="`第 ${index + 1} 行采购数量`" />
                    </td>
                    <td>¥ {{ Number(productByCode(item.productCode)?.purchasePrice || 0).toFixed(2) }}</td>
                    <td class="purchase-amount-cell">
                      ¥ {{ (Number(item.quantity || 0) * Number(productByCode(item.productCode)?.purchasePrice || 0)).toFixed(2) }}
                    </td>
                    <td>
                      <button class="purchase-row-delete" type="button" :disabled="demandForm.items.length === 1" @click="removeDemandItem(index)">
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
          <span>保存后可在“采购需求”中继续审核和生成采购计划。</span>
          <div>
            <button class="btn" type="button" @click="showDemandModal = false">取消</button>
            <button class="btn" type="button" @click="submitDemand(true)">
              <Save :size="16" />
              保存并继续
            </button>
            <button class="btn btn-primary" type="button" @click="submitDemand(false)">
              <CheckCircle2 :size="17" />
              保存
            </button>
          </div>
        </footer>
      </section>
    </div>

    <div v-if="showCreateModal" class="modal-mask">
      <section class="edit-modal purchase-create-modal">
        <header>
          <h3>新增采购订单</h3>
          <button class="btn-icon" type="button" @click="showCreateModal = false"><X :size="18" /></button>
        </header>

        <!-- 采购信息 -->
        <section class="form-section">
          <h4 class="form-section-title">采购信息</h4>
          <div class="modal-grid">
            <label>
              <span>供应商</span>
              <select v-model="form.supplierName">
                <option value="">请选择</option>
                <option v-for="supplier in suppliers" :key="supplier.supplierName" :value="supplier.supplierName">
                  {{ supplier.supplierName }}
                </option>
              </select>
            </label>
            <label><span>采购类型</span><input v-model="form.orderSource" /></label>
            <label><span>预计到货</span><input v-model="form.expectedArrivalDate" type="date" /></label>
          </div>
        </section>

        <!-- 商品明细 -->
        <section class="form-section">
          <h4 class="form-section-title">商品明细</h4>
          <div class="product-search-bar">
            <input v-model="productSearchQuery" placeholder="搜索商品编码/名称/规格" class="product-search-input" />
            <span class="product-search-hint">从下方列表选择已添加的商品，或点击「新增明细」添加商品行</span>
          </div>
          <div class="item-editor">
            <div class="item-editor-header">
              <span class="ieh-col-product">商品</span>
              <span class="ieh-col-qty">数量</span>
              <span class="ieh-col-unit">单位</span>
              <span class="ieh-col-price">单价</span>
              <span class="ieh-col-action">操作</span>
            </div>
            <article v-for="(item, index) in form.items" :key="index">
              <select v-model="item.productCode" @change="fillProduct(index)">
                <option value="">选择商品</option>
                <option v-for="product in filteredProducts" :key="product.productCode" :value="product.productCode">
                  {{ product.productName }} · {{ product.specModel }}
                </option>
              </select>
              <input v-model.number="item.quantity" type="number" min="1" placeholder="数量" />
              <input v-model="item.unit" placeholder="单位" />
              <input v-model.number="item.estimatedUnitPrice" type="number" min="0" step="0.01" placeholder="单价" />
              <button class="btn-text btn-text-danger" type="button" @click="removeItem(index)">删除</button>
            </article>
            <button class="btn add-item-btn" type="button" @click="addItem">
              + 新增明细
            </button>
          </div>
        </section>

        <footer>
          <button class="btn" type="button" @click="showCreateModal = false">取消</button>
          <button class="btn btn-primary" type="button" @click="submitCreate">保存</button>
        </footer>
      </section>
    </div>

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
  </section>
</template>

<style scoped>
.purchase-page {
  display: grid;
  gap: 18px;
}

.inline-message {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid #b7ded8;
  border-radius: 8px;
  color: #0f766e;
  background: #f0fdfa;
}

.inline-select {
  min-height: 42px;
  border: 1px solid #d6e0ea;
  border-radius: 8px;
  padding: 0 12px;
  font: inherit;
  color: #0f2233;
  background: #fff;
}

.inline-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #52677a;
  font-weight: 600;
}

.hospital-action-row {
  align-items: center;
}

.action-hint {
  color: #66788a;
  font-size: 14px;
  line-height: 1.4;
}

.action-row--plans .inline-select,
.action-row--orders .inline-select,
.action-row--tracking .inline-select {
  display: none;
}

.purchase-query-grid {
  grid-template-columns: repeat(3, minmax(180px, 1fr));
}

.purchase-table td strong,
.purchase-table td small {
  display: block;
}

.purchase-table td small {
  color: #718096;
}

.purchase-plan-table {
  min-width: 2400px;
}

.purchase-plan-table th {
  white-space: nowrap;
}

.purchase-plan-table td {
  vertical-align: middle;
}

.purchase-plan-table td:nth-child(3),
.purchase-plan-table td:nth-child(4),
.purchase-plan-table td:nth-child(5),
.purchase-plan-table td:nth-child(6),
.purchase-plan-table td:nth-child(15),
.purchase-plan-table td:nth-child(16) {
  min-width: 150px;
}

.document-no-cell {
  color: #24566b;
  font-weight: 700;
  white-space: nowrap;
}

.purchase-plan-amount {
  color: #0f766e;
  font-weight: 700;
  white-space: nowrap;
}

.order-select-column {
  width: 56px;
  text-align: center;
}

.order-select-column input {
  width: 16px;
  height: 16px;
  accent-color: #0f766e;
  cursor: pointer;
}

.selected-order-row td {
  background: #eef8f6;
}

.btn-danger-soft {
  border-color: #efc4c4;
  background: #fff6f6;
  color: #b83a3a;
}

.btn-danger-soft:hover:not(:disabled) {
  border-color: #d97777;
  background: #fff0f0;
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

.tracking-header span,
.tracking-header strong {
  display: block;
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
  z-index: 50;
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

/* ── Purchase create modal sections ── */
.purchase-create-modal {
  width: min(860px, 100%);
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
