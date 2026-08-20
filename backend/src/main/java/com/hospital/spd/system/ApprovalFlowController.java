package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.ApprovalFlowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/approval-flows")
public class ApprovalFlowController {
    private final ApprovalFlowService approvalFlowService;

    public ApprovalFlowController(ApprovalFlowService approvalFlowService) {
        this.approvalFlowService = approvalFlowService;
    }

    @GetMapping
    public ApiResponse<List<ApprovalFlowRow>> list(@RequestParam(required = false) String featureCode,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(approvalFlowService.list(featureCode, keyword, status));
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(approvalFlowService.options());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody ApprovalFlowRequest request) {
        return ApiResponse.ok(approvalFlowService.create(request));
    }

    @PutMapping("/{flowId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long flowId, @RequestBody ApprovalFlowRequest request) {
        return ApiResponse.ok(approvalFlowService.update(flowId, request));
    }

    @PutMapping("/{flowId}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long flowId,
                                                         @RequestBody Map<String, Integer> request) {
        return ApiResponse.ok(approvalFlowService.updateStatus(flowId, request.getOrDefault("status", 1)));
    }
}
