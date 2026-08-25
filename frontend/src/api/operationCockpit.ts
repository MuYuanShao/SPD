import { getData } from './http'

export interface CockpitComparison {
  currentAmount: number
  previousAmount: number
  monthOnMonth?: number
}

export interface OperationCockpitData {
  month: string
  statisticsDate?: string
  generatedAt?: string
  summary: CockpitComparison & {
    monthOnMonth: number
    departmentCount: number
    warningCount: number
  }
  categories: Array<CockpitComparison & { category: string; monthOnMonth: number }>
  trend: Array<{ month: string; label: string; amount: number }>
  departments: Array<CockpitComparison & { code: string; name: string }>
  focusedProducts: Array<CockpitComparison & { code: string; name: string }>
  alerts: Array<{
    productCode: string
    productName: string
    availableQty: number
    threshold: number
    alertType: string
  }>
}

export function fetchOperationCockpit(month: string) {
  return getData<OperationCockpitData>('/operation-cockpit', { params: { month } })
}
