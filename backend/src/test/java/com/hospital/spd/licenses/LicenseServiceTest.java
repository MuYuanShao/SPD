package com.hospital.spd.licenses;

import com.hospital.spd.common.OperatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LicenseServiceTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"product", "supplier", "manufacturer", "contract"})
    void listsLatestModifiedDocumentsBeforePagination(String type) {
        var jdbc = mock(JdbcTemplate.class);
        var service = new LicenseService(jdbc, OperatorContext::system, directory.toString());

        service.list(type, "", 2, 20);

        verify(jdbc).queryForList(
                contains("ORDER BY l.update_time DESC, l.license_id DESC LIMIT ? OFFSET ?"),
                eq(type), eq(20), eq(20));
    }

    @Test
    void storedNameCannotReadAnUnregisteredOrDeletedAttachment() throws Exception {
        Files.writeString(directory.resolve("license-1-old.pdf"), "%PDF-old");
        var service = new LicenseService(mock(JdbcTemplate.class), OperatorContext::system, directory.toString());
        assertThatThrownBy(() -> service.downloadAttachmentByStoredName("license-1-old.pdf"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("附件");
    }

    @Test
    void storedNameCannotEscapeTheUploadDirectory() {
        var service = new LicenseService(mock(JdbcTemplate.class), OperatorContext::system, directory.toString());
        assertThatThrownBy(() -> service.downloadAttachmentByStoredName("../private.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
