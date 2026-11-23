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

test('公告加载失败可重试，同一公告只分配给一次登录', async ({ browser }) => {
  const firstSession = await browser.newContext()
  const secondSession = await browser.newContext()
  let owner: string | null = null
  let failures = 0
  let acknowledged = false
  const sample = { id: '1', title: '系统维护', content: '维护期间请保存工作。', date: '2026-11-17 09:00' }
  const deliveryPath = '**/api/system-notices/login-delivery?*'
  try {
    const first = await firstSession.newPage()
    const second = await secondSession.newPage()
    for (const page of [first, second]) {
      await page.route(deliveryPath, route => {
        const loginId = new URL(route.request().url()).searchParams.get('loginId')
        expect(loginId).toBeTruthy()
        owner ??= loginId
        if (page === first && failures < 2) {
          expect(loginId).toBe(owner)
          failures++
          return route.fulfill({ status: 503, json: { code: 503, message: '暂时不可用' } })
        }
        return route.fulfill({ json: { code: 0, data: !acknowledged && loginId === owner ? sample : null } })
      })
    }
    await first.route('**/api/system-notices/1/read', route => {
      expect(route.request().method()).toBe('PUT')
      acknowledged = true
      return route.fulfill({ json: { code: 0, data: null } })
    })
    await first.goto('/')
    await login(first)
    const dialog = first.getByRole('dialog', { name: '系统公告', exact: true })
    await expect(dialog.getByRole('alert')).toHaveText('系统公告加载失败，请重试')
    await dialog.getByRole('button', { name: '重试' }).click()
    await expect(dialog.getByText(sample.title)).toBeVisible()

    await second.goto('/')
    const delivery = second.waitForResponse(deliveryPath)
    await login(second)
    expect((await (await delivery).json()).data).toBeNull()
    await expect(second.getByRole('dialog', { name: '系统公告', exact: true })).toBeHidden()

    await dialog.getByRole('button', { name: '我知道了' }).click()
    await expect(dialog).toBeHidden()
    expect(acknowledged).toBeTruthy()
  } finally {
    await firstSession.close()
    await secondSession.close()
  }
})

test('供应商和厂家操作按当前状态切换启用与停用', async ({ browser }) => {
  for (const [tab, endpoint, codeField, label] of [
    ['supplier', 'suppliers', 'supplierCodes', '供应商'],
    ['manufacturer', 'manufacturers', 'manufacturerCodes', '厂家']
  ]) {
    const page = await browser.newPage()
    try {
      let status = 0
      await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
      await page.route(new RegExp(`/api/master-data/${endpoint}(?:\\?|$)`), route => route.fulfill({ json: { code: 0, data: {
        rows: [{ code: 'PARTNER-01', name: '测试往来单位', status: status ? '启用' : '停用' }],
        total: 1, page: 1, size: 20
      } } }))
      await page.route(`**/api/master-data/${endpoint}/status`, route => {
        expect(route.request().method()).toBe('PUT')
        const nextStatus = status ? 0 : 1
        expect(route.request().postDataJSON()).toEqual({ [codeField]: ['PARTNER-01'], status: nextStatus })
        status = nextStatus
        return route.fulfill({ json: { code: 0, data: { updatedRows: 1 } } })
      })
      await page.goto(`/features/supplier-manufacturer-management?tab=${tab}`)
      await login(page)
      const row = page.getByRole('row').filter({ hasText: 'PARTNER-01' })
      for (const [action, next] of [['启用', '停用'], ['停用', '启用']]) {
        await row.getByRole('button', { name: action, exact: true }).click()
        const dialog = page.getByRole('dialog', { name: `${action}${label}` })
        await dialog.getByRole('button', { name: `确认${action}${label}` }).click()
        await expect(row.getByRole('button', { name: next, exact: true })).toBeVisible()
        await expect(row.getByRole('button', { name: action, exact: true })).toHaveCount(0)
      }
    } finally {
      await page.close()
    }
  }
})

test('打印模板新增字段在按钮旁显示校验和添加结果', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  const fixture = {
    id: 1, templateCode: 'quota_label', templateType: 'quota_label', templateName: '标签模板',
    paperWidthMm: 40, paperHeightMm: 60, status: 1,
    fieldsJson: JSON.stringify([{ code: 'labelNo', label: '标签号', enabled: true }])
  }
  await page.route('**/api/print-templates', route => route.fulfill({ json: { code: 0, data: [fixture] } }))
  await page.route('**/api/print-templates/quota_label', route => {
    const payload = route.request().postDataJSON()
    expect(route.request().method()).toBe('PUT')
    expect(JSON.parse(payload.fieldsJson)).toContainEqual({ code: 'operator', label: '操作员', enabled: true })
    return route.fulfill({ json: { code: 0, data: { templateType: 'quota_label' } } })
  })
  await page.goto('/features/print-template-settings')
  await login(page)
  const dialog = page.getByRole('dialog')
  await page.getByRole('button', { name: '调整模板' }).first().click()
  const addField = dialog.getByRole('button', { name: '新增字段', exact: true })
  const code = dialog.getByRole('textbox', { name: '新字段编码' })
  await addField.click()
  const alert = dialog.getByRole('alert')
  await expect(alert).toHaveText('请填写新增字段的编码')
  await expect(alert).toBeInViewport()

  const label = dialog.getByRole('textbox', { name: '新字段名称' })
  await code.fill('operator')
  await label.fill('操作员')
  await addField.click()
  const operator = dialog.getByRole('checkbox', { name: '启用字段 operator' })
  await expect(operator).toBeChecked()
  await expect(dialog.getByRole('status')).toContainText('已添加「操作员」')

  await dialog.locator('.md-print-template-field-row').filter({
    has: page.getByRole('checkbox', { name: '启用字段 operator' })
  })
    .getByRole('button', { name: '删除字段' }).click()
  await expect(dialog.getByRole('status')).toHaveCount(0)
  await expect(operator).toHaveCount(0)

  await code.fill('operator')
  await label.fill('操作员')
  await addField.click()
  await code.fill('operator')
  await addField.click()
  await expect(alert).toHaveText('该字段编码已存在')
  await expect(alert).toBeInViewport()
  await expect(operator).toHaveCount(1)
  await dialog.getByRole('button', { name: '保存模板' }).click()
  await expect(dialog).toBeHidden()
})

test('管理员可提交含空格和换行的公告并收到发布提示', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/system-notices', route => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().postDataJSON()).toEqual({ title: 'SPD 系统维护', content: '第一段内容\n第二段内容' })
    return route.fulfill({ json: { code: 0, data: null } })
  })
  await page.goto('/')
  await login(page)
  await page.getByRole('button', { name: '新增公告', exact: true }).click()
  const editor = page.getByRole('dialog', { name: '新增系统公告' })
  await editor.getByLabel('公告标题').fill('SPD 系统维护')
  await editor.getByLabel('公告正文').fill('第一段内容\n第二段内容')
  await editor.getByRole('button', { name: '发布公告' }).click()
  await expect(page.getByText('公告已发布', { exact: true })).toBeVisible()
})

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

  await dialog.getByLabel('新密码', { exact: true }).fill('new test password')
  await dialog.getByLabel('确认新密码', { exact: true }).fill('new test password')
  await save.click()
  await expect(dialog.getByRole('alert')).toContainText('不支持空格及换行符存在！')
  expect(attempts).toBe(0)

  await dialog.getByLabel('新密码', { exact: true }).fill('new-test-password')
  await dialog.getByLabel('确认新密码', { exact: true }).fill('new-test-password')
  await save.click()
  await expect(dialog.getByRole('alert')).toContainText('当前密码不正确')
  await save.click()
  await expect(dialog.getByRole('alert')).toContainText('修改密码请求过于频繁，请稍后再试')
  await save.click()
  await expect(page).toHaveURL(/\/login$/)
  expect(attempts).toBe(3)
})

