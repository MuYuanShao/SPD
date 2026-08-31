package com.hospital.spd.masterdata;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.masterdata.service.PendingProductAttachmentService;
import com.hospital.spd.masterdata.service.PendingProductImportExportService;
import com.hospital.spd.masterdata.service.ProductApprovalService;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Binds pending-catalog HTTP requests and delegates all business work to services. */
@RestController
@RequestMapping("/pending-product-applications")
public class PendingProductApplicationController {
    private final ProductApprovalService productApprovalService;
    private final PendingProductAttachmentService attachmentService;
    private final PendingProductImportExportService importExportService;

    @Autowired
    public PendingProductApplicationController(ProductApprovalService productApprovalService,
                                               PendingProductAttachmentService attachmentService,
                                               PendingProductImportExportService importExportService) {
        this.productApprovalService = productApprovalService;
        this.attachmentService = attachmentService;
        this.importExportService = importExportService;
    }

    /** Compatibility constructor retained for isolated controller tests. */
    public PendingProductApplicationController(ProductApprovalService productApprovalService,
                                               PendingProductAttachmentService attachmentService) {
        this(productApprovalService, attachmentService, new PendingProductImportExportService(productApprovalService));
    }

    @GetMapping("/partner-options")
    public ApiResponse<Map<String, List<Map<String, Object>>>> partnerOptions() {
        return ApiResponse.ok(productApprovalService.partnerOptions());
    }

    @GetMapping("/source-products")
    public ApiResponse<Map<String, Object>> sourceProducts(@RequestParam(required = false) String keyword,
                                                           @RequestParam Map<String, String> params) {
        return ApiResponse.ok(productApprovalService.sourceProducts(keyword, params));
    }

    @GetMapping("/source-products/{productCode}")
    public ApiResponse<Map<String, Object>> sourceProduct(@PathVariable String productCode) {
        return ApiResponse.ok(productApprovalService.sourceProduct(productCode));
    }

    @GetMapping("/{applicationNo}")
    public ApiResponse<PendingProductApplicationDetail> detail(@PathVariable String applicationNo) {
        return ApiResponse.ok(productApprovalService.getDetail(applicationNo));
    }

    @GetMapping
    public ApiResponse<PendingProductApplicationPage> list(
            @RequestParam(defaultValue = "new") String type,
            @RequestParam(defaultValue = "todo") String scope,
            @RequestParam(defaultValue = "pending") String mineStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam Map<String, String> params) {
        return ApiResponse.ok(productApprovalService.listApplications(type, scope, mineStatus, keyword, params));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody PendingProductApplicationRequest request) {
        return ApiResponse.ok(productApprovalService.createApplication(request));
    }

    @PutMapping("/{applicationNo}/action")
    public ApiResponse<Map<String, Object>> action(@PathVariable String applicationNo,
                                                   @RequestBody PendingProductApprovalActionRequest request) {
        return ApiResponse.ok(productApprovalService.processAction(applicationNo, request));
    }

    @PutMapping("/batch-action")
    public ApiResponse<Map<String, Object>> batchAction(@RequestBody PendingProductBatchApprovalRequest request) {
        if (request.applicationNos() == null || request.applicationNos().isEmpty()) {
            return ApiResponse.error(400, "请选择至少一条审批单");
        }
        return ApiResponse.ok(productApprovalService.batchProcessAction(request.applicationNos(),
                new PendingProductApprovalActionRequest(request.action(), request.opinion())));
    }

    @PutMapping("/{applicationNo}/update")
    public ApiResponse<Map<String, Object>> updateApplication(@PathVariable String applicationNo,
                                                              @RequestBody PendingProductApplicationRequest request) {
        return ApiResponse.ok(productApprovalService.updateApplicationData(applicationNo, request));
    }

    @PutMapping("/{applicationNo}/resubmit")
    public ApiResponse<Map<String, Object>> resubmit(@PathVariable String applicationNo,
                                                     @RequestBody PendingProductApplicationRequest request) {
        return ApiResponse.ok(productApprovalService.resubmitApplication(applicationNo, request));
    }

    @GetMapping("/export")
    public ApiResponse<List<PendingProductApplicationRow>> export(@RequestParam(defaultValue = "new") String type,
                                                                  @RequestParam(defaultValue = "todo") String scope,
                                                                  @RequestParam(defaultValue = "pending") String mineStatus,
                                                                  @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productApprovalService.listApplications(type, scope, mineStatus, keyword).rows());
    }

    @GetMapping("/import-template.xlsx")
    public ResponseEntity<byte[]> importTemplate() throws Exception {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pending-product-application-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importExportService.template());
    }

    @GetMapping("/{applicationNo}/attachments")
    public ApiResponse<List<Map<String, Object>>> attachments(@PathVariable String applicationNo) {
        return ApiResponse.ok(attachmentService.list(applicationNo));
    }

    @PostMapping("/{applicationNo}/attachments")
    public ApiResponse<Map<String, Object>> uploadAttachment(@PathVariable String applicationNo,
                                                             @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(attachmentService.upload(applicationNo, file));
    }

    @GetMapping("/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> previewAttachment(@PathVariable Long attachmentId) {
        return attachmentService.preview(attachmentId);
    }

    @DeleteMapping("/{applicationNo}/attachments/{attachmentId}")
    public ApiResponse<Map<String, Object>> deleteAttachment(@PathVariable String applicationNo,
                                                             @PathVariable Long attachmentId) {
        return ApiResponse.ok(attachmentService.delete(applicationNo, attachmentId));
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importApplications(@RequestParam("file") MultipartFile file) throws Exception {
        PendingProductImportExportService.ImportOutcome outcome = importExportService.importApplications(file);
        return outcome.error() == null ? ApiResponse.ok(outcome.data()) : ApiResponse.error(400, outcome.error());
    }
}
