import { expect, test, type Page } from '@playwright/test'

const envelope = (data: unknown) => ({ code: 0, message: '成功', data, timestamp: new Date().toISOString() })

async function setupApproval(page: Page, options: { final?: boolean; fail?: boolean; count?: number; delay?: number; returned?: boolean } = {}) {
  let approved = false
  let completedNo = ''
  let actions = 0
  let listReads = 0
  let hospitalReads = 0
  const detailReads: string[] = []
  const count = options.count ?? 2
  const application = (index: number) => `SP-APP-${String(index).padStart(3, '0')}`
  const detail = (no: string) => ({
    applicationNo: no, applicationType: '新品准入', approvalStatus: options.returned && !approved ? 'returned' : approved && no === completedNo ? options.final === false ? 'pending_step_2' : 'approved' : 'pending_step_1',
    statusLabel: approved && no === completedNo ? options.final === false ? '待复审' : '已通过' : '待初审',
    productName: `审批商品${no}`, productCode: no, specModel: '10ml', brand: '', manufacturerName: '测试厂家', supplierName: '测试供应商',
    unit: '支', purchasePrice: 1, retailPrice: null, minPurchaseQty: 1, purchaseUnit: '盒', conversionRate: 1, purchasePackageQty: null,
    udiCode: '', registrationNo: '', registrationExpireDate: '', productionLicenseNo: '', businessLicenseNo: '',
    volumeBased: false, centralizedProcurement: false, domestic: true, contractCode: '', firstCategory: '', secondCategory: '', thirdCategory: '',
    chargeable: true, tenderSubCode: '', qualificationAttachmentCount: 0, highValue: false, coldChain: false, quotaManaged: false,
    keyMonitored: false, storageCondition: '常温', applicant: '申请人', submitTime: '2026-10-22 09:00:00', changeItems: [],
    timeline: [{ title: '初审', operator: '审批人', time: '', status: 'active' }], canApprove: !options.returned && (!approved || no !== completedNo),
  })
  await page.addInitScript(() => localStorage.setItem('spd.token', 'approval-regression-fixture'))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const url = new URL(route.request().url())
    const path = url.pathname
    let data: unknown = {}
    if (path === '/api/auth/me') data = { userId: 1, username: 'reviewer', realName: '审批验证', status: 1, roles: ['ROLE_ADMIN'], permissionCodes: ['*'], menuCodes: ['*'], dataScope: 1 }
    else if (path === '/api/dashboard') data = { metrics: { productCount: 0, todayOrders: 0, lowStockCount: 0, monthlyPurchaseAmount: 0 }, trend: [], departments: [], exceptions: [], notices: [] }
    else if (path === '/api/modules') data = []
    else if (path.endsWith('/partner-options')) data = { manufacturers: [], suppliers: [] }
    else if (path === '/api/pending-product-applications') {
      listReads++
      const rows = Array.from({ length: count }, (_, i) => ({ no: application(i + 1), type: '新品准入', product: `审批商品${application(i + 1)}`, supplier: '测试供应商', applicant: '申请人', time: '2026-10-22', status: '待初审', statusTone: 'orange', warning: false }))
        .filter(row => !(approved && options.final !== false && row.no === completedNo))
      const size = Number(url.searchParams.get('size') || 25)
      const current = Number(url.searchParams.get('page') || 1)
      data = { rows: rows.slice((current - 1) * size, current * size), total: rows.length, page: current, size,
        typeCounts: [{ key: 'new', label: '新品准入', count: rows.length }, { key: 'mine-approved', label: '审批通过目录', count: approved ? 1 : 0 }] }
    } else if (path.startsWith('/api/pending-product-applications/') && path.endsWith('/action')) {
      actions++
      await new Promise(resolve => setTimeout(resolve, options.delay ?? 250))
      if (options.fail) return route.fulfill({ json: { code: 400, message: '该审批单已由其他人处理', data: null } })
      approved = true
      completedNo = path.split('/')[3]
      data = { applicationNo: path.split('/')[3], status: options.final === false ? 'pending_step_2' : 'approved' }
    } else if (path.endsWith('/attachments') || path.endsWith('/qualification-attachments')) data = []
    else if (path.startsWith('/api/pending-product-applications/')) {
      const no = path.split('/').pop()!
      detailReads.push(no)
      data = detail(no)
    } else if (path === '/api/master-data/hospital-products') {
      hospitalReads++
      const rows = [{ code: 'EXISTING', name: '原有目录商品', spec: '10ml', unit: '支', status: '启用' }]
      if (approved && options.final !== false) rows.push({ code: completedNo, name: `审批商品${completedNo}`, spec: '10ml', unit: '支', status: '启用' })
      data = { title: '医院目录', columns: ['商品编码', '商品名称', '规格型号', '单位', '状态'], rows, total: rows.length, page: 1, size: 20 }
    }
    await route.fulfill({ json: envelope(data) })
  })
  return { application, detailReads, actions: () => actions, listReads: () => listReads, hospitalReads: () => hospitalReads }
}

