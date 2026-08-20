package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.ApprovalTimelineNode;
import com.hospital.spd.masterdata.PendingProductApplicationDetail;
import com.hospital.spd.masterdata.PendingProductApplicationPage;
import com.hospital.spd.masterdata.PendingProductApplicationRequest;
import com.hospital.spd.masterdata.PendingProductApplicationRow;
import com.hospital.spd.masterdata.PendingProductApprovalActionRequest;
import com.hospital.spd.masterdata.PendingProductChangeItem;
import com.hospital.spd.masterdata.PendingProductTypeCount;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import static com.hospital.spd.common.SqlHelper.isBlank;
import static com.hospital.spd.common.SqlHelper.nullIfBlank;
import static com.hospital.spd.masterdata.service.ProductApprovalWorkflowSteps.currentStepOrder;
import static com.hospital.spd.masterdata.service.ProductApprovalWorkflowSteps.isFinalApprovalStep;
import static com.hospital.spd.masterdata.service.ProductApprovalWorkflowSteps.isPendingStatus;
import static com.hospital.spd.masterdata.service.ProductApprovalWorkflowSteps.legacyApprovalSteps;
import static com.hospital.spd.masterdata.service.ProductApprovalWorkflowSteps.nextPendingStatus;
import static com.hospital.spd.masterdata.service.ProductApprovalWorkflowSteps.nextStepOrder;
import static com.hospital.spd.masterdata.service.ProductApprovalWorkflowSteps.pendingStatus;
import static com.hospital.spd.masterdata.service.ProductApprovalWorkflowSteps.stepForStatus;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.addChange;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.boolLabel;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.defaultDecimal;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.firstNonBlank;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.integer;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.matchText;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.number;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.parseDate;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.text;
import static com.hospital.spd.masterdata.service.ProductApprovalStatus.toStatusLabel;
import static com.hospital.spd.masterdata.service.ProductApprovalStatus.toStatusTone;
import static com.hospital.spd.masterdata.service.ProductApprovalChangeSummary.computeChangeSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes product catalog approval workflows, including submit, return, reject, approve, and resubmit.
 */
