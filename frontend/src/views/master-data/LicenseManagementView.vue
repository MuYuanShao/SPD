<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Teleport } from 'vue'
import { Check, Columns3, ChevronDown, Eye, FileText, Plus, Search, Trash2, Upload, X, Pencil, Download } from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdEmptyState from '../../components/master-data/MdEmptyState.vue'
import { useNotify } from '../../composables/useNotify'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { formatDateTime } from '../../utils/dateTime'
import {
  createLicense,
  fetchLicenseDetail,
  fetchLicenseOwnerOptions,
  fetchLicenseHistory,
  renewLicense,
  type LicenseOwnerOption,
  type LicenseRevision,
  fetchLicenseAttachmentBlob,
  fetchLicenseAttachments,
  fetchLicenses,
  removeLicense,
  updateLicense,
  uploadLicenseAttachment,
  type LicenseAttachment,
  type LicensePayload,
  type LicenseRow,
  type LicenseType
} from '../../api/licenses'

const tabs: Array<{ type: LicenseType; label: string; ownerLabel: string }> = [
  { type: 'product', label: '商品证照', ownerLabel: '商品' },
  { type: 'supplier', label: '供应商证照', ownerLabel: '供应商' },
  { type: 'manufacturer', label: '厂家证照', ownerLabel: '厂家' },
  { type: 'contract', label: '合同管理', ownerLabel: '合同对象' }
]

const activeTab = ref<LicenseType>('product')
const activeTabInfo = computed(() => tabs.find((tab) => tab.type === activeTab.value) ?? tabs[0])

const notify = useNotify()

const rows = ref<LicenseRow[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const loading = ref(false)
const error = ref('')

const showFormModal = ref(false)
const editId = ref<number | null>(null)
const form = ref<LicensePayload>(emptyForm())
const saving = ref(false)
const formLoading = ref(false)
const renewalMode = ref(false)
const previousExpiry = ref('')
const formAttachmentFiles = ref<File[]>([])
const createdFormLicenseId = ref<number | null>(null)
const uploadedFormAttachmentCount = ref(0)
const ownerKeyword = ref('')
const ownerOptions = ref<LicenseOwnerOption[]>([])
const ownerLoading = ref(false)
const ownerPage = ref(1)
const ownerTotal = ref(0)
const historyOpen = ref(false)
const historyLoading = ref(false)
const revisions = ref<Array<LicenseRevision & { snapshot: Record<string, any> }>>([])
const historyTitle = ref('')
const historyLicenseId = ref<number | null>(null)
const historyFileOpen = ref(false)
const ownerSearched = ref(false)

async function searchOwners(pageNumber = 1) {
  ownerLoading.value = true
  try {
    const data = await fetchLicenseOwnerOptions(form.value.ownerType || activeTab.value, ownerKeyword.value, pageNumber)
    ownerSearched.value = true
    ownerOptions.value = data.rows
    ownerPage.value = pageNumber
    ownerTotal.value = data.total
  } catch (err) { notify.error(err instanceof Error ? err.message : '主体搜索失败') }
  finally { ownerLoading.value = false }
}
function selectOwner(owner: LicenseOwnerOption) {
  form.value.ownerId = owner.id
  form.value.ownerCode = owner.code
  form.value.ownerName = owner.name
  ownerKeyword.value = `${owner.name}（${owner.code}）`
  ownerOptions.value = []
}
function onOwnerInput() { form.value.ownerId = null; form.value.ownerCode = ''; form.value.ownerName = '' }
function resetOwner() {
  ownerSearched.value = false
  form.value.ownerId = null; form.value.ownerCode = ''; form.value.ownerName = ''
  ownerKeyword.value = ''; ownerOptions.value = []
}
async function openHistory(row: LicenseRow) {
  historyLicenseId.value = row.id
  historyOpen.value = true; historyLoading.value = true; revisions.value = []; historyTitle.value = row.licenseName
  try {
    const data = await fetchLicenseHistory(row.id)
    revisions.value = data.history.map(item => ({ ...item, snapshot: JSON.parse(item.snapshotJson) }))
  } catch (err) { notify.error(err instanceof Error ? err.message : '历史记录加载失败') }
  finally { historyLoading.value = false }
}
function operationText(value: string) {
  return ({ create: '新增', update: '编辑', renew: '续证', attachment: '附件上传', baseline: '历史原版本', delete: '删除', mcp_import: 'MCP审批发布' } as Record<string, string>)[value] || value
}

const showAttachmentModal = ref(false)
const attachmentLicense = ref<LicenseRow | null>(null)
const attachments = ref<LicenseAttachment[]>([])
const attachmentsLoading = ref(false)
const previewBlobUrl = ref('')
const previewContentType = ref('')
const previewName = ref('')
const previewing = ref(false)

function emptyForm(): LicensePayload {
  return {
    licenseType: activeTab.value,
    licenseName: '',
    licenseNo: '',
    ownerType: activeTab.value === 'contract' ? 'supplier' : activeTab.value,
    ownerId: null,
    ownerCode: '',
    ownerName: '',
    partyA: '',
    partyB: '',
    contractAmount: null,
    issueDate: '',
    expireDate: '',
    status: 1,
    remark: ''
  }
}

function typeLabel(type: LicenseType) {
  return tabs.find((tab) => tab.type === type)?.label ?? type
}

function statusText(row: LicenseRow) {
  const labels: Record<string, string> = { invalid: '失效', expired: '已过期', not_effective: '尚未生效', valid: '有效' }
  if (row.effectiveStatus) return labels[row.effectiveStatus] || row.effectiveStatus
  if (row.status === 0) return '失效'
  if (row.expireDate && row.expireDate < today()) return '已过期'
  return row.status === 1 ? '有效' : '失效'
}

function today() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchLicenses(activeTab.value, keyword.value, page.value, size)
    rows.value = result.rows
    total.value = result.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '证照列表加载失败'
  } finally {
    loading.value = false
  }
}

function switchTab(type: LicenseType) {
  activeTab.value = type
  page.value = 1
  keyword.value = ''
  loadList()
}

function onSearch() {
  page.value = 1
  loadList()
}

