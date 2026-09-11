import assert from 'node:assert/strict'
import { chromium, request } from 'playwright'
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
const expected = (await (await client.get('/api/dashboard/workbench', { headers })).json()).data
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
  assert.equal(await page.locator('.fli-statistics .el-col').count(), 4)
  const stats = await page.locator('.fli-statistics').innerText()
  const amount = expected.metrics.salesAmount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  assert.ok(stats.includes(amount))
  assert.ok(!(await page.locator('.fli-dashboard').innerText()).includes('演示数据'))
  await page.getByText('暂无公告', { exact: true }).waitFor()
  const search = page.getByRole('textbox', { name: '搜索进销存商品' })
  await search.fill(process.env.SPD_HOME_PRODUCT)
  await page.getByText('库存记录，共 7 条', { exact: true }).waitFor()
  assert.equal(await page.locator('.fli-products .el-table__body tbody tr').count(), 5)
  await page.locator('.fli-pagination .el-pager').getByText('2', { exact: true }).click()
  await ready()
  await page.waitForFunction(() => document.querySelectorAll('.fli-products .el-table__body tbody tr').length === 2)
  await search.fill('NO-SUCH-HOME-PRODUCT')
  await page.getByText('暂无匹配商品', { exact: true }).waitFor()
  assert.equal(await page.locator('.fli-pagination .el-pager .is-active').innerText(), '1')
  const fail = route => route.fulfill({ status: 503, json: { code: 503, message: '验收模拟服务不可用', data: null } })
  await page.route('**/api/dashboard/workbench', fail)
  await page.getByRole('button', { name: /^实时数据/ }).click()
  await page.getByRole('button', { name: '加载失败 · 点击重试' }).waitFor()
  assert.ok((await page.locator('.fli-statistics').innerText()).includes('—'))
  await page.unroute('**/api/dashboard/workbench', fail)
  await page.getByRole('button', { name: '加载失败 · 点击重试' }).click()
  await ready()
  await search.fill(process.env.SPD_HOME_PRODUCT)
  await page.getByText('库存记录，共 7 条', { exact: true }).waitFor()
  let announceSlow, releaseSlow, finishSlow
  const started = new Promise(resolve => { announceSlow = resolve })
  const released = new Promise(resolve => { releaseSlow = resolve })
  const finished = new Promise(resolve => { finishSlow = resolve })
  await page.route('**/api/dashboard/products?*', async route => {
    const url = new URL(route.request().url())
    if (url.searchParams.get('keyword') !== 'STALE-EMPTY') return route.fallback()
    announceSlow()
    await released
    try {
      const actual = await route.fetch({ url: api + url.pathname + url.search })
      await route.fulfill({ response: actual })
    } catch { /* The obsolete browser request may already have been aborted. */ }
    finally { finishSlow() }
  })
  await search.fill('STALE-EMPTY')
  await started
  await search.fill(process.env.SPD_HOME_PRODUCT)
  await ready()
  releaseSlow()
  await finished
  await page.getByText('库存记录，共 7 条', { exact: true }).waitFor()
  await page.screenshot({ path: 'output/playwright/home-workbench-real.png', animations: 'disabled', fullPage: true })
  await writeFile('output/playwright/home-workbench-snapshot.txt', await page.locator('.fli-dashboard').ariaSnapshot(), 'utf8')
  console.log('PASS: real summary, four unchanged statistic components, no mock fallback, server search/pagination, empty state, failure/retry, obsolete search cancellation')
} finally {
  await browser.close()
  await client.dispose()
}
