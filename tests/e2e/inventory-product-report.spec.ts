import { expect, test } from '@playwright/test'

const username = process.env.SPD_E2E_USERNAME || process.env.SPD_USERNAME
const password = process.env.SPD_E2E_PASSWORD || process.env.SPD_PASSWORD

test('首页列表迁入报表中心，真实字段、检索、分页和空结果可用', async ({ page }) => {
  test.skip(!username || !password, '需要本地测试账号')
  let oldProductRequests = 0
  page.on('request', request => { if (request.url().includes('/api/dashboard/products')) oldProductRequests++ })
  await page.goto('/')
  await page.getByRole('textbox', { name: '用户名' }).fill(username!)
  await page.getByRole('textbox', { name: '密码' }).fill(password!)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: /^实时数据/ })).toBeVisible()
  await expect(page.locator('.fli-products')).toHaveCount(0)
  expect(oldProductRequests).toBe(0)
  const response = page.waitForResponse(r => r.url().includes('/api/report-center/inventory-products') && r.status() === 200)
  await page.goto('/features/inventory-product-detail-report')
  const payload = (await (await response).json()).data
  expect(payload.rows.length).toBeGreaterThan(0)
  for (const row of payload.rows) {
    expect(row).toHaveProperty('registrationNo')
    expect(row).toHaveProperty('manufacturerName')
    expect(row).toHaveProperty('distributorName')
    expect(row).not.toHaveProperty('holder')
  }
  await expect(page.getByRole('heading', { name: '进销存商品明细' })).toBeVisible()
  await expect(page.locator('.fli-submenu')).toContainText('进销存商品明细')
  for (const label of ['注册证', '厂家', '配送商']) await expect(page.getByRole('columnheader', { name: label, exact: true })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '注册证持有人', exact: true })).toHaveCount(0)
  for (const field of ['registrationNo', 'manufacturerName', 'distributorName']) {
    const example = payload.rows.find((row: Record<string, unknown>) => row[field])
    if (!example) continue
    const found = page.waitForResponse(r => r.url().includes('/api/report-center/inventory-products') && r.status() === 200)
    await page.getByLabel('关键词', { exact: true }).fill(example[field])
    await page.getByRole('button', { name: '查询', exact: true }).click()
    const result = (await (await found).json()).data
    expect(result.total).toBeGreaterThan(0)
    expect(result.rows.some((row: Record<string, unknown>) => row[field] === example[field])).toBe(true)
  }
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.getByRole('spinbutton').first()).toBeVisible()
  await page.getByRole('spinbutton').first().fill('1')
  await page.getByRole('spinbutton').first().blur()
  await expect(page.locator('.hospital-pagination-summary')).toContainText('显示 1-1 条')
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('.hospital-pagination-summary')).toContainText('显示 2-2 条')
  await page.getByLabel('关键词', { exact: true }).fill('NO-SUCH-INVENTORY-PRODUCT-REPORT')
  await page.getByLabel('关键词', { exact: true }).press('Enter')
  await expect(page.getByText('暂无匹配商品', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.locator('.hospital-pagination-summary')).toContainText('显示 1-1 条')
  const scroll = page.locator('.inventory-product-report .el-table__body-wrapper .el-scrollbar__wrap')
  await scroll.evaluate(el => { el.scrollLeft = el.scrollWidth })
  await expect.poll(() => scroll.evaluate(el => el.scrollLeft)).toBeGreaterThan(0)
  await scroll.evaluate(el => { el.scrollLeft = 0 })
  await page.screenshot({ path: 'output/playwright/inventory-product-report.png', fullPage: true })
})
