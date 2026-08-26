package com.hospital.spd.system.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Objects;

/** Materialises one incremental inventory movement snapshot per business day and inventory batch. */
@Service
public class DailyInventorySummaryService {
    private final JdbcTemplate jdbcTemplate;

    public DailyInventorySummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Generates missing days sequentially so each opening balance comes from the preceding daily close. */
    @Transactional
    public int generateThrough(LocalDate targetDate) {
        if (targetDate == null) throw new IllegalArgumentException("target date is required");
        Date latest = jdbcTemplate.queryForObject("SELECT MAX(business_date) FROM inventory_daily_summary", Date.class);
        if (latest == null) {
            generateBaseline(targetDate);
            return 1;
        }
        LocalDate next = latest.toLocalDate().plusDays(1);
        int generated = 0;
        while (!next.isAfter(targetDate)) {
            generateIncremental(next);
            generated++;
            next = next.plusDays(1);
        }
        return generated;
    }

    @Transactional
    public void regenerate(LocalDate businessDate) {
        if (businessDate == null) throw new IllegalArgumentException("business date is required");
        Integer previousCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_daily_summary WHERE business_date = ?",
                Integer.class, Date.valueOf(businessDate.minusDays(1)));
        if (previousCount != null && previousCount > 0) generateIncremental(businessDate);
        else generateBaseline(businessDate);
    }

    private void generateIncremental(LocalDate businessDate) {
        Date date = Date.valueOf(businessDate);
        Date previousDate = Date.valueOf(businessDate.minusDays(1));
        Timestamp start = Timestamp.valueOf(businessDate.atStartOfDay());
        Timestamp end = Timestamp.valueOf(businessDate.plusDays(1).atStartOfDay());
        Timestamp snapshotTime = end;
        jdbcTemplate.update("""
                INSERT INTO inventory_daily_summary (
                  business_date, snapshot_time, warehouse_id, product_id, batch_id, supplier_id,
                  opening_quantity, inbound_quantity, return_quantity, requisition_quantity,
                  consumption_quantity, scrap_quantity, closing_quantity, unit_price
                )
                WITH movement AS (
                  SELECT warehouse_id, product_id, batch_id,
                         SUM(CASE WHEN event_type IN ('purchase_receive_in', 'warehouse_transfer_in', 'stocktaking_profit',
                                      'quota_package_delivery_sign_in', 'department_consumption_reverse_in',
                                      'quota_unpack_in', 'quota_terminate_in') THEN GREATEST(qty_change, 0) ELSE 0 END) AS inbound_qty,
                         SUM(CASE WHEN event_type IN ('supplier_return_out', 'warehouse_return_out', 'purchase_return_out') THEN ABS(qty_change) ELSE 0 END) AS return_qty,
                         SUM(CASE WHEN event_type IN ('delivery_out', 'delivery_loose_out', 'warehouse_transfer_out', 'quota_pack_out') THEN ABS(qty_change) ELSE 0 END) AS requisition_qty,
                         SUM(CASE WHEN event_type = 'department_consumption_out' THEN ABS(qty_change) ELSE 0 END) AS consumption_qty,
                         SUM(CASE WHEN event_type IN ('stocktaking_loss', 'inventory_scrap_out', 'scrap_out') THEN ABS(qty_change) ELSE 0 END) AS scrap_qty
                    FROM inventory_event
                   WHERE event_time >= ? AND event_time < ?
                   GROUP BY warehouse_id, product_id, batch_id
                ), keys_for_day AS (
                  SELECT warehouse_id, product_id, batch_id
                    FROM inventory_daily_summary WHERE business_date = ?
                  UNION
                  SELECT warehouse_id, product_id, batch_id FROM movement
                )
                SELECT ?, ?, k.warehouse_id, k.product_id, k.batch_id, ib.supplier_id,
                       COALESCE(previous.closing_quantity, 0),
                       COALESCE(m.inbound_qty, 0), COALESCE(m.return_qty, 0), COALESCE(m.requisition_qty, 0),
                       COALESCE(m.consumption_qty, 0), COALESCE(m.scrap_qty, 0),
                       COALESCE(previous.closing_quantity, 0) + COALESCE(m.inbound_qty, 0)
                         - COALESCE(m.return_qty, 0) - COALESCE(m.requisition_qty, 0)
                         - COALESCE(m.consumption_qty, 0) - COALESCE(m.scrap_qty, 0),
                       COALESCE(ib.batch_unit_price, previous.unit_price, 0)
                  FROM keys_for_day k
                  LEFT JOIN inventory_daily_summary previous
                    ON previous.business_date = ? AND previous.warehouse_id = k.warehouse_id
                   AND previous.product_id = k.product_id AND previous.batch_id = k.batch_id
                  LEFT JOIN movement m ON m.warehouse_id = k.warehouse_id AND m.product_id = k.product_id AND m.batch_id = k.batch_id
                  LEFT JOIN inventory_batch ib ON ib.batch_id = k.batch_id
                ON DUPLICATE KEY UPDATE
                  snapshot_time = VALUES(snapshot_time), supplier_id = VALUES(supplier_id),
                  opening_quantity = VALUES(opening_quantity), inbound_quantity = VALUES(inbound_quantity),
                  return_quantity = VALUES(return_quantity), requisition_quantity = VALUES(requisition_quantity),
                  consumption_quantity = VALUES(consumption_quantity), scrap_quantity = VALUES(scrap_quantity),
                  closing_quantity = VALUES(closing_quantity), unit_price = VALUES(unit_price)
                """, start, end, previousDate, date, snapshotTime, previousDate);
        assertFormula(date);
    }

    /** Establishes only the first requested day from the current balance anchor; later days are incremental. */
    private void generateBaseline(LocalDate businessDate) {
        Date date = Date.valueOf(businessDate);
        Timestamp start = Timestamp.valueOf(businessDate.atStartOfDay());
        Timestamp end = Timestamp.valueOf(businessDate.plusDays(1).atStartOfDay());
        jdbcTemplate.update("""
                INSERT INTO inventory_daily_summary (
                  business_date, snapshot_time, warehouse_id, product_id, batch_id, supplier_id,
                  opening_quantity, inbound_quantity, return_quantity, requisition_quantity,
                  consumption_quantity, scrap_quantity, closing_quantity, unit_price
                )
                WITH movement AS (
                  SELECT warehouse_id, product_id, batch_id,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type IN ('purchase_receive_in', 'warehouse_transfer_in', 'stocktaking_profit',
                                      'quota_package_delivery_sign_in', 'department_consumption_reverse_in', 'quota_unpack_in', 'quota_terminate_in') THEN GREATEST(qty_change, 0) ELSE 0 END) AS inbound_qty,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type IN ('supplier_return_out', 'warehouse_return_out', 'purchase_return_out') THEN ABS(qty_change) ELSE 0 END) AS return_qty,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type IN ('delivery_out', 'delivery_loose_out', 'warehouse_transfer_out', 'quota_pack_out') THEN ABS(qty_change) ELSE 0 END) AS requisition_qty,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type = 'department_consumption_out' THEN ABS(qty_change) ELSE 0 END) AS consumption_qty,
                         SUM(CASE WHEN event_time >= ? AND event_time < ? AND event_type IN ('stocktaking_loss', 'inventory_scrap_out', 'scrap_out') THEN ABS(qty_change) ELSE 0 END) AS scrap_qty,
                         SUM(CASE WHEN event_time >= ? AND event_type <> 'recall_isolate' THEN qty_change ELSE 0 END) AS net_after_end
                    FROM inventory_event
                   GROUP BY warehouse_id, product_id, batch_id
                ), current_balance AS (
                  SELECT warehouse_id, product_id, batch_id,
                         SUM(available_qty + locked_qty + isolated_qty) AS current_qty
                    FROM inventory_balance GROUP BY warehouse_id, product_id, batch_id
                ), keys_for_day AS (
                  SELECT warehouse_id, product_id, batch_id FROM current_balance
                  UNION
                  SELECT warehouse_id, product_id, batch_id FROM movement
                ), calculated AS (
                  SELECT k.warehouse_id, k.product_id, k.batch_id,
                         COALESCE(m.inbound_qty, 0) AS inbound_qty, COALESCE(m.return_qty, 0) AS return_qty,
                         COALESCE(m.requisition_qty, 0) AS requisition_qty,
                         COALESCE(m.consumption_qty, 0) AS consumption_qty, COALESCE(m.scrap_qty, 0) AS scrap_qty,
                         COALESCE(cb.current_qty, 0) - COALESCE(m.net_after_end, 0) AS closing_qty
                    FROM keys_for_day k
                    LEFT JOIN current_balance cb ON cb.warehouse_id = k.warehouse_id AND cb.product_id = k.product_id AND cb.batch_id = k.batch_id
                    LEFT JOIN movement m ON m.warehouse_id = k.warehouse_id AND m.product_id = k.product_id AND m.batch_id = k.batch_id
                )
                SELECT ?, ?, c.warehouse_id, c.product_id, c.batch_id, ib.supplier_id,
                       c.closing_qty - c.inbound_qty + c.return_qty + c.requisition_qty + c.consumption_qty + c.scrap_qty,
                       c.inbound_qty, c.return_qty, c.requisition_qty, c.consumption_qty, c.scrap_qty,
                       c.closing_qty, COALESCE(ib.batch_unit_price, 0)
                  FROM calculated c
                  LEFT JOIN inventory_batch ib ON ib.batch_id = c.batch_id
                ON DUPLICATE KEY UPDATE
                  snapshot_time = VALUES(snapshot_time), supplier_id = VALUES(supplier_id),
                  opening_quantity = VALUES(opening_quantity), inbound_quantity = VALUES(inbound_quantity),
                  return_quantity = VALUES(return_quantity), requisition_quantity = VALUES(requisition_quantity),
                  consumption_quantity = VALUES(consumption_quantity), scrap_quantity = VALUES(scrap_quantity),
                  closing_quantity = VALUES(closing_quantity), unit_price = VALUES(unit_price)
                """, start, end, start, end, start, end, start, end, start, end, end,
                date, Timestamp.valueOf(businessDate.plusDays(1).atStartOfDay()));
        assertFormula(date);
    }

    private void assertFormula(Date businessDate) {
        Integer invalid = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM inventory_daily_summary
                 WHERE business_date = ?
                   AND opening_quantity + inbound_quantity - return_quantity - requisition_quantity
                       - consumption_quantity - scrap_quantity <> closing_quantity
                """, Integer.class, businessDate);
        if (!Objects.equals(invalid, 0)) {
            throw new IllegalStateException("inventory daily summary formula validation failed");
        }
    }
}
