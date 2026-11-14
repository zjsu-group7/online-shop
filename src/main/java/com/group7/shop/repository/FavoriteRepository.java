package com.group7.shop.repository;

import com.group7.shop.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 收藏数据访问接口（负责人：华天遥）。 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /** 买家收藏列表：按收藏时间倒序。 */
    List<Favorite> findByCustomerIdOrderByIdDesc(Long customerId);

    Optional<Favorite> findByCustomerIdAndProductId(Long customerId, Long productId);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    void deleteByCustomerIdAndProductId(Long customerId, Long productId);
}
