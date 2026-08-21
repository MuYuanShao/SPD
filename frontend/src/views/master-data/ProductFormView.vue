<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CheckCircle2, ClipboardList, PackagePlus, Save } from '@lucide/vue'
import {
  createHospitalProduct,
  fetchHospitalProductDetail,
  fetchHospitalProductPartnerOptions,
  updateHospitalProduct,
  type PartnerOption,
  type ProductCreatePayload,
  type ProductDetail
} from '../../api/masterData'

const router = useRouter()
const route = useRoute()
const saving = ref(false)
const loading = ref(false)
const error = ref('')
const originalQuotaManaged = ref<boolean | null>(null)
const productCode = computed(() => String(route.params.productCode ?? ''))
const editMode = computed(() => route.name === 'hospital-product-edit')

const manufacturerOptions = ref<PartnerOption[]>([])
const supplierOptions = ref<PartnerOption[]>([])

function withRetainedOption(list: PartnerOption[], current: string) {
  if (!current || list.some((item) => item.name === current)) {
    return list
  }
  return [...list, { code: '-', name: current, status: 0 }]
}

async function loadPartnerOptions() {
  try {
    const options = await fetchHospitalProductPartnerOptions()
    manufacturerOptions.value = options.manufacturers
    supplierOptions.value = options.suppliers
  } catch (err) {
    console.error('厂家与供应商选项加载失败', err)
    manufacturerOptions.value = []
    supplierOptions.value = []
  }
}

/**
 * 选择厂家时自动回填该厂家的生产许可证号。
 */
function handleManufacturerChange() {
  const option = manufacturerOptions.value.find((item) => item.name === form.manufacturerName)
  if (option) {
    form.productionLicenseNo = option.licenseNo ?? ''
  }
}

/**
 * 选择供应商时自动回填该供应商的经营许可证号。
 */
function handleSupplierChange() {
  const option = supplierOptions.value.find((item) => item.name === form.supplierName)
  if (option) {
    form.businessLicenseNo = option.businessLicenseNo ?? ''
  }
}

const form = reactive<ProductCreatePayload>({
  productCode: '',
  productName: '',
  specModel: '',
  brand: '',
  manufacturerName: '',
  supplierName: '',
  unit: '支',
  purchasePrice: '',
  retailPrice: null,
  minPurchaseQty: 1,
  purchaseUnit: '盒',
  conversionRate: 1,
  purchasePackageQty: null,
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
  highValue: false,
  coldChain: false,
  quotaManaged: false,
  storageCondition: '常温'
})

const requiredFields = [
  ['productCode', '商品编码'],
  ['productName', '商品名称'],
  ['specModel', '规格型号'],
  ['unit', '基本单位']
] as const

function normalizeNumber(value: string | number | null) {
  if (value === '' || value === null) return null
  return Number(value)
}

function assignDetail(detail: ProductDetail) {
  originalQuotaManaged.value = detail.quotaManaged
  Object.assign(form, {
    productCode: detail.productCode,
    productName: detail.productName,
    specModel: detail.specModel,
    brand: detail.brand === '-' ? '' : detail.brand,
    manufacturerName: detail.manufacturerName === '-' ? '' : detail.manufacturerName,
    supplierName: detail.supplierName === '-' ? '' : detail.supplierName,
    unit: detail.unit,
    purchasePrice: detail.purchasePrice,
    retailPrice: detail.retailPrice,
    minPurchaseQty: detail.minPurchaseQty,
    purchaseUnit: detail.purchaseUnit === '-' ? '' : detail.purchaseUnit,
    conversionRate: detail.conversionRate,
    purchasePackageQty: detail.purchasePackageQty,
    udiCode: detail.udiCode === '-' ? '' : detail.udiCode,
    registrationNo: detail.registrationNo === '-' ? '' : detail.registrationNo,
    registrationExpireDate: detail.registrationExpireDate === '-' ? '' : detail.registrationExpireDate,
    productionLicenseNo: detail.productionLicenseNo === '-' ? '' : detail.productionLicenseNo,
    businessLicenseNo: detail.businessLicenseNo === '-' ? '' : detail.businessLicenseNo,
    volumeBased: detail.volumeBased,
    centralizedProcurement: detail.centralizedProcurement,
    domestic: detail.domestic,
    contractCode: detail.contractCode === '-' ? '' : detail.contractCode,
    firstCategory: detail.firstCategory === '-' ? '' : detail.firstCategory,
    secondCategory: detail.secondCategory === '-' ? '' : detail.secondCategory,
    thirdCategory: detail.thirdCategory === '-' ? '' : detail.thirdCategory,
    chargeable: detail.chargeable,
    tenderSubCode: detail.tenderSubCode === '-' ? '' : detail.tenderSubCode,
    highValue: detail.highValue,
    coldChain: detail.coldChain,
    quotaManaged: detail.quotaManaged,
    storageCondition: detail.storageCondition === '-' ? '' : detail.storageCondition
  })
}

