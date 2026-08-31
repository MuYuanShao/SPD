package com.hospital.spd.masterdata;

import java.util.List;
import java.util.Map;

public record PendingProductApplicationPage(
        List<PendingProductTypeCount> typeCounts,
        List<PendingProductApplicationRow> rows,
        long total,
        int page,
        int size,
        Map<String, Object> summary
) {
    public PendingProductApplicationPage(List<PendingProductTypeCount> typeCounts,
                                         List<PendingProductApplicationRow> rows,
                                         long total, int page, int size) {
        this(typeCounts, rows, total, page, size, Map.of("typeCounts", typeCounts));
    }
}
