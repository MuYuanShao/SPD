<script setup lang="ts">
/** 专项与合规下的系统操作记录，提供日志筛选、分页和变更详情。 */
import { computed, onMounted, ref, watch } from 'vue'
import { Teleport } from 'vue'
import {
  Activity,
  AlertOctagon,
  Calendar,
  ClipboardList,
  Clock,
  Columns3,
  ChevronDown,
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
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import { formatBusinessText } from '../../utils/chineseDisplay'
import { notifyError, resolveErrorMessage } from '../../composables/useNotify'
import {
  fetchAuditLogBizTypes,
  fetchAuditLogDetail,
  fetchAuditLogOperationTypes,
  fetchAuditLogs,
  type AuditLogFacet,
  type AuditLogBizTypeFacet,
  type AuditLogRow
} from '../../api/auditLogs'

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const AUDIT_LOG_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'operatorName', label: '操作人', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 220 },
  { key: 'operationType', label: '操作类型', width: 140, visible: true, weight: 4, minWidth: 110, maxWidth: 200 },
  { key: 'bizType', label: '业务类型', width: 150, visible: true, weight: 4, minWidth: 110, maxWidth: 220 },
  { key: 'bizNo', label: '业务单据', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'bizId', label: '业务 ID', width: 140, visible: true, weight: 3, minWidth: 100, maxWidth: 220 },
  { key: 'ipAddress', label: 'IP', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'operationTime', label: '操作时间', width: 170, visible: true, weight: 4, minWidth: 140, maxWidth: 220 },
  { key: 'remark', label: '备注', width: 200, visible: true, flexible: true, idealWidth: 240, weight: 6, minWidth: 130, maxWidth: 340 },
  { key: 'actions', label: '操作', width: 110, visible: true, weight: 3, minWidth: 100, maxWidth: 180, fill: true }
]

