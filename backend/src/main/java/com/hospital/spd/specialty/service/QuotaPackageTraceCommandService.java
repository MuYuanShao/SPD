package com.hospital.spd.specialty.service;

import com.hospital.spd.supplychain.service.OperationalDeliveryModule;
import com.hospital.spd.supplychain.service.QuotaPermissionGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Owns scan-driven quota-package signing and consumption commands across trace and delivery modules.
 */
@Service
public class QuotaPackageTraceCommandService {

    private final QuotaPackageTraceFlowService traceFlowService;
    private final OperationalDeliveryModule deliveryModule;
    private final QuotaPermissionGuard permissionGuard;

    public QuotaPackageTraceCommandService(QuotaPackageTraceFlowService traceFlowService,
                                           OperationalDeliveryModule deliveryModule) {
        this(traceFlowService, deliveryModule, null);
    }

    @Autowired
    public QuotaPackageTraceCommandService(QuotaPackageTraceFlowService traceFlowService,
                                           OperationalDeliveryModule deliveryModule,
                                           QuotaPermissionGuard permissionGuard) {
        this.traceFlowService = traceFlowService;
        this.deliveryModule = deliveryModule;
        this.permissionGuard = permissionGuard;
    }

    @Transactional
    public Map<String, Object> sign(Map<String, Object> body) {
        if (permissionGuard != null) permissionGuard.require("quota-package-label:sign");
        String code = required(body, "code");
        Map<String, Object> target = traceFlowService.signTarget(code);
        String status = String.valueOf(target.get("status"));
        String deliveryNo = String.valueOf(target.get("deliveryNo"));
        if ("delivered".equals(status)) {
            deliveryModule.signDelivery(deliveryNo);
        } else if (!"signed".equals(status)) {
            throw new IllegalArgumentException("只有已配送的定数包才能签收入库");
        }
        Long labelId = ((Number) target.get("labelId")).longValue();
        traceFlowService.completeSign(labelId, deliveryNo);
        return Map.of("code", code, "deliveryNo", deliveryNo, "status", "signed");
    }

    @Transactional
    public Map<String, Object> consume(Map<String, Object> body) {
        if (permissionGuard != null) permissionGuard.require("quota-package-label:consume");
        return traceFlowService.consumeByCode(body);
    }

    private static String required(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return String.valueOf(value).trim();
    }
}
