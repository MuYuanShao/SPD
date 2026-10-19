<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Teleport } from 'vue'
import {
  AlertOctagon,
  Check,
  CheckCircle2,
  ClipboardList,
  Cog,
  Database,
  Edit3,
  GitBranch,
  KeyRound,
  Plus,
  RefreshCw,
  Save,
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

const summary = computed(() => {
  const enabled = rows.value.filter((row) => row.status === '启用').length
  const stopped = rows.value.filter((row) => row.status === '停用').length
  const highRisk = rows.value.filter((row) => row.riskLevel === 'high').length
  const workflow = rows.value.filter((row) => row.configType === 'workflow').length
  return [
    { icon: 'list',  theme: 'blue',   label: '配置总数',  value: rows.value.length, hint: '当前筛选条件下' },
    { icon: 'check', theme: 'green',  label: '启用配置',  value: enabled,            hint: '生效中' },
    { icon: 'stop',  theme: 'red',    label: '停用配置',  value: stopped,            hint: '已禁用' },
    { icon: 'risk',  theme: 'orange', label: '高风险配置',value: highRisk,           hint: '需重点关注' },
    { icon: 'flow',  theme: 'purple', label: '流程配置',  value: workflow,           hint: 'workflow 类型' }
  ]
})

function iconFor(code: string) {
  return configTypes.find(t => t.code === code)?.icon ?? 'list'
}
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
  <div class="sys-page">

    <MdPageHeader
      title="系统配置"
      description="为基础信息、流程、权限、参数、审计、数据和系统集成提供统一配置中心。"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadConfigs"
    />

    <!-- 顶部 5 张主题色 KPI 卡 -->
    <section class="sys-kpi-card sys-kpi-card-5" aria-label="系统配置统计">
      <div v-for="item in summary" :key="item.label" class="sys-kpi-item" :class="`is-${item.theme}`">
        <i class="sys-kpi-icon" :class="`is-${item.icon}`">
          <ClipboardList v-if="item.icon === 'list'" :size="18" />
          <Check v-else-if="item.icon === 'check'" :size="18" />
          <X v-else-if="item.icon === 'stop'" :size="18" />
          <AlertOctagon v-else-if="item.icon === 'risk'" :size="18" />
          <Workflow v-else-if="item.icon === 'flow'" :size="18" />
          <Settings v-else :size="18" />
        </i>
        <div>
          <span class="sys-kpi-label">{{ item.label }}</span>
          <strong class="sys-kpi-value">{{ item.value }}</strong>
          <span class="sys-kpi-hint">{{ item.hint }}</span>
        </div>
      </div>
    </section>

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

    <!-- 工具栏 -->
    <div class="sys-toolbar-panel">
      <div class="sys-toolbar-search">
        <i class="sys-toolbar-search-icon"><Search :size="15" /></i>
        <input v-model.trim="keyword" placeholder="配置键 / 配置名" @keyup.enter="loadConfigs" />
        <select v-model="status" style="min-height:34px;border:none;border-left:1px solid #e2e8f0;background:#f8fafc;padding:0 8px;flex-shrink:0">
          <option value="">全部</option>
          <option value="启用">启用</option>
          <option value="停用">停用</option>
        </select>
      </div>
      <div class="sys-toolbar-actions">
        <button class="btn" type="button" @click="resetQuery"><RefreshCw :size="15" /> 重置</button>
        <i class="sys-toolbar-sep"></i>
        <button class="btn btn-primary" type="button" @click="openCreate"><Plus :size="15" /> 新增配置</button>
      </div>
    </div>

    <!-- 消息 -->
    <div v-if="actionMessage" class="sys-msg" role="alert">{{ actionMessage }}</div>
    <div v-if="actionError" class="sys-error" role="alert">{{ actionError }}</div>

    <!-- 配置表 -->
    <div class="sys-table-card">
      <div class="sys-table-scroll">
        <table class="sys-table">
          <thead>
            <tr>
              <th>配置类型</th>
              <th>配置名称</th>
              <th>适用范围</th>
              <th>适用对象</th>
              <th>配置键</th>
              <th>配置值</th>
              <th>生效方式</th>
              <th>风险</th>
              <th>状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="11" class="text-center muted">正在加载...</td></tr>
            <tr v-else-if="!rows.length"><td colspan="11" class="text-center muted">暂无配置</td></tr>
            <tr v-else v-for="row in rows" :key="row.configId">
              <td>
                <span :class="['sys-tag', `is-type-${themeFor(row.configType)}`]">{{ labelFor(row.configType) }}</span>
              </td>
              <td>{{ row.configName }}</td>
              <td>{{ row.scopeType }}</td>
              <td>{{ row.scopeId }}</td>
              <td><span class="sys-tag is-code">{{ row.configKey }}</span></td>
              <td class="config-value-cell" :title="row.configValue">{{ row.configValue }}</td>
              <td>{{ row.effectiveMode }}</td>
              <td>
                <span :class="['sys-chip', `is-risk-${row.riskLevel}`]">
                  {{ row.riskLevel === 'high' ? '高' : row.riskLevel === 'medium' ? '中' : '低' }}
                </span>
              </td>
              <td>
                <span :class="['sys-badge', row.status === '启用' ? 'is-active' : 'is-stopped']">
                  {{ formatStatusText(row.status) }}
                </span>
              </td>
              <td>{{ row.updateTime }}</td>
              <td>
                <button class="btn-text" type="button" @click="openEdit(row)">编辑</button>
                <button class="btn-text" type="button" @click="toggleStatus(row)">{{ row.status === '启用' ? '停用' : '启用' }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 弹窗 -->
    <Teleport to="body">
    <div v-if="dialogOpen" class="sys-dialog-mask" @click.self="dialogOpen = false">
      <div class="sys-dialog">
        <div class="sys-dialog-header">
          <h3>{{ dialogMode === 'create' ? '新增配置' : '编辑配置' }}</h3>
          <button class="btn-icon" type="button" @click="dialogOpen = false"><X :size="16" /></button>
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
          <button class="btn" type="button" @click="dialogOpen = false">取消</button>
          <button class="btn btn-primary" type="button" @click="saveConfig"><CheckCircle2 :size="15" /> 保存</button>
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
   1. 顶部 5 张主题色 KPI 卡。
   2. Tab：图标 + 文字 underline 激活态（绿色）。
   3. 工具栏：搜索框 + 动作按钮组。
   4. 表格：浅绿渐变表头，配置类型彩色 tag，状态 badge，配置值 monospace 省略。
   5. 弹窗：统一表单控件。
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

/* ---------- 顶部 5 项 KPI 摘要 ---------- */
.sys-kpi-card {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.sys-kpi-card-5 {
  grid-template-columns: repeat(5, minmax(0, 1fr));
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
.sys-kpi-item.is-red {
  background: linear-gradient(180deg, #fee2e2 0%, #fecaca 100%);
  border-color: #fca5a5;
}
.sys-kpi-item.is-orange {
  background: linear-gradient(180deg, #fef3c7 0%, #fde68a 100%);
  border-color: #fcd34d;
}
.sys-kpi-item.is-purple {
  background: linear-gradient(180deg, #ede9fe 0%, #ddd6fe 100%);
  border-color: #c4b5fd;
}
.sys-kpi-item.is-cyan {
  background: linear-gradient(180deg, #cffafe 0%, #a5f3fc 100%);
  border-color: #67e8f9;
}
.sys-kpi-item.is-teal {
  background: linear-gradient(180deg, #ccfbf1 0%, #99f6e4 100%);
  border-color: #5eead4;
}
.sys-kpi-item.is-rose {
  background: linear-gradient(180deg, #ffe4e6 0%, #fecdd3 100%);
  border-color: #fda4af;
}
.sys-kpi-item.is-slate {
  background: linear-gradient(180deg, #e2e8f0 0%, #cbd5e1 100%);
  border-color: #94a3b8;
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
.sys-kpi-item.is-green .sys-kpi-icon { color: #047857; }
.sys-kpi-item.is-red    .sys-kpi-icon { color: #b91c1c; }
.sys-kpi-item.is-orange .sys-kpi-icon { color: #b45309; }
.sys-kpi-item.is-purple .sys-kpi-icon { color: #6d28d9; }
.sys-kpi-item.is-cyan   .sys-kpi-icon { color: #0e7490; }
.sys-kpi-item.is-teal   .sys-kpi-icon { color: #0f766e; }
.sys-kpi-item.is-rose   .sys-kpi-icon { color: #be123c; }
.sys-kpi-item.is-slate  .sys-kpi-icon { color: #475569; }

.sys-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.sys-kpi-label {
  font-size: 12px;
  font-weight: 500;
  opacity: 0.85;
}

.sys-kpi-value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.sys-kpi-hint {
  font-size: 11px;
  font-weight: 400;
  line-height: 1.3;
  opacity: 0.75;
}

.sys-kpi-item.is-blue   .sys-kpi-label,
.sys-kpi-item.is-blue   .sys-kpi-value,
.sys-kpi-item.is-blue   .sys-kpi-hint { color: #1e3a8a; }
.sys-kpi-item.is-green .sys-kpi-label,
.sys-kpi-item.is-green .sys-kpi-value,
.sys-kpi-item.is-green .sys-kpi-hint { color: #065f46; }
.sys-kpi-item.is-red    .sys-kpi-label,
.sys-kpi-item.is-red    .sys-kpi-value,
.sys-kpi-item.is-red    .sys-kpi-hint { color: #7f1d1d; }
.sys-kpi-item.is-orange .sys-kpi-label,
.sys-kpi-item.is-orange .sys-kpi-value,
.sys-kpi-item.is-orange .sys-kpi-hint { color: #78350f; }
.sys-kpi-item.is-purple .sys-kpi-label,
.sys-kpi-item.is-purple .sys-kpi-value,
.sys-kpi-item.is-purple .sys-kpi-hint { color: #4c1d95; }
.sys-kpi-item.is-cyan   .sys-kpi-label,
.sys-kpi-item.is-cyan   .sys-kpi-value,
.sys-kpi-item.is-cyan   .sys-kpi-hint { color: #155e75; }
.sys-kpi-item.is-teal   .sys-kpi-label,
.sys-kpi-item.is-teal   .sys-kpi-value,
.sys-kpi-item.is-teal   .sys-kpi-hint { color: #134e4a; }
.sys-kpi-item.is-rose   .sys-kpi-label,
.sys-kpi-item.is-rose   .sys-kpi-value,
.sys-kpi-item.is-rose   .sys-kpi-hint { color: #881337; }
.sys-kpi-item.is-slate  .sys-kpi-label,
.sys-kpi-item.is-slate  .sys-kpi-value,
.sys-kpi-item.is-slate  .sys-kpi-hint { color: #1e293b; }

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
  font-size: 13px;
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

.btn:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #94a3b8;
  color: #1e293b;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

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

.btn-icon:hover {
  background: #f1f5f9;
  color: #334155;
}

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

/* ---------- 消息条 ---------- */
.sys-msg {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #334155;
  font-size: 13px;
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
  font-size: 13px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}

/* ---------- 表格 ---------- */
.sys-table-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

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

.text-center { text-align: center; }
.muted { color: #94a3b8; padding: 32px; font-size: 13px; }

/* ---------- Tag / Chip / Badge ---------- */
.sys-tag {
  display: inline-flex;
  align-items: center;
  border: 1px solid;
  border-radius: 999px;
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.sys-tag.is-code {
  color: #334155;
  background: #f1f5f9;
  border-color: #e2e8f0;
  font-family: var(--spd-font-mono), monospace;
  font-size: 11.5px;
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
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid;
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
  font-size: 12px;
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

.config-value-cell {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--spd-font-mono), monospace;
  font-size: 12px;
  color: #475569;
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

/* ---------- 表单 ---------- */
.sys-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}

.sys-form-grid label {
  display: grid;
  gap: 5px;
  font-size: 12px;
  font-weight: 700;
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
  font-size: 13px;
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
  font-size: 13px;
  font-weight: 500;
  resize: vertical;
  min-height: 80px;
  font-family: var(--spd-font-mono), monospace;
}

.sys-textarea:focus { outline: none; border-color: #cbd5e1; }

/* ---------- 响应式 ---------- */
@media (max-width: 1400px) {
  .sys-kpi-card-5 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 1200px) {
  .sys-kpi-card { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .sys-kpi-card-5 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .sys-toolbar-panel { gap: 10px; }
  .sys-toolbar-search { flex: 1; min-width: 0; }
  .sys-form-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .sys-kpi-card,
  .sys-kpi-card-5 { grid-template-columns: 1fr; }
  .sys-page { padding: 12px; }
}
</style>
