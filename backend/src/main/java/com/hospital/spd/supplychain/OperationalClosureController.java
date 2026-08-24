package com.hospital.spd.supplychain;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.supplychain.service.OperationalClosureService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Exposes the operational closure endpoints for shortage, requisition, delivery, consumption, and settlement flows.
 */
@RestController
@RequestMapping("/operational-closure")
public class OperationalClosureController {

    private final OperationalClosureService service;

    public OperationalClosureController(OperationalClosureService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(service.options());
    }

    @GetMapping("/lists/{type}")
    public ApiResponse<Map<String, Object>> list(@PathVariable String type,
                                                 @RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.list(type, params));
    }

    @GetMapping("/requisitions/{requisitionNo}/items")
    public ApiResponse<Map<String, Object>> requisitionDetails(@PathVariable String requisitionNo) {
        return ApiResponse.ok(service.requisitionDetails(requisitionNo));
    }

    @GetMapping("/picking/requisitions")
    public ApiResponse<Map<String, Object>> pickingRequisitions() {
        return ApiResponse.ok(service.pickingRequisitions());
    }

    @GetMapping("/picking/package-labels")
    public ApiResponse<Map<String, Object>> availablePackageLabels(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.availablePackageLabels(params));
    }

    @GetMapping("/picking/package-labels/{labelNo}")
    public ApiResponse<Map<String, Object>> packageLabelDetail(@PathVariable String labelNo) {
        return ApiResponse.ok(service.packageLabelDetail(labelNo));
    }

    @GetMapping("/picking/unique-codes")
    public ApiResponse<Map<String, Object>> availableUniqueCodes(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.availableUniqueCodes(params));
    }

    @GetMapping("/picking/loose-stock")
    public ApiResponse<Map<String, Object>> availableLooseStock(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.availableLooseStock(params));
    }

    @PostMapping("/picking/confirm-loose")
    public ApiResponse<Map<String, Object>> confirmLoosePicking(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.confirmLoosePicking(body));
    }

    @PostMapping("/shortage/generate")
    public ApiResponse<Map<String, Object>> generateShortage(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.generateShortage(body));
    }

    @PostMapping("/shortage/smart-analysis")
    public ApiResponse<Map<String, Object>> smartReplenishmentAnalysis(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.smartReplenishmentAnalysis(body));
    }

    @PostMapping("/requisitions")
    public ApiResponse<Map<String, Object>> createRequisition(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createRequisition(body));
    }

    @PutMapping("/requisitions/{requisitionNo}/action")
    public ApiResponse<Map<String, Object>> processRequisition(@PathVariable String requisitionNo,
                                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.processRequisition(requisitionNo, body));
    }

    @PostMapping("/deliveries")
    public ApiResponse<Map<String, Object>> createDelivery(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createDelivery(body));
    }

    @PostMapping("/picking/confirm")
    public ApiResponse<Map<String, Object>> confirmPicking(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.confirmPicking(body));
    }

    @PutMapping("/deliveries/{deliveryNo}/sign")
    public ApiResponse<Map<String, Object>> signDelivery(@PathVariable String deliveryNo) {
        return ApiResponse.ok(service.signDelivery(deliveryNo));
    }

    @PostMapping("/consumptions")
    public ApiResponse<Map<String, Object>> createConsumption(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createConsumption(body));
    }

    @GetMapping("/consumptions/resolve")
    public ApiResponse<Map<String, Object>> resolveConsumptionProduct(
            @RequestParam(defaultValue = "") String queryCode) {
        return ApiResponse.ok(service.resolveConsumptionProduct(queryCode));
    }

    @PutMapping("/consumptions/{consumptionNo}/reverse")
    public ApiResponse<Map<String, Object>> reverseConsumption(@PathVariable String consumptionNo) {
        return ApiResponse.ok(service.reverseConsumption(consumptionNo));
    }

    @PostMapping("/settlements/generate")
    public ApiResponse<Map<String, Object>> rejectManualSettlementGeneration() {
        return ApiResponse.ok(service.rejectManualSettlementGeneration());
    }
    @PutMapping("/settlements/{settlementNo}/confirm")
    public ApiResponse<Map<String, Object>> confirmSettlement(@PathVariable String settlementNo) {
        return ApiResponse.ok(service.confirmSettlement(settlementNo));
    }

    @PostMapping("/pda/offline-upload")
    public ApiResponse<Map<String, Object>> uploadPda(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.uploadPda(body));
    }

    @PostMapping("/cold-chain/exceptions")
    public ApiResponse<Map<String, Object>> coldChainException(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.coldChainException(body));
    }

    @PostMapping("/recalls")
    public ApiResponse<Map<String, Object>> createRecall(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createRecall(body));
    }

    @GetMapping("/recalls/batches")
    public ApiResponse<Map<String, Object>> recallBatches(@RequestParam(defaultValue = "") String productCode,
                                                          @RequestParam(defaultValue = "") String warehouseName) {
        return ApiResponse.ok(service.recallBatches(productCode, warehouseName));
    }

    @PostMapping("/high-value/charges")
    public ApiResponse<Map<String, Object>> highValueCharge(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.highValueCharge(body));
    }

    @PostMapping("/high-value/patient-bindings")
    public ApiResponse<Map<String, Object>> bindHighValuePatient(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.bindHighValuePatient(body));
    }

    @PostMapping("/high-value/billing-callback")
    public ApiResponse<Map<String, Object>> highValueBillingCallback(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.receiveHighValueBillingCallback(body));
    }
}
