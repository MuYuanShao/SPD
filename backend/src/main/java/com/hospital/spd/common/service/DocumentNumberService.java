package com.hospital.spd.common.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Allocates concurrency-safe document numbers shared by business workflow services.
 */
@Service
public class DocumentNumberService {

    private final JdbcTemplate jdbcTemplate;

    public DocumentNumberService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generates a concurrency-safe document number for a named business document policy.
     */
    public String next(DocumentKind kind) {
        return next(kind.prefix(), kind.width(), kind.table(), kind.column());
    }

    /**
     * Generates a daily business document number with the default five digit sequence.
     */
    public String next(String prefix) {
        return next(prefix, 5);
    }

    /**
     * Generates a concurrency-safe daily document number in the form PREFIX + yyyyMMdd + sequence.
     */
    public String next(String prefix, int width) {
        return next(prefix, width, 0L);
    }

    /**
     * Generates a document number while reconciling the allocator with existing business rows.
     */
    public String next(String prefix, int width, String table, String column) {
        String seqKey = prefix + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long existingMax = maxExistingSequence(seqKey, table, column);
        return next(prefix, width, existingMax == null ? 0L : existingMax);
    }

    private String next(String prefix, int width, Long existingMax) {
        String seqKey = prefix + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        long floor = existingMax == null ? 0L : existingMax;
        long seed = floor + 1;
        jdbcTemplate.update("""
                INSERT INTO sys_sequence (seq_key, seq_value)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE seq_value = GREATEST(seq_value, ?) + 1
                """, seqKey, seed, floor);
        Long seq = jdbcTemplate.queryForObject("SELECT seq_value FROM sys_sequence WHERE seq_key = ?", Long.class, seqKey);
        return seqKey + String.format("%0" + width + "d", seq == null ? 1 : seq);
    }

    private Long maxExistingSequence(String seqKey, String table, String column) {
        if (!isSafeIdentifier(table) || !isSafeIdentifier(column)) {
            throw new IllegalArgumentException("Unsafe document number table or column");
        }
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(`" + column + "`, ?) AS UNSIGNED)), 0) " +
                "FROM `" + table + "` WHERE `" + column + "` LIKE ?";
        return jdbcTemplate.queryForObject(sql, Long.class, seqKey.length() + 1, seqKey + "%");
    }

    private static boolean isSafeIdentifier(String identifier) {
        return identifier != null && identifier.matches("[A-Za-z0-9_]+");
    }

    /**
     * Keeps numbering self-contained so each business service can rely on the shared allocator.
     */
    @PostConstruct
    void ensureSequenceTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_sequence (
                  seq_key    VARCHAR(100) NOT NULL,
                  seq_value  BIGINT UNSIGNED NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (seq_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
    }
}
