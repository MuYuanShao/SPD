async(page)=>{
 await page.context().unroute('**/api/**');
 const base={id:11,requisitionId:20003,documentNo:'SL-RECOVERY-ORDER',status:'approved',kind:'PICK_PACKAGE',deptId:2,deptName:'测试科室',sourceWarehouseId:3,sourceWarehouseName:'中心库',warehouseId:4,warehouseName:'科室库',productId:501,productCode:'P0',productName:'测试物料',specModel:'标准型',unit:'个',quantity:2,pickedQuantity:0,remainingQuantity:2,packageQuantity:2,packageUnit:'包'};
 const unit={type:'package',id:701,code:'PK701',status:'available',quantity:2};let receipt=null,writes=0,ambiguous=false;
 function items(){return ambiguous?[{...base,id:21,kind:'PICK_LOOSE',quantity:4,remainingQuantity:4},{...base,id:22,kind:'PICK_LOOSE',quantity:6,remainingQuantity:6}]:receipt?[]:[base]}
 function order(){return {id:20003,documentNo:base.documentNo,status:items().length?'approved':'picked',deptId:2,deptName:'测试科室',sourceWarehouseId:3,sourceWarehouseName:'中心库',warehouseId:4,warehouseName:'科室库',pendingLineCount:items().length}}
 await page.context().route('**/api/**',async route=>{const req=route.request(),path=req.url().replace(/^https?:\/\/[^/]+/,'').split('?')[0],body=req.method()==='POST'?req.postDataJSON():null;let data;
  if(path.endsWith('/auth/login'))data={token:'order-recovery-fixture'};
  else if(path.endsWith('/mobile/context'))data={serverId:'order-recovery-fixture',userId:90003,username:'订单恢复测试',capabilities:['PICK_PACKAGE','PICK_LOOSE'],warehouses:[{deptId:2,deptName:'测试科室',warehouseId:4,warehouseName:'科室库'}]};
  else if(path.endsWith('/picking/orders'))data={rows:items().length?[order()]:[],total:items().length?1:0,page:1,size:20};
  else if(path.endsWith('/orders/20003'))data={order:order(),items:items()};
  else if(path.endsWith('/orders/20003/scans/resolve'))data={matches:items().map(task=>({task,unit:ambiguous?{type:'product',id:501,code:'P0',status:'available',quantity:1}:unit}))};
  else if(path.endsWith('/picking/operations/validate'))data={reviewHash:'order-review',expiresAt:new Date(Date.now()+300000).toISOString(),summary:{task:base,units:[unit],quantity:2,availableQuantity:null}};
  else if(path.endsWith('/picking/operations')){writes++;receipt={operationId:body.operationId,kind:body.kind,taskId:body.taskId,documentNo:'DELIVERY-ONCE',status:'succeeded',userId:90003,deviceId:body.deviceId,completedAt:new Date().toISOString(),message:'成功',remainingQuantity:0};await route.fulfill({status:503,json:{code:503,message:'模拟提交成功但响应丢失'}});return;}
  else if(path.includes('/mobile/operations/'))data=receipt;
  else throw Error('Unexpected recovery request '+path);
  await route.fulfill({json:{code:0,data,message:'fixture'}});
 });
 async function login(){await page.getByLabel('账号',{exact:true}).fill('fixture');await page.getByLabel('密码',{exact:true}).fill('fixture');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('button',{name:'登录',exact:true}).waitFor({state:'detached'});}
 await page.setViewportSize({width:390,height:800});await page.goto('http://127.0.0.1:1831/');await page.evaluate(()=>{for(const key of Object.keys(sessionStorage))if(key.startsWith('spd-dev-signing:')&&key.includes('order-recovery-fixture'))sessionStorage.removeItem(key)});await login();await page.getByRole('button',{name:/^拣选/}).click();await page.getByRole('button',{name:'进入申领单'}).click();const scan=page.getByLabel('扫描本单商品 / 包码 / 唯一码',{exact:true});await scan.fill('PK701');await scan.press('Enter');await page.getByRole('button',{name:'校验本次拣选'}).click();await page.getByRole('button',{name:'确认本次拣选'}).click();await page.getByRole('button',{name:'查询原拣选结果'}).waitFor();
 await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('button',{name:/^待处理/}).click();await page.locator('.pending-row').click();await page.getByRole('button',{name:'查询原拣选结果'}).waitFor();
 await page.reload();await login();await page.getByRole('button',{name:'查询原拣选结果'}).click();await page.getByText('本次拣选成功',{exact:true}).waitFor();await page.getByRole('button',{name:'完成并继续本单拣选'}).click();await page.getByText('本单拣选已完成',{exact:true}).waitFor();if(writes!==1)throw Error('Duplicate write during order recovery');
 ambiguous=true;await page.getByRole('button',{name:'刷新本单'}).click();await scan.fill('P0');await scan.press('Enter');await page.getByText('同一码对应本单多条明细，请选择本次拣选项',{exact:true}).waitFor();if(await page.locator('.scan-choices button').count()!==2)throw Error('Ambiguous code did not present choices');if(await page.getByRole('textbox',{name:'本次数量（个）'}).count())throw Error('Ambiguous code silently selected a line');
 await page.locator('.scan-choices button').filter({hasText:'明细 #22'}).click();await page.getByText('#22 · 散货',{exact:true}).waitFor();if(writes!==1)throw Error('Read-only scan changed stock');console.log('PASS: lost response/reload keeps parent order, one receipt, completed order retained, ambiguous scans require explicit item choice');
}
