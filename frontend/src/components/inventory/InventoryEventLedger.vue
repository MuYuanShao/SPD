<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElDrawer } from 'element-plus/es/components/drawer/index.mjs'
import 'element-plus/theme-chalk/el-drawer.css'
import { useRouter } from 'vue-router'
import { ChevronDown, Columns3, Eye, Search, SlidersHorizontal } from '@lucide/vue'
import MdPageHeader from '../master-data/MdPageHeader.vue'
import MdDataTableCard from '../master-data/MdDataTableCard.vue'
import MdPagination from '../master-data/MdPagination.vue'
import SectionTitle from '../common/SectionTitle.vue'
import StatusMessage from '../common/StatusMessage.vue'
import EmptyState from '../common/EmptyState.vue'
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
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'

const router = useRouter()
const authStore = useAuthStore()

/* ===== 列表列配置 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：各列宽度互相独立，拖拽一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const LEDGER_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'eventNo', label: '事件号', width: 170, visible: true, weight: 6, minWidth: 140, maxWidth: 280 },
  { key: 'scope', label: '科室 / 库房', width: 170, visible: true, flexible: true, idealWidth: 220, weight: 7, minWidth: 130, maxWidth: 320 },
  { key: 'product', label: '商品', width: 190, visible: true, flexible: true, idealWidth: 240, weight: 8, minWidth: 140, maxWidth: 340 },
  { key: 'transactionType', label: '交易类型', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 200 },
  { key: 'qtyChange', label: '数量', width: 110, visible: true, weight: 4, minWidth: 95, maxWidth: 180 },
  { key: 'unitPrice', label: '事件单价', width: 110, visible: true, weight: 4, minWidth: 95, maxWidth: 180 },
  { key: 'amount', label: '金额', width: 120, visible: true, weight: 4, minWidth: 100, maxWidth: 190 },
  { key: 'qtyAfter', label: '结存', width: 110, visible: true, weight: 4, minWidth: 95, maxWidth: 180 },
  { key: 'eventTime', label: '发生时间', width: 150, visible: true, weight: 5, minWidth: 130, maxWidth: 230 },
  // 「操作」列是最后一列，用 fill 标记吃掉表格右侧剩余空白，
  // 使整张表铺满内容区（100%）；按钮单行排列，不换行。
  { key: 'actions', label: '操作', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 320, fill: true }
]

const ledgerCols = useListColumns({
  storageKey: 'spd-inventory-event-ledger-cols-v1',
  defaults: LEDGER_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「商品 / 科室库房」等长文本列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const ledgerTableRef = ledgerCols.tableRef
const isLedgerColVisible = (key: string) => ledgerCols.isColumnVisible(key)
const ledgerColBinding = (key: string) => ledgerCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const ledgerActionFix = useActionColumnFix()
const ledgerActionFixed = computed(() => ledgerActionFix.actionFixedBinding.value)

watch(
  [ledgerTableRef],
  () => {
    ledgerActionFix.observe(ledgerTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const ledgerColSettingsAnchorRef = ref<HTMLElement | null>(null)
const ledgerColSettingsPopRef = ref<HTMLElement | null>(null)
const ledgerColSettingsPop = useColSettingsPopover({
  open: ledgerCols.settingsOpen,
  anchorRef: ledgerColSettingsAnchorRef,
  popRef: ledgerColSettingsPopRef
})

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
const detailRemark = computed(() => {
  const remark = detail.value?.remark
  if (detail.value?.eventType === 'quota_unpack_in' && (
    remark === 'unpack package label back to loose stock' || remark === '页面操作解包回散货'
  )) return ''
  return remark
})

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

/**
 * 行级样式类名：调价事件（valuation）整行用橙色系底色标记，
 * 与主色区分 —— 迁移到 el-table 后通过 row-class-name 实现。
 */
function ledgerRowClassName({ row }: { row: InventoryEventRow }) {
  return row.eventCategory === 'valuation' ? 'valuation-row' : ''
}

onMounted(async () => {
  const [options] = await Promise.allSettled([fetchInventoryTransactionTypes(), load()])
  if (options.status === 'fulfilled') types.value = options.value.rows
})
</script>

