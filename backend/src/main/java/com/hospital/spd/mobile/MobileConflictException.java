package com.hospital.spd.mobile;

/** Signals a mobile operation conflict that requires checking the original result or reviewing again. */
public class MobileConflictException extends RuntimeException {
    public MobileConflictException(String message) { super(message); }
}
