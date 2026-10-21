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
 * 客户与下单记录的关联表（李宇轩负责，挑战需求 A.2.2）。
 *
 * <p><b>为什么单独建表而不改 intents：</b>
 * {@code Intent} 实体与意向域归华天遥负责，客户域不修改他人代码。
 * 因此以「关联表」方式记录「这条意向是谁下的」：客户下单时由
 * {@link com.group7.shop.service.CustomerService#createOrder} 写入一行，
 * 「我的下单记录」再按 customer_id 反查意向。
 *
 * <p>意向的状态仍以 {@code intents.status} 为唯一事实来源，本表不冗余状态，
 * 只冗余下单时的商品 id 与提交时间，避免联表缺失时排序失真。
 */
@Entity
@Table(name = "customer_order_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_customer_order_intent", columnNames = "intent_id"),
        indexes = @Index(name = "idx_customer_order_customer", columnList = "customer_id,submitted_at"))
public class CustomerOrderLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 对应 intents.id，一意向下单只允许关联一次。 */
    @Column(name = "intent_id", nullable = false)
    private Long intentId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /**
     * 交易结果落定时间。首次查询到终态时从商品侧补齐并落库（成功取 closedAt，失败取 lastTradeFailedAt），
     * 之后固定不变，避免同一商品的多次失败互相覆盖历史时间。
     */
    @Column(name = "result_at")
    private Instant resultAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CustomerOrderLink() {
    }

    public CustomerOrderLink(Long customerId, Long intentId, Long productId, Instant submittedAt) {
        this.customerId = customerId;
        this.intentId = intentId;
        this.productId = productId;
        this.submittedAt = submittedAt;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getIntentId() {
        return intentId;
    }

    public Long getProductId() {
        return productId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getResultAt() {
        return resultAt;
    }

    public void setResultAt(Instant resultAt) {
        this.resultAt = resultAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
