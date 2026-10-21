package com.group7.shop.dto;

import com.group7.shop.entity.Customer;

import java.time.Instant;

/** 客户个人信息视图；不包含密码、盐、token 等敏感字段。 */
public record CustomerView(
        Long id,
        String username,
        String phone,
        String defaultTradePlace,
        Instant createdAt
) {
    public static CustomerView from(Customer customer) {
        return new CustomerView(
                customer.getId(),
                customer.getUsername(),
                customer.getPhone(),
                customer.getDefaultTradePlace(),
                customer.getCreatedAt()
        );
    }
}
