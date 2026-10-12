package com.group7.shop.repository;

import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByStatusIn(List<ProductStatus> statuses);

    List<Product> findByStatusInOrderByCreatedAtDesc(List<ProductStatus> statuses);

    List<Product> findByStatusOrderByClosedAtDesc(ProductStatus status);

    @Query("select p from Product p where p.status = com.group7.shop.entity.ProductStatus.OFF_SHELF or p.lastTradeFailedAt is not null")
    List<Product> findHistoryProducts();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
