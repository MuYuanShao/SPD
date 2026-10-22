import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch, type Ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { useCatalogApprovalRefreshStore } from '../stores/catalogApprovalRefresh'
import { useAuthStore } from '../stores/auth'
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
    keyMonitored: false,
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
  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()
  const refreshStore = useCatalogApprovalRefreshStore()
  const productSaving = ref(false)
  const batchProductSaving = ref(false)
  const productOpening = ref(false)
  const submittedApplicationNo = ref('')
  let active = true
  let openRequest = 0
  let productSnapshot = ''
  let batchSnapshot = ''
  let batchCodes: string[] = []
  let originalQuotaManaged = false
  const busy = computed(() => productSaving.value || batchProductSaving.value)
  const dirty = computed(() =>
    (options.productDialogOpen.value && productSnapshot !== JSON.stringify(productForm.value)) ||
    (options.batchProductDialogOpen.value && batchSnapshot !== JSON.stringify(batchProductForm.value)))

  function canLeave() {
    if (busy.value) return false
    return !dirty.value || window.confirm('有未保存的修改，确定要离开吗？')
  }
  async function clearEditorQuery() {
    if (route.name !== 'master-data-list' || route.params.code !== 'hospital-product-catalog') return
    const { editProduct, productAction, codes, ...query } = route.query
    if (editProduct || productAction) await router.replace({ query })
  }
  function closeProductDialog() {
    if (!canLeave()) return
    options.productDialogOpen.value = false
    void clearEditorQuery()
  }
  function closeBatchProductDialog() {
    if (!canLeave()) return
    options.batchProductDialogOpen.value = false
    void clearEditorQuery()
  }
  onBeforeRouteLeave(canLeave)
  onBeforeRouteUpdate((to, from) => {
    if (to.params.code !== from.params.code) {
      if (!canLeave()) return false
      openRequest++
      productOpening.value = false
      options.productDialogOpen.value = false
      options.batchProductDialogOpen.value = false
    }
  })
  function beforeUnload(event: BeforeUnloadEvent) {
    if (active && (dirty.value || busy.value)) event.preventDefault()
  }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))
  onDeactivated(() => {
    active = false
    openRequest++
    productOpening.value = false
    options.productDialogOpen.value = false
    options.batchProductDialogOpen.value = false
  })
  async function openFromRoute() {
    if (!active || route.name !== 'master-data-list' || route.params.code !== 'hospital-product-catalog') return
    if (!auth.canWrite('hospital-product-catalog')) return
    if (typeof route.query.editProduct === 'string') await openEditProduct(route.query.editProduct, false)
    else if (route.query.productAction === 'new') await openCreateProduct()
    else if (route.query.productAction === 'batch') {
      options.selectedCodes.value = String(route.query.codes ?? '').split(',').map(code => code.trim()).filter(Boolean)
      batchEditSelectedProducts()
    }
  }
  onActivated(() => { active = true; void openFromRoute() })
  watch(() => [route.query.editProduct, route.query.productAction, route.query.codes], () => { void openFromRoute() })

  const productForm = ref<ProductCreatePayload>(emptyProductForm())
  const batchProductForm = ref(emptyBatchProductForm())
  const manufacturerOptions = ref<PartnerOption[]>([])
  const supplierOptions = ref<PartnerOption[]>([])
  const disableConfirmOpen = ref(false)
  const disableConfirmLoading = ref(false)
  const disableConfirmCodes = ref<string[]>([])
  const disableConfirmCount = ref(0)

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
      keyMonitored: detail.keyMonitored,
      storageCondition: detail.storageCondition === '-' ? '' : detail.storageCondition
    }
  }

  async function openCreateProduct() {
    if (busy.value || productOpening.value || !canLeave()) return
    const request = ++openRequest
    productOpening.value = true
    options.clearActionState()
    try {
      await loadPartnerOptions()
      if (!active || request !== openRequest) return
      options.productDialogMode.value = 'create'
      productForm.value = emptyProductForm()
      originalQuotaManaged = false
      productSnapshot = JSON.stringify(productForm.value)
      options.productDialogOpen.value = true
    } catch (err) {
      if (active && request === openRequest) options.actionError.value = err instanceof Error ? err.message : '厂家和供应商选项加载失败'
    } finally {
      if (request === openRequest) productOpening.value = false
    }
  }

  async function openEditProduct(rowCode = options.selectedCodes.value[0], requireSelected = true) {
    if (busy.value || !canLeave()) return
    options.clearActionState()
    if (!rowCode || (requireSelected && options.selectedCodes.value.length !== 1)) {
      options.actionError.value = '请选择一条商品记录后再修改'
      return
    }
    const request = ++openRequest
    productOpening.value = true
    try {
      const [detail] = await Promise.all([fetchHospitalProductDetail(rowCode), loadPartnerOptions()])
      if (!active || request !== openRequest) return
      options.productDialogMode.value = 'edit'
      fillProductForm(detail)
      originalQuotaManaged = detail.quotaManaged
      productSnapshot = JSON.stringify(productForm.value)
      options.productDialogOpen.value = true
    } catch (err) {
      if (active && request === openRequest) options.actionError.value = err instanceof Error ? err.message : '商品信息加载失败'
    } finally {
      if (request === openRequest) productOpening.value = false
    }
  }

  async function saveProduct() {
    if (busy.value || !options.productDialogOpen.value) return
    options.clearActionState()
    const form = productForm.value
    if (!form.productName.trim() || !form.specModel.trim() || !form.unit.trim()) {
      options.actionError.value = '商品名称、规格型号、单位为必填项'
      return
    }
    if (form.quotaManaged && (form.highValue || form.coldChain)) {
      options.actionError.value = '高值耗材或冷链耗材不能设置为定数管理'
      return
    }

    if (options.productDialogMode.value === 'edit' && originalQuotaManaged && !form.quotaManaged &&
        !window.confirm('是否定数管理改为“否”后，将禁止后续新增定数包业务，历史已打包库存继续流转。确认提交审批吗？')) return
    productSaving.value = true
    const payload = {
      ...form,
      purchasePrice: normalizeProductNumber(form.purchasePrice) ?? 0,
      retailPrice: normalizeProductNumber(form.retailPrice),
      minPurchaseQty: normalizeProductNumber(form.minPurchaseQty) ?? 1,
      conversionRate: normalizeProductNumber(form.conversionRate) ?? 1,
      purchasePackageQty: normalizeProductNumber(form.purchasePackageQty)
    }

    try {
      const result =
        options.productDialogMode.value === 'edit'
          ? await updateHospitalProduct(form.productCode, payload)
          : await createHospitalProduct(payload)
      submittedApplicationNo.value = result.applicationNo
      refreshStore.changed()
      options.actionMessage.value = `已生成审批单 ${result.applicationNo}，最终审批通过后生效`
      options.productDialogOpen.value = false
      await clearEditorQuery()
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '保存商品失败'
    } finally {
      productSaving.value = false
    }
  }

  function batchEditSelectedProducts() {
    if (busy.value || !canLeave()) return
    options.clearActionState()
    if (!options.selectedCodes.value.length) {
      options.actionError.value = '请选择需要批量修改的商品'
      return
    }

    batchCodes = [...options.selectedCodes.value]
    batchProductForm.value = emptyBatchProductForm()
    batchSnapshot = JSON.stringify(batchProductForm.value)
    options.batchProductDialogOpen.value = true
  }

  async function saveBatchProductEdit() {
    if (busy.value || !options.batchProductDialogOpen.value) return
    options.clearActionState()
    const hasValue = Object.values(batchProductForm.value).some((value) => String(value).trim() !== '')
    if (!hasValue) {
      options.actionError.value = '请至少填写一个批量修改字段'
      return
    }
    batchProductSaving.value = true
    try {
      const result = await batchUpdateHospitalProducts({
        productCodes: batchCodes,
        ...batchProductForm.value,
        purchasePrice: batchProductForm.value.purchasePrice === '' ? null : Number(batchProductForm.value.purchasePrice)
      })
      if (result.updatedRows > 0) refreshStore.changed()
      options.actionMessage.value = `已生成 ${result.updatedRows} 条批量修改审批单，最终审批通过后生效`
      options.batchProductDialogOpen.value = false
      options.selectedCodes.value = []
      await clearEditorQuery()
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '批量修改失败'
    } finally {
      batchProductSaving.value = false
    }
  }

  async function disableProducts(productCodes = options.selectedCodes.value) {
    options.clearActionState()
    if (!productCodes.length) {
      options.actionError.value = '请选择需要停用的商品'
      return
    }
    // 准备确认弹窗所需的数据，确认动作由 view 层调用 confirmDisableProducts 执行
    disableConfirmOpen.value = true
    disableConfirmCount.value = productCodes.length
    disableConfirmCodes.value = [...productCodes]
  }

  async function confirmDisableProducts() {
    const productCodes = disableConfirmCodes.value
    disableConfirmLoading.value = true
    try {
      await updateHospitalProductStatus({ productCodes, status: 0 })
      disableConfirmOpen.value = false
      disableConfirmCodes.value = []
      await options.loadPage()
    } catch (err) {
      options.actionError.value = err instanceof Error ? err.message : '停用失败'
    } finally {
      disableConfirmLoading.value = false
    }
  }

  function cancelDisableProducts() {
    if (disableConfirmLoading.value) return
    disableConfirmOpen.value = false
    disableConfirmCodes.value = []
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
    productSaving,
    batchProductSaving,
    submittedApplicationNo,
    closeProductDialog,
    closeBatchProductDialog,
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
    confirmDisableProducts,
    cancelDisableProducts,
    disableConfirmOpen,
    disableConfirmLoading,
    disableConfirmCount,
    submitSelectedProducts,
    exportHospitalCatalog
  }
}
