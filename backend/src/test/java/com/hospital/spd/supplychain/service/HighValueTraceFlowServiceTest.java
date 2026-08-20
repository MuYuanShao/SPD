package com.hospital.spd.supplychain.service;
import com.hospital.spd.specialty.service.HighValueTraceFlowService;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.SupplyChainSupport;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HighValueTraceFlowServiceTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock SupplyChainSupport support;
    HighValueTraceFlowService service;

    @BeforeEach
    void setUp() {
        service = new HighValueTraceFlowService(jdbcTemplate, support, OperatorContext::system);
    }

    @Test
    void resolvesScannedUnitsInInputOrderAndExactWarehouse() {
        when(jdbcTemplate.queryForList(contains("FROM udi_trace_code tc"), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("traceCodeId", 2L, "uniqueCode", "20260723000002", "batchId", 20L,
                                "warehouseId", 1L, "lifecycleStatus", "in_stock"),
                        Map.of("traceCodeId", 1L, "uniqueCode", "20260723000001", "batchId", 10L,
                                "warehouseId", 1L, "lifecycleStatus", "in_stock")));

        List<HighValueTraceFlowService.TraceUnit> units = service.requireUnits(
                "20260723000001,20260723000002", BigDecimal.valueOf(2), 100L, 1L, List.of("in_stock"));

        assertThat(units).extracting(HighValueTraceFlowService.TraceUnit::uniqueCode)
                .containsExactly("20260723000001", "20260723000002");
    }

    @Test
    void rejectsWhenUniqueCodeCountDiffersFromQuantity() {
        assertThatThrownBy(() -> service.requireUnits(
                "20260723000001", BigDecimal.valueOf(2), 100L, 1L, List.of("in_stock")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("唯一码数量必须与业务数量一致");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void patientBindingWritesStableIdentityAndTraceEvent() {
        when(jdbcTemplate.queryForMap(contains("ibtc.lifecycle_status"), eq("20260723000001")))
                .thenReturn(Map.of("traceCodeId", 1L, "uniqueCode", "20260723000001", "batchId", 10L,
                        "lifecycleStatus", "signed"));
        when(support.nextNo(DocumentKind.UDI_TRACE_EVENT)).thenReturn("UT20260723000001");

        Map<String, Object> result = service.bindPatient(Map.of(
                "uniqueCode", "20260723000001", "patientNo", "ZY001", "deptName", "骨科"));

        assertThat(result).containsEntry("status", "patient_bound");
        verify(jdbcTemplate).update(contains("INSERT INTO udi_trace_patient_binding"), any(Object[].class));
        verify(jdbcTemplate).update(contains("INSERT INTO udi_trace_event"), any(Object[].class));
    }
}
