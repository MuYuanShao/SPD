<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { onBeforeRouteLeave } from 'vue-router'
import { ArrowLeft, CheckCircle2, FileText, PackageCheck, Save, ShieldCheck, X } from '@lucide/vue'
import {
  approvePendingProductApplication,
  fetchPendingProductApplicationDetail,
  resubmitPendingProductApplication,
  updatePendingProductApplication,
  type PendingProductApplicationDetail
} from '../../api/pendingProductApplications'

const route = useRoute()
const router = useRouter()
const detail = ref<PendingProductApplicationDetail | null>(null)
const loading = ref(false)
const error = ref('')
const previewFile = ref<{ name: string; type: string; status: string } | null>(null)
const opinion = ref('')
const actionMessage = ref('')
const actionError = ref('')
const resubmitModalOpen = ref(false)
const resubmitCleanSnapshot = ref('')
const resubmitForm = reactive({
  applicationType: '',
  productCode: '',
  productName: '',
  specModel: '',
  brand: '',
  manufacturerName: '',
  supplierName: '',
  unit: '',
  purchasePrice: 0,
  retailPrice: null as number | null,
  minPurchaseQty: 1,
  purchaseUnit: '',
  conversionRate: 1,
  udiCode: '',
  registrationNo: '',
  registrationExpireDate: '',
  productionLicenseNo: '',
  businessLicenseNo: '',
  volumeBased: false,
  centralizedProcurement: false,
  domestic: true,
  contractCode: '',
  firstCategory: '',
  secondCategory: '',
  thirdCategory: '',
  chargeable: true,
  tenderSubCode: '',
  qualificationAttachmentCount: 0,
  highValue: false,
  coldChain: false,
  quotaManaged: false,
  storageCondition: '',
  changeReason: ''
})

/* ── Edit mode state ── */
const isEditing = computed(() =>
  isPendingApprovalStatus(detail.value?.approvalStatus) && detail.value?.canApprove === true
)

const currentApprovalNode = computed(() =>
  detail.value?.timeline.find((node) => node.status === 'active')?.title ?? '审批'
)

const approveButtonText = computed(() => `${currentApprovalNode.value}通过`)

const editForm = reactive({
  applicationType: '',
  productCode: '',
  productName: '',
  specModel: '',
  brand: '',
  manufacturerName: '',
  supplierName: '',
  unit: '',
  purchasePrice: 0,
  retailPrice: null as number | null,
  minPurchaseQty: 1,
  purchaseUnit: '',
  conversionRate: 1,
  udiCode: '',
  registrationNo: '',
  registrationExpireDate: '',
  productionLicenseNo: '',
  businessLicenseNo: '',
  volumeBased: false,
  centralizedProcurement: false,
  domestic: true,
  contractCode: '',
  firstCategory: '',
  secondCategory: '',
  thirdCategory: '',
  chargeable: true,
  tenderSubCode: '',
  qualificationAttachmentCount: 0,
  highValue: false,
  coldChain: false,
  quotaManaged: false,
  storageCondition: ''
})

const cleanSnapshot = ref('')
const isDirty = computed(() => {
  if (!detail.value) return false
  return JSON.stringify(extractFormValues()) !== cleanSnapshot.value
})
const saving = ref(false)
const saveMessage = ref('')
const saveError = ref('')

function extractFormValues() {
  const { ...rest } = editForm
  return rest
}

function captureCleanSnapshot() {
  cleanSnapshot.value = JSON.stringify(extractFormValues())
}

