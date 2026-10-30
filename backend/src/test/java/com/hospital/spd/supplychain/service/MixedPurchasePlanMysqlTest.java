package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.*;
import com.hospital.spd.common.service.*;
import com.hospital.spd.integration.McpPhase3Outbox;
import com.hospital.spd.supplychain.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies migrations, mixed supplier snapshots, rollback and concurrent final approval in disposable MySQL. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="SPD_PHASE3_REVIEW_TESTS",matches="true")
class MixedPurchasePlanMysqlTest {
    JdbcTemplate db; TransactionTemplate tx; PurchaseOrderService service; McpPhase3Outbox outbox; SupplyChainSupport support;
    long a,b,legacy; String low,high;
    @BeforeAll void database() {
        String url=System.getenv("SPD_PHASE3_REVIEW_URL");assertTrue(url.contains("/spd_phase3_review?"));
        var ds=new DriverManagerDataSource(url,"root",System.getenv("SPD_PHASE3_REVIEW_PASSWORD"));db=new JdbcTemplate(ds);
        tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        Flyway.configure().dataSource(ds).target("106").load().migrate();
        a=db.queryForObject("SELECT MIN(supplier_id) FROM supplier WHERE status=1 AND deleted=0",Long.class);
        b=db.queryForObject("SELECT MAX(supplier_id) FROM supplier WHERE status=1 AND deleted=0",Long.class);assertNotEquals(a,b);
        long product=db.queryForObject("SELECT MIN(product_id) FROM product",Long.class);
        db.update("INSERT INTO purchase_plan(plan_no,plan_status,supplier_id,product_id,planned_quantity,converted_order_no) VALUES('MIX-LEGACY','executed',?,?,1,'MIX-LEGACY-ORDER')",a,product);
        legacy=db.queryForObject("SELECT plan_id FROM purchase_plan WHERE plan_no='MIX-LEGACY'",Long.class);
        db.update("INSERT INTO purchase_order(order_no,supplier_id,order_source,order_status,total_amount) VALUES('MIX-LEGACY-ORDER',?,'plan','approved',1)",a);
        long order=db.queryForObject("SELECT purchase_order_id FROM purchase_order WHERE order_no='MIX-LEGACY-ORDER'",Long.class);
        db.update("INSERT INTO purchase_order_item(purchase_order_id,product_id,quantity,unit,estimated_unit_price,amount,received_quantity) VALUES(?,?,1,'支',1,1,0)",order,product);
        Flyway.configure().dataSource(ds).load().migrate();
        var numbers=new DocumentNumberService(db);var audit=new AuditLogService(db,OperatorContext::system);
        support=new SupplyChainSupport(numbers,audit,new InventoryMovementService(db,new InventoryEventService(db,numbers,OperatorContext::system)));
        service=new PurchaseOrderService(db,support,OperatorContext::system);
        outbox=new McpPhase3Outbox(db,new MockEnvironment());ReflectionTestUtils.setField(service,"phase3Outbox",outbox);
    }
    @BeforeEach void products() {
        String id=UUID.randomUUID().toString().substring(0,12);low="MIX-L-"+id;high="MIX-H-"+id;
        for(String c:List.of(low,high))db.update("INSERT INTO product(product_code,product_name,spec_model,category_id,supplier_id,unit,purchase_price,min_purchase_qty,is_high_value,is_quota_managed) VALUES(?,?,'mix',(SELECT MIN(category_id) FROM product_category),?,'支',?,1,?,?)",c,c,c.equals(low)?a:b,new BigDecimal(c.equals(low)?"2.5000":"100.0000"),c.equals(high)?1:0,c.equals(low)?1:0);
    }
    String demand() {
        String no=tx.execute(t->service.createDemand(Map.of("items",List.of(Map.of("productCode",low,"quantity",20),Map.of("productCode",high,"quantity",4))))).get("demandNo").toString();
        for(String action:List.of("submit","approve"))tx.execute(t->service.performDemandAction(no,new PurchaseOrderActionRequest(action,null)));
        return no;
    }
    String plan() {
        String demand=demand();var r=tx.execute(t->service.createPlanFromDemands(Map.of("demandNos",List.of(demand))));
        assertEquals(1,r.get("createdPlans"));return r.get("planNo").toString();
    }
    Map<String,Object> approve(String no){return tx.execute(t->service.performPlanAction(no,new PurchaseOrderActionRequest("approve","混合计划审批")));}
    long planId(String no){return db.queryForObject("SELECT plan_id FROM purchase_plan WHERE plan_no=?",Long.class,no);}
    @Test void migrationPreservesLegacyIdentityAndOrder() {
        assertEquals(legacy,db.queryForObject("SELECT projection_id FROM purchase_plan_supplier WHERE plan_id=?",Long.class,legacy));
        assertEquals(legacy,db.queryForObject("SELECT plan_item_id FROM purchase_plan_item WHERE plan_id=?",Long.class,legacy));
        assertNull(db.queryForObject("SELECT estimated_unit_price FROM purchase_plan_item WHERE plan_id=?",BigDecimal.class,legacy));
        assertEquals(legacy,db.queryForObject("SELECT source_plan_id FROM purchase_order WHERE order_no='MIX-LEGACY-ORDER'",Long.class));
    }
    @Test void finalApprovalCreatesTwoApprovedOrdersWithCommonPlan() {
        String no=plan();assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM purchase_plan_item WHERE plan_id=?",Long.class,planId(no)));
        var r=approve(no);assertEquals("executed",r.get("status"));assertEquals(2,((List<?>)r.get("orderNos")).size());
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE source_plan_id=? AND order_status='approved' AND approve_by=1",Long.class,planId(no)));
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM purchase_order_item i JOIN purchase_order o ON o.purchase_order_id=i.purchase_order_id WHERE o.source_plan_id=? AND i.source_plan_item_id IS NOT NULL",Long.class,planId(no)));
        assertNull(db.queryForObject("SELECT converted_order_no FROM purchase_plan WHERE plan_no=?",String.class,no));
    }
    @Test void sameSupplierCombinesHighAndLowIntoOneOrder() {
        db.update("UPDATE product SET supplier_id=? WHERE product_code=?",a,high);
        String no=plan();assertEquals(1,((List<?>)approve(no).get("orderNos")).size());
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM purchase_order_item i JOIN purchase_order o ON o.purchase_order_id=i.purchase_order_id WHERE o.source_plan_id=?",Long.class,planId(no)));
    }
    @Test void approvedSnapshotDoesNotFollowLaterCatalogSupplierOrPrice() {
        String no=plan();db.update("UPDATE product SET supplier_id=?,purchase_price=999 WHERE product_code=?",a,high);approve(no);
        assertEquals(new BigDecimal("400.0000"),db.queryForObject("SELECT total_amount FROM purchase_order WHERE source_plan_id=? AND supplier_id=?",BigDecimal.class,planId(no),b));
    }
    @Test void missingSupplierCannotChooseAnArbitraryPartner() {
        db.update("UPDATE product SET supplier_id=NULL WHERE product_code=?",high);String d=demand();
        assertThrows(IllegalArgumentException.class,()->tx.execute(t->service.createPlanFromDemands(Map.of("demandNos",List.of(d)))));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM purchase_plan_demand pd JOIN purchase_demand d ON d.demand_id=pd.demand_id WHERE d.demand_no=?",Long.class,d));
    }
    @Test void uniformSupplierOverrideIsRejected() {
        String d=demand();assertThrows(IllegalArgumentException.class,()->tx.execute(t->service.createPlanFromDemands(Map.of("demandNos",List.of(d),"supplierName","覆盖全部明细"))));
    }
    @Test void draftDistributorEditChangesGroupingButCompletedPlanIsImmutable() {
        String no=plan();long id=planId(no);long item=db.queryForObject("SELECT i.plan_item_id FROM purchase_plan_item i JOIN product p ON p.product_id=i.product_id WHERE i.plan_id=? AND p.product_code=?",Long.class,id,high);
        tx.execute(t->service.updatePlanSupplier(no,item,a));assertEquals(1,((List<?>)approve(no).get("orderNos")).size());
        assertThrows(IllegalArgumentException.class,()->tx.execute(t->service.updatePlanSupplier(no,item,b)));
    }
    @Test void supplierFailureRollsBackApprovalAndAllOrders() {
        String no=plan();db.update("UPDATE supplier SET status=0 WHERE supplier_id=?",b);
        try {assertThrows(IllegalArgumentException.class,()->approve(no));assertEquals("draft",db.queryForObject("SELECT plan_status FROM purchase_plan WHERE plan_no=?",String.class,no));assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE source_plan_id=?",Long.class,planId(no)));}
        finally {db.update("UPDATE supplier SET status=1 WHERE supplier_id=?",b);}
    }
    @Test void partialOrderInsertFailureRollsBackThenRetryCreatesExactlyTwo() {
        String no=plan();db.execute("CREATE TRIGGER reject_mixed_supplier BEFORE INSERT ON purchase_order FOR EACH ROW BEGIN IF NEW.source_plan_id="+planId(no)+" AND NEW.supplier_id="+b+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected second supplier failure'; END IF; END");
        try {assertThrows(RuntimeException.class,()->approve(no));assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE source_plan_id=?",Long.class,planId(no)));assertEquals("draft",db.queryForObject("SELECT plan_status FROM purchase_plan WHERE plan_no=?",String.class,no));}
        finally {db.execute("DROP TRIGGER reject_mixed_supplier");}
        assertEquals(2,((List<?>)approve(no).get("orderNos")).size());
    }
    @Test void concurrentFinalApprovalAndReplayDoNotDuplicateOrders() throws Exception {
        String no=plan();ExecutorService pool=Executors.newFixedThreadPool(2);
        try {var first=pool.submit(()->approve(no));var second=pool.submit(()->approve(no));assertEquals(first.get(20,TimeUnit.SECONDS).get("orderNos"),second.get(20,TimeUnit.SECONDS).get("orderNos"));}
        finally {pool.shutdownNow();}
        assertEquals(2,((List<?>)tx.execute(t->service.performPlanAction(no,new PurchaseOrderActionRequest("execute",null))).get("orderNos")).size());
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE source_plan_id=?",Long.class,planId(no)));
    }
    @Test void eachMcpProjectionContainsOnlyItsSupplierLines() {
        String no=plan();approve(no);
        var payloads=db.queryForList("SELECT source_payload FROM mcp_phase3_outbox WHERE document_type='purchase_plan' AND JSON_UNQUOTE(JSON_EXTRACT(source_payload,'$.documentNo'))=? AND state<>'superseded'",String.class,no);
        assertEquals(2,payloads.size());for(String json:payloads){var p=read(json);assertEquals(Long.toString(planId(no)),p.path("sourcePlanId").asText());assertEquals(1,p.path("items").size());assertEquals(1,p.path("convertedOrderNos").size());assertEquals("catalog_at_plan_creation",p.path("items").get(0).path("priceBasis").asText());}
    }
    @Test void partialApprovalCannotChangeDistributorOrCreateOrders() {
        String no=plan();db.update("INSERT INTO business_approval_run(document_key,current_order,status,flow_data_scope) VALUES(?,1,'pending',1)","purchase-management:plan-approval:"+no);
        long item=db.queryForObject("SELECT MIN(plan_item_id) FROM purchase_plan_item WHERE plan_id=?",Long.class,planId(no));
        assertThrows(IllegalArgumentException.class,()->tx.execute(t->service.updatePlanSupplier(no,item,b)));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE source_plan_id=?",Long.class,planId(no)));
    }
    @Test void generatedOrdersKeepPlanOwnerAndRecordActualApprover() {
        String no=plan();long owner=db.queryForObject("SELECT MIN(user_id) FROM sys_user WHERE user_id<>1 AND deleted=0",Long.class);
        db.update("UPDATE purchase_plan SET create_by=? WHERE plan_no=?",owner,no);approve(no);
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE source_plan_id=? AND create_by=? AND approve_by=1",Long.class,planId(no),owner));
    }
    @Test void selfScopedNonOwnerCannotReadOrApproveAnotherPlan() {
        String no=plan();var operator=new OperatorContext(999999L,"scope-test","127.0.0.1",List.of("ROLE_SYSTEM"),null,OperatorContext.DATA_SCOPE_SELF);
        var scoped=new PurchaseOrderService(db,support,()->operator);
        assertThrows(IllegalArgumentException.class,()->scoped.getPlanDetail(no));
        assertThrows(IllegalArgumentException.class,()->tx.execute(t->scoped.performPlanAction(no,new PurchaseOrderActionRequest("approve",null))));
        assertEquals("draft",db.queryForObject("SELECT plan_status FROM purchase_plan WHERE plan_no=?",String.class,no));
    }
    private com.fasterxml.jackson.databind.JsonNode read(String json){try{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);}catch(Exception e){throw new AssertionError(e);}}
}
