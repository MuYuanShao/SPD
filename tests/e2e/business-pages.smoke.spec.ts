import { expect, test } from '@playwright/test'

const username = process.env.SPD_E2E_USERNAME || process.env.SPD_USERNAME
const password = process.env.SPD_E2E_PASSWORD || process.env.SPD_PASSWORD
if (!username || !password) {
  throw new Error('Business UI smoke tests require SPD_E2E_USERNAME/SPD_E2E_PASSWORD or SPD_USERNAME/SPD_PASSWORD')
}

async function login(page: import('@playwright/test').Page) {
  await page.getByRole('textbox', { name: '用户名' }).fill(username)
  await page.getByRole('textbox', { name: '密码' }).fill(password)
  await page.getByRole('button', { name: /^登\s*录$/ }).click()
}

test('Vite 代理保留局域网同源登录的 Host', async ({ request }) => {
  const host = '192.0.2.1:1820'
  const response = await request.post('/api/auth/login', {
    headers: { Host: host, Origin: `http://${host}` },
    data: { username, password }
  })
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  expect(body.code).toBe(0)
  expect(Boolean(body.data?.token)).toBe(true)
})

test('账户菜单修改密码支持校验、失败重试和重新登录', async ({ page }) => {
  let attempts = 0
  await page.route(url => url.pathname === '/api/auth/password', async route => {
    attempts += 1
    expect(route.request().method()).toBe('PUT')
    expect(route.request().headers().authorization).toMatch(/^Bearer /)
    expect(route.request().postDataJSON()).toEqual({
      currentPassword: 'current-test-password',
      newPassword: 'new-test-password'
    })
    if (attempts === 2) return route.fulfill({ status: 429, body: 'Too Many Requests' })
    await route.fulfill({
      json: attempts === 1 ? { code: 400, message: '当前密码不正确' } : { code: 0, data: null }
    })
  })

  await page.goto('/')
  await login(page)
  await page.locator('.spd-user-btn').click()
  await page.getByText('修改密码', { exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '修改密码' })
  await dialog.getByLabel('当前密码', { exact: true }).fill('current-test-password')
  await dialog.getByLabel('新密码', { exact: true }).fill('new-test-password')
  await dialog.getByLabel('确认新密码', { exact: true }).fill('different-password')
  const save = dialog.getByRole('button', { name: '保存修改' })
  await save.click()
  await expect(dialog.getByRole('alert')).toContainText('两次输入的新密码不一致')
  expect(attempts).toBe(0)

  await dialog.getByLabel('确认新密码', { exact: true }).fill('new-test-password')
  await save.click()
  await expect(dialog.getByRole('alert')).toContainText('当前密码不正确')
  await save.click()
  await expect(dialog.getByRole('alert')).toContainText('修改密码请求过于频繁，请稍后再试')
  await save.click()
  await expect(page).toHaveURL(/\/login$/)
  expect(attempts).toBe(3)
})

