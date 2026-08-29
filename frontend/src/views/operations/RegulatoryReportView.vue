<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Download, FileSpreadsheet, LockKeyhole, RefreshCw, Search } from '@lucide/vue'
import PageHeader from '../../components/common/PageHeader.vue'
import TableStateRow from '../../components/common/TableStateRow.vue'
import {
  exportCentralizedProcurementProgressExcel,
  exportInventoryMovementSummaryExcel,
  exportSupplierDeliveryLedgerExcel,
  fetchCentralizedProcurementProgress,
  fetchInventoryMovementSummary,
  fetchSupplierDeliveryLedger,
  recordRegulatoryPdfExport,
  type CentralizedProcurementRow,
  type InventoryMovementRow,
  type SupplierDeliveryRow,
} from '../../api/reportCenter'

type ReportType = 'supplier' | 'centralized' | 'inventory'
type ReportRow = SupplierDeliveryRow | CentralizedProcurementRow | InventoryMovementRow

const route = useRoute()
const today = new Date()
const monthAgo = new Date(today)
monthAgo.setDate(today.getDate() - 29)

const reportType = computed(() => (route.meta.reportType || 'supplier') as ReportType)
const configs = {
  supplier: { title: '供应商供货明细台账', code: 'supplier-delivery-ledger', note: '统计口径：已完成验收的合格数量及对应批次采购价。' },
  centralized: { title: '集采执行进度报表', code: 'centralized-procurement-progress', note: '统计口径：累计采购量按任务年度起始日至筛选截止日的验收合格数量计算。' },
  inventory: { title: '全院物资进销存汇总表', code: 'inventory-movement-summary', note: '统计口径：每日00:00生成前一业务日快照，期初沿用前日结存，期间发生额按库存流水增量汇总。' },
} as const
const config = computed(() => configs[reportType.value])
const query = reactive({
  dateFrom: inputDate(monthAgo), dateTo: inputDate(today), supplier: '', category: '', department: '',
  centralizedStatus: 'ALL', batch: '', warehouse: '', materialType: 'ALL', page: 1, size: 20,
})
const rows = ref<ReportRow[]>([])
const supplierRows = computed(() => rows.value as SupplierDeliveryRow[])
const centralizedRows = computed(() => rows.value as CentralizedProcurementRow[])
const inventoryRows = computed(() => rows.value as InventoryMovementRow[])
const total = ref(0)
const summary = ref<Record<string, number>>({})
const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))
const pageNumbers = computed(() => {
  const start = Math.max(1, Math.min(query.page - 2, totalPages.value - 4))
  const end = Math.min(totalPages.value, start + 4)
  return Array.from({ length: Math.max(0, end - start + 1) }, (_, index) => start + index)
})

