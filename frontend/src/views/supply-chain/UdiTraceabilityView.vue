<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  AlertTriangle,
  ArrowRight,
  BarChart3,
  Boxes,
  Building2,
  Calendar,
  CheckCircle2,
  CircleX,
  ClipboardList,
  Download,
  FileSearch,
  Filter,
  History,
  Package,
  PackageCheck,
  Printer,
  RefreshCw,
  ScanLine,
  Search,
  ShieldCheck,
  Tag,
  Truck,
  User,
  Warehouse
} from '@lucide/vue'
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
  type UdiTraceEvent,
  type UdiTraceOption,
  type UdiTraceRecord,
  type UdiTraceSummary
} from '../../api/udiTraceability'
import { formatRemarkText, formatStatusText } from '../../utils/chineseDisplay'
import { useNotify } from '../../composables/useNotify'

const notify = useNotify()


const loading = ref(false)
const detailLoading = ref(false)
const message = ref('')
const messageTone = ref<'info' | 'success' | 'warning' | 'error'>('info')
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
  keyword: '',
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

const statusTone: Record<string, 'blue' | 'amber' | 'green' | 'red' | 'gray'> = {
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

const statusIcon: Record<string, typeof Package> = {
  in_stock: Warehouse,
  putaway: Warehouse,
  delivered: Truck,
  requisitioned: ClipboardList,
  delivery_picked: Package,
  patient_bound: User,
  signed: CheckCircle2,
  consumed: PackageCheck,
  settled: CheckCircle2,
  isolated: AlertTriangle
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
  { label: '追溯对象', value: summary.value.totalCodes ?? 0, tone: 'blue', icon: BarChart3 },
  { label: '高值耗材', value: summary.value.highValueCodes ?? 0, tone: 'amber', icon: Tag },
  { label: '低值定数包', value: summary.value.quotaPackageCodes ?? 0, tone: 'green', icon: Boxes },
  { label: '在库', value: summary.value.inStockCodes ?? 0, tone: 'cyan', icon: Warehouse },
  { label: '已完成消耗', value: summary.value.consumedCodes ?? 0, tone: 'success', icon: PackageCheck },
  { label: '异常 / 隔离', value: summary.value.exceptionCodes ?? 0, tone: 'red', icon: AlertTriangle }
])

const activeRecord = computed(() => selected.value?.record ?? records.value[0])
const timeline = computed(() => selected.value?.timeline ?? [])

const isHighValue = computed(() => activeRecord.value?.traceScope === 'high_value')

function labelStatus(value?: string) {
  if (!value) return '-'
  return statusText[value] ?? formatStatusText(value)
}

function pushMessage(text: string, tone: 'info' | 'success' | 'warning' | 'error' = 'info') {
  if (!text) return
  // 业务消息统一走顶部 ElMessage 弹层，避免占用布局、不与表单争抢高度，
  // 同时脱离 .main-area / 弹窗遮罩的 stacking context。
  notify.show(tone, text)
  // 兼容历史 inline-message 引用：保留本地状态以便模板或 watch 还能读取。
  message.value = text
  messageTone.value = tone
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
    pushMessage(error instanceof Error ? error.message : '加载 UDI 追溯数据失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadDetail(code: string) {
  detailLoading.value = true
  try {
    selected.value = await fetchUdiTraceDetail(code)
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '追溯详情加载失败', 'error')
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
    pushMessage('请扫描或输入定数包唯一码', 'warning')
    return
  }
  packageActionLoading.value = true
  try {
    const result = await signQuotaPackageByCode(packageAction.code.trim())
    query.keyword = packageAction.code.trim()
    await loadRecords()
    pushMessage(`定数包已签收入库：${result.deliveryNo}`, 'success')
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '定数包签收入库失败', 'error')
  } finally {
    packageActionLoading.value = false
  }
}

async function consumeQuotaPackage() {
  if (!packageAction.code.trim()) {
    pushMessage('请扫描或输入定数包唯一码', 'warning')
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
    pushMessage(
      `定数包已消耗并进入结算：${result.consumptionNo}，金额 ¥${Number(result.amount).toFixed(2)}`,
      'success'
    )
  } catch (error) {
    pushMessage(error instanceof Error ? error.message : '定数包扫码消耗失败', 'error')
  } finally {
    packageActionLoading.value = false
  }
}

