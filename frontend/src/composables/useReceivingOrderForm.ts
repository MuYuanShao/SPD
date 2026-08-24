import { computed, reactive, ref, type Ref } from 'vue'
import type { SupplierOption } from '../api/receivingOrders'

function createEmptyReceivingItem() {
  return {
    productCode: '',
    productionBatchNo: '',
    udiCode: '',
    productionDate: '',
    expireDate: '',
    quantity: 1,
    qualifiedQuantity: 1,
    unqualifiedQuantity: 0
  }
}

export function useReceivingOrderForm(options: {
  suppliers: Ref<SupplierOption[]>
  showCreateModal: Ref<boolean>
  editingReceivingNo: Ref<string>
}) {
  const supplierSearchQuery = ref('')
  const showSupplierDropdown = ref(false)
  const form = reactive({
    purchaseOrderNo: '',
    supplierName: '',
    warehouseName: '',
    receivingType: '',
    isAgent: false,
    remark: '',
    items: [createEmptyReceivingItem()]
  })

  const filteredSuppliers = computed(() => {
    const query = supplierSearchQuery.value.trim().toLowerCase()
    if (!query) return options.suppliers.value
    return options.suppliers.value.filter((supplier) => supplier.supplierName.toLowerCase().includes(query))
  })

  const createTotals = computed(() =>
    form.items.reduce(
      (totals, item) => ({
        quantity: totals.quantity + Number(item.quantity || 0),
        qualifiedQuantity: totals.qualifiedQuantity + Number(item.qualifiedQuantity || 0),
        unqualifiedQuantity: totals.unqualifiedQuantity + Number(item.unqualifiedQuantity || 0)
      }),
      { quantity: 0, qualifiedQuantity: 0, unqualifiedQuantity: 0 }
    )
  )

  const currentCreateTime = computed(() => new Date().toLocaleString('zh-CN', { hour12: false }))

  function selectSupplier(name: string) {
    form.supplierName = name
    supplierSearchQuery.value = name
    showSupplierDropdown.value = false
  }

  function clearSupplier() {
    form.supplierName = ''
    supplierSearchQuery.value = ''
  }

  function resetForm() {
    form.purchaseOrderNo = ''
    form.supplierName = ''
    form.warehouseName = ''
    form.receivingType = ''
    form.isAgent = false
    form.remark = ''
    supplierSearchQuery.value = ''
    form.items = [createEmptyReceivingItem()]
  }

  function openCreateModal() {
    options.editingReceivingNo.value = ''
    resetForm()
    options.showCreateModal.value = true
  }

  function closeCreateModal() {
    options.showCreateModal.value = false
    showSupplierDropdown.value = false
    options.editingReceivingNo.value = ''
  }

  function closeSupplierDropdown() {
    setTimeout(() => {
      showSupplierDropdown.value = false
    }, 200)
  }

  function addItem() {
    form.items.push(createEmptyReceivingItem())
  }

  function removeItem(index: number) {
    if (form.items.length > 1) {
      form.items.splice(index, 1)
    }
  }

  function syncQualifiedQuantity(index: number) {
    const item = form.items[index]
    if (!item) return
    const quantity = Number(item.quantity || 0)
    item.qualifiedQuantity = quantity
    item.unqualifiedQuantity = 0
  }

  function syncUnqualifiedQuantity(index: number) {
    const item = form.items[index]
    if (!item) return
    const quantity = Number(item.quantity || 0)
    const qualifiedQuantity = Number(item.qualifiedQuantity || 0)
    item.unqualifiedQuantity = Math.max(quantity - qualifiedQuantity, 0)
  }

  return {
    form,
    supplierSearchQuery,
    showSupplierDropdown,
    filteredSuppliers,
    createTotals,
    currentCreateTime,
    selectSupplier,
    clearSupplier,
    resetForm,
    openCreateModal,
    closeCreateModal,
    closeSupplierDropdown,
    addItem,
    removeItem,
    syncQualifiedQuantity,
    syncUnqualifiedQuantity
  }
}
