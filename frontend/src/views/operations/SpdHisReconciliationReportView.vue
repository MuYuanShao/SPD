<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  AlertTriangle,
  Download,
  FileSpreadsheet,
  ListChecks,
  RefreshCw,
  Search,
  Wallet,
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

    <!-- 顶部 4 项 KPI 摘要 -->
    <section class="report-kpi-card" aria-label="对账结果汇总">
      <div class="report-kpi-item is-total">
        <i class="report-kpi-icon is-total"><ListChecks :size="18" /></i>
        <div>
          <span class="report-kpi-label">核对记录数</span>
          <strong class="report-kpi-value">{{ summary.totalCount.toLocaleString('zh-CN') }} <small>条</small></strong>
          <span class="report-kpi-hint">当前筛选条件下共 {{ total.toLocaleString('zh-CN') }} 条</span>
        </div>
      </div>
      <div class="report-kpi-item is-exception">
        <i class="report-kpi-icon is-exception"><AlertTriangle :size="18" /></i>
        <div>
          <span class="report-kpi-label">异常记录数</span>
          <strong class="report-kpi-value">{{ summary.exceptionCount.toLocaleString('zh-CN') }} <small>条</small></strong>
          <span class="report-kpi-hint">漏计费 / 重复计费 / 疑似串换</span>
        </div>
      </div>
      <div class="report-kpi-item is-missing">
        <i class="report-kpi-icon is-missing"><FileSpreadsheet :size="18" /></i>
        <div>
          <span class="report-kpi-label">漏计费</span>
          <strong class="report-kpi-value">{{ summary.missingChargeCount.toLocaleString('zh-CN') }} <small>条</small></strong>
          <span class="report-kpi-hint">SPD 已消耗但 HIS 未计费</span>
        </div>
      </div>
      <div class="report-kpi-item is-amount">
        <i class="report-kpi-icon is-amount"><Wallet :size="18" /></i>
        <div>
          <span class="report-kpi-label">差异金额</span>
          <strong class="report-kpi-value">¥{{ amount(summary.differenceAmount) }}</strong>
          <span class="report-kpi-hint">含税金额差（SPD - HIS）</span>
        </div>
      </div>
    </section>

    <StatusMessage :message="error" tone="error" role="alert" />

    <section class="report-panel report-toolbar-panel">
      <div class="section-title">
        <i class="section-title-icon is-toolbar"><Search :size="16" /></i>
        <div>
          <h3>查询条件</h3>
          <p>选择结算日期范围、科室、耗材、患者、风险类型筛选对账记录。</p>
        </div>
      </div>
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

    <section class="report-panel report-summary-panel">
      <div class="section-title">
        <i class="section-title-icon is-summary"><ListChecks :size="16" /></i>
        <div>
          <h3>风险汇总</h3>
          <p>本屏按风险类型聚合，按风险等级标记的差异金额已在 KPI 卡体现。</p>
        </div>
      </div>
      <div class="report-summary">
        <span class="summary-text">共 <strong>{{ summary.totalCount.toLocaleString('zh-CN') }}</strong> 条</span>
        <span class="summary-text">异常 <strong class="danger-text">{{ summary.exceptionCount.toLocaleString('zh-CN') }}</strong> 条</span>
        <span class="summary-text">差异金额 <strong class="danger-text">¥{{ amount(summary.differenceAmount) }}</strong></span>
        <i class="divider"></i>
        <span class="summary-chip missing">漏计费 {{ summary.missingChargeCount }}</span>
        <span class="summary-chip duplicate">重复计费 {{ summary.duplicateChargeCount }}</span>
        <span class="summary-chip swap">疑似串换 {{ summary.suspectedSwapCount }}</span>
      </div>
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
   1. 顶部 4 张主题色 KPI 卡（共 N / 异常 / 漏计费 / 差异金额）。
   2. 工具区：section-title 风格，左侧筛选，右侧动作。
   3. 风险汇总条：浅色背景 + 4 种风险 pill。
   4. 表格：el-table 主题色表头，差异列红字加粗。
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

/* ---------- 顶部 4 项 KPI 摘要（4 张不同主题色卡） ---------- */
.report-kpi-card {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.report-kpi-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid;
  border-radius: var(--spd-radius-md, 10px);
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.report-kpi-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
}

