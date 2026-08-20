package com.hospital.spd.common;

/**
 * 登录成功响应
 */
public record LoginResponse(
    String token,
    String tokenType,
    long userId,
    String username
) {
    public LoginResponse(String token, long userId, String username) {
        this(token, "Bearer", userId, username);
    }
}