function exportRecords() {
  if (!records.value.length) {
    pushMessage('暂无可导出的追溯记录', 'warning')
    return
  }
  const header = ['唯一码', 'UDI', '对象类型', '商品编码', '商品名称', '规格', '厂家', '批次', '状态', '当前位置', '最近节点', '最近时间']
  const escape = (val: unknown) => {
    const s = val === null || val === undefined ? '' : String(val)
    if (/[",\n]/.test(s)) return '"' + s.replace(/"/g, '""') + '"'
    return s
  }
  const lines = records.value.map((row) => [
    row.uniqueCode,
    row.udiCode,
    scopeText[row.traceScope] || row.traceScope,
    row.productCode,
    row.productName,
    row.specModel,
    row.manufacturerName,
    row.batchNo,
    labelStatus(row.currentStatus),
    row.currentLocation,
    row.lastEventName,
    row.lastEventTime
  ].map(escape).join(','))
  const csv = '\uFEFF' + header.join(',') + '\n' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  const stamp = new Date().toISOString().replace(/[:T]/g, '-').slice(0, 19)
  a.href = url
  a.download = `UDI追溯记录-${stamp}.csv`
  a.click()
  URL.revokeObjectURL(url)
  pushMessage(`已导出 ${records.value.length} 条追溯记录`, 'success')
}

function printDetail() {
  if (!activeRecord.value) {
    pushMessage('请先选择追溯对象', 'warning')
    return
  }
  window.print()
}

onMounted(async () => {
  await loadOptions()
  await loadRecords()
})

const eventDotTone: Record<string, string> = {
  sign: 'green',
  putaway: 'blue',
  requisition: 'blue',
  pick: 'blue',
  deliver: 'amber',
  sign_in: 'amber',
  consume: 'green',
  settle: 'green',
  isolate: 'red',
  recall: 'red',
  reverse: 'red'
}

function eventTone(event: UdiTraceEvent) {
  if (eventDotTone[event.eventType]) return eventDotTone[event.eventType]
  const status = String(event.status || '').toLowerCase()
  if (status === 'failed' || status === 'reversed') return 'red'
  if (status === 'warning') return 'amber'
  return 'blue'
}
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

    <!-- ============ 工作台：扫描查询 ============ -->
    <section class="udi-workbench">
      <div class="scan-card">
        <div class="scan-card-head">
          <span class="scan-card-icon"><ScanLine :size="18" /></span>
          <div>
            <strong>扫码追溯</strong>
            <small>支持 UDI / 唯一码 / 定数包码 / 批号 / 商品关键字</small>
          </div>
        </div>
        <form class="scan-form" @submit.prevent="submitSearch">
          <div class="scan-input-wrap">
            <Search :size="16" class="scan-input-icon" />
            <input
              v-model.trim="query.keyword"
              type="search"
              placeholder="扫描或输入 UDI / 唯一码 / 定数包码 / 批号 / 商品，回车定位"
              @keyup.enter="submitSearch"
            />
            <button v-if="query.keyword" type="button" class="scan-clear" aria-label="清空" @click="query.keyword = ''">
              <CircleX :size="14" />
            </button>
          </div>
          <button class="btn btn-primary scan-submit" type="submit" :disabled="loading">
            <Search :size="15" />
            {{ loading ? '查询中...' : '查询追溯' }}
          </button>
          <button class="btn scan-reset" type="button" @click="resetSearch">
            <RefreshCw :size="15" />
            重置
          </button>
        </form>
      </div>

      <div class="filter-card">
        <div class="filter-card-head">
          <span class="filter-card-icon"><Filter :size="16" /></span>
          <strong>筛选条件</strong>
        </div>
        <div class="filter-card-grid">
          <label>
            <span>对象类型</span>
            <select v-model="query.traceScope" @change="submitSearch">
              <option value="">全部对象</option>
              <option v-for="item in traceScopeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label>
            <span>当前状态</span>
            <select v-model="query.status" @change="submitSearch">
              <option value="">全部状态</option>
              <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
        </div>
      </div>
    </section>

    <!-- ============ 事件类型彩色 tab ============ -->
    <section class="event-tabs" aria-label="追溯事件筛选">
      <button
        type="button"
        class="event-tab is-all"
        :class="{ active: query.eventType === '' }"
        @click="selectEventType('')"
      >
        <BarChart3 :size="14" />
        全部事件
      </button>
      <button
        v-for="(item, idx) in eventTypeOptions"
        :key="item.value"
        type="button"
        class="event-tab"
        :class="[`is-tone-${idx % 5}`, { active: query.eventType === item.value }]"
        @click="selectEventType(item.value)"
      >
        {{ item.label }}
      </button>
    </section>

    <!-- ============ 定数包快捷作业 ============ -->
    <section class="quota-card">
      <div class="quota-card-head">
        <span class="quota-card-icon"><Boxes :size="18" /></span>
        <div>
          <strong>低值定数包扫码作业</strong>
          <small>签收入库后可整包消耗并进入结算，支持 UDI / 唯一码 / 定数包码</small>
        </div>
        <span class="quota-card-tag">快捷作业</span>
      </div>
      <div class="quota-card-grid">
        <label>
          <span class="quota-label-tag is-scan"><ScanLine :size="13" /> 扫描码</span>
          <input
            v-model.trim="packageAction.code"
            placeholder="UDI / 唯一码 / 定数包码"
            @keyup.enter="signQuotaPackage"
          />
        </label>
        <label>
          <span class="quota-label-tag is-dept"><Building2 :size="13" /> 消耗科室</span>
          <input v-model.trim="packageAction.deptName" placeholder="可自动识别，留空将按规则推导" />
        </label>
        <div class="quota-card-actions">
          <button
            class="btn quota-action-sign"
            type="button"
            :disabled="packageActionLoading"
            @click="signQuotaPackage"
          >
            <Truck :size="16" />
            签收入库
          </button>
          <button
            class="btn quota-action-consume"
            type="button"
            :disabled="packageActionLoading"
            @click="consumeQuotaPackage"
          >
            <PackageCheck :size="16" />
            扫码消耗
          </button>
        </div>
      </div>
    </section>

    <!-- 消息：操作反馈统一走顶部 ElMessage 弹层 -->

    <!-- ============ 汇总 chips ============ -->
    <section class="udi-summary" aria-label="追溯汇总">
      <div
        v-for="item in stats"
        :key="item.label"
        class="udi-summary-item"
        :class="`tone-${item.tone}`"
      >
        <span class="udi-summary-icon"><component :is="item.icon" :size="14" /></span>
        <span class="udi-summary-label">{{ item.label }}</span>
        <strong class="udi-summary-value">{{ item.value }}</strong>
      </div>
    </section>

    <!-- ============ 追溯详情：时间线 + 单件详情 ============ -->
    <section class="trace-layout">
      <div class="trace-main">
        <header class="udi-panel-header">
          <div class="udi-panel-title">
            <span class="udi-panel-icon"><History :size="16" /></span>
            <div>
              <h3>追溯时间线</h3>
              <p v-if="activeRecord">
                <span class="scope-pill" :class="isHighValue ? 'is-hv' : 'is-quota'">
                  {{ scopeText[activeRecord.traceScope] || '追溯对象' }}
                </span>
                <span class="trace-code">{{ activeRecord.uniqueCode }}</span>
              </p>
              <p v-else>暂无追溯对象</p>
            </div>
          </div>
          <span
            v-if="activeRecord"
            class="status-pill"
            :class="`tone-${statusTone[activeRecord.currentStatus] || 'gray'}`"
          >
            <component :is="statusIcon[activeRecord.currentStatus] || ShieldCheck" :size="13" />
            {{ labelStatus(activeRecord.currentStatus) }}
          </span>
        </header>
        <ol v-if="timeline.length" class="timeline" :class="{ loading: detailLoading }">
          <li
            v-for="(event, idx) in timeline"
            :key="event.traceEventId"
            :class="[`tone-${eventTone(event)}`, { last: idx === timeline.length - 1 }]"
          >
            <span class="timeline-dot">
              <ShieldCheck :size="14" />
            </span>
            <div class="timeline-content">
              <div class="timeline-head">
                <strong>{{ event.eventName || eventText[event.eventType] || event.eventType }}</strong>
                <small>{{ event.eventTime }}</small>
              </div>
              <p class="timeline-meta">
                <span v-if="event.locationName"><Building2 :size="12" /> {{ event.locationName }}</span>
                <span v-if="event.operatorName"><User :size="12" /> {{ event.operatorName }}</span>
                <span v-if="event.bizNo"><Tag :size="12" /> {{ event.bizNo }}</span>
              </p>
              <em v-if="event.remark">{{ formatRemarkText(event.remark) }}</em>
            </div>
          </li>
        </ol>
        <EmptyState v-else message="暂无追溯节点" />
      </div>

      <aside class="trace-detail">
        <header class="udi-panel-header compact">
          <div class="udi-panel-title">
            <span class="udi-panel-icon"><FileSearch :size="16" /></span>
            <h3>单件详情</h3>
          </div>
          <span v-if="detailLoading" class="detail-loading-tag">加载中</span>
        </header>
        <dl v-if="activeRecord" class="detail-list">
          <dt>对象类型</dt>
          <dd>
            <span class="scope-pill" :class="isHighValue ? 'is-hv' : 'is-quota'">
              {{ scopeText[activeRecord.traceScope] || '-' }}
            </span>
          </dd>
          <dt>商品</dt>
          <dd>
            <strong>{{ activeRecord.productName }}</strong>
            <small>{{ activeRecord.productCode }} · {{ activeRecord.specModel || '-' }}</small>
          </dd>
          <dt>UDI</dt>
          <dd class="detail-mono">{{ activeRecord.udiCode }}</dd>
          <dt>唯一码</dt>
          <dd class="detail-mono detail-strong">{{ activeRecord.uniqueCode }}</dd>
          <template v-if="activeRecord.traceScope === 'low_value_quota_pack'">
            <dt>定数包码</dt>
            <dd>{{ activeRecord.packageLabelNo || '-' }}</dd>
            <dt>定数模板</dt>
            <dd>{{ activeRecord.templateName || '-' }} <small>{{ activeRecord.templateCode || '' }}</small></dd>
            <dt>包内数量</dt>
            <dd>{{ activeRecord.packageQuantity || '-' }} {{ activeRecord.packageUnit || '' }}</dd>
          </template>
          <dt>系统批次</dt>
          <dd>{{ activeRecord.batchNo || '-' }}</dd>
          <dt>当前库位</dt>
          <dd>
            <Warehouse :size="12" />
            {{ activeRecord.currentLocation || '-' }}
          </dd>
          <dt>当前科室</dt>
          <dd>
            <Building2 :size="12" />
            {{ activeRecord.currentDepartment || '-' }}
          </dd>
          <dt>有效期</dt>
          <dd>
            <Calendar :size="12" />
            {{ activeRecord.expireDate || '-' }}
          </dd>
          <dt>厂家 / 供应商</dt>
          <dd>{{ activeRecord.manufacturerName || '-' }} <small>/ {{ activeRecord.supplierName || '-' }}</small></dd>
          <template v-if="isHighValue">
            <dt>患者</dt>
            <dd>
              <User :size="12" />
              {{ activeRecord.patientNo || '-' }} {{ activeRecord.patientNameMasked || '' }}
            </dd>
          </template>
          <template v-else>
            <dt>包状态</dt>
            <dd>{{ formatStatusText(activeRecord.packageStatus) }}</dd>
          </template>
        </dl>
        <EmptyState v-else message="请选择追溯记录" />
      </aside>
    </section>

    <!-- ============ 唯一码记录表 ============ -->
    <section class="record-panel">
      <header class="udi-panel-header compact">
        <div class="udi-panel-title">
          <span class="udi-panel-icon"><ClipboardList :size="16" /></span>
          <div>
            <h3>唯一码记录</h3>
            <p>共 {{ total }} 条 · 当前第 {{ page }} 页</p>
          </div>
        </div>
        <div class="record-tools">
          <button class="btn tool-btn tool-refresh" type="button" :disabled="loading" @click="loadRecords">
            <RefreshCw :size="14" />
            刷新
          </button>
          <button class="btn tool-btn tool-export" type="button" :disabled="!records.length" @click="exportRecords">
            <Download :size="14" />
            导出 CSV
          </button>
          <button class="btn tool-btn tool-print" type="button" :disabled="!activeRecord" @click="printDetail">
            <Printer :size="14" />
            打印
          </button>
        </div>
      </header>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>唯一码 / UDI</th>
              <th>对象类型</th>
              <th>商品</th>
              <th>批次</th>
              <th>状态</th>
              <th>当前位置</th>
              <th>最近节点</th>
              <th class="th-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8"><EmptyState message="正在加载追溯记录..." /></td>
            </tr>
            <tr v-else-if="!records.length">
              <td colspan="8"><EmptyState message="暂无符合条件的追溯记录" /></td>
            </tr>
            <template v-else>
              <tr
                v-for="row in records"
                :key="row.uniqueCode"
                :class="{ selected: row.uniqueCode === activeRecord?.uniqueCode }"
                @click="loadDetail(row.uniqueCode)"
              >
                <td>
                  <strong class="cell-mono">{{ row.uniqueCode }}</strong>
                  <small class="cell-mono">{{ row.udiCode }}</small>
                </td>
                <td>
                  <span class="scope-pill" :class="row.traceScope === 'high_value' ? 'is-hv' : 'is-quota'">
                    {{ scopeText[row.traceScope] || '-' }}
                  </span>
                  <small v-if="row.packageLabelNo">{{ row.packageLabelNo }}</small>
                </td>
                <td>
                  <strong>{{ row.productName }}</strong>
                  <small>{{ row.productCode }} · {{ row.specModel || '-' }}</small>
                  <small v-if="row.templateName">{{ row.templateName }} · {{ row.packageQuantity || '-' }}{{ row.packageUnit || '' }}</small>
                </td>
                <td>{{ row.batchNo || '-' }}</td>
                <td>
                  <span class="status-pill" :class="`tone-${statusTone[row.currentStatus] || 'gray'}`">
                    <component :is="statusIcon[row.currentStatus] || ShieldCheck" :size="12" />
                    {{ labelStatus(row.currentStatus) }}
                  </span>
                </td>
                <td>{{ row.currentLocation || '-' }}</td>
                <td>
                  <span class="last-event">{{ row.lastEventName || '-' }}</span>
                  <small>{{ row.lastEventTime || '-' }}</small>
                </td>
                <td class="td-action">
                  <button class="row-action-btn" type="button" @click.stop="loadDetail(row.uniqueCode)">
                    <ArrowRight :size="13" />
                    追溯
                  </button>
                </td>
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
   UDI / 唯一码追溯（重制版）
   - 三段式工作台：扫码查询 / 筛选 / 事件 tab
   - 快捷作业卡片（橙色签收 + 绿色消耗）
   - 时间线节点按事件类型着色
   - 单件详情按字段分组
   - 唯一码记录表带行点击 + 状态彩色 pill
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

/* ---------- 工作台：扫描 + 筛选双卡 ---------- */
.udi-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 1fr);
  gap: 12px;
  align-items: stretch;
}

