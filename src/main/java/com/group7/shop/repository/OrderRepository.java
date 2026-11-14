package com.group7.shop.repository;

import com.group7.shop.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/** 订单数据访问接口（负责人：周琳皓）。 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 买家「我的订单」：按时间倒序 + 翻页（id 兜底同毫秒排序）。 */
    List<Order> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);

    /** 卖家后台订单列表：全部订单按时间倒序。 */
    List<Order> findAllByOrderByCreatedAtDescIdDesc();

    Optional<Order> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