async function loadDetailForEdit() {
  if (!editMode.value) return

  loading.value = true
  error.value = ''
  try {
    const detail = await fetchHospitalProductDetail(productCode.value)
    assignDetail(detail)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '耗材信息加载失败'
  } finally {
    loading.value = false
  }
}

async function submitForm() {
  error.value = ''
  const missing = requiredFields.find(([key]) => !String(form[key]).trim())
  if (missing) {
    error.value = `${missing[1]}为必填项`
    return
  }
  if (form.quotaManaged && (form.highValue || form.coldChain)) {
    error.value = '高值耗材或冷链耗材不能设置为定数管理'
    return
  }
  if (editMode.value && originalQuotaManaged.value === true && form.quotaManaged === false) {
    const confirmed = window.confirm('是否定数管理改为“否”后，将禁止后续新增定数包业务，历史已打包库存继续流转。确认提交审批吗？')
    if (!confirmed) return
  }

  saving.value = true
  try {
    const payload = {
      ...form,
      purchasePrice: normalizeNumber(form.purchasePrice) ?? 0,
      retailPrice: normalizeNumber(form.retailPrice),
      minPurchaseQty: normalizeNumber(form.minPurchaseQty) ?? 1,
      conversionRate: normalizeNumber(form.conversionRate) ?? 1,
      purchasePackageQty: normalizeNumber(form.purchasePackageQty)
    }
    const result = editMode.value
      ? await updateHospitalProduct(productCode.value, payload)
      : await createHospitalProduct(payload)
    router.push({ name: 'pending-product-approval-detail', params: { applicationNo: result.applicationNo } })
  } catch (err) {
    error.value = err instanceof Error ? err.message : editMode.value ? '修改耗材失败' : '新增耗材失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadDetailForEdit()
  loadPartnerOptions()
})
</script>

