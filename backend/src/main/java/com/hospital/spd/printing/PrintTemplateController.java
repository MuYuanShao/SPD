package com.hospital.spd.printing;

import com.hospital.spd.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exposes print template configuration endpoints for label and document printing.
 */
@RestController
@RequestMapping("/print-templates")
public class PrintTemplateController {

    private final PrintTemplateService printTemplateService;

    public PrintTemplateController(PrintTemplateService printTemplateService) {
        this.printTemplateService = printTemplateService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(printTemplateService.list());
    }

    @GetMapping("/label-print")
    public ApiResponse<Map<String, Object>> activeQuotaLabelTemplates() {
        return ApiResponse.ok(printTemplateService.activeQuotaLabelTemplates());
    }

    @GetMapping("/{templateType}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String templateType) {
        return ApiResponse.ok(printTemplateService.get(templateType));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody PrintTemplateUpsertRequest request) {
        return ApiResponse.ok(printTemplateService.createQuotaLabel(request.templateCode(), request));
    }

    @PutMapping("/by-id/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id,
                                                   @RequestBody PrintTemplateUpsertRequest request) {
        return ApiResponse.ok(printTemplateService.update(id, request));
    }

    @PutMapping("/{templateType}")
    public ApiResponse<Map<String, Object>> upsert(@PathVariable String templateType,
                                                   @RequestBody PrintTemplateUpsertRequest request) {
        return ApiResponse.ok(printTemplateService.upsert(templateType, request));
    }

}
