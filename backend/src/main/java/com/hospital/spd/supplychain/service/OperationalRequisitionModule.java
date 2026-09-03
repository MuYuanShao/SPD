package com.hospital.spd.supplychain.service;
import com.hospital.spd.specialty.service.HighValueTraceFlowService;

import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.supplychain.RequisitionItemMode;
import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hospital.spd.common.service.DocumentKind.DEPARTMENT_REQUISITION;

/**
 * Owns department requisition creation inside Operational Closure.
 */
@Service
public class OperationalRequisitionModule {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final ApprovalFlowGuard approvalFlowGuard;
    private final OperatorContextProvider operatorContextProvider;
    private final HighValueTraceFlowService traceFlowService;
    private final DepartmentRequisitionAccessService accessService;

    public OperationalRequisitionModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, new ApprovalFlowGuard(jdbcTemplate), OperatorContext::system,
                new HighValueTraceFlowService(jdbcTemplate, support, OperatorContext::system));
    }

    public OperationalRequisitionModule(JdbcTemplate jdbcTemplate,
                                         SupplyChainSupport support,
                                         ApprovalFlowGuard approvalFlowGuard,
                                         OperatorContextProvider operatorContextProvider,
                                         HighValueTraceFlowService traceFlowService) {
        this(jdbcTemplate, support, approvalFlowGuard, operatorContextProvider, traceFlowService,
                new DepartmentRequisitionAccessService(jdbcTemplate, operatorContextProvider,
                        new DataScopeService(operatorContextProvider)));
    }

    @Autowired
    public OperationalRequisitionModule(JdbcTemplate jdbcTemplate,
                                         SupplyChainSupport support,
                                         ApprovalFlowGuard approvalFlowGuard,
                                         OperatorContextProvider operatorContextProvider,
                                         HighValueTraceFlowService traceFlowService,
                                         DepartmentRequisitionAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.approvalFlowGuard = approvalFlowGuard;
        this.operatorContextProvider = operatorContextProvider;
        this.traceFlowService = traceFlowService;
        this.accessService = accessService;
    }

    @Transactional
    public Map<String, Object> createRequisition(Map<String, Object> body) {
        accessService.requirePermission("department-requisition:create");
        Map<String, Object> department = accessService.resolveDepartment(text(body.get("deptCode")), text(body.get("deptName")));
        String deptName = String.valueOf(department.get("deptName"));
        Long deptId = ((Number) department.get("deptId")).longValue();
        Long warehouseId = resolveDestinationWarehouse(body);
        Long sourceWarehouseId = resolveSourceWarehouse(body, warehouseId);
        accessService.requireDestinationWarehouse(deptId, warehouseId);
        List<PreparedRequisitionItem> items = new ArrayList<>();
        for (Map<String, Object> itemBody : requisitionItems(body)) {
            String productCode = requireText(itemBody, "productCode");
            Map<String, Object> product = findProduct(productCode);
            requireDepartmentWarehouseCatalog(deptId, warehouseId,
                    ((Number) product.get("productId")).longValue());
            BigDecimal unitPrice = (BigDecimal) product.get("purchasePrice");
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("product purchase price is required");
            }
            RequisitionItemMode mode = resolveItemMode(itemBody, product);
            TemplateSnapshot snapshot = mode == RequisitionItemMode.QUOTA_PACKAGE
                    ? resolveTemplateSnapshot(itemBody, product, deptId) : null;
            BigDecimal packageCount = optionalPositive(itemBody.get("packageCount"), "packageCount");
            BigDecimal quantity = requirePositive(itemBody, "quantity");
            if (mode == RequisitionItemMode.QUOTA_PACKAGE && packageCount != null) {
                quantity = packageCount.multiply(snapshot.packageQuantity());
            }
            validateModePayload(itemBody, product, mode, snapshot, quantity);
            items.add(new PreparedRequisitionItem(itemBody, product, quantity, packageCount, unitPrice,
                    quantity.multiply(unitPrice), mode.code(), snapshot));
        }
        OperatorContext operator = operatorContextProvider.current();
        String requisitionNo = support.nextNo(DEPARTMENT_REQUISITION);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO department_requisition (
                      requisition_no, dept_id, warehouse_id, source_warehouse_id,
                      requisition_type, status, applicant_id
                    ) VALUES (?, ?, ?, ?, 'regular_requisition', 'pending_approval', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, requisitionNo);
            ps.setLong(2, deptId);
            if (warehouseId == null) {
                ps.setObject(3, null);
            } else {
                ps.setLong(3, warehouseId);
            }
            ps.setLong(4, sourceWarehouseId);
            ps.setLong(5, operator.userId());
            return ps;
        }, keyHolder);
        Long requisitionId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        for (PreparedRequisitionItem item : items) {
            jdbcTemplate.update("""
                    INSERT INTO department_requisition_item (
                      requisition_id, product_id, quantity, item_type,
                      quota_template_id, quota_template_version, quota_package_quantity, quota_package_unit,
                      requested_package_count, unit, unit_price, amount, remark
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, requisitionId, item.product().get("productId"), item.quantity(), item.itemType(),
                    item.snapshot() == null ? null : item.snapshot().templateId(),
                    item.snapshot() == null ? null : item.snapshot().versionNo(),
                    item.snapshot() == null ? null : item.snapshot().packageQuantity(),
                    item.snapshot() == null ? null : item.snapshot().packageUnit(),
                    item.packageCount(),
                    item.product().get("unit"), item.unitPrice(), item.amount(), "department requisition");
        }
        support.writeAudit("department_requisition", "create", requisitionId, requisitionNo,
                "创建科室申领，明细数：" + items.size());
        return Map.of("requisitionNo", requisitionNo, "status", "pending_approval", "itemCount", items.size());
    }

    private static List<Map<String, Object>> requisitionItems(Map<String, Object> body) {
        Object rawItems = body.get("items");
        if (rawItems == null) {
            return List.of(body);
        }
        if (!(rawItems instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one requisition item");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object rawItem : list) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException("each requisition item must be an object");
            }
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            rawMap.forEach((key, value) -> item.put(String.valueOf(key), value));
            items.add(item);
        }
        return items;
    }

    private record PreparedRequisitionItem(Map<String, Object> body, Map<String, Object> product,
                                           BigDecimal quantity, BigDecimal packageCount, BigDecimal unitPrice,
                                           BigDecimal amount, String itemType, TemplateSnapshot snapshot) {
    }

    private record TemplateSnapshot(Long templateId, Integer versionNo,
                                    BigDecimal packageQuantity, String packageUnit) {}

    @Transactional
    public Map<String, Object> action(String requisitionNo, Map<String, Object> body) {
        accessService.requirePermission("department-requisition:approve");
        Map<String, Object> requisition = jdbcTemplate.queryForMap("""
                SELECT requisition_id AS requisitionId, dept_id AS deptId, status
                  FROM department_requisition
                 WHERE requisition_no = ?
                 FOR UPDATE
                """, requisitionNo);
        if (!"pending_approval".equals(String.valueOf(requisition.get("status")))) {
            throw new IllegalArgumentException("only pending requisition can be processed");
        }
        accessService.requireDepartment(((Number) requisition.get("deptId")).longValue());
        String action = requireText(body, "action");
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new IllegalArgumentException("requisition action is invalid");
        }
        approvalFlowGuard.requireApprovalAccess("department-requisition", "requisition-approval",
                ((Number) requisition.get("deptId")).longValue(), null);
        String nextStatus = "approve".equals(action) ? "approved" : "rejected";
        OperatorContext operator = operatorContextProvider.current();
        int updated = jdbcTemplate.update("""
                UPDATE department_requisition
                   SET status = ?, approve_by = ?, approve_time = NOW()
                 WHERE requisition_no = ? AND status = 'pending_approval'
                """, nextStatus, operator.userId(), requisitionNo);
        if (updated != 1) throw new IllegalArgumentException("requisition has already been processed");
        if ("reject".equals(action)) {
            traceFlowService.releaseRejectedRequisition(
                    ((Number) requisition.get("requisitionId")).longValue(), requisitionNo);
        }
        return Map.of("requisitionNo", requisitionNo, "status", nextStatus);
    }

    private static RequisitionItemMode resolveItemMode(Map<String, Object> body, Map<String, Object> product) {
        RequisitionItemMode requested = RequisitionItemMode.parse(body.get("requisitionMode"));
        if (requested != null) return requested;
        if (number(product.get("highValue")) == 1) return RequisitionItemMode.HIGH_VALUE;
        return hasText(body.get("templateCode")) ? RequisitionItemMode.QUOTA_PACKAGE : RequisitionItemMode.LOOSE;
    }

    private static void validateModePayload(Map<String, Object> body, Map<String, Object> product,
                                            RequisitionItemMode mode, TemplateSnapshot snapshot,
                                            BigDecimal quantity) {
        boolean highValue = number(product.get("highValue")) == 1;
        boolean hasTemplate = hasText(body.get("templateCode"));
        boolean hasCodes = hasText(body.get("uniqueCode")) || body.get("uniqueCodes") != null;
        if (hasCodes) throw new IllegalArgumentException("高值耗材唯一码请在拣配阶段选择");
        if (mode == RequisitionItemMode.HIGH_VALUE) {
            if (!highValue) throw new IllegalArgumentException("普通耗材不能使用高值申领模式");
            if (hasTemplate) throw new IllegalArgumentException("高值申领不得携带定数包模板");
            try { quantity.intValueExact(); }
            catch (ArithmeticException ex) { throw new IllegalArgumentException("高值耗材申领数量必须为整数"); }
        } else if (highValue) {
            throw new IllegalArgumentException("高值耗材必须使用高值申领模式");
        } else if (mode == RequisitionItemMode.LOOSE && hasTemplate) {
            throw new IllegalArgumentException("散货申领不得携带定数包模板");
        } else if (mode == RequisitionItemMode.QUOTA_PACKAGE && snapshot == null) {
            throw new IllegalArgumentException("定数包申领必须选择有效模板");
        }
    }

    private TemplateSnapshot resolveTemplateSnapshot(Map<String, Object> body, Map<String, Object> product, Long deptId) {
        Object rawTemplateCode = body.get("templateCode");
        if (rawTemplateCode == null || String.valueOf(rawTemplateCode).isBlank()
                || "-".equals(String.valueOf(rawTemplateCode).trim())) {
            throw new IllegalArgumentException("定数包申领必须选择有效模板");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT qpt.template_id AS templateId, qpt.version_no AS versionNo,
                       qpti.quantity AS packageQuantity, qpti.unit AS packageUnit
                  FROM quota_package_template qpt
                  JOIN quota_package_template_item qpti ON qpti.template_id = qpt.template_id AND qpti.deleted = 0
                 WHERE qpt.template_code = ? AND qpt.is_current = 1
                   AND qpt.status = 1 AND qpt.deleted = 0
                   AND qpti.product_id = ?
                   AND (qpt.dept_id IS NULL OR qpt.dept_id = ?)
                 ORDER BY qpt.dept_id IS NULL
                """, String.valueOf(rawTemplateCode).trim(), product.get("productId"), deptId);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("所选定数包模板不存在、已停用或与商品不匹配");
        }
        Map<String, Object> row = rows.get(0);
        return new TemplateSnapshot(((Number) row.get("templateId")).longValue(),
                ((Number) row.get("versionNo")).intValue(), (BigDecimal) row.get("packageQuantity"),
                String.valueOf(row.get("packageUnit")));
    }

    private Map<String, Object> findProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_price AS purchasePrice, is_high_value AS highValue
                       , is_quota_managed AS quotaManaged
                  FROM product WHERE product_code = ? AND deleted = 0 AND status = 1
                """, productCode);
    }

    private Long findWarehouseId(String warehouseName) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT warehouse_id FROM warehouse
                 WHERE warehouse_name = ? AND deleted = 0 AND status = 1 LIMIT 1
                """, Long.class, warehouseName.trim());
        if (ids.isEmpty()) throw new IllegalArgumentException("warehouse does not exist or is disabled");
        return ids.get(0);
    }

    private Long resolveDestinationWarehouse(Map<String, Object> body) {
        Long explicitId = optionalLong(body.get("destinationWarehouseId"), "destinationWarehouseId");
        if (explicitId == null) {
            return findWarehouseId(requireText(body, "warehouseName"));
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT warehouse_id FROM warehouse
                 WHERE warehouse_id = ? AND deleted = 0 AND status = 1
                """, Long.class, explicitId);
        if (ids.size() != 1) throw new IllegalArgumentException("destination warehouse does not exist or is disabled");
        return ids.get(0);
    }

    private Long resolveSourceWarehouse(Map<String, Object> body, Long destinationWarehouseId) {
        Long explicitId = optionalLong(body.get("sourceWarehouseId"), "sourceWarehouseId");
        if (explicitId != null) {
            List<Long> ids = jdbcTemplate.queryForList("""
                    SELECT warehouse_id FROM warehouse
                     WHERE warehouse_id = ? AND deleted = 0 AND status = 1
                       AND (warehouse_type LIKE '%一级%' OR warehouse_type LIKE '%中心%')
                    """, Long.class, explicitId);
            if (ids.size() != 1) throw new IllegalArgumentException("source warehouse must be an enabled central warehouse");
            return ids.get(0);
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT source.warehouse_id
                  FROM warehouse source
                  JOIN warehouse destination ON destination.warehouse_id = ?
                 WHERE source.deleted = 0 AND source.status = 1
                   AND destination.deleted = 0
                   AND (source.warehouse_type LIKE '%一级%' OR source.warehouse_type LIKE '%中心%')
                   AND COALESCE(source.campus_name, '') = COALESCE(destination.campus_name, '')
                 ORDER BY source.warehouse_id
                """, Long.class, destinationWarehouseId);
        if (ids.size() != 1) {
            throw new IllegalArgumentException("cannot uniquely resolve source central warehouse; sourceWarehouseId is required");
        }
        return ids.get(0);
    }

    private static Long optionalLong(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    private void requireDepartmentWarehouseCatalog(Long deptId, Long warehouseId, Long productId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM department_warehouse_catalog
                 WHERE dept_id = ?
                   AND warehouse_id = ?
                   AND product_id = ?
                   AND status = 1
                   AND deleted = 0
                """, Long.class, deptId, warehouseId, productId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("所选商品不在当前科室库房目录中");
        }
    }

    private static String requireText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(key + " is required");
        return String.valueOf(value).trim();
    }

    private static BigDecimal requirePositive(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(key + " is required");
        BigDecimal result = new BigDecimal(String.valueOf(value));
        if (result.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(key + " must be greater than zero");
        return result;
    }

    private static BigDecimal optionalPositive(Object value, String key) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        BigDecimal result = new BigDecimal(String.valueOf(value));
        if (result.signum() <= 0) throw new IllegalArgumentException(key + " must be greater than zero");
        return result;
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean hasText(Object value) {
        if (value instanceof Iterable<?> iterable) return iterable.iterator().hasNext();
        return value != null && !String.valueOf(value).isBlank();
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }
}
