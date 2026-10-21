package com.group7.shop.repository;

import com.group7.shop.entity.CustomerOrderLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 客户下单关联数据访问（李宇轩负责）。 */
public interface CustomerOrderLinkRepository extends JpaRepository<CustomerOrderLink, Long> {

    /** 我的下单记录：按客户取全部关联，提交时间倒序（id 兜底同毫秒排序）。 */
    List<CustomerOrderLink> findByCustomerIdOrderBySubmittedAtDescIdDesc(Long customerId);

    Optional<CustomerOrderLink> findByIntentId(Long intentId);

    boolean existsByIntentId(Long intentId);
}
