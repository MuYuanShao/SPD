import { reactive, ref } from 'vue'
import { fetchQuotaManagedProducts } from '../api/quotaPackages'

type TemplateProductForm = {
  productCode: string
  quantity: number
  unit: string
}

export type SelectedQuotaProductInfo = {
  productName: string
  specModel: string
  brand: string
  manufacturerName: string
  unit: string
  purchasePrice: string
}

export function useQuotaProductSelector(templateForm: TemplateProductForm) {
  const productSelectorOpen = ref(false)
  const productLoading = ref(false)
  const productQuery = reactive({ productCode: '', productName: '' })
  const quotaProducts = ref<Record<string, unknown>[]>([])
  const selectedProductInfo = ref<SelectedQuotaProductInfo | null>(null)

  async function searchQuotaProducts() {
    productLoading.value = true
    try {
      quotaProducts.value = await fetchQuotaManagedProducts({
        productCode: productQuery.productCode,
        productName: productQuery.productName
      })
    } finally {
      productLoading.value = false
    }
  }

  function openProductSelector() {
    productQuery.productCode = ''
    productQuery.productName = ''
    quotaProducts.value = []
    productSelectorOpen.value = true
    searchQuotaProducts()
  }

  function selectProduct(row: Record<string, unknown>) {
    templateForm.productCode = String(row.code || '')
    selectedProductInfo.value = {
      productName: String(row.name || ''),
      specModel: String(row.spec || ''),
      brand: String(row.brand || ''),
      manufacturerName: String(row.manufacturer || ''),
      unit: String(row.unit || ''),
      purchasePrice: String(row.price || '')
    }
    templateForm.quantity = 1
    templateForm.unit = String(row.unit || '')
    productSelectorOpen.value = false
  }

  return {
    productSelectorOpen,
    productLoading,
    productQuery,
    quotaProducts,
    selectedProductInfo,
    searchQuotaProducts,
    openProductSelector,
    selectProduct
  }
}
