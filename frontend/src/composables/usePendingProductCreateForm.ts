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
  const createForm = reactive<PendingProductApplicationPayload>({
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
  })
  const createAttachments = ref<File[]>([])
  const attachmentInput = ref<HTMLInputElement | null>(null)

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
    options.message.value = `已生成待审批单：${result.applicationNo}`
    options.showCreateModal.value = false
    await options.reload()
  }

  return {
    createForm,
    createAttachments,
    attachmentInput,
    handleCreateAttachments,
    removeCreateAttachment,
    submitCreateForm
  }
}
