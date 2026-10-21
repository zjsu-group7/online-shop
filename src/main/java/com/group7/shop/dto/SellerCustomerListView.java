package com.group7.shop.dto;

import java.util.List;

/** 卖家客户列表响应。 */
public record SellerCustomerListView(
        List<SellerCustomerView> items,
        int total
) {
}
