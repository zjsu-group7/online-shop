package com.group7.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 订单商品项（挑战需求 B，负责人：周琳皓；表结构牵头：李婉婷）。
 * 名称与单价为下单时快照，商品后来改价/删除不影响历史订单显示。
 */
@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order", columnList = "order_id")
})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属订单 id。 */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 商品 id（快照引用，不建 JPA 关联）。 */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 商品名称快照。 */
    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    /** 成交单价快照。 */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** 购买数量（同一商品可买多份）。 */
    @Column(nullable = false)
    private Integer quantity;

    protected OrderItem() {
    }

    public OrderItem(Long orderId, Long productId, String productName,
                     BigDecimal unitPrice, Integer quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Integer getQuantity() { return quantity; }
}
