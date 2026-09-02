import { computed, reactive, type Ref } from 'vue'
import type { QuotaTemplateRow } from '../api/quotaPackages'

export function useQuotaSafetyCatalog(options: {
  templates: Ref<QuotaTemplateRow[]>
  safetyForm: {
    deptName: string
    templateCode: string
    templateId?: number
    productCode: string
  }
}) {
  const safetyCatalogQuery = reactive({ templateCode: '', templateName: '', deptName: '', productName: '' })
  const safetyCatalogPagination = reactive({ page: 1, size: 5 })

  const filteredSafetyTemplates = computed(() =>
    options.templates.value.filter((template) => {
      return (
        (!safetyCatalogQuery.templateCode || template.templateCode.includes(safetyCatalogQuery.templateCode)) &&
        (!safetyCatalogQuery.templateName || template.templateName.includes(safetyCatalogQuery.templateName)) &&
        (!safetyCatalogQuery.deptName || (template.deptName ?? '').includes(safetyCatalogQuery.deptName)) &&
        (!safetyCatalogQuery.productName || template.productName.includes(safetyCatalogQuery.productName))
      )
    })
  )

  const pagedSafetyTemplates = computed(() => {
    const start = (safetyCatalogPagination.page - 1) * safetyCatalogPagination.size
    return filteredSafetyTemplates.value.slice(start, start + safetyCatalogPagination.size)
  })

  function resetSafetyCatalogQuery() {
    safetyCatalogQuery.templateCode = ''
    safetyCatalogQuery.templateName = ''
    safetyCatalogQuery.deptName = ''
    safetyCatalogQuery.productName = ''
    safetyCatalogPagination.page = 1
  }

  function selectSafetyTemplate(row: QuotaTemplateRow) {
    options.safetyForm.templateCode = row.templateCode
    options.safetyForm.templateId = row.templateId
    options.safetyForm.productCode = row.productCode
  }

  function changeSafetyCatalogPage(page: number) {
    safetyCatalogPagination.page = page
  }

  function changeSafetyCatalogPageSize(size: number) {
    safetyCatalogPagination.size = size
    safetyCatalogPagination.page = 1
  }

  return {
    safetyCatalogQuery,
    safetyCatalogPagination,
    filteredSafetyTemplates,
    pagedSafetyTemplates,
    resetSafetyCatalogQuery,
    selectSafetyTemplate,
    changeSafetyCatalogPage,
    changeSafetyCatalogPageSize
  }
}
