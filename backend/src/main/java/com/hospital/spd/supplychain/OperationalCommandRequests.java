package com.hospital.spd.supplychain;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validated request records for operational write commands. */
public final class OperationalCommandRequests {
    private OperationalCommandRequests() {}

    public interface CompatibleRequest {
        Map<String, Object> toCompatibilityMap();
    }

    public record LoosePickingRequest(@NotBlank String requisitionNo, @NotNull @Positive Long itemId,
                                      @NotBlank String warehouseName, @NotNull @Positive @com.hospital.spd.common.WholeQuantity BigDecimal quantity)
            implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("requisitionNo", requisitionNo, "itemId", itemId, "warehouseName", warehouseName, "quantity", quantity); }
    }

    public record ShortageRequest(@NotBlank String deptName, @NotBlank String productCode,
                                  @NotNull @Positive @com.hospital.spd.common.WholeQuantity BigDecimal minQty, @PositiveOrZero @com.hospital.spd.common.WholeQuantity BigDecimal currentQty,
                                  @PositiveOrZero @com.hospital.spd.common.WholeQuantity BigDecimal replenishQty, @Positive Integer replenishmentDays)
            implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("deptName", deptName, "productCode", productCode, "minQty", minQty, "currentQty", currentQty, "replenishQty", replenishQty, "replenishmentDays", replenishmentDays); }
    }

    public record SmartAnalysisRequest(String deptName, String warehouseName, @Positive Integer selectedPeriodDays)
            implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("deptName", deptName, "warehouseName", warehouseName, "selectedPeriodDays", selectedPeriodDays); }
    }

    public record RequisitionActionRequest(@NotBlank @Pattern(regexp = "approve|reject") String action)
            implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("action", action); }
    }

    public record DeliveryRequest(String requisitionNo, @NotBlank String deptName, @NotBlank String warehouseName,
                                  @NotBlank String productCode, @NotNull @Positive @com.hospital.spd.common.WholeQuantity BigDecimal quantity,
                                  List<String> uniqueCodes, String uniqueCode) implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("requisitionNo", requisitionNo, "deptName", deptName, "warehouseName", warehouseName, "productCode", productCode, "quantity", quantity, "uniqueCodes", uniqueCodes, "uniqueCode", uniqueCode); }
    }

    public record PackagePickingRequest(@NotBlank String requisitionNo, @NotNull @Positive Long itemId,
                                        @NotBlank String warehouseName, @NotEmpty List<@NotBlank String> labelNos)
            implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("requisitionNo", requisitionNo, "itemId", itemId, "warehouseName", warehouseName, "labelNos", labelNos); }
    }

    public record ConsumptionRequest(@NotBlank(message = "科室名称不能为空") String deptName,
                                     @NotBlank(message = "库房名称不能为空") String warehouseName,
                                     @NotBlank(message = "商品编码不能为空") String productCode,
                                     @NotNull(message = "消耗数量不能为空") @Positive(message = "消耗数量必须大于零") @com.hospital.spd.common.WholeQuantity BigDecimal quantity,
                                     String deptCode, Long warehouseId)
            implements CompatibleRequest {
        public ConsumptionRequest(String deptName, String warehouseName, String productCode, @com.hospital.spd.common.WholeQuantity BigDecimal quantity) {
            this(deptName, warehouseName, productCode, quantity, null, null);
        }
        public Map<String, Object> toCompatibilityMap() { return map("deptName", deptName, "warehouseName", warehouseName, "productCode", productCode, "quantity", quantity, "deptCode", deptCode, "warehouseId", warehouseId); }
    }

    public record PdaUploadRequest(String deviceNo, String operationType) implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("deviceNo", deviceNo, "operationType", operationType); }
    }

    public record ColdChainExceptionRequest(@NotBlank String productCode, @NotBlank String warehouseName,
                                            @NotNull BigDecimal temperature, @NotBlank String severity)
            implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("productCode", productCode, "warehouseName", warehouseName, "temperature", temperature, "severity", severity); }
    }

    public record RecallRequest(String scope, String warehouseName, @NotBlank String productCode,
                                @NotBlank String batchNo, @NotBlank String reason,
                                @Pattern(regexp = "recall|isolate") String businessType) implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("scope", scope, "warehouseName", warehouseName, "productCode", productCode, "batchNo", batchNo, "reason", reason, "businessType", businessType == null ? "recall" : businessType); }
    }

    public record HighValueChargeRequest(@NotBlank String deptName, @NotBlank String patientNo,
                                         @NotBlank String productCode, @NotNull @Positive @com.hospital.spd.common.WholeQuantity BigDecimal quantity)
            implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("deptName", deptName, "patientNo", patientNo, "productCode", productCode, "quantity", quantity); }
    }

    public record HighValuePatientBindingRequest(@NotBlank String uniqueCode, @NotBlank String patientNo,
                                                 String deptName, String roomName, String patientNameMasked)
            implements CompatibleRequest {
        public Map<String, Object> toCompatibilityMap() { return map("uniqueCode", uniqueCode, "patientNo", patientNo, "deptName", deptName, "roomName", roomName, "patientNameMasked", patientNameMasked); }
    }

    public record HighValueBillingCallbackRequest(String externalChargeNo, String eventId, String sourceSystem,
                                                  String operationNo, String udiCode, String uniqueCode,
                                                  String productCode, @NotNull(message = "计费数量不能为空")
                                                  @Positive(message = "计费数量必须大于零") @com.hospital.spd.common.WholeQuantity BigDecimal quantity,
                                                  @PositiveOrZero(message = "计费金额不能为负数") BigDecimal amount, String deptName,
                                                  String patientNo, String patientNameMasked,
                                                  String roomName, String warehouseName, String operatorName)
            implements CompatibleRequest {
        @AssertTrue(message = "外部计费流水号或事件号不能为空")
        public boolean isSourceEventPresent() {
            return notBlank(externalChargeNo) || notBlank(eventId);
        }

        @AssertTrue(message = "唯一码或UDI不能为空")
        public boolean isTraceCodePresent() {
            return notBlank(uniqueCode) || notBlank(udiCode);
        }

        public Map<String, Object> toCompatibilityMap() {
            return map("externalChargeNo", externalChargeNo, "eventId", eventId, "sourceSystem", sourceSystem,
                    "operationNo", operationNo, "udiCode", udiCode, "uniqueCode", uniqueCode,
                    "productCode", productCode, "quantity", quantity, "amount", amount, "deptName", deptName,
                    "patientNo", patientNo, "patientNameMasked", patientNameMasked, "roomName", roomName,
                    "warehouseName", warehouseName, "operatorName", operatorName);
        }
    }

    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            if (values[index + 1] != null) result.put((String) values[index], values[index + 1]);
        }
        return result;
    }
}
