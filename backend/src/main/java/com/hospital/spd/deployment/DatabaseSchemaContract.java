package com.hospital.spd.deployment;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/** Verifies runtime schema and authorization metadata that Flyway checksums alone cannot prove. */
public final class DatabaseSchemaContract {
    private DatabaseSchemaContract() {}

    public static void verify(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            try (var ignored = statement.executeQuery("SELECT invoice_id,invoice_no,supplier_id,settlement_id,amount,tax_amount,status,verify_time FROM supplier_invoice WHERE 1=0")) { }
            try (var ignored = statement.executeQuery("SELECT snapshot_event_id FROM inventory_stocktaking_item WHERE 1=0")) { }
            try (var ignored = statement.executeQuery("SELECT goods_type FROM settlement_bill_item WHERE 1=0")) { }
            try (var ignored = statement.executeQuery("SELECT document_key,current_order,flow_data_scope,status FROM business_approval_run WHERE 1=0")) { }
            Set<String> required = new HashSet<>(Set.of("purchase-management", "purchase-order:create", "purchase-order:approve", "invoice-management"));
            try (var rows = statement.executeQuery("SELECT perm_code FROM sys_permission WHERE perm_code IN ('purchase-management','purchase-order:create','purchase-order:approve','invoice-management')")) {
                while (rows.next()) required.remove(rows.getString(1));
            }
            if (!required.isEmpty()) throw new IllegalStateException("数据库授权元数据缺失：" + String.join("、", required) + "；请执行补偿迁移并检查结构");
        } catch (SQLException error) {
            throw new SQLException("数据库业务结构校验失败，请执行补偿迁移后启动；不能仅修复迁移校验和", error.getSQLState(), error.getErrorCode(), error);
        }
    }
}
