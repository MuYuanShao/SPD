package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.PendingProductApplicationDetail;
import com.hospital.spd.masterdata.PendingProductApplicationRow;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The 待审批目录 list and detail must no longer carry a 规格型号 (spec_model) value.
 * The pending application flow no longer collects a specification, so the row, the
 * detail and the list SQL must stay free of it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductApprovalRowSpecModelTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void listRowHasNoSpecModelComponent() {
        assertThat(PendingProductApplicationRow.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("specModel");
    }

    @Test
    void detailHasNoSpecModelComponent() {
        assertThat(PendingProductApplicationDetail.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("specModel");
    }

    @Test
    void listQueryDoesNotSelectSpecModelColumn() {
        ProductApprovalService service = new ProductApprovalService(jdbcTemplate);
        when(jdbcTemplate.query(contains("SELECT a.application_no"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("FROM pending_product_application a"), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.query(contains("application_type, COUNT(*)"),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .thenReturn(new java.util.HashMap<String, Integer>());
        when(jdbcTemplate.query(contains("SUM(CASE WHEN"),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.ResultSetExtractor.class),
                any(Object[].class)))
                .thenReturn(new java.util.HashMap<String, Integer>());

        service.listApplications("new", "todo", "", "");

        verify(jdbcTemplate, never()).query(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("a.spec_model")),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    void mapperReadsRowWithoutSpecModel() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("application_no")).thenReturn("SP20261127012");
        when(rs.getString("application_type")).thenReturn("新品准入");
        when(rs.getString("product_name")).thenReturn("测试商品ABC");
        when(rs.getString("supplier_name")).thenReturn("供应商A");
        when(rs.getLong("submit_by")).thenReturn(1L);
        when(rs.getTimestamp("submit_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 27, 10, 28)));
        when(rs.getString("approval_status")).thenReturn("pending_initial");
        when(rs.getString("product_code")).thenReturn("SPD000017");
        when(rs.getString("manufacturer_name")).thenReturn("厂家A");
        when(rs.getTimestamp("create_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 27, 10, 28)));
        when(rs.getTimestamp("update_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 27, 10, 28)));

        PendingProductApplicationRow row = ProductApprovalMapper.mapRow(rs);

        assertThat(row.productCode()).isEqualTo("SPD000017");
        assertThat(row.product()).isEqualTo("测试商品ABC");
        assertThat(row.no()).isEqualTo("SP20261127012");
        assertThat(row.manufacturerName()).isEqualTo("厂家A");
    }
}
