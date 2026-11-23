package com.hospital.spd.system.service;

import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.common.service.AuditLogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Publishes system notices and tracks acknowledgments per user. */
@Service
public class SystemNoticeService {
    private static final String NOTICE_PROJECTION = """
            SELECT CAST(n.notice_id AS CHAR) AS id, n.title, n.content,
                   DATE_FORMAT(n.published_at, '%Y-%m-%d %H:%i') AS date
              FROM system_notice n
            """;
    public record Notice(String id, String title, String date, String content) {}
    private final JdbcTemplate jdbc;
    private final OperatorContextProvider operators;
    private final AuditLogService audit;

    public SystemNoticeService(JdbcTemplate jdbc, OperatorContextProvider operators, AuditLogService audit) {
        this.jdbc = jdbc;
        this.operators = operators;
        this.audit = audit;
    }

    @Transactional
    public void publish(String title, String content) {
        String normalizedTitle = title == null ? "" : title.strip();
        String normalizedContent = content == null ? "" : content.strip();
        if (normalizedTitle.isBlank() || normalizedTitle.length() > 120
                || normalizedContent.isBlank() || normalizedContent.length() > 5000) {
            throw new IllegalArgumentException("请输入公告标题和正文，标题最多120字，正文最多5000字");
        }
        jdbc.update("""
                INSERT INTO system_notice (title, content, published_by) VALUES (?, ?, ?)
                """, normalizedTitle, normalizedContent, operators.current().userId());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        audit.record("system_notice", "publish", id, String.valueOf(id), normalizedTitle);
    }

    public List<Notice> recent() {
        return queryNotices(" ORDER BY n.notice_id DESC LIMIT 20");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Notice claimForLogin(UUID loginId) {
        Long userId = operators.current().userId();
        // Serialize publication delivery to concurrent sessions of the same account.
        jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_id = ? FOR UPDATE", Long.class, userId);
        String key = loginId.toString();
        List<Notice> pending = claimedNotices(userId, key);
        if (!pending.isEmpty()) return pending.get(0);

        jdbc.update("""
                INSERT INTO system_notice_delivery (user_id, notice_id, login_id)
                SELECT ?, n.notice_id, ?
                  FROM system_notice n
                  LEFT JOIN system_notice_read r ON r.notice_id = n.notice_id AND r.user_id = ?
                  LEFT JOIN system_notice_delivery d ON d.notice_id = n.notice_id AND d.user_id = ?
                 WHERE r.notice_id IS NULL AND d.notice_id IS NULL
                 ORDER BY n.notice_id DESC LIMIT 1
                """, userId, key, userId, userId);
        return claimedNotices(userId, key).stream().findFirst().orElse(null);
    }

    private List<Notice> claimedNotices(Long userId, String key) {
        return queryNotices("""
                  JOIN system_notice_delivery d ON d.notice_id = n.notice_id
                       AND d.user_id = ? AND d.login_id = ?
                  LEFT JOIN system_notice_read r ON r.notice_id = n.notice_id AND r.user_id = d.user_id
                 WHERE r.notice_id IS NULL
                 ORDER BY n.notice_id DESC
                """, userId, key);
    }

    private List<Notice> queryNotices(String clauses, Object... args) {
        return jdbc.query(NOTICE_PROJECTION + clauses,
                (rs, row) -> new Notice(rs.getString("id"), rs.getString("title"),
                        rs.getString("date"), rs.getString("content")), args);
    }

    public void acknowledge(long noticeId) {
        jdbc.update("""
                INSERT INTO system_notice_read (user_id, notice_id)
                SELECT ?, n.notice_id FROM system_notice n WHERE n.notice_id = ?
                ON DUPLICATE KEY UPDATE read_at = system_notice_read.read_at
                """, operators.current().userId(), noticeId);
    }
}
