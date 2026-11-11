import { computed, reactive, ref, type Ref } from 'vue'
import { fetchQuotaTemplateChoices, type QuotaTemplateRow } from '../api/quotaPackages'

export function useQuotaTemplateSelector(options: {
  templates: Ref<QuotaTemplateRow[]>
  warehouses: Ref<Array<{ warehouseName: string; warehouseType: string }>>
  packingForm: {
    templateCode: string
    warehouseName: string
  }
}) {
  const templateSelectorOpen = ref(false)
  const templateSelectorLoading = ref(false)
  const templateSelectorError = ref('')
  const templateChoices = ref<QuotaTemplateRow[]>([])
  let requestVersion = 0
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
    templateChoices.value.filter((template) => {
      if (!showAllTemplates.value && template.status !== '启用' && template.status !== 'enabled') return false
      return (
        (!templateSelectorQuery.templateCode || template.templateCode.includes(templateSelectorQuery.templateCode)) &&
        (!templateSelectorQuery.templateName || template.templateName.includes(templateSelectorQuery.templateName))
      )
    })
  )

  async function loadTemplateChoices() {
    const request = ++requestVersion
    templateChoices.value = []
    templateSelectorLoading.value = true
    templateSelectorError.value = ''
    try {
      const rows = await fetchQuotaTemplateChoices()
      if (request === requestVersion) templateChoices.value = rows
    } catch (err) {
      if (request === requestVersion) templateSelectorError.value = err instanceof Error ? err.message : '打包模板加载失败'
    } finally {
      if (request === requestVersion) templateSelectorLoading.value = false
    }
  }

  function openTemplateSelector() {
    templateSelectorQuery.templateCode = ''
    templateSelectorQuery.templateName = ''
    templateSelectorOpen.value = true
    void loadTemplateChoices()
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
    templateSelectorLoading,
    templateSelectorError,
    loadTemplateChoices,
    templateSelectorQuery,
    showAllTemplates,
    filteredPackingTemplates,
    selectedPackingTemplate,
    openTemplateSelector,
    selectPackingTemplate,
    clearPackingTemplate
  }
}
