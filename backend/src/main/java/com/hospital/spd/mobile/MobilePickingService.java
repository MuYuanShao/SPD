package com.hospital.spd.mobile;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.common.OperatorContext;
import org.springframework.security.access.AccessDeniedException;
import com.hospital.spd.supplychain.service.OperationalDeliveryModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Coordinates per-line, partial mobile picking while existing services own inventory and trace transitions. */
@Service
public class MobilePickingService {
    private final MobileAccessService access;
    private final MobilePickingRepository data;
    private final MobileReceiptRepository receipts;
    private final MobileReviewService reviews;
    private final OperationalDeliveryModule business;
    private final MobileRecoveryService recovery;
    public MobilePickingService(MobileAccessService access,MobilePickingRepository data,MobileReceiptRepository receipts,
                                MobileReviewService reviews,OperationalDeliveryModule business,MobileRecoveryService recovery) {
        this.access=access;this.data=data;this.receipts=receipts;this.reviews=reviews;this.business=business;
        this.recovery=recovery;
    }
    public PageResponse<PickTask> tasks(Long dept,Long warehouse,String query,PageRequest page) {
        access.requirePickingWarehouse(dept,warehouse);return data.tasks(dept,warehouse,query,page);
    }
    public PageResponse<PickOrder> orders(Long dept,Long warehouse,String query,PageRequest page) {
        access.requirePickingWarehouse(dept,warehouse);return data.orders(dept,warehouse,query,page);
    }
    @Transactional(readOnly=true)
    public PickOrderDetail orderDetail(Long id,Long dept,Long warehouse) {
        PickOrder header=authorizedOrder(id,dept,warehouse);
        var items=data.orderItems(id,dept,warehouse).stream().map(MobilePickingRepository.Data::task).toList();
        return new PickOrderDetail(new PickOrder(header.id(),header.documentNo(),header.status(),header.deptId(),header.deptName(),
                header.sourceWarehouseId(),header.sourceWarehouseName(),header.warehouseId(),header.warehouseName(),items.size()),items);
    }
    @Transactional(readOnly=true)
    public PickOrderScan resolveOrder(Long id,OrderScan scan) {
        authorizedOrder(id,scan.deptId(),scan.warehouseId());
        List<PickOrderMatch> matches=new ArrayList<>();
        for(var row:data.orderItems(id,scan.deptId(),scan.warehouseId())) {
            try {
                requireOpen(row.task());PickUnit unit=resolveUnit(row,scan.rawCode(),false);
                if("package".equals(unit.type()))unit=packageById(row,unit.id(),false);
                matches.add(new PickOrderMatch(row.task(),unit));
            } catch(IllegalArgumentException|MobileConflictException ignored) {
                // A scan may match another line, but must never escape this authorized requisition.
            }
        }
        if(matches.isEmpty())throw new IllegalArgumentException("该码不属于本单待拣明细，或实物状态不可用，请核对原单");
        return new PickOrderScan(List.copyOf(matches));
    }
    private PickOrder authorizedOrder(Long id,Long dept,Long warehouse) {
        access.requirePickingWarehouse(dept,warehouse);
        var headers=data.orderHeaders(id,dept,warehouse);
        if(headers.size()!=1)throw new org.springframework.security.access.AccessDeniedException("申领单不存在或不属于当前账号及库房范围");
        return headers.get(0);
    }
    public PickTask detail(Long id,Long dept,Long warehouse) {
        access.requirePickingWarehouse(dept,warehouse);return authorizedDetail(id,dept,warehouse,false).task();
    }
    public PickUnit resolve(PickScan scan) {
        access.requirePickingWarehouse(scan.deptId(),scan.warehouseId());
        var current=authorizedDetail(scan.taskId(),scan.deptId(),scan.warehouseId(),false);
        requireOpen(current.task());
        PickUnit unit=resolveUnit(current,scan.rawCode(),false);
        return "package".equals(unit.type())?packageById(current,unit.id(),false):unit;
    }
    public PickReview validate(PickOperation operation) {
        shape(operation);access.requirePickingWarehouse(operation.deptId(),operation.warehouseId());
        return reviews.issue(access.operator().userId(),operation,snapshot(operation,false));
    }
    public Recovery<PickSnapshot> recover(PickOperation operation) {
        shape(operation);access.requirePickingWarehouse(operation.deptId(),operation.warehouseId());
        return recovery.recover(operation, () -> {
            PickReview review=reviews.issue(access.operator().userId(),operation,snapshot(operation,true));
            return new Recovery<>(null,review.reviewHash(),review.expiresAt(),review.summary());
        });
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result submit(PickOperation operation) {
        shape(operation);access.requirePickingWarehouse(operation.deptId(),operation.warehouseId());
        Long user=access.operator().userId();String hash=reviews.payloadHash(operation);
        var receipt=receipts.acquire(user,operation,hash);
        if(!hash.equals(receipt.payloadHash())) throw new MobileConflictException("同一操作编号的内容不一致，请先查询原操作");
        if(receipt.result()!=null) return receipt.result();
        PickSnapshot summary=snapshot(operation,true);reviews.verify(user,operation,summary);
        Map<String,Object> result=switch(operation.kind()) {
            case "PICK_LOOSE" -> business.confirmLoosePicking(Map.of("requisitionNo",summary.task().documentNo(),
                    "itemId",operation.taskId(),"warehouseName",summary.task().sourceWarehouseName(),"quantity",summary.quantity()));
            case "PICK_PACKAGE" -> business.confirmPicking(Map.of("requisitionNo",summary.task().documentNo(),"itemId",operation.taskId(),
                    "warehouseName",summary.task().sourceWarehouseName(),"labelNos",summary.units().stream().map(PickUnit::code).toList()));
            case "PICK_HIGH_VALUE" -> business.confirmHighValuePicking(operation.taskId(),operation.traceCodeIds());
            default -> throw new IllegalArgumentException("不支持的拣选类型");
        };
        BigDecimal remaining=data.detail(operation.taskId(),operation.deptId(),operation.warehouseId(),false).task().remainingQuantity();
        Result response=new Result(operation.operationId(),operation.kind(),operation.taskId(),String.valueOf(result.get("deliveryNo")),
                "succeeded",user,operation.deviceId(),Instant.now(),"本次拣选成功，已生成配送单",remaining);
        receipts.complete(user,operation,response,reviews.encode(response));return response;
    }
    PickSnapshot snapshot(PickOperation op,boolean lock) {
        var current=authorizedDetail(op.taskId(),op.deptId(),op.warehouseId(),lock);PickTask task=current.task();requireOpen(task);
        if(!task.kind().equals(op.kind()) || !task.sourceWarehouseId().equals(op.sourceWarehouseId())) throw new MobileConflictException("明细类型或原单来源库已变化");
        List<PickUnit> units=new ArrayList<>();BigDecimal quantity;BigDecimal available=null;
        if("PICK_LOOSE".equals(op.kind())) {
            if(op.quantity()==null || op.quantity().signum()<=0 || op.rawCode()==null || op.rawCode().isBlank()
                    || !op.packageIds().isEmpty() || !op.traceCodeIds().isEmpty()) throw new IllegalArgumentException("散货需扫描正确物料码并确认基本单位数量");
            com.hospital.spd.common.QuantityRules.positive(op.quantity(),"拣选数量");
            units.add(resolveUnit(current,op.rawCode(),lock));quantity=op.quantity();available=looseAvailable(current,lock);
            if(quantity.compareTo(available)>0) throw new MobileConflictException("可用散货库存不足");
        } else {
            if(op.quantity()!=null || (op.rawCode()!=null && !op.rawCode().isBlank())) throw new IllegalArgumentException("实际码拣选不能手工声明数量或商品码");
            if("PICK_PACKAGE".equals(op.kind())) {
                if(op.packageIds().isEmpty() || !op.traceCodeIds().isEmpty()) throw new IllegalArgumentException("请扫描实际定数包");
                for(Long id:op.packageIds().stream().sorted().toList()) units.add(packageById(current,id,lock));
            } else {
                if(op.traceCodeIds().isEmpty() || !op.packageIds().isEmpty()) throw new IllegalArgumentException("请扫描高值实际唯一码");
                for(Long id:op.traceCodeIds().stream().sorted().toList()) units.add(traceById(current,id,lock));
            }
            quantity=units.stream().map(PickUnit::quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        }
        if(quantity.signum()<=0 || quantity.compareTo(task.remainingQuantity())>0) throw new MobileConflictException("本次数量超过申领剩余数量");
        return new PickSnapshot(task,List.copyOf(units),quantity,available);
    }
    private PickUnit resolveUnit(MobilePickingRepository.Data row,String code,boolean lock) {
        PickTask task=row.task();
        if("PICK_LOOSE".equals(task.kind())) {
            if(!task.productCode().equals(code) && (row.productUdi()==null || row.productUdi().isBlank() || !row.productUdi().equals(code)))
                throw new IllegalArgumentException("物料码与当前散货明细不匹配");
            return new PickUnit("product",task.productId(),task.productCode(),"available",BigDecimal.ONE);
        }
        List<PickUnit> matches=data.resolve(row,code,lock);
        if(matches.isEmpty()) throw new IllegalArgumentException("该实物码不可拣选，请核对商品、来源库、模板版本和当前状态");
        if(matches.size()!=1) throw new IllegalArgumentException("该码对应多个实物，请扫描具体包码或唯一码");
        return matches.get(0);
    }
    private PickUnit packageById(MobilePickingRepository.Data row,Long id,boolean lock) {
        List<String> codes=data.packageCodes(row,id);
        if(codes.size()!=1) throw new IllegalArgumentException("所选包不属于当前商品或来源库");
        PickUnit unit=resolveUnit(row,codes.get(0),lock);
        if(!id.equals(unit.id())) throw new IllegalArgumentException("定数包身份不一致");
        var sources=data.packageSources(id,lock);
        if(sources.isEmpty()) throw new IllegalArgumentException("定数包来源批次缺失");
        BigDecimal total=BigDecimal.ZERO;
        for(var source:sources) {
            if(!row.task().sourceWarehouseId().equals(source.warehouseId()) || !row.task().productId().equals(source.productId())
                    || source.quantity()==null || source.quantity().signum()<=0 || source.expired())
                throw new IllegalArgumentException("定数包来源批次缺失、过期或数量不完整");
            com.hospital.spd.common.QuantityRules.positive(source.quantity(),"包来源数量");total=total.add(source.quantity());
        }
        if(total.compareTo(unit.quantity())!=0) throw new IllegalArgumentException("包来源数量与包内数量不一致");
        return unit;
    }
    private PickUnit traceById(MobilePickingRepository.Data row,Long id,boolean lock) {
        List<String> codes=data.traceCodes(id);
        if(codes.size()!=1) throw new IllegalArgumentException("唯一码不存在");
        PickUnit unit=resolveUnit(row,codes.get(0),lock);
        if(!unit.id().equals(id)) throw new IllegalArgumentException("唯一码身份不一致");
        return unit;
    }
    private BigDecimal looseAvailable(MobilePickingRepository.Data row,boolean lock) {
        BigDecimal total=BigDecimal.ZERO;
        for(var balance:data.looseBalances(row,lock)) {
            if(balance.quantity().signum()>0 && balance.expired()) throw new IllegalArgumentException("来源散货含过期批次，请先隔离后再拣选");
            total=total.add(balance.quantity());
        }
        return total;
    }
    static void shape(PickOperation op) {
        if(!List.of("PICK_LOOSE","PICK_PACKAGE","PICK_HIGH_VALUE").contains(op.kind())) throw new IllegalArgumentException("不支持的拣选类型");
        if(op.packageIds()==null || op.traceCodeIds()==null || new HashSet<>(op.packageIds()).size()!=op.packageIds().size()
                || new HashSet<>(op.traceCodeIds()).size()!=op.traceCodeIds().size()) throw new IllegalArgumentException("实际码清单缺失或存在重复码");
    }
    private MobilePickingRepository.Data authorizedDetail(Long id,Long dept,Long warehouse,boolean lock) {
        var row=data.detail(id,dept,warehouse,lock);var operator=access.operator();
        if(Integer.valueOf(OperatorContext.DATA_SCOPE_SELF).equals(operator.dataScope()) && !Objects.equals(operator.userId(),row.applicantId()))
            throw new AccessDeniedException("无权访问其他申请人的申领明细");
        return row;
    }
    private static void requireOpen(PickTask task) {
        if(!List.of("approved","partial_picked").contains(task.status()) || task.remainingQuantity().signum()<=0)
            throw new MobileConflictException("申领状态已变化或该明细已拣选完成，请刷新");
    }
}
