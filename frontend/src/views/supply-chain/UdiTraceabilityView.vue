<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RefreshCw, ScanLine, Search, ShieldCheck } from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import EmptyState from '../../components/common/EmptyState.vue'
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
  { label: '追溯对象', value: summary.value.totalCodes ?? 0 },
  { label: '高值耗材', value: summary.value.highValueCodes ?? 0 },
  { label: '低值定数包', value: summary.value.quotaPackageCodes ?? 0 },
  { label: '异常 / 隔离', value: summary.value.exceptionCodes ?? 0 },
  { label: '已完成消耗', value: summary.value.consumedCodes ?? 0 }
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
    <MdPageHeader
      crumb="专项与合规"
      title="UDI / 唯一码追溯"
      description="高值耗材以手麻/手术系统计费回传作为库存扣减和追溯触发点，低值定数包按包码、标签码追溯。"
      :loading="loading"
      @refresh="loadRecords"
    />

    <section class="udi-filters">
      <form class="scan-bar" @submit.prevent="submitSearch">
        <ScanLine :size="16" />
        <input v-model.trim="query.keyword" type="search" placeholder="扫描或输入 UDI / 唯一码 / 定数包码 / 批号 / 商品" />
        <button class="btn btn-sm udi-scan-btn" type="submit">
          <Search :size="15" />
          查询
        </button>
        <button class="btn btn-sm" type="button" @click="resetSearch">
          <RefreshCw :size="15" />
          重置
        </button>
      </form>
      <div class="udi-filter-fields">
        <label><span>对象类型</span>
          <select v-model="query.traceScope" @change="submitSearch">
            <option value="">全部对象</option>
            <option v-for="item in traceScopeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
        <label><span>当前状态</span>
          <select v-model="query.status" @change="submitSearch">
            <option value="">全部状态</option>
            <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
      </div>
    </section>

    <section class="udi-event-tabs" aria-label="追溯事件筛选">
      <button type="button" :class="{ active: query.eventType === '' }" @click="selectEventType('')">全部事件</button>
      <button
        v-for="item in eventTypeOptions"
        :key="item.value"
        type="button"
        :class="{ active: query.eventType === item.value }"
        @click="selectEventType(item.value)"
      >
        {{ item.label }}
      </button>
    </section>

    <section class="quota-scan-actions">
      <div class="quota-scan-intro">
        <strong>低值定数包扫码作业</strong>
        <span>支持扫描 UDI、唯一码或定数包标签码，签收入库后可整包消耗并进入结算。</span>
      </div>
      <label><span>扫描码</span>
        <input v-model.trim="packageAction.code" placeholder="UDI / 唯一码 / 定数包码" @keyup.enter="signQuotaPackage" />
      </label>
      <label><span>消耗科室</span>
        <input v-model.trim="packageAction.deptName" placeholder="可自动识别" />
      </label>
      <div class="quota-scan-btns">
        <button class="btn btn-sm" type="button" :disabled="packageActionLoading" @click="signQuotaPackage">签收入库</button>
        <button class="btn btn-sm btn-primary" type="button" :disabled="packageActionLoading" @click="consumeQuotaPackage">扫码消耗</button>
      </div>
    </section>

    <p v-if="message" class="form-message">{{ message }}</p>

    <section class="udi-summary" aria-label="追溯汇总">
      <div v-for="item in stats" :key="item.label" class="udi-summary-item">
        <span class="udi-summary-label">{{ item.label }}</span>
        <strong class="udi-summary-value">{{ item.value }}</strong>
      </div>
    </section>

    <section class="trace-layout">
      <div class="trace-main">
        <header class="udi-panel-header">
          <div>
            <h3>追溯时间线</h3>
            <p v-if="activeRecord">{{ scopeText[activeRecord.traceScope] || '追溯对象' }} · {{ activeRecord.uniqueCode }}</p>
            <p v-else>暂无追溯对象</p>
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
        <EmptyState v-else message="暂无追溯节点" />
      </div>

      <aside class="trace-detail">
        <header class="udi-panel-header compact">
          <h3>单件详情</h3>
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
        <EmptyState v-else message="请选择追溯记录" />
      </aside>
    </section>

    <section class="record-panel">
      <header class="udi-panel-header compact">
        <h3>唯一码记录</h3>
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
            <tr v-if="loading">
              <td colspan="8"><EmptyState message="正在加载追溯记录..." /></td>
            </tr>
            <tr v-else-if="message && !records.length">
              <td colspan="8"><EmptyState message="追溯记录加载失败" /></td>
            </tr>
            <template v-else>
              <tr
                v-for="row in records"
                :key="row.uniqueCode"
                :class="{ selected: row.uniqueCode === activeRecord?.uniqueCode }"
              >
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
                  <button class="btn btn-sm" type="button" @click="loadDetail(row.uniqueCode)">追溯</button>
                </td>
              </tr>
              <tr v-if="!loading && records.length === 0">
                <td colspan="8"><EmptyState message="暂无符合条件的追溯记录" /></td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <PaginationControls :page="page" :size="size" :total="total" :loading="loading" @change-page="changePage" @change-size="changeSize" />
    </section>
  </main>
