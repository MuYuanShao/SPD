package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.hospital.spd.common.service.DocumentKind.PDA_OFFLINE_RECORD;

/**
 * Owns PDA offline upload replay inside Operational Closure.
 */
@Service
public class OperationalPdaModule {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;

    public OperationalPdaModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
    }

    public Map<String, Object> uploadPda(Map<String, Object> body) {
        String recordNo = support.nextNo(PDA_OFFLINE_RECORD);
        jdbcTemplate.update("""
                INSERT INTO pda_offline_record (record_no, device_no, operation_type, payload, status)
                VALUES (?, ?, ?, CAST(? AS JSON), 'replayed')
                """, recordNo, text(body, "deviceNo", "PDA-001"), text(body, "operationType", "delivery_sign"),
                "{\"source\":\"offline\",\"result\":\"replayed\"}");
        return Map.of("recordNo", recordNo, "status", "replayed");
    }

    private static String text(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }
}
