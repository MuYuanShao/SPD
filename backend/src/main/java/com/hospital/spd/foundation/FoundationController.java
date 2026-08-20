package com.hospital.spd.foundation;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.foundation.service.FoundationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposes user, role, permission, and data-scope management endpoints for the foundation module.
 */
@RestController
@RequestMapping("/users")
public class FoundationController {
    private final FoundationService foundationService;

    public FoundationController(FoundationService foundationService) {
        this.foundationService = foundationService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> users(@RequestParam(required = false) String username,
                                                       @RequestParam(required = false) String realName,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String deptName,
                                                       @RequestParam Map<String, String> params) {
        return ApiResponse.ok(foundationService.users(username, realName, status, deptName, params));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createUser(@RequestBody UserRequest request) {
        return ApiResponse.ok(foundationService.createUser(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable Long userId, @RequestBody UserRequest request) {
        return ApiResponse.ok(foundationService.updateUser(userId, request));
    }

    @PutMapping("/delete")
    public ApiResponse<Map<String, Object>> deleteUsers(@RequestBody UserIdsRequest request) {
        return ApiResponse.ok(foundationService.deleteUsers(request));
    }

    @PutMapping("/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@RequestBody UserIdsRequest request) {
        return ApiResponse.ok(foundationService.resetPassword(request));
    }

    @PutMapping("/roles")
    public ApiResponse<Map<String, Object>> updateUserRoles(@RequestBody UserRoleAssignRequest request) {
        return ApiResponse.ok(foundationService.updateUserRoles(request));
    }

    @GetMapping("/roles")
    public ApiResponse<Map<String, Object>> roles(@RequestParam(required = false) String roleName,
                                                       @RequestParam(required = false) String roleCode,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam Map<String, String> params) {
        return ApiResponse.ok(foundationService.roles(roleName, roleCode, status, params));
    }

    @PostMapping("/roles")
    public ApiResponse<Map<String, Object>> createRole(@RequestBody RoleRequest request) {
        return ApiResponse.ok(foundationService.createRole(request));
    }

    @PutMapping("/roles/{roleId}")
    public ApiResponse<Map<String, Object>> updateRole(@PathVariable Long roleId, @RequestBody RoleRequest request) {
        return ApiResponse.ok(foundationService.updateRole(roleId, request));
    }

    @PutMapping("/roles/delete")
    public ApiResponse<Map<String, Object>> deleteRoles(@RequestBody RoleIdsRequest request) {
        return ApiResponse.ok(foundationService.deleteRoles(request));
    }

    @PutMapping("/roles/permissions")
    public ApiResponse<Map<String, Object>> updateRolePermissions(@RequestBody RolePermissionAssignRequest request) {
        return ApiResponse.ok(foundationService.updateRolePermissions(request));
    }

    @PutMapping("/roles/data-permission")
    public ApiResponse<Map<String, Object>> updateDataPermission(@RequestBody DataPermissionRequest request) {
        return ApiResponse.ok(foundationService.updateDataPermission(request));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> permissions() {
        return ApiResponse.ok(foundationService.permissions());
    }

    @GetMapping("/departments/options")
    public ApiResponse<List<Map<String, Object>>> departmentOptions() {
        return ApiResponse.ok(foundationService.departmentOptions());
    }
}