</template>

<style scoped>
/* =====================================================================
   UDI / 唯一码追溯
   全部使用项目设计令牌（--spd-*），与科室申领、拣配配送等页面保持一致。
   ===================================================================== */
.udi-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  margin: 0 !important;
  padding: 16px 20px 24px;
  border: 0 !important;
  background: transparent;
  overflow: visible;
}

.udi-page h3,
.udi-page p {
  margin: 0;
}

/* ---------- 查询区 ---------- */
.udi-filters {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(0, 1.15fr);
  gap: 12px 16px;
  align-items: end;
  padding: 14px 16px;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

.scan-bar {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
  height: 38px;
  padding: 0 10px 0 12px;
  border: 1px solid var(--spd-border-default);
  border-radius: var(--spd-radius-sm, 6px);
  background: var(--spd-bg-container);
  color: var(--spd-text-tertiary);
}

.scan-bar:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--spd-primary-bg);
}

.scan-bar input {
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--spd-text-primary);
  font: inherit;
  font-size: 13px;
}

.scan-bar .btn {
  height: 30px;
  white-space: nowrap;
}

.udi-scan-btn {
  color: var(--primary) !important;
  border-color: color-mix(in srgb, var(--primary) 30%, transparent) !important;
  background: var(--spd-primary-bg) !important;
}

