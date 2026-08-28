<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { History, RefreshCw, RotateCcw, Search } from '@lucide/vue'
import EmptyState from '../../components/common/EmptyState.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import SectionTitle from '../../components/common/SectionTitle.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { fetchPickingRecords } from '../../api/operationalClosure'
import { formatStatusText } from '../../utils/chineseDisplay'

const loading = ref(false)
const message = ref('')
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const query = reactive({
  deliveryNo: '',
  requisitionNo: '',
  deptName: '',
  warehouseName: '',
  productKeyword: '',
  labelNo: '',
  status: '',
  dateFrom: '',
  dateTo: ''
})

function normalizedParams() {
  return Object.fromEntries(
    Object.entries(query)
      .map(([key, value]) => [key, value.trim()])
      .filter(([, value]) => value)
  ) as Record<string, string>
}

async function loadRecords() {
  if (query.dateFrom && query.dateTo && query.dateFrom > query.dateTo) {
    message.value = '开始日期不能晚于结束日期'
    return
  }
  loading.value = true
  message.value = ''
  try {
    const result = await fetchPickingRecords({
      ...normalizedParams(),
      page: String(page.value),
      size: String(size.value)
    })
    rows.value = result.rows
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    message.value = error instanceof Error ? error.message : '拣配记录加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void loadRecords()
}

function reset() {
  Object.assign(query, {
    deliveryNo: '',
    requisitionNo: '',
    deptName: '',
    warehouseName: '',
    productKeyword: '',
    labelNo: '',
    status: '',
    dateFrom: '',
    dateTo: ''
  })
  page.value = 1
  void loadRecords()
}

function changePage(nextPage: number) {
  page.value = nextPage
  void loadRecords()
}

function changeSize(nextSize: number) {
  size.value = nextSize
  page.value = 1
  void loadRecords()
}

function quantityText(value: unknown) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed.toLocaleString('zh-CN') : '-'
}

onMounted(loadRecords)
</script>

<template>
  <section class="picking-record-page">
    <PageHeader
      eyebrow="供应链业务"
      title="拣配记录"
      description="集中查询中心库拣配出库及科室签收记录"
    >
      <template #actions>
        <button class="btn" type="button" :disabled="loading" @click="loadRecords">
          <RefreshCw :size="16" />
          刷新
        </button>
      </template>
    </PageHeader>

    <section class="record-query-card" aria-label="拣配记录查询条件">
      <form class="record-query-grid" @submit.prevent="search">
        <label><span>配送单号</span><input v-model.trim="query.deliveryNo" placeholder="请输入配送单号" /></label>
        <label><span>申领单号</span><input v-model.trim="query.requisitionNo" placeholder="请输入申领单号" /></label>
        <label><span>科室</span><input v-model.trim="query.deptName" placeholder="请输入科室名称" /></label>
        <label><span>库房</span><input v-model.trim="query.warehouseName" placeholder="请输入库房名称" /></label>
        <label><span>商品</span><input v-model.trim="query.productKeyword" placeholder="商品编码 / 名称" /></label>
        <label><span>定数包码</span><input v-model.trim="query.labelNo" placeholder="请输入定数包码" /></label>
        <label>
          <span>状态</span>
          <select v-model="query.status">
            <option value="">全部</option>
            <option value="picked">待签收</option>
            <option value="signed">已签收</option>
          </select>
        </label>
        <label><span>开始日期</span><input v-model="query.dateFrom" type="date" /></label>
        <label><span>结束日期</span><input v-model="query.dateTo" type="date" /></label>
        <div class="record-query-actions">
          <button class="btn btn-primary" type="submit" :disabled="loading"><Search :size="16" />查询</button>
          <button class="btn" type="button" :disabled="loading" @click="reset"><RotateCcw :size="16" />重置</button>
        </div>
      </form>
    </section>

    <StatusMessage :message="message" tone="error" />

    <section class="record-table-card">
      <div class="record-table-title">
        <SectionTitle title="拣配记录列表" :level="3">
          <template #icon><History :size="19" /></template>
        </SectionTitle>
        <span>共 {{ total }} 条</span>
      </div>
      <div class="record-table-viewport">
        <table class="master-table record-table">
          <thead>
            <tr>
              <th>配送单号</th><th>申领单号</th><th>科室</th><th>出库库房</th>
              <th>商品信息</th><th class="numeric-cell">数量</th><th>定数包码</th>
              <th>状态</th><th>拣配时间</th><th>签收时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="10" class="record-empty"><EmptyState message="正在加载拣配记录..." /></td>
            </tr>
            <tr v-else-if="!rows.length">
              <td colspan="10" class="record-empty"><EmptyState message="暂无符合条件的拣配记录" /></td>
            </tr>
            <tr v-for="row in rows" v-else :key="String(row.bizNo)">
              <td class="primary-cell">{{ row.bizNo || '-' }}</td>
              <td>{{ row.sourceNo || '-' }}</td>
              <td>{{ row.deptName || '-' }}</td>
              <td>{{ row.warehouseName || '-' }}</td>
              <td><strong>{{ row.productName || '-' }}</strong><small>{{ row.productCode || '-' }}</small></td>
              <td class="numeric-cell">{{ quantityText(row.quantity) }}</td>
              <td class="label-cell" :title="String(row.labelNos || '-')">{{ row.labelNos || '-' }}</td>
              <td><span class="status-badge" :class="row.status === 'signed' ? 'enabled' : 'pending'">{{ formatStatusText(row.status) }}</span></td>
              <td>{{ row.createTime || '-' }}</td>
              <td>{{ row.finishTime || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="page" :size="size" :total="total" :loading="loading"
        @change-page="changePage" @change-size="changeSize"
      />
    </section>
  </section>
</template>

<style scoped>
.picking-record-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100dvh - 48px);
  min-height: 600px;
  max-width: 1600px;
  margin: 0 auto;
  container-type: inline-size;
}

