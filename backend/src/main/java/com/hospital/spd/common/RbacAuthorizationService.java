package com.hospital.spd.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves every authenticated API request to a persisted RBAC permission code.
 */
@Service
public class RbacAuthorizationService {

    private static final Map<String, String> FEATURE_BY_PREFIX = featurePrefixes();

    private final JdbcTemplate jdbcTemplate;

    public RbacAuthorizationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAllowed(Long userId, List<String> roles, String method, String requestPath) {
        if (roles != null && roles.contains("ROLE_ADMIN")) {
            return true;
        }

        String path = normalizePath(requestPath);
        if ("/auth/me".equals(path)) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String requiredPermission = requiredPermission(method, path);
        if (requiredPermission == null || userId == null) {
            return false;
        }
        return permissionCodes(userId).contains(requiredPermission);
    }

    public List<String> permissionCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT p.perm_code
                  FROM sys_user_role ur
                  JOIN sys_role r ON r.role_id = ur.role_id AND r.deleted = 0 AND r.status = 1
                  JOIN sys_role_perm rp ON rp.role_id = r.role_id
                  JOIN sys_permission p ON p.perm_id = rp.perm_id AND p.deleted = 0
                 WHERE ur.user_id = ?
                 ORDER BY p.perm_code
                """, String.class, userId);
    }

    private static String requiredPermission(String method, String path) {
        String verb = method == null ? "GET" : method.toUpperCase(Locale.ROOT);

        if (path.startsWith("/users")) {
            return userManagementPermission(verb, path);
        }
        if (path.startsWith("/operational-closure")) {
            return operationalClosurePermission(verb, path);
        }
        if (path.startsWith("/receiving-orders")) {
            return receivingOrderPermission(verb, path);
        }
        if (path.startsWith("/features/")) {
            String featureCode = path.substring("/features/".length()).split("/")[0];
            return isRead(verb) ? featureCode : featureCode + ":write";
        }

        String featureCode = FEATURE_BY_PREFIX.entrySet().stream()
                .filter(entry -> path.equals(entry.getKey()) || path.startsWith(entry.getKey() + "/"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (featureCode == null) {
            return null;
        }
        return isRead(verb) ? featureCode : featureCode + ":write";
    }

    private static String operationalClosurePermission(String verb, String path) {
        if (path.startsWith("/operational-closure/requisitions/smart-analysis")
                || path.startsWith("/operational-closure/requisitions/from-smart-analysis")) {
            return "department-requisition:smart-analysis";
        }
        if (path.startsWith("/operational-closure/requisitions/") && path.endsWith("/action")) {
            return "department-requisition:approve";
        }
        if (path.equals("/operational-closure/requisitions")) {
            return isRead(verb) ? "department-requisition:read" : "department-requisition:create";
        }
        if (path.startsWith("/operational-closure/requisitions/")) {
            return "department-requisition:read";
        }
        if (path.startsWith("/operational-closure/picking")) {
            return "department-requisition:pick";
        }
        String featureCode;
        if (path.equals("/operational-closure/overview") || path.equals("/operational-closure/options")) {
            featureCode = "operational-closure";
        } else if (path.startsWith("/operational-closure/lists/")) {
            String type = path.substring("/operational-closure/lists/".length()).split("/")[0];
            if ("requisition".equals(type) || "department-requisition".equals(type)) {
                return "department-requisition:read";
            }
            featureCode = switch (type) {
                case "shortage" -> "shortage-reminder";
                case "delivery" -> "picking-delivery";
                case "consumption" -> "department-consumption";
                case "red-flush" -> "red-flush-management";
                case "settlement", "settlement-reconciliation" -> "settlement-reconciliation";
                case "pda" -> "pda-offline-record";
                case "cold-chain", "risk" -> "cold-chain-monitoring";
                case "high-value" -> "high-value-consumables";
                default -> null;
            };
        } else if (path.startsWith("/operational-closure/shortage/smart-analysis")) {
            featureCode = "replenishment-task";
        } else if (path.startsWith("/operational-closure/shortage")) {
            featureCode = "shortage-reminder";
        } else if (path.startsWith("/operational-closure/requisitions")) {
            featureCode = "department-requisition";
        } else if (path.startsWith("/operational-closure/deliveries") || path.startsWith("/operational-closure/picking")) {
            featureCode = "picking-delivery";
        } else if (path.contains("/reverse")) {
            featureCode = "reverse-consumption";
        } else if (path.startsWith("/operational-closure/consumptions")) {
            featureCode = "department-consumption";
        } else if (path.startsWith("/operational-closure/settlements")) {
            featureCode = "settlement-reconciliation";
        } else if (path.startsWith("/operational-closure/pda")) {
            featureCode = "pda-offline-record";
        } else if (path.startsWith("/operational-closure/cold-chain")) {
            featureCode = "cold-chain-monitoring";
        } else if (path.startsWith("/operational-closure/recalls")) {
            featureCode = "recall-isolation";
        } else if (path.startsWith("/operational-closure/high-value")) {
            featureCode = "high-value-consumables";
        } else {
            featureCode = null;
        }
        return featureCode == null ? null : isRead(verb) ? featureCode : featureCode + ":write";
    }

    private static String receivingOrderPermission(String verb, String path) {
        if (isRead(verb)) return "receiving-order:read";
        if (path.endsWith("/approve")) return "receiving-order:approve";
        if (path.endsWith("/reject")) return "receiving-order:reject";
        if (path.endsWith("/action")) return "receiving-acceptance:write";
        if ("POST".equals(verb) && "/receiving-orders".equals(path)) return "receiving-order:create";
        if ("PUT".equals(verb) && path.matches("/receiving-orders/[^/]+")) return "receiving-order:update";
        return "receiving-order:read";
    }

    private static String userManagementPermission(String verb, String path) {
        if (path.equals("/users/roles") && "PUT".equals(verb)) {
            return "user:assign-role";
        }
        if (path.startsWith("/users/roles")) {
            if (isRead(verb)) return "role-permission";
            if (path.equals("/users/roles/permissions")) return "role:assign-permission";
            if (path.equals("/users/roles/data-permission")) return "role:data-permission";
            if (path.equals("/users/roles/delete")) return "role:delete";
            if (path.matches("/users/roles/\\d+")) return "role:update";
            return "role:create";
        }
        if (path.equals("/users/permissions")) return "role-permission";
        if (path.equals("/users/departments/options")) return "user-management";
        if (isRead(verb)) return "user-management";
        if (path.equals("/users/reset-password")) return "user:reset-password";
        if (path.equals("/users/delete")) return "user:delete";
        if (path.equals("/users/roles")) return "user:assign-role";
        if (path.matches("/users/\\d+")) return "user:update";
        return "user:create";
    }

    private static boolean isRead(String method) {
        return "GET".equals(method) || "HEAD".equals(method);
    }

    private static String normalizePath(String requestPath) {
        String path = requestPath == null || requestPath.isBlank() ? "/" : requestPath;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) path = path.substring(0, queryIndex);
        return path.startsWith("/api/") ? path.substring(4) : path;
    }

    private static Map<String, String> featurePrefixes() {
        Map<String, String> prefixes = new LinkedHashMap<>();
        prefixes.put("/master-data/department-warehouse-catalogs", "department-warehouse-catalog");
        prefixes.put("/master-data/hospital-products", "hospital-product-catalog");
        prefixes.put("/master-data/manufacturers", "manufacturer-management");
        prefixes.put("/master-data/departments", "department-management");
        prefixes.put("/master-data/warehouses", "warehouse-location-management");
        prefixes.put("/master-data/suppliers", "supplier-management");
        prefixes.put("/master-data/campuses", "campus-management");
        prefixes.put("/pending-product-applications", "pending-product-catalog");
        prefixes.put("/purchase-orders", "purchase-management");
        prefixes.put("/receiving-orders", "receiving-acceptance");
        prefixes.put("/inventory/batch-price-adjustments", "batch-price-adjustment");
        prefixes.put("/inventory/stocktaking", "stocktaking-management");
        prefixes.put("/inventory/events", "inventory-events");
        prefixes.put("/inventory", "inventory-management");
        prefixes.put("/quota-packages/templates", "quota-template-maintenance");
        prefixes.put("/quota-packages/safety", "quota-safety-stock");
        prefixes.put("/quota-packages/packing-tasks", "packing-task-confirmation");
        prefixes.put("/quota-packages/labels", "quota-label-unpack");
        prefixes.put("/quota-packages/events", "quota-package-events");
        prefixes.put("/quota-packages", "quota-package-template");
        prefixes.put("/udi-traceability", "udi-traceability");
        prefixes.put("/invoices", "invoice-management");
        prefixes.put("/licenses", "license-management");
        prefixes.put("/print-templates", "print-template-settings");
        prefixes.put("/operational-closure", "operational-closure");
        prefixes.put("/approval-flows", "approval-flow-settings");
        prefixes.put("/system/field-options", "field-option-management");
        prefixes.put("/system-config", "system-config");
        prefixes.put("/config-hit-explanation", "config-hit-explanation");
        prefixes.put("/report-center/supplier-delivery-ledger", "supplier-delivery-ledger-report");
        prefixes.put("/report-center/centralized-procurement-progress", "centralized-procurement-progress-report");
        prefixes.put("/report-center/inventory-movement-summary", "inventory-movement-summary-report");
        prefixes.put("/report-center/inventory-products", "inventory-product-detail-report");
        prefixes.put("/dashboard/products", "inventory-product-detail-report");
        prefixes.put("/report-center", "spd-his-reconciliation-report");
        prefixes.put("/operation-cockpit", "operation-cockpit");
        prefixes.put("/dashboard", "dashboard");
        prefixes.put("/modules", "dashboard");
        // Prefixes are deliberately ordered from most specific to least specific.
        // Map.copyOf does not guarantee iteration order, which could make a broad
        // prefix such as /inventory shadow /inventory/batch-price-adjustments.
        return java.util.Collections.unmodifiableMap(prefixes);
    }
}
