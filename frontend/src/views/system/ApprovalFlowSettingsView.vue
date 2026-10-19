<script setup lang="ts">
/**
 * 审批流设置（系统管理 / 审批流设置）
 * ---------------------------------------------------------------------
 * 交互模式：
 *   - 页面只展示已配置的审批流列表（行卡：菜单 + 节点 + 步骤摘要 + 状态）。
 *   - 工具栏"新增审批流" / 列表行"编辑"  →  弹出审批流弹窗。
 *   - 弹窗内编辑：基本信息（菜单/节点/作用域/数据隔离）+ 审批步骤表。
 *   - "保存"  写回后端，刷新列表。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { Teleport } from 'vue'
import {
  AlertCircle,
  Check,
  CheckCircle2,
  ClipboardCheck,
  Edit3,
  FileText,
  GitBranch,
  GitMerge,
  Layers,
  LayoutDashboard,
  ListTree,
  Plus,
  RefreshCw,
  Save,
  Search,
  Settings2,
  Trash2,
  X
} from '@lucide/vue'
import {
  createApprovalFlow,
  fetchApprovalFlowOptions,
  fetchApprovalFlows,
  updateApprovalFlow,
  type ApprovalFlowNodeOption,
  type ApprovalFlowOptions,
  type ApprovalFlowPayload,
  type ApprovalFlowRow,
  type ApprovalFlowStep
} from '../../api/approvalFlows'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import { useNotify } from '../../composables/useNotify'

const notify = useNotify()


const scopeOptions = [
  { value: 'global',     label: '全局' },
  { value: 'department', label: '科室' },
  { value: 'warehouse',  label: '库房' },
  { value: 'role',       label: '角色' }
]

const dataScopeOptions = [
  { value: 1, label: '全部数据'   },
  { value: 2, label: '本部门数据' },
  { value: 3, label: '本部门及下级' },
  { value: 4, label: '仅本人数据' }
]

const featureNameMap: Record<string, string> = {
  'pending-product-catalog': '待审批目录',
  'purchase-management':     '采购管理',
  'receiving-acceptance':    '收货验收',
  'department-requisition':  '科室申领',
  'stocktaking-management':  '盘点管理',
  'batch-price-adjustment':  '价格调整',
  'settlement-reconciliation': '结算对账'
}

const nodeNameMap: Record<string, string> = {
  'initial-review':           '目录顺序审批',
  'demand-review':            '采购需求审核',
  'plan-approval':            '采购计划审批',
  'order-approval':           '采购订单审批',
  'receiving-approval':       '收货验收审批',
  'requisition-approval':     '申领审批',
  'stocktaking-approval':     '盘点审批',
  'price-adjustment-approval':'调价审批',
  'settlement-confirm':       '结算确认'
}

// ── 数据 ─────────────────────────────────────────────────────────────────────
const rows = ref<ApprovalFlowRow[]>([])
const options = ref<ApprovalFlowOptions>({
  nodes: [],
  roles: [],
  users: [],
  departments: []
})
const loading = ref(false)
const saving = ref(false)
const message = ref('')
const error = ref('')

const keyword = ref('')
const status = ref('')
const activeFeatureCode = ref('')

// ── 弹窗状态 ─────────────────────────────────────────────────────────────────
const dialogOpen = ref(false)
const dialogMode = ref<'new' | 'edit'>('new')
const editingFlowId = ref<number | null>(null)
const dialogForm = reactive<ApprovalFlowPayload>({
  featureCode: '',
  featureName: '',
  nodeCode: '',
  nodeName: '',
  scopeType: 'global',
  scopeId: 'default',
  dataScope: 1,
  status: 1,
  remark: '',
  steps: []
})

// ── 计算 ─────────────────────────────────────────────────────────────────────
const kpiStats = computed(() => [
  {
    icon: 'flow', theme: 'blue',
    label: '审批流总数',  value: rows.value.length, hint: '当前筛选条件'
  },
  {
    icon: 'check', theme: 'green',
    label: '已启用',      value: rows.value.filter(r => r.status === 1).length, hint: '正常流转中'
  },
  {
    icon: 'step', theme: 'orange',
    label: '审批步骤',    value: rows.value.reduce((sum, r) => sum + (r.steps?.length ?? 0), 0), hint: '所有流程步骤合计'
  },
  {
    icon: 'module', theme: 'purple',
    label: '覆盖模块',    value: featureGroups.value.length, hint: '已配置审批的模块数'
  }
])

const featureGroups = computed(() => {
  const map = new Map<string, { featureCode: string; featureName: string; count: number }>()
  for (const node of options.value.nodes) {
    if (!map.has(node.featureCode)) {
      map.set(node.featureCode, {
        featureCode: node.featureCode,
        featureName: featureLabel(node),
        count: 0
      })
    }
  }
  for (const row of rows.value) {
    const item = map.get(row.featureCode)
    if (item) item.count += 1
  }
  return Array.from(map.values())
})

const featureNodes = computed(() =>
  activeFeatureCode.value
    ? options.value.nodes.filter((node) => node.featureCode === activeFeatureCode.value)
    : options.value.nodes
)

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter((row) =>
    row.featureName?.toLowerCase().includes(kw) ||
    row.nodeName?.toLowerCase().includes(kw) ||
    (row.remark ?? '').toLowerCase().includes(kw) ||
    (row.scopeId ?? '').toLowerCase().includes(kw)
  )
})

// 弹窗内可用的菜单列表（按"全部菜单"或当前活动菜单过滤）
const dialogNodeOptions = computed(() => {
  // 编辑模式锁定原菜单；新增模式可切
  if (dialogMode.value === 'edit' && dialogForm.featureCode) {
    return options.value.nodes.filter(n => n.featureCode === dialogForm.featureCode)
  }
  return options.value.nodes
})

const dialogFeatureCode = computed({
  get: () => dialogForm.featureCode,
  set: (v: string) => {
    const node = options.value.nodes.find(n => n.featureCode === v)
    if (!node) return
    dialogForm.featureCode = node.featureCode
    dialogForm.featureName = featureLabel(node)
    dialogForm.nodeCode = node.nodeCode
    dialogForm.nodeName = nodeLabel(node)
  }
})

// 弹窗内步骤的若干只读统计
const stepSummary = computed(() => {
  const total = dialogForm.steps.length
  const enabled = dialogForm.steps.filter(s => s.status === 1).length
  return { total, enabled }
})

// ── 工具函数 ─────────────────────────────────────────────────────────────────
function cleanText(value: string | undefined, fallback: string) {
  if (!value || /\?{2,}|�|锟/.test(value)) {
    return fallback
  }
  return value
}

function featureLabel(item: { featureCode: string; featureName?: string }) {
  return cleanText(item.featureName, featureNameMap[item.featureCode] ?? '审批流')
}

function nodeLabel(item: { nodeCode: string; nodeName?: string }) {
  return cleanText(item.nodeName, nodeNameMap[item.nodeCode] ?? '审批节点')
}

function scopeName(value?: string) {
  return scopeOptions.find(s => s.value === value)?.label ?? value ?? '-'
}

function dataScopeName(value?: number) {
  return dataScopeOptions.find(s => s.value === value)?.label ?? '全部数据'
}

function roleName(roleId?: number) {
  return options.value.roles.find(r => r.roleId === roleId)?.roleName ?? '未指定角色'
}

function userName(userId?: number) {
  return options.value.users.find(u => u.userId === userId)?.realName ?? '未指定人员'
}

function deptName(deptId?: number) {
  return options.value.departments.find(d => d.deptId === deptId)?.deptName ?? '单据科室负责人'
}

function emptyStep(order: number): ApprovalFlowStep {
  return {
    stepOrder: order,
    stepName: `第 ${order} 级审批`,
    approverType: 'role',
    minApprovals: 1,
    allowSelfApprove: false,
    dataScope: 1,
    status: 1
  }
}

function copyStep(step: ApprovalFlowStep): ApprovalFlowStep {
  return {
    stepId: step.stepId,
    stepOrder: step.stepOrder,
    stepName: step.stepName,
    approverType: step.approverType,
    roleId: step.roleId,
    userId: step.userId,
    deptId: step.deptId,
    minApprovals: step.minApprovals,
    allowSelfApprove: step.allowSelfApprove,
    dataScope: step.dataScope,
    status: step.status
  }
}

// ── 加载 ─────────────────────────────────────────────────────────────────────
async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [optionRows, flowRows] = await Promise.all([
      fetchApprovalFlowOptions(),
      fetchApprovalFlows({
        featureCode: activeFeatureCode.value,
        keyword: keyword.value,
        status: status.value
      })
    ])
    options.value = optionRows
    rows.value = flowRows
  } catch (err) {
    error.value = err instanceof Error ? err.message : '审批流配置加载失败'
  } finally {
    loading.value = false
  }
}

function selectFeature(featureCode: string) {
  activeFeatureCode.value = featureCode
  loadAll()
}

function resetQuery() {
  keyword.value = ''
  status.value = ''
  loadAll()
}

// ── 弹窗 ─────────────────────────────────────────────────────────────────────
function openCreateDialog() {
  notify.success('')
  error.value = ''
  dialogMode.value = 'new'
  editingFlowId.value = null

  // 默认选当前活动菜单的第一个节点；若无则选系统首个节点
  const targetNode = (activeFeatureCode.value
    ? options.value.nodes.find(n => n.featureCode === activeFeatureCode.value)
    : options.value.nodes[0]) ?? options.value.nodes[0]

  Object.assign(dialogForm, {
    featureCode: targetNode?.featureCode ?? '',
    featureName: targetNode ? featureLabel(targetNode) : '',
    nodeCode: targetNode?.nodeCode ?? '',
    nodeName: targetNode ? nodeLabel(targetNode) : '',
    scopeType: 'global',
    scopeId: 'default',
    dataScope: 1,
    status: 1,
    remark: '',
    deptId: undefined,
    steps: [emptyStep(1)]
  })
  dialogOpen.value = true
}

function openEditDialog(row: ApprovalFlowRow) {
  notify.success('')
  error.value = ''
  dialogMode.value = 'edit'
  editingFlowId.value = row.flowId
  Object.assign(dialogForm, {
    featureCode: row.featureCode,
    featureName: featureLabel(row),
    nodeCode: row.nodeCode,
    nodeName: nodeLabel(row),
    scopeType: row.scopeType,
    scopeId: row.scopeId,
    dataScope: row.dataScope,
    status: row.status,
    remark: row.remark ?? '',
    deptId: row.deptId,
    steps: row.steps.length ? row.steps.map(step => copyStep(step)) : [emptyStep(1)]
  })
  dialogOpen.value = true
}

function closeDialog() {
  dialogOpen.value = false
  editingFlowId.value = null
  dialogMode.value = 'new'
}

function addStep() {
  const nextOrder = Math.max(0, ...dialogForm.steps.map(s => Number(s.stepOrder) || 0)) + 1
  dialogForm.steps.push(emptyStep(nextOrder))
}

function removeStep(index: number) {
  if (dialogForm.steps.length === 1) {
    error.value = '审批流至少需要一个步骤'
    return
  }
  dialogForm.steps.splice(index, 1)
  dialogForm.steps.forEach((step, i) => { step.stepOrder = i + 1 })
}

function normalizeStepOrders() {
  dialogForm.steps.forEach((step, i) => {
    step.stepOrder = Number(step.stepOrder) || i + 1
  })
}

function validateSteps(): string {
  const orders = new Set<number>()
  for (const step of dialogForm.steps) {
    const order = Number(step.stepOrder)
    if (!Number.isInteger(order) || order < 1) return '审批顺序必须填写大于 0 的整数'
    if (orders.has(order)) return '审批顺序不能重复，请调整后再保存'
    orders.add(order)
    if (!step.stepName?.trim()) return '审批步骤名称不能为空'
    if (step.approverType === 'role' && !step.roleId) return '按角色审批时必须选择角色'
    if (step.approverType === 'user' && !step.userId) return '指定人员审批时必须选择人员'
  }
  return ''
}

function setStepApproverType(step: ApprovalFlowStep, type: string) {
  step.approverType = type
}

async function saveDialog() {
  notify.success('')
  error.value = ''
  normalizeStepOrders()
  const validationMessage = validateSteps()
  if (validationMessage) {
    error.value = validationMessage
    return
  }
  const orderedSteps = [...dialogForm.steps]
    .map(step => copyStep(step))
    .sort((a, b) => Number(a.stepOrder) - Number(b.stepOrder))

  const payload: ApprovalFlowPayload = {
    featureCode: dialogForm.featureCode,
    featureName: dialogForm.featureName,
    nodeCode: dialogForm.nodeCode,
    nodeName: dialogForm.nodeName,
    scopeType: dialogForm.scopeType,
    scopeId: dialogForm.scopeId || 'default',
    dataScope: Number(dialogForm.dataScope),
    status: Number(dialogForm.status),
    remark: dialogForm.remark,
    deptId: dialogForm.deptId,
    steps: orderedSteps
  }

  saving.value = true
  try {
    if (editingFlowId.value) {
      await updateApprovalFlow(editingFlowId.value, payload)
      notify.success('审批流配置已更新')
    } else {
      await createApprovalFlow(payload)
      notify.success('审批流已新增')
    }
    dialogOpen.value = false
    await loadAll()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '审批流保存失败'
  } finally {
    saving.value = false
  }
}

// 状态切换（行内快捷）
async function toggleStatus(row: ApprovalFlowRow) {
  const next = row.status === 1 ? 0 : 1
  try {
    const res = await import('../../api/approvalFlows')
    await res.updateApprovalFlowStatus(row.flowId, next)
    message.value = next === 1 ? '审批流已启用' : '审批流已停用'
    await loadAll()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '状态切换失败'
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="sys-page">

    <MdPageHeader
      title="审批流设置"
      description="维护各业务菜单的审批节点、参与角色、人员与数据隔离范围。"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadAll"
    />

    <!-- 顶部 4 项 KPI 摘要 -->
    <section class="sys-kpi-card" aria-label="审批流统计">
      <div v-for="item in kpiStats" :key="item.label" class="sys-kpi-item" :class="`is-${item.theme}`">
        <i class="sys-kpi-icon" :class="`is-${item.icon}`">
          <GitBranch v-if="item.icon === 'flow'" :size="18" />
          <CheckCircle2 v-else-if="item.icon === 'check'" :size="18" />
          <ListTree v-else-if="item.icon === 'step'" :size="18" />
          <LayoutDashboard v-else-if="item.icon === 'module'" :size="18" />
          <AlertCircle v-else :size="18" />
        </i>
        <div>
          <span class="sys-kpi-label">{{ item.label }}</span>
          <strong class="sys-kpi-value">{{ item.value }}</strong>
          <span class="sys-kpi-hint">{{ item.hint }}</span>
        </div>
      </div>
    </section>

    <!-- Tab（菜单分组） -->
    <div class="sys-tabs">
      <button type="button" :class="['sys-tab', { active: activeFeatureCode === '' }]" @click="selectFeature('')">
        <Layers :size="14" /> 全部菜单
        <span class="sys-tab-count">{{ rows.length }}</span>
      </button>
      <button v-for="feature in featureGroups" :key="feature.featureCode" type="button"
        :class="['sys-tab', { active: activeFeatureCode === feature.featureCode }]"
        @click="selectFeature(feature.featureCode)">
        <ClipboardCheck :size="14" /> {{ featureLabel(feature) }}
        <span class="sys-tab-count">{{ feature.count }}</span>
      </button>
    </div>

    <!-- 工具栏 -->
    <div class="sys-toolbar-panel">
      <div class="sys-toolbar-search">
        <i class="sys-toolbar-search-icon"><Search :size="15" /></i>
        <input v-model.trim="keyword" placeholder="菜单 / 节点 / 备注 / 作用对象" @keyup.enter="loadAll" />
        <select v-model="status" class="sys-toolbar-status">
          <option value="">全部状态</option>
          <option value="1">启用</option>
          <option value="0">停用</option>
        </select>
      </div>
      <div class="sys-toolbar-actions">
        <button class="btn" type="button" @click="resetQuery"><RefreshCw :size="15" /> 重置</button>
        <i class="sys-toolbar-sep"></i>
        <button class="btn btn-primary" type="button" @click="openCreateDialog"><Plus :size="15" /> 新增审批流</button>
      </div>
    </div>

    <!-- 消息 -->
    <!-- 消息：操作反馈统一走顶部 ElMessage 弹层 -->
    <div v-if="error" class="sys-error" role="alert">{{ error }}</div>

    <!-- 列表：每一项是一张"行卡" -->
    <div v-if="loading" class="sys-loading-card">
      <i class="sys-loading-icon"><RefreshCw :size="20" /></i>
      <p>正在加载审批流...</p>
    </div>
    <div v-else-if="!filteredRows.length" class="sys-empty">
      <i class="sys-empty-icon"><GitBranch :size="36" /></i>
      <p>当前筛选下暂无审批流配置</p>
      <button class="btn btn-primary" type="button" @click="openCreateDialog">
        <Plus :size="15" /> 新增审批流
      </button>
    </div>
    <div v-else class="sys-flow-list">
      <article
        v-for="row in filteredRows"
        :key="row.flowId"
        class="sys-flow-card"
        :class="{ 'is-stopped': row.status === 0 }"
      >
        <div class="sys-flow-card-head">
          <div class="sys-flow-card-title">
            <span class="sys-tag is-menu">{{ row.featureName || featureLabel(row) }}</span>
            <i class="sys-flow-card-arrow"><GitMerge :size="13" /></i>
            <span class="sys-tag is-node">{{ row.nodeName || nodeLabel(row) }}</span>
            <span :class="['sys-badge', row.status === 1 ? 'is-active' : 'is-stopped']">
              {{ row.status === 1 ? '启用' : '停用' }}
            </span>
            <span class="sys-tag is-scope">{{ scopeName(row.scopeType) }} · {{ row.scopeId }}</span>
          </div>
          <div class="sys-flow-card-actions">
            <button class="btn-text" type="button" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </button>
            <button class="btn-text btn-text-primary" type="button" @click="openEditDialog(row)">
              <Edit3 :size="13" /> 编辑
            </button>
          </div>
        </div>
        <div class="sys-flow-card-meta">
          <span><i class="sys-flow-card-meta-icon"><Layers :size="12" /></i> {{ row.steps.length }} 级审批</span>
          <span><i class="sys-flow-card-meta-icon"><Settings2 :size="12" /></i> 数据隔离 {{ dataScopeName(row.dataScope) }}</span>
          <span v-if="row.remark"><i class="sys-flow-card-meta-icon"><FileText :size="12" /></i> {{ row.remark }}</span>
        </div>
        <ol class="sys-flow-steps">
          <li v-for="(step, i) in row.steps" :key="i" class="sys-flow-step">
            <span class="sys-flow-step-num">{{ step.stepOrder }}</span>
            <span class="sys-flow-step-name">{{ step.stepName }}</span>
            <span class="sys-flow-step-approver">
              <template v-if="step.approverType === 'role'">角色：{{ roleName(step.roleId) }}</template>
              <template v-else-if="step.approverType === 'user'">人员：{{ userName(step.userId) }}</template>
              <template v-else>部门：{{ deptName(step.deptId) }}</template>
            </span>
            <span class="sys-flow-step-stat">
              最少 {{ step.minApprovals }} 通过 · {{ dataScopeName(step.dataScope) }}
            </span>
            <span :class="['sys-badge', step.status === 1 ? 'is-active' : 'is-stopped']">
              {{ step.status === 1 ? '启用' : '停用' }}
            </span>
          </li>
        </ol>
      </article>
    </div>

    <!-- 新增 / 编辑 弹窗 -->
    <Teleport to="body">
    <div v-if="dialogOpen" class="sys-dialog-mask" @click.self="closeDialog">
      <div class="sys-dialog sys-dialog-xl" role="dialog" aria-modal="true">
        <div class="sys-dialog-header">
          <div>
            <h3>{{ dialogMode === 'edit' ? '编辑审批流' : '新增审批流' }}</h3>
            <p>绑定菜单与作用域，并配置多级审批步骤</p>
          </div>
          <button class="btn-icon" type="button" @click="closeDialog"><X :size="16" /></button>
        </div>
        <div class="sys-dialog-body">

          <!-- 弹窗第 1 段：基本信息 -->
          <section class="sys-dialog-section">
            <div class="sys-dialog-section-title">
              <i class="sys-dialog-section-icon is-green"><GitMerge :size="13" /></i>
              <h4>基本信息</h4>
            </div>
            <div class="sys-form-grid">
              <label><span>菜单界面</span>
                <select v-model="dialogFeatureCode" :disabled="dialogMode === 'edit'" class="sys-select">
                  <option v-for="node in dialogNodeOptions" :key="node.featureCode" :value="node.featureCode">
                    {{ featureLabel(node) }}
                  </option>
                </select>
              </label>
              <label><span>审批节点</span>
                <input :value="dialogForm.nodeName" readonly class="sys-input" />
              </label>
              <label><span>适用范围</span>
                <select v-model="dialogForm.scopeType" class="sys-select">
                  <option v-for="scope in scopeOptions.filter(item => dialogForm.featureCode !== 'pending-product-catalog' || item.value !== 'warehouse')"
                    :key="scope.value" :value="scope.value">{{ scope.label }}</option>
                </select>
              </label>
              <label><span>适用对象</span>
                <input v-model.trim="dialogForm.scopeId" class="sys-input" placeholder="default" />
              </label>
              <label><span>流程数据隔离</span>
                <select v-model.number="dialogForm.dataScope" class="sys-select">
                  <option v-for="scope in dataScopeOptions" :key="scope.value" :value="scope.value">{{ scope.label }}</option>
                </select>
              </label>
              <label><span>流程状态</span>
                <select v-model.number="dialogForm.status" class="sys-select">
                  <option :value="1">启用</option>
                  <option :value="0">停用</option>
                </select>
              </label>
              <label class="sys-form-wide"><span>备注</span>
                <input v-model.trim="dialogForm.remark" class="sys-input" placeholder="可填写该流程的用途、触发条件等" />
              </label>
            </div>
          </section>

          <!-- 弹窗第 2 段：审批步骤 -->
          <section class="sys-dialog-section">
            <div class="sys-dialog-section-title">
              <i class="sys-dialog-section-icon is-blue"><ListTree :size="13" /></i>
              <h4>审批步骤</h4>
              <span class="sys-dialog-section-meta">
                共 <strong>{{ stepSummary.total }}</strong> 级 ·
                启用 <strong>{{ stepSummary.enabled }}</strong> 级
              </span>
              <button class="btn-text btn-text-primary" type="button" @click="addStep">
                <Plus :size="13" /> 新增步骤
              </button>
            </div>
            <div class="sys-table-scroll">
              <table class="sys-table is-compact">
                <thead>
                  <tr>
                    <th style="width:80px">顺序</th>
                    <th>步骤名称</th>
                    <th>角色参与</th>
                    <th>人员参与</th>
                    <th>部门参与</th>
                    <th style="width:80px">最少通过</th>
                    <th>数据隔离</th>
                    <th style="width:80px">状态</th>
                    <th style="width:80px">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(step, index) in dialogForm.steps" :key="index">
                    <td><input v-model.number="step.stepOrder" min="1" type="number" class="sys-input is-tiny" /></td>
                    <td><input v-model.trim="step.stepName" class="sys-input" /></td>
                    <td>
                      <select v-model.number="step.roleId" class="sys-select" @change="setStepApproverType(step, 'role')">
                        <option :value="undefined">不指定角色</option>
                        <option v-for="r in options.roles" :key="r.roleId" :value="r.roleId">{{ r.roleName }}</option>
                      </select>
                    </td>
                    <td>
                      <select v-model.number="step.userId" class="sys-select" @change="setStepApproverType(step, 'user')">
                        <option :value="undefined">不指定人员</option>
                        <option v-for="u in options.users" :key="u.userId" :value="u.userId">{{ u.realName }}</option>
                      </select>
                    </td>
                    <td>
                      <select v-model.number="step.deptId" class="sys-select" @change="setStepApproverType(step, 'dept_manager')">
                        <option :value="undefined">按单据科室</option>
                        <option v-for="d in options.departments" :key="d.deptId" :value="d.deptId">{{ d.deptName }}</option>
                      </select>
                    </td>
                    <td><input v-model.number="step.minApprovals" min="1" type="number" class="sys-input is-tiny" /></td>
                    <td>
                      <select v-model.number="step.dataScope" class="sys-select">
                        <option v-for="scope in dataScopeOptions" :key="scope.value" :value="scope.value">{{ scope.label }}</option>
                      </select>
                    </td>
                    <td>
                      <span :class="['sys-badge', step.status === 1 ? 'is-active' : 'is-stopped']">
                        {{ step.status === 1 ? '启用' : '停用' }}
                      </span>
                    </td>
                    <td>
                      <button class="btn-text btn-text-primary" type="button" @click="step.status = step.status === 1 ? 0 : 1">
                        {{ step.status === 1 ? '停用' : '启用' }}
                      </button>
                      <button class="btn-text btn-text-danger" type="button" @click="removeStep(index)">删除</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

        </div>
        <div class="sys-dialog-footer">
          <button class="btn" type="button" :disabled="saving" @click="closeDialog">取消</button>
          <button class="btn btn-primary" type="button" :disabled="saving" @click="saveDialog">
            <Save :size="15" /> {{ saving ? '保存中...' : (dialogMode === 'edit' ? '保存修改' : '保存并新增') }}
          </button>
        </div>
      </div>
    </div>
    </Teleport>

  </div>
</template>

<style scoped>
/* =====================================================================
   系统管理 · 审批流设置
   ---------------------------------------------------------------------
   设计目标：
   1. 顶部 4 张主题色 KPI 卡。
   2. Tab：菜单分组（绿色 underline 激活态）。
   3. 工具栏：左侧搜索 + 状态过滤 + 右侧动作（重置 / 新增审批流）。
   4. 列表：行卡（菜单/节点 pill + 状态 badge + 步骤时间线）。
   5. 弹窗：左基本信息表单，右审批步骤表。
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

/* ---------- 顶部 4 项 KPI 摘要 ---------- */
.sys-kpi-card { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }

