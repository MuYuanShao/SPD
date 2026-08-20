package com.hospital.spd.common;

import java.util.Map;

/**
 * 通用分页请求参数——从查询参数中解析 page 和 size，自动计算 OFFSET。
 * 遵循开发规范 3.2 节：?page=1&size=20
 */
public record PageRequest(
    int page,
    int size,
    int offset
) {
    /** 默认每页大小 */
    public static final int DEFAULT_SIZE = 20;
    /** 最大每页大小 */
    public static final int MAX_SIZE = 200;

    /**
     * 从请求参数 Map 中构造分页对象。
     * @param params 请求参数 Map（来自 @RequestParam）
     * @return 分页请求对象
     */
    public static PageRequest from(Map<String, String> params) {
        int page = parseInt(params.get("page"), 1);
        int size = parseInt(params.get("size"), DEFAULT_SIZE);
        if (page < 1) page = 1;
        if (size < 1) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
        int offset = (page - 1) * size;
        return new PageRequest(page, size, offset);
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }
}
