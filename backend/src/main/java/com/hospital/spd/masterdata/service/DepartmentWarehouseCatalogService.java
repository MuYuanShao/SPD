package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.masterdata.DepartmentWarehouseCatalogBatchRequest;
import com.hospital.spd.masterdata.DepartmentWarehouseCatalogIdsRequest;
import com.hospital.spd.masterdata.DepartmentWarehouseCatalogStatusRequest;
import com.hospital.spd.masterdata.DepartmentWarehouseCatalogUpsertRequest;
import com.hospital.spd.masterdata.MasterDataPage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hospital.spd.common.SqlHelper.isBlank;
import static com.hospital.spd.common.SqlHelper.placeholders;

/**
 * Maintains the products that a department may request from each associated warehouse.
 */
@Service
public class DepartmentWarehouseCatalogService {

    private final JdbcTemplate jdbcTemplate;

    public DepartmentWarehouseCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MasterDataPage catalogs(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        String fromClause = """
                FROM department_warehouse_catalog dwc
                JOIN sys_dept d ON d.dept_id = dwc.dept_id AND d.deleted = 0
                JOIN warehouse w ON w.warehouse_id = dwc.warehouse_id AND w.deleted = 0
                JOIN product p ON p.product_id = dwc.product_id AND p.deleted = 0
                LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id AND m.deleted = 0
                """;
        StringBuilder where = new StringBuilder(" WHERE dwc.deleted = 0");
        appendLike(where, args, "d.dept_name", params.get("deptName"));
        appendLike(where, args, "w.warehouse_name", params.get("warehouseName"));
        appendProductKeyword(where, args, params.get("productKeyword"));
        appendCatalogStatus(where, args, params.get("status"));

        Long total = args.isEmpty()
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rawRows = jdbcTemplate.queryForList("""
                SELECT dwc.catalog_id AS code,
                       d.dept_name AS deptName,
                       w.warehouse_name AS warehouseName,
                       p.product_code AS productCode,
                       p.product_name AS productName,
                       COALESCE(p.spec_model, '-') AS specModel,
                       COALESCE(m.manufacturer_name, '-') AS manufacturerName,
                       CASE dwc.source_type WHEN 'manual' THEN '手工维护' ELSE '系统生成' END AS sourceType,
                       CASE dwc.status WHEN 1 THEN '启用' ELSE '停用' END AS status,
                       DATE_FORMAT(dwc.update_time, '%Y-%m-%d %H:%i') AS updateTime
                """ + fromClause + where + " ORDER BY dwc.catalog_id ASC LIMIT ? OFFSET ?",
                queryArgs.toArray());
        List<Map<String, Object>> rows = withSequenceNumbers(rawRows, pageReq.offset());

        return new MasterDataPage(
                "科室库房目录",
                "维护科室关联库房可申领商品范围，科室申领只读取这里配置的目录。",
                List.of("序号", "科室", "库房", "商品编码", "商品名称", "规格型号", "生产厂家", "来源", "状态", "更新时间"),
                rows,
                total == null ? 0 : total,
                pageReq.page(),
                pageReq.size()
        );
    }

