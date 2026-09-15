import { expect, test, type Page } from '@playwright/test'

const user = { userId: 1, username: 'tester', roles: ['ROLE_ADMIN'], permissionCodes: ['*'], menuCodes: ['*'], status: 1, dataScope: 1 }
const envelope = (data: unknown) => ({ code: 0, message: '', data })
async function prepare(page: Page) {
  await page.addInitScript(user => {
    localStorage.setItem('spd.token', 'test-token')
    localStorage.setItem('spd.user', JSON.stringify(user))
  }, user)
  await page.route('**/api/auth/me', route => route.fulfill({ json: envelope(user) }))
}
const result = (code: string, page = 1) => envelope({ rows: [{ bizNo: code, productCode: code, chargeQuantity: 0, unitPrice: 0, chargeAmount: 0 }], total: 45, page, size: 20 })

test('收费查询入口独立、零值保留、滚动条支持键盘', async ({ page }) => {
  await prepare(page)
  await page.route('**/api/operational-closure/lists/high-value?**', route => route.fulfill({ json: result('INITIAL') }))
  await page.goto('/features/high-value-consumables')
  const refresh = page.getByRole('button', { name: '刷新', exact: true })
  await expect(refresh).toBeVisible()
  await expect(refresh.locator('a')).toHaveCount(0)
  await expect(page.getByRole('link', { name: '高值耗材计费操作' })).toBeVisible()
  await expect(page.locator('tbody tr td').nth(6)).toHaveText('0')
  await expect(page.locator('tbody tr td').nth(8)).toHaveText('0.00')
  await page.getByRole('scrollbar').focus()
  await page.keyboard.press('End')
  await expect.poll(() => page.locator('.charge-detail-scroll').evaluate(el => el.scrollLeft)).toBeGreaterThan(0)
})

test('最新查询胜出，翻页只使用已提交条件，失败不残留旧结果', async ({ page }) => {
  await prepare(page)
  let releaseSlow!: () => void
  const slowGate = new Promise<void>(resolve => { releaseSlow = resolve })
  let slowStarted!: () => void
  const started = new Promise<void>(resolve => { slowStarted = resolve })
  let slowFinished!: () => void
  const finished = new Promise<void>(resolve => { slowFinished = resolve })
  const queries: string[] = []
  await page.route('**/api/operational-closure/lists/high-value?**', async route => {
    const url = new URL(route.request().url())
    const code = url.searchParams.get('productCode') || 'INITIAL'
    queries.push(code)
    if (code === 'SLOW') { slowStarted(); await slowGate }
    if (code === 'FAIL') { await route.fulfill({ status: 500, json: { message: '测试请求失败' } }); return }
    await route.fulfill({ json: result(code, Number(url.searchParams.get('page'))) }).catch(() => {})
    if (code === 'SLOW') slowFinished()
  })
  await page.goto('/features/high-value-consumables')
  await expect(page.locator('tbody')).toContainText('INITIAL')
  const input = page.getByRole('textbox', { name: '商品编码', exact: true })
  await input.fill('SLOW')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await started
  await input.fill('LATEST')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('tbody')).toContainText('LATEST')
  releaseSlow()
  await finished
  await expect(page.locator('tbody')).toContainText('LATEST')
  await input.fill('DRAFT')
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('tbody')).toContainText('LATEST')
  expect(queries.at(-1)).toBe('LATEST')
  await input.fill('FAIL')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.locator('tbody')).not.toContainText('LATEST')
  await expect(page.locator('tbody [data-state="error"]')).toBeVisible()
})

