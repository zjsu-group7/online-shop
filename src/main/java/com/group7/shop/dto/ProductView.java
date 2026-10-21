package com.group7.shop.dto;

import com.group7.shop.entity.FrozenBy;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductCloseReason;
import com.group7.shop.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** API 展示 DTO：对外字段名使用 imageUrl / images，实体字段仍为 image。 */
public record ProductView(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        List<String> images,
        Long categoryId,
        Integer stock,
        ProductStatus status,
        FrozenBy frozenBy,
        Instant createdAt,
        Instant closedAt,
        ProductCloseReason closeReason,
        Instant lastTradeFailedAt
) {
    public static ProductView from(Product product) {
        return from(product, product.getImage() == null ? List.of() : List.of(product.getImage()));
    }

    public static ProductView from(Product product, List<String> images) {
        return new ProductView(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImage(),
                images == null ? List.of() : images,
                product.getCategoryId(),
                product.getStock(),
                product.getStatus(),
                product.getFrozenBy(),
                product.getCreatedAt(),
                product.getClosedAt(),
                product.getCloseReason(),
                product.getLastTradeFailedAt()
        );
    }
}