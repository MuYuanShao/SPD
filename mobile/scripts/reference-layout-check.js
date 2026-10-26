async(page)=>{
 let apiRequests=0;await page.context().unroute('**/api/**');await page.context().route('**/api/**',async route=>{apiRequests++;await route.abort();});
 await page.setViewportSize({width:1440,height:1000});await page.goto('http://127.0.0.1:1831/?demo=1');
 await page.locator('.demo-tools>summary').click();await page.getByRole('button',{name:'重置演示数据'}).click();
 await page.getByLabel('账号',{exact:true}).waitFor();await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/reference-login.png'});
 await page.getByRole('button',{name:'服务器设置',exact:true}).click();await page.getByRole('textbox',{name:'服务器地址'}).waitFor();await page.getByRole('button',{name:'返回',exact:true}).click();
 await page.getByLabel('账号',{exact:true}).fill('demo');await page.getByLabel('密码',{exact:true}).fill('demo123');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('button',{name:/^拣选/}).waitFor();
 await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/reference-home.png'});
 const bounds=await page.locator('.mobile-app').boundingBox();if(bounds.width!==390||Math.abs(bounds.x-525)>1)throw Error('Desktop app is shifted');
 await page.getByRole('button',{name:/^拣选/}).click();await page.locator('article.order-task').filter({hasText:'演示-申领-103'}).getByRole('button',{name:'进入申领单'}).click();await page.locator('article.product-task').getByRole('button',{name:'扫描此明细'}).click();
 await page.getByLabel('PDA 扫码 / 手工输入',{exact:true}).fill('U901');await page.getByLabel('PDA 扫码 / 手工输入',{exact:true}).press('Enter');await page.getByRole('status').filter({hasText:'物料核对成功'}).waitFor();
 await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/reference-picking.png'});
 await page.getByRole('button',{name:'校验本次拣选'}).click();await page.getByRole('button',{name:'确认本次拣选'}).click();await page.getByText('本次拣选成功',{exact:true}).waitFor();await page.getByRole('button',{name:'完成并继续本单拣选'}).click();await page.getByRole('button',{name:'返回单据列表',exact:true}).click();await page.getByRole('button',{name:'返回',exact:true}).click();
 await page.getByRole('button',{name:/^签收/}).click();await page.locator('article').filter({hasText:'演示-配送-20'}).getByRole('button',{name:'打开核对'}).click();
 for(const code of ['PK11','PK12']){const input=page.getByLabel('PDA 扫码 / 手工输入',{exact:true});await input.fill(code);await input.press('Enter');await page.getByRole('status').filter({hasText:'实物码核对成功'}).waitFor();}
 await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/reference-signing.png'});
 await page.getByRole('button',{name:'校验本次签收'}).click();await page.getByRole('button',{name:'确认整单签收'}).click();await page.getByRole('button',{name:'完成并刷新任务'}).click();await page.getByRole('button',{name:'返回',exact:true}).click();
 await page.getByRole('button',{name:'扫码消耗',exact:false}).click();await page.getByLabel('扫描实际定数包').fill('PK8');await page.getByLabel('扫描实际定数包').press('Enter');await page.getByRole('checkbox').waitFor();
 await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/reference-consumption.png'});
 await page.getByRole('checkbox').check();await page.getByRole('button',{name:'校验整包消耗',exact:true}).click();await page.getByRole('button',{name:'确认整包消耗',exact:true}).click();await page.getByText('定数包整包消耗成功',{exact:true}).waitFor();await page.getByRole('button',{name:'完成并继续扫码'}).click();await page.getByRole('button',{name:'返回',exact:true}).click();
 await page.getByRole('button',{name:'待处理',exact:true}).click();await page.getByText('暂无待处理记录',{exact:true}).waitFor();await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/reference-pending.png'});await page.getByRole('button',{name:'返回',exact:true}).click();
 for(const width of [360,390]){await page.setViewportSize({width,height:800});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Horizontal overflow at '+width);await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/reference-home-'+width+'.png'});}
 if(apiRequests)throw Error('Demo reached real API');
 await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/reference-desktop.png'});
 console.log('PASS: reference navigation, login/settings, centered390px, picking/signing/consumption, pending, 360/390px, no real requests');
}
