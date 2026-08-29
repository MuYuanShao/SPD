package com.hospital.spd.supplychain;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.supplychain.service.OperationalClosureService;
import com.hospital.spd.supplychain.service.ManualSettlementGenerationRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.hospital.spd.supplychain.OperationalCommandRequests.*;

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
    public ApiResponse<Map<String, Object>> confirmLoosePicking(@Valid @RequestBody LoosePickingRequest request) {
        return ApiResponse.ok(service.confirmLoosePicking(request));
    }

    @PostMapping("/shortage/generate")
    public ApiResponse<Map<String, Object>> generateShortage(@Valid @RequestBody ShortageRequest request) {
        return ApiResponse.ok(service.generateShortage(request));
    }

    @PostMapping("/shortage/smart-analysis")
    public ApiResponse<Map<String, Object>> smartReplenishmentAnalysis(@Valid @RequestBody SmartAnalysisRequest request) {
        return ApiResponse.ok(service.smartReplenishmentAnalysis(request));
    }

    @PostMapping("/requisitions")
    public ApiResponse<Map<String, Object>> createRequisition(
            @Valid @RequestBody CreateRequisitionRequest request,
            HttpServletResponse response) {
        if (request.destinationWarehouseId() == null && request.destinationWarehouseName() != null) {
            response.setHeader("Deprecation", "true");
            response.setHeader("Link", "</api/operational-closure/requisitions>; rel=successor-version");
        }
        return ApiResponse.ok(service.createRequisition(request));
    }

    @PutMapping("/requisitions/{requisitionNo}/action")
    public ApiResponse<Map<String, Object>> processRequisition(@PathVariable String requisitionNo,
                                                                @Valid @RequestBody RequisitionActionRequest request) {
        return ApiResponse.ok(service.processRequisition(requisitionNo, request));
    }

    @PostMapping("/deliveries")
    public ApiResponse<Map<String, Object>> createDelivery(@Valid @RequestBody DeliveryRequest request) {
        return ApiResponse.ok(service.createDelivery(request));
    }

    @PostMapping("/picking/confirm")
    public ApiResponse<Map<String, Object>> confirmPicking(@Valid @RequestBody PackagePickingRequest request) {
        return ApiResponse.ok(service.confirmPicking(request));
    }

    @PutMapping("/deliveries/{deliveryNo}/sign")
    public ApiResponse<Map<String, Object>> signDelivery(@PathVariable String deliveryNo) {
        return ApiResponse.ok(service.signDelivery(deliveryNo));
    }

    @PostMapping("/consumptions")
    public ApiResponse<Map<String, Object>> createConsumption(@Valid @RequestBody ConsumptionRequest request) {
        return ApiResponse.ok(service.createConsumption(request));
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
    public ApiResponse<Map<String, Object>> generateMissingSettlements(
            @Valid @RequestBody ManualSettlementGenerationRequest request) {
        return ApiResponse.ok(service.generateMissingSettlements(request));
    }
    @PutMapping("/settlements/{settlementNo}/confirm")
    public ApiResponse<Map<String, Object>> confirmSettlement(@PathVariable String settlementNo) {
        return ApiResponse.ok(service.confirmSettlement(settlementNo));
    }

    @PostMapping("/pda/offline-upload")
    public ApiResponse<Map<String, Object>> uploadPda(@Valid @RequestBody PdaUploadRequest request) {
        return ApiResponse.ok(service.uploadPda(request));
    }

    @PostMapping("/cold-chain/exceptions")
    public ApiResponse<Map<String, Object>> coldChainException(@Valid @RequestBody ColdChainExceptionRequest request) {
        return ApiResponse.ok(service.coldChainException(request));
    }

    @PostMapping("/recalls")
    public ApiResponse<Map<String, Object>> createRecall(@Valid @RequestBody RecallRequest request) {
        return ApiResponse.ok(service.createRecall(request));
    }

    @GetMapping("/recalls/batches")
    public ApiResponse<Map<String, Object>> recallBatches(@RequestParam(defaultValue = "") String productCode,
                                                          @RequestParam(defaultValue = "") String warehouseName) {
        return ApiResponse.ok(service.recallBatches(productCode, warehouseName));
    }

    @GetMapping("/recalls/inventory")
    public ApiResponse<Map<String, Object>> recallInventory(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.recallInventory(params));
    }

    @PostMapping("/high-value/charges")
    public ApiResponse<Map<String, Object>> highValueCharge(@Valid @RequestBody HighValueChargeRequest request) {
        return ApiResponse.ok(service.highValueCharge(request));
    }

    @PostMapping("/high-value/patient-bindings")
    public ApiResponse<Map<String, Object>> bindHighValuePatient(@Valid @RequestBody HighValuePatientBindingRequest request) {
        return ApiResponse.ok(service.bindHighValuePatient(request));
    }

    @PostMapping("/high-value/billing-callback")
    public ApiResponse<Map<String, Object>> highValueBillingCallback(@Valid @RequestBody HighValueBillingCallbackRequest request) {
        return ApiResponse.ok(service.receiveHighValueBillingCallback(request));
    }
}
