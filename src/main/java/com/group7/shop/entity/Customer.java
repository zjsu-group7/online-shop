package com.group7.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 客户（买家）账号，第 2 次迭代随挑战需求 A.2 新增（李宇轩负责）。
 *
 * <p>与 Seller 保持同一套安全口径，但**独立建表**：两套身份不共用 token 表，
 * 避免卖家与买家的凭证互相污染，也让现有卖家登录测试不受影响。
 *
 * <ul>
 *   <li>密码存储为「随机盐 + SHA-256」，见 {@link com.group7.shop.common.PasswordHasher}；</li>
 *   <li>用户名唯一（应用层忽略大小写判重），电话**不要求唯一**（同一电话可注册多个账号）；</li>
 *   <li>登录凭证为「用户名 + 密码」，电话仅作联系方式，不能用于登录；</li>
 *   <li>token 单值模型：重新登录 / 登出后旧 token 立即失效，默认有效期 2 小时；</li>
 *   <li>连续失败 5 次锁定 5 分钟，与卖家策略一致。</li>
 * </ul>
 */
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "uk_customers_username", columnList = "username", unique = true),
        @Index(name = "idx_customers_token", columnList = "token")
})
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** 形如 "saltBase64:hashBase64"，与 salt 列前缀保持一致。 */
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String salt;

    /** 联系电话，必填但不唯一；仅作联系方式，不作为登录凭证。 */
    @Column(nullable = false, length = 20)
    private String phone;

    /** 默认交易地点，选填，纯文本；不结构化、不接地图。 */
    @Column(name = "default_trade_place", length = 200)
    private String defaultTradePlace;

    /** 当前有效 token；登出后置 null。 */
    @Column(length = 64)
    private String token;

    @Column
    private Instant tokenExpiresAt;

    /** 连续登录失败次数；包装类型兼容新增列时的历史空值。 */
    @Column
    private Integer failedAttempts;

    @Column
    private Instant lockedUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(String username, String salt, String passwordHash, String phone, String defaultTradePlace) {
        this.username = username;
        this.salt = salt;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.defaultTradePlace = defaultTradePlace;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDefaultTradePlace() {
        return defaultTradePlace;
    }

    public void setDefaultTradePlace(String defaultTradePlace) {
        this.defaultTradePlace = defaultTradePlace;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(Instant tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    /** 历史数据该列为空，统一按 0 处理。 */
    public int getFailedAttempts() {
        return failedAttempts == null ? 0 : failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 账号当前是否处于锁定期。 */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
