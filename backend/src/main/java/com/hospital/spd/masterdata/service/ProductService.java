package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.masterdata.*;
import static com.hospital.spd.common.SqlHelper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maintains hospital product catalog records and creates approval applications for catalog changes.
 */
@Service
public class ProductService {

    private final JdbcTemplate jdbcTemplate;

    public ProductService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== 公开方法 ====================

    /** 分页查询医院商品目录 */
    public MasterDataPage hospitalProducts(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                WHERE p.deleted = 0
                """);
        appendLike(where, args, "p.product_code", params.get("productCode"));
        appendLike(where, args, "p.product_name", params.get("productName"));
        appendLike(where, args, "p.spec_model", params.get("specModel"));
        appendLike(where, args, "p.spec_model", params.get("model"));
        appendLike(where, args, "m.manufacturer_name", params.get("manufacturerName"));
        appendLike(where, args, "s.supplier_name", params.get("supplierName"));
        appendLike(where, args, "p.purchase_price", params.get("price"));
        appendBoolean(where, args, "p.is_centralized_procurement", params.get("isCentralized"));
        appendBoolean(where, args, "p.is_domestic", params.get("isDomestic"));
        appendBoolean(where, args, "p.is_quota_managed", params.get("isQuotaManaged"));
        appendLike(where, args, "p.tender_sub_code", params.get("tenderCode"));

        Long total = args.isEmpty()
            ? jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM product p
                LEFT JOIN manufacturer m ON p.manufacturer_id = m.manufacturer_id
                LEFT JOIN supplier s ON p.supplier_id = s.supplier_id
                """ + where, Long.class)
            : jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM product p
                LEFT JOIN manufacturer m ON p.manufacturer_id = m.manufacturer_id
                LEFT JOIN supplier s ON p.supplier_id = s.supplier_id
                """ + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.product_code AS code, p.product_name AS name, p.spec_model AS spec,
                       COALESCE(p.brand, '-') AS brand,
                       COALESCE(m.manufacturer_name, '-') AS manufacturer,
                       COALESCE(s.supplier_name, '-') AS supplier,
                       CASE p.is_quota_managed WHEN 1 THEN '是' ELSE '否' END AS quotaManaged,
                       p.registration_no AS registrationNo,
                       COALESCE(p.contract_code, '-') AS contractCode,
                       COALESCE(p.first_category, '-') AS firstCategory,
                       COALESCE(p.second_category, '-') AS secondCategory,
                       COALESCE(p.third_category, '-') AS thirdCategory,
                       CASE p.is_volume_based WHEN 1 THEN '是' ELSE '否' END AS volumeBased,
                       CASE p.is_centralized_procurement WHEN 1 THEN '是' ELSE '否' END AS centralized,
                       CASE p.is_domestic WHEN 1 THEN '是' ELSE '否' END AS domestic,
                       CASE p.is_chargeable WHEN 1 THEN '是' ELSE '否' END AS chargeable,
                       COALESCE(p.tender_sub_code, '-') AS tenderSubCode,
                       p.unit AS unit, p.purchase_price AS price,
                       CASE p.status WHEN 1 THEN '启用' ELSE '停用' END AS status
                FROM product p
                LEFT JOIN manufacturer m ON p.manufacturer_id = m.manufacturer_id
                LEFT JOIN supplier s ON p.supplier_id = s.supplier_id
                """ + where + " ORDER BY p.product_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());

