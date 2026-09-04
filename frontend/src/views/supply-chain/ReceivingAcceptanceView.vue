<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  CheckCircle2,
  CopyPlus,
  Eye,
  PackageCheck,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Search,
  Trash2,
  X,
  XCircle
} from '@lucide/vue'
import {
  fetchReceivingOptions,
  fetchReceivingOrders,
  updateReceivingAction,
  type ReceivingOptionRow,
  type ReceivingOrderRow,
  type SupplierOption
} from '../../api/receivingOrders'
import PaginationControls from '../../components/common/PaginationControls.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import { useAuthStore } from '../../stores/auth'
import { fetchFieldOptions, type FieldOption } from '../../api/fieldOptions'
import { useReceivingOrderCreateActions } from '../../composables/useReceivingOrderCreateActions'
import { useReceivingOrderDetail } from '../../composables/useReceivingOrderDetail'
import { useReceivingOrderForm } from '../../composables/useReceivingOrderForm'
import { useReceivingOrderPagination } from '../../composables/useReceivingOrderPagination'
import { formatStatusText } from '../../utils/chineseDisplay'

type ReceivingTab = 'pending' | 'completed'

const rows = ref<ReceivingOrderRow[]>([])
const purchaseOrders = ref<ReceivingOptionRow[]>([])
const warehouses = ref<ReceivingOptionRow[]>([])
const products = ref<ReceivingOptionRow[]>([])
const suppliers = ref<SupplierOption[]>([])
const summary = ref<Record<string, number>>({})
const receivingTypeOptions = ref<FieldOption[]>([
  { optionId: -1, fieldKey: 'receiving_type', fieldLabel: '收货类型', optionLabel: '正常收货', optionValue: 'normal', sortOrder: 1, status: 1 },
  { optionId: -2, fieldKey: 'receiving_type', fieldLabel: '收货类型', optionLabel: '代理商直送', optionValue: 'agent', sortOrder: 2, status: 1 }
])
const authStore = useAuthStore()
const optionsLoaded = ref(false)
const optionsLoading = ref(false)
const selectedItemIndexes = ref<number[]>([])
const scanCode = ref('')
const canCreate = computed(() => authStore.hasPermission('receiving-order:create'))
const canUpdate = computed(() => authStore.hasPermission('receiving-order:update'))
const canApprove = computed(() => authStore.hasPermission('receiving-order:approve'))
const canReject = computed(() => authStore.hasPermission('receiving-order:reject'))
const quantityMismatch = computed(() => form.items.some((item) =>
  Number(item.qualifiedQuantity || 0) + Number(item.unqualifiedQuantity || 0) !== Number(item.quantity || 0)
))

async function loadReceivingTypeOptions() {
  try {
    const options = await fetchFieldOptions('receiving_type')
    receivingTypeOptions.value = options.filter((item) => item.status === 1 && ['normal', 'agent'].includes(item.optionValue))
  } catch {
    receivingTypeOptions.value = [
      { optionId: -1, fieldKey: 'receiving_type', fieldLabel: '收货类型', optionLabel: '正常收货', optionValue: 'normal', sortOrder: 1, status: 1 },
      { optionId: -2, fieldKey: 'receiving_type', fieldLabel: '收货类型', optionLabel: '代理商直送', optionValue: 'agent', sortOrder: 2, status: 1 }
    ]
  }
}
const loading = ref(false)
const message = ref('')
const showCreateModal = ref(false)
const editingReceivingNo = ref('')
const actionLoadingNo = ref('')
const activeTab = ref<ReceivingTab>('pending')
const tabPageState = reactive<Record<ReceivingTab, { page: number; size: number }>>({
  pending: { page: 1, size: 20 },
  completed: { page: 1, size: 20 }
})
const query = reactive({
  receivingNo: '',
  purchaseOrderNo: '',
  supplierName: '',
  status: ''
})
const {
  form,
  supplierSearchQuery,
  showSupplierDropdown,
  filteredSuppliers,
  createTotals,
  currentCreateTime,
  selectSupplier,
  clearSupplier,
  resetForm,
  openCreateModal: openCreateForm,
  closeCreateModal,
  closeSupplierDropdown,
  addItem,
  removeItem,
  syncQualifiedQuantity,
  syncUnqualifiedQuantity
} = useReceivingOrderForm({ suppliers, showCreateModal, editingReceivingNo })
const {
  detail,
  detailLoading,
  detailPage,
  detailSize,
  detailQuery,
  openDetail,
  searchDetailItems,
  changeDetailPage,
  changeDetailPageSize
} = useReceivingOrderDetail()
const { currentPage, pageSize, totalItems, changePage, changePageSize, searchOrders } =
  useReceivingOrderPagination({ reload: loadData })

