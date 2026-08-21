<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, ArrowRight, CheckCircle2, ChevronDown, ClipboardCheck, Clock3, Download, FileDown, FileUp, Plus, Search, Send, UserRound, X, XCircle } from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import { useApprovalTableScroll } from '../../composables/useApprovalTableScroll'
import { usePendingProductBatchApproval } from '../../composables/usePendingProductBatchApproval'
import { usePendingProductCreateForm } from '../../composables/usePendingProductCreateForm'
import { usePendingProductImportExport } from '../../composables/usePendingProductImportExport'
import {
  approvalFieldChips,
  approvalFieldColumnIndex,
  approvalFieldGroups,
  approvalFieldGroupTargets,
  approvalFieldToGroup,
  approvalRiskLabels,
  approvalRiskTone,
  approvalTypeClass,
  approvalWideField
} from '../../composables/usePendingProductCatalogDisplay'
import { usePendingProductCatalogNavigation } from '../../composables/usePendingProductCatalogNavigation'
import { usePendingProductCatalogPagination } from '../../composables/usePendingProductCatalogPagination'
import {
  fetchPendingProductApplications,
  fetchPendingProductPartnerOptions,
  batchApprovePendingProductApplications,
  type PendingProductApplicationRow,
  type PendingProductTypeCount
} from '../../api/pendingProductApplications'
import type { PartnerOption } from '../../api/masterData'

const route = useRoute()
const router = useRouter()
const { selectType, selectScope, selectMineStatus } = usePendingProductCatalogNavigation({ route, router })

const approvalTypes = [
  {
    key: 'new',
    label: '新品准入',

    description: '审核新商品首次进入医院目录的准入资料、价格、资质与仓储属性。',
    columns: ['商品基础信息', '供应商资质', '注册证有效期', '采购价', '是否定数管理']
  },
  {
    key: 'change',
    label: '信息变更',

    description: '审核已入库商品的名称、规格、单位、厂家、供应商等字段变更。',
    columns: ['变更字段', '变更前', '变更后', '变更原因', '影响范围']
  },
  {
    key: 'qualification',
    label: '资质更新',

    description: '审核注册证、生产许可证、经营许可证、授权文件等资质材料更新。',
    columns: ['资质类型', '原有效期', '新有效期', '附件数量', '到期风险']
  }
]

const activeTypeKey = computed(() => {
  const value = typeof route.query.type === 'string' ? route.query.type : 'new'
  return approvalTypes.some((item) => item.key === value) ? value : 'new'
})
const activeScope = computed(() => {
  const value = typeof route.query.scope === 'string' ? route.query.scope : 'todo'
  return value === 'mine' || value === 'handled' ? value : 'todo'
})
const mineStatusOptions = [
  {
    key: 'pending',
    label: '待审批目录',
    description: '查看当前用户发起且仍在初审或复审中的审批单据。'
  },
  {
    key: 'returned',
    label: '退回修改目录',
    description: '查看被退回修改、需要补充资料后重新提交的审批单据。'
  },
  {
    key: 'approved',
    label: '审批通过目录',
    description: '查看当前用户发起且已经审批通过的单据。'
  }
]
const activeMineStatus = computed(() => {
  const value = typeof route.query.mineStatus === 'string' ? route.query.mineStatus : 'pending'
  return mineStatusOptions.some((item) => item.key === value) ? value : 'pending'
})
const activeMineOption = computed(
  () => mineStatusOptions.find((item) => item.key === activeMineStatus.value) ?? mineStatusOptions[0]
)

const activeType = computed(() => approvalTypes.find((item) => item.key === activeTypeKey.value) ?? approvalTypes[0])
const isReturnedMineDirectory = computed(() => activeScope.value === 'mine' && activeMineStatus.value === 'returned')

