async (page) => {
 let externalApiRequests=0;
 await page.context().route('**/api/**',async route=>{externalApiRequests++;await route.abort();});
 await page.setViewportSize({width:430,height:900});await page.goto('http://127.0.0.1:1831/?demo=1');await page.locator('.demo-tools>summary').click();await page.getByRole('button',{name:'重置演示数据'}).click();
 await page.getByLabel('账号',{exact:true}).fill('demo');await page.getByLabel('密码',{exact:true}).fill('demo123');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('button',{name:/^签收/}).click();
 await page.getByRole('combobox',{name:'科室与库房'}).selectOption('4');
 await page.locator('article').filter({hasText:'演示-配送-10'}).getByRole('button',{name:'打开核对'}).click();await page.getByRole('checkbox').check();await page.getByRole('button',{name:'校验本次签收'}).click();await page.getByRole('button',{name:'确认整单签收'}).click();await page.getByText('配送整单签收成功（本地演示）',{exact:true}).waitFor();await page.getByRole('button',{name:'完成并刷新任务'}).click();
 await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('button',{name:/^拣选/}).click();await page.getByRole('combobox',{name:'科室与库房'}).selectOption('4');
 for(const [id,code] of [[101,'P0'],[102,'PK701'],[103,'U901']]) {
  if(id!==101)await page.getByRole('button',{name:'返回单据列表',exact:true}).click();await page.locator('article.order-task').filter({hasText:'演示-申领-'+id}).getByRole('button',{name:'进入申领单'}).click();await page.locator('article.product-task').getByRole('button',{name:'扫描此明细'}).click();const scan=page.getByLabel('PDA 扫码 / 手工输入',{exact:true});await scan.fill(code);await scan.press('Enter');await page.getByRole('status').filter({hasText:'物料核对成功'}).waitFor();
  if(id===101)await page.getByRole('textbox',{name:'本次数量（个）'}).fill('2');
  await page.getByRole('button',{name:'校验本次拣选'}).click();await page.getByRole('button',{name:'确认本次拣选'}).click();await page.getByText('本次拣选成功',{exact:true}).waitFor();await page.getByRole('button',{name:'完成并继续本单拣选'}).click();
 }
 await page.getByRole('button',{name:'返回单据列表',exact:true}).click();await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('button',{name:/^扫码消耗/}).click();await page.getByRole('combobox',{name:'科室与库房'}).selectOption('4');const pack=page.getByLabel('扫描实际定数包');await pack.fill('PK8');await pack.press('Enter');await page.getByRole('checkbox').check();await page.getByRole('button',{name:'校验整包消耗',exact:true}).click();await page.getByRole('button',{name:'确认整包消耗',exact:true}).click();await page.getByText('定数包整包消耗成功',{exact:true}).waitFor();await page.getByText('后台金额：20 元',{exact:true}).waitFor();
 if(externalApiRequests)throw Error('Demo attempted external requests: '+externalApiRequests);
 await page.screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/local-demo-success.png',fullPage:true});
 await page.locator('.demo-tools>summary').click();await page.getByRole('button',{name:'重置演示数据'}).click();await page.getByLabel('账号',{exact:true}).waitFor();
 console.log('PASS: local login, signing, three picking modes, whole-package consumption, reset, zero real API requests');
}
