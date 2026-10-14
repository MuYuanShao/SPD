<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, FileSpreadsheet, RefreshCw, Search } from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
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
  supplier: {
    code: 'supplier-delivery-ledger',
    title: '供应商供货明细台账',
    crumb: '运营与决策 / 报表中心 / 供应链合规',
    description: '按已完成验收的合格数量及对应批次采购价统计的供应商供货明细。',
  },
  centralized: {
    code: 'centralized-procurement-progress',
    title: '集采执行进度报表',
    crumb: '运营与决策 / 报表中心 / 集采与医保',
    description: '按集采任务年度起始日至筛选截止日的验收合格数量计算累计采购量与完成率。',
  },
  inventory: {
    code: 'inventory-movement-summary',
    title: '全院物资进销存汇总表',
    crumb: '运营与决策 / 报表中心 / 进销存汇总',
    description: '每日 00:00 生成前一业务日快照，期初沿用前日结存，期间发生额按库存流水增量汇总。',
  },
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

async function loadReport() {
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
    ElMessage.error(error.value)
  } finally { loading.value = false }
}

function resetQuery() {
  Object.assign(query, { dateFrom: inputDate(monthAgo), dateTo: inputDate(today), supplier: '', category: '', department: '', centralizedStatus: 'ALL', batch: '', warehouse: '', materialType: 'ALL', page: 1 })
  void loadReport()
}
function changePage(page: number) {
  if (page === query.page) return
  query.page = page
  void loadReport()
}
function changeSize(size: number) {
  query.size = size
  query.page = 1
  void loadReport()
}
async function exportExcel() {
  exporting.value = true
  error.value = ''
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
    ElMessage.success('Excel 已开始下载')
  } catch (reason) {
    error.value = readableError(reason, 'Excel 导出失败，请稍后重试')
    ElMessage.error(error.value)
  } finally { exporting.value = false }
}
async function exportPdf() {
  try { await recordRegulatoryPdfExport(config.value.code); window.print() }
  catch (reason) { error.value = readableError(reason, 'PDF 导出准备失败，请稍后重试'); ElMessage.error(error.value) }
}

watch(reportType, () => { resetQuery() })
onMounted(() => void loadReport())
</script>