function initEditForm() {
  if (!detail.value) return
  const d = detail.value
  editForm.applicationType = d.applicationType
  editForm.productCode = d.productCode
  editForm.productName = d.productName
  editForm.specModel = d.specModel
  editForm.brand = d.brand || ''
  editForm.manufacturerName = d.manufacturerName || ''
  editForm.supplierName = d.supplierName || ''
  editForm.unit = d.unit
  editForm.purchasePrice = Number(d.purchasePrice || 0)
  editForm.retailPrice = d.retailPrice ? Number(d.retailPrice) : null
  editForm.minPurchaseQty = Number(d.minPurchaseQty || 1)
  editForm.purchaseUnit = d.purchaseUnit || ''
  editForm.conversionRate = Number(d.conversionRate || 1)
  editForm.udiCode = d.udiCode || ''
  editForm.registrationNo = d.registrationNo || ''
  editForm.registrationExpireDate = d.registrationExpireDate === '-' ? '' : d.registrationExpireDate
  editForm.productionLicenseNo = d.productionLicenseNo || ''
  editForm.businessLicenseNo = d.businessLicenseNo || ''
  editForm.volumeBased = d.volumeBased
  editForm.centralizedProcurement = d.centralizedProcurement
  editForm.domestic = d.domestic
  editForm.contractCode = d.contractCode || ''
  editForm.firstCategory = d.firstCategory || ''
  editForm.secondCategory = d.secondCategory || ''
  editForm.thirdCategory = d.thirdCategory || ''
  editForm.chargeable = d.chargeable
  editForm.tenderSubCode = d.tenderSubCode || ''
  editForm.qualificationAttachmentCount = d.qualificationAttachmentCount
  editForm.highValue = d.highValue
  editForm.coldChain = d.coldChain
  editForm.quotaManaged = d.quotaManaged
  editForm.storageCondition = d.storageCondition || ''
  captureCleanSnapshot()
}

function buildPayload() {
  return { ...editForm }
}

async function saveChanges() {
  if (!detail.value || !isDirty.value) return
  saveError.value = ''
  saveMessage.value = ''
  saving.value = true
  try {
    await updatePendingProductApplication(detail.value.applicationNo, buildPayload())
    saveMessage.value = '修改已保存'
    captureCleanSnapshot()
    await loadDetail()
  } catch (err) {
    saveError.value = err instanceof Error ? err.message : '保存失败'
  } finally {
    saving.value = false
  }
}

const applicationNo = computed(() => String(route.params.applicationNo ?? ''))

function splitFields<T>(fields: T[], sizes: number[]) {
  const sections: T[][] = []
  let cursor = 0
  for (const size of sizes) {
    sections.push(fields.slice(cursor, cursor + size))
    cursor += size
  }
  if (cursor < fields.length) {
    sections.push(fields.slice(cursor))
  }
  return sections
}

/* ── New grouped info sections (3-column layout) ── */
const approvalInfoGroups = computed(() => {
  const item = detail.value
  if (!item) return []

  return [
    {
      title: '商品基础信息',
      icon: PackageCheck,
      iconClass: 'blue',
      fields: [
        { label: '商品名称', value: item.productName, key: 'productName', highlight: true, full: false },
        { label: '商品编码', value: item.productCode, key: 'productCode', highlight: false, full: false },
        { label: '规格型号', value: item.specModel, key: 'specModel', highlight: false, full: false },
        { label: '品牌', value: item.brand || '-', key: 'brand', highlight: false, full: false },
        { label: '单位', value: item.unit, key: 'unit', highlight: false, full: false },
        { label: '生产厂家', value: item.manufacturerName || '-', key: 'manufacturerName', highlight: false, full: false },
        { label: '供应商', value: item.supplierName || '-', key: 'supplierName', highlight: false, full: true },
      ]
    },
    {
      title: '价格与采购',
      icon: ShieldCheck,
      iconClass: 'orange',
      fields: [
        { label: '采购价', value: `¥ ${item.purchasePrice}`, key: 'purchasePrice', highlight: true, full: false, inputType: 'number' },
        { label: '零售价', value: item.retailPrice ? `¥ ${item.retailPrice}` : '-', key: 'retailPrice', highlight: false, full: false, inputType: 'number' },
        { label: '最小采购量', value: item.minPurchaseQty, key: 'minPurchaseQty', highlight: false, full: false },
        { label: '采购单位', value: item.purchaseUnit || '-', key: 'purchaseUnit', highlight: false, full: false },
        { label: '换算系数', value: item.conversionRate, key: 'conversionRate', highlight: false, full: false },
        { label: '合同编码', value: item.contractCode || '-', key: 'contractCode', highlight: false, full: false },
        { label: '招采子编码', value: item.tenderSubCode || '-', key: 'tenderSubCode', highlight: false, full: false },
      ]
    },
    {
      title: '业务属性',
      icon: FileText,
      iconClass: 'green',
      type: 'tags',
      booleans: [
        { label: '带量', value: item.volumeBased, field: 'volumeBased' },
        { label: '集采', value: item.centralizedProcurement, field: 'centralizedProcurement' },
        { label: '国产', value: item.domestic, field: 'domestic' },
        { label: '收费', value: item.chargeable, field: 'chargeable' },
        { label: '高值耗材', value: item.highValue, field: 'highValue' },
        { label: '冷链', value: item.coldChain, field: 'coldChain' },
        { label: '定数管理', value: item.quotaManaged, field: 'quotaManaged' },
      ],
      storage: item.storageCondition || '常温',
      categories: [item.firstCategory, item.secondCategory, item.thirdCategory].filter(Boolean).join(' > ') || '-'
    }
  ]
})

