package com.hospital.spd.mobile;

import com.hospital.spd.common.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Preserves actionable authorization and conflict responses for mobile clients. */
@Order(-1)
@RestControllerAdvice(assignableTypes = MobileController.class)
public class MobileExceptionHandler {
    @ExceptionHandler(MobileConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(MobileConflictException ex) {
        return ResponseEntity.status(409).body(ApiResponse.error(409, ex.getMessage()));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ApiResponse.error(403, ex.getMessage()));
    }
}
