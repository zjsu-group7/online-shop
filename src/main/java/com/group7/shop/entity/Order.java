package com.group7.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单（挑战需求 B，负责人：周琳皓；表结构牵头：李婉婷）。
 * 一个订单含多件商品清单（见 {@link OrderItem}）；价格取下单时快照。
 * 状态流转与取消规则见 {@link OrderStatus} 与《需求规格说明书》3.3/3.4。
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "uk_orders_order_no", columnList = "order_no", unique = true),
        @Index(name = "idx_orders_customer", columnList = "customer_id,created_at")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 订单号，全局唯一。 */
    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    /** 下单客户 id（账号域）；跨模块以 id 解耦，不建 JPA 关联。 */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 订单总金额 = Σ(单价 × 数量)，取下单时快照。 */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.CUSTOMER_SUBMITTED;

    /** 交易地点：沿用账号默认交易地点，允许为空（选填未填则留空）。 */
    @Column(length = 200)
    private String location;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    protected Order() {
    }

    public Order(String orderNo, Long customerId, BigDecimal totalAmount, String location) {
        this.orderNo = orderNo;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.location = location;
        this.status = OrderStatus.CUSTOMER_SUBMITTED;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = OrderStatus.CUSTOMER_SUBMITTED;
        }
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public Long getCustomerId() { return customerId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public OrderStatus getStatus() { return status; }
    public String getLocation() { return location; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void setStatus(OrderStatus status) { this.status = status; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
