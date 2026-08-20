package com.hospital.spd.common;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用分页响应结构——统一所有列表接口的分页返回格式。
 */
public record PageResponse<T>(
    List<T> rows,
    long total,
    int page,
    int size,
    Map<String, Object> summary
) {
    /**
     * 构建分页结果 Map（兼容现有 Controller 返回 ApiResponse<Map<String, Object>> 的模式）
     * @param rows 当前页数据
     * @param total 总记录数
     * @param pageRequest 分页请求
     * @return 分页 Map
     */
    public static Map<String, Object> of(List<?> rows, long total, PageRequest pageRequest) {
        return of(rows, total, pageRequest, null);
    }

    public static Map<String, Object> of(List<?> rows, long total, PageRequest pageRequest, Map<String, Object> summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (summary != null) {
            result.put("summary", summary);
        }
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", pageRequest.page());
        result.put("size", pageRequest.size());
        return result;
    }
}
