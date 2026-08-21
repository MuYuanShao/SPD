import type { ComputedRef, Ref } from 'vue'
import {
  disableQuotaTemplate,
  enableQuotaTemplate,
  saveQuotaTemplate,
  type QuotaTemplateRow
} from '../api/quotaPackages'
import { downloadCsvContent } from '../utils/downloadCsv'

type TemplateForm = {
  templateCode: string
  templateName: string
  deptName?: string
  productCode: string
  quantity: number
  unit: string
}

type PackingForm = {
  templateCode: string
}

type ProductInfo = {
  productName: string
  specModel: string
  brand: string
  manufacturerName: string
  unit: string
  purchasePrice: string
}

export function useQuotaTemplateActions(options: {
  templates: Ref<QuotaTemplateRow[]>
  selectedTemplates: ComputedRef<QuotaTemplateRow[]>
  selectedTemplateCodes: Ref<string[]>
  templateForm: TemplateForm
  packingForm: PackingForm
  selectedProductInfo: Ref<ProductInfo | null>
  templateDialogOpen: Ref<boolean>
  templateDialogMode: Ref<'create' | 'edit'>
  templateError: Ref<string>
  importInput: Ref<HTMLInputElement | null>
  message: Ref<string>
  reload: () => Promise<void>
}) {
  function fillTemplate(row: QuotaTemplateRow) {
    options.templateForm.templateCode = row.templateCode
    options.templateForm.templateName = row.templateName
    options.templateForm.productCode = row.productCode
    options.templateForm.quantity = Number(row.quantity)
    options.templateForm.unit = row.unit
    options.selectedProductInfo.value = {
      productName: row.productName,
      specModel: row.specModel,
      brand: '',
      manufacturerName: row.manufacturerName === '-' ? '' : row.manufacturerName,
      unit: row.unit,
      purchasePrice: ''
    }
    options.packingForm.templateCode = row.templateCode
  }

  function resetTemplateForm() {
    options.templateForm.templateCode = ''
    options.templateForm.templateName = ''
    options.templateForm.productCode = ''
    options.templateForm.quantity = 1
    options.templateForm.unit = ''
    options.selectedProductInfo.value = null
    options.templateError.value = ''
  }

  function openCreateTemplate() {
    options.templateDialogMode.value = 'create'
    resetTemplateForm()
    options.templateDialogOpen.value = true
  }

  function openEditTemplate(row?: QuotaTemplateRow) {
    const target = row || options.selectedTemplates.value[0]
    if (!target) {
      options.message.value = '请先选择一条定数包模板记录'
      return
    }
    options.templateDialogMode.value = 'edit'
    fillTemplate(target)
    options.templateError.value = ''
    options.templateDialogOpen.value = true
  }

  function hasDuplicateTemplate() {
    return options.templates.value.some((item) => {
      const sameRecord = item.templateCode === options.templateForm.templateCode
      const sameProduct = item.productCode.trim() === options.templateForm.productCode.trim()
      return !sameRecord && sameProduct
    })
  }

  async function submitTemplate() {
    options.templateError.value = ''
    if (hasDuplicateTemplate()) {
      options.templateError.value = '已有记录存在：相同商品的定数包模板已存在'
      return
    }
    try {
      const result = await saveQuotaTemplate(options.templateForm)
      options.message.value = `定数包模板已保存：${result.templateCode}`
      options.templateDialogOpen.value = false
      await options.reload()
    } catch (error: any) {
      options.templateError.value = error?.response?.data?.message || '保存失败，请检查定数包模板信息'
    }
  }

  async function disableSelectedTemplates(row?: QuotaTemplateRow) {
    const rows = row ? [row] : options.selectedTemplates.value
    if (!rows.length) {
      options.message.value = '请先选择需要停用的定数包模板'
      return
    }
    if (!window.confirm(`确认停用 ${rows.length} 条定数包模板？`)) {
      return
    }
    for (const item of rows) {
      await disableQuotaTemplate(item.templateCode)
    }
    options.selectedTemplateCodes.value = []
    options.message.value = `已停用 ${rows.length} 条定数包模板`
    await options.reload()
  }

  async function enableSelectedTemplates(row?: QuotaTemplateRow) {
    const rows = row ? [row] : options.selectedTemplates.value.filter((item) => item.status === '禁用')
    if (!rows.length) {
      options.message.value = '请先选择需要启用的定数包模板'
      return
    }
    if (!window.confirm(`确认启用 ${rows.length} 条定数包模板？`)) {
      return
    }
    try {
      for (const item of rows) {
        await enableQuotaTemplate(item.templateCode)
      }
      options.selectedTemplateCodes.value = []
      options.message.value = `已启用 ${rows.length} 条定数包模板`
      await options.reload()
    } catch (error: any) {
      options.message.value = error?.response?.data?.message || error?.message || '启用失败，请重试'
    }
  }

  function exportTemplates() {
    const header = ['模板编码', '模板名称', '商品编码', '商品名称', '数量', '单位', '状态']
    const lines = options.templates.value.map((item) =>
      [item.templateCode, item.templateName, item.productCode, item.productName, item.quantity, item.unit, item.status]
        .map((value) => `"${String(value ?? '').replace(/"/g, '""')}"`)
        .join(',')
    )
    downloadCsvContent('quota-template-export.csv', [header.join(','), ...lines].join('\n'))
  }

  function downloadTemplateImportFile() {
    downloadCsvContent('quota-template-import-template.csv', '模板编码,商品编码,数量,单位\n,P0001,10,支')
  }

  function triggerTemplateImport() {
    options.importInput.value?.click()
  }

  async function handleTemplateImport(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      const rows = text.split(/\r?\n/).slice(1).filter(Boolean)
      let successCount = 0
      for (const line of rows) {
        const [templateCode, productCode, quantity, unit] = line
          .split(',')
          .map((item) => item.replace(/^"|"$/g, '').trim())
        if (!productCode) continue
        await saveQuotaTemplate({
          templateCode,
          productCode,
          quantity: Number(quantity || 1),
          unit
        })
        successCount += 1
      }
      options.message.value = `已按模板导入 ${successCount} 条定数包模板`
      await options.reload()
    } catch (error: any) {
      options.message.value = error?.response?.data?.message || '导入失败，请检查模板内容'
    } finally {
      input.value = ''
    }
  }

  return {
    fillTemplate,
    resetTemplateForm,
    openCreateTemplate,
    openEditTemplate,
    submitTemplate,
    disableSelectedTemplates,
    enableSelectedTemplates,
    exportTemplates,
    downloadTemplateImportFile,
    triggerTemplateImport,
    handleTemplateImport
  }
}
