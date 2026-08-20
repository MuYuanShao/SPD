import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  outputDir: './output/playwright/test-results',
  reporter: [['list'], ['html', { outputFolder: './output/playwright/report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:1820',
    launchOptions: process.platform === 'win32'
      ? { executablePath: process.env.PLAYWRIGHT_CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' }
      : undefined,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      command: 'npm run backend:start',
      url: 'http://127.0.0.1:1818/api/health',
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: 'npm run dev:frontend -- --host 127.0.0.1',
      url: 'http://127.0.0.1:1820',
      reuseExistingServer: true,
      timeout: 120_000,
    },
  ],
})
