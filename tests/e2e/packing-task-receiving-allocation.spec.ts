import { expect, test, type APIRequestContext } from '@playwright/test'

/**
 * 打包任务确认界面「按验收单分配散货」流程冒烟用例。
 *
 * 该用例会先在真实 MySQL 中自建冒烟数据（收货单草稿 → 审核入库 → 新建打包任务预占 10），
 * 再驱动浏览器验证页面交互：查询验收单散货 → 部分分配 → 全部打包分配 → 分配明细与预占数量更新。
 * 数据可重复创建（每次使用新批号与自动编号），不依赖预置业务数据。
 */
const USERNAME = process.env.SPD_E2E_USERNAME || 'admin'
const PASSWORD = process.env.SPD_E2E_PASSWORD || 'admin123'
const PRODUCT_CODE_OVERRIDE = process.env.SPD_E2E_PACK_PRODUCT
const TEMPLATE_CODE_OVERRIDE = process.env.SPD_E2E_PACK_TEMPLATE
const WAREHOUSE_NAME_OVERRIDE = process.env.SPD_E2E_PACK_WAREHOUSE

interface AllocationFixture {
  receivingNo: string
  taskNo: string
  productionBatchNo: string
  productCode: string
  templateCode: string
  warehouseName: string
  packageQuantity: number
}

async function apiLogin(request: APIRequestContext): Promise<string> {
  const response = await request.post('/api/auth/login', {
    data: { username: USERNAME, password: PASSWORD }
  })
  const body = await response.json()
  if (body.code !== 0 || !body.data?.token) {
    throw new Error(`API 登录失败：${JSON.stringify(body).slice(0, 300)}`)
  }
  return body.data.token as string
}

async function createFixture(request: APIRequestContext, token: string): Promise<AllocationFixture> {
  const auth = { Authorization: `Bearer ${token}` }

  const optionsResponse = await request.get('/api/receiving-orders/options', { headers: auth })
  const optionsBody = await optionsResponse.json()
  const supplierName = optionsBody.data?.suppliers?.[0]?.supplierName as string | undefined
  if (!supplierName) {
    throw new Error('没有可用供应商，无法构建按验收单分配冒烟数据')
  }

  const templatesResponse = await request.get('/api/quota-packages/templates', {
    headers: auth,
    params: { page: 1, size: 200 }
  })
  const templatesBody = await templatesResponse.json()
  const templates = (templatesBody.data?.rows ?? []) as Array<Record<string, unknown>>
  const template = templates.find((item) => {
    const active = item.status === '启用'
    const matchesTemplate = !TEMPLATE_CODE_OVERRIDE || item.templateCode === TEMPLATE_CODE_OVERRIDE
    const matchesProduct = !PRODUCT_CODE_OVERRIDE || item.productCode === PRODUCT_CODE_OVERRIDE
    const quantity = Number(item.quantity ?? 0)
    return active && matchesTemplate && matchesProduct && quantity > 0 && quantity <= 10
  })
  if (!template) {
    throw new Error('没有可用的启用定数包模板，无法构建按验收单分配冒烟数据')
  }
  const templateCode = String(template.templateCode)
  const productCode = String(template.productCode)
  const packageQuantity = Number(template.quantity)

  const packingOptionsResponse = await request.get('/api/quota-packages/packing-options', { headers: auth })
  const packingOptionsBody = await packingOptionsResponse.json()
  const warehouses = (packingOptionsBody.data?.warehouses ?? []) as Array<Record<string, unknown>>
  const warehouse = WAREHOUSE_NAME_OVERRIDE
    ? warehouses.find((item) => item.warehouseName === WAREHOUSE_NAME_OVERRIDE)
    : warehouses.find((item) => item.warehouseName === 'SPD中心库') ?? warehouses[0]
  const warehouseName = String(warehouse?.warehouseName ?? '')
  if (!warehouseName) {
    throw new Error('没有可用库房，无法构建按验收单分配冒烟数据')
  }

  // 1. 创建收货单草稿：商品 100 件，验收合格 100 件
  const productionBatchNo = `SMOKE-${Date.now()}`
  const createResponse = await request.post('/api/receiving-orders', {
    headers: auth,
    data: {
      supplierName,
      warehouseName,
      receivingType: 'normal',
      isAgent: false,
      remark: 'Playwright 冒烟：按验收单分配散货',
      items: [
        {
          productCode,
          productionBatchNo,
          productionDate: '2026-08-01',
          expireDate: '2028-12-31',
          quantity: 100,
          qualifiedQuantity: 100,
          unqualifiedQuantity: 0
        }
      ]
    }
  })
  const createBody = await createResponse.json()
  if (createBody.code !== 0 || !createBody.data?.receivingNo) {
    throw new Error(`创建收货单失败：${JSON.stringify(createBody).slice(0, 300)}`)
  }
  const receivingNo = createBody.data.receivingNo as string

  // 2. 审核入库：生成系统批次与 100 可用散货库存
  const approveResponse = await request.put(`/api/receiving-orders/${receivingNo}/action`, {
    headers: auth,
    data: { action: 'approve', opinion: '冒烟用例审核入库' }
  })
  const approveBody = await approveResponse.json()
  if (approveBody.code !== 0) {
    throw new Error(`审核入库失败：${JSON.stringify(approveBody).slice(0, 300)}`)
  }

  // 3. 新建打包任务：按模板预占 1 包（每包 10 件），剩余 90 件可分配散货
  const taskResponse = await request.post('/api/quota-packages/packing-tasks', {
    headers: auth,
    data: { templateCode, warehouseName, packageCount: 1 }
  })
  const taskBody = await taskResponse.json()
  if (taskBody.code !== 0 || !taskBody.data?.taskNo) {
    throw new Error(`创建打包任务失败：${JSON.stringify(taskBody).slice(0, 300)}`)
  }

  return {
    receivingNo,
    taskNo: taskBody.data.taskNo as string,
    productionBatchNo,
    productCode,
    templateCode,
    warehouseName,
    packageQuantity
  }
}

