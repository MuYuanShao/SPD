import { ref, type Ref } from 'vue'
import { saveQuotaSafety, type QuotaSafetyRow } from '../api/quotaPackages'

type SafetyCatalogQuery = {
  templateCode: string
  productName: string
}

type SafetyForm = {
  deptCode: string
  deptName: string
  warehouseName: string
  templateCode: string
  templateId?: number
  productCode: string
  minQty: number
  maxQty: number
}

export function useQuotaSafetyDialog(options: {
  safetyForm: SafetyForm
  message: Ref<string>
  safetyCatalogQuery: SafetyCatalogQuery
  resetSafetyCatalogQuery: () => void
  reload: () => Promise<void>
}) {
  const safetyDialogOpen = ref(false)
  const safetyDialogMode = ref<'create' | 'edit'>('create')
  const editingSafetyId = ref<number | null>(null)

  function fillSafety(row: QuotaSafetyRow) {
    editingSafetyId.value = row.safetyId
    options.safetyForm.deptName = row.deptName
    options.safetyForm.deptCode = row.deptCode
    options.safetyForm.warehouseName = ''
    options.safetyForm.templateCode = row.templateCode === '-' ? '' : row.templateCode
    options.safetyForm.templateId = row.templateId
    options.safetyForm.productCode = row.productCode
    options.safetyForm.minQty = Number(row.minQty)
    options.safetyForm.maxQty = Number(row.maxQty)
  }

  function resetSafetyForm() {
    editingSafetyId.value = null
    options.safetyForm.deptName = ''
    options.safetyForm.deptCode = ''
    options.safetyForm.warehouseName = ''
    options.safetyForm.templateCode = ''
    options.safetyForm.templateId = undefined
    options.safetyForm.productCode = ''
    options.safetyForm.minQty = 1
    options.safetyForm.maxQty = 3
  }

  function openSafetyDialog(row?: QuotaSafetyRow) {
    options.resetSafetyCatalogQuery()
    if (row) {
      safetyDialogMode.value = 'edit'
      fillSafety(row)
      options.safetyCatalogQuery.templateCode = row.templateCode === '-' ? '' : row.templateCode
      options.safetyCatalogQuery.productName = row.productName === '-' ? '' : row.productName
    } else {
      safetyDialogMode.value = 'create'
      resetSafetyForm()
    }
    safetyDialogOpen.value = true
  }

  function closeSafetyDialog() {
    safetyDialogOpen.value = false
    editingSafetyId.value = null
  }

  async function submitSafety() {
    const result = await saveQuotaSafety(options.safetyForm)
    options.message.value = `安全量${editingSafetyId.value ? '已修改' : '已保存'}：${result.deptName} / ${result.productCode}`
    editingSafetyId.value = null
    safetyDialogOpen.value = false
    await options.reload()
  }

  return {
    safetyDialogOpen,
    safetyDialogMode,
    editingSafetyId,
    fillSafety,
    resetSafetyForm,
    openSafetyDialog,
    closeSafetyDialog,
    submitSafety
  }
}
