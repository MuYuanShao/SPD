package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.masterdata.*;
import static com.hospital.spd.common.SqlHelper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maintains department hierarchy and department-to-warehouse relationships used by requisition workflows.
 */
@Service
public class DepartmentService {

    private final JdbcTemplate jdbcTemplate;

    public DepartmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== 公开方法 ====================

    /** 分页查询科室列表 */
    public MasterDataPage departments(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        String fromClause = """
                FROM sys_dept d
                """;
        StringBuilder where = new StringBuilder("""
                WHERE d.deleted = 0
                """);
        appendDepartmentKeyword(where, args, params.get("deptKeyword"));
        appendDepartmentType(where, args, params.get("deptType"));
        appendLike(where, args, "d.finance_dept_name", params.get("financeDeptName"));
        appendLike(where, args, "d.campus_name", params.get("campusName"));
        appendLike(where, args, "d.dept_code", params.get("sourceFlag"));
        appendDepartmentStatus(where, args, params.get("status"));
        appendDepartmentWarehouse(where, args, params.get("warehouseName"));

        Long total = args.isEmpty()
            ? jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class)
            : jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromClause + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT d.dept_code AS code, d.dept_name AS name,
                       COALESCE(d.finance_dept_code, '-') AS financeDeptCode,
                       COALESCE(d.finance_dept_name, '-') AS financeDept,
                       '临床科室' AS deptType,
                       CASE WHEN d.parent_id = 0 THEN '主科室' ELSE '子科室' END AS deptAttribute,
                       CASE d.status WHEN 1 THEN '正常' ELSE '停用' END AS status,
                       COALESCE(d.address, '-') AS address,
                       COALESCE(d.campus_name, '-') AS campus,
                       DATE_FORMAT(d.update_time, '%Y-%m-%d %H:%i') AS updateTime,
                       COALESCE(d.manager_name, '-') AS manager,
                       COALESCE(d.phone, '-') AS phone,
                       d.dept_code AS sourceFlag,
                       COALESCE((
                         SELECT GROUP_CONCAT(w.warehouse_name ORDER BY w.warehouse_name SEPARATOR '、')
                         FROM warehouse w
                         WHERE w.deleted = 0 AND w.dept_id = d.dept_id
                       ), '-') AS relatedWarehouse
                FROM sys_dept d
                """ + where + " ORDER BY d.sort_order, d.dept_id LIMIT ? OFFSET ?",
                queryArgs.toArray());

        return new MasterDataPage(
                "科室管理",
                "医院科室层级、院区、财务科室和负责人维护。",
                List.of("科室编码", "科室名称", "财务科室编码", "财务科室名称", "科室类型", "科室属性",
                        "科室状态", "科室地址", "所属院区", "修改时间", "负责人", "联系电话", "原数据标识", "关联库房"),
                rows,
                total == null ? 0 : total,
                pageReq.page(),
                pageReq.size()
        );
    }

    /** 创建科室 */
    public Map<String, Object> createDepartment(DepartmentUpsertRequest request) {
        validateDepartment(request, true);
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_dept (
                      parent_id, dept_code, dept_name, finance_dept_code, finance_dept_name,
                      campus_name, address, manager_name, phone, sort_order, status
                    ) VALUES (0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    request.deptCode().trim(),
                    request.deptName().trim(),
                    nullIfBlank(request.financeDeptCode()),
                    nullIfBlank(request.financeDeptName()),
                    nullIfBlank(request.campusName()),
                    nullIfBlank(request.address()),
                    nullIfBlank(request.managerName()),
                    nullIfBlank(request.phone()),
                    request.sortOrder() == null ? 0 : request.sortOrder(),
                    request.status() == null ? 1 : request.status()
            );
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("科室编码已存在");
        }
        return Map.of("deptCode", request.deptCode().trim());
    }

    /** 更新科室 */
    public Map<String, Object> updateDepartment(String deptCode, DepartmentUpsertRequest request) {
        validateDepartment(request, false);
        int updatedRows = jdbcTemplate.update("""
                UPDATE sys_dept
                SET dept_name = ?, finance_dept_code = ?, finance_dept_name = ?, campus_name = ?,
                    address = ?, manager_name = ?, phone = ?, sort_order = ?, status = ?
                WHERE dept_code = ? AND deleted = 0
                """,
                request.deptName().trim(),
                nullIfBlank(request.financeDeptCode()),
                nullIfBlank(request.financeDeptName()),
                nullIfBlank(request.campusName()),
                nullIfBlank(request.address()),
                nullIfBlank(request.managerName()),
                nullIfBlank(request.phone()),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.status() == null ? 1 : request.status(),
                deptCode.trim()
        );
        return Map.of("updatedRows", updatedRows, "deptCode", deptCode.trim());
    }

    /** 删除科室 */
    public Map<String, Object> deleteDepartments(DepartmentCodesRequest request) {
        if (request.deptCodes() == null || request.deptCodes().isEmpty()) {
            throw new IllegalArgumentException("请选择需要删除的科室");
        }

        String placeholders = String.join(",", request.deptCodes().stream().map(code -> "?").toList());
        int deletedRows = jdbcTemplate.update("""
                UPDATE sys_dept
                SET deleted = 1
                WHERE dept_code IN (%s)
                """.formatted(placeholders), request.deptCodes().stream().map(String::trim).toArray());
        return Map.of("deletedRows", deletedRows);
    }

    /** 导出科室 */
    public List<Map<String, Object>> exportDepartments(Map<String, String> params) {
        return departments(params).rows();
    }

    public List<Map<String, Object>> departmentWarehouses(String deptCode) {
        Long deptId = findDepartmentId(deptCode);
        return jdbcTemplate.queryForList("""
                SELECT w.warehouse_code AS code,
                       w.warehouse_name AS name,
                       w.warehouse_type AS type,
                       w.campus_name AS campus,
                       CASE w.status WHEN 1 THEN '启用' ELSE '停用' END AS status,
                       COALESCE(d.dept_name, '-') AS relatedDepartment,
                       CASE WHEN w.dept_id = ? THEN 1 ELSE 0 END AS selected
                  FROM warehouse w
                  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id AND d.deleted = 0
                 WHERE w.deleted = 0
                 ORDER BY selected DESC, w.warehouse_id DESC
                """, deptId);
    }

    @Transactional
    public Map<String, Object> updateDepartmentWarehouses(String deptCode, DepartmentWarehouseRelationRequest request) {
        Long deptId = findDepartmentId(deptCode);
        List<String> warehouseCodes = request == null || request.warehouseCodes() == null
                ? List.of()
                : request.warehouseCodes().stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        int clearedRows;
        if (warehouseCodes.isEmpty()) {
            clearedRows = jdbcTemplate.update("""
                    UPDATE warehouse
                       SET dept_id = NULL
                     WHERE dept_id = ? AND deleted = 0
                    """, deptId);
            return Map.of("updatedRows", clearedRows, "warehouseCount", 0);
        }

        String placeholders = String.join(",", warehouseCodes.stream().map(code -> "?").toList());
        List<Object> clearArgs = new ArrayList<>();
        clearArgs.add(deptId);
        clearArgs.addAll(warehouseCodes);
        clearedRows = jdbcTemplate.update("""
                UPDATE warehouse
                   SET dept_id = NULL
                 WHERE dept_id = ? AND deleted = 0 AND warehouse_code NOT IN (%s)
                """.formatted(placeholders), clearArgs.toArray());

        List<Object> assignArgs = new ArrayList<>();
        assignArgs.add(deptId);
        assignArgs.addAll(warehouseCodes);
        int assignedRows = jdbcTemplate.update("""
                UPDATE warehouse
                   SET dept_id = ?
                 WHERE deleted = 0 AND warehouse_code IN (%s)
                """.formatted(placeholders), assignArgs.toArray());

        return Map.of("updatedRows", clearedRows + assignedRows, "warehouseCount", warehouseCodes.size());
    }

    /** 导入科室 */
    public Map<String, Object> importDepartments(java.io.BufferedReader reader) throws Exception {
        int importedRows = 0;
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
            String[] cells = line.split(",", -1);
            if (cells.length < 2 || isBlank(cells[0]) || isBlank(cells[1])) {
                continue;
            }
            jdbcTemplate.update("""
                    INSERT INTO sys_dept (
                      parent_id, dept_code, dept_name, finance_dept_code, finance_dept_name,
                      campus_name, address, manager_name, phone, sort_order, status
                    ) VALUES (0, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1)
                    ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name),
                      finance_dept_code = VALUES(finance_dept_code),
                      finance_dept_name = VALUES(finance_dept_name),
                      campus_name = VALUES(campus_name), address = VALUES(address),
                      manager_name = VALUES(manager_name), phone = VALUES(phone), deleted = 0
                    """,
                    cells[0].trim(), cells[1].trim(), nullIfBlank(defaultText(cells, 2, "")),
                    nullIfBlank(defaultText(cells, 3, "")), nullIfBlank(defaultText(cells, 4, "")),
                    nullIfBlank(defaultText(cells, 5, "")), nullIfBlank(defaultText(cells, 6, "")),
                    nullIfBlank(defaultText(cells, 7, ""))
            );
            importedRows++;
        }
        return Map.of("importedRows", importedRows);
    }

    // ==================== 私有辅助方法 ====================

    private static void validateDepartment(DepartmentUpsertRequest request, boolean requireCode) {
        if ((requireCode && isBlank(request.deptCode())) || isBlank(request.deptName())) {
            throw new IllegalArgumentException("科室编码、科室名称为必填项");
        }
    }

    private Long findDepartmentId(String deptCode) {
        if (isBlank(deptCode)) {
            throw new IllegalArgumentException("请选择科室");
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT dept_id FROM sys_dept
                 WHERE dept_code = ? AND deleted = 0
                 LIMIT 1
                """, Long.class, deptCode.trim());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("科室不存在或已删除");
        }
        return ids.get(0);
    }

    private static void appendDepartmentKeyword(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND (d.dept_name LIKE ? OR d.dept_code LIKE ?)");
        args.add("%" + value.trim() + "%");
        args.add("%" + value.trim() + "%");
    }

    private static void appendDepartmentStatus(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND d.status = ?");
        args.add("正常".equals(value.trim()) || "启用".equals(value.trim()) ? 1 : 0);
    }

    private static void appendDepartmentType(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND '临床科室' LIKE ?");
        args.add("%" + value.trim() + "%");
    }

    private static void appendDepartmentWarehouse(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append("""
                 AND EXISTS (
                   SELECT 1 FROM warehouse w
                   WHERE w.deleted = 0 AND w.dept_id = d.dept_id AND w.warehouse_name LIKE ?
                 )
                """);
        args.add("%" + value.trim() + "%");
    }
}
