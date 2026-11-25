package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.supplychain.StocktakingRequest;
import com.hospital.spd.supplychain.PackingTaskRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Reproduces inventory audit findings in a disposable schema, never the hospital database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "SPD_AUDIT_MYSQL_TESTS", matches = "true")
@Transactional
class InventoryAuditMysqlTest {
    static IsolatedMysqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        database = new IsolatedMysqlDatabase(); database.register(registry);
    }
    @AfterAll static void cleanup() { if (database != null) database.close(); }
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryService inventory;
    @Autowired OperationalRiskModule risk;
    @Autowired SupplyChainSupport supplyChainSupport;
    @Autowired DailyInventorySummaryService daily;
    @Autowired com.hospital.spd.common.service.InventoryEventService events;
    @Autowired PackingTaskService packing;
    @Autowired OperationalConsumptionModule consumption;
    @Autowired OperationalHighValueModule highValue;
    @Autowired OperationalDeliveryModule delivery;
    @Autowired ReceivingOrderService receiving;
    @Autowired StockReturnService stockReturns;
    @Autowired OperationalClosureReadModel closureReadModel;
    String code;
    long productId, warehouseId, batchId;

    @BeforeEach void fixture() {
        code = "AF-" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO product_category (category_code, category_name, level) VALUES (?, '审核测试', 1)", code);
        long category = id("SELECT category_id FROM product_category WHERE category_code = ?", code);
        jdbc.update("INSERT INTO product (product_code, product_name, spec_model, category_id, unit, purchase_price, is_chargeable) VALUES (?, '审核耗材', '测试', ?, '个', 10, 0)", code, category);
        productId = id("SELECT product_id FROM product WHERE product_code = ?", code);
        jdbc.update("INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, campus_name) VALUES (?, ?, '中心库', '审核测试院区')", code, code);
        warehouseId = id("SELECT warehouse_id FROM warehouse WHERE warehouse_code = ?", code);
        jdbc.update("INSERT INTO inventory_batch (system_batch_no, product_id, batch_unit_price) VALUES (?, ?, 10)", code, productId);
        batchId = id("SELECT batch_id FROM inventory_batch WHERE system_batch_no = ?", code);
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, product_id, batch_id, available_qty, locked_qty) VALUES (?, ?, ?, 2, 8)", warehouseId, productId, batchId);
    }

    @Test void cannotCountReservedPhysicalStockAsProfit() {
        assertThatThrownBy(() -> inventory.createStocktaking(new StocktakingRequest(code, code, BigDecimal.TEN, "实物十件，八件已预占")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("预占");
        assertThat(id("SELECT COUNT(*) FROM inventory_stocktaking WHERE warehouse_id = ?", warehouseId)).isZero();
        assertThat(jdbc.queryForObject("SELECT available_qty + locked_qty FROM inventory_balance WHERE warehouse_id = ?", BigDecimal.class, warehouseId))
                .isEqualByComparingTo("10");
    }

    long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    @Test void zeroBalanceCatalogProductCanBeCounted() {
        jdbc.update("UPDATE inventory_balance SET available_qty = 0, locked_qty = 0 WHERE batch_id = ?", batchId);
        jdbc.update("INSERT INTO warehouse_product_binding (warehouse_id, product_id) VALUES (?, ?)", warehouseId, productId);
        var preview = inventory.previewStocktakingSheet(new com.hospital.spd.supplychain.StocktakingSheetRequest(code, null, java.util.List.of("nonChargeable")));
        assertThat((java.util.List<?>) preview.get("rows")).hasSize(1);
    }

    @Test void recallReleasesAndIsolatesPackingReservations() {
        long template = template();
        jdbc.update("INSERT INTO quota_packing_task (task_no, template_id, warehouse_id, product_id, package_count, package_quantity, planned_loose_qty, reserved_loose_qty) VALUES (?, ?, ?, ?, 1, 8, 8, 8)", code, template, warehouseId, productId);
        long task = id("SELECT task_id FROM quota_packing_task WHERE task_no = ?", code);
        long balance = id("SELECT balance_id FROM inventory_balance WHERE batch_id = ?", batchId);
        jdbc.update("INSERT INTO quota_packing_task_reservation (task_id, balance_id, batch_id, reserved_qty, unit_price) VALUES (?, ?, ?, 8, 10)", task, balance, batchId);
        risk.createRecall(java.util.Map.of("businessType", "isolate", "warehouseName", code, "productCode", code, "batchNo", code, "reason", "质量召回"));
        assertThat(quantity("available_qty")).isZero();
        assertThat(quantity("locked_qty")).isZero();
        assertThat(quantity("isolated_qty")).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT status FROM quota_packing_task WHERE task_id = ?", String.class, task)).isEqualTo("need_recalculate");
        assertThatThrownBy(() -> packing.confirmTask(code)).hasMessageContaining("待确认");
    }

    @Test void packingPreviewsChangedTemplateAndReservesOnlyAfterVersionConfirmation() {
        long originalId = template();
        jdbc.update("UPDATE quota_package_template SET is_current = 0, status = 0 WHERE template_id = ?", originalId);
        jdbc.update("""
                INSERT INTO quota_package_template (template_code, template_name, version_no, is_current, status)
                VALUES (?, '更新后的测试定数包', 2, 1, 1)
                """, code);
        long currentId = id("SELECT template_id FROM quota_package_template WHERE template_code = ? AND is_current = 1", code);
        jdbc.update("INSERT INTO quota_package_template_item (template_id, product_id, quantity, unit) VALUES (?, ?, 7, '个')",
                currentId, productId);
        jdbc.update("UPDATE inventory_balance SET available_qty = 20, locked_qty = 0 WHERE batch_id = ?", batchId);

        var preview = packing.createTask(new PackingTaskRequest(code, code, BigDecimal.ONE, null, false, null, originalId));
        assertThat(preview).containsEntry("created", false).containsKey("latestTemplate");
        assertThat(id("SELECT COUNT(*) FROM quota_packing_task WHERE product_id = ?", productId)).isZero();
        assertThat(id("SELECT locked_qty FROM inventory_balance WHERE batch_id = ?", batchId)).isZero();

        var result = packing.createTask(new PackingTaskRequest(code, code, BigDecimal.ONE, null, false, null, currentId));
        assertThat(result).containsEntry("created", true);
        assertThat(id("SELECT template_id FROM quota_packing_task WHERE product_id = ?", productId)).isEqualTo(currentId);
        assertThat(id("SELECT locked_qty FROM inventory_balance WHERE batch_id = ?", batchId)).isEqualTo(7L);
    }

    @Test void recallIncludesUnprintedAndInTransitPackages() {
        long template = template();
        jdbc.update("UPDATE inventory_balance SET available_qty = 2, locked_qty = 0 WHERE batch_id = ?", batchId);
        long first = label(template, "pending_print", 4, "-P");
        long second = label(template, "delivered", 4, "-D");
        var result = risk.createRecall(java.util.Map.of("businessType", "isolate", "warehouseName", code, "productCode", code, "batchNo", code, "reason", "质量召回"));
        assertThat((BigDecimal) result.get("affectedQty")).isEqualByComparingTo("10");
        assertThat(quantity("available_qty")).isZero();
        assertThat(quantity("isolated_qty")).isEqualByComparingTo("10");
        assertThat(id("SELECT COUNT(*) FROM quota_package_label WHERE label_id IN (?, ?) AND status = 'void'", first, second)).isEqualTo(2);
    }

    @Test void groupedPackageStockReturnsOnlyTheCurrentPackagesUniqueCodes() {
        long template = template();
        long first = label(template, "available", 2, "-AVAILABLE");
        long second = label(template, "signed", 2, "-SIGNED");
        long consumed = label(template, "consumed", 2, "-CONSUMED");
        label(template, "pending_print", 2, "-PENDING");
        for (long labelId : new long[]{first, second, consumed}) {
            String uniqueCode = code + "-" + labelId;
            jdbc.update("""
                    INSERT INTO udi_trace_code (udi_code, unique_code, product_code, product_name, trace_scope)
                    VALUES (?, ?, ?, '测试定数包', 'low_value_quota_pack')
                    """, uniqueCode, uniqueCode, code);
            jdbc.update("""
                    UPDATE quota_package_label SET trace_code_id =
                      (SELECT trace_code_id FROM udi_trace_code WHERE unique_code = ?)
                    WHERE label_id = ?
                    """, uniqueCode, labelId);
        }
        var result = inventory.quotaPackageStock(java.util.Map.of("productCode", code, "size", "1"));
        assertThat(result).containsEntry("total", 1L);
        assertThat((List<?>) result.get("rows")).singleElement().satisfies(row -> {
            var stock = (java.util.Map<?, ?>) row;
            assertThat(((Number) stock.get("packageCount")).intValue()).isEqualTo(3);
            assertThat(stock.get("packageUniqueCodes")).isEqualTo(
                    java.util.stream.Stream.of(code + "-" + first, code + "-" + second).sorted().toList());
        });
    }

    @Test void signedPackageRecallCountsItsPhysicalUnitsOnlyOnce() {
        long template = template();
        jdbc.update("UPDATE inventory_balance SET available_qty = 10, locked_qty = 0 WHERE batch_id = ?", batchId);
        label(template, "signed", 5, "-S");
        var result = risk.createRecall(java.util.Map.of("businessType", "isolate", "warehouseName", code, "productCode", code, "batchNo", code, "reason", "质量召回"));
        assertThat((BigDecimal) result.get("affectedQty")).isEqualByComparingTo("10");
        assertThat(quantity("available_qty")).isZero();
        assertThat(quantity("isolated_qty")).isEqualByComparingTo("10");
    }

    @Test void availableLooseStockPagesExcludeSignedPackagesAndExpiredBatches() {
        jdbc.update("UPDATE inventory_balance SET available_qty = 10 WHERE batch_id = ?", batchId);
        label(template(), "signed", 5, "-LOOSE");
        jdbc.update("""
                INSERT INTO inventory_batch (system_batch_no,product_id,batch_unit_price,expire_date)
                VALUES (?,?,10,CURRENT_DATE + INTERVAL 1 DAY),(?,?,10,CURRENT_DATE - INTERVAL 1 DAY)
                """, code + "-LIVE", productId, code + "-EXPIRED", productId);
        jdbc.update("""
                INSERT INTO inventory_balance (warehouse_id,product_id,batch_id,available_qty,locked_qty)
                SELECT ?,product_id,batch_id,3,0 FROM inventory_batch WHERE system_batch_no IN (?,?)
                """, warehouseId, code + "-LIVE", code + "-EXPIRED");

        var first = inventory.looseStock(java.util.Map.of("productCode", code, "size", "1"));
        var second = inventory.looseStock(java.util.Map.of("warehouseName", code, "page", "2", "size", "1"));
        assertThat(first.total()).isEqualTo(2);
        assertThat(second.total()).isEqualTo(2);
        assertThat(first.rows()).singleElement().satisfies(row -> assertThat(row).containsEntry("systemBatchNo", code + "-LIVE"));
        assertThat(second.rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("systemBatchNo", code);
            assertThat((BigDecimal) row.get("availableQty")).isEqualByComparingTo("5");
        });
    }

    @Test void midnightConsumptionBelongsToExactlyOneBusinessDate() {
        java.time.LocalDate day = java.time.LocalDate.now().minusDays(3);
        jdbc.update("DELETE FROM inventory_daily_summary");
        jdbc.update("UPDATE inventory_balance SET available_qty = 5, locked_qty = 0 WHERE batch_id = ?", batchId);
        event("department_consumption_out", "-2", "8", day.plusDays(1).atStartOfDay().minusSeconds(1));
        event("department_consumption_out", "-3", "5", day.plusDays(1).atStartOfDay());
        daily.generateThrough(day.minusDays(1));
        daily.generateThrough(day.plusDays(1));
        assertSummary(day, "10", "2", "8");
        assertSummary(day.plusDays(1), "8", "3", "5");
    }

    @Test void ordinaryDeliveryReceiptIncreasesIncrementalClosingStock() {
        java.time.LocalDate day = java.time.LocalDate.now().minusDays(2);
        jdbc.update("DELETE FROM inventory_daily_summary");
        jdbc.update("UPDATE inventory_balance SET available_qty = 10, locked_qty = 0 WHERE batch_id = ?", batchId);
        event("delivery_sign_in", "10", "10", day.atTime(10, 0));
        daily.generateThrough(day.minusDays(1));
        daily.generateThrough(day);
        assertThat(jdbc.queryForObject("SELECT inbound_quantity FROM inventory_daily_summary WHERE business_date = ? AND batch_id = ?", BigDecimal.class, java.sql.Date.valueOf(day), batchId)).isEqualByComparingTo("10");
        assertSummary(day, "0", "0", "10");
    }

    @Test void historicalRecalculationPreservesPriceBeforeLaterAdjustment() {
        java.time.LocalDate day = java.time.LocalDate.now().minusDays(2);
        jdbc.update("DELETE FROM inventory_daily_summary");
        jdbc.update("UPDATE inventory_balance SET available_qty = 20, locked_qty = 0 WHERE batch_id = ?", batchId);
        daily.generateThrough(day);
        jdbc.update("UPDATE inventory_batch SET batch_unit_price = 12 WHERE batch_id = ?", batchId);
        events.recordValuationEvents(1L, batchId, BigDecimal.TEN, new BigDecimal("12"), "事后调价");
        daily.regenerate(day);
        assertThat(jdbc.queryForObject("SELECT unit_price * closing_quantity FROM inventory_daily_summary WHERE business_date = ? AND batch_id = ?", BigDecimal.class, java.sql.Date.valueOf(day), batchId)).isEqualByComparingTo("200");
    }

    @Test void valuationIncludesEveryStockState() {
        jdbc.update("UPDATE inventory_balance SET available_qty = 8, locked_qty = 1, in_transit_qty = 2, isolated_qty = 3 WHERE batch_id = ?", batchId);
        var ids = events.recordValuationEvents(1L, batchId, BigDecimal.TEN, new BigDecimal("12"), "估值核对");
        assertThat(ids).hasSize(1);
        var row = jdbc.queryForMap("SELECT affected_qty_snapshot AS qty, value_change AS amount FROM inventory_event WHERE event_id = ?", ids.get(0));
        assertThat((BigDecimal) row.get("qty")).isEqualByComparingTo("14");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("28");
    }

    @Test void invoicedPendingSettlementRejectsReversalBeforeChangingStock() {
        long bill = sharedSettlement();
        long supplier = id("SELECT supplier_id FROM settlement_bill WHERE settlement_id = ?", bill);
        jdbc.update("INSERT INTO supplier_invoice (invoice_no, supplier_id, invoice_code, invoice_number, invoice_date, amount, settlement_id) VALUES (?, ?, 'C', ?, CURRENT_DATE, 30, ?)", code, supplier, code, bill);
        assertThatThrownBy(() -> consumption.reverseConsumption(code + "-X1")).hasMessageContaining("关联发票");
        assertThat(quantity("available_qty")).isEqualByComparingTo("2");
        assertThat(id("SELECT COUNT(*) FROM settlement_bill_item WHERE settlement_id = ?", bill)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM department_consumption WHERE consumption_no = ?", String.class, code + "-X1")).isEqualTo("confirmed");
    }

    @Test void reversingOneConsumptionPreservesOtherItemsAndExactSharedBillTotal() {
        long bill = sharedSettlement();
        String billNo = jdbc.queryForObject("SELECT settlement_no FROM settlement_bill WHERE settlement_id = ?", String.class, bill);
        var result = consumption.reverseConsumption(code + "-X1");
        assertThat(quantity("available_qty")).isEqualByComparingTo("3");
        assertThat(id("SELECT COUNT(*) FROM settlement_bill_item WHERE settlement_id = ?", bill)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_amount FROM settlement_bill WHERE settlement_id = ?", BigDecimal.class, bill)).isEqualByComparingTo("20");
        var detail = closureReadModel.redFlushDetail((String) result.get("flushNo"));
        assertThat(detail.get("items")).asList().hasSize(1);
        assertThat(detail.get("settlements")).asList()
                .containsExactly(Map.of("settlementNo", billNo, "removedAmount", new BigDecimal("10.0000")));
    }

    @Test void approvalInProgressBlocksConsumptionReversal() {
        long bill = sharedSettlement();
        String billNo = jdbc.queryForObject("SELECT settlement_no FROM settlement_bill WHERE settlement_id = ?", String.class, bill);
        jdbc.update("INSERT INTO business_approval_run (document_key,current_order,status) VALUES (?,2,'pending')",
                "settlement-reconciliation:settlement-confirm:" + billNo);
        assertThatThrownBy(() -> consumption.reverseConsumption(code + "-X1")).hasMessageContaining("已开始审批");
        assertThat(quantity("available_qty")).isEqualByComparingTo("2");
        assertThat(id("SELECT COUNT(*) FROM settlement_bill_item WHERE settlement_id = ?", bill)).isEqualTo(2);
        assertThat(id("SELECT COUNT(*) FROM consumption_red_flush WHERE source_consumption_no = ?", code + "-X1")).isZero();
    }

    @Test void signedHighValueCanBeConsumedAndSettledWithoutBillingCallback() {
        long dept = department("-DEP");
        jdbc.update("UPDATE warehouse SET dept_id = ? WHERE warehouse_id = ?", dept, warehouseId);
        jdbc.update("UPDATE product SET is_high_value = 1 WHERE product_id = ?", productId);
        jdbc.update("INSERT INTO supplier (supplier_code, supplier_name, credit_code, supplier_type, contact_name, contact_phone) VALUES (?, ?, ?, '配送商', '审核测试', '13000000000')",
                code, code, code.substring(3, 21));
        long supplier = id("SELECT supplier_id FROM supplier WHERE supplier_code = ?", code);
        jdbc.update("UPDATE inventory_batch SET supplier_id = ?, settlement_mode = 'actual_sale' WHERE batch_id = ?", supplier, batchId);
        jdbc.update("INSERT INTO udi_trace_code (udi_code, unique_code, product_code, product_name, trace_scope, current_status) VALUES (?, ?, ?, '高值测试', 'high_value', 'signed')", code, code, code);
        long trace = id("SELECT trace_code_id FROM udi_trace_code WHERE unique_code = ?", code);
        jdbc.update("INSERT INTO inventory_batch_trace_code (batch_id, trace_code_id, receiving_item_id, current_warehouse_id, lifecycle_status) VALUES (?, ?, 1, ?, 'signed')", batchId, trace, warehouseId);
        jdbc.update("""
                INSERT INTO department_requisition (requisition_no, dept_id, warehouse_id, requisition_type, status)
                VALUES (?, ?, ?, 'urgent', 'signed')
                """, code, dept, warehouseId);
        jdbc.update("""
                INSERT INTO spd_delivery_order
                  (delivery_no, requisition_no, dept_name, warehouse_name, product_code, product_name, quantity,
                   status, delivery_type, destination_warehouse_id, sign_time, requisition_type)
                VALUES (?, ?, ?, ?, ?, '高值测试', 1, 'signed', 'unique_code', ?, NOW(), 'urgent')
                """, code, code, code + "-DEP", code, code, warehouseId);
        jdbc.update("""
                INSERT INTO spd_delivery_trace_code (delivery_id, trace_code_id)
                SELECT delivery_id, ? FROM spd_delivery_order WHERE delivery_no = ?
                """, trace, code);

        var request = java.util.Map.<String, Object>of("deptName", code + "-DEP", "warehouseName", code,
                "productCode", code, "uniqueCode", code, "quantity", BigDecimal.ONE);
        var result = consumption.createConsumption(request);
        assertThat((java.util.List<?>) result.get("settlementNos")).hasSize(1);
        assertThat(quantity("available_qty")).isEqualByComparingTo("1");
        assertThat(id("SELECT COUNT(*) FROM settlement_bill_item WHERE trace_code_id = ? AND source_biz_type = 'department_consumption_item'", trace)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT requisition_type FROM department_consumption_item WHERE trace_code_id = ?",
                String.class, trace)).isEqualTo("urgent");
        assertThat(jdbc.queryForObject("SELECT requisition_type FROM settlement_bill_item WHERE trace_code_id = ?",
                String.class, trace)).isEqualTo("urgent");
        assertThat(id("SELECT COUNT(*) FROM inventory_event_trace_code WHERE trace_code_id = ?", trace)).isEqualTo(1);
        assertThatThrownBy(() -> consumption.createConsumption(request)).hasMessageContaining("consumed");
        assertThat(quantity("available_qty")).isEqualByComparingTo("1");

        consumption.reverseConsumption(String.valueOf(result.get("consumptionNo")));
        assertThat(quantity("available_qty")).isEqualByComparingTo("2");
        assertThat(id("SELECT COUNT(*) FROM settlement_bill_item WHERE trace_code_id = ?", trace)).isZero();
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM inventory_batch_trace_code WHERE trace_code_id = ?", String.class, trace)).isEqualTo("signed");
        highValue.receiveBillingCallback(java.util.Map.of("externalChargeNo", code + "-HIS", "uniqueCode", code,
                "productCode", code, "warehouseName", code, "deptName", "HIS科室别名",
                "patientNo", "FLOW-TEST", "amount", BigDecimal.TEN, "quantity", BigDecimal.ONE));
        assertThat(jdbc.queryForObject("""
                SELECT requisition_type FROM settlement_bill_item
                WHERE trace_code_id = ? AND source_biz_type = 'high_value_charge'
                """, String.class, trace)).isEqualTo("urgent");
    }

    @Test void departmentReturnReservesThenTransfersOnlyOnConfirmation() {
        long dept = department("-RET");
        jdbc.update("UPDATE warehouse SET warehouse_type='二级库',dept_id=? WHERE warehouse_id=?", dept, warehouseId);
        jdbc.update("UPDATE inventory_balance SET available_qty=6,locked_qty=0 WHERE batch_id=?", batchId);
        jdbc.update("INSERT INTO warehouse (warehouse_code,warehouse_name,warehouse_type,campus_name) VALUES (?,?,'中心库','审核测试院区')",
                code + "-DST", code + "-DST");
        long center = id("SELECT warehouse_id FROM warehouse WHERE warehouse_code=?", code + "-DST");
        long balance = id("SELECT balance_id FROM inventory_balance WHERE warehouse_id=? AND batch_id=?", warehouseId, batchId);

        assertThat(stockReturns.candidates(warehouseId, "batch", null, "", code))
                .singleElement().satisfies(row -> {
                    assertThat(row).containsEntry("unit", "个").containsEntry("specModel", "测试")
                            .containsKeys("registrationNo", "manufacturerName");
                    assertThat((BigDecimal) row.get("unitPrice")).isEqualByComparingTo("10");
                });
        var request = new StockReturnService.ReturnRequest("department", warehouseId, center, null, "batch", "退库测试",
                java.util.List.of(new StockReturnService.Selection(balance, BigDecimal.valueOf(2))), returnKey());
        var pending = stockReturns.submit(request);
        String no = (String) pending.get("returnNo");
        assertThat(stockReturns.submit(request)).isEqualTo(pending);
        assertThat(pending).containsEntry("status", "pending_confirm");
        assertThat(quantity("available_qty")).isEqualByComparingTo("4");
        assertThat(quantity("locked_qty")).isEqualByComparingTo("2");
        assertThat(id("SELECT COUNT(*) FROM inventory_balance WHERE warehouse_id=? AND batch_id=?", center, batchId)).isZero();
        assertThat(stockReturns.records(true)).anyMatch(row -> no.equals(row.get("returnNo")));
        var pendingReturns = stockReturns.pickingQueue(new PageRequest(1, 1, 0));
        assertThat(pendingReturns.total()).isPositive();
        assertThat(pendingReturns.rows()).singleElement().satisfies(row ->
                assertThat(row).containsEntry("returnNo", no));

        assertThat(stockReturns.confirmWarehouseReturn(no)).containsEntry("status", "confirmed");
        stockReturns.confirmWarehouseReturn(no);
        assertThat(quantity("available_qty")).isEqualByComparingTo("4");
        assertThat(quantity("locked_qty")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT available_qty FROM inventory_balance WHERE warehouse_id=? AND batch_id=?",
                BigDecimal.class, center, batchId)).isEqualByComparingTo("2");
        assertThat(stockReturns.records(true)).noneMatch(row -> no.equals(row.get("returnNo")));
        var confirmedReturns = stockReturns.pickingQueue(new PageRequest(1, 1, 0), "confirmed");
        assertThat(confirmedReturns.rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("returnNo", no).containsEntry("status", "confirmed");
            assertThat(row.get("confirmTime")).isNotNull();
        });
    }

    @Test void departmentReturnKeepsThePackageAndDoesNotDoubleCountLooseStockAtCenter() {
        long dept = department("-PK-RET");
        jdbc.update("UPDATE warehouse SET warehouse_type='二级库',dept_id=? WHERE warehouse_id=?", dept, warehouseId);
        jdbc.update("UPDATE inventory_balance SET available_qty=3,locked_qty=0 WHERE batch_id=?", batchId);
        jdbc.update("INSERT INTO warehouse (warehouse_code,warehouse_name,warehouse_type,campus_name) VALUES (?,?,'中心库','审核测试院区')",
                code + "-DST", code + "-DST");
        long center = id("SELECT warehouse_id FROM warehouse WHERE warehouse_code=?", code + "-DST");
        long template = template();
        jdbc.update("INSERT INTO quota_package_label(label_no,template_id,product_id,warehouse_id,package_quantity,status) VALUES (?,?,?,?,2,'signed')",
                code, template, productId, warehouseId);
        long label = id("SELECT label_id FROM quota_package_label WHERE label_no=?", code);
        jdbc.update("INSERT INTO quota_package_label_source(label_id,batch_id,source_qty,unit_price,warehouse_id) VALUES (?,?,2,10,?)",
                label, batchId, warehouseId);
        jdbc.update("UPDATE inventory_batch SET batch_unit_price=1.25 WHERE batch_id=?", batchId);
        assertThat(stockReturns.candidates(warehouseId, "package", null, "", code))
                .singleElement().satisfies(row ->
                        assertThat((BigDecimal) row.get("unitPrice")).isEqualByComparingTo("2.50"));
        var request = new StockReturnService.ReturnRequest("department", warehouseId, center, null, "package", "退包测试",
                java.util.List.of(new StockReturnService.Selection(label, BigDecimal.ONE)), returnKey());
        var order = stockReturns.submit(request);
        assertThat(quantity("available_qty")).isEqualByComparingTo("1");
        assertThat(quantity("locked_qty")).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT status FROM quota_package_label WHERE label_id=?", String.class, label)).isEqualTo("return_reserved");
        stockReturns.confirmWarehouseReturn((String) order.get("returnNo"));
        assertThat(id("SELECT warehouse_id FROM quota_package_label WHERE label_id=?", label)).isEqualTo(center);
        assertThat(jdbc.queryForObject("SELECT status FROM quota_package_label WHERE label_id=?", String.class, label)).isEqualTo("available");
        assertThat(quantity("locked_qty")).isEqualByComparingTo("0");
        assertThat(id("SELECT COUNT(*) FROM inventory_balance WHERE warehouse_id=? AND batch_id=? AND available_qty>0", center, batchId)).isZero();
    }

    @Test void supplierReturnDeductsOnlyChosenHighValueCode() {
        long supplier = returnSupplier();
        jdbc.update("UPDATE inventory_balance SET locked_qty=0 WHERE batch_id=?", batchId);
        jdbc.update("UPDATE product SET is_high_value=1 WHERE product_id=?", productId);
        for (int i = 0; i < 2; i++) {
            String unique = code + "-" + i;
            jdbc.update("INSERT INTO udi_trace_code (udi_code,unique_code,product_code,product_name,trace_scope,current_status) VALUES (?,?,?,'高值测试','high_value','in_stock')",
                    unique, unique, code);
            long trace = id("SELECT trace_code_id FROM udi_trace_code WHERE unique_code=?", unique);
            jdbc.update("INSERT INTO inventory_batch_trace_code (batch_id,trace_code_id,receiving_item_id,current_warehouse_id,lifecycle_status) VALUES (?,?,1,?,'in_stock')",
                    batchId, trace, warehouseId);
        }
        var candidates = stockReturns.candidates(warehouseId, "trace", supplier, code, "");
        assertThat(candidates).hasSize(2);
        long chosen = ((Number) candidates.get(0).get("id")).longValue();
        long other = ((Number) candidates.get(1).get("id")).longValue();
        var request = new StockReturnService.ReturnRequest("supplier", warehouseId, null, supplier, "trace", "退货测试",
                java.util.List.of(new StockReturnService.Selection(chosen, BigDecimal.ONE)), returnKey());
        var result = stockReturns.submit(request);
        assertThat(result).containsEntry("status", "confirmed");
        assertThat(stockReturns.submit(request)).isEqualTo(result);
        assertThat(quantity("available_qty")).isEqualByComparingTo("1");
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM inventory_batch_trace_code WHERE trace_code_id=?", String.class, chosen)).isEqualTo("supplier_returned");
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM inventory_batch_trace_code WHERE trace_code_id=?", String.class, other)).isEqualTo("in_stock");
        assertThat(id("SELECT COUNT(*) FROM inventory_event_trace_code WHERE trace_code_id=?", chosen)).isEqualTo(1);
    }

    @Test void supplierReturnUsesWholeSignedPackageWithoutTakingOtherBatchStock() {
        long supplier = returnSupplier();
        long dept = department("-PKG");
        jdbc.update("UPDATE warehouse SET warehouse_type='二级库',dept_id=? WHERE warehouse_id=?", dept, warehouseId);
        jdbc.update("UPDATE inventory_balance SET available_qty=5,locked_qty=0 WHERE batch_id=?", batchId);
        long template = template();
        jdbc.update("INSERT INTO quota_package_label(label_no,template_id,product_id,warehouse_id,package_quantity,status) VALUES (?,?,?,?,2,'signed')",
                code, template, productId, warehouseId);
        long label = id("SELECT label_id FROM quota_package_label WHERE label_no=?", code);
        jdbc.update("INSERT INTO quota_package_label_source(label_id,batch_id,source_qty,unit_price,warehouse_id) VALUES (?,?,2,10,?)",
                label, batchId, warehouseId);
        assertThat(stockReturns.candidates(warehouseId, "package", supplier, code, "")).hasSize(1);
        assertThat((BigDecimal) stockReturns.candidates(warehouseId, "batch", supplier, code, "").get(0).get("availableQuantity"))
                .isEqualByComparingTo("3");

        var request = new StockReturnService.ReturnRequest("supplier", warehouseId, null, supplier, "package", "退包测试",
                java.util.List.of(new StockReturnService.Selection(label, BigDecimal.ONE)), returnKey());
        assertThat(stockReturns.submit(request)).containsEntry("status", "confirmed");
        assertThat(quantity("available_qty")).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("SELECT status FROM quota_package_label WHERE label_id=?", String.class, label)).isEqualTo("returned");
    }

    private long returnSupplier() {
        jdbc.update("INSERT INTO supplier (supplier_code,supplier_name,credit_code,supplier_type,contact_name,contact_phone) VALUES (?,?,?,'配送商','审核测试','13000000000')",
                code, code, code.substring(3, 21));
        long supplier = id("SELECT supplier_id FROM supplier WHERE supplier_code=?", code);
        jdbc.update("UPDATE inventory_batch SET supplier_id=? WHERE batch_id=?", supplier, batchId);
        return supplier;
    }

    private String returnKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Test void recallReturnsHighValueToCenterOnlyAfterReceipt() {
        long dept = department("-DEP");
        jdbc.update("INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, campus_name) VALUES (?, ?, '中心库', '审核测试院区')",
                code + "-CENTER", code + "-CENTER");
        jdbc.update("UPDATE warehouse SET warehouse_type = '二级库', dept_id = ? WHERE warehouse_id = ?", dept, warehouseId);
        jdbc.update("UPDATE inventory_balance SET available_qty = 1, locked_qty = 0 WHERE batch_id = ?", batchId);
        jdbc.update("UPDATE product SET is_high_value = 1 WHERE product_id = ?", productId);
        jdbc.update("INSERT INTO udi_trace_code (udi_code, unique_code, product_code, product_name, trace_scope, current_status) VALUES (?, ?, ?, '高值测试', 'high_value', 'signed')", code, code, code);
        long trace = id("SELECT trace_code_id FROM udi_trace_code WHERE unique_code = ?", code);
        jdbc.update("INSERT INTO inventory_batch_trace_code (batch_id, trace_code_id, receiving_item_id, current_warehouse_id, lifecycle_status) VALUES (?, ?, 1, ?, 'signed')", batchId, trace, warehouseId);

        var result = risk.createRecall(java.util.Map.of("businessType", "recall", "scope", "secondary",
                "warehouseName", code, "productCode", code, "batchNo", code, "reason", "召回签收测试"));
        String recallNo = (String) result.get("recallNo");
        long destination = id("SELECT destination_warehouse_id FROM recall_event WHERE recall_no = ?", recallNo);
        assertThat(result).containsEntry("status", "pending_receipt");
        assertThat(quantity("available_qty")).isEqualByComparingTo("0");
        assertThat(id("SELECT COUNT(*) FROM inventory_balance WHERE warehouse_id = ? AND batch_id = ?", destination, batchId)).isZero();
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM inventory_batch_trace_code WHERE trace_code_id = ?", String.class, trace)).isEqualTo("recall_in_transit");

        long centerDept = department("-CENTER-DEP");
        jdbc.update("UPDATE warehouse SET dept_id = ? WHERE warehouse_id = ?", centerDept, destination);
        var sourceOperator = new OperatorContext(2L, "source", null, List.of(), dept, OperatorContext.DATA_SCOPE_DEPT);
        var centerOperator = new OperatorContext(3L, "center", null, List.of(), centerDept, OperatorContext.DATA_SCOPE_DEPT);
        var sourceRisk = new OperationalRiskModule(jdbc, supplyChainSupport, new DataScopeService(() -> sourceOperator));
        var centerRisk = new OperationalRiskModule(jdbc, supplyChainSupport, new DataScopeService(() -> centerOperator));
        assertThat(sourceRisk.pendingRecallReceipts()).isEmpty();
        assertThat(sourceRisk.pendingRecallReceipts(new PageRequest(1, 1, 0)).total()).isZero();
        assertThatThrownBy(() -> sourceRisk.receiveRecall(recallNo)).isInstanceOf(AccessDeniedException.class);
        assertThat(centerRisk.pendingRecallReceipts()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("recallNo", recallNo).containsEntry("sourceWarehouses", code)
                    .containsEntry("destinationWarehouse", result.get("primaryWarehouseName"));
            assertThat((BigDecimal) row.get("quantity")).isEqualByComparingTo("1");
        });
        var pendingRecalls = centerRisk.pendingRecallReceipts(new PageRequest(1, 1, 0));
        assertThat(pendingRecalls.total()).isEqualTo(1);
        assertThat(pendingRecalls.rows()).singleElement().satisfies(row ->
                assertThat(row).containsEntry("recallNo", recallNo));
        var pastLastPage = centerRisk.pendingRecallReceipts(new PageRequest(2, 1, 1));
        assertThat(pastLastPage.total()).isEqualTo(1);
        assertThat(pastLastPage.rows()).isEmpty();
        assertThat(centerRisk.receiveRecall(recallNo)).containsEntry("status", "received");
        assertThat(centerRisk.pendingRecallReceipts()).isEmpty();
        assertThatThrownBy(() -> sourceRisk.receiveRecall(recallNo)).isInstanceOf(AccessDeniedException.class);
        assertThat(risk.receiveRecall(recallNo)).containsEntry("status", "received");
        assertThat(jdbc.queryForObject("SELECT available_qty FROM inventory_balance WHERE warehouse_id = ? AND batch_id = ?", BigDecimal.class, destination, batchId)).isEqualByComparingTo("1");
        assertThat(id("SELECT current_warehouse_id FROM inventory_batch_trace_code WHERE trace_code_id = ?", trace)).isEqualTo(destination);
        assertThat(id("SELECT COUNT(*) FROM inventory_event_trace_code WHERE trace_code_id = ?", trace)).isEqualTo(2);
        assertThat(id("SELECT COUNT(*) FROM recall_event WHERE recall_no = ? AND received_time IS NOT NULL", recallNo)).isEqualTo(1);
    }

    @Test void highValueIsolationUpdatesStableIdentityAndTimeline() {
        jdbc.update("UPDATE inventory_balance SET locked_qty = 0 WHERE batch_id = ?", batchId);
        jdbc.update("UPDATE product SET is_high_value = 1 WHERE product_id = ?", productId);
        jdbc.update("INSERT INTO udi_trace_code (udi_code, unique_code, product_code, product_name, trace_scope, current_status) VALUES (?, ?, ?, '高值测试', 'high_value', 'in_stock')", code, code, code);
        long trace = id("SELECT trace_code_id FROM udi_trace_code WHERE unique_code = ?", code);
        jdbc.update("INSERT INTO inventory_batch_trace_code (batch_id, trace_code_id, receiving_item_id, current_warehouse_id, lifecycle_status) VALUES (?, ?, 1, ?, 'in_stock')", batchId, trace, warehouseId);
        risk.createRecall(java.util.Map.of("businessType", "isolate", "warehouseName", code, "productCode", code, "batchNo", code, "reason", "质量召回"));
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM inventory_batch_trace_code WHERE trace_code_id = ?", String.class, trace)).isEqualTo("isolated");
        assertThat(jdbc.queryForObject("SELECT current_status FROM udi_trace_code WHERE trace_code_id = ?", String.class, trace)).isEqualTo("isolated");
        assertThat(id("SELECT COUNT(*) FROM udi_trace_event WHERE trace_code_id = ? AND event_type = 'recall_isolation'", trace)).isEqualTo(1);
    }

    @Test void scopedConsumptionListAndOldAnalysisCannotExposeAnotherDepartment() {
        long bill = sharedSettlement();
        long own = department("-OWN");
        var operator = new com.hospital.spd.common.OperatorContext(1L, "nurse", "127.0.0.1", java.util.List.of("ROLE_DEPT_USER"), own, 2);
        var access = new DepartmentRequisitionAccessService(jdbc, () -> operator);
        var model = new OperationalClosureReadModel(jdbc, access);
        assertThat((java.util.List<?>) model.list("consumption", java.util.Map.of()).get("rows")).isEmpty();
        assertThatThrownBy(() -> new OperationalShortageModule(jdbc, support, access).smartAnalyze(java.util.Map.of("deptName", code + "-DEP")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(id("SELECT COUNT(*) FROM settlement_bill WHERE settlement_id = ?", bill)).isEqualTo(1);
    }

    @Test void receiptsUseTheSelectedPurchaseLineAndCombinedCapacity() {
        sharedSettlement();
        long supplier = id("SELECT supplier_id FROM supplier WHERE supplier_code = ?", code);
        jdbc.update("INSERT INTO purchase_order (order_no, supplier_id, order_source, order_status, total_amount) VALUES (?, ?, 'manual', 'approved', 100)", code, supplier);
        long order = id("SELECT purchase_order_id FROM purchase_order WHERE order_no = ?", code);
        jdbc.update("INSERT INTO purchase_order_item (purchase_order_id, product_id, quantity, unit, estimated_unit_price, amount, received_quantity) VALUES (?, ?, 1, '个', 10, 10, 1)", order, productId);
        jdbc.update("INSERT INTO purchase_order_item (purchase_order_id, product_id, quantity, unit, estimated_unit_price, amount, received_quantity) VALUES (?, ?, 10, '个', 10, 100, 0)", order, productId);
        long line = id("SELECT MAX(item_id) FROM purchase_order_item WHERE purchase_order_id = ?", order);
        jdbc.update("UPDATE warehouse SET receiving_enabled = 1 WHERE warehouse_id = ?", warehouseId);
        var explicit = new com.hospital.spd.supplychain.ReceivingItemRequest(code, "B", null, null, null, new BigDecimal("6"), new BigDecimal("6"), BigDecimal.ZERO, line);
        var valid = new com.hospital.spd.supplychain.ReceivingOrderRequest("purchase_order", code, supplier, null, code, code, "normal", false, null, java.util.List.of(explicit));
        assertThat(receiving.create(valid)).containsKey("receivingNo");
        var implicit = new com.hospital.spd.supplychain.ReceivingItemRequest(code, "B", null, null, null, new BigDecimal("5"), new BigDecimal("5"), BigDecimal.ZERO);
        var overflow = new com.hospital.spd.supplychain.ReceivingOrderRequest("purchase_order", code, supplier, null, code, code, "normal", false, null, java.util.List.of(explicit, implicit));
        assertThatThrownBy(() -> receiving.create(overflow)).hasMessageContaining("超过");
    }

    @Test void expiredRejectedDeliveryCanBeRecordedButAcceptedStockCannot() {
        sharedSettlement();
        long supplier = id("SELECT supplier_id FROM supplier WHERE supplier_code = ?", code);
        jdbc.update("UPDATE warehouse SET receiving_enabled = 1 WHERE warehouse_id = ?", warehouseId);
        String expiry = java.time.LocalDate.now().minusDays(1).toString();
        var rejected = new com.hospital.spd.supplychain.ReceivingItemRequest(code, "B", null, null, expiry, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE);
        var request = new com.hospital.spd.supplychain.ReceivingOrderRequest("temporary", null, supplier, null, code, code, "normal", false, null, java.util.List.of(rejected));
        assertThat(receiving.create(request)).containsKey("receivingNo");
        var accepted = new com.hospital.spd.supplychain.ReceivingItemRequest(code, "B", null, null, expiry, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO);
        var invalid = new com.hospital.spd.supplychain.ReceivingOrderRequest("temporary", null, supplier, null, code, code, "normal", false, null, java.util.List.of(accepted));
        assertThatThrownBy(() -> receiving.create(invalid)).hasMessageContaining("过期");
    }

    @Test void recalledPartOfDeliveryDoesNotStrandHealthyPackageOrReplacementPicking() {
        long template = template();
        jdbc.update("UPDATE inventory_balance SET available_qty = 0, locked_qty = 0 WHERE batch_id = ?", batchId);
        long bad = label(template, "delivered", 5, "-BAD");
        jdbc.update("INSERT INTO inventory_batch (system_batch_no, product_id, batch_unit_price) VALUES (?, ?, 10)", code + "-GOOD", productId);
        long goodBatch = id("SELECT batch_id FROM inventory_batch WHERE system_batch_no = ?", code + "-GOOD");
        long good = label(template, "delivered", 5, "-GOOD");
        jdbc.update("UPDATE quota_package_label_source SET batch_id = ? WHERE label_id = ?", goodBatch, good);
        long dept = department("-DEST");
        jdbc.update("INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, campus_name, dept_id) VALUES (?, ?, '科室库', '审核测试院区', ?)", code + "-DEST", code + "-DEST", dept);
        long dest = id("SELECT warehouse_id FROM warehouse WHERE warehouse_code = ?", code + "-DEST");
        jdbc.update("INSERT INTO department_requisition (requisition_no, dept_id, warehouse_id, source_warehouse_id, requisition_type, status) VALUES (?, ?, ?, ?, 'normal', 'picked')", code, dept, dest, warehouseId);
        long req = id("SELECT requisition_id FROM department_requisition WHERE requisition_no = ?", code);
        jdbc.update("INSERT INTO department_requisition_item (requisition_id, product_id, quantity, unit, item_type, quota_template_id, quota_template_version, quota_package_quantity, quota_package_unit) VALUES (?, ?, 10, '个', 'quota_package', ?, 1, 5, '个')", req, productId, template);
        long item = id("SELECT item_id FROM department_requisition_item WHERE requisition_id = ?", req);
        jdbc.update("INSERT INTO spd_delivery_order (delivery_no, requisition_no, requisition_item_id, delivery_type, dept_name, warehouse_name, product_code, product_name, quantity, status) VALUES (?, ?, ?, 'package', ?, ?, ?, '审核耗材', 10, 'picked')", code, code, item, code + "-DEST", code, code);
        long shipment = id("SELECT delivery_id FROM spd_delivery_order WHERE delivery_no = ?", code);
        for (long label : new long[] {bad, good}) jdbc.update("INSERT INTO spd_delivery_package_binding (delivery_id, requisition_id, requisition_item_id, label_id, product_id, package_quantity) VALUES (?, ?, ?, ?, ?, 5)", shipment, req, item, label, productId);
        new com.hospital.spd.specialty.service.QuotaPackageTraceFlowService(jdbc, support).ensureTrace(good);
        risk.createRecall(java.util.Map.of("businessType", "isolate", "warehouseName", code, "productCode", code, "batchNo", code, "reason", "召回部分来源"));
        assertThat(jdbc.queryForObject("SELECT quantity FROM spd_delivery_order WHERE delivery_id = ?", BigDecimal.class, shipment)).isEqualByComparingTo("5");
        assertThat(jdbc.queryForObject("SELECT status FROM department_requisition WHERE requisition_id = ?", String.class, req)).isEqualTo("partial_picked");
        delivery.signDelivery(code);
        assertThat(jdbc.queryForObject("SELECT available_qty FROM inventory_balance WHERE warehouse_id = ? AND batch_id = ?", BigDecimal.class, dest, goodBatch)).isEqualByComparingTo("5");
        long replacement = label(template, "available", 5, "-REPLACE");
        jdbc.update("UPDATE quota_package_label_source SET batch_id = ? WHERE label_id = ?", goodBatch, replacement);
        var picked = delivery.confirmPicking(java.util.Map.of("requisitionNo", code, "itemId", item, "warehouseName", code, "labelNos", java.util.List.of(code + "-REPLACE")));
        assertThat(picked).containsEntry("status", "picked");
        assertThat(jdbc.queryForObject("SELECT status FROM department_requisition WHERE requisition_id = ?", String.class, req)).isEqualTo("picked");
    }

    @Autowired com.hospital.spd.supplychain.SupplyChainSupport support;

    private long department(String suffix) {
        jdbc.update("INSERT INTO sys_dept (dept_code, dept_name) VALUES (?, ?)", code + suffix, code + suffix);
        return id("SELECT dept_id FROM sys_dept WHERE dept_code = ?", code + suffix);
    }

    private long sharedSettlement() {
        long dept = department("-DEP");
        jdbc.update("UPDATE warehouse SET dept_id = ? WHERE warehouse_id = ?", dept, warehouseId);
        jdbc.update("UPDATE inventory_balance SET available_qty = 2, locked_qty = 0 WHERE batch_id = ?", batchId);
        jdbc.update("INSERT INTO supplier (supplier_code, supplier_name, credit_code, supplier_type, contact_name, contact_phone) VALUES (?, ?, ?, '配送商', '审核测试', '13000000000')", code, code, code.substring(3, 21));
        long supplier = id("SELECT supplier_id FROM supplier WHERE supplier_code = ?", code);
        jdbc.update("INSERT INTO settlement_bill (settlement_no, supplier_id, settlement_period, settlement_mode, total_amount, status) VALUES (?, ?, '2026-10', 'department_consumption', 30, 'pending_confirm')", code, supplier);
        long bill = id("SELECT settlement_id FROM settlement_bill WHERE settlement_no = ?", code);
        for (int i = 1; i <= 2; i++) {
            jdbc.update("INSERT INTO department_consumption (consumption_no, dept_id, warehouse_id, consumption_type, related_biz_type, status, consume_by) VALUES (?, ?, ?, 'department_consumption', 'inventory', 'confirmed', 1)", code + "-X" + i, dept, warehouseId);
            long dc = id("SELECT consumption_id FROM department_consumption WHERE consumption_no = ?", code + "-X" + i);
            jdbc.update("INSERT INTO department_consumption_item (consumption_id, product_id, batch_id, quantity, unit_price, amount) VALUES (?, ?, ?, ?, 10, ?)", dc, productId, batchId, i, 10 * i);
            long item = id("SELECT item_id FROM department_consumption_item WHERE consumption_id = ?", dc);
            jdbc.update("INSERT INTO settlement_bill_item (settlement_id, source_biz_type, source_biz_id, product_id, batch_id, quantity, unit_price, amount) VALUES (?, 'department_consumption_item', ?, ?, ?, ?, 10, ?)", bill, item, productId, batchId, i, 10 * i);
        }
        return bill;
    }

    private long template() {
        jdbc.update("UPDATE product SET is_quota_managed = 1 WHERE product_id = ?", productId);
        jdbc.update("INSERT INTO quota_package_template (template_code, template_name) VALUES (?, '测试定数包')", code);
        long template = id("SELECT template_id FROM quota_package_template WHERE template_code = ?", code);
        jdbc.update("INSERT INTO quota_package_template_item (template_id, product_id, quantity, unit) VALUES (?, ?, 5, '个')", template, productId);
        return template;
    }

    private long label(long template, String status, int qty, String suffix) {
        jdbc.update("INSERT INTO quota_package_label (label_no, template_id, warehouse_id, product_id, package_quantity, status) VALUES (?, ?, ?, ?, ?, ?)", code + suffix, template, warehouseId, productId, qty, status);
        long label = id("SELECT label_id FROM quota_package_label WHERE label_no = ?", code + suffix);
        jdbc.update("INSERT INTO quota_package_label_source (label_id, batch_id, source_qty, unit_price, warehouse_id) VALUES (?, ?, ?, 10, ?)", label, batchId, qty, warehouseId);
        return label;
    }

    private BigDecimal quantity(String column) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(" + column + "), 0) FROM inventory_balance WHERE warehouse_id = ? AND batch_id = ?", BigDecimal.class, warehouseId, batchId);
    }

    private void event(String type, String change, String after, java.time.LocalDateTime time) {
        jdbc.update("INSERT INTO inventory_event (event_no, event_type, source_biz_type, source_biz_id, warehouse_id, product_id, batch_id, qty_change, qty_after, event_time) VALUES (?, ?, 'audit', 1, ?, ?, ?, ?, ?, ?)",
                "AF-" + UUID.randomUUID(), type, warehouseId, productId, batchId, new BigDecimal(change), new BigDecimal(after), java.sql.Timestamp.valueOf(time));
    }

    private void assertSummary(java.time.LocalDate day, String opening, String consumption, String closing) {
        var row = jdbc.queryForMap("SELECT opening_quantity AS opening, consumption_quantity AS consumption, closing_quantity AS closing FROM inventory_daily_summary WHERE business_date = ? AND batch_id = ? AND warehouse_id = ?", java.sql.Date.valueOf(day), batchId, warehouseId);
        assertThat((BigDecimal) row.get("opening")).isEqualByComparingTo(opening);
        assertThat((BigDecimal) row.get("consumption")).isEqualByComparingTo(consumption);
        assertThat((BigDecimal) row.get("closing")).isEqualByComparingTo(closing);
    }
}
