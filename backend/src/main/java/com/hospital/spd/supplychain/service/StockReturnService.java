package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.JdbcPageQuery;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.QuantityRules;
import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.common.service.InventoryEventCommand.TraceLink;
import com.hospital.spd.specialty.service.QuotaPackageTraceFlowService;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Owns physical inventory returns, stock reservations and confirmation. */
@Service
public class StockReturnService {
    private static final String SOURCE = "stock_return_order";
    private final JdbcTemplate jdbc;
    private final SupplyChainSupport stock;
    private final OperatorContextProvider operators;
    private final DepartmentRequisitionAccessService access;
    private final QuotaPackageTraceFlowService packages;
    private final QuotaPackagePricingService pricing;

    public StockReturnService(JdbcTemplate jdbc, SupplyChainSupport stock, OperatorContextProvider operators,
                              DepartmentRequisitionAccessService access, QuotaPackageTraceFlowService packages,
                              QuotaPackagePricingService pricing) {
        this.jdbc = jdbc;
        this.stock = stock;
        this.operators = operators;
        this.access = access;
        this.packages = packages;
        this.pricing = pricing;
    }

    public record Selection(long id, BigDecimal quantity) {}
    public record ReturnRequest(String kind, long sourceWarehouseId, Long destinationWarehouseId,
                                Long supplierId, String selectionType, String reason, List<Selection> items, String requestKey) {}

    public Map<String, Object> options() {
        StringBuilder filter = new StringBuilder(" WHERE w.deleted=0 AND w.status=1");
        List<Object> args = new ArrayList<>();
        if (!access.isUnrestricted()) access.appendScope(filter, args, "w.dept_id", null);
        var sources = jdbc.queryForList("""
                SELECT w.warehouse_id AS id, w.warehouse_name AS name, w.warehouse_type AS type,
                       w.campus_name AS campus
                  FROM warehouse w
                """ + filter + " ORDER BY w.warehouse_name,w.warehouse_id", args.toArray());
        var centers = jdbc.queryForList("""
                SELECT warehouse_id AS id, warehouse_name AS name, campus_name AS campus FROM warehouse
                 WHERE deleted=0 AND status=1 AND (warehouse_type LIKE '%一级%' OR warehouse_type LIKE '%中心%')
                 ORDER BY warehouse_name,warehouse_id
                """);
        var suppliers = jdbc.queryForList("""
                SELECT supplier_id AS id, supplier_name AS name FROM supplier WHERE deleted=0
                 ORDER BY supplier_name,supplier_id
                """);
        return Map.of("warehouses", sources, "centers", centers, "suppliers", suppliers);
    }

    public List<Map<String, Object>> records(boolean queue) {
        return queryReturns(queue, null).rows();
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> pickingQueue(PageRequest page) {
        return queryReturns(true, page);
    }

    private PageResponse<Map<String, Object>> queryReturns(boolean queue, PageRequest page) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        access.appendScope(where, args, "r.dept_id", "r.created_by");
        if (queue) where.append(" AND r.return_kind='department' AND r.status='pending_confirm'");
        String from = """
                  FROM stock_return_order r JOIN warehouse w ON w.warehouse_id=r.source_warehouse_id
                  LEFT JOIN warehouse target ON target.warehouse_id=r.destination_warehouse_id
                  LEFT JOIN supplier ON supplier.supplier_id=r.supplier_id
                """ + where;
        return JdbcPageQuery.fetch(jdbc, page, """
                SELECT r.return_no AS returnNo, r.return_kind AS kind, r.status, r.reason,
                       w.warehouse_name AS sourceWarehouse, target.warehouse_name AS destinationWarehouse,
                       supplier.supplier_name AS supplierName, DATE_FORMAT(r.create_time,'%Y-%m-%d %H:%i:%s') AS createTime,
                       DATE_FORMAT(r.confirm_time,'%Y-%m-%d %H:%i:%s') AS confirmTime,
                       (SELECT SUM(i.quantity) FROM stock_return_item i WHERE i.return_id=r.return_id) AS quantity
                """, from, " ORDER BY r.return_id DESC", args);
    }

