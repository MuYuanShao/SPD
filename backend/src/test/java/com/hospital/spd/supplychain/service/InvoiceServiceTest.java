package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.common.service.DocumentNumberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private DocumentNumberService documentNumberService;
    @Mock private AuditLogService auditLogService;
    private InvoiceService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(jdbcTemplate, documentNumberService, auditLogService);
    }

    @Test
    void listsInvoicesFromDedicatedInvoiceTable() {
        when(jdbcTemplate.queryForList(contains("FROM supplier_invoice si"), eq(20), eq(0))).thenReturn(List.of(Map.of("invoiceNo", "FP001")));
        when(jdbcTemplate.queryForObject(contains("supplier_invoice"), eq(Long.class))).thenReturn(1L);
        Map<String, Object> page = service.list(Map.of("page", "1", "size", "20"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) page.get("rows");
        assertThat(rows).containsExactly(Map.of("invoiceNo", "FP001"));
        assertThat(page).containsEntry("total", 1L);
    }

    @Test
    void createsInvoiceWithSharedDocumentNumberAndAudit() {
        when(documentNumberService.next(DocumentKind.SUPPLIER_INVOICE)).thenReturn("FP2026072200001");
        when(jdbcTemplate.queryForObject(contains("FROM supplier"), eq(Long.class), eq(1L))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("FROM supplier_invoice"), eq(Long.class), eq("FP2026072200001"))).thenReturn(9L);
        Map<String, Object> result = service.create(Map.of(
                "supplierId", 1, "invoiceCode", "3100", "invoiceNumber", "000001",
                "invoiceDate", "2026-07-22", "amount", new BigDecimal("100.00"), "taxAmount", new BigDecimal("13.00")));
        assertThat(result).containsEntry("invoiceNo", "FP2026072200001");
        verify(jdbcTemplate).update(contains("INSERT INTO supplier_invoice"),
                eq("FP2026072200001"), eq(1L), eq("3100"), eq("000001"), eq(java.sql.Date.valueOf("2026-07-22")),
                eq(new BigDecimal("100.00")), eq(new BigDecimal("13.00")), eq(null), eq("pending_verification"), eq(null));
        verify(auditLogService).record("supplier_invoice", "create", 9L, "FP2026072200001", "supplier invoice created");
    }
}
