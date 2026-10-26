async (page) => {
 await page.context().unroute('**/api/**');
 const summary={pack:{id:8,labelNo:'PK8',traceCodeId:9,productId:3,productCode:'P3',productName:'消耗验收物料',unit:'个',quantity:2,deptId:2,deptName:'测试科室',warehouseId:4,warehouseName:'科室库',status:'signed',version:1},batches:[{batchId:5,quantity:2,unitPrice:10}]};
 const receipts=new Map();let writes=0,reviews=0;
 await page.context().route('**/api/**',async route=>{
  const req=route.request(),path=req.url().replace(/^https?:\/\/[^/]+/,'').split('?')[0];const body=req.method()==='POST'?req.postDataJSON():null;let data;
  if(path.endsWith('/auth/login'))data={token:'ui-consumption-token'};
  else if(path.endsWith('/mobile/context'))data={serverId:'consumption-ui-test',userId:1,username:'消耗界面验收',capabilities:['CONSUME_PACKAGE'],warehouses:[],consumptionWarehouses:[{deptId:2,deptName:'测试科室',warehouseId:4,warehouseName:'科室库'}]};
  else if(path.endsWith('/mobile/consumption/packages/resolve')){
   if(body.deptId!==2||body.warehouseId!==4)throw Error('Wrong scope');
   if(body.rawCode!=='PK8'||writes){await route.fulfill({status:409,json:{code:409,message:writes?'定数包已消耗':'包码不匹配'}});return;}data=summary;
  }else if(path.endsWith('/mobile/consumption/operations/validate')){
   reviews++;if(!body.wholePackageConfirmed||'quantity' in body||'amount' in body)throw Error('Invalid whole package confirmation');
   data={reviewHash:'consume-review',expiresAt:new Date(Date.now()+300000).toISOString(),summary};
  }else if(path.endsWith('/mobile/consumption/operations')){
   if(body.reviewHash!=='consume-review')throw Error('Missing review');writes++;
   data={operationId:body.operationId,kind:body.kind,taskId:8,documentNo:'UI-XH1',status:'succeeded',userId:1,deviceId:body.deviceId,completedAt:new Date().toISOString(),message:'成功',quantity:2,amount:20};receipts.set(body.operationId,data);
   await route.fulfill({status:503,json:{code:503,message:'模拟成功响应丢失'}});return;
  }else if(path.includes('/mobile/operations/'))data=receipts.get(path.split('/').pop());
  else throw Error('Unexpected API '+path);
  await route.fulfill({json:{code:0,message:'success',data}});
 });
 async function login(){await page.getByLabel('账号',{exact:true}).fill('ui-consumption');await page.getByLabel('密码',{exact:true}).fill('fixture-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('button',{name:'登录',exact:true}).waitFor({state:'detached'});}
 await page.setViewportSize({width:360,height:800});await page.goto('http://127.0.0.1:1831/');
 await page.evaluate(()=>{for(const key of Object.keys(sessionStorage))if(key.startsWith('spd-dev-signing:'))sessionStorage.removeItem(key);});
 await login();await page.getByRole('button',{name:/^扫码消耗/}).click();await page.getByRole('combobox',{name:'科室与库房'}).selectOption('4');
 const scan=page.getByLabel('扫描实际定数包',{exact:true});await scan.fill('WRONG');await scan.press('Enter');await page.getByRole('status').filter({hasText:'包码不匹配'}).waitFor();
 await scan.fill('PK8');await scan.press('Enter');await page.getByText('实际定数包整包消耗',{exact:true}).waitFor();
 if(await page.getByRole('textbox').count()!==0)throw Error('Unexpected editable quantity');
 if(await page.getByRole('button',{name:'校验整包消耗',exact:true}).isEnabled())throw Error('Missing explicit confirmation');
 await page.getByRole('checkbox').check();await page.getByRole('button',{name:'校验整包消耗',exact:true}).click();await page.getByRole('button',{name:'确认整包消耗',exact:true}).waitFor();
 if(writes!==0)throw Error('Review wrote data');await page.getByRole('button',{name:'确认整包消耗',exact:true}).click();await page.getByRole('button',{name:'查询原消耗结果'}).waitFor();
 await page.reload();await login();await page.getByRole('button',{name:'查询原消耗结果'}).click();await page.getByText('定数包整包消耗成功',{exact:true}).waitFor();await page.getByText('后台金额：20 元',{exact:true}).waitFor();
 if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Horizontal overflow');
 await page.screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/consumption-success.png',fullPage:true});
 await page.getByRole('button',{name:'完成并继续扫码'}).click();await scan.fill('PK8');await scan.press('Enter');await page.getByRole('status').filter({hasText:'定数包已消耗'}).waitFor();
 if(writes!==1||reviews!==1)throw Error('Unexpected duplicate write');console.log('PASS: whole confirmation, no client quantities, wrong code, review only, lost response/reload/query, consumed code rejected, 360px layout');
}
