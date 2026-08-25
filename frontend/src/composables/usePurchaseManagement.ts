import { computed, onMounted, reactive, ref } from 'vue'
import {
  addPurchaseOrderRemark,
  createPurchaseDemand,
  createPurchaseOrder,
  createPurchasePlansFromDemands,
  fetchPurchaseDemands,
  fetchPurchaseOrderAttachmentBlob,
  fetchPurchaseOrderAttachments,
  fetchPurchaseOrderDetail,
  fetchPurchaseOptions,
  fetchPurchaseOrders,
  fetchPurchasePlans,
  fetchPurchaseSmartReplenishmentAnalysis,
  uploadPurchaseOrderAttachment,
  updatePurchaseDemandAction,
  updatePurchaseOrderAction,
  updatePurchasePlanAction,
  type PurchaseDemandRow,
  type PurchaseOrderRow,
  type PurchaseOrderAttachment,
  type PurchaseOrderDetail,
  type PurchasePlanRow,
  type PurchaseProductOption,
  type PurchaseSmartReplenishmentRow,
  type PurchaseSupplierOption
} from '../api/purchaseOrders'
import { formatStatusText } from '../utils/chineseDisplay'

export type PurchaseTab = 'smart' | 'demands' | 'plans' | 'orders' | 'tracking'

interface DemandGroup {
  demandNo: string
  demandSource: string
  demandStatus: string
  urgentLevel: string
  deptName?: string
  createTime: string
  items: PurchaseDemandRow[]
  totalQuantity: number
  totalSuggestedQty: number
  itemCount: number
}

