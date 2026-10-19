<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElDrawer } from 'element-plus/es/components/drawer/index.mjs'
import 'element-plus/theme-chalk/el-drawer.css'
import { useRouter } from 'vue-router'
import { Eye, RefreshCw, Search, SlidersHorizontal } from '@lucide/vue'
import MdPageHeader from '../master-data/MdPageHeader.vue'
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
  <section class="md-page inventory-ledger-page">
    <MdPageHeader
      crumb="供应链业务 / 库存账务"
      title="库存交易流水"
      description="按事件发生时快照查询不可变库存事实，主表始终一条事件一行。"
      :loading="loading"
      @refresh="load"
    />

    <StatusMessage v-if="error" :message="error" tone="error" />

    <section class="hospital-catalog-panel ledger-panel">
      <form class="ledger-filter" role="search" @submit.prevent="search">
        <label><span>开始日期</span><input v-model="query.startTime" class="md-input" type="date" /></label>
        <label><span>结束日期</span><input v-model="query.endTime" class="md-input" type="date" /></label>
        <label><span>交易类型</span><select v-model="query.transactionTypeCode" class="md-select"><option value="">全部类型</option><option v-for="type in types" :key="type.code" :value="type.code">{{ type.label }}</option></select></label>
        <label><span>库房</span><input v-model.trim="query.warehouseName" class="md-input" placeholder="库房名称" /></label>
        <label><span>商品</span><input v-model.trim="query.productName" class="md-input" placeholder="商品名称" /></label>
        <div class="ledger-filter-actions">
          <button class="btn btn-primary md-btn md-btn-primary" type="submit" :disabled="loading"><Search :size="15" />查询</button>
          <button class="btn md-btn" type="button" @click="reset">重置</button>
          <button class="btn md-btn advanced-toggle" :class="{ active: advancedOpen || hasAdvancedFilter }" type="button" :aria-expanded="advancedOpen" @click="advancedOpen = !advancedOpen">
            <SlidersHorizontal :size="15" />高级筛选<span v-if="hasAdvancedFilter" class="filter-dot" aria-label="高级筛选已启用"></span>
          </button>
        </div>
        <div v-if="advancedOpen" class="advanced-filters">
          <label><span>科室</span><input v-model.trim="query.deptName" class="md-input" placeholder="科室名称" /></label>
          <label><span>商品编码</span><input v-model.trim="query.productCode" class="md-input" placeholder="商品编码" /></label>
          <label><span>系统批次</span><input v-model.trim="query.batchNo" class="md-input" placeholder="系统批次号" /></label>
          <label><span>生产批号</span><input v-model.trim="query.productionBatchNo" class="md-input" placeholder="生产批号" /></label>
          <label><span>厂家</span><input v-model.trim="query.manufacturerName" class="md-input" placeholder="生产厂家" /></label>
          <label><span>供应商</span><input v-model.trim="query.supplierName" class="md-input" placeholder="供应商" /></label>
          <label><span>来源单据</span><input v-model.trim="query.sourceBizNo" class="md-input" placeholder="来源单号" /></label>
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
              <td class="ledger-pair-cell"><strong>{{ row.warehouseName || '-' }}</strong><small>{{ row.deptName || '无科室归属' }}</small></td>
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
/* ================================================================
   库存交易流水 ledger
   ----------------------------------------------------------------
   与采购管理 / 收货验收 / 库存管理 统一到 md 设计语言：
   1. 页头改用 MdPageHeader
   2. 面板 md 卡片化，查询区紧凑
   3. 表格 md 密度：表头 12px / 9px 12px，表体 13px / 8px 12px
   4. 汇总条去掉硬编码蓝色，改用主色令牌
   5. 补齐此前缺失的样式：sticky-event / sticky-product /
      code-cell / number-cell / ledger-table-wrap 等类名在旧版
      样式表中未定义，导致吸附列与数字对齐从未生效，本次补上。

   优先级：.master-table 的 padding / font-size / color 由
   spd-override.css 以 !important 统一设定，覆盖时加
   .inventory-ledger-page 前缀提高特指度。
   ================================================================ */

/* ---------- 页面外壳 ---------- */
.inventory-ledger-page {
  display: flex !important;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  padding: 0 20px;
}

/* ---------- 面板卡片 ---------- */
.inventory-ledger-page .ledger-panel {
  display: flex !important;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 14px 16px 16px;
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--md-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
}

