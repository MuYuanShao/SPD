package com.hospital.spd.supplychain.service;
import com.hospital.spd.specialty.service.QuotaPackageTraceFlowService;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.supplychain.PackageActionRequest;
import com.hospital.spd.supplychain.PackingTaskRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import static com.hospital.spd.common.service.DocumentKind.*;
import static com.hospital.spd.common.SqlHelper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinates quota package creation, loose-stock reservation, label generation, unpacking, and package events.
 */
@Service
public class PackingTaskService {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;
    private final QuotaPackageTraceFlowService traceFlowService;
    private final QuotaPermissionGuard permissionGuard;

    public PackingTaskService(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this(jdbcTemplate, support,
                new QuotaPackageTraceFlowService(jdbcTemplate, support, OperatorContext::system),
                new QuotaPermissionGuard(jdbcTemplate, OperatorContext::system));
    }

    @Autowired
    public PackingTaskService(JdbcTemplate jdbcTemplate, SupplyChainSupport support,
                              QuotaPackageTraceFlowService traceFlowService,
                              QuotaPermissionGuard permissionGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.traceFlowService = traceFlowService;
        this.permissionGuard = permissionGuard;
    }

    public Map<String, Object> packingOptions() {

        List<Map<String, Object>> warehouses = jdbcTemplate.queryForList("""
                SELECT warehouse_name AS warehouseName, warehouse_type AS warehouseType
                 FROM warehouse
                 WHERE deleted = 0 AND status = 1
                   AND (warehouse_type LIKE '%一级%' OR warehouse_type LIKE '%中心%')
                 ORDER BY warehouse_id
                """);
        Object candidates = packableLooseStock(Map.of("page", "1", "size", "200")).get("rows");
        return Map.of("warehouses", warehouses, "candidates", candidates);
    }

