package com.hospital.spd.supplychain.service;

import com.hospital.spd.supplychain.CreateRequisitionRequest;
import com.hospital.spd.supplychain.DepartmentRequisitionSmartRequests.AnalysisRequest;
import com.hospital.spd.supplychain.DepartmentRequisitionSmartRequests.GenerateRequest;
import com.hospital.spd.supplychain.DepartmentRequisitionSmartRequests.HighValuePickingRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static com.hospital.spd.supplychain.OperationalCommandRequests.*;

/**
 * Coordinates operational closure flows after stock-in, including shortage, delivery, consumption, settlement, PDA, and risk events.
 */
@Service
public class OperationalClosureService {

    private final OperationalClosureReadModel readModel;
    private final OperationalShortageModule shortageModule;
    private final OperationalSettlementModule settlementModule;
    private final OperationalPdaModule pdaModule;
    private final OperationalRiskModule riskModule;
    private final OperationalHighValueModule highValueModule;
    private final OperationalDeliveryModule deliveryModule;
    private final OperationalRequisitionModule requisitionModule;
    private final OperationalConsumptionModule consumptionModule;
    private final SettlementPointService settlementPointService;
    private final DepartmentRequisitionSmartService requisitionSmartService;

    public OperationalClosureService(OperationalClosureReadModel readModel,
                                     OperationalShortageModule shortageModule,
                                     OperationalSettlementModule settlementModule,
                                     OperationalPdaModule pdaModule,
                                     OperationalRiskModule riskModule,
                                     OperationalHighValueModule highValueModule,
                                     OperationalDeliveryModule deliveryModule,
                                     OperationalRequisitionModule requisitionModule,
                                     OperationalConsumptionModule consumptionModule,
                                     SettlementPointService settlementPointService) {
        this(readModel, shortageModule, settlementModule, pdaModule, riskModule, highValueModule,
                deliveryModule, requisitionModule, consumptionModule, settlementPointService, null);
    }

    @Autowired
    public OperationalClosureService(OperationalClosureReadModel readModel,
                                     OperationalShortageModule shortageModule,
                                     OperationalSettlementModule settlementModule,
                                     OperationalPdaModule pdaModule,
                                     OperationalRiskModule riskModule,
                                     OperationalHighValueModule highValueModule,
                                     OperationalDeliveryModule deliveryModule,
                                     OperationalRequisitionModule requisitionModule,
                                     OperationalConsumptionModule consumptionModule,
                                     SettlementPointService settlementPointService,
                                     DepartmentRequisitionSmartService requisitionSmartService) {
        this.readModel = readModel;
        this.shortageModule = shortageModule;
        this.settlementModule = settlementModule;
        this.pdaModule = pdaModule;
        this.riskModule = riskModule;
        this.highValueModule = highValueModule;
        this.deliveryModule = deliveryModule;
        this.requisitionModule = requisitionModule;
        this.consumptionModule = consumptionModule;
        this.settlementPointService = settlementPointService;
        this.requisitionSmartService = requisitionSmartService;
    }

    public Map<String, Object> overview() {
        return readModel.overview();
    }

    public Map<String, Object> options() {
        return readModel.options();
    }

    public Map<String, Object> requisitionOptions() {
        return readModel.requisitionOptions();
    }

    public java.util.List<Map<String, Object>> requisitionWarehouses(String deptCode) {
        return readModel.requisitionWarehouses(deptCode);
    }

    public Map<String, Object> list(String type, Map<String, String> params) {
        return readModel.list(type, params);
    }

    public Map<String, Object> requisitionDetails(String requisitionNo) {
        return readModel.requisitionDetails(requisitionNo);
    }

    public Map<String, Object> pickingRequisitions() {
        return deliveryModule.pickingRequisitions();
    }

    public Map<String, Object> availablePackageLabels(Map<String, String> params) {
        return deliveryModule.availablePackageLabels(params);
    }

    public Map<String, Object> packageLabelDetail(String labelNo) {
        return deliveryModule.packageLabelDetail(labelNo);
    }

    public Map<String, Object> availableUniqueCodes(Map<String, String> params) {
        return deliveryModule.availableUniqueCodes(params);
    }

    public Map<String, Object> availableLooseStock(Map<String, String> params) {
        return deliveryModule.availableLooseStock(params);
    }