    public List<Map<String, Object>> candidates(long warehouseId, String type, Long supplierId, String batchNo, String keyword) {
        sourceWarehouse(warehouseId);
        String batch = batchNo == null ? "" : batchNo.trim();
        String code = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return switch (type) {
            case "batch" -> jdbc.queryForList("""
                    SELECT bal.balance_id AS id, b.system_batch_no AS batchNo, p.product_code AS productCode,
                           p.product_name AS productName, b.system_batch_no AS code,
                           p.spec_model AS specModel, p.registration_no AS registrationNo, p.unit,
                           m.manufacturer_name AS manufacturerName, b.batch_unit_price AS unitPrice,
                           GREATEST(bal.available_qty - (
                               SELECT COALESCE(SUM(s.source_qty),0) FROM quota_package_label_source s
                               JOIN quota_package_label q ON q.label_id=s.label_id
                               WHERE s.batch_id=bal.batch_id AND q.warehouse_id=bal.warehouse_id AND q.status='signed'
                           ),0) AS availableQuantity
                      FROM inventory_balance bal JOIN inventory_batch b ON b.batch_id=bal.batch_id
                      JOIN product p ON p.product_id=bal.product_id
                      LEFT JOIN manufacturer m ON m.manufacturer_id=p.manufacturer_id
                     WHERE bal.warehouse_id=? AND bal.location_id IS NULL AND bal.available_qty>0
                       AND COALESCE(p.is_high_value,0)=0
                       AND NOT EXISTS (SELECT 1 FROM inventory_batch_trace_code tc WHERE tc.batch_id=b.batch_id)
                       AND (? IS NULL OR b.supplier_id=?)
                       AND (?='' OR b.system_batch_no=? OR b.production_batch_no=?)
                       AND (p.product_code LIKE ? OR p.product_name LIKE ? OR b.system_batch_no LIKE ?)
                    HAVING availableQuantity>0 ORDER BY b.batch_id
                    """, warehouseId, supplierId, supplierId, batch, batch, batch, code, code, code);
            case "trace" -> jdbc.queryForList("""
                    SELECT t.trace_code_id AS id, b.system_batch_no AS batchNo, p.product_code AS productCode,
                           p.product_name AS productName, u.unique_code AS code, 1 AS availableQuantity,
                           p.spec_model AS specModel, p.registration_no AS registrationNo, p.unit,
                           m.manufacturer_name AS manufacturerName, b.batch_unit_price AS unitPrice
                      FROM inventory_batch_trace_code t JOIN inventory_batch b ON b.batch_id=t.batch_id
                      JOIN udi_trace_code u ON u.trace_code_id=t.trace_code_id AND u.trace_scope='high_value'
                      JOIN product p ON p.product_id=b.product_id
                      LEFT JOIN manufacturer m ON m.manufacturer_id=p.manufacturer_id
                     WHERE t.current_warehouse_id=? AND (t.lifecycle_status='signed' OR (? IS NOT NULL AND t.lifecycle_status='in_stock'))
                       AND (? IS NULL OR b.supplier_id=?)
                       AND (?='' OR b.system_batch_no=? OR b.production_batch_no=?)
                       AND (u.unique_code LIKE ? OR u.udi_code LIKE ? OR p.product_name LIKE ?)
                     ORDER BY t.trace_code_id
                    """, warehouseId, supplierId, supplierId, supplierId, batch, batch, batch, code, code, code);
            case "package" -> jdbc.queryForList("""
                    SELECT q.label_id AS id, q.label_no AS code, p.product_code AS productCode, p.product_name AS productName,
                           1 AS availableQuantity, q.package_quantity AS packageQuantity,
                           p.spec_model AS specModel, p.registration_no AS registrationNo, p.unit,
                           m.manufacturer_name AS manufacturerName,
                           (SELECT SUM(s.source_qty * b.batch_unit_price)
                              FROM quota_package_label_source s JOIN inventory_batch b ON b.batch_id=s.batch_id
                             WHERE s.label_id=q.label_id) AS unitPrice,
                           (SELECT GROUP_CONCAT(DISTINCT b.system_batch_no ORDER BY b.system_batch_no)
                              FROM quota_package_label_source s JOIN inventory_batch b ON b.batch_id=s.batch_id
                             WHERE s.label_id=q.label_id) AS batchNo
                      FROM quota_package_label q JOIN product p ON p.product_id=q.product_id
                      LEFT JOIN manufacturer m ON m.manufacturer_id=p.manufacturer_id
                     WHERE q.warehouse_id=? AND (q.status='signed' OR (? IS NOT NULL AND q.status='available'))
                       AND (q.label_no LIKE ? OR p.product_name LIKE ?)
                       AND EXISTS (SELECT 1 FROM quota_package_label_source s JOIN inventory_batch b ON b.batch_id=s.batch_id
                                    WHERE s.label_id=q.label_id AND (?='' OR b.system_batch_no=? OR b.production_batch_no=?))
                       AND (? IS NULL OR NOT EXISTS (
                           SELECT 1 FROM quota_package_label_source s JOIN inventory_batch b ON b.batch_id=s.batch_id
                            WHERE s.label_id=q.label_id AND NOT (b.supplier_id <=> ?)
                       ))
                     ORDER BY q.label_id
                    """, warehouseId, supplierId, code, code, batch, batch, batch, supplierId, supplierId);
            default -> throw new IllegalArgumentException("请选择退货库存类型");
        };
    }