test('新增院区时留空自动生成的编码也能提交', async ({ page }) => {
  let payload: Record<string, unknown> | undefined
  await page.route(url => url.pathname === '/api/master-data/campuses', async route => {
    if (route.request().method() !== 'POST') return route.continue()
    payload = route.request().postDataJSON()
    await route.fulfill({ json: { code: 0, data: { campusCode: 'XQ999' } } })
  })

  await page.goto('/features/campus-management')
  await login(page)
  await page.getByRole('button', { name: '新增院区', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新增院区' })
  const code = dialog.locator('input[name="campusCode"]')
  await expect(code).toBeDisabled()
  await expect(code).toHaveValue('')
  await dialog.getByRole('textbox', { name: '院区名称' }).fill('院区回归测试')
  await dialog.getByRole('button', { name: '创建院区' }).click()
  await expect(dialog).toBeHidden()
  expect(payload).toMatchObject({ campusCode: '', campusName: '院区回归测试' })
})

test('定数包模板的打包按钮跳转并保留第二页选中的模板', async ({ page }) => {
  const template = {
    templateId: 21, templateCode: 'TPL-SMOKE-021', templateName: '第二页测试模板',
    productCode: 'SMOKE-021', productName: '测试耗材', quantity: 5, unit: '支',
    status: '启用', versionNo: 1, currentVersion: 1
  }
  const ok = (data: unknown) => ({ code: 0, data })
  const submittedVersions: number[] = []
  const templates = [
    ...Array.from({ length: 20 }, (_, index) => ({
      ...template, templateId: index + 1, templateCode: `TPL-SMOKE-${String(index + 1).padStart(3, '0')}`,
      status: index === 0 ? '禁用' : '启用'
    })),
    template
  ]
  await page.route(url => url.pathname === '/api/quota-packages/templates', route => {
    const params = new URL(route.request().url()).searchParams
    const pageNo = Number(params.get('page'))
    const size = Number(params.get('size'))
    const rows = templates.slice((pageNo - 1) * size, pageNo * size)
    return route.fulfill({ json: ok({ rows, page: pageNo, size, total: templates.length }) })
  })
  await page.route(url => url.pathname === '/api/quota-packages/packing-options', route => route.fulfill({
    json: ok({ warehouses: [{ warehouseName: '测试中心库', warehouseType: 'central' }], candidates: [] })
  }))
  await page.route(url => url.pathname === '/api/quota-packages/packing-tasks', route => {
    if (route.request().method() === 'POST') {
      const id = route.request().postDataJSON().expectedTemplateId
      submittedVersions.push(id)
      if (id === 21 || id === 22) {
        return route.fulfill({ json: ok({
          created: false, requiresConfirmation: true,
          latestTemplate: { ...template, templateId: id + 1, versionNo: id - 19, quantity: id - 15 }
        }) })
      }
      return route.fulfill({ json: ok({
        created: true, requiresConfirmation: false, requestedPackageCount: 1,
        packageCount: 1, taskNo: 'PT-SMOKE-CREATED', reservedLooseQty: 7
      }) })
    }
    return route.fulfill({ json: ok({
      rows: [{ taskNo: 'PT-SMOKE-READY', templateName: '任务列表已加载', status: 'pending_confirm' }],
      page: 1, size: 20, total: 1
    }) })
  })

  await page.goto('/features/quota-template-maintenance')
  await login(page)
  const disabledPack = page.getByRole('row').filter({ hasText: 'TPL-SMOKE-001' })
    .getByRole('button', { name: '打包', exact: true })
  await expect(disabledPack).toBeDisabled()
  await disabledPack.evaluate((button: HTMLButtonElement) => button.click())
  await expect(page).toHaveURL(/\/features\/quota-template-maintenance$/)
  expect(submittedVersions).toEqual([])
  await page.getByRole('button', { name: '下一页' }).click()
  const row = page.getByRole('row').filter({ hasText: template.templateCode })
  await row.getByRole('button', { name: '打包', exact: true }).click()
  await expect(page).toHaveURL(/\/features\/packing-task-confirmation$/)
  await expect(page.getByRole('row').filter({ hasText: 'PT-SMOKE-READY' })).toBeVisible()
  await expect(page.locator('.pt-selected-template')).toContainText(template.templateCode)
  const create = page.getByRole('button', { name: '新建任务', exact: true })
  await expect(create).toBeEnabled()
  expect(submittedVersions).toEqual([])

  await create.click()
  const changes = page.getByRole('dialog', { name: '打包模板已更新' })
  await expect(changes).toContainText('每包：5 支')
  await expect(changes).toContainText('每包：6 支')
  await changes.getByRole('button', { name: '返回' }).click()
  await expect(changes).toBeHidden()
  await expect(create).toBeEnabled()
  expect(submittedVersions).toEqual([21])

  await create.click()
  await changes.getByRole('button', { name: '确认使用新版本' }).click()
  const nextChanges = changes.filter({ hasText: '每包：7 支' })
  await expect(nextChanges).toContainText('每包：6 支')
  expect(submittedVersions).toEqual([21, 21, 22])
  await nextChanges.getByRole('button', { name: '确认使用新版本' }).click()
  await expect(page.getByText(/打包任务创建成功：PT-SMOKE-CREATED/)).toBeVisible()
  expect(submittedVersions).toEqual([21, 21, 22, 23])
})

test('采购订单提交期间禁止重复发送并刷新订单状态', async ({ page }) => {
  const orderNo = 'CG-SMOKE-DRAFT'
  let orderStatus = 'draft'
  let calls = 0
  let releaseSubmit!: () => void
  const submitted = new Promise<void>(resolve => { releaseSubmit = resolve })
  await page.route(url => url.pathname === '/api/purchase-orders', route => route.fulfill({
    json: { code: 0, data: { rows: [{ orderNo, orderStatus, totalAmount: 12 }], total: 1, page: 1, size: 20 } }
  }))
  await page.route(`**/api/purchase-orders/${orderNo}/action`, async route => {
    calls += 1
    expect(route.request().method()).toBe('PUT')
    expect(route.request().postDataJSON().action).toBe('submit')
    await submitted
    orderStatus = 'pending_approval'
    await route.fulfill({ json: { code: 0, data: { orderNo, status: orderStatus } } })
  })

  await page.goto('/features/purchase-management')
  await login(page)
  await page.getByRole('button', { name: '采购订单', exact: true }).click()
  const row = page.locator('tbody tr').filter({ hasText: orderNo })
  await row.click()
  const submit = page.getByRole('button', { name: '提交', exact: true })
  try {
    await submit.click()
    await expect(submit).toBeDisabled()
    await submit.evaluate((button: HTMLButtonElement) => button.click())
    await expect.poll(() => calls).toBe(1)
  } finally {
    releaseSubmit()
  }
  await expect(row).toContainText('待审批')
  await expect(submit).toBeDisabled()
  expect(calls).toBe(1)
})

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

  const firstUniqueCell = table.locator('tbody tr').first().getByTestId('charge-unique-code')
  const uniqueCode = (await firstUniqueCell.textContent())?.trim()
  expect(uniqueCode).toBeTruthy()
  await page.getByRole('textbox', { name: '唯一码', exact: true }).fill(uniqueCode!)
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

test('待审批目录取消字段导航后仍可设置列和横向滚动', async ({ page }) => {
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await login(page)
  await expect(page.getByRole('heading', { name: '新品准入 · 多字段列表' })).toBeVisible()
  await expect(page.locator('.approval-field-nav')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '列设置', exact: true })).toBeVisible()
  await expect(page.locator('.approval-wide-table thead')).toContainText('变更记录')
  const table = page.locator('.approval-wide-table-shell')
  await expect.poll(() => table.evaluate(el => {
    el.scrollLeft = el.scrollWidth
    return el.scrollLeft
  })).toBeGreaterThan(0)
})

