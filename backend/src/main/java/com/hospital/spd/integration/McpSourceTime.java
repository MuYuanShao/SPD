package com.hospital.spd.integration;

import java.sql.Timestamp;
import java.time.*;

/** Normalizes both modern and legacy JDBC representations of source business times. */
public final class McpSourceTime {
  private McpSourceTime() {}
  public static LocalDateTime dateTime(Object value) {
    if(value instanceof LocalDateTime time) return time;
    if(value instanceof Timestamp time) return time.toLocalDateTime();
    throw new IllegalArgumentException("来源日期时间类型不支持");
  }
  public static LocalDate date(Object value) {
    if(value instanceof LocalDate date) return date;
    if(value instanceof java.sql.Date date) return date.toLocalDate();
    return dateTime(value).toLocalDate();
  }
  public static String instant(Object value,String zone) {return dateTime(value).atZone(ZoneId.of(zone)).toInstant().toString();}
}