test('医院目录停用商品显示启用并提交审批', async ({ page }) => {
  const code = 'SMOKE-DISABLED-PRODUCT'
  let submitted: unknown
  let attempts = 0
  await page.route('**/api/master-data/hospital-products?*', route => route.fulfill({
    json: { code: 0, data: { columns: ['商品编码', '商品名称', '状态'],
      rows: [{ code, name: '测试停用商品', status: '停用' }], total: 1, page: 1, size: 20 } }
  }))
  await page.route('**/api/master-data/hospital-products/status', route => {
    submitted = route.request().postDataJSON()
    if (++attempts === 1) return route.fulfill({ json: { code: 400, message: '该商品已有进行中的目录申请，请勿重复提交' } })
    return route.fulfill({ json: { code: 0, data: { updatedRows: 1 } } })
  })
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.goto('/features/hospital-product-catalog')
  await login(page)
  const menu = page.locator('.spd-side-nav')
  const masterData = menu.locator('.el-sub-menu').filter({
    has: page.getByText('基础数据管理', { exact: true })
  })
  const directItems = masterData.locator(':scope > ul > .el-menu-item')
  for (const name of ['待审批目录', '医院目录', '供应商厂家管理', '院区管理']) {
    await expect(directItems.getByText(name, { exact: true })).toHaveCount(1)
  }
  await expect(menu.getByText('商品目录', { exact: true })).toHaveCount(0)
  const row = page.getByRole('row').filter({ hasText: code })
  await expect(row.getByRole('button', { name: '停用', exact: true })).toBeHidden()
  await row.getByRole('button', { name: '启用', exact: true }).click()
  const confirm = page.getByRole('dialog', { name: '启用医院目录商品' })
  const submit = confirm.getByRole('button', { name: '提交启用申请' })
  await submit.click()
  await expect(confirm.getByRole('alert')).toHaveText('该商品已有进行中的目录申请，请勿重复提交')
  await submit.click()
  await expect(page.getByText('已提交商品状态变更申请，审批通过后生效', { exact: true })).toBeVisible()
  expect(submitted).toEqual({ productCodes: [code], status: 1 })
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

test('新增定数包模板时可查询并选择商品', async ({ page }) => {
  const product = { code: 'SMOKE-QP-001', name: '定数包测试耗材', unit: '支', conversionRate: 5 }
  await page.route(url => url.pathname === '/api/master-data/hospital-products', route => {
    const matches = new URL(route.request().url()).searchParams.get('productCode') === product.code
    return route.fulfill({ json: { code: 0, data: { rows: matches ? [product] : [], total: matches ? 1 : 0 } } })
  })

  await page.goto('/features/quota-template-maintenance')
  await login(page)
  await page.getByRole('button', { name: '新增定数包模板' }).click()
  const templateDialog = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: '新增定数包模板' }) })
  await templateDialog.getByTitle('选择商品').click()
  const productDialog = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: '选择商品' }) })
  await productDialog.getByPlaceholder('商品编码', { exact: true }).fill(product.code, { timeout: 5000 })
  await productDialog.getByRole('button', { name: '查询', exact: true }).click({ timeout: 5000 })
  await productDialog.getByRole('row').filter({ hasText: product.code })
    .getByRole('button', { name: '选择', exact: true }).click({ timeout: 5000 })
  await expect(productDialog).toBeHidden()
  await expect(templateDialog.getByLabel('商品编码', { exact: true })).toHaveValue(product.code)
  await expect(templateDialog.getByLabel('商品名称', { exact: true })).toHaveValue(product.name)
  await expect(templateDialog.getByRole('spinbutton', { name: '包内数量' })).toHaveValue('5')
})

test('科室库房目录显示切回页面后的当前库房等级', async ({ page }) => {
  let warehouseType = '一级库'
  await page.route('**/api/master-data/department-warehouse-catalogs?*', route => route.fulfill({
    json: { code: 0, data: {
      rows: [{ code: 1, sequenceNo: 1, deptName: '设备科', warehouseName: '虚拟一级库',
        warehouseType, productCode: 'SMOKE-WH-001', productName: '测试耗材', status: '启用' }],
      page: 1, size: 20, total: 1
    } }
  }))

  await page.goto('/features/department-warehouse-catalog')
  await login(page)
  const row = page.getByRole('row').filter({ hasText: 'SMOKE-WH-001' })
  await expect(row.getByText('库房等级：一级库', { exact: true })).toBeVisible()
  await page.getByRole('menuitem', { name: '库房 / 货位管理', exact: true }).click()
  await expect(page).toHaveURL(/\/features\/warehouse-location-management$/)
  warehouseType = '二级库'
  await page.locator('.spd-tabbar-tab-label').getByText('科室库房目录', { exact: true }).click()
  await expect(row.getByText('库房名称：虚拟一级库', { exact: true })).toBeVisible()
  await expect(row.getByText('库房等级：二级库', { exact: true })).toBeVisible()
})

test('新增库房按所属院区筛选关联科室并清除原选择', async ({ page }) => {
  await page.route('**/api/master-data/campuses/options', route => route.fulfill({
    json: { code: 0, data: [{ code: 'MAIN', name: '主院区' }, { code: 'EAST', name: '东院区' }] }
  }))
  await page.route('**/api/master-data/warehouses/department-options', route => route.fulfill({
    json: { code: 0, data: [
      { deptCode: 'MAIN-01', deptName: '主院区内科', campusName: '主院区' },
      { deptCode: 'EAST-01', deptName: '东院区内科', campusName: '东院区' }
    ] }
  }))
  await page.goto('/features/warehouse-location-management')
  await login(page)
  await page.getByRole('button', { name: '新增', exact: true }).click()
  const dialog = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: '新增库房' }) })
  const campus = dialog.getByRole('combobox').filter({ has: page.getByRole('option', { name: '请选择院区' }) })
  await expect(dialog.getByPlaceholder('请先选择所属院区')).toBeDisabled()
  await campus.selectOption('主院区')
  await dialog.getByRole('button', { name: '搜索科室' }).click()
  await expect(dialog.getByRole('button', { name: /主院区内科/ })).toBeVisible()
  await expect(dialog.getByRole('button', { name: /东院区内科/ })).toBeHidden()
  await dialog.getByRole('button', { name: /主院区内科/ }).click()
  await campus.selectOption('东院区')
  await expect(dialog.getByPlaceholder('输入科室名称或编码')).toHaveValue('')
  await dialog.getByRole('button', { name: '搜索科室' }).click()
  await expect(dialog.getByRole('button', { name: /东院区内科/ })).toBeVisible()
  await expect(dialog.getByRole('button', { name: /主院区内科/ })).toBeHidden()
})

test('打开打包模板弹窗时能选到页面打开后新增的模板', async ({ page }) => {
  let available = false
  const template = { templateId: 301, templateCode: 'TPL-NEW', templateName: '刚新增的模板', status: '启用',
    productName: '测试耗材', specModel: 'TEST-01', manufacturerName: '测试厂家', quantity: 5, unit: '支' }
  const original = { ...template, templateId: 300, templateCode: 'TPL-ORIGINAL', templateName: '原模板' }
  await page.route('**/api/quota-packages/templates?*', route => {
    const params = new URL(route.request().url()).searchParams
    const size = Number(params.get('size'))
    const rows = available ? [original, template] : [original]
    return route.fulfill({ json: { code: 0, data: { rows, page: 1, size, total: rows.length } } })
  })
  await page.route('**/api/quota-packages/packing-options', route => route.fulfill({
    json: { code: 0, data: { warehouses: [
      { warehouseName: '主院区中心库', warehouseType: '中心库' },
      { warehouseName: '东院区中心库', warehouseType: '中心库' }
    ], candidates: [] } }
  }))
  await page.route('**/api/quota-packages/packing-tasks?*', route => route.fulfill({
    json: { code: 0, data: { rows: [], page: 1, size: 20, total: 0 } }
  }))
  await page.goto('/features/quota-template-maintenance')
  await login(page)
  const originalRow = page.getByRole('row').filter({ hasText: 'TPL-ORIGINAL' })
  await expect(originalRow).toBeVisible()
  const initialTemplates = page.waitForResponse(response => new URL(response.url()).pathname === '/api/quota-packages/templates')
  await originalRow.getByRole('button', { name: '打包', exact: true }).click()
  await initialTemplates
  available = true
  const topCard = page.locator('.pt-page > .pt-card').first()
  await expect(topCard.getByRole('heading', { name: '按验收单分配散货' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新建任务', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '终止任务', exact: true })).toHaveCount(0)
  const dialog = page.getByRole('dialog', { name: '新建打包任务' })
  const warehouse = dialog.getByRole('combobox', { name: '库房' })
  await warehouse.click()
  await expect(page.getByRole('option', { name: '主院区中心库' })).toBeVisible()
  await expect(page.getByRole('option', { name: '东院区中心库' })).toBeVisible()
  await warehouse.fill('东院区')
  await expect(page.getByRole('option', { name: '主院区中心库' })).toBeHidden()
  await page.getByRole('option', { name: '东院区中心库' }).click()
  await expect(page.locator('.pt-warehouse-select')).toContainText('东院区中心库')
  await expect(dialog.getByLabel('模板编码')).toHaveValue('TPL-ORIGINAL')
  await dialog.getByRole('button', { name: '清除', exact: true }).click()
  await expect(dialog.getByLabel('模板编码')).toHaveValue('')
  await expect(dialog.getByLabel('每包数量')).toHaveValue('')
  await dialog.getByRole('button', { name: '选择打包模板', exact: true }).click()
  await page.getByRole('row').filter({ hasText: 'TPL-NEW' }).getByRole('button', { name: '选择', exact: true }).click()
  await expect(dialog.getByLabel('模板编码')).toHaveValue('TPL-NEW')
  await expect(dialog.getByLabel('模板名称')).toHaveValue('测试耗材')
  await expect(dialog.getByLabel('商品名称')).toHaveValue('测试耗材')
  await expect(dialog.getByLabel('每包数量')).toHaveValue('5 支/包')
  await dialog.getByRole('button', { name: '清除', exact: true }).click()
  await expect(dialog.getByLabel('模板编码')).toHaveValue('')
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
  await expect(page.getByRole('dialog', { name: '新建打包任务' }).getByLabel('模板编码')).toHaveValue(template.templateCode)
  const create = page.getByRole('dialog', { name: '新建打包任务' }).getByRole('button', { name: '确认创建' })
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

test('智能补货无默认科室时选择授权科室后才能生成采购需求', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/purchase-orders/options', route => route.fulfill({ json: { code: 0, data: {
    products: [], suppliers: [], departments: [{ deptCode: 'TEST-DEP', deptName: '测试科室', isCurrent: false }]
  } } }))
  await page.route('**/api/purchase-orders/smart-replenishment-analysis?*', route => route.fulfill({ json: { code: 0, data: {
    analysisId: 99, analysisNo: 'SMART-TEST', periodDays: [15], selectedPeriodDays: 15,
    totalFormulaQty: 2, totalRecommendedQty: 2,
    rows: [{ warehouseCode: 'W1', productCode: 'P1', recommendedQty: 2 }]
  } } }))
  let submitted = false
  await page.route('**/api/purchase-orders/demands', route => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().postDataJSON()).toMatchObject({ analysisId: 99, deptCode: 'TEST-DEP' })
    submitted = true
    return route.fulfill({ json: { code: 0, data: { demandNo: 'CG-TEST' } } })
  })
  await page.goto('/features/purchase-management')
  await login(page)
  await page.getByRole('button', { name: '智能补货', exact: true }).click()
  await page.getByRole('button', { name: '智能补货分析', exact: true }).click()
  const modal = page.locator('.purchase-smart-modal')
  await expect(modal.getByText('SMART-TEST', { exact: true })).toBeVisible()
  const submit = modal.getByRole('button', { name: '生成采购需求' })
  await expect(submit).toBeDisabled()
  await modal.getByLabel('发起科室').selectOption('TEST-DEP')
  await submit.click()
  await expect(modal).toBeHidden()
  expect(submitted).toBeTruthy()
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
  await expect(page.getByRole('heading', { name: '待审批目录', exact: true })).toBeVisible()
  await expect(page.locator('.approval-field-nav')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '列设置', exact: true })).toBeVisible()
  const headers = page.locator('.approval-wide-table thead')
  await expect(headers).toContainText('变更记录')
  await expect(headers).toContainText('创建时间')
  await expect(headers).toContainText('修改时间')
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

