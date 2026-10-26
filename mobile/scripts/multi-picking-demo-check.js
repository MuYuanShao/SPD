async(page)=>{
 await page.context().unroute('**/api/**');let externalRequests=0;await page.context().route('**/api/**',async route=>{externalRequests++;await route.abort()});
 await page.setViewportSize({width:1440,height:1000});await page.goto('http://127.0.0.1:1831/?demo=1&scenario=multi');await page.locator('.demo-tools>summary').click();await page.getByRole('button',{name:'重置演示数据'}).click();
 await page.getByLabel('账号',{exact:true}).fill('demo');await page.getByLabel('密码',{exact:true}).fill('demo123');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('button',{name:/^拣选/}).click();
 if(await page.locator('article.order-task').count()!==2)throw Error('Expected two requisition cards, not product lines');
 await page.getByRole('textbox',{name:'检索申领单或商品'}).fill('注射器');await page.getByRole('button',{name:'查询',exact:true}).click();await page.locator('article[data-order-id="5001"]').getByRole('button',{name:'进入申领单'}).click();await page.locator('article.product-task').nth(4).waitFor();
 for(const [id,code,spec] of [[104,'SYR-5','5ml'],[105,'SYR-10','10ml']]){const card=page.locator('article[data-item-id="'+id+'"]');await card .getByText('SYR-'+(id===104?'5':'10'),{exact:true}).waitFor();await card.getByText(code,{exact:true}).waitFor();await card.getByText(spec+' · 无菌独立包装',{exact:true}).waitFor();}
 await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/multi-product-list-after.png'});
 await page.locator('article[data-item-id="104"]').getByRole('button',{name:'扫描此明细'}).click();await page.getByText('SYR-5',{exact:true}).waitFor();await page.getByText('5ml · 无菌独立包装',{exact:true}).waitFor();
 const scan=page.getByLabel('PDA 扫码 / 手工输入',{exact:true});await scan.fill('SYR-10');await scan.press('Enter');await page.getByRole('status').filter({hasText:'演示码不匹配'}).waitFor();await scan.fill('SYR-5');await scan.press('Enter');await page.getByRole('status').filter({hasText:'物料核对成功'}).waitFor();
 const amount=page.getByRole('textbox',{name:'本次数量（支）'});await amount.fill('1.5');if(await page.getByRole('button',{name:'校验本次拣选'}).isEnabled())throw Error('Fractional picking allowed');await amount.fill('2');
 await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/multi-product-detail-after.png'});
 await page.getByRole('button',{name:'校验本次拣选'}).click();await page.getByRole('button',{name:'确认本次拣选'}).click();await page.getByText('本次拣选成功',{exact:true}).waitFor();await page.getByRole('button',{name:'完成并继续本单拣选'}).click();
 await page.locator('article[data-item-id="104"]').getByText('待拣 8',{exact:true}).waitFor();await page.locator('article[data-item-id="105"]').getByText('待拣 10',{exact:true}).waitFor();
 await page.locator('.mobile-app').screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/multi-product-partial-after.png'});
 for(const width of [360,390]){await page.setViewportSize({width,height:800});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Horizontal overflow at '+width)}
 if(externalRequests)throw Error('Demo requested real SPD');await page.setViewportSize({width:1440,height:1000});console.log('PASS: same requisition five items, distinct code/spec, wrong-item scan rejected, whole quantities only, partial picking isolated, no real requests');
}
