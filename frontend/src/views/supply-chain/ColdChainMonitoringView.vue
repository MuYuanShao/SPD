<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RefreshCw, Search, Thermometer, X } from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import {
  createColdChainException,
  fetchClosureList,
  fetchClosureOptions,
  type ClosureOptions
} from '../../api/operationalClosure'

const loading = ref(false)
const submitting = ref(false)
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
    message.value = `冷链异常已登记：${result.eventNo || ''}`
    currentPage.value = 1
    await loadData()
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
  <section class="purchase-page closure-page">
    <div class="breadcrumb-line">供应链业务 / 冷链监控</div>

    <header class="detail-heading cold-chain-heading">
      <div>
        <span class="eyebrow">Cold Chain Monitoring</span>
        <h1>冷链监控</h1>
        <p>登记冷链温度异常，并查看冷链异常事件记录。</p>
      </div>
      <div class="cold-chain-total">
        <span>异常记录</span>
        <strong>{{ totalItems }}</strong>
      </div>
    </header>

    <section class="hospital-catalog-panel">
      <div class="panel-title">
        <Thermometer :size="20" />
        <h2>异常登记</h2>
      </div>
      <div class="hospital-query-grid cold-chain-form">
        <label>
          库房
          <select v-model="form.warehouseName">
            <option v-for="warehouse in options.warehouses" :key="warehouse.warehouseName" :value="warehouse.warehouseName">
              {{ warehouse.warehouseName }}
            </option>
          </select>
        </label>
        <label>
          商品
          <select v-model="form.productCode">
            <option v-for="product in options.products" :key="product.productCode" :value="product.productCode">
              {{ product.productName }} / {{ product.productCode }}
            </option>
          </select>
        </label>
        <label>
          当前温度
          <input v-model="form.temperature" type="number" step="0.1" placeholder="例如 9.5" />
        </label>
        <label>
          严重程度
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
      <div class="hospital-query-actions">
        <button class="btn btn-primary" type="button" :disabled="submitting" @click="submitException">
          <Search :size="18" />
          登记异常
        </button>
        <button class="btn btn-secondary" type="button" :disabled="submitting" @click="resetForm">
          <X :size="18" />
          重置
        </button>
        <button class="btn btn-secondary" type="button" :disabled="loading" @click="refresh">
          <RefreshCw :size="18" />
          刷新列表
        </button>
      </div>
      <p v-if="message" class="form-message">{{ message }}</p>
    </section>

    <section class="hospital-catalog-panel">
      <div class="panel-title cold-chain-list-title">
        <Thermometer :size="20" />
        <h2>冷链异常记录</h2>
      </div>
      <div class="table-scroll">
        <table class="master-table purchase-table">
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
            <tr v-if="loading">
              <td colspan="7" class="empty-cell">加载中...</td>
            </tr>
            <tr v-else-if="rows.length === 0">
              <td colspan="7" class="empty-cell">暂无冷链异常记录</td>
            </tr>
            <template v-else>
              <tr v-for="row in rows" :key="String(row.bizNo)">
                <td>
                  <strong>{{ row.bizNo || '-' }}</strong>
                </td>
                <td>{{ row.warehouseName || '-' }}</td>
                <td>
                  <strong>{{ row.productName || '-' }}</strong>
                  <span class="muted-cell">{{ row.productCode || '-' }}</span>
                </td>
                <td>{{ row.temperature || '-' }}</td>
                <td>{{ severityText(row.severity) }}</td>
                <td>{{ statusText(row.status) }}</td>
                <td>{{ row.createTime || '-' }}</td>
              </tr>
            </template>
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
  </section>
</template>

<style scoped>
.cold-chain-heading {
  align-items: center;
  justify-content: space-between;
}

.cold-chain-total {
  min-width: 128px;
  padding: 14px 18px;
  border: 1px solid #dbe5ec;
  border-radius: 8px;
  background: #ffffff;
  text-align: right;
}

.cold-chain-total span,
.cold-chain-product {
  color: #64748b;
}

.cold-chain-total strong {
  display: block;
  margin-top: 4px;
  color: #0f766e;
  font-size: 28px;
}

.cold-chain-form {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.cold-chain-product {
  margin: 12px 0 0;
}

.cold-chain-list-title {
  margin-bottom: 16px;
}

.form-message {
  margin: 14px 0 0;
  color: #0f766e;
  font-weight: 600;
}

.table-scroll {
  overflow-x: auto;
}

.muted-cell {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.empty-cell {
  padding: 28px;
  color: #64748b;
  text-align: center;
}

@media (max-width: 760px) {
  .cold-chain-heading {
    align-items: stretch;
  }

  .cold-chain-total {
    text-align: left;
  }
}
</style>
