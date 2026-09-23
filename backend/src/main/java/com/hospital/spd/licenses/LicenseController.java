package com.hospital.spd.licenses;

import com.hospital.spd.common.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Exposes license management endpoints for product, supplier, manufacturer licenses and contracts.
 */
@RestController
@RequestMapping("/licenses")
public class LicenseController {

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(licenseService.list(type, keyword, page, size));
    }

    @GetMapping("/owner-options")
    public ApiResponse<Map<String, Object>> ownerOptions(@RequestParam String type,
            @RequestParam(defaultValue = "") String keyword, @RequestParam Map<String, String> params) {
        return ApiResponse.ok(licenseService.ownerOptions(type, keyword, params));
    }

    @GetMapping("/{licenseId}/history")
    public ApiResponse<Map<String, Object>> history(@PathVariable Long licenseId) {
        return ApiResponse.ok(licenseService.history(licenseId));
    }

    @PostMapping("/{licenseId}/renew")
    public ApiResponse<Map<String, Object>> renew(@PathVariable Long licenseId, @RequestBody LicenseUpsertRequest request) {
        return ApiResponse.ok(licenseService.renew(licenseId, request));
    }

    @GetMapping("/{licenseId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long licenseId) {
        return ApiResponse.ok(licenseService.detail(licenseId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody LicenseUpsertRequest request) {
        return ApiResponse.ok(licenseService.create(request));
    }

    @PutMapping("/{licenseId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long licenseId,
                                                   @RequestBody LicenseUpsertRequest request) {
        return ApiResponse.ok(licenseService.update(licenseId, request));
    }

    @DeleteMapping("/{licenseId}")
    public ApiResponse<Map<String, Object>> remove(@PathVariable Long licenseId) {
        return ApiResponse.ok(licenseService.remove(licenseId));
    }

    @GetMapping("/{licenseId}/attachments")
    public ApiResponse<List<Map<String, Object>>> attachments(@PathVariable Long licenseId) {
        return ApiResponse.ok(licenseService.attachments(licenseId));
    }

    @PostMapping("/{licenseId}/attachments")
    public ApiResponse<Map<String, Object>> uploadAttachment(@PathVariable Long licenseId,
                                                             @RequestParam("file") MultipartFile file,
                                                             @RequestParam(defaultValue = "other") String category) {
        return ApiResponse.ok(licenseService.uploadAttachment(licenseId, file, category));
    }

    @GetMapping("/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId) {
        return licenseService.downloadAttachment(attachmentId);
    }

    @GetMapping("/attachments/file/{storedName}")
    public ResponseEntity<Resource> downloadAttachmentByStoredName(@PathVariable String storedName) {
        return licenseService.downloadAttachmentByStoredName(storedName);
    }
}
