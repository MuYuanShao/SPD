package com.hospital.spd.common.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Centralizes durable audit logging for business services after validation and state changes.
 */
@Service
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;

    public AuditLogService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, OperatorContext::system);
    }

    @Autowired
    public AuditLogService(JdbcTemplate jdbcTemplate, OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
    }

    /**
     * Records a normalized audit row for business services that already completed validation.
     */
    public void record(String bizType, String operationType, Long bizId, String bizNo, String remark) {
        OperatorContext operator = operatorContextProvider.current();
        jdbcTemplate.update("""
                INSERT INTO audit_log (operator_name, operation_type, biz_type, biz_id, after_data, ip_address, remark)
                VALUES (?, ?, ?, ?, JSON_OBJECT('bizNo', ?), ?, ?)
                """, operator.username(), operationType, bizType, bizId, bizNo, operator.ipAddress(), remark);
    }
}
