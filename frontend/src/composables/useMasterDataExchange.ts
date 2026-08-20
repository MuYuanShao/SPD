import type { ComputedRef, Ref } from 'vue'
import {
  exportDepartments,
  exportManufacturers,
  exportSuppliers,
  exportWarehouses,
  importDepartments,
  importManufacturers,
  importSuppliers,
  importWarehouses,
  type MasterDataQuery
} from '../api/masterData'
import { downloadCsv } from '../utils/downloadCsv'

type ExchangeKind = 'supplier' | 'manufacturer' | 'department' | 'warehouse'

interface ExchangeDeps {
  queryParams: ComputedRef<MasterDataQuery>
  importInput: Ref<HTMLInputElement | null>
  manufacturerImportInput: Ref<HTMLInputElement | null>
  departmentImportInput: Ref<HTMLInputElement | null>
  warehouseImportInput: Ref<HTMLInputElement | null>
  actionError: Ref<string>
  actionMessage: Ref<string>
  clearActionState: () => void
  loadPage: () => Promise<void>
}

const templates: Record<ExchangeKind, { fileName: string; rows: Record<string, unknown>[] }> = {
  supplier: {
    fileName: '供应商导入模板.csv',
    rows: [{
      code: 'SUP-001',
      name: '示例供应商',
      creditCode: '913100000000000000',
      type: '配送商',
      grade: 'A',
      contactName: '张三',
      contactPhone: '13800000000',
      email: 'demo@example.com',
      address: '示例地址'
    }]
  },
  manufacturer: {
    fileName: '厂家导入模板.csv',
    rows: [{
      code: 'MFR-001',
      name: '示例厂家',
      creditCode: '913100000000000000',
      licenseNo: '沪械生产备20260001',
      contactName: '张三',
      contactPhone: '13800000000',
      address: '示例地址'
    }]
  },
  department: {
    fileName: '科室导入模板.csv',
    rows: [{
      code: 'DEPT-001',
      name: '示例科室',
      financeDeptCode: 'FIN-001',
      financeDept: '示例财务科室',
      campus: '主院区',
      address: '门诊三楼',
      manager: '张三',
      phone: '13800000000'
    }]
  },
  warehouse: {
    fileName: '库房导入模板.csv',
    rows: [{
      code: 'WH-001',
      name: '示例中心库',
      type: '中心库',
      campus: '主院区',
      dept: '骨科',
      participateStats: '参与',
      statsCategories: '耗材',
      locationCode: 'A-01-01',
      locationType: '整件位',
      capacityLimit: '1000',
      productCode: '',
      locationStatus: '启用'
    }]
  }
}

const labels: Record<ExchangeKind, string> = {
  supplier: '供应商',
  manufacturer: '厂家',
  department: '科室',
  warehouse: '库房'
}

const exportFileNames: Record<ExchangeKind, string> = {
  supplier: '供应商管理导出.csv',
  manufacturer: '厂家管理导出.csv',
  department: '科室管理导出.csv',
  warehouse: '库房货位管理导出.csv'
}

export function useMasterDataExchange(deps: ExchangeDeps) {
  const importInputs: Record<ExchangeKind, Ref<HTMLInputElement | null>> = {
    supplier: deps.importInput,
    manufacturer: deps.manufacturerImportInput,
    department: deps.departmentImportInput,
    warehouse: deps.warehouseImportInput
  }

  const exporters: Record<ExchangeKind, (query: MasterDataQuery) => Promise<Record<string, unknown>[]>> = {
    supplier: exportSuppliers,
    manufacturer: exportManufacturers,
    department: exportDepartments,
    warehouse: exportWarehouses
  }

  const importers: Record<
    ExchangeKind,
    (file: File) => Promise<{ importedRows: number; importedLocations?: number }>
  > = {
    supplier: importSuppliers,
    manufacturer: importManufacturers,
    department: importDepartments,
    warehouse: importWarehouses
  }

  function downloadTemplate(kind: ExchangeKind) {
    const template = templates[kind]
    downloadCsv(template.fileName, template.rows)
  }

  async function exportRows(kind: ExchangeKind) {
    deps.clearActionState()
    try {
      const rows = await exporters[kind](deps.queryParams.value)
      downloadCsv(exportFileNames[kind], rows)
      deps.actionMessage.value = `已导出 ${rows.length} 条${labels[kind]}`
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : `${labels[kind]}导出失败`
    }
  }

  function triggerImport(kind: ExchangeKind) {
    importInputs[kind].value?.click()
  }

  async function handleImport(kind: ExchangeKind, event: Event) {
    deps.clearActionState()
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return

    try {
      const result = await importers[kind](file)
      const locationMessage = kind === 'warehouse'
        ? `，其中货位 ${result.importedLocations ?? 0} 条`
        : ''
      deps.actionMessage.value = `已导入 ${result.importedRows} 条${labels[kind]}${locationMessage}`
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : `${labels[kind]}导入失败`
    } finally {
      input.value = ''
    }
  }

  return {
    downloadSupplierTemplate: () => downloadTemplate('supplier'),
    exportSupplierRows: () => exportRows('supplier'),
    triggerSupplierImport: () => triggerImport('supplier'),
    handleSupplierImport: (event: Event) => handleImport('supplier', event),
    downloadManufacturerTemplate: () => downloadTemplate('manufacturer'),
    exportManufacturerRows: () => exportRows('manufacturer'),
    triggerManufacturerImport: () => triggerImport('manufacturer'),
    handleManufacturerImport: (event: Event) => handleImport('manufacturer', event),
    downloadDepartmentTemplate: () => downloadTemplate('department'),
    exportDepartmentRows: () => exportRows('department'),
    triggerDepartmentImport: () => triggerImport('department'),
    handleDepartmentImport: (event: Event) => handleImport('department', event),
    downloadWarehouseTemplate: () => downloadTemplate('warehouse'),
    exportWarehouseRows: () => exportRows('warehouse'),
    triggerWarehouseImport: () => triggerImport('warehouse'),
    handleWarehouseImport: (event: Event) => handleImport('warehouse', event)
  }
}
