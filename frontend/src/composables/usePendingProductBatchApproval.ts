import { ref, type Ref } from 'vue'
import { isAxiosError } from 'axios'
import { useCatalogApprovalRefreshStore } from '../stores/catalogApprovalRefresh'
import { useNotify } from './useNotify'

export type PendingProductBatchAction = 'approve' | 'return' | 'reject'

type BatchApproveApi = (
  applicationNos: string[],
  action: PendingProductBatchAction,
  opinion: string
) => Promise<{ successCount: number; failCount: number; failedNos?: string[] }>

export function usePendingProductBatchApproval(options: {
  selectedNos: Ref<string[]>
  error: Ref<string>
  message: Ref<string>
  batchApprove: BatchApproveApi
  reload: () => Promise<void>
  readStatus: (applicationNo: string) => Promise<{ statusLabel: string; approvalStatus: string; updateTime: string }>
}) {
  const refreshStore = useCatalogApprovalRefreshStore()
  const notify = useNotify()
  const batchApproveLoading = ref(false)
  const showBatchApproveModal = ref(false)
  const batchApproveAction = ref<PendingProductBatchAction>('approve')
  const batchApproveOpinion = ref('')
  const batchApproveError = ref('')
  const uncertainNos = ref<string[]>([])
  const checkingStatuses = ref(false)
  const checkedStatuses = ref<Array<{ no: string; status: string; updateTime?: string }>>([])
  let checkVersion = 0

  function openBatchApprove(action: PendingProductBatchAction) {
    if (batchApproveLoading.value || !options.selectedNos.value.length) return
    batchApproveAction.value = action
    batchApproveOpinion.value = ''
    batchApproveError.value = ''
    uncertainNos.value = []
    checkedStatuses.value = []
    checkingStatuses.value = false
    checkVersion++
    showBatchApproveModal.value = true
  }

  async function confirmBatchApprove() {
    if (batchApproveLoading.value || uncertainNos.value.length || !options.selectedNos.value.length) return
    if (
      (batchApproveAction.value === 'return' || batchApproveAction.value === 'reject') &&
      !batchApproveOpinion.value.trim()
    ) {
      batchApproveError.value =
        batchApproveAction.value === 'return' ? '退回修改必须填写退回原因' : '驳回必须填写审批意见'
      return
    }

    batchApproveLoading.value = true
    options.error.value = ''
    batchApproveError.value = ''
    const submittedNos = [...options.selectedNos.value]
    try {
      const result = await options.batchApprove(
        submittedNos,
        batchApproveAction.value,
        batchApproveOpinion.value.trim()
      )
      // The batch API only returns counts, so conservatively invalidate catalog readers after any success.
      if (result.successCount > 0) refreshStore.changed({ catalogChanged: true })
      const actionLabel =
        batchApproveAction.value === 'approve' ? '审批通过' : batchApproveAction.value === 'return' ? '退回修改' : '驳回'
      options.message.value = `已${actionLabel} ${result.successCount} 条${
        result.failCount > 0 ? `，${result.failCount} 条操作失败` : ''
      }`
      showBatchApproveModal.value = false
      options.selectedNos.value = result.failedNos ?? (result.failCount > 0 ? submittedNos : [])
      await options.reload()
    } catch (err) {
      if (isAxiosError(err) && (!err.response || err.response.status >= 500)) {
        const timedOut = err.code === 'ECONNABORTED' || err.code === 'ETIMEDOUT'
        batchApproveError.value = `${timedOut ? '批量审批请求超时，' : ''}未收到完整审批结果，部分单据可能已处理。请先核对当前状态，避免重复提交。`
        notify.warning(batchApproveError.value)
        uncertainNos.value = submittedNos
        options.selectedNos.value = []
        refreshStore.changed({ catalogChanged: true })
        void checkBatchStatuses()
      } else {
        batchApproveError.value = err instanceof Error ? err.message : '批量审批操作失败'
      }
    } finally {
      batchApproveLoading.value = false
    }
  }

  async function checkBatchStatuses() {
    if (checkingStatuses.value || !uncertainNos.value.length) return
    const version = ++checkVersion
    const numbers = [...uncertainNos.value]
    checkingStatuses.value = true
    try {
      await options.reload()
      const statuses: typeof checkedStatuses.value = []
      for (let index = 0; index < numbers.length && version === checkVersion; index += 5) {
        statuses.push(...await Promise.all(numbers.slice(index, index + 5).map(async no => {
          try {
            const detail = await options.readStatus(no)
            return { no, status: detail.statusLabel || detail.approvalStatus, updateTime: detail.updateTime }
          } catch {
            return { no, status: '暂未查到，请稍后核对' }
          }
        })))
      }
      if (version === checkVersion) checkedStatuses.value = statuses
    } finally {
      if (version === checkVersion) checkingStatuses.value = false
    }
  }

  return {
    batchApproveLoading,
    showBatchApproveModal,
    batchApproveAction,
    batchApproveOpinion,
    batchApproveError,
    uncertainNos,
    checkingStatuses,
    checkedStatuses,
    checkBatchStatuses,
    openBatchApprove,
    confirmBatchApprove
  }
}