<template>
  <section class="md-page inventory-ledger-page spd-unified-list">
    <MdPageHeader
      crumb="供应链业务 / 库存账务"
      title="库存交易流水"
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

      <MdDataTableCard class="ledger-table-card">
        <template #toolbar>
          <div class="ulc-col-settings">
            <button
              ref="ledgerColSettingsAnchorRef"
              type="button"
              class="md-btn md-btn-primary"
              @click="ledgerCols.settingsOpen.value = !ledgerCols.settingsOpen.value"
            >
              <Columns3 :size="16" />
              <span>列设置</span>
              <ChevronDown :size="14" :class="{ rotated: ledgerCols.settingsOpen.value }" />
            </button>
            <div
              v-if="ledgerCols.settingsOpen.value"
              ref="ledgerColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': ledgerColSettingsPop.align.value === 'right', 'is-align-up': ledgerColSettingsPop.vertical.value === 'up' }"
              :style="{ '--ulc-pop-offset-y': ledgerColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': ledgerColSettingsPop.maxHeight.value + 'px' }"
            >
              <header>
                <strong>列展示设置</strong>
                <button type="button" class="ulc-link" @click="ledgerCols.resetColumns()">恢复默认</button>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in ledgerCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && ledgerCols.visibleColumnCount.value <= 1"
                    @change="ledgerCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <button type="button" class="ulc-action" @click="ledgerCols.settingsOpen.value = false">确定</button>
              </footer>
            </div>
          </div>
        </template>
        <template #meta-extra>
          <span class="md-chip primary">共 {{ total }} 条</span>
        </template>

        <div class="ulc-table-wrap">
          <el-table
            ref="ledgerTableRef"
            v-loading="loading"
            :data="rows"
            row-key="eventNo"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="loading ? '正在加载库存交易流水…' : '当前筛选条件下暂无库存交易流水'"
            :row-class-name="ledgerRowClassName"
            @header-dragend="(width: number, oldWidth: number, column: any) => ledgerCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="eventNo"
              v-if="isLedgerColVisible('eventNo')"
              label="事件号"
              v-bind="ledgerColBinding('eventNo')"
            >
              <template #header>
                <span class="ulc-th-text">事件号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text ledger-event-no">{{ row.eventNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="scope"
              v-if="isLedgerColVisible('scope')"
              label="科室 / 库房"
              v-bind="ledgerColBinding('scope')"
            >
              <template #header>
                <span class="ulc-th-text">科室 / 库房</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack ledger-pair-cell">
                  <span class="ulc-strong ulc-wrap-text">{{ row.warehouseName || '-' }}</span>
                  <span class="ulc-sub ulc-wrap-text is-sub">{{ row.deptName || '无科室归属' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="product"
              v-if="isLedgerColVisible('product')"
              label="商品"
              v-bind="ledgerColBinding('product')"
            >
              <template #header>
                <span class="ulc-th-text">商品</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack sticky-product">
                  <span class="ulc-strong ulc-wrap-text">{{ row.productName }}</span>
                  <span class="ulc-mono ulc-wrap-text">{{ row.productCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="transactionType"
              v-if="isLedgerColVisible('transactionType')"
              label="交易类型"
              v-bind="ledgerColBinding('transactionType')"
            >
              <template #header>
                <span class="ulc-th-text">交易类型</span>
              </template>
              <template #default="{ row }">
                <span class="event-type-chip" :class="row.eventCategory">{{ row.transactionTypeName }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="qtyChange"
              v-if="isLedgerColVisible('qtyChange')"
              label="数量"
              v-bind="ledgerColBinding('qtyChange')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number" :class="{ 'qty-out': Number(row.qtyChange) < 0 }">{{ row.eventCategory === 'valuation' ? '0.0000' : signed(row.qtyChange) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="unitPrice"
              v-if="isLedgerColVisible('unitPrice')"
              label="事件单价"
              v-bind="ledgerColBinding('unitPrice')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">事件单价</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number">¥ {{ number(row.unitPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="amount"
              v-if="isLedgerColVisible('amount')"
              label="金额"
              v-bind="ledgerColBinding('amount')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">金额</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number">¥ {{ number(row.eventCategory === 'valuation' ? row.valueChange : row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="qtyAfter"
              v-if="isLedgerColVisible('qtyAfter')"
              label="结存"
              v-bind="ledgerColBinding('qtyAfter')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">结存</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-number">{{ number(row.qtyAfter, 4) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="eventTime"
              v-if="isLedgerColVisible('eventTime')"
              label="发生时间"
              v-bind="ledgerColBinding('eventTime')"
            >
              <template #header>
                <span class="ulc-th-text">发生时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time time-cell">{{ row.eventTime }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isLedgerColVisible('actions')"
              label="操作"
              v-bind="ledgerColBinding('actions')"
              :fixed="ledgerActionFixed"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-actions ledger-actions">
                  <button class="ulc-action" type="button" @click="openDetail(row)">
                    <Eye :size="15" />详情
                  </button>
                </div>
              </template>
            </el-table-column>
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
        <SectionTitle title="备注" :level="3" /><p class="detail-remark">{{ detailRemark || '无' }}</p>
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
   3. 表格改用 Element Plus（ulc-table / is-sticky），
      外观统一由 unified-list.css 提供，
      本页只补充事件号 / 商品 / 科室库房等本页特有语义
   4. 汇总条去掉硬编码蓝色，改用主色令牌
   5. 调价事件（valuation）整行橙色系标记

   说明：原先的原生 <table class="master-table ledger-table">
   相关样式（表头吸顶、斑马纹、吸附列）已全部移除 ——
   这些能力改由 el-table + unified-list.css 承担。
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

/* 表格卡片与外层面板之间留出呼吸感 */
.inventory-ledger-page .ledger-table-card {
  width: 100%;
  min-width: 0;
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
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  line-height: var(--spd-lh-tight);
}

.ledger-filter .md-input,
.ledger-filter .md-select,
.advanced-filters .md-input {
  min-height: 30px;
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-base);
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
  font-size: var(--spd-font-size-sm);
}

.ledger-summary strong {
  margin-left: 5px;
  color: var(--md-text-heading, var(--spd-text-heading));
  font-family: var(--spd-font-family-mono);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.muted-hint {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-sm);
}

/* ---------- 表格 ----------
   表格本身（表头吸顶 / 斑马纹 / 列宽拖拽 / 右对齐）
   由 unified-list.css 的 .ulc-table 统一提供，
   这里只补充本页特有的语义样式。 */

/* 事件号列：等宽字体，保持单行不换行 */
.ledger-event-no {
  white-space: nowrap;
}

/* 库房 / 科室 两行结构：与 ulc-cell-stack 叠加使用 */
.ledger-pair-cell {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

/* 商品列不参与吸附（与事件号列同为 left:0 会互相叠压），
   仅保留最小宽度；事件号列已足够作为定位锚点。 */
.sticky-product {
  min-width: 180px;
}

/* 数字列：居中 + 等宽字体 + 等宽数字（tabular-nums）
   与统一列表标准一致：数值列与普通列一样居中。 */
.ulc-number {
  display: inline-block;
  text-align: center;
  font-family: var(--spd-font-family-mono);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  font-size: var(--spd-font-size-base, 14px);
  font-weight: 400;
  line-height: var(--spd-lh-tight, 1.35);
}

/* 时间列 */
.time-cell {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* ---------- 出库 / 调价 标识 ---------- */
.qty-out {
  color: var(--md-danger, var(--ulc-danger, var(--spd-danger))) !important;
  font-weight: 600;
}

.event-type-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--md-primary-soft, #e8f1ef);
  color: var(--md-primary-strong, var(--md-primary, #0d6b63));
  font-size: var(--spd-font-size-xs);
  font-weight: 600;
  white-space: nowrap;
}

/* 调价事件：橙色系，与主色区分 */
.event-type-chip.valuation {
  background: var(--spd-warning-bg, #fff4e5);
  color: var(--spd-warning-text, #9a5b00);
}

/* 调价事件整行标记：橙色系底色，与主色区分。
   el-table 的斑马纹与 hover 也需要一并压过，因此用 !important。 */
.inventory-ledger-page .ulc-table .el-table__row.valuation-row td.el-table__cell {
  background: color-mix(in srgb, var(--spd-warning-bg, #fff4e5) 45%, white) !important;
}

.inventory-ledger-page .ulc-table .el-table__row.valuation-row:hover td.el-table__cell {
  background: color-mix(in srgb, var(--spd-warning-bg, #fff4e5) 70%, white) !important;
}

/* 操作列：按钮单行不换行 */
.ledger-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  /* 宽度不足时允许换行，避免按钮横向溢出侵占相邻列 */
  flex-wrap: wrap;
  gap: 6px;
  white-space: nowrap;
  max-width: 100%;
  min-width: 0;
}

.ledger-actions .ulc-action {
  flex-shrink: 0;
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
  font-size: var(--spd-font-size-sm);
}

.detail-grid strong {
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: var(--spd-font-size-base);
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
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-sm);
  color: var(--md-text-heading, var(--spd-text-heading));
}

.detail-remark {
  margin: 0;
  padding: 10px 12px;
  background: var(--md-bg-muted, var(--spd-bg-page));
  border-radius: var(--md-radius, 6px);
  color: var(--md-text, var(--spd-text-primary));
  font-size: var(--spd-font-size-base);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
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
}
</style>

