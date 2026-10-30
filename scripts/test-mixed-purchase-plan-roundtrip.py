"""Mixed plan final approval -> supplier-scoped MCP fulfilment -> SPD stock/consumption, on owned databases."""
import datetime as dt
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import uuid

ROOT=Path(__file__).resolve().parents[1]
PEER=Path(os.environ.get('SPD_MCP_REPO','D:/SPD-MCP-UI'))
os.environ.update(SPD_MCP_SUPPLY_ENABLED='true',APP_PHASE3_SUPPLY_ENABLED='true')
spec=importlib.util.spec_from_file_location('mixed_plan_dual',PEER/'tests/integration/dual_system.py')
h=importlib.util.module_from_spec(spec);sys.modules[spec.name]=h;spec.loader.exec_module(h)

def main():
    source=(PEER/'tests/integration/dual_system.py').read_text(encoding='utf-8')
    setup=source[source.index('def main():'):source.index('    key = secrets.token_hex(4)')]
    begin,end=setup.index('    # Native MCP purchasing:'),setup.index('    cfg =')
    setup=setup[:begin]+setup[end:]+"    globals().update(mcp=mcp, spd=spd, supplier=supplier, source_company=source_company)\n"
    exec(setup,h.__dict__);h.main()
    api,sql,check,wait=h.api,h.sql,h.check,h.wait;mcp,spd=h.mcp,h.spd
    base=f'/integration/instances/{h.INSTANCE}/phase3'
    suffix=uuid.uuid4().hex[:8];low='MIXG_'+suffix;high='MIXD_'+suffix
    suppliers=[tuple(r.split('\t')) for r in sql('SELECT supplier_code,supplier_name FROM supplier WHERE status=1 AND deleted=0 ORDER BY supplier_id LIMIT 2','spd_it').splitlines()]
    check(len(suppliers)==2,'两家院内有效配送商准备完成')
    products={}
    for code,partner,is_high,price in [(low,suppliers[0],False,2.5),(high,suppliers[1],True,100)]:
        p=api(h.MCP_URL,'/products','POST',{'code':code,'name':code,'spec':'混合计划双端验证','unit':'支','unitPrice':price,'category':'高值耗材' if is_high else '低值耗材','manufacturer':h.source_company,'distributor':partner[1],'status':'active'},mcp)
        dispatch=api(h.MCP_URL,f'/integration/instances/{h.INSTANCE}/dispatch','POST',{'productIds':[p['id']],'licensesOnly':False},mcp)
        check(dispatch[0]['state']=='processed',code+'真实下发SPD')
        application=sql(f"SELECT application_no FROM mcp_inbound_product WHERE product_code='{code}'",'spd_it')
        d=api(h.SPD_URL,'/pending-product-applications/'+application,token=spd)
        fields=['applicationType','productCode','productName','specModel','brand','manufacturerName','supplierName','unit','purchasePrice','retailPrice','minPurchaseQty','purchaseUnit','conversionRate','purchasePackageQty','udiCode','registrationNo','registrationExpireDate','productionLicenseNo','businessLicenseNo','volumeBased','centralizedProcurement','domestic','contractCode','firstCategory','secondCategory','thirdCategory','chargeable','tenderSubCode','qualificationAttachmentCount','highValue','coldChain','quotaManaged','keyMonitored','storageCondition']
        body={k:None if d.get(k)=='-' else d.get(k) for k in fields}
        body.update(highValue=is_high,quotaManaged=not is_high,chargeable=is_high,minPurchaseQty=1,conversionRate=1,purchasePackageQty=1,storageCondition='常温')
        api(h.SPD_URL,'/pending-product-applications/'+application+'/update','PUT',body,spd);h.approve_application(spd,application)
        products[code]=p
    def new_plan():
        demand=api(h.SPD_URL,'/purchase-orders/demands','POST',{'items':[{'productCode':low,'quantity':20},{'productCode':high,'quantity':4}],'demandSource':'manual'},spd)['demandNo']
        for action in ['submit','approve']:api(h.SPD_URL,f'/purchase-orders/demands/{demand}/action','PUT',{'action':action},spd)
        r=api(h.SPD_URL,'/purchase-orders/plans/from-demands','POST',{'demandNos':[demand]},spd)
        check(r['createdPlans']==1 and len(r['planNos'])==1,'含高低值及不同配送商的需求只生成一张混合计划')
        return r['planNos'][0]
    plan=new_plan();detail=api(h.SPD_URL,'/purchase-orders/plans/'+plan,token=spd)
    check(len(detail['items'])==2 and detail['canEditSupplier'],'审批前可查看两条配送商明细')
    check(detail['orderNos']==[],'计划草稿尚未生成订单')
    result=api(h.SPD_URL,f'/purchase-orders/plans/{plan}/action','PUT',{'action':'approve','opinion':'最终审批自动按配送商拆单'},spd)
    check(result['status']=='executed' and len(result['orderNos'])==2,'最终审批直接生成两张订单，不调用execute补转单')
    plan_id=sql(f"SELECT plan_id FROM purchase_plan WHERE plan_no='{plan}'",'spd_it')
    check(sql(f"SELECT COUNT(*) FROM purchase_order WHERE source_plan_id={plan_id} AND order_status='approved' AND approve_time IS NOT NULL",'spd_it')=='2','自动订单为可供货已审批状态并保留审批依据')
    orders={}
    for no in result['orderNos']:
        code=sql(f"SELECT p.product_code FROM purchase_order_item i JOIN purchase_order o ON o.purchase_order_id=i.purchase_order_id JOIN product p ON p.product_id=i.product_id WHERE o.order_no='{no}'",'spd_it')
        orders[code]=no
        wait('供应商订单上传 '+no,lambda no=no:h.find_record(mcp,'order',no))
        row=api(h.MCP_URL,f'{base}/purchase-orders?query={no}',token=mcp)['rows'][0]
        check(row['sourcePlanId']==plan_id and row['sourcePlanNo']==plan,code+'订单上传保留共同来源计划身份')
        check(row['items'][0]['productCode']==code and len(row['items'])==1,code+'上传订单只含对应配送商明细')
    repeated=api(h.SPD_URL,f'/purchase-orders/plans/{plan}/action','PUT',{'action':'approve'},spd)
    check(repeated['orderNos']==result['orderNos'],'重复最终审批返回原订单，不重复生成')
    old_execute=api(h.SPD_URL,f'/purchase-orders/plans/{plan}/action','PUT',{'action':'execute'},spd)
    check(old_execute['orderNos']==result['orderNos'],'兼容旧execute入口且不重复生成')
    sql("INSERT INTO sys_role(id,tenant_id,role_code,role_name) VALUES(900070,1,'mixed_supplier','混合计划供应商'); INSERT INTO sys_role_permission(role_id,permission_id) SELECT 900070,id FROM sys_permission WHERE permission_code IN ('integration:phase3:read','integration:supply');",'mcp_it')
    vendors={}
    for index,(code,partner) in enumerate(zip([low,high],suppliers)):
        user='mixed_vendor_'+str(index);uid=900070+index
        sql(f"INSERT INTO sys_user(id,tenant_id,username,password_hash,real_name,status) SELECT {uid},1,'{user}',password_hash,'混合供应商{index}',1 FROM sys_user WHERE username='e2e_admin' AND tenant_id=1; INSERT INTO sys_user_role(user_id,role_id) VALUES({uid},900070);",'mcp_it')
        api(h.MCP_URL,f'{base}/supplier-access','POST',{'username':user,'supplierCode':partner[0]},mcp)
        vendors[code]=api(h.MCP_URL,'/auth/login','POST',{'username':user,'password':h.PASSWORD})['token']
    projection_ids={}
    for code,partner in zip([low,high],suppliers):
        vendor=vendors[code];other=high if code==low else low
        docs=wait(code+'供应商计划投影上传',lambda vendor=vendor:api(h.MCP_URL,f'{base}/purchase-plans?query={plan}',token=vendor)['rows'])
        check(len(docs)==1 and docs[0]['documentNo']==plan and docs[0]['supplierCode']==partner[0],code+'供应商看到同一院内计划号的专属视图')
        projection_ids[code]=docs[0]['sourceId']
        items=api(h.MCP_URL,f"{base}/purchase-plans/{docs[0]['sourceId']}/items?page=1&size=20",token=vendor)
        check(items['total']==1 and items['rows'][0]['productCode']==code,'计划投影不泄漏其他配送商耗材')
        check(items['rows'][0]['priceBasis']=='catalog_at_plan_creation','计划价格快照标明真实来源')
        status,_=h.request(h.MCP_URL,f'{base}/purchase-orders/{orders[other]}/supply-options',token=vendor)
        check(status==404,'跨供应商读取供货选项被拒绝')
    check(projection_ids[low]!=projection_ids[high],'同一计划各配送商使用稳定独立投影身份，互不覆盖')
    status,_=h.request(h.MCP_URL,f"{base}/purchase-plans/{projection_ids[high]}/items",token=vendors[low])
    check(status==404,'跨供应商读取另一计划投影明细被拒绝')
    receipts={}
    for code,qty in [(low,20),(high,4)]:
        vendor=vendors[code];order=orders[code]
        options=api(h.MCP_URL,f'{base}/purchase-orders/{order}/supply-options',token=vendor);line=options['items'][0]
        key=str(uuid.uuid4());body={'supplyNo':'GH-'+code,'warehouseCode':'WH-CENTER','remark':'混合计划自动拆单后的真实供货','items':[{'supplyLineId':str(uuid.uuid4()),'orderLineId':line['lineId'],'productCode':code,'unit':'支','quantity':str(qty),'productionBatchNo':'LOT-'+code,'productionDate':str(h.TODAY),'expireDate':str(h.TODAY+dt.timedelta(days=365)),'udiCode':''}]}
        api(h.MCP_URL,f'{base}/purchase-orders/{order}/supplies','POST',body,vendor,idempotency_key=key)
        def delivered():
            rows=api(h.MCP_URL,f'{base}/purchase-orders/{order}/supplies',token=vendor)['rows']
            return next((r for r in rows if r['messageId']==key and r['state']=='processed'),None)
        row=wait(code+'供货生成待收货',delivered);receiving=row['result']['receivingNo'];receipts[code]=receiving
        check(row['result']['receivingStatus']=='draft','供货先生成草稿，不自动验收入库')
        check(sql(f"SELECT COALESCE(SUM(b.available_qty),0) FROM inventory_balance b JOIN product p ON p.product_id=b.product_id WHERE p.product_code='{code}'",'spd_it') in ['0','0.0000'],'院方验收前库存不增加')
        api(h.SPD_URL,f'/receiving-orders/{receiving}/action','PUT',{'action':'approve'},spd)
        r=api(h.MCP_URL,f'{base}/supplies/{key}/refresh','POST',{},vendor)
        check(r['receivingStatus']=='approved' and r['quantities'][0]['qualifiedQuantity']==str(qty),code+'院方验收并返回真实合格数量')
    # A second mixed plan routes both types to the same distributor through the real detail editor.
    same=new_plan();d=api(h.SPD_URL,'/purchase-orders/plans/'+same,token=spd)
    low_line=next(i for i in d['items'] if i['productCode']==low);high_line=next(i for i in d['items'] if i['productCode']==high)
    api(h.SPD_URL,f"/purchase-orders/plans/{same}/items/{high_line['planItemId']}/supplier",'PUT',{'supplierId':low_line['supplierId']},spd)
    combined=api(h.SPD_URL,f'/purchase-orders/plans/{same}/action','PUT',{'action':'approve'},spd)
    check(len(combined['orderNos'])==1,'同配送商的高低值明细合成一张订单')
    order=combined['orderNos'][0]
    check(sql(f"SELECT COUNT(*) FROM purchase_order_item i JOIN purchase_order o ON o.purchase_order_id=i.purchase_order_id WHERE o.order_no='{order}'",'spd_it')=='2','同配送商订单同时保留高低值两条明细')
    template=api(h.SPD_URL,'/quota-packages/templates','POST',{'productCode':low,'quantity':5,'unit':'支'},spd)['templateCode']
    task=api(h.SPD_URL,'/quota-packages/packing-tasks','POST',{'templateCode':template,'warehouseName':'SPD中心库','packageCount':2},spd)['taskNo']
    labels=api(h.SPD_URL,f'/quota-packages/packing-tasks/{task}/confirm','PUT',token=spd)['labels']
    for label in labels:api(h.SPD_URL,f'/quota-packages/labels/{label}/print','PUT',token=spd)
    wh=next(w for w in api(h.SPD_URL,'/master-data/warehouses?page=1&size=100',token=spd)['rows'] if w['code']=='WH-ORTH')
    old=api(h.SPD_URL,'/master-data/warehouses/WH-ORTH/products',token=spd)
    api(h.SPD_URL,'/master-data/warehouses/WH-ORTH','PUT',{'warehouseCode':wh['code'],'warehouseName':wh['name'],'warehouseType':wh['type'],'campusName':wh['campus'],'deptCode':wh['deptCode'],'deptName':wh['dept'],'participateStats':wh['participateStats']=='参与','statsCategories':None if wh['statsCategories']=='-' else wh['statsCategories'],'receivingEnabled':bool(wh['receivingEnabled']),'status':1,'productCodes':list(dict.fromkeys([i['productCode'] for i in old]+[low,high]))},spd)
    destination=int(sql("SELECT warehouse_id FROM warehouse WHERE warehouse_code='WH-ORTH'",'spd_it'));center=int(sql("SELECT warehouse_id FROM warehouse WHERE warehouse_code='WH-CENTER'",'spd_it'))
    req=api(h.SPD_URL,'/operational-closure/requisitions','POST',{'deptCode':wh['deptCode'],'deptName':wh['dept'],'destinationWarehouseId':destination,'sourceWarehouseId':center,'items':[{'productCode':low,'quantity':10,'requisitionMode':'quota_package','templateCode':template,'packageCount':2},{'productCode':high,'quantity':2,'requisitionMode':'high_value'}]},spd)['requisitionNo']
    api(h.SPD_URL,f'/operational-closure/requisitions/{req}/action','PUT',{'action':'approve'},spd)
    items=[i for i in api(h.SPD_URL,'/operational-closure/picking/requisitions',token=spd)['rows'] if i['requisitionNo']==req]
    lo=next(i for i in items if i['productCode']==low);hi=next(i for i in items if i['productCode']==high)
    units=api(h.SPD_URL,f"/operational-closure/picking/unique-codes?itemId={hi['itemId']}",token=spd)['rows'][:2]
    delivery_low=api(h.SPD_URL,'/operational-closure/picking/confirm','POST',{'requisitionNo':req,'itemId':lo['itemId'],'warehouseName':'SPD中心库','labelNos':labels},spd)['deliveryNo']
    delivery_high=api(h.SPD_URL,'/operational-closure/picking/confirm-high-value','POST',{'itemId':hi['itemId'],'traceCodeIds':[u['traceCodeId'] for u in units]},spd)['deliveryNo']
    check(sql(f"SELECT COUNT(*) FROM inventory_batch_trace_code WHERE trace_code_id IN ({','.join(str(u['traceCodeId']) for u in units)}) AND lifecycle_status='delivery_picked'",'spd_it')=='2','高值扫码独占唯一码，形成配送在途预扣')
    def balance(code,warehouse):return sql(f"SELECT COALESCE(SUM(b.available_qty),0) FROM inventory_balance b JOIN product p ON p.product_id=b.product_id WHERE p.product_code='{code}' AND b.warehouse_id={warehouse}",'spd_it')
    check(balance(high,center)=='4.0000' and balance(high,destination) in ['0','0.0000'],'高值占用不提前正式扣一级库或增二级库')
    for no in [delivery_low,delivery_high]:api(h.SPD_URL,f'/operational-closure/deliveries/{no}/sign','PUT',token=spd)
    check(balance(low,center)=='10.0000' and balance(low,destination)=='10.0000' and balance(high,center)=='2.0000' and balance(high,destination)=='2.0000','签收完成一级到二级库存正式转移')
    api(h.SPD_URL,f'/operational-closure/deliveries/{delivery_high}/sign','PUT',token=spd)
    check(balance(high,destination)=='2.0000','重复签收不重复增库存')
    low_use=api(h.SPD_URL,'/udi-traceability/quota-packages/consume','POST',{'code':labels[0]},spd)
    billing={'externalChargeNo':'HIS-'+high,'sourceSystem':'HIS','uniqueCode':units[0]['uniqueCode'],'productCode':high,'quantity':1,'deptName':wh['dept'],'patientNo':'TEST-'+suffix,'patientNameMasked':'测**'}
    charge=api(h.SPD_URL,'/operational-closure/high-value/billing-callback','POST',billing,spd)
    api(h.SPD_URL,'/operational-closure/high-value/billing-callback','POST',billing,spd)
    check(balance(low,destination)=='5.0000' and balance(high,destination)=='1.0000','低值消耗及高值幂等HIS计费后科室库存正确')
    for code,qty,amount in [(low,5,'12.5000'),(high,1,'100.0000')]:
        settlement=sql(f"SELECT sb.settlement_no FROM settlement_bill sb JOIN settlement_bill_item i ON i.settlement_id=sb.settlement_id JOIN product p ON p.product_id=i.product_id WHERE p.product_code='{code}'",'spd_it')
        check(bool(settlement),code+'生成对应供应商结算')
        docs=wait(code+'结算上传',lambda code=code,settlement=settlement:api(h.MCP_URL,f'{base}/settlements?query={settlement}',token=vendors[code])['rows'])
        check(len(docs)==1 and docs[0]['totalAmount']==amount,code+'供应商接收到正确金额结算')
    print('MIXED_PLAN_RESULT '+json.dumps({'planNo':plan,'orders':orders,'receipts':receipts,'sameSupplierPlan':same,'sameSupplierOrder':order,'requisition':req,'lowConsumption':low_use['consumptionNo'],'highCharge':charge['chargeNo']},ensure_ascii=False),flush=True)

if __name__=='__main__':
    code=0
    try:main()
    except Exception as e:
        code=1;h.emit('混合计划双端全链路',False,str(e).replace(h.PASSWORD,'[redacted]').replace(h.DB_PASSWORD,'[redacted]').replace(h.SECRET,'[redacted]')[:800])
    finally:
        try:
            env=os.environ.copy();env['MYSQL_PWD']=h.DB_PASSWORD
            subprocess.run([str(h.MYSQL_BIN/'mysqladmin.exe'),'--no-defaults','--protocol=TCP','--host=127.0.0.1',f'--port={h.MYSQL_PORT}','--user=root','shutdown'],env=env,capture_output=True,timeout=15,creationflags=h.FLAGS)
        except Exception:pass
        for process in reversed(h.PROCESSES):
            if process.poll() is None:
                process.terminate()
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:process.kill()
        for handle in h.HANDLES:handle.close()
        h.RUN.mkdir(exist_ok=True,parents=True)
        report=h.RUN/'mixed-plan-report.json';report.write_text(json.dumps({'passed':code==0,'checks':h.RESULTS},ensure_ascii=False,indent=2),encoding='utf-8')
        print('Mixed plan report: '+str(report),flush=True)
    raise SystemExit(code)
