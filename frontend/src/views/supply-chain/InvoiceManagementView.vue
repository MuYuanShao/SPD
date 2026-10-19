<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  CheckCircle2,
  FileText,
  FileSpreadsheet,
  Receipt,
  Stamp,
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import {
  createInvoice,
  fetchInvoiceOptions,
  fetchInvoices,
  verifyInvoice,
  type InvoiceRow,
} from '../../api/invoices'
import { formatStatusText } from '../../utils/chineseDisplay'
import { useNotify } from '../../composables/useNotify'

const notify = useNotify()


const loading = ref(false)
const saving = ref(false)
const message = ref('')
const messageTone = ref<'info' | 'success' | 'error'>('info')
const rows = ref<InvoiceRow[]>([])
const suppliers = ref<Array<{ supplierId: number; supplierName: string }>>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const query = reactive({ keyword: '', status: '' })
const form = reactive({
  supplierId: 0,
  invoiceCode: '',
  invoiceNumber: '',
  invoiceDate: new Date().toISOString().slice(0, 10),
  amount: 0,
  taxAmount: 0,
  remark: '',
})

const invoiceTotalCount = computed(() => total.value)
const invoiceVerifiedCount = computed(() => rows.value.filter((row) => row.status === 'verified').length)
const invoicePendingCount = computed(() => rows.value.filter((row) => row.status === 'pending_verification').length)
const invoiceVerifiedAmount = computed(() =>
  rows.value
    .filter((row) => row.status === 'verified')
    .reduce((sum, row) => sum + Number(row.amount || 0), 0)
)
const invoiceTotalAmount = computed(() =>
  rows.value.reduce((sum, row) => sum + Number(row.amount || 0), 0)
)
const invoiceSupplierCount = computed(() => {
  const set = new Set<string>()
  for (const row of rows.value) {
    if (row.supplierName) set.add(row.supplierName)
  }
  return set.size
})

function flash(text: string, tone: 'info' | 'success' | 'error' = 'info') {
  message.value = text
  messageTone.value = tone
}