const auditLogCols = useListColumns({
  storageKey: 'spd-audit-log-cols-v1',
  defaults: AUDIT_LOG_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「备注」列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const auditLogTableRef = auditLogCols.tableRef
const isAuditLogColVisible = (key: string) => auditLogCols.isColumnVisible(key)
const auditLogColBinding = (key: string) => auditLogCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const auditLogActionFix = useActionColumnFix()
const auditLogActionFixed = computed(() => auditLogActionFix.actionFixedBinding.value)

watch(
  [auditLogTableRef],
  () => {
    auditLogActionFix.observe(auditLogTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const auditLogColSettingsAnchorRef = ref<HTMLElement | null>(null)
const auditLogColSettingsPopRef = ref<HTMLElement | null>(null)
const auditLogColSettingsPop = useColSettingsPopover({
  open: auditLogCols.settingsOpen,
  anchorRef: auditLogColSettingsAnchorRef,
  popRef: auditLogColSettingsPopRef
})

const rows = ref<AuditLogRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

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
  try {
    const data = await fetchAuditLogs({
      ...query.value,
      page: page.value,
      size: size.value
    })
    rows.value = data.rows
    total.value = data.total

  } catch (err: any) {
    notifyError(resolveErrorMessage(err, '系统操作记录加载失败'))
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

function changePage(p: number) {
  page.value = p
  loadList()
}

function changePageSize(s: number) {
  size.value = Math.min(200, Math.max(1, Math.floor(s)))
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
    detailError.value = err?.message || '系统操作记录详情加载失败'
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
  <div class="sys-page spd-unified-list">

    <MdPageHeader
      crumb="专项与合规"
      title="系统操作记录"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadList"
    />

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
            <option v-for="f in operationTypeFacets" :key="f.operationType" :value="f.operationType">{{ formatBusinessText(f.operationType) }} ({{ f.total }})</option>
          </select>
        </label>
        <label><span>业务类型</span>
          <select v-model="query.bizType" class="sys-select">
            <option value="">全部</option>
            <option v-for="f in bizTypeFacets" :key="f.bizType" :value="f.bizType">{{ formatBusinessText(f.bizType) }} ({{ f.total }})</option>
          </select>
        </label>
        <label><span>业务单据号</span><input v-model.trim="query.bizNo" class="sys-input" placeholder="模糊查询业务单据号" @keyup.enter="onSearch" /></label>
        <label><span>关键字</span><input v-model.trim="query.keyword" class="sys-input" placeholder="备注 / IP" @keyup.enter="onSearch" /></label>
        <label><span>开始日期</span><input v-model="query.fromDate" type="date" class="sys-input" /></label>
        <label><span>结束日期</span><input v-model="query.toDate" type="date" class="sys-input" /></label>
        <div class="sys-form-actions">
          <SpdButton label="重置" tone="neutral" @click="onReset">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton>
          <SpdButton label="查询" tone="primary" @click="onSearch">
          <template #icon><Search :size="15" /></template>
        </SpdButton>
          <SpdButton label="导出当前页" tone="neutral" :disabled="!rows.length" @click="exportJson">
          <template #icon><Download :size="15" /></template>
        </SpdButton>
        </div>
      </div>
    </div>

    <!-- 请求失败统一由通用消息接口（Element Plus 消息弹层）抛出，
         不再在页面上方渲染内联错误条。 -->

    <!-- 审计日志表 -->
    <MdDataTableCard class="master-table-card" :loading="loading">
      <template #toolbar>
        <div class="ulc-col-settings">
          <SpdButton label="列设置" tone="neutral" ref="auditLogColSettingsAnchorRef"
           
           
            @click="auditLogCols.settingsOpen.value = !auditLogCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
          <SpdColumnSettings
            v-if="auditLogCols.settingsOpen.value"
            ref="auditLogColSettingsPopRef"
            :class="{
              'is-align-right': auditLogColSettingsPop.align.value === 'right',
              'is-align-up': auditLogColSettingsPop.vertical.value === 'up'
            }"
            :style="auditLogColSettingsPop.popStyle.value"
            :columns="auditLogCols.columns.value"
            :visible-column-count="auditLogCols.visibleColumnCount.value"
            @toggle="auditLogCols.setColumnVisible"
            @move="auditLogCols.moveColumn"
            @reset="auditLogCols.resetColumns()"
            @confirm="auditLogCols.settingsOpen.value = false"
          />
        </div>
      </template>
      <template #meta-extra>
        <span class="md-chip primary">共 {{ total }} 条记录</span>
      </template>

      <div class="ulc-table-wrap">
        <el-table
          ref="auditLogTableRef"
          v-loading="loading"
          :data="rows"
          row-key="auditId"
          border
          stripe
          class="ulc-table is-sticky"
          max-height="560"
          style="width: 100%"
          :empty-text="'暂无系统操作记录'"
          @header-dragend="(width: number, oldWidth: number, column: any) => auditLogCols.handleHeaderDragEnd(width, oldWidth, column)"
        >
          <el-table-column
            column-key="operatorName"
            v-if="isAuditLogColVisible('operatorName')"
            label="操作人"
            v-bind="auditLogColBinding('operatorName')"
          >
            <template #header>
              <span class="ulc-th-text">操作人</span>
            </template>
            <template #default="{ row }">
              <i class="sys-cell-icon"><User :size="13" /></i>
              <span class="ulc-strong ulc-wrap-text">{{ row.operatorName || '系统' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="operationType"
            v-if="isAuditLogColVisible('operationType')"
            label="操作类型"
            v-bind="auditLogColBinding('operationType')"
          >
            <template #header>
              <span class="ulc-th-text">操作类型</span>
            </template>
            <template #default="{ row }">
              <span :class="['sys-chip', `is-op-${operationChipType(row.operationType)}`]" :title="row.operationType">
                {{ formatBusinessText(row.operationType) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="bizType"
            v-if="isAuditLogColVisible('bizType')"
            label="业务类型"
            v-bind="auditLogColBinding('bizType')"
          >
            <template #header>
              <span class="ulc-th-text">业务类型</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text" :title="row.bizType">{{ formatBusinessText(row.bizType) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="bizNo"
            v-if="isAuditLogColVisible('bizNo')"
            label="业务单据"
            v-bind="auditLogColBinding('bizNo')"
          >
            <template #header>
              <span class="ulc-th-text">业务单据</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-strong ulc-wrap-text">{{ row.bizNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="bizId"
            v-if="isAuditLogColVisible('bizId')"
            label="业务 ID"
            v-bind="auditLogColBinding('bizId')"
          >
            <template #header>
              <span class="ulc-th-text">业务 ID</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-mono ulc-wrap-text">{{ row.bizId ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="ipAddress"
            v-if="isAuditLogColVisible('ipAddress')"
            label="IP"
            v-bind="auditLogColBinding('ipAddress')"
          >
            <template #header>
              <span class="ulc-th-text">IP</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-mono ulc-wrap-text">{{ row.ipAddress || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="operationTime"
            v-if="isAuditLogColVisible('operationTime')"
            label="操作时间"
            v-bind="auditLogColBinding('operationTime')"
          >
            <template #header>
              <span class="ulc-th-text">操作时间</span>
            </template>
            <template #default="{ row }">
              <i class="sys-cell-icon is-muted"><Clock :size="13" /></i>
              <span class="ulc-time">{{ row.operationTime || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="remark"
            v-if="isAuditLogColVisible('remark')"
            label="备注"
            v-bind="auditLogColBinding('remark')"
          >
            <template #header>
              <span class="ulc-th-text">备注</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text" :title="row.remark || ''">{{ row.remark || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="actions"

            v-if="isAuditLogColVisible('actions')"

            label="操作"

            v-bind="auditLogColBinding('actions')"
            
            align="center"
            :fixed="auditLogActionFixed"
          >
            <template #header>
              <span class="ulc-th-text">操作</span>
            </template>
            <template #default="{ row }">
              <div class="ulc-actions">
                <button class="ulc-action is-outline" type="button" @click="openDetail(row)">
                  <Eye :size="14" /> 详情
                </button>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <MdEmptyState text="暂无系统操作记录" hint="调整筛选条件或稍后重试" />
          </template>
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
          @update:size="changePageSize"
        />
      </template>
    </MdDataTableCard>

    <!-- 详情弹窗 -->
    <Teleport to="body">
    <div v-if="detailOpen" class="sys-dialog-mask" @click.self="closeDetail">
      <div class="sys-dialog sys-dialog-xl" role="dialog" aria-modal="true">
        <div class="sys-dialog-header">
          <h3>系统操作记录详情 · #{{ detailRow?.auditId }}</h3>
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" @click="closeDetail">
          <template #icon><X :size="16" /></template>
        </SpdButton>
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
                    {{ formatBusinessText(detailRow.operationType) }}
                  </span>
                </div>
              </article>
              <article>
                <i class="sys-detail-icon"><Database :size="14" /></i>
                <div><span>业务类型</span><strong>{{ formatBusinessText(detailRow.bizType) }}</strong></div>
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
          <SpdButton label="关闭" tone="neutral" @click="closeDetail"></SpdButton>
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
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.sys-kpi-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--spd-p-shadow-2);
}

.sys-kpi-item.is-blue {
  background: linear-gradient(180deg, var(--spd-p-border) 0%, var(--spd-p-info-border) 100%);
  border-color: var(--spd-p-info-border);
}
.sys-kpi-item.is-green {
  background: linear-gradient(180deg, var(--spd-p-success-bg) 0%, var(--spd-p-success-border) 100%);
  border-color: var(--spd-p-success);
}
.sys-kpi-item.is-orange {
  background: linear-gradient(180deg, var(--spd-p-warning-bg) 0%, var(--spd-p-warning-border) 100%);
  border-color: var(--spd-p-warning-border);
}
.sys-kpi-item.is-purple {
  background: linear-gradient(180deg, var(--spd-p-info-bg) 0%, var(--spd-p-info-bg) 100%);
  border-color: var(--spd-p-info-border);
}

.sys-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--spd-p-bg) 65%, transparent);
  flex-shrink: 0;
  border: 1px solid color-mix(in srgb, var(--spd-p-bg) 90%, transparent);
}

.sys-kpi-item.is-blue   .sys-kpi-icon { color: var(--spd-p-info-strong); }
.sys-kpi-item.is-green  .sys-kpi-icon { color: var(--spd-p-success-strong); }
.sys-kpi-item.is-orange .sys-kpi-icon { color: var(--spd-p-warning-text); }
.sys-kpi-item.is-purple .sys-kpi-icon { color: var(--spd-p-primary); }

.sys-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.sys-kpi-label { font-size: var(--spd-font-size-sm); font-weight: 500; opacity: 0.85; }
.sys-kpi-value {
  font-size: var(--spd-font-size-2xl);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
  font-variant-numeric: tabular-nums;
}
.sys-kpi-hint { font-size: var(--spd-font-size-xs); font-weight: 400; line-height: var(--spd-lh-tight); opacity: 0.75; }

.sys-kpi-item.is-blue   .sys-kpi-label,
.sys-kpi-item.is-blue   .sys-kpi-value,
.sys-kpi-item.is-blue   .sys-kpi-hint { color: var(--spd-p-info-strong); }
.sys-kpi-item.is-green  .sys-kpi-label,
.sys-kpi-item.is-green  .sys-kpi-value,
.sys-kpi-item.is-green  .sys-kpi-hint { color: var(--spd-p-primary-strong); }
.sys-kpi-item.is-orange .sys-kpi-label,
.sys-kpi-item.is-orange .sys-kpi-value,
.sys-kpi-item.is-orange .sys-kpi-hint { color: var(--spd-p-warning-strong); }
.sys-kpi-item.is-purple .sys-kpi-label,
.sys-kpi-item.is-purple .sys-kpi-value,
.sys-kpi-item.is-purple .sys-kpi-hint { color: var(--spd-p-primary-strong); }

/* ---------- 查询条件卡 ---------- */
.sys-form-section {
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border);
  border-radius: var(--spd-radius-md, 10px);
  padding: 18px;
  box-shadow: var(--spd-p-shadow-1);
}

.sys-form-section-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--spd-p-border);
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
  background: var(--spd-p-success-bg);
  color: var(--spd-p-success-strong);
}

.sys-form-section-header h4 {
  margin: 0;
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-form-section-header p {
  margin: 3px 0 0;
  font-size: var(--spd-font-size-sm);
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
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  color: var(--spd-p-text-secondary);
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
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  padding: 0 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  transition: border-color 0.15s;
}

.sys-input:focus,
.sys-select:focus { outline: none; border-color: var(--spd-p-border-strong); }

/* ---------- 按钮 ---------- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 32px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn:hover:not(:disabled) { background: var(--spd-p-bg-muted); border-color: var(--spd-p-text-muted); color: var(--spd-p-text-strong); }
.btn:disabled { opacity: 0.55; cursor: not-allowed; }

.btn-primary {
  background: linear-gradient(180deg, var(--spd-p-success) 0%, var(--spd-p-success) 100%);
  border-color: var(--spd-p-success-strong);
  color: var(--spd-p-bg);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(180deg, var(--spd-p-success) 0%, var(--spd-p-success-strong) 100%);
  border-color: var(--spd-p-primary-strong);
  color: var(--spd-p-bg);
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
  color: var(--spd-p-text-secondary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.btn-icon:hover { background: var(--spd-p-bg-muted); color: var(--spd-p-text-strong); }

.btn-text {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: var(--spd-p-success);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.15s;
}

.btn-text:hover { background: var(--spd-p-success-bg); }

/* ---------- 消息 / 错误 ---------- */
.sys-error {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: var(--spd-p-danger-bg);
  border: 1px solid var(--spd-p-danger-border);
  border-radius: 8px;
  color: var(--spd-p-danger-strong);
  font-size: var(--spd-font-size-sm);
  box-shadow: var(--spd-p-shadow-1);
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
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
  border-radius: 6px 6px 0 0;
  white-space: nowrap;
}

.sys-tab:hover { color: var(--spd-primary); background: var(--spd-p-primary-bg); }
.sys-tab.active {
  color: var(--spd-primary);
  border-bottom-color: var(--spd-primary);
  background: var(--spd-p-primary-bg);
  font-weight: 600;
}

/* ---------- 表格 ---------- */
.sys-table-card {
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border);
  border-radius: var(--spd-radius-md, 10px);
  overflow: hidden;
  box-shadow: var(--spd-p-shadow-1);
  display: flex;
  flex-direction: column;
}

.sys-table-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
  gap: 12px;
}

.sys-table-card-header h4 {
  margin: 0;
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-table-card-meta {
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-secondary);
}

.sys-table-card-meta strong { color: var(--spd-p-success-strong); font-weight: 700; }

.sys-table-scroll { overflow-x: auto; }

.sys-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--spd-font-size-base);
}

.sys-table thead tr {
  background: linear-gradient(180deg, var(--spd-p-primary-bg) 0%, var(--spd-p-success-bg) 100%);
  border-bottom: 1px solid var(--spd-p-success);
}

.sys-table th {
  color: var(--spd-p-primary-strong);
  font-weight: 700;
  font-size: var(--spd-font-size-table-head);
  text-align: left;
  padding: 10px 12px;
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.sys-table td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--spd-p-text-strong);
  color: var(--spd-p-text-secondary);
  vertical-align: middle;
}

