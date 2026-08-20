package com.hospital.spd.common;

/**
 * 登录请求
 */
public record LoginRequest(
    String username,
    String password
) {}
