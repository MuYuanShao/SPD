package com.hospital.spd.mobile;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Defines the first mobile capability: whole-delivery signing with explicit physical checks. */
public final class MobileContracts {
    private MobileContracts() {}

    public record Context(String serverId, Long userId, String username,
                          List<String> capabilities, List<Warehouse> warehouses) {}
    public record Warehouse(Long deptId, String deptName, Long warehouseId, String warehouseName) {}
    public record Task(Long id, String documentNo, String status, String deliveryType,
                       Long deptId, String deptName, Long sourceWarehouseId, String sourceWarehouseName,
                       Long warehouseId, String warehouseName, String productCode, String productName,
                       BigDecimal quantity, String unit) {}
    public record CheckItem(String type, Long id, String code, String alternateCode, String status) {}
    public record Detail(Task task, List<CheckItem> items) {}
    public record Scan(@NotNull @Positive Long taskId, @NotNull @Positive Long deptId,
                       @NotNull @Positive Long warehouseId, @NotBlank @Size(max = 2048) String rawCode) {}
    public record SignOperation(@NotNull UUID operationId, @NotBlank @Size(max = 128) String deviceId,
                                @NotBlank @Pattern(regexp = "SIGN_DELIVERY") String kind,
                                @NotNull @Positive Long taskId, @NotNull @Positive Long deptId,
                                @NotNull @Positive Long warehouseId,
                                @NotNull @Size(max = 1000) List<@NotNull @Positive Long> packageIds,
                                @NotNull @Size(max = 1000) List<@NotNull @Positive Long> traceCodeIds,
                                boolean looseConfirmed,
                                @Size(max = 256) String reviewHash) {}
    public record Review(String reviewHash, Instant expiresAt, Detail summary) {}
    public record Result(UUID operationId, String kind, Long taskId, String documentNo,
                         String status, Long userId, String deviceId, Instant completedAt, String message) {}
}
