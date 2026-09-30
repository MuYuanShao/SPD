package com.hospital.spd.masterdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.common.service.DocumentNumberService;
import com.hospital.spd.masterdata.PendingProductApplicationRequest;
import com.hospital.spd.masterdata.PendingProductApprovalActionRequest;
import com.hospital.spd.system.ApprovalFlowRequest;
import com.hospital.spd.system.ApprovalFlowStepRequest;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import com.hospital.spd.system.service.ApprovalFlowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Actual catalog submissions, approvals and resubmission use a private department flow and roll back together. */
@EnabledIfEnvironmentVariable(named="SPD_CATALOG_FLOW_MYSQL_TESTS", matches="true")
class CatalogSingleFlowLifecycleMysqlTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Test void inFlightFourStepsNewThreeStepsAndResubmissionUseTheirOwnRoutes() throws Exception {
        try (var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/ISPD?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",System.getenv("SPD_DB_USERNAME"),System.getenv("SPD_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                String code="SF-"+UUID.randomUUID().toString().substring(0,12);
                jdbc.update("INSERT INTO sys_dept (dept_code,dept_name,status,deleted) VALUES (?,?,1,0)",code,code);
                Long dept=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
                OperatorContextProvider operator=()->new OperatorContext(1L,"system","127.0.0.1",List.of("ROLE_SYSTEM"),dept,1);
                var settings=new ApprovalFlowService(jdbc,operator);
                var four=List.of(step(1,"设备科"),step(2,"采购科"),step(3,"物价科"),step(4,"医保科"));
                Long flow=((Number)settings.create(flow(dept,four)).get("flowId")).longValue();
                var routes=new CatalogApprovalRouteService(jdbc,operator);
                var service=new ProductApprovalService(jdbc,operator,new ApprovalFlowGuard(jdbc,operator),new ProductCodeService(jdbc),
                        new DocumentNumberService(jdbc),routes,new AuditLogService(jdbc,operator),null,new CatalogApplicationSnapshotService(jdbc,mapper));
                var oldRequest=request(code+"-OLD");
                String oldNo=(String)service.createApplication(oldRequest).get("applicationNo");
                String returnedNo=(String)service.createApplication(request(code+"-RET")).get("applicationNo");
                Long oldId=id(jdbc,oldNo);
                assertThat(routes.configuredSteps(oldId,1)).hasSize(4);
                settings.update(flow,flow(dept,List.of(step(1,"设备科"),step(2,"采购科"),step(4,"医保科"))));
                String newNo=(String)service.createApplication(request(code+"-NEW")).get("applicationNo");
                assertThat(routes.configuredSteps(id(jdbc,newNo),1)).hasSize(3);
                assertThat(routes.configuredSteps(oldId,1)).hasSize(4);
                for(int order=1;order<=3;order++) service.processAction(oldNo,new PendingProductApprovalActionRequest("approve","同意"));
                assertThat(service.getDetail(oldNo).approvalStatus()).isEqualTo("pending_step_4");
                service.processAction(oldNo,new PendingProductApprovalActionRequest("approve","同意"));
                assertThat(service.getDetail(oldNo).approvalStatus()).isEqualTo("approved");
                assertThat(service.getDetail(oldNo).timeline()).hasSize(6);
                for(int order=1;order<=3;order++) service.processAction(newNo,new PendingProductApprovalActionRequest("approve","同意"));
                assertThat(service.getDetail(newNo).approvalStatus()).isEqualTo("approved");
                assertThat(service.getDetail(newNo).timeline()).hasSize(5);
                service.processAction(returnedNo,new PendingProductApprovalActionRequest("return","补充资料"));
                service.resubmitApplication(returnedNo,request(code+"-RET"));
                assertThat(routes.configuredSteps(id(jdbc,returnedNo),1)).hasSize(4);
                assertThat(routes.configuredSteps(id(jdbc,returnedNo),2)).hasSize(3);
                assertThat(service.getDetail(returnedNo).approvalStatus()).isEqualTo("pending_step_1");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_code IN (?,?)",Integer.class,code+"-OLD",code+"-NEW")).isEqualTo(2);
                assertThatThrownBy(()->settings.create(flow(dept,four))).hasMessageContaining("已有启用");
                System.out.println("CATALOG FLOW VERIFIED: old 4-step completed; new 3-step completed; returned round uses latest 3-step; product rows persisted within rollback transaction");
            } finally { connection.rollback(); }
        }
    }
    private long id(JdbcTemplate jdbc,String no) { return jdbc.queryForObject("SELECT application_id FROM pending_product_application WHERE application_no=?",Long.class,no); }
    private PendingProductApplicationRequest request(String code) {
        return mapper.convertValue(Map.of("applicationType","新品准入","productCode",code,"productName",code,"specModel","6.0","unit","个","purchasePrice",1,"registrationExpireDate","2099-12-31"),PendingProductApplicationRequest.class);
    }
    private ApprovalFlowStepRequest step(int order,String name) { return new ApprovalFlowStepRequest(null,order,name,"user",null,1L,null,1,false,1,1); }
    private ApprovalFlowRequest flow(Long dept,List<ApprovalFlowStepRequest> steps) {
        return new ApprovalFlowRequest("pending-product-catalog","待审批目录","initial-review","目录顺序审批","department",dept.toString(),1,1,"回滚测试",dept,steps);
    }
}