async function load() {
  loading.value = true
  notify.success('')
  try {
    const [result, options] = await Promise.all([
      fetchInvoices({
        page: String(page.value),
        size: String(size.value),
        keyword: query.keyword,
        status: query.status,
      }),
      fetchInvoiceOptions(),
    ])
    rows.value = result.rows
    total.value = result.total
    suppliers.value = options
    if (!form.supplierId && options[0]) form.supplierId = options[0].supplierId
  } catch (error) {
    flash(error instanceof Error ? error.message : '发票数据加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (
    !form.supplierId ||
    !form.invoiceCode.trim() ||
    !form.invoiceNumber.trim() ||
    !form.invoiceDate
  ) {
    flash('请完整填写供应商、发票代码、号码和日期', 'error')
    return
  }
  saving.value = true
  try {
    const result = await createInvoice({
      ...form,
      invoiceCode: form.invoiceCode.trim(),
      invoiceNumber: form.invoiceNumber.trim(),
    })
    flash(`发票 ${result.invoiceNo} 已登记`, 'success')
    form.invoiceCode = ''
    form.invoiceNumber = ''
    form.amount = 0
    form.taxAmount = 0
    form.remark = ''
    page.value = 1
    await load()
  } catch (error) {
    flash(error instanceof Error ? error.message : '发票登记失败', 'error')
  } finally {
    saving.value = false
  }
}

async function verify(row: InvoiceRow) {
  try {
    await verifyInvoice(row.invoiceNo)
    flash(`发票 ${row.invoiceNo} 已核验`, 'success')
    await load()
  } catch (error) {
    flash(error instanceof Error ? error.message : '核验失败', 'error')
  }
}

function changePage(value: number) {
  page.value = value
  load()
}

function changeSize(value: number) {
  size.value = value
  page.value = 1
  load()
}

function resetForm() {
  form.invoiceCode = ''
  form.invoiceNumber = ''
  form.amount = 0
  form.taxAmount = 0
  form.remark = ''
}

function statusTone(status: string) {
  return status === 'verified' ? 'enabled' : 'pending'
}

onMounted(load)
</script>

<template>
  <section class="invoice-page">
    <MdPageHeader
      crumb="结算与财务"
      title="发票管理"
      description="供应商发票登记、核验及结算关联均实时写入 MySQL。"
      :loading="loading"
      @refresh="load"
    />

    <!-- 消息：操作反馈统一走顶部 ElMessage 弹层 -->

    <!-- 顶部 4 项 KPI 摘要 -->
    <section class="invoice-kpi-card" aria-label="发票汇总">
      <div class="invoice-kpi-item is-total">
        <i class="invoice-kpi-icon is-total"><Receipt :size="18" /></i>
        <div>
          <span class="invoice-kpi-label">发票总数</span>
          <strong class="invoice-kpi-value">{{ invoiceTotalCount }} <small>/ {{ total }}</small></strong>
          <span class="invoice-kpi-hint">当前查询条件下发票总数</span>
        </div>
      </div>
      <div class="invoice-kpi-item is-pending">
        <i class="invoice-kpi-icon is-pending"><FileText :size="18" /></i>
        <div>
          <span class="invoice-kpi-label">待核验</span>
          <strong class="invoice-kpi-value">{{ invoicePendingCount }}</strong>
          <span class="invoice-kpi-hint">本屏待核验发票数</span>
        </div>
      </div>
      <div class="invoice-kpi-item is-verified">
        <i class="invoice-kpi-icon is-verified"><CheckCircle2 :size="18" /></i>
        <div>
          <span class="invoice-kpi-label">已核验</span>
          <strong class="invoice-kpi-value">{{ invoiceVerifiedCount }}</strong>
          <span class="invoice-kpi-hint">本屏已核验发票数</span>
        </div>
      </div>
      <div class="invoice-kpi-item is-amount">
        <i class="invoice-kpi-icon is-amount"><Stamp :size="18" /></i>
        <div>
          <span class="invoice-kpi-label">含税金额</span>
          <strong class="invoice-kpi-value">¥ {{ invoiceTotalAmount.toFixed(2) }}</strong>
          <span class="invoice-kpi-hint">本屏含税金额累加 / 已核验 ¥ {{ invoiceVerifiedAmount.toFixed(2) }}</span>
        </div>
      </div>
    </section>

    <!-- 登记发票 -->
    <section class="invoice-panel invoice-form-panel">
      <div class="section-title">
        <i class="section-title-icon is-form"><FileSpreadsheet :size="20" /></i>
        <div>
          <h3>登记供应商发票</h3>
          <p>发票代码、号码、含税金额与税额需完整填写，登记后可在列表中核验。</p>
        </div>
      </div>
      <form class="invoice-form" @submit.prevent="submit">
        <label><span>供应商</span>
          <select v-model.number="form.supplierId">
            <option v-for="item in suppliers" :key="item.supplierId" :value="item.supplierId">{{ item.supplierName }}</option>
          </select>
        </label>
        <label><span>发票代码</span><input v-model="form.invoiceCode" placeholder="如 011002100311" /></label>
        <label><span>发票号码</span><input v-model="form.invoiceNumber" placeholder="8 位 / 20 位数字" /></label>
        <label><span>开票日期</span><input v-model="form.invoiceDate" type="date" /></label>
        <label><span>含税金额（元）</span><input v-model.number="form.amount" type="number" min="0" step="0.01" /></label>
        <label><span>税额（元）</span><input v-model.number="form.taxAmount" type="number" min="0" step="0.01" /></label>
        <label class="wide"><span>备注</span><input v-model="form.remark" placeholder="选填，可记录业务背景" /></label>
        <div class="invoice-form-actions">
          <button class="btn" type="button" :disabled="saving" @click="resetForm">重置</button>
          <button class="btn btn-primary" type="submit" :disabled="saving">{{ saving ? '保存中...' : '登记发票' }}</button>
        </div>
      </form>
    </section>

    <!-- 发票列表 -->
    <section class="invoice-panel invoice-list-panel">
      <div class="section-title">
        <i class="section-title-icon is-list"><Receipt :size="20" /></i>
        <div>
          <h3>发票明细</h3>
          <p>按登记时间倒序展示，结算关联后会自动回填 SPD 结算单编号。</p>
        </div>
        <div class="section-title-tools">
          <input v-model="query.keyword" placeholder="发票编号 / 代码 / 号码 / 供应商" @keyup.enter="page = 1; load()" />
          <select v-model="query.status">
            <option value="">全部状态</option>
            <option value="pending_verification">待核验</option>
            <option value="verified">已核验</option>
          </select>
          <button class="btn btn-primary" type="button" @click="page = 1; load()">查询</button>
        </div>
      </div>
      <div class="table-scroll invoice-detail-scroll">
        <table class="master-table invoice-detail-table">
          <thead>
            <tr>
              <th class="col-invoice-no">SPD 发票编号</th>
              <th class="col-supplier">供应商</th>
              <th class="col-code">发票代码</th>
              <th class="col-number">发票号码</th>
              <th class="col-date">开票日期</th>
              <th class="numeric-cell">含税金额</th>
              <th class="numeric-cell">税额</th>
              <th class="col-settlement">结算单</th>
              <th class="col-status">状态</th>
              <th class="col-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="10" class="empty-cell">正在加载发票数据...</td>
            </tr>
            <tr v-else-if="!rows.length" class="invoice-empty-row">
              <td colspan="10" class="empty-cell">暂无发票数据</td>
            </tr>
            <tr v-for="row in rows" v-else :key="row.invoiceId">
              <td class="col-invoice-no">
                <div class="invoice-cell-stack">
                  <strong>{{ row.invoiceNo }}</strong>
                  <span class="invoice-cell-meta">登记：{{ row.createTime || '-' }}</span>
                </div>
              </td>
              <td class="col-supplier" :title="row.supplierName || '-'">{{ row.supplierName || '-' }}</td>
              <td class="col-code invoice-mono" :title="row.invoiceCode || '-'">{{ row.invoiceCode || '-' }}</td>
              <td class="col-number invoice-mono" :title="row.invoiceNumber || '-'">{{ row.invoiceNumber || '-' }}</td>
              <td class="col-date">{{ row.invoiceDate || '-' }}</td>
              <td class="numeric-cell amount-cell">{{ row.amount ?? '-' }}</td>
              <td class="numeric-cell">{{ row.taxAmount ?? '-' }}</td>
              <td class="col-settlement" :title="row.settlementNo || '-'">{{ row.settlementNo || '-' }}</td>
              <td class="col-status">
                <span class="status-badge" :class="statusTone(row.status)">{{ formatStatusText(row.status) }}</span>
              </td>
              <td class="col-action">
                <button v-if="row.status === 'pending_verification'" class="btn btn-sm btn-primary" type="button" @click="verify(row)">核验</button>
                <span v-else class="invoice-cell-meta">已核验</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls :page="page" :size="size" :total="total" :loading="loading" @change-page="changePage" @change-size="changeSize" />
    </section>
  </section>
</template>

<style scoped>
/* =====================================================================
   发票管理 InvoiceManagementView 样式（沿用结算对账 finance 主题）
   ===================================================================== */

.invoice-page {
  display: grid;
  gap: 14px;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: none;
  padding: 16px 20px 24px;
}

.invoice-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

/* ---------- 顶部 4 项 KPI 摘要（4 张不同主题色卡） ---------- */
.invoice-kpi-card {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.invoice-kpi-item {
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

.invoice-kpi-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
}

/* ① 蓝色：发票总数 */
.invoice-kpi-item.is-total {
  background: linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%);
  border-color: #93c5fd;
}

/* ② 金色：待核验 */
.invoice-kpi-item.is-pending {
  background: linear-gradient(180deg, #fef3c7 0%, #fde68a 100%);
  border-color: #fcd34d;
}

/* ③ 绿色：已核验 */
.invoice-kpi-item.is-verified {
  background: linear-gradient(180deg, #d1fae5 0%, #a7f3d0 100%);
  border-color: #6ee7b7;
}

/* ④ 紫色：含税金额 */
.invoice-kpi-item.is-amount {
  background: linear-gradient(180deg, #ede9fe 0%, #ddd6fe 100%);
  border-color: #c4b5fd;
}

.invoice-kpi-icon {
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

/* 图标颜色对应主题 */
.invoice-kpi-item.is-total .invoice-kpi-icon {
  color: #1d4ed8;
}

.invoice-kpi-item.is-pending .invoice-kpi-icon {
  color: #b45309;
}

.invoice-kpi-item.is-verified .invoice-kpi-icon {
  color: #047857;
}

.invoice-kpi-item.is-amount .invoice-kpi-icon {
  color: #6d28d9;
}

.invoice-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.invoice-kpi-label {
  font-size: 12px;
  font-weight: 500;
  opacity: 0.85;
}

.invoice-kpi-value {
  color: var(--spd-text-heading, #14213a);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.invoice-kpi-value small {
  color: var(--spd-text-tertiary);
  font-size: 12px;
  font-weight: 400;
  margin-left: 2px;
  opacity: 0.75;
}

.invoice-kpi-hint {
  font-size: 11px;
  font-weight: 400;
  line-height: 1.3;
  opacity: 0.75;
}

/* 文字颜色对应主题（深色） */
.invoice-kpi-item.is-total .invoice-kpi-label,
.invoice-kpi-item.is-total .invoice-kpi-value,
.invoice-kpi-item.is-total .invoice-kpi-hint {
  color: #1e3a8a;
}

.invoice-kpi-item.is-pending .invoice-kpi-label,
.invoice-kpi-item.is-pending .invoice-kpi-value,
.invoice-kpi-item.is-pending .invoice-kpi-hint {
  color: #78350f;
}

.invoice-kpi-item.is-verified .invoice-kpi-label,
.invoice-kpi-item.is-verified .invoice-kpi-value,
.invoice-kpi-item.is-verified .invoice-kpi-hint {
  color: #065f46;
}

.invoice-kpi-item.is-amount .invoice-kpi-label,
.invoice-kpi-item.is-amount .invoice-kpi-value,
.invoice-kpi-item.is-amount .invoice-kpi-hint {
  color: #4c1d95;
}

/* ---------- 通用面板：finance 绿色主题 ---------- */
.invoice-panel {
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

.section-title-icon.is-form {
  background: #d1fae5;
  color: #047857;
}

.section-title-icon.is-list {
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

.section-title-tools input {
  min-width: 280px;
  min-height: 32px;
  height: 32px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 11px;
  background: #fff;
  font-size: 13px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.section-title-tools input:focus {
  outline: none;
  border-color: #cbd5e1;
  box-shadow: none;
}

.section-title-tools select {
  min-height: 32px;
  height: 32px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 11px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

/* ---------- 登记表单 ---------- */
.invoice-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 14px;
  align-items: end;
}

.invoice-form label {
  display: grid;
  gap: 6px;
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.invoice-form input,
.invoice-form select {
  min-height: 32px;
  height: 32px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 11px;
  background: #fff;
  font-size: 13px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  color: var(--spd-text-primary);
}

.invoice-form input:focus,
.invoice-form select:focus {
  outline: none;
  border-color: #cbd5e1;
  box-shadow: none;
}

.invoice-form .wide {
  grid-column: span 2;
}

.invoice-form-actions {
  grid-column: span 4;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}

/* ---------- 表格：financial 绿色主题 ---------- */
.invoice-detail-scroll {
  max-height: min(72vh, 820px);
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.invoice-detail-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.invoice-detail-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 999px;
}

.invoice-detail-scroll::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.invoice-detail-table {
  width: 100%;
  min-width: 1200px;
  border-collapse: separate;
  border-spacing: 0;
}

.invoice-detail-table th,
.invoice-detail-table td {
  padding: 9px 12px;
  font-size: 13px;
  line-height: 1.4;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
  white-space: nowrap;
}

.invoice-detail-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 12px;
  background: linear-gradient(180deg, #ecfdf5 0%, #d1fae5 100%);
  color: #065f46;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.2px;
  border-bottom: 1px solid #6ee7b7;
}

.invoice-detail-table tbody td {
  background: #fff;
  color: #334155;
}

.invoice-detail-table tbody tr:nth-child(even) td {
  background: #fafdfb;
}

.invoice-detail-table tbody tr:hover td {
  background: #ecfdf5;
}

.invoice-empty-row td {
  min-height: min(500px, 60vh);
  padding: 0 !important;
  text-align: center !important;
  vertical-align: middle !important;
}

.invoice-empty-row .empty-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: min(500px, 60vh);
  color: var(--spd-text-tertiary);
  font-size: 13px;
}

.invoice-cell-stack {
  display: grid;
  gap: 2px;
  line-height: 1.4;
}

.invoice-cell-stack > strong {
  font-weight: 600;
  color: var(--spd-text-primary);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.invoice-cell-meta {
  color: #64748b;
  font-size: 11.5px;
  font-weight: 400;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.invoice-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #475569;
}

.invoice-detail-table .numeric-cell {
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
  color: #475569;
}

.invoice-detail-table .amount-cell {
  color: #047857;
  font-weight: 700;
  font-size: 13.5px;
}

.invoice-detail-table .col-invoice-no,
.invoice-detail-table .col-supplier,
.invoice-detail-table .col-code,
.invoice-detail-table .col-number,
.invoice-detail-table .col-date,
.invoice-detail-table .col-settlement,
.invoice-detail-table .col-status,
.invoice-detail-table .col-action {
  vertical-align: top;
}

.invoice-detail-table .col-supplier,
.invoice-detail-table .col-settlement {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 状态 badge */
.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 9px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.status-badge.pending {
  color: #b45309;
  background: #fef3c7;
  border-color: #fde68a;
}

.status-badge.enabled {
  color: #047857;
  background: #d1fae5;
  border-color: #6ee7b7;
}

/* 按钮 */
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

.btn-sm {
  min-height: 26px;
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
}

/* 状态消息 */
.invoice-page :deep(.status-message) {
  border-radius: 8px;
}

/* 响应式 */
@media (max-width: 1100px) {
  .invoice-form {
    grid-template-columns: repeat(2, minmax(150px, 1fr));
  }
  .invoice-form .wide,
  .invoice-form-actions {
    grid-column: span 2;
  }
  .invoice-kpi-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .section-title-tools input {
    min-width: 200px;
  }
}

@media (max-width: 720px) {
  .invoice-page {
    padding: 12px;
  }
  .invoice-kpi-card {
    grid-template-columns: 1fr;
  }
  .section-title {
    flex-direction: column;
    align-items: flex-start;
  }
  .section-title-tools {
    width: 100%;
  }
  .section-title-tools input {
    min-width: 0;
    flex: 1;
  }
}
</style>
