package com.hospital.spd.integration;

import java.sql.Timestamp;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpSourceTimeTest {
  @Test void supportsBothJdbcDateTimeRepresentations() {
    var time=LocalDateTime.of(2026,10,8,10,30);
    assertEquals("2026-10-08T02:30:00Z",McpSourceTime.instant(time,"Asia/Shanghai"));
    assertEquals(McpSourceTime.instant(time,"Asia/Shanghai"),McpSourceTime.instant(Timestamp.valueOf(time),"Asia/Shanghai"));
    assertEquals(time.toLocalDate(),McpSourceTime.date(time));
    assertEquals(time.toLocalDate(),McpSourceTime.date(java.sql.Date.valueOf(time.toLocalDate())));
  }
}
