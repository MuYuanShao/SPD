import { expect, test } from '@playwright/test'

async function login(page: import('@playwright/test').Page) {
  await page.goto('/features/receiving-acceptance')
  const username = page.getByRole('textbox', { name: '用户名' })
  if (await username.isVisible()) {
    await username.fill(process.env.SPD_E2E_USERNAME || 'admin')
    await page.getByRole('textbox', { name: '密码' }).fill(process.env.SPD_E2E_PASSWORD || 'admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(username).toBeHidden()
  }
}

test('收货验收按待收货和已验收分页展示', async ({ page }) => {
  await login(page)

  const pendingTab = page.getByRole('tab', { name: /待收货/ })
  const completedTab = page.getByRole('tab', { name: /已验收/ })
  await expect(pendingTab).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('tabpanel')).toBeVisible()

  const completedResponse = page.waitForResponse((response) =>
    response.url().includes('/api/receiving-orders') &&
    response.url().includes('statusGroup=completed') &&
    response.request().method() === 'GET'
  )
  await completedTab.click()
  await completedResponse

  await expect(completedTab).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText('待验收（草稿）')).toBeHidden()
  await expect(page.getByRole('combobox', { name: '状态' })).toBeVisible()
})