test('AI医护助手的自定义补货问题返回补货建议入口', async ({ page }) => {
  test.setTimeout(150_000)
  await page.goto('/')
  await login(page)

  await page.getByRole('button', { name: '打开AI医护助手' }).click()
  await page.getByRole('textbox', { name: '向AI医护助手提问' }).fill('请生成补货建议')
  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith('/api/ai-assistant/chat') && response.request().method() === 'POST',
    { timeout: 130_000 })
  await page.getByRole('button', { name: '发送问题' }).click()
  const response = await responsePromise
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  expect(body.code).toBe(0)
  expect(body.data.model).toBe('qwen3-vl:8b')
  expect(body.data.text.trim()).toBeTruthy()
  await expect(page.locator('.ai-response-text').last()).toHaveText(body.data.text)
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

test('拣配记录弹窗逐条显示完整定数包码', async ({ page }) => {
  const labelCodes = Array.from({ length: 80 }, (_, index) => `D2026102300${String(index + 1).padStart(4, '0')}`)
  const order = { deliveryId: 1, bizNo: 'PS-DETAIL-001', status: 'signed', productName: '测试耗材' }
  await page.route('**/api/operational-closure/lists/delivery**', route => route.fulfill({
    json: { code: 0, data: {
      rows: [{ ...order, labelCodes }, { ...order, deliveryId: 2, bizNo: 'PS-DETAIL-002', labelCodes: [] }],
      total: 2, page: 1, size: 20
    } }
  }))
  await page.goto('/features/picking-records')
  await login(page)
  const withoutCodes = page.getByRole('row').filter({ hasText: 'PS-DETAIL-002' })
  await expect(withoutCodes).toBeVisible()
  await expect(withoutCodes.getByRole('button', { name: /查看详情/ })).toHaveCount(0)
  await page.getByRole('button', { name: '查看详情（80）' }).click()
  const dialog = page.getByRole('dialog', { name: '定数包码详情' })
  await expect(dialog).toContainText('PS-DETAIL-001')
  const codes = dialog.getByRole('region', { name: '完整定数包码列表' })
  await expect(codes.getByRole('listitem')).toHaveText(labelCodes)
  await codes.getByRole('listitem').last().scrollIntoViewIfNeeded()
  await expect(codes.getByRole('listitem').last()).toBeInViewport()
  await dialog.getByRole('button', { name: '关闭', exact: true }).click()
  await expect(dialog).toBeHidden()
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
            looseAvailableQty: 1,
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
            packageAvailableQty: 1,
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
  const selectedLoose = page.locator('.dept-req-selected-list li').filter({ hasText: 'LOW-001' })
  await selectedLoose.getByRole('spinbutton', { name: '申领数量' }).fill('12.5')
  await expect(selectedLoose.getByRole('spinbutton', { name: '申领数量' })).not.toHaveValue('12.5')
  // Also exercise submission validation when a script bypasses native input prevention.
  await selectedLoose.getByRole('spinbutton', { name: '申领数量' }).evaluate(element => {
    const input = element as HTMLInputElement
    input.value = '12.5'
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
  await selectedLoose.getByRole('spinbutton', { name: '申领数量' }).blur()
  await page.getByRole('button', { name: /提交申领/ }).click()
  await expect(page.getByText(/申领数量必须为.*整数，不支持小数/).first()).toBeVisible()
  expect(requisitionPostCount).toBe(0)
  await selectedLoose.getByRole('spinbutton', { name: '申领数量' }).fill('12')
  await selectedLoose.getByRole('spinbutton', { name: '申领数量' }).blur()
  await selectedLoose.getByRole('button', { name: '增加申领数量' }).click()
  await expect(selectedLoose.getByRole('spinbutton', { name: '申领数量' })).toHaveValue('13')
  await selectedLoose.getByRole('button', { name: '减少申领数量' }).click()
  await expect(looseRow.getByRole('spinbutton', { name: '申领数量' })).toHaveValue('12')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(selectedLoose.getByRole('spinbutton', { name: '申领数量' })).toHaveValue('12')
  await expect(looseRow.getByRole('spinbutton', { name: '申领数量' })).toHaveValue('12')
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
      },
      { productCode: 'LOW-001', quantity: 12, requisitionMode: 'loose' }
    ]
  })
  await expect(page.locator('.dept-req-selected')).toHaveCount(0)
  await expect(looseRow.getByRole('spinbutton', { name: '申领数量' })).toHaveValue('1')
})

