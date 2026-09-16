package com.hospital.spd.mobile;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Exposes the first mobile delivery-signing capability without enabling unimplemented picking or consumption. */
@RestController
@RequestMapping("/mobile")
public class MobileController {
    private final MobileAccessService access;
    private final MobileSigningService signing;
    public MobileController(MobileAccessService access, MobileSigningService signing) {
        this.access = access; this.signing = signing;
    }
    @GetMapping("/context")
    public ApiResponse<Context> context() { return ApiResponse.ok(access.context()); }

    @GetMapping("/tasks")
    public ApiResponse<PageResponse<Task>> tasks(@RequestParam Long deptId, @RequestParam Long warehouseId,
                                                 @RequestParam(defaultValue = "SIGN_DELIVERY") String kind,
                                                 @RequestParam(defaultValue = "picked") String status,
                                                 @RequestParam Map<String, String> params) {
        requireKind(kind);
        return ApiResponse.ok(signing.tasks(deptId, warehouseId, status, PageRequest.from(params)));
    }
    @GetMapping("/tasks/{kind}/{id}")
    public ApiResponse<Detail> detail(@PathVariable String kind, @PathVariable Long id,
                                      @RequestParam Long deptId, @RequestParam Long warehouseId) {
        requireKind(kind);
        return ApiResponse.ok(signing.detail(id, deptId, warehouseId));
    }
    @PostMapping("/scans/resolve")
    public ApiResponse<CheckItem> resolve(@Valid @RequestBody Scan scan) { return ApiResponse.ok(signing.resolve(scan)); }
    @PostMapping("/operations/validate")
    public ApiResponse<Review> validate(@Valid @RequestBody SignOperation operation) { return ApiResponse.ok(signing.validate(operation)); }
    @PostMapping("/operations")
    public ApiResponse<Result> submit(@Valid @RequestBody SignOperation operation) { return ApiResponse.ok(signing.submit(operation)); }
    @GetMapping("/operations/{operationId}")
    public ApiResponse<Result> result(@PathVariable UUID operationId) { return ApiResponse.ok(signing.result(operationId)); }
    private static void requireKind(String kind) {
        if (!"SIGN_DELIVERY".equals(kind)) throw new IllegalArgumentException("当前仅开放配送签收");
    }
}
