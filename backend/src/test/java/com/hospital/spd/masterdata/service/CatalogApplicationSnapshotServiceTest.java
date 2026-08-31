package com.hospital.spd.masterdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogApplicationSnapshotServiceTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void disabledProductInformationChangeKeepsStatusAndWritesImmutablePriceDiff() throws Exception {
        Map<String, Object> pending = new HashMap<>();
        pending.put("product_code", "P001");
        pending.put("product_name", "耗材");
        pending.put("spec_model", "10ml");
        pending.put("unit", "支");
        pending.put("purchase_price", new BigDecimal("2.00"));
        Map<String, Object> current = new HashMap<>(pending);
        current.put("product_id", 8L);
        current.put("purchase_price", new BigDecimal("1.50"));
        current.put("status", 0);
        when(jdbc.queryForMap(anyString(), eq(9L))).thenReturn(pending);
        when(jdbc.queryForList(anyString(), eq("P001"))).thenReturn(List.of(current));

        new CatalogApplicationSnapshotService(jdbc, new ObjectMapper())
                .write(9L, "信息变更", "调整采购信息", null);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), args.capture());
        JsonNode snapshot = new ObjectMapper().readTree(String.valueOf(args.getValue()[0]));
        JsonNode diff = new ObjectMapper().readTree(String.valueOf(args.getValue()[1]));
        assertThat(snapshot.path("status").asInt()).isZero();
        assertThat(snapshot.path("targetStatus").asInt()).isZero();
        assertThat(diff.path("items").toString()).contains("采购价", "1.50", "2.00");
    }

    @Test
    void disableApplicationRecordsExplicitStatusChange() throws Exception {
        Map<String, Object> pending = new HashMap<>();
        pending.put("product_code", "P002");
        Map<String, Object> current = new HashMap<>(pending);
        current.put("product_id", 10L);
        current.put("status", 1);
        when(jdbc.queryForMap(anyString(), eq(11L))).thenReturn(pending);
        when(jdbc.queryForList(anyString(), eq("P002"))).thenReturn(List.of(current));

        new CatalogApplicationSnapshotService(jdbc, new ObjectMapper())
                .write(11L, "停用申请", "停止使用", null);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), args.capture());
        JsonNode diff = new ObjectMapper().readTree(String.valueOf(args.getValue()[1]));
        assertThat(diff.path("items").toString()).contains("启停状态", "启用", "停用");
    }
}
