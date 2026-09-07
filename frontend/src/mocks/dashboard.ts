import type { TodoItem } from '../components/business/TodoList.vue'
import type { NoticeItem } from '../components/business/NoticeList.vue'
import type { TrendSeries } from '../components/business/TrendChart.vue'

/** Fixed demonstration data; never submitted to business APIs. */
export const todos: TodoItem[] = [
  { id: 'CG202609070001', title: '一次性耗材采购申请', status: '待审核', progress: 35 },
  { id: 'RK202609070008', title: '中心库到货验收', status: '待验收', progress: 65 },
  { id: 'SL202609070012', title: '手术室耗材申领', status: '待配送', progress: 80 }
]
export const alerts = [
  { title: '库存下限预警', value: 12, tone: 'orange' as const },
  { title: '批次效期预警', value: 8, tone: 'purple' as const },
  { title: '不良品待处理', value: 3, tone: 'blue' as const },
  { title: '缺货提醒', value: 6, tone: 'teal' as const },
  { title: '证照到期预警', value: 5, tone: 'purple' as const },
  { title: '滞销库存提醒', value: 2, tone: 'orange' as const }
]
export const notices: NoticeItem[] = [
  { id: '1', title: '关于开展月度耗材盘点的通知', date: '2026-09-07', content: '示例公告：请各科室核对本月耗材库存，按计划完成盘点与差异复核。' },
  { id: '2', title: '医用耗材质量管理培训', date: '2026-09-05', content: '示例公告：培训内容包括耗材验收、效期管理与追溯操作。' },
  { id: '3', title: '中心库配送时间调整通知', date: '2026-09-03', content: '示例公告：常规配送安排可在科室申领页面查询，紧急需求请联系中心库。' }
]
export const labels = Array.from({ length: 30 }, (_, i) => {
  const date = new Date(Date.UTC(2026, 7, 9 + i))
  return (date.getUTCMonth() + 1) + '/' + date.getUTCDate()
})
export const movementSeries: TrendSeries[] = [
  { name: '入库', color: '#2bbcaf', values: labels.map((_, i) => 8 + (i * 7 % 17)) },
  { name: '出库', color: '#66a8ec', values: labels.map((_, i) => 6 + (i * 11 % 15)) }
]
export const usageSeries: TrendSeries[] = [
  { name: '耗用金额', type: 'line', color: '#009688', values: labels.map((_, i) => 15 + (i * 5 % 9) + i * 0.4) },
  { name: '采购金额', type: 'line', color: '#edac60', values: labels.map((_, i) => 10 + (i * 3 % 8) + i * 0.3) }
]
const names = ['一次性使用无菌注射器', '医用外科口罩', '一次性使用输液器', '医用脱脂纱布块', '一次性使用无菌手套', '静脉留置针', '医用棉签', '一次性使用采血管']
export const products = Array.from({ length: 24 }, (_, i) => ({
  id: i + 1, name: names[i % names.length], holder: ['示例医疗器械有限公司', '示例卫生材料有限公司'][i % 2],
  expiry: '2028-0' + (i % 9 + 1) + '-28', specification: ['5mL', '成人型', '标准型', '10×10cm'][i % 4],
  quantity: 120 + i * 37, location: '中心库 A-' + String(i + 1).padStart(2, '0')
}))
