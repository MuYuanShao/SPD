<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { useAuthStore } from '../../stores/auth'
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
import {
  cancelWarehouseReturn, fetchStockReturnCandidates, fetchStockReturnOptions, fetchStockReturns, submitStockReturn,
  type ReturnCandidate, type ReturnRecord, type ReturnSelection
} from '../../api/stockReturns'

const auth = useAuthStore()
const notify = useNotify()
const kind = ref<'department' | 'supplier'>('department')
const selectionType = ref<ReturnSelection>('batch')
const sourceId = ref<number | ''>('')
const centerId = ref<number | ''>('')
const supplierId = ref<number | ''>('')
const batchNo = ref('')
const keyword = ref('')
const reason = ref('')
const options = ref<Awaited<ReturnType<typeof fetchStockReturnOptions>>>({ warehouses: [], centers: [], suppliers: [] })
const candidates = ref<ReturnCandidate[]>([])
const records = ref<ReturnRecord[]>([])
const selected = ref<Record<number, number>>({})
const loading = ref(false)
const searching = ref(false)
const submitting = ref(false)
const confirmationOpen = ref(false)
const error = ref('')
let searchVersion = 0
let retryRequest: { fingerprint: string; key: string } | undefined

const warehouses = computed(() => options.value.warehouses.filter(item => {
  const type = item.type || ''
  return kind.value === 'department' ? type.includes('二级') : /一级|中心|二级/.test(type)
}))
const centers = computed(() => {
  const campus = warehouses.value.find(item => item.id === sourceId.value)?.campus
  return options.value.centers.filter(item => campus && campus === item.campus)
})
const choiceReady = computed(() => Boolean(sourceId.value && (kind.value === 'supplier' ? supplierId.value : centerId.value)))
const selectedItems = computed(() => candidates.value.filter(row => row.id in selected.value))
const canWrite = computed(() => auth.canWrite('stock-return'))
const money = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' })
const confirmationRows = computed(() => selectedItems.value.map(row => {
  const quantity = Number(selected.value[row.id])
  // 批次价格保留四位小数，按明细金额四舍五入到分后再汇总。
  const priceInTenThousandths = row.unitPrice == null ? null : Math.round(Number(row.unitPrice) * 10000)
  const amount = priceInTenThousandths == null ? null : Math.round(priceInTenThousandths * quantity / 100)
  return { ...row, quantity, baseQuantity: quantity * (row.packageQuantity ?? 1), amount }
}))
const quantitySummary = computed(() => {
  const totals = new Map<string, number>()
  for (const row of confirmationRows.value) {
    const unit = row.unit || '（单位未维护）'
    totals.set(unit, (totals.get(unit) ?? 0) + row.baseQuantity)
  }
  return Array.from(totals, ([unit, quantity]) => `${quantity} ${unit}`).join('、')
})
const amountSummary = computed(() => confirmationRows.value.some(row => row.amount == null)
  ? '金额待核实'
  : money.format(confirmationRows.value.reduce((total, row) => total + (row.amount ?? 0), 0) / 100))

watch(kind, () => {
  sourceId.value = ''
  supplierId.value = ''
})
watch(centers, values => {
  if (!values.some(item => item.id === centerId.value)) centerId.value = values.length === 1 ? values[0].id : ''
})
watch([kind, sourceId, supplierId, selectionType], () => {
  searchVersion++
  searching.value = false
  candidates.value = []
  selected.value = {}
})

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    const [filterOptions, rows] = await Promise.all([fetchStockReturnOptions(), fetchStockReturns()])
    options.value = filterOptions
    records.value = rows
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '退货记录加载失败'
  } finally {
    loading.value = false
  }
}

async function search() {
  if (!choiceReady.value || !sourceId.value) return
  const version = ++searchVersion
  searching.value = true
  candidates.value = []
  selected.value = {}
  error.value = ''
  try {
    const result = await fetchStockReturnCandidates({
      warehouseId: sourceId.value, type: selectionType.value, batchNo: batchNo.value,
      keyword: keyword.value, supplierId: kind.value === 'supplier' ? supplierId.value || undefined : undefined
    })
    if (version === searchVersion) candidates.value = result
  } catch (cause) {
    if (version === searchVersion) error.value = cause instanceof Error ? cause.message : '可退库存查询失败'
  } finally {
    if (version === searchVersion) searching.value = false
  }
}

