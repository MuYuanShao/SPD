import { defineConfig } from '@playwright/test'
import base from './playwright.config'

// Deterministic UI regression tests use intercepted APIs and need no database.
export default defineConfig({
  ...base,
  outputDir: './output/playwright/frontend-regression',
  reporter: [['list']],
  testMatch: 'charge-detail-regression.spec.ts',
  webServer: {
    command: 'npm --prefix frontend run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:1820',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})

