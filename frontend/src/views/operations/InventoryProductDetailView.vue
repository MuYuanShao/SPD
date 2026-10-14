<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw, Search, X } from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { fetchInventoryProductDetails, type InventoryProductDetail } from '../../api/reportCenter'

const keyword = ref('')
const rows = ref<InventoryProductDetail[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const error = ref('')
let appliedKeyword = ''
let sequence = 0
let request: AbortController | undefined

async function load() {
  const current = ++sequence
  request?.abort()
  const controller = request = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const result = await fetchInventoryProductDetails({ page: page.value, size: size.value, keyword: appliedKeyword }, controller.signal)
    if (current !== sequence) return
    const last = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > last) { page.value = last; await load(); return }
    rows.value = result.rows
    total.value = result.total
  } catch (reason) {
    if (current === sequence && !controller.signal.aborted) {
      total.value = 0
      error.value = '进销存商品明细加载失败，请刷新重试'
      ElMessage.error(error.value)
    }
  } finally {
    if (current === sequence) loading.value = false
  }
}

function search() {
  appliedKeyword = keyword.value.trim()
  page.value = 1
  void load()
}
function reset() {
  keyword.value = ''
  search()
}
function changePage(value: number) {
  if (value === page.value) return
  page.value = value
  void load()
}
function changeSize(value: number) {
  size.value = value
  page.value = 1
  void load()
}
function refresh() { void load() }
onMounted(load)
onBeforeUnmount(() => { ++sequence; request?.abort() })
</script>

<template>
  <main class="report-page inventory-product-page">
    <MdPageHeader
      crumb="运营与决策 / 报表中心 / 进销存明细"
      title="进销存商品明细"
      description="按库存记录查询商品、批次有效期及库位。"
      :loading="loading"
      :show-refresh="false"
      @refresh="refresh"
    />

    <section class="report-toolbar">
      <form class="report-filters" @submit.prevent="search">
        <label class="search-keyword">
          <span>关键词</span>
          <input v-model="keyword" maxlength="100" placeholder="搜索产品、注册证、厂家、配送商、规格或库位" />
        </label>
      </form>
      <div class="report-actions">
        <button class="btn btn-primary" type="button" :disabled="loading" @click="search"><Search :size="16" />查询</button>
        <button class="btn" type="button" :disabled="loading" @click="reset"><X :size="16" />重置</button>
        <button class="btn" type="button" :disabled="loading" @click="refresh"><RefreshCw :size="16" />刷新</button>
      </div>
    </section>

    <StatusMessage :message="error" tone="error" role="alert" />

    <section class="report-table-card" :aria-busy="loading">
      <el-table
        :data="rows"
        stripe
        row-key="id"
        class="report-table inventory-product-table"
        :empty-text="loading ? '正在加载进销存商品明细…' : error ? '加载失败，请刷新重试' : '暂无匹配商品'"
      >
        <el-table-column prop="name" label="产品名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="registrationNo" label="注册证" min-width="200">
          <template #default="{ row }">{{ row.registrationNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="manufacturerName" label="厂家" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.manufacturerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="distributorName" label="配送商" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.distributorName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="expiry" label="有效期至" min-width="150" />
        <el-table-column prop="specification" label="规格型号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
        <el-table-column prop="location" label="库位" min-width="220" show-overflow-tooltip />
      </el-table>
      <PaginationControls
        :page="page"
        :size="size"
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
   报表中心 / 进销存商品明细 InventoryProductDetailView
   ---------------------------------------------------------------------
   与其他报表一致：MdPageHeader + 工具区 + 表格 + 分页器。
   区别：只有一个搜索输入 + 三按钮，工具区只占一行。
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
  grid-template-columns: minmax(280px, 1fr);
  gap: 8px;
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
.report-filters input {
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
.report-filters input:focus {
  outline: none;
  border-color: var(--spd-primary, #1f8d7a);
  box-shadow: 0 0 0 3px rgba(31, 141, 122, 0.16);
}
.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

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

@media (max-width: 1100px) {
  .report-toolbar { grid-template-columns: 1fr; }
  .report-actions { justify-content: flex-start; }
}
@media (max-width: 720px) {
  .report-actions { display: grid; grid-template-columns: repeat(2, 1fr); }
  .report-actions .btn { width: 100%; }
}
</style>