function toggle(row: ReturnCandidate, checked: boolean) {
  if (checked) selected.value[row.id] = 1
  else delete selected.value[row.id]
}

function selectAll() {
  selected.value = Object.fromEntries(candidates.value.map(row => [
    row.id, selectionType.value === 'batch' ? Number(row.availableQuantity) : 1
  ]))
}

async function submit(confirmed = false) {
  if (!sourceId.value || !choiceReady.value || !canWrite.value || submitting.value || !selectedItems.value.length) return
  const items = selectedItems.value.map(row => ({ id: row.id, quantity: Number(selected.value[row.id]) }))
  if (!reason.value.trim()) {
    notify.warning('请填写退货原因')
    return
  }
  if (items.some(item => !Number.isInteger(item.quantity) || item.quantity < 1
    || item.quantity > Number(candidates.value.find(row => row.id === item.id)?.availableQuantity ?? 0))) {
    notify.warning('退货数量须为正整数且不超过可退数量')
    return
  }
  const department = kind.value === 'department'
  if (department && !confirmed) {
    confirmationOpen.value = true
    return
  }
  submitting.value = true
  confirmationOpen.value = false
  try {
    if (!department) {
      try {
        await ElMessageBox.confirm('确认后将扣减选中库存并生成供应商退货记录。', '确认退货',
          { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' })
      } catch {
        return
      }
    }
    const request = {
      kind: kind.value, sourceWarehouseId: sourceId.value, destinationWarehouseId: centerId.value || undefined,
      supplierId: supplierId.value || undefined, selectionType: selectionType.value, reason: reason.value.trim(), items
    }
    const fingerprint = JSON.stringify(request)
    if (retryRequest?.fingerprint !== fingerprint) {
      const key = Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
      retryRequest = { fingerprint, key }
    }
    const result = await submitStockReturn({ ...request, requestKey: retryRequest.key })
    retryRequest = undefined
    candidates.value = []
    selected.value = {}
    reason.value = ''
    await refresh()
    notify.success(department ? `已预占库存，退库单 ${result.returnNo} 待确认` : `供应商退货已出库：${result.returnNo}`)
  } catch (cause) {
    notify.error(cause instanceof Error ? cause.message : '退货操作失败')
  } finally {
    submitting.value = false
  }
}

async function cancel(record: ReturnRecord) {
  if (submitting.value) return
  submitting.value = true
  try {
    try {
      await ElMessageBox.confirm(`确认取消退库单 ${record.returnNo} 并释放预占库存？`, '取消退库', { type: 'warning' })
    } catch {
      return
    }
    await cancelWarehouseReturn(record.returnNo)
    await refresh()
    notify.success('预占库存已释放')
  } catch (cause) {
    notify.error(cause instanceof Error ? cause.message : '取消退库失败')
  } finally {
    submitting.value = false
  }
}

onMounted(refresh)

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const STOCKRETURNCANDIDATES_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'selection', label: '选择', width: 70, visible: true, weight: 1, minWidth: 56, maxWidth: 90 },
  { key: 'product', label: '商品', width: 220, visible: true, flexible: true, idealWidth: 300, weight: 8, minWidth: 130, maxWidth: 460 },
  { key: 'batchNo', label: '批号', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 230 },
  { key: 'stockCode', label: '库存码', width: 170, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'returnableQty', label: '可退数量', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 230 },
  { key: 'returnQty', label: '本次退货', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190, fill: true }
]

