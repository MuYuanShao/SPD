import { ref, type ComputedRef, type Ref } from 'vue'
import {
  downloadPendingProductImportTemplate,
  exportPendingProductApplications,
  importPendingProductApplications
} from '../api/pendingProductApplications'
import { downloadCsvContent } from '../utils/downloadCsv'

interface UsePendingProductImportExportOptions {
  activeTypeKey: Ref<string> | ComputedRef<string>
  activeScope: Ref<string> | ComputedRef<string>
  keyword: Ref<string>
  message: Ref<string>
  error: Ref<string>
  reload: () => Promise<void>
}

export function usePendingProductImportExport({
  activeTypeKey,
  activeScope,
  keyword,
  message,
  error,
  reload
}: UsePendingProductImportExportOptions) {
  const importInput = ref<HTMLInputElement | null>(null)

  async function downloadTemplate() {
    const blob = await downloadPendingProductImportTemplate()
    const href = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = href
    link.download = '待审批目录导入模板.xlsx'
    link.click()
    URL.revokeObjectURL(href)
  }

  async function handleImportFile(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return

    message.value = ''
    error.value = ''

    try {
      const result = await importPendingProductApplications(file)
      const duplicateText = result.duplicateRows ? `，${result.duplicateRows} 行目录已存在被跳过` : ''
      const otherSkipped = (result.skippedRows ?? 0) - (result.duplicateRows ?? 0)
      const skippedText = otherSkipped > 0 ? `，${otherSkipped} 行格式异常被跳过` : ''
      message.value = result.message || `已导入 ${result.importedRows} 条待审批单${duplicateText}${skippedText}`
      await reload()
    } catch (err) {
      message.value = err instanceof Error ? err.message : '导入失败'
    } finally {
      input.value = ''
    }
  }

  async function exportRows() {
    const rows = await exportPendingProductApplications(activeTypeKey.value, activeScope.value, keyword.value)
    const content = [
      '审批单号,申请类型,商品名称,申请方,申请人,时间,状态',
      ...rows.map((row) => [row.no, row.type, row.product, row.supplier, row.applicant, row.time, row.status].join(','))
    ].join('\n')
    downloadCsvContent('待审批目录.csv', content)
  }

  return {
    importInput,
    downloadTemplate,
    handleImportFile,
    exportRows
  }
}