function resetFormAttachments() {
  formAttachmentFiles.value = []
  createdFormLicenseId.value = null
  uploadedFormAttachmentCount.value = 0
}

function closeFormModal() {
  if (saving.value) return
  showFormModal.value = false
  resetFormAttachments()
}

function selectFormAttachments(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files ?? [])
  const invalid: string[] = []
  for (const file of selected) {
    if (!/\.(pdf|png|jpe?g|gif|webp)$/i.test(file.name) || file.size === 0) {
      invalid.push(file.name)
      continue
    }
    if (!formAttachmentFiles.value.some(existing => existing.name === file.name
        && existing.size === file.size && existing.lastModified === file.lastModified)) {
      formAttachmentFiles.value.push(file)
    }
  }
  input.value = ''
  if (invalid.length) notify.warning(`以下文件为空或格式不支持：${invalid.join('、')}。请选择 PDF 或图片文件。`)
}

function openCreateModal() {
  resetFormAttachments()
  renewalMode.value = false
  ownerKeyword.value = ''; ownerOptions.value = []; ownerSearched.value = false
  editId.value = null
  form.value = emptyForm()
  form.value.licenseType = activeTab.value
  showFormModal.value = true
}

async function openEditModal(row: LicenseRow, renewal = false) {
  if (formLoading.value) return
  formLoading.value = true
  try {
    const detail = await fetchLicenseDetail(row.id)
    resetFormAttachments()
    editId.value = row.id
    renewalMode.value = renewal
    previousExpiry.value = detail.expireDate || ''
    form.value = { ...detail, ownerType: detail.ownerType || (detail.licenseType === 'contract' ? 'supplier' : detail.licenseType),
      ownerId: detail.ownerId ?? null, revisionNo: detail.revisionNo,
      status: renewal ? 1 : detail.status, expireDate: renewal ? '' : detail.expireDate }
    ownerKeyword.value = detail.ownerName ? `${detail.ownerName}（${detail.ownerCode || ''}）` : ''
    ownerOptions.value = []; ownerSearched.value = false
    showFormModal.value = true
  } catch (err) { notify.error(err instanceof Error ? err.message : '证照详情加载失败') }
  finally { formLoading.value = false }
}

async function submitForm() {
  if (saving.value) return
  if (!form.value.licenseName.trim()) {
    notify.warning('证照名称为必填项')
    return
  }
  if (!form.value.ownerId) { notify.warning('请搜索并选择证照所属主体'); return }
  saving.value = true
  try {
    if (editId.value === null) {
      if (createdFormLicenseId.value === null) {
        const result = await createLicense(form.value)
        createdFormLicenseId.value = result.id
        form.value.revisionNo = 1
      } else {
        await updateLicense(createdFormLicenseId.value, form.value)
        form.value.revisionNo = (form.value.revisionNo ?? 1) + 1
      }
      // Remove only acknowledged uploads so retries keep the same license and remaining files.
      while (formAttachmentFiles.value.length) {
        const file = formAttachmentFiles.value[0]
        await uploadLicenseAttachment(createdFormLicenseId.value, file, 'license')
        formAttachmentFiles.value.shift()
        uploadedFormAttachmentCount.value += 1
        form.value.revisionNo = (form.value.revisionNo ?? 1) + 1
      }
      notify.success(uploadedFormAttachmentCount.value ? '证照已新增，附件上传成功' : '证照已新增')
    } else if (renewalMode.value) {
      await renewLicense(editId.value, form.value)
      notify.success('续证已保存，到期联动按最新有效期自动恢复')
    } else {
      await updateLicense(editId.value, form.value)
      notify.success('证照已保存')
    }
    showFormModal.value = false
    resetFormAttachments()
    await loadList()
  } catch (err) {
    const detail = err instanceof Error ? err.message : '证照保存失败'
    if (createdFormLicenseId.value !== null) {
      notify.warning(`证照已保存，当前还有 ${formAttachmentFiles.value.length} 个附件未上传。本次保存未完成：${detail}。请重试保存。`)
      await loadList()
    } else {
      notify.error(detail)
    }
  } finally {
    saving.value = false
  }
}

async function confirmRemove(row: LicenseRow) {
  if (!window.confirm(`确认删除证照「${row.licenseName}」？删除后其附件一并失效。`)) return
  try {
    await removeLicense(row.id)
    notify.success('证照已删除')
    await loadList()
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '证照删除失败')
  }
}

async function openAttachments(row: LicenseRow) {
  attachmentLicense.value = row
  attachments.value = []
  closePreview()
  showAttachmentModal.value = true
  await loadAttachments()
}

async function loadAttachments() {
  if (!attachmentLicense.value) return
  attachmentsLoading.value = true
  try {
    attachments.value = await fetchLicenseAttachments(attachmentLicense.value.id)
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '附件加载失败')
  } finally {
    attachmentsLoading.value = false
  }
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !attachmentLicense.value) return
  try {
    await uploadLicenseAttachment(attachmentLicense.value.id, file)
    input.value = ''
    await loadAttachments()
    await loadList()
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '附件上传失败')
  }
}

async function previewAttachment(attachment: LicenseAttachment, revision?: number) {
  previewing.value = true
  try {
    const { blob, contentType } = await fetchLicenseAttachmentBlob(attachment.id, revision !== undefined && historyLicenseId.value !== null ? { licenseId: historyLicenseId.value, revision } : undefined)
    if (previewBlobUrl.value) URL.revokeObjectURL(previewBlobUrl.value)
    previewBlobUrl.value = URL.createObjectURL(blob)
    previewContentType.value = contentType
    previewName.value = attachment.fileName
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '附件预览失败')
  } finally {
    previewing.value = false
  }
}

function closePreview() {
  if (previewBlobUrl.value) URL.revokeObjectURL(previewBlobUrl.value)
  previewBlobUrl.value = ''
  previewContentType.value = ''
  previewName.value = ''
}