        return new MasterDataPage(
                "医院目录",
                "审批通过后的院内正式可用商品。",
                List.of("商品编码", "商品名称", "规格型号", "厂家", "供应商", "注册证号", "合同编码",
                        "一级分类", "二级分类", "三级分类", "是否带量", "是否集采", "是否国产", "是否收费",
                        "招采子编码", "单位", "采购价", "状态"),
                rows,
                total == null ? 0 : total,
                pageReq.page(),
                pageReq.size()
        );
    }

    /** 创建医院商品（提交审批） */
    public Map<String, Object> createHospitalProduct(ProductCreateRequest request) {
        if (isBlank(request.productCode()) || isBlank(request.productName()) || isBlank(request.specModel()) ||
                isBlank(request.unit())) {
            throw new IllegalArgumentException("商品编码、商品名称、规格型号、单位为必填项");
        }
        validateQuotaEligibility(request.highValue(), request.coldChain(), request.quotaManaged());

        String applicationNo = createPendingApplication("新品准入", request, "医院目录新增提交审批");
        return Map.of("productCode", request.productCode().trim(), "applicationNo", applicationNo);
    }

    /** 更新医院商品（提交审批） */
    public Map<String, Object> updateHospitalProduct(String productCode, ProductCreateRequest request) {
        if (isBlank(productCode) || isBlank(request.productName()) || isBlank(request.specModel()) ||
                isBlank(request.unit())) {
            throw new IllegalArgumentException("商品名称、规格型号、单位为必填项");
        }
        validateQuotaEligibility(request.highValue(), request.coldChain(), request.quotaManaged());
        ProductDetail current = hospitalProductDetail(productCode.trim());

        String applicationType = request.purchasePrice() != null && differs(productCode, "purchase_price", request.purchasePrice())
                ? "价格调整"
                : "信息变更";
        if ("信息变更".equals(applicationType)) {
            assertInformationChangeHasDifference(current, request);
        }
        String applicationNo = createPendingApplication(applicationType, request, "医院目录修改提交审批");
        return Map.of("productCode", productCode.trim(), "applicationNo", applicationNo);
    }

    /** 批量更新医院商品（提交审批） */
    public Map<String, Object> batchUpdateHospitalProducts(ProductBatchUpdateRequest request) {
        if (request.productCodes() == null || request.productCodes().isEmpty()) {
            throw new IllegalArgumentException("请选择需要批量修改的商品");
        }

        if (!hasBatchChange(request)) {
            throw new IllegalArgumentException("请至少填写一个批量修改字段");
        }

        int submittedRows = 0;
        for (String productCode : request.productCodes()) {
            ProductDetail detail = hospitalProductDetail(productCode.trim());
            ProductCreateRequest merged = mergeBatchUpdate(detail, request);
            validateQuotaEligibility(merged.highValue(), merged.coldChain(), merged.quotaManaged());
            String applicationType = request.purchasePrice() == null ? "信息变更" : "价格调整";
            if ("信息变更".equals(applicationType) && !hasInformationChangeDifference(detail, merged)) {
                continue;
            }
            createPendingApplication(applicationType, merged, "医院目录批量修改提交审批");
            submittedRows++;
        }

        if (submittedRows == 0) {
            throw new IllegalArgumentException("未检测到与医院目录当前数据的差异，不需要提交信息变更审批");
        }
        return Map.of("updatedRows", submittedRows);
    }

    /** 更新医院商品状态（提交审批） */
    public Map<String, Object> updateHospitalProductStatus(ProductStatusUpdateRequest request) {
        if (request.productCodes() == null || request.productCodes().isEmpty()) {
            throw new IllegalArgumentException("请选择需要停用的商品");
        }

        int submittedRows = 0;
        for (String productCode : request.productCodes()) {
            ProductDetail detail = hospitalProductDetail(productCode.trim());
            createPendingApplication(request.status() != null && request.status() == 1 ? "信息变更" : "停用申请",
                    toRequest(detail), "医院目录启停提交审批");
            submittedRows++;
        }

        return Map.of("updatedRows", submittedRows);
    }
    public Map<String, List<Map<String, Object>>> partnerOptions() {
        List<Map<String, Object>> manufacturers = jdbcTemplate.queryForList("""
                SELECT manufacturer_code AS code, manufacturer_name AS name,
                       license_no AS licenseNo, status
                  FROM manufacturer
                 WHERE deleted = 0
                 ORDER BY status DESC, manufacturer_name, manufacturer_id
                """);
        List<Map<String, Object>> suppliers = jdbcTemplate.queryForList("""
                SELECT supplier_code AS code, supplier_name AS name, status
                  FROM supplier
                 WHERE deleted = 0
                 ORDER BY status DESC, supplier_name, supplier_id
                """);
        return Map.of("manufacturers", manufacturers, "suppliers", suppliers);
    }

    /** 提交医院商品（提交审批） */
    public Map<String, Object> submitHospitalProducts(ProductSubmitRequest request) {
        if (request.productCodes() == null || request.productCodes().isEmpty()) {
            throw new IllegalArgumentException("请选择需要提交的商品");
        }

        int submittedRows = 0;
        for (String productCode : request.productCodes()) {
            ProductDetail detail = hospitalProductDetail(productCode.trim());
            ProductCreateRequest pending = toRequest(detail);
            assertInformationChangeHasDifference(detail, pending);
            createPendingApplication("信息变更", pending, "医院目录提交审批");
            submittedRows++;
        }

        return Map.of("submittedRows", submittedRows);
    }

    /** 导出医院商品 */
    public List<Map<String, Object>> exportHospitalProducts(Map<String, String> params) {
        return hospitalProducts(params).rows();
    }

    /** 查询商品详情 */
    public ProductDetail hospitalProductDetail(String productCode) {
        return jdbcTemplate.queryForObject("""
                SELECT p.product_id AS product_id, p.product_code, p.product_name, p.spec_model,
                       p.brand, COALESCE(c.category_name, '-') AS category_name,
                       COALESCE(m.manufacturer_name, '-') AS manufacturer_name,
                       COALESCE(s.supplier_name, '-') AS supplier_name,
                       p.unit, p.purchase_price, p.retail_price, p.min_purchase_qty,
                       p.purchase_unit, p.conversion_rate, p.udi_code, p.registration_no,
                       p.registration_expire_date, p.production_license_no, p.business_license_no,
                       p.is_volume_based, p.is_centralized_procurement, p.is_domestic, p.contract_code,
                       p.first_category, p.second_category, p.third_category, p.is_chargeable,
                       p.tender_sub_code,
                       p.is_high_value, p.is_cold_chain, p.is_quota_managed, p.storage_condition,
                       p.status
                FROM product p
                LEFT JOIN product_category c ON p.category_id = c.category_id
                LEFT JOIN manufacturer m ON p.manufacturer_id = m.manufacturer_id
                LEFT JOIN supplier s ON p.supplier_id = s.supplier_id
                WHERE p.product_code = ? AND p.deleted = 0
                """, (rs, rowNum) -> {
            Long productId = rs.getLong("product_id");
            List<ProductAttachment> attachments = loadProductAttachments(productId, rs.getString("registration_no"),
                    rs.getString("registration_expire_date"), rs.getString("production_license_no"),
                    rs.getString("business_license_no"));

            return new ProductDetail(
                    productId,
                    rs.getString("product_code"),
                    rs.getString("product_name"),
                    rs.getString("spec_model"),
                    fallback(rs.getString("brand")),
                    fallback(rs.getString("category_name")),
                    fallback(rs.getString("manufacturer_name")),
                    fallback(rs.getString("supplier_name")),
                    rs.getString("unit"),
                    rs.getBigDecimal("purchase_price"),
                    rs.getBigDecimal("retail_price"),
                    rs.getBigDecimal("min_purchase_qty"),
                    fallback(rs.getString("purchase_unit")),
                    rs.getBigDecimal("conversion_rate"),
                    fallback(rs.getString("udi_code")),
                    fallback(rs.getString("registration_no")),
                    dateString(rs.getDate("registration_expire_date")),
                    fallback(rs.getString("production_license_no")),
                    fallback(rs.getString("business_license_no")),
                    rs.getInt("is_volume_based") == 1,
                    rs.getInt("is_centralized_procurement") == 1,
                    rs.getInt("is_domestic") == 1,
                    fallback(rs.getString("contract_code")),
                    fallback(rs.getString("first_category")),
                    fallback(rs.getString("second_category")),
                    fallback(rs.getString("third_category")),
                    rs.getInt("is_chargeable") == 1,
                    fallback(rs.getString("tender_sub_code")),
                    rs.getInt("is_high_value") == 1,
                    rs.getInt("is_cold_chain") == 1,
                    rs.getInt("is_quota_managed") == 1,
                    fallback(rs.getString("storage_condition")),
                    rs.getInt("status") == 1 ? "启用" : "停用",
                    attachments
            );
        }, productCode);
    }

    // ==================== 私有辅助方法 ====================

    private List<ProductAttachment> loadProductAttachments(Long productId, String registrationNo,
                                                           String registrationExpireDate,
                                                           String productionLicenseNo,
                                                           String businessLicenseNo) {
        List<ProductAttachment> attachments = jdbcTemplate.query("""
                SELECT file_name, category, file_url, valid_date
                FROM sys_attachment
                WHERE biz_type = 'product' AND biz_id = ? AND deleted = 0
                ORDER BY attachment_id DESC
                """, (rs, rowNum) -> new ProductAttachment(
                rs.getString("file_name"),
                attachmentCategory(rs.getString("category")),
                "可查看",
                rs.getString("file_url"),
                dateString(rs.getDate("valid_date"))
        ), productId);

        if (!attachments.isEmpty()) {
            return attachments;
        }

        List<ProductAttachment> fallbackAttachments = new ArrayList<>();
        if (registrationNo != null && !registrationNo.isBlank() && !"-".equals(registrationNo)) {
            fallbackAttachments.add(new ProductAttachment("注册证附件.pdf", "注册证", "可查看", "", registrationExpireDate));
        }
        if (productionLicenseNo != null && !productionLicenseNo.isBlank()) {
            fallbackAttachments.add(new ProductAttachment("生产许可证附件.pdf", "生产许可", "可查看", "", ""));
        }
        if (businessLicenseNo != null && !businessLicenseNo.isBlank()) {
            fallbackAttachments.add(new ProductAttachment("经营许可证附件.pdf", "经营许可", "可查看", "", ""));
        }
        if (fallbackAttachments.isEmpty()) {
            fallbackAttachments.add(new ProductAttachment("商品资质材料.pdf", "资质材料", "可查看", "", ""));
        }
        return fallbackAttachments;
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

    private String createPendingApplication(String applicationType, ProductCreateRequest request, String reason) {
        String applicationNo = nextApplicationNo();
        Long manufacturerId = findIdByName("manufacturer", "manufacturer_id", "manufacturer_name", request.manufacturerName());
        Long supplierId = findIdByName("supplier", "supplier_id", "supplier_name", request.supplierName());
        Long categoryId = ensureCategory(request.firstCategory(), request.secondCategory(), request.thirdCategory());

        jdbcTemplate.update("""
                INSERT INTO pending_product_application (
                  application_no, application_type, supplier_id, product_name, product_code, spec_model,
                  brand, manufacturer_id, manufacturer_name, category_id, unit, purchase_price, retail_price,
                  min_purchase_qty, purchase_unit, conversion_rate, udi_code, registration_no,
                  registration_expire_date, production_license_no, business_license_no,
                  is_volume_based, is_centralized_procurement, is_domestic, contract_code,
                  first_category, second_category, third_category, is_chargeable, tender_sub_code,
                  qualification_attachment_count, is_high_value, is_cold_chain, is_quota_managed,
                  storage_condition, product_snapshot, change_diff, approval_status, submit_by, submit_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  JSON_OBJECT('source','manual','changeReason', ?), JSON_OBJECT('changeReason', ?),
                  'pending_initial', 1, NOW())
                """,
                applicationNo,
                normalizeApplicationType(applicationType),
                supplierId,
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
                toSqlDate(request.registrationExpireDate()),
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
                estimateAttachmentCount(request),
                Boolean.TRUE.equals(request.highValue()) ? 1 : 0,
                Boolean.TRUE.equals(request.coldChain()) ? 1 : 0,
                Boolean.TRUE.equals(request.quotaManaged()) ? 1 : 0,
                nullIfBlank(request.storageCondition()),
                nullIfBlank(reason),
                nullIfBlank(reason)
        );

        return applicationNo;
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

    private ProductCreateRequest toRequest(ProductDetail detail) {
        return new ProductCreateRequest(
                detail.productCode(),
                detail.productName(),
                detail.specModel(),
                cleanFallback(detail.brand()),
                cleanFallback(detail.manufacturerName()),
                cleanFallback(detail.supplierName()),
                detail.unit(),
                detail.purchasePrice(),
                detail.retailPrice(),
                detail.minPurchaseQty(),
                cleanFallback(detail.purchaseUnit()),
                detail.conversionRate(),
                cleanFallback(detail.udiCode()),
                cleanFallback(detail.registrationNo()),
                cleanFallback(detail.registrationExpireDate()),
                cleanFallback(detail.productionLicenseNo()),
                cleanFallback(detail.businessLicenseNo()),
                detail.volumeBased(),
                detail.centralizedProcurement(),
                detail.domestic(),
                cleanFallback(detail.contractCode()),
                cleanFallback(detail.firstCategory()),
                cleanFallback(detail.secondCategory()),
                cleanFallback(detail.thirdCategory()),
                detail.chargeable(),
                cleanFallback(detail.tenderSubCode()),
                detail.highValue(),
                detail.coldChain(),
                detail.quotaManaged(),
                cleanFallback(detail.storageCondition())
        );
    }

    private ProductCreateRequest mergeBatchUpdate(ProductDetail detail, ProductBatchUpdateRequest request) {
        ProductCreateRequest base = toRequest(detail);
        return new ProductCreateRequest(
                base.productCode(),
                base.productName(),
                base.specModel(),
                base.brand(),
                base.manufacturerName(),
                base.supplierName(),
                prefer(request.unit(), base.unit()),
                request.purchasePrice() == null ? base.purchasePrice() : request.purchasePrice(),
                base.retailPrice(),
                base.minPurchaseQty(),
                base.purchaseUnit(),
                base.conversionRate(),
                base.udiCode(),
                prefer(request.registrationNo(), base.registrationNo()),
                base.registrationExpireDate(),
                base.productionLicenseNo(),
                base.businessLicenseNo(),
                parseYesNo(request.volumeBased(), base.volumeBased()),
                base.centralizedProcurement(),
                parseYesNo(request.domestic(), base.domestic()),
                prefer(request.contractCode(), base.contractCode()),
                prefer(request.firstCategory(), base.firstCategory()),
                prefer(request.secondCategory(), base.secondCategory()),
                prefer(request.thirdCategory(), base.thirdCategory()),
                parseYesNo(request.chargeable(), base.chargeable()),
                base.tenderSubCode(),
                base.highValue(),
                base.coldChain(),
                base.quotaManaged(),
                base.storageCondition()
        );
    }

    private boolean hasBatchChange(ProductBatchUpdateRequest request) {
        return !isBlank(request.volumeBased()) || !isBlank(request.domestic()) || request.purchasePrice() != null ||
                !isBlank(request.unit()) || !isBlank(request.registrationNo()) || !isBlank(request.contractCode()) ||
                !isBlank(request.firstCategory()) || !isBlank(request.secondCategory()) ||
                !isBlank(request.thirdCategory()) || !isBlank(request.chargeable());
    }

    private void assertInformationChangeHasDifference(ProductDetail current, ProductCreateRequest request) {
        if (!hasInformationChangeDifference(current, request)) {
            throw new IllegalArgumentException("未检测到与医院目录当前数据的差异，不需要提交信息变更审批");
        }
    }

    private boolean hasInformationChangeDifference(ProductDetail current, ProductCreateRequest request) {
        return differentText(current.productName(), request.productName()) ||
                differentText(current.specModel(), request.specModel()) ||
                differentText(current.brand(), request.brand()) ||
                differentText(current.manufacturerName(), request.manufacturerName()) ||
                differentText(current.supplierName(), request.supplierName()) ||
                differentText(current.unit(), request.unit()) ||
                differentDecimal(current.purchasePrice(), request.purchasePrice()) ||
                differentDecimal(current.retailPrice(), request.retailPrice()) ||
                differentDecimal(current.minPurchaseQty(), request.minPurchaseQty()) ||
                differentText(current.purchaseUnit(), request.purchaseUnit()) ||
                differentDecimal(current.conversionRate(), request.conversionRate()) ||
                differentText(current.udiCode(), request.udiCode()) ||
                differentText(current.registrationNo(), request.registrationNo()) ||
                differentText(current.registrationExpireDate(), request.registrationExpireDate()) ||
                differentText(current.productionLicenseNo(), request.productionLicenseNo()) ||
                differentText(current.businessLicenseNo(), request.businessLicenseNo()) ||
                current.volumeBased() != request.volumeBased() ||
                current.centralizedProcurement() != request.centralizedProcurement() ||
                current.domestic() != request.domestic() ||
                differentText(current.contractCode(), request.contractCode()) ||
                differentText(current.firstCategory(), request.firstCategory()) ||
                differentText(current.secondCategory(), request.secondCategory()) ||
                differentText(current.thirdCategory(), request.thirdCategory()) ||
                current.chargeable() != request.chargeable() ||
                differentText(current.tenderSubCode(), request.tenderSubCode()) ||
                current.highValue() != request.highValue() ||
                current.coldChain() != request.coldChain() ||
                current.quotaManaged() != request.quotaManaged() ||
                differentText(current.storageCondition(), request.storageCondition());
    }

    private static boolean differentText(String before, String after) {
        return !Objects.equals(normalizeText(before), normalizeText(after));
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return "";
        }
        return value.trim();
    }

    private static boolean differentDecimal(BigDecimal before, BigDecimal after) {
        BigDecimal left = before == null ? BigDecimal.ZERO : before;
        BigDecimal right = after == null ? BigDecimal.ZERO : after;
        return left.compareTo(right) != 0;
    }

    private boolean differs(String productCode, String column, BigDecimal nextValue) {
        List<BigDecimal> values = jdbcTemplate.queryForList(
                "SELECT " + column + " FROM product WHERE product_code = ? AND deleted = 0 LIMIT 1",
                BigDecimal.class,
                productCode
        );
        return values.isEmpty() || values.get(0) == null || values.get(0).compareTo(nextValue) != 0;
    }

    private static int estimateAttachmentCount(ProductCreateRequest request) {
        int count = 0;
        if (!isBlank(request.registrationNo())) {
            count++;
        }
        if (!isBlank(request.productionLicenseNo())) {
            count++;
        }
        if (!isBlank(request.businessLicenseNo())) {
            count++;
        }
        return count;
    }

    private static void validateQuotaEligibility(boolean highValue, boolean coldChain, boolean quotaManaged) {
        if (quotaManaged && (highValue || coldChain)) {
            throw new IllegalArgumentException("高值耗材或冷链耗材不能设置为定数管理");
        }
    }
}
