async(page)=>{
 let real=0;await page.context().unroute('**/api/**');await page.context().route('**/api/**',async r=>{real++;await r.abort()});
 await page.setViewportSize({width:390,height:900});await page.goto('http://127.0.0.1:1831/?demo=1&scenario=multi');await page.locator('.demo-tools>summary').click();await page.getByRole('button',{name:'重置演示数据'}).click();
 await page.getByLabel('账号',{exact:true}).fill('demo');await page.getByLabel('密码',{exact:true}).fill('demo123');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('button',{name:/^签收/}).click();
 const card=page.locator('article').filter({hasText:'演示-配送-10'});await card.getByText('一次性使用注射器',{exact:false}).waitFor();await card.getByText(/SYR-5/).waitFor();await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/signing-information-list.png'});
 await card.getByRole('button',{name:'打开核对'}).click();
 for(const title of ['单据信息','耗材明细','实物核对'])await page.getByRole('heading',{name:title,exact:true}).waitFor();
 for(const value of ['SL-DEMO-20261026-001','SYR-5','一次性使用注射器','5ml · 无菌独立包装','演示医疗器械厂'])await page.locator('.signing').getByText(value,{exact:true}).waitFor();
 if(await page.locator('.delivery-batch').count()||await page.getByRole('heading',{name:'配送批次',exact:true}).count())throw Error('Batch detail must not be displayed');
 for(const width of [360,390]){await page.setViewportSize({width,height:900});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Signing overflow at '+width)}
 await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/signing-information-detail.png'});
 await page.locator('.screen-scroll').evaluate(el=>el.scrollTop=el.scrollHeight);await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/signing-information-check.png'});await page.getByRole('checkbox').check();await page.getByRole('button',{name:'校验本次签收'}).click();await page.getByRole('button',{name:'确认整单签收'}).click();await page.getByText('配送整单签收成功（本地演示）',{exact:true}).waitFor();await page.locator('.signing').getByText('已签收',{exact:true}).waitFor();await page.getByRole('button',{name:'完成并刷新任务'}).click();
 await page.locator('article').filter({hasText:'演示-配送-20'}).getByRole('button',{name:'打开核对'}).click();await page.getByText('医用纱布定数包',{exact:true}).waitFor();
 for(const code of ['PK11','PK12']){const scan=page.getByLabel('PDA 扫码 / 手工输入',{exact:true});await scan.fill(code);await scan.press('Enter');await page.getByRole('status').filter({hasText:'实物码核对成功'}).waitFor()}
 await page.getByRole('button',{name:'校验本次签收'}).click();await page.getByRole('button',{name:'确认整单签收'}).click();await page.getByText('配送整单签收成功（本地演示）',{exact:true}).waitFor();
 if(real)throw Error('Demo contacted real SPD');console.log('PASS signing fields, no batch display,360/390 layout,loose/package confirmation,no real requests');
}