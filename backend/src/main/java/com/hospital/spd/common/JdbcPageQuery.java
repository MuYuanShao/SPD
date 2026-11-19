package com.hospital.spd.common;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Executes a page and its count against the same filtered JDBC query. */
public final class JdbcPageQuery {
    private JdbcPageQuery() {}

    public static PageResponse<Map<String, Object>> fetch(JdbcTemplate jdbc, PageRequest page,
            String select, String from, String orderBy, List<Object> args) {
        if (page == null) {
            var rows = jdbc.queryForList(select + from + orderBy, args.toArray());
            return new PageResponse<>(rows, rows.size(), 1, rows.size(), null);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        long offset = (page.page() - 1L) * page.size();
        if (offset >= total) {
            return new PageResponse<>(List.of(), total, page.page(), page.size(), null);
        }
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(page.size());
        pageArgs.add(offset);
        var rows = jdbc.queryForList(select + from + orderBy + " LIMIT ? OFFSET ?", pageArgs.toArray());
        return new PageResponse<>(rows, total, page.page(), page.size(), null);
    }
}
