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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendingProductAttachmentServiceTest {

    @TempDir
    Path tempDir;

    /** Smallest valid JPEG byte sequence: SOI + JFIF APP0 + EOI. */
    private static final byte[] JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
    };

    private PendingProductAttachmentService service(JdbcTemplate jdbc) {
        OperatorContextProvider operators = mock(OperatorContextProvider.class);
        ApprovalFlowGuard guard = mock(ApprovalFlowGuard.class);
        when(operators.current()).thenReturn(OperatorContext.system());
        when(jdbc.queryForMap(contains("FROM pending_product_application"), eq("APP-1")))
                .thenReturn(Map.of("applicationId", 1L, "submitBy", 1L, "approvalStatus", "pending_initial"));
        when(jdbc.queryForObject(contains("COUNT(*) FROM sys_attachment"), eq(Integer.class), eq(1L)))
                .thenReturn(0);
        when(jdbc.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Long.class))).thenReturn(77L);
        return new PendingProductAttachmentService(jdbc, operators, guard, tempDir.toString());
    }

    @Test
    void rejectsExecutableContentDisguisedAsPdf() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PendingProductAttachmentService service = service(jdbc);

        MockMultipartFile file = new MockMultipartFile("file", "license.pdf", "application/pdf",
                "MZ executable".getBytes());

        assertThatThrownBy(() -> service.upload("APP-1", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件签名");
    }

    @Test
    void acceptsFileNameContainingSpace() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PendingProductAttachmentService service = service(jdbc);

        MockMultipartFile file = new MockMultipartFile("file", "营业执照 (1).jpg", "image/jpeg", JPEG_BYTES);

        Map<String, Object> result = service.upload("APP-1", file);

        assertThat(result).containsEntry("attachmentId", 77L);
    }

    @Test
    void acceptsJpegExtensionAlias() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PendingProductAttachmentService service = service(jdbc);

        MockMultipartFile file = new MockMultipartFile("file", "test 附件.jpeg", "image/jpeg", JPEG_BYTES);

        Map<String, Object> result = service.upload("APP-1", file);

        assertThat(result).containsEntry("attachmentId", 77L);
    }

    @Test
    void acceptsUpperCaseExtensionAlias() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PendingProductAttachmentService service = service(jdbc);

        MockMultipartFile file = new MockMultipartFile("file", "SCAN.JPG", "image/jpeg", JPEG_BYTES);

        Map<String, Object> result = service.upload("APP-1", file);

        assertThat(result).containsEntry("attachmentId", 77L);
    }

    @Test
    void rejectsExtensionThatDoesNotMatchDetectedSignature() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PendingProductAttachmentService service = service(jdbc);

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", JPEG_BYTES);

        assertThatThrownBy(() -> service.upload("APP-1", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件扩展名与文件签名不一致");
    }

    @Test
    void stripsPathSegmentsFromOriginalFileName() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PendingProductAttachmentService service = service(jdbc);

        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.jpg", "image/jpeg", JPEG_BYTES);

        Map<String, Object> result = service.upload("APP-1", file);

        assertThat(result).containsEntry("attachmentId", 77L);
    }
}
