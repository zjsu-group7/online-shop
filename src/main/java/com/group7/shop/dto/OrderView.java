package com.group7.shop.dto;

import com.group7.shop.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderView(Long id, String orderNo, OrderStatus status, BigDecimal totalAmount,
                        Instant createdAt, List<Item> items) {
    public record Item(Long productId, String productName, BigDecimal unitPrice, Integer quantity) {}
}
