package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.supplychain.*;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import static com.hospital.spd.common.service.DocumentKind.*;
import static com.hospital.spd.common.SqlHelper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/**
 * Serves inventory balances, batch data, stocktaking, price adjustment, and inventory event queries.
 */
@Service
public class InventoryService {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final OperatorContextProvider operatorContextProvider;
    private final ApprovalFlowGuard approvalFlowGuard;

    public InventoryService(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, OperatorContext::system, new ApprovalFlowGuard(jdbcTemplate));
    }

    public InventoryService(JdbcTemplate jdbcTemplate,
                            SupplyChainSupport support,
                            OperatorContextProvider operatorContextProvider) {
        this(jdbcTemplate, support, operatorContextProvider, new ApprovalFlowGuard(jdbcTemplate, operatorContextProvider));
    }

    @Autowired
    public InventoryService(JdbcTemplate jdbcTemplate,
                            SupplyChainSupport support,
                            OperatorContextProvider operatorContextProvider,
                            ApprovalFlowGuard approvalFlowGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.operatorContextProvider = operatorContextProvider;
        this.approvalFlowGuard = approvalFlowGuard;
    }

    // ===== 公开方法 =====

    public Map<String, Object> balances(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE (bal.available_qty <> 0
                    OR bal.locked_qty <> 0
                    OR bal.in_transit_qty <> 0
                    OR bal.isolated_qty <> 0)
                """);
        appendLike(where, args, "w.warehouse_name", params.get("warehouseName"));
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));
        appendLike(where, args, "ib.system_batch_no", params.get("systemBatchNo"));

        String fromClause = """
                  FROM inventory_balance bal
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  JOIN product p ON p.product_id = bal.product_id
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT bal.balance_id AS balanceId, w.warehouse_name AS warehouseName,
                       p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                       ib.system_batch_no AS systemBatchNo, ib.production_batch_no AS productionBatchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       ib.batch_unit_price AS batchUnitPrice, bal.available_qty AS availableQty,
                       bal.locked_qty AS lockedQty, bal.in_transit_qty AS inTransitQty,
                       bal.isolated_qty AS isolatedQty, ib.ownership_type AS ownershipType,
                       ib.settlement_mode AS settlementMode, DATE_FORMAT(bal.update_time, '%Y-%m-%d %H:%i') AS updateTime
                  FROM inventory_balance bal
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  JOIN product p ON p.product_id = bal.product_id
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                """ + where + " ORDER BY bal.update_time DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());

        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS batchCount,
                       COALESCE(SUM(available_qty), 0) AS availableQty,
                       COALESCE(SUM(locked_qty), 0) AS lockedQty,
                       COALESCE(SUM(isolated_qty), 0) AS isolatedQty
                  FROM inventory_balance
                 WHERE available_qty <> 0
                    OR locked_qty <> 0
                    OR in_transit_qty <> 0
                    OR isolated_qty <> 0
                """);
        return PageResponse.of(rows, total == null ? 0 : total, pageReq, summary);
    }

    public Map<String, Object> events(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1
                """);
        appendLike(where, args, "ie.event_type", params.get("eventType"));
        appendLike(where, args, "d.dept_name", params.get("deptName"));
        appendLike(where, args, "w.warehouse_name", params.get("warehouseName"));
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));
        appendLike(where, args, "ib.system_batch_no", params.get("batchNo"));
        appendLike(where, args, "ib.production_batch_no", params.get("productionBatchNo"));
        appendLike(where, args, "m.manufacturer_name", params.get("manufacturerName"));
        appendLike(where, args, "s.supplier_name", params.get("supplierName"));
        appendTimeRange(where, args, params.get("startTime"), params.get("endTime"));

        String fromClause = """
                  FROM inventory_event ie
                  JOIN warehouse w ON w.warehouse_id = ie.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id AND d.deleted = 0
                  JOIN product p ON p.product_id = ie.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id AND m.deleted = 0
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id AND s.deleted = 0
                  JOIN inventory_batch ib ON ib.batch_id = ie.batch_id
                  LEFT JOIN inventory_batch_trace_code ibtc ON ibtc.batch_id = ie.batch_id
                  LEFT JOIN udi_trace_code utc ON utc.trace_code_id = ibtc.trace_code_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ie.event_no AS eventNo, ie.event_type AS eventType,
                       COALESCE(d.dept_name, '-') AS deptName,
                       w.warehouse_name AS warehouseName, p.product_code AS productCode,
                       p.product_name AS productName, COALESCE(p.spec_model, '-') AS specModel,
                       COALESCE(p.registration_no, '-') AS registrationNo,
                       ib.system_batch_no AS batchNo, COALESCE(ib.production_batch_no, '-') AS productionBatchNo,
                       ib.batch_unit_price AS unitPrice, p.unit AS unit,
                       ie.qty_change AS qtyChange,
                       ROUND(ie.qty_change * COALESCE(ib.batch_unit_price, 0), 2) AS amount,
                       ie.qty_after AS qtyAfter,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       COALESCE(s.supplier_name, '-') AS supplierName,
                       COALESCE(utc.udi_code, utc.unique_code, '-') AS traceCode,
                       COALESCE(utc.udi_code, '-') AS udiCode,
                       ie.remark, DATE_FORMAT(ie.event_time, '%Y-%m-%d %H:%i') AS eventTime
                  FROM inventory_event ie
                  JOIN warehouse w ON w.warehouse_id = ie.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id AND d.deleted = 0
                  JOIN product p ON p.product_id = ie.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id AND m.deleted = 0
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id AND s.deleted = 0
                  JOIN inventory_batch ib ON ib.batch_id = ie.batch_id
                  LEFT JOIN inventory_batch_trace_code ibtc ON ibtc.batch_id = ie.batch_id
                  LEFT JOIN udi_trace_code utc ON utc.trace_code_id = ibtc.trace_code_id
                """ + where + " ORDER BY d.dept_name, ie.event_time DESC, ie.event_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    /** 时间段过滤：按发生时间区间（yyyy-MM-dd，含起止当天） */
    private static void appendTimeRange(StringBuilder sql, List<Object> args, String startTime, String endTime) {
        if (!isBlank(startTime)) {
            sql.append(" AND ie.event_time >= ?");
            args.add(startTime.trim() + " 00:00:00");
        }
        if (!isBlank(endTime)) {
            sql.append(" AND ie.event_time <= ?");
            args.add(endTime.trim() + " 23:59:59");
        }
    }

    public Map<String, Object> batches(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1
                """);
        appendLike(where, args, "ib.system_batch_no", params.get("systemBatchNo"));
        appendLike(where, args, "p.product_name", params.get("productName"));

        String fromClause = """
                  FROM inventory_batch ib
                  JOIN product p ON p.product_id = ib.product_id
                  LEFT JOIN inventory_balance bal ON bal.batch_id = ib.batch_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT ib.batch_id) " + fromClause + where,
                Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ib.batch_id AS batchId, ib.system_batch_no AS systemBatchNo,
                       p.product_code AS productCode, p.product_name AS productName,
                       ib.production_batch_no AS productionBatchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       ib.batch_unit_price AS batchUnitPrice,
                       COALESCE(SUM(bal.available_qty), 0) AS availableQty,
                       ib.ownership_type AS ownershipType, ib.settlement_mode AS settlementMode
                  FROM inventory_batch ib
                  JOIN product p ON p.product_id = ib.product_id
                  LEFT JOIN inventory_balance bal ON bal.batch_id = ib.batch_id
                """ + where + " GROUP BY ib.batch_id ORDER BY ib.create_time DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    @Transactional
    public Map<String, Object> createStocktaking(StocktakingRequest request) {
        Map<String, Object> row = findBalance(request.warehouseName(), request.systemBatchNo());
        BigDecimal systemQty = (BigDecimal) row.get("availableQty");
        BigDecimal actualQty = request.actualQty() == null ? BigDecimal.ZERO : request.actualQty();
        BigDecimal diffQty = actualQty.subtract(systemQty);
        String stocktakingNo = support.nextNo(INVENTORY_STOCKTAKING);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO inventory_stocktaking (stocktaking_no, warehouse_id, stocktaking_type, status, reason)
                    VALUES (?, ?, '动态盘', 'draft', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, stocktakingNo);
            ps.setLong(2, ((Number) row.get("warehouseId")).longValue());
            ps.setString(3, nullIfBlank(request.reason()));
            return ps;
        }, keyHolder);
        Long stocktakingId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        jdbcTemplate.update("""
                INSERT INTO inventory_stocktaking_item (
                  stocktaking_id, balance_id, product_id, batch_id, system_qty, actual_qty, diff_qty, diff_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, stocktakingId, row.get("balanceId"), row.get("productId"), row.get("batchId"), systemQty,
                actualQty, diffQty, nullIfBlank(request.reason()));
        writeAudit("create_stocktaking", stocktakingId, stocktakingNo, "create stocktaking");
        return Map.of("stocktakingNo", stocktakingNo, "diffQty", diffQty);
    }

    @Transactional
    public Map<String, Object> approveStocktaking(String stocktakingNo) {
        OperatorContext operator = operatorContextProvider.current();
        Map<String, Object> doc = jdbcTemplate.queryForMap("""
                SELECT stocktaking_id AS stocktakingId, warehouse_id AS warehouseId, status
                  FROM inventory_stocktaking
                 WHERE stocktaking_no = ?
                 FOR UPDATE
                """, stocktakingNo);
        if (!"draft".equals(String.valueOf(doc.get("status")))) {
            throw new IllegalArgumentException("stocktaking document has already been processed");
        }
        approvalFlowGuard.requireApprovalAccess("stocktaking-management", "stocktaking-approval", null, null);
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT item_id AS itemId, balance_id AS balanceId, product_id AS productId,
                       batch_id AS batchId, diff_qty AS diffQty, actual_qty AS actualQty
                  FROM inventory_stocktaking_item
                 WHERE stocktaking_id = ?
                """, doc.get("stocktakingId"));
        for (Map<String, Object> item : items) {
            BigDecimal diffQty = (BigDecimal) item.get("diffQty");
            if (diffQty == null || diffQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            support.adjustAvailable(((Number) item.get("balanceId")).longValue(),
                    ((Number) doc.get("warehouseId")).longValue(),
                    ((Number) item.get("productId")).longValue(),
                    ((Number) item.get("batchId")).longValue(),
                    diffQty,
                    diffQty.compareTo(BigDecimal.ZERO) >= 0 ? "stocktaking_profit" : "stocktaking_loss",
                    "inventory_stocktaking", ((Number) doc.get("stocktakingId")).longValue(),
                    "stocktaking approval generated inventory adjustment");
        }
        int updated = jdbcTemplate.update("UPDATE inventory_stocktaking SET status = 'approved', approve_time = NOW(), approve_by = ? WHERE stocktaking_no = ? AND status = 'draft'",
                operator.userId(), stocktakingNo);
        requireSingleStateChange(updated, "stocktaking document has already been processed");
        writeAudit("approve_stocktaking", ((Number) doc.get("stocktakingId")).longValue(), stocktakingNo, "盘点复核通过");
        return Map.of("stocktakingNo", stocktakingNo, "status", "approved");
    }

    @Transactional
    public Map<String, Object> createPriceAdjustment(BatchPriceAdjustmentRequest request) {
        Map<String, Object> batch = jdbcTemplate.queryForMap("""
                SELECT batch_id AS batchId, system_batch_no AS systemBatchNo,
                       batch_unit_price AS oldUnitPrice
                  FROM inventory_batch
                 WHERE system_batch_no = ?
                """, request.systemBatchNo());
        BigDecimal newPrice = request.newUnitPrice();
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("鏂版壒娆″崟浠峰繀椤诲ぇ浜?0");
        }
        BigDecimal affectedQty = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(available_qty + locked_qty + in_transit_qty + isolated_qty), 0) FROM inventory_balance WHERE batch_id = ?",
                BigDecimal.class,
                batch.get("batchId"));
        String adjustmentNo = support.nextNo(BATCH_PRICE_ADJUSTMENT);
        jdbcTemplate.update("""
                INSERT INTO batch_price_adjustment (
                  adjustment_no, batch_id, old_unit_price, new_unit_price, affected_qty, reason, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'draft')
                """, adjustmentNo, batch.get("batchId"), batch.get("oldUnitPrice"), newPrice, affectedQty,
                nullIfBlank(request.reason()));
        writeAudit("create_batch_price_adjustment", ((Number) batch.get("batchId")).longValue(), adjustmentNo, "create batch price adjustment");
        return Map.of("adjustmentNo", adjustmentNo, "affectedQty", affectedQty);
    }

    @Transactional
    public Map<String, Object> approvePriceAdjustment(String adjustmentNo) {
        OperatorContext operator = operatorContextProvider.current();
        Map<String, Object> adjustment = jdbcTemplate.queryForMap("""
                SELECT adjustment_id AS adjustmentId, batch_id AS batchId, new_unit_price AS newUnitPrice, status
                  FROM batch_price_adjustment
                 WHERE adjustment_no = ?
                 FOR UPDATE
                """, adjustmentNo);
        if (!"draft".equals(String.valueOf(adjustment.get("status")))) {
            throw new IllegalArgumentException("调价单已处理");
        }
        approvalFlowGuard.requireApprovalAccess("batch-price-adjustment", "price-adjustment-approval", null, null);
        jdbcTemplate.update("UPDATE inventory_batch SET batch_unit_price = ? WHERE batch_id = ?",
                adjustment.get("newUnitPrice"), adjustment.get("batchId"));
        int updated = jdbcTemplate.update("UPDATE batch_price_adjustment SET status = 'approved', approve_time = NOW(), approve_by = ? WHERE adjustment_no = ? AND status = 'draft'",
                operator.userId(), adjustmentNo);
        requireSingleStateChange(updated, "price adjustment has already been processed");
        writeAudit("approve_batch_price_adjustment", ((Number) adjustment.get("batchId")).longValue(), adjustmentNo,
                "batch price adjustment approved");
        return Map.of("adjustmentNo", adjustmentNo, "status", "approved");
    }

    public Map<String, Object> stocktakingList(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        String fromClause = """
                  FROM inventory_stocktaking st
                  JOIN warehouse w ON w.warehouse_id = st.warehouse_id
                  LEFT JOIN inventory_stocktaking_item si ON si.stocktaking_id = st.stocktaking_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT st.stocktaking_id) " + fromClause, Long.class);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT st.stocktaking_no AS stocktakingNo, w.warehouse_name AS warehouseName,
                       st.stocktaking_type AS stocktakingType, st.status, st.reason,
                       DATE_FORMAT(st.create_time, '%Y-%m-%d %H:%i') AS createTime,
                       COALESCE(SUM(si.diff_qty), 0) AS diffQty
                  FROM inventory_stocktaking st
                  JOIN warehouse w ON w.warehouse_id = st.warehouse_id
                  LEFT JOIN inventory_stocktaking_item si ON si.stocktaking_id = st.stocktaking_id
                 GROUP BY st.stocktaking_id
                 ORDER BY st.create_time DESC
                 LIMIT ? OFFSET ?
                """, pageReq.size(), pageReq.offset());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    public Map<String, Object> priceAdjustmentList(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM batch_price_adjustment", Long.class);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT pa.adjustment_no AS adjustmentNo, ib.system_batch_no AS systemBatchNo,
                       pa.old_unit_price AS oldUnitPrice, pa.new_unit_price AS newUnitPrice,
                       pa.affected_qty AS affectedQty, pa.status, pa.reason,
                       DATE_FORMAT(pa.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM batch_price_adjustment pa
                  JOIN inventory_batch ib ON ib.batch_id = pa.batch_id
                 ORDER BY pa.create_time DESC
                 LIMIT ? OFFSET ?
                """, pageReq.size(), pageReq.offset());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    // ===== 私有辅助方法 =====

    private Map<String, Object> findBalance(String warehouseName, String systemBatchNo) {
        return jdbcTemplate.queryForMap("""
                SELECT bal.balance_id AS balanceId, bal.warehouse_id AS warehouseId,
                       bal.product_id AS productId, bal.batch_id AS batchId,
                       bal.available_qty AS availableQty
                  FROM inventory_balance bal
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE w.warehouse_name = ? AND ib.system_batch_no = ?
                 ORDER BY bal.balance_id DESC
                 LIMIT 1
                """, warehouseName, systemBatchNo);
    }

    private void writeAudit(String operationType, Long bizId, String bizNo, String remark) {
        support.writeAudit("inventory", operationType, bizId, bizNo, remark);
    }

    private static void requireSingleStateChange(int updated, String message) {
        if (updated != 1) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void appendLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append(" LIKE ?");
            args.add("%" + value.trim() + "%");
        }
    }

}
