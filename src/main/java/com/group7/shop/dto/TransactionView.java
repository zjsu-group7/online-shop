package com.group7.shop.dto;

import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;

/** 卖家执行交易操作后的状态快照；不含口令码。 */
public record TransactionView(
        ProductView product,
        Long intentId,
        IntentStatus intentStatus
) {
    public static TransactionView from(ProductView product, Intent intent) {
        return new TransactionView(product, intent.getId(), intent.getStatus());
    }
}