<template>
  <main class="report-page regulatory-page">
    <MdPageHeader
      :crumb="config.crumb"
      :title="config.title"
      :description="config.description"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadReport"
    />

    <section class="report-toolbar">
      <div class="report-filters">
        <label><span>统计时间</span><div class="date-range"><input v-model="query.dateFrom" type="date" /><i>至</i><input v-model="query.dateTo" type="date" /></div></label>
        <template v-if="reportType === 'supplier'">
          <label><span>供应商</span><input v-model.trim="query.supplier" type="search" placeholder="全部供应商" @keyup.enter="changePage(1)" /></label>
          <label><span>物资分类</span><input v-model.trim="query.category" type="search" placeholder="一级 / 二级 / 三级分类" @keyup.enter="changePage(1)" /></label>
          <label><span>科室</span><input v-model.trim="query.department" type="search" placeholder="全部科室" @keyup.enter="changePage(1)" /></label>
          <label><span>集采中选状态</span><select v-model="query.centralizedStatus"><option value="ALL">全部</option><option value="SELECTED">中选</option><option value="NON_SELECTED">非中选</option></select></label>
        </template>
        <template v-else-if="reportType === 'centralized'">
          <label><span>集采批次</span><input v-model.trim="query.batch" type="search" placeholder="批次名称 / 编码" @keyup.enter="changePage(1)" /></label>
          <label><span>物资分类</span><input v-model.trim="query.category" type="search" placeholder="全部分类" @keyup.enter="changePage(1)" /></label>
          <label><span>科室</span><input v-model.trim="query.department" type="search" placeholder="消耗科室" @keyup.enter="changePage(1)" /></label>
        </template>
        <template v-else>
          <label><span>库房</span><input v-model.trim="query.warehouse" type="search" placeholder="全部库房" @keyup.enter="changePage(1)" /></label>
          <label><span>物资分类</span><input v-model.trim="query.category" type="search" placeholder="全部分类" @keyup.enter="changePage(1)" /></label>
          <label><span>耗材类型</span><select v-model="query.materialType"><option value="ALL">全部</option><option value="HIGH">高值耗材</option><option value="LOW">低值耗材</option><option value="REAGENT">试剂</option></select></label>
        </template>
      </div>
      <div class="report-actions">
        <button class="btn btn-primary" type="button" :disabled="loading" @click="changePage(1)"><Search :size="16" />查询</button>
        <button class="btn" type="button" :disabled="loading" @click="resetQuery"><RefreshCw :size="16" />重置</button>
        <button class="btn" type="button" :disabled="exporting" @click="exportExcel"><FileSpreadsheet :size="16" />导出 Excel</button>
        <button class="btn" type="button" @click="exportPdf"><Download :size="16" />导出 PDF</button>
      </div>
    </section>

    <StatusMessage :message="error" tone="error" role="alert" />

    <section class="report-summary" aria-label="汇总指标">
      <template v-if="reportType === 'supplier'">
        <span>明细 <strong>{{ quantity(summary.totalCount) }}</strong> 条</span>
        <span>供应商 <strong>{{ quantity(summary.supplierCount) }}</strong> 家</span>
        <span>供货数量 <strong>{{ quantity(summary.supplyQuantity) }}</strong></span>
        <span>供货金额 <strong class="primary-text">¥{{ amount(summary.supplyAmount) }}</strong></span>
      </template>
      <template v-else-if="reportType === 'centralized'">
        <span>任务物资 <strong>{{ quantity(summary.taskCount) }}</strong> 项</span>
        <span>年度任务量 <strong>{{ quantity(summary.annualTargetQuantity) }}</strong></span>
        <span>累计采购量 <strong>{{ quantity(summary.cumulativePurchaseQuantity) }}</strong></span>
        <span>综合完成率 <strong class="primary-text">{{ quantity(summary.completionRate) }}%</strong></span>
        <span>未完成 <strong class="danger-text">{{ quantity(summary.incompleteCount) }}</strong> 项</span>
      </template>
      <template v-else>
        <span>物资库房组合 <strong>{{ quantity(summary.itemCount) }}</strong> 项</span>
        <span>期初金额 <strong>¥{{ amount(summary.openingAmount) }}</strong></span>
        <span>入库金额 <strong class="primary-text">¥{{ amount(summary.inboundAmount) }}</strong></span>
        <span>消耗金额 <strong>¥{{ amount(summary.consumptionAmount) }}</strong></span>
        <span>期末金额 <strong class="primary-text">¥{{ amount(summary.closingAmount) }}</strong></span>
      </template>
    </section>

    <section class="report-table-card" :aria-busy="loading">
      <el-table
        v-if="reportType === 'supplier'"
        :data="supplierRows"
        stripe
        row-key="orderNo"
        class="report-table supplier-ledger-table"
        :empty-text="loading ? '正在加载供货明细…' : '当前筛选条件下暂无供货数据'"
      >
        <el-table-column type="index" label="序号" width="64" align="center" :index="(i: number) => (query.page - 1) * query.size + i + 1" />
        <el-table-column prop="supplyDate" label="供货日期" width="110" />
        <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column prop="manufacturerName" label="生产厂家" min-width="140" show-overflow-tooltip />
        <el-table-column prop="productCode" label="物资编码" min-width="120" />
        <el-table-column prop="medicalInsuranceCode" label="医保编码" min-width="120" />
        <el-table-column prop="productName" label="物资名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="specModel" label="规格" min-width="100" />
        <el-table-column prop="registrationNo" label="注册证号" min-width="160" />
        <el-table-column prop="unitPrice" label="采购单价" width="110" align="right"><template #default="{ row }">¥{{ amount(row.unitPrice) }}</template></el-table-column>
        <el-table-column prop="supplyQuantity" label="供货数量" width="110" align="right"><template #default="{ row }">{{ quantity(row.supplyQuantity) }}</template></el-table-column>
        <el-table-column prop="supplyAmount" label="供货金额" width="120" align="right"><template #default="{ row }">¥{{ amount(row.supplyAmount) }}</template></el-table-column>
        <el-table-column prop="orderNo" label="订单号" min-width="160" />
        <el-table-column prop="contractNo" label="合同号" min-width="140" />
        <el-table-column prop="acceptanceDepartment" label="验收科室" min-width="120" />
        <el-table-column prop="acceptanceUser" label="验收人" min-width="100" />
        <el-table-column prop="acceptanceTime" label="验收时间" min-width="160" />
      </el-table>

      <el-table
        v-else-if="reportType === 'centralized'"
        :data="centralizedRows"
        stripe
        row-key="productCode"
        class="report-table centralized-table"
        :empty-text="loading ? '正在计算集采执行进度…' : '暂无集采任务或中选物资'"
      >
        <el-table-column label="集采批次" min-width="200">
          <template #default="{ row }"><strong>{{ row.batchName }}</strong><small class="cell-sub">{{ row.batchCode }}</small></template>
        </el-table-column>
        <el-table-column prop="productName" label="物资名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="specModel" label="规格" min-width="120" />
        <el-table-column prop="selectedManufacturer" label="中选厂家" min-width="160" show-overflow-tooltip />
        <el-table-column prop="selectedPrice" label="中选价格" width="120" align="right"><template #default="{ row }">¥{{ amount(row.selectedPrice) }}</template></el-table-column>
        <el-table-column prop="annualTargetQuantity" label="年度任务量" width="120" align="right"><template #default="{ row }">{{ quantity(row.annualTargetQuantity) }}</template></el-table-column>
        <el-table-column prop="periodPurchaseQuantity" label="本期采购量" width="120" align="right"><template #default="{ row }">{{ quantity(row.periodPurchaseQuantity) }}</template></el-table-column>
        <el-table-column prop="cumulativePurchaseQuantity" label="累计采购量" width="120" align="right"><template #default="{ row }">{{ quantity(row.cumulativePurchaseQuantity) }}</template></el-table-column>
        <el-table-column label="完成率" width="200">
          <template #default="{ row }">
            <div class="progress-cell">
              <span><i :style="{ width: `${Math.min(100, number(row.completionRate))}%` }"></i></span>
              <strong>{{ quantity(row.completionRate) }}%</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="selectedFlag" label="中选标识" width="100">
          <template #default="{ row }"><em class="status-pill">{{ row.selectedFlag }}</em></template>
        </el-table-column>
        <el-table-column prop="departmentConsumptionQuantity" label="科室消耗量" width="120" align="right"><template #default="{ row }">{{ quantity(row.departmentConsumptionQuantity) }}</template></el-table-column>
        <el-table-column prop="incompleteReason" label="未完成原因" min-width="160">
          <template #default="{ row }"><span :class="{ 'warning-text': row.incompleteReason !== '-' }">{{ row.incompleteReason }}</span></template>
        </el-table-column>
      </el-table>

      <el-table
        v-else
        :data="inventoryRows"
        stripe
        row-key="productCode"
        class="report-table inventory-summary-table"
        :empty-text="loading ? '正在读取每日进销存快照…' : '当前筛选条件下暂无每日进销存快照'"
      >
        <el-table-column prop="productCode" label="物资编码" min-width="120" />
        <el-table-column prop="productName" label="物资名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="specModel" label="规格" min-width="100" />
        <el-table-column prop="manufacturerName" label="生产厂家" min-width="140" show-overflow-tooltip />
        <el-table-column prop="distributorName" label="配送商" min-width="140" show-overflow-tooltip />
        <el-table-column prop="openingQuantity" label="期初库存" width="110" align="right"><template #default="{ row }">{{ quantity(row.openingQuantity) }}</template></el-table-column>
        <el-table-column prop="inboundQuantity" label="本期入库" width="110" align="right"><template #default="{ row }"><span class="positive-text">{{ quantity(row.inboundQuantity) }}</span></template></el-table-column>
        <el-table-column prop="returnQuantity" label="本期退库" width="110" align="right"><template #default="{ row }">{{ quantity(row.returnQuantity) }}</template></el-table-column>
        <el-table-column prop="requisitionQuantity" label="本期领用" width="110" align="right"><template #default="{ row }">{{ quantity(row.requisitionQuantity) }}</template></el-table-column>
        <el-table-column prop="consumptionQuantity" label="本期消耗" width="110" align="right"><template #default="{ row }">{{ quantity(row.consumptionQuantity) }}</template></el-table-column>
        <el-table-column prop="scrapQuantity" label="本期报废" width="110" align="right"><template #default="{ row }"><span class="danger-text">{{ quantity(row.scrapQuantity) }}</span></template></el-table-column>
        <el-table-column prop="closingQuantity" label="期末库存" width="110" align="right"><template #default="{ row }"><strong class="strong-number">{{ quantity(row.closingQuantity) }}</strong></template></el-table-column>
        <el-table-column prop="unitPrice" label="库存单价" width="110" align="right"><template #default="{ row }">¥{{ amount(row.unitPrice) }}</template></el-table-column>
        <el-table-column prop="inventoryAmount" label="库存金额" width="120" align="right"><template #default="{ row }"><strong class="strong-number">¥{{ amount(row.inventoryAmount) }}</strong></template></el-table-column>
        <el-table-column prop="warehouseName" label="库房" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialAttribute" label="物资属性" min-width="120">
          <template #default="{ row }"><em class="status-pill neutral">{{ row.materialAttribute }}</em></template>
        </el-table-column>
      </el-table>
      <PaginationControls
        :page="query.page"
        :size="query.size"
        :total="total"
        :loading="loading"
        @change-page="changePage"
        @change-size="changeSize"
      />
    </section>
  </main>
