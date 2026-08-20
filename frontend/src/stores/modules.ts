import { defineStore } from 'pinia'
import { fetchModules, type SpdModule } from '../api/modules'

export const useModuleStore = defineStore('modules', {
  state: () => ({
    modules: [] as SpdModule[],
    loading: false,
    error: ''
  }),
  actions: {
    async load() {
      this.loading = true
      this.error = ''

      try {
        this.modules = await fetchModules()
      } catch (error) {
        this.error = error instanceof Error ? error.message : '模块目录加载失败'
      } finally {
        this.loading = false
      }
    }
  }
})
