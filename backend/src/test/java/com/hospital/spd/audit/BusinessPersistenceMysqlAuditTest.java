package com.hospital.spd.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.service.*;
import com.hospital.spd.masterdata.*;
import com.hospital.spd.masterdata.service.*;
import com.hospital.spd.supplychain.*;
import com.hospital.spd.supplychain.service.*;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

/** Opt-in audit of current MySQL wiring; all writes use one uncommitted connection and are rolled back. */
@EnabledIfEnvironmentVariable(named="SPD_PERSISTENCE_AUDIT", matches="true")
class BusinessPersistenceMysqlAuditTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private void check(String module, Consumer<JdbcTemplate> action) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/ISPD?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai", System.getenv("SPD_DB_USERNAME"), System.getenv("SPD_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try { action.accept(new JdbcTemplate(new SingleConnectionDataSource(connection, true))); System.out.println("PERSISTENCE PASS: " + module); }
            finally { connection.rollback(); }
        }
    }
    private String code() { return "PV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private long id(JdbcTemplate jdbc) { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
    private String product(JdbcTemplate jdbc) {
        String code = code();
        Long category = jdbc.queryForObject("SELECT MIN(category_id) FROM product_category", Long.class);
        jdbc.update("INSERT INTO product (product_code,product_name,spec_model,category_id,unit,purchase_price,is_quota_managed,status) VALUES (?,?,'6.0',?,'个',1,1,1)", code, "核查商品"+code, category);
        return code;
    }
    private DepartmentUpsertRequest department(String code, String name) { return new DepartmentUpsertRequest(code,name,"F-"+code,name,"核查院区","地址","负责人","123",0,1); }
    private SupplyChainSupport support(JdbcTemplate jdbc) {
        var numbers = new DocumentNumberService(jdbc);
        return new SupplyChainSupport(numbers,new AuditLogService(jdbc),new InventoryMovementService(jdbc,new InventoryEventService(jdbc,numbers,OperatorContext::system)));
    }
    @Test void suppliers() throws Exception { check("供应商新增修改停用", jdbc -> {
        var service = new SupplierService(jdbc,new DocumentNumberService(jdbc));
        String credit="91"+UUID.randomUUID().toString().replace("-", "").substring(0,16).toUpperCase();
        String name=code();
        var req=new SupplierUpsertRequest("",name,credit,"许可证","配送商","A","联系人","123",null,"地址");
        String code=(String)service.createSupplier(req).get("supplierCode");
        service.updateSupplier(code,new SupplierUpsertRequest(code,name+"修改",credit,"许可证","配送商","A","联系人","456",null,"新地址"));
        assertThat(jdbc.queryForObject("SELECT contact_phone FROM supplier WHERE supplier_code=?",String.class,code)).isEqualTo("456");
        service.updateSupplierStatus(new SupplierStatusUpdateRequest(List.of(code),0));
        assertThat(jdbc.queryForObject("SELECT status FROM supplier WHERE supplier_code=?",Integer.class,code)).isZero();
    }); }
    @Test void manufacturers() throws Exception { check("厂家新增修改停用", jdbc -> {
        var service=new ManufacturerService(jdbc,new DocumentNumberService(jdbc));String name=code();
        String code=(String)service.createManufacturer(new ManufacturerUpsertRequest("",name,null,"L1","联系人","123","地址")).get("manufacturerCode");
        service.updateManufacturer(code,new ManufacturerUpsertRequest(code,name+"修改",null,"L2","联系人","456","新地址"));
        assertThat(jdbc.queryForObject("SELECT license_no FROM manufacturer WHERE manufacturer_code=?",String.class,code)).isEqualTo("L2");
        service.updateManufacturerStatus(new ManufacturerStatusUpdateRequest(List.of(code),0));
        assertThat(jdbc.queryForObject("SELECT status FROM manufacturer WHERE manufacturer_code=?",Integer.class,code)).isZero();
    }); }
    @Test void departments() throws Exception { check("科室新增修改删除", jdbc -> {
        var service=new DepartmentService(jdbc);String code=code();service.createDepartment(department(code,"核查科室"+code));
        service.updateDepartment(code,department(code,"修改科室"+code));
        assertThat(jdbc.queryForObject("SELECT dept_name FROM sys_dept WHERE dept_code=?",String.class,code)).isEqualTo("修改科室"+code);
        service.deleteDepartments(new DepartmentCodesRequest(List.of(code)));
        assertThat(jdbc.queryForObject("SELECT deleted FROM sys_dept WHERE dept_code=?",Integer.class,code)).isEqualTo(1);
    }); }
    @Test void warehouses() throws Exception { check("库房货位与绑定", jdbc -> {
        String deptCode=code(), deptName="核查科室"+deptCode;new DepartmentService(jdbc).createDepartment(department(deptCode,deptName));
        String code=code(), product=product(jdbc);var service=new WarehouseService(jdbc);
        service.createWarehouse(new WarehouseUpsertRequest(code,"核查库房"+code,"二级库","核查院区",deptCode,deptName,true,"[]",1,List.of(product)));
        service.updateWarehouse(code,new WarehouseUpsertRequest(code,"修改库房"+code,"二级库","核查院区",deptCode,deptName,true,"[]",1,List.of(product)));
        assertThat(jdbc.queryForObject("SELECT warehouse_name FROM warehouse WHERE warehouse_code=?",String.class,code)).isEqualTo("修改库房"+code);
        long warehouse=jdbc.queryForObject("SELECT warehouse_id FROM warehouse WHERE warehouse_code=?",Long.class,code);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_product_binding WHERE warehouse_id=? AND deleted=0",Integer.class,warehouse)).isEqualTo(1);
        service.createWarehouseLocation(code,new WarehouseLocationUpsertRequest("LOC-"+code,"普通货位",BigDecimal.TEN,product,1));
        long location=jdbc.queryForObject("SELECT location_id FROM warehouse_location WHERE warehouse_id=?",Long.class,warehouse);
        service.updateWarehouseLocation(code,location,new WarehouseLocationUpsertRequest("LOC-"+code,"普通货位",BigDecimal.valueOf(20),product,1));
        assertThat(jdbc.queryForObject("SELECT capacity_limit FROM warehouse_location WHERE location_id=?",BigDecimal.class,location)).isEqualByComparingTo("20");
        service.deleteWarehouseLocation(code,location);
        assertThat(jdbc.queryForObject("SELECT deleted FROM warehouse_location WHERE location_id=?",Integer.class,location)).isEqualTo(1);
    }); }
    @Test void quotaPackages() throws Exception { check("定数模板安全量打包打印解包", jdbc -> {
        String product=product(jdbc), code=code(), dept=code();var support=support(jdbc);
        new DepartmentService(jdbc).createDepartment(department(dept,dept));
        new WarehouseService(jdbc).createWarehouse(new WarehouseUpsertRequest(code,code,"一级库","核查院区",null,null,true,"[]",1,List.of(product)));
        long warehouse=jdbc.queryForObject("SELECT warehouse_id FROM warehouse WHERE warehouse_code=?",Long.class,code);
        long productId=jdbc.queryForObject("SELECT product_id FROM product WHERE product_code=?",Long.class,product);
        var templates=new QuotaTemplateService(jdbc,support);
        var template=templates.createTemplate(new QuotaTemplateRequest("",null,null,product,BigDecimal.TEN,"个"));String templateCode=(String)template.get("templateCode");
        assertThat(jdbc.queryForObject("SELECT quantity FROM quota_package_template_item WHERE template_id=?",BigDecimal.class,template.get("templateId"))).isEqualByComparingTo("10");
        template=templates.createTemplate(new QuotaTemplateRequest(templateCode,null,null,product,BigDecimal.TEN,"个"));
        assertThat(((Number)template.get("versionNo")).intValue()).isEqualTo(2);
        templates.disableTemplate(templateCode);
        assertThat(jdbc.queryForObject("SELECT status FROM quota_package_template WHERE template_id=?",Integer.class,template.get("templateId"))).isZero();
        templates.enableTemplate(templateCode);
        new SafetyStockService(jdbc,support).saveSafety(new QuotaSafetyRequest(dept,dept,((Number)template.get("templateId")).longValue(),templateCode,product,BigDecimal.ONE,BigDecimal.TEN));
        assertThat(jdbc.queryForObject("SELECT max_qty FROM quota_safety_stock WHERE product_id=?",BigDecimal.class,productId)).isEqualByComparingTo("10");
        jdbc.update("INSERT INTO inventory_batch (system_batch_no,product_id,batch_unit_price,expire_date) VALUES (?,?,1,'2099-12-31')",code,productId);long batch=id(jdbc);
        jdbc.update("INSERT INTO inventory_balance (warehouse_id,product_id,batch_id,available_qty) VALUES (?,?,?,100)",warehouse,productId,batch);
        var packing=new PackingTaskService(jdbc,support);String task=(String)packing.createTask(new PackingTaskRequest(templateCode,code,BigDecimal.ONE,"回滚核查")).get("taskNo");
        assertThat(jdbc.queryForObject("SELECT locked_qty FROM inventory_balance WHERE warehouse_id=? AND batch_id=?",BigDecimal.class,warehouse,batch)).isEqualByComparingTo("10");
        var confirmed=packing.confirmTask(task);String label=((List<String>)confirmed.get("labels")).get(0);
        packing.printLabel(label);
        assertThat(jdbc.queryForObject("SELECT print_count FROM quota_package_label WHERE label_no=?",Integer.class,label)).isEqualTo(1);
        packing.unpack(label,new PackageActionRequest("回滚核查"));
        assertThat(jdbc.queryForObject("SELECT available_qty FROM inventory_balance WHERE warehouse_id=? AND batch_id=?",BigDecimal.class,warehouse,batch)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_event WHERE warehouse_id=?",Integer.class,warehouse)).isGreaterThan(0);
        String cancelled=(String)packing.createTask(new PackingTaskRequest(templateCode,code,BigDecimal.ONE,"回滚核查")).get("taskNo");
        packing.recalculateTask(cancelled);
        packing.cancelTask(cancelled,new PackageActionRequest("回滚核查"));
        assertThat(jdbc.queryForObject("SELECT status FROM quota_packing_task WHERE task_no=?",String.class,cancelled)).isEqualTo("cancelled");
        String terminated=(String)packing.createTask(new PackingTaskRequest(templateCode,code,BigDecimal.ONE,"回滚核查")).get("taskNo");
        packing.terminateTask(terminated,new PackageActionRequest("回滚核查"));
        assertThat(jdbc.queryForObject("SELECT status FROM quota_packing_task WHERE task_no=?",String.class,terminated)).isEqualTo("terminated");
        assertThat(jdbc.queryForObject("SELECT available_qty FROM inventory_balance WHERE warehouse_id=? AND batch_id=?",BigDecimal.class,warehouse,batch)).isEqualByComparingTo("100");
    }); }
    @Test void priceApprovalUpdatesHospitalCatalog() throws Exception { check("价格申请审批更新医院目录", jdbc -> {
        var numbers=new DocumentNumberService(jdbc);var operator=(com.hospital.spd.common.OperatorContextProvider)OperatorContext::system;
        var service=new ProductApprovalService(jdbc,operator,new ApprovalFlowGuard(jdbc,operator),new ProductCodeService(jdbc),numbers,new CatalogApprovalRouteService(jdbc,operator),new AuditLogService(jdbc),null,new CatalogApplicationSnapshotService(jdbc,mapper));
        String code=product(jdbc);
        var req=mapper.convertValue(Map.of("applicationType","价格调整","productCode",code,"productName","核查商品"+code,"specModel","6.0","unit","个","purchasePrice",2,"quotaManaged",true),PendingProductApplicationRequest.class);
        String application=(String)service.createApplication(req).get("applicationNo");
        assertThat(jdbc.queryForObject("SELECT purchase_price FROM product WHERE product_code=?",BigDecimal.class,code)).isEqualByComparingTo("1");
        service.processAction(application,new PendingProductApprovalActionRequest("approve","回滚核查"));
        assertThat(jdbc.queryForObject("SELECT purchase_price FROM product WHERE product_code=?",BigDecimal.class,code)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT approval_status FROM pending_product_application WHERE application_no=?",String.class,application)).isEqualTo("approved");
    }); }
    @Test void pendingCatalog() throws Exception { check("待审批目录新增", jdbc -> {
        var numbers=new DocumentNumberService(jdbc);var operator=(com.hospital.spd.common.OperatorContextProvider)OperatorContext::system;
        var service=new ProductApprovalService(jdbc,operator,new ApprovalFlowGuard(jdbc,operator),new ProductCodeService(jdbc),numbers,new CatalogApprovalRouteService(jdbc,operator),new AuditLogService(jdbc),null,new CatalogApplicationSnapshotService(jdbc,mapper));
        String code=code();var req=mapper.convertValue(Map.of("applicationType","新品准入","productCode",code,"productName",code,"specModel","6.0","unit","个","purchasePrice",1),PendingProductApplicationRequest.class);
        var result=service.createApplication(req);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_product_application WHERE application_no=?",Integer.class,result.get("applicationNo"))).isEqualTo(1);
    }); }
    @Test void hospitalCatalog() throws Exception { check("医院目录保存转审批", jdbc -> {
        var numbers=new DocumentNumberService(jdbc);var operator=(com.hospital.spd.common.OperatorContextProvider)OperatorContext::system;
        var service=new ProductService(jdbc,new ProductCodeService(jdbc),numbers,operator,new CatalogApprovalRouteService(jdbc,operator),new AuditLogService(jdbc),new CatalogApplicationSnapshotService(jdbc,mapper));
        String code=code();var req=mapper.convertValue(Map.of("productCode",code,"productName",code,"specModel","6.0","unit","个","purchasePrice",1),ProductCreateRequest.class);
        var result=service.createHospitalProduct(req);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_product_application WHERE application_no=?",Integer.class,result.get("applicationNo"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_code=?",Integer.class,code)).isZero();
    }); }
}
