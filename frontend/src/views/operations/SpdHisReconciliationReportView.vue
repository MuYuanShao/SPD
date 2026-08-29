<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Download, FileSpreadsheet, LockKeyhole, RefreshCw, Search, ShieldAlert } from '@lucide/vue'
import PageHeader from '../../components/common/PageHeader.vue'
import TableStateRow from '../../components/common/TableStateRow.vue'
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

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / Number(query.size || 20))))
const pageNumbers = computed(() => {
  const current = Number(query.page || 1)
  const start = Math.max(1, Math.min(current - 2, totalPages.value - 4))
  const end = Math.min(totalPages.value, start + 4)
  return Array.from({ length: Math.max(0, end - start + 1) }, (_, index) => start + index)
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

async function loadReport(resetPage = false) {
  if (resetPage) query.page = 1
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
  if (page < 1 || page > totalPages.value || page === query.page) return
  query.page = page
  void loadReport()
}

async function exportExcel() {
  exporting.value = true
  error.value = ''
  try {
    const blob = await exportSpdHisReconciliationExcel({ ...query, page: undefined, size: undefined })
    const href = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = href
    link.download = `SPD-HIS收费核对对账报表_${query.dateFrom}_${query.dateTo}.xlsx`
    link.click()
    URL.revokeObjectURL(href)
  } catch (reason) {
    error.value = readableError(reason, 'Excel导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

async function exportPdf() {
  try {
    await recordSpdHisPdfExport()
    window.print()
  } catch (reason) {
    error.value = readableError(reason, 'PDF导出准备失败，请稍后重试')
  }
}

onMounted(() => loadReport())
</script>

<template>
  <main class="reconciliation-report-page">
    <PageHeader eyebrow="运营与决策 / 报表中心 / 医保合规与DRG" title="SPD消耗-HIS收费核对对账报表">
      <template #actions>
        <span class="inspection-badge"><ShieldAlert :size="15" />飞检核心报表</span>
        <span class="audit-badge"><LockKeyhole :size="14" />查看与导出行为已留痕</span>
      </template>
    </PageHeader>

    <section class="reconciliation-filter-panel">
      <div class="reconciliation-filters">
        <label><span>结算日期</span><div class="date-range"><input v-model="query.dateFrom" type="date" /><i>至</i><input v-model="query.dateTo" type="date" /></div></label>
        <label><span>科室</span><input v-model.trim="query.department" type="search" placeholder="全部科室" @keyup.enter="loadReport(true)" /></label>
        <label><span>耗材</span><input v-model.trim="query.productKeyword" type="search" placeholder="编码 / 名称 / 医保编码" @keyup.enter="loadReport(true)" /></label>
        <label><span>患者</span><input v-model.trim="query.patientKeyword" type="search" placeholder="姓名 / 住院号" @keyup.enter="loadReport(true)" /></label>
        <label><span>风险类型</span><select v-model="query.riskType"><option value="ALL">全部风险</option><option value="MISSING_CHARGE">漏计费</option><option value="DUPLICATE_CHARGE">重复计费</option><option value="SUSPECTED_SWAP">疑似串换</option><option value="CONSISTENT">一致</option></select></label>
        <label class="exception-switch"><input v-model="query.onlyExceptions" type="checkbox" /><span>仅看异常</span></label>
      </div>
      <div class="reconciliation-actions">
        <button class="btn primary" type="button" :disabled="loading" @click="loadReport(true)"><Search :size="16" />查询</button>
        <button class="btn" type="button" :disabled="loading" @click="resetQuery"><RefreshCw :size="16" />重置</button>
        <button class="btn" type="button" :disabled="exporting" @click="exportExcel"><FileSpreadsheet :size="16" />导出Excel</button>
        <button class="btn" type="button" @click="exportPdf"><Download :size="16" />导出PDF</button>
      </div>
    </section>

    <p v-if="error" class="reconciliation-error">{{ error }}</p>

    <section class="reconciliation-summary" aria-label="对账结果汇总">
      <span>共 <strong>{{ summary.totalCount.toLocaleString('zh-CN') }}</strong> 条</span>
      <span>异常 <strong class="danger-text">{{ summary.exceptionCount.toLocaleString('zh-CN') }}</strong> 条</span>
      <span>差异金额 <strong class="danger-text">¥{{ amount(summary.differenceAmount) }}</strong></span>
      <i></i>
      <span class="summary-chip missing">漏计费 {{ summary.missingChargeCount }}</span>
      <span class="summary-chip duplicate">重复计费 {{ summary.duplicateChargeCount }}</span>
      <span class="summary-chip swap">疑似串换 {{ summary.suspectedSwapCount }}</span>
    </section>

    <section class="reconciliation-table-card" :aria-busy="loading">
      <div class="reconciliation-table-scroll">
        <table class="reconciliation-table">
          <thead><tr><th>序号</th><th>核对日期</th><th>耗材编码</th><th>医保编码</th><th>HIS医保编码</th><th>耗材名称</th><th>规格</th><th>科室</th><th>患者 / 住院号</th><th class="numeric">SPD消耗数量</th><th class="numeric">HIS计费数量</th><th class="numeric">差异数量</th><th class="numeric">差异金额</th><th>收费时间</th><th>差异原因标记</th><th>风险标记</th></tr></thead>
          <tbody>
            <TableStateRow v-if="loading" :colspan="16" state="loading" message="正在核对SPD消耗与HIS收费数据…" />
            <TableStateRow v-else-if="!rows.length" :colspan="16" message="当前筛选条件下暂无对账数据" />
            <tr v-for="(row, index) in rows" v-else :key="`${row.reconciliationDate}-${row.departmentName}-${row.patientNo}-${row.productCode}`" :class="`risk-row ${row.riskCode.toLowerCase()}`">
              <td>{{ (Number(query.page) - 1) * Number(query.size) + index + 1 }}</td><td>{{ row.reconciliationDate }}</td><td class="code-cell">{{ row.productCode }}</td><td class="code-cell">{{ row.medicalInsuranceCode }}</td><td class="code-cell">{{ row.hisMedicalInsuranceCode }}</td><td class="product-cell">{{ row.productName }}</td><td>{{ row.specModel }}</td><td>{{ row.departmentName }}</td><td><strong>{{ row.patientName }}</strong><small>{{ row.patientNo }}</small></td><td class="numeric">{{ quantity(row.spdConsumptionQuantity) }}</td><td class="numeric">{{ quantity(row.hisChargeQuantity) }}</td><td class="numeric difference-cell">{{ difference(row.differenceQuantity) }}</td><td class="numeric difference-cell">¥{{ amount(row.differenceAmount) }}</td><td>{{ row.chargeTime }}</td><td><span class="reason-tag" :class="row.riskCode.toLowerCase()">{{ row.differenceReason }}</span></td><td><span class="risk-tag" :class="row.riskCode.toLowerCase()">{{ row.riskLevel }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="reconciliation-table-footer">
        <p>统计口径：SPD实际消耗数据与HIS收费明细按患者、耗材、科室及自然日核对；患者信息仅脱敏展示。</p>
        <div class="report-pagination"><span>共{{ total.toLocaleString('zh-CN') }}条</span><select v-model.number="query.size" @change="query.page = 1; loadReport()"><option :value="20">20条/页</option><option :value="50">50条/页</option><option :value="100">100条/页</option></select><button type="button" :disabled="query.page === 1" @click="changePage(Number(query.page) - 1)">‹</button><button v-for="page in pageNumbers" :key="page" type="button" :class="{ active: page === query.page }" @click="changePage(page)">{{ page }}</button><span v-if="totalPages > 5">… {{ totalPages }}</span><button type="button" :disabled="query.page === totalPages" @click="changePage(Number(query.page) + 1)">›</button></div>
      </footer>
    </section>
  </main>
</template>
