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
  <section class="system-config-page">
    <header class="master-page-title">
      <div>
        <p>系统管理</p>
        <h2>系统配置</h2>
        <span>为基础信息、流程、权限、参数、审计、数据和系统集成提供统一配置中心。</span>
      </div>
      <button type="button" class="btn" @click="loadConfigs">
        <RefreshCw :size="17" />
        刷新
      </button>
    </header>

    <section class="config-summary-grid">
      <article v-for="item in summary" :key="item.label">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <section class="system-config-layout">
      <aside class="subnav-panel">
        <button
          v-for="item in configTypes"
          :key="item.code"
          type="button"
          :class="{ active: activeType === item.code }"
          @click="selectType(item.code)"
        >
          <Settings :size="17" />
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.desc }}</small>
          </span>
        </button>
      </aside>

      <main class="system-config-main">
        <section class="hospital-catalog-panel">
          <div class="hospital-action-row">
            <button type="button" class="btn btn-primary" @click="openCreate">
              <Plus :size="17" />
              新增配置
            </button>
            <button type="button" class="btn" @click="loadConfigs">
              <ClipboardList :size="17" />
              配置列表查询
            </button>
          </div>

          <form class="hospital-query-grid config-query-grid" @submit.prevent="loadConfigs">
            <label>
              <span>配置关键字</span>
              <input v-model.trim="keyword" type="text" placeholder="配置键 / 适用对象" />
            </label>
            <label>
              <span>状态</span>
              <select v-model="status">
                <option value="">全部</option>
                <option value="启用">启用</option>
                <option value="停用">停用</option>
              </select>
            </label>
            <div class="hospital-query-actions">
              <button type="submit" class="btn btn-primary">
                <Search :size="17" />
                查询
              </button>
              <button type="button" class="btn" @click="resetQuery">重置</button>
            </div>
          </form>
          <StatusMessage :message="actionError" tone="error" />
          <StatusMessage :message="actionMessage" tone="success" />
        </section>

        <section class="master-table-card">
          <SectionTitle :level="3">
            <template #icon>
              <SlidersHorizontal :size="20" />
            </template>
            <h3>配置项</h3>
          </SectionTitle>

          <p v-if="loading" class="approval-empty">正在加载...</p>
          <EmptyState v-else-if="error" :message="error" />
          <table v-else class="master-table config-table">
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
              <tr v-for="row in rows" :key="row.configId">
                <td>{{ row.configTypeName }}</td>
                <td>{{ row.configName }}</td>
                <td>{{ row.scopeType }}</td>
                <td>{{ row.scopeId }}</td>
                <td>{{ row.configKey }}</td>
                <td class="config-value-cell">{{ row.configValue }}</td>
                <td>{{ row.effectiveMode }}</td>
                <td>{{ row.riskLevel }}</td>
                <td>{{ formatStatusText(row.status) }}</td>
                <td>{{ row.updateTime }}</td>
                <td>
                  <button type="button" class="btn-text" @click="openEdit(row)">编辑</button>
                  <button type="button" class="btn-text" @click="toggleStatus(row)">
                    {{ row.status === '启用' ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
              <tr v-if="!rows.length">
                <td class="approval-empty" colspan="11">暂无配置</td>
              </tr>
            </tbody>
          </table>
        </section>
      </main>
    </section>

    <div v-if="dialogOpen" class="attachment-preview-mask" @click.self="dialogOpen = false">
      <section class="supplier-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>系统配置</p>
            <h3>{{ dialogMode === 'create' ? '新增配置' : '编辑配置' }}</h3>
          </div>
          <button type="button" class="btn-icon" aria-label="关闭" @click="dialogOpen = false">
            <X :size="18" />
          </button>
        </header>
        <form class="supplier-form-grid" @submit.prevent="saveConfig">
          <label>
            <span>配置类型</span>
            <select v-model="form.configType">
              <option value="base">基础配置</option>
              <option value="workflow">业务流程配置</option>
              <option value="permission">权限配置</option>
              <option value="parameter">参数配置</option>
              <option value="audit">日志审计</option>
              <option value="data">数据管理</option>
              <option value="integration">系统集成</option>
            </select>
          </label>
          <label>
            <span>适用范围</span>
            <select v-model="form.scopeType">
              <option value="global">全局</option>
              <option value="hospital">医院</option>
              <option value="campus">院区</option>
              <option value="department">科室</option>
              <option value="warehouse">库房</option>
              <option value="role">角色</option>
            </select>
          </label>
          <label>
            <span>适用对象</span>
            <input v-model.trim="form.scopeId" required />
          </label>
          <label>
            <span>配置键</span>
            <input v-model.trim="form.configKey" required />
          </label>
          <label>
            <span>生效方式</span>
            <select v-model="form.effectiveMode">
              <option value="realtime">实时生效</option>
              <option value="scheduled">定时生效</option>
              <option value="manual">手动发布</option>
            </select>
          </label>
          <label>
            <span>风险等级</span>
            <select v-model="form.riskLevel">
              <option value="low">低</option>
              <option value="medium">中</option>
              <option value="high">高</option>
            </select>
          </label>
          <label>
            <span>失效日期</span>
            <input v-model="form.expireTime" type="date" />
          </label>
          <label>
            <span>状态</span>
            <select v-model.number="form.status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
          <label class="wide">
            <span>配置值 JSON</span>
            <textarea v-model.trim="form.configValue" required></textarea>
          </label>
          <div class="product-form-actions wide">
            <button type="button" class="btn" @click="dialogOpen = false">取消</button>
            <button type="submit" class="btn btn-primary">
              <CheckCircle2 :size="17" />
              保存
            </button>
          </div>
        </form>
      </section>
    </div>
  </section>
</template>
