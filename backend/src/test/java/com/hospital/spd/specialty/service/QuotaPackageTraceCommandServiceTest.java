package com.hospital.spd.specialty.service;

import com.hospital.spd.supplychain.service.OperationalDeliveryModule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaPackageTraceCommandServiceTest {

    private final QuotaPackageTraceFlowService traceFlowService = mock(QuotaPackageTraceFlowService.class);
    private final OperationalDeliveryModule deliveryModule = mock(OperationalDeliveryModule.class);
    private final QuotaPackageTraceCommandService service =
            new QuotaPackageTraceCommandService(traceFlowService, deliveryModule);

    @Test
    void signsDeliveredPackageBeforeCompletingTraceState() {
        when(traceFlowService.signTarget("QP-001")).thenReturn(Map.of(
                "status", "delivered",
                "deliveryNo", "DEL-001",
                "labelId", 7L
        ));

        Map<String, Object> result = service.sign(Map.of("code", "QP-001"));

        verify(deliveryModule).signDelivery("DEL-001");
        verify(traceFlowService).completeSign(7L, "DEL-001");
        assertThat(result).containsEntry("status", "signed");
    }

    @Test
    void rejectsPackageOutsideDeliveredOrSignedState() {
        when(traceFlowService.signTarget("QP-002")).thenReturn(Map.of(
                "status", "packed",
                "deliveryNo", "DEL-002",
                "labelId", 8L
        ));

        assertThatThrownBy(() -> service.sign(Map.of("code", "QP-002")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已配送");

        verify(deliveryModule, never()).signDelivery("DEL-002");
        verify(traceFlowService, never()).completeSign(8L, "DEL-002");
    }
}
