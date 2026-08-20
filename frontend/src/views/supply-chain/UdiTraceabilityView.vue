<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { AlertTriangle, Boxes, CheckCircle2, RefreshCw, ScanLine, Search, ShieldCheck } from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import {
  consumeQuotaPackageByCode,
  fetchUdiTraceDetail,
  fetchUdiTraceOptions,
  fetchUdiTraceRecords,
  signQuotaPackageByCode,
  type UdiTraceDetail,
  type UdiTraceOption,
  type UdiTraceRecord,
  type UdiTraceSummary
} from '../../api/udiTraceability'
import { formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'

const loading = ref(false)
const detailLoading = ref(false)
const message = ref('')
const records = ref<UdiTraceRecord[]>([])
const selected = ref<UdiTraceDetail | null>(null)
const statusOptions = ref<UdiTraceOption[]>([])
const traceScopeOptions = ref<UdiTraceOption[]>([])
const eventTypeOptions = ref<UdiTraceOption[]>([])
const summary = ref<UdiTraceSummary>({
  totalCodes: 0,
  highValueCodes: 0,
  quotaPackageCodes: 0,
  exceptionCodes: 0,
  inStockCodes: 0,
  consumedCodes: 0
})
const page = ref(1)
const size = ref(20)
const total = ref(0)
const query = reactive({
  keyword: 'UDI20260706000158',
  traceScope: '',
  status: '',
  eventType: ''
})
const packageActionLoading = ref(false)
const packageAction = reactive({
  code: '',
  deptName: ''
})

const statusText: Record<string, string> = {
  in_stock: '在库',
  putaway: '已上架',
  requisitioned: '已申领',
  delivery_picked: '已拣配',
  patient_bound: '已绑定患者',
  delivered: '已配送',
  signed: '科室签收',
  consumed: '已消耗',
  settled: '已结算',
  isolated: '隔离'
}

const statusTone: Record<string, string> = {
  in_stock: 'blue',
  putaway: 'blue',
  delivered: 'amber',
  requisitioned: 'amber',
  delivery_picked: 'amber',
  patient_bound: 'amber',
  signed: 'amber',
  consumed: 'green',
  settled: 'green',
  isolated: 'red'
}

const scopeText: Record<string, string> = {
  high_value: '高值耗材',
  low_value_quota_pack: '低值定数包'
}

const eventText = computed(() =>
  eventTypeOptions.value.reduce<Record<string, string>>((acc, item) => {
    acc[item.value] = item.label
    return acc
  }, {})
)

const stats = computed(() => [
  { label: '追溯对象', value: summary.value.totalCodes ?? 0, icon: ScanLine, tone: 'teal' },
  { label: '高值耗材', value: summary.value.highValueCodes ?? 0, icon: ShieldCheck, tone: 'teal' },
  { label: '低值定数包', value: summary.value.quotaPackageCodes ?? 0, icon: Boxes, tone: 'blue' },
  { label: '异常 / 隔离', value: summary.value.exceptionCodes ?? 0, icon: AlertTriangle, tone: 'red' },
  { label: '已完成消耗', value: summary.value.consumedCodes ?? 0, icon: CheckCircle2, tone: 'green' }
])

const activeRecord = computed(() => selected.value?.record ?? records.value[0])
const timeline = computed(() => selected.value?.timeline ?? [])

function labelStatus(value?: string) {
  if (!value) return '-'
  return statusText[value] ?? formatStatusText(value)
}

function selectEventType(value: string) {
  query.eventType = query.eventType === value ? '' : value
  page.value = 1
  loadRecords()
}

async function loadOptions() {
  const options = await fetchUdiTraceOptions()
  statusOptions.value = options.statuses
  traceScopeOptions.value = options.traceScopes
  eventTypeOptions.value = options.eventTypes
}

async function loadRecords() {
  loading.value = true
  message.value = ''
  try {
    const data = await fetchUdiTraceRecords({
      keyword: query.keyword,
      traceScope: query.traceScope,
      status: query.status,
      eventType: query.eventType,
      page: String(page.value),
      size: String(size.value)
    })
    records.value = data.rows
    total.value = data.total
    summary.value = {
      ...summary.value,
      ...(data.summary as UdiTraceSummary | undefined)
    }
    if (records.value.length > 0) {
      await loadDetail(records.value[0].uniqueCode)
    } else {
      selected.value = null
    }
  } catch (error) {
    message.value = error instanceof Error ? error.message : '加载 UDI 追溯数据失败'
  } finally {
    loading.value = false
  }
}

async function loadDetail(code: string) {
  detailLoading.value = true
  try {
    selected.value = await fetchUdiTraceDetail(code)
  } finally {
    detailLoading.value = false
  }
}

function submitSearch() {
  page.value = 1
  loadRecords()
}

function resetSearch() {
  query.keyword = ''
  query.traceScope = ''
  query.status = ''
  query.eventType = ''
  page.value = 1
  loadRecords()
}

function changePage(nextPage: number) {
  page.value = nextPage
  loadRecords()
}

function changeSize(nextSize: number) {
  size.value = nextSize
  page.value = 1
  loadRecords()
}

async function signQuotaPackage() {
  if (!packageAction.code.trim()) {
    message.value = '请扫描或输入定数包唯一码'
    return
  }
  packageActionLoading.value = true
  try {
    const result = await signQuotaPackageByCode(packageAction.code.trim())
    query.keyword = packageAction.code.trim()
    await loadRecords()
    message.value = `定数包已签收入库：${result.deliveryNo}`
  } catch (error) {
    message.value = error instanceof Error ? error.message : '定数包签收入库失败'
  } finally {
    packageActionLoading.value = false
  }
}

async function consumeQuotaPackage() {
  if (!packageAction.code.trim()) {
    message.value = '请扫描或输入定数包唯一码'
    return
  }
  packageActionLoading.value = true
  try {
    const result = await consumeQuotaPackageByCode({
      code: packageAction.code.trim(),
      deptName: packageAction.deptName.trim() || undefined
    })
    query.keyword = packageAction.code.trim()
    await loadRecords()
    message.value = `定数包已消耗并进入结算：${result.consumptionNo}，金额 ¥${Number(result.amount).toFixed(2)}`
  } catch (error) {
    message.value = error instanceof Error ? error.message : '定数包扫码消耗失败'
  } finally {
    packageActionLoading.value = false
  }
}

onMounted(async () => {
  await loadOptions()
  await loadRecords()
})
</script>

<template>
  <main class="udi-page">
    <section class="udi-header">
      <div>
        <p class="eyebrow">专项与合规</p>
        <h1>UDI / 唯一码追溯</h1>
        <p class="subtitle">高值耗材以手麻/手术系统计费回传作为库存扣减和追溯触发点，低值定数包按包码、标签码追溯。</p>
      </div>
      <form class="scan-bar" @submit.prevent="submitSearch">
        <ScanLine :size="20" />
        <input v-model.trim="query.keyword" type="search" placeholder="扫描或输入 UDI / 唯一码 / 定数包码 / 批号 / 商品" />
        <button class="icon-btn primary" type="submit" title="查询">
          <Search :size="18" />
        </button>
        <button class="icon-btn" type="button" title="重置" @click="resetSearch">
          <RefreshCw :size="18" />
        </button>
      </form>
    </section>

    <section class="udi-filters">
      <select v-model="query.traceScope" @change="submitSearch">
        <option value="">全部对象</option>
        <option v-for="item in traceScopeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <select v-model="query.status" @change="submitSearch">
        <option value="">全部状态</option>
        <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <div class="event-tabs" aria-label="追溯事件筛选">
        <button type="button" :class="{ active: query.eventType === '' }" @click="selectEventType('')">全部</button>
        <button
          v-for="item in eventTypeOptions"
          :key="item.value"
          type="button"
          :class="{ active: query.eventType === item.value }"
          @click="selectEventType(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
    </section>

    <section class="quota-scan-actions">
      <div>
        <strong>低值定数包扫码作业</strong>
        <span>支持扫描 UDI、唯一码或定数包标签码，签收入库后可整包消耗并进入结算。</span>
      </div>
      <input v-model.trim="packageAction.code" placeholder="扫描 UDI / 唯一码 / 定数包码" @keyup.enter="signQuotaPackage" />
      <input v-model.trim="packageAction.deptName" placeholder="消耗科室（可自动识别）" />
      <button class="text-btn" type="button" :disabled="packageActionLoading" @click="signQuotaPackage">签收入库</button>
      <button class="icon-btn primary quota-consume-btn" type="button" :disabled="packageActionLoading" @click="consumeQuotaPackage">
        扫码消耗
      </button>
    </section>

    <p v-if="message" class="form-message">{{ message }}</p>

    <section class="stats-grid">
      <article v-for="item in stats" :key="item.label" class="stat-tile" :class="`tone-${item.tone}`">
        <component :is="item.icon" :size="22" />
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <section class="trace-layout">
      <div class="trace-main">
        <header class="panel-header">
          <div>
            <h2>追溯时间线</h2>
            <p>{{ activeRecord ? `${scopeText[activeRecord.traceScope] || '追溯对象'} · ${activeRecord.uniqueCode}` : '暂无追溯对象' }}</p>
          </div>
          <span v-if="activeRecord" class="status-pill" :class="`tone-${statusTone[activeRecord.currentStatus] || 'gray'}`">
            {{ labelStatus(activeRecord.currentStatus) }}
          </span>
        </header>
        <ol v-if="timeline.length" class="timeline" :class="{ loading: detailLoading }">
          <li v-for="event in timeline" :key="event.traceEventId">
            <span class="timeline-dot"><ShieldCheck :size="14" /></span>
            <div>
              <strong>{{ event.eventName || eventText[event.eventType] }}</strong>
              <p>{{ event.locationName || '-' }} · {{ event.operatorName || '-' }}</p>
              <small>{{ event.eventTime }} · {{ event.bizNo || '-' }}</small>
              <em v-if="event.remark">{{ formatRemarkText(event.remark) }}</em>
            </div>
          </li>
        </ol>
        <div v-else class="empty-state">暂无追溯节点</div>
      </div>

      <aside class="trace-detail">
        <header class="panel-header compact">
          <h2>单件详情</h2>
          <span v-if="detailLoading">加载中</span>
        </header>
        <dl v-if="activeRecord" class="detail-list">
          <dt>对象类型</dt>
          <dd>{{ scopeText[activeRecord.traceScope] || '-' }}</dd>
          <dt>商品</dt>
          <dd>{{ activeRecord.productName }}</dd>
          <dt>UDI</dt>
          <dd>{{ activeRecord.udiCode }}</dd>
          <dt>唯一码</dt>
          <dd>{{ activeRecord.uniqueCode }}</dd>
          <dt v-if="activeRecord.traceScope === 'low_value_quota_pack'">定数包码</dt>
          <dd v-if="activeRecord.traceScope === 'low_value_quota_pack'">{{ activeRecord.packageLabelNo || '-' }}</dd>
          <dt v-if="activeRecord.traceScope === 'low_value_quota_pack'">定数模板</dt>
          <dd v-if="activeRecord.traceScope === 'low_value_quota_pack'">{{ activeRecord.templateName || '-' }} {{ activeRecord.templateCode || '' }}</dd>
          <dt v-if="activeRecord.traceScope === 'low_value_quota_pack'">包内数量</dt>
          <dd v-if="activeRecord.traceScope === 'low_value_quota_pack'">{{ activeRecord.packageQuantity || '-' }} {{ activeRecord.packageUnit || '' }}</dd>
          <dt>系统批次</dt>
          <dd>{{ activeRecord.batchNo || '-' }}</dd>
          <dt>当前库位</dt>
          <dd>{{ activeRecord.currentLocation || '-' }}</dd>
          <dt>当前科室</dt>
          <dd>{{ activeRecord.currentDepartment || '-' }}</dd>
          <dt>有效期</dt>
          <dd>{{ activeRecord.expireDate || '-' }}</dd>
          <dt>厂家 / 供应商</dt>
          <dd>{{ activeRecord.manufacturerName || '-' }} / {{ activeRecord.supplierName || '-' }}</dd>
          <dt>{{ activeRecord.traceScope === 'high_value' ? '患者' : '包状态' }}</dt>
          <dd v-if="activeRecord.traceScope === 'high_value'">{{ activeRecord.patientNo || '-' }} {{ activeRecord.patientNameMasked || '' }}</dd>
          <dd v-else>{{ formatStatusText(activeRecord.packageStatus) }}</dd>
        </dl>
        <div v-else class="empty-state">请选择追溯记录</div>
      </aside>
    </section>

    <section class="record-panel">
      <header class="panel-header compact">
        <h2>唯一码记录</h2>
        <span>{{ total }} 条</span>
      </header>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>唯一码</th>
              <th>对象类型</th>
              <th>商品</th>
              <th>批次</th>
              <th>状态</th>
              <th>当前位置</th>
              <th>最近节点</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in records" :key="row.uniqueCode" :class="{ selected: row.uniqueCode === activeRecord?.uniqueCode }">
              <td>
                <strong>{{ row.uniqueCode }}</strong>
                <small>{{ row.udiCode }}</small>
              </td>
              <td>
                <span class="scope-pill">{{ scopeText[row.traceScope] || '-' }}</span>
                <small v-if="row.packageLabelNo">{{ row.packageLabelNo }}</small>
              </td>
              <td>
                {{ row.productName }}
                <small>{{ row.productCode }} · {{ row.specModel || '-' }}</small>
                <small v-if="row.templateName">{{ row.templateName }} · {{ row.packageQuantity || '-' }}{{ row.packageUnit || '' }}</small>
              </td>
              <td>{{ row.batchNo || '-' }}</td>
              <td>
                <span class="status-pill" :class="`tone-${statusTone[row.currentStatus] || 'gray'}`">
                  {{ labelStatus(row.currentStatus) }}
                </span>
              </td>
              <td>{{ row.currentLocation || '-' }}</td>
              <td>
                {{ row.lastEventName || '-' }}
                <small>{{ row.lastEventTime || '-' }}</small>
              </td>
              <td>
                <button class="text-btn" type="button" @click="loadDetail(row.uniqueCode)">追溯</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls :page="page" :size="size" :total="total" :loading="loading" @change-page="changePage" @change-size="changeSize" />
    </section>
  </main>
</template>

<style scoped>
.udi-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  color: #1f2a37;
}

