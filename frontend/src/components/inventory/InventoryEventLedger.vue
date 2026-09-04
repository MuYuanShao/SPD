<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Eye, RefreshCw, Search, SlidersHorizontal } from '@lucide/vue'
import PageHeader from '../common/PageHeader.vue'
import SectionTitle from '../common/SectionTitle.vue'
import StatusMessage from '../common/StatusMessage.vue'
import EmptyState from '../common/EmptyState.vue'
import PaginationControls from '../common/PaginationControls.vue'
import {
  fetchInventoryEventDetail,
  fetchInventoryEvents,
  fetchInventoryTransactionTypes,
  type InventoryEventDetail,
  type InventoryEventQuery,
  type InventoryEventRow,
  type InventoryEventSummary,
  type InventoryTransactionTypeOption
} from '../../api/inventory'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const rows = ref<InventoryEventRow[]>([])
const types = ref<InventoryTransactionTypeOption[]>([])
const loading = ref(false)
const error = ref('')
const advancedOpen = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const summary = ref<InventoryEventSummary>()
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<InventoryEventDetail>()

const query = reactive({
  startTime: '', endTime: '', transactionTypeCode: '', warehouseName: '', productName: '',
  deptName: '', productCode: '', batchNo: '', productionBatchNo: '', manufacturerName: '',
  supplierName: '', sourceBizNo: ''
})

const hasAdvancedFilter = computed(() => Boolean(query.deptName || query.productCode || query.batchNo ||
  query.productionBatchNo || query.manufacturerName || query.supplierName || query.sourceBizNo))

function requestQuery(): InventoryEventQuery {
  return { page: page.value, size: size.value, ...query }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchInventoryEvents(requestQuery())
    rows.value = result.rows
    total.value = result.total
    summary.value = result.summary
  } catch (err) {
    error.value = err instanceof Error ? err.message : '库存交易流水加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void load()
}

function reset() {
  Object.keys(query).forEach((key) => { query[key as keyof typeof query] = '' })
  page.value = 1
  advancedOpen.value = false
  void load()
}

