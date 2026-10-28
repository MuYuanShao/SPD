package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalHighValueModuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private OperationalHighValueModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalHighValueModule(jdbcTemplate, support);
    }

    @Test
    void sameExternalNumberIsResolvedWithinItsSourceSystem() {
        for (String source : List.of("AIMS", "HIS")) {
            when(jdbcTemplate.queryForList(contains("FROM high_value_charge"), eq(source), eq("SAME-LOCAL-NO")))
                    .thenReturn(List.of(Map.of("chargeNo", source + "-CHARGE", "status", "charged",
                            "sourceSystem", source, "uniqueCode", source + "-UID", "quantity", BigDecimal.ONE)));
            assertThat(module.receiveBillingCallback(Map.of("externalChargeNo", "SAME-LOCAL-NO", "sourceSystem", source,
                    "uniqueCode", source + "-UID", "quantity", BigDecimal.ONE)))
                    .containsEntry("chargeNo", source + "-CHARGE").containsEntry("idempotent", true);
        }
        org.mockito.Mockito.verifyNoInteractions(support);
    }

    @Test
    void rejectsReusedExternalNumberWithDifferentTracePatientSourceQuantityOrAmount() {
        Map<String, Object> stored = Map.ofEntries(
                Map.entry("chargeNo", "GZ-ORIGINAL"), Map.entry("status", "charged"),
                Map.entry("sourceSystem", "AIMS"), Map.entry("uniqueCode", "UID-1"),
                Map.entry("udiCode", "UDI-1"), Map.entry("productCode", "PC001"),
                Map.entry("patientNo", "PATIENT-1"), Map.entry("deptName", "Surgery"),
                Map.entry("quantity", BigDecimal.ONE), Map.entry("amount", BigDecimal.TEN));
        when(jdbcTemplate.queryForList(contains("FROM high_value_charge"), eq("AIMS"), eq("EXT-REUSED")))
                .thenReturn(List.of(stored));
        Map<String, Object> original = Map.of("externalChargeNo", "EXT-REUSED", "sourceSystem", "AIMS",
                "uniqueCode", "UID-1", "productCode", "PC001", "patientNo", "PATIENT-1",
                "deptName", "Surgery", "quantity", BigDecimal.ONE, "amount", BigDecimal.TEN);
        for (Map<String, Object> change : List.<Map<String, Object>>of(
                Map.of("uniqueCode", "UID-2"), Map.of("patientNo", "PATIENT-2"),
                Map.of("quantity", new BigDecimal("2")),
                Map.of("amount", new BigDecimal("11")), Map.of("productCode", "PC002"))) {
            var body = new java.util.HashMap<>(original);
            body.putAll(change);
            assertThatThrownBy(() -> module.receiveBillingCallback(body))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("计费内容不一致");
        }
        org.mockito.Mockito.verifyNoInteractions(support);
    }

    @Test
    void identicalHighPrecisionAmountRetryUsesTheSameFourPlaceMoneyAsPersistence() {
        when(jdbcTemplate.queryForList(contains("FROM high_value_charge"), eq("AIMS"), eq("EXT-PRECISE")))
                .thenReturn(List.of(Map.of("chargeNo", "GZ-ORIGINAL", "status", "charged",
                        "sourceSystem", "AIMS", "uniqueCode", "UID-1", "quantity", BigDecimal.ONE,
                        "amount", new BigDecimal("120.1235"))));
        assertThat(module.receiveBillingCallback(Map.of("externalChargeNo", "EXT-PRECISE",
                "uniqueCode", "UID-1", "quantity", BigDecimal.ONE, "amount", new BigDecimal("120.12345"))))
                .containsEntry("idempotent", true);
    }

    @Test
    void identicalRetryUsesOriginalAmountWithoutLookingUpTodaysCatalogPrice() {
        when(jdbcTemplate.queryForList(contains("FROM high_value_charge"), eq("AIMS"), eq("EXT-RETRY")))
                .thenReturn(List.of(Map.of("chargeNo", "GZ-ORIGINAL", "status", "charged",
                        "sourceSystem", "AIMS", "uniqueCode", "UID-1", "quantity", BigDecimal.ONE,
                        "amount", new BigDecimal("120.0000"))));
        var retry = module.receiveBillingCallback(Map.of("externalChargeNo", "EXT-RETRY",
                "uniqueCode", " UID-1 ", "quantity", new BigDecimal("1.0000")));
        assertThat(retry).containsEntry("idempotent", true).containsEntry("chargeNo", "GZ-ORIGINAL");
        verify(jdbcTemplate, org.mockito.Mockito.never()).queryForMap(anyString(), anyString());
        org.mockito.Mockito.verifyNoInteractions(support);
    }

    @Test
    void rejectsDifferentChargeNumberWhenTraceWasConsumedWhileWaitingForProductLock() {
        when(jdbcTemplate.queryForList(contains("FROM high_value_charge"), eq("AIMS"), eq("EXT-SECOND")))
                .thenReturn(List.of());
        Map<String, Object> signed = Map.of("traceCodeId", 2L, "productCode", "PC002", "lifecycleStatus", "signed");
        Map<String, Object> consumed = Map.of("traceCodeId", 2L, "productCode", "PC002", "lifecycleStatus", "consumed");
        when(jdbcTemplate.queryForList(contains("unique_code = ?"), eq("UID-002")))
                .thenReturn(List.of(signed), List.of(consumed));
        mockProduct("PC002", "Stent", BigDecimal.TEN);

        assertThatThrownBy(() -> module.receiveBillingCallback(Map.of(
                "externalChargeNo", "EXT-SECOND", "uniqueCode", "UID-002", "quantity", BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能重复计费");
        org.mockito.Mockito.verifyNoInteractions(support);
        verify(jdbcTemplate, org.mockito.Mockito.never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        verify(jdbcTemplate).queryForList(org.mockito.ArgumentMatchers.argThat(sql ->
                sql.contains("unique_code = ?") && sql.contains("FOR UPDATE")), eq("UID-002"));
    }

    @Test
    void createsHighValueChargeFromExplicitValues() {
        mockProduct("PC001", "Stent", BigDecimal.valueOf(500));
        when(support.nextNo(DocumentKind.HIGH_VALUE_CHARGE)).thenReturn("GZ20260601001");

        Map<String, Object> result = module.highValueCharge(Map.of(
                "productCode", "PC001",
                "deptName", "Surgery",
                "patientNo", "MZ-1001",
                "quantity", BigDecimal.valueOf(2)
        ));

        assertThat(result)
                .containsEntry("chargeNo", "GZ20260601001")
                .containsEntry("amount", BigDecimal.valueOf(1000));
        verify(jdbcTemplate).update(contains("INSERT INTO high_value_charge"),
                eq("GZ20260601001"), eq("Surgery"), eq("MZ-1001"), eq("PC001"), eq("Stent"),
                eq(BigDecimal.valueOf(2)), eq(BigDecimal.valueOf(1000)));
    }

    @Test
    void rejectsMissingHighValueChargeValues() {
        assertThatThrownBy(() -> module.highValueCharge(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productCode is required");
    }

    @Test
    void rejectsBillingCallbackWhenUdiAndUniqueCodeDoNotIdentifyTheSameTrace() {
        when(jdbcTemplate.queryForList(contains("FROM high_value_charge"), eq("AIMS"), eq("EXT-001")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq("UID-001"), eq("UDI-002")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> module.receiveBillingCallback(Map.of(
                "externalChargeNo", "EXT-001",
                "uniqueCode", "UID-001",
                "udiCode", "UDI-002",
                "quantity", BigDecimal.ONE
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UDI 与唯一码未指向同一高值耗材");
    }

    @Test
    void returnsExistingChargeAfterWaitingForProductLock() {
        when(jdbcTemplate.queryForList(contains("FROM high_value_charge"), eq("AIMS"), eq("EXT-002")))
                .thenReturn(List.of())
                .thenReturn(List.of(Map.of("chargeNo", "GZ-EXISTING", "status", "charged", "sourceSystem", "AIMS", "uniqueCode", "UID-002", "quantity", BigDecimal.ONE, "amount", BigDecimal.TEN)));
        when(jdbcTemplate.queryForList(contains("unique_code = ?"), eq("UID-002")))
                .thenReturn(List.of(Map.of(
                        "traceCodeId", 2L,
                        "udiCode", "UDI-002",
                        "uniqueCode", "UID-002",
                        "productCode", "PC002",
                        "currentLocation", "Surgery Warehouse",
                        "currentDepartment", "Surgery",
                        "patientNo", "ZY-002"
                )));
        mockProduct("PC002", "Stent", BigDecimal.TEN);
        Map<String, Object> result = module.receiveBillingCallback(Map.of(
                "externalChargeNo", "EXT-002",
                "uniqueCode", "UID-002",
                "quantity", BigDecimal.ONE
        ));

        assertThat(result)
                .containsEntry("chargeNo", "GZ-EXISTING")
                .containsEntry("status", "charged")
                .containsEntry("idempotent", true);
    }

    private void mockProduct(String code, String name, BigDecimal price) {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", code, "productName", name, "purchasePrice", price));
    }
}
