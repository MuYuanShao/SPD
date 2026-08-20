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
        Map<String, Object> settlement = jdbcTemplate.queryForMap("""
                SELECT settlement_id AS settlementId, status
                  FROM settlement_bill
                 WHERE settlement_no = ?
                 FOR UPDATE
                """, settlementNo);
        if (!"pending_confirm".equals(String.valueOf(settlement.get("status")))) {
            throw new IllegalArgumentException("only pending settlement can be confirmed");
        }
        approvalFlowGuard.requireApprovalAccess("settlement-reconciliation", "settlement-confirm", null, null);
        int updated = jdbcTemplate.update("""
                UPDATE settlement_bill
                   SET status = 'confirmed', confirm_time = NOW()
                 WHERE settlement_no = ? AND status = 'pending_confirm'
                """, settlementNo);
        if (updated != 1) throw new IllegalArgumentException("settlement has already been processed");
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
        support.writeAudit("settlement_bill", "confirm_settlement", number(settlement.get("settlementId")),
                settlementNo, "confirm settlement bill");
        return Map.of("settlementNo", settlementNo, "status", "confirmed");
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