</template>

<style scoped>
/* =====================================================================
   报表中心 / 监管报表 RegulatoryReportView
   ---------------------------------------------------------------------
   1. MdPageHeader 渐变页头（无动作按钮）
   2. 工具区：查询与动作同卡
   3. 汇总条：报告类型不同，指标不同
   4. 三种 el-table：supplier / centralized / inventory
   5. 内容区 padding 16/20/24
   ===================================================================== */
.report-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 16px 20px 24px;
  box-sizing: border-box;
}
.report-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}
/* 工具区：查询 + 动作同一张卡 */
.report-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 14px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid var(--spd-line, #e6ecef);
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.report-filters {
  display: grid;
  grid-template-columns: minmax(280px, 1.2fr) repeat(4, minmax(140px, 1fr));
  gap: 10px 14px;
  align-items: end;
}
.report-filters label {
  display: grid;
  gap: 5px;
  min-width: 0;
  color: #4b5b66;
  font-size: 12px;
  font-weight: 700;
}
.report-filters input,
.report-filters select {
  width: 100%;
  min-height: 36px;
  border: 1px solid var(--spd-line, #dbe5ec);
  border-radius: 7px;
  background: #fff;
  color: #102033;
  padding: 0 10px;
  font: inherit;
  font-weight: 500;
}
.report-filters input:focus,
.report-filters select:focus {
  outline: none;
  border-color: var(--spd-primary, #1f8d7a);
  box-shadow: 0 0 0 3px rgba(31, 141, 122, 0.16);
}
.date-range {
  display: grid;
  grid-template-columns: minmax(112px, 1fr) auto minmax(112px, 1fr);
  align-items: center;
  gap: 6px;
}
.date-range i { color: #72848f; font-style: normal; }
.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.report-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 22px;
  min-height: 44px;
  padding: 8px 16px;
  background: #fff;
  border: 1px solid var(--spd-line, #e6ecef);
  border-radius: 10px;
  color: #455463;
  font-size: 13px;
}
.report-summary strong { color: #102033; font-size: 16px; font-weight: 800; }
.report-summary .primary-text { color: var(--spd-primary, #1f8d7a); }
.report-summary .danger-text { color: #b42318; }
.report-summary .warning-text { color: #a15c08; font-weight: 800; }

.report-table-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
  border: 1px solid var(--spd-line, #e6ecef);
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.report-table {
  width: 100%;
  font-size: 13px;
}
.report-table :deep(th .cell) {
  color: #294b52;
  font-weight: 800;
  font-size: 12px;
}
.report-table :deep(.cell-sub) {
  display: block;
  margin-top: 2px;
  color: #72848f;
  font-size: 11px;
}
.report-table :deep(.progress-cell) {
  display: grid;
  grid-template-columns: minmax(80px, 1fr) 56px;
  align-items: center;
  gap: 8px;
}
.report-table :deep(.progress-cell > span) {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #dbe8ea;
}
.report-table :deep(.progress-cell > span i) {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #16867d;
}
.report-table :deep(.progress-cell strong) {
  color: #23565a;
  font-size: 12px;
  text-align: right;
}
.report-table :deep(.status-pill) {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border-radius: 5px;
  background: #e7f5f2;
  color: #087268;
  padding: 0 8px;
  font-style: normal;
  font-weight: 800;
  font-size: 12px;
}
.report-table :deep(.status-pill.neutral) {
  background: #eef3f5;
  color: #49616d;
}
.report-table :deep(.positive-text) { color: #087268; }
.report-table :deep(.danger-text) { color: #b42318; }
.report-table :deep(.strong-number) { color: #102033; font-weight: 900; }
.report-table :deep(.warning-text) { color: #a15c08; font-weight: 800; }

@media (max-width: 1480px) {
  .report-toolbar { grid-template-columns: 1fr; }
  .report-actions { justify-content: flex-start; }
  .report-filters { grid-template-columns: repeat(3, minmax(160px, 1fr)); }
}
@media (max-width: 1100px) {
  .report-filters { grid-template-columns: repeat(2, minmax(160px, 1fr)); }
}
@media (max-width: 720px) {
  .report-filters { grid-template-columns: 1fr; }
  .report-actions { display: grid; grid-template-columns: repeat(2, 1fr); }
  .report-actions .btn { width: 100%; }
}
@media print {
  .sidebar, .topbar, .report-toolbar, .report-page > .md-header, .hospital-pagination { display: none !important; }
  .app-shell { display: block !important; height: auto !important; }
  .main-area { overflow: visible !important; padding: 0 !important; background: #fff !important; }
  .report-page { padding: 0 !important; }
  .report-summary, .report-table-card { box-shadow: none; border-color: #ccc; }
}
</style>
