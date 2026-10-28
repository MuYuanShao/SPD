package com.hospital.spd.supplychain.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;

/** Owns catalog-driven package settlement valuation while retaining immutable source costs. */
@Service
public class QuotaPackagePricingService {
    private final JdbcTemplate jdbc;

    public QuotaPackagePricingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All callers hold the product mutex until their enclosing business transaction commits. */
    public Map<String, Object> lockProduct(long productId) {
        return jdbc.queryForMap("""
                SELECT product_id AS productId, purchase_price AS unitPrice,
                       catalog_price_version AS priceVersion, unit
                  FROM product WHERE product_id = ? FOR UPDATE
                """, productId);
    }

    public void lockProducts(List<Long> productIds) {
        productIds.stream().distinct().sorted().forEach(this::lockProduct);
    }

    public List<Long> lockReceivingProducts(String receivingNo, List<String> requestedCodes) {
        List<Long> ids = new ArrayList<>();
        if (receivingNo != null) {
            ids.addAll(jdbc.queryForList("""
                    SELECT DISTINCT item.product_id FROM receiving_order_item item
                      JOIN receiving_order receiving ON receiving.receiving_order_id = item.receiving_order_id
                     WHERE receiving.receiving_no = ?
                    """, Long.class, receivingNo));
        }
        List<String> codes = requestedCodes.stream().map(String::trim).distinct().toList();
        if (!codes.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
            ids.addAll(jdbc.queryForList("SELECT product_id FROM product WHERE product_code IN (" + placeholders + ")",
                    Long.class, codes.toArray()));
        }
        List<Long> ordered = ids.stream().distinct().sorted().toList();
        lockProducts(ordered);
        return ordered;
    }

