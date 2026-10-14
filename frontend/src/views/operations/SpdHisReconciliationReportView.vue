<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Download, FileSpreadsheet, RefreshCw, Search,
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import {
  exportSpdHisReconciliationExcel,
  fetchSpdHisReconciliation,
  recordSpdHisPdfExport,
  type ReconciliationQuery,
  type ReconciliationSummary,
  type SpdHisReconciliationRow,
} from '../../api/reportCenter'

const today = new Date()
const thirtyDaysAgo = new Date(today)
thirtyDaysAgo.setDate(today.getDate() - 29)

const query = reactive<ReconciliationQuery>({
  dateFrom: formatInputDate(thirtyDaysAgo),
  dateTo: formatInputDate(today),
  department: '',
  productKeyword: '',
  patientKeyword: '',
  riskType: 'ALL',
  onlyExceptions: false,
  page: 1,
  size: 20,
})
const rows = ref<SpdHisReconciliationRow[]>([])
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const summary = ref<ReconciliationSummary>(emptySummary())

function formatInputDate(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function emptySummary(): ReconciliationSummary {
  return {
    totalCount: 0,
    exceptionCount: 0,
    differenceAmount: 0,
    missingChargeCount: 0,
    duplicateChargeCount: 0,
    suspectedSwapCount: 0,
  }
}

function numberValue(value: unknown) {
  return Number(value || 0)
}

function normalizeSummary(source?: Record<string, number>): ReconciliationSummary {
  return {
    totalCount: numberValue(source?.totalCount),
    exceptionCount: numberValue(source?.exceptionCount),
    differenceAmount: numberValue(source?.differenceAmount),
    missingChargeCount: numberValue(source?.missingChargeCount),
    duplicateChargeCount: numberValue(source?.duplicateChargeCount),
    suspectedSwapCount: numberValue(source?.suspectedSwapCount),
  }
}

function amount(value: number) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function quantity(value: number) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

function readableError(reason: unknown, fallback: string) {
  if (!(reason instanceof Error)) return fallback
  return reason.message && reason.message !== 'Request failed' ? reason.message : fallback
}

function difference(value: number) {
  const normalized = Number(value || 0)
  return `${normalized > 0 ? '+' : ''}${quantity(normalized)}`
}

async function loadReport() {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchSpdHisReconciliation({ ...query })
    rows.value = result.rows
    total.value = result.total
    summary.value = normalizeSummary(result.summary)
  } catch (reason) {
    rows.value = []
    total.value = 0
    summary.value = emptySummary()
    error.value = readableError(reason, '对账报表加载失败，请稍后重试')
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.dateFrom = formatInputDate(thirtyDaysAgo)
  query.dateTo = formatInputDate(today)
  query.department = ''
  query.productKeyword = ''
  query.patientKeyword = ''
  query.riskType = 'ALL'
  query.onlyExceptions = false
  query.page = 1
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
  try {
    const blob = await exportSpdHisReconciliationExcel({ ...query, page: undefined, size: undefined })
    const href = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = href
    link.download = `SPD-HIS收费核对对账报表_${query.dateFrom}_${query.dateTo}.xlsx`
    link.click()
    URL.revokeObjectURL(href)
    ElMessage.success('Excel 已开始下载')
  } catch (reason) {
    error.value = readableError(reason, 'Excel 导出失败，请稍后重试')
    ElMessage.error(error.value)
  } finally {
    exporting.value = false
  }
}

async function exportPdf() {
  try {
    await recordSpdHisPdfExport()
    window.print()
  } catch (reason) {
    error.value = readableError(reason, 'PDF 导出准备失败，请稍后重试')
    ElMessage.error(error.value)
  }
}

onMounted(() => void loadReport())
</script>

<template>
  <main class="report-page reconciliation-page">
    <MdPageHeader
      crumb="运营与决策 / 报表中心 / 医保合规与 DRG"
      title="SPD 消耗 - HIS 收费核对对账报表"
      description="SPD 实际消耗数据与 HIS 收费明细按患者、耗材、科室及自然日核对，患者信息仅脱敏展示。"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadReport"
    />

    <section class="report-toolbar">
      <div class="report-filters">
        <label><span>结算日期</span><div class="date-range"><input v-model="query.dateFrom" type="date" /><i>至</i><input v-model="query.dateTo" type="date" /></div></label>
        <label><span>科室</span><input v-model.trim="query.department" type="search" placeholder="全部科室" @keyup.enter="changePage(1)" /></label>
        <label><span>耗材</span><input v-model.trim="query.productKeyword" type="search" placeholder="编码 / 名称 / 医保编码" @keyup.enter="changePage(1)" /></label>
        <label><span>患者</span><input v-model.trim="query.patientKeyword" type="search" placeholder="姓名 / 住院号" @keyup.enter="changePage(1)" /></label>
        <label><span>风险类型</span><select v-model="query.riskType"><option value="ALL">全部风险</option><option value="MISSING_CHARGE">漏计费</option><option value="DUPLICATE_CHARGE">重复计费</option><option value="SUSPECTED_SWAP">疑似串换</option><option value="CONSISTENT">一致</option></select></label>
        <label class="switch"><input v-model="query.onlyExceptions" type="checkbox" /><span>仅看异常</span></label>
      </div>
      <div class="report-actions">
        <button class="btn btn-primary" type="button" :disabled="loading" @click="changePage(1)"><Search :size="16" />查询</button>
        <button class="btn" type="button" :disabled="loading" @click="resetQuery"><RefreshCw :size="16" />重置</button>
        <button class="btn" type="button" :disabled="exporting" @click="exportExcel"><FileSpreadsheet :size="16" />导出 Excel</button>
        <button class="btn" type="button" @click="exportPdf"><Download :size="16" />导出 PDF</button>
      </div>
    </section>

    <StatusMessage :message="error" tone="error" role="alert" />

    <section class="report-summary" aria-label="对账结果汇总">
      <span>共 <strong>{{ summary.totalCount.toLocaleString('zh-CN') }}</strong> 条</span>
      <span>异常 <strong class="danger-text">{{ summary.exceptionCount.toLocaleString('zh-CN') }}</strong> 条</span>
      <span>差异金额 <strong class="danger-text">¥{{ amount(summary.differenceAmount) }}</strong></span>
      <i class="divider"></i>
      <span class="summary-chip missing">漏计费 {{ summary.missingChargeCount }}</span>
      <span class="summary-chip duplicate">重复计费 {{ summary.duplicateChargeCount }}</span>
      <span class="summary-chip swap">疑似串换 {{ summary.suspectedSwapCount }}</span>
    </section>

    <section class="report-table-card" :aria-busy="loading">
      <el-table
        :data="rows"
        stripe
        row-key="reconciliationDate"
        class="report-table reconciliation-table"
        :empty-text="loading ? '正在加载…' : '当前筛选条件下暂无对账数据'"
      >
        <el-table-column type="index" label="序号" width="64" align="center" :index="(i: number) => (Number(query.page) - 1) * Number(query.size) + i + 1" />
        <el-table-column prop="reconciliationDate" label="核对日期" width="120" />
        <el-table-column prop="productCode" label="耗材编码" min-width="120" />
        <el-table-column prop="medicalInsuranceCode" label="医保编码" min-width="120" />
        <el-table-column prop="hisMedicalInsuranceCode" label="HIS 医保编码" min-width="120" />
        <el-table-column prop="productName" label="耗材名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="specModel" label="规格" min-width="120" />
        <el-table-column prop="departmentName" label="科室" min-width="120" />
        <el-table-column label="患者 / 住院号" min-width="180">
          <template #default="{ row }"><strong>{{ row.patientName }}</strong><small class="cell-sub">{{ row.patientNo }}</small></template>
        </el-table-column>
        <el-table-column prop="spdConsumptionQuantity" label="SPD 消耗数量" width="120" align="right">
          <template #default="{ row }">{{ quantity(row.spdConsumptionQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="hisChargeQuantity" label="HIS 计费数量" width="120" align="right">
          <template #default="{ row }">{{ quantity(row.hisChargeQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="differenceQuantity" label="差异数量" width="110" align="right" :cell-class-name="'diff-cell'">
          <template #default="{ row }">{{ difference(row.differenceQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="differenceAmount" label="差异金额" width="120" align="right" :cell-class-name="'diff-cell'">
          <template #default="{ row }">¥{{ amount(row.differenceAmount) }}</template>
        </el-table-column>
        <el-table-column prop="chargeTime" label="收费时间" min-width="160" />
        <el-table-column prop="differenceReason" label="差异原因标记" min-width="160">
          <template #default="{ row }"><span class="reason-tag" :class="row.riskCode.toLowerCase()">{{ row.differenceReason }}</span></template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险标记" width="100" fixed="right">
          <template #default="{ row }"><span class="risk-tag" :class="row.riskCode.toLowerCase()">{{ row.riskLevel }}</span></template>
        </el-table-column>
      </el-table>
      <PaginationControls
        :page="Number(query.page)"
        :size="Number(query.size)"
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
   报表中心 / 医保合规与 DRG · SPD-HIS 收费核对对账报表
   ---------------------------------------------------------------------
   设计目标：
   1. 顶部条：md-header 渐变底 + 主题色描边，无动作按钮。
   2. 工具区：查询/动作同一卡片内部，16px 圆角，无内嵌子卡片。
   3. 汇总条：与表格卡片之间空 14px，灰底 + 主题强调数字。
   4. 表格：el-table 主题色表头，差异列红字加粗，分页器在底部。
   5. 内容区 padding：16px 20px 24px，与全平台一致。
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
  grid-template-columns: minmax(280px, 1.2fr) repeat(4, minmax(140px, 1fr)) auto;
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
  transition: border-color 0.15s, box-shadow 0.15s;
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
.report-filters label.switch {
  align-self: end;
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  font-size: 13px;
  font-weight: 600;
  color: #455463;
  white-space: nowrap;
}
.report-filters label.switch input {
  width: 16px;
  min-height: 16px;
  accent-color: var(--spd-primary, #1f8d7a);
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

/* 汇总条 */
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
.report-summary .danger-text { color: #b42318; }
.report-summary .divider { width: 1px; height: 20px; background: var(--spd-line, #e6ecef); }
.summary-chip {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  border-radius: 5px;
  padding: 0 9px;
  font-size: 12px;
  font-weight: 800;
}
.summary-chip.missing { color: #b42318; background: #feeceb; }
.summary-chip.duplicate { color: #a15c08; background: #fff2dd; }
.summary-chip.swap { color: #255ea8; background: #e8f1ff; }

/* 表格卡片 */
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
.reconciliation-table {
  width: 100%;
  font-size: 13px;
}
.reconciliation-table :deep(th .cell) {
  color: #294b52;
  font-weight: 800;
  font-size: 12px;
}
.reconciliation-table :deep(.cell-sub) {
  display: block;
  margin-top: 2px;
  color: #72848f;
  font-size: 11px;
}
.reconciliation-table :deep(.diff-cell) {
  color: #b42318;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.reconciliation-table :deep(td .reason-tag),
.reconciliation-table :deep(td .risk-tag) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  border: 1px solid;
  border-radius: 5px;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}
.reconciliation-table :deep(.reason-tag.missing_charge),
.reconciliation-table :deep(.risk-tag.missing_charge) {
  border-color: #f0a6a0;
  color: #b42318;
  background: #fff6f5;
}
.reconciliation-table :deep(.reason-tag.duplicate_charge),
.reconciliation-table :deep(.risk-tag.duplicate_charge) {
  border-color: #edc47d;
  color: #a15c08;
  background: #fffaf0;
}
.reconciliation-table :deep(.reason-tag.suspected_swap),
.reconciliation-table :deep(.risk-tag.suspected_swap) {
  border-color: #9fc0ee;
  color: #255ea8;
  background: #f5f8ff;
}
.reconciliation-table :deep(.reason-tag.consistent),
.reconciliation-table :deep(.risk-tag.consistent) {
  border-color: #9bd4c5;
  color: #087268;
  background: #f2fbf8;
}

/* 响应式 */
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
