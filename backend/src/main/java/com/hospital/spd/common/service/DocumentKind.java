package com.hospital.spd.common.service;

/**
 * Names the business document number policies that share the concurrency-safe allocator.
 */
public enum DocumentKind {
    SUPPLIER("SUP", 5, "supplier", "supplier_code"),
    MANUFACTURER("MFR", 5, "manufacturer", "manufacturer_code"),
    PURCHASE_ORDER("CG", 3, "purchase_order", "order_no"),
    PURCHASE_DEMAND("XQ", 4, "purchase_demand", "demand_no"),
    PURCHASE_PLAN("JH", 4, "purchase_plan", "plan_no"),
    PURCHASE_REPLENISHMENT_ANALYSIS("CGFX", 5, "purchase_replenishment_analysis", "analysis_no"),
    RECEIVING_ORDER("RK", 3, "receiving_order", "receiving_no"),
    INVENTORY_BATCH("PC", 5, "inventory_batch", "system_batch_no"),
    INVENTORY_EVENT("KC", 5, "inventory_event", "event_no"),
    INVENTORY_STOCKTAKING("PD", 5, "inventory_stocktaking", "stocktaking_no"),
    BATCH_PRICE_ADJUSTMENT("TJ", 5, "batch_price_adjustment", "adjustment_no"),
    SHORTAGE_REPLENISHMENT_TASK("QH", 5, "shortage_replenishment_task", "task_no"),
    DEPARTMENT_REQUISITION("SL", 5, "department_requisition", "requisition_no"),
    DELIVERY_ORDER("PS", 5, "spd_delivery_order", "delivery_no"),
    DEPARTMENT_CONSUMPTION("XH", 5, "department_consumption", "consumption_no"),
    CONSUMPTION_RED_FLUSH("FXH", 5, "consumption_red_flush", "flush_no"),
    SETTLEMENT_BILL("JS", 5, "settlement_bill", "settlement_no"),
    SUPPLIER_INVOICE("FP", 5, "supplier_invoice", "invoice_no"),
    PDA_OFFLINE_RECORD("PDA", 5, "pda_offline_record", "record_no"),
    COLD_CHAIN_EXCEPTION("LL", 5, "cold_chain_exception", "event_no"),
    RECALL_EVENT("ZH", 5, "recall_event", "recall_no"),
    HIGH_VALUE_CHARGE("GZ", 5, "high_value_charge", "charge_no"),
    HIGH_VALUE_UNIQUE_CODE("", 6, "udi_trace_code", "unique_code"),
    UDI_TRACE_EVENT("UT", 6, "udi_trace_event", "event_no"),
    QUOTA_PACKING_TASK("DB", 5, "quota_packing_task", "task_no"),
    QUOTA_PACKAGE_LABEL("D", 6, "quota_package_label", "label_no"),
    QUOTA_PACKAGE_EVENT("DBSJ", 5, "quota_package_event", "event_no"),
    QUOTA_PACKAGE_TEMPLATE("DS", 5, "quota_package_template", "template_code");

    private final String prefix;
    private final int width;
    private final String table;
    private final String column;

    DocumentKind(String prefix, int width, String table, String column) {
        this.prefix = prefix;
        this.width = width;
        this.table = table;
        this.column = column;
    }

    public String prefix() {
        return prefix;
    }

    public int width() {
        return width;
    }

    public String table() {
        return table;
    }

    public String column() {
        return column;
    }
}
