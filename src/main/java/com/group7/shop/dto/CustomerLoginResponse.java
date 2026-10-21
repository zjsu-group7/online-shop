package com.group7.shop.dto;

import java.time.Instant;

/**
 * 客户登录响应。与卖家登录保持同一形状（token + 过期时间），额外回带账号资料，
 * 便于前端登录后直接渲染个人中心，不用再请求一次 /me。
 */
public record CustomerLoginResponse(
        String token,
        Instant expiresAt,
        CustomerView customer
) {
}
