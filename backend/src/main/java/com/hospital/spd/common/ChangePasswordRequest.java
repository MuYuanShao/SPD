package com.hospital.spd.common;

/**
 * 当前登录用户自助修改密码请求。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码
 * @param confirmPassword 确认新密码
 */
public record ChangePasswordRequest(
    String oldPassword,
    String newPassword,
    String confirmPassword
) {}
