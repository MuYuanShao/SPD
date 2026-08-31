package com.hospital.spd.masterdata.service;

import static com.hospital.spd.common.SqlHelper.isBlank;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Resolves and allocates hospital product codes for new catalog products.
 */
@Service
public class ProductCodeService {

    private static final String PREFIX = "SPD";
    private static final String SEQ_KEY = "spd_product_code";
    private static final int WIDTH = 6;

    private final JdbcTemplate jdbcTemplate;

    public ProductCodeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolves the product code for a new catalog product. The explicitly filled code wins,
     * otherwise the tender sub-code is used as the product code; when both are blank an
     * SPD+000001 style code is allocated from the shared sequence.
     */
    public String resolveNewProductCode(String providedCode, String tenderSubCode) {
        if (!isBlank(providedCode)) {
            return providedCode.trim();
        }
        if (!isBlank(tenderSubCode)) {
            return tenderSubCode.trim();
        }
        return nextGeneratedCode();
    }

    /**
     * Rejects resolved codes that are already used by the hospital catalog or by another
     * pending application, protecting the product unique key when approval syncs the
     * application into the hospital catalog. The excluded application is skipped so a
     * resubmitted application does not conflict with itself.
     */
    public void requireAvailable(String code, String excludedApplicationNo) {
        Integer productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE product_code = ?",
                Integer.class, code);
        if (productCount != null && productCount > 0) {
            throw new IllegalArgumentException("商品编码已存在，请更换商品编码");
        }
        String pendingSql = "SELECT COUNT(*) FROM pending_product_application WHERE product_code = ?";
        Object[] pendingArgs;
        if (isBlank(excludedApplicationNo)) {
            pendingArgs = new Object[]{code};
        } else {
            pendingSql += " AND application_no <> ?";
            pendingArgs = new Object[]{code, excludedApplicationNo.trim()};
        }
        Integer pendingCount = jdbcTemplate.queryForObject(pendingSql, Integer.class, pendingArgs);
        if (pendingCount != null && pendingCount > 0) {
            throw new IllegalArgumentException("商品编码已存在，请更换商品编码");
        }
    }

    private String nextGeneratedCode() {
        jdbcTemplate.update("""
                INSERT INTO sys_sequence (seq_key, seq_value)
                VALUES (?, 1)
                ON DUPLICATE KEY UPDATE seq_value = seq_value + 1
                """, SEQ_KEY);
        Long seq = jdbcTemplate.queryForObject(
                "SELECT seq_value FROM sys_sequence WHERE seq_key = ?", Long.class, SEQ_KEY);
        long value = seq == null ? 1L : seq;
        String candidate = PREFIX + String.format("%0" + WIDTH + "d", value);
        // 跳过已被业务数据占用的候选编码（例如手工录入过 SPD 编码）
        while (codeInUse(candidate)) {
            jdbcTemplate.update("UPDATE sys_sequence SET seq_value = seq_value + 1 WHERE seq_key = ?", SEQ_KEY);
            value++;
            candidate = PREFIX + String.format("%0" + WIDTH + "d", value);
        }
        return candidate;
    }

    private boolean codeInUse(String code) {
        Integer productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE product_code = ?", Integer.class, code);
        if (productCount != null && productCount > 0) {
            return true;
        }
        Integer pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pending_product_application WHERE product_code = ?", Integer.class, code);
        return pendingCount != null && pendingCount > 0;
    }

}
