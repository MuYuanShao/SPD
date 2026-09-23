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
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.service.AuditLogService;
import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.common.service.DocumentNumberService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final ProductCodeService productCodeService;
    private final DocumentNumberService documentNumberService;
    private final CatalogApprovalRouteService catalogApprovalRouteService;
    private final AuditLogService auditLogService;
    private final CatalogApprovalTransactionExecutor transactionExecutor;
    private final CatalogApplicationSnapshotService snapshotService;

    public ProductApprovalService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, OperatorContext::system, new ApprovalFlowGuard(jdbcTemplate),
                new ProductCodeService(jdbcTemplate), new DocumentNumberService(jdbcTemplate), null,
                new AuditLogService(jdbcTemplate), null,
                new CatalogApplicationSnapshotService(jdbcTemplate, new ObjectMapper()));
    }

    public ProductApprovalService(JdbcTemplate jdbcTemplate, OperatorContextProvider operatorContextProvider) {
        this(jdbcTemplate, operatorContextProvider, new ApprovalFlowGuard(jdbcTemplate, operatorContextProvider),
                new ProductCodeService(jdbcTemplate), new DocumentNumberService(jdbcTemplate), null,
                new AuditLogService(jdbcTemplate, operatorContextProvider), null,
                new CatalogApplicationSnapshotService(jdbcTemplate, new ObjectMapper()));
    }

    @Autowired
    public ProductApprovalService(JdbcTemplate jdbcTemplate,
                                  OperatorContextProvider operatorContextProvider,
                                  ApprovalFlowGuard approvalFlowGuard,
                                  ProductCodeService productCodeService,
                                  DocumentNumberService documentNumberService,
                                  CatalogApprovalRouteService catalogApprovalRouteService,
                                  AuditLogService auditLogService,
                                  CatalogApprovalTransactionExecutor transactionExecutor,
                                  CatalogApplicationSnapshotService snapshotService) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
        this.approvalFlowGuard = approvalFlowGuard;
        this.changeItems = new ProductApprovalChangeItems(jdbcTemplate);
        this.productCodeService = productCodeService;
        this.documentNumberService = documentNumberService;
        this.catalogApprovalRouteService = catalogApprovalRouteService;
        this.auditLogService = auditLogService;
        this.transactionExecutor = transactionExecutor;
        this.snapshotService = snapshotService;
    }

    // ======================== Public API ========================

    public PendingProductApplicationDetail getDetail(String applicationNo) {
        return jdbcTemplate.queryForObject("""
                        SELECT a.application_id, a.approval_round, a.application_no, a.application_type, a.approval_status,
                               a.product_name, a.product_code, a.spec_model, a.brand, a.manufacturer_name,
                               COALESCE(s.supplier_name, a.supplier_name, '') AS supplier_name,
                               a.unit, a.purchase_price, a.retail_price, a.min_purchase_qty, a.purchase_unit,
                               a.conversion_rate, a.purchase_package_qty, a.udi_code, a.registration_no, a.registration_expire_date,
                               a.production_license_no, a.business_license_no, a.qualification_attachment_count,
                               a.is_volume_based, a.is_centralized_procurement, a.is_domestic, a.contract_code,
                               a.first_category, a.second_category, a.third_category, a.is_chargeable, a.tender_sub_code,
                               a.is_high_value, a.is_cold_chain, a.is_quota_managed, a.is_key_monitored,
                               a.storage_condition,
                               a.change_diff, a.submit_by, submitter.dept_id AS submit_dept_id, a.submit_time,
                               a.approve_opinion, a.initial_review_opinion,
                               a.final_review_opinion, a.return_reason, a.reject_reason
                        FROM pending_product_application a
                        LEFT JOIN supplier s ON s.supplier_id = a.supplier_id
                        LEFT JOIN sys_user submitter ON submitter.user_id = a.submit_by
                        WHERE a.application_no = ?
                        """,
                (rs, rowNum) -> {
                    String status = rs.getString("approval_status");
                    String submitTime = ProductApprovalMapper.formatTimestamp(rs.getTimestamp("submit_time"));
                    String applicant = "申请人" + rs.getLong("submit_by");
                    List<ConfiguredApprovalStep> approvalSteps;
                    boolean canApprove;
                    if (isPendingStatus(status) && usesSnapshotRoute(
                            rs.getLong("application_id"), rs.getInt("approval_round"), status)) {
                        long applicationId = rs.getLong("application_id");
                        int approvalRound = rs.getInt("approval_round");
                        Long documentDeptId = nullableLong(rs, "submit_dept_id");
                        catalogApprovalRouteService.ensureLegacyRoute(applicationId, approvalRound,
                                rs.getString("application_type"), status, documentDeptId);
                        approvalSteps = catalogApprovalRouteService.configuredSteps(applicationId, approvalRound);
                        try {
                            catalogApprovalRouteService.requireApprovalAccess(
                                    catalogApprovalRouteService.currentStep(applicationId, approvalRound),
                                    documentDeptId, rs.getLong("submit_by"));
                            canApprove = true;
                        } catch (IllegalArgumentException exception) {
                            canApprove = false;
                        }
                    } else {
                        approvalSteps = loadApprovalSteps();
                        canApprove = isPendingStatus(status) && approvalFlowGuard.hasApprovalAccess(
                                CATALOG_FEATURE_CODE, CATALOG_NODE_CODE,
                                currentStepOrder(status, approvalSteps), nullableLong(rs, "submit_dept_id"), rs.getLong("submit_by"));
                    }
                    List<ApprovalTimelineNode> timeline = buildTimeline(status, applicant, submitTime, approvalSteps);
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
            case "handled" -> """
                    WHERE EXISTS (
                      SELECT 1
                        FROM pending_product_approval_action action
                       WHERE action.application_id = a.application_id
                         AND action.actor_id = ?
                    )""";
            default -> "WHERE a.application_type = ? AND (a.approval_status LIKE 'pending_step_%' OR a.approval_status IN ('pending_initial', 'pending_final'))";
        });
        List<Object> args = new ArrayList<>();
        if ("mine".equals(scope) || "handled".equals(scope)) {
            args.add(operator.userId());
        }
        if ("todo".equals(scope)) {
            args.add(applicationType);
            appendTodoVisibility(whereClause, args, operator);
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
                               a.is_quota_managed, a.is_key_monitored,
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
                        FROM pending_product_application a
                        WHERE EXISTS (
                          SELECT 1
                            FROM pending_product_approval_action action
                           WHERE action.application_id = a.application_id
                             AND action.actor_id = ?
                        )
                        """,
                Integer.class,
                operator.userId()
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

    /**
     * Provides manufacturer and supplier dropdown options for pending catalog forms
     * from the master data tables (enabled entries first).
     */
    public Map<String, List<Map<String, Object>>> partnerOptions() {
        List<Map<String, Object>> manufacturers = jdbcTemplate.queryForList("""
                SELECT manufacturer_code AS code, manufacturer_name AS name,
                       license_no AS licenseNo, status
                  FROM manufacturer
                 WHERE deleted = 0
                 ORDER BY status DESC, manufacturer_name, manufacturer_id
                """);
        List<Map<String, Object>> suppliers = jdbcTemplate.queryForList("""
                SELECT supplier_code AS code, supplier_name AS name,
                       business_license_no AS businessLicenseNo, status
                  FROM supplier
                 WHERE deleted = 0
                 ORDER BY status DESC, supplier_name, supplier_id
                """);
        return Map.of("manufacturers", manufacturers, "suppliers", suppliers);
    }

    public Map<String, Object> sourceProducts(String keyword, Map<String, String> params) {
        PageRequest page = PageRequest.from(params);
        String term = isBlank(keyword) ? "" : keyword.trim();
        String where = term.isEmpty() ? "" : " AND (p.product_code LIKE ? OR p.product_name LIKE ? OR p.spec_model LIKE ?)";
        Object[] filterArgs = term.isEmpty() ? new Object[0]
                : new Object[]{"%" + term + "%", "%" + term + "%", "%" + term + "%"};
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product p WHERE p.deleted = 0" + where,
                Long.class, filterArgs);
        List<Object> args = new ArrayList<>(Arrays.asList(filterArgs));
        args.add(page.size());
        args.add(page.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.product_code AS productCode, p.product_name AS productName,
                       p.spec_model AS specModel, p.unit, p.purchase_price AS purchasePrice,
                       p.status, COALESCE(m.manufacturer_name, '') AS manufacturerName,
                       COALESCE(s.supplier_name, '') AS supplierName
                  FROM product p
                  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                 WHERE p.deleted = 0
                """ + where + " ORDER BY p.product_id DESC LIMIT ? OFFSET ?", args.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, page, Map.of("source", "hospital-catalog"));
    }

    public Map<String, Object> sourceProduct(String productCode) {
        return jdbcTemplate.queryForMap("""
                SELECT p.product_code AS productCode, p.product_name AS productName, p.spec_model AS specModel,
                       COALESCE(p.brand,'') AS brand, COALESCE(m.manufacturer_name,'') AS manufacturerName,
                       COALESCE(s.supplier_name,'') AS supplierName, p.unit, p.purchase_price AS purchasePrice,
                       p.retail_price AS retailPrice, p.min_purchase_qty AS minPurchaseQty,
                       COALESCE(p.purchase_unit,'') AS purchaseUnit, p.conversion_rate AS conversionRate,
                       p.purchase_package_qty AS purchasePackageQty, COALESCE(p.udi_code,'') AS udiCode,
                       COALESCE(p.registration_no,'') AS registrationNo,
                       DATE_FORMAT(p.registration_expire_date,'%Y-%m-%d') AS registrationExpireDate,
                       COALESCE(p.production_license_no,'') AS productionLicenseNo,
                       COALESCE(p.business_license_no,'') AS businessLicenseNo,
                       p.is_volume_based AS volumeBased, p.is_centralized_procurement AS centralizedProcurement,
                       p.is_domestic AS domestic, COALESCE(p.contract_code,'') AS contractCode,
                       COALESCE(p.first_category,'') AS firstCategory, COALESCE(p.second_category,'') AS secondCategory,
                       COALESCE(p.third_category,'') AS thirdCategory, p.is_chargeable AS chargeable,
                       COALESCE(p.tender_sub_code,'') AS tenderSubCode, p.is_high_value AS highValue,
                       p.is_cold_chain AS coldChain, p.is_quota_managed AS quotaManaged,
                       p.is_key_monitored AS keyMonitored, COALESCE(p.storage_condition,'') AS storageCondition,
                       p.status
                  FROM product p LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
                  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
                 WHERE p.product_code = ? AND p.deleted = 0
                """, productCode);
    }

    @Transactional
    public Map<String, Object> createApplication(PendingProductApplicationRequest request) {
        validateRequest(request);
        validateQuotaEligibility(request.highValue(), request.coldChain(), request.quotaManaged());
        String applicationType = normalizeApplicationType(request.applicationType());
        String productCode = resolveProductCode(request, applicationType, null);
        if ("新品准入".equals(applicationType)) {
            validateNoExistingCatalogMatch(request, null);
        } else {
            validateExistingApplicationSource(productCode, applicationType, request.purchasePrice(), null);
        }
        validateInformationChangeHasDifference(request, applicationType);
        OperatorContext operator = operatorContextProvider.current();
        String applicationNo = nextApplicationNo();
        Long manufacturerId = findIdByName("manufacturer", "manufacturer_id", "manufacturer_name", request.manufacturerName());
        Long supplierId = findIdByName("supplier", "supplier_id", "supplier_name", request.supplierName());
        if ("新品准入".equals(applicationType)) {
            new com.hospital.spd.licenses.LicenseEligibilityService(jdbcTemplate)
                    .requireEligible(productCode, supplierId, manufacturerId, request.contractCode(), request.registrationExpireDate());
        }
        Long categoryId = ensureCategory(request.firstCategory(), request.secondCategory(), request.thirdCategory());

        jdbcTemplate.update("""
                INSERT INTO pending_product_application (
                  application_no, application_type, supplier_id, product_name, product_code, spec_model,
                  supplier_name, brand, manufacturer_id, manufacturer_name, category_id, unit, purchase_price, retail_price,
                  min_purchase_qty, purchase_unit, conversion_rate, purchase_package_qty, udi_code, registration_no,
                  registration_expire_date, production_license_no, business_license_no,
                  is_volume_based, is_centralized_procurement, is_domestic, contract_code,
                  first_category, second_category, third_category, is_chargeable, tender_sub_code,
                  qualification_attachment_count, is_high_value, is_cold_chain, is_quota_managed, is_key_monitored,
                  storage_condition, product_snapshot, change_diff, approval_status, submit_by, submit_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  JSON_OBJECT('source','manual','changeReason', ?), JSON_OBJECT('changeReason', ?),
                  ?, ?, NOW())
                """,
                applicationNo,
                applicationType,
                supplierId,
                request.productName().trim(),
                productCode,
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
                request.purchasePackageQty(),
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
                0,
                Boolean.TRUE.equals(request.highValue()) ? 1 : 0,
                Boolean.TRUE.equals(request.coldChain()) ? 1 : 0,
                Boolean.TRUE.equals(request.quotaManaged()) ? 1 : 0,
                Boolean.TRUE.equals(request.keyMonitored()) ? 1 : 0,
                nullIfBlank(request.storageCondition()),
                nullIfBlank(request.changeReason()),
                nullIfBlank(request.changeReason()),
                catalogApprovalRouteService == null ? firstPendingStatus() : "routing",
                operator.userId()
        );

        Long applicationId = jdbcTemplate.queryForObject(
                "SELECT application_id FROM pending_product_application WHERE application_no = ?",
                Long.class, applicationNo);
        snapshotExistingCatalog(applicationId, applicationType, request.changeReason());
        if (catalogApprovalRouteService != null) {
            String status = catalogApprovalRouteService.snapshotRoute(
                    applicationId, 1, applicationType, operator.deptId());
            jdbcTemplate.update("UPDATE pending_product_application SET approval_status = ? WHERE application_id = ?",
                    status, applicationId);
        }
        auditLogService.record("pending_product_application", "submit", applicationId, applicationNo,
                "提交目录申请");

        return Map.of("applicationNo", applicationNo, "productCode", productCode);
    }

    public Map<String, Object> batchProcessAction(List<String> applicationNos, PendingProductApprovalActionRequest request) {
        int successCount = 0;
        int failCount = 0;
        for (String applicationNo : applicationNos) {
            try {
                if (transactionExecutor == null) {
                    processAction(applicationNo, request);
                } else {
                    transactionExecutor.execute(() -> processAction(applicationNo, request));
                }
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
        Map<String, Object> application = jdbcTemplate.queryForMap("""
                SELECT a.application_id AS applicationId, a.approval_round AS approvalRound,
                       a.application_type AS applicationType, a.approval_status AS approvalStatus,
                       a.submit_by AS submitBy, u.dept_id AS documentDeptId
                  FROM pending_product_application a
                  LEFT JOIN sys_user u ON u.user_id = a.submit_by
                 WHERE a.application_no = ? FOR UPDATE
                """, applicationNo);
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
        long applicationId = catalogApprovalRouteService == null ? 0L
                : Objects.requireNonNull(number(application.get("applicationId")), "applicationId");
        int approvalRound = catalogApprovalRouteService == null ? 1
                : Objects.requireNonNull(integer(application.get("approvalRound")), "approvalRound");
        Long documentDeptId = number(application.get("documentDeptId"));
        CatalogApprovalRouteService.RouteSnapshotStep routeStep = null;
        int currentStepOrder;
        if (usesSnapshotRoute(applicationId, approvalRound, detail.approvalStatus())) {
            catalogApprovalRouteService.ensureLegacyRoute(applicationId, approvalRound,
                    text(application.get("applicationType")), detail.approvalStatus(), documentDeptId);
            routeStep = catalogApprovalRouteService.currentStep(applicationId, approvalRound);
            catalogApprovalRouteService.requireApprovalAccess(routeStep, documentDeptId,
                    number(application.get("submitBy")));
            currentStepOrder = routeStep.routeOrder();
        } else {
            currentStepOrder = currentStepOrder(detail.approvalStatus(), approvalSteps);
            approvalFlowGuard.requireApprovalAccess(CATALOG_FEATURE_CODE, CATALOG_NODE_CODE,
                    currentStepOrder, documentDeptId, findApplicationSubmitBy(applicationNo));
        }

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
            if (routeStep != null) catalogApprovalRouteService.terminate(
                    applicationId, approvalRound, currentStepOrder, "rejected");
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
            if (routeStep != null) catalogApprovalRouteService.terminate(
                    applicationId, approvalRound, currentStepOrder, "returned");
        } else if ("approve".equals(action)) {
            ConfiguredApprovalStep currentStep = routeStep == null
                    ? approvalSteps.stream().filter(step -> step.stepOrder() == currentStepOrder).findFirst()
                        .orElse(new ConfiguredApprovalStep(currentStepOrder, "审批"))
                    : new ConfiguredApprovalStep(currentStepOrder, routeStep.stepName(), routeStep.minApprovals());
            int priorApprovals = countCurrentStepApprovals(applicationNo, currentStepOrder);
            boolean minApprovalsReached = priorApprovals + 1 >= currentStep.minApprovals();
            java.util.Optional<Integer> nextRouteOrder = java.util.Optional.empty();
            boolean finalApprovalStep = isFinalApprovalStep(currentStepOrder, approvalSteps);
            if (minApprovalsReached && routeStep != null) {
                nextRouteOrder = catalogApprovalRouteService.completeAndAdvance(
                        applicationId, approvalRound, currentStepOrder);
                finalApprovalStep = nextRouteOrder.isEmpty();
            }
            if (!minApprovalsReached) {
                nextStatus = detail.approvalStatus();
                operationType = "step_" + currentStepOrder + "_approve_waiting";
            } else if (!finalApprovalStep) {
                int followingStepOrder = routeStep == null
                        ? nextStepOrder(currentStepOrder, approvalSteps) : nextRouteOrder.orElseThrow();
                nextStatus = routeStep == null
                        ? nextPendingStatus(detail.approvalStatus(), followingStepOrder, approvalSteps)
                        : pendingStatus(followingStepOrder);
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
                assertCatalogSnapshotUnchanged(applicationNo);
                if ("停用申请".equals(detail.applicationType())) {
                    disableHospitalCatalogProduct(applicationNo);
                } else {
                    syncToHospitalCatalog(applicationNo);
                }
            }
        } else {
            throw new IllegalArgumentException("审批动作不正确");
        }

        recordApprovalAction(applicationNo, currentStepOrder, action, opinion,
                detail.approvalStatus(), nextStatus, operator.userId(),
                routeStep == null ? null : routeStep.flowId(), routeStep == null ? null : routeStep.sourceStepId());
        writeAudit(operationType, applicationNo, opinion);
        return Map.of("applicationNo", applicationNo, "status", nextStatus);
    }

    @Transactional
    public Map<String, Object> resubmitApplication(String applicationNo, PendingProductApplicationRequest request) {
        PendingProductApplicationDetail detail = getDetail(applicationNo);
        if (!"returned".equals(detail.approvalStatus())) {
            throw new IllegalArgumentException("只有退回修改状态的审批单可以重新提交");
        }
        OperatorContext operator = operatorContextProvider.current();
        Long originalApplicant = findApplicationSubmitBy(applicationNo);
        if (!Objects.equals(originalApplicant, operator.userId()) && !isGlobalAdmin(operator)) {
            throw new IllegalArgumentException("只有原申请人或全局管理员可以重新提交");
        }
        validateRequest(request);
        validateQuotaEligibility(request.highValue(), request.coldChain(), request.quotaManaged());
        String applicationType = normalizeApplicationType(request.applicationType());
        String productCode = resolveProductCode(request, applicationType, applicationNo);
        if ("新品准入".equals(applicationType)) {
            validateNoExistingCatalogMatch(request, applicationNo);
        } else {
            validateExistingApplicationSource(productCode, applicationType, request.purchasePrice(), applicationNo);
        }
        validateInformationChangeHasDifference(request, applicationType);

        Long manufacturerId = findIdByName("manufacturer", "manufacturer_id", "manufacturer_name", request.manufacturerName());
        Long supplierId = findIdByName("supplier", "supplier_id", "supplier_name", request.supplierName());
        if ("新品准入".equals(applicationType)) {
            new com.hospital.spd.licenses.LicenseEligibilityService(jdbcTemplate)
                    .requireEligible(productCode, supplierId, manufacturerId, request.contractCode(), request.registrationExpireDate());
        }
        Long categoryId = ensureCategory(request.firstCategory(), request.secondCategory(), request.thirdCategory());

        jdbcTemplate.update("""
                UPDATE pending_product_application
                   SET application_type = ?, supplier_id = ?, supplier_name = ?, product_name = ?, product_code = ?,
                       spec_model = ?, brand = ?, manufacturer_id = ?, manufacturer_name = ?,
                       category_id = ?, unit = ?, purchase_price = ?, retail_price = ?,
                       min_purchase_qty = ?, purchase_unit = ?, conversion_rate = ?, purchase_package_qty = ?,
                       udi_code = ?, registration_no = ?, registration_expire_date = ?,
                       production_license_no = ?, business_license_no = ?,
                       is_volume_based = ?, is_centralized_procurement = ?, is_domestic = ?,
                       contract_code = ?, first_category = ?, second_category = ?, third_category = ?,
                       is_chargeable = ?, tender_sub_code = ?, qualification_attachment_count = ?,
                       is_high_value = ?, is_cold_chain = ?, is_quota_managed = ?, is_key_monitored = ?,
                       storage_condition = ?,
                       product_snapshot = JSON_OBJECT('source','resubmit','changeReason', ?),
                       change_diff = JSON_OBJECT('changeReason', ?),
                       approval_status = ?, submit_time = NOW(),
                       approval_round = approval_round + 1,
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
                productCode,
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
                request.purchasePackageQty(),
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
                currentAttachmentCount(applicationNo),
                Boolean.TRUE.equals(request.highValue()) ? 1 : 0,
                Boolean.TRUE.equals(request.coldChain()) ? 1 : 0,
                Boolean.TRUE.equals(request.quotaManaged()) ? 1 : 0,
                Boolean.TRUE.equals(request.keyMonitored()) ? 1 : 0,
                nullIfBlank(request.storageCondition()),
                nullIfBlank(request.changeReason()),
                nullIfBlank(request.changeReason()),
                catalogApprovalRouteService == null ? firstPendingStatus() : "routing",
                applicationNo
        );

        String resubmittedStatus = "pending_initial";
        if (catalogApprovalRouteService != null) {
            Map<String, Object> identity = jdbcTemplate.queryForMap("""
                    SELECT application_id AS applicationId, approval_round AS approvalRound
                      FROM pending_product_application WHERE application_no = ?
                    """, applicationNo);
            Long applicationId = number(identity.get("applicationId"));
            int approvalRound = integer(identity.get("approvalRound"));
            snapshotExistingCatalog(applicationId, applicationType, request.changeReason());
            resubmittedStatus = catalogApprovalRouteService.snapshotRoute(
                    applicationId, approvalRound, applicationType, operator.deptId());
            jdbcTemplate.update("UPDATE pending_product_application SET approval_status = ? WHERE application_id = ?",
                    resubmittedStatus, applicationId);
        }

        writeAudit("resubmit", applicationNo, nullIfBlank(request.changeReason()));
        return Map.of("applicationNo", applicationNo, "status", resubmittedStatus);
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
        if (!isPendingStatus(status)) {
            throw new IllegalArgumentException("只有待审批状态的申请可以修改");
        }
        if (catalogApprovalRouteService != null) {
            Map<String, Object> identity = jdbcTemplate.queryForMap("""
                    SELECT a.application_id AS applicationId, a.approval_round AS approvalRound,
                           a.application_type AS applicationType, a.submit_by AS submitBy,
                           u.dept_id AS documentDeptId
                      FROM pending_product_application a LEFT JOIN sys_user u ON u.user_id = a.submit_by
                     WHERE a.application_no = ?
                    """, applicationNo);
            long applicationId = number(identity.get("applicationId"));
            int approvalRound = integer(identity.get("approvalRound"));
            Long documentDeptId = number(identity.get("documentDeptId"));
            if (usesSnapshotRoute(applicationId, approvalRound, status)) {
                catalogApprovalRouteService.ensureLegacyRoute(applicationId, approvalRound,
                        text(identity.get("applicationType")), status, documentDeptId);
                catalogApprovalRouteService.requireApprovalAccess(
                        catalogApprovalRouteService.currentStep(applicationId, approvalRound),
                        documentDeptId, number(identity.get("submitBy")));
            } else {
                approvalFlowGuard.requireApprovalAccess(CATALOG_FEATURE_CODE, CATALOG_NODE_CODE,
                        currentStepOrder(status, loadApprovalSteps()), documentDeptId,
                        number(identity.get("submitBy")));
            }
        }
        validateRequest(request);
        validateQuotaEligibility(request.highValue(), request.coldChain(), request.quotaManaged());
        String applicationType = normalizeApplicationType(request.applicationType());
        String productCode = resolveProductCode(request, applicationType, applicationNo);
        validateNoExistingCatalogMatch(request, applicationNo);

        Long manufacturerId = findIdByName("manufacturer", "manufacturer_id", "manufacturer_name", request.manufacturerName());
        Long supplierId = findIdByName("supplier", "supplier_id", "supplier_name", request.supplierName());
        Long categoryId = ensureCategory(request.firstCategory(), request.secondCategory(), request.thirdCategory());

        jdbcTemplate.update("""
                UPDATE pending_product_application
                   SET supplier_id = ?, supplier_name = ?, product_name = ?, product_code = ?,
                       spec_model = ?, brand = ?, manufacturer_id = ?, manufacturer_name = ?,
                       category_id = ?, unit = ?, purchase_price = ?, retail_price = ?,
                       min_purchase_qty = ?, purchase_unit = ?, conversion_rate = ?, purchase_package_qty = ?,
                       udi_code = ?, registration_no = ?, registration_expire_date = ?,
                       production_license_no = ?, business_license_no = ?,
                       is_volume_based = ?, is_centralized_procurement = ?, is_domestic = ?,
                       contract_code = ?, first_category = ?, second_category = ?, third_category = ?,
                       is_chargeable = ?, tender_sub_code = ?, qualification_attachment_count = ?,
                       is_high_value = ?, is_cold_chain = ?, is_quota_managed = ?, is_key_monitored = ?,
                       storage_condition = ?
                 WHERE application_no = ?
                """,
                supplierId,
                nullIfBlank(request.supplierName()),
                request.productName().trim(),
                productCode,
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
                request.purchasePackageQty(),
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
                currentAttachmentCount(applicationNo),
                Boolean.TRUE.equals(request.highValue()) ? 1 : 0,
                Boolean.TRUE.equals(request.coldChain()) ? 1 : 0,
                Boolean.TRUE.equals(request.quotaManaged()) ? 1 : 0,
                Boolean.TRUE.equals(request.keyMonitored()) ? 1 : 0,
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

    private void appendTodoVisibility(StringBuilder whereClause, List<Object> args, OperatorContext operator) {
        String currentStep = """
                CASE
                  WHEN a.approval_status = 'pending_initial' THEN 1
                  WHEN a.approval_status = 'pending_final' THEN 2
                  ELSE CAST(SUBSTRING(a.approval_status, LENGTH('pending_step_') + 1) AS UNSIGNED)
                END
                """;
        whereClause.append("""
                AND NOT EXISTS (
                  SELECT 1
                    FROM pending_product_approval_action own_action
                   WHERE own_action.application_id = a.application_id
                     AND own_action.approval_round = a.approval_round
                     AND own_action.step_order = %s
                     AND own_action.actor_id = ?
                )
                """.formatted(currentStep));
        args.add(operator.userId());
        if (isGlobalApprovalAdmin(operator)) {
            return;
        }

        List<String> roles = normalizedRoleCodes(operator);
        String rolePlaceholders = roles.isEmpty()
                ? "NULL"
                : String.join(",", Collections.nCopies(roles.size(), "?"));
        whereClause.append("""
                AND EXISTS (
                  SELECT 1
                    FROM approval_flow af
                    JOIN approval_flow_step step ON step.flow_id = af.flow_id AND step.status = 1
                    LEFT JOIN sys_role role ON role.role_id = step.role_id
                   WHERE af.feature_code = 'pending-product-catalog'
                     AND af.node_code = 'initial-review'
                     AND af.status = 1 AND af.deleted = 0
                     AND step.step_order = %s
                     AND (step.allow_self_approve = 1 OR a.submit_by <> ?)
                     AND (
                       (step.approver_type = 'user' AND step.user_id = ?)
                       OR (step.approver_type = 'role' AND LOWER(REPLACE(role.role_code, 'ROLE_', '')) IN (%s))
                       OR (step.approver_type = 'dept_manager' AND ? IS NOT NULL
                           AND (step.dept_id IS NULL OR step.dept_id = ?))
                     )
                     AND (
                       af.scope_type IS NULL OR af.scope_type = 'global'
                       OR (af.scope_type = 'role' AND LOWER(REPLACE(af.scope_id, 'ROLE_', '')) IN (%s))
                       OR (af.scope_type = 'department' AND CAST(af.scope_id AS UNSIGNED) = ?)
                     )
                )
                """.formatted(currentStep, rolePlaceholders, rolePlaceholders));
        args.add(operator.userId());
        args.add(operator.userId());
        args.addAll(roles);
        args.add(operator.deptId());
        args.add(operator.deptId());
        args.addAll(roles);
        args.add(operator.deptId());
    }

    private static boolean isGlobalApprovalAdmin(OperatorContext operator) {
        String username = operator.username() == null ? "" : operator.username().trim().toLowerCase(Locale.ROOT);
        return "system".equals(username) || "admin".equals(username)
                || normalizedRoleCodes(operator).stream().anyMatch(role -> "system".equals(role) || "admin".equals(role));
    }

    private static List<String> normalizedRoleCodes(OperatorContext operator) {
        if (operator.roles() == null) {
            return List.of();
        }
        return operator.roles().stream()
                .filter(Objects::nonNull)
                .map(role -> role.trim().toLowerCase(Locale.ROOT))
                .map(role -> role.startsWith("role_") ? role.substring(5) : role)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    private void recordApprovalAction(String applicationNo,
                                      int stepOrder,
                                      String action,
                                      String opinion,
                                      String fromStatus,
                                      String toStatus,
                                      Long actorId,
                                      Long flowId,
                                      Long stepId) {
        try {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO pending_product_approval_action (
                      application_id, approval_round, flow_id, step_id, step_order, actor_id,
                      action, opinion, from_status, to_status
                    )
                    SELECT application_id, approval_round, ?, ?, ?, ?, ?, ?, ?, ?
                      FROM pending_product_application
                     WHERE application_no = ?
                    """, flowId, stepId, stepOrder, actorId, action, opinion, fromStatus, toStatus, applicationNo);
            if (inserted != 1) {
                throw new IllegalArgumentException("审批申请不存在");
            }
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("当前审批节点已由本人处理，请勿重复提交", exception);
        }
    }

    private int countCurrentStepApprovals(String applicationNo, int stepOrder) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM pending_product_approval_action action
                  JOIN pending_product_application application
                    ON application.application_id = action.application_id
                   AND application.approval_round = action.approval_round
                 WHERE application.application_no = ?
                   AND action.step_order = ?
                   AND action.action = 'approve'
                """, Integer.class, applicationNo, stepOrder);
        return count == null ? 0 : count;
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
                       a.is_high_value, a.is_cold_chain, a.is_quota_managed, a.is_key_monitored,
                       a.storage_condition
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
                           p.is_cold_chain, p.is_quota_managed, p.is_key_monitored, p.storage_condition
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
                    row.quotaManaged(), row.keyMonitored()
            ));
        }
        return enriched;
    }

    private boolean usesSnapshotRoute(long applicationId, int approvalRound, String status) {
        return catalogApprovalRouteService != null
                && !catalogApprovalRouteService.isLegacyDynamicRoute(applicationId, approvalRound, status);
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
                SELECT step_order AS stepOrder, step_name AS stepName,
                       min_approvals AS minApprovals
                  FROM approval_flow_step
                 WHERE flow_id = ? AND status = 1
                 ORDER BY step_order, step_id
                """, flowId).stream()
                .map(row -> new ConfiguredApprovalStep(
                        integer(row.get("stepOrder")),
                        text(row.get("stepName")),
                        integer(row.get("minApprovals"))))
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
                       p.purchase_unit, p.conversion_rate, p.purchase_package_qty, p.udi_code, p.registration_no,
                       p.registration_expire_date, p.production_license_no, p.business_license_no,
                       p.is_volume_based, p.is_centralized_procurement, p.is_domestic,
                       p.contract_code, p.first_category, p.second_category, p.third_category,
                       p.is_chargeable, p.tender_sub_code, p.is_high_value, p.is_cold_chain,
                       p.is_quota_managed, p.is_key_monitored, p.storage_condition
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
        addChange(changes, "中包装数量", current.get("conversion_rate"), defaultDecimal(request.conversionRate(), BigDecimal.ONE));
        addChange(changes, "采购包装数量", current.get("purchase_package_qty"), request.purchasePackageQty());
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
        addChange(changes, "重点监控", boolLabel(current.get("is_key_monitored")), boolLabel(Boolean.TRUE.equals(request.keyMonitored()) ? 1 : 0));
        addChange(changes, "储存条件", current.get("storage_condition"), request.storageCondition());

        if (changes.isEmpty()) {
            throw new IllegalArgumentException("未检测到与医院目录当前数据的差异，不需要提交信息变更审批");
        }
    }

    private void validateExistingApplicationSource(String productCode, String applicationType, BigDecimal requestedPrice,
                                                   String excludedApplicationNo) {
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT product_id, purchase_price, status FROM product
                 WHERE product_code = ? AND deleted = 0 ORDER BY product_id DESC LIMIT 1
                """, productCode);
        if (products.isEmpty()) {
            throw new IllegalArgumentException("非新品申请必须选择已有医院目录商品");
        }
        if ("停用申请".equals(applicationType) && integer(products.get(0).get("status")) != 1) {
            throw new IllegalArgumentException("只能对启用状态的商品发起停用申请");
        }
        if ("价格调整".equals(applicationType)) {
            BigDecimal currentPrice = products.get(0).get("purchase_price") instanceof BigDecimal value
                    ? value : BigDecimal.ZERO;
            if (requestedPrice == null || currentPrice.compareTo(requestedPrice) == 0) {
                throw new IllegalArgumentException("价格调整必须修改采购价");
            }
        }
        Integer active = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pending_product_application
                 WHERE product_code = ? AND (approval_status LIKE 'pending_step_%'
                   OR approval_status IN ('pending_initial','pending_final','routing','returned'))
                   AND (? IS NULL OR application_no <> ?)
                """, Integer.class, productCode, excludedApplicationNo, excludedApplicationNo);
        if (active != null && active > 0) {
            throw new IllegalArgumentException("该商品已有进行中的目录申请，请勿重复提交");
        }
    }

    private void snapshotExistingCatalog(Long applicationId, String applicationType, String reason) {
        snapshotService.write(applicationId, applicationType, nullIfBlank(reason), null);
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
        new com.hospital.spd.licenses.LicenseEligibilityService(jdbcTemplate).requireAdmission(applicationNo);
        jdbcTemplate.update("""
                INSERT INTO product (
                  product_code, product_name, spec_model, brand, manufacturer_id, supplier_id, category_id,
                  unit, purchase_price, retail_price, min_purchase_qty, purchase_unit, conversion_rate, purchase_package_qty,
                  udi_code, registration_no, registration_expire_date, production_license_no, business_license_no,
                  is_volume_based, is_centralized_procurement, is_domestic, contract_code,
                  first_category, second_category, third_category, is_chargeable, tender_sub_code,
                  is_high_value, is_cold_chain, is_quota_managed, is_key_monitored, storage_condition, status
                )
                SELECT product_code, product_name, spec_model, brand, manufacturer_id, supplier_id,
                       COALESCE(category_id, ?), unit, COALESCE(purchase_price, 0), retail_price,
                       COALESCE(min_purchase_qty, 1), purchase_unit, COALESCE(conversion_rate, 1), purchase_package_qty,
                       udi_code, registration_no, registration_expire_date, production_license_no,
                       business_license_no, is_volume_based, is_centralized_procurement, is_domestic,
                       contract_code, first_category, second_category, third_category,
                       is_chargeable, tender_sub_code, is_high_value, is_cold_chain, is_quota_managed,
                       is_key_monitored,
                       storage_condition,
                       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(product_snapshot, '$.targetStatus')) AS UNSIGNED), 1)
                  FROM pending_product_application
                 WHERE application_no = ?
                ON DUPLICATE KEY UPDATE product_name = VALUES(product_name),
                  spec_model = VALUES(spec_model), brand = VALUES(brand),
                  manufacturer_id = VALUES(manufacturer_id), supplier_id = VALUES(supplier_id),
                  category_id = VALUES(category_id),
                  unit = VALUES(unit), purchase_price = VALUES(purchase_price),
                  retail_price = VALUES(retail_price), min_purchase_qty = VALUES(min_purchase_qty),
                  purchase_unit = VALUES(purchase_unit), conversion_rate = VALUES(conversion_rate),
                  purchase_package_qty = VALUES(purchase_package_qty),
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
                  is_key_monitored = VALUES(is_key_monitored),
                  storage_condition = VALUES(storage_condition), status = VALUES(status), deleted = 0
                """, ensureCategory("未分类", null, null), applicationNo);
        jdbcTemplate.update("""
                INSERT INTO sys_attachment (
                  biz_type, biz_id, file_name, file_ext, file_type, file_size, file_path, file_url,
                  category, description, valid_date, create_by, source_attachment_id
                )
                SELECT 'product', p.product_id, source.file_name, source.file_ext, source.file_type,
                       source.file_size, source.file_path,
                       CONCAT('/pending-product-applications/attachments/', source.attachment_id, '/file'),
                       source.category, source.description, source.valid_date, source.create_by,
                       source.attachment_id
                  FROM pending_product_application application
                  JOIN product p ON p.product_code = application.product_code
                  JOIN sys_attachment source
                    ON source.biz_type = 'pending_product_application'
                   AND source.biz_id = application.application_id AND source.deleted = 0
                  LEFT JOIN sys_attachment promoted
                    ON promoted.biz_type = 'product' AND promoted.biz_id = p.product_id
                   AND promoted.source_attachment_id = source.attachment_id AND promoted.deleted = 0
                 WHERE application.application_no = ? AND promoted.attachment_id IS NULL
                """, applicationNo);
    }

    private void disableHospitalCatalogProduct(String applicationNo) {
        jdbcTemplate.update("""
                UPDATE product p
                JOIN pending_product_application a ON a.product_code = p.product_code
                   SET p.status = 0, p.deleted = 0
                 WHERE a.application_no = ? AND p.deleted = 0
                """, applicationNo);
    }

    /** Prevents an older approval from overwriting catalog changes made after submission. */
    private void assertCatalogSnapshotUnchanged(String applicationNo) {
        Integer conflicting = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM pending_product_application a
                  JOIN product p ON p.product_code = a.product_code AND p.deleted = 0
                 WHERE a.application_no = ?
                   AND JSON_UNQUOTE(JSON_EXTRACT(a.product_snapshot, '$.source')) = 'hospital'
                   AND NOT (
                     p.product_name <=> JSON_UNQUOTE(JSON_EXTRACT(a.product_snapshot, '$.productName'))
                     AND p.spec_model <=> JSON_UNQUOTE(JSON_EXTRACT(a.product_snapshot, '$.specModel'))
                     AND p.unit <=> JSON_UNQUOTE(JSON_EXTRACT(a.product_snapshot, '$.unit'))
                     AND p.purchase_price <=> CAST(JSON_UNQUOTE(JSON_EXTRACT(a.product_snapshot, '$.purchasePrice')) AS DECIMAL(18,4))
                     AND p.category_id <=> CAST(JSON_UNQUOTE(JSON_EXTRACT(a.product_snapshot, '$.categoryId')) AS UNSIGNED)
                     AND p.status <=> CAST(JSON_UNQUOTE(JSON_EXTRACT(a.product_snapshot, '$.status')) AS UNSIGNED)
                   )
                """, Integer.class, applicationNo);
        if (conflicting != null && conflicting > 0) {
            throw new IllegalArgumentException("医院目录已发生变化，请重新提交申请");
        }
    }

    private void writeAudit(String operationType, String applicationNo, String opinion) {
        Long applicationId = jdbcTemplate.queryForObject(
                "SELECT application_id FROM pending_product_application WHERE application_no = ?",
                Long.class,
                applicationNo
        );
        auditLogService.record("pending_product_application", operationType, applicationId, applicationNo,
                isBlank(opinion) ? "商品准入/变更审批" : opinion);
    }

    private int currentAttachmentCount(String applicationNo) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_attachment attachment
                  JOIN pending_product_application application
                    ON application.application_id = attachment.biz_id
                 WHERE application.application_no = ?
                   AND attachment.biz_type = 'pending_product_application'
                   AND attachment.deleted = 0
                """, Integer.class, applicationNo);
        return count == null ? 0 : count;
    }

    private String nextApplicationNo() {
        return documentNumberService.next(DocumentKind.PENDING_PRODUCT_APPLICATION);
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
        if (isBlank(request.productName()) || isBlank(request.specModel()) || isBlank(request.unit())) {
            throw new IllegalArgumentException("商品名称、规格型号、单位为必填项");
        }
    }

    /**
     * Resolves the product code for an application. New-product applications may leave the code
     * blank: the tender sub-code is used as fallback and an SPD code is generated when both are
     * blank. Other application types reference an existing catalog product and must carry a code.
     */
    private String resolveProductCode(PendingProductApplicationRequest request, String applicationType,
                                      String excludedApplicationNo) {
        if ("新品准入".equals(applicationType)) {
            String code = productCodeService.resolveNewProductCode(request.productCode(), request.tenderSubCode());
            productCodeService.requireAvailable(code, excludedApplicationNo);
            return code;
        }
        if (isBlank(request.productCode())) {
            throw new IllegalArgumentException("商品编码为必填项");
        }
        return request.productCode().trim();
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
            case "新品准入", "信息变更", "资质更新", "价格调整", "停用申请" -> value.trim();
            default -> throw new IllegalArgumentException("不支持的申请类型");
        };
    }

    private static boolean isGlobalAdmin(OperatorContext operator) {
        if (operator == null) return false;
        if ("admin".equalsIgnoreCase(operator.username()) || "system".equalsIgnoreCase(operator.username())) return true;
        return operator.roles() != null && operator.roles().stream()
                .map(role -> role.toLowerCase(Locale.ROOT).replaceFirst("^role_", ""))
                .anyMatch(role -> role.equals("admin") || role.equals("system"));
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static void validateQuotaEligibility(Boolean highValue, Boolean coldChain, Boolean quotaManaged) {
        if (Boolean.TRUE.equals(quotaManaged) && (Boolean.TRUE.equals(highValue) || Boolean.TRUE.equals(coldChain))) {
            throw new IllegalArgumentException("高值耗材或冷链耗材不能设置为定数管理");
        }
    }

}
