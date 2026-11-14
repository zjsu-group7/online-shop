package com.group7.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 收藏（挑战需求 B，负责人：华天遥；表结构牵头：李婉婷）。
 * 账号绑定；「收藏/取消收藏」二态标记，同一商品不能重复收藏，故 (customer_id, product_id) 唯一。
 */
@Entity
@Table(name = "favorites",
        uniqueConstraints = @UniqueConstraint(name = "uk_favorite_customer_product",
                columnNames = {"customer_id", "product_id"}),
        indexes = @Index(name = "idx_favorite_customer", columnList = "customer_id"))
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Favorite() {
    }

    public Favorite(Long customerId, Long productId) {
        this.customerId = customerId;
        this.productId = productId;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getProductId() { return productId; }
    public Instant getCreatedAt() { return createdAt; }
}