.scan-card,
.filter-card,
.quota-card,
.trace-main,
.trace-detail,
.record-panel {
  border: 1px solid var(--spd-border-subtle);
  border-radius: var(--spd-radius-md, 10px);
  background: var(--spd-bg-container);
  box-shadow: var(--spd-shadow-1);
}

.scan-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-left: 3px solid var(--primary, #1677ff);
  background: linear-gradient(120deg, #f0f7ff 0%, #ffffff 65%);
}

.scan-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.scan-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #1677ff;
  background: #dbeafe;
  border-radius: 8px;
}

.scan-card-head strong {
  display: block;
  color: var(--spd-text-heading);
  font-size: 14px;
  font-weight: 600;
}

.scan-card-head small {
  display: block;
  color: var(--spd-text-secondary);
  font-size: 12px;
}

.scan-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
}

.scan-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 12px 0 38px;
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.scan-input-wrap:focus-within {
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.15);
}

.scan-input-icon {
  position: absolute;
  left: 12px;
  color: #1677ff;
}

.scan-input-wrap input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--spd-text-primary);
  font: inherit;
  font-size: 13px;
}

.scan-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-left: 6px;
  color: #94a3b8;
  background: transparent;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
}

.scan-clear:hover {
  color: #ef4444;
  background: #fef2f2;
}

