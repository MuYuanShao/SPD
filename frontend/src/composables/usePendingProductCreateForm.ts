import { reactive, ref, type Ref } from 'vue'
import {
  createPendingProductApplication,
  type PendingProductApplicationPayload
} from '../api/pendingProductApplications'

export function usePendingProductCreateForm(options: {
  message: Ref<string>
  showCreateModal: Ref<boolean>
  reload: () => Promise<void>
}) {
  const createForm = reactive<PendingProductApplicationPayload>(emptyCreateForm())
  const createAttachments = ref<File[]>([])
  const attachmentInput = ref<HTMLInputElement | null>(null)

  function resetCreateForm() {
    Object.assign(createForm, emptyCreateForm())
    createAttachments.value = []
    if (attachmentInput.value) {
      attachmentInput.value.value = ''
    }
  }

  function handleCreateAttachments(event: Event) {
    const files = Array.from((event.target as HTMLInputElement).files ?? [])
    createAttachments.value = files
    createForm.qualificationAttachmentCount = files.length
  }

  function removeCreateAttachment(index: number) {
    createAttachments.value = createAttachments.value.filter((_, fileIndex) => fileIndex !== index)
    createForm.qualificationAttachmentCount = createAttachments.value.length
    if (!createAttachments.value.length && attachmentInput.value) {
      attachmentInput.value.value = ''
    }
  }

  async function submitCreateForm() {
    createForm.qualificationAttachmentCount = createAttachments.value.length
    const result = await createPendingProductApplication(createForm)
    const codeSuffix = result.productCode ? `（商品编码 ${result.productCode}）` : ''
    options.message.value = `已生成待审批单：${result.applicationNo}${codeSuffix}`
    options.showCreateModal.value = false
    resetCreateForm()
    await options.reload()
  }

  return {
    createForm,
    createAttachments,
    attachmentInput,
    resetCreateForm,
    handleCreateAttachments,
    removeCreateAttachment,
    submitCreateForm
  }
}

function emptyCreateForm(): PendingProductApplicationPayload {
  return {
    applicationType: '新品准入',
    productCode: '',
    productName: '',
    specModel: '',
    brand: '',
    manufacturerName: '',
    supplierName: '',
    unit: '支',
    purchasePrice: 0,
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
    qualificationAttachmentCount: 0,
    highValue: false,
    coldChain: false,
    quotaManaged: false,
    storageCondition: '常温',
    changeReason: ''
  }
}
