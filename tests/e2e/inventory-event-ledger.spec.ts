import { expect, test } from '@playwright/test'

const envelope = (data: unknown) => ({ code: 0, message: 'ok', data, timestamp: '' })

test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/me', (route) => route.fulfill({
    json: envelope({ userId: 1, username: 'admin', status: 1, roles: ['ROLE_ADMIN'],
      permissionCodes: ['*'], menuCodes: ['*'], dataScope: 1 })
  }))
  await page.addInitScript(() => {
    localStorage.setItem('spd.token', 'inventory-ledger-test')
    localStorage.setItem('spd.user', JSON.stringify({
      userId: 1, username: 'admin', status: 1, roles: ['ROLE_ADMIN'],
      permissionCodes: ['*'], menuCodes: ['*'], dataScope: 1
    }))
  })
  await page.route('**/api/inventory/events/options/transaction-types', (route) => route.fulfill({
    json: envelope({ rows: [
      { code: 'receiving_in', label: '验收入库', category: 'quantity' },
      { code: 'batch_price_adjustment', label: '批次调价', category: 'valuation' }
    ] })
  }))
  await page.route('**/api/inventory/events/IE-001', (route) => route.fulfill({
    json: envelope({
      eventNo: 'IE-001', eventType: 'purchase_receive_in', transactionTypeCode: 'receiving_in',
      transactionTypeName: '验收入库', eventCategory: 'quantity', warehouseName: 'SPD中心库',
      productCode: 'P001', productName: '一次性导管', unitPrice: 12.5, qtyChange: 2,
      amount: 25, qtyAfter: 10, traceCount: 2, sourceBizNo: 'RK001', snapshotOrigin: 'captured',
      eventTime: '2026-09-04 10:00:00', traceCodes: [
        { traceCodeId: 1, traceType: 'high_value_unit', linkedQuantity: 1, udiCode: 'UDI-001' },
        { traceCodeId: 2, traceType: 'high_value_unit', linkedQuantity: 1, udiCode: 'UDI-002' }
      ]
    })
  }))
  await page.route('**/api/inventory/events?*', (route) => route.fulfill({
    json: envelope({
      rows: [
        { eventNo: 'IE-001', eventType: 'purchase_receive_in', transactionTypeCode: 'receiving_in', transactionTypeName: '验收入库', eventCategory: 'quantity', warehouseName: 'SPD中心库', productCode: 'P001', productName: '一次性导管', unitPrice: 12.5, qtyChange: 2, amount: 25, qtyAfter: 10, traceCount: 2, snapshotOrigin: 'captured', eventTime: '2026-09-04 10:00' },
        { eventNo: 'IE-002', eventType: 'batch_price_adjustment', transactionTypeCode: 'batch_price_adjustment', transactionTypeName: '批次调价', eventCategory: 'valuation', warehouseName: 'SPD中心库', productCode: 'P001', productName: '一次性导管', unitPrice: 15, qtyChange: 0, valueChange: 25, qtyAfter: 10, traceCount: 0, snapshotOrigin: 'captured', eventTime: '2026-09-04 11:00' }
      ], total: 2, page: 1, size: 20,
      summary: { inboundQty: 2, outboundQty: 0, netQty: 2, movementAmount: 25, valuationChange: 25 }
    })
  }))
})

test('库存流水一事件一行并在详情展示稳定追溯关系', async ({ page }) => {
  await page.goto('/features/inventory-events')

  await expect(page.getByRole('heading', { name: '库存交易流水' })).toBeVisible()
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(3)
  await expect(page.getByRole('table').getByText('批次调价', { exact: true })).toBeVisible()
  await expect(page.getByText('UDI-001')).toBeHidden()

  await page.getByRole('button', { name: '高级筛选' }).click()
  await expect(page.getByRole('textbox', { name: '来源单据' })).toBeVisible()

  await page.getByRole('button', { name: /详情.*2/ }).click()
  await expect(page.getByRole('heading', { name: '追溯对象' })).toBeVisible()
  await expect(page.getByText('UDI-001')).toBeVisible()
  await expect(page.getByText('UDI-002')).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: /关闭|close/i }).focus()
  await page.keyboard.press('Escape')
  await expect(page.getByText('UDI-001')).toBeHidden()
})

for (const [remark, displayed] of [
  ['unpack package label back to loose stock', '无'],
  ['页面操作解包回散货', '无'],
  ['包装破损\nQC checked', '包装破损\nQC checked']
]) {
  test(`解包流水备注显示人工原文：${JSON.stringify(remark)}`, async ({ page }) => {
    await page.route('**/api/inventory/events/IE-001', route => route.fulfill({
      json: envelope({ eventNo: 'IE-001', eventType: 'quota_unpack_in', traceCodes: [], remark })
    }))
    await page.goto('/features/inventory-events')
    await page.getByRole('button', { name: /详情.*2/ }).click()
    await expect(page.getByRole('dialog').locator('.detail-remark')).toHaveText(displayed)
  })
}

test('解包确认提交人工备注，未填写时发送空备注', async ({ page }) => {
  const remarks: string[] = []
  await page.route(url => url.pathname === '/api/quota-packages/labels', route => route.fulfill({
    json: envelope({
      rows: [{ labelNo: 'PK-REMARK-001', templateCode: 'TP-001', templateName: '测试定数包',
        status: 'available', packageQuantity: 5, warehouseName: 'SPD中心库', printCount: 1 }],
      page: 1, size: 20, total: 1
    })
  }))
  await page.route('**/api/quota-packages/labels/PK-REMARK-001/unpack', route => {
    expect(route.request().method()).toBe('PUT')
    remarks.push(route.request().postDataJSON().reason)
    return route.fulfill({ json: envelope({ labelNo: 'PK-REMARK-001', status: 'void', restoredLooseQty: 5 }) })
  })
  await page.goto('/features/quota-label-unpack')
  const unpack = page.getByRole('button', { name: '解包', exact: true })
  const dialog = page.getByRole('dialog', { name: '定数包解包' })
  for (const remark of ['包装破损\nQC checked', '']) {
    await unpack.click()
    const input = dialog.getByPlaceholder('填写备注（选填）')
    await expect(input).toHaveValue('')
    await input.fill(remark)
    await dialog.getByRole('button', { name: '确认解包' }).click()
    await expect(dialog).toBeHidden()
  }
  expect(remarks).toEqual(['包装破损\nQC checked', ''])
})
