<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Thermometer } from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import TableStateRow from '../../components/common/TableStateRow.vue'
import {
  createColdChainException,
  fetchClosureList,
  fetchClosureOptions,
  type ClosureOptions
} from '../../api/operationalClosure'

const loading = ref(false)
const submitting = ref(false)
const exceptionOpen = ref(false)
const message = ref('')
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

const form = reactive({
  warehouseName: '',
  productCode: '',
  temperature: '',
  severity: 'high'
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

function statusText(value: unknown) {
  const status = String(value || '')
  const labels: Record<string, string> = {
    pending_dispose: '待处置',
    disposed: '已处置',
    closed: '已关闭'
  }
  return labels[status] || status || '-'
}

async function loadOptions() {
  options.value = await fetchClosureOptions()
  form.warehouseName = form.warehouseName || options.value.warehouses[0]?.warehouseName || ''
  form.productCode = form.productCode || options.value.products[0]?.productCode || ''
}

async function loadData() {
  loading.value = true
  message.value = ''
  try {
    const page = await fetchClosureList('cold-chain', {
      page: String(currentPage.value),
      size: String(pageSize.value)
    })
    rows.value = page.rows
    totalItems.value = page.total
  } catch (error) {
    message.value = error instanceof Error ? error.message : '冷链异常记录加载失败'
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await loadData()
}

async function resetForm() {
  form.warehouseName = options.value.warehouses[0]?.warehouseName || ''
  form.productCode = options.value.products[0]?.productCode || ''
  form.temperature = ''
  form.severity = 'high'
}

function openExceptionDialog() {
  resetForm()
  message.value = ''
  exceptionOpen.value = true
}

async function submitException() {
  if (!form.warehouseName || !form.productCode || !form.temperature) {
    message.value = '请填写库房、商品和温度'
    return
  }
  submitting.value = true
  message.value = ''
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
    message.value = `冷链异常已登记：${result.eventNo || ''}`
  } catch (error) {
    message.value = error instanceof Error ? error.message : '冷链异常登记失败'
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
    >
      <template #actions>
        <button class="btn btn-primary md-btn" type="button" @click="openExceptionDialog">
          <Thermometer :size="16" />
          登记异常
        </button>
      </template>
    </MdPageHeader>

    <StatusMessage :message="message" tone="success" role="status" />

    <section class="cold-chain-panel" :aria-busy="loading">
      <header class="cold-chain-panel-header">
        <h3>冷链异常记录</h3>
        <span class="cold-chain-count">共 {{ totalItems }} 条</span>
      </header>
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
            <TableStateRow v-if="loading" :colspan="7" state="loading" message="正在加载冷链异常记录..." />
            <TableStateRow v-else-if="!rows.length" :colspan="7" message="暂无冷链异常记录" />
            <tr v-for="row in rows" v-else :key="String(row.bizNo)">
              <td>
                <strong>{{ row.bizNo || '-' }}</strong>
              </td>
              <td>{{ row.warehouseName || '-' }}</td>
              <td>
                <strong>{{ row.productName || '-' }}</strong>
                <small>{{ row.productCode || '-' }}</small>
              </td>
              <td>
                <span class="cold-temp" :class="`is-${String(row.severity || '').toLowerCase()}`">
                  {{ row.temperature || '-' }}<em v-if="row.temperature">℃</em>
                </span>
              </td>
              <td>
                <span class="severity-pill" :class="`is-${String(row.severity || '').toLowerCase()}`">
                  {{ severityText(row.severity) }}
                </span>
              </td>
              <td>
                <span class="status-pill" :class="`is-${String(row.status || '').toLowerCase()}`">
                  {{ statusText(row.status) }}
                </span>
              </td>
              <td class="cold-chain-time">{{ row.createTime || '-' }}</td>
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
        <label><span>库房</span>
          <select v-model="form.warehouseName">
            <option v-for="warehouse in options.warehouses" :key="warehouse.warehouseName" :value="warehouse.warehouseName">
              {{ warehouse.warehouseName }}
            </option>
          </select>
        </label>
        <label><span>商品</span>
          <select v-model="form.productCode">
            <option v-for="product in options.products" :key="product.productCode" :value="product.productCode">
              {{ product.productName }} / {{ product.productCode }}
            </option>
          </select>
        </label>
        <label><span>当前温度（℃）</span>
          <input v-model="form.temperature" type="number" step="0.1" placeholder="例如 9.5" />
        </label>
        <label><span>严重程度</span>
          <select v-model="form.severity">
            <option value="high">高</option>
            <option value="medium">中</option>
            <option value="low">低</option>
          </select>
        </label>
      </div>
      <p v-if="selectedProduct" class="cold-chain-product">
        当前商品：{{ selectedProduct.productName }}，规格 {{ selectedProduct.specModel || '-' }}，单位 {{ selectedProduct.unit || '-' }}
      </p>
      <div class="cold-chain-dialog-actions">
        <button class="btn btn-primary" type="button" :disabled="submitting" @click="submitException">保存异常</button>
        <button class="btn" type="button" :disabled="submitting" @click="resetForm">重置</button>
      </div>
    </el-dialog>
  </main>
</template>

<style scoped>
/* =====================================================================
   冷链监控
   全部使用项目设计令牌（--spd-*），与科室申领、UDI 追溯等页面保持一致。
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

/* ---------- 面板 ---------- */
.cold-chain-panel {
  box-sizing: border-box;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

.cold-chain-panel-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.cold-chain-panel-header h3 {
  color: var(--spd-text-heading);
  font-size: 15px;
  font-weight: 600;
}

.cold-chain-count {
  color: var(--spd-text-secondary);
  font-size: 12px;
}

/* ---------- 表格 ---------- */
.cold-chain-scroll {
  width: 100%;
  overflow-x: auto;
}

.cold-chain-table {
  width: 100%;
  /* 仅 7 列，容器足够宽时铺满，不产生横向滚动 */
  min-width: 760px;
  border-collapse: collapse;
  font-size: 13px;
}

th,
td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--spd-border-subtle);
  text-align: left;
  vertical-align: middle;
}

th {
  background: var(--spd-bg-subtle, #f6f8fb);
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

tbody tr:hover {
  background: var(--spd-primary-bg);
}

td small {
  display: block;
  margin-top: 3px;
  color: var(--spd-text-tertiary);
  font-size: 12px;
}

.cold-chain-time {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* ---------- 温度 ---------- */
.cold-temp {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.cold-temp em {
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
}

.cold-temp.is-high {
  color: #b91c1c;
}

.cold-temp.is-medium {
  color: #b45309;
}

.cold-temp.is-low {
  color: #047857;
}

/* ---------- 标签 ---------- */
.severity-pill,
.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.severity-pill.is-high {
  background: var(--spd-danger-bg, #fef2f2);
  color: #b91c1c;
}

.severity-pill.is-medium {
  background: var(--spd-warning-bg, #fff7ed);
  color: #b45309;
}

.severity-pill.is-low {
  background: var(--spd-success-bg, #ecfdf5);
  color: #047857;
}

.status-pill.is-pending_dispose {
  background: var(--spd-warning-bg, #fff7ed);
  color: #b45309;
}

.status-pill.is-disposed {
  background: var(--spd-info-bg, #eff6ff);
  color: #2563eb;
}

.status-pill.is-closed {
  background: var(--spd-bg-subtle, #f3f4f6);
  color: var(--spd-text-secondary);
}

/* ---------- 登记弹窗 ---------- */
.cold-chain-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
  gap: 12px 16px;
}

.cold-chain-form label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.cold-chain-form label > span {
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.cold-chain-form select,
.cold-chain-form input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--spd-border-default);
  border-radius: var(--spd-radius-sm, 6px);
  outline: 0;
  background: var(--spd-bg-container);
  color: var(--spd-text-primary);
  font: inherit;
  font-size: 13px;
}

.cold-chain-form select:focus,
.cold-chain-form input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--spd-primary-bg);
}

.cold-chain-product {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: var(--spd-radius-sm, 6px);
  background: var(--spd-primary-bg);
  color: var(--spd-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.cold-chain-dialog-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--spd-border-subtle);
}

/* ---------- 响应式 ---------- */
@media (max-width: 760px) {
  .cold-chain-page {
    padding: 12px 12px 20px;
  }

  .cold-chain-form {
    grid-template-columns: 1fr;
  }
}
</style>