package com.group7.shop.dto;

import java.util.List;

public record CheckoutResult(PurchaseOrderView order, List<RejectedCartItem> rejected) {
    public record RejectedCartItem(Long cartItemId, String reason) {}
}