    @Transactional
    public Map<String, Object> submit(ReturnRequest request) {
        if (request.requestKey() == null || !request.requestKey().matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException("退货提交标识无效，请刷新后重试");
        }
        Long operatorId = operators.current().userId();
        if (operatorId == null) throw new AccessDeniedException("无法识别退货操作人");
        var prior = jdbc.queryForList("SELECT return_no,status,created_by FROM stock_return_order WHERE request_key=?", request.requestKey());
        if (!prior.isEmpty()) return submittedResult(prior.get(0), operatorId);
        boolean department = "department".equals(request.kind());
        if (!department && !"supplier".equals(request.kind())) throw new IllegalArgumentException("请选择退货类型");
        if (request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw new IllegalArgumentException("请输入退货原因，最多500字");
        }
        if (request.items() == null || request.items().isEmpty()) throw new IllegalArgumentException("请选择退货库存");
        if (request.selectionType() == null || !List.of("batch", "trace", "package").contains(request.selectionType())) {
            throw new IllegalArgumentException("请选择退货库存类型");
        }
        var source = sourceWarehouse(request.sourceWarehouseId());
        if (department) {
            if (!String.valueOf(source.get("type")).contains("二级")) throw new IllegalArgumentException("科室退库须从二级库发起");
            if (source.get("deptId") == null) throw new IllegalArgumentException("请选择已关联科室的二级库");
            requireCenter(request.destinationWarehouseId(), source.get("campus"));
        } else {
            if (request.supplierId() == null) throw new IllegalArgumentException("请选择退货供应商");
        }

        var seen = new HashSet<Long>();
        var products = new ArrayList<Long>();
        for (Selection selection : request.items()) {
            if (selection == null || !seen.add(selection.id())) throw new IllegalArgumentException("退货库存不能重复");
            QuantityRules.positive(selection.quantity(), "退货数量");
            if (!"batch".equals(request.selectionType()) && selection.quantity().compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalArgumentException("定数包和唯一码按件选择");
            }
            products.add(selectedProduct(request.selectionType(), selection.id()));
        }
        pricing.lockProducts(products);

        String no = stock.nextNo(DocumentKind.STOCK_RETURN);
        jdbc.update("""
                INSERT INTO stock_return_order(return_no,return_kind,source_warehouse_id,destination_warehouse_id,
                                               supplier_id,dept_id,created_by,reason,request_key,status)
                VALUES (?,?,?,?,?,?,?,?,?,'pending_confirm')
                ON DUPLICATE KEY UPDATE return_id=LAST_INSERT_ID(return_id)
                """, no, request.kind(), request.sourceWarehouseId(), department ? request.destinationWarehouseId() : null,
                department ? null : request.supplierId(), source.get("deptId"), operatorId, request.reason().trim(), request.requestKey());
        long returnId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        var persisted = requireRow("SELECT return_no,status,created_by FROM stock_return_order WHERE return_id=? FOR UPDATE", returnId);
        if (!no.equals(persisted.get("return_no"))) return submittedResult(persisted, operatorId);
        for (Selection selection : request.items()) {
            switch (request.selectionType()) {
                case "batch" -> reserveLoose(request, returnId, selection);
                case "trace" -> reserveTrace(request, returnId, selection, department);
                case "package" -> reservePackage(request, returnId, selection, no, department);
                default -> throw new IllegalStateException("Invalid selection type");
            }
        }
        if (!department) finalizeOrder(returnId, no, request.sourceWarehouseId(), null, false);
        stock.writeAudit(SOURCE, department ? "reserve" : "supplier_return", returnId, no, request.reason().trim());
        return Map.of("returnNo", no, "status", department ? "pending_confirm" : "confirmed");
    }

