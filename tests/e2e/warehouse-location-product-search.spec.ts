import { expect, test } from '@playwright/test'

test('货位维护固定商品编码支持回车/放大镜搜索医院目录并回填', async ({ page }) => {
  const username = process.env.SPD_E2E_USERNAME || 'admin'
  const password = process.env.SPD_E2E_PASSWORD || 'admin123'
  await page.goto('/features/warehouse-location-management')
  await page.getByRole('textbox', { name: '用户名' }).fill(username)
  await page.getByRole('textbox', { name: '密码' }).fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/warehouse-location-management/)

  // 选择第一条库房记录后打开维护货位弹窗
  const firstRowCheckbox = page.locator('tbody tr').first().locator('input[type="checkbox"]')
  await firstRowCheckbox.check()
  await page.getByRole('button', { name: '维护货位' }).click()
  await expect(page.getByRole('heading', { name: '维护货位' })).toBeVisible()

  // 固定商品编码输入框：聚焦出现医院目录候选列表，点击第一项回填编码
  const codeInput = page.locator('.location-product-field input')
  await codeInput.click()
  const firstOption = page.locator('.product-result-option').first()
  await expect(firstOption).toBeVisible()
  const firstCode = (await firstOption.locator('.product-code').textContent())?.trim()
  await firstOption.click()
  await expect(codeInput).toHaveValue(firstCode!)

  // 输入关键字实时过滤候选列表（屏蔽不匹配商品信息）
  await codeInput.fill('')
  const searchKeyword = firstCode!.slice(0, Math.min(6, firstCode!.length))
  await codeInput.type(searchKeyword)
  const filtered = page.locator('.product-result-option')
  await expect(filtered.first()).toBeVisible()
  const filteredCodes = await filtered.locator('.product-code').allTextContents()
  expect(filteredCodes.length).toBeGreaterThan(0)
  for (const code of filteredCodes) {
    expect(code.trim().toLowerCase()).toContain(searchKeyword.toLowerCase())
  }
  const pickedCode = (await filtered.first().locator('.product-code').textContent())?.trim()
  await filtered.first().click()
  await expect(codeInput).toHaveValue(pickedCode!)
})
