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

    /**
     * 库存汇总查询：按库房与商品聚合散货余额，数量 = 散货数量（中心库散货）+ 在库定数包内的散货数量，
     * 金额 = 数量 × 商品采购价。散货口径为无货位余额（location_id IS NULL），定数包口径为
     * 在库标签（待打印/已打印可用）的 package_quantity 之和。
     */
    public Map<String, Object> balances(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE p.deleted = 0
                """);
        appendLike(where, args, "w.warehouse_name", params.get("warehouseName"));
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));
        appendLike(where, args, "d.dept_name", params.get("deptName"));
        appendLike(where, args, "m.manufacturer_name", params.get("manufacturerName"));
        appendLike(where, args, "s.supplier_name", params.get("supplierName"));

        String fromClause = """
                   FROM product p
                   JOIN (
                     SELECT warehouse_id, product_id, SUM(available_qty) AS loose_qty
                       FROM inventory_balance
                      WHERE location_id IS NULL
                      GROUP BY warehouse_id, product_id
                   ) lo ON lo.product_id = p.product_id
                   LEFT JOIN (
                     SELECT warehouse_id, product_id, SUM(package_quantity) AS packaged_qty
                       FROM quota_package_label
                      WHERE status IN ('pending_print', 'available')
                      GROUP BY warehouse_id, product_id
                   ) qp ON qp.warehouse_id = lo.warehouse_id AND qp.product_id = lo.product_id
                   JOIN warehouse w ON w.warehouse_id = lo.warehouse_id
                   LEFT JOIN sys_dept d ON d.dept_id = w.dept_id AND d.deleted = 0
                   LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id AND m.deleted = 0
                   LEFT JOIN supplier s ON s.supplier_id = p.supplier_id AND s.deleted = 0
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT w.warehouse_name AS warehouseName,
                       COALESCE(d.dept_name, '-') AS deptName,
                       p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                       COALESCE(p.registration_no, '-') AS registrationNo,
                       COALESCE(p.purchase_price, 0) AS unitPrice, p.unit AS unit,
                       ROUND(lo.loose_qty + COALESCE(qp.packaged_qty, 0), 4) AS qty,
                       ROUND((lo.loose_qty + COALESCE(qp.packaged_qty, 0)) * COALESCE(p.purchase_price, 0), 2) AS amount,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       COALESCE(s.supplier_name, '-') AS supplierName
                """ + fromClause + where + " ORDER BY w.warehouse_name, p.product_code LIMIT ? OFFSET ?",
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

    /**
     * 定数包库存查询：在库定数包标签（待打印/已打印可用）按库房与模板汇总，附带同商品散货数量。
     */
    public Map<String, Object> quotaPackageStock(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE qpl.status IN ('pending_print', 'available')
                """);
        appendLike(where, args, "w.warehouse_name", params.get("warehouseName"));
        appendLike(where, args, "d.dept_name", params.get("deptName"));
        appendLike(where, args, "qpt.template_name", params.get("packageName"));
        appendLike(where, args, "p.product_name", params.get("productName"));

        String groupKeys = "d.dept_name, w.warehouse_name, qpt.template_code, qpt.template_name, p.product_id";
        String fromClause = """
                  FROM quota_package_label qpl
                  JOIN quota_package_template qpt ON qpt.template_id = qpl.template_id
                  JOIN product p ON p.product_id = qpl.product_id
                  JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id AND d.deleted = 0
                """;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT " + groupKeys + ") " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT w.warehouse_name AS warehouseName, COALESCE(d.dept_name, '-') AS deptName,
                       qpt.template_code AS packageCode, qpt.template_name AS packageName,
                       p.spec_model AS specModel, COALESCE(p.registration_no, '-') AS registrationNo,
                       COALESCE(p.purchase_price, 0) AS unitPrice, p.unit AS unit,
                       COUNT(qpl.label_id) AS packageCount,
                       COALESCE(SUM(qpl.package_quantity), 0) AS packageQty,
                       COALESCE(lo.loose_qty, 0) AS looseQty,
                       ROUND((COALESCE(SUM(qpl.package_quantity), 0) + COALESCE(lo.loose_qty, 0))
                             * COALESCE(p.purchase_price, 0), 2) AS amount,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       COALESCE(s.supplier_name, '-') AS supplierName
                  FROM quota_package_label qpl
                  JOIN quota_package_template qpt ON qpt.template_id = qpl.template_id
                  JOIN product p ON p.product_id = qpl.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id AND m.deleted = 0
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id AND s.deleted = 0
                  JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id AND d.deleted = 0
                  LEFT JOIN (
                    SELECT warehouse_id, product_id, SUM(available_qty) AS loose_qty
                      FROM inventory_balance
                     WHERE location_id IS NULL
                     GROUP BY warehouse_id, product_id
                  ) lo ON lo.warehouse_id = qpl.warehouse_id AND lo.product_id = qpl.product_id
                """ + where + " GROUP BY " + groupKeys
                + ", p.spec_model, p.registration_no, p.purchase_price, p.unit, m.manufacturer_name, s.supplier_name"
                + ", lo.loose_qty"
                + " ORDER BY w.warehouse_name, qpt.template_code LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    /**
     * 唯一码库存查询：在库唯一码（UDI 追溯单元）关联批次与余额。
     */
    public Map<String, Object> uniqueCodeStock(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE bal.available_qty > 0 AND bal.location_id IS NULL
                   AND utc.current_status = 'in_stock'
                """);
        appendLike(where, args, "d.dept_name", params.get("deptName"));
        appendLike(where, args, "w.warehouse_name", params.get("warehouseName"));
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));
        appendLike(where, args, "ib.system_batch_no", params.get("batchNo"));
        appendLike(where, args, "utc.unique_code", params.get("uniqueCode"));
        appendLike(where, args, "roi.udi_code", params.get("udiCode"));

        String fromClause = """
                  FROM inventory_batch_trace_code ibtc
                  JOIN udi_trace_code utc ON utc.trace_code_id = ibtc.trace_code_id
                  JOIN inventory_batch ib ON ib.batch_id = ibtc.batch_id
                  LEFT JOIN receiving_order_item roi ON roi.item_id = ib.receiving_item_id
                  JOIN inventory_balance bal ON bal.batch_id = ib.batch_id AND bal.location_id IS NULL
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id AND d.deleted = 0
                  JOIN product p ON p.product_id = ib.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id AND m.deleted = 0
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id AND s.deleted = 0
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT COALESCE(d.dept_name, '-') AS deptName, w.warehouse_name AS warehouseName,
                       p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, COALESCE(p.registration_no, '-') AS registrationNo,
                       ib.system_batch_no AS batchNo, COALESCE(ib.production_batch_no, '-') AS productionBatchNo,
                       ib.batch_unit_price AS unitPrice, p.unit AS unit,
                       bal.available_qty AS qty,
                       ROUND(bal.available_qty * COALESCE(ib.batch_unit_price, 0), 2) AS amount,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       COALESCE(s.supplier_name, '-') AS supplierName,
                       COALESCE(utc.unique_code, '-') AS uniqueCode,
                       COALESCE(roi.udi_code, '-') AS udiCode
                  FROM inventory_batch_trace_code ibtc
                  JOIN udi_trace_code utc ON utc.trace_code_id = ibtc.trace_code_id
                  JOIN inventory_batch ib ON ib.batch_id = ibtc.batch_id
                  LEFT JOIN receiving_order_item roi ON roi.item_id = ib.receiving_item_id
                  JOIN inventory_balance bal ON bal.batch_id = ib.batch_id AND bal.location_id IS NULL
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id AND d.deleted = 0
                  JOIN product p ON p.product_id = ib.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id AND m.deleted = 0
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id AND s.deleted = 0
                """ + where + " ORDER BY d.dept_name, w.warehouse_name, utc.unique_code LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    /**
     * 交易类型映射：验收入库、打包入库、解包、二级库入库、三级库入库、二级库出库、三级库出库。
     * 一级库（中心库）其余出入库按方向归入入库/出库类型展示。
     */
    private static final String TRANSACTION_TYPE_EXPR = """
            (CASE
               WHEN ie.event_type = 'purchase_receive_in' THEN '验收入库'
               WHEN ie.event_type = 'quota_pack_out' THEN '打包入库'
               WHEN ie.event_type IN ('quota_unpack_in', 'quota_terminate_in') THEN '解包'
               WHEN w.warehouse_type LIKE '%三级%' AND ie.qty_change > 0 THEN '三级库入库'
               WHEN w.warehouse_type LIKE '%三级%' AND ie.qty_change < 0 THEN '三级库出库'
               WHEN w.warehouse_type LIKE '%二级%' AND ie.qty_change > 0 THEN '二级库入库'
               WHEN w.warehouse_type LIKE '%二级%' AND ie.qty_change < 0 THEN '二级库出库'
               WHEN ie.qty_change > 0 THEN '验收入库'
               ELSE '二级库出库'
             END)""";

    public Map<String, Object> events(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1
                """);
        appendLike(where, args, "ie.event_type", params.get("eventType"));
        if (!isBlank(params.get("transactionType"))) {
            where.append(" AND ").append(TRANSACTION_TYPE_EXPR).append(" = ?");
            args.add(params.get("transactionType").trim());
        }
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
                       """ + TRANSACTION_TYPE_EXPR + """
                       AS transactionType,
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

    /**
     * 新增盘点表：按所选商品范围（高值耗材/可收费耗材/不可收费耗材/定数包）生成该库房散货商品的
     * 盘点明细，库存数量 = 无货位可用余额合计；盘点数量由盘点人录入，差异数量 = 库存数量 - 盘点数量。
     */
    @Transactional
    public Map<String, Object> createStocktakingSheet(StocktakingSheetRequest request) {
        if (request == null || request.scopes() == null || request.scopes().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个盘点商品范围");
        }
        Long warehouseId = jdbcTemplate.queryForObject("""
                SELECT warehouse_id FROM warehouse
                 WHERE warehouse_name = ? AND deleted = 0 AND status = 1 LIMIT 1
                """, Long.class, request.warehouseName().trim());
        StringBuilder scopeWhere = new StringBuilder();
        List<Object> scopeArgs = new ArrayList<>();
        for (String scope : request.scopes()) {
            String condition = switch (scope.trim()) {
                case "highValue" -> "p.is_high_value = 1";
                case "chargeable" -> "p.is_chargeable = 1";
                case "nonChargeable" -> "p.is_chargeable = 0";
                case "quotaPackage" -> "p.is_quota_managed = 1";
                default -> null;
            };
            if (condition != null) {
                if (scopeWhere.length() > 0) {
                    scopeWhere.append(" OR ");
                }
                scopeWhere.append("(").append(condition).append(")");
            }
        }
        if (scopeWhere.length() == 0) {
            throw new IllegalArgumentException("盘点商品范围不正确");
        }
        List<Map<String, Object>> balances = jdbcTemplate.queryForList("""
                SELECT p.product_id AS productId, p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       p.unit, SUM(bal.available_qty) AS systemQty
                  FROM inventory_balance bal
                  JOIN product p ON p.product_id = bal.product_id AND p.deleted = 0 AND p.status = 1
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                 WHERE bal.warehouse_id = ? AND bal.location_id IS NULL AND bal.available_qty > 0
                   AND (
                """ + scopeWhere + " ) GROUP BY p.product_id, p.product_code, p.product_name, p.spec_model, m.manufacturer_name, p.unit ORDER BY p.product_code",
                prepend(warehouseId, scopeArgs));

        String stocktakingNo = support.nextNo(INVENTORY_STOCKTAKING);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO inventory_stocktaking (stocktaking_no, warehouse_id, dept_name, stocktaking_type, status, reason)
                    VALUES (?, ?, ?, '范围盘点', 'draft', NULL)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, stocktakingNo);
            ps.setLong(2, warehouseId);
            ps.setString(3, nullIfBlank(request.deptName()));
            return ps;
        }, keyHolder);
        Long stocktakingId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        for (Map<String, Object> balance : balances) {
            jdbcTemplate.update("""
                    INSERT INTO inventory_stocktaking_item (
                      stocktaking_id, balance_id, product_id, batch_id, system_qty, actual_qty, diff_qty, diff_reason
                    ) VALUES (?, NULL, ?, NULL, ?, NULL, NULL, NULL)
                    """, stocktakingId, ((Number) balance.get("productId")).longValue(),
                    (BigDecimal) balance.get("systemQty"));
        }
        writeAudit("create_stocktaking_sheet", stocktakingId, stocktakingNo,
                "create scope stocktaking sheet with scopes " + request.scopes());
        return Map.of("stocktakingNo", stocktakingNo, "rowCount", balances.size());
    }

    /** 盘点表明细：商品、库存数量、盘点数量；差异数量由库存数量减盘点数量计算得出。 */
    public Map<String, Object> stocktakingItems(String stocktakingNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT si.item_id AS itemId, p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       p.unit, si.system_qty AS systemQty, si.actual_qty AS actualQty,
                       si.diff_qty AS diffQty
                  FROM inventory_stocktaking st
                  JOIN inventory_stocktaking_item si ON si.stocktaking_id = st.stocktaking_id
                  JOIN product p ON p.product_id = si.product_id
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                 WHERE st.stocktaking_no = ?
                 ORDER BY si.item_id
                """, stocktakingNo.trim());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("盘点表不存在或没有明细");
        }
        return Map.of("rows", rows);
    }

    /** 保存盘点数量：写入实盘数量并计算差异（差异 = 实盘 - 库存，正数盘盈负数盘亏）。 */
    @Transactional
    public Map<String, Object> updateStocktakingItems(String stocktakingNo, StocktakingItemsUpdateRequest request) {
        Map<String, Object> doc = jdbcTemplate.queryForMap("""
                SELECT stocktaking_id AS stocktakingId, status
                  FROM inventory_stocktaking
                 WHERE stocktaking_no = ?
                 FOR UPDATE
                """, stocktakingNo.trim());
        if (!"draft".equals(String.valueOf(doc.get("status")))) {
            throw new IllegalArgumentException("盘点表已复核，不能再保存盘点数量");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("请至少填写一条盘点数量");
        }
        int updated = 0;
        for (StocktakingItemsUpdateRequest.Item item : request.items()) {
            if (item == null || item.itemId() == null) {
                continue;
            }
            BigDecimal actualQty = item.actualQty() == null ? BigDecimal.ZERO : item.actualQty();
            int changed = jdbcTemplate.update("""
                    UPDATE inventory_stocktaking_item si
                    JOIN inventory_stocktaking st ON st.stocktaking_id = si.stocktaking_id
                       SET si.actual_qty = ?, si.diff_qty = ? - si.system_qty
                     WHERE st.stocktaking_no = ? AND si.item_id = ? AND st.status = 'draft'
                    """, actualQty, actualQty, stocktakingNo.trim(), item.itemId());
            updated += changed;
        }
        return Map.of("stocktakingNo", stocktakingNo, "updatedRows", updated);
    }

    private static Object[] prepend(Object first, List<Object> rest) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.addAll(rest);
        return args.toArray();
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
            Long productId = ((Number) item.get("productId")).longValue();
            if (item.get("balanceId") == null) {
                // 范围盘点明细按商品汇总：盘亏按 FIFO 扣减，盘盈计入该商品最近批次
                if (diffQty.compareTo(BigDecimal.ZERO) < 0) {
                    support.consumeAvailableFifo(((Number) doc.get("warehouseId")).longValue(), productId,
                            diffQty.negate(), "stocktaking_loss", "inventory_stocktaking",
                            ((Number) doc.get("stocktakingId")).longValue(),
                            "stocktaking approval generated inventory adjustment");
                } else {
                    Long batchId = jdbcTemplate.queryForObject("""
                            SELECT batch_id FROM inventory_batch
                             WHERE product_id = ?
                             ORDER BY batch_id DESC LIMIT 1
                            """, Long.class, productId);
                    support.receiveAvailable(((Number) doc.get("warehouseId")).longValue(), productId, batchId,
                            diffQty, "stocktaking_profit", "inventory_stocktaking",
                            ((Number) doc.get("stocktakingId")).longValue(),
                            "stocktaking approval generated inventory adjustment");
                }
                continue;
            }
            support.adjustAvailable(((Number) item.get("balanceId")).longValue(),
                    ((Number) doc.get("warehouseId")).longValue(),
                    productId,
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
                       st.dept_name AS deptName,
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
