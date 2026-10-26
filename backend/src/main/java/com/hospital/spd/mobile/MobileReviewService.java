package com.hospital.spd.mobile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import static com.hospital.spd.mobile.MobileContracts.*;

/** Issues short-lived server-authenticated review tokens bound to the operator, payload and current delivery. */
@Service
public class MobileReviewService {
    private final ObjectMapper json;
    private final byte[] key;

    public MobileReviewService(ObjectMapper json, @Value("${spd.mobile.review-secret:}") String secret) {
        this.json = json;
        if (!secret.isBlank() && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("移动核对密钥至少需要 32 字节");
        }
        key = secret.isBlank() ? new SecureRandom().generateSeed(32) : secret.getBytes(StandardCharsets.UTF_8);
    }

    public String payloadHash(Command command) {
        if(command instanceof ConsumeOperation op) {
            return hash(encode(List.of(op.operationId(),op.deviceId(),op.kind(),op.taskId(),op.deptId(),op.warehouseId(),op.rawCode(),op.wholePackageConfirmed())));
        }
        if (command instanceof PickOperation op) {
            return hash(encode(List.of(op.operationId(), op.deviceId(), op.kind(), op.taskId(), op.deptId(),
                    op.warehouseId(), op.sourceWarehouseId(), op.quantity() == null ? "" : op.quantity().stripTrailingZeros().toPlainString(),
                    op.rawCode() == null ? "" : op.rawCode(), op.packageIds().stream().sorted().toList(), op.traceCodeIds().stream().sorted().toList())));
        }
        SignOperation op = (SignOperation) command;
        // Lists are sets for business identity, but duplicates are rejected before hashing.
        return hash(encode(List.of(op.operationId(), op.deviceId(), op.kind(), op.taskId(), op.deptId(),
                op.warehouseId(), op.packageIds().stream().sorted().toList(),
                op.traceCodeIds().stream().sorted().toList(), op.looseConfirmed())));
    }

    public Review issue(Long userId, SignOperation op, Detail detail) {
        Instant expires = Instant.now().plusSeconds(300);
        String epoch = Long.toString(expires.getEpochSecond());
        return new Review(epoch + "." + signature(userId, op, detail, epoch),
                Instant.ofEpochSecond(expires.getEpochSecond()), detail);
    }

    public void verify(Long userId, SignOperation op, Detail detail) {
        verifyToken(userId, op, detail, op.reviewHash());
    }

    public PickReview issue(Long userId, PickOperation op, PickSnapshot snapshot) {
        String epoch = Long.toString(Instant.now().plusSeconds(300).getEpochSecond());
        return new PickReview(epoch + "." + signature(userId, op, snapshot, epoch),
                Instant.ofEpochSecond(Long.parseLong(epoch)), snapshot);
    }

    public void verify(Long userId, PickOperation op, PickSnapshot snapshot) {
        verifyToken(userId, op, snapshot, op.reviewHash());
    }
    public ConsumeReview issue(Long userId,ConsumeOperation op,ConsumeSnapshot snapshot) {
        String epoch=Long.toString(Instant.now().plusSeconds(300).getEpochSecond());
        return new ConsumeReview(epoch+"."+signature(userId,op,snapshot,epoch),Instant.ofEpochSecond(Long.parseLong(epoch)),snapshot);
    }
    public void verify(Long userId,ConsumeOperation op,ConsumeSnapshot snapshot) {
        verifyToken(userId,op,snapshot,op.reviewHash());
    }

    private void verifyToken(Long userId, Command op, Object detail, String token) {
        if (token == null || !token.matches("[0-9]{1,12}\\.[a-f0-9]{64}")) throw conflict();
        String[] parts = token.split("\\.");
        long expiry = Long.parseLong(parts[0]);
        long now = Instant.now().getEpochSecond();
        if (expiry <= now || expiry > now + 300 || !MessageDigest.isEqual(
                parts[1].getBytes(StandardCharsets.US_ASCII),
                signature(userId, op, detail, parts[0]).getBytes(StandardCharsets.US_ASCII))) throw conflict();
    }

    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("无法保存移动操作回执", ex); }
    }

    private String signature(Long userId, Command op, Object detail, String expiry) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(encode(List.of(userId, payloadHash(op), detail, expiry))
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }

    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static MobileConflictException conflict() {
        return new MobileConflictException("核对摘要已失效或内容已变化，请重新校验并确认");
    }
}
