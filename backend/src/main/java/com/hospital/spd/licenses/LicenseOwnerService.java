package com.hospital.spd.licenses;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Resolves license subjects from active master data instead of trusting display text. */
@Service
public class LicenseOwnerService {
    private final JdbcTemplate jdbc;
    public LicenseOwnerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> options(String type, String keyword, Map<String, String> params) {
        String[] columns = columns(type);
        PageRequest page = PageRequest.from(params);
        String where = " WHERE deleted = 0 AND status = 1";
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where += " AND (" + columns[1] + " LIKE ? OR " + columns[2] + " LIKE ?)";
            args.add("%" + keyword.trim() + "%"); args.add("%" + keyword.trim() + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + type + where, Long.class, args.toArray());
        args.add(page.size()); args.add(page.offset());
        var rows = jdbc.queryForList("SELECT " + columns[0] + " AS id, " + columns[1] + " AS code, "
                + columns[2] + " AS name FROM " + type + where + " ORDER BY " + columns[1] + " LIMIT ? OFFSET ?", args.toArray());
        return PageResponse.of(rows, total == null ? 0 : total, page);
    }

    public Owner resolve(String licenseType, LicenseUpsertRequest request, Map<String, Object> previous) {
        String type = "contract".equals(licenseType) ? request.ownerType() : licenseType;
        if (type == null || type.isBlank()) throw new IllegalArgumentException("请选择证照所属主体类型");
        String[] cols = columns(type);
        if (!"contract".equals(licenseType) && request.ownerType() != null && !request.ownerType().isBlank()
                && !type.equals(request.ownerType())) throw new IllegalArgumentException("证照类型与主体类型不一致");
        Long id = request.ownerId();
        String code = request.ownerCode() == null ? "" : request.ownerCode().trim();
        String previousType = previous == null ? null : (String) previous.get("ownerType");
        if (previousType == null || previousType.isBlank()) previousType = licenseType;
        if (id == null && previous != null && type.equals(previousType)
                && (code.isBlank() || code.equals(previous.get("ownerCode"))) && previous.get("ownerId") instanceof Number n) {
            id = n.longValue();
        }
        if (id == null && code.isBlank()) throw new IllegalArgumentException("请搜索并选择有效的证照所属主体");
        var rows = jdbc.queryForList("SELECT " + cols[0] + " AS id, " + cols[1] + " AS code, " + cols[2]
                + " AS name FROM " + type + " WHERE deleted = 0 AND status = 1 AND "
                + (id != null ? cols[0] : cols[1]) + " = ?", id != null ? id : code);
        if (rows.size() != 1) throw new IllegalArgumentException("证照所属主体不存在、已停用或不唯一，请重新选择");
        var row = rows.get(0);
        if (!code.isBlank() && !code.equals(row.get("code"))) throw new IllegalArgumentException("主体ID与编码不一致，请重新选择");
        return new Owner(type, ((Number) row.get("id")).longValue(), String.valueOf(row.get("code")), String.valueOf(row.get("name")));
    }

    private String[] columns(String type) {
        return switch (String.valueOf(type)) {
            case "product" -> new String[]{"product_id", "product_code", "product_name"};
            case "supplier" -> new String[]{"supplier_id", "supplier_code", "supplier_name"};
            case "manufacturer" -> new String[]{"manufacturer_id", "manufacturer_code", "manufacturer_name"};
            default -> throw new IllegalArgumentException("主体类型必须为商品、供应商或厂家");
        };
    }
    public record Owner(String type, Long id, String code, String name) {}
}
