package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.supplychain.DepartmentRequisitionSmartRequests.AnalysisRequest;
import com.hospital.spd.supplychain.DepartmentRequisitionSmartRequests.GenerateItem;
import com.hospital.spd.supplychain.DepartmentRequisitionSmartRequests.GenerateRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates data-scoped replenishment analyses and atomically turns confirmed rows into requisitions. */
@Service
public class DepartmentRequisitionSmartService {
    private static final List<Integer> PERIODS = List.of(5, 7, 15, 30);

    private final JdbcTemplate jdbcTemplate;
    private final OperationalRequisitionModule requisitionModule;
    private final DepartmentRequisitionAccessService accessService;
    private final OperatorContextProvider operatorContextProvider;
    private final SupplyChainSupport support;

    public DepartmentRequisitionSmartService(JdbcTemplate jdbcTemplate,
                                             OperationalRequisitionModule requisitionModule,
                                             DepartmentRequisitionAccessService accessService,
                                             OperatorContextProvider operatorContextProvider,
                                             SupplyChainSupport support) {
        this.jdbcTemplate = jdbcTemplate;
        this.requisitionModule = requisitionModule;
        this.accessService = accessService;
        this.operatorContextProvider = operatorContextProvider;
        this.support = support;
    }

    @Transactional
    public Map<String, Object> analyze(AnalysisRequest request) {
        accessService.requirePermission("department-requisition:smart-analysis");
        Map<String, Object> dept = accessService.resolveDepartment(request.deptCode(), request.deptName());
        Long deptId = number(dept, "deptId");
        int days = request.selectedPeriodDays() == null || !PERIODS.contains(request.selectedPeriodDays())
                ? 7 : request.selectedPeriodDays();
        List<Long> targets = resolveTargets(deptId, request.destinationWarehouseId(), request.destinationWarehouseName());
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Long targetId : targets) {
            Long sourceId = resolveSourceWarehouse(targetId, request.sourceWarehouseId());
            suggestions.addAll(analyzeWarehouse(deptId, targetId, sourceId, days));
        }
        OperatorContext operator = operatorContextProvider.current();
        List<Long> resolvedSources = suggestions.stream().map(row -> number(row, "sourceWarehouseId"))
                .filter(Objects::nonNull).distinct().toList();
        Long resolvedSourceId = resolvedSources.size() == 1 ? resolvedSources.get(0) : null;
        Long analysisId = insertAnalysis(deptId, targets.size() == 1 ? targets.get(0) : null,
                resolvedSourceId, operator.userId(), days, suggestions);
        insertAnalysisItems(analysisId, suggestions);
        List<Long> itemIds = jdbcTemplate.queryForList(
                "SELECT item_id FROM replenishment_smart_analysis_item WHERE analysis_id = ? ORDER BY item_id",
                Long.class, analysisId);
        for (int index = 0; index < suggestions.size() && index < itemIds.size(); index++) {
            suggestions.get(index).put("analysisItemId", itemIds.get(index));
            suggestions.get(index).put("selected", true);
        }
        support.writeAudit("department_requisition_analysis", "analyze", analysisId, String.valueOf(analysisId),
                "科室智能补货分析，建议明细数：" + suggestions.size());
        return Map.of("analysisId", analysisId, "selectedPeriodDays", days, "periodDays", PERIODS,
                "rows", suggestions, "groupCount", targets.size());
    }

    @Transactional
    public Map<String, Object> generate(GenerateRequest request) {
        accessService.requirePermission("department-requisition:smart-analysis");
        OperatorContext operator = operatorContextProvider.current();
        List<Map<String, Object>> analyses = jdbcTemplate.queryForList("""
                SELECT analysis_id AS analysisId, created_by AS createdBy, status
                  FROM replenishment_smart_analysis WHERE analysis_id = ? FOR UPDATE
                """, request.analysisId());
        if (analyses.size() != 1) throw new IllegalArgumentException("智能补货分析不存在");
        Map<String, Object> analysis = analyses.get(0);
        if (!"draft".equals(String.valueOf(analysis.get("status")))) {
            throw new IllegalArgumentException("该分析已生成申领单，请勿重复提交");
        }
        if (!operator.canViewAllData() && !Objects.equals(operator.userId(), number(analysis, "createdBy"))) {
            throw new org.springframework.security.access.AccessDeniedException("只能确认本人创建的智能补货分析");
        }
        Map<Long, GenerateItem> submitted = new LinkedHashMap<>();
        for (GenerateItem item : request.items()) {
            if (submitted.put(item.analysisItemId(), item) != null) throw new IllegalArgumentException("分析明细重复");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT item_id AS analysisItemId, dept_id AS deptId, warehouse_id AS warehouseId,
                       source_warehouse_id AS sourceWarehouseId, product_code AS productCode,
                       item_mode AS itemMode, quota_template_id AS quotaTemplateId,
                       package_quantity AS packageQuantity
                  FROM replenishment_smart_analysis_item
                 WHERE analysis_id = ?
                 FOR UPDATE
                """, request.analysisId());
        if (rows.size() != submitted.size()
                || rows.stream().map(row -> number(row, "analysisItemId")).anyMatch(id -> !submitted.containsKey(id))) {
            throw new IllegalArgumentException("必须提交当前分析的全部明细，未选择的明细请标记 selected=false");
        }

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            GenerateItem selected = submitted.get(number(row, "analysisItemId"));
            if (selected.quantity().signum() < 0 || selected.selected() && selected.quantity().signum() <= 0) {
                throw new IllegalArgumentException("已选择建议的申领数量必须大于零，未选择建议不得为负数");
            }
            jdbcTemplate.update("""
                    UPDATE replenishment_smart_analysis_item
                       SET selected = ?, manual_adjusted_qty = ? WHERE item_id = ?
                    """, selected.selected() ? 1 : 0, selected.quantity(), selected.analysisItemId());
            if (!selected.selected()) continue;
            Long deptId = number(row, "deptId");
            Long warehouseId = number(row, "warehouseId");
            Long sourceId = number(row, "sourceWarehouseId");
            accessService.requireDepartment(deptId);
            accessService.requireDestinationWarehouse(deptId, warehouseId);
            String key = deptId + ":" + warehouseId + ":" + sourceId;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productCode", row.get("productCode"));
            item.put("requisitionMode", row.get("itemMode"));
            if ("quota_package".equals(row.get("itemMode"))) {
                BigDecimal packageQty = decimal(row.get("packageQuantity"));
                item.put("packageCount", selected.quantity());
                item.put("quantity", selected.quantity().multiply(packageQty));
                String templateCode = jdbcTemplate.queryForObject(
                        "SELECT template_code FROM quota_package_template WHERE template_id = ? AND is_current = 1 AND status = 1 AND deleted = 0",
                        String.class, row.get("quotaTemplateId"));
                item.put("templateCode", templateCode);
            } else {
                item.put("quantity", selected.quantity());
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        if (groups.isEmpty()) throw new IllegalArgumentException("请至少选择一条建议并填写大于零的申领数量");

        List<String> requisitionNos = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            String[] ids = entry.getKey().split(":");
            Long deptId = Long.valueOf(ids[0]);
            Map<String, Object> dept = jdbcTemplate.queryForMap(
                    "SELECT dept_code AS deptCode, dept_name AS deptName FROM sys_dept WHERE dept_id = ?", deptId);
            Map<String, Object> result = requisitionModule.createRequisition(Map.of(
                    "deptCode", dept.get("deptCode"), "deptName", dept.get("deptName"),
                    "destinationWarehouseId", Long.valueOf(ids[1]), "sourceWarehouseId", Long.valueOf(ids[2]),
                    "items", entry.getValue()));
            String no = String.valueOf(result.get("requisitionNo"));
            requisitionNos.add(no);
            Long requisitionId = jdbcTemplate.queryForObject(
                    "SELECT requisition_id FROM department_requisition WHERE requisition_no = ?", Long.class, no);
            jdbcTemplate.update("INSERT INTO replenishment_analysis_requisition (analysis_id, requisition_id) VALUES (?, ?)",
                    request.analysisId(), requisitionId);
        }
        jdbcTemplate.update("UPDATE replenishment_smart_analysis SET status = 'generated' WHERE analysis_id = ? AND status = 'draft'",
                request.analysisId());
        support.writeAudit("department_requisition_analysis", "generate", request.analysisId(),
                String.valueOf(request.analysisId()), "智能补货生成申领单：" + String.join(",", requisitionNos));
        return Map.of("createdCount", requisitionNos.size(), "requisitionNos", requisitionNos, "status", "generated");
    }

    private List<Long> resolveTargets(Long deptId, Long requestedId, String requestedName) {
        if (requestedId != null) {
            accessService.requireDestinationWarehouse(deptId, requestedId);
            return List.of(requestedId);
        }
        if (requestedName != null && !requestedName.isBlank()) {
            List<Long> ids = jdbcTemplate.queryForList("""
                    SELECT warehouse_id FROM warehouse
                     WHERE dept_id = ? AND warehouse_name = ? AND deleted = 0 AND status = 1
                    """, Long.class, deptId, requestedName.trim());
            if (ids.size() != 1) throw new IllegalArgumentException("目标库房不存在、已停用或名称不唯一");
            return ids;
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT warehouse_id FROM warehouse
                 WHERE dept_id = ? AND deleted = 0 AND status = 1 ORDER BY warehouse_id
                """, Long.class, deptId);
        if (ids.isEmpty()) throw new IllegalArgumentException("当前科室未关联启用库房");
        return ids;
    }

    private Long resolveSourceWarehouse(Long targetId, Long requestedId) {
        List<Long> ids;
        if (requestedId != null) {
            ids = jdbcTemplate.queryForList("""
                    SELECT source.warehouse_id FROM warehouse source JOIN warehouse target ON target.warehouse_id = ?
                     WHERE source.warehouse_id = ? AND source.deleted = 0 AND source.status = 1
                       AND (source.warehouse_type LIKE '%一级%' OR source.warehouse_type LIKE '%中心%')
                       AND COALESCE(source.campus_name, '') = COALESCE(target.campus_name, '')
                    """, Long.class, targetId, requestedId);
        } else {
            ids = jdbcTemplate.queryForList("""
                    SELECT source.warehouse_id FROM warehouse source JOIN warehouse target ON target.warehouse_id = ?
                     WHERE source.deleted = 0 AND source.status = 1
                       AND (source.warehouse_type LIKE '%一级%' OR source.warehouse_type LIKE '%中心%')
                       AND COALESCE(source.campus_name, '') = COALESCE(target.campus_name, '')
                     ORDER BY source.warehouse_id
                    """, Long.class, targetId);
        }
        if (ids.size() != 1) throw new IllegalArgumentException("无法唯一确定来源中心库，请明确选择来源库房");
        return ids.get(0);
    }

    private List<Map<String, Object>> analyzeWarehouse(Long deptId, Long warehouseId, Long sourceId, int days) {
        String interval = String.valueOf(days);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.product_id AS productId, p.product_code AS productCode, p.product_name AS productName,
                       p.unit AS baseUnit, p.is_high_value AS highValue, p.is_quota_managed AS quotaManaged,
                       w.warehouse_name AS warehouseName, sd.dept_name AS deptName,
                       COALESCE((SELECT SUM(-ie.qty_change) FROM inventory_event ie
                                  WHERE ie.warehouse_id = dwc.warehouse_id AND ie.product_id = dwc.product_id
                                    AND ie.qty_change < 0
                                    AND ie.event_type IN ('department_consumption_out', 'quota_package_scan_out', 'high_value_billing_deduct')
                                    AND ie.event_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL %s DAY)), 0) AS periodDemand,
                       COALESCE((SELECT SUM(ib.available_qty) FROM inventory_balance ib
                                  WHERE ib.warehouse_id = dwc.warehouse_id AND ib.product_id = dwc.product_id), 0) AS currentQty,
                       CASE WHEN p.is_high_value = 1 THEN
                         COALESCE((SELECT COUNT(*)
                                     FROM udi_trace_code utc
                                     JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = utc.trace_code_id
                                     JOIN inventory_batch ib ON ib.batch_id = ibtc.batch_id
                                    WHERE ib.product_id = dwc.product_id AND ibtc.current_warehouse_id = ?
                                      AND utc.current_status = 'in_stock' AND ibtc.lifecycle_status = 'in_stock'), 0)
                       ELSE COALESCE((SELECT SUM(ib.available_qty) FROM inventory_balance ib
                                      WHERE ib.warehouse_id = ? AND ib.product_id = dwc.product_id), 0)
                       END AS sourceAvailableQty,
                       qpt.template_id AS quotaTemplateId, qpt.version_no AS quotaTemplateVersion,
                       qpti.quantity AS packageQuantity, qpti.unit AS packageUnit
                  FROM department_warehouse_catalog dwc
                  JOIN product p ON p.product_id = dwc.product_id AND p.deleted = 0 AND p.status = 1
                  JOIN warehouse w ON w.warehouse_id = dwc.warehouse_id AND w.deleted = 0 AND w.status = 1
                  JOIN sys_dept sd ON sd.dept_id = dwc.dept_id AND sd.deleted = 0 AND sd.status = 1
                  LEFT JOIN quota_safety_stock qss ON qss.dept_id = dwc.dept_id AND qss.product_id = dwc.product_id
                  LEFT JOIN quota_package_template qpt ON qpt.template_id = qss.template_id
                       AND qpt.is_current = 1 AND qpt.status = 1 AND qpt.deleted = 0
                  LEFT JOIN quota_package_template_item qpti ON qpti.template_id = qpt.template_id
                       AND qpti.product_id = p.product_id AND qpti.deleted = 0
                 WHERE dwc.dept_id = ? AND dwc.warehouse_id = ? AND dwc.status = 1 AND dwc.deleted = 0
                 ORDER BY p.product_name, p.product_id
                """.formatted(interval), sourceId, sourceId, deptId, warehouseId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            BigDecimal demand = decimal(row.get("periodDemand"));
            BigDecimal current = decimal(row.get("currentQty"));
            BigDecimal shortage = demand.subtract(current).max(BigDecimal.ZERO);
            String mode = numberValue(row.get("highValue")) == 1 ? "high_value"
                    : numberValue(row.get("quotaManaged")) == 1 && row.get("quotaTemplateId") != null ? "quota_package" : "loose";
            BigDecimal recommendation = shortage;
            if ("quota_package".equals(mode)) {
                recommendation = shortage.divide(decimal(row.get("packageQuantity")), 0, RoundingMode.CEILING);
            } else if ("high_value".equals(mode)) {
                recommendation = shortage.setScale(0, RoundingMode.CEILING);
            }
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("deptId", deptId);
            item.put("warehouseId", warehouseId);
            item.put("sourceWarehouseId", sourceId);
            item.put("itemMode", mode);
            item.put("recommendedQty", recommendation);
            item.put("shortageQty", shortage);
            item.put("formulaText", "近" + days + "天实际消耗 - 目标库可用库存");
            result.add(item);
        }
        return result;
    }

    private Long insertAnalysis(Long deptId, Long warehouseId, Long sourceId, Long userId, int days,
                                List<Map<String, Object>> rows) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO replenishment_smart_analysis
                      (dept_id, warehouse_id, source_warehouse_id, created_by, status, dept_name, warehouse_name,
                       selected_period_days, item_count, total_recommended_qty)
                    VALUES (?, ?, ?, ?, 'draft', ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, deptId); ps.setObject(2, warehouseId); ps.setObject(3, sourceId); ps.setObject(4, userId);
            ps.setString(5, rows.isEmpty() ? "-" : String.valueOf(rows.get(0).get("deptName")));
            ps.setString(6, warehouseId == null ? "多库房" : rows.isEmpty() ? "-" : String.valueOf(rows.get(0).get("warehouseName")));
            ps.setInt(7, days); ps.setInt(8, rows.size());
            ps.setBigDecimal(9, rows.stream().map(row -> decimal(row.get("recommendedQty"))).reduce(BigDecimal.ZERO, BigDecimal::add));
            return ps;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }

    private void insertAnalysisItems(Long analysisId, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            jdbcTemplate.update("""
                    INSERT INTO replenishment_smart_analysis_item
                      (analysis_id, product_id, dept_id, warehouse_id, source_warehouse_id, item_mode,
                       quota_template_id, quota_template_version, package_quantity, product_code, product_name,
                       current_qty, source_available_qty, recommended_qty, formula_text)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, analysisId, row.get("productId"), row.get("deptId"), row.get("warehouseId"),
                    row.get("sourceWarehouseId"), row.get("itemMode"), row.get("quotaTemplateId"),
                    row.get("quotaTemplateVersion"), row.get("packageQuantity"), row.get("productCode"),
                    row.get("productName"), row.get("currentQty"), row.get("sourceAvailableQty"),
                    row.get("recommendedQty"), row.get("formulaText"));
        }
    }

    private static Long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private static int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : value instanceof BigDecimal decimal
                ? decimal : new BigDecimal(String.valueOf(value));
    }
}