async function openFirstApproval(page: Page) {
  await page.locator('.approval-sticky-action .btn-primary').first().click()
  await expect(page.locator('.approval-detail-header h2')).toBeVisible()
}

async function openApprovalMenu(page: Page) {
  const nav = page.getByRole('navigation')
  await nav.getByText('基础数据管理', { exact: true }).click()
  const link = nav.getByRole('link', { name: '待审批目录', exact: true })
  if (!await link.isVisible()) await nav.getByText('商品目录', { exact: true }).click()
  await link.click()
}

test('直达详情返回保留审批范围和类型且只有一个业务标签', async ({ page }) => {
  await setupApproval(page)
  await page.goto('/features/pending-product-catalog/SP-APP-002?scope=mine&type=change&mineStatus=returned&keyword=abc&page=2')
  await expect(page.locator('.approval-detail-header h2')).toContainText('SP-APP-002')
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await expect(page).toHaveURL(/scope=mine/)
  const params = new URL(page.url()).searchParams
  expect(params.get('type')).toBe('change')
  expect(params.get('mineStatus')).toBe('returned')
  await expect(page.locator('.spd-tabbar-tab-label').filter({ hasText: /^待审批目录$/ })).toHaveCount(1)
})

test('未保存修改取消离开后仍可继续编辑', async ({ page }) => {
  await setupApproval(page)
  await page.goto('/features/pending-product-catalog/SP-APP-001')
  const name = page.locator('.approval-info-fields .edit-input').first()
  await expect(name).toBeVisible()
  await name.fill('未保存商品名称')
  page.once('dialog', dialog => dialog.dismiss())
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await expect(page).toHaveURL(/SP-APP-001/)
  await expect(name).toHaveValue('未保存商品名称')
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await expect(page.locator('.approval-wide-table')).toBeVisible()
})

test('A详情延迟返回不能覆盖已经显示的B详情', async ({ page }) => {
  await setupApproval(page)
  let releaseA!: () => void
  const held = new Promise<void>(resolve => { releaseA = resolve })
  await page.route('**/api/pending-product-applications/SP-APP-001', async route => {
    await held
    await route.fallback()
  })
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await page.locator('.approval-sticky-action .btn-primary').first().click()
  await expect(page).toHaveURL(/SP-APP-001/)
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await page.locator('.approval-sticky-action .btn-primary').nth(1).click()
  await expect(page.locator('.approval-detail-header h2')).toContainText('SP-APP-002')
  const delivered = page.waitForResponse(response => new URL(response.url()).pathname === '/api/pending-product-applications/SP-APP-001')
  releaseA()
  await delivered
  await expect(page.locator('.approval-detail-header h2')).toContainText('SP-APP-002')
})