/* ── Qualification / certificate info ── */
const qualificationInfo = computed(() => {
  const item = detail.value
  if (!item) return null
  return [
    { icon: '📜', label: '注册证号', value: item.registrationNo || '-', key: 'registrationNo', extra: '' },
    { icon: '📅', label: '注册证有效期', value: item.registrationExpireDate === '-' ? '-' : (item.registrationExpireDate || '-'), key: 'registrationExpireDate', extra: '' },
    { icon: '🏭', label: '生产许可证号', value: item.productionLicenseNo || '-', key: 'productionLicenseNo', extra: '' },
    { icon: '📋', label: '经营许可证号', value: item.businessLicenseNo || '-', key: 'businessLicenseNo', extra: '' },
    { icon: '🔖', label: 'UDI 编码', value: item.udiCode || '-', key: 'udiCode', extra: '' },
  ]
})

const attachments = computed(() => {
  const item = detail.value
  if (!item) return []

  return Array.from({ length: item.qualificationAttachmentCount }, (_, index) => ({
    name: index === 0 ? '注册证附件.pdf' : `资质附件-${index + 1}.pdf`,
    type: index === 0 ? '注册证' : '资质材料',
    status: '可查看'
  }))
})

const changeItems = computed(() => detail.value?.changeItems ?? [])
const showChangeDiff = computed(() => detail.value?.applicationType === '信息变更')

async function loadDetail() {
  loading.value = true
  error.value = ''
  saveError.value = ''
  saveMessage.value = ''

  try {
    detail.value = await fetchPendingProductApplicationDetail(applicationNo.value)
    initEditForm()
    // Re-apply form values after DOM render to override Chrome autofill
    await nextTick()
    captureCleanSnapshot()
    openReturnedEditFromRoute()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '审批明细加载失败'
  } finally {
    loading.value = false
  }
}

function openReturnedEditFromRoute() {
  if (route.query.edit === '1' && detail.value?.approvalStatus === 'returned') {
    openResubmitModal()
  }
}

async function submitAction(action: 'approve' | 'return' | 'reject') {
  if (!detail.value) return
  actionError.value = ''
  actionMessage.value = ''
  if (!detail.value.canApprove) {
    actionError.value = '当前账号无此节点审批权限'
    return
  }
  if ((action === 'return' || action === 'reject') && !opinion.value.trim()) {
    actionError.value = action === 'return' ? '退回修改必须填写退回原因' : '驳回必须填写审批意见'
    return
  }
  try {
    // Save form data first if dirty
    if (isEditing.value && isDirty.value) {
      await updatePendingProductApplication(detail.value.applicationNo, buildPayload())
      captureCleanSnapshot()
    }
    const result = await approvePendingProductApplication(detail.value.applicationNo, action, opinion.value)
    actionMessage.value =
      action === 'approve'
        ? result.status === 'approved'
          ? '审批通过，已同步到医院目录'
          : `${currentApprovalNode.value}通过，已进入下一审批节点`
        : action === 'return'
          ? '已退回修改，申请人可在"我发起的"中修改后重新提交'
          : '已驳回'
    opinion.value = ''
    await loadDetail()
  } catch (err) {
    actionError.value = err instanceof Error ? err.message : '审批操作失败'
  }
}

function isPendingApprovalStatus(status?: string) {
  return Boolean(status && (status === 'pending_initial' || status === 'pending_final' || status.startsWith('pending_step_')))
}

