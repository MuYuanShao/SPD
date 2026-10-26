package com.hospital.spd.mobile;

import com.hospital.spd.common.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Exposes authorized three-mode picking of a single requisition line per operation. */
@RestController
@RequestMapping("/mobile/picking")
public class MobilePickingController {
    private final MobilePickingService picking;
    public MobilePickingController(MobilePickingService picking) {this.picking=picking;}
    @GetMapping("/orders") public ApiResponse<PageResponse<PickOrder>> orders(@RequestParam Long deptId,@RequestParam Long warehouseId,
            @RequestParam(defaultValue="") String query,@RequestParam Map<String,String> params) {
        return ApiResponse.ok(picking.orders(deptId,warehouseId,query,PageRequest.from(params)));
    }
    @GetMapping("/orders/{id}") public ApiResponse<PickOrderDetail> order(@PathVariable Long id,@RequestParam Long deptId,@RequestParam Long warehouseId) {
        return ApiResponse.ok(picking.orderDetail(id,deptId,warehouseId));
    }
    @PostMapping("/orders/{id}/scans/resolve") public ApiResponse<PickOrderScan> orderScan(@PathVariable Long id,@Valid @RequestBody OrderScan scan) {
        return ApiResponse.ok(picking.resolveOrder(id,scan));
    }
    @GetMapping("/tasks") public ApiResponse<PageResponse<PickTask>> tasks(@RequestParam Long deptId,@RequestParam Long warehouseId,
            @RequestParam(defaultValue="") String query,@RequestParam Map<String,String> params) {
        return ApiResponse.ok(picking.tasks(deptId,warehouseId,query,PageRequest.from(params)));
    }
    @GetMapping("/tasks/{id}") public ApiResponse<PickTask> detail(@PathVariable Long id,@RequestParam Long deptId,@RequestParam Long warehouseId) {
        return ApiResponse.ok(picking.detail(id,deptId,warehouseId));
    }
    @PostMapping("/scans/resolve") public ApiResponse<PickUnit> resolve(@Valid @RequestBody PickScan scan) {return ApiResponse.ok(picking.resolve(scan));}
    @PostMapping("/operations/validate") public ApiResponse<PickReview> validate(@Valid @RequestBody PickOperation operation) {return ApiResponse.ok(picking.validate(operation));}
    @PostMapping("/operations") public ApiResponse<Result> submit(@Valid @RequestBody PickOperation operation) {return ApiResponse.ok(picking.submit(operation));}
    @PostMapping("/operations/recover") public ApiResponse<Recovery<PickSnapshot>> recover(@Valid @RequestBody PickOperation operation) {return ApiResponse.ok(picking.recover(operation));}
}