export function usePurchaseManagement() {
  const activeTab = ref<PurchaseTab>('demands')
  const rows = ref<PurchaseOrderRow[]>([])
  const demands = ref<PurchaseDemandRow[]>([])
  const plans = ref<PurchasePlanRow[]>([])
  const products = ref<PurchaseProductOption[]>([])
  const suppliers = ref<PurchaseSupplierOption[]>([])
  const smartRows = ref<PurchaseSmartReplenishmentRow[]>([])
  const smartPeriods = ref<number[]>([5, 15, 30, 45, 60])
  const smartSelectedPeriod = ref(30)
  const smartAnalysisNo = ref('')
  const smartTotalFormulaQty = ref(0)
  const smartTotalRecommendedQty = ref(0)
  const showSmartAnalysisDialog = ref(false)
  const smartAnalysisLoading = ref(false)
  const smartAnalysisError = ref('')
  const summary = ref<Record<string, number>>({})
  const loading = ref(false)
  const message = ref('')
  const purchasePagination = reactive({
    demands: { page: 1, size: 20, total: 0 },
    plans: { page: 1, size: 20, total: 0 },
    orders: { page: 1, size: 20, total: 0 }
  })
  const showCreateModal = ref(false)
  const showDemandModal = ref(false)
  const closeTarget = ref<PurchaseOrderRow | null>(null)
  const closeReason = ref('')
  const detail = ref<PurchaseOrderDetail | null>(null)
  const selectedOrder = ref<PurchaseOrderRow | null>(null)
  const showOrderDetailDialog = ref(false)
  const orderOperationMode = ref<'void' | 'remark' | null>(null)
  const orderOperationText = ref('')
  const showOrderAttachmentDialog = ref(false)
  const orderAttachments = ref<PurchaseOrderAttachment[]>([])
  const orderAttachmentsLoading = ref(false)
  const orderAttachmentPreviewUrl = ref('')
  const orderAttachmentPreviewType = ref('')
  const orderAttachmentPreviewName = ref('')

  const query = reactive({
    orderNo: '',
    supplierName: '',
    status: '',
    keyword: '',
    demandNo: '',
    planNo: ''
  })

  const form = reactive({
    supplierName: '',
    orderSource: '临时采购',
    expectedArrivalDate: '',
    items: [{ productCode: '', quantity: 1, unit: '', estimatedUnitPrice: 0 }]
  })

  const productSearchQuery = ref('')
  const demandProductSearchQuery = ref('')

  const demandForm = reactive({
    deptName: '',
    demandSource: '临时采购',
    urgentLevel: 'normal',
    remark: '',
    items: [{ productCode: '', quantity: 1 }]
  })

  const planForm = reactive({
    supplierName: '',
    remark: '由已审核采购需求生成'
  })

  const demandGroups = computed<DemandGroup[]>(() => {
    const groups = new Map<string, DemandGroup>()
    for (const row of demands.value) {
      let group = groups.get(row.demandNo)
      if (!group) {
        group = {
          demandNo: row.demandNo,
          demandSource: row.demandSource,
          demandStatus: row.demandStatus,
          urgentLevel: row.urgentLevel,
          deptName: row.deptName,
          createTime: row.createTime,
          items: [],
          totalQuantity: 0,
          totalSuggestedQty: 0,
          itemCount: 0
        }
        groups.set(row.demandNo, group)
      }
      group.items.push(row)
      group.totalQuantity += Number(row.quantity)
      group.totalSuggestedQty += Number(row.suggestedPurchaseQty)
      group.itemCount++
    }
    return Array.from(groups.values())
  })

  const showDemandDetailDialog = ref(false)
  const selectedDemandGroup = ref<DemandGroup | null>(null)

  const tabs: Array<{ key: PurchaseTab; label: string }> = [
    { key: 'smart', label: '智能补货' },
    { key: 'demands', label: '采购需求池' },
    { key: 'plans', label: '采购计划' },
    { key: 'orders', label: '采购订单' },
    { key: 'tracking', label: '订单跟踪' }
  ]

  const statusOptions = [
    { value: '', label: '全部' },
    { value: 'draft', label: '草稿' },
    { value: 'pending_review', label: '待审核' },
    { value: 'pending_approval', label: '待审批' },
    { value: 'approved', label: '已审核' },
    { value: 'planned', label: '已转计划' },
    { value: 'executed', label: '已转订单' },
    { value: 'sent', label: '已发送' },
    { value: 'closed', label: '已关闭' },
    { value: 'voided', label: '已作废' },
    { value: 'rejected', label: '已驳回' }
  ]

  const demandStatusText: Record<string, string> = {
    draft: '草稿',
    pending_review: '待审核',
    approved: '已审核',
    planned: '已转计划',
    rejected: '已驳回'
  }

  const planStatusText: Record<string, string> = {
    draft: '草稿',
    approved: '已审核',
    executed: '已转订单',
    rejected: '已驳回'
  }

  const orderStatusText: Record<string, string> = {
    draft: '草稿',
    pending_approval: '待审批',
    approved: '已审批',
    sent: '已发送',
    closed: '已关闭',
    voided: '已作废',
    rejected: '已驳回'
  }

  const stats = computed(() => [
    { label: '智能建议', value: smartRows.value.filter((row) => Number(row.recommendedQty) > 0).length },
    { label: '采购需求', value: demandGroups.value.length },
    { label: '采购计划', value: plans.value.length },
    { label: '采购订单', value: summary.value.totalOrders ?? rows.value.length },
    { label: '订单金额', value: `¥ ${Number(summary.value.totalAmount ?? 0).toFixed(2)}` }
  ])

  const actionQueryLabel = computed(() => {
    if (activeTab.value === 'smart') return '智能分析'
    if (activeTab.value === 'plans') return '计划查询'
    if (activeTab.value === 'orders') return '订单查询'
    if (activeTab.value === 'tracking') return '刷新跟踪'
    return '查询'
  })

  const filteredProducts = computed(() => filterProducts(products.value, productSearchQuery.value))
  const demandFilteredProducts = computed(() => filterProducts(products.value, demandProductSearchQuery.value))
  const demandValidItemCount = computed(() => demandForm.items.filter((item) => item.productCode).length)
  const demandEstimatedAmount = computed(() =>
    demandForm.items.reduce((total, item) => {
      const product = productByCode(item.productCode)
      return total + Number(item.quantity || 0) * Number(product?.purchasePrice || 0)
    }, 0)
  )

  function openDemandDetail(group: DemandGroup) {
    selectedDemandGroup.value = group
    showDemandDetailDialog.value = true
  }

  function statusLabel(status: string) {
    return orderStatusText[status] ?? demandStatusText[status] ?? planStatusText[status] ?? formatStatusText(status)
  }

  function statusTone(status: string) {
    if (['closed', 'sent', 'approved', 'executed', 'planned'].includes(status)) return 'enabled'
    if (['rejected', 'voided'].includes(status)) return 'disabled'
    return 'pending'
  }

  function remainingQty(row: PurchaseOrderRow) {
    return Number(row.remainingQuantity ?? Number(row.orderQuantity || 0) - Number(row.receivedQuantity || 0))
  }

  function canCloseOrder(row: PurchaseOrderRow) {
    return ['approved', 'sent'].includes(row.orderStatus) && remainingQty(row) <= 0
  }

  function productByCode(code: string) {
    return products.value.find((item) => item.productCode === code)
  }

  function fillProduct(index: number) {
    const product = productByCode(form.items[index].productCode)
    if (!product) return
    form.items[index].unit = product.unit
    form.items[index].estimatedUnitPrice = Number(product.purchasePrice)
  }

  function addItem() {
    form.items.push({ productCode: '', quantity: 1, unit: '', estimatedUnitPrice: 0 })
  }

  function removeItem(index: number) {
    if (form.items.length > 1) form.items.splice(index, 1)
  }

  function resetOrderForm() {
    form.supplierName = ''
    form.orderSource = '临时采购'
    form.expectedArrivalDate = ''
    form.items = [{ productCode: '', quantity: 1, unit: '', estimatedUnitPrice: 0 }]
    productSearchQuery.value = ''
  }

  function resetDemandForm() {
    demandForm.deptName = ''
    demandForm.demandSource = '临时采购'
    demandForm.urgentLevel = 'normal'
    demandForm.remark = ''
    demandForm.items = [{ productCode: '', quantity: 1 }]
    demandProductSearchQuery.value = ''
  }

  async function loadData() {
    loading.value = true
    try {
      const results = await Promise.allSettled([
        fetchPurchaseOrders({
          orderNo: query.orderNo,
          supplierName: query.supplierName,
          status: query.status,
          keyword: query.keyword,
          page: String(purchasePagination.orders.page),
          size: String(purchasePagination.orders.size)
        }),
        fetchPurchaseOptions(),
        fetchPurchaseDemands({
          demandNo: query.demandNo,
          keyword: query.keyword,
          status: query.status,
          page: String(purchasePagination.demands.page),
          size: String(purchasePagination.demands.size)
        }),
        fetchPurchasePlans({
          planNo: query.planNo,
          keyword: query.keyword,
          status: query.status,
          page: String(purchasePagination.plans.page),
          size: String(purchasePagination.plans.size)
        })
      ])
      const [orderResult, optionResult, demandResult, planResult] = results

      if (orderResult.status === 'fulfilled') {
        rows.value = orderResult.value.rows
        if (selectedOrder.value) {
          selectedOrder.value = rows.value.find((row) => row.orderNo === selectedOrder.value?.orderNo) ?? null
        }
        purchasePagination.orders.total = orderResult.value.total
        summary.value = orderResult.value.summary ?? {}
      } else {
        console.error('Failed to load orders:', orderResult.reason)
      }
      if (optionResult.status === 'fulfilled') {
        suppliers.value = optionResult.value.suppliers
        products.value = optionResult.value.products
      } else {
        console.error('Failed to load options:', optionResult.reason)
      }
      if (demandResult.status === 'fulfilled') {
        demands.value = demandResult.value.rows
        purchasePagination.demands.total = demandResult.value.total
      } else {
        console.error('Failed to load demands:', demandResult.reason)
      }
      if (planResult.status === 'fulfilled') {
        plans.value = planResult.value.rows
        purchasePagination.plans.total = planResult.value.total
      } else {
        console.error('Failed to load plans:', planResult.reason)
      }
      message.value = results.some((result) => result.status === 'rejected') ? '部分数据加载失败，请刷新重试' : ''
    } finally {
      loading.value = false
    }
  }

  async function changePurchasePage(tab: 'demands' | 'plans' | 'orders', page: number) {
    const state = purchasePagination[tab]
    const totalPages = Math.max(Math.ceil(state.total / Math.max(state.size, 1)), 1)
    const nextPage = Math.min(Math.max(page, 1), totalPages)
    if (nextPage === state.page) return
    state.page = nextPage
    await loadData()
  }

  async function changePurchasePageSize(tab: 'demands' | 'plans' | 'orders', size: number) {
    const state = purchasePagination[tab]
    if (state.size === size) return
    state.size = size
    state.page = 1
    await loadData()
  }

  async function submitCreate() {
    try {
      const result = await createPurchaseOrder(form)
      message.value = `采购订单已创建：${result.orderNo}`
      showCreateModal.value = false
      activeTab.value = 'orders'
      await loadData()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function submitDemand(continueAfterSave = false) {
    try {
      if (!demandForm.deptName.trim()) {
        message.value = '请填写申请科室'
        return
      }
      if (!demandForm.demandSource.trim()) {
        message.value = '请填写采购来源'
        return
      }
      const validItems = demandForm.items.filter((item) => item.productCode)
      if (validItems.length === 0) {
        message.value = '请至少添加一条商品明细'
        return
      }
      if (validItems.some((item) => Number(item.quantity) <= 0)) {
        message.value = '商品采购数量必须大于 0'
        return
      }
      const mergedMap = new Map<string, number>()
      for (const item of validItems) {
        mergedMap.set(item.productCode, (mergedMap.get(item.productCode) || 0) + item.quantity)
      }
      const mergedItems = Array.from(mergedMap.entries()).map(([productCode, quantity]) => ({ productCode, quantity }))
      const result = await createPurchaseDemand({
        deptName: demandForm.deptName,
        demandSource: demandForm.demandSource,
        urgentLevel: demandForm.urgentLevel,
        remark: demandForm.remark,
        items: mergedItems
      })
      message.value = `采购申请已保存，需求编号：${result.demandNo}`
      if (continueAfterSave) {
        resetDemandForm()
      } else {
        showDemandModal.value = false
      }
      activeTab.value = 'demands'
      await loadData()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  function addDemandItem() {
    const lastItem = demandForm.items[demandForm.items.length - 1]
    if (lastItem.productCode) {
      const existing = demandForm.items.find(
        (item, index) => index !== demandForm.items.length - 1 && item.productCode === lastItem.productCode
      )
      if (existing) {
        existing.quantity += lastItem.quantity
        demandForm.items.pop()
      }
    }
    demandForm.items.push({ productCode: '', quantity: 1 })
  }

  function removeDemandItem(index: number) {
    if (demandForm.items.length > 1) demandForm.items.splice(index, 1)
  }

  async function generatePlans() {
    try {
      const result = await createPurchasePlansFromDemands({
        supplierName: planForm.supplierName || undefined,
        remark: planForm.remark
      })
      message.value = `已根据审核通过需求生成 ${result.createdPlans} 条采购计划`
      activeTab.value = 'plans'
      await loadData()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function runSmartAnalysis() {
    smartAnalysisLoading.value = true
    smartAnalysisError.value = ''
    showSmartAnalysisDialog.value = true
    smartRows.value = []
    smartAnalysisNo.value = ''
    smartTotalFormulaQty.value = 0
    smartTotalRecommendedQty.value = 0
    try {
      const result = await fetchPurchaseSmartReplenishmentAnalysis(smartSelectedPeriod.value)
      smartRows.value = result.rows
      smartPeriods.value = result.periodDays
      smartSelectedPeriod.value = result.selectedPeriodDays
      smartAnalysisNo.value = result.analysisNo
      smartTotalFormulaQty.value = Number(result.totalFormulaQty || 0)
      smartTotalRecommendedQty.value = Number(result.totalRecommendedQty || 0)
      message.value = `已完成近${result.selectedPeriodDays}天一级库智能补货分析，分析编号：${result.analysisNo}，建议补货 ${smartTotalRecommendedQty.value}`
    } catch (error) {
      smartAnalysisError.value = errorMessage(error)
      message.value = smartAnalysisError.value
    } finally {
      smartAnalysisLoading.value = false
    }
  }

  function updateSmartRecommendedQty(row: PurchaseSmartReplenishmentRow, event: Event) {
    const input = event.target as HTMLInputElement
    const value = Number(input.value)
    row.recommendedQty = Number.isFinite(value) && value >= 0 ? value : 0
    smartTotalRecommendedQty.value = smartRows.value
      .map((item) => Number(item.recommendedQty || 0))
      .reduce((total, qty) => total + qty, 0)
  }

  async function createDemandFromSmartAnalysis() {
    const mergedItems = new Map<string, number>()
    for (const row of smartRows.value) {
      const qty = Number(row.recommendedQty)
      if (qty > 0) {
        mergedItems.set(row.productCode, (mergedItems.get(row.productCode) || 0) + qty)
      }
    }
    const items = Array.from(mergedItems.entries()).map(([productCode, quantity]) => ({
      productCode,
      quantity
    }))
    if (items.length === 0) {
      message.value = '暂无可生成采购需求的补货建议'
      return
    }
    try {
      const result = await createPurchaseDemand({
        demandSource: '智能补货分析',
        urgentLevel: 'normal',
        remark: `近${smartSelectedPeriod.value}天一级库出二级库智能分析生成`,
        items
      })
      message.value = `已根据智能补货分析生成采购需求：${result.demandNo}`
      showSmartAnalysisDialog.value = false
      activeTab.value = 'demands'
      await loadData()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function runOrderAction(row: PurchaseOrderRow, action: string) {
    try {
      const result = await updatePurchaseOrderAction(row.orderNo, action, `${statusLabel(row.orderStatus)} -> ${action}`)
      message.value = `${row.orderNo} 已更新为 ${statusLabel(result.status)}`
      await loadData()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  function requestClose(row: PurchaseOrderRow) {
    if (!canCloseOrder(row)) {
      const remaining = remainingQty(row)
      message.value = `${row.orderNo} 尚未完成数量履约，剩余 ${remaining} 未收货。请先在“收货验收”完成入库后再关闭。`
      void openDetail(row)
      return
    }
    closeTarget.value = row
    closeReason.value = '数量履约完成，采购订单关闭'
  }

  async function submitClose() {
    if (!closeTarget.value) return
    try {
      const result = await updatePurchaseOrderAction(closeTarget.value.orderNo, 'close', closeReason.value)
      message.value = `${closeTarget.value.orderNo} 已更新为 ${statusLabel(result.status)}`
      closeTarget.value = null
      closeReason.value = ''
      await loadData()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function runDemandAction(row: { demandNo: string; demandStatus: string }, action: string) {
    try {
      const result = await updatePurchaseDemandAction(row.demandNo, action, `${statusLabel(row.demandStatus)} -> ${action}`)
      message.value = `${row.demandNo} 已更新为 ${statusLabel(result.status)}`
      await loadData()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function runPlanAction(row: PurchasePlanRow, action: string) {
    try {
      const result = await updatePurchasePlanAction(row.planNo, action, `${statusLabel(row.planStatus)} -> ${action}`)
      message.value = result.orderNo
        ? `${row.planNo} 已转采购订单：${result.orderNo}`
        : `${row.planNo} 已更新为 ${statusLabel(result.status)}`
      await loadData()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  function selectOrder(row: PurchaseOrderRow) {
    selectedOrder.value = row
  }

  async function openDetail(row: PurchaseOrderRow) {
    try {
      selectOrder(row)
      detail.value = await fetchPurchaseOrderDetail(row.orderNo)
      showOrderDetailDialog.value = true
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function openTracking(row: PurchaseOrderRow) {
    try {
      selectOrder(row)
      detail.value = await fetchPurchaseOrderDetail(row.orderNo)
      activeTab.value = 'tracking'
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function runSelectedOrderAction(action: 'submit' | 'approve') {
    if (!selectedOrder.value) {
      message.value = '请先选择一条采购订单'
      return
    }
    await runOrderAction(selectedOrder.value, action)
  }

  function openOrderOperation(mode: 'void' | 'remark') {
    if (!selectedOrder.value) {
      message.value = '请先选择一条采购订单'
      return
    }
    orderOperationMode.value = mode
    orderOperationText.value = ''
  }

  async function submitOrderOperation() {
    if (!selectedOrder.value || !orderOperationMode.value) return
    if (!orderOperationText.value.trim()) {
      message.value = orderOperationMode.value === 'void' ? '请填写作废原因' : '请填写订单备注'
      return
    }
    try {
      if (orderOperationMode.value === 'void') {
        const result = await updatePurchaseOrderAction(selectedOrder.value.orderNo, 'void', orderOperationText.value.trim())
        message.value = `${selectedOrder.value.orderNo} 已更新为 ${statusLabel(result.status)}`
      } else {
        await addPurchaseOrderRemark(selectedOrder.value.orderNo, orderOperationText.value.trim())
        message.value = `${selectedOrder.value.orderNo} 已添加备注`
      }
      orderOperationMode.value = null
      orderOperationText.value = ''
      await loadData()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function copySelectedOrder() {
    if (!selectedOrder.value) {
      message.value = '请先选择一条采购订单'
      return
    }
    try {
      const source = await fetchPurchaseOrderDetail(selectedOrder.value.orderNo)
      resetOrderForm()
      form.supplierName = source.order.supplierName
      form.orderSource = source.order.orderSource || '复制订单'
      form.expectedArrivalDate = source.order.expectedArrivalDate || ''
      form.items = source.items.map((item) => ({
        productCode: item.productCode,
        quantity: Number(item.quantity),
        unit: item.unit,
        estimatedUnitPrice: Number(item.estimatedUnitPrice)
      }))
      if (form.items.length === 0) addItem()
      showCreateModal.value = true
      message.value = `已复制 ${source.order.orderNo}，保存后生成新订单`
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function loadOrderAttachments() {
    if (!selectedOrder.value) return
    orderAttachmentsLoading.value = true
    try {
      orderAttachments.value = await fetchPurchaseOrderAttachments(selectedOrder.value.orderNo)
    } catch (error) {
      message.value = errorMessage(error)
    } finally {
      orderAttachmentsLoading.value = false
    }
  }

  async function openOrderAttachments() {
    if (!selectedOrder.value) {
      message.value = '请先选择一条采购订单'
      return
    }
    closeOrderAttachmentPreview()
    showOrderAttachmentDialog.value = true
    await loadOrderAttachments()
  }

  async function uploadSelectedOrderAttachment(file: File) {
    if (!selectedOrder.value) {
      message.value = '请先选择一条采购订单'
      return
    }
    try {
      await uploadPurchaseOrderAttachment(selectedOrder.value.orderNo, file)
      message.value = `${selectedOrder.value.orderNo} 附件上传成功`
      if (showOrderAttachmentDialog.value) await loadOrderAttachments()
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  async function previewOrderAttachment(attachment: PurchaseOrderAttachment) {
    try {
      const { blob, contentType } = await fetchPurchaseOrderAttachmentBlob(attachment.id)
      closeOrderAttachmentPreview()
      orderAttachmentPreviewUrl.value = URL.createObjectURL(blob)
      orderAttachmentPreviewType.value = contentType
      orderAttachmentPreviewName.value = attachment.fileName
    } catch (error) {
      message.value = errorMessage(error)
    }
  }

  function closeOrderAttachmentPreview() {
    if (orderAttachmentPreviewUrl.value) URL.revokeObjectURL(orderAttachmentPreviewUrl.value)
    orderAttachmentPreviewUrl.value = ''
    orderAttachmentPreviewType.value = ''
    orderAttachmentPreviewName.value = ''
  }

  function closeOrderAttachmentDialog() {
    closeOrderAttachmentPreview()
    showOrderAttachmentDialog.value = false
  }

  onMounted(loadData)

  return {
    activeTab,
    rows,
    demands,
    plans,
    smartRows,
    smartPeriods,
    smartSelectedPeriod,
    smartAnalysisNo,
    smartTotalFormulaQty,
    smartTotalRecommendedQty,
    showSmartAnalysisDialog,
    smartAnalysisLoading,
    smartAnalysisError,
    products,
    suppliers,
    summary,
    loading,
    message,
    purchasePagination,
    showCreateModal,
    showDemandModal,
    closeTarget,
    closeReason,
    detail,
    selectedOrder,
    showOrderDetailDialog,
    orderOperationMode,
    orderOperationText,
    showOrderAttachmentDialog,
    orderAttachments,
    orderAttachmentsLoading,
    orderAttachmentPreviewUrl,
    orderAttachmentPreviewType,
    orderAttachmentPreviewName,
    query,
    form,
    productSearchQuery,
    demandProductSearchQuery,
    demandForm,
    planForm,
    demandGroups,
    showDemandDetailDialog,
    selectedDemandGroup,
    tabs,
    statusOptions,
    stats,
    actionQueryLabel,
    filteredProducts,
    demandFilteredProducts,
    demandValidItemCount,
    demandEstimatedAmount,
    productByCode,
    openDemandDetail,
    statusLabel,
    statusTone,
    remainingQty,
    canCloseOrder,
    fillProduct,
    addItem,
    removeItem,
    resetOrderForm,
    resetDemandForm,
    loadData,
    changePurchasePage,
    changePurchasePageSize,
    submitCreate,
    submitDemand,
    addDemandItem,
    removeDemandItem,
    generatePlans,
    runSmartAnalysis,
    updateSmartRecommendedQty,
    createDemandFromSmartAnalysis,
    runOrderAction,
    requestClose,
    submitClose,
    runDemandAction,
    runPlanAction,
    openDetail,
    openTracking,
    selectOrder,
    runSelectedOrderAction,
    openOrderOperation,
    submitOrderOperation,
    copySelectedOrder,
    openOrderAttachments,
    uploadSelectedOrderAttachment,
    previewOrderAttachment,
    closeOrderAttachmentDialog
  }
}

function filterProducts(products: PurchaseProductOption[], query: string) {
  const normalized = query.trim().toLowerCase()
  if (!normalized) return products
  return products.filter(
    (product) =>
      product.productCode.toLowerCase().includes(normalized) ||
      product.productName.toLowerCase().includes(normalized) ||
      (product.specModel && product.specModel.toLowerCase().includes(normalized))
  )
}

function errorMessage(error: unknown) {
  const data = (error as { response?: { data?: { message?: string } } }).response?.data
  return data?.message || (error instanceof Error ? error.message : '操作失败')
}