.sys-table tbody tr:nth-child(even) td { background: var(--spd-p-bg); }
.sys-table tbody tr:hover td { background: var(--spd-p-primary-bg) !important; }
.sys-table tbody tr:last-child td { border-bottom: none; }

/* 单元格前小图标 */
.sys-cell-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  /* 浅底深字（此前误用近黑底配深灰字，图标几乎不可见） */
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-secondary);
  margin-right: 6px;
  vertical-align: -3px;
}

.sys-cell-icon.is-muted { background: transparent; color: var(--spd-p-text-muted); }

.text-center { text-align: center; }
.muted { color: var(--spd-p-text-muted); padding: 32px 16px; font-size: var(--spd-font-size-base); }

.audit-remark-cell {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--spd-p-text-secondary);
}

/* ---------- 通用 tag / chip / badge ---------- */
.sys-tag.is-code {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  padding: 1px 8px;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  white-space: nowrap;
  color: var(--spd-p-text-secondary);
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  background: var(--spd-p-bg-muted);
  font-family: var(--spd-font-family-mono);
}

/* 操作类型 chip（语义色） */
.sys-chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid;
  border-radius: 6px;
  padding: 2px 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  white-space: nowrap;
}

.sys-chip.is-op-create  { color: var(--spd-p-primary-strong); background: var(--spd-p-success-bg); border-color: var(--spd-p-success); }
.sys-chip.is-op-update  { color: var(--spd-p-info-strong); background: var(--spd-p-border); border-color: var(--spd-p-info-border); }
.sys-chip.is-op-delete  { color: var(--spd-p-danger-strong); background: var(--spd-p-danger-bg); border-color: var(--spd-p-danger-border); }
.sys-chip.is-op-export  { color: var(--spd-p-primary); background: var(--spd-p-info-bg); border-color: var(--spd-p-info-border); }
.sys-chip.is-op-import  { color: var(--spd-p-warning-text); background: var(--spd-p-warning-bg); border-color: var(--spd-p-warning-border); }
.sys-chip.is-op-login   { color: var(--spd-p-success-text); background: var(--spd-p-info-bg); border-color: var(--spd-p-success); }
.sys-chip.is-op-logout  { color: var(--spd-p-text-secondary); background: var(--spd-p-bg-muted); border-color: var(--spd-p-border-strong); }
.sys-chip.is-op-query   { color: var(--spd-p-primary); background: var(--spd-p-primary-bg); border-color: var(--spd-p-primary-hover); }
.sys-chip.is-op-approve { color: var(--spd-p-danger-strong); background: var(--spd-p-danger-bg); border-color: var(--spd-p-danger-border); }
.sys-chip.is-op-default { color: var(--spd-p-text-secondary); background: var(--spd-p-bg-muted); border-color: var(--spd-p-border-strong); }

