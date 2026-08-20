package com.hospital.spd.masterdata.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.masterdata.CampusCodesRequest;
import com.hospital.spd.masterdata.CampusUpsertRequest;
import com.hospital.spd.masterdata.MasterDataPage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hospital.spd.common.SqlHelper.appendLike;
import static com.hospital.spd.common.SqlHelper.isBlank;
import static com.hospital.spd.common.SqlHelper.nullIfBlank;

@Service
public class CampusService {

    private final JdbcTemplate jdbcTemplate;

    public CampusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MasterDataPage campuses(Map<String, String> params) {
        PageRequest pageReq = PageRequest.from(params);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE deleted = 0");
        appendLike(where, args, "campus_name", params.get("campusName"));
        appendLike(where, args, "campus_code", params.get("campusCode"));
        appendStatus(where, args, params.get("status"));

        Long total = args.isEmpty()
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus" + where, Long.class)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus" + where, Long.class, args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageReq.size());
        queryArgs.add(pageReq.offset());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT campus_code AS code,
                       campus_name AS name,
                       COALESCE(address, '-') AS address,
                       COALESCE(manager_name, '-') AS manager,
                       COALESCE(phone, '-') AS phone,
                       sort_order AS sortOrder,
                       CASE status WHEN 1 THEN '启用' ELSE '停用' END AS status,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i') AS updateTime
                  FROM campus
                """ + where + " ORDER BY sort_order, campus_id DESC LIMIT ? OFFSET ?",
                queryArgs.toArray());

        return new MasterDataPage(
                "院区管理",
                "维护院区主数据，科室管理和库房 / 货位管理中的所属院区从这里统一选择。",
                List.of("院区编码", "院区名称", "院区地址", "负责人", "联系电话", "排序", "状态", "修改时间"),
                rows,
                total == null ? 0 : total,
                pageReq.page(),
                pageReq.size()
        );
    }

    public List<Map<String, Object>> campusOptions() {
        return jdbcTemplate.queryForList("""
                SELECT campus_code AS code, campus_name AS name
                  FROM campus
                 WHERE deleted = 0 AND status = 1
                 ORDER BY sort_order, campus_id
                """);
    }

    public Map<String, Object> createCampus(CampusUpsertRequest request) {
        validate(request, true);
        try {
            jdbcTemplate.update("""
                    INSERT INTO campus (
                      campus_code, campus_name, address, manager_name, phone, sort_order, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    request.campusCode().trim(),
                    request.campusName().trim(),
                    nullIfBlank(request.address()),
                    nullIfBlank(request.managerName()),
                    nullIfBlank(request.phone()),
                    request.sortOrder() == null ? 0 : request.sortOrder(),
                    request.status() == null ? 1 : request.status()
            );
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("院区编码或院区名称已存在");
        }
        return Map.of("campusCode", request.campusCode().trim());
    }

    public Map<String, Object> updateCampus(String campusCode, CampusUpsertRequest request) {
        validate(request, false);
        int updatedRows = jdbcTemplate.update("""
                UPDATE campus
                   SET campus_name = ?, address = ?, manager_name = ?, phone = ?, sort_order = ?, status = ?
                 WHERE campus_code = ? AND deleted = 0
                """,
                request.campusName().trim(),
                nullIfBlank(request.address()),
                nullIfBlank(request.managerName()),
                nullIfBlank(request.phone()),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.status() == null ? 1 : request.status(),
                campusCode.trim()
        );
        return Map.of("updatedRows", updatedRows, "campusCode", campusCode.trim());
    }

    public Map<String, Object> deleteCampuses(CampusCodesRequest request) {
        if (request.campusCodes() == null || request.campusCodes().isEmpty()) {
            throw new IllegalArgumentException("请选择需要删除的院区");
        }
        String placeholders = String.join(",", request.campusCodes().stream().map(code -> "?").toList());
        int deletedRows = jdbcTemplate.update("""
                UPDATE campus
                   SET deleted = 1
                 WHERE campus_code IN (%s)
                """.formatted(placeholders), request.campusCodes().stream().map(String::trim).toArray());
        return Map.of("deletedRows", deletedRows);
    }

    private static void validate(CampusUpsertRequest request, boolean requireCode) {
        if ((requireCode && isBlank(request.campusCode())) || isBlank(request.campusName())) {
            throw new IllegalArgumentException("院区编码、院区名称为必填项");
        }
    }

    private static void appendStatus(StringBuilder sql, List<Object> args, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND status = ?");
        args.add("启用".equals(value.trim()) ? 1 : 0);
    }
}
