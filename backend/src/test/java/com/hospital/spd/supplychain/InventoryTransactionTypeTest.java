package com.hospital.spd.supplychain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTransactionTypeTest {

    @Test
    void mapsRawInventoryEventsToStableTransactionTypes() {
        assertThat(InventoryTransactionType.fromEvent("purchase_receive_in", "中心库", 10))
                .isEqualTo(InventoryTransactionType.RECEIVING_IN);
        assertThat(InventoryTransactionType.fromEvent("quota_pack_out", "中心库", -10))
                .isEqualTo(InventoryTransactionType.QUOTA_PACK_IN);
        assertThat(InventoryTransactionType.fromEvent("quota_unpack_in", "中心库", 10))
                .isEqualTo(InventoryTransactionType.QUOTA_UNPACK);
        assertThat(InventoryTransactionType.fromEvent("warehouse_transfer_in", "二级库", 10))
                .isEqualTo(InventoryTransactionType.SECONDARY_IN);
        assertThat(InventoryTransactionType.fromEvent("warehouse_transfer_out", "三级库", -10))
                .isEqualTo(InventoryTransactionType.TERTIARY_OUT);
        assertThat(InventoryTransactionType.fromEvent("batch_price_adjustment", "一级库", 0))
                .isEqualTo(InventoryTransactionType.BATCH_PRICE_ADJUSTMENT);
    }

    @Test
    void exposesStableCodeLabelAndCategory() {
        assertThat(InventoryTransactionType.BATCH_PRICE_ADJUSTMENT.code()).isEqualTo("batch_price_adjustment");
        assertThat(InventoryTransactionType.BATCH_PRICE_ADJUSTMENT.label()).isEqualTo("批次调价");
        assertThat(InventoryTransactionType.BATCH_PRICE_ADJUSTMENT.category()).isEqualTo("valuation");
        assertThat(InventoryTransactionType.options()).hasSize(8);
    }
}
