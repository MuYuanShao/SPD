package com.hospital.spd.supplychain.service;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalShortageModuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private OperationalShortageModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalShortageModule(jdbcTemplate, support);
    }

    @Test
    void generatesShortageFromExplicitValues() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "Syringe"));
        when(jdbcTemplate.queryForObject(contains("department_consumption"), eq(BigDecimal.class), any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(21));
        when(support.nextNo(DocumentKind.SHORTAGE_REPLENISHMENT_TASK)).thenReturn("QH20260601001");

        Map<String, Object> result = module.generateShortage(Map.of(
                "deptName", "Surgery",
                "productCode", "PC001",
                "minQty", BigDecimal.valueOf(10),
                "currentQty", BigDecimal.valueOf(3),
                "replenishmentDays", 7,
                "replenishQty", BigDecimal.valueOf(8)
        ));

        assertThat(result)
                .containsEntry("taskNo", "QH20260601001")
                .containsEntry("status", "pending_replenish")
                .containsEntry("periodDays", 7)
                .containsEntry("periodIssueQty", BigDecimal.valueOf(21))
                .containsEntry("formulaReplenishQty", BigDecimal.valueOf(18))
                .containsEntry("replenishQty", BigDecimal.valueOf(8))
                .containsEntry("manualAdjusted", true);
        verify(jdbcTemplate).update(contains("INSERT INTO shortage_replenishment_task"),
                eq("QH20260601001"), eq("Surgery"), eq("PC001"), eq("Syringe"),
                eq(BigDecimal.valueOf(10)), eq(BigDecimal.valueOf(3)), eq(BigDecimal.valueOf(8)),
                eq(7), eq(BigDecimal.valueOf(21)), eq(new BigDecimal("3.0000")),
                eq(BigDecimal.valueOf(18)), eq(1), anyString());
    }

    @Test
    void defaultsReplenishQuantityFromPeriodIssueAndCurrentStock() {
        when(jdbcTemplate.queryForMap(contains("FROM product WHERE product_code = ?"), anyString()))
                .thenReturn(Map.of("productId", 100L, "productCode", "PC001", "productName", "Gauze"));
        when(jdbcTemplate.queryForObject(contains("department_consumption"), eq(BigDecimal.class), any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(15));
        when(support.nextNo(DocumentKind.SHORTAGE_REPLENISHMENT_TASK)).thenReturn("QH20260601002");

        Map<String, Object> result = module.generateShortage(Map.of(
                "deptName", "Internal Medicine", "productCode", "PC001", "minQty", BigDecimal.ONE));

        assertThat(result)
                .containsEntry("status", "pending_replenish")
                .containsEntry("periodDays", 7)
                .containsEntry("formulaReplenishQty", BigDecimal.valueOf(15))
                .containsEntry("manualAdjusted", false);
        verify(jdbcTemplate).update(contains("INSERT INTO shortage_replenishment_task"),
                eq("QH20260601002"), eq("Internal Medicine"), eq("PC001"), eq("Gauze"),
                eq(BigDecimal.ONE), eq(BigDecimal.ZERO), eq(BigDecimal.valueOf(15)),
                eq(7), eq(BigDecimal.valueOf(15)), eq(new BigDecimal("2.1429")),
                eq(BigDecimal.valueOf(15)), eq(0), anyString());
    }

    @Test
    void smartAnalysisStoresPeriodIssueSnapshotAndRecommendation() {
        when(jdbcTemplate.queryForList(contains("JOIN sys_dept sd ON sd.dept_id = w.dept_id"),
                eq(String.class), eq("Surgery"), eq("Main Warehouse")))
                .thenReturn(List.of("Main Warehouse"));
        when(jdbcTemplate.queryForList(contains("issue5"), eq("Main Warehouse"), eq("Surgery")))
                .thenReturn(List.of(Map.of(
                        "productCode", "PC001",
                        "productName", "Gauze",
                        "issue5", BigDecimal.valueOf(5),
                        "issue7", BigDecimal.valueOf(9),
                        "issue15", BigDecimal.valueOf(20),
                        "issue30", BigDecimal.valueOf(40),
                        "currentQty", BigDecimal.valueOf(4)
                )));
        when(jdbcTemplate.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Long.class))).thenReturn(88L);

        Map<String, Object> result = module.smartAnalyze(Map.of(
                "deptName", "Surgery",
                "warehouseName", "Main Warehouse",
                "selectedPeriodDays", 7
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(result)
                .containsEntry("analysisId", 88L)
                .containsEntry("selectedPeriodDays", 7);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("productCode", "PC001")
                .containsEntry("selectedIssueQty", BigDecimal.valueOf(9))
                .containsEntry("currentQty", BigDecimal.valueOf(4))
                .containsEntry("recommendedQty", BigDecimal.valueOf(5));
        verify(jdbcTemplate).update(contains("INSERT INTO replenishment_smart_analysis"),
                eq("Surgery"), eq("Main Warehouse"), eq(7), eq(1), eq(BigDecimal.valueOf(5)));
        verify(jdbcTemplate).update(contains("INSERT INTO replenishment_smart_analysis_item"),
                eq(88L), eq("PC001"), eq("Gauze"), eq(BigDecimal.valueOf(5)), eq(BigDecimal.valueOf(9)),
                eq(BigDecimal.valueOf(20)), eq(BigDecimal.valueOf(40)), eq(BigDecimal.valueOf(4)),
                eq(BigDecimal.valueOf(5)), eq("近7天出库量 - 当前库存"));
    }

    @Test
    void smartAnalysisRejectsWarehouseThatIsNotLinkedToDepartment() {
        when(jdbcTemplate.queryForList(contains("JOIN sys_dept sd ON sd.dept_id = w.dept_id"),
                eq(String.class), eq("Surgery"), eq("Main Warehouse")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> module.smartAnalyze(Map.of(
                "deptName", "Surgery",
                "warehouseName", "Main Warehouse",
                "selectedPeriodDays", 7
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("所选库房未关联当前科室");
    }

    @Test
    void smartAnalysisUsesFirstLinkedWarehouseWhenWarehouseIsBlank() {
        when(jdbcTemplate.queryForList(contains("ORDER BY w.warehouse_id"),
                eq(String.class), eq("Surgery")))
                .thenReturn(List.of("Surgery Warehouse"));
        when(jdbcTemplate.queryForList(contains("issue5"), eq("Surgery Warehouse"), eq("Surgery")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Long.class))).thenReturn(89L);

        Map<String, Object> result = module.smartAnalyze(Map.of(
                "deptName", "Surgery",
                "selectedPeriodDays", 7
        ));

        assertThat(result)
                .containsEntry("analysisId", 89L)
                .containsEntry("warehouseName", "Surgery Warehouse");
        verify(jdbcTemplate).update(contains("INSERT INTO replenishment_smart_analysis"),
                eq("Surgery"), eq("Surgery Warehouse"), eq(7), eq(0), eq(BigDecimal.ZERO));
    }
}
