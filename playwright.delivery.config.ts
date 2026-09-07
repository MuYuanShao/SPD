import { defineConfig } from '@playwright/test'

// No backend startup: Java owns a random-port server and disposable database.
export default defineConfig({
  testDir: './tests/e2e',
  testMatch: 'delivery-real.spec.ts',
  workers: 1,
  maxFailures: 1,
  timeout: 30_000,
  outputDir: './output/playwright/delivery-results',
  reporter: [['list'], ['html', { outputFolder: './output/playwright/delivery-report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:1820',
    launchOptions: process.platform === 'win32'
      ? { executablePath: process.env.PLAYWRIGHT_CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' }
      : undefined,
    screenshot: 'only-on-failure'
  }
})
