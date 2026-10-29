package com.hospital.spd.mobile;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.DataScopeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Reads approved requisition lines and exact pickable identities, preserving source and destination snapshots. */
@Repository
public class MobilePickingRepository {
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;
    private static final String PICKED = """
            ((SELECT COALESCE(SUM(b.package_quantity),0) FROM spd_delivery_package_binding b WHERE b.requisition_item_id=i.item_id AND b.recalled=0)
             + (SELECT COUNT(*) FROM department_requisition_trace_code rt JOIN spd_delivery_trace_code dt ON dt.trace_code_id=rt.trace_code_id WHERE rt.requisition_item_id=i.item_id AND dt.recalled=0)
             + (SELECT COALESCE(SUM(d.quantity),0) FROM spd_delivery_order d WHERE d.requisition_item_id=i.item_id AND d.delivery_type='loose'))
            """;
    private static final String BASE = """
            SELECT i.item_id AS id,r.requisition_id AS requisitionId,r.requisition_no AS documentNo,r.status,
              CASE WHEN i.item_type='loose' THEN 'PICK_LOOSE' WHEN i.item_type='quota_package' THEN 'PICK_PACKAGE'
                   WHEN i.item_type IN ('high_value','unique_code') THEN 'PICK_HIGH_VALUE' ELSE 'UNSUPPORTED' END AS kind,
              r.dept_id AS deptId,dep.dept_name AS deptName,r.source_warehouse_id AS sourceWarehouseId,src.warehouse_name AS sourceWarehouseName,
              r.warehouse_id AS warehouseId,dst.warehouse_name AS warehouseName,p.product_id AS productId,p.product_code AS productCode,
              p.product_name AS productName,COALESCE(NULLIF(i.unit,''),p.unit) AS unit,i.quantity,
              %s AS pickedQuantity, GREATEST(i.quantity - %s,0) AS remainingQuantity,
              i.quota_package_quantity AS packageQuantity,i.quota_package_unit AS packageUnit,
              i.quota_template_id AS templateId,i.quota_template_version AS templateVersion,p.udi_code AS productUdi,r.applicant_id AS applicantId,
              COALESCE(p.spec_model,'') AS specModel,COALESCE(m.manufacturer_name,'') AS manufacturerName,COALESCE(sup.supplier_name,'') AS supplierName
            FROM department_requisition_item i JOIN department_requisition r ON r.requisition_id=i.requisition_id
            JOIN sys_dept dep ON dep.dept_id=r.dept_id AND dep.deleted=0 AND dep.status=1
            JOIN warehouse src ON src.warehouse_id=r.source_warehouse_id AND src.deleted=0 AND src.status=1
            JOIN warehouse dst ON dst.warehouse_id=r.warehouse_id AND dst.dept_id=r.dept_id AND dst.deleted=0 AND dst.status=1
            JOIN product p ON p.product_id=i.product_id AND p.deleted=0 AND p.status=1
            LEFT JOIN manufacturer m ON m.manufacturer_id=p.manufacturer_id
            LEFT JOIN supplier sup ON sup.supplier_id=p.supplier_id
            """.formatted(PICKED, PICKED);
    public record Data(PickTask task, Long templateId, Long templateVersion, String productUdi, Long applicantId) {}
    private static final RowMapper<Data> DATA = (rs, n) -> new Data(new PickTask(
            rs.getLong("id"),rs.getString("documentNo"),rs.getString("status"),rs.getString("kind"),
            rs.getLong("deptId"),rs.getString("deptName"),rs.getLong("sourceWarehouseId"),rs.getString("sourceWarehouseName"),
            rs.getLong("warehouseId"),rs.getString("warehouseName"),rs.getLong("productId"),rs.getString("productCode"),
            rs.getString("productName"),rs.getString("unit"),rs.getBigDecimal("quantity"),rs.getBigDecimal("pickedQuantity"),
            rs.getBigDecimal("remainingQuantity"),rs.getBigDecimal("packageQuantity"),rs.getString("packageUnit"),
            rs.getString("specModel"),rs.getString("manufacturerName"),rs.getString("supplierName"),rs.getLong("requisitionId")),
            rs.getObject("templateId", Long.class),rs.getObject("templateVersion",Long.class),rs.getString("productUdi"),rs.getObject("applicantId",Long.class));
    public MobilePickingRepository(JdbcTemplate jdbc,DataScopeService scopes) { this.jdbc = jdbc;this.scopes=scopes; }

    private static final RowMapper<PickOrder> ORDER=(rs,n)->new PickOrder(rs.getLong("id"),rs.getString("documentNo"),rs.getString("status"),
            rs.getLong("deptId"),rs.getString("deptName"),rs.getLong("sourceWarehouseId"),rs.getString("sourceWarehouseName"),
            rs.getLong("warehouseId"),rs.getString("warehouseName"),rs.getLong("pendingLineCount"));

