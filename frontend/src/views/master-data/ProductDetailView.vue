<script setup lang="ts">
import { computed, onActivated, onDeactivated, onUnmounted, ref, watch } from 'vue'
import { Teleport } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Eye, FileText, Pencil, X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import {
  fetchHospitalProductDetail,
  fetchProductAttachmentBlob,
  type ProductAttachment,
  type ProductDetail
} from '../../api/masterData'
import { useAuthStore } from '../../stores/auth'
import PageHeader from '../../components/common/PageHeader.vue'

interface DetailField {
  label: string
  value: string | number
  emphasis?: boolean
}

interface DetailSection {
  id: string
  title: string
  description: string
  fields: DetailField[]
}

const route = useRoute()
const auth = useAuthStore()
const router = useRouter()
const detail = ref<ProductDetail | null>(null)
const loading = ref(false)
const error = ref('')
const previewFile = ref<ProductAttachment | null>(null)
const previewUrl = ref('')
const previewType = ref('')
const previewLoading = ref(false)
const previewError = ref('')
let previewRequest = 0

let requestVersion = 0
let active = false

function returnToCatalog() {
  void router.push({ name: 'master-data-list', params: { code: 'hospital-product-catalog' }, query: { ...route.query } })
}

const productCode = computed(() => String(route.params.productCode ?? ''))