<template>
  <section class="product-form-page">
    <button class="btn" type="button" @click="router.back()">
      <ArrowLeft :size="17" />
      返回医院目录
    </button>

    <p v-if="loading" class="approval-empty">正在加载耗材信息...</p>

    <header v-else class="approval-detail-header">
      <div>
        <p>医院目录</p>
        <h2>{{ editMode ? '修改耗材' : '新增耗材' }}</h2>
        <span>{{ editMode ? '调整耗材字段后点击保存。' : '填写耗材基础资料、价格采购、分类和资质属性。' }}</span>
      </div>
    </header>

    <form v-if="!loading" class="product-form-card" @submit.prevent="submitForm">
      <section>
        <div class="section-title">
          <PackagePlus :size="20" />
          <h3>基础信息</h3>
        </div>
        <div class="product-form-grid">
          <label>
            <span>商品编码</span>
            <input
              v-model.trim="form.productCode"
              required
              type="text"
              placeholder="如 PROD-004"
              :readonly="editMode"
            />
          </label>
          <label>
            <span>商品名称</span>
            <input v-model.trim="form.productName" required type="text" placeholder="耗材名称" />
          </label>
          <label>
            <span>规格型号</span>
            <input v-model.trim="form.specModel" required type="text" placeholder="规格 / 型号" />
          </label>
          <label>
            <span>品牌</span>
            <input v-model.trim="form.brand" type="text" placeholder="品牌" />
          </label>
          <label>
            <span>生产厂家</span>
            <select v-model="form.manufacturerName" @change="handleManufacturerChange">
              <option value="">请选择生产厂家</option>
              <option
                v-for="item in withRetainedOption(manufacturerOptions, form.manufacturerName)"
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
            <select v-model="form.supplierName" @change="handleSupplierChange">
              <option value="">请选择供应商</option>
              <option
                v-for="item in withRetainedOption(supplierOptions, form.supplierName)"
                :key="item.code + item.name"
                :value="item.name"
                :disabled="item.status !== 1"
              >
                {{ item.name }}（{{ item.code }}）{{ item.status === 1 ? '' : ' · 已停用' }}
              </option>
            </select>
          </label>
          <label>
            <span>基本单位</span>
            <input v-model.trim="form.unit" required type="text" placeholder="支 / 包 / 盒" />
          </label>
          <label>
            <span>储存条件</span>
            <input v-model.trim="form.storageCondition" type="text" placeholder="常温 / 冷藏" />
          </label>
        </div>
      </section>

      <section>
        <div class="section-title">
          <ClipboardList :size="20" />
          <h3>价格采购与分类</h3>
        </div>
        <div class="product-form-grid">
          <label>
            <span>单价</span>
            <input v-model="form.purchasePrice" type="number" min="0" step="0.0001" placeholder="采购单价" />
          </label>
          <label>
            <span>零售价</span>
            <input v-model="form.retailPrice" type="number" min="0" step="0.0001" placeholder="零售价" />
          </label>
          <label>
            <span>最小采购量</span>
            <input v-model="form.minPurchaseQty" type="number" min="0" step="0.0001" />
          </label>
          <label>
            <span>采购单位</span>
            <input v-model.trim="form.purchaseUnit" type="text" />
          </label>
          <label>
            <span>中包装数量</span>
            <input v-model="form.conversionRate" type="number" min="0" step="0.000001" />
          </label>
          <label>
            <span>采购包装数量</span>
            <input v-model="form.purchasePackageQty" type="number" min="0" step="0.0001" />
          </label>
          <label>
            <span>合同编码</span>
            <input v-model.trim="form.contractCode" type="text" placeholder="合同编码" />
          </label>
          <label>
            <span>一级分类</span>
            <input v-model.trim="form.firstCategory" type="text" placeholder="一级分类" />
          </label>
          <label>
            <span>二级分类</span>
            <input v-model.trim="form.secondCategory" type="text" placeholder="二级分类" />
          </label>
          <label>
            <span>三级分类</span>
            <input v-model.trim="form.thirdCategory" type="text" placeholder="三级分类" />
          </label>
          <label>
            <span>招采子编码</span>
            <input v-model.trim="form.tenderSubCode" type="text" placeholder="招采子编码" />
          </label>
        </div>
      </section>

      <section>
        <div class="section-title">
          <CheckCircle2 :size="20" />
          <h3>资质与业务属性</h3>
        </div>
        <div class="product-form-grid">
          <label>
            <span>UDI编码</span>
            <input v-model.trim="form.udiCode" type="text" placeholder="UDI编码" />
          </label>
          <label>
            <span>注册证号</span>
            <input v-model.trim="form.registrationNo" type="text" placeholder="注册证号" />
          </label>
          <label>
            <span>注册证有效期</span>
            <input v-model="form.registrationExpireDate" type="date" />
          </label>
          <label>
            <span>生产许可证号</span>
            <input v-model.trim="form.productionLicenseNo" type="text" />
          </label>
          <label>
            <span>经营许可证号</span>
            <input v-model.trim="form.businessLicenseNo" type="text" />
          </label>
        </div>
        <div class="product-toggle-grid">
          <label><input v-model="form.volumeBased" type="checkbox" /> 是否带量</label>
          <label><input v-model="form.centralizedProcurement" type="checkbox" /> 是否集采</label>
          <label><input v-model="form.domestic" type="checkbox" /> 是否国产</label>
          <label><input v-model="form.chargeable" type="checkbox" /> 是否收费</label>
          <label><input v-model="form.highValue" type="checkbox" /> 是否高值耗材</label>
          <label><input v-model="form.coldChain" type="checkbox" /> 是否冷链</label>
          <label><input v-model="form.quotaManaged" type="checkbox" /> 是否定数管理</label>
        </div>
      </section>

      <p v-if="error" class="error-text">{{ error }}</p>
      <div class="product-form-actions">
        <button class="btn" type="button" @click="router.back()">取消</button>
        <button class="btn btn-primary" type="submit" :disabled="saving">
          <Save :size="17" />
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </form>
  </section>
</template>
