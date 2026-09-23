package com.hospital.spd.licenses;

import com.hospital.spd.common.OperatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Opt-in SQL integration: all fixture rows are isolated in one uncommitted transaction and rolled back. */
@EnabledIfEnvironmentVariable(named="SPD_LICENSE_MYSQL_TESTS", matches="true")
class LicenseLifecycleMysqlTest {
    @TempDir Path uploads;
    @Test void subjectHistoryRenewalEligibilityAndDeletedAttachmentAccess() throws Exception {
        String url = System.getenv().getOrDefault("SPD_LICENSE_TEST_JDBC_URL", "jdbc:mysql://127.0.0.1:3306/ISPD?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        try (var connection = DriverManager.getConnection(url, System.getenv("SPD_DB_USERNAME"), System.getenv("SPD_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                String code = "LC-" + UUID.randomUUID().toString().substring(0, 12);
                jdbc.update("INSERT INTO supplier (supplier_code,supplier_name,credit_code,supplier_type,contact_name,contact_phone,status,deleted) VALUES (?,?,?,'test','测试','000',1,0)", code, "证照回滚测试主体", code);
                Long ownerId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                Long categoryId = jdbc.queryForObject("SELECT MIN(category_id) FROM product_category", Long.class);
                jdbc.update("INSERT INTO product (product_code,product_name,spec_model,category_id,unit,supplier_id) VALUES (?,?,'6.0',?,'个',?)", code, "证照关联测试商品", categoryId, ownerId);
                Long productId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                jdbc.update("INSERT INTO purchase_order (order_no,supplier_id,order_source,order_status,total_amount) VALUES (?,?,'manual','draft',1)", code, ownerId);
                Long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                jdbc.update("INSERT INTO purchase_order_item (purchase_order_id,product_id,quantity,unit,estimated_unit_price,amount,received_quantity) VALUES (?,?,1,'个',1,1,0)", orderId, productId);
                var purchases = new com.hospital.spd.supplychain.service.PurchaseOrderService(jdbc, org.mockito.Mockito.mock(com.hospital.spd.supplychain.SupplyChainSupport.class));
                var service = new LicenseService(jdbc, OperatorContext::system, uploads.toString());
                var gate = new LicenseEligibilityService(jdbc);
                String oldExpiry = LocalDate.now().minusDays(1).toString();
                String nextExpiry = LocalDate.now().plusYears(1).toString();
                Long id = (Long) service.create(request(ownerId, code, oldExpiry, null)).get("id");
                Long attachmentId = (Long) service.uploadAttachment(id, new MockMultipartFile("file", "原证照.pdf", "application/pdf", "%PDF-1.7 test".getBytes()), "license").get("attachmentId");
                String storedName = jdbc.queryForObject("SELECT file_path FROM sys_attachment WHERE attachment_id=?", String.class, attachmentId);
                assertThat(service.downloadAttachmentByStoredName(storedName).getHeaders().getContentType().toString()).isEqualTo("application/pdf");
                assertThatThrownBy(() -> gate.requireEligible("TEST-UNUSED", ownerId, null, null)).hasMessageContaining("已过期");
                assertThatThrownBy(() -> purchases.performAction(code, new com.hospital.spd.supplychain.PurchaseOrderActionRequest("submit", null))).hasMessageContaining("已过期");
                assertThat(jdbc.queryForObject("SELECT order_status FROM purchase_order WHERE order_no=?", String.class, code)).isEqualTo("draft");
                service.update(id, request(null, code, oldExpiry, 2));
                assertThat(((Number) service.detail(id).get("ownerId")).longValue()).isEqualTo(ownerId);
                assertThat(service.detail(id).get("ownerName")).isEqualTo("证照回滚测试主体");
                assertThatThrownBy(() -> service.update(id, request(ownerId, code, oldExpiry, 2))).hasMessageContaining("其他用户修改");
                service.renew(id, request(null, code, nextExpiry, 3));
                assertThatCode(() -> gate.requireEligible("TEST-UNUSED", ownerId, null, null)).doesNotThrowAnyException();
                assertThatCode(() -> purchases.performAction(code, new com.hospital.spd.supplychain.PurchaseOrderActionRequest("submit", null))).doesNotThrowAnyException();
                assertThat(jdbc.queryForObject("SELECT order_status FROM purchase_order WHERE order_no=?", String.class, code)).isEqualTo("pending_approval");
                var history = (List<Map<String,Object>>) service.history(id).get("history");
                assertThat(history).hasSize(4);
                assertThat(history.get(0).get("operationType")).isEqualTo("renew");
                assertThat(String.valueOf(history.get(1).get("snapshotJson"))).contains(oldExpiry).contains("原证照.pdf");
                assertThatThrownBy(() -> service.renew(id, request(ownerId, code, oldExpiry, 4))).hasMessageContaining("今天或之后");
                jdbc.update("UPDATE license_document SET status=0 WHERE license_id=?", id);
                assertThatThrownBy(() -> gate.requireEligible("TEST-UNUSED", ownerId, null, null)).hasMessageContaining("失效");
                jdbc.update("UPDATE license_document SET status=1, issue_date=? WHERE license_id=?", LocalDate.now().plusDays(1), id);
                assertThatThrownBy(() -> gate.requireEligible("TEST-UNUSED", ownerId, null, null)).hasMessageContaining("尚未生效");
                jdbc.update("UPDATE license_document SET issue_date=NULL WHERE license_id=?", id);
                service.remove(id);
                assertThatThrownBy(() -> service.downloadAttachmentByStoredName(storedName)).hasMessageContaining("附件不存在或已删除");
                assertThatThrownBy(() -> service.downloadAttachment(attachmentId)).hasMessageContaining("附件不存在或已删除");
                jdbc.update("UPDATE sys_attachment SET deleted=0 WHERE attachment_id=?", attachmentId);
                assertThatThrownBy(() -> service.downloadAttachment(attachmentId)).hasMessageContaining("附件不存在或已删除");
            } finally { connection.rollback(); }
        }
    }
    private LicenseUpsertRequest request(Long id, String code, String expiry, Integer revision) {
        return new LicenseUpsertRequest("supplier", "经营证照", "REG-TEST", "supplier", id, code, "不可信名称",
                null,null,null,null,expiry,1,"回滚测试",revision);
    }
}