test('科室退库预占后在拣配配送确认', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/stock-returns/options', route => route.fulfill({ json: { code: 0, data: {
    warehouses: [{ id: 20, name: '二级测试库', type: '二级库', campus: '测试院区' }],
    centers: [{ id: 10, name: '测试中心库', campus: '测试院区' }], suppliers: []
  } } }))
  await page.route('**/api/stock-returns/candidates?*', route => route.fulfill({ json: { code: 0, data: [
    { id: 8, code: 'B-TEST', batchNo: 'B-TEST', productCode: 'P1', productName: '测试耗材',
      availableQuantity: 4, specModel: '测试规格', registrationNo: '测试注册证', manufacturerName: '测试厂家',
      unit: '件', unitPrice: 1.255 }
  ] } }))
  let pending = false
  const order = { returnNo: 'TH-TEST', kind: 'department', status: 'pending_confirm',
    sourceWarehouse: '二级测试库', destinationWarehouse: '测试中心库', quantity: 4 }
  await page.route('**/api/stock-returns', route => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toMatchObject({
        kind: 'department', sourceWarehouseId: 20, destinationWarehouseId: 10,
        items: [{ id: 8, quantity: 4 }]
      })
      pending = true
      return route.fulfill({ json: { code: 0, data: order } })
    }
    return route.fulfill({ json: { code: 0, data: pending ? [order] : [] } })
  })
  await page.route('**/api/stock-returns/picking-queue/page?*', route =>
    route.fulfill({ json: { code: 0, data: { rows: pending ? [order] : [], total: pending ? 1 : 0, page: 1, size: 20 } } }))
  await page.route('**/api/stock-returns/TH-TEST/warehouse-confirm', route => {
    expect(route.request().method()).toBe('PUT')
    pending = false
    return route.fulfill({ json: { code: 0, data: { returnNo: 'TH-TEST', status: 'confirmed' } } })
  })
  await page.goto('/features/stock-return')
  await login(page)
  await page.getByRole('combobox', { name: '来源库房' }).selectOption('20')
  await expect(page.getByRole('combobox', { name: '接收中心库' })).toHaveValue('10')
  await page.getByRole('button', { name: '查询可退库存' }).click()
  await page.getByRole('button', { name: '批量选择查询结果' }).click()
  await page.getByRole('textbox', { name: '退货原因' }).fill('测试退货')
  await page.getByRole('button', { name: '提交退库预占' }).click()
  const confirmation = page.getByRole('dialog', { name: '确认退货' })
  await expect(confirmation).toBeVisible()
  for (const text of ['测试耗材', '测试规格', '测试注册证', '测试厂家', '4 件', '金额汇总']) {
    await expect(confirmation.getByText(text, { exact: false }).first()).toBeVisible()
  }
  await expect(confirmation.getByText(/^[￥¥]5\.02$/)).toHaveCount(2)
  await expect.poll(() => confirmation.locator('.stock-return-confirm-dialog').evaluate(element => {
    const bounds = element.getBoundingClientRect()
    return Math.max(Math.abs(bounds.left + bounds.width / 2 - innerWidth / 2),
      Math.abs(bounds.top + bounds.height / 2 - innerHeight / 2))
  })).toBeLessThan(12)
  await confirmation.getByRole('button', { name: '取消', exact: true }).click()
  await expect(confirmation).toBeHidden()
  expect(pending).toBeFalsy()
  await page.getByRole('button', { name: '提交退库预占' }).click()
  await confirmation.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByRole('cell', { name: '待拣配配送确认' })).toBeVisible()
  await page.locator('.spd-side-nav').getByText('拣配配送', { exact: true }).click()
  await page.getByRole('tab', { name: '科室退货确认' }).click()
  const pendingRow = page.getByRole('row').filter({ hasText: 'TH-TEST' })
  await pendingRow.getByRole('button', { name: '确认退库' }).click()
  await page.getByRole('dialog', { name: '确认科室退库' }).getByRole('button', { name: '确认退库' }).click()
  await expect(pendingRow).toHaveCount(0)
  expect(pending).toBeFalsy()
})

test('供应商退货按批号批量选包且仅最终确认提交', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/stock-returns/options', route => route.fulfill({ json: { code: 0, data: {
    warehouses: [{ id: 20, name: '二级测试库', type: '二级库', campus: '测试院区' }],
    centers: [], suppliers: [{ id: 4, name: '测试供应商' }]
  } } }))
  await page.route('**/api/stock-returns/candidates?*', route => {
    const params = new URL(route.request().url()).searchParams
    expect(Object.fromEntries(params)).toMatchObject({ batchNo: 'B-PACK', supplierId: '4', type: 'package' })
    return route.fulfill({ json: { code: 0, data: ['D-P1', 'D-P2'].map((code, index) => ({
      id: 8 + index, code, batchNo: 'B-PACK', productCode: 'P1', productName: '测试耗材', availableQuantity: 1, packageQuantity: 5
    })) } })
  })
  let submissions = 0
  await page.route('**/api/stock-returns', route => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toMatchObject({
        kind: 'supplier', supplierId: 4, sourceWarehouseId: 20, selectionType: 'package',
        items: [{ id: 8, quantity: 1 }, { id: 9, quantity: 1 }]
      })
      submissions++
      return route.fulfill({ json: { code: 0, data: { returnNo: 'TH-SUP', status: 'confirmed' } } })
    }
    return route.fulfill({ json: { code: 0, data: [] } })
  })
  await page.goto('/features/stock-return')
  await login(page)
  await page.getByRole('combobox', { name: '退货类型' }).selectOption('supplier')
  await page.getByRole('combobox', { name: '来源库房' }).selectOption('20')
  await page.getByRole('combobox', { name: '退货供应商' }).selectOption('4')
  await page.getByRole('combobox', { name: '库存类型' }).selectOption('package')
  await page.getByRole('textbox', { name: '批号' }).fill('B-PACK')
  await page.getByRole('button', { name: '查询可退库存' }).click()
  const submit = page.getByRole('button', { name: '确认供应商退货' })
  const firstPackage = page.getByRole('checkbox', { name: '选择 D-P1', exact: true })
  await expect(submit).toBeDisabled()
  await firstPackage.check()
  await expect(submit).toBeEnabled()
  await firstPackage.uncheck()
  await expect(submit).toBeDisabled()
  await page.getByRole('button', { name: '批量选择查询结果' }).click()
  await page.getByRole('textbox', { name: '退货原因' }).fill('测试退货')
  expect(submissions).toBe(0)
  const confirm = page.getByRole('dialog', { name: '确认退货' })
  await page.getByRole('button', { name: '确认供应商退货' }).click()
  await confirm.getByRole('button', { name: '取消' }).click()
  expect(submissions).toBe(0)
  await page.getByRole('button', { name: '确认供应商退货' }).click()
  await confirm.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByText('供应商退货已出库：TH-SUP', { exact: true }).first()).toBeVisible()
  expect(submissions).toBe(1)
})

