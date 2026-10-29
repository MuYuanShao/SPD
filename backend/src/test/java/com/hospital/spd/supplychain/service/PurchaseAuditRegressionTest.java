package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ensures invalid demand payloads cannot allocate successful business documents. */
class PurchaseAuditRegressionTest {
    @Test void emptyDemandCannotReturnAnUnpersistedNumber() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SupplyChainSupport support = mock(SupplyChainSupport.class);
        when(support.nextNo(com.hospital.spd.common.service.DocumentKind.PURCHASE_DEMAND)).thenReturn("CG-EMPTY");
        assertThatThrownBy(() -> new PurchaseOrderService(jdbc, support).createDemand(Map.of("items", List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("明细");
        verifyNoInteractions(support);
    }
}
