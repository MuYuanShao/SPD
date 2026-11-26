<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  CheckCircle2,
  FileText,
  FileSpreadsheet,
  Receipt,
  Stamp,
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import type { SpdStatusChipTone } from '../../components/common/SpdStatusChip.vue'
import {
  createInvoice,
  fetchInvoiceOptions,
  fetchInvoices,
  verifyInvoice,
  type InvoiceRow,
} from '../../api/invoices'
import { formatStatusText } from '../../utils/chineseDisplay'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'

import {
  Columns3,
  ChevronDown
} from '@lucide/vue'
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

function statusTone(status: string): SpdStatusChipTone {
  return status === 'verified' ? 'success' : 'warning'
}

onMounted(load)

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const INVOICEDETAIL_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'invoiceNo', label: 'SPD 发票编号', width: 190, visible: true, weight: 5, minWidth: 140, maxWidth: 280 },
  { key: 'supplierName', label: '供应商', width: 170, visible: true, weight: 5, minWidth: 130, maxWidth: 260 },
  { key: 'invoiceCode', label: '发票代码', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 210 },
  { key: 'invoiceNumber', label: '发票号码', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 210 },
  { key: 'invoiceDate', label: '开票日期', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'taxIncludedAmount', label: '含税金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'taxAmount', label: '税额', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'settlementNo', label: '结算单', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 240 },
  { key: 'status', label: '状态', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'actions', label: '操作', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200, fill: true }
]