function displayValue(value: unknown) {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

function closeAttachment() {
  previewRequest++
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''; previewType.value = ''; previewError.value = ''; previewLoading.value = false; previewFile.value = null
}
async function openAttachment(file: ProductAttachment) {
  closeAttachment()
  const request = ++previewRequest
  previewFile.value = file
  if (!file.fileUrl) { previewError.value = '未配置预览文件'; return }
  previewLoading.value = true
  try {
    const blob = await fetchProductAttachmentBlob(file.fileUrl)
    if (request !== previewRequest) return
    previewType.value = blob.type.split(';')[0].toLowerCase()
    previewUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    if (request === previewRequest) previewError.value = error instanceof Error ? error.message : '附件读取失败'
  } finally { if (request === previewRequest) previewLoading.value = false }
}

function money(value: ProductDetail['purchasePrice'] | null) {
  if (value === null || value === undefined || value === '') return '-'
  const amount = Number(value)
  return Number.isFinite(amount) ? `¥ ${amount.toFixed(2)}` : '-'
}

function yesNo(value: boolean) {
  return value ? '是' : '否'
}

const detailSections = computed<DetailSection[]>(() => {
  const item = detail.value
  if (!item) return []

  return [
    {
      id: 'basic',
      title: '基础信息',
      description: '商品身份、分类及合作方信息',
      fields: [
        { label: '商品编码', value: displayValue(item.productCode), emphasis: true },
        { label: '商品名称', value: displayValue(item.productName), emphasis: true },
        { label: '规格型号', value: displayValue(item.specModel) },
        { label: '品牌', value: displayValue(item.brand) },
        { label: '商品分类', value: displayValue(item.categoryName) },
        { label: '基本单位', value: displayValue(item.unit) },
        { label: '生产厂家', value: displayValue(item.manufacturerName) },
        { label: '供应商', value: displayValue(item.supplierName) },
        { label: '一级分类', value: displayValue(item.firstCategory) },
        { label: '二级分类', value: displayValue(item.secondCategory) },
        { label: '三级分类', value: displayValue(item.thirdCategory) },
        { label: '状态', value: displayValue(item.statusLabel), emphasis: true }
      ]
    },
    {
      id: 'purchase',
      title: '采购与价格',
      description: '采购单位、价格及合同招采信息',
      fields: [
        { label: '采购价', value: money(item.purchasePrice), emphasis: true },
        { label: '零售价', value: money(item.retailPrice), emphasis: true },
        { label: '最小采购量', value: displayValue(item.minPurchaseQty) },
        { label: '采购单位', value: displayValue(item.purchaseUnit) },
        { label: '中包装数量', value: displayValue(item.conversionRate) },
        { label: '采购包装数量', value: displayValue(item.purchasePackageQty) },
        { label: '合同编码', value: displayValue(item.contractCode) },
        { label: '招采子编码', value: displayValue(item.tenderSubCode) },
        { label: '附件数量', value: item.attachments.length }
      ]
    },
    {
      id: 'qualification',
      title: '资质与注册',
      description: 'UDI、注册证及许可证信息',
      fields: [
        { label: 'UDI 编码', value: displayValue(item.udiCode), emphasis: true },
        { label: '注册证号', value: displayValue(item.registrationNo) },
        { label: '注册证有效期', value: displayValue(item.registrationExpireDate) },
        { label: '生产许可证号', value: displayValue(item.productionLicenseNo) },
        { label: '经营许可证号', value: displayValue(item.businessLicenseNo) }
      ]
    },
    {
      id: 'management',
      title: '管理属性',
      description: '收费、采购策略及储运管理标识',
      fields: [
        { label: '定数管理', value: yesNo(item.quotaManaged) },
        { label: '带量采购', value: yesNo(item.volumeBased) },
        { label: '集中采购', value: yesNo(item.centralizedProcurement) },
        { label: '国产产品', value: yesNo(item.domestic) },
        { label: '收费项目', value: yesNo(item.chargeable) },
        { label: '高值耗材', value: yesNo(item.highValue) },
        { label: '重点监控', value: yesNo(item.keyMonitored) },
        { label: '冷链管理', value: yesNo(item.coldChain) },
        { label: '储存条件', value: displayValue(item.storageCondition) }
      ]
    }
  ]
})

async function loadDetail() {
  if (!active || route.name !== 'hospital-product-detail') return
  const request = ++requestVersion
  const expectedCode = productCode.value
  closeAttachment()
  detail.value = null
  loading.value = true
  error.value = ''

  try {
    const result = await fetchHospitalProductDetail(expectedCode)
    if (active && request === requestVersion && productCode.value === expectedCode) detail.value = result
  } catch (err) {
    if (active && request === requestVersion) error.value = err instanceof Error ? err.message : '商品详情加载失败'
  } finally {
    if (request === requestVersion) loading.value = false
  }
}

onActivated(() => { active = true; void loadDetail() })
onDeactivated(() => { active = false; requestVersion++; loading.value = false; closeAttachment() })
onUnmounted(closeAttachment)
watch(productCode, () => { if (active) void loadDetail() })
</script>

<template>
  <main class="approval-detail-page product-detail-page">
    <button class="btn product-detail-back" type="button" @click="returnToCatalog">
      <ArrowLeft :size="16" aria-hidden="true" />
      返回医院目录
    </button>

    <p v-if="loading" class="approval-empty" role="status" aria-live="polite">
      正在加载商品详情...
    </p>
    <div v-else-if="error" class="product-detail-error" role="alert">
      <strong>商品详情加载失败</strong>
      <span>{{ error }}</span>
      <button class="btn" type="button" @click="loadDetail">重新加载</button>
    </div>

    <template v-else-if="detail">
      <PageHeader
        eyebrow="医院目录商品"
        :title="detail.productName"
        :description="`${detail.productCode} · ${detail.specModel || '暂无规格'}`"
      />
      <div class="product-detail-toolbar">
        <span
          class="product-detail-status"
          :class="{ active: detail.statusLabel.includes('启用') || detail.statusLabel.includes('正常') }"
        >
          {{ detail.statusLabel }}
        </span>
        <div class="product-detail-toolbar-actions">
          <button
            class="btn"
            type="button"
            @click="returnToCatalog"
          >
            <ArrowLeft :size="15" aria-hidden="true" />
            返回列表
          </button>
          <button
            v-if="auth.canWrite('hospital-product-catalog')"
            class="btn btn-primary"
            type="button"
            @click="router.push({ name: 'master-data-list', params: { code: 'hospital-product-catalog' }, query: { ...route.query, editProduct: productCode } })"
          >
            <Pencil :size="15" aria-hidden="true" />
            编辑商品
          </button>
        </div>
      </div>

      <div class="product-detail-sections">
        <section
          v-for="section in detailSections"
          :key="section.id"
          class="product-detail-section"
          :aria-labelledby="`${section.id}-heading`"
        >
          <header class="product-detail-section-heading">
            <div>
              <h3 :id="`${section.id}-heading`">{{ section.title }}</h3>
              <p>{{ section.description }}</p>
            </div>
          </header>

          <dl class="product-detail-grid">
            <div
              v-for="field in section.fields"
              :key="field.label"
              class="product-detail-field"
              :class="{ emphasis: field.emphasis }"
            >
              <dt>{{ field.label }}</dt>
              <dd>{{ field.value }}</dd>
            </div>
          </dl>
        </section>
      </div>

      <section class="product-detail-section" aria-labelledby="attachments-heading">
        <header class="product-detail-section-heading">
          <div>
            <h3 id="attachments-heading">附件信息</h3>
            <p>商品注册、授权及合规资料</p>
          </div>
          <span>{{ detail.attachments.length }} 份</span>
        </header>

        <div v-if="detail.attachments.length" class="product-attachment-table-wrap">
          <table class="product-attachment-table">
            <thead>
              <tr>
                <th scope="col">文件名称</th>
                <th scope="col">资料分类</th>
                <th scope="col">状态</th>
                <th scope="col">有效期</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="file in detail.attachments" :key="file.fileName">
                <td data-label="文件名称">
                  <span class="product-attachment-name">
                    <FileText :size="17" aria-hidden="true" />
                    {{ file.fileName }}
                  </span>
                </td>
                <td data-label="资料分类">{{ file.category || '-' }}</td>
                <td data-label="状态">{{ formatStatusText(file.status) }}</td>
                <td data-label="有效期">{{ file.validDate || '-' }}</td>
                <td data-label="操作">
                  <button
                    class="btn-text"
                    type="button"
                    @click="openAttachment(file)"
                  >
                    <Eye :size="16" aria-hidden="true" />
                    查看
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="product-detail-empty-state">暂无附件</p>
      </section>

      <Teleport to="body">
      <div v-if="previewFile" class="attachment-preview-mask" @click.self="closeAttachment">
        <section class="attachment-preview-dialog" role="dialog" aria-modal="true" aria-label="附件预览">
          <header>
            <div>
              <p>{{ previewFile.category }}</p>
              <h3>{{ previewFile.fileName }}</h3>
            </div>
            <button class="btn-icon" type="button" aria-label="关闭附件预览" @click="closeAttachment">
              <X :size="18" aria-hidden="true" />
            </button>
          </header>
          <p v-if="previewLoading" role="status">正在读取附件...</p>
          <p v-else-if="previewError" role="alert">{{ previewError }}</p>
          <img v-else-if="previewUrl && previewType.startsWith('image/')" class="product-attachment-image" :src="previewUrl" :alt="previewFile.fileName" />
          <iframe
            v-else-if="previewUrl && previewType === 'application/pdf'"
            class="attachment-preview-frame"
            :src="previewUrl"
            title="附件预览"
          ></iframe>
          <div v-else class="attachment-preview-empty">
            <FileText :size="36" aria-hidden="true" />
            <strong>{{ previewFile.fileName }}</strong>
            <span>该格式不支持在线预览</span>
          </div>
        </section>
      </div>
      </Teleport>
    </template>
  </main>
</template>

<style scoped>
.product-attachment-image { display: block; max-width: 100%; max-height: 70vh; object-fit: contain; margin: 0 auto; }
/* 注：详细样式由 master-data-all.override.css 统一控制 */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

button:focus-visible {
  outline: 3px solid rgba(8, 127, 113, 0.28);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