for (const successCount of [0, 1, 2]) {
  test(`批量结果成功${successCount}条时准确显示部分失败并按需刷新医院目录`, async ({ page }) => {
    const state = await setupApproval(page)
    let requests = 0
    await page.route('**/api/pending-product-applications/batch-action', async route => {
      requests++
      await new Promise(resolve => setTimeout(resolve, 200))
      await route.fulfill({ json: envelope({ successCount, failCount: 2 - successCount }) })
    })
    await page.goto('/features/hospital-product-catalog')
    await expect(page.getByText('原有目录商品', { exact: true })).toBeVisible()
    const initial = state.hospitalReads()
    await openApprovalMenu(page)
    await page.locator('.approval-wide-table thead input[type=checkbox]').check()
    await page.getByRole('button', { name: '批量通过', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await expect(page.getByText(`已审批通过 ${successCount} 条${successCount < 2 ? `，${2 - successCount} 条操作失败` : ''}`, { exact: true })).toBeVisible()
    expect(requests).toBe(1)
    expect(state.listReads()).toBeGreaterThan(1)
    await page.locator('.spd-tabbar-tab').filter({ hasText: '医院目录' }).click()
    await expect(page.getByText('原有目录商品', { exact: true })).toBeVisible()
    if (successCount > 0) await expect.poll(state.hospitalReads).toBeGreaterThan(initial)
    else expect(state.hospitalReads()).toBe(initial)
  })
}

test('重新提交阻止重复请求并刷新列表而不刷新医院目录', async ({ page }) => {
  const state = await setupApproval(page, { returned: true })
  let requests = 0
  await page.route('**/api/pending-product-applications/SP-APP-001/resubmit', async route => {
    requests++
    await new Promise(resolve => setTimeout(resolve, 400))
    await route.fulfill({ json: envelope({ applicationNo: 'SP-APP-001', status: 'pending_step_1' }) })
  })
  await page.goto('/features/hospital-product-catalog')
  await expect(page.getByText('原有目录商品', { exact: true })).toBeVisible()
  const initialHospital = state.hospitalReads()
  await openApprovalMenu(page)
  await openFirstApproval(page)
  await page.getByRole('button', { name: '修改后重新提交', exact: true }).click()
  const submit = page.getByRole('dialog').getByRole('button', { name: '重新提交', exact: true })
  await submit.evaluate((el: HTMLButtonElement) => { el.click(); el.click() })
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(requests).toBe(1)
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  expect(state.listReads()).toBeGreaterThan(1)
  await page.locator('.spd-tabbar-tab').filter({ hasText: '医院目录' }).click()
  await expect(page.getByText('原有目录商品', { exact: true })).toBeVisible()
  expect(state.hospitalReads()).toBe(initialHospital)
})

test('审批后缓存首页待办在重新激活时刷新', async ({ page }) => {
  const state = await setupApproval(page)
  let homeReads = 0
  await page.route('**/api/dashboard/workbench', async route => {
    homeReads++
    const day = { date: '2026-10-22', salesAmount: 0, salesQuantity: 0, inboundQuantity: 0, outboundQuantity: 0, inboundAmount: 0, outboundAmount: 0, purchaseAmount: 0 }
    await route.fulfill({ json: envelope({ date: day.date, metrics: day, trend: Array.from({ length: 30 }, () => day), alerts: { lowStock: 0, expiry: 0, quality: 0, shortage: 0, license: 0, stagnant: 0 }, todos: { rows: [], total: state.actions() ? 0 : 1 }, notices: [], definitions: { expiryWarningDays: 30, stagnantDays: 90 } }) })
  })
  await page.goto('/')
  await expect.poll(() => homeReads).toBeGreaterThan(0)
  await openApprovalMenu(page)
  await openFirstApproval(page)
  await page.getByRole('button', { name: '初审通过', exact: true }).click()
  await expect(page.locator('.approval-header-meta .status-pill')).toHaveText('已通过')
  const previous = homeReads
  await page.locator('.spd-tabbar-tab').filter({ hasText: '工作台' }).click()
  await expect.poll(() => homeReads).toBeGreaterThan(previous)
})

test('取消关闭有脏编辑的活动详情标签后标签保留', async ({ page }) => {
  await setupApproval(page)
  await page.goto('/features/pending-product-catalog/SP-APP-001')
  await page.locator('.approval-info-fields .edit-input').first().fill('保留修改')
  page.once('dialog', dialog => dialog.dismiss())
  const tab = page.locator('.spd-tabbar-tab').filter({ hasText: '待审批目录' })
  await tab.locator('.spd-tabbar-tab-close').click()
  await expect(page).toHaveURL(/SP-APP-001/)
  await expect(tab).toHaveCount(1)
})

test('A审批意见不带入B详情', async ({ page }) => {
  await setupApproval(page)
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await openFirstApproval(page)
  await page.locator('.approval-opinion-card textarea').fill('A专属意见')
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await page.locator('.approval-sticky-action .btn-primary').nth(1).click()
  await expect(page.locator('.approval-detail-header h2')).toContainText('SP-APP-002')
  await expect(page.locator('.approval-opinion-card textarea')).toHaveValue('')
})

test('审批提交期间所有编辑输入被禁用', async ({ page }) => {
  await setupApproval(page, { delay: 1000 })
  await page.goto('/features/pending-product-catalog/SP-APP-001')
  await page.getByRole('button', { name: '初审通过', exact: true }).click()
  await expect(page.locator('.approval-info-fields .edit-input').first()).toBeDisabled()
  await expect(page.locator('.approval-opinion-card textarea')).toBeDisabled()
  await expect(page.locator('.approval-header-meta .status-pill')).toHaveText('已通过')
})
