import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchHomeWorkbench, fetchWorkbenchProducts, type HomeWorkbench, type WorkbenchProduct } from '../api/dashboard'
import type { TrendSeries } from '../components/business/TrendChart.vue'

/** Data adapter only: retains the existing home components and presentation. */
export function useHomeWorkbench() {
  const data = ref<HomeWorkbench>()
  const rows = ref<WorkbenchProduct[]>([])
  const total = ref(0), page = ref(1), size = ref(5), keyword = ref('')
  const loading = ref(false), productsLoading = ref(false)
  const error = ref(''), productError = ref('')
  let homeRequest: AbortController | undefined, productRequest: AbortController | undefined
  let homeSequence = 0, productSequence = 0, disposed = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let interval: ReturnType<typeof setInterval> | undefined
  async function loadHome() {
    const sequence = ++homeSequence
    homeRequest?.abort()
    const request = homeRequest = new AbortController()
    loading.value = true; error.value = ''
    try {
      const result = await fetchHomeWorkbench(request.signal)
      if (!disposed && sequence === homeSequence) data.value = result
    } catch (cause) {
      if (!disposed && sequence === homeSequence && !request.signal.aborted) {
        data.value = undefined
        error.value = cause instanceof Error ? cause.message : '首页汇总加载失败'
      }
    } finally { if (!disposed && sequence === homeSequence) loading.value = false }
  }
  async function loadProducts() {
    const sequence = ++productSequence
    productRequest?.abort()
    const request = productRequest = new AbortController()
    productsLoading.value = true; productError.value = ''
    try {
      const result = await fetchWorkbenchProducts({ page: page.value, size: size.value, keyword: keyword.value.trim() }, request.signal)
      if (disposed || sequence !== productSequence) return
      const last = Math.max(1, Math.ceil(result.total / size.value))
      if (page.value > last) { page.value = last; return }
      rows.value = result.rows; total.value = result.total
    } catch (cause) {
      if (!disposed && sequence === productSequence && !request.signal.aborted) {
        rows.value = []; total.value = 0
        productError.value = cause instanceof Error ? cause.message : '库存列表加载失败'
      }
    } finally { if (!disposed && sequence === productSequence) productsLoading.value = false }
  }
  function scheduleProducts() {
    ++productSequence; productRequest?.abort()
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => { void loadProducts() }, 200)
  }
  watch([keyword, size], () => { page.value = 1; scheduleProducts() })
  watch(page, scheduleProducts)
  function refresh() { void loadHome(); void loadProducts() }
  onMounted(() => {
    refresh()
    interval = setInterval(() => { if (!document.hidden) refresh() }, 60_000)
  })
  onBeforeUnmount(() => {
    disposed = true; homeRequest?.abort(); productRequest?.abort()
    if (timer) clearTimeout(timer)
    if (interval) clearInterval(interval)
  })
  const labels = computed(() => data.value?.trend.map(day => day.date.slice(5)) || [])
  const movementSeries = computed<TrendSeries[]>(() => [
    { name: '入库', color: '#2bbcaf', values: data.value?.trend.map(day => day.inboundAmount / 10000) || [] },
    { name: '出库', color: '#66a8ec', values: data.value?.trend.map(day => day.outboundAmount / 10000) || [] }
  ])
  const usageSeries = computed<TrendSeries[]>(() => [
    { name: '耗用金额', type: 'line', color: '#009688', values: data.value?.trend.map(day => day.salesAmount / 10000) || [] },
    { name: '采购金额', type: 'line', color: '#edac60', values: data.value?.trend.map(day => day.purchaseAmount / 10000) || [] }
  ])
  const alertDefinitions = [
    { key: 'lowStock', title: '库存下限预警', tone: 'orange' }, { key: 'expiry', title: '批次效期预警', tone: 'purple' },
    { key: 'quality', title: '不良品待处理', tone: 'blue' }, { key: 'shortage', title: '缺货提醒', tone: 'teal' },
    { key: 'license', title: '证照到期预警', tone: 'purple' }, { key: 'stagnant', title: '滞销库存提醒', tone: 'orange' }
  ] as const
  const alerts = computed(() => data.value ? alertDefinitions.map(item => ({ title: item.title, tone: item.tone, value: data.value!.alerts[item.key] })) : [])
  const todos = computed(() => data.value?.todos.rows || [])
  const todoTotal = computed(() => data.value?.todos.total ?? 0)
  const notices = computed(() => data.value?.notices || [])
  const status = computed(() => error.value || productError.value ? '加载失败 · 点击重试'
    : loading.value || productsLoading.value ? '正在加载真实数据…' : '实时数据 · ' + (data.value?.date || '—'))
  const statusDetail = computed(() => error.value || productError.value ||
    '销售指标按已确认耗用统计；效期预警' + (data.value?.definitions?.expiryWarningDays ?? 30) +
    '天，滞销90天；缺少真实公告源和注册证持有人字段')
  function metric(key: 'salesAmount' | 'salesQuantity' | 'inboundQuantity' | 'outboundQuantity') {
    if (!data.value) return '—'
    return data.value.metrics[key].toLocaleString('zh-CN', { minimumFractionDigits: key === 'salesAmount' ? 2 : 0, maximumFractionDigits: key === 'salesAmount' ? 2 : 4 })
  }
  return { rows, total, page, size, keyword, loading, productsLoading, error, productError, refresh,
    labels, movementSeries, usageSeries, alerts, todos, todoTotal, notices, status, statusDetail, metric }
}
