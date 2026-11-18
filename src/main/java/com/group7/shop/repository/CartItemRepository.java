package com.group7.shop.repository;

import com.group7.shop.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 购物车数据访问接口（负责人：华天遥）。 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** 买家购物车列表：按加入顺序。 */
    List<CartItem> findByCustomerIdOrderByIdAsc(Long customerId);

    /** 多选下单 / 多选转收藏时按 id 批量取（并限定本人，防越权）。 */
    List<CartItem> findByCustomerIdAndIdIn(Long customerId, Collection<Long> ids);

    Optional<CartItem> findByCustomerIdAndProductId(Long customerId, Long productId);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    void deleteByCustomerIdAndProductId(Long customerId, Long productId);
}