const stockReturnCandidatesCols = useListColumns({
  storageKey: 'spd-stock-return-candidates-cols-v1',
  defaults: STOCKRETURNCANDIDATES_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const stockReturnCandidatesTableRef = stockReturnCandidatesCols.tableRef
const isStockReturnCandidatesColVisible = (key: string) => stockReturnCandidatesCols.isColumnVisible(key)
const stockReturnCandidatesColBinding = (key: string) => stockReturnCandidatesCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const stockReturnCandidatesColSettingsAnchorRef = ref<HTMLElement | null>(null)
const stockReturnCandidatesColSettingsPopRef = ref<HTMLElement | null>(null)
const stockReturnCandidatesColSettingsPop = useColSettingsPopover({
  open: stockReturnCandidatesCols.settingsOpen,
  anchorRef: stockReturnCandidatesColSettingsAnchorRef,
  popRef: stockReturnCandidatesColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const STOCKRETURNLIST_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'returnNo', label: '退货单号', width: 180, visible: true, weight: 4, minWidth: 140, maxWidth: 280 },
  { key: 'type', label: '类型', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'sourceWarehouse', label: '来源库房', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'receiver', label: '接收方', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 240 },
  { key: 'baseQty', label: '基本数量', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'status', label: '状态', width: 110, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'recordTime', label: '登记时间', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 200 },
  { key: 'actions', label: '操作', width: 170, visible: true, fill: true, minWidth: 140, maxWidth: 300 }
]

const stockReturnListCols = useListColumns({
  storageKey: 'spd-stock-return-list-cols-v1',
  defaults: STOCKRETURNLIST_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const stockReturnListTableRef = stockReturnListCols.tableRef
const isStockReturnListColVisible = (key: string) => stockReturnListCols.isColumnVisible(key)
const stockReturnListColBinding = (key: string) => stockReturnListCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const stockReturnListActionFix = useActionColumnFix()
const stockReturnListActionFixed = computed(() => stockReturnListActionFix.actionFixedBinding.value)

watch(
  [stockReturnListTableRef],
  () => {
    stockReturnListActionFix.observe(stockReturnListTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const stockReturnListColSettingsAnchorRef = ref<HTMLElement | null>(null)
const stockReturnListColSettingsPopRef = ref<HTMLElement | null>(null)
const stockReturnListColSettingsPop = useColSettingsPopover({
  open: stockReturnListCols.settingsOpen,
  anchorRef: stockReturnListColSettingsAnchorRef,
  popRef: stockReturnListColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 'hybrid'：按权重把容器宽度分配给所有列，表格 100% 铺满内容区；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const STOCKRETURNITEMS_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'product', label: '商品信息', width: 220, visible: true, flexible: true, idealWidth: 300, weight: 8, minWidth: 140, maxWidth: 460 },
  { key: 'batchStock', label: '批号 / 库存码', width: 170, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'registrationNo', label: '注册证', width: 170, visible: true, weight: 4, minWidth: 130, maxWidth: 260 },
  { key: 'manufacturer', label: '厂家', width: 160, visible: true, weight: 5, minWidth: 110, maxWidth: 280 },
  { key: 'unit', label: '单位', width: 90, visible: true, weight: 2, minWidth: 70, maxWidth: 130 },
  { key: 'returnQty', label: '退货数量', width: 110, visible: true, weight: 2, minWidth: 90, maxWidth: 160 },
  { key: 'amount', label: '金额', width: 130, visible: true, weight: 3, minWidth: 100, maxWidth: 190, fill: true }
]

const stockReturnItemsCols = useListColumns({
  storageKey: 'spd-stock-return-items-cols-v1',
  defaults: STOCKRETURNITEMS_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0
})

const stockReturnItemsTableRef = stockReturnItemsCols.tableRef
const isStockReturnItemsColVisible = (key: string) => stockReturnItemsCols.isColumnVisible(key)
const stockReturnItemsColBinding = (key: string) => stockReturnItemsCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const stockReturnItemsColSettingsAnchorRef = ref<HTMLElement | null>(null)
const stockReturnItemsColSettingsPopRef = ref<HTMLElement | null>(null)
const stockReturnItemsColSettingsPop = useColSettingsPopover({
  open: stockReturnItemsCols.settingsOpen,
  anchorRef: stockReturnItemsColSettingsAnchorRef,
  popRef: stockReturnItemsColSettingsPopRef
})

</script>

<template><section class="return-page spd-unified-list">
    <MdPageHeader crumb="供应链业务" title="退货管理" :loading="loading" @refresh="refresh" />
    <StatusMessage :message="error" tone="error" role="alert" />
    <section v-if="canWrite" class="hospital-catalog-panel">
      <h3>登记退货</h3>
      <form class="return-filters" aria-label="可退库存查询" @submit.prevent="search">
        <label>退货类型<select v-model="kind" class="md-select" :disabled="submitting">
          <option value="department">科室退库至中心库</option><option value="supplier">退货给供应商</option>
        </select></label>
        <label>来源库房<select v-model="sourceId" class="md-select" :disabled="submitting">
          <option value="">请选择</option><option v-for="item in warehouses" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select></label>
        <label v-if="kind === 'department'">接收中心库<select v-model="centerId" class="md-select" :disabled="submitting">
          <option value="">请选择同院区中心库</option><option v-for="item in centers" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select></label>
        <label v-else>退货供应商<select v-model="supplierId" class="md-select" :disabled="submitting">
          <option value="">请选择</option><option v-for="item in options.suppliers" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select></label>
        <label>库存类型<select v-model="selectionType" class="md-select" :disabled="submitting">
          <option value="batch">散货批次</option><option value="trace">高值唯一码</option>
          <option value="package">定数包码</option>
        </select></label>
        <label>批号<input v-model.trim="batchNo" class="md-input" placeholder="系统批号或生产批号" :disabled="submitting" /></label>
        <label>关键词<input v-model.trim="keyword" class="md-input" placeholder="商品或追溯码" :disabled="submitting" /></label>
        <SpdButton label="查询可退库存" tone="primary" type="submit" :disabled="!choiceReady || searching || submitting"></SpdButton>
      </form>
            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="stockReturnCandidatesColSettingsAnchorRef"
           
           
            @click="stockReturnCandidatesCols.settingsOpen.value = !stockReturnCandidatesCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <div
            v-if="stockReturnCandidatesCols.settingsOpen.value"
            ref="stockReturnCandidatesColSettingsPopRef"
            class="ulc-col-settings-pop"
            :class="{ 'is-align-right': stockReturnCandidatesColSettingsPop.align.value === 'right', 'is-align-up': stockReturnCandidatesColSettingsPop.vertical.value === 'up' }"
            :style="stockReturnCandidatesColSettingsPop.popStyle.value"
          >
            <header>
              <strong>列展示设置</strong>
              <SpdButton label="恢复默认" variant="text" tone="neutral" @click="stockReturnCandidatesCols.resetColumns()"></SpdButton>
            </header>
            <div class="ulc-col-settings-list">
              <label v-for="col in stockReturnCandidatesCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                <input
                  type="checkbox"
                  :checked="col.visible"
                  :disabled="col.visible && stockReturnCandidatesCols.visibleColumnCount.value <= 1"
                  @change="stockReturnCandidatesCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ col.label }}</span>
                <small>{{ col.width }}px</small>
              </label>
            </div>
            <footer>
              <SpdButton label="确定" tone="primary" @click="stockReturnCandidatesCols.settingsOpen.value = false"></SpdButton>
            </footer>
          </div>
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="stockReturnCandidatesTableRef"
            :data="candidates"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无可退库存'"
            @header-dragend="(width: number, oldWidth: number, column: any) => stockReturnCandidatesCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="selection"
              v-if="isStockReturnCandidatesColVisible('selection')"
              label="选择"
              v-bind="stockReturnCandidatesColBinding('selection')"
            >
              <template #header>
                <span class="ulc-th-text">选择</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text"><input type="checkbox" :aria-label="`选择 ${row.code}`" :checked="row.id in selected"
                :disabled="submitting" @change="toggle(row, ($event.target as HTMLInputElement).checked)" /></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="product"
              v-if="isStockReturnCandidatesColVisible('product')"
              label="商品"
              v-bind="stockReturnCandidatesColBinding('product')"
            >
              <template #header>
                <span class="ulc-th-text">商品</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-joined">
                  <span class="ulc-joined-part">{{ row.productCode }}</span>
                  <span class="ulc-joined-part">{{ row.productName }}</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="batchNo"
              v-if="isStockReturnCandidatesColVisible('batchNo')"
              label="批号"
              v-bind="stockReturnCandidatesColBinding('batchNo')"
            >
              <template #header>
                <span class="ulc-th-text">批号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.batchNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="stockCode"
              v-if="isStockReturnCandidatesColVisible('stockCode')"
              label="库存码"
              v-bind="stockReturnCandidatesColBinding('stockCode')"
            >
              <template #header>
                <span class="ulc-th-text">库存码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="returnableQty"
              v-if="isStockReturnCandidatesColVisible('returnableQty')"
              label="可退数量"
              v-bind="stockReturnCandidatesColBinding('returnableQty')"
            >
              <template #header>
                <span class="ulc-th-text">可退数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-joined">
                  <span class="ulc-joined-part ulc-number">{{ row.availableQuantity }}</span>
                  <span class="ulc-joined-part" v-if="row.packageQuantity">包（每包 {{ row.packageQuantity }}）</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="returnQty"
              v-if="isStockReturnCandidatesColVisible('returnQty')"
              label="本次退货"
              v-bind="stockReturnCandidatesColBinding('returnQty')"
            >
              <template #header>
                <span class="ulc-th-text">本次退货</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text"><input v-if="selectionType === 'batch' && row.id in selected" v-model.number="selected[row.id]"
                class="md-input return-quantity" type="number" min="1" :max="row.availableQuantity" :disabled="submitting" :aria-label="`${row.code} 退货数量`" />
                <span v-else>{{ selected[row.id] ?? '—' }}</span></span>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无可退库存" />
            </template>
          </el-table>
        </div>
      </MdDataTableCard>
      <div class="return-actions">
        <SpdButton label="批量选择查询结果" tone="primary" :disabled="!candidates.length || submitting" @click="selectAll"></SpdButton>
        <label>退货原因<input v-model.trim="reason" class="md-input" maxlength="500" :disabled="submitting" /></label>
        <SpdButton tone="primary" :disabled="submitting || !selectedItems.length" @click="submit()">
          {{ kind === 'department' ? '提交退库预占' : '确认供应商退货' }}
        </SpdButton>
      </div>
    </section>
    <section class="hospital-catalog-panel">
      <h3>退货记录</h3>
            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="stockReturnListColSettingsAnchorRef"
           
           
            @click="stockReturnListCols.settingsOpen.value = !stockReturnListCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <div
            v-if="stockReturnListCols.settingsOpen.value"
            ref="stockReturnListColSettingsPopRef"
            class="ulc-col-settings-pop"
            :class="{ 'is-align-right': stockReturnListColSettingsPop.align.value === 'right', 'is-align-up': stockReturnListColSettingsPop.vertical.value === 'up' }"
            :style="stockReturnListColSettingsPop.popStyle.value"
          >
            <header>
              <strong>列展示设置</strong>
              <SpdButton label="恢复默认" variant="text" tone="neutral" @click="stockReturnListCols.resetColumns()"></SpdButton>
            </header>
            <div class="ulc-col-settings-list">
              <label v-for="col in stockReturnListCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                <input
                  type="checkbox"
                  :checked="col.visible"
                  :disabled="col.visible && stockReturnListCols.visibleColumnCount.value <= 1"
                  @change="stockReturnListCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ col.label }}</span>
                <small>{{ col.width }}px</small>
              </label>
            </div>
            <footer>
              <SpdButton label="确定" tone="primary" @click="stockReturnListCols.settingsOpen.value = false"></SpdButton>
            </footer>
          </div>
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="stockReturnListTableRef"
            :data="records"
            row-key="returnNo"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无退货单'"
            @header-dragend="(width: number, oldWidth: number, column: any) => stockReturnListCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="returnNo"
              v-if="isStockReturnListColVisible('returnNo')"
              label="退货单号"
              v-bind="stockReturnListColBinding('returnNo')"
            >
              <template #header>
                <span class="ulc-th-text">退货单号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.returnNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="type"
              v-if="isStockReturnListColVisible('type')"
              label="类型"
              v-bind="stockReturnListColBinding('type')"
            >
              <template #header>
                <span class="ulc-th-text">类型</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.kind === 'department' ? '科室退库' : '供应商退货' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="sourceWarehouse"
              v-if="isStockReturnListColVisible('sourceWarehouse')"
              label="来源库房"
              v-bind="stockReturnListColBinding('sourceWarehouse')"
            >
              <template #header>
                <span class="ulc-th-text">来源库房</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.sourceWarehouse }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="receiver"
              v-if="isStockReturnListColVisible('receiver')"
              label="接收方"
              v-bind="stockReturnListColBinding('receiver')"
            >
              <template #header>
                <span class="ulc-th-text">接收方</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.destinationWarehouse || row.supplierName }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="baseQty"
              v-if="isStockReturnListColVisible('baseQty')"
              label="基本数量"
              v-bind="stockReturnListColBinding('baseQty')"
            >
              <template #header>
                <span class="ulc-th-text">基本数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.quantity }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isStockReturnListColVisible('status')"
              label="状态"
              v-bind="stockReturnListColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.status === 'pending_confirm' ? '待拣配配送确认' : row.status === 'confirmed' ? '已完成' : '已取消' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="recordTime"
              v-if="isStockReturnListColVisible('recordTime')"
              label="登记时间"
              v-bind="stockReturnListColBinding('recordTime')"
            >
              <template #header>
                <span class="ulc-th-text">登记时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.createTime }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"
              v-if="isStockReturnListColVisible('actions')"
              label="操作"
              v-bind="stockReturnListColBinding('actions')"
              :fixed="stockReturnListActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
              <div class="ulc-actions">
                <button v-if="canWrite && row.status === 'pending_confirm'"
                :disabled="submitting" @click="cancel(row)" class="ulc-action md-btn" type="button">取消退库</button>
              </div>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无退货单" />
            </template>
          </el-table>
        </div>
      </MdDataTableCard>
    </section>
    <el-dialog v-model="confirmationOpen" title="确认退货" class="stock-return-confirm-dialog"
      modal-class="stock-return-confirm-overlay" append-to-body destroy-on-close :close-on-click-modal="false">
      <p class="return-confirm-note">提交后将预占二级库库存，待拣配配送确认后转入中心库。</p>
            <MdDataTableCard>
        <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton
            ref="stockReturnItemsColSettingsAnchorRef"
            label="列设置"
            @click="stockReturnItemsCols.settingsOpen.value = !stockReturnItemsCols.settingsOpen.value">
            <template #icon><Columns3 :size="16" /></template>
          </SpdButton>
          <div
            v-if="stockReturnItemsCols.settingsOpen.value"
            ref="stockReturnItemsColSettingsPopRef"
            class="ulc-col-settings-pop"
            :class="{ 'is-align-right': stockReturnItemsColSettingsPop.align.value === 'right', 'is-align-up': stockReturnItemsColSettingsPop.vertical.value === 'up' }"
            :style="stockReturnItemsColSettingsPop.popStyle.value"
          >
            <header>
              <strong>列展示设置</strong>
              <SpdButton label="恢复默认" variant="text" tone="neutral" @click="stockReturnItemsCols.resetColumns()"></SpdButton>
            </header>
            <div class="ulc-col-settings-list">
              <label v-for="col in stockReturnItemsCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                <input
                  type="checkbox"
                  :checked="col.visible"
                  :disabled="col.visible && stockReturnItemsCols.visibleColumnCount.value <= 1"
                  @change="stockReturnItemsCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ col.label }}</span>
                <small>{{ col.width }}px</small>
              </label>
            </div>
            <footer>
              <SpdButton label="确定" tone="primary" @click="stockReturnItemsCols.settingsOpen.value = false"></SpdButton>
            </footer>
          </div>
        </div>
        </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="stockReturnItemsTableRef"
            :data="confirmationRows"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无退货明细'"
            @header-dragend="(width: number, oldWidth: number, column: any) => stockReturnItemsCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="product"
              v-if="isStockReturnItemsColVisible('product')"
              label="商品信息"
              v-bind="stockReturnItemsColBinding('product')"
            >
              <template #header>
                <span class="ulc-th-text">商品信息</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.productName }}<span class="return-confirm-detail">{{ row.productCode }}<template v-if="row.specModel"> · {{ row.specModel }}</template></span></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="batchStock"
              v-if="isStockReturnItemsColVisible('batchStock')"
              label="批号 / 库存码"
              v-bind="stockReturnItemsColBinding('batchStock')"
            >
              <template #header>
                <span class="ulc-th-text">批号 / 库存码</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.batchNo || '—' }}<span v-if="row.code !== row.batchNo" class="return-confirm-detail">{{ row.code }}</span></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="registrationNo"
              v-if="isStockReturnItemsColVisible('registrationNo')"
              label="注册证"
              v-bind="stockReturnItemsColBinding('registrationNo')"
            >
              <template #header>
                <span class="ulc-th-text">注册证</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.registrationNo || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="manufacturer"
              v-if="isStockReturnItemsColVisible('manufacturer')"
              label="厂家"
              v-bind="stockReturnItemsColBinding('manufacturer')"
            >
              <template #header>
                <span class="ulc-th-text">厂家</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.manufacturerName || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="unit"
              v-if="isStockReturnItemsColVisible('unit')"
              label="单位"
              v-bind="stockReturnItemsColBinding('unit')"
            >
              <template #header>
                <span class="ulc-th-text">单位</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.unit || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="returnQty"
              v-if="isStockReturnItemsColVisible('returnQty')"
              label="退货数量"
              v-bind="stockReturnItemsColBinding('returnQty')"
            >
              <template #header>
                <span class="ulc-th-text">退货数量</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.baseQuantity }}<span v-if="row.packageQuantity" class="return-confirm-detail">{{ row.quantity }} 包</span></span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="amount"
              v-if="isStockReturnItemsColVisible('amount')"
              label="金额"
              v-bind="stockReturnItemsColBinding('amount')"
            >
              <template #header>
                <span class="ulc-th-text">金额</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.amount == null ? '待核实' : money.format(row.amount / 100) }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="暂无退货明细" />
            </template>
          </el-table>
        </div>
      </MdDataTableCard>
      <dl class="return-confirm-totals">
        <div><dt>数量汇总</dt><dd>{{ quantitySummary }}</dd></div>
        <div><dt>金额汇总</dt><dd>{{ amountSummary }}</dd></div>
      </dl>
      <template #footer>
        <SpdButton label="取消" tone="neutral" @click="confirmationOpen = false"></SpdButton>
        <SpdButton label="确认提交" tone="primary" :disabled="submitting || !confirmationRows.length" @click="submit(true)"></SpdButton>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.return-page { display: grid; gap: 16px; padding: 16px; }
