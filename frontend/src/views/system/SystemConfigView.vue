<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Teleport } from 'vue'
import McpConnectionSettings from '../../components/system/McpConnectionSettings.vue'
import {
  CheckCircle2,
  ChevronDown,
  ClipboardList,
  Cog,
  Columns3,
  Database,
  Plus,
  RefreshCw,
  Search,
  Settings,
  Shield,
  SlidersHorizontal,
  Workflow,
  X
} from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import {
  createSystemConfig,
  fetchSystemConfigs,
  updateSystemConfig,
  updateSystemConfigStatus,
  type SystemConfigPayload,
  type SystemConfigRow
} from '../../api/systemConfig'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'

const configTypes = [
  { code: '',          label: '全部配置',     desc: '查看所有配置项',         icon: 'list',     theme: 'slate'   },
  { code: 'base',      label: '基础配置',     desc: '医院信息、组织、仓库',   icon: 'cog',      theme: 'blue'    },
  { code: 'workflow',  label: '业务流程配置', desc: '审批流程、业务规则',     icon: 'workflow', theme: 'green'   },
  { code: 'permission',label: '权限配置',     desc: '角色、菜单、按钮、数据', icon: 'shield',   theme: 'purple'  },
  { code: 'parameter', label: '参数配置',     desc: '系统、业务、预警参数',   icon: 'sliders',  theme: 'orange'  },
  { code: 'audit',     label: '日志审计',     desc: '操作、登录、异常日志',   icon: 'clipboard',theme: 'cyan'    },
  { code: 'data',      label: '数据管理',     desc: '数据字典、编码、备份',   icon: 'database', theme: 'teal'    },
  { code: 'integration', label: '系统集成',   desc: '接口、定时任务、消息',   icon: 'plug',     theme: 'rose'    }
]

const rows = ref<SystemConfigRow[]>([])
const loading = ref(false)
const error = ref('')
const actionMessage = ref('')
const actionError = ref('')
const activeType = ref('')
const keyword = ref('')
const status = ref('')
const dialogOpen = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)

const form = ref<SystemConfigPayload>({
  configType: 'base',
  scopeType: 'global',
  scopeId: 'default',
  configKey: '',
  configValue: '{}',
  effectiveMode: 'realtime',
  expireTime: '',
  riskLevel: 'low',
  status: 1
})

