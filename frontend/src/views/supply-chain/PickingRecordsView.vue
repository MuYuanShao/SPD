<script setup lang="ts">
import { onMounted, reactive, ref, watch, computed } from 'vue'
import { History, RotateCcw, Search } from '@lucide/vue'
import EmptyState from '../../components/common/EmptyState.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { fetchPickingRecords, signDelivery } from '../../api/operationalClosure'
import { formatStatusText } from '../../utils/chineseDisplay'
import { formatDateTime } from '../../utils/dateTime'
import { useNotify } from '../../composables/useNotify'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'

import {
  Columns3,
  ChevronDown
} from '@lucide/vue'
import { useAuthStore } from '../../stores/auth'
import { useFillHeightTable } from '../../composables/useFillHeightTable'

const notify = useNotify()
const auth = useAuthStore()


const loading = ref(false)
const message = ref('')
const signingNo = ref('')
type PickingRecord = Record<string, unknown> & { labelCodes: string[] }

const rows = ref<PickingRecord[]>([])
const labelDetailOpen = ref(false)
const labelDetail = ref<PickingRecord>()
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
  dateType: 'picking',
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
  message.value = ''
  notify.success('')
  try {
    const result = await fetchPickingRecords({
      ...normalizedParams(),
      page: String(page.value),
      size: String(size.value)
    })
    rows.value = result.rows.map(row => ({
      ...row,
      labelCodes: Array.isArray(row.labelCodes)
        ? row.labelCodes.filter((code): code is string => typeof code === 'string' && Boolean(code))
        : typeof row.labelNos === 'string' ? row.labelNos.split(',').map(code => code.trim()).filter(Boolean) : []
    }))
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
    dateType: 'picking',
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

function showLabelDetail(row: PickingRecord) {
  labelDetail.value = row
  labelDetailOpen.value = true
}

async function confirmReceipt(row: PickingRecord) {
  const deliveryNo = String(row.bizNo || '')
  if (!deliveryNo || row.status !== 'picked' || signingNo.value || !auth.canWrite('picking-delivery')) return

  signingNo.value = deliveryNo
  try {
    const result = await signDelivery(deliveryNo)
    await loadRecords()
    notify.success(`配送已签收：${result.deliveryNo}`)
  } catch (error) {
    notify.error(error instanceof Error ? error.message : '配送签收失败，请重试')
  } finally {
    signingNo.value = ''
  }
}

onMounted(loadRecords)

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const PICKINGRECORDS_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'deliveryNo', label: '配送单号', width: 190, visible: true, weight: 5, minWidth: 140, maxWidth: 280 },
  { key: 'requisitionNo', label: '申领单号', width: 190, visible: true, weight: 5, minWidth: 140, maxWidth: 280 },
  { key: 'requisitionType', label: '申领类型', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190 },
  { key: 'requisitionCreateTime', label: '申领单创建时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'deptName', label: '科室', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 220 },
  { key: 'warehouseName', label: '出库库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 220 },
  { key: 'product', label: '商品信息', width: 200, visible: true, flexible: true, idealWidth: 250, weight: 6, minWidth: 140, maxWidth: 340 },
  { key: 'quantity', label: '数量', width: 100, visible: true, weight: 2, minWidth: 90, maxWidth: 150 },
  { key: 'packageCode', label: '定数包码', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 240 },
  { key: 'status', label: '状态', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'pickingTime', label: '配送单拣配时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'partialPickingTime', label: '部分拣配完成时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'pickingFinishTime', label: '拣配完成时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'signTime', label: '签收时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'actions', label: '操作', width: 170, visible: true, fill: true, minWidth: 140, maxWidth: 300 }
]

const pickingRecordsCols = useListColumns({
  storageKey: 'spd-picking-records-cols-v1',
  defaults: PICKINGRECORDS_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const pickingRecordsTableRef = pickingRecordsCols.tableRef
const fillHeightPickingRecords = useFillHeightTable({ tableRef: pickingRecordsTableRef })
const isPickingRecordsColVisible = (key: string) => pickingRecordsCols.isColumnVisible(key)
const pickingRecordsColBinding = (key: string) => pickingRecordsCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const pickingRecordsActionFix = useActionColumnFix()
const pickingRecordsActionFixed = computed(() => pickingRecordsActionFix.actionFixedBinding.value)

watch(
  [pickingRecordsTableRef],
  () => {
    pickingRecordsActionFix.observe(pickingRecordsTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const pickingRecordsColSettingsAnchorRef = ref<HTMLElement | null>(null)
const pickingRecordsColSettingsPopRef = ref<HTMLElement | null>(null)
const pickingRecordsColSettingsPop = useColSettingsPopover({
  open: pickingRecordsCols.settingsOpen,
  anchorRef: pickingRecordsColSettingsAnchorRef,
  popRef: pickingRecordsColSettingsPopRef
})

</script>

<template>
  <section class="picking-record-page spd-unified-list">
    <MdPageHeader
      crumb="供应链业务"
      title="拣配记录"
      :loading="loading"
      @refresh="loadRecords"
    />

    <StatusMessage :message="message" tone="error" />

    <section class="record-workspace" aria-label="拣配记录查询与列表">
      <SpdFilterBar class="record-query-grid" role="search" :loading="loading" @submit="search" @reset="reset">
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
        <label>
          <span>时间口径</span>
          <select v-model="query.dateType" class="md-select">
            <option value="picking">配送单拣配时间</option>
            <option value="approval">申领单审批通过时间</option>
          </select>
        </label>
        <label><span>开始日期</span><input v-model="query.dateFrom" class="md-input" type="date" /></label>
        <label><span>结束日期</span><input v-model="query.dateTo" class="md-input" type="date" /></label>
      </SpdFilterBar>

      <div class="record-table-title">
        <History :size="19" />
        <h3>拣配记录列表</h3>
        <span>共 {{ total }} 条</span>
      </div>

            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="pickingRecordsColSettingsAnchorRef"
           
           
            @click="pickingRecordsCols.settingsOpen.value = !pickingRecordsCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
v-if="pickingRecordsCols.settingsOpen.value"
ref="pickingRecordsColSettingsPopRef"
:class="{ 'is-align-right': pickingRecordsColSettingsPop.align.value === 'right', 'is-align-up': pickingRecordsColSettingsPop.vertical.value === 'up' }"
:style="pickingRecordsColSettingsPop.popStyle.value"
:columns="pickingRecordsCols.columns.value"
:visible-column-count="pickingRecordsCols.visibleColumnCount.value"
@toggle="pickingRecordsCols.setColumnVisible"
@move="pickingRecordsCols.moveColumn"
@reset="pickingRecordsCols.resetColumns()"
@confirm="pickingRecordsCols.settingsOpen.value = false"
          />
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="pickingRecordsTableRef"
            :data="rows"
            row-key="deliveryNo"
            border
            stripe
            class="ulc-table is-sticky"
            :max-height="fillHeightPickingRecords.maxHeight"
            style="width: 100%"
            :empty-text="'暂无拣配记录'"
            @header-dragend="(width: number, oldWidth: number, column: any) => pickingRecordsCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="deliveryNo"
              v-if="isPickingRecordsColVisible('deliveryNo')"
              label="配送单号"
              v-bind="pickingRecordsColBinding('deliveryNo')"
            >
              <template #header>
                <span class="ulc-th-text">配送单号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.bizNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="requisitionNo"
              v-if="isPickingRecordsColVisible('requisitionNo')"
              label="申领单号"
              v-bind="pickingRecordsColBinding('requisitionNo')"
            >
              <template #header>
                <span class="ulc-th-text">申领单号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.sourceNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="requisitionType"
              v-if="isPickingRecordsColVisible('requisitionType')"
              label="申领类型"
              v-bind="pickingRecordsColBinding('requisitionType')"
            >
              <template #header>
                <span class="ulc-th-text">申领类型</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.requisitionTypeLabel || row.requisitionType || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="requisitionCreateTime"
              v-if="isPickingRecordsColVisible('requisitionCreateTime')"
              label="申领单创建时间"
              v-bind="pickingRecordsColBinding('requisitionCreateTime')"
            >
              <template #header>
                <span class="ulc-th-text">申领单创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatDateTime(row.requisitionCreatedAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="deptName"
              v-if="isPickingRecordsColVisible('deptName')"
              label="科室"
              v-bind="pickingRecordsColBinding('deptName')"
            >
              <template #header>
                <span class="ulc-th-text">科室</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.deptName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="warehouseName"
              v-if="isPickingRecordsColVisible('warehouseName')"
              label="出库库房"
              v-bind="pickingRecordsColBinding('warehouseName')"
            >
              <template #header>
                <span class="ulc-th-text">出库库房</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.warehouseName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="product"
              v-if="isPickingRecordsColVisible('product')"
              label="商品信息"
              v-bind="pickingRecordsColBinding('product')"
            >
              <template #header>
                <span class="ulc-th-text">商品信息</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-cell-stack picking-product-cell">
                  <strong>{{ row.productName || '-' }}</strong>
                  <small>{{ row.productCode || '-' }}</small>
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="quantity"
              v-if="isPickingRecordsColVisible('quantity')"
              label="数量"
              v-bind="pickingRecordsColBinding('quantity')"
              align="right"
            >
              <template #header>
                <span class="ulc-th-text">数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ quantityText(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="packageCode"
              v-if="isPickingRecordsColVisible('packageCode')"
              label="定数包码"
              v-bind="pickingRecordsColBinding('packageCode')"
            >
              <template #header>
                <span class="ulc-th-text">定数包码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text"><SpdButton tone="primary" size="small" v-if="row.labelCodes.length" @click="showLabelDetail(row)">
          查看详情（{{ row.labelCodes.length }}）
        </SpdButton>
                <span v-else>-</span></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isPickingRecordsColVisible('status')"
              label="状态"
              v-bind="pickingRecordsColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <SpdStatusChip
                  :tone="row.status === 'signed' ? 'success' : 'warning'"
                  :label="formatStatusText(row.status)"
                />
              </template>
            </el-table-column>
            <el-table-column
              column-key="pickingTime"
              v-if="isPickingRecordsColVisible('pickingTime')"
              label="配送单拣配时间"
              v-bind="pickingRecordsColBinding('pickingTime')"
            >
              <template #header>
                <span class="ulc-th-text">配送单拣配时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.createTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="partialPickingTime"
              v-if="isPickingRecordsColVisible('partialPickingTime')"
              label="部分拣配完成时间"
              v-bind="pickingRecordsColBinding('partialPickingTime')"
            >
              <template #header>
                <span class="ulc-th-text">部分拣配完成时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatDateTime(row.partialPickingCompletedAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="pickingFinishTime"
              v-if="isPickingRecordsColVisible('pickingFinishTime')"
              label="拣配完成时间"
              v-bind="pickingRecordsColBinding('pickingFinishTime')"
            >
              <template #header>
                <span class="ulc-th-text">拣配完成时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ formatDateTime(row.pickingCompletedAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="signTime"
              v-if="isPickingRecordsColVisible('signTime')"
              label="签收时间"
              v-bind="pickingRecordsColBinding('signTime')"
            >
              <template #header>
                <span class="ulc-th-text">签收时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.finishTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isPickingRecordsColVisible('actions')"
              label="操作"
              v-bind="pickingRecordsColBinding('actions')"
              :fixed="pickingRecordsActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button v-if="row.status === 'picked'"
                  :disabled="loading || Boolean(signingNo)" @click="confirmReceipt(row)" class="ulc-action btn-sm" type="button">{{ signingNo === row.bizNo ? '签收中…' : '确认签收' }}</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无拣配记录" />
            </template>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="page"
            :total-pages="Math.max(1, Math.ceil(total / Math.max(size, 1)))"
            :total="total"
            :size="size"
            :loading="loading"
            @update:page="changePage"
            @update:size="changeSize"
          />
        </template>
      </MdDataTableCard>
    </section>

    <el-dialog v-model="labelDetailOpen" title="定数包码详情" width="min(560px, 92vw)" append-to-body destroy-on-close>
      <template v-if="labelDetail">
        <p class="label-detail-summary">配送单号 {{ labelDetail.bizNo || '-' }} · 共 {{ labelDetail.labelCodes.length }} 个</p>
        <div class="label-detail-scroll" role="region" aria-label="完整定数包码列表" tabindex="0">
          <ol class="label-detail-list">
            <li v-for="code in labelDetail.labelCodes" :key="code"><code>{{ code }}</code></li>
          </ol>
        </div>
      </template>
      <template #footer><SpdButton label="关闭" tone="neutral" @click="labelDetailOpen = false"></SpdButton></template>
    </el-dialog>
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

/* ---------- 数字列：居中 + 等宽数字 ----------
   与统一列表标准一致：金额 / 数量类单元格与普通列一样**居中**，
   数字使用等宽字体便于纵向比对。
   具体样式统一由 styles/list/unified-list.css 的 .ulc-number 提供，
   这里不再重复定义，避免各页面出现不同的对齐口径。 */

/* ---------- 商品信息列：名称 + 编码（两行堆叠，整体超宽才换行） ---------- */
.picking-product-cell {
  gap: 2px;
}

.picking-product-cell strong {
  font-weight: 600;
  line-height: 1.35;
}

.picking-product-cell small {
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-sm, 13px);
  font-weight: 400;
}

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
  background: var(--md-bg-card, var(--spd-p-bg));
  border: 1px solid var(--md-border, var(--spd-border-subtle));
  border-radius: var(--md-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
}

/* ---------- 查询区 ----------
   筛选区已统一改用 SpdFilterBar 组件，
   栅格列数 / 换行规则 / 控件高度 / 操作区位置全部由
   styles/components/spd-filter-bar.css 的「标准规则」统一控制，
   本页不再覆写列数与间距（避免各页各写一套换行规则）。 */

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
  font-size: var(--spd-font-size-lg);
  font-weight: 600;
}

.record-table-title > span {
  margin-left: auto;
  color: var(--md-text-muted, var(--spd-text-tertiary));
  font-size: var(--spd-font-size-sm);
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
  background: var(--md-bg-subtle, var(--spd-p-bg-subtle)) !important;
  color: var(--md-text-secondary, var(--spd-text-secondary)) !important;
  font-size: var(--spd-font-size-table-head) !important;
  font-weight: 600 !important;
  white-space: nowrap;
  border-bottom: 1px solid var(--md-border, var(--spd-border-subtle)) !important;
}

.record-table tbody td {
  height: 40px;
  padding: 9px 12px !important;
  color: var(--md-text, var(--spd-text-primary)) !important;
  font-size: var(--spd-font-size-base) !important;
  vertical-align: middle;
  background: var(--md-bg-card, var(--spd-p-bg)) !important;
  border-bottom: 1px solid var(--md-border-subtle, var(--spd-border-subtle)) !important;
}

.record-table tbody tr:nth-child(even) td {
  background: color-mix(in srgb, var(--md-bg-subtle, var(--spd-p-bg-subtle)) 46%, var(--spd-p-bg)) !important;
}

.record-table tbody tr:hover td {
  background: color-mix(in srgb, var(--md-primary, var(--primary)) 6%, var(--spd-p-bg)) !important;
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
  font-size: var(--spd-font-size-sm);
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

.label-detail-summary {
  margin: 0 0 10px;
  color: var(--spd-text-secondary);
}

.label-detail-scroll {
  max-height: min(50vh, 420px);
  overflow: auto;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 8px);
}

.label-detail-list {
  margin: 0;
  padding: 6px 12px 6px 40px;
}

.label-detail-list li {
  padding: 6px 0;
  overflow-wrap: anywhere;
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
@media (max-width: 760px) {
  .picking-record-page {
    padding: 12px 12px 20px;
  }

  .record-workspace {
    padding: 14px;
  }
}
</style>