.udi-filter-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.udi-filters label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.udi-filters label > span,
.quota-scan-actions label > span {
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.udi-filters select,
.quota-scan-actions input {
  min-width: 0;
  height: 38px;
  padding: 0 10px;
  border: 1px solid var(--spd-border-default);
  border-radius: var(--spd-radius-sm, 6px);
  outline: 0;
  background: var(--spd-bg-container);
  color: var(--spd-text-primary);
  font: inherit;
  font-size: 13px;
}

.udi-filters select:focus,
.quota-scan-actions input:focus {
  border-color: var(--primary);
}

/* ---------- 事件筛选标签 ---------- */
.udi-event-tabs {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.udi-event-tabs button {
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid var(--spd-border-default);
  border-radius: 999px;
  background: var(--spd-bg-container);
  color: var(--spd-text-secondary);
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}

.udi-event-tabs button:hover {
  border-color: color-mix(in srgb, var(--primary) 30%, transparent);
  color: var(--primary);
}

.udi-event-tabs button.active {
  border-color: var(--primary);
  background: var(--spd-primary-bg);
  color: var(--primary);
  font-weight: 600;
}

/* ---------- 定数包扫码作业 ---------- */
.quota-scan-actions {
  display: grid;
  grid-template-columns: minmax(240px, 1.1fr) minmax(200px, 1fr) minmax(160px, 0.8fr) auto;
  gap: 12px;
  align-items: end;
  padding: 14px 16px;
  border: 1px solid var(--spd-border-subtle);
  border-left: 3px solid var(--primary);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

.quota-scan-intro {
  display: grid;
  gap: 4px;
  padding-bottom: 2px;
}

.quota-scan-intro strong {
  color: var(--spd-text-heading);
  font-size: 14px;
}

.quota-scan-intro span {
  color: var(--spd-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.quota-scan-actions label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.quota-scan-btns {
  display: flex;
  gap: 8px;
}

.quota-scan-btns .btn {
  height: 38px;
  white-space: nowrap;
}

.form-message {
  padding: 10px 12px;
  border: 1px solid var(--spd-border-default);
  border-radius: var(--spd-radius-sm, 6px);
  background: var(--spd-primary-bg);
  color: var(--spd-text-primary);
  font-size: 13px;
}

/* ---------- 汇总条（紧凑，非统计卡） ---------- */
.udi-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  padding: 8px 4px;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
}

.udi-summary-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 4px 18px;
  border-right: 1px solid var(--spd-border-subtle);
}

.udi-summary-item:last-child {
  border-right: 0;
}

.udi-summary-label {
  color: var(--spd-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.udi-summary-value {
  color: var(--spd-text-heading);
  font-size: 17px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* ---------- 面板通用 ---------- */
.trace-main,
.trace-detail,
.record-panel {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

.udi-panel-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.udi-panel-header.compact {
  align-items: center;
}

.udi-panel-header h3 {
  color: var(--spd-text-heading);
  font-size: 15px;
  font-weight: 600;
}

.udi-panel-header p,
.udi-panel-header > span {
  margin-top: 4px;
  color: var(--spd-text-secondary);
  font-size: 12px;
}

/* ---------- 时间线 ---------- */
.trace-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 14px;
  align-items: start;
}

.timeline {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  transition: opacity 0.15s ease;
}

.timeline.loading {
  opacity: 0.55;
}

.timeline li {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  position: relative;
  padding-bottom: 16px;
}

.timeline li:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 26px;
  bottom: 0;
  left: 13px;
  width: 2px;
  background: var(--spd-border-subtle);
}

.timeline li:last-child {
  padding-bottom: 0;
}

.timeline-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--spd-primary-bg);
  color: var(--primary);
  z-index: 1;
}

.timeline strong {
  display: block;
  padding-top: 4px;
  color: var(--spd-text-heading);
  font-size: 13px;
  font-weight: 600;
}

.timeline p,
.timeline small,
.timeline em {
  display: block;
  margin-top: 3px;
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.timeline em {
  color: var(--spd-text-tertiary);
}

/* ---------- 单件详情 ---------- */
.detail-list {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 9px 12px;
  margin: 0;
}

.detail-list dt {
  color: var(--spd-text-secondary);
  font-size: 12px;
}

.detail-list dd {
  margin: 0;
  min-width: 0;
  color: var(--spd-text-primary);
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

/* ---------- 状态标签 ---------- */
.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.scope-pill {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: var(--spd-radius-sm, 4px);
  background: var(--spd-primary-bg);
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.status-pill.tone-blue {
  background: var(--spd-info-bg, #eff6ff);
  color: #2563eb;
}

.status-pill.tone-amber {
  background: var(--spd-warning-bg, #fff7ed);
  color: #b45309;
}

.status-pill.tone-green {
  background: var(--spd-success-bg, #ecfdf5);
  color: #047857;
}

.status-pill.tone-red {
  background: var(--spd-danger-bg, #fef2f2);
  color: #b91c1c;
}

.status-pill.tone-gray {
  background: var(--spd-bg-subtle, #f3f4f6);
  color: var(--spd-text-secondary);
}

/* ---------- 记录表 ---------- */
.table-wrap {
  /* 未超出容器时不保留滚动条轨道 */
  overflow-x: auto;
  scrollbar-width: thin;
}

.table-wrap::-webkit-scrollbar {
  height: 8px;
}

.table-wrap::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: var(--spd-text-tertiary, #cbd5e1);
}

.table-wrap::-webkit-scrollbar-thumb:hover {
  background: var(--primary);
}

.table-wrap::-webkit-scrollbar-track {
  background: transparent;
}

table {
  width: 100%;
  /* 容器足够宽时铺满，不够时才产生横向滚动 */
  min-width: 860px;
  border-collapse: collapse;
  font-size: 13px;
}

th,
td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--spd-border-subtle);
  text-align: left;
  vertical-align: middle;
}

th {
  background: var(--spd-bg-subtle, #f6f8fb);
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

tbody tr:hover {
  background: var(--spd-primary-bg);
}

tr.selected {
  background: var(--spd-primary-bg);
  box-shadow: inset 3px 0 0 var(--primary);
}

td small {
  display: block;
  margin-top: 3px;
  color: var(--spd-text-tertiary);
  font-size: 12px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1280px) {
  .udi-filters,
  .quota-scan-actions {
    grid-template-columns: 1fr 1fr;
  }

  .trace-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .udi-page {
    padding: 12px 12px 20px;
  }

  .udi-filters,
  .udi-filter-fields,
  .quota-scan-actions {
    grid-template-columns: 1fr;
  }

  .scan-bar {
    grid-template-columns: 18px minmax(0, 1fr) auto;
  }

  .scan-bar .btn:last-child {
    display: none;
  }

  .udi-summary-item {
    border-right: 0;
  }
}
</style>