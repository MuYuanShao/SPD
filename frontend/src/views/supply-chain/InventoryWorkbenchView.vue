<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Teleport } from 'vue'
import { useRoute } from 'vue-router'
import { CheckCircle2, ClipboardCheck, Eye, PackageSearch, Plus, RefreshCw, Save, Search, X } from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import InventoryEventLedger from '../../components/inventory/InventoryEventLedger.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import {
  approveBatchPriceAdjustment,
  approveStocktaking,
  createBatchPriceAdjustment,
  createStocktakingSheet,
  fetchBatchPriceAdjustments,
  fetchInventoryBalances,
  fetchQuotaPackageStock,
  previewStocktakingSheet,
  fetchStocktakingItems,
  fetchStocktakingList,
  fetchUniqueCodeStock,
  updateStocktakingItems,
  type InventoryBalanceRow,
  type StocktakingSheetItem
} from '../../api/inventory'
import { fetchClosureOptions, type ClosureOptions } from '../../api/operationalClosure'
import { useNotify } from '../../composables/useNotify'
import { formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'

const notify = useNotify()

const route = useRoute()
const balances = ref<InventoryBalanceRow[]>([])
const stocktakingRows = ref<Record<string, unknown>[]>([])
const stocktakingScopeOptions = [
  { key: 'highValue', label: '高值耗材' },
  { key: 'chargeable', label: '可收费耗材' },
  { key: 'nonChargeable', label: '不可收费耗材' },
  { key: 'quotaPackage', label: '定数包' }
] as const
const stocktakingSheetOpen = ref(false)
const stocktakingSheetForm = reactive({
  warehouseName: '',
  deptName: '',
  scopes: [] as string[]
})
const stocktakingOptions = ref<ClosureOptions>({ departments: [], warehouses: [], products: [], balances: [] })
const stocktakingDetailOpen = ref(false)
const stocktakingDetailNo = ref('')
const stocktakingDetailRows = ref<StocktakingSheetItem[]>([])
const stocktakingDetailLoading = ref(false)
const stocktakingDetailSaving = ref(false)
const stocktakingPreviewRows = ref<StocktakingSheetItem[]>([])
const stocktakingPreviewLoading = ref(false)

/** 盘点明细差异数量 = 库存数量 - 盘点数量 */
function stocktakingRowDiff(row: StocktakingSheetItem) {
  if (row.actualQty == null) return null
  return Number(row.systemQty) - Number(row.actualQty)
}

function toggleSheetScope(key: string) {
  const scopes = new Set(stocktakingSheetForm.scopes)
  if (scopes.has(key)) {
    scopes.delete(key)
  } else {
    scopes.add(key)
  }
  stocktakingSheetForm.scopes = [...scopes]
  void loadStocktakingPreview()
}

async function loadStocktakingPreview() {
  stocktakingPreviewRows.value = []
  if (!stocktakingSheetForm.warehouseName || !stocktakingSheetForm.scopes.length) return
  stocktakingPreviewLoading.value = true
  try {
    const result = await previewStocktakingSheet({
      warehouseName: stocktakingSheetForm.warehouseName,
      deptName: stocktakingSheetForm.deptName || undefined,
      scopes: stocktakingSheetForm.scopes
    })
    stocktakingPreviewRows.value = result.rows
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '当前库存明细加载失败')
  } finally {
    stocktakingPreviewLoading.value = false
  }
}

const selectedScopeLabels = computed(() =>
  stocktakingSheetForm.scopes
    .map((key) => stocktakingScopeOptions.find((item) => item.key === key)?.label ?? key)
)

async function openStocktakingSheet() {
  if (!stocktakingOptions.value.warehouses.length) {
    try {
      stocktakingOptions.value = await fetchClosureOptions()
    } catch {
      stocktakingOptions.value = { departments: [], warehouses: [], products: [], balances: [] }
    }
  }
  stocktakingSheetForm.warehouseName = stocktakingOptions.value.warehouses[0]?.warehouseName || ''
  stocktakingSheetForm.deptName = stocktakingOptions.value.departments[0]?.deptName || ''
  stocktakingSheetForm.scopes = []
  stocktakingPreviewRows.value = []
  stocktakingSheetOpen.value = true
}

async function submitStocktakingSheet() {
  if (!stocktakingSheetForm.warehouseName) {
    notify.warning('请选择盘点库房')
    return
  }
  if (!stocktakingSheetForm.scopes.length) {
    notify.warning('请至少选择一个盘点商品范围')
    return
  }
  try {
    const result = await createStocktakingSheet({
      warehouseName: stocktakingSheetForm.warehouseName,
      deptName: stocktakingSheetForm.deptName || undefined,
      scopes: stocktakingSheetForm.scopes
    })
    stocktakingSheetOpen.value = false
    notify.success(`盘点表已生成：${result.stocktakingNo}，共 ${result.rowCount} 条盘点明细`)
    await loadData()
    await openStocktakingDetail(result.stocktakingNo)
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '盘点表生成失败')
  }
}