/* ---------- 查询区 ---------- */
.ledger-filter {
  display: grid;
  grid-template-columns: repeat(5, minmax(150px, 1fr));
  gap: 10px 14px;
  align-items: end;
  padding: 12px;
  background: var(--md-bg-muted, var(--spd-bg-page));
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
}

.ledger-filter label,
.advanced-filters label {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.ledger-filter label > span,
.advanced-filters label > span {
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
}

.ledger-filter .md-input,
.ledger-filter .md-select,
.advanced-filters .md-input {
  min-height: 30px;
  font-size: 13px;
}

.ledger-filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.ledger-filter-actions .md-btn {
  min-height: 30px;
  padding: 0 12px;
  font-size: 13px;
}

.ledger-filter-actions .md-btn svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.advanced-toggle {
  position: relative;
}

/* 高级筛选启用态：用主色令牌，不再使用硬编码蓝 */
.advanced-toggle.active {
  border-color: var(--md-primary, var(--spd-primary));
  color: var(--md-primary, var(--spd-primary));
  background: var(--md-primary-soft, var(--spd-primary-bg));
}

.filter-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--md-primary, var(--spd-primary));
}

.advanced-filters {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 10px 14px;
  padding: 12px;
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
}

/* ---------- 汇总条 ---------- */
.ledger-summary {
  display: flex;
  gap: 8px 22px;
  flex-wrap: wrap;
  align-items: center;
  padding: 9px 12px;
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-left: 3px solid var(--md-primary, var(--spd-primary));
  border-radius: var(--md-radius, 6px);
  background: var(--md-bg-muted, var(--spd-bg-page));
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: 12px;
}

.ledger-summary strong {
  margin-left: 5px;
  color: var(--md-text-heading, var(--spd-text-heading));
  font-family: var(--md-font-mono, ui-monospace, monospace);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.muted-hint {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 12px;
}

/* ---------- 表格 ---------- */
.ledger-table-wrap {
  max-height: max(300px, calc(100dvh - 460px));
  overflow: auto;
  border: 1px solid var(--md-border-soft, var(--spd-border-subtle));
  border-radius: var(--md-radius, 6px);
  background: var(--md-bg-card, #fff);
  scrollbar-width: thin;
  scrollbar-color: var(--md-border, var(--spd-border-default)) transparent;
}

.ledger-table-wrap::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.ledger-table-wrap::-webkit-scrollbar-thumb {
  background: var(--md-border, var(--spd-border-default));
  border-radius: 4px;
}

.inventory-ledger-page .ledger-table {
  min-width: 1160px;
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px !important;
}

.inventory-ledger-page .ledger-table thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  padding: 9px 12px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  letter-spacing: 0.2px;
  white-space: nowrap;
  color: var(--md-text-heading) !important;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%) !important;
  border-bottom: 1px solid var(--md-border, var(--spd-border-default)) !important;
}