.sys-kpi-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 18px;
  border: 1px solid;
  border-radius: var(--spd-radius-md, 10px);
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.sys-kpi-item:hover { transform: translateY(-1px); box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08); }

.sys-kpi-item.is-blue   { background: linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%); border-color: #93c5fd; }
.sys-kpi-item.is-green  { background: linear-gradient(180deg, #d1fae5 0%, #a7f3d0 100%); border-color: #6ee7b7; }
.sys-kpi-item.is-orange { background: linear-gradient(180deg, #fef3c7 0%, #fde68a 100%); border-color: #fcd34d; }
.sys-kpi-item.is-purple { background: linear-gradient(180deg, #ede9fe 0%, #ddd6fe 100%); border-color: #c4b5fd; }

.sys-kpi-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 36px; height: 36px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.65);
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.sys-kpi-item.is-blue   .sys-kpi-icon { color: #1d4ed8; }
.sys-kpi-item.is-green  .sys-kpi-icon { color: #047857; }
.sys-kpi-item.is-orange .sys-kpi-icon { color: #b45309; }
.sys-kpi-item.is-purple .sys-kpi-icon { color: #6d28d9; }

.sys-kpi-item > div { display: grid; gap: 2px; min-width: 0; flex: 1; }
.sys-kpi-label { font-size: 12px; font-weight: 500; opacity: 0.85; }
.sys-kpi-value { font-size: 18px; font-weight: 700; line-height: 1.2; font-variant-numeric: tabular-nums; }
.sys-kpi-hint  { font-size: 11px; font-weight: 400; line-height: 1.3; opacity: 0.75; }

.sys-kpi-item.is-blue   .sys-kpi-label, .sys-kpi-item.is-blue   .sys-kpi-value, .sys-kpi-item.is-blue   .sys-kpi-hint { color: #1e3a8a; }
.sys-kpi-item.is-green  .sys-kpi-label, .sys-kpi-item.is-green  .sys-kpi-value, .sys-kpi-item.is-green  .sys-kpi-hint { color: #065f46; }
.sys-kpi-item.is-orange .sys-kpi-label, .sys-kpi-item.is-orange .sys-kpi-value, .sys-kpi-item.is-orange .sys-kpi-hint { color: #78350f; }
.sys-kpi-item.is-purple .sys-kpi-label, .sys-kpi-item.is-purple .sys-kpi-value, .sys-kpi-item.is-purple .sys-kpi-hint { color: #4c1d95; }

/* ---------- Tab ---------- */
.sys-tabs { display: flex; align-items: center; gap: 4px; overflow-x: auto; padding: 0 4px; }
.sys-tab {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px;
  border: none; border-bottom: 2px solid transparent; background: transparent;
  color: var(--spd-text-secondary);
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
  border-radius: 6px 6px 0 0; white-space: nowrap;
}
.sys-tab:hover { color: var(--spd-primary); background: #f0fdf9; }
.sys-tab.active { color: var(--spd-primary); border-bottom-color: var(--spd-primary); background: #f0fdf9; font-weight: 600; }
.sys-tab-count {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 20px; height: 20px;
  border-radius: 999px; padding: 0 6px;
  background: var(--spd-primary); color: #fff;
  font-size: 11px; font-weight: 700; line-height: 1;
}

/* ---------- 工具栏 ---------- */
.sys-toolbar-panel {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px;
  background: #fff; border: 1px solid #e2e8f0; border-radius: var(--spd-radius-md, 10px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  flex-wrap: wrap;
}

.sys-toolbar-search {
  display: flex; align-items: center;
  border: 1px solid #cbd5e1; border-radius: 7px; background: #fff;
  overflow: hidden;
  flex: 0 0 360px; min-width: 0;
}

.sys-toolbar-search-icon {
  display: flex; align-items: center; justify-content: center;
  width: 34px; height: 34px; color: #64748b;
  background: #f8fafc; border-right: 1px solid #e2e8f0; flex-shrink: 0;
}

.sys-toolbar-search input {
  flex: 1; min-width: 0; height: 34px;
  border: none; background: transparent; color: #102033;
  padding: 0 10px; font-size: 13px; font-weight: 500;
}
.sys-toolbar-search input::placeholder { color: #94a3b8; }
.sys-toolbar-search input:focus { outline: none; }

.sys-toolbar-status {
  border: none; border-left: 1px solid #e2e8f0;
  background: #f8fafc; color: #102033;
  height: 34px; padding: 0 8px; font-size: 13px; font-weight: 500;
  flex-shrink: 0;
  cursor: pointer;
}

.sys-toolbar-actions { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.sys-toolbar-sep { width: 1px; height: 22px; background: #e2e8f0; margin: 0 4px; }

/* ---------- 按钮 ---------- */
.btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 4px;
  min-height: 32px; height: 32px; padding: 0 14px;
  border: 1px solid #cbd5e1; border-radius: 6px; background: #fff; color: #334155;
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}
.btn:hover:not(:disabled) { background: #f8fafc; border-color: #94a3b8; color: #1e293b; }
.btn:disabled { opacity: 0.55; cursor: not-allowed; }
.btn-primary {
  background: linear-gradient(180deg, #10b981 0%, #059669 100%);
  border-color: #047857; color: #fff;
}
.btn-primary:hover:not(:disabled) { background: linear-gradient(180deg, #059669 0%, #047857 100%); border-color: #065f46; }

.btn-text {
  display: inline-flex; align-items: center; gap: 3px;
  border: none; background: transparent; color: #10b981;
  font-size: 12px; font-weight: 500; cursor: pointer;
  padding: 2px 6px; border-radius: 4px; transition: background 0.15s;
}
.btn-text:hover { background: #d1fae5; }
.btn-text-primary { color: #10b981; }
.btn-text-danger { color: #b91c1c; }
.btn-text-danger:hover { background: #fee2e2; color: #991b1b; }

.btn-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; border: none; border-radius: 6px;
  background: transparent; color: #64748b; cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.btn-icon:hover { background: #f1f5f9; color: #334155; }

/* ---------- 消息 / 错误 ---------- */
.sys-msg, .sys-error {
  display: flex; align-items: center; padding: 8px 14px;
  border-radius: 8px; font-size: 13px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}
.sys-msg   { background: #f0fdf9; border: 1px solid #6ee7b7; color: #065f46; }
.sys-error { background: #fee2e2; border: 1px solid #fca5a5; color: #991b1b; }

/* ---------- 加载 / 空状态 ---------- */
.sys-loading-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 10px; padding: 48px;
  background: #fff; border: 1px solid #e2e8f0; border-radius: var(--spd-radius-md, 10px);
  color: #64748b;
}
.sys-loading-icon { display: inline-flex; color: #10b981; animation: spin 1.2s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.sys-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 12px; padding: 56px 16px;
  background: #fff; border: 1px dashed #cbd5e1; border-radius: var(--spd-radius-md, 10px);
  color: #64748b;
}
.sys-empty-icon { color: #cbd5e1; }
.sys-empty p { margin: 0; font-size: 13px; }

/* ---------- 列表行卡 ---------- */
.sys-flow-list { display: flex; flex-direction: column; gap: 10px; }

.sys-flow-card {
  background: #fff; border: 1px solid #e2e8f0; border-radius: var(--spd-radius-md, 10px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  padding: 14px 16px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.sys-flow-card:hover { border-color: #6ee7b7; box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06); }
.sys-flow-card.is-stopped { background: #f8fafc; opacity: 0.78; }

.sys-flow-card-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; flex-wrap: wrap;
}

.sys-flow-card-title {
  display: flex; align-items: center; gap: 6px;
  flex-wrap: wrap; min-width: 0;
}

.sys-flow-card-arrow { color: #94a3b8; display: inline-flex; }

.sys-flow-card-actions { display: flex; align-items: center; gap: 4px; flex-shrink: 0; }

.sys-flow-card-meta {
  display: flex; align-items: center; gap: 14px;
  flex-wrap: wrap;
  margin: 8px 0 10px;
  font-size: 12px; color: #64748b;
}
.sys-flow-card-meta > span { display: inline-flex; align-items: center; gap: 4px; }
.sys-flow-card-meta-icon { display: inline-flex; color: #94a3b8; }

/* 步骤时间线 */
.sys-flow-steps {
  list-style: none; padding: 0; margin: 0;
  display: flex; flex-direction: column; gap: 4px;
  border-left: 2px solid #d1fae5;
  padding-left: 12px;
}

.sys-flow-step {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px;
  background: #fafdfb; border: 1px solid #e2e8f0; border-radius: 6px;
  font-size: 12.5px; color: #334155;
  flex-wrap: wrap;
  position: relative;
}
.sys-flow-step::before {
  content: '';
  position: absolute; left: -16px; top: 50%;
  transform: translateY(-50%);
  width: 8px; height: 8px; border-radius: 50%;
  background: #10b981; border: 2px solid #fff; box-shadow: 0 0 0 1px #d1fae5;
}

.sys-flow-step-num {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; border-radius: 999px;
  background: linear-gradient(180deg, #10b981 0%, #059669 100%);
  color: #fff; font-size: 11px; font-weight: 700; flex-shrink: 0;
}

.sys-flow-step-name { font-weight: 600; color: #102033; }
.sys-flow-step-approver { color: #475569; }
.sys-flow-step-stat { color: #94a3b8; margin-left: auto; font-size: 12px; }

/* ---------- 通用 tag / badge ---------- */
.sys-tag {
  display: inline-flex; align-items: center;
  border: 1px solid; border-radius: 6px; padding: 1px 8px;
  font-size: 12px; font-weight: 600; white-space: nowrap;
}
.sys-tag.is-menu  { color: #1d4ed8; background: #dbeafe; border-color: #93c5fd; }
.sys-tag.is-node  { color: #6d28d9; background: #ede9fe; border-color: #c4b5fd; }
.sys-tag.is-scope { color: #b45309; background: #fef3c7; border-color: #fcd34d; }

.sys-badge {
  display: inline-flex; align-items: center;
  border-radius: 999px; padding: 2px 10px;
  font-size: 11.5px; font-weight: 600;
  border: 1px solid;
}
.sys-badge.is-active  { color: #065f46; background: #d1fae5; border-color: #6ee7b7; }
.sys-badge.is-stopped { color: #991b1b; background: #fee2e2; border-color: #fca5a5; }

/* ---------- 弹窗 ---------- */
.sys-dialog-mask {
  position: fixed; inset: 0; z-index: 1000;
  display: flex; align-items: center; justify-content: center;
  background: rgba(15, 23, 42, 0.5); backdrop-filter: blur(2px);
}

.sys-dialog {
  background: #fff; border-radius: var(--spd-radius-lg, 14px);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.2);
  width: 100%; max-width: 720px; max-height: 90vh;
  display: flex; flex-direction: column; overflow: hidden;
  animation: dialog-in 0.2s ease;
}
.sys-dialog.sys-dialog-xl { max-width: 960px; }
@keyframes dialog-in { from { opacity: 0; transform: scale(0.95) translateY(-8px); } to { opacity: 1; transform: scale(1) translateY(0); } }

.sys-dialog-header {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 16px 20px; border-bottom: 1px solid #e2e8f0;
}
.sys-dialog-header h3 { margin: 0; font-size: 15px; font-weight: 700; color: var(--spd-text-heading); }
.sys-dialog-header p  { margin: 3px 0 0; font-size: 12px; color: var(--spd-text-tertiary); }

.sys-dialog-body {
  padding: 16px 20px;
  overflow-y: auto; flex: 1; min-height: 0;
  display: flex; flex-direction: column; gap: 16px;
}

.sys-dialog-section {
  border: 1px solid #e2e8f0; border-radius: 10px;
  background: #fafdfb;
  overflow: hidden;
}

.sys-dialog-section-title {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}

.sys-dialog-section-title h4 {
  margin: 0; font-size: 13px; font-weight: 700; color: var(--spd-text-heading);
}

.sys-dialog-section-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 6px;
}
.sys-dialog-section-icon.is-green { background: #d1fae5; color: #047857; }
.sys-dialog-section-icon.is-blue  { background: #dbeafe; color: #1d4ed8; }

.sys-dialog-section-meta {
  margin-left: auto;
  font-size: 12px; color: #64748b;
}
.sys-dialog-section-meta strong { color: #047857; font-weight: 700; }

.sys-dialog-section .sys-form-grid { padding: 14px; }

.sys-form-grid {
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 14px;
}
.sys-form-grid label { display: grid; gap: 5px; font-size: 12px; font-weight: 700; color: #4b5b66; }
.sys-form-grid label.sys-form-wide { grid-column: 1 / -1; }

.sys-input, .sys-select {
  width: 100%; min-height: 32px; height: 32px;
  border: 1px solid #cbd5e1; border-radius: 6px;
  background: #fff; color: #102033;
  padding: 0 10px; font-size: 13px; font-weight: 500;
  transition: border-color 0.15s;
}
.sys-input:focus, .sys-select:focus { outline: none; border-color: #94a3b8; }
.sys-input:read-only, .sys-select:disabled { background: #f1f5f9; color: #94a3b8; cursor: not-allowed; }
.sys-input.is-tiny { min-width: 60px; }

.sys-dialog-footer {
  display: flex; align-items: center; justify-content: flex-end; gap: 8px;
  padding: 14px 20px; border-top: 1px solid #e2e8f0; background: #fafdfb;
}

/* 弹窗内表格（紧凑） */
.sys-table.is-compact th, .sys-table.is-compact td {
  padding: 6px 8px; font-size: 12.5px;
}

.sys-table-scroll { overflow-x: auto; }

.sys-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.sys-table thead tr {
  background: linear-gradient(180deg, #ecfdf5 0%, #d1fae5 100%);
  border-bottom: 1px solid #6ee7b7;
}
.sys-table th {
  color: #065f46; font-weight: 700; font-size: 12px;
  text-align: left; padding: 10px 12px; white-space: nowrap; letter-spacing: 0.3px;
}
.sys-table td {
  padding: 8px 12px; border-bottom: 1px solid #f1f5f9;
  color: #334155; vertical-align: middle;
}
.sys-table tbody tr:hover td { background: #f8fafc; }
.sys-table tbody tr:last-child td { border-bottom: none; }

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .sys-kpi-card { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .sys-form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .sys-toolbar-search { flex: 1; min-width: 0; }
}
@media (max-width: 800px) {
  .sys-flow-card-head { flex-direction: column; align-items: flex-start; }
  .sys-flow-step-stat { margin-left: 0; }
}
@media (max-width: 640px) {
  .sys-kpi-card { grid-template-columns: 1fr; }
  .sys-form-grid { grid-template-columns: 1fr; }
  .sys-page { padding: 12px; }
}
</style>
