<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChevronDown,
  Columns3,
  Download,
  FileSpreadsheet,
  ListChecks,
  RefreshCw,
  Search,
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
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

/* ===== 列表列配置 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 使用默认的 'fixed'：每列宽度互相独立，
       拖拽某一列不会因为容器宽度变化而影响其它列；
     - 列宽与列显隐持久化到 localStorage。
   ===================================================================== */
const RECONCILIATION_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'index', label: '序号', width: 64, visible: true, weight: 1, minWidth: 56, maxWidth: 90 },
  { key: 'reconciliationDate', label: '核对日期', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 160 },
  { key: 'productCode', label: '耗材编码', width: 130, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'medicalInsuranceCode', label: '医保编码', width: 130, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'hisMedicalInsuranceCode', label: 'HIS 医保编码', width: 140, visible: true, weight: 4, minWidth: 110, maxWidth: 210 },
  { key: 'productName', label: '耗材名称', width: 200, visible: true, flexible: true, idealWidth: 240, weight: 9, minWidth: 150, maxWidth: 380 },
  { key: 'specModel', label: '规格', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'departmentName', label: '科室', width: 130, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'patient', label: '患者 / 住院号', width: 180, visible: true, flexible: true, idealWidth: 220, weight: 7, minWidth: 140, maxWidth: 300 },
  { key: 'spdConsumptionQuantity', label: 'SPD 消耗数量', width: 130, visible: true, weight: 4, minWidth: 110, maxWidth: 190 },
  { key: 'hisChargeQuantity', label: 'HIS 计费数量', width: 130, visible: true, weight: 4, minWidth: 110, maxWidth: 190 },
  { key: 'differenceQuantity', label: '差异数量', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 170 },
  { key: 'differenceAmount', label: '差异金额', width: 130, visible: true, weight: 4, minWidth: 110, maxWidth: 190 },
  { key: 'chargeTime', label: '收费时间', width: 160, visible: true, weight: 5, minWidth: 130, maxWidth: 240 },
  { key: 'differenceReason', label: '差异原因标记', width: 160, visible: true, weight: 5, minWidth: 130, maxWidth: 240 },
  { key: 'riskLevel', label: '风险标记', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160, fill: true }
]

