import { getData } from './http'

export interface DashboardData {
  metrics: {
    productCount: number
    todayOrders: number
    lowStockCount: number
    monthlyPurchaseAmount: number
  }
  trend: Array<{
    date: string
    label: string
    receivingAmount: number
    consumptionAmount: number
  }>
  departments: Array<{ deptName: string; amount: number }>
  exceptions: Array<{
    bizNo: string
    riskEvents: string
    title: string
    reason: string
    status: string
    eventTime: string
  }>
  notices: Array<{
    title: string
    detail: string
    operatorName: string
    operationTime: string
  }>
}

export function fetchDashboard() {
  return getData<DashboardData>('/dashboard')
}
