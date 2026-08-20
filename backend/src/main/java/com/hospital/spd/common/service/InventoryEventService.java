package com.hospital.spd.common.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;

/**
 * Records immutable inventory events after stock-affecting services complete their balance updates.
 */
@Service
public class InventoryEventService {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentNumberService documentNumberService;
    private final OperatorContextProvider operatorContextProvider;

    public InventoryEventService(JdbcTemplate jdbcTemplate,
                                 DocumentNumberService documentNumberService,
                                 OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentNumberService = documentNumberService;
        this.operatorContextProvider = operatorContextProvider;
    }

    /**
     * Persists an immutable inventory event after the caller has updated stock and computed the balance.
     */
    public Long record(String eventType, String sourceType, Long sourceId, Long warehouseId, Long productId,
                       Long batchId, BigDecimal qtyChange, BigDecimal qtyAfter, String remark) {
        OperatorContext operator = operatorContextProvider.current();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO inventory_event (
                      event_no, event_type, source_biz_type, source_biz_id, warehouse_id,
                      product_id, batch_id, qty_change, qty_after, operator_id, remark
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, documentNumberService.next("KC", 5, "inventory_event", "event_no"));
            ps.setString(2, eventType);
            ps.setString(3, sourceType);
            ps.setLong(4, sourceId);
            ps.setLong(5, warehouseId);
            ps.setLong(6, productId);
            ps.setLong(7, batchId);
            ps.setBigDecimal(8, qtyChange);
            ps.setBigDecimal(9, qtyAfter);
            ps.setLong(10, operator.userId());
            ps.setString(11, remark);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }
}