.scan-submit,
.scan-reset {
  height: 40px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 8px;
  white-space: nowrap;
}

.scan-submit {
  color: #fff;
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 100%);
  border: 1px solid #1677ff;
  box-shadow: 0 3px 8px rgba(22, 119, 255, 0.25);
}

.scan-submit:hover:not(:disabled) {
  background: linear-gradient(135deg, #0958d9 0%, #1677ff 100%);
  box-shadow: 0 5px 12px rgba(22, 119, 255, 0.3);
}

.scan-reset {
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.scan-reset:hover:not(:disabled) {
  color: #0f172a;
  background: #e2e8f0;
  border-color: #cbd5e1;
}

.filter-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-left: 3px solid #10b981;
}

.filter-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #047857;
  background: #d1fae5;
  border-radius: 6px;
}

.filter-card-head strong {
  color: var(--spd-text-heading);
  font-size: 14px;
  font-weight: 600;
}

.filter-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.filter-card-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
}

.filter-card-grid label > span {
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.filter-card-grid select {
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  font: inherit;
  font-size: 13px;
  color: var(--spd-text-primary);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.filter-card-grid select:focus {
  outline: 0;
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.15);
}

/* ---------- 事件类型彩色 tab ---------- */
.event-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px 0 2px;
}

.event-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  color: var(--spd-text-secondary);
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
}

