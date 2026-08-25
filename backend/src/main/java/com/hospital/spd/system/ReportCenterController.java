package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.RegulatoryReportCenterService;
import com.hospital.spd.system.service.SpdHisReconciliationReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Exposes regulatory report-center reads and audited exports. */
@RestController
@RequestMapping("/report-center")
public class ReportCenterController {
    private final SpdHisReconciliationReportService reconciliationReportService;
    private final RegulatoryReportCenterService regulatoryReportService;

    public ReportCenterController(SpdHisReconciliationReportService reconciliationReportService,
                                  RegulatoryReportCenterService regulatoryReportService) {
        this.reconciliationReportService = reconciliationReportService;
        this.regulatoryReportService = regulatoryReportService;
    }

    @GetMapping("/spd-his-reconciliation")
    public ApiResponse<Map<String, Object>> reconciliation(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(reconciliationReportService.list(params));
    }

    @GetMapping("/spd-his-reconciliation/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@RequestParam Map<String, String> params) {
        byte[] workbook = reconciliationReportService.exportExcel(params);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("SPD-HIS收费核对对账报表.xlsx", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }

    @GetMapping("/spd-his-reconciliation/export-audit")
    public ApiResponse<Map<String, Object>> recordExport(@RequestParam(defaultValue = "PDF") String format) {
        reconciliationReportService.recordExport(format);
        return ApiResponse.ok(Map.of("recorded", true, "format", format.toUpperCase()));
    }

    @GetMapping("/supplier-delivery-ledger")
    public ApiResponse<Map<String, Object>> supplierDeliveryLedger(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(regulatoryReportService.supplierDeliveryLedger(params));
    }

    @GetMapping("/supplier-delivery-ledger/export.xlsx")
    public ResponseEntity<byte[]> exportSupplierDeliveryLedger(@RequestParam Map<String, String> params) {
        return excel(regulatoryReportService.exportSupplierDeliveryLedger(params), "供应商供货明细台账.xlsx");
    }

    @GetMapping("/centralized-procurement-progress")
    public ApiResponse<Map<String, Object>> centralizedProcurementProgress(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(regulatoryReportService.centralizedProcurementProgress(params));
    }

    @GetMapping("/centralized-procurement-progress/export.xlsx")
    public ResponseEntity<byte[]> exportCentralizedProcurementProgress(@RequestParam Map<String, String> params) {
        return excel(regulatoryReportService.exportCentralizedProcurementProgress(params), "集采执行进度报表.xlsx");
    }

    @GetMapping("/inventory-movement-summary")
    public ApiResponse<Map<String, Object>> inventoryMovementSummary(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(regulatoryReportService.inventoryMovementSummary(params));
    }

    @GetMapping("/inventory-movement-summary/export.xlsx")
    public ResponseEntity<byte[]> exportInventoryMovementSummary(@RequestParam Map<String, String> params) {
        return excel(regulatoryReportService.exportInventoryMovementSummary(params), "全院物资进销存汇总表.xlsx");
    }

    @GetMapping("/{reportCode}/export-audit")
    public ApiResponse<Map<String, Object>> recordRegulatoryExport(@org.springframework.web.bind.annotation.PathVariable String reportCode,
                                                                   @RequestParam(defaultValue = "PDF") String format) {
        regulatoryReportService.recordExport(reportCode, format);
        return ApiResponse.ok(Map.of("recorded", true, "format", format.toUpperCase()));
    }

    private static ResponseEntity<byte[]> excel(byte[] workbook, String filename) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }
}
