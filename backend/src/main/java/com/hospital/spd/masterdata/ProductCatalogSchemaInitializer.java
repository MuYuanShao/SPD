package com.hospital.spd.masterdata;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public ProductCatalogSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing("is_volume_based", "TINYINT NOT NULL DEFAULT 0 COMMENT '是否带量'");
        addColumnIfMissing("is_centralized_procurement", "TINYINT NOT NULL DEFAULT 0 COMMENT '是否集采'");
        addColumnIfMissing("is_domestic", "TINYINT NOT NULL DEFAULT 1 COMMENT '是否国产'");
        addColumnIfMissing("contract_code", "VARCHAR(80) DEFAULT NULL COMMENT '合同编码'");
        addColumnIfMissing("first_category", "VARCHAR(80) DEFAULT NULL COMMENT '一级分类'");
        addColumnIfMissing("second_category", "VARCHAR(80) DEFAULT NULL COMMENT '二级分类'");
        addColumnIfMissing("third_category", "VARCHAR(80) DEFAULT NULL COMMENT '三级分类'");
        addColumnIfMissing("is_chargeable", "TINYINT NOT NULL DEFAULT 1 COMMENT '是否收费'");
        addColumnIfMissing("is_key_monitored", "TINYINT NOT NULL DEFAULT 0 COMMENT '是否重点监控'");
        addColumnIfMissing("tender_sub_code", "VARCHAR(80) DEFAULT NULL COMMENT '招采子编码'");
        addPendingColumnIfMissing("is_volume_based", "TINYINT NOT NULL DEFAULT 0 COMMENT '是否带量'");
        addPendingColumnIfMissing("is_centralized_procurement", "TINYINT NOT NULL DEFAULT 0 COMMENT '是否集采'");
        addPendingColumnIfMissing("is_domestic", "TINYINT NOT NULL DEFAULT 1 COMMENT '是否国产'");
        addPendingColumnIfMissing("contract_code", "VARCHAR(80) DEFAULT NULL COMMENT '合同编码'");
        addPendingColumnIfMissing("first_category", "VARCHAR(80) DEFAULT NULL COMMENT '一级分类'");
        addPendingColumnIfMissing("second_category", "VARCHAR(80) DEFAULT NULL COMMENT '二级分类'");
        addPendingColumnIfMissing("third_category", "VARCHAR(80) DEFAULT NULL COMMENT '三级分类'");
        addPendingColumnIfMissing("is_chargeable", "TINYINT NOT NULL DEFAULT 1 COMMENT '是否收费'");
        addPendingColumnIfMissing("is_key_monitored", "TINYINT NOT NULL DEFAULT 0 COMMENT '是否重点监控'");
        addPendingColumnIfMissing("tender_sub_code", "VARCHAR(80) DEFAULT NULL COMMENT '招采子编码'");
        seedCatalogExtensionFields();
    }

    private void addColumnIfMissing(String columnName, String columnDefinition) {
        addColumnIfMissing("product", columnName, columnDefinition);
    }

    private void addPendingColumnIfMissing(String columnName, String columnDefinition) {
        addColumnIfMissing("pending_product_application", columnName, columnDefinition);
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);

        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + columnDefinition);
        }
    }

    private void seedCatalogExtensionFields() {
        jdbcTemplate.update("""
                UPDATE product
                SET is_volume_based = 1,
                    is_centralized_procurement = 1,
                    is_domestic = 1,
                    contract_code = COALESCE(contract_code, 'HT-2026-GY-001'),
                    first_category = COALESCE(first_category, '医用耗材'),
                    second_category = COALESCE(second_category, '注射穿刺'),
                    third_category = COALESCE(third_category, '注射器'),
                    is_chargeable = 1,
                    tender_sub_code = COALESCE(tender_sub_code, 'ZC-2026-001')
                WHERE product_code = 'PROD-001'
                """);
        jdbcTemplate.update("""
                UPDATE product
                SET is_volume_based = 0,
                    is_centralized_procurement = 1,
                    is_domestic = 1,
                    contract_code = COALESCE(contract_code, 'HT-2026-JZT-002'),
                    first_category = COALESCE(first_category, '医用耗材'),
                    second_category = COALESCE(second_category, '护理耗材'),
                    third_category = COALESCE(third_category, '留置针'),
                    is_chargeable = 1,
                    tender_sub_code = COALESCE(tender_sub_code, 'ZC-2026-002')
                WHERE product_code = 'PROD-002'
                """);
        jdbcTemplate.update("""
                UPDATE product
                SET is_volume_based = 0,
                    is_centralized_procurement = 0,
                    is_domestic = 1,
                    contract_code = COALESCE(contract_code, 'HT-2026-WJ-003'),
                    first_category = COALESCE(first_category, '医用耗材'),
                    second_category = COALESCE(second_category, '敷料'),
                    third_category = COALESCE(third_category, '纱布块'),
                    is_chargeable = 1,
                    tender_sub_code = COALESCE(tender_sub_code, 'ZC-2026-003')
                WHERE product_code = 'PROD-003'
                """);
    }
}