.event-tab:hover {
  border-color: #cbd5e1;
  color: #0f172a;
  background: #f8fafc;
}

.event-tab.is-all {
  color: #475569;
}

.event-tab.is-tone-0 {
  border-color: #bfdbfe;
  color: #1d4ed8;
}
.event-tab.is-tone-0:hover,
.event-tab.is-tone-0.active {
  border-color: #1677ff;
  color: #fff;
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 100%);
  box-shadow: 0 2px 6px rgba(22, 119, 255, 0.3);
}

.event-tab.is-tone-1 {
  border-color: #bbf7d0;
  color: #047857;
}
.event-tab.is-tone-1:hover,
.event-tab.is-tone-1.active {
  border-color: #10b981;
  color: #fff;
  background: linear-gradient(135deg, #10b981 0%, #34d399 100%);
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.3);
}

.event-tab.is-tone-2 {
  border-color: #fde68a;
  color: #b45309;
}
.event-tab.is-tone-2:hover,
.event-tab.is-tone-2.active {
  border-color: #f59e0b;
  color: #fff;
  background: linear-gradient(135deg, #f59e0b 0%, #fbbf24 100%);
  box-shadow: 0 2px 6px rgba(245, 158, 11, 0.3);
}

.event-tab.is-tone-3 {
  border-color: #fecaca;
  color: #b91c1c;
}
.event-tab.is-tone-3:hover,
.event-tab.is-tone-3.active {
  border-color: #ef4444;
  color: #fff;
  background: linear-gradient(135deg, #ef4444 0%, #f87171 100%);
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.3);
}

.event-tab.is-tone-4 {
  border-color: #c4b5fd;
  color: #6d28d9;
}
.event-tab.is-tone-4:hover,
.event-tab.is-tone-4.active {
  border-color: #8b5cf6;
  color: #fff;
  background: linear-gradient(135deg, #8b5cf6 0%, #a78bfa 100%);
  box-shadow: 0 2px 6px rgba(139, 92, 246, 0.3);
}

.event-tab.is-all.active {
  border-color: #0f172a;
  color: #fff;
  background: linear-gradient(135deg, #0f172a 0%, #334155 100%);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.3);
}

/* ---------- 定数包快捷作业卡 ---------- */
.quota-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
  border-left: 3px solid #f59e0b;
  background: linear-gradient(120deg, #fff7ed 0%, #ffffff 65%);
}

.quota-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.quota-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #b45309;
  background: #fef3c7;
  border-radius: 8px;
}

.quota-card-head strong {
  display: block;
  color: var(--spd-text-heading);
  font-size: 14px;
  font-weight: 600;
}

.quota-card-head small {
  display: block;
  color: var(--spd-text-secondary);
  font-size: 12px;
}

.quota-card-tag {
  display: inline-flex;
  align-items: center;
  margin-left: auto;
  padding: 2px 10px;
  color: #b45309;
  font-size: 12px;
  font-weight: 600;
  background: #fef3c7;
  border: 1px solid #fde68a;
  border-radius: 999px;
}

.quota-card-grid {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(180px, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.quota-card-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
}

.quota-label-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  width: max-content;
  font-size: 12px;
  font-weight: 600;
  border-radius: 4px;
}

.quota-label-tag.is-scan {
  color: #1d4ed8;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
}

.quota-label-tag.is-dept {
  color: #047857;
  background: #d1fae5;
  border: 1px solid #a7f3d0;
}

.quota-card-grid input {
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  font: inherit;
  font-size: 13px;
  color: var(--spd-text-primary);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.quota-card-grid input:focus {
  outline: 0;
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.15);
}

.quota-card-actions {
  display: flex;
  gap: 8px;
}

.quota-action-sign,
.quota-action-consume {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 130px;
  height: 38px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 8px;
  white-space: nowrap;
}

.quota-action-sign {
  color: #fff;
  background: linear-gradient(135deg, #f59e0b 0%, #fbbf24 100%);
  border: 1px solid #f59e0b;
  box-shadow: 0 3px 8px rgba(245, 158, 11, 0.25);
}

.quota-action-sign:hover:not(:disabled) {
  background: linear-gradient(135deg, #d97706 0%, #f59e0b 100%);
  box-shadow: 0 5px 12px rgba(245, 158, 11, 0.3);
}

.quota-action-consume {
  color: #fff;
  background: linear-gradient(135deg, #10b981 0%, #34d399 100%);
  border: 1px solid #10b981;
  box-shadow: 0 3px 8px rgba(16, 185, 129, 0.25);
}

.quota-action-consume:hover:not(:disabled) {
  background: linear-gradient(135deg, #059669 0%, #10b981 100%);
  box-shadow: 0 5px 12px rgba(16, 185, 129, 0.3);
}

/* ---------- 消息提示 ---------- */
.form-message {
  padding: 9px 12px;
  border: 1px solid;
  border-radius: var(--spd-radius-sm, 6px);
  background: var(--spd-primary-bg);
  color: var(--spd-text-primary);
  font-size: 13px;
}

.form-message.is-info {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.form-message.is-success {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.form-message.is-warning {
  border-color: #fde68a;
  background: #fffbeb;
  color: #b45309;
}

.form-message.is-error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

/* ---------- 汇总 chips ---------- */
.udi-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.udi-summary-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 130px;
  padding: 6px 12px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 12px;
  background: #fff;
  transition: transform 0.1s ease;
}

.udi-summary-item:hover {
  transform: translateY(-1px);
}

.udi-summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
}

.udi-summary-label {
  color: var(--spd-text-secondary);
}

.udi-summary-value {
  margin-left: auto;
  color: var(--spd-text-heading);
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.udi-summary-item.tone-blue {
  border-color: #bfdbfe;
  background: #eff6ff;
}
.udi-summary-item.tone-blue .udi-summary-icon {
  color: #1d4ed8;
  background: #dbeafe;
}
.udi-summary-item.tone-blue .udi-summary-value {
  color: #1d4ed8;
}

.udi-summary-item.tone-amber {
  border-color: #fde68a;
  background: #fffbeb;
}
.udi-summary-item.tone-amber .udi-summary-icon {
  color: #b45309;
  background: #fef3c7;
}
.udi-summary-item.tone-amber .udi-summary-value {
  color: #b45309;
}

.udi-summary-item.tone-green {
  border-color: #a7f3d0;
  background: #ecfdf5;
}
.udi-summary-item.tone-green .udi-summary-icon {
  color: #047857;
  background: #d1fae5;
}
.udi-summary-item.tone-green .udi-summary-value {
  color: #047857;
}

.udi-summary-item.tone-success {
  border-color: #6ee7b7;
  background: #d1fae5;
}
.udi-summary-item.tone-success .udi-summary-icon {
  color: #047857;
  background: #a7f3d0;
}
.udi-summary-item.tone-success .udi-summary-value {
  color: #065f46;
}

.udi-summary-item.tone-cyan {
  border-color: #a5f3fc;
  background: #ecfeff;
}
.udi-summary-item.tone-cyan .udi-summary-icon {
  color: #0e7490;
  background: #cffafe;
}
.udi-summary-item.tone-cyan .udi-summary-value {
  color: #155e75;
}

.udi-summary-item.tone-red {
  border-color: #fecaca;
  background: #fef2f2;
}
.udi-summary-item.tone-red .udi-summary-icon {
  color: #b91c1c;
  background: #fee2e2;
}
.udi-summary-item.tone-red .udi-summary-value {
  color: #b91c1c;
}

/* ---------- 面板通用 ---------- */
.udi-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.udi-panel-header.compact {
  align-items: center;
}

.udi-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.udi-panel-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  color: #1677ff;
  background: #dbeafe;
  border-radius: 6px;
}

.udi-panel-header h3 {
  margin: 0;
  color: var(--spd-text-heading);
  font-size: 15px;
  font-weight: 600;
}

.udi-panel-header p {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  color: var(--spd-text-secondary);
  font-size: 12px;
}

.trace-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--spd-text-heading);
  font-size: 12px;
  font-weight: 500;
}

.detail-loading-tag {
  padding: 2px 8px;
  color: #1d4ed8;
  font-size: 12px;
  background: #dbeafe;
  border-radius: 999px;
}

.record-tools {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  transition: all 0.15s ease;
}

.tool-refresh {
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}
.tool-refresh:hover:not(:disabled) {
  color: #0f172a;
  background: #e2e8f0;
  border-color: #cbd5e1;
}

.tool-export {
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}
.tool-export:hover:not(:disabled) {
  color: #064e3b;
  background: #d1fae5;
  border-color: #6ee7b7;
}

.tool-print {
  color: #6d28d9;
  background: #f5f3ff;
  border: 1px solid #c4b5fd;
}
.tool-print:hover:not(:disabled) {
  color: #4c1d95;
  background: #ede9fe;
  border-color: #a78bfa;
}

/* ---------- 时间线 ---------- */
.trace-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 14px;
  align-items: start;
}

.trace-main {
  padding: 16px;
}

.timeline {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 4px 0 0;
  list-style: none;
  transition: opacity 0.15s ease;
}

.timeline.loading {
  opacity: 0.55;
}

.timeline li {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  position: relative;
  padding-bottom: 16px;
}

.timeline li:not(.last)::before {
  content: '';
  position: absolute;
  top: 28px;
  bottom: 0;
  left: 15px;
  width: 2px;
  background: #e2e8f0;
}

.timeline-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #dbeafe;
  color: #1677ff;
  z-index: 1;
  box-shadow: 0 0 0 2px #e0e7ff;
}

.timeline li.tone-blue .timeline-dot {
  background: #dbeafe;
  color: #1d4ed8;
  box-shadow: 0 0 0 2px #bfdbfe;
}
.timeline li.tone-blue:not(.last)::before {
  background: linear-gradient(180deg, #bfdbfe 0%, #e2e8f0 100%);
}

.timeline li.tone-green .timeline-dot {
  background: #d1fae5;
  color: #047857;
  box-shadow: 0 0 0 2px #a7f3d0;
}
.timeline li.tone-green:not(.last)::before {
  background: linear-gradient(180deg, #a7f3d0 0%, #e2e8f0 100%);
}

.timeline li.tone-amber .timeline-dot {
  background: #fef3c7;
  color: #b45309;
  box-shadow: 0 0 0 2px #fde68a;
}
.timeline li.tone-amber:not(.last)::before {
  background: linear-gradient(180deg, #fde68a 0%, #e2e8f0 100%);
}

.timeline li.tone-red .timeline-dot {
  background: #fee2e2;
  color: #b91c1c;
  box-shadow: 0 0 0 2px #fecaca;
}
.timeline li.tone-red:not(.last)::before {
  background: linear-gradient(180deg, #fecaca 0%, #e2e8f0 100%);
}

.timeline-content {
  min-width: 0;
  padding: 0 0 0 4px;
}

.timeline-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.timeline-head strong {
  color: var(--spd-text-heading);
  font-size: 13px;
  font-weight: 600;
}

.timeline-head small {
  color: var(--spd-text-tertiary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 4px 0 0;
  color: var(--spd-text-secondary);
  font-size: 12px;
}

.timeline-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.timeline em {
  display: block;
  margin-top: 4px;
  padding: 4px 8px;
  color: var(--spd-text-tertiary);
  font-size: 12px;
  font-style: normal;
  background: #f8fafc;
  border-left: 2px solid #cbd5e1;
  border-radius: 0 4px 4px 0;
}

.timeline li.tone-red em {
  background: #fef2f2;
  border-left-color: #fecaca;
  color: #b91c1c;
}

/* ---------- 单件详情 ---------- */
.trace-detail {
  padding: 16px;
}

.detail-list {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px 10px;
  margin: 0;
}

.detail-list dt {
  display: flex;
  align-items: center;
  min-height: 28px;
  padding: 4px 0;
  color: var(--spd-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.detail-list dd {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-width: 0;
  min-height: 28px;
  margin: 0;
  padding: 4px 0;
  color: var(--spd-text-primary);
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.detail-list dd > svg {
  color: #94a3b8;
  flex-shrink: 0;
}

.detail-list dd small {
  color: var(--spd-text-tertiary);
  font-size: 12px;
  font-weight: 400;
}

.detail-list dd strong {
  display: block;
  width: 100%;
  color: var(--spd-text-heading);
  font-size: 13px;
  font-weight: 600;
}

.detail-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace !important;
  font-size: 12px !important;
  color: #475569 !important;
}

.detail-strong {
  color: #1d4ed8 !important;
  font-weight: 600 !important;
}

/* ---------- 状态 / 范围 pill ---------- */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
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
  border: 1px solid;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.scope-pill.is-hv {
  color: #b45309;
  background: #fef3c7;
  border-color: #fde68a;
}

.scope-pill.is-quota {
  color: #047857;
  background: #d1fae5;
  border-color: #a7f3d0;
}

.status-pill.tone-blue {
  background: #eff6ff;
  color: #1d4ed8;
  border: 1px solid #bfdbfe;
}

.status-pill.tone-amber {
  background: #fffbeb;
  color: #b45309;
  border: 1px solid #fde68a;
}

.status-pill.tone-green {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.status-pill.tone-red {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}

.status-pill.tone-gray {
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}

/* ---------- 记录表 ---------- */
.record-panel {
  padding: 16px;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  scrollbar-width: thin;
}

.table-wrap::-webkit-scrollbar {
  height: 8px;
}

.table-wrap::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #cbd5e1;
}

.table-wrap::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.table-wrap::-webkit-scrollbar-track {
  background: transparent;
}

table {
  width: 100%;
  min-width: 1100px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 14px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  color: #1e293b !important;
  background: linear-gradient(180deg, #f0f7ff 0%, #e0efff 100%) !important;
  border-bottom: 1px solid #c7d2fe !important;
  text-align: left;
  white-space: nowrap;
}

tbody td {
  padding: 10px 14px !important;
  border-bottom: 1px solid #f1f5f9 !important;
  color: #334155 !important;
  font-size: 13px !important;
  vertical-align: middle;
  background: #fff;
}

tbody tr:nth-child(even) td {
  background: #fafbfc;
}

tbody tr {
  cursor: pointer;
  transition: background 0.12s ease;
}

tbody tr:hover td {
  background: #f0f7ff !important;
}

tbody tr.selected td {
  background: #e0efff !important;
  box-shadow: inset 3px 0 0 #1677ff;
}

tbody tr.selected:hover td {
  background: #d1e5ff !important;
}

.cell-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace !important;
  font-size: 12px !important;
  color: #475569 !important;
}

td small {
  display: block;
  margin-top: 3px;
  color: var(--spd-text-tertiary);
  font-size: 12px;
  font-weight: 400;
}

.last-event {
  display: block;
  color: var(--spd-text-heading);
  font-size: 13px;
  font-weight: 500;
}

.th-action,
.td-action {
  text-align: right;
  white-space: nowrap;
}

.row-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 28px;
  padding: 0 10px;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 500;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  transition: all 0.15s ease;
}

.row-action-btn:hover {
  color: #fff;
  background: #1677ff;
  border-color: #1677ff;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1280px) {
  .udi-workbench,
  .quota-card-grid,
  .trace-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .udi-page {
    padding: 12px 12px 20px;
  }
  .scan-form,
  .filter-card-grid,
  .quota-card-grid {
    grid-template-columns: 1fr;
  }
  .quota-card-actions {
    width: 100%;
  }
  .quota-action-sign,
  .quota-action-consume {
    flex: 1;
  }
  .udi-summary-item {
    min-width: 0;
    flex: 1 1 45%;
  }
  .record-tools {
    flex-wrap: wrap;
  }
}
</style>