    private Map<String, Object> submittedResult(Map<String, Object> row, Long operatorId) {
        if (number(row, "created_by") != operatorId) throw new AccessDeniedException("退货提交标识已被占用");
        return Map.of("returnNo", row.get("return_no"), "status", row.get("status"));
    }

    @Transactional
    public Map<String, Object> confirmWarehouseReturn(String no) {
        var order = requireRow("SELECT * FROM stock_return_order WHERE return_no=? AND return_kind='department' FOR UPDATE", no);
        long sourceId = number(order, "source_warehouse_id");
        var source = sourceWarehouse(sourceId);
        if ("confirmed".equals(order.get("status"))) return Map.of("returnNo", no, "status", "confirmed");
        if (!"pending_confirm".equals(order.get("status"))) throw new IllegalArgumentException("退库单已处理");
        long destination = number(order, "destination_warehouse_id");
        requireCenter(destination, source.get("campus"));
        finalizeOrder(number(order, "return_id"), no, sourceId, destination, true);
        stock.writeAudit(SOURCE, "warehouse_confirm", number(order, "return_id"), no, "中心库确认科室退库");
        return Map.of("returnNo", no, "status", "confirmed");
    }

    @Transactional
    public Map<String, Object> cancelWarehouseReturn(String no) {
        var order = requireRow("SELECT * FROM stock_return_order WHERE return_no=? AND return_kind='department' FOR UPDATE", no);
        if (order.get("dept_id") instanceof Number dept) access.requireDepartment(dept.longValue());
        else if (!access.isUnrestricted()) throw new AccessDeniedException("无权取消该退库单");
        var operator = operators.current();
        if (Integer.valueOf(com.hospital.spd.common.OperatorContext.DATA_SCOPE_SELF).equals(operator.dataScope())
                && (operator.userId() == null || number(order, "created_by") != operator.userId())) {
            throw new AccessDeniedException("只能取消本人登记的退库单");
        }
        if (!"pending_confirm".equals(order.get("status"))) throw new IllegalArgumentException("退库单已处理");
        long id = number(order, "return_id");
        var items = items(id);
        pricing.lockProducts(items.stream().map(row -> number(row, "product_id")).toList());
        var restoredLabels = new HashSet<Long>();
        for (var item : items) {
            stock.releaseLockedToAvailable(number(item, "balance_id"), (BigDecimal) item.get("quantity"));
            if (item.get("label_id") instanceof Number label) {
                if (restoredLabels.add(label.longValue())) {
                    changeReservedPackage(label.longValue(), number(order, "source_warehouse_id"),
                            number(order, "source_warehouse_id"), "signed", no);
                }
            } else if (item.get("trace_code_id") != null) {
                requireReservedTrace(number(item, "trace_code_id"), number(order, "source_warehouse_id"));
                setTrace(number(item, "trace_code_id"), number(order, "source_warehouse_id"), "signed", no, "科室退库撤销");
            }
        }
        jdbc.update("UPDATE stock_return_order SET status='cancelled' WHERE return_id=?", id);
        stock.writeAudit(SOURCE, "cancel", id, no, "取消科室退库预占");
        return Map.of("returnNo", no, "status", "cancelled");
    }

    private void reserveLoose(ReturnRequest request, long returnId, Selection selection) {
        var row = requireRow("""
                SELECT bal.balance_id AS balanceId, bal.product_id AS productId, bal.batch_id AS batchId,
                       b.supplier_id AS supplierId, bal.available_qty AS available,
                       COALESCE(p.is_high_value,0) AS highValue,
                       EXISTS(SELECT 1 FROM inventory_batch_trace_code tc WHERE tc.batch_id=b.batch_id) AS hasTraces
                  FROM inventory_balance bal JOIN inventory_batch b ON b.batch_id=bal.batch_id
                  JOIN product p ON p.product_id=bal.product_id
                 WHERE bal.balance_id=? AND bal.warehouse_id=? AND bal.location_id IS NULL FOR UPDATE
                """, selection.id(), request.sourceWarehouseId());
        requireSupplier(request, row.get("supplierId"));
        if (affirmative(row.get("highValue")) || affirmative(row.get("hasTraces"))) {
            throw new IllegalArgumentException("高值耗材请按唯一码选择退货");
        }
        long batch = number(row, "batchId");
        BigDecimal packageStock = jdbc.queryForObject("""
                SELECT COALESCE(SUM(s.source_qty),0) FROM quota_package_label_source s
                  JOIN quota_package_label q ON q.label_id=s.label_id
                 WHERE s.batch_id=? AND q.warehouse_id=? AND q.status='signed' FOR SHARE
                """, BigDecimal.class, batch, request.sourceWarehouseId());
        if (((BigDecimal) row.get("available")).subtract(packageStock).compareTo(selection.quantity()) < 0) {
            throw new IllegalArgumentException("所选批次可退散货数量不足，定数包库存须按包码选择");
        }
        reserveBalance(selection.id(), selection.quantity());
        insertItem(returnId, number(row, "productId"), batch, selection.id(), null, null, selection.quantity());
    }

