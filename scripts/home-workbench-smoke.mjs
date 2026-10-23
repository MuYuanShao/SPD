import assert from 'node:assert/strict'
import { chromium, request } from 'playwright'
import { expect } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'

const api = process.env.SPD_HOME_API
assert.match(api || '', /^http:\/\/127\.0\.0\.1:\d+$/)
assert.ok(process.env.SPD_HOME_PASSWORD && process.env.SPD_HOME_PRODUCT, 'Run through the isolated MySQL test')
const client = await request.newContext({ baseURL: api })
const login = await client.post('/api/auth/login', { data: { username: 'admin', password: process.env.SPD_HOME_PASSWORD } })
const loginBody = await login.json()
assert.equal(loginBody.code, 0)
const token = loginBody.data.token
const headers = { Authorization: 'Bearer ' + token }
const user = (await (await client.get('/api/auth/me', { headers })).json()).data
const expected = (await (await client.get('/api/dashboard', { headers })).json()).data
const browser = await chromium.launch(process.platform === 'win32'
  ? { executablePath: process.env.PLAYWRIGHT_CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' } : {})
await mkdir('output/playwright', { recursive: true })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await page.addInitScript(({ token, user }) => {
    localStorage.setItem('spd.token', token)
    localStorage.setItem('spd.user', JSON.stringify(user))
  }, { token, user })
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const response = await route.fetch({ url: api + url.pathname + url.search })
    await route.fulfill({ response })
  })
  const ready = () => page.getByRole('button', { name: /^实时数据/ }).waitFor()
  await page.goto('http://127.0.0.1:1820/')
  await ready()
  await expect(page.locator('.wb-hero-metrics > li')).toHaveCount(4)
  const values = page.locator('.wb-hero-metrics strong')
  await expect(values.nth(0)).toContainText(expected.metrics.productCount.toLocaleString('zh-CN'))
  await expect(values.nth(1)).toContainText(expected.metrics.todayOrders.toLocaleString('zh-CN'))
  await expect(values.nth(2)).toContainText(expected.metrics.lowStockCount.toLocaleString('zh-CN'))
  const purchase = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 2 }).format(Number(expected.metrics.monthlyPurchaseAmount))
  await expect(values.nth(3)).toContainText(purchase)
  const priorMetrics = await page.locator('.wb-hero-metrics').innerText()
  assert.ok(!(await page.locator('.wb').innerText()).includes('演示数据'))
  await page.getByText('暂无公告', { exact: true }).waitFor()
  assert.equal(await page.locator('.fli-products').count(), 0)
  const fail = route => route.fulfill({ status: 503, json: { code: 503, message: '验收模拟服务不可用', data: null } })
  await page.route('**/api/dashboard', fail)
  await page.getByRole('button', { name: /^实时数据/ }).click()
  await page.getByRole('button', { name: '数据异常 · 点击重试' }).waitFor()
  assert.equal(await page.locator('.wb-hero-metrics').innerText(), priorMetrics)
  await expect(page.locator('.wb-banner-error')).toBeVisible()
  await page.unroute('**/api/dashboard', fail)
  await page.getByRole('button', { name: '数据异常 · 点击重试' }).click()
  await ready()
  await page.screenshot({ path: 'output/playwright/home-workbench-real.png', animations: 'disabled', fullPage: true })
  await writeFile('output/playwright/home-workbench-snapshot.txt', await page.locator('.wb').ariaSnapshot(), 'utf8')
  console.log('PASS: real summary, four actual routed dashboard metrics, no mock fallback, inventory list removed, failure/retry')
} finally {
  await browser.close()
  await client.dispose()
}
