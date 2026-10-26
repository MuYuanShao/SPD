package com.hospital.spd.supplychain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Proves mobile HTTP authentication, transactional receipts and desktop conflicts in an owned MySQL database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "spd.mobile.enabled=true", "spd.mobile.server-id=mobile-isolated-test",
        "spd.cors.allowed-origins[0]=http://127.0.0.1:1831"})
@EnabledIfEnvironmentVariable(named = "SPD_MYSQL_INTEGRATION_TESTS", matches = "true")
class MobileMysqlAcceptanceTest {
    static IsolatedMysqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        database = new IsolatedMysqlDatabase(); database.register(registry);
    }
    @AfterAll static void cleanup() { if (database != null) database.close(); }
    @Autowired JdbcTemplate jdbc;
    @Autowired OperationalDeliveryModule delivery;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired com.hospital.spd.specialty.service.QuotaPackageTraceFlowService packageTrace;
    @Autowired InventoryMovementService inventoryMovement;
    @LocalServerPort int port;
    long dept, source, target, product, user, role, deliveryId;
    String token, no;
    Map<String, Object> operation;
    final HttpClient http = HttpClient.newBuilder().proxy(new java.net.ProxySelector() {
        public List<java.net.Proxy> select(URI uri) { return List.of(java.net.Proxy.NO_PROXY); }
        public void connectFailed(URI uri, java.net.SocketAddress address, java.io.IOException ex) {}
    }).build();

    @BeforeEach void fixture() throws Exception {
        String code = "MA-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO sys_dept (dept_code, dept_name) VALUES (?, ?)", code, code);
        dept = id("SELECT dept_id FROM sys_dept WHERE dept_code = ?", code);
        jdbc.update("INSERT INTO product_category (category_code, category_name, level) VALUES (?, ?, 1)", code, code);
        long category = id("SELECT category_id FROM product_category WHERE category_code = ?", code);
        jdbc.update("INSERT INTO product (product_code, product_name, spec_model, category_id, unit) VALUES (?, ?, 'test', ?, '个')", code, code, category);
        product = id("SELECT product_id FROM product WHERE product_code = ?", code);
        source = warehouse(code + "S", null, "中心库"); target = warehouse(code + "T", dept, "科室库");
        jdbc.update("INSERT INTO inventory_batch (system_batch_no, product_id, batch_unit_price) VALUES (?, ?, 10)", code, product);
        long batch = id("SELECT batch_id FROM inventory_batch WHERE system_batch_no = ?", code);
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, product_id, batch_id, available_qty) VALUES (?, ?, ?, 10)", source, product, batch);
        jdbc.update("INSERT INTO department_requisition (requisition_no, dept_id, warehouse_id, source_warehouse_id, requisition_type, status, applicant_id) VALUES (?, ?, ?, ?, 'normal', 'approved', 1)", code, dept, target, source);
        long req = id("SELECT requisition_id FROM department_requisition WHERE requisition_no = ?", code);
        jdbc.update("INSERT INTO department_requisition_item (requisition_id, product_id, quantity, unit, item_type) VALUES (?, ?, 4, '个', 'loose')", req, product);
        long item = id("SELECT item_id FROM department_requisition_item WHERE requisition_id = ?", req);
        no = (String) delivery.confirmLoosePicking(Map.of("requisitionNo", code, "itemId", item, "warehouseName", code + "S", "quantity", 4)).get("deliveryNo");
        deliveryId = id("SELECT delivery_id FROM spd_delivery_order WHERE delivery_no = ?", no);
        String password = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sys_user (username, password, real_name, dept_id) VALUES (?, ?, ?, ?)", code, encoder.encode(password), code, dept);
        user = id("SELECT user_id FROM sys_user WHERE username = ?", code);
        jdbc.update("INSERT INTO sys_role (role_code, role_name, data_scope) VALUES (?, ?, 2)", code, code);
        role = id("SELECT role_id FROM sys_role WHERE role_code = ?", code);
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", user, role);
        jdbc.update("INSERT INTO sys_role_perm (role_id, perm_id) SELECT ?, perm_id FROM sys_permission WHERE perm_code = 'picking-delivery:write' AND deleted = 0", role);
        jdbc.update("INSERT INTO sys_role_perm (role_id, perm_id) SELECT ?, perm_id FROM sys_permission WHERE perm_code = 'department-requisition:pick' AND deleted = 0", role);
        jdbc.update("INSERT INTO sys_role_perm (role_id, perm_id) SELECT ?, perm_id FROM sys_permission WHERE perm_code = 'department-consumption:write' AND deleted = 0", role);
        JsonNode login = call("POST", "/auth/login", Map.of("username", code, "password", password));
        assertThat(login.path("code").asInt()).isZero(); token = login.path("data").path("token").asText();
        operation = new LinkedHashMap<>(Map.of("operationId", UUID.randomUUID().toString(), "deviceId", "acceptance-device",
                "kind", "SIGN_DELIVERY", "taskId", deliveryId, "deptId", dept, "warehouseId", target,
                "packageIds", List.of(), "traceCodeIds", List.of(), "looseConfirmed", true));
    }

    @Test void signingDetailsExposeOnlyTheirOwnAllocatedBatches() throws Exception {
        long batch=id("SELECT batch_id FROM spd_delivery_batch WHERE delivery_id=? LIMIT 1",deliveryId);
        jdbc.update("UPDATE inventory_batch SET production_batch_no='SIGN-BATCH',production_date='2026-09-01',expire_date='2029-08-31' WHERE batch_id=?",batch);
        String code=jdbc.queryForObject("SELECT product_code FROM product WHERE product_id=?",String.class,product);
        jdbc.update("INSERT INTO inventory_batch (system_batch_no,product_id,batch_unit_price,production_batch_no) VALUES (?, ?, 10, 'UNRELATED')",code+"-OTHER",product);
        var data=call("GET","/mobile/tasks/SIGN_DELIVERY/"+deliveryId+"?deptId="+dept+"&warehouseId="+target,null).path("data");
        assertThat(data.path("task").path("requisitionNo").asText()).isEqualTo(code);
        assertThat(data.path("task").path("specModel").asText()).isEqualTo("test");
        assertThat(data.path("task").path("createdAt").asText()).isNotBlank();
        assertThat(data.path("batches").size()).isEqualTo(1);
        assertThat(data.path("batches").get(0).path("batchId").asLong()).isEqualTo(batch);
        assertThat(data.path("batches").get(0).path("productionBatchNo").asText()).isEqualTo("SIGN-BATCH");
        assertThat(data.path("batches").get(0).path("expireDate").asText()).isEqualTo("2029-08-31");
        assertThat(data.path("batches").get(0).path("quantity").decimalValue()).isEqualByComparingTo("4");
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isZero();
    }

    @Test void loginValidateSubmitReplayAndDatabaseReadback() throws Exception {
        assertThat(call("GET", "/auth/me", null).path("data").path("userId").asLong()).isEqualTo(user);
        assertThat(call("GET", "/mobile/context", null).path("data").path("capabilities").toString()).contains("SIGN_DELIVERY");
        assertThat(call("GET", "/mobile/tasks?deptId=" + dept + "&warehouseId=" + target, null).path("data").path("total").asLong()).isEqualTo(1);
        review();
        JsonNode first = call("POST", "/mobile/operations", operation);
        assertThat(first.path("code").asInt()).isZero();
        assertThat(call("POST", "/mobile/operations", operation).path("data")).isEqualTo(first.path("data"));
        assertThat(call("GET", "/mobile/operations/" + operation.get("operationId"), null).path("data")).isEqualTo(first.path("data"));
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id = ? AND operation_id = ? AND status = 'succeeded'", user, operation.get("operationId"))).isEqualTo(1);
        assertThat(stock(source)).isEqualByComparingTo("6"); assertThat(stock(target)).isEqualByComparingTo("4");
        assertThat(id("SELECT COUNT(*) FROM inventory_event WHERE source_biz_type = 'spd_delivery_order' AND source_biz_id = ? AND event_type = 'delivery_sign_in'", deliveryId)).isEqualTo(1);
        operation.put("deviceId", "changed-device");
        assertThat(call("POST", "/mobile/operations", operation).path("code").asInt()).isEqualTo(409);
    }

    @Test void desktopWinsAfterReviewAndMobileRollsBackReceipt() throws Exception {
        review(); delivery.signDelivery(no);
        assertThat(call("POST", "/mobile/operations", operation).path("code").asInt()).isEqualTo(409);
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id = ?", user)).isZero();
        assertThat(stock(target)).isEqualByComparingTo("4");
    }

    @Test void concurrentRetriesReturnOneReceiptAndOneInventoryMovement() throws Exception {
        review(); ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<JsonNode> task = () -> { start.await(); return call("POST", "/mobile/operations", operation); };
        try {
            Future<JsonNode> a = pool.submit(task), b = pool.submit(task); start.countDown();
            JsonNode first = a.get(30, TimeUnit.SECONDS), second = b.get(30, TimeUnit.SECONDS);
            assertThat(first.path("code").asInt()).isZero(); assertThat(second.path("code").asInt()).isZero();
            assertThat(first.path("data")).isEqualTo(second.path("data"));
            assertThat(stock(target)).isEqualByComparingTo("4");
            assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id = ?", user)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void missingConfirmationAndRevokedPermissionNeverWrite() throws Exception {
        operation.put("looseConfirmed", false);
        assertThat(call("POST", "/mobile/operations/validate", operation).path("code").asInt()).isEqualTo(409);
        operation.put("looseConfirmed", true); review();
        jdbc.update("DELETE FROM sys_role_perm WHERE role_id = ?", role);
        assertThat(call("POST", "/mobile/operations", operation).path("code").asInt()).isEqualTo(403);
        assertThat(stock(target)).isZero();
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id = ?", user)).isZero();
    }

    @Test void permissionRepairRestoresOnlyMissingDefinitionWithoutGrantingRoles() {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status -> {
            long permission = id("SELECT perm_id FROM sys_permission WHERE perm_code = 'picking-delivery:write'");
            jdbc.update("DELETE FROM sys_role_perm WHERE perm_id = ?", permission);
            jdbc.update("DELETE FROM sys_permission WHERE perm_id = ?", permission);
            var script = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                    new org.springframework.core.io.ClassPathResource("db/migration/V81__ensure_mobile_signing_permission.sql"));
            script.execute(jdbc.getDataSource());
            script.execute(jdbc.getDataSource());
            assertThat(id("SELECT COUNT(*) FROM sys_permission WHERE perm_code = 'picking-delivery:write' AND status = 1 AND deleted = 0")).isEqualTo(1);
            assertThat(id("SELECT COUNT(*) FROM sys_role_perm rp JOIN sys_permission p ON p.perm_id = rp.perm_id WHERE p.perm_code = 'picking-delivery:write'")).isZero();
            jdbc.update("UPDATE sys_permission SET status = 0 WHERE perm_code = 'picking-delivery:write'");
            script.execute(jdbc.getDataSource());
            assertThat(id("SELECT status FROM sys_permission WHERE perm_code = 'picking-delivery:write'")).isZero();
            status.setRollbackOnly();
        });
    }

    private void review() throws Exception {
        JsonNode review = call("POST", "/mobile/operations/validate", operation);
        assertThat(review.path("code").asInt()).as(review.toString()).isZero();
        operation.put("reviewHash", review.path("data").path("reviewHash").asText());
    }
    @Test void loosePickingIsPartialIdempotentAndReturnsRealRemaining() throws Exception {
        var op = pickOperation("PICK_LOOSE");
        JsonNode tasks = call("GET", "/mobile/picking/tasks?deptId="+dept+"&warehouseId="+target, null);
        assertThat(tasks.path("code").asInt()).isZero();
        assertThat(tasks.path("data").path("total").asInt()).isEqualTo(1);
        pickReview(op);
        JsonNode first = call("POST", "/mobile/picking/operations", op);
        assertThat(first.path("code").asInt()).as(first.toString()).isZero();
        assertThat(first.path("data").path("remainingQuantity").decimalValue()).isEqualByComparingTo("2");
        assertThat(call("POST", "/mobile/picking/operations", op).path("data")).isEqualTo(first.path("data"));
        assertThat(call("GET", "/mobile/operations/"+op.get("operationId"), null).path("data")).isEqualTo(first.path("data"));
        assertThat(stock(source)).isEqualByComparingTo("4");assertThat(stock(target)).isZero();
        op.put("operationId",UUID.randomUUID().toString());pickReview(op);
        JsonNode second = call("POST", "/mobile/picking/operations", op);
        assertThat(second.path("code").asInt()).isZero();
        assertThat(second.path("data").path("remainingQuantity").asInt()).isZero();
        assertThat(stock(source)).isEqualByComparingTo("2");
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=? AND kind='PICK_LOOSE'",user)).isEqualTo(2);
    }
    @Test void packagePickingUsesActualLabelsAndBaseUnitQuantity() throws Exception {
        var op = pickOperation("PICK_PACKAGE");List<Long> labels = pickPackages(op);
        op.put("packageIds",List.of(labels.get(0)));
        JsonNode summary=pickReview(op);
        assertThat(summary.path("data").path("summary").path("quantity").asInt()).isEqualTo(2);
        JsonNode result=call("POST","/mobile/picking/operations",op);
        assertThat(result.path("code").asInt()).as(result.toString()).isZero();
        assertThat(result.path("data").path("remainingQuantity").asInt()).isEqualTo(2);
        assertThat(stock(source)).isEqualByComparingTo("2");assertThat(stock(target)).isZero();
        assertThat(id("SELECT COUNT(*) FROM quota_package_label WHERE label_id=? AND status='delivered'",labels.get(0))).isEqualTo(1);
        assertThat(call("POST","/mobile/picking/operations",op).path("data")).isEqualTo(result.path("data"));
    }
    @Test void highValuePickingBindsCodesAndMovesStockOnlyAtSigning() throws Exception {
        var op=pickOperation("PICK_HIGH_VALUE");op.put("traceCodeIds",pickHighValueCodes());pickReview(op);
        JsonNode result=call("POST","/mobile/picking/operations",op);
        assertThat(result.path("code").asInt()).as(result.toString()).isZero();
        assertThat(stock(source)).isEqualByComparingTo("6");assertThat(stock(target)).isZero();
        String deliveryNo=result.path("data").path("documentNo").asText();
        delivery.signDelivery(deliveryNo);
        assertThat(stock(source)).isEqualByComparingTo("4");assertThat(stock(target)).isEqualByComparingTo("2");
        assertThat(id("SELECT COUNT(*) FROM department_consumption_item WHERE product_id=?",product)).isZero();
    }
    @Test void concurrentPartialPicksWithDifferentIdsCannotOverFulfil() throws Exception {
        var first=pickOperation("PICK_LOOSE");first.put("quantity","3");pickReview(first);
        var second=new LinkedHashMap<>(first);second.put("operationId",UUID.randomUUID().toString());pickReview(second);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<JsonNode> a=pool.submit(()->{start.await();return call("POST","/mobile/picking/operations",first);});
            Future<JsonNode> b=pool.submit(()->{start.await();return call("POST","/mobile/picking/operations",second);});start.countDown();
            List<Integer> codes=List.of(a.get(30,TimeUnit.SECONDS).path("code").asInt(),b.get(30,TimeUnit.SECONDS).path("code").asInt());
            assertThat(codes).containsExactlyInAnyOrder(0,409);assertThat(stock(source)).isEqualByComparingTo("3");
            assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void pickingPermissionCannotBeReplacedBySigningPermission() throws Exception {
        var op=pickOperation("PICK_LOOSE");pickReview(op);
        JsonNode success=call("POST","/mobile/picking/operations",op);assertThat(success.path("code").asInt()).isZero();
        jdbc.update("DELETE rp FROM sys_role_perm rp JOIN sys_permission p ON p.perm_id=rp.perm_id WHERE rp.role_id=? AND p.perm_code='department-requisition:pick'",role);
        assertThat(call("GET","/mobile/operations/"+op.get("operationId"),null).path("code").asInt()).isEqualTo(403);
        assertThat(call("GET","/mobile/picking/tasks?deptId="+dept+"&warehouseId="+target,null).path("code").asInt()).isEqualTo(403);
    }
    @Test void wrongSourceAndWrongProductNeverCreatePickingReceipts() throws Exception {
        var op=pickOperation("PICK_LOOSE");op.put("sourceWarehouseId",target);
        assertThat(call("POST","/mobile/picking/operations/validate",op).path("code").asInt()).isEqualTo(409);
        op.put("sourceWarehouseId",source);op.put("rawCode","WRONG-PRODUCT");
        assertThat(call("POST","/mobile/picking/operations/validate",op).path("code").asInt()).isEqualTo(400);
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isZero();
        assertThat(stock(source)).isEqualByComparingTo("6");
    }
    @Test void repeatedPackageIdentityAndStaleSnapshotAreRejected() throws Exception {
        var op=pickOperation("PICK_PACKAGE");var labels=pickPackages(op);op.put("packageIds",List.of(labels.get(0),labels.get(0)));
        assertThat(call("POST","/mobile/picking/operations/validate",op).path("code").asInt()).isEqualTo(400);
        op.put("packageIds",List.of(labels.get(0)));pickReview(op);
        jdbc.update("UPDATE department_requisition_item SET quantity=3 WHERE item_id=?",op.get("taskId"));
        assertThat(call("POST","/mobile/picking/operations",op).path("code").asInt()).isEqualTo(409);
        assertThat(stock(source)).isEqualByComparingTo("2");assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isZero();
    }
    @Test void selfScopeDoesNotReadAnotherApplicantsPickingLine() throws Exception {
        var op=pickOperation("PICK_LOOSE");
        long req=id("SELECT requisition_id FROM department_requisition_item WHERE item_id=?",op.get("taskId"));
        jdbc.update("UPDATE sys_role SET data_scope=4 WHERE role_id=?",role);
        jdbc.update("UPDATE department_requisition r JOIN department_requisition_item i ON i.requisition_id=r.requisition_id SET r.applicant_id=1 WHERE i.item_id=?",op.get("taskId"));
        JsonNode list=call("GET","/mobile/picking/tasks?deptId="+dept+"&warehouseId="+target,null);
        assertThat(list.path("code").asInt()).isZero();assertThat(list.path("data").path("total").asInt()).isZero();
        assertThat(call("GET","/mobile/picking/tasks/"+op.get("taskId")+"?deptId="+dept+"&warehouseId="+target,null).path("code").asInt()).isEqualTo(403);
        assertThat(call("GET","/mobile/picking/orders?deptId="+dept+"&warehouseId="+target,null).path("data").path("total").asInt()).isZero();
        assertThat(call("GET","/mobile/picking/orders/"+req+"?deptId="+dept+"&warehouseId="+target,null).path("code").asInt()).isEqualTo(403);
        assertThat(call("POST","/mobile/picking/orders/"+req+"/scans/resolve",Map.of("deptId",dept,"warehouseId",target,"rawCode",op.get("rawCode"))).path("code").asInt()).isEqualTo(403);
    }
    private Map<String,Object> pickOperation(String kind) {
        String reqNo="MP-"+UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO department_requisition (requisition_no,dept_id,warehouse_id,source_warehouse_id,requisition_type,status,applicant_id) VALUES(?,?,?,?,'normal','approved',?)",reqNo,dept,target,source,user);
        long req=id("SELECT requisition_id FROM department_requisition WHERE requisition_no=?",reqNo);
        String type=kind.equals("PICK_PACKAGE")?"quota_package":kind.equals("PICK_HIGH_VALUE")?"unique_code":"loose";
        jdbc.update("INSERT INTO department_requisition_item(requisition_id,product_id,quantity,unit,item_type) VALUES(?,?,4,'个',?)",req,product,type);
        long item=id("SELECT item_id FROM department_requisition_item WHERE requisition_id=?",req);
        Map<String,Object> op=new LinkedHashMap<>(Map.of("operationId",UUID.randomUUID().toString(),"deviceId","picking-test","kind",kind,
                "taskId",item,"deptId",dept,"warehouseId",target,"sourceWarehouseId",source,"packageIds",List.of(),"traceCodeIds",List.of()));
        op.put("quantity",kind.equals("PICK_LOOSE")?"2":null);
        op.put("rawCode",kind.equals("PICK_LOOSE")?jdbc.queryForObject("SELECT product_code FROM product WHERE product_id=?",String.class,product):null);
        return op;
    }
    private List<Long> pickPackages(Map<String,Object> op) {
        inventoryMovement.consumeAvailableFifo(source,product,new BigDecimal("4"),"quota_pack_out","quota_packing_task",1L,"测试制包前置扣减");
        String code="MQ-"+UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO quota_package_template(template_code,template_name,version_no,is_current,status) VALUES(?,'测试模板',1,1,1)",code);
        long template=id("SELECT template_id FROM quota_package_template WHERE template_code=?",code);
        jdbc.update("INSERT INTO quota_package_template_item(template_id,product_id,quantity,unit) VALUES(?,?,2,'包')",template,product);
        jdbc.update("UPDATE department_requisition_item SET quota_template_id=?,quota_template_version=1,quota_package_quantity=2,quota_package_unit='包' WHERE item_id=?",template,op.get("taskId"));
        long batch=id("SELECT batch_id FROM inventory_batch WHERE product_id=?",product);List<Long> labels=new ArrayList<>();
        for(int n=0;n<2;n++) {
            String label=code+"-"+n;
            jdbc.update("INSERT INTO quota_package_label(label_no,template_id,warehouse_id,product_id,package_quantity,status) VALUES(?,?,?,?,2,'available')",label,template,source,product);
            long id=id("SELECT label_id FROM quota_package_label WHERE label_no=?",label);labels.add(id);
            jdbc.update("INSERT INTO quota_package_label_source(label_id,batch_id,source_qty,unit_price,warehouse_id) VALUES(?,?,2,10,?)",id,batch,source);
            packageTrace.ensureTrace(id);
        }return labels;
    }
    private List<Long> pickHighValueCodes() {
        jdbc.update("UPDATE product SET is_high_value=1 WHERE product_id=?",product);
        String code=jdbc.queryForObject("SELECT product_code FROM product WHERE product_id=?",String.class,product);
        long batch=id("SELECT batch_id FROM inventory_batch WHERE product_id=?",product);List<Long> ids=new ArrayList<>();
        for(int n=0;n<2;n++) {
            String raw="HU-"+UUID.randomUUID();
            jdbc.update("INSERT INTO udi_trace_code(udi_code,unique_code,trace_scope,product_code,product_name,current_location,current_status,risk_level,last_event_time) VALUES(?,?,'high_value',?,'测试高值','测试库','in_stock','normal',NOW())",raw,raw,code);
            long trace=id("SELECT trace_code_id FROM udi_trace_code WHERE unique_code=?",raw);ids.add(trace);
            jdbc.update("INSERT INTO inventory_batch_trace_code(batch_id,trace_code_id,receiving_item_id,current_warehouse_id,lifecycle_status) VALUES(?,?,1,?,'in_stock')",batch,trace,source);
        }return ids;
    }
    private JsonNode pickReview(Map<String,Object> op) throws Exception {
        JsonNode result=call("POST","/mobile/picking/operations/validate",op);
        assertThat(result.path("code").asInt()).as(result.toString()).isZero();op.put("reviewHash",result.path("data").path("reviewHash").asText());return result;
    }
    @Test void consumePackageWritesStockTraceSettlementAndOneIdempotentReceipt() throws Exception {
        var labels=signedConsumptionPackages();var op=consumptionOperation(labels.get(0));
        JsonNode resolved=call("POST","/mobile/consumption/packages/resolve",Map.of("deptId",dept,"warehouseId",target,"rawCode",op.get("rawCode")));
        assertThat(resolved.path("data").path("pack").path("quantity").asInt()).isEqualTo(2);
        consumptionReview(op);assertThat(stock(target)).isEqualByComparingTo("4");
        JsonNode result=call("POST","/mobile/consumption/operations",op);assertThat(result.path("code").asInt()).as(result.toString()).isZero();
        assertThat(result.path("data").path("status").asText()).isEqualTo("succeeded");
        assertThat(result.path("data").path("quantity").asInt()).isEqualTo(2);assertThat(result.path("data").path("amount").decimalValue()).isEqualByComparingTo("20");
        assertThat(call("POST","/mobile/consumption/operations",op).path("data")).isEqualTo(result.path("data"));
        assertThat(call("GET","/mobile/operations/"+op.get("operationId"),null).path("data")).isEqualTo(result.path("data"));
        assertThat(stock(target)).isEqualByComparingTo("2");
        assertThat(id("SELECT COUNT(*) FROM quota_package_label q JOIN udi_trace_code t ON t.trace_code_id=q.trace_code_id WHERE q.label_id=? AND q.status='consumed' AND t.current_status='consumed'",labels.get(0))).isEqualTo(1);
        assertThat(consumptionEvents()).isEqualTo(1);assertThat(consumptionSettlementItems()).isEqualTo(1);
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=? AND kind='CONSUME_PACKAGE'",user)).isEqualTo(1);
        op.put("rawCode","changed-code");assertThat(call("POST","/mobile/consumption/operations",op).path("code").asInt()).isEqualTo(409);
    }
    @Test void partialConsumptionAndMissingConfirmationAreRejectedWithoutWrites() throws Exception {
        var op=consumptionOperation(signedConsumptionPackages().get(0));
        op.put("quantity",1);assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(400);
        op.remove("quantity");op.put("amount",1);assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(400);
        op.remove("amount");op.put("wholePackageConfirmed",false);assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(400);
        assertThat(stock(target)).isEqualByComparingTo("4");assertThat(consumptionEvents()).isZero();assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isZero();
    }
    @Test void wrongWarehouseAndWrongPackageIdentityCannotConsume() throws Exception {
        var labels=signedConsumptionPackages();var op=consumptionOperation(labels.get(0));
        op.put("taskId",labels.get(1));assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(409);
        op.put("taskId",labels.get(0));op.put("warehouseId",warehouse("OTHER-"+UUID.randomUUID().toString().substring(0,8),dept,"科室库"));
        assertThat(call("POST","/mobile/consumption/operations",op).path("code").asInt()).isEqualTo(400);
        assertThat(stock(target)).isEqualByComparingTo("4");assertThat(consumptionEvents()).isZero();assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isZero();
    }
    @Test void highValueProductAndUnreceivedPackageAreRejected() throws Exception {
        var labels=signedConsumptionPackages();var op=consumptionOperation(labels.get(0));
        jdbc.update("UPDATE product SET is_high_value=1 WHERE product_id=?",product);
        assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(400);
        jdbc.update("UPDATE product SET is_high_value=0 WHERE product_id=?",product);
        jdbc.update("UPDATE quota_package_label SET status='delivered' WHERE label_id=?",labels.get(0));
        assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(409);
        assertThat(stock(target)).isEqualByComparingTo("4");assertThat(consumptionEvents()).isZero();
    }
    @Test void invalidSourceExpiryAndIsolatedTraceBlockConsumption() throws Exception {
        var labels=signedConsumptionPackages();var op=consumptionOperation(labels.get(0));
        jdbc.update("UPDATE inventory_batch SET expire_date=DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY) WHERE product_id=?",product);
        assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(409);
        jdbc.update("UPDATE inventory_batch SET expire_date=NULL WHERE product_id=?",product);
        jdbc.update("UPDATE quota_package_label_source SET source_qty=1 WHERE label_id=?",labels.get(0));
        assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(409);
        jdbc.update("UPDATE quota_package_label_source SET source_qty=2 WHERE label_id=?",labels.get(0));
        jdbc.update("UPDATE udi_trace_code t JOIN quota_package_label q ON q.trace_code_id=t.trace_code_id SET t.current_status='isolated' WHERE q.label_id=?",labels.get(0));
        assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(409);
        assertThat(stock(target)).isEqualByComparingTo("4");assertThat(consumptionEvents()).isZero();
    }
    @Test void insufficientPackageBatchStockCannotBeConsumed() throws Exception {
        var op=consumptionOperation(signedConsumptionPackages().get(0));
        jdbc.update("UPDATE inventory_balance SET available_qty=1 WHERE warehouse_id=? AND product_id=?",target,product);
        assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(409);
        assertThat(stock(target)).isEqualByComparingTo("1");assertThat(consumptionEvents()).isZero();
    }
    @Test void packageChangeAfterReviewRollsBackPendingReceipt() throws Exception {
        var labels=signedConsumptionPackages();var op=consumptionOperation(labels.get(0));consumptionReview(op);
        jdbc.update("UPDATE quota_package_label SET version=version+1 WHERE label_id=?",labels.get(0));
        assertThat(call("POST","/mobile/consumption/operations",op).path("code").asInt()).isEqualTo(409);
        assertThat(stock(target)).isEqualByComparingTo("4");assertThat(consumptionEvents()).isZero();assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isZero();
    }
    @Test void concurrentConsumptionOfSamePackageSucceedsOnce() throws Exception {
        var labels=signedConsumptionPackages();var a=consumptionOperation(labels.get(0));var b=consumptionOperation(labels.get(0));consumptionReview(a);consumptionReview(b);
        assertThat(concurrentConsumption(a,b)).containsExactlyInAnyOrder(0,409);
        assertThat(stock(target)).isEqualByComparingTo("2");assertThat(consumptionEvents()).isEqualTo(1);assertThat(consumptionSettlementItems()).isEqualTo(1);
        assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isEqualTo(1);
    }
    @Test void independentPackagesCanConsumeConcurrentlyWithoutFalseStaleStockConflicts() throws Exception {
        var labels=signedConsumptionPackages();var a=consumptionOperation(labels.get(0));var b=consumptionOperation(labels.get(1));consumptionReview(a);consumptionReview(b);
        assertThat(concurrentConsumption(a,b)).containsExactly(0,0);
        assertThat(stock(target)).isZero();assertThat(consumptionEvents()).isEqualTo(2);assertThat(consumptionSettlementItems()).isEqualTo(2);
    }
    @Test void desktopConsumptionWinsAndMobileDoesNotRepeatIt() throws Exception {
        var op=consumptionOperation(signedConsumptionPackages().get(0));consumptionReview(op);
        packageTrace.consumeByCode(Map.of("code",op.get("rawCode")));
        assertThat(call("POST","/mobile/consumption/operations",op).path("code").asInt()).isEqualTo(409);
        assertThat(stock(target)).isEqualByComparingTo("2");assertThat(consumptionEvents()).isEqualTo(1);assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isZero();
    }
    @Test void consumptionPermissionIsRequiredForOwnReceiptEvenWithSigningPermission() throws Exception {
        var op=consumptionOperation(signedConsumptionPackages().get(0));consumptionReview(op);
        assertThat(call("POST","/mobile/consumption/operations",op).path("data").path("status").asText()).isEqualTo("succeeded");
        jdbc.update("DELETE rp FROM sys_role_perm rp JOIN sys_permission p ON p.perm_id=rp.perm_id WHERE rp.role_id=? AND p.perm_code='department-consumption:write'",role);
        assertThat(call("GET","/mobile/operations/"+op.get("operationId"),null).path("code").asInt()).isEqualTo(403);
        assertThat(call("POST","/mobile/consumption/operations/validate",op).path("code").asInt()).isEqualTo(403);
    }
    @Test void expiredSigningReviewCanRecoverAndCommitTheOriginalId() throws Exception {
        review();operation.put("reviewHash","1."+"0".repeat(64));
        assertThat(call("POST","/mobile/operations",operation).path("code").asInt()).isEqualTo(409);
        assertThat(receiptCount(operation)).isZero();
        recoverReview("/mobile/operations",operation);
        assertThat(receiptCount(operation)).isEqualTo(1);assertThat(stock(target)).isZero();
        assertThat(call("POST","/mobile/operations",operation).path("data").path("status").asText()).isEqualTo("succeeded");
        assertThat(stock(target)).isEqualByComparingTo("4");
        assertThat(call("POST","/mobile/operations/recover",operation).path("data").path("result").path("status").asText()).isEqualTo("succeeded");
        assertThat(stock(target)).isEqualByComparingTo("4");
    }
    @Test void loosePickingRecoveryPreservesQuantityAndOnlyWritesAfterConfirmation() throws Exception {
        var op=pickOperation("PICK_LOOSE");pickReview(op);op.put("reviewHash","1."+"0".repeat(64));
        assertThat(call("POST","/mobile/picking/operations",op).path("code").asInt()).isEqualTo(409);
        recoverReview("/mobile/picking/operations",op);assertThat(stock(source)).isEqualByComparingTo("6");
        assertThat(call("POST","/mobile/picking/operations",op).path("data").path("status").asText()).isEqualTo("succeeded");
        assertThat(stock(source)).isEqualByComparingTo("4");
    }
    @Test void packagePickingCanRecoverWithoutDoubleDeductingPackedStock() throws Exception {
        var op=pickOperation("PICK_PACKAGE");op.put("packageIds",List.of(pickPackages(op).get(0)));pickReview(op);
        recoverReview("/mobile/picking/operations",op);assertThat(stock(source)).isEqualByComparingTo("2");
        assertThat(call("POST","/mobile/picking/operations",op).path("data").path("status").asText()).isEqualTo("succeeded");
        assertThat(stock(source)).isEqualByComparingTo("2");
    }
    @Test void highValuePickingCanRecoverWithoutBillingConsumption() throws Exception {
        var op=pickOperation("PICK_HIGH_VALUE");op.put("traceCodeIds",pickHighValueCodes());pickReview(op);
        recoverReview("/mobile/picking/operations",op);
        assertThat(call("POST","/mobile/picking/operations",op).path("data").path("status").asText()).isEqualTo("succeeded");
        assertThat(stock(source)).isEqualByComparingTo("6");assertThat(consumptionEvents()).isZero();
    }
    @Test void changedReviewKeyCanRecoverConsumptionWithOriginalIdentity() throws Exception {
        var op=consumptionOperation(signedConsumptionPackages().get(0));consumptionReview(op);
        var resolved=call("POST","/mobile/consumption/packages/resolve",Map.of("deptId",dept,"warehouseId",target,"rawCode",op.get("rawCode"))).path("data");
        var oldProcess=new com.hospital.spd.mobile.MobileReviewService(json,"");
        var command=json.convertValue(op,com.hospital.spd.mobile.MobileContracts.ConsumeOperation.class);
        var snapshot=json.treeToValue(resolved,com.hospital.spd.mobile.MobileContracts.ConsumeSnapshot.class);
        op.put("reviewHash",oldProcess.issue(user,command,snapshot).reviewHash());
        assertThat(call("POST","/mobile/consumption/operations",op).path("code").asInt()).isEqualTo(409);
        recoverReview("/mobile/consumption/operations",op);assertThat(stock(target)).isEqualByComparingTo("4");assertThat(consumptionEvents()).isZero();
        var result=call("POST","/mobile/consumption/operations",op).path("data");
        assertThat(result.path("status").asText()).isEqualTo("succeeded");assertThat(result.path("operationId").asText()).isEqualTo(op.get("operationId"));
        assertThat(stock(target)).isEqualByComparingTo("2");assertThat(consumptionEvents()).isEqualTo(1);
    }
    @Test void terminalRecoveryReceiptFencesEvenLateValidConsumptionRequests() throws Exception {
        long label=signedConsumptionPackages().get(0);var op=consumptionOperation(label);consumptionReview(op);
        packageTrace.consumeByCode(Map.of("code",op.get("rawCode")));
        var recovery=call("POST","/mobile/consumption/operations/recover",op);
        assertThat(recovery.path("code").asInt()).isZero();assertThat(recovery.path("data").path("result").path("status").asText()).isEqualTo("rejected");
        // Even if a later workflow restores eligibility, this exact operation has been permanently fenced.
        jdbc.update("UPDATE quota_package_label SET status='signed' WHERE label_id=?",label);
        jdbc.update("UPDATE udi_trace_code SET current_status='signed' WHERE trace_code_id=(SELECT trace_code_id FROM quota_package_label WHERE label_id=?)",label);
        assertThat(call("POST","/mobile/consumption/operations",op).path("data").path("status").asText()).isEqualTo("rejected");
        assertThat(call("GET","/mobile/operations/"+op.get("operationId"),null).path("data").path("status").asText()).isEqualTo("rejected");
        assertThat(stock(target)).isEqualByComparingTo("2");assertThat(consumptionEvents()).isEqualTo(1);
    }
    @Test void recoveryAndOriginalSubmitSerializeToOneConsumption() throws Exception {
        var op=consumptionOperation(signedConsumptionPackages().get(0));consumptionReview(op);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var submit=pool.submit(()->{start.await();return call("POST","/mobile/consumption/operations",op);});
            var recover=pool.submit(()->{start.await();return call("POST","/mobile/consumption/operations/recover",op);});start.countDown();
            assertThat(submit.get(30,TimeUnit.SECONDS).path("data").path("status").asText()).isEqualTo("succeeded");
            assertThat(recover.get(30,TimeUnit.SECONDS).path("code").asInt()).isZero();
        } finally {pool.shutdownNow();}
        assertThat(call("POST","/mobile/consumption/operations",op).path("data").path("status").asText()).isEqualTo("succeeded");
        assertThat(stock(target)).isEqualByComparingTo("2");assertThat(consumptionEvents()).isEqualTo(1);assertThat(receiptCount(op)).isEqualTo(1);
    }
    @Test void competingConsumptionAfterRecoveryCanBeTerminatedWithoutAnotherWrite() throws Exception {
        var original=consumptionOperation(signedConsumptionPackages().get(0));consumptionReview(original);
        var other=new LinkedHashMap<>(original);other.put("operationId",UUID.randomUUID().toString());consumptionReview(other);
        recoverReview("/mobile/consumption/operations",original);
        assertThat(call("POST","/mobile/consumption/operations",other).path("data").path("status").asText()).isEqualTo("succeeded");
        assertThat(call("POST","/mobile/consumption/operations",original).path("code").asInt()).isEqualTo(409);
        assertThat(call("POST","/mobile/consumption/operations/recover",original).path("data").path("result").path("status").asText()).isEqualTo("rejected");
        assertThat(consumptionEvents()).isEqualTo(1);assertThat(stock(target)).isEqualByComparingTo("2");
    }
    @Test void recoveryRejectsChangedPayloadUnderTheSameId() throws Exception {
        var op=consumptionOperation(signedConsumptionPackages().get(0));consumptionReview(op);recoverReview("/mobile/consumption/operations",op);
        var changed=new LinkedHashMap<>(op);changed.put("rawCode","different-code");
        assertThat(call("POST","/mobile/consumption/operations/recover",changed).path("code").asInt()).isEqualTo(409);
        assertThat(call("POST","/mobile/consumption/operations",op).path("data").path("status").asText()).isEqualTo("succeeded");
        assertThat(consumptionEvents()).isEqualTo(1);
    }
    @Test void recoveryRechecksPermissionsAndWarehouseScope() throws Exception {
        var op=consumptionOperation(signedConsumptionPackages().get(0));consumptionReview(op);
        var wrong=new LinkedHashMap<>(op);wrong.put("warehouseId",source);
        assertThat(call("POST","/mobile/consumption/operations/recover",wrong).path("code").asInt()).isEqualTo(400);
        jdbc.update("DELETE rp FROM sys_role_perm rp JOIN sys_permission p ON p.perm_id=rp.perm_id WHERE rp.role_id=? AND p.perm_code='department-consumption:write'",role);
        assertThat(call("POST","/mobile/consumption/operations/recover",op).path("code").asInt()).isEqualTo(403);
        assertThat(receiptCount(op)).isZero();assertThat(stock(target)).isEqualByComparingTo("4");
    }
    private void recoverReview(String path,Map<String,Object> op) throws Exception {
        JsonNode result=call("POST",path+"/recover",op);
        assertThat(result.path("code").asInt()).as(result.toString()).isZero();
        assertThat(result.path("data").hasNonNull("result")).as(result.toString()).isFalse();
        assertThat(result.path("data").path("reviewHash").asText()).isNotBlank();op.put("reviewHash",result.path("data").path("reviewHash").asText());
    }
    private long receiptCount(Map<String,Object> op) {return id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=? AND operation_id=?",user,op.get("operationId"));}
    @Test void sameRequisitionDifferentSpecificationsStayDistinctAndPickIndependently() throws Exception {
        var first=pickOperation("PICK_LOOSE");
        jdbc.update("UPDATE product SET product_name='一次性使用注射器',spec_model='5ml' WHERE product_id=?",product);
        long category=id("SELECT category_id FROM product WHERE product_id=?",product);
        String secondCode="SYR-10-"+UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO product(product_code,product_name,spec_model,category_id,unit) VALUES(?,'一次性使用注射器','10ml',?,'个')",secondCode,category);
        long secondProduct=id("SELECT product_id FROM product WHERE product_code=?",secondCode);
        jdbc.update("INSERT INTO inventory_batch(system_batch_no,product_id,batch_unit_price) VALUES(?,?,10)",secondCode,secondProduct);
        long secondBatch=id("SELECT batch_id FROM inventory_batch WHERE product_id=?",secondProduct);
        jdbc.update("INSERT INTO inventory_balance(warehouse_id,product_id,batch_id,available_qty) VALUES(?,?,?,10)",source,secondProduct,secondBatch);
        long req=id("SELECT requisition_id FROM department_requisition_item WHERE item_id=?",first.get("taskId"));
        jdbc.update("INSERT INTO department_requisition_item(requisition_id,product_id,quantity,unit,item_type) VALUES(?,?,4,'个','loose')",req,secondProduct);
        long secondItem=id("SELECT item_id FROM department_requisition_item WHERE requisition_id=? AND product_id=?",req,secondProduct);
        String reqNo=jdbc.queryForObject("SELECT requisition_no FROM department_requisition WHERE requisition_id=?",String.class,req);
        var orders=call("GET","/mobile/picking/orders?deptId="+dept+"&warehouseId="+target+"&query="+reqNo,null);
        assertThat(orders.path("code").asInt()).as(orders.toString()).isZero();assertThat(orders.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(orders.path("data").path("rows").get(0).path("pendingLineCount").asInt()).isEqualTo(2);
        var wholeOrder=call("GET","/mobile/picking/orders/"+req+"?deptId="+dept+"&warehouseId="+target,null);
        assertThat(wholeOrder.path("data").path("items")).hasSize(2);
        var located=call("POST","/mobile/picking/orders/"+req+"/scans/resolve",Map.of("deptId",dept,"warehouseId",target,"rawCode",secondCode));
        assertThat(located.path("data").path("matches")).hasSize(1);
        assertThat(located.path("data").path("matches").get(0).path("task").path("id").asLong()).isEqualTo(secondItem);
        var list=call("GET","/mobile/picking/tasks?deptId="+dept+"&warehouseId="+target+"&query="+reqNo,null);
        assertThat(list.path("code").asInt()).as(list.toString()).isZero();assertThat(list.path("data").path("total").asInt()).isEqualTo(2);
        var a=call("GET","/mobile/picking/tasks/"+first.get("taskId")+"?deptId="+dept+"&warehouseId="+target,null).path("data");
        var b=call("GET","/mobile/picking/tasks/"+secondItem+"?deptId="+dept+"&warehouseId="+target,null).path("data");
        assertThat(a.path("documentNo")).isEqualTo(b.path("documentNo"));assertThat(a.path("productName")).isEqualTo(b.path("productName"));
        assertThat(a.path("specModel").asText()).isEqualTo("5ml");assertThat(b.path("specModel").asText()).isEqualTo("10ml");
        assertThat(a.path("productCode")).isNotEqualTo(b.path("productCode"));
        assertThat(call("POST","/mobile/picking/scans/resolve",Map.of("taskId",first.get("taskId"),"deptId",dept,"warehouseId",target,"rawCode",secondCode)).path("code").asInt()).isEqualTo(400);
        pickReview(first);assertThat(call("POST","/mobile/picking/operations",first).path("data").path("remainingQuantity").decimalValue()).isEqualByComparingTo("2");
        assertThat(stock(source)).isEqualByComparingTo("4");
        assertThat(jdbc.queryForObject("SELECT available_qty FROM inventory_balance WHERE batch_id=? AND warehouse_id=?",BigDecimal.class,secondBatch,source)).isEqualByComparingTo("10");
        var second=new LinkedHashMap<>(first);second.put("operationId",UUID.randomUUID().toString());second.put("taskId",secondItem);second.put("rawCode",secondCode);second.put("quantity","3");second.remove("reviewHash");
        pickReview(second);assertThat(call("POST","/mobile/picking/operations",second).path("data").path("remainingQuantity").decimalValue()).isEqualByComparingTo("1");
        assertThat(stock(source)).isEqualByComparingTo("4");assertThat(jdbc.queryForObject("SELECT available_qty FROM inventory_balance WHERE batch_id=? AND warehouse_id=?",BigDecimal.class,secondBatch,source)).isEqualByComparingTo("7");
        assertThat(id("SELECT COUNT(DISTINCT requisition_item_id) FROM spd_delivery_order WHERE requisition_no=?",reqNo)).isEqualTo(2);
    }
    @Test void orderPagingCountsDocumentsAndOrderDetailIncludesMoreThanTwentyLines() throws Exception {
        var op=pickOperation("PICK_LOOSE");long req=id("SELECT requisition_id FROM department_requisition_item WHERE item_id=?",op.get("taskId"));
        String reqNo=jdbc.queryForObject("SELECT requisition_no FROM department_requisition WHERE requisition_id=?",String.class,req);
        for(int n=0;n<24;n++)jdbc.update("INSERT INTO department_requisition_item(requisition_id,product_id,quantity,unit,item_type) VALUES(?,?,1,'个','loose')",req,product);
        var orders=call("GET","/mobile/picking/orders?deptId="+dept+"&warehouseId="+target+"&query="+reqNo+"&size=1",null);
        assertThat(orders.path("code").asInt()).as(orders.toString()).isZero();assertThat(orders.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(orders.path("data").path("rows").get(0).path("pendingLineCount").asInt()).isEqualTo(25);
        var detail=call("GET","/mobile/picking/orders/"+req+"?deptId="+dept+"&warehouseId="+target,null);
        assertThat(detail.path("data").path("items")).hasSize(25);
        var scan=call("POST","/mobile/picking/orders/"+req+"/scans/resolve",Map.of("deptId",dept,"warehouseId",target,"rawCode",op.get("rawCode")));
        assertThat(scan.path("data").path("matches")).hasSize(25);assertThat(stock(source)).isEqualByComparingTo("6");assertThat(receiptCount(op)).isZero();
    }
    @Test void orderScanCannotSelectAProductFromAnotherRequisition() throws Exception {
        var first=pickOperation("PICK_LOOSE");var second=pickOperation("PICK_LOOSE");
        long reqA=id("SELECT requisition_id FROM department_requisition_item WHERE item_id=?",first.get("taskId"));
        long reqB=id("SELECT requisition_id FROM department_requisition_item WHERE item_id=?",second.get("taskId"));
        long category=id("SELECT category_id FROM product WHERE product_id=?",product);String code="OTHER-"+UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO product(product_code,product_name,spec_model,category_id,unit) VALUES(?,'另一单商品','test',?,'个')",code,category);
        long other=id("SELECT product_id FROM product WHERE product_code=?",code);jdbc.update("UPDATE department_requisition_item SET product_id=? WHERE item_id=?",other,second.get("taskId"));
        var body=Map.of("deptId",dept,"warehouseId",target,"rawCode",code);
        assertThat(call("POST","/mobile/picking/orders/"+reqA+"/scans/resolve",body).path("code").asInt()).isEqualTo(400);
        var found=call("POST","/mobile/picking/orders/"+reqB+"/scans/resolve",body);
        assertThat(found.path("data").path("matches")).hasSize(1);assertThat(found.path("data").path("matches").get(0).path("task").path("id").asLong()).isEqualTo(second.get("taskId"));
        assertThat(stock(source)).isEqualByComparingTo("6");assertThat(id("SELECT COUNT(*) FROM mobile_operation_receipt WHERE user_id=?",user)).isZero();
    }
    @Test void completedOrderRemainsReadableWithNoPendingLines() throws Exception {
        var op=pickOperation("PICK_LOOSE");op.put("quantity","4");long req=id("SELECT requisition_id FROM department_requisition_item WHERE item_id=?",op.get("taskId"));
        String reqNo=jdbc.queryForObject("SELECT requisition_no FROM department_requisition WHERE requisition_id=?",String.class,req);
        pickReview(op);assertThat(call("POST","/mobile/picking/operations",op).path("code").asInt()).isZero();
        assertThat(call("GET","/mobile/picking/orders?deptId="+dept+"&warehouseId="+target+"&query="+reqNo,null).path("data").path("total").asInt()).isZero();
        var detail=call("GET","/mobile/picking/orders/"+req+"?deptId="+dept+"&warehouseId="+target,null);
        assertThat(detail.path("data").path("items")).isEmpty();assertThat(detail.path("data").path("order").path("status").asText()).isEqualTo("picked");
        assertThat(call("POST","/mobile/picking/orders/"+req+"/scans/resolve",Map.of("deptId",dept,"warehouseId",target,"rawCode",op.get("rawCode"))).path("code").asInt()).isEqualTo(400);
        assertThat(stock(source)).isEqualByComparingTo("2");assertThat(receiptCount(op)).isEqualTo(1);
    }
    @Test void fractionalPickingIsRejectedAtReviewAndSubmitWithoutStockWrites() throws Exception {
        var op=pickOperation("PICK_LOOSE");op.put("quantity","1.5");
        assertThat(call("POST","/mobile/picking/operations/validate",op).path("code").asInt()).isEqualTo(400);
        assertThat(call("POST","/mobile/picking/operations",op).path("code").asInt()).isEqualTo(400);
        assertThat(stock(source)).isEqualByComparingTo("6");assertThat(receiptCount(op)).isZero();
    }
    @Test void localBrowserPreflightDoesNotBypassBusinessAuthentication() throws Exception {
        URI uri=URI.create("http://127.0.0.1:"+port+"/api/mobile/picking/tasks?deptId="+dept+"&warehouseId="+target);
        var allowed=http.send(HttpRequest.newBuilder(uri).header("Origin","http://127.0.0.1:1831")
                .header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","authorization")
                .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(allowed.statusCode()).isEqualTo(200);assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("http://127.0.0.1:1831");
        var unauthenticated=http.send(HttpRequest.newBuilder(uri).header("Origin","http://127.0.0.1:1831").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        var unknownOrigin=http.send(HttpRequest.newBuilder(uri).header("Origin","https://untrusted.invalid")
                .header("Access-Control-Request-Method","GET").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(unknownOrigin.statusCode()).isEqualTo(403);
        var bareOptions=http.send(HttpRequest.newBuilder(uri).method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(bareOptions.statusCode()).isEqualTo(401);
        var authenticated=http.send(HttpRequest.newBuilder(uri).header("Origin","http://127.0.0.1:1831").header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(authenticated.statusCode()).isEqualTo(200);assertThat(json.readTree(authenticated.body()).path("code").asInt()).isZero();
    }
    private List<Long> signedConsumptionPackages() {
        var op=pickOperation("PICK_PACKAGE");var ids=pickPackages(op);
        String supplier="SC-"+UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO supplier(supplier_code,supplier_name,credit_code,supplier_type,contact_name,contact_phone) VALUES(?,'消耗验收供应商',?,'distributor','测试联系人','00000000000')",supplier,supplier);
        long supplierId=id("SELECT supplier_id FROM supplier WHERE supplier_code=?",supplier);
        jdbc.update("UPDATE inventory_batch SET supplier_id=?,settlement_mode='department_consumption' WHERE product_id=?",supplierId,product);
        String req=jdbc.queryForObject("SELECT r.requisition_no FROM department_requisition r JOIN department_requisition_item i ON i.requisition_id=r.requisition_id WHERE i.item_id=?",String.class,op.get("taskId"));
        List<String> codes=ids.stream().map(label->jdbc.queryForObject("SELECT label_no FROM quota_package_label WHERE label_id=?",String.class,label)).toList();
        String no=(String)delivery.confirmPicking(Map.of("requisitionNo",req,"itemId",op.get("taskId"),"warehouseName",jdbc.queryForObject("SELECT warehouse_name FROM warehouse WHERE warehouse_id=?",String.class,source),"labelNos",codes)).get("deliveryNo");
        delivery.signDelivery(no);return ids;
    }
    private Map<String,Object> consumptionOperation(Long label) {
        return new LinkedHashMap<>(Map.of("operationId",UUID.randomUUID().toString(),"deviceId","consume-test","kind","CONSUME_PACKAGE",
                "taskId",label,"deptId",dept,"warehouseId",target,"rawCode",jdbc.queryForObject("SELECT label_no FROM quota_package_label WHERE label_id=?",String.class,label),"wholePackageConfirmed",true));
    }
    private void consumptionReview(Map<String,Object> op) throws Exception {
        JsonNode review=call("POST","/mobile/consumption/operations/validate",op);
        assertThat(review.path("code").asInt()).as(review.toString()).isZero();op.put("reviewHash",review.path("data").path("reviewHash").asText());
    }
    private List<Integer> concurrentConsumption(Map<String,Object> a,Map<String,Object> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var first=pool.submit(()->{start.await();return call("POST","/mobile/consumption/operations",a);});
            var second=pool.submit(()->{start.await();return call("POST","/mobile/consumption/operations",b);});start.countDown();
            return List.of(first.get(30,TimeUnit.SECONDS).path("code").asInt(),second.get(30,TimeUnit.SECONDS).path("code").asInt());
        } finally {pool.shutdownNow();}
    }
    private long consumptionEvents(){return id("SELECT COUNT(*) FROM inventory_event WHERE warehouse_id=? AND product_id=? AND event_type='quota_package_scan_out'",target,product);}
    private long consumptionSettlementItems(){return id("SELECT COUNT(*) FROM settlement_bill_item WHERE product_id=? AND source_biz_type='department_consumption_item'",product);}
    private JsonNode call(String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path)).timeout(java.time.Duration.ofSeconds(20));
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return json.readTree(http.send(request.build(), HttpResponse.BodyHandlers.ofString()).body());
    }
    private long warehouse(String name, Long deptId, String type) {
        jdbc.update("INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, campus_name, dept_id) VALUES (?, ?, ?, '测试', ?)", name, name, type, deptId);
        return id("SELECT warehouse_id FROM warehouse WHERE warehouse_code = ?", name);
    }
    private long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private BigDecimal stock(long warehouse) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(available_qty), 0) FROM inventory_balance WHERE warehouse_id = ? AND product_id = ?", BigDecimal.class, warehouse, product);
    }
}