    private void reserveTrace(ReturnRequest request, long returnId, Selection selection, boolean department) {
        var row = requireRow("""
                SELECT t.lifecycle_status AS status,b.supplier_id AS supplierId,b.product_id AS productId,b.batch_id AS batchId
                  FROM inventory_batch_trace_code t JOIN inventory_batch b ON b.batch_id=t.batch_id
                  JOIN udi_trace_code u ON u.trace_code_id=t.trace_code_id AND u.trace_scope='high_value'
                 WHERE t.trace_code_id=? AND t.current_warehouse_id=? FOR UPDATE
                """, selection.id(), request.sourceWarehouseId());
        requireSupplier(request, row.get("supplierId"));
        List<String> statuses = department ? List.of("signed") : List.of("in_stock", "signed");
        if (!statuses.contains(String.valueOf(row.get("status")))) throw new IllegalArgumentException("唯一码已被占用或不可退货");
        long product = number(row, "productId");
        long batch = number(row, "batchId");
        long balance = reserve(request.sourceWarehouseId(), product, batch, BigDecimal.ONE);
        if (department) {
            jdbc.update("UPDATE inventory_batch_trace_code SET lifecycle_status='return_reserved' WHERE trace_code_id=?", selection.id());
            jdbc.update("UPDATE udi_trace_code SET current_status='return_reserved' WHERE trace_code_id=?", selection.id());
        }
        insertItem(returnId, product, batch, balance, selection.id(), null, BigDecimal.ONE);
    }

