import { expect, test } from '@playwright/test'

async function login(page: import('@playwright/test').Page) {
  await page.goto('/features/purchase-management')
  const username = page.getByRole('textbox', { name: '用户名' })
  if (await username.isVisible()) {
    await username.fill(process.env.SPD_E2E_USERNAME || 'operator01')
    await page.getByRole('textbox', { name: '密码' }).fill(process.env.SPD_E2E_PASSWORD || 'admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(username).toBeHidden()
  }
}

test('真实 MySQL 全流程测试数据在关键业务页可见', async ({ page }) => {
  await login(page)

  const checks = [
    ['/features/purchase-management', process.env.SPD_E2E_PURCHASE_NO || 'CG20260713001', async () => {
      await page.locator('.subnav-tabs').getByRole('button', { name: '采购订单', exact: true }).click()
    }],
    ['/features/receiving-acceptance', process.env.SPD_E2E_RECEIVING_NO || 'RK20260713001'],
    ['/features/inventory-management', process.env.SPD_E2E_INVENTORY_PRODUCT || 'SMARTTEST-P002'],
    ['/features/department-requisition', process.env.SPD_E2E_REQUISITION_NO || 'SL2026071300001'],
    ['/features/udi-traceability', process.env.SPD_E2E_UDI_CODE || 'UDI20260706000158'],
    ['/features/high-value-consumables', process.env.SPD_E2E_CHARGE_NO || 'GZ2026070700001'],
    ['/features/cold-chain-monitoring', process.env.SPD_E2E_COLD_CHAIN_NO || 'LL2026071300001'],
    ['/features/recall-isolation', process.env.SPD_E2E_RECALL_NO || 'ZH2026071300001'],
    ['/features/pda-offline-record', process.env.SPD_E2E_PDA_NO || 'PDA2026071300001'],
  ] as const

  for (const [route, expected, prepare] of checks) {
    await page.goto(route)
    if (prepare) await prepare()
    await expect(page.locator('body')).toContainText(expected, { timeout: 10_000 })
  }
})