.sys-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.sys-badge.is-changed {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
  border: 1px solid var(--spd-p-danger-border);
}

.sys-badge.is-unchanged {
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-border);
}

/* ---------- 分页 ---------- */
.sys-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-secondary);
  flex-wrap: wrap;
  gap: 12px;
}

.sys-pagination strong { color: var(--spd-p-success-strong); font-weight: 700; }

/* ---------- 详情弹窗 ---------- */
.sys-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--spd-p-text-strong) 50%, transparent);
  backdrop-filter: blur(2px);
}

.sys-dialog {
  background: var(--spd-p-bg);
  border-radius: var(--spd-radius-lg, 14px);
  box-shadow: var(--spd-p-shadow-dialog);
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
  border-bottom: 1px solid var(--spd-p-border);
}

.sys-dialog-header h3 {
  margin: 0;
  font-size: var(--spd-font-size-lg);
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
  border-top: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
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
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
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
  background: var(--spd-p-success-bg);
  color: var(--spd-p-success-strong);
  flex-shrink: 0;
}

.sys-detail-grid article > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.sys-detail-grid article > div span {
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-secondary);
  font-weight: 600;
}

.sys-detail-grid article > div strong {
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text);
  font-weight: 600;
  word-break: break-all;
}

/* 变更对比 / JSON */
.sys-diff-card {
  margin-top: 10px;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  overflow: hidden;
}

.sys-diff-card .sys-table tbody tr.is-changed td { background: var(--spd-p-warning-bg); }
.sys-diff-card .sys-table tbody tr.is-changed:hover td { background: var(--spd-p-warning-border) !important; }

.sys-diff-card code {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-secondary);
  word-break: break-all;
  background: color-mix(in srgb, var(--spd-p-bg) 70%, transparent);
  padding: 1px 5px;
  border-radius: 3px;
}

.sys-json {
  margin-top: 10px;
  padding: 14px;
  background: var(--spd-p-text-strong);
  border-radius: 8px;
  border: 1px solid var(--spd-p-bg-muted);
  max-height: 360px;
  overflow: auto;
}

.sys-json pre {
  margin: 0;
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
  line-height: var(--spd-lh-base);
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
