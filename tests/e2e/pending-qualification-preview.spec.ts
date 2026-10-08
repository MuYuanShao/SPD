import { expect, test, type Page } from '@playwright/test'
const username=process.env.SPD_E2E_USERNAME || process.env.SPD_USERNAME
const password=process.env.SPD_E2E_PASSWORD || process.env.SPD_PASSWORD
if (!username || !password) throw new Error('Qualification UI tests require SPD_USERNAME/SPD_PASSWORD')
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRlsAAAAASUVORK5CYII=','base64')
const pdf=Buffer.from('%PDF-1.7 qualification preview test')
const files=[
  {attachmentId:9101,fileName:'申请扫描件.png',contentType:'image/png',fileSize:png.length,source:'application',category:'qualification',createTime:'2026-09-30 10:00'},
  {attachmentId:9102,fileName:'供应商经营证照.pdf',contentType:'application/pdf',fileSize:pdf.length,source:'license',licenseName:'经营证照',licenseNo:'LICENSE-TEST',ownerName:'测试供应商',licenseType:'supplier',licenseStatus:'expired',expireDate:'2025-01-01',category:'license',createTime:'2026-09-30 10:00'}
]
async function setup(page:Page,mode:'normal'|'error'|'empty'='normal') {
 let fail=mode==='error'
 const reads:string[]=[]
 // Only qualification responses are mocked; login, lists and application detail use the real APIs.
 await page.route(url=>/^\/api\/pending-product-applications\/[^/]+\/qualification-attachments(?:\/|$)/.test(url.pathname),async route=>{
  const req=route.request();const url=new URL(req.url());
  expect(req.headers().authorization).toMatch(/^Bearer /)
  if(url.pathname.endsWith('/qualification-attachments')) return route.fulfill({contentType:'application/json',body:JSON.stringify({code:0,message:'success',data:mode==='empty'?[]:files})})
  reads.push(url.pathname)
  if(fail) { fail=false;return route.fulfill({status:403,contentType:'application/json',body:JSON.stringify({code:403,message:'无权读取资质附件',data:null})}) }
  const image=url.pathname.endsWith('/9101/file')
  return route.fulfill({contentType:image?'image/png':'application/pdf',body:image?png:pdf})
 })
 await page.goto('/features/pending-product-catalog?scope=todo&type=new')
 await page.getByRole('textbox',{name:'用户名'}).fill(username!)
 await page.getByRole('textbox',{name:'密码'}).fill(password!)
 await page.getByRole('button',{name:'登录'}).click()
 await expect(page.locator('.approval-wide-table tbody tr').first()).toBeVisible()
 return reads
}

test('待审批列表可阅览申请图片、关联证照PDF并下载',async({page})=>{
 const reads=await setup(page)
 await page.getByRole('button',{name:'资质阅览',exact:true}).first().click()
 const attachments=page.getByRole('dialog',{name:/资质证照附件/})
 await expect(attachments.locator('.qualification-files li')).toHaveCount(2)
 await expect(attachments).toContainText('供应商证照')
 await expect(attachments).toContainText('已过期')
 await attachments.getByRole('button',{name:'阅览 申请扫描件.png',exact:true}).click()
 const image=page.getByRole('dialog',{name:'申请扫描件.png',exact:true})
 await expect(image.locator('img')).toHaveAttribute('src',/^blob:/)
 await expect.poll(()=>image.locator('img').evaluate((element:HTMLImageElement)=>element.naturalWidth)).toBeGreaterThan(0)
 await image.getByRole('button',{name:'关闭',exact:true}).click()
 await attachments.getByRole('button',{name:'阅览 供应商经营证照.pdf',exact:true}).click()
 const preview=page.getByRole('dialog',{name:'供应商经营证照.pdf',exact:true})
 await expect(preview.locator('iframe')).toHaveAttribute('src',/^blob:/)
 const download=page.waitForEvent('download')
 await preview.getByRole('button',{name:'下载附件',exact:true}).click()
 expect((await download).suggestedFilename()).toBe('供应商经营证照.pdf')
 expect(reads).toHaveLength(3)
 await page.screenshot({path:'output/qualification-list-preview.png',fullPage:true})
})

test('审批详情可直接阅览相同的关联资质附件',async({page})=>{
 await setup(page)
 await page.locator('.approval-wide-table tbody tr').first().getByRole('link',{name:'查看',exact:true}).click()
 await expect(page.locator('.qualification-card .qualification-files li')).toHaveCount(2)
 await page.locator('.qualification-card').getByRole('button',{name:'阅览 供应商经营证照.pdf',exact:true}).click()
 await expect(page.getByRole('dialog',{name:'供应商经营证照.pdf',exact:true}).locator('iframe')).toHaveAttribute('src',/^blob:/)
})

test('附件读取无权限显示中文错误并保留列表供重试',async({page})=>{
 const reads=await setup(page,'error')
 await page.getByRole('button',{name:'资质阅览',exact:true}).first().click()
 const attachments=page.getByRole('dialog',{name:/资质证照附件/})
 await attachments.getByRole('button',{name:'阅览 申请扫描件.png',exact:true}).click()
 const preview=page.getByRole('dialog',{name:'申请扫描件.png',exact:true})
 await expect(preview.getByRole('alert')).toHaveText('无权读取资质附件')
 await preview.getByRole('button',{name:'关闭',exact:true}).click()
 await expect(attachments.locator('.qualification-files li')).toHaveCount(2)
 await attachments.getByRole('button',{name:'阅览 申请扫描件.png',exact:true}).click()
 await expect(preview.locator('img')).toHaveAttribute('src',/^blob:/)
 expect(reads).toHaveLength(2)
})

test('无资质附件展示空状态',async({page})=>{
 await setup(page,'empty')
 await page.getByRole('button',{name:'资质阅览',exact:true}).first().click()
 await expect(page.getByRole('dialog',{name:/资质证照附件/})).toContainText('暂无资质附件')
})