/* ===== 列表列配置 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 使用默认的 'fixed'：每列宽度互相独立，
       拖拽某一列不会因为容器宽度变化而影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const SYSTEM_CONFIG_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'configType', label: '配置类型', width: 110, visible: true, weight: 4, minWidth: 100, maxWidth: 190 },
  { key: 'configName', label: '配置名称', width: 160, visible: true, flexible: true, idealWidth: 220, weight: 8, minWidth: 130, maxWidth: 320 },
  { key: 'scopeType', label: '适用范围', width: 100, visible: true, weight: 3, minWidth: 90, maxWidth: 160 },
  { key: 'scopeId', label: '适用对象', width: 110, visible: true, weight: 4, minWidth: 95, maxWidth: 220 },
  { key: 'configKey', label: '配置键', width: 150, visible: true, flexible: true, idealWidth: 210, weight: 6, minWidth: 120, maxWidth: 300 },
  { key: 'configValue', label: '配置值', width: 170, visible: true, flexible: true, idealWidth: 260, weight: 6, minWidth: 130, maxWidth: 380 },
  { key: 'effectiveMode', label: '生效方式', width: 100, visible: true, weight: 3, minWidth: 90, maxWidth: 170 },
  { key: 'riskLevel', label: '风险', width: 80, visible: true, weight: 2, minWidth: 72, maxWidth: 130 },
  { key: 'status', label: '状态', width: 80, visible: true, weight: 2, minWidth: 72, maxWidth: 130 },
  { key: 'createTime', label: '创建时间', width: 140, visible: true, weight: 4, minWidth: 125, maxWidth: 200 },
  { key: 'updateTime', label: '更新时间', width: 140, visible: true, weight: 4, minWidth: 125, maxWidth: 200 },
  // 「操作」列是最后一列，用 fill 标记吃掉表格右侧剩余空白，
  // 使整张表铺满内容区（100%）；按钮单行排列，不换行。
  { key: 'actions', label: '操作', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 400, fill: true }
]

const systemConfigCols = useListColumns({
  storageKey: 'spd-system-config-cols-v1',
  defaults: SYSTEM_CONFIG_COL_DEFAULTS,
  // 方案 C：混合策略 —— 按权重把内容区宽度铺满给所有列，
  // 长文本列（配置名称 / 配置键 / 配置值）额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const systemConfigTableRef = systemConfigCols.tableRef
const isSystemConfigColVisible = (key: string) => systemConfigCols.isColumnVisible(key)
const systemConfigColBinding = (key: string) => systemConfigCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const systemConfigActionFix = useActionColumnFix()
const systemConfigActionFixed = computed(() => systemConfigActionFix.actionFixedBinding.value)

watch(
  [systemConfigTableRef],
  () => {
    systemConfigActionFix.observe(systemConfigTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const systemConfigColSettingsAnchorRef = ref<HTMLElement | null>(null)
const systemConfigColSettingsPopRef = ref<HTMLElement | null>(null)
const systemConfigColSettingsPop = useColSettingsPopover({
  open: systemConfigCols.settingsOpen,
  anchorRef: systemConfigColSettingsAnchorRef,
  popRef: systemConfigColSettingsPopRef
})

function themeFor(code: string) {
  return configTypes.find(t => t.code === code)?.theme ?? 'slate'
}
function labelFor(code: string) {
  return configTypes.find(t => t.code === code)?.label ?? code
}

async function loadConfigs() {
  loading.value = true
  error.value = ''
  actionError.value = ''

  try {
    rows.value = await fetchSystemConfigs({
      configType: activeType.value,
      keyword: keyword.value,
      status: status.value
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '系统配置加载失败'
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  keyword.value = ''
  status.value = ''
  loadConfigs()
}

function selectType(type: string) {
  activeType.value = type
  loadConfigs()
}

function emptyForm() {
  form.value = {
    configType: activeType.value || 'base',
    scopeType: 'global',
    scopeId: 'default',
    configKey: '',
    configValue: '{}',
    effectiveMode: 'realtime',
    expireTime: '',
    riskLevel: 'low',
    status: 1
  }
}

function openCreate() {
  actionError.value = ''
  actionMessage.value = ''
  dialogMode.value = 'create'
  editingId.value = null
  emptyForm()
  dialogOpen.value = true
}

function openEdit(row: SystemConfigRow) {
  actionError.value = ''
  actionMessage.value = ''
  dialogMode.value = 'edit'
  editingId.value = row.configId
  form.value = {
    configType: row.configType,
    scopeType: row.scopeType,
    scopeId: row.scopeId,
    configKey: row.configKey,
    configValue: row.configValue,
    effectiveMode: row.effectiveMode,
    expireTime: row.expireTime === '-' ? '' : row.expireTime,
    riskLevel: row.riskLevel === '-' ? 'low' : row.riskLevel,
    status: row.status === '启用' ? 1 : 0
  }
  dialogOpen.value = true
}

async function saveConfig() {
  actionError.value = ''
  actionMessage.value = ''

  try {
    JSON.parse(form.value.configValue)
    if (dialogMode.value === 'create') {
      await createSystemConfig(form.value)
      actionMessage.value = '系统配置已新增'
    } else if (editingId.value != null) {
      await updateSystemConfig(editingId.value, form.value)
      actionMessage.value = '系统配置已更新'
    }
    dialogOpen.value = false
    await loadConfigs()
  } catch (err) {
    actionError.value = err instanceof SyntaxError ? '配置值必须是合法 JSON' : err instanceof Error ? err.message : '保存失败'
  }
}

async function toggleStatus(row: SystemConfigRow) {
  actionError.value = ''
  actionMessage.value = ''
  try {
    await updateSystemConfigStatus(row.configId, row.status === '启用' ? 0 : 1)
    actionMessage.value = `${row.configName} 已${row.status === '启用' ? '停用' : '启用'}`
    await loadConfigs()
  } catch (err) {
    actionError.value = err instanceof Error ? err.message : '状态更新失败'
  }
}

onMounted(loadConfigs)
</script>

<template>
  <div class="sys-page spd-unified-list">

    <MdPageHeader
      title="系统配置"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadConfigs"
    />

    <!-- 类型导航 Tab -->
    <div class="sys-tabs">
      <button
        v-for="item in configTypes"
        :key="item.code"
        type="button"
        :class="['sys-tab', { active: activeType === item.code }]"
        :title="item.desc"
        @click="selectType(item.code)"
      >
        <Cog v-if="item.icon === 'cog'" :size="14" />
        <Workflow v-else-if="item.icon === 'workflow'" :size="14" />
        <Shield v-else-if="item.icon === 'shield'" :size="14" />
        <SlidersHorizontal v-else-if="item.icon === 'sliders'" :size="14" />
        <ClipboardList v-else-if="item.icon === 'clipboard'" :size="14" />
        <Database v-else-if="item.icon === 'database'" :size="14" />
        <Settings v-else-if="item.icon === 'plug'" :size="14" />
        <ClipboardList v-else :size="14" />
        {{ item.label }}
      </button>
    </div>

    <McpConnectionSettings v-if="activeType === 'integration'" @saved="loadConfigs" />

    <!-- 工具栏：使用 md-table-toolbar 以继承标准列表按钮的主题配色 -->
    <div class="sys-toolbar-panel md-table-toolbar">
      <div class="sys-toolbar-search">
        <i class="sys-toolbar-search-icon"><Search :size="15" /></i>
        <input v-model.trim="keyword" placeholder="配置键 / 配置名" @keyup.enter="loadConfigs" />
        <select v-model="status" class="sys-toolbar-select">
          <option value="">全部</option>
          <option value="启用">启用</option>
          <option value="停用">停用</option>
        </select>
      </div>
      <div class="sys-toolbar-actions">
        <button class="md-btn" type="button" @click="resetQuery"><RefreshCw :size="15" /> 重置</button>
        <i class="sys-toolbar-sep"></i>
        <button class="md-btn md-btn-primary" type="button" @click="openCreate"><Plus :size="15" /> 新增配置</button>
      </div>
    </div>

    <!-- 消息 -->
    <div v-if="actionMessage" class="sys-msg" role="alert">{{ actionMessage }}</div>
    <div v-if="actionError" class="sys-error" role="alert">{{ actionError }}</div>

    <!-- 配置表 -->
    <MdDataTableCard :loading="loading">
      <template #toolbar>
        <div class="ulc-col-settings">
          <button
            ref="systemConfigColSettingsAnchorRef"
            type="button"
            class="md-btn md-btn-primary"
            @click="systemConfigCols.settingsOpen.value = !systemConfigCols.settingsOpen.value"
          >
            <Columns3 :size="16" />
            <span>列设置</span>
            <ChevronDown :size="14" :class="{ rotated: systemConfigCols.settingsOpen.value }" />
          </button>
          <div
            v-if="systemConfigCols.settingsOpen.value"
            ref="systemConfigColSettingsPopRef"
            class="ulc-col-settings-pop"
            :class="{ 'is-align-right': systemConfigColSettingsPop.align.value === 'right', 'is-align-up': systemConfigColSettingsPop.vertical.value === 'up' }"
            :style="{ '--ulc-pop-offset-y': systemConfigColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': systemConfigColSettingsPop.maxHeight.value + 'px' }"
          >
            <header>
              <strong>列展示设置</strong>
              <button type="button" class="ulc-link" @click="systemConfigCols.resetColumns()">恢复默认</button>
            </header>
            <div class="ulc-col-settings-list">
              <label v-for="col in systemConfigCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                <input
                  type="checkbox"
                  :checked="col.visible"
                  :disabled="col.visible && systemConfigCols.visibleColumnCount.value <= 1"
                  @change="systemConfigCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ col.label }}</span>
                <small>{{ col.width }}px</small>
              </label>
            </div>
            <footer>
              <button type="button" class="ulc-action" @click="systemConfigCols.settingsOpen.value = false">确定</button>
            </footer>
          </div>
        </div>
      </template>
      <template #meta-extra>
        <span class="md-chip primary">共 {{ rows.length }} 条</span>
      </template>

      <div class="ulc-table-wrap">
        <el-table
          ref="systemConfigTableRef"
          v-loading="loading"
          :data="rows"
          stripe
          border
          row-key="configId"
          class="ulc-table is-sticky"
          max-height="560"
          style="width: 100%"
          :empty-text="loading ? '正在加载系统配置…' : '暂无配置'"
          @header-dragend="(width: number, oldWidth: number, column: any) => systemConfigCols.handleHeaderDragEnd(width, oldWidth, column)"
        >
          <el-table-column
            column-key="configType"
            v-if="isSystemConfigColVisible('configType')"
            prop="configType"
            v-bind="systemConfigColBinding('configType')"
          >
            <template #header>
              <span class="ulc-th-text">配置类型</span>
            </template>
            <template #default="{ row }">
              <span :class="['sys-tag', `is-type-${themeFor(row.configType)}`]">{{ labelFor(row.configType) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="configName"
            v-if="isSystemConfigColVisible('configName')"
            prop="configName"
            v-bind="systemConfigColBinding('configName')"
          >
            <template #header>
              <span class="ulc-th-text">配置名称</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-strong ulc-wrap-text">{{ row.configName }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="scopeType"
            v-if="isSystemConfigColVisible('scopeType')"
            prop="scopeType"
            v-bind="systemConfigColBinding('scopeType')"
          >
            <template #header>
              <span class="ulc-th-text">适用范围</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text">{{ row.scopeType }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="scopeId"
            v-if="isSystemConfigColVisible('scopeId')"
            prop="scopeId"
            v-bind="systemConfigColBinding('scopeId')"
          >
            <template #header>
              <span class="ulc-th-text">适用对象</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text">{{ row.scopeId }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="configKey"
            v-if="isSystemConfigColVisible('configKey')"
            prop="configKey"
            v-bind="systemConfigColBinding('configKey')"
          >
            <template #header>
              <span class="ulc-th-text">配置键</span>
            </template>
            <template #default="{ row }">
              <span class="sys-tag is-code ulc-wrap-text">{{ row.configKey }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="configValue"
            v-if="isSystemConfigColVisible('configValue')"
            prop="configValue"
            v-bind="systemConfigColBinding('configValue')"
          >
            <template #header>
              <span class="ulc-th-text">配置值</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-mono ulc-wrap-text">{{ row.configValue }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="effectiveMode"
            v-if="isSystemConfigColVisible('effectiveMode')"
            prop="effectiveMode"
            v-bind="systemConfigColBinding('effectiveMode')"
          >
            <template #header>
              <span class="ulc-th-text">生效方式</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-wrap-text">{{ row.effectiveMode }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="riskLevel"
            v-if="isSystemConfigColVisible('riskLevel')"
            prop="riskLevel"
            v-bind="systemConfigColBinding('riskLevel')"
          >
            <template #header>
              <span class="ulc-th-text">风险</span>
            </template>
            <template #default="{ row }">
              <span :class="['sys-chip', `is-risk-${row.riskLevel}`]">
                {{ row.riskLevel === 'high' ? '高' : row.riskLevel === 'medium' ? '中' : '低' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="status"
            v-if="isSystemConfigColVisible('status')"
            prop="status"
            v-bind="systemConfigColBinding('status')"
          >
            <template #header>
              <span class="ulc-th-text">状态</span>
            </template>
            <template #default="{ row }">
              <span :class="['sys-badge', row.status === '启用' ? 'is-active' : 'is-stopped']">
                {{ formatStatusText(row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="createTime"
            v-if="isSystemConfigColVisible('createTime')"
            prop="createTime"
            v-bind="systemConfigColBinding('createTime')"
          >
            <template #header>
              <span class="ulc-th-text">创建时间</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-time">{{ row.createTime || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="updateTime"
            v-if="isSystemConfigColVisible('updateTime')"
            prop="updateTime"
            v-bind="systemConfigColBinding('updateTime')"
          >
            <template #header>
              <span class="ulc-th-text">更新时间</span>
            </template>
            <template #default="{ row }">
              <span class="ulc-time">{{ row.updateTime }}</span>
            </template>
          </el-table-column>
          <el-table-column
            column-key="actions"
            v-if="isSystemConfigColVisible('actions')"
            label="操作"
            v-bind="systemConfigColBinding('actions')"
            :fixed="systemConfigActionFixed"
          >
            <template #header>
              <span class="ulc-th-text">操作</span>
            </template>
            <template #default="{ row }">
              <div class="ulc-actions">
                <button class="ulc-action" type="button" @click="openEdit(row)">编辑</button>
                <button class="ulc-action is-info" type="button" @click="toggleStatus(row)">{{ row.status === '启用' ? '停用' : '启用' }}</button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </MdDataTableCard>

    <!-- 弹窗 -->
    <Teleport to="body">
    <div v-if="dialogOpen" class="sys-dialog-mask" @click.self="dialogOpen = false">
      <div class="sys-dialog">
        <div class="sys-dialog-header">
          <h3>{{ dialogMode === 'create' ? '新增配置' : '编辑配置' }}</h3>
          <button class="md-btn md-btn-icon" type="button" @click="dialogOpen = false"><X :size="16" /></button>
        </div>
        <div class="sys-dialog-body">
          <div class="sys-form-grid">
            <label><span>配置类型</span>
              <select v-model="form.configType" class="sys-select">
                <option v-for="t in configTypes.filter(t => t.code)" :key="t.code" :value="t.code">{{ t.label }}</option>
              </select>
            </label>
            <label><span>适用范围</span>
              <select v-model="form.scopeType" class="sys-select">
                <option value="global">全局</option>
                <option value="department">科室</option>
                <option value="warehouse">库房</option>
                <option value="role">角色</option>
              </select>
            </label>
            <label><span>适用对象</span><input v-model.trim="form.scopeId" class="sys-input" /></label>
            <label><span>配置键</span><input v-model.trim="form.configKey" class="sys-input" /></label>
            <label><span>生效方式</span>
              <select v-model="form.effectiveMode" class="sys-select">
                <option value="realtime">实时生效</option>
                <option value="scheduled">定时生效</option>
                <option value="manual">手动发布</option>
              </select>
            </label>
            <label><span>风险等级</span>
              <select v-model="form.riskLevel" class="sys-select">
                <option value="low">低</option>
                <option value="medium">中</option>
                <option value="high">高</option>
              </select>
            </label>
            <label><span>失效日期</span><input v-model="form.expireTime" type="date" class="sys-input" /></label>
            <label><span>状态</span>
              <select v-model.number="form.status" class="sys-select">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
            </label>
            <label class="wide"><span>配置值 JSON</span><textarea v-model.trim="form.configValue" class="sys-textarea" rows="3" /></label>
          </div>
        </div>
        <div class="sys-dialog-footer">
          <button class="md-btn" type="button" @click="dialogOpen = false">取消</button>
          <button class="md-btn md-btn-primary" type="button" @click="saveConfig"><CheckCircle2 :size="15" /> 保存</button>
        </div>
      </div>
    </div>
    </Teleport>

  </div>
</template>

<style scoped>
/* =====================================================================
   系统管理 · 系统配置
   ---------------------------------------------------------------------
   设计目标：
   1. 类型 Tab：图标 + 文字 underline 激活态。
   2. 工具栏：搜索框 + 动作按钮组（使用全局 md-btn 主题按钮）。
   3. 表格：统一由 unified-list.css 的 .ulc-table 提供外观，
      配置类型彩色 tag、状态 badge 等本页特有样式在此补充。
   4. 弹窗：统一表单控件。
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
  transition: color 0.15s, border-color 0.15s;
  border-radius: 6px 6px 0 0;
  white-space: nowrap;
}

.sys-tab:hover {
  color: var(--spd-primary);
  background: #f0fdf9;
}

.sys-tab.active {
  color: var(--spd-primary);
  border-bottom-color: var(--spd-primary);
  background: #f0fdf9;
  font-weight: 600;
}

/* ---------- 工具栏 ---------- */
.sys-toolbar-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  flex-wrap: wrap;
}

