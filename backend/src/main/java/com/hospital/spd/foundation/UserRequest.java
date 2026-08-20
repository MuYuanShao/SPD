package com.hospital.spd.foundation;

import java.util.List;

public record UserRequest(
        String username,
        String password,
        String realName,
        String phone,
        String email,
        Integer gender,
        Long deptId,
        Integer status,
        List<Long> roleIds
) {
}
