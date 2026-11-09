import { computed, reactive, ref, type Ref } from 'vue'
import type { QuotaTemplateRow } from '../api/quotaPackages'

export function useQuotaTemplateSelector(options: {
  templates: Ref<QuotaTemplateRow[]>
  warehouses: Ref<Array<{ warehouseName: string; warehouseType: string }>>
  packingForm: {
    templateCode: string
    warehouseName: string
  }
}) {
  const templateSelectorOpen = ref(false)
  const templateSelectorQuery = reactive({ templateCode: '', templateName: '' })
  const showAllTemplates = ref(false)
  const lastSelectedTemplate = ref<QuotaTemplateRow>()
  const selectedPackingTemplate = computed(() => {
    const code = options.packingForm.templateCode
    const selected = lastSelectedTemplate.value
    if (selected?.templateCode === code) return selected
    return options.templates.value.find(row => row.templateCode === code)
  })

  const filteredPackingTemplates = computed(() =>
    options.templates.value.filter((template) => {
      if (!showAllTemplates.value && template.status !== '启用' && template.status !== 'enabled') return false
      return (
        (!templateSelectorQuery.templateCode || template.templateCode.includes(templateSelectorQuery.templateCode)) &&
        (!templateSelectorQuery.templateName || template.templateName.includes(templateSelectorQuery.templateName))
      )
    })
  )

  function openTemplateSelector() {
    templateSelectorQuery.templateCode = ''
    templateSelectorQuery.templateName = ''
    templateSelectorOpen.value = true
  }

  function selectPackingTemplate(row: QuotaTemplateRow) {
    lastSelectedTemplate.value = row
    options.packingForm.templateCode = row.templateCode
    if (!options.packingForm.warehouseName && options.warehouses.value.length) {
      options.packingForm.warehouseName = options.warehouses.value[0].warehouseName
    }
    templateSelectorOpen.value = false
  }

  function clearPackingTemplate() {
    options.packingForm.templateCode = ''
    lastSelectedTemplate.value = undefined
  }

  return {
    templateSelectorOpen,
    templateSelectorQuery,
    showAllTemplates,
    filteredPackingTemplates,
    selectedPackingTemplate,
    openTemplateSelector,
    selectPackingTemplate,
    clearPackingTemplate
  }
}