test('供货台账首页可查询且导出沿用已应用筛选', async ({ page }) => {
  const api = (data: unknown) => ({ json: { code: 0, data } })
  await page.route('**/api/auth/login', route => route.fulfill(api({ token: 'supplier-ledger-test' })))
  await page.route('**/api/auth/me', route => route.fulfill(api({
    userId: 1, username: 'ledger', roles: [], dataScope: 2,
    menuCodes: ['supplier-delivery-ledger-report'], permissionCodes: []
  })))
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill(api(null)))
  const ledgerPath = '**/api/report-center/supplier-delivery-ledger?*'
  const exportPath = '**/api/report-center/supplier-delivery-ledger/export.xlsx?*'
  await page.route('**/api/report-center/supplier-delivery-ledger/options', route => route.fulfill(api({
    suppliers: [{ id: 7, code: 'SP-7', name: '测试配送商' }],
    departments: [{ id: 9, code: 'DEP-9', name: '测试科室' }]
  })))
  await page.route(ledgerPath, route => {
    const params = new URL(route.request().url()).searchParams
    return route.fulfill(api({
      rows: [{ orderNo: 'LEDGER-1', acceptanceDepartment: params.get('department')
        || (params.get('departmentId') === '9' ? '测试科室' : '默认科室') }],
      total: 1, page: 1, size: 20, summary: {}
    }))
  })
  await page.route(exportPath, route => route.fulfill({
    contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body: 'export-test'
  }))
  await page.goto('/features/supplier-delivery-ledger-report')
  await login(page)
  await expect(page.getByText('默认科室', { exact: true })).toBeVisible()
  const supplier = page.getByRole('combobox', { name: '供应商', exact: true })
  await supplier.click()
  await supplier.fill('SP-7')
  await page.getByRole('option', { name: '测试配送商 · SP-7' }).click()
  const department = page.getByRole('combobox', { name: '科室', exact: true })
  await department.click()
  await page.getByRole('option', { name: '测试科室 · DEP-9' }).click()
  const filtered = page.waitForRequest(ledgerPath)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  expect(Object.fromEntries(new URL((await filtered).url()).searchParams)).toMatchObject({ supplierId: '7', departmentId: '9' })
  await expect(page.getByRole('cell', { name: '测试科室', exact: true })).toBeVisible()
  await department.click()
  await department.fill('中心库')
  await page.getByRole('option', { name: '中心库', exact: true }).click()
  const exported = page.waitForRequest(exportPath)
  await page.getByRole('button', { name: '导出 Excel' }).click()
  expect(Object.fromEntries(new URL((await exported).url()).searchParams)).toMatchObject({ supplierId: '7', departmentId: '9' })
  const fallback = page.waitForRequest(ledgerPath)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  expect(new URL((await fallback).url()).searchParams.get('department')).toBe('中心库')
  await expect(page.getByRole('cell', { name: '中心库', exact: true })).toBeVisible()
})

test('库存管理的可用散货按条件查询并请求当前页', async ({ page }) => {
  const api = (data: unknown) => ({ json: { code: 0, data } })
  await page.route('**/api/auth/login', route => route.fulfill(api({ token: 'loose-stock-test' })))
  await page.route('**/api/auth/me', route => route.fulfill(api({
    userId: 1, username: 'inventory', roles: [], dataScope: 2,
    menuCodes: ['inventory-management'], permissionCodes: []
  })))
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill(api(null)))
  await page.route('**/api/inventory/balances?*', route => route.fulfill(api({ rows: [], total: 0, page: 1, size: 20 })))
  const requests: Record<string, string>[] = []
  await page.route('**/api/inventory/loose-stock?*', route => {
    const params = Object.fromEntries(new URL(route.request().url()).searchParams)
    requests.push(params)
    const pageNumber = Number(params.page)
    const size = Number(params.size)
    const total = params.productCode === 'P-LOOSE' ? 21 : 0
    const start = (pageNumber - 1) * size
    const rows = Array.from({ length: Math.max(0, Math.min(total - start, size)) }, (_, i) => ({
      balanceId: start + i, productCode: 'P-LOOSE', productName: '测试散货',
      warehouseName: 'SPD中心库', systemBatchNo: 'B-' + (start + i + 1), availableQty: 3
    }))
    return route.fulfill(api({ rows, total, page: pageNumber, size }))
  })
  await page.goto('/features/inventory-management')
  await login(page)
  await page.getByRole('tab', { name: '可用散货查询' }).click()
  await expect(page.getByText('暂无可用散货')).toBeVisible()
  const filters = page.getByRole('form', { name: '可用散货查询' })
  await filters.getByLabel('商品编码').fill('P-LOOSE')
  await filters.getByRole('button', { name: '查询' }).click()
  await expect(page.getByText('显示 1-20 条，共 21 条')).toBeVisible()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByRole('cell', { name: 'B-21', exact: true })).toBeVisible()
  expect(requests.map(({ page, size, productCode }) => ({ page, size, productCode }))).toEqual([
    { page: '1', size: '20', productCode: '' },
    { page: '1', size: '20', productCode: 'P-LOOSE' },
    { page: '2', size: '20', productCode: 'P-LOOSE' }
  ])
})

test('定数包库存同组多个唯一码在独立弹窗显示', async ({ page }) => {
  const api = (data: unknown) => ({ json: { code: 0, data } })
  await page.route('**/api/auth/login', route => route.fulfill(api({ token: 'quota-stock-codes-test' })))
  await page.route('**/api/auth/me', route => route.fulfill(api({
    userId: 1, username: 'inventory', roles: [], dataScope: 2,
    menuCodes: ['inventory-management'], permissionCodes: []
  })))
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill(api(null)))
  await page.route('**/api/inventory/balances?*', route => route.fulfill(api({ rows: [], total: 0, page: 1, size: 20 })))
  await page.route('**/api/inventory/quota-package-stock?*', route => route.fulfill(api({
    rows: [{ warehouseId: 1, templateId: 2, productId: 3, packageCode: 'PACK-A', packageCount: 2,
      packageUniqueCodes: ['QP-TEST-1', 'QP-TEST-2'] }],
    total: 1, page: 1, size: 20
  })))
  await page.goto('/features/inventory-management')
  await login(page)
  await page.getByRole('tab', { name: '定数包库存查询' }).click()
  await page.getByRole('button', { name: '查看 2 个唯一码' }).click()
  const codes = page.getByRole('dialog', { name: '定数包唯一码' })
  await expect(codes.getByRole('listitem')).toHaveText(['QP-TEST-1', 'QP-TEST-2'])
  await codes.getByRole('button', { name: '关闭', exact: true }).click()
  await expect(codes).toBeHidden()
})

test('定数包库存按商品拼音排序后再跨页展示', async ({ page }) => {
  const api = (data: unknown) => ({ json: { code: 0, data } })
  await page.route('**/api/auth/login', route => route.fulfill(api({ token: 'quota-stock-sort-test' })))
  await page.route('**/api/auth/me', route => route.fulfill(api({
    userId: 1, username: 'inventory', roles: [], dataScope: 2,
    menuCodes: ['inventory-management'], permissionCodes: []
  })))
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill(api(null)))
  await page.route('**/api/inventory/balances?*', route => route.fulfill(api({ rows: [], total: 0, page: 1, size: 20 })))
  const stock = Array.from({ length: 22 }, (_, index) => ({
    warehouseId: 1, templateId: index + 1, productId: index + 1,
    packageCode: index < 2 ? 'PACK-GAUZE' : 'PACK-' + String(index + 1).padStart(2, '0'), packageCount: 1,
    templateVersion: index === 0 ? 2 : 1,
    productName: index < 2 ? '纱布耗材' : index === 2 ? '坐垫耗材' : '阿尔法耗材',
    packageUniqueCodes: [index === 0 ? 'A-GAUZE-V2' : index === 1 ? 'Z-GAUZE-V1' : 'QP-' + (index + 1)]
  }))
  await page.route('**/api/inventory/quota-package-stock?*', route => {
    const params = new URL(route.request().url()).searchParams
    const currentPage = Number(params.get('page'))
    const size = Number(params.get('size'))
    const start = (currentPage - 1) * size
    return route.fulfill(api({ rows: stock.slice(start, start + size), total: stock.length, page: currentPage, size }))
  })
  await page.goto('/features/inventory-management')
  await login(page)
  await page.getByRole('tab', { name: '定数包库存查询' }).click()
  const rows = page.getByRole('region', { name: '定数包库存结果' }).locator('tbody tr')
  await expect(rows).toHaveCount(20)
  await expect(rows.first()).toContainText('阿尔法耗材')
  await expect(rows.last()).toContainText('Z-GAUZE-V1')
  await expect(rows.last()).toContainText('PACK-GAUZE')
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(rows).toHaveCount(2)
  await expect(rows.first()).toContainText('A-GAUZE-V2')
  await expect(rows.last()).toContainText('坐垫耗材')
})

