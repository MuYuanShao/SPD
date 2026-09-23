<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Eye, FileText, Plus, Search, Trash2, Upload, X, Pencil, Download } from '@lucide/vue'
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

const rows = ref<LicenseRow[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const loading = ref(false)
const message = ref('')
const error = ref('')

const showFormModal = ref(false)
const editId = ref<number | null>(null)
const form = ref<LicensePayload>(emptyForm())
const saving = ref(false)
const formLoading = ref(false)
const renewalMode = ref(false)
const previousExpiry = ref('')
const ownerKeyword = ref('')
const ownerOptions = ref<LicenseOwnerOption[]>([])
const ownerLoading = ref(false)
const ownerPage = ref(1)
const ownerTotal = ref(0)
const historyOpen = ref(false)
const historyLoading = ref(false)
const revisions = ref<Array<LicenseRevision & { snapshot: Record<string, any> }>>([])
const historyTitle = ref('')
const historyFileOpen = ref(false)
const ownerSearched = ref(false)

async function searchOwners(pageNumber = 1) {
  ownerLoading.value = true
  message.value = ''
  try {
    const data = await fetchLicenseOwnerOptions(form.value.ownerType || activeTab.value, ownerKeyword.value, pageNumber)
    ownerSearched.value = true
    ownerOptions.value = data.rows
    ownerPage.value = pageNumber
    ownerTotal.value = data.total
  } catch (err) { message.value = err instanceof Error ? err.message : '主体搜索失败' }
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
  historyOpen.value = true; historyLoading.value = true; revisions.value = []; historyTitle.value = row.licenseName
  try {
    const data = await fetchLicenseHistory(row.id)
    revisions.value = data.history.map(item => ({ ...item, snapshot: JSON.parse(item.snapshotJson) }))
  } catch (err) { message.value = err instanceof Error ? err.message : '历史记录加载失败' }
  finally { historyLoading.value = false }
}
function operationText(value: string) {
  return ({ create: '新增', update: '编辑', renew: '续证', attachment: '附件上传', baseline: '历史原版本', delete: '删除' } as Record<string, string>)[value] || value
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
  message.value = ''
  loadList()
}

function onSearch() {
  page.value = 1
  loadList()
}

function openCreateModal() {
  renewalMode.value = false
  ownerKeyword.value = ''; ownerOptions.value = []; ownerSearched.value = false
  editId.value = null
  form.value = emptyForm()
  form.value.licenseType = activeTab.value
  message.value = ''
  showFormModal.value = true
}

async function openEditModal(row: LicenseRow, renewal = false) {
  if (formLoading.value) return
  formLoading.value = true; message.value = ''
  try {
    const detail = await fetchLicenseDetail(row.id)
    editId.value = row.id
    renewalMode.value = renewal
    previousExpiry.value = detail.expireDate || ''
    form.value = { ...detail, ownerType: detail.ownerType || (detail.licenseType === 'contract' ? 'supplier' : detail.licenseType),
      ownerId: detail.ownerId ?? null, revisionNo: detail.revisionNo,
      status: renewal ? 1 : detail.status, expireDate: renewal ? '' : detail.expireDate }
    ownerKeyword.value = detail.ownerName ? `${detail.ownerName}（${detail.ownerCode || ''}）` : ''
    ownerOptions.value = []; ownerSearched.value = false
    showFormModal.value = true
  } catch (err) { message.value = err instanceof Error ? err.message : '证照详情加载失败' }
  finally { formLoading.value = false }
}

async function submitForm() {
  if (!form.value.licenseName.trim()) {
    message.value = '证照名称为必填项'
    return
  }
  if (!form.value.ownerId) { message.value = '请搜索并选择证照所属主体'; return }
  saving.value = true
  message.value = ''
  try {
    if (editId.value === null) {
      await createLicense(form.value)
      message.value = '证照已新增'
    } else if (renewalMode.value) {
      await renewLicense(editId.value, form.value)
      message.value = '续证已保存，到期联动按最新有效期自动恢复'
    } else {
      await updateLicense(editId.value, form.value)
      message.value = '证照已保存'
    }
    showFormModal.value = false
    await loadList()
  } catch (err) {
    message.value = err instanceof Error ? err.message : '证照保存失败'
  } finally {
    saving.value = false
  }
}

async function confirmRemove(row: LicenseRow) {
  if (!window.confirm(`确认删除证照「${row.licenseName}」？删除后其附件一并失效。`)) return
  try {
    await removeLicense(row.id)
    message.value = '证照已删除'
    await loadList()
  } catch (err) {
    message.value = err instanceof Error ? err.message : '证照删除失败'
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
    message.value = err instanceof Error ? err.message : '附件加载失败'
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
    message.value = err instanceof Error ? err.message : '附件上传失败'
  }
}

async function previewAttachment(attachment: LicenseAttachment) {
  previewing.value = true
  try {
    const { blob, contentType } = await fetchLicenseAttachmentBlob(attachment.id)
    if (previewBlobUrl.value) URL.revokeObjectURL(previewBlobUrl.value)
    previewBlobUrl.value = URL.createObjectURL(blob)
    previewContentType.value = contentType
    previewName.value = attachment.fileName
  } catch (err) {
    message.value = err instanceof Error ? err.message : '附件预览失败'
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
    message.value = err instanceof Error ? err.message : '附件下载失败'
  }
}

const isContract = computed(() => activeTab.value === 'contract')

onMounted(loadList)
onUnmounted(closePreview)
</script>

<template>
  <div class="license-page">
    <header class="license-header">
      <div>
        <p>主数据</p>
        <h2>证照管理</h2>
        <span>集中管理商品、供应商、厂家的证照与合同，支持附件上传与阅览。</span>
      </div>
    </header>

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

    <div class="license-toolbar">
      <div class="license-search">
        <input
          v-model.trim="keyword"
          :placeholder="`搜索证照名称、编号、${activeTabInfo.ownerLabel}名称或编码`"
          @keyup.enter="onSearch"
        />
        <button class="btn" type="button" @click="onSearch"><Search :size="16" /> 查询</button>
      </div>
      <button class="btn btn-primary" type="button" @click="openCreateModal">
        <Plus :size="16" /> 新增{{ activeTabInfo.label }}
      </button>
    </div>

    <p v-if="message" class="license-message">{{ message }}</p>
    <p v-if="error" class="license-error">{{ error }}</p>

    <div class="license-table-wrap">
      <table class="license-table">
        <thead>
          <tr>
            <th>证照名称</th>
            <th>证照编号</th>
            <th>{{ isContract ? '合同双方' : activeTabInfo.ownerLabel }}</th>
            <th>{{ isContract ? '合同金额' : '签发日期' }}</th>
            <th>有效期至</th>
            <th>状态</th>
            <th>附件</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="approval-empty">正在加载{{ activeTabInfo.label }}...</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="8" class="approval-empty">暂无{{ activeTabInfo.label }}数据，点击右上角新增</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td>{{ row.licenseName }}</td>
            <td>{{ row.licenseNo || '-' }}</td>
            <td v-if="isContract">{{ [row.partyA, row.partyB].filter(Boolean).join(' / ') || '-' }}<small>所属：{{ row.ownerName || '未关联' }} / {{ row.ownerCode || '-' }}</small></td>
            <td v-else>{{ [row.ownerName, row.ownerCode].filter(Boolean).join(' / ') || '-' }}</td>
            <td v-if="isContract">{{ row.contractAmount != null ? `¥${row.contractAmount}` : '-' }}</td>
            <td v-else>{{ row.issueDate || '-' }}</td>
            <td>{{ row.expireDate || '-' }}</td>
            <td>
              <span :class="['license-status', statusText(row) === '有效' ? 'ok' : 'warn']">
                {{ statusText(row) }}
              </span>
            </td>
            <td>{{ row.attachmentCount ?? 0 }}</td>
            <td class="license-actions">
              <button class="btn-text" type="button" :disabled="formLoading" @click="openEditModal(row)"><Pencil :size="14" /> 编辑</button>
              <button class="btn-text" type="button" :disabled="formLoading" @click="openEditModal(row, true)">续证</button>
              <button class="btn-text" type="button" @click="openHistory(row)">历史</button>
              <button class="btn-text" type="button" @click="openAttachments(row)"><FileText :size="14" /> 附件</button>
              <button class="btn-text btn-text-danger" type="button" @click="confirmRemove(row)"><Trash2 :size="14" /> 删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="total > size" class="license-pagination">
        <button class="btn" type="button" :disabled="page <= 1" @click="page--; loadList()">上一页</button>
        <span>第 {{ page }} 页 / 共 {{ Math.ceil(total / size) }} 页（{{ total }} 条）</span>
        <button class="btn" type="button" :disabled="page >= Math.ceil(total / size)" @click="page++; loadList()">下一页</button>
      </div>
    </div>

    <!-- 新增 / 编辑证照 -->
    <div v-if="showFormModal" class="attachment-preview-mask" @click.self="showFormModal = false">
      <section class="supplier-dialog product-dialog license-form-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>{{ activeTabInfo.label }}</p>
            <h3>{{ renewalMode ? '续证' : editId === null ? '新增' : '编辑' }}{{ activeTabInfo.label }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="showFormModal = false"><X :size="18" /></button>
        </header>
        <form @submit.prevent="submitForm">
          <div class="supplier-form-grid compact">
            <label class="wide"><span>证照名称</span><input v-model.trim="form.licenseName" required /></label>
            <label><span>{{ isContract ? '合同编号' : '证照编号' }}</span><input v-model.trim="form.licenseNo" /></label>
            <label v-if="isContract"><span>所属主体类型</span><select v-model="form.ownerType" :disabled="renewalMode && Boolean(form.ownerId)" @change="resetOwner"><option value="supplier">供应商</option><option value="manufacturer">厂家</option><option value="product">商品</option></select></label>
            <div class="wide license-owner-picker">
              <label><span>所属主体（搜索名称或编码后选择）</span><div class="license-owner-search"><input v-model="ownerKeyword" placeholder="名称或编码" @input="onOwnerInput" :disabled="renewalMode && Boolean(form.ownerId)" @keydown.enter.prevent="searchOwners()" /><button class="btn" type="button" :disabled="ownerLoading || (renewalMode && Boolean(form.ownerId))" @click="searchOwners()">搜索主体</button></div></label>
              <div v-if="ownerOptions.length" class="license-owner-options">
                <button v-for="owner in ownerOptions" :key="owner.id" type="button" class="btn-text" @click="selectOwner(owner)">{{ owner.name }}（{{ owner.code }}）</button>
                <div><button class="btn" type="button" :disabled="ownerPage <= 1 || ownerLoading" @click="searchOwners(ownerPage - 1)">上一页</button><span> {{ ownerPage }} / {{ Math.ceil(ownerTotal / 10) }} </span><button class="btn" type="button" :disabled="ownerPage * 10 >= ownerTotal || ownerLoading" @click="searchOwners(ownerPage + 1)">下一页</button></div>
              </div>
              <p v-if="ownerSearched && !ownerLoading && ownerTotal === 0">没有匹配的有效主体</p>
              <small v-if="form.ownerId">已关联：{{ form.ownerName }} / {{ form.ownerCode }}</small><small v-else>未关联有效主体，请选择后保存</small>
            </div>
            <template v-if="!isContract"><label><span>主体编码</span><input :value="form.ownerCode" readonly /></label><label><span>签发日期</span><input v-model="form.issueDate" type="date" /></label></template>
            <template v-else>
              <label><span>甲方</span><input v-model.trim="form.partyA" placeholder="医院/甲方名称" /></label>
              <label><span>乙方</span><input v-model.trim="form.partyB" placeholder="供应商/乙方名称" /></label>
              <label><span>合同金额</span><input v-model.number="form.contractAmount" type="number" step="0.01" min="0" /></label>
              <label><span>签订日期</span><input v-model="form.issueDate" type="date" /></label>
            </template>
            <label><span>有效期至</span><input v-model="form.expireDate" type="date" :required="renewalMode" /><small v-if="renewalMode">原有效期：{{ previousExpiry || '未维护' }}；续证须延长有效期</small></label>
            <label>
              <span>状态</span>
              <select v-model.number="form.status">
                <option :value="1">有效</option>
                <option :value="0">失效</option>
              </select>
            </label>
            <label class="wide"><span>备注</span><textarea v-model.trim="form.remark" rows="2" /></label>
          </div>
          <p v-if="message" class="license-message" role="alert">{{ message }}</p>
          <div class="dialog-actions">
            <button class="btn" type="button" :disabled="saving" @click="showFormModal = false">取消</button>
            <button class="btn btn-primary" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
          </div>
        </form>
      </section>
    </div>

    <el-dialog destroy-on-close v-model="historyOpen" :title="`证照历史：${historyTitle}`" width="min(1100px, 94vw)" append-to-body>
      <p v-if="historyLoading">正在加载历史...</p>
      <p v-else-if="!revisions.length">该历史证照尚未产生版本记录；首次编辑或续证时会保留原版本。</p>
      <article v-for="revision in revisions" :key="revision.revisionNo" class="license-history-item">
        <h4>版本 {{ revision.revisionNo }} · {{ operationText(revision.operationType) }} · {{ revision.operatorName }} · {{ revision.createTime }}</h4>
        <p>{{ revision.snapshot.licenseName }} / {{ revision.snapshot.licenseNo || '-' }}</p>
        <p>主体：{{ revision.snapshot.ownerName || '-' }}（{{ revision.snapshot.ownerCode || '-' }}） · 签发：{{ revision.snapshot.issueDate || '-' }} · 到期：{{ revision.snapshot.expireDate || '-' }} · {{ revision.snapshot.status === 1 ? '有效' : '失效' }}</p>
        <p>备注：{{ revision.snapshot.remark || '-' }}</p>
        <div><span>当时附件：</span><button v-for="file in revision.snapshot.attachments || []" :key="file.id" class="btn-text" @click="historyFileOpen = true; closePreview(); previewAttachment(file)">{{ file.fileName }}</button><span v-if="!revision.snapshot.attachments?.length">无</span></div>
      </article>
    </el-dialog>

    <el-dialog destroy-on-close v-model="historyFileOpen" :title="previewName || '历史附件阅览'" width="min(1000px, 94vw)" append-to-body @closed="closePreview">
      <p v-if="previewing">正在加载附件...</p>
      <img v-else-if="previewBlobUrl && previewContentType.startsWith('image/')" :src="previewBlobUrl" :alt="previewName" style="max-width:100%;max-height:65vh;object-fit:contain" />
      <iframe v-else-if="previewBlobUrl && previewContentType.includes('pdf')" :src="previewBlobUrl" :title="previewName" style="width:100%;height:65vh;border:0" />
      <p v-else>{{ message || '该格式请从附件列表下载查看' }}</p>
    </el-dialog>

    <!-- 附件阅览 -->
    <div v-if="showAttachmentModal" class="attachment-preview-mask" @click.self="closePreview(); showAttachmentModal = false">
      <section class="supplier-dialog product-dialog license-attachment-dialog" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>附件阅览</p>
            <h3>{{ attachmentLicense?.licenseName }}</h3>
          </div>
          <button class="btn-icon" type="button" aria-label="关闭" @click="closePreview(); showAttachmentModal = false"><X :size="18" /></button>
        </header>
        <div class="license-attachment-body">
          <div class="license-attachment-list">
            <div class="license-upload-row">
              <label class="btn">
                <Upload :size="15" /> 上传附件
                <input type="file" hidden accept=".pdf,.png,.jpg,.jpeg,.gif,.webp" @change="handleUpload" />
              </label>
              <span>支持 PDF 与图片文件</span>
            </div>
            <p v-if="attachmentsLoading" class="approval-empty">正在加载附件...</p>
            <p v-else-if="!attachments.length" class="approval-empty">暂无附件，请上传证照扫描件</p>
            <ul v-else class="license-attachment-items">
              <li v-for="item in attachments" :key="item.id">
                <div class="license-attachment-info">
                  <strong>{{ item.fileName }}</strong>
                  <span>{{ item.createTime }} · {{ (item.size / 1024).toFixed(1) }} KB</span>
                </div>
                <div class="license-attachment-actions">
                  <button class="btn-text" type="button" :disabled="previewing" @click="previewAttachment(item)">
                    <Eye :size="14" /> 阅览
                  </button>
                  <button class="btn-text" type="button" @click="downloadAttachment(item)"><Download :size="14" /> 下载</button>
                </div>
              </li>
            </ul>
          </div>
          <div class="license-attachment-preview">
            <template v-if="previewBlobUrl">
              <img v-if="previewContentType.startsWith('image/')" :src="previewBlobUrl" :alt="previewName" />
              <iframe v-else-if="previewContentType.includes('pdf')" :src="previewBlobUrl" :title="previewName" />
              <p v-else class="approval-empty">该文件类型不支持在线预览，请下载后查看：{{ previewName }}</p>
            </template>
            <p v-else class="approval-empty">点击左侧「阅览」查看证照附件</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.license-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px;
}
.license-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 16px;
}
.license-header p {
  margin: 0 0 4px;
  color: #64748b;
  font-size: 13px;
}
.license-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
  color: #0f172a;
}
.license-header span {
  color: #64748b;
  font-size: 13px;
}
.license-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid #e2e8f0;
  margin-bottom: 16px;
}
.license-tab {
  border: none;
  background: transparent;
  padding: 10px 18px;
  font-size: 14px;
  color: #475569;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.license-tab.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
  font-weight: 600;
}
.license-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.license-search {
  display: flex;
  gap: 8px;
  flex: 1;
  max-width: 520px;
}
.license-search input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font-size: 14px;
}
.license-message {
  color: #047857;
  font-size: 13px;
  margin: 0 0 8px;
}
.license-error {
  color: #b91c1c;
  font-size: 13px;
  margin: 0 0 8px;
}
.license-table-wrap {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}
.license-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.license-table th,
.license-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #eef2f7;
  text-align: left;
  white-space: nowrap;
}
.license-table th {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
}
.license-table tbody tr:hover {
  background: #f8fafc;
}
.license-status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.license-status.ok {
  color: #047857;
  background: #ecfdf5;
}
.license-status.warn {
  color: #b45309;
  background: #fffbeb;
}
.license-actions {
  display: flex;
  gap: 10px;
}
.license-pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  padding: 12px;
  font-size: 13px;
  color: #64748b;
}
.license-form-dialog {
  width: min(680px, 92vw);
}
.license-attachment-dialog {
  width: min(960px, 94vw);
}
.license-attachment-body {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  padding: 16px;
  min-height: 420px;
}
.license-upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.license-upload-row span {
  color: #64748b;
  font-size: 12px;
}
.license-attachment-items {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 380px;
  overflow: auto;
}
.license-attachment-items li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 10px 8px;
  border-bottom: 1px solid #eef2f7;
}
.license-attachment-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.license-attachment-info strong {
  font-size: 13px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.license-attachment-info span {
  font-size: 12px;
  color: #94a3b8;
}
.license-attachment-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.license-attachment-preview {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  min-height: 420px;
}
.license-attachment-preview img,
.license-attachment-preview iframe {
  max-width: 100%;
  max-height: 560px;
  width: 100%;
  height: 100%;
  border: none;
  object-fit: contain;
}
.license-owner-search { display: flex; gap: 8px; }
.license-owner-search input { flex: 1; min-width: 0; }
.license-owner-options { display: grid; gap: 6px; max-height: 230px; overflow: auto; border: 1px solid #dce4e8; padding: 10px; }
.license-owner-options > button { text-align: left; }
.license-history-item { padding: 12px 0; border-bottom: 1px solid #dce4e8; }
.license-history-item h4 { margin: 0 0 8px; }
</style>