.sys-toolbar-search {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #fff;
  overflow: hidden;
  flex: 0 0 300px;
  min-width: 0;
}

.sys-toolbar-search-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: #64748b;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.sys-toolbar-search input {
  flex: 1;
  min-width: 0;
  height: 34px;
  border: none;
  background: transparent;
  color: #102033;
  padding: 0 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
}

.sys-toolbar-search input::placeholder { color: #94a3b8; }
.sys-toolbar-search input:focus { outline: none; }

.sys-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.sys-toolbar-sep {
  width: 1px;
  height: 22px;
  background: #e2e8f0;
  margin: 0 4px;
}

.sys-toolbar-select {
  min-height: 34px;
  border: none;
  border-left: 1px solid #e2e8f0;
  background: #f8fafc;
  padding: 0 8px;
  flex-shrink: 0;
  color: #334155;
  font: inherit;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
}

/* 操作列统一使用 .ulc-actions / .ulc-action（见 unified-list.css），
   此处不再自定义按钮样式，避免覆盖三套主题配色。 */

/* ---------- 消息条 ---------- */
.sys-msg {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #334155;
  font-size: var(--spd-font-size-sm);
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}

.sys-error {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: #fee2e2;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  color: #991b1b;
  font-size: var(--spd-font-size-sm);
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}

/* ---------- Tag / Chip / Badge ---------- */
.sys-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  /* 文字居中于色块内；宽度不足时换行而不是横向溢出侵占相邻列 */
  text-align: center;
  max-width: 100%;
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
}

