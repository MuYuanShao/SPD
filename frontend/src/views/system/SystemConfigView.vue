<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, ClipboardList, Edit3, Plus, RefreshCw, Search, Settings, SlidersHorizontal, X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import {
  createSystemConfig,
  fetchSystemConfigs,
  updateSystemConfig,
  updateSystemConfigStatus,
  type SystemConfigPayload,
  type SystemConfigRow
} from '../../api/systemConfig'
import EmptyState from '../../components/common/EmptyState.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import SectionTitle from '../../components/common/SectionTitle.vue'
import StatusMessage from '../../components/common/StatusMessage.vue'

const configTypes = [
  { code: '', label: '全部配置', desc: '查看所有配置项' },
  { code: 'base', label: '基础配置', desc: '医院信息、组织、仓库' },
  { code: 'workflow', label: '业务流程配置', desc: '审批流程、业务规则' },
  { code: 'permission', label: '权限配置', desc: '角色、菜单、按钮、数据权限' },
  { code: 'parameter', label: '参数配置', desc: '系统、业务、预警参数' },
  { code: 'audit', label: '日志审计', desc: '操作、登录、异常日志' },
  { code: 'data', label: '数据管理', desc: '数据字典、编码、备份' },
  { code: 'integration', label: '系统集成', desc: '接口、定时任务、消息通知' }
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
  const highRisk = rows.value.filter((row) => row.riskLevel === 'high').length
  const workflow = rows.value.filter((row) => row.configType === 'workflow').length
  return [
    { label: '配置总数', value: rows.value.length },
    { label: '启用配置', value: enabled },
    { label: '高风险配置', value: highRisk },
    { label: '流程配置', value: workflow }
  ]
})

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
  <div class="md-page sys-page">
    <MdPageHeader
      crumb="系统管理"
      title="系统配置"
      description="为基础信息、流程、权限、参数、审计、数据和系统集成提供统一配置中心。"
      :loading="loading"
      @refresh="loadConfigs"
    />

    <!-- 汇总 -->
    <div class="md-content sys-content">
      <div class="sys-stat-grid">
        <article v-for="item in summary" :key="item.label">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </div>

      <!-- 类型导航 -->
      <div class="md-tabs sys-tabs" style="margin:0">
        <button
          v-for="item in configTypes"
          :key="item.code"
          type="button"
          :class="['md-tab', { active: activeType === item.code }]"
          @click="selectType(item.code)"
        >
          {{ item.label }}
        </button>
      </div>

      <!-- 工具栏 -->
      <div class="md-toolbar sys-toolbar" style="margin:0">
        <div class="sys-search-wrap">
          <span class="sys-search-label">关键词</span>
          <div class="sys-search-input-wrap">
            <input v-model.trim="keyword" class="sys-search-input" placeholder="配置键 / 配置名" @keyup.enter="loadConfigs" />
            <button class="sys-search-btn" type="button" @click="loadConfigs"><Search :size="15" /></button>
          </div>
          <select v-model="status" class="md-select" style="min-height:34px;width:auto;flex-shrink:0">
            <option value="">全部</option>
            <option value="启用">启用</option>
            <option value="停用">停用</option>
          </select>
        </div>
        <div class="sys-toolbar-right">
          <button class="md-btn md-btn-primary" type="button" @click="openCreate"><Plus :size="15" /> 新增配置</button>
        </div>
      </div>

      <div v-if="actionError" class="md-error">{{ actionError }}</div>
      <div v-if="actionMessage" class="md-message">{{ actionMessage }}</div>

      <div class="md-table-card">
        <div class="table-scroll">
          <table class="md-table">
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
                <td>{{ row.configTypeName }}</td>
                <td>{{ row.configName }}</td>
                <td>{{ row.scopeType }}</td>
                <td>{{ row.scopeId }}</td>
                <td>{{ row.configKey }}</td>
                <td class="config-value-cell">{{ row.configValue }}</td>
                <td>{{ row.effectiveMode }}</td>
                <td><span :class="['md-chip', row.riskLevel === 'high' ? 'danger' : '']">{{ row.riskLevel }}</span></td>
                <td><span :class="['md-status', row.status === '启用' ? 'success' : 'danger']">{{ formatStatusText(row.status) }}</span></td>
                <td>{{ row.updateTime }}</td>
                <td>
                  <button class="md-btn-text" type="button" @click="openEdit(row)">编辑</button>
                  <button class="md-btn-text" type="button" @click="toggleStatus(row)">{{ row.status === '启用' ? '停用' : '启用' }}</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 弹窗 -->
    <div v-if="dialogOpen" class="md-dialog-mask" @click.self="dialogOpen = false">
      <div class="md-dialog">
        <div class="md-dialog-header">
          <h3>{{ dialogMode === 'create' ? '新增配置' : '编辑配置' }}</h3>
          <button class="md-btn-icon" type="button" @click="dialogOpen = false"><X :size="16" /></button>
        </div>
        <div class="md-dialog-body">
          <div class="md-form-grid">
            <label><span>配置类型</span><select v-model="form.configType" class="md-select"><option value="base">基础配置</option><option value="workflow">业务流程配置</option><option value="permission">权限配置</option><option value="parameter">参数配置</option><option value="audit">日志审计</option><option value="data">数据管理</option><option value="integration">系统集成</option></select></label>
            <label><span>适用范围</span><select v-model="form.scopeType" class="md-select"><option value="global">全局</option><option value="department">科室</option><option value="warehouse">库房</option><option value="role">角色</option></select></label>
            <label><span>适用对象</span><input v-model.trim="form.scopeId" class="md-input" /></label>
            <label><span>配置键</span><input v-model.trim="form.configKey" class="md-input" /></label>
            <label><span>生效方式</span><select v-model="form.effectiveMode" class="md-select"><option value="realtime">实时生效</option><option value="scheduled">定时生效</option><option value="manual">手动发布</option></select></label>
            <label><span>风险等级</span><select v-model="form.riskLevel" class="md-select"><option value="low">低</option><option value="medium">中</option><option value="high">高</option></select></label>
            <label><span>失效日期</span><input v-model="form.expireTime" type="date" class="md-input" /></label>
            <label><span>状态</span><select v-model.number="form.status" class="md-select"><option :value="1">启用</option><option :value="0">停用</option></select></label>
            <label class="wide"><span>配置值 JSON</span><textarea v-model.trim="form.configValue" class="md-textarea" rows="3" /></label>
          </div>
        </div>
        <div class="md-dialog-footer">
          <button class="md-btn" type="button" @click="dialogOpen = false">取消</button>
          <button class="md-btn md-btn-primary" type="button" @click="saveConfig"><CheckCircle2 :size="15" /> 保存</button>
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
.sys-tabs { overflow-x: auto; }
.sys-toolbar { margin: 0; }
.table-scroll { overflow-x: auto; }
.text-center { text-align: center; }
.muted { color: var(--md-text-muted); padding: 32px; }
.config-value-cell { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: var(--md-font-mono); font-size: 12px; }
</style>
