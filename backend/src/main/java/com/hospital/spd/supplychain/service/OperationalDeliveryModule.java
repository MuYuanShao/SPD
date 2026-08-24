package com.hospital.spd.supplychain.service;
import com.hospital.spd.specialty.service.HighValueTraceFlowService;

import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.common.OperatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hospital.spd.common.SqlHelper.nullIfBlank;
import static com.hospital.spd.common.service.DocumentKind.DELIVERY_ORDER;
import static com.hospital.spd.common.service.DocumentKind.QUOTA_PACKAGE_EVENT;

/**
 * Owns delivery creation and signing inside Operational Closure.
 */
@Service
public class OperationalDeliveryModule {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final HighValueTraceFlowService traceFlowService;

    public OperationalDeliveryModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support, new HighValueTraceFlowService(jdbcTemplate, support, OperatorContext::system));
    }

    @Autowired
    public OperationalDeliveryModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                                     HighValueTraceFlowService traceFlowService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.traceFlowService = traceFlowService;
    }

    @Transactional
    public Map<String, Object> createDelivery(Map<String, Object> body) {
        String requisitionNo = text(body, "requisitionNo", "");
        String deptName = requireText(body, "deptName");
        String warehouseName = requireText(body, "warehouseName");
        Map<String, Object> product = findProduct(requireText(body, "productCode"));
        BigDecimal quantity = decimal(body, "quantity", null);
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        List<HighValueTraceFlowService.TraceUnit> traceUnits = List.of();
        if (product.get("highValue") instanceof Number highValue && highValue.intValue() == 1) {
            Object rawCodes = body.get("uniqueCodes") == null ? body.get("uniqueCode") : body.get("uniqueCodes");
            traceUnits = traceFlowService.requireUnits(rawCodes, quantity,
                    ((Number) product.get("productId")).longValue(), findWarehouseId(warehouseName),
                    List.of("in_stock", "requisitioned"));
        }
        String deliveryNo = support.nextNo(DELIVERY_ORDER);
        jdbcTemplate.update("""
                INSERT INTO spd_delivery_order (
                  delivery_no, requisition_no, dept_name, warehouse_name, product_code, product_name,
                  quantity, status, delivery_type
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'picked', 'unique_code')
                """, deliveryNo, nullIfBlank(requisitionNo), deptName, warehouseName, product.get("productCode"),
                product.get("productName"), quantity);
        if (!traceUnits.isEmpty()) {
            Long deliveryId = jdbcTemplate.queryForObject(
                    "SELECT delivery_id FROM spd_delivery_order WHERE delivery_no = ?", Long.class, deliveryNo);
            traceFlowService.bindDelivery(deliveryId, traceUnits, deliveryNo, deptName);
            if (!requisitionNo.isBlank()) {
                Long requisitionId = jdbcTemplate.queryForObject(
                        "SELECT requisition_id FROM department_requisition WHERE requisition_no = ?",
                        Long.class, requisitionNo);
                updateRequisitionPickStatus(requisitionId);
            }
        }
        return Map.of("deliveryNo", deliveryNo, "status", "picked");
    }

    /**
     * 拣配唯一码货源：展示申请明细绑定的唯一码/UDI（配对申请单申请的商品明细类型），
     * 仅列出未配送的在库/已申领唯一码。
     */
    public Map<String, Object> availableUniqueCodes(Map<String, String> params) {
        Long itemId = longValue(params.get("itemId"));
        if (itemId == null) {
            return Map.of("rows", List.of());
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT utc.trace_code_id AS traceCodeId, utc.unique_code AS uniqueCode,
                       COALESCE(utc.udi_code, '-') AS udiCode, ib.system_batch_no AS batchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate
                  FROM department_requisition_trace_code rt
                  JOIN udi_trace_code utc ON utc.trace_code_id = rt.trace_code_id
                  LEFT JOIN inventory_batch_trace_code ibtc ON ibtc.trace_code_id = utc.trace_code_id
                  LEFT JOIN inventory_batch ib ON ib.batch_id = ibtc.batch_id
                 WHERE rt.requisition_item_id = ?
                   AND utc.current_status IN ('in_stock', 'requisitioned')
                   AND NOT EXISTS (
                     SELECT 1 FROM spd_delivery_trace_code dt WHERE dt.trace_code_id = rt.trace_code_id
                   )
                 ORDER BY rt.trace_code_id
                """, itemId);
        return Map.of("rows", rows);
    }

    /**
     * 拣配散货货源：一级库（中心库）中该申请商品的无货位可用余额，按批次展示。
     */
    public Map<String, Object> availableLooseStock(Map<String, String> params) {
        Long itemId = longValue(params.get("itemId"));
        String warehouseName = params.getOrDefault("warehouseName", "").trim();
        if (itemId == null) {
            return Map.of("rows", List.of());
        }
        List<Object> args = new ArrayList<>();
        args.add(itemId);
        StringBuilder where = new StringBuilder("""
                 WHERE dri.item_id = ?
                   AND (w.warehouse_type LIKE '%一级%' OR w.warehouse_type LIKE '%中心%')
                """);
        if (!warehouseName.isBlank()) {
            where.append(" AND w.warehouse_name = ?");
            args.add(warehouseName);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT bal.balance_id AS balanceId, ib.batch_id AS batchId,
                       ib.system_batch_no AS systemBatchNo,
                       ib.production_batch_no AS productionBatchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       ib.batch_unit_price AS unitPrice, bal.available_qty AS availableQty
                  FROM department_requisition_item dri
                  JOIN inventory_balance bal ON bal.product_id = dri.product_id
                   AND bal.location_id IS NULL AND bal.available_qty > 0
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                """ + where + " ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id",
                args.toArray());
        return Map.of("rows", rows);
    }

    /**
     * 散货拣配确认：按申请明细从一级库散货中扣减库存并生成配送单，
     * 配送单记录唯一单据号与关联申请明细。
     */
    @Transactional
    public Map<String, Object> confirmLoosePicking(Map<String, Object> body) {
        String requisitionNo = text(body, "requisitionNo", "");
        Long itemId = longValue(body.get("itemId"));
        String warehouseName = text(body, "warehouseName", "");
        BigDecimal quantity = decimal(body, "quantity", null);
        if (requisitionNo.isBlank() || itemId == null || warehouseName.isBlank()
                || quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("requisitionNo, itemId, warehouseName and quantity are required");
        }
        Map<String, Object> requisition = findRequisitionItemForPicking(requisitionNo, itemId);
        Long requisitionId = ((Number) requisition.get("requisitionId")).longValue();
        Long productId = ((Number) requisition.get("productId")).longValue();
        String deptName = String.valueOf(requisition.get("deptName"));
        BigDecimal requested = (BigDecimal) requisition.get("quantity");
        Long warehouseId = findWarehouseId(warehouseName);
        BigDecimal picked = pickedQuantity(itemId);
        if (picked.add(quantity).compareTo(requested) > 0) {
            throw new IllegalArgumentException("picked quantity exceeds requisition item quantity");
        }
        Map<String, Object> product = findProductByProductId(productId);
        String deliveryNo = support.nextNo(DELIVERY_ORDER);
        Long deliveryId = insertLoosePickedDelivery(deliveryNo, requisitionNo, itemId, deptName, warehouseName,
                String.valueOf(product.get("productCode")), String.valueOf(product.get("productName")), quantity);
        support.consumeAvailableFifo(warehouseId, productId, quantity,
                "delivery_loose_out", "spd_delivery_order", deliveryId,
                "picked loose stock for requisition " + requisitionNo + ", delivery " + deliveryNo);
        updateRequisitionPickStatus(requisitionId);
        support.writeAudit("delivery", "confirm_loose_picking", deliveryId, deliveryNo,
                "picked loose stock for requisition " + requisitionNo);
        return Map.of("deliveryNo", deliveryNo, "status", "picked", "quantity", quantity);
    }

    public Map<String, Object> pickingRequisitions() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT dr.requisition_no AS requisitionNo,
                       dr.requisition_id AS requisitionId,
                       sd.dept_name AS deptName,
                       p.product_code AS productCode,
                       p.product_name AS productName,
                       dri.item_id AS itemId,
                       dri.quantity AS requisitionQty,
                       COALESCE(dri.item_type, CASE WHEN p.is_high_value = 1 THEN 'unique_code'
                                                    WHEN p.is_quota_managed = 1 THEN 'quota_package'
                                                    ELSE 'loose' END) AS itemType,
                       COALESCE(picked.picked_qty, 0) AS pickedQty,
                       GREATEST(dri.quantity - COALESCE(picked.picked_qty, 0), 0) AS remainingQty,
                       dr.status,
                       DATE_FORMAT(dr.apply_time, '%Y-%m-%d %H:%i') AS applyTime
                  FROM department_requisition dr
                  JOIN sys_dept sd ON sd.dept_id = dr.dept_id
                  JOIN department_requisition_item dri ON dri.requisition_id = dr.requisition_id
                  JOIN product p ON p.product_id = dri.product_id
                  LEFT JOIN (
                    SELECT requisition_item_id, SUM(picked_qty) AS picked_qty
                      FROM (
                        SELECT requisition_item_id, COUNT(*) AS picked_qty
                          FROM spd_delivery_package_binding
                         GROUP BY requisition_item_id
                        UNION ALL
                        SELECT rt.requisition_item_id, COUNT(*) AS picked_qty
                          FROM department_requisition_trace_code rt
                          JOIN spd_delivery_trace_code dt ON dt.trace_code_id = rt.trace_code_id
                         GROUP BY rt.requisition_item_id
                        UNION ALL
                        SELECT requisition_item_id, SUM(quantity) AS picked_qty
                          FROM spd_delivery_order
                         WHERE delivery_type = 'loose' AND requisition_item_id IS NOT NULL
                         GROUP BY requisition_item_id
                      ) picked_sources
                     GROUP BY requisition_item_id
                  ) picked ON picked.requisition_item_id = dri.item_id
                 WHERE dr.status IN ('approved', 'partial_picked')
                 HAVING remainingQty > 0
                 ORDER BY dr.apply_time DESC, dr.requisition_id DESC
                 LIMIT 100
                """);
        return Map.of("rows", rows);
    }

    public Map<String, Object> availablePackageLabels(Map<String, String> params) {
        String requisitionNo = params.getOrDefault("requisitionNo", "").trim();
        Long itemId = longValue(params.get("itemId"));
        String warehouseName = params.getOrDefault("warehouseName", "").trim();
        if (requisitionNo.isBlank() || itemId == null) {
            return Map.of("rows", List.of());
        }
        List<Object> args = new ArrayList<>();
        args.add(requisitionNo);
        args.add(itemId);
        StringBuilder where = new StringBuilder("""
                 WHERE dr.requisition_no = ?
                   AND dri.item_id = ?
                   AND qpl.status = 'available'
                   AND qpl.product_id = dri.product_id
                   AND NOT EXISTS (
                     SELECT 1 FROM spd_delivery_package_binding b WHERE b.label_id = qpl.label_id
                   )
                   AND (w.warehouse_type LIKE '%一级%' OR w.warehouse_type LIKE '%中心%')
                """);
        if (!warehouseName.isBlank()) {
            where.append(" AND w.warehouse_name = ?");
            args.add(warehouseName);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT qpl.label_no AS labelNo,
                       w.warehouse_name AS warehouseName,
                       w.warehouse_type AS warehouseType,
                       p.product_code AS productCode,
                       p.product_name AS productName,
                       qpl.package_quantity AS packageQuantity,
                       DATE_FORMAT(qpl.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM department_requisition dr
                  JOIN department_requisition_item dri ON dri.requisition_id = dr.requisition_id
                  JOIN quota_package_label qpl ON qpl.product_id = dri.product_id
                  JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
                  JOIN product p ON p.product_id = qpl.product_id
                """ + where + " ORDER BY qpl.create_time, qpl.label_id LIMIT 200", args.toArray());
        return Map.of("rows", rows);
    }

    /**
     * 定数包明细：标签基础信息、来源批次、绑定去向与事件流水，
     * 供拣配配送页面点击定数包时查看明细（而非汇总数量）。
     */
    public Map<String, Object> packageLabelDetail(String labelNo) {
        List<Map<String, Object>> labels = jdbcTemplate.queryForList("""
                SELECT qpl.label_id AS labelId, qpl.label_no AS labelNo, qpl.status,
                       qpl.package_quantity AS packageQuantity, qpl.print_count AS printCount,
                       p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, p.unit,
                       qpt.template_code AS templateCode, qpt.template_name AS templateName,
                       w.warehouse_name AS warehouseName, w.warehouse_type AS warehouseType,
                       DATE_FORMAT(qpl.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM quota_package_label qpl
                  JOIN product p ON p.product_id = qpl.product_id
                  LEFT JOIN quota_package_template qpt ON qpt.template_id = qpl.template_id
                  LEFT JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
                 WHERE qpl.label_no = ?
                 LIMIT 1
                """, labelNo.trim());
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("定数包标签不存在");
        }
        Map<String, Object> detail = labels.get(0);
        Long labelId = ((Number) detail.get("labelId")).longValue();
        List<Map<String, Object>> sources = jdbcTemplate.queryForList("""
                SELECT s.batch_id AS batchId, ib.system_batch_no AS systemBatchNo,
                       ib.production_batch_no AS productionBatchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       s.source_qty AS sourceQty, s.unit_price AS unitPrice
                  FROM quota_package_label_source s
                  LEFT JOIN inventory_batch ib ON ib.batch_id = s.batch_id
                 WHERE s.label_id = ?
                 ORDER BY s.source_id
                """, labelId);
        List<Map<String, Object>> bindings = jdbcTemplate.queryForList("""
                SELECT d.delivery_no AS deliveryNo, d.requisition_no AS requisitionNo,
                       b.package_quantity AS packageQuantity,
                       DATE_FORMAT(d.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM spd_delivery_package_binding b
                  JOIN spd_delivery_order d ON d.delivery_id = b.delivery_id
                 WHERE b.label_id = ?
                 ORDER BY b.binding_id DESC
                """, labelId);
        List<Map<String, Object>> events = jdbcTemplate.queryForList("""
                SELECT event_no AS eventNo, event_type AS eventType,
                       status_before AS statusBefore, status_after AS statusAfter,
                       qty_change AS qtyChange, remark,
                       DATE_FORMAT(event_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM quota_package_event
                 WHERE label_id = ?
                 ORDER BY event_id DESC
                 LIMIT 20
                """, labelId);
        detail.put("sources", sources);
        detail.put("bindings", bindings);
        detail.put("events", events);
        return detail;
    }

    @Transactional
    public Map<String, Object> confirmPicking(Map<String, Object> body) {
        String requisitionNo = text(body, "requisitionNo", "");
        Long itemId = longValue(body.get("itemId"));
        String warehouseName = text(body, "warehouseName", "");
        List<String> labelNos = stringList(body.get("labelNos"));
        if (requisitionNo.isBlank() || itemId == null || warehouseName.isBlank() || labelNos.isEmpty()) {
            throw new IllegalArgumentException("requisitionNo, itemId, warehouseName and labelNos are required");
        }
        Map<String, Object> requisition = findRequisitionItemForPicking(requisitionNo, itemId);
        Long requisitionId = ((Number) requisition.get("requisitionId")).longValue();
        Long productId = ((Number) requisition.get("productId")).longValue();
        String deptName = String.valueOf(requisition.get("deptName"));
        BigDecimal requested = (BigDecimal) requisition.get("quantity");
        Long warehouseId = findWarehouseId(warehouseName);
        List<Map<String, Object>> labels = findLabelsForUpdate(labelNos);
        validateLabelSet(labels, labelNos, warehouseId);
        BigDecimal selectedPackageCount = BigDecimal.valueOf(labels.size());
        for (Map<String, Object> label : labels) {
            Long labelProductId = ((Number) label.get("productId")).longValue();
            if (!labelProductId.equals(productId)) {
                throw new IllegalArgumentException("selected package label product does not match requisition item");
            }
        }
        BigDecimal picked = pickedQuantity(itemId);
        if (picked.add(selectedPackageCount).compareTo(requested) > 0) {
            throw new IllegalArgumentException("picked package quantity exceeds requisition item quantity");
        }

        String deliveryNo = support.nextNo(DELIVERY_ORDER);
        Map<String, Object> firstLabel = labels.get(0);
        BigDecimal totalQuantity = labels.stream()
                .map(label -> (BigDecimal) label.get("packageQuantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String productCode = labels.size() == 1 ? String.valueOf(firstLabel.get("productCode")) : "MULTI";
        String productName = labels.size() == 1 ? String.valueOf(firstLabel.get("productName")) : "定数包组合";
        Long deliveryId = insertPickedDelivery(deliveryNo, requisitionNo, itemId, deptName, warehouseName,
                productCode, productName, totalQuantity);

        for (Map<String, Object> label : labels) {
            Long labelId = ((Number) label.get("labelId")).longValue();
            BigDecimal packageQuantity = (BigDecimal) label.get("packageQuantity");
            jdbcTemplate.update("""
                    INSERT INTO spd_delivery_package_binding (
                      delivery_id, requisition_id, requisition_item_id, label_id, product_id, package_quantity
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """, deliveryId, requisitionId, itemId, labelId, productId, packageQuantity);
            jdbcTemplate.update("""
                    UPDATE quota_package_label
                       SET status = 'delivered', version = version + 1
                     WHERE label_id = ? AND status = 'available'
                    """, labelId);
            writePackageEvent(labelId, "delivery_out", "available", "delivered",
                    packageQuantity.negate(), "picked for requisition " + requisitionNo + ", delivery " + deliveryNo);
        }
        updateRequisitionPickStatus(requisitionId);
        support.writeAudit("delivery", "confirm_picking", deliveryId, deliveryNo,
                "picked quota packages for requisition " + requisitionNo);
        return Map.of("deliveryNo", deliveryNo, "status", "picked", "labelCount", labels.size(), "quantity", totalQuantity);
    }

    @Transactional
    public Map<String, Object> signDelivery(String deliveryNo) {
        Map<String, Object> delivery = jdbcTemplate.queryForMap("""
                SELECT delivery_id AS deliveryId, delivery_no AS deliveryNo, warehouse_name AS warehouseName,
                       dept_name AS deptName, product_code AS productCode, quantity, status,
                       destination_warehouse_id AS destinationWarehouseId
                  FROM spd_delivery_order WHERE delivery_no = ?
                 FOR UPDATE
                """, deliveryNo);
        String deliveryStatus = String.valueOf(delivery.get("status"));
        Long deliveryId = ((Number) delivery.get("deliveryId")).longValue();
        if ("signed".equals(deliveryStatus)
                && delivery.get("destinationWarehouseId") instanceof Number destinationWarehouseId
                && hasDeliveredPackageBindings(deliveryId)) {
            receiveSignedPackages(deliveryId, deliveryNo, destinationWarehouseId.longValue());
            return Map.of("deliveryNo", deliveryNo, "status", "signed", "repaired", true);
        }
        if (!"picked".equals(deliveryStatus)) {
            throw new IllegalArgumentException("only picked delivery can be signed");
        }
        Long sourceWarehouseId = findWarehouseId(String.valueOf(delivery.get("warehouseName")));
        Long destinationWarehouseId = findDepartmentWarehouseId(String.valueOf(delivery.get("deptName")), sourceWarehouseId);
        Integer packageBindingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spd_delivery_package_binding WHERE delivery_id = ?", Integer.class, deliveryId);
        Integer traceBindingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spd_delivery_trace_code WHERE delivery_id = ?", Integer.class, deliveryId);
        Map<String, Object> product = findProduct(String.valueOf(delivery.get("productCode")));
        if (traceBindingCount != null && traceBindingCount > 0) {
            int signedCount = traceFlowService.signDelivery(deliveryId, deliveryNo,
                    ((Number) product.get("productId")).longValue(), sourceWarehouseId, destinationWarehouseId,
                    String.valueOf(delivery.get("deptName")), String.valueOf(delivery.get("deptName")));
            if (BigDecimal.valueOf(signedCount).compareTo((BigDecimal) delivery.get("quantity")) != 0) {
                throw new IllegalArgumentException("配送唯一码数量与配送数量不一致");
            }
        } else if (packageBindingCount != null && packageBindingCount > 0) {
            receiveSignedPackages(deliveryId, deliveryNo, destinationWarehouseId);
        } else {
            support.transferAvailableFifo(sourceWarehouseId, destinationWarehouseId,
                    ((Number) product.get("productId")).longValue(), (BigDecimal) delivery.get("quantity"),
                    "spd_delivery_order", deliveryId, "delivery sign transfers inventory to department warehouse");
        }
        int updated = jdbcTemplate.update("UPDATE spd_delivery_order SET status = 'signed', sign_time = NOW(), destination_warehouse_id = ? WHERE delivery_no = ? AND status = 'picked'",
                destinationWarehouseId, deliveryNo);
        if (updated != 1) {
            throw new IllegalArgumentException("delivery has already been processed");
        }
        return Map.of("deliveryNo", deliveryNo, "status", "signed");
    }

    private boolean hasDeliveredPackageBindings(Long deliveryId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM spd_delivery_package_binding b
                  JOIN quota_package_label qpl ON qpl.label_id = b.label_id
                 WHERE b.delivery_id = ? AND qpl.status = 'delivered'
                """, Integer.class, deliveryId);
        return count != null && count > 0;
    }
    private void receiveSignedPackages(Long deliveryId, String deliveryNo, Long destinationWarehouseId) {
        List<Map<String, Object>> sources = jdbcTemplate.queryForList("""
                SELECT b.label_id AS labelId, b.product_id AS productId,
                       s.batch_id AS batchId, s.source_qty AS sourceQty
                  FROM spd_delivery_package_binding b
                  JOIN quota_package_label_source s ON s.label_id = b.label_id
                  JOIN quota_package_label qpl ON qpl.label_id = b.label_id AND qpl.status = 'delivered'
                 WHERE b.delivery_id = ?
                 FOR UPDATE
                """, deliveryId);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("delivery package label has no source batch");
        }
        List<Long> signedLabelIds = new ArrayList<>();
        for (Map<String, Object> source : sources) {
            Long labelId = ((Number) source.get("labelId")).longValue();
            support.receiveAvailable(destinationWarehouseId,
                    ((Number) source.get("productId")).longValue(),
                    ((Number) source.get("batchId")).longValue(),
                    (BigDecimal) source.get("sourceQty"),
                    "quota_package_delivery_sign_in", "spd_delivery_order", deliveryId,
                    "signed quota package received into department warehouse");
            jdbcTemplate.update("UPDATE quota_package_label_source SET warehouse_id = ? WHERE label_id = ? AND batch_id = ?",
                    destinationWarehouseId, labelId, source.get("batchId"));
            if (!signedLabelIds.contains(labelId)) {
                signedLabelIds.add(labelId);
            }
        }
        for (Long labelId : signedLabelIds) {
            jdbcTemplate.update("UPDATE quota_package_label SET warehouse_id = ?, status = 'signed', version = version + 1 WHERE label_id = ? AND status = 'delivered'",
                    destinationWarehouseId, labelId);
            writePackageEvent(labelId, "delivery_sign", "delivered", "signed", BigDecimal.ZERO,
                    "delivery " + deliveryNo + " signed into department warehouse");
        }
    }
    private Map<String, Object> findRequisitionItemForPicking(String requisitionNo, Long itemId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT dr.requisition_id AS requisitionId, dr.status, sd.dept_name AS deptName,
                       dri.item_id AS itemId, dri.product_id AS productId, dri.quantity
                  FROM department_requisition dr
                  JOIN sys_dept sd ON sd.dept_id = dr.dept_id
                  JOIN department_requisition_item dri ON dri.requisition_id = dr.requisition_id
                 WHERE dr.requisition_no = ?
                   AND dri.item_id = ?
                   AND dr.status IN ('approved', 'partial_picked')
                 LIMIT 1
                 FOR UPDATE
                """, requisitionNo, itemId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("requisition item is not approved or has been fully picked");
        }
        return rows.get(0);
    }

    private BigDecimal pickedQuantity(Long itemId) {
        BigDecimal picked = jdbcTemplate.queryForObject("""
                SELECT (
                    SELECT COUNT(*)
                      FROM spd_delivery_package_binding
                     WHERE requisition_item_id = ?
                  ) + (
                    SELECT COUNT(*)
                      FROM department_requisition_trace_code rt
                      JOIN spd_delivery_trace_code dt ON dt.trace_code_id = rt.trace_code_id
                     WHERE rt.requisition_item_id = ?
                  ) + (
                    SELECT COALESCE(SUM(quantity), 0)
                      FROM spd_delivery_order
                     WHERE requisition_item_id = ? AND delivery_type = 'loose'
                  )
                """, BigDecimal.class, itemId, itemId, itemId);
        return picked == null ? BigDecimal.ZERO : picked;
    }

    private List<Map<String, Object>> findLabelsForUpdate(List<String> labelNos) {
        String placeholders = String.join(",", labelNos.stream().map(label -> "?").toList());
        return jdbcTemplate.queryForList("""
                SELECT qpl.label_id AS labelId, qpl.label_no AS labelNo, qpl.status,
                       qpl.warehouse_id AS warehouseId, qpl.product_id AS productId,
                       qpl.package_quantity AS packageQuantity,
                       p.product_code AS productCode, p.product_name AS productName
                  FROM quota_package_label qpl
                  JOIN product p ON p.product_id = qpl.product_id
                 WHERE qpl.label_no IN (%s)
                 FOR UPDATE
                """.formatted(placeholders), labelNos.toArray());
    }

    private void validateLabelSet(List<Map<String, Object>> labels, List<String> labelNos, Long warehouseId) {
        if (labels.size() != labelNos.size()) {
            throw new IllegalArgumentException("one or more package labels do not exist");
        }
        for (Map<String, Object> label : labels) {
            if (!"available".equals(String.valueOf(label.get("status")))) {
                throw new IllegalArgumentException("only available package labels can be picked");
            }
            if (((Number) label.get("warehouseId")).longValue() != warehouseId) {
                throw new IllegalArgumentException("selected package labels must belong to the selected warehouse");
            }
        }
    }

    private Long insertPickedDelivery(String deliveryNo, String requisitionNo, Long requisitionItemId,
                                      String deptName, String warehouseName,
                                      String productCode, String productName, BigDecimal quantity) {
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO spd_delivery_order (
                      delivery_no, requisition_no, requisition_item_id, delivery_type,
                      dept_name, warehouse_name, product_code, product_name, quantity, status
                    ) VALUES (?, ?, ?, 'package', ?, ?, ?, ?, ?, 'picked')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, deliveryNo);
            ps.setString(2, requisitionNo);
            ps.setLong(3, requisitionItemId);
            ps.setString(4, deptName);
            ps.setString(5, warehouseName);
            ps.setString(6, productCode);
            ps.setString(7, productName);
            ps.setBigDecimal(8, quantity);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private Long insertLoosePickedDelivery(String deliveryNo, String requisitionNo, Long requisitionItemId,
                                           String deptName, String warehouseName,
                                           String productCode, String productName, BigDecimal quantity) {
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO spd_delivery_order (
                      delivery_no, requisition_no, requisition_item_id, delivery_type,
                      dept_name, warehouse_name, product_code, product_name, quantity, status
                    ) VALUES (?, ?, ?, 'loose', ?, ?, ?, ?, ?, 'picked')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, deliveryNo);
            ps.setString(2, requisitionNo);
            ps.setLong(3, requisitionItemId);
            ps.setString(4, deptName);
            ps.setString(5, warehouseName);
            ps.setString(6, productCode);
            ps.setString(7, productName);
            ps.setBigDecimal(8, quantity);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private Map<String, Object> findProductByProductId(Long productId) {
        return jdbcTemplate.queryForMap("""
                SELECT product_code AS productCode, product_name AS productName
                  FROM product WHERE product_id = ?
                """, productId);
    }

    private void updateRequisitionPickStatus(Long requisitionId) {
        BigDecimal remaining = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(GREATEST(dri.quantity - COALESCE(picked.picked_qty, 0), 0)), 0)
                  FROM department_requisition_item dri
                  LEFT JOIN (
                    SELECT requisition_item_id, SUM(picked_qty) AS picked_qty
                      FROM (
                        SELECT requisition_item_id, COUNT(*) AS picked_qty
                          FROM spd_delivery_package_binding
                         GROUP BY requisition_item_id
                        UNION ALL
                        SELECT rt.requisition_item_id, COUNT(*) AS picked_qty
                          FROM department_requisition_trace_code rt
                          JOIN spd_delivery_trace_code dt ON dt.trace_code_id = rt.trace_code_id
                         GROUP BY rt.requisition_item_id
                        UNION ALL
                        SELECT requisition_item_id, SUM(quantity) AS picked_qty
                          FROM spd_delivery_order
                         WHERE delivery_type = 'loose'
                         GROUP BY requisition_item_id
                      ) picked_sources
                     GROUP BY requisition_item_id
                  ) picked ON picked.requisition_item_id = dri.item_id
                 WHERE dri.requisition_id = ?
                """, BigDecimal.class, requisitionId);
        jdbcTemplate.update("""
                UPDATE department_requisition
                   SET status = ?
                 WHERE requisition_id = ?
                """, remaining != null && remaining.compareTo(BigDecimal.ZERO) <= 0 ? "picked" : "partial_picked", requisitionId);
    }

    private void writePackageEvent(Long labelId, String eventType, String before, String after, BigDecimal qtyChange, String remark) {
        jdbcTemplate.update("""
                INSERT INTO quota_package_event (
                  event_no, label_id, event_type, status_before, status_after, qty_change, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, support.nextNo(QUOTA_PACKAGE_EVENT), labelId, eventType, before, after, qtyChange, remark);
    }

    private Map<String, Object> findProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT product_id AS productId, product_code AS productCode, product_name AS productName,
                       unit, purchase_price AS purchasePrice, is_high_value AS highValue
                  FROM product WHERE product_code = ? AND deleted = 0 AND status = 1
                """, productCode);
    }

    private Long findWarehouseId(String warehouseName) {
        return jdbcTemplate.queryForObject("""
                SELECT warehouse_id FROM warehouse WHERE warehouse_name = ? AND deleted = 0 AND status = 1 LIMIT 1
                """, Long.class, warehouseName);
    }

    private Long findDepartmentWarehouseId(String deptName, Long sourceWarehouseId) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT w.warehouse_id
                  FROM warehouse w
                  JOIN sys_dept d ON d.dept_id = w.dept_id
                 WHERE d.dept_name = ? AND w.warehouse_id <> ?
                   AND d.deleted = 0 AND d.status = 1 AND w.deleted = 0 AND w.status = 1
                 ORDER BY w.warehouse_id
                 LIMIT 1
                """, Long.class, deptName, sourceWarehouseId);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("科室“" + deptName + "”未配置对应的科室库房，请先完成科室库房关联");
        }
        return ids.get(0);
    }

    private static String text(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private static BigDecimal decimal(Map<String, Object> body, String key, BigDecimal fallback) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return new BigDecimal(String.valueOf(value));
    }

    private static String requireText(Map<String, Object> body, String key) {
        String value = text(body, key, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        return List.of(String.valueOf(value).trim());
    }
}
