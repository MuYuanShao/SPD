package com.hospital.spd.supplychain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

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

    public OperationalClosureService(OperationalClosureReadModel readModel,
                                     OperationalShortageModule shortageModule,
                                     OperationalSettlementModule settlementModule,
                                     OperationalPdaModule pdaModule,
                                     OperationalRiskModule riskModule,
                                     OperationalHighValueModule highValueModule,
                                     OperationalDeliveryModule deliveryModule,
                                     OperationalRequisitionModule requisitionModule,
                                     OperationalConsumptionModule consumptionModule) {
        this.readModel = readModel;
        this.shortageModule = shortageModule;
        this.settlementModule = settlementModule;
        this.pdaModule = pdaModule;
        this.riskModule = riskModule;
        this.highValueModule = highValueModule;
        this.deliveryModule = deliveryModule;
        this.requisitionModule = requisitionModule;
        this.consumptionModule = consumptionModule;
    }

    public Map<String, Object> overview() {
        return readModel.overview();
    }

    public Map<String, Object> options() {
        return readModel.options();
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

    @Transactional
    public Map<String, Object> generateShortage(Map<String, Object> body) {
        return shortageModule.generateShortage(body);
    }

    @Transactional
    public Map<String, Object> smartReplenishmentAnalysis(Map<String, Object> body) {
        return shortageModule.smartAnalyze(body);
    }

    @Transactional
    public Map<String, Object> createRequisition(Map<String, Object> body) {
        return requisitionModule.createRequisition(body);
    }

    @Transactional
    public Map<String, Object> processRequisition(String requisitionNo, Map<String, Object> body) {
        return requisitionModule.action(requisitionNo, body);
    }

    @Transactional
    public Map<String, Object> createDelivery(Map<String, Object> body) {
        return deliveryModule.createDelivery(body);
    }

    @Transactional
    public Map<String, Object> confirmPicking(Map<String, Object> body) {
        return deliveryModule.confirmPicking(body);
    }

    @Transactional
    public Map<String, Object> signDelivery(String deliveryNo) {
        return deliveryModule.signDelivery(deliveryNo);
    }

    @Transactional
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

    public Map<String, Object> rejectManualSettlementGeneration() {
        throw new IllegalArgumentException("结算数据已改为按批次结算点自动生成，无需人工生成");
    }

    @Transactional
    public Map<String, Object> confirmSettlement(String settlementNo) {
        return settlementModule.confirmSettlement(settlementNo);
    }

    public Map<String, Object> uploadPda(Map<String, Object> body) {
        return pdaModule.uploadPda(body);
    }

    public Map<String, Object> coldChainException(Map<String, Object> body) {
        return riskModule.coldChainException(body);
    }

    public Map<String, Object> createRecall(Map<String, Object> body) {
        return riskModule.createRecall(body);
    }

    public Map<String, Object> recallBatches(String productCode, String warehouseName) {
        return riskModule.recallBatches(productCode, warehouseName);
    }

    @Transactional
    public Map<String, Object> bindHighValuePatient(Map<String, Object> body) {
        return highValueModule.bindPatient(body);
    }

    public Map<String, Object> highValueCharge(Map<String, Object> body) {
        return highValueModule.highValueCharge(body);
    }

    @Transactional
    public Map<String, Object> receiveHighValueBillingCallback(Map<String, Object> body) {
        return highValueModule.receiveBillingCallback(body);
    }
}
