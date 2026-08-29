package com.hospital.spd.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SpaFallbackControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new SpaFallbackController()).build();
    }

    @Test
    void rootRedirectsToBundledApplication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/"));
    }

    @Test
    void nestedHistoryRouteForwardsToBundledIndex() throws Exception {
        mockMvc.perform(get("/app/supply-chain/requisitions/42"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/app/index.html"));
    }
}
