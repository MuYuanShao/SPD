package com.hospital.spd.licenses;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.integration.McpLicenseLifecycleService;
import com.hospital.spd.integration.Phase3Json;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real migrations, approved-version projection, file reads and rollback in an owned disposable database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="SPD_PHASE3_REVIEW_TESTS",matches="true")
class McpLicenseLedgerMysqlTest {
    @TempDir static Path uploads;
    JdbcTemplate db;
    TransactionTemplate tx;
    LicenseService licenses;
    long source=910000,product;
    String code;
    long migrationLicense;
    @BeforeAll void migrate() throws Exception {
        String url=System.getenv("SPD_PHASE3_REVIEW_URL");assertTrue(url.contains("/spd_phase3_review?"));
        var ds=new DriverManagerDataSource(url,"root",System.getenv("SPD_PHASE3_REVIEW_PASSWORD"));
        db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        Flyway.configure().dataSource(ds).target("104").load().migrate();
        licenses=new LicenseService(db,OperatorContext::system,uploads.toString());
        Files.createDirectories(uploads.resolve("pending-products"));
        fixture();
        long attachment=file("legacy.pdf");
        intent("LEGACY",attachment,"active","OLD","2098-01-01","2026-01-01T00:00:00Z");
        db.update("""
                INSERT INTO mcp_license_approved(source_product_id,source_license_id,product_code,license_code,attachment_id,metadata,application_no,source_updated_at)
                SELECT source_product_id,source_license_id,product_code,license_code,attachment_id,metadata,application_no,source_updated_at
                FROM mcp_license_intent WHERE application_no='LEGACY'
                """);
        Flyway.configure().dataSource(ds).load().migrate();migrationLicense=id();
    }
    void fixture() {
        source++;code="MCP-LEDGER-"+source;
        db.update("INSERT INTO product(product_code,product_name,spec_model,category_id,unit) VALUES(?,?,'1ml',(SELECT MIN(category_id) FROM product_category),'支')",code,"来源证照测试"+source);
        product=db.queryForObject("SELECT product_id FROM product WHERE product_code=?",Long.class,code);
    }
    long file(String name) throws Exception {
        String stored=UUID.randomUUID()+".pdf";Files.writeString(uploads.resolve("pending-products").resolve(stored),"%PDF-1.4 "+name);
        db.update("INSERT INTO sys_attachment(biz_type,biz_id,file_name,file_ext,file_type,file_size,file_path,file_url) VALUES('pending_product_application',1,?,'pdf','application/pdf',20,?,'')",name,stored);
        return db.queryForObject("SELECT attachment_id FROM sys_attachment WHERE file_path=?",Long.class,stored);
    }
    void intent(String app,Long attachment,String status,String number,String expiry,String time) {
        var metadata=Map.of("licenseName","MCP注册证","licenseCode",number,"status",status,"issueDate","2026-01-01","expiryDate",expiry);
        db.update("INSERT INTO mcp_license_intent(application_no,source_product_id,source_license_id,product_code,license_code,attachment_id,metadata,source_updated_at) VALUES(?,?,'L1',?,?,?,?,?)",app,source,code,number,attachment,Phase3Json.json(metadata),time);
    }
    void approve(String app) {
        tx.executeWithoutResult(status->{var flow=new McpLicenseLifecycleService(db);flow.prepareApproval(app);flow.publishApproval(app,OperatorContext::system);});
    }
    long id(){return db.queryForObject("SELECT license_id FROM license_document WHERE source_product_id=? AND source_license_id='L1'",Long.class,source);}
    int revision(){return db.queryForObject("SELECT revision_no FROM license_document WHERE license_id=?",Integer.class,id());}
    @Test void migrationBackfillsAlreadyApprovedCertificatesAndHistory() throws Exception {
        assertEquals("OLD",licenses.detail(migrationLicense).get("licenseNo"));
        assertEquals(1,licenses.attachments(migrationLicense).size());
        var attachment=((Number)licenses.attachments(migrationLicense).get(0).get("id")).longValue();
        assertTrue(new String(licenses.downloadAttachment(attachment).getBody().getInputStream().readAllBytes()).contains("legacy.pdf"));
        assertTrue(licenses.history(migrationLicense).toString().contains("mcp_import"));
    }
    @Test void freshApprovalCreatesQueryableLedgerAndReadablePrivateAttachment() throws Exception {
        fixture();intent("FIRST",file("first.pdf"),"active","REG1","2098-01-01","2026-01-02T00:00:00Z");
        assertEquals(0,((Number)licenses.list("product",code,1,20).get("total")).intValue());
        approve("FIRST");assertEquals(1,((Number)licenses.list("product",code,1,20).get("total")).intValue());
        assertEquals("MCP-UI",licenses.detail(id()).get("sourceSystem"));
        var files=licenses.attachments(id());assertEquals(1,files.size());
        assertTrue(new String(licenses.downloadAttachment(((Number)files.get(0).get("id")).longValue()).getBody().getInputStream().readAllBytes()).contains("first.pdf"));
    }
    @Test void renewalKeepsIdentityAndOldSnapshotButReplacesCurrentAttachment() throws Exception {
        fixture();intent("A",file("old.pdf"),"active","REG-OLD","2098-01-01","2026-01-02T00:00:00Z");approve("A");
        long id=id(),old=((Number)licenses.attachments(id).get(0).get("id")).longValue();
        intent("B",file("new.pdf"),"active","REG-NEW","2099-01-01","2026-01-03T00:00:00Z");approve("B");
        assertEquals(id,id());assertEquals(2,revision());assertEquals(1,licenses.attachments(id).size());
        assertThrows(IllegalArgumentException.class,()->licenses.downloadAttachment(old));
        assertTrue(new String(licenses.downloadHistoryAttachment(id,1,old).getBody().getInputStream().readAllBytes()).contains("old.pdf"));
        assertThrows(IllegalArgumentException.class,()->licenses.downloadHistoryAttachment(id,2,old));
        assertTrue(licenses.history(id).toString().contains("REG-OLD"));
    }
    @Test void replayOfPublishedApprovalIsIdempotent() throws Exception {
        fixture();intent("REPLAY",file("replay.pdf"),"active","REG","2099-01-01","2026-01-02T00:00:00Z");approve("REPLAY");
        tx.executeWithoutResult(status->new McpLicenseLifecycleService(db).publishApproval("REPLAY",OperatorContext::system));
        assertEquals(1,revision());assertEquals(1,licenses.attachments(id()).size());
    }
    @Test void rejectedOrUnpublishedIntentCannotChangeLedger() throws Exception {
        fixture();intent("APPROVED",file("approved.pdf"),"active","REG","2098-01-01","2026-01-02T00:00:00Z");approve("APPROVED");
        intent("REJECTED",file("rejected.pdf"),"active","REJECTED","2099-01-01","2026-01-03T00:00:00Z");
        assertEquals(1,revision());assertEquals("REG",licenses.detail(id()).get("licenseNo"));
    }
    @Test void approvalRollbackDoesNotPublishLedgerOrApprovedSource() throws Exception {
        fixture();intent("ROLLBACK",file("rollback.pdf"),"active","REG","2098-01-01","2026-01-02T00:00:00Z");
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{new McpLicenseLifecycleService(db).publishApproval("ROLLBACK",OperatorContext::system);throw new IllegalStateException("rollback");}));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM license_document WHERE source_product_id=?",Long.class,source));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM mcp_license_approved WHERE source_product_id=?",Long.class,source));
    }
    @Test void sourceDocumentsCannotBypassCatalogApprovalThroughLocalWrites() throws Exception {
        fixture();intent("OWNED",file("owned.pdf"),"active","REG","2098-01-01","2026-01-02T00:00:00Z");approve("OWNED");long id=id();
        var request=new LicenseUpsertRequest("product","改写","NEW","product",product,code,"来源证照",null,null,null,"2026-01-01","2099-01-01",1,"",1);
        assertThrows(IllegalArgumentException.class,()->licenses.update(id,request));
        assertThrows(IllegalArgumentException.class,()->licenses.renew(id,request));
        assertThrows(IllegalArgumentException.class,()->licenses.remove(id));
        assertThrows(IllegalArgumentException.class,()->licenses.uploadAttachment(id,new MockMultipartFile("file","x.pdf","application/pdf",new byte[]{1}),"license"));
        assertEquals(1,revision());
    }
    @Test void unlinkRemovesEffectiveLedgerAndRelinkReusesSameIdentity() throws Exception {
        fixture();intent("LINK",file("link.pdf"),"active","REG","2098-01-01","2026-01-02T00:00:00Z");approve("LINK");long id=id();
        intent("UNLINK",null,"unlinked","REG","2098-01-01","2026-01-03T00:00:00Z");approve("UNLINK");
        assertEquals(0,((Number)licenses.list("product",code,1,20).get("total")).intValue());
        intent("RELINK",file("relink.pdf"),"active","REG","2099-01-01","2026-01-04T00:00:00Z");approve("RELINK");
        assertEquals(id,id());assertEquals(3,revision());assertEquals(1,licenses.attachments(id).size());
    }
    @Test void hospitalPreviewRequiresCurrentAttachmentOwnershipAndRejectsPendingFiles() throws Exception {
        fixture();long pending=file("hospital.pdf");
        db.update("""
                INSERT INTO sys_attachment(biz_type,biz_id,file_name,file_ext,file_type,file_size,file_path,file_url,source_attachment_id)
                SELECT 'product',?,file_name,file_ext,file_type,file_size,file_path,file_url,attachment_id FROM sys_attachment WHERE attachment_id=?
                """,product,pending);
        long attachment=db.queryForObject("SELECT attachment_id FROM sys_attachment WHERE biz_type='product' AND biz_id=?",Long.class,product);
        var files=new com.hospital.spd.masterdata.service.HospitalProductAttachmentService(db,uploads.toString());
        assertTrue(new String(files.file(code,attachment).getBody().getInputStream().readAllBytes()).contains("hospital.pdf"));
        assertThrows(IllegalArgumentException.class,()->files.file("OTHER-PRODUCT",attachment));
        assertThrows(IllegalArgumentException.class,()->files.file(code,pending));
        db.update("UPDATE sys_attachment SET deleted=1 WHERE attachment_id=?",attachment);
        assertThrows(IllegalArgumentException.class,()->files.file(code,attachment));
    }
}
