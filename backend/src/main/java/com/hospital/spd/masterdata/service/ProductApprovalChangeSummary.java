package com.hospital.spd.masterdata.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ProductApprovalChangeSummary {

    private ProductApprovalChangeSummary() {
    }

    static String computeChangeSummary(String type, Map<String, Object> pending, Map<String, Object> current) {
        List<String> changed = new ArrayList<>();

        if ("信息变更".equals(type)) {
            diff(changed, pending, current, "product_name", "商品名称");
            diff(changed, pending, current, "spec_model", "规格型号");
            diff(changed, pending, current, "brand", "品牌");
            diff(changed, pending, current, "manufacturer_name", "生产厂家");
            diff(changed, pending, current, "supplier_name", "供应商");
            diff(changed, pending, current, "unit", "单位");
            diff(changed, pending, current, "purchase_price", "采购价");
            diff(changed, pending, current, "is_volume_based", "是否带量");
            diff(changed, pending, current, "is_centralized_procurement", "是否集采");
            diff(changed, pending, current, "is_domestic", "是否国产");
            diff(changed, pending, current, "contract_code", "合同编码");
            diff(changed, pending, current, "first_category", "一级分类");
            diff(changed, pending, current, "second_category", "二级分类");
            diff(changed, pending, current, "third_category", "三级分类");
            diff(changed, pending, current, "is_chargeable", "是否收费");
            diff(changed, pending, current, "tender_sub_code", "招采子编码");
            diff(changed, pending, current, "is_high_value", "是否高值耗材");
            diff(changed, pending, current, "is_cold_chain", "是否冷链");
            diff(changed, pending, current, "is_quota_managed", "是否定数管理");
            diff(changed, pending, current, "is_key_monitored", "重点监控");
            diff(changed, pending, current, "storage_condition", "储存条件");
        } else if ("资质更新".equals(type)) {
            diff(changed, pending, current, "registration_no", "注册证号");
            diff(changed, pending, current, "registration_expire_date", "注册证有效期");
            diff(changed, pending, current, "production_license_no", "生产许可证号");
            diff(changed, pending, current, "business_license_no", "经营许可证号");
        }

        if (changed.isEmpty()) return "";
        if (changed.size() <= 3) return String.join("、", changed) + " 变更";
        return String.join("、", changed.subList(0, 3)) + " 等" + changed.size() + "项变更";
    }

    private static void diff(List<String> changed, Map<String, Object> pending, Map<String, Object> current,
                             String column, String label) {
        Object newVal = pending.get(column);
        Object oldVal = current == null ? null : current.get(column);
        String newStr = newVal == null ? "" : newVal.toString().trim();
        String oldStr = oldVal == null ? "" : oldVal.toString().trim();

        if (!newStr.isEmpty() && !newStr.equals(oldStr) && !"-".equals(newStr)) {
            changed.add(label);
        }
    }
}
