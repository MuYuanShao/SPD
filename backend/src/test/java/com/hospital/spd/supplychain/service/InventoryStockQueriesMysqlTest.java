package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Executes inventory read SQL against connection-local temporary MySQL tables; live business tables are untouched. */
@EnabledIfEnvironmentVariable(named = "SPD_INVENTORY_QUERY_MYSQL_TESTS", matches = "true")
class InventoryStockQueriesMysqlTest {
    private Connection connection;
    private JdbcTemplate jdbc;
    private InventoryService service;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv().getOrDefault("SPD_DB_URL",
                "jdbc:mysql://127.0.0.1:3306/ISPD?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        connection = DriverManager.getConnection(url, System.getenv("SPD_DB_USERNAME"), System.getenv("SPD_DB_PASSWORD"));
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        temporary("product", "product_id BIGINT PRIMARY KEY, product_code VARCHAR(64), product_name VARCHAR(100), spec_model VARCHAR(100), registration_no VARCHAR(100), purchase_price DECIMAL(18,4), unit VARCHAR(20), manufacturer_id BIGINT, supplier_id BIGINT, deleted INT");
        temporary("warehouse", "warehouse_id BIGINT PRIMARY KEY, warehouse_name VARCHAR(100), dept_id BIGINT");
        temporary("sys_dept", "dept_id BIGINT PRIMARY KEY, dept_name VARCHAR(100), deleted INT");
        temporary("manufacturer", "manufacturer_id BIGINT PRIMARY KEY, manufacturer_name VARCHAR(100), deleted INT");
        temporary("supplier", "supplier_id BIGINT PRIMARY KEY, supplier_name VARCHAR(100), deleted INT");
        temporary("inventory_balance", "warehouse_id BIGINT, product_id BIGINT, batch_id BIGINT, location_id BIGINT, available_qty DECIMAL(18,4), locked_qty DECIMAL(18,4) DEFAULT 0, isolated_qty DECIMAL(18,4) DEFAULT 0, in_transit_qty DECIMAL(18,4) DEFAULT 0");
        temporary("quota_package_template", "template_id BIGINT PRIMARY KEY, template_code VARCHAR(64), template_name VARCHAR(100)");
        temporary("quota_package_label", "label_id BIGINT PRIMARY KEY, template_id BIGINT, product_id BIGINT, warehouse_id BIGINT, package_quantity DECIMAL(18,4), status VARCHAR(32)");
        temporary("inventory_batch", "batch_id BIGINT PRIMARY KEY, product_id BIGINT, receiving_item_id BIGINT, system_batch_no VARCHAR(64), production_batch_no VARCHAR(64), batch_unit_price DECIMAL(18,4)");
        temporary("inventory_batch_trace_code", "batch_id BIGINT, trace_code_id BIGINT, current_warehouse_id BIGINT, lifecycle_status VARCHAR(32)");
        temporary("udi_trace_code", "trace_code_id BIGINT PRIMARY KEY, unique_code VARCHAR(64), udi_code VARCHAR(100), trace_scope VARCHAR(32), current_status VARCHAR(32)");
        temporary("receiving_order_item", "item_id BIGINT PRIMARY KEY, udi_code VARCHAR(100)");
        jdbc.update("INSERT INTO product VALUES (1,'LOW','Low product','spec','reg',2,'unit',1,1,0),(2,'HIGH','High product','spec','reg',100,'unit',1,1,0)");
        jdbc.update("INSERT INTO warehouse VALUES (1,'CENTER',NULL),(2,'DEPARTMENT',7)");
        jdbc.update("INSERT INTO sys_dept VALUES (7,'Test department',0)");
        jdbc.update("INSERT INTO manufacturer VALUES (1,'Maker',0)");
        jdbc.update("INSERT INTO supplier VALUES (1,'Supplier',0)");
        jdbc.update("INSERT INTO quota_package_template VALUES (1,'PACK-A','Package A'),(2,'PACK-B','Package B')");
        service = new InventoryService(jdbc, mock(SupplyChainSupport.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    void centerPackagesCountEvenWhenDepartmentIsNullAndHaveStablePagination() {
        jdbc.update("INSERT INTO quota_package_label VALUES (1,1,1,1,5,'available'),(2,2,1,1,5,'pending_print')");
        Map<String, Object> first = service.quotaPackageStock(Map.of("warehouseName", "CENTER", "size", "1"));
        Map<String, Object> second = service.quotaPackageStock(Map.of("warehouseName", "CENTER", "size", "1", "page", "2"));
        assertThat(first.get("total")).isEqualTo(2L);
        assertThat(second.get("total")).isEqualTo(2L);
        assertThat(rows(first)).hasSize(1);
        assertThat(rows(second)).hasSize(1);
        assertThat(rows(first).get(0).get("packageCode")).isNotEqualTo(rows(second).get(0).get("packageCode"));
    }

    @Test
    void signedPackageQuantityIsNotCountedAgainAsLooseStockOrMoney() {
        jdbc.update("INSERT INTO quota_package_label VALUES (1,1,1,2,5,'signed'),(2,1,1,2,5,'signed')");
        balance(2, 1, 10, 10);
        Map<String, Object> row = rows(service.quotaPackageStock(Map.of("warehouseName", "DEPARTMENT"))).get(0);
        assertDecimal(row, "packageQty", 10);
        assertDecimal(row, "looseQty", 0);
        assertDecimal(row, "amount", 20);
    }

    @Test
    void signedPackagesInOtherTemplatesAreExcludedFromLooseStock() {
        jdbc.update("INSERT INTO quota_package_label VALUES (1,1,1,2,5,'signed'),(2,2,1,2,5,'signed')");
        balance(2, 1, 10, 13);
        for (Map<String, Object> row : rows(service.quotaPackageStock(Map.of("warehouseName", "DEPARTMENT")))) {
            assertDecimal(row, "packageQty", 5);
            assertDecimal(row, "looseQty", 3);
        }
    }

    @Test
    void packageFiltersMatchCountAndRows() {
        jdbc.update("INSERT INTO quota_package_label VALUES (1,1,1,1,5,'available'),(2,2,2,1,5,'available')");
        Map<String, Object> result = service.quotaPackageStock(Map.of("productCode", "LOW", "packageCode", "PACK-A"));
        assertThat(result.get("total")).isEqualTo(1L);
        assertThat(rows(result)).hasSize(1);
        assertThat(rows(result).get(0).get("packageCode")).isEqualTo("PACK-A");
    }

    @Test
    void balancesIncludeUnreceivedCenterPackagesWithoutDoublingSignedPackages() {
        balance(1, 1, 10, 3);
        balance(2, 1, 10, 10);
        jdbc.update("INSERT INTO quota_package_label VALUES (1,1,1,1,5,'available'),(2,1,1,1,5,'pending_print'),(3,1,1,2,5,'signed'),(4,1,1,2,5,'signed')");
        assertDecimal(rows(service.balances(Map.of("warehouseName", "CENTER"))).get(0), "qty", 13);
        assertDecimal(rows(service.balances(Map.of("warehouseName", "DEPARTMENT"))).get(0), "qty", 10);
        assertDecimal(rows(service.balances(Map.of("warehouseName", "DEPARTMENT"))).get(0), "amount", 20);
    }

    @Test
    void uniqueCodesFollowCurrentWarehouseAndCountOneUnitPerCodeIncludingSignedAndBound() {
        highValueFixture();
        Map<String, Object> center = service.uniqueCodeStock(Map.of("warehouseName", "CENTER"));
        Map<String, Object> department = service.uniqueCodeStock(Map.of("warehouseName", "DEPARTMENT"));
        assertThat(center.get("total")).isEqualTo(1L);
        assertThat(rows(center)).extracting(row -> row.get("uniqueCode")).containsExactly("CENTER-CODE");
        assertThat(department.get("total")).isEqualTo(2L);
        assertThat(rows(department)).extracting(row -> row.get("uniqueCode")).containsExactly("BOUND-CODE", "SIGNED-CODE");
        for (Map<String, Object> row : rows(department)) {
            assertDecimal(row, "qty", 1);
            assertDecimal(row, "amount", 100);
        }
    }

    @Test
    void uniqueCodeFilterUsesIndividualUdiAndExcludesConsumedAndInTransitUnits() {
        highValueFixture();
        Map<String, Object> result = service.uniqueCodeStock(Map.of("udiCode", "UDI-SIGNED"));
        assertThat(result.get("total")).isEqualTo(1L);
        assertThat(rows(result).get(0).get("uniqueCode")).isEqualTo("SIGNED-CODE");
        Map<String, Object> all = service.uniqueCodeStock(Map.of());
        assertThat(rows(all)).extracting(row -> row.get("uniqueCode")).doesNotContain("CONSUMED-CODE", "PICKED-CODE");
    }

    private void highValueFixture() {
        jdbc.update("INSERT INTO inventory_batch VALUES (20,2,1,'BATCH','PRODUCTION',100)");
        jdbc.update("INSERT INTO receiving_order_item VALUES (1,NULL)");
        balance(1, 2, 20, 1);
        balance(2, 2, 20, 2);
        jdbc.update("INSERT INTO udi_trace_code VALUES (1,'CENTER-CODE','UDI-CENTER','high_value','in_stock'),(2,'SIGNED-CODE','UDI-SIGNED','high_value','signed'),(3,'BOUND-CODE','UDI-BOUND','high_value','patient_bound'),(4,'CONSUMED-CODE','UDI-CONSUMED','high_value','consumed'),(5,'PICKED-CODE','UDI-PICKED','high_value','delivery_picked')");
        jdbc.update("INSERT INTO inventory_batch_trace_code VALUES (20,1,1,'in_stock'),(20,2,2,'signed'),(20,3,2,'patient_bound'),(20,4,2,'consumed'),(20,5,1,'delivery_picked')");
    }

    private void temporary(String table, String columns) {
        jdbc.execute("CREATE TEMPORARY TABLE " + table + " (" + columns + ")");
    }

    private void balance(long warehouse, long product, long batch, int quantity) {
        jdbc.update("INSERT INTO inventory_balance (warehouse_id,product_id,batch_id,location_id,available_qty) VALUES (?,?,?,NULL,?)", warehouse, product, batch, quantity);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("rows");
    }

    private static void assertDecimal(Map<String, Object> row, String field, int value) {
        assertThat(new BigDecimal(String.valueOf(row.get(field)))).isEqualByComparingTo(BigDecimal.valueOf(value));
    }
}
