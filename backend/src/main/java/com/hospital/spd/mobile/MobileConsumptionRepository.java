package com.hospital.spd.mobile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Reads package identities, source batches and balances, with locks owned by the calling transaction. */
@Repository
public class MobileConsumptionRepository {
    private final JdbcTemplate jdbc;
    public MobileConsumptionRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public List<Long> resolveIds(String rawCode,Long deptId,Long warehouseId) {
        return jdbc.queryForList("""
                SELECT DISTINCT q.label_id FROM quota_package_label q
                JOIN warehouse w ON w.warehouse_id=q.warehouse_id AND w.dept_id=? AND w.deleted=0 AND w.status=1
                LEFT JOIN udi_trace_code tc ON tc.trace_code_id=q.trace_code_id
                WHERE q.warehouse_id=? AND (q.label_no=? OR tc.unique_code=? OR tc.udi_code=?)
                """,Long.class,deptId,warehouseId,rawCode,rawCode,rawCode);
    }
    public record PackData(ConsumePack pack,boolean highValue,String traceStatus,String traceScope) {}
    public record Source(Long batchId,Long warehouseId,Long productId,BigDecimal quantity,BigDecimal unitPrice,String expireDate,boolean expired) {}
    public List<PackData> packs(Long labelId,Long deptId,Long warehouseId,boolean lock) {
        return jdbc.query("""
                SELECT q.label_id,q.label_no,q.trace_code_id,q.product_id,p.product_code,p.product_name,p.unit,q.package_quantity,
                  w.dept_id,d.dept_name,q.warehouse_id,w.warehouse_name,q.status,q.version,p.is_high_value,tc.current_status,tc.trace_scope
                FROM quota_package_label q
                JOIN warehouse w ON w.warehouse_id=q.warehouse_id AND w.deleted=0 AND w.status=1
                JOIN sys_dept d ON d.dept_id=w.dept_id AND d.deleted=0 AND d.status=1
                JOIN product p ON p.product_id=q.product_id AND p.deleted=0 AND p.status=1
                LEFT JOIN udi_trace_code tc ON tc.trace_code_id=q.trace_code_id
                WHERE q.label_id=? AND w.dept_id=? AND q.warehouse_id=?
                """+(lock?" FOR UPDATE":""),(rs,n)->new PackData(new ConsumePack(rs.getLong(1),rs.getString(2),rs.getObject(3,Long.class),
                rs.getLong(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getBigDecimal(8),rs.getLong(9),rs.getString(10),
                rs.getLong(11),rs.getString(12),rs.getString(13),rs.getInt(14)),rs.getBoolean(15),rs.getString(16),rs.getString(17)),labelId,deptId,warehouseId);
    }
    public List<Source> sources(Long labelId,boolean lock) {
        return jdbc.query("""
                SELECT s.batch_id,s.warehouse_id,ib.product_id,s.source_qty,ib.batch_unit_price,
                  DATE_FORMAT(ib.expire_date,'%Y-%m-%d'),ib.expire_date<CURRENT_DATE
                FROM quota_package_label_source s LEFT JOIN inventory_batch ib ON ib.batch_id=s.batch_id
                WHERE s.label_id=? ORDER BY s.source_id
                """+(lock?" FOR UPDATE":""),(rs,n)->new Source(rs.getLong(1),rs.getLong(2),rs.getObject(3,Long.class),
                rs.getBigDecimal(4),rs.getBigDecimal(5),rs.getString(6),rs.getBoolean(7)),labelId);
    }
    public List<BigDecimal> available(Long warehouseId,Long productId,Long batchId,boolean lock) {
        // Match the existing stock service's per-batch balance selection.
        return jdbc.query("""
                    SELECT b.available_qty FROM inventory_balance b JOIN inventory_batch ib ON ib.batch_id=b.batch_id
                    WHERE b.warehouse_id=? AND b.product_id=? AND b.batch_id=? LIMIT 1
                    """+(lock?" FOR UPDATE":""),(rs,n)->rs.getBigDecimal(1),warehouseId,productId,batchId);
    }
}