.udi-header {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(360px, 560px);
  gap: 20px;
  align-items: end;
}

.eyebrow {
  margin: 0 0 6px;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  font-size: 28px;
  line-height: 1.25;
}

h2 {
  font-size: 18px;
}

.subtitle {
  margin-top: 8px;
  max-width: 760px;
  color: #607083;
  line-height: 1.6;
}

.scan-bar {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 40px 40px;
  gap: 8px;
  align-items: center;
  min-height: 52px;
  padding: 0 10px 0 14px;
  border: 1px solid #d7e2ea;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(15, 118, 110, 0.08);
}

.scan-bar input,
.udi-filters select {
  min-width: 0;
  border: 0;
  outline: 0;
  color: #1f2a37;
  font: inherit;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 36px;
  border: 1px solid #d7e2ea;
  border-radius: 8px;
  background: #fff;
  color: #405467;
  cursor: pointer;
}

.icon-btn.primary {
  border-color: #0f766e;
  background: #0f766e;
  color: #fff;
}

.udi-filters {
  display: grid;
  grid-template-columns: 160px 160px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.udi-filters select {
  height: 40px;
  padding: 0 12px;
  border: 1px solid #d7e2ea;
  border-radius: 8px;
  background: #fff;
}

.event-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.event-tabs button,
.text-btn {
  min-height: 36px;
  border: 1px solid #d7e2ea;
  border-radius: 8px;
  background: #fff;
  color: #405467;
  font: inherit;
  cursor: pointer;
  white-space: nowrap;
}

.event-tabs button {
  padding: 0 12px;
}

.event-tabs button.active {
  border-color: #0f766e;
  background: #e6f5f3;
  color: #0f766e;
  font-weight: 700;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(140px, 1fr));
  gap: 12px;
}

