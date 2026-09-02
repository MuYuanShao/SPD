import type { Ref } from 'vue'
import {
  fetchPackageEvents,
  fetchPackageLabels,
  fetchPackingOptions,
  fetchPackingTasks,
  fetchQuotaSafety,
  fetchQuotaTemplates,
  type PackageLabelRow,
  type PackingTaskRow,
  type QuotaSafetyRow,
  type QuotaTemplateRow
} from '../api/quotaPackages'
import type { QuotaPackagePageKey } from './useQuotaPackagePagination'
import type { QuotaPackageSectionCode } from '../config/quotaPackageDisplay'

type QueryState = {
  taskNo: string
  eventNo: string
  eventType: string
  deptName: string
  productCode: string
  productName: string
  templateCode: string
  templateName: string
  labelNo: string
}

type PackingForm = {
  templateCode: string
  warehouseName: string
  packageCount: number
  remark: string
}

type QuotaPagination = Record<QuotaPackagePageKey, { page: number; size: number; total: number }>

export function useQuotaPackageDataLoader(options: {
  mode: Ref<'safety' | 'template'>
  packageSection: Ref<QuotaPackageSectionCode>
  loading: Ref<boolean>
  loadError: Ref<string>
  query: QueryState
  quotaPagination: QuotaPagination
  packingForm: PackingForm
  templates: Ref<QuotaTemplateRow[]>
  safetyRows: Ref<QuotaSafetyRow[]>
  tasks: Ref<PackingTaskRow[]>
  labels: Ref<PackageLabelRow[]>
  events: Ref<Record<string, unknown>[]>
  warehouses: Ref<Array<{ warehouseName: string; warehouseType: string }>>
  candidates: Ref<Array<Record<string, unknown>>>
}) {
  function templateQuery() {
    return {
      page: String(options.quotaPagination.templates.page),
      size: String(options.quotaPagination.templates.size),
      templateCode: options.query.templateCode,
      templateName: options.query.templateName,
      productCode: options.query.productCode,
      productName: options.query.productName
    }
  }

  async function loadData() {
    options.loading.value = true
    options.loadError.value = ''
    try {
      if (options.mode.value === 'safety') {
        const [safetyData, templateData] = await Promise.all([
          fetchQuotaSafety({
            page: String(options.quotaPagination.safety.page),
            size: String(options.quotaPagination.safety.size),
            deptName: options.query.deptName,
            productCode: options.query.productCode,
            productName: options.query.productName
          }),
          fetchQuotaTemplates({
            page: '1',
            size: '500',
            templateCode: '',
            templateName: '',
            productCode: '',
            productName: ''
          })
        ])
        options.safetyRows.value = safetyData.rows
        options.quotaPagination.safety.total = safetyData.total
        options.templates.value = templateData.rows
        return
      }

      if (options.packageSection.value === 'packing-task-confirmation') {
        const [templateData, taskData, optionData] = await Promise.all([
          fetchQuotaTemplates(templateQuery()),
          fetchPackingTasks({
            page: String(options.quotaPagination.tasks.page),
            size: String(options.quotaPagination.tasks.size),
            taskNo: options.query.taskNo,
            templateCode: options.query.templateCode,
            productCode: options.query.productCode,
            productName: options.query.productName
          }),
          fetchPackingOptions()
        ])
        options.templates.value = templateData.rows
        options.quotaPagination.templates.total = templateData.total
        options.tasks.value = taskData.rows
        options.quotaPagination.tasks.total = taskData.total
        options.warehouses.value = optionData.warehouses
        options.candidates.value = optionData.candidates
        if (!options.packingForm.warehouseName && options.warehouses.value.length) {
          options.packingForm.warehouseName = options.warehouses.value[0].warehouseName
        }
        return
      }

      if (options.packageSection.value === 'quota-label-unpack') {
        const labelData = await fetchPackageLabels({
          page: String(options.quotaPagination.labels.page),
          size: String(options.quotaPagination.labels.size),
          labelNo: options.query.labelNo,
          templateCode: options.query.templateCode,
          productCode: options.query.productCode,
          deptName: options.query.deptName,
          productName: options.query.productName
        })
        options.labels.value = labelData.rows
        options.quotaPagination.labels.total = labelData.total
        return
      }

      if (options.packageSection.value === 'quota-package-events') {
        const eventData = await fetchPackageEvents({
          page: String(options.quotaPagination.events.page),
          size: String(options.quotaPagination.events.size),
          labelNo: options.query.labelNo,
          eventNo: options.query.eventNo,
          eventType: options.query.eventType
        })
        options.events.value = eventData.rows
        options.quotaPagination.events.total = eventData.total
        return
      }

      if (options.packageSection.value === 'packable-loose-snapshot') {
        const optionData = await fetchPackingOptions()
        options.warehouses.value = optionData.warehouses
        options.candidates.value = optionData.candidates
        return
      }

      const templateData = await fetchQuotaTemplates(templateQuery())
      options.templates.value = templateData.rows
      options.quotaPagination.templates.total = templateData.total
    } catch (error) {
      options.loadError.value = error instanceof Error ? error.message : '加载失败，请稍后重试'
    } finally {
      options.loading.value = false
    }
  }

  return {
    loadData,
    templateQuery
  }
}
