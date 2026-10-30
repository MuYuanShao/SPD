package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.*;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.supplychain.SupplyChainSupport;
import com.hospital.spd.supplychain.PurchaseOrderActionRequest;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import static com.hospital.spd.common.service.DocumentKind.*;

/** Owns mixed plan snapshots and atomic, supplier-scoped order generation after final approval. */
final class MixedPurchasePlanService {
    private final JdbcTemplate db;
    private final SupplyChainSupport support;
    private final OperatorContextProvider operators;
    private final DataScopeService scope;
    private final AuditLogService audit;
    private final ApprovalFlowGuard approvals;

    MixedPurchasePlanService(JdbcTemplate db, SupplyChainSupport support, OperatorContextProvider operators, DataScopeService scope) {
        this.db=db;this.support=support;this.operators=operators;this.scope=scope;
        audit=new AuditLogService(db,operators);approvals=new ApprovalFlowGuard(db,operators);
    }

    Map<String,Object> create(Map<String,Object> request) {
        if(request.get("supplierName") instanceof String s && !s.isBlank())
            throw new IllegalArgumentException("混合计划不设置统一供应商，请在计划明细中确认配送商");
        if(!(request.get("demandNos") instanceof List<?> raw))throw new IllegalArgumentException("请选择已审核需求");
        var nos=raw.stream().map(String::valueOf).map(String::trim).filter(s->!s.isBlank()).distinct().toList();
        if(nos.isEmpty())throw new IllegalArgumentException("请选择已审核需求");
        var args=new ArrayList<Object>(nos);var where=new StringBuilder(" WHERE pd.demand_no IN ("+String.join(",",Collections.nCopies(nos.size(),"?"))+") AND pd.demand_status='approved'");
        scope.appendScope(where,args,"pd.dept_id","pd.create_by");
        var lines=db.queryForList("""
                SELECT pd.demand_id,pd.demand_no,pd.product_id,pd.approved_quantity,pd.dept_id,
                       p.supplier_id,p.unit,p.purchase_price,p.status AS product_status,p.deleted AS product_deleted,
                       s.status AS supplier_status,s.deleted AS supplier_deleted
                  FROM purchase_demand pd JOIN product p ON p.product_id=pd.product_id
                  LEFT JOIN supplier s ON s.supplier_id=p.supplier_id
                """+where+" ORDER BY pd.demand_id FOR UPDATE",args.toArray());
        if(lines.stream().map(r->r.get("demand_no")).distinct().count()!=nos.size())
            throw new IllegalArgumentException("需求不存在、无权访问、未审核或已转计划，请刷新");
        for(var l:lines)validateLine(l);
        String no=support.nextNo(PURCHASE_PLAN);var key=new GeneratedKeyHolder();
        db.update(c->{var ps=c.prepareStatement("INSERT INTO purchase_plan(plan_no,plan_status,remark,create_by,dept_id) VALUES(?,'draft',?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,no);ps.setString(2,Objects.toString(request.get("remark"),"由已审核采购需求生成"));ps.setObject(3,operators.current().userId());ps.setObject(4,operators.current().deptId());return ps;},key);
        long id=Objects.requireNonNull(key.getKey()).longValue();
        for(var l:lines){
            db.update("INSERT INTO purchase_plan_item(plan_id,demand_id,product_id,supplier_id,quantity,unit,estimated_unit_price) VALUES(?,?,?,?,?,?,?)",
                    id,l.get("demand_id"),l.get("product_id"),l.get("supplier_id"),l.get("approved_quantity"),l.get("unit"),l.get("purchase_price"));
            db.update("INSERT INTO purchase_plan_demand(plan_id,demand_id,allocated_quantity) VALUES(?,?,?)",id,l.get("demand_id"),l.get("approved_quantity"));
            if(db.update("UPDATE purchase_demand SET demand_status='planned' WHERE demand_id=? AND demand_status='approved'",l.get("demand_id"))!=1)
                throw new IllegalArgumentException("需求状态已变化，请刷新");
        }
        syncSuppliers(id);
        audit.record("purchase_plan","create_from_demand",id,no,"混合需求："+String.join("、",nos));
        return Map.of("createdPlans",1,"planNos",List.of(no),"planNo",no);
    }

    private void validateLine(Map<String,Object> line) {
        if(!Objects.equals(line.get("product_status"),1)||!Objects.equals(line.get("product_deleted"),0))throw new IllegalArgumentException("计划商品不存在或已停用");
        if(line.get("supplier_id")==null||!Objects.equals(line.get("supplier_status"),1)||!Objects.equals(line.get("supplier_deleted"),0))
            throw new IllegalArgumentException("计划明细配送商缺失或已停用，请先维护商品配送商");
        QuantityRules.positive((BigDecimal)line.get("approved_quantity"),"计划数量");
        if(line.get("purchase_price")==null||((BigDecimal)line.get("purchase_price")).signum()<0)throw new IllegalArgumentException("计划明细缺少有效采购价格");
        if(line.get("unit")==null||line.get("unit").toString().isBlank())throw new IllegalArgumentException("计划明细缺少单位");
    }

    private void syncSuppliers(long id) {
        // Supplier edits are allowed only before any approval or publication.
        db.update("DELETE ps FROM purchase_plan_supplier ps WHERE ps.plan_id=? AND NOT EXISTS(SELECT 1 FROM purchase_plan_item i WHERE i.plan_id=ps.plan_id AND i.supplier_id=ps.supplier_id)",id);
        db.update("INSERT IGNORE INTO purchase_plan_supplier(plan_id,supplier_id) SELECT DISTINCT plan_id,supplier_id FROM purchase_plan_item WHERE plan_id=?",id);
    }

    Map<String,Object> requirePlan(String no,boolean lock) {
        var args=new ArrayList<Object>(List.of(no));var where=new StringBuilder(" WHERE pp.plan_no=?");scope.appendScope(where,args,"pp.dept_id","pp.create_by");
        var rows=db.queryForList("SELECT pp.* FROM purchase_plan pp"+where+(lock?" FOR UPDATE":""),args.toArray());
        if(rows.size()!=1)throw new IllegalArgumentException("采购计划不存在或无权访问");return rows.get(0);
    }

    Map<String,Object> action(String no,PurchaseOrderActionRequest request) {
        var plan=requirePlan(no,true);String status=plan.get("plan_status").toString();
        if("executed".equals(status)&&Set.of("approve","execute").contains(Objects.toString(request.action(),"")))return result(no);
        new PurchaseFlowCommandRunner(db,operators,this::createOrders).runPlanAction(no,request);
        // The legacy scalar is meaningful only for historical/single-supplier plans.
        db.update("UPDATE purchase_plan pp SET converted_order_no=NULL WHERE pp.plan_no=? AND (SELECT COUNT(*) FROM purchase_order po WHERE po.source_plan_id=pp.plan_id)>1",no);
        return result(no);
    }

    private Map<String,Object> result(String no) {
        var result=new LinkedHashMap<String,Object>();var header=requirePlan(no,true);
        result.put("planNo",no);result.put("status",header.get("plan_status"));
        var orders=db.queryForList("SELECT order_no AS orderNo,supplier_id AS supplierId,order_status AS status FROM purchase_order WHERE source_plan_id=? ORDER BY purchase_order_id FOR SHARE",header.get("plan_id"));
        result.put("orders",orders);result.put("orderNos",orders.stream().map(o->o.get("orderNo")).toList());
        if(orders.size()==1)result.put("orderNo",orders.get(0).get("orderNo"));return result;
    }

    String createOrders(String no,Map<String,Object> header) {
        long id=((Number)header.get("planId")).longValue();
        var existing=db.queryForList("SELECT order_no FROM purchase_order WHERE source_plan_id=? ORDER BY purchase_order_id FOR UPDATE",String.class,id);
        if(!existing.isEmpty())return existing.get(0);
        var lines=db.queryForList("""
                SELECT i.*,p.status AS product_status,p.deleted AS product_deleted,
                       s.status AS supplier_status,s.deleted AS supplier_deleted
                  FROM purchase_plan_item i JOIN product p ON p.product_id=i.product_id
                  LEFT JOIN supplier s ON s.supplier_id=i.supplier_id
                 WHERE i.plan_id=? ORDER BY i.supplier_id,i.plan_item_id FOR UPDATE
                """,id);
        if(lines.isEmpty())throw new IllegalArgumentException("采购计划没有有效明细");
        var groups=new LinkedHashMap<Long,List<Map<String,Object>>>();
        for(var line:lines){
            if(!Objects.equals(line.get("product_status"),1)||!Objects.equals(line.get("product_deleted"),0)||!Objects.equals(line.get("supplier_status"),1)||!Objects.equals(line.get("supplier_deleted"),0))
                throw new IllegalArgumentException("计划商品或明细配送商已停用，请核对原计划");
            QuantityRules.positive((BigDecimal)line.get("quantity"),"计划数量");
            // Legacy plans did not record an estimate; resolve it once at their explicit conversion.
            if(line.get("estimated_unit_price")==null)line.put("estimated_unit_price",db.queryForObject("SELECT purchase_price FROM product WHERE product_id=?",BigDecimal.class,line.get("product_id")));
            if(line.get("estimated_unit_price")==null||((BigDecimal)line.get("estimated_unit_price")).signum()<0)throw new IllegalArgumentException("计划明细采购价格无效");
            groups.computeIfAbsent(((Number)line.get("supplier_id")).longValue(),x->new ArrayList<>()).add(line);
        }
        var numbers=new ArrayList<String>();
        for(var group:groups.entrySet()){
            String orderNo=support.nextNo(PURCHASE_ORDER);numbers.add(orderNo);
            BigDecimal total=group.getValue().stream().map(l->((BigDecimal)l.get("quantity")).multiply((BigDecimal)l.get("estimated_unit_price"))).reduce(BigDecimal.ZERO,BigDecimal::add);
            var key=new GeneratedKeyHolder();
            db.update(c->{PreparedStatement ps=c.prepareStatement("""
                    INSERT INTO purchase_order(order_no,supplier_id,order_source,order_status,total_amount,expected_arrival_date,create_by,approve_by,approve_time,purchase_type,source_plan_id)
                    VALUES(?,?,'plan','approved',?,DATE_ADD(CURRENT_DATE,INTERVAL 7 DAY),?,?,NOW(),'regular',?)
                    """,Statement.RETURN_GENERATED_KEYS);ps.setString(1,orderNo);ps.setLong(2,group.getKey());ps.setBigDecimal(3,total);ps.setObject(4,header.get("createBy")==null?operators.current().userId():header.get("createBy"));ps.setObject(5,operators.current().userId());ps.setLong(6,id);return ps;},key);
            long order=Objects.requireNonNull(key.getKey()).longValue();
            for(var l:group.getValue())db.update("INSERT INTO purchase_order_item(purchase_order_id,product_id,quantity,unit,estimated_unit_price,amount,received_quantity,source_plan_item_id) VALUES(?,?,?,?,?,?,0,?)",order,l.get("product_id"),l.get("quantity"),l.get("unit"),l.get("estimated_unit_price"),((BigDecimal)l.get("quantity")).multiply((BigDecimal)l.get("estimated_unit_price")),l.get("plan_item_id"));
            db.update("INSERT INTO purchase_order_tracking(purchase_order_id,event_type,event_status,remark) VALUES(?,'approved_from_plan','计划最终审批自动生成',?)",order,no);
            audit.record("purchase_order","approved_from_plan",order,orderNo,"来源计划："+no+"；按明细配送商拆单，承接计划审批授权");
        }
        audit.record("purchase_plan","split_orders",id,no,String.join("、",numbers));
        return numbers.get(0);
    }

    Map<String,Object> detail(String no) {
        var header=requirePlan(no,false);var result=new LinkedHashMap<String,Object>(result(no));
        result.put("items",db.queryForList("""
                SELECT i.plan_item_id AS planItemId,p.product_code AS productCode,p.product_name AS productName,p.spec_model AS specModel,
                       i.supplier_id AS supplierId,s.supplier_name AS supplierName,i.quantity,i.unit,
                       i.estimated_unit_price AS unitPrice,i.quantity*i.estimated_unit_price AS amount,p.is_high_value AS highValue
                  FROM purchase_plan_item i JOIN product p ON p.product_id=i.product_id JOIN supplier s ON s.supplier_id=i.supplier_id
                 WHERE i.plan_id=? ORDER BY i.plan_item_id
                """,header.get("plan_id")));
        result.put("suppliers",db.queryForList("SELECT supplier_id AS supplierId,supplier_name AS supplierName FROM supplier WHERE status=1 AND deleted=0 ORDER BY supplier_name,supplier_id"));
        Long started=db.queryForObject("SELECT COUNT(*) FROM business_approval_run WHERE document_key=?",Long.class,"purchase-management:plan-approval:"+no);
        result.put("canEditSupplier","draft".equals(header.get("plan_status"))&&(started==null||started==0));return result;
    }

    void supplier(String no,long item,long supplier) {
        var header=requirePlan(no,true);if(!"draft".equals(header.get("plan_status")))throw new IllegalArgumentException("仅未开始审批的草稿计划可修改配送商");
        approvals.requireNotStarted("purchase-management","plan-approval",no);
        if(db.queryForObject("SELECT COUNT(*) FROM supplier WHERE supplier_id=? AND status=1 AND deleted=0",Long.class,supplier)!=1)throw new IllegalArgumentException("配送商不存在或已停用");
        if(db.update("UPDATE purchase_plan_item SET supplier_id=? WHERE plan_item_id=? AND plan_id=?",supplier,item,header.get("plan_id"))!=1)throw new IllegalArgumentException("计划明细不存在");
        syncSuppliers(((Number)header.get("plan_id")).longValue());audit.record("purchase_plan","update_distributor",((Number)header.get("plan_id")).longValue(),no,"明细："+item+"；配送商："+supplier);
    }

    Map<String,Object> list(Map<String,String> params) {
        var page=PageRequest.from(params);var args=new ArrayList<Object>();var where=new StringBuilder(" WHERE 1=1");
        like(where,args,"pp.plan_no",params.get("planNo"));
        for(String key:List.of("keyword","supplierName")){String value=params.get(key);if(value!=null&&!value.isBlank()){
            where.append(" AND EXISTS(SELECT 1 FROM purchase_plan_item qi JOIN product qp ON qp.product_id=qi.product_id JOIN supplier qs ON qs.supplier_id=qi.supplier_id WHERE qi.plan_id=pp.plan_id AND ");
            where.append(key.equals("keyword")?"(qp.product_name LIKE ? OR qp.product_code LIKE ?))":"qs.supplier_name LIKE ?)");args.add("%"+value.trim()+"%");if(key.equals("keyword"))args.add("%"+value.trim()+"%");}}
        String status=params.get("status");if(status!=null&&!status.isBlank()){where.append(" AND pp.plan_status=?");args.add(status);}
        scope.appendScope(where,args,"pp.dept_id","pp.create_by");
        long total=Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM purchase_plan pp"+where,Long.class,args.toArray()));
        var query=new ArrayList<>(args);query.add(page.size());query.add(page.offset());
        var rows=db.queryForList("""
                SELECT pp.plan_id AS planId,pp.plan_no AS planNo,pp.plan_status AS planStatus,pp.remark,
                       DATE_FORMAT(pp.create_time,'%Y-%m-%d %H:%i') AS createTime,
                       (SELECT COUNT(*) FROM purchase_plan_item i WHERE i.plan_id=pp.plan_id) AS itemCount,
                       (SELECT COUNT(DISTINCT i.supplier_id) FROM purchase_plan_item i WHERE i.plan_id=pp.plan_id) AS supplierCount,
                       (SELECT SUM(i.quantity*i.estimated_unit_price) FROM purchase_plan_item i WHERE i.plan_id=pp.plan_id) AS amount,
                       (SELECT GROUP_CONCAT(DISTINCT d.dept_name ORDER BY d.dept_name SEPARATOR '、') FROM purchase_plan_demand pd JOIN purchase_demand demand ON demand.demand_id=pd.demand_id LEFT JOIN sys_dept d ON d.dept_id=demand.dept_id WHERE pd.plan_id=pp.plan_id) AS initiatingDeptName,
                       (SELECT COUNT(*) FROM purchase_order po WHERE po.source_plan_id=pp.plan_id) AS orderCount
                FROM purchase_plan pp
                """+where+" ORDER BY pp.create_time DESC,pp.plan_id DESC LIMIT ? OFFSET ?",query.toArray());
        return PageResponse.of(rows,total,page);
    }
    private void like(StringBuilder where,List<Object> args,String column,String value){if(value!=null&&!value.isBlank()){where.append(" AND ").append(column).append(" LIKE ?");args.add("%"+value.trim()+"%");}}
}