const rows = ref<PendingProductApplicationRow[]>([])
const typeCounts = ref<PendingProductTypeCount[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const message = ref('')
const showCreateModal = ref(false)
const totalItems = ref(0)
const selectedNos = ref<string[]>([])
const {
  createForm,
  createAttachments,
  attachmentInput,
  resetCreateForm,
  handleCreateAttachments,
  removeCreateAttachment,
  submitCreateForm
} = usePendingProductCreateForm({
  message,
  showCreateModal,
  reload: loadApplications
})

const duplicateAlertVisible = ref(false)
const manufacturerOptions = ref<PartnerOption[]>([])
const supplierOptions = ref<PartnerOption[]>([])

function withRetainedOption(list: PartnerOption[], current: string | undefined) {
  if (!current || list.some((item) => item.name === current)) {
    return list
  }
  return [...list, { code: '-', name: current, status: 0 }]
}

/**
 * 选择厂家时自动回填该厂家的生产许可证号。
 */
function handleManufacturerChange() {
  const name = createForm.manufacturerName ?? ''
  const option = manufacturerOptions.value.find((item) => item.name === name)
  if (option) {
    createForm.productionLicenseNo = option.licenseNo ?? ''
  }
}

/**
 * 选择供应商时自动回填该供应商的经营许可证号。
 */
function handleSupplierChange() {
  const name = createForm.supplierName ?? ''
  const option = supplierOptions.value.find((item) => item.name === name)
  if (option) {
    createForm.businessLicenseNo = option.businessLicenseNo ?? ''
  }
}

async function loadPartnerOptions() {
  try {
    const options = await fetchPendingProductPartnerOptions()
    manufacturerOptions.value = options.manufacturers
    supplierOptions.value = options.suppliers
  } catch (err) {
    console.error('厂家与供应商选项加载失败', err)
    manufacturerOptions.value = []
    supplierOptions.value = []
  }
}

async function openCreateModal() {
  message.value = ''
  resetCreateForm()
  showCreateModal.value = true
  await loadPartnerOptions()
}

async function handleCreateSubmit() {
  message.value = ''
  try {
    await submitCreateForm()
  } catch (err) {
    const text = err instanceof Error ? err.message : ''
    if (text.includes('商品目录已存在')) {
      duplicateAlertVisible.value = true
    } else {
      message.value = text || '提交失败，请稍后重试'
    }
  }
}

const {
  importInput,
  downloadTemplate,
  handleImportFile,
  exportRows
} = usePendingProductImportExport({
  activeTypeKey,
  activeScope,
  keyword,
  message,
  error,
  reload: loadApplications
})

const typeCountMap = computed(() => new Map(typeCounts.value.map((item) => [item.key, item.count])))

const taskGroups = computed(() => [
  {
    label: '待我审批',
    count: approvalTypes.reduce((total, item) => total + (typeCountMap.value.get(item.key) ?? 0), 0),
    icon: Clock3,
    tone: 'orange',
    children: approvalTypes.map((item) => ({
      ...item,
      count: typeCountMap.value.get(item.key) ?? 0,
      mineStatus: ''
    }))
  },
  {
    label: '我发起的',
    count:
      (typeCountMap.value.get('mine-pending') ?? 0) +
      (typeCountMap.value.get('mine-returned') ?? 0) +
      (typeCountMap.value.get('mine-approved') ?? 0),
    icon: UserRound,
    tone: 'blue',
    scope: 'mine',
    children: mineStatusOptions.map((item) => ({
      ...item,
      count: typeCountMap.value.get(`mine-${item.key}`) ?? 0,
      mineStatus: item.key
    }))
  },
  {
    label: '已处理/抄送',
    count: typeCountMap.value.get('handled') ?? 0,
    icon: CheckCircle2,
    tone: 'green',
    scope: 'handled'
  }
])

const allVisibleSelected = computed(() => {
  return rows.value.length > 0 && rows.value.every((row) => selectedNos.value.includes(row.no))
})

const approvalMetricCards = computed(() => {
  const totalTodo = approvalTypes.reduce((total, item) => total + (typeCountMap.value.get(item.key) ?? 0), 0)
  return [
    { label: '待我审批', value: totalTodo, hint: '按风险与时效排序' },
    { label: '新品准入', value: typeCountMap.value.get('new') ?? 0, hint: '重点核验准入资料' },
    { label: '信息变更', value: typeCountMap.value.get('change') ?? 0, hint: '关注价格与供应商' },
    { label: '已展示字段', value: 18, hint: '可按分组快速定位' },
    { label: '风险提醒', value: rows.value.filter((row) => row.warning).length, hint: '含临期 / 冷链 / 高值', tone: 'alert' }
  ]
})

const {
  approvalTableShellRef,
  approvalScrollTrackRef,
  approvalScrollDragging,
  approvalScrollState,
  activeApprovalField,
  activeApprovalGroup,
  approvalScrollThumbStyle,
  scrollApprovalTableToField,
  scrollApprovalToGroup,
  updateApprovalScrollState,
  jumpApprovalTableScroll,
  startApprovalScrollDrag,
  moveApprovalScrollDrag,
  endApprovalScrollDrag
} = useApprovalTableScroll({
  fieldColumnIndex: approvalFieldColumnIndex,
  fieldGroupTargets: approvalFieldGroupTargets,
  fieldToGroup: approvalFieldToGroup
})

const { currentPage, pageSize, totalPages, visiblePages, changePage, changePageSize, resetPage } =
  usePendingProductCatalogPagination({
    totalItems,
    reload: loadApplications
  })

function toggleAllRows() {
  selectedNos.value = allVisibleSelected.value ? [] : rows.value.map((row) => row.no)
}

const {
  batchApproveLoading,
  showBatchApproveModal,
  batchApproveAction,
  batchApproveOpinion,
  openBatchApprove,
  confirmBatchApprove
} = usePendingProductBatchApproval({
  selectedNos,
  error,
  message,
  batchApprove: batchApprovePendingProductApplications,
  reload: loadApplications
})

async function loadApplications() {
  loading.value = true
  error.value = ''

  try {
    const data = await fetchPendingProductApplications(
      activeTypeKey.value,
      activeScope.value,
      keyword.value,
      activeMineStatus.value,
      currentPage.value,
      pageSize.value
    )
    rows.value = data.rows
    typeCounts.value = data.typeCounts
    totalItems.value = data.total
    selectedNos.value = []
  } catch (err) {
    rows.value = []
    typeCounts.value = []
    totalItems.value = 0
    error.value = err instanceof Error ? err.message : '待审批目录加载失败'
  } finally {
    loading.value = false
  }
}

function rowPrimaryActionLabel() {
  if (isReturnedMineDirectory.value) return '修改'
  if (activeScope.value === 'handled') return '查看'
  return '审批'
}

function rowDetailRoute(row: PendingProductApplicationRow) {
  return {
    name: 'pending-product-approval-detail',
    params: { applicationNo: row.no }
  }
}

function rowEditRoute(row: PendingProductApplicationRow) {
  return {
    name: 'pending-product-approval-detail',
    params: { applicationNo: row.no },
    query: isReturnedMineDirectory.value ? { edit: '1' } : undefined
  }
}

onMounted(() => {
  void loadApplications()
  requestAnimationFrame(updateApprovalScrollState)
})
watch([activeTypeKey, activeScope, activeMineStatus], () => {
  resetPage()
  void loadApplications()
})
watch(rows, () => requestAnimationFrame(updateApprovalScrollState))
</script>

<template>
  <section class="approval-page">
    <header class="approval-page-title">
      <h2>待审批目录</h2>
      <p>多字段模式：固定核心列与操作列，中间字段横向滚动，避免字段换行挤压。</p>
    </header>

    <section class="approval-metric-grid">
      <article
        v-for="metric in approvalMetricCards"
        :key="metric.label"
        class="approval-metric-card"
        :class="metric.tone"
      >
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.hint }}</small>
      </article>
    </section>

    <div class="approval-workspace">
      <aside class="approval-sidebar-card">
        <div class="approval-sidebar-title">
          <ClipboardCheck :size="22" />
          <div>
            <h3>审批队列</h3>
            <p>按处理责任和任务类型筛选。</p>
          </div>
        </div>

        <div class="approval-task-list">
          <section v-for="group in taskGroups" :key="group.label" class="approval-task-group">
            <button
              v-if="group.scope"
              type="button"
              class="approval-task-item scope-button"
              :class="[group.tone, { active: activeScope === group.scope }]"
              @click="selectScope(group.scope)"
            >
              <div>
                <component :is="group.icon" :size="18" />
                <span>{{ group.label }}</span>
              </div>
              <strong>{{ group.count }}</strong>
            </button>
            <div v-else class="approval-task-item" :class="group.tone">
              <div>
                <component :is="group.icon" :size="18" />
                <span>{{ group.label }}</span>
              </div>
              <strong>{{ group.count }}</strong>
            </div>

            <div v-if="group.children" class="approval-sub-list">
              <button
                v-for="child in group.children"
                :key="child.key"
                type="button"
                :class="{ active: group.scope === 'mine' ? child.mineStatus === activeMineStatus && activeScope === 'mine' : child.key === activeTypeKey && activeScope === 'todo' }"
                @click="group.scope === 'mine' ? selectMineStatus(child.mineStatus) : selectType(child.key)"
              >
                <span>{{ child.label }}</span>
                <em>{{ child.count }}</em>
              </button>
            </div>
          </section>
        </div>
      </aside>

      <main class="approval-list-card">
        <div class="approval-context">
          <div>
            <h3>{{ activeScope === 'mine' ? activeMineOption.label : activeScope === 'handled' ? '已处理/抄送' : `${activeType.label} · 多字段列表` }}</h3>
            <p>
              {{
                activeScope === 'mine'
                  ? activeMineOption.description
                  : activeScope === 'handled'
                    ? '查看当前用户已处理、参与或被抄送的审批单据。'
                    : activeType.description
              }}
            </p>
          </div>
          <div v-if="activeScope === 'todo'" class="approval-action-row">
            <button type="button" class="btn btn-primary" @click="openCreateModal">
              <Plus :size="17" />
              新增
            </button>
            <details class="import-menu">
              <summary class="btn">
                <FileUp :size="17" />
                导入
                <ChevronDown :size="15" />
              </summary>
              <div>
                <button class="btn-text" type="button" @click="downloadTemplate">
                  <FileDown :size="16" />
                  模板导出
                </button>
                <button class="btn-text" type="button" @click="importInput?.click()">
                  <FileUp :size="16" />
                  按模板导入
                </button>
              </div>
            </details>
            <input ref="importInput" type="file" accept=".xlsx" hidden @change="handleImportFile" />
            <button type="button" class="btn" @click="exportRows">
              <Download :size="17" />
              导出
            </button>
            <button type="button" class="btn">列设置</button>
          </div>
        </div>
        <p v-if="message" class="inline-message">{{ message }}</p>

        <div class="approval-filter-row">
          <div class="approval-search">
            <Search :size="20" />
            <input v-model="keyword" type="text" placeholder="搜索审批单号、申请供应商、商品名称..." @keyup.enter="currentPage = 1; loadApplications()" />
          </div>
          <select class="filter-select" aria-label="申请类型">
            <option>全部类型</option>
            <option>{{ activeType.label }}</option>
          </select>
          <select class="filter-select" aria-label="审批状态">
            <option>全部状态</option>
            <option>初审中</option>
            <option>复审中</option>
          </select>
          <button class="filter-button" type="button" @click="currentPage = 1; loadApplications()">查询</button>
        </div>

        <section class="approval-field-nav">
          <div class="approval-field-nav-head">
            <strong>字段导航</strong>
            <span>可导航 {{ approvalFieldChips.length }} 个横向字段</span>
          </div>
          <div class="subnav-tabs">
            <button
              v-for="group in approvalFieldGroups"
              :key="group.key"
              type="button"
              :class="{ active: activeApprovalGroup === group.key }"
              @click="scrollApprovalToGroup(group.key)"
            >
              {{ group.label }}
            </button>
          </div>
          <div class="subnav-chips">
            <button
              v-for="field in approvalFieldChips"
              :key="field"
              type="button"
              class="subnav-chip"
              :class="{ active: activeApprovalField === field }"
              @click="scrollApprovalTableToField(field)"
            >
              {{ field }}
            </button>
          </div>
        </section>

        <div ref="approvalTableShellRef" class="approval-wide-table-shell" @scroll="updateApprovalScrollState">
          <table class="approval-wide-table">
            <thead>
              <tr>
                <th class="approval-sticky-check">
                  <input type="checkbox" :checked="allVisibleSelected" @change="toggleAllRows" />
                </th>
                <th class="approval-sticky-no">审批单号</th>
                <th class="approval-sticky-product">商品名称 / 规格</th>
                <th>申请类型</th>
                <th>申请人</th>
                <th>供应商</th>
                <th>厂家</th>
                <th>注册证号</th>
                <th>合同编码</th>
                <th>一级分类</th>
                <th>二级分类</th>
                <th>三级分类</th>
                <th>是否带量</th>
                <th>是否集采</th>
                <th>是否国产</th>
                <th>是否收费</th>
                <th>采购价</th>
                <th>UDI 编码</th>
                <th>风险标签</th>
                <th>节点 / 等待</th>
                <th>变更记录</th>
                <th class="approval-sticky-action">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="22" class="approval-empty">正在加载待审批任务...</td>
              </tr>
              <tr v-else-if="error">
                <td colspan="22" class="approval-empty">{{ error }}</td>
              </tr>
              <tr v-for="row in rows" v-else :key="row.no">
                <td class="approval-sticky-check">
                  <input v-model="selectedNos" type="checkbox" :value="row.no" />
                </td>
                <td class="approval-sticky-no">
                  <div class="approval-no">
                    <AlertTriangle v-if="row.warning" :size="15" />
                    <span>{{ row.no }}</span>
                  </div>
                </td>
                <td class="approval-sticky-product approval-product-cell">
                  <strong>{{ row.product }}</strong>
                  <small>{{ row.supplier }}</small>
                </td>
                <td><span class="approval-type" :class="approvalTypeClass(row.type)">{{ row.type }}</span></td>
                <td>{{ approvalWideField(row, 'applicant') }}</td>
                <td>{{ approvalWideField(row, 'supplier') }}</td>
                <td>{{ approvalWideField(row, 'manufacturer') }}</td>
                <td>{{ approvalWideField(row, 'registrationNo') }}</td>
                <td>{{ approvalWideField(row, 'contractCode') }}</td>
                <td>{{ approvalWideField(row, 'firstCategory') }}</td>
                <td>{{ approvalWideField(row, 'secondCategory') }}</td>
                <td>{{ approvalWideField(row, 'thirdCategory') }}</td>
                <td>{{ approvalWideField(row, 'volumeBased') }}</td>
                <td>{{ approvalWideField(row, 'centralized') }}</td>
                <td>{{ approvalWideField(row, 'domestic') }}</td>
                <td>{{ approvalWideField(row, 'chargeable') }}</td>
                <td class="approval-money">{{ approvalWideField(row, 'purchasePrice') }}</td>
                <td>{{ approvalWideField(row, 'udiCode') }}</td>
                <td>
                  <span
                    v-for="label in approvalRiskLabels(row)"
                    :key="label"
                    class="approval-risk-chip"
                    :class="approvalRiskTone(label)"
                  >
                    {{ label }}
                  </span>
                </td>
                <td>
                  <span class="approval-status" :class="row.statusTone">
                    <Clock3 v-if="row.statusTone === 'orange'" :size="14" />
                    <Send v-else-if="row.statusTone === 'blue'" :size="14" />
                    <XCircle v-else :size="14" />
                    {{ approvalWideField(row, 'wait') }}
                  </span>
                </td>
                <td class="approval-change-summary">{{ row.changeSummary || '-' }}</td>
                <td class="approval-sticky-action">
                  <div class="approval-row-actions">
                    <RouterLink
                      class="btn btn-sm"
                      :to="rowDetailRoute(row)"
                    >
                      查看
                    </RouterLink>
                    <RouterLink
                      class="btn btn-sm btn-primary"
                      :to="rowEditRoute(row)"
                    >
                      {{ rowPrimaryActionLabel() }}
                    </RouterLink>
                  </div>
                </td>
              </tr>
              <tr v-if="!loading && !error && rows.length === 0">
                <td colspan="22">
                  <div class="approval-empty-state">
                    <svg width="72" height="72" viewBox="0 0 24 24" fill="none" stroke="#cbd5e1" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                    </svg>
                    <p>当前分类暂无待审批任务</p>
                    <button v-if="activeScope === 'todo'" class="btn btn-primary" type="button" @click="openCreateModal">
                      <Plus :size="17" /> 去新增
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div
          ref="approvalScrollTrackRef"
          class="approval-wide-scroll-rail"
          :class="{ dragging: approvalScrollDragging }"
          role="scrollbar"
          aria-orientation="horizontal"
          :aria-valuenow="Math.round(approvalScrollState.scrollLeft)"
          :aria-valuemax="Math.max(0, approvalScrollState.scrollWidth - approvalScrollState.clientWidth)"
          tabindex="0"
          @pointerdown="jumpApprovalTableScroll"
        >
          <span
            class="approval-wide-scroll-thumb"
            :style="approvalScrollThumbStyle"
            @pointerdown.stop="startApprovalScrollDrag"
            @pointermove="moveApprovalScrollDrag"
            @pointerup="endApprovalScrollDrag"
            @pointercancel="endApprovalScrollDrag"
          ></span>
        </div>

        <div v-if="selectedNos.length" class="approval-batch-bar">
          <span>已选择 {{ selectedNos.length }} 条</span>
          <button type="button" class="btn btn-sm" :disabled="batchApproveLoading" @click="openBatchApprove('approve')">
            <CheckCircle2 :size="16" /> 批量通过
          </button>
          <button type="button" class="btn btn-sm" :disabled="batchApproveLoading" @click="openBatchApprove('return')">
            <XCircle :size="16" /> 批量退回
          </button>
          <button type="button" class="btn btn-sm" :disabled="batchApproveLoading" @click="openBatchApprove('reject')">
            <XCircle :size="16" /> 批量驳回
          </button>
          <button type="button" class="btn btn-sm" style="margin-left:auto" :disabled="batchApproveLoading" @click="selectedNos = []">
            清除选择
          </button>
        </div>

        <PaginationControls
          :page="currentPage"
          :size="pageSize"
          :total="totalItems"
          :loading="loading"
          :page-size-options="[10, 20, 25, 50, 100]"
          @change-page="changePage"
          @change-size="changePageSize"
        />
        <footer v-if="false && totalItems > 0" class="approval-pagination">
          <span class="approval-pagination-info">显示 {{ (currentPage - 1) * pageSize + 1 }}-{{ Math.min(currentPage * pageSize, totalItems) }} 条，共 {{ totalItems }} 条</span>
          <div class="approval-pagination-pages">
            <button class="btn btn-sm" type="button" :disabled="currentPage <= 1 || loading" @click="changePage(currentPage - 1)">上一页</button>
            <button
              v-for="p in visiblePages"
              :key="p"
              class="btn btn-sm page-num"
              :class="{ 'btn-primary': p === currentPage }"
              type="button"
              :disabled="loading"
              @click="changePage(p)"
            >{{ p }}</button>
            <button class="btn btn-sm" type="button" :disabled="currentPage >= totalPages || loading" @click="changePage(currentPage + 1)">下一页</button>
          </div>
        </footer>
      </main>
    </div>

    <div v-if="showBatchApproveModal" class="attachment-preview-mask" @click.self="showBatchApproveModal = false">
      <section class="supplier-dialog product-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>批量审批</p>
            <h3>{{ { approve: '批量通过', return: '批量退回修改', reject: '批量驳回' }[batchApproveAction] }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="showBatchApproveModal = false">
            <X :size="18" />
          </button>
        </header>
        <form @submit.prevent="confirmBatchApprove">
          <div class="approval-create-section">
            <p style="margin:0 0 12px;color:#475569;">已选择 <strong>{{ selectedNos.length }}</strong> 条审批单</p>
            <label>
              <span>审批意见</span>
              <textarea
                v-model="batchApproveOpinion"
                :placeholder="batchApproveAction === 'return' ? '请填写退回修改原因（必填）' : batchApproveAction === 'reject' ? '请填写驳回理由（必填）' : '填写审批意见（选填）'"
                rows="4"
                :required="batchApproveAction !== 'approve'"
              />
            </label>
          </div>
          <div class="dialog-actions">
            <button class="btn" type="button" :disabled="batchApproveLoading" @click="showBatchApproveModal = false">取消</button>
            <button class="btn btn-primary" type="submit" :disabled="batchApproveLoading">
              <Send :size="18" /> {{ batchApproveLoading ? '提交中...' : '确认' }}
            </button>
          </div>
        </form>
      </section>
    </div>

    <div v-if="showCreateModal" class="attachment-preview-mask" @click.self="showCreateModal = false">
      <section class="supplier-dialog product-dialog approval-create-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>待审批目录</p>
            <h3>新增待审批目录</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="showCreateModal = false">
            <X :size="18" />
          </button>
        </header>
        <form class="approval-create-form" @submit.prevent="handleCreateSubmit">
          <div class="approval-create-scroll">
            <section class="approval-create-section">
            <h4>申请信息</h4>
            <div class="supplier-form-grid compact">
              <label>
                <span>申请类型</span>
                <select v-model="createForm.applicationType">
                  <option>新品准入</option>
                  <option>信息变更</option>
                  <option>资质更新</option>
                  <option>价格调整</option>
                  <option>停用申请</option>
                </select>
              </label>
              <label class="wide"><span>申请原因</span><textarea v-model="createForm.changeReason" /></label>
            </div>
          </section>

          <section class="approval-create-section">
            <h4>商品基础信息</h4>
            <div class="supplier-form-grid compact">
              <label><span>商品编码</span><input v-model="createForm.productCode" required /></label>
              <label><span>商品名称</span><input v-model="createForm.productName" required /></label>
              <label><span>规格型号</span><input v-model="createForm.specModel" required /></label>
              <label><span>品牌</span><input v-model="createForm.brand" /></label>
              <label>
                <span>生产厂家</span>
                <select v-model="createForm.manufacturerName" @change="handleManufacturerChange">
                  <option value="">请选择生产厂家</option>
                  <option
                    v-for="item in withRetainedOption(manufacturerOptions, createForm.manufacturerName)"
                    :key="item.code + item.name"
                    :value="item.name"
                    :disabled="item.status !== 1"
                  >
                    {{ item.name }}（{{ item.code }}）{{ item.status === 1 ? '' : ' · 已停用' }}
                  </option>
                </select>
              </label>
              <label>
                <span>供应商</span>
                <select v-model="createForm.supplierName" @change="handleSupplierChange">
                  <option value="">请选择供应商</option>
                  <option
                    v-for="item in withRetainedOption(supplierOptions, createForm.supplierName)"
                    :key="item.code + item.name"
                    :value="item.name"
                    :disabled="item.status !== 1"
                  >
                    {{ item.name }}（{{ item.code }}）{{ item.status === 1 ? '' : ' · 已停用' }}
                  </option>
                </select>
              </label>
              <label><span>单位</span><input v-model="createForm.unit" required /></label>
              <label><span>储存条件</span><input v-model="createForm.storageCondition" /></label>
            </div>
          </section>

          <section class="approval-create-section">
            <h4>价格采购与分类</h4>
            <div class="supplier-form-grid compact">
              <label><span>采购价</span><input v-model.number="createForm.purchasePrice" type="number" step="0.0001" min="0" /></label>
              <label><span>零售价</span><input v-model.number="createForm.retailPrice" type="number" step="0.0001" min="0" /></label>
              <label><span>最小采购量</span><input v-model.number="createForm.minPurchaseQty" type="number" step="0.0001" min="0" /></label>
              <label><span>采购单位</span><input v-model="createForm.purchaseUnit" /></label>
              <label><span>中包装数量</span><input v-model.number="createForm.conversionRate" type="number" step="0.000001" min="0" /></label>
              <label><span>采购包装数量</span><input v-model.number="createForm.purchasePackageQty" type="number" step="0.0001" min="0" /></label>
              <label><span>合同编码</span><input v-model="createForm.contractCode" /></label>
              <label><span>一级分类</span><input v-model="createForm.firstCategory" /></label>
              <label><span>二级分类</span><input v-model="createForm.secondCategory" /></label>
              <label><span>三级分类</span><input v-model="createForm.thirdCategory" /></label>
              <label><span>招采子编码</span><input v-model="createForm.tenderSubCode" /></label>
            </div>
          </section>

          <section class="approval-create-section">
            <h4>资质与仓储</h4>
            <div class="supplier-form-grid compact">
              <label><span>UDI编码</span><input v-model="createForm.udiCode" /></label>
              <label><span>注册证号</span><input v-model="createForm.registrationNo" /></label>
              <label><span>注册证有效期</span><input v-model="createForm.registrationExpireDate" type="date" /></label>
              <label><span>生产许可证号</span><input v-model="createForm.productionLicenseNo" /></label>
              <label><span>经营许可证号</span><input v-model="createForm.businessLicenseNo" /></label>
              <label>
                <span>资质附件</span>
                <input ref="attachmentInput" type="file" multiple accept="image/*,.pdf" @change="handleCreateAttachments" />
              </label>
            </div>
            <div class="approval-attachment-summary">
              <strong>附件数量：{{ createAttachments.length }}</strong>
              <span>根据上传图片或 PDF 自动统计，无需人工填写。</span>
              <div v-if="createAttachments.length" class="approval-upload-list">
                <button
                  v-for="(file, index) in createAttachments"
                  :key="`${file.name}-${index}`"
                  type="button"
                  @click="removeCreateAttachment(index)"
                >
                  {{ file.name }} ×
                </button>
              </div>
            </div>
          </section>

          <section class="approval-create-section">
            <h4>业务属性</h4>
            <div class="dialog-toggle-row">
              <label><input v-model="createForm.volumeBased" type="checkbox" /> 是否带量</label>
              <label><input v-model="createForm.centralizedProcurement" type="checkbox" /> 是否集采</label>
              <label><input v-model="createForm.domestic" type="checkbox" /> 是否国产</label>
              <label><input v-model="createForm.chargeable" type="checkbox" /> 是否收费</label>
              <label><input v-model="createForm.highValue" type="checkbox" /> 高值耗材</label>
              <label><input v-model="createForm.coldChain" type="checkbox" /> 冷链</label>
              <label><input v-model="createForm.quotaManaged" type="checkbox" /> 定数管理</label>
            </div>
          </section>
          </div>

          <div class="dialog-actions approval-create-actions">
            <button class="btn" type="button" @click="showCreateModal = false">取消</button>
            <button class="btn btn-primary" type="submit">
              <Send :size="18" />
              提交审批
            </button>
          </div>
        </form>
      </section>
    </div>

    <div v-if="duplicateAlertVisible" class="attachment-preview-mask" @click.self="duplicateAlertVisible = false">
      <section class="duplicate-alert-dialog" role="alertdialog" aria-modal="true" aria-labelledby="duplicate-alert-title">
        <header>
          <div>
            <p>重复校验</p>
            <h3 id="duplicate-alert-title">该商品目录已存在！</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="duplicateAlertVisible = false">
            <X :size="18" />
          </button>
        </header>
        <div class="duplicate-alert-body">
          <AlertTriangle :size="40" aria-hidden="true" />
          <p>商品名称、规格型号、生产厂家、供应商、注册证号完全一致的目录已存在，请核对后修改或直接使用现有目录。</p>
        </div>
        <div class="dialog-actions">
          <button class="btn btn-primary" type="button" @click="duplicateAlertVisible = false">知道了</button>
        </div>
      </section>
    </div>
  </section>
</template>