/* ① 蓝色：共 N 条 */
.report-kpi-item.is-total {
  background: linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%);
  border-color: #93c5fd;
}

/* ② 红色：异常 */
.report-kpi-item.is-exception {
  background: linear-gradient(180deg, #fee2e2 0%, #fecaca 100%);
  border-color: #fca5a5;
}

/* ③ 金色：漏计费 */
.report-kpi-item.is-missing {
  background: linear-gradient(180deg, #fef3c7 0%, #fde68a 100%);
  border-color: #fcd34d;
}

/* ④ 紫色：差异金额 */
.report-kpi-item.is-amount {
  background: linear-gradient(180deg, #ede9fe 0%, #ddd6fe 100%);
  border-color: #c4b5fd;
}

.report-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.65);
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.report-kpi-item.is-total .report-kpi-icon {
  color: #1d4ed8;
}
.report-kpi-item.is-exception .report-kpi-icon {
  color: #b91c1c;
}
.report-kpi-item.is-missing .report-kpi-icon {
  color: #b45309;
}
.report-kpi-item.is-amount .report-kpi-icon {
  color: #6d28d9;
}

.report-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.report-kpi-label {
  font-size: 12px;
  font-weight: 500;
  opacity: 0.85;
}

.report-kpi-value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.report-kpi-value small {
  font-size: 12px;
  font-weight: 400;
  margin-left: 2px;
  opacity: 0.75;
}

.report-kpi-hint {
  font-size: 11px;
  font-weight: 400;
  line-height: 1.3;
  opacity: 0.75;
}

/* 文字颜色对应主题 */
.report-kpi-item.is-total .report-kpi-label,
.report-kpi-item.is-total .report-kpi-value,
.report-kpi-item.is-total .report-kpi-hint {
  color: #1e3a8a;
}
.report-kpi-item.is-exception .report-kpi-label,
.report-kpi-item.is-exception .report-kpi-value,
.report-kpi-item.is-exception .report-kpi-hint {
  color: #7f1d1d;
}
.report-kpi-item.is-missing .report-kpi-label,
.report-kpi-item.is-missing .report-kpi-value,
.report-kpi-item.is-missing .report-kpi-hint {
  color: #78350f;
}
.report-kpi-item.is-amount .report-kpi-label,
.report-kpi-item.is-amount .report-kpi-value,
.report-kpi-item.is-amount .report-kpi-hint {
  color: #4c1d95;
}

/* ---------- 通用面板 ---------- */
.report-panel {
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  background: #fff;
  padding: 18px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.section-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  flex-shrink: 0;
}

.section-title-icon.is-toolbar {
  background: #d1fae5;
  color: #047857;
}
.section-title-icon.is-summary {
  background: #dbeafe;
  color: #1d4ed8;
}

.section-title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--spd-text-heading);
}

.section-title p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--spd-text-tertiary);
}

.section-title-tools {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---------- 工具区：section-title 风格，左筛选 + 右动作 ---------- */
.report-toolbar-panel {
  display: flex;
  flex-direction: column;
}

.report-toolbar-panel .section-title {
  margin-bottom: 12px;
}

.report-filters {
  display: grid;
  grid-template-columns: minmax(260px, 1.2fr) repeat(4, minmax(140px, 1fr)) auto;
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
  height: 36px;
  border: 1px solid #cbd5e1;
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
  border-color: #cbd5e1;
  box-shadow: none;
}

.date-range {
  display: grid;
  grid-template-columns: minmax(112px, 1fr) auto minmax(112px, 1fr);
  align-items: center;
  gap: 6px;
}

.date-range i {
  color: #72848f;
  font-style: normal;
}

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
  accent-color: #10b981;
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

/* ---------- 汇总条（风险 pill）---------- */
.report-summary-panel .section-title {
  margin-bottom: 8px;
}

.report-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 16px;
  font-size: 13px;
  color: #455463;
}

.report-summary .summary-text {
  color: #4b5b66;
}