@Service
public class ProductApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ProductApprovalService.class);

    private static final Map<String, String> TYPE_MAP = Map.of(
            "new", "新品准入",
            "change", "信息变更",
            "qualification", "资质更新",
            "price", "价格调整",
            "disable", "停用申请"
    );
    private static final String CATALOG_FEATURE_CODE = "pending-product-catalog";
    private static final String CATALOG_NODE_CODE = "initial-review";

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;
    private final ApprovalFlowGuard approvalFlowGuard;
    private final ProductApprovalChangeItems changeItems;

    public ProductApprovalService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, OperatorContext::system, new ApprovalFlowGuard(jdbcTemplate));
    }

    public ProductApprovalService(JdbcTemplate jdbcTemplate, OperatorContextProvider operatorContextProvider) {
        this(jdbcTemplate, operatorContextProvider, new ApprovalFlowGuard(jdbcTemplate, operatorContextProvider));
    }

    @Autowired
    public ProductApprovalService(JdbcTemplate jdbcTemplate,
                                  OperatorContextProvider operatorContextProvider,
                                  ApprovalFlowGuard approvalFlowGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
        this.approvalFlowGuard = approvalFlowGuard;
        this.changeItems = new ProductApprovalChangeItems(jdbcTemplate);
    }

    // ======================== Public API ========================

    public PendingProductApplicationDetail getDetail(String applicationNo) {
        return jdbcTemplate.queryForObject("""
                        SELECT a.application_no, a.application_type, a.approval_status,
                               a.product_name, a.product_code, a.spec_model, a.brand, a.manufacturer_name,
                               COALESCE(s.supplier_name, a.supplier_name, '') AS supplier_name,
                               a.unit, a.purchase_price, a.retail_price, a.min_purchase_qty, a.purchase_unit,
                               a.conversion_rate, a.udi_code, a.registration_no, a.registration_expire_date,
                               a.production_license_no, a.business_license_no, a.qualification_attachment_count,
                               a.is_volume_based, a.is_centralized_procurement, a.is_domestic, a.contract_code,
                               a.first_category, a.second_category, a.third_category, a.is_chargeable, a.tender_sub_code,
                               a.is_high_value, a.is_cold_chain, a.is_quota_managed, a.storage_condition,
                               a.submit_by, a.submit_time, a.approve_opinion, a.initial_review_opinion,
                               a.final_review_opinion, a.return_reason, a.reject_reason
                        FROM pending_product_application a
                        LEFT JOIN supplier s ON s.supplier_id = a.supplier_id
                        WHERE a.application_no = ?
                        """,
                (rs, rowNum) -> {
                    String status = rs.getString("approval_status");
                    String submitTime = ProductApprovalMapper.formatTimestamp(rs.getTimestamp("submit_time"));
                    String applicant = "申请人" + rs.getLong("submit_by");
                    List<ConfiguredApprovalStep> approvalSteps = loadApprovalSteps();
                    List<ApprovalTimelineNode> timeline = buildTimeline(status, applicant, submitTime, approvalSteps);
                    boolean canApprove = isPendingStatus(status) && approvalFlowGuard.hasApprovalAccess(
                            CATALOG_FEATURE_CODE,
                            CATALOG_NODE_CODE,
                            currentStepOrder(status, approvalSteps),
                            null,
                            rs.getLong("submit_by")
                    );
                    return ProductApprovalMapper.mapDetail(
                            rs,
                            status,
                            applicant,
                            submitTime,
                            toStatusLabel(status, approvalSteps),
                            changeItems.load(rs),
                            timeline,
                            canApprove
                    );
                },
                applicationNo
        );
    }

    public PendingProductApplicationPage listApplications(String type, String scope, String mineStatus, String keyword) {
        return listApplications(type, scope, mineStatus, keyword, Map.of());
    }

    public PendingProductApplicationPage listApplications(String type, String scope, String mineStatus, String keyword, Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        String applicationType = TYPE_MAP.getOrDefault(type, "新品准入");
        OperatorContext operator = operatorContextProvider.current();

        StringBuilder whereClause = new StringBuilder(switch (scope) {
            case "mine" -> "WHERE a.submit_by = ?";
            case "handled" -> "WHERE (a.approval_status IN ('approved', 'rejected', 'returned') OR a.approval_status LIKE 'pending_step_%' OR a.approval_status IN ('pending_initial', 'pending_final'))";
            default -> "WHERE a.application_type = ? AND (a.approval_status LIKE 'pending_step_%' OR a.approval_status IN ('pending_initial', 'pending_final'))";
        });
        List<Object> args = new ArrayList<>();
        if ("mine".equals(scope)) {
            args.add(operator.userId());
        }
        if ("todo".equals(scope)) {
            args.add(applicationType);
        }
        if ("mine".equals(scope)) {
            switch (mineStatus) {
                case "returned" -> whereClause.append(" AND a.approval_status = 'returned'");
                case "approved" -> whereClause.append(" AND a.approval_status = 'approved'");
                default -> whereClause.append(" AND (a.approval_status LIKE 'pending_step_%' OR a.approval_status IN ('pending_initial', 'pending_final'))");
            }
        }
        if (!isBlank(keyword)) {
            whereClause.append(" AND (a.application_no LIKE ? OR a.product_name LIKE ? OR a.manufacturer_name LIKE ? OR COALESCE(s.supplier_name, a.supplier_name, '') LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }

        Long total = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM pending_product_application a
                        LEFT JOIN supplier s ON s.supplier_id = a.supplier_id
                        %s
                        """.formatted(whereClause),
                Long.class,
                args.toArray()
        );

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<PendingProductApplicationRow> rows = jdbcTemplate.query("""
                        SELECT a.application_no, a.application_type, a.product_name, a.product_code,
                               a.registration_no, a.registration_expire_date,
                               a.production_license_no, a.business_license_no,
                               a.manufacturer_name,
                               COALESCE(s.supplier_name, a.supplier_name, '') AS supplier_name,
                               a.contract_code, a.first_category, a.second_category, a.third_category,
                               a.is_volume_based, a.is_centralized_procurement, a.is_domestic,
                               a.is_chargeable, a.purchase_price, a.purchase_unit, a.udi_code,
                               a.is_quota_managed,
                               a.approval_status, a.submit_by, a.submit_time
                        FROM pending_product_application a
                        LEFT JOIN supplier s ON s.supplier_id = a.supplier_id
                        %s
                        ORDER BY a.submit_time DESC, a.application_id DESC
                        LIMIT ? OFFSET ?
                        """.formatted(whereClause),
                (rs, rowNum) -> ProductApprovalMapper.mapRow(rs),
                queryArgs.toArray()
        );
        rows = enrichWithChangeSummaries(rows);

        Map<String, Integer> counts = jdbcTemplate.query("""
                        SELECT application_type, COUNT(*) AS total
                        FROM pending_product_application
                        WHERE approval_status LIKE 'pending_step_%' OR approval_status IN ('pending_initial', 'pending_final')
                        GROUP BY application_type
                        """,
                rs -> {
                    Map<String, Integer> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("application_type"), rs.getInt("total"));
                    }
                    return result;
                }
        );

        Map<String, Integer> mineCounts = jdbcTemplate.query("""
                        SELECT
                          SUM(CASE WHEN approval_status LIKE 'pending_step_%' OR approval_status IN ('pending_initial', 'pending_final') THEN 1 ELSE 0 END) AS pending_count,
                          SUM(CASE WHEN approval_status = 'returned' THEN 1 ELSE 0 END) AS returned_count,
                          SUM(CASE WHEN approval_status = 'approved' THEN 1 ELSE 0 END) AS approved_count
                        FROM pending_product_application
                        WHERE submit_by = ?
                        """,
                rs -> {
                    Map<String, Integer> result = new HashMap<>();
                    if (rs.next()) {
                        result.put("mine_pending", rs.getInt("pending_count"));
                        result.put("mine_returned", rs.getInt("returned_count"));
                        result.put("mine_approved", rs.getInt("approved_count"));
                    }
                    return result;
                },
                operator.userId());
        if (mineCounts == null) {
            mineCounts = Map.of();
        }

        Integer handledCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM pending_product_application
                        WHERE approval_status IN ('approved', 'rejected', 'returned', 'pending_final')
                        """,
                Integer.class
        );

        return new PendingProductApplicationPage(
                List.of(
                        new PendingProductTypeCount("new", "新品准入", counts.getOrDefault("新品准入", 0)),
                        new PendingProductTypeCount("change", "信息变更", counts.getOrDefault("信息变更", 0)),
                        new PendingProductTypeCount("qualification", "资质更新", counts.getOrDefault("资质更新", 0)),
                        new PendingProductTypeCount("price", "价格调整", counts.getOrDefault("价格调整", 0)),
                        new PendingProductTypeCount("disable", "停用申请", counts.getOrDefault("停用申请", 0)),
                        new PendingProductTypeCount("mine-pending", "待审批目录", mineCounts.getOrDefault("mine_pending", 0)),
                        new PendingProductTypeCount("mine-returned", "退回修改目录", mineCounts.getOrDefault("mine_returned", 0)),
                        new PendingProductTypeCount("mine-approved", "审批通过目录", mineCounts.getOrDefault("mine_approved", 0)),
                        new PendingProductTypeCount("handled", "已处理/抄送", handledCount == null ? 0 : handledCount)
                ),
                rows,
                total == null ? 0 : total,
                pageReq.page(),
                pageReq.size()
        );
    }

    @Transactional
    public Map<String, Object> createApplication(PendingProductApplicationRequest request) {
        validateRequest(request);
        validateQuotaEligibility(request.highValue(), request.coldChain(), request.quotaManaged());
        String applicationType = normalizeApplicationType(request.applicationType());
        validateNoExistingCatalogMatch(request, null);
        validateInformationChangeHasDifference(request, applicationType);
        OperatorContext operator = operatorContextProvider.current();
        String applicationNo = nextApplicationNo();
        Long manufacturerId = findIdByName("manufacturer", "manufacturer_id", "manufacturer_name", request.manufacturerName());
        Long supplierId = findIdByName("supplier", "supplier_id", "supplier_name", request.supplierName());
        Long categoryId = ensureCategory(request.firstCategory(), request.secondCategory(), request.thirdCategory());

        jdbcTemplate.update("""
                INSERT INTO pending_product_application (
                  application_no, application_type, supplier_id, product_name, product_code, spec_model,
                  supplier_name, brand, manufacturer_id, manufacturer_name, category_id, unit, purchase_price, retail_price,
                  min_purchase_qty, purchase_unit, conversion_rate, udi_code, registration_no,
                  registration_expire_date, production_license_no, business_license_no,
                  is_volume_based, is_centralized_procurement, is_domestic, contract_code,
                  first_category, second_category, third_category, is_chargeable, tender_sub_code,
                  qualification_attachment_count, is_high_value, is_cold_chain, is_quota_managed,
                  storage_condition, product_snapshot, change_diff, approval_status, submit_by, submit_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  JSON_OBJECT('source','manual','changeReason', ?), JSON_OBJECT('changeReason', ?),
                  ?, ?, NOW())
                """,
                applicationNo,
                applicationType,
                supplierId,
                request.productName().trim(),
                request.productCode().trim(),
                request.specModel().trim(),
                nullIfBlank(request.supplierName()),
                nullIfBlank(request.brand()),
                manufacturerId,
                nullIfBlank(request.manufacturerName()),
                categoryId,
                request.unit().trim(),
                defaultDecimal(request.purchasePrice(), BigDecimal.ZERO),
                request.retailPrice(),
                defaultDecimal(request.minPurchaseQty(), BigDecimal.ONE),
                nullIfBlank(request.purchaseUnit()),
                defaultDecimal(request.conversionRate(), BigDecimal.ONE),
                nullIfBlank(request.udiCode()),
                nullIfBlank(request.registrationNo()),
                parseDate(request.registrationExpireDate()),
                nullIfBlank(request.productionLicenseNo()),
                nullIfBlank(request.businessLicenseNo()),
                Boolean.TRUE.equals(request.volumeBased()) ? 1 : 0,
                Boolean.TRUE.equals(request.centralizedProcurement()) ? 1 : 0,
                Boolean.FALSE.equals(request.domestic()) ? 0 : 1,
                nullIfBlank(request.contractCode()),
                nullIfBlank(request.firstCategory()),
                nullIfBlank(request.secondCategory()),
                nullIfBlank(request.thirdCategory()),
                Boolean.FALSE.equals(request.chargeable()) ? 0 : 1,
                nullIfBlank(request.tenderSubCode()),
                request.qualificationAttachmentCount() == null ? 0 : request.qualificationAttachmentCount(),
                Boolean.TRUE.equals(request.highValue()) ? 1 : 0,
                Boolean.TRUE.equals(request.coldChain()) ? 1 : 0,
                Boolean.TRUE.equals(request.quotaManaged()) ? 1 : 0,
                nullIfBlank(request.storageCondition()),
                nullIfBlank(request.changeReason()),
                nullIfBlank(request.changeReason()),
                firstPendingStatus(),
                operator.userId()
        );

        return Map.of("applicationNo", applicationNo);
    }

    @Transactional
    public Map<String, Object> batchProcessAction(List<String> applicationNos, PendingProductApprovalActionRequest request) {
        int successCount = 0;
        int failCount = 0;
        for (String applicationNo : applicationNos) {
            try {
                processAction(applicationNo, request);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("Batch action failed for {}: {}", applicationNo, e.getMessage());
            }
        }
        return Map.of("successCount", successCount, "failCount", failCount);
    }

    @Transactional
    public Map<String, Object> processAction(String applicationNo, PendingProductApprovalActionRequest request) {
        PendingProductApplicationDetail detail = getDetail(applicationNo);
        OperatorContext operator = operatorContextProvider.current();
        String action = request.action() == null ? "" : request.action().trim();
        String opinion = nullIfBlank(request.opinion());
        String nextStatus;
        String operationType;
        if (!isPendingStatus(detail.approvalStatus())) {
            throw new IllegalArgumentException("当前审批单已结束，不能继续审批");
        }
        List<ConfiguredApprovalStep> approvalSteps = loadApprovalSteps();
        int currentStepOrder = currentStepOrder(detail.approvalStatus(), approvalSteps);
        approvalFlowGuard.requireApprovalAccess(
                CATALOG_FEATURE_CODE,
                CATALOG_NODE_CODE,
                currentStepOrder,
                null,
                findApplicationSubmitBy(applicationNo)
        );

        if ("reject".equals(action)) {
            if (isBlank(opinion)) {
                throw new IllegalArgumentException("驳回必须填写审批意见");
            }
            nextStatus = "rejected";
            operationType = "reject";
            jdbcTemplate.update("""
                    UPDATE pending_product_application
                       SET approval_status = ?, approve_by = ?, approve_time = NOW(), reject_reason = ?,
                           approve_opinion = ?
                      WHERE application_no = ?
                    """, nextStatus, operator.userId(), opinion, opinion, applicationNo);
        } else if ("return".equals(action)) {
            if (isBlank(opinion)) {
                throw new IllegalArgumentException("退回修改必须填写退回原因");
            }
            nextStatus = "returned";
            operationType = "return";
            jdbcTemplate.update("""
                    UPDATE pending_product_application
                       SET approval_status = ?, approve_by = ?, approve_time = NOW(), return_reason = ?,
                           approve_opinion = ?
                      WHERE application_no = ?
                    """, nextStatus, operator.userId(), opinion, opinion, applicationNo);
        } else if ("approve".equals(action)) {
            if (!isFinalApprovalStep(currentStepOrder, approvalSteps)) {
                int nextStepOrder = nextStepOrder(currentStepOrder, approvalSteps);
                nextStatus = nextPendingStatus(detail.approvalStatus(), nextStepOrder, approvalSteps);
                operationType = "step_" + currentStepOrder + "_approve";
                jdbcTemplate.update("""
                        UPDATE pending_product_application
                           SET approval_status = ?, initial_review_by = ?, initial_review_time = NOW(),
                               initial_review_opinion = ?
                          WHERE application_no = ?
                        """, nextStatus, operator.userId(), opinion, applicationNo);
            } else {
                nextStatus = "approved";
                operationType = "step_" + currentStepOrder + "_approve";
                jdbcTemplate.update("""
                        UPDATE pending_product_application
                           SET approval_status = ?, final_review_by = ?, final_review_time = NOW(),
                               final_review_opinion = ?, approve_by = ?, approve_time = NOW(), approve_opinion = ?
                          WHERE application_no = ?
                        """, nextStatus, operator.userId(), opinion, operator.userId(), opinion, applicationNo);
                if ("停用申请".equals(detail.applicationType())) {
                    disableHospitalCatalogProduct(applicationNo);
                } else {
                    syncToHospitalCatalog(applicationNo);
                }
            }
        } else {
            throw new IllegalArgumentException("审批动作不正确");
        }

        writeAudit(operationType, applicationNo, opinion);
        return Map.of("applicationNo", applicationNo, "status", nextStatus);
    }

    @Transactional
    public Map<String, Object> resubmitApplication(String applicationNo, PendingProductApplicationRequest request) {
        PendingProductApplicationDetail detail = getDetail(applicationNo);
        if (!"returned".equals(detail.approvalStatus())) {
            throw new IllegalArgumentException("只有退回修改状态的审批单可以重新提交");
        }
        validateRequest(request);
        validateQuotaEligibility(request.highValue(), request.coldChain(), request.quotaManaged());
        String applicationType = normalizeApplicationType(request.applicationType());
        validateNoExistingCatalogMatch(request, applicationNo);
        validateInformationChangeHasDifference(request, applicationType);

        Long manufacturerId = findIdByName("manufacturer", "manufacturer_id", "manufacturer_name", request.manufacturerName());
        Long supplierId = findIdByName("supplier", "supplier_id", "supplier_name", request.supplierName());
        Long categoryId = ensureCategory(request.firstCategory(), request.secondCategory(), request.thirdCategory());

        jdbcTemplate.update("""
                UPDATE pending_product_application
                   SET application_type = ?, supplier_id = ?, supplier_name = ?, product_name = ?, product_code = ?,
                       spec_model = ?, brand = ?, manufacturer_id = ?, manufacturer_name = ?,
                       category_id = ?, unit = ?, purchase_price = ?, retail_price = ?,
                       min_purchase_qty = ?, purchase_unit = ?, conversion_rate = ?,
                       udi_code = ?, registration_no = ?, registration_expire_date = ?,
                       production_license_no = ?, business_license_no = ?,
                       is_volume_based = ?, is_centralized_procurement = ?, is_domestic = ?,
                       contract_code = ?, first_category = ?, second_category = ?, third_category = ?,
                       is_chargeable = ?, tender_sub_code = ?, qualification_attachment_count = ?,
                       is_high_value = ?, is_cold_chain = ?, is_quota_managed = ?, storage_condition = ?,
                       product_snapshot = JSON_OBJECT('source','resubmit','changeReason', ?),
                       change_diff = JSON_OBJECT('changeReason', ?),
                       approval_status = ?, submit_time = NOW(),
                       approve_by = NULL, approve_time = NULL, approve_opinion = NULL,
                       initial_review_by = NULL, initial_review_time = NULL, initial_review_opinion = NULL,
                       final_review_by = NULL, final_review_time = NULL, final_review_opinion = NULL,
                       return_reason = NULL, reject_reason = NULL
                 WHERE application_no = ?
                """,
                applicationType,
                supplierId,
                nullIfBlank(request.supplierName()),
                request.productName().trim(),
                request.productCode().trim(),
                request.specModel().trim(),
                nullIfBlank(request.brand()),
                manufacturerId,
                nullIfBlank(request.manufacturerName()),
                categoryId,
                request.unit().trim(),
                defaultDecimal(request.purchasePrice(), BigDecimal.ZERO),
                request.retailPrice(),
                defaultDecimal(request.minPurchaseQty(), BigDecimal.ONE),
                nullIfBlank(request.purchaseUnit()),
                defaultDecimal(request.conversionRate(), BigDecimal.ONE),
                nullIfBlank(request.udiCode()),
                nullIfBlank(request.registrationNo()),
                parseDate(request.registrationExpireDate()),
                nullIfBlank(request.productionLicenseNo()),
                nullIfBlank(request.businessLicenseNo()),
                Boolean.TRUE.equals(request.volumeBased()) ? 1 : 0,
                Boolean.TRUE.equals(request.centralizedProcurement()) ? 1 : 0,
                Boolean.FALSE.equals(request.domestic()) ? 0 : 1,
                nullIfBlank(request.contractCode()),
                nullIfBlank(request.firstCategory()),
                nullIfBlank(request.secondCategory()),
                nullIfBlank(request.thirdCategory()),
                Boolean.FALSE.equals(request.chargeable()) ? 0 : 1,
                nullIfBlank(request.tenderSubCode()),
                request.qualificationAttachmentCount() == null ? 0 : request.qualificationAttachmentCount(),
                Boolean.TRUE.equals(request.highValue()) ? 1 : 0,
                Boolean.TRUE.equals(request.coldChain()) ? 1 : 0,
                Boolean.TRUE.equals(request.quotaManaged()) ? 1 : 0,
                nullIfBlank(request.storageCondition()),
                nullIfBlank(request.changeReason()),
                nullIfBlank(request.changeReason()),
                firstPendingStatus(),
                applicationNo
        );

        writeAudit("resubmit", applicationNo, nullIfBlank(request.changeReason()));
        return Map.of("applicationNo", applicationNo, "status", "pending_initial");
    }

    /**
     * Updates product-data fields on a pending application without changing its approval status.
     * Intended for reviewers to correct data during the approval workflow.
     * Only allowed for pending_initial/pending_final statuses.
     */
    @Transactional
    public Map<String, Object> updateApplicationData(String applicationNo, PendingProductApplicationRequest request) {
        PendingProductApplicationDetail detail = getDetail(applicationNo);
        String status = detail.approvalStatus();
        if (!"pending_initial".equals(status) && !"pending_final".equals(status)) {
            throw new IllegalArgumentException("只有待审批状态的申请可以修改");
        }
        validateRequest(request);
        validateQuotaEligibility(request.highValue(), request.coldChain(), request.quotaManaged());
        validateNoExistingCatalogMatch(request, applicationNo);

        Long manufacturerId = findIdByName("manufacturer", "manufacturer_id", "manufacturer_name", request.manufacturerName());
        Long supplierId = findIdByName("supplier", "supplier_id", "supplier_name", request.supplierName());
        Long categoryId = ensureCategory(request.firstCategory(), request.secondCategory(), request.thirdCategory());

        jdbcTemplate.update("""
                UPDATE pending_product_application
                   SET supplier_id = ?, supplier_name = ?, product_name = ?, product_code = ?,
                       spec_model = ?, brand = ?, manufacturer_id = ?, manufacturer_name = ?,
                       category_id = ?, unit = ?, purchase_price = ?, retail_price = ?,
                       min_purchase_qty = ?, purchase_unit = ?, conversion_rate = ?,
                       udi_code = ?, registration_no = ?, registration_expire_date = ?,
                       production_license_no = ?, business_license_no = ?,
                       is_volume_based = ?, is_centralized_procurement = ?, is_domestic = ?,
                       contract_code = ?, first_category = ?, second_category = ?, third_category = ?,
                       is_chargeable = ?, tender_sub_code = ?, qualification_attachment_count = ?,
                       is_high_value = ?, is_cold_chain = ?, is_quota_managed = ?, storage_condition = ?
                 WHERE application_no = ?
                """,
                supplierId,
                nullIfBlank(request.supplierName()),
                request.productName().trim(),
                request.productCode().trim(),
                request.specModel().trim(),
                nullIfBlank(request.brand()),
                manufacturerId,
                nullIfBlank(request.manufacturerName()),
                categoryId,
                request.unit().trim(),
                defaultDecimal(request.purchasePrice(), BigDecimal.ZERO),
                request.retailPrice(),
                defaultDecimal(request.minPurchaseQty(), BigDecimal.ONE),
                nullIfBlank(request.purchaseUnit()),
                defaultDecimal(request.conversionRate(), BigDecimal.ONE),
                nullIfBlank(request.udiCode()),
                nullIfBlank(request.registrationNo()),
                parseDate(request.registrationExpireDate()),
                nullIfBlank(request.productionLicenseNo()),
                nullIfBlank(request.businessLicenseNo()),
                Boolean.TRUE.equals(request.volumeBased()) ? 1 : 0,
                Boolean.TRUE.equals(request.centralizedProcurement()) ? 1 : 0,
                Boolean.FALSE.equals(request.domestic()) ? 0 : 1,
                nullIfBlank(request.contractCode()),
                nullIfBlank(request.firstCategory()),
                nullIfBlank(request.secondCategory()),
                nullIfBlank(request.thirdCategory()),
                Boolean.FALSE.equals(request.chargeable()) ? 0 : 1,
                nullIfBlank(request.tenderSubCode()),
                request.qualificationAttachmentCount() == null ? 0 : request.qualificationAttachmentCount(),
                Boolean.TRUE.equals(request.highValue()) ? 1 : 0,
                Boolean.TRUE.equals(request.coldChain()) ? 1 : 0,
                Boolean.TRUE.equals(request.quotaManaged()) ? 1 : 0,
                nullIfBlank(request.storageCondition()),
                applicationNo
        );

        writeAudit("reviewer_update", applicationNo, "Reviewer updated application fields");
        return Map.of("applicationNo", applicationNo);
    }

    // ======================== Private helpers ========================

    private Long findApplicationSubmitBy(String applicationNo) {
        return jdbcTemplate.queryForObject(
                "SELECT submit_by FROM pending_product_application WHERE application_no = ?",
                Long.class,
                applicationNo
        );
    }

    /** Batch-computes change summaries by comparing current product data with pending-application values. */
    private List<PendingProductApplicationRow> enrichWithChangeSummaries(List<PendingProductApplicationRow> rows) {
        List<String> applicationNos = rows.stream()
                .filter(r -> ("信息变更".equals(r.type()) || "资质更新".equals(r.type())) && !isBlank(r.productCode()))
                .map(PendingProductApplicationRow::no)
                .distinct()
                .toList();
        if (applicationNos.isEmpty()) {
            return rows;
        }

        String placeholders = String.join(",", Collections.nCopies(applicationNos.size(), "?"));

        // 1) Fetch pending-application field values we need for comparison
        List<Map<String, Object>> pendingVals = jdbcTemplate.queryForList("""
                SELECT a.application_no, a.product_code,
                       a.registration_no, a.registration_expire_date,
                       a.production_license_no, a.business_license_no,
                       a.product_name, a.spec_model, a.brand,
                       a.manufacturer_name,
                       COALESCE(s.supplier_name, a.supplier_name, '') AS supplier_name,
                       a.unit, a.purchase_price,
                       a.is_volume_based, a.is_centralized_procurement, a.is_domestic,
                       a.contract_code, a.first_category, a.second_category, a.third_category,
                       a.is_chargeable, a.tender_sub_code,
                       a.is_high_value, a.is_cold_chain, a.is_quota_managed, a.storage_condition
                FROM pending_product_application a
                LEFT JOIN supplier s ON s.supplier_id = a.supplier_id
                WHERE a.application_no IN (%s)
                """.formatted(placeholders), applicationNos.toArray());
        Map<String, Map<String, Object>> pendingMap = new HashMap<>();
        for (Map<String, Object> p : pendingVals) {
            pendingMap.put((String) p.get("application_no"), p);
        }

        // 2) Batch-fetch current product data for the same codes
        List<String> codes = pendingVals.stream()
                .map(m -> (String) m.get("product_code"))
                .filter(c -> !isBlank(c))
                .distinct()
                .toList();
        Map<String, Map<String, Object>> productMap = new HashMap<>();
        if (!codes.isEmpty()) {
            String productPlaceholders = String.join(",", Collections.nCopies(codes.size(), "?"));
            List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                    SELECT p.product_code,
                           p.registration_no, p.registration_expire_date,
                           p.production_license_no, p.business_license_no,
                           p.product_name, p.spec_model, p.brand,
                           COALESCE(m.manufacturer_name, '') AS manufacturer_name,
                           COALESCE(s.supplier_name, '') AS supplier_name,
                           p.unit, p.purchase_price, p.is_volume_based,
                           p.is_centralized_procurement, p.is_domestic, p.contract_code,
                           p.first_category, p.second_category, p.third_category,
                           p.is_chargeable, p.tender_sub_code, p.is_high_value,
                           p.is_cold_chain, p.is_quota_managed, p.storage_condition
                    FROM product p
                    LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                    LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                    WHERE p.product_code IN (%s) AND p.deleted = 0
                    """.formatted(productPlaceholders), codes.toArray());
            for (Map<String, Object> p : products) {
                productMap.put((String) p.get("product_code"), p);
            }
        }

        List<PendingProductApplicationRow> enriched = new ArrayList<>();
        for (PendingProductApplicationRow row : rows) {
            Map<String, Object> pending = pendingMap.get(row.no());
            String summary = "";
            if (pending != null) {
                Map<String, Object> current = productMap.get(pending.get("product_code"));
                summary = computeChangeSummary(row.type(), pending, current);
            }
            enriched.add(new PendingProductApplicationRow(
                    row.no(), row.type(), row.product(), row.supplier(),
                    row.applicant(), row.time(), row.status(), row.statusTone(), row.warning(),
                    row.productCode(), summary,
                    row.manufacturerName(), row.registrationNo(), row.contractCode(),
                    row.firstCategory(), row.secondCategory(), row.thirdCategory(),
                    row.volumeBased(), row.centralizedProcurement(), row.domestic(),
                    row.chargeable(), row.purchasePrice(), row.purchaseUnit(), row.udiCode(),
                    row.quotaManaged()
            ));
        }
        return enriched;
    }

    private List<ConfiguredApprovalStep> loadApprovalSteps() {
        List<Map<String, Object>> flowRows = jdbcTemplate.queryForList("""
                SELECT af.flow_id AS flowId, COUNT(s.step_id) AS stepCount
                  FROM approval_flow af
                  LEFT JOIN approval_flow_step s ON s.flow_id = af.flow_id AND s.status = 1
                 WHERE af.feature_code = ? AND af.status = 1 AND af.deleted = 0
                 GROUP BY af.flow_id, af.node_code
                 ORDER BY stepCount DESC, CASE WHEN af.node_code = ? THEN 0 ELSE 1 END, af.flow_id
                 LIMIT 1
                """, CATALOG_FEATURE_CODE, CATALOG_NODE_CODE);
        if (flowRows.isEmpty()) {
            return legacyApprovalSteps();
        }
        Long flowId = number(flowRows.get(0).get("flowId"));
        if (flowId == null) {
            return legacyApprovalSteps();
        }
        List<ConfiguredApprovalStep> steps = jdbcTemplate.queryForList("""
                SELECT step_order AS stepOrder, step_name AS stepName
                  FROM approval_flow_step
                 WHERE flow_id = ? AND status = 1
                 ORDER BY step_order, step_id
                """, flowId).stream()
                .map(row -> new ConfiguredApprovalStep(integer(row.get("stepOrder")), text(row.get("stepName"))))
                .filter(step -> step.stepOrder() != null && step.stepOrder() > 0)
                .toList();
        return steps.isEmpty() ? legacyApprovalSteps() : steps;
    }

    private List<ApprovalTimelineNode> buildTimeline(String status,
                                                     String applicant,
                                                     String submitTime,
                                                     List<ConfiguredApprovalStep> approvalSteps) {
        List<ApprovalTimelineNode> timeline = new ArrayList<>();
        timeline.add(new ApprovalTimelineNode("提交申请", applicant, submitTime, "done"));
        if ("rejected".equals(status) || "returned".equals(status)) {
            timeline.add(new ApprovalTimelineNode("审批结束", toStatusLabel(status, approvalSteps), "-", "reject"));
            return timeline;
        }
        int currentOrder = currentStepOrder(status, approvalSteps);
        for (ConfiguredApprovalStep step : approvalSteps) {
            if ("approved".equals(status) || step.stepOrder() < currentOrder) {
                timeline.add(new ApprovalTimelineNode(step.stepName(), "通过", "-", "done"));
            } else if (step.stepOrder() == currentOrder) {
                timeline.add(new ApprovalTimelineNode(step.stepName(), "待审批", "-", "active"));
            } else {
                timeline.add(new ApprovalTimelineNode(step.stepName(), "待处理", "-", "wait"));
            }
        }
        timeline.add(new ApprovalTimelineNode("审批通过", "approved".equals(status) ? "已完成" : "已处理", "-", "approved".equals(status) ? "done" : "wait"));
        return timeline;
    }

    private String firstPendingStatus() {
        return pendingStatus(loadApprovalSteps().get(0).stepOrder());
    }

    private void validateInformationChangeHasDifference(PendingProductApplicationRequest request, String applicationType) {
        if (!"信息变更".equals(applicationType)) {
            return;
        }
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT p.product_name, p.spec_model, p.brand,
                       COALESCE(m.manufacturer_name, '') AS manufacturer_name,
                       COALESCE(s.supplier_name, '') AS supplier_name,
                       p.unit, p.purchase_price, p.retail_price, p.min_purchase_qty,
                       p.purchase_unit, p.conversion_rate, p.udi_code, p.registration_no,
                       p.registration_expire_date, p.production_license_no, p.business_license_no,
                       p.is_volume_based, p.is_centralized_procurement, p.is_domestic,
                       p.contract_code, p.first_category, p.second_category, p.third_category,
                       p.is_chargeable, p.tender_sub_code, p.is_high_value, p.is_cold_chain,
                       p.is_quota_managed, p.storage_condition
                  FROM product p
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                 WHERE p.product_code = ? AND p.deleted = 0
                 ORDER BY p.product_id DESC
                 LIMIT 1
                """, request.productCode().trim());
        if (products.isEmpty()) {
            throw new IllegalArgumentException("信息变更必须对已有医院目录商品发起");
        }

        Map<String, Object> current = products.get(0);
        List<PendingProductChangeItem> changes = new ArrayList<>();
        addChange(changes, "商品名称", current.get("product_name"), request.productName());
        addChange(changes, "规格型号", current.get("spec_model"), request.specModel());
        addChange(changes, "品牌", current.get("brand"), request.brand());
        addChange(changes, "生产厂家", current.get("manufacturer_name"), request.manufacturerName());
        addChange(changes, "供应商", current.get("supplier_name"), request.supplierName());
        addChange(changes, "单位", current.get("unit"), request.unit());
        addChange(changes, "采购价", current.get("purchase_price"), defaultDecimal(request.purchasePrice(), BigDecimal.ZERO));
        addChange(changes, "零售价", current.get("retail_price"), request.retailPrice());
        addChange(changes, "最小采购量", current.get("min_purchase_qty"), defaultDecimal(request.minPurchaseQty(), BigDecimal.ONE));
        addChange(changes, "采购单位", current.get("purchase_unit"), request.purchaseUnit());
        addChange(changes, "换算系数", current.get("conversion_rate"), defaultDecimal(request.conversionRate(), BigDecimal.ONE));
        addChange(changes, "UDI编码", current.get("udi_code"), request.udiCode());
        addChange(changes, "注册证号", current.get("registration_no"), request.registrationNo());
        addChange(changes, "注册证有效期", current.get("registration_expire_date"), request.registrationExpireDate());
        addChange(changes, "生产许可证号", current.get("production_license_no"), request.productionLicenseNo());
        addChange(changes, "经营许可证号", current.get("business_license_no"), request.businessLicenseNo());
        addChange(changes, "是否带量", boolLabel(current.get("is_volume_based")), boolLabel(Boolean.TRUE.equals(request.volumeBased()) ? 1 : 0));
        addChange(changes, "是否集采", boolLabel(current.get("is_centralized_procurement")), boolLabel(Boolean.TRUE.equals(request.centralizedProcurement()) ? 1 : 0));
        addChange(changes, "是否国产", boolLabel(current.get("is_domestic")), boolLabel(Boolean.FALSE.equals(request.domestic()) ? 0 : 1));
        addChange(changes, "合同编码", current.get("contract_code"), request.contractCode());
        addChange(changes, "一级分类", current.get("first_category"), request.firstCategory());
        addChange(changes, "二级分类", current.get("second_category"), request.secondCategory());
        addChange(changes, "三级分类", current.get("third_category"), request.thirdCategory());
        addChange(changes, "是否收费", boolLabel(current.get("is_chargeable")), boolLabel(Boolean.FALSE.equals(request.chargeable()) ? 0 : 1));
        addChange(changes, "招采子编码", current.get("tender_sub_code"), request.tenderSubCode());
        addChange(changes, "是否高值耗材", boolLabel(current.get("is_high_value")), boolLabel(Boolean.TRUE.equals(request.highValue()) ? 1 : 0));
        addChange(changes, "是否冷链", boolLabel(current.get("is_cold_chain")), boolLabel(Boolean.TRUE.equals(request.coldChain()) ? 1 : 0));
        addChange(changes, "是否定数管理", boolLabel(current.get("is_quota_managed")), boolLabel(Boolean.TRUE.equals(request.quotaManaged()) ? 1 : 0));
        addChange(changes, "储存条件", current.get("storage_condition"), request.storageCondition());

        if (changes.isEmpty()) {
            throw new IllegalArgumentException("未检测到与医院目录当前数据的差异，不需要提交信息变更审批");
        }
    }

    private void validateNoExistingCatalogMatch(PendingProductApplicationRequest request, String excludedApplicationNo) {
        List<String> fields = duplicateRuleFields();
        if (fields.isEmpty()) {
            return;
        }
        if (hospitalCatalogMatches(fields, request)) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
        if (pendingCatalogMatches(fields, request, excludedApplicationNo)) {
            throw new IllegalArgumentException("该商品目录已存在！");
        }
    }

    /**
     * Loads the duplicate-check field combination stored in sys_validation_rule.
     * Falls back to the documented rule when the rule table or row is unavailable.
     */
    private List<String> duplicateRuleFields() {
        try {
            String raw = jdbcTemplate.queryForObject("""
                    SELECT rule_fields
                      FROM sys_validation_rule
                     WHERE rule_code = 'pending_product_duplicate' AND status = 1
                     LIMIT 1
                    """, String.class);
            if (!isBlank(raw)) {
                List<String> fields = Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(field -> !field.isBlank() && productFieldExpression(field) != null)
                        .toList();
                if (!fields.isEmpty()) {
                    return fields;
                }
            }
        } catch (RuntimeException ignored) {
            // Rule storage unavailable; use the documented default below.
        }
        return List.of("product_name", "spec_model", "manufacturer_name", "supplier_name", "registration_no");
    }

    private boolean hospitalCatalogMatches(List<String> fields, PendingProductApplicationRequest request) {
        StringBuilder where = new StringBuilder(" WHERE p.deleted = 0");
        List<Object> args = new ArrayList<>();
        for (String field : fields) {
            where.append(" AND ").append(productFieldExpression(field)).append(" = ?");
            args.add(requestFieldValue(request, field));
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM product p
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                """ + where, Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private boolean pendingCatalogMatches(List<String> fields, PendingProductApplicationRequest request,
                                          String excludedApplicationNo) {
        StringBuilder where = new StringBuilder("""
                 WHERE a.approval_status IN ('pending_initial', 'pending_final', 'returned')
                """);
        List<Object> args = new ArrayList<>();
        for (String field : fields) {
            where.append(" AND COALESCE(NULLIF(")
                    .append(pendingFieldExpression(field))
                    .append(", ''), '') = ?");
            args.add(requestFieldValue(request, field));
        }
        if (!isBlank(excludedApplicationNo)) {
            where.append(" AND a.application_no <> ?");
            args.add(excludedApplicationNo.trim());
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM pending_product_application a
                  LEFT JOIN supplier s ON s.supplier_id = a.supplier_id
                """ + where, Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private static String productFieldExpression(String field) {
        return switch (field) {
            case "product_name" -> "COALESCE(TRIM(p.product_name), '')";
            case "spec_model" -> "COALESCE(TRIM(p.spec_model), '')";
            case "manufacturer_name" -> "COALESCE(TRIM(m.manufacturer_name), '')";
            case "supplier_name" -> "COALESCE(TRIM(s.supplier_name), '')";
            case "registration_no" -> "COALESCE(TRIM(p.registration_no), '')";
            default -> null;
        };
    }

    private static String pendingFieldExpression(String field) {
        return switch (field) {
            case "product_name" -> "TRIM(a.product_name)";
            case "spec_model" -> "TRIM(a.spec_model)";
            case "manufacturer_name" -> "TRIM(a.manufacturer_name)";
            case "supplier_name" -> "COALESCE(TRIM(s.supplier_name), TRIM(a.supplier_name))";
            case "registration_no" -> "TRIM(a.registration_no)";
            default -> null;
        };
    }

    private static String requestFieldValue(PendingProductApplicationRequest request, String field) {
        String value = switch (field) {
            case "product_name" -> request.productName();
            case "spec_model" -> request.specModel();
            case "manufacturer_name" -> request.manufacturerName();
            case "supplier_name" -> request.supplierName();
            case "registration_no" -> request.registrationNo();
            default -> null;
        };
        return value == null ? "" : value.trim();
    }

    private void syncToHospitalCatalog(String applicationNo) {
        jdbcTemplate.update("""
                INSERT INTO product (
                  product_code, product_name, spec_model, brand, manufacturer_id, supplier_id, category_id,
                  unit, purchase_price, retail_price, min_purchase_qty, purchase_unit, conversion_rate,
                  udi_code, registration_no, registration_expire_date, production_license_no, business_license_no,
                  is_volume_based, is_centralized_procurement, is_domestic, contract_code,
                  first_category, second_category, third_category, is_chargeable, tender_sub_code,
                  is_high_value, is_cold_chain, is_quota_managed, storage_condition, status
                )
                SELECT product_code, product_name, spec_model, brand, manufacturer_id, supplier_id,
                       COALESCE(category_id, ?), unit, COALESCE(purchase_price, 0), retail_price,
                       COALESCE(min_purchase_qty, 1), purchase_unit, COALESCE(conversion_rate, 1),
                       udi_code, registration_no, registration_expire_date, production_license_no,
                       business_license_no, is_volume_based, is_centralized_procurement, is_domestic,
                       contract_code, first_category, second_category, third_category,
                       is_chargeable, tender_sub_code, is_high_value, is_cold_chain, is_quota_managed,
                       storage_condition, 1
                  FROM pending_product_application
                 WHERE application_no = ?
                ON DUPLICATE KEY UPDATE product_name = VALUES(product_name),
                  spec_model = VALUES(spec_model), brand = VALUES(brand),
                  manufacturer_id = VALUES(manufacturer_id), supplier_id = VALUES(supplier_id),
                  unit = VALUES(unit), purchase_price = VALUES(purchase_price),
                  retail_price = VALUES(retail_price), min_purchase_qty = VALUES(min_purchase_qty),
                  purchase_unit = VALUES(purchase_unit), conversion_rate = VALUES(conversion_rate),
                  udi_code = VALUES(udi_code), registration_no = VALUES(registration_no),
                  registration_expire_date = VALUES(registration_expire_date),
                  production_license_no = VALUES(production_license_no),
                  business_license_no = VALUES(business_license_no),
                  is_volume_based = VALUES(is_volume_based),
                  is_centralized_procurement = VALUES(is_centralized_procurement),
                  is_domestic = VALUES(is_domestic), contract_code = VALUES(contract_code),
                  first_category = VALUES(first_category), second_category = VALUES(second_category),
                  third_category = VALUES(third_category), is_chargeable = VALUES(is_chargeable),
                  tender_sub_code = VALUES(tender_sub_code),
                  is_high_value = VALUES(is_high_value), is_cold_chain = VALUES(is_cold_chain),
                  is_quota_managed = VALUES(is_quota_managed),
                  storage_condition = VALUES(storage_condition), status = 1, deleted = 0
                """, ensureCategory("未分类", null, null), applicationNo);
    }

    private void disableHospitalCatalogProduct(String applicationNo) {
        jdbcTemplate.update("""
                UPDATE product p
                JOIN pending_product_application a ON a.product_code = p.product_code
                   SET p.status = 0, p.deleted = 0
                 WHERE a.application_no = ? AND p.deleted = 0
                """, applicationNo);
    }

    private void writeAudit(String operationType, String applicationNo, String opinion) {
        Long applicationId = jdbcTemplate.queryForObject(
                "SELECT application_id FROM pending_product_application WHERE application_no = ?",
                Long.class,
                applicationNo
        );
        jdbcTemplate.update("""
                INSERT INTO audit_log (operator_name, operation_type, biz_type, biz_id, after_data, ip_address, remark)
                VALUES ('admin', ?, 'pending_product_application', ?,
                        JSON_OBJECT('applicationNo', ?, 'opinion', ?), '127.0.0.1', '商品准入/变更审批')
                """, operationType, applicationId, applicationNo, opinion);
    }

    private String nextApplicationNo() {
        String prefix = "SP" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pending_product_application WHERE application_no LIKE ?",
                Integer.class,
                prefix + "%"
        );
        return prefix + String.format("%03d", (count == null ? 0 : count) + 1);
    }

    private Long ensureCategory(String firstCategory, String secondCategory, String thirdCategory) {
        String categoryName = firstNonBlank(thirdCategory, secondCategory, firstCategory, "未分类");
        List<Long> existing = jdbcTemplate.queryForList("""
                SELECT category_id
                FROM product_category
                WHERE category_name = ? AND deleted = 0
                ORDER BY level DESC, category_id DESC
                LIMIT 1
                """, Long.class, categoryName);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO product_category (parent_id, category_code, category_name, level, sort_order, status)
                    VALUES (0, ?, ?, 1, 999, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "CAT-" + System.currentTimeMillis());
            ps.setString(2, categoryName);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private Long findIdByName(String table, String idColumn, String nameColumn, String name) {
        if (isBlank(name)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT " + idColumn + " FROM " + table + " WHERE " + nameColumn + " = ? AND deleted = 0 LIMIT 1",
                Long.class,
                name.trim()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    // ======================== Static utilities ========================

    private static void validateRequest(PendingProductApplicationRequest request) {
        if (isBlank(request.productCode()) || isBlank(request.productName()) || isBlank(request.specModel()) || isBlank(request.unit())) {
            throw new IllegalArgumentException("商品编码、商品名称、规格型号、单位为必填项");
        }
    }

    private static String normalizeApplicationType(String value) {
        if (isBlank(value)) {
            return "新品准入";
        }
        return switch (value.trim()) {
            case "new" -> "新品准入";
            case "change" -> "信息变更";
            case "qualification" -> "资质更新";
            case "price" -> "价格调整";
            case "disable" -> "停用申请";
            default -> value.trim();
        };
    }

    private static void validateQuotaEligibility(Boolean highValue, Boolean coldChain, Boolean quotaManaged) {
        if (Boolean.TRUE.equals(quotaManaged) && (Boolean.TRUE.equals(highValue) || Boolean.TRUE.equals(coldChain))) {
            throw new IllegalArgumentException("高值耗材或冷链耗材不能设置为定数管理");
        }
    }

}
