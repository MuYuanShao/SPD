package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.supplychain.InventoryEventQuery;
import com.hospital.spd.supplychain.InventoryTransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hospital.spd.common.SqlHelper.appendLike;

/** Reads immutable inventory ledger events without expanding one event into one row per trace code. */
@Service
public class InventoryEventQueryService {

    private static final Set<String> SOURCE_BIZ_TYPES = Set.of(
            "receiving_order", "inventory_stocktaking", "batch_price_adjustment", "spd_delivery_order",
            "department_consumption", "quota_packing_task", "quota_package_label", "high_value_charge",
            "recall_event", "warehouse_transfer");

    private static final String SOURCE_NO_EXPR = """
            (CASE ie.source_biz_type
              WHEN 'receiving_order' THEN (SELECT ro.receiving_no FROM receiving_order ro WHERE ro.receiving_order_id = ie.source_biz_id)
              WHEN 'inventory_stocktaking' THEN (SELECT st.stocktaking_no FROM inventory_stocktaking st WHERE st.stocktaking_id = ie.source_biz_id)
              WHEN 'batch_price_adjustment' THEN (SELECT pa.adjustment_no FROM batch_price_adjustment pa WHERE pa.adjustment_id = ie.source_biz_id)
              WHEN 'spd_delivery_order' THEN (SELECT delivery_no FROM spd_delivery_order WHERE delivery_id = ie.source_biz_id)
              WHEN 'department_consumption' THEN (SELECT consumption_no FROM department_consumption WHERE consumption_id = ie.source_biz_id)
              WHEN 'quota_packing_task' THEN (SELECT task_no FROM quota_packing_task WHERE task_id = ie.source_biz_id)
              WHEN 'quota_package_label' THEN (SELECT label_no FROM quota_package_label WHERE label_id = ie.source_biz_id)
              WHEN 'high_value_charge' THEN (SELECT charge_no FROM high_value_charge WHERE charge_id = ie.source_biz_id)
              WHEN 'recall_event' THEN (SELECT recall_no FROM product_recall_event WHERE recall_id = ie.source_biz_id)
              ELSE CAST(ie.source_biz_id AS CHAR)
            END)""";

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;

    public InventoryEventQueryService(JdbcTemplate jdbcTemplate, OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
    }