const reconciliationCols = useListColumns({
  storageKey: 'spd-his-reconciliation-cols-v1',
  defaults: RECONCILIATION_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「耗材名称 / 患者」等长文本列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const reconciliationTableRef = reconciliationCols.tableRef
const isReconciliationColVisible = (key: string) => reconciliationCols.isColumnVisible(key)
const reconciliationColBinding = (key: string) => reconciliationCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const reconciliationActionFix = useActionColumnFix()
const reconciliationActionFixed = computed(() => reconciliationActionFix.actionFixedBinding.value)

watch(
  [reconciliationTableRef],
  () => {
    reconciliationActionFix.observe(reconciliationTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const reconciliationColSettingsAnchorRef = ref<HTMLElement | null>(null)
const reconciliationColSettingsPopRef = ref<HTMLElement | null>(null)
const reconciliationColSettingsPop = useColSettingsPopover({
  open: reconciliationCols.settingsOpen,
  anchorRef: reconciliationColSettingsAnchorRef,
  popRef: reconciliationColSettingsPopRef
})

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
  <main class="report-page reconciliation-page spd-unified-list">
    <MdPageHeader
      crumb="运营与决策 / 报表中心 / 医保合规与 DRG"
      title="SPD 消耗 - HIS 收费核对对账报表"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadReport"
    />

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
        <SpdButton label="查询" tone="primary" :disabled="loading" @click="changePage(1)">
          <template #icon><Search :size="16" /></template>
        </SpdButton>
        <SpdButton label="重置" tone="neutral" :disabled="loading" @click="resetQuery">
          <template #icon><RefreshCw :size="16" /></template>
        </SpdButton>
        <SpdButton label="导出 Excel" tone="primary" :disabled="exporting" @click="exportExcel">
          <template #icon><FileSpreadsheet :size="16" /></template>
        </SpdButton>
        <SpdButton label="导出 PDF" tone="primary" @click="exportPdf">
          <template #icon><Download :size="16" /></template>
        </SpdButton>
      </div>
    </section>

    <section class="report-panel report-summary-panel">
      <div class="section-title">
        <i class="section-title-icon is-summary"><ListChecks :size="16" /></i>
        <div>
          <h3>风险汇总</h3>
          <p>按风险类型聚合；顶部 KPI 卡已移除，汇总指标统一在此展示。</p>
        </div>
      </div>
      <div class="report-summary">
        <span class="summary-text">核对记录数 <strong>{{ summary.totalCount.toLocaleString('zh-CN') }}</strong> 条</span>
        <span class="summary-text">异常记录数 <strong class="danger-text">{{ summary.exceptionCount.toLocaleString('zh-CN') }}</strong> 条</span>
        <span class="summary-text">差异金额 <strong class="danger-text">¥{{ amount(summary.differenceAmount) }}</strong></span>
        <i class="divider"></i>
        <span class="summary-chip missing">漏计费 {{ summary.missingChargeCount }}</span>
        <span class="summary-chip duplicate">重复计费 {{ summary.duplicateChargeCount }}</span>
        <span class="summary-chip swap">疑似串换 {{ summary.suspectedSwapCount }}</span>
      </div>
    </section>

    <MdDataTableCard :loading="loading">
      <template #toolbar>
        <!-- 查询 / 重置 / 导出 按钮保留在「查询条件」面板内，
             表格工具条只承载列设置，避免同一动作出现两处。 -->
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="reconciliationColSettingsAnchorRef"
           
           
            @click="reconciliationCols.settingsOpen.value = !reconciliationCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="reconciliationCols.settingsOpen.value"
ref="reconciliationColSettingsPopRef"
:class="{ 'is-align-right': reconciliationColSettingsPop.align.value === 'right', 'is-align-up': reconciliationColSettingsPop.vertical.value === 'up' }"
:style="reconciliationColSettingsPop.popStyle.value"
:columns="reconciliationCols.columns.value"
:visible-column-count="reconciliationCols.visibleColumnCount.value"
@toggle="reconciliationCols.setColumnVisible"
@move="reconciliationCols.moveColumn"
@reset="reconciliationCols.resetColumns()"
@confirm="reconciliationCols.settingsOpen.value = false"
          />
        </div>
      </template>
      <template #meta-extra>
        <span class="md-chip primary">共 {{ total.toLocaleString('zh-CN') }} 条</span>
      </template>

      <div class="ulc-table-wrap">
        <el-table
          ref="reconciliationTableRef"
          v-loading="loading"
          :data="rows"
          stripe
          border
          row-key="reconciliationDate"
          class="ulc-table is-sticky"
          max-height="560"
          style="width: 100%"
          :empty-text="loading ? '正在加载…' : '当前筛选条件下暂无对账数据'"
          @header-dragend="(width: number, oldWidth: number, column: any) => reconciliationCols.handleHeaderDragEnd(width, oldWidth, column)"
        >
          <el-table-column
            column-key="index"
            v-if="isReconciliationColVisible('index')"
            type="index"
            align="center"
            v-bind="reconciliationColBinding('index')"
            :index="(i: number) => (Number(query.page) - 1) * Number(query.size) + i + 1"
          >
            <template #header>
              <span class="ulc-th-text">序号</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="reconciliationDate"
            v-if="isReconciliationColVisible('reconciliationDate')"
            prop="reconciliationDate"
            v-bind="reconciliationColBinding('reconciliationDate')"
          >
            <template #header>
              <span class="ulc-th-text">核对日期</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="productCode"
            v-if="isReconciliationColVisible('productCode')"
            prop="productCode"
            v-bind="reconciliationColBinding('productCode')"
          >
            <template #header>
              <span class="ulc-th-text">耗材编码</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="medicalInsuranceCode"
            v-if="isReconciliationColVisible('medicalInsuranceCode')"
            prop="medicalInsuranceCode"
            v-bind="reconciliationColBinding('medicalInsuranceCode')"
          >
            <template #header>
              <span class="ulc-th-text">医保编码</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="hisMedicalInsuranceCode"
            v-if="isReconciliationColVisible('hisMedicalInsuranceCode')"
            prop="hisMedicalInsuranceCode"
            v-bind="reconciliationColBinding('hisMedicalInsuranceCode')"
          >
            <template #header>
              <span class="ulc-th-text">HIS 医保编码</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="productName"
            v-if="isReconciliationColVisible('productName')"
            prop="productName"
            v-bind="reconciliationColBinding('productName')"
          >
            <template #header>
              <span class="ulc-th-text">耗材名称</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="specModel"
            v-if="isReconciliationColVisible('specModel')"
            prop="specModel"
            v-bind="reconciliationColBinding('specModel')"
          >
            <template #header>
              <span class="ulc-th-text">规格</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="departmentName"
            v-if="isReconciliationColVisible('departmentName')"
            prop="departmentName"
            v-bind="reconciliationColBinding('departmentName')"
          >
            <template #header>
              <span class="ulc-th-text">科室</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="patient"
            v-if="isReconciliationColVisible('patient')"
            v-bind="reconciliationColBinding('patient')"
          >
            <template #header>
              <span class="ulc-th-text">患者 / 住院号</span>
            </template>
            <template #default="{ row }">
              <div class="ulc-cell-stack">
                <span class="ulc-strong ulc-wrap-text">{{ row.patientName }}</span>
                <span class="ulc-sub ulc-wrap-text is-sub">{{ row.patientNo }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            column-key="spdConsumptionQuantity"
            v-if="isReconciliationColVisible('spdConsumptionQuantity')"
            prop="spdConsumptionQuantity"
            v-bind="reconciliationColBinding('spdConsumptionQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">SPD 消耗数量</span>
            </template>
            <template #default="{ row }">{{ quantity(row.spdConsumptionQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="hisChargeQuantity"
            v-if="isReconciliationColVisible('hisChargeQuantity')"
            prop="hisChargeQuantity"
            v-bind="reconciliationColBinding('hisChargeQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">HIS 计费数量</span>
            </template>
            <template #default="{ row }">{{ quantity(row.hisChargeQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="differenceQuantity"
            v-if="isReconciliationColVisible('differenceQuantity')"
            prop="differenceQuantity"
            :cell-class-name="'diff-cell'"
            v-bind="reconciliationColBinding('differenceQuantity')"
          >
            <template #header>
              <span class="ulc-th-text">差异数量</span>
            </template>
            <template #default="{ row }">{{ difference(row.differenceQuantity) }}</template>
          </el-table-column>
          <el-table-column
            column-key="differenceAmount"
            v-if="isReconciliationColVisible('differenceAmount')"
            prop="differenceAmount"
            :cell-class-name="'diff-cell'"
            v-bind="reconciliationColBinding('differenceAmount')"
          >
            <template #header>
              <span class="ulc-th-text">差异金额</span>
            </template>
            <template #default="{ row }">¥{{ amount(row.differenceAmount) }}</template>
          </el-table-column>
          <el-table-column
            column-key="chargeTime"
            v-if="isReconciliationColVisible('chargeTime')"
            prop="chargeTime"
            v-bind="reconciliationColBinding('chargeTime')"
          >
            <template #header>
              <span class="ulc-th-text">收费时间</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="differenceReason"
            v-if="isReconciliationColVisible('differenceReason')"
            prop="differenceReason"
            v-bind="reconciliationColBinding('differenceReason')"
          >
            <template #header>
              <span class="ulc-th-text">差异原因标记</span>
            </template>
            <template #default="{ row }"><span class="reason-tag" :class="row.riskCode.toLowerCase()">{{ row.differenceReason }}</span></template>
          </el-table-column>
          <el-table-column
            column-key="riskLevel"
            v-if="isReconciliationColVisible('riskLevel')"
            prop="riskLevel"
            :fixed="reconciliationActionFixed"
            v-bind="reconciliationColBinding('riskLevel')"
          >
            <template #header>
              <span class="ulc-th-text">风险标记</span>
            </template>
            <template #default="{ row }"><span class="risk-tag" :class="row.riskCode.toLowerCase()">{{ row.riskLevel }}</span></template>
          </el-table-column>
        </el-table>
      </div>
      <template #pagination>
        <MdPagination
          :page="Number(query.page)"
          :total-pages="Math.max(1, Math.ceil(total / Number(query.size)))"
          :total="total"
          :size="Number(query.size)"
          :loading="loading"
          @update:page="changePage"
          @update:size="changeSize"
        />
      </template>
    </MdDataTableCard>
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

/* ---------- 顶部 4 项 KPI 摘要 ----------
   顶部 KPI 卡已按产品方向移除；核对记录数 / 异常记录数 / 漏计费 / 差异金额
   已合并到「风险汇总」条中，避免同一指标在页面上重复出现。 */

/* ---------- 通用面板 ---------- */
.report-panel {
  border: 1px solid var(--spd-p-border);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-p-bg);
  padding: 18px;
  box-shadow: var(--spd-p-shadow-1);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--spd-p-border);
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
  background: var(--spd-p-success-bg);
  color: var(--spd-p-success-strong);
}
.section-title-icon.is-summary {
  background: var(--spd-p-border);
  color: var(--spd-p-info-strong);
}

.section-title h3 {
  margin: 0;
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
  color: var(--spd-text-heading);
}

.section-title p {
  margin: 2px 0 0;
  font-size: var(--spd-font-size-sm);
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
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

.report-filters input,
.report-filters select {
  width: 100%;
  min-height: 36px;
  height: 36px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 7px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  padding: 0 10px;
  font: inherit;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.report-filters input:focus,
.report-filters select:focus {
  outline: none;
  border-color: var(--spd-p-border-strong);
  box-shadow: none;
}

.date-range {
  display: grid;
  grid-template-columns: minmax(112px, 1fr) auto minmax(112px, 1fr);
  align-items: center;
  gap: 6px;
}

.date-range i {
  color: var(--spd-p-text-muted);
  font-style: normal;
}

.report-filters label.switch {
  align-self: end;
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  color: var(--spd-p-text-muted);
  white-space: nowrap;
}

.report-filters label.switch input {
  width: 16px;
  min-height: 16px;
  accent-color: var(--spd-p-success);
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--spd-p-border);
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
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-muted);
}

.report-summary .summary-text {
  color: var(--spd-p-text-secondary);
}

.report-summary strong {
  color: var(--spd-p-text);
  font-size: var(--spd-font-size-base);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.report-summary .danger-text {
  color: var(--spd-p-danger-strong);
}

.report-summary .divider {
  width: 1px;
  height: 20px;
  background: var(--spd-p-border);
}

.summary-chip {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  border-radius: 999px;
  border: 1px solid;
  padding: 0 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  white-space: nowrap;
}

.summary-chip.missing {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-warning-bg);
  border-color: var(--spd-p-warning-border);
}

.summary-chip.duplicate {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border-color: var(--spd-p-warning-border);
}

.summary-chip.swap {
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-info-bg);
  border-color: var(--spd-p-text-secondary);
}

.summary-chip.consistent {
  color: var(--spd-p-primary);
  background: var(--spd-p-success-bg);
  border-color: var(--spd-p-success);
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
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn:hover:not(:disabled) {
  background: var(--spd-p-bg-muted);
  border-color: var(--spd-p-text-muted);
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(180deg, var(--spd-p-success) 0%, var(--spd-p-success) 100%);
  border-color: var(--spd-p-success-strong);
  color: var(--spd-p-bg);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(180deg, var(--spd-p-success) 0%, var(--spd-p-success-strong) 100%);
  border-color: var(--spd-p-primary-strong);
  color: var(--spd-p-bg);
}

/* ---------- 表格 ----------
   表头 / 斑马纹 / 拖拽热区等通用外观由 unified-list.css 的
   .spd-unified-list .ulc-table 提供，这里只保留本页特有的
   差异列与风险标签样式。 */

/* 差异列：红字加粗 */
.ulc-table :deep(.diff-cell) {
  color: var(--spd-p-danger-strong);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

/* 风险 / 原因 tag */
.ulc-table :deep(.reason-tag),
.ulc-table :deep(.risk-tag) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  border: 1px solid;
  border-radius: 999px;
  padding: 0 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  white-space: nowrap;
}

.ulc-table :deep(.reason-tag.missing_charge),
.ulc-table :deep(.risk-tag.missing_charge) {
  border-color: var(--spd-p-danger-border);
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
}

.ulc-table :deep(.reason-tag.duplicate_charge),
.ulc-table :deep(.risk-tag.duplicate_charge) {
  border-color: var(--spd-p-warning-border);
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
}

.ulc-table :deep(.reason-tag.suspected_swap),
.ulc-table :deep(.risk-tag.suspected_swap) {
  border-color: var(--spd-p-info-border);
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-border);
}

.ulc-table :deep(.reason-tag.consistent),
.ulc-table :deep(.risk-tag.consistent) {
  border-color: var(--spd-p-success);
  color: var(--spd-p-primary);
  background: var(--spd-p-success-bg);
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
    background: var(--spd-p-bg) !important;
  }
  .report-page {
    padding: 0 !important;
  }
  .report-summary-panel,
  .md-table-card {
    box-shadow: none;
    border-color: var(--spd-p-text-secondary);
  }
}
</style>
