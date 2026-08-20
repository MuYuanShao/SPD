package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.PendingProductChangeItem;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hospital.spd.common.SqlHelper.isBlank;
import static com.hospital.spd.masterdata.service.ProductApprovalMapper.getDateString;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.addChange;
import static com.hospital.spd.masterdata.service.ProductApprovalValues.boolLabel;

final class ProductApprovalChangeItems {
    private final JdbcTemplate jdbcTemplate;

    ProductApprovalChangeItems(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingProductChangeItem> load(ResultSet application) throws SQLException {
        if (!"信息变更".equals(application.getString("application_type"))) {
            return List.of();
        }

        String productCode = application.getString("product_code");
        if (isBlank(productCode)) {
            return List.of();
        }

        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT p.product_name, p.spec_model, p.brand,
                       COALESCE(m.manufacturer_name, '') AS manufacturer_name,
                       COALESCE(s.supplier_name, a.supplier_name, '') AS supplier_name,
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
                """, productCode);

        Map<String, Object> current = products.isEmpty() ? Map.of() : products.get(0);
        List<PendingProductChangeItem> changes = new ArrayList<>();
        addChange(changes, "商品名称", current.get("product_name"), application.getString("product_name"));
        addChange(changes, "规格型号", current.get("spec_model"), application.getString("spec_model"));
        addChange(changes, "品牌", current.get("brand"), application.getString("brand"));
        addChange(changes, "生产厂家", current.get("manufacturer_name"), application.getString("manufacturer_name"));
        addChange(changes, "供应商", current.get("supplier_name"), application.getString("supplier_name"));
        addChange(changes, "单位", current.get("unit"), application.getString("unit"));
        addChange(changes, "采购价", current.get("purchase_price"), application.getBigDecimal("purchase_price"));
        addChange(changes, "零售价", current.get("retail_price"), application.getBigDecimal("retail_price"));
        addChange(changes, "最小采购量", current.get("min_purchase_qty"), application.getBigDecimal("min_purchase_qty"));
        addChange(changes, "采购单位", current.get("purchase_unit"), application.getString("purchase_unit"));
        addChange(changes, "换算系数", current.get("conversion_rate"), application.getBigDecimal("conversion_rate"));
        addChange(changes, "UDI编码", current.get("udi_code"), application.getString("udi_code"));
        addChange(changes, "注册证号", current.get("registration_no"), application.getString("registration_no"));
        addChange(changes, "注册证有效期", current.get("registration_expire_date"), getDateString(application, "registration_expire_date"));
        addChange(changes, "生产许可证号", current.get("production_license_no"), application.getString("production_license_no"));
        addChange(changes, "经营许可证号", current.get("business_license_no"), application.getString("business_license_no"));
        addChange(changes, "是否带量", boolLabel(current.get("is_volume_based")), boolLabel(application.getInt("is_volume_based")));
        addChange(changes, "是否集采", boolLabel(current.get("is_centralized_procurement")), boolLabel(application.getInt("is_centralized_procurement")));
        addChange(changes, "是否国产", boolLabel(current.get("is_domestic")), boolLabel(application.getInt("is_domestic")));
        addChange(changes, "合同编码", current.get("contract_code"), application.getString("contract_code"));
        addChange(changes, "一级分类", current.get("first_category"), application.getString("first_category"));
        addChange(changes, "二级分类", current.get("second_category"), application.getString("second_category"));
        addChange(changes, "三级分类", current.get("third_category"), application.getString("third_category"));
        addChange(changes, "是否收费", boolLabel(current.get("is_chargeable")), boolLabel(application.getInt("is_chargeable")));
        addChange(changes, "招采子编码", current.get("tender_sub_code"), application.getString("tender_sub_code"));
        addChange(changes, "是否高值耗材", boolLabel(current.get("is_high_value")), boolLabel(application.getInt("is_high_value")));
        addChange(changes, "是否冷链", boolLabel(current.get("is_cold_chain")), boolLabel(application.getInt("is_cold_chain")));
        addChange(changes, "是否定数管理", boolLabel(current.get("is_quota_managed")), boolLabel(application.getInt("is_quota_managed")));
        addChange(changes, "储存条件", current.get("storage_condition"), application.getString("storage_condition"));
        return changes;
    }
}
