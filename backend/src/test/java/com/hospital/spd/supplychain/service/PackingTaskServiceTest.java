package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.service.DocumentKind;
import com.hospital.spd.supplychain.PackageActionRequest;
import com.hospital.spd.supplychain.PackingTaskRequest;
import com.hospital.spd.supplychain.SupplyChainSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PackingTaskServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SupplyChainSupport support;

    private PackingTaskService service;

    @BeforeEach
    void setUp() {
        service = new PackingTaskService(jdbcTemplate, support);
        lenient().when(support.nextNo(any(DocumentKind.class)))
                .thenAnswer(invocation -> ((DocumentKind) invocation.getArgument(0)).prefix() + "20260601001");
    }

    @Test
    void createsTaskAndReservesLooseStock() throws Exception {
        mockTemplate();
        mockWarehouse();
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(available_qty), 0)"),
                eq(BigDecimal.class), anyLong(), anyLong())).thenReturn(BigDecimal.valueOf(100));
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKING_TASK))).thenReturn("DB2026060800001");
        mockGeneratedKey(42L);
        when(support.reserveAvailableFifo(anyLong(), anyLong(), any(BigDecimal.class)))
                .thenReturn(List.of(new SupplyChainSupport.InventoryReservation(
                        50L, 200L, BigDecimal.valueOf(30), BigDecimal.valueOf(5))));

        Map<String, Object> result = service.createTask(
                new PackingTaskRequest("TP001", "MAIN", BigDecimal.valueOf(3), "remark"));

        assertThat(result).containsEntry("taskNo", "DB2026060800001");
        assertThat(result).containsEntry("requestedPackageCount", BigDecimal.valueOf(3));
        assertThat(result).containsEntry("packageCount", BigDecimal.valueOf(3));
        assertThat(result).containsEntry("plannedLooseQty", BigDecimal.valueOf(30));
        verify(support).reserveAvailableFifo(eq(1L), eq(100L), eq(BigDecimal.valueOf(30)));
        verify(support).writeAudit(eq("quota_package"), eq("create_quota_pack_task"), anyLong(), anyString(), anyString());
    }

    @Test
    void createsPartialPackingTaskWhenLooseStockOnlySupportsSomePackages() throws Exception {
        mockTemplate();
        mockWarehouse();
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(available_qty), 0)"),
                eq(BigDecimal.class), anyLong(), anyLong())).thenReturn(BigDecimal.valueOf(15));
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKING_TASK))).thenReturn("DB2026060800003");
        mockGeneratedKey(43L);
        when(support.reserveAvailableFifo(anyLong(), anyLong(), any(BigDecimal.class)))
                .thenReturn(List.of(new SupplyChainSupport.InventoryReservation(
                        51L, 201L, BigDecimal.TEN, BigDecimal.valueOf(5))));

        Map<String, Object> result = service.createTask(
                new PackingTaskRequest("TP001", "MAIN", BigDecimal.valueOf(3), "partial"));

        assertThat(result)
                .containsEntry("requestedPackageCount", BigDecimal.valueOf(3))
                .containsEntry("packageCount", BigDecimal.ONE)
                .containsEntry("plannedLooseQty", BigDecimal.TEN)
                .containsEntry("reservedLooseQty", BigDecimal.TEN);
        verify(support).reserveAvailableFifo(eq(1L), eq(100L), eq(BigDecimal.TEN));
    }

    @Test
    void rejectsTaskCreationWhenLooseStockInsufficient() {
        mockTemplate();
        mockWarehouse();
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(available_qty), 0)"),
                eq(BigDecimal.class), anyLong(), anyLong())).thenReturn(BigDecimal.valueOf(9));

        assertThatThrownBy(() -> service.createTask(
                new PackingTaskRequest("TP001", "MAIN", BigDecimal.valueOf(3), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one package");
    }

    @Test
    void rejectsFractionalPackageCountBecauseEveryPackageNeedsOneUniqueCode() {
        assertThatThrownBy(() -> service.createTask(
                new PackingTaskRequest("TP001", "MAIN", BigDecimal.valueOf(1.5), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole number");
    }

    @Test
    void confirmsTaskConsumesReservationAndGeneratesLabels() throws Exception {
        mockPackingTask("pending_confirm");
        when(jdbcTemplate.queryForList(contains("FROM quota_packing_task_reservation"), anyLong()))
                .thenReturn(List.of(reservation(BigDecimal.valueOf(20))));
        when(jdbcTemplate.queryForMap(contains("WHERE task_id = ?"), anyLong()))
                .thenReturn(Map.of("warehouseId", 1L, "productId", 100L));
        when(support.consumeLocked(anyLong(), anyLong(), anyLong(), anyLong(), any(BigDecimal.class),
                anyString(), anyString(), anyLong(), anyString())).thenReturn(99L);
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_LABEL)))
                .thenReturn("D000001", "D000002");
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_EVENT))).thenReturn("EVT001");
        when(jdbcTemplate.update(contains("SET status = 'confirmed'"), eq("DB2026060800001"))).thenReturn(1);
        mockGeneratedKey(10L);

        Map<String, Object> result = service.confirmTask("DB2026060800001");

        assertThat(result).containsEntry("taskNo", "DB2026060800001");
        assertThat((List<?>) result.get("labels")).hasSize(2);
        verify(support).consumeLocked(eq(50L), eq(1L), eq(100L), eq(200L), eq(BigDecimal.valueOf(20)),
                eq("quota_pack_out"), eq("quota_packing_task"), eq(1L), anyString());
        verify(support).writeAudit(eq("quota_package"), eq("confirm_quota_pack_task"), anyLong(), anyString(), anyString());
    }

    @Test
    void confirmsTenPackagesGeneratesTenUniquePackageCodes() throws Exception {
        mockPackingTask("pending_confirm", BigDecimal.TEN, BigDecimal.valueOf(100), BigDecimal.TEN);
        when(jdbcTemplate.queryForList(contains("FROM quota_packing_task_reservation"), anyLong()))
                .thenReturn(List.of(reservation(BigDecimal.valueOf(100))));
        when(jdbcTemplate.queryForMap(contains("WHERE task_id = ?"), anyLong()))
                .thenReturn(Map.of("warehouseId", 1L, "productId", 100L));
        when(support.consumeLocked(anyLong(), anyLong(), anyLong(), anyLong(), any(BigDecimal.class),
                anyString(), anyString(), anyLong(), anyString())).thenReturn(99L);
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_LABEL)))
                .thenReturn("D20260702000001", "D20260702000002", "D20260702000003", "D20260702000004",
                        "D20260702000005", "D20260702000006", "D20260702000007", "D20260702000008",
                        "D20260702000009", "D20260702000010");
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_EVENT)))
                .thenReturn("EVT001", "EVT002", "EVT003", "EVT004", "EVT005",
                        "EVT006", "EVT007", "EVT008", "EVT009", "EVT010");
        when(jdbcTemplate.update(contains("SET status = 'confirmed'"), eq("DB2026060800001"))).thenReturn(1);
        mockGeneratedKey(10L);

        Map<String, Object> result = service.confirmTask("DB2026060800001");

        List<String> labels = ((List<?>) result.get("labels")).stream()
                .map(String.class::cast)
                .toList();
        assertThat(labels)
                .hasSize(10)
                .doesNotHaveDuplicates()
                .containsExactly("D20260702000001", "D20260702000002", "D20260702000003", "D20260702000004",
                        "D20260702000005", "D20260702000006", "D20260702000007", "D20260702000008",
                        "D20260702000009", "D20260702000010");
        verify(support, times(10)).nextNo(eq(DocumentKind.QUOTA_PACKAGE_LABEL));
        verify(jdbcTemplate, times(10)).update(contains("INSERT INTO quota_package_label_source"),
                anyLong(), eq(200L), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(5)), eq(1L));
    }

    @Test
    void confirmsTaskAllocatesLabelSourcesByBatchFifoInsteadOfAveragingBatches() throws Exception {
        mockPackingTask("pending_confirm");
        when(jdbcTemplate.queryForList(contains("FROM quota_packing_task_reservation"), anyLong()))
                .thenReturn(List.of(
                        reservation(1L, 50L, 200L, BigDecimal.valueOf(5)),
                        reservation(2L, 51L, 201L, BigDecimal.valueOf(15))));
        when(jdbcTemplate.queryForMap(contains("WHERE task_id = ?"), anyLong()))
                .thenReturn(Map.of("warehouseId", 1L, "productId", 100L));
        when(support.consumeLocked(anyLong(), anyLong(), anyLong(), anyLong(), any(BigDecimal.class),
                anyString(), anyString(), anyLong(), anyString())).thenReturn(99L);
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_LABEL)))
                .thenReturn("D000001", "D000002");
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_EVENT))).thenReturn("EVT001", "EVT002");
        when(jdbcTemplate.update(contains("SET status = 'confirmed'"), eq("DB2026060800001"))).thenReturn(1);
        mockGeneratedKey(10L);

        service.confirmTask("DB2026060800001");

        verify(jdbcTemplate).update(contains("INSERT INTO quota_package_label_source"),
                anyLong(), eq(200L), eq(BigDecimal.valueOf(5)), eq(BigDecimal.valueOf(5)), eq(1L));
        verify(jdbcTemplate).update(contains("INSERT INTO quota_package_label_source"),
                anyLong(), eq(201L), eq(BigDecimal.valueOf(5)), eq(BigDecimal.valueOf(5)), eq(1L));
        verify(jdbcTemplate).update(contains("INSERT INTO quota_package_label_source"),
                anyLong(), eq(201L), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(5)), eq(1L));
        verify(jdbcTemplate, never()).update(contains("INSERT INTO quota_package_label_source"),
                anyLong(), eq(200L), eq(BigDecimal.valueOf(2.5)), any(), eq(1L));
    }

    @Test
    void preventsConfirmingNonPendingTask() {
        mockPackingTask("confirmed");

        assertThatThrownBy(() -> service.confirmTask("DB2026060800001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only pending task can be confirmed");
    }

    @Test
    void rejectsConfirmWhenReservedStockIsNotFullyLocked() {
        mockPackingTask("pending_confirm");
        when(jdbcTemplate.queryForList(contains("FROM quota_packing_task_reservation"), anyLong()))
                .thenReturn(List.of(reservation(BigDecimal.valueOf(20), BigDecimal.ZERO)));

        assertThatThrownBy(() -> service.confirmTask("DB2026060800001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not locked");
        verify(support, never()).consumeLocked(anyLong(), anyLong(), anyLong(), anyLong(), any(BigDecimal.class),
                anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void rejectsConfirmWhenReservationDoesNotCoverPlannedQuantity() {
        mockPackingTask("pending_confirm");
        when(jdbcTemplate.queryForList(contains("FROM quota_packing_task_reservation"), anyLong()))
                .thenReturn(List.of(reservation(BigDecimal.TEN)));

        assertThatThrownBy(() -> service.confirmTask("DB2026060800001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reservation is incomplete");
        verify(support, never()).consumeLocked(anyLong(), anyLong(), anyLong(), anyLong(), any(BigDecimal.class),
                anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void cancelsPendingTaskAndReleasesReservations() {
        mockPackingTask("pending_confirm");
        when(jdbcTemplate.queryForList(contains("FROM quota_packing_task_reservation"), anyLong()))
                .thenReturn(List.of(reservation(BigDecimal.TEN)));

        Map<String, Object> result = service.cancelTask("DB2026060800001", new PackageActionRequest("cancel"));

        assertThat(result).containsEntry("status", "cancelled");
        verify(support).releaseLockedToAvailable(eq(50L), eq(BigDecimal.TEN));
        verify(support).writeAudit(eq("quota_package"), eq("cancel_quota_pack_task"), anyLong(), anyString(), anyString());
    }

    @Test
    void terminatesPendingTaskAndReleasesReservations() {
        mockPackingTask("pending_confirm");
        when(jdbcTemplate.queryForList(contains("FROM quota_packing_task_reservation"), anyLong()))
                .thenReturn(List.of(reservation(BigDecimal.valueOf(20))));

        Map<String, Object> result = service.terminateTask("DB2026060800001", new PackageActionRequest("terminate"));

        assertThat(result).containsEntry("status", "terminated");
        assertThat(result).containsEntry("restoredLooseQty", BigDecimal.valueOf(20));
        verify(support).releaseLockedToAvailable(eq(50L), eq(BigDecimal.valueOf(20)));
        verify(support).writeAudit(eq("quota_package"), eq("terminate_quota_pack_task"), anyLong(), anyString(), anyString());
    }

    @Test
    void terminatesConfirmedTaskAndRestoresLabelSourcesToLooseStock() {
        mockPackingTask("confirmed");
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql.contains("FROM quota_package_label") && sql.contains("WHERE task_id")),
                anyLong()))
                .thenReturn(List.of(
                        Map.of("labelId", 10L, "labelNo", "D000001", "status", "pending_print",
                                "packageQuantity", BigDecimal.TEN),
                        Map.of("labelId", 11L, "labelNo", "D000002", "status", "available",
                                "packageQuantity", BigDecimal.TEN)
                ));
        when(jdbcTemplate.queryForList(contains("FROM quota_package_label_source"), anyLong()))
                .thenReturn(
                        List.of(Map.of("batchId", 200L, "sourceQty", BigDecimal.TEN, "warehouseId", 1L)),
                        List.of(Map.of("batchId", 201L, "sourceQty", BigDecimal.TEN, "warehouseId", 1L))
                );
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_EVENT))).thenReturn("EVT001", "EVT002");

        Map<String, Object> result = service.terminateTask("DB2026060800001", new PackageActionRequest("terminate"));

        assertThat(result).containsEntry("status", "terminated");
        assertThat(result).containsEntry("restoredLooseQty", BigDecimal.valueOf(20));
        verify(support).receiveAvailable(eq(1L), eq(100L), eq(200L), eq(BigDecimal.TEN),
                eq("quota_terminate_in"), eq("quota_packing_task"), eq(1L), eq("terminate"));
        verify(support).receiveAvailable(eq(1L), eq(100L), eq(201L), eq(BigDecimal.TEN),
                eq("quota_terminate_in"), eq("quota_packing_task"), eq(1L), eq("terminate"));
        verify(support).writeAudit(eq("quota_package"), eq("terminate_quota_pack_task"), anyLong(), anyString(), anyString());
    }

    @Test
    void recalculatesPendingTaskReservation() {
        mockPackingTask("pending_confirm");
        when(jdbcTemplate.queryForList(contains("FROM quota_packing_task_reservation"), anyLong())).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(available_qty), 0)"),
                eq(BigDecimal.class), anyLong(), anyLong())).thenReturn(BigDecimal.valueOf(100));
        when(support.reserveAvailableFifo(anyLong(), anyLong(), any(BigDecimal.class)))
                .thenReturn(List.of(new SupplyChainSupport.InventoryReservation(
                        50L, 200L, BigDecimal.valueOf(20), BigDecimal.valueOf(5))));

        Map<String, Object> result = service.recalculateTask("DB2026060800001");

        assertThat(result).containsEntry("reservedLooseQty", BigDecimal.valueOf(20));
        verify(support).reserveAvailableFifo(eq(1L), eq(100L), eq(BigDecimal.valueOf(20)));
        verify(support).writeAudit(eq("quota_package"), eq("recalculate_quota_pack_task"), anyLong(), anyString(), anyString());
    }

    @Test
    void returnsTaskReservations() {
        when(jdbcTemplate.queryForList(contains("SELECT task_id FROM quota_packing_task"), eq(Long.class), anyString()))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.queryForList(contains("FROM quota_packing_task_reservation"), anyLong()))
                .thenReturn(List.of(Map.of("reservationId", 1L, "reservedQty", BigDecimal.TEN)));

        List<Map<String, Object>> result = service.taskReservations("DB2026060800001");

        assertThat(result).hasSize(1);
    }

    @Test
    void excludesConsumedAndSettledPackagesFromLabelWorklist() {
        service.labels(Map.of());

        List<String> sqlCalls = mockingDetails(jdbcTemplate).getInvocations().stream()
                .map(invocation -> String.valueOf(invocation.getRawArguments()[0]))
                .toList();
        assertThat(sqlCalls)
                .hasSize(2)
                .allMatch(sql -> sql.contains("qpl.status NOT IN ('consumed', 'settled')"));
    }

    @Test
    void unpacksAvailableLabelBackToLooseStock() throws Exception {
        when(jdbcTemplate.queryForMap(contains("WHERE label_no = ?"), anyString()))
                .thenReturn(Map.of("labelId", 10L, "status", "available", "warehouseId", 1L,
                        "productId", 100L, "packageQuantity", BigDecimal.TEN));
        when(jdbcTemplate.queryForList(contains("FROM quota_package_label_source"), anyLong()))
                .thenReturn(List.of(Map.of("batchId", 200L, "sourceQty", BigDecimal.TEN, "warehouseId", 1L)));
        when(support.receiveAvailable(anyLong(), anyLong(), anyLong(), any(BigDecimal.class),
                anyString(), anyString(), anyLong(), anyString())).thenReturn(99L);
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_EVENT))).thenReturn("EVT001");

        Map<String, Object> result = service.unpack("D000001", null);

        assertThat(result)
                .containsEntry("status", "void")
                .containsEntry("restoredLooseQty", BigDecimal.TEN);
        verify(jdbcTemplate).queryForMap(contains("FOR UPDATE"), eq("D000001"));
        verify(support).receiveAvailable(eq(1L), eq(100L), eq(200L), eq(BigDecimal.TEN),
                eq("quota_unpack_in"), eq("quota_package_label"), eq(10L), anyString());
        verify(jdbcTemplate).update(contains("status = 'available'"), eq(10L));
        verify(support).writeAudit(eq("quota_package"), eq("unpack_quota_label"), anyLong(), anyString(), anyString());
    }

    @Test
    void printsPendingLabelAndMakesItAvailable() {
        when(jdbcTemplate.queryForMap(contains("WHERE label_no = ?"), anyString()))
                .thenReturn(Map.of("labelId", 10L, "status", "pending_print",
                        "packageQuantity", BigDecimal.TEN, "printCount", 0));
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_EVENT))).thenReturn("EVT001");

        Map<String, Object> result = service.printLabel("D000001");

        assertThat(result)
                .containsEntry("labelNo", "D000001")
                .containsEntry("status", "available")
                .containsEntry("printCount", 1);
        verify(jdbcTemplate).queryForMap(contains("FOR UPDATE"), eq("D000001"));
        verify(jdbcTemplate).update(contains("SET status = 'available', print_count = print_count + 1"),
                eq(10L));
        verify(support).writeAudit(eq("quota_package"), eq("print_quota_label"), eq(10L), eq("D000001"), anyString());
    }

    @Test
    void reprintsAvailableLabelAndIncrementsPrintCount() {
        when(jdbcTemplate.queryForMap(contains("WHERE label_no = ?"), anyString()))
                .thenReturn(Map.of("labelId", 10L, "status", "available",
                        "packageQuantity", BigDecimal.TEN, "printCount", 2));
        when(support.nextNo(eq(DocumentKind.QUOTA_PACKAGE_EVENT))).thenReturn("EVT001");

        Map<String, Object> result = service.printLabel("D000001");

        assertThat(result)
                .containsEntry("status", "available")
                .containsEntry("printCount", 3);
        verify(jdbcTemplate).queryForMap(contains("FOR UPDATE"), eq("D000001"));
        verify(jdbcTemplate).update(contains("print_count = print_count + 1"), eq(10L));
    }

    @Test
    void rejectsPrintingVoidedLabel() {
        when(jdbcTemplate.queryForMap(contains("WHERE label_no = ?"), anyString()))
                .thenReturn(Map.of("labelId", 10L, "status", "void",
                        "packageQuantity", BigDecimal.TEN, "printCount", 1));

        assertThatThrownBy(() -> service.printLabel("D000001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can be printed");
        verify(jdbcTemplate, never()).update(contains("print_count = print_count + 1"), anyLong());
    }

    private void mockTemplate() {
        when(jdbcTemplate.queryForList(contains("FROM quota_package_template"), anyString()))
                .thenReturn(List.of(Map.of(
                        "templateId", 10L,
                        "templateCode", "TP001",
                        "templateName", "Template",
                        "productId", 100L,
                        "quantity", BigDecimal.TEN,
                        "unit", "piece"
                )));
    }

    private void mockWarehouse() {
        when(jdbcTemplate.queryForList(contains("FROM warehouse"), eq(Long.class), anyString()))
                .thenReturn(List.of(1L));
    }

    private void mockPackingTask(String status) {
        mockPackingTask(status, BigDecimal.valueOf(2), BigDecimal.valueOf(20), BigDecimal.TEN);
    }

    private void mockPackingTask(String status, BigDecimal packageCount, BigDecimal plannedLooseQty,
                                 BigDecimal packageQuantity) {
        when(jdbcTemplate.queryForList(contains("WHERE task_no = ?"), anyString()))
                .thenReturn(List.of(Map.of(
                        "taskId", 1L,
                        "taskNo", "DB2026060800001",
                        "status", status,
                        "templateId", 10L,
                        "warehouseId", 1L,
                        "productId", 100L,
                        "packageCount", packageCount,
                        "packageQuantity", packageQuantity,
                        "plannedLooseQty", plannedLooseQty,
                        "reservedLooseQty", plannedLooseQty
                )));
    }

    private Map<String, Object> reservation(BigDecimal qty) {
        return reservation(qty, qty);
    }

    private Map<String, Object> reservation(BigDecimal qty, BigDecimal lockedQty) {
        return reservation(1L, 50L, 200L, qty, lockedQty);
    }

    private Map<String, Object> reservation(Long reservationId, Long balanceId, Long batchId, BigDecimal qty) {
        return reservation(reservationId, balanceId, batchId, qty, qty);
    }

    private Map<String, Object> reservation(Long reservationId, Long balanceId, Long batchId,
                                            BigDecimal qty, BigDecimal lockedQty) {
        return Map.of(
                "reservationId", reservationId,
                "balanceId", balanceId,
                "batchId", batchId,
                "reservedQty", qty,
                "deductQty", qty,
                "lockedQty", lockedQty,
                "unitPrice", BigDecimal.valueOf(5)
        );
    }

    private void mockGeneratedKey(Long key) throws Exception {
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            Field keyListField = GeneratedKeyHolder.class.getDeclaredField("keyList");
            keyListField.setAccessible(true);
            keyListField.set(keyHolder, List.of(Map.of("GENERATED_KEY", key)));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }
}
