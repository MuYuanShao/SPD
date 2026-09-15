<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Search, RefreshCw, X } from '@lucide/vue'
import { fetchInventoryProductDetails, type InventoryProductDetail } from '../../api/reportCenter'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'

const keyword = ref('')
const rows = ref<InventoryProductDetail[]>([])
const page = ref(1), size = ref(20), total = ref(0)
const loading = ref(false), error = ref('')
let appliedKeyword = ''
let sequence = 0
let request: AbortController | undefined
async function load() {
  const current = ++sequence
  request?.abort()
  const controller = request = new AbortController()
  loading.value = true
  error.value = ''
  rows.value = []
  try {
    const result = await fetchInventoryProductDetails({ page: page.value, size: size.value, keyword: appliedKeyword }, controller.signal)
    if (current !== sequence) return
    const last = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > last) { page.value = last; await load(); return }
    rows.value = result.rows
    total.value = result.total
  } catch {
    if (current === sequence && !controller.signal.aborted) {
      total.value = 0
      error.value = '进销存商品明细加载失败，请刷新重试'
    }
  } finally {
    if (current === sequence) loading.value = false
  }
}
function search() { appliedKeyword = keyword.value.trim(); page.value = 1; void load() }
function reset() { keyword.value = ''; search() }
function changePage(value: number) { page.value = value; void load() }
function changeSize(value: number) { size.value = value; page.value = 1; void load() }
onMounted(load)
onBeforeUnmount(() => { ++sequence; request?.abort() })
</script>

<template>
  <section class="inventory-product-report" aria-label="进销存商品明细">
    <div class="report-heading"><h2>进销存商品明细</h2><span>按库存记录查询商品、批次有效期及库位</span></div>
    <form class="report-query" @submit.prevent="search">
      <label for="inventory-product-keyword">关键词</label>
      <input id="inventory-product-keyword" v-model="keyword" maxlength="100" placeholder="搜索产品、注册证、厂家、配送商、规格或库位" />
      <button class="btn btn-primary" type="submit"><Search :size="16" />查询</button>
      <button class="btn" type="button" @click="reset"><X :size="16" />重置</button>
      <button class="btn" type="button" @click="load"><RefreshCw :size="16" />刷新</button>
    </form>
    <StatusMessage :message="error" tone="error" role="alert" />
    <el-table :data="rows" stripe row-key="id" :aria-busy="loading" :empty-text="loading ? '正在加载进销存商品明细…' : error ? '加载失败，请刷新重试' : '暂无匹配商品'">
      <el-table-column prop="name" label="产品名称" min-width="220" />
      <el-table-column prop="registrationNo" label="注册证" min-width="200"><template #default="{ row }">{{ row.registrationNo || '-' }}</template></el-table-column>
      <el-table-column prop="manufacturerName" label="厂家" min-width="180"><template #default="{ row }">{{ row.manufacturerName || '-' }}</template></el-table-column>
      <el-table-column prop="distributorName" label="配送商" min-width="180"><template #default="{ row }">{{ row.distributorName || '-' }}</template></el-table-column>
      <el-table-column prop="expiry" label="有效期至" min-width="150" class-name="expiry-cell" />
      <el-table-column prop="specification" label="规格型号" min-width="150" />
      <el-table-column prop="quantity" label="数量" width="100" align="right" />
      <el-table-column prop="location" label="库位" min-width="220" />
    </el-table>
    <PaginationControls :page="page" :size="size" :total="total" :loading="loading" @change-page="changePage" @change-size="changeSize" />
  </section>
</template>

<style scoped>
.inventory-product-report { min-width: 0; padding: 16px; border: 1px solid var(--fli-line, #e5eaf0); border-radius: 8px; background: white; }
.report-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 16px; }
.report-heading h2 { font-size: 18px; margin: 0; }
.report-heading span { color: #64748b; font-size: 13px; }
.report-query { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 16px; }
.report-query input { flex: 0 1 420px; min-width: 200px; height: 36px; padding: 0 10px; border: 1px solid #dbe5ec; border-radius: 6px; font: inherit; }
:deep(.expiry-cell .cell) { white-space: nowrap; }
.report-query input:focus-visible { outline: 2px solid var(--primary); }
</style>
