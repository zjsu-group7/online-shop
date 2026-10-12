package com.group7.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** 图片 URL 或相对路径；对外接口将其映射为 imageUrl。 */
    @Column(length = 500)
    private String image;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ON_SALE;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FrozenBy frozenBy;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProductCloseReason closeReason;

    /** 最近一次交易失败时间；商品恢复在售后仍用于历史记录查询。 */
    private Instant lastTradeFailedAt;

    protected Product() {
    }

    public Product(String name, String description, BigDecimal price, String image) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.image = image;
        this.status = ProductStatus.ON_SALE;
        this.frozenBy = null;
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ProductStatus.ON_SALE;
        }
    }

    public void freezeManually() {
        this.status = ProductStatus.FROZEN;
        this.frozenBy = FrozenBy.MANUAL;
    }

    public void freezeForTrade() {
        this.status = ProductStatus.FROZEN;
        this.frozenBy = FrozenBy.AUTO;
    }

    public void unfreezeManually() {
        this.status = ProductStatus.ON_SALE;
        this.frozenBy = null;
    }

    public void restoreAfterTradeFailure() {
        this.status = ProductStatus.ON_SALE;
        this.frozenBy = null;
        this.lastTradeFailedAt = Instant.now();
    }

    public void closeManually() {
        this.status = ProductStatus.OFF_SHELF;
        this.frozenBy = null;
        this.closedAt = Instant.now();
        this.closeReason = ProductCloseReason.MANUAL;
    }

    public void closeAfterTradeSuccess() {
        this.status = ProductStatus.OFF_SHELF;
        this.frozenBy = null;
        this.closedAt = Instant.now();
        this.closeReason = ProductCloseReason.SOLD;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getImage() { return image; }
    public ProductStatus getStatus() { return status; }
    public FrozenBy getFrozenBy() { return frozenBy; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
    public ProductCloseReason getCloseReason() { return closeReason; }
    public Instant getLastTradeFailedAt() { return lastTradeFailedAt; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setImage(String image) { this.image = image; }
    public void setStatus(ProductStatus status) { this.status = status; }
    public void setFrozenBy(FrozenBy frozenBy) { this.frozenBy = frozenBy; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}
