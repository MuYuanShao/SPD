package com.hospital.spd.supplychain;

import java.math.BigDecimal;
import java.util.List;

public record StocktakingItemsUpdateRequest(
        @jakarta.validation.Valid List<Item> items
) {
    public record Item(Long itemId, @com.hospital.spd.common.WholeQuantity BigDecimal actualQty) {
    }
}
