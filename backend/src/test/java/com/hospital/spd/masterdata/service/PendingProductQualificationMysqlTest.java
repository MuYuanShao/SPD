package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.licenses.LicenseService;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests current qualification links and real file reads in a rolled-back MySQL fixture. */
@EnabledIfEnvironmentVariable(named="SPD_QUALIFICATION_MYSQL_TESTS", matches="true")
class PendingProductQualificationMysqlTest {
    @TempDir Path uploads;

    @Test void reviewsApplicationAndLinkedLicensesButRejectsUnrelatedOrDeletedFiles() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/ISPD?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("SPD_DB_USERNAME"), System.getenv("SPD_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                var product = jdbc.queryForMap("SELECT product_id,product_code,supplier_id,manufacturer_id FROM product WHERE deleted=0 AND supplier_id IS NOT NULL AND manufacturer_id IS NOT NULL LIMIT 1");
                String no="QA-"+UUID.randomUUID().toString().substring(0,12);
                jdbc.update("INSERT INTO pending_product_application (application_no,application_type,product_code,supplier_id,manufacturer_id,contract_code,product_snapshot,submit_by,approval_status) VALUES (?,'资质更新',?,?,?,?,'{}',1,'pending_step_1')",
                        no,product.get("product_code"),product.get("supplier_id"),product.get("manufacturer_id"),no);
                var service=new PendingProductAttachmentService(jdbc,OperatorContext::system,new ApprovalFlowGuard(jdbc),uploads.toString());
                var licenses=new LicenseService(jdbc,OperatorContext::system,uploads.toString());
                long own=((Number)service.upload(no,new MockMultipartFile("file",no+".pdf","application/pdf","%PDF-1.7 own".getBytes())).get("attachmentId")).longValue();
                List<Long> allowed=new ArrayList<>(List.of(own));
                for (String type : List.of("product","supplier","manufacturer","contract")) {
                    String ownerType=type.equals("contract")?"supplier":type;
                    Object owner=product.get(ownerType.equals("product")?"product_id":ownerType+"_id");
                    Long license=license(jdbc,type,ownerType,owner,no,no+"-"+type);
                    allowed.add(((Number)licenses.uploadAttachment(license,new MockMultipartFile("file",no+"-"+type+".pdf","application/pdf","%PDF-1.7 linked".getBytes()),"license").get("attachmentId")).longValue());
                }
                Long unrelated=license(jdbc,"product","product",Long.MAX_VALUE-1,no,no+"-unrelated");
                long unrelatedFile=((Number)licenses.uploadAttachment(unrelated,new MockMultipartFile("file","unrelated.pdf","application/pdf","%PDF-1.7".getBytes()),"license").get("attachmentId")).longValue();
                Long otherContract=license(jdbc,"contract","supplier",product.get("supplier_id"),no+"-OTHER",no+"-other-contract");
                long otherFile=((Number)licenses.uploadAttachment(otherContract,new MockMultipartFile("file","other.pdf","application/pdf","%PDF-1.7".getBytes()),"license").get("attachmentId")).longValue();
                var linked=service.qualificationAttachments(no);
                var ids=linked.stream().map(r->((Number)r.get("attachmentId")).longValue()).toList();
                assertThat(ids).containsAll(allowed).doesNotContain(unrelatedFile,otherFile);
                for(long id:allowed) {
                    var response=service.previewQualification(no,id);
                    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
                    try(var stream=response.getBody().getInputStream()) { assertThat(new String(stream.readAllBytes())).startsWith("%PDF-"); }
                }
                assertThatThrownBy(()->service.previewQualification(no,unrelatedFile)).hasMessageContaining("未关联");
                assertThatThrownBy(()->service.previewQualification(no,otherFile)).hasMessageContaining("未关联");
                jdbc.update("UPDATE sys_attachment SET deleted=1 WHERE attachment_id=?",own);
                assertThatThrownBy(()->service.previewQualification(no,own)).hasMessageContaining("已删除");
                jdbc.update("UPDATE license_document SET deleted=1 WHERE license_id=(SELECT biz_id FROM sys_attachment WHERE attachment_id=?)",allowed.get(1));
                assertThatThrownBy(()->service.previewQualification(no,allowed.get(1))).hasMessageContaining("已删除");
                var denied=mock(ApprovalFlowGuard.class);
                doThrow(new IllegalArgumentException("无审批权限")).when(denied).requireApprovalAccess(anyString(),anyString(),anyInt(),isNull(),any());
                var outsider=new PendingProductAttachmentService(jdbc,()->new OperatorContext(999999L,"outsider","127.0.0.1",List.of("ROLE_DEPT"),99999L,2),denied,uploads.toString());
                assertThatThrownBy(()->outsider.previewQualification(no,allowed.get(2))).hasMessageContaining("无审批权限");
                System.out.println("QUALIFICATION VERIFIED: application/product/supplier/manufacturer/contract files readable; unrelated/deleted/unauthorized files denied; fixture rolled back");
            } finally { connection.rollback(); }
        }
    }
    private Long license(JdbcTemplate jdbc,String type,String ownerType,Object owner,String number,String name) {
        jdbc.update("INSERT INTO license_document (license_type,license_name,license_no,owner_type,owner_id,owner_name,status) VALUES (?,?,?,?,?,?,1)",type,name,number,ownerType,owner,"测试主体");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }
}
