package com.group7.shop.dto;

import com.group7.shop.entity.FrozenBy;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductCloseReason;
import com.group7.shop.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** 历史列表条目；商品字段保持与 ProductView 同层，便于前端复用。 */
public record ProductHistoryView(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        ProductStatus status,
        FrozenBy frozenBy,
        Instant createdAt,
        Instant closedAt,
        ProductCloseReason closeReason,
        Instant lastTradeFailedAt,
        String historyType,
        Instant historyAt,
        boolean hadTradeFailure
) {
    public static ProductHistoryView from(Product product) {
        String historyType;
        Instant historyAt;
        if (product.getClosedAt() != null) {
            historyType = product.getCloseReason() == null
                    ? "OFF_SHELF"
                    : product.getCloseReason().name();
            historyAt = product.getClosedAt();
        } else {
            historyType = "TRADE_FAILED";
            historyAt = product.getLastTradeFailedAt();
        }

        return new ProductHistoryView(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImage(),
                product.getStatus(),
                product.getFrozenBy(),
                product.getCreatedAt(),
                product.getClosedAt(),
                product.getCloseReason(),
                product.getLastTradeFailedAt(),
                historyType,
                historyAt,
                product.getLastTradeFailedAt() != null
        );
    }
}
