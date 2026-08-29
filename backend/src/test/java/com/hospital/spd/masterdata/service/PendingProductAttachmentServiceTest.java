package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.system.service.ApprovalFlowGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendingProductAttachmentServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsExecutableContentDisguisedAsPdf() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OperatorContextProvider operators = mock(OperatorContextProvider.class);
        ApprovalFlowGuard guard = mock(ApprovalFlowGuard.class);
        when(operators.current()).thenReturn(OperatorContext.system());
        when(jdbc.queryForMap(contains("FROM pending_product_application"), eq("APP-1")))
                .thenReturn(Map.of("applicationId", 1L, "submitBy", 1L, "approvalStatus", "pending_initial"));
        when(jdbc.queryForObject(contains("COUNT(*) FROM sys_attachment"), eq(Integer.class), eq(1L)))
                .thenReturn(0);
        PendingProductAttachmentService service = new PendingProductAttachmentService(
                jdbc, operators, guard, tempDir.toString());

        MockMultipartFile file = new MockMultipartFile("file", "license.pdf", "application/pdf",
                "MZ executable".getBytes());

        assertThatThrownBy(() -> service.upload("APP-1", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件签名");
    }
}