    /** Paged stock projection; quantities remain owned by inventory movement and reservations. */
    public Map<String, Object> packableLooseStock(Map<String, String> params) {
        PageRequest page = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder filters = new StringBuilder();
        appendLike(filters, args, "p.product_code", params.get("productCode"));
        appendLike(filters, args, "p.product_name", params.get("productName"));
        appendLike(filters, args, "w.warehouse_name", params.get("warehouseName"));
        String query = """
                SELECT w.warehouse_name AS warehouseName, p.product_code AS productCode,
                       p.product_name AS productName, ib.system_batch_no AS systemBatchNo,
                       ib.production_batch_no AS productionBatchNo, DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       ib.batch_unit_price AS batchUnitPrice, SUM(bal.available_qty) AS availableQty
                 FROM inventory_balance bal
                  JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
                  JOIN product p ON p.product_id = bal.product_id
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                 WHERE p.is_quota_managed = 1 AND p.is_high_value = 0 AND p.is_cold_chain = 0
                   AND w.deleted = 0 AND w.status = 1
                   AND (w.warehouse_type LIKE '%一级%' OR w.warehouse_type LIKE '%中心%')
                   AND p.deleted = 0 AND p.status = 1
                   AND bal.available_qty > 0

                """ + filters + " GROUP BY w.warehouse_id, p.product_id, ib.batch_id";
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + query + ") stock", Long.class, args.toArray());
        args.add(page.size());
        args.add(page.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query
                + " ORDER BY p.product_name, w.warehouse_name, ib.expire_date, ib.batch_id LIMIT ? OFFSET ?", args.toArray());
        return PageResponse.of(rows, total == null ? 0L : total, page);
    }

    public Map<String, Object> tasks(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendLike(where, args, "qpt.task_no", params.get("taskNo"));
        appendLike(where, args, "t.template_code", params.get("templateCode"));
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));
        appendLike(where, args, "w.warehouse_name", params.get("warehouseName"));
        if (!isBlank(params.get("status"))) {
            where.append(" AND qpt.status = ?");
            args.add(params.get("status").trim());
        }
        String fromClause = """
                  FROM quota_packing_task qpt
                  JOIN quota_package_template t ON t.template_id = qpt.template_id
                  JOIN warehouse w ON w.warehouse_id = qpt.warehouse_id
                  JOIN product p ON p.product_id = qpt.product_id
                  LEFT JOIN quota_packing_task_reservation qptr ON qptr.task_id = qpt.task_id
                  LEFT JOIN inventory_batch ib ON ib.batch_id = qptr.batch_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT qpt.task_id) " + fromClause + where,
                Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT qpt.task_no AS taskNo, qpt.status, t.template_code AS templateCode,
                       t.template_name AS templateName, w.warehouse_name AS warehouseName,
                       p.product_code AS productCode, p.product_name AS productName,
                       qpt.package_count AS packageCount, qpt.package_quantity AS packageQuantity,
                       qpt.planned_loose_qty AS plannedLooseQty, qpt.reserved_loose_qty AS reservedLooseQty,
                       qpt.remark, DATE_FORMAT(qpt.create_time, '%Y-%m-%d %H:%i') AS createTime,
                       DATE_FORMAT(qpt.confirm_time, '%Y-%m-%d %H:%i') AS confirmTime,
                       DATE_FORMAT(qpt.cancel_time, '%Y-%m-%d %H:%i') AS cancelTime,
                       GROUP_CONCAT(CONCAT(ib.system_batch_no, ':',
                            TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM qptr.reserved_qty + 0)))
                            ORDER BY ib.expire_date SEPARATOR '; ') AS reservationSummary
                  FROM quota_packing_task qpt
                  JOIN quota_package_template t ON t.template_id = qpt.template_id
                  JOIN warehouse w ON w.warehouse_id = qpt.warehouse_id
                  JOIN product p ON p.product_id = qpt.product_id
                  LEFT JOIN quota_packing_task_reservation qptr ON qptr.task_id = qpt.task_id
                  LEFT JOIN inventory_batch ib ON ib.batch_id = qptr.batch_id
                """ + where + " GROUP BY qpt.task_id ORDER BY qpt.create_time DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    @Transactional
    public Map<String, Object> createTask(PackingTaskRequest request) {
        permissionGuard.require("quota-packing-task:create");

        BigDecimal packageCount = positiveWholeNumber(request.packageCount(), "packing package count must be a whole number greater than zero");
        Map<String, Object> template = findTemplate(request.templateCode());
        Long warehouseId = findWarehouseId(request.warehouseName());
        BigDecimal packageQuantity = (BigDecimal) template.get("quantity");
        Long productId = ((Number) template.get("productId")).longValue();
        BigDecimal availableQty = availableLoose(warehouseId, productId);
        BigDecimal packableCount = availableQty
                .divideToIntegralValue(packageQuantity)
                .min(packageCount);
        if (packableCount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("loose stock is insufficient for one package");
        }
        if (packableCount.compareTo(packageCount) < 0) {
            if (!request.allowPartial()
                    || request.expectedPackableCount() == null
                    || request.expectedPackableCount().compareTo(packableCount) != 0) {
                Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("created", false);
                preview.put("requiresConfirmation", true);
                preview.put("requestedPackageCount", packageCount);
                preview.put("packablePackageCount", packableCount);
                preview.put("availableLooseQty", availableQty);
                preview.put("shortagePackageCount", packageCount.subtract(packableCount));
                return preview;
            }
        }
        BigDecimal plannedLooseQty = packageQuantity.multiply(packableCount);
        String taskNo = support.nextNo(QUOTA_PACKING_TASK);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO quota_packing_task (
                      task_no, template_id, warehouse_id, product_id, package_count, package_quantity,
                      planned_loose_qty, reserved_loose_qty, status, remark
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending_confirm', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, taskNo);
            ps.setLong(2, ((Number) template.get("templateId")).longValue());
            ps.setLong(3, warehouseId);
            ps.setLong(4, productId);
            ps.setBigDecimal(5, packableCount);
            ps.setBigDecimal(6, packageQuantity);
            ps.setBigDecimal(7, plannedLooseQty);
            ps.setBigDecimal(8, plannedLooseQty);
            ps.setString(9, nullIfBlank(request.remark()));
            return ps;
        }, keyHolder);
        Long taskId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        reserveLooseFifo(taskId, warehouseId, productId, plannedLooseQty);
        support.writeAudit("quota_package", "create_quota_pack_task", taskId, taskNo, "create packing task and reserve loose stock");
        return Map.of(
                "created", true,
                "requiresConfirmation", false,
                "taskNo", taskNo,
                "requestedPackageCount", packageCount,
                "packageCount", packableCount,
                "plannedLooseQty", plannedLooseQty,
                "reservedLooseQty", plannedLooseQty);
    }

    @Transactional
    public Map<String, Object> confirmTask(String taskNo) {
        permissionGuard.require("quota-packing-task:confirm");

        Map<String, Object> task = findPackingTaskForUpdate(taskNo);
        if (!"pending_confirm".equals(String.valueOf(task.get("status")))) {
            throw new IllegalArgumentException("only pending task can be confirmed");
        }
        Long warehouseId = ((Number) task.get("warehouseId")).longValue();
        validateLockedReservations(((Number) task.get("taskId")).longValue(), (BigDecimal) task.get("plannedLooseQty"));
        List<Map<String, Object>> consumed = consumeReservedLoose(((Number) task.get("taskId")).longValue());
        int packageCount = ((BigDecimal) task.get("packageCount")).intValueExact();
        List<BigDecimal> sourceRemaining = new ArrayList<>();
        for (Map<String, Object> row : consumed) {
            sourceRemaining.add((BigDecimal) row.get("deductQty"));
        }
        int sourceIndex = 0;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < packageCount; i++) {
            String labelNo = support.nextNo(QUOTA_PACKAGE_LABEL);
            Long labelId = insertLabel(labelNo, task);
            traceFlowService.ensureTrace(labelId);
            sourceIndex = insertLabelSources(labelId, consumed, sourceRemaining, sourceIndex,
                    (BigDecimal) task.get("packageQuantity"), warehouseId);
            writePackageEvent(labelId, "pack_confirm", null, "pending_print", (BigDecimal) task.get("packageQuantity"), "pack confirmed and label waits for print");
            labels.add(labelNo);
        }
        int updated = jdbcTemplate.update("""
                UPDATE quota_packing_task
                   SET status = 'confirmed', confirm_time = NOW()
                 WHERE task_no = ? AND status = 'pending_confirm'
                """, taskNo);
        if (updated != 1) {
            throw new IllegalArgumentException("packing task status changed, please refresh and retry");
        }
        support.writeAudit("quota_package", "confirm_quota_pack_task", ((Number) task.get("taskId")).longValue(), taskNo, "confirm packing task and generate labels");
        return Map.of("taskNo", taskNo, "labels", labels);
    }

    @Transactional
    public Map<String, Object> cancelTask(String taskNo, PackageActionRequest request) {
        permissionGuard.require("quota-packing-task:cancel");

        Map<String, Object> task = findPackingTaskForUpdate(taskNo);
        if (!"pending_confirm".equals(String.valueOf(task.get("status")))) {
            throw new IllegalArgumentException("only pending task can be cancelled");
        }
        Long taskId = ((Number) task.get("taskId")).longValue();
        releaseReservations(taskId);
        int updated = jdbcTemplate.update("""
                UPDATE quota_packing_task
                   SET status = 'cancelled', reserved_loose_qty = 0, cancel_time = NOW(), remark = COALESCE(?, remark)
                 WHERE task_id = ? AND status = 'pending_confirm'
                """, nullIfBlank(request == null ? null : request.reason()), taskId);
        if (updated != 1) throw new IllegalArgumentException("packing task status changed, please refresh and retry");
        support.writeAudit("quota_package", "cancel_quota_pack_task", taskId, taskNo, "cancel packing task and release reservation");
        return Map.of("taskNo", taskNo, "status", "cancelled");
    }

    @Transactional
    public Map<String, Object> terminateTask(String taskNo, PackageActionRequest request) {
        permissionGuard.require("quota-packing-task:terminate");

        Map<String, Object> task = findPackingTaskForUpdate(taskNo);
        String status = String.valueOf(task.get("status"));
        if (!"pending_confirm".equals(status) && !"need_recalculate".equals(status) && !"confirmed".equals(status)) {
            throw new IllegalArgumentException("only pending, recalculating, or confirmed task can be terminated");
        }

        Long taskId = ((Number) task.get("taskId")).longValue();
        String reason = isBlank(request == null ? null : request.reason())
                ? "terminate packing task and rollback stock to loose inventory"
                : request.reason().trim();
        BigDecimal restoredLooseQty = BigDecimal.ZERO;
        if ("confirmed".equals(status)) {
            Integer irreversible = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM quota_package_label
                     WHERE task_id = ? AND status NOT IN ('pending_print', 'available', 'void')
                     FOR UPDATE
                    """, Integer.class, taskId);
            if (irreversible != null && irreversible > 0) {
                throw new IllegalArgumentException("已有定数包进入配送、签收、消耗或结算流程，不能终止");
            }
            restoredLooseQty = restoreConfirmedTaskLabels(task, reason);
            jdbcTemplate.update("""
                    UPDATE quota_packing_task_reservation
                       SET status = 'terminated'
                     WHERE task_id = ? AND status = 'consumed'
                    """, taskId);
        } else {
            releaseReservations(taskId);
            restoredLooseQty = (BigDecimal) task.get("reservedLooseQty");
        }

        jdbcTemplate.update("""
                UPDATE quota_packing_task
                   SET status = 'terminated', reserved_loose_qty = 0, cancel_time = NOW(), remark = COALESCE(?, remark)
                 WHERE task_id = ? AND status = ?
                """, reason, taskId, status);
        support.writeAudit("quota_package", "terminate_quota_pack_task", taskId, taskNo,
                "terminate packing task and rollback stock to loose inventory");
        return Map.of("taskNo", taskNo, "status", "terminated", "restoredLooseQty", restoredLooseQty);
    }

    @Transactional
    public Map<String, Object> recalculateTask(String taskNo) {
        permissionGuard.require("quota-packing-task:recalculate");

        Map<String, Object> task = findPackingTaskForUpdate(taskNo);
        String status = String.valueOf(task.get("status"));
        if (!"pending_confirm".equals(status) && !"need_recalculate".equals(status)) {
            throw new IllegalArgumentException("only pending task can be recalculated");
        }
        Long taskId = ((Number) task.get("taskId")).longValue();
        Long warehouseId = ((Number) task.get("warehouseId")).longValue();
        Long productId = ((Number) task.get("productId")).longValue();
        BigDecimal plannedLooseQty = (BigDecimal) task.get("plannedLooseQty");
        releaseReservations(taskId);
        if (availableLoose(warehouseId, productId).compareTo(plannedLooseQty) < 0) {
            jdbcTemplate.update("UPDATE quota_packing_task SET status = 'need_recalculate', reserved_loose_qty = 0 WHERE task_id = ?", taskId);
            return Map.of("taskNo", taskNo, "status", "need_recalculate", "reservedLooseQty", BigDecimal.ZERO);
        }
        reserveLooseFifo(taskId, warehouseId, productId, plannedLooseQty);
        jdbcTemplate.update("UPDATE quota_packing_task SET status = 'pending_confirm', reserved_loose_qty = ? WHERE task_id = ?",
                plannedLooseQty, taskId);
        support.writeAudit("quota_package", "recalculate_quota_pack_task", taskId, taskNo, "recalculate packing reservation");
        return Map.of("taskNo", taskNo, "reservedLooseQty", plannedLooseQty);
    }

    public List<Map<String, Object>> taskReservations(String taskNo) {

        List<Long> taskIds = jdbcTemplate.queryForList("SELECT task_id FROM quota_packing_task WHERE task_no = ?",
                Long.class, taskNo.trim());
        if (taskIds.isEmpty()) {
            throw new IllegalArgumentException("packing task " + taskNo + " does not exist");
        }
        Long taskId = taskIds.get(0);
        return jdbcTemplate.queryForList("""
                SELECT qptr.reservation_id AS reservationId, ib.system_batch_no AS systemBatchNo,
                       ib.production_batch_no AS productionBatchNo, DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       qptr.reserved_qty AS reservedQty, qptr.unit_price AS unitPrice,
                       DATE_FORMAT(qptr.create_time, '%Y-%m-%d %H:%i') AS createTime
                  FROM quota_packing_task_reservation qptr
                  JOIN inventory_batch ib ON ib.batch_id = qptr.batch_id
                 WHERE qptr.task_id = ?
                 ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id
                """, taskId);
    }

    public Map<String, Object> labels(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                 WHERE 1 = 1
                   AND qpl.status NOT IN ('consumed', 'settled')
                """);
        appendLike(where, args, "qpl.label_no", params.get("labelNo"));
        appendLike(where, args, "t.template_code", params.get("templateCode"));
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));
        appendLike(where, args, "d.dept_name", params.get("deptName"));
        if (!isBlank(params.get("status"))) {
            where.append(" AND qpl.status = ?");
            args.add(params.get("status").trim());
        }

        String fromClause = """
                  FROM quota_package_label qpl
                  JOIN quota_package_template t ON t.template_id = qpl.template_id
                  JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id
                  JOIN product p ON p.product_id = qpl.product_id
                  LEFT JOIN quota_package_label_source qpls ON qpls.label_id = qpl.label_id
                  LEFT JOIN inventory_batch ib ON ib.batch_id = qpls.batch_id
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT qpl.label_id) " + fromClause + where,
                Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT qpl.label_no AS labelNo, qpl.status, t.template_code AS templateCode,
                       t.template_name AS templateName, w.warehouse_name AS warehouseName,
                       p.product_code AS productCode, p.product_name AS productName,
                       qpl.package_quantity AS packageQuantity, qpl.print_count AS printCount,
                       DATE_FORMAT(qpl.create_time, '%Y-%m-%d %H:%i') AS createTime,
                       GROUP_CONCAT(CONCAT(ib.system_batch_no, ':',
                            TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM qpls.source_qty + 0)))
                            ORDER BY ib.expire_date SEPARATOR '; ') AS sourceBatches
                  FROM quota_package_label qpl
                  JOIN quota_package_template t ON t.template_id = qpl.template_id
                  JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id
                  JOIN product p ON p.product_id = qpl.product_id
                  LEFT JOIN quota_package_label_source qpls ON qpls.label_id = qpl.label_id
                  LEFT JOIN inventory_batch ib ON ib.batch_id = qpls.batch_id
                """ + where + " GROUP BY qpl.label_id ORDER BY qpl.create_time DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    @Transactional
    public Map<String, Object> printLabel(String labelNo) {
        permissionGuard.require("quota-package-label:print");
        Map<String, Object> label = jdbcTemplate.queryForMap("""
                SELECT label_id AS labelId, label_no AS labelNo, status, package_quantity AS packageQuantity,
                       print_count AS printCount
                  FROM quota_package_label
                 WHERE label_no = ?
                   FOR UPDATE
                """, labelNo);
        String statusBefore = String.valueOf(label.get("status"));
        if (!"pending_print".equals(statusBefore) && !"available".equals(statusBefore)) {
            throw new IllegalArgumentException("only pending or available package label can be printed");
        }
        Long labelId = ((Number) label.get("labelId")).longValue();
        jdbcTemplate.update("""
                UPDATE quota_package_label
                   SET status = 'available', print_count = print_count + 1, version = version + 1
                 WHERE label_id = ? AND status IN ('pending_print', 'available')
                """, labelId);
        writePackageEvent(labelId, "label_print", statusBefore, "available",
                (BigDecimal) label.get("packageQuantity"), "print quota package label");
        traceFlowService.transitionLabel(labelId, "in_stock", "quota_pack_print", "定数包标签打印",
                labelNo, null, null, "打印定数包唯一码标签", 20);
        support.writeAudit("quota_package", "print_quota_label", labelId, labelNo, "print quota package label");
        Integer printCount = label.get("printCount") instanceof Number number ? number.intValue() + 1 : 1;
        return Map.of("labelNo", labelNo, "status", "available", "printCount", printCount);
    }

    @Transactional
    public Map<String, Object> unpack(String labelNo, PackageActionRequest request) {
        permissionGuard.require("quota-label-unpack:write");

        Map<String, Object> label = jdbcTemplate.queryForMap("""
                SELECT label_id AS labelId, label_no AS labelNo, status, warehouse_id AS warehouseId,
                       product_id AS productId, package_quantity AS packageQuantity
                  FROM quota_package_label
                 WHERE label_no = ?
                   FOR UPDATE
                """, labelNo);
        if (!"available".equals(String.valueOf(label.get("status")))) {
            throw new IllegalArgumentException("only available package label can be unpacked");
        }
        Long labelId = ((Number) label.get("labelId")).longValue();
        List<Map<String, Object>> sources = jdbcTemplate.queryForList("""
                SELECT qpls.batch_id AS batchId, qpls.source_qty AS sourceQty, qpls.warehouse_id AS warehouseId
                  FROM quota_package_label_source qpls
                 WHERE qpls.label_id = ?
                """, labelId);
        if (sources.isEmpty()) {
            throw new IllegalStateException("定数包标签缺少来源批次，不能解包");
        }
        String reason = isBlank(request == null ? null : request.reason())
                ? "unpack package label back to loose stock"
                : request.reason().trim();
        BigDecimal restoredLooseQty = BigDecimal.ZERO;
        for (Map<String, Object> source : sources) {
            Long warehouseId = ((Number) source.get("warehouseId")).longValue();
            Long batchId = ((Number) source.get("batchId")).longValue();
            BigDecimal sourceQty = (BigDecimal) source.get("sourceQty");
            Long inventoryEventId = support.receiveAvailable(warehouseId, ((Number) label.get("productId")).longValue(), batchId, sourceQty,
                    "quota_unpack_in", "quota_package_label", labelId, reason);
            support.linkInventoryEventTraceCodes(inventoryEventId, List.of(
                    new com.hospital.spd.common.service.InventoryEventCommand.TraceLink(
                            traceFlowService.ensureTrace(labelId), "quota_package", sourceQty)));
            restoredLooseQty = restoredLooseQty.add(sourceQty);
        }
        jdbcTemplate.update("""
                UPDATE quota_package_label
                   SET status = 'void', version = version + 1
                 WHERE label_id = ? AND status = 'available'
                """, labelId);
        writePackageEvent(labelId, "unpack_to_loose", "available", "void", (BigDecimal) label.get("packageQuantity"),
                reason);
        traceFlowService.transitionLabel(labelId, "unpacked", "quota_unpack", "定数包解包",
                labelNo, null, null, reason, 30);
        support.writeAudit("quota_package", "unpack_quota_label", labelId, labelNo, reason);
        return Map.of("labelNo", labelNo, "status", "void", "restoredLooseQty", restoredLooseQty);
    }

    public Map<String, Object> packageEvents(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendLike(where, args, "qpe.event_no", params.get("eventNo"));
        appendLike(where, args, "qpl.label_no", params.get("labelNo"));
        if (!isBlank(params.get("eventType"))) {
            where.append(" AND qpe.event_type = ?");
            args.add(params.get("eventType").trim());
        }
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM quota_package_event qpe
                LEFT JOIN quota_package_label qpl ON qpl.label_id = qpe.label_id
                """ + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT qpe.event_no AS eventNo, qpl.label_no AS labelNo, qpe.event_type AS eventType,
                       qpe.status_before AS statusBefore, qpe.status_after AS statusAfter,
                       qpe.qty_change AS qtyChange, qpe.remark,
                       DATE_FORMAT(qpe.event_time, '%Y-%m-%d %H:%i') AS eventTime
                  FROM quota_package_event qpe
                  LEFT JOIN quota_package_label qpl ON qpl.label_id = qpe.label_id
                """ + where + """
                 ORDER BY qpe.event_time DESC
                 LIMIT ? OFFSET ?
                """, queryArgs.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, pageReq);
    }

    // ---- Private helpers ----

    /**
     * 按验收单号查询可分配的散货库存（该验收单合格收货后尚未打包的可用余额）。
     */
    public List<Map<String, Object>> receivingLooseStock(String receivingNo) {
        if (isBlank(receivingNo)) {
            throw new IllegalArgumentException("验收单号为必填项");
        }
        return jdbcTemplate.queryForList("""
                SELECT ro.receiving_no AS receivingNo, roi.item_id AS receivingItemId,
                       roi.product_id AS productId, ro.warehouse_id AS warehouseId,
                       p.product_code AS productCode, p.product_name AS productName,
                       ib.batch_id AS batchId, ib.system_batch_no AS systemBatchNo,
                       ib.production_batch_no AS productionBatchNo,
                       bal.balance_id AS balanceId, bal.available_qty AS availableQty,
                       ib.batch_unit_price AS unitPrice, p.unit
                  FROM receiving_order ro
                  JOIN receiving_order_item roi ON roi.receiving_order_id = ro.receiving_order_id
                  JOIN inventory_batch ib ON ib.receiving_item_id = roi.item_id
                  JOIN inventory_balance bal ON bal.batch_id = ib.batch_id
                   AND bal.warehouse_id = ro.warehouse_id
                   AND bal.product_id = roi.product_id
                   AND bal.location_id IS NULL
                  JOIN product p ON p.product_id = roi.product_id
                 WHERE ro.receiving_no = ? AND bal.available_qty > 0
                 ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id
                """, receivingNo.trim());
    }

    /**
     * 打包任务确认界面按验收单号分配散货库存：
     * quantity 为空或小于等于 0 表示全部打包分配；部分分配后验收单剩余量保持散货库存。
     */
    @Transactional
    public Map<String, Object> allocateFromReceiving(String taskNo, String receivingNo, BigDecimal quantity) {
        permissionGuard.require("quota-packing-task:create");
        Map<String, Object> task = findPackingTaskForUpdate(taskNo);
        String status = String.valueOf(task.get("status"));
        if (!"pending_confirm".equals(status) && !"need_recalculate".equals(status)) {
            throw new IllegalArgumentException("仅待确认或待重算的打包任务支持按验收单分配");
        }
        Long warehouseId = ((Number) task.get("warehouseId")).longValue();
        Long productId = ((Number) task.get("productId")).longValue();
        Long taskId = ((Number) task.get("taskId")).longValue();

        // 仅分配与任务商品、库房一致的验收单散货；验收单内其它商品/库房散货保持不动
        List<Map<String, Object>> rows = receivingLooseStock(receivingNo).stream()
                .filter(row -> productId.equals(((Number) row.get("productId")).longValue()))
                .filter(row -> warehouseId.equals(((Number) row.get("warehouseId")).longValue()))
                .toList();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("该验收单没有匹配当前打包任务商品与库房的散货库存");
        }
        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("分配数量必须大于零；留空表示分配全部可用数量");
        }
        boolean fullAllocation = quantity == null;
        BigDecimal remaining = fullAllocation
                ? rows.stream().map(row -> (BigDecimal) row.get("availableQty")).reduce(BigDecimal.ZERO, BigDecimal::add)
                : quantity;
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("该验收单没有可分配的散货库存");
        }

        BigDecimal allocatedTotal = BigDecimal.ZERO;
        List<Long> allocatedBalanceIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = (BigDecimal) row.get("availableQty");
            BigDecimal allocate = available.min(remaining);
            Long balanceId = ((Number) row.get("balanceId")).longValue();
            int affected = jdbcTemplate.update("""
                    UPDATE inventory_balance
                       SET available_qty = available_qty - ?, locked_qty = locked_qty + ?
                     WHERE balance_id = ? AND available_qty >= ?
                    """, allocate, allocate, balanceId, allocate);
            if (affected != 1) {
                throw new IllegalArgumentException("验收单散货库存不足，分配失败");
            }
            jdbcTemplate.update("""
                    INSERT INTO quota_packing_task_reservation (
                      task_id, balance_id, batch_id, reserved_qty, unit_price, status,
                      receiving_no, receiving_item_id
                    ) VALUES (?, ?, ?, ?, ?, 'reserved', ?, ?)
                    """, taskId, balanceId, row.get("batchId"), allocate,
                    row.get("unitPrice"), receivingNo.trim(), row.get("receivingItemId"));
            allocatedBalanceIds.add(balanceId);
            allocatedTotal = allocatedTotal.add(allocate);
            remaining = remaining.subtract(allocate);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("验收单散货库存不足，仅可分配 " + allocatedTotal + "，剩余 " + remaining + " 无法满足");
        }

        BigDecimal packageQuantity = (BigDecimal) task.get("packageQuantity");
        if (allocatedTotal.remainder(packageQuantity).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("分配数量必须是每包数量的整数倍（每包 " + packageQuantity + "）");
        }
        BigDecimal addedPackages = allocatedTotal.divide(packageQuantity);
        BigDecimal currentReserved = (BigDecimal) task.get("reservedLooseQty");
        BigDecimal newReserved = currentReserved.add(allocatedTotal);
        jdbcTemplate.update("""
                UPDATE quota_packing_task
                   SET reserved_loose_qty = ?, planned_loose_qty = ?, package_count = package_count + ?,
                       status = 'pending_confirm'
                 WHERE task_id = ?
                """, newReserved, newReserved, addedPackages, taskId);
        BigDecimal taskReserved = jdbcTemplate.queryForObject(
                "SELECT reserved_loose_qty FROM quota_packing_task WHERE task_id = ?", BigDecimal.class, taskId);
        return Map.of(
                "taskNo", taskNo,
                "receivingNo", receivingNo.trim(),
                "allocatedQty", allocatedTotal,
                "reservedLooseQty", taskReserved,
                "fullAllocation", fullAllocation
        );
    }

    /** 打包任务的验收单分配明细（含来源验收单号） */
    public List<Map<String, Object>> allocations(String taskNo) {
        Map<String, Object> task = findPackingTask(taskNo);
        return jdbcTemplate.queryForList("""
                SELECT qptr.reservation_id AS reservationId, qptr.receiving_no AS receivingNo,
                       qptr.receiving_item_id AS receivingItemId,
                       ib.system_batch_no AS systemBatchNo, ib.production_batch_no AS productionBatchNo,
                       p.product_code AS productCode, p.product_name AS productName,
                       qptr.reserved_qty AS reservedQty, qptr.unit_price AS unitPrice, qptr.status
                  FROM quota_packing_task_reservation qptr
                  JOIN inventory_batch ib ON ib.batch_id = qptr.batch_id
                  JOIN product p ON p.product_id = ib.product_id
                 WHERE qptr.task_id = ? AND qptr.status IN ('reserved', 'consumed')
                 ORDER BY qptr.reservation_id
                """, task.get("taskId"));
    }

    private Map<String, Object> findTemplate(String templateCode) {
        List<Map<String, Object>> templates = jdbcTemplate.queryForList("""
                SELECT t.template_id AS templateId, t.template_code AS templateCode, t.template_name AS templateName,
                       ti.product_id AS productId, ti.quantity, ti.unit
                  FROM quota_package_template t
                  JOIN quota_package_template_item ti ON ti.template_id = t.template_id
                 WHERE t.template_code = ? AND t.status = 1 AND t.deleted = 0
                   AND t.is_current = 1 AND ti.deleted = 0
                """, templateCode.trim());
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("quota package template " + templateCode + " does not exist or is disabled");
        }
        return templates.get(0);
    }

    private Long findWarehouseId(String warehouseName) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT warehouse_id
                 FROM warehouse
                 WHERE warehouse_name = ? AND deleted = 0 AND status = 1
                   AND (warehouse_type LIKE '%一级%' OR warehouse_type LIKE '%中心%')
                 LIMIT 1
                """, Long.class, warehouseName.trim());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("warehouse " + warehouseName + " does not exist or is disabled");
        }
        return ids.get(0);
    }

    private BigDecimal availableLoose(Long warehouseId, Long productId) {
        BigDecimal value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(available_qty), 0)
                  FROM inventory_balance
                 WHERE warehouse_id = ? AND product_id = ?
                """, BigDecimal.class, warehouseId, productId);
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<String, Object> findPackingTask(String taskNo) {
        return findPackingTask(taskNo, false);
    }

    private Map<String, Object> findPackingTaskForUpdate(String taskNo) {
        return findPackingTask(taskNo, true);
    }

    private Map<String, Object> findPackingTask(String taskNo, boolean forUpdate) {
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList("""
                SELECT task_id AS taskId, task_no AS taskNo, status, template_id AS templateId,
                       warehouse_id AS warehouseId, product_id AS productId,
                       package_count AS packageCount, package_quantity AS packageQuantity,
                       planned_loose_qty AS plannedLooseQty, reserved_loose_qty AS reservedLooseQty
                  FROM quota_packing_task
                 WHERE task_no = ?
                """ + (forUpdate ? " FOR UPDATE" : ""), taskNo.trim());
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("packing task " + taskNo + " does not exist");
        }
        return tasks.get(0);
    }

    private void validateLockedReservations(Long taskId, BigDecimal plannedLooseQty) {
        List<Map<String, Object>> reservations = jdbcTemplate.queryForList("""
                SELECT qptr.reservation_id AS reservationId, qptr.reserved_qty AS reservedQty,
                       bal.locked_qty AS lockedQty
                  FROM quota_packing_task_reservation qptr
                  JOIN inventory_balance bal ON bal.balance_id = qptr.balance_id
                 WHERE qptr.task_id = ? AND qptr.status = 'reserved'
                 ORDER BY qptr.reservation_id
                 FOR UPDATE
                """, taskId);
        BigDecimal reservedTotal = BigDecimal.ZERO;
        for (Map<String, Object> reservation : reservations) {
            BigDecimal reservedQty = (BigDecimal) reservation.get("reservedQty");
            BigDecimal lockedQty = (BigDecimal) reservation.get("lockedQty");
            if (lockedQty.compareTo(reservedQty) < 0) {
                throw new IllegalArgumentException("reserved loose stock is not locked, please recalculate packing task");
            }
            reservedTotal = reservedTotal.add(reservedQty);
        }
        if (reservedTotal.compareTo(plannedLooseQty) != 0) {
            throw new IllegalArgumentException("packing task reservation is incomplete, please recalculate packing task");
        }
    }

    private void reserveLooseFifo(Long taskId, Long warehouseId, Long productId, BigDecimal requiredQty) {
        List<SupplyChainSupport.InventoryReservation> reservations =
                support.reserveAvailableFifo(warehouseId, productId, requiredQty);
        for (SupplyChainSupport.InventoryReservation reservation : reservations) {
            jdbcTemplate.update("""
                    INSERT INTO quota_packing_task_reservation (task_id, balance_id, batch_id, reserved_qty, unit_price, status)
                    VALUES (?, ?, ?, ?, ?, 'reserved')
                    """, taskId, reservation.balanceId(), reservation.batchId(),
                    reservation.quantity(), reservation.unitPrice());
        }
    }

    private List<Map<String, Object>> consumeReservedLoose(Long taskId) {
        List<Map<String, Object>> reservations = jdbcTemplate.queryForList("""
                SELECT reservation_id AS reservationId, balance_id AS balanceId, batch_id AS batchId,
                       reserved_qty AS deductQty, unit_price AS unitPrice
                  FROM quota_packing_task_reservation
                 WHERE task_id = ? AND status = 'reserved'
                 ORDER BY reservation_id
                """, taskId).stream().map(row -> (Map<String, Object>) new java.util.LinkedHashMap<>(row)).toList();
        if (reservations.isEmpty()) {
            throw new IllegalArgumentException("packing task has no reserved loose stock");
        }
        for (Map<String, Object> reservation : reservations) {
            BigDecimal deductQty = (BigDecimal) reservation.get("deductQty");
            Long balanceId = ((Number) reservation.get("balanceId")).longValue();
            Map<String, Object> task = jdbcTemplate.queryForMap("""
                    SELECT warehouse_id AS warehouseId, product_id AS productId
                      FROM quota_packing_task
                     WHERE task_id = ?
                    """, taskId);
            Long inventoryEventId = support.consumeLocked(balanceId,
                    ((Number) task.get("warehouseId")).longValue(), ((Number) task.get("productId")).longValue(),
                    ((Number) reservation.get("batchId")).longValue(), deductQty,
                    "quota_pack_out", "quota_packing_task", taskId,
                    "confirm packing from reserved loose stock");
            reservation.put("inventoryEventId", inventoryEventId);
            jdbcTemplate.update("UPDATE quota_packing_task_reservation SET status = 'consumed' WHERE reservation_id = ?",
                    reservation.get("reservationId"));
        }
        return reservations;
    }

    private int insertLabelSources(Long labelId, List<Map<String, Object>> consumed,
                                   List<BigDecimal> sourceRemaining, int sourceIndex,
                                   BigDecimal packageQuantity, Long warehouseId) {
        BigDecimal remainingPackageQty = packageQuantity;
        while (remainingPackageQty.compareTo(BigDecimal.ZERO) > 0 && sourceIndex < consumed.size()) {
            BigDecimal currentRemaining = sourceRemaining.get(sourceIndex);
            if (currentRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                sourceIndex++;
                continue;
            }
            BigDecimal sourceQty = currentRemaining.min(remainingPackageQty);
            Map<String, Object> row = consumed.get(sourceIndex);
            jdbcTemplate.update("""
                    INSERT INTO quota_package_label_source (label_id, batch_id, source_qty, unit_price, warehouse_id)
                    VALUES (?, ?, ?, ?, ?)
                    """, labelId, row.get("batchId"), sourceQty, row.get("unitPrice"), warehouseId);
            support.linkInventoryEventTraceCodes(((Number) row.get("inventoryEventId")).longValue(), List.of(
                    new com.hospital.spd.common.service.InventoryEventCommand.TraceLink(
                            traceFlowService.ensureTrace(labelId), "quota_package", sourceQty)));
            sourceRemaining.set(sourceIndex, currentRemaining.subtract(sourceQty));
            remainingPackageQty = remainingPackageQty.subtract(sourceQty);
            if (sourceRemaining.get(sourceIndex).compareTo(BigDecimal.ZERO) <= 0) {
                sourceIndex++;
            }
        }
        if (remainingPackageQty.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("packing task source batches cannot cover package quantity");
        }
        return sourceIndex;
    }

    private void releaseReservations(Long taskId) {
        List<Map<String, Object>> reservations = jdbcTemplate.queryForList("""
                SELECT reservation_id AS reservationId, balance_id AS balanceId, reserved_qty AS reservedQty
                  FROM quota_packing_task_reservation
                 WHERE task_id = ? AND status = 'reserved'
                """, taskId);
        for (Map<String, Object> reservation : reservations) {
            BigDecimal qty = (BigDecimal) reservation.get("reservedQty");
            support.releaseLockedToAvailable(((Number) reservation.get("balanceId")).longValue(), qty);
            jdbcTemplate.update("UPDATE quota_packing_task_reservation SET status = 'released' WHERE reservation_id = ?",
                    reservation.get("reservationId"));
        }
    }

    private BigDecimal restoreConfirmedTaskLabels(Map<String, Object> task, String reason) {
        Long taskId = ((Number) task.get("taskId")).longValue();
        Long productId = ((Number) task.get("productId")).longValue();
        List<Map<String, Object>> labels = jdbcTemplate.queryForList("""
                SELECT label_id AS labelId, label_no AS labelNo, status, package_quantity AS packageQuantity
                  FROM quota_package_label
                 WHERE task_id = ? AND status IN ('pending_print', 'available')
                 ORDER BY label_id
                """, taskId);
        BigDecimal restoredLooseQty = BigDecimal.ZERO;
        for (Map<String, Object> label : labels) {
            Long labelId = ((Number) label.get("labelId")).longValue();
            String statusBefore = String.valueOf(label.get("status"));
            List<Map<String, Object>> sources = jdbcTemplate.queryForList("""
                    SELECT batch_id AS batchId, source_qty AS sourceQty, warehouse_id AS warehouseId
                      FROM quota_package_label_source
                     WHERE label_id = ?
                    """, labelId);
            for (Map<String, Object> source : sources) {
                BigDecimal sourceQty = (BigDecimal) source.get("sourceQty");
                support.receiveAvailable(((Number) source.get("warehouseId")).longValue(), productId,
                        ((Number) source.get("batchId")).longValue(), sourceQty,
                        "quota_terminate_in", "quota_packing_task", taskId, reason);
                restoredLooseQty = restoredLooseQty.add(sourceQty);
            }
            jdbcTemplate.update("UPDATE quota_package_label SET status = 'void', version = version + 1 WHERE label_id = ?", labelId);
            writePackageEvent(labelId, "terminate_to_loose", statusBefore, "void",
                    (BigDecimal) label.get("packageQuantity"), reason);
        }
        return restoredLooseQty;
    }

    private Long insertLabel(String labelNo, Map<String, Object> task) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO quota_package_label (
                      label_no, task_id, template_id, warehouse_id, product_id, package_quantity, status
                    ) VALUES (?, ?, ?, ?, ?, ?, 'pending_print')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, labelNo);
            ps.setLong(2, ((Number) task.get("taskId")).longValue());
            ps.setLong(3, ((Number) task.get("templateId")).longValue());
            ps.setLong(4, ((Number) task.get("warehouseId")).longValue());
            ps.setLong(5, ((Number) task.get("productId")).longValue());
            ps.setBigDecimal(6, (BigDecimal) task.get("packageQuantity"));
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private void writePackageEvent(Long labelId, String eventType, String before, String after, BigDecimal qtyChange, String remark) {
        jdbcTemplate.update("""
                INSERT INTO quota_package_event (
                  event_no, label_id, event_type, status_before, status_after, qty_change, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, support.nextNo(QUOTA_PACKAGE_EVENT), labelId, eventType, before, after, qtyChange, remark);
    }

    private static BigDecimal positiveWholeNumber(BigDecimal value, String message) {
        BigDecimal positiveValue = positive(value, message);
        if (positiveValue.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(message);
        }
        return positiveValue;
    }

    private static void appendLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (!isBlank(value)) {
            sql.append(" AND ").append(column).append(" LIKE ?");
            args.add("%" + value.trim() + "%");
        }
    }
}
