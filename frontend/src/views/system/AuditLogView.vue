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
import { Teleport } from 'vue'
import {
  Activity,
  AlertOctagon,
  Calendar,
  ClipboardList,
  Clock,
  Database,
  Download,
  Eye,
  FileSearch,
  Filter,
  Globe,
  Hash,
  RefreshCw,
  Save,
  Search,
  ServerCog,
  Tag,
  User,
  X
} from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import {
  fetchAuditLogBizTypes,
  fetchAuditLogDetail,
  fetchAuditLogOperationTypes,
  fetchAuditLogs,
  type AuditLogFacet,
  type AuditLogBizTypeFacet,
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
const bizTypeFacets = ref<AuditLogBizTypeFacet[]>([])

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
  { icon: 'list',  theme: 'blue',   label: '总记录',     value: summary.value.total,            hint: '已记录日志' },
  { icon: 'today', theme: 'green',  label: '今日操作',   value: summary.value.today,            hint: '今天产生' },
  { icon: 'user',  theme: 'orange', label: '涉及人员',   value: summary.value.distinctOperators,hint: '操作人' },
  { icon: 'biz',   theme: 'purple', label: '业务类型',   value: summary.value.distinctBizTypes, hint: '业务模块' }
])

function operationChipType(op?: string): 'create' | 'update' | 'delete' | 'export' | 'import' | 'login' | 'logout' | 'query' | 'approve' | 'default' {
  const o = (op ?? '').toUpperCase()
  if (/CREATE|新增|添加|创建/.test(o)) return 'create'
  if (/UPDATE|修改|编辑|更新/.test(o)) return 'update'
  if (/DELETE|REMOVE|删除|移除/.test(o)) return 'delete'
  if (/EXPORT|导出/.test(o)) return 'export'
  if (/IMPORT|导入/.test(o)) return 'import'
  if (/LOGIN|登录|登入/.test(o)) return 'login'
  if (/LOGOUT|登出|注销/.test(o)) return 'logout'
  if (/QUERY|查询|检索/.test(o)) return 'query'
  if (/APPROVE|REVIEW|审批|审核/.test(o)) return 'approve'
  return 'default'
}

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
    if (data.summary) summary.value = {
      total: data.summary.total ?? 0,
      today: data.summary.today ?? 0,
      distinctOperators: data.summary.distinctOperators ?? 0,
      distinctBizTypes: data.summary.distinctBizTypes ?? 0
    }
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
  <div class="sys-page">

    <MdPageHeader
      title="审计日志"
      description="查看系统关键业务的操作留痕、变更前后数据与操作上下文，支持多条件查询与导出。"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadList"
    />

    <!-- 顶部 4 张主题色 KPI 卡 -->
    <section class="sys-kpi-card" aria-label="审计日志统计">
      <div v-for="item in summaryStats" :key="item.label" class="sys-kpi-item" :class="`is-${item.theme}`">
        <i class="sys-kpi-icon" :class="`is-${item.icon}`">
          <ClipboardList v-if="item.icon === 'list'" :size="18" />
          <Clock v-else-if="item.icon === 'today'" :size="18" />
          <User v-else-if="item.icon === 'user'" :size="18" />
          <Database v-else-if="item.icon === 'biz'" :size="18" />
          <Activity v-else :size="18" />
        </i>
        <div>
          <span class="sys-kpi-label">{{ item.label }}</span>
          <strong class="sys-kpi-value">{{ item.value }}</strong>
          <span class="sys-kpi-hint">{{ item.hint }}</span>
        </div>
      </div>
    </section>

    <!-- 查询条件卡片 -->
    <div class="sys-form-section">
      <div class="sys-form-section-header">
        <i class="sys-form-section-icon is-green"><Filter :size="15" /></i>
        <div>
          <h4>查询条件</h4>
          <p>按操作人 / 类型 / 时间范围检索审计记录</p>
        </div>
      </div>
      <div class="sys-form-grid">
        <label><span>操作人</span><input v-model.trim="query.operatorName" class="sys-input" placeholder="模糊查询操作人姓名" @keyup.enter="onSearch" /></label>
        <label><span>操作类型</span>
          <select v-model="query.operationType" class="sys-select">
            <option value="">全部</option>
            <option v-for="f in operationTypeFacets" :key="f.operationType" :value="f.operationType">{{ f.operationType }} ({{ f.total }})</option>
          </select>
        </label>
        <label><span>业务类型</span>
          <select v-model="query.bizType" class="sys-select">
            <option value="">全部</option>
            <option v-for="f in bizTypeFacets" :key="f.bizType" :value="f.bizType">{{ f.bizType }} ({{ f.total }})</option>
          </select>
        </label>
        <label><span>业务单据号</span><input v-model.trim="query.bizNo" class="sys-input" placeholder="模糊查询业务单据号" @keyup.enter="onSearch" /></label>
        <label><span>关键字</span><input v-model.trim="query.keyword" class="sys-input" placeholder="备注 / IP" @keyup.enter="onSearch" /></label>
        <label><span>开始日期</span><input v-model="query.fromDate" type="date" class="sys-input" /></label>
        <label><span>结束日期</span><input v-model="query.toDate" type="date" class="sys-input" /></label>
        <div class="sys-form-actions">
          <button class="btn" type="button" @click="onReset"><RefreshCw :size="15" /> 重置</button>
          <button class="btn btn-primary" type="button" @click="onSearch"><Search :size="15" /> 查询</button>
          <button class="btn" type="button" :disabled="!rows.length" @click="exportJson"><Download :size="15" /> 导出当前页</button>
        </div>
      </div>
    </div>

    <div v-if="error" class="sys-error" role="alert">{{ error }}</div>

    <!-- 审计日志表 -->
    <div class="sys-table-card">
      <div class="sys-table-card-header">
        <h4>审计日志列表</h4>
        <span class="sys-table-card-meta">共 <strong>{{ total }}</strong> 条记录</span>
      </div>
      <div class="sys-table-scroll">
        <table class="sys-table">
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
              <td>
                <i class="sys-cell-icon"><User :size="13" /></i>
                <span>{{ row.operatorName || '系统' }}</span>
              </td>
              <td>
                <span :class="['sys-chip', `is-op-${operationChipType(row.operationType)}`]">
                  {{ row.operationType }}
                </span>
              </td>
              <td>{{ row.bizType }}</td>
              <td>{{ row.bizNo || '-' }}</td>
              <td><span class="sys-tag is-code">{{ row.bizId ?? '-' }}</span></td>
              <td><span class="sys-tag is-code">{{ row.ipAddress || '-' }}</span></td>
              <td>
                <i class="sys-cell-icon is-muted"><Clock :size="13" /></i>
                <span>{{ row.operationTime || '-' }}</span>
              </td>
              <td class="audit-remark-cell" :title="row.remark || ''">{{ row.remark || '-' }}</td>
              <td>
                <button class="btn-text" type="button" @click="openDetail(row)">
                  <Eye :size="14" /> 详情
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="total > 0" class="sys-pagination">
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

    <!-- 详情弹窗 -->
    <Teleport to="body">
    <div v-if="detailOpen" class="sys-dialog-mask" @click.self="closeDetail">
      <div class="sys-dialog sys-dialog-xl" role="dialog" aria-modal="true">
        <div class="sys-dialog-header">
          <h3>审计日志详情 · #{{ detailRow?.auditId }}</h3>
          <button class="btn-icon" type="button" @click="closeDetail"><X :size="16" /></button>
        </div>
        <div class="sys-dialog-body">
          <div v-if="detailLoading" class="text-center muted">正在加载详情...</div>
          <div v-else-if="detailError" class="sys-error" role="alert">{{ detailError }}</div>
          <template v-else-if="detailRow">
            <div class="sys-detail-grid">
              <article>
                <i class="sys-detail-icon"><User :size="14" /></i>
                <div><span>操作人</span><strong>{{ detailRow.operatorName || '系统' }}</strong></div>
              </article>
              <article>
                <i class="sys-detail-icon"><Tag :size="14" /></i>
                <div><span>操作类型</span>
                  <span :class="['sys-chip', `is-op-${operationChipType(detailRow.operationType)}`]">
                    {{ detailRow.operationType }}
                  </span>
                </div>
              </article>
              <article>
                <i class="sys-detail-icon"><Database :size="14" /></i>
                <div><span>业务类型</span><strong>{{ detailRow.bizType }}</strong></div>
              </article>
              <article>
                <i class="sys-detail-icon"><Hash :size="14" /></i>
                <div><span>业务单据号</span><strong>{{ detailRow.bizNo || '-' }}</strong></div>
              </article>
              <article>
                <i class="sys-detail-icon"><Hash :size="14" /></i>
                <div><span>业务 ID</span><strong>{{ detailRow.bizId ?? '-' }}</strong></div>
              </article>
              <article>
                <i class="sys-detail-icon"><Globe :size="14" /></i>
                <div><span>IP 地址</span><strong>{{ detailRow.ipAddress || '-' }}</strong></div>
              </article>
              <article>
                <i class="sys-detail-icon"><Calendar :size="14" /></i>
                <div><span>操作时间</span><strong>{{ detailRow.operationTime || '-' }}</strong></div>
              </article>
              <article class="wide">
                <i class="sys-detail-icon"><FileSearch :size="14" /></i>
                <div><span>备注</span><strong>{{ detailRow.remark || '-' }}</strong></div>
              </article>
            </div>

            <div v-if="showBeforeAfter" class="sys-tabs" style="margin:14px 0 0">
              <button type="button" :class="['sys-tab', { active: activeTab === 'overview' }]" @click="activeTab = 'overview'">变更对比</button>
              <button type="button" :class="['sys-tab', { active: activeTab === 'before' }]" @click="activeTab = 'before'">变更前</button>
              <button type="button" :class="['sys-tab', { active: activeTab === 'after' }]" @click="activeTab = 'after'">变更后</button>
            </div>

            <div v-if="showBeforeAfter && activeTab === 'overview'" class="sys-diff-card">
              <table class="sys-table">
                <thead>
                  <tr>
                    <th style="width:160px">字段</th>
                    <th>变更前</th>
                    <th>变更后</th>
                    <th style="width:90px">状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="entry in beforeAfterEntries" :key="entry.key" :class="{ 'is-changed': entry.changed }">
                    <td><code>{{ entry.key }}</code></td>
                    <td><code>{{ valuePreview(entry.before) }}</code></td>
                    <td><code>{{ valuePreview(entry.after) }}</code></td>
                    <td>
                      <span :class="['sys-badge', entry.changed ? 'is-changed' : 'is-unchanged']">
                        {{ entry.changed ? '变更' : '无变化' }}
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-else-if="activeTab === 'before'" class="sys-json">
              <pre>{{ valuePreview(detailRow.beforeData) }}</pre>
            </div>
            <div v-else-if="activeTab === 'after'" class="sys-json">
              <pre>{{ valuePreview(detailRow.afterData) }}</pre>
            </div>
          </template>
        </div>
        <div class="sys-dialog-footer">
          <button class="btn" type="button" @click="closeDetail">关闭</button>
        </div>
      </div>
    </div>
    </Teleport>

  </div>