function inputDate(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
}
function number(value: unknown) { return Number(value || 0) }
function quantity(value: unknown) { return number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 }) }
function amount(value: unknown) { return number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function reportParams() {
  const base = { dateFrom: query.dateFrom, dateTo: query.dateTo, page: query.page, size: query.size }
  if (reportType.value === 'supplier') return { ...base, supplier: query.supplier, category: query.category, department: query.department, centralizedStatus: query.centralizedStatus as 'ALL' | 'SELECTED' | 'NON_SELECTED' }
  if (reportType.value === 'centralized') return { ...base, batch: query.batch, category: query.category, department: query.department }
  return { ...base, warehouse: query.warehouse, category: query.category, materialType: query.materialType as 'ALL' | 'HIGH' | 'LOW' | 'REAGENT' }
}
function readableError(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message && reason.message !== 'Request failed' ? reason.message : fallback
}

async function loadReport(resetPage = false) {
  if (resetPage) query.page = 1
  loading.value = true
  error.value = ''
  try {
    const params = reportParams()
    const result = reportType.value === 'supplier'
      ? await fetchSupplierDeliveryLedger(params as Parameters<typeof fetchSupplierDeliveryLedger>[0])
      : reportType.value === 'centralized'
        ? await fetchCentralizedProcurementProgress(params as Parameters<typeof fetchCentralizedProcurementProgress>[0])
        : await fetchInventoryMovementSummary(params as Parameters<typeof fetchInventoryMovementSummary>[0])
    rows.value = result.rows
    total.value = result.total
    summary.value = Object.fromEntries(Object.entries(result.summary || {}).map(([key, value]) => [key, number(value)]))
  } catch (reason) {
    rows.value = []; total.value = 0; summary.value = {}
    error.value = readableError(reason, `${config.value.title}加载失败，请稍后重试`)
  } finally { loading.value = false }
}

function resetQuery() {
  Object.assign(query, { dateFrom: inputDate(monthAgo), dateTo: inputDate(today), supplier: '', category: '', department: '', centralizedStatus: 'ALL', batch: '', warehouse: '', materialType: 'ALL', page: 1 })
  void loadReport()
}
function changePage(page: number) {
  if (page < 1 || page > totalPages.value || page === query.page) return
  query.page = page; void loadReport()
}
async function exportExcel() {
  exporting.value = true; error.value = ''
  try {
    const params = { ...reportParams(), page: undefined, size: undefined }
    const blob = reportType.value === 'supplier'
      ? await exportSupplierDeliveryLedgerExcel(params as Parameters<typeof exportSupplierDeliveryLedgerExcel>[0])
      : reportType.value === 'centralized'
        ? await exportCentralizedProcurementProgressExcel(params as Parameters<typeof exportCentralizedProcurementProgressExcel>[0])
        : await exportInventoryMovementSummaryExcel(params as Parameters<typeof exportInventoryMovementSummaryExcel>[0])
    const href = URL.createObjectURL(blob)
    const link = document.createElement('a'); link.href = href; link.download = `${config.value.title}_${query.dateFrom}_${query.dateTo}.xlsx`; link.click()
    URL.revokeObjectURL(href)
  } catch (reason) { error.value = readableError(reason, 'Excel导出失败，请稍后重试') }
  finally { exporting.value = false }
}
async function exportPdf() {
  try { await recordRegulatoryPdfExport(config.value.code); window.print() }
  catch (reason) { error.value = readableError(reason, 'PDF导出准备失败，请稍后重试') }
}

watch(reportType, () => { resetQuery() })
onMounted(() => loadReport())
</script>

<template>
  <main class="reconciliation-report-page regulatory-report-page">
    <PageHeader :eyebrow="`运营与决策 / 报表中心 / ${config.title}`" :title="config.title">
      <template #actions><span class="audit-badge"><LockKeyhole :size="14" />查看与导出行为已留痕</span></template>
    </PageHeader>

    <section class="reconciliation-filter-panel">
      <div class="reconciliation-filters regulatory-filters">
        <label><span>统计时间</span><div class="date-range"><input v-model="query.dateFrom" type="date" /><i>至</i><input v-model="query.dateTo" type="date" /></div></label>
        <template v-if="reportType === 'supplier'">
          <label><span>供应商</span><input v-model.trim="query.supplier" type="search" placeholder="全部供应商" @keyup.enter="loadReport(true)" /></label>
          <label><span>物资分类</span><input v-model.trim="query.category" type="search" placeholder="一级 / 二级 / 三级分类" @keyup.enter="loadReport(true)" /></label>
          <label><span>科室</span><input v-model.trim="query.department" type="search" placeholder="全部科室" @keyup.enter="loadReport(true)" /></label>
          <label><span>集采中选状态</span><select v-model="query.centralizedStatus"><option value="ALL">全部</option><option value="SELECTED">中选</option><option value="NON_SELECTED">非中选</option></select></label>
        </template>
        <template v-else-if="reportType === 'centralized'">
          <label><span>集采批次</span><input v-model.trim="query.batch" type="search" placeholder="批次名称 / 编码" @keyup.enter="loadReport(true)" /></label>
          <label><span>物资分类</span><input v-model.trim="query.category" type="search" placeholder="全部分类" @keyup.enter="loadReport(true)" /></label>
          <label><span>科室</span><input v-model.trim="query.department" type="search" placeholder="消耗科室" @keyup.enter="loadReport(true)" /></label>
        </template>
        <template v-else>
          <label><span>库房</span><input v-model.trim="query.warehouse" type="search" placeholder="全部库房" @keyup.enter="loadReport(true)" /></label>
          <label><span>物资分类</span><input v-model.trim="query.category" type="search" placeholder="全部分类" @keyup.enter="loadReport(true)" /></label>
          <label><span>耗材类型</span><select v-model="query.materialType"><option value="ALL">全部</option><option value="HIGH">高值耗材</option><option value="LOW">低值耗材</option><option value="REAGENT">试剂</option></select></label>
        </template>
      </div>
      <div class="reconciliation-actions">
        <button class="btn primary" type="button" :disabled="loading" @click="loadReport(true)"><Search :size="16" />查询</button>
        <button class="btn" type="button" :disabled="loading" @click="resetQuery"><RefreshCw :size="16" />重置</button>
        <button class="btn" type="button" :disabled="exporting" @click="exportExcel"><FileSpreadsheet :size="16" />导出Excel</button>
        <button class="btn" type="button" @click="exportPdf"><Download :size="16" />导出PDF</button>
      </div>
    </section>
    <p v-if="error" class="reconciliation-error">{{ error }}</p>

    <section class="reconciliation-summary">
      <template v-if="reportType === 'supplier'"><span>明细 <strong>{{ quantity(summary.totalCount) }}</strong> 条</span><span>供应商 <strong>{{ quantity(summary.supplierCount) }}</strong> 家</span><span>供货数量 <strong>{{ quantity(summary.supplyQuantity) }}</strong></span><span>供货金额 <strong>¥{{ amount(summary.supplyAmount) }}</strong></span></template>
      <template v-else-if="reportType === 'centralized'"><span>任务物资 <strong>{{ quantity(summary.taskCount) }}</strong> 项</span><span>年度任务量 <strong>{{ quantity(summary.annualTargetQuantity) }}</strong></span><span>累计采购量 <strong>{{ quantity(summary.cumulativePurchaseQuantity) }}</strong></span><span>综合完成率 <strong>{{ quantity(summary.completionRate) }}%</strong></span><span>未完成 <strong class="danger-text">{{ quantity(summary.incompleteCount) }}</strong> 项</span></template>
      <template v-else><span>物资库房组合 <strong>{{ quantity(summary.itemCount) }}</strong> 项</span><span>期初金额 <strong>¥{{ amount(summary.openingAmount) }}</strong></span><span>入库金额 <strong>¥{{ amount(summary.inboundAmount) }}</strong></span><span>消耗金额 <strong>¥{{ amount(summary.consumptionAmount) }}</strong></span><span>期末金额 <strong>¥{{ amount(summary.closingAmount) }}</strong></span></template>
    </section>

    <section class="reconciliation-table-card" :aria-busy="loading">
      <div class="reconciliation-table-scroll">
        <table v-if="reportType === 'supplier'" class="reconciliation-table supplier-ledger-table">
          <thead><tr><th>序号</th><th>供货日期</th><th>供应商</th><th>生产厂家</th><th>物资编码</th><th>医保编码</th><th>物资名称</th><th>规格</th><th>注册证号</th><th class="numeric">采购单价</th><th class="numeric">供货数量</th><th class="numeric">供货金额</th><th>订单号</th><th>合同号</th><th>验收科室</th><th>验收人</th><th>验收时间</th></tr></thead>
          <tbody><TableStateRow v-if="loading" :colspan="17" state="loading" message="正在加载供货明细…" /><TableStateRow v-else-if="!rows.length" :colspan="17" message="当前筛选条件下暂无供货数据" /><tr v-for="(item, index) in supplierRows" v-else :key="`${item.orderNo}-${item.productCode}-${index}`"><td>{{ (query.page - 1) * query.size + index + 1 }}</td><td>{{ item.supplyDate }}</td><td>{{ item.supplierName }}</td><td>{{ item.manufacturerName }}</td><td class="code-cell">{{ item.productCode }}</td><td class="code-cell">{{ item.medicalInsuranceCode }}</td><td class="product-cell">{{ item.productName }}</td><td>{{ item.specModel }}</td><td>{{ item.registrationNo }}</td><td class="numeric">¥{{ amount(item.unitPrice) }}</td><td class="numeric">{{ quantity(item.supplyQuantity) }}</td><td class="numeric">¥{{ amount(item.supplyAmount) }}</td><td class="code-cell">{{ item.orderNo }}</td><td>{{ item.contractNo }}</td><td>{{ item.acceptanceDepartment }}</td><td>{{ item.acceptanceUser }}</td><td>{{ item.acceptanceTime }}</td></tr></tbody>
        </table>
        <table v-else-if="reportType === 'centralized'" class="reconciliation-table centralized-table">
          <thead><tr><th>集采批次</th><th>物资名称</th><th>规格</th><th>中选厂家</th><th class="numeric">中选价格</th><th class="numeric">年度任务量</th><th class="numeric">本期采购量</th><th class="numeric">累计采购量</th><th>完成率</th><th>中选标识</th><th class="numeric">科室消耗量</th><th>未完成原因</th></tr></thead>
          <tbody><TableStateRow v-if="loading" :colspan="12" state="loading" message="正在计算集采执行进度…" /><TableStateRow v-else-if="!rows.length" :colspan="12" message="暂无集采任务或中选物资" /><tr v-for="item in centralizedRows" v-else :key="`${item.batchCode}-${item.productCode}`"><td><strong>{{ item.batchName }}</strong><small>{{ item.batchCode }}</small></td><td class="product-cell">{{ item.productName }}</td><td>{{ item.specModel }}</td><td>{{ item.selectedManufacturer }}</td><td class="numeric">¥{{ amount(item.selectedPrice) }}</td><td class="numeric">{{ quantity(item.annualTargetQuantity) }}</td><td class="numeric">{{ quantity(item.periodPurchaseQuantity) }}</td><td class="numeric">{{ quantity(item.cumulativePurchaseQuantity) }}</td><td><div class="progress-cell"><span><i :style="{ width: `${Math.min(100, number(item.completionRate))}%` }"></i></span><strong>{{ quantity(item.completionRate) }}%</strong></div></td><td><em class="status-pill">{{ item.selectedFlag }}</em></td><td class="numeric">{{ quantity(item.departmentConsumptionQuantity) }}</td><td :class="{ 'warning-text': item.incompleteReason !== '-' }">{{ item.incompleteReason }}</td></tr></tbody>
        </table>
        <table v-else class="reconciliation-table inventory-summary-table">
          <thead><tr><th>物资编码</th><th>物资名称</th><th>规格</th><th>生产厂家</th><th>配送商</th><th class="numeric">期初库存</th><th class="numeric">本期入库</th><th class="numeric">本期退库</th><th class="numeric">本期领用</th><th class="numeric">本期消耗</th><th class="numeric">本期报废</th><th class="numeric">期末库存</th><th class="numeric">库存单价</th><th class="numeric">库存金额</th><th>库房</th><th>物资属性</th></tr></thead>
          <tbody><TableStateRow v-if="loading" :colspan="16" state="loading" message="正在读取每日进销存快照…" /><TableStateRow v-else-if="!rows.length" :colspan="16" message="当前筛选条件下暂无每日进销存快照" /><tr v-for="item in inventoryRows" v-else :key="`${item.warehouseName}-${item.productCode}-${item.distributorName}`"><td class="code-cell">{{ item.productCode }}</td><td class="product-cell">{{ item.productName }}</td><td>{{ item.specModel }}</td><td>{{ item.manufacturerName }}</td><td>{{ item.distributorName }}</td><td class="numeric">{{ quantity(item.openingQuantity) }}</td><td class="numeric positive-text">{{ quantity(item.inboundQuantity) }}</td><td class="numeric">{{ quantity(item.returnQuantity) }}</td><td class="numeric">{{ quantity(item.requisitionQuantity) }}</td><td class="numeric">{{ quantity(item.consumptionQuantity) }}</td><td class="numeric danger-text">{{ quantity(item.scrapQuantity) }}</td><td class="numeric strong-number">{{ quantity(item.closingQuantity) }}</td><td class="numeric">¥{{ amount(item.unitPrice) }}</td><td class="numeric strong-number">¥{{ amount(item.inventoryAmount) }}</td><td>{{ item.warehouseName }}</td><td><em class="status-pill neutral">{{ item.materialAttribute }}</em></td></tr></tbody>
        </table>
      </div>
      <footer class="reconciliation-table-footer"><p>{{ config.note }}</p><nav class="report-pagination" aria-label="报表分页"><span>共{{ total.toLocaleString('zh-CN') }}条</span><select v-model.number="query.size" aria-label="每页条数" @change="query.page = 1; loadReport()"><option :value="20">20条/页</option><option :value="50">50条/页</option><option :value="100">100条/页</option></select><button type="button" aria-label="上一页" :disabled="query.page === 1" @click="changePage(query.page - 1)">‹</button><button v-for="page in pageNumbers" :key="page" type="button" :class="{ active: page === query.page }" :aria-current="page === query.page ? 'page' : undefined" :aria-label="`第${page}页`" @click="changePage(page)">{{ page }}</button><button type="button" aria-label="下一页" :disabled="query.page === totalPages" @click="changePage(query.page + 1)">›</button></nav></footer>
    </section>
  </main>
</template>
