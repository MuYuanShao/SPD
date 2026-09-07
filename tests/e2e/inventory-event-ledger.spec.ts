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
  await page.getByRole('dialog').getByRole('button', { name: /close/i }).focus()
  await page.keyboard.press('Escape')
  await expect(page.getByText('UDI-001')).toBeHidden()
})