.sys-tag.is-code {
  color: #334155;
  background: #f1f5f9;
  border-color: #e2e8f0;
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-sm);
  /* 配置键是长英文标识，需要在断点处折行 */
  word-break: break-word;
}

/* 配置类型彩色 tag（与 Tab 主题色对应） */
.sys-tag.is-type-blue   { color: #1d4ed8; background: #dbeafe; border-color: #93c5fd; }
.sys-tag.is-type-green  { color: #047857; background: #d1fae5; border-color: #6ee7b7; }
.sys-tag.is-type-purple { color: #6d28d9; background: #ede9fe; border-color: #c4b5fd; }
.sys-tag.is-type-orange { color: #b45309; background: #fef3c7; border-color: #fcd34d; }
.sys-tag.is-type-cyan   { color: #0e7490; background: #cffafe; border-color: #67e8f9; }
.sys-tag.is-type-teal   { color: #0f766e; background: #ccfbf1; border-color: #5eead4; }
.sys-tag.is-type-rose   { color: #be123c; background: #ffe4e6; border-color: #fda4af; }
.sys-tag.is-type-slate  { color: #475569; background: #f1f5f9; border-color: #cbd5e1; }

/* 风险等级 */
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

.sys-chip.is-risk-high {
  color: #991b1b;
  background: #fee2e2;
  border-color: #fca5a5;
}

.sys-chip.is-risk-medium {
  color: #b45309;
  background: #fef3c7;
  border-color: #fcd34d;
}

.sys-chip.is-risk-low {
  color: #065f46;
  background: #d1fae5;
  border-color: #6ee7b7;
}

/* 状态 badge */
.sys-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.sys-badge.is-active {
  color: #065f46;
  background: #d1fae5;
  border: 1px solid #6ee7b7;
}

.sys-badge.is-stopped {
  color: #991b1b;
  background: #fee2e2;
  border: 1px solid #fca5a5;
}

/* ---------- 弹窗 ---------- */
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
  max-width: 560px;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: dialog-in 0.2s ease;
}

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
  border-top: 1px solid #e2e8f0;
  background: #fafdfb;
}

/* ---------- 表单 ---------- */
.sys-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}

.sys-form-grid label {
  display: grid;
  gap: 5px;
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  color: #4b5b66;
}

.sys-form-grid label.wide { grid-column: 1 / -1; }

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
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  transition: border-color 0.15s;
}

.sys-input:focus,
.sys-select:focus {
  outline: none;
  border-color: #cbd5e1;
}

.sys-textarea {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #102033;
  padding: 8px 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  resize: vertical;
  min-height: 80px;
  font-family: var(--spd-font-family-mono);
}

.sys-textarea:focus { outline: none; border-color: #cbd5e1; }

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .sys-toolbar-panel { gap: 10px; }
  .sys-toolbar-search { flex: 1; min-width: 0; }
  .sys-form-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .sys-page { padding: 12px; }
}
</style>
