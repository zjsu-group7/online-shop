package com.group7.shop.dto;

import com.group7.shop.entity.Product;
import com.group7.shop.service.ProductAvailability;

import java.time.Instant;

/**
 * 购物车条目视图（华天遥，挑战需求 B）。
 * available / unavailableReason 为查询时实时计算：商品下架 → OFF_SHELF，
 * 可售库存（availableStock）为 0 → OUT_OF_STOCK；补货或重新上架后标注自动消失。
 */
public record CartItemView(
        Long itemId,
        Integer quantity,
        Instant addedAt,
        ProductView product,
        boolean available,
        String unavailableReason
) {
    public static CartItemView of(CartItemViewSource source) {
        Product product = source.product();
        String reason = ProductAvailability.reasonOf(product);
        return new CartItemView(
                source.itemId(),
                source.quantity(),
                source.addedAt(),
                ProductView.from(product),
                reason == null,
                reason
        );
    }

    /** 解耦实体与视图的最小取值集。 */
    public record CartItemViewSource(Long itemId, Integer quantity, Instant addedAt, Product product) {
    }
}