async function openDetail(row: InventoryEventRow) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = undefined
  try {
    detail.value = await fetchInventoryEventDetail(row.eventNo)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '流水详情加载失败'
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

function changePage(next: number) {
  page.value = next
  void load()
}

function changeSize(next: number) {
  size.value = next
  page.value = 1
  void load()
}

function number(value: unknown, digits = 2) {
  if (value === null || value === undefined || value === '') return '-'
  return Number(value).toFixed(digits)
}

function signed(value: unknown) {
  const amount = Number(value || 0)
  return `${amount > 0 ? '+' : ''}${number(amount, 4)}`
}

const sourceTargets: Record<string, { path: string; permission: string }> = {
  receiving_order: { path: '/features/receiving-acceptance', permission: 'receiving-order:read' },
  inventory_stocktaking: { path: '/features/stocktaking-management', permission: 'stocktaking-management:read' },
  batch_price_adjustment: { path: '/features/batch-price-adjustment', permission: 'batch-price-adjustment:read' },
  spd_delivery_order: { path: '/features/picking-delivery', permission: 'picking-delivery:read' },
  department_consumption: { path: '/features/department-consumption', permission: 'department-consumption:read' },
  quota_packing_task: { path: '/features/packing-task-confirmation', permission: 'quota-packing-task:read' }
}

function canOpenSource(event: InventoryEventDetail) {
  const target = event.sourceBizType ? sourceTargets[event.sourceBizType] : undefined
  return Boolean(target && authStore.hasPermission(target.permission))
}

function openSource(event: InventoryEventDetail) {
  const target = event.sourceBizType ? sourceTargets[event.sourceBizType] : undefined
  if (target && canOpenSource(event)) void router.push({ path: target.path, query: { sourceNo: event.sourceBizNo } })
}

onMounted(async () => {
  const [options] = await Promise.allSettled([fetchInventoryTransactionTypes(), load()])
  if (options.status === 'fulfilled') types.value = options.value.rows
})
</script>

<template>
  <section class="inventory-ledger-page">
    <PageHeader eyebrow="供应链业务 / 库存账务" title="库存交易流水" description="按事件发生时快照查询不可变库存事实，主表始终一条事件一行。">
      <template #actions>
        <button class="btn" type="button" :disabled="loading" @click="load"><RefreshCw :size="17" />刷新</button>
      </template>
    </PageHeader>

    <StatusMessage v-if="error" :message="error" tone="error" />

    <section class="hospital-catalog-panel ledger-panel">
      <form class="ledger-filter" role="search" @submit.prevent="search">
        <label><span>开始日期</span><input v-model="query.startTime" type="date" /></label>
        <label><span>结束日期</span><input v-model="query.endTime" type="date" /></label>
        <label><span>交易类型</span><select v-model="query.transactionTypeCode"><option value="">全部类型</option><option v-for="type in types" :key="type.code" :value="type.code">{{ type.label }}</option></select></label>
        <label><span>库房</span><input v-model.trim="query.warehouseName" placeholder="库房名称" /></label>
        <label><span>商品</span><input v-model.trim="query.productName" placeholder="商品名称" /></label>
        <div class="ledger-filter-actions">
          <button class="btn btn-primary" type="submit" :disabled="loading"><Search :size="17" />查询</button>
          <button class="btn" type="button" @click="reset">重置</button>
          <button class="btn advanced-toggle" :class="{ active: advancedOpen || hasAdvancedFilter }" type="button" :aria-expanded="advancedOpen" @click="advancedOpen = !advancedOpen">
            <SlidersHorizontal :size="17" />高级筛选<span v-if="hasAdvancedFilter" class="filter-dot" aria-label="高级筛选已启用"></span>
          </button>
        </div>
        <div v-if="advancedOpen" class="advanced-filters">
          <label><span>科室</span><input v-model.trim="query.deptName" placeholder="科室名称" /></label>
          <label><span>商品编码</span><input v-model.trim="query.productCode" placeholder="商品编码" /></label>
          <label><span>系统批次</span><input v-model.trim="query.batchNo" placeholder="系统批次号" /></label>
          <label><span>生产批号</span><input v-model.trim="query.productionBatchNo" placeholder="生产批号" /></label>
          <label><span>厂家</span><input v-model.trim="query.manufacturerName" placeholder="生产厂家" /></label>
          <label><span>供应商</span><input v-model.trim="query.supplierName" placeholder="供应商" /></label>
          <label><span>来源单据</span><input v-model.trim="query.sourceBizNo" placeholder="来源单号" /></label>
        </div>
      </form>

      <div v-if="summary" class="ledger-summary" aria-label="当前筛选汇总">
        <span>入库 <strong>{{ number(summary.inboundQty, 4) }}</strong></span>
        <span>出库 <strong>{{ number(summary.outboundQty, 4) }}</strong></span>
        <span>净变动 <strong>{{ signed(summary.netQty) }}</strong></span>
        <span>交易金额 <strong>¥ {{ number(summary.movementAmount) }}</strong></span>
        <span>调价价值变化 <strong>¥ {{ signed(summary.valuationChange) }}</strong></span>
      </div>

      <SectionTitle title="流水明细" :level="3"><span class="muted-hint">共 {{ total }} 条事件</span></SectionTitle>
      <StatusMessage v-if="loading" message="正在加载库存交易流水..." tone="info" />
      <div v-else-if="rows.length" class="table-scroll ledger-table-wrap">
        <table class="master-table ledger-table">
          <thead><tr><th>事件号</th><th>科室 / 库房</th><th>商品</th><th>交易类型</th><th>数量</th><th>事件单价</th><th>金额</th><th>结存</th><th>发生时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.eventNo" :class="{ 'valuation-row': row.eventCategory === 'valuation' }">
              <td class="code-cell sticky-event">{{ row.eventNo }}</td>
              <td><strong>{{ row.warehouseName || '-' }}</strong><small>{{ row.deptName || '无科室归属' }}</small></td>
              <td class="sticky-product"><strong>{{ row.productName }}</strong><small>{{ row.productCode }}</small></td>
              <td><span class="event-type-chip" :class="row.eventCategory">{{ row.transactionTypeName }}</span></td>
              <td class="number-cell" :class="{ 'qty-out': Number(row.qtyChange) < 0 }">{{ row.eventCategory === 'valuation' ? '0.0000' : signed(row.qtyChange) }}</td>
              <td class="number-cell">¥ {{ number(row.unitPrice) }}</td>
              <td class="number-cell">¥ {{ number(row.eventCategory === 'valuation' ? row.valueChange : row.amount) }}</td>
              <td class="number-cell">{{ number(row.qtyAfter, 4) }}</td>
              <td class="time-cell">{{ row.eventTime }}</td>
              <td><button class="btn-text" type="button" @click="openDetail(row)"><Eye :size="15" />详情<span v-if="row.traceCount">（{{ row.traceCount }}）</span></button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else message="当前筛选条件下暂无库存交易流水" />
      <PaginationControls :page="page" :size="size" :total="total" :loading="loading" @change-page="changePage" @change-size="changeSize" />
    </section>

    <el-drawer v-model="detailOpen" title="库存流水详情" size="min(720px, 92vw)" destroy-on-close>
      <StatusMessage v-if="detailLoading" message="正在加载流水详情..." tone="info" />
      <div v-else-if="detail" class="ledger-detail">
        <div class="detail-grid">
          <div><span>事件号</span><strong>{{ detail.eventNo }}</strong></div><div><span>交易类型</span><strong>{{ detail.transactionTypeName }}</strong></div>
          <div><span>科室</span><strong>{{ detail.deptName || '无科室归属' }}</strong></div><div><span>库房</span><strong>{{ detail.warehouseName }}</strong></div>
          <div><span>商品</span><strong>{{ detail.productName }}（{{ detail.productCode }}）</strong></div><div><span>规格 / 单位</span><strong>{{ detail.specModel || '-' }} / {{ detail.unit || '-' }}</strong></div>
          <div><span>系统批次 / 生产批号</span><strong>{{ detail.batchNo || '-' }} / {{ detail.productionBatchNo || '-' }}</strong></div><div><span>注册证号</span><strong>{{ detail.registrationNo || '-' }}</strong></div>
          <div><span>厂家</span><strong>{{ detail.manufacturerName || '-' }}</strong></div><div><span>供应商</span><strong>{{ detail.supplierName || '-' }}</strong></div>
          <div><span>事件价格 / 金额</span><strong>¥ {{ number(detail.unitPrice) }} / ¥ {{ number(detail.amount) }}</strong></div><div><span>发生时间</span><strong>{{ detail.eventTime }}</strong></div>
          <div v-if="detail.eventCategory === 'valuation'"><span>调价前 / 调价后</span><strong>¥ {{ number(detail.oldUnitPrice) }} → ¥ {{ number(detail.newUnitPrice) }}</strong></div><div v-if="detail.eventCategory === 'valuation'"><span>影响数量 / 价值变化</span><strong>{{ number(detail.affectedQty, 4) }} / ¥ {{ signed(detail.valueChange) }}</strong></div>
          <div><span>来源单据</span><strong><button v-if="detail.sourceBizNo && canOpenSource(detail)" class="source-link" type="button" @click="openSource(detail)">{{ detail.sourceBizNo }}</button><template v-else>{{ detail.sourceBizNo || '-' }}</template></strong></div><div><span>快照来源</span><strong>{{ detail.snapshotOrigin === 'captured' ? '事件发生时固化' : '历史迁移补录' }}</strong></div>
        </div>
        <SectionTitle title="追溯对象" :level="3" />
        <div v-if="detail.traceCodes?.length" class="trace-list"><div v-for="trace in detail.traceCodes" :key="trace.traceCodeId"><strong>{{ trace.packageLabelNo || trace.udiCode || trace.uniqueCode || trace.traceCodeId }}</strong><span>{{ trace.traceType }} · 数量 {{ trace.linkedQuantity }}</span></div></div>
        <EmptyState v-else message="该事件未关联唯一码或定数包标签" align="left" />
        <SectionTitle title="备注" :level="3" /><p class="detail-remark">{{ detail.remark || '无' }}</p>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.inventory-ledger-page{display:grid;gap:16px}.ledger-panel{display:grid;gap:14px}.ledger-filter{display:grid;grid-template-columns:repeat(5,minmax(150px,1fr));gap:12px;align-items:end}.ledger-filter label,.advanced-filters label{display:grid;gap:6px;font-size:13px;color:var(--text-secondary,#596579)}.ledger-filter input,.ledger-filter select{min-height:36px;border:1px solid var(--border-color,#dfe4eb);border-radius:6px;padding:0 10px;background:#fff;color:inherit}.ledger-filter-actions{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.advanced-toggle{position:relative}.advanced-toggle.active{border-color:#3b82f6;color:#2563eb}.filter-dot{width:7px;height:7px;border-radius:50%;background:#2563eb}.advanced-filters{grid-column:1/-1;display:grid;grid-template-columns:repeat(4,minmax(150px,1fr));gap:12px;padding:12px;background:#f7f9fc;border:1px solid #e8edf3;border-radius:8px}.ledger-summary{display:flex;gap:8px 22px;flex-wrap:wrap;padding:9px 12px;border-left:3px solid #2563eb;background:#f5f8fc;color:#596579;font-size:13px}.ledger-summary strong{margin-left:5px;color:#172033;font-variant-numeric:tabular-nums}.section-title{display:flex;align-items:center;gap:10px}.ledger-table{min-width:1160px}.ledger-table th,.ledger-table td{white-space:nowrap}.ledger-table td strong,.ledger-table td small{display:block}.ledger-table td small{margin-top:3px;color:#7b8798}.event-type-chip{display:inline-flex;padding:3px 8px;border-radius:999px;background:#eaf2ff;color:#245fb8;font-size:12px}.event-type-chip.valuation{background:#fff2dd;color:#9a5b00}.valuation-row{background:#fffaf1}.qty-out{color:#c2413b}.time-cell{font-variant-numeric:tabular-nums}.ledger-detail{display:grid;gap:18px}.detail-grid{display:grid;grid-template-columns:1fr 1fr;border-top:1px solid #e7ebf0;border-left:1px solid #e7ebf0}.detail-grid>div{display:grid;gap:5px;padding:11px;border-right:1px solid #e7ebf0;border-bottom:1px solid #e7ebf0}.detail-grid span,.trace-list span{font-size:12px;color:#738095}.detail-grid strong{font-size:14px;font-weight:600}.source-link{border:0;padding:0;background:none;color:#2563eb;font:inherit;font-weight:600;cursor:pointer}.source-link:hover{text-decoration:underline}.trace-list{display:grid;gap:8px}.trace-list>div{display:flex;justify-content:space-between;gap:16px;padding:10px 12px;border:1px solid #e7ebf0;border-radius:7px}.detail-remark{margin:0;padding:10px 12px;background:#f7f9fc;border-radius:7px}@media(max-width:1280px){.ledger-filter{grid-template-columns:repeat(3,minmax(150px,1fr))}.advanced-filters{grid-template-columns:repeat(3,minmax(150px,1fr))}}@media(max-width:760px){.ledger-filter,.advanced-filters,.detail-grid{grid-template-columns:1fr}.ledger-filter-actions{grid-column:1}.ledger-summary{display:grid;grid-template-columns:1fr 1fr}.inventory-ledger-page{gap:10px}}
</style>
