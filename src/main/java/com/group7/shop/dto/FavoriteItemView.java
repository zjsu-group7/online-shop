package com.group7.shop.dto;

import com.group7.shop.entity.Product;
import com.group7.shop.service.ProductAvailability;

import java.time.Instant;

/** 收藏条目视图（华天遥，挑战需求 B）：与购物车同款的实时下架/无货标注。 */
public record FavoriteItemView(
        Long favoriteId,
        Instant createdAt,
        ProductView product,
        boolean available,
        String unavailableReason
) {
    public static FavoriteItemView of(Long favoriteId, Instant createdAt, Product product) {
        String reason = ProductAvailability.reasonOf(product);
        return new FavoriteItemView(favoriteId, createdAt, ProductView.from(product),
                reason == null, reason);
    }
}
