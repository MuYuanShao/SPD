package com.hospital.spd.mobile;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Defines mobile commands and authoritative review snapshots for signing, picking and package consumption. */
public final class MobileContracts {
    private MobileContracts() {}
    public interface Command {
        UUID operationId(); String deviceId(); String kind(); Long taskId(); Long deptId(); Long warehouseId();
    }

    public record Context(String serverId, Long userId, String username,
                          List<String> capabilities, List<Warehouse> warehouses, List<Warehouse> consumptionWarehouses) {
        public Context(String serverId, Long userId, String username,List<String> capabilities,List<Warehouse> warehouses) {
            this(serverId,userId,username,capabilities,warehouses,List.of());
        }
    }
    public record Warehouse(Long deptId, String deptName, Long warehouseId, String warehouseName) {}
    public record Task(Long id, String documentNo, String status, String deliveryType,
                       Long deptId, String deptName, Long sourceWarehouseId, String sourceWarehouseName,
                       Long warehouseId, String warehouseName, String productCode, String productName,
                       BigDecimal quantity, String unit, String requisitionNo, String specModel,
                       String manufacturerName, java.time.LocalDateTime createdAt, java.time.LocalDateTime signedAt) {
        public Task(Long id, String documentNo, String status, String deliveryType, Long deptId, String deptName,
                    Long sourceWarehouseId, String sourceWarehouseName, Long warehouseId, String warehouseName,
                    String productCode, String productName, BigDecimal quantity, String unit) {
            this(id,documentNo,status,deliveryType,deptId,deptName,sourceWarehouseId,sourceWarehouseName,
                    warehouseId,warehouseName,productCode,productName,quantity,unit,null,null,null,null,null);
        }
    }
    public record CheckItem(String type, Long id, String code, String alternateCode, String status) {}
    public record DeliveryBatch(Long batchId, String systemBatchNo, String productionBatchNo,
                                java.time.LocalDate productionDate, java.time.LocalDate expireDate, BigDecimal quantity) {}
    public record Detail(Task task, List<CheckItem> items, List<DeliveryBatch> batches) {
        public Detail(Task task, List<CheckItem> items) { this(task,items,List.of()); }
    }
    public record Scan(@NotNull @Positive Long taskId, @NotNull @Positive Long deptId,
                       @NotNull @Positive Long warehouseId, @NotBlank @Size(max = 2048) String rawCode) {}
    public record SignOperation(@NotNull UUID operationId, @NotBlank @Size(max = 128) String deviceId,
                                @NotBlank @Pattern(regexp = "SIGN_DELIVERY") String kind,
                                @NotNull @Positive Long taskId, @NotNull @Positive Long deptId,
                                @NotNull @Positive Long warehouseId,
                                @NotNull @Size(max = 1000) List<@NotNull @Positive Long> packageIds,
                                @NotNull @Size(max = 1000) List<@NotNull @Positive Long> traceCodeIds,
                                boolean looseConfirmed,
                                @Size(max = 256) String reviewHash) implements Command {}
    public record Review(String reviewHash, Instant expiresAt, Detail summary) {}
    public record Recovery<T>(Result result, String reviewHash, Instant expiresAt, T summary) {}
    public record Result(UUID operationId, String kind, Long taskId, String documentNo,
                         String status, Long userId, String deviceId, Instant completedAt, String message,
                         BigDecimal remainingQuantity,BigDecimal quantity,BigDecimal amount) {
        public Result(UUID operationId, String kind, Long taskId, String documentNo, String status,
                      Long userId, String deviceId, Instant completedAt, String message) {
            this(operationId, kind, taskId, documentNo, status, userId, deviceId, completedAt, message, null,null,null);
        }
        public Result(UUID operationId,String kind,Long taskId,String documentNo,String status,Long userId,
                      String deviceId,Instant completedAt,String message,BigDecimal remainingQuantity) {
            this(operationId,kind,taskId,documentNo,status,userId,deviceId,completedAt,message,remainingQuantity,null,null);
        }
    }
    public record PickTask(Long id, String documentNo, String status, String kind, Long deptId, String deptName,
                           Long sourceWarehouseId, String sourceWarehouseName, Long warehouseId, String warehouseName,
                           Long productId, String productCode, String productName, String unit,
                           BigDecimal quantity, BigDecimal pickedQuantity, BigDecimal remainingQuantity,
                           BigDecimal packageQuantity, String packageUnit,
                           String specModel, String manufacturerName, String supplierName, Long requisitionId) {}
    public record PickOrder(Long id,String documentNo,String status,Long deptId,String deptName,
                            Long sourceWarehouseId,String sourceWarehouseName,Long warehouseId,String warehouseName,long pendingLineCount) {}
    public record PickOrderDetail(PickOrder order,List<PickTask> items) {}
    public record OrderScan(@NotNull @Positive Long deptId,@NotNull @Positive Long warehouseId,@NotBlank @Size(max=2048) String rawCode) {}
    public record PickOrderMatch(PickTask task,PickUnit unit) {}
    public record PickOrderScan(List<PickOrderMatch> matches) {}
    public record PickUnit(String type, Long id, String code, String status, BigDecimal quantity) {}
    public record PickSnapshot(PickTask task, List<PickUnit> units, BigDecimal quantity, BigDecimal availableQuantity) {}
    public record PickReview(String reviewHash, Instant expiresAt, PickSnapshot summary) {}
    public record PickScan(@NotNull @Positive Long taskId, @NotNull @Positive Long deptId,
                           @NotNull @Positive Long warehouseId, @NotBlank @Size(max = 2048) String rawCode) {}
    public record PickOperation(@NotNull UUID operationId, @NotBlank @Size(max = 128) String deviceId,
                                @NotBlank @Pattern(regexp = "PICK_LOOSE|PICK_PACKAGE|PICK_HIGH_VALUE") String kind,
                                @NotNull @Positive Long taskId, @NotNull @Positive Long deptId,
                                @NotNull @Positive Long warehouseId, @NotNull @Positive Long sourceWarehouseId,
                                @Digits(integer = 14, fraction = 4) BigDecimal quantity,
                                @Size(max = 2048) String rawCode,
                                @NotNull @Size(max = 1000) List<@NotNull @Positive Long> packageIds,
                                @NotNull @Size(max = 1000) List<@NotNull @Positive Long> traceCodeIds,
                                @Size(max = 256) String reviewHash) implements Command {}
    public record ConsumeScan(@NotNull @Positive Long deptId,@NotNull @Positive Long warehouseId,
                              @NotBlank @Size(max=2048) String rawCode) {}
    public record ConsumeOperation(@NotNull UUID operationId,@NotBlank @Size(max=128) String deviceId,
                                   @NotBlank @Pattern(regexp="CONSUME_PACKAGE") String kind,
                                   @NotNull @Positive Long taskId,@NotNull @Positive Long deptId,@NotNull @Positive Long warehouseId,
                                   @NotBlank @Size(max=2048) String rawCode,@AssertTrue boolean wholePackageConfirmed,
                                   @Null BigDecimal quantity,@Null BigDecimal amount,@Size(max=256) String reviewHash) implements Command {}
    public record ConsumePack(Long id,String labelNo,Long traceCodeId,Long productId,String productCode,String productName,
                              String unit,BigDecimal quantity,Long deptId,String deptName,Long warehouseId,String warehouseName,
                              String status,Integer version) {}
    public record ConsumeBatch(Long batchId,BigDecimal quantity,BigDecimal unitPrice,String expireDate) {}
    public record ConsumeSnapshot(ConsumePack pack,List<ConsumeBatch> batches) {}
    public record ConsumeReview(String reviewHash,Instant expiresAt,ConsumeSnapshot summary) {}
}
