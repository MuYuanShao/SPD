import type { Ref } from 'vue'
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

export function useQuotaPackingTaskActions(options: {
  packingForm: PackingTaskForm
  message: Ref<string>
  taskLoading: Ref<boolean>
  selectedTaskNo: Ref<string>
  taskReservations: Ref<Record<string, unknown>[]>
  reload: () => Promise<void>
  showGeneratedLabels?: (labelNos: string[]) => Promise<void> | void
}) {
  async function submitPackingTask() {
    if (options.taskLoading.value) return
    if (!options.packingForm.templateCode) {
      options.message.value = '请先选择模板编码'
      return
    }
    options.taskLoading.value = true
    options.message.value = ''
    try {
      const result = await createPackingTask(options.packingForm)
      const reservedLooseQty = result.reservedLooseQty ?? result.plannedLooseQty
      const requestedCount = result.requestedPackageCount ?? options.packingForm.packageCount
      const actualCount = result.packageCount ?? requestedCount
      options.message.value = actualCount < requestedCount
        ? `散货库存不足，已按现有库存创建 ${actualCount}/${requestedCount} 包：${result.taskNo}，已锁定散货 ${reservedLooseQty}`
        : `打包任务已创建：${result.taskNo}，已锁定散货 ${reservedLooseQty}`
      await options.reload()
    } catch (error: any) {
      options.message.value = error?.response?.data?.message || error?.message || '创建打包任务失败，请重试'
    } finally {
      options.taskLoading.value = false
    }
  }

  async function approveTask(row: PackingTaskRow) {
    const result = await confirmPackingTask(row.taskNo)
    options.message.value = `${result.taskNo} 已确认，生成待打印标签 ${result.labels.join('、')}`
    await options.showGeneratedLabels?.(result.labels)
    await options.reload()
  }

  async function cancelTask(row: PackingTaskRow) {
    if (!window.confirm(`确认取消打包任务 ${row.taskNo} 并释放预占散货？`)) {
      return
    }
    const result = await cancelPackingTask(row.taskNo, '页面取消打包任务')
    options.message.value = `${result.taskNo} 已取消，预占散货已释放`
    options.selectedTaskNo.value = ''
    options.taskReservations.value = []
    await options.reload()
  }

  async function terminateTask(row: PackingTaskRow) {
    if (!window.confirm(`确认终止打包任务 ${row.taskNo}？系统会回滚未完成或已生成的定数包，并将库存退回中心库散货，可重新组包。`)) {
      return
    }
    const result = await terminatePackingTask(row.taskNo, '页面终止打包任务并退回散货库存')
    options.message.value = `${result.taskNo} 已终止，退回散货 ${result.restoredLooseQty}`
    options.selectedTaskNo.value = ''
    options.taskReservations.value = []
    await options.reload()
  }

  async function terminateTaskByTaskNo() {
    const taskNo = window.prompt('请输入需要终止的打包任务号', options.selectedTaskNo.value)
    if (!taskNo?.trim()) {
      return
    }
    if (!window.confirm(`确认终止打包任务 ${taskNo.trim()}？系统会回滚库存并退回中心库散货。`)) {
      return
    }
    const result = await terminatePackingTask(taskNo.trim(), '页面终止打包任务并退回散货库存')
    options.message.value = `${result.taskNo} 已终止，退回散货 ${result.restoredLooseQty}`
    options.selectedTaskNo.value = ''
    options.taskReservations.value = []
    await options.reload()
  }

  async function viewTaskReservations(row: PackingTaskRow) {
    options.selectedTaskNo.value = row.taskNo
    options.taskReservations.value = await fetchPackingTaskReservations(row.taskNo)
  }

  async function recalculateTask(row: PackingTaskRow) {
    const result = await recalculatePackingTask(row.taskNo)
    options.message.value = `${result.taskNo} 已重算，预占散货 ${result.reservedLooseQty}`
    await viewTaskReservations(row)
    await options.reload()
  }

  async function unpackLabel(row: PackageLabelRow) {
    if (!window.confirm(`确认将定数包标签 ${row.labelNo} 解包成散货？解包后原标签不可继续使用。`)) {
      return
    }
    try {
      const result = await unpackPackageLabel(row.labelNo, '页面操作解包回散货')
      options.message.value = `${result.labelNo} 已解包，恢复散货 ${result.restoredLooseQty}`
      await options.reload()
    } catch (error: any) {
      options.message.value = error?.response?.data?.message || error?.message || '定数包解包失败，请重试'
    }
  }

  async function printLabel(row: PackageLabelRow) {
    try {
      const printerName = await printQuotaLabel(row)
      const result = await printPackageLabel(row.labelNo)
      options.message.value = `${result.labelNo} 已发送至${printerName}，打印次数 ${result.printCount}`
      await options.reload()
    } catch (error: any) {
      options.message.value = error?.response?.data?.message || error?.message || '定数包标签打印失败，请重试'
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
