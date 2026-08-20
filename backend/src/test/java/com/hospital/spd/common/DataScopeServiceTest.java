package com.hospital.spd.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeServiceTest {

    @Test
    void custom_department_scope_uses_persisted_role_department_assignments() {
        OperatorContext operator = new OperatorContext(
                42L, "rbac-user", "127.0.0.1", List.of("ROLE_RBAC_CUSTOM"), 7L,
                OperatorContext.DATA_SCOPE_CUSTOM);
        DataScopeService service = new DataScopeService(() -> operator);
        StringBuilder where = new StringBuilder(" WHERE business.deleted = 0");
        List<Object> args = new ArrayList<>();

        service.appendScope(where, args, "business.dept_id", "business.create_by");

        assertThat(where.toString()).contains("sys_role_dept", "sys_user_role", "business.dept_id");
        assertThat(args).containsExactly(42L);
    }
}