    public PageResponse<PickOrder> orders(Long dept,Long warehouse,String query,PageRequest page) {
        var args=new ArrayList<Object>(List.of(dept,warehouse));
        var where=new StringBuilder(" WHERE t.deptId=? AND t.warehouseId=? AND t.status IN ('approved','partial_picked') AND t.remainingQuantity>0 AND t.kind<>'UNSUPPORTED'");
        scopes.appendScope(where,args,"t.deptId","t.applicantId");
        String grouped="""
                SELECT t.requisitionId AS id,t.documentNo,t.status,t.deptId,t.deptName,t.sourceWarehouseId,t.sourceWarehouseName,
                  t.warehouseId,t.warehouseName,COUNT(*) AS pendingLineCount FROM (
                """+BASE+") t"+where+" GROUP BY t.requisitionId,t.documentNo,t.status,t.deptId,t.deptName,t.sourceWarehouseId,t.sourceWarehouseName,t.warehouseId,t.warehouseName";
        if(query!=null&&!query.isBlank()) {
            grouped+=" HAVING SUM(CASE WHEN t.documentNo LIKE ? OR t.productCode LIKE ? OR t.productName LIKE ? THEN 1 ELSE 0 END)>0";
            for(int i=0;i<3;i++)args.add("%"+query.trim()+"%");
        }
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM ("+grouped+") g",Long.class,args.toArray());
        args.add(page.size());args.add(page.offset());
        var rows=jdbc.query(grouped+" ORDER BY id DESC LIMIT ? OFFSET ?",ORDER,args.toArray());
        return new PageResponse<>(rows,count==null?0:count,page.page(),page.size(),null);
    }
    public List<PickOrder> orderHeaders(Long id,Long dept,Long warehouse) {
        var args=new ArrayList<Object>(List.of(id,dept,warehouse));
        var where=new StringBuilder(" WHERE r.requisition_id=? AND r.dept_id=? AND r.warehouse_id=?");
        scopes.appendScope(where,args,"r.dept_id","r.applicant_id");
        return jdbc.query("""
                SELECT r.requisition_id AS id,r.requisition_no AS documentNo,r.status,r.dept_id AS deptId,d.dept_name AS deptName,
                  src.warehouse_id AS sourceWarehouseId,src.warehouse_name AS sourceWarehouseName,
                  dst.warehouse_id AS warehouseId,dst.warehouse_name AS warehouseName,0 AS pendingLineCount
                FROM department_requisition r JOIN sys_dept d ON d.dept_id=r.dept_id AND d.deleted=0 AND d.status=1
                JOIN warehouse src ON src.warehouse_id=r.source_warehouse_id AND src.deleted=0 AND src.status=1
                JOIN warehouse dst ON dst.warehouse_id=r.warehouse_id AND dst.dept_id=r.dept_id AND dst.deleted=0 AND dst.status=1
                """+where,ORDER,args.toArray());
    }
    public List<Data> orderItems(Long id,Long dept,Long warehouse) {
        var args=new ArrayList<Object>(List.of(id,dept,warehouse));
        var where=new StringBuilder(" WHERE t.requisitionId=? AND t.deptId=? AND t.warehouseId=? AND t.status IN ('approved','partial_picked') AND t.remainingQuantity>0 AND t.kind<>'UNSUPPORTED'");
        scopes.appendScope(where,args,"t.deptId","t.applicantId");
        return jdbc.query("SELECT t.* FROM ("+BASE+") t"+where+" ORDER BY t.id",DATA,args.toArray());
    }

