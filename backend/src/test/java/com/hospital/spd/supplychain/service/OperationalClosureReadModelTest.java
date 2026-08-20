package com.hospital.spd.supplychain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalClosureReadModelTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private OperationalClosureReadModel readModel;

    @BeforeEach
    void setUp() {
        readModel = new OperationalClosureReadModel(jdbcTemplate);
    }

    @Test
    @DisplayName("overview returns summary counts and recent inventory events")
    void shouldReturnOverview() {
        when(jdbcTemplate.queryForObject(contains("shortage_replenishment_task"), eq(Integer.class))).thenReturn(3);
        when(jdbcTemplate.queryForObject(contains("spd_delivery_order"), eq(Integer.class))).thenReturn(5);
        when(jdbcTemplate.queryForObject(contains("department_consumption"), eq(Integer.class))).thenReturn(10);
        when(jdbcTemplate.queryForObject(contains("settlement_bill"), eq(Integer.class))).thenReturn(2);
        when(jdbcTemplate.queryForObject(contains("pda_offline_record"), eq(Integer.class))).thenReturn(8);
        when(jdbcTemplate.queryForObject(contains("cold_chain_exception"), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("recall_event"), eq(Integer.class))).thenReturn(2);
        when(jdbcTemplate.queryForList(contains("FROM inventory_event")))
                .thenReturn(List.of(Map.of("eventNo", "EVT001", "eventType", "delivery_out")));

        Map<String, Object> result = readModel.overview();

        assertThat(result).containsKeys("summary", "events");
        assertThat(summary(result)).containsEntry("riskEvents", 3);
        assertThat(events(result)).hasSize(1);
    }

    @Test
    @DisplayName("options returns all closure dropdown datasets")
    void shouldReturnOptions() {
        when(jdbcTemplate.queryForList(contains("FROM sys_dept"))).thenReturn(List.of(Map.of("deptName", "Surgery")));
        when(jdbcTemplate.queryForList(contains("FROM warehouse"))).thenReturn(List.of(Map.of("warehouseName", "Main")));
        when(jdbcTemplate.queryForList(contains("FROM product p"))).thenReturn(List.of(Map.of("productCode", "PC001")));
        when(jdbcTemplate.queryForList(contains("FROM inventory_balance bal")))
                .thenReturn(List.of(Map.of("availableQty", BigDecimal.TEN)));

        Map<String, Object> result = readModel.options();

        assertThat(result).containsKeys("departments", "warehouses", "products", "balances");
    }

    @Test
    @DisplayName("list returns paged rows for the requested closure type")
    void shouldReturnPagedRows() {
        when(jdbcTemplate.queryForList(contains("shortage_replenishment_task"), eq(10), eq(10)))
                .thenReturn(List.of(Map.of("bizNo", "QH001")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);

        Map<String, Object> page = readModel.list("shortage", Map.of("page", "2", "size", "10"));

        assertThat(rows(page)).containsExactly(Map.of("bizNo", "QH001"));
        assertThat(page).containsEntry("total", 1L);
        verify(jdbcTemplate).queryForList(contains("shortage_replenishment_task"), eq(10), eq(10));
    }

    @Test
    @DisplayName("cold-chain list returns exception rows from the cold chain table")
    void shouldReturnColdChainExceptionRows() {
        when(jdbcTemplate.queryForList(contains("FROM cold_chain_exception"), eq(20), eq(0)))
                .thenReturn(List.of(Map.of("bizNo", "CC001")));
        when(jdbcTemplate.queryForObject(contains("cold_chain_exception"), eq(Long.class))).thenReturn(1L);

        Map<String, Object> page = readModel.list("cold-chain", Map.of("page", "1", "size", "20"));

        assertThat(rows(page)).containsExactly(Map.of("bizNo", "CC001"));
        assertThat(page).containsEntry("total", 1L);
    }

    @Test
    @DisplayName("red-flush list reads durable red-flush records instead of consumption rows")
    void shouldReturnRedFlushRows() {
        when(jdbcTemplate.queryForList(contains("FROM consumption_red_flush"), eq(20), eq(0)))
                .thenReturn(List.of(Map.of("bizNo", "FXH001", "sourceNo", "XH001")));
        when(jdbcTemplate.queryForObject(contains("consumption_red_flush"), eq(Long.class))).thenReturn(1L);

        Map<String, Object> page = readModel.list("red-flush", Map.of("page", "1", "size", "20"));

        assertThat(rows(page)).containsExactly(Map.of("bizNo", "FXH001", "sourceNo", "XH001"));
        assertThat(page).containsEntry("total", 1L);
    }

    @Test
    @DisplayName("requisition list returns order summary rows")
    void shouldReturnRequisitionOrderSummaries() {
        when(jdbcTemplate.queryForList(argThat(sql -> sql.contains("SUM(dri.quantity)")
                        && sql.contains("dr.warehouse_id")), eq(20), eq(0)))
                .thenReturn(List.of(Map.of("bizNo", "SL001", "totalQuantity", BigDecimal.TEN)));
        when(jdbcTemplate.queryForObject(contains("FROM department_requisition dr"), eq(Long.class))).thenReturn(1L);

        Map<String, Object> page = readModel.list("requisition", Map.of("page", "1", "size", "20"));

        assertThat(rows(page)).containsExactly(Map.of("bizNo", "SL001", "totalQuantity", BigDecimal.TEN));
        assertThat(page).containsEntry("total", 1L);
    }

    @Test
    @DisplayName("requisition details returns catalog and amount fields")
    void shouldReturnRequisitionDetails() {
        when(jdbcTemplate.queryForList(contains("p.registration_no"), eq("SL001")))
                .thenReturn(List.of(Map.of("productCode", "PC001", "applyAmount", BigDecimal.TEN)));

        Map<String, Object> result = readModel.requisitionDetails("SL001");

        assertThat(rows(result)).containsExactly(Map.of("productCode", "PC001", "applyAmount", BigDecimal.TEN));
        assertThat(result).containsEntry("requisitionNo", "SL001");
    }

    @Test
    @DisplayName("settlement list returns item-level reconciliation details")
    void shouldReturnSettlementDetails() {
        when(jdbcTemplate.queryForList(argThat(sql -> sql.contains("sb.settlement_id AS settlementId")
                        && sql.contains("p.generic_name")
                        && sql.contains("tc.package_label_no")
                        && sql.contains("sd.finance_dept_name")
                        && sql.contains("tc.patient_name_masked")),
                eq(20), eq(0)))
                .thenReturn(List.of(Map.of(
                        "settlementId", 7L,
                        "settlementNo", "JS001",
                        "productCode", "P001",
                        "settlementAmount", BigDecimal.TEN
                )));
        when(jdbcTemplate.queryForObject(contains("FROM settlement_bill_item sbi"), eq(Long.class)))
                .thenReturn(1L);

        Map<String, Object> page = readModel.list("settlement", Map.of("page", "1", "size", "20"));

        assertThat(rows(page)).hasSize(1);
        assertThat(rows(page).get(0)).containsEntry("settlementNo", "JS001");
        assertThat(page).containsEntry("total", 1L);
    }

    @Test
    @DisplayName("high-value list returns charge details with catalog and patient filters")
    void shouldReturnHighValueChargeDetails() {
        when(jdbcTemplate.queryForObject(contains("FROM high_value_charge hvc"), eq(Long.class), eq("%MZ001%"),
                eq("%UID001%"), eq("%UID001%"), eq("%ACME%"), eq("%REG001%"), eq("2026-07-01 00:00:00"),
                eq("2026-07-07 23:59:59"))).thenReturn(1L);
        when(jdbcTemplate.queryForList(argThat(sql -> sql.contains("p.registration_no AS registrationNo")
                        && sql.contains("utc.patient_name_masked")
                        && sql.contains("ORDER BY COALESCE(hvc.charge_time, hvc.create_time) DESC")),
                eq("%MZ001%"), eq("%UID001%"), eq("%UID001%"), eq("%ACME%"), eq("%REG001%"),
                eq("2026-07-01 00:00:00"), eq("2026-07-07 23:59:59"), eq(20), eq(0)))
                .thenReturn(List.of(Map.of(
                        "bizNo", "HV001",
                        "patientNo", "MZ001",
                        "productCode", "P001",
                        "chargeQuantity", BigDecimal.ONE,
                        "unitPrice", BigDecimal.TEN
                )));
        when(jdbcTemplate.queryForList(contains("SUM(hvc.quantity)"),
                eq("%MZ001%"), eq("%UID001%"), eq("%UID001%"), eq("%ACME%"), eq("%REG001%"),
                eq("2026-07-01 00:00:00"), eq("2026-07-07 23:59:59")))
                .thenReturn(List.of(Map.of("totalQuantity", BigDecimal.ONE, "totalAmount", BigDecimal.TEN)));

        Map<String, Object> page = readModel.list("high-value", Map.of(
                "patientNo", "MZ001",
                "uid", "UID001",
                "supplierName", "ACME",
                "registrationNo", "REG001",
                "dateFrom", "2026-07-01",
                "dateTo", "2026-07-07"
        ));

        assertThat(rows(page)).hasSize(1);
        assertThat(page).containsEntry("total", 1L);
        assertThat(summary(page)).containsEntry("totalAmount", BigDecimal.TEN);
    }

    @Test
    @DisplayName("list returns an empty page for unknown closure types")
    void shouldReturnEmptyPageForUnknownType() {
        Map<String, Object> page = readModel.list("unknown", Map.of("page", "1", "size", "20"));

        assertThat(rows(page)).isEmpty();
        assertThat(page).containsEntry("total", 0L);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summary(Map<String, Object> result) {
        return (Map<String, Object>) result.get("summary");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> events(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("events");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("rows");
    }
}