test('登录后用户接口失败必须留在登录页并说明原因', async ({ page }) => {
  await page.route('**/api/auth/login', route => route.fulfill({ json: envelope({ token: 'test-token' }) }))
  await page.route('**/api/auth/me', route => route.fulfill({ status: 503, json: { message: '用户信息暂时不可用' } }))
  await page.goto('/login')
  await page.getByRole('textbox', { name: '用户名' }).fill('tester')
  await page.getByRole('textbox', { name: '密码' }).fill('test-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.locator('.login-error')).toBeVisible()
  await expect(page).toHaveURL(/login/)
})

test('刷新先验证服务端权限，不用缓存权限发起业务请求', async ({ page }) => {
  await prepare(page)
  let reads = 0
  await page.route('**/api/auth/me', route => route.fulfill({ json: envelope({ ...user, roles: [], permissionCodes: [], menuCodes: [] }) }))
  await page.route('**/api/operational-closure/lists/high-value?**', route => { reads++; return route.fulfill({ json: result('DENIED') }) })
  await page.goto('/features/high-value-consumables')
  await expect(page).toHaveURL(/forbidden/)
  expect(reads).toBe(0)
})


test('日期校验、回车查询、空结果和失败重试', async ({ page }) => {
  await prepare(page)
  let reads = 0
  let failed = true
  await page.route('**/api/operational-closure/lists/high-value?**', route => {
    reads++
    const code = new URL(route.request().url()).searchParams.get('productCode')
    if (code === 'RETRY' && failed) return route.fulfill({ status: 503, json: { message: '暂时不可用' } })
    if (code === 'EMPTY') return route.fulfill({ json: envelope({ rows: [], total: 0, page: 1, size: 20 }) })
    return route.fulfill({ json: result(code || 'INITIAL') })
  })
  await page.goto('/features/high-value-consumables')
  await expect(page.locator('tbody')).toContainText('INITIAL')
  await page.getByLabel('开始日期').fill('2026-09-12')
  await page.getByLabel('结束日期').fill('2026-09-11')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('开始日期不能晚于结束日期')
  expect(reads).toBe(1)
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  const input = page.getByRole('textbox', { name: '商品编码', exact: true })
  await input.fill('EMPTY')
  await input.press('Enter')
  await expect(page.locator('tbody [data-state="empty"]')).toBeVisible()
  await input.fill('RETRY')
  await input.press('Enter')
  await expect(page.locator('tbody [data-state="error"]')).toBeVisible()
  failed = false
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.locator('tbody')).toContainText('RETRY')
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('认证初始化只请求一次，业务401清理缓存并保留返回路径', async ({ page }) => {
  await prepare(page)
  let meReads = 0
  let expired = false
  await page.route('**/api/auth/me', route => { meReads++; return route.fulfill({ json: envelope(user) }) })
  await page.route('**/api/operational-closure/lists/high-value?**', route => expired
    ? route.fulfill({ status: 401, json: { message: '登录已失效' } })
    : route.fulfill({ json: result('INITIAL') }))
  await page.goto('/features/high-value-consumables')
  await expect(page.locator('tbody')).toContainText('INITIAL')
  expect(meReads).toBe(1)
  expired = true
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page).toHaveURL(/login\?redirect=/)
  expect(await page.evaluate(() => [localStorage.getItem('spd.token'), localStorage.getItem('spd.user')])).toEqual([null, null])
})

test('窄屏和宽屏下滚动条末端对齐且只读用户不显示计费入口', async ({ page }) => {
  await prepare(page)
  await page.route('**/api/auth/me', route => route.fulfill({ json: envelope({ ...user, roles: [], permissionCodes: [], menuCodes: ['high-value-consumables'] }) }))
  await page.route('**/api/operational-closure/lists/high-value?**', route => route.fulfill({ json: result('INITIAL') }))
  await page.goto('/features/high-value-consumables')
  await expect(page.locator('tbody')).toContainText('INITIAL')
  await expect(page.getByRole('link', { name: '高值耗材计费操作' })).toHaveCount(0)
  for (const width of [1366, 900, 1920]) {
    await page.setViewportSize({ width, height: 900 })
    await page.getByRole('scrollbar').focus()
    await page.keyboard.press('End')
    await expect.poll(async () => {
      const track = await page.locator('.charge-scroll-track').boundingBox()
      const thumb = await page.locator('.charge-scroll-thumb').boundingBox()
      return Math.abs(track!.x + track!.width - thumb!.x - thumb!.width)
    }).toBeLessThan(2)
  }
  await page.keyboard.press('Home')
  await page.getByRole('heading', { name: '收费明细' }).click()
  await page.screenshot({ path: 'output/playwright/charge-detail-pilot.png', fullPage: true })
})
