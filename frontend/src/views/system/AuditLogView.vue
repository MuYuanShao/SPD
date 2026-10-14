<script setup lang="ts">
/**
 * 审计日志（系统管理 / 审计日志）
 * ---------------------------------------------------------------------
 * 造型与证照管理 / 系统管理其他页面完全一致：
 *   - MdPageHeader 渐变页头（系统管理面包屑 + 标题 + 副标题 + 刷新）
 *   - md-form-section 多条件查询区（操作人 / 操作类型 / 业务类型 / 业务单据号 / 关键字 / 时间范围）
 *   - sys-stat-grid 4 张汇总卡（总记录 / 今日 / 涉及人员 / 业务类型）
 *   - md-table-card 表格（按时间倒序，操作人 / 业务类型 / 操作类型 / 业务单据 / 业务 ID / IP / 时间 / 备注 / 操作）
 *   - md-dialog 详情弹窗（基本信息 + 前后变更 JSON 对比）
 * 设计语言与系统管理其他页面（字段管理、配置命中说明）保持完全一致。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Download, FileSearch, Filter, RefreshCw, Search, X } from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import {
  fetchAuditLogBizTypes,
  fetchAuditLogDetail,
  fetchAuditLogOperationTypes,
  fetchAuditLogs,
  type AuditLogFacet,
  type AuditLogRow,
  type AuditLogSummary
} from '../../api/auditLogs'

const rows = ref<AuditLogRow[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const error = ref('')

const summary = ref<AuditLogSummary>({ total: 0, today: 0, distinctOperators: 0, distinctBizTypes: 0 })
const operationTypeFacets = ref<AuditLogFacet[]>([])
const bizTypeFacets = ref<AuditLogFacet[]>([])

const query = ref({
  operatorName: '',
  operationType: '',
  bizType: '',
  bizNo: '',
  keyword: '',
  fromDate: '',
  toDate: ''
})

const pagination = { page: computed(() => page.value), size: computed(() => size) }

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailRow = ref<AuditLogRow | null>(null)
const detailError = ref('')
const activeTab = ref<'overview' | 'before' | 'after'>('overview')

const showBeforeAfter = computed(() => detailRow.value && (detailRow.value.beforeData || detailRow.value.afterData))
const beforeAfterEntries = computed(() => {
  const row = detailRow.value
  if (!row) return [] as Array<{ key: string; before: unknown; after: unknown; changed: boolean }>
  const before = (row.beforeData ?? {}) as Record<string, unknown>
  const after = (row.afterData ?? {}) as Record<string, unknown>
  const keys = new Set([...Object.keys(before), ...Object.keys(after)])
  return Array.from(keys)
    .sort()
    .map((key) => {
      const bv = before[key]
      const av = after[key]
      return {
        key,
        before: bv === undefined ? null : bv,
        after: av === undefined ? null : av,
        changed: JSON.stringify(bv ?? null) !== JSON.stringify(av ?? null)
      }
    })
})

const summaryStats = computed(() => [
  { label: '总记录', value: summary.value.total },
  { label: '今日操作', value: summary.value.today },
  { label: '涉及人员', value: summary.value.distinctOperators },
  { label: '业务类型', value: summary.value.distinctBizTypes }
])

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchAuditLogs({
      ...query.value,
      page: page.value,
      size
    })
    rows.value = data.rows
    total.value = data.total
    if (data.summary) summary.value = data.summary
  } catch (err: any) {
    error.value = err?.message || '审计日志加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadFacets() {
  try {
    const [ops, biz] = await Promise.all([
      fetchAuditLogOperationTypes(),
      fetchAuditLogBizTypes()
    ])
    operationTypeFacets.value = ops.operationTypes ?? []
    bizTypeFacets.value = biz.bizTypes ?? []
  } catch (err) {
    // Facets are advisory; non-blocking.
    operationTypeFacets.value = []
    bizTypeFacets.value = []
  }
}

function onSearch() {
  page.value = 1
  loadList()
}

function onReset() {
  query.value = { operatorName: '', operationType: '', bizType: '', bizNo: '', keyword: '', fromDate: '', toDate: '' }
  page.value = 1
  loadList()
}

function changePage(p: number, s?: number) {
  if (s !== undefined) {
    page.value = Math.floor((page.value * size) / s) || 1
    // size is constant; we still update local size via pagination ref
  }
  page.value = p
  loadList()
}

function changePageSize(s: number) {
  // Size is constant here; the page resets to 1.
  page.value = 1
  loadList()
}

async function openDetail(row: AuditLogRow) {
  detailOpen.value = true
  detailLoading.value = true
  detailRow.value = row
  detailError.value = ''
  activeTab.value = 'overview'
  try {
    const data = await fetchAuditLogDetail(row.auditId)
    if (data.audit) detailRow.value = data.audit
  } catch (err: any) {
    detailError.value = err?.message || '审计日志详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailOpen.value = false
  detailRow.value = null
  detailError.value = ''
}

function exportJson() {
  if (!rows.value.length) return
  const exportRows = rows.value.map((row) => ({
    auditId: row.auditId,
    operatorName: row.operatorName,
    operationType: row.operationType,
    bizType: row.bizType,
    bizId: row.bizId,
    bizNo: row.bizNo,
    ipAddress: row.ipAddress,
    operationTime: row.operationTime,
    remark: row.remark
  }))
  const blob = new Blob([JSON.stringify(exportRows, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `audit-log-${new Date().toISOString().slice(0, 10)}.json`
  link.click()
  URL.revokeObjectURL(url)
}

function valuePreview(v: unknown): string {
  if (v === null || v === undefined) return '—'
  if (typeof v === 'string') return v
  try { return JSON.stringify(v) } catch { return String(v) }
}

onMounted(() => {
  loadFacets()
  loadList()
})

watch(() => query.value.bizType, (v) => {
  // Auto-submit when business type changes via facet
  if (v) {
    page.value = 1
    loadList()
  }
})
</script>

<template>
  <div class="md-page sys-page">
    <MdPageHeader
      crumb="系统管理"
      title="审计日志"
      description="查看系统关键业务的操作留痕、变更前后数据与操作上下文，支持多条件查询与导出。"
      :loading="loading"
      @refresh="loadList"
    >
      <template #actions>
        <button class="md-btn" type="button" :disabled="!rows.length" @click="exportJson">
          <Download :size="15" /> 导出当前页
        </button>
      </template>
    </MdPageHeader>

    <div class="md-content sys-content">
      <!-- 汇总 -->
      <div class="sys-stat-grid">
        <article v-for="item in summaryStats" :key="item.label">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </div>

      <!-- 查询条件 -->
      <div class="md-form-section">
        <h4><Filter :size="14" /> 查询条件</h4>
        <div class="md-form-grid">
          <label><span>操作人</span><input v-model.trim="query.operatorName" class="md-input" placeholder="模糊查询操作人姓名" @keyup.enter="onSearch" /></label>
          <label><span>操作类型</span>
            <select v-model="query.operationType" class="md-select">
              <option value="">全部</option>
              <option v-for="f in operationTypeFacets" :key="f.operationType" :value="f.operationType">{{ f.operationType }} ({{ f.total }})</option>
            </select>
          </label>
          <label><span>业务类型</span>
            <select v-model="query.bizType" class="md-select">
              <option value="">全部</option>
              <option v-for="f in bizTypeFacets" :key="f.operationType" :value="f.operationType">{{ f.bizTypeLabel || f.operationType }} ({{ f.total }})</option>
            </select>
          </label>
          <label><span>业务单据号</span><input v-model.trim="query.bizNo" class="md-input" placeholder="模糊查询业务单据号" @keyup.enter="onSearch" /></label>
          <label><span>关键字</span><input v-model.trim="query.keyword" class="md-input" placeholder="备注 / IP" @keyup.enter="onSearch" /></label>
          <label><span>开始日期</span><input v-model="query.fromDate" type="date" class="md-input" /></label>
          <label><span>结束日期</span><input v-model="query.toDate" type="date" class="md-input" /></label>
          <div class="md-form-grid-actions">
            <button class="md-btn" type="button" @click="onReset">重置</button>
            <button class="md-btn md-btn-primary" type="button" @click="onSearch"><Search :size="15" /> 查询</button>
          </div>
        </div>
      </div>

      <div v-if="error" class="md-error">{{ error }}</div>

      <!-- 表格 -->
      <div class="md-table-card">
        <div class="table-scroll">
          <table class="md-table">
            <thead>
              <tr>
                <th>操作人</th>
                <th>操作类型</th>
                <th>业务类型</th>
                <th>业务单据</th>
                <th>业务 ID</th>
                <th>IP</th>
                <th>操作时间</th>
                <th>备注</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="9" class="text-center muted">正在加载审计日志...</td></tr>
              <tr v-else-if="!rows.length"><td colspan="9" class="text-center muted">未找到匹配的审计日志</td></tr>
              <tr v-else v-for="row in rows" :key="row.auditId">
                <td>{{ row.operatorName || '系统' }}</td>
                <td><span class="md-chip primary">{{ row.operationType }}</span></td>
                <td>{{ row.bizType }}</td>
                <td>{{ row.bizNo || '-' }}</td>
                <td>{{ row.bizId ?? '-' }}</td>
                <td>{{ row.ipAddress || '-' }}</td>
                <td>{{ row.operationTime || '-' }}</td>
                <td class="audit-remark-cell" :title="row.remark || ''">{{ row.remark || '-' }}</td>
                <td>
                  <button class="md-btn-text" type="button" @click="openDetail(row)">
                    <FileSearch :size="14" /> 详情
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="total > 0" class="md-pagination">
          <span>共 <strong>{{ total }}</strong> 条 · 第 <strong>{{ page }}</strong> / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
          <PaginationControls
            :page="pagination.page.value"
            :size="size"
            :total="total"
            :loading="loading"
            @change-page="changePage"
            @change-size="changePageSize"
          />
        </div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <div v-if="detailOpen" class="md-dialog-mask" @click.self="closeDetail">
      <div class="md-dialog xl" role="dialog" aria-modal="true">
        <div class="md-dialog-header">
          <h3>审计日志详情 · #{{ detailRow?.auditId }}</h3>
          <button class="md-btn-icon" type="button" @click="closeDetail"><X :size="16" /></button>
        </div>
        <div class="md-dialog-body">
          <div v-if="detailLoading" class="text-center muted">正在加载详情...</div>
          <div v-else-if="detailError" class="md-error">{{ detailError }}</div>
          <template v-else-if="detailRow">
            <div class="audit-detail-info">
              <article><span>操作人</span><strong>{{ detailRow.operatorName || '系统' }}</strong></article>
              <article><span>操作类型</span><span class="md-chip primary">{{ detailRow.operationType }}</span></article>
              <article><span>业务类型</span><strong>{{ detailRow.bizType }}</strong></article>
              <article><span>业务单据号</span><strong>{{ detailRow.bizNo || '-' }}</strong></article>
              <article><span>业务 ID</span><strong>{{ detailRow.bizId ?? '-' }}</strong></article>
              <article><span>IP 地址</span><strong>{{ detailRow.ipAddress || '-' }}</strong></article>
              <article><span>操作时间</span><strong>{{ detailRow.operationTime || '-' }}</strong></article>
              <article class="wide"><span>备注</span><strong>{{ detailRow.remark || '-' }}</strong></article>
            </div>

            <div v-if="showBeforeAfter" class="md-tabs sys-tabs" style="margin:12px 0 0">
              <button type="button" :class="['md-tab', { active: activeTab === 'overview' }]" @click="activeTab = 'overview'">变更对比</button>
              <button type="button" :class="['md-tab', { active: activeTab === 'before' }]" @click="activeTab = 'before'">变更前</button>
              <button type="button" :class="['md-tab', { active: activeTab === 'after' }]" @click="activeTab = 'after'">变更后</button>
            </div>

            <div v-if="showBeforeAfter && activeTab === 'overview'" class="audit-diff-table">
              <table class="md-table">
                <thead>
                  <tr>
                    <th style="width:160px">字段</th>
                    <th>变更前</th>
                    <th>变更后</th>
                    <th style="width:80px">状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="entry in beforeAfterEntries" :key="entry.key" :class="{ 'diff-changed': entry.changed }">
                    <td><code>{{ entry.key }}</code></td>
                    <td><code>{{ valuePreview(entry.before) }}</code></td>
                    <td><code>{{ valuePreview(entry.after) }}</code></td>
                    <td>
                      <span :class="['md-status', entry.changed ? 'warning' : 'neutral']">{{ entry.changed ? '变更' : '无变化' }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-else-if="activeTab === 'before'" class="audit-raw-json">
              <pre>{{ valuePreview(detailRow.beforeData) }}</pre>
            </div>
            <div v-else-if="activeTab === 'after'" class="audit-raw-json">
              <pre>{{ valuePreview(detailRow.afterData) }}</pre>
            </div>
          </template>
        </div>
        <div class="md-dialog-footer">
          <button class="md-btn" type="button" @click="closeDetail">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sys-page { display: flex; flex-direction: column; }
.sys-content { padding: 12px 0 24px; display: flex; flex-direction: column; gap: 12px; }
.sys-stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; }
.sys-stat-grid article { background: var(--md-bg-card); border: 1px solid var(--md-border); border-radius: var(--md-radius-md); padding: 14px 16px; display: flex; flex-direction: column; gap: 4px; }
.sys-stat-grid article span { font-size: 12px; color: var(--md-text-secondary); font-weight: 500; }
.sys-stat-grid article strong { font-size: 24px; color: var(--md-text-heading); font-weight: 700; font-family: var(--md-font-mono); }
.sys-tabs { margin: 0 0 0; }
.md-form-grid-actions { grid-column: 1 / -1; display: flex; gap: 8px; justify-content: flex-end; }
.table-scroll { overflow-x: auto; }
.text-center { text-align: center; }
.muted { color: var(--md-text-muted); padding: 32px 12px; }
.audit-remark-cell { max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--md-text-secondary); }
.audit-detail-info { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.audit-detail-info article { background: var(--md-bg-muted); border: 1px solid var(--md-border-soft); border-radius: var(--md-radius); padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.audit-detail-info article.wide { grid-column: 1 / -1; }
.audit-detail-info article span { color: var(--md-text-secondary); font-size: 12px; font-weight: 500; }
.audit-detail-info article strong { color: var(--md-text-heading); font-size: 13px; font-weight: 600; word-break: break-all; }
.audit-diff-table { margin-top: 12px; border: 1px solid var(--md-border); border-radius: var(--md-radius); overflow: hidden; }
.audit-diff-table .md-table tbody tr.diff-changed td { background: var(--md-warning-soft); }
.audit-diff-table code { font-family: var(--md-font-mono); font-size: 12px; color: var(--md-text); word-break: break-all; }
.audit-raw-json { margin-top: 12px; padding: 12px 14px; background: var(--md-bg-muted); border: 1px solid var(--md-border); border-radius: var(--md-radius); }
.audit-raw-json pre { margin: 0; font-family: var(--md-font-mono); font-size: 12px; color: var(--md-text); white-space: pre-wrap; word-break: break-all; max-height: 320px; overflow: auto; }
@media (max-width: 800px) { .audit-detail-info { grid-template-columns: 1fr; } }
</style>
