package com.hospital.spd.mobile;

import com.hospital.spd.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Exposes package scan, authoritative review and explicit whole-package consumption. */
@RestController
@RequestMapping("/mobile/consumption")
public class MobileConsumptionController {
    private final MobileConsumptionService service;
    public MobileConsumptionController(MobileConsumptionService service) {this.service=service;}
    @PostMapping("/packages/resolve") public ApiResponse<ConsumeSnapshot> resolve(@Valid @RequestBody ConsumeScan scan) {return ApiResponse.ok(service.resolve(scan));}
    @PostMapping("/operations/validate") public ApiResponse<ConsumeReview> validate(@Valid @RequestBody ConsumeOperation op) {return ApiResponse.ok(service.validate(op));}
    @PostMapping("/operations") public ApiResponse<Result> submit(@Valid @RequestBody ConsumeOperation op) {return ApiResponse.ok(service.submit(op));}
    @PostMapping("/operations/recover") public ApiResponse<Recovery<ConsumeSnapshot>> recover(@Valid @RequestBody ConsumeOperation op) {return ApiResponse.ok(service.recover(op));}
}
