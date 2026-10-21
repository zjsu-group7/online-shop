package com.group7.shop.dto;

import java.time.Instant;

/** 卖家客户列表条目；phone 已脱敏，不返回地址、密码、盐或 token。 */
public record SellerCustomerView(
        Long id,
        String username,
        String phone,
        Instant createdAt,
        int successCount,
        int failureCount,
        int orderCount
) {
}
