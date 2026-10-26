package com.hospital.spd.mobile;

import com.hospital.spd.specialty.service.QuotaPackageTraceFlowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Coordinates idempotent whole-package consumption while shared services own stock, trace and settlement writes. */
@Service
public class MobileConsumptionService {
    private final MobileAccessService access;
    private final MobileConsumptionRepository data;
    private final MobileReceiptRepository receipts;
    private final MobileReviewService reviews;
    private final QuotaPackageTraceFlowService business;
    private final MobileRecoveryService recovery;
    public MobileConsumptionService(MobileAccessService access,MobileConsumptionRepository data,MobileReceiptRepository receipts,
                                    MobileReviewService reviews,QuotaPackageTraceFlowService business,MobileRecoveryService recovery) {
        this.access=access;this.data=data;this.receipts=receipts;this.reviews=reviews;this.business=business;
        this.recovery=recovery;
    }
    public ConsumeSnapshot resolve(ConsumeScan scan) {
        access.requireConsumptionWarehouse(scan.deptId(),scan.warehouseId());
        return validatedSnapshot(resolveLabelId(scan.rawCode(),scan.deptId(),scan.warehouseId()),scan.deptId(),scan.warehouseId(),false);
    }
    public ConsumeReview validate(ConsumeOperation operation) {
        shape(operation);access.requireConsumptionWarehouse(operation.deptId(),operation.warehouseId());
        return reviews.issue(access.operator().userId(),operation,snapshot(operation,false));
    }
    public Recovery<ConsumeSnapshot> recover(ConsumeOperation operation) {
        shape(operation);access.requireConsumptionWarehouse(operation.deptId(),operation.warehouseId());
        return recovery.recover(operation, () -> {
            ConsumeReview review=reviews.issue(access.operator().userId(),operation,snapshot(operation,true));
            return new Recovery<>(null,review.reviewHash(),review.expiresAt(),review.summary());
        });
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result submit(ConsumeOperation operation) {
        shape(operation);access.requireConsumptionWarehouse(operation.deptId(),operation.warehouseId());
        Long user=access.operator().userId();String hash=reviews.payloadHash(operation);
        var receipt=receipts.acquire(user,operation,hash);
        if(!hash.equals(receipt.payloadHash())) throw new MobileConflictException("同一操作编号的内容不一致，请先查询原消耗结果");
        if(receipt.result()!=null) return receipt.result();
        ConsumeSnapshot summary=snapshot(operation,true);reviews.verify(user,operation,summary);
        Map<String,Object> result=business.consumeByLabelId(summary.pack().id());
        if(!(result.get("consumptionNo") instanceof String number) || number.isBlank()
                || !(result.get("quantity") instanceof BigDecimal quantity) || quantity.compareTo(summary.pack().quantity())!=0
                || !(result.get("amount") instanceof BigDecimal amount)) throw new IllegalStateException("消耗服务回执不完整，业务事务已回滚");
        Result response=new Result(operation.operationId(),operation.kind(),operation.taskId(),number,"succeeded",user,operation.deviceId(),
                Instant.now(),"定数包整包消耗成功",null,quantity,amount);
        receipts.complete(user,operation,response,reviews.encode(response));return response;
    }
    private ConsumeSnapshot snapshot(ConsumeOperation op,boolean lock) {
        ConsumeSnapshot snapshot=validatedSnapshot(op.taskId(),op.deptId(),op.warehouseId(),lock);
        if(!op.taskId().equals(resolveLabelId(op.rawCode(),op.deptId(),op.warehouseId()))) throw new MobileConflictException("扫码内容与选中实际包不一致");
        return snapshot;
    }
    private Long resolveLabelId(String code,Long deptId,Long warehouseId) {
        List<Long> ids=data.resolveIds(code,deptId,warehouseId);
        if(ids.isEmpty()) throw new IllegalArgumentException("未找到当前科室库房内的实际定数包；散货和高值不支持此入口");
        if(ids.size()!=1) throw new IllegalArgumentException("该码对应多个包，请扫描具体包码或唯一码");
        return ids.get(0);
    }
    private ConsumeSnapshot validatedSnapshot(Long labelId,Long deptId,Long warehouseId,boolean lock) {
        var rows=data.packs(labelId,deptId,warehouseId,lock);
        if(rows.size()!=1) throw new IllegalArgumentException("定数包不属于当前科室库房或相关资料已停用");
        var row=rows.get(0);ConsumePack pack=row.pack();
        if(row.highValue()) throw new IllegalArgumentException("高值耗材不支持定数包消耗入口");
        if("consumed".equals(pack.status())) throw new MobileConflictException("该定数包已消耗，不能重复消耗");
        if(!"signed".equals(pack.status())) throw new MobileConflictException("只有已签收入当前科室库房的完整定数包才能消耗");
        if(pack.traceCodeId()==null || !"signed".equals(row.traceStatus()) || !"low_value_quota_pack".equals(row.traceScope()))
            throw new MobileConflictException("定数包追溯身份缺失或状态不允许消耗，请电脑端核对");
        if(pack.quantity()==null || pack.quantity().signum()<=0) throw new IllegalArgumentException("包内数量无效");
        var sources=data.sources(labelId,lock);
        if(sources.isEmpty()) throw new IllegalArgumentException("定数包来源批次缺失，不能消耗");
        BigDecimal total=BigDecimal.ZERO;
        Map<Long,ConsumeBatch> grouped=new TreeMap<>();
        for(var source:sources) {
            if(!warehouseId.equals(source.warehouseId()) || !pack.productId().equals(source.productId()) || source.quantity()==null
                    || source.quantity().signum()<=0 || source.unitPrice()==null || source.unitPrice().signum()<0 || source.expired())
                throw new MobileConflictException("包来源批次、数量、库房或效期不符合消耗要求");
            total=total.add(source.quantity());ConsumeBatch old=grouped.get(source.batchId());
            grouped.put(source.batchId(),new ConsumeBatch(source.batchId(),source.quantity().add(old==null?BigDecimal.ZERO:old.quantity()),source.unitPrice(),source.expireDate()));
        }
        if(total.compareTo(pack.quantity())!=0) throw new MobileConflictException("包内数量与来源批次合计不一致，不能整包消耗");
        for(ConsumeBatch batch:grouped.values()) {
            var available=data.available(warehouseId,pack.productId(),batch.batchId(),lock);
            if(available.size()!=1 || available.get(0).compareTo(batch.quantity())<0) throw new MobileConflictException("包来源批次可用库存不足，不能消耗");
        }
        return new ConsumeSnapshot(pack,List.copyOf(grouped.values()));
    }
    static void shape(ConsumeOperation op) {
        if(!"CONSUME_PACKAGE".equals(op.kind()) || !op.wholePackageConfirmed() || op.quantity()!=null || op.amount()!=null)
            throw new IllegalArgumentException("仅支持已确认的实际定数包整包消耗，不能手工指定数量或金额");
    }
}
