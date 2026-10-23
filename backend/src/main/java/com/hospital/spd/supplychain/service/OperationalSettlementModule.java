package com.hospital.spd.supplychain.service;
import com.hospital.spd.specialty.service.HighValueTraceFlowService;
import com.hospital.spd.specialty.service.QuotaPackageTraceFlowService;

import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;


/**
 * Owns settlement review and confirmation inside Operational Closure.
 */
@Service
public class OperationalSettlementModule {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final ApprovalFlowGuard approvalFlowGuard;
    private final HighValueTraceFlowService traceFlowService;
    private final QuotaPackageTraceFlowService quotaPackageTraceFlowService;

    public OperationalSettlementModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, new ApprovalFlowGuard(jdbcTemplate),
                new HighValueTraceFlowService(jdbcTemplate, support, OperatorContext::system),
                new QuotaPackageTraceFlowService(jdbcTemplate, support, OperatorContext::system));
    }

    @Autowired
    public OperationalSettlementModule(JdbcTemplate jdbcTemplate,
                                       SupplyChainSupport support,
                                       ApprovalFlowGuard approvalFlowGuard,
                                       HighValueTraceFlowService traceFlowService,
                                       QuotaPackageTraceFlowService quotaPackageTraceFlowService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.approvalFlowGuard = approvalFlowGuard;
        this.traceFlowService = traceFlowService;
        this.quotaPackageTraceFlowService = quotaPackageTraceFlowService;
    }

    @Transactional
    public Map<String, Object> confirmSettlement(String settlementNo) {
        List<Long> products = jdbcTemplate.queryForList("""
                SELECT DISTINCT item.product_id FROM settlement_bill_item item
                  JOIN settlement_bill bill ON bill.settlement_id = item.settlement_id
                 WHERE bill.settlement_no = ? ORDER BY item.product_id
                """, Long.class, settlementNo);
        new QuotaPackagePricingService(jdbcTemplate).lockProducts(products);
        Map<String, Object> settlement = jdbcTemplate.queryForMap("""
                SELECT settlement_id AS settlementId, status
                  FROM settlement_bill
                 WHERE settlement_no = ?
                 FOR UPDATE
                """, settlementNo);
        if (!"pending_confirm".equals(String.valueOf(settlement.get("status")))) {
            throw new IllegalArgumentException("只有待确认的结算单才能确认");
        }
        approvalFlowGuard.requireApprovalAccess("settlement-reconciliation", "settlement-confirm", null, null);
        int updated = jdbcTemplate.update("""
                UPDATE settlement_bill
                   SET status = 'confirmed', confirm_time = NOW()
                 WHERE settlement_no = ? AND status = 'pending_confirm'
                """, settlementNo);
        if (updated != 1) throw new IllegalArgumentException("结算单已被处理，请刷新后重试");
        List<Long> traceCodeIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT trace_code_id FROM settlement_bill_item WHERE settlement_id = ? AND trace_code_id IS NOT NULL",
                Long.class, settlement.get("settlementId"));
        for (Long traceCodeId : traceCodeIds) {
            if (quotaPackageTraceFlowService.isQuotaPackageTrace(traceCodeId)) {
                quotaPackageTraceFlowService.markSettled(traceCodeId, settlementNo);
            } else {
                traceFlowService.markSettled(traceCodeId, settlementNo);
            }
        }
        // Receiving settlements have no trace identity: revisit consumed packs sourced from those receipts.
        List<Long> receivingPackageTraces = jdbcTemplate.queryForList("""
                SELECT DISTINCT label.trace_code_id
                  FROM settlement_bill_item item
                  JOIN inventory_batch batch ON item.source_biz_type='receiving_order_item'
                    AND batch.receiving_item_id=item.source_biz_id
                  JOIN quota_package_label_source source ON source.batch_id=batch.batch_id
                  JOIN quota_package_label label ON label.label_id=source.label_id
                 WHERE item.settlement_id=? AND label.status='consumed' AND label.trace_code_id IS NOT NULL
                 ORDER BY label.trace_code_id FOR SHARE
                """, Long.class, settlement.get("settlementId"));
        for (Long traceCodeId : receivingPackageTraces) {
            quotaPackageTraceFlowService.markSettled(traceCodeId, settlementNo);
        }
        support.writeAudit("settlement_bill", "confirm_settlement", number(settlement.get("settlementId")),
                settlementNo, "confirm settlement bill");
        return Map.of("settlementNo", settlementNo, "status", "confirmed");
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