.inventory-ledger-page .ledger-table tbody td {
  padding: 8px 12px !important;
  font-size: 13px !important;
  color: var(--md-text) !important;
  border-bottom: 1px solid var(--md-border-soft, var(--spd-border-subtle)) !important;
  vertical-align: middle;
  white-space: nowrap;
  background: var(--md-bg-card, #fff) !important;
}

.inventory-ledger-page .ledger-table tbody tr:nth-child(even) td {
  background: var(--md-bg-muted, #fafbfc) !important;
}

.inventory-ledger-page .ledger-table tbody tr:hover td {
  background: var(--md-primary-soft, #f0f7f6) !important;
}

/* ---------- 吸附列（此前未定义，本次补齐） ---------- */
.ledger-table .sticky-event {
  position: sticky;
  left: 0;
  z-index: 2;
  font-family: var(--md-font-mono, ui-monospace, monospace);
  font-size: 12px !important;
  color: var(--md-text-heading) !important;
}

/* 商品列不能同为 left:0 —— 会与事件号列叠在一起。
   这里改为不吸附，仅保留最小宽度；事件号列已足够作为定位锚点。 */
.ledger-table .sticky-product {
  min-width: 180px;
}

/* 吸附列在斑马纹/hover 下需保持不透明，否则会与下方内容透叠 */
.ledger-table tbody tr:nth-child(even) .sticky-event {
  background: var(--md-bg-muted, #fafbfc) !important;
}

.ledger-table tbody tr:hover .sticky-event {
  background: var(--md-primary-soft, #f0f7f6) !important;
}

.ledger-table thead th.sticky-event {
  z-index: 4;
  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%) !important;
}

/* ---------- 数字 / 编码列（此前未定义，本次补齐） ---------- */
.ledger-table .number-cell {
  text-align: right;
  font-family: var(--md-font-mono, ui-monospace, monospace);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.ledger-table .code-cell {
  font-family: var(--md-font-mono, ui-monospace, monospace);
  font-size: 12px !important;
  white-space: nowrap;
}

.ledger-table .time-cell {
  font-variant-numeric: tabular-nums;
  color: var(--md-text-secondary, var(--spd-text-secondary)) !important;
  white-space: nowrap;
}

/* 库房 / 科室 与 商品 / 编码 两行结构 */
.ledger-table .ledger-pair-cell strong,
.ledger-table .sticky-product strong {
  display: block;
  color: var(--md-text-heading, var(--spd-text-heading));
  font-weight: 600;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ledger-table .ledger-pair-cell small,
.ledger-table .sticky-product small {
  display: block;
  margin-top: 1px;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 11px;
}

/* ---------- 出库 / 调价 标识 ---------- */
/* 需压过上面的 `.inventory-ledger-page .ledger-table tbody td`（同为
   !important，但特指度更高），故这里加表选择器前缀。 */
.ledger-table tbody td.qty-out {
  color: var(--md-danger, var(--spd-danger)) !important;
  font-weight: 600;
}

.event-type-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--md-primary-soft, #e8f1ef);
  color: var(--md-primary-strong, var(--md-primary, #0d6b63));
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

/* 调价事件：橙色系，与主色区分 */
.event-type-chip.valuation {
  background: var(--spd-warning-bg, #fff4e5);
  color: var(--spd-warning-text, #9a5b00);
}

/* 调价事件：橙色系，与主色区分。
   同为 !important，需比 tbody td 更高的特指度。 */
.ledger-table tbody tr.valuation-row td {
  background: color-mix(in srgb, var(--spd-warning-bg, #fff4e5) 45%, white) !important;
}

.ledger-table tbody tr.valuation-row:hover td {
  background: color-mix(in srgb, var(--spd-warning-bg, #fff4e5) 70%, white) !important;
}

.ledger-table tbody tr.valuation-row .sticky-event {
  background: color-mix(in srgb, var(--spd-warning-bg, #fff4e5) 45%, white) !important;
}

/* ---------- 详情抽屉 ---------- */
.ledger-detail {
  display: grid;
  gap: 18px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid var(--md-border-soft, #e7ebf0);
  border-left: 1px solid var(--md-border-soft, #e7ebf0);
  border-radius: var(--md-radius, 6px);
  overflow: hidden;
}

.detail-grid > div {
  display: grid;
  gap: 5px;
  padding: 11px;
  border-right: 1px solid var(--md-border-soft, #e7ebf0);
  border-bottom: 1px solid var(--md-border-soft, #e7ebf0);
}

.detail-grid span,
.trace-list span {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 12px;
}

.detail-grid strong {
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
}

.source-link {
  border: 0;
  padding: 0;
  background: none;
  color: var(--md-primary, var(--spd-primary));
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.source-link:hover {
  text-decoration: underline;
}

.trace-list {
  display: grid;
  gap: 8px;
}

.trace-list > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  border: 1px solid var(--md-border-soft, #e7ebf0);
  border-radius: var(--md-radius, 6px);
}

.trace-list strong {
  font-family: var(--md-font-mono, ui-monospace, monospace);
  font-size: 12px;
  color: var(--md-text-heading, var(--spd-text-heading));
}

.detail-remark {
  margin: 0;
  padding: 10px 12px;
  background: var(--md-bg-muted, var(--spd-bg-page));
  border-radius: var(--md-radius, 6px);
  color: var(--md-text, var(--spd-text-primary));
  font-size: 13px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1280px) {
  .ledger-filter {
    grid-template-columns: repeat(3, minmax(150px, 1fr));
  }

  .advanced-filters {
    grid-template-columns: repeat(3, minmax(150px, 1fr));
  }
}

@media (max-width: 760px) {
  .inventory-ledger-page {
    padding: 12px 12px 20px;
  }

  .ledger-filter,
  .advanced-filters,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .ledger-filter-actions {
    grid-column: 1;
  }

  .ledger-summary {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .ledger-table-wrap {
    max-height: max(240px, calc(100dvh - 400px));
  }
}
</style>

