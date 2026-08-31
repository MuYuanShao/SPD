import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  outputDir: './output/playwright/mock-test-results',
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:1820',
    launchOptions: process.platform === 'win32'
      ? { executablePath: process.env.PLAYWRIGHT_CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' }
      : undefined,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 1820',
    url: 'http://127.0.0.1:1820',
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