.hospital-catalog-panel { padding: 12px 16px; }
.return-filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(170px,100%),1fr)); gap: 10px; align-items: end; }
.return-filters label, .return-actions label { display: grid; gap: 4px; min-width: 0; }
.return-actions { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; margin-top: 12px; }
.return-quantity { width: 100px; }

/* ---------- 数字列：居中 + 等宽数字 ----------
   与统一列表标准一致：金额 / 数量类单元格与普通列一样**居中**，
   数字使用等宽字体便于纵向比对。
   具体样式统一由 styles/list/unified-list.css 的 .ulc-number 提供，
   这里不再重复定义，避免各页面出现不同的对齐口径。 */
:global(.stock-return-confirm-overlay .el-overlay-dialog) {
  display: grid;
  place-items: center;
  padding: 16px;
}
:global(.el-dialog.stock-return-confirm-dialog) {
  width: min(1080px, calc(100vw - 32px)) !important;
  max-width: calc(100vw - 32px) !important;
  max-height: calc(100dvh - 32px) !important;
  margin: 0 !important;
}
.return-confirm-note { margin: 0 0 12px; color: var(--el-text-color-secondary); }
.return-confirm-table { max-height: min(44vh, 420px); overflow: auto; }
.return-confirm-table table { min-width: 780px; }
.return-confirm-table th { position: sticky; top: 0; z-index: 1; background: var(--el-fill-color-light); }
.return-confirm-detail { display: block; color: var(--el-text-color-secondary); font-size: var(--spd-font-size-sm); }
.return-confirm-number { text-align: right; font-variant-numeric: tabular-nums; }
.return-confirm-totals { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px 24px; margin: 12px 0 0; }
.return-confirm-totals > div { display: flex; align-items: baseline; gap: 8px; }
.return-confirm-totals dd { margin: 0; font-weight: 600; font-variant-numeric: tabular-nums; }
</style>
