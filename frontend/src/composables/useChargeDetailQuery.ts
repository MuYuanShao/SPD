import { isAxiosError } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { fetchHighValueCharges, type ChargeDetailFilters, type HighValueChargeDetail } from '../api/highValueCharges'

const emptyFilters = (): ChargeDetailFilters => ({
  patientNo: '', patientName: '', productCode: '', productName: '', dateFrom: '', dateTo: '',
  uid: '', udi: '', supplierName: '', manufacturerName: '', registrationNo: '',
})

/**
 * 收费耗材明细查询 composable
 * ---------------------------------------------------------------------
 * 行为：
 *   - 加载 / 查询 / 校验 失败通过 Element Plus `ElMessage` 弹层提示，
 *     不再以 StatusMessage 的方式铺在页面上方。
 *   - 暴露 `notify(type, text)` 与 `confirm(text, title?)`，
 *     方便「绑定患者 / 计费回传并扣减」等业务动作复用同一提示通道。
 */
export function useChargeDetailQuery() {
  const filters = reactive(emptyFilters())
  let appliedFilters = emptyFilters()
  const rows = ref<HighValueChargeDetail[]>([])
  const loading = ref(false)
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
        const text = isAxiosError(error)
          ? '收费耗材明细加载失败，请稍后刷新重试'
          : error instanceof Error ? error.message : '收费耗材明细加载失败'
        ElMessage({ type: 'error', message: text, grouping: true })
      }
    } finally {
      if (ownSequence === sequence) loading.value = false
    }
  }

  async function search() {
    if (filters.dateFrom && filters.dateTo && filters.dateFrom > filters.dateTo) {
      ElMessage({ type: 'warning', message: '开始日期不能晚于结束日期', grouping: true })
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

  /**
   * 业务消息：例如「绑定患者成功」「计费回传并扣减成功 / 失败」。
   * 通过 Element Plus Message 弹层展示，不占用页面布局。
   */
  function notify(type: 'success' | 'warning' | 'error' | 'info', text: string) {
    if (!text) return
    ElMessage({ type, message: text, grouping: true })
  }

  async function confirm(text: string, title = '操作确认') {
    try {
      await ElMessageBox.confirm(text, title, { type: 'warning' })
      return true
    } catch {
      return false
    }
  }

  onMounted(loadData)
  onBeforeUnmount(() => { ++sequence; request?.abort() })
  return { filters, rows, loading, currentPage, pageSize, totalItems,
    loadData, search, reset, changePage, changePageSize, notify, confirm }
}
