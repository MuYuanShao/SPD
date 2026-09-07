import { expect, test } from '@playwright/test'

type Scenario = { requisitionNo: string; sourceName: string; mode: string; labels: string[]; width: number; height: number }
const api = process.env.SPD_DELIVERY_API_URL
const scenarios: Scenario[] = JSON.parse(process.env.SPD_DELIVERY_UI_CASES || '[]')
test.skip(!api || !process.env.SPD_DELIVERY_UI_PASSWORD, 'Only run through the isolated MySQL acceptance class')

test.beforeEach(async ({ page, request }) => {
  const login = await request.post(api + '/api/auth/login', {
    data: { username: 'admin', password: process.env.SPD_DELIVERY_UI_PASSWORD }
  })
  expect(login.ok()).toBeTruthy()
  const { data } = await login.json()
  expect(data?.token).toBeTruthy()
  const profile = await request.get(api + '/api/auth/me', { headers: { Authorization: 'Bearer ' + data.token } })
  expect(profile.ok()).toBeTruthy()
  const user = (await profile.json()).data
  expect(user.permissionCodes).toContain('*')
  await page.addInitScript(({ token, user }) => {
    localStorage.setItem('spd.token', token)
    localStorage.setItem('spd.user', JSON.stringify(user))
  }, { token: data.token, user })
  // Forward every API request to the actual isolated Spring server, never fabricate responses.
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }
    const response = await route.fetch({ url: api + url.pathname + url.search })
    await route.fulfill({ response })
  })
})

for (const scenario of scenarios) {
  test(scenario.mode + ' 拣配→签收 ' + scenario.width, async ({ page }) => {
    await page.setViewportSize({ width: scenario.width, height: scenario.height })
    await page.goto('/features/picking-delivery')
    const pending = page.locator('section').filter({ has: page.getByRole('heading', { name: '待拣配申领单', exact: true }) }).last()
    const row = pending.getByRole('row').filter({ hasText: scenario.requisitionNo })
    await row.getByRole('button', { name: '选择', exact: true }).click()
    await page.getByRole('combobox', { name: /^库房/ }).selectOption(scenario.sourceName)
    if (scenario.mode === 'loose') {
      await expect(page.getByRole('heading', { name: '可用定数包', exact: true })).toHaveCount(0)
      const stock = page.locator('.picking-panel--loose')
      await expect(stock.getByRole('spinbutton')).toHaveCount(1)
      await stock.getByRole('spinbutton').fill('4')
    } else {
      await expect(page.getByRole('heading', { name: '可用散货', exact: true })).toHaveCount(0)
      await expect(row).toContainText('版本 1')
      await page.getByRole('button', { name: scenario.labels[0], exact: true }).click()
      const detail = page.getByRole('dialog')
      await expect(detail.getByRole('heading', { name: '来源批次明细', exact: true })).toBeVisible()
      await page.screenshot({ path: 'output/playwright/delivery-detail-' + scenario.width + '.png', animations: 'disabled' })
      await detail.getByRole('button', { name: '关闭', exact: true }).focus()
      await page.keyboard.press('Escape')
      await expect(detail).toHaveCount(0)
      for (const label of scenario.labels) {
        await page.getByRole('row').filter({ hasText: label }).getByRole('checkbox').check()
      }
    }
    await page.screenshot({ path: 'output/playwright/delivery-' + scenario.mode + '-' + scenario.width + '-picking.png', animations: 'disabled' })
    const endpoint = scenario.mode === 'loose' ? '/picking/confirm-loose' : '/picking/confirm'
    let submissions = 0
    let release!: () => void
    const submitted = new Promise<void>(resolve => { release = resolve })
    await page.route('**/api/operational-closure' + endpoint, async route => {
      submissions++
      await submitted
      const url = new URL(route.request().url())
      const actual = await route.fetch({ url: api + url.pathname })
      await route.fulfill({ response: actual })
    })
    const response = page.waitForResponse(r => r.url().includes(endpoint) && r.request().method() === 'POST')
    const submit = page.getByRole('button', { name: '确认拣配出库', exact: true })
    await submit.click()
    try {
      await expect(submit).toBeDisabled()
      await submit.dispatchEvent('click')
    } finally { release() }
    const result = await (await response).json()
    expect(result.code, result.message).toBe(0)
    expect(submissions).toBe(1)
    const deliveryNo = result.data.deliveryNo
    const deliveryRow = page.getByRole('row').filter({ hasText: deliveryNo })
    await deliveryRow.getByRole('button', { name: '确认签收', exact: true }).click()
    await expect(deliveryRow).toContainText('已签收')
    await expect(deliveryRow.getByRole('button', { name: '确认签收', exact: true })).toHaveCount(0)
    await deliveryRow.scrollIntoViewIfNeeded()
    await page.screenshot({ path: 'output/playwright/delivery-' + scenario.mode + '-' + scenario.width + '-signed.png', animations: 'disabled' })
  })
}

test('无效目标库拒绝签收并展示原因、保留原单', async ({ page }) => {
  await page.goto('/features/picking-delivery')
  const row = page.getByRole('row').filter({ hasText: process.env.SPD_DELIVERY_BLOCKED_NO! })
  await row.getByRole('button', { name: '确认签收', exact: true }).click()
  await expect(page.getByText(/申领来源库或目标库无效/)).toBeVisible()
  await expect(row.getByRole('button', { name: '确认签收', exact: true })).toBeVisible()
  await page.screenshot({ path: 'output/playwright/delivery-error.png', animations: 'disabled' })
})