</template>

<style scoped>
/* =====================================================================
   系统管理 · 审计日志
   ---------------------------------------------------------------------
   设计目标：
   1. 顶部 4 张主题色 KPI 卡。
   2. 查询条件卡：绿底 Filter 图标 + section-title 风格。
   3. 表格：浅绿渐变表头；操作类型彩色 chip（按语义分配颜色）。
   4. 详情弹窗：字段小卡 + 绿色 tab 切分变更对比 / 前 / 后。
   5. 变更对比：变更行用淡橙底 + "变更"badge，未变更"无变化"灰色 badge。
   ===================================================================== */
.sys-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 16px 20px 24px;
  box-sizing: border-box;
}

/* ---------- 顶部 4 项 KPI ---------- */
.sys-kpi-card {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.sys-kpi-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid;
  border-radius: var(--spd-radius-md, 10px);
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.sys-kpi-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
}

.sys-kpi-item.is-blue {
  background: linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%);
  border-color: #93c5fd;
}
.sys-kpi-item.is-green {
  background: linear-gradient(180deg, #d1fae5 0%, #a7f3d0 100%);
  border-color: #6ee7b7;
}
.sys-kpi-item.is-orange {
  background: linear-gradient(180deg, #fef3c7 0%, #fde68a 100%);
  border-color: #fcd34d;
}
.sys-kpi-item.is-purple {
  background: linear-gradient(180deg, #ede9fe 0%, #ddd6fe 100%);
  border-color: #c4b5fd;
}

.sys-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.65);
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.sys-kpi-item.is-blue   .sys-kpi-icon { color: #1d4ed8; }
.sys-kpi-item.is-green  .sys-kpi-icon { color: #047857; }
.sys-kpi-item.is-orange .sys-kpi-icon { color: #b45309; }
.sys-kpi-item.is-purple .sys-kpi-icon { color: #6d28d9; }

.sys-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.sys-kpi-label { font-size: 12px; font-weight: 500; opacity: 0.85; }
.sys-kpi-value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.sys-kpi-hint { font-size: 11px; font-weight: 400; line-height: 1.3; opacity: 0.75; }

.sys-kpi-item.is-blue   .sys-kpi-label,
.sys-kpi-item.is-blue   .sys-kpi-value,
.sys-kpi-item.is-blue   .sys-kpi-hint { color: #1e3a8a; }
.sys-kpi-item.is-green  .sys-kpi-label,
.sys-kpi-item.is-green  .sys-kpi-value,
.sys-kpi-item.is-green  .sys-kpi-hint { color: #065f46; }
.sys-kpi-item.is-orange .sys-kpi-label,
.sys-kpi-item.is-orange .sys-kpi-value,
.sys-kpi-item.is-orange .sys-kpi-hint { color: #78350f; }
.sys-kpi-item.is-purple .sys-kpi-label,
.sys-kpi-item.is-purple .sys-kpi-value,
.sys-kpi-item.is-purple .sys-kpi-hint { color: #4c1d95; }

/* ---------- 查询条件卡 ---------- */
.sys-form-section {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  padding: 18px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.sys-form-section-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.sys-form-section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  flex-shrink: 0;
}

.sys-form-section-icon.is-green {
  background: #d1fae5;
  color: #047857;
}

.sys-form-section-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-form-section-header p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--spd-text-tertiary);
}

.sys-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px 16px;
}

.sys-form-grid label {
  display: grid;
  gap: 5px;
  font-size: 12px;
  font-weight: 700;
  color: #4b5b66;
}

.sys-form-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 4px;
}

.sys-input,
.sys-select {
  width: 100%;
  min-height: 34px;
  height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #102033;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 500;
  transition: border-color 0.15s;
}

.sys-input:focus,
.sys-select:focus { outline: none; border-color: #cbd5e1; }

/* ---------- 按钮 ---------- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 32px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn:hover:not(:disabled) { background: #f8fafc; border-color: #94a3b8; color: #1e293b; }
.btn:disabled { opacity: 0.55; cursor: not-allowed; }

.btn-primary {
  background: linear-gradient(180deg, #10b981 0%, #059669 100%);
  border-color: #047857;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(180deg, #059669 0%, #047857 100%);
  border-color: #065f46;
  color: #fff;
}

.btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.btn-icon:hover { background: #f1f5f9; color: #334155; }

.btn-text {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: #10b981;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.15s;
}

.btn-text:hover { background: #d1fae5; }

/* ---------- 消息 / 错误 ---------- */
.sys-error {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: #fee2e2;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  color: #991b1b;
  font-size: 13px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}

/* ---------- Tab ---------- */
.sys-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  padding: 0 4px;
}

.sys-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--spd-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
  border-radius: 6px 6px 0 0;
  white-space: nowrap;
}

.sys-tab:hover { color: var(--spd-primary); background: #f0fdf9; }
.sys-tab.active {
  color: var(--spd-primary);
  border-bottom-color: var(--spd-primary);
  background: #f0fdf9;
  font-weight: 600;
}

/* ---------- 表格 ---------- */
.sys-table-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
}

.sys-table-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafdfb;
  gap: 12px;
}

.sys-table-card-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-table-card-meta {
  font-size: 12px;
  color: #64748b;
}

.sys-table-card-meta strong { color: #047857; font-weight: 700; }

.sys-table-scroll { overflow-x: auto; }

.sys-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.sys-table thead tr {
  background: linear-gradient(180deg, #ecfdf5 0%, #d1fae5 100%);
  border-bottom: 1px solid #6ee7b7;
}

.sys-table th {
  color: #065f46;
  font-weight: 700;
  font-size: 12px;
  text-align: left;
  padding: 10px 12px;
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.sys-table td {
  padding: 8px 12px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
  vertical-align: middle;
}

.sys-table tbody tr:nth-child(even) td { background: #fafdfb; }
.sys-table tbody tr:hover td { background: #ecfdf5 !important; }
.sys-table tbody tr:last-child td { border-bottom: none; }

/* 单元格前小图标 */
.sys-cell-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #475569;
  margin-right: 6px;
  vertical-align: -3px;
}

.sys-cell-icon.is-muted { background: transparent; color: #94a3b8; }

.text-center { text-align: center; }
.muted { color: #94a3b8; padding: 32px 16px; font-size: 13px; }

.audit-remark-cell {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #64748b;
}

/* ---------- 通用 tag / chip / badge ---------- */
.sys-tag.is-code {
  display: inline-flex;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1px 8px;
  font-size: 11.5px;
  font-weight: 500;
  white-space: nowrap;
  color: #334155;
  background: #f1f5f9;
  font-family: var(--spd-font-mono), monospace;
}

/* 操作类型 chip（语义色） */
.sys-chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid;
  border-radius: 6px;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.sys-chip.is-op-create  { color: #065f46; background: #d1fae5; border-color: #6ee7b7; }
.sys-chip.is-op-update  { color: #1d4ed8; background: #dbeafe; border-color: #93c5fd; }
.sys-chip.is-op-delete  { color: #991b1b; background: #fee2e2; border-color: #fca5a5; }
.sys-chip.is-op-export  { color: #6d28d9; background: #ede9fe; border-color: #c4b5fd; }
.sys-chip.is-op-import  { color: #b45309; background: #fef3c7; border-color: #fcd34d; }
.sys-chip.is-op-login   { color: #0e7490; background: #cffafe; border-color: #67e8f9; }
.sys-chip.is-op-logout  { color: #475569; background: #f1f5f9; border-color: #cbd5e1; }
.sys-chip.is-op-query   { color: #0f766e; background: #ccfbf1; border-color: #5eead4; }
.sys-chip.is-op-approve { color: #be123c; background: #ffe4e6; border-color: #fda4af; }
.sys-chip.is-op-default { color: #475569; background: #f1f5f9; border-color: #cbd5e1; }

.sys-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 600;
}

.sys-badge.is-changed {
  color: #991b1b;
  background: #fee2e2;
  border: 1px solid #fca5a5;
}

.sys-badge.is-unchanged {
  color: #64748b;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

/* ---------- 分页 ---------- */
.sys-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid #e2e8f0;
  background: #fafdfb;
  font-size: 13px;
  color: #64748b;
  flex-wrap: wrap;
  gap: 12px;
}

.sys-pagination strong { color: #047857; font-weight: 700; }

/* ---------- 详情弹窗 ---------- */
.sys-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(2px);
}

.sys-dialog {
  background: #fff;
  border-radius: var(--spd-radius-lg, 14px);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.2);
  width: 100%;
  max-width: 600px;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: dialog-in 0.2s ease;
}

.sys-dialog.sys-dialog-xl { max-width: 880px; }

@keyframes dialog-in {
  from { opacity: 0; transform: scale(0.95) translateY(-8px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.sys-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
}

.sys-dialog-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-dialog-body {
  padding: 18px 20px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.sys-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 20px;
  border-top: 1px solid #e2e8f0;
  background: #fafdfb;
}

/* 详情信息网格 */
.sys-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.sys-detail-grid article {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fafdfb;
  min-width: 0;
}

.sys-detail-grid article.wide { grid-column: 1 / -1; }

.sys-detail-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #d1fae5;
  color: #047857;
  flex-shrink: 0;
}

.sys-detail-grid article > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.sys-detail-grid article > div span {
  font-size: 11.5px;
  color: #64748b;
  font-weight: 600;
}

.sys-detail-grid article > div strong {
  font-size: 13px;
  color: #102033;
  font-weight: 600;
  word-break: break-all;
}

/* 变更对比 / JSON */
.sys-diff-card {
  margin-top: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.sys-diff-card .sys-table tbody tr.is-changed td { background: #fef3c7; }
.sys-diff-card .sys-table tbody tr.is-changed:hover td { background: #fde68a !important; }

.sys-diff-card code {
  font-family: var(--spd-font-mono), monospace;
  font-size: 12px;
  color: #334155;
  word-break: break-all;
  background: rgba(255, 255, 255, 0.7);
  padding: 1px 5px;
  border-radius: 3px;
}

.sys-json {
  margin-top: 10px;
  padding: 14px;
  background: #0f172a;
  border-radius: 8px;
  border: 1px solid #1e293b;
  max-height: 360px;
  overflow: auto;
}

.sys-json pre {
  margin: 0;
  font-family: var(--spd-font-mono), monospace;
  font-size: 12px;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .sys-kpi-card { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .sys-form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 800px) {
  .sys-detail-grid { grid-template-columns: 1fr; }
  .sys-pagination { flex-direction: column; align-items: flex-start; }
}
@media (max-width: 640px) {
  .sys-kpi-card { grid-template-columns: 1fr; }
  .sys-form-grid { grid-template-columns: 1fr; }
  .sys-page { padding: 12px; }
}
</style>
