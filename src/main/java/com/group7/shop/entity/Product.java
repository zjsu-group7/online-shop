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

    /** 所属二级类别。 */
    private Long categoryId;

    /** 库存数量；成交后减 1，为 0 时在售商品自动下架。 */
    private Integer stock;

    /** 已锁定的库存数量（下单锁定、成交扣减、取消释放）；对外「库存」仍以 stock 为准。 */
    private Integer lockedStock = 0;

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

    public Product(String name, String description, BigDecimal price, String image,
                   Long categoryId, Integer stock) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.image = image;
        this.categoryId = categoryId;
        this.stock = stock;
        this.status = ProductStatus.ON_SALE;
        this.frozenBy = null;
    }

    public Product(String name, String description, BigDecimal price, String image, int stock) {
        this(name, description, price, image, null, stock);
    }

    public Product(String name, String description, BigDecimal price, String image) {
        this(name, description, price, image, null, null);
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ProductStatus.ON_SALE;
        }
        if (lockedStock == null) {
            lockedStock = 0;
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

    /** 库存改为 0 时在售商品自动下架。 */
    public void closeOutOfStock() {
        this.status = ProductStatus.OFF_SHELF;
        this.frozenBy = null;
        this.closedAt = Instant.now();
        this.closeReason = ProductCloseReason.OUT_OF_STOCK;
    }

    /** 修改库存（唯一入口由 ProductService.updateStock 保证）。 */
    public void changeStock(int stock) {
        this.stock = stock;
    }

    /** 交易成功扣减 1 件库存；调用前由服务层校验库存 > 0。 */
    public void deductOneStock() {
        this.stock = this.stock - 1;
    }

    /* ==================== 库存锁定（挑战需求 B，负责人：李婉婷） ==================== */

    /** 可售库存 = 库存 − 已锁定；下单校验以此为准。 */
    public int availableStock() {
        return (stock == null ? 0 : stock) - (lockedStock == null ? 0 : lockedStock);
    }

    /** 下单锁定 n 件库存（调用前由服务层校验 availableStock() >= n）。 */
    public void lockStock(int n) {
        this.lockedStock = (lockedStock == null ? 0 : lockedStock) + n;
    }

    /** 取消订单时释放 n 件锁定库存。 */
    public void unlockStock(int n) {
        this.lockedStock = Math.max(0, (lockedStock == null ? 0 : lockedStock) - n);
    }

    /** 交易完成时真正扣减：库存 −n 并同步释放 n 件锁定。 */
    public void commitStock(int n) {
        this.stock = (stock == null ? 0 : stock) - n;
        this.lockedStock = Math.max(0, (lockedStock == null ? 0 : lockedStock) - n);
    }

    /** 重新上架：由已下架回到在售，清空关闭信息；库存由服务层校验 > 0。 */
    public void reopen() {
        this.status = ProductStatus.ON_SALE;
        this.frozenBy = null;
        this.closedAt = null;
        this.closeReason = null;
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
    public Long getCategoryId() { return categoryId; }
    public Integer getStock() { return stock; }
    public Integer getLockedStock() { return lockedStock; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public void setStock(Integer stock) { this.stock = stock; }
    public void setLockedStock(Integer lockedStock) { this.lockedStock = lockedStock; }
}
