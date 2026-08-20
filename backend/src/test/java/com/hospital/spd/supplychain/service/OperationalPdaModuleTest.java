package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalPdaModuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private OperationalPdaModule module;

    @BeforeEach
    void setUp() {
        module = new OperationalPdaModule(jdbcTemplate, support);
    }

    @Test
    void uploadsPdaRecordWithExplicitDeviceAndOperation() {
        when(support.nextNo(DocumentKind.PDA_OFFLINE_RECORD)).thenReturn("PDA20260601001");

        Map<String, Object> result = module.uploadPda(Map.of(
                "deviceNo", "PDA-009",
                "operationType", "inventory_count"
        ));

        assertThat(result)
                .containsEntry("recordNo", "PDA20260601001")
                .containsEntry("status", "replayed");
        verify(jdbcTemplate).update(contains("INSERT INTO pda_offline_record"),
                eq("PDA20260601001"), eq("PDA-009"), eq("inventory_count"),
                eq("{\"source\":\"offline\",\"result\":\"replayed\"}"));
    }

    @Test
    void defaultsPdaRecordWhenBodyIsEmpty() {
        when(support.nextNo(DocumentKind.PDA_OFFLINE_RECORD)).thenReturn("PDA20260601002");

        Map<String, Object> result = module.uploadPda(Map.of());

        assertThat(result).containsEntry("status", "replayed");
        verify(jdbcTemplate).update(contains("INSERT INTO pda_offline_record"),
                eq("PDA20260601002"), eq("PDA-001"), eq("delivery_sign"),
                eq("{\"source\":\"offline\",\"result\":\"replayed\"}"));
    }
}
