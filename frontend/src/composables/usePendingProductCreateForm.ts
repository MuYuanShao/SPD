import { reactive, ref, type Ref } from 'vue'
import {
  createPendingProductApplication,
  uploadPendingProductAttachment,
  type PendingProductApplicationPayload
} from '../api/pendingProductApplications'
import { notifySuccess } from './useNotify'

export function usePendingProductCreateForm(options: {
  message: Ref<string>
  showCreateModal: Ref<boolean>
  reload: () => Promise<void>
}) {
  const createForm = reactive<PendingProductApplicationPayload>(emptyCreateForm())
  const createAttachments = ref<File[]>([])
  const attachmentInput = ref<HTMLInputElement | null>(null)
  const createdApplicationNo = ref('')
  const createdProductCode = ref('')

  function resetCreateForm() {
    Object.assign(createForm, emptyCreateForm())
    createAttachments.value = []
    createdApplicationNo.value = ''
    createdProductCode.value = ''
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
    if (!createdApplicationNo.value) {
      createForm.qualificationAttachmentCount = 0
      const result = await createPendingProductApplication(createForm)
      createdApplicationNo.value = result.applicationNo
      createdProductCode.value = result.productCode ?? ''
    }
    const failedFiles: File[] = []
    for (const file of createAttachments.value) {
      try {
        await uploadPendingProductAttachment(createdApplicationNo.value, file)
      } catch {
        failedFiles.push(file)
      }
    }
    if (failedFiles.length) {
      createAttachments.value = failedFiles
      const text = `审批单 ${createdApplicationNo.value} 已创建，${failedFiles.length} 个附件上传失败；请再次提交重试附件`
      // 失败信息由调用方（视图层）通过 Element Plus message 统一抛出，
      // 这里只负责抛出异常，不再重复提示，避免同一错误弹两次。
      throw new Error(text)
    }
    const codeSuffix = createdProductCode.value ? `（商品编码 ${createdProductCode.value}）` : ''
    // 「新增成功」以 Element Plus message 提示，不占用页面布局
    notifySuccess(`已生成待审批单：${createdApplicationNo.value}${codeSuffix}`)
    options.showCreateModal.value = false
    resetCreateForm()
    await options.reload()
  }

  return {
    createForm,
    createAttachments,
    attachmentInput,
    createdApplicationNo,
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
    keyMonitored: false,
    storageCondition: '常温',
    changeReason: ''
  }
}
