package com.group7.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** 商品类别：固定两级，parentId 为空表示一级类别。 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** null = 一级类别；非空 = 二级类别，指向其父类别。 */
    private Long parentId;

    @Column(nullable = false)
    private int sort;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Category() {
    }

    public Category(String name, Long parentId, int sort) {
        this.name = name;
        this.parentId = parentId;
        this.sort = sort;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isTopLevel() {
        return parentId == null;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getParentId() { return parentId; }
    public int getSort() { return sort; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setSort(int sort) { this.sort = sort; }
}