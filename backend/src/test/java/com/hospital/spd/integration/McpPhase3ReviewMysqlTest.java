package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only against the disposable database provisioned by scripts/test-mcp-phase3-review.py. */
@EnabledIfEnvironmentVariable(named="SPD_PHASE3_REVIEW_TESTS",matches="true")
class McpPhase3ReviewMysqlTest {
    JdbcTemplate db;
    McpPhase3Outbox outbox;
    McpPhase3Delivery delivery;
    TransactionTemplate tx;
    @BeforeEach void setup() {
        String url=System.getenv("SPD_PHASE3_REVIEW_URL");
        assertTrue(url.contains("/spd_phase3_review?"),"Only the owned review database is permitted");
        var source=new DriverManagerDataSource(url,"root",System.getenv("SPD_PHASE3_REVIEW_PASSWORD"));
        db=new JdbcTemplate(source);
        for(String table:List.of("mcp_phase3_feedback","mcp_phase3_outbox","mcp_phase3_document","sys_role_perm","sys_role","sys_permission","purchase_plan","purchase_order","settlement_bill_item","settlement_bill","inventory_batch","supplier","product"))db.execute("DROP TABLE IF EXISTS "+table);
        db.execute("CREATE TABLE sys_permission(perm_id BIGINT PRIMARY KEY AUTO_INCREMENT,parent_id BIGINT,perm_name VARCHAR(100),perm_code VARCHAR(100),perm_type INT,path VARCHAR(100),sort_order INT)");
        db.execute("CREATE TABLE sys_role(role_id BIGINT,role_code VARCHAR(100),deleted INT)");
        db.execute("CREATE TABLE sys_role_perm(role_id BIGINT,perm_id BIGINT)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V104__mcp_phase3_outbox.sql")).execute(source);
        db.execute("CREATE TABLE supplier(supplier_id BIGINT PRIMARY KEY,supplier_code VARCHAR(100))");
        db.execute("CREATE TABLE product(product_id BIGINT PRIMARY KEY,product_code VARCHAR(100),unit VARCHAR(30))");
        db.execute("CREATE TABLE purchase_order(purchase_order_id BIGINT,order_no VARCHAR(100))");
        db.execute("CREATE TABLE purchase_plan(plan_id BIGINT PRIMARY KEY,plan_no VARCHAR(100),supplier_id BIGINT,product_id BIGINT,plan_status VARCHAR(30),planned_quantity DECIMAL(18,4),create_time DATETIME(6),approve_time DATETIME(6),converted_order_no VARCHAR(100),remark VARCHAR(100))");
        db.execute("CREATE TABLE settlement_bill(settlement_id BIGINT PRIMARY KEY,settlement_no VARCHAR(100),supplier_id BIGINT,status VARCHAR(30),settlement_period VARCHAR(30),settlement_mode VARCHAR(30),total_amount DECIMAL(18,4),generate_time DATETIME(6),confirm_time DATETIME(6))");
        db.execute("CREATE TABLE inventory_batch(batch_id BIGINT PRIMARY KEY,system_batch_no VARCHAR(100))");
        db.execute("CREATE TABLE settlement_bill_item(item_id BIGINT PRIMARY KEY,settlement_id BIGINT,product_id BIGINT,batch_id BIGINT,source_biz_type VARCHAR(50),source_biz_id BIGINT,trace_code_id BIGINT,goods_type VARCHAR(30),quantity DECIMAL(18,4),unit_price DECIMAL(18,4),amount DECIMAL(18,4))");
        var env=new MockEnvironment().withProperty("SPD_MCP_CURRENCY","USD").withProperty("SPD_MCP_TAX_INCLUDED","false").withProperty("SPD_MCP_ORGANIZATION","HOSPITAL").withProperty("SPD_MCP_UI_INSTANCE_ID","new");
        var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);
        outbox=new McpPhase3Outbox(db,env);delivery=new McpPhase3Delivery(db,env,manager);
    }
    String frozen(String state,String target,boolean expired) {
        String id=UUID.randomUUID().toString();
        String payload="{\"documentNo\":\"BILL-1\",\"currency\":\"CNY\",\"taxIncluded\":true,\"totalAmount\":\"0.0001\",\"items\":[]}";
        String wire="{\"messageId\":\""+id+"\",\"documentType\":\"settlement\",\"revision\":\"1\",\"payload\":"+payload+"}";
        db.update("INSERT INTO mcp_phase3_document(document_type,source_id,revision,published) VALUES('settlement',1,1,1)");
        db.update("INSERT INTO mcp_phase3_outbox(message_id,document_type,source_id,revision,operation,source_updated_at,source_payload,wire_payload,payload_hash,state,target_key,lease_id,lease_until) VALUES(?,'settlement',1,1,'CANCEL','2026-10-01T00:00:00Z',CAST(? AS JSON),CAST(? AS JSON),?,?,?,'old-lease',"+(expired?"DATE_SUB":"DATE_ADD")+"(NOW(6),INTERVAL 1 MINUTE))",id,"{\"documentNo\":\"BILL-1\",\"items\":[]}",wire,Phase3Json.hash(Phase3Json.parse(wire)),state,target);
        return id;
    }
    List<JsonNode> send() {
        var sent=new ArrayList<JsonNode>();
        delivery.deliver("new-target",(method,path,body,key,timeout)->{
            JsonNode wire=Phase3Json.MAPPER.valueToTree(body);sent.add(wire);
            return Phase3Json.parse("{\"data\":{\"state\":\"processed\",\"storedRevision\":\""+wire.path("revision").asText()+"\"}}");
        });return sent;
    }
    @Test void republishPreservesFrozenCurrencyTaxAndAmount() {
        frozen("processed","old-target",true);
        tx.executeWithoutResult(status->outbox.republish("settlement",1));
        var sent=send();assertEquals(1,sent.size());
        assertEquals("CNY",sent.get(0).path("payload").path("currency").asText());
        assertTrue(sent.get(0).path("payload").path("taxIncluded").asBoolean());
        assertEquals("0.0001",sent.get(0).path("payload").path("totalAmount").asText());
    }
    @Test void republishRecoversExpiredOldTargetLeaseAndFencesItsAcknowledgement() {
        String old=frozen("sending","old-target",true);
        tx.executeWithoutResult(status->outbox.republish("settlement",1));
        assertEquals(1,send().size());
        assertEquals("superseded",db.queryForObject("SELECT state FROM mcp_phase3_outbox WHERE message_id=?",String.class,old));
        assertEquals(0,db.update("UPDATE mcp_phase3_outbox SET state='processed' WHERE message_id=? AND lease_id='old-lease'",old));
    }
    @Test void refusesUnscopedBackfill() {
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(status->outbox.backfill(100)));
    }
    @Test void doesNotInterruptAnActiveSenderOrAdvanceItsRevision() {
        frozen("sending","old-target",false);
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(status->outbox.republish("settlement",1)));
        assertEquals(1L,db.queryForObject("SELECT revision FROM mcp_phase3_document WHERE source_id=1",Long.class));
        assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM mcp_phase3_outbox WHERE state='sending' AND lease_id='old-lease'",Long.class));
    }
    @Test void aNewTargetNeverImplicitlyClaimsAnOldTargetsMessage() {
        frozen("sending","old-target",true);
        assertTrue(send().isEmpty());
    }
    @Test void repeatedRepublishRetainsFrozenBasisBeforeAnyNewTransmission() {
        frozen("processed","old-target",true);
        tx.executeWithoutResult(status->outbox.republish("settlement",1));
        tx.executeWithoutResult(status->outbox.republish("settlement",1));
        var sent=send();assertEquals(1,sent.size());
        assertEquals("3",sent.get(0).path("revision").asText());
        assertEquals("CNY",sent.get(0).path("payload").path("currency").asText());
        assertTrue(sent.get(0).path("payload").path("taxIncluded").asBoolean());
    }
    @Test void unsentNewerRevisionRetainsItsOwnContentAndEarlierFrozenBasis() {
        frozen("processed","old-target",true);
        db.update("UPDATE mcp_phase3_document SET revision=2");
        db.update("INSERT INTO mcp_phase3_outbox(message_id,document_type,source_id,revision,operation,source_updated_at,source_payload) VALUES(?,'settlement',1,2,'UPSERT','2026-10-02T00:00:00Z',CAST(? AS JSON))",UUID.randomUUID().toString(),"{\"documentNo\":\"BILL-1\",\"totalAmount\":\"0.0002\",\"items\":[]}");
        tx.executeWithoutResult(status->outbox.republish("settlement",1));
        var sent=send();assertEquals(1,sent.size());
        assertEquals("CNY",sent.get(0).path("payload").path("currency").asText());
        assertEquals("0.0002",sent.get(0).path("payload").path("totalAmount").asText());
    }
    void historicalFixtures() {
        db.update("INSERT INTO supplier VALUES(1,'S1'),(2,'S2')");
        db.update("INSERT INTO product VALUES(1,'P1','支')");
        String[] dates={"2026-10-01 00:00:00","2026-10-02 23:59:59.999999","2026-10-01 12:00:00","2026-09-30 23:59:59.999999","2026-10-03 00:00:00","2026-10-01 12:00:00"};
        for(int i=0;i<dates.length;i++) {
            int supplier=i==2?2:1;
            db.update("INSERT INTO purchase_plan(plan_id,plan_no,supplier_id,product_id,plan_status,planned_quantity,create_time) VALUES(?,?,?,1,?,12,?)",10+i,"PLAN-"+i,supplier,i==5?"draft":"approved",dates[i]);
            db.update("INSERT INTO settlement_bill(settlement_id,settlement_no,supplier_id,status,settlement_period,settlement_mode,total_amount,generate_time) VALUES(?,?,?,'pending_confirm','2026-10','department_consumption',0.0001,?)",20+i,"BILL-"+i,supplier,dates[i]);
            db.update("INSERT INTO settlement_bill_item(item_id,settlement_id,product_id,source_biz_type,source_biz_id,goods_type,quantity,unit_price,amount) VALUES(?,?,1,'department_consumption_item',?,'low_value',1,0.0001,0.0001)",30+i,20+i,40+i);
        }
    }
    Map<String,Object> backfill(int size) {
        return tx.execute(status->outbox.backfill(size,"S1",LocalDate.of(2026,10,1),LocalDate.of(2026,10,2)));
    }
    @Test void backfillHonorsSupplierInclusiveDaysStatusesAndRepeatScope() {
        historicalFixtures();
        assertEquals(Map.of("plans",2,"settlements",3),backfill(100));
        assertEquals(List.of(10L,11L),db.queryForList("SELECT source_id FROM mcp_phase3_outbox WHERE document_type='purchase_plan' ORDER BY source_id",Long.class));
        assertEquals(List.of(20L,21L,25L),db.queryForList("SELECT source_id FROM mcp_phase3_outbox WHERE document_type='settlement' ORDER BY source_id",Long.class));
        assertEquals(Map.of("plans",0,"settlements",0),backfill(100));
    }
    @Test void backfillBatchLimitDoesNotEscapeSelectedScope() {
        historicalFixtures();
        assertEquals(Map.of("plans",1,"settlements",1),backfill(1));
        assertEquals(Map.of("plans",1,"settlements",1),backfill(1));
        assertEquals(Map.of("plans",0,"settlements",1),backfill(1));
        assertEquals(Map.of("plans",0,"settlements",0),backfill(1));
        assertEquals(5L,db.queryForObject("SELECT COUNT(*) FROM mcp_phase3_outbox",Long.class));
    }
    @Test void backfillRejectsUnknownSupplierMissingDatesAndReversedDates() {
        historicalFixtures();var day=LocalDate.of(2026,10,1);
        assertThrows(IllegalArgumentException.class,()->outbox.backfill(100,"UNKNOWN",day,day));
        assertThrows(IllegalArgumentException.class,()->outbox.backfill(100,"S1",null,day));
        assertThrows(IllegalArgumentException.class,()->outbox.backfill(100,"S1",day.plusDays(1),day));
        assertThrows(IllegalArgumentException.class,()->outbox.backfill(501,"S1",day,day));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM mcp_phase3_document",Long.class));
    }
}
