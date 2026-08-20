import type { Ref } from 'vue'
import { fetchQuotaSafety, saveQuotaSafety, type QuotaSafetyRow } from '../api/quotaPackages'
import { csvCell, downloadCsvContent, parseCsvLine } from '../utils/downloadCsv'

type SafetyQuery = {
  deptName: string
  productCode: string
  productName: string
}

type SafetyPagination = {
  page: number
  size: number
  total: number
}

export function useQuotaSafetyImportExport(options: {
  safetyImportInput: Ref<HTMLInputElement | null>
  safetyImporting: Ref<boolean>
  safetyRows: Ref<QuotaSafetyRow[]>
  safetyPagination: SafetyPagination
  query: SafetyQuery
  message: Ref<string>
  reload: () => Promise<void>
}) {
  function triggerSafetyImport() {
    options.safetyImportInput.value?.click()
  }

  function downloadSafetyImportFile() {
    downloadCsvContent('quota-safety-import-template.csv', '科室名称,模板编码,商品编码,安全下限,安全上限\n骨科,,P0001,1,3')
  }

  async function exportSafetyRows() {
    const exportData = await fetchQuotaSafety({
      page: '1',
      size: String(Math.max(options.safetyPagination.total || options.safetyRows.value.length || 20, 20)),
      deptName: options.query.deptName,
      productCode: options.query.productCode,
      productName: options.query.productName
    })
    const header = ['科室名称', '模板编码', '商品编码', '商品名称', '安全下限', '安全上限', '状态', '更新时间']
    const lines = exportData.rows.map((item) =>
      [
        item.deptName,
        item.templateCode,
        item.productCode,
        item.productName,
        item.minQty,
        item.maxQty,
        item.status,
        item.updateTime
      ]
        .map(csvCell)
        .join(',')
    )
    downloadCsvContent('quota-safety-export.csv', [header.join(','), ...lines].join('\n'))
  }

  async function handleSafetyImport(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file || options.safetyImporting.value) return
    options.safetyImporting.value = true
    try {
      const text = await file.text()
      const rows = text.split(/\r?\n/).slice(1).filter((line) => line.trim())
      let successCount = 0
      let skippedCount = 0
      let failedCount = 0
      for (const line of rows) {
        const [deptName, templateCode, productCode, minQtyText, maxQtyText] = parseCsvLine(line)
        const minQty = Number(minQtyText || 0)
        const maxQty = Number(maxQtyText || minQty)
        if (!deptName || !productCode || !Number.isFinite(minQty) || !Number.isFinite(maxQty)) {
          skippedCount += 1
          continue
        }
        try {
          await saveQuotaSafety({
            deptName,
            templateCode: templateCode || undefined,
            productCode,
            minQty,
            maxQty
          })
          successCount += 1
        } catch {
          failedCount += 1
        }
      }
      options.message.value = `已导入 ${successCount} 条安全量配置${skippedCount ? `，跳过 ${skippedCount} 行` : ''}${failedCount ? `，失败 ${failedCount} 行` : ''}`
      options.safetyPagination.page = 1
      await options.reload()
    } catch (error: any) {
      options.message.value = error?.response?.data?.message || error?.message || '导入失败，请检查 CSV 内容'
    } finally {
      options.safetyImporting.value = false
      input.value = ''
    }
  }

  return {
    triggerSafetyImport,
    downloadSafetyImportFile,
    exportSafetyRows,
    handleSafetyImport
  }
}
