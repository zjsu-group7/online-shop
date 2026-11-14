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
 * 购物车条目（挑战需求 B，负责人：华天遥；表结构牵头：李婉婷）。
 * 账号绑定；按「商品 + 数量」记录，重复加入同一商品即数量累加，故 (customer_id, product_id) 唯一。
 */
@Entity
@Table(name = "cart_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_cart_customer_product",
                columnNames = {"customer_id", "product_id"}),
        indexes = @Index(name = "idx_cart_customer", columnList = "customer_id"))
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 数量；重复加购在此累加。 */
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CartItem() {
    }

    public CartItem(Long customerId, Long productId, Integer quantity) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public Instant getCreatedAt() { return createdAt; }

    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
