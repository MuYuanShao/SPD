import { expect, test } from '@playwright/test'

test('收费耗材明细支持筛选、分页、空状态和横向表格操作', async ({ page }) => {
  await page.goto('/features/high-value-consumables')
  await page.getByRole('textbox', { name: '用户名' }).fill(process.env.SPD_E2E_USERNAME || 'admin')
  await page.getByRole('textbox', { name: '密码' }).fill(process.env.SPD_E2E_PASSWORD || 'admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/high-value-consumables/)

  const table = page.locator('.charge-detail-scroll')
  await expect(page.getByRole('heading', { name: '收费明细' })).toBeVisible()
  await expect(page.getByRole('button', { name: '向右滚动' })).toBeVisible()
  const before = await table.evaluate(element => element.scrollLeft)
  await page.getByRole('button', { name: '向右滚动' }).click()
  await expect.poll(() => table.evaluate(element => element.scrollLeft)).toBeGreaterThan(before)

  const firstUniqueCell = page.locator('tbody tr').first().locator('td').nth(5).locator('strong')
  const uniqueCode = (await firstUniqueCell.textContent())?.trim()
  expect(uniqueCode).toBeTruthy()
  await page.getByRole('textbox', { name: 'UID/唯一码' }).fill(uniqueCode!)
  await page.getByRole('button', { name: '查询' }).click()
  await expect(page.locator('tbody tr')).toHaveCount(1)
  await expect(page.locator('tbody')).toContainText(uniqueCode!)

  await page.getByRole('button', { name: '重置' }).click()
  const pageSize = page.getByRole('spinbutton').first()
  await pageSize.fill('1')
  await pageSize.blur()
  await expect(page.locator('.hospital-pagination-summary')).toContainText('显示 1-1 条')
  await expect(page.getByRole('button', { name: '下一页' })).toBeEnabled()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByRole('button', { name: '上一页' })).toBeEnabled()

  await page.getByRole('textbox', { name: '商品编码' }).fill('NO-SUCH-PRODUCT-SMOKE')
  await page.getByRole('button', { name: '查询' }).click()
  await expect(page.getByText('暂无收费耗材明细')).toBeVisible()
})

test('待审批新品准入字段导航与表头及分组保持一致', async ({ page }) => {
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await page.getByRole('textbox', { name: '用户名' }).fill(process.env.SPD_E2E_USERNAME || 'admin')
  await page.getByRole('textbox', { name: '密码' }).fill(process.env.SPD_E2E_PASSWORD || 'admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: '新品准入 · 多字段列表' })).toBeVisible()

  const navigationFields = await page.locator('.subnav-chips .subnav-chip').allTextContents()
  const tableFields = (await page.locator('.approval-wide-table thead th').allTextContents())
    .slice(3, 3 + navigationFields.length)
  expect(navigationFields.map(text => text.trim())).toEqual(tableFields.map(text => text.trim()))

  await page.getByRole('button', { name: '变更记录' }).click()
  await expect(page.locator('.subnav-tabs button.active')).toHaveText('审批进度')
})

test('AI医护助手的自定义补货问题返回补货建议入口', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('textbox', { name: '用户名' }).fill(process.env.SPD_E2E_USERNAME || 'admin')
  await page.getByRole('textbox', { name: '密码' }).fill(process.env.SPD_E2E_PASSWORD || 'admin123')
  await page.getByRole('button', { name: '登录' }).click()

  await page.getByRole('button', { name: '打开AI医护助手' }).click()
  await page.getByRole('textbox', { name: '向AI医护助手提问' }).fill('请生成补货建议')
  await page.getByRole('button', { name: '发送问题' }).click()

  await expect(page.getByText('查看实时补货建议')).toBeVisible()
  await expect(page.getByRole('button', { name: '进入补货任务' })).toBeVisible()
})

test('拣配配送在没有历史记录时显示明确空状态', async ({ page }) => {
  await page.route('**/api/operational-closure/lists/delivery**', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'success',
        data: { rows: [], total: 0, page: 1, size: 20 },
        timestamp: new Date().toISOString()
      })
    })
  })

  await page.goto('/features/picking-delivery')
  await page.getByRole('textbox', { name: '用户名' }).fill(process.env.SPD_E2E_USERNAME || 'admin')
  await page.getByRole('textbox', { name: '密码' }).fill(process.env.SPD_E2E_PASSWORD || 'admin123')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page.getByRole('heading', { name: '拣配记录' })).toBeVisible()
  await expect(page.getByText('暂无拣配记录')).toBeVisible()
})
