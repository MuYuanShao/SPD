package com.hospital.spd.common.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hospital.spd.supplychain.InventoryTransactionType;

/**
 * Records immutable inventory events after stock-affecting services complete their balance updates.
 */
@Service
public class InventoryEventService {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentNumberService documentNumberService;
    private final OperatorContextProvider operatorContextProvider;

    public InventoryEventService(JdbcTemplate jdbcTemplate,
                                 DocumentNumberService documentNumberService,
                                 OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentNumberService = documentNumberService;
        this.operatorContextProvider = operatorContextProvider;
    }

    /**
     * Persists an immutable inventory event after the caller has updated stock and computed the balance.
     */
    public Long record(String eventType, String sourceType, Long sourceId, Long warehouseId, Long productId,
                       Long batchId, BigDecimal qtyChange, BigDecimal qtyAfter, String remark) {
        return record(InventoryEventCommand.quantity(eventType, sourceType, sourceId, warehouseId, productId,
                batchId, qtyChange, qtyAfter, remark));
    }

    /** Persists a fully materialized quantity event so later master-data changes cannot rewrite history. */
    public Long record(InventoryEventCommand command) {
        OperatorContext operator = operatorContextProvider.current();
        Map<String, Object> snapshot = loadSnapshot(command.warehouseId(), command.productId(), command.batchId());
        InventoryTransactionType transactionType = InventoryTransactionType.fromEvent(
                command.eventType(), String.valueOf(snapshot.get("warehouseType")), command.quantityChange().signum());
        BigDecimal unitPrice = (BigDecimal) snapshot.get("unitPrice");
        BigDecimal amount = command.quantityChange().multiply(unitPrice);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO inventory_event (
                      event_no, event_type, transaction_type_code, event_category,
                      source_biz_type, source_biz_id, dept_id_snapshot, dept_name_snapshot,
                      warehouse_id, warehouse_code_snapshot, warehouse_name_snapshot,
                      product_id, product_code_snapshot, product_name_snapshot, spec_model_snapshot,
                      registration_no_snapshot, unit_snapshot, manufacturer_name_snapshot,
                      supplier_id_snapshot, supplier_name_snapshot,
                      batch_id, system_batch_no_snapshot, production_batch_no_snapshot,
                      qty_change, qty_after, unit_price_snapshot, amount_snapshot, snapshot_origin,
                      operator_id, remark
                    ) VALUES (?, ?, ?, 'quantity', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'captured', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, documentNumberService.next("KC", 5, "inventory_event", "event_no"));
            ps.setString(2, command.eventType());
            ps.setString(3, transactionType.code());
            ps.setString(4, command.sourceBizType());
            ps.setLong(5, command.sourceBizId());
            setNullableLong(ps, 6, snapshot.get("deptId"));
            ps.setString(7, (String) snapshot.get("deptName"));
            ps.setLong(8, command.warehouseId());
            ps.setString(9, (String) snapshot.get("warehouseCode"));
            ps.setString(10, (String) snapshot.get("warehouseName"));
            ps.setLong(11, command.productId());
            ps.setString(12, (String) snapshot.get("productCode"));
            ps.setString(13, (String) snapshot.get("productName"));
            ps.setString(14, (String) snapshot.get("specModel"));
            ps.setString(15, (String) snapshot.get("registrationNo"));
            ps.setString(16, (String) snapshot.get("unit"));
            ps.setString(17, (String) snapshot.get("manufacturerName"));
            setNullableLong(ps, 18, snapshot.get("supplierId"));
            ps.setString(19, (String) snapshot.get("supplierName"));
            ps.setLong(20, command.batchId());
            ps.setString(21, (String) snapshot.get("systemBatchNo"));
            ps.setString(22, (String) snapshot.get("productionBatchNo"));
            ps.setBigDecimal(23, command.quantityChange());
            ps.setBigDecimal(24, command.quantityAfter());
            ps.setBigDecimal(25, unitPrice);
            ps.setBigDecimal(26, amount);
            ps.setLong(27, operator.userId());
            ps.setString(28, command.remark());
            return ps;
        }, keyHolder);
        Long eventId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        for (InventoryEventCommand.TraceLink link : command.traceLinks()) {
            jdbcTemplate.update("""
                    INSERT INTO inventory_event_trace_code (event_id, trace_code_id, trace_type, linked_quantity)
                    VALUES (?, ?, ?, ?)
                    """, eventId, link.traceCodeId(), link.traceType(), link.linkedQuantity());
        }
        return eventId;
    }

    /** Adds stable trace identities in the same business transaction after their IDs have been created. */
    public void linkTraceCodes(Long eventId, List<InventoryEventCommand.TraceLink> traceLinks) {
        if (eventId == null || traceLinks == null) return;
        for (InventoryEventCommand.TraceLink link : traceLinks) {
            jdbcTemplate.update("""
                    INSERT INTO inventory_event_trace_code (event_id, trace_code_id, trace_type, linked_quantity)
                    VALUES (?, ?, ?, ?)
                    """, eventId, link.traceCodeId(), link.traceType(), link.linkedQuantity());
        }
    }

    /** Records one valuation event per warehouse affected by an approved batch price adjustment. */
    public List<Long> recordValuationEvents(Long adjustmentId, Long batchId, BigDecimal oldPrice,
                                            BigDecimal newPrice, String remark) {
        List<Map<String, Object>> warehouses = jdbcTemplate.queryForList("""
                SELECT bal.warehouse_id AS warehouseId,
                       SUM(bal.available_qty) AS availableQty,
                       SUM(bal.available_qty + bal.locked_qty + bal.in_transit_qty + bal.isolated_qty) AS affectedQty
                  FROM inventory_balance bal
                 WHERE bal.batch_id = ?
                 GROUP BY bal.warehouse_id
                HAVING affectedQty <> 0
                 ORDER BY bal.warehouse_id
                """, batchId);
        return warehouses.stream().map(row -> recordValuationEvent(adjustmentId, batchId,
                ((Number) row.get("warehouseId")).longValue(), (BigDecimal) row.get("availableQty"),
                (BigDecimal) row.get("affectedQty"), oldPrice, newPrice, remark)).toList();
    }

    private Long recordValuationEvent(Long adjustmentId, Long batchId, Long warehouseId, BigDecimal qtyAfter,
                                      BigDecimal affectedQty, BigDecimal oldPrice, BigDecimal newPrice, String remark) {
        OperatorContext operator = operatorContextProvider.current();
        Long productId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM inventory_batch WHERE batch_id = ?", Long.class, batchId);
        Map<String, Object> snapshot = loadSnapshot(warehouseId, productId, batchId);
        BigDecimal valueChange = newPrice.subtract(oldPrice).multiply(affectedQty);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO inventory_event (
                      event_no, event_type, transaction_type_code, event_category,
                      source_biz_type, source_biz_id, dept_id_snapshot, dept_name_snapshot,
                      warehouse_id, warehouse_code_snapshot, warehouse_name_snapshot,
                      product_id, product_code_snapshot, product_name_snapshot, spec_model_snapshot,
                      registration_no_snapshot, unit_snapshot, manufacturer_name_snapshot,
                      supplier_id_snapshot, supplier_name_snapshot,
                      batch_id, system_batch_no_snapshot, production_batch_no_snapshot,
                      qty_change, qty_after, unit_price_snapshot, amount_snapshot,
                      old_unit_price, new_unit_price, affected_qty_snapshot, value_change,
                      snapshot_origin, operator_id, remark
                    ) VALUES (?, 'batch_price_adjustment', 'batch_price_adjustment', 'valuation',
                      'batch_price_adjustment', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      0, ?, ?, 0, ?, ?, ?, ?, 'captured', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, documentNumberService.next("KC", 5, "inventory_event", "event_no"));
            ps.setLong(2, adjustmentId);
            setNullableLong(ps, 3, snapshot.get("deptId"));
            ps.setString(4, (String) snapshot.get("deptName"));
            ps.setLong(5, warehouseId);
            ps.setString(6, (String) snapshot.get("warehouseCode"));
            ps.setString(7, (String) snapshot.get("warehouseName"));
            ps.setLong(8, productId);
            ps.setString(9, (String) snapshot.get("productCode"));
            ps.setString(10, (String) snapshot.get("productName"));
            ps.setString(11, (String) snapshot.get("specModel"));
            ps.setString(12, (String) snapshot.get("registrationNo"));
            ps.setString(13, (String) snapshot.get("unit"));
            ps.setString(14, (String) snapshot.get("manufacturerName"));
            setNullableLong(ps, 15, snapshot.get("supplierId"));
            ps.setString(16, (String) snapshot.get("supplierName"));
            ps.setLong(17, batchId);
            ps.setString(18, (String) snapshot.get("systemBatchNo"));
            ps.setString(19, (String) snapshot.get("productionBatchNo"));
            ps.setBigDecimal(20, qtyAfter);
            ps.setBigDecimal(21, newPrice);
            ps.setBigDecimal(22, oldPrice);
            ps.setBigDecimal(23, newPrice);
            ps.setBigDecimal(24, affectedQty);
            ps.setBigDecimal(25, valueChange);
            ps.setLong(26, operator.userId());
            ps.setString(27, remark);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private Map<String, Object> loadSnapshot(Long warehouseId, Long productId, Long batchId) {
        return jdbcTemplate.queryForMap("""
                SELECT w.warehouse_code AS warehouseCode, w.warehouse_name AS warehouseName,
                       w.warehouse_type AS warehouseType, w.dept_id AS deptId, d.dept_name AS deptName,
                       p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, p.registration_no AS registrationNo, p.unit,
                       m.manufacturer_name AS manufacturerName,
                       ib.supplier_id AS supplierId, s.supplier_name AS supplierName,
                       ib.system_batch_no AS systemBatchNo, ib.production_batch_no AS productionBatchNo,
                       ib.batch_unit_price AS unitPrice
                  FROM warehouse w
                  JOIN product p ON p.product_id = ?
                  JOIN inventory_batch ib ON ib.batch_id = ? AND ib.product_id = p.product_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = ib.supplier_id
                 WHERE w.warehouse_id = ?
                """, productId, batchId, warehouseId);
    }

    private static void setNullableLong(PreparedStatement ps, int index, Object value) throws java.sql.SQLException {
        if (value == null) ps.setNull(index, java.sql.Types.BIGINT);
        else ps.setLong(index, ((Number) value).longValue());
    }
}
