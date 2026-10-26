async (page) => {
 await page.context().unroute('**/api/**');
 const summary={pack:{id:8,labelNo:'PK8',traceCodeId:9,productId:3,productCode:'P3',productName:'恢复验收物料',unit:'个',quantity:2,deptId:2,deptName:'测试科室',warehouseId:4,warehouseName:'科室库',status:'signed',version:1},batches:[{batchId:5,quantity:2,unitPrice:10}]};
 let original=null,receipt=null,attempts=0,recoveries=0,writes=0,terminal=false;
 await page.context().route('**/api/**',async route=>{
  const req=route.request(),path=req.url().replace(/^https?:\/\/[^/]+/,'').split('?')[0];const body=req.method()==='POST'?req.postDataJSON():null;let data;
  if(path.endsWith('/auth/login'))data={token:'recovery-fixture'};
  else if(path.endsWith('/mobile/context'))data={serverId:'recovery-ui-test',userId:1,username:'恢复界面验收',capabilities:['CONSUME_PACKAGE'],warehouses:[],consumptionWarehouses:[{deptId:2,deptName:'测试科室',warehouseId:4,warehouseName:'科室库'}]};
  else if(path.endsWith('/mobile/consumption/packages/resolve'))data=summary;
  else if(path.endsWith('/mobile/consumption/operations/validate'))data={reviewHash:'old-review',expiresAt:new Date(Date.now()+300000).toISOString(),summary};
  else if(path.endsWith('/mobile/consumption/operations/recover')){
   recoveries++;if(body.operationId!==original.operationId||body.rawCode!==original.rawCode)throw Error('Changed original operation');
   if(terminal){receipt={operationId:body.operationId,kind:body.kind,taskId:8,userId:1,status:'rejected',message:'原操作已终止：电脑端已消耗'};data={result:receipt};}
   else data={reviewHash:'renewed-'+recoveries,expiresAt:new Date(Date.now()+300000).toISOString(),summary};
  }else if(path.endsWith('/mobile/consumption/operations')){
   attempts++;
   if(body.reviewHash==='old-review'){
    original=body;await route.fulfill({status:503,json:{code:503,message:'请求未到服务器'}});return;
   }
   if(body.operationId!==original.operationId||body.rawCode!==original.rawCode||body.reviewHash!=='renewed-'+recoveries)throw Error('Changed retry');
   writes++;receipt={operationId:body.operationId,kind:body.kind,taskId:8,documentNo:'UI-RECOVER-XH',status:'succeeded',userId:1,deviceId:body.deviceId,message:'成功',quantity:2,amount:20};data=receipt;
  }else if(path.includes('/mobile/operations/'))data=receipt??{operationId:path.split('/').pop(),userId:1,status:'unknown'};
  else throw Error('Unexpected '+path);
  await route.fulfill({json:{code:0,data,message:'success'}});
 });
 async function login(){await page.getByLabel('账号',{exact:true}).fill('recovery-user');await page.getByLabel('密码',{exact:true}).fill('fixture');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('button',{name:'登录',exact:true}).waitFor({state:'detached'});}
 async function start(){await page.locator('.menu-card, .warehouse-choice').first().waitFor();if(await page.locator('.menu-card').count())await page.getByRole('button',{name:/^扫码消耗/}).click();await page.getByRole('combobox',{name:'科室与库房'}).selectOption('4');const scan=page.getByLabel('扫描实际定数包');await scan.fill('PK8');await scan.press('Enter');await page.getByRole('checkbox').check();await page.getByRole('button',{name:'校验整包消耗',exact:true}).click();await page.getByRole('button',{name:'确认整包消耗',exact:true}).click();await page.getByRole('button',{name:'恢复原操作并重新核对'}).waitFor();}
 await page.setViewportSize({width:360,height:800});await page.goto('http://127.0.0.1:1831/');await page.evaluate(()=>{for(const key of Object.keys(sessionStorage))if(key.startsWith('spd-dev-signing:'))sessionStorage.removeItem(key);});
 await login();await start();await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('button',{name:/^待处理/}).click();await page.getByText('待核对原结果',{exact:true}).waitFor();await page.locator('.pending-row').click();await page.getByRole('button',{name:'查询原消耗结果'}).waitFor();await page.reload();await login();await page.getByRole('button',{name:'查询原消耗结果'}).click();
 await page.getByRole('button',{name:'恢复原操作并重新核对'}).click();await page.getByRole('button',{name:'确认恢复后的原消耗'}).waitFor();
 if(writes!==0||attempts!==1)throw Error('Recovery submitted automatically');
 await page.reload();await login();await page.getByRole('button',{name:'恢复原操作并重新核对'}).click();await page.getByRole('button',{name:'确认恢复后的原消耗'}).click();await page.getByText('定数包整包消耗成功',{exact:true}).waitFor();
 if(writes!==1||attempts!==2||recoveries!==2)throw Error('Duplicate writes');
 if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Horizontal overflow');await page.screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/recovery-success.png',fullPage:true});
 await page.getByRole('button',{name:'完成并继续扫码'}).click();terminal=true;receipt=null;await start();await page.getByRole('button',{name:'恢复原操作并重新核对'}).click();await page.getByText('原操作已终止：电脑端已消耗',{exact:true}).waitFor();await page.getByRole('button',{name:'返回扫码重新核对'}).click();await page.getByLabel('扫描实际定数包').waitFor();
 if(writes!==1)throw Error('Terminal recovery wrote stock');
 console.log('PASS: lost request, no receipt, same ID, fresh explicit confirmation, recovery/restart, terminal fence UI, 360px');
}
