import { expect, test } from '@playwright/test'

test('库房新增弹窗关联科室可搜索并回填', async ({ page }) => {
  await page.goto('/features/warehouse-location-management')
  await page.getByRole('textbox', { name: '用户名' }).fill('admin')
  await page.getByRole('textbox', { name: '密码' }).fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/warehouse-location-management/)

  // 打开新增库房弹窗
  await page.getByRole('button', { name: '新增', exact: true }).click()
  await expect(page.getByRole('heading', { name: '新增库房' })).toBeVisible()

  // 关联科室输入框聚焦 → 出现候选列表 → 点击第一项
  const deptInput = page.locator('.warehouse-dept-field input')
  await deptInput.click()
  const firstOption = page.locator('.dept-result-option').first()
  await expect(firstOption).toBeVisible()
  const deptName = (await firstOption.locator('span').first().textContent())?.trim()
  await firstOption.click()

  // 选中的科室应回填显示在输入框中
  await expect(deptInput).toHaveValue(deptName!)

  // 输入关键字过滤后再次选择
  await deptInput.fill('')
  await deptInput.type('骨科')
  const filtered = page.locator('.dept-result-option')
  await expect(filtered.first()).toBeVisible()
  const filteredName = (await filtered.first().locator('span').first().textContent())?.trim()
  await filtered.first().click()
  await expect(deptInput).toHaveValue(filteredName!)
})
