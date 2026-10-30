package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.service.*;
import com.hospital.spd.supplychain.*;
import com.hospital.spd.supplychain.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real draft-receipt, capacity, replay and hospital acceptance tests in an owned disposable MySQL schema. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="SPD_PHASE3_REVIEW_TESTS",matches="true")
class McpSupplyReceiverMysqlTest {
    JdbcTemplate db;TransactionTemplate tx;McpSupplyReceiver receiver;ReceivingOrderService receiving;
    MockEnvironment env;long product,order,line,supplier;String code,no,supplierCode;
    @BeforeAll void setup() {
        String url=System.getenv("SPD_PHASE3_REVIEW_URL");assertTrue(url.contains("/spd_phase3_review?"));
        var ds=new DriverManagerDataSource(url,"root",System.getenv("SPD_PHASE3_REVIEW_PASSWORD"));
        db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));Flyway.configure().dataSource(ds).load().migrate();
        env=new MockEnvironment().withProperty("SPD_MCP_SUPPLY_ENABLED","true").withProperty("SPD_MCP_ORGANIZATION","HOSPITAL");
        var numbers=new DocumentNumberService(db);var audit=new AuditLogService(db,OperatorContext::system);
        var stock=new InventoryMovementService(db,new InventoryEventService(db,numbers,OperatorContext::system));
        var support=new SupplyChainSupport(numbers,audit,stock);receiving=new ReceivingOrderService(db,support,OperatorContext::system);
        receiver=new McpSupplyReceiver(db,env,receiving,audit);
    }
    @BeforeEach void fixture() {
        env.setProperty("SPD_MCP_SUPPLY_ENABLED","true");code="SUPPLY-"+UUID.randomUUID().toString().substring(0,12);no=code;
        supplier=db.queryForObject("SELECT MIN(supplier_id) FROM supplier WHERE status=1 AND deleted=0",Long.class);
        supplierCode=db.queryForObject("SELECT supplier_code FROM supplier WHERE supplier_id=?",String.class,supplier);
        db.update("INSERT INTO product(product_code,product_name,spec_model,category_id,unit,purchase_price,is_high_value,is_quota_managed) VALUES(?,?,'1ml',(SELECT MIN(category_id) FROM product_category),'支',1.2345,0,0)",code,code);
        product=db.queryForObject("SELECT product_id FROM product WHERE product_code=?",Long.class,code);
        db.update("INSERT INTO purchase_order(order_no,supplier_id,order_source,order_status,total_amount) VALUES(?,?,'manual','sent',9.8760)",no,supplier);
        order=db.queryForObject("SELECT purchase_order_id FROM purchase_order WHERE order_no=?",Long.class,no);
        db.update("INSERT INTO purchase_order_item(purchase_order_id,product_id,quantity,unit,estimated_unit_price,amount,received_quantity) VALUES(?,?,8,'支',1.2345,9.8760,0)",order,product);
        line=db.queryForObject("SELECT item_id FROM purchase_order_item WHERE purchase_order_id=?",Long.class,order);
    }
    ObjectNode wire(String qty) {
        var wire=Phase3Json.MAPPER.createObjectNode();wire.put("messageId",UUID.randomUUID().toString());wire.put("schemaVersion","3.0");wire.put("sourceSystem","MCP-UI");wire.put("documentType","supply");wire.put("organization","HOSPITAL");
        var p=wire.putObject("payload");p.put("supplyNo","GH-"+UUID.randomUUID());p.put("orderNo",no);p.put("supplierCode",supplierCode);p.put("warehouseCode","WH-CENTER");p.put("externalOperator","供应商测试员");p.put("externalOperatorId","123");
        var item=p.putArray("items").addObject();item.put("supplyLineId",UUID.randomUUID().toString());item.put("orderLineId",Long.toString(line));item.put("productCode",code);item.put("unit","支");item.put("quantity",qty);item.put("productionBatchNo","LOT-TEST");item.put("productionDate",LocalDate.now().minusDays(1).toString());item.put("expireDate",LocalDate.now().plusYears(1).toString());item.put("udiCode","");return wire;
    }
    Map<String,Object> receive(ObjectNode w){return tx.execute(status->receiver.receive(w.path("messageId").asText(),w));}
    long count(){return db.queryForObject("SELECT COUNT(*) FROM receiving_order WHERE purchase_order_id=?",Long.class,order);}
    @Test void createsOnlyDraftWithoutInventoryOrSettlementAndIsIdempotent() {
        long events=db.queryForObject("SELECT COUNT(*) FROM inventory_event",Long.class),bills=db.queryForObject("SELECT COUNT(*) FROM settlement_bill",Long.class);
        var w=wire("3");var result=receive(w);assertEquals("draft",result.get("receivingStatus"));assertEquals(result,receive(w));assertEquals(1,count());
        assertEquals(events,db.queryForObject("SELECT COUNT(*) FROM inventory_event",Long.class));assertEquals(bills,db.queryForObject("SELECT COUNT(*) FROM settlement_bill",Long.class));
        var changed=w.deepCopy();changed.withObject("/payload").put("remark","different");assertThrows(IllegalArgumentException.class,()->receive(changed));assertEquals(1,count());
    }
    @Test void rejectsDuplicateBusinessNumberWithAnotherMessageId() {
        var w=wire("3");receive(w);w.put("messageId",UUID.randomUUID().toString());assertThrows(IllegalArgumentException.class,()->receive(w));assertEquals(1,count());
    }
    @Test void rejectsWrongSupplierWrongLineAndWrongUnit() {
        var supplier=wire("3");supplier.withObject("/payload").put("supplierCode","NOT-OWNER");assertThrows(IllegalArgumentException.class,()->receive(supplier));
        var invalid=wire("3");((ObjectNode)invalid.path("payload").path("items").get(0)).put("orderLineId","999999999");assertThrows(IllegalArgumentException.class,()->receive(invalid));
        var unit=wire("3");((ObjectNode)unit.path("payload").path("items").get(0)).put("unit","盒");assertThrows(IllegalArgumentException.class,()->receive(unit));assertEquals(0,count());
    }
    @Test void rejectsOverSupplyIncludingOutstandingDrafts() {
        receive(wire("5"));assertThrows(IllegalArgumentException.class,()->receive(wire("4")));assertEquals(1,count());
        var result=receiver.options("HOSPITAL",no,supplierCode);assertTrue(result.toString().contains("remainingQuantity=3"));
    }
    @Test void concurrentSuppliesCannotBothReserveBeyondTheOrder() throws Exception {
        var a=wire("5");var b=wire("5");var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->{start.await();try{receive(a);return true;}catch(IllegalArgumentException error){return false;}});
            var two=pool.submit(()->{start.await();try{receive(b);return true;}catch(IllegalArgumentException error){return false;}});
            start.countDown();assertNotEquals(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));assertEquals(1,count());
        }finally{pool.shutdownNow();}
    }
    @Test void hospitalApprovalAloneChangesStockAndReceiptStatus() {
        var w=wire("3");var result=receive(w);String receipt=result.get("receivingNo").toString();
        for(int i=0;i<10 && !"approved".equals(db.queryForObject("SELECT receiving_status FROM receiving_order WHERE receiving_no=?",String.class,receipt));i++)tx.executeWithoutResult(status->receiving.action(receipt,new ReceivingActionRequest("approve","医院验收")));
        assertEquals("approved",receiver.receipt("HOSPITAL",w.path("messageId").asText(),supplierCode).get("receivingStatus"));
        BigDecimal qty=db.queryForObject("SELECT SUM(available_qty) FROM inventory_balance WHERE product_id=?",BigDecimal.class,product);assertEquals(0,qty.compareTo(new BigDecimal("3")));
        receive(w);assertEquals(1,count());assertEquals(0,qty.compareTo(db.queryForObject("SELECT SUM(available_qty) FROM inventory_balance WHERE product_id=?",BigDecimal.class,product)));
    }
    @Test void hospitalCannotEditBeyondDeclaredQuantityOrChangeOrder() {
        var result=receive(wire("3"));String receipt=result.get("receivingNo").toString();
        var item=new ReceivingItemRequest(code,"LOT", "",LocalDate.now().minusDays(1).toString(),LocalDate.now().plusYears(1).toString(),new BigDecimal("4"),new BigDecimal("4"),BigDecimal.ZERO,line);
        var request=new ReceivingOrderRequest("purchase_order",no,supplier,null,"WH-CENTER",null,"normal",false,"",List.of(item));
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(status->receiving.update(receipt,request)));
        long id=db.queryForObject("SELECT receiving_order_id FROM receiving_order WHERE receiving_no=?",Long.class,receipt);
        assertThrows(IllegalArgumentException.class,()->new McpSupplyReceiptRules(db).validateEdit(id,order+999,List.of(item)));
    }
    @Test void rejectsDisabledGateBadDatesZeroAndFractionalQuantities() {
        for(String qty:List.of("0","-1","1.5","9"))assertThrows(IllegalArgumentException.class,()->receive(wire(qty)));
        var w=wire("1");((ObjectNode)w.path("payload").path("items").get(0)).put("expireDate","bad-date");assertThrows(IllegalArgumentException.class,()->receive(w));
        var udi=wire("1");((ObjectNode)udi.path("payload").path("items").get(0)).put("udiCode","X".repeat(121));assertThrows(IllegalArgumentException.class,()->receive(udi));
        env.setProperty("SPD_MCP_SUPPLY_ENABLED","false");assertThrows(IllegalStateException.class,()->receive(wire("1")));assertEquals(0,count());
    }
    @Test void rollbackRemovesBothDraftAndReceiptMapping() {
        var w=wire("3");assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{receiver.receive(w.path("messageId").asText(),w);throw new IllegalStateException("rollback");}));assertEquals(0,count());
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM mcp_supply_receipt WHERE message_id=?",Long.class,w.path("messageId").asText()));
    }
    @Test void legacyUnallocatedDraftsAlsoReserveProductCapacity() {
        long warehouse=db.queryForObject("SELECT warehouse_id FROM warehouse WHERE warehouse_code='WH-CENTER'",Long.class);
        db.update("INSERT INTO receiving_order(receiving_no,purchase_order_id,supplier_id,warehouse_id,receiving_status) VALUES(?,?,?,?,'draft')","MANUAL-"+code,order,supplier,warehouse);
        long receipt=db.queryForObject("SELECT receiving_order_id FROM receiving_order WHERE receiving_no=?",Long.class,"MANUAL-"+code);
        db.update("INSERT INTO receiving_order_item(receiving_order_id,product_id,quantity,unit_price,amount,qualified_quantity,unqualified_quantity) VALUES(?,?,2,1.2345,2.4690,2,0)",receipt,product);
        assertTrue(receiver.options("HOSPITAL",no,supplierCode).toString().contains("remainingQuantity=6"));
        assertThrows(IllegalArgumentException.class,()->receive(wire("7")));
        assertEquals(1,count());
    }
    @Test void receiptReportsActualHospitalAdjustedAcceptedAndRejectedQuantities() {
        var w=wire("3");String no=receive(w).get("receivingNo").toString();
        var item=new ReceivingItemRequest(code,"LOT","",LocalDate.now().minusDays(1).toString(),LocalDate.now().plusYears(1).toString(),new BigDecimal("2"),BigDecimal.ONE,BigDecimal.ONE,line);
        tx.executeWithoutResult(status->receiving.update(no,new ReceivingOrderRequest("purchase_order",this.no,supplier,null,"WH-CENTER",null,"normal",false,"医院核对少到1，不合格1",List.of(item))));
        for(int i=0;i<10 && !"approved".equals(db.queryForObject("SELECT receiving_status FROM receiving_order WHERE receiving_no=?",String.class,no));i++)tx.executeWithoutResult(status->receiving.action(no,new ReceivingActionRequest("approve","医院验收")));
        var result=receiver.receipt("HOSPITAL",w.path("messageId").asText(),supplierCode);
        assertEquals("2",result.get("quantity"));assertEquals("1",result.get("qualifiedQuantity"));assertEquals("1",result.get("unqualifiedQuantity"));
        assertEquals(0,db.queryForObject("SELECT SUM(available_qty) FROM inventory_balance WHERE product_id=?",BigDecimal.class,product).compareTo(BigDecimal.ONE));
    }
    long secondLine() {
        db.update("INSERT INTO product(product_code,product_name,spec_model,category_id,unit,purchase_price,is_high_value,is_quota_managed) VALUES(?,?,'1盒',(SELECT MIN(category_id) FROM product_category),'盒',1.2345,0,0)",code+"-B",code+"-B");
        long id=db.queryForObject("SELECT product_id FROM product WHERE product_code=?",Long.class,code+"-B");
        db.update("INSERT INTO purchase_order_item(purchase_order_id,product_id,quantity,unit,estimated_unit_price,amount,received_quantity) VALUES(?,?,8,'盒',1.2345,9.8760,0)",order,id);
        db.update("UPDATE purchase_order SET total_amount=19.7520 WHERE purchase_order_id=?",order);
        return db.queryForObject("SELECT item_id FROM purchase_order_item WHERE purchase_order_id=? AND product_id=?",Long.class,order,id);
    }
    @Test void concurrentDifferentProductsOnTheSameOrderUseOneProductLockOrder() throws Exception {
        long second=secondLine();var a=wire("2");var b=wire("2");var item=(ObjectNode)b.path("payload").path("items").get(0);item.put("orderLineId",Long.toString(second));item.put("productCode",code+"-B");item.put("unit","盒");
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {var one=pool.submit(()->{start.await();return receive(a);});var two=pool.submit(()->{start.await();return receive(b);});start.countDown();assertEquals("draft",one.get(20,TimeUnit.SECONDS).get("receivingStatus"));assertEquals("draft",two.get(20,TimeUnit.SECONDS).get("receivingStatus"));assertEquals(2,count());}
        finally {pool.shutdownNow();}
    }
    @Test void receiptNeverAddsDifferentBasicUnitsIntoOneQuantity() {
        long second=secondLine();var w=wire("3");var b=((ObjectNode)w.path("payload").path("items").get(0)).deepCopy();b.put("supplyLineId",UUID.randomUUID().toString());b.put("orderLineId",Long.toString(second));b.put("productCode",code+"-B");b.put("unit","盒");b.put("quantity","2");((com.fasterxml.jackson.databind.node.ArrayNode)w.path("payload").path("items")).add(b);
        String receipt=receive(w).get("receivingNo").toString();for(int i=0;i<10&&!"approved".equals(db.queryForObject("SELECT receiving_status FROM receiving_order WHERE receiving_no=?",String.class,receipt));i++)tx.executeWithoutResult(status->receiving.action(receipt,new ReceivingActionRequest("approve","医院验收")));
        var result=receiver.receipt("HOSPITAL",w.path("messageId").asText(),supplierCode);assertFalse(result.containsKey("quantity"));assertEquals(2,((List<?>)result.get("quantities")).size());
    }
}
