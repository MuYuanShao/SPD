package com.hospital.spd.licenses;

import com.hospital.spd.common.OperatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LicenseServiceTest {
    @TempDir Path directory;

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
