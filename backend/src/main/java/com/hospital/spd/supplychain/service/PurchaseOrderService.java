package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.supplychain.PurchaseOrderActionRequest;
import com.hospital.spd.supplychain.PurchaseOrderItemRequest;
import com.hospital.spd.supplychain.PurchaseOrderRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import static com.hospital.spd.common.service.DocumentKind.*;
import static com.hospital.spd.common.SqlHelper.isBlank;
import static com.hospital.spd.common.SqlHelper.nullIfBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinates purchase demands, plans, orders, and order status transitions before receiving.
 */
@Service
public class PurchaseOrderService {

    private static final List<Integer> SMART_REPLENISHMENT_PERIOD_DAYS = List.of(5, 15, 30, 45, 60);

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final PurchaseFlowCommandRunner purchaseFlow;
    private final DataScopeService dataScopeService;
    private final OperatorContextProvider operatorContextProvider;
    private final PurchasePermissionGuard permissionGuard;
    private final AuditLogService auditLogService;

    public PurchaseOrderService(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, OperatorContext::system, new DataScopeService(OperatorContext::system));
    }

    public PurchaseOrderService(JdbcTemplate jdbcTemplate,
                                SupplyChainSupport support,
                                OperatorContextProvider operatorContextProvider) {
        this(jdbcTemplate, support, operatorContextProvider, new DataScopeService(operatorContextProvider));
    }

    @Autowired
    public PurchaseOrderService(JdbcTemplate jdbcTemplate,
                                SupplyChainSupport support,
                                OperatorContextProvider operatorContextProvider,
                                DataScopeService dataScopeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.dataScopeService = dataScopeService;
        this.operatorContextProvider = operatorContextProvider;
        this.permissionGuard = new PurchasePermissionGuard(jdbcTemplate, operatorContextProvider);
        this.auditLogService = new AuditLogService(jdbcTemplate, operatorContextProvider);
        this.purchaseFlow = new PurchaseFlowCommandRunner(jdbcTemplate, operatorContextProvider, this::createOrderFromPlan);
    }

    // ==================== 采购订单 ====================

    public Map<String, Object> listOrders(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1
                """);
        appendLike(where, args, "po.order_no", params.get("orderNo"));
        appendLike(where, args, "s.supplier_name", params.get("supplierName"));
        if (!isBlank(params.get("status"))) {
            where.append(" AND po.order_status = ?");
            args.add(params.get("status").trim());
        }
        if (!isBlank(params.get("keyword"))) {
            where.append("""
                     AND EXISTS (
                       SELECT 1
                         FROM purchase_order_item ki
                         JOIN product kp ON kp.product_id = ki.product_id
                        WHERE ki.purchase_order_id = po.purchase_order_id
                          AND (kp.product_code LIKE ? OR kp.product_name LIKE ? OR kp.spec_model LIKE ?)
                     )
                    """);
            String keyword = "%" + params.get("keyword").trim() + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }

        String fromClause = """
                  FROM purchase_order po
                  JOIN supplier s ON s.supplier_id = po.supplier_id
                  LEFT JOIN purchase_order_item poi ON poi.purchase_order_id = po.purchase_order_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT po.purchase_order_id) " + fromClause + where,
                Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT po.purchase_order_id AS orderId, po.order_no AS orderNo,
                       s.supplier_name AS supplierName, po.order_source AS orderSource,
                       po.order_status AS orderStatus, po.total_amount AS totalAmount,
                       po.purchase_type AS purchaseType, po.close_reason AS closeReason,
                       DATE_FORMAT(po.expected_arrival_date, '%Y-%m-%d') AS expectedArrivalDate,
                       DATE_FORMAT(po.create_time, '%Y-%m-%d %H:%i') AS createTime,
                       DATE_FORMAT(po.send_time, '%Y-%m-%d %H:%i') AS sendTime,
                       DATE_FORMAT(po.close_time, '%Y-%m-%d %H:%i') AS closeTime,
                       COUNT(poi.item_id) AS itemCount,
                       COALESCE(SUM(poi.quantity), 0) AS orderQuantity,
                       COALESCE(SUM(poi.received_quantity), 0) AS receivedQuantity,
                       GREATEST(COALESCE(SUM(poi.quantity), 0) - COALESCE(SUM(poi.received_quantity), 0), 0) AS remainingQuantity
                  FROM purchase_order po
                  JOIN supplier s ON s.supplier_id = po.supplier_id
                  LEFT JOIN purchase_order_item poi ON poi.purchase_order_id = po.purchase_order_id
                """ + where + " GROUP BY po.purchase_order_id ORDER BY po.create_time DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());

        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS totalOrders,
                       COALESCE(SUM(total_amount), 0) AS totalAmount,
                       SUM(CASE WHEN order_status = 'pending_approval' THEN 1 ELSE 0 END) AS pendingApproval,
                       SUM(CASE WHEN order_status = 'sent' THEN 1 ELSE 0 END) AS sentOrders,
                       SUM(CASE WHEN order_status = 'closed' THEN 1 ELSE 0 END) AS closedOrders
                  FROM purchase_order
                """);
        return PageResponse.of(rows, total == null ? 0 : total, pageReq, summary);
    }

    public Map<String, Object> getDetail(String orderNo) {
        Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT po.purchase_order_id AS orderId, po.order_no AS orderNo,
                       s.supplier_name AS supplierName, po.order_source AS orderSource,
                       po.order_status AS orderStatus, po.total_amount AS totalAmount,
                       po.purchase_type AS purchaseType, po.close_reason AS closeReason,
                       DATE_FORMAT(po.expected_arrival_date, '%Y-%m-%d') AS expectedArrivalDate,
                       DATE_FORMAT(po.create_time, '%Y-%m-%d %H:%i') AS createTime,
                       DATE_FORMAT(po.approve_time, '%Y-%m-%d %H:%i') AS approveTime,
                       DATE_FORMAT(po.send_time, '%Y-%m-%d %H:%i') AS sendTime,
                       DATE_FORMAT(po.close_time, '%Y-%m-%d %H:%i') AS closeTime,
                       COALESCE(SUM(poi.quantity), 0) AS orderQuantity,
                       COALESCE(SUM(poi.received_quantity), 0) AS receivedQuantity,
                       GREATEST(COALESCE(SUM(poi.quantity), 0) - COALESCE(SUM(poi.received_quantity), 0), 0) AS remainingQuantity
                  FROM purchase_order po
                  JOIN supplier s ON s.supplier_id = po.supplier_id
                  LEFT JOIN purchase_order_item poi ON poi.purchase_order_id = po.purchase_order_id
                 WHERE po.order_no = ?
                 GROUP BY po.purchase_order_id
                """, orderNo);
        Long orderId = ((Number) order.get("orderId")).longValue();
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT poi.item_id AS itemId, s.supplier_name AS supplierName,
                       p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, p.registration_no AS registrationNo,
                       m.manufacturer_name AS manufacturerName, poi.unit,
                       poi.estimated_unit_price AS estimatedUnitPrice,
                       poi.quantity, poi.amount,
                       p.conversion_rate AS middlePackageQuantity,
                       p.purchase_package_qty AS purchasePackageQuantity,
                       p.tender_sub_code AS tenderSubCode, p.contract_code AS contractCode,
                       p.udi_code AS udiCode, poi.received_quantity AS receivedQuantity,
                       p.purchase_price AS latestCatalogPrice,
                       CASE WHEN p.purchase_price <> poi.estimated_unit_price THEN 1 ELSE 0 END AS priceDiff
                  FROM purchase_order_item poi
                  JOIN purchase_order po ON po.purchase_order_id = poi.purchase_order_id
                  JOIN supplier s ON s.supplier_id = po.supplier_id
                  JOIN product p ON p.product_id = poi.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                 WHERE poi.purchase_order_id = ?
                 ORDER BY poi.item_id
                """, orderId);
        List<Map<String, Object>> tracking = trackingRows(orderId);
        return Map.of("order", order, "items", items, "tracking", tracking);
    }

    @Transactional
    public Map<String, Object> createOrder(PurchaseOrderRequest request) {
        permissionGuard.require("purchase-order:create");
        validateRequest(request);
        String normalizedOrderSource = normalizeOrderSource(request.orderSource());
        String normalizedPurchaseType = normalizePurchaseType(request.purchaseType(), request.orderSource());
        Long supplierId = findSupplierId(request.supplierName());
        String orderNo = support.nextNo(PURCHASE_ORDER);
        BigDecimal totalAmount = request.items().stream()
                .map(item -> defaultDecimal(item.quantity(), BigDecimal.ZERO).multiply(defaultDecimal(item.estimatedUnitPrice(), BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO purchase_order (
                      order_no, supplier_id, order_source, order_status, total_amount,
                      expected_arrival_date, create_by, purchase_type
                    ) VALUES (?, ?, ?, 'draft', ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, orderNo);
            ps.setLong(2, supplierId);
            ps.setString(3, normalizedOrderSource);
            ps.setBigDecimal(4, totalAmount);
            ps.setDate(5, parseDate(request.expectedArrivalDate()));
            ps.setLong(6, operatorContextProvider.current().userId());
            ps.setString(7, normalizedPurchaseType);
            return ps;
        }, keyHolder);
        Long orderId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        insertItems(orderId, request.items());
        appendTracking(orderId, "created", "订单已创建", "采购订单保存为草稿");
        writeAudit("create", orderId, orderNo, "create purchase order");
        return Map.of("orderNo", orderNo, "totalAmount", totalAmount);
    }

    @Transactional
    public Map<String, Object> performAction(String orderNo, PurchaseOrderActionRequest request) {
        permissionGuard.require(orderActionPermission(request.action()));
        if (List.of("submit", "approve", "send").contains(String.valueOf(request.action()).trim().toLowerCase(java.util.Locale.ROOT))) {
            new com.hospital.spd.licenses.LicenseEligibilityService(jdbcTemplate).requirePurchaseOrder(orderNo);
        }
        return purchaseFlow.runOrderAction(orderNo, request);
    }

    @Transactional
    public Map<String, Object> addRemark(String orderNo, String remark) {
        permissionGuard.require("purchase-order:remark");
        if (isBlank(remark)) {
            throw new IllegalArgumentException("请填写订单备注");
        }
        Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT purchase_order_id AS orderId, order_status AS orderStatus
                  FROM purchase_order
                 WHERE order_no = ?
                """, orderNo);
        Long orderId = ((Number) order.get("orderId")).longValue();
        jdbcTemplate.update("""
                INSERT INTO purchase_order_tracking (purchase_order_id, event_type, event_status, remark)
                VALUES (?, 'remark', ?, ?)
                """, orderId, order.get("orderStatus"), remark.trim());
        writeAudit("remark", orderId, orderNo, remark.trim());
        return Map.of("orderNo", orderNo, "remark", remark.trim());
    }

    // ==================== 采购需求 ====================

    public Map<String, Object> listDemands(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1
                """);
        appendLike(where, args, "pd.demand_no", params.get("demandNo"));
        appendLike(where, args, "p.product_name", params.get("keyword"));
        if (!isBlank(params.get("status"))) {
            where.append(" AND pd.demand_status = ?");
            args.add(params.get("status").trim());
        }
        if (!isBlank(params.get("deptCode"))) {
            where.append(" AND d.dept_code = ?");
            args.add(params.get("deptCode").trim());
        }
        dataScopeService.appendScope(where, args, "pd.dept_id", "pd.create_by");

        String fromClause = """
                  FROM purchase_demand pd
                  JOIN product p ON p.product_id = pd.product_id
                  LEFT JOIN sys_dept d ON d.dept_id = pd.dept_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT pd.demand_no) " + fromClause + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageReq.size());
        pageArgs.add(pageReq.offset());
        List<Map<String, Object>> demandHeaders = jdbcTemplate.queryForList("""
                SELECT pd.demand_no AS demandNo, MAX(pd.create_time) AS sortTime
                """ + fromClause + where + " GROUP BY pd.demand_no ORDER BY sortTime DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        if (demandHeaders.isEmpty()) {
            return PageResponse.of(List.of(), total == null ? 0 : total, pageReq);
        }
        List<String> demandNos = demandHeaders.stream()
                .map(row -> String.valueOf(row.get("demandNo")))
                .toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(demandNos.size(), "?"));
        List<Object> detailArgs = new ArrayList<>(demandNos);
        detailArgs.addAll(demandNos);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT pd.demand_id AS demandId, pd.demand_no AS demandNo, pd.demand_source AS demandSource,
                       pd.demand_status AS demandStatus, pd.urgent_level AS urgentLevel,
                       d.dept_name AS deptName, p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, pd.quantity, pd.approved_quantity AS approvedQuantity,
                       pd.suggested_purchase_qty AS suggestedPurchaseQty,
                       p.purchase_price AS unitPrice, p.registration_no AS registrationNo,
                       m.manufacturer_name AS manufacturerName,
                       DATE_FORMAT(pd.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM purchase_demand pd
                  JOIN product p ON p.product_id = pd.product_id
                  LEFT JOIN sys_dept d ON d.dept_id = pd.dept_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                """ + " WHERE pd.demand_no IN (" + placeholders + ")"
                + " ORDER BY FIELD(pd.demand_no," + placeholders + "), pd.demand_id",
                detailArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> createDemand(Map<String, Object> request) {
        permissionGuard.require("purchase-demand:create");
        // items 数组：每个元素包含 productCode + quantity
        List<Map<String, Object>> items;
        Object raw = request.get("items");
        if (raw instanceof List) {
            items = (List<Map<String, Object>>) raw;
        } else {
            // 兼容单条旧格式
            items = List.of(Map.of(
                "productCode", stringValue(request.get("productCode")),
                "quantity", request.get("quantity")
            ));
        }

        Long analysisId = nullableLong(request.get("analysisId"));
        String analysisNo = null;
        BigDecimal adjustedTotal = null;
        if (analysisId != null) {
            Map<String, Object> analysis = jdbcTemplate.queryForMap("""
                    SELECT analysis_no AS analysisNo, analysis_status AS analysisStatus
                      FROM purchase_replenishment_analysis
                     WHERE analysis_id = ?
                     FOR UPDATE
                    """, analysisId);
            analysisNo = stringValue(analysis.get("analysisNo"));
            if (!"analyzed".equals(stringValue(analysis.get("analysisStatus")))) {
                throw new IllegalArgumentException("smart replenishment analysis has already generated a demand");
            }
            Object rawAdjustments = request.get("analysisAdjustments");
            if (!(rawAdjustments instanceof List<?> adjustments) || adjustments.isEmpty()) {
                throw new IllegalArgumentException("analysis adjustments are required");
            }
            adjustedTotal = BigDecimal.ZERO;
            for (Object rawAdjustment : adjustments) {
                if (!(rawAdjustment instanceof Map<?, ?> adjustment)) {
                    throw new IllegalArgumentException("analysis adjustment is invalid");
                }
                String warehouseCode = stringValue(adjustment.get("warehouseCode"));
                String productCode = stringValue(adjustment.get("productCode"));
                BigDecimal quantity = decimalValue(adjustment.get("quantity"), null);
                if (warehouseCode.isBlank() || productCode.isBlank() || quantity == null || quantity.signum() < 0) {
                    throw new IllegalArgumentException("analysis adjustment warehouse, product and quantity are required");
                }
                int updated = jdbcTemplate.update("""
                        UPDATE purchase_replenishment_analysis_item
                           SET manual_adjusted_qty = ?
                         WHERE analysis_id = ? AND warehouse_code = ? AND product_code = ?
                        """, quantity, analysisId, warehouseCode, productCode);
                if (updated != 1) {
                    throw new IllegalArgumentException("analysis adjustment does not match the analysis snapshot");
                }
                adjustedTotal = adjustedTotal.add(quantity);
            }
        }

        Long deptId = resolveDemandDepartment(request);
        List<Map<String, Object>> validatedItems = new ArrayList<>();
        BigDecimal totalSuggested = BigDecimal.ZERO;
        String source = defaultText(request.get("demandSource"), "manual");
        String urgent = defaultText(request.get("urgentLevel"), "normal");
        String userRemark = stringValue(request.get("remark"));
        String remark = analysisNo == null
                ? nullIfBlank(userRemark)
                : "智能补货分析 " + analysisNo + " 生成" + (userRemark.isBlank() ? "" : "；" + userRemark);

        for (Map<String, Object> item : items) {
            String productCode = stringValue(item.get("productCode"));
            if (productCode == null || productCode.isBlank()) {
                throw new IllegalArgumentException("productCode is required");
            }
            BigDecimal quantity = decimalValue(item.get("quantity"), null);
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalArgumentException("quantity must be greater than zero");
            }
            Map<String, Object> product = findProduct(productCode);
            BigDecimal suggested = suggestedPurchaseQty(quantity, product);
            totalSuggested = totalSuggested.add(suggested);
            validatedItems.add(Map.of("product", product, "quantity", quantity, "suggested", suggested));
        }

        String demandNo = support.nextNo(PURCHASE_DEMAND);
        OperatorContext operator = operatorContextProvider.current();
        for (Map<String, Object> item : validatedItems) {
            @SuppressWarnings("unchecked")
            Map<String, Object> product = (Map<String, Object>) item.get("product");

            jdbcTemplate.update("""
                    INSERT INTO purchase_demand (
                      demand_no, demand_source, demand_status, urgent_level, dept_id, product_id,
                      quantity, approved_quantity, suggested_purchase_qty, remark, create_by
                    ) VALUES (?, ?, 'draft', ?, ?, ?, ?, 0, ?, ?, ?)
                    """, demandNo, source, urgent, deptId, product.get("productId"),
                    item.get("quantity"), item.get("suggested"), remark, operator.userId());
        }

        if (analysisId != null) {
            jdbcTemplate.update("""
                    UPDATE purchase_replenishment_analysis
                       SET total_recommended_qty = ?, analysis_status = 'demand_created',
                           remark = CONCAT('生成采购需求：', ?)
                     WHERE analysis_id = ? AND analysis_status = 'analyzed'
                    """, adjustedTotal, demandNo, analysisId);
        }

        writeAudit("create_demand", taskNoNumeric(demandNo), demandNo, "create purchase demand");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("demandNo", demandNo);
        result.put("suggestedPurchaseQty", totalSuggested);
        if (analysisId != null) {
            result.put("analysisId", analysisId);
            result.put("analysisNo", analysisNo);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> performDemandAction(String demandNo, PurchaseOrderActionRequest request) {
        permissionGuard.require(switch (request.action() == null ? "" : request.action().trim()) {
            case "submit" -> "purchase-demand:submit";
            case "approve" -> "purchase-demand:review";
            case "reject" -> "purchase-demand:reject";
            default -> throw new IllegalArgumentException("purchase demand action is invalid");
        });
        return purchaseFlow.runDemandAction(demandNo, request);
    }

    // ==================== 采购计划 ====================

    public Map<String, Object> listPlans(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1
                """);
        appendLike(where, args, "pp.plan_no", params.get("planNo"));
        appendLike(where, args, "p.product_name", params.get("keyword"));
        appendLike(where, args, "s.supplier_name", params.get("supplierName"));
        if (!isBlank(params.get("status"))) {
            where.append(" AND pp.plan_status = ?");
            args.add(params.get("status").trim());
        }

        String fromClause = """
                  FROM purchase_plan pp
                  JOIN supplier s ON s.supplier_id = pp.supplier_id
                  JOIN product p ON p.product_id = pp.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN (
                       SELECT ppd.plan_id,
                              GROUP_CONCAT(DISTINCT d.dept_name ORDER BY d.dept_name SEPARATOR '、') AS initiatingDeptName,
                              GROUP_CONCAT(DISTINCT w.warehouse_name ORDER BY w.warehouse_name SEPARATOR '、') AS deliveryWarehouseName
                         FROM purchase_plan_demand ppd
                         JOIN purchase_demand pd ON pd.demand_id = ppd.demand_id
                         LEFT JOIN sys_dept d ON d.dept_id = pd.dept_id AND d.deleted = 0
                         LEFT JOIN warehouse w ON w.dept_id = pd.dept_id AND w.deleted = 0 AND w.status = 1
                        GROUP BY ppd.plan_id
                  ) plan_origin ON plan_origin.plan_id = pp.plan_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT pp.plan_id AS planId, pp.plan_no AS planNo, pp.plan_status AS planStatus,
                       s.supplier_name AS supplierName, p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, p.registration_no AS registrationNo,
                       m.manufacturer_name AS manufacturerName, p.unit, p.purchase_price AS unitPrice,
                       pp.planned_quantity AS plannedQuantity, pp.converted_order_no AS convertedOrderNo,
                       ROUND(p.purchase_price * pp.planned_quantity, 4) AS amount,
                       p.tender_sub_code AS tenderSubCode,
                       plan_origin.initiatingDeptName, plan_origin.deliveryWarehouseName,
                       pp.remark, DATE_FORMAT(pp.create_time, '%Y-%m-%d %H:%i') AS createTime
                """ + fromClause + where + " ORDER BY pp.create_time DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    @Transactional
    public Map<String, Object> createPlanFromDemands(Map<String, Object> request) {
        permissionGuard.require("purchase-demand:convert-plan");
        Object rawDemandNos = request.get("demandNos");
        if (!(rawDemandNos instanceof List<?> rawList)) {
            throw new IllegalArgumentException("请选择需要转计划的已审核需求");
        }
        List<String> demandNos = rawList.stream()
                .map(PurchaseOrderService::stringValue)
                .filter(value -> !isBlank(value))
                .distinct()
                .toList();
        if (demandNos.isEmpty()) {
            throw new IllegalArgumentException("请选择需要转计划的已审核需求");
        }
        String supplierName = stringValue(request.get("supplierName"));
        Long requestedSupplierId = isBlank(supplierName) ? null : findSupplierId(supplierName);
        String placeholders = String.join(",", java.util.Collections.nCopies(demandNos.size(), "?"));
        List<Object> queryArgs = new ArrayList<>(demandNos);
        StringBuilder demandWhere = new StringBuilder(" WHERE pd.demand_no IN (" + placeholders + ")");
        demandWhere.append(" AND pd.demand_status = 'approved' AND pd.approved_quantity > 0");
        dataScopeService.appendScope(demandWhere, queryArgs, "pd.dept_id", "pd.create_by");
        List<Map<String, Object>> demands = jdbcTemplate.queryForList("""
                SELECT pd.demand_id AS demandId, pd.demand_no AS demandNo, pd.product_id AS productId,
                       ps.supplier_id AS productSupplierId, pd.approved_quantity AS plannedQuantity
                  FROM purchase_demand pd
                  JOIN product p ON p.product_id = pd.product_id
                  LEFT JOIN supplier ps ON ps.supplier_id = p.supplier_id
                                        AND ps.status = 1 AND ps.deleted = 0
                """ + demandWhere + " ORDER BY pd.demand_id FOR UPDATE", queryArgs.toArray());
        long matchedDemandCount = demands.stream()
                .map(row -> stringValue(row.get("demandNo")))
                .filter(Objects::nonNull)
                .distinct()
                .count();
        if (matchedDemandCount != demandNos.size()) {
            throw new IllegalArgumentException("所选需求不存在、无权访问、状态已变化或已转计划，请刷新后重试");
        }
        int created = 0;
        List<String> planNos = new ArrayList<>();
        for (Map<String, Object> row : demands) {
            Long supplierId = requestedSupplierId != null
                    ? requestedSupplierId
                    : nullableLong(row.get("productSupplierId"));
            if (supplierId == null) {
                supplierId = findFirstEnabledSupplierId();
            }
            String planNo = support.nextNo(PURCHASE_PLAN);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            Long finalSupplierId = supplierId;
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO purchase_plan (plan_no, plan_status, supplier_id, product_id, planned_quantity, remark)
                        VALUES (?, 'draft', ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, planNo);
                ps.setLong(2, finalSupplierId);
                ps.setLong(3, ((Number) row.get("productId")).longValue());
                ps.setBigDecimal(4, (BigDecimal) row.get("plannedQuantity"));
                ps.setString(5, defaultText(request.get("remark"), "由已审核采购需求生成"));
                return ps;
            }, keyHolder);
            Long planId = Objects.requireNonNull(keyHolder.getKey()).longValue();
            writeAudit("purchase_plan", "create_from_demand", planId, planNo,
                    "由采购需求 " + row.get("demandNo") + " 生成");
            Long demandId = ((Number) row.get("demandId")).longValue();
            jdbcTemplate.update("""
                    INSERT INTO purchase_plan_demand (plan_id, demand_id, allocated_quantity)
                    VALUES (?, ?, ?)
                    """, planId, demandId, row.get("plannedQuantity"));
            int changed = jdbcTemplate.update("""
                    UPDATE purchase_demand
                       SET demand_status = 'planned'
                     WHERE demand_id = ? AND demand_status = 'approved'
                    """, demandId);
            if (changed != 1) {
                throw new IllegalStateException("purchase demand status changed, please refresh and retry");
            }
            planNos.add(planNo);
            created++;
        }
        return Map.of("createdPlans", created, "planNos", List.copyOf(planNos));
    }

    @Transactional
    public Map<String, Object> performPlanAction(String planNo, PurchaseOrderActionRequest request) {
        permissionGuard.require(switch (request.action() == null ? "" : request.action().trim()) {
            case "approve" -> "purchase-plan:approve";
            case "reject" -> "purchase-plan:reject";
            case "execute" -> "purchase-plan:execute";
            default -> throw new IllegalArgumentException("purchase plan action is invalid");
        });
        return purchaseFlow.runPlanAction(planNo, request);
    }

    // ==================== 跟踪与选项 ====================

    public List<Map<String, Object>> getTracking(String orderNo) {
        Long orderId = jdbcTemplate.queryForObject("SELECT purchase_order_id FROM purchase_order WHERE order_no = ?", Long.class, orderNo);
        return trackingRows(orderId);
    }

    public Map<String, Object> getOptions() {
        List<Map<String, Object>> suppliers = jdbcTemplate.queryForList("""
                SELECT supplier_name AS supplierName
                  FROM supplier
                 WHERE deleted = 0 AND status = 1
                 ORDER BY supplier_id DESC
                 LIMIT 100
                """);
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT product_code AS productCode, product_name AS productName, spec_model AS specModel,
                       unit, purchase_price AS purchasePrice
                  FROM product
                 WHERE deleted = 0 AND status = 1
                 ORDER BY product_id DESC
                 LIMIT 200
                """);
        List<Object> departmentArgs = new ArrayList<>();
        StringBuilder departmentWhere = new StringBuilder(" WHERE d.deleted = 0 AND d.status = 1");
        appendPurchasableDepartmentScope(departmentWhere, departmentArgs, "d.dept_id");
        List<Map<String, Object>> departments = jdbcTemplate.queryForList("""
                SELECT d.dept_code AS deptCode, d.dept_name AS deptName,
                       CASE WHEN d.dept_id = ? THEN 1 ELSE 0 END AS isCurrent
                  FROM sys_dept d
                """ + departmentWhere + " ORDER BY isCurrent DESC, d.dept_name, d.dept_id",
                prepend(operatorContextProvider.current().deptId(), departmentArgs));
        return Map.of("suppliers", suppliers, "products", products, "departments", departments);
    }

    /**
     * 采购管理智能补货分析：独立于科室申领（缺货提醒）的智能补货事务，单独写入
     * purchase_replenishment_analysis 表；出库量取实际已拣配配送单，并按申领来源一级库归集。
     */
    @Transactional
    public Map<String, Object> smartReplenishmentAnalysis(Map<String, String> params) {
        int selectedPeriodDays = smartPeriodDays(params.get("periodDays"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                WITH primary_warehouse AS (
                    SELECT warehouse_id, warehouse_code, warehouse_name, warehouse_type
                      FROM warehouse
                     WHERE deleted = 0
                       AND status = 1
                       AND (
                            warehouse_type LIKE '%一级%'
                         OR warehouse_type LIKE '%中心%'
                         OR (parent_id = 0 AND dept_id IS NULL)
                       )
                ),
                delivery_out AS (
                    SELECT dr.source_warehouse_id AS warehouse_id,
                           dri.product_id,
                           SUM(CASE WHEN sdo.create_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY)
                                    THEN sdo.quantity ELSE 0 END) AS issue5,
                           SUM(CASE WHEN sdo.create_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 15 DAY)
                                    THEN sdo.quantity ELSE 0 END) AS issue15,
                           SUM(CASE WHEN sdo.create_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
                                    THEN sdo.quantity ELSE 0 END) AS issue30,
                           SUM(CASE WHEN sdo.create_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 45 DAY)
                                    THEN sdo.quantity ELSE 0 END) AS issue45,
                           SUM(CASE WHEN sdo.create_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 60 DAY)
                                    THEN sdo.quantity ELSE 0 END) AS issue60
                      FROM spd_delivery_order sdo
                      JOIN department_requisition dr ON dr.requisition_no = sdo.requisition_no
                      JOIN department_requisition_item dri
                        ON dri.item_id = sdo.requisition_item_id
                       AND dri.requisition_id = dr.requisition_id
                     WHERE sdo.status IN ('picked', 'signed')
                       AND sdo.create_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 60 DAY)
                       AND dr.source_warehouse_id IS NOT NULL
                     GROUP BY dr.source_warehouse_id, dri.product_id
                ),
                outbound AS (
                    SELECT pw.warehouse_id,
                           delivery.product_id,
                           delivery.issue5, delivery.issue15, delivery.issue30,
                           delivery.issue45, delivery.issue60
                      FROM delivery_out delivery
                      JOIN primary_warehouse pw ON pw.warehouse_id = delivery.warehouse_id
                ),
                stock AS (
                    SELECT ib.warehouse_id,
                           ib.product_id,
                           SUM(ib.available_qty) AS current_qty
                      FROM inventory_balance ib
                      JOIN primary_warehouse pw ON pw.warehouse_id = ib.warehouse_id
                     GROUP BY ib.warehouse_id, ib.product_id
                ),
                analysis_key AS (
                    SELECT warehouse_id, product_id FROM outbound
                    UNION
                    SELECT warehouse_id, product_id FROM stock
                )
                SELECT pw.warehouse_code AS warehouseCode,
                       pw.warehouse_name AS warehouseName,
                       pw.warehouse_type AS warehouseType,
                       p.product_code AS productCode,
                       p.product_name AS productName,
                       p.spec_model AS specModel,
                       p.unit,
                       p.purchase_price AS purchasePrice,
                       p.min_purchase_qty AS minPurchaseQty,
                       s.supplier_name AS supplierName,
                       COALESCE(st.current_qty, 0) AS currentQty,
                       COALESCE(o.issue5, 0) AS issue5,
                       COALESCE(o.issue15, 0) AS issue15,
                       COALESCE(o.issue30, 0) AS issue30,
                       COALESCE(o.issue45, 0) AS issue45,
                       COALESCE(o.issue60, 0) AS issue60
                  FROM analysis_key ak
                  JOIN primary_warehouse pw ON pw.warehouse_id = ak.warehouse_id
                  JOIN product p ON p.product_id = ak.product_id AND p.deleted = 0 AND p.status = 1
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                  LEFT JOIN outbound o ON o.warehouse_id = ak.warehouse_id AND o.product_id = ak.product_id
                  LEFT JOIN stock st ON st.warehouse_id = ak.warehouse_id AND st.product_id = ak.product_id
                 ORDER BY COALESCE(o.issue60, 0) DESC, p.product_name
                """);

        List<Map<String, Object>> suggestions = rows.stream()
                .map(row -> purchaseReplenishmentSuggestion(row, selectedPeriodDays))
                .toList();
        BigDecimal totalRecommendedQty = suggestions.stream()
                .map(row -> decimalValue(row.get("recommendedQty")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFormulaQty = suggestions.stream()
                .map(row -> decimalValue(row.get("formulaReplenishQty")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> storedAnalysis = storePurchaseReplenishmentAnalysis(
                selectedPeriodDays,
                suggestions,
                totalFormulaQty,
                totalRecommendedQty);
        return Map.of(
                "analysisId", storedAnalysis.get("analysisId"),
                "analysisNo", storedAnalysis.get("analysisNo"),
                "selectedPeriodDays", selectedPeriodDays,
                "periodDays", SMART_REPLENISHMENT_PERIOD_DAYS,
                "totalFormulaQty", totalFormulaQty,
                "totalRecommendedQty", totalRecommendedQty,
                "rows", suggestions
        );
    }

    // ==================== 私有辅助方法 ====================

    private String createOrderFromPlan(String planNo, Map<String, Object> plan) {
        Long supplierId = ((Number) plan.get("supplierId")).longValue();
        Long productId = ((Number) plan.get("productId")).longValue();
        BigDecimal plannedQuantity = (BigDecimal) plan.get("plannedQuantity");
        Map<String, Object> product = jdbcTemplate.queryForMap("""
                SELECT product_code AS productCode, unit, purchase_price AS purchasePrice
                  FROM product
                 WHERE product_id = ?
                """, productId);
        String orderNo = support.nextNo(PURCHASE_ORDER);
        BigDecimal price = (BigDecimal) product.get("purchasePrice");
        BigDecimal totalAmount = plannedQuantity.multiply(price);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO purchase_order (
                      order_no, supplier_id, order_source, order_status, total_amount,
                      expected_arrival_date, create_by, purchase_type
                    ) VALUES (?, ?, 'plan', 'draft', ?, ?, ?, 'regular')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, orderNo);
            ps.setLong(2, supplierId);
            ps.setBigDecimal(3, totalAmount);
            ps.setDate(4, Date.valueOf(LocalDate.now().plusDays(7)));
            ps.setLong(5, operatorContextProvider.current().userId());
            return ps;
        }, keyHolder);
        Long orderId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        jdbcTemplate.update("""
                INSERT INTO purchase_order_item (
                  purchase_order_id, product_id, quantity, unit, estimated_unit_price, amount, received_quantity
                ) VALUES (?, ?, ?, ?, ?, ?, 0)
                """, orderId, productId, plannedQuantity, product.get("unit"), price, totalAmount);
        appendTracking(orderId, "created_from_plan", "订单由采购计划生成", planNo);
        return orderNo;
    }

    private void insertItems(Long orderId, List<PurchaseOrderItemRequest> items) {
        for (PurchaseOrderItemRequest item : items) {
            Map<String, Object> product = findProduct(item.productCode());
            Long productId = ((Number) product.get("productId")).longValue();
            BigDecimal quantity = defaultDecimal(item.quantity(), BigDecimal.ZERO);
            BigDecimal price = item.estimatedUnitPrice() == null
                    ? (BigDecimal) product.get("purchasePrice")
                    : item.estimatedUnitPrice();
            jdbcTemplate.update("""
                    INSERT INTO purchase_order_item (
                      purchase_order_id, product_id, quantity, unit, estimated_unit_price, amount, received_quantity
                    ) VALUES (?, ?, ?, ?, ?, ?, 0)
                    """, orderId, productId, quantity,
                    isBlank(item.unit()) ? String.valueOf(product.get("unit")) : item.unit().trim(),
                    price, quantity.multiply(price));
        }
    }

    private BigDecimal suggestedPurchaseQty(BigDecimal demandQty, Map<String, Object> product) {
        BigDecimal minPurchaseQty = (BigDecimal) product.get("minPurchaseQty");
        BigDecimal base = demandQty.max(minPurchaseQty == null ? BigDecimal.ONE : minPurchaseQty);
        return base.setScale(0, java.math.RoundingMode.CEILING);
    }

    private Map<String, Object> findProduct(String productCode) {
        if (isBlank(productCode)) {
            throw new IllegalArgumentException("product code is required");
        }
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, unit,
                       purchase_price AS purchasePrice, min_purchase_qty AS minPurchaseQty
                  FROM product
                 WHERE product_code = ? AND deleted = 0 AND status = 1
                """, productCode.trim());
    }

    private Long findSupplierId(String supplierName) {
        if (isBlank(supplierName)) {
            throw new IllegalArgumentException("supplier is required");
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT supplier_id FROM supplier WHERE supplier_name = ? AND deleted = 0 AND status = 1 LIMIT 1",
                Long.class,
                supplierName.trim()
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("supplier does not exist or is disabled");
        }
        return ids.get(0);
    }

    private Long findFirstEnabledSupplierId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT supplier_id FROM supplier WHERE deleted = 0 AND status = 1 ORDER BY supplier_id LIMIT 1",
                Long.class
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("no enabled supplier can generate purchase plan");
        }
        return ids.get(0);
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Long resolveDemandDepartment(Map<String, Object> request) {
        String deptCode = stringValue(request.get("deptCode"));
        String deptName = stringValue(request.get("deptName"));
        OperatorContext operator = operatorContextProvider.current();
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE d.deleted = 0 AND d.status = 1");
        if (!isBlank(deptCode)) {
            where.append(" AND d.dept_code = ?");
            args.add(deptCode.trim());
            if (!isBlank(deptName)) {
                where.append(" AND d.dept_name = ?");
                args.add(deptName.trim());
            }
        } else if (!isBlank(deptName)) {
            where.append(" AND d.dept_name = ?");
            args.add(deptName.trim());
        } else if (operator.deptId() != null) {
            where.append(" AND d.dept_id = ?");
            args.add(operator.deptId());
        } else if (operator.roles().contains("ROLE_SYSTEM")) {
            return null;
        } else {
            throw new IllegalArgumentException("请选择采购需求科室");
        }
        appendPurchasableDepartmentScope(where, args, "d.dept_id");
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT d.dept_id FROM sys_dept d" + where + " ORDER BY d.dept_id",
                Long.class,
                args.toArray()
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("科室不存在、已停用或超出当前操作人的数据范围");
        }
        if (ids.size() > 1) {
            throw new IllegalArgumentException("科室名称不唯一，请使用科室编码选择");
        }
        return ids.get(0);
    }

    private void appendPurchasableDepartmentScope(StringBuilder where, List<Object> args, String deptColumn) {
        OperatorContext operator = operatorContextProvider.current();
        if (operator.canViewAllData()) {
            return;
        }
        if (operator.dataScope() == OperatorContext.DATA_SCOPE_CUSTOM) {
            where.append(" AND ").append(deptColumn)
                    .append(" IN (SELECT rd.dept_id FROM sys_role_dept rd JOIN sys_user_role ur ON ur.role_id = rd.role_id WHERE ur.user_id = ?)");
            args.add(operator.userId());
            return;
        }
        if (operator.deptId() == null) {
            where.append(" AND 1 = 0");
            return;
        }
        if (operator.dataScope() == OperatorContext.DATA_SCOPE_DEPT_AND_CHILDREN) {
            where.append(" AND (").append(deptColumn).append(" = ? OR ").append(deptColumn)
                    .append(" IN (SELECT dept_id FROM sys_dept WHERE parent_id = ? AND deleted = 0))");
            args.add(operator.deptId());
            args.add(operator.deptId());
            return;
        }
        where.append(" AND ").append(deptColumn).append(" = ?");
        args.add(operator.deptId());
    }

    private List<Map<String, Object>> trackingRows(Long orderId) {
        return jdbcTemplate.queryForList("""
                SELECT tracking_id AS trackingId, event_type AS eventType, event_status AS eventStatus,
                       remark, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM purchase_order_tracking
                 WHERE purchase_order_id = ?
                 ORDER BY create_time, tracking_id
                """, orderId);
    }

    private void appendTracking(Long orderId, String eventType, String eventStatus, String remark) {
        jdbcTemplate.update("""
                INSERT INTO purchase_order_tracking (purchase_order_id, event_type, event_status, remark)
                VALUES (?, ?, ?, ?)
                """, orderId, eventType, eventStatus, remark);
    }

    private void writeAudit(String operationType, Long orderId, String orderNo, String remark) {
        String bizType = operationType.contains("demand") ? "purchase_demand" : "purchase_order";
        writeAudit(bizType, operationType, orderId, orderNo, remark);
    }

    private void writeAudit(String bizType, String operationType, Long orderId, String orderNo, String remark) {
        auditLogService.record(bizType, operationType, orderId, orderNo, remark);
    }

    private static Object[] prepend(Object first, List<Object> rest) {
        List<Object> args = new ArrayList<>(rest.size() + 1);
        args.add(first);
        args.addAll(rest);
        return args.toArray();
    }

    private static void validateRequest(PurchaseOrderRequest request) {
        normalizeOrderSource(request.orderSource());
        normalizePurchaseType(request.purchaseType(), request.orderSource());
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("采购订单至少需要一条明细");
        }
        for (PurchaseOrderItemRequest item : request.items()) {
            if (isBlank(item.productCode()) || item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("商品编码和采购数量为必填项");
            }
        }
    }

    private static String normalizeOrderSource(String value) {
        if (isBlank(value)) {
            return "manual";
        }
        return switch (value.trim().toLowerCase()) {
            case "manual", "手工采购", "手动采购", "临时采购", "紧急采购", "常规采购" -> "manual";
            case "plan", "计划采购" -> "plan";
            case "demand", "需求采购" -> "demand";
            default -> throw new IllegalArgumentException("订单来源仅支持手工、计划或需求");
        };
    }

    private static String normalizePurchaseType(String value, String legacyOrderSource) {
        String candidate = isBlank(value) ? legacyOrderSource : value;
        if (isBlank(candidate)) {
            return "regular";
        }
        return switch (candidate.trim().toLowerCase()) {
            case "regular", "常规采购", "manual", "手工采购", "手动采购", "plan", "计划采购", "demand", "需求采购" -> "regular";
            case "temporary", "临时采购" -> "temporary";
            case "urgent", "紧急采购" -> "urgent";
            default -> throw new IllegalArgumentException("采购类型仅支持常规、临时或紧急");
        };
    }

    private static String orderActionPermission(String action) {
        return switch (action == null ? "" : action.trim()) {
            case "submit" -> "purchase-order:submit";
            case "approve" -> "purchase-order:approve";
            case "reject" -> "purchase-order:reject";
            case "send" -> "purchase-order:send";
            case "close" -> "purchase-order:close";
            case "void" -> "purchase-order:void";
            default -> throw new IllegalArgumentException("采购订单动作无效");
        };
    }

    private Long taskNoNumeric(String no) {
        String digits = no == null ? "" : no.replaceAll("\\D", "");
        if (digits.length() > 18) {
            digits = digits.substring(digits.length() - 18);
        }
        return digits.isBlank() ? 0L : Long.parseLong(digits);
    }

    private static void appendLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (!isBlank(value)) {
            sql.append(" AND ").append(column).append(" LIKE ?");
            args.add("%" + value.trim() + "%");
        }
    }

    private static Date parseDate(String value) {
        return isBlank(value) ? null : Date.valueOf(LocalDate.parse(value.trim()));
    }

    private Map<String, Object> purchaseReplenishmentSuggestion(Map<String, Object> row, int selectedPeriodDays) {
        BigDecimal currentQty = decimalValue(row.get("currentQty"));
        BigDecimal issue5 = decimalValue(row.get("issue5"));
        BigDecimal issue15 = decimalValue(row.get("issue15"));
        BigDecimal issue30 = decimalValue(row.get("issue30"));
        BigDecimal issue45 = decimalValue(row.get("issue45"));
        BigDecimal issue60 = decimalValue(row.get("issue60"));
        BigDecimal minPurchaseQty = decimalValue(row.get("minPurchaseQty"));
        ReplenishmentSuggestionCalculator.Result calculation = ReplenishmentSuggestionCalculator.calculate(
                Map.of(5, issue5, 15, issue15, 30, issue30, 45, issue45, 60, issue60),
                selectedPeriodDays,
                currentQty,
                minPurchaseQty,
                true);
        Map<String, Object> suggestion = new LinkedHashMap<>(row);
        suggestion.put("issue5", issue5);
        suggestion.put("issue15", issue15);
        suggestion.put("issue30", issue30);
        suggestion.put("issue45", issue45);
        suggestion.put("issue60", issue60);
        suggestion.put("currentQty", currentQty);
        suggestion.put("selectedIssueQty", calculation.selectedIssueQty());
        suggestion.put("formulaReplenishQty", calculation.formulaReplenishQty());
        suggestion.put("recommendedQty", calculation.recommendedQty());
        suggestion.put("formulaText", "近" + selectedPeriodDays + "天实际拣配出库数量 - 一级库当前可用库存");
        return suggestion;
    }

    private Map<String, Object> storePurchaseReplenishmentAnalysis(int selectedPeriodDays,
                                                                   List<Map<String, Object>> rows,
                                                                   BigDecimal totalFormulaQty,
                                                                   BigDecimal totalRecommendedQty) {
        String analysisNo = support.nextNo(PURCHASE_REPLENISHMENT_ANALYSIS);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO purchase_replenishment_analysis (
                      analysis_no, selected_period_days, item_count, total_formula_qty,
                      total_recommended_qty, analysis_status, remark
                    ) VALUES (?, ?, ?, ?, ?, 'analyzed', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, analysisNo);
            ps.setInt(2, selectedPeriodDays);
            ps.setInt(3, rows.size());
            ps.setBigDecimal(4, totalFormulaQty);
            ps.setBigDecimal(5, totalRecommendedQty);
            ps.setString(6, "基于科室申请表自动分析");
            return ps;
        }, keyHolder);
        Long storedId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        for (Map<String, Object> row : rows) {
            jdbcTemplate.update("""
                    INSERT INTO purchase_replenishment_analysis_item (
                      analysis_id, warehouse_code, warehouse_name, warehouse_type, supplier_name,
                      product_code, product_name, spec_model, unit, purchase_price, min_purchase_qty,
                      current_qty, issue_5_qty, issue_15_qty, issue_30_qty, issue_45_qty, issue_60_qty,
                      selected_issue_qty, formula_replenish_qty, recommended_qty, manual_adjusted_qty, formula_text
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                    """,
                    storedId,
                    row.get("warehouseCode"),
                    row.get("warehouseName"),
                    row.get("warehouseType"),
                    row.get("supplierName"),
                    row.get("productCode"),
                    row.get("productName"),
                    row.get("specModel"),
                    row.get("unit"),
                    row.get("purchasePrice"),
                    row.get("minPurchaseQty"),
                    row.get("currentQty"),
                    row.get("issue5"),
                    row.get("issue15"),
                    row.get("issue30"),
                    row.get("issue45"),
                    row.get("issue60"),
                    row.get("selectedIssueQty"),
                    row.get("formulaReplenishQty"),
                    row.get("recommendedQty"),
                    row.get("formulaText"));
        }
        return Map.of("analysisId", storedId, "analysisNo", analysisNo);
    }

    private static int smartPeriodDays(String value) {
        if (isBlank(value)) {
            return 30;
        }
        int parsed = Integer.parseInt(value.trim());
        return SMART_REPLENISHMENT_PERIOD_DAYS.contains(parsed) ? parsed : 30;
    }

    private static BigDecimal defaultDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static BigDecimal decimalValue(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static BigDecimal decimalValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String defaultText(Object value, String fallback) {
        String text = stringValue(value);
        return text.isBlank() ? fallback : text;
    }
}
