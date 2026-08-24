package com.hospital.spd.supplychain;

import java.math.BigDecimal;
import java.util.List;

public record StocktakingItemsUpdateRequest(
        List<Item> items
) {
    public record Item(Long itemId, BigDecimal actualQty) {
    }
}