test('拣配配送保留待拣配工作台并移除重复的记录列表', async ({ page }) => {
  let historyRequested = false
  let pickingCompleted = false
  let finishPicking!: () => void
  const pickingResponse = new Promise<void>(resolve => { finishPicking = resolve })
  const api = (data: unknown) => ({ json: { code: 0, data } })
  await page.route('**/api/auth/login', route => route.fulfill(api({ token: 'picking-tab-test' })))
  await page.route('**/api/auth/me', route => route.fulfill(api({
    userId: 1, username: 'picker', roles: [], dataScope: 2,
    menuCodes: ['picking-delivery'], permissionCodes: ['picking-delivery:write']
  })))
  await page.route('**/api/operational-closure/overview', route => route.fulfill(api({ summary: {}, events: [] })))
  await page.route('**/api/stock-returns/picking-queue/page?*', route =>
    route.fulfill(api({ rows: [], total: 0, page: 1, size: 20 })))
  await page.route('**/api/operational-closure/picking/confirm-loose', async route => {
    expect(route.request().postDataJSON()).toMatchObject({ quantity: 2 })
    await pickingResponse
    pickingCompleted = true
    await route.fulfill(api({ deliveryNo: 'PS-TAB-TEST' }))
  })
  page.on('request', request => {
    if (request.url().includes('/api/operational-closure/lists/delivery')) historyRequested = true
  })
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/operational-closure/options', route => route.fulfill({ json: { code: 0, data: {
    departments: [{ deptCode: 'DEP-1', deptName: '测试科室' }],
    warehouses: [{ warehouseName: '一级测试库', warehouseType: '一级库' }], products: [], balances: []
  } } }))
  await page.route('**/api/operational-closure/picking/requisitions**', route => {
    const params = new URL(route.request().url()).searchParams
    return route.fulfill({ json: { code: 0, data: { rows: params.get('deptCode') && !pickingCompleted ? [{
      requisitionNo: 'SL-PICK-TEST', itemId: 1, deptName: '测试科室', sourceWarehouseName: '一级测试库',
      requisitionType: 'urgent', requisitionTypeLabel: '紧急申领',
      productCode: 'P1', status: 'partial_picked', requisitionCreatedAt: '2026-11-18 10:00:00',
      partialPickingCompletedAt: '2026-11-18 11:00:00', remainingQty: 2, itemType: 'loose'
    }, {
      requisitionNo: 'SL2026102800007', itemId: 2, deptName: '测试科室', sourceWarehouseName: '一级测试库',
      productCode: 'P2', remainingQty: 1, itemType: 'loose'
    }] : [] } } })
  })
  let looseLookupFails = false
  await page.route('**/api/operational-closure/picking/loose-stock?*', route => {
    const itemId = new URL(route.request().url()).searchParams.get('itemId')
    if (itemId === '2' && looseLookupFails) {
      return route.fulfill({ json: { code: 500, message: '库存查询暂不可用' } })
    }
    const rows = itemId === '2' ? [] : Array.from({ length: 51 }, (_, index) => ({
      balanceId: index + 1, productName: '测试散货',
      systemBatchNo: 'LOOSE-' + String(index + 1).padStart(2, '0'), availableQty: 2
    }))
    return route.fulfill(api({ rows }))
  })
  const pending = page.waitForResponse('**/api/operational-closure/picking/requisitions')
  await page.goto('/features/picking-delivery')
  await login(page)
  await pending
  await expect(page.getByRole('heading', { name: '拣配出库', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '拣配记录', exact: true })).toHaveCount(0)
  expect(historyRequested).toBeFalsy()
  const filter = page.getByRole('form', { name: '待拣配申领日期查询' })
  await filter.getByLabel('审批开始日期').fill('2026-11-18')
  await filter.getByLabel('审批结束日期').fill('2026-11-18')
  const filtered = page.waitForRequest(r => r.url().includes('/picking/requisitions?') && r.url().includes('deptCode=DEP-1'))
  await page.getByRole('combobox', { name: '科室', exact: true }).selectOption('DEP-1')
  expect(Object.fromEntries(new URL((await filtered).url()).searchParams)).toMatchObject({
    deptCode: 'DEP-1', dateFrom: '2026-11-18', dateTo: '2026-11-18'
  })
  const req = page.getByRole('row').filter({ hasText: 'SL-PICK-TEST' })
  await expect(req.getByRole('cell', { name: '紧急申领', exact: true })).toBeVisible()
  await expect(req.getByText('2026-11-18 10:00:00', { exact: true })).toBeVisible()
  await expect(req.getByText('2026-11-18 11:00:00', { exact: true })).toBeVisible()
  await expect(page.getByRole('combobox', { name: '库房', exact: true })).toHaveValue('一级测试库')
  const emptyStockRow = page.getByRole('row').filter({ hasText: 'SL2026102800007' })
  await emptyStockRow.getByRole('button', { name: '选择', exact: true }).click()
  await expect(emptyStockRow.getByRole('button', { name: '已选择' })).toHaveAttribute('aria-pressed', 'true')
  await expect(emptyStockRow.getByRole('status')).toHaveText('当前库房没有该商品的可用散货')
  await expect(page.getByRole('button', { name: '确认拣配出库' })).toBeDisabled()
  looseLookupFails = true
  await emptyStockRow.getByRole('button', { name: '已选择' }).click()
  await expect(emptyStockRow.getByRole('alert')).toContainText('库存查询暂不可用')
  await expect(emptyStockRow.getByText('当前库房没有该商品的可用散货')).toHaveCount(0)
  await req.getByRole('button', { name: '选择', exact: true }).click()
  const detailPager = page.locator('[aria-label="拣配库存明细分页"]')
  const batchRows = page.locator('.picking-panel--loose tbody tr')
  const batchRow = (batch: string) => batchRows.filter({ has: page.getByRole('cell', { name: batch, exact: true }) })
  await expect(detailPager).toContainText('显示 1-10 条，共 51 条')
  await expect(batchRows).toHaveCount(10)
  const firstBatch = (await batchRows.first().getByRole('cell').nth(1).innerText()).trim()
  await batchRow(firstBatch).getByRole('spinbutton').fill('1')
  await detailPager.getByRole('button', { name: '下一页' }).click()
  await expect(detailPager).toContainText('显示 11-20 条，共 51 条')
  await expect(batchRow(firstBatch)).toHaveCount(0)
  const secondBatch = (await batchRows.first().getByRole('cell').nth(1).innerText()).trim()
  await batchRow(secondBatch).getByRole('spinbutton').fill('1')
  const detailSize = detailPager.getByRole('spinbutton', { name: '每页条数' })
  await detailSize.fill('100')
  await detailSize.blur()
  await expect(detailSize).toHaveValue('50')
  await expect(detailPager).toContainText('显示 1-50 条，共 51 条')
  await expect(batchRows).toHaveCount(50)
  await expect(batchRow(firstBatch).getByRole('spinbutton')).toHaveValue('1')
  await expect(batchRow(secondBatch).getByRole('spinbutton')).toHaveValue('1')
  const submitting = page.waitForRequest('**/api/operational-closure/picking/confirm-loose')
  await page.getByRole('button', { name: '确认拣配出库' }).click()
  await submitting
  await page.getByRole('tab', { name: '科室退货确认' }).click()
  const reloaded = page.waitForResponse(response => response.url().includes('/picking/requisitions?'))
  finishPicking()
  await reloaded
  await expect(page.getByRole('tab', { name: '科室退货确认' })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('tab', { name: '拣配管理' }).click()
  await expect(req).toHaveCount(0)
})

