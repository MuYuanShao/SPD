<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ClipboardCheck,
  Download,
  GitBranch,
  Plus,
  RefreshCw,
  Save,
  Search,
  Settings2,
  Trash2,
  Upload
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

const scopeOptions = [
  { value: 'global', label: '全局' },
  { value: 'department', label: '科室' },
  { value: 'warehouse', label: '库房' },
  { value: 'role', label: '角色' }
]

const dataScopeOptions = [
  { value: 1, label: '全部数据' },
  { value: 2, label: '本部门数据' },
  { value: 3, label: '本部门及下级' },
  { value: 4, label: '仅本人数据' }
]

const approverTypes = [
  { value: 'role', label: '按角色' },
  { value: 'user', label: '指定用户' },
  { value: 'dept_manager', label: '部门负责人' }
]

const featureNameMap: Record<string, string> = {
  'pending-product-catalog': '待审批目录',
  'purchase-management': '采购管理',
  'receiving-acceptance': '收货验收',
  'department-requisition': '科室申领',
  'stocktaking-management': '盘点管理',
  'batch-price-adjustment': '价格调整',
  'settlement-reconciliation': '结算对账'
}

const nodeNameMap: Record<string, string> = {
  'initial-review': '目录初审',
  'final-review': '目录终审',
  'demand-review': '采购需求审核',
  'plan-approval': '采购计划审批',
  'order-approval': '采购订单审批',
  'receiving-approval': '收货验收审批',
  'requisition-approval': '申领审批',
  'stocktaking-approval': '盘点审批',
  'price-adjustment-approval': '调价审批',
  'settlement-confirm': '结算确认'
}

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
const editingFlowId = ref<number | null>(null)
const editMode = ref(false)
const activeFeatureCode = ref('')
const keyword = ref('')
const status = ref('')

const form = reactive<ApprovalFlowPayload>({
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

const featureGroups = computed(() => {
  const map = new Map<string, { featureCode: string; featureName: string; count: number }>()
  for (const node of options.value.nodes) {
    if (!map.has(node.featureCode)) {
      map.set(node.featureCode, { featureCode: node.featureCode, featureName: featureLabel(node), count: 0 })
    }
  }
  for (const row of rows.value) {
    const item = map.get(row.featureCode)
    if (item) item.count += 1
  }
  return Array.from(map.values())
})

const filteredNodes = computed(() =>
  activeFeatureCode.value
    ? options.value.nodes.filter((node) => node.featureCode === activeFeatureCode.value)
    : options.value.nodes
)

const menuOptions = computed(() => {
  const map = new Map<string, ApprovalFlowNodeOption>()
  for (const node of filteredNodes.value) {
    if (!map.has(node.featureCode)) {
      map.set(node.featureCode, node)
    }
  }
  return Array.from(map.values())
})

const activeFeatureName = computed(() => {
  if (!activeFeatureCode.value) return '全部审批流'
  return featureGroups.value.find((feature) => feature.featureCode === activeFeatureCode.value)?.featureName ?? featureNameMap[activeFeatureCode.value] ?? '审批流'
})

const currentSteps = computed(() => form.steps.length ? form.steps : [emptyStep(1)])

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
    ensureActiveForm()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '审批流配置加载失败'
  } finally {
    loading.value = false
  }
}

function ensureActiveForm() {
  if (editingFlowId.value && rows.value.some((row) => row.flowId === editingFlowId.value)) {
    return
  }
  const firstRow = rows.value[0]
  if (firstRow) {
    openEdit(firstRow, false)
    return
  }
  resetForm(options.value.nodes[0])
}

function selectFeature(featureCode: string) {
  activeFeatureCode.value = featureCode
  editingFlowId.value = null
  loadAll()
}

function resetQuery() {
  keyword.value = ''
  status.value = ''
  loadAll()
}

