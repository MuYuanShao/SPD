package com.hospital.spd.masterdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Writes immutable server-side catalog snapshots and field-level changes at submission time. */
@Service
public class CatalogApplicationSnapshotService {
    private static final Map<String, String> FIELDS = new LinkedHashMap<>();
    static {
        FIELDS.put("product_name", "商品名称"); FIELDS.put("spec_model", "规格型号");
        FIELDS.put("brand", "品牌"); FIELDS.put("manufacturer_id", "生产厂家");
        FIELDS.put("supplier_id", "供应商"); FIELDS.put("category_id", "商品分类");
        FIELDS.put("unit", "单位"); FIELDS.put("purchase_price", "采购价");
        FIELDS.put("retail_price", "零售价"); FIELDS.put("min_purchase_qty", "最小采购量");
        FIELDS.put("purchase_unit", "采购单位"); FIELDS.put("conversion_rate", "中包装数量");
        FIELDS.put("purchase_package_qty", "采购包装数量"); FIELDS.put("udi_code", "UDI编码");
        FIELDS.put("registration_no", "注册证号"); FIELDS.put("registration_expire_date", "注册证有效期");
        FIELDS.put("production_license_no", "生产许可证号"); FIELDS.put("business_license_no", "经营许可证号");
        FIELDS.put("is_volume_based", "是否带量"); FIELDS.put("is_centralized_procurement", "是否集采");
        FIELDS.put("is_domestic", "是否国产"); FIELDS.put("contract_code", "合同编码");
        FIELDS.put("first_category", "一级分类"); FIELDS.put("second_category", "二级分类");
        FIELDS.put("third_category", "三级分类"); FIELDS.put("is_chargeable", "是否收费");
        FIELDS.put("tender_sub_code", "招采子编码"); FIELDS.put("is_high_value", "是否高值耗材");
        FIELDS.put("is_cold_chain", "是否冷链"); FIELDS.put("is_quota_managed", "是否定数管理");
        FIELDS.put("is_key_monitored", "重点监控"); FIELDS.put("storage_condition", "储存条件");
    }

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CatalogApplicationSnapshotService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void write(Long applicationId, String applicationType, String reason, Integer explicitTargetStatus) {
        Map<String, Object> application = jdbcTemplate.queryForMap(
                "SELECT * FROM pending_product_application WHERE application_id = ?", applicationId);
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
                "SELECT * FROM product WHERE product_code = ? AND deleted = 0 ORDER BY product_id DESC LIMIT 1",
                application.get("product_code"));
        Map<String, Object> old = products.isEmpty() ? Map.of() : products.get(0);
        Integer originalStatus = number(old.get("status"));
        int targetStatus = explicitTargetStatus != null ? explicitTargetStatus
                : "停用申请".equals(applicationType) ? 0 : originalStatus == null ? 1 : originalStatus;
        List<Map<String, Object>> items = new ArrayList<>();
        if (!old.isEmpty()) {
            FIELDS.forEach((key, label) -> {
                Object before = old.get(key);
                Object after = application.get(key);
                if (!equalValue(before, after)) {
                    items.add(Map.of("fieldKey", key, "fieldName", label,
                            "beforeValue", display(before), "afterValue", display(after)));
                }
            });
            if (!Objects.equals(originalStatus, targetStatus)) {
                items.add(Map.of("fieldKey", "status", "fieldName", "启停状态",
                        "beforeValue", originalStatus != null && originalStatus == 1 ? "启用" : "停用",
                        "afterValue", targetStatus == 1 ? "启用" : "停用"));
            }
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", old.isEmpty() ? "new" : "hospital");
        snapshot.put("productId", old.get("product_id"));
        snapshot.put("productCode", application.get("product_code"));
        snapshot.put("productName", old.get("product_name"));
        snapshot.put("specModel", old.get("spec_model"));
        snapshot.put("unit", old.get("unit"));
        snapshot.put("purchasePrice", old.get("purchase_price"));
        snapshot.put("categoryId", old.get("category_id"));
        snapshot.put("status", originalStatus);
        snapshot.put("targetStatus", targetStatus);
        snapshot.put("before", old);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("changeReason", reason);
        diff.put("items", items);
        jdbcTemplate.update("""
                UPDATE pending_product_application
                   SET product_snapshot = CAST(? AS JSON), change_diff = CAST(? AS JSON)
                 WHERE application_id = ?
                """, json(snapshot), json(diff), applicationId);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("目录快照序列化失败", exception); }
    }
    private static boolean equalValue(Object left, Object right) {
        return display(left).equals(display(right));
    }
    private static String display(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static Integer number(Object value) { return value instanceof Number number ? number.intValue() : null; }
}
