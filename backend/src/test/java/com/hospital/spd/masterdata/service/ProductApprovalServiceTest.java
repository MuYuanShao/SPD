package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductApprovalServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProductApprovalService service;

    @Captor
    private ArgumentCaptor<Object[]> argsCaptor;

    @BeforeEach
    void setUp() {
        service = new ProductApprovalService(jdbcTemplate);
    }

    // ==================== Helper: build common test data ====================

    private List<ApprovalTimelineNode> timelineForStatus(String status) {
        return switch (status) {
            case "rejected", "returned" -> List.of(
                    new ApprovalTimelineNode("提交申请", "申请人1", "2025-06-01 10:00", "done"),
                    new ApprovalTimelineNode("审批结束", statusLabel(status), "-", "reject")
            );
            case "approved" -> List.of(
                    new ApprovalTimelineNode("提交申请", "申请人1", "2025-06-01 10:00", "done"),
                    new ApprovalTimelineNode("初审", "通过", "-", "done"),
                    new ApprovalTimelineNode("复审", "通过", "-", "done"),
                    new ApprovalTimelineNode("审批通过", "已完成", "-", "done")
            );
            default -> {
                if ("pending_initial".equals(status)) {
                    yield List.of(
                            new ApprovalTimelineNode("提交申请", "申请人1", "2025-06-01 10:00", "done"),
                            new ApprovalTimelineNode("初审", "待审批", "-", "active"),
                            new ApprovalTimelineNode("复审", "待处理", "-", "wait"),
                            new ApprovalTimelineNode("审批通过", "已处理", "-", "wait")
                    );
                }
                yield List.of(
                        new ApprovalTimelineNode("提交申请", "申请人1", "2025-06-01 10:00", "done"),
                        new ApprovalTimelineNode("初审", "初审通过", "-", "done"),
                        new ApprovalTimelineNode("复审", "待审批", "-", "active"),
                        new ApprovalTimelineNode("审批通过", "已处理", "-", "wait")
                );
            }
        };
    }

    private PendingProductApplicationDetail detail(String status, String applicationType) {
        return new PendingProductApplicationDetail(
                "APP001", applicationType, status,
                statusLabel(status), "测试商品", "P001",
                "10ml/支", "品牌A", "厂家A", "供应商A",
                "支", BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.ONE,
                "箱", BigDecimal.valueOf(10), "UDI001", "注册证号001", "2025-12-31",
                "生产许可001", "经营许可001",
                true, true, true, "合同001",
                "一级", "二级", "三级", true, "招采001", 2,
                false, false, false, "常温",
                "申请人1", "2025-06-01 10:00",
                null, null, null, null, null,
                List.of(),
                timelineForStatus(status),
                true
        );
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "pending_initial" -> "待审批";
            case "pending_final" -> "流转中";
            case "rejected" -> "已驳回";
            case "returned" -> "退回修改";
            case "approved" -> "已通过";
            default -> status;
        };
    }

    private PendingProductApplicationRequest validApplicationRequest() {
        return new PendingProductApplicationRequest(
                "new", "P001", "测试商品", "10ml/支",
                "品牌A", "厂家A", "供应商A",
                "支", BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.ONE,
                "箱", BigDecimal.valueOf(10), "UDI001", "注册证号001", "2025-12-31",
                "生产许可001", "经营许可001",
                true, true, true, "合同001",
                "一级", "二级", "三级", true, "招采001", 2,
                false, false, false, "常温", "新增测试商品"
        );
    }

    // ======================== getDetail ========================

    @Nested
    @DisplayName("申请单详情 getDetail()")
    class GetDetailTest {

        @Test
        @DisplayName("待初审状态返回正确时间线")
        void should_return_detail_with_pending_initial_timeline() {
            PendingProductApplicationDetail expected = detail("pending_initial", "新品准入");

            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(expected);

            PendingProductApplicationDetail result = service.getDetail("APP001");

            assertThat(result.applicationNo()).isEqualTo("APP001");
            assertThat(result.approvalStatus()).isEqualTo("pending_initial");
            assertThat(result.statusLabel()).isEqualTo("待审批");
            assertThat(result.timeline()).hasSize(4);
            assertThat(result.timeline().get(1).title()).isEqualTo("初审");
            assertThat(result.timeline().get(1).status()).isEqualTo("active");
        }

        @Test
        @DisplayName("待终审状态返回正确时间线")
        void should_return_detail_with_pending_final_timeline() {
            PendingProductApplicationDetail expected = detail("pending_final", "新品准入");

            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(expected);

            PendingProductApplicationDetail result = service.getDetail("APP001");

            assertThat(result.approvalStatus()).isEqualTo("pending_final");
            assertThat(result.timeline().get(1).status()).isEqualTo("done");
            assertThat(result.timeline().get(2).status()).isEqualTo("active");
        }

        @Test
        @DisplayName("已驳回状态返回正确时间线")
        void should_return_detail_with_rejected_timeline() {
            PendingProductApplicationDetail expected = detail("rejected", "新品准入");

            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(expected);

            PendingProductApplicationDetail result = service.getDetail("APP001");

            assertThat(result.approvalStatus()).isEqualTo("rejected");
            assertThat(result.statusLabel()).isEqualTo("已驳回");
            assertThat(result.timeline()).hasSize(2);
            assertThat(result.timeline().get(1).status()).isEqualTo("reject");
        }

        @Test
        @DisplayName("已通过状态返回正确时间线")
        void should_return_detail_with_approved_timeline() {
            PendingProductApplicationDetail expected = detail("approved", "新品准入");

            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(expected);

            PendingProductApplicationDetail result = service.getDetail("APP001");

            assertThat(result.approvalStatus()).isEqualTo("approved");
            assertThat(result.statusLabel()).isEqualTo("已通过");
            assertThat(result.timeline()).hasSize(4);
            assertThat(result.timeline().get(3).status()).isEqualTo("done");
        }
    }

    // ======================== listApplications ========================

    @Nested
    @DisplayName("分页申请列表 listApplications()")
    class ListApplicationsTest {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("todo 范围查询待审批列表")
        void should_list_pending_applications_when_scope_todo() {
            // Arrange
            List<PendingProductApplicationRow> rows = List.of(
                    new PendingProductApplicationRow("APP001", "新品准入", "测试商品",
                            "供应商A", "申请人1", "2025-06-01 10:00", "待审批", "orange", true, null, null)
            );

            when(jdbcTemplate.query(
                    contains("SELECT a.application_no"),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(rows);

            when(jdbcTemplate.queryForObject(
                    contains("FROM pending_product_application a"),
                    eq(Long.class),
                    any(Object[].class)
            )).thenReturn(1L);

            Map<String, Integer> counts = new java.util.HashMap<>();
            counts.put("新品准入", 3);
            counts.put("信息变更", 1);

            when(jdbcTemplate.query(
                    contains("application_type, COUNT(*)"),
                    any(ResultSetExtractor.class)
            )).thenReturn(counts);

            Map<String, Integer> mineCounts = new java.util.HashMap<>();
            mineCounts.put("mine_pending", 2);
            mineCounts.put("mine_returned", 0);
            mineCounts.put("mine_approved", 5);

            when(jdbcTemplate.query(
                    contains("SUM(CASE WHEN"),
                    any(ResultSetExtractor.class),
                    any(Object[].class)
            )).thenReturn(mineCounts);

            when(jdbcTemplate.queryForObject(
                    contains("SELECT COUNT(*)"),
                    eq(Integer.class)
            )).thenReturn(10);

            // Act
            PendingProductApplicationPage result = service.listApplications("new", "todo", "", "");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().get(0).no()).isEqualTo("APP001");
            assertThat(result.typeCounts()).hasSize(9);

            PendingProductTypeCount newCount = result.typeCounts().stream()
                    .filter(tc -> "new".equals(tc.key())).findFirst().orElseThrow();
            assertThat(newCount.count()).isEqualTo(3);

            PendingProductTypeCount minePending = result.typeCounts().stream()
                    .filter(tc -> "mine-pending".equals(tc.key())).findFirst().orElseThrow();
            assertThat(minePending.count()).isEqualTo(2);

            PendingProductTypeCount handled = result.typeCounts().stream()
                    .filter(tc -> "handled".equals(tc.key())).findFirst().orElseThrow();
            assertThat(handled.count()).isEqualTo(10);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("mine 范围查询我的待处理")
        void should_list_mine_applications_when_scope_mine() {
            when(jdbcTemplate.query(
                    contains("SELECT a.application_no"),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            when(jdbcTemplate.queryForObject(
                    contains("FROM pending_product_application a"),
                    eq(Long.class),
                    any(Object[].class)
            )).thenReturn(0L);

            when(jdbcTemplate.query(
                    contains("application_type, COUNT(*)"),
                    any(ResultSetExtractor.class)
            )).thenReturn(new java.util.HashMap<>());

            Map<String, Integer> mineCounts = new java.util.HashMap<>();
            mineCounts.put("mine_pending", 1);
            mineCounts.put("mine_returned", 2);
            mineCounts.put("mine_approved", 3);

            when(jdbcTemplate.query(
                    contains("SUM(CASE WHEN"),
                    any(ResultSetExtractor.class),
                    any(Object[].class)
            )).thenReturn(mineCounts);

            when(jdbcTemplate.queryForObject(
                    contains("SELECT COUNT(*)"),
                    eq(Integer.class)
            )).thenReturn(6);

            PendingProductApplicationPage result = service.listApplications("new", "mine", "pending", "");

            assertThat(result.rows()).isEmpty();
            PendingProductTypeCount mineReturned = result.typeCounts().stream()
                    .filter(tc -> "mine-returned".equals(tc.key())).findFirst().orElseThrow();
            assertThat(mineReturned.count()).isEqualTo(2);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("handled 范围查询已处理列表")
        void should_list_handled_applications_when_scope_handled() {
            when(jdbcTemplate.query(
                    contains("SELECT a.application_no"),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            when(jdbcTemplate.queryForObject(
                    contains("FROM pending_product_application a"),
                    eq(Long.class),
                    any(Object[].class)
            )).thenReturn(0L);

            when(jdbcTemplate.query(
                    contains("application_type, COUNT(*)"),
                    any(ResultSetExtractor.class)
            )).thenReturn(new java.util.HashMap<>());

            Map<String, Integer> mineCounts = new java.util.HashMap<>();
            mineCounts.put("mine_pending", 0);
            mineCounts.put("mine_returned", 0);
            mineCounts.put("mine_approved", 0);

            when(jdbcTemplate.query(
                    contains("SUM(CASE WHEN"),
                    any(ResultSetExtractor.class),
                    any(Object[].class)
            )).thenReturn(mineCounts);

            when(jdbcTemplate.queryForObject(
                    contains("SELECT COUNT(*)"),
                    eq(Integer.class)
            )).thenReturn(15);

            PendingProductApplicationPage result = service.listApplications("new", "handled", "", "");

            PendingProductTypeCount handled = result.typeCounts().stream()
                    .filter(tc -> "handled".equals(tc.key())).findFirst().orElseThrow();
            assertThat(handled.count()).isEqualTo(15);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("关键词搜索过滤")
        void should_filter_by_keyword() {
            when(jdbcTemplate.query(
                    contains("SELECT a.application_no"),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            when(jdbcTemplate.queryForObject(
                    contains("FROM pending_product_application a"),
                    eq(Long.class),
                    any(Object[].class)
            )).thenReturn(0L);

            when(jdbcTemplate.query(
                    contains("application_type, COUNT(*)"),
                    any(ResultSetExtractor.class)
            )).thenReturn(new java.util.HashMap<>());

            Map<String, Integer> mineCounts = new java.util.HashMap<>();
            mineCounts.put("mine_pending", 0);
            mineCounts.put("mine_returned", 0);
            mineCounts.put("mine_approved", 0);

            when(jdbcTemplate.query(
                    contains("SUM(CASE WHEN"),
                    any(ResultSetExtractor.class),
                    any(Object[].class)
            )).thenReturn(mineCounts);

            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

            PendingProductApplicationPage result = service.listApplications("new", "todo", "", "测试");

            assertThat(result).isNotNull();
        }
    }

    // ======================== createApplication ========================

    @Nested
    @DisplayName("创建申请 createApplication()")
    class CreateApplicationTest {

        @Test
        @DisplayName("创建申请成功返回申请单号")
        void should_create_application_successfully() {
            PendingProductApplicationRequest request = validApplicationRequest();

            // nextApplicationNo
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
            // findIdByName manufacturer
            when(jdbcTemplate.queryForList(
                    contains("manufacturer_id FROM manufacturer"),
                    eq(Long.class), any(Object[].class))
            ).thenReturn(List.of(10L));
            // findIdByName supplier
            when(jdbcTemplate.queryForList(
                    contains("supplier_id FROM supplier"),
                    eq(Long.class), any(Object[].class))
            ).thenReturn(List.of(20L));
            // ensureCategory
            when(jdbcTemplate.queryForList(
                    contains("FROM product_category"),
                    eq(Long.class), any(Object[].class))
            ).thenReturn(List.of(30L));
            // insert
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.createApplication(request);

            assertThat(result).containsKey("applicationNo");
            assertThat((String) result.get("applicationNo")).startsWith("SP");
        }

        @Test
        @DisplayName("缺少必填字段抛出异常")
        void should_throw_when_required_fields_missing() {
            PendingProductApplicationRequest request = new PendingProductApplicationRequest(
                    "new", "", "测试商品", "10ml/支",
                    null, null, null,
                    "", null, null, null,
                    null, null, null, null, null,
                    null, null,
                    null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null, null
            );

            assertThatThrownBy(() -> service.createApplication(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("必填项");
        }

        @Test
        @DisplayName("高值耗材设置为定数管理抛出异常")
        void should_throw_when_quota_managed_with_high_value() {
            PendingProductApplicationRequest request = new PendingProductApplicationRequest(
                    "new", "P001", "测试商品", "10ml/支",
                    null, null, null,
                    "支", null, null, null,
                    null, null, null, null, null,
                    null, null,
                    null, null, null, null,
                    null, null, null, null, null, null,
                    true, false, true, null, "测试"
            );

            // Stub calls needed before validateQuotaEligibility is reached
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(99L));

            assertThatThrownBy(() -> service.createApplication(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("高值耗材或冷链耗材不能设置为定数管理");
        }

        @Test
        @DisplayName("商品名称规格单价注册证厂家供应商完全一致时判定目录已存在")
        void should_throw_when_same_catalog_already_exists() {
            PendingProductApplicationRequest request = validApplicationRequest();

            when(jdbcTemplate.queryForObject(
                    contains("pending_product_application WHERE application_no"),
                    eq(Integer.class),
                    any(Object[].class)
            )).thenReturn(0);
            when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(99L));
            when(jdbcTemplate.queryForObject(
                    contains("FROM product p"),
                    eq(Integer.class),
                    any(Object[].class)
            )).thenReturn(1);

            assertThatThrownBy(() -> service.createApplication(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("商品目录已存在");
        }

        @Test
        @DisplayName("待审批目录已有相同商品时判定目录已存在")
        void should_throw_when_same_pending_application_already_exists() {
            PendingProductApplicationRequest request = validApplicationRequest();

            when(jdbcTemplate.queryForObject(
                    contains("FROM product p"),
                    eq(Integer.class),
                    any(Object[].class)
            )).thenReturn(0);
            when(jdbcTemplate.queryForObject(
                    contains("FROM pending_product_application a"),
                    eq(Integer.class),
                    any(Object[].class)
            )).thenReturn(1);

            assertThatThrownBy(() -> service.createApplication(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("商品目录已存在");
            verify(jdbcTemplate).queryForObject(
                    argThat(sql -> sql.contains("FROM pending_product_application a")
                            && sql.contains("COALESCE(NULLIF(TRIM(a.registration_no), ''), '') = ?")
                            && sql.contains("COALESCE(TRIM(s.supplier_name), TRIM(a.supplier_name))")),
                    eq(Integer.class),
                    any(Object[].class)
            );
        }
    }

    // ======================== processAction (THE KEY TEST) ========================

    @Nested
    @DisplayName("审批动作 processAction()")
    class ProcessActionTest {

        private PendingProductApprovalActionRequest action(String action, String opinion) {
            return new PendingProductApprovalActionRequest(action, opinion);
        }

        @Test
        @DisplayName("驳回：状态变为 rejected，必须填写意见")
        void should_reject_application_when_action_is_reject() {
            // Arrange
            PendingProductApplicationDetail detail = detail("pending_initial", "新品准入");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("rejected"), eq("不符合要求"), eq("不符合要求"), eq("APP001")
            )).thenReturn(1);

            when(jdbcTemplate.queryForObject(
                    contains("SELECT application_id FROM pending_product_application"),
                    eq(Long.class), eq("APP001")
            )).thenReturn(5L);

            when(jdbcTemplate.update(
                    contains("INSERT INTO audit_log"),
                    anyString(), any(), anyString(), anyString()
            )).thenReturn(1);

            // Act
            Map<String, Object> result = service.processAction("APP001", action("reject", "不符合要求"));

            // Assert
            assertThat(result.get("status")).isEqualTo("rejected");
        }

        @Test
        @DisplayName("驳回不填写意见抛出异常")
        void should_throw_when_reject_without_opinion() {
            PendingProductApplicationDetail detail = detail("pending_initial", "新品准入");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            assertThatThrownBy(() -> service.processAction("APP001", action("reject", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("驳回必须填写审批意见");
        }

        @Test
        @DisplayName("退回：状态变为 returned，必须填写退回原因")
        void should_return_application_when_action_is_return() {
            PendingProductApplicationDetail detail = detail("pending_initial", "新品准入");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("returned"), eq("需要修改"), eq("需要修改"), eq("APP001")
            )).thenReturn(1);

            when(jdbcTemplate.queryForObject(
                    contains("SELECT application_id FROM pending_product_application"),
                    eq(Long.class), eq("APP001")
            )).thenReturn(5L);

            when(jdbcTemplate.update(
                    contains("INSERT INTO audit_log"),
                    anyString(), any(), anyString(), anyString()
            )).thenReturn(1);

            Map<String, Object> result = service.processAction("APP001", action("return", "需要修改"));

            assertThat(result.get("status")).isEqualTo("returned");
        }

        @Test
        @DisplayName("退回不填写意见抛出异常")
        void should_throw_when_return_without_opinion() {
            PendingProductApplicationDetail detail = detail("pending_initial", "新品准入");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            assertThatThrownBy(() -> service.processAction("APP001", action("return", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("退回修改必须填写退回原因");
        }

        @Test
        @DisplayName("初审通过：状态变为 pending_final")
        void should_initial_approve_when_action_is_approve_and_status_pending_initial() {
            PendingProductApplicationDetail detail = detail("pending_initial", "新品准入");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("pending_final"), isNull(), eq("APP001")
            )).thenReturn(1);

            when(jdbcTemplate.queryForObject(
                    contains("SELECT application_id FROM pending_product_application"),
                    eq(Long.class), eq("APP001")
            )).thenReturn(5L);

            when(jdbcTemplate.update(
                    contains("INSERT INTO audit_log"),
                    anyString(), any(), anyString(), anyString()
            )).thenReturn(1);

            Map<String, Object> result = service.processAction("APP001", action("approve", null));

            assertThat(result.get("status")).isEqualTo("pending_final");
        }

        @Test
        @DisplayName("按审批流配置推进：第二级通过后进入第三级")
        void should_move_to_next_configured_step_instead_of_hardcoded_final_status() {
            PendingProductApplicationDetail detail = detail("pending_step_2", "新品准入");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);
            when(jdbcTemplate.queryForList(
                    contains("FROM approval_flow af"),
                    eq("pending-product-catalog"),
                    eq("initial-review")
            )).thenReturn(List.of(Map.of("flowId", 88L, "stepCount", 3L)));
            when(jdbcTemplate.queryForList(
                    contains("FROM approval_flow_step"),
                    eq(88L)
            )).thenReturn(List.of(
                    Map.of("stepOrder", 1, "stepName", "设备科审批"),
                    Map.of("stepOrder", 2, "stepName", "财务审批"),
                    Map.of("stepOrder", 3, "stepName", "院领导审批")
            ));
            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("pending_step_3"), isNull(), eq("APP001")
            )).thenReturn(1);
            when(jdbcTemplate.queryForObject(
                    contains("SELECT application_id FROM pending_product_application"),
                    eq(Long.class), eq("APP001")
            )).thenReturn(5L);
            when(jdbcTemplate.update(
                    contains("INSERT INTO audit_log"),
                    anyString(), any(), anyString(), anyString()
            )).thenReturn(1);

            Map<String, Object> result = service.processAction("APP001", action("approve", null));

            assertThat(result.get("status")).isEqualTo("pending_step_3");
        }

        @Test
        @DisplayName("终审通过：状态变为 approved，同步到商品目录")
        void should_final_approve_and_sync_to_catalog() {
            PendingProductApplicationDetail detail = detail("pending_final", "新品准入");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            // processAction update
            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("approved"), isNull(), isNull(), eq(1), any(), isNull(), eq("APP001")
            )).thenReturn(1);

            // syncToHospitalCatalog -> ensureCategory
            when(jdbcTemplate.queryForList(
                    contains("FROM product_category"),
                    eq(Long.class), eq("未分类"))
            ).thenReturn(List.of(99L));

            // syncToHospitalCatalog -> INSERT INTO product ... ON DUPLICATE KEY
            when(jdbcTemplate.update(
                    contains("INSERT INTO product"),
                    eq(99L), eq("APP001")
            )).thenReturn(1);

            // writeAudit -> get application_id
            when(jdbcTemplate.queryForObject(
                    contains("SELECT application_id FROM pending_product_application"),
                    eq(Long.class), eq("APP001")
            )).thenReturn(5L);

            // writeAudit -> INSERT INTO audit_log
            when(jdbcTemplate.update(
                    contains("INSERT INTO audit_log"),
                    anyString(), any(), anyString(), anyString()
            )).thenReturn(1);

            Map<String, Object> result = service.processAction("APP001", action("approve", null));

            assertThat(result.get("status")).isEqualTo("approved");
        }

        @Test
        @DisplayName("终审通过（停用申请）：禁用商品到目录")
        void should_disable_product_when_final_approve_disable_application() {
            PendingProductApplicationDetail detail = detail("pending_final", "停用申请");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            // processAction update (停用申请 final approve)
            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("approved"), isNull(), isNull(), eq(1), any(), isNull(), eq("APP001")
            )).thenReturn(1);

            // disableHospitalCatalogProduct
            when(jdbcTemplate.update(
                    contains("UPDATE product p JOIN pending_product_application a"),
                    eq("APP001")
            )).thenReturn(1);

            // writeAudit -> get application_id
            when(jdbcTemplate.queryForObject(
                    contains("SELECT application_id FROM pending_product_application"),
                    eq(Long.class), eq("APP001")
            )).thenReturn(5L);

            // writeAudit -> INSERT INTO audit_log
            when(jdbcTemplate.update(
                    contains("INSERT INTO audit_log"),
                    anyString(), any(), anyString(), anyString()
            )).thenReturn(1);

            Map<String, Object> result = service.processAction("APP001", action("approve", null));

            assertThat(result.get("status")).isEqualTo("approved");
        }

        @Test
        @DisplayName("已结束的审批单不能继续审批")
        void should_throw_when_application_already_closed() {
            PendingProductApplicationDetail detail = detail("approved", "新品准入");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            assertThatThrownBy(() -> service.processAction("APP001", action("approve", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("已结束");
        }

        @Test
        @DisplayName("无效审批动作抛出异常")
        void should_throw_when_action_is_invalid() {
            PendingProductApplicationDetail detail = detail("pending_initial", "新品准入");
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            assertThatThrownBy(() -> service.processAction("APP001", action("invalid_action", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("审批动作不正确");
        }
    }

    // ======================== resubmitApplication ========================

    @Nested
    @DisplayName("重新提交 resubmitApplication()")
    class ResubmitApplicationTest {

        @Test
        @DisplayName("退回状态可重新提交")
        void should_resubmit_when_status_is_returned() {
            PendingProductApplicationDetail detail = detail("returned", "新品准入");

            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);
            when(jdbcTemplate.queryForList(
                    contains("manufacturer_id FROM manufacturer"),
                    eq(Long.class), any(Object[].class))
            ).thenReturn(List.of(10L));
            when(jdbcTemplate.queryForList(
                    contains("supplier_id FROM supplier"),
                    eq(Long.class), any(Object[].class))
            ).thenReturn(List.of(20L));
            when(jdbcTemplate.queryForList(
                    contains("FROM product_category"),
                    eq(Long.class), any(Object[].class))
            ).thenReturn(List.of(30L));

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            Map<String, Object> result = service.resubmitApplication("APP001", validApplicationRequest());

            assertThat(result.get("status")).isEqualTo("pending_initial");
            assertThat(result.get("applicationNo")).isEqualTo("APP001");
        }

        @Test
        @DisplayName("非退回状态不能重新提交")
        void should_throw_when_status_not_returned() {
            PendingProductApplicationDetail detail = detail("pending_initial", "新品准入");

            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(detail);

            assertThatThrownBy(() -> service.resubmitApplication("APP001", validApplicationRequest()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("只有退回修改状态的审批单可以重新提交");
        }
    }

    // ======================== batchProcessAction ========================

    @Nested
    @DisplayName("批量审批 batchProcessAction()")
    class BatchProcessActionTest {

        private PendingProductApprovalActionRequest action(String action, String opinion) {
            return new PendingProductApprovalActionRequest(action, opinion);
        }

        private PendingProductApplicationDetail pendingInitialDetail() {
            return detail("pending_initial", "新品准入");
        }

        /** Stub all dependencies needed for processAction("approve") on one application. */
        private void stubSingleApprove(String applicationNo) {
            when(jdbcTemplate.queryForObject(
                    contains("SELECT application_id FROM pending_product_application"),
                    eq(Long.class), eq(applicationNo)
            )).thenReturn(1L);

            when(jdbcTemplate.update(
                    contains("INSERT INTO audit_log"),
                    anyString(), anyString(), anyString(), anyString()
            )).thenReturn(1);
        }

        @Test
        @DisplayName("批量通过：全部成功")
        void should_approve_all_when_all_valid() {
            // Arrange
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(pendingInitialDetail());
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP002")))
                    .thenReturn(pendingInitialDetail());

            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("pending_final"), isNull(), eq("APP001")
            )).thenReturn(1);
            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("pending_final"), isNull(), eq("APP002")
            )).thenReturn(1);

            stubSingleApprove("APP001");
            stubSingleApprove("APP002");

            // Act
            Map<String, Object> result = service.batchProcessAction(
                    List.of("APP001", "APP002"),
                    action("approve", null)
            );

            // Assert
            assertThat(result.get("successCount")).isEqualTo(2);
            assertThat(result.get("failCount")).isEqualTo(0);
        }

        @Test
        @DisplayName("批量操作：部分失败时成功计数正确")
        void should_partially_succeed_when_some_fail() {
            // Arrange — APP003 does not exist (queryForObject throws)
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(pendingInitialDetail());
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP002")))
                    .thenReturn(pendingInitialDetail());
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP003")))
                    .thenThrow(new IllegalArgumentException("申请单不存在"));

            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("pending_final"), isNull(), eq("APP001")
            )).thenReturn(1);
            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("pending_final"), isNull(), eq("APP002")
            )).thenReturn(1);

            stubSingleApprove("APP001");
            stubSingleApprove("APP002");

            // Act
            Map<String, Object> result = service.batchProcessAction(
                    List.of("APP001", "APP002", "APP003"),
                    action("approve", null)
            );

            // Assert
            assertThat(result.get("successCount")).isEqualTo(2);
            assertThat(result.get("failCount")).isEqualTo(1);
        }

        @Test
        @DisplayName("批量操作：全部失败")
        void should_all_fail_when_none_valid() {
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString()))
                    .thenThrow(new IllegalArgumentException("申请单不存在"));

            Map<String, Object> result = service.batchProcessAction(
                    List.of("APP001", "APP002"),
                    action("approve", null)
            );

            assertThat(result.get("successCount")).isEqualTo(0);
            assertThat(result.get("failCount")).isEqualTo(2);
        }

        @Test
        @DisplayName("批量操作：空列表返回 0")
        void should_return_zero_when_empty_list() {
            Map<String, Object> result = service.batchProcessAction(
                    List.of(),
                    action("approve", null)
            );

            assertThat(result.get("successCount")).isEqualTo(0);
            assertThat(result.get("failCount")).isEqualTo(0);
        }

        @Test
        @DisplayName("批量操作：单条审批")
        void should_approve_single_item() {
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("APP001")))
                    .thenReturn(pendingInitialDetail());

            when(jdbcTemplate.update(
                    contains("UPDATE pending_product_application"),
                    eq("pending_final"), isNull(), eq("APP001")
            )).thenReturn(1);

            stubSingleApprove("APP001");

            Map<String, Object> result = service.batchProcessAction(
                    List.of("APP001"),
                    action("approve", null)
            );

            assertThat(result.get("successCount")).isEqualTo(1);
            assertThat(result.get("failCount")).isEqualTo(0);
        }
    }
}