    @Transactional
    public Map<String, Object> confirmLoosePicking(LoosePickingRequest request) {
        return deliveryModule.confirmLoosePicking(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> confirmLoosePicking(Map<String, Object> body) {
        return deliveryModule.confirmLoosePicking(body);
    }

    @Transactional
    public Map<String, Object> generateShortage(ShortageRequest request) {
        return shortageModule.generateShortage(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> generateShortage(Map<String, Object> body) {
        return shortageModule.generateShortage(body);
    }

    @Transactional
    public Map<String, Object> smartReplenishmentAnalysis(SmartAnalysisRequest request) {
        return shortageModule.smartAnalyze(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> smartReplenishmentAnalysis(Map<String, Object> body) {
        return shortageModule.smartAnalyze(body);
    }

    @Transactional
    public Map<String, Object> createRequisition(CreateRequisitionRequest request) {
        return requisitionModule.createRequisition(request.toCompatibilityMap());
    }

    @Transactional
    public Map<String, Object> analyzeDepartmentRequisition(AnalysisRequest request) {
        return requisitionSmartService.analyze(request);
    }

    @Transactional
    public Map<String, Object> generateDepartmentRequisitions(GenerateRequest request) {
        return requisitionSmartService.generate(request);
    }

    @Transactional
    public Map<String, Object> confirmHighValuePicking(HighValuePickingRequest request) {
        return deliveryModule.confirmHighValuePicking(request.itemId(), request.traceCodeIds());
    }

    @Transactional
    public Map<String, Object> processRequisition(String requisitionNo, RequisitionActionRequest request) {
        return requisitionModule.action(requisitionNo, request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> processRequisition(String requisitionNo, Map<String, Object> body) {
        return requisitionModule.action(requisitionNo, body);
    }

    @Transactional
    public Map<String, Object> createDelivery(DeliveryRequest request) {
        return deliveryModule.createDelivery(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> createDelivery(Map<String, Object> body) {
        return deliveryModule.createDelivery(body);
    }

    @Transactional
    public Map<String, Object> confirmPicking(PackagePickingRequest request) {
        return deliveryModule.confirmPicking(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> confirmPicking(Map<String, Object> body) {
        return deliveryModule.confirmPicking(body);
    }

    @Transactional
    public Map<String, Object> signDelivery(String deliveryNo) {
        return deliveryModule.signDelivery(deliveryNo);
    }

    @Transactional
    public Map<String, Object> createConsumption(ConsumptionRequest request) {
        return consumptionModule.createConsumption(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> createConsumption(Map<String, Object> body) {
        return consumptionModule.createConsumption(body);
    }

    public Map<String, Object> resolveConsumptionProduct(String queryCode) {
        return consumptionModule.resolveConsumptionProduct(queryCode);
    }

    @Transactional
    public Map<String, Object> reverseConsumption(String consumptionNo) {
        return consumptionModule.reverseConsumption(consumptionNo);
    }

    @Transactional
    public Map<String, Object> generateMissingSettlements(ManualSettlementGenerationRequest request) {
        return settlementPointService.generateMissing(request);
    }

    @Transactional
    public Map<String, Object> confirmSettlement(String settlementNo) {
        return settlementModule.confirmSettlement(settlementNo);
    }

    public Map<String, Object> uploadPda(PdaUploadRequest request) {
        return pdaModule.uploadPda(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> uploadPda(Map<String, Object> body) {
        return pdaModule.uploadPda(body);
    }

    public Map<String, Object> coldChainException(ColdChainExceptionRequest request) {
        return riskModule.coldChainException(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> coldChainException(Map<String, Object> body) {
        return riskModule.coldChainException(body);
    }

    public Map<String, Object> createRecall(RecallRequest request) {
        return riskModule.createRecall(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> createRecall(Map<String, Object> body) {
        return riskModule.createRecall(body);
    }

    public Map<String, Object> recallBatches(String productCode, String warehouseName) {
        return riskModule.recallBatches(productCode, warehouseName);
    }

    public Map<String, Object> recallInventory(Map<String, String> params) {
        return riskModule.recallInventory(params);
    }

    @Transactional
    public Map<String, Object> bindHighValuePatient(HighValuePatientBindingRequest request) {
        return highValueModule.bindPatient(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> bindHighValuePatient(Map<String, Object> body) {
        return highValueModule.bindPatient(body);
    }

    public Map<String, Object> highValueCharge(HighValueChargeRequest request) {
        return highValueModule.highValueCharge(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> highValueCharge(Map<String, Object> body) {
        return highValueModule.highValueCharge(body);
    }

    @Transactional
    public Map<String, Object> receiveHighValueBillingCallback(HighValueBillingCallbackRequest request) {
        return highValueModule.receiveBillingCallback(request.toCompatibilityMap());
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> receiveHighValueBillingCallback(Map<String, Object> body) {
        return highValueModule.receiveBillingCallback(body);
    }
}