const invoiceDetailCols = useListColumns({
  storageKey: 'spd-invoice-detail-cols-v1',
  defaults: INVOICEDETAIL_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const invoiceDetailTableRef = invoiceDetailCols.tableRef
const isInvoiceDetailColVisible = (key: string) => invoiceDetailCols.isColumnVisible(key)
const invoiceDetailColBinding = (key: string) => invoiceDetailCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const invoiceDetailActionFix = useActionColumnFix()
const invoiceDetailActionFixed = computed(() => invoiceDetailActionFix.actionFixedBinding.value)

watch(
  [invoiceDetailTableRef],
  () => {
    invoiceDetailActionFix.observe(invoiceDetailTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const invoiceDetailColSettingsAnchorRef = ref<HTMLElement | null>(null)
const invoiceDetailColSettingsPopRef = ref<HTMLElement | null>(null)
const invoiceDetailColSettingsPop = useColSettingsPopover({
  open: invoiceDetailCols.settingsOpen,
  anchorRef: invoiceDetailColSettingsAnchorRef,
  popRef: invoiceDetailColSettingsPopRef
})

</script>

<template>
  <section class="invoice-page spd-unified-list">
    <MdPageHeader
      crumb="结算与财务"
      title="发票管理"
      :loading="loading"
      @refresh="load"
    />

    <!-- 消息：操作反馈统一走顶部 ElMessage 弹层 -->

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
          <SpdButton label="重置" tone="neutral" :disabled="saving" @click="resetForm"></SpdButton>
          <SpdButton tone="primary" type="submit" :disabled="saving">
          {{ saving ? '保存中...' : '登记发票' }}
        </SpdButton>
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
          <SpdButton label="查询" tone="primary" @click="page = 1; load()"></SpdButton>
        </div>
      </div>
            <MdDataTableCard>
        <template #meta-extra>
          <span class="md-chip primary">共 {{ total }} 张发票</span>
          <span class="md-chip">待核验 {{ invoicePendingCount }}</span>
          <span class="md-chip">已核验 {{ invoiceVerifiedCount }}</span>
          <span class="md-chip">含税 ¥ {{ invoiceTotalAmount.toFixed(2) }}</span>
        </template>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="invoiceDetailColSettingsAnchorRef"
           
           
            @click="invoiceDetailCols.settingsOpen.value = !invoiceDetailCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <div
            v-if="invoiceDetailCols.settingsOpen.value"
            ref="invoiceDetailColSettingsPopRef"
            class="ulc-col-settings-pop"
            :class="{ 'is-align-right': invoiceDetailColSettingsPop.align.value === 'right', 'is-align-up': invoiceDetailColSettingsPop.vertical.value === 'up' }"
            :style="invoiceDetailColSettingsPop.popStyle.value"
          >
            <header>
              <strong>列展示设置</strong>
              <SpdButton label="恢复默认" variant="text" tone="neutral" @click="invoiceDetailCols.resetColumns()"></SpdButton>
            </header>
            <div class="ulc-col-settings-list">
              <label v-for="col in invoiceDetailCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                <input
                  type="checkbox"
                  :checked="col.visible"
                  :disabled="col.visible && invoiceDetailCols.visibleColumnCount.value <= 1"
                  @change="invoiceDetailCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ col.label }}</span>
                <small>{{ col.width }}px</small>
              </label>
            </div>
            <footer>
              <SpdButton label="确定" tone="primary" @click="invoiceDetailCols.settingsOpen.value = false"></SpdButton>
            </footer>
          </div>
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="invoiceDetailTableRef"
            :data="rows"
            row-key="invoiceNo"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无发票记录'"
            @header-dragend="(width: number, oldWidth: number, column: any) => invoiceDetailCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="invoiceNo"
              v-if="isInvoiceDetailColVisible('invoiceNo')"
              label="SPD 发票编号"
              v-bind="invoiceDetailColBinding('invoiceNo')"
            >
              <template #header>
                <span class="ulc-th-text">SPD 发票编号</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong ulc-wrap-text">{{ row.invoiceNo }}</span>
                  <span class="ulc-sub ulc-wrap-text is-sub">登记：{{ row.createTime || '-' }}</span>
                  <span class="ulc-sub ulc-wrap-text is-sub">修改：{{ row.updateTime || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="supplierName"
              v-if="isInvoiceDetailColVisible('supplierName')"
              label="供应商"
              v-bind="invoiceDetailColBinding('supplierName')"
            >
              <template #header>
                <span class="ulc-th-text">供应商</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.supplierName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="invoiceCode"
              v-if="isInvoiceDetailColVisible('invoiceCode')"
              label="发票代码"
              v-bind="invoiceDetailColBinding('invoiceCode')"
            >
              <template #header>
                <span class="ulc-th-text">发票代码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.invoiceCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="invoiceNumber"
              v-if="isInvoiceDetailColVisible('invoiceNumber')"
              label="发票号码"
              v-bind="invoiceDetailColBinding('invoiceNumber')"
            >
              <template #header>
                <span class="ulc-th-text">发票号码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.invoiceNumber || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="invoiceDate"
              v-if="isInvoiceDetailColVisible('invoiceDate')"
              label="开票日期"
              v-bind="invoiceDetailColBinding('invoiceDate')"
            >
              <template #header>
                <span class="ulc-th-text">开票日期</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.invoiceDate || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="taxIncludedAmount"
              v-if="isInvoiceDetailColVisible('taxIncludedAmount')"
              label="含税金额"
              v-bind="invoiceDetailColBinding('taxIncludedAmount')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">含税金额</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number ulc-wrap-text">{{ row.amount ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="taxAmount"
              v-if="isInvoiceDetailColVisible('taxAmount')"
              label="税额"
              v-bind="invoiceDetailColBinding('taxAmount')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">税额</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number ulc-wrap-text">{{ row.taxAmount ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="settlementNo"
              v-if="isInvoiceDetailColVisible('settlementNo')"
              label="结算单"
              v-bind="invoiceDetailColBinding('settlementNo')"
            >
              <template #header>
                <span class="ulc-th-text">结算单</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.settlementNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isInvoiceDetailColVisible('status')"
              label="状态"
              v-bind="invoiceDetailColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip :tone="statusTone(row.status)" :label="formatStatusText(row.status)" />
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isInvoiceDetailColVisible('actions')"
              label="操作"
              v-bind="invoiceDetailColBinding('actions')"
              :fixed="invoiceDetailActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button v-if="row.status === 'pending_verification'" @click="verify(row)" class="ulc-action btn-sm btn-primary" type="button">核验</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无发票记录" />
            </template>
            </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="page"
            :total-pages="Math.max(1, Math.ceil(total / size))"
            :total="total"
            :size="size"
            :loading="loading"
            @update:page="changePage"
            @update:size="changeSize"
          />
        </template>
      </MdDataTableCard>
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
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.invoice-kpi-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--spd-p-shadow-2);
}

/* ① 蓝色：发票总数 */
.invoice-kpi-item.is-total {
  background: linear-gradient(180deg, var(--spd-p-border) 0%, var(--spd-p-info-border) 100%);
  border-color: var(--spd-p-info-border);
}

/* ② 金色：待核验 */
.invoice-kpi-item.is-pending {
  background: linear-gradient(180deg, var(--spd-p-warning-bg) 0%, var(--spd-p-warning-border) 100%);
  border-color: var(--spd-p-warning-border);
}

/* ③ 绿色：已核验 */
.invoice-kpi-item.is-verified {
  background: linear-gradient(180deg, var(--spd-p-success-bg) 0%, var(--spd-p-success-border) 100%);
  border-color: var(--spd-p-success);
}

/* ④ 紫色：含税金额 */
.invoice-kpi-item.is-amount {
  background: linear-gradient(180deg, var(--spd-p-info-bg) 0%, var(--spd-p-info-bg) 100%);
  border-color: var(--spd-p-info-border);
}

.invoice-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--spd-p-bg) 65%, transparent);
  flex-shrink: 0;
  border: 1px solid color-mix(in srgb, var(--spd-p-bg) 90%, transparent);
}

/* 图标颜色对应主题 */
.invoice-kpi-item.is-total .invoice-kpi-icon {
  color: var(--spd-p-info-strong);
}

.invoice-kpi-item.is-pending .invoice-kpi-icon {
  color: var(--spd-p-warning-text);
}

.invoice-kpi-item.is-verified .invoice-kpi-icon {
  color: var(--spd-p-success-strong);
}

.invoice-kpi-item.is-amount .invoice-kpi-icon {
  color: var(--spd-p-primary);
}

.invoice-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.invoice-kpi-label {
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  opacity: 0.85;
}

.invoice-kpi-value {
  color: var(--spd-text-heading, var(--spd-p-text));
  font-size: var(--spd-font-size-2xl);
  font-weight: 800;
  line-height: var(--spd-lh-tight);
  font-variant-numeric: tabular-nums;
}

.invoice-kpi-value small {
  color: var(--spd-text-tertiary);
  font-size: var(--spd-font-size-xs);
  font-weight: 400;
  margin-left: 2px;
  opacity: 0.75;
}

.invoice-kpi-hint {
  font-size: var(--spd-font-size-xs);
  font-weight: 400;
  line-height: var(--spd-lh-base);
  opacity: 0.75;
}

/* 文字颜色对应主题（深色） */
.invoice-kpi-item.is-total .invoice-kpi-label,
.invoice-kpi-item.is-total .invoice-kpi-value,
.invoice-kpi-item.is-total .invoice-kpi-hint {
  color: var(--spd-p-info-strong);
}

.invoice-kpi-item.is-pending .invoice-kpi-label,
.invoice-kpi-item.is-pending .invoice-kpi-value,
.invoice-kpi-item.is-pending .invoice-kpi-hint {
  color: var(--spd-p-warning-strong);
}

.invoice-kpi-item.is-verified .invoice-kpi-label,
.invoice-kpi-item.is-verified .invoice-kpi-value,
.invoice-kpi-item.is-verified .invoice-kpi-hint {
  color: var(--spd-p-primary-strong);
}

.invoice-kpi-item.is-amount .invoice-kpi-label,
.invoice-kpi-item.is-amount .invoice-kpi-value,
.invoice-kpi-item.is-amount .invoice-kpi-hint {
  color: var(--spd-p-primary-strong);
}

/* ---------- 通用面板：finance 绿色主题 ---------- */
.invoice-panel {
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

.section-title-icon.is-form {
  background: var(--spd-p-success-bg);
  color: var(--spd-p-success-strong);
}

.section-title-icon.is-list {
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

.section-title-tools input {
  min-width: 280px;
  min-height: 32px;
  height: 32px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  padding: 0 11px;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-base);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.section-title-tools input:focus {
  outline: none;
  border-color: var(--spd-p-border-strong);
  box-shadow: none;
}

.section-title-tools select {
  min-height: 32px;
  height: 32px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  padding: 0 11px;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
}

.invoice-form input,
.invoice-form select {
  min-height: 32px;
  height: 32px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  padding: 0 11px;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-base);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  color: var(--spd-text-primary);
}

.invoice-form input:focus,
.invoice-form select:focus {
  outline: none;
  border-color: var(--spd-p-border-strong);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  scrollbar-width: thin;
  scrollbar-color: var(--spd-p-border-strong) transparent;
}

.invoice-detail-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.invoice-detail-scroll::-webkit-scrollbar-thumb {
  background: var(--spd-p-border-strong);
  border-radius: 999px;
}

.invoice-detail-scroll::-webkit-scrollbar-thumb:hover {
  background: var(--spd-p-text-muted);
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
  font-size: var(--spd-font-size-base);
  line-height: var(--spd-lh-tight);
  border-bottom: 1px solid var(--spd-p-text-strong);
  text-align: left;
  white-space: nowrap;
}

.invoice-detail-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 12px;
  background: linear-gradient(180deg, var(--spd-p-primary-bg) 0%, var(--spd-p-success-bg) 100%);
  color: var(--spd-p-primary-strong);
  font-size: var(--spd-font-size-table-head);
  font-weight: 700;
  letter-spacing: 0.2px;
  border-bottom: 1px solid var(--spd-p-success);
}

.invoice-detail-table tbody td {
  background: var(--spd-p-bg);
  color: var(--spd-p-text-secondary);
}

.invoice-detail-table tbody tr:nth-child(even) td {
  background: var(--spd-p-bg);
}

.invoice-detail-table tbody tr:hover td {
  background: var(--spd-p-primary-bg);
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
  font-size: var(--spd-font-size-base);
}

.invoice-cell-stack {
  display: grid;
  gap: 2px;
  line-height: var(--spd-lh-tight);
}

.invoice-cell-stack > strong {
  font-weight: 700;
  color: var(--spd-text-primary);
  font-size: var(--spd-font-size-base);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.invoice-cell-meta {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-xs);
  font-weight: 400;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.invoice-mono {
  font-family: var(--spd-font-family-mono);
  color: var(--spd-p-text-secondary);
}

.invoice-detail-table .numeric-cell {
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
  color: var(--spd-p-text-secondary);
}

.invoice-detail-table .amount-cell {
  color: var(--spd-p-success-strong);
  font-weight: 800;
  font-size: var(--spd-font-size-base);
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

/* 【已移除】本文件原有的 .status-badge 局部样式（22px 高 / 999px 圆角）。
   列表状态条统一改用 <SpdStatusChip>（styles/components/spd-status-chip.css），
   全站共享同一套高度 / 内边距 / 圆角 / 浅底深字配色。 */

/* 按钮 */
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
  font-weight: 600;
  line-height: 1;
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

.btn-sm {
  min-height: 26px;
  height: 26px;
  padding: 0 10px;
  font-size: var(--spd-font-size-sm);
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
