package com.hospital.spd.masterdata;

import java.util.List;
import java.util.Map;

public record MasterDataPage(
        String title,
        String description,
        List<String> columns,
        List<Map<String, Object>> rows,
        long total,
        int page,
        int size,
        Map<String, Object> summary
) {
    public MasterDataPage(String title, String description, List<String> columns,
                          List<Map<String, Object>> rows, long total, int page, int size) {
        this(title, description, columns, rows, total, page, size,
                Map.of("title", title, "description", description, "columns", columns));
    }

    /** 向后兼容——无分页信息的构造器，设 total=rows.size(), page=1, size=rows.size() */
    public MasterDataPage(String title, String description, List<String> columns, List<Map<String, Object>> rows) {
        this(title, description, columns, rows, rows.size(), 1, rows.size(),
                Map.of("title", title, "description", description, "columns", columns));
    }
}
