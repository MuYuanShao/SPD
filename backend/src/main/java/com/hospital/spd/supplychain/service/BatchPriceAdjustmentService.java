package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.common.service.InventoryEventService;
import com.hospital.spd.supplychain.BatchPriceAdjustmentRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.hospital.spd.common.service.DocumentKind.BATCH_PRICE_ADJUSTMENT;

/** Owns batch-price changes and their immutable inventory valuation events. */
@Service
public class BatchPriceAdjustmentService {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final OperatorContextProvider operatorContextProvider;
    private final ApprovalFlowGuard approvalFlowGuard;
    private final InventoryEventService inventoryEventService;
    private final AuditLogService auditLogService;

    public BatchPriceAdjustmentService(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                                       OperatorContextProvider operatorContextProvider,
                                       ApprovalFlowGuard approvalFlowGuard,
                                       InventoryEventService inventoryEventService,
                                       AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.operatorContextProvider = operatorContextProvider;
        this.approvalFlowGuard = approvalFlowGuard;
        this.inventoryEventService = inventoryEventService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Map<String, Object> create(BatchPriceAdjustmentRequest request) {
        if (request.systemBatchNo() == null || request.systemBatchNo().isBlank()) {
            throw new IllegalArgumentException("系统批次号不能为空");
        }
        if (request.newUnitPrice() == null || request.newUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("新批次单价必须大于 0");
        }
        Map<String, Object> batch = jdbcTemplate.queryForMap("""
                SELECT batch_id AS batchId, system_batch_no AS systemBatchNo,
                       batch_unit_price AS oldUnitPrice
                  FROM inventory_batch
                 WHERE system_batch_no = ?
                """, request.systemBatchNo().trim());
        BigDecimal affectedQty = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(available_qty + locked_qty + in_transit_qty + isolated_qty), 0) FROM inventory_balance WHERE batch_id = ?",
                BigDecimal.class, batch.get("batchId"));
        String adjustmentNo = support.nextNo(BATCH_PRICE_ADJUSTMENT);
        jdbcTemplate.update("""
                INSERT INTO batch_price_adjustment (
                  adjustment_no, batch_id, old_unit_price, new_unit_price, affected_qty, reason, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'draft')
                """, adjustmentNo, batch.get("batchId"), batch.get("oldUnitPrice"), request.newUnitPrice(),
                affectedQty, text(request.reason()));
        Long adjustmentId = jdbcTemplate.queryForObject(
                "SELECT adjustment_id FROM batch_price_adjustment WHERE adjustment_no = ?", Long.class, adjustmentNo);
        auditLogService.record("batch_price_adjustment", "create", adjustmentId, adjustmentNo, "创建批次调价单");
        return Map.of("adjustmentNo", adjustmentNo, "affectedQty", affectedQty);
    }

    @Transactional
    public Map<String, Object> approve(String adjustmentNo) {
        OperatorContext operator = operatorContextProvider.current();
        Map<String, Object> adjustment = jdbcTemplate.queryForMap("""
                SELECT adjustment_id AS adjustmentId, batch_id AS batchId,
                       old_unit_price AS oldUnitPrice, new_unit_price AS newUnitPrice, reason, status
                  FROM batch_price_adjustment
                 WHERE adjustment_no = ?
                 FOR UPDATE
                """, adjustmentNo);
        if (!"draft".equals(String.valueOf(adjustment.get("status")))) {
            throw new IllegalArgumentException("调价单已处理");
        }
        approvalFlowGuard.requireApprovalAccess("batch-price-adjustment", "price-adjustment-approval", null, null);
        Long batchId = ((Number) adjustment.get("batchId")).longValue();
        jdbcTemplate.queryForList("SELECT balance_id FROM inventory_balance WHERE batch_id = ? FOR UPDATE", batchId);
        BigDecimal oldPrice = (BigDecimal) adjustment.get("oldUnitPrice");
        BigDecimal newPrice = (BigDecimal) adjustment.get("newUnitPrice");
        jdbcTemplate.update("UPDATE inventory_batch SET batch_unit_price = ? WHERE batch_id = ?", newPrice, batchId);
        Long adjustmentId = ((Number) adjustment.get("adjustmentId")).longValue();
        List<Long> eventIds = inventoryEventService.recordValuationEvents(adjustmentId, batchId, oldPrice, newPrice,
                "批次调价：" + adjustmentNo);
        int updated = jdbcTemplate.update("UPDATE batch_price_adjustment SET status = 'approved', approve_time = NOW(), approve_by = ? WHERE adjustment_id = ? AND status = 'draft'",
                operator.userId(), adjustmentId);
        if (updated != 1) throw new IllegalArgumentException("调价单已处理");
        auditLogService.record("batch_price_adjustment", "approve", adjustmentId, adjustmentNo,
                "批次调价审批通过，生成价值流水 " + eventIds.size() + " 条");
        return Map.of("adjustmentNo", adjustmentNo, "status", "approved", "valuationEventCount", eventIds.size());
    }

    public Map<String, Object> list(Map<String, String> params) {
        PageRequest page = PageRequest.from(params);
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
                """, page.size(), page.offset());
        return PageResponse.of(rows, total == null ? 0 : total, page);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
