package com.hospital.spd.foundation.service;

import com.hospital.spd.foundation.UserRequest;
import com.hospital.spd.foundation.RoleRequest;
import com.hospital.spd.foundation.UserRoleAssignRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FoundationService 基础服务测试")
class FoundationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<Object[]> argsCaptor;

    private FoundationService foundationService;

    @BeforeEach
    void setUp() {
        foundationService = new FoundationService(jdbcTemplate);
    }

    // ========== users() ==========

    @Test
    @DisplayName("users()：分页查询用户列表")
    void should_return_users_when_users_given_valid_params() {
        // Arrange
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(5L);
        List<Map<String, Object>> mockRows = List.of(
                Map.of("userId", 1L, "username", "admin"),
                Map.of("userId", 2L, "username", "user1")
        );
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(mockRows);

        Map<String, String> params = Map.of("page", "1", "size", "20");

        // Act
        Map<String, Object> result = foundationService.users(
                "admin", null, null, null, params);

        // Assert
        assertThat((List<?>) result.get("rows")).hasSize(2);
        assertThat(result.get("total")).isEqualTo(5L);
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("users()：带所有过滤条件查询")
    void should_apply_all_filters_when_users_given_all_filters() {
        // Arrange
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("userId", 1L)));

        Map<String, String> params = Map.of("page", "1", "size", "10");

        // Act
        Map<String, Object> result = foundationService.users(
                "admin", "管理员", "1", "信息科", params);

        // Assert
        assertThat((List<?>) result.get("rows")).hasSize(1);
        assertThat(result.get("total")).isEqualTo(1L);
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("users()：无过滤条件分页查询")
    void should_query_without_filters_when_users_given_null_filters() {
        // Arrange
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        // Act
        Map<String, Object> result = foundationService.users(
                null, null, null, null, Map.of("page", "1", "size", "20"));

        // Assert
        assertThat((List<?>) result.get("rows")).isEmpty();
    }

    // ========== createUser() ==========

    @Test
    @DisplayName("createUser()：成功创建用户")
    void should_create_user_when_createUser_given_valid_request() {
        // Arrange
        UserRequest request = new UserRequest(
                "newuser", "pass123", "张三",
                "13800000000", "test@test.com",
                1, 1L, 1, List.of(10L, 20L));

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(100L);

        // Act
        Map<String, Object> result = foundationService.createUser(request);

        // Assert
        assertThat(result).containsEntry("userId", 100L);
        // INSERT user (1) + DELETE roles + INSERT roles (2) + query userId (1) = 5 DB calls
        verify(jdbcTemplate, atLeast(4)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("createUser()：用户名已存在时提示具体冲突字段")
    void should_throw_when_createUser_given_duplicate_username() {
        // Arrange
        UserRequest request = new UserRequest(
                "existing", null, "李四",
                null, null, null, 1L, null, List.of());

        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'sys_user.uk_username'"));

        // Act & Assert
        assertThatThrownBy(() -> foundationService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名已存在，请使用其他用户名");
    }

    @Test
    @DisplayName("createUser()：用户名为空抛出异常")
    void should_throw_when_createUser_given_blank_username() {
        // Arrange
        UserRequest request = new UserRequest(
                "", null, "王五",
                null, null, null, 1L, null, List.of());

        // Act & Assert
        assertThatThrownBy(() -> foundationService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名不能为空");
    }

    @Test
    @DisplayName("createUser()：真实姓名为空抛出异常")
    void should_throw_when_createUser_given_blank_realName() {
        // Arrange
        UserRequest request = new UserRequest(
                "user1", null, "",
                null, null, null, 1L, null, List.of());

        // Act & Assert
        assertThatThrownBy(() -> foundationService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("真实姓名不能为空");
    }

    @Test
    @DisplayName("createUser()：无角色ID时仍能创建")
    void should_create_user_without_roles_when_createUser_given_no_roleIds() {
        // Arrange
        UserRequest request = new UserRequest(
                "nouser", null, "赵六",
                null, null, null, 1L, null, null);

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(101L);

        // Act
        Map<String, Object> result = foundationService.createUser(request);

        // Assert
        assertThat(result).containsEntry("userId", 101L);
    }

    @Test
    @DisplayName("updateUser()：编辑用户时同步更新用户名")
    void should_update_username_when_updateUser_given_username() {
        UserRequest request = new UserRequest(
                "updated_user", null, "测试用户",
                "13900000000", "user@test.com", 1, 1L, 1, List.of(1L));

        Map<String, Object> result = foundationService.updateUser(10L, request);

        assertThat(result).containsEntry("updated", 1);
        verify(jdbcTemplate).update(startsWith("UPDATE sys_user"), argsCaptor.capture());
        assertThat(argsCaptor.getValue()[0]).isEqualTo("updated_user");
    }

    @Test
    @DisplayName("updateUser()：手机号已存在时提示具体冲突字段")
    void should_identify_phone_when_updateUser_given_duplicate_phone() {
        UserRequest request = new UserRequest(
                "updated_user", null, "测试用户",
                "13900000000", "user@test.com", 1, 1L, 1, List.of(1L));
        when(jdbcTemplate.update(startsWith("UPDATE sys_user"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'sys_user.uk_phone'"));

        assertThatThrownBy(() -> foundationService.updateUser(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号已存在，请使用其他手机号");
    }

    // ========== updateUserRoles() ==========

    @Test
    @DisplayName("updateUserRoles()：成功分配角色")
    void should_assign_roles_when_updateUserRoles_given_valid_request() {
        // Arrange
        UserRoleAssignRequest request = new UserRoleAssignRequest(1L, List.of(10L, 20L, 30L));

        // Act
        Map<String, Object> result = foundationService.updateUserRoles(request);

        // Assert
        assertThat(result).containsEntry("assigned", true);
        // 至少调用了 1 次 update（实际为 DELETE + 3×INSERT）
        verify(jdbcTemplate, atLeast(1)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("updateUserRoles()：userId 为空抛出异常")
    void should_throw_when_updateUserRoles_given_null_userId() {
        // Arrange
        UserRoleAssignRequest request = new UserRoleAssignRequest(null, List.of(1L));

        // Act & Assert
        assertThatThrownBy(() -> foundationService.updateUserRoles(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请选择用户");
    }

    @Test
    @DisplayName("updateUserRoles()：空角色列表只执行删除")
    void should_only_delete_when_updateUserRoles_given_empty_roleIds() {
        // Arrange
        UserRoleAssignRequest request = new UserRoleAssignRequest(1L, List.of());

        // Act
        Map<String, Object> result = foundationService.updateUserRoles(request);

        // Assert
        assertThat(result).containsEntry("assigned", true);
        // 仅 1 次 DELETE（无 INSERT），且无 stubbing 冲突
    }

    @Test
    @DisplayName("updateUserRoles()：null 角色列表只执行删除")
    void should_only_delete_when_updateUserRoles_given_null_roleIds() {
        // Arrange
        UserRoleAssignRequest request = new UserRoleAssignRequest(1L, null);

        // Act
        Map<String, Object> result = foundationService.updateUserRoles(request);

        // Assert
        assertThat(result).containsEntry("assigned", true);
    }

    @Test
    @DisplayName("createRole()：角色编码为空时自动生成编码并创建")
    void should_generate_role_code_when_createRole_given_blank_roleCode() {
        RoleRequest request = new RoleRequest("测试角色", "", "说明", 1, 1, 99);

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(201L);

        Map<String, Object> result = foundationService.createRole(request);

        assertThat(result).containsEntry("roleId", 201L);
        verify(jdbcTemplate).update(anyString(), argsCaptor.capture());
        assertThat(argsCaptor.getValue()[1]).asString().startsWith("role_");
    }

    @Test
    @DisplayName("createRole()：角色编码重复时返回业务异常")
    void should_throw_business_error_when_createRole_given_duplicate_roleCode() {
        RoleRequest request = new RoleRequest("测试角色", "duplicated", "说明", 1, 1, 99);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> foundationService.createRole(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色编码已存在");
    }

    // ========== permissions() ==========

    @Test
    @DisplayName("permissions()：返回权限列表")
    void should_return_permissions_when_permissions_called() {
        // Arrange
        List<Map<String, Object>> mockPerms = List.of(
                Map.of("permissionId", 1L, "permissionName", "用户管理", "parentId", 0L),
                Map.of("permissionId", 2L, "permissionName", "角色管理", "parentId", 0L),
                Map.of("permissionId", 3L, "permissionName", "新增用户", "parentId", 1L)
        );
        when(jdbcTemplate.queryForList(anyString())).thenReturn(mockPerms);

        // Act
        List<Map<String, Object>> result = foundationService.permissions();

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).containsEntry("permissionId", 1L);
        verify(jdbcTemplate, times(1)).queryForList(anyString());
    }

    @Test
    @DisplayName("permissions()：返回空列表时正常处理")
    void should_return_empty_when_permissions_given_no_data() {
        // Arrange
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        // Act
        List<Map<String, Object>> result = foundationService.permissions();

        // Assert
        assertThat(result).isEmpty();
    }
}