    public Map<String, Object> lockCatalogProduct(String productCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT product_id AS productId, purchase_price AS unitPrice,
                       catalog_price_version AS priceVersion, unit
                  FROM product WHERE product_code = ? FOR UPDATE
                """, productCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void lockPackingProduct(String taskNo) {
        List<Long> ids = jdbc.queryForList(
                "SELECT product_id FROM quota_packing_task WHERE task_no = ?", Long.class, taskNo);
        if (!ids.isEmpty()) lockProduct(ids.get(0));
    }

    public void afterCatalogApproval(String applicationNo, Map<String, Object> before, long operatorId) {
        if (before == null) return; // New admissions have no existing packages to revalue.
        long productId = ((Number) before.get("productId")).longValue();
        Map<String, Object> current = lockProduct(productId);
        BigDecimal oldPrice = decimal(before.get("unitPrice"));
        BigDecimal newPrice = decimal(current.get("unitPrice"));
        if (!Objects.equals(before.get("unit"), current.get("unit"))) {
            List<Long> history = jdbc.queryForList("SELECT batch_id FROM inventory_batch WHERE product_id = ? LIMIT 1 FOR SHARE", Long.class, productId);
            List<Long> templates = jdbc.queryForList("SELECT item_id FROM quota_package_template_item WHERE product_id = ? LIMIT 1 FOR SHARE", Long.class, productId);
            if (!history.isEmpty() || !templates.isEmpty()) throw new IllegalArgumentException("商品已有库存业务历史，不能修改基本单位；请新建商品或执行专门的单位转换");
        }
        if (oldPrice.compareTo(newPrice) == 0) return;
        if (!Objects.equals(before.get("unit"), current.get("unit"))) {
            throw new IllegalArgumentException("调价时不能同时变更基本单位，请分开申请并核实包装换算");
        }
        if (newPrice.signum() < 0) throw new IllegalArgumentException("目录价格不能为负数");
        List<Long> existing = jdbc.queryForList(
                "SELECT change_id FROM catalog_price_change WHERE application_no = ?", Long.class, applicationNo);
        if (!existing.isEmpty()) return;
        long version = ((Number) current.get("priceVersion")).longValue() + 1;
        int updated = jdbc.update("UPDATE product SET catalog_price_version = ? WHERE product_id = ? AND catalog_price_version = ?",
                version, productId, version - 1);
        if (updated != 1) throw new IllegalStateException("目录价格版本已变化，请刷新重试");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO catalog_price_change
                      (application_no, product_id, price_version, old_unit_price, new_unit_price, operator_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, applicationNo);
            ps.setLong(2, productId);
            ps.setLong(3, version);
            ps.setBigDecimal(4, oldPrice);
            ps.setBigDecimal(5, newPrice);
            ps.setLong(6, operatorId);
            return ps;
        }, key);
        long changeId = Objects.requireNonNull(key.getKey()).longValue();
        List<Map<String, Object>> labels = jdbc.queryForList("""
                SELECT label_id AS labelId, status, package_quantity AS quantity,
                       settlement_unit_price AS settlementUnitPrice
                  FROM quota_package_label WHERE product_id = ?
                 ORDER BY label_id FOR UPDATE
                """, productId);
        int affected = 0;
        for (Map<String, Object> label : labels) {
            long labelId = ((Number) label.get("labelId")).longValue();
            if (isTerminal(label.get("status")) || isSettlementLocked(labelId)) continue;
            BigDecimal quantity = decimal(label.get("quantity"));
            BigDecimal oldAmount;
            BigDecimal oldUnit;
            if (label.get("settlementUnitPrice") == null) {
                oldAmount = jdbc.queryForObject("SELECT SUM(source_qty * unit_price) FROM quota_package_label_source WHERE label_id = ? FOR SHARE",
                        BigDecimal.class, labelId);
                if (oldAmount == null || quantity.signum() <= 0) continue;
                oldUnit = oldAmount.divide(quantity, 6, RoundingMode.HALF_UP);
            } else {
                oldUnit = decimal(label.get("settlementUnitPrice"));
                oldAmount = oldUnit.multiply(quantity);
            }
            oldAmount = money(oldAmount);
            BigDecimal newAmount = money(newPrice.multiply(quantity));
            jdbc.update("""
                    INSERT INTO quota_package_price_change_item
                      (change_id, label_id, package_quantity, old_unit_price, new_unit_price,
                       old_amount, new_amount, difference_amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, changeId, labelId, quantity, oldUnit, newPrice, oldAmount, newAmount, newAmount.subtract(oldAmount));
            jdbc.update("UPDATE quota_package_label SET settlement_unit_price = ?, catalog_price_version = ? WHERE label_id = ?",
                    newPrice, version, labelId);
            affected++;
        }
        jdbc.update("UPDATE catalog_price_change SET affected_packages = ? WHERE change_id = ?", affected, changeId);
    }

    public void initializePackage(long labelId, long productId) {
        Map<String, Object> product = lockProduct(productId);
        if (!isSettlementLocked(labelId)) {
            jdbc.update("UPDATE quota_package_label SET settlement_unit_price = ?, catalog_price_version = ? WHERE label_id = ? AND settlement_unit_price IS NULL",
                    product.get("unitPrice"), product.get("priceVersion"), labelId);
        }
    }

    /** Releasing the last pending bill makes a returned package eligible for today's approved price. */
    public java.util.Optional<PackagePriceRefresh> refreshAfterConsumptionReversal(long labelId) {
        Long productId = jdbc.queryForObject("SELECT product_id FROM quota_package_label WHERE label_id = ?",
                Long.class, labelId);
        if (productId == null) throw new IllegalArgumentException("定数包不存在");
        Map<String, Object> product = lockProduct(productId);
        Map<String, Object> label = jdbc.queryForMap("""
                SELECT status, package_quantity AS quantity, settlement_unit_price AS unitPrice,
                       catalog_price_version AS priceVersion
                  FROM quota_package_label WHERE label_id = ? FOR UPDATE
                """, labelId);
        if (!"signed".equals(label.get("status"))) throw new IllegalArgumentException("只能刷新反消耗回库包的计价");
        if (isSettlementLocked(labelId)) return java.util.Optional.empty();
        BigDecimal newPrice = decimal(product.get("unitPrice"));
        long version = ((Number) product.get("priceVersion")).longValue();
        if (label.get("unitPrice") != null && decimal(label.get("unitPrice")).compareTo(newPrice) == 0
                && label.get("priceVersion") instanceof Number oldVersion && oldVersion.longValue() == version) {
            return java.util.Optional.empty();
        }
        BigDecimal quantity = decimal(label.get("quantity"));
        if (quantity.signum() <= 0) throw new IllegalArgumentException("定数包包内数量必须大于零");
        BigDecimal oldAmount = label.get("unitPrice") == null
                ? jdbc.queryForObject("SELECT SUM(source_qty * unit_price) FROM quota_package_label_source WHERE label_id = ? FOR SHARE",
                    BigDecimal.class, labelId)
                : decimal(label.get("unitPrice")).multiply(quantity);
        if (oldAmount == null) throw new IllegalArgumentException("定数包缺少来源成本，不能刷新计价");
        jdbc.update("UPDATE quota_package_label SET settlement_unit_price = ?, catalog_price_version = ? WHERE label_id = ?",
                newPrice, version, labelId);
        return java.util.Optional.of(new PackagePriceRefresh(money(oldAmount), money(newPrice.multiply(quantity)), version));
    }

    public record PackagePriceRefresh(BigDecimal oldAmount, BigDecimal newAmount, long priceVersion) {
        public BigDecimal differenceAmount() { return newAmount.subtract(oldAmount); }
    }

    /** Any generated settlement detail locks the entire package, including partial/mixed-batch settlements. */
    boolean isSettlementLocked(long labelId) {
        List<Long> sources = jdbc.queryForList(
                "SELECT source_id FROM quota_package_label_source WHERE label_id = ? ORDER BY source_id FOR SHARE", Long.class, labelId);
        if (sources.isEmpty()) return true;
        List<Long> traced = jdbc.queryForList("""
                SELECT bill.item_id FROM settlement_bill_item bill
                  JOIN quota_package_label label ON label.trace_code_id = bill.trace_code_id
                 WHERE label.label_id = ? LIMIT 1 FOR SHARE
                """, Long.class, labelId);
        if (!traced.isEmpty()) return true;
        List<Long> consumed = jdbc.queryForList("""
                SELECT bill.item_id FROM settlement_bill_item bill
                  JOIN department_consumption_item item
                    ON bill.source_biz_type = 'department_consumption_item' AND bill.source_biz_id = item.item_id
                  JOIN department_consumption consumption ON consumption.consumption_id = item.consumption_id
                 WHERE consumption.related_biz_type = 'quota_package_label' AND consumption.related_biz_id = ?
                 LIMIT 1 FOR SHARE
                """, Long.class, labelId);
        if (!consumed.isEmpty()) return true;
        List<Long> received = jdbc.queryForList("""
                SELECT bill.item_id FROM quota_package_label_source source
                  JOIN inventory_batch batch ON batch.batch_id = source.batch_id
                  JOIN settlement_bill_item bill ON bill.source_biz_type = 'receiving_order_item'
                                               AND bill.source_biz_id = batch.receiving_item_id
                 WHERE source.label_id = ? LIMIT 1 FOR SHARE
                """, Long.class, labelId);
        return !received.isEmpty();
    }

    /** Freeze a package's current valuation into new settlement rows; never modify existing bills or source costs. */
    public List<Map<String, Object>> settlementRows(String mode, List<Map<String, Object>> rows) {
        rows.forEach(row -> com.hospital.spd.common.QuantityRules.positive((BigDecimal) row.get("quantity"), "结算数量"));
        rows.stream().map(row -> row.get("productId")).filter(Number.class::isInstance)
                .map(value -> ((Number) value).longValue()).distinct().sorted().forEach(this::lockProduct);
        if (!SettlementPointService.DEPARTMENT_CONSUMPTION.equals(mode)) return rows;
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Long, Map<String, Object>> packages = new LinkedHashMap<>();
        Map<Long, Map<Long, BigDecimal>> allocations = new LinkedHashMap<>();
        for (Long traceId : rows.stream().map(row -> row.get("traceCodeId"))
                .filter(Number.class::isInstance).map(value -> ((Number) value).longValue())
                .distinct().sorted().toList()) {
            List<Map<String, Object>> labels = jdbc.queryForList("""
                    SELECT label_id AS labelId, settlement_unit_price AS unitPrice, product_id AS productId
                      FROM quota_package_label WHERE trace_code_id = ? ORDER BY label_id FOR UPDATE
                    """, traceId);
            if (!labels.isEmpty()) packages.put(traceId, labels.get(0));
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            Map<String, Object> label = null;
            if (row.get("traceCodeId") instanceof Number traceId) {
                label = packages.get(traceId.longValue());
            } else if ("department_consumption_item".equals(row.get("sourceBizType"))
                    && row.get("sourceBizId") instanceof Number sourceId) {
                List<Map<String, Object>> legacy = jdbc.queryForList("""
                        SELECT label.label_id AS labelId, label.settlement_unit_price AS unitPrice,
                               label.trace_code_id AS traceCodeId, label.product_id AS productId
                          FROM department_consumption_item item
                          JOIN department_consumption consumption ON consumption.consumption_id = item.consumption_id
                          JOIN quota_package_label label ON consumption.related_biz_type = 'quota_package_label'
                                                       AND consumption.related_biz_id = label.label_id
                                                       AND item.product_id = label.product_id
                         WHERE item.item_id = ? AND label.product_id = ? FOR UPDATE
                        """, sourceId.longValue(), row.get("productId"));
                if (!legacy.isEmpty()) {
                    label = legacy.get(0);
                    if (label.get("traceCodeId") != null) copy.put("traceCodeId", label.get("traceCodeId"));
                }
            }
            if (label != null) copy.put("goodsType", "quota_package");
            if (label != null && label.get("unitPrice") != null) {
                if (label.get("productId") instanceof Number productId
                        && productId.longValue() != ((Number) row.get("productId")).longValue()) {
                    throw new IllegalArgumentException("结算来源商品与定数包不一致");
                }
                BigDecimal unitPrice = decimal(label.get("unitPrice"));
                copy.put("unitPrice", unitPrice);
                long labelId = ((Number) label.get("labelId")).longValue();
                Map<Long, BigDecimal> amounts = allocations.computeIfAbsent(labelId,
                        id -> packageSettlementAmounts(id, unitPrice));
                if (!(row.get("sourceBizId") instanceof Number sourceId) || !amounts.containsKey(sourceId.longValue())) {
                    throw new IllegalArgumentException("套包结算来源不完整或已结算，请刷新后重试");
                }
                copy.put("amount", amounts.get(sourceId.longValue()));
            }
            result.add(copy);
        }
        return result;
    }

    /** Allocate the package total once across all sources, including other suppliers and already billed parts. */
    private Map<Long, BigDecimal> packageSettlementAmounts(long labelId, BigDecimal unitPrice) {
        List<Map<String, Object>> sources = jdbc.queryForList("""
                SELECT item.item_id AS itemId, item.quantity, label.package_quantity AS packageQuantity,
                       bill.amount AS billedAmount
                  FROM department_consumption_item item
                  JOIN department_consumption consumption ON consumption.consumption_id = item.consumption_id
                  JOIN quota_package_label label ON label.label_id = ? AND label.product_id = item.product_id
                  LEFT JOIN settlement_bill_item bill ON bill.source_biz_type = 'department_consumption_item'
                                                     AND bill.source_biz_id = item.item_id
                 WHERE consumption.status = 'confirmed'
                   AND (item.trace_code_id = label.trace_code_id OR
                        (consumption.related_biz_type = 'quota_package_label' AND consumption.related_biz_id = label.label_id))
                 ORDER BY item.item_id FOR UPDATE
                """, labelId);
        if (sources.isEmpty()) throw new IllegalArgumentException("套包缺少有效消耗来源，不能生成结算");
        sources.forEach(row -> {
            com.hospital.spd.common.QuantityRules.positive(decimal(row.get("quantity")), "来源数量");
            com.hospital.spd.common.QuantityRules.positive(decimal(row.get("packageQuantity")), "包内数量");
        });
        BigDecimal quantity = sources.stream().map(row -> decimal(row.get("quantity"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (quantity.compareTo(decimal(sources.get(0).get("packageQuantity"))) != 0) {
            throw new IllegalArgumentException("套包消耗来源数量与包内数量不一致，不能自动分摊结算金额");
        }
        BigDecimal total = money(unitPrice.multiply(quantity));
        BigDecimal billed = sources.stream().filter(row -> row.get("billedAmount") != null)
                .map(row -> decimal(row.get("billedAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> unpaid = sources.stream().filter(row -> row.get("billedAmount") == null).toList();
        BigDecimal unpaidQuantity = unpaid.stream().map(row -> decimal(row.get("quantity"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal budget = total.subtract(billed);
        BigDecimal expected = money(unitPrice.multiply(unpaidQuantity));
        BigDecimal quantum = new BigDecimal("0.0001");
        if (budget.signum() < 0 || budget.subtract(expected).abs().compareTo(quantum.multiply(BigDecimal.valueOf(sources.size()))) > 0) {
            throw new IllegalArgumentException("历史结算金额与套包计价不一致，请通过差额调整流程处理");
        }
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        if (unpaid.isEmpty()) return amounts;
        if (unpaidQuantity.signum() <= 0) throw new IllegalArgumentException("套包消耗数量必须大于零");
        Map<Long, BigDecimal> remainders = new LinkedHashMap<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (Map<String, Object> source : unpaid) {
            long id = ((Number) source.get("itemId")).longValue();
            BigDecimal numerator = budget.multiply(decimal(source.get("quantity")));
            BigDecimal amount = numerator.divide(unpaidQuantity, 4, RoundingMode.DOWN);
            amounts.put(id, amount);
            remainders.put(id, numerator.subtract(amount.multiply(unpaidQuantity)));
            allocated = allocated.add(amount);
        }
        int tailUnits = budget.subtract(allocated).movePointRight(4).intValueExact();
        List<Long> order = amounts.keySet().stream().sorted(
                Comparator.<Long, BigDecimal>comparing(remainders::get).reversed().thenComparingLong(Long::longValue)).toList();
        for (int index = 0; index < tailUnits; index++) {
            long id = order.get(index);
            amounts.put(id, amounts.get(id).add(quantum));
        }
        return amounts;
    }

    private static boolean isTerminal(Object status) {
        return List.of("settled", "unpacked", "cancelled", "terminated", "voided", "void").contains(String.valueOf(status));
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) throw new IllegalStateException("计价数据缺失，不能继续调价或结算");
        return value instanceof BigDecimal number ? number : new BigDecimal(value.toString());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
