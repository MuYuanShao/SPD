package com.hospital.spd.common;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RbacAuthorizationServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final RbacAuthorizationService service = new RbacAuthorizationService(jdbcTemplate);

    @Test
    void user_without_role_permissions_cannot_access_user_management() {
        stubPermissions(8L, List.of());

        assertThat(service.isAllowed(8L, List.of("ROLE_USER"), "GET", "/api/users")).isFalse();
    }

    @Test
    void menu_permission_allows_read_but_not_create_without_button_permission() {
        stubPermissions(9L, List.of("user-management"));

        assertThat(service.isAllowed(9L, List.of("ROLE_OPERATOR"), "GET", "/api/users")).isTrue();
        assertThat(service.isAllowed(9L, List.of("ROLE_OPERATOR"), "POST", "/api/users")).isFalse();
    }

    @Test
    void matching_button_permission_allows_create() {
        stubPermissions(9L, List.of("user-management", "user:create"));

        assertThat(service.isAllowed(9L, List.of("ROLE_OPERATOR"), "POST", "/api/users")).isTrue();
    }
    @Test
    void assigning_roles_to_a_user_requires_user_assign_role_permission() {
        stubPermissions(9L, List.of("role:create"));
        assertThat(service.isAllowed(9L, List.of("ROLE_OPERATOR"), "PUT", "/api/users/roles")).isFalse();

        stubPermissions(10L, List.of("user:assign-role"));
        assertThat(service.isAllowed(10L, List.of("ROLE_OPERATOR"), "PUT", "/api/users/roles")).isTrue();
    }


    @Test
    void administrator_bypasses_permission_lookup() {
        assertThat(service.isAllowed(1L, List.of("ROLE_ADMIN"), "DELETE", "/api/inventory/events/1")).isTrue();
    }

    @Test
    void current_user_endpoint_remains_available_to_every_authenticated_user() {
        assertThat(service.isAllowed(8L, List.of("ROLE_USER"), "GET", "/api/auth/me")).isTrue();
    }

    @Test
    void specific_inventory_feature_is_not_shadowed_by_generic_inventory_prefix() {
        stubPermissions(11L, List.of("batch-price-adjustment"));

        assertThat(service.isAllowed(
                11L,
                List.of("ROLE_OPERATOR"),
                "GET",
                "/api/inventory/batch-price-adjustments"
        )).isTrue();
    }

    @Test
    void operationalClosureCommandsRequireSpecificFeaturePermissions() {
        stubPermissions(12L, List.of("department-consumption:write"));
        assertThat(service.isAllowed(12L, List.of("ROLE_DEPT_USER"), "POST", "/api/operational-closure/consumptions")).isTrue();
        assertThat(service.isAllowed(12L, List.of("ROLE_DEPT_USER"), "POST", "/api/operational-closure/deliveries")).isFalse();
        assertThat(service.isAllowed(12L, List.of("ROLE_DEPT_USER"), "PUT", "/api/operational-closure/settlements/JS001/confirm")).isFalse();
    }

    @Test
    void operationalClosureReadsRequireRequestedListPermission() {
        stubPermissions(13L, List.of("department-requisition:read"));
        assertThat(service.isAllowed(13L, List.of("ROLE_DEPT_USER"), "GET", "/api/operational-closure/lists/department-requisition")).isTrue();
        assertThat(service.isAllowed(13L, List.of("ROLE_DEPT_USER"), "GET", "/api/operational-closure/lists/settlement-reconciliation")).isFalse();
    }

    @Test
    void departmentRequisitionRoutesRequireTheirFineGrainedPermission() {
        stubPermissions(14L, List.of("department-requisition:read", "department-requisition:create"));
        assertThat(service.isAllowed(14L, List.of("ROLE_DEPT_USER"), "GET",
                "/api/operational-closure/requisitions/options")).isTrue();
        assertThat(service.isAllowed(14L, List.of("ROLE_DEPT_USER"), "POST",
                "/api/operational-closure/requisitions")).isTrue();
        assertThat(service.isAllowed(14L, List.of("ROLE_DEPT_USER"), "POST",
                "/api/operational-closure/requisitions/smart-analysis")).isFalse();
        assertThat(service.isAllowed(14L, List.of("ROLE_DEPT_USER"), "POST",
                "/api/operational-closure/picking/confirm-high-value")).isFalse();

        stubPermissions(15L, List.of("department-requisition:smart-analysis", "department-requisition:pick"));
        assertThat(service.isAllowed(15L, List.of("ROLE_OPERATOR"), "POST",
                "/api/operational-closure/requisitions/from-smart-analysis")).isTrue();
        assertThat(service.isAllowed(15L, List.of("ROLE_OPERATOR"), "GET",
                "/api/operational-closure/picking/unique-codes")).isTrue();
    }

    private void stubPermissions(Long userId, List<String> permissions) {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(userId))).thenReturn(permissions);
    }
}