const { submitting, fillFromPurchaseOrder, submitCreate, submitCreateAndContinue, openEditModal: openEditForm } =
  useReceivingOrderCreateActions({
    form,
    supplierSearchQuery,
    showCreateModal,
    editingReceivingNo,
    message,
    resetForm,
    addItem,
    reload: loadData
  })
const statusOptions = [
  { value: '', label: '全部已完成' },
  { value: 'approved', label: '已入库' },
  { value: 'rejected', label: '已拒收' }
]

const tabs = computed(() => [
  { key: 'pending' as const, label: '待收货', count: summary.value.draftCount ?? 0 },
  { key: 'completed' as const, label: '已验收', count: summary.value.completedCount ?? 0 }
])

function statusLabel(status: string) {
  return statusOptions.find((item) => item.value === status)?.label ?? formatStatusText(status)
}

function statusTone(status: string) {
  if (status === 'approved') return 'enabled'
  if (status === 'rejected') return 'disabled'
  return 'pending'
}

async function loadData() {
  loading.value = true
  try {
    const listData = await fetchReceivingOrders({
        ...query,
        statusGroup: activeTab.value,
        page: String(currentPage.value),
        size: String(pageSize.value)
      })
    rows.value = listData.rows
    totalItems.value = listData.total
    tabPageState[activeTab.value] = { page: currentPage.value, size: pageSize.value }
    summary.value = listData.summary ?? {}
  } catch (error) {
    message.value = error instanceof Error ? error.message : '收货单加载失败'
  } finally {
    loading.value = false
  }
}

async function ensureOptions() {
  if (optionsLoaded.value || optionsLoading.value) return
  optionsLoading.value = true
  try {
    const [optionData] = await Promise.all([fetchReceivingOptions(), loadReceivingTypeOptions()])
    purchaseOrders.value = optionData.purchaseOrders
    warehouses.value = optionData.warehouses
    products.value = optionData.products
    suppliers.value = optionData.suppliers
    optionsLoaded.value = true
  } finally {
    optionsLoading.value = false
  }
}

async function openCreateModal() {
  await ensureOptions()
  selectedItemIndexes.value = []
  openCreateForm()
}

async function openEditModal(row: ReceivingOrderRow) {
  await ensureOptions()
  selectedItemIndexes.value = []
  await openEditForm(row)
}

function changeSourceType() {
  form.purchaseOrderNo = ''
  clearSupplier()
  form.items = [{ productCode: '', productionBatchNo: '', udiCode: '', productionDate: '', expireDate: '', quantity: 1, qualifiedQuantity: 1, unqualifiedQuantity: 0 }]
}

function deleteSelectedItems() {
  const selected = new Set(selectedItemIndexes.value)
  form.items = form.items.filter((_, index) => !selected.has(index))
  if (!form.items.length) addItem()
  selectedItemIndexes.value = []
}

function copySelectedItems() {
  selectedItemIndexes.value.forEach((index) => {
    const source = form.items[index]
    if (!source) return
    form.items.push({ ...source, productionBatchNo: '', udiCode: '', productionDate: '', expireDate: '' })
  })
  selectedItemIndexes.value = []
}

function scanProduct() {
  const code = scanCode.value.trim()
  if (!code) return
  const product = products.value.find((item) => item.productCode === code)
  if (!product) {
    message.value = `未找到启用的商品编码：${code}`
    return
  }
  const existing = form.items.findIndex((item) => item.productCode === code)
  if (existing >= 0) selectedItemIndexes.value = [existing]
  else {
    const blankIndex = form.items.findIndex((item) => !item.productCode)
    if (blankIndex >= 0) {
      form.items[blankIndex]!.productCode = code
      selectedItemIndexes.value = [blankIndex]
    } else {
      addItem()
      form.items[form.items.length - 1]!.productCode = code
      selectedItemIndexes.value = [form.items.length - 1]
    }
  }
  scanCode.value = ''
}

