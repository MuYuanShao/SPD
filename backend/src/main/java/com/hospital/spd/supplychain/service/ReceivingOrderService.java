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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

/**
 * Coordinates receiving acceptance and stock-in creation while keeping purchase order fulfillment aligned.
 */
@Service
public class ReceivingOrderService {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final OperatorContextProvider operatorContextProvider;
    private final PurchaseFulfillmentService purchaseFulfillmentService;
    private final ApprovalFlowGuard approvalFlowGuard;
    private final SettlementPointService settlementPointService;

    public ReceivingOrderService(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, OperatorContext::system, new PurchaseFulfillmentService(jdbcTemplate),
                new ApprovalFlowGuard(jdbcTemplate), new SettlementPointService(jdbcTemplate, support));
    }

    public ReceivingOrderService(JdbcTemplate jdbcTemplate,
                                 SupplyChainSupport support,
                                 OperatorContextProvider operatorContextProvider) {
        this(jdbcTemplate, support, operatorContextProvider, new PurchaseFulfillmentService(jdbcTemplate),
                new ApprovalFlowGuard(jdbcTemplate, operatorContextProvider),
                new SettlementPointService(jdbcTemplate, support));
    }

    public ReceivingOrderService(JdbcTemplate jdbcTemplate,
                                 SupplyChainSupport support,
                                 OperatorContextProvider operatorContextProvider,
                                 PurchaseFulfillmentService purchaseFulfillmentService) {
        this(jdbcTemplate, support, operatorContextProvider, purchaseFulfillmentService,
                new ApprovalFlowGuard(jdbcTemplate, operatorContextProvider),
                new SettlementPointService(jdbcTemplate, support));
    }

    public ReceivingOrderService(JdbcTemplate jdbcTemplate,
                                 SupplyChainSupport support,
                                 OperatorContextProvider operatorContextProvider,
                                 PurchaseFulfillmentService purchaseFulfillmentService,
                                 ApprovalFlowGuard approvalFlowGuard) {
        this(jdbcTemplate, support, operatorContextProvider, purchaseFulfillmentService, approvalFlowGuard,
                new SettlementPointService(jdbcTemplate, support));
    }
    @Autowired
    public ReceivingOrderService(JdbcTemplate jdbcTemplate,
                                 SupplyChainSupport support,
                                 OperatorContextProvider operatorContextProvider,
                                 PurchaseFulfillmentService purchaseFulfillmentService,
                                 ApprovalFlowGuard approvalFlowGuard,
                                 SettlementPointService settlementPointService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.operatorContextProvider = operatorContextProvider;
        this.purchaseFulfillmentService = purchaseFulfillmentService;
        this.approvalFlowGuard = approvalFlowGuard;
        this.settlementPointService = settlementPointService;
    }

    // ===== 公开方法 =====

    public Map<String, Object> list(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1
                """);
        appendLike(where, args, "ro.receiving_no", params.get("receivingNo"));
        appendLike(where, args, "po.order_no", params.get("purchaseOrderNo"));
        appendLike(where, args, "s.supplier_name", params.get("supplierName"));
        if (!isBlank(params.get("status"))) {
            where.append(" AND ro.receiving_status = ?");
            args.add(params.get("status").trim());
        } else if (!isBlank(params.get("statusGroup"))) {
            switch (params.get("statusGroup").trim()) {
                case "pending" -> where.append(" AND ro.receiving_status = 'draft'");
                case "completed" -> where.append(" AND ro.receiving_status IN ('approved', 'rejected')");
                default -> throw new IllegalArgumentException("收货状态分组不正确");
            }
        }

        String fromClause = """
                  FROM receiving_order ro
                  LEFT JOIN purchase_order po ON po.purchase_order_id = ro.purchase_order_id
                  JOIN supplier s ON s.supplier_id = ro.supplier_id
                  JOIN warehouse w ON w.warehouse_id = ro.warehouse_id
                  LEFT JOIN receiving_order_item roi ON roi.receiving_order_id = ro.receiving_order_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT ro.receiving_order_id) " + fromClause + where,
                Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ro.receiving_order_id AS receivingOrderId, ro.receiving_no AS receivingNo,
                       po.order_no AS purchaseOrderNo, s.supplier_name AS supplierName,
                       w.warehouse_name AS warehouseName, ro.receiving_status AS receivingStatus,
                       ro.receiving_type AS receivingType, ro.is_agent AS isAgent,
                       DATE_FORMAT(ro.receive_time, '%Y-%m-%d %H:%i') AS receiveTime,
                       DATE_FORMAT(ro.create_time, '%Y-%m-%d %H:%i') AS createTime,
                       COUNT(roi.item_id) AS itemCount,
                       COALESCE(SUM(roi.quantity), 0) AS receiveQuantity,
                       COALESCE(SUM(roi.amount), 0) AS receiveAmount
                  FROM receiving_order ro
                  LEFT JOIN purchase_order po ON po.purchase_order_id = ro.purchase_order_id
                  JOIN supplier s ON s.supplier_id = ro.supplier_id
                  JOIN warehouse w ON w.warehouse_id = ro.warehouse_id
                  LEFT JOIN receiving_order_item roi ON roi.receiving_order_id = ro.receiving_order_id
                """ + where + " GROUP BY ro.receiving_order_id ORDER BY ro.create_time DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());

        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS totalReceipts,
                       SUM(CASE WHEN receiving_status = 'draft' THEN 1 ELSE 0 END) AS draftCount,
                       SUM(CASE WHEN receiving_status = 'approved' THEN 1 ELSE 0 END) AS approvedCount,
                       SUM(CASE WHEN receiving_status = 'rejected' THEN 1 ELSE 0 END) AS rejectedCount,
                       SUM(CASE WHEN receiving_status IN ('approved', 'rejected') THEN 1 ELSE 0 END) AS completedCount
                  FROM receiving_order
                """);
        return PageResponse.of(rows, total == null ? 0 : total, pageReq, summary);
    }

    public Map<String, Object> detail(String receivingNo) {
        return detail(receivingNo, Map.of());
    }

    public Map<String, Object> detail(String receivingNo, Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT ro.receiving_order_id AS receivingOrderId, ro.receiving_no AS receivingNo,
                       po.order_no AS purchaseOrderNo, s.supplier_name AS supplierName,
                       w.warehouse_name AS warehouseName, ro.receiving_status AS receivingStatus,
                       ro.remark, DATE_FORMAT(ro.receive_time, '%Y-%m-%d %H:%i') AS receiveTime,
                       DATE_FORMAT(ro.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM receiving_order ro
                  LEFT JOIN purchase_order po ON po.purchase_order_id = ro.purchase_order_id
                  JOIN supplier s ON s.supplier_id = ro.supplier_id
                  JOIN warehouse w ON w.warehouse_id = ro.warehouse_id
                 WHERE ro.receiving_no = ?
                """, receivingNo);
        Long receivingOrderId = ((Number) order.get("receivingOrderId")).longValue();
        List<Object> itemArgs = new ArrayList<>();
        itemArgs.add(receivingOrderId);
        StringBuilder itemWhere = new StringBuilder(" WHERE roi.receiving_order_id = ?");
        String keyword = params.get("keyword");
        if (!isBlank(keyword)) {
            itemWhere.append("""
                     AND (
                       p.product_code LIKE ?
                       OR p.product_name LIKE ?
                       OR roi.production_batch_no LIKE ?
                       OR ib.system_batch_no LIKE ?
                     )
                    """);
            String likeKeyword = "%" + keyword.trim() + "%";
            itemArgs.add(likeKeyword);
            itemArgs.add(likeKeyword);
            itemArgs.add(likeKeyword);
            itemArgs.add(likeKeyword);
        }
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM receiving_order_item roi
                  JOIN product p ON p.product_id = roi.product_id
                  LEFT JOIN inventory_batch ib ON ib.receiving_item_id = roi.item_id
                """ + itemWhere, Long.class, itemArgs.toArray());
        List<Object> queryArgs = new ArrayList<>(itemArgs);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                       roi.production_batch_no AS productionBatchNo,
                       roi.udi_code AS udiCode,
                       DATE_FORMAT(roi.production_date, '%Y-%m-%d') AS productionDate,
                       DATE_FORMAT(roi.expire_date, '%Y-%m-%d') AS expireDate,
                       roi.quantity, roi.qualified_quantity AS qualifiedQuantity,
                       roi.unqualified_quantity AS unqualifiedQuantity,
                       roi.unit_price AS batchUnitPrice, roi.amount,
                       ib.system_batch_no AS systemBatchNo
                  FROM receiving_order_item roi
                  JOIN product p ON p.product_id = roi.product_id
                  LEFT JOIN inventory_batch ib ON ib.receiving_item_id = roi.item_id
                """ + itemWhere + """
                 ORDER BY roi.item_id
                 LIMIT ? OFFSET ?
                """, queryArgs.toArray());
        return Map.of(
                "order", order,
                "items", items,
                "total", total == null ? 0 : total,
                "page", pageReq.page(),
                "size", pageReq.size()
        );
    }

    @Transactional
    public Map<String, Object> create(ReceivingOrderRequest request) {
        validateRequest(request);
        OperatorContext operator = operatorContextProvider.current();
        if (isBlank(request.warehouseName())) {
            throw new IllegalArgumentException("warehouse is required");
        }
        Long purchaseOrderId = findPurchaseOrderId(request.purchaseOrderNo());
        validatePurchaseOrderItems(purchaseOrderId, request.items());
        Long supplierId = findSupplierId(request.supplierName(), purchaseOrderId);
        Long warehouseId = findWarehouseId(request.warehouseName());
        String receivingNo = support.nextNo(RECEIVING_ORDER);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO receiving_order (
                      receiving_no, purchase_order_id, supplier_id, warehouse_id,
                      receiving_status, receiving_type, is_agent, receiver_id, remark
                    ) VALUES (?, ?, ?, ?, 'draft', ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, receivingNo);
            if (purchaseOrderId == null) {
                ps.setObject(2, null);
            } else {
                ps.setLong(2, purchaseOrderId);
            }
            ps.setLong(3, supplierId);
            ps.setLong(4, warehouseId);
            ps.setString(5, nullIfBlank(request.receivingType()));
            ps.setInt(6, Boolean.TRUE.equals(request.isAgent()) ? 1 : 0);
            ps.setLong(7, operator.userId());
            ps.setString(8, nullIfBlank(request.remark()));
            return ps;
        }, keyHolder);
        Long receivingOrderId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        insertReceivingItems(receivingOrderId, request.items());
        writeAudit("create", receivingOrderId, receivingNo, "create receiving order");
        return Map.of("receivingNo", receivingNo);
    }

    @Transactional
    public Map<String, Object> update(String receivingNo, ReceivingOrderRequest request) {
        validateRequest(request);
        Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT receiving_order_id AS receivingOrderId, receiving_status AS receivingStatus
                  FROM receiving_order
                 WHERE receiving_no = ?
                 FOR UPDATE
                """, receivingNo);
        Long receivingOrderId = ((Number) order.get("receivingOrderId")).longValue();
        requireStatus(String.valueOf(order.get("receivingStatus")), "draft");

        Long purchaseOrderId = findPurchaseOrderId(request.purchaseOrderNo());
        validatePurchaseOrderItems(purchaseOrderId, request.items());
        Long supplierId = findSupplierId(request.supplierName(), purchaseOrderId);
        Long warehouseId = findWarehouseId(request.warehouseName());

        jdbcTemplate.update("""
                UPDATE receiving_order
                   SET purchase_order_id = ?, supplier_id = ?, warehouse_id = ?,
                       receiving_type = ?, is_agent = ?, remark = ?
                 WHERE receiving_order_id = ?
                """, purchaseOrderId, supplierId, warehouseId,
                nullIfBlank(request.receivingType()),
                Boolean.TRUE.equals(request.isAgent()) ? 1 : 0,
                nullIfBlank(request.remark()), receivingOrderId);
        jdbcTemplate.update("DELETE FROM receiving_order_item WHERE receiving_order_id = ?", receivingOrderId);
        insertReceivingItems(receivingOrderId, request.items());
        writeAudit("update", receivingOrderId, receivingNo, "修改待验收收货单");
        return Map.of("receivingNo", receivingNo, "status", "draft");
    }

    @Transactional
    public Map<String, Object> action(String receivingNo, ReceivingActionRequest request) {
        Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT receiving_order_id AS receivingOrderId, purchase_order_id AS purchaseOrderId,
                       warehouse_id AS warehouseId, supplier_id AS supplierId, receiver_id AS receiverId,
                       receiving_status AS receivingStatus
                 FROM receiving_order
                 WHERE receiving_no = ?
                 FOR UPDATE
                """, receivingNo);
        String action = request.action() == null ? "" : request.action().trim();
        String status = String.valueOf(order.get("receivingStatus"));
        Long receivingOrderId = ((Number) order.get("receivingOrderId")).longValue();
        Long warehouseId = ((Number) order.get("warehouseId")).longValue();
        Long supplierId = ((Number) order.get("supplierId")).longValue();

        if ("reject".equals(action)) {
            requireStatus(status, "draft");
            approvalFlowGuard.requireApprovalAccess("receiving-acceptance", "receiving-approval", null, number(order.get("receiverId")));
            int updated = jdbcTemplate.update("UPDATE receiving_order SET receiving_status = 'rejected', remark = ? WHERE receiving_no = ? AND receiving_status = 'draft'",
                    nullIfBlank(request.opinion()), receivingNo);
            requireSingleStateChange(updated);
            writeAudit("reject", receivingOrderId, receivingNo, request.opinion());
            return Map.of("receivingNo", receivingNo, "status", "rejected");
        }

        if (!"approve".equals(action)) {
            throw new IllegalArgumentException("收货验收仅支持审核入库或拒收操作");
        }
        requireStatus(status, "draft");
        approvalFlowGuard.requireApprovalAccess("receiving-acceptance", "receiving-approval", null, number(order.get("receiverId")));
        approveReceiving(receivingOrderId, receivingNo, warehouseId, supplierId, order.get("purchaseOrderId"));
        int updated = jdbcTemplate.update("""
                UPDATE receiving_order
                   SET receiving_status = 'approved', receive_time = NOW(), remark = COALESCE(?, remark)
                 WHERE receiving_no = ? AND receiving_status = 'draft'
                """, nullIfBlank(request.opinion()), receivingNo);
        requireSingleStateChange(updated);
        settlementPointService.generateForReceivingOrder(receivingOrderId);
        writeAudit("approve", receivingOrderId, receivingNo, "验收审核通过，按最新医院目录采购价生成系统批次");
        return Map.of("receivingNo", receivingNo, "status", "approved");
    }

    public Map<String, Object> options() {
        List<Map<String, Object>> purchaseOrders = jdbcTemplate.queryForList("""
                SELECT po.order_no AS orderNo, s.supplier_name AS supplierName, po.order_status AS orderStatus
                  FROM purchase_order po
                  JOIN supplier s ON s.supplier_id = po.supplier_id
                 WHERE po.order_status IN ('approved', 'sent')
                   AND EXISTS (
                       SELECT 1 FROM purchase_order_item poi
                        WHERE poi.purchase_order_id = po.purchase_order_id
                          AND poi.received_quantity < poi.quantity
                   )
                 ORDER BY po.create_time DESC
                 LIMIT 100
                """);
        List<Map<String, Object>> warehouses = jdbcTemplate.queryForList("""
                SELECT warehouse_name AS warehouseName
                  FROM warehouse
                 WHERE deleted = 0 AND status = 1
                 ORDER BY warehouse_id DESC
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
        List<Map<String, Object>> suppliers = jdbcTemplate.queryForList("""
                SELECT supplier_name AS supplierName
                  FROM supplier
                 WHERE deleted = 0 AND status = 1
                 ORDER BY supplier_name
                """);
        return Map.of("purchaseOrders", purchaseOrders, "warehouses", warehouses, "products", products, "suppliers", suppliers);
    }

    public Map<String, Object> purchaseOrderItems(String orderNo) {
        Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT po.purchase_order_id AS orderId, po.order_no AS orderNo, s.supplier_name AS supplierName
                  FROM purchase_order po
                  JOIN supplier s ON s.supplier_id = po.supplier_id
                 WHERE po.order_no = ?
                """, orderNo);
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                       poi.quantity - poi.received_quantity AS pendingQuantity,
                       poi.unit, poi.estimated_unit_price AS estimatedUnitPrice,
                       p.purchase_price AS latestCatalogPrice
                  FROM purchase_order_item poi
                  JOIN product p ON p.product_id = poi.product_id
                 WHERE poi.purchase_order_id = ?
                   AND poi.quantity > poi.received_quantity
                 ORDER BY poi.item_id
                """, ((Number) order.get("orderId")).longValue());
        return Map.of("order", order, "items", items);
    }

    // ===== 私有辅助方法 =====

    private void approveReceiving(Long receivingOrderId, String receivingNo, Long warehouseId, Long supplierId, Object purchaseOrderIdObject) {
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT roi.item_id AS itemId, roi.product_id AS productId, roi.production_batch_no AS productionBatchNo,
                       roi.udi_code AS udiCode,
                       roi.production_date AS productionDate, roi.expire_date AS expireDate,
                       roi.quantity, roi.qualified_quantity AS qualifiedQuantity,
                       p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                       p.is_high_value AS highValue, p.is_quota_managed AS quotaManaged, m.manufacturer_name AS manufacturerName,
                       s.supplier_name AS supplierName, p.purchase_price AS latestCatalogPrice,
                       roi.unit_price AS previewUnitPrice
                  FROM receiving_order_item roi
                  JOIN product p ON p.product_id = roi.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = ?
                 WHERE roi.receiving_order_id = ?
                """, supplierId, receivingOrderId);

        validateHighValueUdisBeforeInventoryMutation(items);
        validatePurchaseRemainingBeforeInventoryMutation(purchaseOrderIdObject, items);

        for (Map<String, Object> item : items) {
            Long itemId = ((Number) item.get("itemId")).longValue();
            Long productId = ((Number) item.get("productId")).longValue();
            BigDecimal qualifiedQty = (BigDecimal) item.get("qualifiedQuantity");
            int highValueUnitCount = isHighValue(item) ? exactHighValueUnitCount(qualifiedQty) : 0;
            BigDecimal latestPrice = (BigDecimal) item.get("latestCatalogPrice");
            BigDecimal previewPrice = (BigDecimal) item.get("previewUnitPrice");
            jdbcTemplate.update("UPDATE receiving_order_item SET unit_price = ?, amount = qualified_quantity * ? WHERE item_id = ?",
                    latestPrice, latestPrice, itemId);
            CreatedInventoryBatch batch = createInventoryBatch(receivingOrderId, itemId, productId, supplierId, item, latestPrice);
            applyInventoryBalance(warehouseId, productId, batch.batchId(), qualifiedQty, receivingOrderId);
            createHighValueTraceCodes(receivingNo, itemId, warehouseId, item, batch, highValueUnitCount);
            if (previewPrice != null && previewPrice.compareTo(latestPrice) != 0) {
                writePriceDiffAudit(receivingOrderId, receivingNo, productId, previewPrice, latestPrice);
            }
            purchaseFulfillmentService.recordAcceptedReceipt(purchaseOrderIdObject, productId, qualifiedQty);
        }
    }

    private void validatePurchaseRemainingBeforeInventoryMutation(Object purchaseOrderIdObject,
                                                                   List<Map<String, Object>> receivingItems) {
        if (!(purchaseOrderIdObject instanceof Number purchaseOrderId)) {
            return;
        }
        Map<Long, BigDecimal> receivingByProduct = new java.util.LinkedHashMap<>();
        for (Map<String, Object> item : receivingItems) {
            Long productId = ((Number) item.get("productId")).longValue();
            BigDecimal qualified = (BigDecimal) item.get("qualifiedQuantity");
            receivingByProduct.merge(productId, qualified, BigDecimal::add);
        }
        List<Map<String, Object>> purchaseItems = jdbcTemplate.queryForList("""
                SELECT product_id AS productId,
                       SUM(quantity - received_quantity) AS remainingQuantity
                  FROM purchase_order_item
                 WHERE purchase_order_id = ?
                 GROUP BY product_id
                 FOR UPDATE
                """, purchaseOrderId.longValue());
        Map<Long, BigDecimal> remainingByProduct = new java.util.HashMap<>();
        for (Map<String, Object> purchaseItem : purchaseItems) {
            remainingByProduct.put(((Number) purchaseItem.get("productId")).longValue(),
                    (BigDecimal) purchaseItem.get("remainingQuantity"));
        }
        for (Map.Entry<Long, BigDecimal> entry : receivingByProduct.entrySet()) {
            BigDecimal remaining = remainingByProduct.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(remaining) > 0) {
                throw new IllegalStateException("采购订单剩余可收数量已变化，本次验收未写入库存，请刷新后重试");
            }
        }
    }

    /**
     * High-value UDI validation must finish before creating batches or balances so an invalid scan cannot
     * leave partial inventory behind even when the database rejects a later trace-code insert.
     */
    private void validateHighValueUdisBeforeInventoryMutation(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            if (!isHighValue(item)) {
                continue;
            }
            int unitCount = exactHighValueUnitCount((BigDecimal) item.get("qualifiedQuantity"));
            String udiCode = nullIfBlank(item.get("udiCode") instanceof String value ? value : null);
            if (udiCode == null) {
                continue;
            }
            if (unitCount != 1) {
                throw new IllegalArgumentException("每件高值耗材必须使用独立 UDI；录入 UDI 时合格数量只能为 1");
            }
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM udi_trace_code WHERE udi_code = ?", Integer.class, udiCode);
            if (existing != null && existing > 0) {
                throw new IllegalArgumentException("高值耗材 UDI 已存在，请勿重复验收入库");
            }
        }
    }

    private CreatedInventoryBatch createInventoryBatch(Long receivingOrderId, Long itemId, Long productId, Long supplierId,
                                                       Map<String, Object> item, BigDecimal latestPrice) {
        String systemBatchNo = support.nextNo(INVENTORY_BATCH);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO inventory_batch (
                      system_batch_no, receiving_order_id, receiving_item_id, product_id, supplier_id,
                      production_batch_no, production_date, expire_date, batch_unit_price,
                      ownership_type, settlement_mode, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'hospital_owned', ?, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, systemBatchNo);
            ps.setLong(2, receivingOrderId);
            ps.setLong(3, itemId);
            ps.setLong(4, productId);
            ps.setLong(5, supplierId);
            ps.setString(6, (String) item.get("productionBatchNo"));
            ps.setDate(7, (Date) item.get("productionDate"));
            ps.setDate(8, (Date) item.get("expireDate"));
            ps.setBigDecimal(9, latestPrice);
            ps.setString(10, settlementPoint(item));
            return ps;
        }, keyHolder);
        return new CreatedInventoryBatch(Objects.requireNonNull(keyHolder.getKey()).longValue(), systemBatchNo);
    }

    private static String settlementPoint(Map<String, Object> item) {
        if (isHighValue(item)) {
            return SettlementPointService.ACTUAL_SALE;
        }
        Object quotaManaged = item.get("quotaManaged");
        if (quotaManaged instanceof Number number && number.intValue() == 1) {
            return SettlementPointService.DEPARTMENT_CONSUMPTION;
        }
        return SettlementPointService.PURCHASE_IN;
    }
    private void createHighValueTraceCodes(String receivingNo, Long receivingItemId, Long warehouseId,
                                           Map<String, Object> item, CreatedInventoryBatch batch, int unitCount) {
        if (unitCount == 0) {
            return;
        }
        String operatorName = operatorContextProvider.current().username();
        // UDI 取验收录入的 UDI 字段（独立于唯一码）；未录入时留空，唯一码由系统自动生成
        String receivingUdi = nullIfBlank(item.get("udiCode") instanceof String value ? value : null);
        for (int index = 0; index < unitCount; index++) {
            String uniqueCode = support.nextNo(HIGH_VALUE_UNIQUE_CODE);
            jdbcTemplate.update("""
                    INSERT INTO udi_trace_code (
                      udi_code, unique_code, trace_scope, product_code, product_name, spec_model,
                      manufacturer_name, supplier_name, batch_no, expire_date, current_status,
                      responsible_person, risk_level, last_event_name, last_event_time
                    ) VALUES (?, ?, 'high_value', ?, ?, ?, ?, ?, ?, ?, 'in_stock', ?, 'normal', '中心库验收入库', NOW())
                    """, receivingUdi, uniqueCode, item.get("productCode"), item.get("productName"), item.get("specModel"),
                    item.get("manufacturerName"), item.get("supplierName"), batch.systemBatchNo(), item.get("expireDate"),
                    operatorName);
            Long traceCodeId = jdbcTemplate.queryForObject(
                    "SELECT trace_code_id FROM udi_trace_code WHERE unique_code = ?",
                    Long.class, uniqueCode);
            jdbcTemplate.update("""
                    INSERT INTO inventory_batch_trace_code (batch_id, trace_code_id, receiving_item_id, current_warehouse_id)
                    VALUES (?, ?, ?, ?)
                    """, batch.batchId(), traceCodeId, receivingItemId, warehouseId);
            jdbcTemplate.update("""
                    INSERT INTO udi_trace_event (
                      trace_code_id, event_no, event_type, event_name, biz_no, operator_name,
                      event_time, status, remark, sort_order
                    ) VALUES (?, ?, 'center_inbound', '中心库验收入库', ?, ?, NOW(), 'done',
                              '高值耗材验收合格入库自动生成唯一码', 20)
                    """, traceCodeId, support.nextNo(UDI_TRACE_EVENT), receivingNo, operatorName);
        }
    }

    private static boolean isHighValue(Map<String, Object> item) {
        Object value = item.get("highValue");
        return value instanceof Boolean bool ? bool : value instanceof Number number && number.intValue() == 1;
    }

    private static int exactHighValueUnitCount(BigDecimal qualifiedQuantity) {
        if (qualifiedQuantity == null) {
            throw new IllegalArgumentException("高值耗材验收合格数量不能为空");
        }
        try {
            int count = qualifiedQuantity.intValueExact();
            if (count < 0) {
                throw new IllegalArgumentException("高值耗材验收合格数量不能小于零");
            }
            return count;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("高值耗材验收合格数量必须为整数");
        }
    }

    private record CreatedInventoryBatch(Long batchId, String systemBatchNo) {
    }

    private Long applyInventoryBalance(Long warehouseId, Long productId, Long batchId, BigDecimal qualifiedQty, Long receivingOrderId) {
        if (qualifiedQty == null) {
            throw new IllegalArgumentException("qualified receiving quantity is required");
        }
        return support.receiveAvailable(warehouseId, productId, batchId, qualifiedQty,
                "purchase_receive_in", "receiving_order", receivingOrderId,
                "receiving approved and stock-in completed");
    }

    private void insertReceivingItems(Long receivingOrderId, List<ReceivingItemRequest> items) {
        for (ReceivingItemRequest item : items) {
            List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                    SELECT product_id AS productId, purchase_price AS purchasePrice
                      FROM product
                     WHERE product_code = ? AND deleted = 0 AND status = 1
                    """, item.productCode());
            if (products.isEmpty()) {
                throw new IllegalArgumentException("product code " + item.productCode() + " does not exist or is disabled");
            }
            Map<String, Object> product = products.get(0);
            Long productId = ((Number) product.get("productId")).longValue();
            BigDecimal quantity = defaultDecimal(item.quantity(), BigDecimal.ZERO);
            BigDecimal qualified = item.qualifiedQuantity() == null ? quantity : item.qualifiedQuantity();
            BigDecimal unqualified = item.unqualifiedQuantity() == null ? BigDecimal.ZERO : item.unqualifiedQuantity();
            BigDecimal latestPrice = (BigDecimal) product.get("purchasePrice");
            jdbcTemplate.update("""
                    INSERT INTO receiving_order_item (
                      receiving_order_id, product_id, production_batch_no, udi_code, production_date, expire_date,
                      quantity, unit_price, amount, qualified_quantity, unqualified_quantity
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, receivingOrderId, productId, nullIfBlank(item.productionBatchNo()),
                    nullIfBlank(item.udiCode()),
                    parseDate(item.productionDate()), parseDate(item.expireDate()), quantity,
                    latestPrice, qualified.multiply(latestPrice), qualified, unqualified);
        }
    }

    private Long findPurchaseOrderId(String purchaseOrderNo) {
        if (isBlank(purchaseOrderNo)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT purchase_order_id FROM purchase_order WHERE order_no = ? LIMIT 1",
                Long.class,
                purchaseOrderNo.trim()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void validatePurchaseOrderItems(Long purchaseOrderId, List<ReceivingItemRequest> items) {
        if (purchaseOrderId == null) {
            return;
        }
        for (ReceivingItemRequest item : items) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT po.order_status AS orderStatus,
                           poi.quantity - poi.received_quantity AS remainingQuantity
                      FROM purchase_order po
                      JOIN purchase_order_item poi ON poi.purchase_order_id = po.purchase_order_id
                      JOIN product p ON p.product_id = poi.product_id
                     WHERE po.purchase_order_id = ? AND p.product_code = ?
                     FOR UPDATE
                    """, purchaseOrderId, item.productCode());
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("receiving product is not included in purchase order");
            }
            Map<String, Object> row = rows.get(0);
            String status = String.valueOf(row.get("orderStatus"));
            if (!"approved".equals(status) && !"sent".equals(status)) {
                throw new IllegalArgumentException("purchase order status does not allow receiving");
            }
            BigDecimal qualified = item.qualifiedQuantity() == null ? item.quantity() : item.qualifiedQuantity();
            BigDecimal remaining = (BigDecimal) row.get("remainingQuantity");
            if (remaining == null || qualified.compareTo(remaining) > 0) {
                throw new IllegalArgumentException("qualified receiving quantity exceeds purchase order remaining quantity");
            }
        }
    }

    private Long findSupplierId(String supplierName, Long purchaseOrderId) {
        if (purchaseOrderId != null) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT supplier_id FROM purchase_order WHERE purchase_order_id = ?",
                    Long.class, purchaseOrderId
            );
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("purchase order does not exist");
            }
            return ids.get(0);
        }
        if (isBlank(supplierName)) {
            throw new IllegalArgumentException("supplier is required");
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT supplier_id FROM supplier WHERE supplier_name = ? AND deleted = 0 AND status = 1 LIMIT 1",
                Long.class,
                supplierName.trim()
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("供应商不存在或已停用");
        }
        return ids.get(0);
    }

    private Long findWarehouseId(String warehouseName) {
        if (isBlank(warehouseName)) {
            throw new IllegalArgumentException("warehouse is required");
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT warehouse_id FROM warehouse WHERE warehouse_name = ? AND deleted = 0 AND status = 1 LIMIT 1",
                Long.class,
                warehouseName.trim()
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("warehouse does not exist or is disabled");
        }
        return ids.get(0);
    }

    private void writeAudit(String operationType, Long receivingOrderId, String receivingNo, String remark) {
        OperatorContext operator = operatorContextProvider.current();
        jdbcTemplate.update("""
                INSERT INTO audit_log (operator_name, operation_type, biz_type, biz_id, after_data, ip_address, remark)
                VALUES (?, ?, 'receiving_order', ?, JSON_OBJECT('receivingNo', ?), ?, ?)
                """, operator.username(), operationType, receivingOrderId, receivingNo, operator.ipAddress(), remark);
    }

    private void writePriceDiffAudit(Long receivingOrderId, String receivingNo, Long productId, BigDecimal previewPrice, BigDecimal latestPrice) {
        OperatorContext operator = operatorContextProvider.current();
        jdbcTemplate.update("""
                INSERT INTO audit_log (operator_name, operation_type, biz_type, biz_id, after_data, ip_address, remark)
                VALUES (?, 'price_diff_notice', 'receiving_order', ?,
                        JSON_OBJECT('receivingNo', ?, 'productId', ?, 'orderPreviewPrice', ?, 'latestCatalogPrice', ?),
                        ?, 'receiving price difference notice')
                """, operator.username(), receivingOrderId, receivingNo, productId, previewPrice, latestPrice, operator.ipAddress());
    }

    private static void validateRequest(ReceivingOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("receiving order requires at least one item");
        }
        for (ReceivingItemRequest item : request.items()) {
            if (isBlank(item.productCode()) || item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("product code and receiving quantity are required");
            }
            BigDecimal qualified = item.qualifiedQuantity() == null ? item.quantity() : item.qualifiedQuantity();
            BigDecimal unqualified = item.unqualifiedQuantity() == null ? BigDecimal.ZERO : item.unqualifiedQuantity();
            if (qualified.compareTo(BigDecimal.ZERO) < 0 || unqualified.compareTo(BigDecimal.ZERO) < 0
                    || qualified.add(unqualified).compareTo(item.quantity()) != 0) {
                throw new IllegalArgumentException("qualified and unqualified quantities must equal receiving quantity");
            }
        }
    }

    private static void requireSingleStateChange(int updated) {
        if (updated != 1) {
            throw new IllegalArgumentException("receiving order has already been processed");
        }
    }

    private static void requireStatus(String currentStatus, String requiredStatus) {
        if (!requiredStatus.equals(currentStatus)) {
            throw new IllegalArgumentException("当前收货单状态不允许执行该操作");
        }
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
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

    private static BigDecimal defaultDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

}