.record-query-card, .record-table-card {
  box-sizing: border-box;
  border: 1px solid #e5eaf1;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 2px 8px rgb(31 50 79 / 4%);
}

.record-query-card { padding: 16px; }
.record-query-grid { display: grid; grid-template-columns: repeat(24, minmax(0, 1fr)); gap: 14px 16px; }
.record-query-grid label { grid-column: span 6; display: grid; gap: 6px; min-width: 0; }
.record-query-grid label > span { color: #53657a; font-size: 13px; font-weight: 600; }
.record-query-grid input, .record-query-grid select {
  box-sizing: border-box;
  width: 100%;
  height: 36px;
  border: 1px solid #d9e0e9;
  border-radius: 6px;
  background: #fff;
  padding: 0 11px;
  color: #17233d;
  font: inherit;
  outline: none;
}
.record-query-grid input:focus, .record-query-grid select:focus { border-color: #1677ff; box-shadow: 0 0 0 2px rgb(22 119 255 / 12%); }
.record-query-actions { grid-column: 19 / span 6; display: flex; align-items: flex-end; justify-content: flex-end; gap: 8px; }

.record-table-card { display: flex; flex: 1; flex-direction: column; min-height: 360px; padding: 16px; overflow: hidden; }
.record-table-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.record-table-title > div { display: flex; align-items: center; gap: 8px; color: #17233d; }
.record-table-title > span { color: #7a8699; font-size: 13px; }
.record-table-viewport { flex: 1; min-height: 240px; overflow: auto; border-top: 1px solid #edf1f5; }
.record-table { min-width: 1280px; }
.record-table thead { position: sticky; top: 0; z-index: 1; }
.record-table th, .record-table td { height: 46px; padding: 9px 12px; }
.record-table td { color: #34445b; }
.record-table td strong, .record-table td small { display: block; max-width: 220px; overflow: hidden; text-overflow: ellipsis; }
.record-table td small { margin-top: 3px; color: #8b98a9; font-size: 12px; }
.record-table .primary-cell { color: #1677ff; font-weight: 650; }
.record-table .numeric-cell { text-align: right; }
.record-table .label-cell { max-width: 220px; overflow: hidden; text-overflow: ellipsis; }
.record-empty { height: 180px !important; color: #8b98a9 !important; text-align: center !important; }
:deep(.hospital-pagination) { margin-top: auto; padding-top: 14px; border-top: 1px solid #edf1f5; }

@container (max-width: 1160px) {
  .record-query-grid label { grid-column: span 12; }
  .record-query-actions { grid-column: 13 / span 12; }
}

@container (max-width: 680px) {
  .record-query-grid label, .record-query-actions { grid-column: 1 / -1; }
  .record-query-actions { justify-content: stretch; }
  .record-query-actions .btn { flex: 1; }
}
</style>
