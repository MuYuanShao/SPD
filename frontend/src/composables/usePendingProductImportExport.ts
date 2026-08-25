import { ref, type ComputedRef, type Ref } from 'vue'
import * as XLSX from 'xlsx'
import {
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

  function downloadTemplate() {
    const wb = XLSX.utils.book_new()
    const header = [
      '申请类型', '商品编码', '商品名称', '规格型号', '品牌',
      '生产厂家', '供应商', '单位', '采购价', '零售价',
      '最小采购量', '采购单位', '中包装数量', 'UDI编码',
      '注册证号', '注册证有效期', '生产许可证号', '经营许可证号',
      '合同编码', '招采子编码',
      '一级分类', '二级分类', '三级分类',
      '是否带量', '是否集采', '是否国产', '是否收费',
      '是否高值耗材', '是否冷链', '是否定数管理', '重点监控',
      '储存条件', '附件数量', '变更原因'
    ]
    const example = [
      '新品准入', 'P-NEW-003', '一次性使用输液器', '0.55mm', '康莱德',
      '康德莱器械', '九州通', '支', 1.60, 3.20,
      1, '盒', 1, '(01)06901234567890',
      '械注准20260003', '2029-12-31', 'XK-2025-00123', 'JJ-2025-00456',
      'HT-2025-888', 'TENDER-SUB-001',
      '一级分类', '二级分类', '三级分类',
      '是', '否', '是', '是',
      '否', '否', '是', '是',
      '常温', 3, ''
    ]
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([header, example]), '模板')
    XLSX.writeFile(wb, '待审批目录导入模板.xlsx')
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