function openResubmitModal() {
  if (!detail.value) return
  Object.assign(resubmitForm, {
    applicationType: detail.value.applicationType,
    productCode: detail.value.productCode,
    productName: detail.value.productName,
    specModel: detail.value.specModel,
    brand: detail.value.brand || '',
    manufacturerName: detail.value.manufacturerName || '',
    supplierName: detail.value.supplierName || '',
    unit: detail.value.unit,
    purchasePrice: Number(detail.value.purchasePrice || 0),
    retailPrice: detail.value.retailPrice ? Number(detail.value.retailPrice) : null,
    minPurchaseQty: Number(detail.value.minPurchaseQty || 1),
    purchaseUnit: detail.value.purchaseUnit || '',
    conversionRate: Number(detail.value.conversionRate || 1),
    udiCode: detail.value.udiCode || '',
    registrationNo: detail.value.registrationNo || '',
    registrationExpireDate: detail.value.registrationExpireDate === '-' ? '' : detail.value.registrationExpireDate,
    productionLicenseNo: detail.value.productionLicenseNo || '',
    businessLicenseNo: detail.value.businessLicenseNo || '',
    volumeBased: detail.value.volumeBased,
    centralizedProcurement: detail.value.centralizedProcurement,
    domestic: detail.value.domestic,
    contractCode: detail.value.contractCode || '',
    firstCategory: detail.value.firstCategory || '',
    secondCategory: detail.value.secondCategory || '',
    thirdCategory: detail.value.thirdCategory || '',
    chargeable: detail.value.chargeable,
    tenderSubCode: detail.value.tenderSubCode || '',
    qualificationAttachmentCount: detail.value.qualificationAttachmentCount,
    highValue: detail.value.highValue,
    coldChain: detail.value.coldChain,
    quotaManaged: detail.value.quotaManaged,
    storageCondition: detail.value.storageCondition || '',
    changeReason: ''
  })
  captureResubmitSnapshot()
  resubmitModalOpen.value = true
}

function captureResubmitSnapshot() {
  resubmitCleanSnapshot.value = JSON.stringify(resubmitForm)
}

const isResubmitDirty = computed(() =>
  resubmitModalOpen.value && resubmitCleanSnapshot.value !== JSON.stringify(resubmitForm)
)

function closeResubmitModal() {
  if (isResubmitDirty.value && !window.confirm('当前修改尚未重新提交，确定关闭吗？')) {
    return
  }
  resubmitModalOpen.value = false
}

async function submitResubmitForm() {
  if (!detail.value) return
  actionError.value = ''
  if (resubmitForm.quotaManaged && (resubmitForm.highValue || resubmitForm.coldChain)) {
    actionError.value = '高值耗材或冷链耗材不能设置为定数管理'
    return
  }
  try {
    await resubmitPendingProductApplication(detail.value.applicationNo, resubmitForm)
    actionMessage.value = '已重新提交，审批单回到初审待审批'
    resubmitModalOpen.value = false
    captureResubmitSnapshot()
    await loadDetail()
  } catch (err) {
    actionError.value = err instanceof Error ? err.message : '重新提交失败'
  }
}

onBeforeRouteLeave((to, from, next) => {
  if (isDirty.value) {
    const answer = window.confirm('有未保存的修改，确定要离开吗？')
    if (!answer) return next(false)
  }
  next()
})

onMounted(() => {
  loadDetail()
  window.addEventListener('beforeunload', handleBeforeUnload)
})
onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

function handleBeforeUnload(e: BeforeUnloadEvent) {
  if (isDirty.value) {
    e.preventDefault()
  }
}
</script>

