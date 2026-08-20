package com.hospital.spd.masterdata;

import java.util.List;

public record PendingProductApplicationPage(
        List<PendingProductTypeCount> typeCounts,
        List<PendingProductApplicationRow> rows,
        long total,
        int page,
        int size
) {
}
