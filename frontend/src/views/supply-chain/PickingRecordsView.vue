<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { History, RotateCcw, Search } from '@lucide/vue'
import EmptyState from '../../components/common/EmptyState.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { fetchPickingRecords } from '../../api/operationalClosure'
import { formatStatusText } from '../../utils/chineseDisplay'
import { useNotify } from '../../composables/useNotify'

const notify = useNotify()


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
    notify.warning('开始日期不能晚于结束日期')
    return
  }
  loading.value = true
  notify.success('')
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
    <MdPageHeader
      crumb="供应链业务"
      title="拣配记录"
      description="集中查询中心库拣配出库及科室签收记录"
      :loading="loading"
      @refresh="loadRecords"
    />

    <StatusMessage :message="message" tone="error" />

    <section class="record-workspace" aria-label="拣配记录查询与列表">
      <form class="hospital-query-grid record-query-grid" role="search" @submit.prevent="search">
        <label><span>配送单号</span><input v-model.trim="query.deliveryNo" class="md-input" placeholder="请输入配送单号" /></label>
        <label><span>申领单号</span><input v-model.trim="query.requisitionNo" class="md-input" placeholder="请输入申领单号" /></label>
        <label><span>科室</span><input v-model.trim="query.deptName" class="md-input" placeholder="请输入科室名称" /></label>
        <label><span>库房</span><input v-model.trim="query.warehouseName" class="md-input" placeholder="请输入库房名称" /></label>
        <label><span>商品</span><input v-model.trim="query.productKeyword" class="md-input" placeholder="商品编码 / 名称" /></label>
        <label><span>定数包码</span><input v-model.trim="query.labelNo" class="md-input" placeholder="请输入定数包码" /></label>
        <label>
          <span>状态</span>
          <select v-model="query.status" class="md-select">
            <option value="">全部</option>
            <option value="picked">待签收</option>
            <option value="signed">已签收</option>
          </select>
        </label>
        <label><span>开始日期</span><input v-model="query.dateFrom" class="md-input" type="date" /></label>
        <label><span>结束日期</span><input v-model="query.dateTo" class="md-input" type="date" /></label>
        <div class="record-query-actions">
          <button class="btn btn-primary md-btn md-btn-primary" type="submit" :disabled="loading"><Search :size="16" />查询</button>
          <button class="btn md-btn" type="button" :disabled="loading" @click="reset"><RotateCcw :size="16" />重置</button>
        </div>
      </form>

      <div class="record-table-title">
        <History :size="19" />
        <h3>拣配记录列表</h3>
        <span>共 {{ total }} 条</span>
      </div>

      <div class="table-scroll record-table-viewport">
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
/* =====================================================================
   拣配记录 picking-record-page
   ---------------------------------------------------------------------
   视觉沿用采购管理 / 收货验收 / 库存管理的 md-* 设计语言：
     1. 页头用 MdPageHeader（渐变底 + 白色标题）
     2. 查询区与表格合并为单张工作区卡片，避免两层卡片割裂
     3. 表格 md 密度：表头 12px、表体 13px、sticky 表头
   ===================================================================== */
.picking-record-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  max-width: none;
  padding: 16px 20px 24px;
  container-type: inline-size;
}

.picking-record-page > .md-header {
  border-radius: var(--spd-radius-md, 10px);
}

/* ---------- 工作区卡片：查询 + 列表 ---------- */
.record-workspace {
  box-sizing: border-box;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  min-height: 420px;
  padding: 16px 18px 18px;
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--md-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
}

/* ---------- 查询区 ---------- */
.record-query-grid {
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px 14px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--md-border-subtle, var(--spd-border-subtle));
}

.record-query-grid label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.record-query-grid label > span {
  color: var(--md-text-secondary, var(--spd-text-secondary));
  font-size: 12px;
  font-weight: 600;
}

.record-query-grid :is(input, select) {
  box-sizing: border-box;
  width: 100%;
  min-height: 32px;
  height: 32px;
  padding: 0 11px;
  color: var(--md-text, var(--spd-text-primary));
  background: var(--md-bg-card, #fff);
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
  font: inherit;
  font-size: 13px;
  outline: none;
}

.record-query-grid :is(input, select):focus {
  border-color: var(--md-primary, var(--primary));
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--md-primary, var(--primary)) 14%, transparent);
}

.record-query-actions {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  gap: 8px;
}

.record-query-actions .btn {
  min-height: 32px;
  padding: 0 14px;
  font-size: 13px;
  border-radius: var(--spd-radius-sm, 6px);
}

/* ---------- 列表标题 ---------- */
.record-table-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: var(--md-primary, var(--primary));
}

.record-table-title h3 {
  margin: 0;
  color: var(--md-text-heading, var(--spd-text-heading));
  font-size: 15px;
  font-weight: 600;
}

.record-table-title > span {
  margin-left: auto;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

/* ---------- 表格：md 密度 + sticky ---------- */
.record-table-viewport {
  flex: 1;
  min-height: 240px;
  overflow: auto;
  border: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
  border-radius: var(--spd-radius-sm, 6px);
}

.record-table {
  width: 100%;
  min-width: 1280px;
  border-collapse: separate;
  border-spacing: 0;
}

.record-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 36px;
  padding: 9px 12px !important;
  background: var(--md-bg-subtle, #f6f8fb) !important;
  color: var(--md-text-secondary, var(--spd-text-secondary)) !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  white-space: nowrap;
  border-bottom: 1px solid var(--md-border, var(--spd-border-subtle)) !important;
}

.record-table tbody td {
  height: 40px;
  padding: 9px 12px !important;
  color: var(--md-text, var(--spd-text-primary)) !important;
  font-size: 13px !important;
  vertical-align: middle;
  background: var(--md-bg-card, #fff) !important;
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle)) !important;
}

.record-table tbody tr:nth-child(even) td {
  background: color-mix(in srgb, var(--md-bg-subtle, #f6f8fb) 46%, #ffffff) !important;
}

.record-table tbody tr:hover td {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 6%, #ffffff) !important;
}

.record-table td strong,
.record-table td small {
  display: block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.record-table td small {
  margin-top: 3px;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: 12px;
}

.record-table .primary-cell {
  color: var(--md-primary, var(--primary));
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.record-table .numeric-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.record-table .label-cell {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.record-empty {
  height: 180px !important;
  color: var(--md-text-muted, var(--spd-text-tertiary)) !important;
  text-align: center !important;
}

:deep(.hospital-pagination) {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--md-border-subtle, var(--spd-border-subtle));
}

/* ---------- 响应式 ---------- */
@container (max-width: 900px) {
  .record-query-grid {
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  }
}

@container (max-width: 620px) {
  .record-query-actions {
    grid-column: 1 / -1;
    justify-content: stretch;
  }

  .record-query-actions .btn {
    flex: 1;
  }
}

@media (max-width: 760px) {
  .picking-record-page {
    padding: 12px 12px 20px;
  }

  .record-workspace {
    padding: 14px;
  }
}
</style>
