import { expect, test, type Page } from '@playwright/test'

const envelope = (data: unknown) => ({ code: 0, message: '成功', data, timestamp: new Date().toISOString() })

async function setupApproval(page: Page, options: { final?: boolean; fail?: boolean; count?: number; delay?: number } = {}) {
  let approved = false
  let completedNo = ''
  let actions = 0
  let listReads = 0
  let hospitalReads = 0
  const detailReads: string[] = []
  const count = options.count ?? 2
  const application = (index: number) => `SP-APP-${String(index).padStart(3, '0')}`
  const detail = (no: string) => ({
    applicationNo: no, applicationType: '新品准入', approvalStatus: approved && no === completedNo ? options.final === false ? 'pending_step_2' : 'approved' : 'pending_step_1',
    statusLabel: approved && no === completedNo ? options.final === false ? '待复审' : '已通过' : '待初审',
    productName: `审批商品${no}`, productCode: no, specModel: '10ml', brand: '', manufacturerName: '测试厂家', supplierName: '测试供应商',
    unit: '支', purchasePrice: 1, retailPrice: null, minPurchaseQty: 1, purchaseUnit: '盒', conversionRate: 1, purchasePackageQty: null,
    udiCode: '', registrationNo: '', registrationExpireDate: '', productionLicenseNo: '', businessLicenseNo: '',
    volumeBased: false, centralizedProcurement: false, domestic: true, contractCode: '', firstCategory: '', secondCategory: '', thirdCategory: '',
    chargeable: true, tenderSubCode: '', qualificationAttachmentCount: 0, highValue: false, coldChain: false, quotaManaged: false,
    keyMonitored: false, storageCondition: '常温', applicant: '申请人', submitTime: '2026-10-22 09:00:00', changeItems: [],
    timeline: [{ title: '初审', operator: '审批人', time: '', status: 'active' }], canApprove: !approved || no !== completedNo,
  })
  await page.addInitScript(() => localStorage.setItem('spd.token', 'approval-regression-fixture'))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const url = new URL(route.request().url())
    const path = url.pathname
    let data: unknown = {}
    if (path === '/api/auth/me') data = { userId: 1, username: 'reviewer', realName: '审批验证', status: 1, roles: ['ROLE_ADMIN'], permissionCodes: ['*'], menuCodes: ['*'], dataScope: 1 }
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

test('列表和审批详情始终只占一个待审批目录标签', async ({ page }) => {
  await setupApproval(page)
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await openFirstApproval(page)
  await expect(page.locator('.spd-tabbar-tab-label').filter({ hasText: /^待审批目录$/ })).toHaveCount(1)
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await expect(page.locator('.approval-wide-table')).toBeVisible()
  await expect(page.locator('.spd-tabbar-tab-label').filter({ hasText: /^待审批目录$/ })).toHaveCount(1)
})

test('审批完成返回列表后自动更新记录和队列计数', async ({ page }) => {
  const state = await setupApproval(page)
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await openFirstApproval(page)
  await page.getByRole('button', { name: '初审通过', exact: true }).click()
  await expect(page.locator('.approval-header-meta .status-pill')).toHaveText('已通过')
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await expect(page.locator('.approval-wide-table tbody tr')).toHaveCount(1)
  await expect(page.locator('.approval-wide-table tbody')).not.toContainText(state.application(1))
  await expect(page.locator('.approval-sub-list button.active em')).toContainText('1')
  expect(state.listReads()).toBeGreaterThan(1)
})

test('在同一标签切换审批单时加载对应详情', async ({ page }) => {
  const state = await setupApproval(page)
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await openFirstApproval(page)
  await expect(page.locator('.approval-detail-header h2')).toContainText(state.application(1))
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await page.locator('.approval-sticky-action .btn-primary').nth(1).click()
  await expect(page.locator('.approval-detail-header h2')).toContainText(state.application(2))
  expect(state.detailReads).toContain(state.application(2))
})

test('最终通过后切回已缓存医院目录会自动读取新商品', async ({ page }) => {
  const state = await setupApproval(page)
  await page.goto('/features/hospital-product-catalog')
  await expect(page.getByText('原有目录商品', { exact: true })).toBeVisible()
  await openApprovalMenu(page)
  await openFirstApproval(page)
  await page.getByRole('button', { name: '初审通过', exact: true }).click()
  await expect(page.locator('.approval-header-meta .status-pill')).toHaveText('已通过')
  const priorReads = state.hospitalReads()
  await page.locator('.spd-tabbar-tab').filter({ hasText: '医院目录' }).click()
  await expect(page.getByText(`审批商品${state.application(1)}`, { exact: true })).toBeVisible()
  expect(state.hospitalReads()).toBeGreaterThan(priorReads)
})

test('中间节点通过不使医院目录误认为已有新商品', async ({ page }) => {
  const state = await setupApproval(page, { final: false })
  await page.goto('/features/hospital-product-catalog')
  await expect(page.getByText('原有目录商品', { exact: true })).toBeVisible()
  await openApprovalMenu(page)
  await openFirstApproval(page)
  await page.getByRole('button', { name: '初审通过', exact: true }).click()
  await expect(page.locator('.approval-header-meta .status-pill')).toHaveText('待复审')
  const priorReads = state.hospitalReads()
  await page.locator('.spd-tabbar-tab').filter({ hasText: '医院目录' }).click()
  await expect(page.getByText('原有目录商品', { exact: true })).toBeVisible()
  await expect(page.getByText(`审批商品${state.application(1)}`, { exact: true })).toHaveCount(0)
  expect(state.hospitalReads()).toBe(priorReads)
})

test('提交期间禁用审批和离开操作且只发送一次请求', async ({ page }) => {
  const state = await setupApproval(page, { delay: 1000 })
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await openFirstApproval(page)
  const approve = page.getByRole('button', { name: '初审通过', exact: true })
  await approve.click()
  await expect(approve).toBeDisabled()
  await expect(page.getByRole('button', { name: '返回待审批目录' })).toBeDisabled()
  await page.locator('.spd-tabbar-tab').filter({ hasText: '工作台' }).click()
  await expect(page).toHaveURL(new RegExp(state.application(1)))
  await expect(page.locator('.approval-header-meta .status-pill')).toHaveText('已通过')
  expect(state.actions()).toBe(1)
})

test('审批失败保留详情和原列表且允许重试', async ({ page }) => {
  const state = await setupApproval(page, { fail: true })
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await openFirstApproval(page)
  await page.getByRole('button', { name: '初审通过', exact: true }).click()
  await expect(page.getByText('该审批单已由其他人处理', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '初审通过', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await expect(page.locator('.approval-wide-table tbody tr')).toHaveCount(2)
  expect(state.listReads()).toBe(1)
})

test('查看详情返回后保留查询页码和滚动位置', async ({ page }) => {
  await setupApproval(page, { count: 60 })
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  const search = page.getByPlaceholder('搜索审批单号、申请供应商、商品名称...')
  await search.fill('审批商品')
  await search.press('Enter')
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('.hospital-pagination-summary')).toContainText('26-50')
  const saved = await page.locator('.spd-content').evaluate(el => { el.scrollTop = 500; return el.scrollTop })
  await page.locator('.approval-sticky-action .btn-primary').first().evaluate((el: HTMLElement) => el.click())
  await expect(page.locator('.approval-detail-header h2')).toContainText('SP-APP-026')
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await expect(search).toHaveValue('审批商品')
  await expect(page.locator('.hospital-pagination-summary')).toContainText('26-50')
  await expect.poll(() => page.locator('.spd-content').evaluate(el => el.scrollTop)).toBe(saved)
})

test('审批后最后一页为空时回退到有效页码', async ({ page }) => {
  const state = await setupApproval(page, { count: 26 })
  await page.goto('/features/pending-product-catalog?scope=todo&type=new')
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('.hospital-pagination-summary')).toContainText('26-26')
  await openFirstApproval(page)
  await page.getByRole('button', { name: '初审通过', exact: true }).click()
  await expect(page.locator('.approval-header-meta .status-pill')).toHaveText('已通过')
  await page.getByRole('button', { name: '返回待审批目录' }).click()
  await expect(page.locator('.hospital-pagination-summary')).toContainText('1-25')
  await expect(page.locator('.approval-wide-table tbody')).not.toContainText(state.application(26))
})
