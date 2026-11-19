package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.common.JdbcPageQuery;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.service.InventoryEventCommand;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;

import static com.hospital.spd.common.service.DocumentKind.COLD_CHAIN_EXCEPTION;
import static com.hospital.spd.common.service.DocumentKind.RECALL_EVENT;
import static com.hospital.spd.common.service.DocumentKind.QUOTA_PACKAGE_EVENT;

/**
 * Owns risk-event creation inside Operational Closure.
 */
@Service
public class OperationalRiskModule {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final DataScopeService dataScope;

    public OperationalRiskModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support, DataScopeService dataScope) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.dataScope = dataScope;
    }

    public Map<String, Object> coldChainException(Map<String, Object> body) {
        Map<String, Object> product = findProduct(requireText(body, "productCode"));
        String warehouseName = requireText(body, "warehouseName");
        BigDecimal temperature = new BigDecimal(requireText(body, "temperature"));
        String severity = requireText(body, "severity");
        findWarehouseId(warehouseName);
        String eventNo = support.nextNo(COLD_CHAIN_EXCEPTION);
        jdbcTemplate.update("""
                INSERT INTO cold_chain_exception (
                  event_no, warehouse_name, product_code, product_name, temperature, severity, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'pending_dispose')
                """, eventNo, warehouseName, product.get("productCode"),
                product.get("productName"), temperature, severity);
        return Map.of("eventNo", eventNo, "status", "pending_dispose");
    }

    /** 召回隔离批次选项：所选商品在该库房仍有可用库存的批次。 */
    public Map<String, Object> recallBatches(String productCode, String warehouseName) {
        Long productId = ((Number) findProduct(productCode).get("productId")).longValue();
        Long warehouseId = findWarehouseId(warehouseName);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ib.batch_id AS batchId, ib.system_batch_no AS systemBatchNo,
                       ib.production_batch_no AS productionBatchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       bal.available_qty AS availableQty
                  FROM inventory_balance bal
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE bal.warehouse_id = ? AND bal.product_id = ? AND bal.available_qty > 0
                 ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id
                """, warehouseId, productId);
        return Map.of("rows", rows);
    }

    /** 召回范围内库存：定数包按来源批次折算为散货数量展示。 */
    public Map<String, Object> recallInventory(Map<String, String> params) {
        String productCode = params.getOrDefault("productCode", "").trim();
        if (productCode.isBlank()) return Map.of("rows", List.of());
        Map<String, Object> product = findProduct(productCode);
        var rows = recallInventoryRows(
                ((Number) product.get("productId")).longValue(),
                normalizeScope(params.get("scope")),
                params.getOrDefault("warehouseName", "").trim(), null);
        return Map.of("rows", "recall".equals(params.get("businessType"))
                ? rows.stream().filter(row -> !isCenterWarehouse(row)).toList() : rows);
    }

    /**
     * 召回生成待签收退库，隔离在原库房冻结可用库存。
     */
    @Transactional
    public Map<String, Object> createRecall(Map<String, Object> body) {
        String businessType = String.valueOf(body.getOrDefault("businessType", "recall"));
        if (!List.of("recall", "isolate").contains(businessType)) {
            throw new IllegalArgumentException("业务类型必须为召回或隔离");
        }
        boolean recall = "recall".equals(businessType);
        String scope = normalizeScope(String.valueOf(body.getOrDefault("scope", "all")));
        String warehouseName = text(body.get("warehouseName"));
        Map<String, Object> product = findProduct(requireText(body, "productCode"));
        String batchNo = requireText(body, "batchNo");
        String reason = requireText(body, "reason");
        Long productId = ((Number) product.get("productId")).longValue();
        new QuotaPackagePricingService(jdbcTemplate).lockProduct(productId);
        List<Map<String, Object>> candidates = recallInventoryRows(productId, scope, warehouseName, batchNo);
        if (candidates.isEmpty()) throw new IllegalArgumentException("所选范围内没有该商品批号的可召回库存");
        Map<String, Object> primary = recall ? jdbcTemplate.queryForMap("""
                SELECT warehouse_id AS warehouseId, warehouse_name AS warehouseName
                  FROM warehouse
                 WHERE deleted = 0 AND status = 1
                   AND (warehouse_type LIKE '%一级%' OR warehouse_type LIKE '%中心%')
                 ORDER BY warehouse_id LIMIT 1
                """) : candidates.get(0);
        Long primaryWarehouseId = ((Number) primary.get("warehouseId")).longValue();
        String primaryWarehouseName = String.valueOf(primary.get("warehouseName"));
        if (recall) {
            candidates = candidates.stream()
                    .filter(row -> ((Number) row.get("warehouseId")).longValue() != primaryWarehouseId)
                    .filter(row -> !isCenterWarehouse(row))
                    .toList();
            if (candidates.isEmpty()) throw new IllegalArgumentException("当前范围无可退回中心库的库存");
        }
        String status = recall ? "pending_receipt" : "isolated";
        BigDecimal quantity = candidates.stream()
                .map(row -> (BigDecimal) row.get("totalQty"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> batchIds = candidates.stream().map(row -> ((Number) row.get("batchId")).longValue()).distinct().toList();
        Long eventBatchId = batchIds.size() == 1 ? batchIds.get(0) : null;
        String recallNo = support.nextNo(RECALL_EVENT);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO recall_event (
                      recall_no, warehouse_name, product_code, product_name, batch_id, affected_qty, status, reason, business_type, destination_warehouse_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, recallNo);
            ps.setString(2, primaryWarehouseName);
            ps.setString(3, String.valueOf(product.get("productCode")));
            ps.setString(4, String.valueOf(product.get("productName")));
            if (eventBatchId == null) {
                ps.setObject(5, null);
            } else {
                ps.setLong(5, eventBatchId);
            }
            ps.setBigDecimal(6, quantity);
            ps.setString(7, status);
            ps.setString(8, reason);
            ps.setString(9, businessType);
            ps.setObject(10, recall ? primaryWarehouseId : null);
            return ps;
        }, keyHolder);
        Long recallId = Objects.requireNonNull(keyHolder.getKey()).longValue();

        releaseRecallReservations(candidates, productId);
        unpackRecallPackages(candidates, productId, recallId, reason);
        for (Map<String, Object> candidate : candidates) {
            Long sourceWarehouseId = ((Number) candidate.get("warehouseId")).longValue();
            Long batchId = ((Number) candidate.get("batchId")).longValue();
            BigDecimal totalQty = (BigDecimal) candidate.get("totalQty");
            List<Long> traces = restrictTraceUnits(sourceWarehouseId, productId, batchId, recallNo, reason, recall);
            if (recall) {
                if (product.get("highValue") instanceof Number flag && flag.intValue() == 1
                        && totalQty.compareTo(BigDecimal.valueOf(traces.size())) != 0) {
                    throw new IllegalArgumentException("高值耗材退库数量与可转运唯一码数量不一致");
                }
                var outbound = support.consumeSpecificBatchEvent(sourceWarehouseId, productId, batchId, totalQty,
                        "warehouse_transfer_out", "recall_event", recallId, "召回退库出库，待中心库签收：" + reason);
                if (!traces.isEmpty()) support.linkInventoryEventTraceCodes(outbound.eventId(), traceLinks(traces));
            } else {
                support.isolateSpecificBatch(sourceWarehouseId, productId, batchId, totalQty, "recall_event", recallId, reason);
            }
        }
        support.writeAudit("recall", recall ? "recall_return_dispatched" : "isolate_in_place", recallId, recallNo,
                "scope=" + scope + ", batch=" + batchNo + ", quantity=" + quantity);
        return Map.of("recallNo", recallNo, "status", status, "affectedQty", quantity,
                "primaryWarehouseName", primaryWarehouseName);
    }

    private boolean isCenterWarehouse(Map<String, Object> row) {
        String type = String.valueOf(row.get("warehouseType"));
        return type.contains("一级") || type.contains("中心");
    }

    public List<Map<String, Object>> pendingRecallReceipts() {
        return queryPendingRecallReceipts(null).rows();
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> pendingRecallReceipts(PageRequest page) {
        return queryPendingRecallReceipts(page);
    }

    private PageResponse<Map<String, Object>> queryPendingRecallReceipts(PageRequest page) {
        StringBuilder where = new StringBuilder("""
                 WHERE r.business_type = 'recall' AND r.status = 'pending_receipt'
                   AND w.deleted = 0 AND w.status = 1
                   AND (w.warehouse_type LIKE '%一级%' OR w.warehouse_type LIKE '%中心%')
                """);
        List<Object> args = new ArrayList<>();
        dataScope.appendScope(where, args, "w.dept_id", null);
        String from = """
                  FROM recall_event r JOIN warehouse w ON w.warehouse_id = r.destination_warehouse_id
                """ + where;
        return JdbcPageQuery.fetch(jdbcTemplate, page, """
                SELECT r.recall_no AS recallNo, w.warehouse_name AS destinationWarehouse,
                       r.affected_qty AS quantity,
                       (SELECT GROUP_CONCAT(DISTINCT e.warehouse_name_snapshot ORDER BY e.warehouse_name_snapshot SEPARATOR '、')
                          FROM inventory_event e
                         WHERE e.source_biz_type = 'recall_event' AND e.source_biz_id = r.recall_id
                           AND e.event_type = 'warehouse_transfer_out' AND e.qty_change < 0) AS sourceWarehouses
                """, from, " ORDER BY r.create_time, r.recall_id", args);
    }

    @Transactional
    public Map<String, Object> receiveRecall(String recallNo) {
        var records = jdbcTemplate.queryForList("""
                SELECT recall_id AS recallId, destination_warehouse_id AS destinationId, status,
                       affected_qty AS quantity
                  FROM recall_event WHERE recall_no = ? AND business_type = 'recall' FOR UPDATE
                """, recallNo);
        if (records.isEmpty()) throw new IllegalArgumentException("未找到召回退库单");
        var record = records.get(0);
        if (!(record.get("destinationId") instanceof Number target)) {
            throw new IllegalArgumentException("只有待签收的召回退库单可以入库");
        }
        long recallId = ((Number) record.get("recallId")).longValue();
        long destination = target.longValue();
        StringBuilder warehouseQuery = new StringBuilder("""
                SELECT w.warehouse_name AS name FROM warehouse w
                 WHERE w.warehouse_id = ? AND w.deleted = 0 AND w.status = 1
                   AND (w.warehouse_type LIKE '%一级%' OR w.warehouse_type LIKE '%中心%')
                """);
        List<Object> warehouseArgs = new ArrayList<>();
        warehouseArgs.add(destination);
        dataScope.appendScope(warehouseQuery, warehouseArgs, "w.dept_id", null);
        var warehouses = jdbcTemplate.queryForList(warehouseQuery + " FOR SHARE", warehouseArgs.toArray());
        if (warehouses.isEmpty()) throw new AccessDeniedException("接收中心库不可用或超出当前操作人的数据范围");
        if ("received".equals(record.get("status"))) return Map.of("recallNo", recallNo, "status", "received");
        if (!"pending_receipt".equals(record.get("status"))) {
            throw new IllegalArgumentException("只有待签收的召回退库单可以入库");
        }
        String destinationName = String.valueOf(warehouses.get(0).get("name"));
        var outgoing = jdbcTemplate.queryForList("""
                SELECT event_id AS eventId, warehouse_id AS sourceId, product_id AS productId,
                       batch_id AS batchId, -qty_change AS quantity
                  FROM inventory_event
                 WHERE source_biz_type = 'recall_event' AND source_biz_id = ?
                   AND event_type = 'warehouse_transfer_out' AND qty_change < 0
                 ORDER BY product_id, batch_id, event_id
                """, recallId);
        BigDecimal shipped = outgoing.stream().map(row -> (BigDecimal) row.get("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (shipped.signum() <= 0 || shipped.compareTo((BigDecimal) record.get("quantity")) != 0) {
            throw new IllegalArgumentException("退库出库明细与单据数量不一致");
        }
        new QuotaPackagePricingService(jdbcTemplate).lockProducts(outgoing.stream()
                .map(row -> ((Number) row.get("productId")).longValue()).toList());
        for (var row : outgoing) {
            Long eventId = support.receiveAvailable(destination, ((Number) row.get("productId")).longValue(),
                    ((Number) row.get("batchId")).longValue(), (BigDecimal) row.get("quantity"),
                    "warehouse_transfer_in", "recall_event", recallId, "中心库签收召回退库：" + recallNo);
            List<Long> traces = jdbcTemplate.queryForList("""
                    SELECT trace_code_id FROM inventory_event_trace_code
                     WHERE event_id = ? AND trace_type = 'high_value_unit' ORDER BY trace_code_id
                    """, Long.class, row.get("eventId"));
            for (Long traceId : traces) {
                int changed = jdbcTemplate.update("""
                        UPDATE inventory_batch_trace_code
                           SET current_warehouse_id = ?, lifecycle_status = 'in_stock'
                         WHERE trace_code_id = ? AND current_warehouse_id = ? AND lifecycle_status = 'recall_in_transit'
                        """, destination, traceId, row.get("sourceId"));
                if (changed != 1) throw new IllegalArgumentException("召回唯一码状态已变化，不能签收入库");
                jdbcTemplate.update("""
                        UPDATE udi_trace_code SET current_status = 'in_stock', current_location = ?,
                               current_department = NULL, last_event_name = '召回签收入库', last_event_time = NOW()
                         WHERE trace_code_id = ?
                        """, destinationName, traceId);
                writeRecallTraceEvent(traceId, recallNo, "recall_receipt", "召回签收入库", "中心库确认签收");
            }
            if (!traces.isEmpty()) support.linkInventoryEventTraceCodes(eventId, traceLinks(traces));
        }
        jdbcTemplate.update("UPDATE recall_event SET status = 'received', received_time = NOW() WHERE recall_id = ?", recallId);
        support.writeAudit("recall", "recall_return_received", recallId, recallNo, "中心库签收入库：" + shipped);
        return Map.of("recallNo", recallNo, "status", "received", "receivedQty", shipped);
    }

    private List<Map<String, Object>> recallInventoryRows(Long productId, String scope, String warehouseName, String batchNo) {
        List<Object> args = new ArrayList<>();
        args.add(productId);
        StringBuilder where = new StringBuilder(" WHERE stock.product_id = ? AND stock.total_qty > 0");
        switch (scope) {
            case "primary" -> where.append(" AND (w.warehouse_type LIKE '%一级%' OR w.warehouse_type LIKE '%中心%')");
            case "secondary" -> where.append(" AND w.warehouse_type LIKE '%二级%'");
            case "tertiary" -> where.append(" AND w.warehouse_type LIKE '%三级%'");
            default -> { }
        }
        if (!warehouseName.isBlank()) {
            where.append(" AND w.warehouse_name = ?");
            args.add(warehouseName);
        }
        if (batchNo != null && !batchNo.isBlank()) {
            where.append(" AND (ib.system_batch_no = ? OR ib.production_batch_no = ?)");
            args.add(batchNo.trim());
            args.add(batchNo.trim());
        }
        return jdbcTemplate.queryForList("""
                SELECT w.warehouse_id AS warehouseId, w.warehouse_name AS warehouseName,
                       w.warehouse_type AS warehouseType, p.product_code AS productCode,
                       p.product_name AS productName, p.spec_model AS specModel,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName, p.unit,
                       p.purchase_price AS unitPrice, ib.batch_id AS batchId,
                       ib.system_batch_no AS systemBatchNo, ib.production_batch_no AS productionBatchNo,
                       stock.loose_qty AS looseQty, stock.package_qty AS packageQty,
                       stock.total_qty AS totalQty
                  FROM (
                    SELECT warehouse_id, product_id, batch_id, SUM(loose_qty) AS loose_qty,
                           SUM(package_qty) AS package_qty, SUM(loose_qty + package_qty) AS total_qty
                      FROM (
                        SELECT bal.warehouse_id, bal.product_id, bal.batch_id,
                               GREATEST(SUM(bal.available_qty + bal.locked_qty) - COALESCE(signed_pkg.package_qty, 0), 0) AS loose_qty,
                               CAST(0 AS DECIMAL(18,4)) AS package_qty
                          FROM inventory_balance bal
                          LEFT JOIN (
                            SELECT qpl.warehouse_id, qpl.product_id, src.batch_id, SUM(src.source_qty) AS package_qty
                              FROM quota_package_label qpl
                              JOIN quota_package_label_source src ON src.label_id = qpl.label_id
                             WHERE qpl.status = 'signed'
                             GROUP BY qpl.warehouse_id, qpl.product_id, src.batch_id
                          ) signed_pkg ON signed_pkg.warehouse_id = bal.warehouse_id
                            AND signed_pkg.product_id = bal.product_id AND signed_pkg.batch_id = bal.batch_id
                         WHERE bal.location_id IS NULL AND (bal.available_qty > 0 OR bal.locked_qty > 0)
                         GROUP BY bal.warehouse_id, bal.product_id, bal.batch_id, signed_pkg.package_qty
                        UNION ALL
                        SELECT qpl.warehouse_id, qpl.product_id, src.batch_id,
                               CAST(0 AS DECIMAL(18,4)) AS loose_qty, SUM(src.source_qty) AS package_qty
                          FROM quota_package_label qpl
                          JOIN quota_package_label_source src ON src.label_id = qpl.label_id
                         WHERE qpl.status IN ('pending_print', 'available', 'delivered', 'signed')
                         GROUP BY qpl.warehouse_id, qpl.product_id, src.batch_id
                      ) converted_stock
                     GROUP BY warehouse_id, product_id, batch_id
                  ) stock
                  JOIN warehouse w ON w.warehouse_id = stock.warehouse_id AND w.deleted = 0 AND w.status = 1
                  JOIN product p ON p.product_id = stock.product_id
                  JOIN inventory_batch ib ON ib.batch_id = stock.batch_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                """ + where + " ORDER BY w.warehouse_type, w.warehouse_name, ib.system_batch_no", args.toArray());
    }

    private void unpackRecallPackages(List<Map<String, Object>> candidates, Long productId, Long recallId, String reason) {
        for (Map<String, Object> candidate : candidates) {
            if (((BigDecimal) candidate.get("packageQty")).signum() <= 0) continue;
            Long warehouseId = ((Number) candidate.get("warehouseId")).longValue();
            Long batchId = ((Number) candidate.get("batchId")).longValue();
            List<Map<String, Object>> labels = jdbcTemplate.queryForList("""
                    SELECT DISTINCT qpl.label_id AS labelId, qpl.label_no AS labelNo, qpl.status
                      FROM quota_package_label qpl
                      JOIN quota_package_label_source src ON src.label_id = qpl.label_id
                     WHERE qpl.status IN ('pending_print', 'available', 'delivered', 'signed') AND qpl.warehouse_id = ?
                       AND qpl.product_id = ? AND src.batch_id = ?
                     FOR UPDATE
                    """, warehouseId, productId, batchId);
            for (Map<String, Object> label : labels) {
                Long labelId = ((Number) label.get("labelId")).longValue();
                String statusBefore = String.valueOf(label.get("status"));
                if ("delivered".equals(statusBefore)) {
                    List<Long> deliveries = jdbcTemplate.queryForList("SELECT delivery_id FROM spd_delivery_package_binding WHERE label_id = ? AND recalled = 0 FOR UPDATE", Long.class, labelId);
                    jdbcTemplate.update("UPDATE spd_delivery_package_binding SET recalled = 1 WHERE label_id = ?", labelId);
                    for (Long delivery : deliveries) reconcileRecallDelivery(delivery);
                }
                List<Map<String, Object>> sources = jdbcTemplate.queryForList("""
                        SELECT batch_id AS batchId, source_qty AS sourceQty, warehouse_id AS warehouseId
                          FROM quota_package_label_source WHERE label_id = ?
                        """, labelId);
                BigDecimal unpacked = BigDecimal.ZERO;
                for (Map<String, Object> source : sources) {
                    BigDecimal sourceQty = (BigDecimal) source.get("sourceQty");
                    if (!"signed".equals(statusBefore)) {
                        support.receiveAvailable(warehouseId, productId,
                                ((Number) source.get("batchId")).longValue(), sourceQty,
                                "quota_unpack_in", "recall_event", recallId, "召回自动解包：" + reason);
                    }
                    unpacked = unpacked.add(sourceQty);
                }
                jdbcTemplate.update("UPDATE quota_package_label SET status = 'void', version = version + 1 WHERE label_id = ? AND status IN ('pending_print', 'available', 'delivered', 'signed')", labelId);
                jdbcTemplate.update("""
                        UPDATE udi_trace_code utc
                        JOIN quota_package_label qpl ON qpl.trace_code_id = utc.trace_code_id
                           SET utc.current_status = 'unpacked', utc.package_status = 'void',
                               utc.last_event_name = '召回自动解包', utc.last_event_time = NOW()
                         WHERE qpl.label_id = ? AND utc.trace_scope = 'low_value_quota_pack'
                        """, labelId);
                jdbcTemplate.update("""
                        INSERT INTO quota_package_event (event_no, label_id, event_type, status_before, status_after, qty_change, remark)
                        VALUES (?, ?, 'recall_unpack_to_loose', ?, 'void', ?, ?)
                        """, support.nextNo(QUOTA_PACKAGE_EVENT), labelId, statusBefore, unpacked, "召回自动解包：" + reason);
            }
        }
    }

    private void releaseRecallReservations(List<Map<String, Object>> candidates, Long productId) {
        for (Map<String, Object> candidate : candidates) {
            List<Map<String, Object>> reserved = jdbcTemplate.queryForList("""
                    SELECT r.reservation_id AS reservationId, r.task_id AS taskId,
                           r.balance_id AS balanceId, r.reserved_qty AS quantity
                      FROM quota_packing_task_reservation r
                      JOIN inventory_balance bal ON bal.balance_id = r.balance_id
                     WHERE bal.warehouse_id = ? AND bal.product_id = ? AND bal.batch_id = ?
                       AND r.status = 'reserved' ORDER BY r.reservation_id FOR UPDATE
                    """, candidate.get("warehouseId"), productId, candidate.get("batchId"));
            for (Map<String, Object> reservation : reserved) {
                support.releaseLockedToAvailable(((Number) reservation.get("balanceId")).longValue(), (BigDecimal) reservation.get("quantity"));
                jdbcTemplate.update("UPDATE quota_packing_task_reservation SET status = 'released' WHERE reservation_id = ? AND status = 'reserved'", reservation.get("reservationId"));
                jdbcTemplate.update("UPDATE quota_packing_task SET status = 'need_recalculate', reserved_loose_qty = GREATEST(reserved_loose_qty - ?, 0) WHERE task_id = ? AND status IN ('pending_confirm', 'need_recalculate')", reservation.get("quantity"), reservation.get("taskId"));
            }
        }
    }

    private List<Long> restrictTraceUnits(Long sourceWarehouse, Long productId, Long batchId,
                                         String recallNo, String reason, boolean recall) {
        List<Long> units = jdbcTemplate.queryForList("""
                SELECT trace.trace_code_id FROM inventory_batch_trace_code trace
                  JOIN inventory_batch batch ON batch.batch_id = trace.batch_id
                  JOIN udi_trace_code code ON code.trace_code_id = trace.trace_code_id AND code.trace_scope = 'high_value'
                 WHERE trace.current_warehouse_id = ? AND trace.batch_id = ? AND batch.product_id = ?
                   AND trace.lifecycle_status NOT IN ('consumed', 'settled', 'void', 'cancelled', 'isolated', 'recall_in_transit')
                 ORDER BY trace.trace_code_id FOR UPDATE
                """, Long.class, sourceWarehouse, batchId, productId);
        String status = recall ? "recall_in_transit" : "isolated";
        String eventName = recall ? "召回退库出库" : "库存隔离";
        for (Long traceId : units) {
            List<Long> deliveries = jdbcTemplate.queryForList("SELECT delivery_id FROM spd_delivery_trace_code WHERE trace_code_id = ? AND recalled = 0 FOR UPDATE", Long.class, traceId);
            jdbcTemplate.update("UPDATE spd_delivery_trace_code SET recalled = 1 WHERE trace_code_id = ?", traceId);
            for (Long delivery : deliveries) reconcileRecallDelivery(delivery);
            jdbcTemplate.update("UPDATE inventory_batch_trace_code SET lifecycle_status = ? WHERE trace_code_id = ?", status, traceId);
            jdbcTemplate.update("""
                    UPDATE udi_trace_code code JOIN warehouse w ON w.warehouse_id = ?
                      LEFT JOIN sys_dept d ON d.dept_id = w.dept_id
                       SET code.current_status = ?, code.risk_level = 'exception',
                           code.current_location = w.warehouse_name, code.current_department = d.dept_name,
                           code.last_event_name = ?, code.last_event_time = NOW()
                     WHERE code.trace_code_id = ?
                    """, sourceWarehouse, status, eventName, traceId);
            writeRecallTraceEvent(traceId, recallNo, recall ? "recall_dispatch" : "recall_isolation", eventName, reason);
        }
        return units;
    }

    private List<InventoryEventCommand.TraceLink> traceLinks(List<Long> traces) {
        return traces.stream().map(id -> new InventoryEventCommand.TraceLink(id, "high_value_unit", BigDecimal.ONE)).toList();
    }

    private void writeRecallTraceEvent(Long traceId, String recallNo, String type, String name, String remark) {
        jdbcTemplate.update("""
                INSERT INTO udi_trace_event (trace_code_id, event_no, event_type, event_name, biz_no, status, remark, sort_order, event_time)
                VALUES (?, ?, ?, ?, ?, 'done', ?, 80, NOW())
                """, traceId, support.nextNo(com.hospital.spd.common.service.DocumentKind.UDI_TRACE_EVENT), type, name, recallNo, remark);
    }

    private void reconcileRecallDelivery(Long deliveryId) {
        jdbcTemplate.update("""
                UPDATE spd_delivery_order d SET quantity =
                  CASE WHEN d.delivery_type = 'package' THEN
                    (SELECT COALESCE(SUM(b.package_quantity), 0) FROM spd_delivery_package_binding b WHERE b.delivery_id = d.delivery_id AND b.recalled = 0)
                  ELSE (SELECT COUNT(*) FROM spd_delivery_trace_code t WHERE t.delivery_id = d.delivery_id AND t.recalled = 0) END
                 WHERE d.delivery_id = ? AND d.status = 'picked'
                """, deliveryId);
        jdbcTemplate.update("UPDATE spd_delivery_order SET status = 'cancelled' WHERE delivery_id = ? AND status = 'picked' AND quantity = 0", deliveryId);
        jdbcTemplate.update("""
                UPDATE department_requisition r JOIN spd_delivery_order d ON d.requisition_no = r.requisition_no
                   SET r.status = CASE WHEN
                       (SELECT COALESCE(SUM(active.quantity), 0) FROM spd_delivery_order active
                         WHERE active.requisition_no = r.requisition_no AND active.status <> 'cancelled') = 0
                       THEN 'approved' ELSE 'partial_picked' END
                 WHERE d.delivery_id = ? AND r.status IN ('picked', 'partial_picked')
                """, deliveryId);
    }

    private static String normalizeScope(String value) {
        String scope = value == null ? "all" : value.trim();
        if (!List.of("all", "primary", "secondary", "tertiary").contains(scope)) {
            throw new IllegalArgumentException("召回范围不正确");
        }
        return scope;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long findWarehouseId(String warehouseName) {
        return jdbcTemplate.queryForObject("""
                SELECT warehouse_id FROM warehouse
                 WHERE warehouse_name = ? AND deleted = 0 AND status = 1
                """, Long.class, warehouseName);
    }

    private static String requireText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(key + " is required");
        return String.valueOf(value).trim();
    }

    private static BigDecimal requirePositive(Map<String, Object> body, String key) {
        BigDecimal value = new BigDecimal(requireText(body, key));
        if (value.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(key + " must be greater than zero");
        return com.hospital.spd.common.QuantityRules.positive(value, "数量");
    }

    private static Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Map<String, Object> findProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_price AS purchasePrice, is_high_value AS highValue
                  FROM product WHERE product_code = ? AND deleted = 0 AND status = 1 FOR UPDATE
                """, productCode);
    }

}
