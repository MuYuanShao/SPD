package com.hospital.spd.mobile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.function.Supplier;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Serializes recovery with the original submit and fences requests that can no longer execute. */
@Service
public class MobileRecoveryService {
    private final MobileAccessService access;
    private final MobileReceiptRepository receipts;
    private final MobileReviewService reviews;

    public MobileRecoveryService(MobileAccessService access, MobileReceiptRepository receipts, MobileReviewService reviews) {
        this.access = access; this.receipts = receipts; this.reviews = reviews;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> Recovery<T> recover(Command command, Supplier<Recovery<T>> review) {
        Long user = access.operator().userId();
        String hash = reviews.payloadHash(command);
        var receipt = receipts.acquire(user, command, hash);
        if (!hash.equals(receipt.payloadHash())) {
            throw new MobileConflictException("原操作编号的内容不一致，不能恢复其他作业");
        }
        if (receipt.result() != null) return new Recovery<>(receipt.result(), null, null, null);
        try {
            // No stock mutation is allowed here. The receipt lock orders this review against all late submits.
            return review.get();
        } catch (MobileConflictException | IllegalArgumentException conflict) {
            Result rejected = new Result(command.operationId(), command.kind(), command.taskId(), null, "rejected",
                    user, command.deviceId(), Instant.now(), "原操作已终止：" + conflict.getMessage());
            receipts.complete(user, command, rejected, reviews.encode(rejected));
            return new Recovery<>(rejected, null, null, null);
        }
    }
}
