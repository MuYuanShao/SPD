package com.hospital.spd.supplychain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.flyway.ignore-migration-patterns=*:missing")
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "SPD_MYSQL_INTEGRATION_TESTS", matches = "true")
class OperationalHighValueMysqlConcurrencyTest {

    @Autowired OperationalHighValueModule module;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void concurrentBillingCallbacksDeductInventoryExactlyOnce() throws Exception {
        Map<String, Object> fixture = jdbcTemplate.queryForMap("""
                SELECT bal.balance_id AS balanceId, bal.available_qty AS availableQty,
                       bal.last_event_id AS lastEventId, bal.warehouse_id AS warehouseId,
                       bal.batch_id AS batchId,
                       w.warehouse_name AS warehouseName, bal.product_id AS productId,
                       p.product_code AS productCode, p.product_name AS productName
                  FROM inventory_balance bal
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  JOIN product p ON p.product_id = bal.product_id
                 WHERE bal.available_qty >= 2 AND w.deleted = 0 AND w.status = 1
                 ORDER BY bal.balance_id LIMIT 1
                """);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String uniqueCode = "IT-UID-" + suffix;
        String udiCode = "IT-UDI-" + suffix;
        String externalNo = "IT-EXT-" + suffix;
        Long traceId = null;
        List<Map<String, Object>> balanceSnapshot = jdbcTemplate.queryForList("""
                SELECT balance_id AS balanceId, available_qty AS availableQty, last_event_id AS lastEventId
                  FROM inventory_balance WHERE warehouse_id = ? AND product_id = ?
                """, fixture.get("warehouseId"), fixture.get("productId"));
        BigDecimal totalBefore = balanceSnapshot.stream()
                .map(row -> (BigDecimal) row.get("availableQty"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        try {
            jdbcTemplate.update("""
                    INSERT INTO udi_trace_code
                      (udi_code, unique_code, trace_scope, product_code, product_name, current_location,
                       current_status, risk_level, last_event_time)
                    VALUES (?, ?, 'high_value', ?, ?, ?, 'in_stock', 'normal', NOW())
                    """, udiCode, uniqueCode, fixture.get("productCode"), fixture.get("productName"), fixture.get("warehouseName"));
            traceId = jdbcTemplate.queryForObject("SELECT trace_code_id FROM udi_trace_code WHERE unique_code = ?", Long.class, uniqueCode);
            jdbcTemplate.update("""
                    INSERT INTO inventory_batch_trace_code
                      (batch_id, trace_code_id, receiving_item_id, current_warehouse_id, lifecycle_status)
                    VALUES (?, ?, 0, ?, 'in_stock')
                    """, fixture.get("batchId"), traceId, fixture.get("warehouseId"));

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Map<String, Object>> callback = () -> {
                ready.countDown();
                start.await();
                return module.receiveBillingCallback(Map.of(
                        "externalChargeNo", externalNo,
                        "uniqueCode", uniqueCode,
                        "productCode", fixture.get("productCode"),
                        "warehouseName", fixture.get("warehouseName"),
                        "deptName", "并发测试科室",
                        "patientNo", "IT-PATIENT",
                        "quantity", BigDecimal.ONE));
            };
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Map<String, Object>> first = executor.submit(callback);
                Future<Map<String, Object>> second = executor.submit(callback);
                ready.await();
                start.countDown();
                List<Map<String, Object>> results = List.of(first.get(), second.get());
                assertThat(results).extracting(result -> result.get("idempotent"))
                        .containsExactlyInAnyOrder(false, true);
            } finally {
                executor.shutdownNow();
            }

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM high_value_charge WHERE external_charge_no = ?", Integer.class, externalNo))
                    .isEqualTo(1);
            BigDecimal after = jdbcTemplate.queryForObject(
                    "SELECT SUM(available_qty) FROM inventory_balance WHERE warehouse_id = ? AND product_id = ?",
                    BigDecimal.class, fixture.get("warehouseId"), fixture.get("productId"));
            assertThat(after).isEqualByComparingTo(totalBefore.subtract(BigDecimal.ONE));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT trace_code_id FROM high_value_charge WHERE external_charge_no = ?", Long.class, externalNo))
                    .isEqualTo(traceId);
        } finally {
            List<Long> chargeIds = jdbcTemplate.queryForList(
                    "SELECT charge_id FROM high_value_charge WHERE external_charge_no = ?", Long.class, externalNo);
            for (Long chargeId : chargeIds) {
                jdbcTemplate.update("DELETE FROM inventory_event WHERE source_biz_type = 'high_value_charge' AND source_biz_id = ?", chargeId);
                jdbcTemplate.update("DELETE FROM audit_log WHERE biz_type = 'high_value_charge' AND biz_id = ?", chargeId);
            }
            jdbcTemplate.update("DELETE FROM high_value_charge WHERE external_charge_no = ?", externalNo);
            if (traceId != null) {
                jdbcTemplate.update("DELETE FROM udi_trace_event WHERE trace_code_id = ?", traceId);
                jdbcTemplate.update("DELETE FROM inventory_batch_trace_code WHERE trace_code_id = ?", traceId);
                jdbcTemplate.update("DELETE FROM udi_trace_code WHERE trace_code_id = ?", traceId);
            }
            for (Map<String, Object> balance : balanceSnapshot) {
                jdbcTemplate.update("UPDATE inventory_balance SET available_qty = ?, last_event_id = ? WHERE balance_id = ?",
                        balance.get("availableQty"), balance.get("lastEventId"), balance.get("balanceId"));
            }
        }
    }
}
