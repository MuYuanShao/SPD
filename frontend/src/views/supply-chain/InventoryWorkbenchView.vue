<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CheckCircle2, ClipboardCheck, History, PackageSearch, RefreshCw, Save, Search, SlidersHorizontal } from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import {
  approveBatchPriceAdjustment,
  approveStocktaking,
  createBatchPriceAdjustment,
  createStocktaking,
  fetchBatchPriceAdjustments,
  fetchInventoryBalances,
  fetchInventoryEvents,
  fetchStocktakingList,
  type InventoryBalanceRow
} from '../../api/inventory'
import { formatBusinessText, formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'

const route = useRoute()
const balances = ref<InventoryBalanceRow[]>([])
const events = ref<Record<string, unknown>[]>([])
const stocktakingRows = ref<Record<string, unknown>[]>([])
const priceRows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const message = ref('')
const inventoryPagination = reactive({
  balances: { page: 1, size: 20, total: 0 },
  events: { page: 1, size: 20, total: 0 },
  stocktaking: { page: 1, size: 20, total: 0 },
  price: { page: 1, size: 20, total: 0 }
})
const query = reactive({
  warehouseName: '',
  productCode: '',
  productName: '',
  systemBatchNo: ''
})
const stocktakingForm = reactive({
  warehouseName: '',
  systemBatchNo: '',
  actualQty: 0,
  reason: ''
})
const priceForm = reactive({
  systemBatchNo: '',
  newUnitPrice: 0,
  reason: ''
})

const pageCode = computed(() => String(route.params.code))
const isInventoryManagement = computed(() => pageCode.value === 'inventory-management')
const isInventoryTransactionLedger = computed(() => pageCode.value === 'inventory-events')
const mode = computed(() => {
  if (isInventoryTransactionLedger.value) return 'events'
  if (pageCode.value === 'stocktaking-management') return 'stocktaking'
  if (pageCode.value === 'batch-price-adjustment') return 'price'
  return 'inventory'
})
const title = computed(() => {
  if (mode.value === 'events') return '库存交易流水'
  if (mode.value === 'stocktaking') return '盘点管理'
  if (mode.value === 'price') return '价格调整'
  return '库存管理'
})
const subtitle = computed(() => {
  if (mode.value === 'events') return '单独追踪入库、出库、盘点、调价等库存事件，保留账务追溯链路。'
  if (mode.value === 'stocktaking') return '盘点复核后生成盘盈/盘亏库存事件，禁止形成负库存。'
  if (mode.value === 'price') return '批次调价只影响未消耗库存价值，不覆盖历史消耗、结算和计费事实。'
  return '全院库存余额集中展示，按库房、商品、系统批次查看可用、锁定和隔离库存。'
})
async function loadData() {
  loading.value = true
  try {
    if (mode.value === 'events') {
      const eventData = await fetchInventoryEvents({
        systemBatchNo: query.systemBatchNo,
        page: String(inventoryPagination.events.page),
        size: String(inventoryPagination.events.size)
      })
      events.value = eventData.rows
      inventoryPagination.events.total = eventData.total
      return
    }

    const balanceData = await fetchInventoryBalances({
      ...query,
      page: String(inventoryPagination.balances.page),
      size: String(inventoryPagination.balances.size)
    })
    balances.value = balanceData.rows
    inventoryPagination.balances.total = balanceData.total


    if (mode.value === 'stocktaking') {
      const stocktakingData = await fetchStocktakingList({
        page: String(inventoryPagination.stocktaking.page),
        size: String(inventoryPagination.stocktaking.size)
      })
      stocktakingRows.value = stocktakingData.rows
      inventoryPagination.stocktaking.total = stocktakingData.total
      return
    }

    if (mode.value === 'price') {
      const priceData = await fetchBatchPriceAdjustments({
        page: String(inventoryPagination.price.page),
        size: String(inventoryPagination.price.size)
      })
      priceRows.value = priceData.rows
      inventoryPagination.price.total = priceData.total
    }
  } finally {
    loading.value = false
  }
}

async function changeInventoryPage(key: keyof typeof inventoryPagination, page: number) {
  const state = inventoryPagination[key]
  const totalPages = Math.max(Math.ceil(state.total / Math.max(state.size, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === state.page) return
  state.page = nextPage
  await loadData()
}

async function changeInventoryPageSize(key: keyof typeof inventoryPagination, size: number) {
  const state = inventoryPagination[key]
  if (size === state.size) return
  state.size = size
  state.page = 1
  await loadData()
}

function fillFromBalance(row: InventoryBalanceRow) {
  stocktakingForm.warehouseName = row.warehouseName
  stocktakingForm.systemBatchNo = row.systemBatchNo
  stocktakingForm.actualQty = Number(row.availableQty)
  priceForm.systemBatchNo = row.systemBatchNo
  priceForm.newUnitPrice = Number(row.batchUnitPrice)
}

async function submitStocktaking() {
  const result = await createStocktaking(stocktakingForm)
  message.value = `盘点单已创建：${result.stocktakingNo}，差异 ${result.diffQty}`
  await loadData()
}

async function approveStocktakingRow(no: string) {
  const result = await approveStocktaking(no)
  message.value = `${result.stocktakingNo} 已复核通过并生成库存事件`
  await loadData()
}

async function submitPriceAdjustment() {
  const result = await createBatchPriceAdjustment(priceForm)
  message.value = `批次调价单已创建：${result.adjustmentNo}，影响库存 ${result.affectedQty}`
  await loadData()
}

async function approvePriceRow(no: string) {
  const result = await approveBatchPriceAdjustment(no)
  message.value = `${result.adjustmentNo} 已审批通过`
  await loadData()
}

onMounted(loadData)
watch(mode, () => {
  inventoryPagination.balances.page = 1
  inventoryPagination.events.page = 1
  inventoryPagination.stocktaking.page = 1
  inventoryPagination.price.page = 1
  loadData()
})
</script>

<template>
  <section class="purchase-page">
    <div class="breadcrumb-line">
      <span>{{ mode === 'price' ? '结算与财务' : '供应链业务' }}</span>
      <strong>{{ title }}</strong>
    </div>

    <div class="detail-heading">
      <div>
        <p>库存账务</p>
        <h2>{{ title }}</h2>
        <small>{{ subtitle }}</small>
      </div>
      <button class="btn" type="button" @click="loadData">
        <RefreshCw :size="17" />
        刷新
      </button>
    </div>

    <p v-if="message" class="inline-message">{{ message }}</p>

    <section v-if="isInventoryManagement" class="hospital-catalog-panel">
      <div class="hospital-action-row">
        <button class="btn" type="button" @click="loadData">
          <Search :size="17" />
          查询
        </button>
      </div>
      <div class="hospital-query-grid purchase-query-grid">
        <label><span>库房</span><input v-model="query.warehouseName" placeholder="模糊查询库房" /></label>
        <label><span>商品编码</span><input v-model="query.productCode" placeholder="商品编码" /></label>
        <label><span>商品名称</span><input v-model="query.productName" placeholder="商品名称" /></label>
        <label><span>系统批次</span><input v-model="query.systemBatchNo" placeholder="系统批次号" /></label>
        <button class="btn btn-primary" type="button" @click="loadData">
          <Search :size="18" />
          查询
        </button>
      </div>

      <div class="table-scroll">
        <table class="master-table purchase-table">
          <thead>
            <tr>
              <th>库房</th>
              <th>商品编码</th>
              <th>商品名称</th>
              <th>系统批次</th>
              <th>生产批号</th>
              <th>效期</th>
              <th>批次单价</th>
              <th>可用</th>
              <th>锁定</th>
              <th>隔离</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="11" class="approval-empty">正在加载库存...</td>
            </tr>
            <tr v-for="row in balances" v-else :key="row.balanceId">
              <td>{{ row.warehouseName }}</td>
              <td>{{ row.productCode }}</td>
              <td>{{ row.productName }}</td>
              <td>{{ row.systemBatchNo }}</td>
              <td>{{ row.productionBatchNo || '-' }}</td>
              <td>{{ row.expireDate || '-' }}</td>
              <td>¥ {{ Number(row.batchUnitPrice).toFixed(2) }}</td>
              <td>{{ row.availableQty }}</td>
              <td>{{ row.lockedQty }}</td>
              <td>{{ row.isolatedQty }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" class="btn-text" @click="fillFromBalance(row)">
                    <SlidersHorizontal :size="15" />
                    带入
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="inventoryPagination.balances.page"
        :size="inventoryPagination.balances.size"
        :total="inventoryPagination.balances.total"
        :loading="loading"
        @change-page="changeInventoryPage('balances', $event)"
        @change-size="changeInventoryPageSize('balances', $event)"
      />
    </section>

    <section v-if="isInventoryTransactionLedger" class="hospital-catalog-panel">
      <div class="hospital-query-grid purchase-query-grid">
        <label><span>系统批次</span><input v-model="query.systemBatchNo" placeholder="系统批次号" /></label>
        <button class="btn btn-primary" type="button" @click="loadData">
          <Search :size="18" />
          查询
        </button>
      </div>
      <div class="section-title">
        <History :size="20" />
        <h3>库存交易流水</h3>
      </div>
      <div class="table-scroll">
        <table class="master-table purchase-detail-table">
          <thead>
            <tr>
              <th>事件编号</th>
              <th>事件类型</th>
              <th>商品</th>
              <th>系统批次</th>
              <th>数量变化</th>
              <th>变化后</th>
              <th>时间</th>
              <th>备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in events" :key="String(row.eventNo)">
              <td>{{ row.eventNo }}</td>
              <td>{{ formatBusinessText(row.eventType) }}</td>
              <td>{{ row.productName }}</td>
              <td>{{ row.systemBatchNo }}</td>
              <td>{{ row.qtyChange }}</td>
              <td>{{ row.qtyAfter }}</td>
              <td>{{ row.eventTime }}</td>
              <td>{{ formatRemarkText(row.remark) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="inventoryPagination.events.page"
        :size="inventoryPagination.events.size"
        :total="inventoryPagination.events.total"
        :loading="loading"
        @change-page="changeInventoryPage('events', $event)"
        @change-size="changeInventoryPageSize('events', $event)"
      />
    </section>

    <section v-if="mode === 'stocktaking'" class="hospital-catalog-panel">
      <div class="section-title">
        <ClipboardCheck :size="20" />
        <h3>新增盘点单</h3>
      </div>
      <div class="hospital-query-grid purchase-query-grid">
        <label><span>盘点库房</span><input v-model="stocktakingForm.warehouseName" /></label>
        <label><span>系统批次</span><input v-model="stocktakingForm.systemBatchNo" /></label>
        <label><span>实盘数量</span><input v-model.number="stocktakingForm.actualQty" type="number" /></label>
        <label><span>差异原因</span><input v-model="stocktakingForm.reason" /></label>
        <button class="btn btn-primary" type="button" @click="submitStocktaking">
          <Save :size="18" />
          保存
        </button>
      </div>
      <div class="table-scroll">
        <table class="master-table purchase-detail-table">
          <thead><tr><th>盘点单号</th><th>库房</th><th>差异</th><th>状态</th><th>原因</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in stocktakingRows" :key="String(row.stocktakingNo)">
              <td>{{ row.stocktakingNo }}</td>
              <td>{{ row.warehouseName }}</td>
              <td>{{ row.diffQty }}</td>
              <td>{{ formatStatusText(row.status) }}</td>
              <td>{{ row.reason || '-' }}</td>
              <td><button v-if="row.status === 'draft'" class="btn btn-primary btn-sm" type="button" @click="approveStocktakingRow(String(row.stocktakingNo))"><CheckCircle2 :size="16" /> 复核</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="inventoryPagination.stocktaking.page"
        :size="inventoryPagination.stocktaking.size"
        :total="inventoryPagination.stocktaking.total"
        :loading="loading"
        @change-page="changeInventoryPage('stocktaking', $event)"
        @change-size="changeInventoryPageSize('stocktaking', $event)"
      />
    </section>

    <section v-if="mode === 'price'" class="hospital-catalog-panel">
      <div class="section-title">
        <PackageSearch :size="20" />
        <h3>批次调价</h3>
      </div>
      <div class="hospital-query-grid purchase-query-grid">
        <label><span>系统批次</span><input v-model="priceForm.systemBatchNo" /></label>
        <label><span>新批次单价</span><input v-model.number="priceForm.newUnitPrice" type="number" step="0.01" /></label>
        <label class="wide-field"><span>调价原因</span><input v-model="priceForm.reason" /></label>
        <button class="btn btn-primary" type="button" @click="submitPriceAdjustment">
          <Save :size="18" />
          保存
        </button>
      </div>
      <div class="table-scroll">
        <table class="master-table purchase-detail-table">
          <thead><tr><th>调价单号</th><th>系统批次</th><th>原价</th><th>新价</th><th>影响数量</th><th>状态</th><th>原因</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in priceRows" :key="String(row.adjustmentNo)">
              <td>{{ row.adjustmentNo }}</td>
              <td>{{ row.systemBatchNo }}</td>
              <td>¥ {{ Number(row.oldUnitPrice).toFixed(2) }}</td>
              <td>¥ {{ Number(row.newUnitPrice).toFixed(2) }}</td>
              <td>{{ row.affectedQty }}</td>
              <td>{{ formatStatusText(row.status) }}</td>
              <td>{{ row.reason || '-' }}</td>
              <td><button v-if="row.status === 'draft'" class="btn btn-primary btn-sm" type="button" @click="approvePriceRow(String(row.adjustmentNo))"><CheckCircle2 :size="16" /> 审批</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="inventoryPagination.price.page"
        :size="inventoryPagination.price.size"
        :total="inventoryPagination.price.total"
        :loading="loading"
        @change-page="changeInventoryPage('price', $event)"
        @change-size="changeInventoryPageSize('price', $event)"
      />
    </section>
  </section>
</template>
