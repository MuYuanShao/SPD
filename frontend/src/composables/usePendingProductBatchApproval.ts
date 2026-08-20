import { ref, type Ref } from 'vue'

export type PendingProductBatchAction = 'approve' | 'return' | 'reject'

type BatchApproveApi = (
  applicationNos: string[],
  action: PendingProductBatchAction,
  opinion: string
) => Promise<{ successCount: number; failCount: number }>

export function usePendingProductBatchApproval(options: {
  selectedNos: Ref<string[]>
  error: Ref<string>
  message: Ref<string>
  batchApprove: BatchApproveApi
  reload: () => Promise<void>
}) {
  const batchApproveLoading = ref(false)
  const showBatchApproveModal = ref(false)
  const batchApproveAction = ref<PendingProductBatchAction>('approve')
  const batchApproveOpinion = ref('')

  function openBatchApprove(action: PendingProductBatchAction) {
    if (!options.selectedNos.value.length) return
    batchApproveAction.value = action
    batchApproveOpinion.value = ''
    showBatchApproveModal.value = true
  }

  async function confirmBatchApprove() {
    if (!options.selectedNos.value.length) return
    if (
      (batchApproveAction.value === 'return' || batchApproveAction.value === 'reject') &&
      !batchApproveOpinion.value.trim()
    ) {
      options.error.value =
        batchApproveAction.value === 'return' ? '退回修改必须填写退回原因' : '驳回必须填写审批意见'
      return
    }

    batchApproveLoading.value = true
    options.error.value = ''
    try {
      const result = await options.batchApprove(
        options.selectedNos.value,
        batchApproveAction.value,
        batchApproveOpinion.value.trim()
      )
      const actionLabel =
        batchApproveAction.value === 'approve' ? '审批通过' : batchApproveAction.value === 'return' ? '退回修改' : '驳回'
      options.message.value = `已${actionLabel} ${result.successCount} 条${
        result.failCount > 0 ? `，${result.failCount} 条操作失败` : ''
      }`
      showBatchApproveModal.value = false
      options.selectedNos.value = []
      await options.reload()
    } catch (err) {
      options.error.value = err instanceof Error ? err.message : '批量审批操作失败'
    } finally {
      batchApproveLoading.value = false
    }
  }

  return {
    batchApproveLoading,
    showBatchApproveModal,
    batchApproveAction,
    batchApproveOpinion,
    openBatchApprove,
    confirmBatchApprove
  }
}
