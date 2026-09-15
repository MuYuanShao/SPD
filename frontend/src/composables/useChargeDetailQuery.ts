import { isAxiosError } from 'axios'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { fetchHighValueCharges, type ChargeDetailFilters, type HighValueChargeDetail } from '../api/highValueCharges'

const emptyFilters = (): ChargeDetailFilters => ({
  patientNo: '', patientName: '', productCode: '', productName: '', dateFrom: '', dateTo: '',
  uid: '', udi: '', supplierName: '', manufacturerName: '', registrationNo: '',
})

export function useChargeDetailQuery() {
  const filters = reactive(emptyFilters())
  let appliedFilters = emptyFilters()
  const rows = ref<HighValueChargeDetail[]>([])
  const loading = ref(false)
  const message = ref('')
  const validationMessage = ref('')
  const currentPage = ref(1)
  const pageSize = ref(20)
  const totalItems = ref(0)
  let sequence = 0
  let request: AbortController | undefined

  async function loadData() {
    const ownSequence = ++sequence
    request?.abort()
    const controller = request = new AbortController()
    loading.value = true
    message.value = ''
    // A pending/failed query must not expose rows from a different query as current data.
    rows.value = []
    totalItems.value = 0
    try {
      const result = await fetchHighValueCharges({ ...appliedFilters, page: currentPage.value, size: pageSize.value }, controller.signal)
      if (ownSequence !== sequence) return
      const lastPage = Math.max(1, Math.ceil(result.total / pageSize.value))
      if (currentPage.value > lastPage) {
        currentPage.value = lastPage
        await loadData()
        return
      }
      rows.value = result.rows
      totalItems.value = result.total
    } catch (error) {
      if (ownSequence === sequence && !controller.signal.aborted) {
        message.value = isAxiosError(error) ? '收费耗材明细加载失败，请稍后刷新重试'
          : error instanceof Error ? error.message : '收费耗材明细加载失败'
      }
    } finally {
      if (ownSequence === sequence) loading.value = false
    }
  }

  async function search() {
    validationMessage.value = ''
    if (filters.dateFrom && filters.dateTo && filters.dateFrom > filters.dateTo) {
      validationMessage.value = '开始日期不能晚于结束日期'
      return
    }
    appliedFilters = { ...filters }
    currentPage.value = 1
    await loadData()
  }

  async function reset() {
    Object.assign(filters, emptyFilters())
    await search()
  }

  async function changePage(page: number) {
    if (loading.value) return
    const lastPage = Math.max(1, Math.ceil(totalItems.value / pageSize.value))
    const next = Math.max(1, Math.min(page, lastPage))
    if (next === currentPage.value) return
    currentPage.value = next
    await loadData()
  }

  async function changePageSize(size: number) {
    if (loading.value || size === pageSize.value || !Number.isInteger(size) || size < 1) return
    pageSize.value = size
    currentPage.value = 1
    await loadData()
  }

  onMounted(loadData)
  onBeforeUnmount(() => { ++sequence; request?.abort() })
  return { filters, rows, loading, message, validationMessage, currentPage, pageSize, totalItems,
    loadData, search, reset, changePage, changePageSize }
}
