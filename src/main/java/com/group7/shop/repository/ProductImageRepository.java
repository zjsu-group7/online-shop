package com.group7.shop.repository;

import com.group7.shop.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortAscIdAsc(Long productId);

    void deleteByProductId(Long productId);
}