test('拣配记录可签收并在弹窗逐条显示完整定数包码', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  const labelCodes = Array.from({ length: 80 }, (_, index) => `D2026102300${String(index + 1).padStart(4, '0')}`)
  const order = {
    deliveryId: 1, bizNo: 'PS-DETAIL-001', status: 'signed', productName: '测试耗材',
    sourceNo: 'SL-DETAIL-001', requisitionCreatedAt: '2026-11-18 09:00:00',
    partialPickingCompletedAt: '2026-11-18 10:00:00', pickingCompletedAt: '2026-11-18 11:00:00'
  }
  let signed = false
  await page.route('**/api/operational-closure/deliveries/PS-DETAIL-002/sign', route => {
    expect(route.request().method()).toBe('PUT')
    signed = true
    return route.fulfill({ json: { code: 0, data: { deliveryNo: 'PS-DETAIL-002', status: 'signed' } } })
  })
  await page.route('**/api/operational-closure/lists/delivery**', route => route.fulfill({
    json: { code: 0, data: {
      rows: [{ ...order, labelCodes }, { ...order, deliveryId: 2, bizNo: 'PS-DETAIL-002', status: signed ? 'signed' : 'picked', labelCodes: [] }],
      total: 2, page: 1, size: 20
    } }
  }))
  await page.goto('/features/picking-records')
  await login(page)
  const completed = page.getByRole('row').filter({ hasText: 'PS-DETAIL-001' })
  for (const time of ['09:00:00', '10:00:00', '11:00:00']) {
    await expect(completed.getByText(`2026-11-18 ${time}`, { exact: true })).toBeVisible()
  }
  const withoutCodes = page.getByRole('row').filter({ hasText: 'PS-DETAIL-002' })
  await expect(withoutCodes).toBeVisible()
  await expect(withoutCodes.getByRole('button', { name: /查看详情/ })).toHaveCount(0)
  await withoutCodes.getByRole('button', { name: '确认签收' }).click()
  await expect(withoutCodes.getByText('已签收', { exact: true })).toBeVisible()
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

test('科室消耗扫描高值唯一码直接提交并显示结算单号', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/operational-closure/options', route => route.fulfill({ json: { code: 0, data: {
    departments: [{ deptCode: 'TEST-DEPT', deptName: '测试科室' }], warehouses: [], products: [], balances: []
  } } }))
  await page.route('**/api/master-data/departments/TEST-DEPT/warehouses', route => route.fulfill({
    json: { code: 0, data: [{ warehouseId: 42, name: '测试库房', selected: 1 }] }
  }))
  await page.route('**/api/operational-closure/consumptions/resolve?*', route => route.fulfill({ json: { code: 0, data: {
    sourceType: 'udi', uniqueCode: 'HV-TEST-01', highValue: 1, productCode: 'HV-P',
    productName: '高值耗材测试', warehouseId: 42, warehouseName: '测试库房'
  } } }))
  await page.route('**/api/operational-closure/consumptions', route => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().postDataJSON()).toMatchObject({
      uniqueCode: 'HV-TEST-01', productCode: 'HV-P', warehouseId: 42, quantity: 1
    })
    return route.fulfill({ json: { code: 0, data: { consumptionNo: 'XH-TEST', settlementNos: ['JS-TEST'] } } })
  })
  await page.goto('/features/department-consumption')
  await login(page)
  await page.getByRole('combobox', { name: '库房', exact: true }).selectOption({ label: '测试库房' })
  await page.getByPlaceholder('扫码或输入定数包码、UDI、唯一码，点击查询定位商品').fill('HV-TEST-01')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('HV-P / 高值耗材测试', { exact: true })).toBeVisible()
  await expect(page.getByLabel('数量', { exact: true })).toHaveValue('1')
  await page.getByRole('button', { name: '登记消耗', exact: true }).click()
  await expect(page.getByText(/科室消耗已登记：XH-TEST，结算单：JS-TEST/).first()).toBeVisible()
})

test('召回与隔离组合查询并在拣配配送确认召回签收', async ({ page }) => {
  let received = false
  const recallPageRequests: { page: string | null; size: string | null }[] = []
  const returnPageRequests: { page: string | null; size: string | null }[] = []
  const api = (data: unknown) => ({ json: { code: 0, data } })
  await page.route('**/api/auth/login', route => route.fulfill(api({ token: 'recall-ui-test', userId: 1, username: 'receiver' })))
  await page.route('**/api/auth/me', route => route.fulfill(api({
    userId: 1, username: 'receiver', roles: [], dataScope: 2,
    menuCodes: ['recall-isolation', 'picking-delivery'], permissionCodes: ['picking-delivery:write']
  })))
  await page.route('**/api/operational-closure/overview', route => route.fulfill(api({ summary: {}, events: [] })))
  await page.route('**/api/operational-closure/options', route => route.fulfill(api({
    departments: [], warehouses: [], products: [], balances: []
  })))
  await page.route('**/api/operational-closure/picking/requisitions', route => route.fulfill(api({ rows: [] })))
  await page.route('**/api/stock-returns/picking-queue/page?*', route => {
    const params = new URL(route.request().url()).searchParams
    returnPageRequests.push({ page: params.get('page'), size: params.get('size') })
    const rows = Array.from({ length: 20 }, (_, index) => ({ returnNo: 'TH-WAIT-' + index }))
    return route.fulfill(api({ rows, total: 21, page: 1, size: 20 }))
  })
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/operational-closure/picking/recall-receipts/page?*', route => {
    const params = new URL(route.request().url()).searchParams
    recallPageRequests.push({ page: params.get('page'), size: params.get('size') })
    const pageNumber = Number(params.get('page'))
    const rows = pageNumber === 1
      ? Array.from({ length: 20 }, (_, index) => ({
        recallNo: 'ZH-WAIT-' + index, sourceWarehouses: '设备科二级库', destinationWarehouse: 'SPD中心库', quantity: 1
      }))
      : received ? [] : [{
        recallNo: 'ZH-FILTERED', sourceWarehouses: '设备科二级库', destinationWarehouse: 'SPD中心库',
        quantity: 2, createTime: '2026-11-13 10:30:00'
      }]
    return route.fulfill(api({ rows, total: received ? 20 : 21, page: pageNumber, size: 20 }))
  })
  await page.route('**/api/operational-closure/picking/recall-receipts/ZH-FILTERED', route => {
    expect(route.request().method()).toBe('PUT')
    received = true
    return route.fulfill({ json: { code: 0, data: { recallNo: 'ZH-FILTERED', status: 'received' } } })
  })
  await page.route('**/api/operational-closure/lists/recall?*', route => {
    const params = new URL(route.request().url()).searchParams
    const filtered = params.get('deptName') === '设备'
    return route.fulfill({ json: { code: 0, data: {
      rows: [{ bizNo: filtered ? 'ZH-FILTERED' : 'ZH-ALL', businessType: 'recall', productCode: 'P1',
        sourceDepartments: '设备科', warehouseName: 'SPD中心库', quantity: 2,
        status: received ? 'received' : 'pending_receipt', receivedTime: received ? '2026-11-13 11:00:00' : null }],
      total: 1, page: 1, size: 20
    } } })
  })
  await page.goto('/features/recall-isolation')
  await login(page)
  await expect(page.getByText('ZH-ALL', { exact: true })).toBeVisible()
  const query = page.getByRole('form', { name: '召回与隔离记录查询' })
  await query.getByLabel('开始日期').fill('2026-11-13')
  await query.getByLabel('结束日期').fill('2026-11-13')
  await query.getByLabel('商品名称').fill('耗材')
  await query.getByLabel('商品编码').fill('P1')
  await query.getByLabel('来源科室').fill('设备')
  const request = page.waitForRequest(r => r.url().includes('/lists/recall?') && Boolean(new URL(r.url()).searchParams.get('deptName')))
  await query.getByRole('button', { name: '查询', exact: true }).click()
  expect(Object.fromEntries(new URL((await request).url()).searchParams)).toMatchObject({
    page: '1', dateFrom: '2026-11-13', dateTo: '2026-11-13', productName: '耗材', productCode: 'P1', deptName: '设备'
  })
  const row = page.getByRole('row').filter({ hasText: 'ZH-FILTERED' })
  await expect(row.getByText('设备科', { exact: true })).toBeVisible()
  await expect(row.getByText('SPD中心库', { exact: true })).toBeVisible()
  await expect(row.getByText('待中心库签收', { exact: true })).toBeVisible()
  await expect(row.getByRole('button', { name: '签收入库', exact: true })).toHaveCount(0)
  await page.goto('/features/picking-delivery')
  await expect(page.getByRole('tab', { name: '拣配管理' })).toHaveAttribute('aria-selected', 'true')
  const recallTab = page.getByRole('tab', { name: '召回确认' })
  await recallTab.click()
  const queue = page.getByRole('tabpanel', { name: '召回确认' })
  await expect(queue).toContainText('显示 1-20 条，共 21 条')
  expect(recallPageRequests).toEqual([{ page: '1', size: '20' }])
  await queue.getByRole('button', { name: '下一页' }).click()
  await expect(queue).toContainText('显示 21-21 条，共 21 条')
  await page.getByRole('tab', { name: '科室退货确认' }).click()
  await expect(page.getByRole('tabpanel', { name: '科室退货确认' })).toContainText('显示 1-20 条，共 21 条')
  expect(returnPageRequests).toEqual([{ page: '1', size: '20' }])
  await expect(queue).toHaveCount(0)
  await recallTab.click()
  const pendingRow = queue.getByRole('row').filter({ hasText: 'ZH-FILTERED' })
  for (const cell of ['设备科二级库', 'SPD中心库', '2', '2026-11-13 10:30:00']) {
    await expect(pendingRow.getByRole('cell', { name: cell, exact: true })).toBeVisible()
  }
  await pendingRow.getByRole('button', { name: '确认签收', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '确认签收入库' })
  await expect(dialog).toContainText('ZH-FILTERED')
  await dialog.getByRole('button', { name: '取消' }).click()
  expect(received).toBeFalsy()
  await pendingRow.getByRole('button', { name: '确认签收', exact: true }).click()
  await dialog.getByRole('button', { name: '签收入库', exact: true }).click()
  await expect(pendingRow).toHaveCount(0)
  await expect(queue).toContainText('显示 1-20 条，共 20 条')
  expect(received).toBeTruthy()
  await page.goto('/features/recall-isolation')
  const history = page.getByRole('row').filter({ hasText: 'ZH-ALL' })
  await expect(history.getByText('已签收入库', { exact: true })).toBeVisible()
  await expect(history.getByText('2026-11-13 11:00:00', { exact: true })).toBeVisible()
})

