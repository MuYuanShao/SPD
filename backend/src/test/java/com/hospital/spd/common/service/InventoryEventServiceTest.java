package com.hospital.spd.common.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryEventServiceTest {

    private JdbcTemplate jdbc;
    private DocumentNumberService numbers;
    private OperatorContextProvider operators;
    private InventoryEventService service;
    private Connection connection;
    private PreparedStatement statement;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        numbers = mock(DocumentNumberService.class);
        operators = mock(OperatorContextProvider.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        service = new InventoryEventService(jdbc, numbers, operators);
        when(numbers.next(eq("KC"), eq(5), eq("inventory_event"), eq("event_no"))).thenReturn("KC001");
        when(operators.current()).thenReturn(OperatorContext.system());
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(statement);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            creator.createPreparedStatement(connection);
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 77L));
            return 1;
        });
    }

    @Test
    void capturesEventTimePriceAmountMasterDataAndStableTraceLinks() throws Exception {
        when(jdbc.queryForMap(contains("FROM warehouse"), eq(2L), eq(3L), eq(1L))).thenReturn(snapshot());
        InventoryEventCommand command = new InventoryEventCommand(
                "purchase_receive_in", "receiving_order", 9L, 1L, 2L, 3L,
                new BigDecimal("2.0000"), new BigDecimal("12.0000"), "验收入库",
                List.of(new InventoryEventCommand.TraceLink(88L, "high_value_unit", BigDecimal.ONE)));

        Long eventId = service.record(command);

        assertThat(eventId).isEqualTo(77L);
        verify(statement).setString(3, "receiving_in");
        verify(statement).setString(10, "中心库");
        verify(statement).setString(13, "导管");
        verify(statement).setBigDecimal(25, new BigDecimal("12.5000"));
        verify(statement).setBigDecimal(26, new BigDecimal("25.00000000"));
        verify(jdbc).update(contains("INSERT INTO inventory_event_trace_code"),
                eq(77L), eq(88L), eq("high_value_unit"), eq(BigDecimal.ONE));
    }

    @Test
    void createsOneZeroQuantityValuationEventPerAffectedWarehouse() throws Exception {
        when(jdbc.queryForList(contains("GROUP BY bal.warehouse_id"), eq(3L))).thenReturn(List.of(
                Map.of("warehouseId", 1L, "availableQty", new BigDecimal("8.0000"),
                        "affectedQty", new BigDecimal("10.0000"))));
        when(jdbc.queryForObject(contains("SELECT product_id"), eq(Long.class), eq(3L))).thenReturn(2L);
        when(jdbc.queryForMap(contains("FROM warehouse"), eq(2L), eq(3L), eq(1L))).thenReturn(snapshot());

        List<Long> ids = service.recordValuationEvents(7L, 3L,
                new BigDecimal("12.5000"), new BigDecimal("15.0000"), "调价");

        assertThat(ids).containsExactly(77L);
        verify(statement).setBigDecimal(22, new BigDecimal("12.5000"));
        verify(statement).setBigDecimal(23, new BigDecimal("15.0000"));
        verify(statement).setBigDecimal(24, new BigDecimal("10.0000"));
        verify(statement).setBigDecimal(25, new BigDecimal("25.00000000"));
    }

    private static Map<String, Object> snapshot() {
        Map<String, Object> row = new HashMap<>();
        row.put("deptId", null);
        row.put("deptName", null);
        row.put("warehouseCode", "WH001");
        row.put("warehouseName", "中心库");
        row.put("warehouseType", "中心库");
        row.put("productCode", "P001");
        row.put("productName", "导管");
        row.put("specModel", "8F");
        row.put("registrationNo", "械注准001");
        row.put("unit", "支");
        row.put("manufacturerName", "厂家");
        row.put("supplierId", 5L);
        row.put("supplierName", "供应商");
        row.put("systemBatchNo", "PC001");
        row.put("productionBatchNo", "LOT001");
        row.put("unitPrice", new BigDecimal("12.5000"));
        return row;
    }
}