    @Transactional
    public Map<String, Object> createCatalog(DepartmentWarehouseCatalogUpsertRequest request) {
        ResolvedCatalog resolved = resolveCatalog(request);
        try {
            jdbcTemplate.update("""
                    INSERT INTO department_warehouse_catalog (
                      dept_id, warehouse_id, product_id, source_type, status, deleted
                    ) VALUES (?, ?, ?, 'manual', ?, 0)
                    """,
                    resolved.deptId(),
                    resolved.warehouseId(),
                    resolved.productId(),
                    normalizeStatus(request.status())
            );
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("科室库房目录已存在");
        }
        return Map.of("catalogId", jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    /**
     * Adds or refreshes many product entries for one department warehouse as one transaction.
     */
    @Transactional
    public Map<String, Object> batchMaintainCatalogs(DepartmentWarehouseCatalogBatchRequest request) {
        List<String> productCodes = normalizedProductCodes(request == null ? null : request.productCodes());
        BatchCatalogTarget target = resolveBatchTarget(request);
        int status = normalizeStatus(request.status());

        List<Object> productArgs = new ArrayList<>();
        productArgs.add(target.warehouseId());
        productArgs.addAll(productCodes);
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT p.product_id, p.product_code
                  FROM product p
                  JOIN warehouse_product_binding b ON b.product_id = p.product_id
                   AND b.warehouse_id = ? AND b.deleted = 0 AND b.status = 1
                 WHERE p.product_code IN (%s) AND p.deleted = 0 AND p.status = 1
                """.formatted(placeholders(productCodes.size())), productArgs.toArray());
        Map<String, Long> productIds = products.stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get("product_code")),
                row -> ((Number) row.get("product_id")).longValue(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
        List<String> missingCodes = productCodes.stream().filter(code -> !productIds.containsKey(code)).toList();
        if (!missingCodes.isEmpty()) {
            throw new IllegalArgumentException("以下商品未绑定当前库房、不存在、已停用或已删除：" + String.join("、", missingCodes));
        }

        List<Object> existingArgs = new ArrayList<>();
        existingArgs.add(target.deptId());
        existingArgs.add(target.warehouseId());
        existingArgs.addAll(productIds.values());
        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList("""
                SELECT catalog_id, product_id, deleted
                  FROM department_warehouse_catalog
                 WHERE dept_id = ? AND warehouse_id = ? AND product_id IN (%s)
                 ORDER BY deleted ASC, catalog_id ASC
                """.formatted(placeholders(productIds.size())), existingArgs.toArray());
        Map<Long, Map<String, Object>> existingByProduct = existingRows.stream().collect(Collectors.toMap(
                row -> ((Number) row.get("product_id")).longValue(),
                Function.identity(),
                (activeFirst, ignored) -> activeFirst,
                LinkedHashMap::new
        ));

        int createdRows = 0;
        int updatedRows = 0;
        int restoredRows = 0;
        for (Long productId : productIds.values()) {
            Map<String, Object> existing = existingByProduct.get(productId);
            if (existing == null) {
                jdbcTemplate.update("""
                        INSERT INTO department_warehouse_catalog (
                          dept_id, warehouse_id, product_id, source_type, status, deleted
                        ) VALUES (?, ?, ?, 'manual', ?, 0)
                        """, target.deptId(), target.warehouseId(), productId, status);
                createdRows++;
                continue;
            }

            long catalogId = ((Number) existing.get("catalog_id")).longValue();
            boolean deleted = ((Number) existing.get("deleted")).intValue() == 1;
            jdbcTemplate.update("""
                    UPDATE department_warehouse_catalog
                       SET source_type = 'manual', status = ?, deleted = 0
                     WHERE catalog_id = ?
                    """, status, catalogId);
            if (deleted) {
                restoredRows++;
            } else {
                updatedRows++;
            }
        }

        return Map.of(
                "createdRows", createdRows,
                "updatedRows", updatedRows,
                "restoredRows", restoredRows,
                "totalRows", productIds.size()
        );
    }

    @Transactional
    public Map<String, Object> updateCatalog(Long catalogId, DepartmentWarehouseCatalogUpsertRequest request) {
        ResolvedCatalog resolved = resolveCatalog(request);
        int updatedRows = jdbcTemplate.update("""
                UPDATE department_warehouse_catalog
                   SET dept_id = ?, warehouse_id = ?, product_id = ?, source_type = 'manual', status = ?
                 WHERE catalog_id = ? AND deleted = 0
                """,
                resolved.deptId(),
                resolved.warehouseId(),
                resolved.productId(),
                normalizeStatus(request.status()),
                catalogId
        );
        return Map.of("catalogId", catalogId, "updatedRows", updatedRows);
    }

    @Transactional
    public Map<String, Object> deleteCatalogs(DepartmentWarehouseCatalogIdsRequest request) {
        List<Long> ids = validIds(request == null ? null : request.catalogIds());
        int deletedRows = jdbcTemplate.update("""
                UPDATE department_warehouse_catalog
                   SET deleted = 1
                 WHERE catalog_id IN (%s) AND deleted = 0
                """.formatted(placeholders(ids.size())), ids.toArray());
        return Map.of("deletedRows", deletedRows);
    }

    @Transactional
    public Map<String, Object> updateCatalogStatus(DepartmentWarehouseCatalogStatusRequest request) {
        List<Long> ids = validIds(request == null ? null : request.catalogIds());
        int status = normalizeStatus(request.status());
        List<Object> args = new ArrayList<>();
        args.add(status);
        args.addAll(ids);
        int updatedRows = jdbcTemplate.update("""
                UPDATE department_warehouse_catalog
                   SET status = ?
                 WHERE catalog_id IN (%s) AND deleted = 0
                """.formatted(placeholders(ids.size())), args.toArray());
        return Map.of("updatedRows", updatedRows);
    }

    private List<Map<String, Object>> withSequenceNumbers(List<Map<String, Object>> rows, int offset) {
        List<Map<String, Object>> sequencedRows = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> source = rows.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", source.get("code"));
            row.put("sequenceNo", offset + index + 1);
            source.forEach((key, value) -> {
                if (!"code".equals(key)) {
                    row.put(key, value);
                }
            });
            sequencedRows.add(row);
        }
        return sequencedRows;
    }

    private ResolvedCatalog resolveCatalog(DepartmentWarehouseCatalogUpsertRequest request) {
        if (request == null || isBlank(request.deptName()) || isBlank(request.warehouseName()) || isBlank(request.productCode())) {
            throw new IllegalArgumentException("科室、库房、商品编码为必填项");
        }
        Long deptId = requiredId("""
                SELECT dept_id FROM sys_dept
                 WHERE dept_name = ? AND deleted = 0
                 LIMIT 1
                """, request.deptName().trim(), "科室不存在或已删除");
        Map<String, Object> warehouse = requiredRow("""
                SELECT warehouse_id, dept_id FROM warehouse
                 WHERE warehouse_name = ? AND deleted = 0
                 LIMIT 1
                """, request.warehouseName().trim(), "库房不存在或已删除");
        Long warehouseId = ((Number) warehouse.get("warehouse_id")).longValue();
        Object warehouseDeptId = warehouse.get("dept_id");
        if (warehouseDeptId == null || ((Number) warehouseDeptId).longValue() != deptId) {
            throw new IllegalArgumentException("库房未关联当前科室，请先在科室管理维护关联库房");
        }
        Long productId = requiredId("""
                SELECT product_id FROM product
                 WHERE product_code = ? AND deleted = 0 AND status = 1
                 LIMIT 1
                """, request.productCode().trim(), "商品不存在、已停用或已删除");
        List<Long> bindingIds = jdbcTemplate.queryForList("""
                SELECT binding_id FROM warehouse_product_binding
                 WHERE warehouse_id = ? AND product_id = ?
                   AND deleted = 0 AND status = 1
                 LIMIT 1
                """, Long.class, warehouseId, productId);
        if (bindingIds.isEmpty()) {
            throw new IllegalArgumentException("商品未绑定当前库房，请先在库房/货位管理维护商品绑定");
        }
        return new ResolvedCatalog(deptId, warehouseId, productId);
    }

    private BatchCatalogTarget resolveBatchTarget(DepartmentWarehouseCatalogBatchRequest request) {
        if (request == null || isBlank(request.deptName()) || isBlank(request.warehouseName())) {
            throw new IllegalArgumentException("科室、库房为必填项");
        }
        Long deptId = requiredId("""
                SELECT dept_id FROM sys_dept
                 WHERE dept_name = ? AND deleted = 0
                 LIMIT 1
                """, request.deptName().trim(), "科室不存在或已删除");
        Map<String, Object> warehouse = requiredRow("""
                SELECT warehouse_id, dept_id FROM warehouse
                 WHERE warehouse_name = ? AND deleted = 0
                 LIMIT 1
                """, request.warehouseName().trim(), "库房不存在或已删除");
        Long warehouseId = ((Number) warehouse.get("warehouse_id")).longValue();
        Object warehouseDeptId = warehouse.get("dept_id");
        if (warehouseDeptId == null || ((Number) warehouseDeptId).longValue() != deptId) {
            throw new IllegalArgumentException("库房未关联当前科室，请先在科室管理维护关联库房");
        }
        return new BatchCatalogTarget(deptId, warehouseId);
    }

    private List<String> normalizedProductCodes(List<String> productCodes) {
        if (productCodes == null) {
            throw new IllegalArgumentException("请选择需要维护的商品");
        }
        List<String> normalized = productCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请选择需要维护的商品");
        }
        if (normalized.size() > 500) {
            throw new IllegalArgumentException("单次最多维护 500 个商品");
        }
        return normalized;
    }

    private Long requiredId(String sql, String value, String message) {
        List<Long> ids = jdbcTemplate.queryForList(sql, Long.class, value);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return ids.get(0);
    }

    private Map<String, Object> requiredRow(String sql, String value, String message) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, value);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return rows.get(0);
    }

    private List<Long> validIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择需要操作的目录");
        }
        List<Long> validIds = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (validIds.isEmpty()) {
            throw new IllegalArgumentException("请选择需要操作的目录");
        }
        return validIds;
    }

    private static int normalizeStatus(Integer status) {
        return status != null && status == 0 ? 0 : 1;
    }

    private static void appendLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND ").append(column).append(" LIKE ?");
        args.add("%" + value.trim() + "%");
    }

    private static void appendProductKeyword(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND (p.product_code LIKE ? OR p.product_name LIKE ?)");
        args.add("%" + value.trim() + "%");
        args.add("%" + value.trim() + "%");
    }

    private static void appendCatalogStatus(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND dwc.status = ?");
        args.add("停用".equals(value.trim()) ? 0 : 1);
    }

    private record ResolvedCatalog(Long deptId, Long warehouseId, Long productId) {
    }

    private record BatchCatalogTarget(Long deptId, Long warehouseId) {
    }
}
