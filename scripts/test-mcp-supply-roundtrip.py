"""Real two-application supply acceptance using the owning disposable dual-system harness."""
import copy
import datetime as dt
import importlib.util
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
PEER = Path(os.environ.get("SPD_MCP_REPO", "D:/SPD-MCP-UI"))
os.environ["SPD_MCP_SUPPLY_ENABLED"] = "true"
os.environ["APP_PHASE3_SUPPLY_ENABLED"] = "true"
spec = importlib.util.spec_from_file_location("supply_owned_dual", PEER / "tests/integration/dual_system.py")
h = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = h
spec.loader.exec_module(h)

def main():
    source = (PEER / "tests/integration/dual_system.py").read_text(encoding="utf-8")
    setup = source[source.index("def main():"):source.index("    key = secrets.token_hex(4)")]
    begin, end = setup.index("    # Native MCP purchasing:"), setup.index("    cfg =")
    setup = setup[:begin] + setup[end:]
    setup += "    globals().update(mcp=mcp, spd=spd, supplier=supplier, source_company=source_company)\n"
    exec(setup, h.__dict__)
    h.main()
    api, request, sql, check, wait = h.api, h.request, h.sql, h.check, h.wait
    mcp, spd = h.mcp, h.spd
    base = f"/integration/instances/{h.INSTANCE}/phase3"
    token = secrets.token_hex(4)
    code = "SUPPLY_IT_" + token
    product = api(h.MCP_URL, "/products", "POST", {"code": code, "name": code, "spec": "1ml", "unit": "支", "unitPrice": 1.2345, "category": "低值耗材", "manufacturer": h.source_company, "distributor": h.supplier[1], "status": "active"}, mcp)
    dispatched = api(h.MCP_URL, f"/integration/instances/{h.INSTANCE}/dispatch", "POST", {"productIds": [product["id"]], "licensesOnly": False}, mcp)
    check(dispatched[0]["state"] == "processed", "供货样本商品通过真实下发")
    application = sql(f"SELECT application_no FROM mcp_inbound_product WHERE product_code='{code}';", "spd_it")
    h.approve_application(spd, application)
    demand = api(h.SPD_URL, "/purchase-orders/demands", "POST", {"items": [{"productCode": code, "quantity": 12}], "demandSource": "manual"}, spd)["demandNo"]
    for action in ("submit", "approve"):
        api(h.SPD_URL, f"/purchase-orders/demands/{demand}/action", "PUT", {"action": action}, spd)
    plan = api(h.SPD_URL, "/purchase-orders/plans/from-demands", "POST", {"demandNos": [demand]}, spd)["planNos"][0]
    for _ in range(12):
        if sql(f"SELECT plan_status FROM purchase_plan WHERE plan_no='{plan}';", "spd_it") in ("approved", "executed"): break
        api(h.SPD_URL, f"/purchase-orders/plans/{plan}/action", "PUT", {"action": "approve"}, spd)
    order = api(h.SPD_URL, f"/purchase-orders/plans/{plan}/action", "PUT", {"action": "execute"}, spd)["orderNo"]
    for _ in range(12):
        status = sql(f"SELECT order_status FROM purchase_order WHERE order_no='{order}';", "spd_it")
        if status == "sent": break
        api(h.SPD_URL, f"/purchase-orders/{order}/action", "PUT", {"action": {"draft": "submit", "pending_approval": "approve", "approved": "send"}[status]}, spd)
    wait("医院真实订单上传完成", lambda: h.find_record(mcp, "order", order))
    order_id = int(sql(f"SELECT purchase_order_id FROM purchase_order WHERE order_no='{order}';", "spd_it"))
    product_id = int(sql(f"SELECT product_id FROM product WHERE product_code='{code}';", "spd_it"))
    record = api(h.MCP_URL, f"{base}/purchase-orders?query={order}", token=mcp)["rows"][0]
    check(record["sourcePlanNo"] == plan, "供货入口关联上传医院订单及来源计划，不使用原生订单ID")
    sql("INSERT INTO sys_role(id,tenant_id,role_code,role_name) VALUES(900050,1,'supply_vendor','供货测试'); INSERT INTO sys_role_permission(role_id,permission_id) SELECT 900050,id FROM sys_permission WHERE permission_code IN ('integration:phase3:read','integration:supply'); INSERT INTO sys_user(id,tenant_id,username,password_hash,real_name,status) SELECT 900050,1,'supply_vendor',password_hash,'供货测试',1 FROM sys_user WHERE username='e2e_admin' AND tenant_id=1; INSERT INTO sys_user_role(user_id,role_id) VALUES(900050,900050);", "mcp_it")
    vendor = api(h.MCP_URL, "/auth/login", "POST", {"username": "supply_vendor", "password": h.PASSWORD})["token"]
    check(api(h.MCP_URL, f"{base}/purchase-orders", token=vendor)["total"] == 0, "未绑定供应商看不到医院订单")
    status, _ = request(h.MCP_URL, f"{base}/purchase-orders/{order}/supply-options", token=vendor)
    check(status == 404, "未绑定供应商不能读取供货选项")
    api(h.MCP_URL, f"{base}/supplier-access", "POST", {"username": "supply_vendor", "supplierCode": h.supplier[0]}, mcp)
    options = api(h.MCP_URL, f"{base}/purchase-orders/{order}/supply-options", token=vendor)
    check(options["items"][0]["remainingQuantity"] == "12", "实时读取医院剩余12个基本单位")
    warehouse = next((w["code"] for w in options["warehouses"] if w["code"] == "WH-CENTER"), options["warehouses"][0]["code"])
    def body(quantity, supply_no=None):
        line = options["items"][0]
        return {"supplyNo": supply_no or "GH_IT_" + uuid.uuid4().hex[:16], "warehouseCode": warehouse, "remark": "隔离供货回传", "items": [{"supplyLineId": str(uuid.uuid4()), "orderLineId": line["lineId"], "productCode": line["productCode"], "unit": line["unit"], "quantity": str(quantity), "productionBatchNo": "LOT_" + token, "productionDate": str(h.TODAY), "expireDate": str(h.TODAY + dt.timedelta(days=365)), "udiCode": ""}]}
    def history(): return api(h.MCP_URL, f"{base}/purchase-orders/{order}/supplies?page=1&size=100", token=vendor)["rows"]
    def row(key): return next((r for r in history() if r["messageId"] == key), None)
    def submit(key, payload): return api(h.MCP_URL, f"{base}/purchase-orders/{order}/supplies", "POST", payload, vendor, idempotency_key=key)
    before_events = sql("SELECT COUNT(*) FROM inventory_event;", "spd_it")
    key = str(uuid.uuid4()); payload = body(3)
    other_supplier = sql(f"SELECT supplier_code FROM supplier WHERE supplier_code<>'{h.supplier[0]}' AND status=1 AND deleted=0 LIMIT 1;", "spd_it")
    if other_supplier:
        api(h.MCP_URL, f"{base}/supplier-access", "POST", {"username": "supply_vendor", "supplierCode": h.supplier[0], "remove": True}, mcp)
        api(h.MCP_URL, f"{base}/supplier-access", "POST", {"username": "supply_vendor", "supplierCode": other_supplier}, mcp)
        check(api(h.MCP_URL, f"{base}/purchase-orders?query={order}", token=vendor)["total"] == 0, "绑定其他供应商不能查看本供应商订单")
        status, _ = request(h.MCP_URL, f"{base}/purchase-orders/{order}/supplies", "POST", payload, vendor, idempotency_key=str(uuid.uuid4()))
        check(status == 404, "跨供应商供货提交被拒绝")
        api(h.MCP_URL, f"{base}/supplier-access", "POST", {"username": "supply_vendor", "supplierCode": other_supplier, "remove": True}, mcp)
        api(h.MCP_URL, f"{base}/supplier-access", "POST", {"username": "supply_vendor", "supplierCode": h.supplier[0]}, mcp)
    status, _ = request(h.MCP_URL, f"{base}/purchase-orders", token=vendor, tenant=2)
    check(status == 401, "跨租户读取供货订单被拒绝")
    sql("INSERT INTO sys_role(id,tenant_id,role_code,role_name) VALUES(900051,1,'supply_readonly','供货只读'); INSERT INTO sys_role_permission(role_id,permission_id) SELECT 900051,id FROM sys_permission WHERE permission_code='integration:phase3:read'; INSERT INTO sys_user(id,tenant_id,username,password_hash,real_name,status) SELECT 900051,1,'supply_readonly',password_hash,'供货只读',1 FROM sys_user WHERE username='e2e_admin' AND tenant_id=1; INSERT INTO sys_user_role(user_id,role_id) VALUES(900051,900051);", "mcp_it")
    api(h.MCP_URL, f"{base}/supplier-access", "POST", {"username": "supply_readonly", "supplierCode": h.supplier[0]}, mcp)
    reader = api(h.MCP_URL, "/auth/login", "POST", {"username": "supply_readonly", "password": h.PASSWORD})["token"]
    check(api(h.MCP_URL, f"{base}/purchase-orders?query={order}", token=reader)["total"] == 1, "只读供应商可以查询已授权医院订单")
    status, _ = request(h.MCP_URL, f"{base}/purchase-orders/{order}/supplies", "POST", payload, reader, idempotency_key=str(uuid.uuid4()))
    check(status == 403, "仅有只读权限不能提交供货")
    status, _ = request(h.MCP_URL, f"{base}/purchase-orders/{order}/supplies", "POST", payload, mcp, idempotency_key=str(uuid.uuid4()))
    check(status == 404, "即使管理员也不能代替未绑定供应商提交供货")
    submit(key, payload)
    accepted = wait("MCP回传在SPD生成待收货单", lambda: (r if r and r["state"] == "processed" else None) if (r := row(key)) else None)
    receiving = accepted["result"]["receivingNo"]
    declared = api(h.MCP_URL, f"{base}/supplies/{key}/items?page=1&size=20", token=reader)
    check(declared["total"] == 1 and declared["rows"][0]["quantity"] == "3" and declared["rows"][0]["productionBatchNo"] == "LOT_" + token, "已授权只读用户可查询不可变原供货批次明细")
    check(accepted["result"]["receivingStatus"] == "draft" and sql("SELECT COUNT(*) FROM inventory_event;", "spd_it") == before_events, "回传仅生成草稿，不自动入库")
    check(sql(f"SELECT CONCAT(source_system,'|',external_supply_no) FROM receiving_order WHERE receiving_no='{receiving}';", "spd_it") == "MCP-UI|" + payload["supplyNo"], "SPD待收货显示MCP来源和供货单号")
    submit(key, payload)
    changed = copy.deepcopy(payload); changed["remark"] = "不同内容"
    status, _ = request(h.MCP_URL, f"{base}/purchase-orders/{order}/supplies", "POST", changed, vendor, idempotency_key=key)
    check(status == 409, "同消息不同供货内容冲突")
    check(sql(f"SELECT COUNT(*) FROM receiving_order WHERE purchase_order_id={order_id};", "spd_it") == "1", "重复提交只有一张收货单")
    wire = json.loads(sql(f"SELECT payload FROM spd_supply_outbox WHERE message_id='{key}';", "mcp_it"))
    status, duplicate = request(h.SPD_URL, "/mcp/phase3/supplies", "POST", wire, h.SECRET, idempotency_key=key)
    check(status == 200 and duplicate["receivingNo"] == receiving, "SPD同消息重放返回原收货单")
    status, _ = request(h.SPD_URL, "/mcp/phase3/supplies", "POST", wire, "wrong-token", idempotency_key=key)
    check(status == 401, "错误供货密钥被拒绝")
    for _ in range(12):
        if sql(f"SELECT receiving_status FROM receiving_order WHERE receiving_no='{receiving}';", "spd_it") == "approved": break
        api(h.SPD_URL, f"/receiving-orders/{receiving}/action", "PUT", {"action": "approve", "opinion": "医院收货验收"}, spd)
    refreshed = api(h.MCP_URL, f"{base}/supplies/{key}/refresh", "POST", {}, vendor)
    check(refreshed["receivingStatus"] == "approved" and sql(f"SELECT SUM(available_qty) FROM inventory_balance WHERE product_id={product_id};", "spd_it") == "3.0000", "院方审核后才入库3个，MCP可刷新已验收状态")
    check(refreshed["quantities"][0]["unit"] == "支" and refreshed["quantities"][0]["qualifiedQuantity"] == "3", "验收数量回执携带基本单位及实际合格数量")
    status, duplicate = request(h.SPD_URL, "/mcp/phase3/supplies", "POST", wire, h.SECRET, idempotency_key=key)
    check(status == 200 and duplicate["receivingStatus"] == "approved" and sql(f"SELECT SUM(available_qty) FROM inventory_balance WHERE product_id={product_id};", "spd_it") == "3.0000", "审核后消息重放不重复入库")
    rejected_key = str(uuid.uuid4()); rejected_body = body(10)
    submit(rejected_key, rejected_body)
    wait("超出医院实时余量的供货被拒绝", lambda: row(rejected_key)["state"] == "rejected")
    check(sql(f"SELECT COUNT(*) FROM receiving_order WHERE purchase_order_id={order_id};", "spd_it") == "1", "拒绝供货未创建额外收货单")
    corrected = body(4, rejected_body["supplyNo"]); corrected_key = str(uuid.uuid4()); submit(corrected_key, corrected)
    fixed = wait("校验拒绝后可用原供货单号更正重提", lambda: (r if r and r["state"] == "processed" else None) if (r := row(corrected_key)) else None)
    sql(f"UPDATE spd_supply_outbox SET state='failed',result=NULL,lease_id=NULL,lease_until=NULL WHERE message_id='{corrected_key}';", "mcp_it")
    api(h.MCP_URL, f"{base}/supplies/{corrected_key}/retry", "POST", {}, vendor)
    wait("模拟响应丢失后按原消息恢复回执", lambda: row(corrected_key)["state"] == "processed")
    check(row(corrected_key)["result"]["receivingNo"] == fixed["result"]["receivingNo"] and sql(f"SELECT COUNT(*) FROM receiving_order WHERE purchase_order_id={order_id};", "spd_it") == "2", "丢失响应重试不会重复建单")
    check(sql(f"SELECT SUM(available_qty) FROM inventory_balance WHERE product_id={product_id};", "spd_it") == "3.0000", "新增4个待收货仍未自动入库")
    check(sql("SELECT COUNT(*) FROM sys_permission WHERE permission_code LIKE 'deliveryOrders:%' OR permission_code LIKE 'shipmentOrders:%';", "mcp_it") == "0", "新供货链路不恢复已移除的旧物流权限")
    print("SUPPLY_RESULT " + json.dumps({"orderNo": order, "planNo": plan, "receivingNo": receiving, "supplier": h.supplier[0]}, ensure_ascii=False), flush=True)

if __name__ == "__main__":
    code = 0
    try:
        main()
    except Exception as error:
        code = 1
        h.emit("供货闭环联调", False, str(error).replace(h.PASSWORD, "[redacted]").replace(h.DB_PASSWORD, "[redacted]").replace(h.SECRET, "[redacted]")[:800])
    finally:
        try:
            env = os.environ.copy(); env["MYSQL_PWD"] = h.DB_PASSWORD
            subprocess.run([str(h.MYSQL_BIN / "mysqladmin.exe"), "--no-defaults", "--protocol=TCP", "--host=127.0.0.1", f"--port={h.MYSQL_PORT}", "--user=root", "shutdown"], env=env, capture_output=True, timeout=15, creationflags=h.FLAGS)
        except Exception:
            pass
        for process in reversed(h.PROCESSES):
            if process.poll() is None:
                process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill()
        for handle in h.HANDLES: handle.close()
        h.RUN.mkdir(parents=True, exist_ok=True)
        report = h.RUN / "supply-roundtrip-report.json"
        report.write_text(json.dumps({"passed": code == 0, "checks": h.RESULTS}, ensure_ascii=False, indent=2), encoding="utf-8")
        print("Supply report: " + str(report), flush=True)
    raise SystemExit(code)
