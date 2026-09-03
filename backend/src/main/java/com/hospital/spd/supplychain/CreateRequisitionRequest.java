package com.hospital.spd.supplychain;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validated mixed-item department requisition request with one-release legacy field compatibility. */
public record CreateRequisitionRequest(
        String deptCode,
        String deptName,
        @JsonAlias("warehouseName") String destinationWarehouseName,
        Long destinationWarehouseId,
        Long sourceWarehouseId,
        List<@Valid RequisitionItemRequest> items,
        String productCode,
        @Positive BigDecimal quantity,
        String requisitionMode,
        String templateCode,
        @Positive BigDecimal packageCount,
        List<String> uniqueCodes,
        String uniqueCode
) {
    public CreateRequisitionRequest(String deptName, String destinationWarehouseName,
                                    Long destinationWarehouseId, Long sourceWarehouseId,
                                    List<RequisitionItemRequest> items, String productCode,
                                    BigDecimal quantity, String requisitionMode,
                                    List<String> uniqueCodes, String uniqueCode) {
        this(null, deptName, destinationWarehouseName, destinationWarehouseId, sourceWarehouseId,
                items, productCode, quantity, requisitionMode, null, null, uniqueCodes, uniqueCode);
    }

    @AssertTrue(message = "items must be non-empty, or legacy productCode and quantity must be provided")
    public boolean hasStructuredOrLegacyItems() {
        return (items != null && !items.isEmpty())
                || (productCode != null && !productCode.isBlank() && quantity != null && quantity.signum() > 0);
    }

    @AssertTrue(message = "deptCode or deptName is required")
    public boolean hasDepartmentIdentity() {
        return (deptCode != null && !deptCode.isBlank()) || (deptName != null && !deptName.isBlank());
    }

    public Map<String, Object> toCompatibilityMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (deptCode != null) body.put("deptCode", deptCode);
        body.put("deptName", deptName);
        if (destinationWarehouseName != null) body.put("warehouseName", destinationWarehouseName);
        if (destinationWarehouseId != null) body.put("destinationWarehouseId", destinationWarehouseId);
        if (sourceWarehouseId != null) body.put("sourceWarehouseId", sourceWarehouseId);
        if (items != null && !items.isEmpty()) {
            body.put("items", items.stream().map(RequisitionItemRequest::toMap).toList());
        } else {
            body.put("productCode", productCode);
            body.put("quantity", quantity);
            if (requisitionMode != null) body.put("requisitionMode", requisitionMode);
            if (templateCode != null) body.put("templateCode", templateCode);
            if (packageCount != null) body.put("packageCount", packageCount);
            if (uniqueCodes != null) body.put("uniqueCodes", uniqueCodes);
            else if (uniqueCode != null) body.put("uniqueCode", uniqueCode);
        }
        return body;
    }

    public record RequisitionItemRequest(
            @NotBlank String productCode,
            @Positive BigDecimal quantity,
            String requisitionMode,
            String templateCode,
            @Positive BigDecimal packageCount,
            List<String> uniqueCodes,
            String uniqueCode
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productCode", productCode);
            item.put("quantity", quantity);
            if (requisitionMode != null) item.put("requisitionMode", requisitionMode);
            if (templateCode != null) item.put("templateCode", templateCode);
            if (packageCount != null) item.put("packageCount", packageCount);
            if (uniqueCodes != null) item.put("uniqueCodes", uniqueCodes);
            else if (uniqueCode != null) item.put("uniqueCode", uniqueCode);
            return item;
        }
    }
}
