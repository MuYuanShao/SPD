import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 0, message: 'ok', data, timestamp: new Date().toISOString() })

test('非新品先选医院目录，附件失败重试不重复创建申请', async ({ page }) => {
  let createCount = 0
  let uploadCount = 0
  const pageErrors: string[] = []
  const consoleErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()) })

  await page.addInitScript(() => {
    localStorage.setItem('spd.token', 'e2e-token')
    localStorage.setItem('spd.user', JSON.stringify({
      userId: 1, username: 'admin', status: 1, roles: ['ROLE_ADMIN'],
      permissionCodes: ['*'], menuCodes: ['*'], dataScope: 1
    }))
  })

  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    if (!path.startsWith('/api/')) return route.continue()
    if (path.endsWith('/auth/login')) {
      return route.fulfill({ json: ok({ token: 'e2e-token', tokenType: 'Bearer', userId: 1, username: 'admin' }) })
    }
    if (path.endsWith('/auth/me')) {
      return route.fulfill({ json: ok({
        userId: 1, username: 'admin', status: 1, roles: ['ROLE_ADMIN'], permissionCodes: ['*'],
        menuCodes: ['*'], dataScope: 1
      }) })
    }
    if (path.endsWith('/pending-product-applications') && request.method() === 'GET') {
      return route.fulfill({ json: ok({ rows: [], total: 0, page: 1, size: 25,
        typeCounts: [], summary: { typeCounts: [] } }) })
    }
    if (path.endsWith('/pending-product-applications/partner-options')) {
      return route.fulfill({ json: ok({ manufacturers: [], suppliers: [] }) })
    }
    if (path.endsWith('/pending-product-applications/source-products')) {
      return route.fulfill({ json: ok({ rows: [{
        productCode: 'P001', productName: '测试耗材', specModel: '10ml', unit: '支',
        purchasePrice: 1.2, status: 1, manufacturerName: '测试厂家', supplierName: '测试供应商'
      }], total: 1, page: 1, size: 10 }) })
    }
    if (path.endsWith('/pending-product-applications/source-products/P001')) {
      return route.fulfill({ json: ok({
        productCode: 'P001', productName: '测试耗材', specModel: '10ml', brand: '',
        manufacturerName: '测试厂家', supplierName: '测试供应商', unit: '支', purchasePrice: 1.2,
        retailPrice: 2, minPurchaseQty: 1, purchaseUnit: '盒', conversionRate: 1,
        purchasePackageQty: null, udiCode: '', registrationNo: '', registrationExpireDate: '',
        productionLicenseNo: '', businessLicenseNo: '', volumeBased: false,
        centralizedProcurement: false, domestic: true, contractCode: '', firstCategory: '',
        secondCategory: '', thirdCategory: '', chargeable: true, tenderSubCode: '', highValue: false,
        coldChain: false, quotaManaged: false, keyMonitored: false, storageCondition: '常温', status: 1
      }) })
    }
    if (path.endsWith('/pending-product-applications') && request.method() === 'POST') {
      createCount += 1
      return route.fulfill({ json: ok({ applicationNo: 'SP20260831001', productCode: 'P001' }) })
    }
    if (path.endsWith('/pending-product-applications/SP20260831001/attachments')) {
      uploadCount += 1
      if (uploadCount === 1) return route.fulfill({ status: 500, json: { message: 'upload failed' } })
      return route.fulfill({ json: ok({ attachmentId: 10 }) })
    }
    return route.fulfill({ json: ok({}) })
  })

  await page.goto('/features/pending-product-catalog')
  await page.waitForTimeout(500)
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
  await expect(page).toHaveURL(/pending-product-catalog/)
  await expect(page.locator('body')).toContainText('待审批目录')
  await page.getByRole('button', { name: '新增', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByText('申请类型').locator('..').getByRole('combobox').selectOption('信息变更')
  await expect(dialog.getByText('来源医院目录商品')).toBeVisible()
  await dialog.getByRole('button', { name: '搜索' }).click()
  await dialog.getByRole('button', { name: /P001 · 测试耗材/ }).click()
  await expect(dialog.getByText(/已载入服务器目录快照：P001/)).toBeVisible()

  await dialog.locator('input[type="file"]').setInputFiles({
    name: 'qualification.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 test')
  })
  await dialog.getByRole('button', { name: '提交审批' }).click()
  await expect(page.getByText(/已创建，1 个附件上传失败/)).toBeVisible()
  expect(createCount).toBe(1)

  await dialog.getByRole('button', { name: '提交审批' }).click()
  await expect(dialog).toBeHidden()
  expect(createCount).toBe(1)
  expect(uploadCount).toBe(2)
})
