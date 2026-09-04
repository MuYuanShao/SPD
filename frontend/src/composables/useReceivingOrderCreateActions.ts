import type { Ref } from 'vue'
import { ref } from 'vue'
import {
  createReceivingOrder,
  fetchPurchaseOrderReceivingItems,
  fetchReceivingOrderDetail,
  updateReceivingOrder,
  type ReceivingOrderRow
} from '../api/receivingOrders'

type ReceivingForm = {
  sourceType: 'purchase_order' | 'temporary'
  purchaseOrderNo: string
  supplierId?: number
  supplierName: string
  warehouseCode: string
  warehouseName: string
  receivingType: string
  isAgent?: boolean
  remark: string
  items: Array<{
    productCode: string
    productionBatchNo: string
    udiCode: string
    productionDate: string
    expireDate: string
    quantity: number
    qualifiedQuantity: number
    unqualifiedQuantity: number
  }>
}

export function useReceivingOrderCreateActions(options: {
  form: ReceivingForm
  supplierSearchQuery: Ref<string>
  showCreateModal: Ref<boolean>
  editingReceivingNo: Ref<string>
  message: Ref<string>
  resetForm: () => void
  addItem: () => void
  reload: () => Promise<void>
}) {
  const submitting = ref(false)

  async function fillFromPurchaseOrder() {
    if (options.form.sourceType !== 'purchase_order' || !options.form.purchaseOrderNo) return
    const data = await fetchPurchaseOrderReceivingItems(options.form.purchaseOrderNo)
    const supplierName = String(data.order.supplierName ?? '')
    options.form.supplierName = supplierName
    options.form.supplierId = Number(data.order.supplierId)
    options.supplierSearchQuery.value = supplierName
    options.form.items = data.items.map((item) => ({
      productCode: String(item.productCode ?? ''),
      productionBatchNo: '',
      udiCode: '',
      productionDate: '',
      expireDate: '',
      quantity: Number(item.pendingQuantity ?? 1),
      qualifiedQuantity: Number(item.pendingQuantity ?? 1),
      unqualifiedQuantity: 0
    }))
  }

  async function submitCreate() {
    if (submitting.value) return
    submitting.value = true
    try {
      const result = options.editingReceivingNo.value
        ? await updateReceivingOrder(options.editingReceivingNo.value, { ...options.form, isAgent: undefined })
        : await createReceivingOrder({ ...options.form, isAgent: undefined })
      options.message.value = options.editingReceivingNo.value
        ? `${options.editingReceivingNo.value} 已修改`
        : `收货验收单已创建：${result.receivingNo}`
      options.showCreateModal.value = false
      options.editingReceivingNo.value = ''
      await options.reload()
    } catch (error) {
      options.message.value = error instanceof Error ? error.message : '收货单保存失败'
    } finally {
      submitting.value = false
    }
  }

  async function submitCreateAndContinue() {
    if (submitting.value) return
    submitting.value = true
    try {
      const result = await createReceivingOrder({ ...options.form, isAgent: undefined })
      options.message.value = `收货验收单已创建：${result.receivingNo}`
      options.resetForm()
      await options.reload()
    } catch (error) {
      options.message.value = error instanceof Error ? error.message : '收货单保存失败'
    } finally {
      submitting.value = false
    }
  }

  async function openEditModal(row: ReceivingOrderRow) {
    const data = await fetchReceivingOrderDetail(row.receivingNo, { page: 1, size: 200 })
    const allItems = [...data.items]
    const pages = Math.ceil(data.total / 200)
    for (let page = 2; page <= pages; page += 1) {
      const next = await fetchReceivingOrderDetail(row.receivingNo, { page, size: 200 })
      allItems.push(...next.items)
    }
    const order = data.order
    const orderExtra = order as unknown as Record<string, unknown>
    options.editingReceivingNo.value = row.receivingNo
    options.form.sourceType = String(orderExtra.sourceType ?? (order.purchaseOrderNo ? 'purchase_order' : 'temporary')) as 'purchase_order' | 'temporary'
    options.form.purchaseOrderNo = order.purchaseOrderNo || ''
    options.form.supplierId = Number(orderExtra.supplierId) || undefined
    options.form.supplierName = order.supplierName
    options.form.warehouseCode = String(orderExtra.warehouseCode ?? '')
    options.form.warehouseName = order.warehouseName
    options.form.receivingType = String(orderExtra.receivingType ?? '')
    options.form.isAgent = Boolean(Number(orderExtra.isAgent ?? 0) === 1)
    options.form.remark = String(orderExtra.remark ?? '')
    options.supplierSearchQuery.value = order.supplierName
    options.form.items = allItems.map((item) => {
      const quantity = Number(item.quantity ?? 1)
      const qualifiedQuantity = Number(item.qualifiedQuantity ?? quantity)
      return {
        productCode: String(item.productCode ?? ''),
        productionBatchNo: String(item.productionBatchNo ?? ''),
        udiCode: String(item.udiCode ?? ''),
        productionDate: String(item.productionDate ?? ''),
        expireDate: String(item.expireDate ?? ''),
        quantity,
        qualifiedQuantity,
        unqualifiedQuantity: Number(item.unqualifiedQuantity ?? Math.max(quantity - qualifiedQuantity, 0))
      }
    })
    if (!options.form.items.length) {
      options.addItem()
    }
    options.showCreateModal.value = true
  }

  return {
    submitting,
    fillFromPurchaseOrder,
    submitCreate,
    submitCreateAndContinue,
    openEditModal
  }
}
