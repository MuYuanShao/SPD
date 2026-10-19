<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  AlertOctagon,
  AlertTriangle,
  Calendar,
  CheckCircle2,
  Filter,
  Flame,
  Package,
  Snowflake,
  Thermometer,
  Warehouse,
  XCircle
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import {
  createColdChainException,
  fetchClosureList,
  fetchClosureOptions,
  type ClosureOptions
} from '../../api/operationalClosure'
import { useNotify } from '../../composables/useNotify'

const notify = useNotify()


const loading = ref(false)
const submitting = ref(false)
const exceptionOpen = ref(false)
const message = ref('')
const messageTone = ref<'info' | 'success' | 'warning' | 'error'>('info')
const rows = ref<Record<string, unknown>[]>([])
const options = ref<ClosureOptions>({
  departments: [],
  warehouses: [],
  products: [],
  balances: []
})
const currentPage = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)

const filterForm = reactive({
  warehouseName: '',
  severity: '',
  status: ''
})

const form = reactive({
  warehouseName: '',
  productCode: '',
  temperature: '',
  severity: 'high',
  note: ''
})

const selectedProduct = computed(() =>
  options.value.products.find((product) => product.productCode === form.productCode)
)

function severityText(value: unknown) {
  const status = String(value || '')
  const labels: Record<string, string> = {
    low: '低',
    medium: '中',
    high: '高'
  }
  return labels[status] || status || '-'
}

function severityTone(value: unknown): 'high' | 'medium' | 'low' | 'gray' {
  const v = String(value || '').toLowerCase()
  if (v === 'high') return 'high'
  if (v === 'medium') return 'medium'
  if (v === 'low') return 'low'
  return 'gray'
}

function severityIcon(value: unknown) {
  const tone = severityTone(value)
  if (tone === 'high') return Flame
  if (tone === 'medium') return AlertTriangle
  if (tone === 'low') return Snowflake
  return Thermometer
}

function statusText(value: unknown) {
  const status = String(value || '')
  const labels: Record<string, string> = {
    pending_dispose: '待处置',
    disposed: '已处置',
    closed: '已关闭'
  }
  return labels[status] || status || '-'
}

function statusTone(value: unknown): 'warning' | 'info' | 'gray' {
  const v = String(value || '').toLowerCase()
  if (v === 'pending_dispose') return 'warning'
  if (v === 'disposed') return 'info'
  return 'gray'
}

function pushMessage(text: string, tone: 'info' | 'success' | 'warning' | 'error' = 'info') {
  message.value = text
  messageTone.value = tone
}

async function loadOptions() {
  options.value = await fetchClosureOptions()
  if (!form.warehouseName) form.warehouseName = options.value.warehouses[0]?.warehouseName || ''
  if (!form.productCode) form.productCode = options.value.products[0]?.productCode || ''
  if (!filterForm.warehouseName) filterForm.warehouseName = options.value.warehouses[0]?.warehouseName || ''
}

async function loadData() {
  loading.value = true
  try {
    const page = await fetchClosureList('cold-chain', {
      page: String(currentPage.value),
      size: String(pageSize.value)
    })
    rows.value = page.rows
    totalItems.value = page.total
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '冷链异常记录加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await loadData()
}

async function applyFilter() {
  currentPage.value = 1
  await loadData()
}

async function resetFilter() {
  filterForm.warehouseName = ''
  filterForm.severity = ''
  filterForm.status = ''
  currentPage.value = 1
  await loadData()
}

async function resetForm() {
  form.warehouseName = options.value.warehouses[0]?.warehouseName || ''
  form.productCode = options.value.products[0]?.productCode || ''
  form.temperature = ''
  form.severity = 'high'
  form.note = ''
}

function openExceptionDialog() {
  resetForm()
  exceptionOpen.value = true
}

async function submitException() {
  if (!form.warehouseName || !form.productCode || !form.temperature) {
    pushMessage('请填写库房、商品和温度', 'warning')
    return
  }
  submitting.value = true
  try {
    const result = await createColdChainException({
      warehouseName: form.warehouseName,
      productCode: form.productCode,
      temperature: form.temperature,
      severity: form.severity
    })
    exceptionOpen.value = false
    currentPage.value = 1
    await loadData()
    pushMessage(`冷链异常已登记：${result.eventNo || ''}`, 'success')
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '冷链异常登记失败', 'error')
  } finally {
    submitting.value = false
  }
}

