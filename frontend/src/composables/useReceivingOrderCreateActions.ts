import type { Ref } from 'vue'
import {
  createReceivingOrder,
  fetchPurchaseOrderReceivingItems,
  fetchReceivingOrderDetail,
  updateReceivingOrder,
  type ReceivingOrderRow
} from '../api/receivingOrders'

type ReceivingForm = {
  purchaseOrderNo: string
  supplierName: string
  warehouseName: string
  receivingType: string
  isAgent: boolean
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
  async function fillFromPurchaseOrder() {
    if (!options.form.purchaseOrderNo) return
    const data = await fetchPurchaseOrderReceivingItems(options.form.purchaseOrderNo)
    const supplierName = String(data.order.supplierName ?? '')
    options.form.supplierName = supplierName
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
    const result = options.editingReceivingNo.value
      ? await updateReceivingOrder(options.editingReceivingNo.value, options.form)
      : await createReceivingOrder(options.form)
    options.message.value = options.editingReceivingNo.value
      ? `${options.editingReceivingNo.value} 已修改`
      : `收货验收单已创建：${result.receivingNo}`
    options.showCreateModal.value = false
    options.editingReceivingNo.value = ''
    await options.reload()
  }

  async function submitCreateAndContinue() {
    const result = await createReceivingOrder(options.form)
    options.message.value = `收货验收单已创建：${result.receivingNo}`
    options.resetForm()
    await options.reload()
  }

  async function openEditModal(row: ReceivingOrderRow) {
    const data = await fetchReceivingOrderDetail(row.receivingNo, { page: 1, size: 200 })
    const order = data.order
    options.editingReceivingNo.value = row.receivingNo
    options.form.purchaseOrderNo = order.purchaseOrderNo || ''
    options.form.supplierName = order.supplierName
    options.form.warehouseName = order.warehouseName
    const orderExtra = order as unknown as Record<string, unknown>
    options.form.receivingType = String(orderExtra.receivingType ?? '')
    options.form.isAgent = Boolean(Number(orderExtra.isAgent ?? 0) === 1)
    options.form.remark = String(orderExtra.remark ?? '')
    options.supplierSearchQuery.value = order.supplierName
    options.form.items = data.items.map((item) => {
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
    fillFromPurchaseOrder,
    submitCreate,
    submitCreateAndContinue,
    openEditModal
  }
}
