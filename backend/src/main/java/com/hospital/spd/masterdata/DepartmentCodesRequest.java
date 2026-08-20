package com.hospital.spd.masterdata;

import java.util.List;

public record DepartmentCodesRequest(
        List<String> deptCodes
) {
}