    private void reservePackage(ReturnRequest request, long returnId, Selection selection, String no, boolean department) {
        var label = requireRow("""
                SELECT product_id AS productId,status,package_quantity AS quantity
                  FROM quota_package_label WHERE label_id=? AND warehouse_id=? FOR UPDATE
                """, selection.id(), request.sourceWarehouseId());
        boolean signed = "signed".equals(label.get("status"));
        if (!signed && (department || !"available".equals(label.get("status")))) {
            throw new IllegalArgumentException("定数包当前状态不可退货");
        }
        var parts = jdbc.queryForList("""
                SELECT s.batch_id AS batchId,s.source_qty AS quantity,b.supplier_id AS supplierId
                  FROM quota_package_label_source s JOIN inventory_batch b ON b.batch_id=s.batch_id
                 WHERE s.label_id=? ORDER BY s.source_id FOR SHARE
                """, selection.id());
        if (parts.isEmpty() || parts.stream().map(row -> (BigDecimal) row.get("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo((BigDecimal) label.get("quantity")) != 0) throw new IllegalArgumentException("定数包来源数量不完整");
        for (var part : parts) requireSupplier(request, part.get("supplierId"));
        long trace = packages.ensureTrace(selection.id());
        long product = number(label, "productId");
        for (var part : parts) {
            long batch = number(part, "batchId");
            BigDecimal quantity = (BigDecimal) part.get("quantity");
            if (!signed) {
                Long in = stock.receiveAvailable(request.sourceWarehouseId(), product, batch, quantity,
                        "quota_unpack_in", SOURCE, returnId, "中心库定数包按来源批次退货：" + no);
                link(in, trace, selection.id(), quantity);
            }
            long balance = reserve(request.sourceWarehouseId(), product, batch, quantity);
            insertItem(returnId, product, batch, balance, trace, selection.id(), quantity);
        }
        String status = department ? "return_reserved" : "returned";
        jdbc.update("UPDATE quota_package_label SET status=?,version=version+1 WHERE label_id=?", status, selection.id());
        packages.transitionLabel(selection.id(), status, department ? "stock_return_reserve" : "supplier_return",
                department ? "科室定数包退库预占" : "定数包退还供应商", no, null, null, request.reason(), 90);
    }

    private void finalizeOrder(long id, String no, long source, Long destination, boolean department) {
        var items = items(id);
        if (items.isEmpty()) throw new IllegalArgumentException("退货单缺少库存明细");
        pricing.lockProducts(items.stream().map(row -> number(row, "product_id")).toList());
        var transferredLabels = new HashSet<Long>();
        for (var item : items) {
            long product = number(item, "product_id");
            long batch = number(item, "batch_id");
            BigDecimal quantity = (BigDecimal) item.get("quantity");
            Long trace = item.get("trace_code_id") instanceof Number n ? n.longValue() : null;
            Long label = item.get("label_id") instanceof Number n ? n.longValue() : null;
            if (department && trace != null && label == null) requireReservedTrace(trace, source);
            Long out = stock.consumeLocked(number(item, "balance_id"), source, product, batch, quantity,
                    department ? "warehouse_transfer_out" : "supplier_return_out", SOURCE, id, "确认退货：" + no);
            link(out, trace, label, quantity);
            if (destination != null) {
                Long in = stock.receiveAvailable(destination, product, batch, quantity,
                        "warehouse_transfer_in", SOURCE, id, "中心库确认退库：" + no);
                link(in, trace, label, quantity);
                if (label != null) {
                    long centerBalance = reserve(destination, product, batch, quantity);
                    Long packed = stock.consumeLocked(centerBalance, destination, product, batch, quantity,
                            "quota_pack_out", SOURCE, id, "中心库保留退回定数包：" + no);
                    link(packed, trace, label, quantity);
                    transferredLabels.add(label);
                }
            }
            if (trace != null && label == null) setTrace(trace, destination, department ? "in_stock" : "supplier_returned", no,
                    department ? "中心库确认退库" : "高值耗材退还供应商");
        }
        for (Long label : transferredLabels) changeReservedPackage(label, source, destination, "available", no);
        jdbc.update("UPDATE stock_return_order SET status='confirmed',confirm_time=NOW() WHERE return_id=?", id);
    }

    private void changeReservedPackage(long label, long source, long destination, String status, String no) {
        int changed = jdbc.update("""
                UPDATE quota_package_label SET warehouse_id=?,status=?,version=version+1
                 WHERE label_id=? AND warehouse_id=? AND status='return_reserved'
                """, destination, status, label, source);
        if (changed != 1) throw new IllegalArgumentException("退库定数包预占状态已变化，请核对库存");
        boolean moved = source != destination;
        String location = null;
        if (moved) {
            jdbc.update("UPDATE quota_package_label_source SET warehouse_id=? WHERE label_id=?", destination, label);
            jdbc.update("UPDATE udi_trace_code SET current_department=NULL WHERE trace_code_id=(SELECT trace_code_id FROM quota_package_label WHERE label_id=?)", label);
            location = jdbc.queryForObject("SELECT warehouse_name FROM warehouse WHERE warehouse_id=?", String.class, destination);
        }
        packages.transitionLabel(label, status, moved ? "stock_return_confirm" : "stock_return_cancel",
                moved ? "中心库确认定数包退库" : "撤销定数包退库预占", no, location, null, null, 90);
    }

    private void requireReservedTrace(long trace, long warehouse) {
        if (jdbc.queryForList("""
                SELECT trace_code_id FROM inventory_batch_trace_code WHERE trace_code_id=? AND current_warehouse_id=?
                   AND lifecycle_status='return_reserved' FOR UPDATE
                """, Long.class, trace, warehouse).isEmpty()) {
            throw new IllegalArgumentException("退库唯一码预占状态已变化，请核对库存");
        }
    }

    private void setTrace(long trace, Long warehouse, String status, String no, String eventName) {
        jdbc.update("UPDATE inventory_batch_trace_code SET current_warehouse_id=?,lifecycle_status=? WHERE trace_code_id=?",
                warehouse, status, trace);
        jdbc.update("""
                UPDATE udi_trace_code u LEFT JOIN warehouse w ON w.warehouse_id=?
                   LEFT JOIN sys_dept d ON d.dept_id=w.dept_id
                   SET u.current_status=?,u.current_location=w.warehouse_name,u.current_department=d.dept_name,
                       u.last_event_name=?,u.last_event_time=NOW()
                 WHERE u.trace_code_id=?
                """, warehouse, status, eventName, trace);
        jdbc.update("""
                INSERT INTO udi_trace_event(trace_code_id,event_no,event_type,event_name,biz_no,status,event_time,sort_order)
                VALUES (?,?,'stock_return',?,?,'done',NOW(),90)
                """, trace, stock.nextNo(DocumentKind.UDI_TRACE_EVENT), eventName, no);
    }

    private long selectedProduct(String type, long id) {
        String sql = switch (type) {
            case "batch" -> "SELECT product_id AS productId FROM inventory_balance WHERE balance_id=?";
            case "trace" -> "SELECT b.product_id AS productId FROM inventory_batch_trace_code t JOIN inventory_batch b ON b.batch_id=t.batch_id WHERE t.trace_code_id=?";
            case "package" -> "SELECT product_id AS productId FROM quota_package_label WHERE label_id=?";
            default -> throw new IllegalArgumentException("请选择退货库存类型");
        };
        return number(requireRow(sql, id), "productId");
    }

    private long reserve(long warehouse, long product, long batch, BigDecimal quantity) {
        long balance = number(requireRow("""
                SELECT balance_id AS id FROM inventory_balance WHERE warehouse_id=? AND product_id=? AND batch_id=?
                   AND location_id IS NULL FOR UPDATE
                """, warehouse, product, batch), "id");
        reserveBalance(balance, quantity);
        return balance;
    }

    private void reserveBalance(long balance, BigDecimal quantity) {
        if (jdbc.update("""
                UPDATE inventory_balance SET available_qty=available_qty-?,locked_qty=locked_qty+?
                 WHERE balance_id=? AND available_qty>=?
                """, quantity, quantity, balance, quantity) != 1) {
            throw new IllegalArgumentException("可退库存不足，请重新查询");
        }
    }

    private Map<String, Object> sourceWarehouse(long id) {
        var warehouse = requireRow("""
                SELECT warehouse_type AS type,dept_id AS deptId,campus_name AS campus
                  FROM warehouse WHERE warehouse_id=? AND status=1 AND deleted=0
                """, id);
        String type = String.valueOf(warehouse.get("type"));
        if (!type.contains("一级") && !type.contains("中心") && !type.contains("二级")) {
            throw new IllegalArgumentException("请选择一级库或二级库");
        }
        if (warehouse.get("deptId") instanceof Number dept) access.requireDepartment(dept.longValue());
        else if (!access.isUnrestricted()) throw new AccessDeniedException("当前账号无权操作中心库库存");
        return warehouse;
    }

    private void requireCenter(Long id, Object campus) {
        if (id == null || jdbc.queryForList("""
                SELECT warehouse_id FROM warehouse WHERE warehouse_id=? AND campus_name=? AND status=1 AND deleted=0
                   AND (warehouse_type LIKE '%一级%' OR warehouse_type LIKE '%中心%')
                """, Long.class, id, campus).isEmpty()) {
            throw new IllegalArgumentException("请选择同院区的启用中心库");
        }
    }

    private void requireSupplier(ReturnRequest request, Object actual) {
        if ("supplier".equals(request.kind()) && (!(actual instanceof Number supplier)
                || supplier.longValue() != request.supplierId().longValue())) {
            throw new IllegalArgumentException("库存来源供应商与退货供应商不一致，混供应商定数包须分批处理");
        }
    }

    private void insertItem(long id, long product, long batch, long balance, Long trace, Long label, BigDecimal quantity) {
        jdbc.update("""
                INSERT INTO stock_return_item(return_id,product_id,batch_id,balance_id,trace_code_id,label_id,quantity)
                VALUES (?,?,?,?,?,?,?)
                """, id, product, batch, balance, trace, label, quantity);
    }

    private List<Map<String, Object>> items(long id) {
        return jdbc.queryForList("SELECT * FROM stock_return_item WHERE return_id=? ORDER BY product_id,batch_id,item_id", id);
    }

    private void link(Long event, Long trace, Long label, BigDecimal quantity) {
        if (trace != null) stock.linkInventoryEventTraceCodes(event,
                List.of(new TraceLink(trace, label == null ? "high_value_unit" : "quota_package", quantity)));
    }

    private Map<String, Object> requireRow(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.size() != 1) throw new IllegalArgumentException("退货单或库存记录不存在，请刷新后重试");
        return rows.get(0);
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static boolean affirmative(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() == 1;
    }
}
