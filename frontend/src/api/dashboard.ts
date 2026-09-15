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

export interface WorkbenchDay {
  date: string
  salesAmount: number
  salesQuantity: number
  inboundQuantity: number
  outboundQuantity: number
  inboundAmount: number
  outboundAmount: number
  purchaseAmount: number
}
export interface WorkbenchTodo { id: string; title: string; status: string; progress: number; eventTime: string }
export interface HomeWorkbench {
  date: string
  metrics: WorkbenchDay
  trend: WorkbenchDay[]
  alerts: Record<'lowStock' | 'expiry' | 'quality' | 'shortage' | 'license' | 'stagnant', number>
  todos: { rows: WorkbenchTodo[]; total: number }
  notices: Array<{ id: string; title: string; date: string; content: string }>
  noticeSourceAvailable: boolean
  definitions: { expiryWarningDays: number; stagnantDays: number }
}
export async function fetchHomeWorkbench(signal?: AbortSignal) {
  const data = await getData<HomeWorkbench>('/dashboard/workbench', { signal })
  if (!data || !data.metrics || !Array.isArray(data.trend) || data.trend.length !== 30 ||
      !Array.isArray(data.todos?.rows) || !Array.isArray(data.notices) || !data.alerts) {
    throw new Error('首页汇总数据格式异常')
  }
  for (const field of ['salesAmount', 'salesQuantity', 'inboundQuantity', 'outboundQuantity'] as const) {
    if (!Number.isFinite(data.metrics[field])) throw new Error('首页指标数据格式异常')
  }
  for (const day of data.trend) {
    if (typeof day.date !== 'string' || !['salesAmount', 'inboundAmount', 'outboundAmount', 'purchaseAmount']
      .every(key => Number.isFinite(day[key as keyof WorkbenchDay]))) throw new Error('首页趋势数据格式异常')
  }
  if (!Object.values(data.alerts).every(value => Number.isFinite(value) && value >= 0)) throw new Error('首页预警数据格式异常')
  return data
}
