<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
  Settings,
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
const receivingTypeOptions = ref<FieldOption[]>([])

async function loadReceivingTypeOptions() {
  try {
    const options = await fetchFieldOptions('receiving_type')
    receivingTypeOptions.value = options.filter((item) => item.status === 1)
  } catch {
    receivingTypeOptions.value = []
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
  openCreateModal,
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

const { fillFromPurchaseOrder, submitCreate, submitCreateAndContinue, openEditModal } =
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

const stats = computed(() => [
  { label: '收货单', value: summary.value.totalReceipts ?? rows.value.length },
  { label: '待验收', value: summary.value.draftCount ?? 0 },
  { label: '已入库', value: summary.value.approvedCount ?? 0 },
  { label: '本页数量', value: rows.value.reduce((sum, row) => sum + Number(row.receiveQuantity || 0), 0) }
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
    const [listData, optionData] = await Promise.all([
      fetchReceivingOrders({
        ...query,
        statusGroup: activeTab.value,
        page: String(currentPage.value),
        size: String(pageSize.value)
      }),
      fetchReceivingOptions()
    ])
    rows.value = listData.rows
    totalItems.value = listData.total
    tabPageState[activeTab.value] = { page: currentPage.value, size: pageSize.value }
    summary.value = listData.summary ?? {}
    purchaseOrders.value = optionData.purchaseOrders
    warehouses.value = optionData.warehouses
    products.value = optionData.products
    suppliers.value = optionData.suppliers
  } finally {
    loading.value = false
  }
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
    const result = await updateReceivingAction(row.receivingNo, action, action === 'approve' ? '验收通过' : '拒收')
    message.value =
      result.status === 'approved'
        ? `${row.receivingNo} 已审核入库，系统批次和库存余额已生成`
        : `${row.receivingNo} 已拒收`
    await loadData()
  } catch (error) {
    message.value = error instanceof Error ? error.message : `${row.receivingNo} 操作失败`
  } finally {
    actionLoadingNo.value = ''
  }
}

onMounted(() => {
  loadData()
  loadReceivingTypeOptions()
})
</script>

<template>
  <section class="purchase-page receiving-page">
    <div class="breadcrumb-line">
      <span>供应链业务</span>
      <strong>收货验收</strong>
    </div>

    <div class="detail-heading">
      <div>
        <p>验收入库与系统批次</p>
        <h2>收货验收</h2>
        <small>审核通过时读取最新医院目录采购价生成批次单价；订单价只做价差提醒，不阻断入库。</small>
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
        <button class="btn btn-primary" type="button" @click="openCreateModal">
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
              <td colspan="10" class="approval-empty">正在加载收货验收单...</td>
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
                  <button v-if="row.receivingStatus === 'draft'" type="button" class="btn-text" @click="openEditModal(row)">
                    <Pencil :size="15" />
                    修改
                  </button>
                  <button
                    v-if="row.receivingStatus === 'draft'"
                    type="button"
                    class="btn-text"
                    :disabled="Boolean(actionLoadingNo)"
                    @click="runAction(row, 'approve')">
                    <CheckCircle2 :size="15" />
                    {{ actionLoadingNo === `${row.receivingNo}:approve` ? '处理中' : '审核入库' }}
                  </button>
                  <button
                    v-if="row.receivingStatus === 'draft'"
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
              <td colspan="10" class="approval-empty">
                {{ activeTab === 'pending' ? '暂无待收货单据' : '暂无已验收单据' }}
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

    <div v-if="showCreateModal" class="modal-mask receiving-modal-mask">
      <section class="supplier-modal purchase-modal receiving-create-modal">
        <div class="receiving-create-titlebar">
          <strong>{{ editingReceivingNo ? `修改 ${editingReceivingNo}` : '新增' }}</strong>
          <button type="button" class="btn-icon" title="关闭" @click="closeCreateModal"><X :size="18" /></button>
        </div>

        <div class="receiving-create-body">
          <div class="receiving-form-grid">
            <label class="required">
              <span>收货库房</span>
              <select v-model="form.warehouseName">
                <option value="">请选择收货库房</option>
                <option v-for="warehouse in warehouses" :key="warehouse.warehouseName" :value="warehouse.warehouseName">
                  {{ warehouse.warehouseName }}
                </option>
              </select>
            </label>
            <label class="required supplier-field">
              <span>配送商</span>
              <div class="supplier-select-wrapper">
                <input v-model="supplierSearchQuery" placeholder="输入供应商名称搜索" @focus="showSupplierDropdown = true" @blur="closeSupplierDropdown()" />
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
            <label>
              <span>采购订单</span>
              <select v-model="form.purchaseOrderNo" @change="fillFromPurchaseOrder">
                <option value="">无采购订单/临时收货</option>
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
            <label class="agent-check">
              <span>是否代理商</span>
              <input v-model="form.isAgent" type="checkbox" />
            </label>
            <label>
              <span>备注</span>
              <input v-model="form.remark" placeholder="填写本次收货备注" />
            </label>
          </div>

          <div class="receiving-entry-toolbar">
            <button type="button" class="teal-action" @click="addItem"><Plus :size="16" /> 新增</button>
            <button type="button" class="teal-action" @click="addItem"><CopyPlus :size="16" /> 批量新增</button>
            <button type="button" class="teal-action danger" @click="removeItem(form.items.length - 1)"><Trash2 :size="16" /> 删除</button>
            <button type="button" class="teal-action"><CopyPlus :size="16" /> 复制细单</button>
            <label class="barcode-field">
              <span>商品码扫描</span>
              <input placeholder="扫描或录入商品码" />
            </label>
            <button type="button" class="teal-action"><Settings :size="16" /> 配置工具条</button>
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
                  <th>医院单位数量</th>
                  <th>验收数量</th>
                  <th>合格数量</th>
                  <th>不合格数量</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in form.items" :key="index">
                  <td><input type="checkbox" /></td>
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
                  <td colspan="14" class="sheet-empty">无匹配数据</td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>

        <div class="receiving-create-footer">
          <div class="receiving-total">
            <span>总数量:{{ createTotals.quantity.toFixed(2) }}</span>
            <span>合格:{{ createTotals.qualifiedQuantity.toFixed(2) }}</span>
            <span>不合格:{{ createTotals.unqualifiedQuantity.toFixed(2) }}</span>
          </div>
          <div class="receiving-footer-actions">
            <button class="teal-action solid" type="button" @click="submitCreate">
              <Save :size="16" />
              保存
            </button>
            <button v-if="!editingReceivingNo" class="teal-action solid" type="button" @click="submitCreateAndContinue">
              <Save :size="16" />
              保存并继续
            </button>
            <button class="teal-action dark" type="button" @click="closeCreateModal">
              <X :size="16" />
              取消
            </button>
          </div>
        </div>
      </section>
    </div>

    <div v-if="detail" class="modal-mask receiving-detail-mask">
      <section class="supplier-modal purchase-modal receiving-detail-modal">
        <header class="receiving-detail-header">
          <div>
            <p>收货验收单</p>
            <h3>{{ detail.order.receivingNo }}</h3>
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

