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

import java.time.Instant;

/**
 * 买家购买意向（意向域负责人：华天遥）。
 * 口令码只存哈希（codeHash，SHA-256，全库唯一），明文仅在提交响应返回一次。
 * 字段与 Wiki《接口设计》数据模型一致：id, productId, name, phone, codeHash, status, submittedAt。
 * 账号与后台域（李宇轩）按 productId + submittedAt 只读聚合到后台总览，融合时保留其只读兼容性。
 */
@Entity
@Table(name = "intents", indexes = {
        @Index(name = "idx_intents_product_status", columnList = "product_id,status"),
        @Index(name = "uk_intents_code_hash", columnList = "code_hash", unique = true)
})
public class Intent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属商品 id。按 Wiki 约定不建 JPA 关联，跨模块仅以 id 解耦。 */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String phone;

    /** 口令码的 SHA-256 十六进制值，唯一索引兜底防止并发重复。 */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntentStatus status = IntentStatus.WAITING;

    /** 排队时间：提交时写入；交易失败选择 REQUEUE 时刷新为当前时间，从而排到队尾。 */
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected Intent() {
    }

    public Intent(Long productId, String name, String phone, String codeHash, Instant submittedAt) {
        this.productId = productId;
        this.name = name;
        this.phone = phone;
        this.codeHash = codeHash;
        this.status = IntentStatus.WAITING;
        this.submittedAt = submittedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = IntentStatus.WAITING;
        }
    }

    /** 买家凭口令码修改姓名/电话（仅排队中允许），不改变排队位次。 */
    public void updateContact(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    /** 买家撤销意向，终态留痕。 */
    public void cancel() {
        this.status = IntentStatus.CANCELLED;
    }

    /** 卖家选定该买家，进入交易。 */
    public void markInTrade() {
        this.status = IntentStatus.IN_TRADE;
    }

    /** 交易成功（终态）。 */
    public void markTraded() {
        this.status = IntentStatus.TRADED;
    }

    /** 交易失败（终态）。 */
    public void markFailed() {
        this.status = IntentStatus.FAILED;
    }

    /** 交易失败后重新排队：回到 WAITING 并刷新排队时间，从而排到队尾。 */
    public void requeueToTail(Instant at) {
        this.status = IntentStatus.WAITING;
        this.submittedAt = at;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getCodeHash() { return codeHash; }
    public IntentStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }

    /** 供账号域只读聚合等场景直接构造视图使用，不影响持久化状态。 */
    public void setStatus(IntentStatus status) { this.status = status; }
}
