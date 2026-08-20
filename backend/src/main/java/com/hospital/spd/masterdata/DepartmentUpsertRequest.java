package com.hospital.spd.masterdata;

public record DepartmentUpsertRequest(
        String deptCode,
        String deptName,
        String financeDeptCode,
        String financeDeptName,
        String campusName,
        String address,
        String managerName,
        String phone,
        Integer sortOrder,
        Integer status
) {
}
