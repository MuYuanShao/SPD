import { expect, test, type Page } from '@playwright/test'

const username = process.env.SPD_E2E_USERNAME || process.env.SPD_USERNAME
const password = process.env.SPD_E2E_PASSWORD || process.env.SPD_PASSWORD
if (!username || !password) throw new Error('License UI tests require SPD_USERNAME/SPD_PASSWORD')
const pdf = { name: 'certificate.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7 license test') }
const png = { name: 'scan.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRlsAAAAASUVORK5CYII=', 'base64') }
const ok = (data: unknown) => JSON.stringify({ code: 0, message: 'success', data })

// Mock only the feature API; login and the Vue page use the running application.
async function setup(page: Page, failPdfOnce = false) {
  const state = { creates: 0, updates: 0, uploads: [] as string[], revision: 0, row: null as Record<string, unknown> | null, files: [] as Record<string, unknown>[] }
  await page.route(url => url.pathname === '/api/licenses' || url.pathname.startsWith('/api/licenses/'), async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const reply = (data: unknown) => route.fulfill({ contentType: 'application/json', body: ok(data) })
    if (path === '/api/licenses/owner-options') return reply({ rows: [{ id: 501, code: 'TEST-OWNER', name: '测试主体' }], total: 1, page: 1, size: 10 })
    if (path === '/api/licenses' && request.method() === 'GET') return reply({ rows: state.row ? [{ ...state.row, attachmentCount: state.files.length }] : [], total: state.row ? 1 : 0, page: 1, size: 20 })
    if (path === '/api/licenses' && request.method() === 'POST') {
      state.creates++
      state.revision = 1
      state.row = { ...request.postDataJSON(), id: 7001 }
      return reply({ id: 7001 })
    }
    if (path === '/api/licenses/7001' && request.method() === 'PUT') {
      expect(request.postDataJSON().revisionNo).toBe(state.revision)
      state.updates++
      state.revision++
      state.row = { ...state.row, ...request.postDataJSON() }
      return reply({ id: 7001 })
    }
    if (path === '/api/licenses/7001/attachments' && request.method() === 'POST') {
      expect(request.headers()['content-type']).toContain('multipart/form-data; boundary=')
      const body = request.postDataBuffer()!.toString()
      const name = body.includes('scan.png') ? 'scan.png' : 'certificate.pdf'
      expect(body).toContain('name="category"')
      expect(body).toContain('license')
      state.uploads.push(name)
      if (name === 'certificate.pdf' && failPdfOnce) {
        failPdfOnce = false
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 400, message: '测试上传失败', data: null }) })
      }
      state.revision++
      const id = 9000 + state.files.length
      state.files.push({ id, fileName: name, fileType: name.endsWith('.png') ? 'image/png' : 'application/pdf', size: 100, createTime: '2026-09-30 10:00' })
      return reply({ attachmentId: id })
    }
    if (path === '/api/licenses/7001/attachments') return reply(state.files)
    if (/^\/api\/licenses\/attachments\/\d+\/file$/.test(path)) {
      const file = state.files.find(f => String(f.id) === path.split('/')[4])!
      return route.fulfill({ contentType: String(file.fileType), body: file.fileName === 'scan.png' ? png.buffer : pdf.buffer })
    }
    throw new Error(`Unexpected license request: ${request.method()} ${path}`)
  })
  await page.goto('/features/license-management')
  await page.getByRole('textbox', { name: '用户名' }).fill(username!)
  await page.getByRole('textbox', { name: '密码' }).fill(password!)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: '证照管理', exact: true })).toBeVisible()
  return state
}

async function fillForm(page: Page, label: string) {
  await page.getByRole('button', { name: label, exact: true }).click()
  await page.getByRole('button', { name: `新增${label}`, exact: true }).click()
  const dialog = page.locator('.license-form-dialog')
  await dialog.getByLabel('证照名称', { exact: true }).fill('新增附件测试证照')
  await dialog.getByRole('button', { name: '搜索主体', exact: true }).click()
  await dialog.getByRole('button', { name: '测试主体（TEST-OWNER）', exact: true }).click()
  return dialog
}

for (const label of ['商品证照', '供应商证照', '厂家证照', '合同管理']) {
  test(`${label}新增可多选附件、移除并保存后阅览`, async ({ page }) => {
    const state = await setup(page)
    const dialog = await fillForm(page, label)
    await dialog.getByLabel('选择证照附件').setInputFiles([png, pdf])
    await expect(dialog.locator('.license-form-file-list li')).toHaveCount(2)
    await dialog.getByRole('button', { name: '移除附件 certificate.pdf', exact: true }).click()
    await expect(dialog.locator('.license-form-file-list li')).toHaveCount(1)
    await dialog.getByLabel('选择证照附件').setInputFiles(pdf)
    await expect(dialog.locator('.license-form-file-list li')).toHaveCount(2)
    if (label === '商品证照') await page.screenshot({ path: 'output/license-create-attachments.png', fullPage: true })
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    await expect(dialog).toBeHidden()
    expect(state.creates).toBe(1)
    expect(state.uploads).toEqual(['scan.png', 'certificate.pdf'])
    await expect(page.locator('.license-page').getByText('证照已新增，附件上传成功', { exact: true })).toBeVisible()
    await page.locator('.license-table tbody').getByRole('button', { name: '附件', exact: true }).click()
    await expect(page.locator('.license-attachment-items li')).toHaveCount(2)
    await page.locator('.license-attachment-items li').filter({ hasText: 'scan.png' }).getByRole('button', { name: '阅览', exact: true }).click()
    await expect(page.locator('.license-attachment-preview img')).toHaveAttribute('src', /^blob:/)
  })
}

test('部分上传失败后仅重试剩余文件，复用证照且保持版本一致', async ({ page }) => {
  const state = await setup(page, true)
  const dialog = await fillForm(page, '商品证照')
  await dialog.getByLabel('选择证照附件').setInputFiles([png, pdf])
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('当前还有 1 个附件未上传')
  await expect(dialog.locator('.license-form-file-list li')).toHaveCount(1)
  await expect(dialog.locator('.license-form-file-list')).toContainText('certificate.pdf')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(state.creates).toBe(1)
  expect(state.updates).toBe(1)
  expect(state.uploads).toEqual(['scan.png', 'certificate.pdf', 'certificate.pdf'])
  expect(state.files).toHaveLength(2)
})

test('取消新增清空附件且不产生写请求，无附件仍可正常保存', async ({ page }) => {
  const state = await setup(page)
  const dialog = await fillForm(page, '商品证照')
  await dialog.getByLabel('选择证照附件').setInputFiles(png)
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  expect(state.creates).toBe(0)
  expect(state.uploads).toHaveLength(0)
  await fillForm(page, '商品证照')
  await expect(dialog.locator('.license-form-file-list li')).toHaveCount(0)
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(state.creates).toBe(1)
  expect(state.uploads).toHaveLength(0)
})
