package com.group7.shop.repository;

import com.group7.shop.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 订单商品项数据访问接口（负责人：周琳皓）。 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);
}