.stat-tile,
.trace-main,
.trace-detail,
.record-panel {
  border: 1px solid #e1e8ee;
  border-radius: 8px;
  background: #fff;
}

.stat-tile {
  display: grid;
  grid-template-columns: 32px 1fr;
  grid-template-rows: auto auto;
  gap: 4px 10px;
  align-items: center;
  min-height: 92px;
  padding: 16px;
}

.stat-tile strong {
  grid-column: 2;
  font-size: 26px;
}

.stat-tile span {
  color: #607083;
}

.trace-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
}

.trace-main,
.trace-detail,
.record-panel {
  padding: 20px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.panel-header p,
.panel-header span {
  color: #607083;
  font-size: 13px;
}

.panel-header.compact {
  align-items: center;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.scope-pill {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  border-radius: 8px;
  background: #eef8f7;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.tone-teal,
.tone-green {
  color: #0f766e;
}

.tone-blue {
  color: #2563eb;
}

.tone-amber {
  color: #b45309;
}

.tone-red {
  color: #b91c1c;
}

.status-pill.tone-blue {
  background: #eff6ff;
}

.status-pill.tone-amber {
  background: #fff7ed;
}

.status-pill.tone-green {
  background: #ecfdf5;
}

.status-pill.tone-red {
  background: #fef2f2;
}

.status-pill.tone-gray {
  background: #f3f4f6;
  color: #4b5563;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
  opacity: 1;
}

.timeline.loading {
  opacity: 0.55;
}

.timeline li {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  min-height: 86px;
  position: relative;
}

.timeline li:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 32px;
  bottom: 0;
  left: 15px;
  width: 2px;
  background: #d7e2ea;
}

.timeline-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e6f5f3;
  color: #0f766e;
  z-index: 1;
}

.timeline strong {
  display: block;
  margin-bottom: 4px;
}

.timeline p,
.timeline small,
.timeline em {
  display: block;
  color: #607083;
  font-style: normal;
  line-height: 1.5;
}

.detail-list {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0;
}

.detail-list dt {
  color: #607083;
}

.detail-list dd {
  margin: 0;
  min-width: 0;
  overflow-wrap: anywhere;
  font-weight: 600;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 1100px;
  border-collapse: collapse;
}

th,
td {
  padding: 13px 12px;
  border-bottom: 1px solid #e8eef3;
  text-align: left;
  vertical-align: middle;
}

th {
  background: #f6f8fb;
  color: #607083;
  font-weight: 700;
}

td small {
  display: block;
  margin-top: 4px;
  color: #607083;
}

tr.selected {
  background: #f0fdfa;
}

.text-btn {
  padding: 0 12px;
  color: #0f766e;
  font-weight: 700;
}

.empty-state {
  display: grid;
  place-items: center;
  min-height: 160px;
  color: #607083;
  background: #f8fafc;
  border-radius: 8px;
}

.form-message {
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fff1f2;
  color: #b91c1c;
}

@media (max-width: 1180px) {
  .udi-header,
  .trace-layout {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(150px, 1fr));
  }
}

@media (max-width: 720px) {
  .udi-page {
    padding: 16px;
  }

  .udi-filters,
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .scan-bar {
    grid-template-columns: 24px minmax(0, 1fr) 40px;
  }

  .scan-bar .icon-btn:last-child {
    display: none;
  }
}
.quota-scan-actions {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(220px, 1fr) minmax(180px, 0.7fr) auto auto;
  gap: 10px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #cfe3df;
  border-radius: 8px;
  background: #f7fbfa;
}

.quota-scan-actions > div {
  display: grid;
  gap: 4px;
}

.quota-scan-actions span {
  color: #607083;
  font-size: 12px;
}

.quota-scan-actions input {
  min-width: 0;
  height: 38px;
  padding: 0 10px;
  border: 1px solid #d7e2ea;
  border-radius: 6px;
  background: #fff;
}

.quota-consume-btn {
  width: auto;
  min-width: 92px;
  padding: 0 14px;
}

@media (max-width: 1100px) {
  .quota-scan-actions {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
