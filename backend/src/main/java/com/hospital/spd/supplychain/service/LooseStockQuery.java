package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.JdbcPageQuery;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/** Shares the available loose-stock calculation between inventory and picking queries. */
final class LooseStockQuery {
    private LooseStockQuery() {}

    private static final String AVAILABLE_QTY = "GREATEST(bal.available_qty - COALESCE(packaged.package_qty, 0), 0)";
    private static final String SELECT = """
                SELECT bal.balance_id AS balanceId, ib.batch_id AS batchId,
                       p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       w.warehouse_name AS warehouseName, p.unit,
                       ib.system_batch_no AS systemBatchNo,
                       ib.production_batch_no AS productionBatchNo,
                       DATE_FORMAT(ib.expire_date, '%Y-%m-%d') AS expireDate,
                       ib.batch_unit_price AS unitPrice,
                       """ + AVAILABLE_QTY + " AS availableQty ";
    private static final String FROM = """
                  FROM inventory_balance bal
                  JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
                    AND (ib.expire_date IS NULL OR ib.expire_date >= CURRENT_DATE)
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
                 WHERE bal.location_id IS NULL AND bal.available_qty > 0
                   AND (w.warehouse_type LIKE '%一级%' OR w.warehouse_type LIKE '%中心%')
                """ + " AND " + AVAILABLE_QTY + " > 0";
    private static final String ORDER_BY = " ORDER BY ib.expire_date IS NULL, ib.expire_date, ib.batch_id, bal.balance_id";

    static PageResponse<Map<String, Object>> fetch(JdbcTemplate jdbc, PageRequest page,
            StringBuilder filters, List<Object> args) {
        return JdbcPageQuery.fetch(jdbc, page, SELECT, FROM + filters, ORDER_BY, args);
    }
}
