package com.hospital.spd.system;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void reportsConfiguredServicePort() {
        assertThat(new HealthController(29181).health().data().get("port")).isEqualTo("29181");
    }
}