    public PageResponse<PickTask> tasks(Long deptId,Long warehouseId,String query,PageRequest page) {
        String where = " WHERE t.deptId=? AND t.warehouseId=? AND t.status IN ('approved','partial_picked') AND t.remainingQuantity>0 AND t.kind<>'UNSUPPORTED'";
        List<Object> args = new ArrayList<>(List.of(deptId,warehouseId));
        StringBuilder scope=new StringBuilder();scopes.appendScope(scope,args,"t.deptId","t.applicantId");where+=scope;
        if (query != null && !query.isBlank()) {
            where += " AND (t.documentNo LIKE ? OR t.productCode LIKE ? OR t.productName LIKE ?)";
            for (int i=0;i<3;i++) args.add("%"+query.trim()+"%");
        }
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM ("+BASE+") t"+where,Long.class,args.toArray());
        args.add(page.size()); args.add(page.offset());
        List<PickTask> rows=jdbc.query("SELECT t.* FROM ("+BASE+") t"+where+" ORDER BY t.id DESC LIMIT ? OFFSET ?",
                (rs,n)->DATA.mapRow(rs,n).task(),args.toArray());
        return new PageResponse<>(rows,count==null?0:count,page.page(),page.size(),null);
    }
    public Data detail(Long itemId,Long deptId,Long warehouseId,boolean lock) {
        if (lock) jdbc.queryForList("""
                SELECT r.requisition_id FROM department_requisition r
                JOIN department_requisition_item i ON i.requisition_id=r.requisition_id
                WHERE i.item_id=? AND r.dept_id=? AND r.warehouse_id=? FOR UPDATE
                """,itemId,deptId,warehouseId);
        List<Data> rows=jdbc.query(BASE+" WHERE i.item_id=? AND r.dept_id=? AND r.warehouse_id=?",DATA,itemId,deptId,warehouseId);
        if(rows.size()!=1) throw new IllegalArgumentException("申领明细不存在、不属于当前科室库房或原单资料已停用");
        return rows.get(0);
    }
    public List<PickUnit> resolve(Data data,String code,boolean lock) {
        PickTask task=data.task();
        List<PickUnit> rows;
        if("PICK_PACKAGE".equals(task.kind())) {
            rows=jdbc.query("""
                    SELECT q.label_id,q.label_no,q.status,q.package_quantity
                    FROM quota_package_label q JOIN quota_package_template t ON t.template_id=q.template_id
                    LEFT JOIN udi_trace_code tc ON tc.trace_code_id=q.trace_code_id
                    WHERE q.product_id=? AND q.warehouse_id=? AND q.status='available'
                      AND q.template_id=? AND t.version_no=? AND q.package_quantity=?
                      AND EXISTS (SELECT 1 FROM quota_package_template_item ti WHERE ti.template_id=q.template_id AND ti.product_id=q.product_id AND ti.deleted=0 AND ti.unit=?)
                      AND (q.label_no=? OR tc.unique_code=? OR tc.udi_code=?)
                      AND NOT EXISTS(SELECT 1 FROM spd_delivery_package_binding b WHERE b.label_id=q.label_id)
                    ORDER BY q.label_id
                    """+(lock?" FOR UPDATE":""),(rs,n)->new PickUnit("package",rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4)),
                    task.productId(),task.sourceWarehouseId(),data.templateId(),data.templateVersion(),task.packageQuantity(),task.packageUnit(),code,code,code);
        } else if("PICK_HIGH_VALUE".equals(task.kind())) {
            rows=jdbc.query("""
                    SELECT tc.trace_code_id,tc.unique_code,tc.current_status FROM udi_trace_code tc
                    JOIN inventory_batch_trace_code bt ON bt.trace_code_id=tc.trace_code_id
                    JOIN inventory_batch ib ON ib.batch_id=bt.batch_id
                    WHERE ib.product_id=? AND bt.current_warehouse_id=? AND bt.lifecycle_status='in_stock'
                      AND tc.current_status='in_stock' AND (tc.unique_code=? OR tc.udi_code=?)
                      AND (ib.expire_date IS NULL OR ib.expire_date>=CURRENT_DATE)
                      AND NOT EXISTS(SELECT 1 FROM spd_delivery_trace_code dt WHERE dt.trace_code_id=tc.trace_code_id)
                    ORDER BY tc.trace_code_id
                    """+(lock?" FOR UPDATE":""),(rs,n)->new PickUnit("trace",rs.getLong(1),rs.getString(2),rs.getString(3),BigDecimal.ONE),
                    task.productId(),task.sourceWarehouseId(),code,code);
        } else return List.of();
        return rows;
    }
    public List<String> packageCodes(Data data,Long id) {
        return jdbc.queryForList("SELECT label_no FROM quota_package_label WHERE label_id=? AND warehouse_id=? AND product_id=?",
                String.class,id,data.task().sourceWarehouseId(),data.task().productId());
    }
    public record PackageSource(Long warehouseId,Long productId,BigDecimal quantity,boolean expired) {}
    public List<PackageSource> packageSources(Long id,boolean lock) {
        return jdbc.query("""
                SELECT s.warehouse_id,ib.product_id,s.source_qty,ib.expire_date<CURRENT_DATE
                FROM quota_package_label_source s LEFT JOIN inventory_batch ib ON ib.batch_id=s.batch_id
                WHERE s.label_id=? ORDER BY s.source_id
                """+(lock?" FOR UPDATE":""),(rs,n)->new PackageSource(rs.getObject(1,Long.class),rs.getObject(2,Long.class),
                rs.getBigDecimal(3),rs.getBoolean(4)),id);
    }
    public List<String> traceCodes(Long id) {
        return jdbc.queryForList("SELECT unique_code FROM udi_trace_code WHERE trace_code_id=?",String.class,id);
    }
    public record Balance(BigDecimal quantity,boolean expired) {}
    public List<Balance> looseBalances(Data data,boolean lock) {
        return jdbc.query("""
                SELECT GREATEST(b.available_qty-COALESCE((SELECT SUM(s.source_qty) FROM quota_package_label q
                  JOIN quota_package_label_source s ON s.label_id=q.label_id WHERE q.status='signed'
                  AND q.warehouse_id=b.warehouse_id AND q.product_id=b.product_id AND s.batch_id=b.batch_id),0),0) AS qty,
                  ib.expire_date<CURRENT_DATE AS expired
                FROM inventory_balance b JOIN inventory_batch ib ON ib.batch_id=b.batch_id
                WHERE b.warehouse_id=? AND b.product_id=? AND b.location_id IS NULL AND b.available_qty>0
                ORDER BY ib.batch_id
                """+(lock?" FOR UPDATE":""),(rs,n)->new Balance(rs.getBigDecimal(1),rs.getBoolean(2)),
                data.task().sourceWarehouseId(),data.task().productId());
    }
}