test('收货验收按需加载选项并正确联动采购与临时来源', async ({ page }) => {
  let optionCalls = 0
  page.on('request', request => {
    if (request.url().includes('/api/receiving-orders/options/')) optionCalls += 1
  })

  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('/features/receiving-acceptance')
  await login(page)
  await expect(page.getByRole('heading', { name: '收货验收' })).toBeVisible()
  expect(optionCalls).toBe(0)

  await page.getByRole('button', { name: '新增收货' }).click()
  const dialog = page.getByRole('dialog', { name: '新增收货单' })
  await expect(dialog).toBeVisible()
  await expect.poll(() => optionCalls).toBeGreaterThanOrEqual(4)
  await expect(dialog.getByRole('button', { name: '删除勾选' })).toBeDisabled()
  await expect(dialog.getByRole('button', { name: '复制细单' })).toBeDisabled()

  await dialog.getByLabel('收货来源').selectOption('temporary')
  await expect(dialog.getByLabel('配送商')).toBeEnabled()
  await expect(dialog.getByTestId('receiving-purchase-order')).toHaveCount(0)

  await dialog.getByLabel('商品码扫描').fill('NO-SUCH-RECEIVING-PRODUCT')
  await dialog.getByLabel('商品码扫描').press('Enter')
  await expect(page.getByText('未找到启用的商品编码：NO-SUCH-RECEIVING-PRODUCT')).toBeVisible()
})
