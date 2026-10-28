package com.hospital.spd.common;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Keeps service-layer authorization failures distinct from unexpected server errors. */
class GlobalAccessDeniedTest {
    @Test
    void mapsDataScopeDenialToHttpAndBusiness403() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new DeniedController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/scope-denied")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @RestController static class DeniedController {
        @GetMapping("/scope-denied") public void denied() { throw new AccessDeniedException("internal scope detail"); }
    }
}
