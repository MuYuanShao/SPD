package com.hospital.spd.supplychain.service;
import com.hospital.spd.specialty.service.HighValueTraceFlowService;
import com.hospital.spd.specialty.service.QuotaPackageTraceFlowService;

import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContextProvider;
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
    private final QuotaPackageTraceFlowService quotaPackageTraceFlowService;
    private final DepartmentRequisitionAccessService accessService;

    public OperationalDeliveryModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support,
                new HighValueTraceFlowService(jdbcTemplate, support, OperatorContext::system),
                new QuotaPackageTraceFlowService(jdbcTemplate, support),
                new DepartmentRequisitionAccessService(jdbcTemplate, OperatorContext::system));
    }

    OperationalDeliveryModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                              QuotaPackageTraceFlowService quotaPackageTraceFlowService) {
        this(jdbcTemplate, support,
                new HighValueTraceFlowService(jdbcTemplate, support, OperatorContext::system),
                quotaPackageTraceFlowService,
                new DepartmentRequisitionAccessService(jdbcTemplate, OperatorContext::system));
    }

    @Autowired
    public OperationalDeliveryModule(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                                     HighValueTraceFlowService traceFlowService,
                                     QuotaPackageTraceFlowService quotaPackageTraceFlowService,
                                     DepartmentRequisitionAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.traceFlowService = traceFlowService;
        this.quotaPackageTraceFlowService = quotaPackageTraceFlowService;
        this.accessService = accessService;
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
        accessService.requirePermission("department-requisition:pick");
        Long itemId = longValue(params.get("itemId"));
        if (itemId == null) {
            return Map.of("rows", List.of());
        }
        requireItemAccess(itemId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT utc.trace_code_id AS traceCodeId, utc.unique_code AS uniqueCode,
                       COALESCE(utc.udi_code, '-') AS udiCode, ib.system_batch_no AS batchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate
                  FROM department_requisition_item dri
                  JOIN department_requisition dr ON dr.requisition_id = dri.requisition_id
                  JOIN inventory_batch ib ON ib.product_id = dri.product_id
                  JOIN inventory_batch_trace_code ibtc ON ibtc.batch_id = ib.batch_id
                       AND ibtc.current_warehouse_id = dr.source_warehouse_id
                  JOIN udi_trace_code utc ON utc.trace_code_id = ibtc.trace_code_id
                 WHERE dri.item_id = ? AND dr.status IN ('approved', 'partial_picked')
                   AND dri.item_type IN ('high_value', 'unique_code')
                   AND utc.current_status = 'in_stock' AND ibtc.lifecycle_status = 'in_stock'
                   AND NOT EXISTS (
                     SELECT 1 FROM spd_delivery_trace_code dt WHERE dt.trace_code_id = utc.trace_code_id
                   )
                 ORDER BY ib.expire_date IS NULL, ib.expire_date, utc.trace_code_id
                """, itemId);
        return Map.of("rows", rows);
    }

    /** Binds selected high-value units at picking time and supports partial fulfillment. */
    @Transactional
    public Map<String, Object> confirmHighValuePicking(Long itemId, List<Long> traceCodeIds) {
        accessService.requirePermission("department-requisition:pick");
        Map<String, Object> item = jdbcTemplate.queryForMap("""
                SELECT dr.requisition_id AS requisitionId, dr.requisition_no AS requisitionNo,
                       dr.dept_id AS deptId, dr.source_warehouse_id AS sourceWarehouseId,
                       sd.dept_name AS deptName, sw.warehouse_name AS sourceWarehouseName,
                       dri.item_id AS itemId, dri.product_id AS productId, dri.quantity,
                       dri.item_type AS itemType, p.product_code AS productCode, p.product_name AS productName
                  FROM department_requisition dr
                  JOIN department_requisition_item dri ON dri.requisition_id = dr.requisition_id
                  JOIN sys_dept sd ON sd.dept_id = dr.dept_id
                  JOIN warehouse sw ON sw.warehouse_id = dr.source_warehouse_id
                  JOIN product p ON p.product_id = dri.product_id
                 WHERE dri.item_id = ? AND dr.status IN ('approved', 'partial_picked')
                 FOR UPDATE
                """, itemId);
        Long deptId = ((Number) item.get("deptId")).longValue();
        accessService.requireDepartment(deptId);
        if (!List.of("high_value", "unique_code").contains(String.valueOf(item.get("itemType")))) {
            throw new IllegalArgumentException("该申请明细不是高值耗材");
        }
        BigDecimal requested = (BigDecimal) item.get("quantity");
        BigDecimal picked = pickedQuantity(itemId);
        BigDecimal current = BigDecimal.valueOf(traceCodeIds.size());
        if (current.signum() <= 0 || picked.add(current).compareTo(requested) > 0) {
            throw new IllegalArgumentException("本次唯一码数量超过申领剩余数量");
        }
        Long productId = ((Number) item.get("productId")).longValue();
        Long sourceWarehouseId = ((Number) item.get("sourceWarehouseId")).longValue();
        List<HighValueTraceFlowService.TraceUnit> units = traceFlowService.requireUnitsByIds(
                traceCodeIds, productId, sourceWarehouseId, List.of("in_stock"));
        String requisitionNo = String.valueOf(item.get("requisitionNo"));
        String deliveryNo = support.nextNo(DELIVERY_ORDER);
        Long deliveryId = insertHighValueDelivery(deliveryNo, requisitionNo, itemId,
                String.valueOf(item.get("deptName")), String.valueOf(item.get("sourceWarehouseName")),
                String.valueOf(item.get("productCode")), String.valueOf(item.get("productName")), current);
        traceFlowService.bindRequisition(((Number) item.get("requisitionId")).longValue(), itemId, units,
                requisitionNo, String.valueOf(item.get("deptName")));
        traceFlowService.bindDelivery(deliveryId, units, deliveryNo, String.valueOf(item.get("deptName")));
        jdbcTemplate.update("UPDATE department_requisition_item SET picked_quantity = picked_quantity + ? WHERE item_id = ?",
                current, itemId);
        updateRequisitionPickStatus(((Number) item.get("requisitionId")).longValue());
        support.writeAudit("department_requisition", "pick_high_value",
                ((Number) item.get("requisitionId")).longValue(), requisitionNo,
                "高值耗材拣配，配送单：" + deliveryNo + "，数量：" + current);
        return Map.of("deliveryNo", deliveryNo, "status", "picked", "pickedQuantity", current,
                "remainingQuantity", requested.subtract(picked).subtract(current));
    }

    /**
     * 拣配散货货源：一级库（中心库）中该申请商品的无货位可用余额，按批次展示。
     */
    public Map<String, Object> availableLooseStock(Map<String, String> params) {
        accessService.requirePermission("department-requisition:pick");
        Long itemId = longValue(params.get("itemId"));
        String warehouseName = params.getOrDefault("warehouseName", "").trim();
        if (itemId == null) {
            return Map.of("rows", List.of());
        }
        requireItemAccess(itemId);
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
                       p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       p.unit, p.purchase_price AS unitPrice,
                       ib.system_batch_no AS systemBatchNo,
                       ib.production_batch_no AS productionBatchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       ib.batch_unit_price AS unitPrice,
                       GREATEST(bal.available_qty - COALESCE(packaged.package_qty, 0), 0) AS availableQty
                  FROM department_requisition_item dri
                  JOIN inventory_balance bal ON bal.product_id = dri.product_id
                   AND bal.location_id IS NULL AND bal.available_qty > 0
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  JOIN product p ON p.product_id = bal.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN (
                    SELECT qpl.warehouse_id, qpl.product_id, src.batch_id, SUM(src.source_qty) AS package_qty
                      FROM quota_package_label qpl
                      JOIN quota_package_label_source src ON src.label_id = qpl.label_id
                     WHERE qpl.status = 'signed'
                     GROUP BY qpl.warehouse_id, qpl.product_id, src.batch_id
                  ) packaged ON packaged.warehouse_id = bal.warehouse_id
                    AND packaged.product_id = bal.product_id AND packaged.batch_id = bal.batch_id
                """ + where + " HAVING availableQty > 0 ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id",
                args.toArray());
        return Map.of("rows", rows);
    }

    /**
     * 散货拣配确认：按申请明细从一级库散货中扣减库存并生成配送单，
     * 配送单记录唯一单据号与关联申请明细。
     */
    @Transactional
    public Map<String, Object> confirmLoosePicking(Map<String, Object> body) {
        accessService.requirePermission("department-requisition:pick");
        String requisitionNo = text(body, "requisitionNo", "");
        Long itemId = longValue(body.get("itemId"));
        String warehouseName = text(body, "warehouseName", "");
        BigDecimal quantity = decimal(body, "quantity", null);
        if (requisitionNo.isBlank() || itemId == null || warehouseName.isBlank()
                || quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("requisitionNo, itemId, warehouseName and quantity are required");
        }
        Map<String, Object> requisition = findRequisitionItemForPicking(requisitionNo, itemId);
        Map<String, Object> savedRoute = requireDeliveryRoute(requisitionNo);
        if (!"loose".equals(requisition.get("itemType"))) {
            throw new IllegalArgumentException("该申领明细不是散货模式");
        }
        if (((Number) savedRoute.get("sourceWarehouseId")).longValue() != findWarehouseId(warehouseName)) {
            throw new IllegalArgumentException("所选来源库与申领保存的来源库不一致");
        }
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
        consumeActualLooseStock(warehouseId, productId, quantity, deliveryId, requisitionNo, deliveryNo);
        updateRequisitionPickStatus(requisitionId);
        support.writeAudit("delivery", "confirm_loose_picking", deliveryId, deliveryNo,
                "picked loose stock for requisition " + requisitionNo);
        return Map.of("deliveryNo", deliveryNo, "status", "picked", "quantity", quantity);
    }

    private void consumeActualLooseStock(Long warehouseId, Long productId, BigDecimal quantity, Long deliveryId,
                                         String requisitionNo, String deliveryNo) {
        List<Map<String, Object>> balances = jdbcTemplate.queryForList("""
                SELECT bal.batch_id AS batchId,
                       GREATEST(bal.available_qty - COALESCE(packaged.package_qty, 0), 0) AS looseQty
                  FROM inventory_balance bal
                  LEFT JOIN (
                    SELECT qpl.warehouse_id, qpl.product_id, src.batch_id, SUM(src.source_qty) AS package_qty
                      FROM quota_package_label qpl
                      JOIN quota_package_label_source src ON src.label_id = qpl.label_id
                     WHERE qpl.status = 'signed'
                     GROUP BY qpl.warehouse_id, qpl.product_id, src.batch_id
                  ) packaged ON packaged.warehouse_id = bal.warehouse_id
                    AND packaged.product_id = bal.product_id AND packaged.batch_id = bal.batch_id
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE bal.warehouse_id = ? AND bal.product_id = ? AND bal.location_id IS NULL
                 HAVING looseQty > 0
                 ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id
                 FOR UPDATE
                """, warehouseId, productId);
        BigDecimal remaining = quantity;
        for (Map<String, Object> balance : balances) {
            if (remaining.signum() <= 0) break;
            BigDecimal deduct = ((BigDecimal) balance.get("looseQty")).min(remaining);
            SupplyChainSupport.InventoryDeductionEvent deduction = support.consumeSpecificBatchEvent(warehouseId, productId, ((Number) balance.get("batchId")).longValue(), deduct,
                    "delivery_loose_out", "spd_delivery_order", deliveryId,
                    "picked loose stock for requisition " + requisitionNo + ", delivery " + deliveryNo);
            jdbcTemplate.update("""
                    INSERT INTO spd_delivery_batch
                      (delivery_id, source_event_id, source_warehouse_id, product_id, batch_id, quantity)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, deliveryId, deduction.eventId(), warehouseId, productId, balance.get("batchId"), deduct);
            remaining = remaining.subtract(deduct);
        }
        if (remaining.signum() > 0) {
            throw new IllegalArgumentException("可用散货库存不足，定数包库存不能按散货拣配");
        }
    }

    public Map<String, Object> pickingRequisitions() {
        accessService.requirePermission("department-requisition:pick");
        StringBuilder scope = new StringBuilder(" WHERE dr.status IN ('approved', 'partial_picked')");
        List<Object> scopeArgs = new ArrayList<>();
        accessService.appendScope(scope, scopeArgs, "dr.dept_id", "dr.applicant_id");
        String pickingSql = """
                SELECT dr.requisition_no AS requisitionNo,
                       dr.requisition_id AS requisitionId,
                       sd.dept_name AS deptName,
                       p.product_code AS productCode,
                       p.product_name AS productName,
                       dri.item_id AS itemId,
                       dri.quantity AS requisitionQty,
                       dri.quota_template_version AS templateVersion,
                       dri.quota_package_quantity AS packageQuantity, dri.quota_package_unit AS packageUnit,
                       CASE WHEN dri.quota_package_quantity > 0 THEN
                         GREATEST(dri.quantity - COALESCE(picked.picked_qty, 0), 0) / dri.quota_package_quantity
                         ELSE NULL END AS remainingPackageCount,
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
                        SELECT requisition_item_id, SUM(package_quantity) AS picked_qty
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
                """ + scope + """
                 HAVING remainingQty > 0
                 ORDER BY dr.apply_time DESC, dr.requisition_id DESC
                 LIMIT 100
                """;
        List<Map<String, Object>> rows = scopeArgs.isEmpty()
                ? jdbcTemplate.queryForList(pickingSql)
                : jdbcTemplate.queryForList(pickingSql, scopeArgs.toArray());
        return Map.of("rows", rows);
    }

    public Map<String, Object> availablePackageLabels(Map<String, String> params) {
        accessService.requirePermission("department-requisition:pick");
        String requisitionNo = params.getOrDefault("requisitionNo", "").trim();
        Long itemId = longValue(params.get("itemId"));
        String warehouseName = params.getOrDefault("warehouseName", "").trim();
        if (requisitionNo.isBlank() || itemId == null) {
            return Map.of("rows", List.of());
        }
        requireItemAccess(itemId);
        List<Object> args = new ArrayList<>();
        args.add(requisitionNo);
        args.add(itemId);
        StringBuilder where = new StringBuilder("""
                 WHERE dr.requisition_no = ?
                   AND dri.item_id = ?
                   AND qpl.status = 'available'
                   AND qpl.product_id = dri.product_id
                   AND dri.item_type = 'quota_package'
                   AND qpl.template_id = dri.quota_template_id
                   AND qpl.package_quantity = dri.quota_package_quantity
                   AND qpl.warehouse_id = dr.source_warehouse_id
                   AND w.deleted = 0 AND w.status = 1
                   AND EXISTS (SELECT 1 FROM quota_package_template t
                     JOIN quota_package_template_item ti ON ti.template_id = t.template_id
                     WHERE t.template_id = qpl.template_id AND t.version_no = dri.quota_template_version
                       AND ti.product_id = dri.product_id AND ti.deleted = 0 AND ti.unit = dri.quota_package_unit)
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
        accessService.requirePermission("department-requisition:pick");
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
        Map<String, Object> savedRoute = requireDeliveryRoute(requisitionNo);
        if (((Number) savedRoute.get("sourceWarehouseId")).longValue() != warehouseId) {
            throw new IllegalArgumentException("所选来源库与申领保存的来源库不一致");
        }
        if (!"quota_package".equals(requisition.get("itemType")) || requisition.get("quotaTemplateId") == null
                || requisition.get("quotaTemplateVersion") == null || requisition.get("quotaPackageQuantity") == null
                || requisition.get("quotaPackageUnit") == null) {
            throw new IllegalArgumentException("申领定数包模板快照缺失，请核对原申请");
        }
        validateLabelSet(labels, labelNos, warehouseId);
        BigDecimal selectedPackageQuantity = labels.stream()
                .map(label -> (BigDecimal) label.get("packageQuantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (Map<String, Object> label : labels) {
            Long labelProductId = ((Number) label.get("productId")).longValue();
            if (!Objects.equals(longValue(label.get("templateId")), longValue(requisition.get("quotaTemplateId")))
                    || !Objects.equals(longValue(label.get("templateVersion")), longValue(requisition.get("quotaTemplateVersion")))
                    || ((BigDecimal) label.get("packageQuantity")).compareTo((BigDecimal) requisition.get("quotaPackageQuantity")) != 0
                    || !Objects.equals(label.get("packageUnit"), requisition.get("quotaPackageUnit"))) {
                throw new IllegalArgumentException("标签模板版本或包装规格与申领快照不一致");
            }
            if (!labelProductId.equals(productId)) {
                throw new IllegalArgumentException("selected package label product does not match requisition item");
            }
        }
        BigDecimal picked = pickedQuantity(itemId);
        if (picked.add(selectedPackageQuantity).compareTo(requested) > 0) {
            throw new IllegalArgumentException("picked package quantity exceeds requisition item quantity");
        }

        String deliveryNo = support.nextNo(DELIVERY_ORDER);
        Map<String, Object> firstLabel = labels.get(0);
        BigDecimal totalQuantity = selectedPackageQuantity;
        String productCode = String.valueOf(firstLabel.get("productCode"));
        String productName = String.valueOf(firstLabel.get("productName"));
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
            quotaPackageTraceFlowService.transitionLabel(labelId, "delivered", "delivery_out",
                    "定数包拣配出库", deliveryNo, warehouseName, deptName,
                    "定数包按申请单拣配出库", 50);
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
                       requisition_no AS requisitionNo,
                       dept_name AS deptName, product_code AS productCode, quantity, status,
                       destination_warehouse_id AS destinationWarehouseId
                  FROM spd_delivery_order WHERE delivery_no = ?
                 FOR UPDATE
                """, deliveryNo);
        String deliveryStatus = String.valueOf(delivery.get("status"));
        Long deliveryId = ((Number) delivery.get("deliveryId")).longValue();
        if ("signed".equals(deliveryStatus)) {
            return Map.of("deliveryNo", deliveryNo, "status", "signed");
        }
        if (!"picked".equals(deliveryStatus)) {
            throw new IllegalArgumentException("only picked delivery can be signed");
        }
        Map<String, Object> route = requireDeliveryRoute(String.valueOf(delivery.get("requisitionNo")));
        Long sourceWarehouseId = ((Number) route.get("sourceWarehouseId")).longValue();
        Long destinationWarehouseId = ((Number) route.get("destinationWarehouseId")).longValue();
        Integer packageBindingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spd_delivery_package_binding WHERE delivery_id = ?", Integer.class, deliveryId);
        Integer traceBindingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spd_delivery_trace_code WHERE delivery_id = ?", Integer.class, deliveryId);
        if (traceBindingCount != null && traceBindingCount > 0) {
            Map<String, Object> product = findProduct(String.valueOf(delivery.get("productCode")));
            int signedCount = traceFlowService.signDelivery(deliveryId, deliveryNo,
                    ((Number) product.get("productId")).longValue(), sourceWarehouseId, destinationWarehouseId,
                    String.valueOf(delivery.get("deptName")), String.valueOf(delivery.get("deptName")));
            if (BigDecimal.valueOf(signedCount).compareTo((BigDecimal) delivery.get("quantity")) != 0) {
                throw new IllegalArgumentException("配送唯一码数量与配送数量不一致");
            }
        } else if (packageBindingCount != null && packageBindingCount > 0) {
            receiveSignedPackages(deliveryId, deliveryNo, sourceWarehouseId, destinationWarehouseId);
        } else {
            receiveLooseDelivery(deliveryId, sourceWarehouseId, destinationWarehouseId,
                    (BigDecimal) delivery.get("quantity"));
        }
        int updated = jdbcTemplate.update("UPDATE spd_delivery_order SET status = 'signed', sign_time = NOW(), destination_warehouse_id = ? WHERE delivery_no = ? AND status = 'picked'",
                destinationWarehouseId, deliveryNo);
        if (updated != 1) {
            throw new IllegalArgumentException("delivery has already been processed");
        }
        return Map.of("deliveryNo", deliveryNo, "status", "signed");
    }

    private Map<String, Object> requireDeliveryRoute(String requisitionNo) {
        List<Map<String, Object>> routes = jdbcTemplate.queryForList("""
                SELECT dr.source_warehouse_id AS sourceWarehouseId, dr.warehouse_id AS destinationWarehouseId,
                       dr.dept_id AS deptId
                  FROM department_requisition dr
                  JOIN warehouse src ON src.warehouse_id = dr.source_warehouse_id AND src.deleted = 0 AND src.status = 1
                  JOIN warehouse dst ON dst.warehouse_id = dr.warehouse_id AND dst.deleted = 0 AND dst.status = 1
                    AND dst.dept_id = dr.dept_id
                  JOIN sys_dept d ON d.dept_id = dr.dept_id AND d.deleted = 0 AND d.status = 1
                 WHERE dr.requisition_no = ? AND src.warehouse_id <> dst.warehouse_id
                 FOR UPDATE
                """, requisitionNo);
        if (routes.size() != 1) throw new IllegalArgumentException("申领来源库或目标库无效、归属已变化或历史关联缺失，请核对原单");
        Map<String, Object> route = routes.get(0);
        if (!accessService.isUnrestricted()) accessService.requireDepartment(((Number) route.get("deptId")).longValue());
        return route;
    }

    private void receiveLooseDelivery(Long deliveryId, Long sourceId, Long destinationId, BigDecimal quantity) {
        List<Map<String, Object>> batches = jdbcTemplate.queryForList("""
                SELECT source_warehouse_id AS sourceWarehouseId, product_id AS productId,
                       batch_id AS batchId, quantity
                  FROM spd_delivery_batch WHERE delivery_id = ? ORDER BY source_event_id FOR UPDATE
                """, deliveryId);
        BigDecimal total = batches.stream().map(row -> (BigDecimal) row.get("quantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (batches.isEmpty() || total.compareTo(quantity) != 0 || batches.stream().anyMatch(row ->
                !sourceId.equals(((Number) row.get("sourceWarehouseId")).longValue())
                        || ((BigDecimal) row.get("quantity")).signum() <= 0)) {
            throw new IllegalArgumentException("配送原扣减批次缺失或数量不一致，请核对历史配送单");
        }
        for (Map<String, Object> batch : batches) {
            support.receiveAvailable(destinationId, ((Number) batch.get("productId")).longValue(),
                    ((Number) batch.get("batchId")).longValue(), (BigDecimal) batch.get("quantity"),
                    "delivery_sign_in", "spd_delivery_order", deliveryId, "配送原拣配批次签收入库");
        }
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
    private void receiveSignedPackages(Long deliveryId, String deliveryNo, Long sourceWarehouseId, Long destinationWarehouseId) {
        Integer invalid = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM spd_delivery_package_binding b
                  LEFT JOIN quota_package_label q ON q.label_id = b.label_id
                 WHERE b.delivery_id = ? AND (
                   q.label_id IS NULL OR q.status <> 'delivered' OR q.trace_code_id IS NULL
                   OR q.warehouse_id <> ? OR q.product_id <> b.product_id
                   OR q.package_quantity <> b.package_quantity
                   OR b.package_quantity <= 0
                   OR (SELECT COALESCE(SUM(s.source_qty), 0) FROM quota_package_label_source s
                        WHERE s.label_id = b.label_id) <> b.package_quantity
                   OR EXISTS (SELECT 1 FROM quota_package_label_source s
                        LEFT JOIN inventory_batch ib ON ib.batch_id = s.batch_id
                       WHERE s.label_id = b.label_id AND
                         (s.source_qty <= 0 OR s.warehouse_id <> ? OR ib.batch_id IS NULL OR ib.product_id <> b.product_id)))
                """, Integer.class, deliveryId, sourceWarehouseId, sourceWarehouseId);
        if (invalid != null && invalid > 0) {
            throw new IllegalArgumentException("配送定数包来源批次、数量或状态不完整，请核对全部标签");
        }
        List<Map<String, Object>> sources = jdbcTemplate.queryForList("""
                SELECT b.label_id AS labelId, b.product_id AS productId,
                       s.batch_id AS batchId, s.source_qty AS sourceQty, qpl.trace_code_id AS traceCodeId
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
            Long inventoryEventId = support.receiveAvailable(destinationWarehouseId,
                    ((Number) source.get("productId")).longValue(),
                    ((Number) source.get("batchId")).longValue(),
                    (BigDecimal) source.get("sourceQty"),
                    "quota_package_delivery_sign_in", "spd_delivery_order", deliveryId,
                    "signed quota package received into department warehouse");
            Long traceCodeId = longValue(source.get("traceCodeId"));
            if (traceCodeId == null) throw new IllegalArgumentException("定数包追溯标识缺失");
            support.linkInventoryEventTraceCodes(inventoryEventId, List.of(
                    new com.hospital.spd.common.service.InventoryEventCommand.TraceLink(
                            traceCodeId, "quota_package", (BigDecimal) source.get("sourceQty"))));
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
            quotaPackageTraceFlowService.completeSign(labelId, deliveryNo);
        }
    }
    private Map<String, Object> findRequisitionItemForPicking(String requisitionNo, Long itemId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT dr.requisition_id AS requisitionId, dr.dept_id AS deptId, dr.status, sd.dept_name AS deptName,
                       dri.item_id AS itemId, dri.product_id AS productId, dri.quantity,
                       dr.source_warehouse_id AS sourceWarehouseId, dr.warehouse_id AS destinationWarehouseId,
                       dri.item_type AS itemType, dri.quota_template_id AS quotaTemplateId,
                       dri.quota_template_version AS quotaTemplateVersion,
                       dri.quota_package_quantity AS quotaPackageQuantity, dri.quota_package_unit AS quotaPackageUnit
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
        if (!accessService.isUnrestricted()) {
            accessService.requireDepartment(((Number) rows.get(0).get("deptId")).longValue());
        }
        return rows.get(0);
    }

    private void requireItemAccess(Long itemId) {
        if (accessService.isUnrestricted()) return;
        List<Long> deptIds = jdbcTemplate.queryForList("""
                SELECT dr.dept_id FROM department_requisition_item dri
                  JOIN department_requisition dr ON dr.requisition_id = dri.requisition_id
                 WHERE dri.item_id = ?
                """, Long.class, itemId);
        if (deptIds.size() != 1) throw new IllegalArgumentException("申领明细不存在");
        accessService.requireDepartment(deptIds.get(0));
    }

    private BigDecimal pickedQuantity(Long itemId) {
        BigDecimal picked = jdbcTemplate.queryForObject("""
                SELECT (
                    SELECT COALESCE(SUM(package_quantity), 0)
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
                       qpl.package_quantity AS packageQuantity, qpl.template_id AS templateId,
                       t.version_no AS templateVersion,
                       (SELECT ti.unit FROM quota_package_template_item ti WHERE ti.template_id = qpl.template_id
                         AND ti.product_id = qpl.product_id AND ti.deleted = 0 LIMIT 1) AS packageUnit,
                       p.product_code AS productCode, p.product_name AS productName
                  FROM quota_package_label qpl
                  JOIN product p ON p.product_id = qpl.product_id
                  JOIN quota_package_template t ON t.template_id = qpl.template_id
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

    private Long insertHighValueDelivery(String deliveryNo, String requisitionNo, Long requisitionItemId,
                                         String deptName, String warehouseName,
                                         String productCode, String productName, BigDecimal quantity) {
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO spd_delivery_order (
                      delivery_no, requisition_no, requisition_item_id, delivery_type,
                      dept_name, warehouse_name, product_code, product_name, quantity, status
                    ) VALUES (?, ?, ?, 'unique_code', ?, ?, ?, ?, ?, 'picked')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, deliveryNo); ps.setString(2, requisitionNo); ps.setLong(3, requisitionItemId);
            ps.setString(4, deptName); ps.setString(5, warehouseName); ps.setString(6, productCode);
            ps.setString(7, productName); ps.setBigDecimal(8, quantity);
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
                        SELECT requisition_item_id, SUM(package_quantity) AS picked_qty
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
