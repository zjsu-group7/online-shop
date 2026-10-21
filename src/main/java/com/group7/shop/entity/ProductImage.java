package com.group7.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 商品多图：一件商品可有多张，按 sort 升序展示。 */
@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private int sort;

    protected ProductImage() {
    }

    public ProductImage(Long productId, String url, int sort) {
        this.productId = productId;
        this.url = url;
        this.sort = sort;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getUrl() { return url; }
    public int getSort() { return sort; }

    public void setUrl(String url) { this.url = url; }
    public void setSort(int sort) { this.sort = sort; }
}