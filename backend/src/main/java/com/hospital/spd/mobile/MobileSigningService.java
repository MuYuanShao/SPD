package com.hospital.spd.mobile;

import com.hospital.spd.common.PageRequest;
import com.hospital.spd.common.PageResponse;
import com.hospital.spd.supplychain.service.OperationalDeliveryModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Coordinates physical verification, review and atomic idempotent whole-delivery signing. */
@Service
public class MobileSigningService {
    private final MobileAccessService access;
    private final MobileDeliveryRepository deliveries;
    private final MobileReceiptRepository receipts;
    private final MobileReviewService reviews;
    private final OperationalDeliveryModule business;

    public MobileSigningService(MobileAccessService access, MobileDeliveryRepository deliveries,
                                 MobileReceiptRepository receipts, MobileReviewService reviews,
                                 OperationalDeliveryModule business) {
        this.access = access; this.deliveries = deliveries; this.receipts = receipts;
        this.reviews = reviews; this.business = business;
    }

    public PageResponse<Task> tasks(Long deptId, Long warehouseId, String status, PageRequest page) {
        access.requireWarehouse(deptId, warehouseId);
        if (!List.of("picked", "signed").contains(status)) throw new IllegalArgumentException("仅支持待签收或已签收状态");
        return deliveries.tasks(deptId, warehouseId, status, page);
    }

    public Detail detail(Long id, Long deptId, Long warehouseId) {
        access.requireWarehouse(deptId, warehouseId);
        return deliveries.detail(id, deptId, warehouseId, false);
    }

    public CheckItem resolve(Scan scan) {
        Detail detail = detail(scan.taskId(), scan.deptId(), scan.warehouseId());
        if (!"picked".equals(detail.task().status())) throw new MobileConflictException("该配送单已被处理，请刷新");
        // Only resolve identities already bound to this authorized task; never leak another warehouse's codes.
        List<CheckItem> matches = detail.items().stream().filter(item -> scan.rawCode().equals(item.code())
                || (!item.alternateCode().isBlank() && scan.rawCode().equals(item.alternateCode()))).toList();
        if (matches.isEmpty()) throw new IllegalArgumentException("该码不属于当前配送单，请核对实物");
        if (matches.size() != 1) throw new IllegalArgumentException("该码对应多个实物，请扫描具体包码或唯一码");
        return matches.get(0);
    }

    public Review validate(SignOperation op) {
        requireShape(op);
        access.requireWarehouse(op.deptId(), op.warehouseId());
        Detail detail = deliveries.detail(op.taskId(), op.deptId(), op.warehouseId(), false);
        requireComplete(op, detail);
        return reviews.issue(access.operator().userId(), op, detail);
    }

    @Transactional
    public Result submit(SignOperation op) {
        requireShape(op);
        access.requireWarehouse(op.deptId(), op.warehouseId());
        Long userId = access.operator().userId();
        String hash = reviews.payloadHash(op);
        MobileReceiptRepository.Receipt receipt = receipts.acquire(userId, op, hash);
        if (!hash.equals(receipt.payloadHash())) throw new MobileConflictException("同一操作编号的内容不一致，请先查询原操作结果");
        if (receipt.result() != null) return receipt.result();
        Detail detail = deliveries.detail(op.taskId(), op.deptId(), op.warehouseId(), true);
        requireComplete(op, detail);
        reviews.verify(userId, op, detail);
        business.signDelivery(detail.task().documentNo());
        Result result = new Result(op.operationId(), op.kind(), op.taskId(), detail.task().documentNo(),
                "succeeded", userId, op.deviceId(), Instant.now(), "配送整单签收成功");
        receipts.complete(userId, op, result, reviews.encode(result));
        return result;
    }

    public Result result(UUID operationId) {
        access.requireSigning();
        Long userId = access.operator().userId();
        MobileReceiptRepository.Receipt receipt = receipts.find(userId, operationId, false);
        if (receipt == null) return new Result(operationId, "SIGN_DELIVERY", null, null, "unknown", userId,
                null, null, "尚未查到回执，不能认定失败；请继续查询或沿用原操作编号重试");
        access.requireWarehouse(receipt.deptId(), receipt.warehouseId());
        if (receipt.result() == null) throw new MobileConflictException("操作结果尚未确认，请继续查询");
        return receipt.result();
    }

    static void requireShape(SignOperation op) {
        if (!"SIGN_DELIVERY".equals(op.kind())) throw new IllegalArgumentException("当前仅开放配送签收");
        if (op.packageIds() == null || op.traceCodeIds() == null) throw new IllegalArgumentException("缺少实物核对清单");
        if (new HashSet<>(op.packageIds()).size() != op.packageIds().size()
                || new HashSet<>(op.traceCodeIds()).size() != op.traceCodeIds().size()) {
            throw new IllegalArgumentException("核对清单包含重复实物码");
        }
    }

    static void requireComplete(SignOperation op, Detail detail) {
        if (!"picked".equals(detail.task().status())) throw new MobileConflictException("配送单已被处理，请刷新后核对");
        Set<Long> packages = ids(detail, "package");
        Set<Long> traces = ids(detail, "trace");
        if (!packages.equals(new HashSet<>(op.packageIds())) || !traces.equals(new HashSet<>(op.traceCodeIds()))) {
            throw new MobileConflictException("实物核对不完整或存在其他单据的码，不能整单签收");
        }
        String type = detail.task().deliveryType();
        boolean valid = switch (type) {
            case "loose" -> packages.isEmpty() && traces.isEmpty() && op.looseConfirmed();
            case "package" -> !packages.isEmpty() && traces.isEmpty() && !op.looseConfirmed();
            case "unique_code" -> !traces.isEmpty() && packages.isEmpty() && !op.looseConfirmed();
            default -> false;
        };
        if (!valid) throw new MobileConflictException("配送类型、实物绑定或散货实收确认不完整，请核对原单");
        if (detail.items().stream().anyMatch(item -> !("package".equals(item.type())
                ? "delivered".equals(item.status()) : "delivery_picked".equals(item.status())))) {
            throw new MobileConflictException("实物状态已变化，请重新核对");
        }
    }

    private static Set<Long> ids(Detail detail, String type) {
        return detail.items().stream().filter(item -> type.equals(item.type())).map(CheckItem::id).collect(Collectors.toSet());
    }
}
