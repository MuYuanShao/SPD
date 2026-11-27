import { onUnmounted, reactive, ref, type Ref } from 'vue'
import {
  createPendingProductApplication,
  uploadPendingProductAttachment,
  type PendingProductApplicationPayload
} from '../api/pendingProductApplications'
import { notifySuccess, notifyWarning } from './useNotify'

export interface CreateAttachmentPreview {
  /** 原始文件 */
  file: File
  /** 本地预览地址（仅图片有值），组件卸载时释放 */
  url: string
  /** 是否为可直接渲染的图片 */
  isImage: boolean
}

export function usePendingProductCreateForm(options: {
  message: Ref<string>
  showCreateModal: Ref<boolean>
  reload: () => Promise<void>
}) {
  const createForm = reactive<PendingProductApplicationPayload>(emptyCreateForm())
  const createAttachments = ref<File[]>([])
  /** 已选附件的本地预览（图片用 blob URL 渲染，PDF 只显示文件信息） */
  const createAttachmentPreviews = ref<CreateAttachmentPreview[]>([])
  const attachmentInput = ref<HTMLInputElement | null>(null)
  const createdApplicationNo = ref('')
  const createdProductCode = ref('')

  /** 判断文件是否可在浏览器内直接预览（图片）。 */
  function isPreviewableImage(file: File) {
    if (file.type && file.type.startsWith('image/')) return true
    return /\.(png|jpe?g|gif|bmp|webp)$/i.test(file.name)
  }

  /** 释放全部本地预览的 blob URL，避免内存泄漏。 */
  function releaseAttachmentPreviews() {
    createAttachmentPreviews.value.forEach((item) => {
      if (item.url) URL.revokeObjectURL(item.url)
    })
    createAttachmentPreviews.value = []
  }

  /** 依据当前附件列表重建预览（图片生成 blob URL）。 */
  function rebuildAttachmentPreviews() {
    releaseAttachmentPreviews()
    createAttachmentPreviews.value = createAttachments.value.map((file) => {
      const isImage = isPreviewableImage(file)
      return {
        file,
        isImage,
        url: isImage ? URL.createObjectURL(file) : ''
      }
    })
  }

  function resetCreateForm() {
    Object.assign(createForm, emptyCreateForm())
    createAttachments.value = []
    releaseAttachmentPreviews()
    createdApplicationNo.value = ''
    createdProductCode.value = ''
    if (attachmentInput.value) {
      attachmentInput.value.value = ''
    }
  }

  /**
   * 选择文件：**追加**到已有附件列表（而不是覆盖）。
   * ---------------------------------------------------------------------
   * 需求：新增弹窗允许连续多次挑选多张图片 / 多份 PDF，
   * 每一次选择都在原有基础上继续累加，用户不会因为重新选文件而丢掉已选项。
   * 同一个文件（名称 + 大小 + 修改时间一致）不重复加入。
   */
  function handleCreateAttachments(event: Event) {
    const input = event.target as HTMLInputElement
    const picked = Array.from(input.files ?? [])
    if (picked.length) {
      const existing = new Set(
        createAttachments.value.map(
          (file) => `${file.name}::${file.size}::${file.lastModified}`
        )
      )
      const merged = [...createAttachments.value]
      picked.forEach((file) => {
        const signature = `${file.name}::${file.size}::${file.lastModified}`
        if (existing.has(signature)) return
        existing.add(signature)
        merged.push(file)
      })
      createAttachments.value = merged
      createForm.qualificationAttachmentCount = merged.length
      rebuildAttachmentPreviews()
    }
    // 清空 input 的 value，保证连续选择同一个文件时也能再次触发 change
    if (input) input.value = ''
  }

  function removeCreateAttachment(index: number) {
    createAttachments.value = createAttachments.value.filter((_, fileIndex) => fileIndex !== index)
    createForm.qualificationAttachmentCount = createAttachments.value.length
    rebuildAttachmentPreviews()
    if (!createAttachments.value.length && attachmentInput.value) {
      attachmentInput.value.value = ''
    }
  }

  // 页面卸载时释放本地预览的 blob URL，避免内存泄漏
  onUnmounted(() => {
    releaseAttachmentPreviews()
  })

  async function submitCreateForm() {
    if (!createdApplicationNo.value) {
      createForm.qualificationAttachmentCount = 0
      const result = await createPendingProductApplication(createForm)
      createdApplicationNo.value = result.applicationNo
      createdProductCode.value = result.productCode ?? ''
    }
    const failedFiles: File[] = []
    const failureReasons: string[] = []
    for (const file of createAttachments.value) {
      try {
        await uploadPendingProductAttachment(createdApplicationNo.value, file)
      } catch (error) {
        failedFiles.push(file)
        const reason = resolveUploadReason(error)
        if (reason) failureReasons.push(`${file.name}：${reason}`)
      }
    }
    if (failedFiles.length) {
      createAttachments.value = failedFiles
      rebuildAttachmentPreviews()
      // 附件上传失败：审批单已生成，用户可以再次提交仅重试附件。
      // 失败原因通过 Element Plus message 抛出（不使用页面内联提示）。
      const reasonText = failureReasons.length ? `（${failureReasons.join('；')}）` : ''
      notifyWarning(
        `审批单 ${createdApplicationNo.value} 已创建，${failedFiles.length} 个附件上传失败${reasonText}；请修正后再次提交重试附件`
      )
      const text = `审批单 ${createdApplicationNo.value} 已创建，${failedFiles.length} 个附件上传失败；请再次提交重试附件`
      // 抛出异常供调用方感知失败状态，但此处已通过 message 提示，不再重复弹一次。
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
    createAttachmentPreviews,
    attachmentInput,
    createdApplicationNo,
    resetCreateForm,
    handleCreateAttachments,
    removeCreateAttachment,
    submitCreateForm
  }
}

/**
 * 从附件上传异常里提取可读的中文原因。
 * 优先取后端 ApiResponse 的 message（文件签名/扩展名不符、超限等），
 * 其次取 Error.message，最后回退到通用文案。
 */
function resolveUploadReason(error: unknown): string {
  const anyError = error as any
  const fromResponse = anyError?.response?.data?.message
  if (typeof fromResponse === 'string' && fromResponse.trim()) return fromResponse
  if (typeof anyError?.message === 'string' && anyError.message.trim()) return anyError.message
  return '附件上传失败，请稍后重试'
}

function emptyCreateForm(): PendingProductApplicationPayload {
  return {
    applicationType: '新品准入',
    productCode: '',
    productName: '',
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