test('反消耗确认使用选中原单并在红冲台账展示结果', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  let attempts = 0
  await page.route('**/api/operational-closure/lists/consumption?*', route => route.fulfill({
    json: { code: 0, data: { page: 1, size: 20, total: 2, rows: [
      { bizNo: 'XH-A', status: 'confirmed' },
      { bizNo: 'XH-B', status: attempts === 2 ? 'reversed' : 'confirmed' }
    ] } }
  }))
  await page.route('**/api/operational-closure/consumptions/*/reverse', route => {
    expect(route.request().method()).toBe('PUT')
    expect(new URL(route.request().url()).pathname).toContain('/consumptions/XH-B/reverse')
    attempts++
    return route.fulfill({ json: attempts === 1
      ? { code: 400, message: '该消耗已确认结算，不能直接反消耗' }
      : { code: 0, data: { flushNo: 'FXH-B', status: 'approved' } } })
  })
  await page.route('**/api/operational-closure/lists/red-flush?*', route => route.fulfill({
    json: { code: 0, data: { page: 1, size: 20, total: 1, rows: [
      { bizNo: 'FXH-B', sourceNo: 'XH-B', flushType: 'reverse_consumption', status: 'approved' }
    ] } }
  }))
  await page.route('**/api/operational-closure/red-flush/options', route => route.fulfill({ json: { code: 0, data: {
    departments: [{ deptCode: 'D-A', deptName: '测试甲科' }, { deptCode: 'D-B', deptName: '测试乙科' }],
    warehouses: [
      { deptCode: 'D-A', warehouseId: 10, warehouseName: '甲科库房' },
      { deptCode: 'D-B', warehouseId: 20, warehouseName: '乙科库房' }
    ]
  } } }))
  await page.goto('/features/department-consumption')
  await login(page)
  const reverse = page.getByRole('row').filter({ hasText: 'XH-B' }).getByRole('button', { name: '反消耗', exact: true })
  const prompt = page.getByRole('dialog', { name: '确认反消耗' })
  await reverse.click()
  await expect(prompt).toContainText('XH-B')
  await prompt.getByRole('button', { name: '取消' }).click()
  expect(attempts).toBe(0)
  await reverse.click()
  await prompt.getByRole('button', { name: '确认冲销' }).click()
  await expect(page.getByText('该消耗已确认结算，不能直接反消耗', { exact: true }).first()).toBeVisible()
  await expect(reverse).toBeEnabled()
  await reverse.click()
  await prompt.getByRole('button', { name: '确认冲销' }).click()
  await expect(page.getByText('反消耗红冲已生成：FXH-B', { exact: true }).first()).toBeVisible()
  const menu = page.locator('.spd-side-nav')
  await menu.getByText('结算与财务', { exact: true }).click()
  await menu.getByText('红冲管理', { exact: true }).click()
  const record = page.getByRole('row').filter({ hasText: 'FXH-B' })
  await expect(record.getByText('XH-B', { exact: true })).toBeVisible()
  const filters = page.getByRole('form', { name: '红冲记录查询' })
  const department = filters.getByRole('combobox', { name: '科室', exact: true })
  const warehouse = filters.getByRole('combobox', { name: '库房', exact: true })
  await expect(warehouse).toBeDisabled()
  await department.selectOption('D-A')
  await warehouse.selectOption('10')
  await expect(warehouse.locator('option')).toHaveText(['该科室全部库房', '甲科库房'])
  await department.selectOption('D-B')
  await expect(warehouse).toHaveValue('')
  await expect(warehouse.locator('option')).toHaveText(['该科室全部库房', '乙科库房'])
  await warehouse.selectOption('20')
  const requested = page.waitForRequest(r => r.url().includes('/lists/red-flush?') && r.url().includes('warehouseId=20'))
  await filters.getByRole('button', { name: '查询' }).click()
  expect(Object.fromEntries(new URL((await requested).url()).searchParams)).toMatchObject({ deptCode: 'D-B', warehouseId: '20', page: '1' })
  await expect(record.getByText('已冲销', { exact: true })).toBeVisible()
})

test('结算对账支持组合查询和服务端科室排序', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/operational-closure/lists/settlement?*', route => {
    const params = new URL(route.request().url()).searchParams
    const filtered = params.get('traceCode') === 'TEST-UDI'
    const pageNumber = Number(params.get('page') || 1)
    const rows = filtered
      ? [{ settlementItemId: pageNumber, settlementNo: `JS-MATCH-${pageNumber}`, requisitionTime: '2026-11-18 12:30:00' }]
      : [{ settlementItemId: 2, settlementNo: 'JS-DEFAULT' }]
    const summary = filtered ? { totalQuantity: 130, totalAmount: 1250.25 } : { totalQuantity: 240, totalAmount: 2800 }
    return route.fulfill({ json: { code: 0, data: { rows, summary, page: pageNumber, size: 20, total: filtered ? 22 : 1 } } })
  })
  await page.goto('/features/settlement-reconciliation')
  await login(page)
  await expect(page.getByText('JS-DEFAULT', { exact: true })).toBeVisible()
  const query = page.getByRole('form', { name: '结算明细查询' })
  await query.getByLabel('申领开始日期').fill('2026-11-01')
  await query.getByLabel('申领结束日期').fill('2026-11-18')
  await query.getByLabel('追溯码').fill('TEST-UDI')
  await query.getByLabel('科室', { exact: true }).fill('设备')
  await query.getByLabel('商品信息').fill('耗材')
  const request = page.waitForRequest(r => r.url().includes('/lists/settlement?') && r.url().includes('departmentAsc'))
  await query.getByLabel('排序').selectOption('departmentAsc')
  expect(Object.fromEntries(new URL((await request).url()).searchParams)).toMatchObject({
    dateFrom: '2026-11-01', dateTo: '2026-11-18', traceCode: 'TEST-UDI',
    deptName: '设备', productKeyword: '耗材', sort: 'departmentAsc', page: '1'
  })
  await expect(page.getByText('JS-MATCH-1', { exact: true })).toBeVisible()
  await expect(page.getByText('申领：2026-11-18 12:30:00')).toBeVisible()
  const totals = page.getByRole('region', { name: '结算汇总' })
  await expect(totals.getByText('130', { exact: true })).toBeVisible()
  await expect(totals.getByText('¥ 1250.25', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByText('JS-MATCH-2', { exact: true })).toBeVisible()
  await expect(totals.getByText('130', { exact: true })).toBeVisible()
  await expect(totals.getByText('¥ 1250.25', { exact: true })).toBeVisible()
  await query.getByRole('button', { name: '重置' }).click()
  await expect(page.getByText('JS-DEFAULT', { exact: true })).toBeVisible()
  await expect(totals.getByText('¥ 2800.00', { exact: true })).toBeVisible()
})

