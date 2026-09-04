package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.masterdata.*;
import static com.hospital.spd.common.SqlHelper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Maintains warehouse and location master data used by receiving, inventory, packaging, and department supply flows.
 */
@Service
public class WarehouseService {

    private final JdbcTemplate jdbcTemplate;
    private final WarehouseDepartmentPolicy warehouseDepartmentPolicy;
    private final WarehouseCatalogBindingService catalogBindingService;
    private final MasterDataReferenceGuard referenceGuard;

    public WarehouseService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new WarehouseDepartmentPolicy(jdbcTemplate), new WarehouseCatalogBindingService(jdbcTemplate));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WarehouseService(JdbcTemplate jdbcTemplate, WarehouseDepartmentPolicy warehouseDepartmentPolicy,
                            WarehouseCatalogBindingService catalogBindingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.warehouseDepartmentPolicy = warehouseDepartmentPolicy;
        this.catalogBindingService = catalogBindingService;
        this.referenceGuard = new MasterDataReferenceGuard(jdbcTemplate);
    }
    // ==================== 公开方法 ====================

    /** 分页查询库房列表 */
    public MasterDataPage warehouses(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        String fromClause = """
                FROM warehouse w
                LEFT JOIN sys_dept d ON w.dept_id = d.dept_id
                """;
        StringBuilder where = new StringBuilder("""
                WHERE w.deleted = 0
                """);
        appendWarehouseKeyword(where, args, params.get("warehouseKeyword"));
        appendLike(where, args, "w.warehouse_type", params.get("warehouseType"));
        appendLike(where, args, "w.campus_name", params.get("campusName"));
        appendLike(where, args, "d.dept_name", params.get("deptName"));
        appendWarehouseStatus(where, args, params.get("status"));

        Long total = args.isEmpty()
            ? jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class)
            : jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT w.warehouse_code AS code, w.warehouse_name AS name, w.warehouse_type AS type,
                       w.campus_name AS campus, COALESCE(d.dept_name, '-') AS dept,
                       COALESCE(d.dept_code, '') AS deptCode,
                       CASE WHEN (w.warehouse_type LIKE '%二级%' OR w.warehouse_type LIKE '%三级%'
                                      OR w.warehouse_type LIKE '%科室库%')
                                  AND (d.dept_id IS NULL OR d.deleted = 1 OR d.status <> 1)
                            THEN TRUE ELSE FALSE END AS needsDepartmentFix,
                       CASE w.participate_stats WHEN 1 THEN '参与' ELSE '不参与' END AS participateStats,
                       CASE w.receiving_enabled WHEN 1 THEN TRUE ELSE FALSE END AS receivingEnabled,
                       COALESCE(JSON_UNQUOTE(w.stats_categories), '-') AS statsCategories,
                       CASE w.status WHEN 1 THEN '启用' ELSE '停用' END AS status,
                       DATE_FORMAT(w.update_time, '%Y-%m-%d %H:%i') AS updateTime,
                       COALESCE((
                         SELECT COUNT(1)
                         FROM warehouse_location wl
                         WHERE wl.deleted = 0 AND wl.warehouse_id = w.warehouse_id
                       ), 0) AS locationCount
                FROM warehouse w
                LEFT JOIN sys_dept d ON w.dept_id = d.dept_id
                """ + where + " ORDER BY w.warehouse_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());

        return new MasterDataPage(
                "库房 / 货位管理",
                "中心库、科室二级库、虚拟库和货位维护。",
                List.of("库房编码", "库房名称", "库房类型", "所属院区", "关联科室", "统计", "允许收货", "统计分类",
                        "状态", "修改时间", "货位数量"),
                rows,
                total == null ? 0 : total,
                pageReq.page(),
                pageReq.size()
        );
    }

    public List<Map<String, Object>> warehouseProductOptions() {
        return jdbcTemplate.queryForList("""
                SELECT product_code AS productCode,
                       product_name AS productName,
                       COALESCE(spec_model, '') AS specModel,
                       COALESCE(unit, '') AS unit
                  FROM product
                 WHERE deleted = 0 AND status = 1
                 ORDER BY product_code
                """);
    }

    public List<Map<String, Object>> warehouseProducts(String warehouseCode) {
        Long warehouseId = findWarehouseId(warehouseCode);
        return jdbcTemplate.queryForList("""
                SELECT p.product_code AS productCode,
                       p.product_name AS productName,
                       COALESCE(p.spec_model, '') AS specModel,
                       COALESCE(p.unit, '') AS unit
                  FROM warehouse_product_binding b
                  JOIN product p ON p.product_id = b.product_id AND p.deleted = 0
                 WHERE b.warehouse_id = ? AND b.deleted = 0 AND b.status = 1
                 ORDER BY p.product_code
                """, warehouseId);
    }

    /** 创建库房 */
    @Transactional
    public Map<String, Object> createWarehouse(WarehouseUpsertRequest request) {
        validateWarehouse(request, true);
        Long deptId = warehouseDepartmentPolicy.resolveDepartmentId(request);
        jdbcTemplate.update("""
                INSERT INTO warehouse (
                  warehouse_code, warehouse_name, warehouse_type, parent_id, campus_name,
                  dept_id, participate_stats, stats_categories, receiving_enabled, status
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?)
                """,
                request.warehouseCode().trim(),
                request.warehouseName().trim(),
                request.warehouseType().trim(),
                request.campusName().trim(),
                deptId,
                Boolean.FALSE.equals(request.participateStats()) ? 0 : 1,
                jsonText(request.statsCategories()),
                Boolean.TRUE.equals(request.receivingEnabled()) ? 1 : 0,
                request.status() == null ? 1 : request.status()
        );
        List<String> productCodes = normalizeProductCodes(request.productCodes());
        if (!productCodes.isEmpty()) {
            syncWarehouseProducts(findWarehouseId(request.warehouseCode()), deptId, productCodes);
        }
        return Map.of(
                "warehouseCode", request.warehouseCode().trim(),
                "productCount", productCodes.size()
        );
    }

    /** 更新库房 */
    @Transactional
    public Map<String, Object> updateWarehouse(String warehouseCode, WarehouseUpsertRequest request) {
        Long warehouseId = findWarehouseId(warehouseCode);
        validateWarehouse(request, false);
        Long deptId = warehouseDepartmentPolicy.resolveDepartmentId(request);
        int updatedRows = jdbcTemplate.update("""
                UPDATE warehouse
                SET warehouse_name = ?, warehouse_type = ?, campus_name = ?, dept_id = ?,
                    participate_stats = ?, stats_categories = ?, receiving_enabled = COALESCE(?, receiving_enabled), status = ?
                WHERE warehouse_code = ? AND deleted = 0
                """,
                request.warehouseName().trim(),
                request.warehouseType().trim(),
                request.campusName().trim(),
                deptId,
                Boolean.FALSE.equals(request.participateStats()) ? 0 : 1,
                jsonText(request.statsCategories()),
                request.receivingEnabled() == null ? null : Boolean.TRUE.equals(request.receivingEnabled()) ? 1 : 0,
                request.status() == null ? 1 : request.status(),
                warehouseCode.trim()
        );
        List<String> productCodes = normalizeProductCodes(request.productCodes());
        syncWarehouseProducts(warehouseId, deptId, productCodes);
        return Map.of(
                "updatedRows", updatedRows,
                "warehouseCode", warehouseCode.trim(),
                "productCount", productCodes.size()
        );
    }

    /** 删除库房 */
    @Transactional
    public Map<String, Object> deleteWarehouses(WarehouseCodesRequest request) {
        if (request.warehouseCodes() == null || request.warehouseCodes().isEmpty()) {
            throw new IllegalArgumentException("请选择需要删除的库房");
        }

        List<String> normalizedCodes = request.warehouseCodes().stream().map(String::trim).distinct().toList();
        referenceGuard.requireWarehousesDeletable(normalizedCodes);

        String placeholders = String.join(",", request.warehouseCodes().stream().map(code -> "?").toList());
        int deletedRows = jdbcTemplate.update("""
                UPDATE warehouse
                SET deleted = 1
                WHERE warehouse_code IN (%s)
                """.formatted(placeholders), request.warehouseCodes().stream().map(String::trim).toArray());
        return Map.of("deletedRows", deletedRows);
    }

    /** 导出库房 */
    public List<Map<String, Object>> exportWarehouses(Map<String, String> params) {
        return warehouses(params).rows();
    }

    /** 导入库房 */
    @Transactional
    public Map<String, Object> importWarehouses(java.io.BufferedReader reader) throws Exception {
        int importedRows = 0;
        int importedLocations = 0;
        String line;
        boolean header = true;
        while ((line = reader.readLine()) != null) {
            if (header) {
                header = false;
                continue;
            }
            if (line.isBlank()) {
                continue;
            }
            String[] cells = parseCsvLine(line);
            if (cells.length < 4 || isBlank(cells[0]) || isBlank(cells[1])) {
                continue;
            }
            Long deptId = findIdByName("sys_dept", "dept_id", "dept_name", defaultText(cells, 4, ""));
            jdbcTemplate.update("""
                    INSERT INTO warehouse (
                      warehouse_code, warehouse_name, warehouse_type, parent_id, campus_name,
                      dept_id, participate_stats, stats_categories, status
                    ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE warehouse_name = VALUES(warehouse_name),
                      warehouse_type = VALUES(warehouse_type), campus_name = VALUES(campus_name),
                      dept_id = VALUES(dept_id), participate_stats = VALUES(participate_stats),
                      stats_categories = VALUES(stats_categories), deleted = 0
                    """,
                    cells[0].trim(), cells[1].trim(), defaultText(cells, 2, "中心库"),
                    defaultText(cells, 3, "主院区"), deptId,
                    "不参与".equals(defaultText(cells, 5, "参与")) ? 0 : 1,
                    jsonText(defaultText(cells, 6, ""))
            );

            String locationCode = defaultText(cells, 7, "");
            if (!isBlank(locationCode)) {
                String locationType = defaultText(cells, 8, "");
                if (isBlank(locationType)) {
                    throw new IllegalArgumentException("填写货位编码时，货位类型为必填项");
                }
                Long warehouseId = findWarehouseId(cells[0]);
                BigDecimal capacityLimit = parseCapacity(defaultText(cells, 9, ""));
                Long productId = findProductIdByCode(defaultText(cells, 10, ""));
                int locationStatus = parseStatus(defaultText(cells, 11, "启用"));
                jdbcTemplate.update("""
                        INSERT INTO warehouse_location (
                          warehouse_id, location_code, location_type, capacity_limit, product_id, status
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          location_type = VALUES(location_type),
                          capacity_limit = VALUES(capacity_limit),
                          product_id = VALUES(product_id),
                          status = VALUES(status),
                          deleted = 0
                        """,
                        warehouseId, locationCode.trim(), locationType.trim(),
                        capacityLimit, productId, locationStatus);
                importedLocations++;
            }
            importedRows++;
        }
        return Map.of("importedRows", importedRows, "importedLocations", importedLocations);
    }

    public List<Map<String, Object>> warehouseLocations(String warehouseCode) {
        Long warehouseId = findWarehouseId(warehouseCode);
        return jdbcTemplate.queryForList("""
                SELECT wl.location_id AS locationId,
                       wl.location_code AS locationCode,
                       wl.location_type AS locationType,
                       wl.capacity_limit AS capacityLimit,
                       p.product_code AS productCode,
                       p.product_name AS productName,
                       CASE wl.status WHEN 1 THEN '启用' ELSE '停用' END AS statusLabel,
                       wl.status AS status,
                       DATE_FORMAT(wl.update_time, '%Y-%m-%d %H:%i') AS updateTime
                  FROM warehouse_location wl
                  LEFT JOIN product p ON p.product_id = wl.product_id AND p.deleted = 0
                 WHERE wl.warehouse_id = ? AND wl.deleted = 0
                 ORDER BY wl.location_id DESC
                """, warehouseId);
    }

    @Transactional
    public Map<String, Object> createWarehouseLocation(String warehouseCode, WarehouseLocationUpsertRequest request) {
        validateLocation(request);
        Long warehouseId = findWarehouseId(warehouseCode);
        Long productId = findProductIdByCode(request.productCode());
        Long deptId = findWarehouseDepartmentId(warehouseId);
        WarehouseCatalogBindingService.BindingResult binding = productId == null
                ? new WarehouseCatalogBindingService.BindingResult(false, false)
                : catalogBindingService.ensureProductBinding(warehouseId, deptId, productId);
        jdbcTemplate.update("""
                INSERT INTO warehouse_location (
                  warehouse_id, location_code, location_type, capacity_limit, product_id, status
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  location_type = VALUES(location_type),
                  capacity_limit = VALUES(capacity_limit),
                  product_id = VALUES(product_id),
                  status = VALUES(status),
                  deleted = 0
                """,
                warehouseId,
                request.locationCode().trim(),
                request.locationType().trim(),
                normalizeCapacity(request.capacityLimit()),
                productId,
                request.status() == null ? 1 : request.status()
        );
        return Map.of("locationCode", request.locationCode().trim(),
                "bindingCreated", binding.bindingCreated(), "catalogUpdated", binding.catalogUpdated());
    }

    @Transactional
    public Map<String, Object> updateWarehouseLocation(String warehouseCode, Long locationId, WarehouseLocationUpsertRequest request) {
        validateLocation(request);
        Long warehouseId = findWarehouseId(warehouseCode);
        Long productId = findProductIdByCode(request.productCode());
        Long deptId = findWarehouseDepartmentId(warehouseId);
        WarehouseCatalogBindingService.BindingResult binding = productId == null
                ? new WarehouseCatalogBindingService.BindingResult(false, false)
                : catalogBindingService.ensureProductBinding(warehouseId, deptId, productId);
        int updatedRows = jdbcTemplate.update("""
                UPDATE warehouse_location
                   SET location_code = ?,
                       location_type = ?,
                       capacity_limit = ?,
                       product_id = ?,
                       status = ?
                 WHERE location_id = ? AND warehouse_id = ? AND deleted = 0
                """,
                request.locationCode().trim(),
                request.locationType().trim(),
                normalizeCapacity(request.capacityLimit()),
                productId,
                request.status() == null ? 1 : request.status(),
                locationId,
                warehouseId
        );
        return Map.of("updatedRows", updatedRows, "locationId", locationId,
                "bindingCreated", binding.bindingCreated(), "catalogUpdated", binding.catalogUpdated());
    }

    @Transactional
    public Map<String, Object> deleteWarehouseLocation(String warehouseCode, Long locationId) {
        Long warehouseId = findWarehouseId(warehouseCode);
        int deletedRows = jdbcTemplate.update("""
        referenceGuard.requireLocationDeletable(warehouseId, locationId);
                UPDATE warehouse_location
                   SET deleted = 1
                 WHERE location_id = ? AND warehouse_id = ? AND deleted = 0
                """, locationId, warehouseId);
        return Map.of("deletedRows", deletedRows);
    }

    // ==================== 私有辅助方法 ====================

    private void syncWarehouseProducts(Long warehouseId, Long deptId, List<String> productCodes) {
        jdbcTemplate.update("""
                UPDATE warehouse_product_binding
                   SET deleted = 1
                 WHERE warehouse_id = ? AND deleted = 0
                """, warehouseId);
        if (productCodes.isEmpty()) {
            catalogBindingService.synchronizeDepartmentCatalog(warehouseId, deptId, List.of());
            return;
        }

        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT product_id, product_code
                  FROM product
                 WHERE product_code IN (%s)
                   AND deleted = 0
                   AND status = 1
                """.formatted(placeholders(productCodes.size())), productCodes.toArray());
        Map<String, Long> productIds = new java.util.LinkedHashMap<>();
        for (Map<String, Object> product : products) {
            productIds.put(
                    String.valueOf(product.get("product_code")),
                    ((Number) product.get("product_id")).longValue()
            );
        }
        List<String> missingCodes = productCodes.stream()
                .filter(code -> !productIds.containsKey(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new IllegalArgumentException("以下商品不存在、已停用或已删除：" + String.join("、", missingCodes));
        }

        for (String productCode : productCodes) {
            jdbcTemplate.update("""
                    INSERT INTO warehouse_product_binding (
                      warehouse_id, product_id, status, deleted
                    ) VALUES (?, ?, 1, 0)
                    ON DUPLICATE KEY UPDATE
                      status = 1,
                      deleted = 0
                    """, warehouseId, productIds.get(productCode));
        }
        catalogBindingService.synchronizeDepartmentCatalog(warehouseId, deptId, new ArrayList<>(productIds.values()));
    }

    /**
     * Keeps system-generated department catalog rows aligned with warehouse bindings without changing manual rows.
     */
    private void syncDepartmentWarehouseCatalog(Long warehouseId, Long deptId, List<Long> productIds) {
        if (deptId == null) {
            jdbcTemplate.update("""
                    UPDATE department_warehouse_catalog
                       SET status = 0
                     WHERE warehouse_id = ?
                       AND source_type = 'warehouse_binding'
                       AND deleted = 0
                    """, warehouseId);
            return;
        }

        jdbcTemplate.update("""
                UPDATE department_warehouse_catalog
                   SET status = 0
                 WHERE warehouse_id = ?
                   AND dept_id <> ?
                   AND source_type = 'warehouse_binding'
                   AND deleted = 0
                """, warehouseId, deptId);

        if (productIds.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE department_warehouse_catalog
                       SET status = 0
                     WHERE warehouse_id = ?
                       AND dept_id = ?
                       AND source_type = 'warehouse_binding'
                       AND deleted = 0
                    """, warehouseId, deptId);
            return;
        }

        List<Object> selectionArgs = new ArrayList<>();
        selectionArgs.add(warehouseId);
        selectionArgs.add(deptId);
        selectionArgs.addAll(productIds);
        jdbcTemplate.update("""
                UPDATE department_warehouse_catalog
                   SET status = 0
                 WHERE warehouse_id = ?
                   AND dept_id = ?
                   AND source_type = 'warehouse_binding'
                   AND deleted = 0
                   AND product_id NOT IN (%s)
                """.formatted(placeholders(productIds.size())), selectionArgs.toArray());

        List<Object> activeArgs = new ArrayList<>();
        activeArgs.add(warehouseId);
        activeArgs.add(deptId);
        activeArgs.addAll(productIds);
        jdbcTemplate.update("""
                UPDATE department_warehouse_catalog
                   SET status = 1
                 WHERE warehouse_id = ?
                   AND dept_id = ?
                   AND source_type = 'warehouse_binding'
                   AND deleted = 0
                   AND product_id IN (%s)
                """.formatted(placeholders(productIds.size())), activeArgs.toArray());

        List<Object> insertArgs = new ArrayList<>();
        insertArgs.add(deptId);
        insertArgs.add(warehouseId);
        insertArgs.addAll(productIds);
        jdbcTemplate.update("""
                INSERT IGNORE INTO department_warehouse_catalog (
                  dept_id, warehouse_id, product_id, source_type, status, deleted
                )
                SELECT ?, ?, p.product_id, 'warehouse_binding', 1, 0
                  FROM product p
                 WHERE p.product_id IN (%s) AND p.deleted = 0 AND p.status = 1
                """.formatted(placeholders(productIds.size())), insertArgs.toArray());
    }

    private static List<String> normalizeProductCodes(List<String> productCodes) {
        if (productCodes == null || productCodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String productCode : productCodes) {
            if (productCode != null && !productCode.isBlank()) {
                normalized.add(productCode.trim());
            }
        }
        if (normalized.size() > 500) {
            throw new IllegalArgumentException("单次最多绑定 500 个商品");
        }
        return List.copyOf(normalized);
    }

    private static String[] parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (currentChar == ',' && !quoted) {
                cells.add(stripBom(current.toString().trim()));
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }
        cells.add(stripBom(current.toString().trim()));
        return cells.toArray(String[]::new);
    }

    private static String stripBom(String value) {
        return !value.isEmpty() && value.charAt(0) == 0xFEFF ? value.substring(1) : value;
    }

    private static BigDecimal parseCapacity(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            BigDecimal capacity = new BigDecimal(value.trim());
            if (BigDecimal.ZERO.compareTo(capacity) >= 0) {
                throw new IllegalArgumentException("货位容量上限必须大于 0");
            }
            return capacity;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("货位容量上限必须为有效数字");
        }
    }

    private static int parseStatus(String value) {
        if (isBlank(value)) {
            return 1;
        }
        String normalized = value.trim();
        return "0".equals(normalized) || "停用".equals(normalized) ? 0 : 1;
    }

    private Long findWarehouseId(String warehouseCode) {
        if (isBlank(warehouseCode)) {
            throw new IllegalArgumentException("请指定库房");
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT warehouse_id FROM warehouse
                 WHERE warehouse_code = ? AND deleted = 0
                 LIMIT 1
                """, Long.class, warehouseCode.trim());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("库房不存在或已删除");
        }
        return ids.get(0);
    }

    private Long findProductIdByCode(String productCode) {
        if (isBlank(productCode)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT product_id FROM product
                 WHERE product_code = ? AND deleted = 0 AND status = 1
                 LIMIT 1
                """, Long.class, productCode.trim());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("固定商品编码不存在");
        }
        return ids.get(0);
    }

    private Long findWarehouseDepartmentId(Long warehouseId) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT dept_id FROM warehouse
                 WHERE warehouse_id = ? AND deleted = 0 AND dept_id IS NOT NULL
                """, Long.class, warehouseId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long findIdByName(String table, String idColumn, String nameColumn, String name) {
        if (isBlank(name)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT " + idColumn + " FROM " + table + " WHERE " + nameColumn + " = ? AND deleted = 0 LIMIT 1",
                Long.class,
                name.trim()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static void validateWarehouse(WarehouseUpsertRequest request, boolean requireCode) {
        if ((requireCode && isBlank(request.warehouseCode())) || isBlank(request.warehouseName()) ||
                isBlank(request.warehouseType()) || isBlank(request.campusName())) {
            throw new IllegalArgumentException("库房编码、库房名称、库房类型、所属院区为必填项");
        }
    }

    private static void validateLocation(WarehouseLocationUpsertRequest request) {
        if (request == null || isBlank(request.locationCode()) || isBlank(request.locationType())) {
            throw new IllegalArgumentException("货位编码、货位类型为必填项");
        }
    }

    private static BigDecimal normalizeCapacity(BigDecimal capacityLimit) {
        if (capacityLimit == null || BigDecimal.ZERO.compareTo(capacityLimit) >= 0) {
            return null;
        }
        return capacityLimit;
    }

    private static void appendWarehouseKeyword(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND (w.warehouse_name LIKE ? OR w.warehouse_code LIKE ?)");
        args.add("%" + value.trim() + "%");
        args.add("%" + value.trim() + "%");
    }

    private static void appendWarehouseStatus(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND w.status = ?");
        args.add("启用".equals(value.trim()) || "正常".equals(value.trim()) ? 1 : 0);
    }
}
