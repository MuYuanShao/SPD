import type { Ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  cancelPackingTask,
  confirmPackingTask,
  createPackingTask,
  fetchPackingTaskReservations,
  printPackageLabel,
  recalculatePackingTask,
  terminatePackingTask,
  unpackPackageLabel,
  type PackageLabelRow,
  type PackingTaskRow
} from '../api/quotaPackages'
import { printQuotaLabel } from '../utils/zebraBrowserPrint'

type PackingTaskForm = {
  templateCode: string
  warehouseName: string
  packageCount: number
  remark: string
}

async function confirmAction(message: string, title: string, confirmButtonText: string) {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText,
      cancelButtonText: '返回',
      type: 'warning'
    })
    return true
  } catch {
    return false
  }
}

export function useQuotaPackingTaskActions(options: {
  packingForm: PackingTaskForm
  /** 操作结果：tone=success 就地显示，tone=error 用 ElMessage 弹出 */
  message: Ref<{ text: string; tone: 'success' | 'error' | 'warning' }>
  taskLoading: Ref<boolean>
  selectedTaskNo: Ref<string>
  taskReservations: Ref<Record<string, unknown>[]>
  reload: () => Promise<void>
  showGeneratedLabels?: (labelNos: string[]) => Promise<void> | void
}) {
  function setResult(text: string, tone: 'success' | 'error' | 'warning' = 'success') {
    options.message.value = { text, tone }
  }
  async function submitPackingTask() {
    if (options.taskLoading.value) return
    if (!options.packingForm.templateCode) {
      setResult('请先选择打包模板')
      return
    }
    options.taskLoading.value = true
    options.message.value = { text: '', tone: 'success' }
    try {
      let result = await createPackingTask(options.packingForm)
      if (result.requiresConfirmation && !result.created) {
        const packable = result.packablePackageCount ?? 0
        const accepted = await confirmAction(
          `申请 ${result.requestedPackageCount} 包，当前仅可创建 ${packable} 包，缺口 ${result.shortagePackageCount ?? 0} 包（可用散货 ${result.availableLooseQty ?? 0}）。是否按当前库存创建？`,
          '散货库存不足',
          '确认部分创建'
        )
        if (!accepted) return
        result = await createPackingTask({
          ...options.packingForm,
          allowPartial: true,
          expectedPackableCount: packable
        })
        if (result.requiresConfirmation || !result.created) {
          setResult('库存已发生变化，请根据最新可打包数量再次确认', 'warning')
          return
        }
      }
      const reservedLooseQty = result.reservedLooseQty ?? result.plannedLooseQty
      const requestedCount = result.requestedPackageCount ?? options.packingForm.packageCount
      const actualCount = result.packageCount ?? requestedCount
      setResult(
        actualCount < requestedCount
          ? `已按当前库存部分创建 ${actualCount}/${requestedCount} 包，任务号 ${result.taskNo}，已锁定散货 ${reservedLooseQty}`
          : `打包任务创建成功：${result.taskNo}，已锁定散货 ${reservedLooseQty}`
      )
      await options.reload()
    } catch (error: any) {
      setResult(error?.response?.data?.message || error?.message || '创建打包任务失败，请重试', 'error')
    } finally {
      options.taskLoading.value = false
    }
  }

  async function approveTask(row: PackingTaskRow) {
    const result = await confirmPackingTask(row.taskNo)
    setResult(`${result.taskNo} 已确认，生成待打印标签 ${result.labels.join('、')}`)
    await options.showGeneratedLabels?.(result.labels)
    await options.reload()
  }

  async function cancelTask(row: PackingTaskRow) {
    if (!await confirmAction(`确认取消打包任务 ${row.taskNo} 并释放预占散货？`, '取消打包任务', '确认取消')) return
    const result = await cancelPackingTask(row.taskNo, '页面取消打包任务')
    setResult(`${result.taskNo} 已取消，预占散货已释放`)
    options.selectedTaskNo.value = ''
    options.taskReservations.value = []
    await options.reload()
  }

  async function terminateTask(row: PackingTaskRow) {
    if (!await confirmAction(`确认终止打包任务 ${row.taskNo}？仅未进入配送、签收、消耗或结算的标签可回退。`, '终止打包任务', '确认终止')) return
    const result = await terminatePackingTask(row.taskNo, '页面终止打包任务并退回散货库存')
    setResult(`${result.taskNo} 已终止，退回散货 ${result.restoredLooseQty}`)
    options.selectedTaskNo.value = ''
    options.taskReservations.value = []
    await options.reload()
  }

  async function terminateTaskByTaskNo() {
    const { value } = await ElMessageBox.prompt('请输入需要终止的打包任务号', '终止打包任务', {
      inputValue: options.selectedTaskNo.value,
      inputPattern: /\S+/,
      inputErrorMessage: '任务号不能为空',
      confirmButtonText: '下一步',
      cancelButtonText: '取消'
    })
    if (!await confirmAction(`确认终止打包任务 ${value.trim()}？仅可逆标签会退回散货。`, '确认终止', '确认终止')) return
    const result = await terminatePackingTask(value.trim(), '页面终止打包任务并退回散货库存')
    setResult(`${result.taskNo} 已终止，退回散货 ${result.restoredLooseQty}`)
    options.selectedTaskNo.value = ''
    options.taskReservations.value = []
    await options.reload()
  }

  async function viewTaskReservations(row: PackingTaskRow) {
    options.selectedTaskNo.value = row.taskNo
    options.taskReservations.value = []
    try {
      options.taskReservations.value = await fetchPackingTaskReservations(row.taskNo)
    } catch (error) {
      options.selectedTaskNo.value = ''
      setResult(error instanceof Error ? error.message : '预占明细加载失败', 'error')
    }
  }

  async function recalculateTask(row: PackingTaskRow) {
    const result = await recalculatePackingTask(row.taskNo)
    setResult(`${result.taskNo} 已重算，预占散货 ${result.reservedLooseQty}`)
    await viewTaskReservations(row)
    await options.reload()
  }

  async function unpackLabel(row: PackageLabelRow) {
    if (!await confirmAction(`确认将定数包标签 ${row.labelNo} 解包成散货？解包后原标签不可继续使用。`, '定数包解包', '确认解包')) return
    try {
      const result = await unpackPackageLabel(row.labelNo, '页面操作解包回散货')
      setResult(`${result.labelNo} 已解包，恢复散货 ${result.restoredLooseQty}`)
      await options.reload()
    } catch (error: any) {
      setResult(error?.response?.data?.message || error?.message || '定数包解包失败，请重试', 'error')
    }
  }

  async function printLabel(row: PackageLabelRow) {
    try {
      const printerName = await printQuotaLabel(row)
      const result = await printPackageLabel(row.labelNo)
      setResult(`${result.labelNo} 已发送至${printerName}，打印次数 ${result.printCount}`)
      await options.reload()
    } catch (error: any) {
      setResult(error?.response?.data?.message || error?.message || '定数包标签打印失败，请重试', 'error')
    }
  }

  return {
    submitPackingTask,
    approveTask,
    cancelTask,
    terminateTask,
    terminateTaskByTaskNo,
    recalculateTask,
    viewTaskReservations,
    unpackLabel,
    printLabel
  }
}
