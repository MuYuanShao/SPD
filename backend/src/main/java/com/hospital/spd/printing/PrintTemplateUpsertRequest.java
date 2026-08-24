package com.hospital.spd.printing;

import java.math.BigDecimal;

public record PrintTemplateUpsertRequest(
        String templateName,
        String fieldsJson,
        BigDecimal paperWidthMm,
        BigDecimal paperHeightMm,
        Integer status,
        String remark
) {
}
