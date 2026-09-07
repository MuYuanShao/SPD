import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { featureChunkRules } from './src/config/featureCatalog'

export default defineConfig({
  base: process.env.VITE_BASE_PATH || '/',
  plugins: [vue()],
  build: {
    rollupOptions: {
      onwarn(warning, defaultHandler) {
        if (
          warning.code === 'INVALID_ANNOTATION' &&
          typeof warning.id === 'string' &&
          warning.id.includes('@vueuse/core')
        ) {
          return
        }
        defaultHandler(warning)
      },
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('echarts') || id.includes('zrender')) {
              return 'vendor-charts'
            }
            if (id.includes('element-plus')) {
              if (id.includes('/components/table/')) {
                return 'vendor-element-plus-table'
              }
              return 'vendor-element-plus-core'
            }
            if (id.includes('@lucide/vue')) {
              return 'vendor-icons'
            }
            if (id.includes('vue') || id.includes('pinia')) {
              return 'vendor-vue'
            }
            return 'vendor'
          }

          const normalizedId = id.replaceAll('\\', '/')
          const chunkRule = featureChunkRules.find((rule) =>
            rule.viewPathIncludes.some((viewPath) => normalizedId.includes(viewPath))
          )

          if (chunkRule) {
            return chunkRule.chunkName
          }
        }
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 1820,
    // Windows 下编辑文件时原生 fs.watch 会因目录改名竞态抛 EBUSY 并终止 dev server，改用轮询监视。
    watch: {
      usePolling: true,
      interval: 300
    },
    proxy: {
      '/api': {
        target: 'http://localhost:1818',
        changeOrigin: true
      }
    }
  }
})
