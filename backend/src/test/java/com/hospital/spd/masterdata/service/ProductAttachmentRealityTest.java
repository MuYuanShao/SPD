package com.hospital.spd.masterdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hospital.spd.masterdata.ProductAttachment;
import com.hospital.spd.masterdata.ProductDetail;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ProductAttachmentRealityTest {
    @Test
    void certificateNumbersWithoutUploadedFilesDoNotCreatePretendAttachments() throws Exception {
        ProductDetail detail = detail(List.of());
        assertThat(detail.registrationNo()).isEqualTo("REG-NO-FILE");
        assertThat(detail.attachments()).isEmpty();
    }

    @Test
    void keepsRealPersistedAttachmentsWithTheirDownloadAddress() throws Exception {
        ProductAttachment file = new ProductAttachment("real.pdf", "注册证", "可查看", "/attachments/42/file", "2029-12-31");
        assertThat(detail(List.of(file)).attachments()).containsExactly(file);
    }

    @SuppressWarnings("unchecked")
    private ProductDetail detail(List<ProductAttachment> attachments) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("product_id")).thenReturn(42L);
        when(row.getString("registration_no")).thenReturn("REG-NO-FILE");
        when(row.getString("production_license_no")).thenReturn("PROD-NO-FILE");
        when(row.getString("business_license_no")).thenReturn("BUSINESS-NO-FILE");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(attachments);
        doAnswer(invocation -> ((RowMapper<ProductDetail>) invocation.getArgument(1)).mapRow(row, 0))
                .when(jdbc).queryForObject(anyString(), any(RowMapper.class), eq("AB-PRODUCT"));
        return new ProductService(jdbc).hospitalProductDetail("AB-PRODUCT");
    }
}