.report-summary strong {
  color: #102033;
  font-size: 15px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.report-summary .danger-text {
  color: #b91c1c;
}

.report-summary .divider {
  width: 1px;
  height: 20px;
  background: #e2e8f0;
}

.summary-chip {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  border-radius: 999px;
  border: 1px solid;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.summary-chip.missing {
  color: #b42318;
  background: #fef3c7;
  border-color: #fde68a;
}

.summary-chip.duplicate {
  color: #a15c08;
  background: #fff2dd;
  border-color: #fde68a;
}

.summary-chip.swap {
  color: #255ea8;
  background: #e8f1ff;
  border-color: #9fc0ee;
}

.summary-chip.consistent {
  color: #087268;
  background: #d1fae5;
  border-color: #6ee7b7;
}

/* ---------- 按钮 ---------- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 32px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #94a3b8;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(180deg, #10b981 0%, #059669 100%);
  border-color: #047857;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(180deg, #059669 0%, #047857 100%);
  border-color: #065f46;
  color: #fff;
}

/* ---------- 表格卡片 ---------- */
.report-table-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.reconciliation-table {
  width: 100%;
  font-size: 13px;
}

.reconciliation-table :deep(th) {
  background: linear-gradient(180deg, #ecfdf5 0%, #d1fae5 100%) !important;
  border-bottom: 1px solid #6ee7b7 !important;
}

.reconciliation-table :deep(th .cell) {
  color: #065f46;
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.2px;
}

.reconciliation-table :deep(.el-table__header-wrapper) {
  border-bottom: 1px solid #6ee7b7;
}

.reconciliation-table :deep(td) {
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
}

.reconciliation-table :deep(.el-table__row--striped td) {
  background: #fafdfb !important;
}

.reconciliation-table :deep(.el-table__row:hover td) {
  background: #ecfdf5 !important;
}

.reconciliation-table :deep(.cell-sub) {
  display: block;
  margin-top: 2px;
  color: #64748b;
  font-size: 11.5px;
  font-weight: 400;
}

.reconciliation-table :deep(.diff-cell) {
  color: #b91c1c;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

/* 风险 / 原因 tag */
.reconciliation-table :deep(.reason-tag),
.reconciliation-table :deep(.risk-tag) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  border: 1px solid;
  border-radius: 999px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.reconciliation-table :deep(.reason-tag.missing_charge),
.reconciliation-table :deep(.risk-tag.missing_charge) {
  border-color: #fca5a5;
  color: #b42318;
  background: #fee2e2;
}

.reconciliation-table :deep(.reason-tag.duplicate_charge),
.reconciliation-table :deep(.risk-tag.duplicate_charge) {
  border-color: #fcd34d;
  color: #a15c08;
  background: #fef3c7;
}

.reconciliation-table :deep(.reason-tag.suspected_swap),
.reconciliation-table :deep(.risk-tag.suspected_swap) {
  border-color: #93c5fd;
  color: #255ea8;
  background: #dbeafe;
}

.reconciliation-table :deep(.reason-tag.consistent),
.reconciliation-table :deep(.risk-tag.consistent) {
  border-color: #6ee7b7;
  color: #087268;
  background: #d1fae5;
}

/* 响应式 */
@media (max-width: 1480px) {
  .report-filters {
    grid-template-columns: repeat(3, minmax(160px, 1fr));
  }
}
@media (max-width: 1100px) {
  .report-filters {
    grid-template-columns: repeat(2, minmax(160px, 1fr));
  }
  .report-kpi-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .report-filters {
    grid-template-columns: 1fr;
  }
  .report-actions {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
  }
  .report-actions .btn {
    width: 100%;
  }
  .report-kpi-card {
    grid-template-columns: 1fr;
  }
  .report-page {
    padding: 12px;
  }
}
@media print {
  .sidebar,
  .topbar,
  .report-toolbar-panel,
  .report-page > .md-header,
  .hospital-pagination {
    display: none !important;
  }
  .app-shell {
    display: block !important;
    height: auto !important;
  }
  .main-area {
    overflow: visible !important;
    padding: 0 !important;
    background: #fff !important;
  }
  .report-page {
    padding: 0 !important;
  }
  .report-summary-panel,
  .report-table-card {
    box-shadow: none;
    border-color: #ccc;
  }
}
</style>
