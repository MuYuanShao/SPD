package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.supplychain.InventoryEventQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryEventQueryServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OperatorContextProvider operators = mock(OperatorContextProvider.class);
    private final InventoryEventQueryService service = new InventoryEventQueryService(jdbc, operators);

    @Test
    void listsOneRowPerEventAndKeepsTraceAggregationOutOfPagination() {
        when(operators.current()).thenReturn(OperatorContext.system());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventNo", "IE001");
        event.put("transactionTypeCode", "receiving_in");
        event.put("traceCount", 2L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(event));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("inboundQty", 3));

        Map<String, Object> result = service.list(query(null, null, null));

        assertThat(result.get("total")).isEqualTo(1L);
        assertThat((List<?>) result.get("rows")).hasSize(1);
        assertThat(event).containsEntry("transactionTypeName", "验收入库").containsEntry("traceCount", 2L);
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(countSql.capture(), eq(Long.class), any(Object[].class));
        assertThat(countSql.getValue()).doesNotContain("inventory_event_trace_code");
    }

    @Test
    void restrictedDepartmentScopeExcludesEventsWithoutDepartmentSnapshot() {
        when(operators.current()).thenReturn(new OperatorContext(9L, "dept-user", "127.0.0.1",
                List.of("ROLE_USER"), 20L, OperatorContext.DATA_SCOPE_DEPT));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of());

        service.list(query(null, null, null));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertThat(sql.getValue()).contains("ie.dept_id_snapshot IS NOT NULL", "ie.dept_id_snapshot = ?");
    }

    @Test
    void rejectsInvalidDateRangeBeforeQueryingDatabase() {
        when(operators.current()).thenReturn(OperatorContext.system());
        assertThatThrownBy(() -> service.list(query("2026-09-05", "2026-09-04", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("结束日期不能早于开始日期");
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnknownSourceBusinessTypeBeforeQueryingDatabase() {
        when(operators.current()).thenReturn(OperatorContext.system());
        InventoryEventQuery query = new InventoryEventQuery(null, null, null, null, null, null, null,
                null, null, null, "forged_source", null, null, null, new PageRequest(1, 20, 0));
        assertThatThrownBy(() -> service.list(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的库存流水来源类型");
        verifyNoInteractions(jdbc);
    }

    @Test
    void detailReturnsOnlyTraceCodesLinkedToTheEvent() {
        when(operators.current()).thenReturn(OperatorContext.system());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventNo", "IE001");
        event.put("transactionTypeCode", "receiving_in");
        when(jdbc.queryForList(contains("SELECT ie.*"), any(Object[].class))).thenReturn(List.of(event));
        when(jdbc.queryForList(contains("FROM inventory_event_trace_code"), eq("IE001")))
                .thenReturn(List.of(Map.of("traceCodeId", 88L, "uniqueCode", "UDI-88")));

        Map<String, Object> detail = service.detail("IE001");

        assertThat((List<?>) detail.get("traceCodes")).hasSize(1);
        verify(jdbc).queryForList(contains("WHERE ie.event_no = ?"), eq("IE001"));
    }

    private InventoryEventQuery query(String start, String end, String type) {
        return new InventoryEventQuery(null, null, null, null, null, null, null, null, null,
                null, null, type, start, end, new PageRequest(1, 20, 0));
    }
}