test('切换到结算对账后忽略旧菜单的延迟返回', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/operational-closure/overview', route => route.fulfill({ json: { code: 0, data: { summary: {} } } }))
  await page.route('**/api/operational-closure/options', route => route.fulfill({
    json: { code: 0, data: { departments: [], warehouses: [], products: [], balances: [] } }
  }))
  let releaseConsumption = () => {}
  const holdConsumption = new Promise<void>(resolve => { releaseConsumption = resolve })
  await page.route('**/api/operational-closure/lists/consumption?*', async route => {
    await holdConsumption
    await route.fulfill({ json: { code: 0, data: {
      rows: [{ bizNo: 'OLD-CONSUMPTION' }], page: 1, size: 20, total: 1
    } } })
  })
  await page.route('**/api/operational-closure/lists/settlement?*', route => route.fulfill({ json: { code: 0, data: {
    rows: [{ settlementItemId: 1, settlementNo: 'JS-CURRENT' }],
    summary: { totalQuantity: 9, totalAmount: 123.45 }, page: 1, size: 20, total: 1
  } } }))
  try {
    await page.goto('/features/department-consumption')
    const started = page.waitForRequest('**/api/operational-closure/lists/consumption?*')
    await login(page)
    await started
    const menu = page.locator('.spd-side-nav')
    await menu.getByText('结算与财务', { exact: true }).click()
    await menu.getByText('结算对账', { exact: true }).click()
    await expect(page.getByText('JS-CURRENT', { exact: true })).toBeVisible()
    const finished = page.waitForResponse('**/api/operational-closure/lists/consumption?*')
    releaseConsumption()
    await (await finished).finished()
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
    await expect(page.getByText('JS-CURRENT', { exact: true })).toBeVisible()
    await expect(page.getByRole('region', { name: '结算汇总' }).getByText('¥ 123.45', { exact: true })).toBeVisible()
  } finally {
    releaseConsumption()
  }
})

test('科室申领历史按查询条件和服务端排序分页', async ({ page }) => {
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))
  await page.route('**/api/operational-closure/lists/requisition?*', route => {
    const params = new URL(route.request().url()).searchParams
    const filtered = params.get('deptName') === '设备'
    return route.fulfill({ json: { code: 0, data: {
      page: Number(params.get('page')), size: 20, total: filtered ? 2 : 23,
      rows: filtered
        ? [{ bizNo: 'SL-B', deptName: '设备乙科' }, { bizNo: 'SL-A', deptName: '设备甲科' }]
        : [{ bizNo: `SL-PAGE-${params.get('page')}` }]
    } } })
  })
  await page.goto('/features/department-requisition')
  await login(page)
  await expect(page.getByText('SL-PAGE-1', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByText('SL-PAGE-2', { exact: true })).toBeVisible()

  const query = page.getByRole('form', { name: '历史申领单查询' })
  await query.getByLabel('开始日期').fill('2026-11-01')
  await query.getByLabel('结束日期').fill('2026-11-18')
  await query.getByLabel('状态').selectOption('pending_approval')
  await query.getByLabel('科室', { exact: true }).fill('设备')
  await query.getByLabel('申领单号').fill('SL')
  const loaded = page.waitForRequest(r => r.url().includes('/lists/requisition?') && r.url().includes('departmentDesc'))
  await query.getByLabel('排序').selectOption('departmentDesc')
  const params = new URL((await loaded).url()).searchParams
  expect(Object.fromEntries(params)).toMatchObject({
    page: '1', dateFrom: '2026-11-01', dateTo: '2026-11-18',
    status: 'pending_approval', deptName: '设备', requisitionNo: 'SL', sort: 'departmentDesc'
  })
  await expect(page.locator('.requisition-order-table tbody tr td:first-child')).toHaveText(['SL-B', 'SL-A'])
  await query.getByRole('button', { name: '重置' }).click()
  await expect(page.getByText('SL-PAGE-1', { exact: true })).toBeVisible()
})

test('待审批科室申领单可在历史列表审批通过', async ({ page }) => {
  const no = 'SL-SMOKE-APPROVAL'
  let status = 'pending_approval'
  await page.route('**/api/operational-closure/lists/requisition**', route => route.fulfill({
    json: { code: 0, data: { rows: [{ bizNo: no, status }], total: 1, page: 1, size: 20 } }
  }))
  await page.route(`**/api/operational-closure/requisitions/${no}/items`, route => route.fulfill({
    json: { code: 0, data: { rows: [
      { itemId: 1, productCode: 'P001', productName: '散货', applyQuantity: 10 },
      { itemId: 2, productCode: 'P002', productName: '定数包商品', itemType: 'quota_package',
        packageQuantity: 5, packageCount: 2, applyQuantity: 10 }
    ], total: 2, requisitionNo: no } }
  }))
  await page.route(`**/api/operational-closure/requisitions/${no}/action`, route => {
    expect(route.request().method()).toBe('PUT')
    const { action, edits } = route.request().postDataJSON()
    expect(action).toBe('approve')
    expect(edits.sort((a: { itemId: number }, b: { itemId: number }) => a.itemId - b.itemId)).toEqual([
      { itemId: 1, originalQuantity: 10 }, { itemId: 2, originalQuantity: 10, quantity: 3 }
    ])
    status = 'approved'
    return route.fulfill({ json: { code: 0, data: { requisitionNo: no, status } } })
  })

  await page.goto('/features/department-requisition')
  await login(page)
  const row = page.getByRole('row').filter({ hasText: no })
  const approve = row.getByRole('button', { name: '审批通过' })
  await approve.click()
  await expect(page.getByRole('spinbutton', { name: 'P001 审批数量' })).toHaveValue('10')
  const packs = page.getByRole('spinbutton', { name: 'P002 审批数量' })
  await expect(packs).toHaveValue('2')
  const firstInput = page.getByRole('dialog', { name: '科室申领审批' }).getByRole('spinbutton').first()
  await firstInput.click({ trial: true, timeout: 5000 })
  await packs.fill('3')
  await page.getByRole('button', { name: '确认通过' }).click()
  await expect(row.getByText('已通过', { exact: true })).toBeVisible()
  await expect(approve).toBeHidden()
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
  await page.route('**/api/auth/login', route => route.fulfill({ json: { code: 0, data: { token: 'requisition-type-test' } } }))
  await page.route('**/api/auth/me', route => route.fulfill({ json: { code: 0, data: {
    userId: 1, username: 'requisition-tester', status: 1, roles: ['ROLE_ADMIN'], menuCodes: [], permissionCodes: []
  } } }))
  await page.route('**/api/system-notices/login-delivery?*', route => route.fulfill({ json: { code: 0, data: null } }))

  await page.route('**/api/operational-closure/requisitions/options', route => route.fulfill({
    contentType: 'application/json',
    body: ok({
      departments: [{ deptCode: 'SURG', deptName: '手术室' }],
      requisitionTypes: [{ optionValue: 'urgent', optionLabel: '紧急申领' }],
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
    body: ok([{ warehouseId: 20, code: 'WH-SURG', name: '手术室二级库', selected: 1, sourceWarehouses: [
      { warehouseId: 30, code: 'WH-30', name: '中心库' },
      { warehouseId: 31, code: 'WH-31', name: '另一个一级库' }
    ] }])
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
  const requisitionType = page.getByRole('combobox', { name: '申领类型', exact: true })
  await expect(requisitionType).toHaveValue('')
  await expect(requisitionType).not.toHaveAttribute('required')
  await requisitionType.selectOption('urgent')
  await expect(page.getByText('当前院区有多个供货库房，请选择本次从哪个库房发货')).toBeVisible()
  expect(catalogCallCount).toBe(0)
  await page.getByLabel('供货库房', { exact: true }).selectOption('30')
  await expect(page.getByText('LOW-001')).toBeVisible()
  expect(catalogCallCount).toBeGreaterThan(0)
  const catalogUrl = new URL(catalogRequestUrl)
  expect(catalogUrl.searchParams.get('deptName')).toBe('手术室')
  expect(catalogUrl.searchParams.get('deptCode')).toBe('SURG')
  expect(catalogUrl.searchParams.get('warehouseName')).toBe('手术室二级库')
  expect(catalogUrl.searchParams.get('destinationWarehouseId')).toBe('20')
  expect(catalogUrl.searchParams.get('sourceWarehouseId')).toBe('30')
  expect(catalogUrl.searchParams.get('page')).toBe('1')
  expect(catalogUrl.searchParams.get('size')).toBe('20')

  await page.getByRole('button', { name: '智能补货' }).click()
  await expect(page.getByRole('dialog', { name: '智能补货分析' })).toBeVisible()
  await page.getByRole('dialog', { name: '智能补货分析' }).getByRole('spinbutton').fill('8')
  await page.getByRole('button', { name: '确认并生成申领单' }).click()
  await expect(page.getByText(/SL-SMART-001/)).toBeVisible()
  expect(smartGeneratePayload).toEqual({
    analysisId: 88,
    requisitionType: 'urgent',
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
  await page.getByRole('dialog', { name: '新增科室申领' })
    .getByRole('button', { name: '查询', exact: true }).click()
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
    destinationWarehouseId: 20,
    sourceWarehouseId: 30,
    requisitionType: 'urgent',
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