async function downloadAttachment(attachment: LicenseAttachment) {
  try {
    const { blob } = await fetchLicenseAttachmentBlob(attachment.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = attachment.fileName
    link.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    notify.error(err instanceof Error ? err.message : '附件下载失败')
  }
}

const isContract = computed(() => activeTab.value === 'contract')

// ===== 统一列表：列宽拖拽 + 列设置 + 统一斑马纹表格 =====
const LICENSE_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'licenseName', label: '证照名称', width: 220, visible: true },
  { key: 'licenseNo', label: '证照编号', width: 180, visible: true },
  { key: 'owner', label: '所属主体', width: 200, visible: true },
  { key: 'issueDate', label: '签发日期', width: 150, visible: true },
  { key: 'expireDate', label: '有效期至', width: 150, visible: true },
  { key: 'status', label: '状态', width: 120, visible: true },
  { key: 'attachmentCount', label: '附件', width: 110, visible: true }
]

const licenseCols = useListColumns({
  storageKey: 'spd-license-cols-v1',
  defaults: LICENSE_COL_DEFAULTS
})
const licenseTableRef = licenseCols.tableRef

const isLicenseColVisible = (key: string) => licenseCols.isColumnVisible(key)
const licenseColWidth = (key: string) => licenseCols.columnWidth(key)
const startLicenseColDrag = (key: string, event: MouseEvent) => licenseCols.startColumnDrag(key, event)

/** 证照状态条配色：有效为成功色，其余（失效 / 已过期 / 尚未生效）为警示色 */
function licenseStatusChipClass(row: LicenseRow) {
  return statusText(row) === '有效' ? 'is-success' : 'is-warning'
}

/** 所属主体展示文案：合同展示双方，其余展示名称 + 编码 */
function licenseOwnerText(row: LicenseRow) {
  if (isContract.value) {
    return [row.partyA, row.partyB].filter(Boolean).join(' / ') || '-'
  }
  return [row.ownerName, row.ownerCode].filter(Boolean).join(' / ') || '-'
}

/** 所属主体副标题：合同展示所属主体，其余展示主体编码 */
function licenseOwnerSubText(row: LicenseRow) {
  if (isContract.value) {
    return `所属：${row.ownerName || '未关联'} / ${row.ownerCode || '-'}`
  }
  return row.ownerCode || ''
}

/** 签发日期 / 合同金额列的动态表头与内容 */
function licenseIssueHeader() {
  return isContract.value ? '合同金额' : '签发日期'
}

function licenseIssueValue(row: LicenseRow) {
  if (isContract.value) {
    return row.contractAmount != null ? `¥${row.contractAmount}` : '-'
  }
  return row.issueDate || '-'
}

/** 日期统一为 YYYY-MM-DD HH:MM:SS */
function formatListDateTime(value: unknown) {
  return formatDateTime(value)
}

onMounted(loadList)
onUnmounted(closePreview)
</script>

