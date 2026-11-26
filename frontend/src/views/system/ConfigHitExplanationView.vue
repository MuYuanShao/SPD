<script setup lang="ts">
/**
 * 配置命中说明（系统管理 / 配置命中说明）
 * ---------------------------------------------------------------------
 * 造型与系统管理其他页面保持一致：
 *   - MdPageHeader（无 actions 按钮）
 *   - 4 张主题色 KPI 卡
 *   - 查询条件卡：Filter 图标 + section-title
 *   - 命中结果：左右双面板（最终配置 / 优先级规则）+ 候选链路表
 *   - 命中行：浅绿底；被覆盖行：灰底
 */
import { computed, onMounted, ref, watch } from 'vue'
import {
  Activity,
  AlertCircle,
  ArrowRight,
  Award,
  BookOpen,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clock,
  Columns3,
  Database,
  FileSearch,
  Filter,
  GitBranch,
  Globe,
  Hash,
  History,
  Info,
  Layers,
  Lightbulb,
  RefreshCw,
  Save,
  Search,
  Settings,
  Shield,
  SlidersHorizontal,
  Tag,
  X
} from '@lucide/vue'
import { fetchConfigHitExplanation, fetchConfigKeyOptions, type ConfigHitExplanation, type ConfigKeyOption } from '../../api/systemConfig'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const CONFIG_HIT_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'hit', label: '命中', width: 110, visible: true, weight: 3, minWidth: 95, maxWidth: 150 },
  { key: 'priority', label: '优先级', width: 90, visible: true, weight: 2, minWidth: 80, maxWidth: 130 },
  { key: 'sourceLevel', label: '来源层级', width: 120, visible: true, weight: 3, minWidth: 95, maxWidth: 180 },
  { key: 'configId', label: '配置编号', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 210 },
  { key: 'configName', label: '配置名称', width: 190, visible: true, flexible: true, idealWidth: 230, weight: 6, minWidth: 130, maxWidth: 320 },
  { key: 'scope', label: '适用对象', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 240 },
  { key: 'configValue', label: '配置值', width: 200, visible: true, flexible: true, idealWidth: 260, weight: 7, minWidth: 130, maxWidth: 380 },
  { key: 'effectiveTime', label: '生效时间', width: 160, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'expireTime', label: '失效时间', width: 160, visible: true, weight: 3, minWidth: 130, maxWidth: 210 },
  { key: 'riskLevel', label: '风险', width: 90, visible: true, weight: 2, minWidth: 80, maxWidth: 130 },
  { key: 'approvalStatus', label: '匹配状态', width: 110, visible: true, weight: 3, minWidth: 95, maxWidth: 160 },
  { key: 'matchReason', label: '匹配原因', width: 200, visible: true, flexible: true, idealWidth: 260, weight: 7, minWidth: 130, maxWidth: 380, fill: true }
]

