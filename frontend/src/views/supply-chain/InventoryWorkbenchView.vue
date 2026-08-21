<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CheckCircle2, ClipboardCheck, History, PackageSearch, RefreshCw, Save, Search } from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import {
  approveBatchPriceAdjustment,
  approveStocktaking,
  createBatchPriceAdjustment,
  createStocktaking,
  fetchBatchPriceAdjustments,
  fetchInventoryBalances,
  fetchInventoryEvents,
  fetchQuotaPackageStock,
  fetchStocktakingList,
  fetchUniqueCodeStock,
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
  quotaStock: { page: 1, size: 20, total: 0 },
  codeStock: { page: 1, size: 20, total: 0 },
  events: { page: 1, size: 20, total: 0 },
  stocktaking: { page: 1, size: 20, total: 0 },
  price: { page: 1, size: 20, total: 0 }
})
const inventoryTab = ref<'summary' | 'quota' | 'unique'>('summary')
const inventoryTabs = [
  { key: 'summary', label: '库存汇总查询' },
  { key: 'quota', label: '定数包库存查询' },
  { key: 'unique', label: '唯一码查询' }
] as const
const quotaStockRows = ref<Record<string, unknown>[]>([])
const codeStockRows = ref<Record<string, unknown>[]>([])
const quotaQuery = reactive({
  warehouseName: '',
  deptName: '',
  packageName: '',
  productName: ''
})
const codeQuery = reactive({
  deptName: '',
  warehouseName: '',
  productCode: '',
  productName: '',
  batchNo: '',
  uniqueCode: '',
  udiCode: ''
})
const query = reactive({
  deptName: '',
  warehouseName: '',
  productCode: '',
  productName: '',
  batchNo: '',
  productionBatchNo: '',
  manufacturerName: '',
  supplierName: '',
  startTime: '',
  endTime: '',
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
        deptName: query.deptName,
        warehouseName: query.warehouseName,
        productCode: query.productCode,
        productName: query.productName,
        batchNo: query.batchNo,
        productionBatchNo: query.productionBatchNo,
        manufacturerName: query.manufacturerName,
        supplierName: query.supplierName,
        startTime: query.startTime,
        endTime: query.endTime,
        page: String(inventoryPagination.events.page),
        size: String(inventoryPagination.events.size)
      })
      events.value = eventData.rows
      inventoryPagination.events.total = eventData.total
      return
    }

    if (mode.value === 'inventory') {
      if (inventoryTab.value === 'quota') {
        const data = await fetchQuotaPackageStock({
          ...quotaQuery,
          page: String(inventoryPagination.quotaStock.page),
          size: String(inventoryPagination.quotaStock.size)
        })
        quotaStockRows.value = data.rows
        inventoryPagination.quotaStock.total = data.total
        return
      }
      if (inventoryTab.value === 'unique') {
        const data = await fetchUniqueCodeStock({
          ...codeQuery,
          page: String(inventoryPagination.codeStock.page),
          size: String(inventoryPagination.codeStock.size)
        })
        codeStockRows.value = data.rows
        inventoryPagination.codeStock.total = data.total
        return
      }
    }

    const balanceData = await fetchInventoryBalances({
      deptName: query.deptName,
      warehouseName: query.warehouseName,
      productCode: query.productCode,
      productName: query.productName,
      systemBatchNo: query.systemBatchNo,
      manufacturerName: query.manufacturerName,
      supplierName: query.supplierName,
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

function resetEventQuery() {
  query.deptName = ''
  query.warehouseName = ''
  query.productCode = ''
  query.productName = ''
  query.batchNo = ''
  query.productionBatchNo = ''
  query.manufacturerName = ''
  query.supplierName = ''
  query.startTime = ''
  query.endTime = ''
  inventoryPagination.events.page = 1
  void loadData()
}

function changeInventoryTab(tab: 'summary' | 'quota' | 'unique') {
  if (tab === inventoryTab.value) return
  inventoryTab.value = tab
  inventoryPagination.balances.page = 1
  inventoryPagination.quotaStock.page = 1
  inventoryPagination.codeStock.page = 1
  void loadData()
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
      <div class="subnav-tabs inventory-query-tabs" role="tablist" aria-label="库存查询类型">
        <button
          v-for="tab in inventoryTabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="inventoryTab === tab.key"
          :class="{ active: inventoryTab === tab.key }"
          @click="changeInventoryTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 库存汇总查询 -->
      <template v-if="inventoryTab === 'summary'">
        <form class="hospital-query-grid purchase-query-grid" role="search" @submit.prevent="loadData">
          <label><span>库房</span><input v-model.trim="query.warehouseName" placeholder="模糊查询库房" /></label>
          <label><span>科室</span><input v-model.trim="query.deptName" placeholder="科室名称" /></label>
          <label><span>商品编码</span><input v-model.trim="query.productCode" placeholder="商品编码" /></label>
          <label><span>商品名称</span><input v-model.trim="query.productName" placeholder="商品名称" /></label>
          <label><span>厂家</span><input v-model.trim="query.manufacturerName" placeholder="厂家名称" /></label>
          <label><span>供应商</span><input v-model.trim="query.supplierName" placeholder="供应商名称" /></label>
          <button class="btn btn-primary" type="submit">
            <Search :size="18" />
            查询
          </button>
        </form>

        <div class="table-scroll">
          <table class="master-table purchase-table">
            <thead>
              <tr>
                <th>库房</th>
                <th>科室</th>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格型号</th>
                <th>注册证号</th>
                <th>单价</th>
                <th>单位</th>
                <th>数量</th>
                <th>金额</th>
                <th>厂家</th>
                <th>供应商</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="12" class="approval-empty">正在加载库存...</td>
              </tr>
              <tr v-for="row in balances" v-else :key="row.balanceId">
                <td>{{ row.warehouseName }}</td>
                <td>{{ row.deptName || '-' }}</td>
                <td class="code-cell">{{ row.productCode }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.specModel || '-' }}</td>
                <td>{{ row.registrationNo || '-' }}</td>
                <td class="number-cell">¥ {{ Number(row.unitPrice).toFixed(2) }}</td>
                <td>{{ row.unit || '-' }}</td>
                <td class="number-cell">{{ row.qty }}</td>
                <td class="number-cell">¥ {{ Number(row.amount).toFixed(2) }}</td>
                <td>{{ row.manufacturerName || '-' }}</td>
                <td>{{ row.supplierName || '-' }}</td>
              </tr>
              <tr v-if="!balances.length && !loading">
                <td colspan="12" class="approval-empty">暂无库存汇总数据</td>
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
      </template>

      <!-- 定数包库存查询 -->
      <template v-else-if="inventoryTab === 'quota'">
        <form class="hospital-query-grid purchase-query-grid" role="search" @submit.prevent="loadData">
          <label><span>库房</span><input v-model.trim="quotaQuery.warehouseName" placeholder="模糊查询库房" /></label>
          <label><span>科室</span><input v-model.trim="quotaQuery.deptName" placeholder="科室名称" /></label>
          <label><span>定数包名称</span><input v-model.trim="quotaQuery.packageName" placeholder="定数包名称" /></label>
          <label><span>商品名称</span><input v-model.trim="quotaQuery.productName" placeholder="商品名称" /></label>
          <button class="btn btn-primary" type="submit">
            <Search :size="18" />
            查询
          </button>
        </form>

        <div class="table-scroll">
          <table class="master-table purchase-table">
            <thead>
              <tr>
                <th>库房</th>
                <th>科室</th>
                <th>定数包编码</th>
                <th>定数包名称</th>
                <th>规格型号</th>
                <th>注册证号</th>
                <th>单价</th>
                <th>单位</th>
                <th>定数包数量</th>
                <th>散货数量</th>
                <th>金额</th>
                <th>厂家</th>
                <th>供应商</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="13" class="approval-empty">正在加载定数包库存...</td>
              </tr>
              <tr v-for="(row, index) in quotaStockRows" v-else :key="index">
                <td>{{ row.warehouseName }}</td>
                <td>{{ row.deptName }}</td>
                <td class="code-cell">{{ row.packageCode }}</td>
                <td>{{ row.packageName }}</td>
                <td>{{ row.specModel || '-' }}</td>
                <td>{{ row.registrationNo || '-' }}</td>
                <td class="number-cell">¥ {{ Number(row.unitPrice).toFixed(2) }}</td>
                <td>{{ row.unit || '-' }}</td>
                <td class="number-cell">{{ row.packageCount }}</td>
                <td class="number-cell">{{ row.looseQty }}</td>
                <td class="number-cell">¥ {{ Number(row.amount).toFixed(2) }}</td>
                <td>{{ row.manufacturerName }}</td>
                <td>{{ row.supplierName }}</td>
              </tr>
              <tr v-if="!quotaStockRows.length && !loading">
                <td colspan="13" class="approval-empty">暂无定数包库存数据</td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="inventoryPagination.quotaStock.page"
          :size="inventoryPagination.quotaStock.size"
          :total="inventoryPagination.quotaStock.total"
          :loading="loading"
          @change-page="changeInventoryPage('quotaStock', $event)"
          @change-size="changeInventoryPageSize('quotaStock', $event)"
        />
      </template>

      <!-- 唯一码查询 -->
      <template v-else>
        <form class="hospital-query-grid purchase-query-grid" role="search" @submit.prevent="loadData">
          <label><span>科室</span><input v-model.trim="codeQuery.deptName" placeholder="科室名称" /></label>
          <label><span>库房</span><input v-model.trim="codeQuery.warehouseName" placeholder="库房名称" /></label>
          <label><span>商品编码</span><input v-model.trim="codeQuery.productCode" placeholder="商品编码" /></label>
          <label><span>商品名称</span><input v-model.trim="codeQuery.productName" placeholder="商品名称" /></label>
          <label><span>批号</span><input v-model.trim="codeQuery.batchNo" placeholder="系统批号" /></label>
          <label><span>唯一码</span><input v-model.trim="codeQuery.uniqueCode" placeholder="唯一码" /></label>
          <label><span>UID码</span><input v-model.trim="codeQuery.udiCode" placeholder="UID码" /></label>
          <button class="btn btn-primary" type="submit">
            <Search :size="18" />
            查询
          </button>
        </form>

        <div class="table-scroll">
          <table class="master-table purchase-table">
            <thead>
              <tr>
                <th>科室</th>
                <th>库房</th>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格型号</th>
                <th>注册证号</th>
                <th>批号</th>
                <th>批次</th>
                <th>单价</th>
                <th>单位</th>
                <th>数量</th>
                <th>金额</th>
                <th>厂家</th>
                <th>供应商</th>
                <th>唯一码</th>
                <th>UID码</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="16" class="approval-empty">正在加载唯一码库存...</td>
              </tr>
              <tr v-for="(row, index) in codeStockRows" v-else :key="index">
                <td>{{ row.deptName }}</td>
                <td>{{ row.warehouseName }}</td>
                <td class="code-cell">{{ row.productCode }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.specModel || '-' }}</td>
                <td>{{ row.registrationNo || '-' }}</td>
                <td>{{ row.batchNo }}</td>
                <td>{{ row.productionBatchNo }}</td>
                <td class="number-cell">¥ {{ Number(row.unitPrice).toFixed(2) }}</td>
                <td>{{ row.unit || '-' }}</td>
                <td class="number-cell">{{ row.qty }}</td>
                <td class="number-cell">¥ {{ Number(row.amount).toFixed(2) }}</td>
                <td>{{ row.manufacturerName }}</td>
                <td>{{ row.supplierName }}</td>
                <td class="code-cell">{{ row.uniqueCode }}</td>
                <td class="code-cell">{{ row.udiCode }}</td>
              </tr>
              <tr v-if="!codeStockRows.length && !loading">
                <td colspan="16" class="approval-empty">暂无唯一码库存数据</td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :page="inventoryPagination.codeStock.page"
          :size="inventoryPagination.codeStock.size"
          :total="inventoryPagination.codeStock.total"
          :loading="loading"
          @change-page="changeInventoryPage('codeStock', $event)"
          @change-size="changeInventoryPageSize('codeStock', $event)"
        />
      </template>
    </section>

    <section v-if="isInventoryTransactionLedger" class="hospital-catalog-panel">
      <form class="hospital-query-grid purchase-query-grid" role="search" @submit.prevent="loadData">
        <label><span>科室</span><input v-model.trim="query.deptName" placeholder="科室名称" /></label>
        <label><span>库房</span><input v-model.trim="query.warehouseName" placeholder="库房名称" /></label>
        <label><span>商品编码</span><input v-model.trim="query.productCode" placeholder="商品编码" /></label>
        <label><span>商品名称</span><input v-model.trim="query.productName" placeholder="商品名称" /></label>
        <label><span>批号</span><input v-model.trim="query.batchNo" placeholder="系统批号" /></label>
        <label><span>批次</span><input v-model.trim="query.productionBatchNo" placeholder="生产批次" /></label>
        <label><span>厂家</span><input v-model.trim="query.manufacturerName" placeholder="厂家名称" /></label>
        <label><span>供应商</span><input v-model.trim="query.supplierName" placeholder="供应商名称" /></label>
        <label><span>开始日期</span><input v-model="query.startTime" type="date" /></label>
        <label><span>结束日期</span><input v-model="query.endTime" type="date" /></label>
        <div class="hospital-query-actions">
          <button class="btn btn-primary" type="submit">
            <Search :size="18" />
            查询
          </button>
          <button class="btn" type="button" @click="resetEventQuery">重置</button>
        </div>
      </form>
      <div class="section-title">
        <History :size="20" />
        <h3>库存交易流水</h3>
        <span class="muted-hint">按科室、发生时间排序；批次单价 × 数量即金额</span>
      </div>
      <div class="table-scroll inventory-events-scroll">
        <table class="master-table purchase-detail-table inventory-events-table">
          <thead>
            <tr>
              <th>科室</th>
              <th>库房</th>
              <th>商品编码</th>
              <th>商品名称</th>
              <th>规格型号</th>
              <th>注册证号</th>
              <th>批号</th>
              <th>批次</th>
              <th>单价</th>
              <th>单位</th>
              <th>数量</th>
              <th>金额</th>
              <th>厂家</th>
              <th>供应商</th>
              <th>定数包码/唯一码</th>
              <th>UID码</th>
              <th>事件类型</th>
              <th>发生时间</th>
              <th>备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in events" :key="String(row.eventNo)">
              <td>{{ row.deptName }}</td>
              <td>{{ row.warehouseName }}</td>
              <td class="code-cell">{{ row.productCode }}</td>
              <td>{{ row.productName }}</td>
              <td>{{ row.specModel }}</td>
              <td>{{ row.registrationNo }}</td>
              <td>{{ row.batchNo }}</td>
              <td>{{ row.productionBatchNo }}</td>
              <td class="number-cell">{{ row.unitPrice }}</td>
              <td>{{ row.unit }}</td>
              <td class="number-cell" :class="{ 'qty-out': Number(row.qtyChange) < 0 }">{{ row.qtyChange }}</td>
              <td class="number-cell">{{ row.amount }}</td>
              <td>{{ row.manufacturerName }}</td>
              <td>{{ row.supplierName }}</td>
              <td>{{ row.traceCode }}</td>
              <td>{{ row.udiCode }}</td>
              <td><span class="event-type-chip">{{ formatBusinessText(row.eventType) }}</span></td>
              <td class="time-cell">{{ row.eventTime }}</td>
              <td class="remark-cell">{{ formatRemarkText(row.remark) }}</td>
            </tr>
            <tr v-if="!events.length && !loading">
              <td class="approval-empty" colspan="19">暂无库存交易流水</td>
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
