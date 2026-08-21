<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Eye, FileText, Pencil, X } from '@lucide/vue'
import { formatStatusText } from '../../utils/chineseDisplay'
import {
  fetchHospitalProductDetail,
  type ProductAttachment,
  type ProductDetail
} from '../../api/masterData'
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
const router = useRouter()
const detail = ref<ProductDetail | null>(null)
const loading = ref(false)
const error = ref('')
const previewFile = ref<ProductAttachment | null>(null)

const productCode = computed(() => String(route.params.productCode ?? ''))

function displayValue(value: unknown) {
  return value === null || value === undefined || value === '' ? '-' : String(value)
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
        { label: '冷链管理', value: yesNo(item.coldChain) },
        { label: '储存条件', value: displayValue(item.storageCondition) }
      ]
    }
  ]
})

async function loadDetail() {
  loading.value = true
  error.value = ''

  try {
    detail.value = await fetchHospitalProductDetail(productCode.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '商品详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<template>
  <main class="approval-detail-page product-detail-page">
    <button class="btn product-detail-back" type="button" @click="router.back()">
      <ArrowLeft :size="17" aria-hidden="true" />
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
      >
        <template #actions>
          <span
            class="product-detail-status"
            :class="{ active: detail.statusLabel.includes('启用') || detail.statusLabel.includes('正常') }"
          >
            {{ detail.statusLabel }}
          </span>
          <button
            class="btn primary"
            type="button"
            @click="router.push({ name: 'hospital-product-edit', params: { productCode } })"
          >
            <Pencil :size="16" aria-hidden="true" />
            编辑商品
          </button>
        </template>
      </PageHeader>

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
                  <button class="btn-text" type="button" @click="previewFile = file">
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

      <div v-if="previewFile" class="attachment-preview-mask" @click.self="previewFile = null">
        <section class="attachment-preview-dialog" role="dialog" aria-modal="true" aria-label="附件预览">
          <header>
            <div>
              <p>{{ previewFile.category }}</p>
              <h3>{{ previewFile.fileName }}</h3>
            </div>
            <button class="btn-icon" type="button" aria-label="关闭附件预览" @click="previewFile = null">
              <X :size="18" aria-hidden="true" />
            </button>
          </header>
          <iframe
            v-if="previewFile.fileUrl"
            class="attachment-preview-frame"
            :src="previewFile.fileUrl"
            title="附件预览"
          ></iframe>
          <div v-else class="attachment-preview-body">
            <FileText :size="42" aria-hidden="true" />
            <strong>{{ previewFile.fileName }}</strong>
            <span>当前附件暂无真实文件地址，接入文件 URL 后可在此预览 PDF 或图片。</span>
          </div>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.product-detail-page {
  max-width: 1280px;
  min-width: 0;
}

.product-detail-back {
  width: fit-content;
  min-height: 40px;
}

.product-detail-status {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  border: 1px solid #f3cfcf;
  border-radius: 999px;
  background: #fff5f5;
  color: #b42318;
  padding: 0 11px;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.product-detail-status.active {
  border-color: #b7ded7;
  background: #eaf7f3;
  color: #087f71;
}

.product-detail-sections {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.product-detail-section {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: 0 2px 8px rgba(27, 39, 51, 0.06);
}

.product-detail-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--line);
  background: #f7fafb;
  padding: 12px 16px;
}

.product-detail-section-heading h3,
.product-detail-section-heading p {
  margin: 0;
}

.product-detail-section-heading h3 {
  color: #102033;
  font-size: 16px;
  line-height: 1.35;
}

.product-detail-section-heading p {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 12px;
}

.product-detail-section-heading > span {
  flex: 0 0 auto;
  color: #607486;
  font-size: 12px;
  font-weight: 700;
}

/* 简洁规整的字段网格：标签在上、值在下，自动换列 */
.product-detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  margin: 0;
}

.product-detail-field {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  min-width: 0;
  min-height: 62px;
  padding: 11px 16px;
  border-bottom: 1px solid #eef2f5;
  border-right: 1px solid #eef2f5;
}

.product-detail-field dt {
  color: #607486;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
}

.product-detail-field dd {
  margin: 0;
  color: #25384a;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.product-detail-field.emphasis dd {
  color: #102033;
  font-weight: 800;
}

.product-attachment-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.product-attachment-table-wrap {
  min-width: 0;
  overflow-x: auto;
}

.product-attachment-table th,
.product-attachment-table td {
  border-bottom: 1px solid #e8eef1;
  padding: 10px 12px;
  text-align: left;
  vertical-align: top;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.product-attachment-table tbody tr:last-child > * {
  border-bottom: 0;
}

.product-attachment-table th {
  background: #fbfcfd;
  color: #607486;
  font-size: 12px;
  font-weight: 800;
}

.product-attachment-table td {
  color: #25384a;
  font-size: 13px;
}

.product-attachment-table th:first-child {
  width: 34%;
}

.product-attachment-table th:last-child {
  width: 88px;
}

.product-attachment-name,
.product-attachment-table .btn-text {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.product-attachment-name svg {
  flex: 0 0 auto;
  color: #087f71;
}

.product-attachment-table .btn-text {
  min-height: 36px;
  cursor: pointer;
}

.product-detail-empty-state {
  margin: 0;
  color: var(--text-muted);
  padding: 28px 16px;
  text-align: center;
}

.product-detail-error {
  display: grid;
  justify-items: start;
  gap: 8px;
  border: 1px solid #f3cfcf;
  border-radius: 8px;
  background: #fff7f7;
  color: #b42318;
  padding: 18px;
}

.product-detail-error span {
  color: #7a2e2e;
}

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

@media (max-width: 680px) {
  .product-detail-grid {
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  }

  .product-detail-field {
    padding: 9px 12px;
    min-height: 54px;
  }

  .product-attachment-table,
  .product-attachment-table tbody,
  .product-attachment-table tr,
  .product-attachment-table td {
    display: block;
    width: 100%;
  }

  .product-attachment-table thead {
    display: none;
  }

  .product-attachment-table tr {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 9px 14px;
    border-bottom: 1px solid var(--line);
    padding: 12px;
  }

  .product-attachment-table td {
    border: 0;
    padding: 0;
  }

  .product-attachment-table td::before {
    content: attr(data-label);
    display: block;
    margin-bottom: 3px;
    color: #607486;
    font-size: 12px;
    font-weight: 700;
  }
}

@media (max-width: 440px) {
  .product-attachment-table tr {
    grid-template-columns: 1fr;
  }
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
