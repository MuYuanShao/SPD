package com.hospital.spd.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.supplychain.service.OperationalDeliveryModule;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static com.hospital.spd.mobile.MobileContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MobileSigningServiceTest {
    final MobileAccessService access = mock(MobileAccessService.class);
    final MobileDeliveryRepository deliveries = mock(MobileDeliveryRepository.class);
    final MobileReceiptRepository receipts = mock(MobileReceiptRepository.class);
    final OperationalDeliveryModule business = mock(OperationalDeliveryModule.class);
    final MobileReviewService reviews = new MobileReviewService(new ObjectMapper().findAndRegisterModules(), "");
    final MobileSigningService service = new MobileSigningService(access, deliveries, receipts, reviews, business);
    final UUID operationId = UUID.randomUUID();

    @Test void exactPackageSetRequiredEvenWhenCountMatches() {
        SignOperation op = op(List.of(1L, 3L), List.of(), false, null);
        assertThatThrownBy(() -> MobileSigningService.requireComplete(op, detail("package", "picked",
                List.of(new CheckItem("package", 1L, "A", "", "delivered"), new CheckItem("package", 2L, "B", "", "delivered")))))
                .isInstanceOf(MobileConflictException.class);
    }
    @Test void duplicateAndMissingCodesAreRejected() {
        assertThatThrownBy(() -> MobileSigningService.requireShape(op(List.of(1L, 1L), List.of(), false, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MobileSigningService.requireComplete(op(List.of(), List.of(), false, null),
                detail("unique_code", "picked", List.of(new CheckItem("trace", 7L, "U7", "UDI", "delivery_picked")))))
                .isInstanceOf(MobileConflictException.class);
    }
    @Test void reviewCannotBeForgedOrReusedByAnotherUserOrAfterStateChange() {
        Detail detail = detail("loose", "picked", List.of());
        SignOperation raw = op(List.of(), List.of(), true, null);
        String token = reviews.issue(1L, raw, detail).reviewHash();
        SignOperation checked = op(List.of(), List.of(), true, token);
        reviews.verify(1L, checked, detail);
        assertThatThrownBy(() -> reviews.verify(2L, checked, detail)).isInstanceOf(MobileConflictException.class);
        assertThatThrownBy(() -> reviews.verify(1L, checked, detail("loose", "signed", List.of())))
                .isInstanceOf(MobileConflictException.class);
        assertThatThrownBy(() -> reviews.verify(1L, op(List.of(), List.of(), true, "0." + "a".repeat(64)), detail))
                .isInstanceOf(MobileConflictException.class);
    }
    @Test void successfulRetryReturnsOriginalReceiptWithoutExecutingBusinessAgain() {
        SignOperation op = op(List.of(), List.of(), true, null);
        when(access.operator()).thenReturn(OperatorContext.system());
        Result original = new Result(operationId, "SIGN_DELIVERY", 10L, "PS-test", "succeeded", 1L, "device", Instant.now(), "成功");
        when(receipts.acquire(eq(1L), eq(op), anyString())).thenReturn(new MobileReceiptRepository.Receipt(reviews.payloadHash(op), 2L, 3L, original));
        assertThat(service.submit(op)).isEqualTo(original);
        verifyNoInteractions(business, deliveries);
    }
    @Test void sameOperationIdWithChangedPayloadNeverExecutesBusiness() {
        SignOperation op = op(List.of(), List.of(), true, null);
        when(access.operator()).thenReturn(OperatorContext.system());
        when(receipts.acquire(eq(1L), eq(op), anyString())).thenReturn(new MobileReceiptRepository.Receipt("different", 2L, 3L, null));
        assertThatThrownBy(() -> service.submit(op)).isInstanceOf(MobileConflictException.class);
        verifyNoInteractions(business, deliveries);
    }
    @Test void permissionIsCheckedBeforeReturningExistingReceipt() {
        doThrow(new AccessDeniedException("denied")).when(access).requireWarehouse(2L, 3L);
        assertThatThrownBy(() -> service.submit(op(List.of(), List.of(), true, null))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(receipts, deliveries, business);
    }
    @Test void validatedOperationInvokesExistingServiceAndSavesReceipt() {
        Detail detail = detail("loose", "picked", List.of());
        SignOperation raw = op(List.of(), List.of(), true, null);
        SignOperation checked = op(List.of(), List.of(), true, reviews.issue(1L, raw, detail).reviewHash());
        when(access.operator()).thenReturn(OperatorContext.system());
        when(receipts.acquire(eq(1L), eq(checked), anyString())).thenReturn(new MobileReceiptRepository.Receipt(reviews.payloadHash(checked), 2L, 3L, null));
        when(deliveries.detail(10L, 2L, 3L, true)).thenReturn(detail);
        assertThat(service.submit(checked).status()).isEqualTo("succeeded");
        var order = inOrder(business, receipts);
        order.verify(business).signDelivery("PS-test");
        order.verify(receipts).complete(eq(1L), eq(checked), any(Result.class), anyString());
    }
    private SignOperation op(List<Long> packages, List<Long> traces, boolean loose, String hash) {
        return new SignOperation(operationId, "device", "SIGN_DELIVERY", 10L, 2L, 3L, packages, traces, loose, hash);
    }
    private Detail detail(String type, String status, List<CheckItem> items) {
        return new Detail(new Task(10L, "PS-test", status, type, 2L, "科室", 4L, "中心库", 3L, "科室库", "P1", "耗材", BigDecimal.ONE, "个"), items);
    }
}