async function openStocktakingDetail(stocktakingNo: string) {
  stocktakingDetailOpen.value = true
  stocktakingDetailNo.value = stocktakingNo
  stocktakingDetailLoading.value = true
  stocktakingDetailRows.value = []
  try {
    const result = await fetchStocktakingItems(stocktakingNo)
    stocktakingDetailRows.value = result.rows
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '盘点明细加载失败')
  } finally {
    stocktakingDetailLoading.value = false
  }
}

async function saveStocktakingDetail() {
  const items = stocktakingDetailRows.value
    .filter((row) => row.actualQty != null)
    .map((row) => ({ itemId: row.itemId, actualQty: Number(row.actualQty) }))
  if (!items.length) {
    notify.warning('请至少填写一条盘点数量')
    return
  }
  stocktakingDetailSaving.value = true
  try {
    const result = await updateStocktakingItems(stocktakingDetailNo.value, items)
    notify.success(`盘点数量已保存：${result.stocktakingNo}，共 ${result.updatedRows} 条明细`)
    await loadData()
    await openStocktakingDetail(stocktakingDetailNo.value)
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '盘点数量保存失败')
  } finally {
    stocktakingDetailSaving.value = false
  }
}
const priceRows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const inventoryPagination = reactive({
  balances: { page: 1, size: 20, total: 0 },
  quotaStock: { page: 1, size: 20, total: 0 },
  codeStock: { page: 1, size: 20, total: 0 },
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
  systemBatchNo: '',
  transactionType: ''
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

/** 盘点单状态徽标配色：草稿待复核、已复核归档 */
function stocktakingTone(status: unknown) {
  if (String(status || '') === 'draft') return 'pending'
  return 'enabled'
}
async function loadData() {
  loading.value = true
  try {
    if (mode.value === 'events') return

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

function changeInventoryTab(tab: 'summary' | 'quota' | 'unique') {
  if (tab === inventoryTab.value) return
  inventoryTab.value = tab
  inventoryPagination.balances.page = 1
  inventoryPagination.quotaStock.page = 1
  inventoryPagination.codeStock.page = 1
  void loadData()
}

async function approveStocktakingRow(no: string) {
  const result = await approveStocktaking(no)
  notify.success(`${result.stocktakingNo} 已复核通过并生成库存事件`)
  await loadData()
}

async function submitPriceAdjustment() {
  const result = await createBatchPriceAdjustment(priceForm)
  notify.success(`批次调价单已创建：${result.adjustmentNo}，影响库存 ${result.affectedQty}`)
  await loadData()
}

async function approvePriceRow(no: string) {
  const result = await approveBatchPriceAdjustment(no)
  notify.success(`${result.adjustmentNo} 已审批通过`)
  await loadData()
}

onMounted(() => {
  if (!isInventoryTransactionLedger.value) void loadData()
})
watch(mode, () => {
  inventoryPagination.balances.page = 1
  inventoryPagination.stocktaking.page = 1
  inventoryPagination.price.page = 1
  if (!isInventoryTransactionLedger.value) void loadData()
})
</script>

<template>
  <section class="purchase-page inventory-page" :class="{ 'price-adjust-page': mode === 'price' }">
    <InventoryEventLedger v-if="isInventoryTransactionLedger" />

    <MdPageHeader
      v-else-if="mode === 'price'"
      crumb="结算与财务"
      :title="title"
      :description="subtitle"
      :loading="loading"
      @refresh="loadData"
    />

    <MdPageHeader
      v-else
      crumb="供应链业务"
      :title="title"
      :description="subtitle"
      :loading="loading"
      @refresh="loadData"
    />

    <section v-if="isInventoryManagement" class="inventory-workspace">
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
        <form class="hospital-query-grid inventory-query-grid" role="search" @submit.prevent="loadData">
          <label><span>库房</span><input v-model.trim="query.warehouseName" class="md-input" placeholder="模糊查询库房" /></label>
          <label><span>科室</span><input v-model.trim="query.deptName" class="md-input" placeholder="科室名称" /></label>
          <label><span>商品编码</span><input v-model.trim="query.productCode" class="md-input" placeholder="商品编码" /></label>
          <label><span>商品名称</span><input v-model.trim="query.productName" class="md-input" placeholder="商品名称" /></label>
          <label><span>厂家</span><input v-model.trim="query.manufacturerName" class="md-input" placeholder="厂家名称" /></label>
          <label><span>供应商</span><input v-model.trim="query.supplierName" class="md-input" placeholder="供应商名称" /></label>
          <button class="btn btn-primary md-btn md-btn-primary inventory-query-button" type="submit">
            <Search :size="16" />
            查询
          </button>
        </form>

        <div class="table-scroll inventory-list-scroll">
          <table class="master-table inventory-table">
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
                <td colspan="12" class="inventory-state-cell">正在加载库存...</td>
              </tr>
              <tr v-for="row in balances" v-else :key="`${row.warehouseName}|${row.productCode}`">
                <td>{{ row.warehouseName }}</td>
                <td>{{ row.deptName || '-' }}</td>
                <td class="inventory-code-cell">{{ row.productCode }}</td>
                <td class="inventory-product-cell">{{ row.productName }}</td>
                <td>{{ row.specModel || '-' }}</td>
                <td>{{ row.registrationNo || '-' }}</td>
                <td class="inventory-money-cell">¥ {{ Number(row.unitPrice).toFixed(2) }}</td>
                <td>{{ row.unit || '-' }}</td>
                <td class="inventory-qty-cell">{{ row.qty }}</td>
                <td class="inventory-money-cell">¥ {{ Number(row.amount).toFixed(2) }}</td>
                <td>{{ row.manufacturerName || '-' }}</td>
                <td>{{ row.supplierName || '-' }}</td>
              </tr>
              <tr v-if="!balances.length && !loading">
                <td colspan="12" class="inventory-state-cell">暂无库存汇总数据</td>
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
        <form class="hospital-query-grid inventory-query-grid" role="search" @submit.prevent="loadData">
          <label><span>库房</span><input v-model.trim="quotaQuery.warehouseName" class="md-input" placeholder="模糊查询库房" /></label>
          <label><span>科室</span><input v-model.trim="quotaQuery.deptName" class="md-input" placeholder="科室名称" /></label>
          <label><span>定数包名称</span><input v-model.trim="quotaQuery.packageName" class="md-input" placeholder="定数包名称" /></label>
          <label><span>商品名称</span><input v-model.trim="quotaQuery.productName" class="md-input" placeholder="商品名称" /></label>
          <button class="btn btn-primary md-btn md-btn-primary inventory-query-button" type="submit">
            <Search :size="16" />
            查询
          </button>
        </form>

        <div class="table-scroll inventory-list-scroll">
          <table class="master-table inventory-table">
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
                <td colspan="13" class="inventory-state-cell">正在加载定数包库存...</td>
              </tr>
              <tr v-for="(row, index) in quotaStockRows" v-else :key="index">
                <td>{{ row.warehouseName }}</td>
                <td>{{ row.deptName }}</td>
                <td class="inventory-code-cell">{{ row.packageCode }}</td>
                <td class="inventory-product-cell">{{ row.packageName }}</td>
                <td>{{ row.specModel || '-' }}</td>
                <td>{{ row.registrationNo || '-' }}</td>
                <td class="inventory-money-cell">¥ {{ Number(row.unitPrice).toFixed(2) }}</td>
                <td>{{ row.unit || '-' }}</td>
                <td class="inventory-qty-cell">{{ row.packageCount }}</td>
                <td class="inventory-qty-cell">{{ row.looseQty }}</td>
                <td class="inventory-money-cell">¥ {{ Number(row.amount).toFixed(2) }}</td>
                <td>{{ row.manufacturerName }}</td>
                <td>{{ row.supplierName }}</td>
              </tr>
              <tr v-if="!quotaStockRows.length && !loading">
                <td colspan="13" class="inventory-state-cell">暂无定数包库存数据</td>
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
        <form class="hospital-query-grid inventory-query-grid" role="search" @submit.prevent="loadData">
          <label><span>科室</span><input v-model.trim="codeQuery.deptName" class="md-input" placeholder="科室名称" /></label>
          <label><span>库房</span><input v-model.trim="codeQuery.warehouseName" class="md-input" placeholder="库房名称" /></label>
          <label><span>商品编码</span><input v-model.trim="codeQuery.productCode" class="md-input" placeholder="商品编码" /></label>
          <label><span>商品名称</span><input v-model.trim="codeQuery.productName" class="md-input" placeholder="商品名称" /></label>
          <label><span>批号</span><input v-model.trim="codeQuery.batchNo" class="md-input" placeholder="系统批号" /></label>
          <label><span>唯一码</span><input v-model.trim="codeQuery.uniqueCode" class="md-input" placeholder="唯一码" /></label>
          <label><span>UID码</span><input v-model.trim="codeQuery.udiCode" class="md-input" placeholder="UID码" /></label>
          <button class="btn btn-primary md-btn md-btn-primary inventory-query-button" type="submit">
            <Search :size="16" />
            查询
          </button>
        </form>

        <div class="table-scroll inventory-list-scroll">
          <table class="master-table inventory-table">
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
                <td colspan="16" class="inventory-state-cell">正在加载唯一码库存...</td>
              </tr>
              <tr v-for="(row, index) in codeStockRows" v-else :key="index">
                <td>{{ row.deptName }}</td>
                <td>{{ row.warehouseName }}</td>
                <td class="inventory-code-cell">{{ row.productCode }}</td>
                <td class="inventory-product-cell">{{ row.productName }}</td>
                <td>{{ row.specModel || '-' }}</td>
                <td>{{ row.registrationNo || '-' }}</td>
                <td class="inventory-code-cell">{{ row.batchNo }}</td>
                <td class="inventory-code-cell">{{ row.productionBatchNo }}</td>
                <td class="inventory-money-cell">¥ {{ Number(row.unitPrice).toFixed(2) }}</td>
                <td>{{ row.unit || '-' }}</td>
                <td class="inventory-qty-cell">{{ row.qty }}</td>
                <td class="inventory-money-cell">¥ {{ Number(row.amount).toFixed(2) }}</td>
                <td>{{ row.manufacturerName }}</td>
                <td>{{ row.supplierName }}</td>
                <td class="inventory-code-cell">{{ row.uniqueCode }}</td>
                <td class="inventory-code-cell">{{ row.udiCode }}</td>
              </tr>
              <tr v-if="!codeStockRows.length && !loading">
                <td colspan="16" class="inventory-state-cell">暂无唯一码库存数据</td>
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

    <section v-if="mode === 'stocktaking'" class="hospital-catalog-panel stocktaking-workspace">
      <div class="section-title stocktaking-title">
        <ClipboardCheck :size="20" />
        <h3>盘点管理</h3>
        <button class="btn btn-primary md-btn md-btn-primary stocktaking-create-btn" type="button" @click="openStocktakingSheet">
          <Plus :size="16" />
          新增盘点表
        </button>
      </div>
      <p class="stocktaking-hint">盘点复核后生成盘盈/盘亏库存事件，禁止形成负库存。</p>
      <div class="table-scroll stocktaking-list-scroll">
        <table class="master-table purchase-detail-table stocktaking-table">
          <thead><tr><th>盘点单号</th><th>库房</th><th>科室</th><th>类型</th><th class="numeric-cell">差异</th><th>状态</th><th>原因</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="stocktaking-state-cell">正在加载盘点表...</td>
            </tr>
            <tr v-if="!stocktakingRows.length && !loading">
              <td colspan="8" class="stocktaking-state-cell">暂无盘点表，点击右上角「新增盘点表」创建</td>
            </tr>
            <tr v-for="row in stocktakingRows" v-else :key="String(row.stocktakingNo)">
              <td class="stocktaking-no-cell">{{ row.stocktakingNo }}</td>
              <td>{{ row.warehouseName }}</td>
              <td>{{ row.deptName || '-' }}</td>
              <td>{{ row.stocktakingType || '-' }}</td>
              <td class="stocktaking-diff-cell">{{ row.diffQty ?? '-' }}</td>
              <td><span :class="['status-badge', stocktakingTone(row.status)]">{{ formatStatusText(row.status) }}</span></td>
              <td>{{ row.reason || '-' }}</td>
              <td>
                <div class="row-actions">
                  <button class="btn-text" type="button" @click="openStocktakingDetail(String(row.stocktakingNo))"><Eye :size="15" /> 明细</button>
                  <button v-if="row.status === 'draft'" class="btn-text stocktaking-approve-btn" type="button" @click="approveStocktakingRow(String(row.stocktakingNo))"><CheckCircle2 :size="15" /> 复核</button>
                </div>
              </td>
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

    <Teleport to="body">
    <div v-if="stocktakingSheetOpen" class="attachment-preview-mask" @click.self="stocktakingSheetOpen = false">
      <section class="supplier-dialog product-dialog stocktaking-sheet-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>盘点管理</p>
            <h3>新增盘点表</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="stocktakingSheetOpen = false"><X :size="18" /></button>
        </header>
        <form @submit.prevent="submitStocktakingSheet">
          <div class="supplier-form-grid compact">
            <label>
              <span>盘点库房</span>
              <select v-model="stocktakingSheetForm.warehouseName" @change="loadStocktakingPreview">
                <option value="">请选择库房</option>
                <option v-for="item in stocktakingOptions.warehouses" :key="item.warehouseName" :value="item.warehouseName">
                  {{ item.warehouseName }}
                </option>
              </select>
            </label>
            <label>
              <span>盘点科室</span>
              <select v-model="stocktakingSheetForm.deptName">
                <option value="">全院</option>
                <option v-for="item in stocktakingOptions.departments" :key="item.deptCode" :value="item.deptName">
                  {{ item.deptName }}
                </option>
              </select>
            </label>
          </div>
          <div class="stocktaking-scope-picker">
            <div class="stocktaking-scope-head">
              <strong>选择商品范围（可多选）</strong>
              <span>按所选范围生成盘点明细，支持多个范围合并盘点</span>
            </div>
            <div class="stocktaking-scope-options">
              <label v-for="option in stocktakingScopeOptions" :key="option.key" class="stocktaking-scope-option">
                <input
                  type="checkbox"
                  :checked="stocktakingSheetForm.scopes.includes(option.key)"
                  @change="toggleSheetScope(option.key)"
                />
                <span>{{ option.label }}</span>
              </label>
            </div>
            <div class="stocktaking-scope-selected">
              <span>已选范围：</span>
              <template v-if="selectedScopeLabels.length">
                <span v-for="label in selectedScopeLabels" :key="label" class="stocktaking-scope-chip">{{ label }}</span>
              </template>
              <em v-else>未选择任何范围</em>
            </div>
          </div>
          <div class="table-scroll stocktaking-detail-scroll">
            <table class="master-table stocktaking-detail-table">
              <thead>
                <tr>
                  <th>商品编码</th><th>商品名称</th><th>规格型号</th><th>厂家</th><th>单位</th><th>当前库存</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="stocktakingPreviewLoading"><td colspan="6" class="approval-empty">正在加载当前库房库存...</td></tr>
                <tr v-else-if="stocktakingSheetForm.scopes.length && !stocktakingPreviewRows.length">
                  <td colspan="6" class="approval-empty">所选范围暂无库存明细</td>
                </tr>
                <tr v-else-if="!stocktakingSheetForm.scopes.length"><td colspan="6" class="approval-empty">选择商品范围后展示当前库存明细</td></tr>
                <tr v-for="row in stocktakingPreviewRows" v-else :key="row.productCode">
                  <td class="stocktaking-code-cell">{{ row.productCode }}</td><td>{{ row.productName }}</td><td>{{ row.specModel || '-' }}</td>
                  <td>{{ row.manufacturerName || '-' }}</td><td>{{ row.unit || '-' }}</td><td class="stocktaking-qty-cell">{{ row.systemQty }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="dialog-actions">
            <button class="btn" type="button" @click="stocktakingSheetOpen = false">取消</button>
            <button class="btn btn-primary" type="submit">
              <CheckCircle2 :size="16" />
              确认生成盘点明细
            </button>
          </div>
        </form>
      </section>
    </div>
    </Teleport>

    <Teleport to="body">
    <div v-if="stocktakingDetailOpen" class="attachment-preview-mask" @click.self="stocktakingDetailOpen = false">
      <section class="supplier-dialog stocktaking-detail-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>盘点明细</p>
            <h3>{{ stocktakingDetailNo }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="stocktakingDetailOpen = false"><X :size="18" /></button>
        </header>
        <div class="table-scroll stocktaking-detail-scroll">
          <table class="master-table stocktaking-detail-table">
            <thead>
              <tr>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格型号</th>
                <th>厂家</th>
                <th>单位</th>
                <th>库存数量</th>
                <th>盘点数量</th>
                <th>差异数量</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="stocktakingDetailLoading">
                <td colspan="8" class="approval-empty">正在加载盘点明细...</td>
              </tr>
              <tr v-else-if="!stocktakingDetailRows.length">
                <td colspan="8" class="approval-empty">所选范围内暂无库存商品</td>
              </tr>
              <tr v-for="row in stocktakingDetailRows" v-else :key="row.itemId">
                <td class="code-cell">{{ row.productCode }}</td>
                <td>{{ row.productName }}</td>
                <td>{{ row.specModel || '-' }}</td>
                <td>{{ row.manufacturerName || '-' }}</td>
                <td>{{ row.unit || '-' }}</td>
                <td class="number-cell">{{ row.systemQty }}</td>
                <td>
                  <input v-integer-quantity
                    v-model.number="row.actualQty"
                    class="stocktaking-qty-input"
                    type="number"
                    min="0" step="1"
                    placeholder="待盘点"
                  />
                </td>
                <td class="number-cell" :class="{ profit: stocktakingRowDiff(row) !== null && Number(stocktakingRowDiff(row)) < 0, loss: stocktakingRowDiff(row) !== null && Number(stocktakingRowDiff(row)) > 0 }">
                  {{ stocktakingRowDiff(row) == null ? '-' : stocktakingRowDiff(row) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="dialog-actions">
          <button class="btn" type="button" @click="stocktakingDetailOpen = false">关闭</button>
          <button class="btn btn-primary" type="button" :disabled="stocktakingDetailSaving" @click="saveStocktakingDetail">
            <Save :size="15" />
            {{ stocktakingDetailSaving ? '保存中...' : '保存盘点数量' }}
          </button>
        </div>
      </section>
    </div>
    </Teleport>
  </section>
</template>

<style scoped>
/* =====================================================================
   库存管理 样式优化
   ---------------------------------------------------------------------
   范围：仅 inventory-management（库存汇总 / 定数包 / 唯一码 三个查询页签）。
   盘点管理、批次调价、库存交易流水沿用各自原有结构与样式，未做改动。

   与采购管理、收货验收保持同一套 md 设计语言：
   1. 页头改用 MdPageHeader（由 breadcrumb-line + detail-heading 替换）
   2. 工作区单卡片：页签 / 查询区 / 表格 三段式
   3. 表格 md 密度：表头 12px、表体 13px、sticky 表头、斑马纹
   4. 编码/数量/金额列统一等宽数字与右对齐，便于纵向比对

   注意优先级：.master-table 的 padding / font-size / color 由
   spd-override.css 以 !important 统一设定；本页规则统一加
   .inventory-page 前缀提高特指度，确保覆盖生效。
   ===================================================================== */

/* ---------- 工作区卡片 ---------- */
.inventory-page {
  padding: 20px 20px 24px;
}

.inventory-page .inventory-workspace {
  display: flex !important;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 14px 20px 16px;
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle)) !important;
  border-radius: var(--md-radius-md, 10px) !important;
  box-shadow: var(--md-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
}

/* ---------- 页签 ---------- */
.inventory-page .inventory-query-tabs {
  gap: 6px;
  padding: 0 0 10px;
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle));
}

.inventory-page .inventory-query-tabs button {
  min-height: 30px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  border-radius: var(--md-radius, 6px);
}

/* ---------- 查询区 ---------- */
.inventory-page .inventory-query-grid {
  display: grid !important;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  align-items: end;
  gap: 10px 14px;
  padding: 12px !important;
  background: var(--md-bg-muted, var(--spd-bg-page));
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
}

.inventory-page .inventory-query-grid label {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.inventory-page .inventory-query-grid label > span {
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
}

.inventory-page .inventory-query-grid .md-input {
  min-height: 30px;
  font-size: 13px;
}

.inventory-page .inventory-query-button {
  min-height: 30px;
  padding: 0 14px;
  font-size: 13px;
}

.inventory-page .inventory-query-button svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

/* ---------- 列表滚动容器 ---------- */
.inventory-page .inventory-list-scroll {
  max-height: max(300px, calc(100dvh - 420px));
  overflow: auto;
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  background: var(--md-bg-card, #fff);
  scrollbar-width: thin;
  scrollbar-color: var(--md-border, var(--spd-border-default)) transparent;
}

.inventory-page .inventory-list-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.inventory-page .inventory-list-scroll::-webkit-scrollbar-thumb {
  background: var(--md-border, var(--spd-border-default));
  border-radius: 4px;
}

/* ---------- 表格：md 密度 ---------- */
.inventory-page .inventory-table {
  min-width: 100%;
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px !important;
}

.inventory-page .inventory-table thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  padding: 9px 12px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  letter-spacing: 0.2px;
  white-space: nowrap;
  color: var(--md-text-heading) !important;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%) !important;
  border-bottom: 1px solid var(--md-border, var(--spd-border-default)) !important;
}

.inventory-page .inventory-table tbody td {
  padding: 8px 12px !important;
  font-size: 13px !important;
  color: var(--md-text) !important;
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  vertical-align: middle;
}

.inventory-page .inventory-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted, #fafbfc) !important;
}

.inventory-page .inventory-table tbody tr:hover td {
  background: var(--md-primary-soft, #f0f7f6) !important;
}

/* ---------- 盘点管理 ---------- */
.inventory-page .stocktaking-workspace {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inventory-page .stocktaking-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.inventory-page .stocktaking-title h3 {
  margin: 0;
  color: var(--md-text-heading);
  font-size: 15px;
  font-weight: 600;
}

.inventory-page .stocktaking-create-btn {
  margin-left: auto;
  min-height: 32px;
  padding: 0 14px;
  font-size: 13px;
  border-radius: var(--spd-radius-sm, 6px);
}

.inventory-page .stocktaking-hint {
  margin: 0;
  padding: 9px 12px;
  color: var(--md-text-secondary);
  background: var(--md-bg-muted, #fafbfc);
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  font-size: 12px;
  line-height: 1.5;
}

.inventory-page .stocktaking-list-scroll {
  max-height: max(300px, calc(100dvh - 400px));
  overflow: auto;
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  scrollbar-width: thin;
  scrollbar-color: var(--md-border, var(--spd-border-default)) transparent;
}

.inventory-page .stocktaking-table {
  width: 100%;
  min-width: 980px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px !important;
}

.inventory-page .stocktaking-table thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  padding: 9px 12px !important;
  color: var(--md-text-heading) !important;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%) !important;
  border-bottom: 1px solid var(--md-border, var(--spd-border-default)) !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.inventory-page .stocktaking-table tbody td {
  padding: 8px 12px !important;
  color: var(--md-text) !important;
  background: var(--md-bg-card, #fff);
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  font-size: 13px !important;
  vertical-align: middle;
}

.inventory-page .stocktaking-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted, #fafbfc) !important;
}

.inventory-page .stocktaking-table tbody tr:hover td {
  background: var(--md-primary-soft, #f0f7f6) !important;
}

.inventory-page .stocktaking-no-cell {
  color: var(--md-primary) !important;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.inventory-page .stocktaking-diff-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.inventory-page .stocktaking-state-cell {
  height: 140px;
  color: var(--md-text-muted) !important;
  text-align: center;
}

.inventory-page .stocktaking-approve-btn {
  color: var(--md-primary) !important;
}

:deep(.hospital-pagination) {
  margin: 0;
  padding: 12px 4px 0;
  border-top: 1px solid var(--md-border-soft, var(--spd-border-subtle));
}

/* ---------- 编码列 ---------- */
.inventory-page .inventory-table .inventory-code-cell {
  font-family: var(--md-font-mono, ui-monospace, monospace);
  font-size: 12px !important;
  color: var(--md-text-secondary, var(--spd-text-secondary)) !important;
  white-space: nowrap;
}

/* 商品名称：过长时省略，避免单行撑爆列宽 */
.inventory-page .inventory-table .inventory-product-cell {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--md-text-heading) !important;
  font-weight: 500;
}

/* ---------- 数量 / 金额列 ---------- */
.inventory-page .inventory-table .inventory-qty-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--md-text-heading) !important;
  white-space: nowrap;
}

.inventory-page .inventory-table .inventory-money-cell {
  text-align: right;
  font-family: var(--md-font-mono, ui-monospace, monospace);
  font-variant-numeric: tabular-nums;
  color: var(--md-text) !important;
  white-space: nowrap;
}

/* ---------- 加载 / 空状态 ---------- */
.inventory-page .inventory-table .inventory-state-cell {
  padding: 48px 16px !important;
  text-align: center;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 13px;
}

/* ---------- 提示条 ---------- */
.inventory-page .inline-message {
  margin: 0;
  padding: 10px 14px;
  border: 1px solid var(--spd-primary-border);
  border-radius: var(--md-radius, 6px);
  color: var(--spd-primary-text);
  background: var(--spd-primary-bg);
  font-size: 13px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1280px) {
  .inventory-page .inventory-query-grid {
    grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  }
}

@media (max-width: 900px) {
  .inventory-page .inventory-workspace {
    padding: 12px 12px 14px;
  }

  .inventory-page .inventory-query-grid {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }

  .inventory-page .inventory-list-scroll {
    max-height: max(240px, calc(100dvh - 380px));
  }
}

/* =====================================================================
   批量调价 price-adjust-page
   ---------------------------------------------------------------------
   与结算对账 / 红冲管理同处"结算与财务"模块，统一使用渐变 md-header
   （与 UDI / 唯一码追溯 / 高值耗材页面一致），页面外壳对齐到
   其他 financial 页面的 padding / gap。
   ===================================================================== */
.price-adjust-page {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: none;
  gap: 14px !important;
  padding: 16px 20px 24px;
}

.price-adjust-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

.price-adjust-page > .hospital-catalog-panel {
  border-radius: var(--spd-radius-md, 10px);
  box-shadow: var(--md-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
}

/* 批次调价查询区：紧凑化控件 + 圆角与主数据一致 */
.price-adjust-page .hospital-query-grid.purchase-query-grid {
  align-items: end;
}

.price-adjust-page .hospital-query-grid input,
.price-adjust-page .hospital-query-grid select {
  min-height: 32px;
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
  padding: 0 11px;
  background: var(--md-bg-card, #fff);
  color: var(--md-text, var(--spd-text-primary));
  font-size: 13px;
}

/* =====================================================================
   新增盘点表弹窗
   ---------------------------------------------------------------------
   把 dialog 改成宽屏 + 高度撑满视口、内部 flex 列布局，
   让「选择商品范围 + 库存明细表」能同时显示更多行。 */
.stocktaking-sheet-dialog {
  width: min(1380px, 98vw) !important;
  max-width: 1380px;
  max-height: min(94vh, 1080px);
  display: flex !important;
  flex-direction: column;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 24px 64px rgba(6, 21, 43, 0.28);
  overflow: hidden;
}

.stocktaking-sheet-dialog > header {
  flex: 0 0 auto;
  padding: 14px 20px;
  border-bottom: 1px solid #eef0f4;
  background: linear-gradient(120deg, #f0f7ff 0%, #ffffff 65%, #ffffff 100%);
}

.stocktaking-sheet-dialog > header p {
  margin: 0 0 2px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  color: #1677ff;
  text-transform: none;
}

.stocktaking-sheet-dialog > header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #172033;
}

.stocktaking-sheet-dialog form {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
  padding: 14px 20px 0;
  overflow: hidden;
}

.stocktaking-sheet-dialog .supplier-form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  margin: 0;
}

.stocktaking-sheet-dialog .supplier-form-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
}

.stocktaking-sheet-dialog .supplier-form-grid label > span {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.stocktaking-sheet-dialog .supplier-form-grid select {
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  color: #1f2937;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.stocktaking-sheet-dialog .supplier-form-grid select:focus {
  outline: 0;
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.15);
}

.stocktaking-scope-picker {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid #e0e7ff;
  border-radius: 10px;
  background: linear-gradient(180deg, #f5f8ff 0%, #ffffff 100%);
}

.stocktaking-scope-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.stocktaking-scope-head strong {
  font-size: 13px;
  font-weight: 600;
  color: #1d4ed8;
}

.stocktaking-scope-head span {
  font-size: 12px;
  color: #64748b;
}

.stocktaking-scope-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stocktaking-scope-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid #c7d2fe;
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
  color: #1e293b;
  cursor: pointer;
  transition: all 0.15s ease;
}

.stocktaking-scope-option:hover {
  border-color: #1677ff;
  color: #1d4ed8;
  background: #eff6ff;
}

.stocktaking-scope-option input[type="checkbox"] {
  width: 14px;
  height: 14px;
  accent-color: #1677ff;
  cursor: pointer;
}

.stocktaking-scope-selected {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
  font-size: 12px;
  color: #64748b;
}

.stocktaking-scope-selected > span:first-child {
  font-weight: 500;
  color: #475569;
}

.stocktaking-scope-selected em {
  font-style: normal;
  color: #94a3b8;
}

.stocktaking-scope-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 500;
  color: #1d4ed8;
  background: #dbeafe;
  border: 1px solid #93c5fd;
  border-radius: 999px;
}

.stocktaking-detail-scroll {
  flex: 1 1 auto;
  min-height: 360px;
  max-height: 100%;
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.stocktaking-detail-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.stocktaking-detail-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

.stocktaking-detail-scroll::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.stocktaking-detail-table {
  width: 100%;
  min-width: 1200px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px !important;
}

.stocktaking-detail-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 14px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  color: #1e293b !important;
  background: linear-gradient(180deg, #f0f7ff 0%, #e0efff 100%) !important;
  border-bottom: 1px solid #c7d2fe !important;
  text-align: left;
  white-space: nowrap;
}

.stocktaking-detail-table tbody td {
  padding: 9px 14px !important;
  border-bottom: 1px solid #f1f5f9 !important;
  color: #334155 !important;
  font-size: 13px !important;
  background: #fff;
  vertical-align: middle;
}

.stocktaking-detail-table tbody tr:nth-child(even) td {
  background: #fafbfc !important;
}

.stocktaking-detail-table tbody tr:hover td {
  background: #f0f7ff !important;
}

.stocktaking-detail-table .stocktaking-qty-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #1d4ed8 !important;
}

.stocktaking-detail-table .stocktaking-code-cell {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px !important;
  color: #475569 !important;
  white-space: nowrap;
}

.stocktaking-detail-table .approval-empty {
  padding: 56px 16px !important;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
}

.stocktaking-sheet-dialog .dialog-actions {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin: 0 -20px;
  padding: 12px 20px;
  border-top: 1px solid #eef0f4;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.stocktaking-sheet-dialog .dialog-actions .btn {
  min-width: 100px;
  height: 34px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
}

.stocktaking-sheet-dialog .dialog-actions .btn-primary {
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 100%);
  border-color: #1677ff;
  box-shadow: 0 3px 8px rgba(22, 119, 255, 0.25);
}

.stocktaking-sheet-dialog .dialog-actions .btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #0958d9 0%, #1677ff 100%);
  box-shadow: 0 5px 12px rgba(22, 119, 255, 0.3);
}

@media (max-width: 1280px) {
  .stocktaking-sheet-dialog {
    width: min(96vw, 1280px) !important;
  }
}

@media (max-width: 900px) {
  .stocktaking-sheet-dialog {
    width: 96vw !important;
    max-height: 92vh;
  }
  .stocktaking-detail-scroll {
    min-height: 260px;
  }
}
</style>
