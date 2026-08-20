package com.hospital.spd.supplychain;

import com.hospital.spd.common.GlobalExceptionHandler;
import com.hospital.spd.supplychain.service.UdiTraceabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class UdiTraceabilityControllerTest {

    private UdiTraceabilityService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(UdiTraceabilityService.class);
        mockMvc = standaloneSetup(new UdiTraceabilityController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsHighValueConsumableDetail() throws Exception {
        when(service.detail("HV-001")).thenReturn(detail("high_value", "HV-001", null));

        mockMvc.perform(get("/udi-traceability/records/{code}", "HV-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.record.traceScope").value("high_value"))
                .andExpect(jsonPath("$.data.record.uniqueCode").value("HV-001"));
    }

    @Test
    void returnsLowValueQuotaPackageDetail() throws Exception {
        when(service.detail("PACK-001")).thenReturn(detail("low_value_quota_pack", "PACK-UID-001", "PACK-001"));

        mockMvc.perform(get("/udi-traceability/records/{code}", "PACK-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.record.traceScope").value("low_value_quota_pack"))
                .andExpect(jsonPath("$.data.record.packageLabelNo").value("PACK-001"));
    }

    @Test
    void returnsBusinessErrorForUnknownCode() throws Exception {
        when(service.detail("UNKNOWN")).thenThrow(new IllegalArgumentException("追溯码不存在"));

        mockMvc.perform(get("/udi-traceability/records/{code}", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("追溯码不存在"));
    }

    private static Map<String, Object> detail(String scope, String uniqueCode, String packageLabelNo) {
        Map<String, Object> record = new java.util.LinkedHashMap<>();
        record.put("traceScope", scope);
        record.put("uniqueCode", uniqueCode);
        record.put("packageLabelNo", packageLabelNo);
        return Map.of("record", record, "timeline", List.of());
    }
}