const configHitCols = useListColumns({
  storageKey: 'spd-config-hit-cols-v1',
  defaults: CONFIG_HIT_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「配置值 / 匹配原因」等长文本列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const configHitTableRef = configHitCols.tableRef
const isConfigHitColVisible = (key: string) => configHitCols.isColumnVisible(key)
const configHitColBinding = (key: string) => configHitCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const configHitColSettingsAnchorRef = ref<HTMLElement | null>(null)
const configHitColSettingsPopRef = ref<HTMLElement | null>(null)
const configHitColSettingsPop = useColSettingsPopover({
  open: configHitCols.settingsOpen,
  anchorRef: configHitColSettingsAnchorRef,
  popRef: configHitColSettingsPopRef
})

// ── 查询上下文字段 ──────────────────────────────────────────────────────────
interface ConfigType {
  code: string
  label: string
}

const configTypes: ConfigType[] = [
  { code: 'workflow',  label: '业务流程配置' },
  { code: 'permission', label: '权限配置'   },
  { code: 'parameter', label: '参数配置'   },
  { code: 'base',      label: '基础配置'   },
  { code: 'audit',     label: '日志审计'   },
  { code: 'data',      label: '数据管理'   },
  { code: 'integration', label: '系统集成'  }
]

const query = ref({
  businessPage: '采购订单',
  configType: 'workflow',
  configKey: 'approval.purchase.order',
  userId: '',
  moduleId: 'purchase_order',
  warehouseId: '',
  departmentId: '',
  campusId: '',
  hospitalId: 'default',
  tenantId: ''
})

// ── 数据 ─────────────────────────────────────────────────────────────────────
const explanation = ref<ConfigHitExplanation | null>(null)
const loading = ref(false)
const keysLoading = ref(false)
const keyOptions = ref<ConfigKeyOption[]>([])
let explanationRequest = 0
let keyRequest = 0
const error = ref('')

// ── 辅助函数 ───────────────────────────────────────────────────────────────────
function labelFor(code: string) {
  return configTypes.find(t => t.code === code)?.label ?? code
}

function riskChipType(level?: string): 'high' | 'medium' | 'low' | 'default' {
  if (level === 'high')   return 'high'
  if (level === 'medium') return 'medium'
  if (level === 'low')    return 'low'
  return 'default'
}

// ── 动作 ─────────────────────────────────────────────────────────────────────
async function loadExplanation() {
  if (!query.value.configKey || keysLoading.value) return
  const request = ++explanationRequest
  const snapshot = { ...query.value }
  loading.value = true
  error.value = ''
  explanation.value = null
  try {
    const result = await fetchConfigHitExplanation(snapshot)
    if (request === explanationRequest) explanation.value = result
  } catch (err) {
    if (request === explanationRequest) error.value = err instanceof Error ? err.message : '配置命中说明加载失败'
  } finally {
    if (request === explanationRequest) loading.value = false
  }
}

async function loadKeys() {
  const request = ++keyRequest
  const configType = query.value.configType
  let loaded = false
  keysLoading.value = true
  keyOptions.value = []
  error.value = ''
  explanation.value = null
  ++explanationRequest
  loading.value = false
  try {
    const options = await fetchConfigKeyOptions(configType)
    if (request !== keyRequest) return
    keyOptions.value = options
    loaded = true
    if (!options.some(option => option.configKey === query.value.configKey)) {
      query.value.configKey = options[0]?.configKey || ''
    }
  } catch (err) {
    if (request === keyRequest) {
      query.value.configKey = ''
      error.value = err instanceof Error ? err.message : '配置项加载失败'
    }
  } finally {
    if (request === keyRequest) keysLoading.value = false
  }
  if (request === keyRequest && loaded && query.value.configKey) await loadExplanation()
}

function resetQuery() {
  const sameType = query.value.configType === 'workflow'
  query.value = {
    businessPage: '采购订单', configType: 'workflow', configKey: 'approval.purchase.order',
    userId: '', moduleId: 'purchase_order', warehouseId: '', departmentId: '', campusId: '',
    hospitalId: 'default', tenantId: ''
  }
  // Changing type is handled by the watcher; otherwise reload this type's options.
  if (sameType) loadKeys()
}

watch(() => query.value.configType, loadKeys)
watch(() => query.value.configKey, () => {
  ++explanationRequest
  explanation.value = null
  loading.value = false
}, { flush: 'sync' })
onMounted(loadKeys)
</script>

<template>
  <div class="sys-page spd-unified-list">

    <MdPageHeader
      title="配置命中说明"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadExplanation"
    />

    <!-- 查询条件卡 -->
    <div class="sys-form-section">
      <div class="sys-form-section-header">
        <i class="sys-form-section-icon is-green"><Filter :size="15" /></i>
        <div>
          <h4>查询上下文</h4>
          <p>选择配置类型与配置项，再填写上下文；不同配置键互不覆盖</p>
        </div>
      </div>
      <div class="sys-form-grid">
        <label><span>业务页面</span><input v-model.trim="query.businessPage" class="sys-input" /></label>
        <label><span>配置类型</span>
          <select v-model="query.configType" class="sys-select">
            <option v-for="t in configTypes" :key="t.code" :value="t.code">{{ t.label }}</option>
          </select>
        </label>
        <label><span>配置项</span>
          <select v-model="query.configKey" class="sys-select" :disabled="keysLoading || !keyOptions.length">
            <option value="">{{ keysLoading ? '正在加载配置项' : '请选择配置项' }}</option>
            <option v-for="option in keyOptions" :key="option.configKey" :value="option.configKey">{{ option.configName }}（{{ option.configKey }}）</option>
          </select>
        </label>
        <label><span>用户</span><input v-model.trim="query.userId" class="sys-input" /></label>
        <label><span>模块</span><input v-model.trim="query.moduleId" class="sys-input" /></label>
        <label><span>库房</span><input v-model.trim="query.warehouseId" class="sys-input" /></label>
        <label><span>科室</span><input v-model.trim="query.departmentId" class="sys-input" /></label>
        <label><span>院区</span><input v-model.trim="query.campusId" class="sys-input" /></label>
        <label><span>医院</span><input v-model.trim="query.hospitalId" class="sys-input" /></label>
        <div class="sys-form-actions">
          <SpdButton label="重置" tone="neutral" @click="resetQuery">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton>
          <SpdButton label="查看命中" tone="primary" :disabled="loading || keysLoading || !query.configKey" @click="loadExplanation">
          <template #icon><Search :size="15" /></template>
        </SpdButton>
        </div>
      </div>
    </div>

    <p v-if="!keysLoading && !keyOptions.length && !error" class="sys-error">当前配置类型没有可查询的配置项</p>
    <!-- 加载 / 错误 -->
    <div v-if="loading" class="sys-loading-card" role="status">
      <i class="sys-loading-icon"><Search :size="20" /></i>
      <p>正在计算配置命中...</p>
    </div>
    <div v-else-if="error" class="sys-error" role="alert">{{ error }}</div>

    <!-- 命中结果 -->
    <template v-else-if="explanation">
      <div :class="explanation.conflict ? 'sys-error' : 'sys-form-section'" role="status">
        <strong>{{ explanation.matchReason }}</strong>
        <p>{{ explanation.executionNote }}</p>
      </div>

      <!-- 命中结果 KPI（第 2 行，4 张） -->
      <section class="sys-kpi-card" aria-label="命中结果详情">
        <div class="sys-kpi-item is-green">
          <i class="sys-kpi-icon is-check"><CheckCircle2 :size="18" /></i>
          <div>
            <span class="sys-kpi-label">最终来源层级</span>
            <strong class="sys-kpi-value">{{ explanation.sourceLevel || '-' }}</strong>
            <span class="sys-kpi-hint">命中的最细粒度层级</span>
          </div>
        </div>
        <div class="sys-kpi-item is-blue">
          <i class="sys-kpi-icon is-tag"><Tag :size="18" /></i>
          <div>
            <span class="sys-kpi-label">来源配置编号</span>
            <strong class="sys-kpi-value" style="font-size: var(--spd-font-size-lg)">
              {{ explanation.sourceConfigId ? `CFG-${explanation.sourceConfigId}` : '-' }}
            </strong>
            <span class="sys-kpi-hint">当前键的匹配配置编号</span>
          </div>
        </div>
        <div class="sys-kpi-item is-orange">
          <i class="sys-kpi-icon is-branch"><GitBranch :size="18" /></i>
          <div>
            <span class="sys-kpi-label">覆盖上级配置</span>
            <strong class="sys-kpi-value">{{ explanation.conflict ? '待核对冲突' : explanation.overridesParent ? '是' : '无继承配置' }}</strong>
            <span class="sys-kpi-hint">是否覆盖了上级通用配置</span>
          </div>
        </div>
        <div class="sys-kpi-item is-purple">
          <i class="sys-kpi-icon is-award"><Award :size="18" /></i>
          <div>
            <span class="sys-kpi-label">匹配状态</span>
            <strong class="sys-kpi-value" style="font-size: var(--spd-font-size-base)">
              {{ explanation.approvalStatus }}
            </strong>
            <span class="sys-kpi-hint">记录匹配结果，非审批进度</span>
          </div>
        </div>
      </section>

      <!-- 左右双面板：最终命中配置 + 优先级规则 -->
      <div class="sys-two-col">

        <!-- 左：最终命中配置详情 -->
        <article class="sys-detail-card">
          <div class="sys-detail-card-header">
            <i class="sys-detail-card-icon"><SlidersHorizontal :size="15" /></i>
            <div>
              <h4>最终命中配置</h4>
              <p>业务页面 <code>{{ explanation.businessPage || '-' }}</code> 的匹配值</p>
            </div>
          </div>
          <div class="sys-detail-body">
            <dl class="sys-detail-dl">
              <div class="sys-detail-row">
                <dt>业务页面</dt>
                <dd>{{ explanation.businessPage || '-' }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>配置类型</dt>
                <dd>
                  <span class="sys-tag is-type">{{ labelFor(explanation.configType) }}</span>
                </dd>
              </div>
              <div class="sys-detail-row">
                <dt>配置键</dt><dd>{{ explanation.configKey }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>匹配原因</dt><dd>{{ explanation.matchReason }}</dd>
              </div>
              <div class="sys-detail-row is-wide">
                <dt>匹配值</dt>
                <dd class="sys-json-inline">{{ explanation.finalValue }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>来源层级</dt>
                <dd>{{ explanation.sourceLevel || '-' }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>来源配置编号</dt>
                <dd>
                  <span class="sys-tag is-code">{{ explanation.sourceConfigId ? `CFG-${explanation.sourceConfigId}` : '-' }}</span>
                </dd>
              </div>
              <div class="sys-detail-row">
                <dt>适用对象</dt>
                <dd>{{ explanation.scopeType }} / {{ explanation.scopeId }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>生效时间</dt>
                <dd>{{ explanation.effectiveTime || '-' }}</dd>
              </div>
              <div class="sys-detail-row">
                <dt>失效时间</dt>
                <dd>{{ explanation.expireTime || '-' }}</dd>
              </div>
              <div class="sys-detail-row is-wide">
                <dt>继承来源</dt>
                <dd>{{ explanation.parentConfigId ? `CFG-${explanation.parentConfigId} / ${explanation.parentSourceLevel}` : '无继承配置' }}</dd>
              </div>
              <div class="sys-detail-row is-wide">
                <dt>上级配置值</dt>
                <dd class="sys-json-inline is-parent">{{ explanation.parentValue || '-' }}</dd>
              </div>
              <div class="sys-detail-row is-wide">
                <dt>最近变更人</dt>
                <dd>{{ explanation.lastChangedBy || '-' }}</dd>
              </div>
            </dl>
          </div>
        </article>

        <!-- 右：优先级规则 -->
        <article class="sys-detail-card">
          <div class="sys-detail-card-header">
            <i class="sys-detail-card-icon is-orange"><Lightbulb :size="15" /></i>
            <div>
              <h4>优先级规则</h4>
              <p>系统如何按层级由粗到细匹配并覆盖配置</p>
            </div>
          </div>
          <div class="sys-detail-body">
            <ol class="sys-rule-list">
              <li v-for="(rule, i) in explanation.priorityRules" :key="i" class="sys-rule-item">
                <i class="sys-rule-num">{{ i + 1 }}</i>
                <span>{{ rule }}</span>
              </li>
            </ol>
          </div>
        </article>

      </div>

      <!-- 候选配置链路表 -->
      <MdDataTableCard class="master-table-card">
        <template #toolbar>
          <div class="ulc-col-settings">
            <SpdButton label="列设置" tone="neutral" ref="configHitColSettingsAnchorRef"
             
             
              @click="configHitCols.settingsOpen.value = !configHitCols.settingsOpen.value">
          <template #icon><Columns3 :size="16" /></template>
        </SpdButton>
            <div
              v-if="configHitCols.settingsOpen.value"
              ref="configHitColSettingsPopRef"
              class="ulc-col-settings-pop"
              :class="{ 'is-align-right': configHitColSettingsPop.align.value === 'right', 'is-align-up': configHitColSettingsPop.vertical.value === 'up' }"
              :style="configHitColSettingsPop.popStyle.value"
            >
              <header>
                <strong>列展示设置</strong>
                <SpdButton label="恢复默认" variant="text" tone="neutral" @click="configHitCols.resetColumns()"></SpdButton>
              </header>
              <div class="ulc-col-settings-list">
                <label v-for="col in configHitCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                  <input
                    type="checkbox"
                    :checked="col.visible"
                    :disabled="col.visible && configHitCols.visibleColumnCount.value <= 1"
                    @change="configHitCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ col.label }}</span>
                  <small>{{ col.width }}px</small>
                </label>
              </div>
              <footer>
                <SpdButton label="确定" tone="primary" @click="configHitCols.settingsOpen.value = false"></SpdButton>
              </footer>
            </div>
          </div>
        </template>
        <template #meta-extra>
          <span class="md-chip primary">共 {{ explanation.candidates.length }} 条</span>
        </template>

        <div class="ulc-table-wrap">
          <el-table
            ref="configHitTableRef"
            v-loading="loading"
            :data="explanation.candidates"
            row-key="configId"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'当前上下文未命中已生效配置'"
            @header-dragend="(width: number, oldWidth: number, column: any) => configHitCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="hit"
              v-if="isConfigHitColVisible('hit')"
              label="命中"
              v-bind="configHitColBinding('hit')"
            >
              <template #header>
                <span class="ulc-th-text">命中</span>
              </template>
              <template #default="{ row }">
                <span v-if="row.conflict" class="sys-badge is-pending">配置冲突</span>
                <span v-else-if="row.hit" class="sys-badge is-hit">
                  <CheckCircle2 :size="11" /> 最终命中
                </span>
                <span v-else-if="row.overridden" class="sys-badge is-overridden">被覆盖</span>
                <span v-else class="sys-badge is-none">—</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="priority"
              v-if="isConfigHitColVisible('priority')"
              label="优先级"
              v-bind="configHitColBinding('priority')"
            >
              <template #header>
                <span class="ulc-th-text">优先级</span>
              </template>
              <template #default="{ row }">
                <span class="sys-tag is-priority">#{{ row.priority }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="sourceLevel"
              v-if="isConfigHitColVisible('sourceLevel')"
              label="来源层级"
              v-bind="configHitColBinding('sourceLevel')"
            >
              <template #header>
                <span class="ulc-th-text">来源层级</span>
              </template>
              <template #default="{ row }">
                <span class="sys-tag is-level">{{ row.sourceLevel }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="configId"
              v-if="isConfigHitColVisible('configId')"
              label="配置编号"
              v-bind="configHitColBinding('configId')"
            >
              <template #header>
                <span class="ulc-th-text">配置编号</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">CFG-{{ row.configId }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="configName"
              v-if="isConfigHitColVisible('configName')"
              label="配置名称"
              v-bind="configHitColBinding('configName')"
            >
              <template #header>
                <span class="ulc-th-text">配置名称</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="ulc-strong ulc-wrap-text">{{ row.configName || '-' }}</span>
                  <span class="ulc-mono ulc-wrap-text">{{ row.configKey }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="scope"
              v-if="isConfigHitColVisible('scope')"
              label="适用对象"
              v-bind="configHitColBinding('scope')"
            >
              <template #header>
                <span class="ulc-th-text">适用对象</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-cell-stack">
                  <span class="sys-tag is-scope ulc-wrap-text">{{ row.scopeType }}</span>
                  <span class="sys-tag is-code ulc-wrap-text">{{ row.scopeId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              column-key="configValue"
              v-if="isConfigHitColVisible('configValue')"
              label="配置值"
              v-bind="configHitColBinding('configValue')"
            >
              <template #header>
                <span class="ulc-th-text">配置值</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text" :title="row.configValue">{{ row.configValue }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="effectiveTime"
              v-if="isConfigHitColVisible('effectiveTime')"
              label="生效时间"
              v-bind="configHitColBinding('effectiveTime')"
            >
              <template #header>
                <span class="ulc-th-text">生效时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ row.effectiveTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="expireTime"
              v-if="isConfigHitColVisible('expireTime')"
              label="失效时间"
              v-bind="configHitColBinding('expireTime')"
            >
              <template #header>
                <span class="ulc-th-text">失效时间</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-time">{{ row.expireTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="riskLevel"
              v-if="isConfigHitColVisible('riskLevel')"
              label="风险"
              v-bind="configHitColBinding('riskLevel')"
            >
              <template #header>
                <span class="ulc-th-text">风险</span>
              </template>
              <template #default="{ row }">
                <span :class="['sys-chip', `is-risk-${riskChipType(row.riskLevel)}`]">
                  {{ row.riskLevel === 'high' ? '高' : row.riskLevel === 'medium' ? '中' : row.riskLevel === 'low' ? '低' : '—' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="approvalStatus"
              v-if="isConfigHitColVisible('approvalStatus')"
              label="匹配状态"
              v-bind="configHitColBinding('approvalStatus')"
            >
              <template #header>
                <span class="ulc-th-text">匹配状态</span>
              </template>
              <template #default="{ row }">
                <span :class="['sys-badge', row.conflict ? 'is-pending' : 'is-approved']">
                  {{ row.approvalStatus }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="matchReason"
              v-if="isConfigHitColVisible('matchReason')"
              label="匹配原因"
              v-bind="configHitColBinding('matchReason')"
            >
              <template #header>
                <span class="ulc-th-text">匹配原因</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.matchReason }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <MdEmptyState text="当前上下文未命中已生效配置" />
            </template>
          </el-table>
        </div>
      </MdDataTableCard>

    </template>

  </div>
</template>

<style scoped>
/* =====================================================================
   系统管理 · 配置命中说明
   ---------------------------------------------------------------------
   设计目标：
   1. 顶部 4 张主题色 KPI 卡（候选数 / 命中数 / 被覆盖 / 高风险）。
   2. 查询条件卡：绿底 Filter 图标 + section-title。
   3. 命中结果：第 2 行 4 张详情卡 + 左右双面板（最终配置 / 优先级规则）。
   4. 候选链路表：命中行浅绿底，被覆盖行灰底，状态 chip 彩色。
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

.sys-kpi-item.is-blue    { background: linear-gradient(180deg, var(--spd-p-border) 0%, var(--spd-p-info-border) 100%); border-color: var(--spd-p-info-border); }
.sys-kpi-item.is-green   { background: linear-gradient(180deg, var(--spd-p-success-bg) 0%, var(--spd-p-success-border) 100%); border-color: var(--spd-p-success); }
.sys-kpi-item.is-orange  { background: linear-gradient(180deg, var(--spd-p-warning-bg) 0%, var(--spd-p-warning-border) 100%); border-color: var(--spd-p-warning-border); }
.sys-kpi-item.is-purple  { background: linear-gradient(180deg, var(--spd-p-info-bg) 0%, var(--spd-p-info-bg) 100%); border-color: var(--spd-p-info-border); }
.sys-kpi-item.is-rose    { background: linear-gradient(180deg, var(--spd-p-danger-bg) 0%, var(--spd-p-danger-border) 100%); border-color: var(--spd-p-danger-border); }
.sys-kpi-item.is-cyan    { background: linear-gradient(180deg, var(--spd-p-info-bg) 0%, var(--spd-p-success-border) 100%); border-color: var(--spd-p-success); }
.sys-kpi-item.is-teal    { background: linear-gradient(180deg, var(--spd-p-primary-bg) 0%, var(--spd-p-primary-border) 100%); border-color: var(--spd-p-primary-hover); }

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
.sys-kpi-item.is-rose   .sys-kpi-icon { color: var(--spd-p-danger-strong); }
.sys-kpi-item.is-cyan   .sys-kpi-icon { color: var(--spd-p-success-text); }
.sys-kpi-item.is-teal   .sys-kpi-icon { color: var(--spd-p-primary); }

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
.sys-kpi-item.is-rose   .sys-kpi-label,
.sys-kpi-item.is-rose   .sys-kpi-value,
.sys-kpi-item.is-rose   .sys-kpi-hint { color: var(--spd-p-danger-strong); }
.sys-kpi-item.is-cyan   .sys-kpi-label,
.sys-kpi-item.is-cyan   .sys-kpi-value,
.sys-kpi-item.is-cyan   .sys-kpi-hint { color: var(--spd-p-info); }
.sys-kpi-item.is-teal   .sys-kpi-label,
.sys-kpi-item.is-teal   .sys-kpi-value,
.sys-kpi-item.is-teal   .sys-kpi-hint { color: var(--spd-p-primary-strong); }

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

.sys-form-section-icon.is-green { background: var(--spd-p-success-bg); color: var(--spd-p-success-strong); }
.sys-form-section-icon.is-orange { background: var(--spd-p-warning-bg); color: var(--spd-p-warning-text); }

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
.sys-select:focus { outline: none; }

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

/* ---------- 加载 / 消息 / 错误 ---------- */
.sys-loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px;
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border);
  border-radius: var(--spd-radius-md, 10px);
  color: var(--spd-p-text-secondary);
}

.sys-loading-icon {
  display: inline-flex;
  color: var(--spd-p-success);
  animation: spin 1.2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

.sys-loading-card p { margin: 0; font-size: var(--spd-font-size-base); }

.sys-error {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  background: var(--spd-p-danger-bg);
  border: 1px solid var(--spd-p-danger-border);
  border-radius: 8px;
  color: var(--spd-p-danger-strong);
  font-size: var(--spd-font-size-sm);
  box-shadow: var(--spd-p-shadow-1);
}

/* ---------- 左右双栏面板 ---------- */
.sys-two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: start;
}

@media (max-width: 1000px) {
  .sys-two-col { grid-template-columns: 1fr; }
}

/* ---------- 详情面板卡片 ---------- */
.sys-detail-card {
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border);
  border-radius: var(--spd-radius-md, 10px);
  overflow: hidden;
  box-shadow: var(--spd-p-shadow-1);
  display: flex;
  flex-direction: column;
}

.sys-detail-card-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
}

.sys-detail-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--spd-p-success-bg);
  color: var(--spd-p-success-strong);
  flex-shrink: 0;
}

.sys-detail-card-icon.is-orange { background: var(--spd-p-warning-bg); color: var(--spd-p-warning-text); }

.sys-detail-card-header h4 {
  margin: 0;
  font-size: var(--spd-font-size-base);
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-detail-card-header p {
  margin: 3px 0 0;
  font-size: var(--spd-font-size-sm);
  color: var(--spd-text-tertiary);
}

.sys-detail-card-header code {
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-border);
  border-radius: 4px;
  padding: 1px 6px;
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-xs);
  color: var(--spd-p-text-secondary);
}

.sys-detail-body {
  padding: 14px 16px;
}

/* ---------- 详情 dl 网格 ---------- */
.sys-detail-dl {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
}

.sys-detail-row {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 8px 14px;
  padding: 7px 0;
  border-bottom: 1px solid var(--spd-p-text-strong);
  align-items: start;
}

.sys-detail-row:last-child { border-bottom: none; }
.sys-detail-row.is-wide { grid-column: 1 / -1; }

.sys-detail-row dt {
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  padding-top: 2px;
  flex-shrink: 0;
}

.sys-detail-row dd {
  margin: 0;
  color: var(--spd-p-text);
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  word-break: break-all;
}

.sys-json-inline {
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-border);
  border-radius: 6px;
  padding: 6px 10px;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 100px;
  overflow: auto;
}

.sys-json-inline.is-parent {
  background: var(--spd-p-warning-bg);
  border-color: var(--spd-p-warning-border);
  color: var(--spd-p-warning-strong);
}

/* ---------- 规则列表 ---------- */
.sys-rule-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sys-rule-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  background: var(--spd-p-bg);
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-secondary);
  line-height: var(--spd-lh-base);
}

.sys-rule-item:hover { background: var(--spd-p-primary-bg); }

.sys-rule-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: linear-gradient(180deg, var(--spd-p-success) 0%, var(--spd-p-success) 100%);
  color: var(--spd-p-bg);
  font-size: var(--spd-font-size-xs);
  font-weight: 700;
  flex-shrink: 0;
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
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px 16px;
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

.sys-table-card-header p {
  margin: 3px 0 0;
  font-size: var(--spd-font-size-sm);
  color: var(--spd-text-tertiary);
}

.sys-table-card-meta {
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-secondary);
  white-space: nowrap;
  padding-top: 2px;
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

.sys-table tbody tr:hover td { background: var(--spd-p-bg-muted); }
.sys-table tbody tr:last-child td { border-bottom: none; }

/* 命中行 / 被覆盖行 */
.sys-table tbody tr.is-hit td {
  background: color-mix(in srgb, var(--spd-p-success) 7%, transparent) !important;
  border-bottom-color: color-mix(in srgb, var(--spd-p-success) 15%, transparent);
}

.sys-table tbody tr.is-overridden td {
  background: var(--spd-p-bg-muted) !important;
  color: var(--spd-p-text-muted);
}

.text-center { text-align: center; }
.muted { color: var(--spd-p-text-muted); padding: 32px 16px; font-size: var(--spd-font-size-base); }

.config-value-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-base);
  color: var(--spd-p-text-secondary);
}

/* ---------- 通用 tag / chip / badge ---------- */
.sys-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  /* 文字居中于色块内；宽度不足时换行而不是横向溢出侵占相邻列 */
  text-align: center;
  max-width: 100%;
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
  gap: 4px;
}

