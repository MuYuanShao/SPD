package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.ApprovalTimelineNode;
import com.hospital.spd.masterdata.PendingProductApplicationDetail;
import com.hospital.spd.masterdata.PendingProductApplicationRow;
import com.hospital.spd.masterdata.PendingProductChangeItem;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.hospital.spd.masterdata.service.ProductApprovalStatus.toStatusLabel;
import static com.hospital.spd.masterdata.service.ProductApprovalStatus.toStatusTone;

final class ProductApprovalMapper {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ProductApprovalMapper() {
    }

    static PendingProductApplicationRow mapRow(ResultSet rs) throws SQLException {
        String status = rs.getString("approval_status");
        return new PendingProductApplicationRow(
                rs.getString("application_no"),
                rs.getString("application_type"),
                rs.getString("product_name"),
                rs.getString("supplier_name"),
                "申请人" + rs.getLong("submit_by"),
                rs.getTimestamp("submit_time").toLocalDateTime().format(FORMATTER),
                toStatusLabel(status),
                toStatusTone(status),
                "pending_initial".equals(status),
                rs.getString("product_code"),
                "",
                rs.getString("manufacturer_name"),
                rs.getString("registration_no"),
                rs.getString("contract_code"),
                rs.getString("first_category"),
                rs.getString("second_category"),
                rs.getString("third_category"),
                rs.getInt("is_volume_based") == 1,
                rs.getInt("is_centralized_procurement") == 1,
                rs.getInt("is_domestic") == 1,
                rs.getInt("is_chargeable") == 1,
                rs.getBigDecimal("purchase_price"),
                rs.getString("purchase_unit"),
                rs.getString("udi_code"),
                rs.getInt("is_quota_managed") == 1
        );
    }

    static PendingProductApplicationDetail mapDetail(ResultSet rs,
                                                     String status,
                                                     String applicant,
                                                     String submitTime,
                                                     String statusLabel,
                                                     List<PendingProductChangeItem> changeItems,
                                                     List<ApprovalTimelineNode> timeline,
                                                     boolean canApprove) throws SQLException {
        return new PendingProductApplicationDetail(
                rs.getString("application_no"),
                rs.getString("application_type"),
                status,
                statusLabel,
                rs.getString("product_name"),
                rs.getString("product_code"),
                rs.getString("spec_model"),
                rs.getString("brand"),
                rs.getString("manufacturer_name"),
                rs.getString("supplier_name"),
                rs.getString("unit"),
                getBigDecimal(rs, "purchase_price"),
                getBigDecimal(rs, "retail_price"),
                getBigDecimal(rs, "min_purchase_qty"),
                rs.getString("purchase_unit"),
                getBigDecimal(rs, "conversion_rate"),
                rs.getString("udi_code"),
                rs.getString("registration_no"),
                getDateString(rs, "registration_expire_date"),
                rs.getString("production_license_no"),
                rs.getString("business_license_no"),
                rs.getInt("is_volume_based") == 1,
                rs.getInt("is_centralized_procurement") == 1,
                rs.getInt("is_domestic") == 1,
                rs.getString("contract_code"),
                rs.getString("first_category"),
                rs.getString("second_category"),
                rs.getString("third_category"),
                rs.getInt("is_chargeable") == 1,
                rs.getString("tender_sub_code"),
                rs.getInt("qualification_attachment_count"),
                rs.getInt("is_high_value") == 1,
                rs.getInt("is_cold_chain") == 1,
                rs.getInt("is_quota_managed") == 1,
                rs.getString("storage_condition"),
                applicant,
                submitTime,
                rs.getString("approve_opinion"),
                rs.getString("initial_review_opinion"),
                rs.getString("final_review_opinion"),
                rs.getString("return_reason"),
                rs.getString("reject_reason"),
                changeItems,
                timeline,
                canApprove
        );
    }

    static String getDateString(ResultSet rs, String column) throws SQLException {
        var value = rs.getDate(column);
        return value == null ? "-" : value.toString();
    }

    static String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? "-" : timestamp.toLocalDateTime().format(FORMATTER);
    }

    private static BigDecimal getBigDecimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}
