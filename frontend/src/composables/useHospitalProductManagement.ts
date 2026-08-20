import { ref, type Ref } from 'vue'
import {
  batchUpdateHospitalProducts,
  createHospitalProduct,
  exportHospitalProducts,
  fetchHospitalProductDetail,
  fetchHospitalProductPartnerOptions,
  submitHospitalProducts,
  updateHospitalProduct,
  updateHospitalProductStatus,
  type ProductCreatePayload,
  type PartnerOption,
} from '../api/masterData'

type ProductDialogMode = 'create' | 'edit'

function emptyProductForm(): ProductCreatePayload {
  return {
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
    purchaseUnit: '包',
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
    highValue: false,
    coldChain: false,
    quotaManaged: false,
    storageCondition: '常温'
  }
}

function emptyBatchProductForm() {
  return {
    volumeBased: '',
    domestic: '',
    purchasePrice: '',
    unit: '',
    registrationNo: '',
    contractCode: '',
    firstCategory: '',
    secondCategory: '',
    thirdCategory: '',
    chargeable: ''
  }
}

function normalizeProductNumber(value: string | number | null) {
  if (value === '' || value === null) return null
  return Number(value)
}

export function useHospitalProductManagement(options: {
  selectedCodes: Ref<string[]>
  productDialogOpen: Ref<boolean>
  productDialogMode: Ref<ProductDialogMode>
  batchProductDialogOpen: Ref<boolean>
  actionError: Ref<string>
  actionMessage: Ref<string>
  queryParams: Ref<Record<string, string>>
  clearActionState: () => void
  loadPage: () => Promise<void>
}) {
  const productForm = ref<ProductCreatePayload>(emptyProductForm())
  const batchProductForm = ref(emptyBatchProductForm())
  const manufacturerOptions = ref<PartnerOption[]>([])
  const supplierOptions = ref<PartnerOption[]>([])

  async function loadPartnerOptions() {
    const partnerOptions = await fetchHospitalProductPartnerOptions()
    manufacturerOptions.value = partnerOptions.manufacturers
    supplierOptions.value = partnerOptions.suppliers
  }

  function fillProductForm(detail: Awaited<ReturnType<typeof fetchHospitalProductDetail>>) {
    productForm.value = {
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
    }
  }

  async function openCreateProduct() {
    options.clearActionState()
    try {
      await loadPartnerOptions()
      options.productDialogMode.value = 'create'
      productForm.value = emptyProductForm()
      options.productDialogOpen.value = true
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '厂家和供应商选项加载失败'
    }
  }

  async function openEditProduct(rowCode = options.selectedCodes.value[0], requireSelected = true) {
    options.clearActionState()
    if (!rowCode || (requireSelected && options.selectedCodes.value.length !== 1)) {
      options.actionError.value = '请选择一条商品记录后再修改'
      return
    }

    try {
      options.productDialogMode.value = 'edit'
      const [detail] = await Promise.all([fetchHospitalProductDetail(rowCode), loadPartnerOptions()])
      fillProductForm(detail)
      options.productDialogOpen.value = true
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '商品信息加载失败'
    }
  }

  async function saveProduct() {
    options.clearActionState()
    const form = productForm.value
    if (!form.productCode.trim() || !form.productName.trim() || !form.specModel.trim() || !form.unit.trim()) {
      options.actionError.value = '商品编码、商品名称、规格型号、单位为必填项'
      return
    }
    if (form.quotaManaged && (form.highValue || form.coldChain)) {
      options.actionError.value = '高值耗材或冷链耗材不能设置为定数管理'
      return
    }

    const payload = {
      ...form,
      purchasePrice: normalizeProductNumber(form.purchasePrice) ?? 0,
      retailPrice: normalizeProductNumber(form.retailPrice),
      minPurchaseQty: normalizeProductNumber(form.minPurchaseQty) ?? 1,
      conversionRate: normalizeProductNumber(form.conversionRate) ?? 1
    }

    try {
      const result =
        options.productDialogMode.value === 'edit'
          ? await updateHospitalProduct(form.productCode, payload)
          : await createHospitalProduct(payload)
      options.actionMessage.value = `已生成审批单 ${result.applicationNo}`
      options.productDialogOpen.value = false
      await options.loadPage()
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '保存商品失败'
    }
  }

  function batchEditSelectedProducts() {
    options.clearActionState()
    if (!options.selectedCodes.value.length) {
      options.actionError.value = '请选择需要批量修改的商品'
      return
    }

    batchProductForm.value = emptyBatchProductForm()
    options.batchProductDialogOpen.value = true
  }

  async function saveBatchProductEdit() {
    options.clearActionState()
    const hasValue = Object.values(batchProductForm.value).some((value) => String(value).trim() !== '')
    if (!hasValue) {
      options.actionError.value = '请至少填写一个批量修改字段'
      return
    }
    try {
      const result = await batchUpdateHospitalProducts({
        productCodes: options.selectedCodes.value,
        ...batchProductForm.value,
        purchasePrice: batchProductForm.value.purchasePrice === '' ? null : Number(batchProductForm.value.purchasePrice)
      })
      options.actionMessage.value = `已生成 ${result.updatedRows} 条批量修改审批单`
      options.batchProductDialogOpen.value = false
      options.selectedCodes.value = []
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '批量修改失败'
    }
  }

  async function disableProducts(productCodes = options.selectedCodes.value) {
    options.clearActionState()
    if (!productCodes.length) {
      options.actionError.value = '请选择需要停用的商品'
      return
    }
    if (!window.confirm(`确认停用已选择的 ${productCodes.length} 条商品吗？`)) {
      return
    }

    try {
      await updateHospitalProductStatus({ productCodes, status: 0 })
      await options.loadPage()
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '停用失败'
    }
  }

  async function submitSelectedProducts() {
    options.clearActionState()
    if (!options.selectedCodes.value.length) {
      options.actionError.value = '请选择需要提交的商品'
      return
    }

    try {
      const result = await submitHospitalProducts({ productCodes: options.selectedCodes.value })
      options.actionMessage.value = `已生成 ${result.submittedRows} 条商品变更审批单`
      options.selectedCodes.value = []
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '提交失败'
    }
  }

  async function exportHospitalCatalog() {
    options.clearActionState()
    try {
      const rows = await exportHospitalProducts(options.queryParams.value)
      options.actionMessage.value = `已导出 ${rows.length} 条医院目录数据`
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '导出失败'
    }
  }

  return {
    productForm,
    batchProductForm,
    manufacturerOptions,
    supplierOptions,
    openCreateProduct,
    openEditProduct,
    saveProduct,
    batchEditSelectedProducts,
    saveBatchProductEdit,
    disableProducts,
    submitSelectedProducts,
    exportHospitalCatalog
  }
}
