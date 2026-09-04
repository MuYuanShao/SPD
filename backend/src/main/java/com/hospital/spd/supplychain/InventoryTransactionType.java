package com.hospital.spd.supplychain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Defines the stable inventory-ledger transaction taxonomy exposed by the API. */
public enum InventoryTransactionType {
    RECEIVING_IN("receiving_in", "验收入库", "quantity"),
    QUOTA_PACK_IN("quota_pack_in", "打包入库", "quantity"),
    QUOTA_UNPACK("quota_unpack", "解包", "quantity"),
    SECONDARY_IN("secondary_in", "二级库入库", "quantity"),
    TERTIARY_IN("tertiary_in", "三级库入库", "quantity"),
    SECONDARY_OUT("secondary_out", "二级库出库", "quantity"),
    TERTIARY_OUT("tertiary_out", "三级库出库", "quantity"),
    BATCH_PRICE_ADJUSTMENT("batch_price_adjustment", "批次调价", "valuation");

    private final String code;
    private final String label;
    private final String category;

    InventoryTransactionType(String code, String label, String category) {
        this.code = code;
        this.label = label;
        this.category = category;
    }

    public String code() { return code; }
    public String label() { return label; }
    public String category() { return category; }

    public static InventoryTransactionType fromEvent(String eventType, String warehouseType, int direction) {
        if ("batch_price_adjustment".equals(eventType)) return BATCH_PRICE_ADJUSTMENT;
        if ("purchase_receive_in".equals(eventType)) return RECEIVING_IN;
        if ("quota_pack_out".equals(eventType)) return QUOTA_PACK_IN;
        if ("quota_unpack_in".equals(eventType) || "quota_terminate_in".equals(eventType)) return QUOTA_UNPACK;
        if (warehouseType != null && warehouseType.contains("三级")) {
            return direction > 0 ? TERTIARY_IN : TERTIARY_OUT;
        }
        if (warehouseType != null && warehouseType.contains("二级")) {
            return direction > 0 ? SECONDARY_IN : SECONDARY_OUT;
        }
        return direction > 0 ? RECEIVING_IN : SECONDARY_OUT;
    }

    public static InventoryTransactionType fromCodeOrLabel(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(values())
                .filter(item -> item.code.equals(value.trim()) || item.label.equals(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的库存交易类型：" + value));
    }

    public static List<Map<String, String>> options() {
        return Arrays.stream(values())
                .map(item -> Map.of("code", item.code, "label", item.label, "category", item.category))
                .toList();
    }
}
