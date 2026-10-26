async (page) => {
  await page.context().unroute('**/api/**');
  const kinds = ['PICK_LOOSE', 'PICK_PACKAGE', 'PICK_HIGH_VALUE'];
  const lines = kinds.map((kind, n) => ({id:101+n,requisitionId:1001+n,documentNo:'UI-SL-'+(101+n),status:'approved',kind,
    deptId:2,deptName:'测试科室',sourceWarehouseId:3,sourceWarehouseName:'测试中心库',warehouseId:4,
    warehouseName:'测试科室库',productId:501+n,productCode:'P'+n,productName:['散货验收','定数包验收','高值验收'][n],
    unit:'个',quantity:4,pickedQuantity:0,remainingQuantity:4,packageQuantity:kind==='PICK_PACKAGE'?2:null,packageUnit:kind==='PICK_PACKAGE'?'包':null}));
  const physical = new Map([
    [101,{type:'product',id:501,code:'P0',status:'available',quantity:1}],
    [102,{type:'package',id:701,code:'PK701',status:'available',quantity:2}],
    [103,{type:'trace',id:901,code:'U901',status:'in_stock',quantity:1}]
  ]);
  const receipts = new Map();const writes=[];let reviews=0;
  const order=task=>({id:task.requisitionId,documentNo:task.documentNo,status:task.remainingQuantity?(task.pickedQuantity?'partial_picked':'approved'):'picked',deptId:task.deptId,deptName:task.deptName,sourceWarehouseId:task.sourceWarehouseId,sourceWarehouseName:task.sourceWarehouseName,warehouseId:task.warehouseId,warehouseName:task.warehouseName,pendingLineCount:task.remainingQuantity?1:0});
  await page.context().route('**/api/**',async route=>{
    const req=route.request();const path=req.url().replace(/^https?:\/\/[^/]+/,'').split('?')[0];
    const body=req.method()==='POST'?req.postDataJSON():null;let data;
    if(path.endsWith('/auth/login'))data={token:'ui-picking-token'};
    else if(path.endsWith('/mobile/context'))data={serverId:'picking-ui-test',userId:1,username:'拣选界面验收',capabilities:['SIGN_DELIVERY',...kinds],warehouses:[{deptId:2,deptName:'测试科室',warehouseId:4,warehouseName:'测试科室库'}]};
    else if(path.endsWith('/mobile/picking/orders')){const rows=lines.filter(t=>t.remainingQuantity>0).map(order);data={rows,total:rows.length,page:1,size:20};}
    else if(/^\/api\/mobile\/picking\/orders\/\d+$/.test(path)){const task=lines.find(t=>t.requisitionId===Number(path.split('/').pop()));data={order:order(task),items:task.remainingQuantity?[{...task}]:[]};}
    else if(path.endsWith('/mobile/picking/tasks'))data={rows:lines.filter(t=>t.remainingQuantity>0),total:lines.filter(t=>t.remainingQuantity>0).length,page:1,size:20};
    else if(path.includes('/mobile/picking/tasks/'))data=lines.find(t=>t.id===Number(path.split('/').pop()));
    else if(path.endsWith('/mobile/picking/scans/resolve')){
      const item=physical.get(body.taskId);
      if(item.code!==body.rawCode){await route.fulfill({status:400,json:{code:400,message:'物料码不匹配'}});return;}
      data=item;
    } else if(path.endsWith('/mobile/picking/operations/validate')){
      reviews++;const task=lines.find(t=>t.id===body.taskId);const unit=physical.get(body.taskId);
      if(body.sourceWarehouseId!==3||body.deptId!==2||body.warehouseId!==4)throw Error('Wrong work context');
      if(body.kind==='PICK_LOOSE' && (body.rawCode!=='P0'||body.quantity!=='2'))throw Error('Loose quantity changed');
      if(body.kind!=='PICK_LOOSE' && (body.quantity!==null||body.rawCode!==null))throw Error('Client declared physical quantity');
      if(body.kind==='PICK_PACKAGE'&&JSON.stringify(body.packageIds)!=='[701]')throw Error('Package IDs mismatch');
      if(body.kind==='PICK_HIGH_VALUE'&&JSON.stringify(body.traceCodeIds)!=='[901]')throw Error('Trace IDs mismatch');
      data={reviewHash:'pick-review',expiresAt:new Date(Date.now()+300000).toISOString(),summary:{task:{...task},units:[unit],quantity:body.kind==='PICK_LOOSE'?2:unit.quantity,availableQuantity:body.kind==='PICK_LOOSE'?6:undefined}};
    } else if(path.endsWith('/mobile/picking/operations')){
      if(!body.reviewHash)throw Error('No review');writes.push(body);
      const task=lines.find(t=>t.id===body.taskId);task.pickedQuantity+=body.kind==='PICK_LOOSE'?2:physical.get(body.taskId).quantity;task.remainingQuantity=task.quantity-task.pickedQuantity;
      data={operationId:body.operationId,kind:body.kind,taskId:body.taskId,documentNo:'UI-PS-'+body.taskId,status:'succeeded',userId:1,deviceId:body.deviceId,completedAt:new Date().toISOString(),message:'本次拣选成功，已生成配送单',remainingQuantity:task.remainingQuantity};
      receipts.set(body.operationId,data);
      if(body.kind==='PICK_PACKAGE'){await route.fulfill({status:503,json:{code:503,message:'模拟响应丢失'}});return;}
    } else if(path.includes('/mobile/operations/'))data=receipts.get(path.split('/').pop());
    else if(path.endsWith('/mobile/tasks'))data={rows:[],total:0,page:1,size:20};
    else throw Error('Unexpected API '+path);
    if(!data)throw Error('Missing fixture '+path);await route.fulfill({json:{code:0,message:'success',data}});
  });
  async function login(){await page.getByLabel('账号',{exact:true}).fill('ui-picking');await page.getByLabel('密码',{exact:true}).fill('ui-fixture-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('button',{name:'登录',exact:true}).waitFor({state:'detached'});}
  await page.setViewportSize({width:360,height:800});await page.goto('http://127.0.0.1:1831/');
  await page.evaluate(()=>{for(const key of Object.keys(sessionStorage))if(key.startsWith('spd-dev-signing:'))sessionStorage.removeItem(key);});
  await login();await page.getByRole('button',{name:/^拣选/}).click();
  await page.getByRole('combobox',{name:'科室与库房'}).selectOption('4');
  for(let n=0;n<3;n++){
    if(n>0)await page.getByRole('button',{name:'返回单据列表',exact:true}).click();
    await page.locator('article.order-task').filter({hasText:lines[n].documentNo}).getByRole('button',{name:'进入申领单'}).click();
    await page.locator('article.product-task').getByRole('button',{name:'扫描此明细'}).click();
    const input=page.getByLabel('PDA 扫码 / 手工输入',{exact:true});await input.fill('WRONG');await input.press('Enter');
    await page.getByRole('status').filter({hasText:'物料码不匹配'}).waitFor();
    await input.fill(physical.get(lines[n].id).code);await input.press('Enter');
    await page.getByRole('status').filter({hasText:'物料核对成功'}).waitFor();
    if(n===0)await page.getByRole('textbox',{name:'本次数量（个）'}).fill('2');
    else {await input.fill(physical.get(lines[n].id).code);await input.press('Enter');await page.getByRole('status').filter({hasText:'未重复累计'}).waitFor();}
    await page.getByRole('button',{name:'校验本次拣选'}).click();await page.getByRole('button',{name:'确认本次拣选'}).waitFor();
    if(n>0&&await page.getByText('校验时可用散货：',{exact:false}).count()!==0)throw Error('Physical-code picking showed loose inventory');
    if(writes.length!==n)throw Error('Review wrote business data');
    await page.getByRole('button',{name:'确认本次拣选'}).click();
    if(n===1){await page.getByRole('button',{name:'查询原拣选结果'}).waitFor();await page.reload();await login();await page.getByRole('button',{name:'查询原拣选结果'}).click();}
    await page.getByText('本次拣选成功',{exact:true}).waitFor();
    await page.getByText('配送单号：UI-PS-'+lines[n].id,{exact:true}).waitFor();
    if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Horizontal overflow');
    await page.screenshot({path:'D:/SpdApp/spd-implementation/mobile/output/playwright/picking-'+n+'.png',fullPage:true});
    await page.getByRole('button',{name:'完成并继续本单拣选'}).click();
  }
  if(writes.length!==3||reviews!==3)throw Error('Duplicate operation or review');
  console.log('PASS: three picking modes, whole loose quantities, wrong/repeated codes, explicit review, partial refresh, timeout/reload recovery, 360px layout');
}
