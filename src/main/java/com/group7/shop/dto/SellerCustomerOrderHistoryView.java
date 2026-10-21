package com.group7.shop.dto;

import java.util.List;

/** 指定客户的终态购买记录，仅包含成功和失败。 */
public record SellerCustomerOrderHistoryView(
        Long customerId,
        String username,
        List<OrderRecordView> items,
        int total
) {
}
