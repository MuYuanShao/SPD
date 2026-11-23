<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'
import { useAuthStore } from '../../stores/auth'
import { useNotify } from '../../composables/useNotify'
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
</script>

<template>
  <section class="return-page">
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
        <button class="md-btn md-btn-primary" type="submit" :disabled="!choiceReady || searching || submitting">查询可退库存</button>
      </form>
      <div class="table-scroll">
        <table class="master-table">
          <thead><tr><th>选择</th><th>商品</th><th>批号</th><th>库存码</th><th>可退数量</th><th>本次退货</th></tr></thead>
          <tbody>
            <tr v-for="row in candidates" :key="row.id">
              <td><input type="checkbox" :aria-label="`选择 ${row.code}`" :checked="row.id in selected"
                :disabled="submitting" @change="toggle(row, ($event.target as HTMLInputElement).checked)" /></td>
              <td>{{ row.productCode }} · {{ row.productName }}</td><td>{{ row.batchNo }}</td><td>{{ row.code }}</td>
              <td>{{ row.availableQuantity }}{{ row.packageQuantity ? ` 包（每包 ${row.packageQuantity}）` : '' }}</td>
              <td><input v-if="selectionType === 'batch' && row.id in selected" v-model.number="selected[row.id]"
                class="md-input return-quantity" type="number" min="1" :max="row.availableQuantity" :disabled="submitting" :aria-label="`${row.code} 退货数量`" />
                <span v-else>{{ selected[row.id] ?? '—' }}</span></td>
            </tr>
            <tr v-if="!candidates.length"><td colspan="6"><EmptyState :message="searching ? '正在查询可退库存...' : '暂无可退库存，请选择库房并查询'" /></td></tr>
          </tbody>
        </table>
      </div>
      <div class="return-actions">
        <button type="button" class="md-btn" :disabled="!candidates.length || submitting" @click="selectAll">批量选择查询结果</button>
        <label>退货原因<input v-model.trim="reason" class="md-input" maxlength="500" :disabled="submitting" /></label>
        <button type="button" class="md-btn md-btn-primary" :disabled="submitting || !selectedItems.length" @click="submit()">
          {{ kind === 'department' ? '提交退库预占' : '确认供应商退货' }}
        </button>
      </div>
    </section>
    <section class="hospital-catalog-panel">
      <h3>退货记录</h3>
      <div class="table-scroll">
        <table class="master-table">
          <thead><tr><th>退货单号</th><th>类型</th><th>来源库房</th><th>接收方</th><th>基本数量</th><th>状态</th><th>登记时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="record in records" :key="record.returnNo">
              <td>{{ record.returnNo }}</td><td>{{ record.kind === 'department' ? '科室退库' : '供应商退货' }}</td>
              <td>{{ record.sourceWarehouse }}</td><td>{{ record.destinationWarehouse || record.supplierName }}</td>
              <td>{{ record.quantity }}</td><td>{{ record.status === 'pending_confirm' ? '待拣配配送确认' : record.status === 'confirmed' ? '已完成' : '已取消' }}</td>
              <td>{{ record.createTime }}</td><td><button v-if="canWrite && record.status === 'pending_confirm'" class="md-btn" type="button"
                :disabled="submitting" @click="cancel(record)">取消退库</button></td>
            </tr>
            <tr v-if="!records.length"><td colspan="8"><EmptyState :message="loading ? '正在加载退货记录...' : '暂无退货记录'" /></td></tr>
          </tbody>
        </table>
      </div>
    </section>
    <el-dialog v-model="confirmationOpen" title="确认退货" class="stock-return-confirm-dialog"
      modal-class="stock-return-confirm-overlay" append-to-body destroy-on-close :close-on-click-modal="false">
      <p class="return-confirm-note">提交后将预占二级库库存，待拣配配送确认后转入中心库。</p>
      <div class="table-scroll return-confirm-table" role="region" aria-label="退货商品明细" tabindex="0">
        <table class="master-table">
          <thead>
            <tr><th>商品信息</th><th>批号 / 库存码</th><th>注册证</th><th>厂家</th><th>单位</th><th>退货数量</th><th>金额</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in confirmationRows" :key="row.id">
              <td>{{ row.productName }}<span class="return-confirm-detail">{{ row.productCode }}<template v-if="row.specModel"> · {{ row.specModel }}</template></span></td>
              <td>{{ row.batchNo || '—' }}<span v-if="row.code !== row.batchNo" class="return-confirm-detail">{{ row.code }}</span></td>
              <td>{{ row.registrationNo || '—' }}</td>
              <td>{{ row.manufacturerName || '—' }}</td>
              <td>{{ row.unit || '—' }}</td>
              <td class="return-confirm-number">{{ row.baseQuantity }}<span v-if="row.packageQuantity" class="return-confirm-detail">{{ row.quantity }} 包</span></td>
              <td class="return-confirm-number">{{ row.amount == null ? '待核实' : money.format(row.amount / 100) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <dl class="return-confirm-totals">
        <div><dt>数量汇总</dt><dd>{{ quantitySummary }}</dd></div>
        <div><dt>金额汇总</dt><dd>{{ amountSummary }}</dd></div>
      </dl>
      <template #footer>
        <el-button @click="confirmationOpen = false">取消</el-button>
        <el-button type="primary" :disabled="submitting || !confirmationRows.length" @click="submit(true)">确认提交</el-button>
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
.return-confirm-detail { display: block; color: var(--el-text-color-secondary); font-size: 12px; }
.return-confirm-number { text-align: right; font-variant-numeric: tabular-nums; }
.return-confirm-totals { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px 24px; margin: 12px 0 0; }
.return-confirm-totals > div { display: flex; align-items: baseline; gap: 8px; }
.return-confirm-totals dd { margin: 0; font-weight: 600; font-variant-numeric: tabular-nums; }
</style>