.sys-tag.is-code {
  color: var(--spd-p-text-secondary);
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  background: var(--spd-p-bg-muted);
  border-color: var(--spd-p-border);
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-sm);
  /* 配置键是长英文标识，需要在断点处折行 */
  word-break: break-word;
}

.sys-tag.is-type {
  color: var(--spd-p-success-strong);
  background: var(--spd-p-success-bg);
  border-color: var(--spd-p-success);
  font-size: var(--spd-font-size-base);
}

.sys-tag.is-priority {
  color: var(--spd-p-info-strong);
  background: var(--spd-p-border);
  border-color: var(--spd-p-info-border);
  font-family: var(--spd-font-family-mono);
}

.sys-tag.is-level {
  color: var(--spd-p-primary);
  background: var(--spd-p-info-bg);
  border-color: var(--spd-p-info-border);
}

.sys-tag.is-scope {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border-color: var(--spd-p-warning-border);
}

/* 风险 chip */
.sys-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  border: 1px solid;
  /* 文字居中于色块内；宽度不足时换行而不是横向溢出侵占相邻列 */
  text-align: center;
  max-width: 100%;
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
}

.sys-chip.is-risk-high   { color: var(--spd-p-danger-strong); background: var(--spd-p-danger-bg); border-color: var(--spd-p-danger-border); }
.sys-chip.is-risk-medium { color: var(--spd-p-warning-text); background: var(--spd-p-warning-bg); border-color: var(--spd-p-warning-border); }
.sys-chip.is-risk-low    { color: var(--spd-p-primary-strong); background: var(--spd-p-success-bg); border-color: var(--spd-p-success); }
.sys-chip.is-risk-default { color: var(--spd-p-text-secondary); background: var(--spd-p-bg-muted); border-color: var(--spd-p-border); }

/* 状态 badge */
.sys-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.sys-badge.is-hit {
  color: var(--spd-p-primary-strong);
  background: var(--spd-p-success-bg);
  border: 1px solid var(--spd-p-success);
}

.sys-badge.is-overridden {
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-border);
}

.sys-badge.is-none {
  color: var(--spd-p-text-muted);
  background: transparent;
  border: 1px solid var(--spd-p-border);
}

.sys-badge.is-approved {
  color: var(--spd-p-primary-strong);
  background: var(--spd-p-success-bg);
  border: 1px solid var(--spd-p-success);
}

.sys-badge.is-pending {
  color: var(--spd-p-warning-text);
  background: var(--spd-p-warning-bg);
  border: 1px solid var(--spd-p-warning-border);
}

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .sys-kpi-card { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .sys-form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .sys-kpi-card { grid-template-columns: 1fr; }
  .sys-form-grid { grid-template-columns: 1fr; }
  .sys-page { padding: 12px; }
}
</style>
