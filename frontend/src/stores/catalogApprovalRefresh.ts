import { defineStore } from 'pinia'
import { ref } from 'vue'

/** Tracks successful catalog writes so cached readers reload only the affected data. */
export const useCatalogApprovalRefreshStore = defineStore('catalog-approval-refresh', () => {
  const applicationsVersion = ref(0)
  const hospitalCatalogVersion = ref(0)
  const workbenchVersion = ref(0)

  function changed(options: { catalogChanged?: boolean } = {}) {
    applicationsVersion.value++
    workbenchVersion.value++
    if (options.catalogChanged) hospitalCatalogVersion.value++
  }

  return { applicationsVersion, hospitalCatalogVersion, workbenchVersion, changed }
})
