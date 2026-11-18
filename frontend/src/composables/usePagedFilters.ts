import { reactive, ref, type Ref } from 'vue'
import type { PageResult } from '../api/http'

type DateFilters = Record<string, string> & { dateFrom: string; dateTo: string }

export function usePagedFilters(options: {
  defaults: DateFilters
  page: Ref<number>
  size: Ref<number>
  loading: Ref<boolean>
  loadError: Ref<string>
  errorMessage: string
  isActive: () => boolean
  fetchPage: (params: Record<string, string>) => Promise<PageResult<Record<string, unknown>>>
  onPage: (result: PageResult<Record<string, unknown>>) => void
  onError?: () => void
}) {
  const filters = reactive({ ...options.defaults })
  let applied = { ...options.defaults }
  const filterError = ref('')
  let version = 0

  function beginRequest() {
    const request = ++version
    return () => request === version
  }

  async function load() {
    if (!options.isActive()) return
    const isLatest = beginRequest()
    const isCurrent = () => isLatest() && options.isActive()
    options.loading.value = true
    options.loadError.value = ''
    try {
      const result = await options.fetchPage({
        ...applied, page: String(options.page.value), size: String(options.size.value)
      })
      if (!isCurrent()) return

      options.size.value = result.size
      const lastPage = Math.max(1, Math.ceil(result.total / result.size))
      if (options.page.value > lastPage) {
        options.page.value = lastPage
        await load()
        return
      }
      options.onPage(result)
    } catch (cause) {
      if (!isCurrent()) return
      options.loadError.value = cause instanceof Error ? cause.message : options.errorMessage
      options.onError?.()
    } finally {
      if (isCurrent()) options.loading.value = false
    }
  }

  function apply() {
    filterError.value = ''
    if (filters.dateFrom && filters.dateTo && filters.dateFrom > filters.dateTo) {
      filterError.value = '开始日期不能晚于结束日期'
      return
    }
    applied = { ...filters }
    options.page.value = 1
    void load()
  }

  function reset() {
    Object.assign(filters, options.defaults)
    apply()
  }

  return { filters, filterError, apply, reset, load, beginRequest }
}