<template>
  <div class="license-page spd-unified-list">
    <MdPageHeader
      crumb="基础数据管理"
      title="证照管理"
      description="集中管理商品、供应商、厂家的证照与合同，支持附件上传与阅览。"
    />

    <div class="license-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.type"
        type="button"
        :class="['license-tab', { active: activeTab === tab.type }]"
        @click="switchTab(tab.type)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div class="license-toolbar md-table-toolbar">
      <div class="license-search md-table-toolbar-main">
        <input
          v-model.trim="keyword"
          :placeholder="`搜索证照名称、编号、${activeTabInfo.ownerLabel}名称或编码`"
          @keyup.enter="onSearch"
        />
        <button class="md-btn md-btn-primary" type="button" @click="onSearch"><Search :size="16" /> 查询</button>
      </div>
      <div class="md-table-toolbar-main">
        <button class="md-btn md-btn-primary" type="button" @click="openCreateModal">
          <Plus :size="16" /> 新增{{ activeTabInfo.label }}
        </button>
        <div class="ulc-col-settings">
          <button type="button" class="md-btn md-btn-primary" @click="licenseCols.settingsOpen.value = !licenseCols.settingsOpen.value">
            <Columns3 :size="16" /> 列设置
            <ChevronDown :size="14" :class="{ rotated: licenseCols.settingsOpen.value }" />
          </button>
          <div v-if="licenseCols.settingsOpen.value" class="ulc-col-settings-pop">
            <header>
              <strong>列展示设置</strong>
              <button type="button" class="ulc-link" @click="licenseCols.resetColumns()">恢复默认</button>
            </header>
            <div class="ulc-col-settings-list">
              <label v-for="col in licenseCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                <input
                  type="checkbox"
                  :checked="col.visible"
                  :disabled="col.visible && licenseCols.visibleColumnCount.value <= 1"
                  @change="licenseCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ col.label }}</span>
                <small>{{ col.width }}px</small>
              </label>
            </div>
            <footer>
              <button type="button" class="ulc-action" @click="licenseCols.settingsOpen.value = false">确定</button>
            </footer>
          </div>
        </div>
      </div>
    </div>

    <p v-if="error" class="license-error">{{ error }}</p>

    <div class="ulc-table-wrap">
      <el-table
        ref="licenseTableRef"
        v-loading="loading"
        class="ulc-table"
        :data="rows"
        row-key="id"
        border
        stripe
        style="width: 100%"
        :empty-text="`暂无${activeTabInfo.label}数据，点击右上角新增`"
      >
        <el-table-column
          v-if="isLicenseColVisible('licenseName')"
          label="证照名称"
          :min-width="licenseColWidth('licenseName')"
          show-overflow-tooltip
        >
          <template #header>
            <span class="ulc-th-text">证照名称</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startLicenseColDrag('licenseName', $event)" />
          </template>
          <template #default="{ row }">
            <div class="ulc-cell-stack">
              <span class="ulc-strong">{{ row.licenseName }}</span>
              <span v-if="row.sourceSystem === 'MCP-UI'" class="ulc-sub">MCP审批同步</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isLicenseColVisible('licenseNo')"
          :label="isContract ? '合同编号' : '证照编号'"
          :min-width="licenseColWidth('licenseNo')"
          show-overflow-tooltip
        >
          <template #header>
            <span class="ulc-th-text">{{ isContract ? '合同编号' : '证照编号' }}</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startLicenseColDrag('licenseNo', $event)" />
          </template>
          <template #default="{ row }">
            <span class="ulc-mono">{{ row.licenseNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isLicenseColVisible('owner')"
          :label="isContract ? '合同双方' : activeTabInfo.ownerLabel"
          :min-width="licenseColWidth('owner')"
          show-overflow-tooltip
        >
          <template #header>
            <span class="ulc-th-text">{{ isContract ? '合同双方' : activeTabInfo.ownerLabel }}</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startLicenseColDrag('owner', $event)" />
          </template>
          <template #default="{ row }">
            <div class="ulc-cell-stack">
              <span class="ulc-strong">{{ licenseOwnerText(row) }}</span>
              <span v-if="licenseOwnerSubText(row)" class="ulc-sub">{{ licenseOwnerSubText(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isLicenseColVisible('issueDate')"
          :label="licenseIssueHeader()"
          :min-width="licenseColWidth('issueDate')"
          align="center"
        >
          <template #header>
            <span class="ulc-th-text">{{ licenseIssueHeader() }}</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startLicenseColDrag('issueDate', $event)" />
          </template>
          <template #default="{ row }">
            <span class="ulc-strong">{{ licenseIssueValue(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isLicenseColVisible('expireDate')"
          label="有效期至"
          :min-width="licenseColWidth('expireDate')"
          align="center"
        >
          <template #header>
            <span class="ulc-th-text">有效期至</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startLicenseColDrag('expireDate', $event)" />
          </template>
          <template #default="{ row }">
            <span class="ulc-time">{{ row.expireDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isLicenseColVisible('status')"
          label="状态"
          :min-width="licenseColWidth('status')"
          align="center"
        >
          <template #header>
            <span class="ulc-th-text">状态</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startLicenseColDrag('status', $event)" />
          </template>
          <template #default="{ row }">
            <span class="ulc-chip" :class="licenseStatusChipClass(row)">
              {{ statusText(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isLicenseColVisible('attachmentCount')"
          label="附件"
          :min-width="licenseColWidth('attachmentCount')"
          align="center"
        >
          <template #header>
            <span class="ulc-th-text">附件</span><i class="ulc-col-grip" title="拖动调节列宽" @mousedown="startLicenseColDrag('attachmentCount', $event)" />
          </template>
          <template #default="{ row }">
            <span class="ulc-tag">{{ row.attachmentCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" :min-width="300" align="center" fixed="right">
          <template #default="{ row }">
            <div class="ulc-actions">
              <button
                v-if="row.sourceSystem !== 'MCP-UI'"
                type="button"
                class="ulc-action"
                :disabled="formLoading"
                @click="openEditModal(row)"
              >
                <Pencil :size="14" /> 编辑
              </button>
              <button
                v-if="row.sourceSystem !== 'MCP-UI'"
                type="button"
                class="ulc-action"
                :disabled="formLoading"
                @click="openEditModal(row, true)"
              >
                续证
              </button>
              <button type="button" class="ulc-action" @click="openHistory(row)">
                历史
              </button>
              <button type="button" class="ulc-action" @click="openAttachments(row)">
                <FileText :size="14" /> 附件
              </button>
              <button
                v-if="row.sourceSystem !== 'MCP-UI'"
                type="button"
                class="ulc-action is-danger"
                @click="confirmRemove(row)"
              >
                <Trash2 :size="14" /> 删除
              </button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <MdEmptyState :text="`暂无${activeTabInfo.label}数据`" hint="点击右上角新增" />
        </template>
      </el-table>
      <div v-if="total > size" class="license-pagination">
        <button class="btn" type="button" :disabled="page <= 1" @click="page--; loadList()">上一页</button>
        <span>第 {{ page }} 页 / 共 {{ Math.ceil(total / size) }} 页（{{ total }} 条）</span>
        <button class="btn" type="button" :disabled="page >= Math.ceil(total / size)" @click="page++; loadList()">下一页</button>
      </div>
    </div>

    <!-- 新增 / 编辑证照 -->
    <Teleport to="body">
    <div v-if="showFormModal" class="attachment-preview-mask" @click.self="closeFormModal">
      <section class="license-form-dialog" role="dialog" aria-modal="true">
        <header class="lf-dialog-header">
          <div class="lf-dialog-title">
            <p>{{ activeTabInfo.label }}</p>
            <h3>{{ renewalMode ? '续证' : editId === null ? '新增' : '编辑' }}{{ activeTabInfo.label }}</h3>
            <span v-if="renewalMode" class="lf-dialog-tag lf-dialog-tag-renew">续证模式</span>
            <span v-else-if="editId !== null" class="lf-dialog-tag lf-dialog-tag-edit">编辑模式</span>
          </div>
          <button class="lf-btn lf-btn-ghost" type="button" aria-label="关闭" :disabled="saving" @click="closeFormModal">
            <X :size="18" />
          </button>
        </header>
        <form class="lf-dialog-form" @submit.prevent="submitForm">
          <div class="lf-dialog-form-grid">
            <label class="lf-field wide">
              <span>证照名称</span>
              <input v-model.trim="form.licenseName" required />
            </label>
            <label class="lf-field">
              <span>{{ isContract ? '合同编号' : '证照编号' }}</span>
              <input v-model.trim="form.licenseNo" />
            </label>
            <label v-if="isContract" class="lf-field">
              <span>所属主体类型</span>
              <select v-model="form.ownerType" :disabled="renewalMode && Boolean(form.ownerId)" @change="resetOwner">
                <option value="supplier">供应商</option>
                <option value="manufacturer">厂家</option>
                <option value="product">商品</option>
              </select>
            </label>

            <div class="lf-field wide lf-owner-picker">
              <span>所属主体（搜索名称或编码后选择）</span>
              <div class="lf-owner-search">
                <input v-model="ownerKeyword" placeholder="名称或编码" @input="onOwnerInput" :disabled="renewalMode && Boolean(form.ownerId)" @keydown.enter.prevent="searchOwners()" />
                <button class="lf-btn lf-btn-info lf-btn-icon" type="button" :disabled="ownerLoading || (renewalMode && Boolean(form.ownerId))" @click="searchOwners()">
                  <Search :size="16" />
                </button>
              </div>
              <div v-if="ownerOptions.length" class="lf-owner-options">
                <button v-for="owner in ownerOptions" :key="owner.id" type="button" class="lf-owner-option-btn" @click="selectOwner(owner)">
                  {{ owner.name }}（{{ owner.code }}）
                </button>
                <div class="lf-owner-pager">
                  <button class="lf-btn lf-btn-secondary lf-btn-sm" type="button" :disabled="ownerPage <= 1 || ownerLoading" @click="searchOwners(ownerPage - 1)">上一页</button>
                  <span>{{ ownerPage }} / {{ Math.ceil(ownerTotal / 10) || 1 }}</span>
                  <button class="lf-btn lf-btn-secondary lf-btn-sm" type="button" :disabled="ownerPage * 10 >= ownerTotal || ownerLoading" @click="searchOwners(ownerPage + 1)">下一页</button>
                </div>
              </div>
              <p v-if="ownerSearched && !ownerLoading && ownerTotal === 0" class="lf-owner-empty">没有匹配的有效主体</p>
              <div class="lf-owner-selected">
                <span v-if="form.ownerId" class="lf-owner-tag">
                  <Check :size="12" />
                  已关联：{{ form.ownerName }} / {{ form.ownerCode }}
                </span>
                <span v-else class="lf-owner-tag lf-owner-tag-warn">未关联有效主体，请选择后保存</span>
              </div>
            </div>

            <template v-if="!isContract">
              <label class="lf-field">
                <span>主体编码</span>
                <input :value="form.ownerCode" readonly />
              </label>
              <label class="lf-field">
                <span>签发日期</span>
                <input v-model="form.issueDate" type="date" />
              </label>
            </template>
            <template v-else>
              <label class="lf-field">
                <span>甲方</span>
                <input v-model.trim="form.partyA" placeholder="医院/甲方名称" />
              </label>
              <label class="lf-field">
                <span>乙方</span>
                <input v-model.trim="form.partyB" placeholder="供应商/乙方名称" />
              </label>
              <label class="lf-field">
                <span>合同金额</span>
                <input v-model.number="form.contractAmount" type="number" step="0.01" min="0" placeholder="可选" />
              </label>
              <label class="lf-field">
                <span>签订日期</span>
                <input v-model="form.issueDate" type="date" />
              </label>
            </template>

            <label class="lf-field">
              <span>有效期至</span>
              <input v-model="form.expireDate" type="date" :required="renewalMode" />
              <small v-if="renewalMode">原有效期：{{ previousExpiry || '未维护' }}；续证须填写新有效期</small>
            </label>
            <label class="lf-field">
              <span>状态</span>
              <select v-model.number="form.status">
                <option :value="1">有效</option>
                <option :value="0">失效</option>
              </select>
            </label>
            <label class="lf-field wide lf-remark">
              <span>备注</span>
              <textarea v-model.trim="form.remark" rows="2" placeholder="可选" />
            </label>
          </div>

          <div v-if="editId === null" class="lf-dialog-upload">
            <div class="lf-upload-header">
              <span>证照附件</span>
              <label class="lf-btn lf-btn-secondary">
                <Upload :size="15" /> 选择附件
                <input type="file" hidden multiple aria-label="选择证照附件" accept=".pdf,.png,.jpg,.jpeg,.gif,.webp" @change="selectFormAttachments" />
              </label>
              <small>支持多选 PDF 与图片，点击保存后上传</small>
            </div>
            <p v-if="uploadedFormAttachmentCount" class="lf-upload-success">
              <Check :size="14" /> 已上传 {{ uploadedFormAttachmentCount }} 个附件
            </p>
            <ul v-if="formAttachmentFiles.length" class="lf-file-list">
              <li v-for="(file, index) in formAttachmentFiles" :key="`${file.name}-${file.size}-${file.lastModified}`">
                <span class="lf-file-name" :title="file.name">{{ file.name }}</span>
                <span class="lf-file-size">{{ (file.size / 1024).toFixed(1) }} KB</span>
                <button type="button" class="lf-btn lf-btn-danger lf-btn-sm" @click="formAttachmentFiles.splice(index, 1)">
                  <X :size="14" /> 移除
                </button>
              </li>
            </ul>
            <small v-else-if="!uploadedFormAttachmentCount" class="lf-upload-hint">尚未选择附件</small>
          </div>

          <div class="lf-dialog-actions">
            <button class="lf-btn lf-btn-secondary" type="button" :disabled="saving" @click="closeFormModal">取消</button>
            <button class="lf-btn lf-btn-primary" type="submit" :disabled="saving">
              <Check :size="16" />
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </form>
      </section>
    </div>
    </Teleport>

    <!-- 证照历史 -->
    <Teleport to="body">
    <div v-if="historyOpen" class="attachment-preview-mask" @click.self="historyOpen = false">
      <section class="lh-dialog" role="dialog" aria-modal="true">
        <header class="lh-dialog-header">
          <div class="lh-dialog-title">
            <p>历史记录</p>
            <h3>证照历史：{{ historyTitle }}</h3>
          </div>
          <button class="lf-btn lf-btn-ghost" type="button" aria-label="关闭" @click="historyOpen = false">
            <X :size="18" />
          </button>
        </header>
        <div class="lh-dialog-body">
          <p v-if="historyLoading" class="approval-empty">正在加载历史...</p>
          <p v-else-if="!revisions.length" class="approval-empty">该历史证照尚未产生版本记录；首次编辑或续证时会保留原版本。</p>
          <article v-for="revision in revisions" :key="revision.revisionNo" class="lh-revision-item">
            <div class="lh-revision-header">
              <span class="lh-revision-badge">
                <FileText :size="13" />
                版本 {{ revision.revisionNo }}
              </span>
              <span class="lh-revision-type">{{ operationText(revision.operationType) }}</span>
              <span class="lh-revision-op">操作人：{{ revision.operatorName }}</span>
              <span class="lh-revision-time">{{ revision.createTime }}</span>
            </div>
            <div class="lh-revision-detail">
              <div class="lh-rev-row">
                <span class="lh-rev-label">证照名称</span>
                <span class="lh-rev-value">{{ revision.snapshot.licenseName }}</span>
              </div>
              <div class="lh-rev-row">
                <span class="lh-rev-label">证照编号</span>
                <span class="lh-rev-value">{{ revision.snapshot.licenseNo || '-' }}</span>
              </div>
              <div class="lh-rev-row">
                <span class="lh-rev-label">主体</span>
                <span class="lh-rev-value">{{ revision.snapshot.ownerName || '-' }}（{{ revision.snapshot.ownerCode || '-' }}）</span>
              </div>
              <div class="lh-rev-row">
                <span class="lh-rev-label">有效期</span>
                <span class="lh-rev-value">
                  {{ revision.snapshot.issueDate || '-' }} ~ {{ revision.snapshot.expireDate || '-' }}
                </span>
              </div>
              <div class="lh-rev-row">
                <span class="lh-rev-label">状态</span>
                <span class="lh-rev-value">
                  <span class="status-badge" :class="{ warning: revision.snapshot.status !== 1 }">
                    {{ revision.snapshot.status === 1 ? '有效' : '失效' }}
                  </span>
                </span>
              </div>
              <div class="lh-rev-row">
                <span class="lh-rev-label">备注</span>
                <span class="lh-rev-value">{{ revision.snapshot.remark || '-' }}</span>
              </div>
              <div class="lh-rev-row">
                <span class="lh-rev-label">当时附件</span>
                <span class="lh-rev-value lh-rev-files">
                  <template v-if="revision.snapshot.attachments?.length">
                    <button
                      v-for="file in revision.snapshot.attachments"
                      :key="file.id"
                      class="lf-btn lf-btn-info lf-btn-sm"
                      type="button"
                      @click="historyFileOpen = true; closePreview(); previewAttachment(file, revision.revisionNo)"
                    >
                      <Eye :size="13" /> {{ file.fileName }}
                    </button>
                  </template>
                  <span v-else class="lh-rev-no-files">无</span>
                </span>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>
    </Teleport>

    <!-- 历史附件阅览 -->
    <Teleport to="body">
    <div v-if="historyFileOpen" class="attachment-preview-mask" @click.self="historyFileOpen = false; closePreview()">
      <section class="license-attachment-dialog" role="dialog" aria-modal="true">
        <header class="la-dialog-header">
          <div class="la-dialog-title">
            <p>历史附件</p>
            <h3>{{ previewName || '历史附件阅览' }}</h3>
          </div>
          <button class="lf-btn lf-btn-ghost" type="button" aria-label="关闭" @click="historyFileOpen = false; closePreview()">
            <X :size="18" />
          </button>
        </header>
        <div class="la-dialog-body">
          <div class="la-attachment-preview la-full-preview">
            <p v-if="previewing" class="approval-empty">正在加载附件...</p>
            <img
              v-else-if="previewBlobUrl && previewContentType.startsWith('image/')"
              :src="previewBlobUrl" :alt="previewName"
            />
            <iframe
              v-else-if="previewBlobUrl && previewContentType.includes('pdf')"
              :src="previewBlobUrl" :title="previewName"
            />
            <p v-else class="approval-empty">该格式请从历史记录中下载查看</p>
          </div>
        </div>
      </section>
    </div>
    </Teleport>

    <!-- 附件阅览 -->
    <Teleport to="body">
    <div v-if="showAttachmentModal" class="attachment-preview-mask" @click.self="closePreview(); showAttachmentModal = false">
      <section class="license-attachment-dialog" role="dialog" aria-modal="true">
        <header class="la-dialog-header">
          <div class="la-dialog-title">
            <p>附件阅览</p>
            <h3>{{ attachmentLicense?.licenseName }}</h3>
          </div>
          <button class="lf-btn lf-btn-ghost" type="button" aria-label="关闭" @click="closePreview(); showAttachmentModal = false">
            <X :size="18" />
          </button>
        </header>
        <div class="la-dialog-body">
          <div class="la-attachment-list">
            <p v-if="attachmentLicense?.sourceSystem === 'MCP-UI'">来源证照由MCP下发并经院内审批生效，变更请在MCP-UI发起。</p>
            <div v-else class="la-upload-row">
              <label class="lf-btn lf-btn-secondary">
                <Upload :size="15" /> 上传附件
                <input type="file" hidden accept=".pdf,.png,.jpg,.jpeg,.gif,.webp" @change="handleUpload" />
              </label>
              <span>支持 PDF 与图片文件</span>
            </div>
            <p v-if="attachmentsLoading" class="approval-empty">正在加载附件...</p>
            <p v-else-if="!attachments.length" class="approval-empty">暂无附件，请上传证照扫描件</p>
            <ul v-else class="la-attachment-items">
              <li v-for="item in attachments" :key="item.id" class="la-attachment-item">
                <div class="la-attachment-info">
                  <strong>{{ item.fileName }}</strong>
                  <span>{{ item.createTime }} · {{ (item.size / 1024).toFixed(1) }} KB</span>
                </div>
                <div class="la-attachment-actions">
                  <button class="lf-btn lf-btn-info lf-btn-sm" type="button" :disabled="previewing" @click="previewAttachment(item)">
                    <Eye :size="14" /> 阅览
                  </button>
                  <button class="lf-btn lf-btn-secondary lf-btn-sm" type="button" @click="downloadAttachment(item)">
                    <Download :size="14" /> 下载
                  </button>
                </div>
              </li>
            </ul>
          </div>
          <div class="la-attachment-preview">
            <template v-if="previewBlobUrl">
              <img v-if="previewContentType.startsWith('image/')" :src="previewBlobUrl" :alt="previewName" />
              <iframe v-else-if="previewContentType.includes('pdf')" :src="previewBlobUrl" :title="previewName" />
              <p v-else class="approval-empty">该文件类型不支持在线预览，请下载后查看：{{ previewName }}</p>
            </template>
            <div v-else class="la-preview-placeholder">
              <Eye :size="40" />
              <p>点击左侧「阅览」查看证照附件</p>
            </div>
          </div>
        </div>
      </section>
    </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* ============================================================
   Shared button system
   ============================================================ */
.lf-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 5px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s, transform 0.18s, box-shadow 0.18s, color 0.18s;
  font-family: inherit;
}

.lf-btn:hover { transform: translateY(-1px); }
.lf-btn:active { transform: translateY(0); }
.lf-btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }

.lf-btn-icon {
  width: 32px; min-height: 32px; padding: 0; justify-content: center;
}

.lf-btn-sm {
  min-height: 26px; padding: 2px 10px; font-size: 12px; border-radius: 6px;
}

.lf-btn-primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 4px 10px -4px rgba(99,102,241,0.4);
}
.lf-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 6px 14px -4px rgba(99,102,241,0.55);
}

.lf-btn-secondary {
  background: linear-gradient(135deg, rgba(20,184,166,0.1), rgba(13,148,136,0.1));
  color: #0d9488; border-color: rgba(20,184,166,0.2);
}
.lf-btn-secondary:hover:not(:disabled) {
  background: linear-gradient(135deg, #14b8a6, #0d9488);
  color: #fff; box-shadow: 0 4px 10px -4px rgba(20,184,166,0.5);
}

.lf-btn-info {
  background: linear-gradient(135deg, rgba(6,182,212,0.1), rgba(14,165,233,0.1));
  color: #0891b2; border-color: rgba(6,182,212,0.2);
}
.lf-btn-info:hover:not(:disabled) {
  background: linear-gradient(135deg, #06b6d4, #0ea5e9);
  color: #fff; box-shadow: 0 4px 10px -4px rgba(6,182,212,0.5);
}

.lf-btn-danger {
  background: linear-gradient(135deg, rgba(220,38,38,0.1), rgba(225,29,72,0.1));
  color: #dc2626; border-color: rgba(220,38,38,0.2);
}
.lf-btn-danger:hover:not(:disabled) {
  background: linear-gradient(135deg, #dc2626, #e11d48);
  color: #fff; box-shadow: 0 4px 10px -4px rgba(220,38,38,0.5);
}

.lf-btn-ghost {
  width: 32px; min-height: 32px; padding: 0; justify-content: center;
  background: rgba(255,255,255,0.18);
  color: #fff; border-color: rgba(255,255,255,0.3);
}
.lf-btn-ghost:hover { background: rgba(255,255,255,0.3); }

/* ============================================================
   Page layout
   ============================================================ */
.license-page {
  max-width: 100%;
  margin: 0;
  padding: 24px 32px 40px;
  display: grid;
  gap: 16px;
  box-sizing: border-box;
  width: 100%;
  background: var(--md-bg-page, #f5f7f8);
  color: var(--md-text, #1d2a35);
  font-family: var(--md-font, "Inter", "Microsoft YaHei", "PingFang SC", system-ui, sans-serif);
  font-size: 14px;
  line-height: 1.5;
}

.license-tabs {
  display: flex;
  gap: 4px;
  border: 1px solid #dce4e8;
  border-radius: 8px;
  padding: 6px;
  background: #ffffff;
  margin-bottom: 0;
  box-shadow: 0 1px 2px rgba(15,42,58,0.04);
}

.license-tab {
  border: none;
  background: transparent;
  padding: 0 16px;
  min-height: 34px;
  font-size: 14px;
  color: #475569;
  cursor: pointer;
  border-radius: 6px;
  font-weight: 500;
  transition: color 0.18s ease, background 0.18s ease;
}
/* 页签高亮使用系统主题色，不再使用绿色 */
.license-tab:hover {
  color: var(--primary, #1677ff);
  background: color-mix(in srgb, var(--primary, #1677ff) 10%, transparent);
}
.license-tab.active {
  color: var(--primary, #1677ff);
  background: color-mix(in srgb, var(--primary, #1677ff) 12%, transparent);
  font-weight: 700;
}

.license-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 0;
  background: #ffffff;
  border: 1px solid #dce4e8;
  border-radius: 8px;
  padding: 12px 16px;
  align-items: center;
  flex-wrap: wrap;
}

/* 搜索区：与统一列表工具栏保持一致的输入框样式 */
.license-search {
  display: flex;
  gap: 8px;
  align-items: center;
  flex: 1;
  max-width: 520px;
}
.license-search input {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  font-size: 14px;
}
.license-search input:focus {
  outline: none;
  border-color: var(--primary, #1677ff);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary, #1677ff) 15%, transparent);
}

.license-message { color: #047857; font-size: 13px; margin: 0 0 8px; }
.license-error { color: #b91c1c; font-size: 13px; margin: 0 0 8px; }

.license-pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  padding: 12px;
  font-size: 13px;
  color: #64748b;
}

/* ============================================================
   新增/编辑证照弹窗
   ============================================================ */
.license-form-dialog {
  width: min(1280px, calc(100vw - 32px)) !important;
  max-height: 92vh !important;
  display: flex !important;
  flex-direction: column;
  background: #ffffff;
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: 0 24px 60px -12px rgba(15,42,58,0.35);
  overflow: hidden;
}

.lf-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
}

.lf-dialog-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.lf-dialog-title p {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}

.lf-dialog-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
}

.lf-dialog-tag {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.lf-dialog-tag-renew {
  background: rgba(245,158,11,0.3);
  color: #fef3c7;
}
.lf-dialog-tag-edit {
  background: rgba(34,197,94,0.3);
  color: #dcfce7;
}

.lf-dialog-form {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.lf-dialog-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
  padding: 18px 24px 12px;
  background: #f5f8fa;
  border-bottom: 1px solid #e1e9ee;
  overflow-y: auto;
}

@media (max-width: 900px) {
  .lf-dialog-form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 600px) {
  .lf-dialog-form-grid { grid-template-columns: 1fr; }
}

.lf-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #566b77;
  font-weight: 500;
}

.lf-field.wide { grid-column: 1 / -1; }

.lf-field input,
.lf-field select,
.lf-field textarea {
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid #cfdfe7;
  border-radius: 6px;
  background: #ffffff;
  color: #172b3a;
  font-size: 13px;
  transition: border-color 0.18s, box-shadow 0.18s;
}
.lf-field input:focus,
.lf-field select:focus,
.lf-field textarea:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99,102,241,0.15);
}
.lf-field input[readonly] { background: #f0f3f5; color: #6b7c8f; cursor: not-allowed; }
.lf-field textarea { min-height: 60px; resize: vertical; }
.lf-field small { color: #94a3b8; font-size: 11px; }

.lf-remark { }

/* Owner picker */
.lf-owner-picker {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lf-owner-search {
  display: flex;
  gap: 8px;
  align-items: center;
}
.lf-owner-search input { flex: 1; min-width: 0; }

.lf-owner-options {
  display: grid;
  gap: 6px;
  max-height: 200px;
  overflow: auto;
  border: 1px solid #cfdfe7;
  border-radius: 8px;
  padding: 10px;
  background: #fff;
}

.lf-owner-option-btn {
  display: block;
  width: 100%;
  text-align: left;
  padding: 6px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font: inherit;
  font-size: 13px;
  color: #172b3a;
  cursor: pointer;
  transition: background 0.15s;
}
.lf-owner-option-btn:hover { background: #eef7f8; }

.lf-owner-pager {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 6px;
  border-top: 1px solid #eef2f5;
  font-size: 12px;
  color: #566b77;
}

.lf-owner-empty {
  color: #6b7c8f;
  font-size: 12.5px;
  margin: 0;
}

.lf-owner-selected { }

.lf-owner-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(34,197,94,0.12);
  color: #15803d;
}

.lf-owner-tag-warn {
  background: rgba(245,158,11,0.12);
  color: #b45309;
}

/* Upload section */
.lf-dialog-upload {
  display: grid;
  gap: 8px;
  padding: 14px 24px;
  background: #ffffff;
  border-bottom: 1px solid #e1e9ee;
}

.lf-upload-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.lf-upload-header > span {
  color: #475569;
  font-size: 13px;
  font-weight: 500;
}
.lf-upload-header > small { color: #94a3b8; font-size: 12px; }

.lf-upload-success {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #15803d;
  margin: 0;
}

.lf-file-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.lf-file-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f5f8fa;
  border-radius: 8px;
  border: 1px solid #eef2f5;
}

.lf-file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #172b3a;
}

.lf-file-size {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
  flex-shrink: 0;
}

.lf-upload-hint {
  color: #94a3b8;
  font-size: 12px;
}

.lf-dialog-message {
  padding: 0 24px;
  margin: 0;
}

.lf-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 24px 18px;
  background: #f5f8fa;
  border-top: 1px solid #e1e9ee;
}

/* ============================================================
   附件阅览弹窗
   ============================================================ */
.license-attachment-dialog {
  width: min(1100px, calc(100vw - 32px)) !important;
  max-height: 92vh !important;
  display: flex !important;
  flex-direction: column;
  background: #ffffff;
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: 0 24px 60px -12px rgba(15,42,58,0.35);
  overflow: hidden;
}

.la-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #0f766e, #0d9488);
  color: #ffffff;
}

.la-dialog-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.la-dialog-title p {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}
.la-dialog-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
}

.la-dialog-body {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 800px) {
  .la-dialog-body { grid-template-columns: 1fr; }
}

.la-attachment-list {
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e1e9ee;
  overflow: hidden;
  background: #f9fbfc;
}

.la-upload-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f5;
  background: #ffffff;
}
.la-upload-row span {
  color: #94a3b8;
  font-size: 12px;
}

.la-attachment-items {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  overflow-y: auto;
}

.la-attachment-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f5;
  background: #ffffff;
  transition: background 0.15s;
}
.la-attachment-item:hover { background: #f0f7f7; }

.la-attachment-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.la-attachment-info strong {
  font-size: 13px;
  color: #172b3a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.la-attachment-info span {
  font-size: 12px;
  color: #94a3b8;
}

.la-attachment-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.la-attachment-preview {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
}

.la-full-preview {
  width: 100%;
  height: 100%;
  min-height: 500px;
  border-radius: 0;
}

.la-attachment-preview img,
.la-attachment-preview iframe {
  max-width: 100%;
  max-height: 100%;
  width: 100%;
  height: 100%;
  border: none;
  object-fit: contain;
}

.la-preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #cbd5e1;
}
.la-preview-placeholder p {
  font-size: 14px;
  color: #94a3b8;
}

/* ============================================================
   证照历史弹窗
   ============================================================ */
.lh-dialog {
  width: min(1100px, calc(100vw - 32px)) !important;
  max-height: 92vh !important;
  display: flex !important;
  flex-direction: column;
  background: #ffffff;
  border: 0 !important;
  border-radius: 16px !important;
  box-shadow: 0 24px 60px -12px rgba(15,42,58,0.35);
  overflow: hidden;
}

.lh-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #475569, #334155);
  color: #ffffff;
}

.lh-dialog-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.lh-dialog-title p {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  opacity: 0.85;
}
.lh-dialog-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
}

.lh-dialog-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.lh-revision-item {
  border: 1px solid #e1e9ee;
  border-radius: 12px;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(15,42,58,0.06);
}

.lh-revision-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 16px;
  background: linear-gradient(135deg, #f8fafc, #eef2f7);
  border-bottom: 1px solid #e1e9ee;
}

.lh-revision-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(99,102,241,0.15), rgba(139,92,246,0.15));
  color: #4f46e5;
  font-size: 12px;
  font-weight: 700;
}

.lh-revision-type {
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(245,158,11,0.12);
  color: #b45309;
  font-size: 12px;
  font-weight: 600;
}

.lh-revision-op,
.lh-revision-time {
  font-size: 12px;
  color: #6b7c8f;
}

.lh-revision-detail {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 8px 16px;
  padding: 14px 16px;
}

.lh-rev-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.lh-rev-label {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.lh-rev-value {
  font-size: 13px;
  color: #172b3a;
  font-weight: 500;
}

.lh-rev-files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.lh-rev-no-files {
  color: #94a3b8;
  font-size: 12px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(34,197,94,0.12);
  color: #15803d;
  border: 1px solid rgba(34,197,94,0.3);
}
.status-badge.warning {
  background: rgba(245,158,11,0.12);
  color: #b45309;
  border-color: rgba(245,158,11,0.3);
}

/* Legacy overrides */
.license-owner-search { display: flex; gap: 8px; }
.license-owner-search input { flex: 1; min-width: 0; }
.license-owner-options { display: grid; gap: 6px; max-height: 230px; overflow: auto; border: 1px solid #dce4e8; padding: 10px; }
.license-owner-options > button { text-align: left; }
</style>