function resetForm(node?: ApprovalFlowNodeOption) {
  const targetNode = node ?? filteredNodes.value[0] ?? options.value.nodes[0]
  editingFlowId.value = null
  editMode.value = true
  Object.assign(form, {
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

function openCreate(node?: ApprovalFlowNodeOption) {
  message.value = ''
  error.value = ''
  const existing = node
    ? rows.value.find((row) => row.featureCode === node.featureCode && row.nodeCode === node.nodeCode)
    : undefined
  if (existing) {
    openEdit(existing)
    return
  }
  resetForm(node)
}

function openEdit(row: ApprovalFlowRow, notify = true) {
  editingFlowId.value = row.flowId
  editMode.value = false
  Object.assign(form, {
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
    steps: row.steps.length ? row.steps.map((step) => copyStep(step, nodeLabel(row))) : [emptyStep(1)]
  })
  if (notify) {
    message.value = `${menuLabel(row)} 已载入配置`
  }
}

function onNodeChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  const existing = rows.value.find((row) => row.featureCode === value)
  if (existing) {
    openEdit(existing)
    return
  }
  const node = options.value.nodes.find((item) => item.featureCode === value)
  if (node) {
    openCreate(node)
  }
}

function copyStep(step: ApprovalFlowStep, fallbackName?: string): ApprovalFlowStep {
  return {
    stepId: step.stepId,
    stepOrder: step.stepOrder,
    stepName: cleanText(step.stepName, fallbackName ?? `第 ${step.stepOrder} 级审批`),
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

function addStep() {
  if (!editMode.value) return
  const nextOrder = Math.max(0, ...form.steps.map((step) => Number(step.stepOrder) || 0)) + 1
  form.steps.push(emptyStep(nextOrder))
}

function removeStep(index: number) {
  if (!editMode.value) return
  if (form.steps.length === 1) {
    message.value = '审批流至少需要一个步骤'
    return
  }
  form.steps.splice(index, 1)
}

function normalizeStepOrders() {
  form.steps.forEach((step, index) => {
    step.stepOrder = Number(step.stepOrder) || index + 1
  })
}

async function saveFlow() {
  if (!editMode.value) {
    message.value = '当前配置已锁定，点击“审批流配置”后再修改'
    return
  }
  message.value = ''
  error.value = ''
  normalizeStepOrders()
  const validationMessage = validateSteps()
  if (validationMessage) {
    error.value = validationMessage
    return
  }
  const orderedSteps = [...form.steps]
    .map((step) => copyStep(step))
    .sort((left, right) => Number(left.stepOrder) - Number(right.stepOrder))
  const payload: ApprovalFlowPayload = {
    featureCode: form.featureCode,
    featureName: form.featureName,
    nodeCode: form.nodeCode,
    nodeName: form.nodeName,
    scopeType: form.scopeType,
    scopeId: form.scopeId || 'default',
    dataScope: Number(form.dataScope),
    status: Number(form.status),
    remark: form.remark,
    deptId: form.deptId,
    steps: orderedSteps
  }
  try {
    saving.value = true
    const existingFlow = editingFlowId.value ? undefined : findExistingFlow(payload)
    const targetFlowId = editingFlowId.value ?? existingFlow?.flowId
    if (targetFlowId) {
      await updateApprovalFlow(targetFlowId, payload)
      message.value = '审批流配置已保存成功，当前页面已锁定。点击“审批流配置”可继续修改。'
    } else {
      const result = await createApprovalFlow(payload)
      editingFlowId.value = result.flowId
      message.value = '审批流配置已新增成功，当前页面已锁定。点击“审批流配置”可继续修改。'
    }
    await loadAll()
    editMode.value = false
  } catch (err) {
    error.value = err instanceof Error ? err.message : '审批流保存失败'
  } finally {
    saving.value = false
  }
}

function enterEditMode() {
  error.value = ''
  message.value = '已进入编辑模式，可修改后保存配置'
  editMode.value = true
}

function validateSteps() {
  const orders = new Set<number>()
  for (const step of form.steps) {
    const order = Number(step.stepOrder)
    if (!Number.isInteger(order) || order < 1) {
      return '审批顺序必须填写大于 0 的整数'
    }
    if (orders.has(order)) {
      return '审批顺序不能重复，请调整后再保存'
    }
    orders.add(order)
    if (!step.stepName?.trim()) {
      return '审批步骤名称不能为空'
    }
    if (step.approverType === 'role' && !step.roleId) {
      return '按角色审批时必须选择角色'
    }
    if (step.approverType === 'user' && !step.userId) {
      return '指定人员审批时必须选择人员'
    }
  }
  return ''
}

function findExistingFlow(payload: ApprovalFlowPayload) {
  return rows.value.find((row) =>
    row.featureCode === payload.featureCode &&
    row.nodeCode === payload.nodeCode &&
    row.scopeType === payload.scopeType &&
    row.scopeId === payload.scopeId
  )
}

function setStatus(value: number) {
  form.status = value
}

function showPlaceholderAction(label: string) {
  message.value = `${label}功能待接入文件处理接口`
}

function dataScopeName(value?: number) {
  return dataScopeOptions.find((item) => item.value === value)?.label ?? '全部数据'
}

function approverName(step: ApprovalFlowStep) {
  if (step.approverType === 'role') return roleName(step.roleId)
  if (step.approverType === 'user') return userName(step.userId)
  return deptName(step.deptId) ?? '单据科室负责人'
}

function roleName(roleId?: number) {
  return options.value.roles.find((role) => role.roleId === roleId)?.roleName ?? '未指定角色'
}

function userName(userId?: number) {
  return options.value.users.find((user) => user.userId === userId)?.realName ?? '未指定用户'
}

function deptName(deptId?: number) {
  return options.value.departments.find((dept) => dept.deptId === deptId)?.deptName
}

function setStepApproverType(step: ApprovalFlowStep, type: string) {
  step.approverType = type
}

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

function menuLabel(item: { featureCode: string; featureName?: string }) {
  return featureLabel(item)
}

onMounted(loadAll)
</script>

<template>
  <section class="approval-config-page">
    <header class="approval-config-toolbar">
      <div class="approval-config-actions">
        <button type="button" class="btn btn-primary" @click="openCreate()">
          <Plus :size="17" />
          新增审批流
        </button>
        <button type="button" class="btn" @click="showPlaceholderAction('批量导入')">
          <Upload :size="17" />
          批量导入
        </button>
        <button type="button" class="btn" @click="showPlaceholderAction('导出 Excel')">
          <Download :size="17" />
          导出 Excel
        </button>
        <button type="button" class="btn btn-outline-primary" @click="enterEditMode">
          <Settings2 :size="17" />
          审批流配置
        </button>
      </div>
      <div class="approval-config-count">共 <strong>{{ rows.length }}</strong> 条配置</div>
    </header>

    <p v-if="message" class="inline-message">{{ message }}</p>
    <p v-if="error" class="inline-message danger">{{ error }}</p>

    <section class="approval-config-shell">
      <div class="approval-config-head">
        <div class="approval-config-title">
          <Settings2 :size="22" />
          <h2>{{ featureLabel(form) }}审批流程配置</h2>
        </div>
        <div class="approval-config-head-actions">
          <button type="button" class="btn" :disabled="!editMode" @click="addStep">
            <Plus :size="17" />
            新增审批步骤
          </button>
          <button type="button" class="btn btn-primary" :disabled="saving || !editMode" @click="saveFlow">
            <Save :size="17" />
            保存配置
          </button>
        </div>
      </div>

      <form class="approval-config-filter" @submit.prevent="loadAll">
        <label>
          <span>菜单界面</span>
          <select :value="form.featureCode" :disabled="!editMode" @change="onNodeChange">
            <option
              v-for="node in menuOptions"
              :key="node.featureCode"
              :value="node.featureCode"
            >
              {{ menuLabel(node) }}
            </option>
          </select>
        </label>
        <label>
          <span>适用范围</span>
          <select v-model="form.scopeType" :disabled="!editMode">
            <option v-for="scope in scopeOptions" :key="scope.value" :value="scope.value">{{ scope.label }}</option>
          </select>
        </label>
        <label>
          <span>适用对象</span>
          <input v-model.trim="form.scopeId" :readonly="!editMode" placeholder="default" />
        </label>
        <label>
          <span>流程数据隔离</span>
          <select v-model.number="form.dataScope" :disabled="!editMode">
            <option v-for="scope in dataScopeOptions" :key="scope.value" :value="scope.value">{{ scope.label }}</option>
          </select>
        </label>
        <label>
          <span>查询关键字</span>
          <input v-model.trim="keyword" placeholder="功能、节点、备注" />
        </label>
        <label>
          <span>筛选状态</span>
          <select v-model="status">
            <option value="">全部</option>
            <option value="1">启用</option>
            <option value="0">停用</option>
          </select>
        </label>
        <div class="approval-filter-actions">
          <button type="submit" class="btn btn-primary">
            <Search :size="17" />
            查询
          </button>
          <button type="button" class="btn" @click="resetQuery">重置</button>
          <button type="button" class="btn" @click="loadAll">
            <RefreshCw :size="17" />
            刷新
          </button>
        </div>
      </form>

      <div class="approval-feature-strip">
        <button type="button" :class="{ active: activeFeatureCode === '' }" @click="selectFeature('')">
          <GitBranch :size="16" />
          全部菜单
        </button>
        <button
          v-for="feature in featureGroups"
          :key="feature.featureCode"
          type="button"
          :class="{ active: activeFeatureCode === feature.featureCode }"
          @click="selectFeature(feature.featureCode)"
        >
          <ClipboardCheck :size="16" />
          {{ featureLabel(feature) }}
          <strong>{{ feature.count }}</strong>
        </button>
      </div>

      <div class="approval-config-tip">
        <span>提示</span>
        当前审批流绑定菜单界面 <strong>{{ menuLabel(form) }}</strong>，下方可维护审批顺序、角色参与、人员参与、部门参与和数据隔离，保存后立即生效。
      </div>

      <div class="approval-timeline-panel">
        <div class="approval-timeline">
          <article v-for="step in currentSteps" :key="step.stepOrder" class="approval-timeline-node">
            <div class="approval-timeline-index">{{ step.stepOrder }}</div>
            <strong>{{ cleanText(step.stepName, nodeLabel(form)) }}</strong>
            <span>{{ approverName(step) }}</span>
          </article>
        </div>
      </div>

      <div class="approval-config-table-wrap">
        <table class="approval-config-table">
          <thead>
            <tr>
              <th>审批顺序</th>
              <th>审批步骤名称</th>
              <th>角色参与</th>
              <th>人员参与</th>
              <th>部门参与</th>
              <th>最少通过</th>
              <th>数据隔离</th>
              <th>启用</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(step, index) in form.steps" :key="index">
              <td class="approval-order-cell"><input v-model.number="step.stepOrder" :readonly="!editMode" min="1" type="number" /></td>
              <td><input v-model.trim="step.stepName" :readonly="!editMode" required /></td>
              <td>
                <select v-model.number="step.roleId" :disabled="!editMode" @change="setStepApproverType(step, 'role')">
                  <option :value="undefined">不指定角色</option>
                  <option v-for="role in options.roles" :key="role.roleId" :value="role.roleId">{{ role.roleName }}</option>
                </select>
              </td>
              <td>
                <select v-model.number="step.userId" :disabled="!editMode" @change="setStepApproverType(step, 'user')">
                  <option :value="undefined">不指定人员</option>
                  <option v-for="user in options.users" :key="user.userId" :value="user.userId">{{ user.realName }}</option>
                </select>
              </td>
              <td>
                <select v-model.number="step.deptId" :disabled="!editMode" @change="setStepApproverType(step, 'dept_manager')">
                  <option :value="undefined">按单据科室</option>
                  <option v-for="dept in options.departments" :key="dept.deptId" :value="dept.deptId">{{ dept.deptName }}</option>
                </select>
              </td>
              <td class="approval-min-cell"><input v-model.number="step.minApprovals" :readonly="!editMode" min="1" type="number" /></td>
              <td>
                <select v-model.number="step.dataScope" :disabled="!editMode">
                  <option v-for="scope in dataScopeOptions" :key="scope.value" :value="scope.value">{{ scope.label }}</option>
                </select>
              </td>
              <td>
                <button
                  type="button"
                  :class="['approval-enable-pill', step.status === 1 ? 'enabled' : 'disabled']"
                  :disabled="!editMode"
                  @click="step.status = step.status === 1 ? 0 : 1"
                >
                  {{ step.status === 1 ? '启用' : '停用' }}
                </button>
              </td>
              <td>
                <button type="button" class="btn-text danger" :disabled="!editMode" @click="removeStep(index)">删除</button>
              </td>
            </tr>
            <tr v-if="loading">
              <td colspan="9" class="approval-empty">正在加载审批流配置...</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="approval-existing-list">
        <button
          v-for="row in rows"
          :key="row.flowId"
          type="button"
          :class="{ active: editingFlowId === row.flowId }"
          @click="openEdit(row)"
        >
          <span>{{ menuLabel(row) }}</span>
          <small>{{ dataScopeName(row.dataScope) }} · {{ row.status === 1 ? '启用' : '停用' }}</small>
        </button>
      </div>
    </section>
  </section>
</template>