test('打包任务确认界面支持按验收单号分配散货（查询→部分分配→全部打包分配）', async ({ page, request }) => {
  const token = await apiLogin(request)
  const fixture = await createFixture(request, token)

  await page.goto('/features/packing-task-confirmation')
  const username = page.getByRole('textbox', { name: '用户名' })
  if (await username.isVisible()) {
    await username.fill(USERNAME)
    await page.getByRole('textbox', { name: '密码' }).fill(PASSWORD)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(username).toBeHidden()
  }

  // 页面标题（h2）在紧凑数据工作台模式下被样式隐藏，改用面板标题与任务表断言页面就绪
  await expect(page.getByRole('heading', { name: '按验收单分配散货' })).toBeVisible()
  await expect(page.locator('.packing-task-table')).toContainText(fixture.taskNo)

  // 查询验收单当前可用散货（建单预占按效期 FIFO 可能落在其它批次，动态取实际可用量）
  const looseResponse = await request.get('/api/quota-packages/receiving-loose-stock', {
    headers: { Authorization: `Bearer ${token}` },
    params: { receivingNo: fixture.receivingNo }
  })
  const looseBody = await looseResponse.json()
  const initialAvailable = Number(looseBody.data?.[0]?.availableQty ?? 0)
  expect(initialAvailable).toBeGreaterThan(30)
  expect(initialAvailable % fixture.packageQuantity).toBe(0)

  const allocatePanel = page.locator('.receiving-allocate-panel')
  const looseTable = allocatePanel.locator('table').filter({ hasText: '可用散货' })
  const allocationTable = allocatePanel.locator('table').filter({ hasText: '已分配' })

  // 选择任务并查询验收单散货
  await page.getByLabel('打包任务').selectOption(fixture.taskNo)
  await page.getByLabel('验收单号').fill(fixture.receivingNo)
  await page.getByRole('button', { name: '查询散货' }).click()
  await expect(looseTable).toBeVisible()
  await expect(looseTable.locator('tbody tr').first()).toContainText(fixture.productCode)
  await expect(looseTable.locator('tbody tr').first().locator('td').nth(3)).toContainText(String(initialAvailable))

  // 部分分配 30：剩余保持散货库存，生成验收单来源分配明细
  await page.getByLabel('分配数量').fill('30')
  await page.getByRole('button', { name: '分配', exact: true }).click()
  await expect(page.locator('p.inline-message')).toContainText('分配 30')
  await expect(page.locator('p.inline-message')).toContainText('剩余保持散货库存')
  await expect(looseTable.locator('tbody tr').first().locator('td').nth(3)).toContainText(String(initialAvailable - 30))

  const receivingRows = allocationTable.locator('tbody tr').filter({ hasText: fixture.receivingNo })
  await expect(receivingRows).toHaveCount(1)
  await expect(receivingRows.first().locator('td').nth(4)).toContainText('30')

  // 全部打包分配：剩余全部转入任务，验收单不再有散货
  await page.getByRole('button', { name: '全部打包分配', exact: true }).click()
  await expect(page.locator('p.inline-message')).toContainText('该验收单没有可分配的散货库存')
  await expect(looseTable).toHaveCount(0)
  await expect(receivingRows).toHaveCount(2)
  await expect(receivingRows.nth(1).locator('td').nth(4)).toContainText(String(initialAvailable - 30))

  // 任务预占散货更新为 建单预占 + 30 + 剩余
  const taskOption = page.getByLabel('打包任务').locator('option', { hasText: fixture.taskNo })
  await expect(taskOption).toContainText(`已预占 ${initialAvailable + fixture.packageQuantity}`)
})