    public Map<String, Object> list(InventoryEventQuery query) {
        QueryParts parts = buildQuery(query, true);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_event ie " + parts.where(),
                Long.class, parts.args().toArray());
        PageRequest page = query.pageRequest();
        List<Object> rowArgs = new ArrayList<>(parts.args());
        rowArgs.add(page.size());
        rowArgs.add(page.offset());
        String rowSql = """
                SELECT ie.event_no AS eventNo, ie.event_type AS eventType,
                       ie.transaction_type_code AS transactionTypeCode, ie.event_category AS eventCategory,
                       ie.dept_name_snapshot AS deptName, ie.warehouse_code_snapshot AS warehouseCode,
                       ie.warehouse_name_snapshot AS warehouseName,
                       ie.product_code_snapshot AS productCode, ie.product_name_snapshot AS productName,
                       ie.spec_model_snapshot AS specModel, ie.registration_no_snapshot AS registrationNo,
                       ie.system_batch_no_snapshot AS batchNo,
                       ie.production_batch_no_snapshot AS productionBatchNo,
                       ie.unit_price_snapshot AS unitPrice, ie.unit_snapshot AS unit,
                       ie.qty_change AS qtyChange, ie.amount_snapshot AS amount, ie.qty_after AS qtyAfter,
                       ie.old_unit_price AS oldUnitPrice, ie.new_unit_price AS newUnitPrice,
                       ie.affected_qty_snapshot AS affectedQty, ie.value_change AS valueChange,
                       ie.manufacturer_name_snapshot AS manufacturerName,
                       ie.supplier_name_snapshot AS supplierName,
                       ie.source_biz_type AS sourceBizType, ie.source_biz_id AS sourceBizId,
                       __SOURCE_BIZ_NO__ AS sourceBizNo,
                       (SELECT COUNT(*) FROM inventory_event_trace_code link WHERE link.event_id = ie.event_id) AS traceCount,
                       ie.snapshot_origin AS snapshotOrigin, ie.remark,
                       DATE_FORMAT(ie.event_time, '%Y-%m-%d %H:%i') AS eventTime
                  FROM inventory_event ie
                """.replace("__SOURCE_BIZ_NO__", SOURCE_NO_EXPR) + parts.where()
                + " ORDER BY ie.dept_name_snapshot, ie.event_time DESC, ie.event_id DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(rowSql, rowArgs.toArray());
        rows.forEach(this::decorateTransactionType);
        return PageResponse.of(rows, total == null ? 0 : total, page, loadSummary(parts));
    }

    public Map<String, Object> detail(String eventNo) {
        InventoryEventQuery query = new InventoryEventQuery(eventNo, null, null, null, null, null, null,
                null, null, null, null, null, null, null, new PageRequest(1, 1, 0));
        QueryParts parts = buildQuery(query, true);
        String detailSql = """
                SELECT ie.*, ie.event_no AS eventNo, ie.event_type AS eventType,
                       ie.transaction_type_code AS transactionTypeCode, ie.event_category AS eventCategory,
                       ie.dept_name_snapshot AS deptName, ie.warehouse_code_snapshot AS warehouseCode,
                       ie.warehouse_name_snapshot AS warehouseName,
                       ie.product_code_snapshot AS productCode, ie.product_name_snapshot AS productName,
                       ie.spec_model_snapshot AS specModel, ie.registration_no_snapshot AS registrationNo,
                       ie.system_batch_no_snapshot AS batchNo, ie.production_batch_no_snapshot AS productionBatchNo,
                       ie.unit_price_snapshot AS unitPrice, ie.unit_snapshot AS unit,
                       ie.qty_change AS qtyChange, ie.amount_snapshot AS amount, ie.qty_after AS qtyAfter,
                       ie.affected_qty_snapshot AS affectedQty, ie.value_change AS valueChange,
                       ie.manufacturer_name_snapshot AS manufacturerName, ie.supplier_name_snapshot AS supplierName,
                       ie.source_biz_type AS sourceBizType, ie.source_biz_id AS sourceBizId,
                       __SOURCE_BIZ_NO__ AS sourceBizNo,
                       (SELECT COUNT(*) FROM inventory_event_trace_code link WHERE link.event_id = ie.event_id) AS traceCount,
                       ie.snapshot_origin AS snapshotOrigin,
                       DATE_FORMAT(ie.event_time, '%Y-%m-%d %H:%i:%s') AS eventTime
                  FROM inventory_event ie
                """.replace("__SOURCE_BIZ_NO__", SOURCE_NO_EXPR) + parts.where();
        List<Map<String, Object>> events = jdbcTemplate.queryForList(detailSql, parts.args().toArray());
        if (events.isEmpty()) throw new IllegalArgumentException("库存交易流水不存在或无权查看");
        Map<String, Object> event = new LinkedHashMap<>(events.get(0));
        decorateTransactionType(event);
        event.put("traceCodes", jdbcTemplate.queryForList("""
                SELECT link.trace_code_id AS traceCodeId, link.trace_type AS traceType,
                       link.linked_quantity AS linkedQuantity, utc.trace_scope AS traceScope,
                       utc.unique_code AS uniqueCode, utc.udi_code AS udiCode,
                       utc.package_label_no AS packageLabelNo, utc.current_status AS currentStatus
                  FROM inventory_event_trace_code link
                  JOIN udi_trace_code utc ON utc.trace_code_id = link.trace_code_id
                  JOIN inventory_event ie ON ie.event_id = link.event_id
                 WHERE ie.event_no = ?
                 ORDER BY link.trace_code_id
                """, eventNo));
        return event;
    }

    public Map<String, Object> transactionTypeOptions() {
        return Map.of("rows", InventoryTransactionType.options());
    }

    private QueryParts buildQuery(InventoryEventQuery query, boolean validateDates) {
        if (validateDates) validateDateRange(query.startTime(), query.endTime());
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendLike(where, args, "ie.event_no", query.eventNo());
        appendLike(where, args, "ie.dept_name_snapshot", query.deptName());
        appendLike(where, args, "ie.warehouse_name_snapshot", query.warehouseName());
        appendLike(where, args, "ie.product_code_snapshot", query.productCode());
        appendLike(where, args, "ie.product_name_snapshot", query.productName());
        appendLike(where, args, "ie.system_batch_no_snapshot", query.batchNo());
        appendLike(where, args, "ie.production_batch_no_snapshot", query.productionBatchNo());
        appendLike(where, args, "ie.manufacturer_name_snapshot", query.manufacturerName());
        appendLike(where, args, "ie.supplier_name_snapshot", query.supplierName());
        appendLike(where, args, SOURCE_NO_EXPR, query.sourceBizNo());
        if (query.sourceBizType() != null) {
            if (!SOURCE_BIZ_TYPES.contains(query.sourceBizType())) {
                throw new IllegalArgumentException("不支持的库存流水来源类型：" + query.sourceBizType());
            }
            where.append(" AND ie.source_biz_type = ?");
            args.add(query.sourceBizType());
        }
        InventoryTransactionType transactionType = InventoryTransactionType.fromCodeOrLabel(query.transactionTypeCode());
        if (transactionType != null) {
            where.append(" AND ie.transaction_type_code = ?");
            args.add(transactionType.code());
        }
        if (query.startTime() != null) {
            where.append(" AND ie.event_time >= ?");
            args.add(query.startTime() + " 00:00:00");
        }
        if (query.endTime() != null) {
            where.append(" AND ie.event_time <= ?");
            args.add(query.endTime() + " 23:59:59");
        }
        appendDataScope(where, args);
        return new QueryParts(where.toString(), args);
    }

    private void appendDataScope(StringBuilder where, List<Object> args) {
        OperatorContext operator = operatorContextProvider.current();
        if (operator.canViewAllData()) return;
        where.append(" AND ie.dept_id_snapshot IS NOT NULL");
        if (operator.deptId() == null) {
            where.append(" AND 1 = 0");
            return;
        }
        if (operator.dataScope() == OperatorContext.DATA_SCOPE_SELF) {
            where.append(" AND ie.dept_id_snapshot = ? AND ie.operator_id = ?");
            args.add(operator.deptId());
            args.add(operator.userId());
        } else if (operator.dataScope() == OperatorContext.DATA_SCOPE_DEPT) {
            where.append(" AND ie.dept_id_snapshot = ?");
            args.add(operator.deptId());
        } else if (operator.dataScope() == OperatorContext.DATA_SCOPE_DEPT_AND_CHILDREN) {
            where.append(" AND (ie.dept_id_snapshot = ? OR ie.dept_id_snapshot IN (SELECT dept_id FROM sys_dept WHERE parent_id = ? AND deleted = 0))");
            args.add(operator.deptId());
            args.add(operator.deptId());
        } else if (operator.dataScope() == OperatorContext.DATA_SCOPE_CUSTOM) {
            where.append(" AND ie.dept_id_snapshot IN (SELECT rd.dept_id FROM sys_role_dept rd JOIN sys_user_role ur ON ur.role_id = rd.role_id WHERE ur.user_id = ?)");
            args.add(operator.userId());
        } else {
            where.append(" AND 1 = 0");
        }
    }

    private Map<String, Object> loadSummary(QueryParts parts) {
        return jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN ie.event_category = 'quantity' AND ie.qty_change > 0 THEN ie.qty_change ELSE 0 END), 0) AS inboundQty,
                       COALESCE(SUM(CASE WHEN ie.event_category = 'quantity' AND ie.qty_change < 0 THEN -ie.qty_change ELSE 0 END), 0) AS outboundQty,
                       COALESCE(SUM(CASE WHEN ie.event_category = 'quantity' THEN ie.qty_change ELSE 0 END), 0) AS netQty,
                       COALESCE(SUM(CASE WHEN ie.event_category = 'quantity' THEN ie.amount_snapshot ELSE 0 END), 0) AS movementAmount,
                       COALESCE(SUM(CASE WHEN ie.event_category = 'valuation' THEN ie.value_change ELSE 0 END), 0) AS valuationChange
                  FROM inventory_event ie
                """ + parts.where(), parts.args().toArray());
    }

    private void decorateTransactionType(Map<String, Object> row) {
        InventoryTransactionType type = InventoryTransactionType.fromCodeOrLabel(String.valueOf(row.get("transactionTypeCode")));
        row.put("transactionTypeName", type.label());
        row.put("transactionType", type.label());
    }

    private static void validateDateRange(String start, String end) {
        try {
            LocalDate startDate = start == null ? null : LocalDate.parse(start);
            LocalDate endDate = end == null ? null : LocalDate.parse(end);
            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("结束日期不能早于开始日期");
            }
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("日期格式必须为 yyyy-MM-dd");
        }
    }

    private record QueryParts(String where, List<Object> args) { }
}