async function confirmCloseCreateModal() {
  const hasInput = form.items.some((item) => item.productCode || item.productionBatchNo || item.udiCode) || Boolean(form.remark)
  if (hasInput) {
    try {
      await ElMessageBox.confirm('当前收货内容尚未保存，确认关闭吗？', '放弃未保存内容', {
        confirmButtonText: '确认关闭', cancelButtonText: '继续编辑', type: 'warning'
      })
    } catch {
      return
    }
  }
  closeCreateModal()
}

async function switchTab(tab: ReceivingTab) {
  if (tab === activeTab.value) return
  tabPageState[activeTab.value] = { page: currentPage.value, size: pageSize.value }
  activeTab.value = tab
  query.status = ''
  currentPage.value = tabPageState[tab].page
  pageSize.value = tabPageState[tab].size
  await loadData()
}

async function runAction(row: ReceivingOrderRow, action: string) {
  actionLoadingNo.value = `${row.receivingNo}:${action}`
  message.value = ''
  try {
    const opinion = action === 'reject'
      ? (await ElMessageBox.prompt('请填写拒收原因', `拒收 ${row.receivingNo}`, { inputPattern: /\S+/, inputErrorMessage: '拒收原因不能为空', confirmButtonText: '确认拒收', cancelButtonText: '取消' })).value
      : (await ElMessageBox.confirm('审核后仅合格数量入库；若全部不合格，系统将自动整单拒收。', `审核 ${row.receivingNo}`, { confirmButtonText: '确认审核', cancelButtonText: '取消', type: 'warning' }), '验收通过')
    const result = await updateReceivingAction(row.receivingNo, action, opinion)
    message.value =
      result.status === 'approved'
        ? `${row.receivingNo} 已审核入库，系统批次和库存余额已生成`
        : `${row.receivingNo} 已拒收`
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    message.value = error instanceof Error ? error.message : `${row.receivingNo} 操作失败`
  } finally {
    actionLoadingNo.value = ''
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <section class="purchase-page receiving-page">
    <PageHeader eyebrow="供应链业务" title="收货验收" description="验收合格数量审核后入库；不合格数量保留记录但不影响库存。">
      <template #actions>
      <button class="btn" type="button" @click="loadData">
        <RefreshCw :size="17" />
        刷新
      </button>
      </template>
    </PageHeader>

    <StatusMessage v-if="message" :message="message" :tone="message.includes('失败') || message.includes('未找到') ? 'error' : 'success'" />

    <section class="hospital-catalog-panel">
      <div class="subnav-tabs receiving-status-tabs" role="tablist" aria-label="收货验收状态">
        <button
          v-for="tab in tabs"
          :id="`receiving-tab-${tab.key}`"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :aria-controls="`receiving-panel-${tab.key}`"
          :class="{ active: activeTab === tab.key }"
          @click="switchTab(tab.key)"
        >
          <span>{{ tab.label }}</span>
          <span class="receiving-tab-count">{{ tab.count }}</span>
        </button>
      </div>

      <div class="hospital-action-row">
        <button v-if="canCreate" class="btn btn-primary" type="button" :disabled="optionsLoading" @click="openCreateModal">
          <PackageCheck :size="18" />
          新增收货
        </button>
        <button class="btn" type="button" @click="searchOrders">
          <Search :size="17" />
          收货单查询
        </button>
      </div>

      <div class="hospital-query-grid purchase-query-grid">
        <label><span>收货单号</span><input v-model="query.receivingNo" placeholder="RK2026..." /></label>
        <label><span>采购订单</span><input v-model="query.purchaseOrderNo" placeholder="CG2026..." /></label>
        <label><span>供应商</span><input v-model="query.supplierName" placeholder="模糊查询供应商" /></label>
        <label v-if="activeTab === 'completed'">
          <span>状态</span>
          <select v-model="query.status">
            <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
        <label v-else>
          <span>当前范围</span>
          <input value="待验收（草稿）" disabled />
        </label>
        <button class="btn btn-primary" type="button" @click="searchOrders">
          <Search :size="18" />
          查询
        </button>
      </div>

      <div
        :id="`receiving-panel-${activeTab}`"
        class="table-scroll"
        role="tabpanel"
        :aria-labelledby="`receiving-tab-${activeTab}`"
      >
        <table class="master-table purchase-table">
          <thead>
            <tr>
              <th>收货单号</th>
              <th>采购订单</th>
              <th>供应商</th>
              <th>入库库房</th>
              <th>明细数</th>
              <th>收货数量</th>
              <th>入库金额</th>
              <th>状态</th>
              <th>收货时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="10"><StatusMessage message="正在加载收货验收单..." tone="info" /></td>
            </tr>
            <tr v-for="row in rows" v-else :key="row.receivingNo">
              <td>{{ row.receivingNo }}</td>
              <td>{{ row.purchaseOrderNo || '-' }}</td>
              <td>{{ row.supplierName }}</td>
              <td>{{ row.warehouseName }}</td>
              <td>{{ row.itemCount }}</td>
              <td>{{ row.receiveQuantity }}</td>
              <td>¥ {{ Number(row.receiveAmount).toFixed(2) }}</td>
              <td><span :class="['status-badge', statusTone(row.receivingStatus)]">{{ statusLabel(row.receivingStatus) }}</span></td>
              <td>{{ row.receiveTime || '-' }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" class="btn-text" @click="openDetail(row)"><Eye :size="15" /> 查看</button>
                  <button v-if="canUpdate && row.receivingStatus === 'draft'" type="button" class="btn-text" @click="openEditModal(row)">
                    <Pencil :size="15" />
                    修改
                  </button>
                  <button
                    v-if="canApprove && row.receivingStatus === 'draft'"
                    type="button"
                    class="btn-text"
                    :disabled="Boolean(actionLoadingNo)"
                    @click="runAction(row, 'approve')">
                    <CheckCircle2 :size="15" />
                    {{ actionLoadingNo === `${row.receivingNo}:approve` ? '处理中' : '审核入库' }}
                  </button>
                  <button
                    v-if="canReject && row.receivingStatus === 'draft'"
                    type="button"
                    class="btn-text btn-text-danger"
                    :disabled="Boolean(actionLoadingNo)"
                    @click="runAction(row, 'reject')">
                    <XCircle :size="15" />
                    {{ actionLoadingNo === `${row.receivingNo}:reject` ? '处理中' : '拒收' }}
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!loading && !rows.length">
              <td colspan="10"><EmptyState :message="activeTab === 'pending' ? '暂无待收货单据' : '暂无已验收单据'" /></td>
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

    <div v-if="showCreateModal" class="modal-mask receiving-modal-mask" @click.self="confirmCloseCreateModal">
      <section class="supplier-modal purchase-modal receiving-create-modal" role="dialog" aria-modal="true" aria-labelledby="receiving-create-title" @keydown.esc="confirmCloseCreateModal">
        <div class="receiving-create-titlebar">
          <strong id="receiving-create-title">{{ editingReceivingNo ? `修改 ${editingReceivingNo}` : '新增收货单' }}</strong>
          <button type="button" class="btn-icon" aria-label="关闭" @click="confirmCloseCreateModal"><X :size="18" /></button>
        </div>

        <div class="receiving-create-body">
          <div class="receiving-form-grid">
            <label class="required">
              <span>收货来源</span>
              <select v-model="form.sourceType" autofocus @change="changeSourceType">
                <option value="purchase_order">采购订单收货</option>
                <option value="temporary">临时收货</option>
              </select>
            </label>
            <label class="required">
              <span>收货库房</span>
              <select v-model="form.warehouseCode">
                <option value="">请选择收货库房</option>
                <option v-for="warehouse in warehouses" :key="warehouse.warehouseCode" :value="warehouse.warehouseCode">
                  {{ warehouse.warehouseName }}
                </option>
              </select>
            </label>
            <label class="required supplier-field">
              <span>配送商</span>
              <div class="supplier-select-wrapper">
                <input v-model="supplierSearchQuery" :disabled="form.sourceType === 'purchase_order'" placeholder="输入供应商名称搜索" @focus="showSupplierDropdown = true" @blur="closeSupplierDropdown()" />
                <button v-if="form.supplierName" type="button" class="supplier-clear" @click="clearSupplier">&times;</button>
                <ul v-if="showSupplierDropdown && filteredSuppliers.length" class="supplier-dropdown">
                  <li v-for="s in filteredSuppliers" :key="s.supplierName"
                    :class="{ active: s.supplierName === form.supplierName }"
                    @mousedown.prevent="selectSupplier(s.supplierName)">
                    {{ s.supplierName }}
                  </li>
                </ul>
              </div>
            </label>
            <label v-if="form.sourceType === 'purchase_order'">
              <span>采购订单</span>
              <select v-model="form.purchaseOrderNo" data-testid="receiving-purchase-order" @change="fillFromPurchaseOrder">
                <option value="">请选择采购订单</option>
                <option v-for="order in purchaseOrders" :key="order.orderNo" :value="order.orderNo">
                  {{ order.orderNo }} · {{ order.supplierName }}
                </option>
              </select>
            </label>
            <label>
              <span>收货类型</span>
              <select v-model="form.receivingType">
                <option value="">请选择收货类型</option>
                <option v-for="option in receivingTypeOptions" :key="option.optionId" :value="option.optionValue">
                  {{ option.optionLabel }}
                </option>
              </select>
            </label>
            <label>
              <span>备注</span>
              <input v-model="form.remark" placeholder="填写本次收货备注" />
            </label>
          </div>

          <div class="receiving-entry-toolbar">
            <button type="button" class="teal-action" @click="addItem"><Plus :size="16" /> 新增</button>
            <button type="button" class="teal-action danger" :disabled="!selectedItemIndexes.length" title="请先勾选明细" @click="deleteSelectedItems"><Trash2 :size="16" /> 删除勾选</button>
            <button type="button" class="teal-action" :disabled="!selectedItemIndexes.length" title="请先勾选明细" @click="copySelectedItems"><CopyPlus :size="16" /> 复制细单</button>
            <label class="barcode-field">
              <span>商品码扫描</span>
              <input v-model="scanCode" placeholder="扫描或录入商品码" @keyup.enter="scanProduct" />
            </label>
          </div>

          <section class="receiving-detail-sheet">
            <table>
              <thead>
                <tr>
                  <th class="check-col"></th>
                  <th>验收明细标识</th>
                  <th>批次</th>
                  <th>商品名称</th>
                  <th>商品编码</th>
                  <th>生产批号</th>
                  <th>UDI</th>
                  <th>生产日期</th>
                  <th>失效日期</th>
                  <th>收货数量</th>
                  <th>合格数量</th>
                  <th>不合格数量</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in form.items" :key="index">
                  <td><input v-model="selectedItemIndexes" type="checkbox" :value="index" :aria-label="`选择明细 ${index + 1}`" /></td>
                  <td>明细 {{ index + 1 }}</td>
                  <td><input value="[自动生成]" disabled /></td>
                  <td>
                    <select v-model="item.productCode">
                      <option value="">选择商品</option>
                      <option v-for="product in products" :key="product.productCode" :value="product.productCode">
                        {{ product.productName }}
                      </option>
                    </select>
                  </td>
                  <td>
                    <select v-model="item.productCode">
                      <option value="">商品编码</option>
                      <option v-for="product in products" :key="product.productCode" :value="product.productCode">
                        {{ product.productCode }}
                      </option>
                    </select>
                  </td>
                  <td><input v-model="item.productionBatchNo" placeholder="生产批号" /></td>
                  <td><input v-model.trim="item.udiCode" placeholder="录入UDI（独立于唯一码）" /></td>
                  <td><input v-model="item.productionDate" type="date" /></td>
                  <td><input v-model="item.expireDate" type="date" /></td>
                  <td><input v-model.number="item.quantity" type="number" min="1" @input="syncQualifiedQuantity(index)" /></td>
                  <td><input v-model.number="item.qualifiedQuantity" type="number" min="0" @input="syncUnqualifiedQuantity(index)" /></td>
                  <td><input v-model.number="item.unqualifiedQuantity" type="number" min="0" /></td>
                  <td>
                    <button type="button" class="sheet-delete" title="删除" @click="removeItem(index)">
                      <Trash2 :size="15" />
                    </button>
                  </td>
                </tr>
                <tr v-if="!form.items.length">
                  <td colspan="13"><EmptyState message="暂无收货明细" /></td>
                </tr>
              </tbody>
            </table>
          </section>
          <StatusMessage v-if="quantityMismatch" message="每条明细的合格数量与不合格数量之和必须等于收货数量" tone="error" />
        </div>

        <div class="receiving-create-footer">
          <div class="receiving-total">
            <span>总数量:{{ createTotals.quantity.toFixed(2) }}</span>
            <span>合格:{{ createTotals.qualifiedQuantity.toFixed(2) }}</span>
            <span>不合格:{{ createTotals.unqualifiedQuantity.toFixed(2) }}</span>
          </div>
          <div class="receiving-footer-actions">
            <button class="teal-action solid" type="button" :disabled="submitting || quantityMismatch" @click="submitCreate">
              <Save :size="16" />
              {{ submitting ? '保存中...' : '保存' }}
            </button>
            <button v-if="!editingReceivingNo" class="teal-action solid" type="button" :disabled="submitting || quantityMismatch" @click="submitCreateAndContinue">
              <Save :size="16" />
              保存并继续
            </button>
            <button class="teal-action dark" type="button" @click="confirmCloseCreateModal">
              <X :size="16" />
              取消
            </button>
          </div>
        </div>
      </section>
    </div>

    <div v-if="detail" class="modal-mask receiving-detail-mask">
      <section class="supplier-modal purchase-modal receiving-detail-modal" role="dialog" aria-modal="true" aria-labelledby="receiving-detail-title" @keydown.esc="detail = null">
        <header class="receiving-detail-header">
          <div>
            <p>收货验收单</p>
            <h3 id="receiving-detail-title">{{ detail.order.receivingNo }}</h3>
          </div>
          <button type="button" class="btn btn-sm" @click="detail = null"><X :size="18" /> 关闭</button>
        </header>

        <div class="receiving-detail-meta">
          <article>
            <span>供应商</span>
            <strong>{{ detail.order.supplierName }}</strong>
          </article>
          <article>
            <span>入库库房</span>
            <strong>{{ detail.order.warehouseName }}</strong>
          </article>
          <article>
            <span>状态</span>
            <strong>{{ statusLabel(detail.order.receivingStatus) }}</strong>
          </article>
          <article>
            <span>收货时间</span>
            <strong>{{ detail.order.receiveTime || '-' }}</strong>
          </article>
        </div>

        <div class="receiving-detail-toolbar">
          <label class="receiving-detail-search">
            <Search :size="17" />
            <input
              v-model="detailQuery"
              placeholder="输入商品编码、商品名称、生产批号或系统批次"
              @keyup.enter="searchDetailItems"
            />
          </label>
          <button class="btn btn-primary btn-sm receiving-detail-search-button" type="button" :disabled="detailLoading" @click="searchDetailItems">
            <Search :size="16" />
            查询
          </button>
          <span class="receiving-detail-count">
            共 {{ detail.total }} 个品种，第 {{ detail.page }} / {{ Math.max(Math.ceil(detail.total / detailSize), 1) }} 页
          </span>
        </div>

        <div class="receiving-detail-table-wrap">
          <table class="receiving-detail-table">
            <thead>
              <tr>
                <th>序号</th>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格型号</th>
                <th>生产批号</th>
                <th>UDI</th>
                <th>生产日期</th>
                <th>有效期</th>
                <th>收货数量</th>
                <th>合格数量</th>
                <th>不合格数量</th>
                <th>批次单价</th>
                <th>金额</th>
                <th>系统批次</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="detailLoading">
                <td colspan="14" class="sheet-empty">正在加载明细...</td>
              </tr>
              <tr v-for="(item, index) in detail.items" v-else :key="`${item.productCode}-${index}`">
                <td>{{ (detail.page - 1) * detail.size + index + 1 }}</td>
                <td>{{ item.productCode }}</td>
                <td>{{ item.productName }}</td>
                <td>{{ item.specModel || '-' }}</td>
                <td>{{ item.productionBatchNo || '-' }}</td>
                <td>{{ item.udiCode || '-' }}</td>
                <td>{{ item.productionDate || '-' }}</td>
                <td>{{ item.expireDate || '-' }}</td>
                <td>{{ item.quantity }}</td>
                <td>{{ item.qualifiedQuantity }}</td>
                <td>{{ item.unqualifiedQuantity }}</td>
                <td>¥ {{ Number(item.batchUnitPrice).toFixed(2) }}</td>
                <td>¥ {{ Number(item.amount).toFixed(2) }}</td>
                <td>{{ item.systemBatchNo || '待审核生成' }}</td>
              </tr>
              <tr v-if="!detailLoading && !detail.items.length">
                <td colspan="14" class="sheet-empty">暂无验收明细</td>
              </tr>
            </tbody>
          </table>
        </div>

        <PaginationControls
          :page="detail.page"
          :size="detailSize"
          :total="detail.total"
          :loading="detailLoading"
          @change-page="changeDetailPage"
          @change-size="changeDetailPageSize"
        />
      </section>
    </div>
  </section>
</template>
