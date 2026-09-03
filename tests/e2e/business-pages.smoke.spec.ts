import { expect, test } from '@playwright/test'

const username = process.env.SPD_E2E_USERNAME || process.env.SPD_USERNAME
const password = process.env.SPD_E2E_PASSWORD || process.env.SPD_PASSWORD
if (!username || !password) {
  throw new Error('Business UI smoke tests require SPD_E2E_USERNAME/SPD_E2E_PASSWORD or SPD_USERNAME/SPD_PASSWORD')
}

async function login(page: import('@playwright/test').Page) {
  await page.getByRole('textbox', { name: '用户名' }).fill(username)
  await page.getByRole('textbox', { name: '密码' }).fill(password)
  await page.getByRole('button', { name: '登录' }).click()
}

test('收费耗材明细支持筛选、分页、空状态和横向表格操作', async ({ page }) => {
  await page.goto('/features/high-value-consumables')
  await login(page)
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
  await login(page)
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
  await login(page)

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
  await login(page)

  await expect(page.getByRole('heading', { name: '拣配记录' })).toBeVisible()
  await expect(page.getByText('暂无拣配记录')).toBeVisible()
})

test('科室申领从历史列表进入当前科室目录并按申领模式折算提交', async ({ page }) => {
  let catalogCallCount = 0
  let requisitionPostCount = 0
  let catalogRequestUrl = ''
  let submittedPayload: Record<string, unknown> | undefined
  let smartGeneratePayload: Record<string, unknown> | undefined
  const ok = (data: unknown) => JSON.stringify({
    code: 0,
    message: 'success',
    data,
    timestamp: new Date().toISOString()
  })

  await page.route('**/api/operational-closure/requisitions/options', route => route.fulfill({
    contentType: 'application/json',
    body: ok({
      departments: [{ deptCode: 'SURG', deptName: '手术室' }],
      warehouses: [],
      products: [],
      balances: []
    })
  }))
  await page.route('**/api/operational-closure/lists/requisition**', route => route.fulfill({
    contentType: 'application/json',
    body: ok({
      rows: [{
        bizNo: 'SL-HISTORY-001',
        deptName: '手术室',
        warehouseName: '手术室二级库',
        applicantName: '测试用户',
        createTime: '2026-08-29 09:00',
        totalQuantity: 3,
        totalAmount: 30,
        status: 'pending_approval'
      }],
      total: 1,
      page: 1,
      size: 20
    })
  }))
  await page.route('**/api/operational-closure/requisitions/departments/SURG/warehouses', route => route.fulfill({
    contentType: 'application/json',
    body: ok([{ warehouseId: 20, code: 'WH-SURG', name: '手术室二级库', selected: 1 }])
  }))
  await page.route('**/api/quota-packages/requisition-catalog**', route => {
    catalogCallCount += 1
    catalogRequestUrl = route.request().url()
    return route.fulfill({
      contentType: 'application/json',
      body: ok({
        rows: [
          {
            productId: 1,
            productCode: 'LOW-001',
            productName: '散货耗材',
            specModel: 'L',
            manufacturerName: '测试厂家',
            supplierName: '测试供应商',
            baseUnit: '支',
            purchaseUnit: '支',
            conversionRate: 1,
            unitPrice: 2,
            quotaManaged: 0,
            highValue: 0,
            centralized: 0,
            chargeable: 0,
            looseAvailableQty: 20,
            packageAvailableQty: 0,
            uniqueCodeAvailableQty: 0,
            templateCode: '-',
            templateName: '-',
            packageQuantity: null,
            packageUnit: '支',
            defaultMode: 'loose',
            allowedModes: ['loose'],
            sourceWarehouseId: 30,
            requisitionStatus: '散货申领'
          },
          {
            productId: 2,
            productCode: 'PKG-001',
            productName: '定数包耗材',
            specModel: 'P',
            manufacturerName: '测试厂家',
            supplierName: '测试供应商',
            baseUnit: '支',
            purchaseUnit: '包',
            conversionRate: 10,
            unitPrice: 1,
            quotaManaged: 1,
            highValue: 0,
            centralized: 0,
            chargeable: 0,
            looseAvailableQty: 50,
            packageAvailableQty: 3,
            uniqueCodeAvailableQty: 0,
            templateCode: 'TP-001',
            templateName: '手术室定数包',
            packageQuantity: 10,
            packageUnit: '支',
            defaultMode: 'quota_package',
            allowedModes: ['loose', 'quota_package'],
            sourceWarehouseId: 30,
            requisitionStatus: '定数包优先'
          },
          {
            productId: 3,
            productCode: 'HV-001',
            productName: '高值耗材',
            specModel: 'H',
            manufacturerName: '测试厂家',
            supplierName: '测试供应商',
            baseUnit: '个',
            purchaseUnit: '个',
            conversionRate: 1,
            unitPrice: 100,
            quotaManaged: 0,
            highValue: 1,
            centralized: 0,
            chargeable: 1,
            looseAvailableQty: 0,
            packageAvailableQty: 0,
            uniqueCodeAvailableQty: 2,
            templateCode: '-',
            templateName: '-',
            packageQuantity: null,
            packageUnit: '个',
            defaultMode: 'high_value',
            allowedModes: ['high_value'],
            sourceWarehouseId: 30,
            requisitionStatus: '高值耗材申领'
          }
        ],
        total: 3,
        page: 1,
        size: 20
      })
    })
  })
  await page.route('**/api/operational-closure/requisitions', async route => {
    requisitionPostCount += 1
    submittedPayload = route.request().postDataJSON()
    await route.fulfill({
      contentType: 'application/json',
      body: ok({ requisitionNo: 'SL-NEW-001', status: 'pending_approval', itemCount: 3 })
    })
  })
  await page.route('**/api/operational-closure/requisitions/smart-analysis', route => route.fulfill({
    contentType: 'application/json',
    body: ok({
      analysisId: 88,
      selectedPeriodDays: 7,
      groupCount: 1,
      rows: [{
        analysisItemId: 801,
        deptId: 10,
        deptName: '手术室',
        warehouseId: 20,
        warehouseName: '手术室二级库',
        sourceWarehouseId: 30,
        productCode: 'LOW-001',
        productName: '散货耗材',
        baseUnit: '支',
        itemMode: 'loose',
        periodDemand: 12,
        currentQty: 2,
        sourceAvailableQty: 5,
        shortageQty: 10,
        recommendedQty: 10
      }]
    })
  }))
  await page.route('**/api/operational-closure/requisitions/from-smart-analysis', async route => {
    smartGeneratePayload = route.request().postDataJSON()
    await route.fulfill({
      contentType: 'application/json',
      body: ok({ createdCount: 1, requisitionNos: ['SL-SMART-001'], status: 'generated' })
    })
  })

  await page.goto('/features/department-requisition')
  await login(page)

  await expect(page.getByText('SL-HISTORY-001')).toBeVisible()
  expect(catalogCallCount).toBe(0)
  await page.getByRole('button', { name: '新增申领' }).click()
  await expect(page.getByText('LOW-001')).toBeVisible()
  expect(catalogCallCount).toBeGreaterThan(0)
  const catalogUrl = new URL(catalogRequestUrl)
  expect(catalogUrl.searchParams.get('deptName')).toBe('手术室')
  expect(catalogUrl.searchParams.get('deptCode')).toBe('SURG')
  expect(catalogUrl.searchParams.get('warehouseName')).toBe('手术室二级库')
  expect(catalogUrl.searchParams.get('page')).toBe('1')
  expect(catalogUrl.searchParams.get('size')).toBe('20')

  await page.getByRole('button', { name: '智能补货' }).click()
  await expect(page.getByRole('dialog', { name: '智能补货分析' })).toBeVisible()
  await page.getByRole('dialog', { name: '智能补货分析' }).getByRole('spinbutton').fill('8')
  await page.getByRole('button', { name: '确认并生成申领单' }).click()
  await expect(page.getByText(/SL-SMART-001/)).toBeVisible()
  expect(smartGeneratePayload).toEqual({
    analysisId: 88,
    items: [{ analysisItemId: 801, quantity: 8, selected: true }]
  })

  const looseRow = page.locator('.requisition-catalog-table tbody tr').filter({ hasText: 'LOW-001' })
  const packageRow = page.locator('.requisition-catalog-table tbody tr').filter({ hasText: 'PKG-001' })
  const highValueRow = page.locator('.requisition-catalog-table tbody tr').filter({ hasText: 'HV-001' })
  await looseRow.locator('input[type="checkbox"]').check()
  await looseRow.locator('input[type="number"]').fill('2')
  await packageRow.locator('input[type="checkbox"]').check()
  await packageRow.locator('input[type="number"]').fill('2')
  await highValueRow.locator('input[type="checkbox"]').check()
  await highValueRow.locator('input[type="number"]').fill('2')
  await page.getByRole('button', { name: /提交申领/ }).click()
  await expect(page.getByText(/申请单号：SL-NEW-001/)).toBeVisible()
  expect(requisitionPostCount).toBe(1)
  expect(submittedPayload).toEqual({
    deptCode: 'SURG',
    deptName: '手术室',
    warehouseName: '手术室二级库',
    sourceWarehouseId: 30,
    items: [
      {
        productCode: 'LOW-001',
        quantity: 2,
        requisitionMode: 'loose'
      },
      {
        productCode: 'PKG-001',
        quantity: 20,
        requisitionMode: 'quota_package',
        templateCode: 'TP-001',
        packageCount: 2
      },
      {
        productCode: 'HV-001',
        quantity: 2,
        requisitionMode: 'high_value'
      }
    ]
  })
})
