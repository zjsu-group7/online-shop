package com.group7.shop.dto;

import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 商品列表轻量条目；详情富文本、多图和内部交易字段不在列表重复传输。 */
public record ProductSummaryView(
        Long id,
        String name,
        BigDecimal price,
        String imageUrl,
        List<String> images,
        Long categoryId,
        Integer stock,
        ProductStatus status,
        Instant createdAt
) {
    public static ProductSummaryView from(Product product) {
        return new ProductSummaryView(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getImage(),
                product.getImage() == null ? List.of() : List.of(product.getImage()),
                product.getCategoryId(),
                product.getStock(),
                product.getStatus(),
                product.getCreatedAt()
        );
    }
}
