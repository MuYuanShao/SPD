import { reactive } from 'vue'

export type QuotaPackagePageKey = 'templates' | 'safety' | 'tasks' | 'labels' | 'events' | 'candidates'

type LoadData = () => Promise<void>

export function useQuotaPackagePagination(loadData: LoadData) {
  const quotaPagination = reactive<Record<QuotaPackagePageKey, { page: number; size: number; total: number }>>({
    templates: { page: 1, size: 20, total: 0 },
    safety: { page: 1, size: 20, total: 0 },
    tasks: { page: 1, size: 20, total: 0 },
    labels: { page: 1, size: 20, total: 0 },
    candidates: { page: 1, size: 20, total: 0 },
    events: { page: 1, size: 20, total: 0 }
  })

  async function changeQuotaPage(key: QuotaPackagePageKey, page: number) {
    const state = quotaPagination[key]
    const totalPages = Math.max(Math.ceil(state.total / Math.max(state.size, 1)), 1)
    const nextPage = Math.min(Math.max(page, 1), totalPages)
    if (nextPage === state.page) return
    state.page = nextPage
    await loadData()
  }

  async function changeQuotaPageSize(key: QuotaPackagePageKey, size: number) {
    const state = quotaPagination[key]
    if (size === state.size) return
    state.size = size
    state.page = 1
    await loadData()
  }

  function resetQuotaPages() {
    quotaPagination.templates.page = 1
    quotaPagination.safety.page = 1
    quotaPagination.tasks.page = 1
    quotaPagination.labels.page = 1
    quotaPagination.events.page = 1
    quotaPagination.candidates.page = 1
  }

  return {
    quotaPagination,
    changeQuotaPage,
    changeQuotaPageSize,
    resetQuotaPages
  }
}