<template>
  <section class="approval-detail-page">
    <button class="back-button" type="button" @click="router.back()">
      <ArrowLeft :size="17" />
      返回待审批目录
    </button>

    <p v-if="loading" class="approval-empty">正在加载审批明细...</p>
    <p v-else-if="error" class="approval-empty">{{ error }}</p>

    <template v-else-if="detail">
      <!-- ═══ Header（合并审批流程） ═══ -->
      <header class="approval-detail-header">
        <div class="approval-header-top-row">
          <div>
            <span class="approval-type-badge">{{ detail.applicationType }}</span>
            <h2>{{ detail.productName }}</h2>
          </div>
          <div class="approval-actions">
            <button v-if="detail.approvalStatus === 'returned'" type="button" class="btn btn-warning" @click="openResubmitModal">
              修改后重新提交
            </button>
            <button
              v-if="isPendingApprovalStatus(detail.approvalStatus)"
              type="button"
              class="btn btn-danger"
              @click="submitAction('reject')"
            >
              驳回
            </button>
            <button
              v-if="isPendingApprovalStatus(detail.approvalStatus)"
              type="button"
              class="btn btn-warning"
              @click="submitAction('return')"
            >
              退回修改
            </button>
            <button
              v-if="isEditing && isDirty"
              type="button"
              class="btn btn-primary save-btn"
              @click="saveChanges"
              :disabled="saving"
            >
              <Save :size="17" />
              {{ saving ? '保存中...' : '保存修改' }}
            </button>
            <button
              v-if="isPendingApprovalStatus(detail.approvalStatus)"
              type="button"
              class="btn btn-primary"
              @click="submitAction('approve')"
            >
              <CheckCircle2 :size="17" />
              {{ approveButtonText }}
            </button>
          </div>
        </div>
        <div class="approval-header-meta">
          <span class="status-pill">{{ detail.statusLabel }}</span>
          <span class="sep">|</span>
          <span>{{ detail.applicationNo }}</span>
          <span class="sep">·</span>
          <span>{{ detail.applicant }}</span>
          <span class="sep">·</span>
          <span>{{ detail.submitTime }}</span>
        </div>
        <div class="approval-header-divider"></div>
        <div class="timeline-row">
          <div
            v-for="node in detail.timeline"
            :key="node.title"
            class="timeline-node"
            :class="node.status"
          >
            <div class="dot">{{ node.status === 'done' ? '✓' : node.status === 'active' ? '●' : '○' }}</div>
            <div class="connector"></div>
            <div class="node-info">
              <div class="node-title">{{ node.title }}</div>
              <div class="node-sub">{{ node.operator }} · {{ node.time }}</div>
            </div>
          </div>
        </div>
      </header>

      <p v-if="saveMessage" class="inline-message" style="background:#f0fdf4;border-color:#86efac;color:#166534;">{{ saveMessage }}</p>
      <p v-if="saveError" class="inline-message error">{{ saveError }}</p>
      <p v-if="actionMessage" class="inline-message">{{ actionMessage }}</p>
      <p v-if="actionError" class="inline-message error">{{ actionError }}</p>
      <p v-if="detail.returnReason" class="inline-message" style="background:#fff7ed;border-color:#fed7aa;color:#c2410c;">退回原因：{{ detail.returnReason }}</p>
      <p v-if="detail.rejectReason" class="inline-message error">驳回原因：{{ detail.rejectReason }}</p>

      <!-- ═══ 3-Column Info Grid ═══ -->
      <div class="approval-info-grid">
        <div v-for="group in approvalInfoGroups" :key="group.title" class="approval-info-card">
          <div class="info-card-header">
            <span class="icn" :class="group.iconClass">
              <component :is="group.icon" :size="14" />
            </span>
            <h4>{{ group.title }}</h4>
          </div>

          <!-- Regular fields -->
          <template v-if="group.type !== 'tags'">
            <div class="approval-info-fields">
              <div
                v-for="f in group.fields"
                :key="f.label"
                class="field"
                :class="{ highlight: f.highlight, full: f.full }"
              >
                <span class="flabel">{{ f.label }}</span>
                <span v-if="!isEditing || !f.key" class="fvalue">{{ f.value }}</span>
                <input
                  v-else
                  v-model="(editForm as any)[f.key]"
                  class="edit-input"
                  autocomplete="off"
                  :type="f.inputType || 'text'"
                  :step="f.inputType === 'number' ? '0.0001' : undefined"
                />
              </div>
            </div>
          </template>

          <!-- Tag cloud for business attributes -->
          <template v-else>
            <div class="tag-cloud">
              <template v-if="!isEditing">
                <span
                  v-for="b in group.booleans"
                  :key="b.label"
                  class="tag-pill"
                  :class="b.value ? 'yes' : 'no'"
                >
                  {{ b.value ? '✓' : '✗' }} {{ b.label }}
                </span>
                <span class="tag-pill storage">🌡 {{ group.storage }}</span>
              </template>
              <template v-else>
                <label v-for="b in group.booleans" :key="b.label" class="edit-checkbox-label">
                  <input type="checkbox" v-model="(editForm as any)[b.field]" />
                  {{ b.label }}
                </label>
                <label class="edit-checkbox-label storage-label">
                  <span>储存条件</span>
                  <input v-model="editForm.storageCondition" class="edit-input" />
                </label>
              </template>
            </div>
            <div v-if="group.categories" class="approval-category-path">
              <div class="clabel">目录分类</div>
              <div v-if="!isEditing" class="cvalue">{{ group.categories }}</div>
              <div v-else class="category-edit-row">
                <input v-model="editForm.firstCategory" placeholder="一级分类" class="edit-input" />
                <input v-model="editForm.secondCategory" placeholder="二级分类" class="edit-input" />
                <input v-model="editForm.thirdCategory" placeholder="三级分类" class="edit-input" />
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- ═══ Qualification / Certificate Hero Card ═══ -->
      <div v-if="qualificationInfo" class="qualification-card">
        <div class="info-card-header">
          <span class="icn green">🛡️</span>
          <h4>资质证照信息</h4>
        </div>
        <div class="qual-grid">
          <div v-for="q in qualificationInfo" :key="q.label" class="qual-item">
            <span class="q-icon">{{ q.icon }}</span>
            <div class="q-body">
              <span class="qlabel">{{ q.label }}</span>
              <span v-if="!isEditing || !q.key" class="qvalue">{{ q.value }}</span>
              <input
                v-else
                v-model="(editForm as any)[q.key]"
                class="edit-input"
                :type="q.key === 'registrationExpireDate' ? 'date' : 'text'"
              />
              <span v-if="q.extra && !isEditing" class="qextra">{{ q.extra }}</span>
            </div>
          </div>
          <div v-if="attachments.length" class="qual-attachments">
            <span class="attach-label">📎 资质附件（{{ attachments.length }} 个文件）</span>
            <span
              v-for="file in attachments"
              :key="file.name"
              class="attach-badge"
              @click="previewFile = file"
            >
              <FileText :size="14" />
              {{ file.name }}
            </span>
          </div>
          <div v-else class="qual-attachments">
            <span class="attach-label">📎 资质附件</span>
            <span style="font-size:13px;color:#94a3b8;">暂无附件</span>
          </div>
        </div>
      </div>

      <!-- ═══ Change Diff (信息变更 only) ═══ -->
      <section v-if="showChangeDiff" class="change-diff-section">
        <div class="approval-info-title">
          <FileText :size="18" />
          <h4>字段变更对比</h4>
        </div>
        <div v-if="changeItems.length" class="change-diff-table-wrap">
          <table class="change-diff-table">
            <thead>
              <tr>
                <th>变更字段</th>
                <th>变更前</th>
                <th>变更后</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in changeItems" :key="item.fieldName">
                <td>{{ item.fieldName }}</td>
                <td>{{ item.beforeValue }}</td>
                <td>{{ item.afterValue }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="muted" style="margin:8px 0 0;color:#94a3b8;">未检测到与医院目录当前数据的差异。</p>
      </section>

      <!-- ═══ Approval Opinion ═══ -->
      <section class="approval-opinion-card">
        <div class="approval-section-title">
          <span class="swrap blue"><FileText :size="16" /></span>
          <h3>审批意见</h3>
        </div>
        <textarea
          v-model="opinion"
          :disabled="!detail.canApprove"
          placeholder="退回修改/驳回必须填写原因。审批通过可填写意见（选填）。"
        ></textarea>
        <p v-if="isPendingApprovalStatus(detail.approvalStatus) && !detail.canApprove" class="inline-message">
          当前账号无此节点审批权限，仅可查看审批明细。
        </p>
        <div class="opinion-actions">
          <button
            v-if="detail.canApprove"
            type="button"
            class="btn btn-danger"
            @click="submitAction('reject')"
          >
            驳回
          </button>
          <button
            v-if="detail.canApprove"
            type="button"
            class="btn btn-warning"
            @click="submitAction('return')"
          >
            退回修改
          </button>
          <button
            v-if="detail.canApprove"
            type="button"
            class="btn btn-primary"
            @click="submitAction('approve')"
          >
            <CheckCircle2 :size="17" />
            {{ approveButtonText }}
          </button>
        </div>
      </section>

      <!-- ═══ Resubmit Modal ═══ -->
      <div v-if="resubmitModalOpen" class="attachment-preview-mask">
        <section class="supplier-dialog product-dialog" role="dialog" aria-modal="true">
          <header>
            <div>
              <p>{{ detail.applicationNo }}</p>
              <h3>修改后重新提交</h3>
            </div>
            <button class="btn-icon" type="button" aria-label="关闭" @click="closeResubmitModal">
              <X :size="18" />
            </button>
          </header>
          <form class="supplier-form-grid" @submit.prevent="submitResubmitForm">
            <label><span>申请类型</span><input v-model="resubmitForm.applicationType" readonly /></label>
            <label><span>商品编码</span><input v-model.trim="resubmitForm.productCode" required /></label>
            <label><span>商品名称</span><input v-model.trim="resubmitForm.productName" required /></label>
            <label><span>规格型号</span><input v-model.trim="resubmitForm.specModel" required /></label>
            <label><span>生产厂家</span><input v-model.trim="resubmitForm.manufacturerName" /></label>
            <label><span>供应商</span><input v-model.trim="resubmitForm.supplierName" /></label>
            <label><span>单位</span><input v-model.trim="resubmitForm.unit" required /></label>
            <label><span>采购价</span><input v-model.number="resubmitForm.purchasePrice" type="number" min="0" step="0.0001" /></label>
            <label><span>注册证号</span><input v-model.trim="resubmitForm.registrationNo" /></label>
            <label><span>注册证有效期</span><input v-model="resubmitForm.registrationExpireDate" type="date" /></label>
            <label><span>一级分类</span><input v-model.trim="resubmitForm.firstCategory" /></label>
            <label><span>二级分类</span><input v-model.trim="resubmitForm.secondCategory" /></label>
            <label><span>三级分类</span><input v-model.trim="resubmitForm.thirdCategory" /></label>
            <label><span>附件数量</span><input v-model.number="resubmitForm.qualificationAttachmentCount" type="number" min="0" /></label>
            <label class="wide"><span>修改说明</span><textarea v-model="resubmitForm.changeReason" placeholder="选填，可填写本次修改说明" /></label>
            <div class="dialog-toggle-row">
              <label><input v-model="resubmitForm.volumeBased" type="checkbox" /> 是否带量</label>
              <label><input v-model="resubmitForm.domestic" type="checkbox" /> 是否国产</label>
              <label><input v-model="resubmitForm.chargeable" type="checkbox" /> 是否收费</label>
              <label><input v-model="resubmitForm.highValue" type="checkbox" /> 高值耗材</label>
              <label><input v-model="resubmitForm.coldChain" type="checkbox" /> 冷链</label>
              <label><input v-model="resubmitForm.quotaManaged" type="checkbox" /> 定数管理</label>
            </div>
            <div class="dialog-actions">
              <button class="btn" type="button" @click="closeResubmitModal">取消</button>
              <button class="btn btn-primary" type="submit">重新提交</button>
            </div>
          </form>
        </section>
      </div>

      <!-- ═══ Attachment Preview Modal ═══ -->
      <div v-if="previewFile" class="attachment-preview-mask" @click.self="previewFile = null">
        <section class="attachment-preview-dialog" role="dialog" aria-modal="true" aria-label="附件预览">
          <header>
            <div>
              <p>{{ previewFile.type }}</p>
              <h3>{{ previewFile.name }}</h3>
            </div>
            <button class="btn-icon" type="button" aria-label="关闭附件预览" @click="previewFile = null">
              <X :size="18" />
            </button>
          </header>
          <div class="attachment-preview-body">
            <FileText :size="42" />
            <strong>{{ previewFile.name }}</strong>
            <span>当前为附件预览占位。接入真实文件 URL 后，此处将显示 PDF / 图片内容。</span>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.edit-input {
  width: 100%;
  padding: 4px 8px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: #1e293b;
  background: #fff;
  transition: border-color .2s;
  box-sizing: border-box;
}
.edit-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59,130,246,.15);
}
.edit-input[type="number"] {
  text-align: right;
}

.edit-checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  cursor: pointer;
  user-select: none;
}
.edit-checkbox-label input[type="checkbox"] {
  margin: 0;
}
.edit-checkbox-label.storage-label {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}
.edit-checkbox-label.storage-label .edit-input {
  width: 120px;
  padding: 2px 6px;
  font-size: 12px;
}

.category-edit-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.category-edit-row .edit-input {
  flex: 1;
  min-width: 0;
}

.save-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.qual-item .edit-input {
  width: 100%;
  max-width: 200px;
}
</style>