async function changePage(page: number) {
  const totalPages = Math.max(Math.ceil(totalItems.value / Math.max(pageSize.value, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === currentPage.value) return
  currentPage.value = nextPage
  await loadData()
}

async function changePageSize(size: number) {
  if (size === pageSize.value) return
  pageSize.value = size
  currentPage.value = 1
  await loadData()
}

const summary = computed(() => {
  const total = totalItems.value
  let pending = 0
  let disposed = 0
  let high = 0
  let medium = 0
  for (const row of rows.value) {
    if (String(row.status || '') === 'pending_dispose') pending++
    if (String(row.status || '') === 'disposed') disposed++
    if (String(row.severity || '') === 'high') high++
    if (String(row.severity || '') === 'medium') medium++
  }
  return [
    { label: '异常总数', value: total, tone: 'blue', icon: AlertOctagon },
    { label: '待处置', value: pending, tone: 'amber', icon: AlertTriangle },
    { label: '已处置', value: disposed, tone: 'green', icon: CheckCircle2 },
    { label: '高严重度', value: high, tone: 'red', icon: Flame }
  ]
})

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<template>
  <main class="cold-chain-page">
    <MdPageHeader
      crumb="供应链业务"
      title="冷链监控"
      description="登记冷链温度异常，并查看冷链异常事件记录。"
      :loading="loading"
      @refresh="refresh"
    />

    <!-- 提示消息：不放在顶部条，根据 tone 着色 -->
    <!-- 消息：操作反馈统一走顶部 ElMessage 弹层 -->

    <!-- ============ 汇总条 ============ -->
    <section class="cold-summary" aria-label="冷链异常汇总">
      <div
        v-for="item in summary"
        :key="item.label"
        class="cold-summary-item"
        :class="`tone-${item.tone}`"
      >
        <span class="cold-summary-icon"><component :is="item.icon" :size="14" /></span>
        <span class="cold-summary-label">{{ item.label }}</span>
        <strong class="cold-summary-value">{{ item.value }}</strong>
      </div>
    </section>

    <!-- ============ 面板：异常记录 + 顶部「登记异常」按钮 ============ -->
    <section class="cold-panel" :aria-busy="loading">
      <header class="cold-panel-header">
        <div class="cold-panel-title">
          <span class="cold-panel-icon"><Snowflake :size="16" /></span>
          <div>
            <h3>冷链异常记录</h3>
            <p>共 {{ totalItems }} 条 · 当前第 {{ currentPage }} / {{ Math.max(1, Math.ceil(totalItems / pageSize)) }} 页</p>
          </div>
        </div>
        <div class="cold-panel-actions">
          <button
            class="btn cold-register-btn"
            type="button"
            @click="openExceptionDialog"
          >
            <Thermometer :size="16" />
            登记异常
          </button>
        </div>
      </header>

      <!-- 筛选 -->
      <form class="cold-filter-bar" @submit.prevent="applyFilter">
        <label>
          <span class="cold-filter-tag is-warehouse"><Warehouse :size="12" /> 库房</span>
          <select v-model="filterForm.warehouseName" @change="applyFilter">
            <option value="">全部库房</option>
            <option v-for="warehouse in options.warehouses" :key="warehouse.warehouseName" :value="warehouse.warehouseName">
              {{ warehouse.warehouseName }}
            </option>
          </select>
        </label>
        <label>
          <span class="cold-filter-tag is-severity"><Flame :size="12" /> 严重程度</span>
          <select v-model="filterForm.severity" @change="applyFilter">
            <option value="">全部程度</option>
            <option value="high">高</option>
            <option value="medium">中</option>
            <option value="low">低</option>
          </select>
        </label>
        <label>
          <span class="cold-filter-tag is-status"><AlertTriangle :size="12" /> 状态</span>
          <select v-model="filterForm.status" @change="applyFilter">
            <option value="">全部状态</option>
            <option value="pending_dispose">待处置</option>
            <option value="disposed">已处置</option>
            <option value="closed">已关闭</option>
          </select>
        </label>
        <div class="cold-filter-actions">
          <button class="btn btn-sm cold-filter-reset" type="button" @click="resetFilter">
            <XCircle :size="14" />
            重置
          </button>
        </div>
      </form>

      <div class="table-scroll cold-chain-scroll">
        <table class="cold-chain-table">
          <thead>
            <tr>
              <th>异常编号</th>
              <th>库房</th>
              <th>商品信息</th>
              <th>温度</th>
              <th>严重程度</th>
              <th>状态</th>
              <th>登记时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading" class="cold-state-row">
              <td colspan="7">
                <div class="cold-state-cell is-loading">
                  <Thermometer :size="20" class="spin" />
                  <span>正在加载冷链异常记录...</span>
                </div>
              </td>
            </tr>
            <tr v-else-if="!rows.length" class="cold-state-row">
              <td colspan="7">
                <div class="cold-state-cell is-empty">
                  <EmptyState message="暂无冷链异常记录" />
                </div>
              </td>
            </tr>
            <tr v-for="row in rows" v-else :key="String(row.bizNo)" :class="`is-row-${severityTone(row.severity)}`">
              <td>
                <strong class="cold-biz-no">{{ row.bizNo || '-' }}</strong>
              </td>
              <td>
                <span class="cold-cell-warehouse">
                  <Warehouse :size="12" />
                  {{ row.warehouseName || '-' }}
                </span>
              </td>
              <td>
                <strong>{{ row.productName || '-' }}</strong>
                <small>{{ row.productCode || '-' }}</small>
              </td>
              <td>
                <span class="cold-temp" :class="`is-${severityTone(row.severity)}`">
                  <component :is="severityIcon(row.severity)" :size="14" />
                  <strong>{{ row.temperature || '-' }}</strong>
                  <em>℃</em>
                </span>
              </td>
              <td>
                <span class="severity-pill" :class="`is-${severityTone(row.severity)}`">
                  <component :is="severityIcon(row.severity)" :size="12" />
                  {{ severityText(row.severity) }}
                </span>
              </td>
              <td>
                <span class="status-pill" :class="`is-${statusTone(row.status)}`">
                  {{ statusText(row.status) }}
                </span>
              </td>
              <td class="cold-chain-time">
                <Calendar :size="12" />
                {{ row.createTime || '-' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="currentPage"
        :size="pageSize"
        :total="totalItems"
        :loading="loading"
        @change-page="changePage"
        @change-size="changePageSize"
      />
    </section>

    <el-dialog v-model="exceptionOpen" title="登记冷链异常" width="min(860px, 94vw)" append-to-body class="cold-chain-dialog">
      <div class="cold-chain-form">
        <label>
          <span class="cold-form-tag is-warehouse"><Warehouse :size="12" /> 库房</span>
          <select v-model="form.warehouseName">
            <option v-for="warehouse in options.warehouses" :key="warehouse.warehouseName" :value="warehouse.warehouseName">
              {{ warehouse.warehouseName }}
            </option>
          </select>
        </label>
        <label>
          <span class="cold-form-tag is-product"><Package :size="12" /> 商品</span>
          <select v-model="form.productCode">
            <option v-for="product in options.products" :key="product.productCode" :value="product.productCode">
              {{ product.productName }} / {{ product.productCode }}
            </option>
          </select>
        </label>
        <label>
          <span class="cold-form-tag is-temp"><Thermometer :size="12" /> 当前温度（℃）</span>
          <input v-model="form.temperature" type="number" step="0.1" placeholder="例如 9.5" />
        </label>
        <label>
          <span class="cold-form-tag is-severity"><Flame :size="12" /> 严重程度</span>
          <select v-model="form.severity">
            <option value="high">高</option>
            <option value="medium">中</option>
            <option value="low">低</option>
          </select>
        </label>
      </div>
      <p v-if="selectedProduct" class="cold-chain-product">
        <span class="cold-form-tag is-product"><Package :size="12" /> 当前商品</span>
        {{ selectedProduct.productName }}，规格 {{ selectedProduct.specModel || '-' }}，单位 {{ selectedProduct.unit || '-' }}
      </p>
      <div class="cold-chain-dialog-actions">
        <button class="btn btn-primary cold-dialog-save" type="button" :disabled="submitting" @click="submitException">
          <CheckCircle2 :size="16" />
          {{ submitting ? '保存中...' : '保存异常' }}
        </button>
        <button class="btn cold-dialog-reset" type="button" :disabled="submitting" @click="resetForm">
          重置
        </button>
        <button class="btn cold-dialog-cancel" type="button" :disabled="submitting" @click="exceptionOpen = false">
          取消
        </button>
      </div>
    </el-dialog>
  </main>
</template>

<style scoped>
/* =====================================================================
   冷链监控（重制版）
   - 登记异常按钮移到面板顶部，不再放在页面标题条
   - 顶部汇总条（4 项彩色 chips）
   - 筛选条（库房/严重度/状态）
   - 表格温度用大字彩色块 + 图标
   ===================================================================== */
.cold-chain-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  margin: 0 !important;
  padding: 16px 20px 24px;
  border: 0 !important;
  background: transparent;
  overflow: visible;
}

.cold-chain-page h3,
.cold-chain-page p {
  margin: 0;
}

/* ---------- 汇总 chips ---------- */
.cold-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cold-summary-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 140px;
  padding: 6px 12px;
  border: 1px solid;
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
  transition: transform 0.1s ease;
}

.cold-summary-item:hover {
  transform: translateY(-1px);
}

.cold-summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
}

.cold-summary-label {
  color: var(--spd-text-secondary);
}

.cold-summary-value {
  margin-left: auto;
  color: var(--spd-text-heading);
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.cold-summary-item.tone-blue {
  border-color: #bfdbfe;
  background: #eff6ff;
}
.cold-summary-item.tone-blue .cold-summary-icon {
  color: #1d4ed8;
  background: #dbeafe;
}
.cold-summary-item.tone-blue .cold-summary-value {
  color: #1d4ed8;
}

.cold-summary-item.tone-amber {
  border-color: #fde68a;
  background: #fffbeb;
}
.cold-summary-item.tone-amber .cold-summary-icon {
  color: #b45309;
  background: #fef3c7;
}
.cold-summary-item.tone-amber .cold-summary-value {
  color: #b45309;
}

.cold-summary-item.tone-green {
  border-color: #a7f3d0;
  background: #ecfdf5;
}
.cold-summary-item.tone-green .cold-summary-icon {
  color: #047857;
  background: #d1fae5;
}
.cold-summary-item.tone-green .cold-summary-value {
  color: #047857;
}

.cold-summary-item.tone-red {
  border-color: #fecaca;
  background: #fef2f2;
}
.cold-summary-item.tone-red .cold-summary-icon {
  color: #b91c1c;
  background: #fee2e2;
}
.cold-summary-item.tone-red .cold-summary-value {
  color: #b91c1c;
}

/* ---------- 面板 ---------- */
.cold-panel {
  box-sizing: border-box;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

.cold-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.cold-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cold-panel-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #0891b2;
  background: #cffafe;
  border-radius: 6px;
}

.cold-panel-title h3 {
  margin: 0;
  color: var(--spd-text-heading);
  font-size: 15px;
  font-weight: 600;
}

.cold-panel-title p {
  margin: 2px 0 0;
  color: var(--spd-text-secondary);
  font-size: 12px;
}

.cold-panel-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.cold-register-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #f97316 0%, #fb923c 100%);
  border: 1px solid #f97316;
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(249, 115, 22, 0.25);
  cursor: pointer;
  transition: all 0.15s ease;
}

.cold-register-btn:hover {
  background: linear-gradient(135deg, #ea580c 0%, #f97316 100%);
  box-shadow: 0 4px 10px rgba(249, 115, 22, 0.3);
}

/* ---------- 筛选条 ---------- */
.cold-filter-bar {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(140px, 0.7fr) minmax(140px, 0.8fr) auto;
  gap: 10px 12px;
  align-items: end;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.cold-filter-bar label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.cold-filter-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: max-content;
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 4px;
}

.cold-filter-tag.is-warehouse {
  color: #1d4ed8;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
}

.cold-filter-tag.is-severity {
  color: #b91c1c;
  background: #fee2e2;
  border: 1px solid #fecaca;
}

.cold-filter-tag.is-status {
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fde68a;
}

.cold-filter-bar select {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  font: inherit;
  font-size: 13px;
  color: var(--spd-text-primary);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.cold-filter-bar select:focus {
  outline: 0;
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}

.cold-filter-actions {
  display: flex;
  gap: 8px;
}

.cold-filter-reset {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 12px;
  font-size: 12px;
  color: #475569;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}

.cold-filter-reset:hover:not(:disabled) {
  color: #0f172a;
  background: #f1f5f9;
  border-color: #94a3b8;
}

/* ---------- 表格 ---------- */
.cold-chain-scroll {
  width: 100%;
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.cold-chain-table {
  width: 100%;
  min-width: 980px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.cold-chain-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 14px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  color: #1e293b !important;
  background: linear-gradient(180deg, #ecfeff 0%, #cffafe 100%) !important;
  border-bottom: 1px solid #67e8f9 !important;
  text-align: left;
  white-space: nowrap;
}

.cold-chain-table tbody td {
  padding: 10px 14px !important;
  border-bottom: 1px solid #f1f5f9 !important;
  color: #334155 !important;
  font-size: 13px !important;
  background: #fff;
  vertical-align: middle;
}

.cold-chain-table tbody tr:nth-child(even) td {
  background: #fafbfc;
}

.cold-chain-table tbody tr:hover td {
  background: #ecfeff !important;
}

.cold-chain-table tbody tr.is-row-high td {
  background: #fff5f5;
}

.cold-chain-table tbody tr.is-row-high:hover td {
  background: #fee2e2 !important;
}

.cold-chain-table tbody tr.is-row-medium td {
  background: #fffaf0;
}

.cold-chain-table tbody tr.is-row-medium:hover td {
  background: #fef3c7 !important;
}

.cold-biz-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #0e7490 !important;
  font-size: 12px !important;
  font-weight: 600 !important;
}

.cold-cell-warehouse {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.cold-cell-warehouse :deep(svg) {
  color: #0891b2;
}

td small {
  display: block;
  margin-top: 3px;
  color: var(--spd-text-tertiary);
  font-size: 12px;
}

.cold-chain-time {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.cold-chain-time :deep(svg) {
  color: #94a3b8;
}

/* ---------- 温度大色块 ---------- */
.cold-temp {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 92px;
  padding: 4px 10px;
  border: 1px solid;
  border-radius: 8px;
  font-variant-numeric: tabular-nums;
}

.cold-temp strong {
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
}

.cold-temp em {
  font-size: 11px;
  font-style: normal;
  font-weight: 500;
}

.cold-temp.is-high {
  color: #b91c1c;
  background: #fee2e2;
  border-color: #fecaca;
}

.cold-temp.is-medium {
  color: #b45309;
  background: #fef3c7;
  border-color: #fde68a;
}

.cold-temp.is-low {
  color: #047857;
  background: #d1fae5;
  border-color: #a7f3d0;
}

.cold-temp.is-gray {
  color: #475569;
  background: #f1f5f9;
  border-color: #e2e8f0;
}

/* ---------- 严重度 pill ---------- */
.severity-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-height: 22px;
  padding: 0 9px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.severity-pill.is-high {
  color: #b91c1c;
  background: #fee2e2;
  border-color: #fecaca;
}

.severity-pill.is-medium {
  color: #b45309;
  background: #fef3c7;
  border-color: #fde68a;
}

.severity-pill.is-low {
  color: #047857;
  background: #d1fae5;
  border-color: #a7f3d0;
}

.severity-pill.is-gray {
  color: #475569;
  background: #f1f5f9;
  border-color: #e2e8f0;
}

/* ---------- 状态 pill ---------- */
.status-pill {
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

.status-pill.is-warning {
  color: #b45309;
  background: #fef3c7;
  border-color: #fde68a;
}

.status-pill.is-info {
  color: #1d4ed8;
  background: #dbeafe;
  border-color: #bfdbfe;
}

.status-pill.is-gray {
  color: #64748b;
  background: #f1f5f9;
  border-color: #e2e8f0;
}

/* ---------- 状态行 ---------- */
.cold-state-row td {
  padding: 0 !important;
  border-bottom: 0 !important;
}

.cold-state-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 200px;
  color: #64748b;
  font-size: 13px;
}

.cold-state-cell.is-loading {
  color: #0e7490;
}

.cold-state-cell .spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---------- 登记弹窗 ---------- */
.cold-chain-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px 16px;
}

.cold-chain-form label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  margin: 0;
}

.cold-form-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: max-content;
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 4px;
}

.cold-form-tag.is-warehouse {
  color: #1d4ed8;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
}

.cold-form-tag.is-product {
  color: #047857;
  background: #d1fae5;
  border: 1px solid #a7f3d0;
}

.cold-form-tag.is-temp {
  color: #b91c1c;
  background: #fee2e2;
  border: 1px solid #fecaca;
}

.cold-form-tag.is-severity {
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fde68a;
}

.cold-chain-form select,
.cold-chain-form input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: 36px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  outline: 0;
  background: var(--spd-bg-container);
  color: var(--spd-text-primary);
  font: inherit;
  font-size: 13px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.cold-chain-form select:focus,
.cold-chain-form input:focus {
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}

.cold-chain-product {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #065f46;
  font-size: 12px;
  line-height: 1.6;
}

.cold-chain-dialog-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--spd-border-subtle);
}

.cold-dialog-save {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #f97316 0%, #fb923c 100%);
  border: 1px solid #f97316;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgba(249, 115, 22, 0.25);
}

.cold-dialog-save:hover:not(:disabled) {
  background: linear-gradient(135deg, #ea580c 0%, #f97316 100%);
}

.cold-dialog-reset,
.cold-dialog-cancel {
  height: 34px;
  padding: 0 14px;
  font-size: 13px;
  color: #475569;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}

.cold-dialog-reset:hover:not(:disabled),
.cold-dialog-cancel:hover:not(:disabled) {
  color: #0f172a;
  background: #f1f5f9;
  border-color: #94a3b8;
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .cold-chain-page {
    padding: 12px 12px 20px;
  }
  .cold-filter-bar {
    grid-template-columns: 1fr 1fr;
  }
  .cold-filter-actions {
    grid-column: 1 / -1;
  }
  .cold-summary-item {
    min-width: 0;
    flex: 1 1 45%;
  }
}
</style>
