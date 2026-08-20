package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
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
        when(jdbcTemplate.queryForList(contains("FROM high_value_charge"), eq("EXT-001")))
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
    void returnsExistingChargeWhenConcurrentCallbackWinsTheUniqueKeyRace() {
        when(jdbcTemplate.queryForList(contains("FROM high_value_charge"), eq("EXT-002")))
                .thenReturn(List.of())
                .thenReturn(List.of(Map.of("chargeNo", "GZ-EXISTING", "status", "charged")));
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
        when(jdbcTemplate.queryForList(contains("SELECT warehouse_id FROM warehouse"), eq(Long.class), eq("Surgery Warehouse")))
                .thenReturn(List.of(20L));
        when(support.nextNo(DocumentKind.HIGH_VALUE_CHARGE)).thenReturn("GZ-NEW");
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenThrow(new DuplicateKeyException("duplicate external charge"));

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
