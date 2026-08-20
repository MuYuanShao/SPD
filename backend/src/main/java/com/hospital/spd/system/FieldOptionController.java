package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.FieldOptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exposes the dropdown option dictionary for the field management module and table select fields.
 */
@RestController
@RequestMapping("/system/field-options")
public class FieldOptionController {

    private final FieldOptionService fieldOptionService;

    public FieldOptionController(FieldOptionService fieldOptionService) {
        this.fieldOptionService = fieldOptionService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> options(@RequestParam(required = false) String fieldKey) {
        return ApiResponse.ok(fieldOptionService.options(fieldKey));
    }

    @GetMapping("/fields")
    public ApiResponse<List<Map<String, Object>>> fields() {
        return ApiResponse.ok(fieldOptionService.fields());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody FieldOptionUpsertRequest request) {
        return ApiResponse.ok(fieldOptionService.create(request));
    }

    @PutMapping("/{optionId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long optionId,
                                                   @RequestBody FieldOptionUpsertRequest request) {
        return ApiResponse.ok(fieldOptionService.update(optionId, request));
    }

    @PutMapping("/{optionId}/delete")
    public ApiResponse<Map<String, Object>> remove(@PathVariable Long optionId) {
        return ApiResponse.ok(fieldOptionService.remove(optionId));
    }
}
