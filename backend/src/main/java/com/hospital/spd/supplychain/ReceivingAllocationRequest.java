package com.hospital.spd.supplychain;

import java.math.BigDecimal;

/** Binds a receiving-order allocation without silently converting malformed quantities to allocate-all. */
public record ReceivingAllocationRequest(String receivingNo, BigDecimal quantity) {
}
