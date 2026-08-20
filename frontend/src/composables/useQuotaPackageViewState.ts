import { computed, type Ref } from 'vue'
import {
  quotaPackageSections,
  type QuotaPackageSectionCode
} from '../config/quotaPackageDisplay'
import type {
  PackageLabelRow,
  PackingTaskRow,
  QuotaSafetyRow,
  QuotaTemplateRow
} from '../api/quotaPackages'

export function useQuotaPackageViewState(options: {
  routeCode: () => string
  templates: Ref<QuotaTemplateRow[]>
  safetyRows: Ref<QuotaSafetyRow[]>
  tasks: Ref<PackingTaskRow[]>
  labels: Ref<PackageLabelRow[]>
  events: Ref<Record<string, unknown>[]>
  warehouses: Ref<Array<{ warehouseName: string; warehouseType: string }>>
  candidates: Ref<Array<Record<string, unknown>>>
}) {
  const mode = computed(() => (options.routeCode() === 'quota-safety-stock' ? 'safety' : 'template'))
  const packageSection = computed<QuotaPackageSectionCode>(() => {
    const code = options.routeCode()
    return code in quotaPackageSections ? (code as QuotaPackageSectionCode) : 'quota-template-maintenance'
  })
  const title = computed(() =>
    mode.value === 'safety' ? '定数安全量' : quotaPackageSections[packageSection.value].title
  )
  const subtitle = computed(() =>
    mode.value === 'safety'
      ? '按科室、商品和定数包模板维护安全上下限，用于请补货和库存预警。'
      : quotaPackageSections[packageSection.value].subtitle
  )
  const pendingTaskCount = computed(
    () => options.tasks.value.filter((item) => item.status === 'pending_confirm').length
  )
  const availableLabelCount = computed(
    () => options.labels.value.filter((item) => item.status === 'available').length
  )
  const stats = computed(() => {
    if (mode.value === 'safety') {
      return [{ label: '安全量配置', value: options.safetyRows.value.length }]
    }
    if (packageSection.value === 'packing-task-confirmation') {
      return [
        { label: '定数模板', value: options.templates.value.length },
        { label: '待确认打包', value: pendingTaskCount.value },
        { label: '可打包散货', value: options.candidates.value.length }
      ]
    }
    if (packageSection.value === 'quota-label-unpack') {
      return [
        { label: '标签记录', value: options.labels.value.length },
        { label: '可用定数包', value: availableLabelCount.value }
      ]
    }
    if (packageSection.value === 'quota-package-events') {
      return [{ label: '事件流水', value: options.events.value.length }]
    }
    if (packageSection.value === 'packable-loose-snapshot') {
      return [
        { label: '可打包散货', value: options.candidates.value.length },
        { label: '可用库房', value: options.warehouses.value.length }
      ]
    }
    return [{ label: '定数模板', value: options.templates.value.length }]
  })

  return {
    mode,
    packageSection,
    title,
    subtitle,
    stats
  }
}
